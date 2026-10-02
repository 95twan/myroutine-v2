# 03. 핵심 흐름 (As-Is)

> 다이어그램의 `⚠ID`는 [04-issues.md](04-issues.md)의 결함 ID.

## 1. 지갑 충전 (Toss)

```mermaid
sequenceDiagram
  participant C as Client
  participant P as payment
  participant R as Redis
  participant T as TossPayments
  participant K as Kafka
  participant W as wallet
  C->>P: POST /payments/request (orderId, amount)
  P->>R: SET payment:ready:{orderId} (TTL 10m)
  C->>T: 결제창 (테스트 client key 하드코딩 html)
  C->>P: POST /payments/confirm (paymentKey, orderId, amount)
  P->>R: 금액/회원 검증
  P->>P: Payment(PENDING) 저장 [별도 tx]
  P->>T: confirm
  alt 예외(타임아웃 포함)
    P->>P: markAsFailed  ⚠PAY-01 (Toss는 승인됐을 수 있음)
  else 성공
    P->>P: [tx] CONFIRMED + outbox(deposit, email) 저장
  end
  loop 1초 폴링
    P->>P: outbox SELECT FOR UPDATE SKIP LOCKED [tx 종료 → 락 해제]
    P->>K: deposit-event 발행  ⚠PAY-03
  end
  K->>W: WalletDepositEventConsumer
  W->>W: 로그 존재 확인 → 지갑 락 → 잔액 증가 + CHARGE 로그
```

## 2. 주문 생성 (예치금 결제)

`order-service/.../order/application/OrderService.java#create` — 메서드 전체가 하나의 `@Transactional`.

```mermaid
sequenceDiagram
  participant C as Client
  participant O as order (tx 열림)
  participant CA as catalog
  participant W as wallet
  C->>O: POST /orders (items[productId, qty, unitPrice, totalPrice])  ⚠ORD-01
  O->>CA: hold(orderId, items) — 조건부 UPDATE로 재고 차감, HELD
  O->>O: Order/OrderItem 저장 (총액 = 클라이언트 totalPrice 합)
  O->>W: withdraw(orderId, amount)  ⚠ORD-10
  alt 성공
    O->>O: markAsPaid
    O->>CA: commit (실패 시 로그만)  ⚠ORD-05
  else 실패
    O->>O: updateOrderStatus(PAYMENT_FAILED) — 외부 tx에 합류  ⚠ORD-03
    O->>CA: release (실패 시 로그만)  ⚠ORD-05
    O-->>C: 예외 → 주문 전체 롤백
  end
  O->>O: tx 커밋 — 여기서 실패하면 돈은 빠졌는데 주문 없음  ⚠ORD-02
```

## 3. 주문 취소 / 품목 환불

```mermaid
sequenceDiagram
  participant C as Client
  participant O as order
  participant W as wallet
  participant K as Kafka
  participant CA as catalog
  C->>O: PATCH /orders/{id}/cancel  (Order.status == PAID만 확인 ⚠ORD-06)
  O->>W: refund(orderId, order.totalAmount)
  O->>O: 품목 CANCELED, 주문 CANCELED
  O-->>K: AFTER_COMMIT stock-restore  ⚠ORD-09
  K->>CA: restoreBatch (processed_event로 멱등)

  C->>O: PATCH /orders/{id}/items/{productId}/refund
  O->>W: refund(orderId, order.totalAmount)  ⚠ORD-04 (품목 금액이 아닌 주문 전액)
  W->>W: withdrawLog PAID→REFUNDED (1회만 가능 → 다른 품목 환불 불가)
  O->>O: 품목 REFUNDED
  O-->>K: AFTER_COMMIT stock-restore
```

## 4. 구독 갱신 (배치)

```mermaid
sequenceDiagram
  participant B as batch (subscriptionOrderJob)
  participant O as order
  participant K as Kafka
  B->>O: GET /internal/subscriptions/batch/targets (ACTIVE/FAILED, nextRunDate=today)
  loop chunk 100
    B->>O: POST /api/v1/orders (subscriptionKey = UUID(subId:runDate), 구독 시점 가격  ⚠BAT-04)
    B->>K: subscription-order-batch-result (SUCCESS/RETRYABLE/PAYMENT_FAILED/UNAVAILABLE/NON_RETRYABLE)
  end
  K->>O: applyBatchResult
  O->>O: SUCCESS: nextRunDate 재계산, FAILED→ACTIVE / PAYMENT_FAILED: FAILED, 다음날 재시도 ...
  O-->>K: AFTER_COMMIT subscription-status-changed → support(알림)
```

## 5. 구매확정 → 정산

```mermaid
sequenceDiagram
  participant OS as order (OrderScheduler, 3분)
  participant S as shop
  participant B as batch (shopSettlementJob)
  participant W as wallet
  OS->>OS: 모든 품목 진행상태 일괄 전진 (시간 조건 없음 ⚠ORD-07)
  OS->>S: CONFIRMED 품목 → /internal/settlements/source (PENDING)
  B->>S: [shop schema 직접 접속 ⚠BAT-03] PENDING 집계 GROUP BY shop, offset 페이징
  B->>B: 수수료 5% → SettlementResult, source PENDING→COMPLETED  ⚠BAT-01, ⚠BAT-02
  B->>S: payout 요청
  S->>W: /internal/wallets/settle (settlementId로 멱등)
```

## 6. 가게 삭제 Saga (본인 구현)

`shop-service/.../shop/application/ShopService.java#deleteMyShop`, 설계: [shop_delete_saga_design.md](../references/original-saga/shop_delete_saga_design.md)

```mermaid
sequenceDiagram
  participant C as Client
  participant S as shop
  participant K as Kafka
  participant M as member
  C->>S: DELETE /shops/{id}
  S->>S: pg_advisory_xact_lock(hashtext(memberId))
  S->>S: ShopDeletion(REQUESTED) 저장
  alt 남은 가게 있음  ⚠SHOP-03
    S->>S: 즉시 COMPLETED, deletedAt 설정
  else 마지막 가게
    S->>S: outbox(MemberRoleChangeRequested REMOVE_SELLER)
  end
  S->>K: outbox poller (SKIP LOCKED, backoff 500ms~30s, key=memberId)
  K->>M: requested 소비
  alt 성공
    M->>M: SELLER 제거 + outbox(completed)
  else 비즈니스 실패
    M-->>K: failed (직접 발행)
  else 인프라 실패
    M->>M: 3회 재시도 → DLT → ShopDLTConsumer → dead 발행
  end
  K->>S: completed / failed / dead 소비 (REQUESTED에서만 전이 → 멱등)  ⚠SHOP-01 shop측 DLT 소비자 없음
  S-->>K: AFTER_COMMIT shop-deleted → catalog(상품 단종), order(구독 종료)  ⚠SHOP-05
```

가게 등록 Saga도 동일 구조(ADD_SELLER, ShopRegistration).

## 7. 회원 탈퇴
`MemberService.deleteMember`: 트랜잭션 안에서 wallet/order/shop Feign 3회 검증 + Redis 삭제 ⚠MEM-07 → `member-deleted` 발행 → shop은 `deleteAllMyShop`(Saga·락 우회 ⚠SHOP-04), catalog/order/wallet 각자 정리.
