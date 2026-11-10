# 서브: 늦게 적재된 정산 품목

> 버전 0.1 · 2026-11-10 · 정산이 끝난 기간에 늦게 적재된 품목을 그 기간의 다음 회차 또는 다음 달 정산으로 정산하게 한다(단계 ID SUB-18)

> **서브**: 로드맵에서 뺀 기능이다. Stage 1(Part 6) 뒤에 한다.
> **따르는 규칙**: 로드맵 Part 1~6의 공통 규칙. 정산 규격의 출발점은 [로드맵 3-4](../03-roadmap/part3-events.md)다.

---

## SUB-18. 늦게 적재된 정산 품목 (M)

**목표**: 정산이 끝난 뒤 적재된 품목도 한 번 정산된다. 관리자가 그 달을 다시 실행하면 그 가게의 다음 회차 정산으로 바로 지급되고, 관리자가 아무것도 하지 않으면 다음 달 정기 정산에 들어간다.

**왜 서브인가**: Part 1~6의 완료 확인은 충족했다. 정산 consumer 처리가 DLT로 갔다가 그 달 정산(매월 5일 03:00) 뒤에 `AdminDltService.replay`로 처리되거나, 발행이 실패해 `DEAD`가 된 `order-line-confirmed` 이벤트를 그 뒤에 `AdminOutboxService.retry`로 다시 보낼 때만 드러난다.

### 빈 곳

| # | 위치 | 지금 | 깨지는 상황 |
|---|---|---|---|
| 1 | `JpaSettlementItemRepository.findShopIdsToSettle`·`assign` | 정산 대상을 `confirmed_at`이 정산 기간 안인 미정산 품목으로 고른다 | 10월 정산 뒤 적재된 10월 품목은 11월 정산 기간에도 맞지 않아 `settlement_id`가 NULL로 계속 남는다. 판매자는 그 금액을 받지 못하고, `SettlementWithdrawalPrecondition`이 미정산 품목을 보고 `PENDING_SETTLEMENT`를 돌려줘 탈퇴도 계속 막힌다(INV-07: 정산되지 않은 구매확정 품목이 남지 않는다) |
| 2 | `uk_settlement_shop_period`, `JpaSettlementRepository.insertIfAbsent` | 가게·기간마다 정산은 하나다 | 관리자가 `POST /admin/api/settlements/run?period=2026-10`으로 다시 실행해도 `insertIfAbsent`가 0을 돌려줘 그 가게를 건너뛴다. 운영자가 앞당겨 지급할 방법이 없다 |

바뀐 뒤의 동작은 다음과 같다.

| 상황 | 결과 |
|---|---|
| 늦은 품목이 적재된 뒤 관리자가 그 달을 실행 | 그 가게의 다음 회차 정산(`seq` = 2)이 만들어지고 같은 실행의 `payoutAll`이 지급한다 |
| 관리자가 실행하지 않음 | 다음 달 정기 정산이 기간 끝 전의 미정산 품목을 모두 넣으므로 그 달 정산에 들어간다 |
| 같은 달을 다시 실행했는데 늦은 품목이 없음 | 대상 가게가 없어 아무것도 만들지 않는다(FR-STL-04) |

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
| `SettlementRepository`·`JpaSettlementRepository.nextSeq(UUID shopId, LocalDate start)` → `int` | native `SELECT COALESCE(MAX(seq), 0) + 1 FROM settlement.settlement WHERE shop_id = :shopId AND period_start = :start`. 커밋된 행만 보이므로 같은 가게·기간을 동시에 정산하는 두 트랜잭션은 같은 값을 받을 수 있고, 그 경우는 `insertIfAbsent`가 막는다 |
| `insertIfAbsent(UUID id, UUID shopId, LocalDate start, LocalDate end, int seq, int bp, Instant now)` | `int seq` 인자를 `end` 뒤에 더하고 INSERT 칸에 `seq`를 넣는다. 충돌 대상은 `ON CONFLICT ON CONSTRAINT uk_settlement_shop_period_seq DO NOTHING` |
| `SettlementRepository.deleteById(UUID id)` | 도메인 인터페이스에 선언한다. 구현은 `JpaRepository`의 것이다 |
| `JpaSettlementItemRepository.findShopIdsToSettle(Instant to, UUID afterShopId, int limit)` | `from` 인자와 `confirmed_at >= :from` 조건을 뺀다. 나머지(`confirmed_at < :to AND settlement_id IS NULL AND shop_id > :afterShopId ORDER BY shop_id LIMIT :limit`)는 그대로다 |
| `assign(UUID settlementId, UUID shopId, Instant to, Instant now)` → `int` | `from` 인자와 조건을 빼고, 반환 타입을 `void`에서 바꾼 행 수(`int`)로 바꾼다 |

**4) `SettlementService`**

`run(YearMonth month)`은 `from`을 만들지 않고 `findShopIdsToSettle(to, after, 500)`을 부른다. 나머지는 그대로다.

`settleShop(UUID shopId, SettlementPeriod period)` 순서 (`run`의 `tx.execute` 안, 한 트랜잭션)

1. `int seq = settlementRepository.nextSeq(shopId, period.start())`.
2. `insertIfAbsent(settlementId, shopId, period.start(), period.end(), seq, bp, now)`가 0이면 `false`를 돌려준다. 같은 가게·기간을 동시에 정산하는 다른 트랜잭션이 같은 회차를 먼저 넣은 경우다. PostgreSQL은 커밋 전의 같은 키 행이 있으면 그 트랜잭션이 끝날 때까지 기다린 뒤 충돌 여부를 정한다.
3. `int assigned = settlementItemRepository.assign(settlementId, shopId, period.toExclusive(zone), now)`. 0이면 `settlementRepository.deleteById(settlementId)` 뒤 `false`를 돌려준다. 다른 실행이 그 품목들을 먼저 가져간 경우이고, 지우지 않으면 빈 정산이 0원 `PAID`와 지급 알림으로 이어진다.
4. 기존대로 `sumBySettlementId` → `calculate` → `true`.

3번의 0행은 동시 실행에서 나온다. 예를 들어 11월 정기 실행과 관리자의 10월 실행이 같은 늦은 품목을 겨냥하면, 먼저 UPDATE한 쪽이 품목 행 락을 잡고 늦은 쪽의 UPDATE는 기다렸다가 `settlement_id IS NULL` 조건을 다시 평가해 그 행을 건너뛴다(READ COMMITTED). 품목은 `settlement_id` 한 칸이라 어느 쪽이 가져가도 한 번만 정산된다.

`runManually`는 그대로다(끝난 달만 받는다).

**5) 응답** — `SettlementResult`·`SettlementResponse`·`AdminSettlementResult`·`AdminSettlementResponse`에 `int seq`를 `periodEnd` 뒤에 더한다. 판매자 정산 목록에 같은 기간의 정산이 둘 나올 때 구분하는 값이다.

**6) 테스트 보조** — `support/`

| 대상 | 규격 | 쓰는 곳 |
|---|---|---|
| `SettlementSnapshot`에 `int seq` | `periodEnd` 뒤에 더한다. `settlementSnapshotOf`의 세 메서드가 SELECT에 `seq`를 더해 채운다 | 아래 3·13번 |
| `settlementSnapshotOf(UUID shopId, YearMonth month)` | 쿼리 끝에 `ORDER BY seq`를 더한다. 1회차를 돌려주므로 기존 호출은 그대로다 | 기존 테스트, 11번 |
| `settlementSnapshotOf(UUID shopId, YearMonth month, int seq)` | 위 메서드에 `AND seq = ?` 조건을 더한 것이다. 없으면 `null` | 3·13번 |

기존 테스트 하나를 고친다: `SettlementServiceTest` 5번의 스파이 설정은 `insertIfAbsent` 인자가 하나 늘어 `insertIfAbsent(any(), eq(badShopId), any(), any(), anyInt(), anyInt(), any())`가 된다.

### 완료 확인

케이스는 [로드맵 README 4.1](../03-roadmap/README.md)의 기준으로 골랐다. 공통 값(정산 월 `YearMonth.of(2026, 9)`, 확정 시각 `Instant.parse("2026-09-15T03:00:00Z")`, 품목 7,500원)은 [3-4a 완료 확인](../03-roadmap/part3-events.md)과 같다. 3-4a의 4번(기간 경계)은 8월 → 9월 → 10월 순서로 실행하므로 바꾸지 않아도 통과한다.

#### `settlement/application/SettlementServiceTest`

**3. 정산 뒤 늦게 적재된 품목은 같은 달을 다시 실행하면 2회차로 정산된다 (고침)** — 준비: 가게 1곳 × 품목 2개(각 7,500원) → `run(YearMonth.of(2026, 9))`. 이어서 같은 가게의 9월 15일 확정 품목을 `insertSettlementItem(shopId, 7500, 위 확정 시각)`으로 하나 더 넣는다(`lateLineId`). 동작: `run(YearMonth.of(2026, 9))`을 다시(결과를 `second`로 받는다).
- 반환값(`second.settledShops()`): 1.
- DB(`settlementSnapshotOf(shopId, YearMonth.of(2026, 9), 2)`): `grossAmount()` = 7500, `itemCount()` = 1.

**11. 늦게 적재된 품목은 다음 달 정산에 들어간다** — 준비: 가게 1곳 × 품목 2개(각 7,500원) → `run(YearMonth.of(2026, 9))`. 이어서 같은 가게에 9월 15일 확정 품목 하나(`insertSettlementItem(shopId, 7500, 위 확정 시각)`)와 10월 15일 확정 품목 하나(`insertSettlementItem(shopId, 5000, Instant.parse("2026-10-15T03:00:00Z"))`)를 넣는다. 동작: `run(YearMonth.of(2026, 10))`.
- DB(`settlementSnapshotOf(shopId, YearMonth.of(2026, 10))`): `grossAmount()` = 12500, `itemCount()` = 2.

**12. 정산할 품목이 없으면 회차를 만들지 않는다** — 동시 실행에서 늦은 쪽이 `assign` 0행을 받는 경로를 직접 부른다. 준비: 가게 1곳 × 품목 2개 → `run(YearMonth.of(2026, 9))`. 동작: `transactionTemplate.execute(_ -> settlementService.settleShop(shopId, SettlementPeriod.of(YearMonth.of(2026, 9))))`(`TransactionTemplate`은 주입받는다).
- 반환값: `false`.
- DB(`countOf("settlement.settlement")`): 1.

**13. 2회차를 만들어도 1회차는 바뀌지 않는다** — 준비와 동작은 3번과 같다.
- DB(`settlementSnapshotOf(shopId, YearMonth.of(2026, 9), 1)`): `grossAmount()` = 15000, `itemCount()` = 2.

#### `settlement/web/SettlementControllerTest`

**같은 기간의 정산은 회차로 구분된다** — 준비: 판매자 1명과 가게 1개, 그 가게에 9월 15일 확정 품목 2개 → `settlementService.run(YearMonth.of(2026, 9))` → 같은 확정 시각의 품목 하나를 더 넣고 `run(YearMonth.of(2026, 9))`을 다시. 동작: 판매자가 `GET /api/shops/{shopId}/settlements`.
- 응답(andExpect): 상태 200, `$.items[0].seq` = 2, `$.items[1].seq` = 1.

### 문서 반영

단계를 마칠 때 Claude가 고친다.

| 문서 | 고칠 곳 |
|---|---|
| [요구사항](../01-requirements/README.md) | FR-STL-04를 "정산 재실행은 이미 정산된 품목을 다시 정산하지 않는다. 정산 뒤 적재된 품목은 그 기간을 다시 실행하면 다음 회차로, 실행하지 않으면 다음 달 정산으로 정산된다"로 |
| [ERD](../02-design/03-erd.md) | `settlement`에 `seq`, 유니크 키 `(shop_id, period_start, seq)`, 정산 실행 설명 |
| [API 명세](../02-design/07-api-spec.md) | 정산 응답과 관리자 정산 항목에 `seq`, 수동 실행 설명 |
| [ADR-009](../adr/ADR-009-settlement.md) | 결과 절의 늦은 품목 처리 |
| [로드맵 3-4](../03-roadmap/part3-events.md) | 정책의 "정산 실행 뒤에 늦게 도착한 전월 품목" 문단, 3-4a 완료 확인 3번 |

**리뷰 때 물어볼 것**

- `nextSeq`로 같은 회차를 읽은 두 트랜잭션은 어떻게 끝나나? `ON CONFLICT DO NOTHING`이 상대 트랜잭션을 기다리지 않으면 무엇이 생기나?
- `assign`이 0행일 때 정산 행을 지우지 않으면 지급과 알림에서 무엇이 나가나?
- 10월 2회차와 11월 정산 중 어느 쪽이 먼저 늦은 품목을 가져가도 품목이 한 번만 정산되는 근거는?
