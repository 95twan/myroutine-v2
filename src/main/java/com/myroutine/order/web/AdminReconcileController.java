package com.myroutine.order.web;

import com.myroutine.common.security.CurrentMember;
import com.myroutine.order.application.OrderReconcileService;
import com.myroutine.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/api")
public class AdminReconcileController {
    private final OrderReconcileService orderReconcileService;

    @PostMapping("/payments/{orderId}/reconcile")
    public ResponseEntity<ReconcileResponse> reconcilePayment(@CurrentMember UUID memberId, @PathVariable UUID orderId) {
        log.info("admin action={} target={} by={}", "payment-reconcile", orderId, memberId);
        OrderStatus status = orderReconcileService.reconcileOne(orderId);
        return ResponseEntity.ok(new ReconcileResponse(orderId, status));
    }
}
