package com.myroutine.search.web;

import com.myroutine.common.storage.ImageUrls;
import com.myroutine.common.web.CursorPage;
import com.myroutine.search.application.ProductSearchResult;
import com.myroutine.search.application.ProductSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {
    private final ProductSearchService productSearchService;
    private final ImageUrls imageUrls;

    @GetMapping("/products")
    public ResponseEntity<CursorPage<ProductSearchResponse>> searchProducts(
            @RequestParam @NotBlank(message = "필수 값입니다.") @Size(max = 50, message = "검색어는 50자 이하여야 합니다.") String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "1 이상이어야 합니다.") @Max(value = 50, message = "50 이하여야 합니다.") int size
    ) {
        CursorPage<ProductSearchResult> result = productSearchService.search(q, category, cursor, size);
        return ResponseEntity.ok(new CursorPage<>(result.items().stream().map(r -> ProductSearchResponse.from(r, imageUrls)).toList(), result.nextCursor()));
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<List<String>> autocomplete(@RequestParam @NotBlank(message = "필수 값입니다.") @Size(min = 2, max = 20, message = "검색어는 2~20자여야 합니다.") String q) {
        List<String> results = productSearchService.autocomplete(q);
        return ResponseEntity.ok(results);
    }
}
