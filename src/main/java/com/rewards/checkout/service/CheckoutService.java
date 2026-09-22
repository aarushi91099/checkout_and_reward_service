package com.rewards.checkout.service;

import com.rewards.checkout.domain.entity.Cart;
import com.rewards.checkout.domain.entity.CartItem;
import com.rewards.checkout.domain.entity.CheckoutRequest;
import com.rewards.checkout.domain.entity.Coupon;
import com.rewards.checkout.domain.entity.Order;
import com.rewards.checkout.domain.entity.OrderItem;
import com.rewards.checkout.domain.entity.Product;
import com.rewards.checkout.domain.enums.CartStatus;
import com.rewards.checkout.domain.enums.CouponStatus;
import com.rewards.checkout.exception.ApiException;
import com.rewards.checkout.exception.ErrorCode;
import com.rewards.checkout.repository.CartItemRepository;
import com.rewards.checkout.repository.CartRepository;
import com.rewards.checkout.repository.CheckoutRequestRepository;
import com.rewards.checkout.repository.CouponRepository;
import com.rewards.checkout.repository.OrderItemRepository;
import com.rewards.checkout.repository.OrderRepository;
import com.rewards.checkout.repository.ProductRepository;
import com.rewards.checkout.support.Money;
import com.rewards.checkout.web.dto.OrderResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lock acquisition order is fixed across every checkout transaction to prevent deadlocks:
 * Cart -> Products (sorted by id) -> Coupon. See ProductRepository#findAllByIdForUpdate for the
 * ORDER BY that makes this dependable at the DB level.
 *
 * Idempotency relies on the Cart row lock rather than polling: a retry with the same key blocks
 * on the same cart's FOR UPDATE lock until the original attempt commits or rolls back, then
 * either replays the committed order or, if the original rolled back, proceeds fresh. See
 * DECISIONS.md for the full argument.
 */
@Service
public class CheckoutService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CheckoutRequestRepository checkoutRequestRepository;
    private final OrderService orderService;
    private final RewardMilestoneService rewardMilestoneService;

    public CheckoutService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                            ProductRepository productRepository, CouponRepository couponRepository,
                            OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                            CheckoutRequestRepository checkoutRequestRepository, OrderService orderService,
                            RewardMilestoneService rewardMilestoneService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.couponRepository = couponRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.checkoutRequestRepository = checkoutRequestRepository;
        this.orderService = orderService;
        this.rewardMilestoneService = rewardMilestoneService;
    }

    @Transactional
    public OrderResponse checkout(UUID cartId, String idempotencyKey, String couponCode) {
        CheckoutRequest existing = checkoutRequestRepository.findById(idempotencyKey).orElse(null);
        if (existing != null) {
            return orderService.getOrder(existing.getOrderId());
        }

        Cart cart = cartRepository.findByIdForUpdate(cartId)
                .orElseThrow(() -> new ApiException(ErrorCode.CART_NOT_FOUND, "Cart not found: " + cartId));

        if (cart.getStatus() == CartStatus.CHECKED_OUT) {
            CheckoutRequest afterLock = checkoutRequestRepository.findById(idempotencyKey).orElse(null);
            if (afterLock != null) {
                return orderService.getOrder(afterLock.getOrderId());
            }
            throw new ApiException(ErrorCode.CART_ALREADY_CHECKED_OUT, "Cart is already checked out: " + cartId);
        }

        List<CartItem> items = cartItemRepository.findByCartId(cartId);
        if (items.isEmpty()) {
            throw new ApiException(ErrorCode.CART_EMPTY, "Cart has no items: " + cartId);
        }

        List<UUID> productIds = items.stream().map(CartItem::getProductId).distinct().toList();
        Map<UUID, Product> productsById = productRepository.findAllByIdForUpdate(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        for (CartItem item : items) {
            Product product = productsById.get(item.getProductId());
            if (product == null) {
                throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found: " + item.getProductId());
            }
            if (product.getInventory() < item.getQuantity()) {
                throw new ApiException(ErrorCode.INSUFFICIENT_INVENTORY,
                        "Insufficient inventory for product: " + product.getId());
            }
        }

        BigDecimal subtotal = Money.normalize(items.stream()
                .map(item -> Money.lineTotal(productsById.get(item.getProductId()).getPrice(), item.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        Coupon coupon = null;
        BigDecimal discount = Money.normalize(BigDecimal.ZERO);
        if (couponCode != null && !couponCode.isBlank()) {
            coupon = couponRepository.findByCodeForUpdate(couponCode)
                    .orElseThrow(() -> new ApiException(ErrorCode.COUPON_NOT_FOUND, "Coupon not found: " + couponCode));
            if (coupon.getStatus() != CouponStatus.AVAILABLE) {
                throw new ApiException(ErrorCode.COUPON_ALREADY_REDEEMED, "Coupon already redeemed: " + couponCode);
            }
            discount = Money.discountFor(subtotal, coupon.getDiscountPercent());
        }
        BigDecimal total = Money.totalAfterDiscount(subtotal, discount);

        for (CartItem item : items) {
            productsById.get(item.getProductId()).deductInventory(item.getQuantity());
        }

        UUID orderId = UUID.randomUUID();
        Order order = orderRepository.save(
                new Order(orderId, cartId, subtotal, discount, total, coupon != null ? coupon.getCode() : null));

        List<OrderItem> orderItems = items.stream()
                .map(item -> {
                    Product product = productsById.get(item.getProductId());
                    return new OrderItem(UUID.randomUUID(), orderId, product.getId(), product.getName(),
                            Money.normalize(product.getPrice()), item.getQuantity());
                })
                .toList();
        orderItemRepository.saveAll(orderItems);

        if (coupon != null) {
            coupon.redeem(orderId);
        }
        cart.markCheckedOut();

        checkoutRequestRepository.save(new CheckoutRequest(idempotencyKey, cartId, orderId));

        scheduleMilestoneCheckAfterCommit();

        return orderService.toResponse(order, orderItems);
    }

    private void scheduleMilestoneCheckAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    rewardMilestoneService.evaluateAndGenerateIfDue();
                } catch (DataIntegrityViolationException ex) {
                    // a concurrent checkout's milestone check already generated this milestone's coupon
                }
            }
        });
    }
}
