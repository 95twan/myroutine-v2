# Stage 2 준비: 측정 환경 · 시드 데이터 · k6 시나리오

> 버전 0.3 · 2026-11-06 · 측정 환경(운영 VM), 시드 규모, 부하 값을 확정했다
> 0.2 · 2026-11-06 · 시나리오 실행 순서와 개선 실험 우선순위를 정했다. 2-8 샤딩은 하지 않고, 재고 이력 3,000만 건은 2-7을 할 때만 넣는다
> 0.1 · 2026-11-06 · 초안. 합격 기준은 [요구사항 5.2](../01-requirements/README.md)의 값이고, 건수·부하·비율은 제안이다

Stage 2 개선 단위(2-1~2-7)의 개요는 [아키텍처 진화](../02-design/01-architecture-evolution.md)에 있다. 이 문서는 첫 단위인 2-1 Baseline의 준비와 개선 실험의 우선순위를 적는다. 시드 스크립트와 k6 스크립트는 사용자가 쓴다. k6는 처음 쓰는 도구라 명령과 옵션의 구체 값은 2-1 착수 때 실행해 확인한 뒤 적는다.

## 1. 측정 환경

| 항목 | 값 |
|---|---|
| 앱·인프라 | 운영 VM(RAM 16GB, vCPU 8, Docker Compose), 앱 1개 |
| 부하 생성 | 개발 PC의 k6 → LAN → VM의 8080([ADR-011](../adr/ADR-011-ops-practice-environment.md) "결과"). Cloudflare Tunnel을 거치지 않는다 |
| PG | 어느 시나리오도 승인을 호출하지 않는다 |
| 지표 | k6 요약, VM의 Prometheus·Grafana, `pg_stat_statements`, slow query log |
| 리포트 | `docs/perf/`. 커밋 sha, VM 자원, 앱 복제 수, 시드 건수를 함께 적는다 |

단일 VM과 합성 데이터라 절대 수치를 앞세우지 않고, 같은 조건에서 비교한 결과로 기록한다.

측정 전에 할 일은 다음과 같다.

- Proxmox 스냅샷을 남긴 뒤 시드를 넣는다.
- 측정 중에는 `develop`에 머지하지 않는다. 코드가 바뀐 push는 VM에 자동 배포되어 앱이 다시 뜬다.
- 운영 compose의 Postgres에 `shared_preload_libraries=pg_stat_statements`와 slow query 설정을 더한다. 확장은 이미지에 있다(`pg_available_extensions`에서 1.11 확인).
- k3s로 옮기면 Baseline을 다시 잰다([ADR-012](../adr/ADR-012-kubernetes-zero-downtime.md)).
- VM 메모리나 디스크가 모자라면 Baseline을 재기 전에 MinIO를 [NAS로 옮긴다](../ops/nas-minio.md).

확인 필요: 시드가 VM 디스크 100GB 안에 드는지. 넣으면서 `pg_database_size`로 본다.

## 2. 시드 데이터

### 규모

| 데이터 | 건수 | 테이블 | 쓰임 |
|---|---|---|---|
| 회원 | 100만 | `member.member`, `member.member_address` | S-02, S-03의 전용 회원 포함 |
| 가게 | 1만 | `shop.shop` | 상품·정산의 소유자 |
| 상품 | 100만 | `product.product`, `product.stock` | S-01, S-04, S-07 |
| 주문 | 1,000만 | `orders.orders`, `payment.payment` | 2-2 쿼리 튜닝 |
| 주문 품목 | 약 2,500만 | `orders.shop_order`, `orders.order_line` | 2-2 쿼리 튜닝 |
| 재고 이력 | 상품당 입고 1건, 2-7을 하면 3,000만 | `product.stock_movement` | 입고 이력은 재고 점검 잡이 읽는다. 3,000만 건은 2-7 파티셔닝 때만 넣는다 |
| 정산 대상 품목 | 100만 | `settlement.settlement_item` | S-05. 끝난 달 하나에 몰아 넣는다 |
| 시나리오 전용 장바구니 | 회원 1,100명 | `orders.cart`, `orders.cart_item` | S-02 100명, S-03 1,000명 |

### 생성 방식

앱 API를 거치지 않고 `psql`로 `INSERT ... SELECT generate_series(...)`를 실행한다. 건수가 많아 API로는 오래 걸리고, 측정 대상이 아닌 이벤트 처리까지 돌기 때문이다.

| 항목 | 규칙 | 이유 |
|---|---|---|
| `id` | `created_at`의 밀리초를 앞 48비트에 넣은 UUIDv7 형태로 만드는 시드용 SQL 함수를 쓴다. Postgres 17에는 `uuidv7()`이 없다(`pg_proc`에서 확인) | 앱이 쓰는 UUIDv7과 인덱스 지역성을 같게 한다. `gen_random_uuid()`는 삽입 위치가 흩어져 결과를 왜곡한다 |
| `created_at` | 최근 24개월에 고르게 | 기간 조회와 파티셔닝 실험의 전제다 |
| 재고 | `available + reserved + sold = received`이고, `RECEIVE`·`ADJUST` 이력 합이 `received`와 같다 | CHECK 제약을 통과하고 `재고 불일치` 알림이 울리지 않는다 |
| 상품 상태·카테고리 | `ON_SALE` 90%, `HIDDEN` 5%, `DISCONTINUED` 5%, 카테고리 6종 균등 | 상태·카테고리 인덱스의 선택도를 현실에 가깝게 한다 |
| 상품 이름 | 단어 풀에서 조합한다 | S-07의 검색어가 결과를 돌려준다 |
| 비밀번호 | 해시 하나를 미리 만들어 모든 회원에 같은 값을 넣는다 | 건마다 해시하면 적재가 오래 걸린다 |
| Elasticsearch | [아키텍처 진화](../02-design/01-architecture-evolution.md)의 Stage 2 절대로 앱을 먼저 띄워 alias `products`를 만든 뒤 `_bulk`로 넣는다 | 시드는 이벤트를 내지 않아 문서가 저절로 생기지 않는다 |

넣은 뒤 `ANALYZE`를 실행하고, [`InvariantChecker`](../../src/test/java/com/myroutine/support/InvariantChecker.java)의 쿼리로 금액 합계·재고 등식 위반이 0건인지 확인한다.

## 3. k6 시나리오

VU 시나리오는 램프업 1분 뒤 5분 유지한다.

| 시나리오 | 요구사항 | 요청 | 부하 | 합격 기준 | 읽는 곳 |
|---|---|---|---|---|---|
| S-01 상품 조회 | NFR-PERF-01 | `GET /api/products`(category, cursor) 30%, `GET /api/products/{id}` 70% | 300 VU | p95 < 200ms, 에러율 < 0.1% | k6 요약 |
| S-02 체크아웃 | NFR-PERF-02 | `POST /api/orders/checkout`(매번 새 `Idempotency-Key`) | 100 VU | p95 < 500ms | k6 요약 |
| S-03 인기 상품 경합 | NFR-PERF-03 | 재고 100개 상품 1개를 회원 1,000명이 각자 체크아웃 1회 | 동시 1,000건 | 정확히 100건 성공, 초과판매 0건, p95 측정 | k6 체크, `product.stock` |
| S-04 인증 오버헤드 | NFR-PERF-04 | `GET /api/products/{id}`를 `Authorization` 헤더 없이, 있게 번갈아 실행 | 100 VU | p95 < 10ms (As-Is 방식 대비 수치 비교) | 두 실행의 k6 요약 |
| S-05 정산 배치 | NFR-PERF-05 | `POST /admin/api/settlements/run?period={끝난 달}` 1회 | 1건 | 구매확정 품목 100만 건을 10분 이내, 누락·중복 0건 | 응답 시간, `InvariantChecker.settlementOnce` 쿼리 |
| S-06 Outbox 지연 | NFR-PERF-06 | 판매자 `PATCH /api/shops/{shopId}/products/{id}` 초당 20건, 5분 | 초당 20건 | 평시 p95 < 1초 | `common.outbox_event`의 `sent_at - created_at`에 `percentile_cont(0.95)` |
| S-07 상품 검색 | NFR-PERF-07 | `GET /api/search/products?q={단어 풀}&size=20`, 상품 100만 건 | 100 VU | p95 < 200ms, 에러율 < 0.1% | k6 요약 |

시나리오는 다음 순서로 잰다.

| 순서 | 시나리오 | 이유 |
|---|---|---|
| 먼저 | S-01, S-02, S-03, S-06 | 개선 실험의 출발점이다. S-01과 S-02는 2-2에서 고칠 느린 쿼리를 찾고, S-03은 2-5의 기준값이고, S-06은 릴레이 폴링을 바꿀지 판단하는 근거다 |
| 나중 | S-04, S-05, S-07 | 요구사항의 목표를 확인하는 측정이다 |

시나리오별로 알아 둘 점은 다음과 같다.

| 시나리오 | 내용 |
|---|---|
| S-02 | 전용 회원마다 배송지와 장바구니 항목을 넣어 둔다. 체크아웃은 장바구니를 건드리지 않아 같은 항목으로 반복할 수 있다. 예약은 결제 TTL 15분 뒤에 풀리므로 전용 상품의 재고를 5분 처리량보다 넉넉히 둔다 |
| S-03 | 로그인은 비밀번호 해시 비용이 커서 k6의 `setup()`에서 끝내 측정 구간에서 뺀다. 재고 부족 응답은 422 `OUT_OF_STOCK`이라 에러율이 아니라 체크 수로 본다 |
| S-04 | Stage 1에는 게이트웨이가 없어 `JwtAuthenticationFilter`의 JWT 검증과 Redis `tokenVersion` 조회가 같은 일을 한다. 헤더가 있을 때만 이 경로를 타므로 두 실행의 차이가 오버헤드다. 두 분포의 p95 차이이지 요청별 오버헤드의 p95는 아니라는 한계를 리포트에 적는다. As-Is 비교군은 매 요청 회원을 조회하는 실험 브랜치로 잰다([ADR-006](../adr/ADR-006-auth.md)). 이 브랜치는 머지하지 않는다. 모놀리스에는 원 프로젝트의 네트워크 호출이 없어 이 비교는 Redis 조회와 DB 조회의 차이만 보여 준다 |
| S-05 | k6의 요청 `timeout`을 15분으로 늘린다. 지급은 `MockPayoutGateway`라 외부 지급 시간은 수치에 들어가지 않는다 |
| S-06 | 릴레이가 1초 폴링이라 대기만 최대 약 1초다. p95가 기준에 붙을 수 있고, 넘으면 [ADR-003](../adr/ADR-003-outbox-kafka.md)대로 폴링 주기나 CDC를 재검토한다. `created_at`은 트랜잭션 안에서 찍은 앱 시각이라 커밋까지 걸린 시간만큼 실제보다 약간 크게 나온다 |

## 4. 개선 실험 우선순위

단계 번호는 실행 순서가 아니다. 선택과 장단점이 결과로 남는 비교 실험을 먼저 한다.

| 우선순위 | 단계 | 이유 |
|---|---|---|
| 꼭 한다 | 2-5 재고 차감 방식 비교 | 조건부 UPDATE, `FOR UPDATE`, Redis 차감을 같은 조건에서 비교한다([ADR-008](../adr/ADR-008-inventory-reservation.md)). 지금 고른 방식의 성능 근거가 여기서 나온다 |
| 꼭 한다 | 2-2 쿼리 튜닝, 인덱스 설계 검증 | 느린 쿼리를 찾아 고치고, 인덱스의 읽기 이득과 쓰기 비용을 함께 잰다 |
| 여유가 있으면 한다 | 2-3 커넥션·스레드 | 플랫폼 스레드와 가상 스레드를 비교한다. 차이가 없으면 그 이유를 기록한다 |
| 측정에서 필요가 드러나면 한다 | 2-4 캐시, 2-6 Read Replica, 2-7 파티셔닝 | 2-6은 같은 물리 서버라 처리량 이득을 말할 수 없고 복제 지연 대응만 남는다. 2-7은 시드 비용이 크다 |
| 하지 않는다 | 샤딩 | 물리 서버 한 대에서는 쓰기 한계를 제대로 보여 주기 어렵다 |
