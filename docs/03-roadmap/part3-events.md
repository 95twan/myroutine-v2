# Part 3. 이벤트

> 버전 0.13 · 2026-10-16 · **쓰지 않는 값과 불필요한 단계를 걷어냈다**(리뷰 지적): 3-1 `relayOnce`의 회수와 선점을 한 트랜잭션으로 묶었고, 3-3 구매확정 이벤트가 2-10 3번에서 이미 찾은 품목을 넘겨 쓰게 했으며, 3-4 `insertIgnoringConflict`·`assign`의 쓰지 않는 반환값과 3-5 `MemberContact`의 `memberId`·`nickname`을 지웠다. 그 밖의 규격·동작은 0.12와 같다
> 0.12 · 2026-10-16 · **"할 일"을 구현 순서대로 다시 놓고 설명 속에 숨어 있던 구현물을 표로 올렸다**(구현 중 지적): 3-1~3-6 "할 일" 맨 앞에 구현 순서 한 줄을 더했다. 3-1 `OutboxRelayJob`·`TypedEnvelope`, 3-2 폐업 구현물 14행(`ShopApi` 3개·저장소 조회·DLT 토픽·소비 클래스, 존재하지 않는 `shopOrderRepository`를 `OrderRepository`로), 3-3 구현물 표(`order/api` 패키지·토픽 설정 3개·`ProductRepository.flush`·`autoConfirm` 반환 변경), 3-4 `SettlementConfig`·`SettlementSum`·`SettlementItemService`·consumer·`SettlementService`·`SettlementJob`·package-info 행과 3-4b 이벤트·조회·오류 코드 표(`myroutine.business-zone`은 이미 `application.yaml`에 있음을 반영), 3-5 클래스 표 11행(저장소·package-info 포함)과 설명 순서, 3-6 구현물 15행(저장소 조회·장바구니 삭제·토픽 설정)을 적었다. 같은 설명이 반복된 곳(`EventContext`·`InboxGuard` MDC, `flush`·`version`, 가게 주인 조회, `MANDATORY`)은 한 곳만 남겼다. 규격·동작은 0.11과 같다
> 0.11 · 2026-10-15 · **`EventPublishingTest` 대상에서 payment를 뺐다**(리뷰 결정): 3-3 이벤트 표에 payment가 발행하는 이벤트가 없다. 그 밖의 규격은 0.10과 같다
> 0.10 · 2026-10-15 · **Part 3 클래스·메서드 설명을 풀어 썼다**(구현 중 "뭔지 모르겠다" 지적): 표의 한 줄 규격이던 클래스·메서드마다 역할·호출하는 곳·입력과 반환·순서·함정을 적었다(3-1 outbox·릴레이·consumer, 3-2 멱등 소비·에러 핸들러·폐업, 3-3 이벤트 발행 위치, 3-4 정산·지급, 3-5 알림, 3-6 탈퇴). 문서에 정해져 있지 않던 부분은 "확인 필요"로 표시했다. 규격·동작은 직전 버전과 같다
> 0.9 · 2026-10-14 · **표현·구조 정리**(설계·동작 변경 없음): 3-3 `OrderEventPublisher`와 3-4 `SettlementPaidEvent`를 쓰는 표·순서 앞에서 정의하고, 3-4 `SettlementSum`·`SettlementItemService`를 정의했으며, 한 셀에 몰려 있던 저장소 메서드를 목록으로 풀었다
> 0.8 · 2026-10-12 · **용어 정리**: 단계 끝 "제안 (선택)"을 "추가 항목"으로 바꿨다. "제안"은 리뷰어의 의견(근거 없는 지적)에만 쓴다. 내용은 그대로다
> 0.7 · 2026-10-11 · JSON 변환 타입을 `ObjectMapper`로 통일(Part 1 코드·Part 2 멱등 장치와 같은 타입)
> 0.6 · 2026-10-09 · **사실 확인·정합**: Kafka 이미지 태그(`apache/kafka:4.2.2`)·Boot 4 스타터 이름·`CommonErrorHandler` 자동 연결을 확인해 확정, 테스트 컨테이너 기동에 1-10의 MinIO 포함, 정산 테이블에 빠져 있던 `payout_reference` 컬럼과 `markPaid` 인자 통일, Part 2에서 제안으로 옮긴 `FailPaymentService` 잔재 제거
> 0.5 · 2026-10-02 · **덜어내기**: 가게 개설·폐업 advisory lock 제거(재조회로 충분), 폐업 이벤트의 남은 가게 수 필드 제거, 재시도 간격은 고정값으로(백오프는 제안), 회원 역할은 단일 `role`
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
| 직렬화 | Kafka 메시지 값은 **JSON 문자열**(`StringSerializer`). 봉투 → JSON 변환은 주입받은 `ObjectMapper`(`tools.jackson.databind.ObjectMapper`)로 직접 한다(Kafka용 Jackson 직렬화 설정을 따로 다루지 않기 위해) |
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

**흐름 한눈에** (가게 개설 기준. 아래 클래스가 어느 줄을 맡는지 보면서 읽는다)
1. `OpenShopService.open`이 한 트랜잭션에서 `shop` 행과 `common.outbox_event` 행(`READY`)을 함께 INSERT한다 — `OutboxPublisher.publish`가 outbox 행을 넣는다.
2. 1초마다 `OutboxRelayJob` → `OutboxRelay.relayOnce()`가 `READY` 행을 집어(선점) Kafka 토픽 `shop.shop-opened.v1`로 보내고, 성공하면 `SENT`로 바꾼다.
3. member 모듈의 `ShopEventConsumer`(`@KafkaListener`)가 같은 토픽에서 메시지를 받아 `EventEnvelopes.parse`로 풀고 `MemberRoleService.grantSeller`를 부른다.
- 1~2 사이, 2~3 사이 어디서 서버가 죽어도 outbox 행이나 Kafka 메시지가 남아 있어 이어서 처리된다.

### 할 일

**구현 순서**: ① 인프라(compose·의존성·`application.yaml`·`IntegrationTestSupport`) → ② outbox 마이그레이션 → ③ `EventEnvelope` → `OutboxPublisher` → `OutboxRelay` → `OutboxRelayJob` → `EventEnvelopes` → `EventContext` → ④ shop: `ShopTopics`·`ShopOpenedEvent` → `ShopTopicConfig` → `OpenShopService` 수정 → ⑤ member: `Member.promoteToSeller`·`demoteToUser` → `MemberRoleService` → `member/package-info.java` → `ShopEventConsumer` → ⑥ 테스트 헬퍼(`Eventually`, `OutboxAssert`) → 테스트.

**1) 인프라**
- `docker/docker-compose.yml`에 Kafka 추가: 이미지 `apache/kafka:4.2.2`(KRaft 단일 브로커. 2026-10-09 Docker Hub의 4.2 줄 최신 패치 — Boot 4.1.1이 관리하는 kafka-clients가 4.2.1이라 같은 줄로 맞췄다. 4.3.x도 있다), 포트 `127.0.0.1:9092:9092`. 1-11 운영 compose에도 같은 태그로 추가한다. 운영에서는 앱이 컨테이너라 브로커가 광고하는 주소(advertised listeners)가 `localhost`면 붙지 못한다 — 서비스 이름(`kafka:9092`)으로 광고하게 설정한다(환경변수 이름은 이미지 문서에서 확인 필요)
- 의존성: `implementation 'org.springframework.boot:spring-boot-starter-kafka'`(Boot 4.1.1에 있다, 2026-10-09 Maven Central 확인. 3.x처럼 `spring-kafka`를 직접 넣지 않는다), 테스트 `testImplementation 'org.testcontainers:testcontainers-kafka'`(버전은 Boot가 import하는 Testcontainers BOM 2.0.5가 관리)
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
- 설정 읽는 법
  - `acks: all` + `enable.idempotence: true`: 리더와 복제본에 기록된 뒤에야 성공 응답을 받고, 프로듀서가 내부에서 재전송해도 브로커가 중복을 걸러낸다. 릴레이가 같은 행을 **다시 발행**하는 중복(아래 `relayOnce` 3~4번 사이에서 죽은 경우)은 걸러 주지 않는다.
  - `delivery.timeout.ms`를 줄인 이유: 기본값(2분)이면 릴레이가 "실패"로 기록한 뒤에도 프로듀서가 뒤에서 계속 재전송해 늦게 도착할 수 있다. 줄여도 중복은 생길 수 있으므로 consumer 멱등이 전제다. `request.timeout.ms`·`max.block.ms`도 브로커가 멈췄을 때 `send`가 오래 붙잡히지 않게 하려는 값이다.
  - `auto-offset-reset: earliest`: 이 consumer 그룹이 읽은 위치(offset)를 아직 저장한 적이 없을 때(처음 뜰 때) 토픽의 맨 앞부터 읽는다.
  - `enable-auto-commit: false` + `listener.ack-mode: record`: offset을 주기적으로 자동 저장하지 않고, **리스너 메서드가 예외 없이 끝난 레코드마다** Spring이 저장한다. 예외로 끝난 레코드는 저장되지 않아 다시 전달된다(재시도·DLT는 3-2).
- `IntegrationTestSupport`: `@ServiceConnection static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.2")`(패키지 `org.testcontainers.kafka`. `@ServiceConnection`이 컨테이너 주소를 `spring.kafka.bootstrap-servers`에 자동으로 넣어 준다), static 블록의 `postgres.start()`(1-10 이후 `minio.start()`도)를 `Startables.deepStart(postgres, minio, kafka).join()`(`org.testcontainers.lifecycle.Startables`, 컨테이너를 병렬로 띄운다)으로 바꾼다

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

| 클래스 | 한 줄 설명 | 쓰는 곳 |
|---|---|---|
| `EventEnvelope` | Kafka 메시지 한 건의 겉봉투(record) | 릴레이가 만들고, consumer가 푼다 |
| `OutboxPublisher` | 이벤트를 "보낼 목록(outbox 행)"에 올리는 입구 | 상태를 바꾸는 서비스(`OpenShopService` 등) |
| `OutboxRelay` | outbox 행을 Kafka로 실제 발행 | `OutboxRelayJob`(1초마다), 테스트가 직접 호출 |
| `OutboxRelayJob` | `@Scheduled(fixedDelay = 1000)` → `jobRunner.run("outbox-relay", relay::relayOnce)`. `JobRunner`(2-5)는 같은 잡을 서버 한 대만 돌리고 예외를 로그로 남기고 삼킨다 | 스케줄러. 테스트에서는 스케줄이 꺼져 있어 `relayOnce()`를 직접 부른다 |
| `EventEnvelopes` + `TypedEnvelope<T>`(봉투와 이벤트 record를 묶는 record) | Kafka에서 받은 JSON 문자열을 봉투 + 이벤트 record로 풂 | 모든 consumer의 첫 줄 |
| `EventContext` | 처리하는 동안 MDC에 `traceId`·`eventId`를 넣고 뺌 | consumer(3-2부터는 `InboxGuard`가 같은 일을 한다) |

**`EventEnvelope`** — `public record EventEnvelope(UUID eventId, String eventType, Instant occurredAt, UUID aggregateId, String traceId, JsonNode payload)` (`tools.jackson.databind.JsonNode`)
- 어떤 이벤트든 같은 모양으로 감싸 보내서, 받는 쪽이 이벤트 종류를 모르고도 `eventId`(멱등 키)·`traceId`(로그 추적)를 먼저 꺼낼 수 있게 한다. 그래서 `payload`는 이벤트 record가 아니라 JSON 트리(`JsonNode`)이고, 이벤트 record로의 변환은 consumer가 `EventEnvelopes.parse`로 한다.
- outbox 행 → 봉투: `id` → `eventId`, `event_type` → `eventType`, `aggregate_id` → `aggregateId`, `trace_id` → `traceId`, `payload`(jsonb) → `payload`. `occurredAt`은 선점 쿼리가 돌려주는 `created_at`을 쓰는 것으로 보인다(확인 필요: 문서에 명시 없음).

**`OutboxPublisher`** (`@Component`, 의존성 `JdbcTemplate`, `ObjectMapper`, `Clock`) — 비즈니스 코드가 이벤트를 내보내는 **유일한 입구**다. Kafka로 직접 보내지 않고, 상태 변경과 **같은 DB 트랜잭션에서 `common.outbox_event` 행 하나를 INSERT**만 한다. 트랜잭션이 롤백되면 이 행도 같이 사라진다 — 이것이 Outbox의 핵심이다.

`@Transactional(propagation = Propagation.MANDATORY) public void publish(String topic, String aggregateType, UUID aggregateId, Object event)` — 반환 없음

| 인자 | 의미 | 들어가는 컬럼 |
|---|---|---|
| `topic` | 보낼 토픽 이름. 발행 모듈의 상수(`ShopTopics.SHOP_OPENED`) | `topic` |
| `aggregateType` | 변경된 대상의 종류를 나타내는 문자열(`"SHOP"`). 조회·로그용 | `aggregate_type` |
| `aggregateId` | 변경된 대상의 ID. Kafka 키가 되어 같은 대상의 이벤트 순서를 지킨다 | `aggregate_id` |
| `event` | 이벤트 record(`ShopOpenedEvent`). 필드만 JSON이 된다 | `payload`, 클래스 이름(`event.getClass().getSimpleName()`)이 `event_type` |

순서
1. `UUID id = Ids.newId()` — 이 값이 봉투의 `eventId`다(consumer 멱등 키가 된다).
2. `String payload = objectMapper.writeValueAsString(event)` — Jackson 3의 예외는 unchecked라 `throws`가 없다.
3. `jdbcTemplate.update("INSERT INTO common.outbox_event (...) VALUES (..., ?::jsonb, ...)", ...)` — `status = 'READY'`, `trace_id = MDC.get(TraceIds.MDC_KEY)`(HTTP 요청이면 `TraceIdFilter`가 넣어 둔 값. 없으면 `null`), `attempts`는 DEFAULT 0. NOT NULL인 `next_attempt_at`·`created_at`·`updated_at`은 INSERT에서 `now`로 직접 채운다. JDBC로 시각을 넘길 때는 `Instant`를 `OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)`로 바꿔 넘긴다(`IdempotencyRepository`와 같다).
- `MANDATORY`: 트랜잭션 밖에서 부르면 `IllegalTransactionStateException`("상태는 롤백됐는데 이벤트만 나가는" 사고를 코드로 막는다). 상태를 바꾸는 서비스의 `@Transactional` 안에서 부르면 JPA와 같은 트랜잭션에 참여한다.

**`OutboxRelay`** (`@Component`, 의존성 `JdbcTemplate`, `KafkaTemplate<String, String>`(Boot가 `spring.kafka.*`로 만든 빈), `ObjectMapper`, `TransactionTemplate`) — **outbox에 쌓인 READY 행을 Kafka로 발행하는 곳**이다. 클래스에 `@Transactional`을 붙이지 않는다: 선점·결과 기록은 짧은 트랜잭션이고 Kafka 호출은 그 밖에서 해야 해서 `TransactionTemplate`으로 구간을 직접 나눈다([Part 2 공통 규칙 K](part2-order-money.md#k-외부-호출과-트랜잭션)).
- `public int relayOnce()` → 이번 호출에서 SENT로 바꾼 건수

`relayOnce()` 순서
1. 회수(2와 같은 짧은 트랜잭션 안의 `jdbcTemplate.update`): 앞서 릴레이가 죽어 `PROCESSING`으로 남은 행을 다시 `READY`로 돌린다. 회수만 하려고 트랜잭션을 한 번 더 열지 않고, 같은 `tx.execute(s -> { 회수 update; return 선점 query; })` 안에서 바로 이어 2의 선점이 방금 돌린 행까지 집는다.
```sql
UPDATE common.outbox_event SET status = 'READY', locked_until = NULL, updated_at = now() WHERE status = 'PROCESSING' AND locked_until < now()
```
2. 선점(1과 같은 트랜잭션): "내가 이 행들을 맡았다"고 표시하고 그 행을 바로 읽는다. `jdbcTemplate.query(sql, rowMapper)`로 `RETURNING` 결과를 `List<OutboxRow>`로 받는다(`OutboxRow`는 릴레이 안에서만 쓰는 private record: `id, aggregateId, eventType, topic, payload(String), traceId, attempts, createdAt`). `FOR UPDATE SKIP LOCKED`는 다른 트랜잭션이 잠근 행을 기다리지 않고 건너뛰고, `PROCESSING + locked_until`은 커밋 뒤에도 다른 인스턴스가 못 가져가게 하는 표시다.
```sql
UPDATE common.outbox_event SET status = 'PROCESSING', locked_until = now() + interval '30 seconds', updated_at = now()
 WHERE id IN (SELECT id FROM common.outbox_event
               WHERE status = 'READY' AND next_attempt_at <= now()
               ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED)
RETURNING id, aggregate_id, event_type, topic, payload, trace_id, attempts, created_at
```
3. **트랜잭션 밖에서** 행마다(행 하나의 실패가 나머지를 막지 않도록 행 단위 `try/catch`)
   1. 봉투를 만든다: `new EventEnvelope(row.id(), row.eventType(), row.createdAt(), row.aggregateId(), row.traceId(), objectMapper.readTree(row.payload()))` → `objectMapper.writeValueAsString(envelope)`가 Kafka 메시지 값(JSON 문자열)이다.
   2. `kafkaTemplate.send(row.topic(), row.aggregateId().toString(), json)` — 키는 집계 ID 문자열. `send`는 `CompletableFuture<SendResult<String, String>>`을 **즉시** 돌려주고 실제 전송은 뒤에서 일어난다. 그래서 `.get(5, TimeUnit.SECONDS)`로 브로커의 응답을 기다려야 "보냈다"를 알 수 있다. `get`은 `InterruptedException`·`ExecutionException`·`TimeoutException`을 던질 수 있고, 전부 "발행 실패"로 취급한다(`catch (Exception e)`).
4. 행마다 결과 기록(짧은 트랜잭션, `WHERE id = ?`의 `UPDATE`)
   - 성공 → `SET status = 'SENT', sent_at = now(), locked_until = NULL, updated_at = now()`
   - 실패 → `attempts + 1`이 10 이상이면 `status = 'DEAD'`, 아니면 `status = 'READY'` + `next_attempt_at = now() + 10초`. 둘 다 `attempts = attempts + 1`, `locked_until = NULL`, `last_error`(예외 메시지를 **500자로 잘라** 넣는다. `last_error`가 `varchar(500)`이라 넘치면 이 UPDATE가 실패해서 행이 `PROCESSING`으로 남는다)
5. 3~4가 성공한 건수를 반환
- 선점 쿼리의 `now()`는 DB 시각이다(릴레이는 앱 `Clock` 대신 DB 시각으로 일관되게 동작).
- 3의 발행이 성공했는데 4의 기록 전에 서버가 죽으면 행이 `PROCESSING`으로 남았다가 리스(30초)가 지나 1번에서 회수되어 **다시 발행된다**. 같은 `eventId`로 두 번 가므로 consumer가 `eventId`로 중복을 걸러야 한다(3-2). 이것이 "리뷰 때 물어볼 것"의 첫 질문이다.
- 확인 필요: Kafka가 멈춰 있으면 행마다 최대 5초씩 기다려 100건이면 리스 30초를 넘길 수 있다. 문서는 이 경우를 따로 정하지 않았다(릴레이가 한 개이고 `fixedDelay`라 다음 실행과 겹치지는 않는다).

**`EventEnvelopes`** (`@Component`, 의존성 `ObjectMapper`) — consumer의 첫 줄. Kafka에서 받은 JSON 문자열을 봉투와 이벤트 record로 푼다.
- `<T> TypedEnvelope<T> parse(String json, Class<T> payloadType)` → `record TypedEnvelope<T>(EventEnvelope envelope, T payload)`. 사용: `TypedEnvelope<ShopOpenedEvent> typed = envelopes.parse(message, ShopOpenedEvent.class);` 뒤에 `typed.envelope()`(봉투), `typed.payload()`(이벤트 record)를 쓴다.
- 순서: ① `objectMapper.readValue(json, EventEnvelope.class)` ② `objectMapper.treeToValue(envelope.payload(), payloadType)` ③ 둘을 `TypedEnvelope`로 묶어 반환. `treeToValue`는 `JsonNode`를 지정한 타입으로 바꾸는 메서드다(Jackson 3 `tools.jackson` 기준 정확한 이름·시그니처는 확인 필요).
- 깨진 JSON이거나 필드가 맞지 않으면 `tools.jackson.core.JacksonException`(unchecked)을 **그대로 던진다**. 다시 시도해도 같은 결과라서 3-2에서 "재시도하지 않을 예외"로 분류해 바로 DLT로 보낸다.

**`EventContext`** (`public final class`) — 이벤트를 처리하는 동안 로그에 `traceId`·`eventId`가 찍히게 하는 헬퍼. `static void run(EventEnvelope envelope, Runnable action)`
1. `MDC.put(TraceIds.MDC_KEY, 봉투의 traceId)` — 봉투의 `traceId`가 `null`이면 `TraceIds.newId()`로 새로 만든다. `MDC.put("eventId", envelope.eventId().toString())`
2. `try { action.run(); } finally { 두 키를 MDC.remove }` — consumer 스레드는 다음 메시지에도 재사용되므로 값이 남지 않게 finally에서 지운다.
- 왜: HTTP 요청의 `traceId`가 outbox → 봉투 → consumer 로그까지 이어져, Kibana에서 `traceId` 하나로 가게 개설부터 역할 부여까지 한 번에 검색된다(ADR-010).

**3) 가게 개설 이벤트**
- `shop.api.event`: `ShopTopics.SHOP_OPENED = "shop.shop-opened.v1"`, `public record ShopOpenedEvent(UUID shopId, UUID memberId)` — 다른 모듈(member)이 import하므로 `shop.api` 쪽에 둔다. `shop.api.event`는 `shop.api`의 하위 패키지라 `@NamedInterface("api")`가 이 패키지에도 필요한지 `ModularityTest`로 확인한다(확인 필요: Modulith가 하위 패키지를 같은 `api` 이름 있는 인터페이스로 보는지. 문서·코드에 아직 정해진 것이 없다. 다른 모듈의 `api.event`도 같다).
- `shop.infrastructure.ShopTopicConfig`: `@Bean NewTopic shopOpenedTopic()` → `TopicBuilder.name(SHOP_OPENED).partitions(3).replicas(1).build()` (`org.springframework.kafka.config.TopicBuilder`. 이후 토픽도 각 발행 모듈의 infrastructure에 같은 방식으로). 앱이 시작할 때 Boot가 만든 `KafkaAdmin`이 `NewTopic` 빈을 보고 브로커에 토픽이 없으면 만든다 — 그래서 브로커가 떠 있어야 앱이 시작된다.
- `OpenShopService.open`: 의존성에 `OutboxPublisher`를 더하고, 기존 `Shop savedShop = shopRepository.save(shop)` 바로 다음 같은 트랜잭션에서 `outboxPublisher.publish(SHOP_OPENED, "SHOP", savedShop.getId(), new ShopOpenedEvent(savedShop.getId(), memberId))`. 사업자번호 중복 예외는 `save` 앞에서 나므로 그때는 outbox 행도 생기지 않는다.

**4) member — 역할 부여**
- `Member.promoteToSeller()`: USER면 SELLER로, 이미 SELLER·ADMIN이면 아무것도 안 함. `Member.demoteToUser()`: SELLER면 USER로, 아니면 아무것도 안 함. 예외를 던지지 않는 이유는 같은 이벤트가 두 번 와도, ADMIN 회원이 가게를 열고 닫아도 결과가 안전해야 해서다(`MemberRole`은 `USER, SELLER, ADMIN`).
- `MemberRoleService` (member.application, `@Service`, 의존성 `MemberRepository`) — consumer가 부르는 "역할 바꾸기" 유스케이스. `@Transactional void grantSeller(UUID memberId)`
  1. `memberRepository.findById(memberId)` — 회원이 없으면 `log.warn` 후 return(예외 금지. 공통 규칙 Q: 테스트 사이에 남은 메시지일 수 있다)
  2. `member.promoteToSeller()` — `save`는 부르지 않는다. 변경은 커밋 때 더티 체킹으로 저장된다.
- `member/package-info.java`: `allowedDependencies = {"common", "shop::api"}` — 현재 값은 `{"common"}`이다. `ShopTopics`·`ShopOpenedEvent`가 `shop::api`에 있어서 추가한다.
- `ShopEventConsumer` (member.infrastructure) — **Kafka 메시지를 받아 서비스를 부르는 얇은 어댑터**. 의존성 `EventEnvelopes`, `MemberRoleService`
  ```java
  @KafkaListener(topics = ShopTopics.SHOP_OPENED, groupId = "member")
  void onShopOpened(String message) {
      TypedEnvelope<ShopOpenedEvent> typed = envelopes.parse(message, ShopOpenedEvent.class);
      EventContext.run(typed.envelope(), () -> roleService.grantSeller(typed.payload().memberId()));
  }
  ```
  - `@KafkaListener`: Spring이 이 메서드를 컨테이너 스레드에서 호출한다. `message`는 레코드의 **값**(JSON 문자열, `StringDeserializer`)이다. 키·offset이 필요하면 `ConsumerRecord<String, String>`을 받을 수도 있지만 지금은 값만 쓴다.
  - `groupId = "member"`: consumer 그룹 이름. **그룹마다 메시지를 한 번씩 받는다**(member 그룹, 나중의 notification 그룹이 같은 토픽을 각자 받는다). 같은 그룹 안에서는 파티션을 나눠 가진다. 그룹 이름은 모듈 이름으로 통일한다.
  - 메서드가 정상 반환하면 offset이 저장되고(`ack-mode: record`), 예외가 나면 저장되지 않아 에러 핸들러(3-2)가 처리한다.
- 역할 반영 전에도 판매자 기능은 소유권으로 동작한다(1-6). 토큰의 role은 다음 로그인 때 갱신된다.

**5) 테스트 헬퍼**
- `support/Eventually` (공통 규칙 Q): `static void await(Duration timeout, ThrowingRunnable assertion)`. `ThrowingRunnable`은 `void run() throws Exception`을 가진 함수형 인터페이스다(JDK에 없어 `support`에 함께 만든다). 100ms마다 `assertion.run()`을 호출해 예외 없이 끝나면 return하고, `timeout`이 지나면 **마지막으로 난 예외**를 던진다. 사용: `Eventually.await(Duration.ofSeconds(5), () -> assertThat(role(memberId)).isEqualTo("SELLER"));`
- `support/OutboxAssert`: `List<Map<String, Object>> rows(String topic)` — `JdbcTemplate`으로 `common.outbox_event`에서 `topic = ?`인 행을 `created_at` 순으로 읽는다. 한 행이 컬럼 이름 → 값의 `Map`이다(예: `rows.get(0).get("status")`, `.get("trace_id")`). `payload`(jsonb)는 `PGobject`로 오므로 `toString()`으로 JSON 문자열을 얻어 `ObjectMapper.readTree`로 필드를 읽는다.

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

**구현 순서**: ① `common.inbox` 마이그레이션 → `InboxGuard` → ② `KafkaConfig` → ③ 기존 `ShopEventConsumer.onShopOpened`에 `InboxGuard` 적용 → ④ 폐업 구현물 표 1~14번을 번호 순서대로 → ⑤ `ShopController` → 테스트(`InboxGuardTest` → `KafkaDltTest` → `CloseShopTest`).

**1) common.inbox**

마이그레이션 `db/migration/common/V{...}__common_create_processed_message.sql`: `common.processed_message` — `consumer varchar(100) X`, `event_id uuid X`, `processed_at timestamptz X`, `pk_processed_message (consumer, event_id)`

`InboxGuard` (`@Component`, common.inbox, 의존성 `JdbcTemplate`, `Clock`) — **"이 이벤트를 이 consumer가 이미 처리했는가"를 확인하고 처음일 때만 처리하는 문지기**. Kafka는 같은 메시지를 두 번 줄 수 있어서(릴레이 재발행, 재시도, 리밸런스) 모든 consumer가 이것을 거친다. 부르는 곳: 각 모듈의 `@KafkaListener` 메서드(`inboxGuard.runOnce(...)`).
- `@Transactional public void runOnce(String consumer, EventEnvelope envelope, Runnable action)` → 반환 없음
  - `consumer`: 처리 주체의 이름(규칙 `{구독 모듈}.{이벤트}`, 예 `member.shop-opened`). 같은 이벤트를 member와 notification이 각자 한 번씩 처리해야 하므로 `(consumer, eventId)` 쌍으로 중복을 판단한다.
  - `envelope`: 파싱한 봉투. `eventId`가 멱등 키다.
  - `action`: 비즈니스 처리(예: `() -> roleService.grantSeller(memberId)`). **처음 본 이벤트일 때만** 실행된다.
  1. MDC 설정 — `EventContext.run`과 같은 일(`traceId`, `eventId`)을 한다.
  2. `jdbcTemplate.update("INSERT INTO common.processed_message (consumer, event_id, processed_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", ...)`의 **반환값**이 0이면 이미 처리한 이벤트다 → `log.info("duplicate event skipped")` 후 return(예외 아님. 정상 종료라 offset이 저장된다). 같은 이벤트가 동시에 두 스레드로 와도 두 번째 INSERT는 첫 트랜잭션이 끝나길 기다렸다가 0이 된다.
  3. `action.run()` — **2의 INSERT와 같은 트랜잭션**이다. `action` 안의 `@Transactional(REQUIRED)` 서비스도 이 트랜잭션에 합류한다. 예외가 나면 INSERT도 함께 롤백되어 "처리 안 한 이벤트"로 남고, 재시도 때 다시 처리된다. 기록과 처리를 다른 트랜잭션에 두면 "기록만 남고 처리는 실패"해서 영영 처리되지 않는다.
  4. MDC 정리(finally)

**2) common.config.KafkaConfig** — consumer 메서드가 예외를 던졌을 때 **다시 시도하고, 끝내 안 되면 DLT(dead letter topic, 처리 못 한 메시지를 모아 두는 `{topic}.dlt` 토픽)로 옮기는** 규칙을 정한다. 이 설정이 없으면 실패한 메시지는 Spring Kafka의 기본 동작을 따른다.
- `@Bean CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template)`: 빈 한 개를 만들어 반환한다. 만드는 순서는 다음과 같다.
  1. `DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template, (record, ex) -> new TopicPartition(record.topic() + ".dlt", record.partition()))` — 재시도를 다 쓴 레코드를 **같은 키·값 그대로** DLT로 보내는 객체. 람다는 "어느 토픽·파티션으로 보낼지"를 정하며, 원본과 **같은 파티션 번호**를 쓰므로 DLT도 원본처럼 파티션 3개여야 한다(아래 `NewTopic`).
  2. `ExponentialBackOff backOff = new ExponentialBackOff(1000, 2.0)` — 첫 대기 1000ms, 이후 2배씩(1초 → 2초 → 4초). `backOff.setMaxAttempts(3)`으로 재시도를 3번으로 제한한다. 그래서 **최초 1번 + 재시도 3번 = 4번 시도** 후 DLT로 간다(완료 확인 `KafkaDltTest`의 "4번 시도").
  3. `DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff)` — 리스너가 예외를 던지면 같은 레코드를 대기 후 다시 전달하고, 횟수를 다 쓰면 `recoverer`를 부른다.
  4. `errorHandler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class, BusinessException.class)` — 이 예외는 재시도 없이 **바로** DLT로 보낸다(시도 1번). 다시 해도 결과가 같기 때문이다. 이 목록에 없는 예외(`DataAccessException`, 낙관적 락 충돌 등 일시적 오류)는 재시도 대상이다.
  5. `return errorHandler`
- Boot는 `CommonErrorHandler` 빈을 기본 리스너 컨테이너 팩토리에 자동으로 연결한다(Boot 4.1.1의 `KafkaAnnotationDrivenConfiguration`이 `ObjectProvider<CommonErrorHandler>`를 받아 팩토리에 넣는 것을 jar로 확인, 2026-10-09). 그래서 `ConcurrentKafkaListenerContainerFactory`를 직접 만들지 않는다
- DLT 토픽도 `NewTopic`으로 만든다(파티션 3). 발행 모듈의 `XxxTopicConfig`에서 원본마다 `{topic}.dlt`를 같이 만든다. 3-1의 `shop.shop-opened.v1.dlt`는 아래 4) 표 6번이 만든다

**3) 기존 consumer에 적용**: `ShopEventConsumer.onShopOpened`의 `EventContext.run(...)`을 `inboxGuard.runOnce("member.shop-opened", typed.envelope(), () -> roleService.grantSeller(typed.payload().memberId()))`로 바꾼다(`typed = envelopes.parse(...)`는 그대로). 의존성에 `InboxGuard`를 더한다. `EventContext.run`은 따로 감싸지 않는다(`InboxGuard` 1번이 같은 MDC 설정을 한다).

**4) 가게 폐업**

폐업 조건(진행 중인 주문)은 order의 데이터인데 shop이 order를 부르면 의존이 순환한다(order는 이미 `shop::api`를 쓴다). 그래서 shop이 조건 확인 인터페이스(`ShopClosePrecondition`)만 `shop.api`에 정의하고 order가 구현체를 빈으로 등록한다. 아래 표 1~14를 번호 순서대로 만든다("쓰이는 곳"의 숫자는 이 표의 행 번호다). 이름은 권장안이고 쓰다 바꿔도 된다. 저장소 구조는 Part 2와 같다(인터페이스는 `domain`, `@Query` 구현은 `infrastructure`의 `Jpa...Repository`).

| # | 대상 | 시그니처 · 동작 | 쓰이는 곳 |
|---|---|---|---|
| 1 | `OrderErrorCode.SHOP_HAS_ACTIVE_ORDERS` | 422, "진행 중인 주문이 있어 폐업할 수 없습니다."(공통 규칙 R) | 4 |
| 2 | `OrderRepository.existsByShopIdAndStatusIn` | `boolean (UUID shopId, Collection<ShopOrderStatus> statuses)` — `ShopOrder`에서 `shopId`가 같고 `status`가 집합에 속하는 행이 있는지. `ShopOrder`는 `Order` 애그리거트 안의 엔티티라 전용 저장소가 없다. 시그니처는 `OrderRepository`(domain), `@Query`는 `JpaOrderRepository`(2-10의 `findAutoConfirmTargets`와 같은 방식. 2-7이 `ShopOrderQueryRepository`를 따로 만들었다면 그쪽에 둔다, 확인 필요) | 4 |
| 3 | `shop.api.ShopClosePrecondition` | `void check(UUID shopId)` — 조건을 어기면 `BusinessException`을 던지고 통과하면 아무 일도 없다. shop은 스프링이 주입하는 `List<ShopClosePrecondition>`을 돌며 확인만 하고, 구현체가 하나도 없으면 목록이 비어 모두 통과한다 | 4, 8 |
| 4 | `OrderShopClosePrecondition` (order.application, `@Component`, `implements ShopClosePrecondition`) | 그 가게의 가게주문 중 `PENDING`·`PAID`·`SHIPPED`·`DELIVERED`가 하나라도 있으면(`orderRepository.existsByShopIdAndStatusIn(shopId, 그 집합)`) `BusinessException(SHOP_HAS_ACTIVE_ORDERS)` | 8 |
| 5 | `shop.api.event` | `ShopTopics.SHOP_CLOSED = "shop.shop-closed.v1"`, `record ShopClosedEvent(UUID shopId, UUID memberId)` | 6, 8, 12, 14 |
| 6 | `ShopTopicConfig`에 `NewTopic` 3개 추가 | `SHOP_CLOSED`, 그리고 두 `.dlt` 토픽 `SHOP_OPENED + ".dlt"`·`SHOP_CLOSED + ".dlt"`(전부 파티션 3·복제 1. 3-1의 `shopOpenedTopic()`과 같은 방식) | `KafkaConfig`의 DLT 전송 |
| 7 | `Shop.close(Instant now)` | `this.status = this.status.transitTo(ShopStatus.CLOSED)`(`ShopStatus`가 이미 ACTIVE → CLOSED만 허용), `closedAt = now`(`closed_at` 컬럼·필드는 이미 있다) | 8 |
| 8 | `CloseShopService` (shop.application, `@Service`) | 가게 폐업 유스케이스. 의존성 `ShopRepository`, `List<ShopClosePrecondition> preconditions`, `OutboxPublisher`, `Clock`. `@Transactional void close(UUID memberId, UUID shopId)`. 순서는 표 아래 | `ShopController`(5) |
| 9 | `ShopApi.countActiveShops` | `int (UUID memberId)` — 그 회원 소유의 **ACTIVE** 가게 수. `ShopRepository`에 건수 조회를 새로 더한다(이름은 자유, 예: `countByMemberIdAndStatus`) | 12(SELLER 회수 판단), 3-6 탈퇴 조건 |
| 10 | `ShopApi.getOwnerId` | `UUID (UUID shopId)` — 가게 주인의 memberId. 가게가 없으면 `SHOP_NOT_FOUND`. `shopRepository.findById`로 읽는다 | 3-5 알림(가게 주인 수신) |
| 11 | `ShopApi.getShopIdsByOwner` | `List<UUID> (UUID memberId)` — 그 회원 소유의 가게 ID 전부(**CLOSED 포함**). 기존 `ShopRepository.findAllByMemberIdOrderByCreatedAtDesc`로 가능 | 3-6 탈퇴 전 미정산 확인 |
| 12 | `MemberRoleService.revokeSellerIfNoActiveShop` + member의 `ShopEventConsumer.onShopClosed` | `@Transactional void revokeSellerIfNoActiveShop(UUID memberId)`(의존성에 `ShopApi`를 더한다): ① 회원 조회(없으면 `log.warn` 후 return) ② `shopApi.countActiveShops(memberId)`를 **처리하는 시점에** 조회 ③ 0이면 `member.demoteToUser()`. consumer는 `@KafkaListener(topics = ShopTopics.SHOP_CLOSED, groupId = "member")` → `inboxGuard.runOnce("member.shop-closed", ...)` → 이 메서드. 이벤트에 "남은 가게 수"를 담지 않는 이유: 같은 회원의 가게 2개가 동시에 폐업되면 각 이벤트가 만들어질 때의 "남은 수"는 서로 모르는 값이라 둘 다 1이 되어 SELLER가 안 회수될 수 있다. 처리 시점에 다시 세면 마지막 이벤트는 항상 0을 본다. 두 consumer가 동시에 같은 회원을 바꾸다 낙관적 락 충돌이 나면 재시도 대상이라 다음 시도에서 해소된다 | `shop-closed` 소비 |
| 13 | `ProductRepository.findAllByShopIdAndStatusIn` | `List<Product> (UUID shopId, Collection<ProductStatus> statuses)` — 파생 쿼리. 현재 `ProductRepository`에는 없다 | 14 |
| 14 | `ProductDiscontinueService.discontinueAllByShop` (product.application, `@Service`) + product의 `ShopEventConsumer.onShopClosed`(product.infrastructure) | `@Transactional void discontinueAllByShop(UUID shopId)`: ① `findAllByShopIdAndStatusIn(shopId, {ON_SALE, HIDDEN})` ② 각 상품에 `product.changeStatus(DISCONTINUED)`. 이미 단종된 상품은 조회에서 빠지므로 이벤트가 두 번 와도 안전하다. consumer는 `@KafkaListener(topics = ShopTopics.SHOP_CLOSED, groupId = "product")` → `inboxGuard.runOnce("product.shop-closed", ...)` → 이 메서드 | `shop-closed` 소비 |

`CloseShopService.close` 순서 (한 트랜잭션)
1. `shopRepository.findById(shopId)` — 없으면 404 `SHOP_NOT_FOUND` → `shop.verifyOwner(memberId)`(남의 가게면 403 `FORBIDDEN`) → `shop.verifyActive()`(이미 CLOSED면 `SHOP_NOT_ACTIVE`)
2. `preconditions.forEach(p -> p.check(shopId))` — 하나라도 던지면 여기서 끝난다(아직 바꾼 것이 없어 되돌릴 것도 없다)
3. `shop.close(Instant.now(clock))` — 저장은 커밋 때 더티 체킹으로 된다
4. `outboxPublisher.publish(SHOP_CLOSED, "SHOP", shopId, new ShopClosedEvent(shopId, memberId))`

- 9~11은 `shop.api.ShopApi`에 더하고 `ShopApiImpl`이 구현한다.
- 두 consumer(12, 14)는 서로 다른 그룹(`member`, `product`)이라 같은 `shop-closed` 메시지를 각자 한 번씩 받는다.
- 모듈 의존: member는 3-1에서 `shop::api`를 이미 추가했다. product는 이미 `shop::api`를 허용한다.

**5) shop.web**: `ShopController`에 `@DeleteMapping("/{id}")` 추가 — `@CurrentMember UUID memberId`, `@PathVariable UUID id` → `closeShopService.close(memberId, id)` → 204 No Content(`ResponseEntity.noContent().build()`)

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `common/inbox/InboxGuardTest` | [ ] 같은 eventId로 `runOnce` 두 번 → action 1번 / [ ] action이 예외 → `processed_message`에 행 없음 → 다시 `runOnce` → action 실행됨 |
| `common/config/KafkaDltTest` | [ ] 항상 예외를 던지는 테스트용 리스너(`src/test`의 `@Component`, 테스트 전용 토픽 `test.always-fail.v1`) → 4번 시도 후 `test.always-fail.v1.dlt`에 메시지 / [ ] 깨진 JSON → 재시도 없이 바로 DLT(시도 1번) |
| `shop/web/CloseShopTest` | [ ] 진행 중(PAID) 가게주문이 있는 가게 폐업 → 422 `SHOP_HAS_ACTIVE_ORDERS` / [ ] 폐업 → 204, outbox `shop.shop-closed.v1` 1건 → 릴레이 → 상품 전부 DISCONTINUED / [ ] 가게 2개 중 1개 폐업 → SELLER 유지, 나머지 폐업 → 회수 / [ ] **같은 회원의 가게 2개를 동시에 폐업 → 둘 다 성공하고 최종적으로 SELLER 회수** / [ ] 같은 shop-closed 이벤트를 두 번 소비(테스트에서 같은 메시지를 직접 두 번 send) → 처리 1번 |

**추가 항목**
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
- 상품 이벤트의 `version`은 발행 시점 상품 버전이다(읽는 방법은 아래 "상품 이벤트 공통").

### 할 일

**구현 순서**: ① 이벤트 정의(1) → ② 구현물 표(2) 1~4번 → ③ `OrderEventPublisher`(3) → ④ 3)의 발행 위치 표를 위에서 아래로(member → product → order. 구현물 5번은 구매확정 행에서) → ⑤ 테스트.

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

- 이벤트 record의 필드 타입은 공통 규칙 P대로 원시 타입이다. 엔티티의 값을 옮길 때: 금액은 `Money.amount()`(long), enum은 `.name()`(String), 시각은 `Instant`.

**2) 구현물** — 아래 3) 발행 위치의 서비스를 고치기 전에 만들어 두는 것이다. 번호 순서대로 만든다.

| # | 대상 | 규격 | 쓰이는 곳 |
|---|---|---|---|
| 1 | `order/api/package-info.java` | `@NamedInterface("api")`. `member/api`·`product/api`·`payment/api`·`shop/api`에는 이미 있고 `order/api`만 현재 코드에 없어 이 단계에서 `order::api`가 처음 공개된다. 하위 패키지 `api.event`가 같은 인터페이스로 보이는지는 `ModularityTest`로 확인한다(3-1의 확인 필요와 같다) | `OrderTopics`·이벤트 record를 import하는 다른 모듈(3-4~3-6) |
| 2 | `MemberTopicConfig`, `ProductTopicConfig`, `OrderTopicConfig` (각 모듈 `infrastructure`) | 1)의 토픽마다 `NewTopic`(파티션 3·복제 1)과 `{topic}.dlt`(공통 규칙·3-2). member 1개, product 3개, order 6개. `ShopTopicConfig`와 같은 방식 | 앱 시작 시 `KafkaAdmin` |
| 3 | `ProductRepository.flush()` | `void flush()` — 현재 `ProductRepository` 인터페이스에는 없다(`MemberAddressRepository.flush()`와 같은 방식으로 JPA `flush`를 위임). 엔티티를 수정한 직후에는 `@Version`이 아직 오르지 않았으므로(flush 때 오른다) 이벤트를 만들기 **전에** 부르고 `product.getVersion()`을 읽는다. 등록(`save` 직후)은 초기값이다 | 상품 이벤트 4곳(아래 "상품 이벤트 공통") |
| 4 | `Order.autoConfirm` 반환 타입 변경 | 확정한 품목 수(int, 2-10) 대신 확정한 품목 목록 `List<OrderLine>`을 돌려준다(품목마다 이벤트를 내기 위해). `AutoConfirmService`는 트랜잭션 안에서 받은 목록을 순회하며 품목마다 `lineConfirmed`를 부르고, 건수는 `list.size()`로 센다 | `AutoConfirmService` |
| 5 | `ConfirmPurchaseService.confirm`의 `lineConfirmed` 호출 | 2-10 3번이 응답을 만들려고 이미 `order.findLine(lineId)`로 찾은 `line`을 `lineConfirmed`에도 넘겨 한 번 부른다. 품목을 다시 찾지 않고 `confirmLine`의 반환 타입도 바꾸지 않는다 | 수동 구매확정 |

**3) 발행 위치** — 해당 상태 변경과 같은 트랜잭션 안, 도메인 메서드 호출 직후

발행하는 모양은 모두 같다: 서비스(또는 엔티티를 바꾼 직후의 코드)가 `outboxPublisher.publish(토픽, aggregateType, aggregateId, 이벤트 record)`를 부른다. 서비스의 `@Transactional` 안에서 부르므로 상태 변경이 롤백되면 이벤트도 사라지고(완료 확인의 "롤백되는 경우 → outbox 0건"), 이 단계에서 서비스에 `OutboxPublisher`(order는 `OrderEventPublisher`) 의존성을 더한다. 한 흐름에서 이벤트가 둘이면(상품 수정 + 가격 변경) 같은 키(`productId`)라 **호출한 순서대로** 같은 파티션에서 소비된다.

`OrderEventPublisher` (order.application, `@Component`, 의존성 `OutboxPublisher`) — **Order 애그리거트를 받아 이벤트 record를 조립해 `OutboxPublisher`로 보내는 곳**. 이벤트 조립 코드가 `OrderPaymentApplier`·`OrderExpirationService`·`ShipmentService` 등에 흩어지지 않게 한다. 모든 메서드는 `@Transactional`을 붙이지 않는다(호출자 트랜잭션 안에서 불리고, `OutboxPublisher`가 `MANDATORY`로 검사한다). 인자는 문서에 정해져 있지 않아 아래는 권장안이다.

| 메서드 | 이벤트 · aggregate | 필드를 채우는 방법 |
|---|---|---|
| `paid(Order order)` | `ORDER_PAID` · `ORDER`/`order.getId()` | `orderId`·`memberId`는 `order.getId()`·`order.getMemberId()`, `totalAmount`는 `order.getTotalAmount().amount()`. `shopOrders`는 `order.getShopOrders()`의 가게주문마다 `PaidShopOrder(shopOrder.getId(), shopOrder.getShopId(), lines)`, `lines`는 품목마다 `PaidLine(line.getId(), line.getProductId(), line.getProductName(), line.getQuantity())` |
| `closed(Order order, String reason)` | `ORDER_EXPIRED` · `ORDER`/`order.getId()` | `OrderExpiredEvent(order.getId(), order.getMemberId(), reason)`. `reason`은 문자열 `"EXPIRED"` 또는 `"PAYMENT_FAILED"` |
| `shipped(...)` / `delivered(...)` | `SHOP_ORDER_SHIPPED` / `SHOP_ORDER_DELIVERED` · `ORDER`/**`orderId`** | 가게주문(`shopOrder.getId()`, `getCarrier()`, `getTrackingNumber()`)과 주문(`orderId`, `memberId`)에서 |
| `lineRefunded(...)` | `ORDER_LINE_REFUNDED` · `ORDER`/`orderId` | 환불(`refundId`, 금액)과 품목·주문 ID에서 |
| `lineConfirmed(...)` | `ORDER_LINE_CONFIRMED` · `ORDER`/`orderId` | 품목(`productId`, `confirmedAt`, **`confirmedAmount().amount()`**), 그 품목이 속한 가게주문의 `shopId`, 주문의 `memberId`에서. 품목 엔티티에는 `shopId`가 없어 가게주문을 거쳐야 한다 |

- `closed`의 `reason`은 이벤트의 값이다. `OrderPaymentApplier.fail(order, reason)`의 `reason`(`"PG 거절: ..."`, `failure_reason`에 저장되는 문장)과 다르다 — `fail` 안에서는 `closed(order, "PAYMENT_FAILED")`처럼 상수를 따로 넘긴다.
- **엔티티를 먼저 읽은 뒤 발행한다**: CAS UPDATE(`expire` 등)는 엔티티를 거치지 않으므로, `memberId`가 필요한 `closed`는 `expireOne`에서 CAS 뒤 `findById`로 읽은 `order`로 부른다(2-5의 3번 다음).
- **이미 처리된 호출은 발행하지 않는다**: `OrderPaymentApplier.applyPaymentState`의 "이미 처리됨" 가드, `RefundCompleter.complete`의 "`!readyToComplete()`면 현재 상태 반환" 가드에서 일찍 return하는 경로에는 발행 코드가 닿지 않는다. 발행은 **실제로 상태를 바꾼 경로에만** 둔다(`complete`·`fail`·`markPaid` 뒤). 그래야 같은 결과를 두 번 반영해도 이벤트가 한 번만 나간다.

| 흐름 | 클래스·메서드 | 발행 | aggregateType / aggregateId |
|---|---|---|---|
| 가입 (1-3) | `SignupService.signUp` | `MEMBER_REGISTERED` | `MEMBER` / memberId |
| 상품 등록 (1-7) | `RegisterProductService.register` | `PRODUCT_UPSERTED` | `PRODUCT` / productId |
| 상품 수정 (1-8) | `UpdateProductService.update` | `PRODUCT_UPSERTED`, 가격이 바뀌었으면 `PRODUCT_PRICE_CHANGED` | `PRODUCT` / productId |
| 상품 상태 (1-8) | `UpdateProductService.changeStatus` | `PRODUCT_UPSERTED`, DISCONTINUED면 `PRODUCT_DISCONTINUED`(reason `SELLER`) | `PRODUCT` / productId |
| 가게 폐업 단종 (3-2) | `ProductDiscontinueService.discontinueAllByShop` | 상품마다 `PRODUCT_UPSERTED` + `PRODUCT_DISCONTINUED`(reason `SHOP_CLOSED`) | `PRODUCT` / productId |
| 결제 완료 (2-4 승인, 2-6 대사) | `OrderEventPublisher.paid(order)` — `OrderPaymentApplier.complete`에서 호출 | `ORDER_PAID` | `ORDER` / orderId |
| 만료·결제 실패 (2-4, 2-5, 2-6) | `OrderEventPublisher.closed(order, reason)` — `OrderPaymentApplier.fail`, `OrderExpirationService.expireOne` (2-4의 결제 전 주문 취소 `PendingOrderCancelService`는 **발행하지 않는다** — 구매자가 직접 한 일이라 알림이 필요 없고 소비자가 알림뿐이다) | `ORDER_EXPIRED` | `ORDER` / orderId |
| 발송·배송완료 (2-7) | `ShipmentService.ship` / `deliver` | `SHOP_ORDER_SHIPPED` / `SHOP_ORDER_DELIVERED` | `ORDER` / orderId (같은 주문 이벤트 순서 유지) |
| 환불 완료 (2-8, 2-9) | `RefundCompleter.complete` | `ORDER_LINE_REFUNDED` | `ORDER` / orderId |
| 구매확정 (2-10) | `ConfirmPurchaseService.confirm`, `AutoConfirmService`(품목마다) | `ORDER_LINE_CONFIRMED` | `ORDER` / orderId |

표를 코드에 옮길 때 (member·product 쪽은 `OrderEventPublisher` 같은 별도 클래스를 두지 않고 서비스에서 바로 `OutboxPublisher`를 부른다)
- **가입** `SignupService.signUp`: `memberRepository.save(member)`가 돌려준 `savedMember`로 `MemberRegisteredEvent(savedMember.getId(), savedMember.getEmail(), savedMember.getNickname())`를 발행한다. 중복 이메일 예외는 save 앞이라 이벤트가 나가지 않는다.
- **상품 이벤트 공통**: `ProductUpsertedEvent`는 `productId`, `shopId`, `name`, `description`, `category`(`product.getCategory().name()`), `price`(`product.getPrice().amount()`), `status`(`product.getStatus().name()`), `subscribable`, `version`을 현재 상품 상태에서 채운다. 상품 변경마다 **전체 스냅샷**을 보낸다(search가 이 값으로 색인을 덮어쓴다). 같은 조립 코드를 등록·수정·상태 변경·단종 네 곳이 쓰므로 한 곳에 모아도 된다(문서는 order에만 `OrderEventPublisher`를 정했고, product는 클래스를 정하지 않았다).
- **`version`**: 등록 외의 이벤트는 위 구현물 3번 `flush()` 뒤에 `product.getVersion()`을 읽어 채운다. 색인 소비자(Part 6)가 이 값이 작은 이벤트를 "오래된 것"으로 무시한다.
- **수정** `UpdateProductService.update`: `product.update(...)`가 돌려주는 `Optional<PriceChange>`가 비어 있으면 가격이 안 바뀐 것이다. 값이 있으면 `ProductPriceChangedEvent(productId, change.oldPrice().amount(), change.newPrice().amount(), 가격 이력 저장에 쓴 now)`를 `PRODUCT_UPSERTED` 다음에 발행한다. 이미 이력 저장 코드(`insertPriceHistory`)가 같은 `ifPresent` 안에 있으므로 거기에 붙인다.
- **상태 변경** `UpdateProductService.changeStatus`: `to == DISCONTINUED`일 때만 `ProductDiscontinuedEvent(productId, shopId, "SELLER")`를 `PRODUCT_UPSERTED` 다음에 추가한다.
- **가게 폐업 단종** `ProductDiscontinueService.discontinueAllByShop`: 상품마다 `changeStatus(DISCONTINUED)` → `flush` → `PRODUCT_UPSERTED` + `PRODUCT_DISCONTINUED`(reason `"SHOP_CLOSED"`).

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `{module}/.../EventPublishingTest` (모듈별 1개: `member/application`, `product/application`, `order/application`. payment는 발행하는 이벤트가 없어 제외한다) | [ ] 위 표의 흐름마다 outbox에 해당 토픽 **정확히 1건**, payload 필드 값 확인(`OutboxAssert`) / [ ] 실패해서 롤백되는 경우(예: 재고 부족 체크아웃, 단종 상품 수정) → outbox 0건 / [ ] 자동 구매확정으로 품목 3개 확정 → `ORDER_LINE_CONFIRMED` 3건 / [ ] 가격 변경 없는 상품 수정 → `PRODUCT_PRICE_CHANGED` 없음 / [ ] `ProductUpsertedEvent.version`이 DB의 `product.version`과 같다 |
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

**흐름 한눈에**
1. **적재**: 구매확정(3-3)이 `order-line-confirmed`를 내면 settlement의 consumer가 품목 한 건을 `settlement_item`에 쌓는다(`settlement_id`는 NULL = 아직 정산 전).
2. **정산**: 매월 5일 03:00에 `SettlementJob`이 `SettlementService.run(전월)`을 부른다. 미정산 품목이 있는 가게마다 `settleShop`이 `settlement` 행을 만들고, 그 기간의 품목에 정산 ID를 붙이고(`assign`), 붙은 품목의 합계로 수수료·정산액을 계산한다(`CALCULATED`).
3. **지급**(3-4b): 이어서 `payoutAll()`이 `CALCULATED`·`PAYOUT_FAILED` 정산마다 `PayoutGateway`로 송금하고 `PAID` + `settlement-paid` 이벤트를 낸다.

**구현 순서**: ① 마이그레이션(1) → ② 2)의 표를 1~14번 순서대로(설정 → 도메인 → 저장소 → 서비스·consumer → 잡) → ③ 정산 대상 JDBC 적재 헬퍼 → 테스트(`SettlementPeriodTest` → `SettlementItemConsumerTest` → `SettlementServiceTest`).

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
| payout_reference | varchar(100) | O | 지급 게이트웨이가 돌려준 이체 참조 ID (3-4b) |
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

**2) settlement 모듈** — 표의 행은 클래스 단위이고, `Settlement`·`SettlementPeriod`·저장소 메서드의 규격은 표 뒤에 메서드별로 적었다.

| # | 클래스 | 규격 |
|---|---|---|
| 1 | `common.config.BusinessProperties` | 업무 시간대 설정을 처음 읽는 곳이라 여기서 만든다: `@ConfigurationProperties("myroutine") record (@NotNull ZoneId businessZone)`. `application.yaml`의 `myroutine.business-zone: Asia/Seoul`은 이미 있고 읽는 클래스만 없다. `ClockConfig`에 `@EnableConfigurationProperties(BusinessProperties.class)`. "정산 월의 경계"처럼 날짜가 필요한 곳에서 `businessProperties.businessZone()`을 쓴다 |
| 2 | `SettlementProperties`, `SettlementConfig` | `@ConfigurationProperties("myroutine.settlement") record (@Min(0) @Max(10000) int feeRateBp)` — `application.yaml`에 `myroutine.settlement.fee-rate-bp: 500`(500 = 5%)을 추가한다. `SettlementConfig`(`@Configuration`)에 `@EnableConfigurationProperties(SettlementProperties.class)`(`OrderConfig`와 같은 방식) |
| 3 | `settlement/package-info.java` | `@ApplicationModule(allowedDependencies = {"common", "order::api"})`. 현재 settlement 모듈에는 코드가 없어 파일이 없다. `shop::api`는 3-4b 조회 API(`verifyOwner`)가, `member::api`는 3-6(`WithdrawalPrecondition`)이 쓸 때 더한다(공통 규칙 S는 Part 3 끝 기준 값이다) |
| 4 | `SettlementStatus` | 상태 enum. 허용 전이: `CALCULATED → {PAID, PAYOUT_FAILED}`, `PAYOUT_FAILED → {PAID, PAYOUT_FAILED}`(실패한 정산을 다시 시도). `ShopStatus.transitTo`처럼 `transitTo(to)`가 허용 밖이면 `INVALID_STATE_TRANSITION`을 던진다 |
| 5 | `Settlement` (`@Entity`) | 정산 한 건. 필드는 컬럼과 1:1. 메서드는 아래 `Settlement` 메서드 표 |
| 6 | `SettlementPeriod` | 정산 기간 값 객체 `record (LocalDate start, LocalDate end)`. 메서드는 아래 `SettlementPeriod` 절 |
| 7 | `SettlementSum` | `record SettlementSum(long grossAmount, int itemCount)` — `SettlementItemRepository.sumBySettlementId`의 반환 |
| 8 | `SettlementItemRepository` | 정산 대상 품목 저장소(domain 인터페이스 + infrastructure의 native `@Query`, Part 2와 같은 방식). 메서드는 아래 표. 이 단계에서 쓰는 4개만 만든다 |
| 9 | `SettlementRepository` | 정산 저장소. 메서드는 아래 표. `findIdsToPay`는 3-4b의 `payoutAll`이 쓴다 |
| 10 | `SettlementItemService` (settlement.application, `@Service`) | `@Transactional void register(OrderLineConfirmedEvent event)` — 품목 한 건을 적재한다. consumer(11)가 부른다 |
| 11 | `OrderEventConsumer` (settlement.infrastructure) | `order-line-confirmed`를 받아 `InboxGuard` 안에서 `register`를 부르는 `@KafkaListener` 어댑터. 의존성 `EventEnvelopes`, `InboxGuard`, `SettlementItemService` |
| 12 | `SettlementRunResult` | `record SettlementRunResult(int settledShops, int paid, int payoutFailed)` — `SettlementService.run`의 반환이고 `POST /admin/api/settlements/run`(3-4b)의 응답이다 |
| 13 | `SettlementService` (settlement.application, `@Service`) | `public SettlementRunResult run(YearMonth month)`와 가게 한 곳을 정산하는 `settleShop`. 가게 단위 트랜잭션과 지급의 외부 호출을 직접 나누려고 `@Transactional` 없이 `TransactionTemplate`을 쓴다(공통 규칙 K). 의존성 `SettlementRepository`, `SettlementItemRepository`, `SettlementProperties`, `BusinessProperties`, `Clock`, `TransactionTemplate tx`, `PayoutGateway`·`OutboxPublisher`(3-4b). 순서는 아래 4) |
| 14 | `SettlementJob` (settlement.infrastructure) | `@Scheduled(cron = "0 0 3 5 * *", zone = "Asia/Seoul")` → `jobRunner.run("settlement", () -> service.run(전월).settledShops())`. `전월`은 `YearMonth.now(clock.withZone(businessZone)).minusMonths(1)`처럼 업무 시간대 기준 현재 월에서 한 달을 뺀 값이다(`JobRunner.run`은 `Supplier<Integer>`를 받아 `settledShops()`를 처리 건수로 로그에 남긴다) |

**`Settlement` 메서드** (`Money`는 `common.model.Money`, `Money.of(long)`·`.amount()`)

| 메서드 | 하는 일 |
|---|---|
| (생성) | 엔티티를 `save`하지 않고 `SettlementRepository.insertIfAbsent`(native INSERT)로 만든다 — 같은 가게·기간이 이미 있으면 예외가 아니라 0행으로 알려 주기 위해서다. 그 뒤 `findById`로 읽어 아래 메서드를 쓴다 |
| `calculate(Money gross, int bp, int itemCount)` | `grossAmount = gross`, `feeRateBp = bp`, `itemCount`를 기록하고 `feeAmount = gross * bp / 10000`(`long` 정수 나눗셈 = 원 미만 내림. 12,345원·500bp면 617), `netAmount = gross - feeAmount` |
| `markPaid(String payoutReference, Instant now)` | `status.transitTo(PAID)`, `payoutReference`, `paidAt = now`. 0원 정산은 `payoutReference`에 `null` |
| `markPayoutFailed(String error)` | `status.transitTo(PAYOUT_FAILED)`, `lastError = error`. `payoutAttempts`를 1 올리는 곳도 여기로 읽힌다(확인 필요: 문서가 "`payout_attempts + 1`"을 따로 적었다) |

**`SettlementPeriod`** — "2026년 9월 정산"이 어느 날짜·시각 구간인지를 한곳에서 계산한다. 시간대를 잘못 쓰면 경계(8/31 23:59 대 9/1 00:00)의 품목이 다른 달로 간다.
- `static SettlementPeriod of(YearMonth month)`: `start = month.atDay(1)`, `end = month.atEndOfMonth()`(말일, **포함**. `period_end` 컬럼도 포함이다)
- `Instant fromInclusive(ZoneId zone)`: `start.atStartOfDay(zone).toInstant()` — 시작 시각(포함)
- `Instant toExclusive(ZoneId zone)`: `end.plusDays(1).atStartOfDay(zone).toInstant()` — 다음 달 1일 00:00(미포함). 쿼리는 `confirmed_at >= from AND confirmed_at < to`
- 2026-09, `Asia/Seoul` → `2026-08-31T15:00:00Z` ~ `2026-09-30T15:00:00Z`(완료 확인 `SettlementPeriodTest`). 순수 계산이라 단위 테스트로 검증한다.

**`SettlementItemRepository`** — domain 인터페이스 + infrastructure의 Spring Data 인터페이스에 native `@Query`(Part 2와 같은 방식). 조회 API용 목록 메서드는 3-4b에서 더한다.

| 메서드 | 의미 | 입력 → 반환 |
|---|---|---|
| native `void insertIgnoringConflict(...)` | 구매확정 품목 한 건을 적재한다. `order_line_id` unique(`uk_settlement_item_order_line`, INV-07)와 충돌하면 `ON CONFLICT ON CONSTRAINT ... DO NOTHING`으로 조용히 건너뛴다 | `id`(`Ids.newId()`), `orderLineId`, `shopId`, `orderId`, `productId`, `amount`, `confirmedAt`, 시각 → 없음 |
| `List<UUID> findShopIdsToSettle(Instant from, Instant to, UUID afterShopId, int limit)` | 기간 내 **미정산** 품목이 있는 가게 ID를 `afterShopId` 다음부터 오름차순으로 `limit`개 | → 가게 ID 목록(비면 끝) |
| `void assign(UUID settlementId, UUID shopId, Instant from, Instant to, Instant now)` | 그 가게의 기간 내 미정산 품목에 이번 정산 ID를 붙인다. 품목 수는 `sumBySettlementId`가 센다 | → 없음 |
| `SettlementSum sumBySettlementId(UUID settlementId)` | 이 정산 ID가 붙은 품목의 합계와 건수 | → `record SettlementSum(long grossAmount, int itemCount)` |

- `sumBySettlementId`의 SQL은 `SELECT COALESCE(SUM(amount), 0), COUNT(*)`다. PostgreSQL에서 `SUM(bigint)`은 `numeric`, `COUNT(*)`는 `bigint`이므로 record(`long`, `int`)에 바로 매핑되지 않는다. SQL에서 `::bigint`·`::int`로 캐스팅하거나 읽을 때 `longValue()`·`intValue()`로 바꾼다. native 쿼리를 record로 받는 방법(인터페이스 프로젝션 등)은 문서에 정해져 있지 않다(확인 필요).

**`SettlementRepository`**

| 메서드 | 의미 | 입력 → 반환 |
|---|---|---|
| native `int insertIfAbsent(UUID id, UUID shopId, LocalDate start, LocalDate end, int bp, Instant now)` | 그 가게·기간의 정산 행을 만든다. `uk_settlement_shop_period`와 충돌하면 `ON CONFLICT ON CONSTRAINT ... DO NOTHING` | → 1(내가 만듦) / 0(이미 있음 = 이 기간은 이미 정산됨) |
| `Optional<Settlement> findById(UUID id)` | 정산 읽기 | |
| `List<UUID> findIdsToPay(Collection<SettlementStatus> statuses, UUID afterId, Limit limit)` | 지급할 정산 ID를 `afterId` 다음부터 `limit`개 (`org.springframework.data.domain.Limit`) | → ID 목록 |

- native INSERT는 JPA를 거치지 않아 `@Version`·`@CreatedDate`가 동작하지 않는다. `NOT NULL`인 `version`(0)·`created_at`·`updated_at`·`status`를 INSERT에서 직접 채운다(`insertIfAbsent`의 `status`와 금액 초깃값은 문서에 정해져 있지 않다. 금액은 DEFAULT 0, `status`는 `CALCULATED`로 읽힌다 — 확인 필요).

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

**3) 적재 consumer** — 표 10·11번의 동작: `OrderEventConsumer`의 `@KafkaListener(topics = OrderTopics.ORDER_LINE_CONFIRMED, groupId = "settlement")` 메서드가 `parse(message, OrderLineConfirmedEvent.class)` → `inboxGuard.runOnce("settlement.order-line-confirmed", typed.envelope(), () -> itemService.register(typed.payload()))`.
- `register`: 이벤트 필드를 그대로 `insertIgnoringConflict`에 넘긴다 — `orderLineId`, `shopId`, `orderId`, `productId`, `amount`(`long`), `confirmedAt`(이미 `Instant`), 새 `id = Ids.newId()`.
- 중복 방어가 둘이다: `InboxGuard`(같은 이벤트 두 번 → 한 번)와 `uk_settlement_item_order_line`(다른 eventId로 같은 품목이 와도 한 번 — 최종 방어, INV-07). 완료 확인 `SettlementItemConsumerTest`가 앞쪽을 확인한다.

**4) 정산 실행** — 표 13번 `SettlementService`의 동작.

`public SettlementRunResult run(YearMonth month)` — `month`는 **정산 대상 월**이다(9월 구매확정분이면 `2026-09`). `SettlementJob`과 관리자 API가 부른다. `settledShops`는 이번 실행에서 정산을 새로 만든 가게 수이고, `paid`·`payoutFailed`는 `payoutAll`이 센 건수로 읽힌다(확인 필요: `payoutAll()`의 반환 타입은 문서에 없다)
1. `period = SettlementPeriod.of(month)`, `from = period.fromInclusive(zone)`, `to = period.toExclusive(zone)` (`zone = businessProperties.businessZone()`)
2. keyset 루프: `after`는 `00000000-0000-0000-0000-000000000000`에서 시작 → `shopIds = findShopIdsToSettle(from, to, after, 500)` → 비면 종료 → 각 가게 `try { tx.execute(s -> settleShop(shopId, period)) } catch (Exception e) { log }` → `after = 마지막 shopId`. 한 가게가 실패해도 `after`는 계속 전진시켜야 한다(안 그러면 같은 가게를 영원히 반복한다). 실패한 가게는 커밋된 것이 없어서 다음 실행(수동 실행 포함)이 다시 처리한다.
3. `payoutAll()` (3-4b)

`settleShop(shopId, period)` — 가게 하나의 정산을 만든다. **한 트랜잭션**(`tx.execute` 안에서 호출. 같은 클래스 안에서 부르므로 `@Transactional`이 아니라 `TransactionTemplate`이 경계다)
1. `id = Ids.newId()`, `insertIfAbsent(id, shopId, period.start(), period.end(), properties.feeRateBp(), now)` → 0이면 return(이미 이 기간 정산이 있음. 그 경우 `assign`을 하지 않으므로 이미 끝난 정산에 품목이 더 붙지 않는다)
2. `assign(id, shopId, from, to, now)` — 기간 내 미정산 품목에 `id`를 붙인다. 이 UPDATE가 끝나면 이 품목들은 다른 정산에 못 들어간다.
3. `sum = sumBySettlementId(id)` — 이번 정산 ID가 **붙은** 품목으로 합계를 낸다(조건으로 다시 찾지 않으므로 2와 3 사이에 새 품목이 들어와도 숫자가 어긋나지 않는다)
4. `settlement = findById(id)` → `settlement.calculate(Money.of(sum.grossAmount()), properties.feeRateBp(), sum.itemCount())` → fee·net 계산, CALCULATED. 저장은 커밋 때 더티 체킹이다. `calculate`의 첫 인자가 `Money`이고 `grossAmount`는 `long`이라 `Money.of(...)`로 감싼다(확인 필요: 규격이 `long`이었다면 감쌀 필요가 없다)

**테스트 헬퍼**: `SettlementServiceTest`는 정산 대상을 이벤트가 아니라 JDBC로 직접 적재한다(가게 1,200개 × 품목 2개). `settlement.settlement_item`에 `settlement_id = NULL`인 행을 넣는 헬퍼가 필요하고 위치·이름은 정해져 있지 않다(확인 필요).

### 할 일 (3-4b)

**구현 순서**: ① `PayoutGateway` → `MockPayoutGateway` → ② `settlement.api` 이벤트·`SettlementTopicConfig` → ③ `SettlementService.payoutAll` → ④ `SettlementErrorCode` → 목록 조회 메서드 → `SettlementController` → `SecurityConfig` → ⑤ `FakePayoutGateway` → 테스트.

**5) 지급**

| # | 클래스 | 규격 |
|---|---|---|
| 1 | `PayoutGateway` (settlement.application 인터페이스) | **판매자에게 정산금을 송금하는 외부 시스템(은행)의 자리**. 실제 은행 대신 Mock을 꽂을 수 있게 인터페이스로 둔다. `String payout(UUID settlementId, UUID shopId, long amount)` → 이체 참조 ID. 실패 시 예외. **같은 settlementId로 다시 부르면 같은 참조 ID를 돌려준다**(멱등. 두 번 호출돼도 돈이 한 번만 나가게). `SettlementService.payoutAll`이 부른다 |
| 2 | `MockPayoutGateway` (settlement.infrastructure) | `@Component @Profile("!prod")`. 은행 대신 메모리에서 흉내 낸다: `ConcurrentHashMap<UUID, String>`에 `computeIfAbsent(settlementId, id -> "PAYOUT-" + Ids.newId())`로 참조 ID를 만들고(같은 키면 기존 값을 돌려줘 멱등) 로그를 남긴다. 실제 은행 연동은 범위 밖(POL-12) |
| 3 | `settlement/package-info.java` 수정 | `allowedDependencies`에 `shop::api` 추가 — 아래 6)의 `shopApi.verifyOwner`가 쓴다 |
| 4 | `settlement/api/package-info.java` + `settlement.api.event` | `@NamedInterface("api")`(다른 모듈의 `api/package-info.java`와 같다. notification이 `settlement::api`로 import한다). `SettlementTopics.SETTLEMENT_PAID = "settlement.settlement-paid.v1"`, `SettlementPaidEvent(UUID settlementId, UUID shopId, long netAmount, LocalDate periodStart, LocalDate periodEnd)`(3-5 알림용) |
| 5 | `SettlementTopicConfig` (settlement.infrastructure) | `NewTopic` `SETTLEMENT_PAID`와 `.dlt`(파티션 3·복제 1. `ShopTopicConfig`와 같은 방식) |

`SettlementService.payoutAll()` — **지급 대기 정산을 모두 송금**한다. `run`의 3번에서 불린다. 정산을 `PAID`로 바꾸는 같은 트랜잭션에서 `outboxPublisher.publish(SETTLEMENT_PAID, aggregateType, settlementId, event)`로 발행한다(`aggregateType`은 문서에 없다. 다른 이벤트 관례대로 `"SETTLEMENT"`이면 된다 — 확인 필요). `findIdsToPay({CALCULATED, PAYOUT_FAILED}, after, Limit.of(500))` keyset 루프(`after`는 첫 페이지에 영(0) UUID, 이후 마지막 ID), 정산마다 try/catch(한 건의 실패가 나머지를 막지 않는다. `after`는 실패해도 전진)
1. 정산 조회(짧은 읽기): `settlementRepository.findById(id)`. `net == 0`이면 송금 없이 트랜잭션에서 `markPaid(null, now)` + `publish(SETTLEMENT_PAID)` 후 다음으로
2. **트랜잭션 밖**: `reference = payoutGateway.payout(settlementId, shopId, net)` — `net`은 `long`(`netAmount`의 `amount()`). 외부 호출을 트랜잭션 안에서 하지 않는 이유는 공통 규칙 K와 같다(송금이 느리면 DB 커넥션을 붙잡는다).
3. 성공 → 트랜잭션(`tx.executeWithoutResult`): **정산을 다시 `findById`로 읽어** `settlement.markPaid(reference, now)` + `publish(SETTLEMENT_PAID)`. 1번에서 읽은 엔티티는 트랜잭션 밖에서 읽은 것이라 여기서 수정해도 저장되지 않는다.
4. 실패(`payout`이 던진 예외) → 트랜잭션: 다시 읽어 `markPayoutFailed(e.getMessage())`(메시지는 `last_error` `varchar(500)`에 맞게 자른다), `payout_attempts + 1`
- 2와 3 사이에 죽으면 정산은 CALCULATED로 남는다 → 다음 실행에서 같은 settlementId로 다시 부르고, 게이트웨이가 같은 참조 ID를 돌려주므로 두 번 지급되지 않는다.
- 설정 확인 필요: `MockPayoutGateway`·`LogMailSender`(3-5)가 `@Profile("!prod")`라서 `prod` 프로필에서는 `PayoutGateway`·`MailSender` 빈이 없다. 문서는 실제 구현을 범위 밖으로 두었으므로 `prod`로 앱을 띄우면 이 빈들 때문에 시작이 실패할 수 있다.

**6) 조회 API**

| # | 대상 | 규격 |
|---|---|---|
| 1 | `SettlementErrorCode` | `SETTLEMENT_NOT_FOUND`(404, 공통 규칙 R). 정산 모듈의 `ErrorCode` enum이며 `MemberErrorCode`와 같은 모양이다(`code()`, `status()`, `message()`) |
| 2 | 목록 조회 메서드 | `SettlementRepository`: 가게의 정산 목록을 최신 기간 먼저 keyset 조회. `SettlementItemRepository`: 정산 ID가 붙은 품목 목록을 keyset 조회. `cursor`·`size`는 Part 1의 `CursorPage`/`Cursor` 방식을 따른다. 시그니처와 품목 읽기 모델(`SettlementItem`)은 이 문서에 정의가 없다(확인 필요) |
| 3 | `SettlementController` | 아래 API 표. 앞단은 조회 두 개 모두 `@CurrentMember UUID memberId`로 로그인 회원 ID를 받아 `shopApi.verifyOwner(shopId, memberId)` — 남의 가게면 403 `FORBIDDEN`. 상세는 이어서 `settlementRepository.findById(id)`(없으면 404 `SETTLEMENT_NOT_FOUND`)로 읽는다. 그 정산이 요청한 `shopId`의 것이 아닐 때의 응답은 문서에 없다(확인 필요: 남의 가게 정산이 id로 보이면 안 되므로 같은 404가 자연스럽다) |
| 4 | `SecurityConfig` | `.requestMatchers("/admin/**").hasRole("ADMIN")`를 `anyRequest().authenticated()` 앞에 더한다. 토큰의 역할이 `ROLE_ADMIN` 권한으로 바뀌는 코드는 이미 있다(`JwtAuthenticationFilter`). DB에서 role을 바꾼 뒤에는 **다시 로그인**해야 토큰에 `ADMIN`이 담긴다 |

| API | 권한 | 응답 |
|---|---|---|
| `GET /api/shops/{shopId}/settlements?cursor=&size=` | 가게 주인(`shopApi.verifyOwner`) | 200 `CursorPage<SettlementResponse(id, periodStart, periodEnd, grossAmount, feeAmount, netAmount, itemCount, status, paidAt)>` (최신 기간 먼저) |
| `GET /api/shops/{shopId}/settlements/{id}?cursor=&size=` | 가게 주인 | 200 `SettlementDetailResponse(settlement, items: CursorPage<SettlementItemResponse(orderLineId, orderId, productId, amount, confirmedAt)>)` |
| `POST /admin/api/settlements/run?period=yyyy-MM` | ADMIN | 200 `SettlementRunResult` |

**7) 테스트 헬퍼** — `support/FakePayoutGateway` (`FakeMailSender`(3-5)와 같은 방식)

| 대상 | 규격 |
|---|---|
| `FakePayoutGateway` | `@Component @Primary`(테스트에서 `MockPayoutGateway` 대신 주입되게). 호출 기록(`List`), `failNext(int times)`(다음 n번 호출이 예외), 멱등 동작은 Mock과 같게. `reset()`은 `IntegrationTestSupport`의 `@AfterEach`에서 부른다 |

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

**흐름 한눈에**
1. 다른 모듈의 이벤트(`order-paid` 등)가 Kafka로 오면 `NotificationEventConsumer`가 받아 `InboxGuard` 안에서 처리한다.
2. `NotificationComposer`가 이벤트를 `NotificationDraft`(수신자 memberId + 제목 + 본문) 목록으로 바꾼다.
3. `NotificationService.enqueue`가 draft마다 수신자 이메일을 조회해 `notification` 행(`PENDING`)으로 **저장만** 한다.
4. 10초마다 `NotificationDispatchJob` → `NotificationDispatcher.dispatchBatch()`가 `PENDING` 행을 `MailSender`로 보내고 `SENT`(실패하면 5분 뒤 재시도, 5번 실패하면 `FAILED`)로 바꾼다.

### 할 일

**구현 순서**: ① 마이그레이션(1) → ② `MemberApi.getContact`(2) → ③ 3)의 표를 1~11번 순서대로 → ④ `FakeMailSender`(4) → 테스트(`NotificationComposerTest` → `NotificationFlowTest`).

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

**2) 수신자 조회용 API 추가** — 알림 행에는 수신 주소(이메일)를 저장하므로, 이벤트에 없는 "수신자의 이메일"을 동기 호출로 알아낸다.
- `MemberApi.MemberContact getContact(UUID memberId)` → `record MemberContact(String email, boolean active)` (탈퇴 회원은 `active = false`). `MemberApiImpl`(member.application, 이미 있음)에 `@Transactional(readOnly = true)`로 더한다. 회원이 없으면 `MEMBER_NOT_FOUND`로 읽히지만 문서에 명시는 없다(확인 필요). `BANNED` 회원의 `active`도 문서에 정해져 있지 않다(확인 필요).
- `ShopApi.getOwnerId(shopId)` (3-2에서 추가됨): 가게 주인의 memberId. 가게가 없으면 `SHOP_NOT_FOUND`(BusinessException)이고, consumer 안이라 재시도 없이 DLT로 간다.

**3) notification 모듈** — 번호 순서대로 만든다.

| # | 클래스 | 역할 · 규격 |
|---|---|---|
| 1 | `notification/package-info.java` | `@ApplicationModule(allowedDependencies = {"common", "member::api", "shop::api", "order::api", "payment::api", "settlement::api"})`(공통 규칙 S). 현재 notification 모듈에는 코드가 없어 파일이 없다. consumer가 `OrderTopics`·`MemberTopics`·`ShopApi`·`SettlementTopics`를 import한다 |
| 2 | `NotificationType` | 알림 종류 enum(`type` 컬럼 값): `WELCOME, ORDER_PAID, SHOP_ORDER_RECEIVED, ORDER_CLOSED, SHIPPED, DELIVERED, REFUNDED, SETTLEMENT_PAID` (Part 5에서 구독 관련, 4-4에서 `BANNED` 추가) |
| 3 | `NotificationDraft` | "보낼 알림 초안" `record (UUID memberId, NotificationType type, String title, String body)`. 수신 주소는 아직 없다(저장할 때 조회) |
| 4 | `common.mail.MailSender` (인터페이스) | **메일을 보내는 자리**(포트). `void send(String to, String title, String body)` — 보내지 못하면 예외. 구현을 갈아 끼울 수 있다(로컬 로그, 테스트 가짜, 선택으로 SMTP). `NotificationDispatcher`(9)와 4-2의 인증 메일이 쓴다 |
| 5 | `common.mail.LogMailSender` | `@Component @Profile("!prod")`, 실제 발송 대신 `log.info`로 출력(본문은 앞 100자만) |
| 6 | `Notification` 엔티티·저장소 | 문서에 정해져 있지 않다(확인 필요). 필요한 연산은 3개다: `enqueue`의 `INSERT ... ON CONFLICT ON CONSTRAINT uk_notification_dedup_key DO NOTHING`, `dispatchBatch`의 PENDING 조회, 결과 기록 `UPDATE ... WHERE id = ?`. 구현 방식(native 쿼리, 엔티티)은 3-4의 정산 저장소와 같은 방식으로 정하면 된다 |
| 7 | `NotificationComposer` (`@Component`) | 이벤트 record → `List<NotificationDraft>`로 바꾸는 **순수 변환**(DB·다른 모듈 조회를 하지 않는다). 이벤트마다 오버로드 하나씩(예: `compose(MemberRegisteredEvent e)`, `compose(OrderPaidEvent e, Map<UUID, UUID> ownerByShopId)` — 인자는 권장안). 매핑은 아래 표. 단위 테스트 대상(`NotificationComposerTest`) |
| 8 | `NotificationService` (`@Service`) | **초안을 `notification` 행으로 저장**한다. `@Transactional void enqueue(UUID eventId, List<NotificationDraft> drafts)`. 동작은 아래 |
| 9 | `NotificationDispatcher` (application, `@Transactional` 없음) | **PENDING 알림을 실제로 보내는 곳**. `public int dispatchBatch()` → 이번에 SENT로 바꾼 건수. 동작은 아래 |
| 10 | `NotificationDispatchJob` | `@Scheduled(fixedDelay = 10_000)` → `jobRunner.run("dispatch-notifications", dispatcher::dispatchBatch)` |
| 11 | `NotificationEventConsumer` (infrastructure) | 토픽마다 `@KafkaListener(topics = ..., groupId = "notification")` 메서드 → 파싱 → `inboxGuard.runOnce("notification.{event}", ...)` → composer → service. 동작은 아래 |

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

`NotificationService.enqueue(eventId, drafts)` — consumer가 `InboxGuard` 트랜잭션 안에서 부르므로 그 트랜잭션에 합류한다.
1. draft마다 `MemberContact contact = memberApi.getContact(draft.memberId())`
2. `!contact.active()`(탈퇴 회원)면 그 draft는 건너뛴다
3. `INSERT INTO notification.notification (...) ... ON CONFLICT ON CONSTRAINT uk_notification_dedup_key DO NOTHING`: `id = Ids.newId()`, `recipient = contact.email()`, `dedup_key = eventId + ":" + memberId + ":" + type`, `status = 'PENDING'`, `next_attempt_at = now`, `version = 0`, `created_at`·`updated_at = now`(native INSERT라 JPA 감사가 동작하지 않는다)
- `dedup_key`가 `eventId:memberId:type`이라 한 주문에 한 판매자의 가게가 둘이면 `SHOP_ORDER_RECEIVED`가 같은 키가 되어 **판매자에게는 1건**만 간다. 같은 이벤트를 두 번 받아도 같은 키라 1건(완료 확인의 "같은 이벤트 두 번 → 알림 그대로").

`dispatchBatch` 순서 — 클래스에 `@Transactional`을 붙이지 않고 `TransactionTemplate`으로 구간을 나눈다(외부 호출인 메일 발송을 트랜잭션 밖에서 하려고, 공통 규칙 K)
1. `SELECT ... WHERE status = 'PENDING' AND next_attempt_at <= now ORDER BY next_attempt_at LIMIT 100` — 보낼 알림 최대 100건 읽기(잡 락으로 한 인스턴스만 실행)
2. 각 알림마다(한 건의 실패가 나머지를 막지 않게 건별 try/catch): **트랜잭션 밖에서** `mailSender.send(notification.recipient(), title, body)`
   - 성공: 짧은 트랜잭션으로 `status = 'SENT'`, `sent_at = now`
   - 실패(`send`가 예외): 짧은 트랜잭션으로 `attempts + 1`, 5 이상이면 `FAILED`, 아니면 `next_attempt_at = now + 5분`(`PENDING` 유지), `last_error`(`varchar(500)`에 맞게 자른다)
3. 발송 성공 건수 반환
- 알림이 읽은 시점의 엔티티를 트랜잭션 밖에서 바꾸면 저장되지 않으므로, 결과 기록은 `UPDATE ... WHERE id = ?`로 하거나 트랜잭션 안에서 다시 읽어 바꾼다.
- 재시도 횟수: `failNext(5)`면 다섯 번의 `dispatchBatch` 모두 실패해 `attempts = 5`에서 `FAILED`가 된다(완료 확인). 그 사이 실행은 `next_attempt_at`이 5분 뒤라 건너뛰므로, 테스트는 JDBC로 `next_attempt_at`을 당긴다.

`NotificationEventConsumer` — 토픽 7개마다 메서드 하나(`member-registered`, `order-paid`, `order-expired`, `shop-order-shipped`, `shop-order-delivered`, `order-line-refunded`, `settlement-paid`). 한 클래스에 `@KafkaListener` 메서드가 여럿이어도 `groupId = "notification"`이 같으면 한 그룹이다. 의존성 `EventEnvelopes`, `InboxGuard`, `ShopApi`, `NotificationComposer`, `NotificationService`. 메서드 하나의 모양(`order-paid` 예)
1. `typed = envelopes.parse(message, OrderPaidEvent.class)`
2. `inboxGuard.runOnce("notification.order-paid", typed.envelope(), () -> { ... })` 안에서
   1. 가게 주인 수신자가 필요하면(`order-paid`의 가게주문마다, `settlement-paid`) `shopApi.getOwnerId(shopId)`로 memberId를 구한다 — composer는 순수 함수로 유지하려고 조회를 consumer가 한다
   2. `drafts = composer.compose(...)`
   3. `notificationService.enqueue(typed.envelope().eventId(), drafts)`

**4) 테스트용 발송기** — `src/test/java/com/myroutine/support/FakeMailSender`: `@Component @Primary`(테스트에서 `LogMailSender` 대신 이 빈이 주입되게), `implements MailSender`. 보낸 목록 기록(`List<SentMail> sent()`, `SentMail`은 `send` 인자와 같은 `record (String to, String title, String body)`), `failNext(int times)`로 앞으로 `times`번의 `send`가 예외를 던지게 하고, `reset()`으로 목록과 남은 실패 횟수를 비운다(`IntegrationTestSupport`의 `@AfterEach`에서 호출). 여러 스레드(스케줄러·consumer)가 건드릴 수 있어 목록은 동시성 안전한 컬렉션(`CopyOnWriteArrayList` 등)으로 둔다

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

**구현 순서**: 아래 표를 번호 순서대로 만든다(member 도메인 1~3 → 인터페이스·이벤트 4~5 → order·settlement 구현체 6~10 → `WithdrawService`·컨트롤러 11~13 → order consumer 14~15) → 테스트(`MemberTest` → `WithdrawTest`).

| # | 대상 | 시그니처 · 규격 | 쓰이는 곳 |
|---|---|---|---|
| 1 | 마이그레이션 `V{...}__member_add_withdrawn_at.sql` | `member.member`에 `withdrawn_at timestamptz NULL` | 2 |
| 2 | `Member.withdrawnAt` 필드 + `Member.withdraw(Instant now)` | `@Column(name = "withdrawn_at") Instant withdrawnAt`. `withdraw`: `this.status = this.status.transitTo(MemberStatus.WITHDRAWN)`(`MemberStatus`가 ACTIVE → WITHDRAWN만 허용하므로 BANNED·이미 WITHDRAWN이면 `INVALID_STATE_TRANSITION`), 위 마스킹 값을 필드에 대입(`email`은 `"withdrawn+" + id + "@deleted.invalid"`, `nickname`은 `"탈퇴-" + id.toString().substring(0, 8)`, `name = "탈퇴회원"`, `phone`·`passwordHash = null`), `withdrawnAt = now`. 원래 이메일이 사라지므로 같은 이메일로 다시 가입해도 중복 검사에 걸리지 않는다(완료 확인) | 12 |
| 3 | `MemberErrorCode.MEMBER_HAS_ACTIVE_RESOURCES` | 422(공통 규칙 R) | 12 |
| 4 | `member.api.WithdrawalPrecondition` | `List<String> violations(UUID memberId)` — 위반 이유 코드 목록, 위반이 없으면 빈 목록. 탈퇴를 막는 조건을 각 모듈이 대신 확인해 주는 인터페이스다(3-2의 `ShopClosePrecondition`과 같은 의존 역전: member가 order·settlement의 데이터를 직접 볼 수 없다). 반환이 `List<String>`인 이유: `ShopClosePrecondition.check`는 첫 위반에서 예외를 던지지만, 탈퇴는 **모든 이유를 한 번에** 응답(`details.reasons`)에 담아야 해서 이유 코드를 돌려준다 | 7, 10, 12 |
| 5 | `MemberTopics.MEMBER_WITHDRAWN` + `MemberWithdrawnEvent` + `MemberTopicConfig` | `MEMBER_WITHDRAWN = "member.member-withdrawn.v1"`, `MemberWithdrawnEvent(UUID memberId)`. 3-3의 `MemberTopicConfig`에 `NewTopic`과 `.dlt`를 더한다 | 12, 15 |
| 6 | `OrderRepository`의 구매자 진행 중 조회 | 정책 표 `ACTIVE_ORDER`의 세 조건(결제 대기·진행 중 주문 `PENDING_PAYMENT`·`PAYMENT_IN_PROGRESS` / 구매자로서 종결되지 않은 가게주문 `PAID`·`SHIPPED`·`DELIVERED` / 진행 중 환불)을 `memberId`로 조회한다. 시그니처는 `OrderRepository`(domain), `@Query`는 `JpaOrderRepository`. 이름은 문서에 없다(확인 필요). 진행 중 환불의 상태 집합은 문서에 명시가 없어, 2-7의 `ship` 검사와 같은 집합(`REQUESTED`, `APPROVED`, `PG_CANCELLED`)을 쓰는 것으로 읽힌다(확인 필요) | 7 |
| 7 | `OrderWithdrawalPrecondition` (order.application, `@Component`, `implements WithdrawalPrecondition`) | 6의 조건 중 하나라도 있으면 `List.of("ACTIVE_ORDER")`, 없으면 `List.of()` | 12 |
| 8 | `settlement/package-info.java` | `allowedDependencies`에 `member::api` 추가(공통 규칙 S) | 10 |
| 9 | settlement 저장소의 탈퇴 조건 조회 | 가게 ID 목록에 대해 ① `settlement_item.settlement_id IS NULL`인 품목이 있는지 ② `PAID`가 아닌 정산이 있는지. 시그니처·이름은 문서에 없다(확인 필요). `SettlementItemRepository`·`SettlementRepository`(3-4)에 더한다 | 10 |
| 10 | `SettlementWithdrawalPrecondition` (settlement.application, `@Component`, `implements WithdrawalPrecondition`) | ① `shopApi.getShopIdsByOwner(memberId)`(CLOSED 포함) → 가게가 하나도 없으면 `List.of()` ② 9의 조회로 미정산 품목이나 `PAID`가 아닌 정산이 있으면 `List.of("PENDING_SETTLEMENT")` | 12 |
| 11 | `MemberRepository`의 잠금 조회 | `@Lock(PESSIMISTIC_WRITE)`를 단 `Optional<Member>` 조회(이름은 자유, `JpaMemberRepository`에 구현). 같은 회원의 탈퇴 요청이 겹쳐도 한 번에 하나씩 처리된다 | 12 |
| 12 | `WithdrawService` (member.application, `@Service`) | 탈퇴 유스케이스. 의존성 `MemberRepository`, `MemberAddressRepository`, `ShopApi`, `List<WithdrawalPrecondition> preconditions`, `OutboxPublisher`, `Clock`. `@Transactional void withdraw(UUID memberId)`. 순서는 표 아래 | 13 |
| 13 | `MemberController.withdraw` | `@DeleteMapping("/me")`, `@CurrentMember UUID memberId` → `withdrawService.withdraw(memberId)` → `ResponseEntity.noContent().build()` → `DELETE /api/members/me` 204 | 클라이언트 |
| 14 | order의 장바구니 삭제 | 그 회원의 장바구니와 항목(`cart_item`)을 삭제한다(`CartService`의 메서드 또는 별도 서비스, 이름은 예시로 `deleteByMember`). `fk_cart_item_cart` 때문에 항목을 먼저 지우고 장바구니를 지운다(현재 `CartItemRepository`에는 `deleteAll`이 있고 `CartRepository`에는 삭제 메서드가 없어 더해야 한다). 장바구니가 없으면 아무 일도 하지 않고 정상 종료한다(공통 규칙 Q) | 15 |
| 15 | `MemberEventConsumer` (order.infrastructure) | `@KafkaListener(topics = MemberTopics.MEMBER_WITHDRAWN, groupId = "order")` → `parse(message, MemberWithdrawnEvent.class)` → `inboxGuard.runOnce("order.member-withdrawn", typed.envelope(), () -> 14(memberId))`. order의 허용 의존에 `member::api`가 이미 있어 `MemberTopics`를 import할 수 있다 | `member-withdrawn` 소비 |

`WithdrawService.withdraw` 순서 (한 트랜잭션)
1. 회원을 잠금 조회(11)로 읽는다
2. `List<String> reasons = new ArrayList<>()` → `shopApi.countActiveShops(memberId) > 0`이면 `reasons.add("ACTIVE_SHOP")` → `preconditions.forEach(p -> reasons.addAll(p.violations(memberId)))`
3. 비어 있지 않으면 `throw new BusinessException(MemberErrorCode.MEMBER_HAS_ACTIVE_RESOURCES, Map.of("reasons", reasons))` → 422, 응답의 `details.reasons`에 담긴다(`BusinessException`의 두 번째 인자 `Map`은 `ShopErrorCode.SHOP_NOT_ACTIVE`가 `shopIds`를 담는 방식과 같다)
4. 배송지 전부 삭제(`memberAddressRepository.findAllByMemberId` → 각각 `delete`) → `member.withdraw(Instant.now(clock))` → `outboxPublisher.publish(MEMBER_WITHDRAWN, "MEMBER", memberId, new MemberWithdrawnEvent(memberId))`

- 구현체(7, 10)는 각자 **자기 모듈의 코드 안에서** member의 인터페이스를 구현한다. member는 구현체를 모르고 스프링이 주입해 준 목록만 돈다. 가게 조건(`ACTIVE_SHOP`)만 member가 `ShopApi`를 직접 부르는 이유는 순환 의존이다(공통 규칙 S).

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
