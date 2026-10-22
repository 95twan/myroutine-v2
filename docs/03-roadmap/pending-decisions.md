# 구현 때 정할 것

> 버전 0.5 · 2026-10-20 · 3-1 착수로 4절의 3-1 행(릴레이 중단 A, 외부 동작 B, `occurredAt` D)을 지웠다
> 0.4 · 2026-10-20 · Part 2 마무리로 4절의 2-5 `JobLock` 예외(E), 2-8a 잠금 범위(B, 테스트 SQL 로그로 확인), 2-8a `RefundResponse`(E) 행을 지웠다
> 0.3 · 2026-10-20 · 2-10 착수로 2절의 "전체 상수 위치"(서비스 안 상수로 확정)와 "2-7~2-10 권장 이름"(구현된 이름으로 확정), 4절의 2-10 행(150건 테스트)과 상태 조합 4번(구매확정의 가게주문 재계산)을 지웠다
> 0.2 · 2026-10-19 · 4절을 더했다: 로드맵·설계 문서의 열린 "확인 필요"를 단계별로 모았다(서브에이전트 4개가 파일 그룹별로 추출, 줄 번호는 이 날짜 기준). 2-9 `approve`의 `findByIdForUpdate` 행은 락 없이 가기로 정해(로드맵 0.90) 지웠다
> 0.1 · 2026-10-16 · 로드맵·설계 문서 전체에서 "쓰지 않는 요소", "재사용하려고 억지로 맞춘 경로", 단계 사이 어긋남을 점검한 결과 중 아직 결정하지 않은 항목을 모았다. 점검은 문서와 `src/`를 grep으로 확인한 것이고 코드를 실행하지는 않았다.

각 항목은 해당 단계를 구현할 때 정하고, 정하면 그 단계 문서에 반영한 뒤 이 표에서 지운다. 4절의 "확인 필요"는 단계 착수 전에 Claude가 풀고 시작한다([CLAUDE.md §2](../../CLAUDE.md)).

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
| 전체 | `TestFixtures` 헬퍼("테스트 준비")가 "완료 확인" 표 뒤에 있는 구조 | 만드는 순서는 헬퍼 → 테스트다. 구조를 바꾸려면 2-1~2-5를 포함해 고친다 |
| 2-6 | `cancel` A-2·A-3의 재조회 | `insertIfAbsent`가 1행이면 방금 만든 `REQUESTED` 행이고 `id`도 알아서 재조회가 필요 없다. 0행일 때만 읽으면 취소마다 SELECT가 하나 준다. 분기가 늘어난다 |
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

## 4. 확인 필요

로드맵·설계 문서에 "확인 필요"로 남은 항목을 단계별로 모았다. 단계 착수("N-M 진행할게")를 알리면 Claude가 그 단계 행부터 푼 뒤 시작한다. 풀면 그 단계 문서에 반영하고 이 표에서 지운다.

| 종류 | 뜻 | Claude가 하는 일 |
|---|---|---|
| A | 설계 결정이 비어 있음 | 선택지와 권장안을 제시해 사용자 결정을 받고, 문서에 반영한다 |
| B | 외부 라이브러리·API·도구 동작 확인 | 공식 문서·소스·실험으로 확인한다. 외부 조회가 필요하면 사용자에게 먼저 묻는다. 기동 실험은 사용자가 구현한 뒤에 하는 것이 많아 그때 확인한다 |
| C | 문서 간 또는 문서-코드 어긋남 | 어느 쪽이 맞는지 확인해 문서를 고친다 |
| D | 구현 방식 선택(권장안 있음) | 권장안을 문서에 확정해 적는다. 쓰다 바꿔도 된다 |
| E | 이미 코드에서 정해짐 | 코드를 확인해 문서의 "확인 필요"를 확정 문장으로 바꾼다 |

파일 약칭: p1~p7은 `docs/03-roadmap/partN-*.md`다. 줄 번호는 2026-10-19 기준이라 밀리면 "확인 필요"로 찾는다. 이미 구현한 단계(1-x, 2-1~2-8)의 항목은 단계 착수가 없으므로 E·C를 먼저 문서에서 정리한다.

### 이미 구현한 단계 (문서만 정리)

| 단계 | 종류 | 항목 | 위치 | 풀 방법 |
|---|---|---|---|---|
| 1-2 | D | CI가 테스트를 실제로 돌렸는지 보는 방법(`--info` 또는 test-results 업로드)을 정하지 않음 | p1:321 | 하나 골라 PR에 기록 |
| 1-2 | B | Testcontainers 컨테이너 시작 로그 문구가 버전마다 다를 수 있음 | p1:323 | `--info` 로그로 확인 |
| 1-11 | E | GHCR용 LABEL. `docker/Dockerfile:7`에 이미 있음 | p1:2272 | 문구 삭제 |
| 1-11 | B | VM이 amd64인지, 맥 LAN 인터페이스가 `en0`인지, `sudo -l` 거부 메시지 문구 | p1:2289, 2334, 2657 | VM·맥에서 실행해 확인 |
| 2-3 | B | Toss 승인·취소·조회 API별 에러 코드 표를 대조하지 못함 | p2:1023 | 개발자센터 에러 표 확인 |
| 공통 | D | springdoc-openapi의 Boot 4 호환 버전(사용하는 단계 없음) | development-guide:110 | 사용 단계 착수 시 확인 |
| 공통 | E | 격리 수준 `READ COMMITTED` 가정. `src/main`에 격리 수준 설정이 없어 기본값을 쓴다 | 08-stock-design-evolution:27 | 확정 문장으로 교체 |
| 상태 조합 | A·C | [10-state-combinations 7절](../02-design/10-state-combinations.md) 10건(발송 가드와 `FAILED` 환불, 대사가 못 닫는 경우 등) | 10 문서 7절 | 해당 단계 착수 때 같이 푼다 |

### Part 3

| 단계 | 종류 | 항목 | 위치 | 풀 방법 |
|---|---|---|---|---|
| 3-3 | D | `existsByShopIdAndStatusIn`을 `JpaOrderRepository`에 둠 | p3:437 | 확정 |
| 3-4a | A | `payoutAll()` 반환 타입. `SettlementRunResult.paid`·`payoutFailed`를 둘지(위 API 표와 같은 건) | p3:947(1155) | 사용자 결정 |
| 3-4a | C | `payoutAttempts` +1을 `markPayoutFailed` 안에서 할지 `payoutAll`에서 할지. 901행과 979행이 다르게 읽힘 | p3:901 | 한 곳으로 정해 문서 수정 |
| 3-4a | B | Spring Data 저장소 프록시에 `@MockitoSpyBean` 스파이가 붙는지 | p3:1079 | 구현 후 실험 |
| 3-4a | D | `sumBySettlementId` native 결과를 record로 받는 방법(`::bigint` 캐스팅), `insertIfAbsent`의 초깃값(`CALCULATED`, 금액 0), `calculate` 첫 인자 `Money`, 테스트용 `Settlement` 생성 방법 | p3:918, 928, 956, 1021 | 권장안으로 확정 |
| 3-4b | A | `prod` 프로필에서 `PayoutGateway`·`MailSender` 빈이 없어 기동 실패 가능. 실제 구현 범위 또는 prod 대체 빈 | p3:980 | 사용자 결정 |
| 3-4b | D | `SETTLEMENT_PAID`의 `aggregateType` `"SETTLEMENT"`, 정산 목록·품목 목록 keyset 시그니처와 읽기 모델, 남의 가게 정산 조회는 404 `SETTLEMENT_NOT_FOUND` | p3:974(1113), 987, 988(1148) | 확정 |
| 3-5 | A | `getContact`에서 `BANNED` 회원의 `active` 값. 회원 없음은 `MEMBER_NOT_FOUND` | p3:1226 | 사용자 결정 |
| 3-5 | D | `Notification`을 native 쿼리로 구현(3-4 정산과 같은 방식), 알림 제목 숫자에 천 단위 구분 기호 없음 | p3:1238, 1292 | 확정 |
| 3-6 | A | "진행 중 환불" 상태 집합(`REQUESTED, APPROVED, PG_CANCELLED`)이 정책에 없음. 탈퇴 차단 정책 | p3:1404 | 사용자 결정 |
| 3-6 | D | settlement 저장소의 탈퇴 조건 조회 메서드 이름·시그니처 | p3:1407 | 구현하며 정함 |

### Part 4

| 단계 | 종류 | 항목 | 위치 | 풀 방법 |
|---|---|---|---|---|
| 공통 | B | Spring Data Redis 4.x에서 `StringRedisTemplate` 호출 시그니처가 표와 같은지 | p4:51 | IDE·공식 문서로 확인 |
| 4-1 | A | `rotate`가 `auth:member-sessions:{memberId}` TTL도 연장해야 하는지(규칙 T와 불일치) | p4:164 | 사용자 결정 후 인자·규칙 T 수정 |
| 4-1 | B | `DefaultRedisScript`의 `setLocation`·`setResultType`이 4.x에서 같은 이름인지 | p4:177 | 4.x API 확인 |
| 4-1 | C | Lua의 `==` 해시 비교가 규칙 T(36행)의 "isEqual 비교"와 어긋남 | p4:178 | 규칙 T에 "Lua 내부 비교는 예외" 적기 |
| 4-1 | E | sessionId 생성은 `Ids.newId()`(`common/model/Ids.java`)로 확정 | p4:189 | 문구 삭제 |
| 4-1 | D | `sid` 없는 옛 토큰은 거절, device(`User-Agent` 200자)를 Command 필드로 넘김(4-3과 같은 결정), refresh 때 회원 행이 없으면 401 후 세션 삭제 | p4:120, 200(235, 324), 208 | 권장안으로 확정 |
| 4-2 | A | 존재하는 이메일만 429가 나와 "항상 202" 정책과 충돌, 가입 여부가 노출됨 | p4:431 | 사용자 결정 |
| 4-2 | A | 비밀번호 재설정 메일 제목·본문 문구(테스트가 의존) | p4:460 | 사용자 결정 |
| 4-2 | D | 재설정 확인에서 코드는 통과했는데 회원이 없으면 400 `EMAIL_CODE_INVALID`로 통일 | p4:435 | 확정 |
| 4-3 | A | `OAuthLoginResponse` 필드 모양(token 중첩 vs 펼침). 테스트 JSON 경로가 달림 | p4:733, 746 | 사용자 결정 |
| 4-3 | B | 제공자별 authorize 파라미터·Naver `state` 요구·읽는 필드 경로, `@PathVariable` enum이 소문자 `kakao`를 받는지 | p4:645, 649, 657, 731 | 제공자 문서·테스트로 확인 |
| 4-3 | C | `FakeOAuthServer` 규격에 요청 기록(`requests`)·`failWith` 범위·Kakao id 숫자 응답이 없음, 가입 인증 메일 제목·본문이 4-2와 같은지 | p4:750, 756, 762 | 규격 절에 추가 |
| 4-3 | D | state consume을 Lua·원자 삭제로, 가입 토큰용 코드 발송 메서드 위치, 소셜 컨트롤러 이름, `providerEmail`은 제공자가 준 이메일(없으면 null) | p4:674, 715, 733, 735, 766 | 권장안으로 확정 |
| 4-4 | C | `NotificationType`에 `BANNED`가 3-5에 없다는 표현. part3:1234가 4-4에서 추가한다고 계획함 | p4:925, 1020 | 문구 정리 |
| 4-4 | D | `tokenVersion` 조회 시 회원 행 없으면 401, 검색 쿼리 시그니처·대소문자·정렬, 관리자 컨트롤러·테스트 헬퍼 이름, `BANNED` 회원은 `loginAttemptStore.reset`하지 않음 | p4:887, 908(1012, 1016), 925, 926, 945 | 권장안으로 확정 |

### Part 5

| 단계 | 종류 | 항목 | 위치 | 풀 방법 |
|---|---|---|---|---|
| 5-1 | A | 등록창의 `customerKey`와 발급 시 서버의 `customerKey`를 같게 맞추는 방법(DTO는 `authKey`만 받음) | p5:70 | 사용자 결정(회원별 고정 키 또는 DTO), Toss 문서 대조 |
| 5-1 | A | 이미 `DELETED`인 빌링키를 다시 삭제할 때의 응답(404 또는 멱등 204) | p5:180 | 사용자 결정 |
| 5-1 | D | `decrypt` 실패 예외 타입, 로그에 `paymentId`가 없을 때 칸, 빌링키 목록 정렬(`createdAt desc`) | p5:101(212), 144, 168 | 확정 |
| 5-2 | C | `SubscriptionResult`가 도메인 `ShippingAddress`를 노출함(2-2는 `ShippingAddressResult`) | p5:397 | 문서를 맞추거나 예외로 명시 |
| 5-2 | D | `OWN_SHOP_PRODUCT`의 `details`, PATCH 200 본문과 응답 필드 | p5:415, 436 | 체크아웃·`SubscriptionResult`에 맞춤 |
| 5-3 | A | 구매자의 일시정지·재개·해지(5-2)에서도 `subscription-status-changed`를 낼지 | p5:658 | 사용자 결정 |
| 5-3 | D | `OrderType` enum 전환, 회차 이력 keyset, `findDueIds` 첫 `lastId`(`new UUID(0,0)`), 알림 type 이름·문구, outbox `aggregateType`, `runDue()` 반환값, `orderName` 100자 자름, SKIPPED 사유 문자열과 `consecutiveFailures` 처리, `onOrderClosed` 회차 없음은 예외, SUSPENDED 이벤트 `reason` | p5:600, 652(696), 653, 661, 667, 669(710), 682, 684(806), 809, 689, 692(786) | 권장안으로 확정 |
| 5-4 | A | 가격 변경 이벤트의 `oldPrice`·`effectiveDate`, 특히 인하 시 즉시 적용인지 | p5:900, 966 | 사용자 결정 |
| 5-4 | D | 가격 알림 type·문구, 종료 이벤트 `reason`은 `endReason`과 같게, 판매자 목록 쿼리·클래스 이름 | p5:901(967), 912(971), 934, 935, 936 | 확정 |

### Part 6

| 단계 | 종류 | 항목 | 위치 | 풀 방법 |
|---|---|---|---|---|
| 6-1 | A | 리뷰 수정 요청을 부분 수정(null=안 바꿈)으로 볼지(규칙 E는 null=안 바꿈, `Review.edit`은 `int`) | p6:125, 181 | 사용자 결정 |
| 6-1 | A | 이미 `DELETED`인 리뷰의 재삭제·수정, 숨겨진 리뷰에 좋아요 응답 | p6:180 | 사용자 결정 |
| 6-1 | A | 평균 반올림 방식, 통계 행이 없거나 0건일 때 응답 | p6:187 | 사용자 결정 |
| 6-1 | A | `sort` 기본값·허용 밖 값 처리·좋아요순 동점 정렬 | p6:192, 281 | 사용자 결정 |
| 6-1 | A | 수정 가능 기간 30일의 경계(30일째 포함 여부) | p6:231 | 사용자 결정 |
| 6-1 | B | Jackson 3 `treeToValue` 시그니처 | p6:314 | 사용 버전으로 확인 |
| 6-1 | D | 품목 조회 쿼리 모양, `review_like`·`product_review_stat`을 `JdbcTemplate`으로, `ReviewTopicConfig` DLT, `aggregateType` `"REVIEW"` | p6:85, 120, 123, 167 | 확정 |
| 6-2 | A | 기동 시 ES가 내려가 있으면 앱 기동을 막을지(경고만 권장) | p6:376 | 사용자 결정 |
| 6-2 | B | 없는 alias로 쓰면 ES가 일반 인덱스를 자동 생성하는지, `_id` 정렬이 막혀 `productId`(keyword)를 쓰는지 | p6:375, 386 | 로컬 ES로 실험 |
| 6-2 | D | ES 클라이언트 선택(`ElasticsearchClient` vs `ElasticsearchOperations`), `SearchErrorCode` 패키지, `TestFixtures`의 `es` 접근 | p6:357, 366, 497 | 확정 |
| 6-3 | A | 재색인 실패 시 `reindex()`가 예외를 던질지 | p6:563 | 사용자 결정 |
| 6-3 | B | alias 교체 전 `refresh`가 필요한지(기본 1초) | p6:536 | ES 실험 |
| 6-3 | D | 상품 keyset 쿼리 이름·첫 `afterId`, ES 쓰기 클래스(`ProductIndexer`) | p6:525, 527 | 확정 |
| 6-4 | A | `member_purchase.purchased_at`에 넣을 시각(이벤트에 결제 시각 없음, 후보 `occurredAt`) | p6:652 | 사용자 결정 |
| 6-4 | A | "이미 산 상품 제외"가 최근 20개만인지 전체 구매 이력인지, 90일 내 20개 선택 순서 | p6:667 | 사용자 결정 |
| 6-4 | A | 탈퇴(`member-withdrawn`)가 구매 이력·캐시를 지우는 테스트를 넣을지(Part 3 의존) | p6:765 | 사용자 범위 결정 |
| 6-4 | B | Spring AI 2.0이 `base-url` 뒤에 `/v1`을 붙이는지, pgvector가 쉼표 뒤 공백을 허용하는지, 가짜 서버가 받는 `input`이 문자열인지 배열인지 | p6:616, 621, 683 | 요청 로그·pgvector 실험 |
| 6-4 | D | `JdbcTemplate` 선택, `InboxGuard`에 조회 메서드, 서비스·consumer 역할, 임베딩 `model`은 설정값, 취향 벡터 NULL이면 빈 목록 | p6:629, 637(658), 639, 662(693), 668 | 확정 |
| 6-5 | A | `period_end`에 넣을 날짜(말일 포함 권장, 3-4 정산과 동일) | p6:800, 831 | 사용자 결정 |
| 6-5 | A | 대표 리뷰 쿼리가 전체 ACTIVE인지 대상 월만인지, `source_review_count`가 프롬프트 개수인지 전체인지 | p6:801, 848 | 사용자 결정 |
| 6-5 | B | `gpt-4o-mini`가 착수 시점의 저가 모델인지, Spring AI 2.0.1 `ChatClient` 호출 형태, chat `content`가 문자열인지 배열인지 | p6:779, 793(802), 810 | 공식 문서·요청 로그 확인 |
| 6-5 | D | `review_summary` 읽기 repository, 요약 `model`은 설정값, `latestSummary` JSON 키 | p6:795, 803, 857 | 확정 |
| 6-6 | D | `InquiryAnswer` 생성 위치, `InquiryResult`·`InquiryDetailResult` 필드, 문의 목록 정렬(본인 최신순·관리자 오래된 순) | p6:885, 888(906, 926), 892(932) | 확정 |

### Part 7

| 단계 | 종류 | 항목 | 위치 | 풀 방법 |
|---|---|---|---|---|
| 7-1 | B | Logstash 재접속·버퍼 동작, `LogstashEncoder` 기본 필드 이름, Logstash 파이프라인 마운트 경로와 Kibana의 ES 주소 환경변수, Kibana 9.4 Saved Objects 메뉴 위치 | p7:62, 64, 86, 88 | 라이브러리 README·이미지 문서·화면 확인 |
| 7-1 | B | 체크아웃 → 알림 흐름이 `traceId` 하나로 묶이는지(`JobRunner`가 새 `traceId` 생성) | p7:133 | Kibana로 실측 |
| 7-1 | D | MDC `close()`가 복원이 아니라 삭제라 중첩 시 `orderId` 구간이 사라짐(가장 바깥에서만 열기), `LogMailSender`가 인증코드를 로그에 찍는 문제의 점검 방법 | p7:79, 120 | 확정 |
| 7-1 | E | Kibana 5601 호스트 publish 여부 | p7:124 | compose 작성 때 결정 |
| 7-2 | A | `pg_call_seconds`·`mail_send_seconds`·`openai_call_seconds`의 `operation`·`result` 라벨 값, 빌링 호출 포함 여부, 메일 계측 위치 | p7:207, 208 | 사용자 결정(권장: 소문자 메서드명) |
| 7-2 | A | 재고 점검 잡을 수동 실행하는 방법(05:00 대기, cron 임시 변경, 별도 트리거) | p7:259, 323 | 사용자 결정 |
| 7-2 | A | `payment_unknown_oldest_age_seconds`의 기준 컬럼을 `updated_at`(대사마다 갱신돼 알림이 안 뜰 수 있음)에서 `created_at`으로 바꿀지 | p7:323 | 사용자 결정(권장: `created_at`) |
| 7-2 | B | percentiles-histogram 속성 효과, `_total`/`_seconds` 접미사 중복, 카프카 lag 지표 이름, Gauge 수집기에 잡 락을 안 쓰는 근거, `DeadLetterPublishingRecoverer` 시그니처, Grafana 13 provider·알림 규칙 YAML, Kafka 중단 시 `DEAD` 알림 이름 | p7:160, 173, 237, 195, 205, 233, 242, 323 | `/actuator/prometheus` 출력·소스·화면 확인 |
| 7-2 | C | 기준 컬럼 `updated_at`이 2-6·2-8b 코드(`findStuckIds`)와 같은지 문서에 명시 | p7:202 | 코드 확인 후 명시 |
| 7-2 | D | prometheus·grafana 호스트 포트 publish | p7:226 | 확정 |
| 7-3 | A | DLT 목록 `limit`이 파티션별인지 전체 상한인지·최대값·내려줄 헤더 | p7:386 | 사용자 결정 |
| 7-3 | A | 환불·알림 목록 응답 항목 필드 | p7:401, 406, 493, 510 | 사용자 결정 |
| 7-3 | A | 쓰기 API의 `Idempotency-Key` 요구 여부(WHERE 상태 가드로 충분한지 포함) | p7:430 | 사용자 결정 |
| 7-3 | A | 대상 없음·상태 불일치 응답 코드, 쓰기 API 성공 응답 상태·본문, DLT 목록 응답 모양. 반품 환불이 재시도해도 계속 `FAILED`로 남는 경우를 닫는 방법 | p7:426, 439, 443, 446, 456, 460, 467, 486, 494, 502, 513 | 사용자가 한 번에 결정 |
| 7-3 | B | Toss가 같은 멱등 키의 실패 응답을 어떻게 처리하는지 | p7:403 | Toss 문서·샌드박스 |
| 7-3 | D | 목록 쿼리 메서드, DLT 코드를 `AdminDltService`로, `AdminOutboxTest`는 JDBC로 `DEAD` 만들기, 실행 로그 `action` 문자열 | p7:364, 366, 380, 367, 372 | 확정 |
| 7-4 | B | Consumer 재접속 시간(await 30초 적정성) | p7:733 | 측정 |
| 7-4 | D | e2e 패키지에서 `IntegrationTestSupport.kafka` 접근은 헬퍼 메서드로 | p7:599, 728 | 확정 |
| 7-5 | A | v1.0.0 때 `v0.7.0` 태그를 따로 찍을지(git-policy:162는 Part 7 완료 시 `v0.7.0`) | p7:765, 785 | 사용자 결정 후 git-policy와 맞춤 |
| 7-5 | A | ADR-012 미정 항목(노드 수, 상태 서비스 위치), K-1~K-5 문서 파일 이름 | p7:766, 786 | Stage 1.5 시작 전에 결정 |

### ADR·운영 문서

| 문서 | 종류 | 항목 | 위치 | 풀 방법 |
|---|---|---|---|---|
| ADR-011 | B | VM 자원(16GB·8vCPU)이 적정한지 | ADR-011:13 | 7-1 구현 뒤 실측 |
| ADR-012 | A | 세부(노드 수, 상태 서비스 위치, 도구)가 제안 상태. MinIO를 클러스터에 다시 올릴지 NAS의 것을 쓸지(문구 없음, 20~21행) | ADR-012:3, 20~21 | Stage 1.5 시작 전에 결정(7-5의 항목과 같은 건) |
| ADR-012 | B | 24GB 호스트에서 멀티 노드가 가능한지, local-path PVC에서 MinIO uid 65532용 `fsGroup`이 필요한지 | ADR-012:16, 19 | 실측·k3s에서 확인 |
| ADR-012 | D | 러너 ServiceAccount의 네임스페이스 한정 RBAC | ADR-012:22 | Role/RoleBinding 작성·검증 |
| ops | B | NAS: Container Manager 패키지명·메모리 기본값, 시놀로지 ACL이 `chown 65532`를 막는지, 프로젝트·방화벽·HDD 절전·스냅샷 메뉴, 호스트 9000 포트 충돌 | nas-minio:22, 24, 31, 35, 48, 49, 84, 85 | NAS 화면·실행으로 확인 |
| ops | B | VM: Proxmox/Ubuntu 설치 화면·LVM 체크 해제 이유, GHCR 패키지 연결·가시성, Proxmox 스토리지 추가 메뉴 | vm-setup:33, 59, 227, 232 | VM·GitHub·Proxmox 화면에서 확인 |
