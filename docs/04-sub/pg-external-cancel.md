# 서브: PG 밖에서 취소된 결제의 대사

> 버전 0.1 · 2026-11-13 · 대사의 PG 조회에서 `CANCELED`는 거절로, `PARTIAL_CANCELED`는 불확실로 분류해 밖에서 취소된 결제의 주문을 `PAID`로 닫지 않는다(단계 ID SUB-20)

> **서브**: 로드맵에서 뺀 기능이다. Stage 1(Part 6) 뒤에 한다.
> **따르는 규칙**: 로드맵 Part 1~6의 공통 규칙. 대사 규격의 출발점은 [로드맵 2-3·2-6](../03-roadmap/part2-order-money.md)이다.

---

## SUB-20. PG 밖에서 취소된 결제의 대사 (S)

**목표**: 우리 쪽 결과를 모르는(`IN_PROGRESS`·`UNKNOWN`) 결제가 토스 상점관리자 화면처럼 우리 앱 밖에서 취소됐으면, 대사가 그 주문을 `PAID`로 닫지 않는다. 전액 취소면 주문을 `PAYMENT_FAILED`로 닫고, 부분 취소면 결제를 `UNKNOWN`으로 둔다(FR-PAY-03: 결과를 모르는 결제는 PG 조회로 확정한다).

**드러나는 때**: 승인 응답이 타임아웃·5xx로 끝나 결제가 `UNKNOWN`인 동안, 토스에서는 승인된 결제를 누군가 앱 밖에서 취소했을 때만 생긴다. 우리 앱의 취소(`PaymentApiImpl.cancel`)는 결제가 `APPROVED`·`PARTIAL_CANCELLED`일 때만 PG를 부르고, 그 상태에서 `UNKNOWN`으로 돌아가는 전이가 없으므로 대사의 조회가 우리 앱이 낸 취소를 읽는 일은 없다. 격리 복사본에서 아래 완료 확인 네 케이스가 지금 코드로 실패하는 것을 확인했다(대사 두 케이스 모두 주문 `PAID`).

### 빈 곳

`TossClient.queryByOrderId`가 조회 응답의 `status`를 `DONE`·`PARTIAL_CANCELED`·`CANCELED` 모두 `Approved`로 분류한다. `Approved`는 한 번 승인됐다는 뜻의 `approvedAt`만 보고 만들어지므로, 지금 취소된 결제도 대사에서 결제 `APPROVED`, 주문 `PAID`가 되어 판매자가 돈을 받지 못한 주문을 발송한다. 결과가 맞는 듯 끝나서 드러나는 지표나 로그가 없다.

같은 `PgResult.Approved`가 취소(`TossClient.cancel`)에서는 "취소 요청이 처리됐다"는 뜻이라 그 매핑(`CANCELED`·`PARTIAL_CANCELED` → `Approved`)은 맞다. 이 단계는 조회의 매핑만 바꾼다.

바뀐 뒤의 동작은 다음과 같다.

| 조회 응답의 `status` | 분류 | 대사 결과 |
|---|---|---|
| `DONE` | `Approved` | 바뀌지 않는다. 결제 `APPROVED`, 주문 `PAID` |
| `CANCELED` | `Rejected("CANCELED", "CANCELED")` | 기존 거절 경로를 탄다. 결제 `FAILED`(`failure_code` = `CANCELED`), 주문 `PAYMENT_FAILED`(사유 `PG 거절: CANCELED`), 재고 예약 원복. PG 취소는 부르지 않는다(고객 돈은 밖의 취소로 이미 돌아갔다) |
| `PARTIAL_CANCELED` | `Unknown("PARTIAL_CANCELED")` | 기존 불확실 경로를 탄다. 결제 `UNKNOWN`, 주문 `PAYMENT_IN_PROGRESS`, `reconcile_attempts` +1. 600초가 지나면 알림 `결제 확인 지연`([6-2](../03-roadmap/part6-ops-wrapup.md))이 울린다. 운영자가 토스에서 남은 금액을 취소하면 다음 대사가 `CANCELED`를 읽어 위 행대로 닫는다 |
| `ABORTED`·`EXPIRED`·그 밖 | 바뀌지 않는다 | 바뀌지 않는다 |

### 고른 방법

전액 취소는 두 방법 모두 주문 `PAYMENT_FAILED`, 재고 원복, PG 취소 0건으로 끝나고 결제 행의 기록만 다르다.

| 방법 | 결제 기록 | 판단 |
|---|---|---|
| 조회의 `CANCELED`를 `Rejected("CANCELED")`로 분류한다(할 일) | `FAILED`, `failure_code` = `CANCELED`. `approved_at`·`method`·`cancelled_amount`는 비어 있어, 실제로는 승인 뒤 취소인데 거절처럼 남는다. `failure_code`로 밖의 취소였음을 구분한다 | 고른다. 매핑 한 줄만 바뀌고 나머지는 기존 거절 경로다. 격리 복사본에서 이 한 줄로 완료 확인 네 케이스와 기존 `TossClientTest`·`OrderReconcileServiceTest` 26건이 모두 통과했다 |
| 새 결과(`PgOutcomeType.CANCELLED`)를 더해 결제를 `CANCELLED`로 기록한다 | `CANCELLED`, 승인 시각·수단·취소 금액이 PG와 같다 | 고르지 않는다. 기록은 정확하지만 `PgResult`·`PgOutcomeType`·`applyOutcome`·리포지토리 update 메서드·`PaymentStatus` 전이 2쌍(`IN_PROGRESS`·`UNKNOWN` → `CANCELLED`)·대사 분기를 바꿔야 하고, 주문·재고·돈의 결과는 같다 |

부분 취소는 다음 셋을 비교했다.

| 방법 | 판단 |
|---|---|
| `Unknown`으로 두고 운영자가 처리한다(할 일) | 고른다. 밖에서 일부를 취소한 것은 사람이 한 일이라 남은 금액도 사람이 정한다. 처리할 때까지 주문이 `PAYMENT_IN_PROGRESS`이고 재고 예약을 쥔다 |
| 대사가 남은 금액을 PG에서 취소하고 주문을 실패로 닫는다 | 고르지 않는다. 사람 없이 끝나지만 돈이 나가는 자동 분기가 늘고, 결제를 먼저 `PARTIAL_CANCELLED`로 반영하는 새 전이(`UNKNOWN → PARTIAL_CANCELLED`)가 필요하다 |
| 지금처럼 `Approved`로 둔다 | 고르지 않는다. 일부 금액만 받은 주문이 `PAID`가 되어 전체가 발송된다 |

### 할 일

**`TossClient.queryByOrderId`** — 조회의 `onSuccess` 규칙을 다음으로 바꾼다. `cancel`의 규칙은 그대로 둔다.

- `"DONE"` → `approved(r)`
- `"CANCELED"`·`"ABORTED"`·`"EXPIRED"` → `new PgResult.Rejected(r.status(), r.status())`
- 그 외(`PARTIAL_CANCELED`, `READY`, `IN_PROGRESS`) → `new PgResult.Unknown(r.status())`

`PaymentApiImpl.queryPgOutcome`·`applyOutcome`·`OrderReconcileService`는 바꾸지 않는다. `Rejected`는 지금처럼 `markFailed` → `applyPaymentState(FAILED)`로, `Unknown`은 `increaseReconcileAttempts`로 간다.

### 완료 확인

케이스는 [로드맵 README 4.1](../03-roadmap/README.md)의 기준으로 골랐다. 앱 밖의 취소는 가짜 PG 서버의 `cancelOutside(String paymentKey, long cancelAmount)`로 만든다. 승인 기록의 취소 금액만 늘리고 요청은 기록하지 않아, 우리 앱이 취소를 보냈는지와 섞이지 않는다. 규격은 [로드맵 2-3](../03-roadmap/part2-order-money.md)의 가짜 PG 서버에 있다.

#### `payment/infrastructure/TossClientTest`

**10. 밖에서 전액 취소된 주문 조회** — 준비: `confirm(Ids.newId(), "pk_1", "order-1", Money.of(1000))` → `pg.cancelOutside("pk_1", 1000)`. 동작: `queryByOrderId(Ids.newId(), "order-1")`.
- 반환값: `Rejected`이고 `code()` = `"CANCELED"`.

**11. 밖에서 부분 취소된 주문 조회** — 준비: `confirm(Ids.newId(), "pk_1", "order-1", Money.of(1000))` → `pg.cancelOutside("pk_1", 400)`. 동작: `queryByOrderId(Ids.newId(), "order-1")`.
- 반환값: `Unknown`이고 `reason()` = `"PARTIAL_CANCELED"`.

#### `order/application/OrderReconcileServiceTest`

**공통 준비**: 같은 클래스의 `reconcileApprovedAfterTimeout`과 같은 순서로 대사 대상 주문을 만든다.

1. `signup("test@test.com")` → `openShop(memberId, "1111111111")` → `registerProduct(memberId, shopId, 1000, 100)`(1000원, 재고 100)
2. `signup("buyer@test.com")` → `createPendingOrder(buyerId, productId)`(수량 1, 총액 1000)
3. `pg.on("/v1/payments/confirm", Behavior.delay(1000))` → `confirmPayment(buyerId, order.orderId())`. 가짜 PG는 승인을 처리한 뒤 1초 늦게 응답해 테스트 `read-timeout`(500ms)에 걸린다. 주문은 `PAYMENT_IN_PROGRESS`, 결제는 `UNKNOWN`이고 가짜 PG에는 승인 기록이 남는다
4. `setOrderUpdatedAt(orderId, Instant.now().minusSeconds(60))`. 대사는 30초 넘게 머문 주문만 처리한다
5. `String paymentKey = snapshotOf(orderId).payment().paymentKey()` → `pg.cancelOutside(paymentKey, 금액)`으로 밖의 취소를 만든다

**13. 밖에서 전액 취소된 결제는 대사로 주문이 `PAYMENT_FAILED`가 된다** — 준비: 공통 준비(5의 금액 1000). 동작: `orderReconcileService.reconcileOne(orderId)`.
- DB(snapshotOf(orderId)): `status()` = `PAYMENT_FAILED`.
- DB(`SELECT status, failure_code FROM payment.payment WHERE order_id = ?`): `FAILED`, `CANCELED`.
- 가짜 서버 요청(pg.requests("/v1/payments/" + paymentKey + "/cancel")): `isEmpty()`.

**14. 밖에서 부분 취소된 결제는 대사로 주문을 닫지 않는다** — 준비: 공통 준비(5의 금액 400). 동작: `orderReconcileService.reconcileOne(orderId)`.
- 반환값: `OrderStatus.PAYMENT_IN_PROGRESS`.
- DB(snapshotOf(orderId)): `payment().status()` = `UNKNOWN`.
- 가짜 서버 요청(pg.requests("/v1/payments/" + paymentKey + "/cancel")): `isEmpty()`.

### 문서 반영

단계를 마칠 때 Claude가 고친다.

| 문서 | 고칠 곳 |
|---|---|
| [로드맵 2-3](../03-roadmap/part2-order-money.md) | `classify` 작업별 규칙의 조회 줄, 가짜 PG 서버 규격에 `cancelOutside` |
| [알려진 문제](../known-issues.md) | "PG에서 취소된 결제를 승인으로 읽음" 행을 지운다 |

**리뷰 때 물어볼 것**

- 같은 `CANCELED`가 취소 API에서는 `Approved`, 조회에서는 `Rejected`인 이유는?
- 대사의 조회가 우리 앱이 낸 취소를 읽는 일이 없다고 할 수 있는 근거는? 그 근거가 깨지는 변경은 무엇인가?
- 밖에서 전액 취소된 주문을 `PAYMENT_FAILED`로 닫을 때 PG 취소를 부르지 않아도 되는 이유는?
- 부분 취소된 결제가 `UNKNOWN`으로 남은 동안 재고는 어떤 상태인가? 운영자는 무엇을 보고 알게 되나?
