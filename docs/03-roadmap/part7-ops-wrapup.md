# Part 7. 관측 · 운영 · 마무리

> 버전 0.11 · 2026-10-16 · **쓰지 않는 요소를 걷어내고 2-6 변경에 맞췄다**(구현 전 점검): ① 7-4 시나리오 보조 호출 표의 "환불 복구"(`recoverBatch()`) 행을 지웠다(S1~S7·장애 어느 시나리오도 부르지 않는다). ② 7-3 `reconcileOne`의 설명이 옛 2-6(`queryOutcome` → `apply` 한 경로)을 가리키고 있어, 확정된 결제는 `applyPaymentState`로 바로 처리하는 지금의 `reconcileBatch` 처리로 맞췄다. 규격·동작은 0.10과 같다
> 0.10 · 2026-10-16 · **구현물이 단계 설명 안에 숨어 있던 곳을 표로 올렸다**(구현 중 지적 "리포지토리·엔티티 작업이 설명에 흩어져 있다"): 7-2의 수집기 조회 메서드(`PaymentRepository`·`OrderRepository`·`RefundRepository`)를 시그니처로 적고, 노출 설정(`percentiles-histogram`)을 1)로 모았으며, docker-compose 서비스와 Grafana dashboards provider yml을 파일 목록에 올렸다. 7-3은 `Refund.retryPgCancel()`·`reconcileOne`·목록 쿼리·컨트롤러·DLT 서비스·테스트 보조를 구현물 표로 모아 중복 문장(`reconcileOne` 두 곳, 대사 API 모듈 위치 두 곳, 확인 필요 정리의 반복)을 줄였다. 7-1·7-2·7-3·7-4에 구현 순서 줄을 더했다. 규격·동작은 0.9와 같다
> 0.9 · 2026-10-15 · **7-3 `reconcile` 경로·반환과 7-4 불변식 범위를 정했다**(리뷰 결정): `{id}`는 `orderId`, `reconcileOne`은 `OrderStatus` 반환, `InvariantChecker`에 INV-08을 더하고 INV-09는 최종적 일관성이라 제외. 그 밖의 규격은 0.8과 같다
> 0.8 · 2026-10-15 · **7-3 환불 재시도의 PG 멱등 키를 정했다**(리뷰 결정): `retry`가 `FAILED → APPROVED`와 함께 `pgAttempts`를 올려 `refundId-r{n}` 키로 PG까지 가게 했다(2-8 `Refund.pgIdempotencyKey()`). 그 밖의 규격은 0.7과 같다
> 0.7 · 2026-10-15 · **Part 7 클래스·메서드 설명을 풀어 썼다**(구현 중 "뭔지 모르겠다" 지적): 표 한 줄 요약이던 항목에 목적·호출 위치·입력→반환·순서·함정을 적었다 — 7-1 MDC 넣고 빼는 법과 `logback-spring.xml` 구성, 7-2 수집기·Micrometer 사용법·`StockCheckJob`·`TossClient` 타이머, 7-3 운영 API별 처리 순서, 7-4 `InvariantChecker`와 시나리오가 부르는 잡. 문서만으로 정할 수 없는 곳은 "(확인 필요)"로 표시했다(7-3 `reconcile`의 `{id}`, 환불 재시도와 `PaymentApi.cancel`의 FAILED 처리 등). 규격·동작은 0.6과 같다
> 0.6 · 2026-10-14 · **표현 정리**(설계·동작 변경 없음): 7-1 MDC 예시의 `var`를 명시 타입으로(리포 코드에 `var`가 없다), 7-2 서비스 개요 대시보드의 긴 항목을 하위 항목으로 풀었다
> 0.5 · 2026-10-09 · **버전 확인·운영 환경 반영**: logstash-logback-encoder `9.0`(Jackson 3·Logback 1.5 기반), ELK·Prometheus·Grafana 이미지 태그를 확인해 고정, Spring Boot가 Kafka 클라이언트 지표를 자동 등록하는 것을 확인, 1-11 운영 VM에서는 Prometheus가 `app:8081`을 긁도록 적었다
> 0.4 · 2026-10-02 · 예치금 제거: 원장 점검 → 재고 대조 점검, E2E 시나리오에서 예치금 삭제
> 0.3 · 이 문서만 보고 개발할 수 있게 구체화(로그 필드, 메트릭 목록과 계산식, 운영 API, E2E 시나리오)

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

**구현 순서**: ① 의존성·`logback-spring.xml` → ② MDC(`memberId` → `orderId`) → ③ `docker/logstash`·compose·Kibana 저장 검색 → ④ 민감정보 점검(`LogSafetyTest`).

**1) 의존성·설정**
- `implementation 'net.logstash.logback:logstash-logback-encoder:9.0'` — 2026-10-09 Maven Central 최신. 9.0의 pom은 `tools.jackson.core:jackson-databind`(Jackson 3)와 `logback-core` 1.5.x에 의존해 Boot 4.1.1(Jackson 3.1.5, Logback 1.5.38)과 맞는다. Boot BOM이 관리하지 않아 버전을 적는다
- `src/main/resources/logback-spring.xml`: `<springProfile name="elk">`에 `LogstashTcpSocketAppender`(destination `${LOGSTASH_HOST:-localhost}:5000`, `LogstashEncoder`), 그 외에는 Boot 기본 콘솔. 콘솔 패턴은 1-3의 `logging.pattern.level` 유지
  - **왜 이 파일인가**: logback 설정은 `logback.xml`로도 쓸 수 있지만 `<springProfile>`(프로필에 따라 설정을 나누는 태그)은 Boot가 읽는 `logback-spring.xml`에서만 된다. 이 파일을 두면 Boot의 로깅 자동 설정을 대신하므로, 기본 콘솔 설정을 직접 불러와야 한다.
  - **구성(위에서 아래로)**
    1. `<include resource="org/springframework/boot/logging/logback/defaults.xml"/>` — Boot의 기본 콘솔 패턴 변수(`CONSOLE_LOG_PATTERN`)를 정의한다. 이 패턴이 `logging.pattern.level`(1-3의 `%5p [%X{traceId:-}]`)을 쓴다
    2. `<include resource="org/springframework/boot/logging/logback/console-appender.xml"/>` — `CONSOLE` appender를 만든다(두 파일 모두 Boot 4.1.1 jar에서 확인). `base.xml`은 파일 appender까지 붙이므로 쓰지 않는다
    3. `<springProfile name="elk">` 안에 `LogstashTcpSocketAppender`(`net.logstash.logback.appender`) 하나를 선언하고 `<destination>`, `<encoder class="net.logstash.logback.encoder.LogstashEncoder">`를 넣는다
    4. `<root level="INFO">`에 `CONSOLE`은 항상, 위 appender는 `elk` 프로필 블록 안에서만 `appender-ref`로 붙인다
  - Logstash가 꺼져 있어도 앱은 기동한다. 그동안의 로그는 전송되지 않을 수 있다(확인 필요: 재접속 주기·버퍼 동작은 라이브러리 README로 확인)
- JSON 필드: `@timestamp`, `level`, `logger_name`, `message`, `stack_trace`, MDC 전부(`traceId`, `memberId`, `orderId`, `job`, `eventId`), `app: myroutine`
  - `LogstashEncoder`는 위 필드 대부분(`@timestamp`, `level`, `logger_name`, `message`, `stack_trace`)과 MDC를 기본으로 JSON 한 줄에 담는다(확인 필요: 기본 필드 이름은 logstash-logback-encoder 9.0 README로 확인). 직접 정하는 것은 `app`뿐이고, `LogstashEncoder`의 `<customFields>{"app":"myroutine"}</customFields>`로 넣는다

**2) MDC 보강**

MDC는 SLF4J가 스레드마다 들고 있는 `Map<String, String>`이다. 인코더가 로그 한 줄을 쓸 때 현재 스레드의 MDC를 필드로 붙이므로, 값을 넣어 두면 그 사이에 찍힌 모든 로그에 따라붙는다. 값은 문자열만 되고(UUID는 `.toString()`), **스레드를 넘어가지 않으며**(Kafka listener·스케줄러 스레드는 따로 넣어야 한다), 안 지우면 스레드 풀의 다음 작업에 남는다. 그래서 넣는 곳마다 빼는 곳이 짝으로 있다. `traceId`는 1-3(`TraceIdFilter`)·2-5(`JobRunner`)·3-1·3-2(`EventContext`·`InboxGuard`)에서 이미 넣고 있고, 이 단계에서 새로 넣는 것은 `memberId`와 `orderId`다.

| 키 | 넣는 곳 | 빼는 곳 |
|---|---|---|
| `traceId` | `TraceIdFilter`, `JobRunner`, `EventContext`/`InboxGuard` (이미 있음) | 각자 finally |
| `memberId` | `JwtAuthenticationFilter` 인증 성공 직후 | `TraceIdFilter`의 finally에서 함께 제거 |
| `orderId` | 주문 흐름 서비스(`ConfirmPaymentService`, `OrderPaymentApplier`, `CancelOrderLineService`, 만료·대사 잡의 주문별 처리)가 `MDC.putCloseable`로 넣는다(아래 `orderId`) | try-with-resources가 제거 |
| `job` | `JobRunner` (이미 있음) | |

- **`memberId`**: `JwtAuthenticationFilter.doFilterInternal`에서 `jwtProvider.parse(token).ifPresent(claims -> ...)` 람다 안, `SecurityContextHolder`에 인증을 넣는 줄 바로 뒤에 `MDC.put("memberId", claims.memberId().toString())`을 더한다(`AuthClaims`는 `record (UUID memberId, String role)`). 빼는 곳은 `TraceIdFilter.doFilterInternal`의 `finally`에 `MDC.remove("memberId")` 한 줄 추가 — `TraceIdFilter`가 `HIGHEST_PRECEDENCE`라 보안 필터보다 바깥에서 요청 전체를 감싸므로 거기서 지우면 된다. 토큰이 없거나 틀린 요청은 이 줄을 지나지 않으니 `memberId`가 비어 있다(익명 요청)
- **`orderId`**: `try (MDC.MDCCloseable ignored = MDC.putCloseable("orderId", orderId.toString())) { ... }`. `putCloseable`은 값을 넣고, try 블록이 끝나면(예외 포함) `close()`가 **그 키를 지운다**. 주문을 처리하는 메서드 본문 전체를 이 블록으로 감싼다. 건별 반복(만료·대사 잡의 `for (UUID id : ids)`)은 주문마다 블록을 열고 닫아야 다음 주문 로그에 앞 주문 ID가 남지 않는다
  - 주의: `close()`는 **이전 값을 복원하지 않고 지우기만 한다**. `ConfirmPaymentService`가 연 블록 안에서 `OrderPaymentApplier`가 같은 키로 다시 열면, 안쪽이 닫히는 순간 바깥 구간의 `orderId`도 사라진다. 어느 쪽을 열지(가장 바깥 한 곳만 열기, 이미 같은 값이면 열지 않기 등)는 문서에 없다(확인 필요)

**3) docker**
- `docker/logstash/pipeline/logstash.conf`: `input { tcp { port => 5000 codec => json_lines } }` → `output { elasticsearch { hosts => ["http://elasticsearch:9200"] index => "logs-myroutine-%{+YYYY.MM.dd}" } }`
  - 흐름: 앱의 appender가 로그 1건을 JSON 한 줄로 TCP 5000에 보낸다 → `json_lines` 코덱이 줄 단위로 JSON을 이벤트로 풀어 필드로 만든다 → `elasticsearch` output이 색인 이름 날짜를 이벤트의 `@timestamp`(UTC)로 채워 저장한다. 그래서 한국 시간 새벽 로그는 전날 날짜 색인에 들어간다
  - `http://elasticsearch:9200`은 인증 없는 접속이다. 6-2가 ES를 `xpack.security.enabled=false`로 띄우기 때문에 이대로 된다. 컨테이너끼리는 compose 서비스 이름(`elasticsearch`)으로 접속한다
- compose: `docker.elastic.co/logstash/logstash:9.4.8`(5000), `docker.elastic.co/kibana/kibana:9.4.8`(5601) — ES(6-2)와 같은 버전
  - Logstash는 `./docker/logstash/pipeline`을 컨테이너의 파이프라인 디렉터리로 마운트한다(확인 필요: 마운트 경로는 `logstash:9.4.8` 이미지 문서로 확인). 5000은 호스트에 publish해야 호스트에서 도는 앱의 `localhost:5000`이 닿는다. Kibana는 환경변수로 ES 주소(`ELASTICSEARCH_HOSTS=http://elasticsearch:9200`)를 준다(확인 필요: 변수 이름)
- Kibana: 데이터 뷰 `logs-myroutine-*`, 저장 검색 3개 — "traceId로 흐름 보기"(`traceId : "..."`, 시간 오름차순, 열 level·logger·message·job), "ERROR만", "결제 UNKNOWN"(`message : "UNKNOWN" and logger_name : *payment*`) → `docker/kibana/saved-objects.ndjson`로 내보내기
  - 쿼리는 Kibana 검색창의 KQL이고 `traceId`·`logger_name`·`message`·`job`은 위 JSON 필드 이름이다. "ERROR만"은 `level : "ERROR"`. 내보내기는 Kibana 화면의 저장된 객체(Saved Objects) 관리에서 하고 `.ndjson`(줄마다 JSON 한 개)으로 받는다(확인 필요: 9.4 화면의 메뉴 위치)

**4) 민감정보 점검**: 아래 테스트로 확인하고, 걸리면 로그 문장을 고친다
- 점검 대상은 **로그 문장(`log.info("...", 값)`의 인자와 예외 메시지)**이다. 토큰·비밀번호·인증코드·빌링키·`authKey`·`paymentKey`가 인자로 들어간 곳, 요청·응답 바디를 통째로 찍는 곳이 후보다. 고치는 방법은 값을 빼거나 앞 몇 글자만 남기는 것이다
- 테스트 방식: `@ExtendWith(OutputCaptureExtension.class)`를 달면 메서드 파라미터로 `CapturedOutput output`을 받는다. 흐름을 실행한 뒤 `output.getAll()`(그때까지 콘솔에 찍힌 전체 문자열)에 위 값들이 없는지 `assertThat(...).doesNotContain(값)`으로 확인한다. 값은 흐름 중에 만든 실제 값(가입 응답의 토큰, 로그인 때 보낸 비밀번호 등)을 변수로 들고 있다가 쓴다. 콘솔 출력을 보므로 JSON 인코더와 무관하게 같은 로그 문장을 검사한다

### 완료 확인
- [ ] `common/LogSafetyTest` (`@ExtendWith(OutputCaptureExtension.class)` + `IntegrationTestSupport`): 가입 → 로그인 → refresh → 빌링키 등록 → 이메일 인증코드 발송 → 결제 흐름을 실행한 뒤, 캡처한 로그 전체에 액세스·refresh 토큰 값, 비밀번호, 인증코드, 빌링키, `authKey`, `paymentKey` 전체 값이 없다
- [ ] (수동) 결제 1건의 traceId로 Kibana 검색 → 체크아웃·승인·outbox 저장·릴레이·consumer·알림 발송 로그가 시간순으로 보임. PR에 스크린샷

**리뷰 때 물어볼 것**: 분산 트레이싱(Tempo 등) 없이 로그로만 추적하면 무엇이 부족한가? (ADR-010)

---

## 7-2. Prometheus · Grafana: 메트릭 · 대시보드 · 알림 (L)

**목표**: 서비스 상태와 **정합성 지표**를 대시보드로 보고, 문제가 생기면 알림이 온다.

**새로 등장**: Prometheus(메트릭 수집), Grafana(대시보드·알림), Micrometer(앱에서 메트릭 만들기: `Counter`, `Timer`, `Gauge`)

**정책 (이 단계에서 정함)**
- DB를 조회해서 만드는 지표(대기 건수 등)는 **스크랩할 때마다 쿼리하지 않는다**. 각 모듈의 수집기가 30초마다 계산해 `AtomicLong`에 넣고, Gauge는 그 값을 읽는다.
- 지표 이름·라벨은 아래 표가 기준이다(ADR-010 목록과 같게, 바뀌면 ADR도 고친다).

### 할 일

**구현 순서**: ① 노출(의존성·`application.yaml`·`SecurityConfig`) → ② 조회 메서드(`PaymentRepository`·`OrderRepository`·`RefundRepository`) → ③ 수집기 3개 → ④ Counter·Timer 연결 → ⑤ `StockCheckJob` → ⑥ Prometheus·Grafana 파일·compose → ⑦ 테스트 → ⑧ 장애 주입(수동).

**1) 노출**
- 의존성 `io.micrometer:micrometer-registry-prometheus` — Micrometer(앱 안에서 메트릭을 만드는 API)가 모은 값을 Prometheus 텍스트 형식으로 내보내는 구현체다. 넣으면 `MeterRegistry` 빈이 `PrometheusMeterRegistry`로 자동 등록되고 `/actuator/prometheus`가 생긴다. 아래 수집기·타이머는 모두 이 `MeterRegistry`를 주입받아 쓴다
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

**수집기 클래스** (`OutboxMetrics`, `PaymentMetrics`, `OrderMetrics`) — 위 정책의 "30초마다 계산해 `AtomicLong`에 넣는" 일을 하는 `@Component`다. 모양은 셋이 같다

1. 필드: 지표마다 `AtomicLong` 하나(초기값 0). `saga_stuck_count`는 같은 이름에 `type` 라벨만 다른 Gauge 둘이라 `AtomicLong`도 둘
2. 생성자(주입: `MeterRegistry`와 아래 조회 수단): 필드마다 `Gauge.builder(이름, 필드, AtomicLong::doubleValue)[.tag(...)].register(registry)`
3. `@Scheduled(fixedDelay = 30_000)` 메서드 하나(이름은 권장안 `refresh()`, **`public`** — `MetricsTest`가 직접 부른다): 쿼리 실행 → 각 필드 `set`. 쿼리 예외는 스케줄러가 로그를 남기고 다음 주기에 다시 하므로 Gauge는 직전 값을 유지한다
4. `JobRunner`·잡 락은 쓰지 않는다. 락은 "한 인스턴스만 실행"인데, Prometheus는 인스턴스마다 긁으므로 각 인스턴스가 자기 Gauge를 계산해야 한다(확인 필요: 문서에 명시가 없다)
5. 테스트 프로필은 `myroutine.scheduling.enabled=false`(2-5)라 `@Scheduled`가 돌지 않는다. 테스트가 `refresh()`를 직접 부르고 `registry.get("outbox_ready_count").gauge().value()`로 읽는다

| 클래스 | 조회 수단 | 계산 |
|---|---|---|
| `OutboxMetrics` (common, 권장 패키지 `common.outbox`) | `JdbcTemplate` — `common.outbox_event`는 같은 모듈 테이블 | 표의 세 값. `now`는 SQL의 `now()`로 구하면 된다(릴레이도 DB 시각을 쓴다). READY가 없으면 age는 0 |
| `PaymentMetrics` (payment.application) | `PaymentRepository`(domain 순수 인터페이스)에 `long countByStatus(PaymentStatus status)`와 `Optional<Instant> findOldestUpdatedAtByStatus(PaymentStatus status)`를 더하고 `JpaPaymentRepository`에 구현한다(이름·인자는 예). 대상은 `UNKNOWN` | `payment_unknown_count` = `countByStatus(UNKNOWN)`, `payment_unknown_oldest_age_seconds` = `Duration.between(가장 오래된 updated_at, clock.instant()).toSeconds()`, 없으면 0 |
| `OrderMetrics` (order.application) | `OrderRepository`에 `long countByStatusAndUpdatedAtBefore(OrderStatus status, Instant before)`, `RefundRepository`에 `long countByStatusInAndUpdatedAtBefore(Collection<RefundStatus> statuses, Instant before)`를 더한다(둘 다 domain 순수 인터페이스 + Jpa 구현. 이름·인자는 예) | `PAYMENT_IN_PROGRESS`이면서 `updated_at < now - 5분`인 주문 수, 환불 `APPROVED`·`PG_CANCELLED`이면서 `updated_at < now - 10분`인 환불 수. 기준 컬럼이 `updated_at`인 것은 2-6 `findStuckIds`·2-8b 복구 잡과 같다(문서에 컬럼이 명시되지 않아 확인 필요) |

**Counter·Timer를 거는 곳**
- `kafka_dlt_records_total{topic}`: 3-2 `KafkaConfig`가 만든 `DeadLetterPublishingRecoverer`는 `ConsumerRecordRecoverer`(= `BiConsumer<ConsumerRecord<?,?>, Exception>`)다. `DefaultErrorHandler`에 넘기기 전에 `ConsumerRecordRecoverer counting = (record, ex) -> { recoverer.accept(record, ex); registry.counter("kafka_dlt_records_total", "topic", record.topic()).increment(); }`로 감싸 넘긴다(시그니처는 spring-kafka 3.3.16 jar에서 확인, Boot 4.1.1이 쓰는 버전에서도 같은지 확인 필요). `record.topic()`은 **원본 토픽**이라 7-3 DLT API의 `{topic}`과 같은 값이다(라벨을 원본·`.dlt` 중 무엇으로 할지는 문서에 없다. 확인 필요). `KafkaConfig`가 `MeterRegistry`를 주입받는다
- `stock_reservation_expired_total`: `OrderExpirationService.expireBatch`의 건별 루프에서 `tx.execute(s -> expireOne(id))`가 `true`를 반환한 **직후**(트랜잭션 밖)에 `increment()`. `expireOne` 안에서 올리면 롤백돼도 카운트가 남는다
- `pg_call_seconds{operation,result}`: `TossClient`의 `confirm`·`cancel`·`queryByOrderId`가 이미 `start`를 재고 마지막에 `logResult(pgMethod, paymentId, result, start)`를 부른다. 같은 자리에서 `registry.timer("pg_call_seconds", "operation", pgMethod, "result", ...).record(System.nanoTime() - start, TimeUnit.NANOSECONDS)`를 하면 된다. `TossClient`에 `MeterRegistry` 필드를 더한다. 라벨 값은 문서에 정해져 있지 않다(확인 필요). 로그와 대시보드가 같은 말을 쓰도록 `operation`은 `pgMethod`(`confirm`·`cancel`·`query`), `result`는 `PgResult` 종류(`Approved`·`Rejected`·`Unknown`)를 소문자로 쓰는 것을 권장한다 Part 5가 더한 `issueBillingKey`·`billingCharge`(로그의 작업 이름 `BILLING_ISSUE`·`BILLING`)에도 같은 Timer를 걸지, `operation` 값을 무엇으로 할지는 문서에 없다(확인 필요).
- `mail_send_seconds{result}`·`openai_call_seconds{operation,result}`: "각 클라이언트"는 메일 발송기(3-5 `MailSender` 구현체. 호출하는 `NotificationDispatcher`에서 잴지는 확인 필요)와 OpenAI를 부르는 곳(Part 6의 `EmbeddingModel.embed`·`ChatClient` 호출부)이다. 방식은 `Timer.start` → 호출 → `stop`(예외가 나도 `finally`에서 `stop`)이며 `result` 값(`success`·`failure` 등)과 `operation` 값은 정해져 있지 않다(확인 필요)

**3) 재고 정합성 점검 잡** — product `StockCheckJob`: 매일 05:00(Asia/Seoul), 잡 이름 `stock-check`. 재고 등식 자체는 CHECK가 막으므로, **재고 행과 기록(이력·예약)의 대조**를 본다
```sql
SELECT s.product_id FROM product.stock s
LEFT JOIN (SELECT product_id, SUM(quantity) q FROM product.stock_movement WHERE type IN ('RECEIVE','ADJUST') GROUP BY product_id) m ON m.product_id = s.product_id
LEFT JOIN (SELECT product_id, SUM(quantity) q FROM product.stock_reservation WHERE status = 'HELD' GROUP BY product_id) r ON r.product_id = s.product_id
WHERE s.received <> COALESCE(m.q, 0) OR s.reserved <> COALESCE(r.q, 0)
```
- 불일치 상품마다 `log.error`, 개수를 Gauge에. (INV-03·04)

`StockCheckJob` (product.infrastructure, `@Component`) — **재고 행의 숫자가 기록(이력·예약)과 맞는지 하루에 한 번 대조하는 잡**. 다른 잡(`OrderExpirationJob` 등)과 같은 모양이다
- **왜 필요한가**: `ck_stock_balance`(`available + reserved + sold = received`)는 한 행 안의 등식만 지킨다. 코드 버그로 `received`와 `available`을 같이 틀리게 바꾸면 CHECK는 통과하지만 이력과는 어긋난다. 이 잡이 그 어긋남을 잡는다(INV-03·04)
- **구성**: `@Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")` 메서드 → `jobRunner.run("stock-check", this::check)`. `JobRunner.run`은 `Supplier<Integer>`를 받으므로 `check()`는 `int`(불일치 상품 수)를 반환한다. `check()`(이름은 권장안, `public`)가 하는 일: ① 위 SQL을 `JdbcTemplate.queryForList(sql, UUID.class)`로 실행(`List<UUID>` = 어긋난 `product_id`) ② 상품마다 `log.error("stock mismatch productId={}", id)` ③ `AtomicLong`(`stock_balance_mismatch_count`)에 `set(개수)` ④ 개수 반환. `StockCheckJobTest`는 `check()`를 직접 부른다(테스트 프로필에서는 스케줄이 꺼져 있다)
- **SQL 읽는 법**: `m.q`는 상품별 `stock_movement`의 **입고(RECEIVE)와 조정(ADJUST)** 수량 합(조정은 음수일 수 있다)이고, `s.received`와 같아야 한다. `r.q`는 상품별 `HELD`(보류 중) 예약 수량 합이고 `s.reserved`와 같아야 한다. 이력·예약이 한 건도 없는 상품은 LEFT JOIN 결과가 `NULL`이라 `COALESCE(..., 0)`로 0과 비교한다
- 이 Gauge는 05:00 실행 때만 바뀐다. 앱을 재시작하면 다음 05:00까지 0으로 보인다.

**4) Prometheus·Grafana**
- docker-compose `prometheus`·`grafana` 서비스(프로필 `observability`, 공통 규칙): 이미지 `prom/prometheus:v3.15.0`, `grafana/grafana:13.2.3`(2026-10-09 Docker Hub의 최신 안정 태그). `docker/prometheus/prometheus.yml`을 컨테이너의 `/etc/prometheus/prometheus.yml`로, `docker/grafana/provisioning`을 컨테이너의 `/etc/grafana/provisioning`으로 마운트한다. 호스트 포트 publish는 문서에 없다(확인 필요)
- `docker/prometheus/prometheus.yml`: scrape 15초, path `/actuator/prometheus`, 대상은 환경마다 다르다
  - 구조: `global.scrape_interval: 15s`, `scrape_configs`에 `job_name: myroutine`, `metrics_path: /actuator/prometheus`, `static_configs[].targets`에 아래 환경별 주소. compose에서는 이 파일을 컨테이너의 `/etc/prometheus/prometheus.yml`로 마운트한다. Prometheus가 그 주소로 15초마다 HTTP GET을 보내 현재 값을 가져가는 "pull" 방식이라 앱은 값을 밀어 보내지 않는다
  - 로컬(앱은 IDE·`bootRun`으로 호스트에서 실행): `host.docker.internal:8081` — Docker Desktop(macOS)에서는 그대로 된다. Linux에서 호스트의 앱을 긁으려면 prometheus 서비스에 `extra_hosts: ["host.docker.internal:host-gateway"]`가 필요하다
  - 운영 VM(1-11 `docker-compose.ops.yml`의 `observability` 프로필): 앱도 컨테이너이므로 `app:8081`(관리 포트는 호스트에 publish하지 않지만 같은 compose 네트워크에서는 닿는다). 파일을 둘로 두거나(`prometheus.yml`, `prometheus.ops.yml`) 대상만 바꾼다
- `docker/grafana/provisioning/datasources/prometheus.yml`, `docker/grafana/provisioning/dashboards/`의 provider 설정 yml 1개와 `*.json` 3개(서비스 개요, 결제·정합성, Kafka·Outbox)
  - 프로비저닝은 Grafana가 기동할 때 이 디렉터리(컨테이너의 `/etc/grafana/provisioning`에 마운트)를 읽어 데이터소스·대시보드를 자동으로 만드는 기능이다. 데이터소스 파일은 `apiVersion: 1`, `datasources:`에 `name: Prometheus`, `type: prometheus`, `url: http://prometheus:9090`(compose 서비스 이름), `isDefault: true`
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

**5) 장애 주입으로 확인 (수동)**
- Kafka 중단: `docker compose stop kafka` → 체크아웃 몇 건 → "Outbox 지연" Firing → `start kafka` → 해제
- 결제 불확실: 로컬에서 `myroutine.toss.base-url`을 응답하지 않는 주소(예: `http://10.255.255.1`)로 바꿔 실행 → 결제 승인 → "결제 확인 지연" Firing
- 재고 불일치: 로컬 DB에서 `stock.received`와 `available`을 같은 양만큼 바꾸고(CHECK 통과) 점검 잡 실행 → Firing. 점검 잡을 실행하는 방법은 문서에 없다(확인 필요: 05:00을 기다림, 로컬에서 cron 임시 변경, 별도 트리거)
- Firing은 수집기 갱신(최대 30초) → 스크랩(15초) → 규칙 평가 → `지속` 시간이 차례로 지나야 보인다("결제 확인 지연"은 UNKNOWN이 된 뒤 `payment_unknown_oldest_age_seconds`가 600초를 넘어야 한다). 화면에 안 뜬다고 바로 실패로 보지 말고 그만큼 기다린다

### 완료 확인
- [ ] `common/MetricsTest`: `/actuator/prometheus`는 MockMvc로 못 부르므로 `MeterRegistry` 빈을 주입해 확인 — READY outbox 3건 → 수집기 실행 → `outbox_ready_count` 3 / DLT 1건 → counter 1 / UNKNOWN 결제 → `payment_unknown_count` 1
- [ ] `product/application/StockCheckJobTest`: JDBC로 `received`·`available`을 함께 바꿈 → 잡 → Gauge 1
- [ ] (수동) 위 장애 주입 3가지 → 알림 발생 → 복구 → 해제. PR에 스크린샷

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

**구현 순서**: ① `RefundStatus`·`Refund.retryPgCancel()` → ② `OrderReconcileService.reconcileOne` → ③ 목록 조회 쿼리 → ④ 관리자 컨트롤러·DLT 처리 → ⑤ 테스트 보조 → ⑥ 테스트. `docs/runbook.md`는 Claude가 쓴다.

| API | 처리 | 위치 |
|---|---|---|
| `GET /admin/api/outbox?status=DEAD&cursor=` | DEAD 행 목록(id, topic, aggregateId, attempts, lastError, createdAt) | common |
| `POST /admin/api/outbox/{id}/retry` | DEAD → READY, `attempts = 0`, `next_attempt_at = now` | common |
| `GET /admin/api/dlt/{topic}?limit=50` | 짧게 쓰는 `KafkaConsumer`(그룹 없이 `assign`)로 `{topic}.dlt` 각 파티션 끝에서 최대 limit개 읽기 → `(partition, offset, key, value 앞 500자, 예외 헤더)` | common |
| `POST /admin/api/dlt/{topic}/{partition}/{offset}/replay` | 그 레코드 하나를 읽어 원래 토픽(`.dlt` 제거)으로 같은 key·value를 `send().get()` | common |
| `GET /admin/api/payments?status=UNKNOWN&cursor=` | 결제 목록(id, orderId, amount, reconcileAttempts, updatedAt) | payment |
| `POST /admin/api/payments/{orderId}/reconcile` | 그 주문만 바로 대사(2-6 로직) → 200 `{orderId, status}`(처리 뒤 주문 상태) | order |
| `GET /admin/api/refunds?status=FAILED&cursor=` · `POST /admin/api/refunds/{id}/retry` | FAILED → APPROVED → `refundProcessor.process` | order |
| `GET /admin/api/notifications?status=FAILED&cursor=` · `POST /admin/api/notifications/{id}/retry` | FAILED → PENDING, `attempts = 0` | notification |
| `POST /admin/api/settlements/run?period=` | (3-4에서 만듦) | settlement |

구현물 (위에서 아래로 만든다. 이름·시그니처는 권장안이다. API별 처리 순서는 아래 각 절)

| # | 대상 | 규격 | 쓰이는 곳 |
|---|---|---|---|
| 1 | `RefundStatus` 전이 + `Refund.retryPgCancel()` (2-8, 기존 수정) | 전이표에 `FAILED → APPROVED`(관리자 재시도)를 더하고([상태머신 §4](../02-design/04-state-machines.md)도 갱신), `Refund`에 "FAILED가 아니면 거부하고, FAILED → APPROVED로 바꾸면서 `pgAttempts`를 1 올리는" 메서드 `retryPgCancel()`을 더한다. `pg_attempts` 칸과 `Refund.pgIdempotencyKey()`(`pgAttempts`가 1 이상이면 `refundId-r{pgAttempts}`)는 2-8에서 만든다 | `refunds` `retry` |
| 2 | `OrderReconcileService.reconcileOne(UUID orderId)` → `OrderStatus` (2-6 `OrderReconcileService`에서 뽑아낸다) | 2-6 `reconcileBatch`의 "각 주문마다" 처리(`paymentApi.find` → `APPROVED`·`FAILED`는 `applyPaymentState`, `CANCELLED`는 `failOrder`, `IN_PROGRESS`·`UNKNOWN`은 `queryPgOutcome` → `applyOutcome` → `applyPaymentState`, 완료 실패 시 보상)를 그대로 옮긴 메서드. `reconcileBatch`는 `findStuckIds` 루프에서 이것을 부르는 형태로 바뀐다. 아래 `payments reconcile` | `payments reconcile`, `reconcileBatch` |
| 3 | 목록 조회 쿼리 | outbox는 `JdbcTemplate`, payments는 `PaymentRepository`, refunds는 `RefundRepository`, notifications는 notification repository에 `status`별 커서 조회를 더한다(domain 순수 인터페이스 + Jpa 구현). 이름·인자는 문서에 없다(확인 필요). 커서·`size + 1`은 아래 "모든 API에 공통"의 목록 규칙 | 목록 API 4개 |
| 4 | 관리자 컨트롤러와 응답 record | outbox·DLT는 common, payments 목록은 payment, reconcile·refunds는 order, notifications는 notification 모듈에 둔다. 클래스 이름은 문서에 없다(권장: `AdminOutboxController`, `AdminDltController`, `AdminPaymentController`, `AdminReconcileController`, `AdminRefundController`, `AdminNotificationController`). `payment` → `order` 호출은 허용 방향이 아니므로 대사 API는 order 모듈에 둔다 | API 표 |
| 5 | DLT 조회·재처리 코드 | 요청마다 `KafkaConsumer`를 만들어 쓰는 코드를 컨트롤러가 직접 할지 별도 서비스(권장 `AdminDltService`)로 둘지는 문서에 없다(확인 필요). 순서는 아래 `DLT` | DLT API 2개 |
| 6 | 테스트 보조 | `AdminDltTest`가 쓰는 "플래그로 성공하게 바꿀 수 있는 테스트 리스너"(3-2 테스트의 항상 실패 리스너를 바꾼 것). `AdminOutboxTest`의 DEAD 행을 만드는 방법은 문서에 없다(확인 필요: JDBC로 `status = 'DEAD'`로 바꾸는 방법이 있다) | 테스트 |
| 7 | `docs/runbook.md` (Claude) | 7-2 알림마다 "의미 → 볼 곳(대시보드·Kibana 검색) → 조치(위 API)" | 운영 |

**모든 API에 공통**
- **인가**: 컨트롤러에서 따로 검사하지 않는다. 3-4가 `SecurityConfig`에 넣은 `.requestMatchers("/admin/**").hasRole("ADMIN")` 한 줄이 맡는다. 일반 회원은 403(`FORBIDDEN`)
- **실행 로그**: 쓰기 API(retry·replay·reconcile·run)는 메서드 첫 부분에서 `log.info("admin action={} target={} by={}", action, target, memberId)`를 남긴다. `memberId`는 컨트롤러 파라미터 `@CurrentMember UUID memberId`, `target`은 대상 식별자(outbox id, `topic/partition/offset`, paymentId 등)다. `action`에 쓸 문자열은 정해져 있지 않다(확인 필요, 권장: API별 `outbox-retry`·`dlt-replay` 같은 고정 문자열)
- **목록 API**(outbox·payments·refunds·notifications): `cursor`가 없으면(`null`·빈 값은 `decode`에 넘기지 않는다) 첫 페이지, 있으면 `Cursor.decode(cursor)`로 `Cursor(Instant createdAt, UUID id)`를 얻어 그 뒤부터 읽는다. `size + 1`건을 읽어 `size`건만 `CursorPage<T>(items, nextCursor)`로 돌려주고, 다음 커서는 `size`번째 항목의 `new Cursor(createdAt, id).encode()`다(마지막 페이지면 `nextCursor = null`. 규칙은 1-7 상품 목록과 같다). `size`의 기본값·최대값은 이 표에 없다(확인 필요)
- **없는 대상**이거나 **상태가 맞지 않을 때**의 응답은 아래 각 API에서 확인 필요로 모았다. 존재하는 코드는 `NOT_FOUND`(404), `INVALID_STATE_TRANSITION`(409)이다

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

**payments 목록** (payment 모듈 컨트롤러) — `PaymentRepository`(domain 순수 인터페이스)에 상태별 커서 조회를 더하고 `JpaPaymentRepository`에 구현한다. 응답 항목은 `(UUID id, UUID orderId, long amount, int reconcileAttempts, Instant updatedAt)`. 같은 경로 `/admin/api/payments`의 `reconcile`은 order 모듈에 있으므로 두 컨트롤러가 서로 다른 모듈에 있다

**payments reconcile** (order 모듈 관리자 컨트롤러) — `payment` → `order` 호출은 허용 방향이 아니므로 order 모듈에 둔다. 대사 잡이 30초 이상 멈춘 주문을 1분마다 처리하는 일을, 운영자가 **주문 하나에 대해 바로** 하는 API다
1. `reconcileOne(UUID orderId)`(구현물 2): `OrderReconcileService`는 클래스에 `@Transactional`이 없고 `TransactionTemplate`을 쓴다. `queryPgOutcome`·`cancel`이 트랜잭션 밖 전용이므로 컨트롤러나 `reconcileOne` 자체에 `@Transactional`을 붙이면 안 된다
2. 두 번 눌러도 안전한 이유: 이미 PAID가 된 주문은 2-4 `applyPaymentState`의 가드(`PAYMENT_IN_PROGRESS`가 아니면 현재 상태를 그대로 반환)가 막는다
3. **경로의 값은 `orderId`다.** `PaymentApi.find(UUID orderId)`와 `reconcileOne(UUID orderId)`가 모두 `orderId`를 받으므로 payment ID로 찾는 메서드를 새로 만들지 않는다. 목록 응답 항목에 `orderId`가 있어 운영자는 그 값을 쓴다
4. `reconcileOne`의 반환은 `OrderStatus`(처리 뒤 주문 상태)다. 조회도 불확실했거나 다른 흐름이 이미 처리했으면 그 시점의 상태(예: `PAYMENT_IN_PROGRESS`)가 나온다. `reconcileBatch`는 이 값이 `PAID`·`PAYMENT_FAILED`일 때 처리 건수에 센다. 컨트롤러 응답은 `{orderId, status}`다

**refunds** (order 모듈)
- 목록: `RefundRepository`에 상태별 커서 조회를 더한다. 응답 항목의 필드는 이 문서에 정해져 있지 않다(확인 필요, `Refund` 컬럼 기준으로 id·orderId·orderLineId·amount·상태·실패 사유 등)
- `retry`: ① 트랜잭션(`tx.execute`)에서 환불을 읽어 `retryPgCancel()`(구현물 1)을 부른다. FAILED가 아니면 거부된다. 같은 환불에 재시도를 두 번 눌러도 두 번째는 이미 `APPROVED`라 거부된다 ② 트랜잭션 밖에서 `refundProcessor.process(refundId)`(2-8, `RefundStatus` 반환) — APPROVED이므로 `needsPgCancel()`이 참이라 PG 취소부터 다시 한다
- **PG 멱등 키**: 재시도는 새 키로 Toss까지 간다. 2-6 `PaymentApi.cancel`은 같은 키의 `payment_cancel` 행이 이미 `FAILED`면 PG를 부르지 않고 `FAILED`를 재생하므로(1단계 3번), `process`가 쓰는 `refund.pgIdempotencyKey()`(2-8)가 `pgAttempts`가 1 이상이면 `refundId-r{pgAttempts}`로 달라진다. 그래서 `AdminRefundRetryTest`("가짜 PG를 정상으로 → retry → COMPLETED")가 통과한다. Toss가 같은 키의 실패 응답을 어떻게 다루는지는 확인하지 못해(확인 필요) 같은 키를 재사용하지 않는 쪽으로 정했다. `pg_attempts` 칸은 2-8 마이그레이션에서 만든다

**notifications** (notification 모듈)
- 목록: 상태별 커서 조회. 응답 항목의 필드는 정해져 있지 않다(확인 필요, `notification` 컬럼 기준 id·type·recipient·attempts·lastError·updatedAt 등)
- `retry`: `UPDATE notification.notification SET status = 'PENDING', attempts = 0, updated_at = now() WHERE id = :id AND status = 'FAILED'`(`version` 컬럼이 있으므로 `version + 1`도 함께, 다른 UPDATE 규칙과 같게). FAILED의 `next_attempt_at`은 마지막 실패 때의 과거 값이라 다음 `dispatchBatch`(10초 주기)가 바로 집어 간다

**정해지지 않은 것(확인 필요) 정리** — 이 단계 구현 전에 정할 것
- 대상이 없을 때·상태가 맞지 않을 때의 응답 코드(예: outbox retry 0행, DLT replay의 record 없음, 환불·알림 retry가 FAILED가 아닐 때)와 성공 응답 상태·본문

### 완료 확인
- [ ] `common/web/AdminOutboxTest`: DEAD 행 → retry → `relayOnce()` → SENT
- [ ] `common/web/AdminDltTest`: 3-2의 항상 실패 리스너를 이번에는 성공하게 바꿀 수 있는 테스트 리스너(플래그)로 DLT 메시지 1건 생성 → 목록에 보임 → 플래그를 성공으로 → **같은 메시지 두 번 replay → 처리 결과 한 번**(InboxGuard)
- [ ] `order/web/AdminReconcileTest`: UNKNOWN 결제(가짜 PG에는 승인 기록) → reconcile → 주문 PAID
- [ ] `order/web/AdminRefundRetryTest`: PG 거절로 FAILED된 환불 → 가짜 PG를 정상으로 → retry → COMPLETED
- [ ] 일반 회원 → 모든 `/admin/api/**` 403

**리뷰 때 물어볼 것**: 운영 API가 실수로 두 번 호출되면 안전한가? 각각 무엇이 멱등을 보장하나?

---

## 7-4. E2E 시나리오 · 불변식 점검 (L)

**목표**: 시나리오 S1~S7을 테스트로 증명하고, 시나리오가 끝날 때마다 불변식(INV-01~08)을 점검한다(INV-09는 최종적 일관성이라 아래 참고).

**정책**: E2E 테스트는 실제 API(MockMvc)와 실제 Kafka·Redis·Postgres·가짜 PG로 돌린다. 잡과 릴레이는 테스트에서 직접 호출한다. 위치 `src/test/java/com/myroutine/e2e/`.

### 할 일

**구현 순서**: ① `InvariantChecker`와 자체 테스트 → ② 시나리오 클래스(S1 → S7 → 장애) → ③ CI 시간 기록.

**1) 불변식 점검 유틸** — `support/InvariantChecker` (테스트 전용이라 모든 schema를 JDBC로 읽어도 된다)

- **무엇인가**: DB를 직접 조회해 "깨지면 안 되는 규칙"(INV)이 지켜졌는지 확인하는 테스트 도우미. 시나리오가 끝난 DB에서 규칙을 어긴 행을 찾는다. 모듈 경계 규칙은 테스트 코드에 적용되지 않아 `orders`·`payment`·`product`·`settlement` 스키마를 한 클래스에서 읽어도 된다
- **형태**(권장안): `TestFixtures`처럼 `@Component @RequiredArgsConstructor`, 주입은 `JdbcTemplate`. 메서드는 모두 `void`이고 어긋난 행을 찾아 있으면 AssertJ로 실패시킨다 — 어긋난 행의 id를 `queryForList(sql, ...)`로 모아 `assertThat(rows).as("메시지").isEmpty()`. 메시지에 id를 담아야 "어떤 행이 어긋났는지" 보인다
- **SQL 쓰는 법**: "위반 행을 SELECT"하는 쿼리로 쓴다(예: 주문별 `total_amount`와 품목 `line_amount` 합을 비교해 다른 주문만 조회). 테이블·컬럼은 각 모듈 마이그레이션(`src/main/resources/db/migration/{모듈}/`)에서 확인한다. 주문 스키마 이름은 `orders`다(`orders.orders`, `orders.order_line`)
- **누가 부르나**: 시나리오 테스트 끝의 `checkAll()`과 `InvariantChecker` 자체 테스트(완료 확인)

| 메서드 | SQL 요지 | INV |
|---|---|---|
| `orderAmounts()` | 주문마다 `total_amount = Σ line_amount`, PAID 주문은 `payment.amount = total_amount` | INV-01 |
| `paymentsReflected()` | `APPROVED`·`PARTIAL_CANCELLED`·`CANCELLED`인 결제의 주문이 PAID이거나, 결제가 CANCELLED(보상)이고 주문이 PAYMENT_FAILED | INV-02 |
| `stockBalance()` | `available + reserved + sold = received`, 모두 ≥ 0, 7-2 재고 대조 쿼리 결과 0건 | INV-03·04 |
| `refundLimits()` | `refunded_amount ≤ line_amount`, `cancelled_amount ≤ amount`, 주문별 COMPLETED 환불 합 ≤ 주문 총액 | INV-06 |
| `settlementOnce()` | CONFIRMED 품목마다 `settlement_item` 정확히 1행(consumer가 따라잡은 뒤), 정산된 item은 정산 1건에만 | INV-07 |
| `subscriptionCycles()` | `subscription_cycle`에서 `(subscription_id, cycle_date)`마다 행이 1개이고, `status = 'PAID'`인 회차의 `order_id` 주문이 `PAID`이며 한 주문이 두 회차에 연결되지 않는다 | INV-08 |
| `checkAll()` | 위 전부. 실패하면 어떤 행이 어긋났는지 메시지에. **INV-09**(SELLER 권한 ↔ 가게)는 최종적 일관성이라 시나리오 끝에서 바로 검사하면 consumer가 따라잡기 전에 실패할 수 있어 여기에 넣지 않는다(3-1 consumer 테스트의 `Eventually`로 확인한다) | INV-01~08 |

**2) 시나리오** (각 테스트 끝에 `checkAll()`)

**테스트 하나의 뼈대**: 클래스는 `@AutoConfigureMockMvc` + `extends IntegrationTestSupport`(Postgres·가짜 PG `pg`·테이블 초기화가 이미 있다). 한 흐름은 아래 세 가지를 순서대로 섞어 쓴다

| 쓰는 것 | 용도 | 출처 |
|---|---|---|
| `mockMvc.perform(...)` | 사용자가 하는 일(체크아웃, 결제 승인, 취소, 발송 등)은 **실제 API**로 | 위 정책. 토큰은 `fixtures.token(email)` |
| `TestFixtures`(`fixtures.signup`, `openShop`, `registerProduct`, `addToCart`, `checkout`, ...) | 흐름의 준비 단계(회원·가게·상품·장바구니)를 API 없이 빠르게 만든다 | 1-9 이후 쌓인 헬퍼(Part 5의 `registerBillingKey`·`registerSubscribableProduct`·`createSubscription`, Part 6의 `createConfirmedLine` 포함) |
| 아래 표의 메서드를 **직접 호출** | 스케줄이 꺼진 테스트에서 "시간이 지나 잡이 돈 것"을 흉내 낸다 | 아래 표 |
| `pg.on(...)`·`pg.markApproved(...)`, `Eventually.await(...)` | 가짜 PG의 응답 설정, 비동기(consumer) 반영 대기 | 2-3·3-1 |

시나리오 흐름에서 "잡"·"릴레이"라고 적힌 단계는 이 메서드를 부르는 것이다

| 단계 | 호출 | 정의 |
|---|---|---|
| Outbox 릴레이 | `outboxRelay.relayOnce()` — 이벤트를 Kafka로 보낸다. consumer는 비동기라 반영은 `Eventually`로 기다린다 | 3-1 |
| 주문 만료 | `orderExpirationService.expireBatch()` (대상은 JDBC로 `expires_at`을 과거로) | 2-5 |
| 결제 대사 | `orderReconcileService.reconcileBatch()` (대상은 JDBC로 `updated_at`을 30초 전보다 과거로) | 2-6 |
| 자동 구매확정 | `autoConfirmService.confirmBatch()` (수동 확정 API를 써도 된다) | 2-10 |
| 정산 | `settlementService.run(YearMonth)` (지급까지 포함) | 3-4 |
| 알림 발송 | `notificationDispatcher.dispatchBatch()` | 3-5 |
| 구독 회차 | `subscriptionCycleService.runDue()` | Part 5 |
| Kafka 중단·재개 | `kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec()` / `unpauseContainerCmd` | 3-1 `OutboxRelayTest`. `kafka` 필드가 `IntegrationTestSupport`의 package-private이면 `e2e` 패키지에서 못 쓴다 — 접근을 열지 헬퍼를 둘지는 확인 필요 |

**클래스와 메서드 이름은 각 단계(2-5·2-6·2-10·3-1·3-4·3-5·Part 5)의 문서를 따른다.** 정확한 이름·시그니처는 그 문서가 기준이고, 위 표는 이 문서 안에서 한눈에 보기 위한 요약이다

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

**3) CI**: 전체 테스트 시간을 CI 로그에서 확인해 PR에 기록. 10분을 넘으면 원인(컨테이너 기동, 느린 테스트 상위 10개)을 정리한다(Gradle `--profile` 또는 테스트 리포트).

### 완료 확인
- [ ] 시나리오 8개 통과, 각 시나리오 끝에 불변식 위반 0
- [ ] `InvariantChecker` 자체 테스트: 일부러 깨뜨린 데이터(예: JDBC로 `stock.available` 변경) → 해당 메서드가 실패를 보고
- [ ] CI 실행 시간 기록

---

## 7-5. README · 회고 · Stage 1 릴리스 (M)

**목표**: 리포만 보고 프로젝트를 이해하고 실행할 수 있다. 이력서에 쓸 근거가 정리된다.

### 할 일
1. (Claude) README: 소개, 아키텍처 다이어그램, 실행 방법(인프라 → 앱 → 관측 스택), 운영 환경(VM)과 배포 흐름([ADR-011](../adr/ADR-011-ops-practice-environment.md)), 문서 안내, 핵심 설계 요약(정합성·동시성), 테스트로 증명한 것 목록
2. (사용자 작성, Claude 리뷰) `docs/retrospectives/stage1.md`: 설계와 달라진 점과 이유, 리뷰에서 반복된 지적, 어려웠던 점, 다시 한다면
3. (사용자 작성, Claude 리뷰) 이력서 bullet 초안 — 문장마다 근거 링크(테스트 클래스, ADR, 수치)
4. (Claude 초안, 사용자 확정) Stage 2 준비: 시드 데이터 규모·생성 방식, k6 시나리오 목록(NFR-PERF-01~06) → Stage 2 로드맵 문서
5. develop → main, 태그 `v1.0.0` — develop → main PR을 **merge commit**으로 합치고(squash 아님), main에 태그를 붙인 뒤 GitHub Release를 만든다. Release 노트에는 완료한 단계 목록, 주요 ADR, 알려진 한계를 쓴다([git-policy §7](../git-policy.md#7-릴리스와-태그). 같은 표에 Part 7 완료 `v0.7.0`도 있다: 이 단계에서 `v0.7.0`을 따로 찍을지는 확인 필요)
6. (Claude 초안, 사용자 확정) Stage 1.5 준비: [ADR-012](../adr/ADR-012-kubernetes-zero-downtime.md)의 "확인 필요"·미정 항목(노드 수, 상태 서비스 위치)을 확정하고 K-1~K-5 로드맵 문서로 구체화. Stage 2 Baseline은 그 이후 k3s 환경에서 측정한다

**리뷰 때 물어볼 것**: 이력서 문장마다 근거(테스트, 문서, 수치)가 있는가?
