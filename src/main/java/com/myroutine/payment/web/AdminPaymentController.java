package com.myroutine.payment.web;

import com.myroutine.common.web.CursorPage;
import com.myroutine.payment.application.AdminPaymentResult;
import com.myroutine.payment.application.AdminPaymentService;
import com.myroutine.payment.domain.PaymentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/api/payments")
@RequiredArgsConstructor
public class AdminPaymentController {
    private final AdminPaymentService adminPaymentService;

    @GetMapping
    public ResponseEntity<CursorPage<AdminPaymentResponse>> getPayments(
            @RequestParam PaymentStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "1 이상이어야 합니다.") @Max(value = 50, message = "50 이하여야 합니다.") int size
    ) {
        CursorPage<AdminPaymentResult> result = adminPaymentService.getPayments(status, cursor, size);
        List<AdminPaymentResponse> items = result.items().stream().map(AdminPaymentResponse::from).toList();
        return ResponseEntity.ok(new CursorPage<>(items, result.nextCursor()));
    }
}
