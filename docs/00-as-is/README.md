# Phase 0. 현행(As-Is) 분석

> 대상: 팀 프로젝트 `my_routine` (develop 브랜치, 2026-10 기준, 커밋 88c510fb)
> 목적: ① 내가 구현하지 않은 영역까지 전체 구조 파악 ② v2를 "왜, 어떻게" 다시 설계하는지에 대한 근거 확보

## 문서 구성
| 문서 | 내용 |
|---|---|
| [01-service-map.md](01-service-map.md) | 서비스 구성, 인프라, 서비스 간 의존(Feign/Kafka) 그래프 |
| [02-domain-model.md](02-domain-model.md) | 서비스별 엔티티, 상태(enum), 소유 데이터 |
| [03-core-flows.md](03-core-flows.md) | 핵심 비즈니스 흐름 시퀀스 다이어그램 |
| [04-issues.md](04-issues.md) | 결함·리스크 목록 (심각도, 근거 파일, v2 방향) |

## 한눈에 보는 요약

**도메인**: 판매자가 가게(Shop)를 열어 상품을 팔고, 구매자는 단건 구매 또는 정기 구독을 하는 오픈마켓. 결제는 **예치금(지갑)** 방식 — Toss는 지갑 충전에만 쓰이고, 주문은 지갑 잔액에서 차감된다.

**구성**: 비즈니스 서비스 8개(member, shop, catalog, order, payment, wallet, support, batch) + Spring Cloud(gateway, eureka, config) + 인프라(Postgres/pgvector, Redis, Kafka, RabbitMQ, Elasticsearch).

**성숙도 편차가 크다**
- 상: shop/member의 가게 등록·삭제 Saga (Outbox + SKIP LOCKED, Retry→DLT→DEAD, advisory lock, 상태 전이 멱등, 설계 문서·시퀀스 다이어그램 존재)
- 중: catalog 재고 (원자적 조건부 차감, 예약 CAS 전이, 소비 멱등 테이블, 동시성 테스트 존재)
- 하: order/payment/wallet 핵심 결제 흐름 (클라이언트 가격 신뢰, 트랜잭션 내 원격 호출, 보상 누락, 대사 부재)

**횡단 관심사의 공백**
- 테스트: 루트 `build.gradle`의 `useJUnitPlatform()`이 주석 처리 → catalog 외 서비스는 CI에서 테스트가 사실상 실행되지 않음
- 관측성: actuator health만 존재. 트레이싱/메트릭/구조화 로그 없음
- 보안: JWT 서명 키가 `config/config/application.yml`에 커밋됨, CORS `*`+credentials, `Member-Id` 헤더 스푸핑 가능

## v2로 계승할 자산
| 자산 | 위치 | 활용 |
|---|---|---|
| 가게 등록/삭제 Saga 설계 문서 | [references/original-saga/](../references/original-saga/) (원본: `shop-service/docs/`) | Outbox/Inbox/DLT 표준 패턴의 원형 → v2 공통 모듈로 일반화 |
| 인가 엔드포인트 캐시 성능 실험 | [references/experiments/authorization-endpoint-cache-performance.md](../references/experiments/authorization-endpoint-cache-performance.md) (VUS 300에서 RPS +16%, avg -14%) | v2 게이트웨이 인가 ADR의 Baseline 수치 |
| 게이트웨이 하이브리드 캐싱 설계 | [references/experiments/gateway-caching-strategy.md](../references/experiments/gateway-caching-strategy.md) | 인가 ADR 대안 비교 자료 |
| 개선 아이디어 메모 | [must.md](../references/notes/must.md), [optional.md](../references/notes/optional.md) | Phase 1 요구사항·정책 결정 목록에 반영 |
| 재고 차감/예약 로직 | `catalog-service/.../inventory/` | 원자적 UPDATE + 예약 CAS 방식 유지 |
| 면접 예상 질문 | [interview-questions.md](../references/notes/interview-questions.md) | 각 ADR이 해당 질문에 답하도록 작성 |

## 검증 표기
`04-issues.md`의 각 항목은 근거 파일 경로를 포함한다.
- ✅ 코드를 직접 열어 확인한 항목
- 🔎 코드 탐색으로 식별, 경로 기재 (구현 착수 전 해당 영역 재확인 권장)
