# Part 5. 구독

> 버전 0.5 · 2026-10-02 · **덜어내기**: 회차 선점(PROCESSING 리스·재시도 회차·시도 수) 제거 — 회차 INSERT와 주문 생성을 한 트랜잭션으로
> 0.4 · 2026-10-02 · 예치금 제거: 구독의 "예치금 우선 사용" 옵션 삭제, 회차 결제는 항상 빌링
> 0.3 · 이 문서만 보고 개발할 수 있게 구체화(빌링·구독·회차의 구현 규격과 테스트 케이스)

> **끝나면**: 구매자가 상품을 정기 구독하고, 매 회차 빌링키로 자동 결제되어 주문이 생긴다. 같은 날 잡이 두 번 돌아도 결제는 한 번이다.
> **인프라 추가**: 없음 (Toss 빌링 API, 테스트는 가짜 PG 서버)
> **릴리스**: `v0.5.0`

| 단계 | 제목 | 크기 |
|---|---|---|
| 5-1 | 빌링키 등록 · 조회 · 삭제 | M |
| 5-2 | 구독 신청 · 관리 | M |
| 5-3 | 구독 회차 실행 잡 | L |
| 5-4 | 가격 변경 · 종료 이벤트 반영 | M |

---

## Part 5 공통 규칙 (Part 1~4 공통 규칙에 더해서)

### V. 구독 날짜 규칙
- 날짜는 모두 **업무 날짜**(`LocalDate`, Asia/Seoul)다. "오늘" = `LocalDate.now(clock.withZone(businessZone))`.
- 주기: `WEEKLY` + 요일 1~7(월=1, `DayOfWeek.getValue()`), `MONTHLY` + 일 1~28(29~31일은 달마다 없을 수 있어 막는다).
- 날짜 계산은 `SubscriptionSchedule` 값 객체 한 곳에서만 한다(서비스·잡에 날짜 계산 코드 금지).

### W. Part 5 에러 코드

| 코드 | HTTP | 메시지 | 정의 위치 | 단계 |
|---|---|---|---|---|
| `BILLING_KEY_NOT_FOUND` | 404 | 결제수단을 찾을 수 없습니다. | `PaymentErrorCode` | 5-1 |
| `BILLING_KEY_ISSUE_FAILED` | 422 | 카드 등록에 실패했습니다. | `PaymentErrorCode` | 5-1 |
| `BILLING_KEY_IN_USE` | 409 | 사용 중인 구독이 있어 삭제할 수 없습니다. | `OrderErrorCode` | 5-1 |
| `SUBSCRIPTION_NOT_FOUND` | 404 | 구독을 찾을 수 없습니다. | `OrderErrorCode` | 5-2 |
| `PRODUCT_NOT_SUBSCRIBABLE` | 422 | 구독할 수 없는 상품입니다. | `OrderErrorCode` | 5-2 |

---

## 5-1. 빌링키 등록 · 조회 · 삭제 (M)

**목표**: 카드를 빌링키로 등록해 두고 구독 결제에 쓴다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| Toss 빌링(자동결제) | 카드 인증으로 받은 `authKey`를 서버가 빌링키로 바꾸고, 이후 빌링키로 결제를 요청한다 |
| 대칭키 암호화 (AES-GCM) | 빌링키는 결제를 일으킬 수 있는 비밀이라 DB에 암호화해 저장한다. JDK `javax.crypto`만 쓴다 |

**Toss 빌링 API 요약** (확인 필요: 구현 전에 Toss 문서와 대조)

| 작업 | 요청 | 성공 응답 |
|---|---|---|
| 빌링키 발급 | `POST /v1/billing/authorizations/issue` `{authKey, customerKey}` | `{billingKey, customerKey, cardCompany, card: {number(마스킹)}}` |
| 빌링 결제 | `POST /v1/billing/{billingKey}` `{customerKey, amount, orderId, orderName}` + `Idempotency-Key` | Payment 객체 `status: "DONE"` |

**정책 (이 단계에서 정함)**
- `customerKey`는 빌링키마다 새로 만든 랜덤 UUID(추측할 수 없게).
- 활성 구독(ACTIVE·PAUSED·SUSPENDED)이 쓰는 빌링키는 삭제 불가(OPEN-04).
- 삭제는 상태만 DELETED로 바꾼다(Toss 쪽 삭제 API는 쓰지 않음, 확인 필요).
- 발급 결과가 불확실(타임아웃)하면 502로 응답하고 사용자가 다시 등록한다(돈이 오가지 않으므로 대사하지 않음).

### 할 일

**1) common.crypto**
- `AesGcmEncryptor` (`@Component`): 키는 `myroutine.crypto.billing-key-secret` (환경변수 `BILLING_KEY_SECRET`, Base64 32바이트, 생성 `openssl rand -base64 32`). `String encrypt(String plain)` → `Base64(iv 12바이트 + 암호문)`, `String decrypt(String encoded)`. IV는 매번 `SecureRandom`으로 새로
- 테스트 프로필에는 테스트 전용 키를 적는다

**2) 마이그레이션** — `db/migration/payment/V{...}__payment_create_billing_key.sql`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_billing_key` |
| member_id | uuid | X | 인덱스 `idx_billing_key_member_id` |
| customer_key | varchar(64) | X | |
| billing_key_encrypted | varchar(500) | X | |
| card_summary | varchar(100) | X | 예: `신한 ****1234` |
| status | varchar(20) | X | `ck_billing_key_status`: `IN ('ACTIVE','DELETED')` |
| deleted_at | timestamptz | O | |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

**3) TossClient 추가** (2-3 분류표 그대로)
- `BillingIssueResult issueBillingKey(String authKey, String customerKey)` → `sealed interface BillingIssueResult { Issued(String billingKey, String cardSummary); Rejected(String code); Unknown(String reason) }`
- `PgResult billingCharge(UUID paymentId, String billingKey, String customerKey, String pgOrderId, Money amount, String orderName)` — `Idempotency-Key: paymentId`
- `pg_call_log`의 operation: `BILLING_ISSUE`, `BILLING`. **request_summary에 빌링키·authKey를 넣지 않는다**
- `FakePgServer`에 두 경로의 기본 동작 추가: 발급은 `{billingKey: "bk_" + 랜덤, card: {number: "1234****5678"}, cardCompany: "신한"}`, 결제는 승인 경로와 같은 방식(승인 기록·멱등)

**4) payment**

| 대상 | 규격 |
|---|---|
| `BillingKey` (entity) | `static BillingKey register(UUID memberId, String customerKey, String encryptedKey, String cardSummary)` → ACTIVE, `void delete(Instant now)` |
| `BillingKeyService` | `BillingKeyResult register(UUID memberId, String authKey)` (`@Transactional` 없음: 발급 호출은 트랜잭션 밖 → 저장은 짧은 트랜잭션) / `@Transactional(readOnly = true) List<BillingKeyResult> list(UUID memberId)` (ACTIVE만) / `@Transactional void delete(UUID memberId, UUID id)` |
| `BillingKeyResult` | `record (UUID id, String cardSummary, Instant createdAt)` — 빌링키·customerKey는 절대 포함하지 않는다 |
| `payment.api.BillingKeyDeletionPrecondition` | `interface { void check(UUID billingKeyId); }` — 구독은 order 데이터라 payment가 직접 볼 수 없다(의존 역전, 3-2와 같은 방식). order가 구현 |
| `PaymentApi` 추가 | `BillingKeyInfo getActiveBillingKey(UUID memberId, UUID billingKeyId)` (없거나 남의 것·DELETED면 `BILLING_KEY_NOT_FOUND`), `record BillingKeyInfo(UUID id, String cardSummary)` |

- `register`: `Rejected` → `BILLING_KEY_ISSUE_FAILED`, `Unknown` → `PG_ERROR`
- `delete`: 소유 확인(아니면 404) → `preconditions.forEach(p -> p.check(id))` → `delete(now)`
- `OrderBillingKeyDeletionPrecondition` (order.application): 이 빌링키를 쓰는 ACTIVE·PAUSED·SUSPENDED 구독이 있으면 `BILLING_KEY_IN_USE` (5-2에서 구독 테이블이 생기면 동작. 5-1 시점에는 클래스만 두고 5-2에서 쿼리를 채운다)
- `member-withdrawn` consumer (payment.infrastructure, consumer `payment.member-withdrawn`): 회원의 ACTIVE 빌링키 전부 DELETED

**5) API** — `BillingKeyController` (`/api/billing-keys`)

| API | 요청 | 응답 |
|---|---|---|
| `POST /api/billing-keys` | `RegisterBillingKeyRequest(@NotBlank String authKey)` | 201 `BillingKeyResponse(id, cardSummary, createdAt)` |
| `GET /api/billing-keys` | | 200 `List<BillingKeyResponse>` |
| `DELETE /api/billing-keys/{id}` | | 204 |

### 완료 확인

| 테스트 클래스 | 케이스 (가짜 PG 서버) |
|---|---|
| `common/crypto/AesGcmEncryptorTest` | [ ] 암호화 → 복호화 왕복 / [ ] 같은 평문을 두 번 암호화하면 결과가 다르다(IV) / [ ] 암호문 한 글자 변조 → 예외 |
| `payment/infrastructure/TossBillingClientTest` | [ ] 발급 성공·거절·타임아웃 3분류 / [ ] 빌링 결제 승인·거절·타임아웃·끊김 3분류 / [ ] `pg_call_log`에 빌링키·authKey 원문이 없다 |
| `payment/web/BillingKeyControllerTest` | [ ] 등록 → DB에 원문이 없고(`billing_key_encrypted` ≠ 발급값) 복호화하면 같다 / [ ] 응답 JSON에 `billingKey`, `customerKey` 필드가 없다 / [ ] 남의 빌링키 삭제 → 404 / [ ] 탈퇴 이벤트 → DELETED |

**리뷰 때 물어볼 것**
- 암호화 키는 어디서 관리하나? 운영이라면?
- 빌링키 발급 결과가 불확실할 때 대사하지 않아도 되는 이유는?

---

## 5-2. 구독 신청 · 관리 (M)

**목표**: 구독 신청(수량, 매주 N요일 / 매월 N일, 배송지, 빌링키), 조회·수정·일시정지·재개·해지. **본인 구독만** 다룰 수 있다.

**정책 (이 단계에서 정함)**
- 시작일은 **내일 이후**. 첫 회차는 시작일 이후 주기에 맞는 첫 날.
- 신청 시점의 상품 가격을 구독 가격(`unit_price`)으로 저장한다.
- 구독 가능(`subscribable`)·판매 중 상품만, 자기 가게 상품 불가, 이메일 인증 필요.
- 재개(PAUSED·SUSPENDED → ACTIVE) 시 다음 실행일은 **내일 이후** 첫 주기 날짜로 다시 계산하고 연속 실패 수를 0으로.
- 해지(CANCELLED)·종료(TERMINATED)된 구독은 수정·재개 불가.
- 폐업·탈퇴 조건: 활성 구독(ACTIVE·PAUSED·SUSPENDED)이 있으면 가게 폐업(POL-15)·회원 탈퇴(POL-16) 불가. 판매자는 구독 상품을 **단종**하면 구독이 종료되므로(5-4), 폐업 전에 상품을 단종하면 된다.

### 할 일

**1) 마이그레이션** — `db/migration/order/V{...}__order_create_subscription.sql`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_subscription` |
| member_id, shop_id, product_id | uuid | X | |
| quantity | int | X | `BETWEEN 1 AND 99` |
| cycle_type | varchar(20) | X | `ck_subscription_cycle_type`: `IN ('WEEKLY','MONTHLY')` |
| cycle_value | int | X | `ck_subscription_cycle_value`: `(cycle_type = 'WEEKLY' AND cycle_value BETWEEN 1 AND 7) OR (cycle_type = 'MONTHLY' AND cycle_value BETWEEN 1 AND 28)` |
| shipping_address | jsonb | X | 스냅샷 |
| billing_key_id | uuid | X | |
| unit_price | bigint | X | `> 0` |
| pending_unit_price | bigint | O | 인상 예정가 (5-4) |
| pending_effective_date | date | O | |
| price_changed_at | timestamptz | O | 마지막으로 반영한 가격 이벤트 시각 (5-4) |
| status | varchar(20) | X | `ck_subscription_status`: `IN ('ACTIVE','PAUSED','SUSPENDED','CANCELLED','TERMINATED')` |
| next_run_date | date | X | |
| consecutive_failures | int | X | DEFAULT 0 |
| end_reason | varchar(50) | O | `USER_CANCEL`, `PRODUCT_DISCONTINUED`, `SHOP_CLOSED`, `MEMBER_WITHDRAWN` |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- 인덱스: `idx_subscription_status_next_run_date (status, next_run_date, id)`, `idx_subscription_member_id_created_at (member_id, created_at DESC, id DESC)`, `idx_subscription_product_id_status (product_id, status)`, `idx_subscription_shop_id_status (shop_id, status)`, `idx_subscription_billing_key_id (billing_key_id)`

**2) order.domain**

| 대상 | 규격 |
|---|---|
| `CycleType` | `WEEKLY, MONTHLY` |
| `SubscriptionSchedule` | `record (CycleType type, int value)`. compact 생성자에서 범위 검사. `LocalDate firstOnOrAfter(LocalDate date)`: date 포함, 주기에 맞는 첫 날 / `LocalDate nextAfter(LocalDate date)`: date 제외, 다음 주기 날 — **단위 테스트 핵심** |
| `SubscriptionStatus` | 전이: `ACTIVE → {PAUSED, SUSPENDED, CANCELLED, TERMINATED}`, `PAUSED → {ACTIVE, CANCELLED, TERMINATED}`, `SUSPENDED → {ACTIVE, CANCELLED, TERMINATED}` |
| `Subscription` (애그리거트 루트) | `@Entity @Table(name = "subscription", schema = "orders")`, `extends BaseTimeEntity`. `@Embedded`가 아니라 `cycleType`·`cycleValue` 컬럼 두 개 + `SubscriptionSchedule schedule()` 메서드 |
| `SubscriptionRepository` | `save`, `findByIdAndMemberId`, `existsByBillingKeyIdAndStatusIn`, `existsByShopIdAndStatusIn`, `existsByMemberIdAndStatusIn`, `List<Subscription> findAllByProductIdAndStatusIn`, `List<Subscription> findAllByShopIdAndStatusIn`, `List<Subscription> findAllByMemberIdAndStatusIn`, 내 목록·판매자 목록 keyset 조회 |
| `OrderErrorCode` | `SUBSCRIPTION_NOT_FOUND`, `PRODUCT_NOT_SUBSCRIBABLE` |

`Subscription` 메서드
- `static Subscription subscribe(UUID memberId, UUID shopId, UUID productId, int quantity, SubscriptionSchedule schedule, ShippingAddress address, UUID billingKeyId, Money unitPrice, LocalDate startDate)` → ACTIVE, `nextRunDate = schedule.firstOnOrAfter(startDate)`
- `void pause()`, `void resume(LocalDate today)` (`nextRunDate = schedule.firstOnOrAfter(today + 1)`, `consecutiveFailures = 0`), `void cancel()`(end_reason USER_CANCEL), `void terminate(String reason)`(이미 CANCELLED·TERMINATED면 아무것도 안 함 — 멱등)
- `void change(Integer quantity, ShippingAddress address, UUID billingKeyId)`: null은 유지. CANCELLED·TERMINATED면 `INVALID_STATE_TRANSITION`
- `boolean isActiveLike()` → ACTIVE·PAUSED·SUSPENDED

**3) order.application** — `SubscriptionService`

| 메서드 | 규격 |
|---|---|
| `@Transactional UUID subscribe(UUID memberId, SubscribeCommand command)` | 시작일 ≤ 오늘이면 `INVALID_REQUEST` → `products = productApi.getPurchasable(List.of(productId))` → `subscribable == false`면 `PRODUCT_NOT_SUBSCRIBABLE` → `shopApi.requireActiveShops` → 가게 주인이 나면 `OWN_SHOP_PRODUCT` → `memberApi.getAddress` → `paymentApi.getActiveBillingKey` → `Subscription.subscribe(...)` |
| `@Transactional(readOnly = true)` `getMine(memberId, cursor, size)`, `getOne(memberId, id)` | `findByIdAndMemberId` 없으면 `SUBSCRIPTION_NOT_FOUND` (남의 것도 404, As-Is ORD-08) |
| `@Transactional SubscriptionResult change(UUID memberId, UUID id, ChangeSubscriptionCommand command)` | addressId가 있으면 `memberApi.getAddress`로 새 스냅샷, billingKeyId가 있으면 `getActiveBillingKey`로 확인 |
| `@Transactional` `pause`, `resume`, `cancel` (memberId, id) | 각 도메인 메서드 |

- `SubscribeCommand(UUID productId, int quantity, CycleType cycleType, int cycleValue, UUID addressId, UUID billingKeyId, LocalDate startDate)`
- `SubscriptionResult(UUID id, UUID productId, UUID shopId, int quantity, CycleType cycleType, int cycleValue, ShippingAddress shippingAddress, UUID billingKeyId, long unitPrice, Long pendingUnitPrice, LocalDate pendingEffectiveDate, SubscriptionStatus status, LocalDate nextRunDate, int consecutiveFailures, Instant createdAt)`

조건 추가
- `OrderShopClosePrecondition`(3-2): 활성 구독이 있으면 `SHOP_HAS_ACTIVE_ORDERS` + `details.reason = "ACTIVE_SUBSCRIPTION"`
- `OrderWithdrawalPrecondition`(3-6): 활성 구독이 있으면 `"ACTIVE_SUBSCRIPTION"` 추가
- `OrderBillingKeyDeletionPrecondition`(5-1): `existsByBillingKeyIdAndStatusIn(id, 활성)`

**4) order.web** — `SubscriptionController` (`/api/subscriptions`)

| API | 요청 | 응답 |
|---|---|---|
| `POST /api/subscriptions` `@Idempotent` `@RequireVerifiedEmail` | `SubscribeRequest(@NotNull productId, @Min(1) @Max(99) quantity, @Valid cycle {@NotNull type, @NotNull value}, @NotNull addressId, @NotNull billingKeyId, @NotNull @Future LocalDate startDate)` — [API 명세 §3.5](../02-design/07-api-spec.md) | 201 `SubscriptionIdResponse(subscriptionId)` |
| `GET /api/subscriptions?cursor=&size=` | | 200 `CursorPage<SubscriptionResponse>` |
| `GET /api/subscriptions/{id}` | | 200 `SubscriptionResponse` |
| `PATCH /api/subscriptions/{id}` | `ChangeSubscriptionRequest(@Min(1) @Max(99) Integer quantity, UUID addressId, UUID billingKeyId)` | 200 |
| `POST /api/subscriptions/{id}/pause` · `/resume` · `/cancel` | | 200 `SubscriptionResponse` |

- `@Future`는 시스템 시계 기준이라 업무 날짜와 다를 수 있다 → 서비스에서 "내일 이후"를 다시 검사한다(위 subscribe 첫 줄).

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `order/domain/SubscriptionScheduleTest` | [ ] WEEKLY 월(1): 2026-10-05(월)부터 → 그날 / 10-06(화)부터 → 10-12 / [ ] `nextAfter`(10-05) → 10-12 / [ ] MONTHLY 28: 1월 28일 다음 → 2월 28일 / [ ] MONTHLY 1: 12월 1일 다음 → 다음 해 1월 1일 / [ ] 범위 밖(8요일, 29일) → 예외 |
| `order/domain/SubscriptionStatusTest` | [ ] 전체 조합 파라미터화 |
| `order/domain/SubscriptionTest` | [ ] `resume` → 다음 실행일 재계산, 실패 수 0 / [ ] `terminate` 두 번 → 두 번째는 변화 없음 / [ ] CANCELLED에서 `change` → 예외 |
| `order/web/SubscriptionControllerTest` | [ ] 신청 → 201, 가격 = 신청 시점 상품 가격 / [ ] `subscribable = false` 상품 → 422 `PRODUCT_NOT_SUBSCRIBABLE` / [ ] 자기 가게 상품 → 422 / [ ] 오늘 시작 → 400 / [ ] **다른 회원 구독 조회·일시정지·해지 → 404** / [ ] 남의 빌링키로 신청 → 404 `BILLING_KEY_NOT_FOUND` / [ ] 활성 구독이 있는 가게 폐업 → 422, 회원 탈퇴 → 422 `ACTIVE_SUBSCRIPTION`, 쓰는 빌링키 삭제 → 409 |

**리뷰 때 물어볼 것**
- 날짜 계산을 어디에 두었나? 매월 29~31일을 막은 이유는?
- 구독 가격을 신청 시점에 저장하는 이유는?

---

## 5-3. 구독 회차 실행 잡 (L)

**목표**: 매일 06:00 실행일이 된 구독마다 주문을 만들고 빌링키로 결제한다. 실패하면 다음 날 재시도, 3회 연속 실패하면 일시정지(SUSPENDED). 재고가 없으면 건너뛴다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 회차 멱등 | `subscription_cycle(subscription_id, cycle_date)` unique. **회차 INSERT와 주문 생성을 한 트랜잭션**에 넣어, 같은 날 두 번 돌면 두 번째는 INSERT가 무시되어 아무것도 하지 않는다 (INV-08) |
| 주문 생성 코드 재사용 | 체크아웃에서 "주문 저장 + 재고 예약" 부분을 `OrderPlacer`로 뽑아 구독도 같이 쓴다 |
| 결과 반영 연결 | 결제가 불확실해 주문 대사(2-6)가 나중에 결과를 정하면, 그때 회차도 함께 갱신된다 |

**정책 (이 단계에서 정함)**
- 회차 = 실행한 날짜 하나. 결제가 거절되면 다음 날 **새 회차**(다음 날짜)로 다시 시도하고, 연속 실패 수는 구독(`consecutive_failures`)이 센다.
- 적용가: `pending_effective_date <= cycle_date`면 인상가를 적용하고 `unit_price`로 승격(POL-03·11).
- 재고 부족·판매 중지(`OUT_OF_STOCK`, `PRODUCT_NOT_ON_SALE`, `SHOP_NOT_ACTIVE`)는 결제 실패로 세지 않고 SKIPPED, 다음 주기로.
- 결제 거절 3회 연속 → SUSPENDED + 이벤트. 결과 불확실 → 주문은 PAYMENT_IN_PROGRESS로 두고 2-6 대사가 이어서 처리.
- 주문명(`orderName`, Toss 표시용): `"{상품명} 정기구독"`(100자 제한).

### 할 일

**1) 마이그레이션** — `db/migration/order/V{...}__order_create_subscription_cycle.sql`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_subscription_cycle` |
| subscription_id | uuid | X | `fk_subscription_cycle_subscription`, `uk_subscription_cycle_subscription_date (subscription_id, cycle_date)` |
| cycle_date | date | X | 실행한 날짜 |
| order_id | uuid | O | SKIPPED면 null |
| applied_unit_price | bigint | O | |
| status | varchar(20) | X | `ck_subscription_cycle_status`: `IN ('ORDERED','PAID','FAILED','SKIPPED')` |
| failure_reason | varchar(200) | O | |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

**2) 체크아웃 리팩터링** — `OrderPlacer` (order.application, `@Component`)
- `Order place(PlaceOrderCommand command)` — **호출자 트랜잭션 안에서**: `Order.checkout(...)` → save → `productApi.reserve` → order 반환
- `PlaceOrderCommand(UUID memberId, OrderType type, List<CheckoutLine> lines, ShippingAddress address)`
- `Order.checkout`에 `OrderType type` 인자 추가(NORMAL / SUBSCRIPTION)
- `CheckoutService`는 상품·가게·배송지 확인 후 `OrderPlacer.place`를 호출하도록 바꾼다(장바구니 삭제는 `CheckoutService`에 남는다). **2-2 테스트가 그대로 통과**해야 한다(리팩터링)

**3) payment 추가**
- `StartPayment`에 `UUID billingKeyId` 추가(일반 결제는 null), `payment.billing_key_id`에 저장
- `PaymentApi.PgOutcome callBillingCharge(UUID paymentId, String orderName)` — 트랜잭션 밖 전용. payment의 빌링키를 복호화해 `tossClient.billingCharge(...)`
- 대사(2-6)는 빌링 결제도 같은 `queryOutcome`(주문번호 조회)으로 처리된다

**4) order — 회차 실행**

| 클래스 | 규격 |
|---|---|
| `CycleStatus` | 전이: `ORDERED → {PAID, FAILED}` (SKIPPED는 처음부터 SKIPPED로 만든다) |
| `SubscriptionCycle` (entity) + `SubscriptionCycleRepository` | native `int insertIfAbsent(UUID id, UUID subscriptionId, LocalDate cycleDate, String status, Instant now)` (`ON CONFLICT ON CONSTRAINT uk_subscription_cycle_subscription_date DO NOTHING`), `Optional<SubscriptionCycle> findById`, `Optional<SubscriptionCycle> findByOrderId(UUID orderId)` |
| `SubscriptionCycleService` (`@Transactional` 없음) | `public int runDue()` |
| `SubscriptionCycleResultHandler` (`@Component`) | `@Transactional(propagation = MANDATORY) void onOrderClosed(Order order)` — 아래 |
| `SubscriptionCycleJob` (infrastructure) | `@Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")` → `jobRunner.run("subscription-cycles", service::runDue)` |

`runDue` — `today` 기준, `status = ACTIVE AND next_run_date <= today`인 구독을 id keyset(100건씩)으로 순회, 구독마다 try/catch:
1. **트랜잭션 1 (회차 + 주문)**:
   1. 구독을 다시 읽어 ACTIVE·실행일 확인
   2. `insertIfAbsent(cycleId, subId, today, "ORDERED", now)` → **0이면 종료**(오늘 회차가 이미 있다 — 같은 날 두 번 실행돼도 여기서 멈춘다)
   3. 적용가 결정·승격 → `orderPlacer.place(...)` → 회차에 `order_id`, `applied_unit_price` → `startPayment` CAS + `paymentApi.begin(..., billingKeyId)`
   - 이 트랜잭션은 한 덩어리라 중간에 죽으면 회차도 주문도 남지 않는다 → 다음 실행이 처음부터 다시 한다(잠금·리스가 필요 없다)
   - `OUT_OF_STOCK`·`PRODUCT_NOT_ON_SALE`·`SHOP_NOT_ACTIVE` 예외 → 트랜잭션 1은 롤백 → **트랜잭션 1'**: `insertIfAbsent(..., "SKIPPED", ...)`(사유 기록), 구독 `nextRunDate = schedule.nextAfter(today)` → 회차 실패 이벤트(reason `SKIPPED_*`)
2. **트랜잭션 밖**: `outcome = paymentApi.callBillingCharge(paymentId, orderName)`
3. **트랜잭션 2**: `orderPaymentApplier.apply(orderId, paymentId, outcome)` — 주문이 PAID·PAYMENT_FAILED가 되면 applier가 `onOrderClosed`를 부른다. UNKNOWN이면 회차는 ORDERED로 남고 2-6 대사가 나중에 같은 경로로 닫는다

`OrderPaymentApplier`(2-4) 변경: `complete`·`fail` 끝에서 `order.getType() == SUBSCRIPTION`이면 `cycleResultHandler.onOrderClosed(order)`

`onOrderClosed(order)` (주문 트랜잭션 안)
- 회차 = `findByOrderId`, 구독 조회
- 주문 PAID → 회차 PAID, 구독 `consecutiveFailures = 0`, `nextRunDate = schedule.nextAfter(cycleDate)`
- 주문 PAYMENT_FAILED → 회차 FAILED(사유), `consecutiveFailures + 1` → 3 이상이면 구독 SUSPENDED + 상태 변경 이벤트 / 아니면 `nextRunDate = cycleDate + 1일` → 회차 실패 이벤트

이벤트 (3-3 방식, `OrderTopics`에 추가)
- `SUBSCRIPTION_STATUS_CHANGED = "order.subscription-status-changed.v1"`: `SubscriptionStatusChangedEvent(UUID subscriptionId, UUID memberId, String from, String to, String reason)`
- `SUBSCRIPTION_CYCLE_FAILED = "order.subscription-cycle-failed.v1"`: `SubscriptionCycleFailedEvent(UUID subscriptionId, UUID cycleId, UUID memberId, int consecutiveFailures, String reason)`
- 3-5 알림 매핑에 `SUBSCRIPTION_STATUS_CHANGED`, `SUBSCRIPTION_CYCLE_FAILED` 추가

**5) API**: `GET /api/subscriptions/{id}/cycles?cursor=&size=` → `CycleResponse(id, cycleDate, status, orderId, appliedUnitPrice, failureReason)` (최신 먼저)

### 완료 확인

| 테스트 클래스 | 케이스 (가짜 PG 서버, `runDue()` 직접 호출, `next_run_date`는 JDBC로 오늘로) |
|---|---|
| 기존 2-2 체크아웃 테스트 | [ ] 리팩터링 후 전체 그대로 통과 |
| `order/domain/CycleStatusTest` | [ ] 전체 조합 파라미터화 |
| `order/application/SubscriptionCycleServiceTest` | [ ] 정상 회차 → 주문 1건(type SUBSCRIPTION) PAID, 회차 PAID, 다음 실행일 = 다음 주기 / [ ] **같은 날 `runDue()` 두 번 → 주문 1건, 가짜 서버 빌링 요청 1건** (INV-08) / [ ] **동시에 두 스레드에서 `runDue()`** → 주문 1건 / [ ] 거절 → 회차 FAILED·실패 1·다음 실행일 내일 → 다음 날(JDBC로 `next_run_date`를 오늘로, 이미 있는 회차 날짜는 어제로) 재실행 → 새 회차 / [ ] **3회 연속 거절 → SUSPENDED + 상태 변경 이벤트** / [ ] 2회 거절 후 성공 → 실패 0 / [ ] 재고 부족 → SKIPPED, 실패 수 그대로, 다음 실행일 = 다음 주기 / [ ] 빌링 타임아웃 → 회차 ORDERED, 주문 PAYMENT_IN_PROGRESS → `reconcileBatch()`(2-6) → 주문 PAID → 회차 PAID / [ ] 인상 예정가가 있고 `pending_effective_date <= cycle_date` → 인상가로 주문, `unit_price` 승격 |

**리뷰 때 물어볼 것**
- 구독 결제가 중복으로 나가지 않게 어떻게 했나? (회차 unique + 결제 unique + PG 멱등키)
- 회차 INSERT와 주문 생성을 한 트랜잭션에 넣은 덕분에 무엇이 필요 없어졌나?
- 체크아웃 코드를 어떻게 재사용했나?

---

## 5-4. 가격 변경 · 종료 이벤트 반영 (M)

**목표**: 상품 가격이 오르면 구독자에게 알리고 유예 후 적용, 내리면 바로 적용(OPEN-05). 상품 단종·가게 폐업·회원 탈퇴 시 구독 종료.

**정책 (이 단계에서 정함)**
- 인상: `pending_unit_price = 새 가격`. 적용일은 다음 실행일까지 **3일 이상** 남았으면 다음 실행일, 아니면 그다음 주기(POL-11).
- 인하: `unit_price = 새 가격`, 예정 인상분 취소(`pending_* = null`).
- 가격 이벤트 순서 보장: 이벤트의 `changedAt`이 구독의 `price_changed_at` 이하면 무시(늦게 도착한 오래된 이벤트).
- 종료 이벤트는 활성 구독만 TERMINATED로(이미 종료·해지면 무시 → 멱등).

### 할 일

**1) order — consumer** (order.infrastructure, `ProductEventConsumer`, `ShopEventConsumer`, 기존 `MemberEventConsumer`)

| 이벤트 | consumer 이름 | 처리 (`SubscriptionEventService`, `@Transactional`) |
|---|---|---|
| `product-price-changed` | `order.product-price-changed` | `findAllByProductIdAndStatusIn(productId, 활성)` 각각 `subscription.applyPriceChange(newPrice, changedAt, today)` → 바뀐 구독마다 `SUBSCRIPTION_PRICE_CHANGED` 이벤트 |
| `product-discontinued` | `order.product-discontinued` | 상품의 활성 구독 `terminate("PRODUCT_DISCONTINUED")` + 상태 변경 이벤트 |
| `shop-closed` | `order.shop-closed` | 가게의 활성 구독 `terminate("SHOP_CLOSED")` (폐업 조건상 보통 0건, 방어용) |
| `member-withdrawn` | `order.member-withdrawn` (3-6) | 기존 장바구니 삭제에 더해 회원의 활성 구독 `terminate("MEMBER_WITHDRAWN")` |

`Subscription.applyPriceChange(Money newPrice, Instant changedAt, LocalDate today)` → 반환 `boolean`(반영했으면 true)
1. `priceChangedAt != null && !changedAt.isAfter(priceChangedAt)`면 false
2. `priceChangedAt = changedAt`
3. 인상(`newPrice > unitPrice`): `effective = nextRunDate - today >= 3일 ? nextRunDate : schedule.nextAfter(nextRunDate)`, pending 설정
4. 인하·동일: `unitPrice = newPrice`, pending 비움
5. true

이벤트 `SUBSCRIPTION_PRICE_CHANGED = "order.subscription-price-changed.v1"`: `SubscriptionPriceChangedEvent(UUID subscriptionId, UUID memberId, long oldPrice, long newPrice, LocalDate effectiveDate)` → 3-5 알림 매핑에 `SUBSCRIPTION_PRICE_CHANGED` 추가(구독자에게 "다음 회차부터 가격이 바뀝니다")

**2) 판매자 구독 현황** — `GET /api/shops/{shopId}/subscriptions?status=&cursor=&size=` (`shopApi.verifyOwner`) → `SellerSubscriptionResponse(id, productId, quantity, cycleType, cycleValue, status, nextRunDate, unitPrice, createdAt)` — 구매자 개인정보(배송지 등)는 내려주지 않는다

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `order/domain/SubscriptionPriceTest` | [ ] **다음 회차 2일 전 인상 → 적용일 = 그다음 회차**, 이번 회차는 기존가 / [ ] 5일 전 인상 → 적용일 = 다음 회차 / [ ] 인하 → 즉시, 예정 인상 취소 / [ ] **오래된 changedAt 이벤트 → 무시**(최신 가격 유지) |
| `order/application/SubscriptionEventTest` | [ ] 상품 가격 인상(1-8 API) → 릴레이 → `Eventually`로 구독 pending 설정 + 가격 변경 이벤트 → 알림 / [ ] **단종 이벤트 두 번 → 종료 1번**(상태 변경 이벤트 1건) / [ ] 탈퇴 이벤트 → 구독 TERMINATED / [ ] 판매자 구독 현황: 남의 가게 → 403 |

**리뷰 때 물어볼 것**
- 이벤트 순서가 바뀌면 어떻게 되나? 버전 대신 `changedAt`을 쓴 것의 한계는?
- 가격 인상을 바로 적용하지 않는 이유는? (POL-11)

---

## Part 5 완료
- [ ] `v0.5.0` 릴리스, Part 6 문서 다듬기
