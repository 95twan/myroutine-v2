package com.myroutine.payment.application;

import com.myroutine.payment.api.*;
import com.myroutine.payment.domain.Payment;
import com.myroutine.payment.domain.PaymentRepository;
import com.myroutine.payment.domain.PaymentStatus;
import com.myroutine.payment.domain.PgResult;
import com.myroutine.payment.infrastructure.TossClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class PaymentApiImpl implements PaymentApi {

    private final PaymentRepository paymentRepository;
    private final TossClient tossClient;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Override
    @Transactional
    public UUID begin(StartPayment command) {
        Payment payment = Payment.begin(
                command.orderId(),
                command.memberId(),
                command.amount(),
                command.pgOrderId(),
                command.paymentKey()
        );

        Payment saved = paymentRepository.save(payment);

        return saved.getId();
    }

    @Override
    public PgOutcome callConfirm(UUID paymentId) {
        Payment payment = transactionTemplate.execute(status ->
                paymentRepository.findById(paymentId).orElseThrow(
                        () -> new IllegalStateException("결제 정보를 찾을 수 없습니다.")
                )
        );
        PgResult result = tossClient.confirm(payment.getId(), payment.getPaymentKey(), payment.getPgOrderId(), payment.getAmount());

        return switch (result) {
            case PgResult.Approved approved ->
                    new PgOutcome(PgOutcomeType.APPROVED, null, approved.method(), approved.approvedAt());
            case PgResult.Rejected rejected -> new PgOutcome(PgOutcomeType.REJECTED, rejected.code(), null, null);
            case PgResult.Unknown unknown -> new PgOutcome(PgOutcomeType.UNKNOWN, unknown.reason(), null, null);
        };
    }

    @Override
    @Transactional
    public PaymentState applyOutcome(UUID paymentId, PgOutcome outcome) {
        List<PaymentStatus> allowed = List.of(PaymentStatus.IN_PROGRESS, PaymentStatus.UNKNOWN);
        switch (outcome.type()) {
            case APPROVED -> paymentRepository.markApproved(
                    paymentId,
                    allowed,
                    outcome.method(),
                    outcome.approvedAt(),
                    clock.instant()
            );
            case REJECTED -> paymentRepository.markFailed(
                    paymentId,
                    allowed,
                    outcome.code(),
                    clock.instant()
            );
            case UNKNOWN -> paymentRepository.markUnknown(
                    paymentId,
                    clock.instant()
            );
        }
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(
                () -> new IllegalStateException("결제 정보를 찾을 수 없습니다.")
        );

        return PaymentState.valueOf(payment.getStatus().name());
    }
}
