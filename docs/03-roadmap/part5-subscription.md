# Part 5. 구독

> 버전 0.30 · 2026-10-28 · 5-2 구독 신청에서 이메일 인증 확인(`@RequireVerifiedEmail`)을 뺐다. 4-2부터 가입 전에 이메일을 인증해 모든 회원이 인증된 이메일을 가진다
> 0.29 · 2026-10-27
> 0.13 · 2026-10-16 · 쓰지 않는 요소와 억지 재사용 경로를 걷어냈다. `PaymentApi.getActiveBillingKey`(반환 `BillingKeyInfo`)를 반환 없는 `verifyActiveBillingKey`로 바꾸고 record를 지웠다. `TossBillingResponse.customerKey`와 `Subscription.isActiveLike()`를 뺐다. SKIPPED 회차는 `insertIfAbsent`에 `failureReason` 인자를 더해 한 번의 INSERT로 만들고 `recordSkipReason`·추가 조회·UPDATE를 없앴다(같은 날 다른 실행이 먼저 만든 경우의 종료도 적었다). 구독 회차 주문의 결제 시작은 2-4 `startPayment` CAS 대신 `Order.markPaymentInProgress()`로 바꿨다(`SubscriptionCycleService`의 `OrderRepository` 의존 제거)
> 0.12 · 2026-10-16 · 5-3 `queryOutcome`의 `PgResult → PgOutcome` 변환도 `paymentKey`를 채우는 곳으로 적었다(자리 값 덮어쓰기 규칙이 대사 경로에서도 지켜지게)
> 0.11 · 2026-10-15 · 구독 신청 응답을 `SubscriptionIdResponse(subscriptionId)`로 확정(API 명세 §3.5)했다. 주기 범위 위반은 서비스가 400 `INVALID_REQUEST`로 바꾸고, payment `allowedDependencies`에 `member::api`를 더했다
> 0.10 · 2026-10-15 · 빌링 결제의 `payment_key`: `begin`은 자리 값 `"BILLING-" + orderId`, 승인 뒤 `PgOutcome.paymentKey`로 덮어쓴다(5-3에서 `PgOutcome`·`markApproved` 변경)
> 0.6 · 2026-10-09 · Toss 빌링 API(발급·승인·삭제 엔드포인트와 응답 필드)를 개발자센터 문서와 대조해 확정했고, `pg_call_log` 잔재를 로그 확인으로 바꿨다
> 0.5 · 2026-10-02 · 회차 선점(PROCESSING 리스·재시도 회차·시도 수)을 제거하고 회차 INSERT와 주문 생성을 한 트랜잭션으로 했다
> 0.4 · 2026-10-02 · 예치금 제거: 구독의 "예치금 우선 사용" 옵션을 삭제하고 회차 결제는 항상 빌링으로 했다

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
- 날짜는 모두 **업무 날짜**(`LocalDate`, Asia/Seoul)다. "오늘" = `LocalDate.now(clock.withZone(businessZone))`. `businessZone`은 3-4a가 만든 `BusinessProperties.businessZone()`(`myroutine.business-zone`)이고, `Clock`과 함께 주입받아 쓴다.
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

**Toss 빌링 API 요약** (2026-10-09 Toss 개발자센터 "코어 API → 자동결제"와 대조. 인증은 `tossRestClient`가 모든 요청에 붙이는 `Authorization: Basic base64(시크릿키 + ":")` 헤더다)

| 작업 | 요청 | 성공 응답 |
|---|---|---|
| 빌링키 발급 | `POST /v1/billing/authorizations/issue` `{authKey, customerKey}` | Billing 객체: `billingKey`, `customerKey`, `cardCompany`, `cardNumber`(일부 마스킹), `card: {issuerCode, acquirerCode, number, cardType, ownerType}` 등 |
| 빌링 결제 | `POST /v1/billing/{billingKey}` `{customerKey, amount, orderId, orderName}`(`orderName` 최대 100자) + `Idempotency-Key` | Payment 객체 `status: "DONE"` (2-3의 `TossPaymentResponse(paymentKey, orderId, status, totalAmount, balanceAmount, method, approvedAt)`로 그대로 받는다) |
| (참고) 빌링키 삭제 | `DELETE /v1/billing/{billingKey}` | 이 프로젝트는 쓰지 않는다(아래 정책) |

- `authKey`는 카드 등록창이 성공하면 리다이렉트 URL의 쿼리로 돌아오는 **일회성** 키다. 발급이 불확실하면 사용자가 카드를 다시 등록한다(아래 정책).
- **등록 흐름**: ① 프론트가 Toss 카드 등록창을 연다(이때 `customerKey`를 넘긴다) → ② 카드 인증이 끝나면 `successUrl`로 `authKey`가 돌아온다 → ③ 프론트가 `POST /api/billing-keys {authKey}`를 부른다 → ④ 서버가 `authKey`와 `customerKey`로 빌링키를 발급받아 암호화해 저장한다. (확인 필요: ①에서 프론트가 쓴 `customerKey`와 ④에서 서버가 쓰는 `customerKey`를 어떻게 같게 맞추는지가 문서에 없다. Toss는 등록창에 넘긴 값과 발급 호출의 값이 같아야 하는 것으로 알고 있는데, 요청 DTO는 `authKey`만 받고 아래 정책은 서버가 새 UUID를 만든다고만 적혀 있다.)

**정책 (이 단계에서 정함)**
- `customerKey`는 빌링키마다 새로 만든 랜덤 UUID(추측할 수 없게).
- 활성 구독(ACTIVE·PAUSED·SUSPENDED)이 쓰는 빌링키는 삭제 불가(OPEN-04).
- 삭제는 상태만 DELETED로 바꾼다. Toss에는 빌링키 삭제 API가 있지만(위 표) 쓰지 않는다 — 외부 호출과 그 실패 처리를 늘리지 않기 위한 기본값이다. Toss 쪽 빌링키가 남는 점은 회고에 한계로 적는다.
- 발급 결과가 불확실(타임아웃)하면 502로 응답하고 사용자가 다시 등록한다(돈이 오가지 않으므로 대사하지 않음).

### 할 일

**구현 순서**: 1) 마이그레이션 → 2) `AesGcmEncryptor` → 3) payment.domain → 4) payment.api → 5) payment.package-info → 6) payment.infrastructure(`TossClient` 추가 포함) → 7) 가짜 PG 서버 → 8) order.domain → 9) order.application → 10) payment.application → 11) `member-withdrawn` consumer → 12) payment.web → 테스트 준비 → 테스트.

**1) 마이그레이션** — `db/migration/payment/V{...}__payment_create_billing_key.sql`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_billing_key` |
| member_id | uuid | X | 인덱스 `idx_billing_key_member_id` |
| customer_key | varchar(64) | X | |
| billing_key_encrypted | varchar(500) | X | |
| card_summary | varchar(100) | X | `cardCompany + " " + cardNumber`(Toss가 마스킹한 번호 그대로), 예: `신한 1234****5678` |
| status | varchar(20) | X | `ck_billing_key_status`: `IN ('ACTIVE','DELETED')` |
| deleted_at | timestamptz | O | |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

**2) common.crypto**
`AesGcmEncryptor` (`@Component`) — **빌링키 같은 비밀 문자열을 DB에 넣기 전에 암호화하고, 꺼낼 때 복호화하는 도구**
- **왜**: 빌링키는 알면 결제를 일으킬 수 있는 비밀이라, DB가 유출돼도 원문이 보이지 않게 한다. 암호화는 `BillingKeyService.register`(5-1), 복호화는 `PaymentApiImpl.callBillingCharge`(5-3)가 한다.
- **키**: `myroutine.crypto.billing-key-secret`(환경변수 `BILLING_KEY_SECRET`, Base64 32바이트, 생성 `openssl rand -base64 32`). 시작할 때 `Base64.getDecoder().decode(값)`으로 `byte[]`를 만들고 `new SecretKeySpec(bytes, "AES")`를 필드로 들고 있는다. 32바이트가 아니면 기동 때 거절하는 편이 안전하다(잘못된 길이의 키로 조용히 도는 것을 막는다). 설정은 `TossProperties`처럼 `@ConfigurationProperties` record로 받는다(이름은 자유).
- **필수 설정이라 머지 전에 운영 VM `.env`와 `.env.ops.example`에 키를 먼저 넣는다**: 필수 프로퍼티(`@NotBlank`)가 VM `.env`에 없으면 develop 머지 뒤 자동 배포가 기동에 실패해 롤백된다. 테스트 프로필에는 테스트 전용 키를 적는다.

| 메서드 | 한 줄 설명 | 입력 → 반환 |
|---|---|---|
| `String encrypt(String plain)` | 평문을 암호화해 DB에 넣을 문자열로 만든다 | 평문 → `Base64(iv 12바이트 + 암호문)` |
| `String decrypt(String encoded)` | 위 형식을 풀어 평문을 돌려준다 | 저장된 문자열 → 평문 |

`encrypt` 순서
1. `byte[] iv = new byte[12]` → `SecureRandom.nextBytes(iv)` — **호출마다 새로 만든다.** 같은 키로 IV를 재사용하면 GCM의 안전성이 깨진다(그래서 같은 평문을 두 번 암호화하면 결과가 달라진다 — 완료 확인의 케이스).
2. `Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding")` → `cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv))` (128은 인증 태그 비트 수)
3. `byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8))` — 결과 끝에 16바이트 인증 태그가 붙어 나온다
4. `iv`와 `encrypted`를 이어 붙여(`ByteBuffer` 또는 `System.arraycopy`) `Base64.getEncoder().encodeToString(...)`

`decrypt` 순서: Base64 디코드 → 앞 12바이트가 IV, 나머지가 암호문+태그 → `cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv))` → `doFinal(나머지)` → `new String(..., UTF_8)`
- 암호문이 한 글자라도 바뀌면 `doFinal`이 `AEADBadTagException`(인증 태그 검증 실패)을 던진다 — "한 글자 변조 → 예외" 케이스.
- JDK 암호 API는 검사 예외(`GeneralSecurityException`)를 던지므로 메서드 안에서 잡아 `IllegalStateException`으로 감싸 던진다(감싸는 예외 타입은 문서에 없다 — 확인 필요. 테스트는 "예외"만 본다).
- `Cipher` 객체는 thread-safe하지 않다. 필드에 두지 말고 메서드마다 `getInstance`한다(`SecretKeySpec`은 공유해도 된다).

**3) payment.domain**

`domain`에 순수 인터페이스를 두고 `infrastructure`에 `JpaXxxRepository`가 그 인터페이스를 상속하는 구조로 만든다.

| 클래스 | 규격 |
|---|---|
| `PaymentErrorCode` | payment 전용 에러 코드. `ErrorCode`를 구현하는 enum(구조는 `OrderErrorCode`와 같다). `BILLING_KEY_NOT_FOUND`(404), `BILLING_KEY_ISSUE_FAILED`(422) — 값·메시지는 위 W 표. 2-4에서는 만들지 않았고 여기서 처음 만든다. `BillingKeyService`와 `PaymentApi.verifyActiveBillingKey`가 던진다 |
| `BillingKeyStatus` | `ACTIVE`, `DELETED` |
| `BillingKey` (entity) | `@Entity @Table(name = "billing_key", schema = "payment")`, `extends BaseTimeEntity`, 필드는 1) 마이그레이션의 컬럼과 1:1(`@Version Long version` 포함). `static BillingKey register(UUID memberId, String customerKey, String encryptedKey, String cardSummary)` → `id = Ids.newId()`, ACTIVE. `encryptedKey`는 **이미 암호화된** 문자열이다(엔티티는 암호화를 모른다). `void delete(Instant now)` → DELETED, `deletedAt = now` |
| `BillingKeyRepository` | `BillingKey save(BillingKey)`, `Optional<BillingKey> findById(UUID id)`, `Optional<BillingKey> findByIdAndMemberId(UUID id, UUID memberId)`, `List<BillingKey> findAllByMemberIdAndStatus(UUID memberId, BillingKeyStatus status)`(이름은 예. 인덱스 `idx_billing_key_member_id`가 받친다). `save`는 `register`, `findByIdAndMemberId`는 `delete`·`verifyActiveBillingKey`, `findAllByMemberIdAndStatus`는 `list`·탈퇴 삭제, `findById`는 5-3 `callBillingCharge`가 쓴다 |
| `BillingIssueResult` | `sealed interface { Issued(String billingKey, String cardSummary); Rejected(String code); Unknown(String reason) }`. `PgResult`(`Approved`·`Rejected(String code, String message)`·`Unknown(String reason)`)처럼 payment.domain에 sealed interface와 중첩 record로 둔다. `TossClient.issueBillingKey`가 반환하고 `BillingKeyService.register`가 분기한다 |

**4) payment.api**

| 클래스 | 규격 |
|---|---|
| `BillingKeyDeletionPrecondition` | `interface { void check(UUID billingKeyId); }` — 삭제해도 되는지 검사하고 안 되면 `BusinessException`을 던진다. 구독은 order 데이터라 payment가 직접 볼 수 없어서 payment가 인터페이스를 정의하고 order가 구현한다(의존 역전). `BillingKeyService.delete`가 부른다 |
| `PaymentApi` (기존 인터페이스에 추가) | `void verifyActiveBillingKey(UUID memberId, UUID billingKeyId)` — order(5-2 구독 신청·수정, `SubscriptionService.subscribe`·`change`)가 "이 빌링키가 내 것이고 살아 있는가"를 확인하는 데 쓴다. 반환값은 없다(호출자가 빌링키 내용을 쓰지 않는다). 없거나 남의 것·DELETED면 `BILLING_KEY_NOT_FOUND`(404) |

**5) payment.package-info**

| 클래스 | 규격 |
|---|---|
| `payment/package-info.java` | `allowedDependencies = {"common", "member::api"}`로 바꾼다(지금은 `{"common"}`). 탈퇴 consumer가 `MemberTopics`·`MemberWithdrawnEvent`를 쓰기 때문이다(Part 3 공통 규칙 P: 구독하는 모듈은 발행자의 `api`에 의존한다). Part 2 공통 규칙 J의 표에도 같게 적혀 있다 |

**6) payment.infrastructure**

`TossClient`(2-3)에 메서드 둘을 더한다. 호출 형태(`RestClient` + `exchange`, 통신 예외는 `ResourceAccessException` → `Unknown`, 마지막에 로그 한 줄)는 기존 메서드와 같고, 4xx·5xx 분류는 `issueBillingKey`의 분류표와 같은 규칙이다. 아래 표를 위에서 아래로 만든다.

| 클래스 | 규격 |
|---|---|
| `JpaBillingKeyRepository` | `extends JpaRepository<BillingKey, UUID>, BillingKeyRepository` |
| `TossBillingResponse` | `record (String billingKey, String cardCompany, String cardNumber)` + `@JsonIgnoreProperties(ignoreUnknown = true)`. Toss는 `customerKey`(우리가 보낸 값)와 `card` 객체 등 더 많은 필드를 주지만 이 세 개만 받는다. `issueBillingKey`의 응답 본문이다 |
| `TossClient.classify` 일반화 (기존 메서드 수정) | 지금은 본문 타입이 `TossPaymentResponse`, 결과가 `PgResult`로 고정돼 발급에 그대로 쓸 수 없다. 4xx·5xx 처리는 같으므로 본문 타입과 성공 변환을 파라미터로 받게 일반화하거나 발급용 분류를 따로 둔다(방식은 구현 재량). 기존 `confirm`·`cancel`·`queryByOrderId`의 동작은 그대로다. `issueBillingKey`가 쓴다 |
| `TossClient.issueBillingKey` | `BillingIssueResult issueBillingKey(String authKey, String customerKey)` — 카드 등록창이 돌려준 `authKey`를 빌링키로 바꾼다. 돈은 오가지 않는다. 요청·분류는 아래. `BillingKeyService.register`가 부른다 |
| `TossClient.billingCharge` | `PgResult billingCharge(UUID paymentId, String billingKey, String customerKey, String pgOrderId, Money amount, String orderName)` — 저장해 둔 빌링키로 결제를 요청한다. 요청·응답은 아래. 5-3 `PaymentApiImpl.callBillingCharge`가 부른다 |

`issueBillingKey`
- 요청: `POST /v1/billing/authorizations/issue`, 바디 `Map.of("authKey", authKey, "customerKey", customerKey)`. 위 API 표에 `Idempotency-Key`가 없으므로 붙이지 않는다.
- 분류

| 응답 | 결과 |
|---|---|
| 2xx, `billingKey`가 있음 | `Issued(billingKey, cardCompany + " " + cardNumber)` — `cardNumber`는 Toss가 이미 마스킹한 값이라 그대로 쓴다 |
| 2xx인데 본문 파싱 실패 또는 `billingKey`가 null | `Unknown("INVALID_BODY")` (NPE가 새지 않게) |
| 4xx | `Rejected(code)`. 단 불확실 코드(`ALREADY_PROCESSED_PAYMENT`, `ALREADY_PROCESSING_REQUEST`, `IDEMPOTENT_REQUEST_PROCESSING`. 결제가 이미 처리됐거나 처리 중이라 결과를 단정할 수 없다)는 `Unknown(code)` |
| 5xx, 타임아웃·연결 끊김 | `Unknown(reason)` |

`billingCharge`
- 요청: `POST /v1/billing/{billingKey}`(`.uri("/v1/billing/{key}", billingKey)`), 바디 `Map.of("customerKey", customerKey, "amount", amount.amount(), "orderId", pgOrderId, "orderName", orderName)`, 헤더 `Idempotency-Key: paymentId.toString()`. 승인(`confirm`)과 달리 요청에 `paymentKey`가 없다 — 빌링키가 결제 수단을 가리키고 Toss가 응답에서 `paymentKey`를 만들어 준다.
- 응답: `classify`를 그대로 쓴다. `status == "DONE"`이면 `Approved`, 그 외 `Unknown(status)`.

로그: 호출마다 한 줄(`log.info("pg {} paymentId={} result={} reason={} latencyMs={}", ...)`, 첫 칸이 작업 이름)을 남기고 작업 이름은 `BILLING_ISSUE`, `BILLING`이다. **빌링키·authKey·customerKey는 로그에 남기지 않는다**(감사 테이블 `pg_call_log`는 Part 2에서 추가 항목으로 옮겼다). `issueBillingKey`에는 `paymentId`가 없어 그 칸에 무엇을 남길지는 문서에 없다(확인 필요).

**7) 가짜 PG 서버** — `src/test/java/com/myroutine/support/`

| 클래스 | 규격 |
|---|---|
| `FakePgServer` (2-3, 기본 동작 추가) | 발급(`/v1/billing/authorizations/issue`)은 `{billingKey: "bk_" + 랜덤, cardCompany: "신한", cardNumber: "1234****5678"}`를 돌려준다. 결제(`/v1/billing/{billingKey}`)는 승인 경로와 같은 방식(바디의 `orderId`·`amount`로 `approved`에 기록, 멱등 키로 첫 응답 저장)이고, 바디에 `paymentKey`가 없으므로 가짜 서버가 만든다(예: `"pay_" + 랜덤`). 기록해 두면 `/v1/payments/orders/{orderId}` 조회가 되어 5-3의 대사 테스트가 된다. 경로 접두어가 `/v1/billing/`로 겹치므로 `handle`의 `defaultHandling` 분기에서 발급 경로를 먼저 판별한다. 테스트에서 결제에만 동작을 걸려면 접두어를 `/v1/billing/bk_`처럼 길게 쓴다(가장 긴 접두어가 이긴다). `TossBillingClientTest`, 5-1 `registerBillingKey`, 5-3 이후 구독 테스트가 쓴다 |

**8) order.domain**

| 클래스 | 규격 |
|---|---|
| `OrderErrorCode` (기존 enum에 추가) | `BILLING_KEY_IN_USE` — 409, "사용 중인 구독이 있어 삭제할 수 없습니다."(위 W 표). `OrderBillingKeyDeletionPrecondition`이 던진다 |

**9) order.application**

| 클래스 | 규격 |
|---|---|
| `OrderBillingKeyDeletionPrecondition` (`@Component`, `implements BillingKeyDeletionPrecondition`) | `check(billingKeyId)`: 이 빌링키를 쓰는 ACTIVE·PAUSED·SUSPENDED 구독이 있으면 `BusinessException(BILLING_KEY_IN_USE)`(409). 5-1 시점에는 구독 테이블이 없으므로 클래스만 두고 `check`는 비워 둔다. 5-2에서 `existsByBillingKeyIdAndStatusIn`을 채운다. 구현이 하나도 없으면 `BillingKeyService`의 `List<BillingKeyDeletionPrecondition>` 주입이 실패하므로 5-1에서 먼저 만들어 둔다. `BillingKeyService.delete`가 부른다 |

**10) payment.application**

| 클래스 | 메서드 |
|---|---|
| `BillingKeyService` | `BillingKeyResult register(UUID memberId, String authKey)` / `@Transactional(readOnly = true) List<BillingKeyResult> list(UUID memberId)` / `@Transactional void delete(UUID memberId, UUID id)` / `@Transactional void deleteAllByMember(UUID memberId)` (이름은 자유) |
| `BillingKeyResult` | `record (UUID id, String cardSummary, Instant createdAt)` — 응답으로 나가는 값이라 빌링키·customerKey는 아예 담지 않는다. `register`, `list`, `BillingKeyResponse.from`이 쓴다 |
| `PaymentApiImpl` (기존 클래스에 추가) | `verifyActiveBillingKey` 구현: `@Transactional(readOnly = true)`, `findByIdAndMemberId` 후 status 확인 |

`BillingKeyService` (`@Service`) — 클래스에 `@Transactional`을 붙이지 않는다(Part 2 공통 규칙 K: 외부 호출을 트랜잭션 밖에서). 의존성은 `TossClient`, `AesGcmEncryptor`, `BillingKeyRepository`, `TransactionTemplate`, `Clock`, `List<BillingKeyDeletionPrecondition>`이다. 호출하는 쪽은 `BillingKeyController`(`payment.web`)와 `member-withdrawn` consumer다.
- `register`: 카드를 등록한다. `authKey` → 저장된 빌링키 요약. 순서는 아래
- `list`: 내 ACTIVE 빌링키 목록(정렬 기준은 문서에 없다 — 확인 필요) → 목록
- `delete`: 빌링키를 삭제(상태만 DELETED)한다 → 없음. 순서는 아래
- `deleteAllByMember`: 회원의 ACTIVE 빌링키를 모두 DELETED로 바꾼다. 삭제 조건 검사는 거치지 않는다(탈퇴는 활성 구독이 없을 때만 통과한다, 3-6·5-2). 회원 ID → 없음

`register` 순서
1. `String customerKey = UUID.randomUUID().toString()` — 정책의 "추측할 수 없는 랜덤 UUID". 시각이 들어 있는 `Ids.newId()`(UUIDv7)는 쓰지 않는다
2. `BillingIssueResult result = tossClient.issueBillingKey(authKey, customerKey)` — **트랜잭션 밖**
3. `Rejected` → `BusinessException(BILLING_KEY_ISSUE_FAILED)`(422), `Unknown` → `BusinessException(CommonErrorCode.PG_ERROR)`(502). 어느 쪽도 아무것도 저장하지 않는다
4. `Issued`면 `String encrypted = aesGcmEncryptor.encrypt(issued.billingKey())`
5. `tx.execute(...)` 짧은 트랜잭션에서 `BillingKey.register(memberId, customerKey, encrypted, issued.cardSummary())` → `save` → `BillingKeyResult(id, cardSummary, createdAt)`

`delete` 순서
1. `findByIdAndMemberId(id, memberId)` — 없으면(남의 것 포함) 404 `BILLING_KEY_NOT_FOUND`. 이미 DELETED인 키를 다시 삭제할 때의 응답은 문서에 없다(확인 필요)
2. `preconditions.forEach(p -> p.check(id))` — 구현이 하나 더 늘어도 이 코드는 그대로다
3. `billingKey.delete(clock.instant())` — 더티 체킹으로 저장된다

**11) payment.infrastructure** — `member-withdrawn` consumer

| 클래스 | 규격 |
|---|---|
| `member-withdrawn` consumer (consumer 이름 `payment.member-withdrawn`) | 회원의 ACTIVE 빌링키를 모두 DELETED로 바꾼다. 필드는 `BillingKeyService`, `InboxGuard`, `EventEnvelopes`이고, 토픽 `MemberTopics.MEMBER_WITHDRAWN`(`"member.member-withdrawn.v1"`), 이벤트 `MemberWithdrawnEvent(UUID memberId)`다. `@KafkaListener(topics = MemberTopics.MEMBER_WITHDRAWN, groupId = "payment") void onMemberWithdrawn(String message)` → `TypedEnvelope<MemberWithdrawnEvent> typed = envelopes.parse(message, MemberWithdrawnEvent.class)` → `inboxGuard.runOnce("payment.member-withdrawn", typed.envelope(), () -> billingKeyService.deleteAllByMember(typed.payload().memberId()))` |

**12) payment.web** — `BillingKeyController` (`/api/billing-keys`)

| API | 요청 | 응답 |
|---|---|---|
| `POST /api/billing-keys` | `RegisterBillingKeyRequest(@NotBlank String authKey)` | 201 `BillingKeyResponse(id, cardSummary, createdAt)` |
| `GET /api/billing-keys` | | 200 `List<BillingKeyResponse>` |
| `DELETE /api/billing-keys/{id}` | | 204 |

요청자는 `@CurrentMember UUID memberId`로 받고(Part 1 공통 규칙 E), `BillingKeyResult` → `BillingKeyResponse.from(result)`로 바꿔 내려준다. 위 `BillingKeyService` 메서드 하나가 API 하나다.

### 완료 확인

**테스트 준비** ([Part 2 공통 규칙 N-1](part2-order-money.md#n-1-단계별-testfixtures-추가)과 같은 방식)

아래 테스트를 쓰기 전에 만든다. 이 단계에서 추가하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `registerBillingKey(UUID memberId)` → `UUID` | `billingKeyService.register(memberId, "auth_" + UUID)`를 직접 호출하고 `BillingKeyResult.id()`를 돌려준다. 가짜 PG의 발급 기본 동작(`bk_` + 랜덤 빌링키)이 빌링키를 만든다. `TestFixtures`가 `BillingKeyService`를 주입받는다 | `BillingKeyControllerTest`(남의 빌링키 삭제), 5-2 이후 구독 테스트 |
| `billingKeyOf(UUID billingKeyId)` → `BillingKeyRow` | JDBC `SELECT id, member_id, customer_key, billing_key_encrypted, card_summary, status, deleted_at FROM payment.billing_key WHERE id = ?` 한 행을 `support/`의 record로 돌려준다. 필드: `id`(UUID), `memberId`(UUID), `customerKey`(String), `billingKeyEncrypted`(String), `billingKey`(String. `aesGcmEncryptor.decrypt(billingKeyEncrypted)`한 원문, `TestFixtures`가 `AesGcmEncryptor`를 주입받는다), `cardSummary`(String), `status`(String), `deletedAt`(`Instant`, 없으면 null). 행이 없으면 예외 | `BillingKeyControllerTest`(등록·삭제 확인), 5-2 빌링키 삭제 거부, 5-3 빌링키 원문·`customerKey` |
| `billingKeysOf(UUID memberId)` → `List<BillingKeyRow>` | 위와 같은 컬럼을 `WHERE member_id = ? ORDER BY created_at, id`로 읽는다(삭제된 행 포함, 없으면 빈 목록) | `BillingKeyControllerTest`(등록 1건·실패 시 0건) |

이미 있는 헬퍼(`TestFixtures`): `UUID signup(String email)`(비밀번호 `pass1234`로 가입한 회원 ID), `String login(String email)`(`pass1234`로 로그인한 access 토큰). 회원 탈퇴 이벤트를 확인하는 케이스는 `outboxRelay.relayOnce()`(outbox의 READY 행을 Kafka로 한 번 내보낸다)와 `Eventually.await(Duration timeout, ThrowingRunnable assertion)`(확인 코드가 예외 없이 끝날 때까지 100ms 간격으로 되풀이하고, 제한 시간이 지나면 마지막 실패를 던진다)를 쓴다.

헬퍼를 쓰는 방법은 다음과 같다.
- 응답: `jsonPath`. 에러 응답의 코드는 `$.code`. 목록 응답은 최상위가 배열이라 `$[0].id`처럼 읽는다.
- 빌링키: `BillingKeyRow k = testFixtures.billingKeyOf(billingKeyId)` 한 번으로 읽고 `k.memberId()`처럼 접근자로 비교한다(필드는 위 표). 쿼리·복호화를 테스트에 쓰지 않는다.
- 가짜 PG가 받은 요청: 발급은 `pg.requests("/v1/billing/authorizations/issue")`, 결제는 `pg.requests("/v1/billing/bk_")`. `RecordedRequest`의 `method()`, `path()`, `headers().get("idempotency-key")`(헤더 키는 소문자)를 읽고, `body()`는 `ObjectMapper.readTree`로 읽는다(금액은 `asLong()`으로 비교한다).
- 발급되는 빌링키를 테스트가 알아야 할 때는 응답을 고정한다: `pg.on("/v1/billing/authorizations/issue", new Behavior(200, "{\"billingKey\":\"bk_test_secret_1\",\"cardCompany\":\"신한\",\"cardNumber\":\"1234****5678\"}", 0, false, false))`. 고정하지 않으면 빌링키는 `bk_` + 랜덤이다. `Behavior`의 인자 순서는 `(int status, String body, long delayMillis, boolean dropConnection, boolean defaultHandling)`이라, `0, false, false`는 지연·끊김 없이 `body`를 그대로 돌려준다는 뜻이다(`defaultHandling = true`면 본문을 무시하고 가짜 서버의 기본 동작을 쓴다).

#### `common/crypto/AesGcmEncryptorTest` (스프링 없는 단위 테스트)

공통 준비: 키는 테스트 전용 Base64 32바이트(`Base64.getEncoder().encodeToString(new byte[32])`)로 `AesGcmEncryptor`를 직접 만든다(생성자 인자는 구현이 정한 설정 타입에 맞춘다). 평문 `plain = "bk_secret-한글-1"`.

| 케이스 | 동작 | 확인할 것 |
|---|---|---|
| 왕복 | `encrypted = encrypt(plain)` → `decrypt(encrypted)` | `decrypt` 결과 = `plain` |
| 호출마다 다른 IV | `a = encrypt(plain)`, `b = encrypt(plain)` | `a` ≠ `b` / `decrypt(a)` = `decrypt(b)` = `plain` / 디코드한 바이트의 앞 12바이트(iv)가 서로 다르다 |
| 한 글자 변조 | `encrypt(plain)`을 Base64 디코드 → 마지막 바이트 하나를 `^ 0x01`로 바꿈 → 다시 Base64 인코드 → `decrypt` | 예외가 난다 |
| 암호문 형식 | `encrypted = encrypt(plain)` | `Base64.getDecoder().decode(encrypted).length` = 12 + `plain`의 UTF-8 바이트 수 + 16 |

#### `payment/infrastructure/TossBillingClientTest` (`extends IntegrationTestSupport`, `TossClient` 주입)

결과 타입은 `BillingIssueResult`(`Issued`/`Rejected`/`Unknown`), 결제는 `PgResult`다. 결제 호출 인자: `billingCharge(paymentId, "bk_test_1", "cust_1", "ORD-test-1", Money.of(10000), "상품 정기구독")`(`paymentId`는 `Ids.newId()`).

**1. 발급 성공** — 동작: `issueBillingKey("auth_1", "cust_1")`.
- `Issued`이고 `billingKey()`가 `bk_`로 시작하며 `cardSummary()` = `신한 1234****5678`.
- 가짜 PG 발급 요청 1건: `method()` = `POST`, 본문 `authKey` = `auth_1`, `customerKey` = `cust_1`.

**2. 발급 실패 분류** — 동작: 가짜 PG 동작을 바꾸고 `issueBillingKey("auth_1", "cust_1")`를 부른다.

| 가짜 PG 동작 | 결과 |
|---|---|
| `Behavior.reject("REJECT_CARD_COMPANY")` | `Rejected`, `code()` = `REJECT_CARD_COMPANY` |
| `Behavior.reject("ALREADY_PROCESSED_PAYMENT")` | `Unknown`, `reason()` = `ALREADY_PROCESSED_PAYMENT` |
| `Behavior.delay(1000)` | `Unknown` |
| `Behavior.serverError()` | `Unknown` |
| `Behavior.drop()` | `Unknown` |
| 상태 200, 본문 `{"cardCompany":"신한","cardNumber":"1234****5678"}`을 주는 `new Behavior(200, 본문, 0, false, false)` | `Unknown`, `reason()` = `INVALID_BODY` |

**3. 빌링 결제 승인** — 동작: `billingCharge(...)`.
- `PgResult.Approved`: `totalAmount()` = 10000, `paymentKey()`는 비어 있지 않다.
- 가짜 PG 요청 1건: `path()` = `/v1/billing/bk_test_1`, 헤더 `idempotency-key` = `paymentId.toString()`, 본문 `customerKey` = `cust_1`, `amount` = 10000, `orderId` = `ORD-test-1`, `orderName` = `상품 정기구독`.

**4. 빌링 결제 실패 분류** — 동작: 가짜 PG 동작을 `pg.on("/v1/billing/bk_", ...)`로 바꾸고 `billingCharge(...)`.

| 가짜 PG 동작 | 결과 |
|---|---|
| `Behavior.delay(1000)` | `Unknown`. 이어서 `queryByOrderId(Ids.newId(), "ORD-test-1")` → `Approved` |
| `Behavior.drop()` | `Unknown`. 이어서 `queryByOrderId(...)` → `Rejected`, `code()` = `NOT_FOUND_PAYMENT` |

**5. 로그에 비밀이 없다** — `@ExtendWith(OutputCaptureExtension.class)`, 테스트 메서드 인자 `CapturedOutput output`. 동작: `issueBillingKey("auth_secret_AAA", "cust_secret_BBB")`(`Issued`의 `billingKey()`를 `issuedKey`로 둔다) → 거절 동작으로 바꿔 `issueBillingKey("auth_secret_AAA", "cust_secret_BBB")` 한 번 더 → `billingCharge(Ids.newId(), issuedKey, "cust_secret_BBB", "ORD-test-2", Money.of(10000), "상품 정기구독")` → 거절 동작으로 바꿔 한 번 더.
- `output.getAll()`에 `pg BILLING_ISSUE`와 `pg BILLING paymentId=`가 있다.
- `output.getAll()`에 `auth_secret_AAA`, `cust_secret_BBB`, `issuedKey`가 없다(`doesNotContain`).

#### `payment/web/BillingKeyControllerTest` (`@AutoConfigureMockMvc`, 가짜 PG)

공통 준비: 구매자 `buyer@test.com`(`signup` → `buyerId`, `login` → 토큰). 가짜 PG 발급 응답을 `bk_test_secret_1`로 고정한다. 등록 요청은 `POST /api/billing-keys`, `Authorization: Bearer {토큰}`, 본문 `{"authKey":"auth_test_1"}`(`Idempotency-Key` 불필요).

**1. 카드 등록** — 동작: 등록 요청.
- 응답: 201, `$.id`는 DB 행의 `id`와 같고, `$.cardSummary` = `신한 1234****5678`, `$.createdAt` 존재. `$.billingKey`, `$.customerKey`는 없다(`doesNotExist()`).
- `billingKeysOf(buyerId)`가 1건이고 그 `id()`가 `$.id`와 같다. `k = billingKeyOf($.id)`: `memberId()` = `buyerId`, `cardSummary()` = `신한 1234****5678`.
- `k.billingKey()` = `bk_test_secret_1`.
- `k.customerKey()`는 `UUID.fromString`이 예외 없이 읽는 문자열이다.
- 가짜 PG 발급 요청 1건: 본문 `authKey` = `auth_test_1`, 본문 `customerKey` = `k.customerKey()`.

**2. 등록 실패는 아무것도 저장하지 않는다** — 동작: 가짜 PG 동작을 바꾸고 등록 요청.

| 가짜 PG 동작 | 응답 | 공통 확인 |
|---|---|---|
| `pg.on("/v1/billing/authorizations/issue", Behavior.reject("REJECT_CARD_COMPANY"))` | 422, `$.code` = `BILLING_KEY_ISSUE_FAILED` | `billingKeysOf(buyerId)`가 비어 있다 |
| `pg.on("/v1/billing/authorizations/issue", Behavior.delay(1000))` | 502, `$.code` = `PG_ERROR` | `billingKeysOf(buyerId)`가 비어 있다 |

**3. 남의 빌링키 삭제 거부** — 준비: 다른 회원 `owner@test.com` 가입 + `ownerKeyId = registerBillingKey(ownerId)`. 동작: `buyer` 토큰으로 `DELETE /api/billing-keys/{ownerKeyId}`.
- 응답: 404, `$.code` = `BILLING_KEY_NOT_FOUND`.
- `billingKeyOf(ownerKeyId)`: `status()` = `ACTIVE`, `deletedAt()` = null.

**4. 내 빌링키 삭제** — 준비: `keyId = registerBillingKey(buyerId)`. 동작: `buyer` 토큰으로 `DELETE /api/billing-keys/{keyId}`.
- 응답: 204.
- `billingKeyOf(keyId)`: `status()` = `DELETED`, `deletedAt()`은 null이 아니다.
- Toss 삭제 API를 부르지 않는다: `pg.requests("/v1/billing/")` 중 `method()` = `DELETE`인 것이 0건.

**5. 회원 탈퇴 이벤트로 빌링키 삭제** — 준비: `registerBillingKey(buyerId)` 두 번(`keyA`, `keyB`), `owner@test.com`의 `ownerKeyId`. 동작: `buyer` 토큰으로 `DELETE /api/members/me`(3-6) → 204 → `outboxRelay.relayOnce()` → `Eventually.await(Duration.ofSeconds(5), ...)` 안에서 확인.
- `billingKeyOf(keyA)`, `billingKeyOf(keyB)`: `status()` = `DELETED`, `deletedAt()`은 null이 아니다.
- `billingKeyOf(ownerKeyId)`: `status()` = `ACTIVE`, `deletedAt()` = null.

**6. 카드 목록은 비밀 값을 내려주지 않는다** — 준비: 1번과 같은 등록 요청 한 번. 동작: `GET /api/billing-keys`.
- 응답: 200, `$.length()` = 1, `$[0].id` = 등록 응답의 `$.id`, `$[0].billingKey`·`$[0].customerKey` 없음.

**7. 삭제한 빌링키는 목록에서 빠진다** — 준비: 4번과 같은 준비, `buyer` 토큰으로 `DELETE /api/billing-keys/{keyId}` → 204. 동작: `GET /api/billing-keys`.
- 응답: 200, `$.length()` = 0.

**리뷰 때 물어볼 것**
- 암호화 키는 어디서 관리하나? 운영이라면?
- 빌링키 발급 결과가 불확실할 때 대사하지 않아도 되는 이유는?

---

## 5-2. 구독 신청 · 관리 (M)

**목표**: 구독 신청(수량, 매주 N요일 / 매월 N일, 배송지, 빌링키), 조회·수정·일시정지·재개·해지. **본인 구독만** 다룰 수 있다.

**정책 (이 단계에서 정함)**
- 시작일은 **내일 이후**. 첫 회차는 시작일 이후 주기에 맞는 첫 날.
- 신청 시점의 상품 가격을 구독 가격(`unit_price`)으로 저장한다.
- 구독 가능(`subscribable`)·판매 중 상품만, 자기 가게 상품 불가.
- 재개(PAUSED·SUSPENDED → ACTIVE) 시 다음 실행일은 **내일 이후** 첫 주기 날짜로 다시 계산하고 연속 실패 수를 0으로.
- 해지(CANCELLED)·종료(TERMINATED)된 구독은 수정·재개 불가.
- 폐업·탈퇴 조건: 활성 구독(ACTIVE·PAUSED·SUSPENDED)이 있으면 가게 폐업(POL-15)·회원 탈퇴(POL-16) 불가. 판매자는 구독 상품을 **단종**하면 구독이 종료되므로(5-4), 폐업 전에 상품을 단종하면 된다.

### 할 일

**구현 순서**: 1) 마이그레이션 → 2) order.domain(`CycleType`·`SubscriptionSchedule`·`SubscriptionStatus` → `Subscription` → `OrderErrorCode` → `SubscriptionRepository`) → 3) order.infrastructure → 4) order.application(`SubscribeCommand`·`ChangeSubscriptionCommand`·`SubscriptionResult`과 기존 precondition 세 개 → `SubscriptionService`) → 5) order.web → 테스트 준비 → 테스트.

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

| 클래스 | 규격 |
|---|---|
| `CycleType` | `WEEKLY, MONTHLY` |
| `SubscriptionSchedule` | **"몇 월 며칠·무슨 요일에 실행한다"는 주기와 그 날짜 계산을 한 곳에 모은 값 객체**(공통 규칙 V). `record (CycleType type, int value)`. compact 생성자에서 범위(WEEKLY 1~7, MONTHLY 1~28)를 검사해 벗어나면 예외. 날짜 계산 메서드는 `LocalDate firstOnOrAfter(LocalDate date)`(date 포함)와 `LocalDate nextAfter(LocalDate date)`(date 제외) 둘이고 계산식은 아래 표다 |
| `SubscriptionStatus` | 전이: `ACTIVE → {PAUSED, SUSPENDED, CANCELLED, TERMINATED}`, `PAUSED → {ACTIVE, CANCELLED, TERMINATED}`, `SUSPENDED → {ACTIVE, CANCELLED, TERMINATED}`. "활성"(ACTIVE·PAUSED·SUSPENDED)은 상수 하나(예: `Set<SubscriptionStatus> ACTIVE_LIKE`)로 둔다 |
| `Subscription` (애그리거트 루트) | **구독 한 건(누가·무엇을·언제마다·어디로·어느 카드로·얼마에)과 그 상태**. `@Entity @Table(name = "subscription", schema = "orders")`, `extends BaseTimeEntity`, 필드는 위 컬럼과 1:1(`@Version Long version` 포함). 주기는 `@Embedded`가 아니라 `cycleType`·`cycleValue` 컬럼 두 개로 두고 `SubscriptionSchedule schedule()`이 `new SubscriptionSchedule(cycleType, cycleValue)`를 돌려준다. `shippingAddress`는 jsonb 매핑(`@JdbcTypeCode(SqlTypes.JSON)`, Part 2 공통 규칙 P-8), 가격은 `Money`(null 가능한 `pendingUnitPrice`도 `MoneyConverter`가 처리) |
| `OrderErrorCode` | `SUBSCRIPTION_NOT_FOUND`, `PRODUCT_NOT_SUBSCRIBABLE` |
| `SubscriptionRepository` | 인터페이스. 메서드는 아래 표 |

`SubscriptionSchedule` 계산 — JDK `LocalDate`와 `java.time.temporal.TemporalAdjusters`로 충분하다

| 주기 | `firstOnOrAfter(date)` (date 포함) | `nextAfter(date)` (date 제외) |
|---|---|---|
| WEEKLY | `date.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(value)))` | `date.with(TemporalAdjusters.next(DayOfWeek.of(value)))` |
| MONTHLY | `date.getDayOfMonth() <= value`면 `date.withDayOfMonth(value)`, 아니면 `date.plusMonths(1).withDayOfMonth(value)` | `date.getDayOfMonth() < value`면 `date.withDayOfMonth(value)`, 아니면 `date.plusMonths(1).withDayOfMonth(value)` |

- MONTHLY는 `value`가 28 이하라 어느 달에도 `withDayOfMonth(value)`가 유효하다(29~31을 막은 이유). `plusMonths(1)`을 먼저 해도 일(日)은 바뀌지 않고 `withDayOfMonth`로 맞춘다. 완료 확인의 사례(월 28일 1월 28일 다음 → 2월 28일, 월 1일 12월 1일 다음 → 다음 해 1월 1일)가 이 식으로 나온다.

`Subscription` 메서드 — 상태를 바꾸는 규칙은 모두 엔티티 안에 둔다(서비스는 호출만). 상태 전이는 `status.transitTo(...)`이고, 허용하지 않는 전이는 `BusinessException(INVALID_STATE_TRANSITION)`(409)이다(Part 1 공통 규칙 C).

| 메서드 | 한 줄 설명 | 입력 → 결과·예외 |
|---|---|---|
| `static Subscription subscribe(UUID memberId, UUID shopId, UUID productId, int quantity, SubscriptionSchedule schedule, ShippingAddress address, UUID billingKeyId, Money unitPrice, LocalDate startDate)` | 새 구독을 만든다. `unitPrice`는 **신청 시점 상품 가격**(5-2 정책)이다 | → ACTIVE, `id = Ids.newId()`, `nextRunDate = schedule.firstOnOrAfter(startDate)`, `consecutiveFailures = 0` |
| `void pause()` | 일시정지 | ACTIVE → PAUSED. 그 밖의 상태는 409 |
| `void resume(LocalDate today)` | 재개. 일시정지·정지(SUSPENDED)를 되돌린다 | PAUSED·SUSPENDED → ACTIVE, `nextRunDate = schedule.firstOnOrAfter(today.plusDays(1))`, `consecutiveFailures = 0`. 그 밖의 상태는 409 |
| `void cancel()` | 구매자가 해지 | → CANCELLED, `endReason = "USER_CANCEL"` |
| `void terminate(String reason)` | 시스템이 종료(단종·폐업·탈퇴, 5-4). `reason`은 `endReason`에 저장 | → TERMINATED. **이미 CANCELLED·TERMINATED면 전이 검사 전에 아무것도 하지 않고 돌아온다**(멱등 — 같은 이벤트가 두 번 와도 안전) |
| `void change(Integer quantity, ShippingAddress address, UUID billingKeyId)` | 수량·배송지·결제수단 수정. `null`인 인자는 바꾸지 않는다 | CANCELLED·TERMINATED면 409 `INVALID_STATE_TRANSITION`(상태 전이가 아니라 직접 검사). ACTIVE·PAUSED·SUSPENDED에서는 허용 |

`SubscriptionRepository` (`domain` 인터페이스) 메서드. 이름·인자는 권장안이다. `StatusIn`의 상태 인자에는 `SubscriptionStatus.ACTIVE_LIKE`를 넘긴다.

| 메서드 | 쿼리·동작 | 쓰이는 곳 |
|---|---|---|
| `Subscription save(Subscription)` | | `subscribe` |
| `Optional<Subscription> findById(UUID id)` | | 5-3 `runDue`·`onOrderClosed` |
| `Optional<Subscription> findByIdAndMemberId(UUID id, UUID memberId)` | 남의 것이면 비어 있다 | `getOne`·`change`·`pause`·`resume`·`cancel`, 5-3 회차 이력 |
| `boolean existsByBillingKeyIdAndStatusIn(UUID billingKeyId, Collection<SubscriptionStatus> statuses)` | 활성 구독 존재 확인 | `OrderBillingKeyDeletionPrecondition`(빌링키 삭제) |
| `boolean existsByShopIdAndStatusIn(UUID shopId, Collection<SubscriptionStatus> statuses)` | 같음 | `OrderShopClosePrecondition`(가게 폐업) |
| `boolean existsByMemberIdAndStatusIn(UUID memberId, Collection<SubscriptionStatus> statuses)` | 같음 | `OrderWithdrawalPrecondition`(회원 탈퇴) |
| `List<Subscription> findAllByProductIdAndStatusIn(UUID productId, Collection<SubscriptionStatus> statuses)` | 활성 구독 목록. 인덱스 `idx_subscription_product_id_status` | 5-4 가격 변경·단종 이벤트 |
| `List<Subscription> findAllByShopIdAndStatusIn(UUID shopId, Collection<SubscriptionStatus> statuses)` | 인덱스 `idx_subscription_shop_id_status` | 5-4 폐업 이벤트 |
| `List<Subscription> findAllByMemberIdAndStatusIn(UUID memberId, Collection<SubscriptionStatus> statuses)` | | 5-4 탈퇴 이벤트 |
| `List<Subscription> findByMemberFirstPage(UUID memberId, Limit limit)` · `findByMemberNextPage(UUID memberId, Instant cursorCreatedAt, UUID cursorId, Limit limit)` | `member_id = :memberId`, `order by created_at desc, id desc`, 다음 페이지는 `(created_at, id)`가 커서보다 작은 행. 인덱스 `idx_subscription_member_id_created_at` | `getMine` |

5-3의 "오늘 실행 대상" 조회와 5-4의 판매자 목록 조회는 그 단계에서 더한다.

**3) order.infrastructure**

| 클래스 | 규격 |
|---|---|
| `JpaSubscriptionRepository` | `extends JpaRepository<Subscription, UUID>, SubscriptionRepository` |

**4) order.application**

| 클래스 | 메서드 |
|---|---|
| `SubscriptionService` | `@Transactional UUID subscribe(UUID memberId, SubscribeCommand command)` / `@Transactional(readOnly = true) CursorPage<SubscriptionResult> getMine(UUID memberId, String cursor, int size)` / `@Transactional(readOnly = true) SubscriptionResult getOne(UUID memberId, UUID id)` / `@Transactional SubscriptionResult change(UUID memberId, UUID id, ChangeSubscriptionCommand command)` / `@Transactional SubscriptionResult pause` · `resume` · `cancel` `(UUID memberId, UUID id)` |
| `SubscribeCommand` | `record (UUID productId, int quantity, CycleType cycleType, int cycleValue, UUID addressId, UUID billingKeyId, LocalDate startDate)`. `request.toCommand()`로 만든다. `subscribe`의 입력이다 |
| `ChangeSubscriptionCommand` | `record (Integer quantity, UUID addressId, UUID billingKeyId)` — 모두 선택. `change`의 입력이다 |
| `SubscriptionResult` | `record (UUID id, UUID productId, UUID shopId, int quantity, CycleType cycleType, int cycleValue, ShippingAddress shippingAddress, UUID billingKeyId, long unitPrice, Long pendingUnitPrice, LocalDate pendingEffectiveDate, SubscriptionStatus status, LocalDate nextRunDate, int consecutiveFailures, Instant createdAt)` — 엔티티를 밖으로 내보내지 않으려고 값만 옮긴 record(`unitPrice`는 `Money.amount()`). (확인 필요: 2-2는 web이 domain을 import하지 않게 `ShippingAddressResult`를 쓰는데 여기는 도메인 `ShippingAddress` 타입이다.) `getMine`·`getOne`·`change`·`pause`·`resume`·`cancel`의 반환이고 `SubscriptionResponse.from`이 쓴다 |
| `OrderShopClosePrecondition` (3-2, 기존 클래스에 추가) | `shop.api.ShopClosePrecondition.check(shopId)`: `existsByShopIdAndStatusIn(shopId, ACTIVE_LIKE)`가 true면 `BusinessException(SHOP_HAS_ACTIVE_ORDERS, Map.of("reason", "ACTIVE_SUBSCRIPTION"))`. 가게 폐업(3-2)이 부른다 |
| `OrderWithdrawalPrecondition` (3-6, 기존 클래스에 추가) | `member.api.WithdrawalPrecondition.violations(memberId)`: `existsByMemberIdAndStatusIn`이 true면 돌려주는 목록에 `"ACTIVE_SUBSCRIPTION"`을 더한다(탈퇴 서비스가 `details.reasons`에 모은다). 회원 탈퇴(3-6)가 부른다 |
| `OrderBillingKeyDeletionPrecondition` (5-1에서 만든 빈 클래스) | `payment.api.BillingKeyDeletionPrecondition.check(billingKeyId)`의 본문을 채운다: `existsByBillingKeyIdAndStatusIn`이 true면 `BILLING_KEY_IN_USE`. `BillingKeyService.delete`(5-1)가 부른다 |

`SubscriptionService` (`@Service`) — **구독 신청·조회·수정·일시정지·재개·해지의 유스케이스**. 호출하는 쪽은 `SubscriptionController`(`order.web`)이고, 메서드 하나가 API 하나·트랜잭션 하나다. 외부(Toss) 호출이 없어 전부 `@Transactional`이다. 의존성: `SubscriptionRepository`, `ProductApi`, `ShopApi`, `MemberApi`, `PaymentApi`, `Clock`, `BusinessProperties`(오늘 날짜용)
- `subscribe`: 구독을 신청한다. 새 구독 ID를 돌려준다. 순서는 아래
- `getMine`: 내 구독 목록(최신 먼저). `cursor`가 비어 있으면 `findByMemberFirstPage(memberId, Limit.of(size + 1))`, 있으면 `Cursor.decode(cursor)`(common.web, `createdAt|id`를 Base64URL로 인코딩한 값. 형식이 틀리면 400 `INVALID_REQUEST`)의 `createdAt()`·`id()`로 `findByMemberNextPage`를 부른다. 읽은 건수가 `size`보다 크면 `size`건만 남기고 `size`번째 구독의 `new Cursor(createdAt, id).encode()`를 `nextCursor`로 둔다(없으면 null) → `new CursorPage<>(items, nextCursor)`
- `getOne`: 내 구독 상세. `findByIdAndMemberId`가 비면 404 `SUBSCRIPTION_NOT_FOUND`(남의 것도 같은 404 — 존재 여부를 드러내지 않는다, As-Is ORD-08)
- `change`: 수량·배송지·결제수단 수정. 순서는 아래
- `pause` · `resume` · `cancel`: 각각 일시정지·재개·해지. `findByIdAndMemberId`(없으면 404) → 도메인 메서드(`resume`에는 오늘 날짜를 넘긴다) → `SubscriptionResult`

`subscribe` 순서 (`today = LocalDate.now(clock.withZone(businessZone))`)
1. `!command.startDate().isAfter(today)`(시작일이 오늘이거나 이전)이면 400 `INVALID_REQUEST`
2. `ProductForCheckout product = productApi.getPurchasable(List.of(command.productId())).get(0)` — 판매 중이 아니면 여기서 422 `PRODUCT_NOT_ON_SALE`. 이어서 `!product.subscribable()`이면 422 `PRODUCT_NOT_SUBSCRIBABLE`
3. `ShopInfo shop = shopApi.requireActiveShops(List.of(product.shopId())).get(0)` — 운영 중이 아니면 422 `SHOP_NOT_ACTIVE`. `shop.ownerId().equals(memberId)`이면 422 `OWN_SHOP_PRODUCT`(`details`에 무엇을 담는지는 문서에 없다 — 체크아웃은 `productIds`를 담는다, 확인 필요)
4. `ShippingAddressInfo info = memberApi.getAddress(memberId, command.addressId())` — 없거나 남의 것이면 `MEMBER_ADDRESS_NOT_FOUND`. `CheckoutService`처럼 `ShippingAddress`(order.domain)로 옮겨 담는다
5. `paymentApi.verifyActiveBillingKey(memberId, command.billingKeyId())` — "내 것이고 ACTIVE인지"만 확인한다(아니면 404 `BILLING_KEY_NOT_FOUND`)
6. `new SubscriptionSchedule(command.cycleType(), command.cycleValue())` → `Subscription.subscribe(memberId, product.shopId(), product.productId(), command.quantity(), schedule, address, command.billingKeyId(), product.price(), command.startDate())` → `save` → `getId()`
   - `SubscriptionSchedule` 생성자가 던지는 `IllegalArgumentException`(`cycle.value`가 범위 밖: WEEKLY 8, MONTHLY 29)은 여기서 잡아 400 `INVALID_REQUEST`로 바꿔 던진다. 범위가 `type`에 따라 달라 DTO 어노테이션만으로는 검증할 수 없고, `GlobalExceptionHandler`에 `IllegalArgumentException` 처리를 더하지 않는 이유는 다른 곳의 버그까지 400으로 가려지기 때문이다

`change` 순서: `findByIdAndMemberId`(없으면 404) → `command.addressId() != null`이면 `memberApi.getAddress(memberId, command.addressId())`로 새 스냅샷 → `command.billingKeyId() != null`이면 `verifyActiveBillingKey`로 확인 → `subscription.change(quantity, address, billingKeyId)`(null이면 유지) → `SubscriptionResult`. 저장은 더티 체킹이다.

**5) order.web** — `SubscriptionController` (`/api/subscriptions`)

| API | 요청 | 응답 |
|---|---|---|
| `POST /api/subscriptions` `@Idempotent` | `SubscribeRequest(@NotNull productId, @Min(1) @Max(99) quantity, @Valid cycle {@NotNull type, @NotNull value}, @NotNull addressId, @NotNull billingKeyId, @NotNull @Future LocalDate startDate)` — [API 명세 §3.5](../02-design/07-api-spec.md) | 201 `SubscriptionIdResponse(subscriptionId)` |
| `GET /api/subscriptions?cursor=&size=` | | 200 `CursorPage<SubscriptionResponse>` |
| `GET /api/subscriptions/{id}` | | 200 `SubscriptionResponse` |
| `PATCH /api/subscriptions/{id}` | `ChangeSubscriptionRequest(@Min(1) @Max(99) Integer quantity, UUID addressId, UUID billingKeyId)` | 200 |
| `POST /api/subscriptions/{id}/pause` · `/resume` · `/cancel` | | 200 `SubscriptionResponse` |

- `@Future`는 시스템 시계 기준이라 업무 날짜와 다를 수 있다 → 서비스에서 "내일 이후"를 다시 검사한다(`subscribe` 순서의 첫 줄).
- 요청 DTO의 `quantity`·`cycle.value`는 Part 2 공통 규칙 P-2에 따라 래퍼 타입(`Integer`)에 `@NotNull`을 붙여 받는다(원시 타입이면 생략 시 `details`가 빈 400이 된다). `cycle`은 중첩 record(`CycleRequest(@NotNull CycleType type, @NotNull Integer value)`)이고 `request.toCommand()`가 `SubscribeCommand`의 `cycleType`·`cycleValue`로 펼친다.
- 신청 응답에는 ID만 담기므로 상태·단가·다음 실행일은 구독 조회 API로 읽는다.
- 컨트롤러 메서드는 다른 컨트롤러처럼 `@CurrentMember UUID memberId`를 받고, `SubscriptionResult` → `SubscriptionResponse.from(result)`로 바꿔 내려준다. `PATCH`의 200 본문과 `SubscriptionResponse`의 필드는 문서에 없다(확인 필요: 서비스가 `SubscriptionResult`를 돌려주므로 `SubscriptionResponse`가 자연스럽고, 필드는 `SubscriptionResult`와 같게 두되 `shippingAddress`만 web 응답 타입으로 바꾸는 것이 자연스럽다).

### 완료 확인

**테스트 준비** ([Part 2 공통 규칙 N-1](part2-order-money.md#n-1-단계별-testfixtures-추가)과 같은 방식)

아래 테스트를 쓰기 전에 만든다. 이 단계에서 추가하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `registerSubscribableProduct(UUID memberId, UUID shopId, long price, int initialStock)` → `UUID` | `registerProductService.register(memberId, shopId, new RegisterProductCommand("product", "description", ProductCategory.ETC, price, initialStock, true))`. 기존 `registerProduct` 오버로드는 모두 `subscribable = false`로 고정이라 새 이름으로 둔다 | `SubscriptionControllerTest`, 5-3·5-4 |
| `createSubscription(UUID memberId, UUID productId, UUID billingKeyId)` → `UUID` | `subscriptionService.subscribe(memberId, new SubscribeCommand(productId, 1, CycleType.WEEKLY, 1, address(memberId), billingKeyId, 오늘 + 1일))`. 오늘은 업무 날짜(`LocalDate.now(clock.withZone(businessZone))`)다 | 5-2 폐업·탈퇴·빌링키 삭제 케이스, 5-3·5-4 |
| `subscriptionSnapshotOf(UUID subscriptionId)` → `SubscriptionSnapshot` | JDBC `SELECT * FROM orders.subscription WHERE id = ?` 한 행을 `support/`의 record로 돌려준다(행이 없으면 예외). 필드: `id`·`memberId`·`shopId`·`productId`·`billingKeyId`(UUID), `quantity`·`cycleValue`·`consecutiveFailures`(int), `cycleType`·`status`·`endReason`(String, `endReason`은 없으면 null), `unitPrice`(long), `pendingUnitPrice`(`Long`, 없으면 null), `pendingEffectiveDate`·`nextRunDate`(`LocalDate`, 앞은 없으면 null), `priceChangedAt`(`Instant`, 없으면 null), `shippingAddress`(`Map<String, String>`: `shipping_address` jsonb를 `ObjectMapper.readValue`로 읽은 `recipient`·`phone`·`zipcode`·`address1`·`address2` 키). 회차는 5-3 테이블이 생긴 뒤 `cyclesOf`로 읽는다 | 5-2 컨트롤러 테스트(신청·접근 거부 확인), 5-3 `SubscriptionCycleServiceTest`, 5-4 `SubscriptionEventTest` |
| `subscriptionIdsOf(UUID memberId)` → `List<UUID>` | JDBC `SELECT id FROM orders.subscription WHERE member_id = ? ORDER BY created_at, id` | 5-2 신청 거부 케이스의 "구독이 만들어지지 않았다"(빈 목록), 신청 성공의 1건 확인 |

이미 있는 헬퍼(`TestFixtures`): `UUID signup(String email)`(비밀번호 `pass1234`로 가입한 회원 ID), `String login(String email)`(`pass1234`로 로그인한 access 토큰), `UUID openShop(UUID memberId, String businessNumber)`(가게를 개설하고 가게 ID), `UUID registerProduct(UUID memberId, UUID shopId, long price, int initialStock)`(`subscribable = false`인 상품을 등록하고 상품 ID), `UUID address(UUID memberId)`(배송지를 등록하고 배송지 ID). 빌링키는 5-1의 `registerBillingKey`로 만든다.

헬퍼를 쓰는 방법은 다음과 같다.
- 응답: `jsonPath`. 에러 응답의 코드는 `$.code`, 상세는 `$.details`.
- 구독: `SubscriptionSnapshot s = testFixtures.subscriptionSnapshotOf(subscriptionId)` 한 번으로 읽고 `s.status()`처럼 접근자로 비교한다(필드는 위 표). 쿼리를 테스트에 쓰지 않는다.
- 오늘 날짜: 테스트 클래스에 `Clock`, `BusinessProperties`를 주입하고 `LocalDate today = LocalDate.now(clock.withZone(businessProperties.businessZone()))`. 기대 날짜는 `SubscriptionSchedule`을 쓰지 않고 JDK로 직접 계산한다(구현을 구현으로 검증하지 않는다): 월요일 주기의 "이 날 포함 첫 날" = `날짜.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))`, "이 날 제외 다음 날" = `날짜.with(TemporalAdjusters.next(DayOfWeek.MONDAY))`.
- 가게 상태: JDBC `shop.shop.status`. 회원 상태: JDBC `member.member.status`(각각 한 케이스만 쓰는 값 하나라 한 줄로 읽는다). 빌링키: 5-1의 `billingKeyOf(billingKeyId)`.

#### `order/domain/SubscriptionScheduleTest` (단위 테스트)

기준 날짜: 2026-10-05는 월요일, 2026-10-06은 화요일이다.

| 케이스 | 동작 | 확인할 것 |
|---|---|---|
| WEEKLY 월(1), 그날 포함 | `new SubscriptionSchedule(WEEKLY, 1).firstOnOrAfter(2026-10-05)` | 2026-10-05 |
| WEEKLY 월(1), 화요일부터 | `firstOnOrAfter(2026-10-06)` | 2026-10-12 |
| WEEKLY 월(1), 그날 제외 | `nextAfter(2026-10-05)` | 2026-10-12 |
| MONTHLY 28, 1월 28일 다음 | `new SubscriptionSchedule(MONTHLY, 28).nextAfter(2026-01-28)` | 2026-02-28 |
| MONTHLY 1, 12월 1일 다음 | `new SubscriptionSchedule(MONTHLY, 1).nextAfter(2026-12-01)` | 2027-01-01 |
| MONTHLY 10, 그날 포함 | `new SubscriptionSchedule(MONTHLY, 10)`의 `firstOnOrAfter(2026-10-10)` / `firstOnOrAfter(2026-10-11)` | 2026-10-10 / 2026-11-10 |
| 범위 밖 | `@ParameterizedTest`: `(WEEKLY, 0)`, `(WEEKLY, 8)`, `(MONTHLY, 0)`, `(MONTHLY, 29)`로 생성 | `IllegalArgumentException` |
| 범위 경계는 통과 | `(WEEKLY, 1)`, `(WEEKLY, 7)`, `(MONTHLY, 1)`, `(MONTHLY, 28)`로 생성 | 예외 없음 |

#### `order/domain/SubscriptionStatusTest` (단위 테스트)

5개 상태의 모든 쌍 25개를 `@ParameterizedTest`로 돌린다(허용 쌍 목록에서 나머지를 계산해 만든다).

| 케이스 | 확인할 것 |
|---|---|
| 허용된 전이 10쌍 | `ACTIVE→PAUSED`, `ACTIVE→SUSPENDED`, `ACTIVE→CANCELLED`, `ACTIVE→TERMINATED`, `PAUSED→ACTIVE`, `PAUSED→CANCELLED`, `PAUSED→TERMINATED`, `SUSPENDED→ACTIVE`, `SUSPENDED→CANCELLED`, `SUSPENDED→TERMINATED`는 `transitTo`가 예외 없이 도착 상태를 돌려준다 |
| 허용되지 않은 전이 15쌍 | 나머지 15쌍(자기 자신 5쌍, `CANCELLED`·`TERMINATED`에서 다른 상태로 나가는 4쌍씩 8쌍, `PAUSED→SUSPENDED`·`SUSPENDED→PAUSED` 2쌍)은 `BusinessException`이고 `errorCode`가 `INVALID_STATE_TRANSITION`이다 |

#### `order/domain/SubscriptionTest` (단위 테스트)

공통 준비: `Subscription.subscribe(memberId, shopId, productId, 2, new SubscriptionSchedule(WEEKLY, 1), address, billingKeyId, Money.of(10000), LocalDate.of(2026, 10, 6))` → 월요일 주기, `nextRunDate` = 2026-10-12(월), `consecutiveFailures` = 0. 상태·실패 수는 `ReflectionTestUtils.setField`로 만든다. `address`는 `new ShippingAddress("recipient", "010-1111-1111", "zipcode", "address1", "address2")`.

| 케이스 | 준비 | 동작 | 확인할 것 |
|---|---|---|---|
| 재개 후 다음 실행일 재계산 | `status` = `SUSPENDED`, `consecutiveFailures` = 3, `nextRunDate` = 2026-10-05 | `resume(LocalDate.of(2026, 10, 7))`(수) | `status` = `ACTIVE`, `nextRunDate` = 2026-10-12, `consecutiveFailures` = 0 |
| 재개: 내일이 주기 날이면 내일 | `status` = `PAUSED`, `consecutiveFailures` = 1 | `resume(LocalDate.of(2026, 10, 11))`(일) | `status` = `ACTIVE`, `nextRunDate` = 2026-10-12, `consecutiveFailures` = 0 |
| ACTIVE 재개 거부 | 기본(ACTIVE) | `resume(LocalDate.of(2026, 10, 7))` | `BusinessException`, `errorCode` = `INVALID_STATE_TRANSITION` / `nextRunDate` 그대로 2026-10-12 |
| 종료 두 번 | 기본(ACTIVE) | `terminate("PRODUCT_DISCONTINUED")` → `terminate("SHOP_CLOSED")` | 첫 호출 뒤 `status` = `TERMINATED`, `endReason` = `PRODUCT_DISCONTINUED` / 두 번째는 예외 없이 끝나고 `status` = `TERMINATED`, `endReason`은 그대로 `PRODUCT_DISCONTINUED` |
| 해지된 구독의 종료 | 기본 → `cancel()` | `terminate("PRODUCT_DISCONTINUED")` | 예외 없음, `status` = `CANCELLED`, `endReason` = `USER_CANCEL` 그대로 |
| 해지·종료 뒤 수정 거부 | `cancel()`한 구독 / `terminate("SHOP_CLOSED")`한 구독 | `change(5, null, null)` | 둘 다 `BusinessException`, `errorCode` = `INVALID_STATE_TRANSITION` / `quantity` 그대로 2 |
| 수정: null은 유지 | 기본(ACTIVE) | `change(5, null, null)` | `quantity` = 5, 배송지 필드와 `billingKeyId`는 준비 때 값 그대로 |

#### `order/web/SubscriptionControllerTest` (`@AutoConfigureMockMvc`)

공통 준비
- 판매자 `seller@test.com`(`signup` → `sellerId`, `login` → 판매자 토큰), `shopId = openShop(sellerId, "1111111111")`, `productId = registerSubscribableProduct(sellerId, shopId, 12000, 10)`.
- 구매자 `buyer@test.com`(`signup` → `buyerId`, `login` → 토큰), `addressId = address(buyerId)`, `billingKeyId = registerBillingKey(buyerId)`.
- 신청 요청: `POST /api/subscriptions`, `Authorization: Bearer {구매자 토큰}`, `Idempotency-Key`(새 UUID), 본문 `{"productId": productId, "quantity": 2, "cycle": {"type": "WEEKLY", "value": 1}, "addressId": addressId, "billingKeyId": billingKeyId, "startDate": 내일}`. 케이스가 바꾸는 값만 아래에 적는다.

**1. 신청 성공** — 동작: 위 신청 요청 그대로.
- 응답: 201, `$.subscriptionId` 존재(UUID).
- `subscriptionIdsOf(buyerId)`가 `$.subscriptionId` 한 건이다. `s = subscriptionSnapshotOf($.subscriptionId)`: `memberId()` = `buyerId`, `shopId()` = `shopId`, `productId()` = `productId`, `quantity()` = 2, `cycleType()` = `WEEKLY`, `cycleValue()` = 1, `billingKeyId()` = `billingKeyId`, `unitPrice()` = 12000, `status()` = `ACTIVE`.
- `nextRunDate()` = `내일.with(nextOrSame(MONDAY))`.
- `shippingAddress().get("recipient")` = `recipient`, `.get("address1")` = `address1`.

**2. 구독 불가 상품 거부** — 준비: `registerProduct(sellerId, shopId, 12000, 10)`(`subscribable = false`)로 만든 `plainProductId`. 동작: `productId` 자리에 `plainProductId`.
- 응답: 422, `$.code` = `PRODUCT_NOT_SUBSCRIBABLE`.
- `subscriptionIdsOf(buyerId)`가 비어 있다.

**3. 자기 가게 상품 거부** — 준비: `address(sellerId)`, `registerBillingKey(sellerId)`. 동작: 판매자 토큰으로, `addressId`·`billingKeyId` 자리에 판매자 것을 넣어 신청.
- 응답: 422, `$.code` = `OWN_SHOP_PRODUCT`.
- `subscriptionIdsOf(sellerId)`가 비어 있다.

**4. 오늘 시작 거부** — 동작: `startDate` = 오늘.
- 응답: 400, `$.code` = `INVALID_REQUEST`.
- `subscriptionIdsOf(buyerId)`가 비어 있다.

**5. 주기 값 범위 밖 거부** — `@ParameterizedTest`. 동작: `cycle`을 `{"type":"WEEKLY","value":8}` / `{"type":"MONTHLY","value":29}`로 바꿔 신청.
- 응답: 400, `$.code` = `INVALID_REQUEST`.
- `subscriptionIdsOf(buyerId)`가 비어 있다.

**6. 남의 빌링키로 신청 거부** — 준비: `owner@test.com` 가입 + `ownerKeyId = registerBillingKey(ownerId)`. 동작: `billingKeyId` 자리에 `ownerKeyId`.
- 응답: 404, `$.code` = `BILLING_KEY_NOT_FOUND`.
- `subscriptionIdsOf(buyerId)`가 비어 있다.

**7. 남의 구독 접근 거부** — 준비: `subscriptionId = createSubscription(buyerId, productId, billingKeyId)`, 다른 회원 `other@test.com` 가입·로그인(`otherToken`). 동작: `otherToken`으로 `GET /api/subscriptions/{subscriptionId}`, `POST /api/subscriptions/{subscriptionId}/pause`, `POST /api/subscriptions/{subscriptionId}/cancel`을 차례로 보낸다.
- 세 요청 모두 응답 404, `$.code` = `SUBSCRIPTION_NOT_FOUND`.
- `subscriptionSnapshotOf(subscriptionId)`: `status()` = `ACTIVE`, `endReason()` = null.

**8. 활성 구독이 있으면 가게 폐업 거부** — 준비: `subscriptionId = createSubscription(buyerId, productId, billingKeyId)`. 동작: 판매자 토큰으로 `DELETE /api/shops/{shopId}`.
- 응답: 422, `$.code` = `SHOP_HAS_ACTIVE_ORDERS`, `$.details.reason` = `ACTIVE_SUBSCRIPTION`.
- `shop.shop.status` = `ACTIVE`.
- 이어서 구매자 토큰으로 `POST /api/subscriptions/{subscriptionId}/cancel` → 200 → 판매자 토큰으로 다시 `DELETE /api/shops/{shopId}` → 204.

**9. 활성 구독이 있으면 회원 탈퇴 거부** — 준비: `createSubscription(buyerId, productId, billingKeyId)`. 동작: 구매자 토큰으로 `DELETE /api/members/me`.
- 응답: 422, `$.code` = `MEMBER_HAS_ACTIVE_RESOURCES`, `$.details.reasons` = `["ACTIVE_SUBSCRIPTION"]`.
- `member.member.status` = `ACTIVE`(그 구매자).
- 이어서 `POST /api/subscriptions/{subscriptionId}/cancel` → 200 → 다시 `DELETE /api/members/me` → 204.

**10. 쓰는 빌링키 삭제 거부** — 준비: `subscriptionId = createSubscription(buyerId, productId, billingKeyId)`. 동작: 구매자 토큰으로 `DELETE /api/billing-keys/{billingKeyId}`.
- 응답: 409, `$.code` = `BILLING_KEY_IN_USE`.
- `billingKeyOf(billingKeyId)`: `status()` = `ACTIVE`, `deletedAt()` = null.
- 이어서 `POST /api/subscriptions/{subscriptionId}/cancel` → 200 → 다시 `DELETE /api/billing-keys/{billingKeyId}` → 204.

**11. 구독 목록에는 내 구독만 나온다** — 준비: 7번과 같은 준비. 동작: `otherToken`으로 `GET /api/subscriptions`.
- 응답: 200, `$.items.length()` = 0.

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

**구현 순서**: 1) 마이그레이션 → 2)~3) 체크아웃 리팩터링(`OrderType` → `Order.checkout`·`markPaymentInProgress` → `PlaceOrderCommand` → `OrderPlacer` → `CheckoutService`·`OrderPaymentApplier`, 2-2 테스트가 그대로 통과하는지 확인) → 4)~7) payment 변경(payment.api → payment.domain → payment.infrastructure → payment.application) → 8)~11) order 회차 실행(order.domain → order.api.event → order.infrastructure → order.application. `SubscriptionCycleJob`은 `SubscriptionCycleService` 다음에 만든다) → 12) 회차 이력 API → 13) 알림 매핑 → 테스트 준비 → 테스트.

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

**2) order.domain** — 체크아웃 리팩터링

지금 `CheckoutService.checkout`의 `Order.checkout` → `save` → `productApi.reserve` 부분(2-2의 7·8번)을 `OrderPlacer`(`order.application`)로 옮긴다. 끝나면 **2-2 테스트가 그대로 통과**해야 한다(리팩터링).

| 클래스 | 규격 |
|---|---|
| `OrderType` | `enum { NORMAL, SUBSCRIPTION }`. 지금 코드는 `Order.type`이 `String`이고 `"NORMAL"`을 고정해 넣는다. enum으로 바꿀지 문자열을 유지할지는 문서에 없다(확인 필요 — 아래 `order.getType() == SUBSCRIPTION`·`type == NORMAL` 비교는 enum 기준으로 적혀 있고 2-2 `OrderDetailResult.type`은 `String`이다). `Order.checkout`, `OrderPaymentApplier`가 쓴다 |
| `Order.checkout` (기존 메서드 수정) | 인자에 `OrderType type`을 더하고 `order.type`에 넣는다. `OrderPlacer.place`가 부른다 |
| `Order.markPaymentInProgress()` (신규) | `status = status.transitTo(PAYMENT_IN_PROGRESS)`(`PENDING_PAYMENT → PAYMENT_IN_PROGRESS`, 그 밖의 상태는 `INVALID_STATE_TRANSITION`). 구독 회차 주문은 만든 트랜잭션 안에서 바로 결제를 시작한다. 커밋 전이라 구매자 취소·만료 같은 다른 흐름이 이 주문을 볼 수 없어 2-4 `startPayment`의 CAS(그런 흐름과의 경쟁을 막는 장치)가 지킬 경쟁이 없고, CAS를 거치면 방금 만든 주문에 항상 성공하는 UPDATE와 0행 처리만 늘어난다. 그래서 CAS를 거치지 않고 엔티티를 바꿔 커밋 때 더티 체킹으로 저장한다. `SubscriptionCycleService.runDue` 트랜잭션 1의 6이 부른다 |

**3) order.application** — 체크아웃 리팩터링

| 클래스 | 메서드 |
|---|---|
| `PlaceOrderCommand` | `record (UUID memberId, OrderType type, List<CheckoutLine> lines, ShippingAddress address)`. `OrderPlacer.place`의 입력이다 |
| `OrderPlacer` (`@Component`) | `Order place(PlaceOrderCommand command)` |
| `CheckoutService` (기존 수정) | 상품·가게·배송지 확인 뒤 그 부분 대신 `orderPlacer.place(new PlaceOrderCommand(memberId, NORMAL, lines, address))`를 부른다. 체크아웃은 장바구니를 건드리지 않으므로 `CheckoutService`에 남길 장바구니 처리는 없다 |
| `OrderPaymentApplier.complete` (기존 수정, 2-4) | 장바구니 차감(`cartService.deductOrdered`)에 `order.getType() == NORMAL` 조건을 더한다. 구독 회차 주문이 구매자의 장바구니를 건드리면 안 된다 |

`OrderPlacer` — **"주문 저장 + 재고 예약"만 하는 작은 컴포넌트.** 불러 주는 쪽은 `CheckoutService`(트랜잭션 안)와 `SubscriptionCycleService.runDue`의 트랜잭션 1이다. 의존성은 `OrderRepository`, `ProductApi`, `Clock`, `OrderProperties`(`paymentTtl`)다. 상품·가게·배송지 확인은 하지 않는다(호출자가 이미 했다).
- `place`: 주문을 저장하고 재고를 예약한다. **호출자의 트랜잭션 안에서** 실행된다(자기 트랜잭션을 열지 않는다). 명령 → 저장된 `Order`(PENDING_PAYMENT)

`place` 순서
1. `Order order = Order.checkout(command.memberId(), command.type(), command.lines(), command.address(), clock.instant(), orderProperties.paymentTtl())`
2. `Order saved = orderRepository.save(order)`
3. `productApi.reserve(saved.getId(), items, saved.getExpiresAt())` — `items`는 `command.lines()`의 `new ReserveItem(productId, quantity)`. 재고 부족이면 422 `OUT_OF_STOCK`(예외 → 호출자의 트랜잭션이 롤백되어 2의 주문도 사라진다)
4. `saved` 반환

**4) payment.api**

**빌링 결제의 `payment_key`**: `payment.payment_key`는 NOT NULL·UNIQUE인데 빌링 결제는 요청 시점에 Toss의 `paymentKey`가 없다. 그래서 ① `begin`에는 자리 값 `"BILLING-" + orderId`를 `StartPayment.paymentKey`로 넣고(주문당 결제가 1건이라 unique하다. `paymentId`는 `begin` 안에서 만들어져 호출 전에는 알 수 없다. Toss가 발급하는 값과 겹치지 않도록 접두어를 둔다), ② 승인되면 Toss가 응답으로 준 `PgResult.Approved.paymentKey`로 **덮어쓴다**. 승인 전에 실패한 빌링 결제에는 자리 값이 남지만 취소 대상이 아니다(승인된 결제만 취소한다). 덮어써야 2-6 보상 취소·2-8 환불이 `payment.paymentKey`로 Toss 취소를 호출할 수 있다.

이름·시그니처는 권장안이다.

| 클래스 | 규격 |
|---|---|
| `StartPayment` (기존 record 수정) | 마지막 컴포넌트로 `UUID billingKeyId`를 더한다(일반 결제는 `null`). 어느 빌링키로 결제했는지 payment가 기억해 두는 용도다. 기존 `new StartPayment(...)` 호출(`ConfirmPaymentService`, `PaymentApiImplTest`)에 `null`을 더한다. `PaymentApiImpl.begin`과 5-3 `SubscriptionCycleService` 트랜잭션 1이 쓴다 |
| `PgOutcome` (기존 record 수정) | 마지막 컴포넌트로 `String paymentKey`를 더한다(`APPROVED`일 때만 값이 있고 아니면 `null`). 기존 `new PgOutcome(...)` 호출 전부(`PaymentApiImpl`의 `callConfirm`·`queryPgOutcome`, `PaymentApiImplTest`)의 인자가 하나 늘어난다. `markApproved`와 `PgResult` → `PgOutcome` 변환이 쓴다 |
| `PaymentApi.callBillingCharge` (기존 인터페이스에 추가) | `PgOutcome callBillingCharge(UUID paymentId, String orderName)` — `callConfirm`의 빌링 버전. **트랜잭션 밖 전용**이고 payment 상태는 바꾸지 않는다(결과는 호출자가 `applyOutcome`/`OrderPaymentApplier`로 반영). `orderName`은 Toss 결제 내역에 보일 이름이다. 5-3 `SubscriptionCycleService` 2가 부른다 |

**5) payment.domain**

| 클래스 | 규격 |
|---|---|
| `Payment.begin` (기존 메서드 수정) | `UUID billingKeyId` 인자를 더해 `billingKeyId` 필드(`payment.billing_key_id`, 2-4 마이그레이션에 이미 있는 컬럼)에 넣는다 |
| `PaymentRepository.markApproved` (기존 수정) | `String paymentKey` 인자를 더한다 |

**6) payment.infrastructure**

| 클래스 | 규격 |
|---|---|
| `JpaPaymentRepository.markApproved` (기존 수정) | 쿼리의 `set`에 `p.paymentKey = :paymentKey`를 더한다(`where`는 그대로). 일반 결제(2-4)는 클라이언트가 보낸 값과 응답 값이 같아 갱신해도 변하지 않는다 |

**7) payment.application**

| 클래스 | 메서드 |
|---|---|
| `PaymentApiImpl.begin` (기존 수정) | `Payment.begin`에 `command.billingKeyId()`를 넘긴다 |
| `PaymentApiImpl.applyOutcome` (기존 수정) | `markApproved`에 `outcome.paymentKey()`를 넘긴다. 그래서 `APPROVED`인 `PgOutcome`은 `paymentKey`가 비어 있으면 안 된다(`PaymentApiImplTest`가 직접 만드는 `APPROVED`도 키를 채운다) |
| `PaymentApiImpl.callConfirm`·`queryPgOutcome` (기존 수정, 2-6) | `PgResult` → `PgOutcome` 변환의 `Approved` 분기에서 `approved.paymentKey()`를 `PgOutcome.paymentKey`에 넣는다. 대사로 닫히는 빌링 결제도 자리 값이 바뀌어야 하므로 `queryPgOutcome` 변환도 고친다 |
| `PaymentApiImpl.callBillingCharge` (신규) | `PaymentApi.callBillingCharge`의 구현. `PaymentApiImpl`에 `BillingKeyRepository`·`AesGcmEncryptor`를 주입한다. 순서는 아래 |

`callBillingCharge` 순서
1. 짧은 읽기 트랜잭션(`TransactionTemplate`)으로 `Payment payment = findById(paymentId)` — 없으면 `IllegalStateException`(`begin`을 거친 뒤에만 부르므로 없으면 호출 순서 버그다. `callConfirm`도 같다)
2. `payment.getBillingKeyId()`로 `BillingKey`를 읽는다(`BillingKeyRepository.findById`) — 없으면(일반 결제의 payment이거나 지워진 키) `IllegalStateException`
3. `String billingKey = aesGcmEncryptor.decrypt(billingKeyEntity.getBillingKeyEncrypted())` — 복호화한 값은 로그·예외 메시지에 남기지 않는다
4. `PgResult result = tossClient.billingCharge(payment.getId(), billingKey, billingKeyEntity.getCustomerKey(), payment.getPgOrderId(), payment.getAmount(), orderName)`
5. `PgResult` → `PgOutcome` 변환: `Approved` → APPROVED + method·approvedAt·paymentKey, `Rejected` → REJECTED + code, `Unknown` → UNKNOWN + reason

대사(2-6)는 빌링 결제도 같은 `queryPgOutcome`(`pgOrderId`로 `GET /v1/payments/orders/{orderId}`)으로 처리된다. 새 메서드는 필요 없다.

**8) order.domain** — 회차 실행

이름·시그니처는 권장안이다(아래에서 이름을 쓰는 `insertIfAbsent`·`runDue`·`onOrderClosed`는 그대로).

| 클래스 | 규격 |
|---|---|
| `CycleStatus` | 회차 상태 `ORDERED, PAID, FAILED, SKIPPED`. 전이: `ORDERED → {PAID, FAILED}` (SKIPPED는 처음부터 SKIPPED로 만든다) |
| `SubscriptionCycle` (entity) | **"이 구독의 이 날짜 회차를 처리했다"는 기록 한 줄.** `@Entity @Table(name = "subscription_cycle", schema = "orders")`, `extends BaseTimeEntity`, 필드는 1) 마이그레이션의 컬럼과 1:1(`@Version Long version` 포함). 도메인 메서드: `void linkOrder(UUID orderId, Money appliedUnitPrice)`(주문 연결), `void markPaid()`(`ORDERED → PAID`), `void markFailed(String failureReason)`(`ORDERED → FAILED`). `runDue`, `onOrderClosed`가 쓴다 |
| `SubscriptionCycleRepository` | `int insertIfAbsent(UUID id, UUID subscriptionId, LocalDate cycleDate, String status, String failureReason, Instant now)`(native. 동작은 `order.infrastructure`의 `JpaSubscriptionCycleRepository`), `Optional<SubscriptionCycle> findById(UUID id)`, `Optional<SubscriptionCycle> findByOrderId(UUID orderId)`, 회차 이력 API(`order.web`)의 keyset 조회(`subscription_id = :id`, `cycle_date` 내림차순, `size + 1`건. 인자·커서 기준은 문서에 없다 — 확인 필요). `runDue`, `onOrderClosed`, 회차 이력 API가 쓴다 |
| `SubscriptionRepository.findDueIds` (5-2 인터페이스에 추가) | `List<UUID> findDueIds(LocalDate today, UUID lastId, Limit limit)` — `status = ACTIVE AND next_run_date <= :today AND id > :lastId ORDER BY id`. `=`가 아니라 `<=`인 이유는 서버가 멈춰 있던 날의 구독도 다음 실행에서 처리하기 위해서다. 인덱스 `idx_subscription_status_next_run_date`가 받친다. 첫 페이지의 `lastId`를 어떻게 줄지는 문서에 없다(확인 필요: 첫 페이지 메서드를 따로 두거나 `new UUID(0, 0)`을 넘긴다). `runDue`가 쓴다 |
| `Subscription.applyPendingPrice` | `Money applyPendingPrice(LocalDate today)` — `pendingEffectiveDate != null && !pendingEffectiveDate.isAfter(today)`이면 `unitPrice = pendingUnitPrice`, pending 두 칸을 null로 승격하고 승격한 가격을 돌려준다(POL-03·11). 아니면 `unitPrice`를 돌려준다. 트랜잭션 1이 롤백되면 승격도 함께 사라진다. `runDue` 트랜잭션 1의 3이 부른다 |
| `Subscription.skipCycle` | `void skipCycle(LocalDate today)` — `nextRunDate = schedule.nextAfter(today)`. `consecutiveFailures`는 그대로(결제 실패가 아니다). `runDue`의 건너뛰기가 부른다 |
| `Subscription.recordCyclePaid` | `void recordCyclePaid(LocalDate cycleDate)` — `consecutiveFailures = 0`, `nextRunDate = schedule.nextAfter(cycleDate)`. `onOrderClosed` 2가 부른다 |
| `Subscription.recordCycleFailed` | `boolean recordCycleFailed(LocalDate cycleDate)` — `consecutiveFailures + 1`. 3 이상이면 ACTIVE → SUSPENDED(`status.transitTo`, `nextRunDate`는 바꾸지 않는다. SUSPENDED는 잡 대상이 아니고 재개 때 `resume`이 다시 계산한다)하고 `true`, 아니면 `nextRunDate = cycleDate.plusDays(1)`로 두고 `false`(호출자가 어떤 이벤트를 낼지 가른다). `onOrderClosed` 3이 부른다 |

**9) order.api.event**

| 클래스 | 규격 |
|---|---|
| `OrderTopics.SUBSCRIPTION_STATUS_CHANGED` + `SubscriptionStatusChangedEvent` | 토픽 `"order.subscription-status-changed.v1"`, `record (UUID subscriptionId, UUID memberId, String from, String to, String reason)` — `from`·`to`는 `SubscriptionStatus.name()`. 이 문서에서 발행하는 곳은 SUSPENDED(5-3)와 TERMINATED(5-4)뿐이다. 구매자의 일시정지·재개·해지(5-2)에서도 내는지는 문서에 없다(확인 필요). `onOrderClosed` 3, 5-4가 발행한다 |
| `OrderTopics.SUBSCRIPTION_CYCLE_FAILED` + `SubscriptionCycleFailedEvent` | 토픽 `"order.subscription-cycle-failed.v1"`, `record (UUID subscriptionId, UUID cycleId, UUID memberId, int consecutiveFailures, String reason)`. `runDue` 건너뛰기, `onOrderClosed` 3이 발행한다 |

이벤트는 상태 변경과 **같은 트랜잭션**에서 `outboxPublisher.publish(topic, "SUBSCRIPTION", subscriptionId, event)`로 낸다(Kafka 키 = 구독 ID라 같은 구독의 이벤트 순서가 지켜진다. `aggregateType`·키는 문서에 명시가 없어 3-3 규칙(키 = 집계 ID)에서 유추한 값이다 — 확인 필요).

**10) order.infrastructure**

| 클래스 | 규격 |
|---|---|
| `JpaSubscriptionCycleRepository` | `JpaRepository<SubscriptionCycle, UUID>`와 `SubscriptionCycleRepository`를 함께 상속하고 native 메서드는 여기에 `@Modifying @Query(nativeQuery = true)`로 쓴다. `insertIfAbsent`: `ON CONFLICT ON CONSTRAINT uk_subscription_cycle_subscription_date DO NOTHING`, 반환 1 = 새로 만듦, 0 = 이미 있음. `failureReason`은 SKIPPED 행의 사유이고 ORDERED 행은 `null`이다. native SQL이라 JPA 감사·`@Version`이 동작하지 않으므로 `version = 0`, `created_at = updated_at = :now`를 INSERT 문에 직접 넣는다. 엔티티가 필요하면 이어서 `findById(cycleId)`로 읽는다(같은 트랜잭션이라 방금 넣은 행이 보인다) |
| `OrderTopicConfig` (3-3, 기존 클래스에 추가) | 위 두 토픽마다 `@Bean NewTopic`을 둘씩 더한다: `TopicBuilder.name(OrderTopics.X).partitions(3).replicas(1).build()`와, 이름에 `".dlt"`를 붙인 같은 설정. 기동 시 토픽을 만든다 |
| `SubscriptionCycleJob` | 스케줄러 연결. `@Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")` → `jobRunner.run("subscription-cycles", service::runDue)` (`JobRunner`는 2-5, 같은 잡이 서버 두 대에서 동시에 돌지 않게 하는 advisory lock 포함) |

**11) order.application** — 회차 실행

| 클래스 | 메서드 |
|---|---|
| `SubscriptionCycleResultHandler` (`@Component`) | `@Transactional(propagation = MANDATORY) void onOrderClosed(Order order)` |
| `OrderPaymentApplier` (기존 수정, 2-4) | `SubscriptionCycleResultHandler`를 주입받아 `complete`·`fail` 끝에서 `order.getType() == SUBSCRIPTION`이면 `cycleResultHandler.onOrderClosed(order)`를 부른다. 2-6의 `failOrder`도 `fail`을 거치므로 보상 취소로 실패한 구독 주문도 같은 경로로 회차를 닫는다. 결제 결과 반영(`apply`·`failOrder`)에서 불린다 |
| `SubscriptionCycleService` (`@Transactional` 없음) | `public int runDue()` |

`SubscriptionCycleResultHandler` — **주문의 결제 결과가 정해졌을 때 회차·구독 쪽을 갱신하는 곳.** 의존성: `SubscriptionCycleRepository`, `SubscriptionRepository`, `OutboxPublisher`, `Clock`.

`SubscriptionCycleService` — **오늘 실행할 구독을 모아 회차를 하나씩 처리하는 서비스.** 의존성: `SubscriptionRepository`, `SubscriptionCycleRepository`, `OrderPlacer`, `ProductApi`, `ShopApi`, `PaymentApi`, `OrderPaymentApplier`, `OutboxPublisher`, `TransactionTemplate`, `Clock`, `BusinessProperties`. `SubscriptionCycleJob`이 부르고 테스트도 직접 부른다.

`runDue()` — **오늘(업무 날짜) 실행해야 하는 구독을 모두 처리하고, 처리한 건수를 돌려준다**(`JobRunner`가 로그에 남기는 값. 정의가 문서에 없어 "회차를 만든 구독 수"(SKIPPED 포함)로 두면 된다 — 확인 필요).
- 대상·읽는 법: `findDueIds`를 id 오름차순 100건씩, 마지막 id를 `lastId`로 넘기며 빈 결과가 나올 때까지 반복한다. 처리하면 `next_run_date`가 바뀌어 대상에서 빠지므로 OFFSET으로는 건너뛰는 행이 생긴다. 회차 날짜(`cycle_date`)는 `next_run_date`가 아니라 **오늘**(실행한 날짜)이다.
- 구독마다 `try/catch`로 감싸 한 건의 실패가 나머지를 막지 않게 하고, 예외는 `log.error`로 남긴다.

구독 한 건의 처리 — 트랜잭션 세 구간(Part 2 공통 규칙 K: Toss 호출을 트랜잭션 밖에). 구간 사이로 넘길 값(`orderId`, `paymentId`, `orderName`)은 트랜잭션 1이 작은 record로 돌려준다. 건너뛰었거나 이미 처리된 경우는 null이다(다음 구간이 쓸 값만 담는 record, 이름 자유).
1. **트랜잭션 1 (회차 + 주문)** — `tx.execute(...)`
   1. 구독을 다시 읽어(`findById`, 목록 조회 이후 상태가 바뀌었을 수 있다) ACTIVE이고 `nextRunDate <= today`인지 확인한다. 아니면 null로 종료
   2. `UUID cycleId = Ids.newId()` → `insertIfAbsent(cycleId, subId, today, "ORDERED", null, now)` → **0이면 종료**(오늘 회차가 이미 있다 — 같은 날 두 번 실행돼도 여기서 멈춘다). 두 스레드가 동시에 같은 구독에 들어오면 Postgres는 두 번째 INSERT를 첫 트랜잭션이 끝날 때까지 기다리게 하고, 첫 쪽이 커밋하면 0을 돌려준다(첫 쪽이 롤백하면 두 번째가 INSERT에 성공한다) → 주문이 하나다(INV-08). 이어서 `findById(cycleId)`로 회차 엔티티를 얻는다
   3. 적용가 결정·승격: `Money appliedPrice = subscription.applyPendingPrice(today)`
   4. 주문 줄 만들기: `ProductForCheckout product = productApi.getPurchasable(List.of(productId)).get(0)`, `ShopInfo shop = shopApi.requireActiveShops(List.of(shopId)).get(0)`를 읽어 `new CheckoutLine(product.productId(), product.shopId(), shop.name(), product.name(), product.thumbnailKey(), appliedPrice, 구독의 수량)`(order.domain, 필드 순서 `productId, shopId, shopName, productName, thumbnailKey, unitPrice, quantity`) 하나를 만든다. 일반 체크아웃과 다른 점은 `unitPrice`가 상품 현재가가 아니라 `appliedPrice`라는 것뿐이다. `address`는 구독의 `shippingAddress`. **`PRODUCT_NOT_ON_SALE`·`SHOP_NOT_ACTIVE`가 나는 곳이 바로 여기(`getPurchasable`·`requireActiveShops`)다**
   5. `Order order = orderPlacer.place(new PlaceOrderCommand(memberId, SUBSCRIPTION, lines, address))` — 재고 부족이면 여기서 `OUT_OF_STOCK`. 이어서 `cycle.linkOrder(order.getId(), appliedPrice)`
   6. `order.markPaymentInProgress()` — 결제를 시작한 상태로 바꾼다. 2-4 `ConfirmPaymentService`의 `startPayment` CAS는 쓰지 않는다
   7. `paymentApi.begin(new StartPayment(order.getId(), memberId, order.getTotalAmount(), order.pgOrderId(), "BILLING-" + order.getId(), billingKeyId))` → `paymentId`
   8. `orderName = product.name() + " 정기구독"` — Toss 표시용이고 100자 제한이다. 넘길 때 어떻게 줄이는지는 문서에 없다(확인 필요)
   - 이 트랜잭션은 한 덩어리라 중간에 죽으면 회차도 주문도 남지 않는다 → 다음 실행이 처음부터 다시 한다(잠금·리스가 필요 없다)
   - **건너뛰기**: 4·5에서 `OUT_OF_STOCK`·`PRODUCT_NOT_ON_SALE`·`SHOP_NOT_ACTIVE`(`BusinessException`의 `errorCode`로 판별) → 트랜잭션 1은 롤백(회차 INSERT도 취소됨) → **트랜잭션 1'**(새 `tx.execute`): `UUID skippedId = Ids.newId()` → `insertIfAbsent(skippedId, subId, today, "SKIPPED", 사유, now)` → 0이면 종료(오늘 회차를 다른 실행이 먼저 만들었다) → 구독을 다시 읽어(롤백된 트랜잭션의 엔티티는 쓰지 않는다) `skipCycle(today)` → `SUBSCRIPTION_CYCLE_FAILED` 이벤트(`cycleId`는 `skippedId`, reason은 사유와 같은 값 `SKIPPED_*`, 예: `SKIPPED_OUT_OF_STOCK` — 정확한 문자열은 문서에 없다, 확인 필요)
2. **트랜잭션 밖**: `PgOutcome outcome = paymentApi.callBillingCharge(paymentId, orderName)` — 빌링키 복호화 + Toss 호출. 실패해도 예외가 아니라 `PgOutcome`(REJECTED·UNKNOWN)으로 온다
3. **트랜잭션 2**: `tx.execute(s -> orderPaymentApplier.apply(orderId, paymentId, outcome))` — payment 결과를 반영하고 주문을 PAID·PAYMENT_FAILED로 바꾼다. 이때 applier가 아래 `onOrderClosed`를 부른다. UNKNOWN이면 주문은 PAYMENT_IN_PROGRESS, 회차는 ORDERED로 남고, 2-6 대사가 나중에 같은 `apply` 경로로 닫는다. 트랜잭션 2가 예외로 실패해도 같은 이유로 대사가 이어서 처리한다(payment는 IN_PROGRESS로 커밋돼 있다)

`onOrderClosed(Order order)` — **주문이 PAID 또는 PAYMENT_FAILED로 정해질 때 그 주문의 회차와 구독을 갱신한다.** 호출하는 쪽은 `OrderPaymentApplier`(주문 트랜잭션 안)이고 `MANDATORY`라 트랜잭션 밖에서 부르면 예외다.
1. `SubscriptionCycle cycle = findByOrderId(order.getId())`, `Subscription subscription = findById(cycle.getSubscriptionId())`. 회차가 없을 때의 처리는 문서에 없다(확인 필요 — 주문 `type`이 SUBSCRIPTION이면 항상 있어야 하는 버그 상황이다)
2. 주문 PAID → `cycle.markPaid()`, `subscription.recordCyclePaid(cycle.getCycleDate())`
3. 주문 PAYMENT_FAILED → `cycle.markFailed(주문의 failureReason)`(예: `"PG 거절: {코드}"`), `boolean suspended = subscription.recordCycleFailed(cycle.getCycleDate())`
   - `suspended`면 `SUBSCRIPTION_STATUS_CHANGED`(from `ACTIVE`, to `SUSPENDED`, reason은 문서에 없다 — 확인 필요)
   - 아니면 `SUBSCRIPTION_CYCLE_FAILED`(`consecutiveFailures`는 증가한 값, reason은 `failureReason`)

**12) order.web** — `GET /api/subscriptions/{id}/cycles?cursor=&size=` → `CycleResponse(id, cycleDate, status, orderId, appliedUnitPrice, failureReason)` (최신 먼저)

- 내 구독의 회차 이력이다. 서비스는 먼저 `subscriptionRepository.findByIdAndMemberId(id, memberId)`로 소유를 확인하고(없으면 남의 것도 404 `SUBSCRIPTION_NOT_FOUND`) 그 구독의 회차를 keyset으로 읽는다(회차 이력 조회를 `size + 1`건 읽고, `size`건을 넘으면 `size`건만 남겨 `size`번째 항목으로 `nextCursor`를 만든다). `uk_subscription_cycle_subscription_date (subscription_id, cycle_date)` 인덱스가 `cycle_date` 내림차순 조회를 받친다. 이 서비스 메서드를 어느 클래스에 두는지, cursor를 `Cursor(createdAt, id)`로 쓸지 `cycle_date` 기준으로 새로 만들지는 문서에 없다(확인 필요).

**13) notification.domain · notification.application · notification.infrastructure** — 3-5 알림 매핑

`SubscriptionStatusChangedEvent`·`SubscriptionCycleFailedEvent`를 구독자(`memberId`)에게 알린다. notification 모듈에 아래 세 곳을 더한다.

| 클래스 | 규격 |
|---|---|
| `NotificationType` (notification.domain) | 값을 더한다 |
| `NotificationComposer` (notification.application) | 이벤트마다 `compose` 오버로드를 더한다(이벤트 → 회원 알림 `NotificationDraft`(3-5, `shopId`는 null), DB·다른 모듈 조회 없는 순수 변환) |
| `NotificationEventConsumer` (notification.infrastructure) | 토픽 메서드를 더한다(`@KafkaListener(topics = ..., groupId = "notification")` → `envelopes.parse` → `inboxGuard.runOnce("notification.{이벤트}", ...)` → `composer.compose` → `notificationService.enqueue(eventId, drafts)`) |

`NotificationType`의 값 이름과 제목·본문은 문서에 없다(확인 필요).

### 완료 확인

**테스트 준비** ([Part 2 공통 규칙 N-1](part2-order-money.md#n-1-단계별-testfixtures-추가)과 같은 방식)

아래 테스트를 쓰기 전에 만든다. 이 단계에서 추가하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `setSubscriptionNextRunDate(UUID subscriptionId, LocalDate nextRunDate)` → `void` | JDBC `UPDATE orders.subscription SET next_run_date = ? WHERE id = ?` | `SubscriptionCycleServiceTest`·`SubscriptionCycleControllerTest`(구독을 오늘 실행 대상으로 당긴다), 5-4 `SubscriptionEventTest`(다음 실행일을 오늘 + 5일로 옮긴다) |
| `moveCyclesToPast(UUID subscriptionId)` → `void` | JDBC `UPDATE orders.subscription_cycle c SET cycle_date = DATE '2000-01-01' + r.n FROM (SELECT id, (ROW_NUMBER() OVER (ORDER BY created_at, id))::int AS n FROM orders.subscription_cycle WHERE subscription_id = ?) r WHERE c.id = r.id` — 그 구독의 회차마다 서로 다른 먼 과거 날짜를 준다. 날짜를 한꺼번에 하루씩 당기는 식(`cycle_date - 1`)은 회차가 둘 이상일 때 갱신 순서에 따라 `(subscription_id, cycle_date)` unique에 걸려 쓰지 않는다 | `SubscriptionCycleServiceTest`("다음 날" 재실행), `SubscriptionCycleControllerTest` |
| `cyclesOf(UUID subscriptionId)` → `List<CycleRow>` | JDBC `SELECT id, cycle_date, status, order_id, applied_unit_price, failure_reason FROM orders.subscription_cycle WHERE subscription_id = ? ORDER BY created_at, id`. `CycleRow`(`support/`의 record) 필드: `id`(UUID), `cycleDate`(`LocalDate`), `status`(String), `orderId`(UUID, SKIPPED면 null), `appliedUnitPrice`(`Long`, SKIPPED면 null), `failureReason`(String, 없으면 null). 회차 테이블이 5-3에서 생기므로 5-2의 `subscriptionSnapshotOf`에 넣지 않고 따로 둔다 | `SubscriptionCycleServiceTest` 모든 케이스 |
| `orderRowsOf(UUID memberId)` → `List<OrderRow>` | JDBC로 `orders.orders`(`id`, `type`, `status`, `total_amount`, `failure_reason`)에 `orders.shop_order`·`orders.order_line`(`unit_price`, `quantity`, `line_amount`)과 `payment.payment`(`id`, `status`, `amount`, `billing_key_id`, `payment_key`, `pg_order_id`, `failure_code`, `LEFT JOIN`으로 `order_id` 기준)를 붙여 `WHERE orders.member_id = ? ORDER BY orders.created_at, orders.id`로 읽는다. `OrderRow` 필드: `id`(UUID), `type`·`status`·`failureReason`(String), `totalAmount`·`lineUnitPrice`·`lineAmount`(long), `lineQuantity`(int), `payment`(`PaymentRow`, 결제가 없으면 null). `PaymentRow` 필드: `id`·`billingKeyId`(UUID), `status`·`paymentKey`·`pgOrderId`·`failureCode`(String), `amount`(long). 구독 회차 주문은 가게주문·품목이 하나라서 조인 결과가 주문당 한 행이다. 가게주문·품목 상태는 담지 않는다(2-8a의 `testFixtures.snapshotOf(orderId)`로 읽는다: `s.shopOrderStatus()`, `s.line(productId).status()`) | `SubscriptionCycleServiceTest`, `SubscriptionCycleControllerTest`(결제 성공 주문의 id) |

이미 있는 헬퍼: 구독은 5-2의 `registerSubscribableProduct`·`createSubscription`, 빌링키는 5-1의 `registerBillingKey`로 만든다. `reconcileBatch()` 케이스의 `setOrderUpdatedAt(UUID orderId, Instant updatedAt)`은 2-6의 헬퍼로, JDBC로 `orders.orders.updated_at`을 바꾼다(`reconcileBatch()`는 30초 넘게 `PAYMENT_IN_PROGRESS`인 주문만 집어 가므로 시각을 60초 전으로 옮겨 쓴다).

헬퍼를 쓰는 방법은 다음과 같다.
- 응답: `jsonPath`. 에러 코드는 `$.code`.
- 구독: 5-2의 `s = testFixtures.subscriptionSnapshotOf(subscriptionId)`. 이 단계에서 읽는 값은 `s.status()`, `s.nextRunDate()`, `s.consecutiveFailures()`, `s.unitPrice()`, `s.pendingUnitPrice()`, `s.pendingEffectiveDate()`.
- 재고: `testFixtures.stockOf(productId)` → `available`, `reserved`, `sold`. 예약(`product.stock_reservation.status`, `WHERE order_id = ?`)과 장바구니 수량(`SELECT ci.quantity FROM orders.cart_item ci JOIN orders.cart c ON c.id = ci.cart_id WHERE c.member_id = ? AND ci.product_id = ?`)은 각각 한 케이스만 쓰는 값 하나라 한 줄로 읽는다.
- 이벤트: `OutboxAssert.rows("order.subscription-cycle-failed.v1")` 등. 각 행은 `OutboxSnapshot`이고, `payload()`(`JsonNode`)의 필드로 비교한다(UUID는 문자열, 숫자는 `asLong()`/`asInt()`).
- 가짜 PG가 받은 빌링 결제 요청: `pg.requests("/v1/billing/bk_")`(발급 요청 `/v1/billing/authorizations/issue`는 걸리지 않는다). `path()`, `headers().get("idempotency-key")`(소문자), `body()`를 `readTree`로 읽는다(금액은 `asLong()`). 빌링키 원문과 `customerKey`: 5-1의 `billingKeyOf(billingKeyId).billingKey()`·`.customerKey()`. 일반 승인 요청은 `pg.requests("/v1/payments/confirm")`.
- 오늘 날짜: 테스트 클래스에 `Clock`, `BusinessProperties`를 주입하고 `LocalDate today = LocalDate.now(clock.withZone(businessProperties.businessZone()))`. 기대 날짜는 `SubscriptionSchedule`을 쓰지 않고 JDK로 계산한다: `nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))`(오늘이 월요일이면 7일 뒤).
- 시간 흉내: `Clock`을 바꾸지 않고 JDBC로 날짜를 옮긴다([Part 2 공통 규칙 M](part2-order-money.md#m-시간-흉내-내기)). "오늘 실행 대상"은 `setSubscriptionNextRunDate(subscriptionId, today)`, "다음 날 실행"은 `moveCyclesToPast(subscriptionId)` 뒤에 `setSubscriptionNextRunDate(subscriptionId, today)`. 사이의 하루는 건너뛰고 오늘 그대로 다시 돌리는 것이 "다음 날 실행"이다.
- `runDue()` 반환값의 정의가 달라지면 아래 반환값만 고친다.
- 빌링 결제 동작 바꾸기: 빌링 결제에만 동작을 건다(`pg.on("/v1/billing/bk_", ...)`). 거절 `pg.on("/v1/billing/bk_", Behavior.reject("REJECT_CARD_COMPANY"))`, 타임아웃 `Behavior.delay(1000)`(테스트 read-timeout 500ms보다 길다), 끊김 `Behavior.drop()`, 정상으로 되돌리기 `Behavior.approve()`.

#### 기존 2-2 체크아웃 테스트

리팩터링(`OrderType`, `Order.checkout` 인자, `OrderPlacer`, `StartPayment`·`PgOutcome`·`markApproved` 인자)만 하고 동작은 바꾸지 않았으므로 기존 테스트가 그대로 통과해야 한다.
- 동작: `./gradlew test --tests 'com.myroutine.order.*' --tests 'com.myroutine.payment.*'`.
- 확인: 실패 0건. 기존 테스트 코드의 수정은 호출 인자 추가뿐이다(`Order.checkout(..., OrderType.NORMAL, ...)`, `new StartPayment(..., null)`, `new PgOutcome(..., paymentKey)`, `markApproved(..., paymentKey)`).
- 추가 확인: 일반 체크아웃으로 만든 주문의 `orders.type` = `NORMAL`, 결제 승인 뒤 장바구니에서 주문한 수량이 빠지는 기존 단언(`PaymentConfirmTest`)이 그대로 통과한다.

#### `order/domain/OrderTest` (2-2에 추가)

공통 준비: `Order.checkout(memberId, OrderType.SUBSCRIPTION, 품목 1개(가게 1곳, 단가 1000, 수량 1), address, Instant.now(), Duration.ofMinutes(15))`.

| 케이스 | 준비 | 동작 | 확인할 것 |
|---|---|---|---|
| 체크아웃 유형 기록 | | 같은 인자로 `type`만 `NORMAL`로 바꿔 한 번 더 `checkout` | 두 주문의 `getType()`이 각각 `SUBSCRIPTION`, `NORMAL` |
| 결제 시작 | 방금 만든 주문(`PENDING_PAYMENT`) | `markPaymentInProgress()` | `getStatus()` = `PAYMENT_IN_PROGRESS` |
| 결제 시작 거부 | `@ParameterizedTest`: `ReflectionTestUtils`로 상태를 `PAYMENT_IN_PROGRESS` / `PAID` / `CANCELLED` / `PAYMENT_FAILED` / `EXPIRED`로 만든다 | `markPaymentInProgress()` | `BusinessException`, `errorCode` = `INVALID_STATE_TRANSITION` / 상태는 준비한 값 그대로 |

#### `order/domain/CycleStatusTest` (단위 테스트)

4개 상태의 모든 쌍 16개를 `@ParameterizedTest`로 돌린다(허용 쌍에서 나머지를 계산해 만든다).

| 케이스 | 확인할 것 |
|---|---|
| 허용된 전이 2쌍 | `ORDERED→PAID`, `ORDERED→FAILED`는 `transitTo`가 예외 없이 도착 상태를 돌려준다 |
| 허용되지 않은 전이 14쌍 | 나머지 14쌍(`ORDERED→ORDERED`, `ORDERED→SKIPPED`, `PAID`·`FAILED`·`SKIPPED`에서 나가는 전이 전부)은 `BusinessException`이고 `errorCode`가 `INVALID_STATE_TRANSITION`이다 |

#### `order/application/SubscriptionCycleServiceTest` (가짜 PG, `runDue()` 직접 호출)

공통 준비
- 판매자 `seller@test.com`, `shopId = openShop(sellerId, "1111111111")`, `productId = registerSubscribableProduct(sellerId, shopId, 10000, 10)`(상품명 `product`), 구매자 `buyer@test.com`(`buyerId`), `billingKeyId = registerBillingKey(buyerId)`.
- `subscriptionId = createSubscription(buyerId, productId, billingKeyId)`(수량 1, 매주 월요일) → `setSubscriptionNextRunDate(subscriptionId, today)`.
- 이때 구독은 `ACTIVE`, `unit_price` 10000, `consecutive_failures` 0, `pending_unit_price` null이고 주문·회차·결제는 0건, 재고는 `available` 10·`reserved` 0·`sold` 0이다.

**1. 정상 회차** — 동작: `runDue()` 한 번.
- 반환값 1.
- `orderRowsOf(buyerId)`가 1건, 그 주문 `o`: `type()` = `SUBSCRIPTION`, `status()` = `PAID`, `lineUnitPrice()` = 10000, `lineQuantity()` = 1.
- `cyclesOf(subscriptionId)`가 1건: `cycleDate()` = `today`, `status()` = `PAID`, `orderId()` = `o.id()`, `appliedUnitPrice()` = 10000, `failureReason()` = null.
- 구독 `s`: `nextRunDate()` = `nextMonday`.
- 결제 `p = o.payment()`: `amount()` = 10000, `billingKeyId()` = `billingKeyId`. `paymentKey()`는 `BILLING-` + `o.id()`가 아니고 `pay_`로 시작한다.
- 가짜 PG 빌링 요청 1건: `path()` = `/v1/billing/` + `billingKeyOf(billingKeyId).billingKey()`, 헤더 `idempotency-key` = `p.id()`, 본문 `customerKey` = `billingKeyOf(billingKeyId).customerKey()`, `amount` = 10000, `orderId` = `p.pgOrderId()`, `orderName` = `product 정기구독`.

**2. 같은 날 `runDue()`를 다시 돌려도 결제는 한 번** (INV-08) — 동작을 순서대로.
1. `runDue()` → 반환 1.
2. 바로 `runDue()` → 반환 0.
3. `setSubscriptionNextRunDate(subscriptionId, today)` → `runDue()` → 반환 0.
- 세 번 뒤 확인: `orderRowsOf(buyerId)` 1건(결제 있음), `cyclesOf` 1건(`PAID`), 가짜 PG 빌링 요청 1건, 재고 `available` 9·`sold` 1.
- 구독: `nextRunDate()` = `today`.

**3. 두 스레드가 동시에 `runDue()`** — 동작: `ConcurrencyRunner.run(2, () -> subscriptionCycleService.runDue())`.
- 두 결과 모두 `ok()`, `value()`의 합이 1.
- `orderRowsOf(buyerId)` 1건(결제 있음), `cyclesOf` 1건, 가짜 PG 빌링 요청 1건, 재고 `available` 9·`reserved` 0·`sold` 1.

**4. 결제 거절 한 번** — 준비: 공통 준비 + 빌링 결제를 `Behavior.reject("REJECT_CARD_COMPANY")`로. 동작: `runDue()`.
- 반환값 1.
- `orderRowsOf(buyerId)` 1건 `o`: `type()` = `SUBSCRIPTION`.
- `cyclesOf(subscriptionId)` 1건: `cycleDate()` = `today`, `status()` = `FAILED`, `orderId()` = `o.id()`, `failureReason()` = `PG 거절: REJECT_CARD_COMPANY`.
- 구독: `status()` = `ACTIVE`, `consecutiveFailures()` = 1, `nextRunDate()` = `today.plusDays(1)`.
- 가짜 PG 빌링 요청 1건.
- `order.subscription-cycle-failed.v1` 1건: `subscriptionId`, `cycleId` = 위 회차의 `id`, `memberId` = `buyerId`, `consecutiveFailures` = 1, `reason` = `PG 거절: REJECT_CARD_COMPANY`. `order.subscription-status-changed.v1`은 0건.

**5. 3회 연속 거절이면 정지** — 준비: 공통 준비 + 빌링 결제를 `Behavior.reject("REJECT_CARD_COMPANY")`로(끝까지 유지). 동작: `runDue()`를 세 번, 두 번째부터는 실행 전에 `moveCyclesToPast(subscriptionId)` → `setSubscriptionNextRunDate(subscriptionId, today)`.

| 실행 | `runDue()` 반환 | 구독 `consecutiveFailures()` / `status()` / `nextRunDate()` | `cyclesOf` 크기(모두 `FAILED`) | `cycle-failed` 이벤트 누계 | `status-changed` 이벤트 누계 |
|---|---|---|---|---|---|
| 1 | 1 | 1 / `ACTIVE` / `today + 1` | 1 | 1 | 0 |
| 2 | 1 | 2 / `ACTIVE` / `today + 1` | 2 | 2 | 0 |
| 3 | 1 | 3 / `SUSPENDED` / `today` | 3 | 2 | 1 |

- 3번째에 나간 `status-changed` 1건: `subscriptionId`, `memberId` = `buyerId`, `from` = `ACTIVE`, `to` = `SUSPENDED`.

**6. 거절 두 번 뒤 성공하면 실패 수가 0으로** — 준비: 공통 준비 + 빌링 결제 거절 상태에서 `runDue()`를 두 번 실행해(두 번째 실행 전에 `moveCyclesToPast(subscriptionId)` → `setSubscriptionNextRunDate(subscriptionId, today)`) `consecutive_failures`를 2로 만든다. 동작: 빌링 결제를 `Behavior.approve()`로 되돌린다 → `moveCyclesToPast(subscriptionId)` → `setSubscriptionNextRunDate(subscriptionId, today)` → `runDue()`.
- 반환값 1.
- `cyclesOf` 3건, 만든 순서대로 `status()`가 `FAILED`, `FAILED`, `PAID`.
- 구독: `consecutiveFailures()` = 0, `status()` = `ACTIVE`, `nextRunDate()` = `nextMonday`.
- 재고: `available` 9, `reserved` 0, `sold` 1.

**7. 재고 부족·판매 중지·가게 운영 중지는 건너뛴다** — `@ParameterizedTest`. 준비: 공통 준비 + JDBC `UPDATE orders.subscription SET consecutive_failures = 2 WHERE id = ?` + 행마다 다음을 만든다. 동작: `runDue()`.

| 행 | 추가 준비 | 재고 확인 |
|---|---|---|
| 재고 부족 | 공통 준비의 상품 대신 `registerSubscribableProduct(sellerId, shopId, 10000, 0)`으로 상품을 만들고 구독도 그 상품으로 만든다 | `available` 0, `reserved` 0, `sold` 0 |
| 판매 중지 | `hideProduct(productId)` | `available` 10, `reserved` 0, `sold` 0 |
| 가게 운영 중지 | `closeShop(shopId)` | `available` 10, `reserved` 0, `sold` 0 |

- 반환값 1.
- `cyclesOf` 1건 `c`: `cycleDate()` = `today`, `status()` = `SKIPPED`, `orderId()` = null, `appliedUnitPrice()` = null, `failureReason()`은 구현이 정한 사유 문자열이다(정확한 문자열은 문서에 없다, 확인 필요. 이벤트 `reason` 예시 `SKIPPED_OUT_OF_STOCK`와 같은 값으로 두고, 정해진 문자열을 `isEqualTo`로 비교한다).
- `orderRowsOf(buyerId)`가 비어 있다. 예약 0건, 가짜 PG 빌링 요청 0건.
- 구독: `consecutiveFailures()` = 2, `nextRunDate()` = `nextMonday`.
- `cycle-failed` 이벤트 1건: `subscriptionId`, `cycleId` = 위 회차의 `id`, `memberId` = `buyerId`, `reason` = `c.failureReason()`과 같은 값. `status-changed`는 0건.

**8. 건너뛴 회차는 가격 승격을 취소한다** — 준비: 재고 0 상품(`registerSubscribableProduct(sellerId, shopId, 10000, 0)`)으로 만든 구독 + JDBC `UPDATE orders.subscription SET pending_unit_price = 12000, pending_effective_date = ? WHERE id = ?`(`today`). 동작: `runDue()`.
- 구독: `unitPrice()` = 10000, `pendingUnitPrice()` = 12000, `pendingEffectiveDate()` = `today`.

**9. 빌링 타임아웃은 대사가 닫는다** — 준비: 공통 준비 + 빌링 결제를 `Behavior.delay(1000)`로. 
- 동작 1: `runDue()`.
- 확인 1: 반환값 1 / `orderRowsOf(buyerId)` 1건 `o`, `status()` = `PAYMENT_IN_PROGRESS`, `payment().status()` = `UNKNOWN` / `cyclesOf` 1건 `ORDERED`(`orderId()` = `o.id()`, `appliedUnitPrice()` = 10000) / 구독 `consecutiveFailures()` 0, `nextRunDate()` = `today` / 가짜 PG 빌링 요청 1건 / `cycle-failed`·`status-changed` 이벤트 0건.
- 동작 3: `setOrderUpdatedAt(orderId, Instant.now().minusSeconds(60))` → `orderReconcileService.reconcileBatch()`(2-6).
- 확인 3: `orderRowsOf` 첫 주문 `payment().paymentKey()`가 `BILLING-` + 주문 `id()`가 아니고 `pay_`로 시작 / `cyclesOf` 첫 회차 `PAID` / 구독 `nextRunDate()` = `nextMonday` / 가짜 PG 빌링 요청은 그대로 1건 / `pg.requests("/v1/payments/orders/")` 1건이고 `path()` = `/v1/payments/orders/` + `payment().pgOrderId()`.

**10. 빌링 끊김은 대사가 실패로 닫는다** — 준비: 공통 준비 + 빌링 결제를 `Behavior.drop()`으로. 동작: `runDue()` → `setOrderUpdatedAt(orderId, Instant.now().minusSeconds(60))` → `reconcileBatch()`.
- 주문 `o`의 `status()` = `PAYMENT_FAILED`. 회차 `c`의 `status()` = `FAILED`이고 `c.failureReason()`이 null이 아니고 `o.failureReason()`과 같은 문자열.
- 구독: `consecutiveFailures()` = 1, `nextRunDate()` = `today.plusDays(1)`.
- `cycle-failed` 이벤트 1건: `cycleId` = `c.id()`, `consecutiveFailures` = 1, `reason` = `c.failureReason()`.

**11. 인상 예정가의 적용일에 따라 가격이 달라진다** — `@ParameterizedTest`. 준비: 공통 준비 + JDBC `UPDATE orders.subscription SET pending_unit_price = 12000, pending_effective_date = ? WHERE id = ?`. 동작: `runDue()`.

| 행 | `pending_effective_date` | 주문·결제·회차 가격 | 구독 `unitPrice()` / `pendingUnitPrice()` / `pendingEffectiveDate()` |
|---|---|---|---|
| 적용일이 오늘 | `today` | 12000 | 12000 / null / null |
| 적용일이 내일 | `today.plusDays(1)` | 10000 | 10000 / 12000 / `today.plusDays(1)` |

- "주문 가격"은 `orderRowsOf(buyerId)` 첫 주문의 `lineUnitPrice()`·`payment().amount()`, `cyclesOf` 첫 회차의 `appliedUnitPrice()`, 가짜 PG 빌링 요청 본문 `amount`가 모두 같은 값이라는 뜻이다.

**12. 정지된 구독은 실행하지 않는다** — 준비: 5번과 같은 준비로 `runDue()`를 세 번 실행해 구독을 `SUSPENDED`로 만든다. 동작: `moveCyclesToPast(subscriptionId)` → `setSubscriptionNextRunDate(subscriptionId, today)` → `runDue()`.
- 반환값 0.
- 구독: `consecutiveFailures()` = 3, `status()` = `SUSPENDED`, `nextRunDate()` = `today`. `cyclesOf` 3건(모두 `FAILED`), `cycle-failed` 이벤트 누계 2, `status-changed` 이벤트 누계 1.

#### `order/web/SubscriptionCycleControllerTest` (`@AutoConfigureMockMvc`, 회차 이력 API)

공통 준비: 판매자 `seller@test.com`, `shopId = openShop(sellerId, "1111111111")`, `productId = registerSubscribableProduct(sellerId, shopId, 10000, 10)`, 구매자 `buyer@test.com`(`buyerId`), `billingKeyId = registerBillingKey(buyerId)`, `subscriptionId = createSubscription(buyerId, productId, billingKeyId)` → `setSubscriptionNextRunDate(subscriptionId, today)`. 회차 두 개를 만든다: 빌링 결제 거절 → `runDue()` → 빌링 결제를 `Behavior.approve()`로 → `moveCyclesToPast(subscriptionId)` → `setSubscriptionNextRunDate(subscriptionId, today)` → `runDue()`. 결과: 회차 2건(과거 `FAILED`, 오늘 `PAID`). 구매자 토큰은 `login("buyer@test.com")`.

**1. 내 회차 이력** — 동작: 구매자 토큰으로 `GET /api/subscriptions/{subscriptionId}/cycles?size=10`.
- 응답: 200, `$.items.length()` = 2, `$.nextCursor` = null.
- `$.items[0]`: `cycleDate` = `today.toString()`, `status` = `PAID`, `orderId` = `orderRowsOf(buyerId)` 중 `status()` = `PAID`인 주문의 `id()`, `appliedUnitPrice` = 10000, `failureReason` = null.
- `$.items[1]`: `status` = `FAILED`, `failureReason` = `PG 거절: REJECT_CARD_COMPANY`.

**2. 남의 구독 회차 이력 거부** — 준비: 다른 회원 `other@test.com` 가입·로그인. 동작: 그 토큰으로 `GET /api/subscriptions/{subscriptionId}/cycles`.
- 응답: 404, `$.code` = `SUBSCRIPTION_NOT_FOUND`.

**3. 회차 이력은 커서로 나눠 읽는다** — 동작: 1번과 같은 준비에서 구매자 토큰으로 `GET /api/subscriptions/{subscriptionId}/cycles?size=1` → `?size=1&cursor={nextCursor}`.
- 첫 요청: `$.items.length()` = 1, `$.items[0].status` = `PAID`, `$.nextCursor`는 null이 아니다.
- 둘째 요청: `$.items.length()` = 1, `$.items[0].status` = `FAILED`, `$.nextCursor` = null.

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

**구현 순서**: 1) order.domain(`Subscription.applyPriceChange`, 판매자 목록 쿼리) → 2) order.api.event → 3) order.infrastructure(토픽 설정) → 4) order.application(`SubscriptionEventService`, 판매자 구독 현황 서비스) → 5) consumer(`ProductEventConsumer`, `ShopEventConsumer`, 기존 `MemberEventConsumer` 수정) → 6) 판매자 구독 현황 컨트롤러 → 7) 알림 매핑 → 테스트.

**1) order.domain**

| 클래스 | 규격 |
|---|---|
| `Subscription.applyPriceChange` | `boolean applyPriceChange(Money newPrice, Instant changedAt, LocalDate today)` — **상품 가격 변경 이벤트 하나를 이 구독에 반영한다.** 부르는 쪽은 아래 `SubscriptionEventService`의 가격 변경 처리이고(`today`는 `LocalDate.now(clock.withZone(businessZone))`, `changedAt`은 이벤트의 `changedAt`), 반환 `boolean`은 "반영했는가"(true면 호출자가 `SUBSCRIPTION_PRICE_CHANGED`를 낸다). 순서는 아래 |
| `SubscriptionRepository` 판매자 목록 쿼리 (5-2 인터페이스에 추가) | `shop_id = :shopId`(와 `status`가 있을 때 `status = :status`)로 거른 keyset 조회. `status`가 있는 경우와 없는 경우 × 첫 페이지·다음 페이지가 필요하다(메서드 이름은 문서에 없다. 인자는 5-2 `findByMemberFirstPage(UUID memberId, Limit limit)`·`findByMemberNextPage(UUID memberId, Instant cursorCreatedAt, UUID cursorId, Limit limit)`의 `memberId` 자리에 `shopId`를 두고, `status`가 있는 쪽은 그 바로 뒤에 `SubscriptionStatus status`를 더한 모양). `Limit.of(size + 1)`로 읽는다. 인덱스 `idx_subscription_shop_id_status`는 가게·상태 필터를 받친다. `Cursor(createdAt, id)` 기준 정렬을 쓸지는 정해져 있지 않다(확인 필요). 판매자 구독 현황 서비스 메서드가 쓴다 |

`applyPriceChange` 순서
1. **오래된 이벤트 무시**: `priceChangedAt != null && !changedAt.isAfter(priceChangedAt)`면 아무것도 바꾸지 않고 false
2. `priceChangedAt = changedAt`
3. **인상**(`newPrice.isGreaterThan(unitPrice)`): `pendingUnitPrice = newPrice`, `pendingEffectiveDate`는 `ChronoUnit.DAYS.between(today, nextRunDate) >= 3`이면 `nextRunDate`, 아니면 `schedule.nextAfter(nextRunDate)`(다음 회차로 미룬다. POL-11: 다음 회차 3일 전까지 알림을 보내야 해서, 3일이 안 남았으면 이번 회차는 기존가로 결제한다). `unitPrice`는 그대로다. 실제 승격은 5-3 회차 실행 때 한다
4. **인하·동일**: `unitPrice = newPrice`, `pendingUnitPrice`·`pendingEffectiveDate`를 null로(예정 인상 취소)
5. true

**2) order.api.event**

| 클래스 | 규격 |
|---|---|
| `OrderTopics.SUBSCRIPTION_PRICE_CHANGED` | `"order.subscription-price-changed.v1"` |
| `SubscriptionPriceChangedEvent` | `record (UUID subscriptionId, UUID memberId, long oldPrice, long newPrice, LocalDate effectiveDate)`. 발행은 `outboxPublisher.publish(topic, "SUBSCRIPTION", subscriptionId, event)`다. `oldPrice`·`effectiveDate`를 무엇으로 채우는지는 문서에 없다(확인 필요: 구독의 변경 전 `unitPrice`와, 인상이면 `pendingEffectiveDate`가 자연스럽지만 인하의 `effectiveDate`는 정해져 있지 않다). `onPriceChanged`가 발행한다 |

**3) order.infrastructure**

| 클래스 | 규격 |
|---|---|
| `OrderTopicConfig` (3-3, 기존 클래스에 추가) | `SUBSCRIPTION_PRICE_CHANGED`의 `NewTopic`과 `.dlt`를 더한다 |

**4) order.application**

| 클래스 | 메서드 |
|---|---|
| `SubscriptionEventService` (`@Service`) | consumer가 부르는 서비스. 메서드마다 `@Transactional`, 이름은 정해져 있지 않다(예: `onPriceChanged`, `onProductDiscontinued`, `onShopClosed`, `onMemberWithdrawn`). 의존성: `SubscriptionRepository`, `OutboxPublisher`, `Clock`, `BusinessProperties`. 각 처리는 아래 표. 아래 consumer(`order.infrastructure`)가 부른다 |
| 판매자 구독 현황 서비스 메서드 | 클래스 이름은 문서에 없다(확인 필요). ① `shopApi.verifyOwner(shopId, memberId)` — 가게가 없으면 404, 남의 가게면 403(`CLOSED`여도 통과, 이미 있는 Part 1 메서드) → ② `status`가 있으면 그 상태만, 없으면 전체를 위 판매자 목록 쿼리로 읽는다 → ③ `SellerSubscriptionResponse`로 옮긴다(엔티티의 `shippingAddress`·`memberId`는 옮기지 않는다 — 구매자 개인정보를 내려주지 않는다). 컨트롤러 메서드가 부른다 |

| 이벤트 | consumer 이름 | 처리 |
|---|---|---|
| `product-price-changed` | `order.product-price-changed` | `findAllByProductIdAndStatusIn(productId, ACTIVE_LIKE)` 각각 `subscription.applyPriceChange(newPrice, changedAt, today)` → 바뀐 구독마다 `SUBSCRIPTION_PRICE_CHANGED` 이벤트 |
| `product-discontinued` | `order.product-discontinued` | 상품의 활성 구독 `terminate("PRODUCT_DISCONTINUED")` + 상태 변경 이벤트 |
| `shop-closed` | `order.shop-closed` | 가게의 활성 구독 `terminate("SHOP_CLOSED")` (폐업 조건상 보통 0건, 방어용) |
| `member-withdrawn` | `order.member-withdrawn` (3-6) | 회원의 활성 구독 `terminate("MEMBER_WITHDRAWN")` (탈퇴 조건상 보통 0건, 방어용) |

종료 처리 세 가지(단종·폐업·탈퇴)의 공통 규칙
- 구독마다 `String from = subscription.getStatus().name()`을 먼저 담아 두고 `subscription.terminate(reason)` → `SUBSCRIPTION_STATUS_CHANGED`(`from`, `to = "TERMINATED"`)를 같은 트랜잭션에서 outbox로 낸다. 이벤트의 `reason`에 무엇을 넣는지는 문서에 없다(확인 필요 — `endReason`과 같은 문자열이 자연스럽다).
- 조회가 활성 구독만 가져오므로 같은 이벤트가 다시 와도 두 번째는 0건이라 상태 변경 이벤트도 나가지 않는다("단종 이벤트 두 번 → 종료 1번").

**5) order.infrastructure** — consumer

**왜 이벤트로 받나**: 상품·가게·회원은 다른 모듈이라 order가 그 변경을 직접 알 수 없다. 그 모듈이 발행한 이벤트(3-3)를 order가 구독해 구독(subscription) 쪽을 맞춘다(product가 order에 의존하면 순환이라 반대 방향은 불가, 이벤트 카탈로그 참조).

**모양**: 토픽마다 `@KafkaListener(topics = ProductTopics.PRODUCT_PRICE_CHANGED, groupId = "order") void onXxx(String message)` → `TypedEnvelope<ProductPriceChangedEvent> typed = eventEnvelopes.parse(message, ProductPriceChangedEvent.class)` → `inboxGuard.runOnce("order.product-price-changed", typed.envelope(), () -> subscriptionEventService.onPriceChanged(typed.payload()))`. `InboxGuard`가 같은 이벤트를 두 번 받아도 한 번만 처리하게 한다. 이벤트 record·토픽 상수는 발행 모듈의 `api.event`에서 import하므로 `order/package-info.java`의 `allowedDependencies`에 `product::api`·`shop::api`·`member::api`가 필요하다(이미 있다). 대상이 없으면(이미 종료된 구독 등) 경고 로그 후 정상 종료한다(Part 3 공통 규칙 Q).

| 클래스 | 규격 |
|---|---|
| `ProductEventConsumer` (신규) | `onProductPriceChanged` · `ProductTopics.PRODUCT_PRICE_CHANGED` · `ProductPriceChangedEvent(productId, oldPrice, newPrice, changedAt)` / `onProductDiscontinued` · `ProductTopics.PRODUCT_DISCONTINUED` · `ProductDiscontinuedEvent(productId, shopId, reason)` |
| `ShopEventConsumer` (신규) | `onShopClosed` · `ShopTopics.SHOP_CLOSED` · `ShopClosedEvent(shopId, memberId)` |
| `MemberEventConsumer` (3-6, 기존 수정) | `onMemberWithdrawn` · `MemberTopics.MEMBER_WITHDRAWN`. 기존 장바구니 삭제에 구독 종료 호출을 더한다. 같은 `inboxGuard.runOnce("order.member-withdrawn", ...)` 안에서 한다 |

**6) order.web** — 판매자 구독 현황: `GET /api/shops/{shopId}/subscriptions?status=&cursor=&size=` → `SellerSubscriptionResponse(id, productId, quantity, cycleType, cycleValue, status, nextRunDate, unitPrice, createdAt)`

| 클래스 | 규격 |
|---|---|
| 컨트롤러 메서드 | `GET /api/shops/{shopId}/subscriptions`. 클래스는 문서에 없다(확인 필요) |

**7) notification.domain · notification.application · notification.infrastructure** — 3-5 알림 매핑

`SubscriptionPriceChangedEvent`를 구독자(`memberId`)에게 "다음 회차부터 가격이 바뀝니다"로 알린다. 세 곳에 더한다.

| 클래스 | 규격 |
|---|---|
| `NotificationType` (notification.domain) | 값을 더한다 |
| `NotificationComposer` (notification.application) | `compose` 오버로드를 더한다 |
| `NotificationEventConsumer` (notification.infrastructure) | 토픽 메서드를 더한다: `@KafkaListener` → `envelopes.parse` → `inboxGuard.runOnce` → `composer.compose` → `notificationService.enqueue` |

`NotificationType` 값 이름·제목·본문은 문서에 없다(확인 필요).

### 완료 확인

**테스트 준비**

이 단계에서 추가하는 헬퍼는 없다.

이미 있는 헬퍼: 구독은 5-2의 `registerSubscribableProduct`·`createSubscription`·`subscriptionSnapshotOf`로 만들고 읽으며, 다음 실행일은 5-3의 `setSubscriptionNextRunDate`로 옮긴다. 가격 인상·단종은 판매자 상품 API를 MockMvc로 부르고(`PATCH /api/shops/{shopId}/products/{productId}`의 `price`, `PATCH .../status`의 `DISCONTINUED`).

헬퍼를 쓰는 방법은 다음과 같다.
- 응답: `jsonPath`. 에러 코드는 `$.code`.
- 구독: 5-2의 `s = testFixtures.subscriptionSnapshotOf(subscriptionId)`. 이 단계에서 읽는 값은 `s.status()`, `s.endReason()`, `s.unitPrice()`, `s.pendingUnitPrice()`, `s.pendingEffectiveDate()`, `s.priceChangedAt()`, `s.nextRunDate()`.
- 이벤트: `OutboxAssert.rows("order.subscription-price-changed.v1")` 등(3-1). 각 행은 `OutboxSnapshot`이고, `payload()`(`JsonNode`)의 필드로 비교한다(UUID는 문자열, 금액은 `asLong()`, 날짜는 `LocalDate.toString()`과 비교).
- 알림: `notificationRowsOf(memberId)`(3-5가 `TestFixtures`에 추가한 헬퍼. 만든 순서). 한 건은 `NotificationRow`로 `type()`, `recipient()`, `status()`. 이 단계는 `type()`만 읽는다.
- 비동기 반영: `outboxRelay.relayOnce()` 뒤 `Eventually.await(Duration.ofSeconds(5), () -> { 단언 })` 안에서 확인한다.
- 오늘 날짜: 테스트 클래스에 `Clock`, `BusinessProperties`를 주입하고 `LocalDate today = LocalDate.now(clock.withZone(businessProperties.businessZone()))`.

#### `order/domain/SubscriptionPriceTest` (단위 테스트)

공통 준비: `Subscription.subscribe(memberId, shopId, productId, 1, new SubscriptionSchedule(WEEKLY, 1), address, billingKeyId, Money.of(10000), LocalDate.of(2026, 10, 6))` → `ReflectionTestUtils.setField(subscription, "nextRunDate", LocalDate.of(2026, 10, 12))`(월요일). `changedAt = Instant.parse("2026-10-08T00:00:00Z")`. 2026-10-12·10-19는 월요일이다.

| 케이스 | 준비 | 동작 | 확인할 것 |
|---|---|---|---|
| 다음 회차 2일 전 인상 | | `applyPriceChange(Money.of(12000), changedAt, LocalDate.of(2026, 10, 10))` | 반환 `true` / `pendingUnitPrice` = `Money.of(12000)` / `pendingEffectiveDate` = 2026-10-19 / `unitPrice` = `Money.of(10000)` / `priceChangedAt` = `changedAt` |
| 5일 전 인상 | | `applyPriceChange(Money.of(12000), changedAt, LocalDate.of(2026, 10, 7))` | `pendingEffectiveDate` = 2026-10-12 |
| 정확히 3일 전 인상 | | `applyPriceChange(Money.of(12000), changedAt, LocalDate.of(2026, 10, 9))` | `pendingEffectiveDate` = 2026-10-12 |
| 인하·동일가는 즉시 | 먼저 `pendingUnitPrice` = `Money.of(12000)`, `pendingEffectiveDate` = 2026-10-12를 `ReflectionTestUtils`로 넣는다. `@ParameterizedTest`: 새 가격 8000 / 10000 | `applyPriceChange(Money.of(새 가격), changedAt, LocalDate.of(2026, 10, 7))` | 반환 `true` / `unitPrice` = 새 가격 / `pendingUnitPrice` = null, `pendingEffectiveDate` = null |
| 오래된 이벤트 무시 | 먼저 `applyPriceChange(Money.of(8000), changedAt, LocalDate.of(2026, 10, 7))`가 `true` | `@ParameterizedTest`: 두 번째 이벤트의 시각 `changedAt.minusSeconds(1)` / `changedAt`으로 `applyPriceChange(Money.of(15000), 그 시각, LocalDate.of(2026, 10, 7))` | 반환 `false` / `unitPrice` = `Money.of(8000)` / `pendingUnitPrice` = null / `priceChangedAt` = `changedAt` 그대로 |

#### `order/application/SubscriptionEventTest` (`@AutoConfigureMockMvc`, 가짜 PG 불필요)

공통 준비: 판매자 `seller@test.com`(`sellerId`, 토큰 `sellerToken = login`), `shopId = openShop(sellerId, "1111111111")`, `productId = registerSubscribableProduct(sellerId, shopId, 10000, 10)`, 구매자 `buyer@test.com`(`buyerId`), `billingKeyId = registerBillingKey(buyerId)`, `subscriptionId = createSubscription(buyerId, productId, billingKeyId)`, `setSubscriptionNextRunDate(subscriptionId, today.plusDays(5))`. 이때 구독은 `ACTIVE`, `unit_price` 10000, `pending_unit_price` null, `end_reason` null이다.

**1. 상품 가격 인상이 구독에 반영된다** — 동작: `sellerToken`으로 `PATCH /api/shops/{shopId}/products/{productId}`, 본문 `{"price":12000}` → 200 → `outboxRelay.relayOnce()`.
- 확인(`Eventually`): 구독 `pendingUnitPrice()` = 12000, `priceChangedAt()`은 null이 아니다.
- `order.subscription-price-changed.v1` 정확히 1건: `subscriptionId`, `memberId` = `buyerId`, `oldPrice` = 10000, `newPrice` = 12000, `effectiveDate` = `today.plusDays(5)`.

**2. 단종 이벤트를 두 번 받아도 종료는 한 번** — 동작 1: `sellerToken`으로 `PATCH /api/shops/{shopId}/products/{productId}/status`, 본문 `{"status":"DISCONTINUED"}` → 200 → `outboxRelay.relayOnce()`.
- 확인 1(`Eventually`): 구독 `status()` = `TERMINATED`, `endReason()` = `PRODUCT_DISCONTINUED`.
- `order.subscription-status-changed.v1` 1건: `subscriptionId`, `memberId` = `buyerId`, `from` = `ACTIVE`, `to` = `TERMINATED`, `reason` = `PRODUCT_DISCONTINUED`.
- 동작 2: `subscriptionEventService.onProductDiscontinued(new ProductDiscontinuedEvent(productId, shopId, "DISCONTINUED"))`를 직접 호출한다.
- 확인 2: 예외 없이 끝난다 / 구독 `status()` = `TERMINATED`, `endReason()` = `PRODUCT_DISCONTINUED` 그대로 / `order.subscription-status-changed.v1`이 여전히 1건.

**3. 탈퇴·폐업 이벤트는 해당 구독만 종료한다** — `@ParameterizedTest`. 준비: 공통 준비 + 구매자 `buyer2@test.com`의 구독 `subscription2Id`(같은 상품, `registerBillingKey(buyer2Id)`, `createSubscription`).

| 행 | 동작 | `subscriptionId` | `subscription2Id` |
|---|---|---|---|
| 탈퇴 | `subscriptionEventService.onMemberWithdrawn(new MemberWithdrawnEvent(buyerId))` | `status()` = `TERMINATED`, `endReason()` = `MEMBER_WITHDRAWN` | `ACTIVE`, `endReason()` = null |
| 폐업 | `subscriptionEventService.onShopClosed(new ShopClosedEvent(shopId, sellerId))` | `status()` = `TERMINATED`, `endReason()` = `SHOP_CLOSED` | `TERMINATED`, `endReason()` = `SHOP_CLOSED` |

- 종료된 구독마다 `order.subscription-status-changed.v1`이 1건씩이고 payload는 `from` = `ACTIVE`, `to` = `TERMINATED`, `subscriptionId`, `memberId`가 그 구독의 값이다. 탈퇴 행은 이벤트 총 1건, 폐업 행은 총 2건이다.

**4. 판매자 구독 현황** — 준비: 공통 준비. 동작: `GET /api/shops/{shopId}/subscriptions`(요청은 5~8번도 같다).
- 내 가게: `sellerToken`으로 `shopId`를 부르면 200, `$.items.length()` = 1, `$.nextCursor` = null. `$.items[0]`: `id` = `subscriptionId`, `productId` = `productId`, `quantity` = 1, `cycleType` = `WEEKLY`, `cycleValue` = 1, `status` = `ACTIVE`, `nextRunDate` = `subscriptionSnapshotOf(subscriptionId).nextRunDate().toString()`, `unitPrice` = 10000, `createdAt` 존재. `$.items[0].memberId`, `$.items[0].shippingAddress`는 없다(`doesNotExist()`).

**5. 가격 인상 알림이 나간다** — 동작: 1번과 같은 준비와 가격 인상 요청 → `outboxRelay.relayOnce()`.
- `Eventually` 안에서 `outboxRelay.relayOnce()`를 부르며 확인: `notificationRowsOf(buyerId)`가 정확히 1건이고 `type()` = 가격 변경 알림 type.

**6. 남의 가게의 구독 현황 거부** — 준비: 공통 준비 + 다른 판매자 `seller2@test.com`(`seller2Id`, 토큰 `seller2Token`)과 `shop2Id = openShop(seller2Id, "2222222222")`. 동작: `seller2Token`으로 `shopId`의 구독 현황을 부른다.
- 응답: 403, `$.code` = `FORBIDDEN`.

**7. 구독이 없는 내 가게는 빈 목록** — 준비: 6번과 같은 준비. 동작: `seller2Token`으로 `shop2Id`의 구독 현황을 부른다.
- 응답: 200, `$.items.length()` = 0.

**8. 상태 필터** — 준비: 공통 준비. 동작: 구매자 토큰으로 `POST /api/subscriptions/{subscriptionId}/pause` → 200 → `sellerToken`으로 `shopId`의 구독 현황에 `?status=PAUSED` / `?status=ACTIVE` / 필터 없이.
- `?status=PAUSED` → `$.items.length()` = 1, `?status=ACTIVE` → `$.items.length()` = 0, 필터 없이 → `$.items.length()` = 1.

**리뷰 때 물어볼 것**
- 이벤트 순서가 바뀌면 어떻게 되나? 버전 대신 `changedAt`을 쓴 것의 한계는?
- 가격 인상을 바로 적용하지 않는 이유는? (POL-11)

---

## Part 5 완료
- [ ] `v0.5.0` 릴리스, Part 6 문서 다듬기
