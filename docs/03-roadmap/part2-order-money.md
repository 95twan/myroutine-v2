# Part 2. 주문과 돈

> 버전 0.5 · 2026-10-02 · **덜어내기**: 주문번호·바로 구매·멱등 키 재점유와 정리 잡·PG 호출 로그 테이블·트랜잭션 안 호출 가드·결제창 실패 통지 API·대사 백오프·판매자 주문 기간 필터를 "제안"으로 옮기고, 제약 이름 매핑을 없앴다
> 0.4 · 2026-10-02 · **예치금(지갑) 제거**: 2-1·2-2(지갑)·2-9(충전) 삭제, 결제는 Toss 단일 수단, 환불은 PG 부분취소만. 단계 번호를 다시 매김(이전 2-3 → 2-1 …)
> 0.3 · 이 문서만 보고 개발할 수 있게 구체화

> **끝나면**: 여러 가게의 상품을 카드(Toss)로 한 번에 결제하고, 판매자가 발송하고, 구매자가 취소·반품·구매확정까지 할 수 있다. 결제 응답이 불확실해도 결제와 재고가 어긋나지 않는다.
> **인프라 추가**: 없음 (Toss는 테스트용 가짜 PG 서버로 대체, 실제 Toss 테스트 키는 수동 확인용)
> **이 Part가 프로젝트의 핵심이다.** 이력서의 "정합성" 이야기는 대부분 여기서 나온다.
> **릴리스**: `v0.2.0`

| 단계 | 제목 | 크기 |
|---|---|---|
| 2-1 | 장바구니 | S |
| 2-2 | 체크아웃: 주문 생성과 재고 예약 (+ 멱등 API, 주문 조회) | L |
| 2-3 | Toss 클라이언트와 가짜 PG 서버 | M |
| 2-4 | PG 결제 승인 | L |
| 2-5 | 주문 만료 잡 (+ 잡 락) | M |
| 2-6 | 결제 대사 (+ PG 취소) | M |
| 2-7 | 판매자 주문 관리: 발송 · 배송완료 | M |
| 2-8 | 품목 취소와 환불 | L |
| 2-9 | 반품 | S |
| 2-10 | 구매확정 (수동 · 자동) | M |

---

## Part 2 공통 규칙 (Part 1 공통 규칙에 더해서)

> [Part 1 공통 규칙](part1-foundation.md#part-1-공통-규칙-모든-단계에-적용) A~I는 그대로 적용된다. 아래는 Part 2에서 추가되는 것이다.
> 리뷰 기준도 같다: 이 문서의 구현 규격·완료 확인·공통 규칙에 어긋나는 것만 지적하고, 나머지는 "제안"으로 분리한다.

### J. 모듈과 허용 의존 (Part 2 끝 기준)

| 모듈 | `package-info.java`의 `allowedDependencies` | 공개 API (`{module}.api`) |
|---|---|---|
| member | `common` | `MemberApi` (2-2) |
| shop | `common` | `ShopApi` |
| product | `common`, `shop::api` | `ProductApi` |
| payment | `common` | `PaymentApi` (2-4) |
| order | `common`, `product::api`, `shop::api`, `member::api`, `payment::api` | 없음 (Part 3에서 추가) |

- `{module}/api/package-info.java`에 `@NamedInterface("api")`. 구현체 `XxxApiImpl`은 `{module}.application`의 **package-private** 클래스다.
- 모듈 API의 쓰기 메서드는 비즈니스 키(orderId, refundId)를 받아 **같은 키로 다시 불러도 결과가 같게** 만든다.
- 모듈 API 메서드는 `@Transactional`(REQUIRED)이라 호출자 트랜잭션에 합류한다. 예외로 표시한 메서드(외부 호출을 하는 `PaymentApi.callConfirm` 등)만 트랜잭션 밖에서 불러야 한다.

### K. 외부 호출과 트랜잭션
- `@Transactional` 메서드 안에서 Toss를 부르지 않는다. 외부 호출이 있는 유스케이스 서비스는 **클래스에 `@Transactional`을 붙이지 않고**, Boot가 만들어 주는 `TransactionTemplate` 빈으로 구간을 나눈다.
```java
Started started = tx.execute(status -> begin(...));       // 트랜잭션 1: 상태 선점 + 커밋
PgOutcome outcome = paymentApi.callConfirm(started.paymentId());   // 트랜잭션 밖
return tx.execute(status -> applier.apply(...));          // 트랜잭션 2: 결과 반영
```
- 같은 클래스의 `@Transactional` 메서드를 `this.method()`로 부르면 트랜잭션이 적용되지 않는다. 결과 반영처럼 여러 곳에서 재사용하는 트랜잭션 로직은 **별도 빈**으로 뺀다.

### L. 상태 전이 방식 (두 가지)
| 방식 | 언제 | 구현 |
|---|---|---|
| CAS UPDATE | 엔티티를 읽지 않고 "지금 이 상태일 때만" 바꿔야 할 때 (결제 시작, 만료, 결제 결과 반영) | `@Modifying(clearAutomatically = true) @Query("update X x set x.status = :to, x.version = x.version + 1, ... where x.id = :id and x.status = :from")` → 반환 `int`. 1이면 내가 바꿈, 0이면 이미 다른 흐름이 바꿈 |
| 엔티티 메서드 + `@Version` | 그 외 (애그리거트 안의 여러 행을 함께 바꿀 때) | `order.markPaid(now)` 같은 도메인 메서드. 동시 수정은 낙관적 락으로 한쪽 실패 |

- CAS 쿼리 뒤에 엔티티가 필요하면 **CAS 다음에** 조회한다(`clearAutomatically`가 1차 캐시를 비우므로, 앞서 읽은 엔티티는 준영속이 된다).
- enum은 쿼리에 문자열로 쓰지 않고 파라미터로 바인딩한다.

### M. 시간 흉내 내기
- 테스트에서 "15분 경과", "7일 경과"는 `Clock`을 바꾸지 않고 **DB의 시각 컬럼을 과거로 UPDATE**해서 만든다(예: `UPDATE orders.orders SET expires_at = now() - interval '1 minute' WHERE id = ?`). 컨텍스트를 새로 띄우지 않아 빠르다.
- 잡은 테스트에서 직접 호출한다. `application-test.yaml`에 `myroutine.scheduling.enabled: false`(2-5에서 만듦)를 둬서 스케줄러가 테스트 중에 끼어들지 않게 한다.

### N. 테스트 지원 (Part 2에서 추가)
| 클래스 | 위치 | 단계 |
|---|---|---|
| `TestFixtures` 확장 | `support/TestFixtures` | 각 단계: `openShop(memberId)`, `registerProduct(memberId, shopId, price, stock)`, `address(memberId)`, `checkout(...)` 등 필요한 헬퍼를 단계마다 추가 |
| `FakePgServer` | `support/FakePgServer`, `support/Behavior` | 2-3 |
| `ConcurrencyRunner` | `support/ConcurrencyRunner` | 2-1(장바구니 동시성 테스트에서 처음 사용): `static <T> List<Result<T>> run(int threads, Callable<T> task)` — 스레드 풀 + latch 3개로 동시에 출발시키고 결과(성공 값 또는 예외)를 모아 반환. 1-9의 동시성 테스트에서 쓴 코드를 여기로 옮겨 재사용한다 |

### O. Part 2 에러 코드

| 코드 | HTTP | 메시지 | 정의 위치 | 단계 |
|---|---|---|---|---|
| `CART_ITEM_NOT_FOUND` | 404 | 장바구니 항목을 찾을 수 없습니다. | `OrderErrorCode` | 2-1 |
| `CART_PRODUCT_NOT_FOUND` | 404 | 상품을 찾을 수 없습니다. | `OrderErrorCode` | 2-1 |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | Idempotency-Key 헤더가 필요합니다. | `CommonErrorCode` | 2-2 |
| `IDEMPOTENCY_IN_PROGRESS` | 409 | 같은 요청을 처리하고 있습니다. | `CommonErrorCode` | 2-2 |
| `IDEMPOTENCY_KEY_REUSED` | 422 | 같은 Idempotency-Key로 다른 요청을 보냈습니다. | `CommonErrorCode` | 2-2 |
| `ORDER_NOT_FOUND` | 404 | 주문을 찾을 수 없습니다. | `OrderErrorCode` | 2-2 |
| `OWN_SHOP_PRODUCT` | 422 | 자기 가게 상품은 주문할 수 없습니다. | `OrderErrorCode` | 2-2 |
| `PAYMENT_FAILED` | 402 | 결제에 실패했습니다. | `CommonErrorCode` | 2-4 |
| `PG_ERROR` | 502 | 결제사 처리 중 오류가 발생했습니다. | `CommonErrorCode` | 2-4 |
| `ORDER_EXPIRED` | 409 | 주문 유효시간이 지났습니다. | `OrderErrorCode` | 2-4 |
| `ORDER_ALREADY_PAID` | 409 | 이미 결제된 주문입니다. | `OrderErrorCode` | 2-4 |
| `AMOUNT_MISMATCH` | 422 | 결제 금액이 주문 금액과 다릅니다. | `CommonErrorCode` | 2-4 |
| `SHOP_ORDER_NOT_FOUND` | 404 | 가게 주문을 찾을 수 없습니다. | `OrderErrorCode` | 2-7 |
| `ORDER_LINE_NOT_FOUND` | 404 | 주문 품목을 찾을 수 없습니다. | `OrderErrorCode` | 2-8 |
| `REFUND_IN_PROGRESS` | 409 | 진행 중인 환불이 있습니다. | `OrderErrorCode` | 2-8 |
| `REFUND_NOT_FOUND` | 404 | 환불 요청을 찾을 수 없습니다. | `OrderErrorCode` | 2-9 |
| `RETURN_NOT_ALLOWED` | 409 | 반품을 요청할 수 없는 품목입니다. | `OrderErrorCode` | 2-9 |

- `PAYMENT_FAILED`·`PG_ERROR`·`AMOUNT_MISMATCH`는 order와 payment가 함께 쓰므로 `CommonErrorCode`에 둔다.

---

## 2-1. 장바구니 (S)

**목표**: 장바구니 담기·수량 변경·삭제·조회. 조회할 때 **현재** 가격·판매 상태·재고 여부를 보여준다.

**왜 지금**: 체크아웃의 입력이다. order 모듈의 첫 코드다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| upsert | `INSERT ... ON CONFLICT (cart_id, product_id) DO UPDATE SET quantity = ...` — "없으면 넣고 있으면 수량 합산"을 한 문장으로. 동시에 담아도 행이 하나 |
| 배치 조회 | 항목마다 상품을 조회하지 않고 ID를 모아 `ProductApi.getForCheckout(ids)` **한 번** |

**정책 (이 단계에서 정함)**
- 항목당 수량 1~99. 합산 결과가 99를 넘으면 99로 맞춘다.
- 담을 때는 상품이 존재하는지만 확인한다(판매 중이 아니어도 담을 수는 있고, 조회 시 "구매 불가"로 표시).
- 장바구니 행은 처음 담을 때 만든다.

### 할 일

**1) 마이그레이션** — `db/migration/order/V{...}__order_create_cart.sql` (`CREATE SCHEMA IF NOT EXISTS orders;`)

`orders.cart`: id `pk_cart`, member_id uuid X `uk_cart_member`, created_at, updated_at

`orders.cart_item`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_cart_item` |
| cart_id | uuid | X | `fk_cart_item_cart` |
| product_id | uuid | X | |
| quantity | int | X | `ck_cart_item_quantity`: `quantity BETWEEN 1 AND 99` |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- unique `uk_cart_item_cart_product (cart_id, product_id)`

**2) order.domain**

| 클래스 | 규격 |
|---|---|
| `Cart` | `@Entity @Table(name = "cart", schema = "orders")`, `extends BaseTimeEntity`, `@Id UUID id`, `UUID memberId`. 생성은 native INSERT |
| `CartItem` | `@Entity`, `extends BaseTimeEntity`. `UUID cartId`, `UUID productId`, `int quantity`, `@Version Long version`. `void changeQuantity(int quantity)` (1~99가 아니면 `IllegalArgumentException`) |
| `CartRepository` | native `int insertIfAbsent(UUID id, UUID memberId, Instant now)` (`ON CONFLICT ON CONSTRAINT uk_cart_member DO NOTHING`), `Optional<Cart> findByMemberId(UUID memberId)` |
| `CartItemRepository` | 아래 |
| `OrderErrorCode` | `CART_ITEM_NOT_FOUND`, `CART_PRODUCT_NOT_FOUND` |

`CartItemRepository`
- native `int addQuantity(UUID id, UUID cartId, UUID productId, int quantity, Instant now)`:
```sql
INSERT INTO orders.cart_item (id, cart_id, product_id, quantity, version, created_at, updated_at)
VALUES (:id, :cartId, :productId, :quantity, 0, :now, :now)
ON CONFLICT ON CONSTRAINT uk_cart_item_cart_product
DO UPDATE SET quantity = LEAST(orders.cart_item.quantity + EXCLUDED.quantity, 99),
              version = orders.cart_item.version + 1, updated_at = EXCLUDED.updated_at
```
- `Optional<CartItem> findByIdAndCartId(UUID id, UUID cartId)`, `Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId)`, `List<CartItem> findAllByCartIdOrderByCreatedAtDesc(UUID cartId)`, `List<CartItem> findAllByIdInAndCartId(Collection<UUID> ids, UUID cartId)`, `void delete(CartItem item)`
- `void deleteAll(Iterable<CartItem> items)` (2-2 체크아웃에서 사용, `JpaRepository`에 이미 있다)

**3) order.application** — `CartService`

| 메서드 | 규격 |
|---|---|
| `@Transactional UUID addItem(UUID memberId, UUID productId, int quantity)` | `productApi.getForCheckout(List.of(productId))`가 비면 `CART_PRODUCT_NOT_FOUND` → `cartRepository.insertIfAbsent` → `findByMemberId` → `addQuantity(Ids.newId(), ...)` → `findByCartIdAndProductId`로 항목 ID 반환 |
| `@Transactional CartResult changeQuantity(UUID memberId, UUID cartItemId, int quantity)` | 내 장바구니의 항목이 아니면 `CART_ITEM_NOT_FOUND` |
| `@Transactional void removeItem(UUID memberId, UUID cartItemId)` | 같음 |
| `@Transactional(readOnly = true) CartResult getCart(UUID memberId)` | 장바구니가 없으면 빈 결과. 항목들의 productId를 모아 `getForCheckout` **1번** → 항목별로 합침 |

`CartResult(List<CartLineResult> items, long totalAmount)` — totalAmount는 구매 가능한 항목만 합산
`CartLineResult(UUID cartItemId, UUID productId, UUID shopId, String name, String thumbnailKey, long unitPrice, int quantity, long lineAmount, boolean purchasable, String unavailableReason)`
- `unavailableReason`: 상품이 결과에 없으면 `"NOT_FOUND"`(shopId 등은 null, 가격 0), `onSale == false`면 `"NOT_ON_SALE"`, `inStock == false`면 `"OUT_OF_STOCK"`, 구매 가능하면 null

**4) order.web** — `CartController`

| API | 요청 | 응답 |
|---|---|---|
| `GET /api/cart` | | 200 `CartResponse` (Result와 같은 구조) |
| `POST /api/cart/items` | `AddCartItemRequest(@NotNull UUID productId, @NotNull @Min(1) @Max(99) Integer quantity)` | 201 `CartItemIdResponse(UUID cartItemId)` |
| `PATCH /api/cart/items/{id}` | `ChangeQuantityRequest(@NotNull @Min(1) @Max(99) Integer quantity)` | 200 `CartResponse` |
| `DELETE /api/cart/items/{id}` | | 204 |

- `order/package-info.java`: `@ApplicationModule(allowedDependencies = {"common", "product::api"})` (이후 단계에서 추가)

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `order/web/CartControllerTest` | [ ] 같은 상품 두 번 담기(2개, 3개) → 1행, 수량 5 / [ ] 98개 + 5개 → 99 / [ ] 상품 가격을 바꾸면(PATCH 상품) 장바구니 조회에도 바뀐 가격 / [ ] HIDDEN 상품 → `purchasable=false, unavailableReason="NOT_ON_SALE"`, 재고 0 → `"OUT_OF_STOCK"`, totalAmount에서 제외 / [ ] 다른 회원의 장바구니 항목 PATCH·DELETE → 404 `CART_ITEM_NOT_FOUND` / [ ] 없는 상품 담기 → 404 `CART_PRODUCT_NOT_FOUND` / [ ] **장바구니 상품이 2개일 때와 20개일 때 조회 SQL 수가 같다** (1-7의 Hibernate statistics 방식) |
| `order/application/CartConcurrencyTest` | [ ] 같은 상품을 동시에 10번 1개씩 담기 → 1행, 수량 10 |

**리뷰 때 물어볼 것**
- 장바구니에 가격을 저장하지 않는 이유는?
- "조회 후 없으면 INSERT, 있으면 UPDATE"로 짜면 동시에 담을 때 무슨 일이 생기나?

---

## 2-2. 체크아웃: 주문 생성과 재고 예약 (+ 멱등 API, 주문 조회) (L)

**목표**
- 장바구니(여러 가게 상품)에서 고른 항목으로 주문서를 만든다. 서버가 가격을 확정하고 재고를 예약한다.
- 주문은 항상 결제 대기(PENDING_PAYMENT)로 만들어진다. 결제(Toss 승인)는 2-4에서 붙인다.
- 같은 요청을 두 번 보내도 주문은 하나다.
- 구매자가 내 주문 목록과 상세를 본다(2-4의 "결제 확인 중" 폴링에 필요).

**왜 지금**: 1-9(재고 예약)를 주문과 처음 연결하는 지점이다. 여러 모듈(product·shop·member)을 한 유스케이스에서 쓴다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 애그리거트 | `Order`(루트)가 `ShopOrder`·`OrderLine`을 만들고 바꾼다. 바깥에서는 루트를 통해서만 수정. 애그리거트 안에서만 `@OneToMany`를 쓴다 |
| 여러 모듈을 한 트랜잭션으로 | 주문 생성 + 재고 예약을 하나의 로컬 트랜잭션으로. 하나라도 실패하면 전부 롤백. **모놀리스라서 가능한 것**이고 Stage 3에서는 Saga가 된다 (ADR-002) |
| 서버 가격 확정 | 요청에 가격을 받지 않는다. 상품 모듈에서 조회한 가격으로 계산 (POL-17, As-Is ORD-01) |
| 멱등 API (`Idempotency-Key`) | 클라이언트가 보낸 키로 처리 결과를 저장해두고, 같은 키로 다시 오면 저장된 응답을 그대로 돌려준다 |
| AOP | `@Idempotent`가 붙은 컨트롤러 메서드를 감싸는 `@Aspect`. 키 확인 → 실행 → 응답 저장을 한 곳에서 처리 |

**정책 (이 단계에서 정함)**
- 주문 유효시간 15분(POL-05). 설정 `myroutine.order.payment-ttl: 15m`
- PG 주문번호(`pgOrderId`): `"ORD-" + orderId` (Toss orderId 규칙: 6~64자, 영문·숫자·`-`·`_`)
- 한 주문의 품목은 최대 50개, 같은 상품은 한 번만.
- 주문에 쓴 장바구니 항목은 **체크아웃 트랜잭션에서 바로 지운다**. 결제에 실패하면 장바구니에 다시 담아야 하는 대가가 있지만, "결제가 끝나면 지운다"를 위한 표시 컬럼과 후처리가 필요 없다.
- 주문을 사람에게 보여줄 때는 주문 ID를 쓴다(별도 주문번호 없음).
- 이메일 미인증 회원의 주문 제한은 Part 4에서 붙인다.
- 멱등 키: 회원 단위로 구분, 24시간 보관. **2xx 응답만 저장**하고, 예외로 끝나면 키를 지워 같은 키로 재시도할 수 있게 한다. 만료된 키는 다음 요청이 지우고 새로 점유한다.

### 할 일

**1) 멱등 공통 장치** — 패키지 `common.idempotency`

의존성: AOP 스타터. 확인 필요: Boot 4에서 이름이 `spring-boot-starter-aspectj`로 바뀌었는지(start.spring.io에서 "AOP"/"AspectJ" 검색).

마이그레이션 `db/migration/common/V{...}__common_create_idempotency_key.sql`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| member_id | uuid | X | `pk_idempotency_key (member_id, idempotency_key)` |
| idempotency_key | varchar(100) | X | |
| request_hash | varchar(64) | X | SHA-256 hex |
| status | varchar(20) | X | `ck_idempotency_key_status`: `IN ('PROCESSING','COMPLETED')` |
| response_status | int | O | |
| response_body | jsonb | O | |
| expires_at | timestamptz | X | 인덱스 `idx_idempotency_key_expires_at` |
| created_at, updated_at | timestamptz | X | |

| 클래스 | 규격 |
|---|---|
| `Idempotent` | `@Target(METHOD) @Retention(RUNTIME) public @interface Idempotent {}` |
| `IdempotencyRepository` | `JdbcTemplate`으로 직접 작성(엔티티 없음, `@Repository`). 메서드 아래 |
| `IdempotencyAspect` | `@Aspect @Component`. `@Around("@annotation(com.myroutine.common.idempotency.Idempotent)")` |
| `CommonErrorCode` | `IDEMPOTENCY_KEY_REQUIRED`, `IDEMPOTENCY_IN_PROGRESS`, `IDEMPOTENCY_KEY_REUSED` 추가 |

`IdempotencyRepository` (각 메서드는 자체 트랜잭션으로 즉시 커밋된다. 컨트롤러는 트랜잭션 밖이므로)
- `boolean claim(UUID memberId, String key, String hash, Instant now, Instant expiresAt)`: `INSERT ... VALUES (..., 'PROCESSING', ...) ON CONFLICT DO NOTHING` → 1이면 true
- `Optional<StoredKey> find(UUID memberId, String key)` — `StoredKey(String hash, String status, Integer responseStatus, String responseBody, Instant expiresAt, Instant updatedAt)`
- `void complete(UUID memberId, String key, int status, String bodyJson, Instant now)`
- `void delete(UUID memberId, String key)`

`IdempotencyAspect.around(ProceedingJoinPoint pjp)` 순서
1. `HttpServletRequest`는 `RequestContextHolder`에서, memberId는 `SecurityContextHolder`의 `AuthClaims`에서 얻는다
2. 헤더 `Idempotency-Key`가 없거나 1~100자가 아니면 `BusinessException(IDEMPOTENCY_KEY_REQUIRED)`
3. `hash` = SHA-256(`HTTP 메서드 + " " + 요청 URI + "\n" + 메서드 인자 중 @RequestBody가 붙은 인자의 JSON`). JSON은 주입받은 `JsonMapper`로 만든다
4. `claim` 성공 → 5로 / 실패 → `find`:
   - 해시가 다르면 `IDEMPOTENCY_KEY_REUSED`
   - COMPLETED이고 만료 전 → 저장된 응답 반환: `ResponseEntity.status(responseStatus).body(jsonMapper.readTree(responseBody))`
   - 만료됐으면 `delete` 후 `claim`을 한 번 더 → 성공하면 5로
   - 그 외(PROCESSING) → `IDEMPOTENCY_IN_PROGRESS`
5. `result = pjp.proceed()` — 예외가 나면 `delete` 후 다시 던진다
6. `result`는 `ResponseEntity<?>`여야 한다(아니면 `IllegalStateException`). 2xx면 `complete(..., status, jsonMapper.writeValueAsString(body))`, 아니면 `delete`
7. `result` 반환
- `@Idempotent` 메서드에는 반드시 `@CurrentMember`가 있어 인증된 요청이다.
- **이 단계에서 붙일 곳**: `POST /api/shops`(1-6), `POST /api/shops/{shopId}/products`(1-7), `POST .../stock-adjustments`(1-8), `POST /api/orders/checkout`

**2) member.api — 배송지 조회**
```java
public interface MemberApi {
    ShippingAddressInfo getAddress(UUID memberId, UUID addressId);   // 없거나 남의 것이면 MEMBER_ADDRESS_NOT_FOUND
}
public record ShippingAddressInfo(String recipient, String phone, String zipcode, String address1, String address2) {}
```
- `MemberApiImpl` (member.application, package-private, `@Transactional(readOnly = true)`), `member/api/package-info.java`에 `@NamedInterface("api")`
- `order/package-info.java`에 `"shop::api", "member::api"` 추가

**3) 마이그레이션** — `db/migration/order/V{...}__order_create_orders.sql`

`orders.orders`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_orders` |
| member_id | uuid | X | |
| type | varchar(20) | X | DEFAULT 'NORMAL', `ck_orders_type`: `IN ('NORMAL','SUBSCRIPTION')` |
| status | varchar(30) | X | `ck_orders_status`: `IN ('PENDING_PAYMENT','PAYMENT_IN_PROGRESS','PAID','PAYMENT_FAILED','EXPIRED')` |
| total_amount | bigint | X | `ck_orders_total`: `> 0` — 결제 금액 그 자체 (INV-01) |
| shipping_address | jsonb | X | 배송지 스냅샷 |
| expires_at | timestamptz | X | |
| paid_at | timestamptz | O | |
| failure_reason | varchar(200) | O | 결제 실패·취소 사유 (2-4) |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- 인덱스 `idx_orders_member_id_created_at (member_id, created_at DESC, id DESC)`

`orders.shop_order`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_shop_order` |
| order_id | uuid | X | `fk_shop_order_orders` |
| shop_id | uuid | X | `uk_shop_order_order_shop (order_id, shop_id)` |
| shop_name | varchar(50) | X | 스냅샷 |
| status | varchar(20) | X | `ck_shop_order_status`: `IN ('PENDING','PAID','SHIPPED','DELIVERED','COMPLETED','CANCELLED')` |
| carrier | varchar(30) | O | |
| tracking_number | varchar(50) | O | |
| shipped_at, delivered_at, completed_at | timestamptz | O | |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

`orders.order_line`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_order_line` |
| shop_order_id | uuid | X | `fk_order_line_shop_order` |
| product_id | uuid | X | |
| product_name | varchar(100) | X | 스냅샷 |
| thumbnail_key | varchar(255) | O | 스냅샷 |
| unit_price | bigint | X | `> 0` |
| quantity | int | X | `> 0` |
| line_amount | bigint | X | `ck_order_line_amount`: `line_amount = unit_price * quantity` |
| refunded_amount | bigint | X | DEFAULT 0, `ck_order_line_refunded`: `refunded_amount >= 0 AND refunded_amount <= line_amount` (INV-06) |
| status | varchar(20) | X | `ck_order_line_status`: `IN ('PENDING','PAID','SHIPPED','DELIVERED','CONFIRMED','CANCELLED','RETURN_REQUESTED','RETURNED')` |
| confirmed_at | timestamptz | O | |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- 가게주문 상태에 `PENDING`(결제 전)이 있다: 체크아웃 때 가게주문이 함께 만들어지기 때문이다. `PENDING → PAID`(결제 완료), `PENDING → CANCELLED`(만료·결제 실패).

**4) order.domain — Order 애그리거트 (이 단계의 핵심, 단위 테스트를 가장 많이)**

| 클래스 | 규격 |
|---|---|
| `OrderStatus` | 전이: `PENDING_PAYMENT → {PAYMENT_IN_PROGRESS, EXPIRED, PAYMENT_FAILED}`, `PAYMENT_IN_PROGRESS → {PAID, PAYMENT_FAILED}` (결제는 항상 승인 시작을 거친다) |
| `ShopOrderStatus` | `PENDING → {PAID, CANCELLED}`, `PAID → {SHIPPED, CANCELLED}`, `SHIPPED → {DELIVERED}`, `DELIVERED → {COMPLETED}` |
| `OrderLineStatus` | `PENDING → {PAID, CANCELLED}`, `PAID → {SHIPPED, CANCELLED}`, `SHIPPED → {DELIVERED}`, `DELIVERED → {CONFIRMED, RETURN_REQUESTED}`, `RETURN_REQUESTED → {DELIVERED, RETURNED}` |
| `ShippingAddress` | `public record ShippingAddress(String recipient, String phone, String zipcode, String address1, String address2)` — `Order`에 `@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")`로 매핑 |
| `CheckoutLine` | `public record CheckoutLine(UUID productId, UUID shopId, String shopName, String productName, String thumbnailKey, Money unitPrice, int quantity)` |
| `Order` | `@Entity @Table(name = "orders", schema = "orders")`, `extends BaseTimeEntity`. 필드는 컬럼과 1:1(`Money totalAmount`). `@OneToMany(cascade = CascadeType.ALL) @JoinColumn(name = "order_id", nullable = false, updatable = false) @OrderBy("createdAt") private List<ShopOrder> shopOrders = new ArrayList<>();` |
| `ShopOrder` | `@Entity`, `extends BaseTimeEntity`. 같은 방식으로 `@OneToMany(cascade = ALL) @JoinColumn(name = "shop_order_id", nullable = false, updatable = false) List<OrderLine> lines`. 조회용으로 `@Column(name = "order_id", insertable = false, updatable = false) private UUID orderId;` |
| `OrderLine` | `@Entity`, `extends BaseTimeEntity`. 조회용 `@Column(name = "shop_order_id", insertable = false, updatable = false) private UUID shopOrderId;` |
| `OrderRepository` | 아래 |
| `OrderErrorCode` | `ORDER_NOT_FOUND`, `OWN_SHOP_PRODUCT` 추가 |

`Order` 메서드
- `static Order checkout(UUID memberId, List<CheckoutLine> lines, ShippingAddress address, Instant now, Duration paymentTtl)`
  - lines가 비었으면 `IllegalArgumentException`
  - **가게별로 묶는다**: `shopId` 기준, 처음 나온 순서를 유지(`LinkedHashMap`). 가게마다 `ShopOrder.create(shopId, shopName, lines)` (PENDING), 품목은 `OrderLine.create(...)` (PENDING, `lineAmount = unitPrice.times(quantity)`)
  - `totalAmount = Σ lineAmount`, status PENDING_PAYMENT, `expiresAt = now + paymentTtl`
- `String pgOrderId()` → `"ORD-" + id`
- `void markPaid(Instant now)`: `status.transitTo(PAID)`, `paidAt = now`, 모든 가게주문 `PENDING → PAID`, 모든 품목 `PENDING → PAID`
- `void markPaymentFailed(String reason)`: `→ PAYMENT_FAILED`, `failureReason = reason`(200자로 자름), `cancelPendingChildren()`
- `void cancelPendingChildren()`: 가게주문 `PENDING → CANCELLED`, 품목 `PENDING → CANCELLED` (2-5 만료에서도 사용)
- `List<UUID> productIds()`, `Map<UUID, Integer> quantitiesByProduct()` — 재고 예약용

`OrderRepository`
- `Order save(Order o)`, `Optional<Order> findById(UUID id)`, `Optional<Order> findByIdAndMemberId(UUID id, UUID memberId)`
- 목록: `findByMemberFirstPage(UUID memberId, Limit limit)`, `findByMemberNextPage(UUID memberId, Instant cursorCreatedAt, UUID cursorId, Limit limit)` (keyset)
- 확인 필요: `@OneToMany` 컬렉션을 읽을 때 N+1이 없도록 상세 조회에 `@EntityGraph(attributePaths = {"shopOrders", "shopOrders.lines"})`를 쓴다. 목록 조회는 주문 행만 읽고 가게주문은 `IN` 쿼리로 한 번에 읽는다(`spring.jpa.properties.hibernate.default_batch_fetch_size: 100`을 `application.yaml`에 추가)

**5) order.application — 체크아웃**

| 클래스 | 규격 |
|---|---|
| `OrderProperties` | `@ConfigurationProperties("myroutine.order") @Validated record OrderProperties(@NotNull Duration paymentTtl)` |
| `CheckoutCommand` | `record (List<UUID> cartItemIds, UUID addressId)` |
| `CheckoutResult` | `record (UUID orderId, OrderStatus status, long totalAmount, String pgOrderId, Instant expiresAt, List<ShopOrderResult> shopOrders)` |
| `CheckoutService` | `@Transactional public CheckoutResult checkout(UUID memberId, CheckoutCommand command)` |

`checkout` 순서 (전부 한 트랜잭션, 하나라도 예외면 전부 롤백)
1. `cartItems = findAllByIdInAndCartId(cartItemIds, 내 cartId)` — 개수가 다르면 `CART_ITEM_NOT_FOUND`. 항목들을 `(productId, quantity)`로 바꾼다(장바구니는 상품당 한 행이라 중복이 없다)
2. 50개를 넘으면 `INVALID_REQUEST`
3. `products = productApi.getPurchasable(productIds)` — 판매 중이 아니면 여기서 422 `PRODUCT_NOT_ON_SALE`
4. `shops = shopApi.requireActiveShops(가게 ID들)` — 운영 중이 아니면 422 `SHOP_NOT_ACTIVE`
5. 가게 소유자가 나(memberId)인 가게가 있으면 `OWN_SHOP_PRODUCT` (details.productIds)
6. `address = memberApi.getAddress(memberId, addressId)`
7. `Order.checkout(...)` → `orderRepository.save(order)`
8. `productApi.reserve(order.getId(), items, order.getExpiresAt())` — 재고 부족이면 422 `OUT_OF_STOCK`
9. `cartItemRepository.deleteAll(cartItems)`
10. `CheckoutResult` 반환 (status PENDING_PAYMENT)
- 8에서 예외가 나면 7의 주문까지 롤백되고 장바구니도 그대로다. 여러 상품 중 일부만 예약된 상태도 남지 않는다. 이게 "한 트랜잭션"의 의미다.

`OrderQueryService` (`@Transactional(readOnly = true)`)
- `CursorPage<OrderSummaryResult> getMyOrders(UUID memberId, String cursor, int size)` — `OrderSummaryResult(UUID orderId, OrderStatus status, long totalAmount, String firstProductName, int lineCount, Instant createdAt)`
- `OrderDetailResult getMyOrder(UUID memberId, UUID orderId)` — 없거나 남의 것이면 `ORDER_NOT_FOUND`. 주문 필드 전체 + `shopOrders[{shopOrderId, shopId, shopName, status, carrier, trackingNumber, lines[{orderLineId, productId, productName, unitPrice, quantity, lineAmount, refundedAmount, status}]}]` (2-8에서 refunds 추가)

**6) order.web** — `OrderController` (`/api/orders`)

| API | 요청 | 응답 |
|---|---|---|
| `POST /api/orders/checkout` `@Idempotent` | `CheckoutRequest` | 201 `CheckoutResponse` (Result와 같은 구조, [API 명세 §3.1](../02-design/07-api-spec.md)) |
| `GET /api/orders?cursor=&size=` | | 200 `CursorPage<OrderSummaryResponse>` |
| `GET /api/orders/{id}` | | 200 `OrderDetailResponse` |

`CheckoutRequest`: `@NotEmpty @Size(max = 50) List<UUID> cartItemIds`, `@NotNull UUID addressId`. 요청에 `price` 같은 필드가 있어도 **record에 없으므로 무시된다**(Jackson 3는 기본적으로 모르는 필드를 무시한다. 확인 필요: 무시되지 않고 400이 나면 `@JsonIgnoreProperties(ignoreUnknown = true)`를 붙인다)

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `order/domain/OrderTest` | [ ] 가게 2곳·품목 3개 → 가게주문 2개, 가게별 품목 수 맞음, `total = Σ(단가 × 수량)` / [ ] 같은 가게 상품은 한 가게주문으로 / [ ] `expiresAt = now + 15분` / [ ] `markPaid` → 가게주문·품목 모두 PAID / [ ] `markPaymentFailed` → 가게주문·품목 CANCELLED, 사유 저장 |
| `order/domain/OrderStatusTest`, `ShopOrderStatusTest`, `OrderLineStatusTest` | [ ] 각각 전체 조합 파라미터화 |
| `common/idempotency/IdempotencyAspectTest` (API 통합, 1-6 가게 개설 API로 검증) | [ ] 키 없이 → 400 `IDEMPOTENCY_KEY_REQUIRED` / [ ] 같은 키·같은 바디 두 번 → 가게 1개, 두 응답 바디가 같다 / [ ] 같은 키·다른 바디 → 422 `IDEMPOTENCY_KEY_REUSED` / [ ] 사업자번호 중복으로 409를 받은 요청의 키는 지워진다: 같은 키·같은 바디로 다시 보내면 저장된 응답이 아니라 다시 처리되어 또 409 / [ ] 만료된 키(JDBC로 `expires_at`을 과거로)와 같은 키로 요청 → 새로 처리됨 |
| `order/web/OrderControllerTest` (checkout) | [ ] 체크아웃 → 201 PENDING_PAYMENT, `stock.reserved` 증가, `pgOrderId = "ORD-" + orderId`, 주문한 장바구니 항목 삭제 / [ ] 요청 JSON에 `"price": 1`을 넣어도 서버 가격으로 계산 / [ ] **상품 2개 중 두 번째가 재고 부족 → 422 `OUT_OF_STOCK`, 주문 0건, 첫 번째 상품 reserved 0(예약도 롤백), 장바구니 그대로** / [ ] 자기 가게 상품 → 422 `OWN_SHOP_PRODUCT` / [ ] CLOSED 가게 상품 → 422 `SHOP_NOT_ACTIVE` / [ ] 남의 배송지 → 404 / [ ] 남의 장바구니 항목 ID → 404 `CART_ITEM_NOT_FOUND` / [ ] 같은 `Idempotency-Key`로 두 번 → 주문 1건, 같은 응답 |
| `order/application/CheckoutIdempotencyConcurrencyTest` | [ ] **같은 키로 동시에 10번**(MockMvc) → 주문 1건, 상태 코드는 201과 409(`IDEMPOTENCY_IN_PROGRESS`)뿐 |
| `order/web/OrderQueryTest` | [ ] 내 주문 목록 커서 페이징 / [ ] 상세에 가게주문·품목 / [ ] 남의 주문 상세 → 404 |
| `ModularityTest` | [ ] 통과 (order → product·shop·member의 api만 참조) |

**제안 (선택)**
- 바로 구매(장바구니를 거치지 않고 상품·수량으로 체크아웃): 요청에 `items`를 받는 경로 추가
- 사람이 읽기 쉬운 주문번호(`yyyyMMdd-000123`, DB 시퀀스 + 업무 날짜)
- 멱등 키가 PROCESSING으로 오래 남은 경우(처리하던 서버가 죽음) 일정 시간 뒤 다시 점유하기, 만료된 키를 지우는 정리 잡

**리뷰 때 물어볼 것**
- 주문 생성과 재고 예약을 한 트랜잭션에 묶을 수 있는 이유는? MSA에서는 어떻게 되나?
- Order 애그리거트가 ShopOrder·OrderLine을 직접 만들게 한 이유는?
- 멱등 키가 "처리 중"인 상태로 서버가 죽으면?
- 멱등 키를 응답 저장 없이 "있으면 409"로만 쓰면 무엇이 부족한가?

---

## 2-3. Toss 클라이언트와 가짜 PG 서버 (M)

**목표**
- Toss 결제 승인·취소·조회 API를 호출하는 클라이언트를 만든다.
- 응답을 **승인 / 명확한 실패 / 불확실** 세 가지로 분류한다.
- 테스트에서 Toss 대신 쓸 가짜 PG 서버를 만든다.

**왜 지금**: 2-4 결제 승인 전에 "외부 API는 실패가 두 종류가 아니라 세 종류"라는 것을 코드로 먼저 정리한다.

**새로 등장**

| 개념·도구 | 한 줄 설명 |
|---|---|
| Toss 결제 흐름 | 결제창은 **인증**만 한다. 실제 **승인**은 우리 서버가 confirm API를 호출할 때 일어난다 (ADR-004) |
| 결과 3분류 | 2xx → 승인 / 거절 코드(4xx) → 명확한 실패 / 타임아웃·5xx·연결 끊김 → **불확실(UNKNOWN)** |
| `RestClient` 타임아웃 | 기본값은 무한 대기. connect·read 타임아웃을 반드시 설정 |
| `RestClient.exchange` | 4xx·5xx에서 예외를 던지는 `retrieve()` 대신, 상태 코드와 본문을 직접 읽어 분류한다 |
| 가짜 PG 서버 | JDK 내장 `HttpServer`로 만든 테스트용 서버. 경로별로 응답·지연·연결 끊기를 설정 |

**Toss API 요약** (확인 필요: 구현 전에 Toss 개발자센터 문서와 한 번 대조한다)

| 작업 | 요청 | 성공 응답 |
|---|---|---|
| 승인 | `POST /v1/payments/confirm` 바디 `{paymentKey, orderId, amount}` | 200, Payment 객체 `status: "DONE"` |
| 취소 | `POST /v1/payments/{paymentKey}/cancel` 바디 `{cancelReason, cancelAmount}` | 200, Payment 객체 `status: "CANCELED"` 또는 `"PARTIAL_CANCELED"` |
| 조회 | `GET /v1/payments/orders/{orderId}` | 200 Payment 객체 / 없으면 404 `NOT_FOUND_PAYMENT` |
| 공통 헤더 | `Authorization: Basic base64(시크릿키 + ":")`, 승인·취소에 `Idempotency-Key` | 에러 본문 `{code, message}` |

### 할 일

**1) 키와 설정**
- Toss 개발자센터에서 테스트 키 발급 → `.env`에 `TOSS_SECRET_KEY`, `TOSS_CLIENT_KEY` (커밋 금지), `.env.example`에 이름만
- `payment.infrastructure.TossProperties`: `@ConfigurationProperties("myroutine.toss") @Validated record TossProperties(@NotBlank String baseUrl, @NotBlank String secretKey, String clientKey, @NotNull Duration connectTimeout, @NotNull Duration readTimeout)`
- `application.yaml`: `base-url: https://api.tosspayments.com`, `secret-key: ${TOSS_SECRET_KEY}`, `client-key: ${TOSS_CLIENT_KEY:}`, `connect-timeout: 3s`, `read-timeout: 10s`
- `application-test.yaml`: `secret-key: test_sk_dummy`, `read-timeout: 500ms` (`base-url`은 가짜 서버 주소를 테스트 베이스에서 주입)

**3) payment.domain — 결과 타입**
```java
public sealed interface PgResult permits PgResult.Approved, PgResult.Rejected, PgResult.Unknown {
    record Approved(String paymentKey, long totalAmount, long balanceAmount, String method, Instant approvedAt) implements PgResult {}
    record Rejected(String code, String message) implements PgResult {}
    record Unknown(String reason) implements PgResult {}
}
```

**4) payment.infrastructure — 클라이언트**

| 클래스 | 규격 |
|---|---|
| `TossClientConfig` | `@Configuration @EnableConfigurationProperties(TossProperties.class)`. `@Bean RestClient tossRestClient(TossProperties p)`: `JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(p.connectTimeout()).build())` + `factory.setReadTimeout(p.readTimeout())`, `baseUrl`, 기본 헤더 `Authorization: Basic ...` |
| `TossPaymentResponse` | `record (String paymentKey, String orderId, String status, long totalAmount, long balanceAmount, String method, OffsetDateTime approvedAt)` + `@JsonIgnoreProperties(ignoreUnknown = true)` |
| `TossErrorResponse` | `record (String code, String message)` + 같은 어노테이션 |
| `TossClient` | `@Component`. 메서드 아래 |

`TossClient` 메서드
- `PgResult confirm(UUID paymentId, String paymentKey, String pgOrderId, Money amount)` — `Idempotency-Key: paymentId.toString()`
- `PgResult cancel(UUID paymentId, String paymentKey, Money amount, String reason, String idempotencyKey)`
- `PgResult queryByOrderId(UUID paymentId, String pgOrderId)`
- 공통 흐름: 시작 시각 기록 → `tossRestClient.method(...).exchange((req, res) -> 분류(res))` → `ResourceAccessException`(타임아웃·연결 실패·끊김)은 `Unknown(예외 이름)` → `finally`에서 `log.info("pg {} orderId={} result={} status={} latencyMs={}", ...)` — **paymentKey 전체·카드 정보는 남기지 않는다**

분류표

| 응답 | 승인·취소 | 조회 |
|---|---|---|
| 2xx + 본문 파싱 성공 | `Approved` (승인은 `status == "DONE"`이 아니면 `Unknown`) | `status == "DONE"`·`"PARTIAL_CANCELED"`·`"CANCELED"` → `Approved` / `"ABORTED"`·`"EXPIRED"` → `Rejected(status)` / 그 외(`READY`, `IN_PROGRESS`) → `Unknown` |
| 2xx + 본문 파싱 실패 | `Unknown("INVALID_BODY")` | 같음 |
| 4xx + 코드가 "불확실 코드" 목록에 있음 | `Unknown(code)` | 같음 |
| 404 `NOT_FOUND_PAYMENT` (조회) | — | `Rejected("NOT_FOUND_PAYMENT")` (승인된 적 없음) |
| 그 외 4xx | `Rejected(code, message)` | `Rejected(code, message)` |
| 5xx | `Unknown(code 또는 "HTTP_5xx")` | 같음 |
| 타임아웃·연결 끊김 | `Unknown(예외 이름)` | 같음 |

- "불확실 코드" 초기 목록(상수 `Set<String> UNCERTAIN_CODES`): `ALREADY_PROCESSED_PAYMENT`, `PROVIDER_ERROR`, `FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING`, `FAILED_INTERNAL_SYSTEM_PROCESSING`, `UNKNOWN_PAYMENT_ERROR`. **확인 필요**: Toss 에러 코드 문서를 보고 "재시도·조회로 확인해야 하는" 4xx 코드를 이 목록에 반영하고, 결과를 PR에 표로 남긴다(Claude가 문서에 옮긴다).

**5) 가짜 PG 서버** — `src/test/java/com/myroutine/support/`

| 클래스 | 규격 |
|---|---|
| `RecordedRequest` | `record (String method, String path, Map<String, String> headers, String body)` — 헤더 이름은 소문자로 저장 |
| `Behavior` | `record (int status, String body, long delayMillis, boolean drop, boolean approve)`. 팩토리: `approve()` (기본 동작: 승인 처리 후 성공 JSON), `reject(String code)` (400 + `{"code":..., "message":"거절"}`), `serverError()` (500), `delay(long ms)` (**승인은 처리한 뒤** ms만큼 늦게 응답 — "PG는 승인했는데 우리는 타임아웃" 상황), `drop()` (응답 없이 연결 종료) |
| `FakePgServer` | `static FakePgServer start()`: `HttpServer.create(new InetSocketAddress(0), 0)`, 가상 스레드 executor. 아래 상태와 메서드 |

`FakePgServer` 상태와 메서드
- 상태: `Map<String, Behavior> behaviors`(경로 접두어 → 동작), `List<RecordedRequest> requests`, `Map<String, ApprovedPayment> approved`(pgOrderId → paymentKey·금액·승인 시각·취소 누계)
- `String baseUrl()`, `void on(String pathPrefix, Behavior behavior)`, `List<RecordedRequest> requests(String pathPrefix)`, `void reset()`(셋 다 비움)
- `void markApproved(String pgOrderId, String paymentKey, long amount, Instant approvedAt)`: 테스트에서 "PG에는 이미 승인된 상태"를 만든다
- 기본 동작(설정된 Behavior가 없거나 `approve`일 때)
  - 승인 `/v1/payments/confirm`: 요청 바디의 paymentKey·orderId·amount로 `approved`에 기록 → 200 `{"paymentKey":..,"orderId":..,"status":"DONE","totalAmount":amount,"balanceAmount":amount,"method":"카드","approvedAt":"..."}`
  - 취소 `/v1/payments/{key}/cancel`: 해당 결제의 취소 누계에 더함 → 200 `status`는 누계 = 총액이면 `CANCELED`, 아니면 `PARTIAL_CANCELED`
  - 조회 `/v1/payments/orders/{orderId}`: `approved`에 있으면 200 DONE(취소 반영), 없으면 404 `{"code":"NOT_FOUND_PAYMENT"}`
- 같은 `Idempotency-Key`로 다시 온 승인·취소는 처음 응답을 그대로 돌려준다(Toss의 멱등 동작 흉내)

`IntegrationTestSupport`에 추가
```java
protected static final FakePgServer pg = FakePgServer.start();

@DynamicPropertySource
static void pgProperties(DynamicPropertyRegistry registry) {
    registry.add("myroutine.toss.base-url", pg::baseUrl);
}

@AfterEach
void resetPg() { pg.reset(); }
```

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `payment/infrastructure/TossClientTest` (`extends IntegrationTestSupport`, `TossClient` 주입) | [ ] 승인 → `Approved`, 금액·paymentKey 일치 / [ ] `reject("REJECT_CARD_COMPANY")` → `Rejected("REJECT_CARD_COMPANY")` / [ ] `serverError()` → `Unknown` / [ ] `delay(1000)`(read-timeout 500ms 초과) → `Unknown`, **그런데 가짜 서버에는 승인 기록이 있다** / [ ] `drop()` → `Unknown` / [ ] `reject("ALREADY_PROCESSED_PAYMENT")` → `Unknown` / [ ] 조회: 승인된 주문 → `Approved`, 없는 주문 → `Rejected("NOT_FOUND_PAYMENT")` / [ ] 요청 헤더에 `idempotency-key`(= paymentId), `authorization`(Basic) |

**제안 (선택)**
- PG 호출 감사 테이블(`payment.pg_call_log`: 작업·HTTP 상태·결과·지연시간·요약) — 지금은 로그 한 줄로 대신한다. Part 7 ELK가 붙으면 로그로 같은 것을 검색할 수 있다
- 외부 호출 메서드 시작 시 `TransactionSynchronizationManager.isActualTransactionActive()`가 true면 예외 — 트랜잭션 안에서 실수로 부르는 것을 막는 안전장치

**리뷰 때 물어볼 것**
- 타임아웃이 났는데 실패로 처리하면 어떤 사고가 나나? (As-Is PAY-01)
- Mockito로 클라이언트를 mock하지 않고 가짜 서버를 쓴 이유는?
- 가짜 서버의 `delay`가 "승인은 하고 늦게 응답"하게 만든 이유는?

---

## 2-4. PG 결제 승인 (L)

**목표**
- 결제 대기 주문을 Toss로 승인한다. 승인되면 PAID(200), 거절되면 결제 실패(402, 재고 원복), 결과가 불확실하면 "결제 확인 중"(202).
- 승인 요청과 주문 만료가 겹쳐도 "만료된 주문에 승인된 결제"가 생기지 않는다.

**왜 지금**: 2-2(주문)와 2-3(Toss 클라이언트)를 연결한다. **이 프로젝트에서 가장 중요한 단계다.**

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 트랜잭션 밖 외부 호출 | DB 트랜잭션을 [상태 선점 → 커밋] → **외부 호출** → [결과 반영 → 커밋]으로 나눈다 (공통 규칙 K) |
| CAS 상태 전이 | `UPDATE ... SET status = 'PAYMENT_IN_PROGRESS' WHERE id = ? AND status = 'PENDING_PAYMENT' AND expires_at > now`. 반영 0건이면 이미 만료·결제된 주문 → PG를 부르지 않는다 |
| 먼저 커밋 | PG를 부르기 **전에** payment(IN_PROGRESS)를 커밋해두면, 호출 중 앱이 죽어도 "확인해야 할 결제"가 남는다 (ADR-004) |
| 결과 반영의 단일 승자 | 결제 결과는 `status IN ('IN_PROGRESS','UNKNOWN')`일 때만 CAS로 반영. 요청 스레드와 대사 잡이 동시에 반영하려 해도 한쪽만 바뀐다 |

### 할 일

**1) 마이그레이션** — `db/migration/payment/V{...}__payment_create_payment.sql`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_payment` |
| order_id | uuid | X | `uk_payment_order` — 주문 1건당 결제 1건 (INV-02) |
| member_id | uuid | X | |
| amount | bigint | X | `> 0` |
| cancelled_amount | bigint | X | DEFAULT 0, `ck_payment_cancelled`: `cancelled_amount >= 0 AND cancelled_amount <= amount` (INV-06) |
| pg_order_id | varchar(64) | X | `uk_payment_pg_order_id` |
| payment_key | varchar(200) | X | `uk_payment_payment_key` |
| billing_key_id | uuid | O | Part 5 |
| method | varchar(30) | O | |
| status | varchar(30) | X | `ck_payment_status`: `IN ('IN_PROGRESS','APPROVED','FAILED','UNKNOWN','PARTIAL_CANCELLED','CANCELLED')` |
| failure_code | varchar(100) | O | 거절 코드 (실패 이력, As-Is ORD-03) |
| approved_at | timestamptz | O | |
| reconcile_attempts | int | X | DEFAULT 0 |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

**2) payment.domain**

| 클래스 | 규격 |
|---|---|
| `PaymentStatus` | 전이: `IN_PROGRESS → {APPROVED, FAILED, UNKNOWN}`, `UNKNOWN → {APPROVED, FAILED}`, `APPROVED → {PARTIAL_CANCELLED, CANCELLED}`, `PARTIAL_CANCELLED → {PARTIAL_CANCELLED, CANCELLED}` |
| `Payment` | `@Entity @Table(name = "payment", schema = "payment")`, `extends BaseTimeEntity`. 필드는 컬럼과 1:1. `static Payment begin(UUID orderId, UUID memberId, Money amount, String pgOrderId, String paymentKey)` → IN_PROGRESS |
| `PaymentRepository` | `save`, `findById`, `Optional<Payment> findByOrderId(UUID orderId)` + CAS 아래 |

`PaymentRepository` CAS (모두 `@Modifying(clearAutomatically = true)`, `version + 1`, `updatedAt = :now` 포함, 반환 `int`)
- `markApproved(UUID id, Collection<PaymentStatus> from, String method, Instant approvedAt, Instant now)` → status APPROVED
- `markFailed(UUID id, Collection<PaymentStatus> from, String failureCode, Instant now)` → FAILED
- `markUnknown(UUID id, Instant now)` → `where status = IN_PROGRESS` → UNKNOWN

**3) payment.api**
```java
public interface PaymentApi {
    UUID begin(StartPayment command);                       // @Transactional. IN_PROGRESS 저장. 같은 orderId면 unique 위반
    PgOutcome callConfirm(UUID paymentId);                  // 트랜잭션 밖 전용. Toss 승인 호출만 하고 payment 상태는 바꾸지 않는다
    PaymentState applyOutcome(UUID paymentId, PgOutcome outcome);   // @Transactional. IN_PROGRESS·UNKNOWN일 때만 반영. 반환: 반영 후 현재 상태
}
public record StartPayment(UUID orderId, UUID memberId, Money amount, String pgOrderId, String paymentKey) {}
public enum PaymentState { IN_PROGRESS, APPROVED, FAILED, UNKNOWN, PARTIAL_CANCELLED, CANCELLED }
public record PgOutcome(PgOutcomeType type, String code, String method, Instant approvedAt) {}   // 승인이 아니면 method·approvedAt은 null
public enum PgOutcomeType { APPROVED, REJECTED, UNKNOWN }
```
- `PaymentApiImpl.callConfirm`: payment를 읽어(짧은 읽기 트랜잭션) `tossClient.confirm(...)` → `PgResult`를 `PgOutcome`으로 변환
- `applyOutcome`: APPROVED → `markApproved(id, {IN_PROGRESS, UNKNOWN}, ...)`, REJECTED → `markFailed(id, {IN_PROGRESS, UNKNOWN}, code)`, UNKNOWN → `markUnknown(id, now)` → 다시 읽어 현재 상태 반환. 반영 행 수가 0이어도 예외 없이 현재 상태를 반환한다(다른 흐름이 먼저 반영함)
- `payment/package-info.java`: `allowedDependencies = {"common"}`, `order/package-info.java`에 `"payment::api"` 추가

**4) order — 결제 승인**

`OrderRepository` 추가
- `@Modifying(clearAutomatically = true) @Query("update Order o set o.status = :to, o.version = o.version + 1, o.updatedAt = :now where o.id = :id and o.status = :from and o.expiresAt > :now") int startPayment(UUID id, OrderStatus from, OrderStatus to, Instant now)` — from = PENDING_PAYMENT, to = PAYMENT_IN_PROGRESS

`OrderErrorCode` 추가: `ORDER_EXPIRED`, `ORDER_ALREADY_PAID`. `CommonErrorCode` 추가: `PAYMENT_FAILED`, `PG_ERROR`, `AMOUNT_MISMATCH`

`OrderPaymentApplier` (`@Service`, 2-6에서 재사용)

| 메서드 | 규격 |
|---|---|
| `@Transactional public OrderStatus apply(UUID orderId, UUID paymentId, PgOutcome outcome)` | ① `state = paymentApi.applyOutcome(paymentId, outcome)` ② `applyPaymentState(orderId, state, outcome.code())` 반환 |
| `@Transactional public OrderStatus applyPaymentState(UUID orderId, PaymentState state, String failureCode)` | `order = findById` → status가 PAYMENT_IN_PROGRESS가 아니면 그대로 반환(이미 처리됨) → APPROVED면 `complete(order)`, FAILED면 `fail(order, "PG 거절: " + failureCode)`, 그 외는 아무것도 안 함 → `order.getStatus()` |
| `@Transactional public void failOrder(UUID orderId, String reason)` | `order = findById` → `fail(order, reason)` (2-6 보상에서 사용) |
| private `complete(Order order)` | `productApi.commitReservation(id)` → `order.markPaid(now)` |
| private `fail(Order order, String reason)` | `productApi.releaseReservation(id, PAYMENT_FAILED)` → `order.markPaymentFailed(reason)` |

`ConfirmPaymentService` (**클래스에 `@Transactional` 없음**, 의존성 `TransactionTemplate`, `OrderRepository`, `PaymentApi`, `OrderPaymentApplier`, `Clock`)
- `public ConfirmResult confirm(UUID memberId, UUID orderId, ConfirmPaymentCommand command)` — `ConfirmPaymentCommand(String paymentKey, String pgOrderId, long amount)`, `ConfirmResult(UUID orderId, OrderStatus status)`

`confirm` 순서
1. **트랜잭션 1** (`tx.execute`)
   1. `order = findByIdAndMemberId` 없으면 `ORDER_NOT_FOUND`
   2. 상태가 PENDING_PAYMENT가 아니면 `toResultOrThrow(order)`(아래 표)
   3. `command.pgOrderId() != order.pgOrderId()`이거나 `command.amount() != totalAmount`면 `AMOUNT_MISMATCH` (PG 호출 없음)
   4. `startPayment(orderId, PENDING_PAYMENT, PAYMENT_IN_PROGRESS, now)` → 0이면 주문을 다시 읽어 `toResultOrThrow`
   5. `paymentId = paymentApi.begin(new StartPayment(orderId, memberId, totalAmount, pgOrderId, paymentKey))`
   6. 커밋 — 이 시점부터 주문은 만료되지 않고, 결제 행은 대사 대상이 된다
2. **트랜잭션 밖**: `outcome = paymentApi.callConfirm(paymentId)`
3. **트랜잭션 2**: `status = applier.apply(orderId, paymentId, outcome)`
4. 반환: PAID → `ConfirmResult(PAID)` / PAYMENT_IN_PROGRESS → `ConfirmResult(PAYMENT_IN_PROGRESS)` / PAYMENT_FAILED → `throw new BusinessException(PAYMENT_FAILED, Map.of("pgCode", outcome.code()))` (트랜잭션 2는 이미 커밋됨)

`toResultOrThrow(order)`

| 현재 상태 | 결과 |
|---|---|
| PAID | `ORDER_ALREADY_PAID` (409) |
| EXPIRED, 또는 PENDING_PAYMENT인데 `expiresAt <= now` | `ORDER_EXPIRED` (409) |
| PAYMENT_IN_PROGRESS | `ConfirmResult(PAYMENT_IN_PROGRESS)` → 202 |
| PAYMENT_FAILED | `INVALID_STATE_TRANSITION` (409) |

**5) order.web** — `OrderController`에 추가

| API | 요청 | 응답 |
|---|---|---|
| `POST /api/orders/{id}/payment/confirm` `@Idempotent` | `ConfirmPaymentRequest(@NotBlank String paymentKey, @NotBlank String pgOrderId, @NotNull @Min(1) Long amount)` | 200 `{orderId, status: "PAID"}` / 202 `{orderId, status: "PAYMENT_IN_PROGRESS"}` / 402·409·422 공통 에러 |

**6) (선택) 실제 Toss로 수동 확인**: Toss 결제위젯 예제 HTML을 `src/main/resources/static/toss-test.html`로 두고 로컬에서 결제창 → successUrl에서 받은 paymentKey로 confirm을 직접 호출해 본다. 클라이언트 키는 공개용이지만 파일에 넣지 말고 실행할 때 입력한다. 결과는 PR에 스크린샷으로.

### 완료 확인

| 테스트 클래스 | 케이스 (모두 가짜 PG 서버) |
|---|---|
| `payment/domain/PaymentStatusTest` | [ ] 전체 조합 파라미터화 |
| `order/web/PaymentConfirmTest` | 아래 |
| `payment/application/PaymentApiImplTest` | [ ] `applyOutcome`: IN_PROGRESS → APPROVED / 이미 APPROVED인 결제에 REJECTED 반영 → 그대로 APPROVED / [ ] 같은 (ORDER, orderId)로 `begin` 두 번 → `DataIntegrityViolationException` |

`PaymentConfirmTest` (43,000원 주문을 `TestFixtures`로 만들어 시작)
- [ ] **승인** → 200 PAID, `stock.sold` 증가·`reserved` 0, 가게주문·품목 PAID, payment APPROVED, 가짜 서버 요청의 `idempotency-key` = paymentId
- [ ] **거절**(`reject("REJECT_CARD_COMPANY")`) → 402 `PAYMENT_FAILED`(`details.pgCode`), 주문 PAYMENT_FAILED·`failure_reason`에 코드, payment FAILED·`failure_code`, 재고 available 원복, 가게주문·품목 CANCELLED (As-Is ORD-03: 실패 이력이 남음)
- [ ] **타임아웃**(`delay(1000)`) → 202, payment UNKNOWN, 주문 PAYMENT_IN_PROGRESS, 재고 아직 reserved
- [ ] **만료된 주문**(JDBC로 `expires_at`을 과거로) → 409 `ORDER_EXPIRED`, `pg.requests("/v1/payments/confirm")` 0건
- [ ] **같은 주문 confirm 동시 2건**(서로 다른 Idempotency-Key, `delay(300)`) → PG 승인 요청 1건, 응답은 200 하나 + 202 하나
- [ ] **금액 위변조**(amount + 1) → 422 `AMOUNT_MISMATCH`, PG 요청 0건, 주문 PENDING_PAYMENT 그대로
- [ ] 이미 PAID인 주문 confirm(다른 키) → 409 `ORDER_ALREADY_PAID`

**제안 (선택)**
- 결제창 실패·취소 통지 API(`POST /api/orders/{id}/payment/fail`): 사용자가 결제창을 닫으면 15분을 기다리지 않고 바로 재고를 푼다. 없으면 2-5 만료 잡이 정리한다

**리뷰 때 물어볼 것**
- 트랜잭션 1과 2 사이에서 앱이 죽으면 무엇이 남고, 누가 이어서 처리하나? (→ 2-6)
- `@Transactional` 메서드 안에서 Toss를 부르면 정확히 어떤 문제가 생기나? (As-Is ORD-02)
- 만료와 승인이 경쟁할 때 "만료된 주문에 승인된 결제"가 왜 생길 수 없나? CAS에 `expires_at > now`를 넣은 이유는?
- 2PC를 안 쓴 이유는?

---

## 2-5. 주문 만료 잡 (+ 잡 락) (M)

**목표**: 결제창에서 이탈한 주문(15분 경과)을 자동으로 만료시키고 재고를 되돌린다. 서버가 여러 대여도 잡은 한 곳에서만 돈다.

**왜 지금**: 2-2부터 결제 대기 주문이 생기는데, 아무도 정리하지 않으면 재고가 영원히 묶인다(As-Is CAT-01).

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 스케줄 작업 | `@Scheduled`로 주기 실행. **서버마다** 실행된다는 점에 주의 |
| 잡 락 (세션 advisory lock) | 전용 커넥션에서 `pg_try_advisory_lock` → 작업 → `pg_advisory_unlock`. 다른 서버는 건너뛴다. 트랜잭션 레벨 `pg_advisory_xact_lock`은 첫 커밋에 풀려서 여기선 못 쓴다 |
| 건별 트랜잭션 | 100건을 한 트랜잭션으로 묶지 않고 주문마다 트랜잭션. 한 건 실패가 나머지를 막지 않는다 |

### 할 일

**1) common.job**

| 클래스 | 규격 |
|---|---|
| `JobLock` | `@Component`, 의존성 `DataSource`. `boolean runExclusively(String jobName, Runnable task)` |
| `JobRunner` | `@Component`, 의존성 `JobLock`. `void run(String jobName, Supplier<Integer> task)` |
| `common.config.SchedulingConfig` | `@Configuration @EnableScheduling @ConditionalOnProperty(prefix = "myroutine.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)` |

`JobLock.runExclusively` 순서
1. `try (Connection c = dataSource.getConnection())` — 이 커넥션은 락 전용이다(작업은 다른 커넥션을 쓴다)
2. `SELECT pg_try_advisory_lock(hashtext(?))`, 파라미터 `"job:" + jobName` → false면 `return false`
3. `try { task.run(); } finally { SELECT pg_advisory_unlock(hashtext(?)) }` → `return true`
- **unlock을 빼먹으면** 커넥션이 풀로 돌아가도 락이 남는다(세션 레벨 락은 커넥션이 닫힐 때까지 유지). 그래서 finally에서 반드시 푼다.

`JobRunner.run` 순서
1. `MDC.put(TraceIds.MDC_KEY, TraceIds.newId())`, `MDC.put("job", jobName)`
2. 시작 시각 → `jobLock.runExclusively(jobName, () -> count = task.get())`
3. 실행했으면 `log.info("job {} processed {} in {}ms", ...)`, 락을 못 잡았으면 `log.debug("job {} skipped: locked", ...)`
4. 예외는 `log.error("job {} failed", jobName, e)`로 남기고 다시 던지지 않는다(다음 주기에 재실행)
5. `finally`에서 MDC 두 키 제거

- `application-test.yaml`: `myroutine.scheduling.enabled: false`

**2) 만료 처리**

| 대상 | 규격 |
|---|---|
| 마이그레이션 | `db/migration/order/V{...}__order_add_orders_status_expires_at_index.sql`: `CREATE INDEX idx_orders_status_expires_at ON orders.orders (status, expires_at)` |
| `OrderRepository` | `@Query("select o.id from Order o where o.status = :status and o.expiresAt < :now order by o.expiresAt") List<UUID> findExpiredIds(OrderStatus status, Instant now, Limit limit)` / `@Modifying(clearAutomatically = true) @Query("update Order o set o.status = :to, o.version = o.version + 1, o.updatedAt = :now where o.id = :id and o.status = :from and o.expiresAt < :now") int expire(UUID id, OrderStatus from, OrderStatus to, Instant now)` |
| `OrderExpirationService` (order.application) | 클래스에 `@Transactional` 없음. `public int expireBatch()` |
| `OrderExpirationJob` (order.infrastructure) | `@Component`. `@Scheduled(fixedDelay = 60_000) void run() { jobRunner.run("expire-orders", expirationService::expireBatch); }` |

`expireBatch` 순서
1. `ids = findExpiredIds(PENDING_PAYMENT, now, Limit.of(100))`
2. 각 ID마다 `try { tx.execute(s -> expireOne(id)) } catch (Exception e) { log.error("expire failed orderId={}", id, e); }`
3. 만료시킨 건수 반환

`expireOne(id)` (트랜잭션 안)
1. `expire(id, PENDING_PAYMENT, EXPIRED, now)` → 0이면 return false (그사이 결제가 시작됐거나 이미 처리됨)
2. `productApi.releaseReservation(id, ReleaseReason.EXPIRED)`
3. `order = findById(id)` (**CAS 뒤에** 읽는다) → `order.cancelPendingChildren()`
4. return true

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `common/job/JobLockTest` | [ ] 스레드 2개가 동시에 같은 잡 이름으로 `runExclusively`(작업 안에서 latch로 잠시 대기) → 하나만 true / [ ] 작업이 예외를 던져도 다음 호출은 true(락이 풀림) / [ ] 다른 잡 이름은 서로 막지 않는다 |
| `order/application/OrderExpirationServiceTest` | [ ] `expires_at`을 과거로 → `expireBatch()` → EXPIRED, 재고 available 원복·예약 EXPIRED, 가게주문·품목 CANCELLED / [ ] **PAYMENT_IN_PROGRESS 주문은 `expires_at`이 지나도 만료되지 않는다** / [ ] 아직 안 지난 주문은 그대로 / [ ] **한 주문 처리 실패가 나머지를 막지 않는다**: 주문 3개 중 하나의 재고를 JDBC로 망가뜨려(`stock.reserved`를 0으로, `available`을 그만큼 증가) 해제가 실패하게 하면 → 나머지 2개는 EXPIRED, 반환값 2 |

**리뷰 때 물어볼 것**
- 트랜잭션 레벨 advisory lock과 세션 레벨 advisory lock의 차이는?
- 잡 락이 있는데도 처리 자체를 멱등하게(CAS) 만든 이유는?
- 왜 100건을 한 트랜잭션이 아니라 건별 트랜잭션으로 처리하나?

---

## 2-6. 결제 대사 (+ PG 취소) (M)

**목표**
- 결과가 불확실했던 결제를 Toss에 조회해서 확정하고, 주문을 완료하거나 실패 처리한다.
- PG는 승인했는데 우리가 주문을 완료할 수 없으면 승인을 취소한다(보상).
- 이 단계에서 PG 취소(`PaymentApi.cancel`)를 처음 만든다(2-8 품목 환불이 재사용).

**왜 지금**: 2-4에서 생긴 "결제 확인 중" 주문을 끝내는 단계다. 이것으로 "PG 승인은 정확히 한 번 반영되고, 반영 못 한 승인은 취소된다"(INV-02)가 완성된다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 대사(reconciliation) | 우리 기록과 외부(PG) 기록을 맞춰보고 차이를 해결하는 작업 |
| 주문이 주도하는 대사 | 주문 모듈이 결제 모듈에게 "이 주문 결제 어떻게 됐어?"라고 묻는다. 이벤트가 필요 없다 (ADR-004) |
| PG 멱등 키 재사용 | 결과가 불확실했던 취소는 **같은 Idempotency-Key로 다시 호출**한다. Toss가 처음 결과를 돌려주므로 두 번 취소되지 않는다 (확인 필요: Toss 멱등 키 보관 기간 15일) |

**정책 (이 단계에서 정함)**
- 재시도 간격: 잡이 1분마다 돌므로 **조회에 실패하면 다음 실행(1분 뒤)에 다시** 한다. `reconcile_attempts`만 늘린다(Part 7 알림의 근거).
- 대사 대상: PAYMENT_IN_PROGRESS로 30초 넘게 머문 주문.
- 보상 취소: PG 승인 시각으로부터 5분이 지났는데도 주문 완료에 실패하면 전액 취소하고 주문을 PAYMENT_FAILED로.

### 할 일

**1) 마이그레이션** — `db/migration/payment/V{...}__payment_create_payment_cancel.sql`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_payment_cancel` |
| payment_id | uuid | X | `fk_payment_cancel_payment` |
| idempotency_key | varchar(100) | X | `uk_payment_cancel_idempotency_key` |
| amount | bigint | X | `> 0` |
| reason | varchar(200) | X | |
| status | varchar(20) | X | `ck_payment_cancel_status`: `IN ('REQUESTED','DONE','FAILED','UNKNOWN')` |
| failure_code | varchar(100) | O | |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

**2) payment — 대사·취소**

`PaymentApi` 추가
```java
Optional<PaymentView> find(UUID orderId);
PgOutcome queryOutcome(UUID orderId);      // 트랜잭션 밖 전용
CancelOutcome cancel(UUID orderId, Money amount, String reason, String idempotencyKey);  // 트랜잭션 밖 전용
record PaymentView(UUID paymentId, PaymentState state, long amount, long cancelledAmount, Instant approvedAt) {}
enum CancelOutcome { DONE, FAILED, UNKNOWN }
```

`queryOutcome` 순서
1. payment 조회 → 없으면 `IllegalStateException`
2. 이미 APPROVED(취소 포함)면 `PgOutcome(APPROVED, ...)`, FAILED면 `PgOutcome(REJECTED, failureCode, ...)` — PG 호출 없음
3. `tossClient.queryByOrderId(paymentId, pgOrderId)` → `Approved`면 APPROVED(승인 시각 포함), `Rejected`면 REJECTED
4. `Unknown`이면 짧은 트랜잭션에서 `reconcile_attempts + 1`, status가 IN_PROGRESS면 UNKNOWN으로 → `PgOutcome(UNKNOWN)`

`cancel` 순서
1. 트랜잭션 A: payment가 APPROVED·PARTIAL_CANCELLED가 아니면 `IllegalStateException` → `payment_cancel` INSERT(REQUESTED) `ON CONFLICT ON CONSTRAINT uk_payment_cancel_idempotency_key DO NOTHING` → 행을 다시 읽어 DONE이면 `return DONE`, FAILED면 `return FAILED` → `amount > payment.amount - cancelled_amount`면 `IllegalArgumentException`
2. 트랜잭션 밖: `tossClient.cancel(paymentId, paymentKey, amount, reason, idempotencyKey)`
3. 트랜잭션 B: `Approved` → cancel 행 CAS(`REQUESTED`·`UNKNOWN` → DONE) 성공 시에만 `payment.cancelled_amount += amount`, status는 누계 = 금액이면 CANCELLED 아니면 PARTIAL_CANCELLED / `Rejected` → FAILED(+코드) / `Unknown` → UNKNOWN
4. 결과 반환
- 결과가 UNKNOWN이었던 취소를 같은 키로 다시 부르면 1에서 행이 UNKNOWN이므로 2로 가서 **같은 Idempotency-Key로** Toss를 다시 부른다.

**3) order — 대사 잡**

| 대상 | 규격 |
|---|---|
| `OrderRepository` | `@Query("select o.id from Order o where o.status = :status and o.updatedAt < :before order by o.updatedAt") List<UUID> findStuckIds(OrderStatus status, Instant before, Limit limit)` |
| `OrderReconcileService` (order.application) | 클래스에 `@Transactional` 없음. `public int reconcileBatch()` |
| `OrderReconcileJob` (order.infrastructure) | `@Scheduled(fixedDelay = 60_000)` → `jobRunner.run("reconcile-orders", service::reconcileBatch)` |

`reconcileBatch` 순서 — `findStuckIds(PAYMENT_IN_PROGRESS, now - 30초, Limit.of(100))`의 각 주문마다 try/catch로 감싸서:
1. `view = paymentApi.find(ORDER, orderId)` (없으면 로그 후 다음 주문)
2. `outcome = paymentApi.queryOutcome(ORDER, orderId)` → UNKNOWN이면 다음 주문
3. `try { tx.execute(s -> applier.apply(orderId, view.paymentId(), outcome)) }`
4. 3이 예외로 실패했고 outcome이 APPROVED이며 `outcome.approvedAt() < now - 5분`이면 **보상**:
   1. `tx.execute(s -> paymentApi.applyOutcome(paymentId, outcome))` — 승인 사실만 먼저 기록
   2. `paymentApi.cancel(ORDER, orderId, 결제 금액, "주문 완료 실패로 자동 취소", "order-compensate-" + orderId)`
   3. DONE이면 `tx.execute(s -> { applier.failOrder(orderId, "결제 완료 처리 실패로 자동 취소"); })`, 아니면 다음 주기에 다시
5. 처리한(PAID 또는 PAYMENT_FAILED가 된) 건수 반환

### 완료 확인

| 테스트 클래스 | 케이스 (가짜 PG 서버, 대사 대상이 되도록 JDBC로 주문 `updated_at`을 과거로) |
|---|---|
| `payment/application/PaymentCancelTest` | [ ] 전액 취소 → DONE, payment CANCELLED, `cancelled_amount` = 금액 / [ ] 부분 취소 두 번 → PARTIAL_CANCELLED 후 CANCELLED / [ ] 같은 키로 두 번 → 가짜 서버 취소 요청 1건, 두 번째는 DONE 반환 / [ ] 취소 타임아웃 → UNKNOWN → 같은 키로 다시 → DONE, `cancelled_amount` 한 번만 증가 / [ ] 거절 → FAILED |
| `order/application/OrderReconcileServiceTest` | 아래 |

`OrderReconcileServiceTest`
- [ ] 승인 타임아웃(`delay`, 가짜 서버는 승인 기록) → 대사 → 주문 PAID, payment APPROVED
- [ ] 승인 타임아웃(`drop`, 승인 기록 없음) → 대사 → 조회 404 → 주문 PAYMENT_FAILED, 재고 원복
- [ ] 조회도 실패(`/v1/payments/orders` 경로에 `serverError()`) → 주문 그대로, `reconcile_attempts` 1 → 다시 실행 → `reconcile_attempts` 2
- [ ] **결과 반영 경쟁**: 같은 주문에 `applier.apply(APPROVED)`를 두 스레드에서 동시에 → 재고 `sold` 한 번만 증가, 재고 이력 COMMIT 1건, 주문 PAID (한쪽은 예외 없이 끝나거나 낙관적 락 예외)
- [ ] **승인 후 완료 불가 → 보상**: 승인 타임아웃 → JDBC로 재고를 망가뜨려(`reserved`를 0으로, `available`을 그만큼 증가) 완료가 실패하게 하고 `pg.markApproved(..., approvedAt = 10분 전)` → 대사 → 가짜 서버 취소 요청 1건, payment CANCELLED, 주문 PAYMENT_FAILED

**리뷰 때 물어볼 것**
- 외부 결제가 타임아웃 나면 어떻게 처리하나? (면접 단골 — 이 단계의 테스트가 답)
- 타임아웃 즉시 PG에 취소를 보내지 않고 조회부터 하는 이유는?
- 대사가 영영 확정을 못 하면? (Part 7 운영 API·알림 예고)
- 보상 취소에서 승인 사실을 먼저 기록하는 이유는?

---

## 2-7. 판매자 주문 관리: 발송 · 배송완료 (M)

**목표**: 판매자가 자기 가게의 주문을 상태별로 보고, 송장번호를 입력해 발송하고, 배송완료로 바꾼다.

**왜 지금**: 결제 다음 단계다. 원 프로젝트는 가게별로 주문을 볼 수 없었다(`must.md` #1·2, As-Is ORD-11).

**정책 (이 단계에서 정함)**
- 판매자 주문 목록·상세는 CLOSED 가게 소유자도 볼 수 있다(`ShopApi.verifyOwner`).
- 발송은 가게주문 단위로 한 번에 한다(품목별 부분 발송 없음). 이미 취소된 품목은 발송 대상에서 빠진다.

### 할 일

**1) 마이그레이션** — `db/migration/order/V{...}__order_add_shop_order_indexes.sql`
- `idx_shop_order_shop_id_status_created_at ON orders.shop_order (shop_id, status, created_at DESC, id DESC)`
- `idx_shop_order_shop_id_created_at ON orders.shop_order (shop_id, created_at DESC, id DESC)` (상태 필터 없을 때)

**2) order.domain — Order에 추가**

| 메서드 | 규격 |
|---|---|
| `ShopOrder findShopOrder(UUID shopOrderId)` | 없으면 `BusinessException(SHOP_ORDER_NOT_FOUND)` |
| `void ship(UUID shopOrderId, String carrier, String trackingNumber, Instant now)` | 가게주문 `PAID → SHIPPED`, carrier·trackingNumber·shippedAt 기록, **CANCELLED가 아닌** 품목 `PAID → SHIPPED` |
| `void deliver(UUID shopOrderId, Instant now)` | 가게주문 `SHIPPED → DELIVERED`, deliveredAt 기록, SHIPPED 품목 `→ DELIVERED` |

- 가게주문과 품목의 상태를 같이 바꾸는 책임은 **애그리거트 루트(Order)**에 있다. 서비스가 품목 상태를 직접 바꾸지 않는다.

`OrderErrorCode` 추가: `SHOP_ORDER_NOT_FOUND`

**3) order.domain — 조회 쿼리** (`OrderRepository` 또는 `ShopOrderQueryRepository`)
- `Optional<Order> findByShopOrderId(UUID shopOrderId)`: `select o from Order o join o.shopOrders so where so.id = :shopOrderId`
- 목록: `ShopOrderListRow(UUID shopOrderId, UUID orderId, ShopOrderStatus status, ShippingAddress shippingAddress, Instant createdAt)` — JPQL `from ShopOrder so join Order o on o.id = so.orderId where so.shopId = :shopId and (:status is null or so.status = :status)` + keyset(1-7 방식). 첫 페이지/다음 페이지 메서드 2개
- 품목: `List<OrderLine> findLinesByShopOrderIdIn(Collection<UUID> shopOrderIds)` — 페이지의 가게주문 ID를 모아 **한 번에**

**4) order.application**

| 클래스 | 메서드 |
|---|---|
| `SellerOrderQueryService` (`@Transactional(readOnly = true)`) | `CursorPage<SellerShopOrderResult> getShopOrders(UUID memberId, UUID shopId, ShopOrderStatus status, String cursor, int size)` / `SellerShopOrderResult getShopOrder(UUID memberId, UUID shopId, UUID shopOrderId)` |
| `ShipmentService` (`@Transactional`) | `SellerShopOrderResult ship(UUID memberId, UUID shopId, UUID shopOrderId, String carrier, String trackingNumber)` / `SellerShopOrderResult deliver(UUID memberId, UUID shopId, UUID shopOrderId)` |

`SellerShopOrderResult(UUID shopOrderId, UUID orderId, ShopOrderStatus status, ShippingAddress shippingAddress, String carrier, String trackingNumber, Instant shippedAt, Instant deliveredAt, long amount, List<SellerLineResult> lines, Instant createdAt)` — amount는 취소되지 않은 품목 `lineAmount` 합
`SellerLineResult(UUID orderLineId, UUID productId, String productName, long unitPrice, int quantity, long lineAmount, long refundedAmount, OrderLineStatus status)`

공통 앞단: `shopApi.verifyOwner(shopId, memberId)` → `findByShopOrderId` → 그 가게주문의 `shopId`가 요청 `shopId`와 다르면 `SHOP_ORDER_NOT_FOUND` (남의 가게 주문은 없는 것처럼)

**5) order.web** — `SellerOrderController` (`/api/shops/{shopId}/orders`)

| API | 요청 | 응답 |
|---|---|---|
| `GET /api/shops/{shopId}/orders?status=&cursor=&size=` | | 200 `CursorPage<SellerShopOrderResponse>` |
| `GET /api/shops/{shopId}/orders/{shopOrderId}` | | 200 `SellerShopOrderResponse` |
| `POST /api/shops/{shopId}/orders/{shopOrderId}/ship` | `ShipRequest(@NotBlank @Size(max=30) String carrier, @NotBlank @Pattern(regexp="^[0-9-]{8,30}$") String trackingNumber)` | 200 `SellerShopOrderResponse` |
| `POST /api/shops/{shopId}/orders/{shopOrderId}/deliver` | | 200 `SellerShopOrderResponse` |

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `order/domain/OrderShipmentTest` | [ ] `ship`: 가게주문 SHIPPED, 송장 기록, PAID 품목 SHIPPED, CANCELLED 품목은 그대로 / [ ] PAID가 아닌 가게주문 `ship` → `INVALID_STATE_TRANSITION` / [ ] `deliver` 전후 상태 / [ ] SHIPPED 아닌데 `deliver` → 예외 |
| `order/web/SellerOrderControllerTest` | [ ] 가게 A·B 상품을 한 주문으로 결제 → A 판매자 목록에 A 가게주문만 / [ ] 상태 필터 / [ ] 발송 → 배송완료 흐름 / [ ] **다른 가게 판매자가 내 가게 shopId로 호출 → 403**, 자기 가게 shopId + 남의 가게주문 ID → 404 `SHOP_ORDER_NOT_FOUND` / [ ] 결제 대기(PENDING) 가게주문 발송 → 409 / [ ] 목록 조회 SQL 수가 가게주문 3개일 때와 10개일 때 같다 |

**제안 (선택)**
- 판매자 주문 목록의 기간 필터(`from`~`to`, 업무 날짜 기준)

**리뷰 때 물어볼 것**
- 가게주문 상태와 품목 상태가 어긋나지 않게 누가 책임지나?
- 판매자 목록에서 주문(orders)과 가게주문을 조인해도 되는 근거는? (같은 schema, 같은 모듈)

---

## 2-8. 품목 취소와 환불 (L)
> PR을 둘로 나눈다: **2-8a** 취소·환불, **2-8b** 복구 잡

**목표**
- 발송 전 품목을 하나만 취소하면 **그 품목 금액만** PG 부분취소로 환불된다. 재고도 돌아온다.
- 중간에 실패해도 복구 잡이 이어서 끝낸다.

**왜 지금**: 원 프로젝트의 가장 심각한 결함 중 하나(품목 환불인데 주문 전액 환불, As-Is ORD-04)를 해결한다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 환불 오케스트레이션 | Refund가 단계별 상태(APPROVED → PG_CANCELLED → COMPLETED)를 가지고 진행. 각 단계는 멱등 |
| 부분취소 (POL-08) | 환불액 = 품목 금액 - 이미 환불된 금액. 전액 PG 부분취소. 주문별 누적 취소액 ≤ 승인액은 `payment.cancelled_amount` CHECK가 최종 방어선 |
| 주문 단위 잠금 | 같은 주문의 품목 두 개를 동시에 처리하면 가게주문 상태 재계산이 겹친다 → 주문 행을 `FOR UPDATE`로 잠가 줄을 세운다 |
| 부분 unique 인덱스 | 품목당 "진행 중 환불"은 하나만: `WHERE status IN ('REQUESTED','APPROVED','PG_CANCELLED')` |
| 복구 잡 | 중간 상태로 멈춘 환불을 찾아 남은 단계를 다시 실행 |

**정책 (이 단계에서 정함)**
- 취소는 품목 단위, 그 품목 금액 전체(`lineAmount - refundedAmount`)를 환불한다(부분 수량 취소 없음).
- 진행 중 환불이 있는 가게주문은 발송할 수 없다(409 `REFUND_IN_PROGRESS`).
- PG 취소가 명확히 실패하면 환불은 FAILED로 끝나고 502를 준다. 운영자 처리 대상이다(Part 7 운영 API).

### 할 일 (2-8a)

**1) 마이그레이션** — `db/migration/order/V{...}__order_create_refund.sql`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_refund` |
| order_id | uuid | X | `fk_refund_orders` |
| shop_order_id | uuid | X | `fk_refund_shop_order` |
| order_line_id | uuid | X | `fk_refund_order_line` |
| member_id | uuid | X | |
| reason_type | varchar(20) | X | `ck_refund_reason_type`: `IN ('BUYER_CANCEL','RETURN','SYSTEM')` |
| reason | varchar(200) | O | |
| amount | bigint | X | `> 0` — PG 부분취소 금액 |
| status | varchar(20) | X | `ck_refund_status`: `IN ('REQUESTED','APPROVED','PG_CANCELLED','COMPLETED','REJECTED','FAILED')` |
| reject_reason | varchar(200) | O | |
| completed_at | timestamptz | O | |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- 부분 unique `uk_refund_order_line_active ON orders.refund (order_line_id) WHERE status IN ('REQUESTED','APPROVED','PG_CANCELLED')`
- 인덱스 `idx_refund_order_id`, `idx_refund_shop_order_id_status`, `idx_refund_status_updated_at`

**2) order.domain**

| 클래스 | 규격 |
|---|---|
| `RefundStatus` | `REQUESTED → {APPROVED, REJECTED}`, `APPROVED → {PG_CANCELLED, COMPLETED, FAILED}`, `PG_CANCELLED → {COMPLETED}` |
| `RefundReasonType` | `BUYER_CANCEL, RETURN, SYSTEM` |
| `Refund` | 별도 애그리거트. `@Entity @Table(name = "refund", schema = "orders")`, `extends BaseTimeEntity`, 필드는 컬럼과 1:1 |
| `RefundRepository` | `save`, `findById`, `boolean existsByShopOrderIdAndStatusIn(UUID shopOrderId, Collection<RefundStatus> statuses)`, `List<Refund> findAllByOrderId(UUID orderId)`, `List<UUID> findStuckIds(RefundStatus status, Instant before, Limit limit)` |
| `OrderErrorCode` | `ORDER_LINE_NOT_FOUND`, `REFUND_IN_PROGRESS` |

`Refund` 메서드
- `static Refund approveBuyerCancel(UUID orderId, UUID shopOrderId, UUID orderLineId, UUID memberId, Money amount, String reason)` → APPROVED
- `void markPgCancelled()`, `void complete(Instant now)`, `void fail()`
- `boolean needsPgCancel()` → `status == APPROVED`
- `boolean readyToComplete()` → `status == PG_CANCELLED`

`Order`에 추가
- `@Lock(LockModeType.PESSIMISTIC_WRITE)` 조회 `Optional<Order> findByIdAndMemberIdForUpdate(UUID id, UUID memberId)`, `Optional<Order> findByIdForUpdate(UUID id)` (`OrderRepository`)
- `OrderLine findLine(UUID lineId)` (없으면 `ORDER_LINE_NOT_FOUND`), `ShopOrder shopOrderOf(UUID lineId)`
- `void verifyCancellable(UUID lineId)`: 주문 PAID, 품목 PAID, 가게주문 PAID(미발송)가 아니면 `INVALID_STATE_TRANSITION`
- `void applyRefund(UUID lineId, Money amount, RefundReasonType type, Instant now)`: `refundedAmount += amount`, 품목 `→ CANCELLED`(BUYER_CANCEL) 또는 `→ RETURNED`(RETURN), 이어서 `recalculateShopOrder`
- `recalculateShopOrder(ShopOrder)` (private): 모든 품목이 CANCELLED면 가게주문 `PAID → CANCELLED` / 가게주문이 DELIVERED이고 모든 품목이 CONFIRMED·RETURNED·CANCELLED 중 하나면 `→ COMPLETED`(completedAt)

**3) payment — 주문 결제 부분취소**: 2-6의 `PaymentApi.cancel(ORDER, orderId, amount, reason, idempotencyKey)`를 그대로 쓴다. `idempotencyKey = refundId.toString()`

**4) order.application**

| 클래스 | 규격 |
|---|---|
| `RefundCompleter` (`@Service`) | `@Transactional public RefundStatus complete(UUID refundId)` — 아래 |
| `RefundProcessor` (`@Service`, `@Transactional` 없음) | `public RefundStatus process(UUID refundId)` — PG 취소(필요하면) → 완료. 2-8b 복구 잡, 2-9 반품 승인이 재사용 |
| `CancelOrderLineService` (`@Transactional` 없음) | `public RefundResult cancel(UUID memberId, UUID orderId, UUID lineId, String reason)` |
| `RefundResult` | `record (UUID refundId, RefundStatus status, long amount)` |

`CancelOrderLineService.cancel` 순서
1. 트랜잭션 1 (`tx.execute`)
   1. `order = findByIdAndMemberIdForUpdate` 없으면 `ORDER_NOT_FOUND` — **주문 행을 잠근다**(같은 주문의 다른 취소는 여기서 대기)
   2. `order.verifyCancellable(lineId)`
   3. `amount = line.lineAmount - line.refundedAmount`
   4. `Refund.approveBuyerCancel(...)` save — 같은 품목을 동시에 취소하면 둘 다 2를 통과할 수 있다. 늦은 쪽은 커밋 시 부분 unique 위반 → 409 `DUPLICATE_RESOURCE`
2. `status = refundProcessor.process(refundId)`
3. 반환: COMPLETED → 200 / APPROVED(PG 결과 불확실) → 202 / FAILED → `PG_ERROR` 502

`RefundProcessor.process(refundId)` 순서
1. `refund = 읽기` → `needsPgCancel()`이면:
   - `outcome = paymentApi.cancel(orderId, amount, "품목 취소", refundId.toString())` (트랜잭션 밖)
   - DONE → `tx.execute(markPgCancelled)` / FAILED → `tx.execute(fail)`, return FAILED / UNKNOWN → return APPROVED
2. `return refundCompleter.complete(refundId)`

`RefundCompleter.complete(refundId)` 순서 (한 트랜잭션)
1. `refund = findById` → `!readyToComplete()`면 현재 상태 반환(이미 완료 등, 멱등)
2. `order = findByIdForUpdate(orderId)`
3. `productApi.restore(orderId, productId, quantity, refundId)` — 재고 이력 unique로 멱등
4. `order.applyRefund(lineId, amount, reasonType, now)`
5. `refund.complete(now)` → COMPLETED 반환

`ShipmentService.ship`에 추가: `existsByShopOrderIdAndStatusIn(shopOrderId, {REQUESTED, APPROVED, PG_CANCELLED})`면 `REFUND_IN_PROGRESS` (2-7에서 비워 둔 검사)

`OrderQueryService.getMyOrder` 응답에 `refunds[{refundId, orderLineId, reasonType, status, amount, createdAt}]` 추가

**5) order.web**

| API | 요청 | 응답 |
|---|---|---|
| `POST /api/orders/{id}/lines/{lineId}/cancel` `@Idempotent` | `CancelLineRequest(@Size(max=200) String reason)` | 200 `RefundResponse` (COMPLETED) / 202 (APPROVED) / 409 / 502 |

**6) DB CHECK 확인**: `order_line.refunded_amount <= line_amount`(2-2), `payment.cancelled_amount <= amount`(2-4)가 이미 있다. 테스트로 "넘으면 DB가 거부한다"를 한 번 확인한다.

### 할 일 (2-8b)

`RefundRecoveryService.recoverBatch()` (order.application, `@Transactional` 없음) + `RefundRecoveryJob` (order.infrastructure, `@Scheduled(fixedDelay = 60_000)`, 잡 이름 `recover-refunds`)
1. `findStuckIds(PG_CANCELLED, now - 5분, Limit.of(100))` → 각 `refundCompleter.complete(id)` (건별 try/catch)
2. `findStuckIds(APPROVED, now - 5분, Limit.of(100))` → 각 `refundProcessor.process(id)` — PG 취소가 불확실(UNKNOWN)했거나 호출 전에 죽은 환불. 같은 Idempotency-Key(refundId)로 다시 부르므로 두 번 취소되지 않는다
3. 처리 건수 반환

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `order/domain/RefundStatusTest` | [ ] 전체 조합 파라미터화 |
| `order/domain/OrderRefundTest` | [ ] `applyRefund` → refundedAmount 증가, 품목 CANCELLED / [ ] 마지막 품목 취소 → 가게주문 CANCELLED / [ ] 발송된 가게주문의 품목 `verifyCancellable` → 예외 |
| `order/web/CancelOrderLineTest` (가짜 PG) | [ ] **품목 3개 중 1개 취소 → 그 품목 금액만 환불, 나머지 품목은 PAID 그대로** / [ ] 가짜 서버 취소 요청 `cancelAmount` = 품목 금액, `Idempotency-Key` = refundId / [ ] 품목 두 개를 차례로 취소 → `refunded_amount`·payment `cancelled_amount` 정확, `cancelled_amount ≤ amount` / [ ] 재고 `sold → available` 복구, 재고 이력 RESTORE 1건 / [ ] 발송된 품목 취소 → 409 `INVALID_STATE_TRANSITION` / [ ] 남의 주문 → 404 / [ ] PG 취소 타임아웃 → 202, refund APPROVED / [ ] PG 취소 거절 → 502, refund FAILED, 재고·품목 변화 없음 / [ ] 진행 중 환불이 있는 가게주문 발송 → 409 `REFUND_IN_PROGRESS` |
| `order/application/CancelLineConcurrencyTest` | [ ] **같은 품목 동시 취소 2건**(다른 Idempotency-Key) → 환불 1건 COMPLETED, 나머지는 409 / [ ] 같은 주문의 서로 다른 품목 2개 동시 취소 → 둘 다 COMPLETED, payment `cancelled_amount` = 두 품목 합 ≤ 승인액 |
| `order/application/RefundRecoveryServiceTest` (2-8b) | [ ] **완료 직전 실패 주입**: `@MockitoSpyBean RefundCompleter`로 첫 호출만 예외(`doThrow(...).doCallRealMethod()`) → refund PG_CANCELLED로 남음 → JDBC로 `updated_at` 5분 전 → `recoverBatch()` → COMPLETED, 재고 RESTORE 1건, `refunded_amount` 한 번만 증가 / [ ] 취소 타임아웃으로 APPROVED에 멈춘 환불 → 복구 → 가짜 서버 취소 요청은 같은 키로 2번이지만 `cancelled_amount`는 한 번만 증가 |

**리뷰 때 물어볼 것**
- 부분 환불을 어떻게 구현했나? 누적 취소액이 승인액을 넘지 않는 것은 무엇이 보장하나?
- PG 취소는 성공했는데 DB 반영이 실패하면?
- 각 단계를 멱등하게 만든 키는 각각 무엇인가? (refund 상태 전이, PG Idempotency-Key, 재고 이력 unique)
- 같은 주문의 품목 두 개를 동시에 취소할 때 주문 행을 잠그지 않으면 무엇이 충돌하나?

---

## 2-9. 반품 (S)

**목표**: 배송완료된 품목에 반품을 요청하고, 판매자가 승인하면 2-8과 같은 방식으로 환불한다. 거절하면 원래대로 돌아간다.

**정책 (이 단계에서 정함)**
- 반품 요청은 DELIVERED 품목만, 구매확정 전까지.
- 거절된 품목은 다시 반품을 요청할 수 없다(OPEN-03 권장안).
- 반품 배송비는 다루지 않는다(전액 환불).

### 할 일

**1) order.domain**
- `Refund`: `static Refund requestReturn(UUID orderId, UUID shopOrderId, UUID orderLineId, UUID memberId, Money amount, String reason)` → REQUESTED / `void approve()` → APPROVED / `void reject(String reason)` → REJECTED
- `Order`: `void requestReturn(UUID lineId)`: 주문 PAID, 품목 `DELIVERED → RETURN_REQUESTED` / `void rejectReturn(UUID lineId)`: `RETURN_REQUESTED → DELIVERED`
- `RefundRepository`: `boolean existsByOrderLineIdAndStatus(UUID orderLineId, RefundStatus status)` (거절 이력 확인)
- `OrderErrorCode` 추가: `REFUND_NOT_FOUND`, `RETURN_NOT_ALLOWED`

**2) order.application**

| 클래스 | 메서드 |
|---|---|
| `ReturnService` | `@Transactional RefundResult request(UUID memberId, UUID orderId, UUID lineId, String reason)` / `RefundResult approve(UUID memberId, UUID shopId, UUID refundId)` (`@Transactional` 없음) / `@Transactional RefundResult reject(UUID memberId, UUID shopId, UUID refundId, String reason)` |

`request` 순서: `findByIdAndMemberIdForUpdate` → 그 품목에 REJECTED 환불이 있으면 `RETURN_NOT_ALLOWED` → `order.requestReturn(lineId)` → `Refund.requestReturn(...)` save (금액 = `lineAmount - refundedAmount`)

`approve` 순서
1. 트랜잭션: `shopApi.verifyOwner(shopId, memberId)` → refund 조회, 그 refund의 가게주문이 이 가게 것이 아니면 `REFUND_NOT_FOUND` → `findByIdForUpdate(orderId)` → `refund.approve()`
2. `refundProcessor.process(refundId)` (2-8과 같음. 완료 시 품목 `RETURNED`, 가게주문 재계산)
3. 반환 규칙은 2-8과 같음(200 / 202 / 502)

`reject`: 소유 확인 → `refund.reject(reason)` → `order.rejectReturn(lineId)`

**3) order.web**

| API | 요청 | 응답 |
|---|---|---|
| `POST /api/orders/{id}/lines/{lineId}/return-requests` `@Idempotent` | `ReturnRequest(@NotBlank @Size(max=200) String reason)` | 201 `RefundResponse` (REQUESTED) |
| `POST /api/shops/{shopId}/refunds/{refundId}/approve` | | 200 / 202 / 502 |
| `POST /api/shops/{shopId}/refunds/{refundId}/reject` | `RejectReturnRequest(@NotBlank @Size(max=200) String reason)` | 200 `RefundResponse` (REJECTED) |

- 판매자용 반품 목록이 필요하면 2-7 상세 응답의 품목 상태(RETURN_REQUESTED)로 확인한다(별도 목록 API는 만들지 않음).

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `order/web/ReturnTest` | [ ] 요청 → 승인 → 환불 COMPLETED → 품목 RETURNED, 가게주문의 나머지 품목이 모두 종결이면 COMPLETED / [ ] 요청 → 거절 → 품목 DELIVERED / 다시 요청 → 409 `RETURN_NOT_ALLOWED` / [ ] 배송 중(SHIPPED) 품목 반품 요청 → 409 `INVALID_STATE_TRANSITION` / [ ] **다른 가게 판매자가 승인 → 403** / [ ] 같은 품목에 반품 요청 두 번(다른 키) → 두 번째는 품목이 이미 RETURN_REQUESTED라 409 `INVALID_STATE_TRANSITION` |

**리뷰 때 물어볼 것**
- 2-8 코드를 어떻게 재사용했나? 복사했다면 왜?
- 반품 요청부터 승인까지 품목 상태와 환불 상태를 어떻게 맞췄나?

---

## 2-10. 구매확정 (수동 · 자동) (M)

**목표**: 구매자가 구매확정하거나, 배송완료 후 7일이 지나면 자동으로 확정된다. 확정된 품목은 이후 정산(3-4)과 리뷰(6-1)의 대상이 된다.

**정책 (이 단계에서 정함)**
- 자동 확정 기준: 가게주문 `delivered_at + 7일 < now` (POL-06). 반품 요청 중(RETURN_REQUESTED) 품목은 제외.
- 확정 금액 = `line_amount - refunded_amount` (정산 이벤트는 3-3에서 붙인다).

### 할 일

**1) 마이그레이션** — `db/migration/order/V{...}__order_add_shop_order_status_delivered_at_index.sql`: `idx_shop_order_status_delivered_at ON orders.shop_order (status, delivered_at, id)`

**2) order.domain**
- `Order.confirmLine(UUID lineId, Instant now)`: 품목 `DELIVERED → CONFIRMED`, `confirmedAt = now` → `recalculateShopOrder`
- `Order.autoConfirm(UUID shopOrderId, Instant now)`: 그 가게주문의 DELIVERED 품목만 확정(RETURN_REQUESTED는 건너뜀) → `recalculateShopOrder`. 확정한 품목 수 반환
- `OrderLine.confirmedAmount()` → `lineAmount - refundedAmount`
- `OrderRepository`: `@Query("select so.id, so.deliveredAt from ShopOrder so where so.status = :status and so.deliveredAt < :before and (so.deliveredAt > :cursorAt or (so.deliveredAt = :cursorAt and so.id > :cursorId)) order by so.deliveredAt, so.id")` 형태의 keyset 조회 `List<AutoConfirmTarget> findAutoConfirmTargets(...)` (첫 페이지용 메서드 별도). `AutoConfirmTarget(UUID shopOrderId, Instant deliveredAt)`

**3) order.application**

| 클래스 | 메서드 |
|---|---|
| `ConfirmPurchaseService` | `@Transactional OrderLineResult confirm(UUID memberId, UUID orderId, UUID lineId)` — `findByIdAndMemberId` → `order.confirmLine` |
| `AutoConfirmService` (`@Transactional` 없음) | `public int confirmBatch()` |
| `AutoConfirmJob` (order.infrastructure) | `@Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")` → `jobRunner.run("auto-confirm", service::confirmBatch)` |

`confirmBatch` 순서
1. `before = now - 7일`, 커서 없음으로 시작
2. `targets = findAutoConfirmTargets(DELIVERED, before, cursor, Limit.of(100))` → 비면 종료
3. 각 대상마다 `try { tx.execute(s -> findByShopOrderId(id).autoConfirm(id, now)) } catch (Exception e) { log }` — 낙관적 락 충돌(구매자가 동시에 수동 확정)도 여기서 잡혀 건너뛴다
4. 커서 = 마지막 대상 `(deliveredAt, id)` → 2로 (keyset이라 처리 중 상태가 바뀌어도 누락·중복 없음, As-Is BAT-02)
5. 확정한 품목 수 반환

**4) order.web**: `POST /api/orders/{id}/lines/{lineId}/confirm` → 200 `OrderLineResponse(orderLineId, status, confirmedAt)`

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `order/domain/OrderConfirmTest` | [ ] `confirmLine`: DELIVERED만 / [ ] 마지막 품목 확정 → 가게주문 COMPLETED / [ ] `autoConfirm`: RETURN_REQUESTED 품목은 그대로 / [ ] `confirmedAmount` = lineAmount - refundedAmount |
| `order/application/AutoConfirmServiceTest` | [ ] `delivered_at`을 8일 전으로 → 자동 확정 / 6일 전 → 그대로 / [ ] 반품 요청 중인 품목은 확정되지 않는다 / [ ] 대상 150건(100건 넘게) → 모두 확정 |
| `order/application/ConfirmConcurrencyTest` | [ ] 수동 확정과 `confirmBatch()`를 동시에 20회 반복 → 매번 품목 CONFIRMED, `confirmed_at` 1개, 예외는 낙관적 락·409 외에 없음 |
| `order/web/ConfirmPurchaseTest` | [ ] DELIVERED 아닌 품목 확정 → 409 / [ ] 남의 주문 → 404 |

**리뷰 때 물어볼 것**
- 원 프로젝트의 스케줄러(As-Is ORD-07)와 무엇이 다른가?
- 자동 확정을 offset 페이징으로 돌리면 무슨 일이 생기나?

---

## Part 2 완료
- [ ] develop → main, 태그 `v0.2.0`. Release 노트에 "결제 불확실성 처리"와 "품목 단위 환불" 요약
- [ ] Part 3 문서 다듬기
