# 06. 핵심 시퀀스

> 2026-10-02: 예치금 제거 반영(체크아웃·만료·환불·구독·정산에서 wallet 삭제, §8 충전 → 상품 이미지 업로드로 교체)

표기: 색 블록 = 하나의 DB 트랜잭션. 외부 호출(PG, 은행, 메일, 객체 저장소)은 항상 트랜잭션 밖이다.

## 1. 체크아웃 (S1)

```mermaid
sequenceDiagram
  autonumber
  participant C as Client
  participant O as order
  participant PR as product
  participant SH as shop
  C->>O: POST /api/orders/checkout (Idempotency-Key, cartItemIds, addressId)
  O->>PR: getPurchasable(productIds) — 가격·상태·shopId (판매 중이 아니면 예외)
  O->>SH: requireActiveShops(shopIds) (운영 중이 아니면 예외)
  O->>O: 검증 (ON_SALE, 가게 ACTIVE, 자기 가게 상품 아님)<br/>총액 = Σ(서버 가격 × 수량) = PG 결제 금액
  rect rgba(80,140,255,0.12)
    O->>O: orders(PENDING_PAYMENT, expires_at=+15m), shop_order, order_line(PENDING) 저장
    O->>PR: reserve(orderId, items, expiresAt) — 조건부 UPDATE, 상품 ID 정렬 순서로(데드락 방지)
  end
  O-->>C: 201 {orderId, pgOrderId, totalAmount, expiresAt, status}
```
- 재고 부족이면 트랜잭션 전체(주문 저장 포함)가 롤백된다(모놀리스 로컬 트랜잭션의 이점). Stage 3에서는 이 블록이 Saga로 바뀐다.
- 여러 상품을 예약할 때 **상품 ID 정렬 순서로 UPDATE**해 교차 주문 간 데드락을 막는다.
- 성능 관점: 인기상품 `stock` 행이 핫스팟이 된다 → Stage 2-5 실험 대상.

## 2. 결제 승인 — 정상 / 실패 / 불확실 (S3)

```mermaid
sequenceDiagram
  autonumber
  participant C as Client
  participant T as Toss 결제창
  participant O as order
  participant P as payment
  participant PG as Toss API
  C->>T: pgOrderId, totalAmount로 결제 인증
  T-->>C: successUrl?paymentKey&orderId&amount
  C->>O: POST /api/orders/{orderId}/payment/confirm (Idempotency-Key, paymentKey, amount)
  rect rgba(80,140,255,0.12)
    O->>O: CAS PENDING_PAYMENT → PAYMENT_IN_PROGRESS (실패 시 409: 만료·중복)
    O->>P: begin(orderId, amount, paymentKey) — payment(IN_PROGRESS) 생성, order_id unique
  end
  P->>PG: POST /v1/payments/confirm (Idempotency-Key = paymentId), timeout 10s
  alt 승인
    PG-->>P: DONE
    rect rgba(80,200,120,0.15)
      P->>P: APPROVED
      O->>O: PAID, 예약 COMMITTED, 보류 CAPTURED, 품목 PAID, outbox(order-paid)
    end
    O-->>C: 200 PAID
  else 명확한 실패 (4xx, 거절)
    rect rgba(255,120,80,0.12)
      P->>P: FAILED
      O->>O: PAYMENT_FAILED, 예약 RELEASED, 보류 RELEASED
    end
    O-->>C: 402 PAYMENT_FAILED
  else 타임아웃·5xx
    P->>P: UNKNOWN
    O-->>C: 202 결제 확인 중 (클라이언트는 주문 상태 폴링)
  end
```

### 대사 (reconciliation) — 주문 모듈이 주도
```mermaid
sequenceDiagram
  autonumber
  participant J as 주문 대사 잡 (1분, advisory lock)
  participant O as order
  participant P as payment
  participant PG as Toss API
  J->>O: PAYMENT_IN_PROGRESS 주문 중 결제가 UNKNOWN이고 재확인 시각이 된 것
  loop 주문별
    O->>P: reconcile(orderId)
    P->>PG: GET /v1/payments/orders/{pgOrderId}
    alt 승인됨
      P->>P: UNKNOWN → APPROVED
      P-->>O: APPROVED
      rect rgba(80,200,120,0.15)
        O->>O: PAYMENT_IN_PROGRESS → PAID, 예약·보류 확정 (이미 PAID면 무시)
      end
      opt 완료 불가 (예: 데이터 이상)
        O->>P: cancel(full, idempotencyKey=orderId) → PG 전액취소 + 알림
      end
    else 미승인
      P->>P: UNKNOWN → FAILED
      P-->>O: FAILED
      O->>O: PAYMENT_IN_PROGRESS → PAYMENT_FAILED, 예약·보류 해제
    else 조회도 실패
      P->>P: reconcile_attempts++
      P-->>O: STILL_UNKNOWN (다음 주기에 재시도)
    end
  end
```
- 결과를 이벤트로 알리지 않고 **주문이 결제에게 물어본다**(order → payment는 원래 허용된 의존 방향). Kafka 없이 동작하고, 누가 흐름을 책임지는지가 분명하다.
- 추가 점검: `payment.APPROVED AND order.status = PAYMENT_IN_PROGRESS`가 5분 넘게 지속되면 완료를 재시도한다(승인 직후 로컬 완료 트랜잭션이 실패한 경우).

## 3. 주문 만료 (S2)
```mermaid
sequenceDiagram
  participant J as 만료 잡 (1분)
  participant O as order
  participant PR as product
  J->>O: PENDING_PAYMENT AND expires_at < now() LIMIT 100
  loop 주문별 트랜잭션
    rect rgba(80,140,255,0.12)
      O->>O: CAS PENDING_PAYMENT → EXPIRED (0건이면 skip: 결제 진행 중)
      O->>PR: expire(orderId) — reserved → available
      O->>O: 품목 CANCELLED + outbox(order-expired)
    end
  end
```

## 4. 품목 취소 (S4) — 발송 전
```mermaid
sequenceDiagram
  autonumber
  participant C as Client
  participant O as order
  participant P as payment
  participant PG as Toss API
  participant PR as product
  C->>O: POST /api/orders/{orderId}/lines/{lineId}/cancel (Idempotency-Key)
  rect rgba(80,140,255,0.12)
    O->>O: 검증 (본인, 품목 PAID, 가게주문 미발송) — 주문 행 잠금
    O->>O: refund(APPROVED, amount = 품목 금액) 생성
  end
  opt 항상 (전액 PG 부분취소, POL-08)
    O->>P: cancel(orderId, amount, idempotencyKey=refundId)
    P->>PG: POST /v1/payments/{paymentKey}/cancel (cancelAmount, Idempotency-Key=refundId)
    alt 성공
      P->>P: payment_cancel DONE, cancelled_amount 증가
      O->>O: refund → PG_CANCELLED
    else 불확실
      P->>P: payment_cancel UNKNOWN → 환불 복구 잡이 같은 Idempotency-Key로 취소를 다시 호출
      O-->>C: 202 처리 중
    else 실패
      O->>O: refund → FAILED + 운영 알림
      O-->>C: 502
    end
  end
  rect rgba(80,200,120,0.15)
    O->>PR: restore(orderId, productId, qty, refId=refundId) — sold → available
    O->>O: refunded_amount 증가, 품목 CANCELLED, refund COMPLETED, 가게주문 상태 재계산, outbox(order-line-refunded)
  end
  O-->>C: 200
```
- 반품(S4')은 `REQUESTED` → 판매자 승인 API → 이후 동일. 판매자 거절 시 품목 DELIVERED로 복귀.
- PG 취소가 성공하고 마지막 트랜잭션이 실패해도 refund는 `PG_CANCELLED`로 남는다 → 복구 잡이 마지막 단계를 재실행한다(각 단계 멱등).

## 5. 구독 회차 (S5)
```mermaid
sequenceDiagram
  autonumber
  participant J as 회차 잡 (매일 06:00)
  participant O as order
  participant PR as product
  participant P as payment
  participant PG as Toss 빌링 API
  J->>O: ACTIVE AND next_run_date <= today (keyset 페이징)
  loop 구독별
    rect rgba(80,140,255,0.12)
      O->>O: cycle(subscription_id, cycle_date) INSERT — UK 충돌이면 오늘 이미 처리됨 → 건너뜀
      O->>O: 적용가 결정 (pending_effective_date <= run_date면 인상가)
      O->>O: 주문(SUBSCRIPTION) 생성 + 재고 예약
    end
    alt 재고 부족
      O->>O: cycle SKIPPED + 알림, next_run_date 다음 주기
    else
      O->>P: billingCharge(orderId, totalAmount, billingKeyId)
      P->>PG: POST /v1/billing/{billingKey} (Idempotency-Key)
      alt 성공
        O->>O: 주문 PAID, cycle PAID, failures=0, next_run_date 갱신
      else 실패
        O->>O: 주문 PAYMENT_FAILED, cycle FAILED, failures++
        alt failures >= 3
          O->>O: 구독 SUSPENDED + outbox(subscription-status-changed)
        else
          O->>O: next_run_date = 내일 (같은 회차 재시도)
        end
      else 불확실
        O->>O: 주문은 PAYMENT_IN_PROGRESS → 주문 대사 잡이 이어서 처리
      end
    end
  end
```

## 6. 정산 (S6)
```mermaid
sequenceDiagram
  autonumber
  participant K as Kafka
  participant S as settlement
  participant J as 정산 잡 (매월 5일)
  participant B as 지급 Mock (PayoutGateway)
  K->>S: order-line-confirmed → settlement_item INSERT (order_line_id UK, 중복 무시)
  J->>S: 대상 가게 ID 목록 (미정산 item 있는 가게, shop_id > :last ORDER BY shop_id LIMIT 500)
  loop 가게별
    rect rgba(80,140,255,0.12)
      S->>S: settlement INSERT (UK shop_id, period_start → 재실행 시 충돌 = 이미 처리)
      S->>S: UPDATE settlement_item SET settlement_id WHERE shop_id AND 기간 AND settlement_id IS NULL
      S->>S: 합계·수수료·정산액 계산 (실제 갱신된 행 기준)
    end
    S->>B: payout(settlementId, shopId, net) — 트랜잭션 밖, 멱등 키 = settlementId
    S->>S: PAID(payout_reference) / 실패 시 PAYOUT_FAILED (다음 실행에서 재시도)
  end
```
- As-Is BAT-02(PENDING 조건 offset 페이징 중 상태 변경 → 누락)를 keyset + "item에 정산 ID 할당" 방식으로 제거.
- 정산과 지급을 분리해 지급 실패가 정산 계산을 되돌리지 않게 한다.

## 7. 가게 개설·폐업 (S7)
```mermaid
sequenceDiagram
  autonumber
  participant C as Client
  participant SH as shop
  participant O as order (폐업 조건 SPI 구현)
  participant K as Kafka
  participant M as member
  participant PR as product
  C->>SH: POST /api/shops
  rect rgba(80,140,255,0.12)
    SH->>SH: shop ACTIVE, outbox(shop-opened)
  end
  K->>M: shop-opened → role = SELLER (멱등)

  C->>SH: DELETE /api/shops/{id}
  rect rgba(80,140,255,0.12)
    SH->>SH: 소유자 확인
    SH->>O: ShopClosePrecondition.check(shopId) — 진행 중 가게주문·활성 구독 없음
    SH->>SH: CLOSED, outbox(shop-closed)
  end
  K->>PR: shop-closed → 상품 일괄 DISCONTINUED + 상품별 product-discontinued
  K->>M: shop-closed → shop API로 활성 가게 수 재조회 → 0이면 SELLER 회수
```
- **의존 역전**: 폐업 조건은 order의 데이터인데 shop → order 의존은 순환을 만든다. shop이 `ShopClosePrecondition` 인터페이스를 `shop.api`에 정의하고 order가 구현한다(order → shop 방향 유지). 회원 탈퇴 조건(`WithdrawalPrecondition`, order·settlement가 구현)도 같은 방식이다.
- 폐업 직후 상품 단종 이벤트가 처리되기 전에 체크아웃이 들어와도, 체크아웃이 가게 ACTIVE를 확인하므로 주문이 생기지 않는다.

## 8. 상품 이미지 업로드 (presigned URL)
```mermaid
sequenceDiagram
  autonumber
  participant C as Client (판매자)
  participant PR as product
  participant S3 as MinIO (S3 호환)
  C->>PR: POST /api/shops/{shopId}/products/{id}/images/presigned-url (contentType, contentLength)
  PR->>PR: 소유자·상품 상태·이미지 개수 확인, objectKey = products/{productId}/{uuid}.{ext}
  PR-->>C: {uploadUrl(PUT, 10분), objectKey}
  C->>S3: PUT uploadUrl (파일 바이트) — 앱 서버를 거치지 않는다
  C->>PR: POST /api/shops/{shopId}/products/{id}/images (objectKey)
  PR->>S3: HEAD objectKey — 실제로 올라왔는지, 크기·타입 확인
  rect rgba(80,140,255,0.12)
    PR->>PR: product_image INSERT (sort_order 다음 번호), 첫 이미지면 thumbnail_key 설정
  end
  PR-->>C: 201 {imageId, url}
```
- 파일이 앱 서버를 거치지 않아 서버 메모리·대역폭을 쓰지 않는다. 대신 "올렸는데 등록 안 한" 객체가 남을 수 있다(정리는 Stage 1 범위 밖, 한계로 기록).

## 9. 인증: 토큰 갱신 · 즉시 무효화
```mermaid
sequenceDiagram
  participant C as Client
  participant F as 인증 필터
  participant R as Redis
  participant M as member
  C->>F: Bearer access (memberId, role, tv=3)
  F->>F: 서명·만료 검증
  F->>R: GET auth:token-version:{memberId}
  alt tv 불일치 (제재·탈퇴·전체 로그아웃)
    F-->>C: 401 UNAUTHORIZED
  end
  C->>M: POST /api/auth/refresh (refresh)
  M->>R: 세션 조회, refreshHash 비교
  alt 이미 사용된 refresh (재사용)
    M->>R: 같은 familyId 세션 전체 폐기
    M-->>C: 401 REFRESH_REUSED
  else 정상
    M->>R: 새 refresh로 교체 (회전)
    M-->>C: 새 access + refresh
  end
```
