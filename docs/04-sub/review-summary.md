# 서브: 월간 리뷰 요약

> 버전 0.1 · 2026-10-31 · 로드맵의 옛 6-5를 서브로 옮겨 왔다(단계 ID SUB-6). 평점 통계 응답의 `latestSummary`는 5-1에서 빼고 이 단계가 더한다

> **서브**: 로드맵에서 뺀 기능이다. Stage 1(Part 6) 뒤에 하고, [서브 안내의 "Part 6에 더할 것"](README.md#part-6에-더할-것)을 함께 반영한다(취향 추천에서 이미 반영했으면 건너뛴다).
> **따르는 규칙**: 로드맵 Part 1~6의 공통 규칙. 외부 호출·Spring AI 버전은 [Part 5](../03-roadmap/part5-review-search.md) 공통 규칙 X다. 5-1(리뷰)이 만든 `review` 모듈을 고친다.
> **인프라 추가**: OpenAI(Spring AI)

---

## SUB-6. 월간 리뷰 요약 (S)
**목표**: 매월 1일 상품별 리뷰를 LLM으로 요약한다. 직전 요약 이후 새 리뷰가 없으면 건너뛴다.

**정책 (이 단계에서 정함)**
- 프롬프트에 넣는 리뷰: 좋아요 많은 순 30개 + 별점 낮은 순 10개(중복 제외, 최대 40개이므로 리뷰가 아무리 많아도 40개를 넘지 않는다).
- 요약 대상: 전월(업무 날짜)에 ACTIVE 리뷰가 1개 이상 새로 생긴 상품. 요약 모델은 설정값 `spring.ai.openai.chat.options.model`(예: `gpt-4o-mini` — 착수 시점에 OpenAI가 제공하는 저가 모델인지 확인 필요).
- 요약 잡: 매월 1일 04:00(Asia/Seoul), 상품별 실패는 기록하고 건너뛴다.

### 할 일

**구현 순서**: 절 번호 순서대로 만든다. 1) 마이그레이션 → 2) 설정 → 3) `review.infrastructure`(`ReviewSummaryJob`) → 4) `review.application`(`ReviewSummaryService.summarizeMonth` → `ReviewQueryService.getStats`의 `latestSummary`) → 테스트.

**1) 마이그레이션** — `review.review_summary`: `id uuid pk`, `product_id uuid X`, `period_end date X`, `uk_review_summary_product_period (product_id, period_end)`, `summary text X`, `source_review_count int X`, `model varchar(50) X`, `created_at`

**2) 설정** — `application.yaml`

- `spring.ai.openai.chat.options.model`(위 정책의 요약 모델)을 둔다. `chat.timeout`(60s)·`chat.max-retries`(0)는 SUB-5 설정 표에 있다. 테스트는 `FakeOpenAiServer`의 `/v1/chat/completions`(SUB-5)를 쓴다. `ReviewSummaryService`가 읽는다

**3) review.infrastructure**

| 클래스 | 규격 |
|---|---|
| `ReviewSummaryJob` | `@Scheduled(cron = "0 0 4 1 * *", zone = "Asia/Seoul")` → `jobRunner.run("review-summary", () -> service.summarizeMonth(YearMonth.now(clock.withZone(businessZone)).minusMonths(1)))`(잡 이름은 권장안) |

**4) review.application**

| 클래스 | 메서드 |
|---|---|
| `ReviewSummaryService` | `int summarizeMonth(YearMonth month)` — `@Transactional` 없음 |
| `ReviewQueryService` (5-1 수정) | `getStats` 수정 |

`ReviewSummaryService.summarizeMonth(YearMonth month)` — 한 달 치 대상 상품을 돌며 LLM 요약을 만들어 `review_summary`에 저장한다. 순서는 아래다. `JobRunner.run`이 `Supplier<Integer>`를 받으므로 저장한 요약 건수(`int`)를 반환한다. 의존성: `JdbcTemplate`(또는 review repository), Spring AI `ChatClient`(스타터가 만드는 `ChatClient.Builder`로 `build()`한다. 2.0.1에서 확인 필요), `Clock`, `BusinessProperties`(3-4가 만드는 `common.config`의 `@ConfigurationProperties("myroutine") record`. `ZoneId businessZone()`이 업무 시간대 `Asia/Seoul`을 돌려준다). `ReviewSummaryJob`이 부르고, 스케줄러는 테스트에서 꺼져 있으므로 테스트가 직접 부른다(2-5)

`summarizeMonth` 순서
1. 기간: `from = month.atDay(1)`의 업무 시간대 0시, `to = month.plusMonths(1).atDay(1)`의 0시(포함 미만). 0시는 `atStartOfDay(businessProperties.businessZone()).toInstant()`로 구한다. 대상 상품 ID: `SELECT DISTINCT product_id FROM review.review WHERE status = 'ACTIVE' AND created_at >= :from AND created_at < :to` (keyset: `product_id > :after ORDER BY product_id LIMIT :n`씩 반복. `n`은 구현이 정한다)
2. 상품마다 try/catch(실패하면 상품 ID·사유를 `log.error`로 남기고 **다음 상품으로**):
   1. 이미 `(product_id, period_end)` 요약이 있으면 건너뜀(**LLM 호출 전에** 확인 — 비용). 잡을 두 번 돌려도 호출이 한 번이 되는 장치다. `period_end`에 넣을 날짜는 SUB-6에 정의돼 있지 않다(`month.atEndOfMonth()`, 말일 포함이 자연스럽다. 3-4 정산의 `period_end`도 말일 포함이다. 확인 필요)
   2. 대표 리뷰 선택(위 정책: `ORDER BY like_count DESC LIMIT 30` + `ORDER BY rating, created_at DESC LIMIT 10`): 쿼리 두 번의 결과를 리뷰 ID로 합쳐 중복을 빼면 최대 40개다. 이 쿼리가 그 상품의 **전체 ACTIVE 리뷰**를 대상으로 하는지 대상 월의 리뷰만인지, `source_review_count`가 프롬프트에 넣은 개수(≤ 40)인지 전체 개수인지는 문서에 없다(확인 필요)
   3. 프롬프트: 시스템 "상품 리뷰를 3문장 이내로 장단점 중심 요약" + 리뷰 목록(별점·본문 200자, `substring(0, Math.min(200, length))`. 줄 형식은 구현이 정한다). **트랜잭션 밖에서** `String summary = chatClient.prompt().system(시스템문구).user(리뷰목록).call().content()` (Spring AI의 `ChatClient` 호출 형태. 2.0.1에서 확인 필요). 응답 지연·500은 예외로 올라와 위 try/catch가 받는다(`max-retries`는 0)
   4. `INSERT INTO review.review_summary (id, product_id, period_end, summary, source_review_count, model, created_at) VALUES (...) ON CONFLICT (product_id, period_end) DO NOTHING`. `id`는 `Ids.newId()`, `model`에는 요약에 쓴 설정 `spring.ai.openai.chat.options.model`의 값을 적는다(`model` 값은 문서에 명시돼 있지 않다. 확인 필요). 1번 확인과 이 INSERT 사이에 다른 서버가 먼저 넣어도 `DO NOTHING`이라 안전하다

`ReviewStatsResult`·`ReviewStatsResponse` 수정 (5-1) — 마지막 필드로 `latestSummary`를 더한다. 5-1의 응답에는 없던 필드다.

`ReviewQueryService.getStats` 수정 (5-1) — `review_summary`에서 `product_id`의 `period_end` 최댓값 행 `(periodEnd, summary)`를 읽어 `latestSummary`에 넣는다. 없으면 `null`. 읽는 쿼리를 둘 곳은 문서에 없다(확인 필요: 5-1 통계 읽기 메서드와 같은 repository).

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 이 단계에서 추가하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `insertReview(UUID productId, int rating, int likeCount, String status, String content, Instant createdAt)` → `UUID` | JDBC `INSERT INTO review.review (id, order_line_id, product_id, member_id, rating, content, like_count, status, version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)`. `id`·`order_line_id`·`member_id`는 `Ids.newId()`, `updated_at` = `created_at` | `ReviewSummaryTest` |
| `reviewSummariesOf(UUID productId)` → `List<ReviewSummaryRow>` | JDBC `SELECT period_end, summary, source_review_count, model FROM review.review_summary WHERE product_id = ? ORDER BY period_end`. `ReviewSummaryRow`는 `support/`의 record `(LocalDate periodEnd, String summary, int sourceReviewCount, String model)`이고 행 수는 `.size()`, 없으면 빈 목록 | `ReviewSummaryTest` 1·2·5·7·9번 |
| `insertReviewStat(UUID productId, int reviewCount, long ratingSum, int r1, int r2, int r3, int r4, int r5)` → `void` | JDBC `INSERT INTO review.product_review_stat (product_id, review_count, rating_sum, r1, r2, r3, r4, r5, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())` | `ReviewSummaryTest` 6번 |
| `insertReviewSummary(UUID productId, LocalDate periodEnd, String summary)` → `void` | JDBC `INSERT INTO review.review_summary (id, product_id, period_end, summary, source_review_count, model, created_at) VALUES (?, ?, ?, ?, 1, 'test', now())`. `id`는 `Ids.newId()` | `ReviewSummaryTest` 6번 |
| `FakeOpenAiServer.chatUserPrompts()` → `List<String>` | 받은 `/v1/chat/completions` 요청마다 `messages` 중 `role`이 `user`인 것의 `content` 텍스트를 받은 순서대로 모은다. 호출 건수는 `.size()`, 프롬프트는 `.get(0)`처럼 읽는다(`content`가 문자열인지 배열인지는 서버를 만들 때 실제 요청을 보고 정한다. 확인 필요) | `ReviewSummaryTest` 1~5·8·9번 |

헬퍼를 쓰는 방법은 다음과 같다.
- 방금 호출 하나만 실패시키는 `openAi.failNext("/v1/chat/completions", 1)`이 필요하다(이름은 권장안. 해당 경로에 앞으로 `n`번 오는 요청만 500으로 응답하고 호출마다 남은 횟수를 하나씩 줄이며, `reset()`이 남은 횟수를 비운다).
- 가짜 서버의 고정 요약 문장: 서버를 만들 때 정하는 상수(예: `FakeOpenAiServer.SUMMARY`)와 `review_summary.summary`를 비교한다.

**공통 준비**: 요약 월은 오늘 날짜에 기대지 않도록 `YearMonth.of(2026, 9)`로 고정하고 `service.summarizeMonth(...)`를 직접 부른다. 리뷰는 5-1 흐름을 거치지 않고 JDBC로 직접 넣는다. `review`는 상품을 호출하지 않고 5-1 마이그레이션에 외래 키도 없으므로 `productId`·`order_line_id`·`member_id`는 `Ids.newId()`면 된다.

시각은 업무 시간대(Asia/Seoul) 기준이다: `ZonedDateTime.of(2026, 9, 15, 12, 0, 0, 0, ZoneId.of("Asia/Seoul")).toInstant()` 같은 식으로 만든다. 기간은 `2026-09-01T00:00+09:00` 이상, `2026-10-01T00:00+09:00` 미만이다.

#### `review/application/ReviewSummaryTest` (가짜 OpenAI 서버)

**1. 잡을 두 번 돌려도 요약 1건, LLM 호출 1번** — 준비: 상품 `Pa`에 `ACTIVE` 리뷰 3개: 5점·`2026-09-01T00:00+09:00`, 4점·`2026-09-15T12:00+09:00`, 2점·`2026-09-30T23:59:59+09:00`. 동작: `summarizeMonth(YearMonth.of(2026, 9))`를 두 번 호출.
- 반환값: 첫 호출 1, 둘째 호출 0.
- `reviewSummariesOf(Pa)`: 1개.
- `openAi.chatUserPrompts()` 정확히 1건.

**2. 전월에 새 리뷰가 없는 상품은 호출하지 않는다** — 준비: 상품 세 개.
- `Pb`: `ACTIVE` 리뷰 1개, `2026-08-31T23:59:59+09:00`.
- `Pc`: `ACTIVE` 리뷰 1개, `2026-10-01T00:00:00+09:00`.
- `Pd`: `DELETED` 리뷰 1개, `2026-09-15T12:00+09:00`.
- 동작: `summarizeMonth(YearMonth.of(2026, 9))`. 확인: 반환값 0, `chatUserPrompts()` 0건.

**3. 리뷰 100개인 상품도 프롬프트는 40개 이하** — 준비: 상품 `Pe`에 `ACTIVE` 리뷰 100개. `i` = 1~100:
- `content` = `"리뷰본문-" + 3자리 0패딩 i`(예: `리뷰본문-007`). 단 `i` = 100은 `"리뷰본문-100 " + "힣"×500`.
- `rating` = `i ≤ 10`이면 1, 아니면 5.
- `like_count` = `i ≥ 71`이면 `i`, 아니면 0.
- `created_at` = `2026-09-05T00:00+09:00`에 `i`분을 더한 값.
- 동작: `summarizeMonth(YearMonth.of(2026, 9))`.
- `chatUserPrompts()` 1건. 그 프롬프트에서 정규식 `리뷰본문-(\d{3})`로 찾은 매치를 리스트로 모으면 크기가 정확히 40이고, 그 집합은 `{001~010, 071~100}`이다.

**4. 좋아요 상위와 별점 하위가 겹치면 한 번만 넣는다** (추가) — 준비: 상품 `Pf`에 `ACTIVE` 리뷰 12개, `i` = 1~12, `content` = `"리뷰본문-0" + 두 자리 i`(예: `리뷰본문-007`), `rating` = `i ≤ 5`이면 1, 아니면 5, `like_count` = `i`, `created_at` = 9월 안의 서로 다른 시각. 확인: `chatUserPrompts()` 1건, 그 프롬프트에서 같은 정규식 매치 리스트 크기가 정확히 12, 집합 `{001~012}`.

**5. 한 상품이 실패해도 다른 상품은 요약된다** — 준비: 상품 `Pg`, `Ph`에 각각 9월 `ACTIVE` 리뷰 2개. `failed` = 두 ID 중 `UUID.toString()` 기준 작은 쪽, `ok` = 큰 쪽. `openAi.failNext("/v1/chat/completions", 1)`.
- 동작: `summarizeMonth(YearMonth.of(2026, 9))`. 확인: 예외가 밖으로 나오지 않는다. 반환값 1. `reviewSummariesOf(ok)` 1개, `reviewSummariesOf(failed)` 빈 목록. `chatUserPrompts()` 2건.

**6. 최신 요약이 리뷰 통계 응답에 들어간다** (추가) — 준비: 상품 `Pa`(1번 케이스처럼 9월 요약 완료) + `insertReviewStat(Pa, 3, 11, 0, 1, 0, 1, 1)` + `insertReviewSummary(Pa, LocalDate.of(2026, 8, 31), "8월 요약")`. 동작: `GET /api/products/{Pa}/review-stats`(Authorization 없이).
- 응답: 200, `$.latestSummary`의 요약 문장 = 가짜 서버의 고정 문장. `latestSummary`의 JSON 키 이름은 문서에 없다(확인 필요, 후보 `$.latestSummary.summary`·`$.latestSummary.periodEnd`).
- 요약이 하나도 없는 상품: `$.latestSummary`는 `value(nullValue())`.

**7. 전월 리뷰로 요약 한 건이 저장된다** — 준비: 1번과 같은 준비. 동작: `summarizeMonth(YearMonth.of(2026, 9))`를 한 번 호출.
- `reviewSummariesOf(Pa)`: `sourceReviewCount()` = 3, `summary()` = 가짜 서버의 고정 문장, `periodEnd()` = `2026-09-30`(말일 포함이 후보. 문서에 없다. 확인 필요), `model()` = 설정 `spring.ai.openai.chat.options.model` 값(문서에 없다. 확인 필요).

**8. 리뷰 본문은 200자까지만 프롬프트에 넣는다** — 준비: 3번과 같은 준비. 동작: `summarizeMonth(YearMonth.of(2026, 9))`.
- 확인: `chatUserPrompts()`의 프롬프트에서 `힣` 글자가 정확히 191개(100번 리뷰의 본문은 200자까지만: 앞의 `"리뷰본문-100 "` 9자 + 191자).

**9. 실패한 상품은 다시 돌리면 요약된다** — 준비: 5번의 준비와 동작(첫 호출까지). 동작: `summarizeMonth(YearMonth.of(2026, 9))`를 한 번 더 호출.
- 확인: 반환값 1. `reviewSummariesOf(failed)` 1개, `reviewSummariesOf(ok)`는 그대로 1개. `chatUserPrompts()`는 총 3건.

**추가 항목**
- 리뷰 임베딩(`review-created` 구독 → 리뷰 벡터 저장)으로 대표 리뷰 고르기: 리뷰 벡터의 평균(중심)에 가까운 리뷰를 고르면 "가장 흔한 의견"을 대표로 넣을 수 있다

**리뷰 때 물어볼 것**: 리뷰가 너무 많으면 무엇을 프롬프트에 넣나? 왜 그렇게 골랐나?
