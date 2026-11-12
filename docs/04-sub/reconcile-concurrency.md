# 서브: 대사의 동시 실행

> 버전 0.1 · 2026-11-12 · 같은 주문의 대사가 동시에 돌지 않게 `reconcileOne`이 주문마다 advisory lock을 잡는다(단계 ID SUB-19)

> **서브**: 로드맵에서 뺀 기능이다. Stage 1(Part 6) 뒤에 한다.
> **따르는 규칙**: 로드맵 Part 1~6의 공통 규칙. 대사 규격의 출발점은 [로드맵 2-6](../03-roadmap/part2-order-money.md)과 [6-3](../03-roadmap/part6-ops-wrapup.md)이다.

---

## SUB-19. 대사의 동시 실행 (S)

**목표**: 같은 주문을 대사 잡과 관리자 대사(또는 관리자 대사 두 번)가 동시에 처리하지 않는다. 늦은 쪽은 기다리지 않고 주문의 지금 상태를 돌려주고, 주문이 `PAID`로 끝난 결제를 취소하지 않는다(INV-02: PG 승인 1건은 정확히 1개 주문에 1회만 반영되고, 반영되지 못한 승인만 취소된다).

**드러나는 때**: 관리자 대사(`POST /admin/api/payments/{orderId}/reconcile`)는 `JobLock`을 거치지 않아, 대사 잡이나 같은 요청의 두 번째 클릭과 같은 주문에서 겹칠 때만 드러난다. 격리 복사본에서 아래 빈 곳 두 개를 각각 3회 재현했다(모두 주문 `PAID`·결제 `CANCELLED`·가짜 PG 취소 요청 1건). 2번은 일시적인 실패를 스텁으로 넣었다.

### 빈 곳

같은 주문의 대사 두 흐름이 겹칠 때 생긴다. 위치는 모두 `OrderReconcileService.reconcileOne`이다.

| # | 겹치는 순서 | 깨지는 상황 |
|---|---|---|
| 1 | 두 흐름이 모두 주문을 `PAYMENT_IN_PROGRESS`로 읽고 `applyPaymentState`에 들어간다 | 먼저 커밋한 쪽이 `PAID`로 끝낸다. 늦은 쪽은 재고 확정이 0행으로 지나간 뒤 `Order.markPaid`가 커밋된 `PAID` 가게주문을 지연 로딩으로 읽어 `INVALID_STATE_TRANSITION`을 던진다. `closeOrder`의 catch는 이 예외를 완료 실패로 보고, 승인 5분이 지난 결제면 `order-compensate-{orderId}` 키로 PG 전액 취소를 부른다. 취소가 `DONE`이면 이어지는 `failOrder`가 `PAID → PAYMENT_FAILED` 전이에서 던져 롤백되므로, 주문은 `PAID`(판매자 발송 대상)인데 결제는 `CANCELLED`로 남는다 |
| 2 | 먼저 쪽의 완료가 일시적인 오류로 실패해 롤백되고, 먼저 쪽이 보상하기 전에 다른 쪽이 주문을 `PAID`로 끝낸다 | 먼저 쪽이 결제를 취소하고 `failOrder`가 롤백되어 1번과 같은 결과가 된다 |

바뀐 뒤의 동작은 다음과 같다.

| 상황 | 결과 |
|---|---|
| 같은 주문의 대사가 겹침 | 늦은 쪽은 락을 잡지 못하면 기다리지 않고 주문의 지금 상태를 돌려준다. 먼저 쪽이 끝나기 전이면 `PAYMENT_IN_PROGRESS`다. 잡은 다음 주기(1분)에 다시 보고, 관리자는 다시 눌러 결과를 본다 |
| 완료가 실패해 보상하는 중 | 먼저 쪽이 보상까지 락을 쥐므로 다른 대사가 끼어들지 못한다. 보상은 지금처럼 PG 취소 → 주문 `PAYMENT_FAILED`다 |
| 다른 주문의 대사 | 서로 막지 않는다 |
| 결제 확인(`ConfirmPaymentService`)과 대사 | 바뀌지 않는다. 대사는 `updated_at`이 30초 안인 주문을 건너뛰므로 결제 확인이 30초 넘게 걸릴 때만 겹치고, 승인이 방금이라 보상까지 가지 않는다 |

### 고른 방법

| 방법 | 막는 겹침 | 판단 |
|---|---|---|
| `reconcileOne` 전체를 주문마다 advisory lock으로 감싼다(할 일) | 같은 주문의 대사끼리. 보상까지 락 안이라 빈 곳 1·2번 모두 | 고른다. 격리 복사본에서 1·2번 재현 실험이 각각 3회 모두 주문과 결제가 맞게 끝났다(1번은 주문 `PAID`·결제 `APPROVED`·PG 취소 0건, 2번은 주문 `PAYMENT_FAILED`·결제 `CANCELLED`). 2번에서 다른 대사가 끼어드는 시점을 PG 취소 직전으로 옮겨도 3회 모두 같았다. 락을 쥔 채 PG를 부르므로 그동안 락용 연결 하나를 쥔다. `JobLock`이 대사 배치 동안 그러는 것과 같다 |
| 관리자 대사도 잡과 같은 이름(`reconcile-orders`)으로 `JobLock`을 잡는다 | 대사 잡과 관리자 대사, 관리자 대사 두 번 | 고르지 않는다. `pg_try_advisory_lock`이라 잡이 1분마다 최대 100건을 PG 조회와 함께 처리하는 동안 관리자 대사가 다른 주문이어도 거절된다 |
| `applyPaymentState`가 주문을 `findByIdForUpdate`로 읽는다 | 결과 반영이 겹치는 1번(결제 확인 포함) | 고르지 않는다. 1번은 막지만(복사본에서 3회 모두 주문 `PAID`·결제 `APPROVED`·PG 취소 0건) 락이 결과 반영 트랜잭션 끝에 풀리므로 2번은 막지 못한다. 복사본에서 이 한 줄만 넣고, 첫 결과 반영이 실패한 뒤 PG 취소 직전에 다른 대사가 주문을 끝내게 하면 3회 모두 주문 `PAID`·결제 `CANCELLED`였다. 보상 전에 주문을 다시 읽어도 이 시점(다시 읽은 뒤, 취소 전)은 남는다 |
| catch에서 예외 종류를 보고 보상을 거른다 | - | 고르지 않는다. 보상 대상인 2-6 완료 확인 8·9번(가게주문이 이미 `CANCELLED`)도 같은 `INVALID_STATE_TRANSITION`이라 구분할 수 없다 |

### 할 일

**1) `JobLock`** — 값을 돌려주는 `public <T> Optional<T> callExclusively(String name, Supplier<T> task)`를 더한다. `runExclusively`의 순서를 옮겨 온다: 연결 하나를 얻어 `pg_try_advisory_lock`으로 잡고, 못 잡으면 `Optional.empty()`, 잡으면 `task.get()`을 `Optional.of`로 돌려주고 `finally`에서 푼다. 키는 지금처럼 `"job:" + name`이고 `SQLException`은 `RuntimeException`으로 감싼다. `runExclusively`는 `callExclusively(jobName, () -> { task.run(); return true; }).isPresent()`로 바꿔 락 코드를 한 곳에 둔다. 기존 `JobLockTest` 1~3번이 이 경로를 그대로 확인한다.

**2) `OrderReconcileService.reconcileOne`** — 지금 본문을 `private OrderStatus reconcileLocked(UUID orderId)`로 옮긴다. `reconcileOne`은 `jobLock.callExclusively("reconcile-order:" + orderId, () -> reconcileLocked(orderId))`를 부르고, 비어 있으면(다른 대사가 이 주문을 처리 중) `orderRepository.findById(orderId)`로 읽은 상태를 돌려준다(없으면 `ORDER_NOT_FOUND`). `OrderReconcileService`에 `JobLock`을 주입한다.

`reconcileBatch`와 관리자 대사(`AdminReconcileController`)가 모두 `reconcileOne`을 거치므로 둘 다 이 락을 잡는다. 잡 전체의 `reconcile-orders` 락과는 이름이 달라 서로 막지 않는다. `reconcileLocked`는 지금처럼 트랜잭션 없이 돌고, 락용 연결은 트랜잭션이 쓰는 연결과 따로다.

### 완료 확인

케이스는 [로드맵 README 4.1](../03-roadmap/README.md)의 기준으로 골랐다. 격리 복사본에서 세 케이스가 할 일 1·2를 넣으면 통과하고 할 일 2를 빼면 실패하는 것을 확인했다. 공통 준비(주문 `PAYMENT_IN_PROGRESS`, 결제 `UNKNOWN`, `updated_at` 1분 전인 대사 대상 주문)는 [2-6 완료 확인](../03-roadmap/part2-order-money.md)의 `OrderReconcileServiceTest` 공통 준비와 같다. 세 케이스 모두 공통 준비 뒤에 JDBC `UPDATE payment.payment SET status = 'APPROVED', method = '카드', approved_at = ? WHERE order_id = ?`(10분 전)로 결제를 승인 5분이 지난 상태로 만든다(아래 "결제 승인 10분 전").

#### `order/application/OrderReconcileServiceTest`

테스트 클래스에 `@MockitoSpyBean ProductApi productApi` 필드를 더하고, `OrderPaymentApplier applier`를 생성자 주입에서 `@MockitoSpyBean` 필드로 바꾼다. 스텁하지 않은 테스트(7번 등)는 실제 메서드를 쓴다.

**10. 같은 주문을 동시에 대사해도 결제를 취소하지 않는다** — 준비: 공통 준비 → 결제 승인 10분 전 → `CyclicBarrier barrier = new CyclicBarrier(2)`와 `willAnswer(inv -> { try { barrier.await(2, TimeUnit.SECONDS); } catch (TimeoutException | BrokenBarrierException e) { } return inv.callRealMethod(); }).given(productApi).commitReservation(orderId)`. 두 흐름이 모두 주문을 읽고 재고 확정에 들어온 뒤에야 지나가게 해서, 락이 없으면 빈 곳 1번의 겹침이 매번 생긴다. 할 일 2를 넣으면 늦은 쪽은 락을 못 잡고 바로 돌아가 장벽에 오지 않으므로, 먼저 쪽은 2초 시간 제한 뒤 진행한다. 동작: `ConcurrencyRunner.run(2, () -> orderReconcileService.reconcileOne(orderId))`.
- 가짜 서버 요청(pg.requests("/v1/payments/" + snapshotOf(orderId).payment().paymentKey() + "/cancel")): `isEmpty()`.
- DB(snapshotOf(orderId)): `status()` = `PAID`.

**11. 다른 대사가 처리 중인 주문은 기다리지 않고 지금 상태를 돌려준다** — 준비: 공통 준비 → 결제 승인 10분 전 → `CountDownLatch entered = new CountDownLatch(1)`, `CountDownLatch release = new CountDownLatch(1)`와 `willAnswer(inv -> { entered.countDown(); release.await(5, TimeUnit.SECONDS); return inv.callRealMethod(); }).given(productApi).commitReservation(orderId)` → `CompletableFuture<OrderStatus> first = CompletableFuture.supplyAsync(() -> orderReconcileService.reconcileOne(orderId))` → `entered.await(5, TimeUnit.SECONDS)`. 먼저 시작한 대사가 락을 쥔 채 재고 확정 직전에서 멈춘다. 동작: `orderReconcileService.reconcileOne(orderId)`. 확인 전에 `release.countDown()`과 `first.get(10, TimeUnit.SECONDS)`로 먼저 시작한 대사를 끝내, 테스트가 끝난 뒤에 남아 돌지 않게 한다.
- 반환값(동작의 `reconcileOne`): `OrderStatus.PAYMENT_IN_PROGRESS`.

**12. 완료가 실패해 보상하는 동안 다른 대사가 주문을 끝내지 않는다** — 준비: 공통 준비 → 결제 승인 10분 전 → `AtomicBoolean failOnce = new AtomicBoolean(true)`, `CountDownLatch entered = new CountDownLatch(1)`, `CountDownLatch release = new CountDownLatch(1)`와 `willAnswer(inv -> { if (failOnce.getAndSet(false)) { entered.countDown(); release.await(5, TimeUnit.SECONDS); throw new IllegalStateException("일시적 실패"); } return inv.callRealMethod(); }).given(applier).applyPaymentState(eq(orderId), any(), any())` → `CompletableFuture<OrderStatus> first = CompletableFuture.supplyAsync(() -> orderReconcileService.reconcileOne(orderId))` → `entered.await(5, TimeUnit.SECONDS)`. 먼저 시작한 대사가 락을 쥔 채 결과 반영 안에서 멈췄다가, 풀리면 일시적 실패를 던지고 보상으로 간다. 실패는 첫 호출 한 번만 낸다. 동작: `orderReconcileService.reconcileOne(orderId)`(끼어든 대사) → `release.countDown()` → `first.get(10, TimeUnit.SECONDS)`로 먼저 시작한 대사가 보상까지 끝내기를 기다린다.
- DB(snapshotOf(orderId)): `status()` = `PAYMENT_FAILED`.

### 문서 반영

단계를 마칠 때 Claude가 고친다.

| 문서 | 고칠 곳 |
|---|---|
| [로드맵 2-5](../03-roadmap/part2-order-money.md) | `JobLock` 표에 `callExclusively`, `runExclusively`가 그것을 부른다는 것 |
| [로드맵 6-3](../03-roadmap/part6-ops-wrapup.md) | `reconcileOne`이 주문마다 락을 잡는다는 것, 경로별 반환 표에 "다른 대사가 처리 중(락을 못 잡음) → 주문의 지금 상태" 행 |
| [알려진 문제](../known-issues.md) | "대사의 동시 실행과 보상 취소", "완료 실패 뒤 다른 대사가 끝낸 주문의 보상 취소" 두 행을 지운다 |

**리뷰 때 물어볼 것**

- 락을 `applyPaymentState`가 아니라 `reconcileOne` 전체에 건 이유는? 빈 곳 2번은 어느 구간에서 생기나?
- 락용 연결은 PG를 부르는 동안 무엇을 하고 있나? 관리자 대사가 몰리면 풀(31)에서 연결을 몇 개 쓰나?
- 앱이 락을 쥔 채 죽으면 그 주문의 락은 어떻게 되나?
- `hashtext`가 서로 다른 두 주문의 키를 같은 값으로 만들면 무엇이 생기나?
