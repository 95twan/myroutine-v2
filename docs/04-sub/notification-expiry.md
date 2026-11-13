# 서브: 때 지난 알림

> 버전 0.1 · 2026-11-13 · 일어난 지 24시간이 지난 진행 알림(가입·결제·주문 닫힘·발송·배송)은 보내지 않고 `EXPIRED`로 남긴다. 환불·정산 지급·제재 알림은 늦어도 보낸다(단계 ID SUB-21)

> **서브**: 로드맵에서 뺀 기능이다. Stage 1(Part 6) 뒤에 한다.
> **따르는 규칙**: 로드맵 Part 1~6의 공통 규칙. 알림 규격의 출발점은 [로드맵 3-5](../03-roadmap/part3-events.md)다.

---

## SUB-21. 때 지난 알림 (S)

**목표**: 이벤트가 일어난 지 24시간이 지난 진행 알림은 메일을 보내지 않고 `EXPIRED`로 남긴다. 구매자가 배송이 끝난 뒤에 "결제가 완료되었습니다" 메일을 받거나, "배송 완료" 뒤에 "발송" 메일을 받는 일을 막는다.

**드러나는 때**: 알림이 일어난 때보다 한참 늦게 발송 단계에 닿는 경로는 넷이다.

| 경로 | 늦어지는 이유 |
|---|---|
| DLT 리플레이 | `NotificationEventConsumer`가 실패해 `.dlt`로 간 이벤트를 관리자가 며칠 뒤 다시 발행하면([6-3](../03-roadmap/part6-ops-wrapup.md)), 그때 알림 행이 생기고 10초 안에 메일이 나간다 |
| outbox `DEAD` 재시도 | 릴레이가 10번 실패한 이벤트를 관리자가 늦게 `READY`로 돌리면 위와 같다 |
| 메일 서버 장애 | 알림 행은 제때 생겼지만 `PENDING`으로 쌓였다가 메일 서버가 살아나는 순간 한꺼번에 나간다 |
| 관리자의 `FAILED` 재시도 | 5번 실패해 `FAILED`가 된 알림을 관리자가 늦게 `PENDING`으로 돌리면 다음 발송에서 나간다 |

### 빈 곳

`NotificationDispatcher.dispatchBatch`는 `PENDING`이고 `next_attempt_at`이 지난 알림을 언제 일어난 일인지 보지 않고 보낸다. 알림 행에는 일이 일어난 시각도 없다. `created_at`은 행을 만든 시각이라 DLT 리플레이로 만든 행이면 리플레이한 시각이 된다.

이벤트 봉투의 `occurredAt`은 outbox 행을 만든 시각, 곧 업무 트랜잭션이 커밋된 시각이다. DLT 리플레이와 outbox 재시도는 같은 봉투를 다시 보내므로 이 값이 바뀌지 않는다.

### 정책

- 유효 시간은 **24시간**이고, 기준 시각은 이벤트 봉투의 `occurredAt`이다. 발송할 때 `now`가 `occurredAt + 24시간`보다 뒤면 만료다.
- 늦으면 지금 상태와 어긋나는 진행 알림만 만료된다. 돈·계정에 관한 알림은 늦어도 여전히 맞는 사실이라 보낸다.

| 만료됨 | 만료되지 않음 |
|---|---|
| `WELCOME`, `ORDER_PAID`, `SHOP_ORDER_RECEIVED`, `ORDER_CLOSED`, `SHIPPED`, `DELIVERED` | `REFUNDED`, `SETTLEMENT_PAID`, `BANNED` |

- 만료는 발송 직전에 판단한다. 알림 행은 그대로 만들고(행을 만들지 않으면 관리자 목록에서도 사라진다), 발송 잡이 `PENDING → EXPIRED`로 바꾸고 메일을 보내지 않는다. 그래서 위 네 경로가 한 규칙으로 막힌다.
- `EXPIRED`는 끝 상태다. 관리자 재시도(`FAILED → PENDING`)는 지금처럼 204로 받지만, 만료된 진행 알림이면 다음 발송이 `EXPIRED`로 바꾼다.
- 만료된 알림은 기존 관리 API `GET /admin/api/notifications?status=EXPIRED`로 본다. `status`가 `NotificationStatus` enum이라 API 코드는 바꾸지 않는다.
- 서브 구독(SUB-3·SUB-4)이 알림 종류를 더할 때 종류마다 만료 여부를 정한다. 아래 `NotificationType`의 생성자 인자라서 정하지 않으면 컴파일되지 않는다.

### 고른 방법

| 방법 | 막는 경로 | 판단 |
|---|---|---|
| 발송할 때 `occurred_at`으로 판단한다(할 일) | 네 경로 모두 | 고른다. 컬럼 하나와 상태 하나가 늘지만 규칙 하나로 다 막고, 만료된 알림이 관리자 목록에 남는다 |
| consumer에서 봉투의 `occurredAt`을 보고 행을 만들지 않는다 | DLT 리플레이, outbox 재시도 | 고르지 않는다. 스키마는 그대로지만 메일 서버 장애와 `FAILED` 재시도는 못 막고, 버린 알림이 로그에만 남는다 |

### 할 일

**1) 마이그레이션** — `db/migration/notification/V{...}__notification_add_occurred_at.sql`

```sql
ALTER TABLE notification.notification ADD COLUMN occurred_at timestamptz;
UPDATE notification.notification SET occurred_at = created_at;
ALTER TABLE notification.notification ALTER COLUMN occurred_at SET NOT NULL;
ALTER TABLE notification.notification DROP CONSTRAINT ck_notification_status;
ALTER TABLE notification.notification ADD CONSTRAINT ck_notification_status CHECK (status IN ('PENDING','SENT','FAILED','EXPIRED'));
```

- 이미 있는 행의 `occurred_at`은 `created_at`으로 채운다. consumer가 제때 처리한 행이면 두 값의 차이는 릴레이·소비 시간(1초 안팎)이다.
- 배포한 뒤 첫 발송에서 24시간이 지난 진행 알림 중 `PENDING`인 것은 `EXPIRED`가 된다. 의도한 동작이다.

**2) notification.domain**

| 클래스 | 규격 |
|---|---|
| `NotificationType` | 생성자 인자 `boolean expires`와 `public boolean expires()`를 더한다. 값은 위 "정책"의 표대로 `WELCOME(true)`, `ORDER_PAID(true)`, `SHOP_ORDER_RECEIVED(true)`, `ORDER_CLOSED(true)`, `SHIPPED(true)`, `DELIVERED(true)`, `REFUNDED(false)`, `SETTLEMENT_PAID(false)`, `BANNED(false)` |
| `NotificationStatus` | `EXPIRED`를 더한다. 허용 전이에 `PENDING → EXPIRED`를 더하고, `EXPIRED → {}`(끝 상태)다. 전이는 `PENDING → {SENT, FAILED, EXPIRED}`, `FAILED → {PENDING}`이 된다 |
| `Notification` | 필드 `Instant occurredAt`(`occurred_at`, `nullable = false`)과 상수 `VALIDITY = Duration.ofHours(24)`를 더한다. `boolean isExpired(Instant now)`: `type.expires() && now.isAfter(occurredAt.plus(VALIDITY))`. `void markExpired()`: `status = status.transitTo(EXPIRED)` |
| `NotificationRepository` | `insertIfAbsent`에 `Instant occurredAt`을 `dedupKey` 뒤에 더한다: `insertIfAbsent(UUID id, UUID memberId, String type, String recipient, String title, String body, String dedupKey, Instant occurredAt, Instant now)`. native INSERT의 컬럼 목록과 `VALUES`에 `occurred_at`·`:occurredAt`을 더한다 |

**3) notification.application**

| 클래스 | 규격 |
|---|---|
| `NotificationService` | `enqueue(UUID eventId, Instant occurredAt, List<NotificationDraft> drafts)`로 바꾸고 `insertIfAbsent`에 `occurredAt`을 넘긴다 |
| `NotificationDispatcher` | `dispatchBatch`의 알림 한 건 처리(바깥 `try` 안)에서 `send` 전에 `notification.isExpired(now)`를 본다. 만료면 짧은 트랜잭션으로 `findById`로 다시 읽어 `markExpired()`하고, `log.info("notification expired notificationId={} type={} occurredAt={}", ...)`를 남긴 뒤 다음 알림으로 넘어간다. 반환값(보낸 건수)에 세지 않는다. 다시 읽어서 기록하는 이유는 `markSent`·`markFailed`와 같다(3-5 `dispatchBatch` 순서) |

**4) notification.infrastructure**

`NotificationEventConsumer`의 메서드 8개가 `notificationService.enqueue(typed.envelope().eventId(), typed.envelope().occurredAt(), drafts)`로 부른다.

**5) 기존 테스트**

- `NotificationFlowTest` 2번의 `then(notificationService).should(times(1)).enqueue(eq(event1Id), anyList())`에 `any(Instant.class)`를 더한다.
- `AdminNotificationTest`·`LogFlowTest`가 JDBC로 넣는 `notification.notification` 행에 `occurred_at`을 더한다(값은 같은 문장의 `created_at`과 같게).
- `NotificationStatusTest`는 `EXPIRED`를 더해 4×4 = 16쌍으로 다시 쓴다(아래 완료 확인).

### 완료 확인

케이스는 [로드맵 README 4.1](../03-roadmap/README.md)의 기준으로 골랐다.

**테스트 준비**

| 대상 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `TestKafka.send(String topic, String key, UUID eventId, Instant occurredAt, Object event)` → `int` | 기존 `send(topic, key, eventId, event)`와 같고, 봉투의 `occurredAt`만 인자로 받는다. 기존 4인자 메서드는 이 메서드에 `Instant.now()`를 넘기게 한다. 리플레이처럼 오래전에 일어난 이벤트를 보낼 때 쓴다 | 아래 9~12번 |
| `NotificationRow`에 `occurredAt` 추가 | `Instant occurredAt`을 더하고, `notificationRowsOf`·`notificationRows`의 `SELECT`에 `occurred_at`을 더한다. `new NotificationRow(...)` 호출도 새 필드에 맞춘다 | 9번 |

시각을 비교할 때는 Postgres `timestamptz`가 마이크로초까지 저장하므로, 보낼 `occurredAt`을 `Instant.now().minus(...).truncatedTo(ChronoUnit.MICROS)`로 만든다.

#### `notification/domain/NotificationStatusTest` (순수 JUnit, `@ParameterizedTest`로 4×4 = 16쌍)

| 케이스 | 확인할 것 |
|---|---|
| 허용 4쌍: `PENDING→SENT`, `PENDING→FAILED`, `PENDING→EXPIRED`, `FAILED→PENDING` | `from.transitTo(to)`의 반환값이 `to`다 |
| 허용 밖 12쌍: 16쌍에서 허용 4쌍을 뺀 것 | `BusinessException`이 던져지고 `getErrorCode()` = `CommonErrorCode.INVALID_STATE_TRANSITION` |

#### `notification/application/NotificationFlowTest`

**9. 리플레이된 이벤트의 알림은 이벤트가 일어난 시각을 저장한다** — 준비: `memberId = signup("welcome@test.com")`, `occurredAt = Instant.now().minus(Duration.ofHours(25)).truncatedTo(ChronoUnit.MICROS)`. 동작: `TestKafka.send(MemberTopics.MEMBER_REGISTERED, memberId.toString(), Ids.newId(), occurredAt, new MemberRegisteredEvent(memberId, "welcome@test.com", "nick"))`.
- DB(`Eventually` 안에서 `notificationRowsOf(memberId)`): 1개이고 `occurredAt()` = `occurredAt`.

**10. 24시간이 지난 진행 알림은 보내지 않고 `EXPIRED`로 바꾼다** — 준비: 9번의 준비와 동작까지 하고 `Eventually` 안에서 `notificationRowsOf(memberId)`가 1개(`WELCOME`, `PENDING`)가 될 때까지 기다린다. 동작: `notificationDispatcher.dispatchBatch()`.
- DB(`notificationRowsOf(memberId).get(0)`): `status()` = `EXPIRED`.
- 가짜 메일(`fakeMailSender.sent()`): `isEmpty()`.

**11. 24시간이 지나지 않은 진행 알림은 보낸다** — 준비: 10번의 준비와 같고 `occurredAt`만 `Instant.now().minus(Duration.ofHours(23))`이다. 동작: `notificationDispatcher.dispatchBatch()`.
- DB(`notificationRowsOf(memberId).get(0)`): `status()` = `SENT`.

**12. 24시간이 지난 환불 알림은 보낸다** — 준비: `buyerId = signup("buyer@test.com")`, `orderId = Ids.newId()`, `occurredAt = Instant.now().minus(Duration.ofHours(25))`. `TestKafka.send(OrderTopics.ORDER_LINE_REFUNDED, orderId.toString(), Ids.newId(), occurredAt, new OrderLineRefundedEvent(Ids.newId(), Ids.newId(), orderId, buyerId, 1000))` → `Eventually` 안에서 `notificationRowsOf(buyerId)`가 1개(`REFUNDED`, `PENDING`). 동작: `notificationDispatcher.dispatchBatch()`.
- DB(`notificationRowsOf(buyerId).get(0)`): `status()` = `SENT`.

### 문서 반영

단계를 마칠 때 Claude가 고친다.

| 문서 | 고칠 곳 |
|---|---|
| [로드맵 3-5](../03-roadmap/part3-events.md) | 정책에 유효 시간, 1) 마이그레이션 표에 `occurred_at`과 `ck_notification_status`, 6) `NotificationType`·`NotificationStatus`·`Notification`·`insertIfAbsent`, 8) `enqueue` 인자와 `dispatchBatch` 순서, `NotificationStatusTest` 표 |
| [ERD](../02-design/03-erd.md) | `notification`에 `occurred_at`, `status`에 `EXPIRED` |
| [상태머신 9절](../02-design/04-state-machines.md) | `notification` 행에 `EXPIRED` |
| [API 명세](../02-design/07-api-spec.md) | `/admin/api/notifications`의 `status`에 `EXPIRED` |
| [알려진 문제](../known-issues.md) | "때 지난 알림 발송" 행을 지운다 |

**리뷰 때 물어볼 것**

- 만료 기준을 `created_at`이 아니라 `occurred_at`으로 따로 둔 이유는? 두 값이 크게 달라지는 경우는?
- 만료를 consumer가 아니라 발송 잡에서 판단하면 어떤 경로가 더 막히나?
- 환불·정산 지급 알림을 만료시키지 않는 이유는? 서브 구독이 알림 종류를 더할 때 만료 여부를 빠뜨리지 않게 하는 장치는?
- 관리자가 24시간이 지난 `FAILED` 진행 알림을 재시도하면 무슨 일이 일어나나?
