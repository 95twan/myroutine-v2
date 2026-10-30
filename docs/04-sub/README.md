# 서브

> 버전 0.1 · 2026-10-31 · 로드맵에서 구독(옛 Part 5), 취향 추천(옛 6-4), 월간 리뷰 요약(옛 6-5)을 빼서 옮겨 오고 단계 ID를 SUB-1~SUB-6으로 붙였다

[로드맵](../03-roadmap/README.md)(Part 1~6, Stage 1 `v1.0.0`)에서 뺀 기능이다. Stage 1 뒤 여유가 있을 때 하고, 시기는 그때 정한다. Stage 1.5(k3s)와 Stage 2의 선행 조건이 아니다.

단계 ID는 로드맵 단계(`Part-번호`)와 겹치지 않게 `SUB-1`~`SUB-6`으로 붙인다.

| 기능 | 단계 | 문서 | 요구사항 |
|---|---|---|---|
| 구독 | SUB-1 빌링키 등록·조회·삭제, SUB-2 구독 신청·관리, SUB-3 구독 회차 실행 잡, SUB-4 가격 변경·종료 이벤트 반영 | [구독](subscription.md) | FR-SUB-01~07, FR-PAY-07 |
| 취향 추천 | SUB-5 | [취향 추천](recommendation.md) | FR-REC-01 |
| 월간 리뷰 요약 | SUB-6 | [월간 리뷰 요약](review-summary.md) | FR-REV-03 |

## 진행 방식

- 단계 하나 = 브랜치 하나 = PR 하나 = 리뷰 하나는 로드맵과 같다. 브랜치명은 `feature/sub-1-billing-key`처럼 단계 ID로 시작한다.
- 착수("SUB-1 진행할게")를 알리면 아래 "구현 때 정할 것"의 그 단계 행을 먼저 풀고 테스트 골격을 만든다([CLAUDE.md §2](../../CLAUDE.md)).
- 기능 하나(구독 전체, 취향 추천, 월간 리뷰 요약)를 마칠 때마다 다음 MINOR(`v1.x.0`)로 릴리스한다([Git 정책 §7](../git-policy.md)).

## 무엇이 새로 등장하나

| 단계 | 인프라·도구 | 개념·패턴 |
|---|---|---|
| SUB-1 | Toss 빌링 | 정기결제 |
| SUB-5 | pgvector, Spring AI | 임베딩, 유사도 검색 |

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

  - 6-2 "7) common.mail"에 설명을 더한다: OpenAI를 부르는 곳(SUB-5의 `EmbeddingModel.embed`, SUB-6의 `ChatClient` 호출부)에서 잰다. 방식은 `mail_send_seconds`와 같이 `Timer.start` → 호출 → `stop`(예외가 나도 `finally`에서 `stop`)이며 `operation`·`result` 값은 정해져 있지 않다(확인 필요).

## 구현 때 정할 것

각 항목은 해당 단계를 구현할 때 정하고, 정하면 그 단계 문서에 반영한 뒤 이 표에서 지운다. 표의 형식과 종류(A~E)의 뜻은 로드맵의 [구현 때 정할 것](../03-roadmap/pending-decisions.md)과 같다.

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
| SUB-1·SUB-3 | A | `pg_call_seconds`에 빌링 호출(`issueBillingKey`·`billingCharge`)을 포함할지와 `operation` 값 | 구독 "Part 6에 더할 것" 2) | 사용자 결정 |
| SUB-5·SUB-6 | A | `openai_call_seconds`의 `operation`·`result` 라벨 값. ADR-010의 이 Timer를 대시보드·알림 어디에도 쓰지 않는다 | 위 "Part 6에 더할 것" | 사용자 결정(권장: 소문자 메서드명) |
