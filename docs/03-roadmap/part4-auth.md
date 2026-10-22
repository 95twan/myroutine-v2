# Part 4. 인증 완성

> 버전 0.23 · 2026-10-23 · **헬퍼 시그니처를 마저 맞췄다**: 변경 행 2개와 `login`·`refresh`·`socialSignup`·`issueState`·`oauthLogin`의 인자·반환 타입. 규격·동작 변경 없음
> 0.22 · 2026-10-23 · **테스트 준비 헬퍼 표의 시그니처를 타입까지 적었다**(사용자 지적: 어떤 메서드를 만드는지 안 보인다): 4-1~4-4 헬퍼 표의 첫 칸을 매개변수 타입과 반환 타입이 있는 자바 시그니처로 고쳤다. 규격·동작 변경 없음
> 0.21 · 2026-10-23 · **테스트 준비가 아래 테스트 절을 가리키던 곳을 위로 옮겼다**(사용자 지적: 헬퍼를 만드는 방법이 테스트 절에 있다): 4-1 `login`·`refresh` 메서드 정의와 4-3 `socialSignup` 단계를 "테스트 준비"로 옮겼다. 규격·동작 변경 없음
> 0.20 · 2026-10-23 · **완료 확인의 "테스트 준비"를 테스트 클래스 앞으로 옮겼다**(사용자 지적: 헬퍼 설명이 테스트보다 뒤에 있다): 4-1~4-4 "완료 확인"을 테스트 준비(헬퍼 표 → 이미 있는 헬퍼 → 헬퍼를 쓰는 방법) → 공통 준비 → 테스트 클래스 순으로 다시 놓았다. 도입부의 "읽는 방법" 불릿은 헬퍼 표 행과 겹치는 부분(`SessionSnapshot`의 `ttlSeconds()`, `EmailAuthSnapshot`의 키별 접근자, 소셜 계정·회원 ID 조회, tokenVersion 필드)을 표로 합치고 나머지를 "헬퍼를 쓰는 방법"에 남겼다. 4-2의 `FakeMailSender`·`login` 안내 중복을 한 번만 쓰고, 순서가 바뀐 "위/아래" 표현을 고쳤다. 규격·동작 변경 없음
> 0.19 · 2026-10-20 · **4.1 기준 점검에서 '하' 후보를 권장안대로 정리했다**(사용자 요청): 4-1 refresh 성공의 토큰 응답 필드(가입이 확인), 4-2 발송 1번의 `attempts` 초기값·미인증 허용 케이스의 `$.id`·주문 수, 4-3의 토큰 응답 필드·`signupToken`/`email` 부재·인증코드 저장·다른 회원의 소셜 계정, 4-4의 현재 기기 로그아웃 대조 `/me`·`status=BANNED` 필터 항목의 `status`. 규격 외 변경 없음
> 0.18 · 2026-10-20 · **4.1 기준 점검에서 '중' 후보를 뺐다**(사용자 요청): 4-1 3번의 `refreshHash` 교체, 4-2 발송 1번의 `codeHash`·쿨다운 값과 3번의 시간당 TTL, 비밀번호 변경·재설정의 `matches`·재설정 뒤 refresh 401·같은 코드 재사용 400, 4-3의 `role`·`status` 초기값·회원 수 중복·기존 회원 로그인, 4-4 `TokenRevocation` 1번의 사전 확인과 5번의 준비 재단언, `LoginLockTest` 4번의 중간 횟수·401, `AdminMemberTest` 4번의 로그인 403. 규격 외 변경 없음
> 0.17 · 2026-10-20 · **4.1 기준 점검에서 확신 '상'을 뺐다**(사용자 요청): 4-2 발송 6번의 쿨다운·시간당 값 비교, 4-3 `OAuthLoginTest` 1번의 `/me` 응답 필드, 4-4 `TokenRevocation` 1·2·3·4번이 4-1·4-2·3-6·`AdminMemberTest`와 같은 경로로 이미 확인하는 세부(ban 상태, refresh 401, 세션 유지), 4-4 `RedisTokenVersionProvider` 1번의 준비 재단언, `AdminMemberTest` 3번의 `nextCursor`. 규격 외 변경 없음
> 0.16 · 2026-10-20 · **4-2·4-3·4-4 완료 확인에서 '중' 후보를 뺐다**(사용자 요청): 확인 동작이 바꾸지 못하는 `accessUnverified`의 `emailVerified()`, 로그인·연결이 만들지 않는 회원 수, 가입 토큰 키, 해제가 내지 않는 outbox, 대기가 이미 확인한 잠금 키. 규격 외 변경 없음
> 0.15 · 2026-10-18 · **읽기 헬퍼 중복을 걷어냈다**: `changeRole`은 3-1의 정의를 쓰고, `memberSnapshotOf`는 3-1이 `email`·`status`·`role`·`withdrawnAt`을 이미 더했으므로 4-2는 `emailVerifiedAt`만, 4-4는 `banReason`만 더한다. outbox 행은 3-1의 `OutboxSnapshot`(`payload()`는 `JsonNode`)으로 읽는다. 규격 변경 없음
> 0.14 · 2026-10-18 · **완료 확인의 Redis·DB 읽기를 읽기 헬퍼로 바꿨다**(사용자 지적: 확인할 값마다 JDBC 쿼리·Redis 호출을 쓰고 `Map`으로 바꾸고 캐스팅하는 코드를 테스트마다 짜야 해서 비효율): 세션(`sessionSnapshotOf`·`sessionIdsOf`), 회원(1-3의 `memberSnapshotOf`에 `email`·`emailVerifiedAt`·`status`·`role`을 4-2에서, `banReason`을 4-4에서 더한다), 인증코드와 발송 제한(`emailAuthSnapshotOf`), 소셜 계정(`socialAccountsOf`·`memberIdOf`), Redis 문자열·해시 키(`redisStringOf`·`redisHashOf`), tokenVersion(`tokenVersionSnapshotOf`)을 `TestFixtures`가 한 번에 읽게 하고 단계마다 "이 단계에서 추가하는 헬퍼" 표에 적었다. 같은 클래스 안에서 반복하던 확인을 지웠다(4-1 케이스 6의 `refreshHash` 재확인, 4-3 케이스 1의 authorize-url 쿼리·state 소진·가입 토큰 필드 값). 4-1 케이스 2는 세션 남은 시간을 미리 100초로 줄여 두어 "회전이 만료를 7일로 되돌린다"가 그 케이스의 확인이 되게 했다. 4-2 `RequireVerifiedEmailTest` 2번의 주문 상태 확인은 `snapshotOf`로 바꿨다. 규격·동작은 0.13과 같다
> 0.13 · 2026-10-18 · **4-1~4-4 "완료 확인"을 읽는 방법·준비·동작·확인 값으로 풀어 썼다**("뭘 확인해야 하는지 애매하다" 지적): 한 줄로 뭉쳐 있던 케이스마다 값을 읽는 곳(jsonPath, DB 컬럼, Redis 키·남은 시간, 토큰 클레임, 가짜 메일·제공자 서버)과 입력·토큰·기대 상태 코드·`$.code`·부수효과가 없어야 하는 부정 확인을 적고, 시간 의존 케이스(만료·잠금 해제)는 Redis 키 삭제·TTL 단축으로 흉내 내는 방법을 적었다. 단계마다 "테스트 준비"(추가 헬퍼)를 더했다. 규격·동작은 0.12와 같다
> 버전 0.12 · 2026-10-16 · **읽는 곳이 없는 값을 지웠다**(리뷰 지적): 4-1 세션의 `createdAt`·`lastUsedAt`과 `StoredSession`(조회는 `findMemberId`가 회원 ID만 돌려준다, `rotate.lua`의 `now` 인자와 `RedisSessionStore`의 `Clock`도 함께), 4-2 `EmailCodeStore.save`가 해시 대신 원문 코드를 받아 `verify`와 대칭이 되게 했고(호출 쪽 해시 계산과 해시 형식 확인 필요 항목 삭제), 4-3 제공자 `nickname`(`OAuthUserInfo`·`PendingSocialSignup`·Redis 가입 토큰 필드·제공자별 읽는 필드·가짜 서버)을 지웠다. 그 밖의 규격·동작은 0.11과 같다
> 0.11 · 2026-10-16 · **"할 일"을 구현 순서대로 다시 놓고 설명 속에 숨어 있던 구현물을 항목으로 올렸다**(구현 중 지적): 4-1~4-4 "할 일" 맨 앞에 구현 순서 한 줄을 더했다. 4-1은 세션 설명을 의존 순서(에러 코드·`TokenResult` → `RefreshToken` → `SessionStore`·`rotate.lua` → `TokenIssuer` → 서비스)로 바꾸고 괄호 속에만 있던 `SignupService`·`LoginService` 변경, 기존 테스트의 컴파일 수정, 테스트 Redis 비우기를 적었다. 4-2는 정책 문단에 있던 마이그레이션과 뒤쪽에 있던 `Member` 필드·메서드를 앞의 "마이그레이션·도메인·에러 코드" 표로 모으고 `ev` 클레임의 `TokenIssuer` 인자·해시 형식·`TestFixtures` 변경·컨트롤러를 항목으로 만들었으며, 0.10 결정(확인 응답이 access를 직접 준다)과 어긋나 있던 "인증 후 refresh로 새 토큰을 받아야 반영된다" 문장을 맞췄다. 4-3은 `SocialAccount`·`Member.signUpWithSocial`·에러 코드를 저장소·서비스 앞으로, 가입 토큰용 인증코드 발송을 순서 목록으로, 설정 클래스·컨트롤러를 항목으로 올렸다. 4-4는 `TokenIssuer` 인자·`Member.banReason`·검색 조회·`MemberBannedEvent`·토픽 설정·`ACCOUNT_LOCKED`·ADMIN 토큰 헬퍼를 표로 올렸다. 반복 설명(`reissueAccess`의 refresh null, 발송·시도 제한)은 한 곳만 남겼다. 규격·동작은 0.10과 같다
> 0.10 · 2026-10-15 · **인증·잠금 결정 반영**(리뷰 결정): ① 이메일 인증 확인·비밀번호 변경은 `TokenIssuer.reissueAccess`로 access만 새로 주고 `refreshToken`은 null(클라이언트가 기존 refresh 유지). ② 이메일 인증 API는 로그인 회원 전용이고 OAuth 가입용 발송은 4-3의 별도 API(`07-api-spec.md`도 맞춤). ③ 로그인 잠금 TTL을 5번째 실패에서 다시 걸어 "15분 동안 423"을 지킨다. 그 밖의 규격은 0.9와 같다
> 0.9 · 2026-10-15 · **Part 4 클래스·메서드 설명을 풀어 썼다**(구현 중 "뭔지 모르겠다" 지적): 한 줄 표로만 적혀 있던 클래스·메서드마다 무엇인지·무엇을 받아 무엇을 돌려주는지·왜 있는지·누가 부르는지를 적었다(4-1 `RefreshToken`·`SessionStore`·`TokenIssuer`·`TokenRefreshService`·`LogoutService`·컨트롤러, 4-2 `EmailCodeStore`·`EmailVerificationService`·인터셉터·`PasswordService`, 4-3 OAuth 전체 흐름·제공자 클라이언트·저장소·`OAuthLoginService`, 4-4 필터 변경·`TokenVersionService`·`AdminMemberService`·`LoginAttemptStore`). 공통 규칙 T에 Redis 명령과 `StringRedisTemplate` 호출의 대응표를 더했다. 문서에 정해져 있지 않은 부분은 "(확인 필요)"로 남겼다(기기 정보 전달 경로, `rotate` 때 세션 목록 키의 TTL 연장, 인증 확인·비밀번호 변경 응답의 refresh, `OAuthLoginResponse` 필드 등). 규격·동작은 직전 버전과 같다
> 0.8 · 2026-10-14 · **표현·구조 정리**(설계·동작 변경 없음): 한 줄 화살표(`→`)로 이어 쓴 흐름(4-2 인증코드·비밀번호, 4-3 소셜 로그인·가입·연결, 4-4 제재·로그인 잠금)을 번호 목록으로 풀고, 정의 없이 쓰던 `StoredSession`·`PendingSocialSignup`을 정의했다
> 0.7 · 2026-10-12 · **용어 정리**: 단계 끝 "제안 (선택)"을 "추가 항목"으로 바꿨다. "제안"은 리뷰어의 의견(근거 없는 지적)에만 쓴다. 내용은 그대로다
> 0.6 · 2026-10-09 · **구현된 Part 1 코드와 정합**: `TokenResult`·`TokenResponse`의 실제 필드 이름(`expiresInSeconds`), `TestFixtures`의 실제 헬퍼 이름(`signup`·`token`), 단계마다 늘어나는 `JwtProvider.issue`·`AuthClaims` 인자를 한 줄로 맞췄다. Redis 이미지 태그를 고정하고(`redis:7.4.11`) 테스트 컨테이너 기동에 1-10의 MinIO를 넣었다
> 0.5 · 2026-10-02 · **덜어내기**: 토큰 거부 코드는 401 `UNAUTHORIZED` 하나로(만료·폐기 구분은 제안), tokenVersion 로컬 캐시와 Redis 장애 정책 제거, 가입 토큰용 인증코드 발송은 별도 API로(선택적 리졸버 제거), 소셜 계정 중복은 사전 조회로
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
| `auth:session:{sessionId}` | HASH | `memberId`, `refreshHash`, `device` | 7일(갱신 때마다 연장) | 4-1 |
| `auth:member-sessions:{memberId}` | SET | sessionId 목록 | 7일(세션 생성·갱신 때 연장) | 4-1 |
| `auth:email-code:{purpose}:{email}` | HASH | `codeHash`, `attempts` | 5분 | 4-2 |
| `auth:email-cooldown:{email}` | STRING | `1` | 60초 | 4-2 |
| `auth:email-hourly:{email}` | STRING | 발송 횟수 | 1시간(첫 발송 때 설정) | 4-2 |
| `auth:oauth-state:{state}` | STRING | provider | 10분 | 4-3 |
| `auth:oauth-signup:{signupToken}` | HASH | `provider`, `providerUserId`, `email` | 10분 | 4-3 |
| `auth:token-version:{memberId}` | STRING | 버전 숫자 | 1일 | 4-4 |
| `auth:login-fail:{email}` | STRING | 연속 실패 횟수 | 15분(첫 실패와 5번째 실패 때 설정) | 4-4 |

**Redis 명령 ↔ `StringRedisTemplate` 호출** — 이 Part의 코드는 아래 호출만 쓴다. 아래 `redis`는 주입받은 `StringRedisTemplate`이다(Spring Data Redis 4.x의 시그니처는 구현 때 IDE로 확인한다, 확인 필요).

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

**구현 순서**: ① 인프라(compose·의존성·`IntegrationTestSupport`) → ② `JwtProperties`·`AuthClaims`·`JwtProvider`(2) → ③ member(3): `REFRESH_REUSED` → `TokenResult`·`TokenResponse`·`SignupResponse` → `RefreshToken` → `SessionStore`·`RedisSessionStore`·`rotate.lua` → `TokenIssuer` → `SignupService`·`LoginService` 변경 → `TokenRefreshService` → `LogoutService` → ④ `AuthController`·`SecurityConfig`(4) → 테스트(`RefreshTokenTest` → `TokenRefreshTest` → `RefreshConcurrencyTest`).

**1) 인프라**
- `docker-compose`: `redis:7.4.11`(문서가 정한 7.4 줄의 최신 패치, 2026-10-09 Docker Hub 확인. 8.x도 있다), `127.0.0.1:6379:6379`. 1-11 운영 compose에도 같은 태그로(호스트 포트 없이)
- 의존성: `implementation 'org.springframework.boot:spring-boot-starter-data-redis'`(Boot 4.1.1에 있다, Lettuce 7.5.2를 Boot가 관리)
- `IntegrationTestSupport`: `@ServiceConnection(name = "redis") static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4.11").withExposedPorts(6379);` → `Startables.deepStart(postgres, minio, kafka, redis).join()`. `@AfterEach`에서 Redis를 비운다(공통 규칙 T의 `flushDb()`)

**2) common.security 변경**
- `JwtProperties`에 `@NotNull Duration refreshTokenTtl` 추가. `application.yaml`에 `refresh-token-ttl: 7d`를 넣고 `access-token-ttl`을 `1h`에서 `15m`으로 바꾼다(필드가 없으면 `@NotNull`로 기동이 실패한다). 쓰는 곳은 `TokenIssuer`(세션 TTL, 응답의 `refreshExpiresInSeconds`)와 `TokenRefreshService`(`rotate`에 넘길 TTL)다
- `AuthClaims`에 `UUID sessionId` 추가 (`sid` 클레임) — 로그아웃과 비밀번호 변경이 "이 토큰이 속한 세션"을 알아야 해서 넣는다. 컨트롤러에서 꺼낼 때는 memberId만 주는 `@CurrentMember` 대신 `@AuthenticationPrincipal AuthClaims claims`로 받아 `claims.sessionId()`를 쓴다. `JwtAuthenticationFilter`가 principal로 `AuthClaims`를 넣어 두기 때문이다(`IdempotencyAspect`도 같은 방식으로 꺼낸다)
- `JwtProvider.issue(UUID memberId, String role)`(1-4) → `issue(UUID memberId, String role, UUID sessionId)`로 변경: `.claim("sid", sessionId.toString())`을 더한다. `parse`는 `claims.get("sid", String.class)`를 `UUID.fromString`해서 `AuthClaims`에 넣는다. 새 클레임이 없는 옛 토큰(`parse`가 `Optional.empty()`로 거절할지, 기본값으로 둘지)은 문서에 없다(확인 필요, 어차피 access 만료 시간 안에 사라진다)

| 단계 | `AuthClaims` (현재 `(UUID memberId, String role)`) | `JwtProvider.issue(...)` | 새 클레임 |
|---|---|---|---|
| 4-1 | `(memberId, role, sessionId)` | `(memberId, role, sessionId)` | `sid` |
| 4-2 | `(memberId, role, sessionId, emailVerified)` | `(memberId, role, sessionId, emailVerified)` | `ev` |
| 4-4 | `(memberId, role, sessionId, emailVerified, tokenVersion)` | `(memberId, role, sessionId, emailVerified, tokenVersion)` | `tv` |

- `@CurrentMember`는 `expression = "memberId"`라 인자가 늘어도 바꾸지 않는다.
- 기존 테스트를 컴파일되게 고친다: `JwtProviderTest`의 `new JwtProperties(secret, Duration.ofHours(1))` 2곳에 `refreshTokenTtl` 인자를, `issue(memberId, role)` 2곳에 `sessionId`를, `MemberControllerTest`의 `issue(memberId, "USER")` 1곳에 `sessionId`를 더한다. 4-2·4-4에서 `issue`의 인자가 늘 때도 같은 호출을 고친다.

**3) member — 세션**

클래스들이 이렇게 이어진다.

- 가입·로그인: `TokenIssuer.issueNewSession` → 세션을 Redis에 만들고 access + refresh를 돌려준다
- 갱신(`POST /api/auth/refresh`): `TokenRefreshService` → `SessionStore.rotate`로 refresh를 바꾸고 → `TokenIssuer.reissue`로 새 토큰을 만든다
- 로그아웃: `LogoutService` → `SessionStore.delete*`

**`TokenResult`**: 기존 `record TokenResult(String accessToken, long expiresInSeconds)`에 `String refreshToken, long refreshExpiresInSeconds` 추가. web의 `TokenResponse(accessToken, tokenType, expiresIn)`에도 `refreshToken, refreshExpiresIn`을 더하고 `from(result)`에서 옮긴다. `SignupResponse(memberId, accessToken, tokenType, expiresIn)`도 응답에 두 필드가 추가되므로(아래 API 표) 같은 필드와 `from`의 옮기기가 필요하다.

**`MemberErrorCode`**에 `REFRESH_REUSED`(401)를 추가한다.

**`RefreshToken`** (member.domain, 값 객체 — Spring 빈이 아니다) `record (UUID sessionId, String secret)`
- 클라이언트에 주는 refresh 문자열 `{sessionId}.{secret}`을 만들고 해석한다. sessionId가 문자열에 들어 있어서 갱신 요청이 오면 Redis의 어느 세션을 볼지 바로 안다. 비밀은 `secret`뿐이라 Redis에는 그 해시만 둔다(공통 규칙 T)

| 메서드 | 하는 일 | 입력 → 반환 |
|---|---|---|
| `static RefreshToken generate(UUID sessionId)` | 새 refresh를 만든다 | sessionId → `SecureRandom`으로 뽑은 32바이트를 `Base64.getUrlEncoder().withoutPadding()`로 인코딩한 문자열이 `secret`인 `RefreshToken` |
| `String value()` | 클라이언트에 내려줄 문자열 | → `sessionId + "." + secret` |
| `static RefreshToken parse(String raw)` | 클라이언트가 보낸 문자열을 되돌린다 | raw → `RefreshToken`. `null`·점으로 나눈 결과가 2조각이 아님·앞조각이 UUID가 아님(`UUID.fromString`의 `IllegalArgumentException`을 잡는다)이면 `BusinessException(UNAUTHORIZED)`(401) |
| `String secretHash()` | Redis에 저장·비교할 값 | → `secret`의 SHA-256(`MessageDigest.getInstance("SHA-256")`)을 한 가지 문자열 형식(예: 16진수)으로 통일한 값 |

**`SessionStore`** (member.domain 인터페이스) — refresh 세션을 저장소에 넣고 빼는 포트. 서비스가 Redis를 모르게 하려고 인터페이스로 둔다. 구현 `RedisSessionStore`(member.infrastructure)의 의존성은 `StringRedisTemplate`, `rotate.lua`를 담은 `DefaultRedisScript<Long>`이다.

| 메서드 | 하는 일 | 비고 |
|---|---|---|
| `void create(UUID sessionId, UUID memberId, String refreshHash, String device, Duration ttl)` | 로그인·가입 직후 세션을 만든다 | `auth:session:{sessionId}` 해시에 `memberId`·`refreshHash`·`device`(`null`이면 그 필드는 뺀다, 공통 규칙 T)를 저장하고 `auth:member-sessions:{memberId}` 집합에 sessionId를 더한다. 두 키 모두 `ttl`로 만료 설정 |
| `Optional<UUID> findMemberId(UUID sessionId)` | 세션 주인 조회 | 해시를 읽어(`entries`) 비어 있으면(없음·만료) `Optional.empty()`, 아니면 `memberId` 필드를 UUID로 |
| `boolean rotate(UUID sessionId, String expectedHash, String newHash, Duration ttl)` | **저장된 `refreshHash`가 `expectedHash`일 때만** `newHash`로 바꾼다(아래 `rotate.lua`) | `true`=바꿨다. `false`=해시가 달랐다(이미 쓴 refresh)·세션이 그 사이에 사라졌다 |
| `void delete(UUID sessionId)` | 세션 하나를 폐기한다 | 해시 키를 지우고 회원의 집합에서도 뺀다. 집합에서 빼려면 memberId가 필요하니 지우기 **전에** 해시에서 읽어 둔다. 없는 세션이어도 오류가 아니다 |
| `void deleteAllOf(UUID memberId)` | 그 회원의 모든 세션 폐기 | 집합의 sessionId마다 해시 키를 지우고 집합을 지운다 |
| `void deleteAllOfExcept(UUID memberId, UUID keepSessionId)` | 한 세션만 남기고 폐기 | 비밀번호 변경이 쓴다(4-2). `keepSessionId`는 건드리지 않는다 |

- `rotate`가 `auth:member-sessions:{memberId}`의 TTL도 연장해야 하는지(공통 규칙 T 표는 "세션 생성·갱신 때 연장"이라고 한다)는 정해져 있지 않다. `rotate`의 인자에 memberId가 없고 스크립트도 그 키를 만지지 않는다(확인 필요)

`rotate.lua` — "저장된 해시가 기대한 값이면 새 해시로 바꾼다"를 **Redis 안에서 한 번에** 실행한다. GET → 비교 → SET을 자바에서 따로 하면 같은 refresh로 온 두 요청이 둘 다 비교를 통과할 수 있기 때문이다(리뷰 질문 참고). 위치는 `src/main/resources/redis/rotate.lua`.
```lua
-- KEYS[1] = auth:session:{id}, ARGV[1] = expectedHash, ARGV[2] = newHash, ARGV[3] = ttlSeconds
if redis.call('HGET', KEYS[1], 'refreshHash') == ARGV[1] then
  redis.call('HSET', KEYS[1], 'refreshHash', ARGV[2])
  redis.call('EXPIRE', KEYS[1], ARGV[3])
  return 1
end
return 0
```

- 자바에서 부르는 형태: `Long r = redis.execute(rotateScript, List.of("auth:session:" + sessionId), expectedHash, newHash, String.valueOf(ttl.getSeconds()));` → `r`이 `1L`이면 `true`. `rotateScript`는 `DefaultRedisScript<Long>`에 `setLocation(new ClassPathResource("redis/rotate.lua"))`, `setResultType(Long.class)`를 한 것이다(확인 필요: 4.x에서 같은 이름인지. 이 객체를 `RedisSessionStore` 안에서 만들지 `@Bean`으로 둘지는 정해져 있지 않다)
- 해시 비교가 자바의 `MessageDigest.isEqual`이 아니라 Redis 안의 `==`에서 일어나, 공통 규칙 T의 "비교는 `isEqual`"과 맞지 않는다. 어느 쪽을 따를지 문서에 없다(확인 필요)

**`TokenIssuer`** (member.application, `@Component`) — **세션 생성과 토큰 발급을 한곳에 모은다.** 가입·로그인·갱신(4-1)과 인증 확인·비밀번호 변경(4-2), 소셜 로그인·가입(4-3)이 모두 "access + refresh 한 쌍"이 필요한데, 호출할 때마다 `JwtProvider`·`SessionStore`·TTL을 조합하면 코드가 흩어지기 때문이다. 트랜잭션을 열지 않고 호출한 서비스의 트랜잭션 안에서 실행된다(Redis 쓰기는 DB 롤백으로 되돌아가지 않는다). 의존성: `JwtProvider`, `SessionStore`, `JwtProperties`.

| 메서드 | 하는 일 | 입력 → 반환 |
|---|---|---|
| `TokenResult issueNewSession(Member member, String device)` | **새 세션을 만들고** 토큰 한 쌍을 발급한다 | 회원·기기 정보 → `TokenResult` |
| `TokenResult reissue(Member member, UUID sessionId, RefreshToken refresh)` | **기존 세션**에 대한 새 access를 만들고, 이미 정해진 `refresh`와 묶어 돌려준다(세션은 만들지 않는다) | 회원·세션·**이미 회전이 끝난 새 refresh** → `TokenResult` |
| `TokenResult reissueAccess(Member member, UUID sessionId)` | **기존 세션의 access만** 새로 만든다. 세션·refresh는 건드리지 않는다. 인증 확인·비밀번호 변경처럼 세션은 그대로이고 클레임(`ev`·`tv`)만 바뀔 때 쓴다 | 회원·세션 → `TokenResult`(`refreshToken = null`, `refreshExpiresInSeconds = 0`. refresh 원문은 서버에 해시로만 있어 다시 줄 수 없으므로 클라이언트가 기존 refresh를 계속 쓴다) |

`issueNewSession` 순서
1. `UUID sessionId`를 새로 만든다(생성 방식은 문서에 지정이 없다. 프로젝트의 ID 생성기는 `Ids.newId()`, 확인 필요)
2. `RefreshToken refresh = RefreshToken.generate(sessionId)`
3. `sessionStore.create(sessionId, member.getId(), refresh.secretHash(), device, refreshTokenTtl)`
4. `String access = jwtProvider.issue(member.getId(), member.getRole().name(), sessionId)` — 4-2에서 `emailVerified`, 4-4에서 `tokenVersion` 인자가 늘어 이 호출을 고친다(위 표, 4-2 2), 4-4 1))
5. `new TokenResult(access, jwtProvider.accessTokenTtl().getSeconds(), refresh.value(), refreshTokenTtl.getSeconds())`

`reissue`는 위 2·3을 하지 않고 4·5만 한다(5의 refresh는 인자로 받은 `refresh`).

**`SignupService`·`LoginService` 변경** — 이 단계부터 두 서비스는 `JwtProvider.issue`를 직접 부르지 않는다. 지금은 `JwtProvider`를 주입받아 access만 만들고 `new TokenResult(access, ttl)`을 직접 만든다.
- `SignupService.signUp`(`@Transactional`): 의존성 `JwtProvider`를 `TokenIssuer`로 바꾸고, `memberRepository.save` 뒤 `tokenIssuer.issueNewSession(savedMember, device)`가 돌려준 `TokenResult`를 `SignupResult(savedMember.getId(), token)`에 넣는다.
- `LoginService.login`(`@Transactional(readOnly = true)`): 의존성을 같게 바꾸고 `member.verifyCanLogin()` 뒤 `return tokenIssuer.issueNewSession(member, device)`.
- `device`를 서비스까지 전달하는 방법은 아래 4)의 `AuthController` 표 뒤 확인 필요 항목과 같다.

`TokenRefreshService` (member.application, `@Service`) — **refresh 한 장으로 새 access + refresh를 받는 유스케이스.** `AuthController.refresh`가 부른다. 의존성: `SessionStore`, `MemberRepository`, `TokenIssuer`, `JwtProperties`(`rotate`에 넘길 `refreshTokenTtl`).

`@Transactional(readOnly = true) TokenResult refresh(String rawRefresh)` — 입력은 클라이언트가 보낸 refresh 문자열 그대로. 순서:
1. `RefreshToken refresh = RefreshToken.parse(rawRefresh)` — 형식이 틀리면 401 `UNAUTHORIZED`
2. `UUID memberId = sessionStore.findMemberId(refresh.sessionId())` — 없으면(만료·로그아웃·이미 폐기) 401 `UNAUTHORIZED`
3. `RefreshToken newRefresh = RefreshToken.generate(refresh.sessionId())` → `sessionStore.rotate(sessionId, refresh.secretHash(), newRefresh.secretHash(), refreshTokenTtl)`. **`false`면 이미 쓴 refresh가 다시 온 것(재사용)**이다. `sessionStore.delete(sessionId)`로 세션을 폐기하고 401 `REFRESH_REUSED`. 같은 refresh로 동시에 두 요청이 오면 한쪽만 `true`를 받고 다른 쪽은 여기로 와서 세션이 폐기된다(정책의 "엄격"). 이긴 쪽이 받은 새 refresh도 같이 죽는다
4. `Member member = memberRepository.findById(memberId)` → `member.verifyCanLogin()`. BANNED는 403 `MEMBER_BANNED`, WITHDRAWN은 401 `LOGIN_FAILED`다. 실패하면 `sessionStore.delete(sessionId)` 후 그 예외를 던진다(Redis 호출은 예외가 나도 되돌아가지 않아 삭제가 남는다). 회원 행이 없는 경우는 문서에 정해져 있지 않다(확인 필요)
5. `return tokenIssuer.reissue(member, sessionId, newRefresh)` — 현재 회원의 role로 만든 새 access(같은 sessionId) + `newRefresh`

`LogoutService` (member.application, `@Service`) — 로그아웃. 의존성 `SessionStore`. `AuthController.logout`이 부른다.

`void logout(UUID memberId, UUID sessionId, boolean all)`
- `all == false`: `sessionStore.delete(sessionId)` — 지금 쓰는 기기(토큰의 `sid`)만
- `all == true`: `sessionStore.deleteAllOf(memberId)` — 모든 기기. 4-4에서 여기에 tokenVersion 올리기가 붙는다
- DB를 읽지 않으므로 이 단계에서는 `@Transactional`이 필요 없다. 이미 폐기된 세션이어도 오류가 아니라 204다
- 주의: access는 서버 상태가 없어서 로그아웃해도 만료(15분)까지 통한다. 바로 막는 것은 4-4(tokenVersion)다. 이 단계에서 끊는 것은 refresh뿐이다

**4) API 변경**

| API | 요청 | 응답 |
|---|---|---|
| `POST /api/auth/signup`, `POST /api/auth/login` | 그대로 | 응답에 `refreshToken`, `refreshExpiresIn` 추가 |
| `POST /api/auth/refresh` | `RefreshRequest(@NotBlank String refreshToken)` | 200 `TokenResponse` |
| `POST /api/auth/logout?all=false` | (로그인 필요) | 204 — `all=false`면 현재 세션(토큰의 `sid`), `true`면 전부(4-4에서 tokenVersion도 올림) |

`AuthController`(기존)에 더하거나 바꾸는 메서드

| 메서드 | 파라미터 | 하는 일 → 응답 |
|---|---|---|
| `signUp`·`login` (변경) | 기존 요청 바디 + `@RequestHeader(value = "User-Agent", required = false) String userAgent` | 서비스가 `TokenIssuer.issueNewSession(member, device)`를 부르게 `device`로 넘긴다 → 기존과 같은 응답에 refresh 두 필드 |
| `refresh` (`POST /refresh`) | `@Valid @RequestBody RefreshRequest request` | `tokenRefreshService.refresh(request.refreshToken())` → 200 `TokenResponse.from(result)` |
| `logout` (`POST /logout`) | `@AuthenticationPrincipal AuthClaims claims`, `@RequestParam(defaultValue = "false") boolean all` | `logoutService.logout(claims.memberId(), claims.sessionId(), all)` → `ResponseEntity.noContent().build()`(204) |

- `User-Agent`를 200자로 자르는 곳과, `device`를 서비스까지 전달하는 방법(`SignupCommand`·`LoginCommand`에 필드로 더할지 인자로 더할지)은 문서에 정해져 있지 않다(확인 필요). `User-Agent`가 없으면 `null`이다
- `/api/auth/refresh`는 공개다. access가 이미 만료됐을 때 부르는 API라서 인증을 요구하면 안 된다. 현재 `SecurityConfig`의 `/api/auth/**` 허용에 이미 들어간다
- `SecurityConfig`: `.requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated()`를 `/api/auth/**` 허용보다 **먼저** 둔다. `authorizeHttpRequests`는 위에서부터 처음 맞는 규칙만 적용하므로, 순서가 반대면 `/api/auth/**`가 먼저 맞아 인증 없이 통과하고 `claims`가 `null`이 된다

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 이 단계에서 추가하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다. `TestFixtures`가 `StringRedisTemplate`을 주입받는다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `sessionSnapshotOf(UUID sessionId)` → `SessionSnapshot` | `redis.opsForHash().entries("auth:session:" + sessionId)`와 `redis.getExpire(key, TimeUnit.SECONDS)`를 한 번에 읽는다. 해시의 키·값은 `Object`로 오므로 `Map<String, String>`으로 바꿔 담는다. `SessionSnapshot`은 `support/`의 record(`Map<String, String> fields`, `long ttlSeconds`)이고 `exists()`(`fields`가 비어 있지 않다), `memberId()`(`UUID`, 없으면 `null`), `refreshHash()`, `device()`(필드가 없으면 `null`), `fieldNames()`, `values()`, `ttlSeconds()`(키가 없으면 -2)를 가진다 | 4-1 `TokenRefreshTest`·`RefreshConcurrencyTest`, 세션을 확인하는 4-2~4-4 테스트 |
| `sessionIdsOf(UUID memberId)` → `Set<UUID>` | `redis.opsForSet().members("auth:member-sessions:" + memberId)`를 `UUID`로 바꾼다. 키가 없으면 빈 집합(Redis는 마지막 원소를 빼면 집합 키를 지우므로 세션이 없을 때도 빈 집합이다) | 위와 같음 |

이미 있는 헬퍼와 설정은 다음과 같다.
- `IntegrationTestSupport`: Redis 컨테이너와 `@AfterEach`의 `flushDb()`는 위 "할 일" 1)에 있다.
- `TestFixtures`의 기존 `signup`·`login`(access 토큰만 돌려준다)은 그대로 쓴다. refresh가 필요한 테스트는 `TokenRefreshTest`의 private 메서드(이름은 권장안)로 얻는다. 4-2~4-4 웹 테스트도 같은 이름·동작의 메서드를 쓴다(여러 클래스가 쓰므로 `support`로 옮길지는 자유). `Tokens login(String email, String userAgent)` — `POST /api/auth/login`(`User-Agent` 헤더는 `userAgent`가 null이면 생략), 200을 확인하고 응답의 `accessToken`·`refreshToken`을 `Tokens` 값으로 돌려준다. `ResultActions refresh(String refreshToken)` — `POST /api/auth/refresh` 본문 `{"refreshToken":"..."}`의 `ResultActions`를 돌려준다.
- `LoginService.login`·`SignupService.signUp`에 `device`를 넘기는 방식은 문서에 없다(위 "할 일" 4)의 확인 필요). `RefreshConcurrencyTest`가 서비스를 직접 부를 때는 그 결정에 맞춘다.

헬퍼를 쓰는 방법은 다음과 같다.
- 응답: `jsonPath`. 에러 응답의 코드는 `$.code`. 토큰 응답의 필드는 `$.accessToken`, `$.tokenType`(`Bearer`), `$.expiresIn`(초), `$.refreshToken`, `$.refreshExpiresIn`(초)이다. 값이 없어야 하는 필드는 `jsonPath("$.refreshToken").doesNotExist()`(필드가 없거나 null이면 통과)로 확인한다.
- access 토큰 안의 값: `jwtProvider.parse(accessToken).orElseThrow()`가 돌려주는 `AuthClaims`의 `memberId()`·`role()`·`sessionId()`. 4-2부터 `emailVerified()`, 4-4부터 `tokenVersion()`이 더 생긴다.
- refresh 문자열 안의 값: `RefreshToken.parse(refresh)`의 `sessionId()`·`secret()`·`secretHash()`.
- Redis 세션: `SessionSnapshot s = testFixtures.sessionSnapshotOf(sessionId)` 한 번으로 읽는다. 회원의 세션 목록은 `testFixtures.sessionIdsOf(memberId)`로 읽는다. 전체 키 이름은 주입받은 `StringRedisTemplate redis`의 `redis.keys("*")`. 키를 읽는 호출·`Map` 변환·캐스팅을 테스트에 쓰지 않는다.
- 시간 의존 값(세션 7일, 인증코드 5분 등)은 `Clock`을 바꾸지 않고 Redis 키를 지우거나(`redis.delete(key)`) 남은 시간을 줄여(`redis.expire(key, Duration.ofSeconds(1))`) 흉내 낸다. Redis 키 만료는 Part 2 공통 규칙 M의 "DB 시각 컬럼을 과거로 UPDATE"에 해당하는 방법이다.

**공통 준비**: 회원 1명 `member@test.com`(`testFixtures.signup`, 비밀번호 `pass1234`, 닉네임 `member`) → `memberId`. 이 단계부터 `signup`이 세션을 하나 만들지만(`SignupService`가 `issueNewSession` 호출) 아무도 그 refresh를 쓰지 않는다. 로그인 요청은 `POST /api/auth/login` 본문 `{"email":"member@test.com","password":"pass1234"}`이고, 기기는 `User-Agent` 헤더로 구분한다.

#### `member/domain/RefreshTokenTest` (Spring 없이)

| 케이스 | 동작 | 확인할 것 |
|---|---|---|
| 왕복 | `sessionId = UUID.randomUUID()` → `t = RefreshToken.generate(sessionId)` → `parsed = RefreshToken.parse(t.value())` | `t.value()` = `sessionId + "." + t.secret()` / `t.secret()`이 정규식 `^[A-Za-z0-9_-]{43}$`와 일치(32바이트, Base64URL, 패딩 없음) / `parsed.sessionId()` = `sessionId`, `parsed.secret()` = `t.secret()` / `parsed.secretHash()` = `t.secretHash()`이고 `t.secretHash()` ≠ `t.secret()` / `generate(sessionId)`를 한 번 더 부르면 `secret`이 다르다 |
| 잘못된 문자열 | `@ParameterizedTest` 입력 `null`, `""`, `"abc"`(점 없음), `"a.b.c"`(3조각), `"not-a-uuid.secret"`(앞이 UUID 아님)을 `RefreshToken.parse(입력)`에 넣는다 | 다섯 입력 모두 `BusinessException`, `errorCode` = `UNAUTHORIZED` |

#### `member/web/TokenRefreshTest` (공통 준비 사용)

이 클래스의 private 메서드 `login(email, userAgent)`·`refresh(refreshToken)`은 위 "테스트 준비"에 있다.

**1. 가입·로그인 응답에 refresh가 들어 있다** — 동작: `POST /api/auth/signup` 본문 `{"email":"new@test.com","password":"pass1234","nickname":"newbie","name":"테스터"}`.
- 응답: 201, `$.memberId`가 UUID, `$.tokenType` = `Bearer`, `$.expiresIn` = 900, `$.refreshExpiresIn` = 604800, `$.accessToken`·`$.refreshToken`이 비어 있지 않다.
- `sid` = `RefreshToken.parse($.refreshToken).sessionId()`가 `parse($.accessToken).sessionId()`와 같다.
- `s = sessionSnapshotOf(sid)`: `s.memberId()` = `$.memberId`, `s.refreshHash()` = `RefreshToken.parse($.refreshToken).secretHash()`, `s.device()` = null(`User-Agent`를 보내지 않았다), `s.ttlSeconds()` 604000~604800.
- 로그인(`User-Agent: device-A`)의 응답도 같은 필드를 가지며 `sessionSnapshotOf(sid).device()` = `device-A`다.
- `User-Agent`를 `"x"` 250자로 보내 로그인하면 `sessionSnapshotOf(sid).device()`의 길이 = 200이다.

**2. 회전과 재사용 탐지** — 준비: `login("member@test.com", "device-A")` → `access1`, `refresh1`, `sid`(= `refresh1`의 sessionId). `redis.expire("auth:session:" + sid, Duration.ofSeconds(100))`로 세션의 남은 시간을 줄여 둔다.
- 동작 1: `refresh(refresh1)`.
- 확인 1: 200. `$.refreshToken`(= `refresh2`) ≠ `refresh1`이고 `RefreshToken.parse(refresh2).sessionId()` = `sid`. `parse($.accessToken)`의 `sessionId()` = `sid`, `memberId()` = `memberId`, `role()` = `USER`. `GET /api/members/me`를 `Bearer {$.accessToken}`으로 부르면 200, `$.id` = `memberId`. `s = sessionSnapshotOf(sid)`: `s.refreshHash()` = `RefreshToken.parse(refresh2).secretHash()`(≠ `refresh1`의 해시), `s.memberId()`·`s.device()`(`device-A`)는 그대로, `s.ttlSeconds()` 604000~604800(줄여 둔 100초가 7일로 되돌아왔다).
- 동작 2: `refresh(refresh1)` 다시.
- 확인 2: 401, `$.code` = `REFRESH_REUSED`. `sessionSnapshotOf(sid).exists()` = false, `sessionIdsOf(memberId)`에 `sid`가 없다.
- 동작 3: `refresh(refresh2)`.
- 확인 3: 401, `$.code` = `UNAUTHORIZED`(세션이 폐기돼 없다).

**3. 한 기기의 재사용이 다른 기기를 끊지 않는다** — 준비: `login(…, "device-A")` → `refreshA`·`sidA`, `login(…, "device-B")` → `refreshB`·`sidB`.
- 동작 1: `refresh(refreshA)` → 200. 동작 2: `refresh(refreshA)` → 401 `REFRESH_REUSED`.
- 확인: `sessionSnapshotOf(sidA).exists()` = false, `sessionSnapshotOf(sidB)`는 `exists()` = true이고 `refreshHash()` = `refreshB`의 `secretHash()` 그대로. `sessionIdsOf(memberId)`에 `sidB`가 있고 `sidA`는 없다.
- 동작 3: `refresh(refreshB)` → 200, `$.refreshToken` ≠ `refreshB`.

**4. 로그아웃(현재 기기만)** — 준비: `login(…, "device-A")` → `accessA`·`refreshA`·`sidA`, `login(…, "device-B")` → `refreshB`·`sidB`.
- 동작 1: `POST /api/auth/logout`, `Authorization: Bearer {accessA}`, 파라미터 없음.
- 확인 1: 204, 본문 없음. `sessionSnapshotOf(sidA).exists()` = false, `sessionIdsOf(memberId)`에 `sidA`는 없고 `sidB`는 있다. `refresh(refreshA)` → 401 `UNAUTHORIZED`. `refresh(refreshB)` → 200. `GET /api/members/me`를 `Bearer {accessA}`로 부르면 200(access는 만료까지 유효하다. 이 단계의 로그아웃은 refresh만 끊는다).
- 동작 2: 같은 `accessA`로 `POST /api/auth/logout`을 다시 → 204(이미 폐기된 세션이어도 오류가 아니다).
- 동작 3: `Authorization` 없이 `POST /api/auth/logout` → 401, `$.code` = `UNAUTHORIZED`. `sessionSnapshotOf(sidB).exists()` = true. (`SecurityConfig`에서 `logout` 규칙이 `/api/auth/**`보다 앞에 있는지 확인한다.)

**5. 전체 로그아웃(`?all=true`)** — 준비: 다른 회원 `other@test.com`을 `signup` 후 `login("other@test.com", "device-X")` → `sidOther`. 대상 회원은 `login(…, "device-A")` → `accessA`·`refreshA`·`sidA`, `login(…, "device-B")` → `refreshB`·`sidB`.
- 동작: `POST /api/auth/logout?all=true`, `Bearer {accessA}`.
- 확인: 204. `sessionSnapshotOf(sidA).exists()`·`sessionSnapshotOf(sidB).exists()` = false, `sessionIdsOf(memberId)`가 비어 있다(가입 때 만든 세션도 함께 지워졌다). `refresh(refreshA)`·`refresh(refreshB)` 모두 401 `UNAUTHORIZED`. 다른 회원: `sessionSnapshotOf(sidOther).exists()` = true.

**6. Redis에 refresh 원문이 없다** — 준비: `login(…, "device-A")` → `refresh1`, `secret1` = `RefreshToken.parse(refresh1).secret()`, `sid`. 동작: `refresh(refresh1)` → `refresh2`, `secret2` = `RefreshToken.parse(refresh2).secret()`.
- `redis.keys("*")`의 어떤 키 이름에도 `secret1`·`secret2`가 들어 있지 않다.
- `s = sessionSnapshotOf(sid)`: `s.fieldNames()`가 정확히 `memberId`, `refreshHash`, `device` 세 개이고, `s.values()`의 어떤 값도 `secret1`·`secret2`와 같거나 포함하지 않는다. 키 이름 확인과 이 확인을 로그인 직후(`secret1`)와 회전 직후(`secret2`) 두 시점 모두에서 한다. `refreshHash`가 `secretHash()`와 같은지는 케이스 1·2가 확인한다.
- `sessionIdsOf(memberId)`의 원소는 sessionId(UUID)뿐이다(`secret`이 섞여 있으면 UUID로 읽지 못해 실패한다).

**7. 잘못된 refresh 요청** — 준비: `login(…, "device-A")` → `refresh1`, `sid`. 각 요청 전후로 `redis.keys("*")` 집합이 같다는 것도 확인한다(세션이 새로 생기거나 지워지지 않는다). 단 마지막 항목은 세션이 지워지는 것이 확인 대상이다.
- `refresh("abc")`, `refresh("not-a-uuid.xyz")`, `refresh(UUID.randomUUID() + ".xyz")`(형식은 맞고 없는 세션) → 모두 401, `$.code` = `UNAUTHORIZED`.
- 본문 `{"refreshToken":""}`과 `{}` → 400, `$.code` = `INVALID_REQUEST`.
- `sid + "." + "x".repeat(43)`(세션은 있고 secret이 틀림) → 401, `$.code` = `REFRESH_REUSED`이고 `sessionSnapshotOf(sid).exists()` = false다(해시가 다르면 재사용으로 본다). 이어 `refresh(refresh1)` → 401 `UNAUTHORIZED`.

**8. 정지·탈퇴 회원의 refresh** — 회원 두 명(`ban@test.com`, `gone@test.com`)을 각각 `login(…, "device-A")` → `refreshBan`·`sidBan` / `refreshGone`·`sidGone`.
- `testFixtures.changeStatus(banId, "BANNED")` → `refresh(refreshBan)` → 403, `$.code` = `MEMBER_BANNED`. `sessionSnapshotOf(sidBan).exists()` = false.
- `testFixtures.changeStatus(goneId, "WITHDRAWN")` → `refresh(refreshGone)` → 401, `$.code` = `LOGIN_FAILED`. `sessionSnapshotOf(sidGone).exists()` = false.

#### `member/application/RefreshConcurrencyTest` (공통 준비 사용, `@RepeatedTest(10)`)

준비: `login`으로 `refresh1`·`sid`를 얻는다(`LoginService`를 직접 부르거나 MockMvc를 쓴다). 동작: `ConcurrencyRunner.run(2, () -> tokenRefreshService.refresh(refresh1))` — 두 스레드가 같은 `refresh1`로 동시에 갱신한다. 결과는 `Result.value()`(`TokenResult`)와 `Result.error()`(`BusinessException`)로 읽는다.
- 두 결과 중 정확히 하나는 `ok()`이고 `value().refreshToken()`이 `refresh1`과 다른 `refresh2`다(둘 다 ok이거나 둘 다 에러이면 실패).
- 나머지 하나는 `BusinessException`이고 `errorCode` = `REFRESH_REUSED`다.
- `sessionSnapshotOf(sid).exists()` = false(진 쪽이 세션을 폐기했다).
- 이긴 쪽이 받은 `refresh2`로 `tokenRefreshService.refresh(refresh2)` → `BusinessException`, `errorCode` = `UNAUTHORIZED`(이긴 쪽의 새 refresh도 함께 죽는다).

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
| 발송 제한 | 같은 이메일로 60초에 1번, 시간당 5번까지만 보낸다(`EmailCodeStore.checkSendLimit`) |
| 시도 제한 | 확인에 5번 틀리면 코드를 지운다(`EmailCodeStore.verify`) |
| 토큰 클레임으로 인가 | 액세스 토큰에 `ev`(이메일 인증 여부) 클레임을 넣고 인터셉터가 확인. 인증 확인 응답으로 받은 새 access부터 반영된다 |

**정책 (이 단계에서 정함)**
- 인증코드 6자리 숫자(`SecureRandom`), 유효 5분. 목적(purpose): `VERIFY_EMAIL`, `RESET_PASSWORD`.
- 발송: 같은 이메일로 60초에 1번, 시간당 5번(초과 시 429). 확인: 5번 틀리면 코드 무효(새로 발송해야 함).
- 이메일 인증이 필요한 API: 체크아웃, 결제 승인, 가게 개설 (구독 신청은 5-2에서 같은 어노테이션을 붙인다).
- 비밀번호 재설정 요청은 **가입 여부와 상관없이 항상 202**(가입된 이메일을 알아낼 수 없게).
- 비밀번호 변경·재설정 후: 변경은 현재 세션만 남기고 나머지 세션 폐기, 재설정은 전부 폐기. (4-4에서 tokenVersion도 올린다)

### 할 일

**구현 순서**: ① 마이그레이션·`Member`·에러 코드(1) → ② `ev` 클레임(2) → ③ 메일 포트(3) → ④ `EmailCodePurpose` → `EmailCodeStore` → `EmailVerificationService`(4) → ⑤ `RequireVerifiedEmail` → `VerifiedEmailInterceptor` → `WebConfig`(5) → ⑥ `PasswordService`(6) → ⑦ 컨트롤러·`SecurityConfig`(7) → ⑧ `TestFixtures`(8) → 테스트.

**1) 마이그레이션·도메인·에러 코드**

| # | 대상 | 규격 |
|---|---|---|
| 1 | 마이그레이션 `V{...}__member_add_email_verified_at.sql` | `member.member`에 `email_verified_at timestamptz NULL`(null = 미인증). 기존 로컬 개발 데이터는 필요하면 로컬 DB에서 `UPDATE member.member SET email_verified_at = now()`를 직접 실행한다 |
| 2 | `Member.emailVerifiedAt` + `Member.verifyEmail(Instant now)` | `@Column(name = "email_verified_at") private Instant emailVerifiedAt;`(`ddl-auto: validate`라서 마이그레이션 컬럼과 타입이 안 맞으면 기동이 실패한다). `verifyEmail`: 이미 인증됐으면 409 `EMAIL_ALREADY_VERIFIED`, 아니면 `emailVerifiedAt = now` |
| 3 | `Member.changePassword(String encodedPassword)` | 이미 `passwordEncoder.encode`한 해시를 받아 `passwordHash`에 넣는다(인코딩은 서비스가 한다) |
| 4 | 에러 코드 (공통 규칙 U) | `EMAIL_NOT_VERIFIED`(403)·`RATE_LIMITED`(429)는 `CommonErrorCode`에, `EMAIL_CODE_INVALID`(400)·`EMAIL_ALREADY_VERIFIED`(409)·`PASSWORD_MISMATCH`(400)는 `MemberErrorCode`에 추가한다 |

**2) 토큰 클레임 `ev`**

이메일 인증 여부는 DB를 매번 읽지 않고 **액세스 토큰의 `ev` 클레임**으로 판단한다. 그래서 인증을 마친 직후 받은 새 토큰부터 반영된다.

- `AuthClaims`에 `boolean emailVerified` 추가(`ev` 클레임, 4-1의 표). `JwtProvider`는 `.claim("ev", emailVerified)`로 넣고 `parse`에서 `claims.get("ev", Boolean.class)`로 읽는다
- `TokenIssuer`의 `issueNewSession`·`reissue`·`reissueAccess`가 `jwtProvider.issue(...)`의 `emailVerified` 인자로 `member.getEmailVerifiedAt() != null`을 넘긴다
- 기존 테스트의 `issue` 호출(`JwtProviderTest` 2곳, `MemberControllerTest` 1곳)에 인자를 더한다

**3) 메일**: 3-5의 `common.mail.MailSender`(`void send(String to, String title, String body)`, 실패 시 예외)를 쓴다. 발송은 트랜잭션 밖에서 한다 — 메일 서버를 기다리는 동안 DB 커넥션을 잡고 있지 않도록, 발송하는 서비스 메서드에는 `@Transactional`을 붙이지 않는다(공통 규칙 K와 같은 이유).

**4) member — 인증코드**

인증코드 흐름: `send`가 코드를 만들어 해시만 Redis에 저장하고 메일로 원문을 보낸다 → 사용자가 코드를 입력하면 `confirm`이 해시를 비교하고 맞으면 `email_verified_at`을 채운다.

`EmailCodePurpose` (member.domain enum) `VERIFY_EMAIL, RESET_PASSWORD` — 코드의 용도. 같은 이메일로 인증용·재설정용 코드가 따로 살도록 Redis 키(`auth:email-code:{purpose}:{email}`)에 들어간다.

`EmailCodeStore` (member.domain 인터페이스) + `RedisEmailCodeStore` (infrastructure) — 인증코드와 **발송 제한**을 Redis에 두는 저장소. `EmailVerificationService`·`PasswordService`(4-2)와 `OAuthLoginService`(4-3)가 쓴다. `email`은 호출한 쪽이 `Member.normalizeEmail`로 정규화한 값이다.

| 메서드 | 하는 일 | 입력 → 반환 |
|---|---|---|
| `void checkSendLimit(String email)` | 지금 보내도 되는지 확인한다. 안 되면 예외 | 위반이면 429 `RATE_LIMITED`. ① `setIfAbsent("auth:email-cooldown:{email}", "1", 60초)`가 `false`면 60초 안에 이미 보낸 것이므로 위반 ② `increment("auth:email-hourly:{email}")` 결과가 1이면 `expire(…, 1시간)`을 건다. 5를 넘으면 위반 |
| `void save(EmailCodePurpose p, String email, String code)` | 새 코드를 저장한다. **원문 `code`를 받아 안에서 SHA-256으로 해시한다**(`verify`와 같다). 이전 코드는 덮어쓰고 `attempts`는 0으로 돌아간다 | `auth:email-code:{p}:{email}` 해시에 해시값을 `codeHash`로, `attempts = 0`을 쓰고 `expire` 5분 |
| `boolean verify(EmailCodePurpose p, String email, String code)` | 입력한 코드가 맞는지 확인한다. **원문 `code`를 받아 안에서 SHA-256으로 해시한다** | ① `entries`로 읽어 비어 있으면(없음·만료·5번 틀려 삭제됨) `false` ② 입력의 해시와 저장된 `codeHash`를 `MessageDigest.isEqual`로 비교 ③ 맞으면 키를 지우고 `true` ④ 틀리면 `HINCRBY attempts 1`, 결과가 5 이상이면 키를 지우고 `false` |

- 코드 만들기: `String.format("%06d", secureRandom.nextInt(1_000_000))` — `000123`처럼 앞이 0이어도 6자리를 지킨다

`EmailVerificationService` (member.application, `@Service`) — **로그인한 회원이 자기 이메일을 인증하는 유스케이스.** 의존성: `MemberRepository`, `EmailCodeStore`, `MailSender`, `Clock`(`confirm`의 `now`). `confirm`의 새 access 발급 때문에 `TokenIssuer`도 필요하다.

`void send(UUID memberId)` — 순서
1. `memberRepository.findById(memberId)` — 이미 `emailVerifiedAt != null`이면 409 `EMAIL_ALREADY_VERIFIED`. 메서드에 `@Transactional`이 없어 조회는 리포지토리가 짧게 연 읽기 트랜잭션에서 끝난다
2. `emailCodeStore.checkSendLimit(email)`
3. 코드 생성 → `emailCodeStore.save(VERIFY_EMAIL, email, code)`
4. `mailSender.send(email, "[MyRoutine] 이메일 인증코드", "인증코드: " + code)`

`TokenResult confirm(UUID memberId, UUID sessionId, String code)` — `@Transactional`. `sessionId`는 `claims.sessionId()`. 순서
1. 회원 조회(코드를 확인하려면 이메일이 필요하다. 이 트랜잭션 안에서 읽어야 `verifyEmail`의 변경이 커밋 때 UPDATE로 나간다)
2. `emailCodeStore.verify(VERIFY_EMAIL, email, code)`가 `false`면 400 `EMAIL_CODE_INVALID`
3. `member.verifyEmail(clock.instant())`
4. `return tokenIssuer.reissueAccess(member, sessionId)` — `ev = true`인 새 access를 돌려준다. 이 트랜잭션 안에서 `verifyEmail`이 끝났으므로 `member.getEmailVerifiedAt() != null`이 반영된다

- 응답은 `reissueAccess`의 결과라 `refreshToken`은 null, `refreshExpiresIn`은 0이다. 세션이 그대로라 회전할 이유가 없어 클라이언트는 기존 refresh를 계속 쓴다.

**5) 인증 여부로 막기**

- `common.security.RequireVerifiedEmail`: `@Target(METHOD) @Retention(RUNTIME)` — 값이 없는 표식 어노테이션. 컨트롤러 메서드에 붙이면 "인증된 이메일만"이라는 뜻이다
- `common.security.VerifiedEmailInterceptor implements HandlerInterceptor` (`@Component`) — 컨트롤러 메서드가 호출되기 **전에** 표식을 확인해 막는 곳. `preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)`의 순서:
  1. `handler instanceof HandlerMethod hm`가 아니면(정적 리소스 등) `true`를 돌려 통과시킨다
  2. `hm.getMethodAnnotation(RequireVerifiedEmail.class) == null`이면 `true`
  3. `SecurityContextHolder.getContext().getAuthentication().getPrincipal()`이 `AuthClaims`이고 `emailVerified()`가 `false`면 `throw new BusinessException(EMAIL_NOT_VERIFIED)`(403). 인터셉터에서 던진 예외도 `GlobalExceptionHandler`가 처리한다
  4. 그 밖에는 `true`
- 등록: `common.config`에 `WebConfig implements WebMvcConfigurer`를 새로 만들고(현재 없다) `addInterceptors(InterceptorRegistry registry)`에서 `registry.addInterceptor(verifiedEmailInterceptor)`. 인터셉터는 Spring Security 필터 체인 뒤에 실행되므로 이 시점에 `SecurityContextHolder`에 인증이 들어 있다. `@Idempotent` AOP보다 먼저 실행되어 미인증 회원은 멱등 키 처리 전에 403이 된다
- 붙일 곳: `POST /api/orders/checkout`, `POST /api/orders/{id}/payment/confirm`, `POST /api/shops` 컨트롤러 메서드

**6) 비밀번호**

`PasswordService` (member.application, `@Service`) — **로그인한 회원의 비밀번호 변경과 이메일 코드로 하는 재설정**을 맡는다. 의존성: `MemberRepository`, `PasswordEncoder`, `SessionStore`, `EmailCodeStore`, `MailSender`, `TokenIssuer`. 변경·재설정이 비밀번호를 바꾸면 이전 비밀번호로 만든 세션을 정리하는 것이 이 서비스의 핵심이다

`@Transactional TokenResult change(UUID memberId, UUID sessionId, String current, String next)` — `PATCH /api/members/me/password`가 부른다. `sessionId`는 `claims.sessionId()`. 순서
1. `memberRepository.findById(memberId)`(없으면 404 `MEMBER_NOT_FOUND`) → `passwordHash`가 `null`(소셜 가입 회원)이거나 `passwordEncoder.matches(current, passwordHash)`가 `false`면 400 `PASSWORD_MISMATCH`
2. `member.changePassword(passwordEncoder.encode(next))`
3. `sessionStore.deleteAllOfExcept(memberId, sessionId)` — 다른 기기의 refresh를 모두 끊고 지금 기기만 남긴다
4. `return tokenIssuer.reissueAccess(member, sessionId)` — 현재 세션용 새 access를 돌려준다(4-4 이후에는 tokenVersion이 올라 기존 access가 막히므로). `refreshToken`은 `confirm`과 같이 null이다

`void requestReset(String email)` — `POST /api/auth/password-reset`이 부른다. 트랜잭션 없음(메일 발송). 순서
1. `Member.normalizeEmail(email)` → `findByEmail`. 회원이 있고 `ACTIVE`면 `checkSendLimit` → 코드 생성 → `save(RESET_PASSWORD, email, code)` → `mailSender.send`
2. 없거나 `ACTIVE`가 아니면 아무것도 하지 않는다(응답은 같다)
- 존재하는 이메일만 `checkSendLimit`이 429 `RATE_LIMITED`를 던질 수 있어서, 정책의 "항상 202"와 함께 가입 여부가 드러날 수 있다(확인 필요)

`@Transactional void confirmReset(String email, String code, String newPassword)` — `POST /api/auth/password-reset/confirm`이 부른다. 순서
1. `emailCodeStore.verify(RESET_PASSWORD, normalizedEmail, code)`가 `false`면 400 `EMAIL_CODE_INVALID`
2. `findByEmail`로 회원을 읽어 `member.changePassword(passwordEncoder.encode(newPassword))` (코드 확인은 통과했는데 회원이 없는 경우는 문서에 없다. 확인 필요)
3. `sessionStore.deleteAllOf(memberId)` — 재설정은 모든 세션을 끊는다
- 재설정은 로그인하지 않은 요청이라 토큰을 돌려주지 않는다(204). 사용자는 새 비밀번호로 다시 로그인한다

**7) API**

| API | 권한 | 요청 | 응답 |
|---|---|---|---|
| `POST /api/auth/email-verifications` | 로그인 | 없음. 이 API는 로그인한 회원 전용이다(OAuth 가입용 인증코드 발송은 4-3의 별도 API `POST /api/auth/oauth/signup/email-verifications`) | 202 |
| `POST /api/auth/email-verifications/confirm` | 로그인 | `ConfirmEmailRequest(@NotBlank @Pattern(regexp="^\\d{6}$") String code)` | 200 `TokenResponse` (인증 반영된 새 토큰, 현재 세션 유지) |
| `PATCH /api/members/me/password` | 로그인 | `ChangePasswordRequest(@NotBlank String currentPassword, newPassword: 1-3의 비밀번호 규칙)` | 200 `TokenResponse` |
| `POST /api/auth/password-reset` | 공개 | `PasswordResetRequest(@NotBlank @Email String email)` | 202 (항상) |
| `POST /api/auth/password-reset/confirm` | 공개 | `PasswordResetConfirmRequest(email, code, newPassword)` | 204 |

- `SecurityConfig`: `.requestMatchers("/api/auth/email-verifications/**").authenticated()`를 `/api/auth/**` 허용보다 **먼저** 둔다(4-1의 logout과 같은 이유: 위에서부터 처음 맞는 규칙이 적용된다). 4-3의 가입 토큰용 발송은 별도 공개 API(`/api/auth/oauth/signup/email-verifications`)라 이 규칙에 경로를 더하지 않는다
- 컨트롤러에서 `sessionId`가 필요한 `confirm`·비밀번호 변경은 `@AuthenticationPrincipal AuthClaims claims`로 받는다(4-1과 같다)
- 컨트롤러: 인증코드 두 API와 재설정 두 API는 `AuthController`(`/api/auth`)에, 비밀번호 변경은 `MemberController`(`/api/members`)에 `@PatchMapping("/me/password")`로 더한다. 각 메서드는 위 서비스를 호출한다(`send`·`confirm`·`change`·`requestReset`·`confirmReset`)

**8) 테스트 헬퍼**

- `TestFixtures` 변경: `RequireVerifiedEmail`이 `POST /api/shops`에 붙으면 1-6 이후 테스트가 쓰는 미인증 토큰이 403이 된다. 다음처럼 바꾼다: `signup(email)`은 가입 직후 JDBC로 `UPDATE member.member SET email_verified_at = now() WHERE id = ?`, `token(email)`은 같은 처리 후 **`login(email)`로 토큰을 다시 받는다**(가입 응답의 토큰은 발급 시점에 `ev = false`라서). 미인증 회원이 필요하면 `signupUnverified(email)`·`tokenUnverified(email)`을 추가한다.

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 값을 읽는 방법과 토큰 로그인 메서드(`login(email, userAgent)`, `refresh(refreshToken)`. 4-1 `TokenRefreshTest`의 private 메서드)는 4-1과 같고, 이 단계에서 더하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `signupUnverified(String email)` → `UUID` | 기존 `signup`이 하던 가입 그대로(`email_verified_at` = null) | `EmailVerificationTest`, `RequireVerifiedEmailTest`, `PasswordTest` |
| `signup(String email)` → `UUID`(변경) | 가입 후 JDBC `UPDATE member.member SET email_verified_at = now() WHERE id = ?` | 1-6 이후 모든 테스트(가게 개설 403 방지) |
| `token(String email)` → `String`(변경) | `signup`과 같은 처리 후 `login(email)`로 토큰을 다시 받는다(가입 응답의 토큰은 `ev` = false) | 기존 테스트 |
| `tokenUnverified(String email)` → `String` | `signupUnverified` 후 가입 응답의 access | 미인증 토큰이 필요한 곳 |
| `memberSnapshotOf(UUID memberId)`에 컬럼 추가 | 3-1에서 넓힌 `MemberSnapshot`(`email`, `status`, `role`, `withdrawnAt`은 3-1이 이미 읽는다)에 `emailVerifiedAt`(`Instant`, 미인증이면 `null`)만 더한다. `email_verified_at` 컬럼은 이 단계의 마이그레이션이 만든다 | `EmailVerificationTest`, `RequireVerifiedEmailTest`, `PasswordTest`, 4-3·4-4 |
| `emailAuthSnapshotOf(EmailCodePurpose purpose, String email)` → `EmailAuthSnapshot` | Redis 키 셋을 한 번에 읽는다: 코드 해시 `redis.opsForHash().entries("auth:email-code:{purpose}:{email}")`, 쿨다운 `redis.opsForValue().get("auth:email-cooldown:{email}")`, 시간당 `redis.opsForValue().get("auth:email-hourly:{email}")`와 각 키의 `redis.getExpire(key, TimeUnit.SECONDS)`. `support/`의 record. 코드 키는 `codeExists()`, `codeHash()`(`String`, 없으면 `null`), `attempts()`(`Integer`, 없으면 `null`), `fieldNames()`(`Set<String>`), `codeTtlSeconds()`, 쿨다운 키는 `cooldown()`(`String`, 없으면 `null`), `cooldownTtlSeconds()`, 시간당 키는 `hourly()`(`Integer`, 없으면 `null`), `hourlyTtlSeconds()`로 읽는다. 없는 키의 남은 시간은 -2 | `EmailVerificationTest`, `PasswordTest`, 4-3 `OAuthLoginTest` |

이미 있는 헬퍼: `sessionSnapshotOf`·`sessionIdsOf`(4-1), `memberSnapshotOf`·`countOf`(3-1), `snapshotOf`(2-8a).

헬퍼를 쓰는 방법은 다음과 같다.
- 메일: `FakeMailSender`(3-5)의 `sent()` — `SentMail(to, title, body)` 목록. 인증코드는 `body`에서 정규식 `\d{6}`의 첫 일치로 꺼낸다(`codeOf(mail)`, 테스트 클래스의 private 메서드). 인증 메일은 `to` = 정규화한 이메일, `title` = `[MyRoutine] 이메일 인증코드`, `body` = `인증코드: {코드}`다. 비밀번호 재설정 메일의 제목·본문은 문서에 없다(확인 필요). 코드만 `codeOf`로 꺼낸다. `FakeMailSender`는 `@Primary` 빈이라 별도 등록이 없다.
- 회원: `MemberSnapshot m = testFixtures.memberSnapshotOf(memberId)`(정의는 1-3, 3-1이 `TestFixtures`에 추가) 한 번으로 읽는다. 이 단계에서 `m.emailVerifiedAt()`·`m.passwordHash()`(소셜 가입이면 `null`)·`m.status()`·`m.role()`·`m.email()`을 쓴다. 비밀번호 확인은 `passwordEncoder.matches(평문, m.passwordHash())`. 행 수는 `testFixtures.countOf("orders.orders")`처럼 테이블 이름으로 읽는다(1-3).
- 인증코드와 발송 제한: `EmailAuthSnapshot a = testFixtures.emailAuthSnapshotOf(EmailCodePurpose, email)` 한 번으로 읽는다(`email`은 정규화한 값). 키 이름 검색은 `redis.keys("auth:email-*")`.
- 토큰의 `ev`: `jwtProvider.parse(accessToken).orElseThrow().emailVerified()`.
- 시간 의존: 5분 경과는 `redis.delete(코드 키)`, 60초 경과는 `redis.delete(쿨다운 키)`로 흉내 낸다.

**공통 준비**: 이메일 `verify@test.com`을 `testFixtures.signupUnverified`로 가입(닉네임 `verify`, 비밀번호 `pass1234`, `email_verified_at` = null) → `memberId`. `login("verify@test.com", "device-A")` → `accessUnverified`(`emailVerified()` = false), `refresh0`, `sid`. 인증 발송은 `POST /api/auth/email-verifications`(본문 없음, `Bearer {accessUnverified}`), 확인은 `POST /api/auth/email-verifications/confirm` 본문 `{"code":"123456"}` 형식이다. 각 케이스 끝에 메일은 `@AfterEach`에서 비운다(`FakeMailSender.reset()`).

#### `member/web/EmailVerificationTest`

**1. 발송 → 확인** — 동작 1: 발송.
- 응답: 202.
- 메일: `sent()` 1건, `to` = `verify@test.com`, `title` = `[MyRoutine] 이메일 인증코드`, `body`가 `인증코드: ` + 6자리 숫자(`codeOf`로 `code`를 꺼낸다).
- `a = emailAuthSnapshotOf(VERIFY_EMAIL, "verify@test.com")`: `a.codeTtlSeconds()` 1~300. `a.cooldownTtlSeconds()` 1~60. `a.hourly()` = 1, `a.hourlyTtlSeconds()` 1~3600.
- 동작 2: 확인 — 본문 `{"code":"{code}"}`, `Bearer {accessUnverified}`. 직전에 `before` = `sessionSnapshotOf(sid).refreshHash()`를 읽어 둔다.
- 응답: 200, `$.tokenType` = `Bearer`, `$.expiresIn` = 900, `$.refreshToken` 없음/null, `$.refreshExpiresIn` = 0.
- 응답 토큰: `parse($.accessToken)`의 `emailVerified()` = true, `sessionId()` = `sid`, `memberId()` = `memberId`.
- `memberSnapshotOf(memberId).emailVerifiedAt()`이 null이 아니고 `Instant.now()` 기준 10초 안이다.
- `emailAuthSnapshotOf(VERIFY_EMAIL, "verify@test.com").codeExists()` = false(맞으면 지운다). `sessionSnapshotOf(sid).refreshHash()`가 `before`와 같다(세션·refresh는 건드리지 않았다).
- 기존 `refresh0`으로 `refresh(refresh0)` → 200, `parse($.accessToken).emailVerified()` = true(클라이언트는 기존 refresh를 계속 쓴다).

**2. 60초 안에 재발송** — 동작: 발송 → 202, 곧바로 같은 토큰으로 발송.
- 두 번째 응답: 429, `$.code` = `RATE_LIMITED`.
- 메일은 1건 그대로. 첫 발송 직후 `before = emailAuthSnapshotOf(VERIFY_EMAIL, "verify@test.com")`를 읽어 두고, 두 번째 응답 뒤 읽은 값이 `codeHash()` = `before.codeHash()`(덮어쓰지 않았다), `hourly()` = 1(쿨다운에서 막혀 시간당 카운트를 올리지 않았다)이다.

**3. 시간당 5번 초과** — 동작: 발송(202) → `redis.delete("auth:email-cooldown:verify@test.com")`를 5번 반복한 뒤 6번째 발송.
- 앞의 5번은 모두 202, 메일 5건. 5번째 직후 `emailAuthSnapshotOf(VERIFY_EMAIL, "verify@test.com").hourly()` = 5.
- 6번째 응답: 429, `$.code` = `RATE_LIMITED`. 메일은 5건 그대로, `hourly()` = 6.

**4. 5번 틀린 뒤 맞는 코드** — 준비: 발송 → `code`. 틀린 코드 `wrong` = `code`가 `000000`이 아니면 `000000`, 맞으면 `111111`.
- 동작: 확인을 `wrong`으로 5번 호출한다. 매번 400, `$.code` = `EMAIL_CODE_INVALID`. 3번째 직후 `emailAuthSnapshotOf(VERIFY_EMAIL, "verify@test.com").attempts()` = 3, 4번째 직후 4. 5번째 직후 `codeExists()` = false.
- 이어 맞는 `code`로 확인 → 400, `$.code` = `EMAIL_CODE_INVALID`. `memberSnapshotOf(memberId).emailVerifiedAt()` = null.
- 복구: `redis.delete(쿨다운 키)` → 발송 → 202, 새 코드 `code2`로 확인 → 200, `emailVerifiedAt()`이 채워진다(새로 발송해야 한다).

**5. 만료된 코드** — 준비: 발송 → `code`. 동작: `redis.delete("auth:email-code:VERIFY_EMAIL:verify@test.com")`(5분 경과 흉내) 후 `code`로 확인.
- 응답: 400, `$.code` = `EMAIL_CODE_INVALID`. `memberSnapshotOf(memberId).emailVerifiedAt()` = null.

**6. Redis에 코드 원문이 없다** — 준비: 발송 → `code`.
- `a = emailAuthSnapshotOf(VERIFY_EMAIL, "verify@test.com")`: `a.fieldNames()`가 정확히 `codeHash`, `attempts`이고 `a.codeHash()`는 `code`와 같지 않다.
- `redis.keys("auth:*")`의 어떤 키 이름에도 `code`가 들어 있지 않다.

**7. 이미 인증된 회원의 발송** — 준비: `testFixtures.signup("done@test.com")`(인증된 상태) → `login("done@test.com", "device-A")`. 동작: 발송.
- 응답: 409, `$.code` = `EMAIL_ALREADY_VERIFIED`. 메일 0건, `redis.keys("auth:email-*")`가 비어 있다(발송 제한 확인보다 앞에서 막는다).

**8. 로그인 없이 호출** — 동작: `Authorization` 없이 발송, 그리고 확인(본문 `{"code":"123456"}`).
- 둘 다 401, `$.code` = `UNAUTHORIZED`. 메일 0건, `redis.keys("auth:email-*")` 비어 있다(`SecurityConfig`의 `email-verifications/**` 규칙이 `/api/auth/**`보다 앞이어야 한다).

#### `member/web/RequireVerifiedEmailTest`

**공통 준비**: 판매자 `seller@test.com`(`signup`), 가게(`openShop(sellerId, "1234567890")`), 상품 1개(`registerProduct(sellerId, shopId)`: 가격 10,000, 재고 10). 구매자 `buyer@test.com`을 `signupUnverified` → `buyerId`, `login("buyer@test.com")`(`TestFixtures`의 기존 `login`) → `buyerToken`(`emailVerified()` = false). `addressId = address(buyerId)`, `cartItemId = addToCart(buyerId, productId, 1)`.

**1. 미인증 회원의 체크아웃** — 동작: `POST /api/orders/checkout`, `Bearer {buyerToken}`, `Idempotency-Key`(새 UUID), 본문 `{"cartItemIds":["{cartItemId}"],"addressId":"{addressId}"}`.
- 응답: 403, `$.code` = `EMAIL_NOT_VERIFIED`.
- `countOf("orders.orders")` = 0. `countOf("common.idempotency_key")` = 0(인터셉터가 멱등 처리보다 먼저 막는다). `stockOf(productId)` → `available` 10, `reserved` 0.
- 대조 1: DB에서 `UPDATE member.member SET email_verified_at = now() WHERE id = ?` 한 뒤 **같은 `buyerToken`**으로 다시 요청(새 `Idempotency-Key`) → 여전히 403 `EMAIL_NOT_VERIFIED`(`ev`는 토큰 안의 값이다).
- 대조 2: `login("buyer@test.com")`로 새 토큰을 받아 요청 → 201.

**2. 미인증 회원의 결제 승인** — 준비: `createPendingOrder(buyerId, productId)` → `order`. 요청 전 `before = snapshotOf(order.orderId())`를 읽어 둔다. 동작: `POST /api/orders/{order.orderId()}/payment/confirm`, `Bearer {buyerToken}`, `Idempotency-Key`, 본문 `{"paymentKey":"pk_test","pgOrderId":"{order.pgOrderId()}","amount":{order.totalAmount()}}`.
- 응답: 403, `$.code` = `EMAIL_NOT_VERIFIED`.
- 가짜 PG: `pg.requests("/v1/payments/")` 0건. 요청 뒤 `s = snapshotOf(order.orderId())`의 `s.payment()`가 null이고 `s.shopOrderStatus()`가 `before.shopOrderStatus()`와 같다(결제 승인이 진행되지 않았다).

**3. 미인증 회원의 가게 개설** — 동작: `POST /api/shops`, `Bearer {buyerToken}`, `Idempotency-Key`, 본문 `{"name":"shop","businessNumber":"9876543210","email":"shop@test.com","phone":"010-1111-1111","address":"addr"}`.
- 응답: 403, `$.code` = `EMAIL_NOT_VERIFIED`.
- `countOf("shop.shop")` = 1(공통 준비의 판매자 가게뿐이다. 사업자번호 `9876543210` 가게가 만들어지지 않았다). `memberSnapshotOf(buyerId).role()` = `USER` 그대로.

**4. 표식이 없는 API는 통과** — 동작: `GET /api/cart`와 `GET /api/members/me`를 `Bearer {buyerToken}`으로.
- 둘 다 200.

#### `member/web/PasswordTest`

**공통 준비**: 회원 `pw@test.com`(`signup`, 비밀번호 `pass1234`) → `memberId`. 변경은 `PATCH /api/members/me/password` 본문 `{"currentPassword":"pass1234","newPassword":"newpass5678"}`. 재설정 요청은 `POST /api/auth/password-reset` 본문 `{"email":"pw@test.com"}`(인증 없음), 재설정 확인은 `POST /api/auth/password-reset/confirm` 본문 `{"email":"pw@test.com","code":"{code}","newPassword":"resetpass99"}`.

**1. 변경 후 로그인** — 준비: `login("pw@test.com", "device-A")` → `accessA`. 동작: 변경(`Bearer {accessA}`).
- 응답: 200, `$.tokenType` = `Bearer`, `$.expiresIn` = 900, `$.accessToken` 있음, `$.refreshToken` 없음/null, `$.refreshExpiresIn` = 0.
- `POST /api/auth/login`을 `pass1234`로 → 401, `$.code` = `LOGIN_FAILED`. `newpass5678`로 → 200, `$.accessToken` 있음.

**2. 변경 후 다른 기기의 refresh** — 준비: `login(…, "device-A")` → `accessA`·`refreshA`·`sidA`, `login(…, "device-B")` → `refreshB`·`sidB`. 동작: 변경(`Bearer {accessA}`).
- 변경 응답의 새 토큰 `newAccess`: `parse(newAccess).sessionId()` = `sidA`. `GET /api/members/me`를 `Bearer {newAccess}`로 → 200, `$.id` = `memberId`.
- `sessionSnapshotOf(sidA).exists()` = true, `sessionSnapshotOf(sidB).exists()` = false. `sessionIdsOf(memberId)`가 정확히 {`sidA`}다(`sidB`와 가입 때 만든 세션이 없다).
- `refresh(refreshB)` → 401, `$.code` = `UNAUTHORIZED`. `refresh(refreshA)` → 200(현재 기기의 세션은 유지된다).

**3. 현재 비밀번호가 틀림** — 준비: `login(…, "device-A")` → `accessA`·`sidA`. 동작: 변경 본문 `{"currentPassword":"wrongpass1","newPassword":"newpass5678"}`.
- 응답: 400, `$.code` = `PASSWORD_MISMATCH`.
- `memberSnapshotOf(memberId).passwordHash()`가 요청 전 값과 같다. `sessionSnapshotOf(sidA).exists()` = true.
- 소셜 가입 회원 흉내: `UPDATE member.member SET password_hash = NULL WHERE id = ?` 후 올바른 현재 비밀번호 `pass1234`로 변경 → 400, `$.code` = `PASSWORD_MISMATCH`.

**4. 재설정 요청은 가입 여부와 상관없이 같은 응답** — 동작 1: 가입되지 않은 `nobody@test.com`으로 재설정 요청.
- 응답: 202, 본문 비어 있음. 메일 0건. `redis.keys("auth:email-*")`가 비어 있다(쿨다운·시간당 카운트도 만들지 않는다).
- 동작 2: 가입된 `pw@test.com`으로 재설정 요청 → 응답 상태와 본문이 동작 1과 같다(202, 비어 있음). 메일 1건(`to` = `pw@test.com`), `emailAuthSnapshotOf(RESET_PASSWORD, "pw@test.com").codeExists()` = true.
- 동작 3: `testFixtures.changeStatus(memberId, "BANNED")` 후 `redis.delete("auth:email-cooldown:pw@test.com")`, 다시 요청 → 202, 메일은 1건 그대로(ACTIVE가 아니면 보내지 않는다).

**5. 재설정 후 모든 refresh와 로그인** — 준비: `login(…, "device-A")` → `refreshA`·`sidA`, `login(…, "device-B")` → `refreshB`·`sidB`. 재설정 요청 → 202, 메일에서 `code`.
- 동작 1: `code`가 아닌 6자리(`wrong`)로 재설정 확인 → 400, `$.code` = `EMAIL_CODE_INVALID`. `memberSnapshotOf(memberId).passwordHash()` 그대로, `sessionSnapshotOf(sidA).exists()`·`sessionSnapshotOf(sidB).exists()` = true.
- 동작 2: `code`로 재설정 확인.
- 확인 2: 204, 본문 없음(토큰을 돌려주지 않는다).  `sessionSnapshotOf(sidA).exists()`·`sessionSnapshotOf(sidB).exists()` = false, `sessionIdsOf(memberId)`가 비어 있다(가입 때 만든 세션까지). `emailAuthSnapshotOf(RESET_PASSWORD, "pw@test.com").codeExists()` = false.
- 확인 3: 로그인 `pass1234` → 401 `LOGIN_FAILED`, `resetpass99` → 200.

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

**전체 흐름** (프론트 = 사용자 브라우저에서 도는 화면, 제공자 = Kakao·Google·Naver)
1. 프론트가 `GET /api/auth/oauth/kakao/authorize-url`을 부른다 → 서버가 `state`를 만들어 Redis에 10분 저장하고, 제공자 로그인 화면 주소 `url`과 `state`를 돌려준다
2. 프론트가 브라우저를 `url`로 이동시킨다. 사용자가 제공자에서 로그인·동의하면 제공자가 **미리 등록해 둔 `redirectUri`(프론트 화면)** 로 `?code=...&state=...`를 붙여 돌려보낸다. `code`는 한 번만 쓰는 교환권이다(우리 API가 `code`·`state`를 바디로 받으므로 `redirectUri`는 서버가 아니라 프론트 화면이다)
3. 프론트가 `POST /api/auth/oauth/kakao/login` 바디 `{code, state}`를 보낸다 → 서버가 ① `state`가 우리가 만든 값인지 확인하고(`consume`) ② `code`를 제공자 토큰 주소로 보내 **제공자 access token**으로 바꾸고 ③ 그 토큰으로 사용자 정보를 읽는다(`fetchUser`). 제공자 access token은 사용자 정보를 읽는 데만 쓰고 저장하지 않는다(우리 토큰과 별개다)
4. 이미 연결된 소셜 계정이면 `LOGGED_IN` + 우리 토큰. 처음 보는 계정이면 `SIGNUP_REQUIRED` + 가입 토큰(Redis 10분)과 제공자가 준 이메일
5. 신규 가입: 프론트가 `POST /api/auth/oauth/signup/email-verifications`로 인증코드를 요청 → 메일의 코드와 함께 `POST /api/auth/oauth/signup` → 201 우리 토큰
6. 로그인한 회원의 계정 연결은 1~3과 같고, 4 대신 소셜 계정을 저장만 한다(`POST /api/members/me/social-accounts/{provider}`)

### 할 일

**구현 순서**: ① 마이그레이션(1) → ② `OAuthProperties`·`application.yaml`(2) → ③ `OAuthProvider`·`OAuthUserInfo`·`OAuthProviderClient`와 구현체 3개·`OAuthClients`(3) → ④ 에러 코드·`SocialAccount`·`SocialAccountRepository`·`Member.signUpWithSocial`(4) → ⑤ `OAuthStateStore`·`SignupTokenStore` → ⑥ `OAuthLoginService` → ⑦ 컨트롤러·`SecurityConfig`(5) → ⑧ `FakeOAuthServer`(6) → 테스트.

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
- `OAuthProperties`를 등록하는 `@EnableConfigurationProperties(OAuthProperties.class)` 설정 클래스가 필요하다(`OrderConfig`·`TossClientConfig`와 같은 방식. 클래스 이름은 정해져 있지 않다)
- yaml은 `myroutine.oauth.providers.kakao.client-id`처럼 **소문자 제공자 이름을 맵 키**로 쓴다(`Map<String, Provider>`). 코드에서는 `properties.providers().get("kakao")`처럼 `OAuthProvider.name().toLowerCase(Locale.ROOT)`로 찾는다
- 환경변수가 없는 `${...}`는 기동 때 실패하므로, 테스트용 더미 `client-id`·`client-secret`은 `src/test/resources/application-test.yaml`에 둔다(`toss.secret-key: test_sk_dummy`와 같은 방식)

**3) member.infrastructure — 제공자 클라이언트**

제공자마다 주소·요청 파라미터·응답 JSON 모양이 달라서 **"인가 URL 만들기"와 "code로 사용자 정보 얻기"를 인터페이스 하나로 감싸고** 제공자별 구현체 3개를 둔다(전략). 서비스(`OAuthLoginService`)는 제공자를 if로 가르지 않는다.

| 타입 | 무엇인가 |
|---|---|
| `OAuthProvider` (member.domain enum) | `KAKAO, GOOGLE, NAVER`. DB `social_account.provider`와 Redis 값에 `name()` 그대로 쓴다 |
| `OAuthUserInfo` (member.domain) | `record (String providerUserId, String email)` — 제공자 응답을 우리 모양으로 옮긴 사용자 정보. `email`은 제공자가 안 주면 `null`(`providerUserId`는 항상 있어야 한다). 닉네임은 가입 요청이 직접 받으므로 제공자 응답에서 읽지 않는다 |
| `OAuthClients` (`@Component`) | `List<OAuthProviderClient>`를 주입받아 `Map<OAuthProvider, OAuthProviderClient>`로 만든다(`client.provider()`가 키). `OAuthProviderClient get(OAuthProvider provider)`가 서비스의 진입점이다 |

`OAuthProviderClient` (interface) — 제공자 한 곳과의 통신을 맡는다. 호출자는 `OAuthLoginService`뿐이다.

| 메서드 | 하는 일 | 입력 → 반환 |
|---|---|---|
| `OAuthProvider provider()` | 이 구현체가 어느 제공자인지 | → enum 값(`OAuthClients`가 키로 쓴다) |
| `String authorizeUrl(String state)` | 사용자를 보낼 제공자 로그인 화면 주소를 만든다. 서버 호출은 없다 | `state` → `authorizeUri`에 `response_type=code`, `client_id`, `redirect_uri`, `scope`, `state`를 쿼리로 붙인 URL(값은 URL 인코딩, `UriComponentsBuilder`). 제공자별로 필수·불필요 파라미터가 다른지는 각 문서와 대조한다(확인 필요) |
| `OAuthUserInfo fetchUser(String code, String state)` | `code`를 제공자 토큰으로 바꾸고 그 토큰으로 사용자 정보를 읽는다 | 아래 순서 |

`fetchUser` 순서 (구현체마다 같고, 3에서 꺼내는 필드 이름만 다르다)
1. 토큰 주소(`tokenUri`)에 `grant_type=authorization_code`, `code`, `redirect_uri`, `client_id`, `client_secret`을 폼으로 POST → 응답 JSON의 `access_token`. Naver는 `state`도 요구하는 것으로 알려져 있어 인터페이스가 `state`를 받는다(확인 필요: 제공자 문서)
2. 사용자 정보 주소(`userInfoUri`)에 `Authorization: Bearer {access_token}`으로 GET
3. 응답 JSON에서 필드를 꺼내 `OAuthUserInfo`로 만든다
4. 어느 단계든 제공자 쪽 4xx·5xx, 타임아웃, 필수 필드(`providerUserId`) 누락이면 `BusinessException(OAUTH_PROVIDER_ERROR)`(502)

- 호출 방식은 2-3 `TossClient`처럼 `RestClient`(정적 `RestClient.builder()` + `JdkClientHttpRequestFactory`에 connect 3초·read 5초)를 쓰면 된다. 다만 여기서는 상태 코드를 `PgResult`처럼 분류하지 않고 `retrieve()`가 4xx·5xx에서 던지는 `RestClientException`(타임아웃은 그 하위 `ResourceAccessException`)을 한 번에 잡아 `OAUTH_PROVIDER_ERROR`로 바꾼다
- 응답 JSON은 `Map<String, Object>` 또는 `JsonNode`(Jackson 3, `tools.jackson.databind`)로 받아 꺼낸다. 중첩 필드가 비어 있을 수 있으니 중간 객체의 `null`을 확인한다. Kakao `id`는 JSON 숫자라 `String`으로 바꿔야 한다

| 구현체 | 읽는 필드 (확인 필요: 각 제공자 문서와 대조) |
|---|---|
| `KakaoOAuthClient` | `id`, `kakao_account.email` |
| `GoogleOAuthClient` | `sub`, `email` |
| `NaverOAuthClient` | `response.id`, `response.email` |

**4) member.application**

- 에러 코드(공통 규칙 U): `MemberErrorCode`에 `OAUTH_STATE_INVALID`(400)·`OAUTH_PROVIDER_ERROR`(502)·`SIGNUP_TOKEN_INVALID`(401)·`SOCIAL_ACCOUNT_ALREADY_LINKED`(409)를 추가한다
- `SocialAccount` (member.domain 엔티티, `member.social_account`) + `SocialAccountRepository`: 필드는 위 마이그레이션 컬럼과 1:1(`id`는 `Ids.newId()`, 수정이 없으므로 `createdAt`만 두는 Part 1 공통 규칙 B의 엔티티). 메서드는 `Optional<SocialAccount> findByProviderAndProviderUserId`, `boolean existsByProviderAndProviderUserId`, `boolean existsByMemberIdAndProvider`, `save`
- `Member.signUpWithSocial(String email, String nickname, String name, Instant now)` — `Member.signUp`과 같은 정적 팩토리(id 생성, `ACTIVE`, `USER`, 이메일 정규화)에 `passwordHash = null`, `emailVerifiedAt = now`

**`OAuthStateStore`·`SignupTokenStore`** (member.domain 인터페이스 + member.infrastructure의 Redis 구현) — OAuth 흐름이 서버에 남겨 두는 두 가지 임시 값을 Redis에 둔다. 둘 다 `OAuthLoginService`가 쓴다.

| 메서드 | 하는 일 | 입력 → 반환 |
|---|---|---|
| state: `String issue(OAuthProvider provider)` | CSRF 방지용 `state`를 만들어 저장한다 | 추측할 수 없는 문자열(`SecureRandom` 32바이트 Base64URL 등)을 만들어 `set("auth:oauth-state:{state}", provider.name(), 10분)` → `state` |
| state: `boolean consume(String state, OAuthProvider provider)` | 돌아온 `state`가 우리가 만든 것인지 확인하고 **한 번만 쓰게** 지운다 | 키가 있고 값이 `provider.name()`과 같으면 지우고 `true`, 아니면 `false`. GET → 비교 → DEL을 따로 하면 같은 state를 동시에 두 번 통과시킬 수 있다. 문서 표현("provider가 같을 때만 삭제")을 지키려면 4-1 `rotate`처럼 Lua로 묶는다(확인 필요) |
| 가입 토큰: `String issue(OAuthProvider provider, OAuthUserInfo info)` | 신규 소셜 사용자의 제공자 정보를 가입이 끝날 때까지 맡아 둔다 | 토큰(`state`와 같은 방식의 랜덤 문자열)을 만들어 `auth:oauth-signup:{token}` 해시에 `provider`·`providerUserId`·`email`을 저장하고 10분 `expire` → 토큰. `email`이 `null`이면 그 필드는 저장하지 않는다(null을 넣으면 예외가 날 수 있다) |
| 가입 토큰: `Optional<PendingSocialSignup> find(String token)` | 토큰으로 맡겨 둔 정보를 찾는다 | 해시가 비었으면 `Optional.empty()`. 없는 필드는 `null`로 읽는다 |
| 가입 토큰: `void delete(String token)` | 가입이 끝났으니 지운다 | `delete(key)` |

- `PendingSocialSignup`: `record (OAuthProvider provider, String providerUserId, String email)` — Redis 해시(공통 규칙 T의 `auth:oauth-signup:{signupToken}`)의 필드

**`OAuthLoginService`** (member.application, `@Service`) — OAuth 로그인·가입·연결 유스케이스 4개. 컨트롤러가 부르고, 제공자 호출·Redis·DB를 조율한다. 의존성: `OAuthClients`, `OAuthStateStore`, `SignupTokenStore`, `SocialAccountRepository`, `MemberRepository`, `EmailCodeStore`, `TokenIssuer`, `Clock`.

| 메서드 | 한 줄 설명 | 입력 → 반환 |
|---|---|---|
| `AuthorizeUrl authorizeUrl(OAuthProvider provider)` | 제공자 로그인 화면 주소와 `state`를 만든다 | → `record AuthorizeUrl(String url, String state)`. `state = stateStore.issue(provider)` → `url = clients.get(provider).authorizeUrl(state)` |
| `OAuthLoginResult login(OAuthProvider provider, String code, String state, String device)` | 소셜 로그인. 가입된 계정이면 로그인, 아니면 가입 토큰을 준다 | → `OAuthLoginResult` |
| `TokenResult signup(SocialSignupCommand command, String device)` | 가입 토큰 + 인증코드로 가입을 끝낸다 | → `TokenResult` |
| `void link(UUID memberId, OAuthProvider provider, String code, String state)` | 로그인한 회원에게 소셜 계정을 연결한다 | → 없음 |

- 트랜잭션: `login`·`link`는 제공자 호출(최대 3초+5초)이 들어 있으므로 **`@Transactional`을 붙이지 않는다**(공통 규칙 K, DB 커넥션을 잡고 기다리지 않게). 조회·저장은 리포지토리가 짧은 트랜잭션으로 한다. `signup`만 `@Transactional`(7단계 한 묶음)
- `OAuthLoginResult` `record (String status, TokenResult token, String signupToken, String email)` — `status`는 `LOGGED_IN`(`token`만 채움) 또는 `SIGNUP_REQUIRED`(`signupToken`·`email`만 채움, 나머지는 `null`)
- `SocialSignupCommand` `record (String signupToken, String email, String nickname, String name, String verificationCode)` — `SocialSignupRequest.toCommand()`로 만든다

`login` 순서
1. `stateStore.consume(state, provider)`가 `false`면 400 `OAUTH_STATE_INVALID`
2. `OAuthUserInfo info = clients.get(provider).fetchUser(code, state)` — 실패하면 502 `OAUTH_PROVIDER_ERROR`
3. `socialAccountRepository.findByProviderAndProviderUserId(provider, info.providerUserId())`가 있으면(제공자 이메일은 보지 않고 제공자 사용자 ID로만 찾는다): `memberRepository.findById(account.getMemberId())` → `member.verifyCanLogin()`(BANNED 403) → `tokenIssuer.issueNewSession(member, device)` → `OAuthLoginResult("LOGGED_IN", token, null, null)`
4. 없으면: `signupTokenStore.issue(provider, info)` → `OAuthLoginResult("SIGNUP_REQUIRED", null, signupToken, info.email())`(제공자가 이메일을 안 줬으면 `null`)

`signup` 순서
1. `signupTokenStore.find(command.signupToken())` — 없으면 401 `SIGNUP_TOKEN_INVALID`
2. `email` = 토큰의 이메일, 없으면 `command.email()`(둘 다 없으면 400 `INVALID_REQUEST`) → `Member.normalizeEmail`
3. `emailCodeStore.verify(VERIFY_EMAIL, email, command.verificationCode())`가 `false`면 400 `EMAIL_CODE_INVALID`
4. `existsByEmail(email)`이면 409 `MEMBER_EMAIL_DUPLICATED` (자동 연결 안 함)
5. `existsByNickname(command.nickname())`이면 409 `MEMBER_NICKNAME_DUPLICATED`
6. `Member.signUpWithSocial(...)`을 저장하고 `SocialAccount`를 저장한다(한 트랜잭션)
7. `signupTokenStore.delete(token)` → `tokenIssuer.issueNewSession(member, device)` → `TokenResult`. 동시 요청이 확인을 둘 다 통과하면 unique 제약이 409 `DUPLICATE_RESOURCE`로 막는다

`link` 순서
1. state 확인 (`login` 1과 같다)
2. `fetchUser`
3. 이미 다른 회원에 연결된 소셜 계정이거나 이 회원이 같은 제공자를 이미 가졌으면(`existsByProviderAndProviderUserId`, `existsByMemberIdAndProvider`) 409 `SOCIAL_ACCOUNT_ALREADY_LINKED`
4. 저장 (동시 요청 경합은 unique 제약 → 409 `DUPLICATE_RESOURCE`)

**가입 토큰용 인증코드 발송** — 별도 API `POST /api/auth/oauth/signup/email-verifications` 바디 `SignupEmailVerificationRequest(@NotBlank String signupToken, @Email String email)`. 로그인하지 않은 가입 중 사용자의 요청이라 회원 조회가 없고, 로그인 회원용 `POST /api/auth/email-verifications`는 4-2 그대로 둔다. 서비스 메서드는 `OAuthLoginService`에 둘지 `EmailVerificationService`에 둘지 문서에 없다(확인 필요). 순서
1. `signupTokenStore.find(signupToken)` — 없으면 401 `SIGNUP_TOKEN_INVALID`
2. 이메일 결정(`signup` 2와 같다: 토큰의 이메일, 없으면 바디의 이메일)
3. `emailCodeStore.checkSendLimit(email)`(4-2의 발송 제한 그대로) → 코드 생성 → `emailCodeStore.save(VERIFY_EMAIL, email, code)` → `mailSender.send`

**5) API**

| API | 권한 | 요청 | 응답 |
|---|---|---|---|
| `GET /api/auth/oauth/{provider}/authorize-url` | 공개 | provider는 소문자(`kakao`) → enum 변환(실패 시 400) | 200 `{url, state}` |
| `POST /api/auth/oauth/{provider}/login` | 공개 | `OAuthLoginRequest(@NotBlank code, @NotBlank state)` | 200 `OAuthLoginResponse` |
| `POST /api/auth/oauth/signup/email-verifications` | 공개(가입 토큰) | `SignupEmailVerificationRequest` | 202 |
| `POST /api/auth/oauth/signup` | 공개(가입 토큰) | `SocialSignupRequest(@NotBlank signupToken, @Email email, nickname, name, @Pattern 6자리 verificationCode)` | 201 `TokenResponse` |
| `POST /api/members/me/social-accounts/{provider}` | 로그인 | `OAuthLoginRequest` | 204 |

- `SecurityConfig`: `/api/auth/oauth/**`는 공개(이미 `/api/auth/**` 허용 안에 있다), `/api/auth/email-verifications/**`는 로그인 필요(4-2)
- 경로의 `{provider}`는 `kakao`처럼 소문자다. `@PathVariable OAuthProvider`로 바로 받으면 Spring의 기본 문자열→enum 변환이 대소문자를 구분해 `kakao`를 받아 주지 않을 수 있다(확인 필요: 구현 때 테스트로 확인). 안 되면 `String`으로 받아 `OAuthProvider.valueOf(value.toUpperCase(Locale.ROOT))`로 바꾸고, `IllegalArgumentException`은 `BusinessException(INVALID_REQUEST)`(400)로 바꾼다. 안 바꾸면 `GlobalExceptionHandler`의 일반 `Exception` 처리로 500이 된다
- `OAuthLoginResponse`의 필드는 문서에 없다. `OAuthLoginResult`의 네 값(`status`, `token`, `signupToken`, `email`)을 옮기는 것으로 보이나, `token`을 `TokenResponse`로 중첩할지 펼칠지는 정해져 있지 않다(확인 필요)
- 컨트롤러 클래스는 문서에 정해져 있지 않다(확인 필요). 각 메서드는 `OAuthLoginService`의 `authorizeUrl`·`login`·`signup`·`link`를 부르고, `login`·`signup`은 `User-Agent` 헤더를 `device`로 넘긴다
- `link`(`POST /api/members/me/social-accounts/{provider}`)는 로그인 회원 요청이라 memberId를 `@CurrentMember UUID memberId`로 받는다. 응답은 204(`ResponseEntity.noContent().build()`)
- `device`는 4-1과 같이 `User-Agent` 헤더에서 온다(확인 필요 항목도 4-1과 같다)

**6) 가짜 제공자 서버** — `support/FakeOAuthServer`: 2-3의 `FakePgServer`와 같은 방식(JDK `HttpServer`, `start()`·`baseUrl()`·`reset()`, 이미 있는 `support/Behavior` 재사용). 실제 제공자 대신 테스트가 띄우는 서버로, 제공자 호출 두 가지에 답한다.
- 경로 `/{provider}/token`: 어떤 `code`가 와도 `{"access_token": "..."}`를 돌려준다
- 경로 `/{provider}/userinfo`: `givenUser`로 정해 둔 사용자를 **그 제공자의 응답 모양**(Kakao `{id, kakao_account:{email}}`, Google `{sub, email}`, Naver `{response:{id, email}}`)으로 돌려준다 — 그래야 세 클라이언트의 파싱을 각각 시험할 수 있다
- `givenUser(provider, providerUserId, email)`: 응답에 쓸 사용자를 등록. `failWith(provider, Behavior)`: 그 제공자의 응답을 지연·500 등으로 바꾼다(`OAUTH_PROVIDER_ERROR` 시험). `reset()`은 `IntegrationTestSupport`의 `@AfterEach`에서 부른다
- `IntegrationTestSupport`의 `@DynamicPropertySource`에서 제공자별 `token-uri`·`user-info-uri`를 가짜 서버 주소로 넣는다(`myroutine.oauth.providers.kakao.token-uri` 등)

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 값을 읽는 방법은 4-1·4-2와 같고, 이 단계에서 더하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `memberIdOf(String email)` → `UUID` | JDBC `SELECT id FROM member.member WHERE email = ?`. 행이 없으면 `null`. `email`은 정규화한 값 | `OAuthLoginTest` |
| `socialAccountsOf(UUID memberId)` → `List<SocialAccountRow>` | JDBC `SELECT provider, provider_user_id, provider_email FROM member.social_account WHERE member_id = ? ORDER BY created_at, id`. `SocialAccountRow`는 `support/`의 record(`provider`, `providerUserId`, `providerEmail`. 모두 `String`) | `OAuthLoginTest`, `SocialLinkTest` |
| `redisStringOf(String key)` → `RedisString` | `redis.opsForValue().get(key)`와 `redis.getExpire(key, TimeUnit.SECONDS)`. `support/`의 record(`String value`, `long ttlSeconds`)이고 `exists()`(`value != null`)를 가진다. 키가 없으면 `value`는 `null`, 남은 시간은 -2 | `OAuthLoginTest`·`SocialLinkTest`의 `auth:oauth-state:*`, 4-4 `LoginLockTest` |
| `redisHashOf(String key)` → `RedisHash` | `redis.opsForHash().entries(key)`를 `Map<String, String>`으로 바꾸고 `redis.getExpire(key, TimeUnit.SECONDS)`. `support/`의 record(`Map<String, String> fields`, `long ttlSeconds`)이고 `exists()`(`fields`가 비어 있지 않다)를 가진다 | `OAuthLoginTest`의 `auth:oauth-signup:*` |

이미 있는 헬퍼: `memberSnapshotOf`·`countOf`(3-1, 4-2에서 `emailVerifiedAt` 추가), `emailAuthSnapshotOf`(4-2), `sessionSnapshotOf`·`sessionIdsOf`(4-1).

헬퍼를 쓰는 방법은 다음과 같다.
- 소셜 로그인 응답: `$.status`(`LOGGED_IN` 또는 `SIGNUP_REQUIRED`), `$.signupToken`, `$.email`, 로그인 성공 시의 토큰. `OAuthLoginResponse`의 모양이 문서에 없다(확인 필요: 4-3 5) API). 아래는 `OAuthLoginResult`의 이름(`status`, `token`, `signupToken`, `email`)을 그대로 옮겨 토큰을 `$.token.accessToken`·`$.token.refreshToken`으로 읽는다고 가정해 쓴다. 펼쳐 놓는다면 `$.accessToken`·`$.refreshToken`으로 바꿔 읽는다.
- authorize-url 응답: `$.url`, `$.state`. `$.url`의 쿼리는 `UriComponentsBuilder.fromUriString(url).build().getQueryParams()`로 읽는다.
- 회원: `memberSnapshotOf(memberId)`(`email`, `passwordHash`, `emailVerifiedAt`, `status`, `role`, `nickname`), 이메일로 회원 ID를 찾을 때는 `memberIdOf(email)`, 행 수는 `countOf("member.member")`·`countOf("member.social_account")`, 소셜 계정은 `socialAccountsOf(memberId)`로 읽는다.
- Redis: `redisStringOf("auth:oauth-state:" + state)`의 `value()`는 제공자 이름 `KAKAO` 등이다. `redisHashOf("auth:oauth-signup:" + signupToken)`의 `fields()` 맵에 `provider`·`providerUserId`·`email`이 있다. 인증코드는 4-2의 `emailAuthSnapshotOf`, 세션은 4-1의 `sessionSnapshotOf`로 읽는다.
- 가짜 제공자 서버 `oauth`(`FakeOAuthServer`): 제공자별로 `oauth.givenUser(OAuthProvider.KAKAO, "kakao-1001", "social@test.com")`로 사용자를 정한다. 제공자가 받은 요청 수는 `oauth.requests("/kakao/token")`로 읽는다고 가정한다(`FakePgServer.requests`와 같은 방식. `FakeOAuthServer` 규격에 요청 기록이 없다, 확인 필요). `failWith`의 `Behavior`가 그 제공자의 token·userinfo 응답 모두에 적용되는지도 문서에 없다(확인 필요). 시간 의존: state·가입 토큰의 10분 경과는 `redis.delete(키)`로 흉내 낸다.
- `support/FakeOAuthServer`, `application-test.yaml`의 더미 설정, `IntegrationTestSupport`의 `@DynamicPropertySource`는 위 "할 일" 6)에 있다.
- `UUID socialSignup(OAuthProvider provider, String providerUserId, String email)`: `OAuthLoginTest`의 private 메서드. ① `oauth.givenUser(provider, providerUserId, email)` ② `issueState(provider)` → `oauthLogin(provider, state)`의 `$.signupToken`(`T`) ③ `POST /api/auth/oauth/signup/email-verifications` 본문 `{"signupToken":"{T}"}` → 메일에서 6자리 코드(`codeOf`) ④ `POST /api/auth/oauth/signup` 본문 `{"signupToken":"{T}","nickname":"socialnick","name":"소셜","verificationCode":"{code}"}`, `User-Agent: device-A`를 MockMvc로 수행하고 `memberIdOf(email)`(가입된 `memberId`)를 돌려준다. `issueState`·`oauthLogin`은 아래 "공통 준비"에 있다.
- `TestFixtures.signup`·`login`은 4-2 이후 동작(미인증 → 인증된 상태로 가입) 그대로 쓴다.

**공통 준비**: `application-test.yaml`의 더미 `client-id`·`client-secret`과 가짜 서버 주소(`token-uri`, `user-info-uri`)가 들어가 있고, 인증코드 메일은 `FakeMailSender`가 받는다(4-2). 테스트 클래스 private 메서드(이름은 권장안): `String issueState(OAuthProvider provider)` — `GET /api/auth/oauth/{provider 소문자}/authorize-url` 응답의 `$.state`. `ResultActions oauthLogin(OAuthProvider provider, String state)` — `POST /api/auth/oauth/{provider 소문자}/login` 본문 `{"code":"any-code","state":"{state}"}`, `User-Agent: device-A`. 가짜 서버는 어떤 `code`도 받아 준다.

#### `member/web/OAuthLoginTest`

**1. 신규 소셜 로그인부터 가입까지** — 준비: `oauth.givenUser(KAKAO, "123456789", "social@test.com")`(Kakao의 `id`는 JSON 숫자다. 가짜 서버가 숫자로 내려 보내는지는 문서에 없다, 확인 필요).
- 동작 1: `GET /api/auth/oauth/kakao/authorize-url`.
- 확인 1: 200, `$.state`가 비어 있지 않다(`$.url`의 쿼리는 6번이 세 제공자 모두 확인한다). `s = redisStringOf("auth:oauth-state:" + state)`: `s.value()` = `KAKAO`, `s.ttlSeconds()` 1~600.
- 동작 2: `oauthLogin(KAKAO, state)`.
- 확인 2: 200, `$.status` = `SIGNUP_REQUIRED`, `$.email` = `social@test.com`, `$.signupToken`이 비어 있지 않음(`T`), `$.token`이 없음/null. `h = redisHashOf("auth:oauth-signup:" + T)`: `h.exists()` = true, `h.ttlSeconds()` 1~600(필드 값은 6번이 제공자별로 확인한다). `countOf("member.member")` = 0, `countOf("member.social_account")` = 0.
- 동작 3: `POST /api/auth/oauth/signup/email-verifications` 본문 `{"signupToken":"{T}"}`(이메일은 토큰의 것을 쓴다).
- 확인 3: 202. 메일 1건, `to` = `social@test.com`, `body`에서 6자리 코드(`code`). 메일 제목·본문은 4-2와 같은지 문서에 없다(확인 필요), `codeOf`로 코드만 꺼낸다.
- 동작 4: `POST /api/auth/oauth/signup` 본문 `{"signupToken":"{T}","nickname":"socialnick","name":"소셜","verificationCode":"{code}"}`, `User-Agent: device-A`.
- 확인 4: 201, `$.accessToken`·`$.refreshToken` 있음. `parse($.accessToken)`: `emailVerified()` = true, `role()` = `USER`, `memberId()` = `memberIdOf("social@test.com")`(아래 회원 ID).
  - `countOf("member.member")` = 1, `m = memberSnapshotOf(memberIdOf("social@test.com"))`: `m.passwordHash()` null, `m.emailVerifiedAt()` null 아님, `m.nickname()` = `socialnick`.
  - `countOf("member.social_account")` = 1이고 `socialAccountsOf(그 회원)`의 1행이 `provider` = `KAKAO`, `providerUserId` = `123456789`. (`providerEmail`에 무엇을 넣는지는 문서에 없다, 확인 필요)
  - `redisHashOf("auth:oauth-signup:" + T).exists()` = false, `emailAuthSnapshotOf(VERIFY_EMAIL, "social@test.com").codeExists()` = false, `s = sessionSnapshotOf(sid)`(`sid` = `RefreshToken.parse($.refreshToken).sessionId()`): `s.memberId()` = 그 회원, `s.device()` = `device-A`.
  - `GET /api/members/me`를 `Bearer {$.accessToken}`으로 → 200.

**2. 같은 소셜 계정으로 다시 로그인** — 준비: 1번의 동작·확인 전부를 `socialSignup(KAKAO, "123456789", "social@test.com")`(private 메서드, 가입된 `memberId`를 돌려준다)로 한 번 수행한다. 새 `state` = `issueState(KAKAO)`.
- 동작: `oauthLogin(KAKAO, state)`.
- 확인: 200, `$.status` = `LOGGED_IN`. `parse($.token.accessToken).memberId()` = `memberId`. `$.token.refreshToken`의 세션 `s = sessionSnapshotOf(sid)`가 있고(`s.exists()`) `s.memberId()`가 같다.
- 제공자 이메일이 바뀌어도: `oauth.givenUser(KAKAO, "123456789", "changed@test.com")` 후 새 state로 로그인 → 여전히 `LOGGED_IN`, 같은 `memberId`(제공자 사용자 ID로만 찾는다).
- 정지 회원: `changeStatus(memberId, "BANNED")` 후 새 state로 로그인 → 403, `$.code` = `MEMBER_BANNED`. 이 요청으로 새 세션이 생기지 않는다(요청 전후 `sessionIdsOf(memberId).size()`가 같다).

**3. 같은 이메일의 이메일 가입 계정이 있으면 가입 거부** — 준비: `testFixtures.signup("dup@test.com")` → `existingId`(비밀번호 `pass1234`). `oauth.givenUser(KAKAO, "kakao-dup", "dup@test.com")`.
- 동작: `oauthLogin` → `SIGNUP_REQUIRED`(`T`) → 가입 토큰으로 인증코드 발송 → 메일에서 `code` → `POST /api/auth/oauth/signup` 본문 `{"signupToken":"{T}","nickname":"socialnick","name":"소셜","verificationCode":"{code}"}`.
- 확인: 로그인 단계는 200 `SIGNUP_REQUIRED`(이 단계에서는 이메일 충돌을 보지 않는다). 가입은 409, `$.code` = `MEMBER_EMAIL_DUPLICATED`. `countOf("member.member")` = 1(닉네임 `socialnick` 회원이 만들어지지 않았다), `memberSnapshotOf(existingId).passwordHash()`는 요청 전 값 그대로, `countOf("member.social_account")` = 0(자동 연결되지 않았다).

**4. 잘못된 state** — 모든 하위 케이스에서 `countOf("member.member")`·`countOf("member.social_account")`는 0이다.
- 발급한 적 없는 state: `oauthLogin(KAKAO, "never-issued")` → 400, `$.code` = `OAUTH_STATE_INVALID`. `oauth.requests("/kakao/token")` 0건(state 검사가 제공자 호출보다 먼저다). 본문에 `state`가 없거나 빈 문자열이면 `@NotBlank`에 걸려 `OAUTH_STATE_INVALID`가 아니라 400 `INVALID_REQUEST`다. `{"code":"x"}`만 보내는 요청도 한 번 보내 이를 확인한다.
- 다른 제공자의 state: `s = issueState(GOOGLE)` → `oauthLogin(KAKAO, s)` → 400, `OAUTH_STATE_INVALID`, `oauth.requests("/kakao/token")` 0건. 이어서 `redisStringOf("auth:oauth-state:" + s).value()` = `GOOGLE`로 남아 있고(제공자가 같을 때만 지운다), `oauth.givenUser(GOOGLE, "g-1", "g@test.com")` 준비 후 `oauthLogin(GOOGLE, s)`는 200 `SIGNUP_REQUIRED`다.
- 두 번 쓴 state: `s = issueState(KAKAO)` → `oauthLogin(KAKAO, s)` 200 `SIGNUP_REQUIRED` → 같은 `s`로 다시 → 400, `OAUTH_STATE_INVALID`. `oauth.requests("/kakao/token")`는 첫 요청의 1건 그대로다(두 번째 요청은 제공자를 부르지 않았다).
- 만료된 state: `s = issueState(KAKAO)` → `redis.delete("auth:oauth-state:" + s)`(10분 경과 흉내) → `oauthLogin(KAKAO, s)` → 400, `OAUTH_STATE_INVALID`, `oauth.requests("/kakao/token")` 0건.

**5. 제공자 장애** — `@ParameterizedTest` 입력: `Behavior.delay(6000)`(read 타임아웃 5초 초과), `Behavior.serverError()`(500). 준비: `oauth.givenUser(KAKAO, "kakao-1", "a@test.com")`, `oauth.failWith(KAKAO, 입력)`, `state = issueState(KAKAO)`.
- 동작: `oauthLogin(KAKAO, state)`.
- 확인: 502, `$.code` = `OAUTH_PROVIDER_ERROR`. `countOf("member.member")`·`countOf("member.social_account")` = 0, `redis.keys("auth:oauth-signup:*")` 비어 있다. `redisStringOf("auth:oauth-state:" + state).exists()` = false(state는 제공자 호출 전에 소진된다).

**6. 세 제공자의 응답 파싱** — `@ParameterizedTest` 입력: `(KAKAO, "123456789")`, `(GOOGLE, "google-sub-1")`, `(NAVER, "naver-id-1")`. 준비: `oauth.givenUser(p, id, p.name().toLowerCase() + "@test.com")`.
- 동작: `GET /api/auth/oauth/{p 소문자}/authorize-url` → `$.state`, 그 state로 `oauthLogin(p, state)`.
- 확인: authorize-url 200, `$.url`이 그 제공자 설정의 `authorizeUri`로 시작하고 쿼리의 `response_type` = `code`, `client_id`·`redirect_uri`·`scope`가 그 제공자 설정값, `state` = `$.state`. 로그인 200, `$.status` = `SIGNUP_REQUIRED`, `$.email` = `{소문자 제공자}@test.com`. `h = redisHashOf("auth:oauth-signup:" + $.signupToken)`: `h.fields()`의 `provider` = `p.name()`, `providerUserId` = `id`, `email` = 위 이메일.
- 알 수 없는 제공자: `GET /api/auth/oauth/facebook/authorize-url` → 400, `$.code` = `INVALID_REQUEST`. `POST /api/auth/oauth/facebook/login`도 같다.

**7. 이메일을 주지 않는 제공자** — 준비: `oauth.givenUser(KAKAO, "kakao-noemail", null)`, `state = issueState(KAKAO)`.
- 동작 1: `oauthLogin(KAKAO, state)` → 200, `$.status` = `SIGNUP_REQUIRED`, `$.email` 없음/null. `redisHashOf("auth:oauth-signup:" + T).fields().keySet()`이 정확히 `provider`, `providerUserId` 둘이다(`email` 필드 없음).
- 동작 2: 발송 본문 `{"signupToken":"{T}","email":"typed@test.com"}` → 202, 메일 `to` = `typed@test.com`.
- 동작 3: 가입 본문 `{"signupToken":"{T}","email":"typed@test.com","nickname":"socialnick","name":"소셜","verificationCode":"{code}"}` → 201. `memberIdOf("typed@test.com")`가 null이 아니다(그 이메일로 가입됐다).
- 이메일이 둘 다 없는 경우: 위 동작 1 뒤 본문의 `email` 없이 가입 요청 → 400, `$.code` = `INVALID_REQUEST`, `countOf("member.member")` = 0.

**8. 가입 거부** — 준비: 1번의 동작 1~3까지(`T`, `code`). 
- 틀린 코드: `verificationCode`를 `code`가 아닌 6자리로 가입 → 400, `$.code` = `EMAIL_CODE_INVALID`. `countOf("member.member")`·`countOf("member.social_account")` = 0. `redisHashOf("auth:oauth-signup:" + T).exists()` = true. 이어 올바른 `code`로 다시 가입 → 201.
- 가입 토큰 없음: `redis.delete("auth:oauth-signup:" + T)`(10분 경과 흉내) 후 가입 → 401, `$.code` = `SIGNUP_TOKEN_INVALID`. 같은 상태에서 인증코드 발송도 401 `SIGNUP_TOKEN_INVALID`이고 메일은 추가되지 않는다.

#### `member/web/SocialLinkTest`

**공통 준비**: 이메일 회원 `link@test.com`(`signup`) → `memberId`, `token = login("link@test.com")`. 연결 요청은 `POST /api/members/me/social-accounts/kakao` 본문 `{"code":"any-code","state":"{state}"}`, `Bearer {token}`.

**1. 연결 후 그 계정으로 로그인** — 준비: `oauth.givenUser(KAKAO, "kakao-9", "other@test.com")`(회원 이메일과 다르다), `state = issueState(KAKAO)`.
- 동작 1: 연결 요청.
- 확인 1: 204, 본문 없음. `countOf("member.social_account")` = 1이고 `socialAccountsOf(memberId)`의 1행이 `provider` = `KAKAO`, `providerUserId` = `kakao-9`. `memberSnapshotOf(memberId).email()` = `link@test.com` 그대로다. `redisStringOf("auth:oauth-state:" + state).exists()` = false.
- 동작 2: 새 `state`로 `oauthLogin(KAKAO, state)`.
- 확인 2: 200, `$.status` = `LOGGED_IN`, `parse($.token.accessToken).memberId()` = `memberId`.
- 로그인 없이: `Authorization` 없이 연결 요청 → 401, `$.code` = `UNAUTHORIZED`. `countOf("member.social_account")`가 늘지 않았다.

**2. 이미 연결된 계정·제공자** — 준비: 1번 동작 1까지 수행(`link@test.com` 회원이 `kakao-9`를 연결). 두 번째 회원 `link2@test.com`(`signup`) → `member2Id`, `token2 = login("link2@test.com")`.
- 다른 회원이 같은 소셜 계정을 연결: `oauth.givenUser(KAKAO, "kakao-9", …)` 그대로 새 `state`로 `Bearer {token2}` 연결 → 409, `$.code` = `SOCIAL_ACCOUNT_ALREADY_LINKED`. `countOf("member.social_account")` = 1이고 그 1행이 `socialAccountsOf(memberId)`의 것이다.
- 같은 회원이 같은 제공자를 다른 계정으로 연결: `oauth.givenUser(KAKAO, "kakao-10", …)`, 새 `state`로 `Bearer {token}` 연결 → 409, `SOCIAL_ACCOUNT_ALREADY_LINKED`. `socialAccountsOf(memberId)`는 1행 그대로.
- 다른 제공자는 연결된다: `oauth.givenUser(GOOGLE, "google-9", …)`, `issueState(GOOGLE)`, `POST /api/members/me/social-accounts/google` → 204. `socialAccountsOf(memberId)`의 `provider`가 [`KAKAO`, `GOOGLE`] 2행이다.

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
- Redis 장애 시 특별한 처리를 하지 않는다(인증이 필요한 요청은 500으로 실패). 장애 정책은 추가 항목으로 둔다.
- 로그인 잠금: 같은 이메일로 연속 5번 실패하면 15분 동안 423. 존재하지 않는 이메일도 똑같이 센다(가입 여부 노출 방지). 성공하면 카운트 삭제.
- 관리자 검색: 이메일 또는 닉네임 **앞부분 일치**, 상태 필터, 커서 페이징.

### 할 일

**구현 순서**: ① tokenVersion 표 → `TokenVersionProvider` → `JwtAuthenticationFilter` 변경 → `RedisTokenVersionProvider` → `TokenVersionService` → ② 호출 위치 수정(`WithdrawService`·`LogoutService`·`PasswordService`) → ③ 제재(2)의 표 → `AdminMemberService` → API → ④ 로그인 잠금(3): `ACCOUNT_LOCKED` → `LoginAttemptStore` → `LoginService` 변경 → ⑤ ADMIN 토큰 헬퍼 → 테스트.

**1) tokenVersion**

| 대상 | 규격 |
|---|---|
| 마이그레이션 | `V{...}__member_add_token_version.sql`: `member.member`에 `token_version int NOT NULL DEFAULT 0` |
| `Member` | `@Column(name = "token_version", nullable = false) private int tokenVersion;`, `void increaseTokenVersion()`(`tokenVersion++`) |
| `JwtProvider`·`AuthClaims` | 클레임 `tv`(tokenVersion) 추가: `issue(memberId, role, sessionId, emailVerified, int tokenVersion)`, `AuthClaims(..., int tokenVersion)` (4-1의 표). 토큰에는 `.claim("tv", tokenVersion)`으로 넣고 `parse`는 `claims.get("tv", Integer.class)`로 읽는다 |
| `TokenIssuer` | `issueNewSession`·`reissue`·`reissueAccess`가 `jwtProvider.issue(...)`의 `tokenVersion` 인자로 `member.getTokenVersion()`을 넘긴다 |
| 기존 테스트 | `issue` 호출(`JwtProviderTest` 2곳, `MemberControllerTest` 1곳)에 인자를 더한다 |

`TokenVersionProvider` (common.security 인터페이스) `int currentVersion(UUID memberId)` — "이 회원의 지금 버전"을 알려 주는 포트. 인증 필터는 common에 있어 member를 모르므로(모듈 규칙) 인터페이스만 common에 두고 구현(`RedisTokenVersionProvider`)을 member가 만든다.

`JwtAuthenticationFilter` 변경 — 지금은 `parse`가 성공하면 무조건 인증을 설정한다. 이제는 토큰의 버전이 현재 버전과 같을 때만 설정한다.
1. 필터 생성자에 `TokenVersionProvider`를 더한다. 필터는 `SecurityConfig`가 `new JwtAuthenticationFilter(jwtProvider)`로 직접 만들 뿐 Spring 빈이 아니라서 자동 주입되지 않는다. `securityFilterChain(...)`의 인자로 `TokenVersionProvider tokenVersionProvider`를 받아 `new JwtAuthenticationFilter(jwtProvider, tokenVersionProvider)`로 넘긴다
2. `parse(token).ifPresent(claims -> { ... })` 안에서 `claims.tokenVersion() == tokenVersionProvider.currentVersion(claims.memberId())`일 때만 `SecurityContextHolder`에 인증을 넣는다. 다르면 아무것도 하지 않는다
3. 인증이 없으면 `authenticated()` 규칙에서 `RestAuthenticationEntryPoint`가 401 `UNAUTHORIZED`를 응답한다. 공개 경로(`/api/auth/**` 등)는 낡은 토큰이 헤더에 있어도 그대로 통과한다

`RedisTokenVersionProvider` (member.infrastructure, `@Component`) — `TokenVersionProvider` 구현. 의존성: `StringRedisTemplate`, `MemberRepository`. **매 인증 요청마다** 불리므로 가볍게 만든다. `int currentVersion(UUID memberId)` 순서
1. `redis.opsForValue().get("auth:token-version:" + memberId)` — 있으면 `Integer.parseInt`해 반환
2. 없으면(처음이거나 1일 TTL로 만료) `memberRepository.findById(memberId)`의 `tokenVersion`을 읽어 `set(key, String.valueOf(version), Duration.ofDays(1))` 후 반환. 회원 행이 없는 경우는 문서에 없다(확인 필요)

`TokenVersionService` (member.application, `@Service`) — **버전을 올리는 유일한 곳.** `void bump(Member member)`는 **반드시 호출자의 `@Transactional` 안에서** 부른다(트랜잭션이 없으면 아래 `registerSynchronization`이 `IllegalStateException`을 던진다). 순서
1. `member.increaseTokenVersion()` — DB 값은 호출자 트랜잭션이 커밋될 때 UPDATE로 나간다
2. `int version = member.getTokenVersion()`
3. `TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { redis.opsForValue().set("auth:token-version:" + memberId, String.valueOf(version), Duration.ofDays(1)); } })` — DB 커밋이 **성공한 뒤에만** Redis를 바꾼다. 롤백되면 `afterCommit`이 불리지 않아 Redis가 그대로다
- `afterCommit`에서 Redis 쓰기가 실패하면 DB만 올라가고 Redis는 옛 값이 남아, 그 키의 TTL(1일)이 끝날 때까지 이전 토큰이 통과한다. 이 단계는 처리하지 않는다(Redis 장애 정책은 아래 추가 항목)

호출 위치와 같이 하는 일
- `WithdrawService`(3-6): `bump(member)` + `sessionStore.deleteAllOf(memberId)`
- `LogoutService(all=true)`: 이제 회원을 읽어야 하므로 `@Transactional`을 붙이고 `bump(member)` + 기존 `deleteAllOf`
- `PasswordService.change`: `bump` + 기존 `deleteAllOfExcept` + 새 토큰 발급(**bump 뒤의** `member.getTokenVersion()`으로 만들어야 응답 토큰이 막히지 않는다). `confirmReset`: `bump` + 기존 `deleteAllOf`
- 아래 제재

**2) 관리자 제재**

| # | 대상 | 규격 |
|---|---|---|
| 1 | 마이그레이션 `V{...}__member_add_ban_reason.sql` | `member.member`에 `ban_reason varchar(200) NULL` |
| 2 | `Member.banReason` + `ban`·`unban` | `@Column(name = "ban_reason") String banReason`. `ban(String reason)`: `ACTIVE → BANNED`(`status.transitTo`), `banReason = reason` / `unban()`: `BANNED → ACTIVE`, `banReason = null` |
| 3 | `MemberTopics.MEMBER_BANNED` + `MemberBannedEvent` + `MemberTopicConfig` | `MEMBER_BANNED = "member.member-banned.v1"`, `MemberBannedEvent(UUID memberId, String reason)`. 3-3의 `MemberTopicConfig`에 `NewTopic`과 `.dlt`를 더한다. 알림 구독은 아래 "알림 매핑" |
| 4 | `MemberRepository` 검색 조회 | 이메일 **또는** 닉네임의 앞부분 일치(`like :prefix%`, 사용자 입력의 `%`·`_`는 이스케이프), 상태 필터(없으면 전체), `Cursor(createdAt, id)` 커서로 `size + 1`건. 시그니처·이름과 대소문자 구분 여부는 문서에 없다(확인 필요) |

- `AdminMemberService` (member.application, `@Service`) — **관리자가 회원을 찾고 제재·해제하는 유스케이스.** 의존성: `MemberRepository`, `TokenVersionService`, `SessionStore`, `OutboxPublisher`(3-1)

| 메서드 | 하는 일 | 입력 → 반환 |
|---|---|---|
| `@Transactional(readOnly = true) CursorPage<AdminMemberResult> search(String query, MemberStatus status, String cursor, int size)` | 관리자 회원 검색 | `query`(없으면 조건 없음), `status`(없으면 전체), 커서 → 한 페이지 |
| `@Transactional void ban(UUID memberId, String reason)` | 회원을 제재한다 | 아래 순서. 없는 회원은 404 `MEMBER_NOT_FOUND`, 이미 BANNED·WITHDRAWN이면 `status.transitTo`가 409 `INVALID_STATE_TRANSITION` |
| `@Transactional void unban(UUID memberId)` | 제재를 푼다 | `member.unban()`. BANNED가 아니면 409 `INVALID_STATE_TRANSITION` |

- `search`: 커서 페이징은 `OrderQueryService.getMyOrders`와 같은 방식이다(`size + 1`건을 읽어 남으면 `nextCursor`를 만든다). 조회는 위 표 4번이다. `AdminMemberResult`는 응답과 같은 필드이고 `emailVerified`는 `emailVerifiedAt != null`이다
- `ban` 순서
  1. `member.ban(reason)` — `status.transitTo(BANNED)`, `banReason = reason`
  2. `tokenVersionService.bump(member)` — 기존 access를 바로 막는다
  3. `sessionStore.deleteAllOf(memberId)` — refresh도 끊는다
  4. `outboxPublisher.publish(MemberTopics.MEMBER_BANNED, "MEMBER", memberId, new MemberBannedEvent(memberId, reason))` — 트랜잭션 안에서 상태 변경과 함께 발행한다(`OutboxPublisher`는 트랜잭션이 없으면 예외다)
- 알림 매핑: 3-5의 `NotificationType`에 `BANNED`를 더하고 `NotificationComposer`가 `MemberBannedEvent`를 초안으로 바꾸게 한다. 소비자가 새 토픽을 구독하는 방법과 알림 문구는 3-5 규칙을 따른다(확인 필요: 3-5에는 `BANNED`가 아직 없다)
- API (`/admin/api/members`, ADMIN): `GET ?query=&status=&cursor=&size=` → `AdminMemberResponse(id, email, nickname, name, status, role, emailVerified, createdAt)` / `POST /{id}/ban` `BanRequest(@NotBlank @Size(max=200) String reason)` → 204 / `POST /{id}/unban` → 204. ADMIN 인가는 3-4b에서 넣은 `SecurityConfig`의 `/admin/**` → `hasRole("ADMIN")`이 하며(일반 회원은 `RestAccessDeniedHandler`가 403), 컨트롤러는 따로 검사하지 않는다. 컨트롤러 클래스 이름은 문서에 없다(확인 필요)
- 테스트 헬퍼: `AdminMemberTest`는 ADMIN 토큰이 필요하다. 3-4의 정책대로 DB에서 `role`을 `ADMIN`으로 바꾼 뒤 `login`으로 토큰을 다시 받는다(role을 바꾸는 헬퍼의 이름은 정해져 있지 않다, 확인 필요)

**3) 로그인 잠금**

`MemberErrorCode`에 `ACCOUNT_LOCKED`(423, 공통 규칙 U)를 추가한다.

`LoginAttemptStore` (member.domain 인터페이스) + `RedisLoginAttemptStore` (infrastructure) — 이메일별 연속 로그인 실패 횟수를 Redis(`auth:login-fail:{email}`)에 세어 잠금 여부를 알려 준다. `LoginService`가 쓴다. 이메일은 정규화한 값이다. 의존성: `StringRedisTemplate`, `Clock`.

| 메서드 | 하는 일 | 입력 → 반환 |
|---|---|---|
| `Optional<Instant> lockedUntil(String email)` | 지금 잠겨 있는지, 언제 풀리는지 | `get`으로 횟수를 읽어 5 미만이거나 키가 없으면 `Optional.empty()`. 5 이상이면 `getExpire(key, SECONDS)`로 남은 초를 읽어 `clock.instant().plusSeconds(남은 초)` |
| `void recordFailure(String email)` | 실패 1회를 센다 | `increment(key)` 결과가 1이거나 5(잠금 임계 도달)이면 `expire(key, 900초)`를 건다. 그 밖의 실패는 TTL을 바꾸지 않는다 |
| `void reset(String email)` | 횟수를 지운다 | `delete(key)` |

- TTL을 첫 실패에 한 번, **5번째 실패에 한 번 더** 건다. 그래서 잠금은 5번째 실패 시각부터 15분 동안 유지된다(정책의 "15분 동안 423"). 첫 실패 TTL만 쓰면 천천히 실패한 사용자는 5번째 실패 직후 몇 분 만에 풀린다. 잠긴 동안은 `login`이 비밀번호를 확인하기 전에 막아 실패 횟수가 더 늘지 않는다

`LoginService.login` 변경 — 기존 `findByEmail` → 비밀번호 확인 → `verifyCanLogin` → 토큰 발급 흐름에 세 군데를 더한다. 의존성에 `LoginAttemptStore`를 더한다.
1. 이메일 정규화 직후 `loginAttemptStore.lockedUntil(email)`이 있으면 `throw new BusinessException(ACCOUNT_LOCKED, Map.of("unlockAt", unlockAt.toString()))` → 423, `details.unlockAt`(`Instant`를 그대로 넣어도 되지만 직렬화 설정에 따라 숫자가 될 수 있어 `toString()`이 확실하다). 비밀번호를 확인하기 **전에** 막으므로 잠긴 동안은 올바른 비밀번호도 423이다
2. 회원이 없거나, `passwordHash`가 `null`이거나, `matches`가 `false`이면 `loginAttemptStore.recordFailure(email)`을 부른 **뒤에** 기존대로 401 `LOGIN_FAILED`를 던진다. 없는 이메일도 같은 키로 센다(가입 여부 노출 방지). Redis 호출은 예외로 트랜잭션이 롤백돼도 남는다
3. 비밀번호 확인을 통과하면 `loginAttemptStore.reset(email)`. `verifyCanLogin`에서 막히는 BANNED 회원에게도 `reset`을 부르는지 순서는 문서에 없다(확인 필요)


### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 값을 읽는 방법은 4-1~4-3과 같고, 이 단계에서 더하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `adminToken(String email)` → `String` | `signup(email)` → 3-1의 `changeRole(memberId, "ADMIN")` → `login(email)`(role을 바꾼 뒤 다시 로그인해야 토큰에 `ADMIN`이 담긴다) | `TokenRevocationTest`, `AdminMemberTest` |
| `memberSnapshotOf(UUID memberId)`에 `banReason` 추가 | 4-2까지 넓힌 `MemberSnapshot`의 SELECT에 `ban_reason`을 더하고 record에 `banReason`(`String`, 제재 사유가 없으면 `null`)을 더한다. 컬럼은 이 단계의 마이그레이션이 만든다 | `TokenRevocationTest` 1번, `AdminMemberTest` |
| `tokenVersionSnapshotOf(UUID memberId)` → `TokenVersionSnapshot` | DB `SELECT token_version FROM member.member WHERE id = ?`와 Redis `redis.opsForValue().get("auth:token-version:" + memberId)`·`redis.getExpire(key, TimeUnit.SECONDS)`를 한 번에 읽는다. `support/`의 record(`int db`, `Integer redis`(키가 없으면 `null`), `long redisTtlSeconds`(키가 없으면 -2)) | `TokenRevocationTest`, `RedisTokenVersionProviderTest` |

이미 있는 헬퍼: `signup`, `signupUnverified`, `login`, `changeStatus`, `sessionSnapshotOf`, `sessionIdsOf`(4-1), `memberSnapshotOf`(3-1·4-2), `changeRole`(3-1), `redisStringOf`(4-3), `FakeMailSender`, `OutboxAssert`, `Eventually`.

헬퍼를 쓰는 방법은 다음과 같다.
- tokenVersion: `TokenVersionSnapshot v = testFixtures.tokenVersionSnapshotOf(memberId)` 한 번으로 읽는다. 토큰 안의 값은 `parse(accessToken).orElseThrow().tokenVersion()`.
- 회원 상태: `memberSnapshotOf(memberId)`의 `status()`·`banReason()`.
- 제재 이벤트: `OutboxAssert.rows("member.member-banned.v1")`(3-1). 각 행은 `OutboxSnapshot`이고, `payload()`(`JsonNode`)의 `memberId`·`reason` 필드를 확인한다.
- 로그인 잠금: `redisStringOf("auth:login-fail:" + email)`의 `value()`(문자열 횟수, 키가 없으면 `null`)·`ttlSeconds()`·`exists()`. 로그인 요청은 `POST /api/auth/login` 본문 `{"email":"…","password":"…"}`.
- 시간 의존: 잠금 15분 경과는 `redis.expire(키, Duration.ofSeconds(1))` 후 `Eventually.await(Duration.ofSeconds(5), () -> assertThat(testFixtures.redisStringOf(키).exists()).isFalse())`(Part 3 `support/Eventually`)로 키가 사라질 때까지 기다린다. tokenVersion 키의 1일 경과는 `redis.delete(키)`로 흉내 낸다.
- 요청에 쓰는 토큰은 4-1의 `login(email, userAgent)`로 얻는다. 로그인 시점의 `tv`가 그 토큰에 들어간다.

**공통 준비**: 관리자 `admin@test.com`(`adminToken("admin@test.com")`, 위 "테스트 준비"), 일반 회원 `target@test.com`(`signup`, 비밀번호 `pass1234`) → `targetId`. 관리자 API는 `Bearer {adminToken}`이다.

#### `member/web/TokenRevocationTest`

**1. 제재 → 기존 access 거부** — 준비: `login("target@test.com", "device-A")` → `accessA`·`refreshA`·`sidA`. `parse(accessA).tokenVersion()` = 0.
- 동작: `POST /admin/api/members/{targetId}/ban`, `Bearer {adminToken}`, 본문 `{"reason":"스팸"}`.
- 확인: 204. `GET /api/members/me`를 `Bearer {accessA}`로 → 401, `$.code` = `UNAUTHORIZED`. `v = tokenVersionSnapshotOf(targetId)`: `v.db()` = 1, `v.redis()` = 1, `v.redisTtlSeconds()` 80000~86400. `sessionIdsOf(targetId)`가 비어 있다. 관리자 자신의 토큰으로 `GET /api/members/me` → 200(다른 회원은 영향이 없다).

**2. 전체 로그아웃 → 기존 access 거부** — 준비: `login(…, "device-A")` → `accessA`·`refreshA`, `login(…, "device-B")` → `accessB`·`refreshB`.
- 동작: `POST /api/auth/logout?all=true`, `Bearer {accessA}`.
- 확인: 204. `accessA`·`accessB`로 `GET /api/members/me` → 둘 다 401 `UNAUTHORIZED`(같은 회원이라 버전이 함께 올랐다). `tokenVersionSnapshotOf(targetId)`의 `db()` = 1, `redis()` = 1. 다시 `login(…, "device-C")`한 토큰은 `tokenVersion()` = 1이고 `GET /api/members/me` → 200.
- 대조: 다른 회원 `other@test.com`(`signup` → `otherId`, `login(…, "device-A")` → `otherAccess`)이 `?all=false`로 로그아웃(`Bearer {otherAccess}`) → 204. `tokenVersionSnapshotOf(otherId).db()` = 0 그대로.

**3. 비밀번호 변경·재설정 → 기존 access 거부** — 준비: `login(…, "device-A")` → `accessA`·`refreshA`·`sidA`, `login(…, "device-B")` → `accessB`·`refreshB`. 비밀번호를 바꾸면 이후 로그인은 새 비밀번호를 써야 하므로 필요한 로그인은 변경 전에 끝낸다.
- 동작 1: `PATCH /api/members/me/password` 본문 `{"currentPassword":"pass1234","newPassword":"newpass5678"}`, `Bearer {accessA}` → 응답의 `$.accessToken` = `newAccess`.
- 확인 1: 200. `GET /api/members/me`를 `Bearer {accessA}`·`Bearer {accessB}`로 → 둘 다 401 `UNAUTHORIZED`, `Bearer {newAccess}`로 → 200. `parse(newAccess)`: `tokenVersion()` = 1(bump 이후의 버전으로 만들었다). `tokenVersionSnapshotOf(targetId)`의 `db()` = 1, `redis()` = 1.
- 동작 2: 재설정 — `POST /api/auth/password-reset`(본문 `{"email":"target@test.com"}`) → 메일에서 `code` → `POST /api/auth/password-reset/confirm`(본문 `{"email":"target@test.com","code":"{code}","newPassword":"resetpass99"}`).
- 확인 2: 204. `tokenVersionSnapshotOf(targetId)`의 `db()` = 2, `redis()` = 2. 동작 1에서 받은 `newAccess`로 `GET /api/members/me` → 401 `UNAUTHORIZED`.

**4. 회원 탈퇴 → 기존 access 거부** — 준비: 가게·주문이 없는 회원 `leave@test.com`을 `signup` → `leaveId`, `login(…, "device-A")` → `accessA`·`refreshA`.
- 동작: `DELETE /api/members/me`, `Bearer {accessA}`.
- 확인: 204. `GET /api/members/me`를 `Bearer {accessA}`로 → 401 `UNAUTHORIZED`. `tokenVersionSnapshotOf(leaveId).db()` = 1.

**5. 롤백된 트랜잭션에서는 Redis가 바뀌지 않는다** — 준비: `login(…, "device-A")` → `accessA`. `GET /api/members/me`(`Bearer {accessA}`)를 한 번 불러 Redis에 버전 0을 채운다. 테스트에 `TransactionTemplate tx`, `MemberRepository`, `TokenVersionService`를 주입한다.
- 동작 1(롤백): `tx.execute(status -> { Member m = memberRepository.findById(targetId).orElseThrow(); tokenVersionService.bump(m); status.setRollbackOnly(); return null; })`.
- 확인 1: `tokenVersionSnapshotOf(targetId)`의 `db()` = 0, `redis()` = 0(그대로). `GET /api/members/me`를 `Bearer {accessA}`로 → 200.
- 동작 2(대조, 커밋): 같은 코드에서 `status.setRollbackOnly()`만 뺀다.
- 확인 2: `tokenVersionSnapshotOf(targetId)`의 `db()` = 1, `redis()` = 1. `Bearer {accessA}`로 `GET /api/members/me` → 401 `UNAUTHORIZED`.
- 동작 3(트랜잭션 없음): `tokenVersionService.bump(member)`를 `tx` 밖에서 호출 → `IllegalStateException`.

#### `member/infrastructure/RedisTokenVersionProviderTest`

**1. Redis에 값이 없으면 DB 값을 읽어 채운다** — 준비: `UPDATE member.member SET token_version = 3 WHERE id = ?`(`targetId`). `redis.delete("auth:token-version:" + targetId)`. `TokenVersionProvider provider`를 주입한다.
- 동작 1: `provider.currentVersion(targetId)`.
- 확인 1: 반환 `3`. `v = tokenVersionSnapshotOf(targetId)`: `v.redis()` = 3, `v.redisTtlSeconds()` 80000~86400.
- 동작 2: DB를 `token_version = 5`로 UPDATE한 뒤 `provider.currentVersion(targetId)`.
- 확인 2: 반환 `3`(Redis 값이 있으면 DB를 읽지 않는다).
- 동작 3: `redis.delete(키)`(1일 경과 흉내) 후 `provider.currentVersion(targetId)`.
- 확인 3: 반환 `5`, `tokenVersionSnapshotOf(targetId).redis()` = 5.

#### `member/web/AdminMemberTest`

**공통 준비**: 회원 5명을 만든다(`signup`, 닉네임은 이메일의 `@` 앞): `alice@test.com`, `alan@test.com`, `bob@test.com`, `carol@test.com`(`signupUnverified`), 관리자 `admin@test.com`. `bob`은 `UPDATE member.member SET nickname = 'zebra' WHERE id = ?`로 닉네임을 바꾸고 `changeStatus(bobId, "BANNED")`. 목록은 `GET /admin/api/members?query=&status=&cursor=&size=`(`size` = 20), 항목은 `$.items[*]`(`id`, `email`, `nickname`, `name`, `status`, `role`, `emailVerified`, `createdAt`), 다음 페이지 표시는 `$.nextCursor`다.

**1. 일반 회원은 관리자 API를 쓸 수 없다** — 동작: `login("alice@test.com")` 토큰으로 `GET /admin/api/members`, `POST /admin/api/members/{bobId}/unban`. 그리고 토큰 없이 `GET /admin/api/members`.
- 일반 회원의 두 요청: 403, `$.code` = `FORBIDDEN`. `bob`의 `status`는 `BANNED` 그대로.
- 토큰 없음: 401, `$.code` = `UNAUTHORIZED`.

**2. 검색** — 관리자 토큰으로 각각 호출한다.
- `query=al` → `$.items`의 `email`이 `alice@test.com`, `alan@test.com` 둘(순서 무관), `$.nextCursor` null.
- `query=bob` → `bob@test.com` 하나(이메일 앞부분 일치). `query=zeb` → 같은 `bob@test.com` 하나(닉네임 앞부분 일치).
- `query=ice` → 0건(`alice` 중간 글자는 앞부분 일치가 아니다). `query=%` → 0건(와일드카드 이스케이프. 전체가 나오면 실패).
- `status=BANNED` → `bob` 하나. `status=ACTIVE` → 4명(`bob` 제외). 상태 없이 → 5명.
- 항목 값: `alice`의 `emailVerified` = true, `carol`의 `emailVerified` = false, `admin`의 `role` = `ADMIN`.
- 대소문자 구분 여부는 문서에 없어(확인 필요) 확인하지 않는다.

**3. 커서 페이징** — 동작: `size=2`로 호출 → `$.nextCursor`로 다음 호출 → 반복.
- 페이지 크기 2, 2, 1이고 마지막 페이지의 `$.nextCursor`는 null이다.
- 세 페이지의 `id`를 합치면 5명이 모두 한 번씩 나온다(중복·누락 없음). 정렬 방향은 문서에 없어(확인 필요) 확인하지 않는다.

**4. 제재 → 로그인 거부 → 해제 → 로그인** — 준비: `alice`가 `login(…, "device-A")` → `sidA`. 
- 동작 1: `POST /admin/api/members/{aliceId}/ban`, 본문 `{"reason":"운영정책 위반"}`.
- 확인 1: 204. `m = memberSnapshotOf(aliceId)`: `m.status()` = `BANNED`, `m.banReason()` = `운영정책 위반`. `sessionSnapshotOf(sidA).exists()` = false. `OutboxAssert.rows("member.member-banned.v1")` 1건, `aggregate_id` = `aliceId`, `payload`의 `memberId` = `aliceId`, `reason` = `운영정책 위반`. 알림(`BANNED`) 확인은 문서에 소비자 규격이 없어(확인 필요) 이 단계에서 제외한다.
- 동작 2: `POST /admin/api/members/{aliceId}/unban`.
- 확인 2: 204. `memberSnapshotOf(aliceId)`: `status()` = `ACTIVE`, `banReason()` = null. 로그인 → 200, `$.accessToken` 있음.

**5. 제재·해제 거부** — 모두 `Bearer {adminToken}`.
- 이미 제재된 회원 `bob`에게 ban(본문 `{"reason":"중복"}`) → 409, `$.code` = `INVALID_STATE_TRANSITION`. outbox `member.member-banned.v1` 0건.
- 정지가 아닌 회원 `alice`에게 unban → 409, `$.code` = `INVALID_STATE_TRANSITION`.
- 없는 회원 ID로 ban → 404, `$.code` = `MEMBER_NOT_FOUND`.
- 사유 없이(`{"reason":""}`) ban → 400, `$.code` = `INVALID_REQUEST`. `alice`의 `status`는 `ACTIVE` 그대로.

#### `member/web/LoginLockTest`

**공통 준비**: 회원 `lock@test.com`(`signup`, 비밀번호 `pass1234`) → `memberId`. 키 `K` = `auth:login-fail:lock@test.com`. 실패 로그인은 비밀번호 `wrongpass1`, 성공 로그인은 `pass1234`. 가입 때 만든 세션의 수 `before` = `sessionIdsOf(memberId).size()`를 읽어 둔다.

**1. 5번 실패하면 올바른 비밀번호도 423** — 동작: 실패 로그인을 5번, 이어 올바른 비밀번호로 6번째.
- 앞의 5번: 매번 401, `$.code` = `LOGIN_FAILED`. 각 요청 직후 `redisStringOf(K).value()`가 `"1"`, `"2"`, …, `"5"`. 1번째 직후 `ttlSeconds()` 1~900. 4번째 직후 `redis.expire(K, Duration.ofSeconds(60))`로 줄여 두고, 5번째 직후 `ttlSeconds()`가 890~900이다(5번째 실패에서 다시 건다).
- 6번째(올바른 비밀번호): 423, `$.code` = `ACCOUNT_LOCKED`, `$.details.unlockAt`이 `Instant.parse`되고 `Duration.between(Instant.now(), unlockAt)`이 14분~15분이다.
- 잠긴 동안 `redisStringOf(K).value()` = `"5"` 그대로(비밀번호를 확인하지 않아 횟수가 늘지 않는다). 세션 수가 `before`와 같다(새 세션이 생기지 않았다).
- 잠긴 상태에서 틀린 비밀번호로 한 번 더 → 423 `ACCOUNT_LOCKED`, `redisStringOf(K).value()` = `"5"`.

**2. 시간이 지나면 풀린다** — 준비: 위 1번의 5번 실패 후 6번째 시도가 423인 것까지.
- 동작: `redis.expire(K, Duration.ofSeconds(1))` → `Eventually.await(5초, () -> redisStringOf(K).exists()가 false)` → 올바른 비밀번호로 로그인.
- 확인: 200, `$.accessToken`·`$.refreshToken` 있음.

**3. 존재하지 않는 이메일도 센다** — 준비: 가입하지 않은 `ghost@test.com`, 키 `auth:login-fail:ghost@test.com`.
- 동작: 틀린 비밀번호로 5번, 6번째 시도.
- 확인: 앞의 5번은 401 `LOGIN_FAILED`(가입된 이메일과 같은 응답). 5번째 직후 `redisStringOf(키).value()` = `"5"`. 6번째: 423, `$.code` = `ACCOUNT_LOCKED`, `$.details.unlockAt` 있음(1번과 같은 상태·코드).

**4. 성공하면 초기화** — 동작: 실패 4번 → 올바른 비밀번호 로그인 → 실패 4번.
- 올바른 로그인: 200, `redisStringOf(K).exists()` = false.
- 이어진 실패 4번 뒤 `redisStringOf(K).value()` = `"4"`(5가 아니다. 카운트가 0부터 다시 시작했다).

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
