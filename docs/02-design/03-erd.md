# 03. ERD (Stage 1)

공통 규칙
- PK: UUIDv7 `uuid`. 금액: `bigint`(원). 시각: `timestamptz`.
- 모든 테이블에 `created_at`, 변경 가능한 테이블에 `updated_at` (다이어그램에서는 생략).
- **FK는 같은 schema 안에서만** 건다. 다른 모듈 참조는 ID 값만 저장한다(Stage 3 분리 대비).
- 다이어그램의 `UK`는 unique 제약. 멱등성·불변식의 마지막 방어선이다.
- 동시성 제어 방식은 엔티티별로 표기했다: `version`(낙관적 락), 조건부 UPDATE, `FOR UPDATE`.

---

## member
```mermaid
erDiagram
  member ||--o{ social_account : has
  member ||--o{ member_address : has
  member ||--o{ inquiry : writes
  inquiry ||--o| inquiry_answer : has
  member {
    uuid id PK
    varchar email UK
    varchar nickname UK
    varchar name
    varchar phone
    varchar password_hash "BCrypt, OAuth 전용 회원은 null"
    timestamptz email_verified_at "null = 이메일 미인증"
    varchar status "ACTIVE, BANNED, WITHDRAWN"
    jsonb roles "USER, SELLER, ADMIN"
    int token_version "토큰 즉시 무효화"
    timestamptz withdrawn_at
  }
  social_account {
    uuid id PK
    uuid member_id FK
    varchar provider "KAKAO, GOOGLE, NAVER"
    varchar provider_user_id "UK(provider, provider_user_id)"
  }
  member_address {
    uuid id PK
    uuid member_id FK
    varchar recipient
    varchar phone
    varchar zipcode
    varchar address1
    varchar address2
    boolean is_default
  }
  inquiry {
    uuid id PK
    uuid member_id FK
    varchar title
    text content
    varchar status "OPEN, ANSWERED"
  }
  inquiry_answer {
    uuid id PK
    uuid inquiry_id FK
    uuid admin_id
    text content
  }
```
Redis: `auth:email-code:{email}`(코드, 시도 횟수, TTL 5분), `auth:email-rate:{email}`, `auth:session:{sessionId}`(memberId, familyId, refreshHash, 기기), `auth:token-version:{memberId}`, `auth:login-fail:{email}`(연속 실패 횟수, TTL 15분).
- 한 회원은 비밀번호(`password_hash`)와 소셜 계정(`social_account`)을 함께 가질 수 있다. 둘 다 없는 회원은 없다.
- 이메일 미인증 회원은 주문·결제·가게 개설 API를 쓸 수 없다(FR-MEM-09).
- Redis는 로드맵 Part 4에서 도입한다. 그 전에는 로그인 실패 제한·refresh 세션 없이 Access 토큰만 쓴다.

## shop
```mermaid
erDiagram
  shop {
    uuid id PK
    uuid member_id "회원 ID (타 모듈)"
    varchar name
    varchar business_number UK
    varchar email
    varchar phone
    varchar address
    varchar status "ACTIVE, CLOSED"
    timestamptz closed_at
    bigint version
  }
```
- 개설 즉시 ACTIVE + `shop.shop-opened` 이벤트 → member가 SELLER 부여(최종적 일관성, 소유권 기반 인가라 지연돼도 무방).
- 폐업은 같은 회원의 개설·폐업을 `pg_advisory_xact_lock(memberId)`로 직렬화한다(원 프로젝트 방식 계승).

## product
```mermaid
erDiagram
  product ||--o{ product_image : has
  product ||--|| stock : has
  product ||--o{ price_history : has
  stock ||--o{ stock_reservation : reserves
  stock ||--o{ stock_movement : logs
  product {
    uuid id PK
    uuid shop_id "가게 ID (타 모듈)"
    varchar name
    text description
    varchar category
    bigint price
    varchar status "ON_SALE, HIDDEN, DISCONTINUED"
    boolean subscribable
    varchar thumbnail_key
    bigint version
  }
  product_image {
    uuid id PK
    uuid product_id FK
    varchar object_key
    int sort_order
  }
  price_history {
    uuid id PK
    uuid product_id FK
    bigint old_price
    bigint new_price
    timestamptz changed_at
  }
  stock {
    uuid product_id PK
    int available "가용"
    int reserved "예약중"
    int sold "확정 판매 누계"
    int received "입고 누계(조정 포함)"
  }
  stock_reservation {
    uuid id PK
    uuid order_id "UK(order_id, product_id)"
    uuid product_id FK
    int quantity
    varchar status "HELD, COMMITTED, RELEASED, EXPIRED"
    timestamptz expires_at
  }
  stock_movement {
    uuid id PK
    uuid product_id FK
    varchar type "RECEIVE, ADJUST, RESERVE, RELEASE, COMMIT, RESTORE"
    int quantity
    varchar ref_type
    uuid ref_id "UK(type, ref_type, ref_id, product_id)"
  }
```
- 예약: `UPDATE stock SET available = available - :q, reserved = reserved + :q WHERE product_id = :id AND available >= :q` (원자적 조건부 UPDATE, As-Is 방식 계승). 반영된 행 수가 0이면 재고 부족.
- 수량 전이: 예약 `available→reserved`, 확정 `reserved→sold`, 해제·만료 `reserved→available`, 환불 복구 `sold→available`, 입고·조정 `received, available` 동시 증감.
- INV-03: 항상 `available + reserved + sold = received`, 각 값 ≥ 0. 정합성 점검 잡이 `stock`과 `stock_movement` 합계를 비교한다.
- `stock_movement`의 unique 제약이 예약·복구의 멱등을 보장한다(같은 주문의 같은 상품 복구는 1회).

## order (schema: orders)
```mermaid
erDiagram
  cart ||--o{ cart_item : has
  orders ||--|{ shop_order : splits
  shop_order ||--|{ order_line : has
  orders ||--o{ refund : has
  order_line ||--o{ refund : "refunded by"
  subscription ||--o{ subscription_cycle : runs
  subscription_cycle |o--o| orders : creates
  cart {
    uuid id PK
    uuid member_id UK
  }
  cart_item {
    uuid id PK
    uuid cart_id FK
    uuid product_id "UK(cart_id, product_id)"
    int quantity
  }
  orders {
    uuid id PK
    varchar order_number UK "사용자 노출용"
    uuid member_id
    varchar type "NORMAL, SUBSCRIPTION"
    varchar status "PENDING_PAYMENT, PAYMENT_IN_PROGRESS, PAID, PAYMENT_FAILED, EXPIRED"
    bigint total_amount "INV-01"
    bigint wallet_amount "예치금 사용"
    bigint pg_amount "PG 결제"
    jsonb shipping_address "스냅샷"
    timestamptz expires_at
    timestamptz paid_at
    bigint version
  }
  shop_order {
    uuid id PK
    uuid order_id FK
    uuid shop_id "UK(order_id, shop_id)"
    varchar status "PAID, SHIPPED, DELIVERED, COMPLETED, CANCELLED"
    varchar carrier
    varchar tracking_number
    timestamptz shipped_at
    timestamptz delivered_at
    bigint version
  }
  order_line {
    uuid id PK
    uuid shop_order_id FK
    uuid product_id
    varchar product_name "스냅샷"
    varchar thumbnail_key "스냅샷"
    bigint unit_price "서버 확정가 스냅샷"
    int quantity
    bigint line_amount "unit_price x quantity"
    bigint refunded_amount "INV-06"
    varchar status "PENDING, PAID, SHIPPED, DELIVERED, CONFIRMED, CANCELLED, RETURN_REQUESTED, RETURNED"
    timestamptz confirmed_at
    bigint version
  }
  refund {
    uuid id PK
    uuid order_id FK
    uuid order_line_id FK
    varchar reason_type "BUYER_CANCEL, RETURN, SYSTEM"
    bigint amount
    bigint pg_amount "POL-08 배분"
    bigint wallet_amount
    varchar status "REQUESTED, APPROVED, PG_CANCELLED, COMPLETED, REJECTED, FAILED"
    text reject_reason
  }
  subscription {
    uuid id PK
    uuid member_id
    uuid shop_id
    uuid product_id
    int quantity
    varchar cycle_type "WEEKLY, MONTHLY"
    int cycle_value "요일 1-7 또는 일 1-28"
    jsonb shipping_address
    uuid billing_key_id
    boolean use_wallet_first
    bigint unit_price "현재 적용가"
    bigint pending_unit_price "인상 예정가"
    date pending_effective_date
    varchar status "ACTIVE, PAUSED, SUSPENDED, CANCELLED, TERMINATED"
    date next_run_date
    int consecutive_failures
    bigint version
  }
  subscription_cycle {
    uuid id PK
    uuid subscription_id "UK(subscription_id, run_date) INV-08"
    date run_date
    uuid order_id
    bigint applied_unit_price
    varchar status "SCHEDULED, ORDERED, PAID, FAILED, SKIPPED"
    int attempt
    text failure_reason
  }
```
- 주문 만료 스윕: `WHERE status = 'PENDING_PAYMENT' AND expires_at < now()` → 인덱스 `(status, expires_at)`.
- 가게주문 조회: 인덱스 `shop_order(shop_id, status, created_at)` (must #1·2).
- 자동 구매확정: 인덱스 `shop_order(status, delivered_at)`.
- 매월 일자는 1~28로 제한해 월말 문제를 피한다 [제안].

## payment
```mermaid
erDiagram
  payment ||--o{ payment_cancel : has
  billing_key ||--o{ payment : "used by"
  payment {
    uuid id PK
    varchar purpose "ORDER, WALLET_CHARGE"
    uuid reference_id "UK(purpose, reference_id) 주문 또는 충전 ID"
    uuid member_id
    bigint amount
    bigint cancelled_amount "INV-06"
    varchar pg_order_id UK "PG 측 orderId"
    varchar payment_key UK "PG 결제 키"
    uuid billing_key_id
    varchar method
    varchar status "IN_PROGRESS, APPROVED, FAILED, UNKNOWN, PARTIAL_CANCELLED, CANCELLED"
    timestamptz approved_at
    int reconcile_attempts
    timestamptz next_reconcile_at
    bigint version
  }
  payment_cancel {
    uuid id PK
    uuid payment_id FK
    varchar idempotency_key "UK refundId 등"
    bigint amount
    varchar status "REQUESTED, DONE, FAILED, UNKNOWN"
    varchar reason
  }
  billing_key {
    uuid id PK
    uuid member_id
    varchar customer_key
    varchar billing_key "암호화 저장"
    varchar card_summary "마스킹"
    varchar status "ACTIVE, DELETED"
  }
  wallet_charge {
    uuid id PK
    uuid member_id
    bigint amount
    varchar status "PENDING, CHARGED, CANCELLED, FAILED"
  }
  pg_call_log {
    uuid id PK
    uuid payment_id
    varchar operation "CONFIRM, CANCEL, QUERY, BILLING"
    int http_status
    text request_summary
    text response_summary
    int latency_ms
  }
```
- `(purpose, reference_id)` unique: 주문 1건당 결제 1건 (FR-ORD-06, INV-02).
- `pg_call_log`: PG 호출 감사 로그. 대사·장애 분석용.
- 대사 대상 인덱스: `(status, next_reconcile_at)`.

## wallet
```mermaid
erDiagram
  wallet ||--o{ ledger_entry : records
  wallet ||--o{ wallet_hold : holds
  wallet ||--o{ withdrawal : requests
  wallet ||--o| bank_account : has
  wallet {
    uuid id PK
    uuid member_id UK
    bigint balance "= Σledger, >= 0"
    bigint held "보류 합계, <= balance"
    varchar status "ACTIVE, CLOSED"
  }
  ledger_entry {
    uuid id PK
    uuid wallet_id FK
    varchar type "CHARGE, CHARGE_CANCEL, ORDER_PAYMENT, REFUND, SETTLEMENT, WITHDRAWAL, WITHDRAWAL_REVERT"
    bigint amount "부호 포함"
    bigint balance_after
    varchar ref_type
    uuid ref_id "UK(type, ref_type, ref_id) 멱등"
  }
  wallet_hold {
    uuid id PK
    uuid wallet_id FK
    uuid order_id UK
    bigint amount
    varchar status "HELD, CAPTURED, RELEASED"
  }
  withdrawal {
    uuid id PK
    uuid wallet_id FK
    bigint amount
    jsonb bank_account_snapshot
    varchar status "REQUESTED, PROCESSING, COMPLETED, FAILED, UNKNOWN"
    varchar bank_transaction_id
  }
  bank_account {
    uuid id PK
    uuid wallet_id FK
    varchar bank_code
    varchar account_number "암호화"
    varchar holder_name
  }
```
- 잔액 변경은 `SELECT ... FOR UPDATE`로 지갑 행을 잠근 뒤, 원장 기록과 잔액 갱신을 같은 트랜잭션에서 한다(As-Is 방식 유지 + 원장 일원화).
- 가용잔액 = `balance - held`. 보류는 `held += amount`, 확정(capture)은 `balance -= amount, held -= amount` + 원장 `ORDER_PAYMENT`, 해제는 `held -= amount`.
- 원장 unique 제약 → 같은 환불·정산·충전이 두 번 반영되지 않는다 (WAL-01 대응: 중복 요청은 기존 결과 반환).
- 정합성 점검 잡: `wallet.balance = Σ ledger_entry.amount` (INV-05).

## settlement
```mermaid
erDiagram
  settlement ||--o{ settlement_item : includes
  settlement_item {
    uuid id PK
    uuid order_line_id UK "INV-07"
    uuid shop_id
    uuid order_id
    bigint amount "구매확정 금액 = line_amount - refunded_amount"
    timestamptz confirmed_at
    uuid settlement_id FK "null = 미정산"
  }
  settlement {
    uuid id PK
    uuid shop_id "UK(shop_id, period_start)"
    date period_start
    date period_end
    bigint gross_amount
    int fee_rate_bp "수수료 basis point (500 = 5%)"
    bigint fee_amount
    bigint net_amount
    int item_count
    varchar status "CALCULATED, PAID, PAYOUT_FAILED"
    timestamptz paid_at
  }
```
- `order.order-line-confirmed` 이벤트 소비 → `settlement_item` 적재 (`order_line_id` unique로 멱등).
- 정산 실행: 가게 ID keyset 순회 → 가게별 한 트랜잭션에서 `settlement` 생성 + 해당 기간 미정산 item에 `settlement_id` 설정 (BAT-02 offset 누락 문제 제거).
- 지급: wallet API `deposit(type=SETTLEMENT, refId=settlementId)` — 원장 unique로 멱등.
- 인덱스: `settlement_item(shop_id, confirmed_at) WHERE settlement_id IS NULL` (부분 인덱스).

## review
```mermaid
erDiagram
  review ||--o{ review_like : has
  review {
    uuid id PK
    uuid order_line_id UK "품목당 1개"
    uuid product_id
    uuid member_id
    int rating "1-5"
    text content
    int like_count
    varchar status "ACTIVE, DELETED"
    vector embedding "1536, nullable"
    varchar embedding_status "PENDING, DONE, FAILED"
  }
  review_like {
    uuid review_id PK
    uuid member_id PK
  }
  product_review_stat {
    uuid product_id PK
    int review_count
    bigint rating_sum
    int r1
    int r2
    int r3
    int r4
    int r5
  }
  review_summary {
    uuid id PK
    uuid product_id "UK(product_id, period_end)"
    date period_end
    text summary
    int source_review_count
    varchar model
  }
```
- 통계는 리뷰 작성 트랜잭션에서 조건부 UPDATE로 증감(`review_count = review_count + 1`).
- 좋아요: `review_like` PK로 중복 방지 + `like_count` 원자적 증감.

## recommendation
```mermaid
erDiagram
  product_embedding {
    uuid product_id PK
    vector embedding "1536"
    varchar model
    varchar source_hash "본문 해시, 변경 시에만 재임베딩"
    varchar status "ACTIVE, INACTIVE"
  }
```
- 취향 벡터 캐시: Redis `rec:taste:{memberId}`(TTL 30분).

## notification
```mermaid
erDiagram
  notification {
    uuid id PK
    uuid member_id
    varchar type "ORDER_PAID, SHIPPED, SUBSCRIPTION_FAILED, PRICE_CHANGED, ..."
    varchar channel "EMAIL"
    varchar dedup_key UK "이벤트ID + 수신자"
    jsonb payload
    varchar status "PENDING, SENT, FAILED"
    int attempts
    text last_error
    timestamptz next_attempt_at
  }
```

## common
```mermaid
erDiagram
  outbox_event {
    uuid id PK
    varchar aggregate_type
    uuid aggregate_id "Kafka key"
    varchar event_type
    varchar topic
    jsonb payload
    varchar status "READY, PROCESSING, SENT, DEAD"
    int attempts
    timestamptz next_attempt_at
    timestamptz locked_until "리스 만료 시 회수"
    timestamptz sent_at
    varchar trace_id "저장 시점 MDC traceId → 봉투로 전달"
  }
  processed_message {
    varchar consumer PK
    uuid event_id PK
    timestamptz processed_at
  }
  idempotency_key {
    varchar key PK
    uuid member_id PK
    varchar request_hash
    varchar status "PROCESSING, COMPLETED"
    int response_status
    jsonb response_body
    timestamptz expires_at
  }
```
- 같은 키 동시 요청: `idempotency_key` INSERT 충돌 → PROCESSING이면 409, COMPLETED면 저장된 응답 반환. requestHash가 다르면 422.
- Outbox 정리: SENT 7일 경과분 삭제 (Stage 2-7에서 파티셔닝 후보).
