# 참고 자료 (원 프로젝트에서 가져온 본인 작업물)

> 팀 프로젝트 MyRoutin(2026년 초)을 진행하며 본인이 작성한 노트·실험·설계 문서의 사본이다. 내용은 **원본 그대로** 두고 고치지 않는다(당시 상태의 기록).
> v2 설계에서 이 자료를 근거로 쓰는 곳을 함께 적었다.

## notes/ — 개선 아이디어와 면접 준비
| 파일 | 원본 | 내용 | v2에서 쓰는 곳 |
|---|---|---|---|
| [must.md](notes/must.md) | `~/Desktop/my_routin/must.md` | 반드시 고쳐야 할 점 메모 (가게별 주문, 주문 스냅샷, 재고 증감, 상품 중복 생성 등) | [요구사항](../01-requirements/README.md) FR·POL 근거, As-Is 결함 목록 |
| [optional.md](notes/optional.md) | `~/Desktop/my_routin/optional.md` | 개선 후보 (내부 API 보호, 인증코드 제한, 트랜잭션 내 외부 호출, 가게 삭제 실패 시나리오 등) | 요구사항 NFR, [개발 가이드 §19](../development-guide.md) |
| [interview-questions.md](notes/interview-questions.md) | `~/Desktop/my_routin/면접 질문.md` | 원 프로젝트 기준 면접 예상 질문 | 각 ADR의 "면접 연결", 로드맵의 "리뷰 때 물어볼 것" |

## experiments/ — 성능 실험과 설계 검토
| 파일 | 원본 | 내용 | v2에서 쓰는 곳 |
|---|---|---|---|
| [authorization-endpoint-cache-performance.md](experiments/authorization-endpoint-cache-performance.md) | `~/Desktop/my_routin/authorization-endpoint-cache-performance.md` | 게이트웨이 인가 엔드포인트 캐시 k6 실험 (VUS 300에서 RPS +16%, 평균 지연 -14%) | [ADR-006](../adr/ADR-006-auth.md), Stage 2 인증 오버헤드 비교(NFR-PERF-04)의 Baseline |
| [gateway-caching-strategy.md](experiments/gateway-caching-strategy.md) | `~/Desktop/my_routin/API Gateway 캐싱 전략 및 동기화.md` | 게이트웨이 하이브리드 로컬 캐싱 설계 | ADR-006 대안 비교, Stage 3 게이트웨이 설계 |

## original-saga/ — 원 프로젝트 가게 등록·삭제 Saga 설계 (본인 구현)
| 파일 | 원본 | 내용 | v2에서 쓰는 곳 |
|---|---|---|---|
| [shop_register_saga_design.md](original-saga/shop_register_saga_design.md) | `my_routine/shop-service/docs/` | 가게 등록 + SELLER 부여 Saga (동기 → 재시도 → 멱등 → 이벤트 기반 오케스트레이션으로 발전) | [ADR-003](../adr/ADR-003-outbox-kafka.md) Outbox 일반화의 원형, 로드맵 3-1 |
| [shop_delete_saga_design.md](original-saga/shop_delete_saga_design.md) | 〃 | 가게 삭제 + SELLER 회수 Saga, 동시성 제어(advisory lock) | 로드맵 3-2, Stage 3 역할 변경 Saga |
| [member_role_change_saga_sequence_diagrams.md](original-saga/member_role_change_saga_sequence_diagrams.md) | 〃 | 등록·삭제 통합 시퀀스 다이어그램 | 〃 |
| [member_role_change_saga_test_plan.md](original-saga/member_role_change_saga_test_plan.md) | 〃 | Saga 테스트 계획 (P0~P2) | 로드맵 3-1·3-2 테스트 설계 참고 |

> v2 Stage 1에서는 소유권 기반 인가(ADR-006) 덕분에 이 Saga가 "이벤트 하나 + 멱등 consumer"로 단순해진다. Stage 3에서 member·shop을 서비스로 분리할 때 이 설계가 다시 필요해진다. **"원 프로젝트에서 Saga로 풀었던 문제를 v2에서는 왜 단순하게 풀 수 있었나"**는 좋은 면접 소재다.
