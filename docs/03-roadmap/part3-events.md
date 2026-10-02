# Part 3. 이벤트

> **끝나면**: 가게 개설·폐업에 따른 역할 변경, 정산, 알림 같은 **후속 작업**이 비동기로 처리된다. 서버가 죽거나 Kafka가 멈춰도 이벤트가 유실되지 않고, 두 번 전달돼도 결과는 한 번이다.
> **인프라 추가**: **Kafka**
> **릴리스**: `v0.3.0`
> Part 3 시작 전에 이 문서를 다시 다듬는다.

| 단계 | 제목 | 크기 |
|---|---|---|
| 3-1 | 이벤트가 필요한 이유 · Kafka · Outbox (가게 개설 → SELLER 역할) | L |
| 3-2 | 멱등 소비 · 재시도 · DLT · 가게 폐업 | M |
| 3-3 | 기존 흐름에 이벤트 발행 붙이기 | M |
| 3-4 | 정산: 적재 · 정산 잡 · 지급 | L |
| 3-5 | 알림 | M |
| 3-6 | 회원 탈퇴 | M |

---

## 3-1. 이벤트가 필요한 이유 · Kafka · Outbox (L)

**목표**: 가게를 열면 회원에게 SELLER 역할이 붙는다. 이 연결을 **Outbox + Kafka**로 만든다.

**왜 지금**: 처음으로 "A 모듈의 변화에 B 모듈이 반응해야 하는데, A가 B를 직접 부르면 곤란한" 상황이 나온다.
- shop이 member를 직접 호출하면: 의존 방향이 늘어나고(member는 이미 shop을 조회함 → 순환), 역할 변경이 실패하면 가게 개설까지 실패한다.
- `@TransactionalEventListener(AFTER_COMMIT)`로 메모리 안에서 알리면: 커밋 직후 서버가 죽으면 알림이 사라진다(As-Is ORD-09, SHOP-05).
- **Outbox**: 가게 저장과 "보낼 이벤트" 저장을 **같은 트랜잭션**에 넣고, 별도 릴레이가 Kafka로 보낸다. 커밋된 변경의 이벤트는 사라지지 않는다.

**새로 등장**

| 개념·도구 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| Kafka | 이벤트를 저장하고 구독자에게 전달하는 브로커. 지금은 같은 앱 안의 다른 모듈이 구독한다 | [ADR-003](../adr/ADR-003-outbox-kafka.md) |
| Transactional Outbox | 상태 변경 + 이벤트 행을 한 트랜잭션에 저장 → 릴레이가 발행 | [이벤트 카탈로그 §1](../02-design/05-events.md) |
| 리스(lease) 선점 | 릴레이가 행을 `PROCESSING + locked_until`로 표시하고 가져간다. 릴레이가 죽으면 시간이 지나 다시 READY로 | |
| 이벤트 봉투 | `eventId, eventType, occurredAt, aggregateId, version, traceId, payload` | |

**할 일**
1. docker-compose에 Kafka(KRaft, 단일 브로커), `IntegrationTestSupport`에 Kafka 컨테이너 추가
2. common: `outbox_event` 마이그레이션, `OutboxPublisher.publish(topic, key, event)`, `EventEnvelope`(traceId는 MDC에서)
3. 릴레이: 잡 락 → 선점(짧은 트랜잭션) → 트랜잭션 밖에서 `send().get(timeout)` → SENT / 실패 시 백오프 / 최대 시도 초과 DEAD / 리스 만료 회수
4. `shop.shop-opened.v1` 발행(가게 개설 트랜잭션 안에서), member consumer: roles에 SELLER 추가(이미 있으면 그대로)
5. consumer는 처리 전에 봉투의 traceId를 MDC에 넣는다

**완료 확인**
- [ ] 가게 개설 → 잠시 후 회원 roles에 SELLER
- [ ] 가게 개설 트랜잭션이 롤백되면 이벤트도 없다
- [ ] **Kafka를 멈춘 상태에서 개설 → outbox READY 유지 → Kafka 재시작 후 발행·소비** (Testcontainers pause/unpause)
- [ ] PROCESSING으로 멈춘 행이 리스 만료 후 다시 발행된다
- [ ] 개설 요청 로그와 consumer 로그의 traceId가 같다

**리뷰 때 물어볼 것**
- Kafka에 보내고 SENT로 바꾸기 전에 죽으면? 그래서 consumer가 반드시 해야 하는 것은?
- 폴링 방식 Outbox와 CDC(Debezium)의 차이는?
- 원 프로젝트에선 이 흐름이 Saga였는데 왜 단순해졌나? (소유권 인가, ADR-006)

---

## 3-2. 멱등 소비 · 재시도 · DLT · 가게 폐업 (M)

**목표**
- consumer가 같은 이벤트를 두 번 받아도 한 번만 처리한다. 계속 실패하는 메시지는 DLT에 모인다.
- 가게 폐업: 진행 중인 주문이 없어야 폐업할 수 있고, 폐업하면 상품이 단종되고, 마지막 가게였다면 SELLER가 회수된다.

**새로 등장**

| 개념 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| Inbox (멱등 소비) | `processed_message(consumer, eventId)`에 기록하고 이미 있으면 건너뛴다 | 개발 가이드 §9.2 |
| 재시도 · DLT | 일시적 오류는 3번 재시도, 그래도 실패하면 `{topic}.dlt`. 역직렬화 오류는 바로 DLT | |
| 의존 역전 | 폐업 조건(진행 중 주문)은 order 데이터인데 shop → order 의존은 순환. shop이 `ShopClosePrecondition` 인터페이스를 정의하고 order가 구현 | 개발 가이드 §4.3 |

**할 일**
1. `processed_message` 마이그레이션, `InboxGuard`, Kafka 공통 에러 핸들러(재시도 + DLT)
2. 3-1 consumer에 InboxGuard 적용
3. `ShopClosePrecondition`(shop.api), order 구현(PAID·SHIPPED·DELIVERED 가게주문이 있으면 위반)
4. `DELETE /api/shops/{id}`: advisory lock(같은 회원 개설·폐업 직렬화) → 조건 확인 → CLOSED → `shop-closed` 발행
5. consumer: member(ShopApi로 **활성 가게 수를 다시 조회**해서 0이면 SELLER 회수), product(가게 상품 전부 DISCONTINUED)

**완료 확인**
- [ ] 같은 이벤트 두 번 소비 → 처리 1번
- [ ] 계속 실패하는 핸들러 → DLT 토픽에 메시지
- [ ] 가게 2개 중 1개 폐업 → SELLER 유지, 나머지 폐업 → 회수
- [ ] **같은 회원의 가게 2개를 동시에 폐업 → 최종적으로 SELLER 회수** (원 프로젝트에서 겪은 동시성 문제)
- [ ] 진행 중 주문이 있는 가게 폐업 → 422
- [ ] 폐업 → 상품 전부 DISCONTINUED

**리뷰 때 물어볼 것**
- member가 이벤트의 "남은 가게 수"를 믿지 않고 다시 조회하는 이유는?
- 의존 역전을 쓰지 않으면 어떤 구조가 되나?

---

## 3-3. 기존 흐름에 이벤트 발행 붙이기 (M)

**목표**: Part 1~2에서 만든 흐름에 이벤트를 발행한다. 구독자는 이후 단계(정산, 알림, 검색, 추천)에서 붙는다.

**할 일**: [이벤트 카탈로그](../02-design/05-events.md)대로, 상태 변경과 같은 트랜잭션에서 발행

| 흐름 | 이벤트 |
|---|---|
| 가입 (1-3) | `member.member-registered.v1` |
| 상품 등록·수정·상태 (1-7, 1-8) | `product.product-upserted.v1`, `product-price-changed`, `product-discontinued` |
| 결제 완료 / 만료·실패 (2-4, 2-6, 2-7) | `order.order-paid.v1`, `order-expired` |
| 발송 / 배송완료 (2-10) | `order.shop-order-shipped.v1`, `shop-order-delivered` |
| 환불 완료 (2-11) | `order.order-line-refunded.v1` |
| 구매확정 (2-13) | `order.order-line-confirmed.v1` |
| 충전 완료 (2-9) | `payment.wallet-charge-completed.v1` |

**완료 확인**
- [ ] 각 흐름에서 outbox에 이벤트가 정확히 1건
- [ ] 각 흐름이 실패(롤백)하면 이벤트가 없다
- [ ] 토픽명은 상수로, 카탈로그 문서와 일치

**리뷰 때 물어볼 것**
- 이벤트에 어떤 필드를 넣을지 어떻게 정했나? (너무 많으면? 너무 적으면?)

---

## 3-4. 정산: 적재 · 정산 잡 · 지급 (L)
> PR 둘: **3-4a** 적재 + 정산 잡, **3-4b** 지급 + 조회

**목표**: 구매확정 품목을 가게별로 월 1회 정산하고, 수수료를 뺀 금액을 판매자 예치금으로 입금한다. 정확히 한 번.

**왜 지금**: 3-3의 `order-line-confirmed` 이벤트가 생겼다.

**새로 등장**

| 개념 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| keyset 배치 | 가게 ID 순서로 500개씩. offset 페이징으로 상태를 바꾸며 읽으면 누락된다(As-Is BAT-02) | [ADR-009](../adr/ADR-009-settlement.md) |
| 정산 1회 보장 | `settlement(shop_id, period_start)` unique + 품목에 정산 ID 할당 | |

**할 일**
1. settlement 모듈: `settlement_item`(order_line_id unique), consumer(`ON CONFLICT DO NOTHING`)
2. `settlement` 마이그레이션, 정산 잡(매월 5일, 잡 락, 관리자 수동 실행): 가게별 트랜잭션에서 정산 INSERT → 미정산 품목에 정산 ID 할당 → **할당된 행 기준으로** 합계·수수료(내림, OPEN-06)·정산액
3. 지급: 정산 트랜잭션과 분리, 판매자(가게 주인) 지갑에 `deposit(SETTLEMENT, settlementId)`, 실패 시 PAYOUT_FAILED → 다음 실행에서 재시도
4. `GET /api/shops/{shopId}/settlements`, 상세 품목

**완료 확인**
- [ ] 같은 확정 이벤트 두 번 → 정산 대상 1행
- [ ] **가게 1,200개 → 모든 가게 정산, 누락 0**
- [ ] 잡 두 번 실행 → 정산 건수 동일
- [ ] 지급 두 번 시도 → 잔액 한 번만 증가
- [ ] 기간 경계(전월 말일 23:59:59 / 당월 1일 00:00:00) 귀속 정확

**리뷰 때 물어볼 것**
- offset 페이징으로 상태를 바꾸며 읽으면 왜 누락되나? (원 프로젝트에서 실제로 있던 결함)
- 정산과 지급을 왜 분리했나?

---

## 3-5. 알림 (M)

**목표**: 결제 완료·발송·환불·정산 지급 등에서 이메일 알림을 보낸다. 실패하면 재시도하고, 끝내 실패한 건은 보관해 나중에 다시 보낼 수 있다. 알림이 실패해도 주문·결제는 영향받지 않는다.

**할 일**
1. notification 모듈: `notification` 마이그레이션(`dedup_key` unique)
2. 이벤트 consumer들: 이벤트 → 수신자·문구 결정 → notification 저장(PENDING)
3. 발송 잡: PENDING을 읽어 `MailSender` 포트로 발송(local은 로그 출력 구현, SMTP는 선택) → SENT / 실패 시 백오프 → 최대 시도 초과 FAILED
4. consumer에서 바로 메일을 보내지 않는다(저장과 발송 분리)

**완료 확인**
- [ ] 같은 이벤트 두 번 → 알림 1건
- [ ] 메일 서버 실패 → 재시도 → FAILED
- [ ] 이벤트 유형별 수신자·문구 매핑 단위 테스트

**리뷰 때 물어볼 것**
- 원 프로젝트에서 RabbitMQ를 따로 쓴 이유와, v2에서 Kafka만으로 충분한 이유는? (ADR-003)
- consumer에서 메일을 바로 보내지 않는 이유는?

---

## 3-6. 회원 탈퇴 (M)

**목표**: 진행 중인 주문·구독·예치금 잔액·운영 중인 가게·정산 대기 금액이 없으면 탈퇴할 수 있다. 개인정보는 마스킹된다.

**할 일**
1. `WithdrawalPrecondition`(member.api) — order, wallet, shop, settlement가 구현 (3-2와 같은 의존 역전)
2. `DELETE /api/members/me`: 조건 확인 → 이메일·이름·전화 마스킹, 비밀번호 제거, WITHDRAWN → `member-withdrawn` 발행
3. consumer: order(장바구니 삭제)
4. 재가입 정책 OPEN-01(권장: 마스킹했으니 같은 이메일로 새 가입 가능)

> 탈퇴한 회원의 기존 토큰은 Part 4(4-4 tokenVersion)부터 즉시 막힌다. 그 전까지는 토큰 만료(1시간)까지 유효하다는 한계를 PR에 적는다.

**완료 확인**
- [ ] 조건별 거부 (details에 어떤 조건인지)
- [ ] 탈퇴 → 로그인 불가, 같은 이메일로 재가입 가능
- [ ] member-withdrawn → 장바구니 삭제

**리뷰 때 물어볼 것**
- 탈퇴 조건 검사와 탈퇴 처리 사이에 새 주문이 들어오면?

---

## Part 3 완료
- [ ] `v0.3.0` 릴리스, Part 4 문서 다듬기
