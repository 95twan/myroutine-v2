# 서브

> 버전 0.11 · 2026-11-14 · 가게 개설·폐업 처리가 회원 행을 잠근 뒤 활성 가게 수로 SELLER·USER를 맞추게 하는 SUB-22를 더했다
> 0.10 · 2026-11-13 · 일어난 지 24시간이 지난 진행 알림을 보내지 않고 `EXPIRED`로 남기는 SUB-21을 더했다
> 0.9 · 2026-11-13 · 대사가 PG 밖에서 취소된 결제의 주문을 `PAID`로 닫지 않게 하는 SUB-20을 더했다
> 0.8 · 2026-11-12 · 같은 주문의 대사가 동시에 돌지 않게 해 주문이 `PAID`인 결제를 취소하지 않는 SUB-19를 더했다
> 0.7 · 2026-11-10 · 정산이 끝난 기간에 늦게 적재된 품목을 그 기간의 다음 회차 또는 다음 달 정산으로 정산하는 SUB-18을 더했다
> 0.6 · 2026-11-09 · 화면을 붙이며 드러난 응답 빈칸(판매자 상품 상세, 실패 환불의 주문번호·구매자, 구매자 환불의 거절 사유)을 SUB-17로 더했다
> 0.5 · 2026-11-08 · Stage 2에서 남긴 개선(outbox 릴레이 지연, 상품 조회 캐시, 남은 요구사항 측정)을 SUB-14~SUB-16으로 더했다
> 0.4 · 2026-11-06 · Stage 1.5(k3s 전환·무중단 배포)를 옮겨 왔다(단계 ID SUB-9\~SUB-13, 옛 K-1\~K-5)
> 0.3 · 2026-11-05 · 6-1이 남긴 로그 빈 곳(알림 발송 실패, 도메인 상태 전이, JWT 거부 사유)을 SUB-8로 더했다
> 0.2 · 2026-11-02 · 로드맵의 옛 5-3 무중단 재색인을 옮겨 왔다(단계 ID SUB-7)
> 0.1 · 2026-10-31 · 로드맵에서 구독(옛 Part 5), 취향 추천(옛 6-4), 월간 리뷰 요약(옛 6-5)을 빼서 옮겨 오고 단계 ID를 SUB-1~SUB-6으로 붙였다

[로드맵](../03-roadmap/README.md)(Part 1\~6, Stage 1 `v1.0.0`)에서 뺀 기능이다. Stage 1 뒤 여유가 있을 때 하고, 시기는 그때 정한다. Stage 2의 선행 조건이 아니다. Stage 2에서 하지 않은 개선(SUB-14\~SUB-16), 화면을 붙이며 드러난 응답 빈칸(SUB-17), consumer 점검에서 드러난 정산 빈 곳(SUB-18), 대사의 동시 실행(SUB-19), PG 밖에서 취소된 결제의 대사(SUB-20), 때 지난 알림(SUB-21), SELLER 역할 맞추기(SUB-22)도 여기에 둔다.

단계 ID는 로드맵 단계(`Part-번호`)와 겹치지 않게 `SUB-1`~`SUB-22`로 붙인다.

| 기능 | 단계 | 문서 | 요구사항 |
|---|---|---|---|
| 구독 | SUB-1 빌링키 등록·조회·삭제, SUB-2 구독 신청·관리, SUB-3 구독 회차 실행 잡, SUB-4 가격 변경·종료 이벤트 반영 | [구독](subscription.md) | FR-SUB-01~07, FR-PAY-07 |
| 취향 추천 | SUB-5 | [취향 추천](recommendation.md) | FR-REC-01 |
| 월간 리뷰 요약 | SUB-6 | [월간 리뷰 요약](review-summary.md) | FR-REV-03 |
| 무중단 재색인 | SUB-7 | [무중단 재색인](search-reindex.md) | FR-SRC-02 |
| 로그 보강 | SUB-8 | [로그 보강](log-coverage.md) | - |
| k3s 전환·무중단 배포 | SUB-9 k3s 구성과 수동 배포, SUB-10 probe·롤링, SUB-11 CD 교체, SUB-12 나머지 인프라 이전, SUB-13 장애 주입·비교 리포트 | [ADR-012](../adr/ADR-012-kubernetes-zero-downtime.md) | NFR-REL |
| Stage 2에서 남긴 개선 | SUB-14 outbox 릴레이 지연, SUB-15 상품 조회 캐시, SUB-16 남은 요구사항 측정 | [Stage 2에서 남긴 개선](stage2-leftovers.md) | NFR-PERF-04~07 |
| 응답 보완 | SUB-17 | [응답 보완](response-gaps.md) | - |
| 늦게 적재된 정산 품목 | SUB-18 | [늦게 적재된 정산 품목](settlement-late-items.md) | FR-STL-04, INV-07 |
| 대사의 동시 실행 | SUB-19 | [대사의 동시 실행](reconcile-concurrency.md) | FR-PAY-03, INV-02 |
| PG 밖에서 취소된 결제의 대사 | SUB-20 | [PG 밖에서 취소된 결제의 대사](pg-external-cancel.md) | FR-PAY-03 |
| 때 지난 알림 | SUB-21 | [때 지난 알림](notification-expiry.md) | FR-NTF-01 |
| SELLER 역할 맞추기 | SUB-22 | [SELLER 역할 맞추기](seller-role-sync.md) | FR-SHOP-01·03, INV-09 |

## 진행 방식

- 단계 하나 = 브랜치 하나 = PR 하나 = 리뷰 하나는 로드맵과 같다. 브랜치명은 `feature/sub-1-billing-key`처럼 단계 ID로 시작한다.
- 착수("SUB-1 진행할게")를 알리면 아래 "구현 때 정할 것"의 그 단계 행을 먼저 풀고 테스트 골격을 만든다([CLAUDE.md §2](../../CLAUDE.md)).
- 기능 하나(구독 전체, 취향 추천, 월간 리뷰 요약, 무중단 재색인, 로그 보강, k3s 전환 전체)를 마칠 때마다 다음 MINOR(`v1.x.0`)로 릴리스한다([Git 정책 §7](../git-policy.md)).

## 무엇이 새로 등장하나

| 단계 | 인프라·도구 | 개념·패턴 |
|---|---|---|
| SUB-1 | Toss 빌링 | 정기결제 |
| SUB-5 | pgvector, Spring AI | 임베딩, 유사도 검색 |
| SUB-7 | - | 이중 쓰기, alias 원자 교체 |
| SUB-8 | - | - |
| SUB-9~SUB-13 | k3s, Kustomize | Deployment, StatefulSet, PVC, probe, PDB, RBAC, 롤링 배포 |

## 미결 정책

권장안으로 진행한다. 바꾸려면 해당 단계 시작 전에 알려주고, [요구사항 §2](../01-requirements/README.md)에 정책으로 추가한다.

| ID | 질문 | 권장안 | 결정 시점 |
|---|---|---|---|
| OPEN-04 | 활성 구독이 쓰는 빌링키를 삭제하면? | 409로 거부 | SUB-1 |
| OPEN-05 | 구독 상품 가격이 내려가면? | 다음 회차부터 바로 적용 | SUB-4 |

## Part 6에 더할 것

서브는 Part 6 뒤에 하므로, Part 6이 만든 로그 점검·지표·E2E 시나리오·불변식 점검에 서브 기능을 더한다.

- 구독: [구독 문서의 "Part 6에 더할 것"](subscription.md#part-6에-더할-것)
- 취향 추천·월간 리뷰 요약: OpenAI 호출 지표. 둘 중 먼저 하는 단계(SUB-5 또는 SUB-6)를 마칠 때 6-2에 아래를 더한다.
  - 6-2 지표 표에 행을 더한다.

| 이름 | 종류 | 계산 | 어디서 |
|---|---|---|---|
| `openai_call_seconds{operation,result}` | Timer | 호출마다 | OpenAI 호출부 |

  - 6-2 "2) 비즈니스 메트릭"에 설명을 더한다: OpenAI를 부르는 곳(SUB-5의 `EmbeddingModel.embed`, SUB-6의 `ChatClient` 호출부)에서 잰다. 방식은 `Timer.start(registry)` → 호출 → `sample.stop(registry.timer("openai_call_seconds", "operation", ..., "result", "success" 또는 "failure"))`(예외가 나도 `finally`에서 `stop`)이며 `operation` 값은 정해져 있지 않다(확인 필요).

## 구현 때 정할 것

각 항목은 해당 단계를 구현할 때 정하고, 정하면 그 단계 문서에 반영한 뒤 이 표에서 지운다. "확인 필요" 표의 종류는 다음과 같다.

| 종류 | 뜻 | Claude가 하는 일 |
|---|---|---|
| A | 설계 결정이 비어 있음 | 선택지와 권장안을 제시해 사용자 결정을 받고, 문서에 반영한다 |
| B | 외부 라이브러리·API·도구 동작 확인 | 공식 문서·소스·실험으로 확인한다. 외부 조회가 필요하면 사용자에게 먼저 묻는다. 기동 실험은 사용자가 구현한 뒤에 하는 것이 많아 그때 확인한다 |
| C | 문서 간 또는 문서-코드 어긋남 | 어느 쪽이 맞는지 확인해 문서를 고친다 |
| D | 구현 방식 선택(권장안 있음) | 권장안을 문서에 확정해 적는다. 쓰다 바꿔도 된다 |
| E | 이미 코드에서 정해짐 | 코드를 확인해 문서의 "확인 필요"를 확정 문장으로 바꾼다 |

위치 열은 단계 번호와 절 이름(`module.layer` 또는 문서 제목)으로 적는다. 단계와 상관없이 찾은 문제는 [알려진 문제](../known-issues.md)에 둔다.

### 구현 단계에서 정할 것

| 단계 | 항목 | 선택지와 영향 |
|---|---|---|
| SUB-3 | `Order.markPaymentInProgress()` 신설 | 방금 만든 구독 주문에 2-4 `startPayment` CAS를 걸던 것을 대체했다. 거부하면 CAS로 되돌리고 0행 처리를 정한다 |
| SUB-3 | `callBillingCharge`가 `Payment`와 `BillingKey`를 다시 읽는다 | `callConfirm`과 같은 선례다. order가 값을 직접 넘기게 바꿀 수도 있다 |
| SUB-3 | 건너뜀 경로에서 `insertIfAbsent`가 0행일 때 | `runDue` 1.2와 같게 "종료"를 기본값으로 적었다 |
| SUB-1 | SUB-1이 추가하는 `FakePgServer` 발급·빌링 결제 동작을 누가 쓰나 | CLAUDE.md의 Claude 작성 예외는 2-3의 `FakePgServer.java`에 한정된다 |
| SUB-5 | `recommend`의 평균 벡터를 문자열로 받아 `CAST(... AS vector)`로 다시 넘기는 쿼리 두 개 | CTE 하나로 합치면 `<=>`에 서브쿼리 값이 들어가 HNSW 인덱스를 못 쓸 가능성이 크다 |

### 확인 필요

| 단계 | 종류 | 항목 | 위치 | 풀 방법 |
|---|---|---|---|---|
| SUB-1 | A | 등록창의 `customerKey`와 발급 시 서버의 `customerKey`를 같게 맞추는 방법(DTO는 `authKey`만 받음) | SUB-1 Toss 빌링 API 요약, payment.application | 사용자 결정(회원별 고정 키 또는 DTO), Toss 문서 대조 |
| SUB-1 | A | 이미 `DELETED`인 빌링키를 다시 삭제할 때의 응답(404 또는 멱등 204) | SUB-1 payment.application | 사용자 결정 |
| SUB-1 | D | `decrypt` 실패 예외 타입, 로그에 `paymentId`가 없을 때 칸, 빌링키 목록 정렬(`createdAt desc`) | SUB-1 common.crypto, payment.infrastructure, payment.application | 확정 |
| SUB-2 | C | `SubscriptionResult`가 도메인 `ShippingAddress`를 노출함(2-2는 `ShippingAddressResult`) | SUB-2 order.application | 문서를 맞추거나 예외로 명시 |
| SUB-2 | D | `OWN_SHOP_PRODUCT`의 `details`, PATCH 200 본문과 응답 필드 | SUB-2 order.application, order.web | 체크아웃·`SubscriptionResult`에 맞춤 |
| SUB-3 | A | 구매자의 일시정지·재개·해지(SUB-2)에서도 `subscription-status-changed`를 낼지 | SUB-3 order.api.event | 사용자 결정 |
| SUB-3 | D | `OrderType` enum 전환, 회차 이력 keyset, `findDueIds`의 첫 페이지 `lastId`(`null` 허용, 첫 페이지 메서드를 따로 두기, `new UUID(0,0)` 중 권장은 `new UUID(0,0)`), 알림 type 이름·문구, outbox `aggregateType`, `runDue()` 반환값, `orderName` 100자 자름, SKIPPED 사유 문자열과 `consecutiveFailures` 처리, `onOrderClosed` 회차 없음은 예외, SUSPENDED 이벤트 `reason` | SUB-3 order.domain, order.api.event, order.application, order.web, notification.domain·notification.application·notification.infrastructure, 완료 확인 | 권장안으로 확정 |
| SUB-4 | A | 가격 변경 이벤트의 `oldPrice`·`effectiveDate`, 특히 인하 시 즉시 적용인지 | SUB-4 정책, order.api.event, 완료 확인 | 사용자 결정 |
| SUB-4 | D | 가격 알림 type·문구, 종료 이벤트 `reason`은 `endReason`과 같게, 판매자 목록 쿼리·클래스 이름 | SUB-4 order.domain, order.application, order.web, notification.domain·notification.application·notification.infrastructure, 완료 확인 | 확정 |
| SUB-5 | A | `member_purchase.purchased_at`에 넣을 시각(이벤트에 결제 시각 없음, 후보 `occurredAt`) | SUB-5 recommendation.infrastructure | 사용자 결정 |
| SUB-5 | A | "이미 산 상품 제외"가 최근 20개만인지 전체 구매 이력인지, 90일 내 20개 선택 순서 | SUB-5 recommendation.application | 사용자 결정 |
| SUB-5 | A | 탈퇴(`member-withdrawn`)가 구매 이력·캐시를 지우는 테스트를 넣을지(Part 3 의존) | SUB-5 recommendation.infrastructure (`member-withdrawn` consumer) | 사용자 범위 결정 |
| SUB-5 | B | Spring AI 2.0이 `base-url` 뒤에 `/v1`을 붙이는지, pgvector가 쉼표 뒤 공백을 허용하는지, 가짜 서버가 받는 `input`이 문자열인지 배열인지 | SUB-5 인프라·설정, 완료 확인 | 요청 로그·pgvector 실험 |
| SUB-5 | D | `JdbcTemplate` 선택, `InboxGuard`에 조회 메서드, 서비스·consumer 역할, 임베딩 `model`은 설정값, 취향 벡터 NULL이면 빈 목록 | SUB-5 common.inbox, recommendation 모듈 경계와 SQL 접근, recommendation.application, 완료 확인 | 확정 |
| SUB-6 | A | `period_end`에 넣을 날짜(말일 포함 권장, 3-4 정산과 동일) | SUB-6 review.application, 완료 확인 | 사용자 결정 |
| SUB-6 | A | 대표 리뷰 쿼리가 전체 ACTIVE인지 대상 월만인지, `source_review_count`가 프롬프트 개수인지 전체인지 | SUB-6 review.application, 완료 확인 | 사용자 결정 |
| SUB-6 | B | `gpt-4o-mini`가 착수 시점의 저가 모델인지, Spring AI 2.0.1 `ChatClient` 호출 형태, chat `content`가 문자열인지 배열인지 | SUB-6 정책, review.application, 완료 확인 | 공식 문서·요청 로그 확인 |
| SUB-6 | D | `review_summary` 읽기 repository, 요약 `model`은 설정값, `latestSummary` JSON 키 | SUB-6 review.application, 완료 확인 | 확정 |
| SUB-7 | A | 재색인 실패 시 `reindex()`가 예외를 던질지 | SUB-7 search.application | 사용자 결정 |
| SUB-7 | B | alias 교체 전 `refresh`가 필요한지(기본 1초) | SUB-7 search.application | ES 실험 |
| SUB-8 | A | 도메인 상태 전이 로그의 범위(전부 vs 돈·재고가 움직이는 전이만) | SUB-8 order·payment·settlement application | 사용자 결정 |
| SUB-8 | A | JWT 거부 로그 레벨(`warn` vs `debug`). 만료는 정상 흐름이라 `warn`이면 로그가 넘칠 수 있다 | SUB-8 common.security | 사용자 결정 |
| SUB-8 | D | JWT 거부 로그를 `JwtProvider`와 `JwtAuthenticationFilter` 중 어디서 찍을지, 사유 구분 방식 | SUB-8 common.security | 확정 |
| SUB-9 | A | 노드 수, 상태 서비스(Postgres·Redis·Kafka·ES) 위치, 도구. 제안은 단일 노드, 클러스터 안 StatefulSet, plain YAML + Kustomize다 | ADR-012 결정 2~4항 | 사용자 결정 |
| SUB-9 | A | MinIO가 그때 VM에 있으면 클러스터에 올릴지 NAS로 옮길지 | ADR-012 결정 4항(MinIO 위치) | 사용자 결정 |
| SUB-9 | B | 24GB 호스트에서 멀티 노드가 가능한지, 클러스터에 올릴 때 local-path PVC에서 MinIO uid 65532용 `fsGroup`이 필요한지 | ADR-012 결정 2항(클러스터), 4항(클러스터의 MinIO) | 실측·k3s에서 확인 |
| SUB-9 | C | Postgres만 먼저 옮기는 순서가 지금 앱에 맞는지. Kafka·Redis·ES 없이 앱이 뜨지 않으면 SUB-9와 SUB-12를 다시 나눈다 | ADR-012 단계 개요 | 앱 기동으로 확인 |
| SUB-9 | D | ADR-012 단계 개요를 구체화할 문서의 파일 이름. 권장은 `docs/04-sub/k3s.md`다 | 이 문서 표 | 확정 |
| SUB-11 | D | 러너 ServiceAccount의 네임스페이스 한정 RBAC | ADR-012 결정 5항(CD) | Role/RoleBinding 작성·검증 |
| SUB-1·SUB-3 | A | `pg_call_seconds`에 빌링 호출(`issueBillingKey`·`billingCharge`)을 포함할지와 `operation` 값 | 구독 "Part 6에 더할 것" 2) | 사용자 결정 |
| SUB-5·SUB-6 | A | `openai_call_seconds`의 `operation`·`result` 라벨 값. ADR-010의 이 Timer를 대시보드·알림 어디에도 쓰지 않는다 | 위 "Part 6에 더할 것" | 사용자 결정(권장: 소문자 메서드명) |
