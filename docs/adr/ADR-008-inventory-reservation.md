# ADR-008. 재고: 조건부 UPDATE + 예약 레코드 + 수량 4분할, 대안은 Stage 2에서 측정 비교

- 상태: 승인 (2026-10-02)
- 관련: [03-erd.md product](../02-design/03-erd.md), INV-03·04, NFR-PERF-03

## 맥락
- As-Is의 장점: `UPDATE stock SET quantity = quantity - :q WHERE quantity >= :q` 원자 차감과 예약 CAS 전이, 동시성 테스트(As-Is catalog-service, 팀원 구현).
- As-Is의 문제: 예약 만료 없음(CAT-01), 판매자 재고 수정이 절대값 덮어쓰기(CAT-06), 복구 이력 추적 불가.

## 결정
1. `stock(available, reserved, sold, received)` 4개 수량으로 관리한다. 모든 전이는 조건부 UPDATE 한 문장이다. 불변식은 `available + reserved + sold = received`.
2. 예약은 `stock_reservation(order_id, product_id UK)` 레코드다. 만료는 주문 모듈의 만료 스윕이 주도한다(ADR-004).
3. 판매자 재고 변경은 **증감량(delta)**으로만 받는다: `available += delta, received += delta` (`available + delta >= 0` 조건).
4. 모든 수량 변화는 `stock_movement`에 기록하고, unique 제약으로 예약·복구의 멱등을 보장한다.
5. 여러 상품 예약 시 **상품 ID 오름차순으로 UPDATE**해 교차 주문 간 데드락을 막는다.

## Stage 2에서 비교할 대안 (2-5 핫스팟 실험)
| 방식 | 예상 특성 |
|---|---|
| A. 조건부 UPDATE (현 결정) | 단순, 정확. 인기상품 행에 락 대기 집중 |
| B. `SELECT FOR UPDATE` 후 차감 | A보다 락 보유 시간이 김. 기준선으로 측정 |
| C. Redis 원자 차감(Lua) + DB 비동기 확정 | 처리량 최고. Redis-DB 불일치 보정과 장애 시 복구가 필요 |
| D. 재고 버킷 분할(행 N개로 쪼갬) | 경합 분산. 잔여 수량이 적을 때 처리가 복잡 |

측정: 재고 100에 동시 1,000건 → 성공 정확히 100건, TPS, p95, DB 락 대기 시간. 결과에 따라 이 ADR을 갱신한다(supersede).

## 면접 연결 ([면접 질문](../references/notes/interview-questions.md) 2.2와 비교)
- "비관적 락 대신 조건부 UPDATE를 쓴 이유는?" → 락 보유 시간과 측정 결과
- "Redis로 재고를 관리하면 무엇이 문제인가요?" → C안 실험 결과와 보정 설계
