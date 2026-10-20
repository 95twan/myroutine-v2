package com.myroutine.order.web;

import com.myroutine.common.security.CurrentMember;
import com.myroutine.order.application.RefundResult;
import com.myroutine.order.application.ReturnService;
import com.myroutine.order.domain.RefundStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/shops/{shopId}/refunds")
@RequiredArgsConstructor
public class SellerRefundController {
    private final ReturnService returnService;

    @PostMapping("/{refundId}/approve")
    public ResponseEntity<RefundResponse> approve(
            @CurrentMember UUID memberId,
            @PathVariable UUID shopId,
            @PathVariable UUID refundId
    ) {
        RefundResult result = returnService.approve(memberId, shopId, refundId);
        if (result.status() == RefundStatus.COMPLETED) {
            return ResponseEntity.ok(RefundResponse.from(result));
        } else {
            return ResponseEntity.accepted().body(RefundResponse.from(result));
        }
    }

    @PostMapping("/{refundId}/reject")
    public ResponseEntity<RefundResponse> reject(
            @CurrentMember UUID memberId,
            @PathVariable UUID shopId,
            @PathVariable UUID refundId,
            @RequestBody @Valid RejectReturnRequest request
    ) {
        RefundResult result = returnService.reject(memberId, shopId, refundId, request.reason());
        return ResponseEntity.ok(RefundResponse.from(result));
    }
}
