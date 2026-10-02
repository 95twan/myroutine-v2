# Part 3. 이벤트

> 버전 0.5 · 2026-10-02 · **덜어내기**: 가게 개설·폐업 advisory lock 제거(재조회로 충분), 폐업 이벤트의 남은 가게 수 필드 제거, 재시도 간격은 고정값으로(백오프는 제안), 회원 역할은 단일 `role`
> 0.4 · 2026-10-02 · 예치금 제거: 정산 지급은 `PayoutGateway`(은행 송금 Mock), 충전 이벤트·지갑 탈퇴 조건 삭제
> 0.3 · 이 문서만 보고 개발할 수 있게 구체화

> **끝나면**: 가게 개설·폐업에 따른 역할 변경, 정산, 알림 같은 **후속 작업**이 비동기로 처리된다. 서버가 죽거나 Kafka가 멈춰도 이벤트가 유실되지 않고, 두 번 전달돼도 결과는 한 번이다.
> **인프라 추가**: **Kafka**
> **릴리스**: `v0.3.0`

| 단계 | 제목 | 크기 |
|---|---|---|
| 3-1 | 이벤트가 필요한 이유 · Kafka · Outbox (가게 개설 → SELLER 역할) | L |
| 3-2 | 멱등 소비 · 재시도 · DLT · 가게 폐업 | M |
| 3-3 | 기존 흐름에 이벤트 발행 붙이기 | M |
| 3-4 | 정산: 적재 · 정산 잡 · 지급 | L |
| 3-5 | 알림 | M |
| 3-6 | 회원 탈퇴 | M |

---

## Part 3 공통 규칙 (Part 1·2 공통 규칙에 더해서)

> [Part 1 공통 규칙](part1-foundation.md#part-1-공통-규칙-모든-단계에-적용), [Part 2 공통 규칙](part2-order-money.md#part-2-공통-규칙-part-1-공통-규칙에-더해서)은 그대로 적용된다. 리뷰 기준도 같다.

### P. 이벤트 작성 규칙

| 항목 | 규칙 |
|---|---|
| 위치 | 이벤트 record와 토픽 상수는 **발행하는 모듈**의 `{module}.api.event` 패키지. 구독하는 모듈은 이것을 import한다(그래서 구독자는 발행자의 `api`에 의존한다) |
| 토픽 상수 | `public final class OrderTopics { public static final String ORDER_PAID = "order.order-paid.v1"; ... }` — 이름 규칙 `{module}.{event}.v{n}` |
| 이벤트 record | 이름은 과거형 `XxxEvent`. 필드는 원시 타입·`UUID`·`String`·`long`·`Instant`·`List`만(엔티티·`Money` 금지, 금액은 `long`) |
| 키 | 집계 ID(aggregateId). 같은 집계의 이벤트는 같은 파티션으로 가서 순서가 지켜진다 |
| 발행 | **상태 변경과 같은 트랜잭션** 안에서 `OutboxPublisher.publish(...)`. `KafkaTemplate`을 비즈니스 코드에서 직접 부르지 않는다 |
| 소비 | 모든 consumer는 `InboxGuard.runOnce(...)` 안에서 처리한다(3-2부터). 같은 이벤트를 두 번 받아도 결과가 한 번 |
| 직렬화 | Kafka 메시지 값은 **JSON 문자열**(`StringSerializer`). 봉투 → JSON 변환은 `JsonMapper`로 직접 한다(Kafka용 Jackson 직렬화 설정을 따로 다루지 않기 위해) |
| 문서 | 새 이벤트·필드는 [이벤트 카탈로그](../02-design/05-events.md)와 일치해야 한다(Claude가 리뷰 때 문서를 맞춘다) |

### Q. 비동기 테스트
- Kafka는 Testcontainers로 띄운다(3-1). 스케줄러가 꺼져 있으므로 **릴레이는 테스트에서 직접 호출**한다(`outboxRelay.relayOnce()`). consumer(`@KafkaListener`)는 테스트 중에도 동작한다.
- "잠시 후 반영"은 직접 만든 헬퍼로 기다린다: `support/Eventually.await(Duration timeout, ThrowingRunnable assertion)` — 100ms마다 단언을 다시 실행하고, 시간이 다 되면 마지막 실패를 던진다. 새 라이브러리(Awaitility)는 쓰지 않는다.
- 이전 테스트가 남긴 메시지가 다음 테스트에서 소비될 수 있다(테이블은 비워졌는데 메시지는 남음). consumer는 **대상이 없으면 경고 로그 후 정상 종료**해야 한다(재시도 대상이 아님).

### R. Part 3 에러 코드

| 코드 | HTTP | 메시지 | 정의 위치 | 단계 |
|---|---|---|---|---|
| `SHOP_HAS_ACTIVE_ORDERS` | 422 | 진행 중인 주문이 있어 폐업할 수 없습니다. | `OrderErrorCode` | 3-2 |
| `SETTLEMENT_NOT_FOUND` | 404 | 정산 내역을 찾을 수 없습니다. | `SettlementErrorCode` | 3-4 |
| `MEMBER_HAS_ACTIVE_RESOURCES` | 422 | 탈퇴할 수 없는 상태입니다. (details.reasons) | `MemberErrorCode` | 3-6 |

### S. 모듈 의존 (Part 3 끝 기준, Part 2 표에 추가·변경된 것만)

| 모듈 | `allowedDependencies` | 추가 이유 |
|---|---|---|
| member | `common`, `shop::api` | shop 이벤트 구독, 활성 가게 수 조회 |
| product | `common`, `shop::api` | shop-closed 구독 (변경 없음) |
| order | Part 2 + 없음 | `order::api` 공개 시작(이벤트·`OrderTopics`) |
| settlement | `common`, `order::api`, `shop::api`, `member::api` | 이벤트 구독, 내 가게 목록 조회, 탈퇴 조건 구현 |
| notification | `common`, `member::api`, `shop::api`, `order::api`, `payment::api`, `settlement::api` | 이벤트 구독, 수신자 연락처·가게 주인 조회 |

- **순환 금지**: member가 shop을 참조하므로 shop은 member를 참조할 수 없다. 그래서 "운영 중인 가게가 있으면 탈퇴 불가"는 shop이 `WithdrawalPrecondition`을 구현하지 않고 **member가 `ShopApi.countActiveShops`로 직접 확인**한다.

---

## 3-1. 이벤트가 필요한 이유 · Kafka · Outbox (가게 개설 → SELLER 역할) (L)

**목표**: 가게를 열면 회원에게 SELLER 역할이 붙는다. 이 연결을 **Outbox + Kafka**로 만든다.

**왜 지금**: 처음으로 "A 모듈의 변화에 B 모듈이 반응해야 하는데, A가 B를 직접 부르면 곤란한" 상황이 나온다.
- shop이 member를 직접 호출하면: 의존 방향이 늘어나고(member는 이미 shop을 조회함 → 순환), 역할 변경이 실패하면 가게 개설까지 실패한다.
- `@TransactionalEventListener(AFTER_COMMIT)`로 메모리 안에서 알리면: 커밋 직후 서버가 죽으면 알림이 사라진다(As-Is ORD-09, SHOP-05).
- **Outbox**: 가게 저장과 "보낼 이벤트" 저장을 **같은 트랜잭션**에 넣고, 별도 릴레이가 Kafka로 보낸다. 커밋된 변경의 이벤트는 사라지지 않는다.

**새로 등장**

| 개념·도구 | 한 줄 설명 |
|---|---|
| Kafka | 이벤트를 저장하고 구독자에게 전달하는 브로커. 지금은 같은 앱 안의 다른 모듈이 구독한다 (ADR-003) |
| Transactional Outbox | 상태 변경 + 이벤트 행을 한 트랜잭션에 저장 → 릴레이가 발행 |
| 리스(lease) 선점 | 릴레이가 행을 `PROCESSING + locked_until`로 표시하고 가져간다. 릴레이가 죽으면 시간이 지나 다시 READY로 |
| 이벤트 봉투 | `eventId, eventType, occurredAt, aggregateId, traceId, payload` |
| `Propagation.MANDATORY` | 트랜잭션이 없으면 예외. "outbox 저장은 반드시 상태 변경 트랜잭션 안에서"를 코드로 강제 |

**정책 (이 단계에서 정함)**
- 토픽은 파티션 3, 복제 1. 앱이 시작할 때 `NewTopic` 빈으로 만든다(브로커 자동 생성에 기대지 않음).
- 릴레이: 1초마다, 한 번에 100건, 리스 30초. 발행에 실패하면 **10초 뒤** 다시. **10번 실패하면 DEAD**.
- SENT 행 정리는 Part 7(운영)에서 한다.

### 할 일

**1) 인프라**
- `docker/docker-compose.yml`에 Kafka 추가: 이미지 `apache/kafka`(KRaft 단일 브로커, 확인 필요: 최신 안정 태그를 정해 고정), 포트 `127.0.0.1:9092:9092`
- 의존성(확인 필요: Boot 4 스타터 이름): `implementation 'org.springframework.boot:spring-boot-starter-kafka'`, 테스트 `org.testcontainers:testcontainers-kafka`
- `application.yaml`
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
      properties:
        enable.idempotence: true
        delivery.timeout.ms: 10000
        request.timeout.ms: 5000
        max.block.ms: 5000
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
    listener:
      ack-mode: record
```
- `delivery.timeout.ms`를 줄인 이유: 기본값(2분)이면 릴레이가 "실패"로 기록한 뒤에도 프로듀서가 뒤에서 계속 재전송해 늦게 도착할 수 있다. 줄여도 중복은 생길 수 있으므로 consumer 멱등이 전제다.
- `IntegrationTestSupport`: `@ServiceConnection static final KafkaContainer kafka = new KafkaContainer("apache/kafka:{같은 태그}")`(패키지 `org.testcontainers.kafka`), static 블록을 `Startables.deepStart(postgres, kafka).join()`으로

**2) common.outbox**

마이그레이션 `db/migration/common/V{...}__common_create_outbox_event.sql`

| 컬럼 | 타입 | NULL | 제약·설명 |
|---|---|---|---|
| id | uuid | X | `pk_outbox_event` — 이 값이 봉투의 `eventId` |
| aggregate_type | varchar(50) | X | 예: `SHOP` |
| aggregate_id | uuid | X | Kafka 키 |
| event_type | varchar(100) | X | 이벤트 record 이름, 예: `ShopOpenedEvent` |
| topic | varchar(100) | X | |
| payload | jsonb | X | 이벤트 record의 JSON |
| trace_id | varchar(64) | O | 저장 시점 MDC traceId |
| status | varchar(20) | X | `ck_outbox_event_status`: `IN ('READY','PROCESSING','SENT','DEAD')` |
| attempts | int | X | DEFAULT 0 |
| next_attempt_at | timestamptz | X | |
| locked_until | timestamptz | O | |
| sent_at | timestamptz | O | |
| last_error | varchar(500) | O | |
| created_at, updated_at | timestamptz | X | |

- 인덱스: `idx_outbox_event_ready ON common.outbox_event (next_attempt_at) WHERE status = 'READY'`, `idx_outbox_event_processing ON common.outbox_event (locked_until) WHERE status = 'PROCESSING'`

| 클래스 | 규격 |
|---|---|
| `EventEnvelope` | `public record EventEnvelope(UUID eventId, String eventType, Instant occurredAt, UUID aggregateId, String traceId, JsonNode payload)` (`tools.jackson.databind.JsonNode`) |
| `OutboxPublisher` | `@Component`. `@Transactional(propagation = Propagation.MANDATORY) public void publish(String topic, String aggregateType, UUID aggregateId, Object event)` — `JdbcTemplate`으로 INSERT(READY, `next_attempt_at = now`, `trace_id = MDC.get(TraceIds.MDC_KEY)`, payload는 `jsonMapper.writeValueAsString(event)`를 `?::jsonb`로) |
| `OutboxRelay` | `@Component`. `public int relayOnce()` (아래). 스케줄은 `OutboxRelayJob`(`@Scheduled(fixedDelay = 1000)` → `jobRunner.run("outbox-relay", relay::relayOnce)`) |
| `EventEnvelopes` | `@Component`. `<T> TypedEnvelope<T> parse(String json, Class<T> payloadType)` → `record TypedEnvelope<T>(EventEnvelope envelope, T payload)`. 파싱 실패는 `tools.jackson.core.JacksonException`을 그대로 던진다(3-2에서 재시도하지 않을 예외로 분류) |
| `EventContext` | `public final class`. `static void run(EventEnvelope envelope, Runnable action)` → MDC에 `traceId`(봉투 값, 없으면 새로), `eventId` 넣고 실행 후 제거 |

`OutboxRelay.relayOnce()` 순서
1. 회수(짧은 트랜잭션): `UPDATE common.outbox_event SET status = 'READY', locked_until = NULL, updated_at = now() WHERE status = 'PROCESSING' AND locked_until < now()`
2. 선점(짧은 트랜잭션, `JdbcTemplate.query`로 `RETURNING` 결과를 읽는다):
```sql
UPDATE common.outbox_event SET status = 'PROCESSING', locked_until = now() + interval '30 seconds', updated_at = now()
 WHERE id IN (SELECT id FROM common.outbox_event
               WHERE status = 'READY' AND next_attempt_at <= now()
               ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED)
RETURNING id, aggregate_id, event_type, topic, payload, trace_id, attempts, created_at
```
3. **트랜잭션 밖에서** 행마다: 봉투 JSON을 만들어 `kafkaTemplate.send(topic, aggregateId.toString(), json).get(5, SECONDS)`
4. 성공 → `SET status = 'SENT', sent_at = now(), locked_until = NULL` / 실패 → `attempts + 1`이 10 이상이면 `DEAD`, 아니면 `READY` + `next_attempt_at = now() + 10초` + `last_error`
5. 발행 성공 건수 반환
- 선점 쿼리의 `now()`는 DB 시각이다(릴레이는 앱 `Clock` 대신 DB 시각으로 일관되게 동작).

**3) 가게 개설 이벤트**
- `shop.api.event`: `ShopTopics.SHOP_OPENED = "shop.shop-opened.v1"`, `public record ShopOpenedEvent(UUID shopId, UUID memberId)`
- `shop.infrastructure.ShopTopicConfig`: `@Bean NewTopic shopOpenedTopic()` → `TopicBuilder.name(SHOP_OPENED).partitions(3).replicas(1).build()` (이후 토픽도 각 발행 모듈의 infrastructure에 같은 방식으로)
- `OpenShopService.open`: 저장 직후 같은 트랜잭션에서 `outboxPublisher.publish(SHOP_OPENED, "SHOP", shop.getId(), new ShopOpenedEvent(shop.getId(), memberId))`

**4) member — 역할 부여**
- `Member.promoteToSeller()`: USER면 SELLER로, 이미 SELLER·ADMIN이면 아무것도 안 함. `Member.demoteToUser()`: SELLER면 USER로, 아니면 아무것도 안 함
- `MemberRoleService` (member.application): `@Transactional void grantSeller(UUID memberId)` → 회원이 없으면 `log.warn` 후 return(공통 규칙 Q)
- `ShopEventConsumer` (member.infrastructure): `@KafkaListener(topics = ShopTopics.SHOP_OPENED, groupId = "member") void onShopOpened(String message)` → `parse(message, ShopOpenedEvent.class)` → `EventContext.run(envelope, () -> roleService.grantSeller(payload.memberId()))`
- `member/package-info.java`: `allowedDependencies = {"common", "shop::api"}`
- 역할 반영 전에도 판매자 기능은 소유권으로 동작한다(1-6). 토큰의 role은 다음 로그인 때 갱신된다.

**5) 테스트 헬퍼**
- `support/Eventually` (공통 규칙 Q)
- `support/OutboxAssert`: `List<Map<String, Object>> rows(String topic)` — `JdbcTemplate`으로 `common.outbox_event` 조회

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `common/outbox/OutboxPublisherTest` | [ ] 트랜잭션 없이 `publish` → `IllegalTransactionStateException` / [ ] 트랜잭션 안에서 publish 후 롤백 → 행 없음 / [ ] `trace_id`에 MDC 값 |
| `common/outbox/OutboxRelayTest` | [ ] READY 3건 → `relayOnce()` = 3, 모두 SENT, 테스트용 `KafkaConsumer`로 토픽에서 봉투 3개 읽힘(eventId = outbox id) / [ ] **Kafka를 멈춘 상태**(`kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec()`)에서 `relayOnce()` → 0, 행은 READY·`attempts` 1 → unpause → JDBC로 `next_attempt_at`을 과거로 → `relayOnce()` → SENT / [ ] PROCESSING·`locked_until` 과거인 행 → `relayOnce()`가 회수 후 발행 / [ ] `attempts` 9인 행이 실패 → DEAD |
| `shop/application/ShopOpenedFlowTest` | [ ] 가게 개설 → outbox 1건(`shop.shop-opened.v1`) → `relayOnce()` → `Eventually`로 회원 role이 SELLER / [ ] 사업자번호 중복으로 개설 실패 → outbox 0건 / [ ] 개설 요청의 `X-Request-Id`와 outbox `trace_id`가 같다 |

**리뷰 때 물어볼 것**
- Kafka에 보내고 SENT로 바꾸기 전에 죽으면? 그래서 consumer가 반드시 해야 하는 것은?
- 폴링 방식 Outbox와 CDC(Debezium)의 차이는?
- 원 프로젝트에선 이 흐름이 Saga였는데 왜 단순해졌나? (소유권 인가, ADR-006)
- 선점을 `FOR UPDATE SKIP LOCKED` + 상태값(PROCESSING)으로 하는 이유는? 락만 쓰면 무엇이 문제였나? (As-Is PAY-03)

---

## 3-2. 멱등 소비 · 재시도 · DLT · 가게 폐업 (M)

**목표**
- consumer가 같은 이벤트를 두 번 받아도 한 번만 처리한다. 계속 실패하는 메시지는 DLT에 모인다.
- 가게 폐업: 진행 중인 주문이 없어야 폐업할 수 있고, 폐업하면 상품이 단종되고, 마지막 가게였다면 SELLER가 회수된다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| Inbox (멱등 소비) | `processed_message(consumer, eventId)`에 기록하고 이미 있으면 건너뛴다. **기록과 처리를 같은 트랜잭션**에 |
| 재시도 · DLT | 일시적 오류는 3번 재시도, 그래도 실패하면 `{topic}.dlt`. 역직렬화 오류는 바로 DLT |
| 의존 역전 | 폐업 조건(진행 중 주문)은 order 데이터인데 shop → order 의존은 순환. shop이 `ShopClosePrecondition` 인터페이스를 정의하고 order가 구현 |

**정책 (이 단계에서 정함)**
- 재시도: 1초 → 2초 → 4초(3번) 후 DLT. 재시도하지 않는 예외: `JacksonException`(역직렬화), `IllegalArgumentException`, `BusinessException`(비즈니스 규칙 위반은 다시 해도 같음)
- DLT 토픽은 원본과 같은 파티션 수(3). DLT 조회·재처리 API는 Part 7.
- 폐업을 막는 진행 중 가게주문: `PENDING`, `PAID`, `SHIPPED`, `DELIVERED`(구매확정·반품 종결 전). 구독은 Part 5에서 추가.

### 할 일

**1) common.inbox**

마이그레이션 `db/migration/common/V{...}__common_create_processed_message.sql`: `common.processed_message` — `consumer varchar(100) X`, `event_id uuid X`, `processed_at timestamptz X`, `pk_processed_message (consumer, event_id)`

`InboxGuard` (`@Component`)
- `@Transactional public void runOnce(String consumer, EventEnvelope envelope, Runnable action)`
  1. `EventContext`처럼 MDC 설정
  2. `INSERT INTO common.processed_message ... ON CONFLICT DO NOTHING` → 0이면 `log.info("duplicate event skipped")` 후 return
  3. `action.run()` — 같은 트랜잭션. 예외가 나면 2의 INSERT도 롤백되어 재시도 때 다시 처리된다
  4. MDC 정리(finally)
- consumer 이름 규칙: `{구독 모듈}.{이벤트}` 예: `member.shop-opened`

**2) common.config.KafkaConfig**
- `@Bean CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template)`:
  - `DeadLetterPublishingRecoverer(template, (record, ex) -> new TopicPartition(record.topic() + ".dlt", record.partition()))`
  - `new DefaultErrorHandler(recoverer, new ExponentialBackOff(1000, 2.0))`에 최대 재시도 3번(`backOff.setMaxAttempts(3)`)
  - `errorHandler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class, BusinessException.class)`
- Boot는 `CommonErrorHandler` 빈을 기본 리스너 컨테이너에 자동으로 연결한다(확인 필요: Boot 4에서도 같은지. 안 되면 `ConcurrentKafkaListenerContainerFactory`에 직접 설정)
- DLT 토픽도 `NewTopic`으로 만든다: 각 모듈의 `XxxTopicConfig`에서 원본마다 `{topic}.dlt` (파티션 3)

**3) 기존 consumer에 적용**: `ShopEventConsumer.onShopOpened` → `inboxGuard.runOnce("member.shop-opened", envelope, () -> roleService.grantSeller(...))`

**4) 가게 폐업**

| 대상 | 규격 |
|---|---|
| `shop.api.ShopClosePrecondition` | `public interface ShopClosePrecondition { void check(UUID shopId); }` — 위반 시 `BusinessException` |
| `shop.api.ShopApi` 추가 | `int countActiveShops(UUID memberId)`, `UUID getOwnerId(UUID shopId)`(없으면 `SHOP_NOT_FOUND`, 3-5 알림에서 사용), `List<UUID> getShopIdsByOwner(UUID memberId)`(CLOSED 포함, 3-6에서 사용) |
| `shop.api.event` | `ShopTopics.SHOP_CLOSED = "shop.shop-closed.v1"`, `record ShopClosedEvent(UUID shopId, UUID memberId)` |
| `Shop.close(Instant now)` | `status.transitTo(CLOSED)`, `closedAt = now` |
| `CloseShopService` (shop.application) | 의존성에 `List<ShopClosePrecondition> preconditions`. `@Transactional void close(UUID memberId, UUID shopId)` |
| `OrderShopClosePrecondition` (order.application) | `@Component`, `implements ShopClosePrecondition`. `shopOrderRepository.existsByShopIdAndStatusIn(shopId, {PENDING, PAID, SHIPPED, DELIVERED})`면 `BusinessException(SHOP_HAS_ACTIVE_ORDERS)` |
| `OrderErrorCode` | `SHOP_HAS_ACTIVE_ORDERS` 추가 |

`CloseShopService.close` 순서
1. 가게 조회(없으면 404) → `verifyOwner` → ACTIVE가 아니면 `SHOP_NOT_ACTIVE`
2. `preconditions.forEach(p -> p.check(shopId))`
3. `shop.close(now)`
4. `publish(SHOP_CLOSED, "SHOP", shopId, new ShopClosedEvent(shopId, memberId))`

consumer

| 모듈 | 클래스·메서드 | 처리 |
|---|---|---|
| member | `ShopEventConsumer.onShopClosed` (consumer `member.shop-closed`) | `roleService.revokeSellerIfNoActiveShop(memberId)`: 이벤트에 "남은 가게 수"를 담지 않고 **처리하는 시점에** `shopApi.countActiveShops(memberId)`를 조회 → 0이면 `demoteToUser()` |
| product | `ShopEventConsumer.onShopClosed` (consumer `product.shop-closed`, product.infrastructure) | `ProductDiscontinueService.discontinueAllByShop(shopId)`: `findAllByShopIdAndStatusIn(shopId, {ON_SALE, HIDDEN})` → 각각 `changeStatus(DISCONTINUED)` (한 트랜잭션) |

**5) shop.web**: `DELETE /api/shops/{id}` → 204

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `common/inbox/InboxGuardTest` | [ ] 같은 eventId로 `runOnce` 두 번 → action 1번 / [ ] action이 예외 → `processed_message`에 행 없음 → 다시 `runOnce` → action 실행됨 |
| `common/config/KafkaDltTest` | [ ] 항상 예외를 던지는 테스트용 리스너(`src/test`의 `@Component`, 테스트 전용 토픽 `test.always-fail.v1`) → 4번 시도 후 `test.always-fail.v1.dlt`에 메시지 / [ ] 깨진 JSON → 재시도 없이 바로 DLT(시도 1번) |
| `shop/web/CloseShopTest` | [ ] 진행 중(PAID) 가게주문이 있는 가게 폐업 → 422 `SHOP_HAS_ACTIVE_ORDERS` / [ ] 폐업 → 204, outbox `shop.shop-closed.v1` 1건 → 릴레이 → 상품 전부 DISCONTINUED / [ ] 가게 2개 중 1개 폐업 → SELLER 유지, 나머지 폐업 → 회수 / [ ] **같은 회원의 가게 2개를 동시에 폐업 → 둘 다 성공하고 최종적으로 SELLER 회수** / [ ] 같은 shop-closed 이벤트를 두 번 소비(테스트에서 같은 메시지를 직접 두 번 send) → 처리 1번 |

**제안 (선택)**
- 같은 회원의 가게 개설·폐업을 `pg_advisory_xact_lock(hashtext('shop-member:' + memberId))`로 줄 세우기 — SELLER 판단은 consumer 재조회로 이미 안전하므로, 줄 세우기는 "동시에 열고 닫는" 이상한 요청을 막는 용도
- 재시도 간격을 실패 횟수에 따라 늘리기(지수 백오프) — 릴레이·알림 발송 공통

**리뷰 때 물어볼 것**
- 이벤트에 "남은 가게 수"를 담지 않고 consumer가 다시 조회하는 이유는? (같은 회원의 가게 2개를 동시에 폐업하는 경우를 생각해 보라)
- 의존 역전을 쓰지 않으면 어떤 구조가 되나?
- `processed_message` INSERT와 비즈니스 처리를 다른 트랜잭션에 두면 어떤 문제가 생기나?
- `BusinessException`을 재시도하지 않는 이유는? 그럼 그 메시지는 어디로 가나?

---

## 3-3. 기존 흐름에 이벤트 발행 붙이기 (M)

**목표**: Part 1~2에서 만든 흐름에 이벤트를 발행한다. 구독자는 이후 단계(정산, 알림, 검색, 추천)에서 붙는다.

**정책 (이 단계에서 정함)**
- 결제 실패와 만료는 같은 이벤트(`order-expired`)로 내고 `reason`으로 구분한다(`EXPIRED`, `PAYMENT_FAILED`).
- 구매확정은 **품목마다** 이벤트 1건(자동 확정으로 한 번에 여러 품목이 확정돼도).
- 상품 이벤트의 `version`은 발행 시점 상품 버전이다. 엔티티 수정 직후에는 버전이 아직 오르지 않았으므로 **`productRepository.flush()` 후** `getVersion()`을 읽는다.

### 할 일

**1) 이벤트 정의** — 각 모듈 `api.event` 패키지 (토픽 상수 클래스 + record)

| 모듈 | 토픽 상수 | record (필드) |
|---|---|---|
| member | `MemberTopics.MEMBER_REGISTERED = "member.member-registered.v1"` | `MemberRegisteredEvent(UUID memberId, String email, String nickname)` |
| product | `ProductTopics.PRODUCT_UPSERTED = "product.product-upserted.v1"` | `ProductUpsertedEvent(UUID productId, UUID shopId, String name, String description, String category, long price, String status, boolean subscribable, long version)` |
| product | `PRODUCT_PRICE_CHANGED = "product.product-price-changed.v1"` | `ProductPriceChangedEvent(UUID productId, long oldPrice, long newPrice, Instant changedAt)` |
| product | `PRODUCT_DISCONTINUED = "product.product-discontinued.v1"` | `ProductDiscontinuedEvent(UUID productId, UUID shopId, String reason)` — reason `SELLER`, `SHOP_CLOSED` |
| order | `OrderTopics.ORDER_PAID = "order.order-paid.v1"` | `OrderPaidEvent(UUID orderId, UUID memberId, long totalAmount, List<PaidShopOrder> shopOrders)`, `PaidShopOrder(UUID shopOrderId, UUID shopId, List<PaidLine> lines)`, `PaidLine(UUID orderLineId, UUID productId, String productName, int quantity)` |
| order | `ORDER_EXPIRED = "order.order-expired.v1"` | `OrderExpiredEvent(UUID orderId, UUID memberId, String reason)` |
| order | `SHOP_ORDER_SHIPPED = "order.shop-order-shipped.v1"` | `ShopOrderShippedEvent(UUID shopOrderId, UUID orderId, UUID memberId, String carrier, String trackingNumber)` |
| order | `SHOP_ORDER_DELIVERED = "order.shop-order-delivered.v1"` | `ShopOrderDeliveredEvent(UUID shopOrderId, UUID orderId, UUID memberId)` |
| order | `ORDER_LINE_REFUNDED = "order.order-line-refunded.v1"` | `OrderLineRefundedEvent(UUID refundId, UUID orderLineId, UUID orderId, UUID memberId, long amount)` |
| order | `ORDER_LINE_CONFIRMED = "order.order-line-confirmed.v1"` | `OrderLineConfirmedEvent(UUID orderLineId, UUID orderId, UUID shopId, UUID productId, UUID memberId, long amount, Instant confirmedAt)` — amount = `confirmedAmount()` |

- `order/api/package-info.java`, `payment/api` 이미 있음 — `order::api`가 처음 공개된다. 토픽마다 `NewTopic` + `.dlt` (각 모듈 infrastructure의 `XxxTopicConfig`)

**2) 발행 위치** — 모두 **해당 상태 변경과 같은 트랜잭션** 안, 도메인 메서드 호출 직후

| 흐름 | 클래스·메서드 | 발행 | aggregateType / aggregateId |
|---|---|---|---|
| 가입 (1-3) | `SignupService.signUp` | `MEMBER_REGISTERED` | `MEMBER` / memberId |
| 상품 등록 (1-7) | `RegisterProductService.register` | `PRODUCT_UPSERTED` | `PRODUCT` / productId |
| 상품 수정 (1-8) | `UpdateProductService.update` | `PRODUCT_UPSERTED`, 가격이 바뀌었으면 `PRODUCT_PRICE_CHANGED` | `PRODUCT` / productId |
| 상품 상태 (1-8) | `UpdateProductService.changeStatus` | `PRODUCT_UPSERTED`, DISCONTINUED면 `PRODUCT_DISCONTINUED`(reason `SELLER`) | `PRODUCT` / productId |
| 가게 폐업 단종 (3-2) | `ProductDiscontinueService.discontinueAllByShop` | 상품마다 `PRODUCT_UPSERTED` + `PRODUCT_DISCONTINUED`(reason `SHOP_CLOSED`) | `PRODUCT` / productId |
| 결제 완료 (2-4 승인, 2-6 대사) | `OrderEventPublisher.paid(order)` — `OrderPaymentApplier.complete`에서 호출 | `ORDER_PAID` | `ORDER` / orderId |
| 만료·결제 실패 (2-4, 2-5, 2-6) | `OrderEventPublisher.closed(order, reason)` — `OrderPaymentApplier.fail`, `FailPaymentService`, `OrderExpirationService.expireOne` | `ORDER_EXPIRED` | `ORDER` / orderId |
| 발송·배송완료 (2-7) | `ShipmentService.ship` / `deliver` | `SHOP_ORDER_SHIPPED` / `SHOP_ORDER_DELIVERED` | `ORDER` / orderId (같은 주문 이벤트 순서 유지) |
| 환불 완료 (2-8, 2-9) | `RefundCompleter.complete` | `ORDER_LINE_REFUNDED` | `ORDER` / orderId |
| 구매확정 (2-10) | `ConfirmPurchaseService.confirm`, `AutoConfirmService`(품목마다) | `ORDER_LINE_CONFIRMED` | `ORDER` / orderId |

- `OrderEventPublisher` (order.application, `@Component`): Order 애그리거트를 받아 이벤트 record를 만들어 `OutboxPublisher`로 보내는 메서드 모음(`paid`, `closed`, `shipped`, `delivered`, `lineRefunded`, `lineConfirmed`). 이벤트 조립 코드가 서비스마다 흩어지지 않게 한다.
- `Order.autoConfirm`은 확정한 품목 목록을 반환하도록 바꾼다(이벤트를 품목마다 내기 위해).

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `{module}/.../EventPublishingTest` (모듈별 1개: `member/application`, `product/application`, `order/application`, `payment/application`) | [ ] 위 표의 흐름마다 outbox에 해당 토픽 **정확히 1건**, payload 필드 값 확인(`OutboxAssert`) / [ ] 실패해서 롤백되는 경우(예: 재고 부족 체크아웃, 단종 상품 수정) → outbox 0건 / [ ] 자동 구매확정으로 품목 3개 확정 → `ORDER_LINE_CONFIRMED` 3건 / [ ] 가격 변경 없는 상품 수정 → `PRODUCT_PRICE_CHANGED` 없음 / [ ] `ProductUpsertedEvent.version`이 DB의 `product.version`과 같다 |
| `ModularityTest` | [ ] 통과 |

**리뷰 때 물어볼 것**
- 이벤트에 어떤 필드를 넣을지 어떻게 정했나? (너무 많으면? 너무 적으면?)
- 발송 이벤트의 키를 shopOrderId가 아니라 orderId로 한 이유는?

---

## 3-4. 정산: 적재 · 정산 잡 · 지급 (L)
> PR 둘: **3-4a** 적재 + 정산 잡, **3-4b** 지급 + 조회

**목표**: 구매확정 품목을 가게별로 월 1회 정산하고, 수수료를 뺀 금액을 판매자에게 지급한다(은행 송금은 Mock). 정확히 한 번.

**왜 지금**: 3-3의 `order-line-confirmed` 이벤트가 생겼다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| keyset 배치 | 가게 ID 순서로 500개씩. offset 페이징으로 상태를 바꾸며 읽으면 누락된다(As-Is BAT-02, ADR-009) |
| 정산 1회 보장 | `settlement(shop_id, period_start)` unique + 품목에 정산 ID 할당 |
| 할당 후 합계 | 합계를 "조건에 맞는 품목"이 아니라 **"이번 정산 ID가 붙은 품목"**으로 계산한다. 계산 도중 새 품목이 들어와도 숫자가 어긋나지 않는다 |
| 외부 송금 원칙 | 지급(송금) 호출은 트랜잭션 밖에서, 멱등 키(settlementId)로. 결과는 호출 뒤 짧은 트랜잭션으로 기록한다 (As-Is WAL-02의 교훈) |

**정책 (이 단계에서 정함)**
- 정산 주기: 매월 5일 03:00(Asia/Seoul)에 **전월** 구매확정분(POL-12). 기간은 업무 날짜 기준 전월 1일 00:00 ~ 당월 1일 00:00(미포함).
- 수수료: 500bp(5%), 설정 `myroutine.settlement.fee-rate-bp: 500`. 원 미만 내림(OPEN-06): `fee = gross * bp / 10000` (정수 나눗셈)
- 정산액 0원인 정산은 입금 없이 PAID.
- 정산 실행 뒤에 늦게 도착한 전월 품목은 그 정산에 들어가지 않는다 → 다음 달 정산 기간에 맞지 않으므로 남는다. 정산 전에 consumer 지연이 없는지 확인하는 것을 운영 절차로 둔다(ADR-009). 남은 품목은 Part 7 점검 대상.
- 관리자 수동 실행: `POST /admin/api/settlements/run?period=2026-09` (ADMIN 역할). 관리자 계정은 DB에서 `role`을 `ADMIN`으로 직접 바꿔 만든다.

### 할 일 (3-4a)

**1) 마이그레이션** — `db/migration/settlement/V{...}__settlement_create_settlement.sql` (`CREATE SCHEMA IF NOT EXISTS settlement;`)

`settlement.settlement`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_settlement` |
| shop_id | uuid | X | `uk_settlement_shop_period (shop_id, period_start)` |
| period_start, period_end | date | X | period_end는 포함(말일) |
| gross_amount, fee_amount, net_amount | bigint | X | DEFAULT 0, `ck_settlement_net`: `net_amount = gross_amount - fee_amount` |
| fee_rate_bp | int | X | |
| item_count | int | X | DEFAULT 0 |
| status | varchar(20) | X | `ck_settlement_status`: `IN ('CALCULATED','PAID','PAYOUT_FAILED')` |
| payout_attempts | int | X | DEFAULT 0 |
| last_error | varchar(500) | O | |
| paid_at | timestamptz | O | |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- 인덱스 `idx_settlement_status (status)`, `idx_settlement_shop_id_period_start (shop_id, period_start DESC)`

`settlement.settlement_item`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_settlement_item` |
| order_line_id | uuid | X | `uk_settlement_item_order_line` (INV-07) |
| shop_id, order_id, product_id | uuid | X | |
| amount | bigint | X | `>= 0` |
| confirmed_at | timestamptz | X | |
| settlement_id | uuid | O | `fk_settlement_item_settlement`, null = 미정산 |
| created_at, updated_at | timestamptz | X | |

- 부분 인덱스 `idx_settlement_item_unsettled ON settlement.settlement_item (shop_id, confirmed_at) WHERE settlement_id IS NULL`, `idx_settlement_item_settlement_id (settlement_id)`

**2) settlement 모듈**

| 클래스 | 규격 |
|---|---|
| `SettlementStatus` | `CALCULATED → {PAID, PAYOUT_FAILED}`, `PAYOUT_FAILED → {PAID, PAYOUT_FAILED}` |
| `Settlement` (`@Entity`) | 필드는 컬럼과 1:1. 생성은 native INSERT, 이후 `calculate(Money gross, int bp, int itemCount)`, `markPaid(Instant now)`, `markPayoutFailed(String error)` |
| `SettlementProperties` | `@ConfigurationProperties("myroutine.settlement") record (@Min(0) @Max(10000) int feeRateBp)` |
| `common.config.BusinessProperties` | 업무 시간대 설정을 처음 쓰는 곳이라 여기서 만든다: `@ConfigurationProperties("myroutine") record (@NotNull ZoneId businessZone)` — `application.yaml`의 `myroutine.business-zone: Asia/Seoul`, `ClockConfig`에 `@EnableConfigurationProperties` |
| `SettlementPeriod` | `record (LocalDate start, LocalDate end)`. `static SettlementPeriod of(YearMonth month)`, `Instant fromInclusive(ZoneId zone)`, `Instant toExclusive(ZoneId zone)` — 단위 테스트 대상 |
| `SettlementItemRepository` | native `int insertIgnoringConflict(...)` (`ON CONFLICT ON CONSTRAINT uk_settlement_item_order_line DO NOTHING`), `List<UUID> findShopIdsToSettle(Instant from, Instant to, UUID afterShopId, int limit)`, `int assign(UUID settlementId, UUID shopId, Instant from, Instant to, Instant now)`, `SettlementSum sumBySettlementId(UUID settlementId)` |
| `SettlementRepository` | native `int insertIfAbsent(UUID id, UUID shopId, LocalDate start, LocalDate end, int bp, Instant now)` (`ON CONFLICT ON CONSTRAINT uk_settlement_shop_period DO NOTHING`), `findById`, `List<UUID> findIdsToPay(Collection<SettlementStatus> statuses, UUID afterId, Limit limit)` |

```sql
-- findShopIdsToSettle: 미정산 품목이 있는 가게 ID를 keyset으로
SELECT DISTINCT shop_id FROM settlement.settlement_item
 WHERE settlement_id IS NULL AND confirmed_at >= :from AND confirmed_at < :to AND shop_id > :afterShopId
 ORDER BY shop_id LIMIT :limit
-- assign: 이번 정산 ID를 붙인다
UPDATE settlement.settlement_item SET settlement_id = :settlementId, updated_at = :now
 WHERE shop_id = :shopId AND settlement_id IS NULL AND confirmed_at >= :from AND confirmed_at < :to
```
- 첫 페이지의 `afterShopId`는 `'00000000-0000-0000-0000-000000000000'`

**3) 적재 consumer** — `OrderEventConsumer` (settlement.infrastructure): `@KafkaListener(topics = OrderTopics.ORDER_LINE_CONFIRMED, groupId = "settlement")` → `inboxGuard.runOnce("settlement.order-line-confirmed", ...)` → `SettlementItemService.register(event)` → `insertIgnoringConflict`

**4) 정산 실행** — `SettlementService` (`@Transactional` 없음)
- `public SettlementRunResult run(YearMonth month)` → `SettlementRunResult(int settledShops, int paid, int payoutFailed)`
1. `period = SettlementPeriod.of(month)`, `from`/`to` 계산
2. keyset 루프: `shopIds = findShopIdsToSettle(from, to, after, 500)` → 비면 종료 → 각 가게 `try { tx.execute(s -> settleShop(shopId, period)) } catch (Exception e) { log }` → `after = 마지막 shopId`
3. `payoutAll()` (3-4b)

`settleShop(shopId, period)` (한 트랜잭션)
1. `id = Ids.newId()`, `insertIfAbsent(id, shopId, ...)` → 0이면 return(이미 이 기간 정산이 있음)
2. `assign(id, shopId, from, to, now)`
3. `sum = sumBySettlementId(id)` (`SELECT COALESCE(SUM(amount), 0), COUNT(*)`)
4. `settlement.calculate(gross, bp, count)` → fee·net 계산, CALCULATED

- `SettlementJob` (settlement.infrastructure): `@Scheduled(cron = "0 0 3 5 * *", zone = "Asia/Seoul")` → `jobRunner.run("settlement", () -> service.run(전월).settledShops())`
- `settlement/package-info.java`: 공통 규칙 S의 의존

### 할 일 (3-4b)

**5) 지급**

| 클래스 | 규격 |
|---|---|
| `PayoutGateway` (settlement.application 인터페이스) | `String payout(UUID settlementId, UUID shopId, long amount)` → 이체 참조 ID. 실패 시 예외. **같은 settlementId로 다시 부르면 같은 참조 ID를 돌려준다**(멱등) |
| `MockPayoutGateway` (settlement.infrastructure) | `@Component @Profile("!prod")`. `ConcurrentHashMap<UUID, String>`에 settlementId → `"PAYOUT-" + Ids.newId()`를 기록하고 로그를 남긴다. 실제 은행 연동은 범위 밖(POL-12) |
| `support/FakePayoutGateway` (테스트) | `@Component @Primary`. 호출 기록, `failNext(int)`, 멱등 동작은 Mock과 같게 |

`SettlementService.payoutAll()` — `findIdsToPay({CALCULATED, PAYOUT_FAILED}, after, Limit.of(500))` keyset 루프, 정산마다 try/catch:
1. 정산 조회(짧은 읽기). `net == 0`이면 트랜잭션에서 `markPaid(null, now)` + `publish(SETTLEMENT_PAID)` 후 다음으로
2. **트랜잭션 밖**: `reference = payoutGateway.payout(settlementId, shopId, net)`
3. 성공 → 트랜잭션: `settlement.markPaid(reference, now)` + `publish(SETTLEMENT_PAID)`
4. 실패 → 트랜잭션: `markPayoutFailed(e.getMessage())`, `payout_attempts + 1`
- 2와 3 사이에 죽으면 정산은 CALCULATED로 남는다 → 다음 실행에서 같은 settlementId로 다시 부르고, 게이트웨이가 같은 참조 ID를 돌려주므로 두 번 지급되지 않는다.
- 이벤트: `settlement.api.event.SettlementTopics.SETTLEMENT_PAID = "settlement.settlement-paid.v1"`, `SettlementPaidEvent(UUID settlementId, UUID shopId, long netAmount, LocalDate periodStart, LocalDate periodEnd)` (3-5 알림용)

**6) 조회 API** — `SettlementController`

| API | 권한 | 응답 |
|---|---|---|
| `GET /api/shops/{shopId}/settlements?cursor=&size=` | 가게 주인(`shopApi.verifyOwner`) | 200 `CursorPage<SettlementResponse(id, periodStart, periodEnd, grossAmount, feeAmount, netAmount, itemCount, status, paidAt)>` (최신 기간 먼저) |
| `GET /api/shops/{shopId}/settlements/{id}?cursor=&size=` | 가게 주인 | 200 `SettlementDetailResponse(settlement, items: CursorPage<SettlementItemResponse(orderLineId, orderId, productId, amount, confirmedAt)>)` |
| `POST /admin/api/settlements/run?period=yyyy-MM` | ADMIN | 200 `SettlementRunResult` |

- `SecurityConfig`: `.requestMatchers("/admin/**").hasRole("ADMIN")`
- `SettlementErrorCode`: `SETTLEMENT_NOT_FOUND`

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `settlement/domain/SettlementPeriodTest` | [ ] 2026-09 → 시작 `2026-08-31T15:00:00Z`, 끝 `2026-09-30T15:00:00Z` / [ ] 수수료 내림: gross 12,345, 500bp → fee 617 |
| `settlement/application/SettlementItemConsumerTest` | [ ] 같은 확정 이벤트 두 번 → `settlement_item` 1행 |
| `settlement/application/SettlementServiceTest` (정산 대상은 JDBC로 직접 적재해 빠르게) | [ ] **가게 1,200개(가게당 품목 2개) → 정산 1,200건, 미정산 품목 0** / [ ] `run`을 두 번 → 정산 건수 동일, 금액 동일 / [ ] **기간 경계**: KST 8월 31일 23:59:59 확정 → 8월 정산, 9월 1일 00:00:00 → 9월 정산 / [ ] 지급 → PAID, `payout_reference` 있음, 가짜 게이트웨이 호출 1건 / [ ] 지급 실패 주입(`fakePayoutGateway.failNext(1)`) → PAYOUT_FAILED, `payout_attempts` 1 → 다시 `run` → PAID / [ ] 같은 정산 지급 두 번 시도(JDBC로 PAID를 CALCULATED로 되돌려 재실행) → 게이트웨이가 같은 참조 ID 반환 |
| `settlement/web/SettlementControllerTest` | [ ] 남의 가게 정산 조회 → 403 / [ ] 일반 회원이 `/admin/api/settlements/run` → 403 |

**리뷰 때 물어볼 것**
- offset 페이징으로 상태를 바꾸며 읽으면 왜 누락되나? (원 프로젝트에서 실제로 있던 결함)
- 정산과 지급을 왜 분리했나?
- 합계를 "정산 ID가 붙은 품목"으로 계산하는 이유는?

---

## 3-5. 알림 (M)

**목표**: 결제 완료·발송·환불·정산 지급 등에서 이메일 알림을 보낸다. 실패하면 재시도하고, 끝내 실패한 건은 보관해 나중에 다시 보낼 수 있다. 알림이 실패해도 주문·결제는 영향받지 않는다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 저장과 발송 분리 | consumer는 "보낼 알림"을 DB에 저장만 하고, 발송 잡이 따로 보낸다. 메일 서버 장애가 consumer 재시도·DLT로 번지지 않는다 |
| 포트 | `common.mail.MailSender` 인터페이스. local은 로그로 출력, 테스트는 가짜 구현, SMTP는 선택. 4-2의 인증 메일도 같은 포트를 쓴다(그래서 notification이 아니라 common에 둔다) |

**정책 (이 단계에서 정함)**
- 채널은 이메일만. 수신 주소는 알림 생성 시점의 회원 이메일을 저장한다.
- 발송 재시도: 실패하면 **5분 뒤** 다시, **5번 실패하면 FAILED**. FAILED 재처리 API는 Part 7.
- 같은 이벤트·같은 수신자·같은 종류의 알림은 1건(`dedup_key`).

### 할 일

**1) 마이그레이션** — `db/migration/notification/V{...}__notification_create_notification.sql` (`CREATE SCHEMA IF NOT EXISTS notification;`)

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_notification` |
| member_id | uuid | X | |
| type | varchar(40) | X | 아래 표의 종류 |
| channel | varchar(20) | X | DEFAULT 'EMAIL' |
| recipient | varchar(255) | X | 이메일 |
| title | varchar(200) | X | |
| body | text | X | |
| dedup_key | varchar(200) | X | `uk_notification_dedup_key` — `eventId:memberId:type` |
| status | varchar(20) | X | `ck_notification_status`: `IN ('PENDING','SENT','FAILED')` |
| attempts | int | X | DEFAULT 0 |
| last_error | varchar(500) | O | |
| next_attempt_at | timestamptz | X | |
| sent_at | timestamptz | O | |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- 인덱스 `idx_notification_pending ON notification.notification (next_attempt_at) WHERE status = 'PENDING'`

**2) 수신자 조회용 API 추가**
- `MemberApi.MemberContact getContact(UUID memberId)` → `record MemberContact(UUID memberId, String email, String nickname, boolean active)` (탈퇴 회원은 `active = false`)
- `ShopApi.getOwnerId(shopId)` (3-2에서 추가됨)

**3) notification 모듈**

| 클래스 | 규격 |
|---|---|
| `NotificationType` | `WELCOME, ORDER_PAID, SHOP_ORDER_RECEIVED, ORDER_CLOSED, SHIPPED, DELIVERED, REFUNDED, SETTLEMENT_PAID` (Part 5에서 구독 관련 추가) |
| `NotificationDraft` | `record (UUID memberId, NotificationType type, String title, String body)` |
| `NotificationComposer` (`@Component`) | 이벤트 → `List<NotificationDraft>`. **순수 변환**(수신자 연락처 조회는 하지 않음) — 단위 테스트 대상 |
| `NotificationService` | `@Transactional void enqueue(UUID eventId, List<NotificationDraft> drafts)`: 각 draft의 회원 연락처 조회(`active == false`면 건너뜀) → `INSERT ... ON CONFLICT ON CONSTRAINT uk_notification_dedup_key DO NOTHING` (PENDING, `next_attempt_at = now`) |
| `NotificationEventConsumer` (infrastructure) | 토픽마다 `@KafkaListener(groupId = "notification")` 메서드 → `inboxGuard.runOnce("notification.{event}", ...)` → composer → service |
| `common.mail.MailSender` (인터페이스) | `void send(String to, String title, String body)` — 실패 시 예외 |
| `common.mail.LogMailSender` | `@Component @Profile("!prod")`, `log.info`로 출력(본문은 앞 100자만) |
| `NotificationDispatcher` (application, `@Transactional` 없음) | `public int dispatchBatch()` |
| `NotificationDispatchJob` | `@Scheduled(fixedDelay = 10_000)` → `jobRunner.run("dispatch-notifications", dispatcher::dispatchBatch)` |

이벤트 → 알림 매핑 (`NotificationComposer`)

| 이벤트 | 수신자 | type | 제목 예 |
|---|---|---|---|
| `member-registered` | 회원 | WELCOME | `[MyRoutin] 가입을 환영합니다` |
| `order-paid` | 구매자 | ORDER_PAID | `주문 결제가 완료되었습니다 ({totalAmount}원)` |
| `order-paid` | 가게주문마다 **가게 주인** (shopId → `getOwnerId`) | SHOP_ORDER_RECEIVED | `새 주문이 들어왔습니다 (상품 {품목 수}개)` |
| `order-expired` | 구매자 | ORDER_CLOSED | reason에 따라 `주문이 만료되었습니다` / `결제에 실패했습니다` |
| `shop-order-shipped` | 구매자 | SHIPPED | `상품이 발송되었습니다 ({carrier} {trackingNumber})` |
| `shop-order-delivered` | 구매자 | DELIVERED | `배송이 완료되었습니다` |
| `order-line-refunded` | 구매자 | REFUNDED | `{amount}원이 환불되었습니다` |
| `settlement-paid` | 가게 주인 | SETTLEMENT_PAID | `{periodStart}~{periodEnd} 정산금 {netAmount}원이 지급되었습니다` |

- 가게 주인 조회가 필요한 매핑(SHOP_ORDER_RECEIVED, SETTLEMENT_PAID)은 composer가 아니라 consumer에서 `shopApi.getOwnerId`로 memberId를 구해 composer에 넘긴다(composer는 순수 함수로 유지).

`dispatchBatch` 순서
1. `SELECT ... WHERE status = 'PENDING' AND next_attempt_at <= now ORDER BY next_attempt_at LIMIT 100` (잡 락으로 한 인스턴스만 실행)
2. 각 알림: **트랜잭션 밖에서** `mailSender.send(...)` → 성공: 짧은 트랜잭션으로 SENT, `sent_at` / 실패: `attempts + 1`, 5 이상이면 FAILED, 아니면 `next_attempt_at = now + 5분`, `last_error`
3. 발송 성공 건수 반환

**4) 테스트용 발송기** — `src/test/java/com/myroutine/support/FakeMailSender`: `@Component @Primary`, `implements MailSender`, 보낸 목록 기록(`List<SentMail> sent()`), `failNext(int times)`로 실패 흉내, `reset()`(`IntegrationTestSupport`의 `@AfterEach`에서 호출)

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `notification/application/NotificationComposerTest` | [ ] 표의 이벤트마다 수신자·type·제목 / [ ] 가게 2곳 주문의 `order-paid` → 구매자 1 + 가게 주인 2 |
| `notification/application/NotificationFlowTest` | [ ] 체크아웃 → 결제 승인(가짜 PG) → 릴레이 → `Eventually`로 notification 3행(구매자 + 가게 주인) → `dispatchBatch()` → SENT, 가짜 발송기에 3건 / [ ] 같은 이벤트 두 번 → 알림 그대로 / [ ] `failNext(5)` → 다섯 번 실행(JDBC로 `next_attempt_at`을 당기며) → FAILED, `attempts` 5, `last_error` 있음 / [ ] 발송 실패가 주문 상태에 영향 없음 |

**리뷰 때 물어볼 것**
- 원 프로젝트에서 RabbitMQ를 따로 쓴 이유와, v2에서 Kafka만으로 충분한 이유는? (ADR-003)
- consumer에서 메일을 바로 보내지 않는 이유는?

---

## 3-6. 회원 탈퇴 (M)

**목표**: 진행 중인 주문·운영 중인 가게·정산 대기 금액이 없으면 탈퇴할 수 있다(구독은 Part 5에서 추가). 개인정보는 마스킹된다.

**정책 (이 단계에서 정함)**
- 막는 조건(`details.reasons`에 모두 담는다)

| reason | 조건 | 확인하는 곳 |
|---|---|---|
| `ACTIVE_SHOP` | ACTIVE 가게가 있다 | member가 `ShopApi.countActiveShops` 직접 호출 |
| `ACTIVE_ORDER` | 결제 대기·진행 중 주문, 또는 구매자로서 종결되지 않은 가게주문(PAID·SHIPPED·DELIVERED), 진행 중 환불 | order 구현 |
| `PENDING_SETTLEMENT` | 내 가게(CLOSED 포함)에 미정산 품목 또는 PAID가 아닌 정산이 있다 (OPEN-07) | settlement 구현 |

- 마스킹(OPEN-01: 같은 이메일로 재가입 가능하게): email `withdrawn+{memberId}@deleted.invalid`, nickname `탈퇴-{memberId 앞 8자}`, name `탈퇴회원`, phone null, password_hash null, 배송지 전부 삭제.
- 탈퇴한 회원의 기존 토큰은 Part 4(4-4 tokenVersion, 그때 탈퇴에도 버전 올리기를 붙인다)부터 즉시 막힌다. 그 전까지는 토큰 만료(1시간)까지 유효하다는 한계를 PR에 적는다.

### 할 일

**1) member.api**
```java
public interface WithdrawalPrecondition {
    List<String> violations(UUID memberId);   // 위반이 없으면 빈 목록
}
```
- 이벤트: `MemberTopics.MEMBER_WITHDRAWN = "member.member-withdrawn.v1"`, `MemberWithdrawnEvent(UUID memberId)`

**2) 구현체**

| 모듈 | 클래스 | 규격 |
|---|---|---|
| order | `OrderWithdrawalPrecondition` | 조건이 하나라도 있으면 `List.of("ACTIVE_ORDER")` |
| settlement | `SettlementWithdrawalPrecondition` | `shopApi.getShopIdsByOwner(memberId)`의 가게들에 `settlement_id IS NULL`인 품목이나 PAID가 아닌 정산이 있으면 `List.of("PENDING_SETTLEMENT")` |

- `settlement/package-info.java`에 `"member::api"` 추가 (공통 규칙 S)

**3) member**
- 마이그레이션 `V{...}__member_add_withdrawn_at.sql`: `member.member`에 `withdrawn_at timestamptz NULL`
- `Member.withdraw(Instant now)`: `status.transitTo(WITHDRAWN)`, 마스킹, `withdrawnAt = now`
- `MemberErrorCode`: `MEMBER_HAS_ACTIVE_RESOURCES`
- `WithdrawService` (`@Transactional void withdraw(UUID memberId)`)
  1. 회원을 `FOR UPDATE`로 조회(`@Lock(PESSIMISTIC_WRITE)` 메서드 추가)
  2. `reasons = new ArrayList<>()` → `shopApi.countActiveShops(memberId) > 0`이면 `ACTIVE_SHOP` → `preconditions.forEach(p -> reasons.addAll(p.violations(memberId)))`
  3. 비어 있지 않으면 `BusinessException(MEMBER_HAS_ACTIVE_RESOURCES, Map.of("reasons", reasons))`
  4. 배송지 전부 삭제 → `member.withdraw(now)` → `publish(MEMBER_WITHDRAWN, "MEMBER", memberId, ...)`
- `DELETE /api/members/me` → 204

**4) consumer**: order `MemberEventConsumer` (consumer `order.member-withdrawn`) → 장바구니와 항목 삭제

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `member/domain/MemberTest` | [ ] `withdraw` → 마스킹 값, WITHDRAWN, `withdrawnAt` 기록 / [ ] BANNED 회원 `withdraw` → `INVALID_STATE_TRANSITION` |
| `member/web/WithdrawTest` | [ ] 조건별 거부: 운영 중 가게 → 422 `details.reasons = ["ACTIVE_SHOP"]` / 결제 대기 주문 → `["ACTIVE_ORDER"]` / 미정산 품목 → `["PENDING_SETTLEMENT"]` / 둘 이상이면 모두 / [ ] 탈퇴 → 204, 같은 이메일로 로그인 → 401 `LOGIN_FAILED`, **같은 이메일로 재가입 → 201** / [ ] 탈퇴 → 릴레이 → `Eventually`로 장바구니 비워짐 |

**리뷰 때 물어볼 것**
- 탈퇴 조건 검사와 탈퇴 처리 사이에 새 주문이 들어오면?
- 가게 조건만 member가 직접 확인하는 이유는? (순환 의존)

---

## Part 3 완료
- [ ] `v0.3.0` 릴리스, Part 4 문서 다듬기
