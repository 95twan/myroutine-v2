package com.myroutine.payment.application;

import com.myroutine.common.model.Ids;
import com.myroutine.common.model.Money;
import com.myroutine.payment.api.*;
import com.myroutine.payment.domain.*;
import com.myroutine.payment.infrastructure.TossClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class PaymentApiImpl implements PaymentApi {

    private final PaymentRepository paymentRepository;
    private final PaymentCancelRepository paymentCancelRepository;
    private final TossClient tossClient;
    private final TransactionTemplate tx;
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
        Payment payment = tx.execute(status ->
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

    @Override
    public Optional<PaymentView> find(UUID orderId) {
        Optional<Payment> payment = paymentRepository.findByOrderId(orderId);

        return payment.map(p -> new PaymentView(
                p.getId(),
                PaymentState.valueOf(p.getStatus().name()),
                p.getAmount().amount(),
                p.getApprovedAt(),
                p.getFailureCode())
        );
    }

    @Override
    public PgOutcome queryPgOutcome(UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow(
                () -> new IllegalStateException("결제 정보를 찾을 수 없습니다.")
        );

        PgResult result = tossClient.queryByOrderId(payment.getId(), payment.getPgOrderId());
        return switch (result) {
            case PgResult.Approved approved ->
                    new PgOutcome(PgOutcomeType.APPROVED, null, approved.method(), approved.approvedAt());
            case PgResult.Rejected rejected -> new PgOutcome(PgOutcomeType.REJECTED, rejected.code(), null, null);
            case PgResult.Unknown unknown -> {
                tx.executeWithoutResult(s -> paymentRepository.increaseReconcileAttempts(payment.getId(), clock.instant()));
                yield new PgOutcome(PgOutcomeType.UNKNOWN, unknown.reason(), null, null);
            }
        };
    }

    @Override
    public CancelOutcome cancel(UUID orderId, Money amount, String reason, String idempotencyKey) {
        CancelTarget target = tx.execute(s -> {
            Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow(
                    () -> new IllegalStateException("결제 정보를 찾을 수 없습니다.")
            );
            paymentCancelRepository.insertIfAbsent(Ids.newId(), payment.getId(), idempotencyKey, amount.amount(), reason, clock.instant());
            PaymentCancel paymentCancel = paymentCancelRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(
                    () -> new IllegalStateException("결제 취소 정보를 찾을 수 없습니다.")
            );
            if (paymentCancel.getStatus() == PaymentCancelStatus.DONE) {
                return new CancelTarget(CancelOutcome.DONE, null, null, null);
            }
            if (paymentCancel.getStatus() == PaymentCancelStatus.FAILED) {
                return new CancelTarget(CancelOutcome.FAILED, null, null, null);
            }
            if (payment.getStatus() != PaymentStatus.APPROVED && payment.getStatus() != PaymentStatus.PARTIAL_CANCELLED) {
                throw new IllegalStateException("결제 상태가 취소 가능한 상태가 아닙니다.");
            }
            if (amount.isGreaterThan(payment.remainingAmount())) {
                throw new IllegalArgumentException("취소 가능한 금액을 초과했습니다.");
            }
            return new CancelTarget(null, paymentCancel.getId(), payment.getId(), payment.getPaymentKey());
        });

        if (target.replay() != null) {
            return target.replay();
        }

        PgResult result = tossClient.cancel(target.paymentId(), target.paymentKey(), amount, reason, idempotencyKey);

        return tx.execute(s -> {
            Instant now = clock.instant();
            return switch (result) {
                case PgResult.Approved _ -> {
                    int count = paymentCancelRepository.markDone(target.cancelId(), now);
                    if (count == 1) {
                        Payment payment = paymentRepository.findById(target.paymentId()).orElseThrow(
                                () -> new IllegalStateException("결제 정보를 찾을 수 없습니다.")
                        );
                        payment.applyCancel(amount);
                    }
                    yield CancelOutcome.DONE;
                }
                case PgResult.Rejected rejected -> {
                    paymentCancelRepository.markFailed(target.cancelId(), rejected.code(), now);
                    yield CancelOutcome.FAILED;
                }
                case PgResult.Unknown _ -> {
                    paymentCancelRepository.markUnknown(target.cancelId(), now);
                    yield CancelOutcome.UNKNOWN;
                }
            };
        });
    }
}
