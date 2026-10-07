package com.myroutine.product.web;

import com.myroutine.common.security.CurrentMember;
import com.myroutine.common.web.CursorPage;
import com.myroutine.product.application.ProductQueryService;
import com.myroutine.product.application.RegisterProductService;
import com.myroutine.product.application.SellerProductResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/shops/{shopId}/products")
@RequiredArgsConstructor
public class SellerProductController {
    private final RegisterProductService registerProductService;
    private final ProductQueryService productQueryService;

    @PostMapping
    public ResponseEntity<ProductIdResponse> register(@CurrentMember UUID memberId, @PathVariable UUID shopId, @RequestBody @Valid RegisterProductRequest request) {
        UUID productId = registerProductService.register(memberId, shopId, request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ProductIdResponse(productId));
    }

    @GetMapping
    public ResponseEntity<CursorPage<SellerProductResponse>> getShopProducts(
            @CurrentMember UUID memberId,
            @PathVariable UUID shopId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        CursorPage<SellerProductResult> result = productQueryService.getShopProducts(memberId, shopId, cursor, size);
        return ResponseEntity.ok(new CursorPage<>(result.items().stream().map(SellerProductResponse::from).toList(), result.nextCursor()));
    }
}
