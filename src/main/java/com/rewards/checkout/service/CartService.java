package com.rewards.checkout.service;

import com.rewards.checkout.domain.entity.Cart;
import com.rewards.checkout.domain.entity.CartItem;
import com.rewards.checkout.domain.entity.Product;
import com.rewards.checkout.domain.enums.CartStatus;
import com.rewards.checkout.exception.ApiException;
import com.rewards.checkout.exception.ErrorCode;
import com.rewards.checkout.repository.CartItemRepository;
import com.rewards.checkout.repository.CartRepository;
import com.rewards.checkout.repository.ProductRepository;
import com.rewards.checkout.support.Money;
import com.rewards.checkout.web.dto.CartItemResponse;
import com.rewards.checkout.web.dto.CartResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                        ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public CartResponse createCart() {
        Cart cart = cartRepository.save(new Cart(UUID.randomUUID()));
        return toResponse(cart, List.of());
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(UUID cartId) {
        Cart cart = requireCart(cartId);
        return toResponse(cart, cartItemRepository.findByCartId(cartId));
    }

    @Transactional
    public CartResponse addItem(UUID cartId, UUID productId, int quantity) {
        Cart cart = requireActiveCart(cartId);
        requireValidQuantity(quantity);
        Product product = requireProduct(productId);

        CartItem existing = cartItemRepository.findByCartIdAndProductId(cartId, productId).orElse(null);
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
        } else {
            cartItemRepository.save(new CartItem(UUID.randomUUID(), cart.getId(), product.getId(), quantity));
        }
        return toResponse(cart, cartItemRepository.findByCartId(cartId));
    }

    @Transactional
    public CartResponse updateItem(UUID cartId, UUID productId, int quantity) {
        Cart cart = requireActiveCart(cartId);
        requireValidQuantity(quantity);
        requireProduct(productId);

        CartItem item = cartItemRepository.findByCartIdAndProductId(cartId, productId)
                .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_FOUND, "Product not in cart: " + productId));
        item.setQuantity(quantity);
        return toResponse(cart, cartItemRepository.findByCartId(cartId));
    }

    @Transactional
    public CartResponse removeItem(UUID cartId, UUID productId) {
        Cart cart = requireActiveCart(cartId);
        cartItemRepository.deleteByCartIdAndProductId(cartId, productId);
        return toResponse(cart, cartItemRepository.findByCartId(cartId));
    }

    private Cart requireCart(UUID cartId) {
        return cartRepository.findById(cartId)
                .orElseThrow(() -> new ApiException(ErrorCode.CART_NOT_FOUND, "Cart not found: " + cartId));
    }

    private Cart requireActiveCart(UUID cartId) {
        Cart cart = requireCart(cartId);
        if (cart.getStatus() != CartStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CART_ALREADY_CHECKED_OUT, "Cart is already checked out: " + cartId);
        }
        return cart;
    }

    private Product requireProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found: " + productId));
    }

    private void requireValidQuantity(int quantity) {
        if (quantity <= 0) {
            throw new ApiException(ErrorCode.INVALID_QUANTITY, "Quantity must be positive: " + quantity);
        }
    }

    private CartResponse toResponse(Cart cart, List<CartItem> items) {
        List<CartItemResponse> itemResponses = items.stream()
                .map(item -> {
                    Product product = requireProduct(item.getProductId());
                    return new CartItemResponse(
                            product.getId(),
                            product.getName(),
                            Money.normalize(product.getPrice()),
                            item.getQuantity(),
                            Money.lineTotal(product.getPrice(), item.getQuantity()));
                })
                .toList();
        return new CartResponse(cart.getId(), cart.getStatus(), itemResponses);
    }
}
