# ADR-009. 정산은 settlement 모듈이 소유하고, keyset 순회 + 품목 할당 방식으로 배치한다

- 상태: 승인 (2026-10-02)
- 관련: [06-sequences.md §6](../02-design/06-sequences.md), INV-07, NFR-PERF-05

## 맥락
- As-Is: 정산 원천·결과는 shop schema에 있었고, batch-service가 shop DB에 직접 접속했다(BAT-03). 청크 트랜잭션 매니저가 달라서 원자성이 없었고(BAT-01), PENDING 조건 offset 페이징 중에 상태를 바꿔서 정산이 누락됐다(BAT-02).

## 결정
1. `settlement` 모듈을 따로 두고 정산 데이터(`settlement_item`, `settlement`)를 소유한다.
2. 정산 대상은 `order-line-confirmed` 이벤트로 적재한다(`order_line_id` UK로 멱등). 주문 데이터를 직접 읽지 않는다.
3. 배치는 **가게 ID keyset 순회**로 한다. 가게별 한 트랜잭션에서 `settlement` INSERT(UK shop_id+기간) + 미정산 item에 `settlement_id` 할당 + 할당된 행 기준으로 합계를 계산한다.
4. 지급은 정산 트랜잭션과 분리한다. 지급 연동(`PayoutGateway`, 은행 송금 Mock)을 **트랜잭션 밖에서** 멱등 키 = settlementId로 호출하고 결과를 기록한다. 실패하면 PAYOUT_FAILED로 두고 다음 실행에서 재시도한다. (2026-10-02: 예치금 제거로 wallet 입금 → 지급 Mock으로 변경)
5. Spring Batch 없이 시작한다. 100만 건 10분 목표를 못 맞추면 가게 범위 파티셔닝 병렬 처리를 도입한다(그때 Spring Batch Partitioner 검토).

## 고려한 대안
| 대안 | 기각 이유 |
|---|---|
| shop 모듈에 정산 포함 (As-Is) | 가게 관리와 돈 계산은 변경 이유가 다름. Stage 3에서 분리 대상이 될 것 |
| order 모듈에서 바로 집계 | 주문 테이블에 정산 조회 부하. 정산 장애가 주문에 영향 |
| Spring Batch로 시작 | 메타 테이블·잡 구성 비용 대비 Stage 1 규모에서 이점이 적음. 필요가 측정되면 도입 |

## 결과
- 재실행해도 같은 가게·기간은 UK 충돌로 건너뛰므로 중복 정산이 0이다(FR-STL-04).
- "구매확정됐는데 정산 item이 없는" 경우(이벤트 처리 지연·실패)를 점검 쿼리로 탐지한다 → 정산 실행 전에 consumer lag 0을 확인한다.

## 면접 연결 ([면접 질문](../references/notes/interview-questions.md) 1.6 배치 관련)
- "offset 페이징으로 상태를 바꾸며 읽으면 왜 누락되나요?" — 원 프로젝트에서 발견한 실제 결함
- "정산 재실행 시 중복은 어떻게 막나요?"
