package com.myroutine.payment.api;

import com.myroutine.common.model.Money;

import java.util.Optional;
import java.util.UUID;

public interface PaymentApi {
    UUID begin(StartPayment command);                       // @Transactional. IN_PROGRESS 저장. 같은 orderId면 unique 위반
    PgOutcome callConfirm(UUID paymentId);                  // 트랜잭션 밖 전용. Toss 승인 호출만 하고 payment 상태는 바꾸지 않는다
    PaymentState applyOutcome(UUID paymentId, PgOutcome outcome);   // @Transactional. IN_PROGRESS·UNKNOWN일 때만 반영. 반환: 반영 후 현재 상태
    Optional<PaymentView> find(UUID orderId);
    PgOutcome queryPgOutcome(UUID orderId);      // 트랜잭션 밖 전용
    CancelOutcome cancel(UUID orderId, Money amount, String reason, String idempotencyKey);  // 트랜잭션 밖 전용
}
