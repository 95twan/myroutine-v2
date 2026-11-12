# 서브: 늦게 적재된 정산 품목

> 버전 0.2 · 2026-11-12 · 관리자 정산 실행을 정기 정산과 같은 `JobLock`으로 감싸 실행 중이면 409 `SETTLEMENT_RUN_IN_PROGRESS`를 돌려주고, 정기 정산은 락을 기다린다. 빈 회차를 지우는 처리를 뺐다
> 0.1 · 2026-11-10 · 정산이 끝난 기간에 늦게 적재된 품목을 그 기간의 다음 회차 또는 다음 달 정산으로 정산하게 한다(단계 ID SUB-18)

> **서브**: 로드맵에서 뺀 기능이다. Stage 1(Part 6) 뒤에 한다.
> **따르는 규칙**: 로드맵 Part 1~6의 공통 규칙. 정산 규격의 출발점은 [로드맵 3-4](../03-roadmap/part3-events.md)다.

---

## SUB-18. 늦게 적재된 정산 품목 (M)

**목표**: 정산이 끝난 뒤 적재된 품목도 한 번 정산된다. 관리자가 그 달을 다시 실행하면 그 가게의 다음 회차 정산으로 바로 지급되고, 관리자가 아무것도 하지 않으면 다음 달 정기 정산에 들어간다.

**드러나는 때**: 정산 consumer 처리가 DLT로 갔다가 그 달 정산(매월 5일 03:00) 뒤에 `AdminDltService.replay`로 처리되거나, 발행이 실패해 `DEAD`가 된 `order-line-confirmed` 이벤트를 그 뒤에 `AdminOutboxService.retry`로 다시 보낼 때만 드러난다.

### 빈 곳

| # | 위치 | 지금 | 깨지는 상황 |
|---|---|---|---|
| 1 | `JpaSettlementItemRepository.findShopIdsToSettle`·`assign` | 정산 대상을 `confirmed_at`이 정산 기간 안인 미정산 품목으로 고른다 | 10월 정산 뒤 적재된 10월 품목은 11월 정산 기간에도 맞지 않아 `settlement_id`가 NULL로 계속 남는다. 판매자는 그 금액을 받지 못하고, `SettlementWithdrawalPrecondition`이 미정산 품목을 보고 `PENDING_SETTLEMENT`를 돌려줘 탈퇴도 계속 막힌다(INV-07: 정산되지 않은 구매확정 품목이 남지 않는다) |
| 2 | `uk_settlement_shop_period`, `JpaSettlementRepository.insertIfAbsent` | 가게·기간마다 정산은 하나다 | 관리자가 `POST /admin/api/settlements/run?period=2026-10`으로 다시 실행해도 `insertIfAbsent`가 0을 돌려줘 그 가게를 건너뛴다. 운영자가 앞당겨 지급할 방법이 없다 |
| 3 | `SettlementController.runSettlement` → `SettlementService.runManually` | `JobLock` 없이 `run`을 부른다. 정기 정산(`SettlementJob`)만 `"settlement"` 락을 잡는다 | 관리자 실행이 정기 정산이나 다른 관리자 실행과 겹쳐 `payoutAll`이 같은 정산에 지급을 두 번 요청한다([알려진 문제](../known-issues.md) "정산 실행 겹침"). 1·2번을 고치면 11월 정기 실행과 관리자의 10월 실행이 같은 늦은 품목을 함께 노린다 |

바뀐 뒤의 동작은 다음과 같다.

| 상황 | 결과 |
|---|---|
| 늦은 품목이 적재된 뒤 관리자가 그 달을 실행 | 그 가게의 다음 회차 정산(`seq` = 2)이 만들어지고 같은 실행의 `payoutAll`이 지급한다 |
| 관리자가 실행하지 않음 | 다음 달 정기 정산이 기간 끝 전의 미정산 품목을 모두 넣으므로 그 달 정산에 들어간다 |
| 같은 달을 다시 실행했는데 늦은 품목이 없음 | 대상 가게가 없어 아무것도 만들지 않는다(FR-STL-04) |
| 정기 정산이나 다른 관리자 실행이 도는 중에 관리자가 실행 | 기다리지 않고 409 `SETTLEMENT_RUN_IN_PROGRESS`. 아무것도 만들거나 지급하지 않는다 |

### 할 일

**1) 마이그레이션** — `V{yyyyMMddHHmm}__settlement_add_settlement_seq.sql`

```sql
ALTER TABLE settlement.settlement ADD COLUMN seq int NOT NULL DEFAULT 1;
ALTER TABLE settlement.settlement ALTER COLUMN seq DROP DEFAULT;
ALTER TABLE settlement.settlement DROP CONSTRAINT uk_settlement_shop_period;
ALTER TABLE settlement.settlement ADD CONSTRAINT uk_settlement_shop_period_seq UNIQUE (shop_id, period_start, seq);
ALTER TABLE settlement.settlement ADD CONSTRAINT ck_settlement_seq CHECK (seq >= 1);
```

기존 정산은 모두 1회차가 된다. 기본값은 기존 행을 채우는 데만 쓰고 지워서, 새 행은 `insertIfAbsent`가 회차를 반드시 넣게 한다.

**2) 도메인** — `Settlement`에 `@Column(name = "seq", nullable = false) private int seq;`를 `periodEnd` 뒤에 더한다.

**3) 저장소**

| 대상 | 규격 |
|---|---|
| `SettlementRepository`·`JpaSettlementRepository.nextSeq(UUID shopId, LocalDate start)` → `int` | native `SELECT COALESCE(MAX(seq), 0) + 1 FROM settlement.settlement WHERE shop_id = :shopId AND period_start = :start`. 정산 실행은 4)의 락으로 하나만 돌아 다른 실행과 같은 값을 받지 않는다 |
| `insertIfAbsent(UUID id, UUID shopId, LocalDate start, LocalDate end, int seq, int bp, Instant now)` | `int seq` 인자를 `end` 뒤에 더하고 INSERT 칸에 `seq`를 넣는다. 충돌 대상은 `ON CONFLICT ON CONSTRAINT uk_settlement_shop_period_seq DO NOTHING` |
| `JpaSettlementItemRepository.findShopIdsToSettle(Instant to, UUID afterShopId, int limit)` | `from` 인자와 `confirmed_at >= :from` 조건을 뺀다. 나머지(`confirmed_at < :to AND settlement_id IS NULL AND shop_id > :afterShopId ORDER BY shop_id LIMIT :limit`)는 그대로다 |
| `assign(UUID settlementId, UUID shopId, Instant to, Instant now)` | `from` 인자와 조건을 뺀다 |

**4) `SettlementService`**

`run(YearMonth month)`은 `from`을 만들지 않고 `findShopIdsToSettle(to, after, 500)`을 부른다. 나머지는 그대로다. 그러면 `SettlementPeriod.fromInclusive`를 쓰는 곳이 없어지므로 지우고, `SettlementPeriodTest`의 "정산 기간의 경계 시각을 계산한다."에서 `from` 확인을 뺀다.

`settleShop(UUID shopId, SettlementPeriod period)`는 `insertIfAbsent` 앞에서 `int seq = settlementRepository.nextSeq(shopId, period.start())`를 읽어 넘긴다. `assign`·`sumBySettlementId`·`calculate`는 그대로다.

`runManually(YearMonth month)`는 끝난 달 검사 뒤 `jobLock.callExclusively("settlement", () -> run(month))`를 부르고, 비어 있으면 `BusinessException(SettlementErrorCode.SETTLEMENT_RUN_IN_PROGRESS)`를 던진다. `SettlementService`는 `JobLock`(`common.job`)을 주입받는다. 락 이름이 `SettlementJob`의 `jobRunner.run("settlement", ...)`과 같아서 정기 정산과 관리자 실행, 관리자 실행끼리 모두 한 번에 하나만 돈다. 락을 못 잡으면 기다리지 않는다. 0건 결과를 돌려주면 운영자가 "실행했는데 정산할 것이 없었다"로 읽기 때문에 오류로 알린다. `SettlementJob`은 `run`을 부르므로 락을 두 번 잡지 않는다.

`SettlementErrorCode`에 `SETTLEMENT_RUN_IN_PROGRESS(HttpStatus.CONFLICT, "정산이 실행 중입니다.")`를 더한다.

**5) 정기 정산의 락 기다리기**

정기 정산은 락을 못 잡아도 건너뛰지 않고 기다린다. 지금 `JobRunner.run`은 `pg_try_advisory_lock`이 실패하면 `log.debug("job {} skipped: locked")`만 남기고 넘어가서, 매월 5일 03:00에 관리자 실행(주로 전전달 재실행)이 돌고 있으면 그달 정기 정산이 통째로 빠진다.

| 대상 | 규격 |
|---|---|
| `JobLock.runWaiting(String jobName, Runnable task)` | `runExclusively`와 같은 키(`"job:" + jobName`)로 `SELECT pg_advisory_lock(hashtext(?))`를 실행해 락이 풀릴 때까지 기다린 뒤 `task`를 실행하고, `finally`에서 같은 연결로 `pg_advisory_unlock`한다. `pg_advisory_lock`은 `void`를 돌려주므로 결과를 boolean으로 읽지 않는다 |
| `JobRunner.runWaiting(String jobName, Supplier<Integer> task)` | `run`과 같은 MDC·로그에서 `jobLock.runExclusively` 대신 `jobLock.runWaiting`을 부른다. 건너뛰는 경우가 없어 `skipped` 로그가 없다 |
| `SettlementJob.run` | `jobRunner.run` 대신 `jobRunner.runWaiting("settlement", ...)`을 부른다 |

기다리는 동안 예약 작업 스레드 하나와 DB 연결 하나를 쥔다. 관리자 실행은 끝이 있는 작업이라 그 뒤에 정기 정산이 돈다. 관리자 실행이 늦은 품목을 먼저 가져가고 정기 정산은 남은 품목만 가져가므로 결과는 실행 순서와 상관없다. 다른 잡은 지금처럼 `run`(건너뛰기)을 쓴다.

**6) 응답** — `SettlementResult`·`SettlementResponse`·`AdminSettlementResult`·`AdminSettlementResponse`에 `int seq`를 `periodEnd` 뒤에 더한다. 판매자 정산 목록에 같은 기간의 정산이 둘 나올 때 구분하는 값이다. 지급 이벤트(`SettlementPaidEvent`)와 알림 문구(`NotificationComposer`)는 바꾸지 않는다. 2회차가 지급되면 같은 기간 제목의 지급 알림이 그 회차 금액으로 한 번 더 간다.

**7) 테스트 보조** — `support/`

| 대상 | 규격 | 쓰는 곳 |
|---|---|---|
| `SettlementSnapshot`에 `int seq` | `periodEnd` 뒤에 더한다. `settlementSnapshotOf`의 세 메서드가 SELECT에 `seq`를 더해 채운다 | 아래 3·12번 |
| `settlementSnapshotOf(UUID shopId, YearMonth month)` | 정산 쿼리(`FROM settlement.settlement`)의 `WHERE` 뒤에 `ORDER BY seq`를 더한다. 품목 쿼리는 그대로다. 1회차를 돌려주므로 기존 호출은 그대로다 | 기존 테스트, 11번 |
| `settlementSnapshotOf(UUID shopId, YearMonth month, int seq)` | 위 메서드의 정산 쿼리 `WHERE`에 `AND seq = ?`를 더한 것이다(인자 `shopId, month.atDay(1), seq`). 없으면 `null` | 3·12번 |

기존 테스트 하나를 고친다: `SettlementServiceTest` 5번의 스파이 설정은 `insertIfAbsent` 인자가 하나 늘어 `insertIfAbsent(any(), eq(badShopId), any(), any(), anyInt(), anyInt(), any())`가 된다.

### 완료 확인

케이스는 [로드맵 README 4.1](../03-roadmap/README.md)의 기준으로 골랐다. 공통 값(정산 월 `YearMonth.of(2026, 9)`, 확정 시각 `Instant.parse("2026-09-15T03:00:00Z")`, 품목 7,500원)은 [3-4a 완료 확인](../03-roadmap/part3-events.md)과 같다. 3-4a의 4번(기간 경계)은 8월 → 9월 → 10월 순서로 실행하므로 바꾸지 않아도 통과한다.

#### `settlement/application/SettlementServiceTest`

**3. 정산 뒤 늦게 적재된 품목은 같은 달을 다시 실행하면 2회차로 정산된다 (고침)** — 준비: 가게 1곳 × 품목 2개(각 7,500원) → `run(YearMonth.of(2026, 9))`. 이어서 같은 가게의 9월 15일 확정 품목을 `insertSettlementItem(shopId, 7500, 위 확정 시각)`으로 하나 더 넣는다(`lateLineId`). 동작: `run(YearMonth.of(2026, 9))`을 다시(결과를 `second`로 받는다).
- 반환값(`second.settledShops()`): 1.
- DB(`settlementSnapshotOf(shopId, YearMonth.of(2026, 9), 2)`): `grossAmount()` = 7500, `itemCount()` = 1.

**11. 늦게 적재된 품목은 다음 달 정산에 들어간다** — 준비: 가게 1곳 × 품목 2개(각 7,500원) → `run(YearMonth.of(2026, 9))`. 이어서 같은 가게에 9월 15일 확정 품목 하나(`insertSettlementItem(shopId, 7500, 위 확정 시각)`)와 10월 15일 확정 품목 하나(`insertSettlementItem(shopId, 5000, Instant.parse("2026-10-15T03:00:00Z"))`)를 넣는다. 동작: `run(YearMonth.of(2026, 10))`.
- DB(`settlementSnapshotOf(shopId, YearMonth.of(2026, 10))`): `grossAmount()` = 12500, `itemCount()` = 2.

**12. 2회차를 만들어도 1회차는 바뀌지 않는다** — 준비와 동작은 3번과 같다.
- DB(`settlementSnapshotOf(shopId, YearMonth.of(2026, 9), 1)`): `grossAmount()` = 15000, `itemCount()` = 2.

#### `common/job/JobLockTest`

**기다리는 실행은 다른 실행이 락을 풀면 실행된다** — 준비: `CountDownLatch entered = new CountDownLatch(1)`, `CountDownLatch release = new CountDownLatch(1)`, `AtomicBoolean ran = new AtomicBoolean(false)`, `CompletableFuture<Boolean> holder = CompletableFuture.supplyAsync(() -> jobLock.runExclusively("job", () -> { entered.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }))` → `entered.await(5, TimeUnit.SECONDS)`. 동작: `CompletableFuture<Void> waiter = CompletableFuture.runAsync(() -> jobLock.runWaiting("job", () -> ran.set(true)))` → `waiter.get(300, TimeUnit.MILLISECONDS)`가 `TimeoutException`을 던지는지 확인 → `release.countDown()` → `holder.get(10, TimeUnit.SECONDS)`, `waiter.get(10, TimeUnit.SECONDS)`.
- 락을 푸기 전: `waiter.get(300, TimeUnit.MILLISECONDS)`가 `TimeoutException`, `ran.get()` = `false`.
- 락을 푼 뒤: `ran.get()` = `true`.

#### `settlement/web/SettlementControllerTest`

**같은 기간의 정산은 회차로 구분된다** — 준비: 판매자 1명과 가게 1개, 그 가게에 9월 15일 확정 품목 2개 → `settlementService.run(YearMonth.of(2026, 9))` → 같은 확정 시각의 품목 하나를 더 넣고 `run(YearMonth.of(2026, 9))`을 다시. 동작: 판매자가 `GET /api/shops/{shopId}/settlements`.
- 응답(andExpect): 상태 200, `$.items[0].seq` = 2, `$.items[1].seq` = 1.

**정산이 실행 중이면 관리자 실행을 거부한다** — 준비: 판매자 1명과 가게 1개, 그 가게에 9월 15일 확정 품목 2개(`insertSettlementItems(List.of(shopId), 2, 7500, 확정 시각)`), 관리자 토큰(`runByAdmin`과 같은 방법). `JobLock`을 주입받아 `CountDownLatch entered = new CountDownLatch(1)`, `CountDownLatch release = new CountDownLatch(1)`, `CompletableFuture<Boolean> holder = CompletableFuture.supplyAsync(() -> jobLock.runExclusively("settlement", () -> { entered.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }))` → `entered.await(5, TimeUnit.SECONDS)`. 다른 연결이 정산 락을 쥔 상태다. 동작: 관리자 토큰으로 `POST /admin/api/settlements/run?period=2026-09`. 확인 뒤 `release.countDown()`과 `holder.get(10, TimeUnit.SECONDS)`로 락을 푼다.
- 응답(andExpect): 상태 409, `$.code` = `SETTLEMENT_RUN_IN_PROGRESS`.
- DB(`countOf("settlement.settlement")`): 0.

### 문서 반영

단계를 마칠 때 Claude가 고친다.

| 문서 | 고칠 곳 |
|---|---|
| [요구사항](../01-requirements/README.md) | FR-STL-04를 "정산 재실행은 이미 정산된 품목을 다시 정산하지 않는다. 정산 뒤 적재된 품목은 그 기간을 다시 실행하면 다음 회차로, 실행하지 않으면 다음 달 정산으로 정산된다"로 |
| [ERD](../02-design/03-erd.md) | `settlement`에 `seq`, 유니크 키 `(shop_id, period_start, seq)`, 정산 실행 설명 |
| [API 명세](../02-design/07-api-spec.md) | 정산 응답과 관리자 정산 항목에 `seq`, 수동 실행 설명과 409 `SETTLEMENT_RUN_IN_PROGRESS`, 에러 코드 표의 409 행 |
| [ADR-009](../adr/ADR-009-settlement.md) | 결과 절의 늦은 품목 처리, 관리자 실행도 정기 정산과 같은 락을 잡고 정기 정산은 락을 기다린다는 것 |
| [로드맵 3-4](../03-roadmap/part3-events.md) | 정책의 "정산 실행 뒤에 늦게 도착한 전월 품목" 문단, 3-4a 완료 확인 3번, 에러 코드 표에 `SETTLEMENT_RUN_IN_PROGRESS`, `SettlementPeriod`의 `fromInclusive`와 `run` 순서의 `from`, `SettlementPeriodTest` 1번의 `fromInclusive` 확인 |
| [로드맵 6-3](../03-roadmap/part6-ops-wrapup.md) | 관리자 API 응답 표의 정산 실행 행에 409 `SETTLEMENT_RUN_IN_PROGRESS` |
| [개발 가이드](../development-guide.md) | 동시성 도구 표의 "스케줄 작업 중복 실행 방지" 행에 정산은 `pg_advisory_lock`으로 기다린다는 것 |
| [알려진 문제](../known-issues.md) | "정산이 끝난 뒤 적재된 전월 품목"·"정산 실행 겹침" 행을 지운다 |

**리뷰 때 물어볼 것**

- `runManually`의 락 이름을 `SettlementJob`과 같게 한 이유는? 다르게 하면 어떤 실행이 겹치나?
- 관리자 실행은 락을 못 잡으면 409로 끝내고 정기 정산은 기다린다. 둘을 다르게 한 이유는?
- 11월 정기 정산이 10월에 남은 품목을 가져간 뒤 관리자가 10월을 실행하면 무엇이 만들어지나?
