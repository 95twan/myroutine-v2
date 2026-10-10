# 07. API 명세 (Stage 1)

> 엔드포인트 목록과 핵심 API의 요청·응답 계약. 필드 단위 상세는 구현 시 springdoc(OpenAPI)로 생성하고, 이 문서와 어긋나면 이 문서를 갱신한다.
> 2026-10-09 · 로드맵과 맞춤: 품목 취소에서 남의 주문은 404 `ORDER_NOT_FOUND`(2-8), DLT 재처리 경로에 파티션(7-3)

## 1. 공통 규약
| 항목 | 규약 |
|---|---|
| Base path | `/api` (외부), `/admin/api` (관리자), `/internal` 없음 — 모듈 간 호출은 Java API |
| 인증 | `Authorization: Bearer {access}`. 권한 표기: `-` 공개, `U` 로그인, `O` 리소스 소유자(구매자 본인 또는 가게 소유자), `A` 관리자 |
| 멱등 | `Idempotency-Key` 헤더 (표에 `IK` 표시). 같은 키 + 같은 요청 → 같은 응답, 다른 요청 → 422, 처리 중 → 409 |
| 페이징 | 목록은 커서 기반: `?size=20&cursor={opaque}` → `{ items, nextCursor }`. 관리자 화면 등 전체 개수가 필요한 곳만 offset |
| 금액 | 원 단위 정수 |
| 시각 | ISO-8601 UTC (`2026-10-02T06:00:00Z`) |
| 에러 | `{ code, message, traceId, details }`, HTTP 상태와 code를 함께 사용 |
| 동시 수정 | 낙관적 락 충돌 → 409 `CONFLICT_RETRY` |

### 주요 에러 코드
| HTTP | code | 상황 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 검증 실패, JSON 형식 오류 |
| 404 | `NOT_FOUND` | 없는 경로 |
| 405 | `METHOD_NOT_ALLOWED` | 지원하지 않는 HTTP 메서드 |
| 401 | `UNAUTHORIZED`(토큰 없음·틀림·만료·폐기 모두), `REFRESH_REUSED` | 인증 |
| 401 | `LOGIN_FAILED` | 이메일 또는 비밀번호 불일치 (어느 쪽이 틀렸는지 알려주지 않음) |
| 403 | `FORBIDDEN`, `MEMBER_BANNED`, `EMAIL_NOT_VERIFIED` | 인가, 제재, 이메일 미인증 회원의 주문·결제·가게 개설 |
| 423 | `ACCOUNT_LOCKED` | 로그인 연속 실패로 잠김 (details.unlockAt) |
| 404 | `*_NOT_FOUND` | `MEMBER_NOT_FOUND`, `MEMBER_ADDRESS_NOT_FOUND`, `SHOP_NOT_FOUND`, `PRODUCT_NOT_FOUND` 등 |
| 409 | `MEMBER_EMAIL_DUPLICATED`, `MEMBER_NICKNAME_DUPLICATED`, `SHOP_BUSINESS_NUMBER_DUPLICATED` | 중복 (서비스의 사전 조회) |
| 409 | `DUPLICATE_RESOURCE` | 동시 요청 경합으로 DB unique 제약에 걸림 |
| 409 | `PRODUCT_DISCONTINUED` | 단종 상품 수정 |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | `@Idempotent` API에 키 없음 |
| 409 | `RETURN_NOT_ALLOWED` | 거절된 품목의 반품 재요청 |
| 422 | `OWN_SHOP_PRODUCT`, `PRODUCT_IMAGE_LIMIT_EXCEEDED`, `IMAGE_NOT_UPLOADED` | 자기 가게 상품 주문, 상품 이미지 개수 초과·업로드되지 않은 이미지 등록 |
| 404 | `CART_ITEM_NOT_FOUND`, `CART_PRODUCT_NOT_FOUND`, `ORDER_LINE_NOT_FOUND`, `SHOP_ORDER_NOT_FOUND`, `REFUND_NOT_FOUND`, `PRODUCT_IMAGE_NOT_FOUND` | |
| 409 | `IDEMPOTENCY_IN_PROGRESS`, `CONFLICT_RETRY`, `INVALID_STATE_TRANSITION` | 경합, 상태 |
| 409 | `ORDER_EXPIRED`, `ORDER_ALREADY_PAID` | 결제 승인 시 주문 상태 불일치 |
| 409 | `REFUND_IN_PROGRESS` | 진행 중 환불이 있는 가게주문 발송 시도 |
| 422 | `OUT_OF_STOCK`, `PRODUCT_NOT_ON_SALE`, `SHOP_NOT_ACTIVE`, `AMOUNT_MISMATCH`, `IDEMPOTENCY_KEY_REUSED` | 비즈니스 규칙 |
| 422 | `SHOP_HAS_ACTIVE_ORDERS`, `MEMBER_HAS_ACTIVE_RESOURCES`, `PRODUCT_NOT_SUBSCRIBABLE`, `OWN_SHOP_PRODUCT` | 폐업·탈퇴·구독 조건 |
| 402 | `PAYMENT_FAILED` | PG 명확한 거절 |
| 429 | `RATE_LIMITED` | 인증코드 발송 등 |
| 502 | `PG_ERROR` | PG 취소 명확한 실패 |
| 500 | `INTERNAL_ERROR` | 예상하지 못한 서버 오류 (내부 정보 비노출) |

## 2. 엔드포인트

### member / auth
| Method | Path | 권한 | 설명 | 요구사항 |
|---|---|---|---|---|
| POST | `/api/auth/signup` | - | 이메일·비밀번호 가입 `{email, password, nickname, name}` → 토큰 (이메일 미인증 상태) | FR-MEM-09 |
| POST | `/api/auth/login` | - | 이메일·비밀번호 로그인 → 토큰. 연속 실패 시 잠금(POL-18) | FR-MEM-09 |
| PATCH | `/api/members/me/password` | U | 비밀번호 변경 `{currentPassword, newPassword}` → 다른 세션 모두 폐기 | FR-MEM-10 |
| POST | `/api/auth/password-reset`, `/api/auth/password-reset/confirm` | - | 재설정 코드 발송 → 코드 + 새 비밀번호 | FR-MEM-10 |
| GET | `/api/auth/oauth/{provider}/authorize-url` | - | OAuth 인가 URL | FR-MEM-01 |
| POST | `/api/auth/oauth/{provider}/login` | - | code 교환 → 기존 회원: 토큰 / 신규: 임시 토큰 | FR-MEM-01 |
| POST | `/api/members/me/social-accounts/{provider}` | U | 로그인한 회원에 소셜 계정 연결 (POL-19) | FR-MEM-01 |
| POST | `/api/auth/email-verifications` | 임시 또는 U | 인증코드 발송 (rate limit). OAuth 가입 중(임시 토큰) 또는 이메일 가입 후 미인증 회원 | FR-MEM-02 |
| POST | `/api/auth/email-verifications/confirm` | 임시 또는 U | 코드 확인 (5회 실패 시 무효) → `email_verified_at` 기록 | FR-MEM-02 |
| POST | `/api/auth/oauth/signup` | 임시 | OAuth 가입 완료 → 토큰 | FR-MEM-01 |
| POST | `/api/auth/refresh` | - | 토큰 회전 | FR-MEM-03 |
| POST | `/api/auth/logout` | U | 현재 세션 폐기 (`?all=true` 전체 세션) | FR-MEM-04 |
| GET/PATCH | `/api/members/me` | U | 내 정보 | FR-MEM-05 |
| GET/POST/PATCH/DELETE | `/api/members/me/addresses[/{id}]` | U | 배송지 | FR-MEM-05 |
| DELETE | `/api/members/me` | U | 탈퇴 (조건 검증) | FR-MEM-06 |
| GET/POST | `/api/inquiries` | U | 문의 | FR-MEM-08 |
| GET | `/admin/api/members` | A | 회원 검색 | FR-MEM-07 |
| POST | `/admin/api/members/{id}/ban`, `/unban` | A | 제재 | FR-MEM-07 |
| POST | `/admin/api/inquiries/{id}/answer` | A | 답변 | FR-MEM-08 |

### shop
| Method | Path | 권한 | 설명 | 요구사항 |
|---|---|---|---|---|
| POST | `/api/shops` | U, IK | 개설 | FR-SHOP-01 |
| GET | `/api/shops/me` | U | 내 가게 목록 | FR-SHOP-05 |
| GET | `/api/shops/{id}` | - | 가게 공개 정보 | |
| PATCH | `/api/shops/{id}` | O | 수정 | FR-SHOP-02 |
| DELETE | `/api/shops/{id}` | O | 폐업 (조건 미충족 시 422 `SHOP_HAS_ACTIVE_ORDERS`) | FR-SHOP-03 |

### product
| Method | Path | 권한 | 설명 | 요구사항 |
|---|---|---|---|---|
| GET | `/api/products` | - | 목록 (category, sort, cursor) | FR-PRD-05 |
| GET | `/api/products/{id}` | - | 상세 (가격, 재고 여부). 평점 요약은 `/api/products/{id}/review-stats`로 따로 (product가 review를 참조하지 않게) | FR-PRD-05 |
| POST | `/api/shops/{shopId}/products` | O, IK | 등록 (초기 재고 포함, 한 트랜잭션) | FR-PRD-01 |
| PATCH | `/api/shops/{shopId}/products/{id}` | O | 수정 (가격 변경 시 이력·이벤트) | FR-PRD-03, 07 |
| PATCH | `/api/shops/{shopId}/products/{id}/status` | O | ON_SALE / HIDDEN / DISCONTINUED | FR-PRD-03 |
| POST | `/api/shops/{shopId}/products/{id}/images/presigned-url` | O | 업로드 URL 발급 (MinIO PUT, 10분) | FR-PRD-02 |
| POST | `/api/shops/{shopId}/products/{id}/images` | O | 업로드한 이미지 등록 `{objectKey}` | FR-PRD-02 |
| DELETE | `/api/shops/{shopId}/products/{id}/images/{imageId}` | O | 이미지 삭제 | FR-PRD-02 |
| POST | `/api/shops/{shopId}/products/{id}/stock-adjustments` | O, IK | 재고 증감 `{delta, reason}` | FR-PRD-04 |
| GET | `/api/shops/{shopId}/products` | O | 판매자용 목록 (재고·상태 포함) | |

### order — 장바구니·주문
| Method | Path | 권한 | 설명 | 요구사항 |
|---|---|---|---|---|
| GET | `/api/cart` | U | 장바구니 (현재가·상태·재고 여부) | FR-PRD-06 |
| POST/PATCH/DELETE | `/api/cart/items[/{id}]` | U | 담기·수량·삭제 | FR-PRD-06 |
| POST | `/api/orders/checkout` | U, IK | 주문서 생성 (재고 예약) | FR-ORD-01~03 |
| POST | `/api/orders/{id}/payment/confirm` | O, IK | PG 승인 | FR-ORD-03~06, FR-PAY-01·02 |
| POST | `/api/orders/{id}/payment/fail` | O | (선택) PG 결제창 실패·취소 통지 → 즉시 해제. 없으면 만료 잡이 정리 | FR-ORD-04 |
| GET | `/api/orders` | U | 내 주문 목록 | FR-ORD-07 |
| GET | `/api/orders/{id}` | O | 상세 (가게주문·품목·환불) | FR-ORD-07 |
| POST | `/api/orders/{id}/lines/{lineId}/cancel` | O, IK | 발송 전 취소 | FR-ORD-08 |
| POST | `/api/orders/{id}/lines/{lineId}/return-requests` | O, IK | 반품 요청 | FR-ORD-09 |
| POST | `/api/orders/{id}/lines/{lineId}/confirm` | O | 구매확정 | FR-ORD-11 |

### order — 판매자
| Method | Path | 권한 | 설명 | 요구사항 |
|---|---|---|---|---|
| GET | `/api/shops/{shopId}/orders` | O | 가게주문 목록 (status, 기간, cursor) | FR-ORD-10 |
| GET | `/api/shops/{shopId}/orders/{shopOrderId}` | O | 상세 | FR-ORD-10 |
| POST | `/api/shops/{shopId}/orders/{shopOrderId}/ship` | O | 발송 `{carrier, trackingNumber}` | FR-ORD-10 |
| POST | `/api/shops/{shopId}/orders/{shopOrderId}/deliver` | O | 배송완료 | FR-ORD-10 |
| POST | `/api/shops/{shopId}/refunds/{refundId}/approve`, `/reject` | O | 반품 승인·거절 | FR-ORD-09 |

### order — 구독
| Method | Path | 권한 | 설명 | 요구사항 |
|---|---|---|---|---|
| POST | `/api/subscriptions` | U, IK | 신청 | FR-SUB-01 |
| GET | `/api/subscriptions` | U | 내 구독 목록 | FR-SUB-02 |
| GET/PATCH | `/api/subscriptions/{id}` | O | 상세·수정 (수량, 배송지, 결제수단) | FR-SUB-02 |
| POST | `/api/subscriptions/{id}/pause`, `/resume`, `/cancel` | O | 상태 변경 | FR-SUB-02 |
| GET | `/api/subscriptions/{id}/cycles` | O | 회차 이력 | |
| GET | `/api/shops/{shopId}/subscriptions` | O | 판매자 구독 현황 | FR-SUB-07 |

### payment
| Method | Path | 권한 | 설명 | 요구사항 |
|---|---|---|---|---|
| POST | `/api/billing-keys` | U | 빌링키 발급 (authKey 교환) | FR-PAY-07 |
| GET/DELETE | `/api/billing-keys[/{id}]` | O | 조회·삭제 | FR-PAY-07 |
| GET | `/api/payments` | U | 결제·취소 내역 | FR-PAY-08 |

### settlement
| Method | Path | 권한 | 설명 | 요구사항 |
|---|---|---|---|---|
| GET | `/api/shops/{shopId}/settlements` | O | 정산 내역 | FR-STL-03 |
| GET | `/api/shops/{shopId}/settlements/{id}/items` | O | 정산 품목 | FR-STL-03 |

### review / search / recommendation
| Method | Path | 권한 | 설명 | 요구사항 |
|---|---|---|---|---|
| POST | `/api/reviews` | U | 작성 `{orderLineId, rating, content}` (구매확정 품목만) | FR-REV-01 |
| PATCH/DELETE | `/api/reviews/{id}` | O | 수정(30일 내)·삭제 | POL-14 |
| GET | `/api/products/{id}/reviews` | - | 리뷰 목록 (정렬: 최신·좋아요) | FR-REV-02 |
| GET | `/api/products/{id}/review-stats` | - | 평점 분포 + 최신 요약 | FR-REV-02·03 |
| POST/DELETE | `/api/reviews/{id}/likes` | U | 좋아요 | FR-REV-02 |
| GET | `/api/search/products` | - | `?q=&category=&cursor=` | FR-SRC-01 |
| GET | `/api/search/autocomplete` | - | `?q=` | FR-SRC-01 |
| GET | `/api/recommendations/me` | U | 취향 추천 | FR-REC-01 |

### 운영 (관리자)
| Method | Path | 설명 | 요구사항 |
|---|---|---|---|
| GET | `/admin/api/outbox?status=DEAD` | 발행 실패 이벤트 | NFR-REL-03 |
| POST | `/admin/api/outbox/{id}/retry` | 재발행 | NFR-REL-03 |
| GET | `/admin/api/dlt/{topic}` · POST `/admin/api/dlt/{topic}/{partition}/{offset}/replay` | DLT 조회·재처리 (offset은 파티션마다 따로라 파티션이 필요하다) | NFR-REL-03 |
| GET | `/admin/api/payments?status=UNKNOWN` | 결과미확정 결제 | FR-PAY-03 |
| POST | `/admin/api/payments/{id}/reconcile` | 즉시 대사 | FR-PAY-03 |
| GET | `/admin/api/refunds?status=FAILED` · POST `/{id}/retry` | 실패 환불 재처리 | |
| POST | `/admin/api/search/reindex` | 무중단 재색인 (alias swap) | FR-SRC-02 |
| POST | `/admin/api/settlements/run?period=2026-09` | 정산 수동 실행 (멱등) | FR-STL-04 |
| GET/POST | `/admin/api/notifications?status=FAILED` · `/{id}/retry` | 실패 알림 재처리 | FR-NTF-02 |

## 3. 핵심 API 계약

### 3.1 POST /api/orders/checkout
```http
POST /api/orders/checkout
Idempotency-Key: 7c9e...
```
```json
{
  "cartItemIds": ["0192...a1", "0192...b7"],
  "addressId": "0192...c3"
}
```
- 클라이언트는 **가격을 보내지 않는다** (POL-17). 상품·수량은 장바구니 항목에서 가져온다.
- 주문에 쓴 장바구니 항목은 체크아웃 때 바로 삭제한다. 장바구니를 거치지 않는 바로 구매(`items`)는 선택 사항이다.

```json
201 Created
{
  "orderId": "0192...d4",
  "status": "PENDING_PAYMENT",
  "totalAmount": 43000,
  "pgOrderId": "ORD-0192d4...",
  "expiresAt": "2026-10-02T06:15:00Z",
  "shopOrders": [
    { "shopId": "...", "shopName": "루틴상회", "lines": [ { "productId": "...", "name": "...", "unitPrice": 15000, "quantity": 2, "lineAmount": 30000 } ] }
  ]
}
```
에러: 422 `OUT_OF_STOCK`(details.productIds), `PRODUCT_NOT_ON_SALE`, `SHOP_NOT_ACTIVE`, `OWN_SHOP_PRODUCT`.

### 3.2 POST /api/orders/{id}/payment/confirm
```json
{ "paymentKey": "tgen_2026...", "pgOrderId": "ORD-0192d4...", "amount": 43000 }
```

| 응답 | 의미 | 클라이언트 동작 |
|---|---|---|
| 200 `{status: "PAID"}` | 승인 완료 | 완료 화면 |
| 202 `{status: "PAYMENT_IN_PROGRESS"}` | 결과 확인 중 (PG 불확실) | `GET /api/orders/{id}` 폴링 |
| 402 `PAYMENT_FAILED` | PG 거절 | 실패 화면, 재주문 |
| 409 `ORDER_EXPIRED` | 승인 시작 전에 만료 | 재주문 안내 (PG 승인 없음) |
| 409 `ORDER_ALREADY_PAID` | 중복 요청 (다른 Idempotency-Key) | 완료 화면 |
| 422 `AMOUNT_MISMATCH` | amount ≠ totalAmount | 위변조 의심, 로그 |

### 3.3 POST /api/orders/{id}/lines/{lineId}/cancel
```json
{ "reason": "단순 변심" }
```
```json
200 { "refundId": "...", "status": "COMPLETED", "amount": 15000 }
202 { "refundId": "...", "status": "APPROVED" }   // PG 취소 결과 확인 중
```
에러: 409 `INVALID_STATE_TRANSITION`(이미 발송·취소됨), 404 `ORDER_NOT_FOUND`(없거나 본인 주문이 아님 — 남의 주문은 없는 것처럼, 2-8), 409 `DUPLICATE_RESOURCE`(같은 품목 동시 취소), 502 `PG_ERROR`(PG 취소 명확한 실패).

### 3.4 POST /api/shops/{shopId}/orders/{shopOrderId}/ship
```json
{ "carrier": "CJ", "trackingNumber": "1234567890" }
```
에러: 409 `REFUND_IN_PROGRESS`(진행 중 환불 존재), 409 `INVALID_STATE_TRANSITION`.

### 3.5 POST /api/subscriptions
```json
{
  "productId": "...", "quantity": 1,
  "cycle": { "type": "WEEKLY", "value": 1 },
  "addressId": "...", "billingKeyId": "...",
  "startDate": "2026-10-05"
}
```
응답: `{ subscriptionId, status: "ACTIVE", unitPrice, nextRunDate }`. 에러: 422 `PRODUCT_NOT_SUBSCRIBABLE`, `OWN_SHOP_PRODUCT`.
