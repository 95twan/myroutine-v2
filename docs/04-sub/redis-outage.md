# 서브: Redis 장애 대응

> 버전 0.3 · 2026-11-22 · 세션을 DB로 옮기는 [SUB-25](session-auth.md)로 로그인한 요청·로그아웃·비밀번호 변경·제재·탈퇴·연결된 소셜 로그인이 Redis를 쓰지 않게 되어, 장애 중 인증 확인·세션의 토큰 버전·장애 중 진행 단계를 지웠다. 남는 값이 임시 값뿐이라 AOF를 켜지 않고 RDB 스냅샷을 그대로 두며, AOF 켜기 단계를 지웠다
> 0.2 · 2026-11-21 · Redis 장애 중 이메일 로그인은 503으로 막고 로그인 실패 횟수는 Redis에 둔다. 실패 횟수를 DB로 옮기는 단계를 빼고 뒤 단계 번호를 당겼다(SUB-24~SUB-28). 타임아웃 뒤에도 Redis가 명령을 실행할 수 있어 세션 생성을 Lua 하나로 묶는다(SUB-26). Redis 저장을 끄지 않고 AOF(1초마다 반영)를 켠다(SUB-28)
> 0.1 · 2026-11-20 · Redis를 쓸 수 없을 때 로그인한 요청·로그인·로그아웃·비밀번호 변경·제재·탈퇴는 진행하고, 가입·토큰 갱신·비밀번호 재설정은 503으로 막는다. 로그인 실패 횟수를 DB로 옮기고, 세션에 토큰 버전을 넣어 갱신 때 DB와 비교하며, 세션 삭제를 커밋 뒤로 옮기고, Redis 저장을 끈다(단계 ID SUB-24~SUB-29)

> **서브**: 로드맵에서 뺀 기능이다. Stage 1(Part 6) 뒤에 한다.
> **따르는 규칙**: 로드맵 Part 1~6의 공통 규칙. 인증 규격은 [서브 세션을 DB로](session-auth.md)와 [ADR-015](../adr/ADR-015-session-auth.md)를 따른다.

---

## 공통

### 지금 Redis를 쓰는 곳

Redis는 member 모듈만 쓴다. 주문·결제·재고·정산·알림·검색·예약 작업·Kafka consumer는 쓰지 않는다.

| 키 | 값의 원본 | 쓰는 곳 |
|---|---|---|
| `auth:login-fail:{email}` | Redis에만 있음 | `RedisLoginAttemptStore`: 15분 안에 5번 틀리면 15분 잠금 |
| `auth:email-code:*`, `auth:email-cooldown:{email}`, `auth:email-hourly:{email}` | Redis에만 있음 | `RedisEmailCodeStore`: 가입 인증·비밀번호 재설정 코드, 발송 간격(60초)·시간당 횟수(5번). 간격·횟수는 목적과 상관없이 이메일 하나에 같이 쌓인다 |
| `auth:signup-verified:*`, `auth:oauth-signup:*` | Redis에만 있음 | `RedisSignupVerificationStore`, `RedisSignupTokenStore`: 가입 인증 완료 표시, 소셜 가입 토큰 |

지금은 Redis가 응답하지 않으면 Redis를 부르는 요청이 Lettuce 기본 타임아웃(60초) 뒤 실패한다. 서비스 안의 실패는 `@Transactional`이 DB 변경을 롤백하고 500 `INTERNAL_ERROR`로 답한다. JWT 필터 안의 실패는 `GlobalExceptionHandler` 밖이라 에러 형식이 다르다. `/actuator/health`에는 Redis 지표가 들어가 있어 DOWN이 된다.

### 정책

| 기능 | Redis를 쓸 수 없을 때 | 단계 |
|---|---|---|
| 토큰 없는 공개 조회, 예약 작업, Kafka consumer | 지금도 진행한다 | - |
| 로그인한 요청, 토큰 갱신, 로그아웃, 비밀번호 변경, 제재, 탈퇴, 이미 연결된 회원의 소셜 로그인 | 진행. 세션이 DB에 있어 Redis를 쓰지 않는다 | SUB-25 |
| 이메일 로그인 | 503. 비밀번호를 확인하기 전에 세는 실패 횟수가 Redis에만 있다 | SUB-24 |
| 가입 인증 코드 발송·확인, 이메일 가입, 처음 오는 회원의 소셜 로그인, 소셜 가입 | 503. 코드·인증 완료 표시·가입 토큰이 Redis에만 있다 | SUB-24 |
| 비밀번호 재설정 요청·확인 | 503. 코드·발송 간격·횟수가 Redis에만 있다 | SUB-24 |
| `/actuator/health` | UP. Redis 지표를 뺀다 | SUB-24 |

### Redis 영속성

운영 Redis는 설정 파일 없이 떠서 기본 RDB 스냅샷(`save 3600 1 300 100 60 10000`)을 쓰고, 이 설정을 그대로 둔다. 정상 종료(`docker restart`, VM 정상 재부팅)는 종료할 때 스냅샷을 저장해 잃지 않는다. 비정상 종료(OOM kill, 강제 종료, 전원 차단)면 마지막 스냅샷(바뀐 건수가 적으면 최대 1시간 전)으로 돌아간다.

SUB-25 뒤 Redis에는 로그인 실패 횟수·잠금(15분), 이메일 인증 코드와 발송 간격·횟수(5분~1시간), 가입 인증 표시·소셜 가입 토큰(10~30분)만 남는다. 옛 스냅샷으로 돌아가면 잠금이 풀리거나 횟수가 줄고, 만료 전이면 이미 쓴 인증 코드가 다시 통하며, 가입 중인 사용자는 인증을 다시 한다.

| 방법 | 고르지 않은 이유 |
|---|---|
| AOF를 켠다(1초마다 반영) | 잃는 범위가 약 1초로 줄지만, 남는 값은 모두 1시간 안에 사라지는 임시 값이라 잃어도 코드를 다시 받거나 잠금이 한 번 풀리는 정도다. 처음 켤 때 돌고 있는 Redis에서 먼저 켜야 데이터가 남는 순서를 지켜야 하고, 쓰기마다 AOF 파일에 덧붙인다 |
| 저장을 끈다 | 정상 재시작에도 잠금과 가입 중인 인증이 모두 사라진다 |

### 로그인 잠금

로그인 실패 횟수(15분 안에 5번 틀리면 15분 잠금)는 막아 주는 값이라, 셀 수 없으면 건너뛰지 않고 이메일 로그인을 막는다. 실패 횟수는 Redis에 그대로 둔다.

| 방법 | 고르지 않은 이유 |
|---|---|
| 실패 횟수를 DB로 옮긴다 | 15분만 사는 임시 값을 위해 로그인마다 DB 커밋이 1~2번(시도마다 늘리는 upsert, 성공 뒤 DELETE) 늘고, Redis TTL이 하던 만료를 정리 작업으로 바꿔야 한다. 공격이 몰리면 쓰기가 DB로 간다 |
| 장애 때만 DB나 앱 메모리에서 센다 | 두 저장소의 횟수가 이어지지 않아 장애에 들어가고 나올 때마다 한도가 새로 시작되고, 장애 전의 잠금이 풀린다. 장애 때만 도는 경로라 평소에 검증되지 않는다 |
| 장애 때 잠금을 건너뛴다 | 장애 동안 비밀번호를 무제한으로 시도할 수 있다 |

장애 동안 이메일 로그인은 503이다. 이미 로그인한 사용자는 세션이 DB에 있어 계속 쓰고, 연결된 소셜 로그인도 된다(SUB-25). Redis는 `restart: unless-stopped`라 프로세스가 죽으면 혼자 다시 뜬다.

### 단계 순서

SUB-24 → [SUB-25](session-auth.md) 순서로 한다.

### "Redis를 쓸 수 없음"의 예외

Spring Data Redis는 Lettuce 예외를 Spring 예외로 바꿔 던진다(`spring-data-redis` 4.1.1 `LettuceExceptionConverter`).

| Lettuce 예외 | Spring 예외 |
|---|---|
| 연결 실패(`RedisConnectionException`, `ChannelException`) | `org.springframework.data.redis.RedisConnectionFailureException` |
| 명령 타임아웃(`RedisCommandTimeoutException`) | `org.springframework.dao.QueryTimeoutException` |
| 그 밖의 Redis 오류(스크립트 오류 등) | `org.springframework.data.redis.RedisSystemException` |

Redis 프로세스가 꺼져 연결이 거절되어도 명령은 바로 실패하지 않는다. Lettuce 기본 설정(`ClientOptions`의 `DisconnectedBehavior.DEFAULT`, 자동 재연결 켬)은 끊긴 동안 명령을 받아 두고 재연결을 기다리므로(`lettuce-core` 7.5.2 `DefaultEndpoint.isRejectCommand`), 명령 타임아웃 뒤 `QueryTimeoutException`으로 실패한다. 응답하지 않는 Redis(일시정지)도 같다. 타임아웃 1초로 두고 로컬에서 확인한 결과는 아래와 같다.

| 상태 | 결과 |
|---|---|
| 일시정지(`docker pause`) | `QueryTimeoutException`, 약 1,000ms |
| `unpause` 뒤 | 같은 연결로 바로 성공 |
| 컨테이너 정지(`docker stop`) | `QueryTimeoutException`, 약 1,000ms |

이 문서에서 "Redis를 쓸 수 없음"은 앞의 둘이다. `RedisSystemException`은 코드나 스크립트의 결함일 수 있어 500으로 둔다. `QueryTimeoutException`은 JDBC 쿼리 타임아웃도 같은 타입이지만, 이 앱은 DB 쿼리 타임아웃을 설정하지 않아 지금은 Redis에서만 나온다.

### 타임아웃은 명령을 취소하지 않는다

명령 타임아웃은 앱이 응답을 기다리지 않겠다는 뜻이고, 이미 보낸 명령을 거두지 않는다. Redis가 응답하지 않는 동안(일시정지, 매우 느림) 연결은 살아 있어서, 보낸 명령은 Redis 서버의 운영체제가 소켓 수신 버퍼에 받아 둔다. Redis가 다시 돌면 그 버퍼를 읽어 차례로 실행한다. Lettuce는 타임아웃이 나도 연결을 끊지 않는다. 로컬에서 `redis:7.4.11`을 일시정지한 채 연결을 열어 두고 `HSET`을 보낸 뒤 2초 뒤 풀자, 키가 만들어졌고 TTL은 -1(만료 없음)이었다. Redis가 그 버퍼를 읽기 전에 꺼지면 명령은 실행되지 않고 사라진다(일시정지한 채 `docker kill` 하고 다시 띄우자 키가 없었고 AOF에도 남지 않았다). Redis 프로세스가 꺼진 동안 Lettuce가 받아 둔 명령을 타임아웃 뒤 재연결할 때 보내는지는 확인하지 못했다.

그래서 타임아웃으로 실패한 요청의 Redis 쓰기가 나중에 반영될 수 있다.

| 명령 | 나중에 실행되면 |
|---|---|
| 실패 횟수 기록(`login-attempt.lua`) | 비밀번호를 확인하지 않은 시도가 1 늘어난다. 거부 쪽이라 받아들인다 |
| 발송 간격·횟수(`INCR` 뒤 따로 `EXPIRE`) | 만료 없는 카운터가 남는다([알려진 문제](../known-issues.md) "이메일 발송 횟수 키의 만료 누락") |

세션 생성·갱신의 해시 교체·세션 삭제·토큰 버전 쓰기는 SUB-25 뒤 Redis에 없다.

### 장애를 흉내 내는 방법

테스트는 공유 Redis 컨테이너(`IntegrationTestSupport.redis`)를 일시정지(`docker pause`)해 Redis가 응답하지 않는 장애를 만든다. 명령이 SUB-24의 타임아웃(1초) 뒤 `QueryTimeoutException`으로 실패하고, `unpause`하면 같은 연결로 다시 동작한다(위 확인 결과). 타임아웃 설정까지 실제 경로로 지난다. `SearchUnavailableTest`가 ES를 멈추는 방법과 같다. 컨테이너를 멈추고(`stop`) 다시 띄우면 포트가 바뀌어 다른 테스트가 깨지므로 쓰지 않는다.

```java
redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();
try {
    // 동작
} finally {
    redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
}
```

`TestFixtures`의 Redis 헬퍼(`redisStringOf`, `sessionSnapshotOf` 등)와 `@AfterEach`의 `resetRedis`도 같은 Redis를 쓰므로, Redis 값 확인은 `unpause` 뒤에 한다. 일시정지 중에는 Redis를 부를 때마다 1초가 걸린다.

---

## SUB-24. Redis 타임아웃, 503 응답, 헬스체크 (S)

**목표**: Redis가 응답하지 않을 때 요청이 1초 안에 끝나게 하고, Redis 때문에 막는 기능은 503으로 답하고, 헬스체크가 Redis 때문에 DOWN이 되지 않게 한다. 막는 기능(공통 "정책"의 503 행)은 이 단계로 끝난다.

### 빈 곳

- `application.yaml`에 `spring.data.redis.timeout`이 없다. Spring Boot는 이 값이 있을 때만 Lettuce에 명령 타임아웃을 넘기므로(`spring-boot-data-redis` 4.1.1 `LettuceConnectionConfiguration` 173행) Lettuce 기본값 60초(`lettuce-core` 7.5.2 `RedisURI.DEFAULT_TIMEOUT`)가 적용된다. 요청마다 Tomcat 스레드를 60초씩 쥔다. Redis 프로세스가 꺼진 경우도 Lettuce가 명령을 받아 두고 재연결을 기다리므로 같다. 연결 타임아웃(`connect-timeout`)도 없어 Lettuce 기본값 10초다.
- Redis를 쓸 수 없어 실패한 요청이 500 `INTERNAL_ERROR`로 나간다. 검색은 ES 장애를 503 `SEARCH_UNAVAILABLE`로 답하는데 Redis에는 이런 구분이 없다.
- Redis 헬스 지표(`DataRedisHealthContributorAutoConfiguration`, `@ConditionalOnEnabledHealthIndicator("redis")`)가 켜져 있어 Redis가 멈추면 `/actuator/health`가 DOWN이 된다. 운영 compose의 app `healthcheck`, `deploy.sh`의 `up --wait`, `smoke.sh`가 이 경로를 본다. ES 지표는 같은 이유로 이미 꺼 두었다(`management.health.elasticsearch.enabled: false`).

### 할 일

**1) 설정** — `application.yaml`

```yaml
spring:
  data:
    redis:
      timeout: 1s
      connect-timeout: 1s

management:
  health:
    redis:
      enabled: false
```

이 앱의 Redis 명령은 키 하나를 읽고 쓰거나 짧은 Lua 스크립트라 1초 안에 끝나지 않으면 장애로 본다. 연결도 같은 VM의 docker 네트워크라 1초로 둔다. Redis 상태는 Redis 컨테이너의 자체 `healthcheck`(`redis-cli ping`)와 앱의 경고 로그로 본다.

**2) common.error**

| 클래스 | 규격 |
|---|---|
| `CommonErrorCode` | `SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.")` |
| `GlobalExceptionHandler` | `@ExceptionHandler({RedisConnectionFailureException.class, QueryTimeoutException.class})` 메서드를 더해 503 `SERVICE_UNAVAILABLE`로 답한다. 로그는 `handleException`과 같은 형식으로 예외와 함께 남긴다. `RedisSystemException`은 지금처럼 `Exception` 처리(500)로 둔다 |

막는 기능은 이 매핑만으로 503이 된다. 서비스 코드는 바꾸지 않는다.

### 완료 확인

케이스는 [로드맵 README 4.1](../03-roadmap/README.md)의 기준으로 골랐다.

#### `common/error/RedisUnavailableTest` (`@AutoConfigureMockMvc`)

| 케이스 | 준비 | 동작 | 확인할 것 |
|---|---|---|---|
| 1. Redis가 응답하지 않으면 가입 인증 코드 발송을 실패한다. (503) | 없음 | Redis 일시정지 → `POST /api/auth/signup/email-verifications` 본문 `{"email":"test@test.com"}` | 상태 503, `$.code` = `SERVICE_UNAVAILABLE` |
| 2. Redis가 응답하지 않으면 타임아웃 뒤 바로 실패한다. | 없음 | Redis 일시정지 → 1번과 같은 요청, 앞뒤 시각을 잰다 | 걸린 시간 5초 미만 |
| 3. Redis가 응답하지 않아도 헬스체크는 UP이다. | 없음 | Redis 일시정지 → `healthEndpoint.health()` | `getStatus()` = `Status.UP` |
| 4. Redis가 응답하지 않으면 이메일 로그인을 실패한다. (503) | `testFixtures.signup("test@test.com")` | Redis 일시정지 → `POST /api/auth/login` 본문 `{"email":"test@test.com","password":"pass1234"}` | 상태 503, `$.code` = `SERVICE_UNAVAILABLE` |

### 문서 반영

단계를 마칠 때 Claude가 고친다.

| 문서 | 고칠 곳 |
|---|---|
| [API 명세](../02-design/07-api-spec.md) | 1절 에러 표에 503 `SERVICE_UNAVAILABLE` |
| [로드맵 4-4](../03-roadmap/part4-auth.md) | "Redis 장애 시 특별한 처리를 하지 않는다" 줄을 이 문서 링크로 |
| [알려진 문제](../known-issues.md) | "Redis 명령 타임아웃 60초", "Redis 장애 응답이 500", "Redis 장애 시 헬스체크 DOWN" 행을 지운다 |

**리뷰 때 물어볼 것**

- 타임아웃을 1초로 둔 근거는? 너무 짧으면 무엇이 생기나?
- `RedisSystemException`은 왜 503에 넣지 않았나?
- 헬스체크에서 Redis를 빼면 Redis 장애는 어디서 알아차리나?
