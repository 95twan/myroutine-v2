# S2-1a Baseline 리포트

> 버전 0.4 · 2026-11-06 · S-03 결과
> 0.3 · 2026-11-06 · S-02 결과, 만료 잡과 알림 메일
> 0.2 · 2026-11-06 · S-06 결과
> 0.1 · 2026-11-06 · 측정 환경, 시드, S-01 결과

[Stage 2 준비](../03-roadmap/stage2-plan.md) 5절의 절차로 운영 VM에서 잰 Baseline이다. 개선 실험(S2-2·S2-3·S2-5)은 이 값과 같은 조건에서 비교한다. 단일 VM과 합성 데이터라 절대 수치보다 병목의 위치와 비교 결과로 읽는다.

## 1. 측정 환경

| 항목 | 값 |
|---|---|
| 커밋 | `f2a6a8c`(develop, S2-1a 머지, VM의 `deployed-sha`로 확인). 시드 `02-products.sql`은 정수 넘침을 고친 파일로 실행했다(`(n::bigint * 7919)`, 브랜치 `fix/seed-integer-overflow`, 측정 뒤 머지) |
| VM | Proxmox VM, vCPU 8, RAM 16GB, Docker Compose. 앱·Postgres·Redis·Kafka·ES·ELK·Prometheus·Grafana가 한 VM을 나눠 쓴다 |
| 앱 | 1개. HikariCP 최대 커넥션 10, Tomcat 작업 스레드 200(모두 Spring Boot 기본값, 설정 없음) |
| Postgres | 17. `shared_buffers` 기본값 128MB(VM에서 확인). `pg_stat_statements`(track=top), `log_min_duration_statement=100ms`(`select pg_sleep(0.15)`가 `duration: 150.559 ms`로 로그에 남는 것을 확인), `track_io_timing=on` |
| 부하 | 개발 PC(macOS, k6 v1.4.2) → LAN → VM 8080. 측정 중 k6 CPU 약 50%(코어 0.5개)로 부하 생성 쪽은 병목이 아니다 |
| VU | 응답을 받자마자 다음 요청을 보낸다(대기 시간 없음). "300 VU"는 사용자 300명이 아니라 동시 요청 300개로 만든 포화 부하다 |
| 측정 전 상태 | 시드 → `vacuum (analyze)` → Proxmox 스냅샷(`seed_vacuumed`, RAM 제외). 스냅샷이 있는 상태에서 쟀다 |
| 측정 시각 | 2026-11-06 20:00~20:45 KST. 재고 점검 잡(05:00)·정산 잡(매달 5일 03:00)과 겹치지 않는다 |

## 2. 시드

| 테이블 | 시드 전 | 시드 후 |
|---|---|---|
| `member.member` | 4 | 1,001,104 |
| `shop.shop` | 1 | 10,001 |
| `product.product` | 1 | 1,000,102 |
| `orders.orders` | 0 | 10,000,000 |
| `orders.shop_order` | 0 | 15,000,000 |
| `orders.order_line` | 0 | 25,000,000 |
| `payment.payment` | 0 | 9,000,000 |
| `settlement.settlement` | 0 | 207,000 |
| `settlement.settlement_item` | 0 | 19,726,589 |
| `pg_database_size` | 9,238 kB | 37 GB |

- 불변식 8개([`InvariantChecker`](../../src/test/java/com/myroutine/support/InvariantChecker.java)와 같은 쿼리)는 시드 뒤 모두 0건이다.
- 스케줄 잡 대상(`PENDING_PAYMENT`·`PAYMENT_IN_PROGRESS` 주문, `DELIVERED` 가게주문)은 시드 전후 모두 0건이다.
- DB 크기는 1/100 규모를 100배 한 추정(약 27GB)보다 컸다. `04`가 정산 대상 품목 18,721,742건을 UPDATE해 죽은 행이 같은 수만큼 생긴 것이 한 원인으로 보인다(`vacuum`은 파일 크기를 줄이지 않는다).

적재 시간(`02`~`05`, 문장별 `\timing`). `02` 시작 17:00, `05` 끝 19:28로 약 2시간 28분이다.

| 파일 | 문장 | 건수 | 시간 |
|---|---|---|---|
| `02` | 상품·재고·입고 이력 INSERT | 각 100만 | 17초, 8초, 15초 |
| `03` | 작업 테이블 4개 만들기 | 1,000만~2,500만 | 각 15~41초 |
| `03` | `orders.orders` INSERT | 1,000만 | 6분 49초 |
| `03` | `orders.shop_order` INSERT | 1,500만 | 34분 45초 |
| `03` | `orders.order_line` INSERT | 2,500만 | 20분 47초 |
| `03` | `payment.payment` INSERT | 900만 | 8분 49초 |
| `04` | `settlement.settlement_item` INSERT | 19,726,589 | 16분 28초 |
| `04` | `settlement.settlement` INSERT | 207,000 | 25초 |
| `04` | `settlement_item.settlement_id` UPDATE | 18,721,742 | 57분 9초 |
| `05` | k6 전용 데이터 + `ANALYZE` | 수천 | 9초 |
| 정리 | `vacuum (analyze)` | | 7초 |

오래 걸린 문장은 인덱스가 여럿인 큰 테이블에 행을 무작위 순서로 넣거나 고친 것이다. `shop_order`는 인덱스가 5개이고 id·`order_id`·`shop_id`·`created_at`이 무작위로 들어가, 인덱스가 `shared_buffers`(128MB)를 넘은 뒤로는 넣을 때마다 디스크를 읽고 썼다. 다시 넣을 일이 있으면 인덱스를 지우고 넣은 뒤 다시 만드는 편이 빠르고, 정산 대상 품목은 `settlement_id`를 INSERT 때 바로 채우면 UPDATE를 없앨 수 있다. `vacuum`이 7초로 끝난 것은 2시간 넘는 적재 중에 autovacuum이 테이블 대부분을 이미 처리했기 때문으로 보인다(추정).

## 3. 결과 요약

| 시나리오 | 부하 | 처리량 | p50 | p95 | p99 | 에러율 | 기준 | 병목 |
|---|---|---|---|---|---|---|---|---|
| S-01 상품 조회 | 300 VU, 6분 | 5,019건/초 | 50.0ms | 124.4ms | 165.5ms | 0% | p95 < 200ms 통과 | 앱의 커넥션 점유 시간 × 풀 10개 |
| S-06 Outbox 지연 | 상품 수정 초당 20건, 5분 | 이벤트 약 40건/초 | 1,019ms | 1,475ms | | 0%(충돌 409 제외) | p95 < 1초 실패 | 릴레이의 1초 쉼과 한 건씩 동기 발송 |
| S-02 체크아웃 | 100 VU, 6분 | 658건/초 | 115.2ms | 298.6ms | 409.4ms | 0% | p95 < 500ms 통과 | 커넥션 풀 10(체크아웃 한 건이 커넥션을 약 3번 빌린다) |
| S-03 인기 상품 | 1,000명 동시 1회 | 1,000건을 약 2.9초에 처리 | 1.77s | 2.70s | 2.73s | 0% | 초과 판매 0 | 커넥션 풀 10 대기 + 재고 행 락 |

## 4. S-01 상품 조회

### 조건

`perf/k6/s01-product-read.js`. 요청의 30%는 `GET /api/products?category=…&size=20`(목록), 70%는 `GET /api/products/{id}`(상세, 판매 중 상품 10만 개 중 무작위)다. 목록은 VU마다 한 카테고리를 최대 10페이지까지 커서로 이어 읽고 새 카테고리로 돌아간다. 램프업 1분, 300 VU 유지 5분.

처음에는 페이지 상한 없이 쟀다(아래 "페이지 상한이 없을 때"). 그 결과가 측정 시간에 따라 계속 나빠져 Baseline으로 쓸 수 없어서 상한을 넣고 다시 쟀다.

### k6 결과

| 요청 | 건수 | avg | p50 | p95 | p99 | max |
|---|---|---|---|---|---|---|
| 전체 | 1,807,237 | 54.6ms | 50.0ms | 124.4ms | 165.5ms | 397ms |
| 상세 | 1,265,522 | 54.4ms | 49.9ms | 124.1ms | 165.3ms | 397ms |
| 목록 | 541,715 | 55.0ms | 50.3ms | 124.9ms | 165.9ms | 396ms |

처리량 5,019건/초, 실패 0건. 기준(p95 < 200ms, 에러율 < 0.1%)을 통과했다.

### DB 상위 문장

| 문장 | 호출 | 평균 | DB 시간 비중 |
|---|---|---|---|
| 목록 다음 페이지(커서 있음) | 487,402 | 0.31ms | 48.7% |
| 상세: 상품 + 이미지 | 1,265,522 | 0.07ms | 27.6% |
| 상세: 재고 | 1,265,522 | 0.03ms | 13.5% |
| `BEGIN READ ONLY` | 1,807,237 | 0.00ms | 2.8% |
| 목록 첫 페이지 | 54,313 | 0.14ms | 2.5% |
| `PaymentMetrics` `count(...) where status = $1` | 18 | 418ms | 2.4% |
| `PaymentMetrics` `min(created_at) where status = $1` | 18 | 388ms | 2.3% |

- 다음 페이지가 첫 페이지보다 두 배 느린 것은 커서 위치까지의 행(평균 약 100행)을 읽고 버리기 때문이다(아래 "페이지 상한이 없을 때"의 실행 계획).
- `PaymentMetrics` 두 문장은 k6 요청이 아니라 앱의 지표 수집기가 30초마다 보내는 배경 부하다. `payment.payment`에 `status` 인덱스가 없어 900만 건을 훑는다.

### 지표

| 지표 | 값 |
|---|---|
| DB 트랜잭션/초 | 램프업 뒤 약 5K로 평평 |
| HikariCP | active 10(최대)에 고정, pending 약 185 |
| 커넥션 획득 시간 | 약 37ms(구간 끝의 1분 평균) |
| Postgres 연결 상태 | active 0~3, idle in transaction 4~9, idle 2~6 |
| 대기 이벤트 | `Client` 2~7, `IO` 가끔 |
| 락 | `AccessShareLock` 20~50만 있다(SELECT가 잡는 락, 경합 없음) |
| 버퍼 적중률 | 약 95%로 평평 |
| 컨테이너 CPU | 앱 약 3.5코어, postgres 약 1.8코어 |
| 컨테이너 메모리 | 특이점 없음 |

### 분석

상세(DB 0.1ms)와 목록(DB 0.3ms)의 응답 시간이 p95 124ms로 같다. 두 요청이 쿼리가 아니라 같은 줄에서 기다린다는 뜻이고, 그 줄은 HikariCP 커넥션 대기다(active 10 고정, pending 약 185).

그런데 Postgres가 보기에 10개 중 실제로 쿼리를 실행 중인 커넥션은 0~3개이고, 나머지는 트랜잭션이 열린 채 앱의 다음 명령을 기다린다(`idle in transaction`, 대기 이벤트 `Client`).

| 값 | 계산 |
|---|---|
| 요청 하나가 커넥션을 쥐는 시간 | 커넥션 10개 ÷ 5,019건/초 ≈ 2.0ms |
| 그중 DB 실행 시간 | k6 요청의 문장 총 실행 시간 295초 ÷ 1,807,237건 ≈ 0.16ms (약 8%) |

커넥션을 쥔 시간의 약 92%가 DB 밖에서 지나간다. 요청마다 `BEGIN READ ONLY`·쿼리(상세는 2개)·`COMMIT`을 따로 주고받고, 그 사이 앱이 결과를 객체로 바꾸며, 스레드 200개가 앱 CPU를 나눠 쓴다. 처리량 5,019건/초는 "풀 10개 ÷ 점유 2ms"로 설명된다. DB CPU(1.8코어)와 VM CPU(앱과 postgres 합쳐 5.3/8코어)에는 여유가 있다.

결론: **S-01의 처리량은 DB가 아니라 "커넥션을 쥔 채 앱에서 보내는 시간 × 풀 크기 10"이 정한다.**

### 페이지 상한이 없을 때

처음 실행은 VU가 한 카테고리의 커서를 끝까지 넘겼다. 6분 동안 목록 호출이 177,177건이라 VU당 평균 약 590페이지(약 1만 2천 행) 깊이까지 들어갔다.

| 항목 | 상한 없음 | 상한 10페이지 |
|---|---|---|
| 처리량 | 1,637건/초 | 5,019건/초 |
| p95 / p99 | 426.6ms / 574.1ms | 124.4ms / 165.5ms |
| 상세 p95 / 목록 p95 | 419.7ms / 442.9ms | 124.1ms / 124.9ms |
| 다음 페이지 평균 / DB 시간 비중 | 14.14ms / 97.5% | 0.31ms / 48.7% |
| DB 트랜잭션/초 | 20:02:30에 2.6K로 최고, 끝날 때 1K까지 계속 감소 | 약 5K로 평평 |
| 버퍼 적중률 | 95% → 85%로 감소 | 약 95% |
| 컨테이너 CPU | postgres 3.9 → 5.5코어로 증가, 앱 2.2 → 1코어로 감소 | 앱 3.5, postgres 1.8 |
| Postgres 연결 상태 | active 7~10 | active 0~3 |
| 커넥션 획득 시간 | 약 190ms | 약 37ms |

깊이 6,000행 위치의 다음 페이지 실행 계획(`EXPLAIN (ANALYZE, BUFFERS)`, 카테고리 FOOD):

```text
Limit (actual time=18.670..18.821 rows=21)
  ->  Nested Loop
        ->  Index Scan using idx_product_status_category_created_at on product p
              Index Cond: ((status = 'ON_SALE') AND (category = 'FOOD'))
              Filter: ((created_at < '…') OR ((created_at = '…') AND (id < '…')))
              Rows Removed by Filter: 6001
              Buffers: shared hit=4104 read=1935
        ->  Index Scan using pk_stock on stock s
Execution Time: 18.858 ms
```

커서 조건 `created_at < :c or (created_at = :c and id < :id)`는 두 컬럼에 걸친 OR라 인덱스의 시작 위치(`Index Cond`)로 쓰이지 않고 `Filter`로 갔다. 그래서 인덱스를 카테고리 구간의 맨 앞부터 읽으며 커서 앞의 6,001행을 테이블 페이지까지 읽고 버렸다. 다음 페이지의 비용이 깊이에 비례하므로, 상한이 없으면 측정 내내 쿼리가 느려지고 DB CPU가 오르며 처리량이 떨어졌다. keyset 페이징을 썼지만 실제 동작은 OFFSET과 같다.

[개발 가이드](../development-guide.md)의 keyset 규칙은 행 값 비교 `(created_at, id) < (:c, :id)`인데, 로드맵의 JPQL 예시([Part 2](../03-roadmap/part2-order-money.md) 내 주문·가게 주문 목록, [Part 3](../03-roadmap/part3-events.md) 정산 목록)가 OR 형태로 적혀 있어 다른 목록 쿼리도 같은 형태다.

## 5. S-06 Outbox 지연

### 조건

`perf/k6/s06-outbox-lag.js`. 판매자 u1이 자기 상품 90개 중 무작위 하나의 가격을 `PATCH /api/shops/{shopId}/products/{id}`로 초당 20건씩 5분 동안 바꾼다(`constant-arrival-rate`). 가격 변경 한 건이 `product-upserted`와 `product-price-changed` 두 이벤트를 outbox에 쓴다. 측정 구간은 2026-11-06 20:54:55~20:59:55 KST.

### 결과

| 항목 | 값 |
|---|---|
| 요청 | 6,000건, 초당 20건, `dropped_iterations` 0 |
| 응답 시간 | p50 10ms, p95 88ms, p99 239ms |
| 실패 | 14건(0.23%). 같은 상품을 동시에 고친 요청의 낙관적 락 충돌(409 `CONFLICT_RETRY`) |

| 토픽 | 이벤트 | 미발송 | p50 | p95 | max |
|---|---|---|---|---|---|
| `product.product-price-changed.v1` | 5,910 | 0 | 1,024ms | 1,479ms | 1,965ms |
| `product.product-upserted.v1` | 5,986 | 0 | 1,015ms | 1,471ms | 1,958ms |
| 합계 | 11,896 | 0 | 1,019ms | **1,475ms** | 1,965ms |

지연은 `sent_at - created_at`이다. `created_at`은 트랜잭션 안에서 찍은 앱 시각이라 커밋까지의 시간만큼 실제보다 약간 크다.

- 실패 14건의 근거: 200 응답 5,986건은 `upserted` 이벤트 수와 같고, 그중 가격이 실제로 바뀐 5,910건만 상품 UPDATE와 가격 변경 이벤트를 만들었다(같은 가격이 뽑힌 76건은 UPDATE 없음). 상품 UPDATE 호출은 5,924건이라 `version`이 맞지 않아 0행을 바꾼 14건이 실패 수와 같다. 설계대로의 응답이므로 스크립트를 409를 따로 세도록 고쳤다(`update_conflict`). 이벤트 지연에는 영향이 없어 다시 재지 않았다.
- DB: 쓰기 문장은 모두 0.1ms 미만이다(상품 UPDATE 0.09ms, outbox INSERT 0.04ms, 발송 완료 UPDATE 0.07ms). DB 시간의 73%는 지표 수집기의 결제 조회다. 연결은 거의 모두 idle, CPU는 앱 0.15~0.45코어, postgres 약 0.1코어.
- 버퍼 적중률이 12~20%로 30초 주기로 진동했다. 부하가 작아 지표 수집기의 결제 900만 건 조회가 읽는 블록이 비율을 지배한 것이고 S-06과는 무관하다.

### 분석

`OutboxRelayJob`은 `@Scheduled(fixedDelay = 1000)`이라 한 번 실행이 끝난 뒤 1초를 쉬고 다시 시작한다. `OutboxRelay.relayOnce`는 `READY` 이벤트를 최대 100건 가져와 한 건씩 `kafkaTemplate.send(...).get(5, SECONDS)`로 Kafka 응답을 기다린 뒤 다음 건을 보낸다. 발송 시각 간격으로 한 실행을 나눴다.

| 값 | 측정 | 계산 |
|---|---|---|
| 5분 동안의 실행 수 | 199회 | 실행 주기 약 1.5초 |
| 실행당 이벤트 | | 11,896 ÷ 199 ≈ 60건 |
| 이벤트 하나 발송(같은 실행 안의 발송 간격) | p50 8.0ms, p95 11.1ms | 실행당 발송 시간 ≈ 60 × 8ms ≈ 480ms |
| 실행 사이 간격(앞 실행의 마지막 발송 → 다음 실행의 첫 발송) | p50 1,015ms | 1초 쉼 + 조회 |

이벤트 하나의 지연은 "다음 실행까지 기다리는 시간(0~1.5초)"과 "실행 안에서 앞 이벤트들을 보내는 시간(0~480ms)"의 합이다. 둘 다 고르게 퍼진다고 보면 평균 약 0.75초 + 0.24초 ≈ 1.0초, 최대 약 1.5초 + 0.48초 ≈ 2.0초로 측정값(p50 1,019ms, max 1,965ms)과 맞는다. DB는 지연에 거의 기여하지 않는다.

결론: **S-06 지연은 릴레이 구조가 정한다. 1초 쉼이 대기의 대부분이고, 한 건씩 동기 발송이 실행을 480ms 늘려 주기를 1.5초로 만든다.**

처리 한계도 같은 구조에서 나온다. 한 실행은 최대 100건이고 그때 실행 시간은 약 1,015ms + 100 × 8ms ≈ 1.8초라, 릴레이가 보낼 수 있는 양은 초당 약 55건(상품 수정으로 초당 약 27건)이다. 이보다 많이 쌓이면 미발송이 계속 늘어난다(계산값, 측정하지 않음).

## 6. S-02 체크아웃

### 조건

`perf/k6/s02-checkout.js`. 회원 `k6-co-{1..100}`이 각자 다른 상품(재고 10만) 1개가 담긴 장바구니로 `POST /api/orders/checkout`을 매번 새 `Idempotency-Key`로 반복한다. 로그인·장바구니·배송지 조회는 `setup()`에서 끝낸다. 램프업 1분, 100 VU 유지 5분. 측정 구간은 2026-11-06 약 21:23~21:29 KST.

### 결과

| 항목 | 값 |
|---|---|
| 체크아웃 | 241,474건, 658건/초(램프업 포함 평균), 실패 0건 |
| 응답 시간 | avg 136.3ms, p50 115.2ms, p95 298.6ms, p99 409.4ms, max 1.16s |
| 기준 | p95 < 500ms 통과 |
| 결제 대기 주문 | 측정 전 0 → 측정 뒤 241,474 |
| outbox 미발송 | 측정 전후 0건(체크아웃은 outbox 이벤트를 만들지 않는다) |

### DB 상위 문장

체크아웃 한 건이 16개 문장을 실행하고, 모두 평균 0.2ms 미만이다.

| 문장 | 평균 | DB 시간 비중 |
|---|---|---|
| `orders.orders` INSERT | 0.16ms | 20.0% |
| `common.idempotency_key` 응답 저장 UPDATE | 0.11ms | 14.3% |
| `orders.shop_order` INSERT | 0.08ms | 10.7% |
| `product.stock_reservation` 주문별 조회(0행) | 0.07ms | 8.5% |
| `product.stock_movement` INSERT | 0.06ms | 7.8% |
| `orders.order_line` INSERT | 0.06ms | 7.8% |
| `common.idempotency_key` INSERT | 0.05ms | 6.7% |
| `product.stock_reservation` INSERT | 0.05ms | 6.7% |
| 상품·재고 조회(호출 2회) | 0.02ms | 3.8% |
| `product.stock` 조건부 차감 UPDATE | 0.03ms | 3.4% |

회원마다 상품이 달라 재고 UPDATE에 경합이 없다(0.03ms). 체크아웃 한 건의 DB 실행 시간은 약 0.79ms다(문장 총 실행 시간 약 191초 ÷ 241,474건).

### 지표

| 지표 | 값 |
|---|---|
| DB 트랜잭션/초 | 약 2.05K로 평평, 끝 무렵 2.3K |
| HikariCP | active 10 고정, pending 약 85~90 |
| 커넥션 획득 시간 | 약 36ms(구간 끝의 1분 평균) |
| Postgres 연결 상태 | active 5~10, idle in transaction 0~4 |
| 대기 이벤트 | `LWLock` 3~9, `Client` 1~7, `IO` 1 |
| 락 | `AccessShareLock`·`RowExclusiveLock`·`RowShareLock`. 경합 표시 없음 |
| 버퍼 적중률 | 82~90%, 30초 주기로 진동(지표 수집기의 결제 조회) |
| 컨테이너 CPU | 앱 약 1.7코어, postgres 약 1.2코어 |

### 분석

`BEGIN`은 체크아웃 수와 같은 241,474회인데, DB 커밋은 초당 약 2.05K로 체크아웃(유지 구간 약 730건/초)의 약 2.8배다. 멱등 키 INSERT와 응답 저장 UPDATE가 본 트랜잭션 밖에서 각각 자동 커밋되어, 체크아웃 한 건이 트랜잭션 3개를 쓰고 커넥션을 3번 빌리는 것으로 본다. 빌릴 때마다 약 36ms를 기다리면 약 108ms로, 평균 응답 136ms의 대부분이다(추정).

결론: **S-02도 DB 실행(건당 약 0.8ms)이 아니라 커넥션 풀 10이 처리량을 정한다. 체크아웃 경로에 느린 쿼리는 없다.**

`LWLock` 대기 3~9는 쓰기가 많은 S-02에서만 크게 보였다. 어떤 LWLock인지(WAL 쓰기 등)는 패널이 `wait_event_type`까지만 보여 줘 확인하지 못했다. 다음 쓰기 측정에서 `pg_stat_activity`의 `wait_event`로 확인한다.

### 결제 대기 주문의 만료와 알림 메일

S-02 뒤 결제 대기 주문 241,474건이 만료되는 속도를 쟀다.

| 시각(KST) | 결제 대기 주문 |
|---|---|
| 21:38:02 | 241,474 |
| 21:40:45 | 241,374 |
| 21:43:45 | 241,274 |
| 21:45:27 | 241,274 |
| 21:49:28 | 241,174 |

`OrderExpirationJob`은 `fixedDelay = 60_000`, 한 번에 `Limit.of(100)`이라 분당 100건을 예상했지만, 실제로는 4~5분에 100건이었다. 같은 구간에 Spring의 `@Scheduled` 실행 시간 지표(`tasks_scheduled_execution_seconds`)를 보니 잡별 한 번 실행 평균은 다음과 같았다.

| 잡 | 한 번 실행 평균 |
|---|---|
| `NotificationDispatchJob` | 292.5초 |
| `PaymentMetrics` 갱신 | 0.50초 |
| `OrderExpirationJob` | 0.37초 |
| `OutboxRelayJob` | 0.07초 |

- 만료된 주문마다 "주문 만료" 알림이 생기고(`notification` INSERT 100건/실행), `NotificationDispatchJob`이 한 번에 100건을 한 건씩 `SmtpMailSender`(Gmail SMTP, `prod` 프로필)로 보낸다. 건당 약 3초라 한 번 실행에 약 5분이 걸린다.
- 만료 잡 자체는 0.37초인데 4~5분에 한 번만 돌았다. `@Scheduled`는 설정이 없으면 스레드 하나(`spring.task.scheduling.pool.size` 기본값 1)로 모든 잡을 차례로 실행하고, `application.yaml`에 이 설정이 없다. 알림 잡이 스레드를 5분 쥐는 동안 다른 잡이 기다린 것으로 본다(추정, 스레드 상태로 확인하지 않음). 그렇다면 outbox 릴레이도 같은 시간만큼 멈춘다. S-06 때는 보낼 알림이 없어 드러나지 않았다.
- 받는 주소는 시드 회원의 `@seed.test`(존재하지 않는 도메인)라 메일은 모두 반송된다. 측정 절차의 빈틈이라 [Stage 2 준비](../03-roadmap/stage2-plan.md) 5.2 6)의 7번을 고쳤다(S-02·S-03 뒤 15분 안에 앱을 멈추거나 되돌린다).

S-02 상태는 스냅샷 `seed_vacuumed`로 되돌렸다(되돌린 뒤 주문 1,000만, 결제 대기 0, 시드 회원 알림 0, 인기 상품 재고 100/0).

## 7. S-03 인기 상품

### 조건

`perf/k6/s03-hot-product.js`. 회원 `k6-hot-{1..1000}`이 재고 100개인 `k6 인기 상품` 1개를 동시에 체크아웃한다(`per-vu-iterations`, VU 1,000개가 1회씩 함께 시작). 로그인·장바구니·배송지 조회는 `setup()`에서 끝낸다. 스냅샷 `seed_vacuumed`로 되돌리고 S-01 연결 확인(VU 10, 30초)으로 데운 뒤 2026-11-06 약 22:01 KST에 쟀고, 끝나자마자 앱을 멈추고 다시 되돌렸다(만료 알림 메일 방지).

### 결과

| 항목 | 값 |
|---|---|
| 성공(201) | **100**(`checkout_succeeded`) |
| 재고 부족(422 `OUT_OF_STOCK`) | 900 |
| 그 밖의 응답 | 0 |
| 재고 행 | `available 0`, `reserved 100`, `sold 0`, `received 100` |
| 주문 품목 | 100 |
| 체크아웃 응답 시간 | avg 1.80s, p50 1.77s, p95 2.70s, p99 2.73s, max 2.91s |

초과 판매는 없다. 재고 부족 900건은 멱등 키를 지워(`DELETE FROM common.idempotency_key` 900회) 같은 키로 다시 시도할 수 있게 한다.

k6 요약의 전체 `http_req_failed`는 75%(4,000건 중 3,000건)로 나왔다. `http.expectedStatuses(201, 422)`가 스크립트의 모든 요청에 적용되어 `setup()`의 로그인·장바구니·배송지 조회(200) 3,000건을 실패로 센 것이다. 체크아웃만 보는 `http_req_failed{name:checkout}`은 0%다. 스크립트를 `expectedStatuses(200, 201, 422)`로 고쳤다.

### DB 상위 문장

| 문장 | 호출 | 바뀐 행 | 평균 | DB 시간 비중 | S-02의 평균 |
|---|---|---|---|---|---|
| `product.stock` 조건부 차감 UPDATE | 1,000 | 100 | **3.87ms** | 49.5% | 0.03ms |
| `orders.orders` INSERT | 1,000 | 1,000 | 1.07ms | 13.7% | 0.16ms |
| `orders.shop_order` INSERT | 1,000 | 1,000 | 0.26ms | 3.3% | 0.08ms |

- 재고 UPDATE는 모두 같은 행을 고친다. 앞 트랜잭션이 커밋할 때까지 행 락을 기다리므로, 경합 없는 S-02(0.03ms)보다 약 130배 길다. 재고가 바닥난 뒤의 900건도 락을 기다린 뒤 조건(`available >= 1`)이 거짓이라 0행을 바꾸고 재고 부족으로 끝난다.
- 주문 INSERT는 1,000번 실행됐지만 남은 품목은 100건이다. 재고 차감에 실패한 900건은 트랜잭션째 롤백된다.

### 분석

1,000개 요청이 약 2.9초 안에 모두 끝났다(초당 약 345건). 커넥션 풀이 10개라 DB 안에서 동시에 진행되는 체크아웃은 최대 10건이고, 나머지는 앱에서 커넥션을 기다린다. 응답 시간 대부분(p50 1.77초)은 이 대기다. DB 안에서는 그 10건이 재고 행 하나의 락을 차례로 잡는다.

결론: **재고 차감은 정확했다(초과 판매 0). 응답 시간은 풀 10 대기가 대부분이고, DB 안의 직렬 구간은 재고 행 락(UPDATE 평균 3.87ms)이다.** 이 값들이 [S2-5 재고 차감 방식 비교](../03-roadmap/stage2-plan.md)의 기준이다.

Grafana는 S-03에 쓸 수 없었다. 측정이 약 3초라 Prometheus 수집 간격과 패널의 `rate(...[1m])` 창보다 짧아 점이 1~2개만 찍혔고, 락 대기(`Lock`)도 잡히지 않았다. S2-5에서 방식별 락 대기를 비교하려면 측정 중 `pg_stat_activity`·`pg_locks`를 짧은 간격으로 뽑거나, 같은 경합을 더 오래 유지하는 시나리오가 필요하다.

## 8. 개선 후보

| 후보 | 근거 | 단계 |
|---|---|---|
| 커서 다음 페이지의 OR 조건 | 깊이에 비례해 느려진다(4절 "페이지 상한이 없을 때"의 실행 계획과 처리량 감소). 상품 외 목록 쿼리도 같은 형태 | S2-2 |
| `PaymentMetrics`의 `status` 조회 | 30초마다 결제 900만 건을 훑는다(한 번에 약 400~460ms) | S2-2 |
| 커넥션 점유 시간과 풀 크기 | S-01: DB는 여유가 있는데 풀 10개가 가득 찼고 점유 시간의 약 92%가 DB 밖. S-02: 체크아웃 한 건이 커넥션을 약 3번 빌리고 빌릴 때마다 약 36ms 대기 | S2-3 |
| 스케줄러 스레드 1개 | 모든 `@Scheduled` 잡이 스레드 하나를 나눠 써서, 알림 발송 한 번(약 5분)이 만료 잡을 4~5분에 한 번으로 늦췄다. outbox 릴레이도 같은 영향을 받는 것으로 본다(추정) | S2-3 |
| 알림 메일 동기 발송 | 한 건씩 SMTP로 보내 건당 약 3초, 실행당 약 5분 | S2 단계 미정 |
| 결제 대기 주문의 만료 속도 | 설계상 분당 100건(1분 쉼, 한 번에 100건)이고, 위 두 문제로 실제는 4~5분에 100건. S-02 뒤 241,474건을 풀 수 없다 | S2 단계 미정(잡의 묶음 크기·주기) |
| Outbox 릴레이의 쉼과 동기 발송 | S-06 p95 1,475ms(기준 1초 실패). 1초 쉼이 대기의 대부분이고, 한 건씩 응답을 기다려 실행당 약 480ms가 더해진다. 처리 한계는 이벤트 초당 약 55건(계산) | [ADR-003](../adr/ADR-003-outbox-kafka.md) 재검토(폴링 주기·발송 방식·CDC) |

비교 측정은 같은 스크립트로 한다. S2-2의 커서 수정은 상한 10페이지(Baseline)와 상한 없음(`-e MAX_PAGES=100000`, 깊은 페이지 비용) 두 가지로 잰다.

## 9. 원자료

| 자료 | 위치 |
|---|---|
| k6 요약 JSON·화면 출력 | 개발 PC `perf/k6/results/`의 `s01.json`·`s01.txt`(상한 10), `s01-deep.json`·`s01-deep.txt`(상한 없음). 커밋하지 않는다 |
| 상위 20문장 | VM(runner) `~/s01-top20.txt`, `~/s01-deep-top20.txt` |
| 실행 계획 | VM(runner) `~/s01-explain.txt` |
| S-03 | 개발 PC `perf/k6/results/s03.json`·`s03.txt`, `results/vm/s03-top20.txt`·`s03-stock.txt` |
| S-02 | 개발 PC `perf/k6/results/s02.json`·`s02.txt`, VM(runner) `~/s02-top20.txt`·`~/s02-before.txt`·`~/s02-after.txt` |
| S-06 | 개발 PC `perf/k6/results/s06.json`·`s06.txt`, VM(runner) `~/s06-top20.txt`·`~/s06-lag.txt`·`~/s06-relay.txt` |
| 시드 확인 | VM(runner) `~/seed-check-before.txt`, `~/seed-check-after.txt`, `~/seed-run.txt` |
