package com.myroutine.product.web;

import com.myroutine.common.web.CursorPage;
import com.myroutine.product.application.ProductDetailResult;
import com.myroutine.product.application.ProductQueryService;
import com.myroutine.product.application.ProductSummaryResult;
import com.myroutine.product.domain.ProductCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductQueryService productQueryService;

    @GetMapping
    public ResponseEntity<CursorPage<ProductSummaryResponse>> getPublicProducts(
            @RequestParam(required = false) ProductCategory category,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        CursorPage<ProductSummaryResult> result = productQueryService.getPublicProducts(category, cursor, size);

        return ResponseEntity.ok(new CursorPage<>(result.items().stream().map(ProductSummaryResponse::from).toList(), result.nextCursor()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailResponse> getProduct(@PathVariable UUID id) {
        ProductDetailResult result = productQueryService.getProduct(id);
        return ResponseEntity.ok(ProductDetailResponse.from(result));
    }
}
