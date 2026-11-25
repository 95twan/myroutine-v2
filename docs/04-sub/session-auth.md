# 서브: 세션을 DB로

> 버전 0.2 · 2026-11-24 · 단일 세션 토큰 대신 JWT access와 refresh 회전·재사용 탐지를 유지하고, 세션만 Redis에서 DB로 옮긴다. 인증 필터가 요청마다 세션 행과 회원 상태·역할을 확인하고 tokenVersion을 지운다
> 0.1 · 2026-11-22 · JWT·refresh 토큰을 DB 세션과 헤더의 세션 토큰으로 바꾼다(SUB-25)

> **서브**: 로드맵에서 뺀 기능이다. Stage 1(Part 6) 뒤에 한다.
> **따르는 규칙**: 로드맵 Part 1~6의 공통 규칙. 결정과 이유는 [ADR-015](../adr/ADR-015-session-auth.md)다.

---

## SUB-25. 세션 저장소를 DB로 옮기기 (M)

**목표**: refresh 세션을 Redis에서 DB `member.auth_session`으로 옮기고, 인증 필터가 요청마다 access의 세션(`sid`)이 살아 있는지와 회원 상태·역할을 DB에서 확인하게 한다. 끊기는 세션 행 삭제로 하고 tokenVersion을 지운다. access JWT, refresh 회전, 재사용 탐지, API와 응답 모양은 그대로다. SUB-24 다음에 한다.

### 바뀌는 것

| 항목 | 지금 | 바뀐 뒤 |
|---|---|---|
| refresh 세션 | Redis `auth:session:*`, `auth:member-sessions:*`, TTL | DB `member.auth_session` |
| 요청 인증 | JWT 서명 + Redis `auth:token-version` 비교 | JWT 서명 + DB에서 `sid`의 세션이 만료 전이고 회원이 `ACTIVE`인지 |
| 역할 | access의 `role` 클레임. 다음 갱신 때 반영 | 회원 행에서 읽어 다음 요청부터 반영 |
| 끊기 | tokenVersion을 올리고 Redis 세션 삭제 | 같은 트랜잭션에서 세션 행 삭제. 그 세션의 access도 다음 요청부터 거부 |
| refresh 회전 | `rotate.lua` | 조건부 `UPDATE` |
| access 클레임 | `sub`, `role`, `sid`, `tv` | `sub`, `sid` |

### 정책

- access JWT(15분)는 회전하지 않고 저장하지도 않는다. 서명과 만료로 유효하고, 차단은 세션 행이 맡는다.
- 인증 조건은 access의 서명·만료가 맞고, `sid`의 세션이 있고 `expires_at`이 지금보다 뒤이며, 세션의 회원이 access의 `sub`와 같고 `ACTIVE`인 것이다. 아니면 인증하지 않고, 보호된 경로는 지금처럼 401 `UNAUTHORIZED`다.
- refresh 회전과 재사용 탐지는 지금과 같다. 저장된 해시와 같을 때만 새 해시로 바꾸고 `expires_at`을 지금 + 7일로 미룬다. 다르면 재사용으로 보고 세션을 지운 뒤 401 `REFRESH_REUSED`다. 세션이 없거나 만료됐으면 401 `UNAUTHORIZED`다.
- 세션을 지우는 곳은 아래와 같다.

| 기능 | 지우는 세션 | 트랜잭션 |
|---|---|---|
| 로그아웃 | 그 요청의 세션 | `LogoutService` |
| 전체 로그아웃(`?all=true`) | 그 회원의 모든 세션 | `LogoutService` |
| 비밀번호 변경 | 그 요청의 세션을 뺀 그 회원의 세션 | `PasswordService.change` |
| 비밀번호 재설정 확인, 제재, 탈퇴 | 그 회원의 모든 세션. 제재·탈퇴는 회원 상태로도 막히지만, 제재를 풀었을 때 옛 세션이 살아나지 않게 지운다 | 각 서비스 |
| refresh 재사용, 갱신할 때 회원이 없거나 로그인할 수 없음 | 그 세션 | 삭제를 커밋한 뒤 예외를 던진다(할 일 4)) |

- 만료된 세션은 매일 04:00(Asia/Seoul)에 정리 작업이 지운다.
- 절대 만료는 두지 않는다. refresh를 쓸 때마다 7일이 다시 시작된다.
- 배포하면 Redis의 refresh 세션을 읽지 않으므로 사용자가 한 번 다시 로그인한다. 남은 Redis 키(`auth:session:*`, `auth:member-sessions:*` 7일, `auth:token-version:*` 1일)는 만료로 사라진다.

### 할 일

**1) DB** — `db/migration/member/V{yyyyMMddHHmm}__member_create_auth_session.sql`

```sql
CREATE TABLE member.auth_session (
    id            uuid         NOT NULL,
    member_id     uuid         NOT NULL,
    refresh_hash  varchar(64)  NOT NULL,
    expires_at    timestamptz  NOT NULL,
    created_at    timestamptz  NOT NULL,
    CONSTRAINT pk_auth_session PRIMARY KEY (id),
    CONSTRAINT fk_auth_session_member FOREIGN KEY (member_id) REFERENCES member.member (id)
);

CREATE INDEX idx_auth_session_member ON member.auth_session (member_id);
CREATE INDEX idx_auth_session_expires_at ON member.auth_session (expires_at);
```

세션은 기본키(`sid`)로 찾는다. `member_id` 인덱스는 회원 단위 삭제가, `expires_at` 인덱스는 정리 작업이 쓴다. `refresh_hash`는 `RefreshToken.secretHash()`(SHA-256 hex)다.

`member.token_version` 컬럼은 이 단계에서 지우지 않는다. 롤백은 이미지만 되돌리므로 이전 앱이 새 스키마에서 떠야 하고, 컬럼 삭제는 "코드 전환 → 삭제"의 두 배포로 나눈다([ADR-011](../adr/ADR-011-ops-practice-environment.md) 결정 7). `Member`에서 필드만 빼고, 컬럼은 `NOT NULL DEFAULT 0`이라 새 앱이 회원을 저장해도 기본값이 들어간다. 삭제는 "머지한 뒤"를 따른다.

**2) member.domain**

| 타입 | 규격 |
|---|---|
| `Session` | 엔티티(`member.auth_session`). 필드 `id`, `memberId`, `refreshHash`, `expiresAt`, `createdAt`. `static Session open(UUID id, UUID memberId, String refreshHash, Instant now, Duration ttl)`은 `expiresAt = now + ttl`, `createdAt = now`. `id`는 refresh 토큰에 넣으므로 밖에서 받는다 |
| `ActiveSession` | record `(UUID memberId, MemberRole role)`. 인증 조회의 결과 |
| `SessionRepository` | 아래 표 |
| `Member` | `tokenVersion` 필드와 `increaseTokenVersion()`을 지운다 |
| `SessionStore` | 지운다 |

`SessionRepository` (구현은 3)의 `JpaSessionRepository`)

| 메서드 | 쿼리 |
|---|---|
| `Session save(Session session)` | `JpaRepository.save` |
| `Optional<Session> findById(UUID id)` | `JpaRepository.findById` |
| `Optional<ActiveSession> findActive(UUID sessionId, Instant now)` | `select new ...ActiveSession(s.memberId, m.role) from Session s join Member m on m.id = s.memberId where s.id = :sessionId and s.expiresAt > :now and m.status = MemberStatus.ACTIVE` |
| `int rotate(UUID sessionId, String expectedHash, String newHash, Instant expiresAt)` | `@Modifying @Transactional`. `update Session s set s.refreshHash = :newHash, s.expiresAt = :expiresAt where s.id = :sessionId and s.refreshHash = :expectedHash` |
| `int deleteBySessionId(UUID sessionId)` | `@Modifying @Transactional`. `delete from Session s where s.id = :sessionId` |
| `int deleteAllByMemberId(UUID memberId)` | `@Modifying`. `delete from Session s where s.memberId = :memberId` |
| `int deleteAllByMemberIdExcept(UUID memberId, UUID keepSessionId)` | `@Modifying`. `delete from Session s where s.memberId = :memberId and s.id <> :keepSessionId` |
| `int deleteExpired(Instant now)` | `@Modifying`. `delete from Session s where s.expiresAt <= :now` |

- `@Modifying` 쿼리는 트랜잭션 없이 부르면 실패한다. `rotate`와 `deleteBySessionId`는 트랜잭션 없는 `TokenRefreshService`(할 일 4))가 부르고, 단계마다 쿼리 하나라 저장소 메서드에 `@Transactional`을 단다. 서비스의 트랜잭션 안에서 부르면 그 트랜잭션에 합류한다. 나머지는 늘 `@Transactional` 서비스 안에서 불러 달지 않는다.
- 삭제는 한 문장으로 지우는 `@Query` 벌크 삭제다. 메서드 이름으로 만든 삭제(쿼리 없는 `deleteAllBy...`)는 행을 읽은 뒤 하나씩 지운다.

**3) member.infrastructure**

| 클래스 | 규격 |
|---|---|
| `JpaSessionRepository` | `JpaRepository<Session, UUID>, SessionRepository`. 2)의 쿼리 |
| `SessionCleanupJob` | `@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")`에서 `jobRunner.run("session-cleanup", sessionCleanupService::deleteExpired)` |
| `RedisSessionStore`, `RedisTokenVersionProvider`, `resources/redis/rotate.lua` | 지운다 |

**4) member.application**

| 클래스 | 규격 |
|---|---|
| `TokenIssuer.issueNewSession` | `sessionId = Ids.newId()` → `RefreshToken.generate(sessionId)` → `sessionRepository.save(Session.open(sessionId, member.getId(), refreshToken.secretHash(), clock.instant(), properties.refreshTokenTtl()))` → `jwtProvider.issue(member.getId(), sessionId)`. 부르는 쪽에 트랜잭션이 있으면 그 안에서 저장된다 |
| `LoginService.login` | `@Transactional(readOnly = true)`를 `@Transactional`로 바꾼다. 세션 행을 저장하는데, 읽기 전용 트랜잭션 안에서는 Hibernate가 flush하지 않아 행이 남지 않는다. 가입·소셜 가입은 이미 `@Transactional`이고, 소셜 로그인(`OAuthLoginService.login`)은 트랜잭션이 없어 `save`가 자기 트랜잭션으로 저장한다 |
| `TokenIssuer.reissue`, `reissueAccessToken` | `jwtProvider.issue(member.getId(), sessionId)`로 바꾼다. `reissueAccess`는 `reissueAccessToken`으로 이름을 바꾼다 |
| `SessionVerificationService` | 5) 표의 `common.security.SessionVerifier`를 구현한다(지금의 `RedisTokenVersionProvider`가 `TokenVersionProvider`를 구현하는 것과 같은 모양). `Optional<AuthClaims> verify(UUID sessionId, UUID memberId)`. 필터가 요청마다 부르는 조회 한 번이라 트랜잭션을 열지 않는다. `findActive(sessionId, clock.instant())`가 비었거나 결과의 `memberId`가 다르면 빈 값, 아니면 `new AuthClaims(sessionId, memberId, role.name())` |
| `TokenRefreshService.refresh` | 메서드에 `@Transactional`을 달지 않는다. 한 트랜잭션 안에서 세션을 지우고 예외를 던지면 삭제까지 롤백되기 때문이다. `rotate`·`deleteBySessionId`가 저장소의 `@Transactional`로 각각 커밋되고, 예외는 그 뒤에 던진다. ① `RefreshToken.parse` ② `findById(sessionId)`가 없거나 `expiresAt`이 지났으면 `UNAUTHORIZED` ③ 새 `RefreshToken`으로 `rotate(sessionId, 보낸 해시, 새 해시, now + refreshTokenTtl)`가 0이면 `deleteBySessionId` 후 `REFRESH_REUSED` ④ 회원이 없으면 `deleteBySessionId` 후 `UNAUTHORIZED`, `verifyCanLogin`이 실패하면 `deleteBySessionId` 후 그 예외 ⑤ `tokenIssuer.reissue` |
| `LogoutService` | `@Transactional logout(memberId, sessionId, all)`: `all`이면 `deleteAllByMemberId(memberId)`, 아니면 `deleteBySessionId(sessionId)`. `MemberRepository`·`TokenVersionService` 의존을 뺀다 |
| `PasswordService.change` | `tokenVersionService.bump` + `sessionStore.deleteAllOfExcept`를 `deleteAllByMemberIdExcept(memberId, sessionId)`로. 응답은 지금처럼 `reissueAccessToken` |
| `PasswordService.confirmReset`, `AdminMemberService.ban`, `WithdrawService.withdraw` | `tokenVersionService.bump` + `sessionStore.deleteAllOf`를 `deleteAllByMemberId(memberId)`로 |
| `SessionCleanupService` | `@Transactional int deleteExpired()`: `sessionRepository.deleteExpired(clock.instant())` |
| `TokenVersionService` | 지운다 |

**5) common.security**

| 클래스 | 규격 |
|---|---|
| `AccessTokenClaims` | record `(UUID sessionId, UUID memberId)`. `JwtProvider.parse`의 결과 |
| `AuthClaims` | record `(UUID sessionId, UUID memberId, String role)`. `tokenVersion`을 뺀다. 역할은 DB에서 채운다 |
| `JwtProvider` | `issue(UUID memberId, UUID sessionId)`: 클레임 `sub`, `sid`만. `parse`는 `Optional<AccessTokenClaims>`, `sub`·`sid`가 없으면 빈 값 |
| `SessionVerifier` | `TokenVersionProvider`를 대신한다. `Optional<AuthClaims> verify(UUID sessionId, UUID memberId)` |
| `JwtAuthenticationFilter` | `jwtProvider.parse(token)` → `sessionVerifier.verify(claims.sessionId(), claims.memberId())` → 값이 있으면 지금처럼 `SecurityContext`(권한 `ROLE_` + 역할)와 MDC `memberId`를 채운다 |
| `SecurityConfig` | 필터 생성에 `SessionVerifier`를 넘긴다 |
| `TokenVersionProvider` | 지운다 |

**6) 설정** — `application.yaml`

`spring.task.scheduling.pool.size`를 11에서 12로(예약 작업 수와 맞춘다, [S2-3](../perf/s2-3-pool-scheduling.md)). `myroutine.jwt`(서명 키, access 15분, refresh 7일)와 `JWT_SECRET`은 그대로다.

### 완료 확인

케이스는 [로드맵 README 4.1](../03-roadmap/README.md)의 기준으로 골랐다.

테스트 보조: `TestFixtures`

- `sessionRowsOf(UUID memberId)` → `List<SessionRow>`(record `(UUID id, String refreshHash, Instant expiresAt)`, `created_at` 순). `jdbcTemplate`으로 `member.auth_session`을 읽는다.
- Redis 세션 헬퍼(`sessionSnapshotOf`, `sessionIdsOf`, `memberSessionsTtlOf`, `tokenVersionSnapshotOf`)와 `SessionSnapshot`, `TokenVersionSnapshot`을 지운다.

#### `member/web/SessionAuthTest` (`@AutoConfigureMockMvc`)

| 케이스 | 준비 | 동작 | 확인할 것 |
|---|---|---|---|
| 1. 로그인하면 DB에 refresh 해시로 세션을 만든다. | `signup("test@test.com")` | `loginTokens("test@test.com")` | `sessionRowsOf(memberId)`의 마지막 행: `id()` = `RefreshToken.parse(refreshToken).sessionId()`, `refreshHash()` = `RefreshToken.parse(refreshToken).secretHash()` |
| 2. 로그아웃하면 그 기기의 access가 바로 거부된다. (401) | `signup` → `loginTokens` | 그 access로 `POST /api/auth/logout`(204) → 같은 access로 `GET /api/members/me` | 상태 401, `$.code` = `UNAUTHORIZED` |
| 3. 세션이 만료되면 access가 유효해도 인증을 실패한다. (401) | `signup` → `loginTokens` → `jdbcTemplate.update("UPDATE member.auth_session SET expires_at = now() - interval '1 second' WHERE member_id = ?", memberId)` | 그 access로 `GET /api/members/me` | 상태 401 |
| 4. 회원이 ACTIVE가 아니면 세션이 남아 있어도 인증을 실패한다. (401) | `signup` → `loginTokens` → `changeStatus(memberId, "BANNED")`(세션은 그대로) | 그 access로 `GET /api/members/me` | 상태 401 |
| 5. 역할을 바꾸면 같은 access의 다음 요청부터 반영한다. | `signup` → `loginTokens` → `jdbcTemplate.update("UPDATE member.member SET role = 'ADMIN' WHERE id = ?", memberId)` | 같은 access로 `GET /admin/api/members` | 상태 200 |
| 6. refresh 재사용으로 세션을 지우면 그 세션의 access도 거부된다. (401) | `signup` → `loginTokens`(access1, refresh1) → `POST /api/auth/refresh` refresh1(200) → 다시 refresh1(401 `REFRESH_REUSED`) | access1로 `GET /api/members/me` | 상태 401 |
| 7. Redis가 응답하지 않아도 access로 인증한다. | `signup` → `loginTokens` | Redis 일시정지([Redis 장애 대응](redis-outage.md) 공통 "장애를 흉내 내는 방법") → `GET /api/members/me` | 상태 200 |
| 8. Redis가 응답하지 않아도 refresh로 갱신한다. | `signup` → `loginTokens` | Redis 일시정지 → `POST /api/auth/refresh` | 상태 200 |
| 9. Redis가 응답하지 않아도 로그아웃한다. | `signup` → `loginTokens` | Redis 일시정지 → `POST /api/auth/logout` | 상태 204 |

#### `member/application/SessionCleanupServiceTest`

| 케이스 | 준비 | 동작 | 확인할 것 |
|---|---|---|---|
| 10. 만료된 세션을 지운다. | `signup` → `loginTokens` 두 번(세션 둘) → 첫 세션의 `expires_at`을 `now() - interval '1 second'`로 | `sessionCleanupService.deleteExpired()` | `sessionRowsOf(memberId)`에 첫 세션 `id`가 없다 |
| 11. 만료되지 않은 세션은 남긴다. | 10번과 같다 | 10번과 같다 | `sessionRowsOf(memberId)`에 둘째 세션 `id`가 있다 |

### 기존 테스트

동작은 그대로이고 저장소만 바뀌는 테스트는 Redis 확인을 DB 확인으로 바꾼다.

| 테스트 | 바꿀 것 |
|---|---|
| `TokenRefreshTest` | `sessionSnapshotOf`·`sessionIdsOf`·`memberSessionsTtlOf`를 `sessionRowsOf`로. "Redis에 refresh 원문을 저장하지 않는다"는 새 1번과 겹쳐 지운다. "회원의 세션 목록 만료도 연장된다"는 세션 목록 키가 없어져 지운다. "같은 access로 로그아웃을 두 번 해도 204"는 지운다. 로그아웃한 access가 거부되는 것은 새 2번이 확인한다. 실패 케이스에서 `redisTemplate.keys("*")`가 그대로인지 보는 확인은 지운다(갱신이 Redis를 쓰지 않는다) |
| `TokenRevocationTest` | `tokenVersionSnapshotOf`와 `jwtProvider.parse(...).tokenVersion()` 확인을 지운다. 끊겼는지는 각 케이스의 401 요청이 확인한다. 제재 케이스의 세션 목록 확인(`sessionIdsOf`)은 `AdminMemberTest`가 제재 뒤 세션 삭제를 확인하므로 지운다. "전체 로그아웃이 아닌 로그아웃은 토큰 버전을 올리지 않는다"는 지운다(다른 기기의 세션이 남는 것은 `TokenRefreshTest` "로그아웃하면 현재 기기의 세션만 폐기된다"가 확인한다). 비밀번호 변경 케이스는 둘로 나눈다: "비밀번호를 변경하면 다른 기기의 access가 거부된다. (401)"(`accessTokenB`), "비밀번호를 변경한 기기는 응답의 새 access로 인증한다."(`newAccess` 200). 바꾼 기기는 세션이 남으므로 그 기기의 기존 access(`accessTokenA`)도 통과한다 |
| `RefreshConcurrencyTest` | `sessionSnapshotOf(sid).exists()`를 `sessionRowsOf`에 그 `sid`가 없는지로 |
| `TokenVersionServiceTest`, `RedisTokenVersionProviderTest` | 지운다 |
| `JwtProviderTest` | `issue(memberId, sessionId)`로 바꾸고, `role`·`tv` 클레임 케이스를 `sid` 없음 케이스로 |
| `MemberControllerTest` "만료된 토큰" | `jwtProvider.issue(memberId, Ids.newId())`로 |
| `OAuthLoginTest` | `jwtProvider.parse(accessToken).get().role()` 확인을 지운다(역할은 회원 행에서 읽는다) |
| `PasswordTest`, `AdminMemberTest`, `LoginLockTest` | Redis 세션 헬퍼를 `sessionRowsOf`로 |
| `SettlementControllerTest` "관리자가 정산을 수동 실행한다." | 역할을 바꾸기 전에 받은 `oldToken`이 403인지 보는 마지막 요청과 `oldToken` 준비를 지운다. 역할을 회원 행에서 읽으므로 그 access도 이제 통과한다(새 5번이 확인한다) |
| `common/error/RedisUnavailableTest` | 바꿀 것 없음 |

### 문서 반영

단계를 마칠 때 Claude가 고친다.

| 문서 | 고칠 곳 |
|---|---|
| [API 명세](../02-design/07-api-spec.md) | 1절 401 `UNAUTHORIZED` 설명에 "세션 없음·만료, 회원이 ACTIVE 아님" |
| [ERD](../02-design/03-erd.md) | `auth_session` 추가, `member.token_version`은 쓰지 않음(다음 배포에서 삭제) |
| [시퀀스](../02-design/06-sequences.md) | 9절 "인증: 토큰 갱신 · 즉시 무효화" |
| [요구사항](../01-requirements/README.md) | FR-MEM-04를 세션 삭제로 |
| [로드맵 4-1·4-4](../03-roadmap/part4-auth.md) | 4-1(Redis 세션)·4-4(tokenVersion)의 머리에 "SUB-25가 세션을 DB로 옮겼다" 한 줄 |
| [로드맵 3-4b](../03-roadmap/part3-events.md) | 완료 확인 7번 "역할을 바꾸기 전에 받은 토큰은 거부"에 "SUB-25부터는 역할을 회원 행에서 읽어 통과한다" 한 줄 |
| [Redis 장애 대응](redis-outage.md) | 공통 "지금 Redis를 쓰는 곳"에서 세션·토큰 버전 키 |
| [알려진 문제](../known-issues.md) | 세션·토큰 버전 행을 지운다. 3.4에 "쓰지 않는 컬럼 `member.token_version`"(확인됨, 다음 배포에서 삭제)을 더하고, 컬럼을 지우면 그 행도 지운다 |
| [서브 SUB-8](log-coverage.md) | "JWT 거부 사유"에 세션 없음·만료·회원 상태를 더하고 토큰 버전 불일치를 뺀다 |

### 머지한 뒤

- 이 단계를 배포해 이전 앱으로 돌아갈 일이 없다고 확인되면, 다음 배포에 `V{yyyyMMddHHmm}__member_drop_token_version.sql`(`ALTER TABLE member.member DROP COLUMN token_version;`)을 따로 넣는다. 운영 DB의 컬럼을 지우는 변경이라 배포 전에 한 번 더 확인한다. 값은 버전 카운터뿐이라 잃어도 되는 데이터다.
- [SUB-16](stage2-leftovers.md)의 S-04로 이 단계 앞(Redis 토큰 버전)과 뒤(DB 세션)의 인증 비용을 재고, 그 결과로 세션 앞에 Redis 캐시를 둘지 정한다(같은 문서 "S-04와 세션 캐시").

**리뷰 때 물어볼 것**

- access는 회전하지 않고 refresh만 회전하는 이유는? access까지 회전하면 무엇을 더 저장해야 하나?
- `TokenRefreshService.refresh`에 트랜잭션을 걸면 재사용 탐지가 어떻게 깨지나?
- 역할을 access 클레임이 아니라 회원 행에서 읽는 이유는? 제재된 회원의 세션을 지우는데도 상태를 확인하는 이유는?
- 한 기기 로그아웃 뒤 그 기기의 access는 지금(바꾸기 전)과 어떻게 다르게 처리되나?
