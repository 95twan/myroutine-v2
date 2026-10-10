# Part 7. 관측 · 운영 · 마무리

> 버전 0.5 · 2026-10-09 · **버전 확인·운영 환경 반영**: logstash-logback-encoder `9.0`(Jackson 3·Logback 1.5 기반), ELK·Prometheus·Grafana 이미지 태그를 확인해 고정, Spring Boot가 Kafka 클라이언트 지표를 자동 등록하는 것을 확인, 1-11 운영 VM에서는 Prometheus가 `app:8081`을 긁도록 적었다
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
- 7-3의 운영 API는 모두 `/admin/api/**`(ADMIN)이고, 실행할 때마다 `log.info("admin action={} target={} by={}", ...)`를 남긴다.

---

## 7-1. ELK: 로그 수집과 traceId 검색 (M)

**목표**: 앱 로그를 Kibana에서 검색한다. traceId 하나로 HTTP 요청 → DB → Kafka 소비 → 알림까지 한 흐름이 보인다.

**왜 지금**: 기능이 다 모였고, 흐름이 길어져서(결제 → 이벤트 → 정산 → 알림) 로그 파일로는 따라가기 어렵다. Stage 2 성능 분석도 이것이 전제다.

**새로 등장**: Logstash(로그 수신·가공) → Elasticsearch(저장) → Kibana(검색·화면), logstash-logback-encoder(로그를 JSON으로)

**정책 (이 단계에서 정함)**
- 콘솔 로그는 지금처럼 사람이 읽는 형식, Logstash로 보내는 로그는 JSON. Logstash 전송은 Spring 프로필 `elk`일 때만(로컬에서 `local,elk`로 실행).
- 로그 인덱스: `logs-myroutine-YYYY.MM.dd`. 보관 기간은 로컬이라 정하지 않는다.

### 할 일

**1) 의존성·설정**
- `implementation 'net.logstash.logback:logstash-logback-encoder:9.0'` — 2026-10-09 Maven Central 최신. 9.0의 pom은 `tools.jackson.core:jackson-databind`(Jackson 3)와 `logback-core` 1.5.x에 의존해 Boot 4.1.1(Jackson 3.1.5, Logback 1.5.38)과 맞는다. Boot BOM이 관리하지 않아 버전을 적는다
- `src/main/resources/logback-spring.xml`: `<springProfile name="elk">`에 `LogstashTcpSocketAppender`(destination `${LOGSTASH_HOST:-localhost}:5000`, `LogstashEncoder`), 그 외에는 Boot 기본 콘솔. 콘솔 패턴은 1-3의 `logging.pattern.level` 유지
- JSON 필드: `@timestamp`, `level`, `logger_name`, `message`, `stack_trace`, MDC 전부(`traceId`, `memberId`, `orderId`, `job`, `eventId`), `app: myroutine`

**2) MDC 보강**

| 키 | 넣는 곳 | 빼는 곳 |
|---|---|---|
| `traceId` | `TraceIdFilter`, `JobRunner`, `EventContext`/`InboxGuard` (이미 있음) | 각자 finally |
| `memberId` | `JwtAuthenticationFilter` 인증 성공 직후 | `TraceIdFilter`의 finally에서 함께 제거 |
| `orderId` | 주문 흐름 서비스(`ConfirmPaymentService`, `OrderPaymentApplier`, `CancelOrderLineService`, 만료·대사 잡의 주문별 처리)에서 `try (var c = MDC.putCloseable("orderId", ...))` | try-with-resources가 제거 |
| `job` | `JobRunner` (이미 있음) | |

**3) docker**
- `docker/logstash/pipeline/logstash.conf`: `input { tcp { port => 5000 codec => json_lines } }` → `output { elasticsearch { hosts => ["http://elasticsearch:9200"] index => "logs-myroutine-%{+YYYY.MM.dd}" } }`
- compose: `docker.elastic.co/logstash/logstash:9.4.8`(5000), `docker.elastic.co/kibana/kibana:9.4.8`(5601) — ES(6-2)와 같은 버전
- Kibana: 데이터 뷰 `logs-myroutine-*`, 저장 검색 3개 — "traceId로 흐름 보기"(`traceId : "..."`, 시간 오름차순, 열 level·logger·message·job), "ERROR만", "결제 UNKNOWN"(`message : "UNKNOWN" and logger_name : *payment*`) → `docker/kibana/saved-objects.ndjson`로 내보내기

**4) 민감정보 점검**: 아래 테스트로 확인하고, 걸리면 로그 문장을 고친다

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

**1) 노출**
- 의존성 `io.micrometer:micrometer-registry-prometheus`
- `management.endpoints.web.exposure.include: health,prometheus`, `management.metrics.tags.application: myroutine`
- `SecurityConfig`: `/actuator/prometheus` 허용(관리 포트에만 열리고 외부에 노출하지 않으므로, 1-3에서 확인했듯 같은 체인이 관리 포트에도 적용된다)

**2) 비즈니스 메트릭**

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

**3) 재고 정합성 점검 잡** — product `StockCheckJob`: 매일 05:00(Asia/Seoul), 잡 이름 `stock-check`. 재고 등식 자체는 CHECK가 막으므로, **재고 행과 기록(이력·예약)의 대조**를 본다
```sql
SELECT s.product_id FROM product.stock s
LEFT JOIN (SELECT product_id, SUM(quantity) q FROM product.stock_movement WHERE type IN ('RECEIVE','ADJUST') GROUP BY product_id) m ON m.product_id = s.product_id
LEFT JOIN (SELECT product_id, SUM(quantity) q FROM product.stock_reservation WHERE status = 'HELD' GROUP BY product_id) r ON r.product_id = s.product_id
WHERE s.received <> COALESCE(m.q, 0) OR s.reserved <> COALESCE(r.q, 0)
```
- 불일치 상품마다 `log.error`, 개수를 Gauge에. (INV-03·04)

**4) Prometheus·Grafana**
- 이미지: `prom/prometheus:v3.15.0`, `grafana/grafana:13.2.3`(2026-10-09 Docker Hub의 최신 안정 태그)
- `docker/prometheus/prometheus.yml`: scrape 15초, path `/actuator/prometheus`, 대상은 환경마다 다르다
  - 로컬(앱은 IDE·`bootRun`으로 호스트에서 실행): `host.docker.internal:8081` — Docker Desktop(macOS)에서는 그대로 된다. Linux에서 호스트의 앱을 긁으려면 prometheus 서비스에 `extra_hosts: ["host.docker.internal:host-gateway"]`가 필요하다
  - 운영 VM(1-11 `docker-compose.ops.yml`의 `observability` 프로필): 앱도 컨테이너이므로 `app:8081`(관리 포트는 호스트에 publish하지 않지만 같은 compose 네트워크에서는 닿는다). 파일을 둘로 두거나(`prometheus.yml`, `prometheus.ops.yml`) 대상만 바꾼다
- `docker/grafana/provisioning/datasources/prometheus.yml`, `dashboards/*.json` 3개
  - **서비스 개요**: 요청률·에러율·p95(`http_server_requests_seconds`), JVM 힙, HikariCP 활성 커넥션, Kafka consumer lag — Boot 4.1.1은 Kafka consumer·producer 팩토리에 Micrometer 리스너(`MicrometerConsumerListener`)를 자동으로 붙인다(jar로 확인). Kafka 클라이언트 지표 `records-lag-max`가 Prometheus에서는 `kafka_consumer_fetch_manager_records_lag_max`로 보일 것으로 예상한다(확인 필요: `/actuator/prometheus` 출력에서 `lag`로 검색해 실제 이름을 쓴다)
  - **결제·정합성**: `payment_unknown_*`, `saga_stuck_count`, `stock_balance_mismatch_count`, `pg_call_seconds` p95·결과별 비율, `stock_reservation_expired_total` 증가율
  - **Kafka·Outbox**: `outbox_*`, `kafka_dlt_records_total`
- `docker/grafana/provisioning/alerting/rules.yml` (Grafana 알림 규칙을 파일로)

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
- 재고 불일치: 로컬 DB에서 `stock.received`와 `available`을 같은 양만큼 바꾸고(CHECK 통과) 점검 잡 실행 → Firing

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

| API | 처리 | 위치 |
|---|---|---|
| `GET /admin/api/outbox?status=DEAD&cursor=` | DEAD 행 목록(id, topic, aggregateId, attempts, lastError, createdAt) | common |
| `POST /admin/api/outbox/{id}/retry` | DEAD → READY, `attempts = 0`, `next_attempt_at = now` | common |
| `GET /admin/api/dlt/{topic}?limit=50` | 짧게 쓰는 `KafkaConsumer`(그룹 없이 `assign`)로 `{topic}.dlt` 각 파티션 끝에서 최대 limit개 읽기 → `(partition, offset, key, value 앞 500자, 예외 헤더)` | common |
| `POST /admin/api/dlt/{topic}/{partition}/{offset}/replay` | 그 레코드 하나를 읽어 원래 토픽(`.dlt` 제거)으로 같은 key·value를 `send().get()` | common |
| `GET /admin/api/payments?status=UNKNOWN&cursor=` | 결제 목록(id, orderId, amount, reconcileAttempts, updatedAt) | payment |
| `POST /admin/api/payments/{id}/reconcile` | 그 주문만 바로 대사(2-6 로직) → 결과 반환 | order |
| `GET /admin/api/refunds?status=FAILED&cursor=` · `POST /admin/api/refunds/{id}/retry` | FAILED → APPROVED → `refundProcessor.process` | order |
| `GET /admin/api/notifications?status=FAILED&cursor=` · `POST /admin/api/notifications/{id}/retry` | FAILED → PENDING, `attempts = 0` | notification |
| `POST /admin/api/settlements/run?period=` | (3-4에서 만듦) | settlement |

- 주문 대사 단건 실행을 위해 `OrderReconcileService`에 `reconcileOne(UUID orderId)`를 뽑아낸다(배치는 이것을 반복 호출)
- `payment` → `order` 호출은 허용 방향이 아니므로, 결제 대사 API는 **order 모듈의 관리자 컨트롤러**(`/admin/api/payments/{id}/reconcile`)에서 `PaymentApi.find` → `reconcileOne`으로 구현한다
- (Claude) `docs/runbook.md`: 7-2 알림마다 "의미 → 볼 곳(대시보드·Kibana 검색) → 조치(위 API)"

### 완료 확인
- [ ] `common/web/AdminOutboxTest`: DEAD 행 → retry → `relayOnce()` → SENT
- [ ] `common/web/AdminDltTest`: 3-2의 항상 실패 리스너를 이번에는 성공하게 바꿀 수 있는 테스트 리스너(플래그)로 DLT 메시지 1건 생성 → 목록에 보임 → 플래그를 성공으로 → **같은 메시지 두 번 replay → 처리 결과 한 번**(InboxGuard)
- [ ] `order/web/AdminReconcileTest`: UNKNOWN 결제(가짜 PG에는 승인 기록) → reconcile → 주문 PAID
- [ ] `order/web/AdminRefundRetryTest`: PG 거절로 FAILED된 환불 → 가짜 PG를 정상으로 → retry → COMPLETED
- [ ] 일반 회원 → 모든 `/admin/api/**` 403

**리뷰 때 물어볼 것**: 운영 API가 실수로 두 번 호출되면 안전한가? 각각 무엇이 멱등을 보장하나?

---

## 7-4. E2E 시나리오 · 불변식 점검 (L)

**목표**: 핵심 시나리오 S1~S7을 테스트로 증명하고, 시나리오가 끝날 때마다 불변식(INV-01~07)을 점검한다.

**정책**: E2E 테스트는 실제 API(MockMvc)와 실제 Kafka·Redis·Postgres·가짜 PG로 돌린다. 잡과 릴레이는 테스트에서 직접 호출한다. 위치 `src/test/java/com/myroutine/e2e/`.

### 할 일

**1) 불변식 점검 유틸** — `support/InvariantChecker` (테스트 전용이라 모든 schema를 JDBC로 읽어도 된다)

| 메서드 | SQL 요지 | INV |
|---|---|---|
| `orderAmounts()` | 주문마다 `total_amount = Σ line_amount`, PAID 주문은 `payment.amount = total_amount` | INV-01 |
| `paymentsReflected()` | `APPROVED`·`PARTIAL_CANCELLED`·`CANCELLED`인 결제의 주문이 PAID이거나, 결제가 CANCELLED(보상)이고 주문이 PAYMENT_FAILED | INV-02 |
| `stockBalance()` | `available + reserved + sold = received`, 모두 ≥ 0, 7-2 재고 대조 쿼리 결과 0건 | INV-03·04 |
| `refundLimits()` | `refunded_amount ≤ line_amount`, `cancelled_amount ≤ amount`, 주문별 COMPLETED 환불 합 ≤ 주문 총액 | INV-06 |
| `settlementOnce()` | CONFIRMED 품목마다 `settlement_item` 정확히 1행(consumer가 따라잡은 뒤), 정산된 item은 정산 1건에만 | INV-07 |
| `checkAll()` | 위 전부. 실패하면 어떤 행이 어긋났는지 메시지에 |

**2) 시나리오** (각 테스트 끝에 `checkAll()`)

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
5. develop → main, 태그 `v1.0.0`
6. (Claude 초안, 사용자 확정) Stage 1.5 준비: [ADR-012](../adr/ADR-012-kubernetes-zero-downtime.md)의 "확인 필요"·미정 항목(노드 수, 상태 서비스 위치)을 확정하고 K-1~K-5 로드맵 문서로 구체화. Stage 2 Baseline은 그 이후 k3s 환경에서 측정한다

**리뷰 때 물어볼 것**: 이력서 문장마다 근거(테스트, 문서, 수치)가 있는가?
