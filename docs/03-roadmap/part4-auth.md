# Part 4. 인증 완성

> 버전 0.5 · 2026-10-02 · **덜어내기**: 토큰 거부 코드는 401 `UNAUTHORIZED` 하나로(만료·폐기 구분은 제안), tokenVersion 로컬 캐시와 Redis 장애 정책 제거, 가입 토큰용 인증코드 발송은 별도 API로(선택적 리졸버 제거), 소셜 계정 중복은 사전 조회로
> 0.4 · 2026-10-02 · 예치금 제거 반영(충전 API·개발용 입금 API 관련 항목 삭제)
> 0.3 · 이 문서만 보고 개발할 수 있게 구체화(Redis 키 설계, 단계별 구현 규격·테스트 케이스)

> **끝나면**: refresh 토큰으로 로그인이 유지되고, 탈취된 토큰을 감지하며, 이메일 인증·OAuth 로그인·관리자 제재·로그인 실패 잠금이 동작한다.
> **인프라 추가**: **Redis**
> **릴리스**: `v0.4.0`

| 단계 | 제목 | 크기 |
|---|---|---|
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
- 비밀값(refresh 토큰, 인증코드)은 **원문을 저장하지 않고 SHA-256 해시**를 저장한다. 비교는 `MessageDigest.isEqual`(시간이 일정한 비교).
- 테스트: `IntegrationTestSupport`의 `@AfterEach`에서 `redis.getConnectionFactory().getConnection().serverCommands().flushDb()`.

| 키 | 타입 | 값 | TTL | 단계 |
|---|---|---|---|---|
| `auth:session:{sessionId}` | HASH | `memberId`, `refreshHash`, `device`, `createdAt`, `lastUsedAt` | 7일(갱신 때마다 연장) | 4-1 |
| `auth:member-sessions:{memberId}` | SET | sessionId 목록 | 7일(세션 생성·갱신 때 연장) | 4-1 |
| `auth:email-code:{purpose}:{email}` | HASH | `codeHash`, `attempts` | 5분 | 4-2 |
| `auth:email-cooldown:{email}` | STRING | `1` | 60초 | 4-2 |
| `auth:email-hourly:{email}` | STRING | 발송 횟수 | 1시간(첫 발송 때 설정) | 4-2 |
| `auth:oauth-state:{state}` | STRING | provider | 10분 | 4-3 |
| `auth:oauth-signup:{signupToken}` | HASH | `provider`, `providerUserId`, `email`, `nickname` | 10분 | 4-3 |
| `auth:token-version:{memberId}` | STRING | 버전 숫자 | 1일 | 4-4 |
| `auth:login-fail:{email}` | STRING | 연속 실패 횟수 | 15분(첫 실패 때 설정) | 4-4 |

### U. Part 4 에러 코드

| 코드 | HTTP | 메시지 | 정의 위치 | 단계 |
|---|---|---|---|---|
| `REFRESH_REUSED` | 401 | 이미 사용된 토큰입니다. 다시 로그인해 주세요. | `MemberErrorCode` | 4-1 |
| `EMAIL_NOT_VERIFIED` | 403 | 이메일 인증이 필요합니다. | `CommonErrorCode` | 4-2 |
| `RATE_LIMITED` | 429 | 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요. | `CommonErrorCode` | 4-2 |
| `EMAIL_CODE_INVALID` | 400 | 인증코드가 올바르지 않거나 만료되었습니다. | `MemberErrorCode` | 4-2 |
| `EMAIL_ALREADY_VERIFIED` | 409 | 이미 인증된 이메일입니다. | `MemberErrorCode` | 4-2 |
| `PASSWORD_MISMATCH` | 400 | 현재 비밀번호가 올바르지 않습니다. | `MemberErrorCode` | 4-2 |
| `OAUTH_STATE_INVALID` | 400 | 로그인 요청이 만료되었거나 올바르지 않습니다. | `MemberErrorCode` | 4-3 |
| `OAUTH_PROVIDER_ERROR` | 502 | 소셜 로그인 제공자와 통신하지 못했습니다. | `MemberErrorCode` | 4-3 |
| `SIGNUP_TOKEN_INVALID` | 401 | 가입 시간이 만료되었습니다. 다시 시도해 주세요. | `MemberErrorCode` | 4-3 |
| `SOCIAL_ACCOUNT_ALREADY_LINKED` | 409 | 이미 연결된 소셜 계정입니다. | `MemberErrorCode` | 4-3 |
| `ACCOUNT_LOCKED` | 423 | 로그인 시도가 많아 잠시 잠겼습니다. (details.unlockAt) | `MemberErrorCode` | 4-4 |

---

## 4-1. Redis · refresh 토큰 회전 · 로그아웃 (M)

**목표**: Access 토큰을 15분으로 줄이고, refresh 토큰으로 갱신한다. refresh는 쓸 때마다 바뀌고, 이미 쓴 refresh가 다시 오면 탈취로 보고 그 세션을 끊는다.

**왜 지금**: 1-4에서 남겨둔 한계(로그아웃 불가, 1시간 만료)를 해결한다. 서버가 상태(세션)를 가져야 하므로 Redis가 등장한다.

**새로 등장**

| 개념·도구 | 한 줄 설명 |
|---|---|
| Redis | 만료 시간(TTL)이 있는 빠른 저장소. 세션·인증코드·잠금 횟수에 쓴다 |
| 세션 = 로그인 한 번 | 로그인할 때마다 세션 하나(sessionId). 회전해도 sessionId는 그대로이고 refresh만 바뀐다. **세션이 곧 family**다 |
| refresh 회전 | refresh를 쓰면 새 refresh를 주고 이전 것은 무효 (ADR-006) |
| 재사용 탐지 | 무효가 된 refresh가 다시 오면 누군가 훔친 것 → 그 세션 폐기. 다른 기기(다른 세션)는 유지 |
| Lua 스크립트 | "해시가 맞으면 새 해시로 바꾼다"를 Redis 안에서 한 번에 실행(동시 갱신 경합 방지) |

**정책 (이 단계에서 정함)**
- Access 15분(`myroutine.jwt.access-token-ttl: 15m`), refresh 7일(`myroutine.jwt.refresh-token-ttl: 7d`).
- refresh 형식: `{sessionId}.{랜덤 32바이트 Base64URL}`. Redis에는 랜덤 부분의 SHA-256만 저장한다.
- 같은 refresh로 **동시에** 두 번 갱신하면 늦은 쪽은 재사용으로 판단해 세션을 폐기한다(OPEN-02 권장안: 엄격).
- 기기 정보는 `User-Agent`를 200자로 잘라 저장한다.

### 할 일

**1) 인프라**
- `docker-compose`: `redis:7.4` (확인 필요: 태그), `127.0.0.1:6379:6379`
- 의존성: `spring-boot-starter-data-redis`
- `IntegrationTestSupport`: `@ServiceConnection(name = "redis") static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4").withExposedPorts(6379);` → `Startables.deepStart(postgres, kafka, redis)`

**2) common.security 변경**
- `JwtProperties`에 `@NotNull Duration refreshTokenTtl` 추가
- `AuthClaims`에 `UUID sessionId` 추가 (`sid` 클레임) — 비밀번호 변경 시 "현재 세션만 남기기"에 쓴다
- `JwtProvider.issue(UUID memberId, String role, UUID sessionId)`로 변경

**3) member — 세션**

| 클래스 | 규격 |
|---|---|
| `RefreshToken` (domain) | `record (UUID sessionId, String secret)`. `static RefreshToken generate(UUID sessionId)`(SecureRandom 32바이트), `String value()`, `static RefreshToken parse(String raw)`(형식이 다르면 `BusinessException(UNAUTHORIZED)`), `String secretHash()` |
| `SessionStore` (domain 인터페이스) | `void create(UUID sessionId, UUID memberId, String refreshHash, String device, Duration ttl)`, `Optional<StoredSession> find(UUID sessionId)`, `boolean rotate(UUID sessionId, String expectedHash, String newHash, Duration ttl)`, `void delete(UUID sessionId)`, `void deleteAllOf(UUID memberId)`, `void deleteAllOfExcept(UUID memberId, UUID keepSessionId)` |
| `RedisSessionStore` (infrastructure) | 위 인터페이스 구현. `rotate`는 Lua 스크립트 |
| `TokenIssuer` (application, `@Component`) | `TokenResult issueNewSession(Member member, String device)`, `TokenResult reissue(Member member, UUID sessionId, RefreshToken refresh)` — access + refresh를 만들어 `TokenResult`로 |
| `TokenResult` | `record (String accessToken, long expiresIn, String refreshToken, long refreshExpiresIn)` |
| `TokenRefreshService` | `@Transactional(readOnly = true) TokenResult refresh(String rawRefresh)` |
| `LogoutService` | `void logout(UUID memberId, UUID sessionId, boolean all)` |
| `MemberErrorCode` | `REFRESH_REUSED` |

`rotate.lua`
```lua
-- KEYS[1] = auth:session:{id}, ARGV[1] = expectedHash, ARGV[2] = newHash, ARGV[3] = ttlSeconds, ARGV[4] = now
if redis.call('HGET', KEYS[1], 'refreshHash') == ARGV[1] then
  redis.call('HSET', KEYS[1], 'refreshHash', ARGV[2], 'lastUsedAt', ARGV[4])
  redis.call('EXPIRE', KEYS[1], ARGV[3])
  return 1
end
return 0
```

`TokenRefreshService.refresh` 순서
1. `refresh = RefreshToken.parse(raw)`
2. `session = sessionStore.find(sessionId)` 없으면 `UNAUTHORIZED` (만료됐거나 로그아웃됨)
3. `newRefresh = RefreshToken.generate(sessionId)` → `rotate(sessionId, refresh.secretHash(), newRefresh.secretHash(), ttl)` → **false면 재사용**: `sessionStore.delete(sessionId)` 후 `REFRESH_REUSED`
4. 회원 조회 → `member.verifyCanLogin()` 실패면 세션 삭제 후 그 예외
5. 새 access(현재 회원의 role, 같은 sessionId) + `newRefresh` 반환

**4) API 변경**

| API | 요청 | 응답 |
|---|---|---|
| `POST /api/auth/signup`, `POST /api/auth/login` | 그대로 | 응답에 `refreshToken`, `refreshExpiresIn` 추가 |
| `POST /api/auth/refresh` | `RefreshRequest(@NotBlank String refreshToken)` | 200 `TokenResponse` |
| `POST /api/auth/logout?all=false` | (로그인 필요) | 204 — `all=false`면 현재 세션(토큰의 `sid`), `true`면 전부(4-4에서 tokenVersion도 올림) |

- `SecurityConfig`: `.requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()`를 `/api/auth/**` 허용보다 **먼저** 둔다

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `member/domain/RefreshTokenTest` | [ ] generate → value → parse 왕복 / [ ] 점이 없는 문자열 parse → `UNAUTHORIZED` |
| `member/web/TokenRefreshTest` | [ ] 로그인 → refresh → 새 access·새 refresh, **이전 refresh로 다시 → 401 `REFRESH_REUSED`**, 이후 새 refresh도 401(세션 폐기) / [ ] 기기 A·B로 각각 로그인 → A에서 재사용 발생 → B의 refresh는 계속 동작 / [ ] 로그아웃 → 그 refresh 401 / [ ] `?all=true` → 모든 기기 refresh 401 / [ ] Redis 키에 refresh 원문이 없다(`HGET`으로 확인) |
| `member/application/RefreshConcurrencyTest` | [ ] 같은 refresh로 동시에 2번 → 하나 200, 하나 401 `REFRESH_REUSED` (OPEN-02) |

**리뷰 때 물어볼 것**
- refresh를 원문이 아니라 해시로 저장하는 이유는?
- JWT는 무상태인데 왜 Redis를 쓰나?
- `rotate`를 GET → 비교 → SET 세 번의 명령으로 하면 어떤 경합이 생기나?

---

## 4-2. 이메일 인증 · 비밀번호 변경·재설정 (M)

**목표**: 이메일 가입 회원은 인증코드로 이메일을 인증해야 주문·결제·구독·가게 개설을 할 수 있다. 비밀번호 변경과 재설정을 지원한다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 발송 제한 | 쿨다운 키(`SET NX EX 60`)와 시간당 횟수 키(`INCR` + 처음일 때 `EXPIRE 3600`) |
| 시도 제한 | 확인 실패마다 `HINCRBY attempts 1`, 5번 넘으면 코드 삭제 |
| 토큰 클레임으로 인가 | 액세스 토큰에 `ev`(이메일 인증 여부) 클레임을 넣고 인터셉터가 확인. 인증 후에는 refresh로 새 토큰을 받아야 반영된다 |

**정책 (이 단계에서 정함)**
- 인증코드 6자리 숫자(`SecureRandom`), 유효 5분. 목적(purpose): `VERIFY_EMAIL`, `RESET_PASSWORD`.
- 발송: 같은 이메일로 60초에 1번, 시간당 5번(초과 시 429). 확인: 5번 틀리면 코드 무효(새로 발송해야 함).
- 이메일 인증이 필요한 API: 체크아웃, 결제 승인, 가게 개설 (구독 신청은 5-2에서 같은 어노테이션을 붙인다).
- 비밀번호 재설정 요청은 **가입 여부와 상관없이 항상 202**(가입된 이메일을 알아낼 수 없게).
- 비밀번호 변경·재설정 후: 변경은 현재 세션만 남기고 나머지 세션 폐기, 재설정은 전부 폐기. (4-4에서 tokenVersion도 올린다)
- 마이그레이션 `V{...}__member_add_email_verified_at.sql`: `member.member`에 `email_verified_at timestamptz NULL` (null = 미인증)
- 기존 로컬 개발 데이터는 필요하면 로컬 DB에서 `UPDATE member.member SET email_verified_at = now()`를 직접 실행한다.

### 할 일

**1) 메일**: 3-5의 `common.mail.MailSender`를 쓴다. 발송은 트랜잭션 밖에서.

**2) member — 인증코드**

| 클래스 | 규격 |
|---|---|
| `EmailCodePurpose` | `VERIFY_EMAIL, RESET_PASSWORD` |
| `EmailCodeStore` (domain 인터페이스) + `RedisEmailCodeStore` | `void checkSendLimit(String email)`(위반 시 `RATE_LIMITED`), `void save(EmailCodePurpose p, String email, String codeHash)`, `boolean verify(EmailCodePurpose p, String email, String code)`(맞으면 키 삭제 후 true, 틀리면 attempts 증가 후 5 이상이면 키 삭제, false) |
| `EmailVerificationService` | `void send(UUID memberId)` / `void confirm(UUID memberId, String code)` |
| `Member.verifyEmail(Instant now)` | 이미 인증됐으면 `EMAIL_ALREADY_VERIFIED`, 아니면 `emailVerifiedAt = now` |

`send`: 회원 조회 → 이미 인증이면 409 → `checkSendLimit` → 코드 생성 → `save` → `mailSender.send(email, "[MyRoutin] 이메일 인증코드", "인증코드: " + code)`
`confirm`: `verify` false면 `EMAIL_CODE_INVALID` → 트랜잭션으로 `member.verifyEmail(now)`

**3) 인증 여부로 막기**
- `AuthClaims`에 `boolean emailVerified` 추가(`ev` 클레임), 발급 시 `member.getEmailVerifiedAt() != null`
- `common.security.RequireVerifiedEmail`: `@Target(METHOD) @Retention(RUNTIME)`
- `common.security.VerifiedEmailInterceptor implements HandlerInterceptor`: 핸들러 메서드에 어노테이션이 있고 `claims.emailVerified() == false`면 `BusinessException(EMAIL_NOT_VERIFIED)` (인터셉터 예외도 `GlobalExceptionHandler`가 처리한다) → `WebConfig.addInterceptors`에 등록
- 붙일 곳: `POST /api/orders/checkout`, `POST /api/orders/{id}/payment/confirm`, `POST /api/shops`
- 테스트 영향: `TestFixtures.signUp(...)`이 기본으로 JDBC로 `email_verified_at`을 채운 뒤 토큰을 발급하도록 바꾼다. 미인증 회원이 필요하면 `signUpUnverified(...)`.

**4) 비밀번호**
- `Member.changePassword(String encodedPassword)`
- `PasswordService`
  - `TokenResult change(UUID memberId, UUID sessionId, String current, String next)`: `passwordHash`가 null이거나 `matches` 실패면 `PASSWORD_MISMATCH` → 변경 → `sessionStore.deleteAllOfExcept(memberId, sessionId)` → 현재 세션용 새 토큰 반환(4-4 이후 tokenVersion이 올라 기존 access가 막히므로)
  - `void requestReset(String email)`: 회원이 있고 ACTIVE면 `checkSendLimit` → 코드(`RESET_PASSWORD`) 발송. 없으면 아무것도 안 함(응답은 같음)
  - `void confirmReset(String email, String code, String newPassword)`: `verify` 실패면 `EMAIL_CODE_INVALID` → 변경 → `deleteAllOf(memberId)`

**5) API**

| API | 권한 | 요청 | 응답 |
|---|---|---|---|
| `POST /api/auth/email-verifications` | 로그인 | (4-3에서 바디 추가) | 202 |
| `POST /api/auth/email-verifications/confirm` | 로그인 | `ConfirmEmailRequest(@NotBlank @Pattern(regexp="^\\d{6}$") String code)` | 200 `TokenResponse` (인증 반영된 새 토큰, 현재 세션 유지) |
| `PATCH /api/members/me/password` | 로그인 | `ChangePasswordRequest(@NotBlank String currentPassword, newPassword: 1-3의 비밀번호 규칙)` | 200 `TokenResponse` |
| `POST /api/auth/password-reset` | 공개 | `PasswordResetRequest(@NotBlank @Email String email)` | 202 (항상) |
| `POST /api/auth/password-reset/confirm` | 공개 | `PasswordResetConfirmRequest(email, code, newPassword)` | 204 |

- `SecurityConfig`: `/api/auth/email-verifications/**`는 로그인 필요(4-3에서 가입 토큰 경로 추가)

### 완료 확인

| 테스트 클래스 | 케이스 (`FakeMailSender`에서 코드를 꺼내 사용) |
|---|---|
| `member/web/EmailVerificationTest` | [ ] 발송 → 확인 → `email_verified_at` 채워짐, 응답 토큰의 `ev = true` / [ ] 60초 안에 재발송 → 429 `RATE_LIMITED` / [ ] 쿨다운 키를 테스트에서 지우며 6번째 발송 → 429 / [ ] **5번 틀린 뒤 맞는 코드 → 400** / [ ] Redis 키에 코드 원문이 없다 |
| `member/web/RequireVerifiedEmailTest` | [ ] 미인증 회원 체크아웃 → 403 `EMAIL_NOT_VERIFIED` / [ ] 가게 개설 → 403 / [ ] 장바구니 조회는 200 |
| `member/web/PasswordTest` | [ ] 변경 → 이전 비밀번호 로그인 401, 새 비밀번호 200 / [ ] **변경 → 다른 기기 refresh 401**, 현재 기기는 응답의 새 토큰으로 동작 / [ ] 현재 비밀번호 틀림 → 400 `PASSWORD_MISMATCH` / [ ] 없는 이메일로 재설정 요청 → 202, 메일 0건 / [ ] 재설정 → 모든 refresh 401, 새 비밀번호 로그인 200 |

**리뷰 때 물어볼 것**
- 인증코드를 비교할 때 타이밍 공격은?
- 비밀번호 재설정 요청에서 "가입되지 않은 이메일"이라고 알려주면 무엇이 문제인가?
- 이메일 인증 여부를 매 요청 DB에서 보지 않고 토큰 클레임으로 보는 것의 장단점은?

---

## 4-3. OAuth 로그인 (Kakao · Google · Naver) (L)

**목표**: Kakao·Google·Naver로 가입·로그인한다. 신규 회원은 가입 토큰 → 이메일 인증 → 가입 완료. 로그인한 회원은 소셜 계정을 연결할 수 있다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| Authorization Code 흐름 | 사용자를 제공자 로그인 화면으로 보냄 → 돌아온 `code`를 서버가 토큰으로 교환 → 사용자 정보 조회 |
| `state` | 인가 URL을 만들 때 랜덤 값을 Redis에 저장하고, 돌아올 때 같은 값인지 확인. 다른 사람이 만든 로그인 응답을 끼워 넣는 공격(CSRF)을 막는다 |
| 전략(Strategy) | 제공자마다 응답 형식이 다르다. `OAuthProviderClient` 구현체 3개가 차이를 흡수한다 |

**정책 (이 단계에서 정함)**
- 소셜로 가입한 회원도 이메일 인증을 거친다(FR-MEM-01). 이미 가입된 이메일(이메일 가입 회원)과 같으면 **자동 연결하지 않고** 409 `MEMBER_EMAIL_DUPLICATED` — 그 계정으로 로그인한 뒤 "소셜 계정 연결"을 쓰게 안내한다(POL-19).
- 한 회원은 제공자마다 소셜 계정 1개.
- 제공자가 이메일을 주지 않으면(카카오 선택 동의 등) 사용자가 가입 단계에서 이메일을 입력한다.
- 제공자 호출 타임아웃: connect 3초, read 5초.

### 할 일

**1) 마이그레이션** — `db/migration/member/V{...}__member_create_social_account.sql`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_social_account` |
| member_id | uuid | X | `fk_social_account_member`, `uk_social_account_member_provider (member_id, provider)` |
| provider | varchar(20) | X | `ck_social_account_provider`: `IN ('KAKAO','GOOGLE','NAVER')` |
| provider_user_id | varchar(100) | X | `uk_social_account_provider_user (provider, provider_user_id)` |
| provider_email | varchar(255) | O | |
| created_at | timestamptz | X | |


**2) 설정** — `OAuthProperties`: `@ConfigurationProperties("myroutine.oauth") record (Map<String, Provider> providers)`, `Provider(String clientId, String clientSecret, String authorizeUri, String tokenUri, String userInfoUri, String redirectUri, String scope)`. 시크릿은 환경변수(`KAKAO_CLIENT_SECRET` 등). 테스트는 가짜 서버 주소를 `@DynamicPropertySource`로.

**3) member.infrastructure — 제공자 클라이언트**

| 클래스 | 규격 |
|---|---|
| `OAuthProvider` (domain enum) | `KAKAO, GOOGLE, NAVER` |
| `OAuthUserInfo` (domain) | `record (String providerUserId, String email, String nickname)` |
| `OAuthProviderClient` (interface) | `OAuthProvider provider()`, `String authorizeUrl(String state)`, `OAuthUserInfo fetchUser(String code, String state)` — 실패·타임아웃은 `BusinessException(OAUTH_PROVIDER_ERROR)` |
| `KakaoOAuthClient`, `GoogleOAuthClient`, `NaverOAuthClient` | 각자 토큰 교환 + 사용자 정보 파싱. 필드: Kakao `id`, `kakao_account.email`, `kakao_account.profile.nickname` / Google `sub`, `email`, `name` / Naver `response.id`, `response.email`, `response.nickname` (확인 필요: 각 제공자 문서와 대조) |
| `OAuthClients` (`@Component`) | `List<OAuthProviderClient>`를 받아 `Map<OAuthProvider, OAuthProviderClient>`로. `get(provider)` |

**4) member.application**

| 클래스 | 메서드 |
|---|---|
| `OAuthStateStore`, `SignupTokenStore` (domain 인터페이스 + Redis 구현) | state: `String issue(OAuthProvider)`, `boolean consume(String state, OAuthProvider)`(있고 provider가 같으면 삭제 후 true) / 가입 토큰: `String issue(OAuthProvider, OAuthUserInfo)`, `Optional<PendingSocialSignup> find(String token)`, `void delete(String token)` |
| `OAuthLoginService` | `AuthorizeUrl authorizeUrl(OAuthProvider)` → `record (String url, String state)` / `OAuthLoginResult login(OAuthProvider, String code, String state, String device)` / `TokenResult signup(SocialSignupCommand, String device)` / `void link(UUID memberId, OAuthProvider, String code, String state)` |
| `OAuthLoginResult` | `record (String status, TokenResult token, String signupToken, String email)` — status `LOGGED_IN` 또는 `SIGNUP_REQUIRED` |
| `SocialSignupCommand` | `record (String signupToken, String email, String nickname, String name, String verificationCode)` |
| `Member.signUpWithSocial(String email, String nickname, String name, Instant now)` | `passwordHash = null`, `emailVerifiedAt = now`, 나머지는 `signUp`과 같음 |
| `SocialAccount` (entity) + `SocialAccountRepository` | `findByProviderAndProviderUserId`, `existsByProviderAndProviderUserId`, `existsByMemberIdAndProvider`, `save` |

`login` 순서: `consume(state, provider)` 실패면 `OAUTH_STATE_INVALID` → `fetchUser` → 소셜 계정이 있으면 회원 `verifyCanLogin` → `issueNewSession` → `LOGGED_IN` / 없으면 가입 토큰 발급 → `SIGNUP_REQUIRED`(email은 제공자 이메일, 없으면 null)

`signup` 순서: 가입 토큰 조회(없으면 `SIGNUP_TOKEN_INVALID`) → email = 토큰의 이메일 ?: 요청 이메일(둘 다 없으면 `INVALID_REQUEST`) → 정규화 → `emailCodeStore.verify(VERIFY_EMAIL, email, code)` 실패면 `EMAIL_CODE_INVALID` → `existsByEmail`이면 `MEMBER_EMAIL_DUPLICATED`(자동 연결 안 함) → 닉네임 중복 확인 → `Member.signUpWithSocial` + `SocialAccount` 저장(한 트랜잭션) → 가입 토큰 삭제 → `issueNewSession`

`link` 순서: state 확인 → `fetchUser` → 이미 다른 회원에 연결된 소셜 계정이거나 이 회원이 같은 제공자를 이미 가졌으면(`existsByProviderAndProviderUserId`, `existsByMemberIdAndProvider`) `SOCIAL_ACCOUNT_ALREADY_LINKED` → 저장 (동시 요청 경합은 unique 제약 → 409 `DUPLICATE_RESOURCE`)

가입 토큰용 인증코드 발송은 **별도 API**로 둔다: `POST /api/auth/oauth/signup/email-verifications` 바디 `SignupEmailVerificationRequest(@NotBlank String signupToken, @Email String email)` — 토큰의 이메일(없으면 바디 이메일)로 `VERIFY_EMAIL` 코드 발송(4-2의 발송 제한 그대로). 로그인 회원용 `POST /api/auth/email-verifications`는 4-2 그대로 둔다.

**5) API**

| API | 권한 | 요청 | 응답 |
|---|---|---|---|
| `GET /api/auth/oauth/{provider}/authorize-url` | 공개 | provider는 소문자(`kakao`) → enum 변환(실패 시 400) | 200 `{url, state}` |
| `POST /api/auth/oauth/{provider}/login` | 공개 | `OAuthLoginRequest(@NotBlank code, @NotBlank state)` | 200 `OAuthLoginResponse` |
| `POST /api/auth/oauth/signup/email-verifications` | 공개(가입 토큰) | `SignupEmailVerificationRequest` | 202 |
| `POST /api/auth/oauth/signup` | 공개(가입 토큰) | `SocialSignupRequest(@NotBlank signupToken, @Email email, nickname, name, @Pattern 6자리 verificationCode)` | 201 `TokenResponse` |
| `POST /api/members/me/social-accounts/{provider}` | 로그인 | `OAuthLoginRequest` | 204 |

- `SecurityConfig`: `/api/auth/oauth/**`는 공개(이미 `/api/auth/**` 허용 안에 있다), `/api/auth/email-verifications/**`는 로그인 필요(4-2)

**6) 가짜 제공자 서버** — `support/FakeOAuthServer`: 2-3의 가짜 서버와 같은 방식(JDK `HttpServer`). 경로 `/{provider}/token`, `/{provider}/userinfo`. `givenUser(provider, providerUserId, email, nickname)`, `failWith(provider, Behavior)` (지연·500). `IntegrationTestSupport`에서 제공자별 `token-uri`, `user-info-uri`를 주입.

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `member/web/OAuthLoginTest` | [ ] 신규 소셜 로그인 → `SIGNUP_REQUIRED` + 가입 토큰 → 가입 토큰으로 인증코드 발송 → 가입 → 201 토큰, 회원 `email_verified_at` 있음, 소셜 계정 1행 / [ ] 같은 소셜 계정으로 다시 로그인 → `LOGGED_IN` / [ ] **같은 이메일의 이메일 가입 계정이 있으면 가입 → 409, 자동 연결되지 않음** / [ ] state 없이·다른 provider의 state·두 번 쓴 state → 400 `OAUTH_STATE_INVALID` / [ ] 제공자 타임아웃 → 502 `OAUTH_PROVIDER_ERROR` / [ ] 세 제공자 모두 사용자 정보 파싱(제공자별 응답 JSON) |
| `member/web/SocialLinkTest` | [ ] 이메일 회원이 카카오 연결 → 이후 카카오 로그인 → 같은 회원 / [ ] 다른 회원에 연결된 카카오 계정 연결 → 409 |

**리뷰 때 물어볼 것**
- OAuth `state` 파라미터는 무엇을 막나?
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
- Redis 장애 시 특별한 처리를 하지 않는다(인증이 필요한 요청은 500으로 실패). 장애 정책은 제안으로 둔다.
- 로그인 잠금: 같은 이메일로 연속 5번 실패하면 15분 동안 423. 존재하지 않는 이메일도 똑같이 센다(가입 여부 노출 방지). 성공하면 카운트 삭제.
- 관리자 검색: 이메일 또는 닉네임 **앞부분 일치**, 상태 필터, 커서 페이징.

### 할 일

**1) tokenVersion**

| 대상 | 규격 |
|---|---|
| 마이그레이션 | `V{...}__member_add_token_version.sql`: `member.member`에 `token_version int NOT NULL DEFAULT 0` |
| `Member` | `int tokenVersion` 필드, `void increaseTokenVersion()` |
| `JwtProvider`·`AuthClaims` | 클레임 `tv`(tokenVersion) 추가: `issue(UUID memberId, String role, int tokenVersion, UUID sessionId)`, `AuthClaims(..., int tokenVersion)` |
| `common.security.TokenVersionProvider` | `int currentVersion(UUID memberId)` — 필터(common)가 member를 모르므로 인터페이스만 common에 둔다 |
| `JwtAuthenticationFilter` 변경 | parse 성공 후 `claims.tokenVersion() != provider.currentVersion(memberId)`면 인증을 설정하지 않는다 → 401 `UNAUTHORIZED` |
| `RedisTokenVersionProvider` (member.infrastructure) | Redis `GET auth:token-version:{memberId}` → 없으면 DB에서 읽어 `SET ... EX 86400` 후 반환 |
| `TokenVersionService` (member.application) | `void bump(Member member)`: `member.increaseTokenVersion()` → `TransactionSynchronizationManager.registerSynchronization`의 `afterCommit`에서 Redis `SET`. 반드시 트랜잭션 안에서 호출 |

- 호출 위치: `WithdrawService`(3-6), `LogoutService(all=true)`, `PasswordService.change/confirmReset`, 아래 제재

**2) 관리자 제재**
- 마이그레이션 `V{...}__member_add_ban_reason.sql`: `member.member`에 `ban_reason varchar(200) NULL`
- `Member.ban(String reason)`: `ACTIVE → BANNED`, `banReason = reason` / `Member.unban()`: `BANNED → ACTIVE`, `banReason = null`
- 이벤트 `MemberTopics.MEMBER_BANNED = "member.member-banned.v1"`, `MemberBannedEvent(UUID memberId, String reason)` → 3-5 알림 매핑에 `BANNED` 추가
- `AdminMemberService`: `CursorPage<AdminMemberResult> search(String query, MemberStatus status, String cursor, int size)` / `@Transactional void ban(UUID memberId, String reason)` (ban → bump → 세션 전부 폐기 → 이벤트) / `@Transactional void unban(UUID memberId)`
- API (`/admin/api/members`, ADMIN): `GET ?query=&status=&cursor=&size=` → `AdminMemberResponse(id, email, nickname, name, status, role, emailVerified, createdAt)` / `POST /{id}/ban` `BanRequest(@NotBlank @Size(max=200) String reason)` → 204 / `POST /{id}/unban` → 204

**3) 로그인 잠금**
- `LoginAttemptStore` (domain 인터페이스) + `RedisLoginAttemptStore`: `Optional<Instant> lockedUntil(String email)`(카운트 ≥ 5면 TTL로 해제 시각 계산), `void recordFailure(String email)`(`INCR`, 1이면 `EXPIRE 900`), `void reset(String email)`
- `LoginService.login` 변경: 정규화 직후 `lockedUntil`이 있으면 `BusinessException(ACCOUNT_LOCKED, Map.of("unlockAt", ...))` → 비밀번호 확인 실패(이메일 없음 포함) 시 `recordFailure` → 성공 시 `reset`


### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `member/web/TokenRevocationTest` | [ ] **제재 → 기존 access로 `/me` → 401** / [ ] 전체 로그아웃 → 기존 access 401 / [ ] 비밀번호 변경 → 기존 access 401, 응답의 새 access 200 / [ ] 롤백된 트랜잭션에서 bump → Redis 값 그대로 |
| `member/infrastructure/RedisTokenVersionProviderTest` | [ ] Redis에 값이 없으면 DB 값을 읽어 Redis에 채운다 |
| `member/web/AdminMemberTest` | [ ] 일반 회원이 관리자 API → 403 / [ ] 검색: 이메일 앞부분, 상태 필터 / [ ] 제재 → 로그인 403 `MEMBER_BANNED` → 해제 → 로그인 200 |
| `member/web/LoginLockTest` | [ ] 5번 실패 → 6번째는 올바른 비밀번호여도 423 `ACCOUNT_LOCKED`, `details.unlockAt` / [ ] 테스트에서 키 TTL을 1초로 줄임(`redis.expire(key, 1s)`) → `Eventually`로 로그인 200 / [ ] 없는 이메일 5번 → 6번째 423 / [ ] 4번 실패 후 성공 → 카운트 초기화 |

**제안 (선택)**
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
