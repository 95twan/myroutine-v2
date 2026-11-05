# 운영 런북

> 버전 0.1 · 2026-11-05 · 6-2 알림 6개와 6-3 운영 API의 대응표를 처음 적었다

알림이 울렸을 때 "무슨 뜻인지 → 어디서 보는지 → 무엇을 누르는지"를 한 곳에 모은 문서다. 알림 규칙은 [로드맵 6-2](03-roadmap/part6-ops-wrapup.md), 운영 API는 [로드맵 6-3](03-roadmap/part6-ops-wrapup.md), 로그 검색은 6-1에 있다.

## 1. 시작하기 전에

### 1.1 관리자 권한

운영 API는 모두 `/admin/api/**`이고 ADMIN 계정만 호출할 수 있다. 일반 회원은 403, 토큰이 없으면 401이다. ADMIN 계정이 없으면 [vm-setup.md 8-1](ops/vm-setup.md)로 만든다. 프론트 없이 직접 호출해야 하면 4절을 본다.

### 1.2 화면 주소

| 화면 | 주소 | 비고 |
|---|---|---|
| Grafana | `http://VM주소:3000` | 사용자 `admin`, 비밀번호는 `.env`의 `GRAFANA_ADMIN_PASSWORD`. 대시보드는 Dashboards → MyRoutine, 알림은 Alerting → Alert rules |
| Kibana | `http://VM주소:5601` | Discover의 데이터 뷰 `myroutine-logs`. 저장 검색은 6-1 5)에서 가져온다 |

### 1.3 운영 API가 남기는 기록

쓰기 API는 호출할 때마다 로그 한 줄을 남긴다. Kibana 검색창에 `message : "admin action"`을 넣으면 누가 무엇을 눌렀는지 볼 수 있다.

```
admin action=refund-retry target=<refundId> by=<관리자 memberId>
```

| action | 대상(target) |
|---|---|
| `outbox-retry` | outbox 행 id |
| `dlt-replay` | `{topic}/{partition}/{offset}` |
| `payment-reconcile` | orderId |
| `refund-retry`, `refund-close` | refundId |
| `notification-retry` | notificationId |
| `settlement-run` | period(`yyyy-MM`) |

## 2. 알림별 대응

| 알림 | 대시보드 | 이 절 |
|---|---|---|
| DLT 적재 | Kafka·Outbox | 2.1 |
| Outbox 지연 | Kafka·Outbox | 2.2 |
| Outbox DEAD | Kafka·Outbox | 2.3 |
| 결제 확인 지연 | 결제·정합성 | 2.4 |
| 정체 Saga | 결제·정합성 | 2.5 |
| 재고 불일치 | 결제·정합성 | 2.6 |

### 2.1 DLT 적재

- **의미**: Kafka consumer가 이벤트 처리를 세 번 시도하고도 실패해서 실패 보관 토픽(`{원본토픽}.dlt`)에 메시지를 넘겼다. 그 이벤트의 후속 처리(알림, 정산 적재 등)가 일어나지 않은 상태다.
- **볼 곳**
  - Grafana 대시보드 Kafka·Outbox의 "DLT 적재(5분)".
  - 어느 토픽인지: `GET /admin/api/dlt` → `[{topic, recordCount}]`.
  - 기록 내용: `GET /admin/api/dlt/{topic}?limit=50` → `[{partition, offset, key, value, timestamp, exception}]`. `exception`이 실패 사유 앞 500자다. `timestamp`는 DLT에 들어간 시각이다.
  - 로그: Kibana "ERROR만"에서 시각을 DLT의 `timestamp` 부근으로 좁힌다. 이벤트 봉투 JSON의 `traceId`를 "traceId로 흐름 보기"에 넣으면 원래 요청부터 실패까지가 이어서 보인다.
- **조치**
  1. `exception`을 보고 원인을 고친다(데이터 문제면 데이터를, 코드 버그면 배포로).
  2. 고친 뒤 목록에서 본 `(partition, offset)`으로 재처리한다.
     `POST /admin/api/dlt/{topic}/{partition}/{offset}/replay` → 204
  3. 같은 이벤트를 두 번 replay해도 처리 결과는 한 번이다(consumer가 `InboxGuard`로 이미 처리한 `eventId`를 건너뛴다).
- **확인**: 재처리가 또 실패했는지는 같은 `key`의 새 기록이 DLT에 생겼는지로 본다. DLT의 기록은 지워지지 않으므로 `recordCount`는 줄지 않는다.

### 2.2 Outbox 지연

- **의미**: 보낼 이벤트(`READY`)가 60초 넘게 밀려 있는 상태가 2분 이어졌다. 보통 Kafka에 연결이 안 되어 릴레이가 발행에 실패하고 있다.
- **볼 곳**
  - Grafana Kafka·Outbox의 "Outbox READY", "Outbox 가장 오래된 행".
  - Kibana: `message : "Failed to send outbox event"`. 이 로그가 쌓이면 발행 실패다.
  - Kafka 자체: `docker ps`로 kafka 컨테이너가 떠 있는지, 앱 로그에 `NetworkClient` WARN이 반복되는지(Kibana에서는 `logger_name : *myroutine*`로 앱 로그만 거른다).
- **조치**
  1. Kafka가 죽어 있으면 되살린다. 복구되면 릴레이(1초 주기)가 밀린 이벤트를 만든 순서대로 보내서 알림이 저절로 풀린다. 눌러야 할 운영 API는 없다.
  2. 복구했는데도 줄지 않으면 2.3(DEAD)에서 포기된 행이 있는지 본다.

### 2.3 Outbox DEAD

- **의미**: 이벤트 하나가 10번 발행에 실패해서 릴레이가 포기했다(`DEAD`). 다시 살리기 전에는 발행되지 않고, 같은 집계(aggregate)의 뒤 이벤트도 그 행이 `READY`로 돌아올 때까지 순서를 기다리는지 확인해야 한다.
- **볼 곳**
  - Grafana Kafka·Outbox의 "Outbox DEAD".
  - 목록: `GET /admin/api/outbox?status=DEAD` → 항목의 `topic`, `aggregateId`, `attempts`, `lastError`, `createdAt`. 오래된 것부터 나오고 `nextCursor`로 다음 쪽을 본다.
- **조치**
  1. `lastError`로 원인을 고친다(Kafka 장애, 없는 토픽 등).
  2. 행마다 다시 살린다. `DEAD → READY`가 되고 `attempts`가 0으로 돌아가 다음 릴레이 주기에 발행된다.
     `POST /admin/api/outbox/{id}/retry` → 204
  3. 없는 id는 404, `DEAD`가 아닌 행은 409다(같은 요청을 두 번 눌러도 두 번째는 409이므로 안전하다).

### 2.4 결제 확인 지연

- **의미**: 승인 요청의 응답이 끊겨서 PG의 결과를 모르는(`UNKNOWN`) 결제가 600초 넘게 남아 있다. 주문은 `PAYMENT_IN_PROGRESS`로 머물고 재고 예약이 붙잡혀 있다. 대사 잡이 1분마다 조회하는데도 풀리지 않은 것이다.
- **볼 곳**
  - Grafana 결제·정합성의 "UNKNOWN 결제 수", "가장 오래된 UNKNOWN".
  - 목록: `GET /admin/api/payments?status=UNKNOWN` → `{id, orderId, pgOrderId, amount, reconcileAttempts, createdAt, updatedAt}`. `createdAt`이 얼마나 밀렸는지 보는 값이다.
  - 로그: Kibana "결제 UNKNOWN", "주문으로 찾기"(`orderId`).
- **조치**
  1. 그 주문만 바로 대사한다. 경로의 값은 `orderId`다.
     `POST /admin/api/payments/{orderId}/reconcile` → 200 `{"orderId":"...","status":"PAID"}`
  2. 응답의 `status`를 본다.
     - `PAID` 또는 `PAYMENT_FAILED`: 해결됐다.
     - `PAYMENT_IN_PROGRESS` 그대로: PG 조회도 불확실했다. Toss 상점 관리자에서 목록의 `pgOrderId`로 결제를 찾아 승인 여부를 확인하고, 시간을 두고 다시 누른다. PG에도 기록이 없으면 대사가 `PAYMENT_FAILED`로 닫는다.
  3. 이미 끝난 주문을 눌러도 현재 상태만 돌려주고 아무것도 바꾸지 않는다.

### 2.5 정체 Saga

- **의미**: 끝나지 않은 흐름이 멈춰 있다. 알림의 `type` 라벨로 구분한다.

| `type` | 멈춘 것 | 기준 |
|---|---|---|
| `order_payment` | 결제 중(`PAYMENT_IN_PROGRESS`) 주문 | 5분 넘음 |
| `refund` | 끝나지 않은 환불(`APPROVED`·`PG_CANCELLED`) | 10분 넘음 |

- **볼 곳**: Grafana 결제·정합성의 "정체 Saga". 라벨별로 어느 쪽인지 본다.
- **조치**
  - `order_payment`: 2.4의 대사(`/admin/api/payments/{orderId}/reconcile`)로 푼다. 어떤 주문인지 모르면 `GET /admin/api/payments?status=UNKNOWN`의 `orderId`를 본다. 결제가 `IN_PROGRESS`(승인 요청 중)면 Kibana "주문으로 찾기"로 흐름을 먼저 본다.
  - `refund`: 복구 잡이 5분 지난 `APPROVED`·`PG_CANCELLED` 환불을 다시 진행한다. 풀리지 않으면 PG 취소가 계속 불확실한 것이다. Kibana에서 `message : "환불 상태 변경 실패"` 또는 `message : "복구 중 PG가 취소를 거절했다"`를 찾는다. 환불이 `FAILED`로 끝났다면 2.7을 본다.

### 2.6 재고 불일치

- **의미**: 하루 한 번(05:00) 도는 점검 잡이 재고 행의 숫자가 기록과 다르다고 찾았다. 한 상품의 `received`가 입고·조정 이력(`stock_movement`의 `RECEIVE`·`ADJUST` 합)과 다르거나 `reserved`가 보류 중(`HELD`) 예약 합과 다르다. 코드 버그로 숫자가 틀어졌다는 뜻이다.
- **볼 곳**
  - Grafana 결제·정합성의 "재고 불일치". 이 값은 점검 잡이 돌 때만 바뀌므로 앱을 재시작하면 다음 실행까지 0으로 보인다.
  - 어느 상품인지: Kibana `message : "stock mismatch"` → `productId`.
- **조치**: 운영 API는 없다. 판매자용 재고 조정 API(`stock-adjustments`)는 판매자 본인 상품의 수량 변경용이라 이 불일치를 고치는 수단이 아니다.
  1. `productId`로 `product.stock`, `product.stock_movement`, `product.stock_reservation`(상태 `HELD`)을 읽어 어느 숫자가 틀렸는지 확인한다(점검 SQL은 로드맵 6-2 6)에 있다).
  2. 원인(코드 버그)을 고치고 숫자는 DB에서 이력에 맞춘다. 어긋난 값이 새로 늘어나는지 다음 날 05:00 점검 또는 수동 실행으로 확인한다.

### 2.7 알림 규칙이 없는 항목

6-2 알림에 없지만 운영 API로 처리해야 하는 실패가 셋 있다. 정기적으로(하루 한 번 등) 목록을 본다.

| 대상 | 확인 | 조치 |
|---|---|---|
| 실패한 환불 | `GET /admin/api/refunds?status=FAILED` | 아래 2.7.1 |
| 실패한 알림 | `GET /admin/api/notifications?status=FAILED` | 2.7.2 |
| 지급 실패 정산 | `GET /admin/api/settlements?status=PAYOUT_FAILED` | 2.7.3 |

#### 2.7.1 실패한 환불

PG가 취소를 명확히 거절해서 환불이 `FAILED`로 끝난 건이다. 구매자 취소면 502를 받고 끝났고, 반품이면 품목이 `RETURN_REQUESTED`에 머문다. `FAILED` 환불이 있는 동안 그 품목은 발송할 수 없고 구매자가 다시 취소할 수도 없다.

1. 목록 항목의 `failureCode`(그 환불의 마지막 PG 취소 코드)와 `pgAttempts`를 본다. `NOT_CANCELABLE_AMOUNT`처럼 다시 해도 안 되는 코드면 재시도하지 않고 2번으로 간다.
2. 다시 해도 되는 실패(일시 장애 등)면 재시도한다. 새 멱등 키(`{refundId}-r{pgAttempts}`)로 PG까지 다시 간다.
   `POST /admin/api/refunds/{id}/retry`
   - 200 `COMPLETED`: 끝났다.
   - 202 `APPROVED`: PG 결과가 불확실하다. 복구 잡이 5분 뒤 이어서 마무리한다.
   - 502 `PG_ERROR`: 다시 거절됐다. `pgAttempts`가 늘어 있다.
   - 409: `FAILED`가 아니다(이미 처리됨).
3. 계속 거절되는 환불(예: PG에서 이미 취소된 결제)은 종결한다. 돈은 PG 밖에서 운영자가 처리하고, 시스템에는 거절로 닫는다. 사유가 필요하다(200자 이하).
   `POST /admin/api/refunds/{id}/close` 본문 `{"reason":"PG에서 이미 취소됨"}`
   - 반품 환불: 품목이 `DELIVERED`로 돌아간다. 같은 품목의 반품은 다시 요청할 수 없다.
   - 구매자 취소 환불: 환불 행만 닫힌다. 품목은 `PAID`라 구매자가 다시 취소하거나 판매자가 발송할 수 있다.

#### 2.7.2 실패한 알림

5번 발송에 실패해 `FAILED`가 된 알림이다.

1. 목록 항목의 `lastError`(예: `smtp down`)를 보고 원인(SMTP 계정, 수신자 주소 등)을 고친다.
2. 다시 보낸다. `FAILED → PENDING`, `attempts`가 0이 되어 10초 주기의 발송 잡이 바로 집어 간다.
   `POST /admin/api/notifications/{id}/retry` → 204
   없는 id는 404, `FAILED`가 아니면 409다.

#### 2.7.3 지급 실패 정산

정산 지급이 실패해 `PAYOUT_FAILED`가 된 건이다. 별도 재지급 API는 없고 정산 실행을 끝난 달로 다시 부른다. 기간과 상관없이 `PAYOUT_FAILED`가 모두 다시 지급되고, 이미 정산한 달이면 새 정산은 만들어지지 않는다.

1. 목록 항목의 `shopName`, `periodStart`~`periodEnd`, `netAmount`, `lastError`를 본다. 지급 계좌·게이트웨이 문제를 먼저 고친다.
2. 끝난 달로 실행한다(이번 달을 넣으면 400이다).
   `POST /admin/api/settlements/run?period=2026-09` → 200 `{"settledShops":0,"paid":1,"payoutFailed":0}`
   `payoutFailed`가 남으면 원인이 그대로인 것이다.

## 3. 알림이 울리지 않을 때

- 알림 전달 채널은 두지 않았다. Grafana Alerting → Alert rules에서 `Firing` 상태로 확인한다.
- 문제가 생기고 알림이 뜨기까지 최대 약 75초가 걸린다.
- Grafana에 값이 안 보이면: Prometheus의 Target health(`http://VM주소:9090`은 VM 안에서만 열린다)에서 앱 수집 대상이 `up`인지 본다.
- 점검 지표(`stock_balance_mismatch_count`)는 앱 재시작 직후 0이다.

## 4. 프론트 없이 직접 호출

프론트의 운영 화면을 쓸 수 없을 때만 쓴다. 위 절의 `METHOD 경로`를 그대로 호출하면 된다.

```bash
BASE=http://VM주소:8080
TOKEN=$(curl -s -XPOST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"관리자@이메일","password":"비밀번호"}' | python3 -c 'import json,sys;print(json.load(sys.stdin)["accessToken"])')

curl -s -H "Authorization: Bearer $TOKEN" "$BASE/admin/api/outbox?status=DEAD"                 # 목록(GET)
curl -s -XPOST -H "Authorization: Bearer $TOKEN" "$BASE/admin/api/outbox/{id}/retry"            # 쓰기(POST)
```

역할은 토큰에 담기므로 ADMIN으로 바꾼 뒤에 다시 로그인해 받은 토큰을 쓴다. 본문이 필요한 `refunds/{id}/close`는 `-H 'Content-Type: application/json' -d '{"reason":"사유"}'`를 더한다.
