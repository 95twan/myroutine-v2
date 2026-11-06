# Stage 2 준비: 측정 환경 · 시드 데이터 · k6 시나리오

> 버전 0.9 · 2026-11-07 · S2-2 단계 규격(6절)을 더했다. 목록 다음 페이지의 커서 조건 16개를 OR에서 행 값 비교로 바꾸고, 다음 페이지 테스트가 없는 10개는 테스트를 먼저 넣는다
> 0.8 · 2026-11-07 · S2-3 커넥션 풀과 예약 작업 스레드를 꼭 하는 단계로 올렸다(Baseline에서 커넥션 풀이 가득 차고, 메일 작업이 다른 예약 작업을 막음). S2-3 첫 작업은 측정용 가짜 메일 발송기
> 0.7 · 2026-11-06 · S-06은 409 `CONFLICT_RETRY`를 실패에서 뺀다. S-03은 스냅샷으로 되돌린 뒤 재고, S-02·S-03 뒤 15분 안에 앱을 멈춘다(만료 알림 메일)
> 0.6 · 2026-11-06 · S-01은 VU마다 한 카테고리를 최대 10페이지까지만 이어 읽는다
> 0.5 · 2026-11-06 · S2-1a 단계 규격(5절)을 더했다. VU 시나리오는 대기 시간 없이 돌리고, S-02는 회원마다 다른 상품을 주문하며, 구매확정 품목마다 정산 대상 품목을 넣는다
> 0.4 · 2026-11-06 · S2-1을 S2-1a(시드·지표 수집·S-01·02·03·06)와 S2-1b(S-04·05·07)로 나눴다. 시드 주문은 스케줄 잡의 대상 상태를 0건으로 넣고, DB·컨테이너 지표는 postgres_exporter와 cAdvisor로 수집한다
> 0.3 · 2026-11-06 · 측정 환경(운영 VM), 시드 규모, 부하 값을 확정했다
> 0.2 · 2026-11-06 · 시나리오 실행 순서와 개선 실험 우선순위를 정했다. S2-8 샤딩은 하지 않고, 재고 이력 3,000만 건은 S2-7을 할 때만 넣는다
> 0.1 · 2026-11-06 · 초안. 합격 기준은 [요구사항 5.2](../01-requirements/README.md)의 값이고, 건수·부하·비율은 제안이다

Stage 2 개선 단위(S2-1~S2-7)의 개요는 [아키텍처 진화](../02-design/01-architecture-evolution.md)에 있다. 이 문서는 Baseline의 준비, 개선 실험의 우선순위, S2-1a 단계 규격(5절), S2-2 단계 규격(6절)을 적는다. 시드 스크립트와 k6 스크립트는 사용자가 쓴다. 5절의 k6 스크립트와 시드 규칙은 로컬 격리 환경에서 1/100 규모로 실행해 확인한 것이다.

## 1. 측정 환경

| 항목 | 값 |
|---|---|
| 앱·인프라 | 운영 VM(RAM 16GB, vCPU 8, Docker Compose), 앱 1개 |
| 부하 생성 | 개발 PC의 k6 → LAN → VM의 8080([ADR-011](../adr/ADR-011-ops-practice-environment.md) "결과"). Cloudflare Tunnel을 거치지 않는다 |
| PG | 어느 시나리오도 승인을 호출하지 않는다 |
| 지표 | k6 요약, VM의 Prometheus·Grafana(앱, postgres_exporter, cAdvisor), `pg_stat_statements`, slow query log |
| 리포트 | `docs/perf/`. 커밋 sha, VM 자원, 앱 복제 수, 시드 건수를 함께 적는다 |

단일 VM과 합성 데이터라 절대 수치를 앞세우지 않고, 같은 조건에서 비교한 결과로 기록한다.

측정 전에 할 일은 다음과 같다.

- Proxmox 스냅샷을 남긴 뒤 시드를 넣는다.
- 측정 중에는 `develop`에 머지하지 않는다. 코드가 바뀐 push는 VM에 자동 배포되어 앱이 다시 뜬다.
- 운영 compose의 Postgres에 `pg_stat_statements`와 slow query 설정을 더한다(5절 1)).
- k3s로 옮기면 Baseline을 다시 잰다([ADR-012](../adr/ADR-012-kubernetes-zero-downtime.md)).
- VM 메모리나 디스크가 모자라면 Baseline을 재기 전에 MinIO를 [NAS로 옮긴다](../ops/nas-minio.md).

디스크: 1/100 규모 시드가 `VACUUM FULL` 뒤 266MB였고, 전체 규모는 넣은 직후 `pg_database_size` 37GB였다(작업 테이블 포함, 1/100 규모를 100배 한 추정보다 크다). 적재 중에는 WAL이 더 쓴다. VM 디스크 100GB 안에 든다.

## 2. 시드 데이터

### 규모

| 데이터 | 건수 | 테이블 | 쓰임 |
|---|---|---|---|
| 회원 | 100만 | `member.member`, `member.member_address` | S-02, S-03의 전용 회원 포함 |
| 가게 | 1만 | `shop.shop` | 상품·정산의 소유자 |
| 상품 | 100만 | `product.product`, `product.stock` | S-01, S-04, S-07 |
| 주문 | 1,000만 | `orders.orders`, `payment.payment` | S2-2 쿼리 튜닝 |
| 주문 품목 | 약 2,500만 | `orders.shop_order`, `orders.order_line` | S2-2 쿼리 튜닝 |
| 재고 이력 | 상품당 입고 1건, S2-7을 하면 3,000만 | `product.stock_movement` | 입고 이력은 재고 점검 잡이 읽는다. 3,000만 건은 S2-7 파티셔닝 때만 넣는다 |
| 정산 대상 품목 | 구매확정 품목마다 1건(약 2,000만) | `settlement.settlement_item`, `settlement.settlement` | 지난달보다 앞선 달은 정산을 끝낸 상태로 넣는다. S-05가 처리할 지난달 건수(1/100 실험에서 지난달 약 8,400건, 전체 약 85만)를 100만으로 맞출지는 S2-1b에서 정한다 |
| 시나리오 전용 회원·장바구니 | 회원 1,100명, 상품 101개 | `member.*`, `orders.cart`, `orders.cart_item`, `product.*` | S-02 회원 100명은 각자 다른 상품, S-03 회원 1,000명은 재고 100개 상품 하나 |

### 생성 방식

앱 API를 거치지 않고 `psql`로 `INSERT ... SELECT generate_series(...)`를 실행한다. 건수가 많아 API로는 오래 걸리고, 측정 대상이 아닌 이벤트 처리까지 돌기 때문이다.

| 항목 | 규칙 | 이유 |
|---|---|---|
| `id` | `created_at`의 밀리초를 앞 48비트에 넣은 UUIDv7 형태로 만드는 시드용 SQL 함수를 쓴다. Postgres 17에는 `uuidv7()`이 없다(`pg_proc`에서 확인) | 앱이 쓰는 UUIDv7과 인덱스 지역성을 같게 한다. `gen_random_uuid()`는 삽입 위치가 흩어져 결과를 왜곡한다 |
| `created_at` | 최근 24개월에 고르게 | 기간 조회와 파티셔닝 실험의 전제다 |
| 재고 | `available + reserved + sold = received`이고, `RECEIVE`·`ADJUST` 이력 합이 `received`와 같다 | CHECK 제약을 통과하고 `재고 불일치` 알림이 울리지 않는다 |
| 상품 상태·카테고리 | `ON_SALE` 90%, `HIDDEN` 5%, `DISCONTINUED` 5%, 카테고리 6종 균등. 한 가게의 상품도 이 비율을 따른다 | 상태·카테고리 인덱스의 선택도를 현실에 가깝게 한다. 상품 번호 `n`으로 `n % 20`을 쓰면 가게 수(1만)가 20의 배수라 한 가게 상품이 모두 같은 상태가 된다 |
| 상품 이름 | 단어 풀에서 조합한다 | S-07의 검색어가 결과를 돌려준다 |
| 주문 상태 | 스케줄 잡이 처리하는 상태는 0건이다. 주문은 `PAID`와 종결 상태(`CANCELLED`, `PAYMENT_FAILED`, `EXPIRED`)만, 가게주문은 `PAID`·`SHIPPED`·`COMPLETED`·`CANCELLED`만 넣는다. 결제 대기·결제 중 주문과 배송완료 가게주문은 넣지 않는다 | 만료·대사 잡(1분)과 자동확정 잡(매시)이 측정 중에 시드를 처리하면 결과가 흔들린다 |
| 비밀번호 | 해시 하나를 미리 만들어 모든 회원에 같은 값을 넣는다 | 건마다 해시하면 적재가 오래 걸린다 |
| 구매확정 품목 | `CONFIRMED` 품목마다 `settlement_item`이 있다 | `InvariantChecker`의 정산 누락 검사(구매확정 품목에 정산 대상이 없으면 위반)를 통과한다 |
| Elasticsearch (S2-1b) | [아키텍처 진화](../02-design/01-architecture-evolution.md)의 Stage 2 절대로 앱을 먼저 띄워 alias `products`를 만든 뒤 `_bulk`로 넣는다 | 시드는 이벤트를 내지 않아 문서가 저절로 생기지 않는다 |

넣은 뒤 `ANALYZE`를 실행하고, [`InvariantChecker`](../../src/test/java/com/myroutine/support/InvariantChecker.java)의 쿼리로 금액 합계·재고 등식 위반이 0건인지 확인한다.

## 3. k6 시나리오

VU 시나리오는 램프업 1분 뒤 5분 유지한다. VU는 응답을 받자마자 다음 요청을 보낸다(대기 시간 없음). 그래서 "300 VU"는 사용자 300명이 아니라 동시 요청 300개로 만든 포화 부하이고, 리포트에 그렇게 적는다. 로컬 실험에서 대기 없는 VU 20개가 초당 약 6,000건을 보냈다.

| 시나리오 | 요구사항 | 요청 | 부하 | 합격 기준 | 읽는 곳 |
|---|---|---|---|---|---|
| S-01 상품 조회 | NFR-PERF-01 | `GET /api/products`(category, cursor) 30%, `GET /api/products/{id}` 70% | 300 VU | p95 < 200ms, 에러율 < 0.1% | k6 요약 |
| S-02 체크아웃 | NFR-PERF-02 | `POST /api/orders/checkout`(매번 새 `Idempotency-Key`). 회원마다 다른 상품 | 100 VU | p95 < 500ms | k6 요약 |
| S-03 인기 상품 경합 | NFR-PERF-03 | 재고 100개 상품 1개를 회원 1,000명이 각자 체크아웃 1회 | 동시 1,000건 | 정확히 100건 성공, 초과판매 0건, p95 측정 | k6 체크, `product.stock` |
| S-04 인증 오버헤드 | NFR-PERF-04 | `GET /api/products/{id}`를 `Authorization` 헤더 없이, 있게 번갈아 실행 | 100 VU | p95 < 10ms (As-Is 방식 대비 수치 비교) | 두 실행의 k6 요약 |
| S-05 정산 배치 | NFR-PERF-05 | `POST /admin/api/settlements/run?period={끝난 달}` 1회 | 1건 | 구매확정 품목 100만 건을 10분 이내, 누락·중복 0건 | 응답 시간, `InvariantChecker.settlementOnce` 쿼리 |
| S-06 Outbox 지연 | NFR-PERF-06 | 판매자 `PATCH /api/shops/{shopId}/products/{id}` 초당 20건, 5분 | 초당 20건 | 평시 p95 < 1초 | `common.outbox_event`의 `sent_at - created_at`에 `percentile_cont(0.95)` |
| S-07 상품 검색 | NFR-PERF-07 | `GET /api/search/products?q={단어 풀}&size=20`, 상품 100만 건 | 100 VU | p95 < 200ms, 에러율 < 0.1% | k6 요약 |

시나리오는 다음 순서로 잰다. S2-1a는 시드·DB 설정·지표 수집까지 포함하고, S2-1a가 끝나면 S2-2·S2-5를 시작할 수 있다.

| 순서 | 시나리오 | 이유 |
|---|---|---|
| S2-1a | S-01, S-02, S-03, S-06 | 개선 실험의 출발점이다. S-01과 S-02는 S2-2에서 고칠 느린 쿼리를 찾고, S-03은 S2-5의 기준값이고, S-06은 릴레이 폴링을 바꿀지 판단하는 근거다 |
| S2-1b | S-04, S-05, S-07 | 요구사항의 목표를 확인하는 측정이다 |

시나리오별로 알아 둘 점은 다음과 같다.

| 시나리오 | 내용 |
|---|---|
| S-02 | 전용 회원마다 배송지와 장바구니 항목을 넣어 둔다. 체크아웃은 장바구니를 건드리지 않아 같은 항목으로 반복할 수 있다. 회원마다 상품이 달라야 한다. 로컬 실험에서 100명이 상품 하나를 주문하자 DB 실행 시간의 81%가 그 재고 행의 UPDATE였다. 경합은 S-03이 잰다. 예약은 결제 TTL 15분 뒤에 풀리므로 상품마다 재고를 5분 처리량보다 넉넉히 둔다 |
| S-03 | 로그인은 비밀번호 해시 비용이 커서 k6의 `setup()`에서 끝내 측정 구간에서 뺀다. 재고 부족 응답은 422 `OUT_OF_STOCK`이라 에러율이 아니라 체크 수로 본다 |
| S-04 | Stage 1에는 게이트웨이가 없어 `JwtAuthenticationFilter`의 JWT 검증과 Redis `tokenVersion` 조회가 같은 일을 한다. 헤더가 있을 때만 이 경로를 타므로 두 실행의 차이가 오버헤드다. 두 분포의 p95 차이이지 요청별 오버헤드의 p95는 아니라는 한계를 리포트에 적는다. As-Is 비교군은 매 요청 회원을 조회하는 실험 브랜치로 잰다([ADR-006](../adr/ADR-006-auth.md)). 이 브랜치는 머지하지 않는다. 모놀리스에는 원 프로젝트의 네트워크 호출이 없어 이 비교는 Redis 조회와 DB 조회의 차이만 보여 준다 |
| S-05 | k6의 요청 `timeout`을 15분으로 늘린다. 지급은 `MockPayoutGateway`라 외부 지급 시간은 수치에 들어가지 않는다 |
| S-06 | 릴레이가 1초 폴링이라 대기만 최대 약 1초다. p95가 기준에 붙을 수 있고, 넘으면 [ADR-003](../adr/ADR-003-outbox-kafka.md)대로 폴링 주기나 CDC를 재검토한다. `created_at`은 트랜잭션 안에서 찍은 앱 시각이라 커밋까지 걸린 시간만큼 실제보다 약간 크게 나온다 |

## 4. 개선 실험 우선순위

단계 번호는 실행 순서가 아니다. 선택과 장단점이 결과로 남는 비교 실험을 먼저 한다.

| 우선순위 | 단계 | 이유 |
|---|---|---|
| 꼭 한다 | S2-5 재고 차감 방식 비교 | 조건부 UPDATE, `FOR UPDATE`, Redis 차감을 같은 조건에서 비교한다([ADR-008](../adr/ADR-008-inventory-reservation.md)). 지금 고른 방식의 성능 근거가 여기서 나온다 |
| 꼭 한다 | S2-2 쿼리 튜닝 | Baseline에서 찾은 목록 커서 조건을 고친다(6절). 뒤 페이지일수록 느려지는 원인이고, S2-3에서 커넥션을 늘리기 전에 고친다 |
| 꼭 한다 | S2-3 커넥션 풀과 예약 작업 스레드 | 첫 작업으로, 측정 중 시드 회원의 가짜 주소(`@seed.test`)로 메일이 실제로 나가지 않게 설정값 하나로 고르는 가짜 메일 발송기를 둔다. 보내지 않고 로그만 남기되 실제 발송처럼 정해진 시간(Baseline에서 잰 한 통 약 3초)을 기다려, 메일 작업이 다른 예약 작업을 막는 상황이 그대로 재현되게 한다(그냥 끄면 메일 작업이 0초에 끝나 고치기 전후를 비교할 수 없다). Baseline의 상품 조회·체크아웃·인기 상품 측정 모두 커넥션 10개가 가득 찼다. 쿼리 튜닝 뒤 풀 크기를 바꿔 가며 재서 DB CPU가 한계에 닿는 지점을 찾는다. 또 `@Scheduled` 작업이 한 번에 하나만 돌아 메일 발송(한 번에 약 5분)이 만료·outbox 릴레이를 막았으므로, 동시에 도는 작업 수를 늘리고 메일이 쌓인 상태에서 만료가 1분마다 도는지 확인한다. 플랫폼 스레드와 가상 스레드 비교는 여유가 있으면 한다 |
| 꼭 할 셋 뒤에 여유가 있으면 한다 | outbox 릴레이 지연 | Baseline에서 이벤트 지연 p95 1.5초(기준 1초 실패). 릴레이의 쉬는 시간(`fixedDelay = 1000`)을 줄이고, 한 건씩 `.get()`으로 기다리던 발송을 묶음을 먼저 다 보낸 뒤 응답을 한꺼번에 기다리는 방식으로 바꾼다. `.get()`을 그냥 빼면 Kafka가 받기 전에 발송 완료로 표시되어 실패한 이벤트를 잃으므로, 응답을 받은 것만 발송 완료로 표시한다 |
| 측정에서 필요가 드러나면 한다 | S2-4 캐시, S2-6 Read Replica, S2-7 파티셔닝 | S2-6은 같은 물리 서버라 처리량 이득을 말할 수 없고 복제 지연 대응만 남는다. S2-7은 시드 비용이 크다 |
| 하지 않는다 | 샤딩 | 물리 서버 한 대에서는 쓰기 한계를 제대로 보여 주기 어렵다 |

## 5. S2-1a Baseline: 시드·지표 수집·첫 측정

**목표**: 운영 VM에 시드를 넣고 DB·컨테이너 지표를 모으는 상태에서 S-01, S-02, S-03, S-06을 재서 S2-2·S2-5의 출발점이 되는 Baseline 리포트를 남긴다.

**왜 지금**: 개선은 측정한 병목에서 시작한다(4절). 느린 쿼리는 `pg_stat_statements`가, 락 대기와 DB CPU는 exporter가 보여 준다. 이것들이 없으면 S2-2에서 무엇을 고칠지 고를 근거가 없다.

**브랜치**: `feature/s2-1-baseline`. PR에는 compose·Prometheus 설정, Grafana 패널, `perf/seed/`, `perf/k6/`가 들어간다. 측정 뒤 리포트(`docs/perf/s2-1a-baseline.md`)는 사용자가 넘긴 결과로 Claude가 쓴다.

### 5.1 새로 등장

**k6**는 JavaScript로 쓴 시나리오를 Go 런타임이 실행하는 부하 도구다. 개발 PC에 v1.4.2가 있다.

| 개념 | 뜻 | 이 단계에서 |
|---|---|---|
| VU(가상 사용자) | 스크립트의 `default` 함수를 반복 실행하는 실행 단위. VU끼리 변수를 나누지 않는다 | S-01·S-02 |
| 반복(iteration) | `default` 함수 한 번 실행 | |
| 시나리오·실행기(executor) | `options.scenarios`에 부하 모양을 적는다. `ramping-vus`는 VU 수를 단계별로 바꾼다. `per-vu-iterations`는 VU마다 정해진 횟수만 돌고 모든 VU가 함께 시작한다. `constant-arrival-rate`는 응답 시간과 상관없이 초당 시작 횟수를 지킨다 | S-01·S-02 `ramping-vus`, S-03 `per-vu-iterations`, S-06 `constant-arrival-rate` |
| 닫힌 모델과 열린 모델 | VU 실행기는 응답이 와야 다음 요청을 보내 서버가 느려지면 부하도 준다(닫힌 모델). 도착률 실행기는 느려져도 요청을 계속 보낸다(열린 모델). 보낼 VU가 모자라면 그 반복을 버리고 `dropped_iterations`로 센다 | S-06은 `dropped_iterations`가 0이어야 초당 20건이 맞다 |
| init 코드 | 함수 밖 최상위 코드. VU마다 한 번 돈다. 파일은 여기서만 읽을 수 있다(`open`) | |
| `SharedArray` | init에서 한 번 읽은 배열을 모든 VU가 복사 없이 나눠 읽는다(`k6/data`) | 상품 ID 파일 |
| `setup()`·`teardown()` | 부하 전후에 한 번 돈다. `setup`의 반환값이 `default(data)`의 인자로 간다. `setup`의 요청도 결과 지표에 들어가므로 태그로 나눈다. 기본 제한 시간은 60초라 `setupTimeout`으로 늘린다 | 로그인 |
| 태그와 하위 지표 | 요청에 `tags: { name: 'detail' }`을 붙이면 `http_req_duration{name:detail}`로 따로 볼 수 있다. 하위 지표는 `thresholds`에 적은 것만 요약에 나온다. 기준 없이 보기만 하려면 빈 배열 `[]`을 적는다 | |
| `thresholds` | 지표의 합격 조건. 하나라도 어기면 요약에 ✗가 찍히고 k6의 종료 코드가 0이 아니다 | NFR 합격 기준 |
| `check` | 응답 검사. 실패해도 실행은 계속되고 `checks` 비율로 센다 | |
| `http_req_failed` | 기대한 상태 코드가 아닌 응답의 비율. 기본은 200~399가 성공이다. `http.setResponseCallback(http.expectedStatuses(200, 201, 422))`로 바꿀 수 있다. 이 설정은 스크립트의 모든 요청에 적용되므로 `setup()`의 로그인·조회(200)도 넣어야 한다 | S-03은 422 `OUT_OF_STOCK`도 기대한 응답이다 |
| `http.batch` | 요청 여러 개를 한 VU에서 병렬로 보낸다 | S-03 `setup`의 로그인 1,000건 |
| `crypto.randomUUID()` | import 없이 쓰는 전역 함수 | `Idempotency-Key` |
| `-e KEY=값`, `__ENV.KEY` | 실행할 때 값을 넘긴다 | 주소, VU 수, 시간 |
| `--summary-export 파일` | 끝 요약을 JSON으로 저장한다. `metrics.{지표}`에 `avg`·`med`·`p(95)`·`p(99)`·`max`(`summaryTrendStats`로 고른 값)가 있다. 이 파일의 `thresholds` 값은 **어겼으면 `true`**다(통과면 `false`). 화면 요약은 통과를 ✓로 보여 준다 | 리포트의 원자료 |

**`pg_stat_statements`**: Postgres 확장. 파라미터만 다른 문장을 하나로 묶어 실행 횟수(`calls`), 누적·평균 시간(`total_exec_time`·`mean_exec_time`, ms), 돌려준 행 수(`rows`)를 쌓는다. 서버를 시작할 때 `shared_preload_libraries`로 올려야 하고, DB마다 `CREATE EXTENSION`을 한 번 실행해야 뷰가 생긴다. `select pg_stat_statements_reset()`으로 비운다.

**slow query log**: `log_min_duration_statement=100ms`이면 100ms 넘게 걸린 문장을 실행 시간과 함께 Postgres 로그(컨테이너 stdout)에 남긴다. `pg_stat_statements`는 파라미터를 `$1`로 바꾸지만 이 로그는 실제 값을 보여 줘서, 같은 쿼리가 어떤 값에서 느린지 볼 때 쓴다.

**postgres_exporter**(`prometheuscommunity/postgres-exporter:v0.20.1`): Postgres 통계 뷰를 Prometheus 지표로 내보낸다. `pg_stat_activity_count{state, wait_event_type}`, `pg_locks_count{mode}`, `pg_stat_database_xact_commit`, `pg_stat_database_blks_hit`·`blks_read`, `pg_stat_database_deadlocks`가 있다(로컬에서 확인).

**cAdvisor**(`ghcr.io/google/cadvisor:v0.60.6`): 컨테이너별 CPU·메모리를 `container_cpu_usage_seconds_total`, `container_memory_working_set_bytes`로 내보낸다. 컨테이너는 `name` 라벨로 구분한다. 운영 VM에서는 `name="myroutine-ops-postgres-1"`처럼 compose 컨테이너 이름이 붙는다. 맥의 Docker Desktop에서는 Docker 소켓을 찾지 못해 라벨 없는 전체 합계만 나온다.

### 5.2 할 일

**1) Postgres 설정** (`docker/docker-compose.ops.yml`의 `postgres`)

```yaml
    command:
      - postgres
      - -c
      - shared_preload_libraries=pg_stat_statements
      - -c
      - pg_stat_statements.track=top
      - -c
      - log_min_duration_statement=100ms
      - -c
      - track_io_timing=on
```

- `track=top`은 클라이언트가 보낸 문장만 센다(함수 안 문장은 빼고). `track_io_timing`은 `EXPLAIN (ANALYZE, BUFFERS)`와 `pg_stat_statements`에 디스크 읽기 시간을 채운다. S2-2에서 쓴다.
- 확장은 마이그레이션이 아니라 VM에서 한 번 만든다. 앱 스키마가 아니라 측정 도구이고, Testcontainers의 Postgres에는 preload가 없다.

```bash
IMAGE_TAG=$(cat /opt/myroutine/deployed-sha) docker compose --env-file /opt/myroutine/.env -f docker/docker-compose.ops.yml exec postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d my_routine -c "create extension if not exists pg_stat_statements"'
```

- 운영 compose는 `app.image`의 `IMAGE_TAG`가 필수라, `exec`처럼 app을 건드리지 않는 명령도 값이 없으면 파일 해석에서 실패한다(`required variable IMAGE_TAG is missing a value`). 지금 배포된 sha는 `scripts/deploy.sh`가 `/opt/myroutine/deployed-sha`에 남긴다.
- 확장은 1)의 `command` 변경을 배포한 뒤에 만든다. preload 없이도 `CREATE EXTENSION`은 되지만 뷰를 조회하면 에러가 난다.

**2) exporter와 수집 대상** (`docker/docker-compose.ops.yml`, `docker/prometheus/prometheus.ops.yml`)

| 서비스 | 설정 |
|---|---|
| `postgres-exporter` | 이미지 `prometheuscommunity/postgres-exporter:v0.20.1`. 환경 변수 `DATA_SOURCE_URI: postgres:5432/my_routine?sslmode=disable`, `DATA_SOURCE_USER`·`DATA_SOURCE_PASS`는 `.env`의 DB 계정. 포트는 열지 않는다(Prometheus가 compose 네트워크로 읽는다). `depends_on: postgres` |
| `cadvisor` | 이미지 `ghcr.io/google/cadvisor:v0.60.6`, `privileged: true`, 볼륨 `/:/rootfs:ro`, `/var/run:/var/run:ro`, `/sys:/sys:ro`, `/var/lib/docker/:/var/lib/docker:ro`. 포트는 열지 않는다 |
| Prometheus 수집 대상 | `job_name: postgres` → `postgres-exporter:9187`, `job_name: cadvisor` → `cadvisor:8080`. 두 exporter는 Prometheus 기본 경로 `/metrics`로 내보내므로 `metrics_path`를 적지 않는다(`/actuator/prometheus`는 Spring 앱의 경로다). 기존 `myroutine` job은 그대로 둔다 |

**3) Grafana 대시보드** (`docker/grafana/provisioning/dashboards/perf.json`, 제목 "성능 측정")

기존 "서비스 개요"에 요청률·p95·HikariCP가 있어 여기에는 DB·컨테이너 패널만 둔다.

| 패널 | PromQL | 볼 것 |
|---|---|---|
| DB 트랜잭션/초 | `sum(rate(pg_stat_database_xact_commit{datname="my_routine"}[1m]))` | k6 처리량과 같이 오르는지 |
| 연결 상태 | `sum by (state) (pg_stat_activity_count{datname="my_routine"})` | `active`가 HikariCP 최대(10)에 붙는지 |
| 대기 중인 연결 | `sum by (wait_event_type) (pg_stat_activity_count{datname="my_routine", wait_event_type!=""})` | `Lock`이 S-03에서 생기는지(로컬 평시에는 `Client`만 보였다) |
| 락 모드 | `sum by (mode) (pg_locks_count{datname="my_routine"})` | `rowexclusivelock` 증가 |
| 버퍼 적중률 | `sum(rate(pg_stat_database_blks_hit{datname="my_routine"}[1m])) / (sum(rate(pg_stat_database_blks_hit{datname="my_routine"}[1m])) + sum(rate(pg_stat_database_blks_read{datname="my_routine"}[1m])))` | 1보다 많이 낮으면 디스크를 읽는다 |
| 컨테이너 CPU | `sum by (name) (rate(container_cpu_usage_seconds_total{name=~"myroutine-ops-(app|postgres)-1"}[1m]))` | 코어 수 단위. 어느 쪽이 먼저 포화되는지 |
| 컨테이너 메모리 | `container_memory_working_set_bytes{name=~"myroutine-ops-(app|postgres)-1"}` | |


**4) 시드 SQL** (`perf/seed/`)

파일은 실행 순서대로 번호를 붙인다: `00-functions.sql`, `01-members-shops.sql`, `02-products.sql`, `03-orders.sql`, `04-settlements.sql`, `05-k6-fixtures.sql`, `99-cleanup.sql`과 확인용 `check.sql`. 정리 파일을 `99`로 둔 것은 `0*.sql`로 순서대로 돌릴 때 불변식 확인보다 먼저 실행되지 않게 하려는 것이다. 건수는 psql 변수로 받아 1/100 규모로 먼저 돌려 볼 수 있게 한다.

```bash
psql -v ON_ERROR_STOP=1 -v n_member=1000000 -v n_shop=10000 -v n_product=1000000 -v n_order=10000000 \
     -v password_hash='{미리 만든 해시}' -f 01-members-shops.sql   # SQL 안에서는 :n_member, 문자열은 :'password_hash'
```

`00-functions.sql`의 UUIDv7 함수. `gen_random_uuid()`(v4)의 앞 48비트를 `ts`의 밀리초로 덮고, 버전 자리(4비트 `0100`)의 비트 52·53을 켜 `0111`(7)로 만든다.

```sql
create schema if not exists seed;

create or replace function seed.uuid_v7(ts timestamptz) returns uuid language sql volatile as $$
  select encode(set_bit(set_bit(overlay(uuid_send(gen_random_uuid())
         placing substring(int8send((extract(epoch from ts) * 1000)::bigint) from 3) from 1 for 6), 52, 1), 53, 1), 'hex')::uuid
$$;
```

비밀번호 해시: `htpasswd -bnBC 10 "" 'Seed1234!' | tr -d ':\n'`. `$2y$10$...`가 나오고, 앱의 `BCryptPasswordEncoder`(기본 강도 10)가 이 형식으로 로그인되는 것을 확인했다.

적재 방법과 순서:

| 규칙 | 내용 |
|---|---|
| 번호 → id 대응표 | 엔티티마다 `seed` 스키마에 `unlogged` 작업 테이블 `(n int primary key, ts, id uuid)`를 먼저 만들고, 실제 테이블과 다른 엔티티는 번호로 이 표를 조인해 id를 얻는다. `unlogged`는 WAL을 쓰지 않아 빠르다 |
| 세션 설정 | 파일 첫 줄에 `set synchronous_commit = off`. 커밋마다 디스크 동기화를 기다리지 않는다 |
| 순서 | 회원 → 배송지 → 가게(회원 1~1만이 주인, `role = 'SELLER'`) → 상품·재고·입고 이력 → 주문 골격 → 가게주문 → 품목 → 주문 합계 → `orders` → `shop_order` → `order_line` → `payment` → `settlement_item` → 지난 달들의 `settlement`와 `settlement_id` 연결 |
| 상품의 가게 | 상품 `n`의 가게는 `(n - 1) % n_shop + 1`. 그러면 가게 `s`의 상품은 `s + n_shop × t`(t = 0~99)다 |
| 상품 상태 | `(n - 1) / n_shop`(가게 안 순번)으로 정한다. 2절의 함정 때문이다 |
| 주문 | `created_at`은 `now() - 1시간`에서 24개월 사이. 상태는 `PAID` 85%, `CANCELLED`·`PAYMENT_FAILED`·`EXPIRED` 각 5% |
| 가게주문 | 주문당 1~2개(평균 1.5). 같은 주문 안에서 가게가 겹치지 않는다(`UK(order_id, shop_id)`). 주문이 `PAID`가 아니면 `CANCELLED`. `PAID` 주문은 최근 10일이면 `PAID`, 아니면 `PAID` 15%·`SHIPPED` 5%·`COMPLETED` 80% |
| 품목 | 가게주문당 1~2개(평균 5/3, 그래서 주문당 2.5). 상품은 그 가게의 상품에서 고르고 `unit_price`·`product_name`은 상품 값을 복사한다. 상태는 가게주문을 따라 `COMPLETED → CONFIRMED`, `confirmed_at = created_at + 10일` |
| 주문 합계 | 품목 작업 테이블에서 `sum(price × quantity)`를 먼저 구해 `orders.total_amount`에 넣는다. FK 때문에 `orders`를 품목보다 먼저 넣어야 해서다 |
| 결제 | `PAID` → `APPROVED`(`approved_at`, `method`), `PAYMENT_FAILED` → `FAILED`. 취소·만료 주문은 결제 행이 없다. `pg_order_id`·`payment_key`는 유일해야 한다 |
| 정산 | `CONFIRMED` 품목마다 `settlement_item`. 지난달보다 앞선 달은 `(shop_id, 달)`마다 `PAID` 정산을 만들고 품목의 `settlement_id`를 채운다. 달 경계는 정산 잡과 같이 `Asia/Seoul` 기준이다: `(date_trunc('month', confirmed_at at time zone 'Asia/Seoul'))::date` |
| 마무리 | `05`의 끝에서 `ANALYZE`. `check.sql`로 불변식 8개가 모두 0건인지 본 뒤 `99-cleanup.sql`에서 `drop schema seed cascade` |

`05-k6-fixtures.sql`:

| 데이터 | 내용 |
|---|---|
| S-02 | 회원 `k6-co-{1..100}@seed.test`, 각자 기본 배송지, 각자 다른 상품 `k6 체크아웃 상품 {i}`(재고 10만) 1개가 담긴 장바구니 |
| S-03 | 회원 `k6-hot-{1..1000}@seed.test`, 각자 기본 배송지, `k6 인기 상품`(재고 100) 1개가 담긴 장바구니 |
| 재고 | 전용 상품마다 `stock`과 `RECEIVE` 이력을 같이 넣는다 |

k6가 읽는 데이터 파일(커밋하지 않는다. `.gitignore`에 `perf/k6/data/`, `perf/k6/results/`):

```sql
-- perf/k6/data/product-ids.json: 판매 중인 상품 10만 개
select json_agg(id) from (select id from product.product where status = 'ON_SALE' and name not like 'k6 %' order by random() limit 100000) t;
-- perf/k6/data/seller-products.json: 판매자 u1의 판매 중인 상품
select json_agg(json_build_object('shopId', s.id, 'productId', p.id))
from product.product p join shop.shop s on s.id = p.shop_id join member.member m on m.id = s.member_id
where m.email = 'u1@seed.test' and p.status = 'ON_SALE' and p.name not like 'k6 %';
```

`psql -tA -c "..." > 파일`로 저장한다.

**5) k6 스크립트** (`perf/k6/`)

아래 네 파일은 로컬에서 실행해 확인했다(VU 수와 시간은 줄여서). 주소는 `-e BASE_URL=http://{VM IP}:8080`으로 넘긴다.

`s01-product-read.js`:

```javascript
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 300);
const MAX_PAGES = Number(__ENV.MAX_PAGES || 10);
const CATEGORIES = ['FOOD', 'HEALTH', 'BEAUTY', 'LIVING', 'PET', 'ETC'];
const productIds = new SharedArray('productIds', () => JSON.parse(open('./data/product-ids.json')));

export const options = {
  scenarios: {
    read: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP || '1m', target: VUS },
        { duration: __ENV.HOLD || '5m', target: VUS },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<200'],
    http_req_failed: ['rate<0.001'],
    'http_req_duration{name:list}': ['p(95)<200'],
    'http_req_duration{name:detail}': ['p(95)<200'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

let cursor = null;
let category = null;
let page = 0;

export default function () {
  if (Math.random() < 0.3) {
    if (cursor === null || page === MAX_PAGES) {
      category = CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)];
      cursor = null;
      page = 0;
    }
    const query = `category=${category}&size=20` + (cursor ? `&cursor=${encodeURIComponent(cursor)}` : '');
    const res = http.get(`${BASE_URL}/api/products?${query}`, { tags: { name: 'list' } });
    check(res, { 'list 200': (r) => r.status === 200 });
    cursor = res.status === 200 ? res.json('nextCursor') : null;
    page += 1;
  } else {
    const id = productIds[Math.floor(Math.random() * productIds.length)];
    const res = http.get(`${BASE_URL}/api/products/${id}`, { tags: { name: 'detail' } });
    check(res, { 'detail 200': (r) => r.status === 200 });
  }
}
```

- `cursor`·`category`·`page`는 VU마다 따로 있는 변수라, 한 VU가 같은 카테고리의 다음 페이지를 이어 읽다가 `MAX_PAGES`(기본 10)페이지를 읽거나 마지막 페이지(`nextCursor`가 `null`)에 닿으면 새 카테고리의 첫 페이지로 돌아간다. 상한이 없으면 VU가 측정 내내 더 깊이 들어가(6분에 약 600페이지) 커서 비용이 깊이에 비례하는 쿼리에서는 처리량이 시간에 따라 떨어지고, 결과가 측정 시간에 따라 달라진다. 깊은 페이지 비용을 따로 볼 때는 `-e MAX_PAGES=100000`처럼 상한을 사실상 없앤다.

`s02-checkout.js`:

```javascript
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 100);
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  setupTimeout: '2m',
  scenarios: {
    checkout: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP || '1m', target: VUS },
        { duration: __ENV.HOLD || '5m', target: VUS },
      ],
    },
  },
  thresholds: {
    'http_req_duration{name:checkout}': ['p(95)<500'],
    'http_req_failed{name:checkout}': ['rate<0.001'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const buyers = [];
  for (let i = 1; i <= VUS; i++) {
    const login = http.post(`${BASE_URL}/api/auth/login`,
      JSON.stringify({ email: `k6-co-${i}@seed.test`, password: 'Seed1234!' }),
      { headers: JSON_HEADERS, tags: { name: 'setup' } });
    const auth = { Authorization: `Bearer ${login.json('accessToken')}` };
    const cart = http.get(`${BASE_URL}/api/cart`, { headers: auth, tags: { name: 'setup' } });
    const addresses = http.get(`${BASE_URL}/api/members/me/addresses`, { headers: auth, tags: { name: 'setup' } });
    buyers.push({
      token: login.json('accessToken'),
      cartItemId: cart.json('items.0.cartItemId'),
      addressId: addresses.json('0.id'),
    });
  }
  return { buyers };
}

export default function (data) {
  const buyer = data.buyers[(__VU - 1) % data.buyers.length];
  const res = http.post(`${BASE_URL}/api/orders/checkout`,
    JSON.stringify({ cartItemIds: [buyer.cartItemId], addressId: buyer.addressId }),
    {
      headers: { ...JSON_HEADERS, Authorization: `Bearer ${buyer.token}`, 'Idempotency-Key': crypto.randomUUID() },
      tags: { name: 'checkout' },
    });
  check(res, { 'checkout 201': (r) => r.status === 201 });
}
```

- 로그인 100건이 `setup`에서 약 7초 걸렸다(로컬). access 토큰 TTL이 15분이라 6분 측정 동안 만료되지 않는다.
- `__VU`는 1부터 시작하는 VU 번호다. VU 하나가 회원 한 명을 맡는다.

`s03-hot-product.js`:

```javascript
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BUYERS = Number(__ENV.BUYERS || 1000);
const JSON_HEADERS = { 'Content-Type': 'application/json' };
const succeeded = new Counter('checkout_succeeded');
const outOfStock = new Counter('checkout_out_of_stock');

http.setResponseCallback(http.expectedStatuses(200, 201, 422));

export const options = {
  setupTimeout: '10m',
  scenarios: {
    rush: { executor: 'per-vu-iterations', vus: BUYERS, iterations: 1, maxDuration: '2m' },
  },
  thresholds: {
    checkout_succeeded: ['count==100'],
    'http_req_duration{name:checkout}': [],
    'http_req_failed{name:checkout}': ['rate==0'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function login(i) {
  return ['POST', `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: `k6-hot-${i}@seed.test`, password: 'Seed1234!' }),
    { headers: JSON_HEADERS, tags: { name: 'setup' } }];
}

export function setup() {
  const buyers = [];
  for (let start = 1; start <= BUYERS; start += 50) {
    const logins = http.batch(Array.from({ length: Math.min(50, BUYERS - start + 1) }, (_, k) => login(start + k)));
    const tokens = logins.map((r) => r.json('accessToken'));
    const carts = http.batch(tokens.map((t) => ['GET', `${BASE_URL}/api/cart`, null, { headers: { Authorization: `Bearer ${t}` }, tags: { name: 'setup' } }]));
    const addresses = http.batch(tokens.map((t) => ['GET', `${BASE_URL}/api/members/me/addresses`, null, { headers: { Authorization: `Bearer ${t}` }, tags: { name: 'setup' } }]));
    tokens.forEach((token, k) => buyers.push({
      token,
      cartItemId: carts[k].json('items.0.cartItemId'),
      addressId: addresses[k].json('0.id'),
    }));
  }
  return { buyers };
}

export default function (data) {
  const buyer = data.buyers[__VU - 1];
  const res = http.post(`${BASE_URL}/api/orders/checkout`,
    JSON.stringify({ cartItemIds: [buyer.cartItemId], addressId: buyer.addressId }),
    {
      headers: { ...JSON_HEADERS, Authorization: `Bearer ${buyer.token}`, 'Idempotency-Key': crypto.randomUUID() },
      tags: { name: 'checkout' },
    });
  if (res.status !== 201 && res.status !== 422) console.log(`status=${res.status} error=${res.error} code=${res.error_code}`);
  if (res.status === 201) succeeded.add(1);
  if (res.status === 422 && res.json('code') === 'OUT_OF_STOCK') outOfStock.add(1);
  check(res, { '201 or OUT_OF_STOCK': (r) => r.status === 201 || (r.status === 422 && r.json('code') === 'OUT_OF_STOCK') });
}
```

- 로그인은 비밀번호 해시 비용이 커서 한 건씩 하면 1,000건이 기본 `setup` 제한 60초를 넘는다. 50건씩 `http.batch`로 보내 로컬에서 약 12초였다.
- `per-vu-iterations`는 `setup`이 끝난 뒤 1,000 VU를 함께 시작한다. 로컬에서 체크아웃 1,000건이 약 1초 안에 끝났고 성공 100건, `OUT_OF_STOCK` 900건, 재고는 `available 0, reserved 100`이었다.
- `Counter`는 직접 만드는 지표다. `count==100` 기준이 "정확히 100건 성공"을 k6 결과로 판정한다.
- 로컬에서 세 번 중 한 번은 1,000건 중 9건이 서버에 닿기 전에 실패했다(앱 로그에 오류 없음, 결과는 성공 100건·초과판매 0건으로 정상). 1,000개 연결이 한꺼번에 열려 맥의 접속 대기열(`somaxconn` 128)과 Tomcat `accept-count`(기본 100)를 넘친 것으로 보고, 재현되지 않아 원인을 확정하지 못했다. 그래서 201·422가 아닌 응답은 `console.log`로 `res.error`·`res.error_code`(k6의 연결 오류 번호)를 남긴다. VM에서도 나오면 리포트에 건수와 오류를 적고, `http_req_failed` 기준 실패는 서버 결함이 아니라 연결 계층 문제로 구분한다.

`s06-outbox-lag.js`:

```javascript
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JSON_HEADERS = { 'Content-Type': 'application/json' };
const products = new SharedArray('sellerProducts', () => JSON.parse(open('./data/seller-products.json')));
const conflicts = new Counter('update_conflict');

http.setResponseCallback(http.expectedStatuses(200, 409));

export const options = {
  scenarios: {
    update: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 20),
      timeUnit: '1s',
      duration: __ENV.DURATION || '5m',
      preAllocatedVUs: 20,
      maxVUs: 50,
    },
  },
  thresholds: {
    'http_req_duration{name:update}': [],
    'http_req_failed{name:update}': ['rate<0.001'],
    dropped_iterations: ['count==0'],
    update_conflict: [],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const login = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: __ENV.SELLER_EMAIL || 'u1@seed.test', password: 'Seed1234!' }),
    { headers: JSON_HEADERS, tags: { name: 'setup' } });
  return { token: login.json('accessToken'), startedAt: new Date().toISOString() };
}

export default function (data) {
  const p = products[Math.floor(Math.random() * products.length)];
  const res = http.patch(`${BASE_URL}/api/shops/${p.shopId}/products/${p.productId}`,
    JSON.stringify({ price: 1000 * (1 + Math.floor(Math.random() * 99)) }),
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${data.token}` }, tags: { name: 'update' } });
  if (res.status !== 200 && res.status !== 409) console.log(`status=${res.status} error=${res.error} code=${res.error_code}`);
  if (res.status === 409 && res.json('code') === 'CONFLICT_RETRY') conflicts.add(1);
  check(res, { '200 or CONFLICT_RETRY': (r) => r.status === 200 || (r.status === 409 && r.json('code') === 'CONFLICT_RETRY') });
}

export function teardown(data) {
  console.log(`started_at=${data.startedAt} finished_at=${new Date().toISOString()}`);
}
```

- 상품 90개를 초당 20건씩 무작위로 고르므로 같은 상품을 동시에 고치는 요청이 생기고, 늦은 쪽은 낙관적 락 충돌로 409 `CONFLICT_RETRY`를 받는다(VM에서 6,000건 중 14건). 설계대로의 응답이라 실패가 아니라 `update_conflict`로 센다. 그 밖의 응답은 `console.log`로 남긴다.
- 가격 변경 한 건이 `product-upserted`와 `product-price-changed` 두 이벤트를 만든다(값이 같으면 가격 변경 이벤트는 없다). `teardown`이 찍는 시각 구간으로 아래 SQL을 돌린다.

```sql
select topic, count(*) events, count(*) filter (where sent_at is null) unsent,
       round((percentile_cont(0.5)  within group (order by extract(epoch from sent_at - created_at)) * 1000)::numeric) p50_ms,
       round((percentile_cont(0.95) within group (order by extract(epoch from sent_at - created_at)) * 1000)::numeric) p95_ms,
       round((max(extract(epoch from sent_at - created_at)) * 1000)::numeric) max_ms
from common.outbox_event
where created_at between '{started_at}' and '{finished_at}'
group by rollup(topic);
```

- 로컬 30초 실행에서 p95가 약 1,350ms였다. 릴레이가 `fixedDelay = 1000`이라 앞 실행이 끝난 뒤 1초를 쉬고 다시 읽기 때문이다(대기만 최대 1초에 처리 시간이 더해진다). Baseline에서도 기준(1초)을 넘을 가능성이 크고, 넘으면 3절 S-06의 재검토 대상이다.

**6) 측정 절차** (운영 VM)

| 순서 | 할 일 |
|---|---|
| 1 | 1)·2)·3)과 `perf/`를 머지해 배포하고 확장을 만든다. Prometheus 설정이 바뀌었으면 `docker restart myroutine-ops-prometheus-1`([런북](../runbook.md)). Targets에서 `postgres`·`cadvisor`가 UP인지 본다 |
| 2 | 사전 확인: `check.sql`을 돌려 결과를 저장한다(아래 명령). 충돌이 0이 아니면 멈춘다 |
| 3 | Proxmox 스냅샷. 시드를 다시 넣거나 측정 뒤 원래대로 돌릴 때 여기로 되돌린다 |
| 4 | 시드 `00`~`05`를 넣고, `check.sql`을 다시 돌려 불변식 0건과 잡 대상이 시드 전과 같은지 본다. 시드 뒤 충돌 두 값은 시드가 넣은 행 수(회원 100만 1,100, 가게 1만)가 나오는 것이 정상이다. 맞으면 `99-cleanup.sql` → `vacuum (analyze)` → 다시 Proxmox 스냅샷 |
| 5 | k6 데이터 파일을 뽑아 개발 PC의 `perf/k6/data/`에 두고, `mkdir -p perf/k6/results`(아래 명령) |
| 6 | 시나리오마다: `select pg_stat_statements_reset()` → k6 실행(`--summary-export results/{시나리오}.json`) → 아래 상위 20문장을 파일로 저장 → Grafana에서 실행 구간 패널 값 기록 → 2분 쉼 |
| 7 | 순서는 S-01 → S-06 → S-02 → (4 끝의 스냅샷으로 되돌림, S-01 연결 확인으로 데움) → S-03 → 앱 정지 → 되돌림. 만료 잡은 1분마다 100건만 풀어 S-02가 만든 결제 대기 주문 수십만 건을 기다릴 수 없다. 또 결제 대기 주문은 15분 뒤 만료되며 알림을 만들고, 운영 앱(`prod`)은 그 알림을 시드 회원의 가짜 주소(`@seed.test`)로 Gmail SMTP를 통해 실제로 보낸다. 그래서 S-02·S-03 뒤에는 15분 안에 앱을 멈추거나 되돌린다 |

기존 운영 데이터는 지우지 않고 그 위에 시드를 넣는다. 수백 건 규모라 측정에 주는 영향이 작고, 관리자 계정(S2-1b의 S-05가 쓴다)이 남아야 하며, 스냅샷으로 되돌릴 수 있다. 시드 전 테이블별 건수를 리포트에 적는다.

VM의 리포 체크아웃(러너 작업 폴더)에서 실행한다. `PASSWORD_HASH`는 DB 비밀번호가 아니라 시드 회원의 로그인 비밀번호 `Seed1234!`를 BCrypt로 해시한 값이다. 개발 PC에서 `htpasswd -bnBC 10 "" 'Seed1234!' | tr -d ':\n'`로 만든다. 시드는 이 해시를 모든 회원의 `password_hash`에 넣고, k6는 평문 `Seed1234!`로 로그인한다. DB 비밀번호는 필요 없다(컨테이너 안의 `psql`은 비밀번호를 묻지 않는다).

```bash
cd ~/actions-runner/_work/myroutine-v2/myroutine-v2
PG='docker exec -i myroutine-ops-postgres-1 sh -c'
PASSWORD_HASH='$2y$10$...'

# 2·4. 사전·사후 확인 (결과를 파일로 남긴다)
$PG 'psql -U "$POSTGRES_USER" -d my_routine -f -' < perf/seed/check.sql | tee ~/seed-check-before.txt

# 4. 시드 (00~05, 실패하면 멈춘다)
for f in perf/seed/0*.sql; do
  echo "== $f"
  $PG 'psql -U "$POSTGRES_USER" -d my_routine -v ON_ERROR_STOP=1 \
       -v n_member=1000000 -v n_shop=10000 -v n_product=1000000 -v n_order=10000000 -v password_hash="$0" -f -' "$PASSWORD_HASH" < "$f" || break
done 2>&1 | tee ~/seed-run.txt

$PG 'psql -U "$POSTGRES_USER" -d my_routine -f -' < perf/seed/check.sql | tee ~/seed-check-after.txt
$PG 'psql -U "$POSTGRES_USER" -d my_routine -f -' < perf/seed/99-cleanup.sql

# 4. 대량 적재 뒤 정리 (끝날 때까지 기다린다)
$PG 'psql -U "$POSTGRES_USER" -d my_routine -c "\timing on" -c "vacuum (analyze)"' 2>&1 | tee ~/seed-vacuum.txt
$PG 'psql -U "$POSTGRES_USER" -d my_routine -c "select relid::regclass, phase from pg_stat_progress_vacuum"'
```

- 해시는 `sh -c`의 첫 인자(`$0`)로 넘긴다. 해시에 `$`가 들어 있어 명령 문자열 안에 직접 넣으면 셸이 값을 바꾼다.
- 시드는 두 번 넣을 수 없다(이메일·닉네임 유일 제약). 다시 넣으려면 3의 스냅샷으로 되돌린다. 측정만 다시 하려면 4 끝의 스냅샷으로 되돌린다(S-02·S-03이 주문을 만들고 재고를 줄인다). RAM 없는 스냅샷이라 되돌리면 VM이 새로 부팅해 캐시가 비므로, S-01 연결 확인(VU 10, 30초)을 한 번 돌려 데운 뒤 같은 순서로 잰다.
- `vacuum (analyze)`: 대량 INSERT 직후에는 행마다 "커밋됐는지" 표시(hint bit)와 가시성 맵이 비어 있어, 처음 읽는 쿼리가 페이지를 다시 쓰고 인덱스 전용 스캔도 테이블을 읽는다. 또 Postgres는 INSERT가 많이 쌓인 테이블에 autovacuum을 스스로 돌리는데, 이것이 측정 도중에 돌면 디스크 부하가 섞인다. 측정 전에 한 번 돌려 두고, `pg_stat_progress_vacuum`이 빈 것(돌고 있는 vacuum 없음)을 확인한 뒤 측정한다.
- 파일마다 `\timing on`이라 문장별 시간이 `seed-run.txt`에 남는다. 전체 규모의 소요 시간은 확인하지 못했다(1/100 규모는 로컬에서 16초). 리포트에 적는다.
- k6 데이터 파일은 VM에서 뽑아 개발 PC로 옮긴다. 쿼리는 4)의 "k6가 읽는 데이터 파일"과 같다.

```bash
# VM
$PG 'psql -U "$POSTGRES_USER" -d my_routine -tA' > ~/product-ids.json <<'SQL'
select json_agg(id) from (select id from product.product where status = 'ON_SALE' and name not like 'k6 %' order by random() limit 100000) t;
SQL
$PG 'psql -U "$POSTGRES_USER" -d my_routine -tA' > ~/seller-products.json <<'SQL'
select json_agg(json_build_object('shopId', s.id, 'productId', p.id))
from product.product p join shop.shop s on s.id = p.shop_id join member.member m on m.id = s.member_id
where m.email = 'u1@seed.test' and p.status = 'ON_SALE' and p.name not like 'k6 %';
SQL

# 개발 PC
mkdir -p perf/k6/data perf/k6/results
scp {VM}:~/product-ids.json {VM}:~/seller-products.json perf/k6/data/
```

- 측정 시각: 시드가 커진 테이블을 훑는 스케줄 잡과 겹치지 않게 한다. 재고 점검 잡(`StockCheckJob`)은 매일 05:00에 상품 100만 건의 입고 이력을 합산하고, 정산 잡(`SettlementJob`)은 매달 5일 03:00에 지난달을 정산한다. 자동확정 잡(매시 정각)은 `idx_shop_order_status_delivered_at`으로 `DELIVERED`만 읽어 시드 규모의 영향이 작다.
- 배경 부하: 앱의 지표 수집기는 30초마다 DB를 센다. `PaymentMetrics`의 `count(*) ... where status = 'UNKNOWN'`은 `payment.payment`에 `status` 인덱스가 없어 시드 뒤 결제 900만 건을 훑는다(VM에서 `count`·`min` 각각 한 번에 약 460ms). 이런 문장이 상위 20문장에 나오면 k6 요청이 만든 부하와 구분해 리포트에 적고, S2-2 후보로 둔다.
- `results/` 폴더가 없으면 k6는 측정을 끝까지 돈 뒤 요약 JSON만 저장하지 못한다(`could not open 'results/...json'`). 실행 전에 만든다.
- `check.sql`의 불변식 8개는 [`InvariantChecker`](../../src/test/java/com/myroutine/support/InvariantChecker.java)의 쿼리를 옮긴 것이다. `InvariantChecker`를 고치면 이 파일도 고친다.

```sql
select calls, round(total_exec_time::numeric) total_ms, round(mean_exec_time::numeric, 2) mean_ms,
       round((100 * total_exec_time / sum(total_exec_time) over ())::numeric, 1) pct, rows,
       left(regexp_replace(query, '\s+', ' ', 'g'), 200) query
from pg_stat_statements
where dbid = (select oid from pg_database where datname = current_database())
order by total_exec_time desc limit 20;
```

- `total_exec_time`(누적)이 큰 문장이 DB 시간을 가장 많이 쓴다. 한 번은 빠르지만 많이 불리는 문장도 여기서 위로 온다. `mean_exec_time`은 한 번이 느린 문장을 찾는다. S2-2의 후보는 두 순서를 다 본다.
- 개발 PC가 병목이 아닌지: 실행 중 PC의 CPU가 여유 있는지 보고, k6가 `Insufficient VUs`나 `dropped_iterations`를 내지 않았는지 본다.

**7) 리포트에 넘길 것**: 시나리오별 `--summary-export` JSON, 상위 20문장 결과, S-06 SQL 결과, Grafana 패널 값(또는 화면), 커밋 sha, `pg_database_size`, 시드 테이블별 건수, S-03 뒤 `k6 인기 상품`의 재고 행.

### 5.3 완료 확인

| 확인 | 방법 | 기대 |
|---|---|---|
| 문장 통계가 쌓인다 | VM에서 `select count(*) from pg_stat_statements` | 0보다 크다 |
| slow query가 남는다 | `select pg_sleep(0.15)` 뒤 Postgres 컨테이너 로그 | `duration: 150.xxx ms  statement: select pg_sleep(0.15)` 한 줄 |
| exporter를 수집한다 | Prometheus Targets | `postgres`, `cadvisor`, `myroutine` 모두 UP |
| 컨테이너별 CPU가 보인다 | "성능 측정" 대시보드의 컨테이너 CPU 패널 | app과 postgres 두 줄 |
| 시드 건수 | `check.sql`의 테이블별 건수(시드 후 - 시드 전) | `orders.orders` 1,000만, `orders.order_line` 2,500만, `product.product` 100만 101, `member.member` 100만 1,100 |
| 시드가 불변식을 지킨다 | `check.sql`(시드 뒤) | 불변식 8개 모두 0건 |
| 잡이 시드를 처리하지 않는다 | `check.sql`의 잡 대상 두 값(시드 전·후) | 시드 전과 같다(시드는 잡 대상 0건) |
| S-03 초과판매가 없다 | S-03 요약의 `checkout_succeeded`, `k6 인기 상품`의 `product.stock` | 100, `available 0, reserved 100` |
| S-01·S-02·S-06이 끝까지 돈다 | 각 요약의 `http_req_failed`, S-06의 `dropped_iterations` | 실패율과 버린 반복이 기록된다(합격 여부는 리포트에 적는다. Baseline은 기준을 넘지 못해도 완료다) |
| 리포트 | `docs/perf/s2-1a-baseline.md` | 환경(커밋 sha, VM, 앱 1개, 시드 건수, 대기 없는 VU), 시나리오별 p50·p95·p99·처리량·에러율, 상위 쿼리, S2-2·S2-5 후보 |

### 5.4 리뷰 때 물어볼 것

- 대기 없는 300 VU의 p95는 무엇을 말하고 무엇을 말하지 않는가? 실제 사용자 수로 바꿔 말할 수 있는가?
- S-02를 회원마다 다른 상품으로 바꾼 이유는? 같은 상품이면 무엇을 재게 되는가?
- S-03의 1,000건은 정말 동시에 도착했는가? 무엇으로 확인하는가?
- 시드 id에 `gen_random_uuid()`를 쓰면 무엇이 달라지는가?
- `pg_stat_statements`에서 S2-2 후보를 `total_exec_time`과 `mean_exec_time` 중 무엇으로 고르는가?
- S-06 p95가 1초를 넘으면 원인은 DB인가, 릴레이 주기인가? 어떻게 구분하는가?
- 측정 중 자동 배포를 막지 않으면 결과에 무엇이 섞이는가?

## 6. S2-2 커서 조건

**목표**: 목록 다음 페이지의 커서 조건을 행 값 비교로 바꿔, 몇 번째 페이지든 읽는 행 수가 같게 한다. 상품 목록으로 바꾸기 전후를 잰다.

**왜 지금**: [Baseline](../perf/s2-1a-baseline.md)에서 페이지 상한 없이 상품 목록을 읽으면 처리량이 초당 약 2,600건에서 1,000건까지 떨어졌다. 이때 다음 페이지 쿼리가 DB 시간의 97.5%를 썼다. 6,000번째 근처에서 20개를 가져오려고 6,001행을 읽고 버렸다. S2-3에서 커넥션 풀을 늘리면 이 쿼리가 DB에 더 몰리므로 먼저 고친다.

**브랜치**: `feature/s2-2-cursor`. PR에는 테스트 10개, 쿼리 16개, 문서가 들어간다. 측정 뒤 리포트(`docs/perf/s2-2-cursor.md`)는 사용자가 넘긴 결과로 Claude가 쓴다.

### 6.1 원인

인덱스 `idx_product_status_category_created_at`은 `(status, category, created_at DESC, id DESC)` 순서로 정렬돼 있다. 다음 페이지는 "이 순서에서 커서 `(c, i)` 바로 뒤부터 20개"다.

| 커서 조건 | Postgres가 하는 일 |
|---|---|
| `created_at < :c or (created_at = :c and id < :id)` | OR로 묶인 조건으로는 인덱스에서 시작 위치를 정하지 못한다. 그래서 `status`·`category`가 같은 첫 행부터 읽고, 행마다 이 조건을 검사해 버린다(`Filter`). 버리는 행 수는 커서 앞에 있는 행 수와 같다 |
| `(created_at, id) < (:c, :id)` | 두 값을 앞 값부터 차례로 비교한다. 뜻은 위와 같다. 인덱스 순서와 같은 비교라서 인덱스 조건(`Index Cond`)이 되고, Postgres가 인덱스에서 `(c, i)` 위치를 바로 찾아 거기서부터 20개만 읽는다 |

로컬 일회용 Postgres 17(상품 20만 개, 카테고리 5개)에서 같은 위치(FOOD 6,001번째)를 쟀다.

| 형태 | 커서 조건의 위치 | 버린 행 | 읽은 블록 | 실행 시간 |
|---|---|---|---|---|
| OR | `Filter` | 6,001 | 297 | 1.16ms |
| 행 값 비교 | `Index Cond` | 0 | 4 | 0.03ms |
| `created_at <= :c and (OR)` | `created_at`만 `Index Cond` | 1 | 4 | 0.02ms |

세 번째 형태도 빠르지만 행 값 비교로 통일한다. 조건이 짧고, 부등호가 하나라 `created_at`과 `id`의 비교 방향이 서로 어긋날 수 없다. [개발 가이드 §8.1](../development-guide.md)도 이미 행 값 비교로 적었다. 1~5부 로드맵이 OR로 적어서 가이드와 어긋났고, 코드는 로드맵을 따랐다.

JPQL은 `(p.createdAt, p.id) < (:cursorCreatedAt, :cursorId)`로 쓴다. Hibernate 7.4.5는 이것을 SQL `(p1_0.created_at,p1_0.id)<(?,?)`로 그대로 내보낸다(격리 복사본에서 `findPublicNextPage`만 바꿔 `ProductControllerTest`를 돌려 SQL 로그로 확인). Hibernate는 방언의 `supportsRowValueConstructorGtLtSyntax()`가 false인 DB에서는 이 비교를 OR로 풀어 내보낸다. Postgres 방언은 기본값 true를 그대로 쓴다(소스로 확인). 그래서 native 쿼리로 바꿀 필요가 없다.

### 6.2 할 일

**1) 다음 페이지 테스트 먼저**: 아래 10개 쿼리는 지금 테스트에서 한 번도 실행되지 않는다(첫 페이지만 확인한다). JPQL은 앱이 뜰 때 문법만 검사한다. 부등호 방향이나 컬럼 순서가 틀려도 앱은 뜨고, native 쿼리(정산 품목)는 문법 오류도 실행할 때 드러난다. 그래서 쿼리를 바꾸기 전에 OR 형태 그대로 테스트를 넣어 통과를 확인하고, 바꾼 뒤 다시 통과하는지 본다. 케이스는 6.3에 있다.

**2) 쿼리 16개**: 각 `@Query`에서 커서 조건 한 줄만 바꾼다. 정렬·첫 페이지 쿼리·메서드 시그니처는 그대로다. 내림차순 목록은 `<`, 오름차순 목록은 `>`다.

| 파일:줄 | 메서드 | 바꾼 조건 | 다음 페이지 테스트 |
|---|---|---|---|
| `JpaProductRepository.java:37` | `findPublicNextPage` | `(p.createdAt, p.id) < (:cursorCreatedAt, :cursorId)` | 있음 |
| `JpaProductRepository.java:56` | `findByShopNextPage` | `(p.createdAt, p.id) < (:cursorCreatedAt, :cursorId)` | 6.3 |
| `JpaOrderRepository.java:28` | `findByMemberNextPage` | `(o.createdAt, o.id) < (:cursorCreatedAt, :cursorId)` | 있음 |
| `JpaOrderRepository.java:95` | `findShopOrderNextPage` | `(so.createdAt, so.id) < (:cursorCreatedAt, :cursorId)` | 6.3 |
| `JpaOrderRepository.java:130` | `findAutoConfirmTargets` | `(so.deliveredAt, so.id) > (:cursorAt, :cursorId)` | 있음(150건) |
| `JpaRefundRepository.java:34` | `findByStatusNextPage` | `(r.createdAt, r.id) > (:cursorCreatedAt, :cursorId)` | 6.3 |
| `JpaSettlementItemRepository.java:76` | `findBySettlementIdAfter`(native) | `(created_at, id) > (:cursorAt, :cursorId)` | 6.3 |
| `JpaSettlementRepository.java:48` | `findByShopIdAfter` | `(s.createdAt, s.id) < (:cursorAt, :cursorId)` | 6.3 |
| `JpaSettlementRepository.java:71` | `findByStatusNextPage` | `(s.createdAt, s.id) > (:cursorCreatedAt, :cursorId)` | 6.3 |
| `JpaNotificationRepository.java:46` | `findByStatusNextPage` | `(n.createdAt, n.id) > (:cursorCreatedAt, :cursorId)` | 6.3 |
| `JpaPaymentRepository.java:71` | `findByStatusNextPage` | `(p.createdAt, p.id) > (:cursorCreatedAt, :cursorId)` | 6.3 |
| `JpaInquiryRepository.java:34` | `findByMemberNextPage` | `(i.createdAt, i.id) < (:cursorCreatedAt, :cursorId)` | 6.3 |
| `JpaInquiryRepository.java:50` | `findByStatusNextPage` | `(i.createdAt, i.id) > (:cursorCreatedAt, :cursorId)` | 있음 |
| `JpaInquiryRepository.java:65` | `findAllNextPage` | `(i.createdAt, i.id) > (:cursorCreatedAt, :cursorId)` | 6.3 |
| `JpaMemberRepository.java:36` | `findForAdminNextPage` | `(m.createdAt, m.id) < (:cursorCreatedAt, :cursorId)` | 있음 |
| `JpaReviewRepository.java:49` | `findPublicNextPage` | `(r.createdAt, r.id) < (:cursorCreatedAt, :cursorId)` | 있음 |

- 결과는 OR 형태와 같다. `created_at`이 null이면 두 형태 모두 참이 아니라서 빠진다. `deliveredAt`은 같은 쿼리의 `so.deliveredAt < :before`가 null을 이미 거른다.
- 성능은 상품 목록만 잰다. 나머지는 맞는 인덱스가 없거나(결제·알림·환불·정산의 상태별 관리자 목록, 상태를 생략한 문의 목록) 키 하나에 행이 적어서(회원의 주문은 시드에서 평균 10건) 결과는 같고 이득은 작다. 리포트에 그렇게 적는다.

**3) 측정**: Baseline과 같은 VM, 같은 스냅샷에서 잰다([5.2의 6) 측정 절차](#52-할-일)). VM 명령은 리포 체크아웃(`~/actions-runner/_work/myroutine-v2/myroutine-v2`)에서 실행하고, `PG`는 5.2와 같다.

| 순서 | 할 일 |
|---|---|
| 1 | PR을 develop에 머지한다. 자동 배포가 끝날 때까지 기다린다 |
| 2 | VM을 `seed_vacuumed`로 되돌린다. 스냅샷에는 Baseline 때 배포된 앱(`f2a6a8c`)이 들어 있다 |
| 3 | 아래 EXPLAIN 세 개를 뜬다. 손으로 쓴 SQL이라 앱 버전과 관계없다 |
| 4 | GitHub Actions의 CD 워크플로를 수동 실행(`workflow_dispatch`)해 1의 머지 커밋 sha를 배포한다. `cat /opt/myroutine/deployed-sha`로 확인한다 |
| 5 | S-01 연결 확인(VU 10, 30초)으로 캐시를 데운다 |
| 6 | `pg_stat_statements_reset()` → S-01(`MAX_PAGES` 기본 10) → 상위 20문장 |
| 7 | `pg_stat_statements_reset()` → S-01(`-e MAX_PAGES=100000`) → 상위 20문장 |

EXPLAIN은 Baseline과 같은 커서 값을 쓴다(`perf/k6/results/vm/s01-explain.txt`의 `Filter`에 있는 값). 스냅샷 데이터가 같으므로 같은 위치다. 세 번째는 앱처럼 prepared statement가 범용 계획(값을 모른 채 만든 계획)으로 바뀐 경우다. `:category is null or ...` 조건이 이때 인덱스 조건에서 빠지는지 본다.

```bash
$PG 'psql -U "$POSTGRES_USER" -d my_routine -f -' <<'SQL' | tee ~/s2-2-explain.txt
\echo === OR
explain (analyze, buffers)
select p.id, p.shop_id, p.name, p.category, p.price, p.status, p.thumbnail_key, p.created_at, s.available, s.reserved, s.sold, s.received
from product.product p join product.stock s on s.product_id = p.id
where p.status = 'ON_SALE' and p.category = 'FOOD'
  and (p.created_at < '2026-10-09 00:00:21.768007+00' or (p.created_at = '2026-10-09 00:00:21.768007+00' and p.id < '01a11df5-f908-7a6d-ac95-87d049a3af0b'))
order by p.created_at desc, p.id desc limit 21;
\echo === 행 값 비교
explain (analyze, buffers)
select p.id, p.shop_id, p.name, p.category, p.price, p.status, p.thumbnail_key, p.created_at, s.available, s.reserved, s.sold, s.received
from product.product p join product.stock s on s.product_id = p.id
where p.status = 'ON_SALE' and p.category = 'FOOD'
  and (p.created_at, p.id) < ('2026-10-09 00:00:21.768007+00'::timestamptz, '01a11df5-f908-7a6d-ac95-87d049a3af0b'::uuid)
order by p.created_at desc, p.id desc limit 21;
\echo === 행 값 비교, 범용 계획
set plan_cache_mode = force_generic_plan;
prepare next_page(text, text, timestamptz, uuid, int) as
select p.id, p.shop_id, p.name, p.category, p.price, p.status, p.thumbnail_key, p.created_at, s.available, s.reserved, s.sold, s.received
from product.product p join product.stock s on s.product_id = p.id
where p.status = $1 and ($2 is null or p.category = $2)
  and (p.created_at, p.id) < ($3, $4)
order by p.created_at desc, p.id desc limit $5;
explain (analyze, buffers) execute next_page('ON_SALE', 'FOOD', '2026-10-09 00:00:21.768007+00', '01a11df5-f908-7a6d-ac95-87d049a3af0b', 21);
SQL
```

k6는 개발 PC의 `perf/k6`에서 실행한다. `{VM IP}`는 Baseline 때와 같은 주소다.

```bash
k6 run -e BASE_URL=http://{VM IP}:8080 -e VUS=10 -e RAMP=0s -e HOLD=30s s01-product-read.js
k6 run -e BASE_URL=http://{VM IP}:8080 --summary-export results/s2-2-s01.json s01-product-read.js | tee results/s2-2-s01.txt
k6 run -e BASE_URL=http://{VM IP}:8080 -e MAX_PAGES=100000 --summary-export results/s2-2-s01-deep.json s01-product-read.js | tee results/s2-2-s01-deep.txt
```

- 범용 계획에서는 `category`가 인덱스 조건에서 빠져, 카테고리 6개 기준으로 행을 약 6배 더 읽을 수 있다(로컬 실험에서 20행을 위해 84행을 버림). 이 비용은 페이지 깊이와 관계없이 일정하다. 앱이 실제로 어느 계획을 쓰는지는 EXPLAIN으로 알 수 없으므로, 6·7의 `pg_stat_statements` 평균 시간도 함께 본다. 이번 단계에서 고치지 않고 리포트에 기록만 한다.
- 7의 깊은 페이지 측정은 Baseline에서 처리량이 시간에 따라 떨어졌다. 고친 뒤에는 처리량이 측정 내내 일정해야 한다. Grafana의 DB 트랜잭션/초와 postgres CPU 패널로 본다.

**4) 리포트에 넘길 것**: `~/s2-2-explain.txt`, k6 요약 두 개(`results/s2-2-s01*.json`), 6·7 뒤의 상위 20문장, 7의 Grafana 화면(트랜잭션/초, postgres CPU, 커넥션), 배포한 sha.

### 6.3 완료 확인

테스트 10개는 모두 같은 모양이다. 항목 3개를 서로 다른 시각에 만들고, `size=2`로 첫 페이지를 읽고, 받은 `nextCursor`로 다음 페이지를 읽는다. 다음 페이지에서 확인할 값은 `$.items[*].id`가 남은 항목 하나뿐인지와 `$.nextCursor`가 `null`인지다. 부등호 방향이 틀린 구현은 첫 페이지 항목을 다시 돌려주고, 커서를 무시하는 구현은 세 개를 다 돌려준다. 내림차순 목록에서 남는 항목은 가장 먼저 만든 것이고, 오름차순 목록에서는 가장 나중에 만든 것이다.

| 테스트 클래스 | 케이스 | 요청 | 남는 항목 |
|---|---|---|---|
| `SellerProductControllerTest` | 판매자 상품 목록은 커서로 다음 페이지를 조회한다. | `GET /api/shops/{shopId}/products?size=2` | 가장 먼저 등록한 상품 |
| `SellerOrderControllerTest` | 가게주문 목록은 커서로 다음 페이지를 조회한다. | `GET /api/shops/{shopId}/orders?size=2` | 가장 먼저 결제한 주문의 가게주문 |
| `AdminRefundRetryTest` | FAILED 환불 목록은 커서로 다음 페이지를 조회한다. | `GET /admin/api/refunds?status=FAILED&size=2` | 가장 나중에 만든 환불 |
| `SettlementControllerTest` | 정산 목록은 커서로 다음 페이지를 조회한다. | `GET /api/shops/{shopId}/settlements?size=2` | 가장 먼저 만든 정산 |
| `SettlementControllerTest` | 정산 품목 목록은 커서로 다음 페이지를 조회한다. | `GET /api/shops/{shopId}/settlements/{id}?size=2`, 확인은 `$.items.items[*].id`·`$.items.nextCursor` | 가장 나중에 적재된 품목 |
| `AdminSettlementTest` | 지급 실패 정산 목록은 커서로 다음 페이지를 조회한다. | `GET /admin/api/settlements?status=PAYOUT_FAILED&size=2` | 가장 나중에 만든 정산 |
| `AdminNotificationTest` | FAILED 알림 목록은 커서로 다음 페이지를 조회한다. | `GET /admin/api/notifications?status=FAILED&size=2` | 가장 나중에 만든 알림 |
| `AdminReconcileTest` | UNKNOWN 결제 목록은 커서로 다음 페이지를 조회한다. | `GET /admin/api/payments?status=UNKNOWN&size=2` | 가장 나중에 만든 결제 |
| `InquiryTest` | 본인 문의 목록은 커서로 다음 페이지를 조회한다. | `GET /api/inquiries?size=2` | 가장 먼저 쓴 문의 |
| `InquiryTest` | 상태를 생략한 관리자 문의 목록은 커서로 다음 페이지를 조회한다. | `GET /admin/api/inquiries?size=2` | 가장 나중에 쓴 문의 |

| 확인 | 방법 | 기대 |
|---|---|---|
| 테스트 10개가 고치기 전에 통과한다 | OR 형태 그대로 `./gradlew test` | 통과(테스트가 맞게 쓰였는지 확인) |
| 동작이 같다 | 쿼리 16개를 바꾼 뒤 `./gradlew test` | 전체 통과 |
| OR이 남지 않았다 | 표 아래 명령 | 0줄 |
| 인덱스에서 바로 찾아간다 | `~/s2-2-explain.txt`의 "행 값 비교" | 커서 조건이 `Index Cond`에 있고 `Rows Removed by Filter`가 없다. Baseline은 6,001행, 18.9ms |
| 깊은 페이지에서 처리량이 일정하다 | 7의 k6 요약과 Grafana | 처리량이 측정 내내 일정하다. Baseline은 초당 1,637건이었고 2,600건에서 1,000건으로 떨어졌다 |
| 리포트 | `docs/perf/s2-2-cursor.md` | 바꾸기 전후 EXPLAIN, S-01 두 측정의 처리량·p95·다음 페이지 쿼리 평균 시간을 Baseline과 비교, 범용 계획 결과 |

```bash
grep -rnE "(createdAt|created_at|deliveredAt|delivered_at) [<>] :[a-zA-Z]+ (or|OR) \(" src/main/java
```

### 6.4 리뷰 때 물어볼 것

- OR 형태와 행 값 비교는 같은 행을 돌려주는가? `created_at`이 같은 두 행이 페이지 경계에 걸리면 각각 어떻게 되는가?
- 목록마다 `<`와 `>` 중 무엇을 쓸지는 무엇을 보고 정하는가?
- 테스트를 쿼리보다 먼저 넣은 이유는? 고치기 전에 통과를 확인하지 않으면 무엇을 놓치는가?
- MAX_PAGES 10 측정에서는 처리량이 크게 바뀌지 않을 수 있다. 왜 그런가?
- 범용 계획에서 `category`가 인덱스 조건에서 빠지면 비용이 페이지 깊이에 따라 늘어나는가?
