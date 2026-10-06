package com.myroutine.shop.web;

import com.myroutine.common.security.CurrentMember;
import com.myroutine.shop.application.OpenShopService;
import com.myroutine.shop.application.ShopQueryService;
import com.myroutine.shop.application.ShopResult;
import com.myroutine.shop.application.UpdateShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
public class ShopController {
    private final OpenShopService openShopService;
    private final UpdateShopService updateShopService;
    private final ShopQueryService shopQueryService;

    @PostMapping
    public ResponseEntity<ShopIdResponse> open(@CurrentMember UUID memberId, @Valid @RequestBody OpenShopRequest request) {
        UUID shopId = openShopService.open(memberId, request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ShopIdResponse(shopId));
    }

    @GetMapping("/me")
    public ResponseEntity<List<MyShopResponse>> getMyShops(@CurrentMember UUID memberId) {
        List<ShopResult> result = shopQueryService.getMyShops(memberId);
        return ResponseEntity.ok(result.stream().map(MyShopResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShopResponse> getShop(@PathVariable UUID id) {
        ShopResult result = shopQueryService.getShop(id);
        return ResponseEntity.ok(ShopResponse.from(result));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<MyShopResponse> update(@CurrentMember UUID memberId, @PathVariable UUID id, @Valid @RequestBody UpdateShopRequest request) {
        ShopResult result = updateShopService.update(memberId, id, request.toCommand());
        return ResponseEntity.ok(MyShopResponse.from(result));
    }
}
