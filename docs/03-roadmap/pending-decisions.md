# 구현 때 정할 것

> 버전 0.1 · 2026-10-16 · 로드맵·설계 문서 전체에서 "쓰지 않는 요소", "재사용하려고 억지로 맞춘 경로", 단계 사이 어긋남을 점검한 결과 중 아직 결정하지 않은 항목을 모았다. 점검은 문서와 `src/`를 grep으로 확인한 것이고 코드를 실행하지는 않았다.

각 항목은 해당 단계를 구현할 때 정하고, 정하면 그 단계 문서에 반영한 뒤 이 표에서 지운다.

## 1. 구현이 이미 있어 코드와 문서를 함께 고쳐야 하는 것

| 항목 | 문제 | 근거 | 위치 | 고치는 방향 |
|---|---|---|---|---|
| `price_history` 테이블 | 가격 변경마다 INSERT만 하고 읽는 곳이 없다. 조회 API·쿼리·consumer·요구사항이 없다. `product-price-changed` 이벤트가 `oldPrice`·`newPrice`·`changedAt`을 이미 담는다 | `price_history`를 읽는 코드 0건, 요구사항 FR-PRD-07은 구독자 알림이고 이력을 요구하지 않는다 | `V202610081540__product_create_price_history.sql`, `UpdateProductService`, `JpaProductRepository`, `ProductRepository.insertPriceHistory`, 1-8 완료 확인("price_history 1건") | 테이블·`insertPriceHistory`·완료 확인 케이스 삭제. 이력이 필요하면 요구사항에 먼저 올린다. 남기면 `changed_at`과 `created_at`이 같은 값이라 하나로 합친다 |
| `stock_reservation.expires_at`과 인덱스 | 아무도 읽지 않는다. 만료 잡은 `orders.expires_at`을 읽는다. 인덱스 주석 "(2-5 만료 잡용)"과 `08-stock-design-evolution.md`의 같은 설명이 틀렸다 | `StockReservation.expiresAt`을 읽는 곳 0건. `04-state-machines.md`도 "예약의 expires_at은 참고값"이라고 적었다 | `V202610090100__product_create_stock_reservation.sql`, `StockReservation`, `ProductApi.reserve(..., expiresAt)`, `ProductApiImpl`, `StockReservationRepository`, 테스트의 `reserve` 호출 | 컬럼·인덱스·`reserve`의 `expiresAt` 인자 삭제(마이그레이션은 새 파일). 남기면 거짓 설명을 지운다 |
| `StockRepository.findProductIdsWithBrokenBalance` | 항상 빈 목록이다. `ck_stock_balance` CHECK가 같은 등식의 위반을 만들 수 없게 막는다. 7-2 `StockCheckJob`은 이 메서드를 쓰지 않는다. 1-9 완료 확인의 "끝에 빈 목록" 단언이 아무것도 증명하지 못한다 | 운영 코드 호출 0건, 테스트 14곳 | `StockRepository`, `JpaStockRepository`, 테스트 4개 파일, `08-stock-design-evolution.md` §3 | 메서드와 `StockStateCheck` 단언 삭제. `available`·`reserved`·`sold`를 직접 단언한다 |
| `cart` 테이블 | `member_id` 한 컬럼뿐인 껍데기인데 모든 장바구니 동작이 먼저 `cart`를 읽거나 만든다. 담기 한 번에 `cart`·`cart_item`에 문장 4개가 나가고, 마지막 select는 방금 쓴 행의 id를 얻으려는 재조회다 | `cart.id`를 참조하는 곳은 `cart_item.cart_id`뿐이다 | `V202610111013__order_create_cart.sql`, `CartService`, `CartRepository`, 체크아웃의 `findByMemberId` | `cart_item.member_id`를 직접 두고 `UK(member_id, product_id)`로 바꿔 `cart`를 없앤다. 최소한 `INSERT … RETURNING id`로 마지막 재조회만 없앤다 |
| 첫 배송지 등록 | "기존 기본 해제" 경로(`changeDefault`)를 재사용해서, 기존 배송지가 없다는 이미 아는 사실을 다시 조회하고 같은 행을 두 번 쓴다. 문장이 4개 나간다 | `first`가 true인 순간 기본 배송지가 없음은 확정이다 | `MemberAddressService.register`, `MemberAddress.register`, 1-5 순서 | 첫 배송지는 `isDefault=true`로 바로 INSERT하고 해제 경로를 건너뛴다 |
| 체크아웃의 `getPurchasable` 이중 호출 | 체크아웃이 `getPurchasable`을 부른 직후 `reserve` 안에서 같은 상품 ID로 같은 JOIN 조회를 한 번 더 하고 결과를 버린다 | 문서의 이유는 "조회 시점과 예약 시점 사이에 상태가 바뀔 수 있다"인데 같은 트랜잭션·READ COMMITTED라 틈이 거의 없다 | `CheckoutService`, `ProductApiImpl.reserve` | 모듈 API의 독립 호출 계약 때문이라면 "의도적 중복 조회"로 문서에 명시한다. 아니면 `reserve`는 재고 UPDATE만 하고 호출자가 `getPurchasable`을 책임진다 |
| `stock_movement`의 `RESERVE`·`COMMIT`·`RELEASE` 행 | 쓰기만 하고 읽지 않는다. 읽는 곳은 `RECEIVE`·`ADJUST` 합계(7-2 `StockCheckJob`)와 `RESTORE`의 unique 멱등뿐이다. 재고 핫스팟 경로에서 상품당 INSERT가 예약 1회 + 확정 또는 해제 1회 더 나간다 | ADR-008 "모든 수량 변화는 기록"이 유일한 이유다 | `ProductApiImpl.reserve`·`commit`·`release`, ERD, 1-9 규격 | 감사 목적이면 읽는 소비자(점검 쿼리)를 ADR에 명시한다. 아니면 이 세 종류를 빼고 `type`을 `RECEIVE`·`ADJUST`·`RESTORE`로 줄인다 |
| `StoredKey.updatedAt` | SELECT·매핑만 하고 읽는 곳이 없다 | `updatedAt()` 접근 0건 | `IdempotencyRepository`, `StoredKey` | 레코드 필드와 SELECT 컬럼 삭제. 컬럼은 갱신 감사용으로 둘지 정한다 |
| `shop.closed_at` | 쓰기만 하고 읽지 않는다 | 응답·쿼리·이벤트 어디에도 없다 | `Shop.closedAt`, `V202610061735__shop_create_shop.sql` | 컬럼 삭제 또는 폐업 시각을 응답·정산에 쓸 요구를 문서에 올린다 |
| `PaymentView.cancelledAmount` | 2-6 `reconcileBatch`가 읽지 않는다. 문서에서는 이미 지웠다 | 로드맵·설계 문서에서 읽는 곳 0건 | `PaymentView.java`, `PaymentApiImpl.find` | 필드와 변환 삭제 |
| `queryOutcome` 이름과 확정 분기 | 2-6 문서에서 `queryPgOutcome`으로 이름을 바꾸고 `APPROVED`·`FAILED`·`CANCELLED` 확정 분기를 지웠다 | 2-6 `queryPgOutcome` 규격 | `PaymentApi`, `PaymentApiImpl`, `OrderReconcileService` | 이름 변경, 확정 분기 제거, `reconcileBatch`를 경로 A·B·C와 보상 3단계로 구현 |
| `Order.productIds()` | 문서는 "`CheckoutService` 8번 `reserve`의 입력"이라고 하지만 실제 8번은 `quantityByProduct` Map을 쓴다. 쓰는 곳이 없다 | | `Order.java` | 메서드 삭제. 2-2 문서의 쓰는 곳 설명도 고친다 |
| `PgResult.Approved.totalAmount`·`balanceAmount`, `PgResult.Rejected.message`, `TossProperties.clientKey` | 2-3에서 정의했지만 읽는 곳이 없다. `PgOutcome`은 `method`·`approvedAt`·`code`만 옮긴다 | 문서·`src` grep 0건 | `PgResult`, `TossPaymentResponse`, `TossProperties` | 삭제할지 정한다 |

## 2. 구현 단계에서 정할 것

| 단계 | 항목 | 선택지와 영향 |
|---|---|---|
| 전체 | 대사 30초·보상 5분·환불 복구 5분·자동 확정 7일 상수의 위치 | 2-5의 15분처럼 `OrderProperties`에 두는지 정한다 |
| 전체 | `TestFixtures` 헬퍼("테스트 준비")가 "완료 확인" 표 뒤에 있는 구조 | 만드는 순서는 헬퍼 → 테스트다. 구조를 바꾸려면 2-1~2-5를 포함해 고친다 |
| 2-6 | `cancel` A-2·A-3의 재조회 | `insertIfAbsent`가 1행이면 방금 만든 `REQUESTED` 행이고 `id`도 알아서 재조회가 필요 없다. 0행일 때만 읽으면 취소마다 SELECT가 하나 준다. 분기가 늘어난다 |
| 2-7~2-10 | 문서에 이름이 없던 구현물에 붙인 권장 이름 | `OrderLine.markShipped`·`markDelivered`·`addRefundedAmount`·`markReturned`·`markReturnRequested`·`markConfirmed(Instant)`, `ShopOrder.markCancelledWithoutLines`·`markCompleted(Instant)`, `findShopOrderFirstPage`·`findShopOrderNextPage`, `findAutoConfirmTargetsFirstPage`. 컨트롤러는 2-8 `cancel`·2-9 `return-requests`·2-10 `confirm`이 `OrderController`, 2-9의 `/api/shops/{shopId}/refunds/...`가 새 `SellerRefundController` |
| 2-9 | `approve`의 `findByIdForUpdate` | 읽은 `order`를 `refund.approve()`가 쓰지 않고, 중복 승인은 `refund`의 `@Version`과 `transitTo`가 막는다. `findById`로 바꾸면 2-8 표의 "반품 승인" 사용처도 고친다 |
| 3 공통 규칙 P | `SettlementPaidEvent`의 `LocalDate periodStart`·`periodEnd` | 규칙 P는 필드 타입을 원시 타입·UUID·String·long·Instant·List로 제한한다. `LocalDate`를 허용하거나 필드를 `String`·`Instant`로 바꾼다 |
| 3 공통 규칙 S | `notification`의 `payment::api` 의존 | 3-3에서 payment는 이벤트를 발행하지 않고 3-5 consumer도 payment를 쓰지 않는다. 불필요하면 지운다. `order` 행의 "Part 2 + 없음" 표기도 모호하다 |
| 3-2 | `InboxGuard.runOnce`가 `EventContext`와 같은 MDC put/remove를 따로 구현한다 | `EventContext.run` 안에서 실행하게 하면 중복이 없다. 7-1의 "각자 finally" 설명도 고친다 |
| 3-4a | `Settlement.calculate(gross, bp, itemCount)`의 `bp` | `insertIfAbsent`가 `fee_rate_bp`를 이미 넣었다. 엔티티 값을 쓰면 중복이 없지만 엔티티를 native INSERT로만 만들어서 `SettlementPeriodTest`의 "12,345원·500bp → 수수료 617"을 어디서 검증할지 정해야 한다 |
| 3-4a | `payoutAll`의 정산 이중 조회 | `findIdsToPay`가 ID만 주고 루프가 `findById`로 읽은 뒤 쓰기 트랜잭션에서 또 읽는다. `(id, shopId, netAmount)` record를 돌려주면 첫 조회가 사라진다 |
| 3-4a | `settlement/package-info`의 허용 의존 | 공통 규칙 S가 "Part 3 끝 기준"이라 모호하다. 3-4a는 `{common, order::api}`, 3-4b가 `shop::api`를 더하는 것으로 풀었다 |
| 3-4a·3-4b | 3-4a만 따로 PR로 낼 때 컴파일 | `run`이 3-4b의 `payoutAll()`을 부른다. 선언만 두거나 두 단계를 합친다 |
| 3-6 | 배송지 삭제가 `findAllByMemberId` 뒤 건별 `delete`(N번 쿼리) | 벌크 삭제 메서드를 `MemberAddressRepository`에 더할지 정한다 |
| 4-1·4-3 | `device` 전달 사슬(`User-Agent` → `signUp`/`login` → `TokenIssuer` → `SessionStore.create` → Redis) | 어디서도 읽지 않는다. 지우면 인자와 4-1의 "확인 필요" 문단 두 개가 줄지만 ERD의 "기기"와 FR-MEM-03(기기별 세션)에 걸린다. 기기 목록 기능을 쓸 계획이면 남긴다 |
| 4-1 | `rotate`와 `findMemberId`가 Redis를 두 번 부른다 | 합치려면 Lua가 memberId와 재사용 여부를 함께 돌려줘야 한다. "세션 없음"과 "재사용" 구분이 3상태 반환으로 복잡해진다 |
| 5-3 | `Order.markPaymentInProgress()` 신설 | 방금 만든 구독 주문에 2-4 `startPayment` CAS를 걸던 것을 대체했다. 거부하면 CAS로 되돌리고 0행 처리를 정한다 |
| 5-3 | `findDueIds`의 첫 페이지 `lastId` | `null` 허용, 첫 페이지 메서드를 따로 두기, `new UUID(0,0)` 중에서 정한다 |
| 5-3 | `callBillingCharge`가 `Payment`와 `BillingKey`를 다시 읽는다 | `callConfirm`과 같은 선례다. order가 값을 직접 넘기게 바꿀 수도 있다 |
| 5-3 | 건너뜀 경로에서 `insertIfAbsent`가 0행일 때 | `runDue` 1.2와 같게 "종료"를 기본값으로 적었다 |
| 5-1 | 5-1이 추가하는 `FakePgServer` 발급·빌링 결제 동작을 누가 쓰나 | CLAUDE.md의 Claude 작성 예외는 2-3의 `FakePgServer.java`에 한정된다 |
| 6-4 | `recommend`의 평균 벡터를 문자열로 받아 `CAST(... AS vector)`로 다시 넘기는 쿼리 두 개 | CTE 하나로 합치면 `<=>`에 서브쿼리 값이 들어가 HNSW 인덱스를 못 쓸 가능성이 크다 |
| 6-6 | `Inquiry.answer(adminId, content)` | `InquiryAnswer`를 누가 만드는지 정해지지 않았다 |
| 7-1 | MDC `memberId`·`orderId`와 `putCloseable` 규칙 | Kibana 저장 검색과 완료 확인 어디서도 쓰지 않는다. 지우거나 검색·테스트를 더한다 |
| 7-2 | `PaymentMetrics`·`OrderMetrics` 조회 메서드의 `status` 인자 | 항상 상수(`UNKNOWN`, `PAYMENT_IN_PROGRESS`)다. 권장안이라 손대지 않았다 |
| 7-2 | Grafana·Prometheus 호스트 포트 publish | 문서에 없다 |
| 7-2 | ADR-010의 `mail_send_seconds`·`openai_call_seconds` Timer | 대시보드·알림 어디에도 쓰지 않는다. 지표 이름은 ADR이 정한다 |

## 3. 외부 계약에서 읽는 곳이 없는 것

DB 컬럼, API 응답 필드, 이벤트 페이로드는 소비자가 다른 모듈일 수 있어서 문서만으로 지우지 않았다. 소비자가 없다고 확정되면 삭제한다.

### 이벤트·Kafka

| 항목 | 문제 |
|---|---|
| 폐업 → 구독 종료 경로 | order의 `shop-closed` consumer(`terminate("SHOP_CLOSED")`)와 `shop-closed` → product 전 상품 단종 → 상품별 `product-discontinued` → order `terminate("PRODUCT_DISCONTINUED")`가 겹친다. 구독은 반드시 어떤 상품의 구독이라 뒤쪽이 앞쪽을 포함한다. 앞쪽은 문서가 "폐업 조건상 보통 0건, 방어용"이라고 적었다. `ProductDiscontinuedEvent.shopId`·`reason`을 order가 읽지 않는다 |
| `review.review-created.v1` | 소비자가 없다. 리뷰 작성마다 outbox 행 + Kafka 메시지 + 토픽·DLT가 생긴다. 6-5 추가 항목을 하기 전까지 발행하지 않아도 된다 |
| `product.stock-depleted.v1` | 발행하는 단계도 소비하는 단계도 로드맵에 없다 |
| SELLER 역할 부여·회수 체인(shop-opened/closed consumer, `countActiveShops` 재조회, INV-09) | 만드는 값을 읽는 곳이 `/api/members/me` 응답의 `role`뿐이다. 인가는 소유권 기반이고 `hasRole`은 `"ADMIN"` 하나뿐이다. Outbox 학습용 첫 사례라는 목적이면 그렇게 명시한다. 기능 가치를 따지면 `/me`에서 가게 수로 `role`을 파생하는 쪽이 이벤트 없이 같은 값을 준다 |
| `member-registered`의 `email`·`nickname` | 알림은 `memberApi.getContact`로 이메일을 따로 가져온다. 이메일(개인정보)이 토픽에 실려 나간다 |
| `product-upserted.subscribable` | search는 문서 필드로 넣지 않고 recommendation도 읽지 않는다 |
| `product-price-changed.oldPrice` | order는 `newPrice`·`changedAt`만 쓴다 |
| `order-paid`의 `PaidLine.productName`·`quantity`·`orderLineId`, `PaidShopOrder.shopOrderId`, `orderId` | 알림은 `memberId`·`totalAmount`·가게별 `shopId`·품목 수를, recommendation은 `memberId`·`productId`를 쓴다. 알림 본문에 상품명을 넣는지는 문서에 없다 |
| `order-line-confirmed.memberId` | settlement 적재가 쓰지 않는다. 리뷰 자격은 동기 `OrderApi`로 확인한다 |
| `ShopOrderShippedEvent.shopOrderId`, `ShopOrderDeliveredEvent.shopOrderId`, `OrderLineRefundedEvent.refundId`·`orderLineId`, `ShopClosedEvent.memberId` | 읽는 consumer가 없다 |
| 봉투의 `eventType`·`occurredAt`, `outbox_event.aggregate_type`과 `publish`의 `aggregateType` 인자 | 토픽마다 consumer 메서드가 따로 있어 `eventType`을 쓰지 않는다. `aggregate_type`은 "조회·로그용"이라고만 적혀 있고 관리 API 응답에도 없다 |
| `ReleaseReason`·`ReservationStatus.EXPIRED` | `RELEASED`와 `EXPIRED`를 구분해 읽는 코드가 없다. 만료 카운터는 order가 센다. 결제 전 구매자 취소도 `PAYMENT_FAILED` 사유로 부른다 |

### DB 컬럼

| 컬럼 | 문제 |
|---|---|
| `member.ban_reason`, `member.withdrawn_at`, `social_account.provider_email` | 쓰기만 하고 읽지 않는다(`withdrawn_at`은 테스트에서만 확인) |
| `shop_order.completed_at` | 판매자 응답은 `shippedAt`·`deliveredAt`만 내려준다 |
| `refund.completed_at`, `refund.member_id`, `payment_cancel.reason`·`failure_code` | 쓰기만 하고 읽지 않는다 |
| `settlement.fee_rate_bp`, `settlement.payout_attempts` | 요율 스냅샷과 증가만 하는 카운터. 감사 목적이면 이유를 ERD에 적는다 |
| `notification.version`·`channel`·`member_id` | 쓰기가 모두 native SQL이라 `version`은 쓰는 곳이 없다 |
| `subscription.end_reason`, `billing_key.deleted_at`, `product_embedding.model`, `review_summary.source_review_count`·`model`, `inquiry_answer.admin_id` | 쓰기만 하고 읽지 않는다 |
| Redis `auth:session`의 `device` | 위 4-1·4-3 `device` 전달 사슬 항목과 같다 |

### API

| 항목 | 문제 |
|---|---|
| `payment.method`와 `GET /api/payments`(FR-PAY-08) | `payment.method`(그리고 이를 나르는 `PgResult.Approved.method`·`PgOutcome.method`)는 이 API에서만 읽을 수 있는데 이 API를 구현하는 로드맵 단계가 없다 |
| `GET /api/billing-keys/{id}` | 단건 조회는 로드맵에 구현이 없다(목록·삭제·등록만 있다) |
| `SettlementRunResult.paid`·`payoutFailed` | `SettlementJob`은 `settledShops`만 쓴다. `payoutAll()` 반환 타입이 문서에 없는 문제와 함께 정한다 |
| `SubscriptionResult`의 `consecutiveFailures`·`pendingUnitPrice`·`pendingEffectiveDate` 등 | 응답 필드가 문서에 정해져 있지 않다 |

### 문서 표기

| 항목 | 문제 |
|---|---|
| `06-sequences.md` §4 환불 시퀀스의 `opt 항상 (전액 PG 부분취소, POL-08)` | 예치금 제거 뒤 항상 실행되는 블록인데 선택(`opt`)으로 그려져 있다 |
