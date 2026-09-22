package com.rewards.checkout.web.controller;

import com.rewards.checkout.service.CartService;
import com.rewards.checkout.web.dto.AddCartItemRequest;
import com.rewards.checkout.web.dto.CartResponse;
import com.rewards.checkout.web.dto.UpdateCartItemRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/carts")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    public ResponseEntity<CartResponse> createCart() {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.createCart());
    }

    @GetMapping("/{cartId}")
    public ResponseEntity<CartResponse> getCart(@PathVariable UUID cartId) {
        return ResponseEntity.ok(cartService.getCart(cartId));
    }

    @PostMapping("/{cartId}/items")
    public ResponseEntity<CartResponse> addItem(@PathVariable UUID cartId, @Valid @RequestBody AddCartItemRequest request) {
        CartResponse response = cartService.addItem(cartId, request.productId(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{cartId}/items/{productId}")
    public ResponseEntity<CartResponse> updateItem(@PathVariable UUID cartId, @PathVariable UUID productId,
                                                     @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(cartService.updateItem(cartId, productId, request.quantity()));
    }

    @DeleteMapping("/{cartId}/items/{productId}")
    public ResponseEntity<CartResponse> removeItem(@PathVariable UUID cartId, @PathVariable UUID productId) {
        return ResponseEntity.ok(cartService.removeItem(cartId, productId));
    }
}
