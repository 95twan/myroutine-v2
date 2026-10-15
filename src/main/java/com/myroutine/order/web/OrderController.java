package com.myroutine.order.web;

import com.myroutine.common.idempotency.Idempotent;
import com.myroutine.common.security.CurrentMember;
import com.myroutine.common.storage.ImageUrls;
import com.myroutine.common.web.CursorPage;
import com.myroutine.order.application.*;
import com.myroutine.order.domain.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CheckoutService checkoutService;
    private final OrderQueryService orderQueryService;
    private final ConfirmPaymentService confirmPaymentService;
    private final PendingOrderCancelService pendingOrderCancelService;
    private final ImageUrls imageUrls;

    @PostMapping("/checkout")
    @Idempotent
    public ResponseEntity<CheckoutResponse> checkout(
            @CurrentMember UUID memberId,
            @RequestBody @Valid CheckoutRequest request
    ) {
        CheckoutResult checkout = checkoutService.checkout(memberId, request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(CheckoutResponse.from(checkout));
    }

    @GetMapping
    public ResponseEntity<CursorPage<OrderSummaryResponse>> getMyOrders(
            @CurrentMember UUID memberId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "1 이상이어야 합니다.") @Max(value = 50, message = "50 이하여야 합니다.") int size
    ) {
        CursorPage<OrderSummaryResult> myOrders = orderQueryService.getMyOrders(memberId, cursor, size);
        return ResponseEntity.ok(new CursorPage<>(myOrders.items().stream().map(OrderSummaryResponse::from).toList(), myOrders.nextCursor()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailResponse> getMyOrder(@CurrentMember UUID memberId, @PathVariable UUID id) {
        OrderDetailResult result = orderQueryService.getMyOrder(memberId, id);
        return ResponseEntity.ok(OrderDetailResponse.from(result, imageUrls));
    }

    @PostMapping("/{id}/payment/confirm")
    @Idempotent
    public ResponseEntity<ConfirmResponse> confirm(@CurrentMember UUID memberId, @PathVariable UUID id, @RequestBody @Valid ConfirmPaymentRequest request) {
        ConfirmResult result = confirmPaymentService.confirm(memberId, id, request.toCommand());
        if (result.status() == OrderStatus.PAID) {
            return ResponseEntity.ok(ConfirmResponse.from(result));
        }
        return ResponseEntity.accepted().body(ConfirmResponse.from(result));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ConfirmResponse> cancel(@CurrentMember UUID memberId, @PathVariable UUID id) {
        OrderStatus result = pendingOrderCancelService.cancel(memberId, id);
        return ResponseEntity.ok(new ConfirmResponse(id, result));
    }
}
