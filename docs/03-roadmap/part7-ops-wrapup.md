# Part 7. 관측 · 운영 · 마무리

> 버전 0.31 · 2026-10-28 · 7-1 `LogSafetyTest`의 가입을 4-2의 가입 전 인증(발송 → 확인 → 인증 토큰으로 가입)으로 바꾸고 인증 토큰도 로그에 없어야 할 값에 더했다
> 0.30 · 2026-10-27
> 0.26 · 2026-10-23 · 7-3 구현물 2번 `reconcileOne`: 이미 있는 `private boolean reconcileOne`을 `public OrderStatus`로 열고 반환을 바꾼다
> 0.22 · 2026-10-22 · 7-1 MDC 표를 3-2 결정에 맞췄다: `InboxGuard`가 `EventContext.run` 안에서 실행되므로 `traceId`를 빼는 곳을 `EventContext`로 했다
> 0.11 · 2026-10-16 · 7-3 `reconcileOne`을 2-6 변경에 맞춰, 확정된 결제는 `applyPaymentState`로 바로 처리하는 지금의 `reconcileBatch` 처리와 같게 했다
> 0.9 · 2026-10-15 · 7-3 `reconcile`의 `{id}`는 `orderId`, `reconcileOne`은 `OrderStatus`를 반환한다. 7-4 `InvariantChecker`에 INV-08을 더하고 INV-09는 최종적 일관성이라 제외했다
> 0.8 · 2026-10-15 · 7-3 환불 재시도의 PG 멱등 키: `retry`가 `FAILED → APPROVED`와 함께 `pgAttempts`를 올려 `refundId-r{n}` 키로 PG까지 가게 했다(2-8 `Refund.pgIdempotencyKey()`)
> 0.5 · 2026-10-09 · logstash-logback-encoder `9.0`(Jackson 3·Logback 1.5 기반)과 ELK·Prometheus·Grafana 이미지 태그를 확인해 고정했다. Spring Boot가 Kafka 클라이언트 지표를 자동 등록한다. 1-11 운영 VM에서는 Prometheus가 `app:8081`을 긁는다
> 0.4 · 2026-10-02 · 예치금 제거: 원장 점검을 재고 대조 점검으로 바꾸고, E2E 시나리오에서 예치금을 삭제했다

> **끝나면**: 로그(Kibana)와 메트릭(Grafana)으로 시스템을 보고, 정합성 문제가 생기면 알림이 오고, 운영 API로 처리할 수 있다. 전체 시나리오가 E2E 테스트로 증명되고, Stage 1이 `v1.0.0`으로 릴리스된다.
> **인프라 추가**: **ELK**(Logstash, Kibana — ES는 Part 6 것을 같이 씀), **Prometheus, Grafana**

| 단계 | 제목 | 크기 |
|---|---|---|
| 7-1 | ELK: 로그 수집과 traceId 검색 | M |
| 7-2 | Prometheus · Grafana: 메트릭 · 대시보드 · 알림 | L |
| 7-3 | 운영 API | M |
| 7-4 | E2E 시나리오 · 불변식 점검 | L |
| 7-5 | README · 회고 · Stage 1 릴리스 | M |

---

## Part 7 공통 규칙

- 관측 스택(Logstash, Kibana, Prometheus, Grafana)은 docker-compose **`observability` 프로필**로 띄운다: `docker compose --profile observability up -d`. 평소 개발에는 띄우지 않는다(메모리).
- 설정 파일은 모두 리포에 둔다: `docker/logstash/`, `docker/kibana/`, `docker/prometheus/`, `docker/grafana/provisioning/`. 화면에서 만든 대시보드·검색은 **JSON으로 내보내 커밋**한다.
- 7-3의 운영 API는 모두 `/admin/api/**`(ADMIN)이다. 실행 로그 형식은 7-3 "모든 API에 공통"에 적었다.

---

## 7-1. ELK: 로그 수집과 traceId 검색 (M)

**목표**: 앱 로그를 Kibana에서 검색한다. traceId 하나로 HTTP 요청 → DB → Kafka 소비 → 알림까지 한 흐름이 보인다.

**왜 지금**: 기능이 다 모였고, 흐름이 길어져서(결제 → 이벤트 → 정산 → 알림) 로그 파일로는 따라가기 어렵다. Stage 2 성능 분석도 이것이 전제다.

**새로 등장**: Logstash(로그 수신·가공) → Elasticsearch(저장) → Kibana(검색·화면), logstash-logback-encoder(로그를 JSON으로)

**정책 (이 단계에서 정함)**
- 콘솔 로그는 지금처럼 사람이 읽는 형식, Logstash로 보내는 로그는 JSON. Logstash 전송은 Spring 프로필 `elk`일 때만(로컬에서 `local,elk`로 실행).
- 로그 인덱스: `logs-myroutine-YYYY.MM.dd`. 보관 기간은 로컬이라 정하지 않는다.

### 할 일

**구현 순서**: 1) 의존성·`logback-spring.xml` → 2) MDC(`memberId` → `orderId`) → 3) `docker/logstash`·compose·Kibana 저장 검색 → 4) 민감정보 점검(`LogSafetyTest`).

**1) 의존성·설정** — `build.gradle`, `src/main/resources/logback-spring.xml`
- `implementation 'net.logstash.logback:logstash-logback-encoder:9.0'` — 2026-10-09 Maven Central 최신. 9.0의 pom은 `tools.jackson.core:jackson-databind`(Jackson 3)와 `logback-core` 1.5.x에 의존해 Boot 4.1.1(Jackson 3.1.5, Logback 1.5.38)과 맞는다. Boot BOM이 관리하지 않아 버전을 적는다
- `src/main/resources/logback-spring.xml`: `<springProfile name="elk">`에 `LogstashTcpSocketAppender`(destination `${LOGSTASH_HOST:-localhost}:5000`, `LogstashEncoder`), 그 외에는 Boot 기본 콘솔. 콘솔 패턴은 `application.yaml`의 `logging.pattern.level: "%5p [%X{traceId:-}]"`를 그대로 둔다
  - **왜 이 파일인가**: logback 설정은 `logback.xml`로도 쓸 수 있지만 `<springProfile>`(프로필에 따라 설정을 나누는 태그)은 Boot가 읽는 `logback-spring.xml`에서만 된다. 이 파일을 두면 Boot의 로깅 자동 설정을 대신하므로, 기본 콘솔 설정을 직접 불러와야 한다.
  - **구성(위에서 아래로)**
    1. `<include resource="org/springframework/boot/logging/logback/defaults.xml"/>` — Boot의 기본 콘솔 패턴 변수(`CONSOLE_LOG_PATTERN`)를 정의한다. 이 패턴이 위 `logging.pattern.level`을 쓴다
    2. `<include resource="org/springframework/boot/logging/logback/console-appender.xml"/>` — `CONSOLE` appender를 만든다(두 파일 모두 Boot 4.1.1 jar에서 확인). `base.xml`은 파일 appender까지 붙이므로 쓰지 않는다
    3. `<springProfile name="elk">` 안에 `LogstashTcpSocketAppender`(`net.logstash.logback.appender`) 하나를 선언하고 `<destination>`, `<encoder class="net.logstash.logback.encoder.LogstashEncoder">`를 넣는다
    4. `<root level="INFO">`에 `CONSOLE`은 항상, 위 appender는 `elk` 프로필 블록 안에서만 `appender-ref`로 붙인다
  - Logstash가 꺼져 있어도 앱은 기동한다. 그동안의 로그는 전송되지 않을 수 있다(확인 필요: 재접속 주기·버퍼 동작은 라이브러리 README로 확인)
- JSON 필드: `@timestamp`, `level`, `logger_name`, `message`, `stack_trace`, MDC 전부(`traceId`, `memberId`, `orderId`, `job`, `eventId`), `app: myroutine`
  - `LogstashEncoder`는 위 필드 중 `app`을 뺀 나머지와 MDC를 기본으로 JSON 한 줄에 담는다(확인 필요: 기본 필드 이름은 logstash-logback-encoder 9.0 README로 확인). 직접 정하는 것은 `app`뿐이고, `LogstashEncoder`의 `<customFields>{"app":"myroutine"}</customFields>`로 넣는다

**2) MDC 보강** — `common.web`·`common.security`·`common.job`·`common.outbox`·`order.application`

MDC는 SLF4J가 스레드마다 들고 있는 `Map<String, String>`이다. 인코더가 로그 한 줄을 쓸 때 현재 스레드의 MDC를 필드로 붙이므로, 값을 넣어 두면 그 사이에 찍힌 모든 로그에 따라붙는다. 값은 문자열만 되고(UUID는 `.toString()`), **스레드를 넘어가지 않으며**(Kafka listener·스케줄러 스레드는 따로 넣어야 한다), 안 지우면 스레드 풀의 다음 작업에 남는다. 그래서 넣는 곳마다 빼는 곳이 짝으로 있다. `traceId`는 1-3(`TraceIdFilter`)·2-5(`JobRunner`)·3-1·3-2(`EventContext`, `InboxGuard`가 이를 부른다)에서 이미 넣고 있고, 이 단계에서 새로 넣는 것은 `memberId`와 `orderId`다.

| 키 | 넣는 곳 | 빼는 곳 |
|---|---|---|
| `traceId` | `common.web.TraceIdFilter`, `common.job.JobRunner`, `common.outbox.EventContext` (`common.inbox.InboxGuard`가 안에서 부른다, 이미 있음) | `common.outbox.EventContext`의 finally |
| `memberId` | `common.security.JwtAuthenticationFilter` 인증 성공 직후 | `common.web.TraceIdFilter`의 finally에서 함께 제거 |
| `orderId` | 주문 흐름 서비스(`order.application`의 `ConfirmPaymentService`, `OrderPaymentApplier`, `CancelOrderLineService`, 만료·대사 잡의 주문별 처리)가 `MDC.putCloseable`로 넣는다(아래 `orderId`) | try-with-resources가 제거 |
| `job` | `common.job.JobRunner` (이미 있음) | |

- **`memberId`**: `JwtAuthenticationFilter.doFilterInternal`에서 `jwtProvider.parse(token).ifPresent(claims -> ...)` 람다 안, `SecurityContextHolder`에 인증을 넣는 줄 바로 뒤에 `MDC.put("memberId", claims.memberId().toString())`을 더한다(`AuthClaims`는 `record (UUID memberId, String role)`). 빼는 곳은 `TraceIdFilter.doFilterInternal`의 `finally`에 `MDC.remove("memberId")` 한 줄 추가 — `TraceIdFilter`가 `HIGHEST_PRECEDENCE`라 보안 필터보다 바깥에서 요청 전체를 감싸므로 거기서 지우면 된다. 토큰이 없거나 틀린 요청은 이 줄을 지나지 않으니 `memberId`가 비어 있다(익명 요청)
- **`orderId`**: `try (MDC.MDCCloseable ignored = MDC.putCloseable("orderId", orderId.toString())) { ... }`. `putCloseable`은 값을 넣고, try 블록이 끝나면(예외 포함) `close()`가 **그 키를 지운다**. 주문을 처리하는 메서드 본문 전체를 이 블록으로 감싼다. 건별 반복(만료·대사 잡의 `for (UUID id : ids)`)은 주문마다 블록을 열고 닫아야 다음 주문 로그에 앞 주문 ID가 남지 않는다
  - 주의: `close()`는 **이전 값을 복원하지 않고 지우기만 한다**. `ConfirmPaymentService`가 연 블록 안에서 `OrderPaymentApplier`가 같은 키로 다시 열면, 안쪽이 닫히는 순간 바깥 구간의 `orderId`도 사라진다. 어느 쪽을 열지(가장 바깥 한 곳만 열기, 이미 같은 값이면 열지 않기 등)는 문서에 없다(확인 필요)

**3) docker** — `docker/logstash/pipeline/logstash.conf`, `docker/docker-compose.yml`, `docker/kibana/saved-objects.ndjson`
- `docker/logstash/pipeline/logstash.conf`: `input { tcp { port => 5000 codec => json_lines } }` → `output { elasticsearch { hosts => ["http://elasticsearch:9200"] index => "logs-myroutine-%{+YYYY.MM.dd}" } }`
  - 흐름: 앱의 appender가 로그 1건을 JSON 한 줄로 TCP 5000에 보낸다 → `json_lines` 코덱이 줄 단위로 JSON을 이벤트로 풀어 필드로 만든다 → `elasticsearch` output이 색인 이름 날짜를 이벤트의 `@timestamp`(UTC)로 채워 저장한다. 그래서 한국 시간 새벽 로그는 전날 날짜 색인에 들어간다
  - `http://elasticsearch:9200`은 인증 없는 접속이다. 6-2가 ES를 `xpack.security.enabled=false`로 띄우기 때문에 이대로 된다. 컨테이너끼리는 compose 서비스 이름(`elasticsearch`)으로 접속한다
- compose: `docker.elastic.co/logstash/logstash:9.4.8`(5000), `docker.elastic.co/kibana/kibana:9.4.8`(5601) — ES(6-2)와 같은 버전
  - Logstash는 `./docker/logstash/pipeline`을 컨테이너의 파이프라인 디렉터리로 마운트한다(확인 필요: 마운트 경로는 `logstash:9.4.8` 이미지 문서로 확인). 5000은 호스트에 publish해야 호스트에서 도는 앱의 `localhost:5000`이 닿는다. Kibana는 환경변수로 ES 주소(`ELASTICSEARCH_HOSTS=http://elasticsearch:9200`)를 준다(확인 필요: 변수 이름)
- Kibana: 데이터 뷰 `logs-myroutine-*`, 저장 검색 3개 — "traceId로 흐름 보기"(`traceId : "..."`, 시간 오름차순, 열 level·logger·message·job), "ERROR만", "결제 UNKNOWN"(`message : "UNKNOWN" and logger_name : *payment*`) → `docker/kibana/saved-objects.ndjson`로 내보내기
  - 쿼리는 Kibana 검색창의 KQL이고 `traceId`·`logger_name`·`message`·`job`은 위 JSON 필드 이름이다. "ERROR만"은 `level : "ERROR"`. 내보내기는 Kibana 화면의 저장된 객체(Saved Objects) 관리에서 하고 `.ndjson`(줄마다 JSON 한 개)으로 받는다(확인 필요: 9.4 화면의 메뉴 위치)

**4) 민감정보 점검** — 테스트 `common/LogSafetyTest`(완료 확인): 아래 테스트로 확인하고, 걸리면 로그 문장을 고친다
- 점검 대상은 **로그 문장(`log.info("...", 값)`의 인자와 예외 메시지)**이다. 토큰·비밀번호·인증코드·빌링키·`authKey`·`paymentKey`가 인자로 들어간 곳, 요청·응답 바디를 통째로 찍는 곳이 후보다. 고치는 방법은 값을 빼거나 앞 몇 글자만 남기는 것이다
- 테스트 방식: `@ExtendWith(OutputCaptureExtension.class)`를 달면 메서드 파라미터로 `CapturedOutput output`을 받는다. 콘솔 출력을 보므로 JSON 인코더와 무관하게 같은 로그 문장을 검사한다. 값과 단언은 완료 확인에 적었다

### 완료 확인

**확인 값을 읽는 방법**
- 테스트의 로그: `CapturedOutput output`의 `output.getAll()`(그때까지 콘솔에 찍힌 전체 문자열).
- 응답: MockMvc `jsonPath`. 요청의 traceId는 응답 헤더 `X-Request-Id`(`TraceIds.HEADER`).
- DB: JDBC. 가짜 PG가 받은 요청: `pg.requests(경로 접두어)`.
- Kibana(수동): Discover의 데이터 뷰 `logs-myroutine-*`. 한 행의 JSON 필드 이름은 이 단계 "JSON 필드"와 같다.

#### `common/LogSafetyTest` (`@ExtendWith(OutputCaptureExtension.class)` + `@AutoConfigureMockMvc` + `IntegrationTestSupport`)

**준비**: 판매자 1명, 가게 1개, 상품 1개(`registerProduct(sellerId, shopId, 10000, 10)`). 구매자는 아래 1단계 API로 가입한다. 아래 순서로 요청을 보내면서 **비밀값을 변수에 모은다**(응답에서 읽은 값은 `jsonPath` 결과를 `JsonPath.read`로 꺼낸다).

| 순서 | 요청 | 모아 둘 값 |
|---|---|---|
| 1 | `POST /api/auth/signup/email-verifications` 본문 `{"email":"logsafe@test.com"}` → 202 | `fakeMailSender.sent()` 중 `to`가 `logsafe@test.com`인 마지막 메일의 `body`(`"인증코드: " + 6자리`)에서 뽑은 6자리 인증코드 |
| 2 | `POST /api/auth/signup/email-verifications/confirm` 본문 `{"email":"logsafe@test.com","code": 인증코드}` → 200 | `$.verificationToken` |
| 3 | `POST /api/auth/signup` 본문 `{"email":"logsafe@test.com","password":"LogSafe9183x","nickname":"logsafe","name":"테스터","verificationToken": 2번 값}` → 201 | 비밀번호 `LogSafe9183x`, `$.accessToken`, `$.refreshToken`, `$.memberId`(구매자 ID) |
| 4 | `POST /api/auth/login` 본문 `{"email":"logsafe@test.com","password":"LogSafe9183x"}` → 200 | `$.accessToken`, `$.refreshToken` |
| 5 | `POST /api/auth/refresh` 본문 `{"refreshToken": 4번의 refresh}` → 200 | `$.accessToken`(이후 요청에 쓴다), `$.refreshToken` |
| 6 | `POST /api/billing-keys` 본문 `{"authKey":"auth_logsafe_0001"}` → 201 | `authKey` = `auth_logsafe_0001`, 빌링키 평문 = JDBC `SELECT billing_key_encrypted FROM payment.billing_key WHERE member_id = ?`을 `AesGcmEncryptor.decrypt`로 푼 값 |
| 7 | `createPendingOrder(구매자 ID, 상품 ID)`로 주문을 만든다(체크아웃 응답에 쓸 값은 `CheckoutResult`의 `orderId`·`pgOrderId`·`totalAmount`) | |
| 8 | `POST /api/orders/{orderId}/payment/confirm`(5번 access 토큰, `Idempotency-Key` = orderId) 본문 `{"paymentKey":"pk_logsafe_{orderId}","pgOrderId": 7번 값,"amount": 7번 totalAmount}` → 200, `$.status` = `PAID` | `paymentKey` = `pk_logsafe_{orderId}` |

**확인할 것**
- 캡처가 비어 있지 않다: `output.getAll()`이 `"pg confirm paymentId=" + paymentId + " result=Approved"`를 포함한다(`paymentId`는 JDBC `SELECT id FROM payment.payment WHERE order_id = ?`).
- 아래 값 각각에 대해 `assertThat(output.getAll()).doesNotContain(값)`이 통과한다: 비밀번호, 인증코드, 인증 토큰, 3·4·5번의 access 토큰 3개와 refresh 토큰 3개, `authKey`, 빌링키 평문, `paymentKey`. 값이 하나라도 걸리면 어느 값이 걸렸는지 `.as("값 이름")`으로 알 수 있게 쓴다.
- 확인 필요: `LogMailSender`(3-5)는 메일 본문 앞 100자를 로그로 찍어 인증코드가 포함된다. 테스트에서는 `FakeMailSender`가 대신 주입되므로 이 테스트로는 잡히지 않는다. `local` 프로필에서 인증코드가 로그에 남는지는 수동으로 확인한다(4번 요청 뒤 앱 콘솔에서 인증코드 검색).

#### Kibana에서 traceId로 흐름 보기 (수동)

**준비**: `docker compose --profile observability up -d` → 앱을 프로필 `local,elk`로 실행 → 판매자·가게·상품(10,000원)과 구매자를 만든다. Kibana 주소·포트는 compose 설정에서 확인한다(`5601`은 문서에 있고 호스트 publish는 확인 필요).

**동작**: 구매자가 `POST /api/orders/checkout`으로 주문을 만들고 `POST /api/orders/{id}/payment/confirm`으로 승인한다. 승인 응답의 `X-Request-Id` 값을 `T`로 적어 둔다. 릴레이(1초 주기)와 알림 발송(10초 주기)이 돌 때까지 30초 기다린다.

**확인할 것**
- 저장된 검색 "traceId로 흐름 보기"에 KQL `traceId : "T"`를 넣으면 **시간 오름차순**으로 행이 나오고, 행마다 `app` = `myroutine`, `traceId` = `T`이다.
- 행 중에 `logger_name`이 `TossClient`로 끝나고 `message`가 `pg confirm paymentId={결제 ID} result=Approved …`인 행이 1개다.
- 같은 검색에서 `memberId` = 구매자 ID, `orderId` = 주문 ID 필드를 가진 행이 1개 이상이다.
- 이벤트 소비 행(`eventId` 필드가 있고 `traceId` = `T`)이 1개 이상이다.
- 문서가 기대하는 흐름은 "체크아웃 → 승인 → outbox 저장 → 릴레이 → consumer → 알림 발송"이다. 확인 필요: 체크아웃은 별도 HTTP 요청이고 릴레이·알림 발송은 `JobRunner`가 새 traceId를 만드는 잡이라(2-5) `T` 하나로 묶이는지는 문서만으로 확정되지 않는다. 묶이지 않는 단계가 있으면 그 단계를 찾는 방법(예: `orderId` 검색)을 이 문서에 적는다.
- "ERROR만" 검색은 `level : "ERROR"`로 오류 없이 실행된다(0건이어도 된다). "결제 UNKNOWN" 검색은 `message : "UNKNOWN" and logger_name : *payment*`로 실행된다.
- 검색 3개가 `docker/kibana/saved-objects.ndjson`에 있고, Kibana를 새로 띄워 그 파일을 가져오면(Saved Objects → Import) 3개 이름이 목록에 보인다.
- PR에 "traceId로 흐름 보기" 결과 스크린샷 1장을 붙인다.

**리뷰 때 물어볼 것**: 분산 트레이싱(Tempo 등) 없이 로그로만 추적하면 무엇이 부족한가? (ADR-010)

---

## 7-2. Prometheus · Grafana: 메트릭 · 대시보드 · 알림 (L)

**목표**: 서비스 상태와 **정합성 지표**를 대시보드로 보고, 문제가 생기면 알림이 온다.

**새로 등장**: Prometheus(메트릭 수집), Grafana(대시보드·알림), Micrometer(앱에서 메트릭 만들기: `Counter`, `Timer`, `Gauge`)

**정책 (이 단계에서 정함)**
- DB를 조회해서 만드는 지표(대기 건수 등)는 **스크랩할 때마다 쿼리하지 않는다**. 각 모듈의 수집기가 30초마다 계산해 `AtomicLong`에 넣고, Gauge는 그 값을 읽는다.
- 지표 이름·라벨은 아래 표가 기준이다(ADR-010 목록과 같게, 바뀌면 ADR도 고친다).

### 할 일

**구현 순서**: 1) 노출(의존성·`application.yaml`·`SecurityConfig`) → 2) 메트릭 이름과 Micrometer 사용법 → 3)~5) 모듈별(조회 메서드 → 수집기 → Counter·Timer 연결) → 6) `StockCheckJob` → 7) 메일·OpenAI 클라이언트 Timer → 8) Prometheus·Grafana 파일·compose → 테스트(완료 확인) → 9) 장애 주입(수동).

**1) 노출** — `build.gradle`, `application.yaml`, `common.security.SecurityConfig`
- 의존성 `io.micrometer:micrometer-registry-prometheus` — Micrometer가 모은 값을 Prometheus 텍스트 형식으로 내보내는 구현체다. 넣으면 `MeterRegistry` 빈이 `PrometheusMeterRegistry`로 자동 등록되고 `/actuator/prometheus`가 생긴다. 아래 수집기·타이머는 모두 이 `MeterRegistry`를 주입받아 쓴다
- `management.endpoints.web.exposure.include: health,prometheus` — 노출할 actuator 엔드포인트 목록. `management.metrics.tags.application: myroutine` — 모든 지표에 `application="myroutine"` 라벨을 붙인다
- `SecurityConfig`: `/actuator/prometheus` 허용(관리 포트에만 열리고 외부에 노출하지 않으므로, 1-3에서 확인했듯 같은 체인이 관리 포트에도 적용된다). 지금 `SecurityConfig`는 `/actuator/health/**`만 `permitAll()`이고 나머지는 `anyRequest().authenticated()`라서, 이 줄이 없으면 Prometheus가 401을 받는다. `.requestMatchers("/actuator/health/**").permitAll()` 옆에 같은 방식으로 더한다
- `management.metrics.distribution.percentiles-histogram.http.server.requests: true` — p95 같은 분위수를 Grafana에서 `histogram_quantile`로 구하려면 `_bucket` 시리즈가 필요하다. HTTP 요청은 이 속성으로, Timer(아래 2))는 만들 때 `.publishPercentileHistogram()`로 켠다(확인 필요: 속성 이름과 효과는 `/actuator/prometheus` 출력에서 `_bucket`이 보이는지로 확인)

**2) 비즈니스 메트릭**

Micrometer 종류 세 가지 — 모두 주입받은 `MeterRegistry registry`로 만든다. 같은 이름·라벨로 다시 만들면 기존 것을 돌려준다

| 종류 | 언제 | 만드는 법 | 값을 바꾸는 법 |
|---|---|---|---|
| `Gauge` | 지금 값(올라갔다 내려감): 대기 건수, 나이(초) | `Gauge.builder("outbox_ready_count", ready, AtomicLong::doubleValue).register(registry)` — `ready`는 `AtomicLong` 필드. 라벨은 `.tag("type", "order_payment")` | 수집기가 `ready.set(n)`. Gauge는 값을 읽을 때만 `ready`를 본다 |
| `Counter` | 누적 횟수(증가만) | `registry.counter("kafka_dlt_records_total", "topic", topic)` → `Counter` | `.increment()` |
| `Timer` | 호출 한 번의 걸린 시간 | `Timer.Sample sample = Timer.start(registry)` → 호출 → `sample.stop(registry.timer("pg_call_seconds", "operation", op, "result", result))`. 이미 `System.nanoTime()`으로 재고 있으면 `registry.timer(...).record(나노초, TimeUnit.NANOSECONDS)` | `stop`/`record` 한 번이 1건 |

- **Gauge 함정**: Gauge는 `AtomicLong`을 약한 참조로만 잡는다. 지역 변수나 곧 버려질 객체에 두면 GC 뒤에 값이 `NaN`이 된다. 수집기 `@Component`의 **필드**에 둔다.
- 코드에 적는 이름이 Prometheus에 나오는 이름이다(Micrometer는 `.`을 `_`로 바꾸고 Counter에 `_total`, Timer에 `_seconds`를 붙인다). 아래 표는 **Prometheus에 보이는 이름**이므로, 구현 후 `/actuator/prometheus`에서 표와 같은 이름이 나오는지 확인한다(확인 필요: `_total`·`_seconds`를 코드 이름에 이미 적었을 때 중복 접미사가 붙는지)

| 이름 | 종류 | 계산 | 만드는 곳 |
|---|---|---|---|
| `outbox_ready_count` | Gauge | `count(*) WHERE status='READY'` | common `OutboxMetrics` (30초) |
| `outbox_oldest_age_seconds` | Gauge | `now - min(created_at) WHERE status='READY'` (없으면 0) | 같음 |
| `outbox_dead_count` | Gauge | `count(*) WHERE status='DEAD'` | 같음 |
| `kafka_dlt_records_total{topic}` | Counter | DLT로 보낼 때마다 +1 | `KafkaConfig`의 recoverer를 감싸서 |
| `payment_unknown_count` | Gauge | `status='UNKNOWN'` 결제 수 | payment `PaymentMetrics` (30초) |
| `payment_unknown_oldest_age_seconds` | Gauge | 가장 오래된 UNKNOWN의 `now - updated_at` | 같음 |
| `saga_stuck_count{type="order_payment"}` | Gauge | `PAYMENT_IN_PROGRESS`로 5분 넘은 주문 수 | order `OrderMetrics` (30초) |
| `saga_stuck_count{type="refund"}` | Gauge | `APPROVED`·`PG_CANCELLED`로 10분 넘은 환불 수 | 같음 |
| `stock_reservation_expired_total` | Counter | 만료 잡이 주문 하나를 만료시킬 때 +1 | `OrderExpirationService` |
| `stock_balance_mismatch_count` | Gauge | 아래 점검 잡의 불일치 상품 수 | product `StockCheckJob` |
| `pg_call_seconds{operation,result}` | Timer | Toss 호출마다 | `TossClient` |
| `mail_send_seconds{result}`, `openai_call_seconds{operation,result}` | Timer | 호출마다 | 각 클라이언트 |

수집기(`OutboxMetrics`, `PaymentMetrics`, `OrderMetrics`)의 공통 모양 — 위 정책의 "30초마다 계산해 `AtomicLong`에 넣는" 일을 하는 `@Component`다. 모양은 셋이 같다

1. 필드: 지표마다 `AtomicLong` 하나(초기값 0). `saga_stuck_count`는 같은 이름에 `type` 라벨만 다른 Gauge 둘이라 `AtomicLong`도 둘
2. 생성자(주입: `MeterRegistry`와 아래 조회 수단): 필드마다 `Gauge.builder(이름, 필드, AtomicLong::doubleValue)[.tag(...)].register(registry)`
3. `@Scheduled(fixedDelay = 30_000)` 메서드 하나(이름은 권장안 `refresh()`, **`public`** — `MetricsTest`가 직접 부른다): 쿼리 실행 → 각 필드 `set`. 쿼리 예외는 스케줄러가 로그를 남기고 다음 주기에 다시 하므로 Gauge는 직전 값을 유지한다
4. `JobRunner`·잡 락은 쓰지 않는다. 락은 "한 인스턴스만 실행"인데, Prometheus는 인스턴스마다 긁으므로 각 인스턴스가 자기 Gauge를 계산해야 한다(확인 필요: 문서에 명시가 없다)

**3) common.outbox · common.config**

| 클래스 | 규격 |
|---|---|
| `OutboxMetrics` (`@Component`, 권장 패키지 `common.outbox`) | 수집기(공통 모양은 위 2)). 조회 수단: `JdbcTemplate` — `common.outbox_event`는 같은 모듈 테이블. 계산: 표의 세 값. `now`는 SQL의 `now()`로 구하면 된다(릴레이도 DB 시각을 쓴다) |
| `KafkaConfig` (기존 수정, 3-2) | `kafka_dlt_records_total{topic}` Counter를 건다. 방법은 아래 첫 항목 |

- `kafka_dlt_records_total{topic}`: 3-2 `KafkaConfig`가 만든 `DeadLetterPublishingRecoverer`는 `ConsumerRecordRecoverer`(= `BiConsumer<ConsumerRecord<?,?>, Exception>`)다. `DefaultErrorHandler`에 넘기기 전에 `ConsumerRecordRecoverer counting = (record, ex) -> { recoverer.accept(record, ex); registry.counter("kafka_dlt_records_total", "topic", record.topic()).increment(); }`로 감싸 넘긴다(시그니처는 spring-kafka 3.3.16 jar에서 확인, Boot 4.1.1이 쓰는 버전에서도 같은지 확인 필요). `record.topic()`은 **원본 토픽**이라 7-3 DLT API의 `{topic}`과 같은 값이다(라벨을 원본·`.dlt` 중 무엇으로 할지는 문서에 없다. 확인 필요). `KafkaConfig`가 `MeterRegistry`를 주입받는다

**4) payment.domain · payment.infrastructure · payment.application**

| 클래스 | 규격 |
|---|---|
| `PaymentRepository` (domain 순수 인터페이스, 기존 수정) | `long countByStatus(PaymentStatus status)`와 `Optional<Instant> findOldestUpdatedAtByStatus(PaymentStatus status)`를 더한다(이름·인자는 예). 대상은 `UNKNOWN` |
| `JpaPaymentRepository` (infrastructure, 기존 수정) | 위 두 메서드를 구현한다 |
| `PaymentMetrics` (`@Component`, application) | 수집기(공통 모양은 위 2)). 조회 수단: `PaymentRepository`. 계산: `payment_unknown_count` = `countByStatus(UNKNOWN)`, `payment_unknown_oldest_age_seconds` = `Duration.between(가장 오래된 updated_at, clock.instant()).toSeconds()`, 없으면 0 |
| `TossClient` (infrastructure, 기존 수정, 2-3) | `pg_call_seconds{operation,result}` Timer를 건다. 방법은 아래 |

- `pg_call_seconds{operation,result}`: `TossClient`의 `confirm`·`cancel`·`queryByOrderId`가 이미 `start`를 재고 마지막에 `logResult(pgMethod, paymentId, result, start)`를 부른다. 같은 자리에서 `registry.timer("pg_call_seconds", "operation", pgMethod, "result", ...).record(System.nanoTime() - start, TimeUnit.NANOSECONDS)`를 하면 된다. `TossClient`에 `MeterRegistry` 필드를 더한다. 라벨 값은 문서에 정해져 있지 않다(확인 필요). 로그와 대시보드가 같은 말을 쓰도록 `operation`은 `pgMethod`(`confirm`·`cancel`·`query`), `result`는 `PgResult` 종류(`Approved`·`Rejected`·`Unknown`)를 소문자로 쓰는 것을 권장한다 Part 5가 더한 `issueBillingKey`·`billingCharge`(로그의 작업 이름 `BILLING_ISSUE`·`BILLING`)에도 같은 Timer를 걸지, `operation` 값을 무엇으로 할지는 문서에 없다(확인 필요).

**5) order.domain · order.infrastructure · order.application**

| 클래스 | 규격 |
|---|---|
| `OrderRepository` (domain 순수 인터페이스, 기존 수정) | `long countByStatusAndUpdatedAtBefore(OrderStatus status, Instant before)`를 더한다(이름·인자는 예) |
| `RefundRepository` (domain 순수 인터페이스, 기존 수정) | `long countByStatusInAndUpdatedAtBefore(Collection<RefundStatus> statuses, Instant before)`를 더한다(이름·인자는 예) |
| `JpaOrderRepository`·`JpaRefundRepository` (infrastructure, 기존 수정) | 위 두 메서드를 구현한다 |
| `OrderMetrics` (`@Component`, application) | 수집기(공통 모양은 위 2)). 조회 수단: 위 두 저장소. 계산: 표의 `saga_stuck_count` 두 값을 `updated_at < now - 기준 시간`으로 센다. 기준 컬럼이 `updated_at`인 것은 2-6 `findStuckIds`·2-8b 복구 잡과 같다(문서에 컬럼이 명시되지 않아 확인 필요) |
| `OrderExpirationService` (application, 기존 수정) | `stock_reservation_expired_total` Counter를 건다. 방법은 아래 |

- `stock_reservation_expired_total`: `OrderExpirationService.expireBatch`의 건별 루프에서 `tx.execute(s -> expireOne(id))`가 `true`를 반환한 **직후**(트랜잭션 밖)에 `increment()`. `expireOne` 안에서 올리면 롤백돼도 카운트가 남는다

**6) product.infrastructure — 재고 정합성 점검 잡** `StockCheckJob`
```sql
SELECT s.product_id FROM product.stock s
LEFT JOIN (SELECT product_id, SUM(quantity) q FROM product.stock_movement WHERE type IN ('RECEIVE','ADJUST') GROUP BY product_id) m ON m.product_id = s.product_id
LEFT JOIN (SELECT product_id, SUM(quantity) q FROM product.stock_reservation WHERE status = 'HELD' GROUP BY product_id) r ON r.product_id = s.product_id
WHERE s.received <> COALESCE(m.q, 0) OR s.reserved <> COALESCE(r.q, 0)
```

`StockCheckJob` (product.infrastructure, `@Component`) — **재고 행의 숫자가 기록(이력·예약)과 맞는지 하루에 한 번 대조하는 잡**. 다른 잡(`OrderExpirationJob` 등)과 같은 모양이다
- **왜 필요한가**: `ck_stock_balance`(`available + reserved + sold = received`)는 한 행 안의 등식만 지킨다. 코드 버그로 `received`와 `available`을 같이 틀리게 바꾸면 CHECK는 통과하지만 이력과는 어긋난다. 이 잡이 그 어긋남을 잡는다(INV-03·04)
- **구성**: `@Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")` 메서드 → `jobRunner.run("stock-check", this::check)`. `JobRunner.run`은 `Supplier<Integer>`를 받으므로 `check()`는 `int`(불일치 상품 수)를 반환한다. `check()`(이름은 권장안, `public`)가 하는 일: ① 위 SQL을 `JdbcTemplate.queryForList(sql, UUID.class)`로 실행(`List<UUID>` = 어긋난 `product_id`) ② 상품마다 `log.error("stock mismatch productId={}", id)` ③ `AtomicLong`(`stock_balance_mismatch_count`)에 `set(개수)` ④ 개수 반환
- **SQL 읽는 법**: `m.q`는 상품별 `stock_movement`의 **입고(RECEIVE)와 조정(ADJUST)** 수량 합(조정은 음수일 수 있다)이고, `s.received`와 같아야 한다. `r.q`는 상품별 `HELD`(보류 중) 예약 수량 합이고 `s.reserved`와 같아야 한다. 이력·예약이 한 건도 없는 상품은 LEFT JOIN 결과가 `NULL`이라 `COALESCE(..., 0)`로 0과 비교한다
- 이 Gauge는 05:00 실행 때만 바뀐다. 앱을 재시작하면 다음 05:00까지 0으로 보인다.

**7) common.mail · Part 6 OpenAI 호출부**

- `mail_send_seconds{result}`·`openai_call_seconds{operation,result}`: "각 클라이언트"는 메일 발송기(3-5 `common.mail.MailSender` 구현체. 호출하는 `NotificationDispatcher`에서 잴지는 확인 필요)와 OpenAI를 부르는 곳(Part 6의 `EmbeddingModel.embed`·`ChatClient` 호출부)이다. 방식은 `Timer.start` → 호출 → `stop`(예외가 나도 `finally`에서 `stop`)이며 `result` 값(`success`·`failure` 등)과 `operation` 값은 정해져 있지 않다(확인 필요)

**8) Prometheus·Grafana** — `docker/docker-compose.yml`(프로필 `observability`), `docker/prometheus/prometheus.yml`, `docker/grafana/provisioning/`
- docker-compose `prometheus`·`grafana` 서비스(프로필 `observability`, 공통 규칙): 이미지 `prom/prometheus:v3.15.0`, `grafana/grafana:13.2.3`(2026-10-09 Docker Hub의 최신 안정 태그). `docker/prometheus/prometheus.yml`을 컨테이너의 `/etc/prometheus/prometheus.yml`로, `docker/grafana/provisioning`을 컨테이너의 `/etc/grafana/provisioning`으로 마운트한다. 호스트 포트 publish는 문서에 없다(확인 필요)
- `docker/prometheus/prometheus.yml`: 대상은 환경마다 다르다
  - 구조: `global.scrape_interval: 15s`, `scrape_configs`에 `job_name: myroutine`, `metrics_path: /actuator/prometheus`, `static_configs[].targets`에 아래 환경별 주소. Prometheus가 그 주소로 15초마다 HTTP GET을 보내 현재 값을 가져가는 "pull" 방식이라 앱은 값을 밀어 보내지 않는다
  - 로컬(앱은 IDE·`bootRun`으로 호스트에서 실행): `host.docker.internal:8081` — Docker Desktop(macOS)에서는 그대로 된다. Linux에서 호스트의 앱을 긁으려면 prometheus 서비스에 `extra_hosts: ["host.docker.internal:host-gateway"]`가 필요하다
  - 운영 VM(1-11 `docker-compose.ops.yml`의 `observability` 프로필): 앱도 컨테이너이므로 `app:8081`(관리 포트는 호스트에 publish하지 않지만 같은 compose 네트워크에서는 닿는다). 파일을 둘로 두거나(`prometheus.yml`, `prometheus.ops.yml`) 대상만 바꾼다
- `docker/grafana/provisioning/datasources/prometheus.yml`, `docker/grafana/provisioning/dashboards/`의 provider 설정 yml 1개와 `*.json` 3개(서비스 개요, 결제·정합성, Kafka·Outbox)
  - 프로비저닝은 Grafana가 기동할 때 이 디렉터리를 읽어 데이터소스·대시보드를 자동으로 만드는 기능이다. 데이터소스 파일은 `apiVersion: 1`, `datasources:`에 `name: Prometheus`, `type: prometheus`, `url: http://prometheus:9090`(compose 서비스 이름), `isDefault: true`
  - 대시보드 JSON을 읽게 하려면 같은 `dashboards/` 디렉터리에 provider 설정 yml(`providers:`에 `type: file`, `options.path`로 JSON이 있는 컨테이너 경로)이 있어야 한다. JSON만 두면 로드되지 않는다(확인 필요: Grafana 13 문서의 형식)
  - 대시보드 JSON은 화면에서 패널을 만든 뒤 대시보드 설정의 JSON Model(또는 Export)을 복사해 파일로 저장한다(공통 규칙)
  - **서비스 개요**: 요청률·에러율·p95(`http_server_requests_seconds`), JVM 힙, HikariCP 활성 커넥션, Kafka consumer lag
    - Boot 4.1.1은 Kafka consumer·producer 팩토리에 Micrometer 리스너(`MicrometerConsumerListener`)를 자동으로 붙인다(jar로 확인)
    - Kafka 클라이언트 지표 `records-lag-max`는 Prometheus에서 `kafka_consumer_fetch_manager_records_lag_max`로 보일 것으로 예상한다(확인 필요: `/actuator/prometheus` 출력에서 `lag`로 검색해 실제 이름을 쓴다)
  - **결제·정합성**: `payment_unknown_*`, `saga_stuck_count`, `stock_balance_mismatch_count`, `pg_call_seconds` p95·결과별 비율, `stock_reservation_expired_total` 증가율
  - **Kafka·Outbox**: `outbox_*`, `kafka_dlt_records_total`
- `docker/grafana/provisioning/alerting/rules.yml` (Grafana 알림 규칙을 파일로)
  - 규칙 하나 = "Prometheus에 PromQL을 묻고(`조건` 열) → 결과가 임계값을 넘으면(`> 0` 등) → 그 상태가 `지속`만큼 이어지면 Firing". `즉시`는 대기 시간 0, `2분`·`5분`은 규칙의 `for` 값이다
  - 파일 형식(`groups[].rules[]`의 `data` 쿼리·`condition`·`for`)은 손으로 쓰기 번거롭다. 화면에서 규칙을 만들고 내보내기(Export)한 YAML을 이 파일로 쓰는 편이 쉽다(확인 필요: Grafana 13 화면의 내보내기 위치와 프로비저닝 형식)
  - 라벨이 다른 시계열은 알림도 따로 뜬다(`saga_stuck_count`는 `type`별로 두 알림 인스턴스)

| 알림 | 조건 | 지속 |
|---|---|---|
| DLT 적재 | `increase(kafka_dlt_records_total[5m]) > 0` | 즉시 |
| Outbox 지연 | `outbox_oldest_age_seconds > 60` | 2분 |
| Outbox DEAD | `outbox_dead_count > 0` | 즉시 |
| 결제 확인 지연 | `payment_unknown_oldest_age_seconds > 600` | 즉시 |
| 정체 Saga | `saga_stuck_count > 0` | 5분 |
| 재고 불일치 | `stock_balance_mismatch_count > 0` | 즉시 |

- 알림 전달 채널은 로컬이라 두지 않는다(Grafana 화면에서 Firing 상태로 확인).

**9) 장애 주입으로 확인 (수동)** — Kafka 중단, 결제 불확실, 재고 불일치 3가지의 주입·기대·복구는 완료 확인 "수동 확인: 장애 주입 3가지"에 있다

### 완료 확인

**확인 값을 읽는 방법**
- 테스트의 메트릭: 주입받은 `MeterRegistry registry`에서 Gauge는 `registry.get("이름").gauge().value()`, 라벨이 있으면 `registry.get("이름").tag("키", "값").gauge()`, Counter는 `.counter().count()`. `/actuator/prometheus`는 MockMvc로 부를 수 없다(관리 포트 8081).
- 수집기 실행: `OutboxMetrics`·`PaymentMetrics`·`OrderMetrics`의 `refresh()`를 테스트가 직접 부른다(테스트 프로필은 `myroutine.scheduling.enabled=false`(2-5)라 `@Scheduled`가 돌지 않는다).
- Counter는 같은 Spring 컨텍스트의 다른 테스트가 올려 둔 값이 남아 있을 수 있다. 동작 전에 `count()`를 읽어 두고 증가분으로 비교한다. Gauge는 `refresh()`가 값을 덮어쓰므로 절댓값으로 비교한다.
- 수동 확인의 화면: Prometheus(Targets, Graph), Grafana(Dashboards, Alerting). 호스트 포트는 compose 설정에서 확인한다(문서에 없다, 확인 필요).

#### `common/MetricsTest` (`IntegrationTestSupport`)

주문·결제를 쓰는 케이스(3~6번)의 공통 준비: 판매자·가게·상품(`registerProduct(sellerId, shopId, 1000, 10)`)과 구매자 1명.

**1. outbox 수집기 Gauge 값** — 준비: `common.outbox_event`에 JDBC `INSERT`로 4행을 넣는다. 공통 값은 `aggregate_type` `TEST`, `event_type` `TestEvent`, `topic` `test.metrics.v1`, `payload` `'{}'::jsonb`, `attempts` 0, `next_attempt_at` 지금, `updated_at` 지금. 행별로 다른 값: READY 행 A(`created_at` = 지금 - 120초), READY 행 B·C(`created_at` = 지금), DEAD 행 D(`status` `DEAD`, `attempts` 10, `created_at` = 지금).
- 동작·확인: `outboxMetrics.refresh()` 뒤 `outbox_ready_count` = 3.0 / `outbox_oldest_age_seconds`는 120.0 이상 130.0 미만 / `outbox_dead_count` = 1.0. 행 A를 `SENT`로 바꾸고 다시 `refresh()`하면 `outbox_ready_count` = 2.0이고 `outbox_oldest_age_seconds`는 0 이상 10.0 미만이다.

**2. DLT Counter** — 동작: 3-2 `KafkaDltTest`의 항상 실패하는 테스트 리스너 토픽 `test.always-fail.v1`에 메시지 1건을 보낸다. 보내기 전 `registry.get("kafka_dlt_records_total").tag("topic", "test.always-fail.v1").counter().count()`를 `before`로 읽는다(Counter가 아직 없으면 `registry.find(...)`가 null이라 `before` = 0).
- 확인: `Eventually.await(Duration.ofSeconds(20), ...)` 안에서 같은 Counter의 `count()` - `before` = 1.0.

**3. 주문의 `saga_stuck_count` 기준 시간** — 준비: `pg.on("/v1/payments/confirm", Behavior.delay(1000))` + `createPendingOrder` + `confirmPayment`로 `PAYMENT_IN_PROGRESS` 주문 1개.
- 확인: `setOrderUpdatedAt(orderId, 지금 - 4분)` → `orderMetrics.refresh()` → `saga_stuck_count{type="order_payment"}` = 0.0 / `setOrderUpdatedAt(orderId, 지금 - 6분)` → `refresh()` → 1.0.

**4. `stock_reservation_expired_total`** — 준비: 서로 다른 상품 `p1`·`p2`·`p3`로 `createPendingOrder` 주문 3개를 만들고 셋 모두 `setOrderExpired(orderId, 지금 - 1분)`. `p3`에는 `breakReservedStock(p3)`(`UPDATE product.stock SET available = available + reserved, reserved = 0`)를 건다. 동작: `before`를 읽은 뒤 `orderExpirationService.expireBatch()`.
- 확인: 반환값 2, `stock_reservation_expired_total`의 `count()` - `before` = 2.0.

**5. 결제 수집기 Gauge 값** — 준비: `pg.on("/v1/payments/confirm", Behavior.delay(1000))` + `createPendingOrder(구매자 ID, 상품 ID)` → `confirmPayment(구매자 ID, orderId)`(응답 202). 다른 주문 하나는 `pg.on("/v1/payments/confirm", Behavior.approve())`로 되돌린 뒤 `createPaidOrder`로 만들어 `APPROVED` 결제를 하나 더 둔다. JDBC로 UNKNOWN 결제의 `updated_at`을 지금 - 90초로 바꾼다(`UPDATE payment.payment SET updated_at = ? WHERE order_id = ?`).
- 확인: `paymentMetrics.refresh()` 뒤 `payment_unknown_count` = 1.0 / `payment_unknown_oldest_age_seconds`는 90.0 이상 100.0 미만.

**6. 환불의 `saga_stuck_count` 기준 시간** — 준비: `createPaidOrder` 주문의 품목 1개를 `pg.on("/v1/payments/" + paymentKey, Behavior.delay(1000))` 후 취소 요청(응답 202)으로 `APPROVED`에 멈춘 환불 1건. 환불 ID는 응답의 `$.refundId`.
- 확인: `setRefundUpdatedAt(refundId, 지금 - 9분)` → `orderMetrics.refresh()` → `saga_stuck_count{type="refund"}` = 0.0 / `setRefundUpdatedAt(refundId, 지금 - 11분)` → `refresh()` → 1.0.

#### `product/application/StockCheckJobTest` (`OutputCaptureExtension` + `IntegrationTestSupport`)

`check()`를 직접 부른다. 준비: 판매자·가게·상품(`registerProduct(sellerId, shopId, 1000, 10)` → `product.stock` `received` 10·`available` 10·`reserved` 0·`sold` 0, `stock_movement`에 `RECEIVE` 10 1행).

**1. 정상 재고** — 동작: `check()`.
- 반환값 0, `stock_balance_mismatch_count` = 0.0.

**2. 입고 합 불일치** — 동작: JDBC `UPDATE product.stock SET received = received + 5, available = available + 5 WHERE product_id = ?` → `check()`.
- 반환값 1, `stock_balance_mismatch_count` = 1.0, 출력에 `"stock mismatch productId=" + productId` 한 줄.
- `UPDATE`를 되돌리고(`received - 5`, `available - 5`) `check()` → 반환값 0, Gauge 0.0.

**3. 예약 합 불일치** — 준비: `createPendingOrder(구매자 ID, productId)`(`reserved` 1, `stock_reservation` `HELD` 1건). 동작: `breakReservedStock(productId)`(`available`에 `reserved`를 더하고 `reserved` 0) → `check()`.
- 확인: 두 번째 `check()` 반환값 1, Gauge 1.0, 출력에 `"stock mismatch productId=" + productId`.

#### 수동 확인: 노출과 대시보드

**준비**: `docker compose --profile observability up -d` → 앱을 프로필 `local`로 실행 → 가입·결제 승인·품목 취소로 요청을 몇 건 보낸다 → 1분 기다린다.

- 노출: `curl -s -o /dev/null -w '%{http_code}' localhost:8081/actuator/prometheus` = `200`. `curl -s localhost:8081/actuator/prometheus | grep -E '^(outbox_|payment_unknown|saga_stuck_count|stock_balance_mismatch_count|stock_reservation_expired_total|kafka_dlt_records_total|pg_call_seconds)'` 출력에 위 지표표의 이름이 모두 나오고 `_total_total`·`_seconds_seconds` 같은 중복 접미사가 없다. `kafka_dlt_records_total`·`stock_reservation_expired_total`·`pg_call_seconds_*`는 처음 한 번 올라간 뒤부터 나온다. 모든 줄에 `application="myroutine"` 라벨이 있다.
- 분위수: 같은 출력에 `http_server_requests_seconds_bucket`이 있다.
- Kafka 지표: 같은 출력에서 `lag`로 검색한 줄의 실제 이름을 적어 서비스 개요 대시보드의 consumer lag 패널 쿼리와 같은지 본다.
- Prometheus: Status → Targets에서 대상 `myroutine`(주소는 로컬 `host.docker.internal:8081`)이 `UP`. 쿼리 `outbox_ready_count`가 값 1개를 돌려준다.
- Grafana: 데이터소스 `Prometheus`의 "Save & test"가 성공한다. Dashboards에 3개(서비스 개요, 결제·정합성, Kafka·Outbox)가 파일에서 자동으로 보이고, 각 패널이 쿼리 오류(빨간 표시) 없이 열린다. Alerting → Alert rules에 6개(DLT 적재, Outbox 지연, Outbox DEAD, 결제 확인 지연, 정체 Saga, 재고 불일치)가 있고 평시 상태가 `Normal`이다.

#### 수동 확인: 장애 주입 3가지

공통: 앱·관측 스택이 떠 있고 위 6개 알림이 `Normal`인 상태에서 시작한다. Firing은 아래 대기 시간만큼 기다린 뒤 본다. 각 시나리오는 Grafana Alerting 화면의 해당 규칙이 `Firing`이 되는 것을 확인하고, 복구한 뒤 `Normal`로 돌아오는 것을 확인한다. PR에 `Firing` 화면과 복구 후 화면 스크린샷을 시나리오마다 붙인다.

| # | 주입 | 기대 | 복구와 해제 확인 |
|---|---|---|---|
| 1 | `docker compose stop kafka` 후 outbox 행이 생기는 요청 2건(가입 2건) | "Outbox 지연"(`outbox_oldest_age_seconds > 60`, 2분 지속) Firing: 행 생성 뒤 약 4분 안. 같은 시각 Prometheus 쿼리 `outbox_ready_count` ≥ 1 | `docker compose start kafka` → `outbox_ready_count` 0, 규칙 `Normal`(2분 안) |
| 2 | 로컬 실행 설정 `myroutine.toss.base-url`을 `http://10.255.255.1`로 바꿔 앱을 다시 띄우고 결제 승인 1건 | 승인 응답 202, `payment_unknown_count` = 1. "결제 확인 지연"(`payment_unknown_oldest_age_seconds > 600`) Firing: 약 10분 뒤 | `base-url`을 되돌려 앱 재기동 → 대사 잡(1분 주기)이 결제를 확정 → `payment_unknown_count` 0, 규칙 `Normal` |
| 3 | 로컬 DB에서 `UPDATE product.stock SET received = received + 5, available = available + 5 WHERE product_id = ?` 후 재고 점검 잡 실행 | `stock_balance_mismatch_count` = 1, "재고 불일치" Firing(지속 즉시) | UPDATE를 되돌리고(`- 5`) 점검 잡 재실행 → Gauge 0, 규칙 `Normal` |

확인 필요 (아래 셋은 구현 전에 정한다)
- 시나리오 1: Kafka가 멈춘 동안 릴레이가 같은 행에서 10번 실패하면(실패마다 `send`가 최대 5초 막히고 10초 뒤 재시도, 약 2분 30초) 행이 `DEAD`가 된다(3-1). 그러면 `outbox_ready_count`가 줄어 "Outbox 지연" 조건이 사라지고 "Outbox DEAD"가 Firing될 수 있다. 어느 알림이 뜨는지 실측해 이 표에 적고, `DEAD`가 된 행은 Kafka를 되살려도 7-3 `POST /admin/api/outbox/{id}/retry`를 해야 READY로 돌아온다.
- 시나리오 2: 대사 잡이 같은 UNKNOWN 결제를 1분마다 다시 조회하며 `increaseReconcileAttempts`가 `payment.updated_at`을 매번 갱신한다(2-6). `payment_unknown_oldest_age_seconds`가 `updated_at` 기준이면 이 값이 600초를 넘기 전에 0으로 돌아가 알림이 뜨지 않는다. 안 뜨면 이 때문이다. 기준 컬럼을 `created_at`으로 바꿀지 정한다.
- 시나리오 3: 재고 점검 잡을 실행하는 방법이 문서에 없다(확인 필요: 05:00을 기다림, 로컬에서 cron 임시 변경, 별도 트리거 중 하나를 정한다).

**리뷰 때 물어볼 것**
- 정합성이 깨지면 어떻게 알 수 있나? 알림 임계값은 어떻게 정했나?
- Gauge에서 매번 DB를 조회하면 무엇이 문제인가?

---

## 7-3. 운영 API (M)

**목표**: 알림을 받았을 때 처리할 수단. 모두 ADMIN만, 실행 이력을 로그로 남긴다.

**정책 (이 단계에서 정함)**
- FAILED 환불 재시도를 위해 환불 상태 전이에 `FAILED → APPROVED`(관리자 재시도)를 추가한다([상태머신 §4](../02-design/04-state-machines.md) 갱신).
- DLT 재처리는 같은 메시지를 **원래 토픽으로 다시 보내는 것**이다. consumer가 `InboxGuard`로 멱등이라 두 번 재처리해도 결과는 한 번이다.

### 할 일

**구현 순서**: 1) `RefundStatus`·`Refund.retryPgCancel()` → 2) `OrderReconcileService.reconcileOne` → 3) → 4) → 5) → 6)(각 절에서 목록 조회 쿼리 → 컨트롤러 순) → 8) 테스트 보조 → 테스트(완료 확인). 7)은 3-4가 만든 컨트롤러라 만들 것이 없고, 9) `docs/runbook.md`는 Claude가 쓴다.

이름·시그니처는 권장안이다. API별 처리 순서는 각 절에 있다.

**모든 API에 공통**
- **인가**: 컨트롤러에서 따로 검사하지 않는다. 3-4가 `SecurityConfig`에 넣은 `.requestMatchers("/admin/**").hasRole("ADMIN")` 한 줄이 맡는다. 일반 회원은 403(`FORBIDDEN`)
- **실행 로그**: 쓰기 API(retry·replay·reconcile·run)는 메서드 첫 부분에서 `log.info("admin action={} target={} by={}", action, target, memberId)`를 남긴다. `memberId`는 컨트롤러 파라미터 `@CurrentMember UUID memberId`, `target`은 대상 식별자(outbox id, `topic/partition/offset`, paymentId 등)다. `action`에 쓸 문자열은 정해져 있지 않다(확인 필요, 권장: API별 `outbox-retry`·`dlt-replay` 같은 고정 문자열)
- **목록 API**(outbox·payments·refunds·notifications): `cursor`가 없으면(`null`·빈 값은 `decode`에 넘기지 않는다) 첫 페이지, 있으면 `Cursor.decode(cursor)`로 `Cursor(Instant createdAt, UUID id)`를 얻어 그 뒤부터 읽는다. `size + 1`건을 읽어 `size`건만 `CursorPage<T>(items, nextCursor)`로 돌려주고, 다음 커서는 `size`번째 항목의 `new Cursor(createdAt, id).encode()`다(마지막 페이지면 `nextCursor = null`). `size`의 기본값·최대값은 이 표에 없다(확인 필요)
- **없는 대상**이거나 **상태가 맞지 않을 때**의 응답은 아래 각 API에서 확인 필요로 모았다. 존재하는 코드는 `NOT_FOUND`(404), `INVALID_STATE_TRANSITION`(409)이다

**1) order.domain · order.infrastructure**

| 클래스 | 규격 |
|---|---|
| `RefundStatus` (기존 수정, 2-8) | 전이표에 `FAILED → APPROVED`(관리자 재시도)를 더한다 |
| `Refund` (기존 수정, 2-8) | `retryPgCancel()`을 더한다 — "FAILED가 아니면 거부하고, FAILED → APPROVED로 바꾸면서 `pgAttempts`를 1 올리는" 메서드다. `pg_attempts` 칸과 `Refund.pgIdempotencyKey()`(키 형식은 아래 "PG 멱등 키")는 2-8에서 만든다. `refunds` `retry`가 부른다 |
| `RefundRepository` (domain 순수 인터페이스, 기존 수정) | `status`별 커서 조회를 더한다. 이름·인자는 문서에 없다(확인 필요). 커서·`size + 1`은 위 "모든 API에 공통"의 목록 규칙. `refunds` 목록 API가 쓴다 |
| `JpaRefundRepository` (infrastructure, 기존 수정) | 위 조회를 구현한다 |

**2) order.application**

| 클래스 | 메서드 |
|---|---|
| `OrderReconcileService` (기존 수정) | `public OrderStatus reconcileOne(UUID orderId)` — 기존 `private boolean reconcileOne(UUID)`를 열고 반환을 바꾼다. `payments reconcile`(3)) API와 `reconcileBatch`가 쓴다 |

- 처리 내용(`paymentApi.find` → `APPROVED`·`FAILED`는 `closeConfirmed`, `IN_PROGRESS`·`UNKNOWN`은 `closeByQuery`(`queryPgOutcome` → `applyOutcome`), `CANCELLED`는 `failOrder`, 완료 실패 시 보상)는 그대로다.
- `reconcileBatch`는 반환된 `OrderStatus`가 `PAID`·`PAYMENT_FAILED`일 때 처리 건수에 센다.

**3) order.web** — 컨트롤러 이름은 문서에 없다(권장: `AdminReconcileController`, `AdminRefundController`)

| 클래스 | 규격 |
|---|---|
| `AdminReconcileController` | `POST /admin/api/payments/{orderId}/reconcile` — 그 주문만 바로 대사(2-6 로직) → 200 `{orderId, status}`(처리 뒤 주문 상태). `payment` → `order` 호출은 허용 방향이 아니므로 대사 API는 order 모듈에 둔다 |
| `AdminRefundController` | `GET /admin/api/refunds?status=FAILED&cursor=` · `POST /admin/api/refunds/{id}/retry` — FAILED → APPROVED → `refundProcessor.process` |

**payments reconcile** (order 모듈 관리자 컨트롤러) — 대사 잡이 30초 이상 멈춘 주문을 1분마다 처리하는 일을, 운영자가 **주문 하나에 대해 바로** 하는 API다
1. `reconcileOne(UUID orderId)`(2)에서 연 메서드): `OrderReconcileService`는 클래스에 `@Transactional`이 없고 `TransactionTemplate`을 쓴다. `queryPgOutcome`·`cancel`이 트랜잭션 밖 전용이므로 컨트롤러나 `reconcileOne` 자체에 `@Transactional`을 붙이면 안 된다
2. 두 번 눌러도 안전한 이유: 이미 PAID가 된 주문은 2-4 `applyPaymentState`의 가드(`PAYMENT_IN_PROGRESS`가 아니면 현재 상태를 그대로 반환)가 막는다
3. **경로의 값은 `orderId`다.** `PaymentApi.find(UUID orderId)`와 `reconcileOne(UUID orderId)`가 모두 `orderId`를 받으므로 payment ID로 찾는 메서드를 새로 만들지 않는다. 목록 응답 항목에 `orderId`가 있어 운영자는 그 값을 쓴다
4. 조회도 불확실했거나 다른 흐름이 이미 처리했으면 `reconcileOne`은 그 시점의 상태(예: `PAYMENT_IN_PROGRESS`)를 돌려준다

**refunds** (order 모듈)
- 목록: 응답 항목의 필드는 이 문서에 정해져 있지 않다(확인 필요, `Refund` 컬럼 기준으로 id·orderId·orderLineId·amount·상태·실패 사유 등)
- `retry`: ① 트랜잭션(`tx.execute`)에서 환불을 읽어 `retryPgCancel()`(1)에서 더한 메서드)을 부른다. 같은 환불에 재시도를 두 번 눌러도 두 번째는 이미 `APPROVED`라 거부된다 ② 트랜잭션 밖에서 `refundProcessor.process(refundId)`(2-8, `RefundStatus` 반환) — APPROVED이므로 `needsPgCancel()`이 참이라 PG 취소부터 다시 한다
- **PG 멱등 키**: 재시도는 새 키로 Toss까지 간다. 2-6 `PaymentApi.cancel`은 같은 키의 `payment_cancel` 행이 이미 `FAILED`면 PG를 부르지 않고 `FAILED`를 재생하므로, `process`가 쓰는 `refund.pgIdempotencyKey()`(2-8)가 `pgAttempts`가 1 이상이면 `refundId-r{pgAttempts}`로 달라진다. 그래서 `AdminRefundRetryTest`("가짜 PG를 정상으로 → retry → COMPLETED")가 통과한다. Toss가 같은 키의 실패 응답을 어떻게 다루는지는 확인하지 못해(확인 필요) 같은 키를 재사용하지 않는 쪽으로 정했다.

**4) payment.domain · payment.infrastructure · payment.web** — 컨트롤러 이름은 문서에 없다(권장: `AdminPaymentController`)

| 클래스 | 규격 |
|---|---|
| `PaymentRepository` (domain 순수 인터페이스, 기존 수정) | `status`별 커서 조회를 더한다. 이름·인자는 문서에 없다(확인 필요). 커서·`size + 1`은 위 "모든 API에 공통"의 목록 규칙 |
| `JpaPaymentRepository` (infrastructure, 기존 수정) | 위 조회를 구현한다 |
| `AdminPaymentController` | `GET /admin/api/payments?status=UNKNOWN&cursor=` — 결제 목록 |

응답 항목은 `(UUID id, UUID orderId, long amount, int reconcileAttempts, Instant updatedAt)`

**5) notification.domain · notification.infrastructure · notification.web** — 컨트롤러 이름은 문서에 없다(권장: `AdminNotificationController`)

| 클래스 | 규격 |
|---|---|
| `NotificationRepository` (domain 순수 인터페이스, 기존 수정, 3-5) | `status`별 커서 조회를 더한다. 이름·인자는 문서에 없다(확인 필요). 커서·`size + 1`은 위 "모든 API에 공통"의 목록 규칙 |
| `JpaNotificationRepository` (infrastructure, 기존 수정) | 위 조회를 구현한다 |
| `AdminNotificationController` | `GET /admin/api/notifications?status=FAILED&cursor=` · `POST /admin/api/notifications/{id}/retry` — FAILED → PENDING, `attempts = 0` |

- 목록: 응답 항목의 필드는 정해져 있지 않다(확인 필요, `notification` 컬럼 기준 id·type·recipient·attempts·lastError·updatedAt 등)
- `retry`: `UPDATE notification.notification SET status = 'PENDING', attempts = 0, updated_at = now() WHERE id = :id AND status = 'FAILED'`(`version` 컬럼이 있으므로 `version + 1`도 함께, 다른 UPDATE 규칙과 같게). FAILED의 `next_attempt_at`은 마지막 실패 때의 과거 값이라 다음 `dispatchBatch`(10초 주기)가 바로 집어 간다

**6) common.web** — 패키지는 문서에 없어 `common.web`을 권장한다(테스트 `AdminOutboxTest`·`AdminDltTest`가 `common/web`에 있다). 클래스 이름은 문서에 없다(권장: `AdminOutboxController`, `AdminDltController`)

| 클래스 | 규격 |
|---|---|
| `AdminOutboxController` | `GET /admin/api/outbox?status=DEAD&cursor=` — DEAD 행 목록(id, topic, aggregateId, attempts, lastError, createdAt) / `POST /admin/api/outbox/{id}/retry` — DEAD → READY, `attempts = 0`, `next_attempt_at = now` |
| `AdminDltController` | `GET /admin/api/dlt/{topic}?limit=50` — 짧게 쓰는 `KafkaConsumer`(그룹 없이 `assign`)로 `{topic}.dlt` 각 파티션 끝에서 최대 limit개 읽기 → `(partition, offset, key, value 앞 500자, 예외 헤더)` / `POST /admin/api/dlt/{topic}/{partition}/{offset}/replay` — 그 레코드 하나를 읽어 원래 토픽(`.dlt` 제거)으로 같은 key·value를 `send().get()` |
| `AdminDltService` | 요청마다 `KafkaConsumer`를 만들어 쓰는 코드를 컨트롤러가 직접 할지 별도 서비스(권장 `AdminDltService`)로 둘지는 문서에 없다(확인 필요). 순서는 아래 `DLT` |

**outbox** (common)
- 목록: `common.outbox_event`에서 `status = :status`인 행을 `created_at, id` 기준으로 읽는다(`JdbcTemplate`). 응답 항목은 `(UUID id, String topic, UUID aggregateId, int attempts, String lastError, Instant createdAt)` 한 record(이름 권장안 `AdminOutboxResponse`)
- `retry`: `UPDATE common.outbox_event SET status = 'READY', attempts = 0, next_attempt_at = now(), updated_at = now() WHERE id = :id AND status = 'DEAD'`. 1행이면 성공이고 다음 `relayOnce()`(1초 주기)가 집어 간다. 0행이면 id가 없거나 DEAD가 아니다. `WHERE status = 'DEAD'`가 같은 요청을 두 번 보내도(두 번째는 이미 READY/SENT라 0행) 안전하게 하는 장치다

**DLT** (common) — Kafka의 DLT 토픽(3-2가 `{원본토픽}.dlt`로 보낸 실패 메시지)을 읽고 되돌려 보낸다. 경로의 `{topic}`은 **원본 토픽 이름**(예: `shop.shop-opened.v1`)이고 실제로 읽는 토픽이 `{topic}.dlt`다. 두 API 모두 요청마다 `KafkaConsumer<String, String>`을 새로 만들어 `try-with-resources`로 닫는다. 속성은 `bootstrap.servers`(앱과 같은 주소), key·value `StringDeserializer`, `enable.auto.commit=false`이고 **`group.id`는 주지 않는다**(그룹에 속하지 않고 `assign`으로 파티션을 직접 지정하므로 오프셋을 저장하지 않고 운영 consumer에 영향이 없다). 만드는 방법(`KafkaProperties`로 속성을 얻을지 직접 쓸지)은 문서에 없다(확인 필요)
- 목록 `GET /admin/api/dlt/{topic}?limit=50`
  1. `consumer.partitionsFor(dltTopic)`(`List<PartitionInfo>`) → 파티션마다 `new TopicPartition(dltTopic, info.partition())` → `consumer.assign(그 목록)`
  2. `consumer.beginningOffsets(tps)`, `consumer.endOffsets(tps)`(둘 다 `Map<TopicPartition, Long>`) → 파티션마다 `start = Math.max(begin, end - limit)`로 `consumer.seek(tp, start)`
  3. `consumer.poll(Duration.ofSeconds(1))`을 반복해 `ConsumerRecord<String, String>`을 모은다. 파티션마다 `end` 직전 offset까지 읽으면 그 파티션은 끝난 것이다(`start == end`인 빈 파티션은 건너뛴다)
  4. 레코드마다 `(partition(), offset(), key(), value()의 앞 500자, 예외 헤더)`. 예외 헤더는 3-2의 `DeadLetterPublishingRecoverer`가 붙인 것으로 `record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)`·`DLT_EXCEPTION_MESSAGE`의 `value()`(`byte[]`)를 `new String(bytes, UTF_8)`로 바꾼다. 헤더가 없으면 `null`
  - `limit`이 **파티션마다**인지 전체 상한인지, 어떤 헤더를 내려줄지(스택트레이스 헤더는 길다)는 표의 문장("각 파티션 끝에서 최대 limit개")만 있다(확인 필요). 최대값도 없다
- 재처리 `POST /admin/api/dlt/{topic}/{partition}/{offset}/replay` — 입력은 위 목록에서 본 `(partition, offset)`이다
  1. `consumer.assign(List.of(new TopicPartition(topic + ".dlt", partition)))` → `consumer.seek(tp, offset)` → `poll`해서 `record.offset() == offset`인 레코드를 찾는다. 없으면(범위 밖·삭제됨) 대상 없음 응답
  2. `kafkaTemplate.send(topic, record.key(), record.value()).get(5, TimeUnit.SECONDS)` — 3-1 릴레이가 쓰는 `KafkaTemplate<String, String>`이다. `.get()`으로 브로커 확인까지 기다린다(실패하면 예외). `record.value()`는 이벤트 봉투 JSON 그대로라 `eventId`가 같고, key는 `aggregateId`라 원래 파티션으로 간다
  3. 원본 consumer가 다시 받아 `InboxGuard`로 처리한다. 이미 처리된 이벤트면 건너뛰고, 처리에 실패했던 이벤트(그때 `processed_message` INSERT도 롤백됨)면 이번에 처리된다. **DLT의 레코드는 지우지 않으므로** 같은 레코드를 두 번 replay해도 처리 결과는 한 번이다(`AdminDltTest`가 이 경우를 본다)

**7) settlement.web**

| API | 처리 |
|---|---|
| `POST /admin/api/settlements/run?period=yyyy-MM` | `SettlementService.run(YearMonth)`을 실행 → 200 `SettlementRunResult(int settledShops, int paid, int payoutFailed)`. 컨트롤러는 3-4가 만들고 이 단계는 인가(`AdminAuthorizationTest` 11번)만 확인한다 |

**8) 테스트 보조** — `src/test/`

- `AdminDltTest`가 쓰는 "플래그로 성공하게 바꿀 수 있는 테스트 리스너"(규격은 완료 확인 `AdminDltTest` 준비)
- `AdminOutboxTest`의 DEAD 행을 만드는 방법은 문서에 없다(확인 필요: JDBC로 `status = 'DEAD'`로 바꾸는 방법이 있다)

**9) 문서** — `docs/runbook.md` (Claude)

7-2 알림마다 "의미 → 볼 곳(대시보드·Kibana 검색) → 조치(위 API)"

**정해지지 않은 것(확인 필요) 정리** — 이 단계 구현 전에 정할 것
- 대상이 없을 때·상태가 맞지 않을 때의 응답 코드(예: outbox retry 0행, DLT replay의 record 없음, 환불·알림 retry가 FAILED가 아닐 때)
- 쓰기 API의 성공 응답 상태와 본문
- DLT 목록의 응답 모양과 필드 이름
- 환불·알림 목록의 항목 필드

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 이 단계에서 더하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다. 반환 record는 `support/`에 둔다. 7-4도 이 헬퍼를 쓴다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `RefundRow`에 `int pgAttempts` 추가 | `RefundRow`(`status`·`reasonType`·`amount`·`reason`·`rejectReason`·`completedAt`)에 필드를 더하고, `refundRowOf`의 SELECT에 `orders.refund.pg_attempts` 컬럼을 더한다 | `AdminRefundRetryTest` 1·2·3 |
| `paymentCancelOf(String idempotencyKey)` → `PaymentCancelState` | JDBC `SELECT status, failure_code FROM payment.payment_cancel WHERE idempotency_key = ?`. `PaymentCancelState`는 `support/`의 record `(String status, String failureCode)`. 행이 없으면 `null`(`idempotency_key`가 unique라 키마다 행이 하나다) | `AdminRefundRetryTest` |
| `OrderSnapshot`에 결제 필드 추가 | `OrderSnapshot.PaymentState`(`amount`·`cancelledAmount`·`status`·`paymentKey`)에 `UUID id`, `String method`, `Instant approvedAt`(승인 전이면 `null`), `int reconcileAttempts`를 더하고, `snapshotOf`의 결제 쿼리에 `payment.payment`의 `id`·`method`·`approved_at`·`reconcile_attempts`를 더한다(주문 `status`는 3-5가 더했다) | `AdminReconcileTest` |
| `NotificationRow`에 `long version` 추가 | `NotificationRow`(`memberId`·`type`·`channel`·`recipient`·`title`·`status`·`lastError`·`dedupKey`·`attempts`·`sentAt`·`nextAttemptAt`)에 필드를 더하고, `notificationRowsOf`·`notificationRows`의 SELECT에 `notification.notification.version` 컬럼을 더한다 | `AdminNotificationTest` |

이미 있는 헬퍼: `snapshotOf`(2-8a), `cartSnapshotOf`(3-6), `orderLineIdOf`(2-8a), `refundIdOf`(2-8b), `refundRowOf`(2-9), `stockOf`, `createPendingOrder`, `confirmPayment`, `createPaidOrder`, `signup`, `token`, `login`, `changeRole`(3-1), `outboxSnapshotOf`·`OutboxAssert.rows`(3-1), `processedCountOf`(3-2), `countOf`(3-1), `FakeMailSender`·`notificationRowsOf`(3-5).

**확인 값을 읽는 방법**
- 응답: MockMvc `jsonPath`. 목록은 `$.items[n].필드`와 `$.nextCursor`(`CursorPage`). 에러 응답의 코드는 `$.code`.
- DB 상태는 쿼리를 테스트에 쓰지 않고 `TestFixtures`의 읽기 헬퍼 한 번으로 읽는다.
  - 주문·결제: `OrderSnapshot s = testFixtures.snapshotOf(orderId)`(`support/`의 record). 주문은 `s.status()`, 품목은 `s.line(productId).status()`·`.refundedAmount()`, 가게주문은 `s.shopOrderStatus()`, 환불 상태 목록은 `s.refundStatuses()`, 결제는 `s.payment()`의 `.id()`·`.status()`·`.amount()`·`.cancelledAmount()`·`.paymentKey()`·`.method()`·`.approvedAt()`·`.reconcileAttempts()`.
  - 장바구니: `testFixtures.cartSnapshotOf(buyerId)`의 `.quantity(productId)`(항목이 없으면 0)·`.itemCount()`(2-1).
  - 재고: `testFixtures.stockOf(productId)` → `available`, `reserved`, `sold`.
  - outbox 행: 행 id를 아는 것은 `testFixtures.outboxSnapshotOf(outboxId)`(3-1) → `status()`, `attempts()`, `nextAttemptAt()`, `sentAt()`, `lastError()`. 토픽별 건수·payload는 `OutboxAssert.rows(topic)`, 전체 행 수는 `countOf("common.outbox_event")`.
  - 환불: `testFixtures.refundRowOf(refundId)`(2-9) → `status()`, `pgAttempts()`(이 단계가 더한다). 환불의 PG 취소 기록은 `paymentCancelOf(멱등 키)`(이 단계가 더한다) → `status()`, `failureCode()`(없으면 `null`).
  - 알림: `notificationRowsOf(memberId)`(3-5) → `NotificationRow`의 `status()`, `attempts()`, `version()`(이 단계가 더한다).
  - 중복 소비 기록: `processedCountOf(consumer, eventId)`(3-2).
- 가짜 PG가 받은 요청: `pg.requests(경로 접두어)`. 헤더 키는 소문자(`idempotency-key`), 본문의 `cancelAmount`는 `readTree`로 읽어 `asLong()`으로 비교한다.
- 실행 로그: 테스트 클래스에 `@ExtendWith(OutputCaptureExtension.class)`를 달고 `output.getAll()`에서 `"target=" + 대상 + " by=" + adminId` 문자열을 찾는다(로그 형식 `admin action={} target={} by={}`).
- 아직 정해지지 않은 응답(위 "정해지지 않은 것")은 아래에서 "확인 필요"로 적었다. 테스트는 정해진 값에 맞춰 쓰고, 그 전까지는 해당 줄을 `is2xxSuccessful()`/`is4xxClientError()`로 둔다.

**공통 준비**
- ADMIN 토큰: `UUID adminId = signup("admin@test.com")` → `changeRole(adminId, "ADMIN")` → `login("admin@test.com")`. 일반 회원 토큰: `token("user@test.com")`.
- 쓰기 API 호출에는 `Authorization: Bearer {ADMIN 토큰}`을 붙인다. 쓰기 API의 `Idempotency-Key` 요구 여부는 문서에 없다(확인 필요).
- 주문·결제가 필요한 테스트는 판매자 1명, 가게 1개, 상품 `product1`(1,000원)·`product2`(2,000원)·`product3`(3,000원, 재고 각 10), 구매자 1명을 쓴다.

#### `common/web/AdminOutboxTest` (`IntegrationTestSupport`)

공통 준비: 테스트용 토픽 `test.admin-outbox.v1`은 `NewTopic` 빈으로 만든다. `common.outbox_event`에 JDBC `INSERT`로 DEAD 행 X(`aggregate_type` `TEST`, `aggregate_id` K, `event_type` `TestEvent`, `topic` `test.admin-outbox.v1`, `payload` `'{"hello":"admin"}'::jsonb`, `status` `DEAD`, `attempts` 10, `last_error` `kafka timeout`, `next_attempt_at`·`created_at` = 지금 - 1시간)와 SENT 행 S(같은 컬럼, `status` `SENT`, `sent_at` 지금)를 넣는다.

**1. DEAD 목록 조회** — 동작: `GET /admin/api/outbox?status=DEAD`.
- 확인: 200, `$.items.length()` = 1, `$.items[0].id` = X, `.topic` = `test.admin-outbox.v1`, `.aggregateId` = K, `.attempts` = 10, `.lastError` = `kafka timeout`, `.createdAt`이 null이 아니다.

**2. 같은 retry 두 번** — 준비: 5번의 retry와 6번의 `relayOnce()`를 마쳐 X가 SENT인 상태. 동작: `POST /admin/api/outbox/{X}/retry` 다시.
- 응답 4xx(코드는 확인 필요). `outboxSnapshotOf(X)`가 동작 전에 읽은 값과 같다. `relayOnce()` 반환값 0, 토픽의 K 메시지는 1건 그대로.

**3. 없는 id** — 동작: 임의의 UUID로 `POST /admin/api/outbox/{임의 UUID}/retry`.
- 응답 4xx(404 `NOT_FOUND`가 후보, 확인 필요).

**4. 목록 페이지** — 준비: DEAD 행 3개를 `created_at`을 서로 다르게(지금 - 3시간, - 2시간, - 1시간) 넣는다. 동작: `GET /admin/api/outbox?status=DEAD&size=2` → 응답의 `$.nextCursor`를 `cursor`로 `GET ...&size=2&cursor={값}`.
- 첫 응답: `$.items.length()` = 2, `$.nextCursor`가 null이 아니다, `items`가 `createdAt` 오름차순. 둘째 응답: `$.items.length()` = 1, `$.nextCursor` = null. 두 응답의 `id` 합이 3개이고 중복이 없다.

**5. DEAD 행 retry → READY** — 동작: `POST /admin/api/outbox/{X}/retry`.
- 확인: 2xx(상태·본문은 확인 필요). `outboxSnapshotOf(X)`: `status()` = `READY`, `attempts()` = 0. 출력에 `"target=" + X + " by=" + adminId`가 있다.

**6. retry한 행은 다시 발행된다** — 준비: 5번의 retry를 마친 상태. 동작: `outboxRelay.relayOnce()`.
- 확인: 반환값 1. 테스트용 `KafkaConsumer`로 `test.admin-outbox.v1`을 읽으면 key = K, 봉투 `eventId` = X인 메시지가 1건이다.

#### `common/web/AdminDltTest` (`IntegrationTestSupport`)

**준비**: 3-2 테스트의 항상 실패하는 리스너에 성공으로 바꾸는 플래그를 더한 테스트 리스너를 **새 토픽** `test.admin-dlt.v1`(+ `.dlt`, 파티션 3·복제 1)에 단다. 리스너는 `static volatile boolean fail = true`, `AtomicInteger received`(메시지 수신마다 증가), `AtomicInteger processed`(액션 실행마다 증가)를 가지고 `received++` 뒤 `inboxGuard.runOnce("test.admin-dlt", envelope, () -> { if (fail) throw new IllegalStateException("always-fail"); processed++; })`를 부른다. 메시지는 `kafkaTemplate.send("test.admin-dlt.v1", K, 봉투 JSON)`, K는 임의 UUID, 봉투 JSON은 `{"eventId":"E","eventType":"TestEvent","occurredAt":"2026-10-18T00:00:00Z","aggregateId":"K","traceId":"t","payload":{}}`(E는 임의 UUID)이다. 테스트마다 `fail = true`, 두 카운터 0으로 되돌린다.

**1. DLT 목록 조회** — 동작: 메시지를 보낸다 → `Eventually.await(Duration.ofSeconds(20), ...)` 안에서 `GET /admin/api/dlt/test.admin-dlt.v1?limit=50`.
- 응답 200. 항목 중 `key` = K인 것이 정확히 1개다(응답 모양과 필드 이름은 확인 필요. 권장: `partition`, `offset`, `key`, `value`, `exception`). 그 항목의 `value`는 보낸 봉투 JSON과 같은 문자열, 예외 헤더 값에 `always-fail`이 들어 있다(`DLT_EXCEPTION_MESSAGE`). 항목의 `partition`·`offset`을 `P`·`O`로 적어 둔다.

**2. replay로 처리 성공** — 준비: 1번 상태. 동작: `fail = false` → `POST /admin/api/dlt/test.admin-dlt.v1/{P}/{O}/replay`.
- 응답 2xx(상태·본문은 확인 필요). 출력에 `"target=" + "test.admin-dlt.v1/" + P + "/" + O + " by=" + adminId`.
- `Eventually.await(Duration.ofSeconds(10), ...)` 안에서 `received` = 1, `processed` = 1.

**3. 같은 메시지 두 번 replay → 처리 한 번** — 준비: 2번 상태. 동작: 같은 `P`·`O`로 replay를 한 번 더.
- 응답 2xx. `Eventually.await(Duration.ofSeconds(10), ...)` 안에서 `received` = 2. 그 뒤 `processed` = 1 그대로. DLT 목록의 `key` = K 항목은 여전히 1개.

**4. 없는 offset** — 동작: `POST /admin/api/dlt/test.admin-dlt.v1/{P}/9999/replay`.
- 응답 4xx("대상 없음", 코드는 확인 필요). 3초 기다린 뒤에도 `received`가 호출 전과 같다.

#### `order/web/AdminReconcileTest` (`@AutoConfigureMockMvc` + `IntegrationTestSupport`)

공통 준비: 위 주문 준비에서 `product1`만 쓴다. 주문·결제 상태는 확인하는 시점(동작 전, 동작 뒤)마다 `OrderSnapshot s = testFixtures.snapshotOf(orderId)`를 한 번 읽어 확인한다. 호출은 `POST /admin/api/payments/{orderId}/reconcile`(ADMIN).

**1. 승인 기록이 있는 UNKNOWN 결제 → PAID** — 준비: `pg.on("/v1/payments/confirm", Behavior.delay(1000))` → `createPendingOrder(구매자 ID, product1)` → `confirmPayment(구매자 ID, orderId)`(응답 202).
- 동작: reconcile 호출. 응답: 200, `$.orderId` = orderId, `$.status` = `PAID`.
- 상태: `s.status()` = `PAID` / 가짜 PG: `/v1/payments/orders/` 요청 1건 / 출력에 `"target=" + orderId + " by=" + adminId`.

**2. 승인 기록이 없는 UNKNOWN 결제 → PAYMENT_FAILED** — 준비: `pg.on("/v1/payments/confirm", Behavior.drop())` → `createPendingOrder` → `confirmPayment`(202). 동작: reconcile.
- 응답: 200, `$.status` = `PAYMENT_FAILED`. 상태: `s.status()` = `PAYMENT_FAILED` / `cartSnapshotOf(구매자 ID).quantity(product1)` = 1 그대로 / 가짜 PG `/v1/payments/orders/` 요청 1건(404 응답).

**3. 조회도 실패하면 상태 그대로** — 준비: `pg.on("/v1/payments/confirm", Behavior.delay(1000))` + `pg.on("/v1/payments/orders", Behavior.serverError())` → `createPendingOrder` → `confirmPayment`(202). 동작: reconcile.
- 응답: 200, `$.status` = `PAYMENT_IN_PROGRESS`. `s.payment().reconcileAttempts()` = 1.

**4. 없는 주문** — 동작: 임의의 UUID로 reconcile. 응답 4xx(코드는 확인 필요). 가짜 PG 요청 0건.

**5. UNKNOWN 결제 목록 조회** — 준비: 1번과 같은 준비. 동작: `GET /admin/api/payments?status=UNKNOWN`(reconcile 전).
- 확인: 200, `$.items.length()` = 1, `$.items[0].id` = `s.payment().id()`, `.orderId` = orderId, `.amount` = 1000, `.updatedAt`이 null이 아니다.

**6. 같은 reconcile을 한 번 더 호출해도 PAID** — 준비: 1번과 같은 준비로 reconcile을 한 번 호출해 `PAID`가 된 상태. 동작: 같은 호출을 한 번 더.
- 확인: 200, `$.status` = `PAID`.

#### `order/web/AdminRefundRetryTest` (`@AutoConfigureMockMvc` + `IntegrationTestSupport`)

**준비**: 위 주문 준비의 상품 3개를 구매자가 `createPaidOrder`로 결제 완료(총액 6,000). `paymentKey`는 `snapshotOf(orderId).payment().paymentKey()`. `pg.on("/v1/payments/" + paymentKey, Behavior.reject("NOT_CANCELABLE_AMOUNT"))` 후 구매자가 `product1` 품목을 취소한다(`POST /api/orders/{orderId}/lines/{lineId}/cancel`, `Idempotency-Key` 새 UUID, 본문 `{"reason":"단순 변심"}`, `lineId`는 `orderLineIdOf(orderId, product1)`) → 응답 502, `$.code` = `PG_ERROR`. 환불 ID는 `refundIdOf(lineId)`(2-8b). 이 시점에 가짜 PG `/cancel` 요청은 1건이다.

**1. FAILED 환불 재시도 → COMPLETED**
- 동작: `pg.on("/v1/payments/" + paymentKey, Behavior.approve())`로 정상 응답으로 되돌린 뒤 `POST /admin/api/refunds/{refundId}/retry`. 응답 2xx(상태·본문은 확인 필요).
- `refundRowOf(refundId)`: `status()` = `COMPLETED`, `pgAttempts()` = 1.
- 가짜 PG `/cancel` 요청 총 2건. 둘째 `idempotency-key` = `{refundId}-r1`.
- `s = snapshotOf(orderId)`: `s.line(product2)`, `s.line(product3)` → `PAID`·0.
- 출력에 `"target=" + refundId + " by=" + adminId`.

**2. 재시도를 두 번 누르면 두 번째는 거부** — 준비: 1번 끝 상태. 동작: 같은 retry 다시.
- 응답 4xx(코드는 확인 필요, 후보 409 `INVALID_STATE_TRANSITION`). `refundRowOf(refundId)`: `status()` = `COMPLETED`, `pgAttempts()` = 1 그대로. 가짜 PG `/cancel` 요청 2건 그대로. `paymentCancelOf(refundId + "-r1").status()` = `DONE` 그대로. `snapshotOf(orderId).payment().cancelledAmount()` = 1000 그대로.

**3. FAILED가 아닌 환불 재시도 거부** — 준비: 위 준비 대신 취소가 성공한 환불(`COMPLETED`)을 둔다(`pg` 기본 동작으로 취소). 동작: 그 환불에 retry.
- 응답 4xx. `refundRowOf(refundId)`: `status()` = `COMPLETED`, `pgAttempts()` = 0. 가짜 PG `/cancel` 요청 1건.

**4. FAILED 환불 목록 조회** — 준비: 위 준비(`FAILED` 환불 1건). 동작: `GET /admin/api/refunds?status=FAILED`(retry 전).
- 확인: 200, `$.items.length()` = 1, 항목의 환불 ID가 refundId(필드 이름은 확인 필요, 권장 `id`).

#### `notification/web/AdminNotificationTest` (`IntegrationTestSupport`)

**준비**: `notification.notification`에 JDBC `INSERT`로 FAILED 행 N(`member_id` `memberId`(임의 UUID), `type` `ORDER_PAID`, `channel` `EMAIL`, `recipient` `buyer@test.com`, `title` `t`, `body` `b`, `dedup_key` 임의 문자열, `status` `FAILED`, `attempts` 5, `last_error` `smtp down`, `next_attempt_at` = 지금 - 1시간, `version` 0, `created_at`·`updated_at` = 지금)과 SENT 행 1개(다른 `member_id`)를 넣는다. N의 상태는 `notificationRowsOf(memberId).get(0)`으로 읽는다.

**1. FAILED 알림 목록 조회** — 동작: `GET /admin/api/notifications?status=FAILED`.
- 확인: 200, `$.items.length()` = 1, 항목의 알림 ID가 N(필드 이름은 확인 필요).

**2. FAILED 알림 retry → PENDING** — 동작: `POST /admin/api/notifications/{N}/retry`.
- 확인: 2xx. N의 행: `status()` = `PENDING`, `attempts()` = 0, `version()` = 1.

**3. retry한 알림은 다시 발송된다** — 준비: 2번의 retry를 마친 상태. 동작: `notificationDispatcher.dispatchBatch()`.
- 확인: 반환값 1. N의 행 `status()` = `SENT`.

**4. 같은 retry 한 번 더** — 준비: 3번까지 마쳐 N이 `SENT`인 상태. 동작: `POST /admin/api/notifications/{N}/retry` 다시.
- 확인: 4xx(코드는 확인 필요), N의 행 `status()` = `SENT` 그대로.

#### `common/web/AdminAuthorizationTest` (`@ExtendWith(OutputCaptureExtension.class)` + `@AutoConfigureMockMvc` + `IntegrationTestSupport`)

**준비**: 일반 회원 토큰(`token("user@test.com")`), DEAD outbox 행 X(`AdminOutboxTest`와 같은 INSERT). 나머지 경로의 ID는 임의 UUID를 쓴다. 각 요청을 일반 회원 토큰으로 한 번, `Authorization` 헤더 없이 한 번 보낸다.

| # | 요청 | 일반 회원 토큰 | 토큰 없음 |
|---|---|---|---|
| 1 | `GET /admin/api/outbox?status=DEAD` | 403, `$.code` = `FORBIDDEN` | 401, `$.code` = `UNAUTHORIZED` |
| 2 | `POST /admin/api/outbox/{X}/retry` | 〃 | 〃 |
| 3 | `GET /admin/api/dlt/test.admin-dlt.v1?limit=50` | 〃 | 〃 |
| 4 | `POST /admin/api/dlt/test.admin-dlt.v1/0/0/replay` | 〃 | 〃 |
| 5 | `GET /admin/api/payments?status=UNKNOWN` | 〃 | 〃 |
| 6 | `POST /admin/api/payments/{임의 UUID}/reconcile` | 〃 | 〃 |
| 7 | `GET /admin/api/refunds?status=FAILED` | 〃 | 〃 |
| 8 | `POST /admin/api/refunds/{임의 UUID}/retry` | 〃 | 〃 |
| 9 | `GET /admin/api/notifications?status=FAILED` | 〃 | 〃 |
| 10 | `POST /admin/api/notifications/{임의 UUID}/retry` | 〃 | 〃 |
| 11 | `POST /admin/api/settlements/run?period=2026-09` | 〃 | 〃 |

- 부수효과 없음: 11개 호출을 마친 뒤 `outboxSnapshotOf(X)`가 호출 전에 읽은 값과 같고 `status()` = `DEAD`다. 출력에 `admin action=`이 없다.

**리뷰 때 물어볼 것**: 운영 API가 실수로 두 번 호출되면 안전한가? 각각 무엇이 멱등을 보장하나?

---

## 7-4. E2E 시나리오 · 불변식 점검 (L)

**목표**: 시나리오 S1~S7을 테스트로 증명하고, 시나리오가 끝날 때마다 불변식(INV-01~08)을 점검한다(INV-09는 최종적 일관성이라 아래 참고).

**정책**: E2E 테스트는 실제 API(MockMvc)와 실제 Kafka·Redis·Postgres·가짜 PG로 돌린다. 잡과 릴레이는 테스트에서 직접 호출한다. 위치 `src/test/java/com/myroutine/e2e/`.

### 할 일

**구현 순서**: 1) `InvariantChecker`와 자체 테스트 → 2) 시나리오 클래스(S1 → S7 → 장애) → 3) CI 시간 기록.

**1) 불변식 점검 유틸** — `src/test/java/com/myroutine/support/InvariantChecker.java`

- **무엇인가**: DB를 직접 조회해 "깨지면 안 되는 규칙"(INV)이 지켜졌는지 확인하는 테스트 도우미. 시나리오가 끝난 DB에서 규칙을 어긴 행을 찾는다. 모듈 경계 규칙은 테스트 코드에 적용되지 않아 `orders`·`payment`·`product`·`settlement` 스키마를 한 클래스에서 읽어도 된다
- **형태**(권장안): `TestFixtures`처럼 `@Component @RequiredArgsConstructor`, 주입은 `JdbcTemplate`. 메서드는 모두 `void`이고 어긋난 행을 찾아 있으면 AssertJ로 실패시킨다 — 어긋난 행의 id를 `queryForList(sql, ...)`로 모아 `assertThat(rows).as("메시지").isEmpty()`. 메시지에 id를 담아야 "어떤 행이 어긋났는지" 보인다
- **SQL 쓰는 법**: "위반 행을 SELECT"하는 쿼리로 쓴다(예: 주문별 `total_amount`와 품목 `line_amount` 합을 비교해 다른 주문만 조회). 테이블·컬럼은 각 모듈 마이그레이션(`src/main/resources/db/migration/{모듈}/`)에서 확인한다. 주문 스키마 이름은 `orders`다(`orders.orders`, `orders.order_line`)
- **누가 부르나**: 시나리오 테스트 끝의 `checkAll()`과 `InvariantChecker` 자체 테스트(완료 확인)

| 메서드 | SQL 요지 | INV |
|---|---|---|
| `orderAmounts()` | 주문마다 `total_amount = Σ line_amount`, PAID 주문은 `payment.amount = total_amount` | INV-01 |
| `paymentsReflected()` | `APPROVED`·`PARTIAL_CANCELLED`·`CANCELLED`인 결제의 주문이 PAID이거나, 결제가 CANCELLED(보상)이고 주문이 PAYMENT_FAILED | INV-02 |
| `stockBalance()` | `available + reserved + sold = received`, 모두 ≥ 0, `received` = `stock_movement`의 `RECEIVE`·`ADJUST` 수량 합, `reserved` = `stock_reservation`의 `HELD` 수량 합(7-2 `StockCheckJob`의 SQL이 0건) | INV-03·04 |
| `refundLimits()` | `refunded_amount ≤ line_amount`, `cancelled_amount ≤ amount`, 주문별 COMPLETED 환불 합 ≤ 주문 총액 | INV-06 |
| `settlementOnce()` | CONFIRMED 품목마다 `settlement_item` 정확히 1행(consumer가 따라잡은 뒤), 정산된 item은 정산 1건에만 | INV-07 |
| `subscriptionCycles()` | `subscription_cycle`에서 `(subscription_id, cycle_date)`마다 행이 1개이고, `status = 'PAID'`인 회차의 `order_id` 주문이 `PAID`이며 한 주문이 두 회차에 연결되지 않는다 | INV-08 |
| `checkAll()` | 위 전부. 실패하면 어떤 행이 어긋났는지 메시지에. **INV-09**(SELLER 권한 ↔ 가게)는 최종적 일관성이라 시나리오 끝에서 바로 검사하면 consumer가 따라잡기 전에 실패할 수 있어 여기에 넣지 않는다(3-1 consumer 테스트의 `Eventually`로 확인한다) | INV-01~08 |

**2) 시나리오** — `src/test/java/com/myroutine/e2e/` (각 테스트 끝에 `checkAll()`)

**테스트 하나의 뼈대**: 클래스는 `@AutoConfigureMockMvc` + `extends IntegrationTestSupport`(Postgres·가짜 PG `pg`·테이블 초기화가 이미 있다). 한 흐름은 아래 세 가지를 순서대로 섞어 쓴다

| 쓰는 것 | 용도 | 출처 |
|---|---|---|
| `mockMvc.perform(...)` | 사용자가 하는 일(체크아웃, 결제 승인, 취소, 발송 등)은 **실제 API**로 | 위 정책. 토큰은 `fixtures.login(email)`(`signup`한 이메일. `token(email)`은 가입까지 해서 중복 가입 예외가 난다) |
| `TestFixtures`(`fixtures.signup`, `openShop`, `registerProduct`, `addToCart`, `checkout`, ...) | 흐름의 준비 단계(회원·가게·상품·장바구니)를 API 없이 빠르게 만든다 | 1-9 이후 쌓인 헬퍼(Part 5의 `registerBillingKey`·`registerSubscribableProduct`·`createSubscription`, Part 6의 `createConfirmedLine` 포함) |
| 아래 표의 메서드를 **직접 호출** | 스케줄이 꺼진 테스트에서 "시간이 지나 잡이 돈 것"을 흉내 낸다 | 아래 표 |
| `pg.on(...)`·`pg.markApproved(...)`, `Eventually.await(...)` | 가짜 PG의 응답 설정, 비동기(consumer) 반영 대기 | 2-3·3-1 |

시나리오 흐름에서 "잡"·"릴레이"라고 적힌 단계는 이 메서드를 부르는 것이다

| 단계 | 호출 | 정의 |
|---|---|---|
| Outbox 릴레이 | `outboxRelay.relayOnce()` → `int` — 이벤트를 Kafka로 보낸다. consumer는 비동기라 반영은 `Eventually`로 기다린다 | 3-1 |
| 주문 만료 | `orderExpirationService.expireBatch()` → `int`(만료시킨 주문 수) (대상은 `PENDING_PAYMENT`이면서 `expires_at`이 지금보다 과거인 주문이라 JDBC로 `expires_at`을 과거로) | 2-5 |
| 결제 대사 | `orderReconcileService.reconcileBatch()` → `int`(`PAID`·`PAYMENT_FAILED`로 끝낸 주문 수) (대상은 `PAYMENT_IN_PROGRESS`이면서 `updated_at`이 30초 전보다 과거인 주문이라 JDBC로 `updated_at`을 30초 전보다 과거로) | 2-6 |
| 자동 구매확정 | `autoConfirmService.confirmBatch()` → `int` (수동 확정 API를 써도 된다) | 2-10 |
| 정산 | `settlementService.run(YearMonth month)` → `SettlementRunResult(int settledShops, int paid, int payoutFailed)` (지급까지 포함) | 3-4 |
| 알림 발송 | `notificationDispatcher.dispatchBatch()` → `int`(`SENT`로 바꾼 알림 수) | 3-5 |
| 구독 회차 | `subscriptionCycleService.runDue()` → `int`(회차를 만든 구독 수) | Part 5 |
| Kafka 중단·재개 | `kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec()` / `unpauseContainerCmd` | 3-1 `OutboxRelayTest`. `kafka` 필드가 `IntegrationTestSupport`의 package-private이면 `e2e` 패키지에서 못 쓴다 — 접근을 열지 헬퍼를 둘지는 확인 필요 |

`run`만 인자(정산 대상 월)를 받고 나머지는 인자가 없다

| ID | 클래스 | 흐름 |
|---|---|---|
| S1 | `CheckoutPaymentE2ETest` | 가게 2곳·상품 3개 장바구니 → 체크아웃 → 카드 결제 승인 → 판매자 발송·배송완료 → 구매확정 → 정산 → 지급(가짜 게이트웨이)·알림 |
| S2 | `AbandonedPaymentE2ETest` | 체크아웃 후 결제창 이탈 → 만료 잡 → 재고 원복 |
| S3 | `UncertainPaymentE2ETest` | 승인 타임아웃(202) → 대사 → PAID / 승인 기록 없는 타임아웃 → 대사 → PAYMENT_FAILED |
| S4 | `LineCancelE2ETest` | 품목 3개 주문의 품목 1개 취소 → 그 금액만 PG 부분취소 → 나머지 품목 정상 진행 → 다른 품목 하나 더 취소 → 누적 취소액 정확 |
| S5 | `SubscriptionE2ETest` | 빌링키 등록 → 구독 → 회차 잡(성공) → 다음 회차 거절 3번 → SUSPENDED → 재개 |
| S6 | `SettlementE2ETest` | 여러 가게 구매확정 → 정산 잡 두 번 → 정산 1회, 지급 1회 |
| S7 | `ShopCloseE2ETest` | 진행 중 주문이 있는 가게 폐업 거부 → 주문 종결 → 구독 상품 단종 → 폐업 → 상품 단종·SELLER 회수 |
| 장애 | `KafkaOutageE2ETest` | Kafka pause 상태에서 체크아웃·결제 → outbox READY 누적 → unpause → 릴레이 → 모든 consumer 처리 완료 → `checkAll()` |

**3) CI**: 전체 테스트 시간을 PR에 기록한다. 읽는 곳과 합격 기준은 완료 확인 "CI 실행 시간"에 있다.

### 완료 확인

**테스트 준비**

아래 시나리오를 쓰기 전에 만든다. 이 단계에서 더하는 헬퍼는 다음과 같다(`support/TestFixtures`에 더한다. 이름·인자는 권장안이라 쓰다 바꿔도 된다. 반환 record는 `support/`에 둔다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `unsentOutboxCount()` → `int` | JDBC `SELECT count(*) FROM common.outbox_event WHERE status <> 'SENT'` | S1 7번, `KafkaOutageE2ETest` 준비·6 |
| `notificationCounts()` → `NotificationCounts` | 3-5의 `notificationRows()`를 `type`별·`status`별로 센 값(쿼리 없음). `NotificationCounts`의 메서드는 `int type(String type)`·`int status(String status)`이고, 건수를 돌려주며 행이 없으면 0이다 | S1·S2·S4·S6, `KafkaOutageE2ETest` |
| `OrderSnapshot`에 `reservationStatuses` 추가 | `snapshotOf`에 쿼리 `SELECT status FROM product.stock_reservation WHERE order_id = ?`를 더해 `OrderSnapshot`에 `List<String> reservationStatuses`로 담는다 | S2 2번 |

이미 있는 헬퍼: `snapshotOf`·`orderLineIdOf`·`shopOrderIdOf`·`createPaidOrder`·`createDeliveredOrder`(Part 2), `cartSnapshotOf`(3-6), `stockOf`, `countOf`(3-1), `settlementSnapshotOf`·`settlementItemSnapshotOf`·`unsettledItemCount`(3-4a), `notificationRows`(3-5), `shopSnapshotOf`·`memberSnapshotOf`·`productSnapshotOf`, `registerSubscribableProduct`·`registerBillingKey`·`createSubscription`(5-2), `subscriptionSnapshotOf`(5-2)·`cyclesOf`·`setSubscriptionNextRunDate`(5-3), `OutboxAssert.rows`·`Eventually`(3-1), `FakePayoutGateway`(3-4b), `FakeMailSender`(3-5).

**확인 값을 읽는 방법**
- 응답: MockMvc `jsonPath`. 에러 응답의 코드는 `$.code`, 부가 정보는 `$.details.필드`.
- DB 상태는 쿼리를 시나리오에 쓰지 않고 `TestFixtures`의 읽기 헬퍼 한 번으로 읽는다. 이 단계가 새로 만드는 헬퍼는 위 "테스트 준비"의 표에 있다.
  - 주문·결제: `OrderSnapshot s = testFixtures.snapshotOf(orderId)`(`support/`의 record)로 주문 `s.status()`, 품목 `s.line(productId).status()`·`.refundedAmount()`·`.confirmedAt()`, 가게주문 `s.shopOrderStatuses()`(하나면 `s.shopOrderStatus()`), 예약 `s.reservationStatuses()`, 환불 `s.refundStatuses()`, 결제 `s.payment().amount()`·`.cancelledAmount()`·`.status()`를 읽는다.
  - 장바구니: `testFixtures.cartSnapshotOf(buyerId)`의 `.quantity(productId)`·`.itemCount()`(2-1).
  - 재고: `testFixtures.stockOf(productId)` → `available`, `reserved`, `sold`.
  - outbox: 토픽별 건수·`payload` 필드·`status`·`attempts`는 `OutboxAssert.rows(topic)`(3-1), 미발행 행 수는 `unsentOutboxCount()`.
  - 정산: `settlementSnapshotOf(shopId, month)`(3-4a)의 `status()`·`grossAmount()`·`feeAmount()`·`netAmount()`·`itemCount()`·`feeRateBp()`·`payoutReference()`·`paidAt()`, 정산 전 품목은 `settlementItemSnapshotOf(orderLineId)`(3-4a)의 `amount()`·`settlementId()`와 `unsettledItemCount()`(3-4a), 정산 행 수는 `countOf("settlement.settlement")`(3-1).
  - 구독: `subscriptionSnapshotOf(subscriptionId)`(5-2)의 `status()`·`consecutiveFailures()`·`nextRunDate()`·`endReason()`, 회차 목록은 `cyclesOf(subscriptionId)`(5-3) → `CycleRow`의 `cycleDate()`·`status()`·`orderId()`·`appliedUnitPrice()`.
  - 알림: `notificationCounts()`(`type(...)`·`status(...)`로 건수).
  - 가게·회원·상품: `shopSnapshotOf(shopId)`의 `status()`·`closedAt()`, `memberSnapshotOf(memberId).role()`, `productSnapshotOf(productId).status()`(각각 `support/`의 `ShopSnapshot`·`MemberSnapshot`·`ProductSnapshot` record).
  - 한 곳에서만 쓰는 값(S5 1번의 구독 주문 `orders.orders.type`·`total_amount`)은 JDBC 한 줄로 읽는다.
- 가짜 서비스: PG는 `pg.requests(경로 접두어)`(헤더 키 소문자, 본문 `cancelAmount`는 `readTree`로 읽어 `asLong()`), 메일은 `fakeMailSender.sent()`, 지급은 `fakePayoutGateway.calls()` → `List<PayoutCall>`(`record PayoutCall(UUID settlementId, UUID shopId, long amount)`, 호출 순서).
- 비동기 반영: "릴레이+대기(조건)"은 `outboxRelay.relayOnce()` 뒤 `Eventually.await(Duration.ofSeconds(10), 조건)`이다. 조건은 아래 각 단계가 적은 DB 값이다.
- 시나리오 클래스의 주입: `MockMvc`, `TestFixtures`, `InvariantChecker`, `JdbcTemplate`, `OutboxRelay`, `OrderExpirationService`, `OrderReconcileService`, `SettlementService`, `NotificationDispatcher`, `SubscriptionCycleService`, `Clock`, `BusinessProperties`.

**공통 준비**: 아래 시나리오마다 새 데이터를 만든다(`IntegrationTestSupport`가 테스트마다 테이블을 비운다). 판매자·구매자는 `signup`, 가게는 `openShop`, 상품은 `registerProduct(sellerId, shopId, 가격, 10)`(재고 10)이다. 토큰은 `login(이메일)`로 받는다(`token(이메일)`은 가입까지 해서 `signup`한 이메일로 부르면 중복 가입 예외가 난다). 배송지는 `address(구매자 ID)`. 모든 요청의 `Authorization: Bearer {토큰}`, 쓰기 요청의 `Idempotency-Key`는 새 UUID(`/payment/confirm`은 `orderId`)다. 날짜 `today`는 `LocalDate.now(clock.withZone(businessProperties.businessZone()))`다. 각 시나리오의 **마지막 줄은 `invariantChecker.checkAll()`이 예외 없이 끝나는 것**이다.

#### `e2e/InvariantCheckerTest` (`InvariantChecker` 자체 테스트)

메서드마다 테스트 한 개이고 케이스마다 데이터를 새로 만든다. 순서: ① 정상 데이터에서 해당 메서드와 `checkAll()`이 예외 없이 끝난다 → ② 아래 JDBC로 깨뜨린다 → ③ 해당 메서드가 `AssertionError`를 던지고 `hasMessageContaining(id.toString())`이 통과한다 → ④ `checkAll()`도 `AssertionError`를 던진다.

| 메서드 | 정상 데이터 | 깨뜨리는 JDBC | 메시지에 담긴 id |
|---|---|---|---|
| `orderAmounts()` | 상품 1,000·2,000·3,000원을 `createPaidOrder`(총액 6,000, 결제 6,000) | (a) `UPDATE orders.orders SET total_amount = 6001 WHERE id = ?` / (b) `UPDATE payment.payment SET amount = 6001 WHERE order_id = ?` — 각각 따로 새 데이터로 | 주문 ID |
| `paymentsReflected()` | 위와 같은 `createPaidOrder` | `UPDATE orders.orders SET status = 'PAYMENT_FAILED' WHERE id = ?` | 주문 ID |
| `stockBalance()` | `registerProduct(sellerId, shopId, 1000, 10)` | `UPDATE product.stock SET received = received + 5, available = available + 5 WHERE product_id = ?` | 상품 ID |
| `refundLimits()` | 위 주문에서 `product1`(1,000원) 품목을 취소 API로 취소(200 `COMPLETED`) | `UPDATE orders.refund SET amount = 6001 WHERE order_id = ?` | 주문 ID |
| `settlementOnce()` | `createDeliveredOrder` 주문의 품목을 구매확정 API로 확정 → 릴레이+대기(`settlement.settlement_item` 1행) | `DELETE FROM settlement.settlement_item WHERE order_line_id = ?` | 품목(order_line) ID |
| `subscriptionCycles()` | 구독 1건의 회차를 `runDue()`로 성공(회차 `PAID`, 주문 `PAID`) | `UPDATE orders.orders SET status = 'PAYMENT_FAILED' WHERE id = {회차의 order_id}` | 회차 ID |

#### 시나리오 S1: `e2e/CheckoutPaymentE2ETest`

**준비**: 가게 A(판매자 `sellerA`)의 `productA1` 10,000원·`productA2` 5,000원, 가게 B(판매자 `sellerB`)의 `productB1` 30,000원, 구매자 1명. 장바구니: `addToCart(구매자 ID, productA1, 1)`, `addToCart(구매자 ID, productA2, 2)`, `addToCart(구매자 ID, productB1, 1)`.

1. **체크아웃** — `POST /api/orders/checkout` 본문 `{"cartItemIds":[세 항목], "addressId": ...}` → 201, `$.status` = `PENDING_PAYMENT`, `$.totalAmount` = 50000, `$.shopOrders.length()` = 2.
2. **결제 승인** — `POST /api/orders/{orderId}/payment/confirm` 본문 `{"paymentKey":"pk_s1_{orderId}","pgOrderId": 1번 $.pgOrderId,"amount":50000}` → 200, `$.status` = `PAID`. `s = snapshotOf(orderId)`: `s.shopOrderStatuses()` = [`PAID`, `PAID`] / `cartSnapshotOf(구매자 ID).itemCount()` = 0.
3. **outbox와 알림** — `OutboxAssert.rows("order.order-paid.v1")` 1건. 릴레이+대기(`notificationCounts().type("ORDER_PAID")` = 1, `.type("SHOP_ORDER_RECEIVED")` = 2) → `notificationDispatcher.dispatchBatch()`로 알림 발송.
4. **발송·배송완료** — 가게마다 판매자 토큰으로 `POST /api/shops/{shopId}/orders/{shopOrderId}/ship` 본문 `{"carrier":"CJ대한통운","trackingNumber":"1234567890"}` → 200; `POST .../deliver` → 200. `s = snapshotOf(orderId)`: `s.shopOrderStatuses()` = [`DELIVERED`, `DELIVERED`]. outbox `order.shop-order-shipped.v1` 2건, `order.shop-order-delivered.v1` 2건.
5. **구매확정** — 구매자 토큰으로 품목 3개 각각 `POST /api/orders/{orderId}/lines/{lineId}/confirm` → 200. `s = snapshotOf(orderId)`: `s.shopOrderStatuses()` = [`COMPLETED`, `COMPLETED`]. outbox `order.order-line-confirmed.v1` 3건. 릴레이+대기(`unsettledItemCount()` = 3) 뒤 `settlementItemSnapshotOf(orderLineIdOf(orderId, 상품))`의 `amount()` — `productA1` 10000, `productA2` 10000, `productB1` 30000.
6. **정산** — `month = YearMonth.from(s.line(productA1).confirmedAt().atZone(businessZone))`(`s`는 5번의 `snapshotOf`)로 `settlementService.run(month)` → `SettlementRunResult(settledShops=2, paid=2, payoutFailed=0)`. `countOf("settlement.settlement")` = 2. `settlementSnapshotOf(shopA, month)` → `grossAmount` 20000, `itemCount` 2, `status` `PAID` / `settlementSnapshotOf(shopB, month)` → 30000, 1, `PAID`. `unsettledItemCount()` = 0. `FakePayoutGateway` 호출 2건(금액 19000, 28500).
7. **지급 알림** — outbox `settlement.settlement-paid.v1` 2건 → 릴레이+대기(`notificationCounts().type("SETTLEMENT_PAID")` = 2) → `dispatchBatch()`. 최종 `notificationCounts()`의 `type`별 건수는 3번의 `ORDER_PAID` 1·`SHOP_ORDER_RECEIVED` 2에 더해 `WELCOME` 3, `SHIPPED` 2, `DELIVERED` 2, `SETTLEMENT_PAID` 2로 합계 12건이고 `status("SENT")` = 12. `unsentOutboxCount()` = 0.
8. `checkAll()`.

#### 시나리오 S2: `e2e/AbandonedPaymentE2ETest`

**준비**: 판매자, 가게, 상품 1개(10,000원), 구매자. 동작: `POST /api/orders/checkout`(상품 1개, 수량 1) → 201 후 결제 승인을 하지 않는다.
1. `setOrderExpired(orderId, 지금 - 1분)` → `orderExpirationService.expireBatch()` 반환값 1.
2. `s = snapshotOf(orderId)`: `s.status()` = `EXPIRED` / `cartSnapshotOf(구매자 ID).quantity(productId)` = 1 그대로.
3. `OutboxAssert.rows("order.order-expired.v1")` 1건, `payload().get("reason").asText()` = `EXPIRED`. 릴레이+대기(`notificationCounts().type("ORDER_CLOSED")` = 1).
4. `expireBatch()` 한 번 더 → 반환값 0, `order.order-expired.v1` 1건 그대로.
5. `checkAll()`.

#### 시나리오 S3: `e2e/UncertainPaymentE2ETest`

공통 준비: 판매자, 가게, 상품 1개(10,000원), 구매자. 아래 두 테스트 메서드가 각각 새 주문을 만든다.

**승인은 됐지만 응답이 늦은 결제** — 준비: `pg.on("/v1/payments/confirm", Behavior.delay(1000))`.
1. 체크아웃 → `POST /api/orders/{id}/payment/confirm` → 202, `$.status` = `PAYMENT_IN_PROGRESS`.
2. `setOrderUpdatedAt(orderId, 지금 - 1분)` → `orderReconcileService.reconcileBatch()` 반환값 1.
3. `s = snapshotOf(orderId)`: `s.status()` = `PAID` / `OutboxAssert.rows("order.order-paid.v1")` 1건.
4. `checkAll()`.

**승인 기록이 없는 결제** — 준비: `pg.on("/v1/payments/confirm", Behavior.drop())`.
1. 체크아웃 → confirm → 202, `$.status` = `PAYMENT_IN_PROGRESS`.
2. `setOrderUpdatedAt(orderId, 지금 - 1분)` → `reconcileBatch()` 반환값 1.
3. `s = snapshotOf(orderId)`: `s.status()` = `PAYMENT_FAILED` / `cartSnapshotOf(구매자 ID).quantity(productId)` = 1 그대로 / `OutboxAssert.rows("order.order-expired.v1")` 1건, `payload().get("reason").asText()` = `PAYMENT_FAILED`.
4. `checkAll()`.

#### 시나리오 S4: `e2e/LineCancelE2ETest`

**준비**: 한 가게의 `product1` 1,000원·`product2` 2,000원·`product3` 3,000원(재고 각 10), 구매자가 3개를 수량 1씩 `createPaidOrder`(총액 6,000). 품목 ID는 `orderLineIdOf(orderId, productId)`.
1. **첫 취소** — `POST /api/orders/{orderId}/lines/{line1}/cancel` 본문 `{"reason":"단순 변심"}` → 200, `$.status` = `COMPLETED`, `$.amount` = 1000.
2. **두 번째 취소** — `line2` 취소 → 200, `$.status` = `COMPLETED`, `$.amount` = 2000. `s = snapshotOf(orderId)`: `s.payment().cancelledAmount()` 3000.
3. **남은 품목 정상 진행** — 판매자가 발송·배송완료 API, 구매자가 `product3` 품목 구매확정 API → `s.line(product3)` = `CONFIRMED`·`refundedAmount` 0, `s.shopOrderStatus()` = `COMPLETED`.
4. **이벤트** — `OutboxAssert.rows("order.order-line-refunded.v1")` 2건(`payload().get("amount").asText()`가 1000, 2000), `"order.order-line-confirmed.v1"` 1건(`payload().get("amount").asText()` = 3000). 릴레이+대기(`notificationCounts().type("REFUNDED")` = 2, `settlementItemSnapshotOf(orderLineIdOf(orderId, product3)).amount()` = 3000, `countOf("settlement.settlement_item")` = 1).
5. `checkAll()`.

#### 시나리오 S5: `e2e/SubscriptionE2ETest`

**준비**: 판매자, 가게, 구독 상품 `registerSubscribableProduct(sellerId, shopId, 10000, 20)`, 구매자, `billingKeyId = registerBillingKey(구매자 ID)`, `subscriptionId = createSubscription(구매자 ID, 상품 ID, billingKeyId)`(주기 WEEKLY 1 = 월요일, 수량 1). 아래 "다시 실행"은 `UPDATE orders.subscription SET next_run_date = today`(= `setSubscriptionNextRunDate`)와 이미 있는 회차를 어제로 미루는 `UPDATE orders.subscription_cycle SET cycle_date = cycle_date - 1 WHERE subscription_id = ?`다.

1. **성공 회차** — `setSubscriptionNextRunDate(subscriptionId, today)` → `subscriptionCycleService.runDue()` 반환값 1. `cyclesOf(subscriptionId)` 1건 — `cycleDate()` = today, `status()` = `PAID`, `appliedUnitPrice()` = 10000, `orderId()` = 구독 주문 ID(`subOrderId`로 둔다) / 구독 주문: `snapshotOf(subOrderId)`의 `status()` = `PAID` / `subscriptionSnapshotOf(subscriptionId)`: `consecutiveFailures()` = 0, `nextRunDate()`가 today보다 크고 today + 7 이하이며 `getDayOfWeek()` = `MONDAY`.
2. **거절 1회** — `pg.on("/v1/billing/bk_", Behavior.reject("REJECT_CARD_COMPANY"))` → 다시 실행 → `runDue()` 반환값 1. `cyclesOf(subscriptionId)`의 마지막 회차 `status()` = `FAILED`, 그 회차의 주문 `snapshotOf(마지막 회차의 orderId()).status()` = `PAYMENT_FAILED` / `subscriptionSnapshotOf(subscriptionId)`: `consecutiveFailures()` = 1, `nextRunDate()` = today + 1 / `OutboxAssert.rows("order.subscription-cycle-failed.v1")` 1건, `payload().get("consecutiveFailures").asText()` = 1.
3. **거절 2회** — 다시 실행 → 반환값 1. `subscriptionSnapshotOf(subscriptionId)`: `consecutiveFailures()` = 2, `order.subscription-cycle-failed.v1` 누적 2건.
4. **거절 3회** — 다시 실행 → 반환값 1. `subscriptionSnapshotOf(subscriptionId)`: `status()` = `SUSPENDED`, `consecutiveFailures()` = 3 / `cyclesOf(subscriptionId)`의 `status()` 목록 = [`PAID`, `FAILED`, `FAILED`, `FAILED`](생성 순). outbox `order.subscription-status-changed.v1` 1건: `payload().get("from").asText()` = `ACTIVE`, `payload().get("to").asText()` = `SUSPENDED`. `order.subscription-cycle-failed.v1`은 2건 그대로.
5. **정지 중에는 실행되지 않음** — 다시 실행 → `runDue()` 반환값 0. `cyclesOf(subscriptionId)`의 `status()` 목록이 4번과 같다.
6. **재개** — 구매자 토큰으로 `POST /api/subscriptions/{subscriptionId}/resume` → 200. `subscriptionSnapshotOf(subscriptionId)`: `status()` = `ACTIVE`, `consecutiveFailures()` = 0, `nextRunDate()` > today.
7. `checkAll()`.

#### 시나리오 S6: `e2e/SettlementE2ETest`

**준비**: 가게 3곳(판매자 3명)의 상품 `productA` 10,000원·`productB` 20,000원·`productC` 30,000원, 구매자 1명. 가게마다 `createDeliveredOrder(구매자 ID, 판매자 ID, 가게 ID, 상품 ID)` → 구매자 토큰으로 품목 3개를 구매확정 API로 확정(각 200, `$.status` = `CONFIRMED`) → 릴레이+대기(`unsettledItemCount()` = 3).
1. `month = YearMonth.from(snapshotOf(세 주문 중 하나).line(그 주문의 상품).confirmedAt().atZone(businessZone))`로 `settlementService.run(month)` → `SettlementRunResult.settledShops` = 3. `countOf("settlement.settlement")` = 3. 가게별 `settlementSnapshotOf(shopId, month)`: A → `grossAmount` 10000 / B → 20000 / C → 30000. 모두 `status` `PAID`. `unsettledItemCount()` = 0. `FakePayoutGateway` 호출 3건. outbox `settlement.settlement-paid.v1` 3건.
2. **두 번째 실행** — 같은 인자로 `run` 한 번 더 → `settledShops` = 0. `FakePayoutGateway` 호출 3건 그대로, outbox `settlement.settlement-paid.v1` 3건 그대로.
3. 릴레이+대기(`notificationCounts().type("SETTLEMENT_PAID")` = 3) → `dispatchBatch()`.
4. `checkAll()`.

#### 시나리오 S7: `e2e/ShopCloseE2ETest`

**준비**: 판매자 1명, 가게 1개(이 판매자의 유일한 가게), 일반 상품 `product1` 10,000원, 구독 상품 `product2`(`registerSubscribableProduct(sellerId, shopId, 10000, 10)`), 구매자 1명. `createPaidOrder(구매자 ID, product1)`(가게주문 `PAID`), `createSubscription(구매자 ID, product2, registerBillingKey(구매자 ID))`(`ACTIVE`).
1. **진행 중 주문** — 판매자 토큰으로 `DELETE /api/shops/{shopId}` → 422, `$.code` = `SHOP_HAS_ACTIVE_ORDERS`. `shopSnapshotOf(shopId)`: `status()` = `ACTIVE`, `closedAt()` = null / outbox `shop.shop-closed.v1` 0건 / `memberSnapshotOf(판매자 ID).role()` = `SELLER` / `productSnapshotOf`의 `status()`가 두 상품 모두 `ON_SALE`.
2. **주문 종결** — `product1` 주문을 발송·배송완료 API와 구매자의 구매확정 API로 끝낸다(`snapshotOf(orderId).shopOrderStatus()` = `COMPLETED`). `DELETE /api/shops/{shopId}` → 422, `$.code` = `SHOP_HAS_ACTIVE_ORDERS`, `$.details.reason` = `ACTIVE_SUBSCRIPTION`. `shopSnapshotOf(shopId).status()` = `ACTIVE` 그대로.
3. **구독 상품 단종** — `PATCH /api/shops/{shopId}/products/{product2}/status` 본문 `{"status":"DISCONTINUED"}` → 200. outbox `product.product-discontinued.v1` 1건(`payload().get("reason").asText()` = `SELLER`). 릴레이+대기(`subscriptionSnapshotOf(subscriptionId)`의 `status()` = `TERMINATED`, `endReason()` = `PRODUCT_DISCONTINUED`).
4. **폐업** — `DELETE /api/shops/{shopId}` → 204. `shopSnapshotOf(shopId)`: `status()` = `CLOSED`, `closedAt()`이 null이 아니다. outbox `shop.shop-closed.v1` 1건. 릴레이+대기(두 상품의 `productSnapshotOf(productId).status()` = `DISCONTINUED`, `memberSnapshotOf(판매자 ID).role()` = `USER`). outbox `product.product-discontinued.v1`은 총 2건(`product2` `SELLER`, `product1` `SHOP_CLOSED`).
5. `checkAll()`.

#### 장애 시나리오: `e2e/KafkaOutageE2ETest`

**준비**: 판매자, 가게, 상품 2개(`productX` 10,000원·`productY` 20,000원), 구매자. 먼저 준비 단계의 이벤트(가입·가게 개설·상품 등록)를 비운다: 릴레이+대기(`unsentOutboxCount()` = 0, `notificationCounts().type("WELCOME")` = 2) → `notificationDispatcher.dispatchBatch()`로 WELCOME 알림 2건을 미리 보낸다. `kafka`는 `IntegrationTestSupport`의 컨테이너다.
1. **중단** — `kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec()`.
2. **중단 중 결제** — 주문 2건(`productX`, `productY` 각 1건)을 `POST /api/orders/checkout` → `POST /api/orders/{id}/payment/confirm`으로 승인한다. 두 응답 모두 200 `$.status` = `PAID`. `OutboxAssert.rows("order.order-paid.v1")` 2건이 모두 `status` = `READY`.
3. **중단 중 릴레이** — `outboxRelay.relayOnce()` → 반환값 0. `OutboxAssert.rows("order.order-paid.v1")`의 두 행은 `READY` 그대로이고 `attempts` = 1. `notificationCounts().type("ORDER_PAID")`·`.type("SHOP_ORDER_RECEIVED")` = 0.
4. **재개** — `unpauseContainerCmd(...)` → JDBC `UPDATE common.outbox_event SET next_attempt_at = now() - interval '1 second' WHERE status = 'READY'` → `outboxRelay.relayOnce()` 반환값 2.
5. **consumer 처리 완료** — `Eventually.await(Duration.ofSeconds(30), ...)`(consumer 재접속 시간은 확인 필요): `notificationCounts().type("ORDER_PAID")` = 2, `.type("SHOP_ORDER_RECEIVED")` = 2. `dispatchBatch()` 반환값 4.
6. `unsentOutboxCount()` = 0. `checkAll()`.

#### CI 실행 시간

- 읽는 곳: GitHub Actions의 `CI` 워크플로 실행 화면에서 `./gradlew build` 단계의 소요 시간과, 그 단계 로그 맨 끝의 `BUILD SUCCESSFUL in {n}m {n}s`.
- 기록: PR 본문에 위 두 값을 적는다.
- 합격 기준: 전체 `./gradlew build`가 10분 이내다. 넘으면 PR에 원인 정리를 붙인다: 컨테이너 기동(Postgres·MinIO·Kafka·Redis) 시간, 소요 시간 상위 10개 테스트 클래스(`./gradlew test --profile` 보고서 또는 `build/reports/tests/test/index.html`의 클래스별 Duration).

---

## 7-5. README · 회고 · Stage 1 릴리스 (M)

**목표**: 리포만 보고 프로젝트를 이해하고 실행할 수 있다. 이력서에 쓸 근거가 정리된다.

### 할 일

**구현 순서**: 1) README → 2) 회고 → 3) 이력서 bullet → 4) Stage 2 준비 → 5) 릴리스 → 6) Stage 1.5 준비.

**1) README** — `README.md` (Claude)

소개, 아키텍처 다이어그램, 실행 방법(인프라 → 앱 → 관측 스택), 운영 환경(VM)과 배포 흐름([ADR-011](../adr/ADR-011-ops-practice-environment.md)), 문서 안내, 핵심 설계 요약(정합성·동시성), 테스트로 증명한 것 목록

**2) 회고** — `docs/retrospectives/stage1.md` (사용자 작성, Claude 리뷰)

설계와 달라진 점과 이유, 리뷰에서 반복된 지적, 어려웠던 점, 다시 한다면

**3) 이력서 bullet 초안** (사용자 작성, Claude 리뷰)

문장마다 근거 링크(테스트 클래스, ADR, 수치)

**4) Stage 2 준비** (Claude 초안, 사용자 확정)

시드 데이터 규모·생성 방식, k6 시나리오 목록(NFR-PERF-01~06) → Stage 2 로드맵 문서

**5) 릴리스** — develop → main, 태그 `v1.0.0`

develop → main PR을 **merge commit**으로 합치고(squash 아님), main에 태그를 붙인 뒤 GitHub Release를 만든다. Release 노트에는 완료한 단계 목록, 주요 ADR, 알려진 한계를 쓴다([git-policy §7](../git-policy.md#7-릴리스와-태그). 같은 표에 Part 7 완료 `v0.7.0`도 있다: 이 단계에서 `v0.7.0`을 따로 찍을지는 확인 필요)

**6) Stage 1.5 준비** (Claude 초안, 사용자 확정)

[ADR-012](../adr/ADR-012-kubernetes-zero-downtime.md)의 "확인 필요"·미정 항목(노드 수, 상태 서비스 위치)을 확정하고 K-1~K-5 로드맵 문서로 구체화. Stage 2 Baseline은 그 이후 k3s 환경에서 측정한다

### 완료 확인

**확인 값을 읽는 방법**: 파일은 리포 루트 기준 경로로 열어 읽고, 명령은 리포 루트에서 실행한다. GitHub 쪽 값은 `gh` 출력의 JSON 필드로 읽는다.

1. **README**
   - 열어서 볼 것: 소개, 아키텍처 다이어그램(Mermaid), 실행 방법(인프라 → 앱 → 관측 스택 순서), 운영 환경(VM)과 배포 흐름, 문서 안내, 핵심 설계 요약(정합성·동시성), 테스트로 증명한 것 목록의 7개 절이 각각 있다.
   - 링크: `grep -oE '\]\((docs/[^)#]+)' README.md | sed 's/](//' | sort -u | while read p; do test -e "$p" || echo "깨짐: $p"; done`의 출력이 없다.
   - 증명 목록: 항목마다 적힌 테스트 클래스 이름 `X`에 대해 `find src/test -name 'X.java'`가 정확히 1건이다.
   - 실행 방법: 새 폴더에 `git clone` 후 README의 명령만 순서대로 실행해 앱이 뜨고 `curl -s localhost:8081/actuator/health`가 `{"status":"UP"}`를 돌려준다. 관측 스택은 `docker compose --profile observability up -d` 뒤 Grafana 대시보드 3개가 보인다.
2. **회고** `docs/retrospectives/stage1.md`: 절 4개(설계와 달라진 점과 이유, 리뷰에서 반복된 지적, 어려웠던 점, 다시 한다면)가 있고 각 절에 항목이 1개 이상이다. "달라진 점"의 항목마다 근거 문서(ADR 또는 로드맵 버전 줄) 링크가 있다.
3. **이력서 bullet 초안**: 문장마다 링크가 1개 이상이고 링크 대상이 존재한다(테스트 클래스 파일, ADR, 수치의 출처). 수치가 있는 문장은 그 수치를 낸 측정·테스트 결과가 링크 대상에 있다.
4. **Stage 2 준비 문서**: 시드 데이터 규모(건수)와 생성 방식이 있고, k6 시나리오 목록에 NFR-PERF-01~06이 각각 1개 이상의 시나리오로 연결된다(`grep -oE 'NFR-PERF-0[1-6]' {문서} | sort -u`가 6줄). 합격 기준 값은 [요구사항 NFR 표](../01-requirements/README.md)의 값(예: NFR-PERF-01 p95 < 200ms, 에러율 < 0.1%)과 같다.
5. **릴리스**
   - develop → main PR: `gh pr view {번호} --json state,mergeCommit` → `state` = `MERGED`. `git cat-file -p {mergeCommit.oid}`에 `parent` 줄이 2개다.
   - 태그: `git tag -l v1.0.0` 출력이 `v1.0.0`, `git rev-parse v1.0.0^{commit}` = `git rev-parse origin/main`.
   - Release: `gh release view v1.0.0 --json tagName,body` → `tagName` = `v1.0.0`, `body`에 완료한 단계 목록, 주요 ADR, 알려진 한계 3개 절이 있다.
   - main의 CI: `gh run list --branch main --workflow CI --limit 1 --json conclusion` → `success`.
   - 확인 필요: 이 단계에서 `v0.7.0`을 따로 찍을지.
6. **Stage 1.5 준비**: ADR-012에서 노드 수·상태 서비스 위치가 "제안"이 아니라 확정 값으로 적혀 있고, K-1~K-5 로드맵 문서가 `docs/03-roadmap/`에 있다(파일 이름은 확인 필요).

**리뷰 때 물어볼 것**: 이력서 문장마다 근거(테스트, 문서, 수치)가 있는가?
