# Part 4. 인증 완성

> 버전 0.42 · 2026-10-30 · 4-3 제공자 로그인 화면 주소를 프론트가 직접 만든다. `GET /api/auth/oauth/{provider}/authorize-url`·`OAuthLoginService.authorizeUrl`·`OAuthProviderClient.authorizeUrl`·`AuthorizeUrl`·`Provider.authorizeUri`/`scope`와 완료 확인 11번(authorize-url)을 지웠고, `state`는 프론트가 만든다. 서버 설정의 `redirect-uri`는 토큰 교환에 남아 프론트 값과 같아야 한다.
> 0.41 · 2026-10-29 · 4-3 탈퇴가 그 회원의 소셜 계정 연결 행을 지운다(`SocialAccountRepository.deleteAllByMemberId`, `WithdrawService.withdraw`). 지우지 않으면 탈퇴한 회원의 소셜 계정으로 로그인은 401 `LOGIN_FAILED`, 가입은 unique 제약으로 영영 막힌다. 탈퇴 후 재가입 완료 확인을 더했다.
> 0.40 · 2026-10-29 · 4-2 `EmailCodeStore.verify`를 `verify.lua` 하나로 실행한다. 읽기·비교·`HINCRBY`를 따로 하면 동시에 보낸 틀린 코드가 모두 비교를 받아 5번 제한을 넘기 때문이다(동시 50개에 50번 비교). 동시성 케이스 `EmailCodeConcurrencyTest`를 더했다.
> 0.39 · 2026-10-29 · 4-2 인증 토큰과 4-3 가입 토큰도 원문 대신 SHA-256 해시로 저장한다. 토큰이 키 이름에 들어가므로 키는 `auth:signup-verified:{tokenHash}`·`auth:oauth-signup:{tokenHash}`다.
> 0.38 · 2026-10-29 · 4-2부터 `prod`는 Gmail SMTP(`spring-boot-starter-mail`, `SmtpMailSender`)로 메일을 실제로 보내고 `LogMailSender`는 `prod` 밖에서만 쓴다. 재설정 요청은 발송 실패를 잡아 202를 돌려준다(가입 여부가 드러나지 않게).
> 0.37 · 2026-10-29 · 4-1 `RefreshToken.toString()`이 secret을 가린다(record 기본 `toString()`이 로그·예외 메시지에 refresh를 그대로 남기지 않게).
> 0.36 · 2026-10-28 · 4-1 `SessionStore.deleteAllOf`는 집합 키를 통째로 지우지 않고 읽은 sessionId만 집합에서 뺀다(그 사이 생긴 세션이 색인에서 빠지지 않게).
> 0.35 · 2026-10-28 · 프론트 빈칸 반영: 4-2 발송 한도 429에 `details.retryAfterSeconds`(G10), 4-3 내 정보에 `hasPassword`·`socialAccounts`(G11), 4-4 관리자 회원 목록에 `banReason`과 `MEMBER_BANNED`의 `details.reason`(G13). 4-2 재설정의 `newPassword`에 가입과 같은 검증 규칙을 붙였다.
> 0.34 · 2026-10-28 · 이메일 가입은 가입 전에 인증한다: 발송 → 확인(인증 토큰 `auth:signup-verified`, 30분) → 가입(`verificationToken`). 이미 가입된 이메일의 발송은 409. `ev` 클레임·`@RequireVerifiedEmail`·`email_verified_at`·로그인 회원용 인증 API·`EMAIL_NOT_VERIFIED`·`EMAIL_ALREADY_VERIFIED`를 지우고 `VERIFICATION_TOKEN_INVALID`를 더했다. 4-3 소셜 가입은 4-2의 발송·확인을 쓰고 `verificationToken`을 받으며 전용 발송 API를 지웠다. 4-4 관리자 회원 목록에서 `emailVerified`를 뺐다.
> 0.33 · 2026-10-28 · 4-3 OAuth `state`를 서버에 저장하지 않는다. 서버 저장은 브라우저와 묶이지 않아 로그인 CSRF를 막지 못하므로 프론트가 `sessionStorage`에 둔 값과 비교한다. `auth:oauth-state` 키·`OAuthStateStore`·`consume.lua`·`OAUTH_STATE_INVALID`를 지웠다.
> 0.32 · 2026-10-28 · Part 4 앞에 4-0(정산 조회 보완)을 더했다: 구매확정 이벤트와 정산 품목에 가게주문 ID·상품 이름·수량(`NOT NULL`, 배포 전 운영 DB 초기화), 정산 응답에 수수료율, 정산 예정 조회 API `GET /api/shops/{shopId}/settlements/upcoming`.
> 0.31 · 2026-10-28 · 세션의 `device` 필드와 `User-Agent` 전달을 지웠다(4-1 `SessionStore.create`·`TokenIssuer.issueNewSession`·`signUp`/`login`, 4-3 `login`/`signup`). 세션은 로그인마다 따로 만들어지므로 FR-MEM-03의 "기기(세션)별 관리"는 그대로고, 기기 목록은 4-1 추가 항목으로 옮겼다. 4-1: `rotate`가 `auth:member-sessions` 집합의 만료도 연장한다(`rotate(sessionId, memberId, …)`, `rotate.lua`의 KEYS[2]), `sid` 없는 옛 토큰은 거절, `rotate.lua`의 해시 비교(`==`)는 `isEqual` 규칙의 예외, 회원 행이 없는 refresh는 401 후 세션 삭제. 4-2: `confirmReset`에서 회원이 없으면 400, 재설정 요청의 발송 한도는 가입 여부와 무관하게 적용해(429로 가입 여부가 드러나지 않게) 재설정 메일 제목은 `[MyRoutine] 비밀번호 재설정 인증코드`, 본문은 `인증코드: ` + 코드. 4-3: 가입 토큰 메일은 4-2 인증 메일과 같게, `OAuthLoginResponse`는 `token`을 중첩, `consume.lua`·`sendSignupVerification` 위치·`OAuthController`·`providerEmail` 확정. 4-4: 관리자 검색 메서드(대소문자 무시, `createdAt desc`)·`AdminMemberController`, `currentVersion`은 회원이 없으면 `-1`, BANNED는 로그인 잠금 `reset`을 하지 않고, 제재 알림 문구 확정.
> 0.12 · 2026-10-16 · 읽는 곳이 없는 값을 지웠다: 4-1 세션의 `createdAt`·`lastUsedAt`과 `StoredSession`(조회는 `findMemberId`가 회원 ID만 돌려준다, `rotate.lua`의 `now` 인자와 `RedisSessionStore`의 `Clock`도 함께), 4-3 제공자 `nickname`(`OAuthUserInfo`·`PendingSocialSignup`·Redis 가입 토큰 필드·제공자별 읽는 필드·가짜 서버). 4-2 `EmailCodeStore.save`는 해시 대신 원문 코드를 받아 `verify`와 대칭이 되게 했다.
> 0.10 · 2026-10-15 · 인증·잠금 결정: ① 이메일 인증 확인·비밀번호 변경은 `TokenIssuer.reissueAccess`로 access만 새로 주고 `refreshToken`은 null(클라이언트가 기존 refresh 유지). ② 이메일 인증 API는 로그인 회원 전용이고 OAuth 가입용 발송은 4-3의 별도 API(`07-api-spec.md`도 맞춤). ③ 로그인 잠금 TTL을 5번째 실패에서 다시 걸어 "15분 동안 423"을 지킨다.
> 0.6 · 2026-10-09 · Part 1 구현 코드와 정합: `TokenResult`·`TokenResponse` 필드 이름(`expiresInSeconds`), 단계마다 늘어나는 `JwtProvider.issue`·`AuthClaims` 인자를 맞췄다. Redis 이미지 태그를 고정하고(`redis:7.4.11`) 테스트 컨테이너 기동에 1-10의 MinIO를 넣었다.
> 0.5 · 2026-10-02 · 덜어내기: 토큰 거부 코드는 401 `UNAUTHORIZED` 하나로(만료·폐기 구분은 제안), tokenVersion 로컬 캐시와 Redis 장애 정책 제거, 가입 토큰용 인증코드 발송은 별도 API로(선택적 리졸버 제거), 소셜 계정 중복은 사전 조회로.
> 0.4 · 2026-10-02 · 예치금 제거 반영(충전 API·개발용 입금 API 관련 항목 삭제)

> **끝나면**: refresh 토큰으로 로그인이 유지되고, 탈취된 토큰을 감지하며, 이메일 인증·OAuth 로그인·관리자 제재·로그인 실패 잠금이 동작한다.
> **인프라 추가**: **Redis**
> **릴리스**: `v0.4.0`

| 단계 | 제목 | 크기 |
|---|---|---|
| 4-0 | 정산 조회 보완 | S |
| 4-1 | Redis · refresh 토큰 회전 · 로그아웃 | M |
| 4-2 | 이메일 인증 · 비밀번호 변경·재설정 | M |
| 4-3 | OAuth 로그인 (Kakao · Google · Naver) | L |
| 4-4 | 즉시 차단(tokenVersion) · 관리자 제재 · 로그인 실패 잠금 | M |

---

## Part 4 공통 규칙 (Part 1~3 공통 규칙에 더해서)

### T. Redis 사용 규칙
- `StringRedisTemplate`만 쓴다(값은 문자열·해시). 객체 직렬화 설정을 따로 하지 않는다.
- 모든 키에 **TTL**을 둔다(영구 키 금지). 키 이름과 TTL은 아래 표가 기준이다.
- 여러 명령을 원자적으로 해야 하면 Lua 스크립트(`DefaultRedisScript`)를 쓴다. 스크립트 파일은 `src/main/resources/redis/*.lua`.
- 비밀값(refresh 토큰, 인증코드, 4-2 인증 토큰, 4-3 가입 토큰)은 **원문을 저장하지 않고 SHA-256 해시**(16진수 문자열, 4-1 `RefreshToken.secretHash`와 같은 형식)를 저장한다. 값으로 저장한 해시의 비교는 `MessageDigest.isEqual`(시간이 일정한 비교). 토큰이 키 이름에 들어가는 경우(인증 토큰·가입 토큰)는 키 이름에 해시를 넣고 받은 토큰을 해시해 키를 찾는다. Redis가 해시값으로 키를 찾으므로 따로 비교하지 않는다.
- 테스트: `IntegrationTestSupport`의 `@AfterEach`에서 주입받은 `StringRedisTemplate redisTemplate`(필드 `redis`는 컨테이너라 이름을 달리한다)으로 `redisTemplate.execute((RedisCallback<Void>) connection -> { connection.serverCommands().flushDb(); return null; })`를 호출한다. `execute`가 연결을 닫아 주므로 `getConnection()`을 직접 쓰지 않는다.

| 키 | 타입 | 값 | TTL | 왜 필요한가 | 단계 |
|---|---|---|---|---|---|
| `auth:session:{sessionId}` | HASH | `memberId`, `refreshHash` | 7일(갱신 때마다 연장) | refresh를 서버가 끊으려면 지금 유효한 refresh를 기억해야 한다. 회전과 재사용 탐지도 이 값과 비교한다 | 4-1 |
| `auth:member-sessions:{memberId}` | SET | sessionId 목록 | 7일(세션 생성·갱신 때 연장) | Redis는 키 이름으로만 찾을 수 있어, 전체 로그아웃·비밀번호 변경 때 회원 ID로 그 회원의 세션들을 찾는 색인이다 | 4-1 |
| `auth:email-code:{purpose}:{email}` | HASH | `codeHash`, `attempts` | 5분 | 보낸 인증코드와 비교하려고 기억한다. 6자리 대입을 막으려고 틀린 횟수를 센다 | 4-2 |
| `auth:email-cooldown:{email}` | STRING | `1` | 60초 | 발송 간격 60초 제한(FR-MEM-02) | 4-2 |
| `auth:email-hourly:{email}` | STRING | 발송 횟수 | 1시간(첫 발송 때 설정) | 시간당 5회 제한(FR-MEM-02). 간격 제한만으로는 시간당 60통까지 보낼 수 있다 | 4-2 |
| `auth:signup-verified:{tokenHash}` | STRING | 이메일 | 30분 | 코드 확인과 가입이 다른 요청이라, 확인을 통과한 브라우저에 준 토큰으로 통과한 이메일을 기억한다 | 4-2 |
| `auth:oauth-signup:{tokenHash}` | HASH | `provider`, `providerUserId`, `email` | 10분 | 소셜 로그인부터 가입 완료까지 제공자 사용자 ID를 서버가 맡아 둔다. 클라이언트에 맡기면 바꿔치기할 수 있다 | 4-3 |
| `auth:token-version:{memberId}` | STRING | 버전 숫자 | 1일 | 요청마다 필터가 비교하는 `member.token_version`의 캐시. 요청마다 DB를 읽지 않게 한다 | 4-4 |
| `auth:login-fail:{email}` | STRING | 연속 실패 횟수 | 15분(첫 실패와 5번째 실패 때 설정) | 비밀번호 대입을 막으려고 연속 실패를 센다. 15분 뒤 저절로 풀린다 | 4-4 |

**Redis 명령 ↔ `StringRedisTemplate` 호출** — 이 Part의 코드는 아래 호출만 쓴다. 아래 `redis`는 주입받은 `StringRedisTemplate`이다(Spring Data Redis 4.1 기준).

| 하고 싶은 일 (Redis 명령) | 호출 | 반환 |
|---|---|---|
| 값 저장 + 만료 (`SET k v EX n`) | `redis.opsForValue().set(key, value, Duration)` | 없음 |
| 없을 때만 저장 (`SET k v NX EX n`) | `redis.opsForValue().setIfAbsent(key, value, Duration)` | `Boolean` — true면 내가 저장했다 |
| 값 읽기 (`GET`) | `redis.opsForValue().get(key)` | `String`, 키가 없으면 `null` |
| 1 증가 (`INCR`) | `redis.opsForValue().increment(key)` | 증가 후 값 `Long`(키가 없으면 0에서 시작해 1) |
| 해시 저장 (`HSET`) | `redis.opsForHash().putAll(key, Map<String, String>)` | 없음. 값에 `null`을 넣으면 예외가 날 수 있으니 `null` 필드는 빼고 저장한다 |
| 해시 전체 읽기 (`HGETALL`) | `redis.opsForHash().entries(key)` | `Map<Object, Object>`, 키가 없으면 빈 맵 |
| 해시 필드 1 증가 (`HINCRBY`) | `redis.opsForHash().increment(key, field, 1)` | 증가 후 값 `Long` |
| 만료 설정 (`EXPIRE`) | `redis.expire(key, Duration)` | `Boolean` |
| 남은 시간 (`TTL`) | `redis.getExpire(key, TimeUnit.SECONDS)` | 초 `Long`. 키가 없으면 -2, 만료가 없으면 -1 |
| 삭제 (`DEL`) | `redis.delete(key)` / `redis.delete(Collection<String>)` | `Boolean`(한 개) / 지운 개수 `Long`. 없는 키여도 오류가 아니다 |
| 집합 (`SADD`·`SMEMBERS`·`SREM`) | `redis.opsForSet().add(key, v)` / `.members(key)` / `.remove(key, v)` | `members`는 `Set<String>` |
| Lua 실행 | `redis.execute(DefaultRedisScript<Long>, List<String> keys, Object... args)` | 스크립트가 돌려준 정수 `Long` |

### U. Part 4 에러 코드

| 코드 | HTTP | 메시지 | 정의 위치 | 단계 |
|---|---|---|---|---|
| `REFRESH_REUSED` | 401 | 이미 사용된 토큰입니다. 다시 로그인해 주세요. | `MemberErrorCode` | 4-1 |
| `RATE_LIMITED` | 429 | 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요. (details.retryAfterSeconds) | `CommonErrorCode` | 4-2 |
| `EMAIL_CODE_INVALID` | 400 | 인증코드가 올바르지 않거나 만료되었습니다. | `MemberErrorCode` | 4-2 |
| `VERIFICATION_TOKEN_INVALID` | 400 | 이메일 인증이 만료되었거나 올바르지 않습니다. 다시 인증해 주세요. | `MemberErrorCode` | 4-2 |
| `PASSWORD_MISMATCH` | 400 | 현재 비밀번호가 올바르지 않습니다. | `MemberErrorCode` | 4-2 |
| `OAUTH_PROVIDER_ERROR` | 502 | 소셜 로그인 제공자와 통신하지 못했습니다. | `MemberErrorCode` | 4-3 |
| `SIGNUP_TOKEN_INVALID` | 401 | 가입 시간이 만료되었습니다. 다시 시도해 주세요. | `MemberErrorCode` | 4-3 |
| `SOCIAL_ACCOUNT_ALREADY_LINKED` | 409 | 이미 연결된 소셜 계정입니다. | `MemberErrorCode` | 4-3 |
| `ACCOUNT_LOCKED` | 423 | 로그인 시도가 많아 잠시 잠겼습니다. (details.unlockAt) | `MemberErrorCode` | 4-4 |

---

## 4-0. 정산 조회 보완 (S)

**목표**: 판매자가 정산 상세의 품목마다 어떤 상품이 몇 개 팔렸는지 보고 그 가게주문으로 이동할 수 있다. 정산의 수수료율과, 아직 정산되지 않은 구매확정 금액(정산 예정)도 볼 수 있다.

**왜 지금**: 판매자 정산 화면(프론트 리포 `myroutine-web`)이 이 값들을 기다린다. 3-4 정산 조회를 보완하는 일이라 Redis가 들어오는 4-1 전에 끝내 두면 인증 변경과 섞이지 않는다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 이벤트 필드 추가 | 필드를 더하는 것은 호환되는 변경이라 토픽(`.v1`)을 그대로 쓴다([개발 가이드 §9.1](../development-guide.md)). 다만 이 단계의 받는 쪽은 새 필드를 필수로 받으므로, 배포 전에 발행돼 새 필드가 없는 이벤트는 역직렬화에 실패해(`quantity`가 `int`라 Jackson 3 기본값 `FAIL_ON_NULL_FOR_PRIMITIVES`에 걸린다) 재시도 없이 DLT로 간다(3-2에서 `JacksonException`을 재시도 제외로 정했다). 그래서 배포 전에 DB를 초기화한다 |

**정책 (이 단계에서 정함)**
- 정산 품목의 가게주문 ID·상품 이름·수량은 구매확정 이벤트가 실어 오고 `settlement_item`에 저장한다. 상품 이름은 주문 때의 스냅샷(`order_line.product_name`)이라 정산 뒤 상품 이름이 바뀌어도 그대로다. 수량은 주문 수량(`order_line.quantity`)이다.
- 새 컬럼 세 개는 `NOT NULL`이다. 이미 적재된 행을 채우는 마이그레이션은 쓰지 않는다. 채우려면 settlement의 마이그레이션이 `orders.order_line`을 읽어야 해서, settlement가 정산 데이터만 소유한다는 경계([ADR-009](../adr/ADR-009-settlement.md))를 넘는다. 대신 이 단계를 운영에 배포하기 전에 운영 DB를 초기화한다. 정산 품목이 남아 있으면 마이그레이션이 실패해 앱이 뜨지 않는다. outbox도 함께 비므로 새 필드가 없는 이벤트도 남지 않는다.
- 정산 예정은 그 가게의 `settlement_id IS NULL`인 품목 전부다(달을 나누지 않는다). 3-6 탈퇴 조건 `existsUnsettledByShopIdIn`과 같은 기준이라, 탈퇴가 `PENDING_SETTLEMENT`로 막혔을 때 판매자가 무엇을 기다리는지 이 API로 본다. `PAID`가 아닌 정산 때문에 막힌 경우는 정산 목록의 `status`로 본다.
- 정산 예정에는 수수료와 정산액을 넣지 않는다. 정산을 실행할 때의 수수료율(`myroutine.settlement.fee-rate-bp`)로 확정되고, 수수료는 정산 합계에 한 번 내림하므로 미리 계산한 값과 다를 수 있다.
- 정산 예정은 폐업한 가게도 조회한다(`shopApi.verifyOwner`). 폐업한 가게의 미정산 품목도 탈퇴를 막기 때문이다.

### 할 일

**구현 순서**: 1) 마이그레이션 → 2) order 이벤트 → 3) settlement 품목 적재·조회 → 4) 정산 응답의 수수료율 → 5) 정산 예정 API → 테스트. 문서([ERD](../02-design/03-erd.md), [이벤트](../02-design/05-events.md), [API 명세](../02-design/07-api-spec.md))는 이미 반영돼 있다.

**1) 마이그레이션** — `settlement` 폴더에 새 파일 `V{yyyyMMddHHmm}__settlement_add_settlement_item_order_line_fields.sql`.

```sql
ALTER TABLE settlement.settlement_item
    ADD COLUMN shop_order_id uuid         NOT NULL,
    ADD COLUMN product_name  varchar(100) NOT NULL,
    ADD COLUMN quantity      int          NOT NULL;
```

길이는 `orders.order_line.product_name`(`varchar(100)`)과 같다. 인덱스는 더하지 않는다. 기본값 없는 `NOT NULL` 컬럼이라 테이블에 행이 있으면 실패한다. 테스트 DB는 매번 빈 상태에서 시작해 문제가 없다.

**2) order 이벤트**

| 클래스 | 규격 |
|---|---|
| `OrderLineConfirmedEvent` | 끝에 `UUID shopOrderId, String productName, int quantity`를 더한다 |
| `OrderEventPublisher.lineConfirmed` | 새 필드에 `shopOrder.getId()`, `orderLine.getProductName()`, `orderLine.getQuantity()`를 넣는다 |

- 생성자 인자가 늘어나므로 기존 테스트의 `new OrderLineConfirmedEvent(...)` 호출(`SettlementItemConsumerTest`)을 고친다.

**3) settlement 품목 적재·조회**

| 클래스 | 규격 |
|---|---|
| `SettlementItemRepository`·`JpaSettlementItemRepository` | `insertIgnoringConflict(UUID id, UUID orderLineId, UUID shopId, UUID orderId, UUID shopOrderId, UUID productId, String productName, int quantity, long amount, Instant confirmedAt, Instant now)` — INSERT의 컬럼 목록에 `shop_order_id`, `product_name`, `quantity`를 더한다. `findBySettlementIdFirstPage`·`findBySettlementIdAfter`의 SELECT에 `shop_order_id AS "shopOrderId", product_name AS "productName", quantity`를 더한다 |
| `SettlementItemRow` | `UUID getShopOrderId()`, `String getProductName()`, `int getQuantity()`를 더한다 |
| `SettlementItemService.register` | 이벤트의 세 값을 그대로 넘긴다 |
| `SettlementItemResult`·`SettlementItemResponse` | `(UUID orderLineId, UUID orderId, UUID shopOrderId, UUID productId, String productName, int quantity, long amount, Instant confirmedAt)` |

**4) 정산 응답의 수수료율**

| 클래스 | 규격 |
|---|---|
| `SettlementResult`·`SettlementResponse` | `grossAmount` 다음에 `int feeRateBp`(500 = 5%)를 더한다. `SettlementResult.from`은 `settlement.getFeeRateBp()`를 읽는다. 목록과 상세(`$.settlement`) 응답 모두에 들어간다 |

**5) 정산 예정 API**

| 클래스 | 규격 |
|---|---|
| `UpcomingSettlementSum` (domain) | 인터페이스 프로젝션. `int getItemCount()`, `long getGrossAmount()`, `Instant getFirstConfirmedAt()`. `SettlementSum`과 같은 이유로 인터페이스다 |
| `SettlementItemRepository.sumUnsettledByShopId(UUID shopId)` → `UpcomingSettlementSum` | 아래 native 쿼리. 집계 함수만 있고 `GROUP BY`가 없어 품목이 없어도 한 행이 나온다. 3-4의 부분 인덱스 `idx_settlement_item_unsettled (shop_id, confirmed_at) WHERE settlement_id IS NULL`을 탄다 |
| `UpcomingSettlementResult` (application) | `(int itemCount, long grossAmount, Instant firstConfirmedAt)`, `static from(UpcomingSettlementSum)` |
| `SettlementQueryService.getUpcoming(UUID memberId, UUID shopId)` → `UpcomingSettlementResult` | `@Transactional(readOnly = true)`. `shopApi.verifyOwner(shopId, memberId)`(폐업한 가게 포함. `verifyOwnerOfActiveShop`이 아니다) → `sumUnsettledByShopId(shopId)` |
| `SettlementController`, `UpcomingSettlementResponse` | 아래 API 표. `@CurrentMember UUID memberId`를 받아 `getUpcoming`을 부른다 |

```sql
SELECT COUNT(*)::int AS "itemCount", COALESCE(SUM(amount), 0)::bigint AS "grossAmount", MIN(confirmed_at) AS "firstConfirmedAt"
FROM settlement.settlement_item
WHERE shop_id = :shopId AND settlement_id IS NULL
```

| API | 권한 | 응답 |
|---|---|---|
| `GET /api/shops/{shopId}/settlements/upcoming` | 가게 주인(폐업한 가게 포함) | 200 `UpcomingSettlementResponse(itemCount, grossAmount, firstConfirmedAt)`. 품목이 없으면 `0`, `0`, `null` |

- 경로가 `GET /api/shops/{shopId}/settlements/{id}`와 겹쳐 보이지만, Spring은 리터럴 경로(`upcoming`)를 경로 변수보다 먼저 고른다. `upcoming`이 `{id}`로 가면 UUID 변환에 실패해 400이 된다.

**운영 반영 순서** — 이 단계의 PR을 develop에 머지하기 전에 VM에서 Postgres와 Kafka 볼륨을 지운다. 머지한 뒤에 지우면 CD가 먼저 돌아 마이그레이션이 실패하고 자동 롤백된다.

| 순서 | 할 일 | 이 시점의 상태 |
|---|---|---|
| 1 | `sudo -iu runner`로 `runner` 사용자의 셸을 연 뒤 러너의 checkout 폴더(`cd ~/actions-runner/_work/myroutine-v2/myroutine-v2`)로 이동한다(`sudo -u runner cd ...`는 `cd`가 셸 내장 명령이라 동작하지 않는다). `export COMPOSE="docker compose --env-file /opt/myroutine/.env -f docker/docker-compose.ops.yml"`, `export IMAGE_TAG=$(cat /opt/myroutine/deployed-sha)` | 지금 버전이 돌고 있다 |
| 2 | `docker volume ls \| grep myroutine-ops`로 볼륨 이름(`myroutine-ops_postgres_data`, `myroutine-ops_kafka_data`)을 확인한다 | 그대로 |
| 3 | `$COMPOSE stop app kafka postgres` → `$COMPOSE rm -f app kafka postgres` → `docker volume rm myroutine-ops_postgres_data myroutine-ops_kafka_data` | 앱이 멈췄다. 회원·가게·주문·정산이 모두 지워졌다 |
| 4 | `$COMPOSE up -d --wait` → `scripts/smoke.sh` | 지금 버전이 빈 DB로 뜬다(Flyway가 스키마를 다시 만든다) |
| 5 | 4-0 PR을 develop에 머지한다 | CD가 4-0을 배포하고 마이그레이션이 빈 `settlement_item`에 적용된다 |

- Kafka 볼륨도 지운다. 남겨 두면 지운 DB의 데이터를 가리키는 메시지가 토픽에 남는다. 소비 오프셋이 만료되면(기본 7일) 이 메시지를 다시 읽을 수 있고, 아직 소비되지 않은 옛 구매확정 메시지는 새 필드가 없어 DLT로 간다.
- MinIO 볼륨은 남긴다. 지운 상품의 이미지 객체가 고아로 남지만 읽는 곳이 없다.
- `settlement` 테이블만 비우지 않는다. 구매확정된 주문 품목은 남는데 정산 품목만 없어져 "확정 품목마다 정산 품목 1행"(INV-07)이 깨진다.
- 관리자 계정도 지워지므로 다시 가입하고 `role`을 `ADMIN`으로 바꾼 뒤 로그인한다.

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다(`support/`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `insertSettlementItem(UUID shopId, long amount, Instant confirmedAt, UUID shopOrderId, String productName, int quantity)` → `UUID`(만든 품목의 `orderLineId`) | 3-4a의 같은 이름 메서드에 `shop_order_id`, `product_name`, `quantity`를 더한 INSERT | `SettlementControllerTest` 8번 |
| 3-4a의 `insertSettlementItem(shopId, amount, confirmedAt)`·`insertSettlementItems(...)` 수정 | 새 컬럼이 `NOT NULL`이라 INSERT에 세 컬럼을 더하고 고정값을 넣는다: `shop_order_id` = `Ids.newId()`, `product_name` = `"product"`, `quantity` = 1. 세 인자 메서드는 위 여섯 인자 메서드를 불러도 된다 | 3-4·3-6의 기존 테스트 |
| `settlementItemSnapshotOf(UUID orderLineId)` 확장 | SELECT에 `i.shop_order_id, i.product_name, i.quantity`를 더하고 `SettlementItemSnapshot`에 `shopOrderId`(UUID), `productName`(String), `quantity`(int)를 더한다 | `SettlementItemConsumerTest` |

이미 있는 헬퍼: `insertSettlementItem(shopId, amount, confirmedAt)`·`insertSettlementItems(...)`·`settlementSnapshotOf(shopId, month)`(3-4), `createDeliveredOrder`·`shopOrderIdOf(orderId, shopId)`·`closeShop(shopId)`·`TestKafka.send`·`signup`·`login`·`openShop`.

#### `order/application/ConfirmEventPublishingTest`

**`confirmPublishesLineConfirmed`(수동 구매확정) 고치기** — 준비·동작은 그대로 두고 payload 확인에 더한다.
- payload `shopOrderId` = `shopOrderIdOf(orderId, shopId)` 문자열, `productName` = `"product"`, `quantity` = 1.

#### `settlement/application/SettlementItemConsumerTest`

준비: 기존 이벤트에 `shopOrderId` = `Ids.newId()`, `productName` = `"상품A"`, `quantity` = 3을 더한다.

**`duplicateEventStoresOnce`(같은 확정 이벤트 두 번) 고치기** — `settlementItemSnapshotOf(orderLineId1)` 확인에 더한다.
- `shopOrderId()` = 이벤트의 `shopOrderId`, `productName()` = `"상품A"`, `quantity()` = 3.

#### `settlement/web/SettlementControllerTest`

**`getSettlements`(가게 주인의 목록 조회) 고치기** — `$.items[0].feeRateBp` = 500을 더한다.

**8. 정산 품목의 가게주문·상품 이름·수량** — 준비: 판매자 A와 가게 A. 가게 A에 `insertSettlementItem(shopA, 7500, Instant.parse("2026-09-15T03:00:00Z"), shopOrderId, "상품A", 3)`(`shopOrderId`는 `Ids.newId()`) → `settlementService.run(YearMonth.of(2026, 9))` → `settlementA = settlementSnapshotOf(shopA).id()`. 동작: A의 토큰으로 `GET /api/shops/{shopA}/settlements/{settlementA}`.
- 응답 200. `$.items.items[0].shopOrderId` = `shopOrderId`, `productName` = `"상품A"`, `quantity` = 3.

#### `settlement/web/UpcomingSettlementTest`

공통 준비: 판매자 A(`seller@test.com`)와 가게 A(`openShop(sellerA, "1111111111")`), 판매자 B(`seller2@test.com`)와 가게 B(`"2222222222"`). 토큰은 `login`.

**1. 정산 예정 합계** — 준비: 가게 A에 `insertSettlementItem(shopA, 1000, Instant.parse("2026-08-15T03:00:00Z"))` → `settlementService.run(YearMonth.of(2026, 8))`. 이어서 가게 A에 `insertSettlementItem(shopA, 7500, Instant.parse("2026-09-15T03:00:00Z"))`, `insertSettlementItem(shopA, 3000, Instant.parse("2026-10-02T03:00:00Z"))`, 가게 B에 `insertSettlementItem(shopB, 5000, Instant.parse("2026-09-01T03:00:00Z"))`. 동작: A의 토큰으로 `GET /api/shops/{shopA}/settlements/upcoming`.
- 응답 200. `$.itemCount` = 2, `$.grossAmount` = 10500, `$.firstConfirmedAt` = `2026-09-15T03:00:00Z`.

**2. 정산 예정이 없으면 0** — 준비: 품목을 넣지 않는다. 동작: A의 토큰으로 `GET /api/shops/{shopA}/settlements/upcoming`.
- 응답 200. `$.itemCount` = 0, `$.grossAmount` = 0, `$.firstConfirmedAt` null.

**3. 폐업한 가게도 조회한다** — 준비: 가게 A에 `insertSettlementItem(shopA, 1000, Instant.parse("2026-09-15T03:00:00Z"))` → `closeShop(shopA)`. 동작: A의 토큰으로 `GET /api/shops/{shopA}/settlements/upcoming`.
- 응답 200. `$.itemCount` = 1, `$.grossAmount` = 1000.

**4. 남의 가게 정산 예정 조회 거부** — 동작: B의 토큰으로 `GET /api/shops/{shopA}/settlements/upcoming`.
- 응답 403, `$.code` = `FORBIDDEN`.

**리뷰 때 물어볼 것**
- 운영 DB를 초기화하지 않고 배포하면 어디서 어떻게 실패하나? 초기화할 수 없는 운영이었다면 기존 행을 어떻게 채웠겠나?
- 배포 전에 발행돼 새 필드가 없는 구매확정 이벤트가 소비되면 어떻게 되나?
- 정산 예정 금액에 수수료를 미리 빼서 보여 주면 정산 뒤 금액과 왜 달라질 수 있나?

---

## 4-1. Redis · refresh 토큰 회전 · 로그아웃 (M)

**목표**: Access 토큰을 15분으로 줄이고, refresh 토큰으로 갱신한다. refresh는 쓸 때마다 바뀌고, 이미 쓴 refresh가 다시 오면 탈취로 보고 그 세션을 끊는다.

**왜 지금**: 1-4에서 남겨둔 한계(로그아웃 불가, 1시간 만료)를 해결한다. 서버가 상태(세션)를 가져야 하므로 Redis가 등장한다.

**새로 등장**

| 개념·도구 | 한 줄 설명 |
|---|---|
| Redis | 만료 시간(TTL)이 있는 빠른 저장소. 세션·인증코드·잠금 횟수에 쓴다 |
| 세션 = 로그인 한 번 | 로그인할 때마다 세션 하나(sessionId). 회전해도 sessionId는 그대로이고 refresh만 바뀐다. **세션이 곧 family**다. 서블릿 `HttpSession`이 아니다. 일반 API 인증은 JWT만 검증하고, 세션은 refresh·로그아웃·비밀번호 변경 때만 본다 |
| refresh = 랜덤 문자열 | refresh는 JWT가 아니다. 쓸 때마다 Redis에서 폐기·재사용 여부를 확인해야 해서 서명으로 얻는 이점이 없으므로, `{sessionId}.{secret}` 형태의 랜덤 문자열로 만든다 |
| refresh 회전 | refresh를 쓰면 새 refresh를 주고 이전 것은 무효 (ADR-006) |
| 재사용 탐지 | 무효가 된 refresh가 다시 오면 누군가 훔친 것 → 그 세션 폐기. 다른 기기(다른 세션)는 유지 |
| Lua 스크립트 | "해시가 맞으면 새 해시로 바꾼다"를 Redis 안에서 한 번에 실행(동시 갱신 경합 방지) |

**정책 (이 단계에서 정함)**
- Access 15분(`myroutine.jwt.access-token-ttl: 15m`), refresh 7일(`myroutine.jwt.refresh-token-ttl: 7d`).
- refresh 형식: `{sessionId}.{랜덤 32바이트 Base64URL}`. Redis에는 랜덤 부분의 SHA-256만 저장한다.
- 같은 refresh로 **동시에** 두 번 갱신하면 늦은 쪽은 재사용으로 판단해 세션을 폐기한다(OPEN-02 권장안: 엄격).

### 할 일

**구현 순서**: ① 인프라(compose·의존성·`IntegrationTestSupport`)(1) → ② common.security(2): `JwtProperties` → `AuthClaims` → `JwtProvider` → `SecurityConfig` → ③ member.domain(3): `MemberErrorCode` → `RefreshToken` → `SessionStore` → ④ member.infrastructure(4): `rotate.lua` → `RedisSessionStore` → ⑤ member.application(5): `TokenResult` → `TokenIssuer` → `SignupService`·`LoginService` 변경 → `TokenRefreshService` → `LogoutService` → ⑥ member.web(6): `TokenResponse`·`SignupResponse` → `AuthController` → 테스트(`RefreshTokenTest` → `TokenRefreshTest` → `RefreshConcurrencyTest`).

**1) 인프라**
- `docker-compose`: `redis:7.4.11`(문서가 정한 7.4 줄의 최신 패치, 2026-10-09 Docker Hub 확인. 8.x도 있다), `127.0.0.1:6379:6379`. 운영 compose(`docker/docker-compose.ops.yml`)에도 같은 태그의 `redis` 서비스를 더하고 호스트 포트는 열지 않는다
- 의존성: `implementation 'org.springframework.boot:spring-boot-starter-data-redis'`(Boot 4.1.1에 있다, Lettuce 7.5.2를 Boot가 관리)
- `IntegrationTestSupport`: `@ServiceConnection(name = "redis") static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4.11").withExposedPorts(6379);` → `Startables.deepStart(postgres, minio, kafka, redis).join()`. `@AfterEach`에서 Redis를 비운다(공통 규칙 T의 `flushDb()`)

**2) common.security**

| 클래스 | 규격 |
|---|---|
| `JwtProperties` | `@NotNull Duration refreshTokenTtl` 추가. `application.yaml`에 `refresh-token-ttl: 7d`를 넣고 `access-token-ttl`을 `1h`에서 `15m`으로 바꾼다(필드가 없으면 `@NotNull`로 기동이 실패한다). 쓰는 곳은 `TokenIssuer`(세션 TTL, 응답의 `refreshExpiresInSeconds`)와 `TokenRefreshService`(`rotate`에 넘길 TTL)다 |
| `AuthClaims` | `UUID sessionId` 추가 (`sid` 클레임) — 로그아웃과 비밀번호 변경이 "이 토큰이 속한 세션"을 알아야 해서 넣는다. 컨트롤러에서 꺼낼 때는 memberId만 주는 `@CurrentMember` 대신 `@AuthenticationPrincipal AuthClaims claims`로 받아 `claims.sessionId()`를 쓴다. `JwtAuthenticationFilter`가 principal로 `AuthClaims`를 넣어 두기 때문이다(`IdempotencyAspect`도 같은 방식으로 꺼낸다) |
| `JwtProvider` | `JwtProvider.issue(UUID memberId, String role)`(1-4) → `issue(UUID memberId, String role, UUID sessionId)`로 변경: `.claim("sid", sessionId.toString())`을 더한다. `parse`는 `claims.get("sid", String.class)`를 `UUID.fromString`해서 `AuthClaims`에 넣는다. `sid` 클레임이 없는 옛 토큰은 `parse`가 `Optional.empty()`로 거절한다(401 `UNAUTHORIZED`. 세션 없이 발급된 access는 로그아웃할 세션이 없어서 받아 주지 않는다) |
| `SecurityConfig` | `.requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()`를 `/api/auth/**` 허용보다 **먼저** 둔다. `authorizeHttpRequests`는 위에서부터 처음 맞는 규칙만 적용하므로, 순서가 반대면 `/api/auth/**`가 먼저 맞아 인증 없이 통과하고 `claims`가 `null`이 된다 |

| 단계 | `AuthClaims` (현재 `(UUID memberId, String role)`) | `JwtProvider.issue(...)` | 새 클레임 |
|---|---|---|---|
| 4-1 | `(memberId, role, sessionId)` | `(memberId, role, sessionId)` | `sid` |
| 4-4 | `(memberId, role, sessionId, tokenVersion)` | `(memberId, role, sessionId, tokenVersion)` | `tv` |

- `@CurrentMember`는 `expression = "memberId"`라 인자가 늘어도 바꾸지 않는다.
- 기존 테스트를 컴파일되게 고친다: `JwtProviderTest`의 `new JwtProperties(secret, Duration.ofHours(1))` 2곳에 `refreshTokenTtl` 인자를, `issue(memberId, role)` 2곳에 `sessionId`를, `MemberControllerTest`의 `issue(memberId, "USER")` 1곳에 `sessionId`를 더한다. 4-4에서 `issue`의 인자가 늘 때도 같은 호출을 고친다.
- access 만료가 15분이 되므로 `AuthControllerTest` 가입 케이스의 `$.expiresIn` 기대값과 DisplayName의 `3600`을 `900`으로 고친다.

**3) member.domain**

| 클래스 | 규격 |
|---|---|
| `MemberErrorCode` | `REFRESH_REUSED`(401)를 추가한다 |
| `RefreshToken` | 값 객체 — Spring 빈이 아니다. `record (UUID sessionId, String secret)`. 클라이언트에 주는 refresh 문자열 `{sessionId}.{secret}`을 만들고 해석한다. sessionId가 문자열에 들어 있어서 갱신 요청이 오면 Redis의 어느 세션을 볼지 바로 안다. 메서드는 아래 표 |
| `SessionStore` | 인터페이스 — refresh 세션을 저장소에 넣고 빼는 포트. 서비스가 Redis를 모르게 하려고 인터페이스로 둔다. 메서드는 아래 표 |

`RefreshToken` 메서드

| 메서드 | 하는 일 | 입력 → 반환 |
|---|---|---|
| `static RefreshToken generate(UUID sessionId)` | 새 refresh를 만든다 | sessionId → `SecureRandom`으로 뽑은 32바이트를 `Base64.getUrlEncoder().withoutPadding()`로 인코딩한 문자열이 `secret`인 `RefreshToken` |
| `String value()` | 클라이언트에 내려줄 문자열 | → `sessionId + "." + secret` |
| `static RefreshToken parse(String raw)` | 클라이언트가 보낸 문자열을 되돌린다 | raw → `RefreshToken`. `null`·점으로 나눈 결과가 2조각이 아님·앞조각이 UUID가 아님(`UUID.fromString`의 `IllegalArgumentException`을 잡는다)이면 `BusinessException(UNAUTHORIZED)`(401) |
| `String secretHash()` | Redis에 저장·비교할 값 | → `secret`의 SHA-256(`MessageDigest.getInstance("SHA-256")`)을 한 가지 문자열 형식(예: 16진수)으로 통일한 값 |
| `String toString()` | 로그·예외 메시지에 secret이 새지 않게 record의 기본 `toString()`을 덮어쓴다 | → `"RefreshToken[sessionId=" + sessionId + ", secret=***]"` |

`SessionStore` 메서드

| 메서드 | 하는 일 | 비고 |
|---|---|---|
| `void create(UUID sessionId, UUID memberId, String refreshHash, Duration ttl)` | 로그인·가입 직후 세션을 만든다 | `auth:session:{sessionId}` 해시에 `memberId`·`refreshHash`를 저장하고 `auth:member-sessions:{memberId}` 집합에 sessionId를 더한다. 두 키 모두 `ttl`로 만료 설정 |
| `Optional<UUID> findMemberId(UUID sessionId)` | 세션 주인 조회 | 해시를 읽어(`entries`) 비어 있으면(없음·만료) `Optional.empty()`, 아니면 `memberId` 필드를 UUID로 |
| `boolean rotate(UUID sessionId, UUID memberId, String expectedHash, String newHash, Duration ttl)` | **저장된 `refreshHash`가 `expectedHash`일 때만** `newHash`로 바꾸고, 세션 해시와 `auth:member-sessions:{memberId}` 집합의 만료를 `ttl`로 다시 건다(아래 `rotate.lua`). 집합의 만료를 함께 늘리지 않으면 매일 refresh하는 사용자도 7일 뒤 집합이 사라져, `deleteAllOf`(전체 로그아웃·비밀번호 변경)가 살아 있는 세션을 찾지 못한다 | `true`=바꿨다. `false`=해시가 달랐다(이미 쓴 refresh)·세션이 그 사이에 사라졌다 |
| `void delete(UUID sessionId)` | 세션 하나를 폐기한다 | 해시 키를 지우고 회원의 집합에서도 뺀다. 집합에서 빼려면 memberId가 필요하니 지우기 **전에** 해시에서 읽어 둔다. 없는 세션이어도 오류가 아니다 |
| `void deleteAllOf(UUID memberId)` | 그 회원의 모든 세션 폐기 | 집합에서 읽은 sessionId마다 해시 키를 지우고, 읽은 sessionId만 집합에서 뺀다. 집합 키를 통째로 지우면 읽은 뒤 다른 기기에서 막 만든 세션이 집합에서 빠져, 다음 `deleteAllOf`가 그 세션을 찾지 못한다 |
| `void deleteAllOfExcept(UUID memberId, UUID keepSessionId)` | 한 세션만 남기고 폐기 | 비밀번호 변경이 쓴다(4-2). `keepSessionId`는 건드리지 않는다 |

**4) member.infrastructure**

| 클래스 | 규격 |
|---|---|
| `RedisSessionStore` | `@Component`. `SessionStore` 구현. 의존성은 `StringRedisTemplate`, `rotate.lua`를 담은 `DefaultRedisScript<Long>`이다. `rotate`는 아래 `rotate.lua`로 구현한다 |

`RedisSessionStore` 메서드 구현. 키 이름은 private 메서드 `sessionKey(UUID sessionId)` = `"auth:session:" + sessionId`, `memberSessionsKey(UUID memberId)` = `"auth:member-sessions:" + memberId`로 한곳에서 만든다. Redis에는 UUID를 `toString()`한 문자열로 넣고, 읽을 때 `UUID.fromString`으로 되돌린다. 호출은 공통 규칙 T의 호출 표에 있는 것만 쓴다.

| 메서드 | 순서 |
|---|---|
| `create` | 1. `opsForHash().putAll(sessionKey, Map.of("memberId", memberId.toString(), "refreshHash", refreshHash))` 2. `expire(sessionKey, ttl)` 3. `opsForSet().add(memberSessionsKey, sessionId.toString())` 4. `expire(memberSessionsKey, ttl)`. 집합의 만료는 가장 최근 세션에 맞춰 다시 걸린다. 1과 2 사이에 Redis 연결이 끊기면 만료 없는 해시가 남을 수 있다. 이때 로그인은 500으로 실패해 refresh가 클라이언트로 나가지 않으므로, 그 키로 들어올 수는 없고 키만 남는다 |
| `findMemberId` | 1. `opsForHash().entries(sessionKey)` 2. 빈 맵이면 `Optional.empty()` 3. 아니면 `Optional.of(UUID.fromString((String) map.get("memberId")))` |
| `rotate` | 1. 위 "자바에서 부르는 형태"대로 `execute` 2. `Long.valueOf(1L).equals(r)`를 돌려준다. `r`이 `null`일 수 있어 `r == 1L`로 언박싱하지 않는다 |
| `delete` | 1. `opsForHash().entries(sessionKey)`. 빈 맵이면 바로 끝낸다(없는 세션) 2. 맵의 `memberId`를 읽어 둔다 3. `delete(sessionKey)` 4. `opsForSet().remove(memberSessionsKey(memberId), sessionId.toString())` |
| `deleteAllOf` | 1. `Set<String> ids = opsForSet().members(memberSessionsKey)`. 비어 있으면 끝낸다 2. `ids`를 `"auth:session:" + id` 목록으로 바꿔 `delete(Collection)` 한 번으로 지운다 3. `opsForSet().remove(memberSessionsKey, ids.toArray())`. 마지막 원소가 빠지면 Redis가 집합 키를 스스로 지운다 |
| `deleteAllOfExcept` | `deleteAllOf`와 같고, 1에서 읽은 `ids`에서 `keepSessionId.toString()`을 뺀 뒤 2·3을 한다. 뺀 뒤 비어 있으면 끝낸다(`remove`에 값을 하나도 주지 않으면 Redis가 오류를 낸다) |

`rotate.lua` — `SessionStore.rotate`의 비교와 교체를 **Redis 안에서 한 번에** 실행한다. GET → 비교 → SET을 자바에서 따로 하면 같은 refresh로 온 두 요청이 둘 다 비교를 통과할 수 있기 때문이다. 위치는 `src/main/resources/redis/rotate.lua`.
```lua
-- KEYS[1] = auth:session:{id}, KEYS[2] = auth:member-sessions:{memberId}, ARGV[1] = expectedHash, ARGV[2] = newHash, ARGV[3] = ttlSeconds
if redis.call('HGET', KEYS[1], 'refreshHash') == ARGV[1] then
  redis.call('HSET', KEYS[1], 'refreshHash', ARGV[2])
  redis.call('EXPIRE', KEYS[1], ARGV[3])
  redis.call('EXPIRE', KEYS[2], ARGV[3])
  return 1
end
return 0
```

- 자바에서 부르는 형태: `Long r = redis.execute(rotateScript, List.of("auth:session:" + sessionId, "auth:member-sessions:" + memberId), expectedHash, newHash, String.valueOf(ttl.getSeconds()));` → `r`이 `1L`이면 `true`. `rotateScript`는 `DefaultRedisScript<Long>`에 `setLocation(new ClassPathResource("redis/rotate.lua"))`, `setResultType(Long.class)`를 한 것이다. 이 객체는 `RedisSessionStore`의 생성자 안에서 만든다(`rotate`만 쓰는 객체라 `@Bean`으로 올리지 않는다)
- 해시 비교가 자바의 `MessageDigest.isEqual`이 아니라 Redis 안의 `==`에서 일어난다. 공통 규칙 T의 `isEqual` 규칙의 예외다: 비교하는 값이 둘 다 SHA-256 해시라 공격자가 입력으로 저장된 해시의 앞부분을 조절할 수 없어, 비교 시간으로 값을 알아낼 수 없다

**5) member.application**

클래스들이 이렇게 이어진다.

- 가입·로그인: `TokenIssuer.issueNewSession` → 세션을 Redis에 만들고 access + refresh를 돌려준다
- 갱신(`POST /api/auth/refresh`): `TokenRefreshService` → `SessionStore.rotate`로 refresh를 바꾸고 → `TokenIssuer.reissue`로 새 토큰을 만든다
- 로그아웃: `LogoutService` → `SessionStore.delete*`

| 클래스 | 메서드 |
|---|---|
| `TokenResult` | 기존 `record TokenResult(String accessToken, long expiresInSeconds)`에 `String refreshToken, long refreshExpiresInSeconds` 추가. |
| `TokenIssuer` | `issueNewSession`·`reissue`·`reissueAccess` 3개. 규격은 아래 표 |
| `SignupService` | `signUp` 변경(아래) |
| `LoginService` | `login` 변경(아래) |
| `TokenRefreshService` | `@Transactional(readOnly = true) TokenResult refresh(String rawRefresh)` |
| `LogoutService` | `void logout(UUID memberId, UUID sessionId, boolean all)` |

**`TokenIssuer`** (`@Component`) — **세션 생성과 토큰 발급을 한곳에 모은다.** 가입·로그인·갱신(4-1)과 비밀번호 변경(4-2), 소셜 로그인·가입(4-3)이 모두 "access + refresh 한 쌍"이 필요한데, 호출할 때마다 `JwtProvider`·`SessionStore`·TTL을 조합하면 코드가 흩어지기 때문이다. 트랜잭션을 열지 않고 호출한 서비스의 트랜잭션 안에서 실행된다(Redis 쓰기는 DB 롤백으로 되돌아가지 않는다). 의존성: `JwtProvider`, `SessionStore`, `JwtProperties`.

| 메서드 | 하는 일 | 입력 → 반환 |
|---|---|---|
| `TokenResult issueNewSession(Member member)` | **새 세션을 만들고** 토큰 한 쌍을 발급한다 | 회원 → `TokenResult` |
| `TokenResult reissue(Member member, UUID sessionId, RefreshToken refresh)` | **기존 세션**에 대한 새 access를 만들고, 이미 정해진 `refresh`와 묶어 돌려준다(세션은 만들지 않는다) | 회원·세션·**이미 회전이 끝난 새 refresh** → `TokenResult` |
| `TokenResult reissueAccess(Member member, UUID sessionId)` | **기존 세션의 access만** 새로 만든다. 세션·refresh는 건드리지 않는다. 비밀번호 변경처럼 세션은 그대로이고 클레임(`tv`)만 바뀔 때 쓴다 | 회원·세션 → `TokenResult`(`refreshToken = null`, `refreshExpiresInSeconds = 0`. refresh 원문은 서버에 해시로만 있어 다시 줄 수 없으므로 클라이언트가 기존 refresh를 계속 쓴다) |

`issueNewSession` 순서
1. `UUID sessionId`를 새로 만든다(`Ids.newId()`)
2. `RefreshToken refresh = RefreshToken.generate(sessionId)`
3. `sessionStore.create(sessionId, member.getId(), refresh.secretHash(), refreshTokenTtl)`
4. `String access = jwtProvider.issue(member.getId(), member.getRole().name(), sessionId)` — 4-4에서 `tokenVersion` 인자가 늘어 이 호출을 고친다
5. `new TokenResult(access, jwtProvider.accessTokenTtl().getSeconds(), refresh.value(), refreshTokenTtl.getSeconds())`

`reissue`는 위 2·3을 하지 않고 4·5만 한다(5의 refresh는 인자로 받은 `refresh`).

**`SignupService`·`LoginService` 변경** — 이 단계부터 두 서비스는 `JwtProvider.issue`를 직접 부르지 않는다. 지금은 `JwtProvider`를 주입받아 access만 만들고 `new TokenResult(access, ttl)`을 직접 만든다.
- `SignupService.signUp`(`@Transactional`): 의존성 `JwtProvider`를 `TokenIssuer`로 바꾸고, `memberRepository.save` 뒤 `tokenIssuer.issueNewSession(savedMember)`가 돌려준 `TokenResult`를 `SignupResult(savedMember.getId(), token)`에 넣는다.
- `LoginService.login`(`@Transactional(readOnly = true)`): 의존성을 같게 바꾸고 `member.verifyCanLogin()` 뒤 `return tokenIssuer.issueNewSession(member)`.

`TokenRefreshService` (`@Service`) — **refresh 한 장으로 새 access + refresh를 받는 유스케이스.** `AuthController.refresh`가 부른다. 의존성: `SessionStore`, `MemberRepository`, `TokenIssuer`, `JwtProperties`(`rotate`에 넘길 `refreshTokenTtl`).

`refresh` — 입력은 클라이언트가 보낸 refresh 문자열 그대로. 순서:
1. `RefreshToken refresh = RefreshToken.parse(rawRefresh)` — 형식이 틀리면 401 `UNAUTHORIZED`
2. `UUID memberId = sessionStore.findMemberId(refresh.sessionId())` — 없으면(만료·로그아웃·이미 폐기) 401 `UNAUTHORIZED`
3. `RefreshToken newRefresh = RefreshToken.generate(refresh.sessionId())` → `sessionStore.rotate(sessionId, memberId, refresh.secretHash(), newRefresh.secretHash(), refreshTokenTtl)`. **`false`면 이미 쓴 refresh가 다시 온 것(재사용)**이다. `sessionStore.delete(sessionId)`로 세션을 폐기하고 401 `REFRESH_REUSED`. 같은 refresh로 동시에 두 요청이 오면 한쪽만 `true`를 받고 다른 쪽은 여기로 와서 세션이 폐기된다(정책의 "엄격"). 이긴 쪽이 받은 새 refresh도 같이 죽는다
4. `Member member = memberRepository.findById(memberId)` → `member.verifyCanLogin()`. BANNED는 403 `MEMBER_BANNED`, WITHDRAWN은 401 `LOGIN_FAILED`다. 실패하면 `sessionStore.delete(sessionId)` 후 그 예외를 던진다(Redis 호출은 예외가 나도 되돌아가지 않아 삭제가 남는다). 회원 행이 없으면 `sessionStore.delete(sessionId)` 후 401 `UNAUTHORIZED`다(세션 주인이 사라진 경우)
5. `return tokenIssuer.reissue(member, sessionId, newRefresh)` — 현재 회원의 role로 만든 새 access(같은 sessionId) + `newRefresh`

`LogoutService` (`@Service`) — 로그아웃. 의존성 `SessionStore`. `AuthController.logout`이 부른다.

`logout`
- `all == false`: `sessionStore.delete(sessionId)` — 지금 쓰는 기기(토큰의 `sid`)만
- `all == true`: `sessionStore.deleteAllOf(memberId)` — 모든 기기. 4-4에서 여기에 tokenVersion 올리기가 붙는다
- DB를 읽지 않으므로 이 단계에서는 `@Transactional`이 필요 없다. 이미 폐기된 세션이어도 오류가 아니라 204다
- 주의: access는 서버 상태가 없어서 로그아웃해도 만료(15분)까지 통한다. 바로 막는 것은 4-4(tokenVersion)다. 이 단계에서 끊는 것은 refresh뿐이다

**6) member.web**

- `TokenResponse(accessToken, tokenType, expiresIn)`에도 `refreshToken, refreshExpiresIn`을 더하고 `from(result)`에서 옮긴다. `SignupResponse(memberId, accessToken, tokenType, expiresIn)`도 응답에 두 필드가 추가되므로(아래 API 표) 같은 필드와 `from`의 옮기기가 필요하다.

| API | 요청 | 응답 |
|---|---|---|
| `POST /api/auth/signup`, `POST /api/auth/login` | 그대로 | 응답에 `refreshToken`, `refreshExpiresIn` 추가 |
| `POST /api/auth/refresh` | `RefreshRequest(@NotBlank String refreshToken)` | 200 `TokenResponse` |
| `POST /api/auth/logout?all=false` | (로그인 필요) | 204 — `all=false`면 현재 세션(토큰의 `sid`), `true`면 전부(4-4에서 tokenVersion도 올림) |

`AuthController`(기존)에 더하거나 바꾸는 메서드

| 메서드 | 파라미터 | 하는 일 → 응답 |
|---|---|---|
| `signUp`·`login` (변경) | 기존 요청 바디 | 서비스가 `TokenIssuer.issueNewSession(member)`를 부른다 → 기존과 같은 응답에 refresh 두 필드 |
| `refresh` (`POST /refresh`) | `@Valid @RequestBody RefreshRequest request` | `tokenRefreshService.refresh(request.refreshToken())` → 200 `TokenResponse.from(result)` |
| `logout` (`POST /logout`) | `@AuthenticationPrincipal AuthClaims claims`, `@RequestParam(defaultValue = "false") boolean all` | `logoutService.logout(claims.memberId(), claims.sessionId(), all)` → `ResponseEntity.noContent().build()`(204) |

- `/api/auth/refresh`는 공개다. access가 이미 만료됐을 때 부르는 API라서 인증을 요구하면 안 된다. 현재 `SecurityConfig`의 `/api/auth/**` 허용에 이미 들어간다

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 이 단계에서 추가하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다. `TestFixtures`가 `StringRedisTemplate`을 주입받는다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `sessionSnapshotOf(UUID sessionId)` → `SessionSnapshot` | `redis.opsForHash().entries("auth:session:" + sessionId)`와 `redis.getExpire(key, TimeUnit.SECONDS)`(키가 없으면 -2)를 한 번에 읽는다. 해시의 키·값은 `Object`로 오므로 `Map<String, String>`으로 바꿔 담는다. `SessionSnapshot`은 `support/`의 record(`Map<String, String> fields`, `long ttlSeconds`)이고 `exists()`(`fields`가 비어 있지 않다), `memberId()`(`UUID`, 없으면 `null`), `refreshHash()`, `fieldNames()`, `values()`를 가진다 | 4-1 `TokenRefreshTest`·`RefreshConcurrencyTest`, 세션을 확인하는 4-2~4-4 테스트 |
| `memberSessionsTtlOf(UUID memberId)` → `long` | `redis.getExpire("auth:member-sessions:" + memberId, TimeUnit.SECONDS)`. 키가 없으면 -2 | 4-1 `TokenRefreshTest` 14번 |
| `sessionIdsOf(UUID memberId)` → `Set<UUID>` | `redis.opsForSet().members("auth:member-sessions:" + memberId)`를 `UUID`로 바꾼다. 키가 없으면 빈 집합(Redis는 마지막 원소를 빼면 집합 키를 지우므로 세션이 없을 때도 빈 집합이다) | 4-1 `TokenRefreshTest`·`RefreshConcurrencyTest`, 세션을 확인하는 4-2~4-4 테스트 |

이미 있는 헬퍼와 설정은 다음과 같다.
- `IntegrationTestSupport`: 이 단계에서 더한 Redis 컨테이너와 `@AfterEach`의 `flushDb()`.
- `TestFixtures`의 기존 `signup`·`login`(access 토큰만 돌려준다)은 그대로 쓴다. refresh가 필요하면 `TestFixtures`에 더한 `TokenResult loginTokens(String email)`(`loginService.login(new LoginCommand(email, "pass1234"))`의 결과를 그대로 돌려준다)로 `accessToken()`·`refreshToken()`을 얻는다. 갱신 요청은 테스트의 대상이라 MockMvc로 `POST /api/auth/refresh` 본문 `{"refreshToken":"..."}`를 부른다. 웹 테스트 케이스의 `refresh(x)`는 이 요청을 줄여 쓴 것이다.

헬퍼를 쓰는 방법은 다음과 같다.
- 응답: `jsonPath`. 에러 응답의 코드는 `$.code`. 토큰 응답의 필드는 `$.accessToken`, `$.tokenType`(`Bearer`), `$.expiresIn`(초), `$.refreshToken`, `$.refreshExpiresIn`(초)이다. 값이 없어야 하는 필드는 `jsonPath("$.refreshToken").doesNotExist()`(필드가 없거나 null이면 통과)로 확인한다.
- access 토큰 안의 값: `jwtProvider.parse(accessToken).orElseThrow()`가 돌려주는 `AuthClaims`의 `memberId()`·`role()`·`sessionId()`. 4-4부터 `tokenVersion()`이 더 생긴다.
- refresh 문자열 안의 값: `RefreshToken.parse(refresh)`의 `sessionId()`·`secret()`·`secretHash()`.
- Redis 세션: 전체 키 이름은 주입받은 `StringRedisTemplate redis`의 `redis.keys("*")`로 읽는다. 세션 내용과 회원의 세션 목록은 위 표의 헬퍼로 읽고, 키를 읽는 호출·`Map` 변환·캐스팅을 테스트에 쓰지 않는다.
- 시간 의존 값(세션 7일, 인증코드 5분 등)은 `Clock`을 바꾸지 않고 Redis 키를 지우거나(`redis.delete(key)`) 남은 시간을 줄여(`redis.expire(key, Duration.ofSeconds(1))`) 흉내 낸다. Redis 키 만료는 Part 2 공통 규칙 M의 "DB 시각 컬럼을 과거로 UPDATE"에 해당하는 방법이다.

**공통 준비**: 회원 1명 `member@test.com`(`testFixtures.signup`, 비밀번호 `pass1234`, 닉네임 `member`) → `memberId`. 로그인 요청은 `POST /api/auth/login` 본문 `{"email":"member@test.com","password":"pass1234"}`.

#### `member/domain/RefreshTokenTest` (Spring 없이)

| 케이스 | 동작 | 확인할 것 |
|---|---|---|
| 왕복 | `sessionId = UUID.randomUUID()` → `t = RefreshToken.generate(sessionId)` → `parsed = RefreshToken.parse(t.value())` | `t.value()` = `sessionId + "." + t.secret()` / `t.secret()`이 정규식 `^[A-Za-z0-9_-]{43}$`와 일치 / `parsed.sessionId()` = `sessionId`, `parsed.secret()` = `t.secret()` / `parsed.secretHash()` = `t.secretHash()`이고 `t.secretHash()` ≠ `t.secret()` / `generate(sessionId)`를 한 번 더 부르면 `secret`이 다르다 |
| 잘못된 문자열 | `@ParameterizedTest` 입력 `null`, `""`, `"abc"`, `"a.b.c"`, `"not-a-uuid.secret"`을 `RefreshToken.parse(입력)`에 넣는다 | 다섯 입력 모두 `BusinessException`, `errorCode` = `UNAUTHORIZED` |
| `toString` | `t = RefreshToken.generate(UUID.randomUUID())` → `t.toString()` | `t.secret()`을 포함하지 않고 `t.sessionId().toString()`을 포함한다 |

#### `member/web/TokenRefreshTest` (공통 준비 사용)

**1. 가입 응답에 refresh가 들어 있다** — 동작: `POST /api/auth/signup` 본문 `{"email":"new@test.com","password":"pass1234","nickname":"newbie","name":"테스터"}`.
- 응답: 201, `$.refreshExpiresIn` = 604800, `$.refreshToken`이 비어 있지 않다.
- `sid` = `RefreshToken.parse($.refreshToken).sessionId()`가 `parse($.accessToken).sessionId()`와 같다.
- `s = sessionSnapshotOf(sid)`: `s.memberId()` = `$.memberId`, `s.refreshHash()` = `RefreshToken.parse($.refreshToken).secretHash()`, `s.ttlSeconds()` 604000~604800.

**2. 갱신하면 새 refresh로 바뀐다** — 준비: `loginTokens("member@test.com")` → `access1`, `refresh1`, `sid`(= `refresh1`의 sessionId).
- 동작: `refresh(refresh1)`.
- 확인: 200. `$.refreshToken`(= `refresh2`) ≠ `refresh1`이고 `RefreshToken.parse(refresh2).sessionId()` = `sid`. `jwtProvider.parse($.accessToken).orElseThrow()`의 `sessionId()` = `sid`, `memberId()` = `memberId`, `role()` = `USER`. `sessionSnapshotOf(sid).refreshHash()` = `RefreshToken.parse(refresh2).secretHash()`.

**3. 이미 쓴 refresh가 다시 오면 세션을 폐기한다** — 준비: `loginTokens(…)` → `refresh1`, `sid`. `refresh(refresh1)` → 200.
- 동작: `refresh(refresh1)` 다시.
- 확인: 401, `$.code` = `REFRESH_REUSED`. `sessionSnapshotOf(sid).exists()` = false, `sessionIdsOf(memberId)`에 `sid`가 없다.

**4. 로그아웃(현재 기기만)** — 준비: `loginTokens(…)` → `accessA`·`refreshA`·`sidA`, `loginTokens(…)` → `refreshB`·`sidB`.
- 동작: `POST /api/auth/logout`, `Authorization: Bearer {accessA}`, 파라미터 없음.
- 확인: 204, 본문 없음. `sessionSnapshotOf(sidA).exists()` = false, `sessionIdsOf(memberId)`에 `sidA`는 없고 `sidB`는 있다. `sessionSnapshotOf(sidB).exists()` = true.

**5. 전체 로그아웃(`?all=true`)** — 준비: 다른 회원 `other@test.com`을 `signup` 후 `loginTokens("other@test.com")` → `sidOther`. 대상 회원은 `loginTokens(…)` → `accessA`·`refreshA`·`sidA`, `loginTokens(…)` → `refreshB`·`sidB`.
- 동작: `POST /api/auth/logout?all=true`, `Bearer {accessA}`.
- 확인: 204. `sessionSnapshotOf(sidA).exists()`·`sessionSnapshotOf(sidB).exists()` = false, `sessionIdsOf(memberId)`가 비어 있다. 다른 회원: `sessionSnapshotOf(sidOther).exists()` = true.

**6. Redis에 refresh 원문이 없다** — 준비: `loginTokens(…)` → `refresh1`, `secret1` = `RefreshToken.parse(refresh1).secret()`, `sid`. 동작: `refresh(refresh1)` → `refresh2`, `secret2` = `RefreshToken.parse(refresh2).secret()`.
- `redis.keys("*")`의 어떤 키 이름에도 `secret1`·`secret2`가 들어 있지 않다.
- `s = sessionSnapshotOf(sid)`: `s.fieldNames()`가 정확히 `memberId`, `refreshHash` 두 개이고, `s.values()`의 어떤 값도 `secret1`·`secret2`와 같거나 포함하지 않는다.

**7. 없는 세션의 refresh** — 준비: 공통 준비만 쓴다.
- `refresh(UUID.randomUUID() + ".xyz")` → 401, `$.code` = `UNAUTHORIZED`. 요청 전후 `redis.keys("*")` 집합이 같다.

**8. 정지 회원의 refresh** — 회원 `ban@test.com`(`banId`)을 `loginTokens(…)` → `refreshBan`·`sidBan`.
- `testFixtures.changeStatus(banId, "BANNED")` → `refresh(refreshBan)` → 403, `$.code` = `MEMBER_BANNED`. `sessionSnapshotOf(sidBan).exists()` = false.

**9. 같은 access로 로그아웃을 두 번 불러도 204** — 준비: 4번과 같다. 동작: `accessA`로 `POST /api/auth/logout`을 부르고(204), 같은 `accessA`로 다시 부른다.
- 확인: 두 번째도 204.

**10. 인증 없는 로그아웃은 거부한다** — 준비: 4번과 같다. 동작: `Authorization` 없이 `POST /api/auth/logout`.
- 확인: 401, `$.code` = `UNAUTHORIZED`. `sessionSnapshotOf(sidB).exists()` = true.

**11. refresh 요청 본문이 비어 있으면 400** — 준비: 7번과 같다.
- 본문 `{"refreshToken":""}` → 400, `$.code` = `INVALID_REQUEST`. 요청 전후 `redis.keys("*")` 집합이 같다.

**12. secret이 틀린 refresh는 재사용으로 보고 세션을 폐기한다** — 준비: `loginTokens(…)` → `sid`.
- `refresh(sid + "." + "x".repeat(43))` → 401, `$.code` = `REFRESH_REUSED`. `sessionSnapshotOf(sid).exists()` = false.

**13. 탈퇴 회원의 refresh** — 회원 `gone@test.com`(`goneId`)을 `loginTokens(…)` → `refreshGone`·`sidGone`.
- `testFixtures.changeStatus(goneId, "WITHDRAWN")` → `refresh(refreshGone)` → 401, `$.code` = `LOGIN_FAILED`. `sessionSnapshotOf(sidGone).exists()` = false.

**14. 회전이 회원의 세션 목록 만료도 연장한다** — 준비: `loginTokens(…)` → `refresh1`. `redis.expire("auth:member-sessions:" + memberId, Duration.ofSeconds(100))`로 목록의 남은 시간을 줄여 둔다.
- 동작: `refresh(refresh1)` → 200.
- 확인: `memberSessionsTtlOf(memberId)`가 604000~604800이다.

**15. 로그인할 때마다 새 세션이 생긴다** — 동작: 공통 준비의 로그인 요청.
- 응답: 200, `$.refreshToken`이 비어 있지 않다. `sid` = `RefreshToken.parse($.refreshToken).sessionId()`.
- `sessionIdsOf(memberId)`가 2개이고 `sid`가 들어 있다. `sessionSnapshotOf(sid).memberId()` = `memberId`.

**16. 갱신하면 세션 만료가 7일로 다시 늘어난다** — 준비: `loginTokens(…)` → `refresh1`, `sid`. `redis.expire("auth:session:" + sid, Duration.ofSeconds(100))`로 세션의 남은 시간을 줄여 둔다.
- 동작: `refresh(refresh1)` → 200.
- 확인: `sessionSnapshotOf(sid).ttlSeconds()` 604000~604800.

#### `member/application/RefreshConcurrencyTest` (공통 준비 사용, `@RepeatedTest(10)`)

준비: `testFixtures.loginTokens(…)`로 `refresh1`·`sid`를 얻는다. 동작: `ConcurrencyRunner.run(2, () -> tokenRefreshService.refresh(refresh1))`. `ConcurrencyRunner.run(int threads, Callable<T> task)`는 스레드마다 결과 하나씩 `List<ConcurrencyRunner.Result<T>>`로 돌려준다. 각 `Result`는 `value()`(`TokenResult`), `error()`(`Throwable`, 여기서는 `BusinessException`), `ok()`(`error() == null`)로 읽는다.
- 두 결과 중 정확히 하나는 `ok()`이고 `value().refreshToken()`이 `refresh1`과 다르다.
- 나머지 하나는 `BusinessException`이고 `errorCode` = `REFRESH_REUSED`다.
- `sessionSnapshotOf(sid).exists()` = false.

**추가 항목**
- 기기 목록: 세션에 `device`(`User-Agent`를 200자로 자른 값)를 저장해 내 로그인 기기 목록을 보여 주고 원격 로그아웃을 지원하기. 바꿀 곳: `auth:session` 해시에 `device` 필드(없으면 뺀다), `SessionStore.create`·`TokenIssuer.issueNewSession`·`SignupService.signUp`·`LoginService.login`·4-3 `OAuthLoginService.login`/`signup`에 `device` 인자(요청 본문과 별개의 인자), 컨트롤러가 `User-Agent` 헤더를 200자로 잘라 전달, 세션 목록 조회·원격 로그아웃 API

**리뷰 때 물어볼 것**
- refresh를 원문이 아니라 해시로 저장하는 이유는?
- JWT는 무상태인데 왜 Redis를 쓰나?
- `rotate`를 GET → 비교 → SET 세 번의 명령으로 하면 어떤 경합이 생기나?

---

## 4-2. 이메일 인증 · 비밀번호 변경·재설정 (M)

**목표**: 이메일로 가입할 때 인증코드로 이메일 소유를 확인해야 가입된다. 비밀번호 변경과 재설정을 지원한다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 발송 제한 | 같은 이메일로 인증코드를 너무 자주 보내지 못하게 막는다(`EmailCodeStore.checkSendLimit`, 횟수는 아래 정책) |
| 시도 제한 | 코드를 계속 찍어 맞히지 못하게 여러 번 틀리면 코드를 지운다(`EmailCodeStore.verify`, 횟수는 아래 정책) |
| 인증 토큰 | 코드 확인과 가입은 서로 다른 요청이라, 확인을 통과한 사실을 가입 요청까지 이어 줄 값이 필요하다. 확인에 성공하면 랜덤 토큰을 돌려주고 서버는 `토큰 → 이메일`을 기억한다. 가입 요청은 이 토큰을 함께 보낸다. 이메일만 기억하면 확인을 통과한 직후 다른 사람이 그 이메일로 먼저 가입할 수 있어서, 확인을 통과한 브라우저만 가진 토큰으로 묶는다 |

**정책 (이 단계에서 정함)**
- 이메일 가입은 발송 → 확인 → 가입 순서다. 인증을 통과하지 않으면 회원이 만들어지지 않으므로 모든 회원은 인증된 이메일을 가진다(인증 여부를 따로 저장하거나 기능마다 확인하지 않는다).
- 인증코드 6자리 숫자(`SecureRandom`), 유효 5분. 목적(purpose): `VERIFY_EMAIL`(가입), `RESET_PASSWORD`.
- 발송: 같은 이메일로 60초에 1번, 시간당 5번(초과 시 429). 확인: 5번 틀리면 코드 무효(새로 발송해야 함).
- 인증 토큰은 30분 유효하고 가입이 성공했을 때만 지운다. 닉네임 중복 등으로 가입이 실패하면 같은 토큰으로 다시 가입할 수 있다.
- 이미 가입된 이메일로 가입 인증코드를 요청하면 409 `MEMBER_EMAIL_DUPLICATED`. 가입 API가 이미 같은 409로 가입 여부를 알려 주므로 여기서 숨기지 않는다.
- 비밀번호 재설정 요청은 **가입 여부와 상관없이 같은 응답**(가입된 이메일을 알아낼 수 없게). 발송 제한(쿨다운·시간당)도 가입 여부와 무관하게 이메일 기준으로 적용해서, 한도를 넘으면 가입 여부와 상관없이 429고 그 밖에는 202다. 가입 인증코드 발송이 이미 가입된 이메일에 409를 주므로 가입 여부는 그쪽으로 알 수 있다. 가입은 사용자에게 알려 주는 편이 낫지만 재설정은 알려 주지 않아도 사용자가 잃는 것이 없어서 숨긴다.
- 비밀번호 변경·재설정 후: 변경은 현재 세션만 남기고 나머지 세션 폐기, 재설정은 전부 폐기. (4-4에서 tokenVersion도 올린다)

### 할 일

**구현 순서**: ① common.error(1): `CommonErrorCode` → ② common.mail(2): 의존성·`SmtpMailSender`·`LogMailSender` 프로필·`prod` 설정 → ③ member.domain(3): `Member`·`MemberErrorCode` → `EmailCodePurpose` → `EmailCodeStore` → `SignupVerificationStore` → ④ member.infrastructure(4): `verify.lua` → `RedisEmailCodeStore`·`RedisSignupVerificationStore` → ⑤ member.application(5): `SignupVerificationService` → `SignupService` 변경 → `PasswordService` → ⑥ member.web(6) → ⑦ `TestFixtures` 변경(완료 확인의 테스트 준비 표) → 테스트. `SignupCommand`에 인자가 늘어 `TestFixtures.signup`과 ⑤의 `SignupService`를 함께 고쳐야 컴파일된다.

**1) common.error**

| 클래스 | 규격 |
|---|---|
| `CommonErrorCode` | `RATE_LIMITED`(429)를 추가한다(공통 규칙 U) |

**2) common.mail**

3-5의 `common.mail.MailSender`(`void send(String to, String title, String body)`, 실패 시 예외)를 쓴다. 발송은 트랜잭션 밖에서 한다 — 메일 서버를 기다리는 동안 DB 커넥션을 잡고 있지 않도록, 발송하는 서비스 메서드에는 `@Transactional`을 붙이지 않는다(공통 규칙 K와 같은 이유).

이 단계부터 사용자가 인증코드를 메일로 받아야 가입할 수 있으므로, `prod`(운영 VM)에서는 Gmail SMTP로 실제로 보낸다. 3-5 알림 메일도 같은 포트를 쓰므로 함께 실제로 나간다.

| 클래스·파일 | 규격 |
|---|---|
| `build.gradle` | `implementation 'org.springframework.boot:spring-boot-starter-mail'`(버전은 Boot BOM). `spring.mail.host`가 있으면 Boot가 `JavaMailSender` 빈을 만든다 |
| `SmtpMailSender` | `@Component @Profile("prod")`, `implements MailSender`. 의존성: `JavaMailSender`, 보내는 주소 `@Value("${spring.mail.username}") String from`. `send`는 `SimpleMailMessage`에 `setFrom(from)`·`setTo(to)`·`setSubject(title)`·`setText(body)`를 넣고 `javaMailSender.send(message)`. 실패하면 `MailException`(런타임 예외)이 그대로 나간다. 잡지 않는다 |
| `LogMailSender` | `@Profile("!prod")`를 붙인다. 3-4에서 뺀 이유(`prod`에 `MailSender` 빈이 없으면 기동 실패)는 `SmtpMailSender`가 생겨 사라졌다. 테스트(`test` 프로필)는 지금처럼 `FakeMailSender`(`@Primary`)가 주입된다 |
| `application-prod.yaml` | 아래 `spring.mail` 설정 |
| `.env.ops.example` | `MAIL_USERNAME=`, `MAIL_PASSWORD=`를 더한다 |

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.starttls.required: true
      mail.smtp.connectiontimeout: 5000
      mail.smtp.timeout: 5000
      mail.smtp.writetimeout: 5000
```

- 타임아웃 세 줄은 빼지 않는다. JavaMail의 기본값은 무한 대기라서 SMTP 서버가 응답하지 않으면 가입 요청 스레드와 3-5 알림 발송 잡이 멈춘 채로 남는다.
- 587 + STARTTLS를 쓴다. `starttls.required`가 없으면 TLS 협상에 실패했을 때 평문으로 계속 보낼 수 있다.
- Gmail 계정은 개인 계정이 아니라 발송 전용 계정을 새로 만든다. 2단계 인증을 켠 뒤 Google 계정 → 보안 → 앱 비밀번호에서 만든 16자리를 공백 없이 `MAIL_PASSWORD`에 넣는다(일반 비밀번호로는 로그인되지 않는다). 값은 VM의 `/opt/myroutine/.env`에만 두고 리포·채팅에 붙이지 않는다([vm-setup 4절](../ops/vm-setup.md)).
- 일반 Gmail 계정의 하루 발송 한도는 약 500통이다(확인 필요: Google 고객센터 "Gmail 전송 한도"). 넘으면 그날은 `MailException`이 나고, 알림은 3-5 재시도로, 인증코드는 사용자가 다시 요청하는 것으로 처리된다.
- `local`은 그대로 `LogMailSender`라 인증코드는 앱 콘솔 로그에서 찾는다.

**발송이 실패하면** — 가입 인증 발송(`SignupVerificationService.send`)은 예외를 잡지 않아 500 `INTERNAL_ERROR`가 된다. 코드 저장과 발송 한도는 이미 반영됐으므로 사용자는 60초 뒤 다시 요청한다. 비밀번호 재설정 요청(`requestReset`)은 예외를 잡아 `log.error`로 남기고 202를 돌려준다. 가입된 이메일에만 메일을 보내므로, 실패를 500으로 드러내면 그 응답으로 가입 여부가 드러나기 때문이다.

**3) member.domain**

| 클래스 | 규격 |
|---|---|
| `Member` | 메서드 `Member.changePassword(String encodedPassword)`: 이미 `passwordEncoder.encode`한 해시를 받아 `passwordHash`에 넣는다(인코딩은 서비스가 한다) |
| `MemberErrorCode` | `EMAIL_CODE_INVALID`(400)·`VERIFICATION_TOKEN_INVALID`(400)·`PASSWORD_MISMATCH`(400)를 추가한다(공통 규칙 U) |
| `EmailCodePurpose` | enum `VERIFY_EMAIL, RESET_PASSWORD` — 코드의 용도. 같은 이메일로 가입용·재설정용 코드가 따로 살도록 Redis 키(`auth:email-code:{purpose}:{email}`)에 들어간다. |
| `EmailCodeStore` | 인터페이스 — 인증코드와 **발송 제한**을 Redis에 두는 저장소. `SignupVerificationService`·`PasswordService`가 쓴다. `email`은 호출한 쪽이 `Member.normalizeEmail`로 정규화한 값이다. 메서드는 아래 표 |
| `SignupVerificationStore` | 인터페이스 — 코드 확인을 통과한 이메일을 가입 요청까지 기억하는 저장소(인증 토큰). `SignupVerificationService`가 만들고 `SignupService`와 4-3 `OAuthLoginService.signup`이 확인·삭제한다. 메서드는 아래 표 |

`EmailCodeStore` 메서드

| 메서드 | 하는 일 | 입력 → 반환 |
|---|---|---|
| `void checkSendLimit(String email)` | 지금 보내도 되는지 확인한다. 안 되면 예외 | 위반이면 429 `RATE_LIMITED`. ① `redis.opsForValue().setIfAbsent("auth:email-cooldown:{email}", "1", Duration.ofSeconds(60))`(Redis `SET … NX EX 60`)가 `false`면 60초 안에 이미 보낸 것이므로 위반 ② `redis.opsForValue().increment("auth:email-hourly:{email}")` 결과가 1이면 `redis.expire(…, Duration.ofHours(1))`을 건다. 5를 넘으면 위반. 위반한 키의 남은 시간(`getExpire(key, TimeUnit.SECONDS)`, 그 사이 만료돼 1보다 작으면 1)을 `new BusinessException(RATE_LIMITED, Map.of("retryAfterSeconds", 초))`로 담는다. 사용자가 "잠시 후"가 1분인지 1시간인지 알 수 있게 하려는 것이다. 재설정 요청도 가입 여부와 상관없이 같은 키를 보므로 이 값으로 가입 여부가 드러나지 않는다 |
| `void save(EmailCodePurpose p, String email, String code)` | 새 코드를 저장한다. **원문 `code`를 받아 안에서 SHA-256으로 해시한다**. 이전 코드는 덮어쓰고 `attempts`는 0으로 돌아간다 | `auth:email-code:{p}:{email}` 해시에 해시값을 `codeHash`로, `attempts = 0`을 쓰고 `expire` 5분 |
| `boolean verify(EmailCodePurpose p, String email, String code)` | 입력한 코드가 맞는지 확인한다. `code`는 `save`처럼 원문으로 받는다 | 입력을 SHA-256으로 해시해 아래 `verify.lua`에 넘기고, 결과가 `1L`이면 `true`. 스크립트 안에서 ① `codeHash`가 없으면(없음·만료·5번 틀려 삭제됨) `false` ② 저장된 `codeHash`와 같으면 키를 지우고 `true` ③ 다르면 `HINCRBY attempts 1`, 결과가 5 이상이면 키를 지우고 `false` |

- 코드 만들기: `String.format("%06d", secureRandom.nextInt(1_000_000))` — `000123`처럼 앞이 0이어도 6자리를 지킨다

`SignupVerificationStore` 메서드

| 메서드 | 하는 일 | 입력 → 반환 |
|---|---|---|
| `String issue(String email)` | 확인을 통과한 이메일에 인증 토큰을 만든다 | 추측할 수 없는 토큰(`SecureRandom` 32바이트를 Base64URL로 인코딩. 4-1 `RefreshToken.generate`의 `secret`과 같은 방식)을 만들어 `set("auth:signup-verified:" + 해시(토큰), email, 30분)` → 토큰(원문은 돌려주기만 하고 저장하지 않는다) |
| `Optional<String> findEmail(String token)` | 토큰이 가리키는 이메일 | `get("auth:signup-verified:" + 해시(token))` 결과가 `null`(없음·만료·이미 가입에 씀)이면 `Optional.empty()` |
| `void delete(String token)` | 가입이 끝났으니 지운다 | `delete("auth:signup-verified:" + 해시(token))` |

**4) member.infrastructure**

| 클래스 | 규격 |
|---|---|
| `RedisEmailCodeStore` | `EmailCodeStore` 구현(Redis). 의존성은 `StringRedisTemplate`, `verify.lua`를 담은 `DefaultRedisScript<Long>`이다. 스크립트 객체는 4-1 `RedisSessionStore`처럼 생성자 안에서 만든다(그래서 `@RequiredArgsConstructor` 대신 생성자를 직접 쓴다) |
| `RedisSignupVerificationStore` | `SignupVerificationStore` 구현(Redis) |

`verify.lua` — `EmailCodeStore.verify`의 읽기·비교·시도 횟수 증가를 **Redis 안에서 한 번에** 실행한다. 자바에서 `entries` → 비교 → `HINCRBY`를 따로 하면, 같은 코드에 틀린 추측을 동시에 보냈을 때 5번째 삭제 전에 해시를 읽어 간 요청이 모두 비교를 받는다(동시 50개면 50번). 재설정 코드는 로그인 없이 요청할 수 있어서 이 차이가 곧 남의 계정 비밀번호를 맞힐 기회다. Redis는 스크립트를 하나씩 실행하므로 비교는 코드 하나에 5번까지만 일어난다. 위치는 `src/main/resources/redis/verify.lua`.
```lua
-- KEYS[1] = auth:email-code:{purpose}:{email}, ARGV[1] = 입력 코드의 해시
local stored = redis.call('HGET', KEYS[1], 'codeHash')
if not stored then
  return 0
end
if stored == ARGV[1] then
  redis.call('DEL', KEYS[1])
  return 1
end
if redis.call('HINCRBY', KEYS[1], 'attempts', 1) >= 5 then
  redis.call('DEL', KEYS[1])
end
return 0
```

- 자바에서 부르는 형태: `Long r = redis.execute(verifyScript, List.of("auth:email-code:" + p + ":" + email), 해시(code));` → `r`이 `1L`이면 `true`. `verifyScript`는 `DefaultRedisScript<Long>`에 `setLocation(new ClassPathResource("redis/verify.lua"))`, `setResultType(Long.class)`를 한 것이다
- 없는 필드의 `HGET`은 Lua에서 `false`라 `not stored`로 확인한다. 스크립트가 도는 동안 키가 만료되지 않으므로, 자바에서 나눠 실행할 때 생기던 "만료 직후 `HINCRBY`가 TTL 없는 키를 만드는" 경우도 없다
- 해시 비교가 `MessageDigest.isEqual`이 아니라 Redis 안의 `==`에서 일어난다. 4-1 `rotate.lua`와 같은 이유로 공통 규칙 T의 예외다(둘 다 SHA-256 해시라 비교 시간으로 값을 알아낼 수 없다)

**5) member.application**

가입 흐름: `send`가 코드를 만들어 해시만 Redis에 저장하고 메일로 원문을 보낸다 → 사용자가 코드를 입력하면 `confirm`이 해시를 비교하고 맞으면 인증 토큰을 돌려준다 → 가입 요청이 인증 토큰을 보내면 `signUp`이 토큰의 이메일과 가입 이메일이 같은지 확인하고 회원을 만든다.

| 클래스 | 메서드 |
|---|---|
| `SignupVerificationService` | `void send(String email)` / `String confirm(String email, String code)` |
| `SignupService` | `signUp` 변경(아래) |
| `PasswordService` | `@Transactional TokenResult change(UUID memberId, UUID sessionId, String current, String next)` / `void requestReset(String email)` / `@Transactional void confirmReset(String email, String code, String newPassword)` |

`SignupVerificationService` (`@Service`) — **가입 전 이메일 인증 유스케이스.** 로그인 전 요청이다. 의존성: `MemberRepository`, `EmailCodeStore`, `SignupVerificationStore`, `MailSender`. 두 메서드 모두 메일 발송이나 Redis만 쓰므로 `@Transactional`을 붙이지 않는다.

`send` — 순서
1. `email = Member.normalizeEmail(email)`
2. `memberRepository.existsByEmail(email)`이면 409 `MEMBER_EMAIL_DUPLICATED`(발송 제한 확인보다 먼저 막는다)
3. `emailCodeStore.checkSendLimit(email)`
4. 코드 생성 → `emailCodeStore.save(VERIFY_EMAIL, email, code)`
5. `mailSender.send(email, "[MyRoutine] 이메일 인증코드", "인증코드: " + code)`

`confirm` — 순서
1. `email = Member.normalizeEmail(email)`
2. `emailCodeStore.verify(VERIFY_EMAIL, email, code)`가 `false`면 400 `EMAIL_CODE_INVALID`
3. `return signupVerificationStore.issue(email)` — 인증 토큰

**`SignupService.signUp` 변경** — `SignupCommand`에 `String verificationToken`을 더하고, 의존성에 `SignupVerificationStore`를 더한다. 순서
1. `email = Member.normalizeEmail(command.email())`
2. `signupVerificationStore.findEmail(command.verificationToken())`가 비었거나 `email`과 다르면 400 `VERIFICATION_TOKEN_INVALID`
3. 기존 이메일·닉네임 중복 확인(409), 회원 저장, `member-registered` 발행은 그대로
4. `signupVerificationStore.delete(command.verificationToken())`
5. `tokenIssuer.issueNewSession(savedMember)`(4-1)

- 4번의 Redis 삭제는 DB 롤백으로 되돌아가지 않는다. 같은 이메일로 동시에 가입해 커밋 때 unique 제약에 걸린 쪽은 토큰도 잃지만, 그 이메일은 이미 가입됐으므로 다시 쓸 일이 없다

`PasswordService` (`@Service`) — **로그인한 회원의 비밀번호 변경과 이메일 코드로 하는 재설정**을 맡는다. 의존성: `MemberRepository`, `PasswordEncoder`, `SessionStore`, `EmailCodeStore`, `MailSender`, `TokenIssuer`

`change` — `PATCH /api/members/me/password`가 부른다. `sessionId`는 `claims.sessionId()`. 순서
1. `memberRepository.findById(memberId)`(없으면 404 `MEMBER_NOT_FOUND`) → `passwordHash`가 `null`(소셜 가입 회원)이거나 `passwordEncoder.matches(current, passwordHash)`가 `false`면 400 `PASSWORD_MISMATCH`
2. `member.changePassword(passwordEncoder.encode(next))`
3. `sessionStore.deleteAllOfExcept(memberId, sessionId)` — 다른 기기의 refresh를 모두 끊고 지금 기기만 남긴다
4. `return tokenIssuer.reissueAccess(member, sessionId)` — 현재 세션용 새 access를 돌려준다(4-4 이후에는 tokenVersion이 올라 기존 access가 막히므로)

`requestReset` — `POST /api/auth/password-reset`이 부른다. 트랜잭션 없음(메일 발송). 순서
1. `Member.normalizeEmail(email)` → `emailCodeStore.checkSendLimit(email)`(한도 위반이면 429 `RATE_LIMITED`. 가입 여부를 보기 전에 하므로 가입하지 않은 이메일도 같은 한도를 받는다)
2. `findByEmail`로 회원이 있고 `ACTIVE`면 코드 생성 → `save(RESET_PASSWORD, email, code)` → `mailSender.send(email, "[MyRoutine] 비밀번호 재설정 인증코드", "인증코드: " + code)`. `send`가 던진 예외는 잡아서 `log.error`로 남기고 정상 반환한다(위 "발송이 실패하면")
3. 없거나 `ACTIVE`가 아니면 메일만 보내지 않는다(응답은 같다)

`confirmReset` — `POST /api/auth/password-reset/confirm`이 부른다. 순서
1. `emailCodeStore.verify(RESET_PASSWORD, normalizedEmail, code)`가 `false`면 400 `EMAIL_CODE_INVALID`
2. `findByEmail`로 회원을 읽어 `member.changePassword(passwordEncoder.encode(newPassword))` (코드 확인은 통과했는데 회원이 없으면 400 `EMAIL_CODE_INVALID`다. 코드를 맞힌 사실로 가입 여부를 알리지 않는다)
3. `sessionStore.deleteAllOf(memberId)` — 재설정은 모든 세션을 끊는다
- 재설정은 로그인하지 않은 요청이라 토큰을 돌려주지 않는다(204). 사용자는 새 비밀번호로 다시 로그인한다

**6) member.web**

| API | 권한 | 요청 | 응답 |
|---|---|---|---|
| `POST /api/auth/signup/email-verifications` | 공개 | `SignupEmailRequest(@NotBlank @Email String email)` | 202 |
| `POST /api/auth/signup/email-verifications/confirm` | 공개 | `SignupEmailConfirmRequest(@NotBlank @Email String email, @NotBlank @Pattern(regexp="^\\d{6}$") String code)` | 200 `SignupVerificationResponse(String verificationToken)` |
| `POST /api/auth/signup` (변경) | 공개 | `SignupRequest`에 `@NotBlank String verificationToken`을 더하고 `toCommand()`로 옮긴다 | 201 `SignupResponse` 그대로 |
| `PATCH /api/members/me/password` | 로그인 | `ChangePasswordRequest(@NotBlank String currentPassword, String newPassword)`. `newPassword`는 가입과 같은 규칙(`@NotBlank @Size(min = 8, max = 64) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$")`, 메시지 "비밀번호는 8~64자, 영문과 숫자를 포함해야 합니다.") | 200 `TokenResponse` |
| `POST /api/auth/password-reset` | 공개 | `PasswordResetRequest(@NotBlank @Email String email)` | 202 (한도 초과는 429) |
| `POST /api/auth/password-reset/confirm` | 공개 | `PasswordResetConfirmRequest(@NotBlank @Email String email, @NotBlank @Pattern(regexp="^\\d{6}$") String code, String newPassword)`. `newPassword`는 비밀번호 변경과 같이 가입 규칙(`@NotBlank @Size(min = 8, max = 64) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$")`, 메시지 "비밀번호는 8~64자, 영문과 숫자를 포함해야 합니다.")을 따른다 | 204 |

- 공개 API는 모두 `SecurityConfig`의 `/api/auth/**` 허용 안에 있다. 비밀번호 변경은 `/api/members/**`라 이미 로그인이 필요하다
- 비밀번호 변경은 `sessionId`가 필요해 `@AuthenticationPrincipal AuthClaims claims`로 받는다
- 컨트롤러: 가입 인증 두 API와 재설정 두 API는 `AuthController`(`/api/auth`)에, 비밀번호 변경은 `MemberController`(`/api/members`)에 `@PatchMapping("/me/password")`로 더한다. 각 메서드는 위 서비스를 호출한다(`send`·`confirm`·`change`·`requestReset`·`confirmReset`)
- `new SignupRequest(…)`로 `/api/auth/signup`을 직접 부르는 기존 테스트(`AuthControllerTest`, 4-1 `TokenRefreshTest` 1번, 3-6 `WithdrawTest`, `support/TableCleanupTest`)는 본문에 `verificationToken`(아래 `testFixtures.verificationToken(email)`)을 더한다

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. refresh가 필요한 토큰은 4-1의 `testFixtures.loginTokens(email)`로 얻는다. 이 단계에서 더하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `verificationToken(String email)` → `String` | `signupVerificationStore.issue(Member.normalizeEmail(email))` — 메일·발송 제한을 거치지 않고 인증 토큰을 만든다 | `/api/auth/signup`·4-3 `/api/auth/oauth/signup`을 직접 부르는 테스트 |
| `signup(String email)` → `UUID`(변경) | `verificationToken(email)`을 `SignupCommand`에 넣어 기존처럼 `signupService.signUp`을 부른다 | 기존 테스트 전부(호출 모양은 그대로) |
| `emailAuthSnapshotOf(EmailCodePurpose purpose, String email)` → `EmailAuthSnapshot` | 한 이메일의 Redis 키 셋을 한 번에 읽어 아래 record에 담는다. 코드 키 `auth:email-code:{purpose}:{email}`는 `redis.opsForHash().entries(key)`를 `Map<String, String>`으로 바꾼 값, 쿨다운 키 `auth:email-cooldown:{email}`은 `redis.opsForValue().get(key)` 그대로, 시간당 키 `auth:email-hourly:{email}`은 `get(key)`를 `Integer.valueOf`(없으면 `null`)로 바꾼 값을 넣는다. 세 키 모두 남은 시간은 `redis.getExpire(key, TimeUnit.SECONDS)`이고 없는 키는 -2 | `SignupVerificationTest`, `PasswordTest` |
| `redisStringOf(String key)` → `RedisString` | `redis.opsForValue().get(key)`와 `redis.getExpire(key, TimeUnit.SECONDS)`. `support/`의 record(`String value`, `long ttlSeconds`)이고 `exists()`(`value != null`)를 가진다. 키가 없으면 `value`는 `null`, 남은 시간은 -2 | `SignupVerificationTest`의 `auth:signup-verified:*`, 4-3 `OAuthLoginTest`, 4-4 `LoginLockTest` |
| `resetRedisStats()`, `redisCallsOf(String command)` → `long` | `resetRedisStats`는 `redis.execute((RedisCallback<Void>) c -> { c.serverCommands().resetConfigStats(); return null; })`(Redis `CONFIG RESETSTAT`). `redisCallsOf`는 `c.serverCommands().info("commandstats")`가 돌려준 `Properties`에서 `cmdstat_{command}`(예: `cmdstat_hincrby`) 값 `calls=5,usec=…,rejected_calls=0,failed_calls=0`의 맨 앞 숫자를 꺼낸다(정규식 `^calls=(\d+),`. `.*calls=`로 쓰면 뒤의 `failed_calls=`를 잡는다). 항목이 없으면 0. Lua 스크립트 안의 `redis.call`도 센다 | `EmailCodeConcurrencyTest` |
| `static String sha256Hex(String value)` | `MessageDigest.getInstance("SHA-256")`로 UTF-8 바이트를 해시해 `HexFormat.of().formatHex`. 운영 코드의 해시와 같은 형식이어야 한다 | 토큰 키 이름 만들기: `"auth:signup-verified:" + sha256Hex(V)`, 4-3 `"auth:oauth-signup:" + sha256Hex(T)` |

`EmailAuthSnapshot`(`support/`)은 `SessionSnapshot`처럼 해시 전체를 맵으로 들고, 해시 필드는 메서드로 꺼낸다.

| 이름 | 타입 | 종류 | 값 |
|---|---|---|---|
| `codeFields` | `Map<String, String>` | record 필드 | 코드 키의 해시 전체. 키가 없으면 빈 맵 |
| `codeTtlSeconds` | `long` | record 필드 | 코드 키의 남은 시간 |
| `cooldown` | `String` | record 필드 | 쿨다운 키의 값(`"1"`). 없으면 `null` |
| `cooldownTtlSeconds` | `long` | record 필드 | 쿨다운 키의 남은 시간 |
| `hourly` | `Integer` | record 필드 | 시간당 발송 횟수. 없으면 `null` |
| `hourlyTtlSeconds` | `long` | record 필드 | 시간당 키의 남은 시간 |
| `codeExists()` | `boolean` | 메서드 | `!codeFields.isEmpty()` |
| `codeHash()` | `String` | 메서드 | `codeFields.get("codeHash")`. 없으면 `null` |
| `attempts()` | `Integer` | 메서드 | `codeFields.get("attempts")`를 `Integer.valueOf`. 없으면 `null` |
| `fieldNames()` | `Set<String>` | 메서드 | `Set.copyOf(codeFields.keySet())` |

이미 있는 헬퍼(`TestFixtures`)는 다음과 같다.
- `SessionSnapshot sessionSnapshotOf(UUID sessionId)`: 세션 해시와 남은 시간을 한 번에 읽는다. 접근자는 `exists()`, `memberId()`, `refreshHash()`, `fieldNames()`, `ttlSeconds()`.
- `Set<UUID> sessionIdsOf(UUID memberId)`: 회원의 세션 ID 집합. 세션이 없으면 빈 집합.
- `MemberSnapshot memberSnapshotOf(UUID memberId)`: 회원 행을 한 번에 읽는다. 필드는 `nickname`, `name`, `phone`, `passwordHash`, `role`, `status`, `email`, `createdAt`, `updatedAt`(`role`·`status`는 문자열).
- `long countOf(String table)`: `SELECT COUNT(*) FROM {table}`. 테이블은 `member.member`처럼 스키마를 붙여 쓴다.

헬퍼를 쓰는 방법은 다음과 같다.
- 메일: `FakeMailSender`(3-5)의 `sent()` — `SentMail(to, title, body)` 목록. 인증코드는 `body`에서 정규식 `\d{6}`의 첫 일치로 꺼낸다(`codeOf(mail)`, 테스트 클래스의 private 메서드). 가입 인증 메일은 제목 `[MyRoutine] 이메일 인증코드`, 재설정 메일은 제목 `[MyRoutine] 비밀번호 재설정 인증코드`이고 본문은 둘 다 `인증코드: ` + 6자리 코드다. `FakeMailSender`는 `@Primary` 빈이라 별도 등록이 없다.
- 회원: 이 단계에서 `memberSnapshotOf(memberId)`의 `passwordHash()`(소셜 가입이면 `null`)·`status()`·`email()`을 쓴다. 비밀번호 확인은 `passwordEncoder.matches(평문, memberSnapshotOf(memberId).passwordHash())`.
- 인증코드와 발송 제한: `emailAuthSnapshotOf`에 넘기는 `email`은 정규화한 값이다. 키 이름 검색은 `redis.keys("auth:email-*")`.
- 시간 의존: 5분 경과는 `redis.delete(코드 키)`, 60초 경과는 `redis.delete(쿨다운 키)`로 흉내 낸다.

#### `member/web/SignupVerificationTest`

**공통 준비**: 가입할 이메일은 `new@test.com`. 발송은 `POST /api/auth/signup/email-verifications` 본문 `{"email":"new@test.com"}`, 확인은 `POST /api/auth/signup/email-verifications/confirm` 본문 `{"email":"new@test.com","code":"{code}"}`, 가입은 `POST /api/auth/signup` 본문 `{"email":"new@test.com","password":"pass1234","nickname":"newbie","name":"테스터","verificationToken":"{V}"}` 형식이다. 각 케이스 끝에 메일은 `@AfterEach`에서 비운다(`FakeMailSender.reset()`).

**1. 발송** — 동작: 발송.
- 응답: 202.
- 메일: `sent()` 1건, `to` = `new@test.com`, `title` = `[MyRoutine] 이메일 인증코드`, `body`가 `인증코드: ` + 6자리 숫자.
- `a = emailAuthSnapshotOf(VERIFY_EMAIL, "new@test.com")`: `a.codeTtlSeconds()` 1~300. `a.cooldownTtlSeconds()` 1~60. `a.hourly()` = 1, `a.hourlyTtlSeconds()` 1~3600.

**2. 확인하면 인증 토큰을 준다** — 준비: 발송 → 메일에서 `code`. 동작: 확인.
- 응답: 200, `$.verificationToken`이 비어 있지 않다(`V`).
- `redisStringOf("auth:signup-verified:" + sha256Hex(V))`: `value()` = `new@test.com`, `ttlSeconds()` 1~1800.
- `emailAuthSnapshotOf(VERIFY_EMAIL, "new@test.com").codeExists()` = false.

**3. 인증 토큰으로 가입한다** — 준비: 2번의 동작까지 → `V`. 동작: 가입.
- 응답: 201.
- `memberSnapshotOf($.memberId).email()` = `new@test.com`. `redisStringOf("auth:signup-verified:" + sha256Hex(V)).exists()` = false.

**4. 60초 안에 재발송** — 동작: 발송 → 202, 곧바로 발송.
- 두 번째 응답: 429, `$.code` = `RATE_LIMITED`, `$.details.retryAfterSeconds` 1~60.
- 메일은 1건 그대로. 첫 발송 직후 `before = emailAuthSnapshotOf(VERIFY_EMAIL, "new@test.com")`를 읽어 두고, 두 번째 응답 뒤 읽은 값이 `codeHash()` = `before.codeHash()`, `hourly()` = 1이다.

**5. 시간당 5번 초과** — 동작: 발송(202) → `redis.delete("auth:email-cooldown:new@test.com")`를 5번 반복한 뒤 6번째 발송.
- 앞의 5번은 모두 202, 메일 5건. 5번째 직후 `emailAuthSnapshotOf(VERIFY_EMAIL, "new@test.com").hourly()` = 5.
- 6번째 응답: 429, `$.code` = `RATE_LIMITED`, `$.details.retryAfterSeconds` 3500~3600. 메일은 5건 그대로, `hourly()` = 6.

**6. 5번 틀리면 맞는 코드도 거부한다** — 준비: 발송 → `code`. 틀린 코드는 `String wrong = code.equals("000000") ? "111111" : "000000";`. 코드가 무작위라 고정값 하나만 쓰면 드물게(100만 분의 1) 맞는 코드와 같아지기 때문이다.
- 동작: 확인을 `wrong`으로 5번 호출한다. 매번 400, `$.code` = `EMAIL_CODE_INVALID`. 3번째 직후 `emailAuthSnapshotOf(VERIFY_EMAIL, "new@test.com").attempts()` = 3, 4번째 직후 4. 5번째 직후 `codeExists()` = false.
- 이어 맞는 `code`로 확인 → 400, `$.code` = `EMAIL_CODE_INVALID`. `redis.keys("auth:signup-verified:*")`가 비어 있다.

**7. 만료된 코드** — 준비: 발송 → `code`. 동작: `redis.delete("auth:email-code:VERIFY_EMAIL:new@test.com")` 후 `code`로 확인.
- 응답: 400, `$.code` = `EMAIL_CODE_INVALID`. `redis.keys("auth:signup-verified:*")`가 비어 있다.

**8. Redis에 코드 원문이 없다** — 준비: 발송 → `code`.
- `a = emailAuthSnapshotOf(VERIFY_EMAIL, "new@test.com")`: `a.fieldNames()`가 정확히 `codeHash`, `attempts`이고 `a.codeHash()`는 `code`와 같지 않다.
- `redis.keys("auth:*")`의 어떤 키 이름에도 `code`가 들어 있지 않다.

**9. 이미 가입된 이메일로 발송** — 준비: `testFixtures.signup("done@test.com")`. 동작: 본문 `{"email":"done@test.com"}`으로 발송.
- 응답: 409, `$.code` = `MEMBER_EMAIL_DUPLICATED`. 메일 0건, `redis.keys("auth:email-*")`가 비어 있다.

**10. 인증 토큰이 틀리면 가입을 거부한다** — 동작: `verificationToken`을 발급한 적 없는 값 `"never-issued"`로 가입.
- 응답: 400, `$.code` = `VERIFICATION_TOKEN_INVALID`. `countOf("member.member")` = 0.

**11. 다른 이메일의 인증 토큰으로는 가입할 수 없다** — 준비: `V = testFixtures.verificationToken("other@test.com")`. 동작: 이메일 `new@test.com`, `verificationToken` = `V`로 가입.
- 응답: 400, `$.code` = `VERIFICATION_TOKEN_INVALID`. `countOf("member.member")` = 0.

**12. 인증 토큰이 없으면 400 `INVALID_REQUEST`** — 동작: 본문에 `verificationToken`이 없는 요청과 빈 문자열인 요청으로 가입.
- 확인: 모두 400, `$.code` = `INVALID_REQUEST`. `countOf("member.member")` = 0.

**13. 가입이 실패해도 인증 토큰은 남는다** — 준비: `testFixtures.signup("taken@test.com")`(닉네임 `taken`), `V = testFixtures.verificationToken("new@test.com")`. 동작: 닉네임 `taken`으로 가입 → 409 `MEMBER_NICKNAME_DUPLICATED`, 이어 같은 `V`와 닉네임 `newbie`로 가입.
- 두 번째 가입: 201.

**14. 같은 인증 토큰으로 두 번 가입할 수 없다** — 준비: 3번의 동작까지(`new@test.com` 가입 201, `V`). 동작: 같은 `V`, 닉네임 `newbie2`로 다시 가입.
- 응답: 400, `$.code` = `VERIFICATION_TOKEN_INVALID`. `countOf("member.member")` = 1.

**15. Redis에 인증 토큰 원문이 없다** — 준비: 2번의 동작까지 → `V`.
- `redis.keys("auth:*")`의 어떤 키 이름에도 `V`가 들어 있지 않고, 어떤 STRING 값도 `V`와 같지 않다.

#### `member/web/PasswordTest`

**공통 준비**: 회원 `pw@test.com`(`signup`, 비밀번호 `pass1234`) → `memberId`. 변경은 `PATCH /api/members/me/password` 본문 `{"currentPassword":"pass1234","newPassword":"newpass5678"}`. 재설정 요청은 `POST /api/auth/password-reset` 본문 `{"email":"pw@test.com"}`(인증 없음), 재설정 확인은 `POST /api/auth/password-reset/confirm` 본문 `{"email":"pw@test.com","code":"{code}","newPassword":"resetpass99"}`.

**1. 변경 후 로그인** — 준비: `login("pw@test.com")` → `accessA`. 동작: 변경(`Bearer {accessA}`).
- 응답: 200, `$.tokenType` = `Bearer`, `$.expiresIn` = 900, `$.accessToken` 있음, `$.refreshToken` 없음/null, `$.refreshExpiresIn` = 0.
- `POST /api/auth/login`을 `pass1234`로 → 401, `$.code` = `LOGIN_FAILED`. `newpass5678`로 → 200, `$.accessToken` 있음.

**2. 변경 후 다른 기기의 refresh** — 준비: `loginTokens(…)` → `accessA`·`refreshA`·`sidA`, `loginTokens(…)` → `refreshB`·`sidB`. 동작: 변경(`Bearer {accessA}`).
- 변경 응답의 새 토큰 `newAccess`: `parse(newAccess).sessionId()` = `sidA`. `GET /api/members/me`를 `Bearer {newAccess}`로 → 200, `$.id` = `memberId`.
- `sessionSnapshotOf(sidA).exists()` = true, `sessionSnapshotOf(sidB).exists()` = false. `sessionIdsOf(memberId)`가 정확히 {`sidA`}다.
- `refresh(refreshB)` → 401, `$.code` = `UNAUTHORIZED`. `refresh(refreshA)` → 200.

**3. 현재 비밀번호가 틀림** — 준비: `loginTokens(…)` → `accessA`·`sidA`. 동작: 변경 본문 `{"currentPassword":"wrongpass1","newPassword":"newpass5678"}`.
- 응답: 400, `$.code` = `PASSWORD_MISMATCH`.
- `memberSnapshotOf(memberId).passwordHash()`가 요청 전 값과 같다. `sessionSnapshotOf(sidA).exists()` = true.

**4. 재설정 요청은 가입 여부와 상관없이 같은 응답** — 동작 1: 가입되지 않은 `nobody@test.com`으로 재설정 요청.
- 응답: 202, 본문 비어 있음. 메일 0건.
- 동작 2: 가입된 `pw@test.com`으로 재설정 요청 → 응답 상태와 본문이 동작 1과 같다(202, 비어 있음). 메일 1건(`to` = `pw@test.com`), `emailAuthSnapshotOf(RESET_PASSWORD, "pw@test.com").codeExists()` = true.

**5. 재설정 후 모든 refresh와 로그인** — 준비: `loginTokens(…)` → `refreshA`·`sidA`, `loginTokens(…)` → `refreshB`·`sidB`. 재설정 요청 → 202, 메일에서 `code`.
- 동작: `code`로 재설정 확인.
- 확인 1: 204. `sessionSnapshotOf(sidA).exists()`·`sessionSnapshotOf(sidB).exists()` = false, `sessionIdsOf(memberId)`가 비어 있다. `emailAuthSnapshotOf(RESET_PASSWORD, "pw@test.com").codeExists()` = false.
- 확인 2: 로그인 `pass1234` → 401 `LOGIN_FAILED`, `resetpass99` → 200.

**6. 비밀번호가 없는 회원의 변경 거부** — 준비: 3번과 같다(`loginTokens(…)` → `accessA`·`sidA`). 동작: `UPDATE member.member SET password_hash = NULL WHERE id = ?` 후 올바른 현재 비밀번호 `pass1234`로 변경(`Bearer {accessA}`, 본문 `{"currentPassword":"pass1234","newPassword":"newpass5678"}`).
- 응답: 400, `$.code` = `PASSWORD_MISMATCH`.

**7. ACTIVE가 아닌 회원에게는 재설정 메일을 보내지 않는다** — 동작: `testFixtures.changeStatus(memberId, "BANNED")` 후 `pw@test.com`으로 재설정 요청.
- 응답: 202. 메일 0건.

**8. 틀린 코드로는 재설정할 수 없다** — 준비: 5번과 같다(`loginTokens(…)` → `sidA`, `loginTokens(…)` → `sidB`, 재설정 요청 → 202, 메일에서 `code`). 요청 전 `memberSnapshotOf(memberId).passwordHash()`를 읽어 둔다. 동작: `code`가 아닌 6자리(`wrong`)로 재설정 확인.
- 응답: 400, `$.code` = `EMAIL_CODE_INVALID`. `memberSnapshotOf(memberId).passwordHash()` 그대로, `sessionSnapshotOf(sidA).exists()`·`sessionSnapshotOf(sidB).exists()` = true.

**9. 재설정 요청의 한도는 가입 여부와 무관하다** — 동작: 가입되지 않은 `nobody@test.com`으로 재설정 요청을 연달아 두 번 보낸다. 같은 방식으로 가입된 `pw@test.com`도 두 번 보낸다.
- 두 이메일 모두 첫 응답은 202, 두 번째 응답은 429, `$.code` = `RATE_LIMITED`, `$.details.retryAfterSeconds` 1~60이다.

**10. 재설정의 새 비밀번호도 가입 규칙을 따른다** — 준비: 재설정 요청 → 202, 메일에서 `code`. 요청 전 `memberSnapshotOf(memberId).passwordHash()`를 읽어 둔다. 동작: `newPassword`를 `"abcdefgh"`로 재설정 확인.
- 응답: 400, `$.code` = `INVALID_REQUEST`. `memberSnapshotOf(memberId).passwordHash()` 그대로, `emailAuthSnapshotOf(RESET_PASSWORD, "pw@test.com").codeExists()` = true.

**11. 재설정 메일 발송이 실패해도 같은 응답** — 준비: `fakeMailSender.failNext(1)`. 동작: `pw@test.com`으로 재설정 요청.
- 응답: 202, 본문 비어 있음(4번의 가입되지 않은 이메일과 같다).
- `sent()` 1건(`FakeMailSender`는 예외를 던지기 전에 기록한다). 발송을 시도하지 않고 202를 준 경우와 구분하기 위한 확인이다.

#### `member/infrastructure/EmailCodeConcurrencyTest` (`@RepeatedTest(10)`)

`IntegrationTestSupport`를 상속하고 `EmailCodeStore`·`TestFixtures`를 주입받는다. 동시 실행은 `support/ConcurrencyRunner.run(List<Callable<T>>)`.

**1. 틀린 코드를 동시에 보내도 비교는 5번까지만 한다** — 준비: `emailCodeStore.save(RESET_PASSWORD, "pw@test.com", "123456")` → `testFixtures.resetRedisStats()`. 동작: 50개 작업을 동시에 실행한다. `i`번째 작업은 `emailCodeStore.verify(RESET_PASSWORD, "pw@test.com", String.format("%06d", 200000 + i))`(모두 `123456`과 다르다).
- 50개 모두 예외 없이 `false`.
- `testFixtures.redisCallsOf("hincrby")` = 5.

**수동 확인: 운영 메일 발송** — 배포 뒤 공개 주소로 본인 이메일에 가입 인증 발송(`POST /api/auth/signup/email-verifications`)을 보낸다.
- 202이고, 받은편지함(없으면 스팸함)에 발신자가 발송 전용 Gmail 주소이고 제목이 `[MyRoutine] 이메일 인증코드`인 메일이 온다. 그 코드로 확인 → 가입까지 된다.
- 운영 VM 앱 로그(`docker compose logs app`)에서 그 6자리 코드가 검색되지 않는다.

**리뷰 때 물어볼 것**
- 인증코드를 비교할 때 타이밍 공격은?
- 비밀번호 재설정 요청에서 "가입되지 않은 이메일"이라고 알려주면 무엇이 문제인가?
- 코드 확인이 이메일에 "통과" 표시만 남기지 않고 인증 토큰을 돌려주는 이유는?

---

## 4-3. OAuth 로그인 (Kakao · Google · Naver) (L)

**목표**: Kakao·Google·Naver로 가입·로그인한다. 신규 회원은 가입 토큰 → 이메일 인증 → 가입 완료. 로그인한 회원은 소셜 계정을 연결할 수 있다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| Authorization Code 흐름 | 사용자를 제공자 로그인 화면으로 보냄 → 돌아온 `code`를 서버가 토큰으로 교환 → 사용자 정보 조회 |
| `state` | 인가 URL에 붙이는 랜덤 값. 프론트가 만들어 출발 전에 브라우저(`sessionStorage`)에 저장하고, 돌아온 `state`가 그 값과 다르면 로그인 요청을 보내지 않는다. "이 로그인은 이 브라우저가 시작했다"를 확인해, 공격자가 자기 계정의 `code`가 붙은 콜백 링크를 피해자에게 열게 하는 공격(로그인 CSRF)을 막는다. 서버는 `state`를 만들지도 저장하지도 비교하지도 않는다. 서버에 저장하면 모든 브라우저가 같은 장부를 쓰게 되어, 공격자가 직접 발급받은 `state`도 "우리가 만든 값"으로 통과한다 |
| 전략(Strategy) | 제공자마다 주소·요청 파라미터·응답 JSON 모양이 다르다. `OAuthProviderClient` 구현체 3개가 차이를 흡수한다 |

**정책 (이 단계에서 정함)**
- 소셜로 가입한 회원도 이메일 인증을 거친다(FR-MEM-01). 이미 가입된 이메일(이메일 가입 회원)과 같으면 **자동 연결하지 않고** 409 `MEMBER_EMAIL_DUPLICATED` — 그 계정으로 로그인한 뒤 "소셜 계정 연결"을 쓰게 안내한다(POL-19).
- 한 회원은 제공자마다 소셜 계정 1개.
- 제공자가 이메일을 주지 않으면(카카오 선택 동의 등) 사용자가 가입 단계에서 이메일을 입력한다.
- 제공자 호출 타임아웃: connect 3초, read 5초.
- 제공자 로그인 화면 주소는 프론트가 만든다(`client_id`·`redirect_uri`·`scope`를 프론트 환경변수로 가진다). 서버에는 인가 주소를 만드는 API가 없고, 토큰 교환 요청에 `redirect_uri`가 같은 값으로 필요해 서버 설정의 `redirect-uri`가 프론트의 값과 같아야 한다.
- 탈퇴하면 그 회원의 소셜 계정 연결을 모두 지운다. 제공자 사용자 ID도 개인을 가리키는 값이고, 행이 남으면 같은 소셜 계정으로 로그인(401 `LOGIN_FAILED`)도 가입(`uk_social_account_provider_user`)도 못 한다. 지운 뒤에는 같은 소셜 계정이 처음 보는 계정이 되어 `SIGNUP_REQUIRED`로 다시 가입한다(이메일 회원의 재가입과 같다, 3-6).

**전체 흐름** (프론트 = 사용자 브라우저에서 도는 화면, 제공자 = Kakao·Google·Naver)
1. 프론트가 제공자 로그인 화면 주소를 직접 만든다(제공자의 인가 주소에 `response_type=code`, `client_id`, `redirect_uri`, `scope`, `state`를 쿼리로 붙인다). `state`는 프론트가 만든 랜덤 값이고 `sessionStorage`에 저장한다
2. 프론트가 브라우저를 그 주소로 이동시킨다. 사용자가 제공자에서 로그인·동의하면 제공자가 **미리 등록해 둔 `redirectUri`(프론트 화면)** 로 `?code=...&state=...`를 붙여 돌려보낸다. `code`는 한 번만 쓰는 교환권이다(우리 API가 `code`·`state`를 바디로 받으므로 `redirectUri`는 서버가 아니라 프론트 화면이다)
3. 프론트가 돌아온 `state`를 `sessionStorage`의 값과 비교해 다르거나 없으면 멈춘다. 같으면 `POST /api/auth/oauth/kakao/login` 바디 `{code, state}`를 보낸다 → 서버가 ① `code`를 제공자 토큰 주소로 보내 **제공자 access token**으로 바꾸고 ② 그 토큰으로 사용자 정보를 읽는다(`fetchUser`). `state`는 비교하지 않고 Naver 토큰 요청에 넘기기만 한다. 제공자 access token은 사용자 정보를 읽는 데만 쓰고 저장하지 않는다(우리 토큰과 별개다)
4. 이미 연결된 소셜 계정이면 `LOGGED_IN` + 우리 토큰. 처음 보는 계정이면 `SIGNUP_REQUIRED` + 가입 토큰(Redis 10분)과 제공자가 준 이메일
5. 신규 가입: 프론트가 4-2의 `POST /api/auth/signup/email-verifications`로 인증코드를 받고 `…/confirm`으로 인증 토큰을 받는다 → 가입 토큰·인증 토큰과 함께 `POST /api/auth/oauth/signup` → 201 우리 토큰
6. 로그인한 회원의 계정 연결은 1~3과 같고, 4 대신 소셜 계정을 저장만 한다(`POST /api/members/me/social-accounts/{provider}`)

### 할 일

**구현 순서**: ① 마이그레이션(1) → ② member.domain(2): `OAuthProvider`·`OAuthUserInfo` → 에러 코드·`SocialAccount`·`SocialAccountRepository`·`Member.signUpWithSocial` → `SignupTokenStore`·`PendingSocialSignup` → ③ member.infrastructure(3): `OAuthProperties`·`OAuthConfig`·`application.yaml` → `OAuthProviderClient`와 구현체 3개·`OAuthClients` → 가입 토큰 저장소의 Redis 구현 → ④ member.application(4): `OAuthLoginService` → `WithdrawService` 변경 → ⑤ member.web(5): 컨트롤러 → ⑥ `FakeOAuthServer`(6) → 테스트.

**1) 마이그레이션** — `db/migration/member/V{...}__member_create_social_account.sql`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_social_account` |
| member_id | uuid | X | `fk_social_account_member`, `uk_social_account_member_provider (member_id, provider)` |
| provider | varchar(20) | X | `ck_social_account_provider`: `IN ('KAKAO','GOOGLE','NAVER')` |
| provider_user_id | varchar(100) | X | `uk_social_account_provider_user (provider, provider_user_id)` |
| provider_email | varchar(255) | O | |
| created_at | timestamptz | X | |

**2) member.domain**

| 클래스 | 규격 |
|---|---|
| `OAuthProvider` | enum `KAKAO, GOOGLE, NAVER`. DB `social_account.provider`와 Redis 가입 토큰의 `provider` 필드에 `name()` 그대로 쓴다 |
| `OAuthUserInfo` | `record (String providerUserId, String email)` — 제공자 응답을 우리 모양으로 옮긴 사용자 정보. `email`은 제공자가 안 주면 `null`(`providerUserId`는 항상 있어야 한다). 닉네임은 가입 요청이 직접 받으므로 제공자 응답에서 읽지 않는다 |
| `PendingSocialSignup` | `record (OAuthProvider provider, String providerUserId, String email)` — Redis 해시(공통 규칙 T의 `auth:oauth-signup:{tokenHash}`)의 필드 |
| `SocialAccount`·`SocialAccountRepository` | 엔티티(`member.social_account`)와 저장소. 필드는 위 마이그레이션 컬럼과 1:1(`id`는 `Ids.newId()`, 수정이 없는 테이블이라 `BaseTimeEntity`를 상속하지 않고 `@EntityListeners(AuditingEntityListener.class)`와 `@CreatedDate Instant createdAt` 필드 하나만 둔다). 만드는 곳은 정적 팩토리 `SocialAccount.link(UUID memberId, OAuthProvider provider, String providerUserId, String providerEmail)` 하나다(`MemberAddress.register`처럼 `new SocialAccount()`에 `id = Ids.newId()`와 인자 넷을 넣어 돌려주고, `createdAt`은 저장할 때 채워지므로 넣지 않는다). 메서드는 `Optional<SocialAccount> findByProviderAndProviderUserId`, `boolean existsByProviderAndProviderUserId`, `boolean existsByMemberIdAndProvider`, `List<SocialAccount> findByMemberIdOrderByCreatedAtAscIdAsc(UUID memberId)`(내 정보의 연결 목록), `void deleteAllByMemberId(UUID memberId)`(탈퇴, `MemberAddressRepository.deleteAllByMemberId`와 같은 방식), `SocialAccount save(SocialAccount socialAccount)`(`MemberRepository.save`처럼 엔티티를 돌려준다. `void`로 선언하면 `JpaRepository.save`와 반환 타입이 충돌해 컴파일되지 않는다) |
| `Member` | `Member.signUpWithSocial(String email, String nickname, String name)` — `Member.signUp`과 같은 정적 팩토리(id 생성, `ACTIVE`, `USER`, 이메일 정규화)에 `passwordHash = null` |
| `MemberErrorCode` | `OAUTH_PROVIDER_ERROR`(502)·`SIGNUP_TOKEN_INVALID`(401)·`SOCIAL_ACCOUNT_ALREADY_LINKED`(409)를 추가한다(공통 규칙 U) |
| `SignupTokenStore` | 인터페이스 — 신규 소셜 사용자의 제공자 정보를 가입이 끝날 때까지 Redis에 맡아 둔다. `OAuthLoginService`가 쓴다. 메서드는 아래 표 |

`SignupTokenStore` 메서드

| 메서드 | 하는 일 | 입력 → 반환 |
|---|---|---|
| `String issue(OAuthProvider provider, OAuthUserInfo info)` | 신규 소셜 사용자의 제공자 정보를 가입이 끝날 때까지 맡아 둔다 | 추측할 수 없는 토큰(`SecureRandom` 32바이트를 Base64URL로 인코딩. 4-1 `RefreshToken.generate`의 `secret`과 같은 방식)을 만들어 `auth:oauth-signup:` + 해시(토큰) 키의 해시에 `provider`·`providerUserId`·`email`을 저장하고 10분 `expire` → 토큰. `email`이 `null`이면 그 필드는 저장하지 않는다(공통 규칙 T) |
| `Optional<PendingSocialSignup> find(String token)` | 토큰으로 맡겨 둔 정보를 찾는다 | `entries("auth:oauth-signup:" + 해시(token))`가 비었으면 `Optional.empty()`. 아니면 `provider`는 `OAuthProvider.valueOf`로, `providerUserId`는 그대로 읽고, `email`만 저장되지 않았을 수 있어 없으면 `null`로 읽는다(`provider`·`providerUserId`는 `issue`가 항상 저장한다) |
| `void delete(String token)` | 가입이 끝났으니 지운다 | `delete("auth:oauth-signup:" + 해시(token))` |

**3) member.infrastructure**

| 클래스 | 규격 |
|---|---|
| `OAuthProperties` | `@ConfigurationProperties("myroutine.oauth") record (Map<String, Provider> providers)`, `Provider(String clientId, String clientSecret, String tokenUri, String userInfoUri, String redirectUri)`. 시크릿은 환경변수(`KAKAO_CLIENT_SECRET` 등) |
| `OAuthConfig` | `@Configuration @EnableConfigurationProperties(OAuthProperties.class)`. `@Bean RestClient oauthRestClient()`: `JdkClientHttpRequestFactory`(`HttpClient.newBuilder().connectTimeout(3초)`, `setReadTimeout(5초)`)를 `RestClient.builder().requestFactory(factory).build()`에 넣는다. 세 구현체가 이 빈 하나를 주입받는다. `TossClientConfig`와 같은 방식이다 |
| `OAuthProviderClient` | 인터페이스 — 제공자 한 곳과의 통신을 맡는다. 호출자는 `OAuthLoginService`뿐이다. 메서드는 아래 표 |
| `KakaoOAuthClient`·`GoogleOAuthClient`·`NaverOAuthClient` | `OAuthProviderClient`의 구현체. 제공자별로 읽는 필드는 아래 표 |
| `OAuthClients` | `@Component`. `List<OAuthProviderClient>`를 주입받아 `Map<OAuthProvider, OAuthProviderClient>`로 만든다(`client.provider()`가 키). `OAuthProviderClient get(OAuthProvider provider)`가 서비스의 진입점이다 |
| `SignupTokenStore`의 Redis 구현 | member.domain의 인터페이스를 Redis로 구현한다(메서드 규격은 member.domain 표) |

`OAuthProperties` 설정

- `OAuthConfig`(`member.infrastructure`, `@Configuration @EnableConfigurationProperties(OAuthProperties.class)`)가 `OAuthProperties`를 등록하고 세 구현체가 함께 쓰는 `RestClient` 빈 `oauthRestClient`도 만든다(타임아웃이 제공자 셋에 같다). `TossClientConfig`와 같은 방식이다
- yaml은 `myroutine.oauth.providers.kakao.client-id`처럼 **소문자 제공자 이름을 맵 키**로 쓴다(`Map<String, Provider>`). 코드에서는 `properties.providers().get("kakao")`처럼 `OAuthProvider.name().toLowerCase(Locale.ROOT)`로 찾는다
- 환경변수가 없는 `${...}`는 기동 때 실패하므로, 테스트용 더미 `client-id`·`client-secret`은 `src/test/resources/application-test.yaml`에 둔다

`application.yaml`의 `myroutine:` 아래(`toss:` 다음)에 제공자 세 곳을 이렇게 쓴다. `Provider`의 필드 이름이 kebab-case로 바인딩된다(`clientId` ← `client-id`).

```yaml
  oauth:
    providers:
      kakao:
        client-id: ${KAKAO_CLIENT_ID}
        client-secret: ${KAKAO_CLIENT_SECRET}
        token-uri: https://kauth.kakao.com/oauth/token
        user-info-uri: https://kapi.kakao.com/v2/user/me
        redirect-uri: ${KAKAO_REDIRECT_URI:http://localhost:5173/oauth/kakao/callback}
      google:
        client-id: ${GOOGLE_CLIENT_ID}
        client-secret: ${GOOGLE_CLIENT_SECRET}
        token-uri: https://oauth2.googleapis.com/token
        user-info-uri: https://openidconnect.googleapis.com/v1/userinfo
        redirect-uri: ${GOOGLE_REDIRECT_URI:http://localhost:5173/oauth/google/callback}
      naver:
        client-id: ${NAVER_CLIENT_ID}
        client-secret: ${NAVER_CLIENT_SECRET}
        token-uri: https://nid.naver.com/oauth2.0/token
        user-info-uri: https://openapi.naver.com/v1/nid/me
        redirect-uri: ${NAVER_REDIRECT_URI:http://localhost:5173/oauth/naver/callback}
```

- `redirect-uri`는 프론트의 콜백 화면 주소이고 제공자 콘솔에 등록한 값, 프론트가 인가 주소에 넣는 `redirect_uri`와 글자까지 같아야 한다(서버는 토큰 교환 요청에 쓴다). 기본값은 로컬이고, 운영은 `.env`의 `KAKAO_REDIRECT_URI=https://myroutine.dev/oauth/kakao/callback`처럼 덮어쓴다(Google·Naver도 같다).
- Naver의 `token-uri`·`user-info-uri`는 동작하는 요청으로 확인했다.
- `client-id`·`client-secret`은 기본값 없이 환경변수만 쓴다. 로컬은 `.env.local`, 운영은 `/opt/myroutine/.env`에 넣고 `docker/`·`docs/ops/vm-setup.md`의 키 목록에도 더한다.
- `application-test.yaml`은 같은 구조에서 `client-id`·`client-secret`만 더미 값으로 덮는다(`token-uri`·`user-info-uri`는 `IntegrationTestSupport`의 `@DynamicPropertySource`가 가짜 서버 주소로 덮는다). 테스트가 덮은 값은 `${...}`가 풀리지 않아 환경변수가 없어도 기동한다(`jwt.secret`과 같은 방식).

서비스(`OAuthLoginService`)는 제공자를 if로 가르지 않고, "code로 사용자 정보 얻기"를 `OAuthProviderClient` 하나로 부른다.

`OAuthProviderClient` 메서드

| 메서드 | 하는 일 | 입력 → 반환 |
|---|---|---|
| `OAuthProvider provider()` | 이 구현체가 어느 제공자인지 | → enum 값(`OAuthClients`가 키로 쓴다) |
| `OAuthUserInfo fetchUser(String code, String state)` | `code`를 제공자 토큰으로 바꾸고 그 토큰으로 사용자 정보를 읽는다 | 아래 순서 |

`fetchUser(code, state)`는 제공자 서버를 두 번 부른다(`code`를 토큰으로 바꾸기, 그 토큰으로 사용자 정보 읽기). 구현체 셋의 구조는 같고 꺼내는 필드 이름만 다르다. 구현체는 생성자로 `OAuthProperties`(자기 제공자 설정)와 `oauthRestClient`(`OAuthConfig`가 만든 공유 `RestClient`)를 받는다.

`fetchUser` 순서
1. 호출 두 개(2·4)와 값 꺼내기(3·5)를 모두 하나의 `try`로 감싼다. `catch (RestClientException e)`에서 `throw new BusinessException(OAUTH_PROVIDER_ERROR)`(502)를 던진다. 제공자의 4xx·5xx는 `RestClientResponseException`, 타임아웃·연결 실패는 `ResourceAccessException`으로 `retrieve()`가 던지고 둘 다 `RestClientException`의 하위라 이 `catch` 하나로 잡힌다. 상태 코드를 따로 분류하지 않는다
2. 토큰 요청: `MultiValueMap<String, String> form = new LinkedMultiValueMap<>()`에 `grant_type`(`authorization_code`), `code`, `redirect_uri`, `client_id`, `client_secret`을 `form.add(이름, 값)`으로 담고 `restClient.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class)`로 보낸다. 폼 값은 `RestClient`가 인코딩한다. 값은 설정(`provider`)과 `code`에서 온다. Kakao·Google의 토큰 요청에는 `state`가 없어 보내지 않는다. Naver는 토큰 요청에 `state`도 보낸다(프론트가 돌려준 값 그대로, 그래서 인터페이스가 `state`를 받는다. 동작하는 요청으로 확인). Naver 요청에 `redirect_uri`가 필요한지는 확인하지 못했다(동작하는 요청에는 없었다). 보내서 거절되면 Naver 구현체만 뺀다. Kakao는 Client Secret이 기본 ON이라 `client_secret`이 필수다
3. 응답에서 `access_token`을 꺼낸다: `tokenResponse`가 `null`이거나 `tokenResponse.path("access_token").asString()`이 빈 문자열이면(Jackson 3에서 없는 노드와 `null` 노드의 `asString()`은 `""`이라 `isBlank()` 하나로 셋을 모두 거른다) `throw new BusinessException(OAUTH_PROVIDER_ERROR)`를 직접 던진다(이 경우는 `RestClientException`이 아니라 순서 1의 `catch`에 걸리지 않는다). 있으면 `asString()`으로 문자열을 얻는다
4. 사용자 정보 요청: `restClient.get().uri(userInfoUri).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).retrieve().body(JsonNode.class)`
5. 응답에서 `providerUserId`와 `email`을 꺼낸다(필드 이름은 아래 표). 응답이 `null`이거나 `providerUserId` 노드가 없거나 비어 있으면 순서 3처럼 직접 `OAUTH_PROVIDER_ERROR`를 던진다. `email`은 없으면 `null`이다(키가 없거나 `null`이거나 빈 문자열이면 모두 `null`). 중첩 필드(`kakao_account.email`)는 `path("kakao_account").path("email")`로 내려간다. `path`는 없으면 `null`이 아니라 `MissingNode`를 돌려주므로 중간 객체가 없어도 NPE가 나지 않는다
6. `new OAuthUserInfo(providerUserId, email)`을 반환한다. Kakao `id`는 JSON 숫자인데 `asString()`이 `"123456789"` 문자열로 바꿔 준다. Jackson 3(`tools.jackson.databind`)의 `JsonNode`는 문자열 변환 메서드가 `asString()`이다

| 구현체 | 읽는 필드 |
|---|---|
| `KakaoOAuthClient` | `id`(숫자), `kakao_account.email`. 사용자 정보 주소 `https://kapi.kakao.com/v2/user/me`. 이메일은 동의 항목 `account_email`에 동의해야 내려온다 |
| `GoogleOAuthClient` | `sub`, `email`. 사용자 정보 주소 `https://openidconnect.googleapis.com/v1/userinfo`. 이메일은 `openid email` scope로 받는다 |
| `NaverOAuthClient` | `response.id`(동작하는 요청으로 확인), `response.email`(확인 필요: 이메일 제공에 동의하지 않았을 때 키가 없는지·`null`인지·빈 문자열인지) |

**4) member.application**

| 클래스 | 메서드 |
|---|---|
| `OAuthLoginService` | `login`·`signup`·`link` 3개. 규격은 아래 표 |
| `MemberQueryService` | `getMe` 변경(아래 "내 정보의 비밀번호 유무와 소셜 계정") |
| `WithdrawService` | `withdraw` 변경(아래 "탈퇴와 소셜 계정") |
| `OAuthLoginResult` | `record (String status, TokenResult token, String signupToken, String email)` — `status`는 `LOGGED_IN`(`token`만 채움) 또는 `SIGNUP_REQUIRED`(`signupToken`·`email`만 채움, 나머지는 `null`) |
| `SocialSignupCommand` | `record (String signupToken, String email, String nickname, String name, String verificationToken)` — `SocialSignupRequest.toCommand()`로 만든다 |

`OAuthLoginService` (`@Service`) — OAuth 로그인·가입·연결 유스케이스 3개. 컨트롤러가 부르고, 제공자 호출·Redis·DB를 조율한다. 의존성: `OAuthClients`, `SignupTokenStore`, `SignupVerificationStore`(4-2), `SocialAccountRepository`, `MemberRepository`, `TokenIssuer`.

| 메서드 | 한 줄 설명 | 입력 → 반환 |
|---|---|---|
| `OAuthLoginResult login(OAuthProvider provider, String code, String state)` | 소셜 로그인. 가입된 계정이면 로그인, 아니면 가입 토큰을 준다 | → `OAuthLoginResult` |
| `TokenResult signup(SocialSignupCommand command)` | 가입 토큰 + 인증코드로 가입을 끝낸다 | → `TokenResult` |
| `void link(UUID memberId, OAuthProvider provider, String code, String state)` | 로그인한 회원에게 소셜 계정을 연결한다 | → 없음 |

- 트랜잭션: `login`·`link`는 제공자 호출(최대 3초+5초)이 들어 있으므로 **`@Transactional`을 붙이지 않는다**(공통 규칙 K, DB 커넥션을 잡고 기다리지 않게). 조회·저장은 리포지토리가 짧은 트랜잭션으로 한다. `signup`만 `@Transactional`(7단계 한 묶음)

`login` 순서
1. `OAuthUserInfo info = clients.get(provider).fetchUser(code, state)` — 실패하면 502 `OAUTH_PROVIDER_ERROR`
2. `socialAccountRepository.findByProviderAndProviderUserId(provider, info.providerUserId())`가 있으면(제공자 이메일은 보지 않고 제공자 사용자 ID로만 찾는다): `memberRepository.findById(account.getMemberId())` → `member.verifyCanLogin()`(BANNED 403) → `tokenIssuer.issueNewSession(member)` → `OAuthLoginResult("LOGGED_IN", token, null, null)`
3. 없으면: `signupTokenStore.issue(provider, info)` → `OAuthLoginResult("SIGNUP_REQUIRED", null, signupToken, info.email())`(제공자가 이메일을 안 줬으면 `null`)

`signup` 순서
1. `signupTokenStore.find(command.signupToken())` — 없으면 401 `SIGNUP_TOKEN_INVALID`
2. `email` = 토큰의 이메일, 없으면 `command.email()`(둘 다 없으면 400 `INVALID_REQUEST`) → `Member.normalizeEmail`
3. `signupVerificationStore.findEmail(command.verificationToken())`가 비었거나 `email`과 다르면 400 `VERIFICATION_TOKEN_INVALID`(4-2 `signUp`과 같다)
4. `existsByEmail(email)`이면 409 `MEMBER_EMAIL_DUPLICATED` (자동 연결 안 함)
5. `existsByNickname(command.nickname())`이면 409 `MEMBER_NICKNAME_DUPLICATED`
6. `Member.signUpWithSocial(...)`을 저장하고 `SocialAccount.link(member.getId(), token.provider(), token.providerUserId(), token.email())`를 저장한다(한 트랜잭션). `providerEmail`은 제공자가 준 이메일이라 `command.email()`로 입력받은 값을 넣지 않는다(제공자가 안 줬으면 `null`)
7. `signupTokenStore.delete(token)`·`signupVerificationStore.delete(command.verificationToken())` → `tokenIssuer.issueNewSession(member)` → `TokenResult`. 동시 요청이 확인을 둘 다 통과하면 unique 제약이 409 `DUPLICATE_RESOURCE`로 막는다

`link` 순서
1. `fetchUser`
2. 이미 다른 회원에 연결된 소셜 계정이거나 이 회원이 같은 제공자를 이미 가졌으면(`existsByProviderAndProviderUserId`, `existsByMemberIdAndProvider`) 409 `SOCIAL_ACCOUNT_ALREADY_LINKED`
3. `SocialAccount.link(memberId, provider, info.providerUserId(), info.email())`를 저장 (동시 요청 경합은 unique 제약 → 409 `DUPLICATE_RESOURCE`)

**내 정보의 비밀번호 유무와 소셜 계정** — 소셜로 가입한 회원은 비밀번호가 없어 비밀번호 변경이 항상 400 `PASSWORD_MISMATCH`다. 화면이 이 회원에게 변경 대신 재설정을 안내하고, 제공자마다 "연결됨"과 "연결하기"를 나눠 보여 주려면 내 정보가 두 값을 알려 줘야 한다. 소셜 계정 연결은 이 화면에서만 하므로(POL-19) 연결 상태도 여기서 보여 준다. 연결 해제는 요구사항에 없다.
- `MeResult`에 `boolean hasPassword`(`member.getPasswordHash() != null`)와 `List<SocialAccountResult> socialAccounts`를 더한다. `SocialAccountResult`는 `record (OAuthProvider provider, Instant linkedAt)`이고 `linkedAt`은 `SocialAccount.createdAt`이다
- `MemberQueryService.getMe`: 의존성에 `SocialAccountRepository`를 더하고, 회원 조회 뒤 `findByMemberIdOrderByCreatedAtAscIdAsc(memberId)`를 `SocialAccountResult`로 옮긴다(연결한 순서, 없으면 빈 목록)

**탈퇴와 소셜 계정** — `WithdrawService.withdraw`(3-6)에 `SocialAccountRepository`를 의존성으로 더하고, 배송지를 지우는 `memberAddressRepository.deleteAllByMemberId(memberId)` 바로 옆에서 `socialAccountRepository.deleteAllByMemberId(memberId)`를 부른다. 같은 `@Transactional`이고 `findByIdForUpdate`로 회원 행을 잠근 뒤라 중간 상태가 보이지 않는다.
- 알려진 한계: 탈퇴 직후 access 토큰이 아직 유효하면 `link`가 탈퇴 회원에게 새 연결 행을 만들 수 있다. 4-4의 `tokenVersion` bump가 탈퇴 직후 토큰을 거부하면 닫히므로 4-3에서는 락을 더하지 않는다.

**5) member.web**

| API | 권한 | 요청 | 응답 |
|---|---|---|---|
| `POST /api/auth/oauth/{provider}/login` | 공개 | `OAuthLoginRequest(@NotBlank code, @NotBlank state)` | 200 `OAuthLoginResponse` |
| `POST /api/auth/oauth/signup` | 공개(가입 토큰) | `SocialSignupRequest(@NotBlank signupToken, @Email email, nickname, name, @NotBlank verificationToken)` | 201 `TokenResponse` |
| `POST /api/members/me/social-accounts/{provider}` | 로그인 | `OAuthLoginRequest` | 204 |

- `SocialSignupRequest`의 제약은 `SignupRequest`(4-2)와 같게 둔다: `signupToken`·`verificationToken`은 `@NotBlank`, `nickname`은 `@NotBlank @Size(min = 2, max = 20)`, `name`은 `@NotBlank @Size(max = 50)`, `email`은 `@Email @Size(max = 255)`이고 `@NotBlank`는 붙이지 않는다(제공자가 이메일을 줬으면 쓰지 않는 값이라 `null`이 정상이다. 제공자가 안 줬는데 `email`도 없으면 서비스가 400 `INVALID_REQUEST`로 거절한다). `toCommand()`는 다섯 값을 `SocialSignupCommand`로 그대로 옮긴다
- `common.security.SecurityConfig`: `/api/auth/oauth/**`는 공개(이미 `/api/auth/**` 허용 안에 있다)
- `{provider}`(소문자)를 `@PathVariable OAuthProvider`로 바로 받으면 Spring의 기본 문자열→enum 변환이 대소문자를 구분해 `kakao`를 받아 주지 않을 수 있다(확인 필요: 구현 때 테스트로 확인). 안 되면 `String`으로 받아 `OAuthProvider.valueOf(value.toUpperCase(Locale.ROOT))`로 바꾸고, `IllegalArgumentException`은 `BusinessException(INVALID_REQUEST)`(400)로 바꾼다. 안 바꾸면 `GlobalExceptionHandler`의 일반 `Exception` 처리로 500이 된다
- `MeResponse`(1-5의 `GET /api/members/me` 응답)에 `boolean hasPassword`와 `List<SocialAccountResponse> socialAccounts`를 더하고 `from`에서 옮긴다. `SocialAccountResponse(String provider, Instant linkedAt)`(`provider`는 `name()`)
- `OAuthLoginResponse(String status, TokenResponse token, String signupToken, String email)`: `OAuthLoginResult`의 네 값을 그대로 옮기고, `token`은 `result.token() == null ? null : TokenResponse.from(result.token())`로 중첩한다(`SIGNUP_REQUIRED`이면 `token`이 통째로 null이고, `TokenResponse.from(null)`은 NPE라 `from` 안에서 null을 먼저 확인한다). 일반 로그인 응답(`TokenResponse`)과 클라이언트가 같은 토큰 객체를 읽는다
- `login`·`signup`은 `OAuthController`(`@RequestMapping("/api/auth/oauth")`, `member.web`)가, `link`는 경로가 `/api/members/me/...`라 `MemberController`(`/api/members`)가 `@PostMapping("/me/social-accounts/{provider}")`로 받는다(비밀번호 변경과 같은 방식). 각 메서드는 `OAuthLoginService`의 `login`·`signup`·`link`를 부른다
- `link`(`POST /api/members/me/social-accounts/{provider}`)는 로그인 회원 요청이라 memberId를 `@CurrentMember UUID memberId`로 받는다. 응답은 204(`ResponseEntity.noContent().build()`)

**6) 가짜 제공자 서버** — `src/test/java/com/myroutine/support/`

| 클래스 | 규격 |
|---|---|
| `FakeOAuthServer` | JDK `HttpServer`로 띄우고 `start()`·`baseUrl()`·`reset()`을 가진다. 지연·500 등을 정하는 이미 있는 `support/Behavior`(`Behavior.delay(ms)`, `Behavior.serverError()`)를 재사용한다. 실제 제공자 대신 테스트가 띄우는 서버로, 제공자 호출 두 가지에 답한다. |

- 경로 `/{provider}/token`: 어떤 `code`가 와도 `{"access_token": "..."}`를 돌려준다
- 경로 `/{provider}/userinfo`: `givenUser`로 정해 둔 사용자를 **그 제공자의 응답 모양**(Kakao `{id, kakao_account:{email}}`, Google `{sub, email}`, Naver `{response:{id, email}}`)으로 돌려준다 — 그래야 세 클라이언트의 파싱을 각각 시험할 수 있다
- `givenUser(provider, providerUserId, email)`: 응답에 쓸 사용자를 등록한다. Kakao의 `id`는 실제 제공자처럼 JSON 숫자로 내려 보낸다(`providerUserId`가 숫자 문자열이면 숫자로, 아니면 문자열). Google의 `sub`, Naver의 `response.id`는 문자열이다
- `failWith(provider, Behavior)`: 그 제공자의 token·userinfo 응답 **모두**를 지연·500 등으로 바꾼다(`OAUTH_PROVIDER_ERROR` 시험)
- `requests(String pathPrefix)` → `List<RecordedRequest>`: 경로가 그 접두사로 시작하는 요청을 받은 순서대로 돌려준다. 2-3의 `FakePgServer.requests`와 같은 방식이다(요청을 `CopyOnWriteArrayList`에 기록한다)
- `reset()`은 `IntegrationTestSupport`의 `@AfterEach`에서 부른다(등록한 사용자·`failWith`·요청 기록을 비운다)
- `IntegrationTestSupport`의 `@DynamicPropertySource`에서 제공자별 `token-uri`·`user-info-uri`를 가짜 서버 주소로 넣는다(`myroutine.oauth.providers.kakao.token-uri` 등)

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 이 단계에서 더하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `memberIdOf(String email)` → `UUID` | JDBC `SELECT id FROM member.member WHERE email = ?`. 행이 없으면 `null`. `email`은 정규화한 값 | `OAuthLoginTest` |
| `socialAccountsOf(UUID memberId)` → `List<SocialAccountRow>` | JDBC `SELECT provider, provider_user_id, provider_email FROM member.social_account WHERE member_id = ? ORDER BY created_at, id`. `SocialAccountRow`는 `support/`의 record(`provider`, `providerUserId`, `providerEmail`. 모두 `String`) | `OAuthLoginTest`, `SocialLinkTest` |
| `redisHashOf(String key)` → `RedisHash` | `redis.opsForHash().entries(key)`를 `Map<String, String>`으로 바꾸고 `redis.getExpire(key, TimeUnit.SECONDS)`. `support/`의 record(`Map<String, String> fields`, `long ttlSeconds`)이고 `exists()`(`fields`가 비어 있지 않다)를 가진다 | `OAuthLoginTest`의 `auth:oauth-signup:*` |

이미 있는 헬퍼(`TestFixtures`)는 다음과 같다.
- `MemberSnapshot memberSnapshotOf(UUID memberId)`: `nickname`, `name`, `phone`, `passwordHash`, `role`, `status`, `email`, `createdAt`, `updatedAt`.
- `long countOf(String table)`: `SELECT COUNT(*) FROM {table}`.
- `String verificationToken(String email)`(4-2): 메일 없이 그 이메일의 인증 토큰을 만든다. `RedisString redisStringOf(String key)`(4-2): 문자열 키의 값과 남은 시간.
- `SessionSnapshot sessionSnapshotOf(UUID sessionId)`: 세션 해시와 남은 시간을 한 번에 읽는다. 접근자는 `exists()`, `memberId()`, `refreshHash()`, `ttlSeconds()`.
- `Set<UUID> sessionIdsOf(UUID memberId)`: 회원의 세션 ID 집합. 세션이 없으면 빈 집합.

헬퍼를 쓰는 방법은 다음과 같다.
- 소셜 로그인 응답: `$.status`(`LOGGED_IN` 또는 `SIGNUP_REQUIRED`), `$.signupToken`, `$.email`, 로그인 성공 시의 토큰. 토큰은 `$.token.accessToken`·`$.token.refreshToken`으로 읽는다(`OAuthLoginResponse`가 `token`을 중첩한다).
- 가짜 제공자 서버 `oauth`(`FakeOAuthServer`): 제공자별로 `oauth.givenUser(OAuthProvider.KAKAO, "kakao-1001", "social@test.com")`로 사용자를 정한다. 제공자가 받은 요청 수는 `oauth.requests("/kakao/token").size()`로 읽는다. 시간 의존: 가입 토큰의 10분 경과는 `redis.delete(키)`로 흉내 낸다.
- `UUID socialSignup(OAuthProvider provider, String providerUserId, String email)`: `OAuthLoginTest`의 private 메서드. ① `oauth.givenUser(provider, providerUserId, email)` ② `oauthLogin(provider, STATE)`의 `$.signupToken`(`T`) ③ `V = testFixtures.verificationToken(email)` ④ `POST /api/auth/oauth/signup` 본문 `{"signupToken":"{T}","nickname":"socialnick","name":"소셜","verificationToken":"{V}"}`를 MockMvc로 수행하고 `memberIdOf(email)`(가입된 `memberId`)를 돌려준다. `issueState`·`oauthLogin`은 아래 "공통 준비"에 있다.
- `TestFixtures.signup`·`login`은 4-2 이후 동작(인증 토큰을 직접 만들어 가입) 그대로 쓴다.

**공통 준비**: `application-test.yaml`의 더미 `client-id`·`client-secret`과 가짜 서버 주소(`token-uri`, `user-info-uri`)가 들어가 있고, 테스트 클래스 private 메서드(이름은 권장안): 상수 `STATE = "test-state"`(서버는 `state`를 만들지도 비교하지도 않으므로 아무 문자열이면 된다). `ResultActions oauthLogin(OAuthProvider provider, String state)` — `POST /api/auth/oauth/{provider 소문자}/login` 본문 `{"code":"any-code","state":"{state}"}`. 가짜 서버는 어떤 `code`도 받아 준다.

#### `member/web/OAuthLoginTest`

**1. 신규 소셜 로그인부터 가입까지** — 준비: `oauth.givenUser(KAKAO, "123456789", "social@test.com")`.
- 동작 1: `oauthLogin(KAKAO, STATE)`.
- 확인 1: 200, `$.status` = `SIGNUP_REQUIRED`, `$.email` = `social@test.com`, `$.signupToken`이 비어 있지 않음(`T`), `$.token`이 없음/null. `h = redisHashOf("auth:oauth-signup:" + sha256Hex(T))`: `h.exists()` = true, `h.ttlSeconds()` 1~600. `countOf("member.member")` = 0, `countOf("member.social_account")` = 0.
- 동작 2: `V = testFixtures.verificationToken("social@test.com")` → `POST /api/auth/oauth/signup` 본문 `{"signupToken":"{T}","nickname":"socialnick","name":"소셜","verificationToken":"{V}"}`.
- 확인 2: 201, `$.accessToken`·`$.refreshToken` 있음. `parse($.accessToken)`: `role()` = `USER`, `memberId()` = `memberIdOf("social@test.com")`.
  - `countOf("member.member")` = 1, `m = memberSnapshotOf(memberIdOf("social@test.com"))`: `m.passwordHash()` null, `m.nickname()` = `socialnick`.
  - `countOf("member.social_account")` = 1이고 `socialAccountsOf(그 회원)`의 1행이 `provider` = `KAKAO`, `providerUserId` = `123456789`.
  - `redisHashOf("auth:oauth-signup:" + sha256Hex(T)).exists()` = false, `redisStringOf("auth:signup-verified:" + sha256Hex(V)).exists()` = false, `s = sessionSnapshotOf(sid)`(`sid` = `RefreshToken.parse($.refreshToken).sessionId()`): `s.memberId()` = 그 회원.
  - `GET /api/members/me`를 `Bearer {$.accessToken}`으로 → 200.

**2. 같은 소셜 계정으로 다시 로그인** — 준비: 1번의 로그인·가입 동작을 `socialSignup(KAKAO, "123456789", "social@test.com")`로 한 번 수행해 가입해 둔다(1번의 확인은 하지 않는다) → `memberId`.
- 동작: `oauthLogin(KAKAO, STATE)`.
- 확인: 200, `$.status` = `LOGGED_IN`. `parse($.token.accessToken).memberId()` = `memberId`. `$.token.refreshToken`의 세션 `s = sessionSnapshotOf(sid)`가 있고(`s.exists()`) `s.memberId()`가 같다.

**3. 같은 이메일의 이메일 가입 계정이 있으면 가입 거부** — 준비: `testFixtures.signup("dup@test.com")` → `existingId`(비밀번호 `pass1234`). `oauth.givenUser(KAKAO, "kakao-dup", "dup@test.com")`.
- 동작: `oauthLogin` → `SIGNUP_REQUIRED`(`T`) → `V = testFixtures.verificationToken("dup@test.com")` → `POST /api/auth/oauth/signup` 본문 `{"signupToken":"{T}","nickname":"socialnick","name":"소셜","verificationToken":"{V}"}`.
- 확인: 로그인 단계는 200 `SIGNUP_REQUIRED`. 가입은 409, `$.code` = `MEMBER_EMAIL_DUPLICATED`. `countOf("member.member")` = 1, `memberSnapshotOf(existingId).passwordHash()`는 요청 전 값 그대로, `countOf("member.social_account")` = 0.

**4. 제공자 장애** — `@ParameterizedTest` 입력: `Behavior.delay(6000)`, `Behavior.serverError()`. 준비: `oauth.givenUser(KAKAO, "kakao-1", "a@test.com")`, `oauth.failWith(KAKAO, 입력)`.
- 동작: `oauthLogin(KAKAO, STATE)`.
- 확인: 502, `$.code` = `OAUTH_PROVIDER_ERROR`. `countOf("member.member")`·`countOf("member.social_account")` = 0, `redis.keys("auth:oauth-signup:*")` 비어 있다.

**5. 세 제공자의 응답 파싱** — `@ParameterizedTest` 입력: `(KAKAO, "123456789")`, `(GOOGLE, "google-sub-1")`, `(NAVER, "naver-id-1")`. 준비: `oauth.givenUser(p, id, p.name().toLowerCase() + "@test.com")`.
- 동작: `oauthLogin(p, STATE)`.
- 확인: 로그인 200, `$.status` = `SIGNUP_REQUIRED`, `$.email` = `{소문자 제공자}@test.com`. `h = redisHashOf("auth:oauth-signup:" + sha256Hex($.signupToken))`: `h.fields()`의 `provider` = `p.name()`, `providerUserId` = `id`, `email` = 위 이메일.

**6. 이메일을 주지 않는 제공자** — 준비: `oauth.givenUser(KAKAO, "kakao-noemail", null)`.
- 동작 1: `oauthLogin(KAKAO, STATE)` → 200, `$.status` = `SIGNUP_REQUIRED`, `$.email` 없음/null. `redisHashOf("auth:oauth-signup:" + sha256Hex(T)).fields().keySet()`이 정확히 `provider`, `providerUserId` 둘이다.
- 동작 2: `V = testFixtures.verificationToken("typed@test.com")` → 가입 본문 `{"signupToken":"{T}","email":"typed@test.com","nickname":"socialnick","name":"소셜","verificationToken":"{V}"}` → 201. `memberIdOf("typed@test.com")`가 null이 아니다.

**7. 다른 이메일의 인증 토큰으로는 가입이 거부된다** — 준비: `oauth.givenUser(KAKAO, "123456789", "social@test.com")`, `oauthLogin(KAKAO, STATE)`의 `$.signupToken`(`T`), `V = testFixtures.verificationToken("other@test.com")`.
- 동작: `verificationToken` = `V`로 가입(이메일은 가입 토큰의 `social@test.com`).
- 확인: 400, `$.code` = `VERIFICATION_TOKEN_INVALID`. `countOf("member.member")`·`countOf("member.social_account")` = 0. `redisHashOf("auth:oauth-signup:" + sha256Hex(T)).exists()` = true.

**8. 같은 소셜 계정의 제공자 이메일이 바뀌어도 같은 회원으로 로그인한다** — 준비: 2번과 같다(`socialSignup(KAKAO, "123456789", "social@test.com")`). 동작: `oauth.givenUser(KAKAO, "123456789", "changed@test.com")` 후 `oauthLogin(KAKAO, STATE)`.
- 확인: 200, `$.status` = `LOGGED_IN`, 같은 `memberId`.

**9. 정지 회원의 소셜 로그인은 거부한다** — 준비: 2번과 같다. 동작: `changeStatus(memberId, "BANNED")` 후 `oauthLogin(KAKAO, STATE)`.
- 확인: 403, `$.code` = `MEMBER_BANNED`. 요청 전후 `sessionIdsOf(memberId).size()`가 같다.

**10. `state`가 없거나 빈 문자열이면 400 `INVALID_REQUEST`** — 동작: 본문에 `state`가 없는 `{"code":"x"}`와 `state`가 빈 문자열인 요청을 `POST /api/auth/oauth/kakao/login`으로 보낸다.
- 확인: 모두 400, `$.code` = `INVALID_REQUEST`. `countOf("member.member")`·`countOf("member.social_account")`는 0이다.

**11. 알 수 없는 제공자는 400** — 동작: `POST /api/auth/oauth/facebook/login`.
- 확인: 400, `$.code` = `INVALID_REQUEST`.

**12. 이메일이 어디에도 없으면 가입을 거부한다** — 준비: 6번의 준비와 동작 1(`oauthLogin(KAKAO, STATE)` → `T`)까지 같다. 동작: 본문의 `email` 없이 가입 요청(`verificationToken`은 `testFixtures.verificationToken("typed@test.com")`).
- 확인: 400, `$.code` = `INVALID_REQUEST`, `countOf("member.member")` = 0.

**13. 가입 토큰이 없으면 가입을 거부한다** — 준비: `oauth.givenUser(KAKAO, "123456789", "social@test.com")`, `oauthLogin(KAKAO, STATE)`의 `$.signupToken`(`T`), `V = testFixtures.verificationToken("social@test.com")`. 동작: `redis.delete("auth:oauth-signup:" + sha256Hex(T))` 후 가입.
- 확인: 401, `$.code` = `SIGNUP_TOKEN_INVALID`. `countOf("member.member")` = 0.

**14. 소셜 가입 회원의 내 정보에는 비밀번호가 없다** — 준비: 2번과 같다(`socialSignup(KAKAO, "123456789", "social@test.com")` → `memberId`), `oauthLogin(KAKAO, STATE)` → `$.token.accessToken`. 동작: `GET /api/members/me`를 그 토큰으로.
- 확인: 200, `$.hasPassword` = false, `$.socialAccounts`가 1건이고 `$.socialAccounts[0].provider` = `KAKAO`, `$.socialAccounts[0].linkedAt`이 비어 있지 않다.

**15. 탈퇴한 회원의 소셜 계정으로 다시 가입한다** — 준비: 2번과 같다(`socialSignup(KAKAO, "123456789", "social@test.com")` → `memberId`), 로그인해 둔 access 토큰. 동작 1: `DELETE /api/members/me`로 탈퇴(가게·주문이 없어 막히지 않는다).
- 확인 1: 204. `countOf("member.social_account")` = 0.
- 동작 2: `oauthLogin(KAKAO, STATE)`.
- 확인 2: 200, `$.status` = `SIGNUP_REQUIRED`(401 `LOGIN_FAILED`가 아니다).

#### `member/web/SocialLinkTest`

**공통 준비**: 이메일 회원 `link@test.com`(`signup`) → `memberId`, `token = login("link@test.com")`. 연결 요청은 `POST /api/members/me/social-accounts/kakao` 본문 `{"code":"any-code","state":"test-state"}`, `Bearer {token}`.

**1. 연결 후 그 계정으로 로그인** — 준비: `oauth.givenUser(KAKAO, "kakao-9", "other@test.com")`.
- 동작 1: 연결 요청.
- 확인 1: 204, 본문 없음. `countOf("member.social_account")` = 1이고 `socialAccountsOf(memberId)`의 1행이 `provider` = `KAKAO`, `providerUserId` = `kakao-9`. `memberSnapshotOf(memberId).email()` = `link@test.com` 그대로다.
- 동작 2: `oauthLogin(KAKAO, STATE)`.
- 확인 2: 200, `$.status` = `LOGGED_IN`, `parse($.token.accessToken).memberId()` = `memberId`.

**2. 다른 회원이 이미 연결한 소셜 계정은 거부한다** — 준비: `oauth.givenUser(KAKAO, "kakao-9", "other@test.com")`, 연결 요청을 보내 `link@test.com` 회원이 `kakao-9`를 연결해 둔다(204). 두 번째 회원 `link2@test.com`(`signup`) → `member2Id`, `token2 = login("link2@test.com")`.
- 다른 회원이 같은 소셜 계정을 연결: `oauth.givenUser(KAKAO, "kakao-9", …)` 그대로 `Bearer {token2}` 연결 → 409, `$.code` = `SOCIAL_ACCOUNT_ALREADY_LINKED`. `countOf("member.social_account")` = 1이고 그 1행이 `socialAccountsOf(memberId)`의 것이다.

**3. 로그인 없이 연결하면 거부한다** — 준비: 1번과 같다(`oauth.givenUser(KAKAO, "kakao-9", "other@test.com")`). 동작: `Authorization` 없이 연결 요청.
- 확인: 401, `$.code` = `UNAUTHORIZED`. `countOf("member.social_account")`가 늘지 않았다.

**4. 같은 회원이 같은 제공자를 다른 계정으로 연결하면 거부한다** — 준비: 2번과 같다. 동작: `oauth.givenUser(KAKAO, "kakao-10", …)`, `Bearer {token}` 연결.
- 확인: 409, `$.code` = `SOCIAL_ACCOUNT_ALREADY_LINKED`. `socialAccountsOf(memberId)`는 1행 그대로.

**5. 다른 제공자는 연결된다** — 준비: 2번과 같다. 동작: `oauth.givenUser(GOOGLE, "google-9", …)`, `POST /api/members/me/social-accounts/google`.
- 확인: 204. `socialAccountsOf(memberId)`의 `provider`가 [`KAKAO`, `GOOGLE`] 2행이다.

**6. 내 정보에 연결한 소셜 계정이 연결한 순서로 나온다** — 준비: 5번의 동작까지(`KAKAO` 다음 `GOOGLE` 연결). 동작: `GET /api/members/me`(`Bearer {token}`).
- 확인: 200, `$.hasPassword` = true, `$.socialAccounts[*].provider` = [`KAKAO`, `GOOGLE`].

**7. 연결한 소셜 계정이 없으면 빈 목록** — 동작: 공통 준비 직후 `GET /api/members/me`(`Bearer {token}`).
- 확인: 200, `$.socialAccounts`가 빈 배열이다.

**리뷰 때 물어볼 것**
- OAuth `state` 파라미터는 무엇을 막나? `state`를 서버 Redis에 저장해 비교하면 왜 막지 못하나?
- 이메일이 같으면 자동으로 연결하면 어떤 보안 문제가 생기나?
- 제공자별 차이를 if 문이 아니라 전략으로 나눈 이유는?

---

## 4-4. 즉시 차단(tokenVersion) · 관리자 제재 · 로그인 실패 잠금 (M)

**목표**: 제재·탈퇴·전체 로그아웃·비밀번호 변경 시 기존 Access 토큰이 바로 막힌다. 관리자가 회원을 제재·해제한다. 비밀번호를 5번 틀리면 15분 잠긴다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| tokenVersion | 회원마다 숫자 하나. 토큰에 발급 당시 버전(`tv`)을 넣고, 필터가 현재 버전과 다르면 거부 (ADR-006) |
| 커밋 후 반영 | DB 커밋이 성공한 뒤에만 Redis를 바꾼다(`TransactionSynchronization.afterCommit`). 롤백됐는데 Redis만 바뀌는 일을 막는다 |
| 포트 (의존 역전) | 필터(common)는 회원 모듈을 모른다. `common.security.TokenVersionProvider` 인터페이스를 member가 구현한다 |

**정책 (이 단계에서 정함)**
- 버전을 올리는 경우: 제재, 탈퇴(3-6), 전체 로그아웃, 비밀번호 변경·재설정. 버전을 올릴 때 세션(refresh)도 함께 폐기한다(비밀번호 변경은 현재 세션 제외).
- 필터는 매 요청 Redis에서 현재 버전을 읽는다(로컬 캐시 없음). 버전이 다르면 401 `UNAUTHORIZED`.
- Redis 장애 시 특별한 처리를 하지 않는다(인증이 필요한 요청은 500으로 실패). 장애 정책은 추가 항목으로 둔다.
- 로그인 잠금: 같은 이메일로 연속 5번 실패하면 15분 동안 423. 존재하지 않는 이메일도 똑같이 센다(가입 여부 노출 방지). 성공하면 카운트 삭제.
- 관리자 검색: 이메일 또는 닉네임 **앞부분 일치**, 상태 필터, 커서 페이징.

### 할 일

**구현 순서**: ① 마이그레이션(1) → ② common.security(2): `TokenVersionProvider` → `JwtProvider`·`AuthClaims`의 `tv` 클레임 → `JwtAuthenticationFilter` 변경 → ③ member.domain(3): `Member`·`MemberErrorCode`·`MemberRepository` 검색 조회·`LoginAttemptStore` → ④ member.api.event(4): `MemberTopics`·`MemberBannedEvent` → ⑤ member.infrastructure(5): `RedisTokenVersionProvider` → `MemberTopicConfig` → `RedisLoginAttemptStore` → ⑥ member.application(6): `TokenIssuer` 변경 → `TokenVersionService` → 호출 위치 수정(`WithdrawService`·`LogoutService`·`PasswordService`) → `AdminMemberService` → `LoginService` 변경 → ⑦ member.web(7) → ⑧ notification(8) → ⑨ ADMIN 토큰 헬퍼(완료 확인의 테스트 준비 표) → 테스트.

**1) 마이그레이션** — `db/migration/member/`

- `V{...}__member_add_token_version.sql`: `member.member`에 `token_version int NOT NULL DEFAULT 0`
- `V{...}__member_add_ban_reason.sql`: `member.member`에 `ban_reason varchar(200) NULL`

**2) common.security**

| 클래스 | 규격 |
|---|---|
| `TokenVersionProvider` | 인터페이스 `int currentVersion(UUID memberId)` — "이 회원의 지금 버전"을 알려 주는 포트. 인터페이스만 common에 두고 구현은 member의 `RedisTokenVersionProvider`다 |
| `JwtProvider`·`AuthClaims` | 클레임 `tv`(tokenVersion) 추가: `issue(memberId, role, sessionId, int tokenVersion)`, `AuthClaims(..., int tokenVersion)`. 토큰에는 `.claim("tv", tokenVersion)`으로 넣고 `parse`는 `claims.get("tv", Integer.class)`로 읽는다 |
| `JwtAuthenticationFilter` | 지금은 `parse`가 성공하면 무조건 인증을 설정한다. 이제는 토큰의 버전이 현재 버전과 같을 때만 설정한다. 변경 순서는 표 아래 |

`JwtAuthenticationFilter` 변경 순서
1. 필터 생성자에 `TokenVersionProvider`를 더한다. 필터는 `SecurityConfig`가 `new JwtAuthenticationFilter(jwtProvider)`로 직접 만들 뿐 Spring 빈이 아니라서 자동 주입되지 않는다. `securityFilterChain(...)`의 인자로 `TokenVersionProvider tokenVersionProvider`를 받아 `new JwtAuthenticationFilter(jwtProvider, tokenVersionProvider)`로 넘긴다
2. `parse(token).ifPresent(claims -> { ... })` 안에서 `claims.tokenVersion() == tokenVersionProvider.currentVersion(claims.memberId())`일 때만 `SecurityContextHolder`에 인증을 넣는다. 다르면 아무것도 하지 않는다
3. 인증이 없으면 `authenticated()` 규칙에서 `RestAuthenticationEntryPoint`가 401 `UNAUTHORIZED`를 응답한다. 공개 경로(`/api/auth/**` 등)는 낡은 토큰이 헤더에 있어도 그대로 통과한다

- 기존 테스트: `issue` 호출(`JwtProviderTest` 2곳, `MemberControllerTest` 1곳)에 인자를 더한다

**3) member.domain**

| 클래스 | 규격 |
|---|---|
| `Member` | tokenVersion — `@Column(name = "token_version", nullable = false) private int tokenVersion;`, `void increaseTokenVersion()`(`tokenVersion++`). 제재 — `@Column(name = "ban_reason") String banReason`. `ban(String reason)`: `ACTIVE → BANNED`(`status.transitTo`), `banReason = reason` / `unban()`: `BANNED → ACTIVE`, `banReason = null`. `verifyCanLogin()` 변경: BANNED이면 `new BusinessException(MEMBER_BANNED, Map.of("reason", banReason))`로 사유를 담는다. 사유가 `null`(이 단계 전에 BANNED가 된 행)이면 `details`를 비운다(`Map.of`는 `null` 값을 받으면 예외다). 로그인·토큰 갱신(4-1)·소셜 로그인(4-3)이 모두 이 메서드로 거절하고, 셋 다 비밀번호·refresh·제공자 인증을 통과한 뒤라 본인에게만 사유가 보인다 |
| `MemberErrorCode` | `ACCOUNT_LOCKED`(423, 공통 규칙 U)를 추가한다 |
| `MemberRepository` | 검색 조회: 이메일 **또는** 닉네임의 앞부분 일치(`like :prefix%`, 사용자 입력의 `%`·`_`는 이스케이프), 상태 필터(없으면 전체), `Cursor(createdAt, id)` 커서로 `size + 1`건. 정렬은 `createdAt desc, id desc`. 대소문자를 구분하지 않는다(이메일은 소문자로 정규화해 저장하므로 서비스가 `query`를 소문자로 바꿔 넘기고, 쿼리는 `lower(email)`·`lower(nickname)`과 비교한다). 메서드는 첫 페이지와 다음 페이지를 나눈 두 개다(`OrderRepository`와 같은 방식): `findForAdminFirstPage(String keyword, MemberStatus status, Limit limit)`, `findForAdminNextPage(String keyword, MemberStatus status, Instant cursorCreatedAt, UUID cursorId, Limit limit)`. `keyword`·`status`가 `null`이면 그 조건은 걸지 않는다 |
| `LoginAttemptStore` | 인터페이스 — 이메일별 연속 로그인 실패 횟수를 Redis(`auth:login-fail:{email}`)에 세어 잠금 여부를 알려 준다. `LoginService`가 쓴다. 이메일은 정규화한 값이다. 메서드는 아래 표 |

`LoginAttemptStore` 메서드

| 메서드 | 하는 일 | 입력 → 반환 |
|---|---|---|
| `Optional<Instant> lockedUntil(String email)` | 지금 잠겨 있는지, 언제 풀리는지 | `get`으로 횟수를 읽어 5 미만이거나 키가 없으면 `Optional.empty()`. 5 이상이면 `getExpire(key, SECONDS)`로 남은 초를 읽어 `clock.instant().plusSeconds(남은 초)` |
| `void recordFailure(String email)` | 실패 1회를 센다 | `increment(key)` 결과가 1이거나 5(잠금 임계 도달)이면 `expire(key, 900초)`를 건다. 그 밖의 실패는 TTL을 바꾸지 않는다 |
| `void reset(String email)` | 횟수를 지운다 | `delete(key)` |

- **5번째 실패에 TTL을 다시 거는 이유**: 그래서 잠금은 5번째 실패 시각부터 15분 동안 유지된다(정책의 "15분 동안 423"). 첫 실패 TTL만 쓰면 천천히 실패한 사용자는 5번째 실패 직후 몇 분 만에 풀린다

**4) member.api.event**

| 클래스 | 규격 |
|---|---|
| `MemberTopics` | `MEMBER_BANNED = "member.member-banned.v1"` 상수를 더한다 |
| `MemberBannedEvent` | `MemberBannedEvent(UUID memberId, String reason)` record |

**5) member.infrastructure**

| 클래스 | 규격 |
|---|---|
| `RedisTokenVersionProvider` | `@Component`. `TokenVersionProvider` 구현. 의존성: `StringRedisTemplate`, `MemberRepository`. **매 인증 요청마다** 불리므로 가볍게 만든다. `int currentVersion(UUID memberId)`의 순서는 표 아래 |
| `MemberTopicConfig` | 토픽마다 `@Bean NewTopic` 두 개가 있다(본 토픽과 `.dlt`. 예: `TopicBuilder.name(MemberTopics.MEMBER_REGISTERED).partitions(3).replicas(1).build()`와 `name(MemberTopics.MEMBER_REGISTERED + ".dlt")`). `MEMBER_BANNED`용으로 같은 모양의 빈 두 개를 더한다. 알림 구독은 8) notification |
| `RedisLoginAttemptStore` | `LoginAttemptStore` 구현. 의존성: `StringRedisTemplate`, `Clock`. |

`RedisTokenVersionProvider.currentVersion` 순서
1. `redis.opsForValue().get("auth:token-version:" + memberId)` — 있으면 `Integer.parseInt`해 반환
2. 없으면(처음이거나 1일 TTL로 만료) `memberRepository.findById(memberId)`의 `tokenVersion`을 읽어 `set(key, String.valueOf(version), Duration.ofDays(1))` 후 반환. 회원 행이 없으면 `-1`을 반환한다(Redis에는 저장하지 않는다. 토큰의 `tv`는 0 이상이어서 어떤 토큰과도 맞지 않아 필터가 401 `UNAUTHORIZED`로 막는다)

**6) member.application**

| 클래스 | 메서드 |
|---|---|
| `TokenIssuer` | 변경: `issueNewSession`·`reissue`·`reissueAccess`가 `jwtProvider.issue(...)`의 `tokenVersion` 인자로 `member.getTokenVersion()`을 넘긴다 |
| `TokenVersionService` | `void bump(Member member)` |
| `WithdrawService` | `withdraw(UUID memberId)` 변경(아래 "호출 위치와 같이 하는 일") |
| `LogoutService` | `logout`의 `all=true` 변경(아래 "호출 위치와 같이 하는 일") |
| `PasswordService` | `change`·`confirmReset` 변경(아래 "호출 위치와 같이 하는 일") |
| `AdminMemberService` | `search`·`ban`·`unban` 3개. 규격은 아래 표 |
| `LoginService` | `login` 변경(아래 "`LoginService.login` 변경") |

`TokenVersionService` (`@Service`) — **버전을 올리는 유일한 곳.** `bump`는 **반드시 호출자의 `@Transactional` 안에서** 부른다(트랜잭션이 없으면 아래 `registerSynchronization`이 `IllegalStateException`을 던진다). 순서
1. `member.increaseTokenVersion()` — DB 값은 호출자 트랜잭션이 커밋될 때 UPDATE로 나간다
2. `int version = member.getTokenVersion()`
3. `TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { redis.opsForValue().set("auth:token-version:" + memberId, String.valueOf(version), Duration.ofDays(1)); } })` — 롤백되면 `afterCommit`이 불리지 않아 Redis가 그대로다
- `afterCommit`에서 Redis 쓰기가 실패하면 DB만 올라가고 Redis는 옛 값이 남아, 그 키의 TTL(1일)이 끝날 때까지 이전 토큰이 통과한다. 이 단계는 처리하지 않는다(Redis 장애 정책은 아래 추가 항목)

호출 위치와 같이 하는 일
- `WithdrawService.withdraw(UUID memberId)`(`@Transactional`, 3-6의 탈퇴 유스케이스): 3-6이 하는 `member.withdraw` 등에 더해 `bump(member)` + `sessionStore.deleteAllOf(memberId)`
- `LogoutService(all=true)`: 이제 회원을 읽어야 하므로 `@Transactional`을 붙이고 `bump(member)` + 기존 `deleteAllOf`
- `PasswordService.change`: `bump` + 기존 `deleteAllOfExcept` + 새 토큰 발급(**bump 뒤의** `member.getTokenVersion()`으로 만들어야 응답 토큰이 막히지 않는다). `confirmReset`: `bump` + 기존 `deleteAllOf`
- `AdminMemberService.ban`: 아래

`AdminMemberService` (`@Service`) — **관리자가 회원을 찾고 제재·해제하는 유스케이스.** 의존성: `MemberRepository`, `TokenVersionService`, `SessionStore`, `OutboxPublisher`(3-1)

| 메서드 | 하는 일 | 입력 → 반환 |
|---|---|---|
| `@Transactional(readOnly = true) CursorPage<AdminMemberResult> search(String query, MemberStatus status, String cursor, int size)` | 관리자 회원 검색 | `query`(없으면 조건 없음), `status`(없으면 전체), 커서 → 한 페이지 |
| `@Transactional void ban(UUID memberId, String reason)` | 회원을 제재한다 | 아래 순서. 없는 회원은 404 `MEMBER_NOT_FOUND`, 이미 BANNED·WITHDRAWN이면 `status.transitTo`가 409 `INVALID_STATE_TRANSITION` |
| `@Transactional void unban(UUID memberId)` | 제재를 푼다 | `member.unban()`. BANNED가 아니면 409 `INVALID_STATE_TRANSITION` |

- `search`: 커서 페이징이다. `cursor`가 없으면 첫 페이지이고, 있으면 `Cursor.decode(cursor)`(`common.web.Cursor(Instant createdAt, UUID id)`)로 푼 값 뒤부터 `size + 1`건을 읽는다(`MemberRepository`의 검색 조회). `size`건보다 많이 읽혔으면 목록을 `size`건으로 자르고 그 페이지 마지막 항목의 `new Cursor(createdAt, id).encode()`를 `nextCursor`로 한다. 결과는 `new CursorPage<>(items, nextCursor)`(`common.web.CursorPage(List<T> items, String nextCursor)`, 다음 페이지가 없으면 `nextCursor`는 `null`)다. `AdminMemberResult`는 응답과 같은 필드다
- `ban` 순서
  1. `member.ban(reason)`
  2. `tokenVersionService.bump(member)` — 기존 access를 바로 막는다
  3. `sessionStore.deleteAllOf(memberId)` — refresh도 끊는다
  4. `outboxPublisher.publish(MemberTopics.MEMBER_BANNED, "MEMBER", memberId, new MemberBannedEvent(memberId, reason))` — 트랜잭션 안에서 상태 변경과 함께 발행한다(`OutboxPublisher`는 트랜잭션이 없으면 예외다)

`LoginService.login` 변경 — 기존 `findByEmail` → 비밀번호 확인 → `verifyCanLogin` → 토큰 발급 흐름에 세 군데를 더한다. 의존성에 `LoginAttemptStore`를 더한다.
1. 이메일 정규화 직후 `loginAttemptStore.lockedUntil(email)`이 있으면 `throw new BusinessException(ACCOUNT_LOCKED, Map.of("unlockAt", unlockAt.toString()))` → 423, `details.unlockAt`(`Instant`를 그대로 넣어도 되지만 직렬화 설정에 따라 숫자가 될 수 있어 `toString()`이 확실하다). 비밀번호를 확인하기 **전에** 막으므로 잠긴 동안은 올바른 비밀번호도 423이고 실패 횟수도 더 늘지 않는다
2. 회원이 없거나, `passwordHash`가 `null`이거나, `matches`가 `false`이면 `loginAttemptStore.recordFailure(email)`을 부른 **뒤에** 기존대로 401 `LOGIN_FAILED`를 던진다. Redis 호출은 예외로 트랜잭션이 롤백돼도 남는다
3. 비밀번호 확인을 통과하고 `verifyCanLogin`까지 통과한 뒤에 `loginAttemptStore.reset(email)`을 부른다(`verifyCanLogin`에서 막히는 BANNED 회원은 `reset`하지 않는다)

**7) member.web**

`/admin/api/members` (ADMIN)

| API | 요청 | 응답 |
|---|---|---|
| `GET /admin/api/members` | `?query=&status=&cursor=&size=` | `AdminMemberResponse(id, email, nickname, name, status, banReason, role, createdAt)`. `banReason`은 관리자가 해제를 판단할 때 보는 제재 사유이고 BANNED가 아니면 `null`이다 |
| `POST /admin/api/members/{id}/ban` | `BanRequest(@NotBlank @Size(max=200) String reason)` | 204 |
| `POST /admin/api/members/{id}/unban` | | 204 |

- ADMIN 인가는 3-4b에서 넣은 `common.security.SecurityConfig`의 `/admin/**` → `hasRole("ADMIN")`이 하며(일반 회원은 `RestAccessDeniedHandler`가 403), 컨트롤러는 따로 검사하지 않는다. 컨트롤러 클래스는 `AdminMemberController`(`@RequestMapping("/admin/api/members")`, `member.web`)다

**8) notification.domain·notification.application·notification.infrastructure**

| 클래스 | 규격 |
|---|---|
| `notification.domain.NotificationType` | 3-5의 알림 종류 enum(`notification.type` 컬럼 값)에 `BANNED`를 더한다 |
| `notification.application.NotificationComposer` | 3-5의 이벤트를 `List<NotificationDraft>`로 바꾸는 순수 변환(이벤트마다 오버로드 하나)에 `compose(MemberBannedEvent e)`를 더해 수신자 `e.memberId()`, type `BANNED`인 회원 알림 `NotificationDraft`(3-5의 `memberId` 쪽, `shopId`는 null) 하나를 돌려주게 한다 |
| `notification.infrastructure.NotificationEventConsumer` | 구독은 3-5의 `NotificationEventConsumer`(`groupId = "notification"`)에 메서드를 더해 한다: `@KafkaListener(topics = MemberTopics.MEMBER_BANNED, groupId = "notification")` → `typed = envelopes.parse(message, MemberBannedEvent.class)` → `inboxGuard.runOnce("notification.member-banned", typed.envelope(), () -> notificationService.enqueue(typed.envelope().eventId(), composer.compose(typed.payload())))`. |

- 알림 문구: 수신자 회원, type `BANNED`, 제목 `계정 이용이 제한되었습니다`, 본문 `운영 정책에 따라 계정 이용이 제한되었습니다. 사유: {reason}`(`MemberBannedEvent.reason`). 테스트는 3-5와 같이 본문을 `isNotBlank()`로만 확인한다

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 이 단계에서 더하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `adminToken(String email)` → `String` | `signup(email)` → 3-1의 `changeRole(memberId, "ADMIN")` → `login(email)`(ADMIN은 DB에서 `role`을 바꿔 만들고, 바꾼 뒤 다시 로그인해야 토큰에 `ADMIN`이 담긴다) | `TokenRevocationTest`, `AdminMemberTest` |
| `memberSnapshotOf(UUID memberId)`에 `banReason` 추가 | 4-2까지 넓힌 `MemberSnapshot`의 SELECT에 `ban_reason`을 더하고 record에 `banReason`(`String`, 제재 사유가 없으면 `null`)을 더한다. 컬럼은 이 단계의 마이그레이션이 만든다 | `TokenRevocationTest` 1번, `AdminMemberTest` |
| `tokenVersionSnapshotOf(UUID memberId)` → `TokenVersionSnapshot` | DB `SELECT token_version FROM member.member WHERE id = ?`와 Redis `redis.opsForValue().get("auth:token-version:" + memberId)`·`redis.getExpire(key, TimeUnit.SECONDS)`를 한 번에 읽는다. `support/`의 record(`int db`, `Integer redis`(키가 없으면 `null`), `long redisTtlSeconds`(키가 없으면 -2)) | `TokenRevocationTest`, `RedisTokenVersionProviderTest` |

이미 있는 헬퍼는 다음과 같다.
- `TestFixtures`: `UUID signup(String email)`(비밀번호 `pass1234`), `String login(String email)`(`pass1234`로 로그인한 access 토큰), `void changeStatus(UUID memberId, String status)`와 `void changeRole(UUID memberId, String role)`(`member.member`의 `status`·`role` 컬럼을 JDBC로 바꾼다), `SessionSnapshot sessionSnapshotOf(UUID)`·`Set<UUID> sessionIdsOf(UUID)`(Redis 세션), `MemberSnapshot memberSnapshotOf(UUID)`, `RedisString redisStringOf(String key)`(4-2).
- `FakeMailSender`: `List<SentMail> sent()`로 보낸 메일을 읽는다.
- `TestFixtures.notificationRowsOf(UUID memberId)` → `List<NotificationRow>`: 그 회원의 알림 행을 생성 순으로 읽는다. 한 행은 `memberId()`, `type()`, `title()`, `status()` 등을 가진다. `outboxRelay.relayOnce()`는 `IntegrationTestSupport`의 `outboxRelay`로 outbox 행을 Kafka로 보내는 호출이다.
- `OutboxAssert.rows(String topic)` → `List<OutboxSnapshot>`: 그 토픽의 outbox 행을 생성 순으로 읽는다. 한 행은 `id()`, `aggregateId()`, `aggregateType()`, `eventType()`, `topic()`, `status()`, `payload()`(`JsonNode`) 등을 가진다.
- `Eventually.await(Duration timeout, ThrowingRunnable assertion)`: 확인 코드가 예외 없이 끝날 때까지 100ms 간격으로 되풀이하고, 제한 시간이 지나면 마지막 실패를 던진다.

헬퍼를 쓰는 방법은 다음과 같다.
- tokenVersion: 토큰 안의 값은 `parse(accessToken).orElseThrow().tokenVersion()`.
- 로그인 잠금: `redisStringOf("auth:login-fail:" + email)`의 `value()`(문자열 횟수, 키가 없으면 `null`)·`ttlSeconds()`·`exists()`. 로그인 요청은 `POST /api/auth/login` 본문 `{"email":"…","password":"…"}`.
- 시간 의존: 잠금 15분 경과는 `redis.expire(키, Duration.ofSeconds(1))` 후 `Eventually.await(Duration.ofSeconds(5), () -> assertThat(testFixtures.redisStringOf(키).exists()).isFalse())`(Part 3 `support/Eventually`)로 키가 사라질 때까지 기다린다. tokenVersion 키의 1일 경과는 `redis.delete(키)`로 흉내 낸다.
- 요청에 쓰는 토큰은 `testFixtures.login(email)`(access만)이나 4-1의 `testFixtures.loginTokens(email)`(access·refresh)로 얻는다. 로그인 시점의 `tv`가 그 토큰에 들어간다.

**공통 준비**: 관리자 `admin@test.com`(`adminToken("admin@test.com")`), 일반 회원 `target@test.com`(`signup`, 비밀번호 `pass1234`) → `targetId`. 관리자 API는 `Bearer {adminToken}`이다.

#### `member/web/TokenRevocationTest`

**1. 제재 → 기존 access 거부** — 준비: `loginTokens("target@test.com")` → `accessA`·`refreshA`·`sidA`. `parse(accessA).tokenVersion()` = 0.
- 동작: `POST /admin/api/members/{targetId}/ban`, `Bearer {adminToken}`, 본문 `{"reason":"스팸"}`.
- 확인: 204. `GET /api/members/me`를 `Bearer {accessA}`로 → 401, `$.code` = `UNAUTHORIZED`. `v = tokenVersionSnapshotOf(targetId)`: `v.db()` = 1, `v.redis()` = 1, `v.redisTtlSeconds()` 80000~86400. `sessionIdsOf(targetId)`가 비어 있다. 관리자 자신의 토큰으로 `GET /api/members/me` → 200.

**2. 전체 로그아웃 → 기존 access 거부** — 준비: `loginTokens(…)` → `accessA`·`refreshA`, `loginTokens(…)` → `accessB`·`refreshB`.
- 동작: `POST /api/auth/logout?all=true`, `Bearer {accessA}`.
- 확인: 204. `accessA`·`accessB`로 `GET /api/members/me` → 둘 다 401 `UNAUTHORIZED`. `tokenVersionSnapshotOf(targetId)`의 `db()` = 1, `redis()` = 1. 다시 `login(…)`한 토큰은 `tokenVersion()` = 1이고 `GET /api/members/me` → 200.

**3. 비밀번호 변경 → 기존 access 거부** — 준비: `loginTokens(…)` → `accessA`·`refreshA`·`sidA`, `loginTokens(…)` → `accessB`·`refreshB`.
- 동작: `PATCH /api/members/me/password` 본문 `{"currentPassword":"pass1234","newPassword":"newpass5678"}`, `Bearer {accessA}` → 응답의 `$.accessToken` = `newAccess`.
- 확인: 200. `GET /api/members/me`를 `Bearer {accessA}`·`Bearer {accessB}`로 → 둘 다 401 `UNAUTHORIZED`, `Bearer {newAccess}`로 → 200. `parse(newAccess)`: `tokenVersion()` = 1. `tokenVersionSnapshotOf(targetId)`의 `db()` = 1, `redis()` = 1.

**4. 회원 탈퇴 → 기존 access 거부** — 준비: 가게·주문이 없는 회원 `leave@test.com`을 `signup` → `leaveId`, `loginTokens(…)` → `accessA`·`refreshA`.
- 동작: `DELETE /api/members/me`, `Bearer {accessA}`.
- 확인: 204. `GET /api/members/me`를 `Bearer {accessA}`로 → 401 `UNAUTHORIZED`. `tokenVersionSnapshotOf(leaveId).db()` = 1.

**5. 롤백된 트랜잭션에서는 Redis가 바뀌지 않는다** — 준비: `login(…)` → `accessA`. `GET /api/members/me`(`Bearer {accessA}`)를 한 번 불러 Redis에 버전 0을 채운다. 테스트에 `TransactionTemplate tx`, `MemberRepository`, `TokenVersionService`를 주입한다.
- 동작 1(롤백): `tx.execute(status -> { Member m = memberRepository.findById(targetId).orElseThrow(); tokenVersionService.bump(m); status.setRollbackOnly(); return null; })`.
- 확인 1: `tokenVersionSnapshotOf(targetId)`의 `db()` = 0, `redis()` = 0. `GET /api/members/me`를 `Bearer {accessA}`로 → 200.
- 동작 2(대조, 커밋): 같은 코드에서 `status.setRollbackOnly()`만 뺀다.
- 확인 2: `tokenVersionSnapshotOf(targetId)`의 `db()` = 1, `redis()` = 1. `Bearer {accessA}`로 `GET /api/members/me` → 401 `UNAUTHORIZED`.

**6. `?all=false` 로그아웃은 tokenVersion을 올리지 않는다** — 준비: 다른 회원 `other@test.com`(`signup` → `otherId`, `login(…)` → `otherAccess`). 동작: `?all=false`로 로그아웃(`Bearer {otherAccess}`).
- 확인: 204. `tokenVersionSnapshotOf(otherId).db()` = 0 그대로.

**7. 비밀번호 재설정 → 기존 access 거부** — 준비: 3번과 같다(`accessA`·`accessB`, 3번의 동작으로 비밀번호를 변경해 `newAccess`를 얻은 상태).
- 동작: 재설정 — `POST /api/auth/password-reset`(본문 `{"email":"target@test.com"}`) → 메일에서 `code` → `POST /api/auth/password-reset/confirm`(본문 `{"email":"target@test.com","code":"{code}","newPassword":"resetpass99"}`).
- 확인: 204. `tokenVersionSnapshotOf(targetId)`의 `db()` = 2, `redis()` = 2. 변경에서 받은 `newAccess`로 `GET /api/members/me` → 401 `UNAUTHORIZED`.

**8. 트랜잭션 없이 `bump`를 부르면 예외** — 준비: 5번과 같다. 동작: `tokenVersionService.bump(member)`를 `tx` 밖에서 호출.
- 확인: `IllegalStateException`.

#### `member/infrastructure/RedisTokenVersionProviderTest`

**1. Redis에 값이 없으면 DB 값을 읽어 채운다** — 준비: `UPDATE member.member SET token_version = 3 WHERE id = ?`(`targetId`). `redis.delete("auth:token-version:" + targetId)`. `TokenVersionProvider provider`를 주입한다.
- 동작 1: `provider.currentVersion(targetId)`.
- 확인 1: 반환 `3`. `v = tokenVersionSnapshotOf(targetId)`: `v.redis()` = 3, `v.redisTtlSeconds()` 80000~86400.
- 동작 2: DB를 `token_version = 5`로 UPDATE하고 `redis.delete(키)` 후 `provider.currentVersion(targetId)`.
- 확인 2: 반환 `5`, `tokenVersionSnapshotOf(targetId).redis()` = 5.

**2. Redis에 값이 있으면 DB를 읽지 않는다** — 준비: 1번의 준비와 동작 1로 Redis를 `3`으로 채워 둔다. 동작: DB를 `token_version = 5`로 UPDATE한 뒤 `provider.currentVersion(targetId)`.
- 확인: 반환 `3`.

#### `member/web/AdminMemberTest`

**공통 준비**: 회원 5명을 만든다(`signup`, 닉네임은 이메일의 `@` 앞): `alice@test.com`, `alan@test.com`, `bob@test.com`, `carol@test.com`, 관리자 `admin@test.com`. `bob`은 `UPDATE member.member SET nickname = 'zebra' WHERE id = ?`로 닉네임을 바꾸고 `changeStatus(bobId, "BANNED")`. 목록은 `GET /admin/api/members?query=&status=&cursor=&size=`(`size` = 20), 항목은 `$.items[*]`(`id`, `email`, `nickname`, `name`, `status`, `banReason`, `role`, `createdAt`), 다음 페이지 표시는 `$.nextCursor`다.

**1. 일반 회원은 관리자 API를 쓸 수 없다** — 동작: `login("alice@test.com")` 토큰으로 `GET /admin/api/members`, `POST /admin/api/members/{bobId}/unban`.
- 두 요청 모두 403, `$.code` = `FORBIDDEN`. `bob`의 `status`는 `BANNED` 그대로.

**2. 검색** — 관리자 토큰으로 각각 호출한다.
- `query=al` → `$.items`의 `email`이 `alice@test.com`, `alan@test.com` 둘(순서 무관), `$.nextCursor` null.
- `query=bob` → `bob@test.com` 하나. `query=zeb` → 같은 `bob@test.com` 하나.
- `query=ice` → 0건. `query=%` → 0건.
- `query=AL` → 같은 두 건(`alice@test.com`, `alan@test.com`).

**3. 커서 페이징** — 동작: `size=2`로 호출 → `$.nextCursor`로 다음 호출 → 반복.
- 페이지 크기 2, 2, 1이고 마지막 페이지의 `$.nextCursor`는 null이다.
- 세 페이지의 `id`를 합치면 5명이 모두 한 번씩 나온다.

**4. 제재 → 로그인 거부 → 해제 → 로그인** — 준비: `alice`가 `loginTokens(…)` → `sidA`.
- 동작 1: `POST /admin/api/members/{aliceId}/ban`, 본문 `{"reason":"운영정책 위반"}`.
- 확인 1: 204. `m = memberSnapshotOf(aliceId)`: `m.status()` = `BANNED`, `m.banReason()` = `운영정책 위반`. `sessionSnapshotOf(sidA).exists()` = false. `OutboxAssert.rows("member.member-banned.v1")` 1건, `aggregate_id` = `aliceId`, `payload`의 `memberId` = `aliceId`, `reason` = `운영정책 위반`.
- 동작 2: `POST /admin/api/members/{aliceId}/unban`.
- 확인 2: 204. `memberSnapshotOf(aliceId)`: `status()` = `ACTIVE`, `banReason()` = null. 로그인 → 200, `$.accessToken` 있음.

**5. 상태 전이 규칙을 어긴 제재·해제 거부** — 모두 `Bearer {adminToken}`.
- 이미 제재된 회원 `bob`에게 ban(본문 `{"reason":"중복"}`) → 409, `$.code` = `INVALID_STATE_TRANSITION`. outbox `member.member-banned.v1` 0건.
- 정지가 아닌 회원 `alice`에게 unban → 409, `$.code` = `INVALID_STATE_TRANSITION`.

**6. 토큰 없이 관리자 API를 부르면 401** — 동작: 토큰 없이 `GET /admin/api/members`.
- 확인: 401, `$.code` = `UNAUTHORIZED`.

**7. 상태 필터** — 관리자 토큰으로 각각 호출한다.
- `status=BANNED` → `bob` 하나. `status=ACTIVE` → 4명. 상태 없이 → 5명.

**8. 목록 항목의 `role`** — 관리자 토큰으로 목록을 호출한다.
- 항목 값: `alice`의 `role` = `USER`, `admin`의 `role` = `ADMIN`.

**9. 제재 이벤트는 `BANNED` 알림이 된다** — 동작: `POST /admin/api/members/{aliceId}/ban`(`Bearer {adminToken}`, 본문 `{"reason":"운영정책 위반"}`) 후 `outboxRelay.relayOnce()`를 부른다.
- 확인: `Eventually.await(5초, ...)` 안에서 `notificationRowsOf(aliceId)`가 `type()` = `BANNED`인 행 1건이다.

**10. 없는 회원을 제재하면 404** — 동작: 없는 회원 ID로 ban(`Bearer {adminToken}`).
- 확인: 404, `$.code` = `MEMBER_NOT_FOUND`.

**11. 제재 사유가 비어 있으면 400** — 동작: `{"reason":""}`로 `alice`에게 ban(`Bearer {adminToken}`).
- 확인: 400, `$.code` = `INVALID_REQUEST`. `alice`의 `status`는 `ACTIVE` 그대로.

**12. 목록 항목의 제재 사유** — 준비: `POST /admin/api/members/{aliceId}/ban` 본문 `{"reason":"운영정책 위반"}`(204). 동작: 관리자 토큰으로 목록을 호출한다.
- 확인: `alice`의 `banReason` = `운영정책 위반`, `carol`의 `banReason` = null.

**13. 제재된 회원의 로그인 거절에 사유가 있다** — 준비: 12번과 같다. 동작: `POST /api/auth/login` 본문 `{"email":"alice@test.com","password":"pass1234"}`.
- 확인: 403, `$.code` = `MEMBER_BANNED`, `$.details.reason` = `운영정책 위반`.

**14. 사유 없이 BANNED가 된 회원의 로그인 거절** — 동작: `bob`으로 `POST /api/auth/login` 본문 `{"email":"bob@test.com","password":"pass1234"}`.
- 확인: 403, `$.code` = `MEMBER_BANNED`, `$.details.reason`이 없다.

#### `member/web/LoginLockTest`

**공통 준비**: 회원 `lock@test.com`(`signup`, 비밀번호 `pass1234`) → `memberId`. 키 `K` = `auth:login-fail:lock@test.com`. 실패 로그인은 비밀번호 `wrongpass1`, 성공 로그인은 `pass1234`. 가입 때 만든 세션의 수 `before` = `sessionIdsOf(memberId).size()`를 읽어 둔다.

**1. 5번 실패하면 올바른 비밀번호도 423** — 동작: 실패 로그인을 5번, 이어 올바른 비밀번호로 6번째.
- 앞의 5번: 매번 401, `$.code` = `LOGIN_FAILED`. 각 요청 직후 `redisStringOf(K).value()`가 `"1"`, `"2"`, …, `"5"`.
- 6번째: 423, `$.code` = `ACCOUNT_LOCKED`, `$.details.unlockAt`이 `Instant.parse`되고 `Duration.between(Instant.now(), unlockAt)`이 14분~15분이다.

**2. 시간이 지나면 풀린다** — 준비: 실패 로그인 5번 뒤 올바른 비밀번호로 6번째 시도가 423인 상태까지 만든다.
- 동작: `redis.expire(K, Duration.ofSeconds(1))` → `Eventually.await(5초, () -> redisStringOf(K).exists()가 false)` → 올바른 비밀번호로 로그인.
- 확인: 200, `$.accessToken`·`$.refreshToken` 있음.

**3. 존재하지 않는 이메일도 센다** — 준비: 가입하지 않은 `ghost@test.com`, 키 `auth:login-fail:ghost@test.com`.
- 동작: 틀린 비밀번호로 5번, 6번째 시도.
- 확인: 앞의 5번은 401 `LOGIN_FAILED`. 5번째 직후 `redisStringOf(키).value()` = `"5"`. 6번째: 423, `$.code` = `ACCOUNT_LOCKED`, `$.details.unlockAt` 있음.

**4. 성공하면 초기화** — 동작: 실패 4번 → 올바른 비밀번호 로그인 → 실패 4번.
- 올바른 로그인: 200, `redisStringOf(K).exists()` = false.
- 이어진 실패 4번 뒤 `redisStringOf(K).value()` = `"4"`.

**5. 잠긴 동안은 횟수를 늘리지 않고 세션을 만들지 않는다** — 준비: 1번의 실패 로그인 5번까지 같다. 동작: 올바른 비밀번호로 로그인, 이어 틀린 비밀번호로 한 번 더.
- 올바른 비밀번호: 423 `ACCOUNT_LOCKED`. `redisStringOf(K).value()` = `"5"` 그대로. 세션 수가 `before`와 같다.
- 틀린 비밀번호: 423 `ACCOUNT_LOCKED`, `redisStringOf(K).value()` = `"5"`.

**6. 잠금 시간은 첫 실패에 걸고 5번째 실패에서 다시 건다** — 동작: 실패 로그인 4번, `redis.expire(K, Duration.ofSeconds(60))`로 키의 남은 시간을 줄인 뒤 5번째 실패.
- 1번째 직후 `redisStringOf(K).ttlSeconds()` 1~900. 5번째 직후 `ttlSeconds()`가 890~900이다.

**추가 항목**
- 토큰 버전을 서버 메모리에 몇 초 캐시해 매 요청 Redis 조회를 줄이기(대가: 다른 서버에서 올린 버전이 그만큼 늦게 반영) — Stage 2 성능 측정 대상
- Redis 장애 정책: 캐시된 버전이 있으면 허용, 없으면 503 / 로그인 잠금 검사는 건너뛰고 허용
- 폐기된 토큰과 만료된 토큰에 서로 다른 401 코드(`TOKEN_REVOKED`, `TOKEN_EXPIRED`)

**리뷰 때 물어볼 것**
- 블랙리스트 방식(토큰마다 저장)과 tokenVersion 방식의 차이는?
- 매 요청 Redis를 조회하는 비용은? (Stage 2에서 측정)
- Redis에 먼저 쓰고 DB 커밋이 실패하면 어떤 일이 생기나? 그래서 afterCommit을 쓴 것이다

---

## Part 4 완료
- [ ] `v0.4.0` 릴리스, Part 5 문서 다듬기
