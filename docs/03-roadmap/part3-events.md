# Part 3. 이벤트

> 버전 0.22 · 2026-10-20 · **Part 2 구현에 맞춰 다듬었다**: Part 3이 가리키는 Part 1·2 클래스·메서드·`TestFixtures` 헬퍼를 코드와 대조해 모두 있음을 확인했다(없는 이름은 모두 Part 3이 새로 만드는 것). 공통 규칙 Q에 테스트 중 스케줄러를 끄려면 두 설정이 모두 필요하다는 근거(Part 2 규칙 M)를 이었고, 3-1의 `memberSnapshotOf`가 코드에 아직 없어 이 단계에서 추가한다는 점을 본문에도 맞췄다. 규격 외 변경 없음
> 0.21 · 2026-10-20 · **4.1 기준 점검에서 '하' 후보를 권장안대로 정리했다**(사용자 요청): 3-4a의 `netAmount` 공식 재서술, 3-4b의 정산 건수 재확인·`payoutFailed`, 3-4b 컨트롤러 5번의 정산 건수 0, 3-6 재가입의 `$.memberId` 비교·`reasons` 개수·성공 케이스의 `WITHDRAWN` 재확인. 규격 외 변경 없음
> 0.20 · 2026-10-20 · **4.1 기준 점검에서 '중' 후보를 뺐다**(사용자 요청): 3-1·3-3의 outbox `eventType`·`status`(3-1 `OutboxPublisherTest`가 확인), 3-2 `InboxGuardTest`·3-4a·3-5의 `processedCountOf`와 초기값(`channel`·`attempts`·`sentAt`), 3-3 결제 전 취소의 `$.status`·롤백 케이스의 이벤트 0건/`confirmedAt`, 3-4a의 `periodStart`·가게 수·`net_amount`, 3-4b의 `payoutAttempts`·`lastError`·0원 정산의 `gross`·`net`, 3-5의 재고·발송 실패 반환값. 규격 외 변경 없음
> 0.19 · 2026-10-20 · **4.1 기준 점검에서 확신 '상'을 뺐다**(사용자 요청): 3-1 `OutboxPublisherTest` 3번과 3-4a 소비 1번의 `id()` not null, 3-2 `InboxGuardTest` 1번의 `processed_at` not null(NOT NULL 컬럼), 3-4a 3번의 "예외 없이 끝난다"(반환값을 읽는다), 3-5 2번의 알림 행 총 5개와 5·6번의 개수 중복. 규격 외 변경 없음
> 0.18 · 2026-10-20 · **3-2 `CloseShopTest` 3번에서 준비가 만든 `product3`의 `DISCONTINUED` 확인을 뺐다**(사용자 요청). `product3`은 이미 단종된 상품이 있어도 폐업이 예외 없이 끝나는지 보는 준비로 남는다. 규격 외 변경 없음
> 0.17 · 2026-10-20 · **3-3·3-5·3-6 완료 확인에서 '중' 후보를 뺐다**(사용자 요청): 상품 등록 이벤트의 가격 변경·단종 0건, 발송·배송완료의 `shipped` 1건, 알림 흐름 4번의 `SENT`, `MemberTest`의 `role`, 탈퇴 전 배송지 수와 거부 케이스의 `before` 초기값. 규격 외 변경 없음
> 0.16 · 2026-10-18 · **읽기 헬퍼 정의를 단계 사이에서 맞췄다**: Part 1·2에서 정의한 `memberSnapshotOf`·`countOf`(3-1), `shopSnapshotOf`·`productSnapshotOf`(3-2), 가격 이력(3-3), 배송지·`cartSnapshotOf`(3-6)를 처음 쓰는 단계의 `TestFixtures` 추가 행으로 옮기고, 3-3의 `orderLineConfirmedAtOf`를 2-10의 `OrderSnapshot.LineState.confirmedAt`으로 합쳤으며, 3-5의 주문 `status` 확인을 `snapshotOf`로 바꿨다. 규격 변경 없음
> 0.15 · 2026-10-18 · **완료 확인에서 값을 읽는 방법을 읽기 헬퍼로 올리고 중복 확인을 걷어냈다**(사용자 지적: 확인할 값마다 JDBC 쿼리·`Map` 변환·캐스팅 코드를 테스트마다 짜야 해서 비효율): outbox 행은 `OutboxAssert.rows`·`outboxSnapshotOf`(`OutboxSnapshot`), Kafka 메시지는 `TestKafka.messages`(`ReceivedMessage`), 회원·가게·상품은 Part 1의 `memberSnapshotOf`·`shopSnapshotOf`·`productSnapshotOf`에 필드(`role`·`status`·`email`·`withdrawnAt`, `closedAt`, `version`·가격 이력 `changedAt`)를 더해, 정산은 `settlementSnapshotOf`·`settlementItemSnapshotOf`, 알림은 `notificationRowsOf`(`NotificationRow`)로 한 번 읽고 단언을 한 줄로 쓴다. 주문·결제는 2-8a의 `snapshotOf(orderId)`, 장바구니는 2-1의 `cartSnapshotOf`, 행 수는 1-3의 `countOf`를 쓴다. `processed_message`는 `processedCountOf`, 역할 변경은 `changeRole`, 알림 재시도 시각 당기기는 `setNotificationsDue`로 올렸다. 각 단계 "이 단계에서 추가하는 헬퍼" 표에 읽는 테이블·컬럼과 반환 필드를 적었다. 같은 클래스에서 값이 목적이 아닌 케이스가 되풀이하던 확인(3-2 `CloseShopTest` 3·7번의 역할·단종 결과, 3-5 4번의 FAILED 재확인)과 어느 케이스도 쓰지 않던 읽기(3-3 `orders.orders`의 `status`·`failure_reason`)를 지웠다. 부정 확인과 구체 수치는 그대로이고 규격·동작 변경 없음
> 0.14 · 2026-10-18 · **3-1~3-6의 완료 확인을 케이스마다 준비·동작·확인할 값으로 다시 썼다**(사용자 지적: 무엇을 확인하는지 모호하다): 값을 읽는 방법(outbox 행과 payload 필드, Kafka 메시지, `processed_message`, 알림 행, 응답 jsonPath), 공통 준비 수치, 케이스별 응답 코드·DB 컬럼·토픽·키·payload 값, 중복 전달·재처리가 남겨야 하는 정확한 건수, 부정 확인(0건·그대로)을 적었다. 3-4는 3-4a·3-4b로 나눴고, 센티널 메시지로 중복 소비를 확인하는 방법과 테스트 헬퍼(`OutboxAssert.clear`·`publishReady`, `TestKafka`, `TestKafkaTopics`, `insertSettlementItem(s)`, `FakePayoutGateway` 호출 기록)를 정했다. 규격에 없던 값은 "확인 필요"로 남겼다. 규격 외 변경 없음. 케이스를 더한 곳: 3-1 `MemberRoleServiceTest`·`OutboxPublisherTest` 4번·`OutboxRelayTest` 3번 일부, 3-2 `InboxGuardTest` 2·4·5번·`CloseShopTest` 2·4번과 `revokeSellerIfNoActiveShop`, 3-3 롤백(발행 실패) 등, 3-4 늦게 온 품목·0원 정산·한 가게 실패·컨트롤러 조회/관리자 성공, 3-5 같은 판매자 가게 둘·탈퇴 회원, 3-6 환불 경계·동시 탈퇴 등
> 0.13 · 2026-10-16 · **쓰지 않는 값과 불필요한 단계를 걷어냈다**(리뷰 지적): 3-1 `relayOnce`의 회수와 선점을 한 트랜잭션으로 묶었고, 3-3 구매확정 이벤트가 2-10 3번에서 이미 찾은 품목을 넘겨 쓰게 했으며, 3-4 `insertIgnoringConflict`·`assign`의 쓰지 않는 반환값과 3-5 `MemberContact`의 `memberId`·`nickname`을 지웠다. 그 밖의 규격·동작은 0.12와 같다
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
- Kafka는 Testcontainers로 띄운다(3-1). 스케줄러가 꺼져 있으므로 **릴레이는 테스트에서 직접 호출**한다(`outboxRelay.relayOnce()`). 스케줄러를 끄는 데는 `application-test.yaml`의 두 설정이 모두 필요하다([Part 2 공통 규칙 M](part2-order-money.md#m-시간-흉내-내기)). 하나라도 빠지면 1초마다 도는 `OutboxRelayJob`이 테스트 중에 행을 먼저 발행해 `relayOnce()`의 반환값과 outbox 상태 확인이 어긋난다. consumer(`@KafkaListener`)는 테스트 중에도 동작한다.
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

**구현 순서**: ① 인프라(compose·의존성·`application.yaml`·`IntegrationTestSupport`) → ② outbox 마이그레이션 → ③ `EventEnvelope` → `OutboxPublisher` → `OutboxRelay` → `OutboxRelayJob` → `EventEnvelopes` → `EventContext` → ④ shop: `ShopTopics`·`ShopOpenedEvent` → `ShopTopicConfig` → `OpenShopService` 수정 → ⑤ member: `Member.promoteToSeller`·`demoteToUser` → `MemberRoleService` → `member/package-info.java` → `ShopEventConsumer` → ⑥ 테스트 헬퍼(`Eventually`, `OutboxSnapshot`, `OutboxAssert`) → 테스트.

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
- `support/OutboxSnapshot`: outbox 행 하나를 컬럼 이름 그대로의 타입 있는 필드로 담는 record다. 필드는 아래 "이 단계에서 추가하는 헬퍼" 표에 있다.
- `support/OutboxAssert`: `List<OutboxSnapshot> rows(String topic)` — `JdbcTemplate`으로 `common.outbox_event`에서 `topic = ?`인 행을 `created_at` 순으로 읽어 `OutboxSnapshot`으로 바꾼다. `payload`(jsonb)는 `PGobject`로 오므로 `toString()`으로 JSON 문자열을 얻어 `ObjectMapper.readTree`로 `JsonNode`를 만든다. 테스트는 `rows.get(0).status()`, `rows.get(0).payload().get("shopId").asText()`처럼 읽는다. 나머지 메서드(`clear`, `publishReady`)는 아래 표에 있다.

### 완료 확인

**확인 값을 읽는 방법** (3-2 이후도 같다)
- 응답: `jsonPath`. 에러 응답의 코드는 `$.code`. 응답 헤더는 `result.getResponse().getHeader("X-Request-Id")`.
- outbox: 토픽의 행은 `OutboxAssert.rows(topic)`(위 5)) → `List<OutboxSnapshot>`(생성 순). id를 아는 행 하나는 `testFixtures.outboxSnapshotOf(outboxId)`. 한 행의 필드는 `id()`, `aggregateType()`, `aggregateId()`, `eventType()`, `topic()`, `status()`, `attempts()`, `traceId()`, `lastError()`, `nextAttemptAt()`, `lockedUntil()`, `sentAt()`, `payload()`(`JsonNode`: `payload().get("shopId").asText()`)다. 전체 행 수는 `testFixtures.countOf("common.outbox_event")`.
- 회원: `testFixtures.memberSnapshotOf(memberId)`의 `role()`. 헬퍼는 1-3 표에 정의돼 있고 코드에는 아직 없어, 이 단계에서 `TestFixtures`에 추가하며 `role()`을 더한다(아래 "테스트 준비" 표).
- Kafka 메시지: `TestKafka.messages(topic, key)` → `List<ReceivedMessage>`. 토픽에서 `key`(집계 ID 문자열)가 같은 레코드만 모은 것이다. 브로커는 테스트 JVM 전체에서 하나라 이전 테스트가 남긴 메시지가 섞여 있어서 키로 거른다. 한 건은 `key()`, `partition()`, `value()`(원문 문자열), `envelope()`(`EventEnvelope`. `value()`가 봉투 JSON이 아니면 `null`)이고, 봉투는 `envelope().eventId()`, `aggregateId()`, `eventType()`, `traceId()`, `payload().get("shopId").asText()`로 읽는다.
- consumer가 처리한 결과: `Eventually.await(Duration.ofSeconds(5), () -> assertThat(...))` 안에서 읽는다.

**공통 준비**: 판매자 `seller@test.com`을 `signup`(역할 `USER`), 가게는 `openShop(sellerId, "1111111111")`. `signup`·`openShop` 같은 준비 호출도 outbox 행을 만든다(`openShop`은 이 단계부터 `shop.shop-opened.v1` 1건). `relayOnce()`는 READY인 행을 전부 발행하므로 준비가 만든 행이 섞이면 반환값과 consumer 결과가 달라진다. 개수를 세거나 `relayOnce()`를 부르는 케이스는 준비를 마친 직후 `OutboxAssert.clear()`로 비우고 시작한다.

#### `common/outbox/OutboxPublisherTest`

공통 준비: `OutboxPublisher`, `TransactionTemplate tx`, `JdbcTemplate` 주입. 호출은 `outboxPublisher.publish("shop.shop-opened.v1", "SHOP", shopId, new ShopOpenedEvent(shopId, memberId))`이고 `shopId`·`memberId`는 `Ids.newId()`로 만든 임의 UUID다(회원·가게 행은 필요 없다).

**1. 트랜잭션 없이 publish** — 동작: `tx` 없이 위 호출을 그대로 한다.
- `IllegalTransactionStateException`이 던져진다(`assertThatThrownBy`).
- `countOf("common.outbox_event")` = 0.

**2. 롤백되면 행이 없다** — 동작: `tx.execute(s -> { publish 호출; s.setRollbackOnly(); return null; })`.
- `OutboxAssert.rows("shop.shop-opened.v1")`가 비어 있다(0건).

**3. 커밋되면 행이 한 개, trace_id는 MDC 값** — 준비: `MDC.put("traceId", "trace-test-1")`(키는 `TraceIds.MDC_KEY`). 동작: `tx.executeWithoutResult(s -> publish 호출)` → `finally`에서 `MDC.remove("traceId")`.
- `OutboxAssert.rows("shop.shop-opened.v1")`가 정확히 1건이다.
- 그 행: `traceId()` = `trace-test-1`, `status()` = `READY`, `aggregateType()` = `SHOP`, `aggregateId()` = shopId, `eventType()` = `ShopOpenedEvent`, `topic()` = `shop.shop-opened.v1`, `attempts()` = 0.
- payload: `shopId` = shopId 문자열, `memberId` = memberId 문자열.

**4. MDC가 없으면 trace_id는 null** — 동작: MDC를 넣지 않고 `tx.executeWithoutResult(s -> publish 호출)`.
- 그 행의 `traceId()`가 `null`이다.

#### `common/outbox/OutboxRelayTest`

공통 준비: 테스트 전용 토픽 `test.outbox-relay.v1`(`TestKafkaTopics`가 만든다)에 READY 행을 `UUID id = OutboxAssert.publishReady("test.outbox-relay.v1", aggregateId)`로 만든다(행의 `id`를 돌려준다). 행은 `testFixtures.outboxSnapshotOf(id)`로 읽고, Kafka 메시지는 `TestKafka.messages("test.outbox-relay.v1", aggregateId.toString())`로 읽는다. 각 케이스는 `OutboxAssert.clear()`로 시작한다.

**1. READY 3건 발행** — 준비: `a1`, `a2`, `a3`(임의 UUID)로 3행. 동작: `int sent = outboxRelay.relayOnce()`.
- `sent` = 3.
- 3행 모두 `status()` = `SENT`, `sentAt()` not null, `lockedUntil()` null, `attempts()` = 0.
- `messages(토픽, a1)`·`messages(토픽, a2)`·`messages(토픽, a3)`(키는 UUID 문자열)이 각각 정확히 1개.
- 각 메시지의 봉투: `eventId()` = 그 행의 `id()`, `aggregateId()` = 키, `eventType()` = `TestEvent`, `traceId()` = 행의 `traceId()`(null).
- 한 번 더 `relayOnce()` = 0이고, 위 키의 메시지 수는 그대로 1개씩이다(SENT 행은 다시 발행하지 않는다).

**2. Kafka가 멈췄다 돌아온 경우** — 준비: READY 1행(`a1`). 동작 1: `kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec()` → `relayOnce()`(브로커 응답을 기다리느라 몇 초 걸린다).
- 반환값 0.
- 그 행: `status()` = `READY`, `attempts()` = 1, `lastError()` not null(길이 500 이하), `lockedUntil()` null.
- `nextAttemptAt()`이 `Instant.now()`보다 뒤다(10초 뒤로 미뤄졌다).
- 동작 2(`finally`에서도 반드시): `unpauseContainerCmd(...)` → JDBC `UPDATE common.outbox_event SET next_attempt_at = now() - interval '1 second' WHERE id = ?` → `relayOnce()`.
- 반환값 1, 그 행 `status()` = `SENT`, `attempts()` = 1(성공해도 지우지 않는다).
- `messages(토픽, a1)`은 1개 또는 2개이고 모두 `envelope().eventId()`가 그 행의 `id()`다(멈춘 동안 보낸 첫 시도가 브로커가 돌아온 뒤 늦게 도착할 수 있다. 그래서 consumer 멱등이 필요하다).

**3. 리스가 끝난 PROCESSING 행 회수, 끝나지 않은 행은 그대로** — 준비: READY 2행(`a1`, `a2`)을 만든 뒤 JDBC로 `UPDATE common.outbox_event SET status = 'PROCESSING', locked_until = now() - interval '1 minute' WHERE aggregate_id = a1`, `... locked_until = now() + interval '1 minute' WHERE aggregate_id = a2`. 동작: `relayOnce()`.
- 반환값 1.
- `a1` 행: `status()` = `SENT`, `messages(토픽, a1)` 1개.
- `a2` 행: `status()` = `PROCESSING`, `lockedUntil()`이 `Instant.now()`보다 뒤(그대로 미래), `messages(토픽, a2)` 0개(남의 리스를 가져가지 않는다).

**4. 10번째 실패는 DEAD** — 준비: READY 1행(`a1`) → JDBC `UPDATE common.outbox_event SET attempts = 9 WHERE aggregate_id = a1`. 동작: Kafka를 멈추고(2번과 같은 방법) `relayOnce()` → `finally`에서 unpause.
- 반환값 0.
- 그 행: `status()` = `DEAD`, `attempts()` = 10, `lastError()` not null, `lockedUntil()` null.
- unpause 뒤 `relayOnce()` = 0이고 그 행은 `DEAD` 그대로(다시 집지 않는다).

#### `member/application/MemberRoleServiceTest`

회원 역할 변환을 consumer 없이 서비스로 직접 확인한다. 준비: 회원 `signup`, 역할은 `changeRole(memberId, role)`로 맞춘다. 동작은 `memberRoleService.grantSeller(memberId)`. 역할은 `memberSnapshotOf(memberId).role()`로 읽는다.

| 준비한 역할 | 확인할 것 |
|---|---|
| `USER` | 호출 뒤 `role()` = `SELLER` |
| `SELLER` | 예외 없이 끝나고 `role()` = `SELLER` 그대로 |
| `ADMIN` | 예외 없이 끝나고 `role()` = `ADMIN` 그대로 |
| 없는 회원(`Ids.newId()`) | 예외 없이 끝난다(경고 로그만 남는다) |

#### `shop/application/ShopOpenedFlowTest`

**1. 가게 개설이 SELLER 역할로 이어진다** — 동작 1: `OutboxAssert.clear()` → `shopId = openShop(sellerId, "1111111111")`.
- `OutboxAssert.rows("shop.shop-opened.v1")`가 정확히 1건(`row`): `aggregateType()` = `SHOP`, `aggregateId()` = shopId, payload `shopId` = shopId 문자열, `memberId` = sellerId 문자열.
- 이 시점 `memberSnapshotOf(sellerId).role()` = `USER`(릴레이 전에는 바뀌지 않는다).
- 동작 2: `outboxRelay.relayOnce()`.
- 반환값 1, `outboxSnapshotOf(row.id()).status()` = `SENT`.
- `TestKafka.messages("shop.shop-opened.v1", shopId.toString())` 1개, `envelope().eventId()` = `row.id()`.
- `Eventually`(5초) 안에서 `memberSnapshotOf(sellerId).role()` = `SELLER`.

**2. 사업자번호 중복이면 이벤트도 없다** — 준비: 판매자 A가 `openShop(sellerId, "1111111111")` 성공 → 판매자 B를 `token("seller2@test.com")`으로 가입 → `OutboxAssert.clear()`. 동작: B의 토큰으로 `POST /api/shops`(`Authorization: Bearer {B 토큰}`, `Idempotency-Key`: 새 UUID, 본문 `{"name":"shop2","businessNumber":"1111111111","email":"shop2@test.com","phone":"010-1234-5678","address":"address"}`).
- 응답 409, `$.code` = `SHOP_BUSINESS_NUMBER_DUPLICATED`.
- `countOf("common.outbox_event")` = 0.
- `countOf("shop.shop")` = 1.

**3. 요청의 traceId가 outbox와 봉투까지 이어진다** — 준비: `token("seller2@test.com")`으로 가입·토큰, `OutboxAssert.clear()`. 동작: 위 2번과 같은 `POST /api/shops`(사업자번호는 `"2222222222"`) → 201. 응답 헤더 `X-Request-Id` 값을 `traceId`로 읽고 응답의 `$.shopId`를 `shopId`로 읽는다.
- `OutboxAssert.rows("shop.shop-opened.v1")`의 `aggregateId()` = shopId인 행의 `traceId()` = `traceId`.
- 동작 2: `relayOnce()`. `TestKafka.messages("shop.shop-opened.v1", shopId.toString())`의 봉투 `traceId()` = `traceId`.

**테스트 준비**

**이 단계에서 추가하는 헬퍼** (`support/`. 이름·인자는 권장안이라 쓰다 바꿔도 된다)

| 대상 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `OutboxSnapshot` | `support/`의 record. 필드: `id`·`aggregateId`(UUID), `aggregateType`·`eventType`·`topic`·`status`·`traceId`·`lastError`(String, `traceId`·`lastError`는 없으면 null), `attempts`(int), `nextAttemptAt`·`lockedUntil`·`sentAt`(`Instant`, `lockedUntil`·`sentAt`은 없으면 null), `payload`(`JsonNode`) | 아래 세 행과 모든 outbox 확인 |
| `OutboxAssert.rows(String topic)` → `List<OutboxSnapshot>` | JDBC `SELECT id, aggregate_type, aggregate_id, event_type, topic, status, trace_id, last_error, attempts, next_attempt_at, locked_until, sent_at, payload FROM common.outbox_event WHERE topic = ? ORDER BY created_at, id`. `payload`는 `PGobject`의 `toString()`을 `objectMapper.readTree`로 푼다. 시각 컬럼은 `OffsetDateTime`으로 읽어 `toInstant()` | 3-1 이후 모든 outbox 확인 |
| `outboxSnapshotOf(UUID outboxId)` → `OutboxSnapshot` | 위와 같은 컬럼·변환으로 `WHERE id = ?` 한 행. 행이 없으면 `null` | `OutboxRelayTest`, 행 id를 아는 확인 |
| `OutboxAssert.clear()` | JDBC `DELETE FROM common.outbox_event` | 개수를 세거나 `relayOnce()`를 부르는 모든 케이스의 준비 끝 |
| `OutboxAssert.publishReady(String topic, UUID aggregateId)` → `UUID` | `tx.executeWithoutResult(s -> outboxPublisher.publish(topic, "TEST", aggregateId, new TestEvent("x")))` 뒤 `SELECT id FROM common.outbox_event WHERE aggregate_id = ?`로 읽은 행 id를 돌려준다 | `OutboxRelayTest` |
| `TestEvent` | `public record TestEvent(String value)` | 위 |
| `TestKafka` (`@Component`) | 의존성 `ObjectMapper`, `@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers`. `List<ConsumerRecord<String, String>> readAll(String topic)`: 새 `group.id`(`UUID`), `auto.offset.reset=earliest`, `StringDeserializer`로 `KafkaConsumer`를 만들어 토픽의 모든 파티션을 구독하고, 새 레코드가 없는 `poll(Duration.ofSeconds(1))`이 연속 2번 나올 때까지 모아 반환한 뒤 닫는다. `List<ReceivedMessage> messages(String topic, String key)`: `readAll(topic)`에서 `record.key()`가 `key`인 레코드만 골라 `ReceivedMessage`로 바꾼다 | 3-1 이후 모든 Kafka 메시지 확인 |
| `ReceivedMessage` | `support/`의 record `(String key, int partition, String value, EventEnvelope envelope)`. `envelope`은 `objectMapper.readValue(value, EventEnvelope.class)`의 결과이고, 읽기에 실패하면(봉투가 아닌 문자열) `null` | `TestKafka.messages` |
| `memberSnapshotOf(memberId)` → `MemberSnapshot` | 1-3 표에 정의된 헬퍼다(읽는 컬럼·필드는 그 표). 처음 쓰는 미구현 단계인 이 단계에서 `TestFixtures`에 추가하고 `role`(String), `status`(String), `email`(String), `withdrawnAt`(`Instant`, 없으면 null)을 더한다. 읽는 컬럼 `member.member.role`, `status`, `email`, `withdrawn_at`. 배송지(`addresses`)는 3-6이 더한다 | 3-1 `MemberRoleServiceTest`·`ShopOpenedFlowTest`, 3-2 `CloseShopTest`, 3-6 `WithdrawTest`, Part 4 |
| `countOf(table)` → `long` | 1-3 표에 정의된 헬퍼다. 이 단계에서 `TestFixtures`에 추가한다 | 3-1 이후 행 수 확인 |
| `changeRole(UUID memberId, String role)` | JDBC `UPDATE member.member SET role = ? WHERE id = ?` | 3-1 `MemberRoleServiceTest`, 3-2 `CloseShopTest` 준비, 3-6 |
| `TestKafkaTopics` (`@Configuration`) | `NewTopic` 빈 3개: `test.outbox-relay.v1`, `test.always-fail.v1`, `test.always-fail.v1.dlt`(파티션 3·복제 1) | `OutboxRelayTest`, 3-2 `KafkaDltTest` |
| `IntegrationTestSupport.kafka` | `static final`을 `protected static final`로 바꾼다(다른 패키지의 `OutboxRelayTest`가 `kafka.getDockerClient()`를 쓴다) | `OutboxRelayTest` |

이미 있는 헬퍼(Part 1·2에서 만든 것, 그대로 쓴다): `signup`, `token`, `login`, `openShop`. 이 단계에서 만드는 `Eventually`·`OutboxSnapshot`·`OutboxAssert.rows`는 위 5)에 있다.

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

읽는 방법은 3-1과 같다. 이 단계에서 더하는 것은 다음과 같다.
- `processed_message`: `processedCountOf(consumer, eventId)` → `int`(그 consumer가 그 이벤트를 처리한 기록 수. 0 또는 1).
- 가게·상품: `shopSnapshotOf(shopId)`의 `status()`·`closedAt()`, `productSnapshotOf(productId)`의 `status()`.
- 가게 폐업 이벤트의 `eventId`: `OutboxAssert.rows("shop.shop-closed.v1")`의 `id()`(= `processed_message.event_id`). consumer가 그 이벤트를 처리했는지는 `processedCountOf`로 안다.
- 테스트가 Kafka에 직접 보내는 메시지: `TestKafka.send(topic, key, eventId, event)` → 보낸 레코드의 파티션 번호(`int`). 봉투를 `new EventEnvelope(eventId, event.getClass().getSimpleName(), Instant.now(), UUID.fromString(key), null, objectMapper.valueToTree(event))`로 만들어 `kafkaTemplate.send(topic, key, json).get(5, TimeUnit.SECONDS)`로 보낸다. 봉투 없이 문자열을 그대로 보낼 때는 `TestKafka.sendRaw(topic, key, value)`.
- 센티널: "중복으로 보낸 메시지가 처리되지 않았다"는 기다려도 알 수 없다. 같은 키로 다른 `eventId`의 메시지를 한 건 더 보내고, 그 메시지의 처리 신호가 나오면 앞의 중복 메시지는 이미 지나간 것이다(같은 키는 같은 파티션이라 보낸 순서대로 처리된다).

#### `common/inbox/InboxGuardTest`

준비: `InboxGuard`, `JdbcTemplate`, `ObjectMapper` 주입. 봉투는 `new EventEnvelope(eventId, "TestEvent", Instant.now(), aggregateId, "trace-test-1", objectMapper.createObjectNode())`(`eventId`·`aggregateId`는 `Ids.newId()`). action은 `AtomicInteger counter`를 올리는 람다다. consumer 이름은 `test.consumer`.

**1. 같은 eventId로 두 번** — 동작: `inboxGuard.runOnce("test.consumer", envelope, () -> counter.incrementAndGet())`을 두 번.
- 두 번째 호출도 예외 없이 끝난다.
- `counter` = 1.
- `processedCountOf("test.consumer", eventId)` = 1.

**2. consumer 이름이 다르면 각자 한 번** — 동작: 1번 뒤 같은 봉투를 consumer 이름 `test.consumer2`로 `runOnce`.
- `counter` = 2.
- `processedCountOf("test.consumer2", eventId)` = 1(consumer 이름이 다르면 같은 eventId가 각각 기록된다).
- 다른 eventId의 봉투를 `test.consumer`로 `runOnce`하면 `counter` = 3.

**3. action이 예외를 던지면 기록도 롤백** — 동작: action이 `throw new IllegalStateException("fail")`인 `runOnce` → 이어서 같은 봉투로 `counter`를 올리는 action의 `runOnce`.
- 첫 호출은 `IllegalStateException`이 그대로 던져진다.
- 첫 호출 직후 `processedCountOf("test.consumer", eventId)` = 0.
- 둘째 호출 뒤 `counter` = 1, `processedCountOf("test.consumer", eventId)` = 1(재시도 때 처리된다).

**4. 같은 eventId 동시 2건** — 동작: `ConcurrencyRunner.run(2, () -> { inboxGuard.runOnce("test.consumer", envelope, () -> { counter.incrementAndGet(); Thread.sleep(300); }); return null; })`(action 안에서 300ms `Thread.sleep`으로 잠시 붙잡아 두 스레드가 겹치게 한다. `InterruptedException`은 `try/catch`로 감싼다).
- 두 스레드 모두 `Result.ok()`(예외 없음).
- `counter` = 1.
- `processedCountOf("test.consumer", eventId)` = 1.

**5. 처리 중 MDC** — 동작: action 안에서 `MDC.get("eventId")`, `MDC.get("traceId")`를 변수에 담는다. 호출이 끝난 뒤 같은 값을 다시 읽는다.
- action 안: `eventId` = eventId 문자열, `traceId` = `trace-test-1`.
- 호출이 끝난 뒤: 둘 다 `null`(다음 메시지에 값이 남지 않는다).

#### `common/config/KafkaDltTest`

준비(테스트 전용): `src/test`의 `@Component AlwaysFailListener` — `@KafkaListener(topics = "test.always-fail.v1", groupId = "test-always-fail")` 메서드가 `ConsumerRecord<String, String>`을 받아 ① `record.key()`별 호출 횟수를 `ConcurrentHashMap`에 올리고(`attempts(String key)`로 읽는다) ② `objectMapper.readTree(record.value())`를 부르고(깨진 JSON이면 `JacksonException`) ③ 성공하면 `throw new IllegalStateException("always fail")`. 토픽은 `TestKafkaTopics`가 파티션 3으로 만든다(`.dlt`도 3). 각 케이스는 새 UUID를 키로 쓴다. DLT 확인은 `TestKafka.messages("test.always-fail.v1.dlt", key)`로 읽는다.

**1. 일시적 오류는 4번 시도 후 DLT** — 동작: `int partition = TestKafka.send("test.always-fail.v1", key, eventId, new TestEvent("x"))`.
- `Eventually`(20초; 재시도 대기 1+2+4초) 안에서 `messages("test.always-fail.v1.dlt", key)`가 정확히 1개.
- 그 메시지의 `value()` = `messages("test.always-fail.v1", key).get(0).value()`(같은 값 그대로 옮긴다), `partition()` = 위 `partition`(원본과 같은 파티션).
- DLT 메시지가 보인 시점에 `attempts(key)` = 4(최초 1번 + 재시도 3번).

**2. 깨진 JSON은 재시도 없이 바로 DLT** — 동작: `TestKafka.sendRaw("test.always-fail.v1", key, "{not-json")`.
- `Eventually`(3초; 재시도 대기 7초보다 짧아야 한다) 안에서 `messages("test.always-fail.v1.dlt", key)`가 정확히 1개, `value()` = `{not-json`.
- `attempts(key)` = 1.

#### `member/application/MemberRoleServiceTest`에 추가 (3-1의 클래스에 더한다)

동작은 `memberRoleService.revokeSellerIfNoActiveShop(memberId)`. 가게는 `openShop`, 폐업은 `closeShop(shopId)`(JDBC로 CLOSED). 역할은 `changeRole`로 맞추고 `memberSnapshotOf(memberId).role()`로 읽는다.

| 준비 | 확인할 것 |
|---|---|
| 역할 `SELLER`, ACTIVE 가게 0개(가게 1개를 `closeShop`) | `role()` = `USER` |
| 역할 `SELLER`, ACTIVE 가게 1개(가게 2개 중 1개만 `closeShop`) | `role()` = `SELLER` 그대로 |
| 역할 `ADMIN`, ACTIVE 가게 0개 | `role()` = `ADMIN` 그대로 |
| 없는 회원(`Ids.newId()`) | 예외 없이 끝난다 |

#### `shop/web/CloseShopTest`

공통 준비: 판매자 `seller@test.com`(`signup`, 토큰은 `login`), 가게 A `openShop(sellerId, "1111111111")`, 상품 `product1`(`registerProduct(sellerId, shopA, 1000, 10)`)·`product2`(2000)·`product3`(3000), 구매자 `buyer@test.com`. 판매자 역할은 `changeRole(sellerId, "SELLER")`로 맞춘다(개설 이벤트를 릴레이하지 않는다. 나가면 폐업 이벤트와 순서가 섞인다). 준비 끝에 `OutboxAssert.clear()`. 폐업 요청은 `DELETE /api/shops/{shopId}`, `Authorization: Bearer {판매자 토큰}`(`Idempotency-Key` 없음).

**1. 진행 중 가게주문이 있으면 거부** — 준비: `createPaidOrder(buyerId, product1Id)`(가게주문 `PAID`). 동작: 가게 A 폐업 요청.
- 응답 422, `$.code` = `SHOP_HAS_ACTIVE_ORDERS`.
- `shopSnapshotOf(shopA)`: `status()` = `ACTIVE`, `closedAt()` null.
- `productSnapshotOf(product1Id).status()` = `ON_SALE` 그대로.
- `countOf("common.outbox_event")` = 0(`shop.shop-closed.v1` 행이 없다).

**2. 가게주문 상태별 경계** — 준비: 1번의 주문을 만든 뒤 JDBC `UPDATE orders.shop_order SET status = ? WHERE id = ?`(shopOrderId는 `shopOrderIdOf`)로 상태를 맞춘다. 동작: 가게 A 폐업 요청.

| 가게주문 상태 | 응답 | 확인할 것 |
|---|---|---|
| `PENDING`, `PAID`, `SHIPPED`, `DELIVERED` | 422 `SHOP_HAS_ACTIVE_ORDERS` | `shopSnapshotOf(shopA).status()` = `ACTIVE` |
| `COMPLETED`, `CANCELLED` | 204 | `shopSnapshotOf(shopA).status()` = `CLOSED` |

**3. 폐업 성공: 상태, 이벤트, 상품 단종** — 준비: `hideProduct(product2Id)`(`HIDDEN`), `discontinueProduct(product3Id)`(`DISCONTINUED`), 가게주문 없음. 동작 1: 가게 A 폐업 요청.
- 응답 204, 본문 없음.
- `shopSnapshotOf(shopA)`: `status()` = `CLOSED`, `closedAt()` not null.
- `OutboxAssert.rows("shop.shop-closed.v1")`가 정확히 1건: `aggregateType()` = `SHOP`, `aggregateId()` = shopA, `status()` = `READY`, payload `shopId` = shopA 문자열, `memberId` = sellerId 문자열.
- 동작 2: `outboxRelay.relayOnce()`.
- 반환값 1.
- `Eventually`(5초) 안에서 `product1`·`product2`의 `productSnapshotOf(...).status()`가 모두 `DISCONTINUED`.

**4. 폐업 요청 자체의 거부** — 동작과 확인:

| 준비 | 요청 | 응답 | 확인할 것 |
|---|---|---|---|
| 다른 판매자 `seller2@test.com`의 토큰 | 가게 A 폐업 | 403 `FORBIDDEN` | `shopSnapshotOf(shopA).status()` = `ACTIVE`, `countOf("common.outbox_event")` = 0 |
| 가게 A를 먼저 폐업(204)한 뒤 `OutboxAssert.clear()` | 가게 A 폐업을 한 번 더 | 422 `SHOP_NOT_ACTIVE` | `countOf("common.outbox_event")` = 0(두 번째 요청이 이벤트를 만들지 않았다) |
| 없는 가게 ID(`Ids.newId()`) | 폐업 | 404 `SHOP_NOT_FOUND` | `countOf("common.outbox_event")` = 0 |

**5. 가게 2개 중 1개 폐업 → SELLER 유지, 나머지 폐업 → 회수** — 준비: 같은 판매자가 가게 B(`openShop(sellerId, "2222222222")`)도 연다. 상품은 만들지 않아도 된다. `OutboxAssert.clear()`.
- 동작 1: 가게 A 폐업 요청(204) → `relayOnce()` = 1. 폐업 이벤트의 `eventId`는 `OutboxAssert.rows("shop.shop-closed.v1")` 중 `aggregateId()` = A인 행의 `id()`(`eventIdA`), B도 같다(`eventIdB`). `Eventually`(5초) 안에서 `processedCountOf("member.shop-closed", eventIdA)` = 1.
- 확인 1: `memberSnapshotOf(sellerId).role()` = `SELLER`(가게 B가 ACTIVE다).
- 동작 2: 가게 B 폐업 요청(204) → `relayOnce()` = 1. `Eventually` 안에서 `processedCountOf("member.shop-closed", eventIdB)` = 1.
- 확인 2: `memberSnapshotOf(sellerId).role()` = `USER`.

**6. 같은 회원의 가게 2개 동시 폐업** — 준비: 5번과 같은 가게 A·B. 동작 1: `ConcurrencyRunner.run(List.of(A 폐업, B 폐업))`. 각 작업은 `DELETE` 요청의 HTTP 상태 코드를 돌려준다.
- 상태 코드: 둘 다 204.
- `OutboxAssert.rows("shop.shop-closed.v1")` 2건(`aggregateId()`가 A, B 하나씩. 두 행의 `id()`가 `eventIdA`, `eventIdB`).
- 동작 2: `relayOnce()` = 2. `Eventually`(10초) 안에서 `processedCountOf("member.shop-closed", eventIdA)` = 1, `processedCountOf("member.shop-closed", eventIdB)` = 1.
- 확인 2: `memberSnapshotOf(sellerId).role()` = `USER`. (처리 신호가 둘 다 나온 뒤에 읽는다. 둘이 겹쳐 낙관적 락 충돌이 나면 재시도되어 결국 마지막 처리가 활성 가게 0개를 본다.)

**7. 같은 shop-closed 이벤트를 두 번 소비** — 준비: 가게 A를 JDBC로 폐업 상태로 만든다(`closeShop(shopA)`). `@MockitoSpyBean ProductDiscontinueService`, `@MockitoSpyBean MemberRoleService`. 동작: `eventId1`로 `TestKafka.send(ShopTopics.SHOP_CLOSED, shopA.toString(), eventId1, new ShopClosedEvent(shopA, sellerId))`를 두 번 보내고, 이어서 센티널로 `eventId2`를 같은 키로 한 번 더 보낸다.
- `Eventually`(10초) 안에서 `processedCountOf("product.shop-closed", eventId2)` = 1, `processedCountOf("member.shop-closed", eventId2)` = 1.
- `processedCountOf("product.shop-closed", eventId1)` = 1, `processedCountOf("member.shop-closed", eventId1)` = 1.
- `verify(productDiscontinueService, times(2)).discontinueAllByShop(shopA)`와 `verify(memberRoleService, times(2)).revokeSellerIfNoActiveShop(sellerId)`(eventId1 한 번 + 센티널 한 번. 3이면 중복이 처리된 것이다).

**테스트 준비**

**이 단계에서 추가하는 헬퍼** (`support/`. 이름·인자는 권장안이라 쓰다 바꿔도 된다)

| 대상 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `TestKafka.send(String topic, String key, UUID eventId, Object event)` → `int` | 위 "읽는 방법"의 봉투 만들기 → `kafkaTemplate.send(topic, key, json).get(5, SECONDS)` → `getRecordMetadata().partition()` 반환 | `KafkaDltTest`, `CloseShopTest` 7번, 3-4 이후 |
| `TestKafka.sendRaw(String topic, String key, String value)` → `int` | 위와 같고 봉투 만들기만 건너뜀 | `KafkaDltTest` 2번 |
| `AlwaysFailListener` (`src/test`의 `@Component`) | 위 `KafkaDltTest` 준비 | `KafkaDltTest` |
| `processedCountOf(String consumer, UUID eventId)` → `int` | JDBC `SELECT COUNT(*) FROM common.processed_message WHERE consumer = ? AND event_id = ?` | `InboxGuardTest`, `CloseShopTest`, 3-4·3-5·3-6의 consumer 확인 |
| `shopSnapshotOf(shopId)` → `ShopSnapshot` | 1-6 표에 정의된 헬퍼다(읽는 컬럼·필드는 그 표). 처음 쓰는 미구현 단계인 이 단계에서 `TestFixtures`에 추가하고 `closedAt`(`Instant`, 없으면 null)을 더한다. 읽는 컬럼 `shop.shop.closed_at` | `CloseShopTest` |
| `productSnapshotOf(productId)` → `ProductSnapshot` | 1-7 표에 정의된 헬퍼다(읽는 컬럼·필드는 그 표). 처음 쓰는 미구현 단계인 이 단계에서 `TestFixtures`에 추가한다. 이 단계는 `status()`만 읽는다. 가격 이력(`priceChanges`)은 3-3이 더한다 | `CloseShopTest`, 3-3 |

이미 있는 헬퍼: `signup`, `token`, `login`, `openShop`, `closeShop`, `registerProduct`, `hideProduct`, `discontinueProduct`, `createPaidOrder`, `shopOrderIdOf`, `ConcurrencyRunner`, 3-1의 `Eventually`·`OutboxAssert`·`TestKafka.messages`·`memberSnapshotOf`·`countOf`·`changeRole`.

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

발행하는 모양은 모두 같다: 서비스(또는 엔티티를 바꾼 직후의 코드)가 `outboxPublisher.publish(토픽, aggregateType, aggregateId, 이벤트 record)`를 부른다. 서비스의 `@Transactional` 안에서 부르므로 상태 변경이 롤백되면 이벤트도 사라지고(완료 확인 3-3의 롤백 케이스: outbox 0건), 이 단계에서 서비스에 `OutboxPublisher`(order는 `OrderEventPublisher`) 의존성을 더한다. 한 흐름에서 이벤트가 둘이면(상품 수정 + 가격 변경) 같은 키(`productId`)라 **호출한 순서대로** 같은 파티션에서 소비된다.

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

읽는 방법은 3-1과 같다. 이 단계에서 더하는 것은 다음과 같다.
- 이벤트는 `OutboxAssert.rows(토픽)`의 `OutboxSnapshot` 하나다. 확인 항목은 `aggregateType()`, `aggregateId()`와 payload의 필드 값이다(`eventType()`·`status()`는 3-1 `OutboxPublisherTest` 3번이 확인한다). payload 필드는 `payload().get("필드").asText()`(문자열·UUID), `asLong()`(금액·`version`), `asBoolean()`, `size()`(배열 길이)로 읽는다. "정확히 N건"은 `rows(토픽).size()`, "없다"는 0건이다.
- DB 값과 비교하는 필드: 상품 `productSnapshotOf(productId)`의 `version()`·`price()`, 가격 이력 `productSnapshotOf(productId).priceChanges()`(`oldPrice()`, `newPrice()`, `changedAt()`), 품목 상태·확정 시각·결제 키 `snapshotOf(orderId)`(2-8a, `confirmedAt`은 2-10)의 `line(productId).status()`·`line(productId).confirmedAt()`·`payment().paymentKey()`.
- 모든 케이스는 준비를 마친 직후 `OutboxAssert.clear()`로 outbox를 비운 뒤 동작한다(준비의 가입·상품 등록·주문이 이벤트를 만든다). "정확히 1건"은 비운 뒤 동작이 만든 행만 센다.
- 이벤트를 일으키는 방법은 서비스를 직접 부르는 헬퍼(`signup`, `registerProduct`, `createPaidOrder` 등)를 우선 쓰고, 응답·요청 형태가 의미 있는 곳만 MockMvc로 한다. 상품 수정은 `PATCH /api/shops/{shopId}/products/{productId}`, 상태 변경은 `PATCH /api/shops/{shopId}/products/{productId}/status`(둘 다 `Authorization: Bearer {판매자 토큰}`, 본문 예 `{"price":12000}`, `{"status":"HIDDEN"}`). 품목 취소는 `POST /api/orders/{orderId}/lines/{lineId}/cancel`(`Idempotency-Key`: 새 UUID, 본문 `{"reason":"단순 변심"}`), 구매확정은 `POST /api/orders/{orderId}/lines/{lineId}/confirm`(`Idempotency-Key` 없음), 결제 전 주문 취소는 `POST /api/orders/{orderId}/cancel`.

**공통 준비** (세 `EventPublishingTest`가 쓴다): 판매자 `seller@test.com`(`signup`, 토큰은 `login`), 가게 `openShop(sellerId, "1111111111")`, 구매자 `buyer@test.com`. 상품은 `product1`(`registerProduct(sellerId, shopId, 1000, 10)`), `product2`(2000), `product3`(3000). 이 픽스처의 상품은 이름 `product`, 설명 `description`, 카테고리 `ETC`, 정기구독 불가(`false`)다.

#### `member/application/EventPublishingTest`

**1. 가입** — 준비: `OutboxAssert.clear()`. 동작: `memberId = signup("test@test.com")`(닉네임 `test`).
- `rows("member.member-registered.v1")` 정확히 1건: `aggregateType()` = `MEMBER`, `aggregateId()` = memberId.
- payload: `memberId` = memberId 문자열, `email` = `test@test.com`, `nickname` = `test`. 필드는 이 셋뿐이다(`payload.size()` = 3. 비밀번호 같은 값이 새지 않는다).

**2. 중복 이메일 가입은 롤백** — 준비: `signup("test@test.com")` → `OutboxAssert.clear()`. 동작: `signup("test@test.com")`을 한 번 더.
- `BusinessException`, `errorCode` = `MEMBER_EMAIL_DUPLICATED`.
- `countOf("common.outbox_event")` = 0.

#### `product/application/EventPublishingTest`

**1. 상품 등록** — 동작: `productId = registerProduct(sellerId, shopId, 1000, 10)`.
- `rows("product.product-upserted.v1")` 정확히 1건: `aggregateType()` = `PRODUCT`, `aggregateId()` = productId.
- payload: `productId`, `shopId` = 문자열 일치, `name` = `product`, `description` = `description`, `category` = `ETC`, `price` = 1000, `status` = `ON_SALE`, `subscribable` = false, `version` = `productSnapshotOf(productId).version()`.

**2. 가격 변경 없는 수정** — 준비: 상품 등록 → 등록 직후 `productSnapshotOf(productId).version()`을 `v0`로 읽음 → `OutboxAssert.clear()`. 동작: `PATCH` 본문 `{"name":"renamed","price":1000}`(가격은 같은 값).
- 응답 200.
- `product-upserted` 정확히 1건: payload `name` = `renamed`, `price` = 1000, `version` = `productSnapshotOf(productId).version()` = `v0 + 1`.
- `product-price-changed` 0건.

**3. 가격 변경** — 준비: 상품 등록 → `OutboxAssert.clear()`. 동작: `PATCH` 본문 `{"price":1200}`.
- `product-upserted` 정확히 1건: payload `price` = 1200, `version` = `productSnapshotOf(productId).version()`.
- `product-price-changed` 정확히 1건: `aggregateType()` = `PRODUCT`, `aggregateId()` = productId, payload `productId`, `oldPrice` = 1000, `newPrice` = 1200, `changedAt` = `productSnapshotOf(productId).priceChanges().get(0).changedAt()`과 같은 시각(둘 다 `truncatedTo(ChronoUnit.MILLIS)`로 맞춰 비교).

**4. 상태 변경** — 준비: 상품 등록 → `OutboxAssert.clear()`.
- `HIDDEN`으로(`{"status":"HIDDEN"}`): `product-upserted` 1건(payload `status` = `HIDDEN`, `version` = `productSnapshotOf(productId).version()`), `product-discontinued` 0건.
- 이어서 `OutboxAssert.clear()` 후 `DISCONTINUED`로(`{"status":"DISCONTINUED"}`): `product-upserted` 1건(payload `status` = `DISCONTINUED`, `version` = `productSnapshotOf(productId).version()`), `product-discontinued` 1건(payload `productId`, `shopId`, `reason` = `SELLER`).

**5. 가게 폐업 단종** — 준비: `product1`은 그대로 `ON_SALE`, `hideProduct(product2Id)`(`HIDDEN`), `discontinueProduct(product3Id)`(이미 `DISCONTINUED`) → `OutboxAssert.clear()`. 동작: `productDiscontinueService.discontinueAllByShop(shopId)`를 직접 호출(서비스의 `@Transactional`이 경계다).
- `product-upserted` 정확히 2건: `aggregateId()`가 {`product1`, `product2`}, 둘 다 payload `status` = `DISCONTINUED`, `version` = 각 상품의 `productSnapshotOf(...).version()`.
- `product-discontinued` 정확히 2건: `aggregateId()`가 {`product1`, `product2`}, 둘 다 payload `reason` = `SHOP_CLOSED`, `shopId` = shopId 문자열.
- `product3`의 행은 없다(이미 단종된 상품은 이벤트를 만들지 않는다).
- 한 번 더 `discontinueAllByShop(shopId)` → 두 토픽 모두 건수 그대로(2건, 2건).

**6. 롤백** — 준비: `discontinueProduct(product1Id)` → `OutboxAssert.clear()`. 동작: `PATCH` 본문 `{"name":"renamed"}`.
- 응답 409, `$.code` = `PRODUCT_DISCONTINUED`.
- `countOf("common.outbox_event")` = 0(전체 토픽).

#### `order/application/EventPublishingTest`

**1. 결제 완료(order-paid)** — 준비: 구매자가 `createPendingOrder(buyerId, product1Id, product2Id)` → `OutboxAssert.clear()`. 동작: `confirmPayment(buyerId, orderId)`(가짜 PG 기본 승인).
- `rows("order.order-paid.v1")` 정확히 1건: `aggregateType()` = `ORDER`, `aggregateId()` = orderId.
- payload: `orderId`, `memberId` = buyerId 문자열, `totalAmount` = 3000, `shopOrders` 길이 1.
- `shopOrders[0]`: `shopOrderId` = `shopOrderIdOf(orderId, shopId)`, `shopId`, `lines` 길이 2.
- `lines`의 각 원소: `orderLineId` = `orderLineIdOf(orderId, productId)`, `productId`, `productName` = `product`, `quantity` = 1. `product1`과 `product2`가 하나씩 들어 있다.
- 같은 결과를 한 번 더 반영해도 이벤트는 늘지 않는다: `orderPaymentApplier.applyPaymentState(orderId, PaymentState.APPROVED, null)`을 호출하면 반환값 `PAID`("이미 처리됨" 가드)이고 `order-paid`는 그대로 1건.

**2. 대사로 늦게 확정된 결제** — 준비: `pg.on("/v1/payments/confirm", Behavior.delay(1000))` → `createPendingOrder(buyerId, product1Id)` → `confirmPayment`(202, 주문 `PAYMENT_IN_PROGRESS`) → `setOrderUpdatedAt(orderId, 지금 - 60초)` → `OutboxAssert.clear()`.
- 확인 1: `rows("order.order-paid.v1")` 0건(승인이 불확실한 동안 이벤트가 없다).
- 동작: `orderReconcileService.reconcileBatch()`.
- 확인 2: 반환값 1, `order-paid` 정확히 1건(`aggregateId()` = orderId, payload `totalAmount` = 1000).

**3. 만료** — 준비: `createPendingOrder(buyerId, product1Id)` → `setOrderExpired(orderId, 지금 - 1분)` → `OutboxAssert.clear()`. 동작: `orderExpirationService.expireBatch()`.
- 반환값 1.
- `rows("order.order-expired.v1")` 정확히 1건: `aggregateId()` = orderId, payload `orderId`, `memberId` = buyerId 문자열, `reason` = `EXPIRED`. 필드는 이 셋뿐이다.

**4. 결제 실패** — 준비: `pg.on("/v1/payments/confirm", Behavior.reject("REJECT_CARD_COMPANY"))` → `createPendingOrder(buyerId, product1Id)` → `OutboxAssert.clear()`. 동작: `confirmPayment(buyerId, orderId)`.
- `BusinessException`, `errorCode` = `PAYMENT_FAILED`.
- `order-expired` 정확히 1건: payload `reason` = `PAYMENT_FAILED`(`orders.orders.failure_reason`에 저장되는 `REJECT_CARD_COMPANY`가 든 문장이 아니다).
- `order-paid` 0건.

**5. 결제 전 주문 취소는 이벤트가 없다** — 준비: `createPendingOrder(buyerId, product1Id)` → `OutboxAssert.clear()`. 동작: `POST /api/orders/{orderId}/cancel`.
- 응답 200.
- `countOf("common.outbox_event")` = 0.

**6. 발송과 배송완료** — 준비: `createPaidOrder(buyerId, product1Id)` → `shopOrderId = shopOrderIdOf(orderId, shopId)` → `OutboxAssert.clear()`. 동작 1: `shipShopOrder(sellerId, shopId, shopOrderId)`.
- `rows("order.shop-order-shipped.v1")` 정확히 1건: `aggregateId()` = **orderId**(shopOrderId가 아니다), payload `shopOrderId`, `orderId`, `memberId` = buyerId 문자열, `carrier` = `CJ대한통운`, `trackingNumber` = `1234567890`.
- 동작 2: `deliverShopOrder(sellerId, shopId, shopOrderId)`.
- `rows("order.shop-order-delivered.v1")` 정확히 1건: `aggregateId()` = orderId, payload `shopOrderId`, `orderId`, `memberId`.

**7. 환불 완료** — 준비: `createPaidOrder(buyerId, product1Id, product2Id)` → `lineId = orderLineIdOf(orderId, product1Id)` → `OutboxAssert.clear()`. 동작 1: `product1` 품목 취소 요청(HTTP) → 200.
- `rows("order.order-line-refunded.v1")` 정확히 1건: `aggregateId()` = orderId, payload `refundId` = 응답의 `$.refundId`, `orderLineId` = lineId, `orderId`, `memberId` = buyerId 문자열, `amount` = 1000.
- 동작 2: `refundCompleter.complete(refundId)`를 한 번 더 호출(이미 완료된 환불).
- 건수 그대로 1건.

**8. 환불이 끝나지 않았으면 이벤트가 없다** — 준비: 7번과 같은 주문에 `pg.on("/v1/payments/" + paymentKey, Behavior.delay(1000))`(`paymentKey`는 `snapshotOf(orderId).payment().paymentKey()`) → `OutboxAssert.clear()`. 동작: `product1` 품목 취소 요청 → 202, `$.status` = `APPROVED`.
- `rows("order.order-line-refunded.v1")` 0건.
- 정상 응답으로 되돌리고(`pg.on(..., Behavior.delay(0))`) `setRefundUpdatedAt(refundId, 지금 - 6분)` → `refundRecoveryService.recoverBatch()` = 1 → `order-line-refunded` 정확히 1건.

**9. 수동 구매확정** — 준비: `createDeliveredOrder(buyerId, sellerId, shopId, product1Id, product2Id)` → `lineId = orderLineIdOf(orderId, product1Id)` → `OutboxAssert.clear()`. 동작: 구매확정 요청(HTTP) → 200.
- `rows("order.order-line-confirmed.v1")` 정확히 1건: `aggregateId()` = orderId, payload `orderLineId` = lineId, `orderId`, `shopId`, `productId` = product1Id, `memberId` = buyerId 문자열, `amount` = 1000, `confirmedAt` = `snapshotOf(orderId).line(product1Id).confirmedAt()`(밀리초까지 맞춰 비교).
- `product2` 품목의 이벤트는 없다(품목마다 1건).

**10. 확정 금액은 환불을 뺀 값** — 준비: 9번의 주문에서 JDBC `UPDATE orders.order_line SET refunded_amount = 300 WHERE id = lineId` → `OutboxAssert.clear()`. 동작: 구매확정 요청.
- `order-line-confirmed` 1건, payload `amount` = 700(`lineAmount - refundedAmount`).

**11. 자동 구매확정은 품목마다 1건** — 준비: `createDeliveredOrder(buyerId, sellerId, shopId, product1Id, product2Id, product3Id)` → `setShopOrderDeliveredAt(shopOrderId, 지금 - 8일)` → `OutboxAssert.clear()`. 동작: `autoConfirmService.confirmBatch()`.
- 반환값 3.
- `rows("order.order-line-confirmed.v1")` 정확히 3건: `aggregateId()`가 모두 orderId, payload `orderLineId`의 집합 = 세 품목 ID, `amount`의 집합 = {1000, 2000, 3000}.
- `confirmBatch()`를 한 번 더 → 반환값 0, 건수 그대로 3건.

**12. 롤백** — 아래 두 경우 모두 outbox가 비어 있다.
- 재고 부족 체크아웃: 준비: 재고 1인 상품(`registerProduct(sellerId, shopId, 1000, 1)`)을 구매자가 수량 2로 담는다(`addToCart(buyerId, productId, 2)`) → `OutboxAssert.clear()`. 동작: `checkout`(`address(buyerId)`의 배송지). `BusinessException`, `errorCode` = `OUT_OF_STOCK`.
- 발행이 실패하면 상태 변경도 취소: 준비: 9번의 주문과 `@MockitoSpyBean OutboxPublisher`에 `doThrow(new IllegalStateException("test")).when(outboxPublisher).publish(any(), any(), any(), any())` → `OutboxAssert.clear()`. 동작: 구매확정 요청(HTTP) → 응답 500, `$.code` = `INTERNAL_ERROR`. `snapshotOf(orderId).line(product1Id).status()` = `DELIVERED`, / `countOf("common.outbox_event")` = 0(상태 변경과 이벤트가 한 트랜잭션이다).

#### `ModularityTest`

기존 `ModularityTest.verifyModules()`가 `ApplicationModules.of(MyRoutineApplication.class).verify()`에서 예외 없이 끝난다. 이 단계에서 `order/api/package-info.java`(`@NamedInterface("api")`)가 새로 생기므로 `order.api`·`order.api.event`가 다른 모듈에서 import 가능한 이름 있는 인터페이스인지가 여기서 갈린다. 실패하면 메시지에 나온 모듈 의존 위반을 `package-info.java`의 `allowedDependencies`나 `@NamedInterface` 위치로 고친다.

**테스트 준비**

**이 단계에서 추가하는 헬퍼** (`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다)

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `productSnapshotOf(productId)`에 필드 추가 | 3-2에서 추가한 `ProductSnapshot`에 1-8 표에 정의된 `priceChanges`(`List<PriceChangeState(oldPrice, newPrice)>`)를 추가하고(처음 쓰는 미구현 단계가 이 단계다), `version`(long, 읽는 컬럼 `product.product.version`)을 더하고, `PriceChangeState`에 `changedAt`(`Instant`, 읽는 컬럼 `product.price_history.changed_at`)을 더한다 | 상품 `EventPublishingTest` 1~5번 |

이미 있는 헬퍼: Part 2의 2-9·2-10이 만든 `createDeliveredOrder(buyerId, sellerId, shopId, productIds...)`, `setShopOrderDeliveredAt(shopOrderId, Instant)`, 2-8a가 만든 `orderLineIdOf`·`snapshotOf(orderId)`(2-10이 `confirmedAt`을 더했다), 2-8b가 만든 `setRefundUpdatedAt`, `signup`, `login`, `openShop`, `registerProduct`, `hideProduct`, `discontinueProduct`, `address`, `addToCart`, `checkout`, `createPendingOrder`, `confirmPayment`, `setOrderExpired`, `setOrderUpdatedAt`, `createPaidOrder`, `shopOrderIdOf`, `shipShopOrder`, `deliverShopOrder`, 3-1의 `OutboxAssert`·`countOf`.

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

**테스트 헬퍼**: `SettlementServiceTest`는 정산 대상을 이벤트가 아니라 JDBC로 직접 적재한다(가게 1,200개 × 품목 2개). `settlement.settlement_item`에 `settlement_id = NULL`인 행을 넣는 헬퍼(`TestFixtures.insertSettlementItem(s)`)는 완료 확인 (3-4a)의 "테스트 준비"에 있다.

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

### 완료 확인 (3-4a)

읽는 방법은 3-1·3-2와 같다. 이 단계에서 더하는 것은 다음과 같다.
- 정산: `SettlementSnapshot s = settlementSnapshotOf(shopId)` 한 번으로 읽는다(가게의 정산이 하나일 때. 없으면 `null`, 둘 이상이면 `IllegalStateException`). 달이 여럿이면 `settlementSnapshotOf(shopId, YearMonth.of(2026, 9))`로 달을 정한다(그 달 정산이 없으면 `null`). `s.status()`, `s.grossAmount()`, `s.feeAmount()`, `s.netAmount()`, `s.itemCount()`, `s.feeRateBp()`, `s.periodStart()`, `s.periodEnd()`, 이 정산에 붙은 품목은 `s.items()`(`orderLineId()`, `amount()`, `confirmedAt()`). 정산 건수는 `countOf("settlement.settlement")`.
- 정산 대상 품목 하나: `SettlementItemSnapshot i = settlementItemSnapshotOf(orderLineId)`(없으면 `null`). `i.shopId()`, `i.orderId()`, `i.productId()`, `i.amount()`, `i.confirmedAt()`, 붙은 정산 `i.settlementId()`(미정산이면 null), 그 정산의 기간 시작 `i.periodStart()`(미정산이면 null). 품목 수는 `countOf("settlement.settlement_item")`, 미정산 품목 수는 `unsettledItemCount()`.
- 실행 결과: `SettlementRunResult result = settlementService.run(YearMonth.of(2026, 9))`의 `result.settledShops()`.

3-4a에는 지급이 없으므로 `run` 뒤 정산 `status`는 `CALCULATED`다. 3-4b에서 `run`이 끝에 `payoutAll()`을 부르면 `PAID`로 바뀌므로, 그때 이 단계 케이스의 `status` 단언만 `PAID`로 고친다.

**공통 준비** (`SettlementServiceTest`): 정산 대상은 이벤트가 아니라 JDBC로 직접 적재한다. 정산 월은 `YearMonth.of(2026, 9)`, 업무 시간대 `Asia/Seoul`, 수수료율 500bp(기본 설정), 품목 확정 시각은 기간 한가운데인 `Instant.parse("2026-09-15T03:00:00Z")`(KST 9월 15일 12시)로 쓴다. 가게는 `Ids.newId()`로 만든 UUID이며 `shop` 모듈에 실제 가게가 없어도 된다. 가게 1곳의 품목이 2개이고 각 7,500원이면 `gross` 15,000, `fee` 750(15,000 × 500 / 10,000), `net` 14,250이다.

#### `settlement/domain/SettlementPeriodTest`

**1. 정산 기간의 경계 시각** — 동작: `SettlementPeriod period = SettlementPeriod.of(YearMonth.of(2026, 9))`, `ZoneId zone = ZoneId.of("Asia/Seoul")`.
- `period.start()` = `LocalDate.of(2026, 9, 1)`, `period.end()` = `LocalDate.of(2026, 9, 30)`(말일, 포함).
- `period.fromInclusive(zone)` = `Instant.parse("2026-08-31T15:00:00Z")`.
- `period.toExclusive(zone)` = `Instant.parse("2026-09-30T15:00:00Z")`.

**2. 수수료는 원 미만 내림** — 준비: `Settlement settlement = BeanUtils.instantiateClass(Settlement.class)`(`org.springframework.beans.BeanUtils`. 엔티티에 테스트용 생성 방법이 규격에 없어 기본 생성자를 리플렉션으로 부른다. 확인 필요). 동작: `settlement.calculate(Money.of(gross), bp, itemCount)`. 값은 필드 이름대로 `getGrossAmount()`·`getFeeAmount()`·`getNetAmount()`(`Money`면 `.amount()`), `getFeeRateBp()`, `getItemCount()`로 읽는다.

| gross | bp | itemCount | `feeAmount` | `netAmount` |
|---|---|---|---|---|
| 12345 | 500 | 3 | 617 (617.25의 내림) | 11728 |
| 19 | 500 | 1 | 0 (0.95의 내림) | 19 |
| 0 | 500 | 0 | 0 | 0 |

모든 행에서 `getGrossAmount()` = gross, `getFeeRateBp()` = bp, `getItemCount()` = itemCount다.

#### `settlement/application/SettlementItemConsumerTest`

준비: `orderId`, `shopId`, `productId`, `memberId`는 `Ids.newId()`. 이벤트 `OrderLineConfirmedEvent(orderLineId1, orderId, shopId, productId, memberId, 15000, Instant.parse("2026-09-15T03:00:00Z"))`. 보낼 때는 `TestKafka.send(OrderTopics.ORDER_LINE_CONFIRMED, orderId.toString(), eventId, event)`.

**1. 같은 확정 이벤트 두 번** — 동작: 같은 `eventId1`로 두 번 보낸 뒤, 센티널로 같은 키에 다른 `eventId2`·다른 `orderLineId2`의 이벤트를 한 번 더 보낸다.
- `Eventually`(5초) 안에서 `settlementItemSnapshotOf(orderLineId2)`가 `null`이 아니다.
- `settlementItemSnapshotOf(orderLineId1)`: `shopId()` = shopId, `orderId()` = orderId, `productId()` = productId, `amount()` = 15000, `confirmedAt()` = 이벤트의 `confirmedAt`, `settlementId()` null.
- `countOf("settlement.settlement_item")` = 2.

**2. 다른 eventId로 같은 품목이 와도 한 번** — InboxGuard를 거치지 않는 최종 방어(`uk_settlement_item_order_line`)를 확인한다. 동작: `settlementItemService.register(event)`를 `amount` 15000으로 한 번, 같은 `orderLineId1`에 `amount` 99999로 한 번 더 호출.
- 두 번째 호출도 예외 없이 끝난다.
- `countOf("settlement.settlement_item")` = 1이고 `settlementItemSnapshotOf(orderLineId1).amount()` = 15000(두 번째 값 99999가 덮어쓰지 않는다).

#### `settlement/application/SettlementServiceTest`

**1. 가게 1,200곳 정산** — 준비: 가게 1,200곳 × 품목 2개(각 7,500원) = 2,400행(`insertSettlementItems`). 가게 수가 `findShopIdsToSettle`의 한 페이지(500)보다 커서 keyset이 3페이지(500, 500, 200)를 돈다. 동작: `settlementService.run(YearMonth.of(2026, 9))`.
- `result.settledShops()` = 1200.
- `countOf("settlement.settlement")` = 1200.
- `SELECT COUNT(*) FROM settlement.settlement WHERE gross_amount = 15000 AND item_count = 2 AND fee_rate_bp = 500 AND period_start = DATE '2026-09-01' AND period_end = DATE '2026-09-30' AND status = 'CALCULATED'` = 1200.
- `unsettledItemCount()` = 0.

**2. run을 두 번** — 준비: 가게 3곳 × 품목 2개(각 7,500원). 동작: `run`을 두 번(결과를 `first`, `second`로 받는다).
- `first.settledShops()` = 3, `second.settledShops()` = 0.
- 첫 `run` 뒤 `countOf("settlement.settlement")` = 3.
- 세 가게의 `settlementSnapshotOf(shopId)`(가게마다 정산이 하나)를 첫 `run` 뒤에 `List`로 모아 두고(`before`) 둘째 `run` 뒤에 다시 모으면(`after`) `after`가 `before`와 같다(`isEqualTo`. 정산 ID·금액·`itemCount`(2)·`grossAmount`(15000)·붙은 품목 목록이 그대로이고, 품목이 다른 정산으로 옮겨 붙지 않았다).

**3. 정산 뒤에 늦게 도착한 전월 품목은 들어가지 않는다** — 준비: 가게 1곳 × 품목 2개(각 7,500원) → `run`. 이어서 같은 가게의 9월 15일 확정 품목을 `insertSettlementItem(shopId, 7500, 위 확정 시각)`으로 하나 더 넣는다(`lateLineId`). 동작: `run`을 다시.
- `second.settledShops()` = 0(같은 가게를 다시 집어도 `insertIfAbsent`가 0행이라 건너뛴다).
- 정산은 1건 그대로: `settlementSnapshotOf(shopId)`의 `grossAmount()` = 15000, `itemCount()` = 2.
- `settlementItemSnapshotOf(lateLineId).settlementId()`는 null(다음 달 정산 기간에도 맞지 않아 남는다).

**4. 기간 경계** — 준비: 가게 1곳에 품목 4개를 `insertSettlementItem`으로 넣는다.

| 품목 | 금액 | `confirmed_at` | KST |
|---|---|---|---|
| A | 1000 | `2026-08-31T14:59:59Z` | 8월 31일 23:59:59 |
| B | 2000 | `2026-08-31T15:00:00Z` | 9월 1일 00:00:00 |
| C | 4000 | `2026-09-30T14:59:59Z` | 9월 30일 23:59:59 |
| D | 8000 | `2026-09-30T15:00:00Z` | 10월 1일 00:00:00 |

동작과 확인:
- `insertSettlementItem`이 돌려준 `orderLineId`를 품목 이름대로 `lineA`~`lineD`라 한다.
- `run(YearMonth.of(2026, 8))` → `result.settledShops()` = 1. `settlementSnapshotOf(shopId, YearMonth.of(2026, 8))`: `grossAmount()` = 1000, `itemCount()` = 1.
- `run(YearMonth.of(2026, 9))` → `settledShops` = 1. `settlementSnapshotOf(shopId, YearMonth.of(2026, 9))`: `grossAmount()` = 6000, `itemCount()` = 2. `settlementItemSnapshotOf(lineD).settlementId()`는 null(10월 몫).
- `run(YearMonth.of(2026, 10))` → `settlementSnapshotOf(shopId, YearMonth.of(2026, 10))`: `grossAmount()` = 8000, `itemCount()` = 1.

**5. 한 가게의 실패가 다른 가게를 막지 않는다** — 준비: `@MockitoSpyBean SettlementRepository`. 가게 3곳 × 품목 2개. DB 순서로 첫 가게를 `badShopId`로 한다(`SELECT shop_id FROM settlement.settlement_item ORDER BY shop_id LIMIT 1`). `doThrow(new RuntimeException("test")).when(settlementRepository).insertIfAbsent(any(), eq(badShopId), any(), any(), anyInt(), any())`. 확인 필요: Spring Data 저장소 프록시에 스파이가 붙는지는 구현 뒤 확인한다(안 붙으면 이 케이스는 다른 방법으로 한 가게만 실패시켜야 한다). 동작 1: `run(YearMonth.of(2026, 9))`.
- 예외가 밖으로 나오지 않는다.
- `result.settledShops()` = 2, `countOf("settlement.settlement")` = 2, `settlementSnapshotOf(badShopId)`가 `null`(정산 0건).
- `unsettledItemCount()` = 2(`badShopId`의 품목 2개만 미정산으로 남는다).
- 동작 2: `Mockito.reset(settlementRepository)` → `run` 다시. `result.settledShops()` = 1, `countOf("settlement.settlement")` = 3, `settlementSnapshotOf(badShopId).grossAmount()` = 15000.

**테스트 준비**

**이 단계에서 추가하는 헬퍼** (`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다)

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `insertSettlementItem(UUID shopId, long amount, Instant confirmedAt)` → `orderLineId` | JDBC `INSERT INTO settlement.settlement_item (id, order_line_id, shop_id, order_id, product_id, amount, confirmed_at, settlement_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, now(), now())`. `id`·`order_line_id`·`order_id`·`product_id`는 `Ids.newId()`. `Instant`는 `OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)`로 넘긴다 | 기간 경계·늦은 품목·3-6 |
| `insertSettlementItems(List<UUID> shopIds, int itemsPerShop, long amount, Instant confirmedAt)` | 위와 같은 INSERT를 `jdbcTemplate.batchUpdate`로 가게마다 `itemsPerShop`개 | 1,200개 가게, 둘 이상의 가게 |
| `settlementSnapshotOf(UUID shopId, YearMonth month)`, `settlementSnapshotOf(UUID shopId)` → `SettlementSnapshot` | 쿼리 2개: 정산(`settlement.settlement`의 `id`, `period_start`, `period_end`, `status`, `gross_amount`, `fee_amount`, `net_amount`, `fee_rate_bp`, `item_count`, `payout_attempts`, `payout_reference`, `last_error`, `paid_at` — `WHERE shop_id = ? AND period_start = ?`에 `month.atDay(1)`), 그 정산에 붙은 품목(`settlement.settlement_item`의 `order_line_id`, `amount`, `confirmed_at` — `WHERE settlement_id = ? ORDER BY confirmed_at, order_line_id`). `support/`의 record로 `id`(UUID), `periodStart`·`periodEnd`(`LocalDate`), `status`·`payoutReference`·`lastError`(String, 뒤 둘은 없으면 null), `grossAmount`·`feeAmount`·`netAmount`(long), `feeRateBp`·`itemCount`·`payoutAttempts`(int), `paidAt`(`Instant`, 없으면 null), `items`(`List<ItemState(UUID orderLineId, long amount, Instant confirmedAt)>`)를 가진다. 정산이 없으면 `null`. 달을 받지 않는 `settlementSnapshotOf(shopId)`는 `period_start` 조건 없이 읽고 정산이 둘 이상이면 `IllegalStateException`을 던진다. 지급 필드(`payoutAttempts` 이하)는 3-4b가 쓴다 | 3-4a·3-4b `SettlementServiceTest`, `SettlementControllerTest` |
| `settlementItemSnapshotOf(UUID orderLineId)` → `SettlementItemSnapshot` | JDBC `SELECT i.id, i.shop_id, i.order_id, i.product_id, i.amount, i.confirmed_at, i.settlement_id, s.period_start FROM settlement.settlement_item i LEFT JOIN settlement.settlement s ON s.id = i.settlement_id WHERE i.order_line_id = ?`. `support/`의 record로 `id`·`shopId`·`orderId`·`productId`·`settlementId`(UUID, `settlementId`는 미정산이면 null), `amount`(long), `confirmedAt`(`Instant`), `periodStart`(`LocalDate`, 미정산이면 null)를 가진다. 행이 없으면 `null` | `SettlementItemConsumerTest`, `SettlementServiceTest` |
| `unsettledItemCount()` → `long` | JDBC `SELECT COUNT(*) FROM settlement.settlement_item WHERE settlement_id IS NULL` | `SettlementServiceTest` 1·5번, `SettlementControllerTest` 5번 |

이미 있는 헬퍼: 3-1의 `Eventually`·`countOf`, 3-2의 `TestKafka.send`·`processedCountOf`.

### 완료 확인 (3-4b)

읽는 방법과 공통 준비는 3-4a와 같다. 이 단계에서 더하는 것은 다음과 같다.
- 호출 기록: `fakePayoutGateway.calls()` → `List<PayoutCall>`(`record PayoutCall(UUID settlementId, UUID shopId, long amount)`). 실패시키기는 `fakePayoutGateway.failNext(int times)`. 기록과 남은 실패 횟수는 `IntegrationTestSupport`의 `@AfterEach`가 `reset()`으로 비운다.
- 지급 값: 3-4a의 `settlementSnapshotOf(shopId)`의 `status()`, `payoutAttempts()`, `payoutReference()`, `lastError()`, `paidAt()`.
- 지급 이벤트: `OutboxAssert.rows("settlement.settlement-paid.v1")`.

#### `settlement/application/SettlementServiceTest` (지급)

공통 준비: 가게 1곳 × 품목 2개(각 7,500원), `net` 14,250. 3-4a의 케이스 1~5는 `status` 단언을 `PAID`로 고쳐 그대로 통과해야 한다.

**6. 지급 성공** — 동작: `run(YearMonth.of(2026, 9))`.
- `s = settlementSnapshotOf(shopId)`: `status()` = `PAID`, `payoutReference()` not null, `paidAt()` not null.
- `fakePayoutGateway.calls()` 정확히 1건: `settlementId` = `s.id()`, `shopId`, `amount` = 14250.
- `rows("settlement.settlement-paid.v1")` 정확히 1건: `aggregateId()` = `s.id()`(`aggregateType()`은 `SETTLEMENT`. 규격에 없어 확인 필요), payload `settlementId`, `shopId`, `netAmount` = 14250, `periodStart` = `2026-09-01`, `periodEnd` = `2026-09-30`.
- `run`을 한 번 더 → `calls()` 1건 그대로, `settlement-paid` 1건 그대로(PAID 정산은 다시 지급하지 않는다).

**7. 0원 정산은 송금 없이 PAID** — 준비: 가게 1곳에 `insertSettlementItem(shopId, 0, 확정 시각)` 한 개. 동작: `run`.
- `settlementSnapshotOf(shopId)`: `status()` = `PAID`, `payoutReference()` null, `paidAt()` not null.
- `fakePayoutGateway.calls()` 0건.
- `rows("settlement.settlement-paid.v1")` 1건, payload `netAmount` = 0.

**8. 지급 실패 후 재시도** — 준비: `fakePayoutGateway.failNext(1)`. 동작 1: `run`.
- `settlementSnapshotOf(shopId)`: `status()` = `PAYOUT_FAILED`, `payoutAttempts()` = 1, `lastError()` not null(길이 500 이하), `payoutReference()` null, `paidAt()` null.
- `rows("settlement.settlement-paid.v1")` 0건.
- 동작 2: `run` 다시.
- `settlementSnapshotOf(shopId)`: `status()` = `PAID`, `payoutReference()` not null, `payoutAttempts()` = 1(성공은 올리지 않는다), `lastError()`는 이전 실패 메시지가 남을 수 있어 확인하지 않는다.
- `rows("settlement.settlement-paid.v1")` 정확히 1건.

**9. 같은 정산을 두 번 지급해도 참조 ID는 같다** — 준비: `run`으로 `PAID`가 된 정산의 `settlementSnapshotOf(shopId).payoutReference()`를 `ref1`으로 읽는다. JDBC `UPDATE settlement.settlement SET status = 'CALCULATED', payout_reference = NULL, paid_at = NULL WHERE id = ?`로 되돌린다(송금은 됐지만 기록 전에 죽은 상황). 동작: `run`.
- `settlementSnapshotOf(shopId)`: `status()` = `PAID`, `payoutReference()` = `ref1`(같은 참조 ID).
- `fakePayoutGateway.calls()` 정확히 2건이고 두 건의 `settlementId`가 같다(게이트웨이는 두 번 불렸지만 같은 키라 같은 이체로 취급한다).

#### `settlement/web/SettlementControllerTest`

공통 준비: 판매자 A(`seller@test.com`)와 가게 A(`openShop(sellerA, "1111111111")`), 판매자 B(`seller2@test.com`)와 가게 B(`"2222222222"`). 토큰은 `login`. 가게 A·B 각각에 `insertSettlementItems(List.of(shopId), 2, 7500, 확정 시각)` 뒤 `settlementService.run(YearMonth.of(2026, 9))`로 정산을 만든다. 정산 ID는 `settlementSnapshotOf(shopId).id()`(`settlementA`, `settlementB`).

**1. 가게 주인의 목록 조회** — 동작: A의 토큰으로 `GET /api/shops/{shopA}/settlements`.
- 응답 200. `$.items.length()` = 1, `$.nextCursor` null.
- `$.items[0]`: `id` = settlementA, `periodStart` = `2026-09-01`, `periodEnd` = `2026-09-30`, `grossAmount` = 15000, `feeAmount` = 750, `netAmount` = 14250, `itemCount` = 2, `status` = `PAID`, `paidAt` not null.

**2. 가게 주인의 상세 조회** — 동작: A의 토큰으로 `GET /api/shops/{shopA}/settlements/{settlementA}`.
- 응답 200. `$.settlement.id` = settlementA, `$.settlement.netAmount` = 14250.
- `$.items.items.length()` = 2. 각 원소에 `orderLineId`, `orderId`, `productId`, `confirmedAt`가 있고 `amount` = 7500.

**3. 남의 가게 정산 조회 거부** — 동작: B의 토큰으로 `GET /api/shops/{shopA}/settlements`, 이어서 `GET /api/shops/{shopA}/settlements/{settlementA}`.
- 둘 다 응답 403, `$.code` = `FORBIDDEN`.

**4. 내 가게 경로에 남의 정산 ID** — 동작: A의 토큰으로 `GET /api/shops/{shopA}/settlements/{settlementB}`.
- 응답 404, `$.code` = `SETTLEMENT_NOT_FOUND`(규격에 없는 응답이라 확인 필요. 남의 정산이 ID로 보이면 안 된다).

**5. 일반 회원의 수동 실행 거부** — 준비: 이 케이스만 정산을 만들지 않고 가게 A에 미정산 품목 2개만 적재한다. 동작: A의 토큰(역할 `USER`/`SELLER`)으로 `POST /admin/api/settlements/run?period=2026-09`.
- 응답 403, `$.code` = `FORBIDDEN`.
- `unsettledItemCount()` = 2(실행되지 않았다).

**6. 관리자의 수동 실행** — 준비: 5번과 같은 미정산 품목. 회원 `admin@test.com`을 `signup`으로 가입해 `adminId`를 받고 `login`으로 `oldToken`을 받고 → `changeRole(adminId, "ADMIN")` → `login`(다시 로그인해야 토큰에 `ADMIN`이 담긴다). 동작: 관리자 토큰으로 `POST /admin/api/settlements/run?period=2026-09`.
- 응답 200, `$.settledShops` = 1. `$.paid` = 1(`payoutAll`이 센 건수로 읽힌다. 확인 필요).
- `countOf("settlement.settlement")` = 1, `settlementSnapshotOf(shopA).status()` = `PAID`.
- `oldToken`(역할을 바꾸기 전에 받은 토큰)으로 같은 요청을 하면 403, `$.code` = `FORBIDDEN`.

**테스트 준비**

**이 단계에서 추가하는 헬퍼** (`support/`. 이름·인자는 권장안이라 쓰다 바꿔도 된다)

| 대상 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `FakePayoutGateway` | 위 7) 규격. `calls()`가 돌려주는 `PayoutCall(UUID settlementId, UUID shopId, long amount)` record를 함께 둔다 | 3-4b 지급 케이스 |
| `IntegrationTestSupport`의 `@AfterEach` | `fakePayoutGateway.reset()` 호출 추가 | 모든 테스트 |

이미 있는 헬퍼: 3-4a의 `insertSettlementItem`·`insertSettlementItems`·`settlementSnapshotOf`·`unsettledItemCount`, 3-1의 `changeRole`, `signup`, `login`, `openShop`, `countOf`.

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
| `member-registered` | 회원 | WELCOME | `[MyRoutine] 가입을 환영합니다` |
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

읽는 방법은 3-1~3-4와 같다. 이 단계에서 더하는 것은 다음과 같다.
- 알림 행: `notificationRowsOf(memberId)` → `List<NotificationRow>`(생성 순), 전체는 `notificationRows()`. 한 행은 `memberId()`, `type()`, `channel()`, `recipient()`, `title()`, `status()`, `attempts()`, `lastError()`, `dedupKey()`, `sentAt()`, `nextAttemptAt()`이다. 개수는 `.size()`이고, 이벤트별 개수는 `dedupKey()`가 `eventId + ":"`로 시작하는 행을 `stream().filter`로 센다.
- 보낸 메일: `fakeMailSender.sent()` → `List<SentMail>`(`SentMail(String to, String title, String body)`). 실패시키기는 `fakeMailSender.failNext(int times)`. 기록은 `@AfterEach`가 `reset()`으로 비운다.
- 재시도 시각 당기기: `setNotificationsDue()`.
- 알림이 만들어졌는지 기다릴 때는 `Eventually.await(Duration.ofSeconds(5), ...)` 안에서 `notificationRows().size()`를 읽는다.

#### `notification/application/NotificationComposerTest`

DB 없이 `new NotificationComposer()`로 만든다. 인자는 권장안(`compose(이벤트)`, 가게 주인이 필요한 이벤트는 `compose(이벤트, 가게 주인 정보)`)이다. 결과 `List<NotificationDraft>`의 `memberId`, `type`, `title`을 아래 값과 `containsExactlyInAnyOrder`로 비교한다. 제목은 매핑 표의 "제목 예"를 그대로 쓰고(숫자에 천 단위 구분 기호를 쓰지 않는다. 서식이 규격에 없어 정한 값이다. 확인 필요), `body`는 본문 형식이 규격에 없어 `isNotBlank()`만 확인한다.

공통 값: `buyerId`, `ownerA`, `ownerB`, `shopA`, `shopB`, `orderId`는 서로 다른 임의 UUID.

| 입력 | 기대하는 draft |
|---|---|
| `MemberRegisteredEvent(buyerId, "a@test.com", "nick")` | 1개: `buyerId`, `WELCOME`, `[MyRoutine] 가입을 환영합니다` |
| `OrderPaidEvent(orderId, buyerId, 3000, [PaidShopOrder(so1, shopA, [PaidLine(l1, p1, "상품A", 1), PaidLine(l2, p2, "상품B", 2)])])`, 가게 주인 `{shopA → ownerA}` | 2개: (`buyerId`, `ORDER_PAID`, `주문 결제가 완료되었습니다 (3000원)`), (`ownerA`, `SHOP_ORDER_RECEIVED`, `새 주문이 들어왔습니다 (상품 2개)`). 개수는 품목 수라 수량 합 3이 아니다 |
| `OrderPaidEvent`에 가게주문 둘: `so1`(`shopA`, 품목 1개), `so2`(`shopB`, 품목 3개). `totalAmount` 5000. 가게 주인 `{shopA → ownerA, shopB → ownerB}` | 3개: (`buyerId`, `ORDER_PAID`, `주문 결제가 완료되었습니다 (5000원)`), (`ownerA`, `SHOP_ORDER_RECEIVED`, `새 주문이 들어왔습니다 (상품 1개)`), (`ownerB`, `SHOP_ORDER_RECEIVED`, `새 주문이 들어왔습니다 (상품 3개)`) |
| `OrderExpiredEvent(orderId, buyerId, "EXPIRED")` | 1개: `buyerId`, `ORDER_CLOSED`, `주문이 만료되었습니다` |
| `OrderExpiredEvent(orderId, buyerId, "PAYMENT_FAILED")` | 1개: `buyerId`, `ORDER_CLOSED`, `결제에 실패했습니다` |
| `ShopOrderShippedEvent(so1, orderId, buyerId, "CJ대한통운", "1234567890")` | 1개: `buyerId`, `SHIPPED`, `상품이 발송되었습니다 (CJ대한통운 1234567890)` |
| `ShopOrderDeliveredEvent(so1, orderId, buyerId)` | 1개: `buyerId`, `DELIVERED`, `배송이 완료되었습니다` |
| `OrderLineRefundedEvent(refundId, lineId, orderId, buyerId, 1000)` | 1개: `buyerId`, `REFUNDED`, `1000원이 환불되었습니다` |
| `SettlementPaidEvent(settlementId, shopA, 14250, 2026-09-01, 2026-09-30)`, 가게 주인 `ownerA` | 1개: `ownerA`, `SETTLEMENT_PAID`, `2026-09-01~2026-09-30 정산금 14250원이 지급되었습니다` |

#### `notification/application/NotificationFlowTest`

공통 준비: 판매자 `seller1@test.com`(가게 1 `openShop(seller1, "1111111111")`, `product1` = `registerProduct(seller1, shop1, 1000, 10)`), 판매자 `seller2@test.com`(가게 2 `"2222222222"`, `product2` 2000원), 구매자 `buyer@test.com`. 주문은 `createPendingOrder(buyerId, product1Id, product2Id)`(합계 3,000원, 가게주문 둘). 준비 끝에 `OutboxAssert.clear()`(가입·개설 이벤트가 알림 행을 섞지 않게 한다).

**1. 결제 승인에서 발송까지** — 동작 1: `confirmPayment(buyerId, orderId)`(가짜 PG 기본 승인) → `outboxRelay.relayOnce()`.
- `relayOnce()` 반환값 1(`order-paid` 한 건).
- `Eventually`(5초) 안에서 `notificationRows().size()` = 3. 이후 읽을 때도 3이다.
- 세 행(모두 `status()` = `PENDING`):

| `memberId()` | `type()` | `recipient()` | `title()` |
|---|---|---|---|
| buyerId | `ORDER_PAID` | `buyer@test.com` | `주문 결제가 완료되었습니다 (3000원)` |
| seller1 | `SHOP_ORDER_RECEIVED` | `seller1@test.com` | `새 주문이 들어왔습니다 (상품 1개)` |
| seller2 | `SHOP_ORDER_RECEIVED` | `seller2@test.com` | `새 주문이 들어왔습니다 (상품 1개)` |

- `dedupKey()` = `{order-paid 이벤트의 eventId}:{memberId()}:{type()}`(eventId는 `OutboxAssert.rows("order.order-paid.v1")`의 `id()`).
- 동작 2: `notificationDispatcher.dispatchBatch()`.
- 반환값 3. `notificationRows()`의 세 행 모두 `status()` = `SENT`.
- `fakeMailSender.sent()` 정확히 3건이고 (`to`, `title`)의 집합이 위 표의 (`recipient`, `title`)과 같다. 각 `body`는 비어 있지 않다.
- `dispatchBatch()`를 한 번 더 → 반환값 0, `sent()` 3건 그대로(보낸 알림을 다시 보내지 않는다).

**2. 같은 이벤트를 두 번 받아도 알림은 그대로** — 준비: 1번의 동작 1까지 해서 알림 3행(`eventId1`)이 생긴 상태. `@MockitoSpyBean NotificationService`. 동작: 릴레이가 같은 행을 다시 발행한 상황을 만든다. JDBC `UPDATE common.outbox_event SET status = 'READY', next_attempt_at = now() WHERE topic = 'order.order-paid.v1'` → `relayOnce()`(반환값 1, 같은 `eventId1`·같은 내용의 메시지가 한 번 더 나간다). 이어서 센티널로 `TestKafka.send(OrderTopics.ORDER_PAID, orderId.toString(), eventId2, 다른 주문의 OrderPaidEvent)`(같은 키, 가게주문은 `shop1`의 1개).
- `Eventually`(5초) 안에서 `dedupKey()`가 `eventId2:`로 시작하는 행이 2개(구매자 `ORDER_PAID`, seller1 `SHOP_ORDER_RECEIVED`).
- `dedupKey()`가 `eventId1:`로 시작하는 행은 3개 그대로.
- `verify(notificationService, times(1)).enqueue(eq(eventId1), anyList())`.

**3. 메일 발송이 5번 실패하면 FAILED** — 준비(1번의 주문 대신 알림 한 건짜리): `OutboxAssert.clear()` → `memberId = signup("welcome@test.com")` → `relayOnce()` → `Eventually` 안에서 `notificationRowsOf(memberId)`가 1개(`type()` = `WELCOME`, `status()` = `PENDING`). `fakeMailSender.failNext(5)`. 동작: 아래를 `i = 1..5`로 반복한다. `dispatchBatch()` 호출 → 확인(`n = notificationRowsOf(memberId).get(0)`) → `setNotificationsDue()`.
- 호출 `i` 뒤: `n.attempts()` = `i`, `n.lastError()` not null(길이 500 이하), `n.sentAt()` null.
- `i` < 5이면 `n.status()` = `PENDING`, `i` = 5이면 `n.status()` = `FAILED`.
- 1번째 호출 직후(`setNotificationsDue()` 전)에는 `n.nextAttemptAt()`이 `Instant.now().plus(4분)`보다 뒤이고 `Instant.now().plus(6분)`보다 앞이다(지금 + 5분 안팎). 시각을 당기지 않고 바로 `dispatchBatch()`를 한 번 더 부르면 반환값 0, `attempts()` = 1 그대로(5분 전에는 다시 시도하지 않는다).
- 5번째 뒤 `setNotificationsDue()`를 하고 `dispatchBatch()`를 한 번 더 부르면 반환값 0, `attempts()` = 5, `status()` = `FAILED` 그대로.

**4. 발송 실패는 주문·결제에 영향이 없다** — 준비: 1번의 동작 1까지(알림 3행). `fakeMailSender.failNext(15)`. 동작: 3번과 같은 방법으로 `dispatchBatch()`를 5번 부른다(3행이라 호출마다 3번의 실패를 쓴다. 세 알림이 `FAILED`가 되는 것은 3번이 확인한다). `s = snapshotOf(orderId)`.
- `s.status()` = `PAID`, `s.payment().status()` = `APPROVED`.

**5. 한 판매자의 가게 둘이면 가게 주인 알림은 1건** — 준비: 판매자 `seller1`이 가게 하나를 더 연다(`openShop(seller1, "3333333333")`, `product3` 3000원). 주문은 `createPendingOrder(buyerId, product1Id, product3Id)`(같은 판매자의 가게 둘, 합계 4,000원) → `OutboxAssert.clear()`. 동작: `confirmPayment` → `relayOnce()`.
- `Eventually` 안에서 `notificationRows()`가 정확히 2개: (`memberId()` = buyerId, `type()` = `ORDER_PAID`), (seller1, `SHOP_ORDER_RECEIVED`). 가게가 둘이어도 `dedupKey()` = `eventId:seller1:SHOP_ORDER_RECEIVED`가 같아 두 번째 INSERT가 건너뛰어진다.

**6. 탈퇴 회원은 건너뛰고 나머지는 만든다** — 준비: 1번의 주문 + 동작 전에 JDBC `UPDATE member.member SET status = 'WITHDRAWN' WHERE id = seller2`. 동작: `confirmPayment` → `relayOnce()`.
- `Eventually` 안에서 `notificationRows()`가 정확히 2개: (`memberId()` = buyerId, `type()` = `ORDER_PAID`), (seller1, `SHOP_ORDER_RECEIVED`).

**테스트 준비**

**이 단계에서 추가하는 헬퍼** (`support/`. 이름·인자는 권장안이라 쓰다 바꿔도 된다)

| 대상 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `FakeMailSender`, `SentMail` record | 위 4) | 3-5 `NotificationFlowTest` |
| `IntegrationTestSupport`의 `@AfterEach` | `fakeMailSender.reset()` 호출 추가 | 모든 테스트 |
| `notificationRowsOf(UUID memberId)` → `List<NotificationRow>` | `TestFixtures`. JDBC `SELECT member_id, type, channel, recipient, title, status, attempts, last_error, dedup_key, sent_at, next_attempt_at FROM notification.notification WHERE member_id = ? ORDER BY created_at, id`. `NotificationRow`(`support/`의 record) 필드: `memberId`(UUID), `type`·`channel`·`recipient`·`title`·`status`·`lastError`·`dedupKey`(String, `lastError`는 없으면 null), `attempts`(int), `sentAt`·`nextAttemptAt`(`Instant`, `sentAt`은 없으면 null) | 3-5 `NotificationFlowTest`, 5-4 |
| `notificationRows()` → `List<NotificationRow>` | 위와 같은 컬럼·변환으로 `WHERE`와 회원 조건 없이 전체 | `NotificationFlowTest` 1·2·5·6번 |
| `setNotificationsDue()` | JDBC `UPDATE notification.notification SET next_attempt_at = now() - interval '1 second'` | `NotificationFlowTest` 3·4번 |
| `OrderSnapshot`에 주문 `status` 추가 | 2-4 표의 `OrderSnapshot` 필드 추가 중 이 단계가 처음 쓰는 `status`만 더한다(`orders.orders.status`, `WHERE id = ?`) | `NotificationFlowTest` 4번 |

이미 있는 헬퍼: `signup`, `openShop`, `registerProduct`, `createPendingOrder`, `confirmPayment`, `stockOf`, 2-8a의 `snapshotOf`, 3-1의 `Eventually`·`OutboxAssert`, 3-2의 `TestKafka.send`·`processedCountOf`.

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

읽는 방법은 3-1~3-5와 같다. 이 단계에서 더하는 것은 다음과 같다.
- 응답: 거부 응답의 이유 목록은 `$.details.reasons`. 목록을 통째로 비교할 때는 `JsonPath.read(body, "$.details.reasons")`로 `List<String>`을 읽는다.
- 회원: `MemberSnapshot s = memberSnapshotOf(memberId)` 한 번으로 읽는다. `s.status()`, `s.email()`, `s.nickname()`, `s.name()`, `s.phone()`, `s.passwordHash()`, `s.withdrawnAt()`, 배송지 수는 `s.addresses().size()`. 값 전체를 비교할 때는 `MemberSnapshot` record의 `isEqualTo`를 쓴다.
- 장바구니: 2-1의 `cartSnapshotOf(memberId)` → `CartSnapshot`의 `cartCount()`(장바구니 행 수), `itemCount()`(항목 수).
- 탈퇴 요청: `DELETE /api/members/me`, `Authorization: Bearer {회원 토큰}`(`Idempotency-Key` 없음).

#### `member/domain/MemberTest`

준비: `Member member = Member.signUp("test@test.com", "pass1234", "test", "테스트")` → `member.changeProfile("test", "테스트", "010-1234-5678")`(전화번호를 채운다). `Instant now = Instant.parse("2026-10-18T00:00:00Z")`. `id = member.getId()`.

**1. 탈퇴하면 개인정보가 마스킹된다** — 동작: `member.withdraw(now)`.
- `getStatus()` = `WITHDRAWN`.
- `getEmail()` = `"withdrawn+" + id + "@deleted.invalid"`.
- `getNickname()` = `"탈퇴-" + id.toString().substring(0, 8)`.
- `getName()` = `탈퇴회원`.
- `getPhone()` = null, `getPasswordHash()` = null.
- `getWithdrawnAt()` = `now`.

**2. BANNED 회원은 탈퇴할 수 없고 값이 바뀌지 않는다** — 준비: `ReflectionTestUtils.setField(member, "status", MemberStatus.BANNED)`. 동작: `member.withdraw(now)`.
- `BusinessException`, `getErrorCode()` = `CommonErrorCode.INVALID_STATE_TRANSITION`.
- `getEmail()` = `test@test.com`, `getNickname()` = `test`, `getName()` = `테스트`, `getPhone()` = `010-1234-5678`, `getWithdrawnAt()` = null, `getStatus()` = `BANNED`(마스킹이 시작되지 않았다).

**3. 이미 탈퇴한 회원의 두 번째 탈퇴** — 동작: `member.withdraw(now)` 뒤 `member.withdraw(now.plusSeconds(60))`.
- 두 번째가 `BusinessException`, `INVALID_STATE_TRANSITION`.
- `getWithdrawnAt()` = 첫 번째 `now` 그대로.

#### `member/web/WithdrawTest`

공통 준비: 탈퇴할 회원 `member@test.com`(`signup`, 토큰은 `login`, 닉네임 `member`), 판매자 `seller@test.com`과 가게 `openShop(sellerId, "1111111111")`, 상품 `product1`(`registerProduct(sellerId, shopId, 1000, 10)`). 정산 월은 `YearMonth.of(2026, 9)`, 미정산 품목의 확정 시각은 `Instant.parse("2026-09-15T03:00:00Z")`. 거부 케이스의 공통 확인: 탈퇴 요청 전에 `before = memberSnapshotOf(memberId)`를 읽어 두고, 요청 뒤 `memberSnapshotOf(memberId)`가 `before`와 같다(`isEqualTo`. 이메일·닉네임·이름·전화번호·비밀번호 해시·배송지가 그대로다). `OutboxAssert.rows("member.member-withdrawn.v1")`가 비어 있다. 성공(204) 케이스의 공통 확인: `memberSnapshotOf(memberId).status()` = `WITHDRAWN`.

**1. 운영 중인 가게가 있으면 거부** — 준비: 회원이 `openShop(memberId, "2222222222")`. 동작: 탈퇴 요청.
- 응답 422, `$.code` = `MEMBER_HAS_ACTIVE_RESOURCES`, `$.details.reasons` = `["ACTIVE_SHOP"]`.
- 거부 공통 확인.

**2. 진행 중인 주문이 있으면 거부, 끝난 주문은 막지 않는다** — 준비: 행마다 새 회원으로 아래 상태를 만든 뒤 탈퇴 요청. 구매자는 탈퇴하는 회원 본인이다.

| 준비 | 응답 | 확인할 것 |
|---|---|---|
| `createPendingOrder(memberId, product1Id)` (주문 `PENDING_PAYMENT`) | 422 | `$.details.reasons` = `["ACTIVE_ORDER"]`, 거부 공통 확인 |
| `pg.on("/v1/payments/confirm", Behavior.delay(1000))` → `createPendingOrder` → `confirmPayment`(202, `PAYMENT_IN_PROGRESS`) | 422 | 위와 같음 |
| `createPaidOrder(memberId, product1Id)` (가게주문 `PAID`) | 422 | 위와 같음 |
| 위 + `shipShopOrder(sellerId, shopId, shopOrderId)` (`SHIPPED`) | 422 | 위와 같음 |
| 위 + `deliverShopOrder(sellerId, shopId, shopOrderId)` (`DELIVERED`) | 422 | 위와 같음 |
| `createPendingOrder` → `POST /api/orders/{orderId}/cancel` (200, `CANCELLED`) | 204 | 성공 공통 확인 |
| `createPaidOrder` → JDBC `UPDATE orders.shop_order SET status = 'COMPLETED' WHERE id = ?` | 204 | 성공 공통 확인 |
| `createPaidOrder` → JDBC `UPDATE orders.shop_order SET status = 'CANCELLED' WHERE id = ?` | 204 | 성공 공통 확인 |

**3. 진행 중인 환불이 있으면 거부** — 준비: `createPaidOrder(memberId, product1Id)` → `pg.on("/v1/payments/" + paymentKey, Behavior.delay(1000))` → 품목 취소 요청(HTTP) → 202, `$.status` = `APPROVED` → JDBC `UPDATE orders.shop_order SET status = 'COMPLETED'`(가게주문 조건만 걷어 환불만 남긴다). 동작: 탈퇴 요청.
- 응답 422, `$.details.reasons` = `["ACTIVE_ORDER"]`, 거부 공통 확인.
- 이어서 JDBC `UPDATE orders.refund SET status = 'COMPLETED' WHERE id = ?`(`id`는 202 응답의 `$.refundId`) 뒤 탈퇴 요청 → 204.

**4. 정산이 끝나지 않았으면 거부** — 준비: 행마다 회원이 가게를 열고 `closeShop(shopId)`로 CLOSED로 만든다(ACTIVE 가게가 아니라 `ACTIVE_SHOP`은 해당 없다). 동작: 탈퇴 요청.

| 준비 | 응답 | 확인할 것 |
|---|---|---|
| 그 가게에 `insertSettlementItem(shopId, 1000, 확정 시각)` (미정산 품목) | 422 | `$.details.reasons` = `["PENDING_SETTLEMENT"]`, 거부 공통 확인 |
| 위 + `fakePayoutGateway.failNext(1)` → `settlementService.run(YearMonth.of(2026, 9))` (정산 `PAYOUT_FAILED`, 미정산 품목 0) | 422 | 위와 같음 |
| 위 + `settlementService.run(YearMonth.of(2026, 9))` 한 번 더 (정산 `PAID`) | 204 | 성공 공통 확인 |
| 품목 없이 CLOSED 가게만 | 204 | 성공 공통 확인 |

**5. 이유가 둘 이상이면 모두 담는다** — 준비: 회원이 ACTIVE 가게 `openShop(memberId, "2222222222")`와 CLOSED 가게 `"3333333333"`(`closeShop` + 미정산 품목 `insertSettlementItem`)를 갖고, `createPendingOrder(memberId, product1Id)`로 결제 대기 주문도 있다. 동작: 탈퇴 요청.
- 응답 422, `$.code` = `MEMBER_HAS_ACTIVE_RESOURCES`.
- `$.details.reasons`: 첫 번째 원소가 `ACTIVE_SHOP`이고, 세 값은 `ACTIVE_SHOP`, `ACTIVE_ORDER`, `PENDING_SETTLEMENT`가 하나씩이다(뒤 두 개의 순서는 정해져 있지 않다).
- 거부 공통 확인.

**6. 탈퇴 성공, 로그인 불가, 같은 이메일로 재가입** — 준비: 회원에게 `address(memberId)`를 두 번(배송지 2개), `addToCart(memberId, product1Id, 2)`, 조건 위반 없음. `OutboxAssert.clear()`. 동작 1: 탈퇴 요청.
- 응답 204, 본문 없음.
- 탈퇴 뒤 `s = memberSnapshotOf(memberId)`: `email()` = `withdrawn+{memberId}@deleted.invalid`, `nickname()` = `탈퇴-{memberId 앞 8자}`, `name()` = `탈퇴회원`, `phone()` null, `passwordHash()` null, `withdrawnAt()` not null, `addresses().size()` = 0.
- `rows("member.member-withdrawn.v1")` 정확히 1건: `aggregateType()` = `MEMBER`, `aggregateId()` = memberId, payload `memberId` = memberId 문자열.
- 동작 2: `POST /api/auth/login` 본문 `{"email":"member@test.com","password":"pass1234"}`.
- 응답 401, `$.code` = `LOGIN_FAILED`.
- 동작 3: `POST /api/auth/signup` 본문 `{"email":"member@test.com","password":"pass1234","nickname":"member","name":"테스터"}`.
- 응답 201. `SELECT COUNT(*) FROM member.member WHERE email = 'member@test.com'` = 1(새 회원).

**7. 탈퇴 뒤 장바구니가 비워진다** — 준비: 6번의 회원(장바구니 항목 1개)과 다른 구매자 `other@test.com`(`addToCart(otherId, product1Id, 1)`). 탈퇴 요청(204) 뒤 `eventId` = `rows("member.member-withdrawn.v1")`의 `id()`. 동작: `outboxRelay.relayOnce()`.
- 반환값 1.
- `Eventually`(5초) 안에서 `processedCountOf("order.member-withdrawn", eventId)` = 1.
- `cartSnapshotOf(memberId)`: `cartCount()` = 0, `itemCount()` = 0(항목과 장바구니 행이 모두 지워졌다).
- 다른 구매자는 그대로: `cartSnapshotOf(otherId)`: `cartCount()` = 1, `itemCount()` = 1.

**8. 장바구니가 없는 회원의 탈퇴 이벤트** — 준비: 장바구니를 만든 적 없는 회원. 탈퇴 요청(204) → `relayOnce()` = 1.
- `Eventually`(5초) 안에서 `processedCountOf("order.member-withdrawn", 그 이벤트의 eventId)` = 1(예외 없이 끝났다는 신호다. 실패했다면 이 행이 롤백돼 남지 않는다).

**9. 같은 회원의 탈퇴 요청 두 건 동시** — 준비: 조건 위반이 없는 회원. 동작: `ConcurrencyRunner.run(2, ...)`로 같은 토큰의 탈퇴 요청을 동시에 보내 HTTP 상태 코드를 모은다.
- 상태 코드: 하나는 204, 하나는 409이고 409 쪽 `$.code` = `INVALID_STATE_TRANSITION`(잠금 조회로 줄 서서, 뒤의 요청은 이미 `WITHDRAWN`인 회원을 읽는다).
- `rows("member.member-withdrawn.v1")` 정확히 1건.
- `memberSnapshotOf(memberId)`: `status()` = `WITHDRAWN`, `email()` = `withdrawn+{memberId}@deleted.invalid` 한 형태(두 번 마스킹되지 않았다).

**테스트 준비**

**이 단계에서 추가하는 헬퍼** (`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다)

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `memberSnapshotOf(memberId)`에 배송지 추가 | 3-1에서 추가한 `MemberSnapshot`에 1-5 표에 정의된 `addresses`(`List<AddressState(id, recipient, address2, isDefault)>`)를 더한다(읽는 쿼리는 1-5 표). 처음 쓰는 미구현 단계가 이 단계다 | `WithdrawTest` 배송지 수·삭제 확인 |
| `cartSnapshotOf(memberId)` → `CartSnapshot` | 2-1 표에 정의된 헬퍼다(읽는 쿼리·필드는 그 표). 처음 쓰는 미구현 단계인 이 단계에서 `TestFixtures`에 추가한다 | `WithdrawTest` 장바구니 삭제 확인 |

이미 있는 헬퍼: 3-4의 `insertSettlementItem`·`FakePayoutGateway`, `signup`, `login`, `openShop`, `closeShop`, `registerProduct`, `address`, `addToCart`, `createPendingOrder`, `confirmPayment`, `createPaidOrder`, `shopOrderIdOf`, `shipShopOrder`, `deliverShopOrder`, `orderLineIdOf`, `ConcurrencyRunner`, 3-1의 `memberSnapshotOf`(`role`·`status`·`email`·`withdrawnAt`)·`countOf`·`OutboxAssert`·`Eventually`, 3-2의 `processedCountOf`.

**리뷰 때 물어볼 것**
- 탈퇴 조건 검사와 탈퇴 처리 사이에 새 주문이 들어오면?
- 가게 조건만 member가 직접 확인하는 이유는? (순환 의존)

---

## Part 3 완료
- [ ] `v0.3.0` 릴리스, Part 4 문서 다듬기
