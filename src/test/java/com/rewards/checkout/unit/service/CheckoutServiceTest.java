package com.rewards.checkout.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rewards.checkout.domain.entity.Cart;
import com.rewards.checkout.domain.entity.CartItem;
import com.rewards.checkout.domain.entity.CheckoutRequest;
import com.rewards.checkout.domain.entity.Coupon;
import com.rewards.checkout.domain.entity.Order;
import com.rewards.checkout.domain.entity.Product;
import com.rewards.checkout.exception.ApiException;
import com.rewards.checkout.exception.ErrorCode;
import com.rewards.checkout.repository.CartItemRepository;
import com.rewards.checkout.repository.CartRepository;
import com.rewards.checkout.repository.CheckoutRequestRepository;
import com.rewards.checkout.repository.CouponRepository;
import com.rewards.checkout.repository.OrderItemRepository;
import com.rewards.checkout.repository.OrderRepository;
import com.rewards.checkout.repository.ProductRepository;
import com.rewards.checkout.service.CheckoutService;
import com.rewards.checkout.service.OrderService;
import com.rewards.checkout.service.RewardMilestoneService;
import com.rewards.checkout.web.dto.OrderResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CouponRepository couponRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private CheckoutRequestRepository checkoutRequestRepository;
    @Mock private OrderService orderService;
    @Mock private RewardMilestoneService rewardMilestoneService;

    private CheckoutService checkoutService;

    private final UUID cartId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final String idempotencyKey = "test-key";

    @BeforeEach
    void setUp() {
        checkoutService = new CheckoutService(cartRepository, cartItemRepository, productRepository,
                couponRepository, orderRepository, orderItemRepository, checkoutRequestRepository, orderService,
                rewardMilestoneService);
    }

    @Test
    void returnsExistingOrderWithoutTouchingLocksWhenIdempotencyKeyAlreadyProcessed() {
        UUID orderId = UUID.randomUUID();
        when(checkoutRequestRepository.findById(idempotencyKey))
                .thenReturn(Optional.of(new CheckoutRequest(idempotencyKey, cartId, orderId)));
        OrderResponse expected = new OrderResponse(orderId, cartId, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN,
                null, null, List.of());
        when(orderService.getOrder(orderId)).thenReturn(expected);

        OrderResponse result = checkoutService.checkout(cartId, idempotencyKey, null);

        assertThat(result).isEqualTo(expected);
        verify(cartRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void throwsCartNotFoundWhenCartDoesNotExist() {
        when(checkoutRequestRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkoutService.checkout(cartId, idempotencyKey, null))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.CART_NOT_FOUND);
    }

    @Test
    void throwsCartAlreadyCheckedOutWhenNoMatchingIdempotencyRecordExists() {
        when(checkoutRequestRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(checkedOutCart()));

        assertThatThrownBy(() -> checkoutService.checkout(cartId, idempotencyKey, null))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.CART_ALREADY_CHECKED_OUT);
    }

    @Test
    void replaysOrderWhenRetryBlockedOnCartLockFindsCompletedRecordAfterAcquiringLock() {
        UUID orderId = UUID.randomUUID();
        when(checkoutRequestRepository.findById(idempotencyKey))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new CheckoutRequest(idempotencyKey, cartId, orderId)));
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(checkedOutCart()));
        OrderResponse expected = new OrderResponse(orderId, cartId, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN,
                null, null, List.of());
        when(orderService.getOrder(orderId)).thenReturn(expected);

        OrderResponse result = checkoutService.checkout(cartId, idempotencyKey, null);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void throwsCartEmptyWhenCartHasNoItems() {
        when(checkoutRequestRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(activeCart()));
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());

        assertThatThrownBy(() -> checkoutService.checkout(cartId, idempotencyKey, null))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.CART_EMPTY);
    }

    @Test
    void throwsInsufficientInventoryAndNeverPersistsOrderWhenStockTooLow() {
        when(checkoutRequestRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(activeCart()));
        when(cartItemRepository.findByCartId(cartId))
                .thenReturn(List.of(new CartItem(UUID.randomUUID(), cartId, productId, 2)));
        when(productRepository.findAllByIdForUpdate(anyCollection()))
                .thenReturn(List.of(new Product(productId, "Widget", new BigDecimal("9.99"), 1)));

        assertThatThrownBy(() -> checkoutService.checkout(cartId, idempotencyKey, null))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.INSUFFICIENT_INVENTORY);

        verify(orderRepository, never()).save(any());
        verify(checkoutRequestRepository, never()).save(any());
    }

    @Test
    void throwsCouponNotFoundWhenCouponCodeDoesNotExist() {
        when(checkoutRequestRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(activeCart()));
        when(cartItemRepository.findByCartId(cartId))
                .thenReturn(List.of(new CartItem(UUID.randomUUID(), cartId, productId, 1)));
        when(productRepository.findAllByIdForUpdate(anyCollection()))
                .thenReturn(List.of(new Product(productId, "Widget", new BigDecimal("9.99"), 5)));
        when(couponRepository.findByCodeForUpdate("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkoutService.checkout(cartId, idempotencyKey, "MISSING"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.COUPON_NOT_FOUND);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void throwsCouponAlreadyRedeemedWhenCouponIsNotAvailable() {
        when(checkoutRequestRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(activeCart()));
        when(cartItemRepository.findByCartId(cartId))
                .thenReturn(List.of(new CartItem(UUID.randomUUID(), cartId, productId, 1)));
        when(productRepository.findAllByIdForUpdate(anyCollection()))
                .thenReturn(List.of(new Product(productId, "Widget", new BigDecimal("9.99"), 5)));
        Coupon redeemed = new Coupon("USED10", new BigDecimal("10"), 1);
        redeemed.redeem(UUID.randomUUID());
        when(couponRepository.findByCodeForUpdate("USED10")).thenReturn(Optional.of(redeemed));

        assertThatThrownBy(() -> checkoutService.checkout(cartId, idempotencyKey, "USED10"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.COUPON_ALREADY_REDEEMED);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void happyPathComputesTotalsAndPersistsOrderCouponAndCart() {
        when(checkoutRequestRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
        Cart cart = activeCart();
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartId(cartId))
                .thenReturn(List.of(new CartItem(UUID.randomUUID(), cartId, productId, 2)));
        Product product = new Product(productId, "Widget", new BigDecimal("10.00"), 5);
        when(productRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(product));
        Coupon coupon = new Coupon("SAVE10", new BigDecimal("10"), 1);
        when(couponRepository.findByCodeForUpdate("SAVE10")).thenReturn(Optional.of(coupon));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderService.toResponse(any(), any())).thenReturn(
                new OrderResponse(UUID.randomUUID(), cartId, new BigDecimal("20.00"), new BigDecimal("2.00"),
                        new BigDecimal("18.00"), "SAVE10", null, List.of()));

        OrderResponse response = checkoutService.checkout(cartId, idempotencyKey, "SAVE10");

        assertThat(response.subtotal()).isEqualByComparingTo("20.00");
        assertThat(response.discount()).isEqualByComparingTo("2.00");
        assertThat(response.total()).isEqualByComparingTo("18.00");
        assertThat(product.getInventory()).isEqualTo(3);
        assertThat(coupon.getStatus().name()).isEqualTo("REDEEMED");
        assertThat(cart.getStatus().name()).isEqualTo("CHECKED_OUT");
        verify(checkoutRequestRepository).save(any());
    }

    private Cart activeCart() {
        return new Cart(cartId);
    }

    private Cart checkedOutCart() {
        Cart cart = new Cart(cartId);
        cart.markCheckedOut();
        return cart;
    }
}
