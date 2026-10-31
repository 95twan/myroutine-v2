# 10. 상태 조합 — 어떤 상태끼리 함께 있을 수 있고, 어떻게 거기 도달하는가

> 버전 0.4 · 2026-10-20 · 2-10 규격이 `confirmLine`·`autoConfirm`에서 `recalculateShopOrder`를 부르게 정했다
> 0.3 · 2026-10-19 · 반품 환불 `FAILED` 때 품목 상태를 정했다(로드맵 0.98)
> 0.2 · 2026-10-19 · `Order.rejectReturn`에 품목 `RETURN_REQUESTED` 검사를 넣기로 했다(로드맵 0.96)

이 문서는 **새 결정을 내리지 않는다.** 코드(2-8b까지 구현, 2-9 작업 중)와 설계 문서([04-state-machines](04-state-machines.md), [03-erd](03-erd.md), 로드맵)에 있는 것을 조합 단위로 다시 모았다. 어긋난 곳은 7절에 "확인 필요"로 적었고 여기서 고치지 않는다.

## 1. 읽는 법

- 조합은 `order=PAID, shopOrder=PAID, line=PAID`처럼 쓴다. 조합마다 **어떤 상황에서 나오는지**를 먼저 쓰고, 그다음 코드 경로를 쓴다.
- 경로의 `파일:줄`은 `src/main/java/com/myroutine/` 아래이고 2026-10-19 작업 트리 기준이다. 줄이 밀리면 메서드 이름으로 찾는다.
- **확인** 열의 뜻
  - **탐색**: 도메인 메서드를 가능한 모든 순서로 적용해 도달 가능한 상태를 전부 뽑아 확인했다(2.4).
  - **코드**: 코드를 읽어 호출 경로를 확인했다. 실행하지는 않았다.
  - **문서**: 설계 문서에만 있고 코드가 없다.
- **(P)**: 도메인 메서드는 있지만 호출하는 서비스·컨트롤러가 아직 없어 지금 서버로는 도달할 수 없는 조합이다. 2-9(반품)·2-10(구매확정) 연결 뒤에 나온다.
- 용어: `order`는 `orders.status`, `shopOrder`는 `shop_order.status`, `line`은 `order_line.status`다.

## 2. 주문 계층: 주문 · 가게주문 · 품목 · 환불

### 2.1 결제 전과 결제 실패

결제 전에는 주문 상태가 곧 비즈니스 상태이고, 가게주문·품목은 주문과 같이 움직인다. 가게주문은 모두 `PENDING`이거나 모두 `CANCELLED`다.

| 조합 | 어떤 상황 | 도달 경로 | 확인 |
|---|---|---|---|
| `order=PENDING_PAYMENT, shopOrder=PENDING, line=PENDING` | 체크아웃이 끝났고 아직 결제하지 않음. 재고는 예약(`HELD`)돼 있다 | `CheckoutService.checkout` → `Order.checkout`(`order/domain/Order.java:63`) | 탐색 |
| `order=PAYMENT_IN_PROGRESS, shopOrder=PENDING, line=PENDING` | 결제 승인 요청을 보냈고 결과를 받기 전. PG 응답이 불확실하면 이 상태로 남아 대사 잡이 닫는다 | `ConfirmPaymentService`(`order/application/ConfirmPaymentService.java:41` `startPayment` CAS) → `paymentApi.begin`(`:63`) | 코드 |
| `order=PAID, shopOrder=PAID, line=PAID` | 결제가 끝났고 발송 전 | 모두 `OrderPaymentApplier.applyPaymentState`(`order/application/OrderPaymentApplier.java:37`) → `complete`(`:62`: 재고 예약 확정 + `Order.markPaid`). 조건은 주문이 `PAYMENT_IN_PROGRESS`이고 payment가 `APPROVED`. 진입점은 (a) 결제 확인 `ConfirmPaymentService:73` (b) 대사 잡 `OrderReconcileService.reconcileOne`의 `APPROVED` 분기(`:57` → `closeConfirmed:76`) (c) 같은 잡의 `IN_PROGRESS`/`UNKNOWN` 분기(`:60` → `closeByQuery:80`)로, PG 조회 결과가 승인 | 탐색, 코드 |
| `order=PAYMENT_FAILED, shopOrder=CANCELLED, line=CANCELLED` | PG가 결제를 거절했거나, 승인됐는데 주문 완료 처리를 못 해서 PG 승인을 취소함 | `OrderPaymentApplier.fail`(`:68`) → 재고 `RELEASED` + `Order.markPaymentFailed`(`Order.java:100`) → `cancelPendingChildren`(`:106`). 진입점은 (a) 결제 확인에서 거절 (b) 대사에서 payment `FAILED` (c) 대사 `closeByQuery`에서 거절 (d) 대사에서 payment `CANCELLED`(`OrderReconcileService:63`) (e) 보상 취소(`:99` `paymentApi.cancel`, 키 `order-compensate-<orderId>`) 뒤 `failOrder` | 탐색, 코드 |
| `order=EXPIRED, shopOrder=CANCELLED, line=CANCELLED` | 결제하지 않고 `expires_at`이 지남 | `OrderExpirationService.expireOne`(`order/application/OrderExpirationService.java:43`): `expire` CAS(`PENDING_PAYMENT`이고 `expires_at <= now`) → 재고 `EXPIRED` → `cancelPendingChildren` | 코드 |
| `order=CANCELLED, shopOrder=CANCELLED, line=CANCELLED` | 구매자가 결제 전에 주문을 취소하거나 결제창을 닫음 | `PendingOrderCancelService`(`order/application/PendingOrderCancelService.java:30` `cancelBeforePayment` CAS) → 재고 `RELEASED` → `cancelPendingChildren` | 코드 |

- `PAYMENT_FAILED`·`EXPIRED`·`CANCELLED`는 가게주문·품목 수준에서는 구분되지 않는다(모두 `CANCELLED`). 원인은 `orders.status`와 `failure_reason`으로만 읽는다.
- `PAYMENT_IN_PROGRESS`는 만료 대상이 아니고(만료 CAS는 `PENDING_PAYMENT`만) 구매자 취소로도 못 닫는다(취소 CAS도 `PENDING_PAYMENT`만).
- `startPayment`·`expire`·`cancelBeforePayment`는 모두 `PENDING_PAYMENT`를 조건으로 하는 CAS(`order/infrastructure/JpaOrderRepository.java:39, 48, 64`)라 셋 중 하나만 성공한다. 이 CAS는 `OrderStatus.ALLOWED`를 거치지 않는다.

### 2.2 결제 후

주문은 `PAID`에서 나가는 전이가 없다(`order/domain/OrderStatus.java:13-14`). 그래서 결제 후의 모든 조합에서 `order=PAID`다. 품목이 전부 취소돼도 주문은 `PAID`이고 가게주문만 `CANCELLED`가 된다.

가게주문은 서로 독립이다. `PAID` 주문 아래에서 가게주문 A는 `SHIPPED`, B는 `CANCELLED`, C는 `DELIVERED`일 수 있다.

가게주문 하나 안의 품목 구성은 아래 표의 것만 가능하다(품목 2개짜리 가게주문을 전수 탐색한 결과를 품목 n개로 일반화했다. 일반화는 추론이다).

| shopOrder | 품목 상태 구성 | 어떤 상황 | 지금 도달 |
|---|---|---|---|
| `PAID` | 모두 `PAID` | 결제 직후, 발송 전 | 예 |
| `PAID` | `PAID` + `CANCELLED`(`PAID`가 1개 이상) | 발송 전에 구매자가 일부 품목만 취소 | 예 |
| `SHIPPED` | 모두 `SHIPPED` | 판매자가 발송 | 예 |
| `SHIPPED` | `SHIPPED` + `CANCELLED`(`SHIPPED`가 1개 이상) | 일부 품목을 취소한 뒤 나머지를 발송 | 예 |
| `DELIVERED` | 모두 `DELIVERED` | 배송완료 | 예 |
| `DELIVERED` | `DELIVERED` + `CANCELLED` | 일부 취소 뒤 발송·배송완료 | 예 |
| `DELIVERED` | `DELIVERED`·`RETURN_REQUESTED`·`RETURNED`·`CONFIRMED`·`CANCELLED`의 혼합 중 `DELIVERED` 또는 `RETURN_REQUESTED`가 1개 이상 | 일부 품목에 반품 요청·구매확정이 일어났고 나머지는 아직 종결이 아님 | (P) |
| `COMPLETED` | `CONFIRMED`·`RETURNED`·`CANCELLED`의 혼합 중 `CONFIRMED` 또는 `RETURNED`가 1개 이상 | 마지막 미종결 품목이 구매확정되거나 반품 환불이 끝남 | (P) |
| `CANCELLED` | 모두 `CANCELLED` | 결제 전에 끝났거나, 결제 후 마지막 품목 환불이 끝남 | 예 |

품목 상태가 그 가게주문·주문에 대해 의미하는 것(다른 상태가 함께 있어야 하는 이유):

| 품목 상태 | 반드시 함께 있는 것 | 이유 |
|---|---|---|
| `PAID` | 가게주문 `PAID`, 주문 `PAID` | `ShopOrder.markPaid`가 가게주문·품목을 함께 `PAID`로 바꾼다(`order/domain/ShopOrder.java:70`). 가게주문은 발송(`ship`, `:80`)하면 취소되지 않은 품목을 모두 `SHIPPED`로 바꾸고, 모두 취소됐을 때만 `CANCELLED`가 된다 |
| `SHIPPED` | 가게주문 `SHIPPED`, 주문 `PAID` | 품목이 `SHIPPED`가 되는 길은 `ship`뿐이다 |
| `DELIVERED`·`CONFIRMED`·`RETURN_REQUESTED`·`RETURNED` | 가게주문 `DELIVERED`(또는 `COMPLETED`), 주문 `PAID` | `deliver`(`:90`)를 거쳐야 `DELIVERED`가 된다 |
| `CANCELLED` | 결제 전 종료면 주문 `PAYMENT_FAILED`/`EXPIRED`/`CANCELLED`, 결제 후 환불이면 주문 `PAID`. 가게주문은 무엇이든 가능 | 취소된 품목은 `ship`·`deliver`가 건너뛴다(`ShopOrder.java:85-87, 93-95`) |

### 2.3 결제 후 도달 경로

| 조합 | 어떤 상황 | 도달 경로 | 확인 |
|---|---|---|---|
| `order=PAID, shopOrder=PAID, line=PAID` + 환불 `APPROVED`/`PG_CANCELLED` | 구매자가 한 품목을 취소했고 PG 취소가 진행 중이거나 응답이 불확실 | `CancelOrderLineService.cancel`(`order/application/CancelOrderLineService.java:23` 주문 행 잠금 → `:26` `verifyCancellable` → `:30` 환불 `APPROVED` 저장) → `RefundProcessor.process`(`order/application/RefundProcessor.java:25` PG 취소 → `:29` `PG_CANCELLED`). 불확실하면 `APPROVED`로 남고 `RefundRecoveryService.recoverBatch`(`:25`)가 5분 뒤 다시 처리 | 코드 |
| `line=PAID` + 환불 `FAILED` | PG가 취소를 명확히 거절 | `RefundProcessor`(`:34` `r.fail()`). 품목은 `PAID` 그대로이고 구매자가 새 환불을 만들 수 있다 | 코드 |
| `shopOrder=PAID, line=PAID+CANCELLED` | 일부 품목의 환불이 끝남 | `RefundCompleter.complete`(`order/application/RefundCompleter.java:22`): 주문 행 잠금(`:29`) → 재고 복구(`:33`) → `Order.applyRefund`(`:37`, 품목 `CANCELLED`) → 환불 `COMPLETED` | 탐색, 코드 |
| `shopOrder=CANCELLED, line=CANCELLED+CANCELLED`, `order=PAID` | 마지막 품목의 환불이 끝남 | 같은 `applyRefund` 안에서 `recalculateShopOrder`(`Order.java:171`)가 모든 품목이 `CANCELLED`인 것을 보고 `markCancelledWithoutLines`(`ShopOrder.java:98`) | 탐색 |
| `shopOrder=SHIPPED` | 판매자가 발송 | `ShipmentService.ship`(`order/application/ShipmentService.java:24`): 주문 행 잠금(`:26`) → 진행 중 환불(`REQUESTED`/`APPROVED`/`PG_CANCELLED`)이 있으면 거부(`:33`) → `Order.ship`(`:39`) | 코드 |
| `shopOrder=DELIVERED` | 판매자가 배송완료 처리 | `ShipmentService.deliver`(`:45`): 잠금도 환불 확인도 없다. `SHIPPED` 품목만 `DELIVERED`로 바꾼다 | 코드 |
| `line=RETURN_REQUESTED`, 환불 `REQUESTED` | 구매자가 반품 요청 | `Order.requestReturn`(`Order.java:181`) + `Refund.requestReturn`. 호출하는 서비스는 2-9 `ReturnService` | (P) |
| `line=DELIVERED`, 환불 `REJECTED` | 판매자가 반품을 거절 | `Order.rejectReturn`(`:186`, 품목이 `RETURN_REQUESTED`일 때만) + `Refund.reject`. 이 품목은 다시 반품 요청할 수 없다 | (P) |
| `line=RETURNED`, 환불 `COMPLETED` | 판매자가 승인해 환불이 끝남 | `RefundCompleter` → `applyRefund(type=RETURN)` → `markReturned` | (P) |
| `shopOrder=COMPLETED` | 마지막 미종결 품목이 반품되거나 구매확정됨 | `recalculateShopOrder`(`Order.java:176`): `DELIVERED`이고 모든 품목이 종결이면 `markCompleted`. 구매확정 쪽(`confirmLine`, `autoConfirm`)은 2-10에서 만들며 같은 메서드를 불러야 한다 | (P) |

### 2.4 불변식과 확인 방법

탐색 방법: 임시 테스트(저장소에는 넣지 않았다)로 가게 2개 × 품목 2개짜리 주문을 만들고, 도메인 메서드(`markPaid`, `markPaymentFailed`, `cancelPendingChildren`, `ship`, `deliver`, `verifyCancellable` + `applyRefund`, `requestReturn`, `rejectReturn`, 반품 환불 완료)를 서비스가 지키는 호출 조건대로 가능한 모든 순서로 적용해 도달 가능한 주문 상태 964개를 전부 뽑았다. 가게주문 단위 조합은 22개다. 구매확정(2-10)은 미구현이라 "`DELIVERED` 품목을 `CONFIRMED`로 바꾸고 가게주문을 재계산한다"고 가정해 넣었다.

아래 불변식은 964개 상태 어디에서도 깨지지 않았다.

1. 품목이 `PAID`이면 가게주문도 `PAID`이고 주문도 `PAID`다.
2. 품목이 `PENDING`·`CANCELLED`가 아니면 주문은 `PAID`다.
3. 가게주문이 `PENDING`이면 모든 품목이 `PENDING`이다.

따라서 `verifyCancellable`이 품목 `PAID` 하나만 검사해도 주문·가게주문이 `PAID`가 아닌 취소는 생기지 않는다. 단 불변식 1은 `ShopOrder.ship`이 취소되지 않은 품목을 **모두** `SHIPPED`로 바꾸는 데 기댄다. 가게주문 일부만 발송하는 기능이 생기면 이 검사를 다시 봐야 한다.

DB는 이 조합을 막지 않는다. `ck_orders_status`, `ck_shop_order_status`, `ck_order_line_status`는 값의 목록만 제한하고, `ck_order_line_refunded`(`0 <= refunded_amount <= line_amount`)가 금액 범위를 제한한다. 엔티티 사이의 조합은 도메인 코드, 주문 행 잠금, `@Version`이 지킨다.

### 2.5 환불 × 품목

환불 한 건은 품목 한 개에 대한 것이고, 진행 중인 환불은 품목마다 1건뿐이다(`uk_refund_order_line_active`, `REQUESTED`/`APPROVED`/`PG_CANCELLED`에 대한 부분 유니크 인덱스, `db/migration/order/V202610171855__order_create_refund.sql:27`).

| 품목 | 함께 있을 수 있는 환불 | 어떤 상황 | 확인 |
|---|---|---|---|
| `PENDING` | 없음 | 결제 전 | 코드 |
| `PAID` | 없음 / `APPROVED` / `PG_CANCELLED` / `FAILED` | 환불이 진행 중이거나 PG가 거절. `COMPLETED`는 같은 트랜잭션에서 품목이 `CANCELLED`가 되므로 공존하지 않는다 | 코드 |
| `CANCELLED` | `COMPLETED`(구매자 취소), 환불 없음(결제 전 종료), 이전의 `FAILED` | 환불이 끝났거나 결제 전에 끝남 | 코드 |
| `SHIPPED` | 진행 중 환불 없음. 이전 `FAILED`는 있을 수 있음 | 발송 가드가 `FAILED`를 보지 않는다(7절 1번) | 코드 |
| `DELIVERED` | 없음 / 이전 `FAILED` / `REJECTED` | 배송완료, 반품 거절 | `REJECTED`는 (P) |
| `RETURN_REQUESTED` | `REQUESTED` / `APPROVED` / `PG_CANCELLED` / `FAILED` | 반품 승인 대기, 진행, PG 거절(운영자 재시도 대기) | (P) |
| `RETURNED` | `COMPLETED`(반품) | 반품 환불 완료 | (P) |
| `CONFIRMED` | 없음 / `REJECTED` | 구매확정 | (P) |

`refunded_amount`는 0이거나 `line_amount`뿐이다. 환불은 품목 전액(`line_amount - refunded_amount`)이고 완료되면 품목이 종결이 되기 때문이다.

## 3. 결제: payment · payment_cancel × 주문

`payment`는 주문당 1건이다(`uk_payment_order`, `uk_payment_pg_order_id`, `uk_payment_payment_key`). 상태 전이 중 enum 규칙(`transitTo`)을 거치는 것은 `Payment.applyCancel`(`payment/domain/Payment.java:79`)뿐이고, 나머지는 `JpaPaymentRepository`의 조건부 UPDATE(CAS)가 실제 가드다(`payment/infrastructure/JpaPaymentRepository.java:28-53`). `payment_cancel`은 전이표 없이 CAS만 쓴다.

| payment | cancelled_amount | payment_cancel | order | 어떤 상황 | 도달 경로 | 확인 |
|---|---|---|---|---|---|---|
| `IN_PROGRESS` | 0 | 없음 | `PAYMENT_IN_PROGRESS` | 승인 요청 직전. 서버가 `begin`과 PG 호출 사이에 죽으면 이 상태로 남는다 | `ConfirmPaymentService:41, 63` 한 트랜잭션 | 코드 |
| `UNKNOWN` | 0 | 없음 | `PAYMENT_IN_PROGRESS` | PG 응답이 타임아웃·5xx·판단 불가 | `PaymentApiImpl.applyOutcome`(`payment/application/PaymentApiImpl.java:64`)의 `markUnknown`(`:80`), 대사의 `queryPgOutcome`(`:106`)이 불확실을 돌려줄 때. 푸는 쪽은 1분 주기 대사 잡 | 코드 |
| `APPROVED` | 0 | 없음 | `PAID` | 결제 완료 | 위 2.1의 `PAID` 경로. 확인 경로에서는 같은 트랜잭션에서 주문까지 `PAID`가 되므로 "payment `APPROVED` + order `PAYMENT_IN_PROGRESS`"는 남지 않는다 | 코드 |
| `APPROVED` | 0 | — | `PAYMENT_IN_PROGRESS` | 대사 잡이 payment를 먼저 `APPROVED`로 반영하고 주문 완료 처리 전에 실패·중단됨 | `OrderReconcileService.closeByQuery`(`:80`)가 `applyOutcome`과 `closeOrder`를 별도 트랜잭션으로 실행. 다음 주기에 `reconcileOne`의 `APPROVED` 분기(`:57`)가 닫는다 | 코드 |
| `APPROVED` | 0 | `REQUESTED`/`UNKNOWN` | `PAID` | 환불 또는 보상 취소의 PG 호출이 진행 중이거나 결과 불확실 | `PaymentApiImpl.cancel`(`:124`) 첫 트랜잭션이 `payment_cancel`을 `REQUESTED`로 넣음(`:129`), 불확실이면 `markUnknown`(`:172`). 같은 멱등 키로 재시도 | 코드 |
| `PARTIAL_CANCELLED` | 0 < c < amount | `DONE` | `PAID` | 일부 품목 환불 완료 | `markDone`(`:158`)과 `Payment.applyCancel`(`:163`)이 같은 트랜잭션. `payment` 행 잠금(`:160`) | 코드 |
| `CANCELLED` | = amount | `DONE` | `PAID` | 모든 품목 환불 완료 | 마지막 품목 환불 | 코드 |
| `CANCELLED` | = amount | `DONE`(키 `order-compensate-<orderId>`) | `PAYMENT_FAILED` | 승인은 났는데 주문 완료 처리를 못 해서 자동 전액 취소 | `OrderReconcileService:99` 이후 `failOrder`. 실패하면 다음 주기에 `case CANCELLED`(`:63`)가 닫는다 | 코드 |
| `FAILED` | 0 | 없음 | `PAYMENT_FAILED` | PG가 승인을 거절 | `applyOutcome`(거절) → `OrderPaymentApplier.fail` | 코드 |

- DB는 `ck_payment_cancelled`(`0 <= cancelled_amount <= amount`)와 `ck_payment_status`만 막는다. `status`와 `cancelled_amount`의 관계("`CANCELLED`이면 `cancelled_amount = amount`")와 "완료된 `payment_cancel` 금액의 합 = `cancelled_amount`"는 코드만 지킨다.
- `payment.status`가 `PARTIAL_CANCELLED`일 때 대사 대상이 되면 대사는 로그만 남기고 끝낸다(`OrderReconcileService:67`).

## 4. 재고: stock · 예약 · 이동 이력 × 주문

재고 불변식은 DB가 지킨다: `available + reserved + sold = received`(`ck_stock_balance`), 각 수량 ≥ 0. 모든 재고 변경은 조건부 UPDATE이고(`product/infrastructure/JpaStockRepository.java:44-98`), 예약 상태 변경은 `HELD`를 조건으로 하는 CAS다. 예약은 `RELEASED`/`EXPIRED`/`COMMITTED`에서 되돌아가지 않는다.

| 예약 | 재고 이력 | 재고 변화 | order | 어떤 상황 | 도달 경로 | 확인 |
|---|---|---|---|---|---|---|
| (없음) | 없음 | — | — | 체크아웃 전이거나 롤백됨 | — | 코드 |
| `HELD` | `RESERVE` | `available -q`, `reserved +q` | `PENDING_PAYMENT`/`PAYMENT_IN_PROGRESS` | 체크아웃 후 | `CheckoutService` → `ProductApiImpl.reserve`(`product/application/ProductApiImpl.java:63`) | 코드 |
| `COMMITTED` | `RESERVE`, `COMMIT` | `reserved -q`, `sold +q` | `PAID` | 결제 완료 | `commitReservation`(`:119`). `Order.markPaid`와 같은 트랜잭션 | 코드 |
| `COMMITTED` + 환불 | `RESERVE`, `COMMIT`, `RESTORE` | `sold -q`, `available +q` | `PAID` | 품목 환불 완료 | `restore`(`:176`). 예약 상태는 그대로 `COMMITTED`다 | 코드 |
| `RELEASED` | `RESERVE`, `RELEASE` | `reserved -q`, `available +q` | `PAYMENT_FAILED` 또는 `CANCELLED` | 결제 실패, 구매자의 결제 전 취소 | `releaseReservation`(`:145`, 사유 `PAYMENT_FAILED`). 구매자 취소도 같은 사유를 쓴다 | 코드 |
| `EXPIRED` | `RESERVE`, `RELEASE` | 같음 | `EXPIRED` | 결제 없이 만료 | `releaseReservation`(사유 `EXPIRED`) | 코드 |

- 재고 이력은 `RELEASED`와 `EXPIRED`를 구분하지 않는다(둘 다 `RELEASE`).
- 상품 상태는 예약·재고와 독립이다. `HIDDEN`/`DISCONTINUED` 상품에도 `HELD` 예약이 있을 수 있고, 결제 완료 시 상품 상태를 다시 보지 않는다. 재고 조정(`AdjustStockService`)도 상품 상태를 보지 않는다.
- `stock_reservation.expires_at`은 저장만 하고 읽는 코드가 없다. 예약 만료는 주문 모듈이 `PENDING_PAYMENT` 주문을 만료시키면서 `releaseReservation`을 부르는 방식뿐이다.

## 5. 상품 · 가게 · 회원 · 멱등 키

| 대상 | 가능한 값과 전이 | 지금 도달 가능한 값 | 확인 |
|---|---|---|---|
| `product.status` | `ON_SALE` ⇄ `HIDDEN`, 둘 다 → `DISCONTINUED`(종결) (`product/domain/ProductStatus.java:13-15`) | 모두. `PATCH .../status`로 전이 | 코드 |
| `shop.status` | `ACTIVE` → `CLOSED` (`shop/domain/ShopStatus.java:13`) | 모두. `CloseShopService.close`로 `CLOSED` | 코드 |
| `member.status` | `ACTIVE` → `BANNED`/`WITHDRAWN`, `BANNED` → `ACTIVE` (`member/domain/MemberStatus.java:15-17`) | `ACTIVE`, `WITHDRAWN`. 탈퇴(3-6, `WithdrawService`)는 구현됐고 제재(`BANNED`)는 4-4에서 구현한다. 가입은 항상 `ACTIVE` + `USER` | 코드 |
| `member.role` | `USER`, `SELLER`, `ADMIN` | `USER`, `SELLER`(가게 개설 이벤트로 `MemberRoleService.grantSeller`, 활성 가게가 없어지면 회수), `ADMIN`(DB에서 직접 지정). `/admin/**`는 `hasRole("ADMIN")`이 막는다 | 코드 |
| `member_address.is_default` | 회원당 0개 또는 1개(`uk_member_address_default` 부분 유니크) | 첫 주소는 자동 기본. 기본 주소를 삭제하거나 해제하면 0개 | 코드 |
| `idempotency_key.status` | `PROCESSING` → `COMPLETED`. 2xx가 아니거나 예외면 행을 지운다 | 모두. 서버가 죽으면 `PROCESSING`이 만료(24시간)까지 남는다는 것은 추론 | 코드 |

- 가게가 `CLOSED`가 되면 상품 등록·수정·재고조정·체크아웃은 막히지만(`Shop.verifyActive`), 이미 있는 `HELD` 예약과 `PAID` 주문은 그대로 진행된다.

## 6. 설계 문서에만 있는 도메인

코드가 없어서 설계 문서 기준이다. 조합의 상세는 해당 단계를 구현할 때 이 문서에 더한다.

| 도메인 | 상태 | 함께 보는 상태 | 구현 단계 |
|---|---|---|---|
| outbox_event | `READY` → `PROCESSING` → `SENT`, 실패 시 `READY`, 10회 실패 시 `DEAD` | 도메인 상태 변경과 같은 트랜잭션에서 `READY` 행을 쓴다 | 3-1 |
| notification | `PENDING` → `SENT`, 5회 실패 시 `FAILED` | 이벤트 소비자가 `PENDING`으로 저장, 별도 잡이 발송 | 3-5 |
| settlement | `CALCULATED` → `PAID`, `PAYOUT_FAILED` → `PAID` | `order_line=CONFIRMED`가 적재 조건. 같은 품목은 한 번만(INV-07) | 3-4 |
| subscription | `ACTIVE`/`PAUSED`/`SUSPENDED`/`CANCELLED`/`TERMINATED` | cycle(`ORDERED`/`PAID`/`FAILED`/`SKIPPED`)과 주문 상태가 함께 움직인다. 주문이 `PAID`면 회차 `PAID`, `PAYMENT_FAILED`면 회차 `FAILED`, 3회 연속이면 구독 `SUSPENDED` | SUB-2~SUB-4 |
| billing_key | `ACTIVE` → `DELETED` | 활성 구독이 쓰는 키는 삭제 불가 | SUB-1 |
| review / inquiry / product_embedding | review `ACTIVE`/`DELETED`, inquiry `OPEN`/`ANSWERED`, embedding `ACTIVE`/`INACTIVE` | 리뷰는 품목이 `CONFIRMED`일 때만 | 5-x, SUB-5 |
| 운영 API 전이 | 환불 `FAILED` → `APPROVED`, outbox `DEAD` → `READY`, notification `FAILED` → `PENDING` | | 6-3 |

## 7. 확인 필요

해결은 이 문서에서 하지 않는다. 항목마다 어디서 어긋나는지만 적는다.

| # | 내용 | 근거 |
|---|---|---|
| 1 | 발송 가드가 `FAILED` 환불을 보지 않는다. `FAILED` 환불이 있는 `PAID` 품목이 발송될 수 있고, 그 뒤 운영자가 `FAILED → APPROVED`로 되돌리면 `SHIPPED` 품목의 PG 취소가 성공하거나 활성 환불이 둘이 되어 인덱스에 걸린다 | `order/application/ShipmentService.java:33`(`REQUESTED`/`APPROVED`/`PG_CANCELLED`만 확인), `uk_refund_order_line_active` |
| 2 | `RefundStatus`는 코드에서 `FAILED`가 종결인데 04 문서 §4는 `FAILED → APPROVED`(6-3)를 그린다 | `order/domain/RefundStatus.java:13-15`, 04-state-machines §4 |
| 3 | 반품 승인 뒤 PG 취소가 `FAILED`이면 품목은 `RETURN_REQUESTED`로 남고 운영자가 6-3 재시도로 처리하기로 했다(로드맵 2-9, 0.98). 재시도해도 PG가 계속 거절하는 환불(예: 이미 PG에서 취소된 결제)을 닫는 방법은 아직 없다. 6-3에서 정한다 | 로드맵 2-9 `approve`, part6 6-3 |
| 4 | 대사는 payment 행이 없거나 `PARTIAL_CANCELLED`이면 로그만 남기고 영원히 다시 시도한다(주문이 `PAYMENT_IN_PROGRESS`로 남음) | `OrderReconcileService:49, 67` |
| 5 | `PaymentApiImpl.cancel`의 상태·잔액 검사가 `payment` 행 락 밖(첫 트랜잭션)이다. 서로 다른 키로 동시에 온 두 취소가 모두 통과할 수 있고, 두 번째는 `applyCancel`에서 예외로 롤백돼 그 `payment_cancel` 행이 `REQUESTED`/`UNKNOWN`으로 남는다(추론) | `PaymentApiImpl.java:139-144`, `:160` |
| 6 | PG 조회 결과 `CANCELED`·`PARTIAL_CANCELED`를 `APPROVED`로 매핑한다. 우리 쪽이 `UNKNOWN`인 결제가 PG에서는 이미 취소된 경우 로컬이 `APPROVED`가 된다(추론) | `payment/infrastructure/TossClient.java:59, 81` |
| 7 | 04 문서 §4는 "누적 취소액 ≤ 승인액은 payment의 CHECK와 **주문 행 잠금**으로 지킨다"고 쓰지만 PG 취소 반영은 `payment` 행 잠금으로 직렬화한다(로드맵 0.83) | 04-state-machines §4, `PaymentApiImpl.java:160` |
| 8 | 04 문서에 상태머신이 없는 것: `payment_cancel`, `billing_key`, `idempotency_key`, `review`, `inquiry`, `product_embedding` | 03-erd |
| 9 | 코드에는 쓰기만 하고 읽지 않거나 저장만 하는 값이 있다: `payment.reconcile_attempts`(상한 없이 `UNKNOWN`이 반복될 수 있음), `stock_reservation.expires_at`, `refund.pg_attempts`(증가시키는 코드가 없어 멱등 키 접미사가 쓰이지 않음) | `JpaPaymentRepository.java:53`, `JpaStockReservationRepository.java:19` |
| 10 | 설계 문서 간 어긋남(미구현 영역): 제재 회원의 로그인과 문의 허용 방식(4-x 대 5-4), `SUSPENDED` 재개 조건(04 대 SUB-2), 진행 중 구독 주문과 구독 상태 변경(`PAUSED`/`CANCELLED`/`TERMINATED`)의 대사 처리, 정산 지급 재시도 주기(04 §7 대 3-4) | 각 단계 문서 |
