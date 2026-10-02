# Part 2. 주문과 돈

> **끝나면**: 여러 가게의 상품을 예치금 + 카드(Toss)로 결제하고, 판매자가 발송하고, 구매자가 취소·반품·구매확정까지 할 수 있다. 결제 응답이 불확실해도 돈과 재고가 어긋나지 않는다.
> **인프라 추가**: 없음 (Toss는 테스트용 가짜 PG 서버로 대체, 실제 Toss 테스트 키는 수동 확인용)
> **이 Part가 프로젝트의 핵심이다.** 이력서의 "정합성" 이야기는 대부분 여기서 나온다.
> **릴리스**: `v0.2.0`

| 단계 | 제목 | 크기 |
|---|---|---|
| 2-1 | 지갑과 원장 | M |
| 2-2 | 예치금 보류 · 확정 · 해제 · 환불 | M |
| 2-3 | 장바구니 | S |
| 2-4 | 체크아웃: 예치금 전액 결제 (+ 멱등 API) | L |
| 2-5 | Toss 클라이언트와 가짜 PG 서버 | M |
| 2-6 | PG 결제 승인 (복합결제) | L |
| 2-7 | 주문 만료 잡 (+ 잡 락) | M |
| 2-8 | 결제 대사 | M |
| 2-9 | 예치금 충전 · 충전 취소 | M |
| 2-10 | 판매자 주문 관리: 발송 · 배송완료 | M |
| 2-11 | 품목 취소와 환불 | L |
| 2-12 | 반품 | S |
| 2-13 | 구매확정 (수동 · 자동) | M |

---

## 2-1. 지갑과 원장 (M)

**목표**
- 회원이 예치금 잔액과 거래 내역을 조회한다. 지갑은 처음 쓸 때 자동으로 생긴다.
- 로컬 개발용 충전 API로 잔액을 넣을 수 있다(진짜 충전은 2-9).

**왜 지금**: 체크아웃(2-4)이 예치금을 쓴다. 돈을 다루는 첫 모듈이라 **원장 모델**을 여기서 확실히 익힌다.

**새로 등장**

| 개념 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| 원장(ledger) | 모든 증감을 한 테이블에 기록. 잔액은 원장 합계의 "캐시"이고 언제든 대조할 수 있다 | [ADR-005](../adr/ADR-005-wallet-ledger.md) |
| 비관적 락 | `SELECT ... FOR UPDATE`로 지갑 행을 잠그고 원장 기록 + 잔액 변경 | 개발 가이드 §10 |
| `ON CONFLICT DO NOTHING` | 중복 INSERT를 예외 없이 무시하고 반영 행 수로 판단. 트랜잭션 안에서 unique 예외를 잡는 것보다 안전 | 개발 가이드 §10 (As-Is SUP-05) |
| `@Profile("local")` | 로컬에서만 생기는 빈. 개발용 API가 운영에 새지 않게 | |

**할 일**
1. 마이그레이션: `wallet`(balance, held, `CHECK (balance >= 0)`, `CHECK (held >= 0 AND held <= balance)`, `uk_wallet_member`), `ledger_entry`(`uk_ledger_entry_type_ref (type, ref_type, ref_id)`)
2. `WalletApi.getOrCreate(memberId)`: `INSERT ... ON CONFLICT (member_id) DO NOTHING` 후 조회
3. 원장 기록 코어(내부 메서드): 지갑 `FOR UPDATE` → 원장 INSERT `ON CONFLICT DO NOTHING` → **새로 들어갔을 때만** 잔액 변경. 중복이면 기존 결과를 그대로 성공으로 돌려준다(As-Is WAL-01)
4. `GET /api/wallet`(잔액, 보류액, 가용잔액), `GET /api/wallet/ledger`(유형·기간 필터, 커서 페이징)
5. `DevWalletController` `POST /dev/wallet/credit` — `@Profile("local")`
6. `wallet/package-info.java` 허용 의존: `common`

**완료 확인**
- [ ] 같은 회원으로 동시에 `getOrCreate` 10번 → 지갑 1개
- [ ] 같은 참조로 입금 두 번 → 잔액은 한 번만 증가, 두 번째도 성공 응답
- [ ] 입금·출금 동시 100건 → `balance = Σ ledger`, 음수 없음
- [ ] 잔액보다 큰 출금 → 422 `INSUFFICIENT_WALLET_BALANCE`
- [ ] test 프로필에서 `DevWalletController` 빈이 없다

**리뷰 때 물어볼 것**
- 잔액 컬럼과 원장을 둘 다 두는 이유는? 원장만 있으면 안 되나?
- 지갑에 낙관적 락이 아니라 비관적 락을 쓴 이유는?
- 원장 unique 제약이 없으면 어떤 사고가 날 수 있나?

---

## 2-2. 예치금 보류 · 확정 · 해제 · 환불 (M)

**목표**: 체크아웃·환불·정산이 쓸 `WalletApi`를 완성한다.

**왜 지금**: 결제가 끝나기 전까지 예치금을 "묶어두는" 장치가 필요하다. 묶어두지 않으면 같은 돈으로 주문 두 개를 동시에 결제할 수 있다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 보류(hold) | `held`를 늘려 가용잔액(`balance - held`)을 줄인다. 확정하면 실제 차감, 해제하면 원복 |

**할 일**
1. `wallet_hold` 마이그레이션(`uk_wallet_hold_order`)
2. `WalletApi`
   - `hold(memberId, orderId, amount)`: 가용잔액 부족 → 422, 같은 orderId 재호출 → 무시
   - `capture(orderId)`: HELD → CAPTURED, `balance -= a, held -= a`, 원장 `ORDER_PAYMENT`
   - `release(orderId)`: HELD → RELEASED, `held -= a` (원장 없음). HELD가 아니면 무시
   - `refund(memberId, orderId, amount, refundId)`: 원장 `REFUND`, 주문별 누적 환불 ≤ 사용액
   - `deposit(memberId, amount, type, refId)`: 정산(`SETTLEMENT`)·충전(`CHARGE`) 입금
   - 금액 0은 아무것도 하지 않는다(예치금을 안 쓰는 주문)

**완료 확인**
- [ ] 보류 → 확정 / 보류 → 해제 각각 잔액·보류액·원장이 맞다
- [ ] **가용잔액 10,000원에서 6,000원 보류 동시 2건 → 1건만 성공**
- [ ] 같은 refundId로 환불 두 번 → 한 번만
- [ ] 누적 환불이 사용액을 넘으면 거부
- [ ] 이미 확정된 보류를 해제 → 아무 일도 없음

**리뷰 때 물어볼 것**
- 보류 없이 결제 성공 시점에 바로 차감하면 무엇이 문제인가?
- 해제에는 왜 원장을 남기지 않나?

---

## 2-3. 장바구니 (S)

**목표**: 장바구니 담기·수량 변경·삭제·조회. 조회할 때 **현재** 가격·판매 상태·재고 여부를 보여준다.

**왜 지금**: 체크아웃의 입력이다. order 모듈의 첫 코드다.

**할 일**
1. `orders` schema, `cart`(`uk_cart_member`), `cart_item`(`uk_cart_item_product (cart_id, product_id)`)
2. 같은 상품을 다시 담으면 수량 합산
3. 조회: 상품 ID들을 모아 `ProductApi`로 **한 번에** 조회(가격은 장바구니에 저장하지 않는다)
4. 단종·숨김·품절 상품은 "구매 불가"로 표시
5. `order/package-info.java` 허용 의존: `common`, `product::api` (이후 단계에서 추가)

**완료 확인**
- [ ] 같은 상품 두 번 담기 → 1행, 수량 합산
- [ ] 상품 가격이 바뀌면 장바구니 조회에도 바뀐 가격
- [ ] 장바구니에 상품 20개가 있어도 상품 조회는 1번
- [ ] 다른 회원의 장바구니 항목 변경 → 404

**리뷰 때 물어볼 것**
- 장바구니에 가격을 저장하지 않는 이유는?

---

## 2-4. 체크아웃: 예치금 전액 결제 (+ 멱등 API) (L)

**목표**
- 장바구니(여러 가게 상품)로 주문서를 만든다. 서버가 가격을 확정하고, 재고를 예약하고, 예치금을 보류한다.
- **예치금으로 전액 결제**하면 그 자리에서 결제 완료(PAID)가 된다.
- 카드 결제가 필요한 금액이 있으면 결제 대기(PENDING_PAYMENT) 주문만 만든다(승인은 2-6).
- 같은 요청을 두 번 보내도 주문은 하나다.

**왜 지금**: 1-9(재고 예약)와 2-2(예치금 보류)를 처음으로 함께 쓰는 지점이다.

**새로 등장**

| 개념 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| 애그리거트 | `Order`(루트)가 `ShopOrder`·`OrderLine`을 만들고 바꾼다. 바깥에서는 루트를 통해서만 수정 | 개발 가이드 §5.2 |
| 여러 모듈을 한 트랜잭션으로 | 주문 생성 + 재고 예약 + 예치금 보류를 하나의 로컬 트랜잭션으로. 하나라도 실패하면 전부 롤백. **모놀리스라서 가능한 것**이고 Stage 3에서는 Saga가 된다 | [ADR-002](../adr/ADR-002-module-boundaries.md) |
| 서버 가격 확정 | 요청에 가격을 받지 않는다. 상품 모듈에서 조회한 가격으로 계산 | POL-17, As-Is ORD-01 |
| 멱등 API (`Idempotency-Key`) | 클라이언트가 보낸 키로 처리 결과를 저장해두고, 같은 키로 다시 오면 저장된 응답을 그대로 돌려준다 | [02-architecture §5.3](../02-design/02-architecture-stage1.md) |

**할 일**
1. **멱등 공통 장치**(common): `idempotency_key` 마이그레이션, `@Idempotent` + 인터셉터
   - 같은 키 + 같은 요청 → 저장된 응답 / 같은 키 + 다른 요청 → 422 / 처리 중 → 409
   - 키는 회원 단위로 구분, 5xx로 끝나면 키를 지워 재시도 허용
   - 1-7의 상품 등록 API에도 `@Idempotent`를 붙인다
2. 마이그레이션: `orders`, `shop_order`, `order_line`([ERD order](../02-design/03-erd.md)). `CHECK (total_amount = wallet_amount + pg_amount)`
3. **Order 애그리거트**: 가게별로 품목을 묶어 `ShopOrder`를 만들고, 금액을 계산한다(단위 테스트로 검증할 핵심)
4. `CheckoutService`
   - 입력: 장바구니 항목 ID들(또는 상품·수량), 사용할 예치금, 배송지 ID
   - `ProductApi.getForCheckout` → 판매 중인지, `ShopApi.getActiveShops` → 가게 운영 중인지, 자기 가게 상품이 아닌지 확인
   - `MemberApi.getAddress(memberId, addressId)`로 배송지 스냅샷 (order → member 의존 추가)
   - **한 트랜잭션**: 주문 저장(PENDING_PAYMENT, `expires_at = 지금 + 15분`) → `ProductApi.reserve` → `WalletApi.hold`
   - PG 금액이 0이면 같은 트랜잭션에서 `commitReservation` + `capture` + PAID + 장바구니 항목 삭제
5. `POST /api/orders/checkout` (`@Idempotent`), 응답은 [API 명세 §3.1](../02-design/07-api-spec.md)

**완료 확인**
- 단위
  - [ ] 금액 계산: 가게 2곳·품목 3개, 예치금 0 / 일부 / 전액
- 통합
  - [ ] 예치금 전액 → PAID, 재고 `sold` 증가, 원장 `ORDER_PAYMENT`, 장바구니 비워짐
  - [ ] 카드 금액 있음 → PENDING_PAYMENT, 재고 `reserved`, 예치금 `held`
  - [ ] 요청에 가격을 넣어 보내도 무시된다
  - [ ] **재고 부족 → 422 `OUT_OF_STOCK`, 주문·예약·보류 모두 없음**
  - [ ] **예치금 부족 → 422, 이미 한 재고 예약도 롤백됨**
  - [ ] 같은 `Idempotency-Key`로 두 번 → 주문 1건, 같은 응답
  - [ ] 같은 키로 동시에 10번 → 주문 1건
- 리팩터링 확인
  - [ ] Modulith verify 통과 (order → product·shop·wallet·member api만 참조)

**리뷰 때 물어볼 것**
- 주문 생성·재고 예약·예치금 보류를 한 트랜잭션에 묶을 수 있는 이유는? MSA에서는 어떻게 되나?
- Order 애그리거트가 ShopOrder·OrderLine을 직접 만들게 한 이유는?
- 멱등 키가 "처리 중"인 상태로 서버가 죽으면?

---

## 2-5. Toss 클라이언트와 가짜 PG 서버 (M)

**목표**
- Toss 결제 승인·취소·조회 API를 호출하는 클라이언트를 만든다.
- 응답을 **승인 / 명확한 실패 / 불확실** 세 가지로 분류한다.
- 테스트에서 Toss 대신 쓸 가짜 PG 서버를 만든다.

**왜 지금**: 2-6 결제 승인 전에 "외부 API는 실패가 두 종류가 아니라 세 종류"라는 것을 코드로 먼저 정리한다.

**새로 등장**

| 개념·도구 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| Toss 결제 흐름 | 결제창은 **인증**만 한다. 실제 **승인**은 우리 서버가 confirm API를 호출할 때 일어난다 | [ADR-004](../adr/ADR-004-checkout-payment-consistency.md) |
| 결과 3분류 | 2xx → 승인 / 거절 코드(4xx) → 명확한 실패 / 타임아웃·5xx·연결 끊김 → **불확실(UNKNOWN)** | 개발 가이드 §8.2 |
| `RestClient` 타임아웃 | 기본값은 무한 대기. connect·read 타임아웃을 반드시 설정 | |
| 가짜 PG 서버 | JDK 내장 `HttpServer`로 만든 테스트용 서버. 경로별로 응답·지연·연결 끊기를 설정 | 개발 가이드 §13.4 |

**할 일**
1. Toss 개발자센터에서 테스트 키 발급 → `.env` (커밋 금지)
2. 테스트 지원: `FakeHttpServer`(범용) → `FakePgServer`(Toss 응답 프리셋: 승인, 거절, 서버 오류, 지연, 끊김)
3. `TossClient`: `confirm`, `cancel`, `queryByOrderId`. 모든 요청에 `Idempotency-Key`, Basic 인증 헤더
4. `PgResult` sealed 타입(`Approved`, `Rejected(code)`, `Unknown(reason)`)으로 분류
5. `pg_call_log` 마이그레이션(payment schema)과 기록: 작업, 상태, 지연시간, 요약(카드번호 등 민감정보 제외)
6. Toss 에러 코드 문서를 보고, 4xx 중 "재시도해야 하는" 코드가 있는지 확인해 분류표에 반영

**완료 확인** (가짜 PG 서버)
- [ ] 승인 / 거절(400) / 서버 오류(500) / 지연(read 타임아웃 초과) / 연결 끊김 → 각각 기대 분류
- [ ] 요청 헤더에 `Idempotency-Key`, `Authorization`이 있다
- [ ] 응답 JSON 역직렬화(Jackson 3)
- [ ] `pg_call_log`에 지연시간이 남는다

**리뷰 때 물어볼 것**
- 타임아웃이 났는데 실패로 처리하면 어떤 사고가 나나? (As-Is PAY-01)
- Mockito로 클라이언트를 mock하지 않고 가짜 서버를 쓴 이유는?

---

## 2-6. PG 결제 승인 (복합결제) (L)

**목표**
- 결제 대기 주문을 Toss로 승인한다. 승인되면 PAID, 거절되면 결제 실패(재고·예치금 원복), 결과가 불확실하면 "결제 확인 중"(202).
- 승인 요청과 주문 만료가 겹쳐도 "만료된 주문에 승인된 결제"가 생기지 않는다.

**왜 지금**: 2-4(주문)와 2-5(Toss 클라이언트)를 연결한다. **이 프로젝트에서 가장 중요한 단계다.**

**새로 등장**

| 개념 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| 트랜잭션 밖 외부 호출 | DB 트랜잭션을 [상태 선점 → 커밋] → **외부 호출** → [결과 반영 → 커밋]으로 나눈다 | 개발 가이드 §6.2 |
| CAS 상태 전이 | `UPDATE ... SET status = 'PAYMENT_IN_PROGRESS' WHERE id = ? AND status = 'PENDING_PAYMENT'`. 반영 0건이면 이미 만료·결제된 주문 → PG를 부르지 않는다 | [상태머신 §1](../02-design/04-state-machines.md) |
| 먼저 커밋 | PG를 부르기 **전에** payment(IN_PROGRESS)를 커밋해두면, 호출 중 앱이 죽어도 "확인해야 할 결제"가 남는다 | [ADR-004](../adr/ADR-004-checkout-payment-consistency.md) |

**할 일**
1. payment 모듈: `payment` 마이그레이션(`uk_payment_purpose_reference`), `Payment` 엔티티(상태 전이표 = [상태머신 §5](../02-design/04-state-machines.md))
2. `PaymentApi.begin(...)`, `PaymentApi.applyResult(...)`
3. `ConfirmPaymentService`(order) — [시퀀스 §2](../02-design/06-sequences.md)
   - 트랜잭션 1: 금액 검증(요청 금액 = pgAmount) → 주문 CAS → payment(IN_PROGRESS) → 커밋
   - 트랜잭션 밖: `TossClient.confirm` (`Idempotency-Key = paymentId`)
   - 트랜잭션 2: 승인 → payment APPROVED + 주문 PAID + 예약 확정 + 보류 확정 + 품목 PAID + 장바구니 삭제 / 거절 → FAILED + PAYMENT_FAILED + 예약·보류 해제 + 품목 CANCELLED / 불확실 → payment UNKNOWN, 주문은 그대로
4. `POST /api/orders/{id}/payment/confirm`(`@Idempotent`), `POST /api/orders/{id}/payment/fail`(결제창에서 취소·실패 시 즉시 해제)
5. (선택) `static/toss-test.html`: Toss 테스트 결제창으로 실제 승인까지 수동 확인. 클라이언트 키는 설정에서 주입

**완료 확인** (가짜 PG 서버)
- [ ] 승인 → PAID, 재고 `sold`, 보류 CAPTURED, 원장
- [ ] 거절 → PAYMENT_FAILED, 재고·예치금 원복, **실패 이력이 남음** (As-Is ORD-03)
- [ ] 타임아웃 → 202, payment UNKNOWN, 주문 PAYMENT_IN_PROGRESS
- [ ] **만료된 주문 confirm → 409, 가짜 PG 서버에 들어온 요청 0건**
- [ ] 같은 주문 confirm 동시 2건 → PG 요청 1건
- [ ] 금액 위변조 → 422 `AMOUNT_MISMATCH`, PG 요청 0건

**리뷰 때 물어볼 것**
- 트랜잭션 1과 2 사이에서 앱이 죽으면 무엇이 남고, 누가 이어서 처리하나? (→ 2-8)
- `@Transactional` 메서드 안에서 Toss를 부르면 정확히 어떤 문제가 생기나? (As-Is ORD-02)
- 만료와 승인이 경쟁할 때 "만료된 주문에 승인된 결제"가 왜 생길 수 없나?
- 2PC를 안 쓴 이유는?

---

## 2-7. 주문 만료 잡 (+ 잡 락) (M)

**목표**: 결제창에서 이탈한 주문(15분 경과)을 자동으로 만료시키고 재고·예치금을 되돌린다. 서버가 여러 대여도 잡은 한 곳에서만 돈다.

**왜 지금**: 2-4부터 결제 대기 주문이 생기는데, 아무도 정리하지 않으면 재고가 영원히 묶인다(As-Is CAT-01).

**새로 등장**

| 개념 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| 스케줄 작업 | `@Scheduled`로 주기 실행. **서버마다** 실행된다는 점에 주의 | |
| 잡 락 (세션 advisory lock) | 전용 커넥션에서 `pg_try_advisory_lock` → 작업 → `pg_advisory_unlock`. 다른 서버는 건너뛴다. 가게 Saga에서 쓴 `pg_advisory_xact_lock`은 첫 커밋에 풀려서 여기선 못 쓴다 | [02-architecture §5.4](../02-design/02-architecture-stage1.md) |
| `Clock` 주입 | 현재 시각을 `Clock` 빈으로 받아서, 테스트에서 "15분 뒤"를 흉내 낸다 | |

**할 일**
1. common: `JobLock`, 잡 실행 래퍼(새 traceId, `job` 이름, 처리 건수·소요 시간 로그)
2. `Clock` 빈, 시간을 쓰는 코드는 모두 `Clock`을 사용하도록 정리(1-4 JWT 포함)
3. 만료 잡(1분): `PENDING_PAYMENT AND expires_at < now()` 100건씩 → **주문별 트랜잭션**에서 CAS로 EXPIRED → 예약 만료 → 보류 해제 → 품목 CANCELLED
4. 인덱스 `orders(status, expires_at)`

**완료 확인**
- [ ] 15분 경과 → EXPIRED, 재고·예치금 원복
- [ ] **PAYMENT_IN_PROGRESS 주문은 만료되지 않는다**
- [ ] 잡을 두 스레드에서 동시에 실행 → 한쪽만 실행
- [ ] 잡에서 예외가 나도 락이 풀린다 (다음 실행 정상)
- [ ] 한 주문 처리 실패가 나머지 주문 처리를 막지 않는다

**리뷰 때 물어볼 것**
- 트랜잭션 레벨 advisory lock과 세션 레벨 advisory lock의 차이는?
- 잡 락이 있는데도 처리 자체를 멱등하게(CAS) 만든 이유는?
- 왜 100건을 한 트랜잭션이 아니라 건별 트랜잭션으로 처리하나?

---

## 2-8. 결제 대사 (M)

**목표**: 결과가 불확실(UNKNOWN)했던 결제를 Toss에 조회해서 확정하고, 주문을 완료하거나 실패 처리한다. 승인됐는데 주문을 완료할 수 없으면 승인을 취소한다.

**왜 지금**: 2-6에서 생긴 "결제 확인 중" 주문을 끝내는 단계다. 이것으로 "PG 승인은 정확히 한 번 반영된다"(INV-02)가 완성된다.

**새로 등장**

| 개념 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| 대사(reconciliation) | 우리 기록과 외부(PG) 기록을 맞춰보고 차이를 해결하는 작업 | [시퀀스 §2 대사](../02-design/06-sequences.md) |
| 주문이 주도하는 대사 | 주문 모듈이 결제 모듈에게 "이 주문 결제 어떻게 됐어?"라고 묻는다. 이벤트가 필요 없다 | [ADR-004](../adr/ADR-004-checkout-payment-consistency.md) |
| 백오프 | 실패할수록 다음 시도까지 간격을 늘린다(10초, 30초, 1분, 5분 … 최대 24시간) | |

**할 일**
1. `PaymentApi.reconcile(orderId)`: UNKNOWN이면 `TossClient.queryByOrderId` → APPROVED / FAILED / STILL_UNKNOWN(백오프 갱신)
2. 주문 대사 잡(1분, 잡 락): PAYMENT_IN_PROGRESS 주문 중 재확인 시각이 된 것 → reconcile → 결과 반영(2-6의 트랜잭션 2 재사용)
3. 복구: payment는 APPROVED인데 주문이 5분 넘게 PAYMENT_IN_PROGRESS → 완료 재시도 → 그래도 안 되면 `TossClient.cancel`(전액, `Idempotency-Key = orderId`) + 실패 처리
4. payment 결과 반영도 CAS로 한다: 원래 요청 스레드의 늦은 응답과 대사가 동시에 결과를 쓰려 해도 한쪽만 반영

**완료 확인** (가짜 PG 서버)
- [ ] 승인 타임아웃 → 대사 → 조회 결과 승인 → 주문 PAID
- [ ] 승인 타임아웃 → 대사 → 조회 결과 미승인 → 주문 PAYMENT_FAILED, 재고 원복
- [ ] 조회도 실패 → 다음 재확인 시각이 백오프로 늘어남
- [ ] 늦은 응답과 대사가 동시에 결과 반영 → 한 번만 반영
- [ ] 승인 후 완료 불가 상황을 흉내 냄 → PG 취소 요청 1건

**리뷰 때 물어볼 것**
- 외부 결제가 타임아웃 나면 어떻게 처리하나? (면접 단골 — 이 단계의 테스트가 답)
- 타임아웃 즉시 PG에 취소를 보내지 않고 조회부터 하는 이유는?
- 대사가 영영 확정을 못 하면? (Part 7 운영 API·알림 예고)

---

## 2-9. 예치금 충전 · 충전 취소 (M)

**목표**: Toss로 예치금을 충전하고, 쓰지 않은 충전은 취소한다. 개발용 충전 API는 local 전용으로 남는다.

**왜 지금**: 2-6·2-8에서 만든 결제 흐름을 "주문"이 아닌 "충전"에 재사용한다. 같은 패턴이 반복되는지 확인하는 단계다.

**할 일**
1. `wallet_charge` 마이그레이션(payment schema), `POST /api/wallet-charges`, `/confirm`, `/cancel`
2. 충전 승인: 2-6과 같은 3단 구조. 승인 시 payment APPROVED + charge CHARGED + `WalletApi.deposit(CHARGE, chargeId)`가 한 트랜잭션
3. 충전 대사: payment 자체 잡이 UNKNOWN 충전을 조회해 승인 시 wallet에 반영(payment → wallet 방향)
4. 충전 취소: 충전액 전체가 가용잔액에 있을 때만. **잔액 선차감(원장 `CHARGE_CANCEL`) 커밋 → Toss 취소 → 실패 시 원장 복원 / 불확실 시 대사** (As-Is PAY-04)

**완료 확인**
- [ ] 충전 → 잔액 증가, 원장 1건
- [ ] 충전 confirm 타임아웃 → 대사 → 잔액 반영
- [ ] 충전 후 일부 사용 → 취소 거부
- [ ] 취소 중 Toss 실패 → 잔액 복원

**리뷰 때 물어볼 것**
- 취소할 때 잔액을 먼저 빼는 이유는? Toss 취소를 먼저 하면 어떤 경합이 생기나?

---

## 2-10. 판매자 주문 관리: 발송 · 배송완료 (M)

**목표**: 판매자가 자기 가게의 주문을 상태별로 보고, 송장번호를 입력해 발송하고, 배송완료로 바꾼다.

**왜 지금**: 결제 다음 단계다. 원 프로젝트는 가게별로 주문을 볼 수 없었다(`must.md` #1·2, As-Is ORD-11).

**할 일**
1. `GET /api/shops/{shopId}/orders`(상태·기간 필터, 커서), 상세
2. `POST .../ship` `{carrier, trackingNumber}`: 가게주문 PAID → SHIPPED, 취소되지 않은 품목 PAID → SHIPPED. **진행 중 환불이 있으면 409** (2-11 이후 의미가 생김, 지금은 검사 자리만)
3. `POST .../deliver`: SHIPPED → DELIVERED, `delivered_at` 기록
4. 상태 동기화(가게주문 ↔ 품목)는 `Order` 애그리거트 메서드가 책임진다
5. 인덱스 `shop_order(shop_id, status, created_at)`

**완료 확인**
- [ ] 가게주문·품목 상태 전이 허용·금지 전체
- [ ] 다른 가게 주문 발송 → 403
- [ ] 판매자 주문 목록이 다른 가게 주문을 포함하지 않는다

**리뷰 때 물어볼 것**
- 가게주문 상태와 품목 상태가 어긋나지 않게 누가 책임지나?

---

## 2-11. 품목 취소와 환불 (L)
> PR을 둘로 나눈다: **2-11a** 취소·환불, **2-11b** 복구 잡

**목표**
- 발송 전 품목을 하나만 취소하면 **그 품목 금액만** 환불된다(카드분은 PG 부분취소, 나머지는 예치금 복원). 재고도 돌아온다.
- 중간에 실패해도 복구 잡이 이어서 끝낸다.

**왜 지금**: 원 프로젝트의 가장 심각한 결함 중 하나(품목 환불인데 주문 전액 환불, As-Is ORD-04)를 해결한다.

**새로 등장**

| 개념 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| 환불 오케스트레이션 | Refund가 단계별 상태(APPROVED → PG_CANCELLED → COMPLETED)를 가지고 진행. 각 단계는 멱등 | [상태머신 §4](../02-design/04-state-machines.md), [시퀀스 §4](../02-design/06-sequences.md) |
| 금액 배분 | `PG 환불 = min(환불액, PG 승인액 - PG 누적 취소액)`, 나머지는 예치금 | POL-08 |
| 복구 잡 | 중간 상태로 멈춘 환불을 찾아 남은 단계를 다시 실행 | |

**할 일 (2-11a)**
1. `refund` 마이그레이션, `Refund` 애그리거트, `payment_cancel` 마이그레이션
2. `PaymentApi.cancel(paymentId, amount, idempotencyKey)` → Toss 부분취소 (`Idempotency-Key = refundId`)
3. `CancelOrderLineService`
   - 트랜잭션 1: 품목 PAID + 가게주문 미발송 확인 → refund(APPROVED) + 금액 배분 → 커밋
   - 트랜잭션 밖: PG 금액이 있으면 부분취소
   - 트랜잭션 2: 예치금 환불 + 재고 복구(`restore`, refundId) + `refunded_amount` 증가 + 품목 CANCELLED + refund COMPLETED + 가게주문 상태 재계산
   - PG 불확실 → 202, PG 명확한 실패 → refund FAILED + 502
4. `POST /api/orders/{id}/lines/{lineId}/cancel` (`@Idempotent`)
5. DB CHECK: `refunded_amount <= line_amount`, `cancelled_amount <= amount`

**할 일 (2-11b)**
1. 환불 복구 잡: PG_CANCELLED로 5분 넘게 멈춘 환불 → 트랜잭션 2 재실행
2. 취소 불확실(payment_cancel UNKNOWN) → 복구 잡이 `PaymentApi.reconcileCancel(refundId)`로 확인 → 성공이면 이어서 처리

**완료 확인**
- [ ] **품목 3개 중 1개 취소 → 그 품목 금액만 환불, 나머지 품목은 그대로**
- [ ] 복합결제 배분: 카드 30,000 + 예치금 10,000 주문에서 35,000 품목 취소 → 카드 30,000 + 예치금 5,000
- [ ] 품목 두 개를 차례로 취소 → 누적 환불액 정확, PG 누적 취소 ≤ 승인액
- [ ] 같은 품목 동시 취소 2건 → 환불 1건
- [ ] 발송된 품목 취소 → 409
- [ ] 마지막 품목 취소 → 가게주문 CANCELLED
- [ ] (2-11b) 트랜잭션 2 직전에 실패를 주입 → 복구 잡 → COMPLETED, 예치금·재고 한 번만 반영

**리뷰 때 물어볼 것**
- 부분 환불을 어떻게 구현했나? 금액을 어떻게 나눴나?
- PG 취소는 성공했는데 DB 반영이 실패하면?
- 각 단계를 멱등하게 만든 키는 각각 무엇인가?

---

## 2-12. 반품 (S)

**목표**: 배송완료된 품목에 반품을 요청하고, 판매자가 승인하면 2-11과 같은 방식으로 환불한다. 거절하면 원래대로 돌아간다.

**할 일**
1. `POST .../lines/{lineId}/return-requests`: DELIVERED만 → 품목 RETURN_REQUESTED, refund REQUESTED
2. 판매자 `POST /api/shops/{shopId}/refunds/{refundId}/approve` → 2-11 흐름 재사용 → 품목 RETURNED
3. `.../reject` `{reason}` → refund REJECTED, 품목 DELIVERED로 복귀, 재요청 불가 [OPEN-03]

**완료 확인**
- [ ] 요청 → 승인 → 환불 → RETURNED
- [ ] 요청 → 거절 → DELIVERED, 다시 요청 → 거부
- [ ] 배송 중(SHIPPED) 품목 반품 요청 → 409
- [ ] 다른 가게 판매자가 승인 → 403

**리뷰 때 물어볼 것**
- 2-11 코드를 어떻게 재사용했나? 복사했다면 왜?

---

## 2-13. 구매확정 (수동 · 자동) (M)

**목표**: 구매자가 구매확정하거나, 배송완료 후 7일이 지나면 자동으로 확정된다. 확정된 품목은 이후 정산(3-4)과 리뷰(6-1)의 대상이 된다.

**할 일**
1. `POST .../lines/{lineId}/confirm`: DELIVERED만
2. 자동 확정 잡(1시간, 잡 락): `delivered_at + 7일 < now()`, 반품 요청 중 품목 제외, 건별 트랜잭션, keyset 순회
3. 가게주문의 모든 품목이 종결(CONFIRMED·RETURNED·CANCELLED)되면 COMPLETED
4. 확정 금액 = `line_amount - refunded_amount` (정산 이벤트는 3-3에서 붙인다)

**완료 확인**
- [ ] 7일 경과 → 자동 확정 (`Clock` 제어)
- [ ] 반품 요청 중인 품목은 자동 확정되지 않는다
- [ ] 수동 확정과 자동 확정이 동시에 → 한 번만 확정
- [ ] 마지막 품목 확정 → 가게주문 COMPLETED

**리뷰 때 물어볼 것**
- 원 프로젝트의 스케줄러(As-Is ORD-07)와 무엇이 다른가?

---

## Part 2 완료
- [ ] develop → main, 태그 `v0.2.0`. Release 노트에 "결제 불확실성 처리"와 "품목 단위 환불" 요약
- [ ] Part 3 문서 다듬기
