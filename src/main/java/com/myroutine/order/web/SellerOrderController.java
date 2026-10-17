package com.myroutine.order.web;

import com.myroutine.common.security.CurrentMember;
import com.myroutine.common.web.CursorPage;
import com.myroutine.order.application.SellerOrderQueryService;
import com.myroutine.order.application.SellerShopOrderResult;
import com.myroutine.order.application.ShipmentService;
import com.myroutine.order.domain.ShopOrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/shops/{shopId}/orders")
@RequiredArgsConstructor
public class SellerOrderController {

    private final SellerOrderQueryService sellerOrderQueryService;
    private final ShipmentService shipmentService;

    @GetMapping
    public ResponseEntity<CursorPage<SellerShopOrderResponse>> getShopOrders(
            @CurrentMember UUID memberId,
            @PathVariable UUID shopId,
            @RequestParam(required = false) ShopOrderStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "1 이상이어야 합니다.") @Max(value = 50, message = "50 이하여야 합니다.") int size
    ) {
        CursorPage<SellerShopOrderResult> results = sellerOrderQueryService.getShopOrders(memberId, shopId, status, cursor, size);
        return ResponseEntity.ok().body(new CursorPage<>(results.items().stream().map(SellerShopOrderResponse::from).toList(), results.nextCursor()));
    }

    @GetMapping("/{shopOrderId}")
    public ResponseEntity<SellerShopOrderResponse> getShopOrder(
            @CurrentMember UUID memberId,
            @PathVariable UUID shopId,
            @PathVariable UUID shopOrderId
    ) {
        SellerShopOrderResult result = sellerOrderQueryService.getShopOrder(memberId, shopId, shopOrderId);
        return ResponseEntity.ok().body(SellerShopOrderResponse.from(result));
    }

    @PostMapping("/{shopOrderId}/ship")
    public ResponseEntity<SellerShopOrderResponse> ship(
            @CurrentMember UUID memberId,
            @PathVariable UUID shopId,
            @PathVariable UUID shopOrderId,
            @RequestBody @Valid ShipRequest request
    ) {
        SellerShopOrderResult result = shipmentService.ship(memberId, shopId, shopOrderId, request.carrier(), request.trackingNumber());
        return ResponseEntity.ok().body(SellerShopOrderResponse.from(result));
    }

    @PostMapping("/{shopOrderId}/deliver")
    public ResponseEntity<SellerShopOrderResponse> deliver(
            @CurrentMember UUID memberId,
            @PathVariable UUID shopId,
            @PathVariable UUID shopOrderId
    ) {
        SellerShopOrderResult result = shipmentService.deliver(memberId, shopId, shopOrderId);
        return ResponseEntity.ok().body(SellerShopOrderResponse.from(result));
    }
}
