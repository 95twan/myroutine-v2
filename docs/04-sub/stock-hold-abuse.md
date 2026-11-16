# 서브: 재고 점유 남용

> 버전 0.1 · 2026-11-15 · 체크아웃이 회원의 결제 대기 주문 수(3건)와 최근 24시간에 만든 주문 중 결제되지 않은 주문 수(5건)로 거절하게 한다(단계 ID SUB-23)

> **서브**: 로드맵에서 뺀 기능이다. Stage 1(Part 6) 뒤에 한다.
> **따르는 규칙**: 로드맵 Part 1~6의 공통 규칙. 체크아웃 규격의 출발점은 [로드맵 2-2](../03-roadmap/part2-order-money.md)다.

---

## SUB-23. 재고 점유 남용 (S)

**목표**: 결제하지 않을 주문으로 인기 상품 재고를 붙잡는 일을, 한 계정이 동시에 붙잡는 양과 하루에 반복하는 횟수 두 쪽에서 막는다.

**드러나는 때**: 체크아웃은 회원별로 아무것도 세지 않는다([2-2](../03-roadmap/part2-order-money.md) 알고 있는 한계 4번). 재고 100개인 상품이면 아래처럼 된다.

| 경우 | 흐름 | 남는 상태 |
|---|---|---|
| ① 한꺼번에 붙잡기 | 한 회원이 `Idempotency-Key`를 바꿔 가며 체크아웃을 여러 번 보낸다. 주문마다 품목당 최대 99개를 예약한다 | 다른 구매자에게 `OUT_OF_STOCK`. 15분 뒤 만료로 풀린다 |
| ② 다시 붙잡기 | 주문이 만료되거나, 결제 전 취소(`POST /api/orders/{id}/cancel`)로 예약을 풀자마자 다시 체크아웃한다 | 같은 회원이 재고를 계속 붙잡는다 |

재고가 적은 상품은 주문 1\~2건이면 다 붙잡히므로, 동시에 붙잡는 주문 수만 제한해서는 ②를 막지 못한다. ②는 결제하지 않은 주문을 세어야 막힌다. 주문 수 상한은 재고가 많은 상품에서 한 계정이 한 번에 붙잡는 양을 줄인다.

### 빈 곳

- `CheckoutService.checkout`은 장바구니 항목·상품·가게·배송지만 검사하고, 회원의 다른 주문을 읽지 않는다.
- 회원별로 "세고 나서 만들기"만 넣으면 동시에 온 체크아웃이 모두 같은 개수를 읽고 상한을 넘긴다. 남용하는 쪽은 요청을 동시에 보낼 수 있다.

### 정책

- **결제 대기 주문 상한**: 체크아웃할 때 그 회원의 `PENDING_PAYMENT`·`PAYMENT_IN_PROGRESS` 주문이 3건 이상이면 409 `PENDING_ORDER_LIMIT_EXCEEDED`로 거절한다. 응답의 `details.orderIds`에 그 주문 ID를 담아, 화면이 이어서 결제하거나 취소하게 한다.
- **결제하지 않은 주문 제한**: 체크아웃할 때 그 회원이 최근 24시간에 만든 주문 중 `PAID`가 아닌 것(`PENDING_PAYMENT`·`PAYMENT_IN_PROGRESS`·`EXPIRED`·`CANCELLED`·`PAYMENT_FAILED`)이 5건 이상이면 429 `UNPAID_ORDER_LIMIT_EXCEEDED`로 거절한다. `details.retryAfterSeconds`는 24시간 창에서 한 건이 빠져 다시 체크아웃할 수 있게 될 때까지 남은 초다. 세는 주문 중 결제 대기 주문을 결제하면 그보다 일찍 풀린다.
  - 결제 대기·결제 중인 주문도 센다. 끝난 주문만 세면, 끝난 주문이 4건일 때 결제 대기 주문을 3건까지 채우고 모두 만료시켜 하루 7건까지 늘릴 수 있다. 함께 세면 한 계정이 하루에 결제하지 않는 주문은 5건을 넘지 않는다.
  - `CANCELLED`도 센다. 만료만 세면 붙잡은 주문을 바로 취소하고 다시 체크아웃하는 식으로 제한을 피한다. 주문의 `CANCELLED`는 결제 전 취소로만 생긴다(결제 뒤 취소는 품목 상태만 바꾼다).
  - `PAYMENT_FAILED`도 센다. 결제를 시작했다가 일부러 실패시키는 동안에도 재고가 붙잡힌다.
- 상한·건수·창은 설정값이다(`myroutine.order.max-pending-orders: 3`, `unpaid-limit: 5`, `unpaid-window: 24h`).
- 같은 회원의 체크아웃은 장바구니 행을 잠가 하나씩 실행한다. 동시에 보낸 요청에서도 결제 대기 주문은 3건, 결제하지 않은 주문은 5건을 넘지 않는다.

체크아웃 때 재고를 예약하는 것은 구매자가 카드 인증을 마친 뒤 품절을 보지 않게 하는 정책이고([2-2](../03-roadmap/part2-order-money.md)), 그 대가로 결제하지 않을 주문에도 재고가 잠깐 잡힌다. 위 제한은 이 점유를 계정당 양과 횟수로 줄이며, 아래 둘은 이 정책이 받아들이는 것이다.

- 계정마다 따로 센다. 가입은 이메일 인증이 필요하지만 인증 발송 제한이 이메일 주소별이라, 주소가 많으면 계정을 여러 개 만들어 붙잡을 수 있다. 한 사람을 한 계정으로 묶는 수단(휴대폰 본인인증, 기기 식별, IP 제한)은 두지 않는다.
- 결제하지 않은 횟수로 남용을 가려내므로, 정상 구매자도 하루에 결제창을 5번 닫으면(화면이 결제 전 취소를 부른다) 막힌다.

### 고른 방법

| 방법 | 막는 경우 | 판단 |
|---|---|---|
| 결제 대기 주문 상한 + 결제하지 않은 주문 제한, 장바구니 행 잠금(할 일) | ①, ② | 고른다. 바뀌는 곳이 `CheckoutService`와 조회 세 개, 에러 코드 두 개다 |
| 결제 대기 주문 상한만 | ①(재고가 많은 상품) | 고르지 않는다. 재고가 적은 상품은 1\~2건으로 다 붙잡히고, ②가 그대로 남는다 |
| 상한을 잠그지 않고 센다(best-effort) | ①을 일부 | 고르지 않는다. 동시에 보낸 요청이 상한을 넘는다. 잠금 비용은 조회 하나를 `FOR UPDATE`로 바꾸는 것뿐이다 |
| 회원별 체크아웃 속도 제한(Redis `setIfAbsent`, 429 `RATE_LIMITED`) | 없음 | 고르지 않는다. 주문이 15분 남아 있어, 10초에 한 번만 허용해도 결제 대기 주문이 90건 쌓인다. 상한이 붙잡는 양을 이미 막으므로 더 막는 것은 요청 빈도뿐이다 |
| IP별 체크아웃 속도 제한 | 여러 계정의 ①을 일부 | 고르지 않는다. Cloudflare Tunnel 뒤에서 실제 클라이언트 IP를 읽는 설정(`CF-Connecting-IP`를 믿을 범위)이 먼저 필요하고, 위와 같은 이유로 붙잡는 양을 막지 못한다 |
| 체크아웃 때 잡지 않고 결제 때 차감(Toss 승인 직전 또는 승인 뒤) | ①, ② | 고르지 않는다. 승인 직전 차감은 재고가 모자라면 카드 인증 뒤 품절을, 승인 뒤 차감은 결제 뒤 환불을 만든다. 둘 다 2-2 정책이 막는 결과다. 승인 직전 차감도 승인 응답이 시간 초과면 대사가 판정할 때까지 재고를 잡아 둬야 해서 예약은 남는다 |

장바구니 행을 잠그는 이유: 장바구니는 회원당 한 행(`uk_cart_member`)이고 체크아웃이 처음에 읽는 행이라, 같은 회원의 체크아웃을 order 모듈 안에서 하나씩 실행하게 할 수 있다. 회원 행은 member 모듈 것이라 order가 잠그지 않는다.

### 할 일

**1) order.domain**

| 대상 | 규격 |
|---|---|
| `OrderErrorCode` | `PENDING_ORDER_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "결제 대기 중인 주문이 많습니다. 결제를 마치거나 취소한 뒤, 또는 잠시 뒤 다시 시도해 주세요.")`, `UNPAID_ORDER_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "결제하지 않은 주문이 많아 잠시 주문할 수 없습니다.")`를 더한다 |
| `CartRepository` | `Optional<Cart> findByMemberIdForUpdate(UUID memberId)` |
| `OrderRepository` | `List<UUID> findIdsByMemberIdAndStatusIn(UUID memberId, Collection<OrderStatus> statuses)`, `List<Instant> findRecentCreatedAts(UUID memberId, Collection<OrderStatus> statuses, Instant since, Limit limit)` |

**2) order.infrastructure**

| 메서드 | 쿼리 |
|---|---|
| `JpaCartRepository.findByMemberIdForUpdate` | `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `select c from Cart c where c.memberId = :memberId` |
| `JpaOrderRepository.findIdsByMemberIdAndStatusIn` | `select o.id from Order o where o.memberId = :memberId and o.status in :statuses` |
| `JpaOrderRepository.findRecentCreatedAts` | `select o.createdAt from Order o where o.memberId = :memberId and o.status in :statuses and o.createdAt > :since order by o.createdAt desc` |

두 주문 조회는 기존 인덱스 `idx_orders_member_id_created_at (member_id, created_at DESC, id DESC)`를 쓴다. 새 마이그레이션은 없다.

**3) order.application**

`OrderProperties`에 `@Positive int maxPendingOrders`, `@Positive int unpaidLimit`, `@NotNull Duration unpaidWindow`를 더하고, `application.yaml`의 `myroutine.order`에 `max-pending-orders: 3`, `unpaid-limit: 5`, `unpaid-window: 24h`를 넣는다.

`CheckoutService.checkout`의 앞부분을 아래 순서로 바꾼다. 나머지(장바구니 항목 확인부터 예약까지)는 그대로다.

| 순서 | 규격 |
|---|---|
| ① | `cartRepository.findByMemberIdForUpdate(memberId)`로 장바구니를 잠그며 읽는다. 없으면 지금처럼 404 `CART_ITEM_NOT_FOUND` |
| ② | `findIdsByMemberIdAndStatusIn(memberId, List.of(PENDING_PAYMENT, PAYMENT_IN_PROGRESS))`의 크기가 `maxPendingOrders` 이상이면 `BusinessException(PENDING_ORDER_LIMIT_EXCEEDED, Map.of("orderIds", 그 목록))` |
| ③ | `now = clock.instant()`. `findRecentCreatedAts(memberId, List.of(PENDING_PAYMENT, PAYMENT_IN_PROGRESS, EXPIRED, CANCELLED, PAYMENT_FAILED), now.minus(unpaidWindow), Limit.of(unpaidLimit))`의 크기가 `unpaidLimit`이면 `BusinessException(UNPAID_ORDER_LIMIT_EXCEEDED, Map.of("retryAfterSeconds", 초))`. 초는 마지막 원소(최근 `unpaidLimit`번째 주문의 `createdAt`)에 `unpaidWindow`를 더한 시각까지 남은 초이고, 1보다 작으면 1이다. 그 주문이 창에서 빠지면 남는 건수가 `unpaidLimit - 1`이 된다 |

①을 ②보다 먼저 해야 동시 요청이 막힌다. 두 요청이 같은 회원의 장바구니 행을 잠그려 하면 뒤 요청은 앞 요청이 커밋할 때까지 기다린다. Postgres의 기본 격리 수준(READ COMMITTED)은 문장마다 그때까지 커밋된 데이터를 보므로, 뒤 요청의 ②는 앞 요청이 만든 주문을 센다. ②·③은 상품·가게를 읽기 전에 해서 거절할 요청이 재고 행을 잠그지 않게 한다. 지금 `checkout` 뒤쪽의 `Instant now = clock.instant()`는 ③의 `now`를 쓴다.

멱등 키는 예외로 끝나면 지워지므로(2-2 정책), 거절된 요청을 같은 키로 다시 보낼 수 있다.

### 완료 확인

케이스는 [로드맵 README 4.1](../03-roadmap/README.md)의 기준으로 골랐다. 격리 복사본에서 할 일 1~3을 넣으면 1~8번이 통과하고, ①의 잠금을 `findByMemberId`로 바꾸거나 ② 뒤로 옮기면 8번이 실패하고(5건 모두 201), ③의 상태 목록에서 `PENDING_PAYMENT`·`PAYMENT_IN_PROGRESS`를 빼면 4번이 실패하는 것을 확인했다. 아래 "기존 테스트"를 고치면 전체 테스트가 통과한다.

새로 만드는 테스트 헬퍼는 `TestFixtures.setOrderCreatedAt(UUID orderId, Instant createdAt)` 하나다. `setOrderUpdatedAt`처럼 `UPDATE orders.orders SET created_at = ? WHERE id = ?`를 실행한다. 결제 전 취소는 `PendingOrderCancelService.cancel(buyerId, orderId)`를 테스트 클래스에 주입해 부른다.

#### `order/web/CheckoutLimitTest`

모든 케이스의 공통 준비는 판매자(`signup("seller@test.com")` → `openShop` → `registerProduct(sellerId, shopId, 1000, 100)`)와 구매자(`signup("buyer@test.com")`, `login`)다. 동작은 구매자 토큰과 새 `Idempotency-Key`로 `POST /api/orders/checkout`이고, 요청은 `addToCart(buyerId, productId, 1)`로 담은 항목과 `address(buyerId)`다. 준비의 주문은 `createPendingOrder(buyerId, productId)`로 만든다(같은 장바구니 항목의 수량이 1씩 늘어난다).

| 케이스 | 준비 | 확인할 것 |
|---|---|---|
| 1. 결제 대기 주문이 3건이면 체크아웃을 실패한다. (409 PENDING_ORDER_LIMIT_EXCEEDED) | `createPendingOrder` 3번 | 응답: 상태 409, `$.code` = `PENDING_ORDER_LIMIT_EXCEEDED`, `$.details.orderIds`가 세 주문 ID(순서 무관). DB: 구매자의 주문 수 = 3 |
| 2. 결제 중인 주문도 결제 대기로 센다. | `pg.on("/v1/payments/confirm", Behavior.delay(1000))` → `createPendingOrder` → `confirmPayment`(`PAYMENT_IN_PROGRESS`로 남는다) → `pg.reset()` → `createPendingOrder` 2번 | 응답: 상태 409, `$.code` = `PENDING_ORDER_LIMIT_EXCEEDED` |
| 3. 결제 대기 주문이 2건이면 체크아웃한다. | `createPendingOrder` 2번 | 응답: 상태 201. DB: 구매자의 주문 수 = 3 |
| 4. 최근 24시간에 만든 주문 중 결제되지 않은 주문이 5건이면 체크아웃을 실패한다. (429 UNPAID_ORDER_LIMIT_EXCEEDED) | 상태마다 한 건씩 만든다. `createPendingOrder` → `cancel`, `createPendingOrder` → `setOrderExpired(orderId, Instant.now().minusSeconds(1))` → `orderExpirationService.expireBatch()`, `pg.on("/v1/payments/confirm", Behavior.delay(1000))` → `createPendingOrder` → `confirmPayment`(`PAYMENT_IN_PROGRESS`), `pg.on("/v1/payments/confirm", Behavior.reject("REJECT_CARD_COMPANY"))` → `createPendingOrder` → `confirmPayment`(`PAYMENT_FAILED`가 되며 던지는 예외는 `catch`로 받는다), `createPendingOrder`(`PENDING_PAYMENT`) | 응답: 상태 429, `$.code` = `UNPAID_ORDER_LIMIT_EXCEEDED`. DB: 구매자의 주문 수 = 5 |
| 5. 결제하지 않은 주문 제한의 다시 시도할 시간은 다섯 번째로 최근 주문이 24시간 창에서 빠질 때까지다. | `createPendingOrder` → `cancel` 5번 → 첫 주문에 `setOrderCreatedAt(firstOrderId, Instant.now().minus(Duration.ofHours(23)))` | 응답: 상태 429, `$.details.retryAfterSeconds`가 3590 이상 3600 이하 |
| 6. 24시간이 지난 결제되지 않은 주문은 세지 않는다. | `createPendingOrder` → `cancel` 5번 → 첫 주문에 `setOrderCreatedAt(firstOrderId, Instant.now().minus(Duration.ofHours(24)).minusSeconds(60))` | 응답: 상태 201 |
| 7. 결제한 주문은 결제하지 않은 주문으로 세지 않는다. | `createPaidOrder(buyerId, productId)` 5번 | 응답: 상태 201 |

1번의 주문 수는 거절 전에 주문이 저장됐다가 롤백되지 않는 구현을 잡는다. 4번이 상태 다섯을 한 케이스에 섞은 것은, 하나라도 세지 않으면 4건이 되어 201로 같은 이유로 실패하기 때문이다.

#### `order/application/CheckoutLimitConcurrencyTest`

**8. 같은 회원이 동시에 체크아웃해도 결제 대기 주문은 3건을 넘지 않는다.** — 준비: 위 공통 준비, `addToCart(buyerId, productId, 1)`, `address(buyerId)`. 동작: `ConcurrencyRunner.run(5, ...)`로 각자 새 `Idempotency-Key`를 붙인 `POST /api/orders/checkout`(`CheckoutIdempotencyConcurrencyTest`와 같은 모양).
- 응답: 모든 결과가 `ok`, 상태 201이 3개, 409(`$.code` = `PENDING_ORDER_LIMIT_EXCEEDED`)가 2개.
- DB: `SELECT count(*) FROM orders.orders WHERE member_id = ?` = 3.

스레드를 5개로 둔 것은 테스트의 커넥션 풀(`maximum-pool-size: 10`) 안에서 잠금을 기다리는 요청이 커넥션을 다 쓰지 않게 하려는 것이다.

### 기존 테스트

`OrderQueryTest`의 "내 주문 목록을 커서 페이징으로 조회한다."는 한 구매자로 결제 대기 주문을 6건 만들어 4번째 체크아웃에서 409로 막힌다. 목록에 나오는 주문이면 되므로 주문을 결제 완료로 만든다.

- 준비의 `testFixtures.checkout(buyerId, List.of(cartItemId), addressId)` 5번을 `testFixtures.createPaidOrder(buyerId, productId)` 5번으로 바꾸고, 쓰지 않게 되는 `addressId`·`cartItemId` 줄을 지운다.
- 첫 페이지를 읽은 뒤의 `testFixtures.checkout(buyerId, List.of(cartItemId), addressId)`도 `testFixtures.createPaidOrder(buyerId, productId)`로 바꾼다.

다른 테스트는 상한에 걸리지 않는다.

### 문서 반영

단계를 마칠 때 Claude가 고친다.

| 문서 | 고칠 곳 |
|---|---|
| [요구사항 §2](../01-requirements/README.md) | 정책 표에 POL-20(재고 점유 제한)을 더한다 |
| [로드맵 2-2](../03-roadmap/part2-order-money.md) | 알고 있는 한계 4번을 이 단계 링크로 바꾸고, "추가 항목"의 재고 점유 남용 방어를 지운다(1인당 구매 수량 제한은 남긴다) |
| [API 명세 §3.1](../02-design/07-api-spec.md) | 체크아웃 에러에 409 `PENDING_ORDER_LIMIT_EXCEEDED`(`details.orderIds`), 429 `UNPAID_ORDER_LIMIT_EXCEEDED`(`details.retryAfterSeconds`) |
| [구독](subscription.md) | SUB-3: 회차 주문도 `orders`에 생기므로 두 주문 조회에 `type = NORMAL` 조건을 더한다 |
| [알려진 문제](../known-issues.md) | "재고 점유 남용" 행을 지운다 |

**리뷰 때 물어볼 것**

- 결제 대기 주문 상한만으로는 재고 100개 상품을 지키지 못하는 이유는?
- 장바구니 행을 잠그지 않으면 8번은 어떻게 되나? 잠금을 ② 뒤로 옮기면?
- 결제 전 취소와 결제 대기 주문을 결제하지 않은 주문으로 세는 이유는? 각각 세지 않으면 어떤 순서로 제한을 피하나?
- `retryAfterSeconds`를 가장 오래된 주문으로 계산하면 언제 틀리나?
