package com.myroutine.payment.application;

import com.myroutine.common.web.Cursor;
import com.myroutine.common.web.CursorPage;
import com.myroutine.payment.domain.Payment;
import com.myroutine.payment.domain.PaymentRepository;
import com.myroutine.payment.domain.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminPaymentService {
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public CursorPage<AdminPaymentResult> getPayments(PaymentStatus status, String cursor, int size) {
        List<Payment> payments;
        if (cursor == null || cursor.isBlank()) {
            payments = paymentRepository.findByStatusFirstPage(status, Limit.of(size + 1));
        } else {
            Cursor current = Cursor.decode(cursor);
            payments = paymentRepository.findByStatusNextPage(status, current.createdAt(), current.id(), Limit.of(size + 1));
        }

        String nextCursor = null;
        if (payments.size() > size) {
            payments = payments.subList(0, size);
            Payment payment = payments.getLast();
            nextCursor = new Cursor(payment.getCreatedAt(), payment.getId()).encode();
        }

        List<AdminPaymentResult> items = payments.stream()
                .map(AdminPaymentResult::from)
                .toList();

        return new CursorPage<>(items, nextCursor);
    }
}
