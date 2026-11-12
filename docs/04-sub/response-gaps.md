# 서브: 응답 보완

> 버전 0.1 · 2026-11-09 · 판매자용 상품 상세 API, 실패 환불 항목의 Toss 주문번호·구매자 이메일, 구매자 환불의 거절 사유를 더하고 관리자 종결 사유를 `close_reason`으로 나눴다(단계 ID SUB-17)

> **서브**: 로드맵에서 뺀 기능이다. Stage 1(Part 6) 뒤에 한다.
> **따르는 규칙**: 로드맵 Part 1~6의 공통 규칙. 응답 필드 이름은 [API 명세](../02-design/07-api-spec.md)와 맞춘다.

---

## SUB-17. 응답 보완 (S)

**목표**: 화면이 응답만으로 할 일을 할 수 있게 응답 세 곳을 보완한다. 판매자는 숨긴 상품을 수정하고, 운영자는 PG 밖에서 처리할 환불의 구매자와 주문을 찾고, 구매자는 반품이 왜 거절됐는지 본다.

세 곳 모두 화면을 붙이며 드러난 빈칸이고, 응답 필드나 조회 하나를 더하는 일이라 한 단계로 묶는다.

### 빈 곳

| # | 위치 | 지금 | 깨지는 상황 |
|---|---|---|---|
| 1 | 판매자 상품 조회 | 공개 상세 `GET /api/products/{id}`는 `HIDDEN`이면 404다. 판매자 목록 `SellerProductResponse`에는 설명·이미지·구독 가능 여부가 없다 | 숨긴 상품의 수정 화면에 기존 설명·이미지를 채울 수 없고, 이미지 삭제에 쓸 `imageId`도 얻을 수 없다 |
| 2 | `AdminRefundResponse` | 구매자를 나타내는 값과 `pgOrderId`가 없다 | 종결한 환불의 돈을 PG 밖에서 돌려줄 때 누구에게 줄지 모르고, Toss 상점 관리자에서 결제를 주문번호로 찾을 수 없다 |
| 3 | `refund.reject_reason` | 판매자 거절(`reject`)과 관리자 종결(`close`)이 같은 칸에 사유를 쓰고, 둘 다 `REJECTED`라 구분할 수 없다. 구매자 응답 `RefundDetailResponse`에는 사유가 없다 | 구매자는 반품이 왜 거절됐는지 모른다. 사유를 그대로 내보내면 운영자가 쓴 종결 메모까지 구매자에게 나간다 |

### 할 일

**1) 판매자용 상품 상세** — `product.application`, `product.web`

`GET /api/shops/{shopId}/products/{id}` → 200 `SellerProductDetailResponse`. 가게 주인만 조회하고, 상품 상태와 관계없이(숨김·단종 포함) 돌려준다.

| 대상 | 규격 |
|---|---|
| `ProductQueryService.getShopProduct(UUID memberId, UUID shopId, UUID productId)` → `SellerProductDetailResult` | `shopApi.verifyOwner(shopId, memberId)`(목록 조회와 같다. 폐업한 가게의 주인도 읽을 수 있다) → `productRepository.findByIdAndShopId(productId, shopId)`, 없으면 `PRODUCT_NOT_FOUND` → `stockRepository.findById(productId)`(없으면 `getProduct`와 같은 `IllegalStateException`) |
| `SellerProductDetailResult` | record `(ProductDetailResult product, int available, int reserved, int sold, int received)`. `ProductDetailResult.from(product, stock)`을 그대로 쓰고 재고 4개를 더한다 |
| `SellerProductDetailResponse` | `ProductDetailResponse`의 필드 `id, shopId, name, description, category, price, status, subscribable, thumbnailUrl, images, inStock, createdAt`에 `available, reserved, sold, received`를 더한 평평한 record. `from(SellerProductDetailResult, ImageUrls)`에서 이미지는 `ImageResponse.from(result, imageUrls)`를 쓴다 |
| `SellerProductController` | `@GetMapping("/{id}")`, `@CurrentMember UUID memberId` |

`findByIdAndShopId`는 이미지를 함께 읽지 않으므로 이미지는 지연 로딩으로 읽힌다. 상품 하나라 쿼리 하나가 더해질 뿐이다.

에러: 가게 주인이 아니면 403 `FORBIDDEN`, 가게가 없으면 404 `SHOP_NOT_FOUND`, 그 가게의 상품이 아니면 404 `PRODUCT_NOT_FOUND`.

**2) 실패 환불 항목의 Toss 주문번호와 구매자 이메일** — `member.api`, `order.domain`, `order.application`, `order.web`

`AdminRefundResponse`에 `pgOrderId`(string)와 `buyerEmail`(string)을 더한다. 필드 순서는 `orderId` 뒤에 `pgOrderId`, `orderLineId` 뒤에 `buyerEmail`이다.

| 대상 | 규격 |
|---|---|
| `MemberApi.getEmails(Collection<UUID> memberIds)` → `Map<UUID, String>` | 회원 ID → 이메일. 6-3의 `ShopApi.getShopNames`와 같은 모양으로, `MemberApiImpl`이 기존 `memberRepository.findAllByIdIn(memberIds)`로 한 번에 읽는다. 탈퇴한 회원은 저장된 가린 값(`withdrawn+{id}@deleted.invalid`)이 그대로 나온다 |
| `Order.pgOrderIdOf(UUID orderId)` (static) | `"ORD-" + orderId`. 기존 인스턴스 메서드 `pgOrderId()`는 이것을 부르게 바꾼다. 환불 목록은 `Order`를 읽지 않고 `refund.getOrderId()`만 있으므로 규칙을 한 곳에 둔다 |
| `AdminRefundService.getRefunds` | 페이지의 `refund.getMemberId()`를 모아 `memberApi.getEmails`를 한 번 부른다(`orderRepository.findLinesByIdIn`과 같은 자리). `AdminRefundResult.from`에 이메일을 넘긴다 |
| `AdminRefundResult`·`AdminRefundResponse` | `pgOrderId`는 `Order.pgOrderIdOf(refund.getOrderId())`, `buyerEmail`은 위 맵에서 꺼낸다 |

**3) 관리자 종결 사유를 나누고, 구매자 환불에 거절 사유를 더한다** — `order.domain`, `order.application`, `order.web`, 마이그레이션

| 대상 | 규격 |
|---|---|
| 마이그레이션 `V{yyyyMMddHHmm}__order_add_refund_close_reason.sql` | `ALTER TABLE orders.refund ADD COLUMN close_reason varchar(200);`. 길이는 `CloseRefundRequest`의 `@Size(max = 200)`과 같다 |
| `Refund` | 필드 `closeReason`(`@Column(name = "close_reason")`)을 더한다. `close(String reason)`은 `rejectReason` 대신 `closeReason`에 쓴다. `reject(String reason)`은 그대로 `rejectReason`에 쓴다 |
| `RefundDetailResult`·`RefundDetailResponse` | `String rejectReason`을 `amount` 뒤에 더한다. `refund.getRejectReason()`이다. `closeReason`은 구매자 응답에 넣지 않는다 |

결과로 구매자 응답의 `rejectReason`은 판매자가 반품을 거절했을 때만 값이 있다. 판매자 거절 사유는 `RejectReturnRequest`에서 `@NotBlank`라 비지 않으므로, `status`가 `REJECTED`인데 `rejectReason`이 `null`이면 관리자가 종결한 환불이다. API 명세에 이 계약을 적는다.

`closeReason`은 지금 어느 응답에도 나가지 않는다. 운영자가 PG 밖에서 처리할 때 남기는 메모이고 DB에서 읽는다.

**배포 전 확인**: 6-3 배포 뒤 운영에서 환불을 종결한 적이 있으면 그 사유가 `reject_reason`에 남아 구매자에게 보인다. 운영 DB에서 아래가 0이면 옮길 것이 없다. 0이 아니면 그 행만 `close_reason`으로 옮기는 마이그레이션을 같은 단계에 더한다(종결된 환불은 PG 취소를 한 번 이상 시도해 `payment.payment_cancel`에 그 환불 키의 행이 있고, 판매자 거절은 PG를 부르지 않아 행이 없다).

```sql
SELECT count(*)
FROM orders.refund r
WHERE r.status = 'REJECTED'
  AND EXISTS (
    SELECT 1 FROM payment.payment_cancel c
    WHERE c.idempotency_key = r.id::text OR c.idempotency_key LIKE r.id::text || '-r%'
  );
```

**4) 테스트 보조** — `support/`

| 대상 | 규격 | 쓰는 곳 |
|---|---|---|
| `RefundRow`에 `String closeReason` 추가 | `refundRowOf`의 SELECT에 `close_reason`을 더한다. 필드 순서는 `rejectReason` 뒤다 | `AdminRefundRetryTest` 8번 |

### 완료 확인

케이스는 [로드맵 README 4.1](../03-roadmap/README.md)의 기준으로 골랐다. 기존 테스트 클래스에 케이스를 더하거나 고친다.

#### `product/web/SellerProductControllerTest`

**준비**: 판매자 1명, 가게 1개, 상품 1개(`registerProduct(sellerId, shopId, 10000, 10)`).

**1. 숨긴 상품의 판매자 상세를 조회한다.** — 준비: `registerProductImage(sellerId, shopId, productId)`로 이미지 1장(결과의 `imageId`), 그 뒤 `hideProduct(productId)`. 동작: 판매자가 `GET /api/shops/{shopId}/products/{productId}`.
- 응답(andExpect): 상태 200, `$.status` = `HIDDEN`, `$.description` = `description`, `$.images[0].imageId` = imageId, `$.available` = 10.

**2. 판매자 상품 상세 조회를 실패한다. (다른 회원의 가게)** — 동작: 다른 회원이 같은 경로로 조회.
- 응답(andExpect): 상태 403, `$.code` = `FORBIDDEN`.

**3. 판매자 상품 상세 조회를 실패한다. (가게에 속하지 않은 상품)** — 준비: 같은 판매자의 두 번째 가게와 그 가게의 상품 B. 동작: 첫 가게 경로로 상품 B를 조회(`GET /api/shops/{shopId}/products/{productB}`).
- 응답(andExpect): 상태 404, `$.code` = `PRODUCT_NOT_FOUND`.

#### `order/web/AdminRefundRetryTest`

준비는 6-3의 같은 클래스 준비([6-3 완료 확인](../03-roadmap/part6-ops-wrapup.md))를 그대로 쓴다. 구매자의 이메일은 가입할 때 쓴 값이다.

**4. FAILED 환불 목록 조회 (고침)** — 확인에 두 필드를 더한다.
- 응답(andExpect): `$.items[0].pgOrderId` = `"ORD-" + orderId`, `$.items[0].buyerEmail` = 구매자 이메일.

**8. 구매자 취소 환불 종결 (고침)** — DB 확인을 바꾼다.
- DB(refundRowOf(refundId)): `status()` = `REJECTED`, `closeReason()` = `PG에서 이미 취소됨`, `rejectReason()`이 `isNull()`.

**13. 종결한 환불의 사유는 구매자 주문 상세에 나오지 않는다.** — 준비: 위 준비 뒤 `POST /admin/api/refunds/{refundId}/close`, 본문 `{"reason":"PG에서 이미 취소됨"}`. 동작: 구매자가 `GET /api/orders/{orderId}`.
- 응답(andExpect): 상태 200, `$.refunds[0].status` = `REJECTED`, `$.refunds[0].rejectReason`이 `nullValue()`.

#### `order/web/ReturnTest`

**반품을 거절하면 구매자 주문 상세의 환불에 거절 사유가 나온다.** — 준비: 2-9 거절 케이스와 같이 배송 완료 품목에 반품을 요청하고 판매자가 `{"reason":"사용 흔적 있음"}`으로 거절. 동작: 구매자가 `GET /api/orders/{orderId}`.
- 응답(andExpect): 상태 200, `$.refunds[0].status` = `REJECTED`, `$.refunds[0].rejectReason` = `사용 흔적 있음`.

**리뷰 때 물어볼 것**

- 판매자 상세가 공개 상세(`getProduct`)를 재사용하지 않고 `findByIdAndShopId`로 따로 읽는 이유는?
- 환불 목록 한 페이지에서 회원 모듈을 몇 번 부르나? 한 건씩 `getContact`를 부르면 무엇이 달라지나?
- 거절 사유와 종결 사유를 한 칸에 두고 구매자 응답에서 종결만 가리면 안 되는 이유는?
