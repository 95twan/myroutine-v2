# 서브: 세션 인증

> 버전 0.1 · 2026-11-22 · JWT·refresh 토큰을 DB 세션과 헤더의 세션 토큰으로 바꾼다(SUB-25)

> **서브**: 로드맵에서 뺀 기능이다. Stage 1(Part 6) 뒤에 한다.
> **따르는 규칙**: 로드맵 Part 1~6의 공통 규칙. 결정과 이유는 [ADR-015](../adr/ADR-015-session-auth.md)다.

---

## SUB-25. 세션 인증 전환 (M)

**목표**: 로그인하면 DB에 세션을 만들고 세션 토큰(회원 정보가 없는 무작위 문자열)을 준다. 인증 필터는 요청마다 토큰 해시로 세션과 회원을 읽는다. JWT, refresh 토큰, tokenVersion을 지운다. SUB-24 다음에 한다.

### 바뀌는 것

| 항목 | 지금 | 바뀐 뒤 |
|---|---|---|
| 토큰 | access JWT(15분) + refresh(7일) | 세션 토큰 하나. 마지막 사용 뒤 7일 |
| 세션 저장 | Redis `auth:session:*`, `auth:member-sessions:*` | DB `member.auth_session` |
| 요청 인증 | JWT 서명 검증 + Redis `auth:token-version` 비교 | 토큰 해시로 세션과 회원을 읽고 만료·회원 상태 확인 |
| 역할 | 토큰에 담아 다음 발급 때 반영 | 회원 행에서 읽어 다음 요청부터 반영 |
| 끊기 | tokenVersion을 올리고 Redis 세션 삭제 | 같은 트랜잭션에서 세션 행 삭제 |
| 갱신 | `POST /api/auth/refresh` | 없음 |
| 비밀번호 변경 응답 | 200 + 새 access 토큰 | 204 |

### 정책

- 토큰은 무작위 32바이트를 base64url(패딩 없음, 43자)로 쓴 값이다. DB에는 원문 대신 SHA-256 hex(64자)를 `token_hash`로 저장한다.
- 인증 조건은 토큰 해시로 찾은 세션의 `expires_at`이 지금보다 뒤이고 회원 `status`가 `ACTIVE`인 것이다. 아니면 인증하지 않고, 보호된 경로는 지금처럼 401 `UNAUTHORIZED`다.
- 인증에 성공했고 `expires_at`이 지금 + 6일보다 앞이면(마지막 연장 뒤 하루가 지남) `expires_at`을 지금 + 7일로 바꾼다. 요청마다 쓰지 않으려고 하루에 한 번만 연장한다.
- 세션을 지우는 곳은 아래와 같고, 모두 호출한 서비스의 트랜잭션 안에서 지운다.

| 기능 | 지우는 세션 |
|---|---|
| 로그아웃 | 그 요청의 세션 |
| 전체 로그아웃(`?all=true`) | 그 회원의 모든 세션 |
| 비밀번호 변경 | 그 요청의 세션을 뺀 그 회원의 세션 |
| 비밀번호 재설정 확인, 제재, 탈퇴 | 그 회원의 모든 세션. 제재·탈퇴는 상태 확인으로도 막히지만, 제재를 풀었을 때 옛 세션이 살아나지 않게 지운다 |

- 만료된 세션은 매일 04:00(Asia/Seoul)에 정리 작업이 지운다.
- 배포하면 이전 JWT는 모두 인증되지 않아 사용자가 한 번 다시 로그인한다. Redis에 남은 `auth:session:*`, `auth:member-sessions:*`(7일), `auth:token-version:*`(1일)은 만료로 사라진다.

### 할 일

**1) DB** — `db/migration/member/`

`V{yyyyMMddHHmm}__member_create_auth_session.sql`

```sql
CREATE TABLE member.auth_session (
    id          uuid         NOT NULL,
    member_id   uuid         NOT NULL,
    token_hash  varchar(64)  NOT NULL,
    expires_at  timestamptz  NOT NULL,
    created_at  timestamptz  NOT NULL,
    CONSTRAINT pk_auth_session PRIMARY KEY (id),
    CONSTRAINT fk_auth_session_member FOREIGN KEY (member_id) REFERENCES member.member (id),
    CONSTRAINT uk_auth_session_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_auth_session_member ON member.auth_session (member_id);
CREATE INDEX idx_auth_session_expires_at ON member.auth_session (expires_at);
```

`member_id` 인덱스는 회원 단위 삭제가, `expires_at` 인덱스는 정리 작업이 쓴다.

`member.token_version` 컬럼은 이 단계에서 지우지 않는다. 롤백은 이미지만 되돌리므로 이전 앱(JWT)이 새 스키마에서 떠야 하고, 컬럼 삭제는 "코드 전환 → 삭제"의 두 배포로 나눈다([ADR-011](../adr/ADR-011-ops-practice-environment.md) 결정 7). `Member`에서 필드만 빼고, 컬럼은 `NOT NULL DEFAULT 0`이라 새 앱이 회원을 저장해도 기본값이 들어간다. 삭제는 "머지한 뒤"를 따른다.

**2) member.domain**

| 타입 | 규격 |
|---|---|
| `SessionToken` | record `(String value)`. `static SessionToken generate()`는 `SecureRandom` 32바이트를 base64url로. `String hash()`는 `value`의 SHA-256 hex. `static String hashOf(String raw)`는 클라이언트가 보낸 원문의 해시. `RefreshToken`의 생성·해시 코드를 옮기고 `RefreshToken`은 지운다 |
| `Session` | 엔티티(`member.auth_session`). 필드 `id`, `memberId`, `tokenHash`, `expiresAt`, `createdAt`. `static Session open(UUID memberId, String tokenHash, Instant now, Duration ttl)`은 `id = Ids.newId()`, `expiresAt = now + ttl`, `createdAt = now` |
| `AuthenticatedSession` | record `(UUID sessionId, UUID memberId, MemberRole role, Instant expiresAt)`. 인증 조회의 결과 |
| `SessionRepository` | 아래 표 |
| `Member` | `tokenVersion` 필드와 `increaseTokenVersion()`을 지운다 |
| `MemberErrorCode` | `REFRESH_REUSED`를 지운다 |
| `SessionStore` | 지운다 |

`SessionRepository` (구현은 3)의 `JpaSessionRepository`)

| 메서드 | 쿼리 |
|---|---|
| `Session save(Session session)` | `JpaRepository.save` |
| `Optional<AuthenticatedSession> findAuthenticated(String tokenHash, Instant now)` | `select new ...AuthenticatedSession(s.id, s.memberId, m.role, s.expiresAt) from Session s join Member m on m.id = s.memberId where s.tokenHash = :tokenHash and s.expiresAt > :now and m.status = MemberStatus.ACTIVE` |
| `int extend(UUID sessionId, Instant expiresAt)` | `@Modifying @Transactional`. `update Session s set s.expiresAt = :expiresAt where s.id = :sessionId`. 필터에서 트랜잭션 없이 부르므로 메서드에 `@Transactional`을 단다 |
| `int deleteBySessionId(UUID sessionId)` | `@Modifying`. `delete from Session s where s.id = :sessionId` |
| `int deleteAllByMemberId(UUID memberId)` | `@Modifying`. `delete from Session s where s.memberId = :memberId` |
| `int deleteAllByMemberIdExcept(UUID memberId, UUID keepSessionId)` | `@Modifying`. `delete from Session s where s.memberId = :memberId and s.id <> :keepSessionId` |
| `int deleteExpired(Instant now)` | `@Modifying`. `delete from Session s where s.expiresAt <= :now` |

삭제는 한 문장으로 지우는 `@Query` 벌크 삭제다. 메서드 이름으로 만든 삭제(`deleteAllBy...`를 쿼리 없이)는 행을 읽은 뒤 하나씩 지운다.

**3) member.infrastructure**

| 클래스 | 규격 |
|---|---|
| `JpaSessionRepository` | `JpaRepository<Session, UUID>, SessionRepository`. 2)의 쿼리 |
| `SessionCleanupJob` | `@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")`에서 `jobRunner.run("session-cleanup", sessionCleanupService::deleteExpired)` |
| `RedisSessionStore`, `RedisTokenVersionProvider`, `resources/redis/rotate.lua` | 지운다 |
| `OAuthConfig` 또는 새 설정 클래스 | `@EnableConfigurationProperties`에 `SessionProperties`를 더한다 |

**4) member.application**

| 클래스 | 규격 |
|---|---|
| `SessionProperties` | `@ConfigurationProperties("myroutine.session")` record `(Duration ttl)` |
| `SessionIssuer` | `TokenIssuer`를 대신한다. `@Transactional String issue(Member member)`: `SessionToken.generate()` → `sessionRepository.save(Session.open(member.getId(), token.hash(), clock.instant(), properties.ttl()))` → `token.value()`. 부르는 쪽에 트랜잭션이 있으면 그 안에서 만든다 |
| `SessionAuthenticationService` | `SessionAuthenticator` 구현(5). `Optional<AuthClaims> authenticate(String rawToken)`, 트랜잭션 없음. ① `now = clock.instant()` ② `findAuthenticated(SessionToken.hashOf(rawToken), now)`가 비면 빈 값 ③ `expiresAt`이 `now + ttl - 1일`보다 앞이면 `extend(sessionId, now + ttl)` ④ `new AuthClaims(sessionId, memberId, role.name())` |
| `SessionCleanupService` | `@Transactional int deleteExpired()`: `sessionRepository.deleteExpired(clock.instant())` |
| `LoginService`, `SignupService`, `OAuthLoginService` | `tokenIssuer.issueNewSession(member)`를 `sessionIssuer.issue(member)`로. 결과의 `TokenResult`를 `String token`으로(`SignupResult`, `OAuthLoginResult`의 필드도) |
| `LogoutService` | `@Transactional logout(memberId, sessionId, all)`: `all`이면 `deleteAllByMemberId(memberId)`, 아니면 `deleteBySessionId(sessionId)` |
| `PasswordService` | `change`는 `void`를 돌려주고 `deleteAllByMemberIdExcept(memberId, sessionId)`. `confirmReset`은 `deleteAllByMemberId(member.getId())` |
| `AdminMemberService.ban`, `WithdrawService.withdraw` | `tokenVersionService.bump` + `sessionStore.deleteAllOf`를 `sessionRepository.deleteAllByMemberId`로 |
| `TokenIssuer`, `TokenResult`, `TokenRefreshService`, `TokenVersionService` | 지운다 |

**5) common.security**

| 클래스 | 규격 |
|---|---|
| `AuthClaims` | record `(UUID sessionId, UUID memberId, String role)`. `tokenVersion`을 뺀다 |
| `SessionAuthenticator` | `TokenVersionProvider`를 대신한다. `Optional<AuthClaims> authenticate(String token)` |
| `SessionAuthenticationFilter` | `JwtAuthenticationFilter`의 이름과 의존을 바꾼다. `Bearer ` 뒤의 문자열로 `sessionAuthenticator.authenticate(token)`을 부르고, 값이 있으면 지금처럼 `SecurityContext`와 MDC `memberId`를 채운다 |
| `SecurityConfig` | 필터 생성에 `SessionAuthenticator`를 넘긴다. `@EnableConfigurationProperties`에서 `JwtProperties`를 뺀다 |
| `JwtProvider`, `JwtProperties`, `TokenVersionProvider` | 지운다 |

**6) member.web**

| 클래스 | 규격 |
|---|---|
| `AuthController` | `POST /api/auth/refresh`와 `RefreshRequest`를 지운다 |
| `TokenResponse` | record `(String token, String tokenType)`. `from(String token)`은 `tokenType = "Bearer"` |
| `SignupResponse` | record `(UUID memberId, String token, String tokenType)` |
| `OAuthLoginResponse` | `token` 필드는 그대로 `TokenResponse`(가입이 필요하면 `null`) |
| `MemberController.changePassword` | `ResponseEntity<Void>` 204 |

**7) 설정·빌드**

| 파일 | 바꿀 것 |
|---|---|
| `application.yaml` | `myroutine.jwt`를 지우고 `myroutine.session.ttl: 7d`. `spring.task.scheduling.pool.size`를 11에서 12로(예약 작업 수와 맞춘다, [S2-3](../perf/s2-3-pool-scheduling.md)) |
| `application-test.yaml` | `myroutine.jwt`를 지운다 |
| `build.gradle` | `io.jsonwebtoken:jjwt-*` 세 줄을 지운다 |
| `.env.example`, `.env.ops.example`, `.env.local`, 운영 VM `.env` | `JWT_SECRET`을 지운다. 앞의 셋은 Claude가 고치고, VM `.env`는 사용자가 고친다 |

### 완료 확인

케이스는 [로드맵 README 4.1](../03-roadmap/README.md)의 기준으로 골랐다.

테스트 보조: `TestFixtures`

- `token(email)`은 가입 결과의 `token()`을, `login(email)`은 `loginService.login(...)`의 토큰을 돌려준다. `loginTokens`는 지운다.
- `sessionRowsOf(UUID memberId)` → `List<SessionRow>`(record `(UUID id, String tokenHash, Instant expiresAt)`, `created_at` 순). `jdbcTemplate`으로 `member.auth_session`을 읽는다.
- Redis 세션 헬퍼(`sessionSnapshotOf`, `sessionIdsOf`, `memberSessionsTtlOf`, `tokenVersionSnapshotOf`)와 `SessionSnapshot`, `TokenVersionSnapshot`을 지운다.

#### `member/web/SessionAuthTest` (`@AutoConfigureMockMvc`)

| 케이스 | 준비 | 동작 | 확인할 것 |
|---|---|---|---|
| 1. 로그인 응답의 토큰으로 인증한다. | `signup("test@test.com")` | `POST /api/auth/login` 본문 `{"email":"test@test.com","password":"pass1234"}` → 응답 `$.token`으로 `GET /api/members/me` | 상태 200 |
| 2. 세션에 토큰 원문 대신 해시를 저장한다. | `signup` | `login("test@test.com")` | `sessionRowsOf(memberId)`의 한 행: `tokenHash()` = `SessionToken.hashOf(토큰)`, 토큰 원문과 다름 |
| 3. 만료된 세션으로 인증을 실패한다. (401) | `signup` → `login` → `jdbcTemplate.update("UPDATE member.auth_session SET expires_at = now() - interval '1 second' WHERE member_id = ?", memberId)` | 그 토큰으로 `GET /api/members/me` | 상태 401, `$.code` = `UNAUTHORIZED` |
| 4. 마지막 연장 뒤 하루가 지난 세션은 만료를 7일 뒤로 미룬다. | `signup` → `login` → `expires_at`을 `now() + interval '5 days'`로 | 요청 전 시각 `before`를 잡고 `GET /api/members/me` | `sessionRowsOf(memberId).get(0).expiresAt()` ≥ `before + 7일` |
| 5. 연장한 지 하루가 안 된 세션은 만료를 바꾸지 않는다. | `signup` → `login` → `sessionRowsOf`로 `expiresAt` 읽기 | `GET /api/members/me` | `expiresAt`이 준비 때와 같다 |
| 6. 회원이 ACTIVE가 아니면 세션이 남아 있어도 인증을 실패한다. (401) | `signup` → `login` → `changeStatus(memberId, "BANNED")`(세션은 그대로) | `GET /api/members/me` | 상태 401 |
| 7. 역할을 바꾸면 같은 토큰의 다음 요청부터 반영한다. | `signup` → `login` → `jdbcTemplate.update("UPDATE member.member SET role = 'ADMIN' WHERE id = ?", memberId)` | 같은 토큰으로 `GET /admin/api/members` | 상태 200 |
| 8. Redis가 응답하지 않아도 세션으로 인증한다. | `signup` → `login` | Redis 일시정지([Redis 장애 대응](redis-outage.md) 공통 "장애를 흉내 내는 방법") → `GET /api/members/me` | 상태 200 |

#### `member/application/SessionCleanupServiceTest`

| 케이스 | 준비 | 동작 | 확인할 것 |
|---|---|---|---|
| 9. 만료된 세션을 지운다. | `signup` → `login` 두 번(세션 둘) → 첫 세션의 `expires_at`을 `now() - interval '1 second'`로 | `sessionCleanupService.deleteExpired()` | `sessionRowsOf(memberId)`에 첫 세션 `id`가 없다 |
| 10. 만료되지 않은 세션은 남긴다. | 9번과 같다 | 9번과 같다 | `sessionRowsOf(memberId)`에 둘째 세션 `id`가 있다 |

### 기존 테스트

| 테스트 | 바꿀 것 |
|---|---|
| `TokenRefreshTest` | `SessionTest`로 이름을 바꾼다. refresh 갱신 케이스(갱신·재사용 탐지·secret 불일치·빈 본문·없는 세션·정지·탈퇴·만료 연장·세션 목록 만료)를 지운다. "가입 응답에 refresh 토큰이 들어 있고 세션이 만들어진다"는 "가입하면 세션이 만들어진다"로, "Redis에 refresh 원문을 저장하지 않는다"는 지운다(새 2번). 로그아웃 케이스는 `sessionRowsOf`로 확인한다. "같은 access로 로그아웃을 두 번 해도 204"는 로그아웃한 토큰이 인증되지 않으므로 "로그아웃한 토큰으로 인증을 실패한다. (401)"로 바꾼다 |
| `TokenRevocationTest` | `SessionRevocationTest`로 이름을 바꾸고 "access 토큰"을 "세션"으로. 비밀번호 변경 케이스는 "다른 기기의 세션은 인증을 실패하고 바꾼 기기는 인증한다"로(응답 204). "전체 로그아웃이 아닌 로그아웃은 토큰 버전을 올리지 않는다"는 "로그아웃은 다른 기기의 세션을 남긴다"로 |
| `RefreshConcurrencyTest`, `TokenVersionServiceTest`, `RedisTokenVersionProviderTest`, `JwtProviderTest` | 지운다 |
| `MemberControllerTest` "만료된 토큰" | 지운다(새 3번) |
| `OAuthLoginTest` | `jwtProvider.parse(accessToken)`의 `memberId`·`role` 확인을, 응답 토큰으로 `GET /api/members/me`를 불러 `$.id`와 비교하는 것으로. 역할 확인은 지운다(역할은 회원 행에서 읽는다) |
| `PasswordTest`, `AdminMemberTest`, `LoginLockTest` | 비밀번호 변경 응답 204, Redis 세션 헬퍼를 `sessionRowsOf`로 |
| `common/error/RedisUnavailableTest` | 바꿀 것 없음 |

### 문서 반영

단계를 마칠 때 Claude가 고친다.

| 문서 | 고칠 곳 |
|---|---|
| [API 명세](../02-design/07-api-spec.md) | 1절 인증 줄(`Bearer {token}`), 401 코드에서 `REFRESH_REUSED`, `/api/auth/refresh` 행, 가입·로그인·소셜 로그인의 응답, 비밀번호 변경 204 |
| [ERD](../02-design/03-erd.md) | `auth_session` 추가, `member.token_version`은 쓰지 않음(다음 배포에서 삭제) |
| [시퀀스](../02-design/06-sequences.md) | 9절 "인증: 토큰 갱신 · 즉시 무효화" |
| [요구사항](../01-requirements/README.md) | FR-MEM-03(access·refresh 발급과 회전)을 세션 발급과 마지막 사용 뒤 7일 만료로, FR-MEM-04를 세션 삭제로 |
| [로드맵 4-1·4-4](../03-roadmap/part4-auth.md) | 4-1(refresh 회전)·4-4(tokenVersion)의 머리에 "SUB-25가 세션으로 바꿨다" 한 줄 |
| [Redis 장애 대응](redis-outage.md) | 공통 "지금 Redis를 쓰는 곳"에서 세션·토큰 버전 키 |
| [알려진 문제](../known-issues.md) | 세션·토큰 버전 행을 지운다. 3.4에 "쓰지 않는 컬럼 `member.token_version`"(확인됨, 다음 배포에서 삭제)을 더하고, 컬럼을 지우면 그 행도 지운다 |
| [서브 SUB-8](log-coverage.md) | "JWT 거부 사유"를 세션 인증 실패 사유(없음·만료·회원 상태)로 |
| `README.md`, `CLAUDE.md` | 인증 설명 |

프론트(`myroutine-web`)는 갱신 흐름을 지우고 응답 필드를 `token`으로 바꾼다. 그쪽 리포의 일이다.

### 머지한 뒤

- 이 단계를 배포해 JWT로 돌아갈 일이 없다고 확인되면, 다음 배포에 `V{yyyyMMddHHmm}__member_drop_token_version.sql`(`ALTER TABLE member.member DROP COLUMN token_version;`)을 따로 넣는다. 운영 DB의 컬럼을 지우는 변경이라 배포 전에 한 번 더 확인한다. 값은 버전 카운터뿐이라 잃어도 되는 데이터다.
- [SUB-16](stage2-leftovers.md)의 S-04로 이 단계 앞(JWT)과 뒤(DB 세션)의 인증 비용을 재고, 그 결과로 세션 앞에 Redis 캐시를 둘지 정한다(같은 문서 "S-04와 세션 캐시").

**리뷰 때 물어볼 것**

- 토큰 원문이 아니라 해시를 저장하는 이유는? 해시로 찾으면 무엇을 잃나?
- 만료 연장을 요청마다 하지 않고 하루에 한 번만 하는 이유는? 그 대가로 만료가 어떻게 달라지나?
- 역할을 세션에 저장하지 않고 회원 행에서 읽는 이유는? 제재된 회원의 세션을 지우는데도 상태를 확인하는 이유는?
- `extend`에 `@Transactional`이 없으면 어떻게 되나?
