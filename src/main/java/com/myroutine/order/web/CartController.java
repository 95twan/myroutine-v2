package com.myroutine.order.web;

import com.myroutine.common.security.CurrentMember;
import com.myroutine.common.storage.ImageUrls;
import com.myroutine.order.application.CartResult;
import com.myroutine.order.application.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;
    private final ImageUrls imageUrls;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(@CurrentMember UUID memberId) {
        CartResult result = cartService.getCart(memberId);
        return ResponseEntity.ok(CartResponse.from(result, imageUrls));
    }

    @PostMapping("/items")
    public ResponseEntity<CartItemIdResponse> addItem(@CurrentMember UUID memberId, @RequestBody @Valid AddCartItemRequest request) {
        UUID cartItemId = cartService.addItem(memberId, request.productId(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CartItemIdResponse(cartItemId));
    }

    @PatchMapping("/items/{id}")
    public ResponseEntity<CartResponse> changeQuantity(
            @CurrentMember UUID memberId,
            @PathVariable UUID id,
            @RequestBody @Valid ChangeQuantityRequest request
    ) {
        CartResult cartResult = cartService.changeQuantity(memberId, id, request.quantity());
        return ResponseEntity.ok(CartResponse.from(cartResult, imageUrls));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> removeItem(@CurrentMember UUID memberId, @PathVariable UUID id) {
        cartService.removeItem(memberId, id);
        return ResponseEntity.noContent().build();
    }
}
