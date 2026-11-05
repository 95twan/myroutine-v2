package com.myroutine.order.web;

import com.myroutine.common.security.CurrentMember;
import com.myroutine.common.web.CursorPage;
import com.myroutine.order.application.AdminRefundResult;
import com.myroutine.order.application.AdminRefundService;
import com.myroutine.order.application.RefundResult;
import com.myroutine.order.domain.RefundStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/admin/api/refunds")
@RequiredArgsConstructor
public class AdminRefundController {
    private final AdminRefundService adminRefundService;

    @GetMapping
    public ResponseEntity<CursorPage<AdminRefundResponse>> getRefunds(
            @RequestParam RefundStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "1 이상이어야 합니다.") @Max(value = 50, message = "50 이하여야 합니다.") int size
    ) {
        CursorPage<AdminRefundResult> result = adminRefundService.getRefunds(status, cursor, size);
        List<AdminRefundResponse> items = result.items().stream()
                .map(AdminRefundResponse::from)
                .toList();
        return ResponseEntity.ok(new CursorPage<>(items, result.nextCursor()));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<RefundResponse> retryRefund(@CurrentMember UUID memberId, @PathVariable UUID id) {
        log.info("admin action={} target={} by={}", "refund-retry", id, memberId);
        RefundResult result = adminRefundService.retry(id);
        if (result.status() == RefundStatus.COMPLETED) {
            return ResponseEntity.ok(RefundResponse.from(result));
        } else {
            return ResponseEntity.accepted().body(RefundResponse.from(result));
        }
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<RefundResponse> closeRefund(@CurrentMember UUID memberId, @PathVariable UUID id, @Valid @RequestBody CloseRefundRequest request) {
        log.info("admin action={} target={} by={}", "refund-close", id, memberId);
        RefundResult result = adminRefundService.close(id, request.reason());
        return ResponseEntity.ok(RefundResponse.from(result));
    }
}
