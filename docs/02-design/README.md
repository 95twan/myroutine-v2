# Phase 2. 설계서 (MyRoutin v2)

> 버전 0.1 · 2026-10-02 · 근거: [01-requirements](../01-requirements/README.md), [00-as-is](../00-as-is/README.md)

## 문서 구성
| 문서 | 내용 |
|---|---|
| [01-architecture-evolution.md](01-architecture-evolution.md) | Stage 1 모놀리스 → Stage 2 성능 개선 → Stage 3 MSA 전환 로드맵 |
| [02-architecture-stage1.md](02-architecture-stage1.md) | 시스템 구성, 모듈, 의존 규칙, 패키지 구조, 횡단 설계, 인프라, 테스트 전략 |
| [03-erd.md](03-erd.md) | 모듈별 ERD, 제약조건, 인덱스, 동시성 제어 방식 |
| [04-state-machines.md](04-state-machines.md) | 주문·가게주문·품목·환불·결제·예약·정산 지급·구독·회차 상태머신 |
| [05-events.md](05-events.md) | Outbox 릴레이, 이벤트 목록(토픽·payload·consumer) |
| [06-sequences.md](06-sequences.md) | 체크아웃, 결제 승인·대사, 만료, 취소, 구독 회차, 정산, 가게, 상품 이미지 업로드, 인증 |
| [07-api-spec.md](07-api-spec.md) | 공통 규약, 에러 코드, 엔드포인트 목록, 핵심 API 계약 |
| [../adr/](../adr/) | 설계 결정 기록 10건 |

## 핵심 결정 요약
| ADR | 결정 | As-Is 결함 대응 |
|---|---|---|
| [001](../adr/ADR-001-modular-monolith-first.md) | 모듈러 모놀리스로 시작, 측정 기반으로 MSA까지 진화 | INF-01·02·04 |
| [002](../adr/ADR-002-module-boundaries.md) | `api` 패키지만 공개, 단방향 동기 의존, 멱등 모듈 API, 의존 역전, schema 소유 | BAT-03, INF-04 |
| [003](../adr/ADR-003-outbox-kafka.md) | 직접 구현한 Outbox(리스 선점) + Kafka 단일 브로커 + consumer 멱등·DLT | ORD-09, CAT-03, PAY-03, SHOP-01·05, SUP-02·03 |
| [004](../adr/ADR-004-checkout-payment-consistency.md) | 서버 가격 확정, 주문 상태 CAS로 승인·만료 경합 차단, UNKNOWN + 대사 | ORD-01·02·10, PAY-01·02 |
| [005](../adr/ADR-005-wallet-ledger.md) | ~~예치금 원장~~ **폐기**(2026-10-02, 예치금 제거) | - |
| [006](../adr/ADR-006-auth.md) | 역할 클레임 JWT + tokenVersion + 소유권 기반 인가, refresh 회전 | MEM-01·06·08, ORD-08 |
| [007](../adr/ADR-007-data-layout.md) | 단일 Postgres·schema 분리, Flyway, UUIDv7, BIGINT 금액, DB 제약으로 불변식 | ORD-11, INF-03 |
| [008](../adr/ADR-008-inventory-reservation.md) | 재고 4분할 조건부 UPDATE + 예약 + 이력, 대안은 Stage 2 측정 | CAT-01·06 |
| [009](../adr/ADR-009-settlement.md) | settlement 모듈, 이벤트 적재, keyset + 품목 할당 배치 | BAT-01·02·03 |
| [010](../adr/ADR-010-observability.md) | ELK(로그·traceId) + Prometheus/Grafana(메트릭), 정합성 비즈니스 메트릭 | INF-02 |

## 불변식 → 설계 장치 매핑
| 불변식 | 보장 장치 |
|---|---|
| INV-01 금액 | 서버 가격 확정, `order_line.line_amount = unit_price × quantity` CHECK, 승인 시 금액 = `total_amount` 검증 |
| INV-02 PG 승인 1회 반영 | 주문 CAS, payment `order_id` UK, UNKNOWN + 대사, 미반영 승인 취소 |
| INV-03·04 재고 | 4분할 수량 조건부 UPDATE, 수량 CHECK ≥ 0, movement UK, 만료 스윕, 점검 잡 |
| INV-06 환불 한도 | `refunded_amount ≤ line_amount` CHECK, `cancelled_amount ≤ amount` CHECK, PG 취소 멱등키 |
| INV-07 정산 1회 | `settlement_item.order_line_id` UK, `settlement(shop_id, period_start)` UK |
| INV-08 회차 | `subscription_cycle(subscription_id, cycle_date)` UK, 회차 INSERT와 주문 생성을 한 트랜잭션으로 |
| INV-09 SELLER | shop-opened/closed 이벤트 + member의 재조회 |
| INV-10 이벤트 | Outbox + `processed_message` |
| INV-11 소유권 | 모든 변경 API에서 소유자 조건 조회, 인가 테스트 |

## 구현 규칙
코드 작성·리뷰 기준은 [개발 가이드](../development-guide.md)를 따른다.

## 다음 단계
Phase 3: [구현 로드맵](../03-roadmap/README.md) — 기능부터 만들고 도구는 필요해질 때 도입하는 Part 1~7. Git 규칙은 [Git 정책](../git-policy.md).
