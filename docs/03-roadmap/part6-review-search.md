# Part 6. 리뷰 · 검색 · 추천

> 버전 0.26 · 2026-10-27
> 0.10 · 2026-10-16 · 쓰지 않는 요소를 걷어냈다. 6-1 `ConfirmedLineInfo`의 `shopId`·`confirmedAt`을 뺐다. 반환값을 아무도 읽지 않는 `ReviewStatRepository.add`·`remove`·`change`, `ReviewRepository.increaseLikeCount`·`decreaseLikeCount`의 "반영 행 수"를 "없음"으로 바꿨다. 6-2 ES 문서의 `updatedAt` 필드를 매핑과 `upsert` 본문에서 뺐다. 6-3 `ProductIndexSnapshot`을 없애고 `findForIndexing`이 `List<ProductUpsertedEvent>`를 돌려주게 했다. 6-4 `recommendation/api/package-info.java`를 뺐다
> 0.5 · 2026-10-09 · ES 이미지 `9.4.8`(Spring Data Elasticsearch: Boot BOM 6.1.1, 클라이언트 9.4.5), Spring AI `2.0.1`(Boot 4.1.1 스타터에 의존)과 OpenAI 설정 키를 확정했다. Spring AI 2.0의 OpenAI 클라이언트가 자체 재시도(기본 3회)를 하므로 테스트의 호출 횟수에 반영한다. pgvector `avg` 집계를 확인했다. 1-10 규칙에 따라 추천 응답의 `thumbnailKey`를 `thumbnailUrl`로 바꿨다
> 0.4 · 2026-10-02 · 6-3 무중단 재색인을 선택 단계로 하고, 리뷰 임베딩을 제거했다(요약은 단순 선택). 제재 회원 문의 허용은 제안으로, 리뷰 중복은 사전 조회로 했다

> **끝나면**: 구매확정한 상품에 리뷰를 쓰고, 키워드로 상품을 검색하고, 취향 기반 추천과 월간 리뷰 요약을 본다.
> **인프라 추가**: **Elasticsearch**, pgvector 확장(이미 쓰는 Postgres 이미지에 포함), OpenAI(Spring AI)
> 검색·추천 모듈은 **이벤트로 데이터를 받고**, 동기 호출은 상품 조회(`ProductApi`)만 한다. Stage 3에서 가장 먼저 분리될 대상이다.
> 시간이 부족하면 6-1(리뷰), 6-2(검색)만 하고 나머지는 생략할 수 있다. **6-3은 선택 단계**다.
> **릴리스**: `v0.6.0`

| 단계 | 제목 | 크기 |
|---|---|---|
| 6-1 | 리뷰 · 평점 통계 · 좋아요 | M |
| 6-2 | 상품 검색 · 자동완성 (Elasticsearch) | M |
| 6-3 | (선택) 무중단 재색인 | S |
| 6-4 | 취향 추천 (pgvector) | M |
| 6-5 | 월간 리뷰 요약 | S |
| 6-6 | 1:1 문의 | S |

---

## Part 6 공통 규칙 (Part 1~5 공통 규칙에 더해서)

### X. 외부 AI·검색 호출
- OpenAI·Elasticsearch 호출은 **트랜잭션 밖**에서 한다(공통 규칙 K). consumer에서 외부 호출이 필요하면 순서를 "중복 확인 → 외부 호출 → `InboxGuard.runOnce`로 저장"으로 한다(`runOnce`는 트랜잭션이라 그 안에서 부르면 안 된다).
- 타임아웃: ES 2초, OpenAI 임베딩 10초·요약 60초. 테스트는 가짜 서버(JDK `HttpServer`).
- 호환 버전(2026-10-09 Maven Central 확인, 개발 가이드 §1.1 표에도 적었다): Spring Data Elasticsearch **6.1.1**(Boot 4.1.1 BOM이 관리, ES Java 클라이언트 9.4.5) / Spring AI **2.0.1**(`spring-ai-starter-model-openai` 2.0.1이 Boot 4.1.1 스타터에 의존). 착수할 때 더 새 패치가 있으면 그것으로.
- **consumer 모양** (6-2·6-4의 consumer 설명은 이 모양을 전제로 한다): `@KafkaListener(topics = XxxTopics.YYY, groupId = "{모듈}") void onYyy(String message)` → `eventEnvelopes.parse(message, Payload.class)`가 `TypedEnvelope`(`envelope()` = `eventId`·`traceId`가 든 봉투, `payload()` = 이벤트 record)를 돌려준다 → `inboxGuard.runOnce("{모듈}.{이벤트}", typed.envelope(), () -> 처리)`. `runOnce`는 `processed_message` INSERT와 처리를 한 트랜잭션에 묶어서, 처리가 예외면 INSERT도 롤백되고 다시 소비해도 처리된다. `runOnce`를 쓰지 않는 consumer(6-2)는 `EventContext.run(envelope, () -> ...)`로 MDC만 설정한다. 예외는 던지면 3-2 정책(3번 재시도 → DLT)이고, `BusinessException`·`IllegalArgumentException`·JSON 오류는 **재시도 없이 바로 DLT**다. 그래서 ES·OpenAI 호출 실패는 `BusinessException`이 아니라 일반 예외(`RuntimeException`)로 던져야 재시도된다.
- Redis는 `StringRedisTemplate`만 쓰고 모든 키에 TTL을 둔다([Part 4 공통 규칙 T](part4-auth.md)). JSON 값은 주입받은 `ObjectMapper`(`tools.jackson.databind`)로 문자열 ↔ record를 바꾼다.

### Y. 모듈 의존 (Part 6 추가분)

| 모듈 | `allowedDependencies` |
|---|---|
| review | `common`, `order::api` |
| search | `common`, `product::api` |
| recommendation | `common`, `product::api`, `order::api`, `member::api` |

### Z. Part 6 에러 코드

| 코드 | HTTP | 메시지 | 정의 위치 | 단계 |
|---|---|---|---|---|
| `REVIEW_NOT_ALLOWED` | 422 | 구매확정한 상품만 리뷰를 쓸 수 있습니다. | `OrderErrorCode` | 6-1 |
| `REVIEW_ALREADY_EXISTS` | 409 | 이미 리뷰를 작성했습니다. | `ReviewErrorCode` | 6-1 |
| `REVIEW_NOT_FOUND` | 404 | 리뷰를 찾을 수 없습니다. | `ReviewErrorCode` | 6-1 |
| `REVIEW_EDIT_EXPIRED` | 409 | 작성 후 30일이 지나 수정할 수 없습니다. | `ReviewErrorCode` | 6-1 |
| `SEARCH_UNAVAILABLE` | 503 | 검색을 일시적으로 사용할 수 없습니다. | `SearchErrorCode` | 6-2 |
| `INQUIRY_NOT_FOUND` | 404 | 문의를 찾을 수 없습니다. | `MemberErrorCode` | 6-6 |

---

## 6-1. 리뷰 · 평점 통계 · 좋아요 (M)

**목표**: 구매확정된 품목당 리뷰 1개. 상품별 평점 분포와 좋아요.

**정책 (이 단계에서 정함)**
- 별점 1~5, 본문 10~1,000자. 품목당 1개(삭제해도 같은 품목에 다시 쓸 수 없다, POL-14).
- 작성 후 30일 안에만 수정 가능. 삭제는 soft delete(DELETED)이고 통계에서 빠진다.
- 상품 상세 API는 평점을 포함하지 않는다. 평점은 `GET /api/products/{id}/review-stats`로 따로 조회한다(product가 review를 참조하지 않게).
- 리뷰 목록 정렬: 최신순(커서) / 좋아요순(상위 50개, 커서 없음 — 좋아요 수는 계속 바뀌어 keyset 기준으로 쓸 수 없다).

### 할 일

**구현 순서**: 절 번호 순서대로 만든다. 1) 마이그레이션 → 2~4) 작성 자격 확인(`order.domain` → `order.api` → `order.application`) → 5) `review.domain` → 6) `review.api` → 7) `review.infrastructure` → 8) `review.application` → 9) `review.web` → 10) `common.security` → 테스트 준비 → 테스트.

**1) 마이그레이션** — `db/migration/review/V{...}__review_create_review.sql` (`CREATE SCHEMA IF NOT EXISTS review;`)

`review.review`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_review` |
| order_line_id | uuid | X | `uk_review_order_line` |
| product_id, member_id | uuid | X | |
| rating | int | X | `BETWEEN 1 AND 5` |
| content | text | X | |
| like_count | int | X | DEFAULT 0, `>= 0` |
| status | varchar(20) | X | `IN ('ACTIVE','DELETED')` |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- 인덱스 `idx_review_product_id_created_at (product_id, created_at DESC, id DESC) WHERE status = 'ACTIVE'`, `idx_review_product_id_like_count (product_id, like_count DESC) WHERE status = 'ACTIVE'`
- `review.review_like`: `review_id uuid`, `member_id uuid`, `created_at`, `pk_review_like (review_id, member_id)`
- `review.product_review_stat`: `product_id uuid pk_product_review_stat`, `review_count int`, `rating_sum bigint`, `r1`~`r5 int` (모두 DEFAULT 0, `>= 0`), `updated_at`

**2) order.domain**

review는 주문을 모른다(허용 의존이 `order::api`뿐). "이 회원이 이 품목을 구매확정했는가"는 order만 아는 사실이라, review는 이벤트로 사본을 만들지 않고 작성할 때 동기로 묻는다([이벤트 카탈로그](../02-design/05-events.md)).

| 클래스 | 규격 |
|---|---|
| `OrderErrorCode` | 기존 enum에 `REVIEW_NOT_ALLOWED`를 추가한다(값·메시지는 위 Z 표). `ORDER_LINE_NOT_FOUND`는 2-8에서 만들었다. `getConfirmedLine`이 던진다 |
| `OrderRepository` | 품목 조회 쿼리를 추가한다. `orderLineId`와 `memberId`로 품목을 찾는다. 품목 행에는 회원이 없고 `order_line → shop_order → orders.member_id`로 이어지므로 그 경로로 소유를 확인하며 품목을 읽는다. 이 조회 쿼리는 Part 2에 정의돼 있지 않다(확인 필요: `OrderRepository`에 더할 쿼리 모양과 반환 타입). `getConfirmedLine`이 가장 먼저 부른다 |

**3) order.api**

| 클래스 | 규격 |
|---|---|
| `ConfirmedLineInfo` | `record (UUID orderLineId, UUID productId)`. `getConfirmedLine`이 반환하고 `ReviewService.write`가 쓴다 |
| `OrderApi` | `interface { ConfirmedLineInfo getConfirmedLine(UUID memberId, UUID orderLineId); }` — 던지는 예외는 4) `getConfirmedLine` 순서. `order/api` 패키지는 3-3에서 처음 공개된다. `ReviewService.write`의 첫 단계에서 부른다 |

**4) order.application**

| 클래스 | 메서드 |
|---|---|
| `OrderApiImpl` | package-private, `OrderApi` 구현. `@Transactional(readOnly = true) ConfirmedLineInfo getConfirmedLine(UUID memberId, UUID orderLineId)` |

`getConfirmedLine` 순서
1. `OrderRepository`의 품목 조회 쿼리로 품목을 찾는다 — 없거나 남의 품목이면 404 `ORDER_LINE_NOT_FOUND`(소유권, 규칙 D)
2. 품목 `status`가 `CONFIRMED`가 아니면 422 `REVIEW_NOT_ALLOWED`
3. `new ConfirmedLineInfo(line.getId(), line.getProductId())` 반환

**5) review.domain**

- review 모듈의 클래스 이름은 권장안이다.
- 통계(`product_review_stat`)와 `like_count`는 **읽고 더해서 저장하지 않고 SQL 한 문장으로 증감**한다. 동시 요청이 같은 행을 읽고 각자 +1을 저장하면 하나가 사라지기 때문이다(`ReviewConcurrencyTest`가 이것을 확인한다).

| 클래스 | 규격 |
|---|---|
| `ReviewStatus` | `ACTIVE`, `DELETED` |
| `Review` | `@Entity @Table(name = "review", schema = "review")`, `extends BaseTimeEntity`, 필드는 위 `review.review` 컬럼과 1:1(`@Version Long version` 포함). 메서드(`write`·`edit`·`delete`)는 아래. `ReviewService`가 쓴다 |
| `ReviewErrorCode` | `ErrorCode`를 구현하는 enum. `REVIEW_ALREADY_EXISTS`(409), `REVIEW_NOT_FOUND`(404), `REVIEW_EDIT_EXPIRED`(409) — 값·메시지는 위 Z 표. `ReviewService`와 `Review.edit`이 쓴다 |
| `ReviewRepository` | 메서드는 아래 |
| `ReviewLikeRepository` | native 쿼리만 쓴다. 메서드는 아래 |
| `ReviewStatRepository` | native 쿼리만 쓴다. 메서드는 아래 |

- `review_like`·`product_review_stat` 매핑은 문서에 정해져 있지 않다(확인 필요): 엔티티를 만들어 Spring Data 리포지토리로 둘지 `JdbcTemplate`으로 쓸지 정해야 한다

`Review` 메서드
- `static Review write(ConfirmedLineInfo line, UUID memberId, int rating, String content)` — 새 리뷰를 만든다. `id = Ids.newId()`, `ACTIVE`, `likeCount 0`. 별점·본문 불변식 위반은 `IllegalArgumentException`(요청 DTO가 먼저 막지만 도메인도 지킨다, 규칙 B)
- `int edit(int rating, String content, Instant now)` — 별점·본문을 바꾼다. `createdAt + 30일`이 지났으면 `REVIEW_EDIT_EXPIRED`. **바꾸기 전 별점**을 반환한다(서비스가 통계의 옛 칸을 줄이는 데 쓴다)
- `void delete()` — `status`를 `DELETED`로(soft delete)

`ReviewRepository` 메서드
- `void increaseLikeCount(UUID id)` / `void decreaseLikeCount(UUID id)` (`@Modifying`) — `like_count`를 SQL로 ±1. `decrease`는 `WHERE like_count > 0`이라 0 아래로 내려가지 않는다(`>= 0` 제약)
- `boolean existsByOrderLineId(UUID)` — 같은 품목의 리뷰가 있는지. **삭제된 리뷰도 센다**(POL-14)
- `Optional<Review> findByIdAndMemberId(UUID id, UUID memberId)` — 작성자 본인의 리뷰만 찾는다. 남의 것은 없는 것과 같다(404)
- 목록 쿼리 → `List<Review>` — `product_id = :productId AND status = ACTIVE`. 최신순은 첫 페이지용과 다음 페이지용 쿼리 두 개로 `ProductRepository.findPublicFirstPage`·`findPublicNextPage`와 같은 모양이다(메서드 이름은 문서에 없다): `ORDER BY created_at DESC, id DESC`, 다음 페이지는 `created_at < :cursorCreatedAt OR (created_at = :cursorCreatedAt AND id < :cursorId)`를 더하고, 둘 다 `Limit.of(size + 1)`로 부른다. 좋아요순은 `like_count DESC` 상위 50

`ReviewLikeRepository` 메서드 (native)
- `int insertIgnoringConflict(UUID reviewId, UUID memberId, Instant now)` — `INSERT ... ON CONFLICT DO NOTHING`. 이미 누른 회원이면 아무것도 하지 않는다. 1(새로 눌림) / 0(이미 있음)을 반환한다
- `int deleteByReviewIdAndMemberId(UUID reviewId, UUID memberId)` — 좋아요 행 삭제. 1 / 0(누른 적 없음)을 반환한다

`ReviewStatRepository` 메서드
- `void add(UUID productId, int rating, Instant now)` (native) — 통계에 리뷰 1개를 더한다. 행이 없으면 만든다(아래 SQL)
- `void remove(UUID productId, int rating, Instant now)` — 통계에서 리뷰 1개를 뺀다. `add`의 반대 방향 `UPDATE`(`review_count - 1`, `rating_sum - :rating`, 그 별점 칸 -1). 행이 없어도 만들지 않는다
- `void change(UUID productId, int oldRating, int newRating, Instant now)` — 별점 수정을 반영한다. `review_count`는 그대로, `rating_sum`은 `new - old`만큼, 옛 칸 -1·새 칸 +1
- 읽기 메서드: `getStats`가 `product_id`로 통계 행(`review_count`, `rating_sum`, `r1`~`r5`)을 읽는다(문서에 메서드 이름이 없다. 같은 repository에 추가한다). `productId` → 행(없을 수 있음)

통계 `add` (평점별 칸을 한 문장에서 올린다)
```sql
INSERT INTO review.product_review_stat (product_id, review_count, rating_sum, r1, r2, r3, r4, r5, updated_at)
VALUES (:productId, 1, :rating, CASE WHEN :rating = 1 THEN 1 ELSE 0 END, ..., :now)
ON CONFLICT (product_id) DO UPDATE SET
  review_count = review.product_review_stat.review_count + 1,
  rating_sum = review.product_review_stat.rating_sum + :rating,
  r1 = review.product_review_stat.r1 + CASE WHEN :rating = 1 THEN 1 ELSE 0 END,
  ... r5까지 ..., updated_at = :now
```

**6) review.api — 이벤트 계약과 모듈 경계**

| 클래스 | 규격 |
|---|---|
| `ReviewTopics`·`ReviewCreatedEvent` | `review.api.event` 패키지. `ReviewTopics.REVIEW_CREATED = "review.review-created.v1"`, `ReviewCreatedEvent(UUID reviewId, UUID productId, String content)`. `ReviewService.write`의 5번(아래 `write` 순서)에서 쓴다 |
| `review/package-info.java`, `review/api/package-info.java` | 모듈 경계 검증. `@ApplicationModule(allowedDependencies = {"common", "order::api"})`(규칙 Y). `review.api`의 `package-info.java`는 `order/api/package-info.java`처럼 `@NamedInterface("api") package com.myroutine.review.api;` 한 줄이다 |

**7) review.infrastructure**

| 클래스 | 규격 |
|---|---|
| `ReviewTopicConfig` | 기동 시 토픽을 만든다. `@Configuration` 클래스에 `@Bean NewTopic`을 둔다: `TopicBuilder.name(ReviewTopics.REVIEW_CREATED).partitions(3).replicas(1).build()`(`org.springframework.kafka.config.TopicBuilder`. `ShopTopicConfig`·`OrderTopicConfig`와 같은 방식). 구독자가 아직 없어 `REVIEW_CREATED + ".dlt"` 토픽(같은 설정)이 필요한지는 확인 필요 |

**8) review.application**

`Command`·`Result` record 이름은 규칙 D·E를 따른 권장안이다. 서비스는 `XxxCommand`를 받아 `XxxResult` record를 반환하고 컨트롤러가 `XxxResponse.from(result)`로 바꾼다(Part 1 규칙 D·E).

| 클래스 | 규격 |
|---|---|
| `WriteReviewCommand` | `record (UUID orderLineId, int rating, String content)`. `ReviewService.write`의 입력 |
| `EditReviewCommand` | `record (int rating, String content)`(필드를 선택으로 볼지는 아래 확인 필요). `ReviewService.edit`의 입력 |
| `ReviewResult` | 응답 `ReviewResponse`(review.web 절의 API 표)와 같은 필드. `ReviewService.edit`·`ReviewQueryService.getReviews`의 출력 |
| `ReviewStatsResult` | 응답 `ReviewStatsResponse`(review.web 절의 API 표)와 같은 필드. `ReviewQueryService.getStats`의 출력 |

| 클래스 | 메서드 |
|---|---|
| `ReviewService` | `@Transactional UUID write(UUID memberId, WriteReviewCommand command)` / `@Transactional ReviewResult edit(UUID memberId, UUID reviewId, EditReviewCommand command)` / `@Transactional void delete(UUID memberId, UUID reviewId)` / `@Transactional void like(UUID memberId, UUID reviewId)` / `@Transactional void unlike(UUID memberId, UUID reviewId)` |
| `ReviewQueryService` | `@Transactional(readOnly = true) CursorPage<ReviewResult> getReviews(UUID productId, String sort, String cursor, int size)` / `@Transactional(readOnly = true) ReviewStatsResult getStats(UUID productId)` |

`write` 순서 — 리뷰를 작성하고 새 `reviewId`(`UUID`)를 반환한다. 전부 한 트랜잭션이다
1. `ConfirmedLineInfo line = orderApi.getConfirmedLine(memberId, command.orderLineId())` — 404·422는 그대로 전파된다
2. `reviewRepository.existsByOrderLineId(line.orderLineId())`면 409 `REVIEW_ALREADY_EXISTS`
3. `Review.write(line, memberId, rating, content)` → `reviewRepository.save`
4. `reviewStatRepository.add(line.productId(), rating, now)` — 리뷰와 통계가 같은 트랜잭션이라 하나만 반영되는 일이 없다
5. `outboxPublisher.publish(ReviewTopics.REVIEW_CREATED, "REVIEW", review.getId(), new ReviewCreatedEvent(review.getId(), line.productId(), content))` — `OutboxPublisher.publish(String topic, String aggregateType, UUID aggregateId, Object event)`는 호출한 트랜잭션 안에서만 쓸 수 있어(없으면 예외) 상태 변경과 같은 트랜잭션에서 발행된다. `aggregateType`은 다른 이벤트처럼 대상 종류를 대문자로 쓴 값(`"SHOP"`, `"PRODUCT"`, `"ORDER"`)을 따라 `"REVIEW"`로 적었다(확인 필요)
6. `review.getId()` 반환

- 같은 품목 동시 작성은 둘 다 2번을 통과하고 커밋 시 `uk_review_order_line` 위반 → 409 `DUPLICATE_RESOURCE`다. 트랜잭션 안에서 잡지 않는다(As-Is SUP-05)

`edit` 순서 — 작성자가 30일 안에 별점·본문을 고친다. 한 트랜잭션이다
1. `findByIdAndMemberId(reviewId, memberId)` — 없으면 404 `REVIEW_NOT_FOUND`
2. `int oldRating = review.edit(rating, content, now)` — 30일이 지났으면 409 `REVIEW_EDIT_EXPIRED`
3. `reviewStatRepository.change(review.getProductId(), oldRating, rating, now)`
4. 바뀐 리뷰를 `ReviewResult`로 반환(저장은 커밋 때 더티 체킹)

`delete` 순서 — 작성자가 삭제한다(soft delete). 한 트랜잭션이다: `findByIdAndMemberId`(없으면 404) → `review.delete()` → `reviewStatRepository.remove(review.getProductId(), review.getRating(), now)`

- 이미 `DELETED`인 리뷰를 다시 삭제·수정하면 어떻게 되는지, 목록에 보이지 않는 리뷰에 좋아요를 누르면 어떻게 되는지는 문서에 정해져 있지 않다(확인 필요). 정하지 않으면 `remove`가 통계를 두 번 빼서 `>= 0` 제약에 걸리거나 틀린 값이 남는다
- `EditReviewRequest`의 필드를 선택으로 볼지도 정해져 있지 않다(규칙 E는 PATCH의 `null`을 "바꾸지 않음"으로 보지만 `Review.edit`은 `int rating`을 받는다. 확인 필요)

`like`·`unlike` — 좋아요 / 취소. 이미 눌렀거나 누른 적 없어도 에러 없이 끝난다(멱등)
- `like` 순서 (한 트랜잭션): `insertIgnoringConflict`가 1이면 `increaseLikeCount`, 0이면 이미 눌렀으니 아무것도 하지 않는다
- `unlike` 순서 (한 트랜잭션): `deleteByReviewIdAndMemberId`가 1이면 `decreaseLikeCount`, 0이면 아무것도 하지 않는다

`getReviews` — 상품의 ACTIVE 리뷰 목록. 최신순은 커서 페이징이다(`ProductQueryService.getPublicProducts`와 같다)
1. `cursor`가 `null`이거나 빈 문자열이면 첫 페이지 쿼리, 아니면 `Cursor.decode(cursor)`로 `(createdAt, id)`를 복원해 다음 페이지 쿼리
2. `Limit.of(size + 1)`로 읽는다
3. 받은 수가 `size`보다 많으면 `size`개만 남기고 `size`번째 리뷰의 `new Cursor(createdAt, id).encode()`를 `nextCursor`로, 아니면 `null`
4. `new CursorPage<>(items, nextCursor)`

좋아요순은 상위 50개이고 `nextCursor = null`이다. `getReviews`의 `sort`: `latest`(최신순) / `likes`(좋아요순). 기본값·허용 밖의 값의 처리와 좋아요순에서 `size`·동점 정렬이 어떻게 적용되는지는 문서에 없다(확인 필요)

`getStats` 순서 — 평점 통계와 최신 월간 요약을 돌려준다
1. 통계 행을 `productId`로 읽는다. `average`는 `rating_sum / review_count`를 소수 1자리로 만든다(반올림 방식과, 행이 없거나 `review_count = 0`일 때의 응답은 문서에 정해져 있지 않다. 확인 필요)
2. `distribution`은 `{1: r1, 2: r2, 3: r3, 4: r4, 5: r5}`
3. `latestSummary`: 6-5 전에는 `null`이다. 채우는 일은 6-5가 한다
- review는 product를 호출하지 않으므로(허용 의존) 상품이 실제로 있는지는 확인하지 않는다

**9) review.web**

| API | 권한 | 요청 | 응답 |
|---|---|---|---|
| `POST /api/reviews` | 로그인 | `WriteReviewRequest(@NotNull orderLineId, @Min(1) @Max(5) rating, @Size(min=10, max=1000) content)` | 201 `{reviewId}` |
| `PATCH /api/reviews/{id}` | 작성자 | `EditReviewRequest(rating, content)` | 200 `ReviewResponse` |
| `DELETE /api/reviews/{id}` | 작성자 | | 204 |
| `POST /api/reviews/{id}/likes`, `DELETE ...` | 로그인 | | 204 |
| `GET /api/products/{id}/reviews?sort=latest\|likes&cursor=&size=` | 공개 | | 200 `CursorPage<ReviewResponse(id, rating, content, likeCount, createdAt)>` (작성자 정보는 내려주지 않는다 — review가 member를 참조하지 않게) |
| `GET /api/products/{id}/review-stats` | 공개 | | 200 `ReviewStatsResponse(reviewCount, average(소수 1자리), distribution{1..5}, latestSummary)` |

- 컨트롤러는 review 모듈의 `review.web`에 둔다(클래스 이름은 문서에 없다. 권장 `ReviewController`). 경로가 `/api/products/{id}/...`인 두 GET도 product 모듈이 아니라 여기서 처리한다(위 정책). 요청자 ID는 `@CurrentMember UUID memberId`

**10) common.security — SecurityConfig**

- 리뷰 목록·통계 GET 공개. 지금 `GET /api/products/*`만 열려 있어 `/api/products/{id}/reviews`·`/api/products/{id}/review-stats`는 막힌다. `.requestMatchers(HttpMethod.GET, "/api/products/*/reviews", "/api/products/*/review-stats").permitAll()`을 더한다

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 이 단계에서 추가하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다. 쿼리와 `Map` 변환은 테스트에 쓰지 않고 헬퍼 한 번 호출로 읽는다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `createConfirmedLine(UUID buyerId, UUID sellerId, UUID shopId, UUID productId)` → `UUID` | 기존 헬퍼 `UUID createDeliveredOrder(UUID buyerId, UUID sellerId, UUID shopId, UUID... productIds)`(결제 완료 주문을 만들고 판매자 발송·배송완료까지 처리해 `orderId`를 돌려준다) → 기존 헬퍼 `UUID orderLineIdOf(UUID orderId, UUID productId)`(JDBC로 그 주문의 품목 ID를 읽는다) → `confirmPurchaseService.confirm(UUID memberId, UUID orderId, UUID lineId)`(구매확정 서비스. 반환값 `OrderLineResult`는 쓰지 않는다)를 차례로 부르고 품목 ID를 돌려준다. `TestFixtures`가 `ConfirmPurchaseService`를 주입받는다 | `ReviewControllerTest`, `ReviewConcurrencyTest` |
| `reviewSnapshotOf(UUID reviewId)` → `ReviewSnapshot` | JDBC 쿼리 2개로 한 번에 읽는다: `review.review`의 `order_line_id`·`product_id`·`rating`·`content`·`like_count`·`status`, `review.review_like`의 `member_id`(해당 `review_id`, 생성 순). `ReviewSnapshot`은 `support/`의 record `(UUID orderLineId, UUID productId, String status, int rating, String content, int likeCount, List<UUID> likerIds)`. 리뷰 행이 없으면 `null`. 좋아요 행 수는 `likerIds().size()`로 센다 | `ReviewControllerTest`, `ReviewConcurrencyTest` |
| `reviewStatOf(UUID productId)` → `ReviewStatSnapshot` | JDBC `SELECT review_count, rating_sum, r1, r2, r3, r4, r5 FROM review.product_review_stat WHERE product_id = ?`. `ReviewStatSnapshot`은 `support/`의 record `(int reviewCount, long ratingSum, int r1, int r2, int r3, int r4, int r5)`이고 `rating(int n)`이 `r1`~`r5` 중 n번째를 돌려준다. 행이 없으면 `null`(0행 확인은 `isNull()`) | `ReviewControllerTest`, `ReviewConcurrencyTest` |
| `reviewCountOf(UUID productId)` → `long` | JDBC `SELECT count(*) FROM review.review WHERE product_id = ?`(`ACTIVE`·`DELETED` 모두 센다) | `ReviewControllerTest`, `ReviewConcurrencyTest` |
| `reviewCreatedEvents()` → `List<ReviewCreatedEvent>` | `OutboxAssert.rows("review.review-created.v1")`(topic의 outbox 행을 생성 순 `List<OutboxSnapshot>`으로 읽는다)의 각 행 `payload()`(`OutboxSnapshot`의 `JsonNode`)를 `objectMapper.treeToValue(…, ReviewCreatedEvent.class)`로 풀어 생성 순으로 돌려준다. 건수는 `.size()`(확인 필요: Jackson 3 `treeToValue`의 시그니처) | `ReviewControllerTest`, `ReviewConcurrencyTest` |
| `setReviewCreatedAt(UUID reviewId, Instant instant)` → `void` | JDBC `UPDATE review.review SET created_at = ? WHERE id = ?`(`Timestamp.from(instant)`) | `ReviewControllerTest` 8·11·16번 |

이미 있는 헬퍼: `signup`, `login`, `token`, `openShop`, `registerProduct`, `createDeliveredOrder`, `orderLineIdOf`, `ConcurrencyRunner`, `OutboxAssert`.

헬퍼를 쓰는 방법은 다음과 같다.
- 응답: `jsonPath`. 에러 응답의 코드는 `$.code`. 리뷰 목록은 `$.items[i].id`·`.rating`·`.content`·`.likeCount`·`.createdAt`과 `$.nextCursor`(마지막 페이지는 `null`). 통계는 `$.reviewCount`, `$.average`, `$.distribution['1']`~`['5']`, `$.latestSummary`.

**공통 준비**: 판매자 1명(`seller@test.com`), 가게 1개, 상품 `productA`(`registerProduct(sellerId, shopId, 10000, 100)`, 재고 100). 구매자는 필요한 만큼 `buyer1@test.com`, `buyer2@test.com`, …로 `signup` + `login`해 토큰을 받고, 각자 `createConfirmedLine(buyerId, sellerId, shopId, productA)`로 구매확정된 품목 하나씩을 가진다(`lineOf(buyerN)`이라 부른다). 작성 요청은 `POST /api/reviews`에 `Authorization: Bearer {구매자 토큰}`, 본문 `{"orderLineId":"{lineId}","rating":5,"content":"효과가 좋아요 정말 만족합니다"}`(본문 16자)다. 응답 `$.reviewId`로 리뷰 ID를 받는다.

**공통 준비 B (리뷰 3개)**: `buyer1`이 5점, `buyer2`가 4점, `buyer3`이 4점으로 `productA`에 순서대로 작성한다. 이 리뷰를 `R1`(5점), `R2`(4점), `R3`(4점)이라 부른다. 이때 통계는 `review_count` 3, `rating_sum` 13, `r4` 2, `r5` 1, 나머지 0이다.

#### `review/domain/ReviewTest`

공통 준비: `Review.write(new ConfirmedLineInfo(lineId, productId), memberId, 4, "열 글자가 넘는 리뷰 본문입니다")`(세 ID는 `Ids.newId()`). 영속 전이라 `createdAt`이 `null`이므로 `ReflectionTestUtils.setField(review, "createdAt", T0)`로 채운다. `T0` = `Instant.parse("2026-09-01T00:00:00Z")`.

| 케이스 | 동작 | 확인할 것 |
|---|---|---|
| 작성 | 공통 준비 직후 | `status` = `ACTIVE`, `likeCount` = 0, `rating` = 4, `content` = 넘긴 본문, `orderLineId`·`productId`·`memberId` = 넘긴 값, `id` ≠ null |
| 불변식 위반 | `Review.write`를 `rating` 0 / `rating` 6 / 본문 `"가"×9` / 본문 `"가"×1001`로 각각 호출 | 네 번 모두 `IllegalArgumentException`. 경계인 `rating` 1·5, 본문 `"가"×10`·`"가"×1000`은 예외 없이 만들어진다 |
| 30일 안 수정 | `edit(5, "수정한 본문입니다 열 글자 이상", T0.plus(29, DAYS))` | 반환값 4(바꾸기 전 별점), `rating` = 5, `content` = 새 본문 |
| 30일 지난 수정 | `edit(5, "수정한 본문입니다 열 글자 이상", T0.plus(31, DAYS))` | `BusinessException`, `errorCode` = `REVIEW_EDIT_EXPIRED` / `rating` = 4, `content` = 원래 본문 그대로. 정확히 30일째 경계는 문서에 없다(확인 필요) |
| 삭제 | `delete()` | `status` = `DELETED` |

#### `review/web/ReviewControllerTest` (MockMvc, 공통 준비 사용)

**1. 구매확정 전 품목 거부** — 준비: `buyer1`이 `createDeliveredOrder(buyerId, sellerId, shopId, productA)` + `orderLineIdOf(orderId, productA)`까지만 한다. 동작: 그 품목으로 작성 요청.
- 응답: 422, `$.code` = `REVIEW_NOT_ALLOWED`.
- `reviewCountOf(productA)` = 0, `reviewStatOf(productA)` = `null`, `reviewCreatedEvents()`가 비어 있다.

**2. 남의 품목 거부** — 준비: `buyer1`의 구매확정 품목, `buyer2` 가입·로그인. 동작: `buyer2` 토큰으로 `buyer1`의 `lineId`를 담아 작성 요청.
- 응답: 404, `$.code` = `ORDER_LINE_NOT_FOUND`.
- `reviewCountOf(productA)` = 0, `reviewStatOf(productA)` = `null`.

**3. 작성하면 통계가 쌓인다** — 동작: 공통 준비 B를 실행한다.
- 세 응답 모두 201, `$.reviewId`가 비어 있지 않다.
- `reviewStatOf(productA)`: `reviewCount` 3, `ratingSum` 13, `rating(4)` 2, `rating(5)` 1, `rating(1)`~`rating(3)` 0.
- `GET /api/products/{productA}/review-stats`(Authorization 없이): 200, `$.reviewCount` = 3, `$.average` = 4.3(13÷3=4.333…), `$.distribution['4']` = 2, `['5']` = 1, `['1']`·`['2']`·`['3']` = 0.

**4. 별점 수정으로 분포가 옮겨 간다** — 준비: 공통 준비 B. 동작: `buyer3`이 `PATCH /api/reviews/{R3}`에 `{"rating":2,"content":"수정한 본문입니다 열 글자 이상"}`.
- 응답: 200, `$.id` = `R3`, `$.rating` = 2, `$.content` = 새 본문.
- `reviewStatOf(productA)`: `reviewCount` 3, `ratingSum` 11, `rating(2)` 1, `rating(4)` 1, `rating(5)` 1.
- review-stats: `$.average` = 3.7(11÷3=3.666…), `$.distribution['2']` = 1.

**5. 삭제하면 통계에서 빠진다** — 준비: 공통 준비 B. 동작: `buyer1`이 `R1`을 `DELETE /api/reviews/{R1}`.
- 응답: 204.
- `reviewSnapshotOf`: `R1` → `DELETED` / `R2`, `R3` → `ACTIVE`.
- `reviewStatOf(productA)`: `reviewCount` 2, `ratingSum` 8, `rating(4)` 2, `rating(5)` 0.
- review-stats: `$.average` = 4.0.

**6. 같은 리뷰에 좋아요 두 번은 1** — 준비: 공통 준비 B. 동작: `buyer2`가 `POST /api/reviews/{R1}/likes`를 두 번 보낸다.
- 두 응답 모두 204.
- `reviewSnapshotOf(R1)`: `likerIds()` = [`buyer2`], `likeCount` = 1.

**7. 입력 검증** — 준비: `buyer1`의 구매확정 품목. 동작: 아래 요청을 차례로 보낸다(`rating`·`content`는 따로 적은 것만 바꾼다).
- `rating` 0 / `rating` 6 / `content` `"가"×9` / `content` `"가"×1001` / `orderLineId` 생략 → 다섯 번 모두 400, `$.code` = `INVALID_REQUEST`. `reviewCountOf(productA)` = 0.
- `content` `"가"×10`, `rating` 1 → 201.

**8. 목록: 최신순 커서** — 준비: `buyer1`~`buyer5`가 `productA`에 5점으로 순서대로 작성해 `R1`~`R5`를 만든다. `setReviewCreatedAt(Ri, 기준.plus(i, HOURS))`(`기준` = `Instant.now().minus(10, DAYS)`, `i`는 리뷰 번호)로 맞춘다. 좋아요(각자 자기 토큰으로 `POST .../likes`, 작성자 본인은 누르지 않는다): `R3`에 `buyer1`·`buyer2`·`buyer4`(3개), `R5`에 `buyer1`·`buyer2`(2개), `R1`에 `buyer2`(1개), `R2`·`R4`는 0개. 아래 `GET`은 모두 Authorization 없이 보낸다.
- 최신순 1쪽 `?sort=latest&size=2`: 200, `$.items.length()` = 2, id 순서 `[R5, R4]`, `$.nextCursor` ≠ null. 각 항목에 `id`·`rating`(5)·`content`·`likeCount`·`createdAt`이 있고 `$.items[0].memberId`는 없다.
- 2쪽 `?sort=latest&size=2&cursor={1쪽 nextCursor}`: id 순서 `[R3, R2]`, `$.nextCursor` ≠ null.
- 3쪽 같은 방식: id `[R1]`, `$.nextCursor` = null.

**9. 작성하면 `ReviewCreatedEvent`가 outbox에 쌓인다** — 3번의 동작(공통 준비 B)을 한다. 확인: `reviewCreatedEvents()` 3건. 각 이벤트의 `reviewId` = 해당 응답의 `reviewId`, `productId` = `productA`, `content` = 보낸 본문.

**10. 남의 리뷰는 수정할 수 없다** — 준비: 4번과 같은 준비와 수정(공통 준비 B 뒤 `buyer3`이 `R3`을 2점으로 수정). 동작: `buyer1`이 `R3`을 수정.
- 응답: 404, `$.code` = `REVIEW_NOT_FOUND`. `reviewSnapshotOf(R3).rating()`은 2 그대로.

**11. 30일이 지난 리뷰는 수정할 수 없다** — 준비: 4번과 같은 준비와 수정. `setReviewCreatedAt(R2, Instant.now().minus(31, DAYS))`. 동작: `buyer2`가 `R2`를 수정.
- 응답: 409, `$.code` = `REVIEW_EDIT_EXPIRED`. `reviewSnapshotOf(R2).rating()` 4, `reviewStatOf(productA).ratingSum()` 11 그대로.

**12. 남의 리뷰는 삭제할 수 없다** — 준비: 공통 준비 B. 동작: `buyer2`가 `R1`을 삭제 시도.
- 응답: 404, `$.code` = `REVIEW_NOT_FOUND`. `reviewSnapshotOf(R1).status()`는 `ACTIVE` 그대로.

**13. 삭제한 리뷰는 목록에서 빠진다** — 준비: 5번의 삭제까지 마친 상태(공통 준비 B 뒤 `buyer1`이 `R1`을 삭제). 동작: `GET /api/products/{productA}/reviews?sort=latest`.
- 응답: `$.items.length()` = 2, id는 `R2`·`R3`이고 `R1`은 없다.

**14. 삭제한 품목에는 다시 쓸 수 없다** — 준비: 5번의 삭제까지 마친 상태. 동작: `buyer1`이 같은 `lineId`로 4점 리뷰를 다시 작성.
- 응답: 409, `$.code` = `REVIEW_ALREADY_EXISTS`. `reviewCountOf(productA)` = 3, `reviewStatOf(productA)`는 5번의 값(`reviewCount` 2, `ratingSum` 8, `rating(4)` 2, `rating(5)` 0) 그대로.

**15. 좋아요 취소는 두 번 보내도 0 아래로 내려가지 않는다** — 준비: 공통 준비 B 뒤 `buyer2`가 `R1`에 좋아요를 한 번 누른다. 동작: `buyer2`가 `DELETE /api/reviews/{R1}/likes`를 두 번 보낸다.
- 두 응답 모두 204. `reviewSnapshotOf(R1)`: `likerIds()`가 비어 있고 `likeCount` = 0.

**16. 목록: 좋아요순** — 준비: 8번과 같은 준비. 동작: `?sort=likes`(size 생략, Authorization 없이).
- 응답: 200, `$.items.length()` = 5, 앞 세 개 id `[R3, R5, R1]`와 `likeCount` `[3, 2, 1]`, 뒤 두 개는 `R2`·`R4`(순서 불문), `$.nextCursor` = null.

**17. 목록: 리뷰가 없는 상품** — 동작: 리뷰가 없는 상품(`Ids.newId()`)의 `reviews`(Authorization 없이).
- 응답: 200, `$.items.length()` = 0.

#### `review/application/ReviewConcurrencyTest` (공통 준비 사용)

동시 요청의 HTTP 상태 코드를 `Result.value()`로 읽는다. 같은 작업을 N번 돌릴 때는 `ConcurrencyRunner.run(int threads, Callable<T> task)`, 작업이 서로 다르면 `run(List<Callable<T>> tasks)`(`run(List.of(...))`)를 쓴다. 둘 다 스레드를 동시에 출발시키고 제출 순서대로 `List<Result<T>>`(`Result(T value, Throwable error)`)를 돌려준다.

**1. 같은 품목 동시 작성 2건** — 준비: `buyer1`의 구매확정 품목 하나. 동작: 같은 요청(5점)을 두 스레드가 동시에 보낸다.
- 상태 코드: 201이 정확히 1개, 409가 정확히 1개. 409 쪽 `$.code`는 `DUPLICATE_RESOURCE` 또는 `REVIEW_ALREADY_EXISTS` 중 하나.
- `reviewCountOf(productA)` = 1, 201 응답의 `$.reviewId`로 읽은 `reviewSnapshotOf(…).status()` = `ACTIVE`.
- `reviewStatOf(productA)`: `reviewCount` 1, `ratingSum` 5, `rating(5)` 1.
- `reviewCreatedEvents()` 1건.

**2. 서로 다른 회원 좋아요 동시 100건** — 준비: `buyer1`이 쓴 리뷰 `R1` 하나. 회원 100명(`token("liker" + i + "@test.com")`, i = 1~100)의 토큰을 미리 받아 둔다. 동작: 100명이 동시에 `POST /api/reviews/{R1}/likes`.
- 상태 코드: 100개 모두 204.
- `reviewSnapshotOf(R1)`: `likerIds().size()` = 100, `likeCount` = 100.

**3. 서로 다른 품목 리뷰 동시 50건** — 준비: 구매자 50명(`signup` + `login`)이 각자 `createConfirmedLine`으로 `productA`의 구매확정 품목을 하나씩 가진다. 별점은 번호 `i`(0~49)에 따라 `i < 20` → 5점, `20 ≤ i < 35` → 4점, `35 ≤ i < 45` → 3점, `i ≥ 45` → 1점. 동작: 50명이 동시에 작성한다.
- 상태 코드: 50개 모두 201.
- `reviewCountOf(productA)` = 50, `reviewCreatedEvents()` 50건.
- `reviewStatOf(productA)`: `reviewCount` 50, `ratingSum` 195(20×5 + 15×4 + 10×3 + 5×1), `rating(1)` 5, `rating(3)` 10, `rating(4)` 15, `rating(5)` 20.
- review-stats: `$.average` = 3.9(195÷50).

**리뷰 때 물어볼 것**
- unique 위반을 트랜잭션 안에서 잡으면 왜 안 되나? (As-Is SUP-05)
- 통계를 매번 `COUNT`/`AVG`로 계산하지 않고 따로 두는 이유와 대가는?

---

## 6-2. 상품 검색 · 자동완성 (Elasticsearch) (M)

**목표**: 키워드 검색과 자동완성. 상품 변경이 몇 초 안에 반영된다. ES가 죽어도 검색만 실패한다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| Elasticsearch | 검색 전용 저장소. 원본은 Postgres, ES는 이벤트로 갱신하는 **읽기 모델**이다 |
| alias | 인덱스에 붙이는 별칭. 검색·색인은 항상 alias(`products`)로 한다 → 6-3에서 실제 인덱스를 바꿔치기할 수 있다 |
| external version | 문서에 상품 `version`을 같이 넣으면 ES가 **더 오래된 버전의 쓰기를 거부**한다 → 이벤트 순서가 뒤바뀌어도 최신 유지 |
| edge_ngram | "루틴" → "루", "루틴"처럼 앞부분 조각을 미리 색인해 자동완성을 빠르게 |

**정책 (이 단계에서 정함)**
- 단종·숨김 상품도 문서는 남기고 `status`로 걸러낸다(삭제와 재색인이 엇갈리는 문제를 피함). 검색 결과는 `ON_SALE`만.
- 한국어 형태소 분석(nori)은 쓰지 않는다(별도 플러그인 설치 필요). 표준 분석기 + edge_ngram으로 시작하고, 검색 품질 개선은 Stage 2 이후 과제로 둔다.
- 검색 정렬: 관련도(`_score`) → id. 페이지는 `search_after` 커서(Base64로 감싼 정렬값), size 기본 20·최대 50.
- 자동완성: 상품명 기준 최대 10개, 2자 이상일 때만.

### 할 일

**구현 순서**: 절 번호 순서대로 만든다. 1) 인프라(compose·의존성·`IntegrationTestSupport`·`application.yaml` 타임아웃) → 2) 모듈 경계 → 3) `search.infrastructure`(`ProductIndexDefinition`·`products-index.json` → `ProductIndexer`(`ensureIndex` → `upsert`) → `ProductEventConsumer`) → 4) `search.application`(`SearchErrorCode` → `ProductSearchService`) → 5) `search.web` → 6) `common.security` → 테스트.

**1) 인프라**
- docker-compose: `docker.elastic.co/elasticsearch/elasticsearch:9.4.8`(클라이언트 9.4.5와 같은 9.4 줄의 최신 패치, 2026-10-09 레지스트리에서 태그 확인), `discovery.type=single-node`, `xpack.security.enabled=false`, `ES_JAVA_OPTS=-Xms512m -Xmx512m`, 포트 `127.0.0.1:9200`
- 의존성: `implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'`(Boot 4.1.1에 있다), 테스트 `testImplementation 'org.testcontainers:testcontainers-elasticsearch'`(Testcontainers BOM 2.0.5가 관리)
- `IntegrationTestSupport`: `@ServiceConnection static final ElasticsearchContainer es = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:9.4.8").withEnv("xpack.security.enabled", "false")`(8.x 이후 이미지는 기본으로 보안이 켜져 있어 끈다) → `Startables.deepStart(...)`에 추가
- `application.yaml`: `spring.elasticsearch.connection-timeout: 2s`, `spring.elasticsearch.socket-timeout: 2s`(공통 규칙 X의 ES 타임아웃 2초. Boot 4.1.1 기본값은 1s·30s, 키 이름은 jar 메타데이터로 확인)

**2) search — 모듈 경계** (domain 생략, application + infrastructure, 개발 가이드 §3.3)

- **역할**: search는 상품 원본(Postgres)을 부르지 않는다. product가 내보내는 `product-upserted` 이벤트를 받아 ES에 문서로 복제해 두고, 검색 요청은 그 ES만 읽는다.
- **호출 수단(확인 필요)**: ES를 어떤 클라이언트로 부를지는 문서에 정해져 있지 않다. `spring-boot-starter-data-elasticsearch`가 공식 Java 클라이언트 `ElasticsearchClient`(요청이 ES REST API의 JSON과 1:1이다)와 Spring Data의 `ElasticsearchOperations`를 모두 빈으로 만든다. 아래는 클라이언트와 무관하게 **ES에 보내야 하는 요청의 모양**으로 적었다. external version·alias 교체·`search_after`·bulk를 각 클라이언트의 어떤 메서드가 맡는지는 쓸 클라이언트 jar에서 확인한다.

| 클래스 | 규격 |
|---|---|
| `search/package-info.java` | 모듈 경계. `@ApplicationModule(allowedDependencies = {"common", "product::api"})`(규칙 Y). `product::api`는 이벤트 record(`ProductUpsertedEvent`)와 6-3의 `findForIndexing`을 쓰기 위한 것이다 |

**3) search.infrastructure**

| 클래스 | 규격 |
|---|---|
| `ProductIndexDefinition` | 인덱스를 만들 때 쓰는 설정·매핑 JSON(`src/main/resources/search/products-index.json`)을 읽어 준다. `ensureIndex`와 6-3 재색인이 같은 JSON을 쓰므로 한 곳에 둔다. JSON 내용: 분석기 `autocomplete`(tokenizer `edge_ngram` min 1 max 20, filter `lowercase`), 필드 `productId keyword`, `shopId keyword`, `name text` + 하위 필드 `name.autocomplete`(분석기 autocomplete, 검색 분석기 standard), `description text`, `category keyword`, `price long`, `status keyword`. JSON 내용을 돌려주는 메서드 하나(이름은 구현이 정한다). 패키지는 문서에 정해져 있지 않다(확인 필요, 권장 `search.infrastructure`) |
| `ProductIndexer` | `@Component`. 상품 문서를 ES에 쓰는 유일한 곳. consumer와 6-3 재색인이 같이 쓴다. `void ensureIndex()` — 앱 시작 때 한 번 / `void upsert(ProductUpsertedEvent e, String indexOrAlias)` — 문서 한 건 색인 |
| `ProductEventConsumer` | consumer `search.product-upserted`. `product-upserted` 이벤트를 받아 `indexer.upsert`를 부른다. `void onProductUpserted(String message)` |

**분석기가 두 개인 이유**: `name.autocomplete`는 **색인할 때만** 앞부분 조각(edge_ngram)을 만들고, 검색어는 `standard`로만 쪼갠다. 검색어까지 조각내면 "루틴"이 "루"·"루틴"으로 쪼개져 "루"로 시작하는 모든 문서가 걸린다.

`ensureIndex()` 순서
1. alias `products`가 있는지 확인한다(`HEAD`/`GET _alias/products`). 있으면 끝
2. 없으면 `products-{yyyyMMddHHmmss}`(현재 시각, `Clock` 기준) 인덱스를 `ProductIndexDefinition`의 JSON으로 만들고 alias `products`를 연결한다
- 존재하지 않는 alias 이름으로 문서를 쓰면 ES가 `products`라는 **일반 인덱스를 자동 생성**해 버려 alias를 만들 수 없게 된다(ES 기본 설정 기준, 확인 필요). `ensureIndex`가 먼저 끝나야 한다.
- 앱이 뜰 때 ES가 내려가 있으면 `ensureIndex`가 예외를 던진다. 이때 앱 기동을 막을지 경고만 남기고 넘어갈지는 문서에 정해져 있지 않다(확인 필요). "ES가 죽어도 검색만 실패한다"는 목표와 맞추려면 기동은 막지 않는 쪽이다.

`upsert(e, indexOrAlias)` 순서
1. 문서 본문: 매핑의 필드 이름 그대로 `productId`, `shopId`, `name`, `description`, `category`, `price`, `status`(문자열 그대로, 예 `ON_SALE`). `subscribable`·`version`은 문서 필드로 넣지 않는다
2. 문서 id = `e.productId().toString()`. 요청에 **`version = e.version()`, `versionType = EXTERNAL`**을 붙인다(REST로는 `PUT {indexOrAlias}/_doc/{id}?version={n}&version_type=external`). ES는 저장된 버전이 **같거나 더 크면** 쓰기를 거부(409 `version_conflict_engine_exception`)하므로 순서가 뒤바뀐 이벤트·같은 이벤트의 중복이 문서를 덮어쓰지 못한다
3. 409는 "이미 같거나 더 최신"이므로 `log.debug` 후 정상 반환한다. 그 밖의 ES 오류·타임아웃은 그대로 던진다
- `IOException` 같은 검사 예외는 `RuntimeException`으로 감싸 던진다. `BusinessException`으로 감싸지 않는다(규칙 X의 consumer 모양).

`ProductEventConsumer.onProductUpserted(String message)` 순서
1. `eventEnvelopes.parse(message, ProductUpsertedEvent.class)` — JSON 오류는 `JacksonException`이라 재시도 없이 DLT(3-2)
2. `EventContext.run(typed.envelope(), () -> indexer.upsert(typed.payload(), "products"))`
- `inboxGuard.runOnce`를 쓰지 않는다. ES 쓰기 자체가 external version으로 멱등이고, `runOnce`는 DB 트랜잭션이라 그 안에서 ES를 부르면 안 되기 때문이다(규칙 X).

- `product-discontinued`는 따로 구독하지 않는다. 단종도 `product-upserted`(status DISCONTINUED)가 함께 발행되기 때문이다(3-3).
- ES가 내려가 있으면 consumer가 실패 → 3번 재시도 → DLT. 복구 후 DLT 재처리 API(`POST /admin/api/dlt/{topic}/{partition}/{offset}/replay`, 7-3 — DLT 레코드 하나를 원래 토픽으로 다시 보낸다)로 재처리하거나 6-3 재색인으로 맞춘다.

**4) search.application**

| 클래스 | 규격 |
|---|---|
| `SearchErrorCode` | 검색 에러 코드. `ErrorCode`를 구현하는 enum. `SEARCH_UNAVAILABLE`(503) — 값·메시지는 위 Z 표. domain을 생략하므로 둘 패키지는 문서에 없다(확인 필요, 권장 `search.application`) |
| `ProductSearchResult` | 검색 결과 한 건. `record (productId, shopId, name, category, price)` — 응답 `ProductSearchResponse`(search.web 절)와 같은 필드 |
| `ProductSearchService` | 검색·자동완성 요청을 ES 쿼리로 바꿔 실행한다. `CursorPage<ProductSearchResult> search(String q, String category, String cursor, int size)` / `List<String> autocomplete(String q)` |

`search(q, category, cursor, size)` 순서
1. `cursor`가 비어 있지 않으면 해석해 `search_after` 값 두 개(점수, `productId`)로 복원한다. 해석 실패는 400 `INVALID_REQUEST`다. 1-7의 `Cursor`는 `(createdAt, id)` 전용이라 쓸 수 없고, 여기서는 `[score, productId]` JSON 배열을 Base64 URL-safe(패딩 없음)로 감싼 값을 쓴다. **이 해석은 ES 호출을 감싸는 `try` 밖에서** 한다(안에서 하면 400이 503으로 바뀐다)
2. 별칭 `products`에 검색 요청을 보낸다: `query.bool.must = multi_match(q, fields [name^3, description])`, `query.bool.filter = [term status = ON_SALE, (category가 있으면 term category)]`(filter라 점수에 영향이 없다), `sort = [_score desc, productId asc]`, `size = size + 1`, 커서가 있으면 `search_after`. 동점을 가르는 두 번째 정렬 키로 `_id`가 아니라 매핑의 `productId`(keyword)를 쓴다(`_id` 정렬은 기본으로 막혀 있다. 확인 필요)
3. 받은 hit가 `size`보다 많으면 다음 페이지가 있다. `size`개만 남기고, **`size`번째 hit**의 `sort` 배열(`[score, productId]`)을 1번의 형식으로 감싸 `nextCursor`에 넣는다. 없으면 `null`
4. 각 hit의 `_source`를 `ProductSearchResult`로 바꿔 `new CursorPage<>(items, nextCursor)`로 반환한다
5. ES 예외·타임아웃은 `BusinessException(SEARCH_UNAVAILABLE)`(503)로 바꾼다

`autocomplete(q)` 순서: `match` 쿼리(`name.autocomplete` 필드에 `q`) + `filter status = ON_SALE`, `size = 10`, `_source = ["name"]`을 `products`에 보내고, hit의 `_source.name`을 모아 `List<String>`으로 반환한다. 2자 미만은 컨트롤러 검증이 막으므로 서비스는 검사하지 않는다. 실패는 `search`와 같이 `SEARCH_UNAVAILABLE`.

**5) search.web**

| API | 요청 | 응답 |
|---|---|---|
| `GET /api/search/products?q=&category=&cursor=&size=` | `q` 1~50자 필수 | 200 `CursorPage<ProductSearchResponse(productId, shopId, name, category, price)>` |
| `GET /api/search/autocomplete?q=` | 2~20자 | 200 `List<String>` |

- `SearchController`는 `ProductSearchService`만 부른다. `q`의 길이 검증은 메서드 파라미터의 `@Size`·`@NotBlank`에 붙이고(클래스에 `@Validated` 금지, Part 2 공통 규칙 P-3), `size`는 기본 20·`@Min(1) @Max(50)`.

**6) common.security — SecurityConfig**

- 두 GET을 공개로 연다: `.requestMatchers(HttpMethod.GET, "/api/search/**").permitAll()`

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 이 단계에서 추가하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `productSearchDocOf(UUID productId)` → `ProductSearchDoc` | ES REST `GET {es 주소}/products/_doc/{productId}`(alias로 읽는다)를 JDK `HttpClient`로 보내 응답 JSON에서 한 번에 읽는다(요청 코드는 테스트에 쓰지 않는다): `found`, `_version`(external version이 그대로 저장된다), `_source.name`·`.description`·`.category`·`.price`·`.status`. `ProductSearchDoc`은 `support/`의 record `(boolean found, Long version, String name, String description, String category, Long price, String status)`이고, 404면 `found` = `false`에 나머지는 `null`이다. ES 주소는 `IntegrationTestSupport`의 `es.getHttpHostAddress()`(확인 필요: `TestFixtures`가 `es`에 접근하는 방법. `es`가 package-private이면 노출이 필요하다) | `ProductSearchTest` 4·5·8번, 6-3 `ReindexTest` 2·5번 |

이미 있는 헬퍼와 주입: `RegisterProductService`·`UpdateProductService`(주입), `outboxRelay.relayOnce()`(`int` 반환. `READY` outbox 행을 Kafka로 한 번 발행한다), `Eventually.await(Duration timeout, ThrowingRunnable assertion)`(단언을 100ms마다 다시 실행해 통과하면 끝나고, 시간이 다 되면 마지막 실패를 던진다), `ProductIndexer.upsert`(이 단계에서 만든다). `ProductUpsertedEvent` 필드 순서는 `(productId, shopId, name, description, category, price, status, subscribable, version)`(3-3)이다.
헬퍼를 쓰는 방법은 다음과 같다.
- 응답: `jsonPath`. 검색은 `$.items[i].productId`·`.shopId`·`.name`·`.category`·`.price`와 `$.nextCursor`(마지막 페이지는 `null`). 자동완성은 응답이 배열이라 `$.length()`와 `$[i]`. 에러 코드는 `$.code`.
- 색인 반영 대기: `Eventually.await(Duration.ofSeconds(10), …)`로 검색 결과를 기다린다(기본 refresh 주기 1초로 충분하다. 못 기다리면 `POST /products/_refresh`). "안 나온다"는 확인은 Eventually로 기다린 뒤 한 번 더 확인한다.

**공통 준비**: 판매자 1명, 가게 1개. 상품은 이름·설명을 정해야 하므로 `TestFixtures.registerProduct`(이름이 `product`로 고정)가 아니라 테스트가 `RegisterProductService.register(sellerId, shopId, new RegisterProductCommand(이름, 설명, ProductCategory.X, 가격, 10, false))`로 만든다. 등록 뒤 `outboxRelay.relayOnce()`로 이벤트를 흘린다. ES 문서는 테스트가 끝나도 지워지지 않으므로(DB 초기화 대상이 아니다) 케이스마다 아래에 굵게 적은 **검색어**를 따로 쓰고, 다른 케이스의 상품 이름은 "루틴"으로 시작하지 않게 한다. 검색 요청은 `GET /api/search/products?q=…`(Authorization 없이)이다.

#### `search/application/ProductSearchTest` (MockMvc로 공개 API를 부른다)

**1. 등록 → 검색됨** (검색어 **마그네슘**, **근육이완**) — 준비: 상품 `P` = 이름 `"마그네슘 데일리 정"`, 설명 `"수면용 근육이완 보충제"`, `HEALTH`, 15000원. 동작: `relayOnce()` → Eventually로 `q=마그네슘` 조회.
- 응답: 200, `$.items.length()` = 1, `$.items[0].productId` = `P`, `.shopId` = 가게 ID, `.name` = `"마그네슘 데일리 정"`, `.category` = `HEALTH`, `.price` = 15000, `$.nextCursor`는 `value(nullValue())`.
- `q=근육이완` → `$.items.length()` = 1, `$.items[0].productId` = `P`.

**2. 숨김·단종은 검색에서 빠진다** (검색어 **오메가**) — 준비: 상품 `P` = 이름 `"오메가 쓰리 캡슐"`, 설명 `"혈행 개선 보충제"`, `HEALTH`, 22000원. 아래를 차례로 하고 매번 `relayOnce()` 후 Eventually로 `q=오메가`를 확인한다.
- 등록 직후: `$.items.length()` = 1.
- `updateProductService.changeStatus(sellerId, shopId, P, ProductStatus.HIDDEN)` 후: `$.items.length()` = 0.
- `changeStatus(…, ON_SALE)` 후: `$.items.length()` = 1, `$.items[0].productId` = `P`.
- `changeStatus(…, DISCONTINUED)` 후: `$.items.length()` = 0.

**3. 자동완성** (검색어 **루틴**) — 준비: 상품 4개, 모두 `HEALTH`.

| 상품 | 이름 | 상태 |
|---|---|---|
| A | `"루틴 비타민"` | `ON_SALE` |
| B | `"루틴 마스크팩"` | `ON_SALE` |
| C | `"비타민 루틴"` | `ON_SALE` |
| D | `"루틴 오일"` | 등록 후 `changeStatus(…, HIDDEN)` |

동작: `relayOnce()` → Eventually로 `GET /api/search/autocomplete?q=루틴`.
- 응답: 200, `$.length()` = 2, 내용은 `["루틴 비타민", "루틴 마스크팩"]`(순서 불문). C와 D는 없다.

**3-1. 자동완성은 최대 10개** (검색어 **젤리맛**) — 준비: 이름 `"젤리맛 01"` ~ `"젤리맛 12"`인 `ON_SALE` 상품 12개. 동작: `GET /api/search/autocomplete?q=젤리맛`. 확인: 200, `$.length()` = 10, 모든 값이 `"젤리맛 "`으로 시작.

**4. 순서 역전: 오래된 버전이 늦게 와도 최신 유지** (검색어 **순서역전**) — 준비: 새 `productId` `X`와 가게 ID로 `ProductUpsertedEvent` 두 개를 직접 만든다(`category` = `"ETC"`, `status` = `"ON_SALE"`, `subscribable` = false).
- `v3` = 이름 `"순서역전 버전삼"`, 설명 `"세 번째 내용"`, 가격 3000, `version` 3.
- `v2` = 이름 `"순서역전 버전이"`, 설명 `"두 번째 내용"`, 가격 2000, `version` 2.
- 동작: `productIndexer.upsert(v3, "products")` → `productIndexer.upsert(v2, "products")`.
- 확인: 두 번째 호출이 예외 없이 끝난다. `productSearchDocOf(X)` → `version()` = 3. Eventually로 `q=순서역전` → `$.items.length()` = 1, `$.items[0].name` = `"순서역전 버전삼"`.

**5. 같은 이벤트 두 번** (검색어 **중복이벤트**) — 준비: `ProductUpsertedEvent` `Y` = 이름 `"중복이벤트 상품"`, 설명 `"같은 이벤트 검증"`, 가격 1000, `version` 1. 동작: `productIndexer.upsert(Y, "products")`를 두 번 호출.
- 확인: 두 번째 호출도 예외가 없다. `productSearchDocOf(Y.productId())` → `version()` = 1. Eventually로 `q=중복이벤트` → `$.items.length()` = 1.

**6. 관련도 정렬과 페이지** (검색어 **프로폴리스**)
- 준비: 상품 `A` = 이름 `"프로폴리스 스프레이"`, 설명 `"목 건강 케어"` / 상품 `B` = 이름 `"목 케어 스프레이"`, 설명 `"프로폴리스 함유"`(둘 다 `HEALTH`, `ON_SALE`).
- `q=프로폴리스&size=20`: `$.items.length()` = 2, id 순서 `[A, B]`, `$.nextCursor` = null.
- `q=프로폴리스&size=1`: `$.items.length()` = 1, id `[A]`, `$.nextCursor` ≠ null.
- 위 `nextCursor`를 `cursor=`로 넘겨 `q=프로폴리스&size=1`: `$.items.length()` = 1, id `[B]`, `$.nextCursor` = null.

**7. 카테고리 필터** (검색어 **카테고리검증**) — 준비: `X1` = 이름 `"카테고리검증 영양제"`, `HEALTH` / `X2` = 이름 `"카테고리검증 크림"`, `BEAUTY`(둘 다 `ON_SALE`).
- `q=카테고리검증&category=HEALTH`: `$.items.length()` = 1, `$.items[0].productId` = `X1`.
- `q=카테고리검증`(`category` 생략): `$.items.length()` = 2, id 집합 `{X1, X2}`.
- `q=카테고리검증&category=FOOD`: `$.items.length()` = 0.

**8. 단종 상품의 ES 문서는 남는다** (검색어 없음) — 준비: 상품 `P` = 이름 `"단종문서 캡슐"`, 설명 `"혈행 개선 보충제"`, `HEALTH`, 22000원. 동작: `changeStatus(sellerId, shopId, P, ProductStatus.DISCONTINUED)` → `relayOnce()` → Eventually로 `productSearchDocOf(P)`를 기다린다.
- 확인: `productSearchDocOf(P)` → `found()` = true, `status()` = `DISCONTINUED`.

**9. 동점은 `productId` 오름차순** (검색어 **동점검증**) — 준비: 이름 `"동점검증 세트"`, 설명 `"동일한 설명"`이 같은 상품 `C1`, `C2`. `lo`·`hi` = 두 `productId`를 문자열(`UUID.toString()`)로 오름차순 정렬한 앞·뒤.
- `q=동점검증&size=1`: id `[lo]`, `$.nextCursor` ≠ null → 그 커서로 다음 쪽: id `[hi]`, `$.nextCursor` = null.

#### `search/web/SearchRequestTest` (추가)

모두 `GET`, Authorization 없이.

| 요청 | 확인할 것 |
|---|---|
| `/api/search/products?q=` / `q`가 `"가"×51` / `q=테스트&size=0` / `q=테스트&size=51` | 400, `$.code` = `INVALID_REQUEST` |
| `q=테스트&cursor=not-a-cursor!!` / `q=테스트&cursor=YWJj` | 400, `$.code` = `INVALID_REQUEST` |
| `q`가 `"가"×50`, `size=50` | 200 |
| `/api/search/autocomplete?q=루` / `q`가 `"가"×21` | 400, `$.code` = `INVALID_REQUEST` |
| `/api/search/autocomplete?q=루틴` | 200 |

#### `search/web/SearchUnavailableTest`

**ES가 멈춰도 검색만 실패한다** — 준비: 판매자·가게·`registerProduct(sellerId, shopId)`로 상품 `P` 하나. 동작: `es.getDockerClient().pauseContainerCmd(es.getContainerId()).exec()`로 ES 컨테이너를 멈추고, `finally`에서 반드시 `unpauseContainerCmd`로 되돌린다.
- `GET /api/search/products?q=product`: 503, `$.code` = `SEARCH_UNAVAILABLE`. 응답이 10초 안에 돌아온다(`assertTimeout(Duration.ofSeconds(10), …)`).
- `GET /api/search/autocomplete?q=pr`: 503, `$.code` = `SEARCH_UNAVAILABLE`.
- `GET /api/products`(DB): 200, `$.items.length()` = 1, `$.items[0].id` = `P`.
- 상품 등록 `POST /api/shops/{shopId}/products`(판매자 토큰, 새 `Idempotency-Key`, 본문은 `RegisterProductRequest`): 201.

**리뷰 때 물어볼 것**
- 검색 결과와 DB가 잠시 다를 수 있는데 괜찮은가?
- external version이 없다면 이벤트 순서 역전을 어떻게 막았을까?

---

## 6-3. (선택) 무중단 재색인 (S)

> 이 단계는 **선택**이다. 하지 않으면 재색인이 필요할 때 인덱스를 지우고 다시 만들며(그동안 검색 결과가 비어 있음), 그 한계를 회고에 적는다.

**목표**: 전체 재색인 중에도 검색이 멈추지 않는다(As-Is SUP-04).

**새로 등장**: 이중 쓰기 — 재색인하는 동안 consumer가 기존 인덱스(alias)와 새 인덱스 **둘 다**에 쓴다. 전체 적재와 실시간 변경이 겹쳐도 external version 덕분에 최신 버전만 남는다.

**정책**: 재색인은 한 번에 하나(잡 락 이름 `search-reindex`). 재색인 대상 인덱스 이름은 Redis `search:reindex-target`(TTL 2시간)에 둔다.

### 할 일

**구현 순서**: 절 번호 순서대로 만든다. 1~2) 상품 keyset 쿼리(`product.domain` → `product.infrastructure`) → 3~4) `ProductApi.findForIndexing`(`product.api` → `product.application`) → 5) `search.infrastructure`(ES 쓰기 작업 → consumer 이중 쓰기 → 실행기 빈) → 6) `ReindexService.reindex()` → 7) 관리자 API → 테스트.

**1) product.domain**

| 클래스 | 규격 |
|---|---|
| `ProductRepository` | 상품 keyset 쿼리를 추가한다. `id > :afterId ORDER BY id LIMIT :limit`, 상태를 가리지 않는다(단종·숨김 문서도 인덱스에 두고 `status`로 걸러내는 정책, 6-2). 비교는 DB가 하므로 자바 `UUID` 정렬과 달라도 문제없다. 메서드 이름은 문서에 없다(확인 필요). 첫 호출의 `afterId`에 `null`을 허용할지도 문서에 없다(확인 필요). `ProductApiImpl.findForIndexing`이 쓴다 |

**2) product.infrastructure**

| 클래스 | 규격 |
|---|---|
| `JpaProductRepository` | 위 keyset 쿼리의 Jpa 구현 |

**3) product.api**

| 클래스 | 규격 |
|---|---|
| `ProductApi` | `List<ProductUpsertedEvent> findForIndexing(UUID afterId, int limit)` — 재색인이 상품 전체를 한 번에 읽지 않고 `limit`개씩 읽어 가는 keyset 조회다. 반환 타입은 `product-upserted` 이벤트 record(3-3)이고 현재 상품 상태에서 같은 조립 코드로 채운다(`version`은 DB의 `product.version`). 이벤트와 같은 필드의 별도 record를 두면 `ProductIndexer.upsert`에 넘길 때마다 변환해야 해서 새로 만들지 않는다. 다음 호출의 `afterId`는 받은 목록의 **마지막 `productId`**, 받은 개수가 `limit`보다 적으면 끝이다. `search` → `product::api` 의존(공통 규칙 Y). `ReindexService`가 쓴다 |

**4) product.application**

| 클래스 | 메서드 |
|---|---|
| `ProductApiImpl` | `List<ProductUpsertedEvent> findForIndexing(UUID afterId, int limit)` — `ProductApi` 구현 |

**5) search.infrastructure**

| 클래스 | 규격 |
|---|---|
| ES 쓰기 작업 | 새 인덱스 생성(`ProductIndexDefinition`의 JSON), bulk 색인(문서마다 external version), alias `products` 원자 교체(`_aliases` 한 요청), 새 인덱스 `refresh`, 인덱스 삭제, alias가 가리키는 현재 인덱스 이름 조회. 메서드 이름과 둘 클래스는 문서에 없다(확인 필요: `ProductIndexer`가 "상품 문서를 ES에 쓰는 유일한 곳"이므로 거기에 더하는 것이 자연스럽다). `ReindexService`가 쓴다 |
| `ProductEventConsumer` (6-2 수정) | 재색인 중 이중 쓰기: 6-2 consumer 순서 2번의 `indexer.upsert(typed.payload(), "products")` 뒤에, `StringRedisTemplate`로 Redis `search:reindex-target`을 읽어 값이 있으면 같은 문서를 `indexer.upsert(payload, target)`으로 그 인덱스에도 쓴다. 두 쓰기 사이에 실패해 같은 메시지가 다시 와도 이미 쓴 쪽은 409로 무시되고 못 쓴 쪽만 반영된다. Redis 의존성을 consumer에 더한다 |
| 실행기 빈 | `Executors.newSingleThreadExecutor()` 빈(`@Async` 대신. MDC traceId는 `JobRunner`가 새로 만든다). 클래스 이름은 문서에 없다 |

**6) search.application**

| 클래스 | 메서드 |
|---|---|
| `ReindexService` | `int reindex()` — `@Transactional` 없음 |

`ReindexService.reindex()` — **누가 부르나**: 아래 `search.web` 절의 API가 `JobRunner`로 부른다. `JobRunner.run`은 `Supplier<Integer>`를 받으므로 `reindex()`는 색인한 문서 수(`int`)를 반환한다.
1. `newIndex = "products-" + 시각` 인덱스를 `ProductIndexDefinition`의 JSON으로 만든다(`ensureIndex`와 같은 매핑). alias는 아직 붙이지 않는다
2. Redis `SET search:reindex-target newIndex EX 7200`(`StringRedisTemplate`의 `opsForValue().set(키, 값, Duration.ofHours(2))`) → 이 순간부터 6-2의 consumer가 새 인덱스에도 쓴다(위 `search.infrastructure` 절의 consumer 이중 쓰기). 적재 중에 들어온 변경이 새 인덱스에서 빠지지 않게 하려는 것이다
3. `afterId = null`부터 `productApi.findForIndexing(afterId, 1_000)`을 반복해 bulk 색인한다. 문서마다 `version`을 external version으로 붙인다(실시간 변경이 이미 더 큰 버전을 썼다면 적재 쪽이 409로 거절된다). **bulk 요청은 HTTP 200이어도 항목별로 실패할 수 있다**: 응답의 항목 상태를 보고 409는 무시, 그 밖의 오류가 하나라도 있으면 재색인 실패로 본다
4. alias `products`를 원자적으로 교체한다. `_aliases` 한 요청에 `remove`(옛 인덱스)와 `add`(새 인덱스)를 같이 담는다(두 요청으로 나누면 그 사이에 alias가 비는 순간이 생긴다). 옛 인덱스 이름은 교체 전에 alias `products`가 가리키는 인덱스를 조회해 얻는다. 교체 전에 새 인덱스를 `refresh`하지 않으면 마지막 몇 건이 최대 1초 동안 검색에 보이지 않을 수 있다(기본 refresh 주기 기준. 문서 규격에 없는 단계라 구현 때 판단, 확인 필요)
5. Redis 키 삭제 → 이전 인덱스 삭제
6. 실패하면 Redis 키 삭제, 새 인덱스 삭제, alias는 그대로(검색 영향 없음). `JobRunner`는 예외를 로그만 남기고 삼키므로(2-5) 이 정리는 `reindex()` 안의 `try/catch`가 직접 한다

**7) search.web**

| 클래스 | 규격 |
|---|---|
| 관리자 컨트롤러 | 클래스 이름은 문서에 없다. `POST /admin/api/search/reindex`(ADMIN) → 202. 컨트롤러는 `executor.submit(() -> jobRunner.run("search-reindex", reindexService::reindex))`만 하고 바로 202를 돌려준다. 이미 다른 재색인이 돌고 있으면 `JobRunner`가 락을 못 잡아 건너뛰며 API는 그래도 202다(재색인 시작 여부를 응답으로 알 수 없다) |

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 이 단계에서 추가하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `searchIndexStateOf()` → `SearchIndexState` | 한 번 호출로 읽는다(요청 코드는 테스트에 쓰지 않는다). 읽는 곳 4개: ES `GET /_alias/products`(응답 JSON의 **키**들 → `aliasTargets` = alias `products`가 가리키는 인덱스 이름들, alias가 없어 404면 빈 목록), ES `GET /_cat/indices/products-*?format=json`(각 항목의 `index` → `indexNames` = `products-*` 인덱스 전부), ES `GET /products/_count`(`count` → `docCount` = alias로 센 문서 수, alias가 없으면 0), Redis `StringRedisTemplate.opsForValue().get("search:reindex-target")`(`reindexTarget`, 키가 없으면 `null`). `SearchIndexState`는 `support/`의 record `(List<String> aliasTargets, List<String> indexNames, long docCount, String reindexTarget)`. ES 요청은 JDK `HttpClient`로 보내고 주소는 `es.getHttpHostAddress()`다(6-2 `productSearchDocOf`와 같다) | `ReindexTest` 2·3·4·5번 |

이미 있는 헬퍼: 6-2 `productSearchDocOf`, `String adminToken(String email)`(4-4. `signup(email)` → `changeRole(memberId, "ADMIN")` → `login(email)`로 ADMIN 토큰을 돌려준다), `Eventually.await(Duration timeout, ThrowingRunnable assertion)`(통과할 때까지 100ms마다 단언을 다시 실행).

헬퍼를 쓰는 방법은 다음과 같다.
- DB: `SELECT version FROM product.product WHERE id = ?`(이 한 곳뿐이라 헬퍼 없이 쓴다).
- 검색: `GET /api/search/products?q=재색인상품&size=1`(Authorization 없이).

**공통 준비**: 판매자 1명, 가게 1개. `RegisterProductService.register`로 상품 1,000개를 만든다: 이름 `"재색인상품 " + i`(i = 1~1000), 설명 `"재색인 검증용 상품"`, `ETC`, 1000원, 재고 1. 첫 상품을 `P1`이라 부른다. 옛 인덱스를 채운다: `productApi.findForIndexing(null, 1_000)`로 받은 이벤트 1,000건을 `productIndexer.upsert(e, "products")`로 색인하고 `POST /products/_refresh`. `oldIndex` = `searchIndexStateOf().aliasTargets()`의 유일한 원소. 재색인은 `reindexService.reindex()`를 별도 스레드(`ExecutorService`)에서 부르고 `Future`로 결과를 받는다(반환값 = 색인한 문서 수 1000).

#### `search/application/ReindexTest`

**1. 재색인 도중에도 검색이 끊기지 않는다** — 동작: 스레드 A가 `reindex()`를 시작한다. 시작 직후부터 A가 끝날 때까지 스레드 B가 검색 요청을 10ms 간격으로 연달아 보내며 응답마다 상태 코드와 `$.items.length()`를 기록한다.
- B가 기록한 모든 응답이 200이고 `$.items.length()` = 1.
- B의 요청 횟수 ≥ 5.

**2. 재색인 도중 수정한 상품은 새 인덱스에 수정 내용으로 남는다** — 동작: ① 스레드 A가 `reindex()`를 시작한다. ② 테스트 스레드가 `Eventually.await(Duration.ofSeconds(5), …)`로 `searchIndexStateOf().reindexTarget()`이 `null`이 아닐 때까지 기다린다. ③ 값이 보이는 즉시 `updateProductService.update(sellerId, shopId, P1, new UpdateProductCommand("재색인상품 수정됨", null, null, null, null))` → `outboxRelay.relayOnce()`. ④ A가 끝날 때까지 기다린다.
- ②에서 5초 안에 `reindexTarget()`에 값이 생기고, ③ 시점에 A가 아직 끝나지 않았다.
- 끝난 뒤 Eventually(10초)로: `productSearchDocOf(P1)` → `name()` = `"재색인상품 수정됨"`, `version()` = `SELECT version FROM product.product WHERE id = P1` 값.

**3. 재색인이 실패하면 아무것도 바뀌지 않는다** (추가) — 준비: 공통 준비 + `@MockitoSpyBean ProductApi`에 `doThrow(new RuntimeException("test")).when(productApi).findForIndexing(any(), anyInt())`. 동작: `reindex()`를 `try/catch`로 감싸 호출한다(밖으로 예외가 나오는지는 문서에 없다. 확인 필요).
- `searchIndexStateOf()`: `aliasTargets()` = [`oldIndex`] 그대로 / `indexNames()` = [`oldIndex`] / `reindexTarget()` = `null`.
- 검색 `q=재색인상품&size=1`: 200, `$.items.length()` = 1.

**4. 재색인 API 권한** (추가) — 준비: 일반 회원 토큰, ADMIN 토큰(`adminToken("admin@test.com")`, 4-4). 동작: `POST /admin/api/search/reindex`.
- ADMIN 토큰: 202. 이 케이스는 공통 준비의 1,000개 대신 상품 1개만 등록하고, 호출 전 `searchIndexStateOf().aliasTargets()`를 `before`로 적어 둔다. 응답 뒤 `Eventually.await(Duration.ofSeconds(30), …)`로 `aliasTargets()`가 `before`와 달라질 때까지 기다린 뒤 끝낸다.
- 일반 회원 토큰: 403, `$.code` = `FORBIDDEN`.
- 토큰 없음: 401, `$.code` = `UNAUTHORIZED`.

**5. 재색인이 끝나면 alias가 새 인덱스를 가리키고 정리된다** — 동작: 공통 준비 뒤 `reindex()`를 호출하고 끝나기를 기다린다.
- `reindex()` 반환값 = 1000.
- `searchIndexStateOf()`: `aliasTargets()`가 정확히 1개이고 그 이름이 `oldIndex`와 다르며 `products-\d{14}` 모양 / `indexNames()`에 `oldIndex`가 없다 / `docCount()` = 1000 / `reindexTarget()` = `null`. `productSearchDocOf(P1).found()` = true.

**리뷰 때 물어볼 것**: alias 없이 같은 인덱스를 지우고 다시 만들면 무엇이 문제인가?

---

## 6-4. 취향 추천 (pgvector) (M)

**목표**: 최근 구매 상품으로 취향을 만들고 비슷한 상품을 추천한다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 임베딩 | 문장을 숫자 벡터(1536차원)로 바꾼 것. 뜻이 비슷하면 벡터도 가깝다 |
| pgvector | Postgres에서 벡터를 저장하고 `<=>`(코사인 거리)로 가까운 것을 찾는다 |
| Spring AI | OpenAI 같은 모델 호출을 감싸는 라이브러리. `EmbeddingModel`, `ChatClient` |

**정책 (이 단계에서 정함)**
- 임베딩 모델 `text-embedding-3-small`(1536차원). 입력 = `상품명 + "\n" + 카테고리 + "\n" + 설명 앞 1,000자`.
- 본문 해시(SHA-256)가 바뀐 경우에만 임베딩을 다시 만든다(비용 관리).
- 취향 = 최근 90일 결제 상품 최대 20개 임베딩의 평균. 이미 산 상품과 판매 중이 아닌 상품은 제외, 최대 20개 추천. 구매 이력이 없으면 빈 목록.
- 결과는 Redis `rec:result:{memberId}`에 30분 캐시. 새 주문 결제·탈퇴 시 삭제.
- 장바구니 기반 취향은 이번 범위에서 뺀다(장바구니 이벤트가 없음).

### 할 일

**구현 순서**: 절 번호 순서대로 만든다. 1) 의존성·설정·`FakeOpenAiServer`·`IntegrationTestSupport` 등록 → 2) 마이그레이션 → 3) `common.inbox` 중복 확인 조회 → 4) 모듈 경계 → 5) `recommendation.infrastructure`(consumer: `order-paid` → `member-withdrawn` → `product-upserted`) → 6) `recommendation.application`(`RecommendationResult` → `ProductEmbeddingService` → `RecommendationService.recommend`) → 7) `recommendation.web` → 테스트.

**1) 인프라·설정**
- 의존성: `implementation platform('org.springframework.ai:spring-ai-bom:2.0.1')`, `implementation 'org.springframework.ai:spring-ai-starter-model-openai'`(버전은 BOM). Spring AI는 Boot BOM이 관리하지 않아 BOM을 직접 import한다
- 설정 (키 이름은 `spring-ai-autoconfigure-model-openai` 2.0.1의 메타데이터로 확인)

| 키 | 값 | 이유 |
|---|---|---|
| `spring.ai.openai.api-key` | `${OPENAI_API_KEY}` | |
| `spring.ai.openai.base-url` | 테스트는 가짜 서버 주소 | 확인 필요: Spring AI 2.0의 OpenAI 클라이언트가 이 값 뒤에 `/v1`을 붙이는지(공식 `openai-java` SDK 4.49.0 기반). 가짜 서버가 받은 경로를 로그로 보고 `/v1`을 base-url에 넣을지 정한다 |
| `spring.ai.openai.embedding.options.model` | `text-embedding-3-small` | |
| `spring.ai.openai.embedding.timeout` / `spring.ai.openai.chat.timeout` | `10s` / `60s` | 공통 규칙 X (둘 다 기본 60s) |
| `spring.ai.openai.embedding.max-retries` / `spring.ai.openai.chat.max-retries` | `0` | **기본 3** — 클라이언트가 안에서 다시 부르면 500 한 번에 가짜 서버 호출이 4번 찍히고, 재시도는 Kafka(3-2)·잡이 이미 한다 |

- `EmbeddingModel`은 스타터가 빈으로 만든다. 호출 형태: `float[] vector = embeddingModel.embed(String text)` — 글 한 건을 OpenAI에 보내 `text-embedding-3-small`의 1536차원 벡터를 받는다(`vector.length == 1536`). 타임아웃·HTTP 오류는 `RuntimeException`으로 올라온다. 이 벡터를 `'[0.1,0.2,...]'` 형식의 문자열(공백 없이 쉼표로 잇고 앞뒤에 대괄호)로 바꿔 `CAST(? AS vector)`에 넘긴다. `Arrays.toString`은 쉼표 뒤에 공백이 붙으므로 쓰지 않고 직접 이어 붙인다(pgvector가 공백을 받는지는 확인 필요)
- `support/FakeOpenAiServer`: `/v1/embeddings` → 입력 문자열 해시로 결정적인 1536차원 벡터를 만들어 응답, 호출 횟수 기록, 지연·500 설정 / `/v1/chat/completions` → 고정 요약 문장 (6-5)

**2) 마이그레이션** — `db/migration/recommendation/V{...}__recommendation_create_tables.sql` (`CREATE SCHEMA IF NOT EXISTS recommendation; CREATE EXTENSION IF NOT EXISTS vector;`)
- `recommendation.product_embedding`: `product_id uuid pk`, `embedding vector(1536) X`, `model varchar(50) X`, `source_hash varchar(64) X`, `status varchar(20) X ck IN ('ACTIVE','INACTIVE')`, `updated_at`. 인덱스 `CREATE INDEX idx_product_embedding_hnsw ON recommendation.product_embedding USING hnsw (embedding vector_cosine_ops)`
- `recommendation.member_purchase`: `member_id uuid`, `product_id uuid`, `purchased_at timestamptz`, `pk_member_purchase (member_id, product_id)` (같은 상품 재구매는 시각만 갱신)

**3) common.inbox — 중복 확인 조회**

| 클래스 | 규격 |
|---|---|
| `processed_message` 조회 전용 메서드 | `(consumer, event_id)`가 이미 있는지 **조회만** 한다. `InboxGuard`에는 `runOnce(String consumer, EventEnvelope envelope, Runnable action)`(INSERT와 처리를 한 트랜잭션에 묶는다) 하나뿐이라 조회 전용 메서드가 없다(확인 필요: `InboxGuard`에 더할지 `JdbcTemplate`으로 직접 읽을지). `product-upserted` consumer 순서의 1번에서 쓴다 |

**4) recommendation — 모듈 경계와 SQL 접근**

- 벡터는 JPA로 매핑하지 않고 `JdbcTemplate`으로 다룬다(새 라이브러리 없이. 문자열 형식은 1) 절의 `EmbeddingModel` 호출 형태). 아래 SQL의 `:name` 표기는 설명용이다. 순수 `JdbcTemplate`은 `?`만 받으므로 `IN`·`NOT IN` 목록은 `?`를 개수만큼 이어 붙여야 한다(`NamedParameterJdbcTemplate`도 spring-jdbc에 들어 있다. 어느 쪽을 쓸지는 문서에 없다. 확인 필요)
- **역할**: recommendation은 이벤트로 받은 두 가지를 자기 테이블에 쌓아 둔다 — 상품별 임베딩(`product_embedding`)과 회원별 구매 상품(`member_purchase`). 추천 요청은 이 테이블과 Redis만 보고, 상품 이름·가격·판매 여부만 `productApi`로 동기 조회한다.
- 클래스 이름은 문서에 없어 권장안으로 적었다.

| 클래스 | 규격 |
|---|---|
| `recommendation/package-info.java` | 모듈 경계. `@ApplicationModule(allowedDependencies = {"common", "product::api", "order::api", "member::api"})`(규칙 Y). 이벤트 record·토픽 상수(`ProductUpsertedEvent`, `OrderPaidEvent`, `MemberWithdrawnEvent`)와 `ProductApi.getForCheckout`을 쓰기 위한 것이다. 다른 모듈이 recommendation을 부르지 않으므로 `api` 패키지는 두지 않는다(search도 같다) |

**5) recommendation.infrastructure**

| 클래스 | 규격 |
|---|---|
| `RecommendationEventConsumer` | consumer 세 개를 둔다(규칙 X의 모양). 아래 표 |

| consumer | 처리 순서 |
|---|---|
| `product-upserted` (consumer `recommendation.product-upserted`) | 아래 `product-upserted` consumer 순서 |
| `order-paid` (consumer `recommendation.order-paid`) | 아래 `order-paid` consumer 순서 |
| `member-withdrawn` (consumer `recommendation.member-withdrawn`) | 아래 `member-withdrawn` consumer 순서 |

`order-paid` consumer — `OrderPaidEvent(orderId, memberId, totalAmount, shopOrders[{shopOrderId, shopId, lines[{orderLineId, productId, productName, quantity}]}])`
1. `runOnce` 안에서 `shopOrders[*].lines[*].productId`를 펼쳐 **중복을 제거**한다(같은 상품이 두 번 나와도 같은 PK로 두 번 쓰지 않게)
2. 상품마다 `INSERT INTO recommendation.member_purchase (member_id, product_id, purchased_at) VALUES (?, ?, ?) ON CONFLICT (member_id, product_id) DO UPDATE SET purchased_at = EXCLUDED.purchased_at` — 같은 상품 재구매는 시각만 갱신한다. `purchased_at`에 넣을 시각은 문서에 없다(이벤트 record에 결제 시각이 없다. 후보는 봉투의 `occurredAt`. 확인 필요)
3. `runOnce`가 **끝난 뒤(커밋 뒤)** `redis.delete("rec:result:" + memberId)`. 커밋 전에 지우면 그 사이 `recommend()`가 옛 데이터로 캐시를 다시 채울 수 있다. Redis 삭제가 실패해 예외가 나면 재시도하며, 두 번째는 `runOnce`가 중복으로 건너뛰고 삭제만 다시 한다

`member-withdrawn` consumer — `MemberWithdrawnEvent(UUID memberId)`: `runOnce` 안에서 `DELETE FROM recommendation.member_purchase WHERE member_id = ?` → 끝난 뒤 같은 캐시 키 삭제(`order-paid`의 3번과 같은 이유)

`product-upserted` consumer 순서 — `ProductUpsertedEvent`에서 쓰는 필드는 `productId`, `name`, `description`, `category`, `status`다(가격은 임베딩 입력이 아니다)
1. **중복 확인**: `common.processed_message`에서 `(consumer = "recommendation.product-upserted", event_id = 봉투의 eventId)`가 있는지 **조회만** 한다. 있으면 종료. 이 조회가 없으면 이미 처리한 메시지가 다시 올 때마다 4번에서 OpenAI를 또 부른다(비용). `runOnce`는 5번에서야 중복을 알기 때문이다. 조회 전용 메서드는 위 `common.inbox` 절이다
2. `status`가 `ON_SALE`이 아니면 `runOnce`로 `UPDATE recommendation.product_embedding SET status = 'INACTIVE', updated_at = ? WHERE product_id = ?`만 하고 종료. 아직 임베딩이 없는 상품이면 0행이라 아무 일도 없다
3. **본문 해시**: 정책의 임베딩 입력을 `input`으로 만든다(설명이 1,000자보다 짧으면 전체. `substring(0, Math.min(1000, length))`). `sourceHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(UTF_8)))`(64자 = `varchar(64)`). `SELECT source_hash FROM recommendation.product_embedding WHERE product_id = ?`의 값과 같으면 `runOnce`로 `status = 'ACTIVE'`만 바꾸고 종료(가격만 바뀐 수정, 숨김 → 판매 중 복귀가 여기로 온다). 행이 없으면 다음 단계로
4. **트랜잭션 밖에서** `float[] vector = embeddingModel.embed(input)` → 문자열 `'[...]'`로 변환. 실패하면 예외가 그대로 올라가 3-2 정책으로 재시도 → DLT(`BusinessException`으로 감싸지 않는다)
5. `runOnce("recommendation.product-upserted", envelope, () -> upsert)`: `INSERT INTO recommendation.product_embedding (product_id, embedding, model, source_hash, status, updated_at) VALUES (?, CAST(? AS vector), ?, ?, 'ACTIVE', ?) ON CONFLICT (product_id) DO UPDATE SET embedding = EXCLUDED.embedding, model = EXCLUDED.model, source_hash = EXCLUDED.source_hash, status = 'ACTIVE', updated_at = EXCLUDED.updated_at`. `model`에 적을 값은 문서에 없다(설정 `spring.ai.openai.embedding.options.model`의 값이 자연스럽다. 확인 필요)
- 4번 호출이 성공한 뒤 5번 전에 죽으면 같은 메시지로 임베딩을 한 번 더 부른다(중복 호출 가능성, "리뷰 때 물어볼 것").
**6) recommendation.application**

| 클래스 | 규격 |
|---|---|
| `RecommendationResult` | `record (UUID productId, String name, long price, String thumbnailKey)` — 응답 `RecommendationResponse`(recommendation.web 절)의 필드 + `thumbnailKey`. 이름은 Part 1 규칙 D·E를 따른 권장안이다. `recommend`의 반환이고 Redis 캐시 JSON이다 |
| `ProductEmbeddingService` | 임베딩 생성·저장. 위 `product-upserted` consumer 순서에서 중복 확인 뒤의 일을 맡는다. consumer와 서비스의 분담은 문서에 없다(확인 필요) |

| 클래스 | 메서드 |
|---|---|
| `RecommendationService` | `List<RecommendationResult> recommend(UUID memberId)` |

`RecommendationService.recommend(UUID memberId)` (`@Transactional(readOnly = true)` 아님 — 캐시 적중이면 DB 커넥션을 잡지 않으려고 Redis를 먼저 본다. SQL은 `JdbcTemplate`이 문장마다 실행한다) → `List<RecommendationResult>`
1. Redis 캐시 있으면 반환: `redis.opsForValue().get("rec:result:" + memberId)`가 `null`이 아니면 JSON 문자열을 `ObjectMapper`로 `List<RecommendationResult>`(`TypeReference` 사용)로 바꿔 반환
2. 최근 구매 상품 ID(90일, 최대 20): `SELECT product_id FROM recommendation.member_purchase WHERE member_id = ? AND purchased_at >= ? ORDER BY purchased_at DESC LIMIT 20`(`?`는 `clock.instant().minus(90, DAYS)`). 비어 있으면 빈 목록을 반환한다. 90일 안에서 20개를 고르는 순서와, 정책의 "이미 산 상품 제외"가 이 20개만인지 전체 구매 이력인지는 문서에 명시돼 있지 않다(확인 필요). 아래 4번의 `NOT IN`은 이 목록을 쓰는 것으로 적혀 있다
3. 평균 벡터를 SQL로: `SELECT AVG(embedding)::text FROM recommendation.product_embedding WHERE product_id IN (...)` (pgvector README의 집계 함수 표에 `avg(vector) → vector`가 있다, 2026-10-09 확인). 결과는 `'[...]'` 형식의 문자열 하나(`queryForObject(sql, String.class, ...)`)로, 4번의 `:taste`에 그대로 넘긴다. 구매한 상품에 임베딩 행이 하나도 없으면 `NULL`이 나온다 — 이때의 응답은 문서에 없다(확인 필요: 빈 목록이 자연스럽다)
4. `SELECT product_id FROM recommendation.product_embedding WHERE status = 'ACTIVE' AND product_id NOT IN (:purchased) ORDER BY embedding <=> CAST(:taste AS vector) LIMIT 20` — `<=>`는 코사인 거리라 작을수록 비슷하다. 결과는 가까운 순서의 `List<UUID>`
5. `productApi.getForCheckout(ids)`(`List<ProductForCheckout>`, 없는 ID는 빠진다)로 이름·가격·판매 여부 → `onSale`인 것만, **4번의 순서대로**(반환 목록은 순서를 보장하지 않으므로 `productId`로 맵을 만들어 `ids`를 순회) `RecommendationResult(productId, name, price.amount(), thumbnailKey)`로 만든다 → Redis 저장: `ObjectMapper`로 JSON 문자열을 만들어 `redis.opsForValue().set("rec:result:" + memberId, json, Duration.ofMinutes(30))`
- 5번에서 다시 판매 중인지 거르는 이유: 4번은 임베딩 테이블의 `status`(이벤트로 갱신되어 잠시 늦을 수 있다)만 보기 때문이다.

**7) recommendation.web**

| API | 요청 | 응답 |
|---|---|---|
| `GET /api/recommendations/me` | | 200 `List<RecommendationResponse(productId, name, price, thumbnailUrl)>` |

- `ImageUrls`는 `common.storage`의 `@Component`이고 `String toUrl(String objectKey)`가 키가 `null`이면 `null`, 아니면 `{publicBaseUrl}/{키}`를 돌려준다. Redis 캐시에는 키를 저장한다(URL은 설정에 따라 바뀔 수 있다)
- `RecommendationController`는 `@CurrentMember UUID memberId`로 `recommendationService.recommend(memberId)`를 부르고, `RecommendationResponse`의 `static RecommendationResponse from(RecommendationResult result, ImageUrls imageUrls)`(`ProductSummaryResponse.from`처럼 `thumbnailUrl = imageUrls.toUrl(result.thumbnailKey())`로 채운다. `ImageUrls`는 컨트롤러가 주입받아 넘긴다)로 바꾼다

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 이 단계에서 추가하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `embeddingOf(UUID productId)` → `EmbeddingRow` | JDBC `SELECT status, source_hash, model, vector_dims(embedding), updated_at FROM recommendation.product_embedding WHERE product_id = ?`. `EmbeddingRow`는 `support/`의 record `(String status, String sourceHash, String model, int dims, Instant updatedAt)`. 행이 없으면 `null`(PK가 `product_id`라 "1행"은 `null`이 아님으로 확인한다) | `ProductEmbeddingTest`, `RecommendationTest` 준비 |
| `purchasedProductIdsOf(UUID memberId)` → `Set<UUID>` | JDBC `SELECT product_id FROM recommendation.member_purchase WHERE member_id = ?` | `RecommendationTest` 준비·4·7번 |
| `processedCountOf(String consumer)` → `long` | 기존 `int processedCountOf(String consumer, UUID eventId)`(그 `(consumer, eventId)`의 행 수)와 이름이 같고 인자가 다른 오버로드다. JDBC `SELECT count(*) FROM common.processed_message WHERE consumer = ?`(그 consumer의 행 수) | `ProductEmbeddingTest` 1·2·5·6번 |
| `recommendationCacheOf(UUID memberId)` → `RecommendationCache` | `StringRedisTemplate`로 `get("rec:result:" + memberId)`를 읽고(없으면 `null`) JSON 배열을 `objectMapper.readTree`로 푼다. `getExpire(키, TimeUnit.SECONDS)`가 TTL. `RecommendationCache`는 `support/`의 record `(JsonNode items, long ttlSeconds)`이고 `productIds()`가 `items`의 `productId`를 저장된 JSON 배열의 순서대로 `List<UUID>`로 돌려준다. `items()`는 `JsonNode` 배열이고 항목에 `thumbnailKey`가 있다 | `RecommendationTest` 2·4·6번 |
| `setEmbedding(UUID productId, float x, float y, float z, String status)` → `void` | JDBC `UPDATE recommendation.product_embedding SET embedding = CAST(? AS vector), status = ? WHERE product_id = ?`. 벡터 문자열은 앞 세 성분이 `x, y, z`이고 나머지 1,533개가 0인 1536개를 공백 없이 `,`로 이어 `[`·`]`로 감싼 것(`'[x,y,z,0,0,…]'`) | `RecommendationTest` 준비 |
| `setEmbeddingStatus(UUID productId, String status)` → `void` | JDBC `UPDATE recommendation.product_embedding SET status = ? WHERE product_id = ?` | `RecommendationTest` 3번 |
| `FakeOpenAiServer.embeddingInputs()` → `List<String>` | 받은 `/v1/embeddings` 요청 본문을 받은 순서대로 `readTree`로 읽어 `input` 문자열을 모은다. 건수는 `.size()`. `input`이 문자열인지 배열인지는 서버를 만들 때 실제 요청을 보고 정한다(확인 필요) | `ProductEmbeddingTest` 전부 |

이미 있는 헬퍼: `signup`, `login`, `UUID createPaidOrder(UUID buyerId, UUID... productIds)`(상품을 담아 체크아웃하고 결제 승인까지 마쳐 `orderId`를 돌려준다), `void hideProduct(UUID productId)`, `outboxRelay.relayOnce()`, `Eventually.await(Duration timeout, ThrowingRunnable assertion)`(통과할 때까지 100ms마다 단언을 다시 실행하고, 시간이 다 되면 마지막 실패를 던진다), `List<ConsumerRecord<String, String>> TestKafka.readAll(String topic)`(토픽의 모든 레코드를 처음부터 읽는다. `key()`가 집계 ID, `value()`가 봉투 JSON). 탈퇴(`member-withdrawn`)가 구매 이력과 캐시를 지우는지는 이 단계의 완료 확인에 없다(Part 3의 탈퇴 흐름이 필요해 추가하지 않았다. 확인 필요).

헬퍼를 쓰는 방법은 다음과 같다.
- 응답: `jsonPath`. 추천은 배열이라 `$.length()`와 `$[i].productId`·`.name`·`.price`·`.thumbnailUrl`.
- 가짜 OpenAI 서버(`FakeOpenAiServer`): 메서드는 `FakePgServer`와 같은 모양을 권장안으로 한다: `static FakeOpenAiServer start()`(JDK `HttpServer`를 빈 포트로 띄운다), `String baseUrl()`, `void on(String pathPrefix, Behavior behavior)`(기존 `support/Behavior` record를 쓴다. 예: `Behavior.serverError()`는 500 응답), `List<RecordedRequest> requests(String pathPrefix)`(`RecordedRequest(method, path, headers, body)`), `void reset()`(설정과 기록을 비운다). `IntegrationTestSupport`의 `@AfterEach`에서 `reset()`을 부른다.
- DLT: `TestKafka.readAll("product.product-upserted.v1.dlt")`(3-1). 레코드의 봉투 `eventId`는 `common.outbox_event`의 행 ID와 같다.

**공통 준비**: 판매자 1명, 가게 1개. 상품은 `RegisterProductService.register(sellerId, shopId, new RegisterProductCommand(이름, "설명", 카테고리, 가격, 10, false))`로 만든다. 이벤트는 `outboxRelay.relayOnce()`로 흘리고 `Eventually.await(Duration.ofSeconds(10), …)`로 기다린다.

#### `recommendation/application/ProductEmbeddingTest` (가짜 OpenAI 서버)

공통: 상품 `P` = 이름 `"비타민 C 1000"`, 설명 `"하루 한 알 비타민"`, `HEALTH`, 10000원. 임베딩 입력은 `expectedInput` = `"비타민 C 1000\nHEALTH\n하루 한 알 비타민"`, `expectedHash` = `expectedInput`의 UTF-8 SHA-256 16진수(테스트가 `MessageDigest`로 계산).

**1. 같은 이벤트가 두 번 와도 임베딩 호출은 1번** — 동작 1: `P` 등록 → `relayOnce()` → Eventually로 `embeddingOf(P)`가 `null`이 아닐 때까지.
- `embeddingOf(P).updatedAt()`을 읽어 둔다.
- 동작 2: JDBC `UPDATE common.outbox_event SET status = 'READY', next_attempt_at = now() WHERE topic = 'product.product-upserted.v1'` → `relayOnce()` → 3초 대기.
- 확인: `embeddingInputs()` 여전히 1건, `embeddingOf(P).updatedAt()`이 읽어 둔 값과 같다, `processedCountOf(…)` 여전히 1.

**2. 가격만 바뀐 수정은 호출하지 않는다** — 준비: 1번 동작 1까지. 동작: `updateProductService.update(sellerId, shopId, P, new UpdateProductCommand(null, null, null, 12000L, null))` → `relayOnce()` → Eventually로 `processedCountOf(…)`가 2가 될 때까지.
- 확인: `embeddingInputs()` 여전히 1건. `embeddingOf(P)`: `sourceHash` = `expectedHash`.

**3. 설명은 앞 1,000자만 쓴다** (추가) — 준비: 상품 이름 `"자르기 검증"`, 설명 `"가"×1500`, `HEALTH`. 동작: 등록 → `relayOnce()` → Eventually로 `embeddingInputs()`가 1건이 될 때까지. 확인: 그 입력 = `"자르기 검증\nHEALTH\n" + "가"×1000`. `embeddingOf(상품).sourceHash()`도 그 문자열의 SHA-256.

**4. 숨김·단종은 INACTIVE, 복귀는 ACTIVE (호출 없이)** — 준비: 1번 동작 1까지. 동작과 확인(매번 `relayOnce()` 후 Eventually로 `embeddingOf(P).status()`를 기다린다):
- `changeStatus(sellerId, shopId, P, HIDDEN)` → `status` = `INACTIVE`.
- `changeStatus(…, ON_SALE)` → `status` = `ACTIVE`.
- `changeStatus(…, DISCONTINUED)` → `status` = `INACTIVE`.
- 세 번의 변경 내내 `sourceHash`는 `expectedHash` 그대로, `embeddingInputs()`는 1건 그대로.

**5. OpenAI 500이면 재시도 후 DLT** — 준비: `openAi.on("/v1/embeddings", Behavior.serverError())`. 동작: `P` 등록 → `relayOnce()`.
- `Eventually.await(Duration.ofSeconds(30), …)`로 `TestKafka.readAll("product.product-upserted.v1.dlt")`에 `P`가 키인 레코드가 1개 올 때까지 기다린다. 그 레코드의 봉투 `eventId` = 이 상품의 `product.product-upserted.v1` outbox 행 ID.
- `openAi.embeddingInputs()` 정확히 4건.
- `embeddingOf(P)` = `null`, `processedCountOf("recommendation.product-upserted")` = 0.

**6. 등록하면 임베딩이 저장된다** — 동작: 1번의 동작 1(`P` 등록 → `relayOnce()` → Eventually로 `embeddingOf(P)`가 `null`이 아닐 때까지).
- 확인: `openAi.embeddingInputs()` = [`expectedInput`]. `embeddingOf(P)`: `status` = `ACTIVE`, `sourceHash` = `expectedHash`, `model` = `text-embedding-3-small`(저장하는 값은 문서에 없다. 확인 필요). `processedCountOf("recommendation.product-upserted")` = 1.

**7. 본문이 바뀐 수정은 임베딩을 다시 만든다** — 준비: 1번 동작 1까지. 동작: `update(sellerId, shopId, P, new UpdateProductCommand("비타민 C 2000", null, null, null, null))` → `relayOnce()` → Eventually로 `embeddingInputs()`가 2건이 될 때까지.
- 확인: 두 번째 입력 = `"비타민 C 2000\nHEALTH\n하루 한 알 비타민"`, `embeddingOf(P).sourceHash()` = 그 문자열의 SHA-256.

**8. OpenAI가 500이어도 상품 API는 정상** — 준비: `openAi.on("/v1/embeddings", Behavior.serverError())`. 동작: 상품 API `POST /api/shops/{shopId}/products`(판매자 토큰, 새 `Idempotency-Key`, 본문 `RegisterProductRequest`) → `GET /api/products/{id}`.
- 확인: 등록 201, 조회 200, `$.id` = 새 상품 ID.

#### `recommendation/web/RecommendationTest` (가짜 OpenAI 서버)

**공통 준비**: 구매자 `buyer`(`signup` + `login`). 상품 6개를 등록한다(모두 설명 `"설명"`).

| 상품 | 이름 | 카테고리 | 가격 | 임베딩 `(x, y, z)` | 임베딩 `status` | 비고 |
|---|---|---|---|---|---|---|
| A | `"비타민 C 1000"` | `HEALTH` | 10000 | (1, 0, 0) | `ACTIVE` | 구매 상품 |
| B | `"비타민 D 2000"` | `HEALTH` | 12000 | (0.9, 0.1, 0) | `ACTIVE` | |
| C | `"오메가 쓰리"` | `HEALTH` | 20000 | (0.6, 0.8, 0) | `ACTIVE` | |
| D | `"고양이 사료"` | `PET` | 30000 | (0, 0, 1) | `ACTIVE` | |
| E | `"비타민 E"` | `HEALTH` | 15000 | (0.95, 0.05, 0) | `INACTIVE` | |
| F | `"비타민 B"` | `HEALTH` | 11000 | (0.8, 0.6, 0) | `ACTIVE` | `hideProduct(F)`로 숨김 |

준비 순서: ① 6개 등록 → `relayOnce()` → Eventually로 여섯 상품 모두 `embeddingOf(상품)`이 `null`이 아닐 때까지 ② 상품마다 `setEmbedding(상품, x, y, z, status)`로 위 표의 벡터·`status`로 덮어쓴다 ③ `hideProduct(F)` ④ `createPaidOrder(buyerId, A)` → `relayOnce()` → Eventually로 `purchasedProductIdsOf(buyerId)`가 {`A`}가 될 때까지.

**1. 비슷한 상품이 앞에, 산 상품은 제외** — 동작: `GET /api/recommendations/me`(Bearer `buyer`).
- 응답: 200, `$.length()` = 3.
- 순서 `[B, C, D]`: `$[0].productId` = B, `$[1].productId` = C, `$[2].productId` = D. F·A·E는 없다.
- `$[0].name` = `"비타민 D 2000"`, `.price` = 12000 / `$[1].name` = `"오메가 쓰리"`, `.price` = 20000 / `$[2].name` = `"고양이 사료"`, `.price` = 30000.

**2. 구매 이력이 없으면 빈 목록** — 동작: 구매가 없는 회원 `buyer2`(`signup` + `login`)로 호출.
- 응답: 200, `$.length()` = 0. `recommendationCacheOf(buyer2)` = `null`.

**3. 두 번째 호출은 캐시에서 나온다** — 준비: 첫 호출(1번과 같다)로 캐시를 만든다. 동작·확인:
- `setEmbeddingStatus(B, "INACTIVE")` 후 두 번째 호출: 200, 응답이 첫 호출과 같다(`[B, C, D]`).
- Redis 키 `rec:result:{buyer}`를 `delete`하고 세 번째 호출: 200, `$.length()` = 2, `[C, D]`.

**4. 새 결제가 캐시를 지운다** — 준비: 첫 호출로 캐시를 만든다. 동작: `createPaidOrder(buyerId, C)` → `relayOnce()` → Eventually(10초)로 아래를 기다린다.
- `purchasedProductIdsOf(buyer)` = `{A, C}`.
- `recommendationCacheOf(buyer)` = `null`.

**5. 90일이 지난 구매는 이력이 아니다** — 준비: 준비 ④까지 한 `buyer`의 구매 시각을 JDBC `UPDATE recommendation.member_purchase SET purchased_at = ? WHERE member_id = ?`(`Instant.now().minus(91, DAYS)`)로 바꾸고 Redis 키 `rec:result:{buyer}`를 지운다. 동작: 호출.
- 응답: 200, `$.length()` = 0.

**6. 첫 호출은 결과를 캐시에 30분 저장한다** — 동작: 첫 호출(1번과 같다).
- `recommendationCacheOf(buyer)`: `null`이 아니고 `productIds()` = `[B, C, D]`, `items().get(0)`에 `thumbnailKey` 필드가 있고 값은 `null`, `ttlSeconds()`는 1700 이상 1800 이하.

**7. 구매 상품이 둘이면 취향은 두 임베딩의 평균** — 준비: 준비 ④까지. 동작: `createPaidOrder(buyerId, C)` → `relayOnce()` → Eventually(10초)로 `purchasedProductIdsOf(buyer)` = `{A, C}`가 될 때까지 기다린 뒤 호출.
- 응답: 200, `$.length()` = 2, `[B, D]`.

**리뷰 때 물어볼 것**
- OpenAI 비용을 어떻게 관리했나?
- consumer에서 외부 호출을 `runOnce` 밖에서 하는 이유는? 그 대가는(중복 호출 가능성)?

---

## 6-5. 월간 리뷰 요약 (S)

**목표**: 매월 1일 상품별 리뷰를 LLM으로 요약한다. 직전 요약 이후 새 리뷰가 없으면 건너뛴다.

**정책 (이 단계에서 정함)**
- 프롬프트에 넣는 리뷰: 좋아요 많은 순 30개 + 별점 낮은 순 10개(중복 제외, 최대 40개이므로 리뷰가 아무리 많아도 40개를 넘지 않는다).
- 요약 대상: 전월(업무 날짜)에 ACTIVE 리뷰가 1개 이상 새로 생긴 상품. 요약 모델은 설정값 `spring.ai.openai.chat.options.model`(예: `gpt-4o-mini` — 착수 시점에 OpenAI가 제공하는 저가 모델인지 확인 필요).
- 요약 잡: 매월 1일 04:00(Asia/Seoul), 상품별 실패는 기록하고 건너뛴다.

### 할 일

**구현 순서**: 절 번호 순서대로 만든다. 1) 마이그레이션 → 2) 설정 → 3) `review.infrastructure`(`ReviewSummaryJob`) → 4) `review.application`(`ReviewSummaryService.summarizeMonth` → `ReviewQueryService.getStats`의 `latestSummary`) → 테스트.

**1) 마이그레이션** — `review.review_summary`: `id uuid pk`, `product_id uuid X`, `period_end date X`, `uk_review_summary_product_period (product_id, period_end)`, `summary text X`, `source_review_count int X`, `model varchar(50) X`, `created_at`

**2) 설정** — `application.yaml`

- `spring.ai.openai.chat.options.model`(위 정책의 요약 모델)을 둔다. `chat.timeout`(60s)·`chat.max-retries`(0)는 6-4 설정 표에 있다. 테스트는 `FakeOpenAiServer`의 `/v1/chat/completions`(6-4)를 쓴다. `ReviewSummaryService`가 읽는다

**3) review.infrastructure**

| 클래스 | 규격 |
|---|---|
| `ReviewSummaryJob` | `@Scheduled(cron = "0 0 4 1 * *", zone = "Asia/Seoul")` → `jobRunner.run("review-summary", () -> service.summarizeMonth(YearMonth.now(clock.withZone(businessZone)).minusMonths(1)))`(잡 이름은 권장안) |

**4) review.application**

| 클래스 | 메서드 |
|---|---|
| `ReviewSummaryService` | `int summarizeMonth(YearMonth month)` — `@Transactional` 없음 |
| `ReviewQueryService` (6-1 수정) | `getStats` 수정 |

`ReviewSummaryService.summarizeMonth(YearMonth month)` — 한 달 치 대상 상품을 돌며 LLM 요약을 만들어 `review_summary`에 저장한다. 순서는 아래다. `JobRunner.run`이 `Supplier<Integer>`를 받으므로 저장한 요약 건수(`int`)를 반환한다. 의존성: `JdbcTemplate`(또는 review repository), Spring AI `ChatClient`(스타터가 만드는 `ChatClient.Builder`로 `build()`한다. 2.0.1에서 확인 필요), `Clock`, `BusinessProperties`(3-4가 만드는 `common.config`의 `@ConfigurationProperties("myroutine") record`. `ZoneId businessZone()`이 업무 시간대 `Asia/Seoul`을 돌려준다). `ReviewSummaryJob`이 부르고, 스케줄러는 테스트에서 꺼져 있으므로 테스트가 직접 부른다(2-5)

`summarizeMonth` 순서
1. 기간: `from = month.atDay(1)`의 업무 시간대 0시, `to = month.plusMonths(1).atDay(1)`의 0시(포함 미만). 0시는 `atStartOfDay(businessProperties.businessZone()).toInstant()`로 구한다. 대상 상품 ID: `SELECT DISTINCT product_id FROM review.review WHERE status = 'ACTIVE' AND created_at >= :from AND created_at < :to` (keyset: `product_id > :after ORDER BY product_id LIMIT :n`씩 반복. `n`은 구현이 정한다)
2. 상품마다 try/catch(실패하면 상품 ID·사유를 `log.error`로 남기고 **다음 상품으로**):
   1. 이미 `(product_id, period_end)` 요약이 있으면 건너뜀(**LLM 호출 전에** 확인 — 비용). 잡을 두 번 돌려도 호출이 한 번이 되는 장치다. `period_end`에 넣을 날짜는 6-5에 정의돼 있지 않다(`month.atEndOfMonth()`, 말일 포함이 자연스럽다. 3-4 정산의 `period_end`도 말일 포함이다. 확인 필요)
   2. 대표 리뷰 선택(위 정책: `ORDER BY like_count DESC LIMIT 30` + `ORDER BY rating, created_at DESC LIMIT 10`): 쿼리 두 번의 결과를 리뷰 ID로 합쳐 중복을 빼면 최대 40개다. 이 쿼리가 그 상품의 **전체 ACTIVE 리뷰**를 대상으로 하는지 대상 월의 리뷰만인지, `source_review_count`가 프롬프트에 넣은 개수(≤ 40)인지 전체 개수인지는 문서에 없다(확인 필요)
   3. 프롬프트: 시스템 "상품 리뷰를 3문장 이내로 장단점 중심 요약" + 리뷰 목록(별점·본문 200자, `substring(0, Math.min(200, length))`. 줄 형식은 구현이 정한다). **트랜잭션 밖에서** `String summary = chatClient.prompt().system(시스템문구).user(리뷰목록).call().content()` (Spring AI의 `ChatClient` 호출 형태. 2.0.1에서 확인 필요). 응답 지연·500은 예외로 올라와 위 try/catch가 받는다(`max-retries`는 0)
   4. `INSERT INTO review.review_summary (id, product_id, period_end, summary, source_review_count, model, created_at) VALUES (...) ON CONFLICT (product_id, period_end) DO NOTHING`. `id`는 `Ids.newId()`, `model`에는 요약에 쓴 설정 `spring.ai.openai.chat.options.model`의 값을 적는다(`model` 값은 문서에 명시돼 있지 않다. 확인 필요). 1번 확인과 이 INSERT 사이에 다른 서버가 먼저 넣어도 `DO NOTHING`이라 안전하다

`ReviewQueryService.getStats` 수정 (6-1) — `review_summary`에서 `product_id`의 `period_end` 최댓값 행 `(periodEnd, summary)`를 읽어 `latestSummary`에 넣는다. 없으면 `null`. 읽는 쿼리를 둘 곳은 문서에 없다(확인 필요: 6-1 통계 읽기 메서드와 같은 repository).

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

**공통 준비**: 요약 월은 오늘 날짜에 기대지 않도록 `YearMonth.of(2026, 9)`로 고정하고 `service.summarizeMonth(...)`를 직접 부른다. 리뷰는 6-1 흐름을 거치지 않고 JDBC로 직접 넣는다. `review`는 상품을 호출하지 않고 6-1 마이그레이션에 외래 키도 없으므로 `productId`·`order_line_id`·`member_id`는 `Ids.newId()`면 된다.

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

---

## 6-6. 1:1 문의 (S)

**목표**: 회원 문의, 관리자 답변.

**정책 (이 단계에서 정함)**: 제재된 회원은 로그인부터 막히므로(1-4) 문의도 쓸 수 없다. 제재 회원 문의 허용은 추가 항목으로 둔다(FR-MEM-07 "문의 외 기능 사용 불가"의 문의 예외는 이번 범위에서 뺀다).

### 할 일

**구현 순서**: 절 번호 순서대로 만든다. 1) 마이그레이션 → 2) `member.domain` → 3) `member.infrastructure` → 4) `member.application` → 5) `member.web` → 테스트.

새 모듈이 아니라 기존 member 모듈(`member.domain`·`member.application`·`member.web`)에 더한다. Part 6 모듈 의존 표(규칙 Y)에 member가 없는 이유다.

**1) 마이그레이션** — `member.inquiry`: `id`, `member_id fk_inquiry_member`, `title varchar(100) X`, `content text X`, `status varchar(20) X ck IN ('OPEN','ANSWERED')`, `version`, `created_at`, `updated_at`, 인덱스 `(member_id, created_at DESC, id DESC)`, `(status, created_at)` / `member.inquiry_answer`: `id`, `inquiry_id uk_inquiry_answer_inquiry fk`, `admin_id uuid X`, `content text X`, `created_at`

**2) member.domain**

| 클래스 | 규격 |
|---|---|
| `InquiryStatus` | `OPEN`, `ANSWERED`. `Inquiry`가 쓴다 |
| `Inquiry` | entity. 위 `member.inquiry` 컬럼과 1:1(`@Version` 포함). `static Inquiry write(UUID memberId, String title, String content)` → `id = Ids.newId()`, `status = OPEN`. `void answer(UUID adminId, String content)` → 상태를 `OPEN → ANSWERED`로(`InquiryStatus`에 전이표 `Map<InquiryStatus, Set<InquiryStatus>>`(`OPEN`만 `ANSWERED`로 갈 수 있다)와 `transitTo(to)`를 두고 `answer`가 `status.transitTo(ANSWERED)`를 부른다. 허용되지 않은 전이는 `BusinessException(INVALID_STATE_TRANSITION)`이라 이미 `ANSWERED`면 409. Part 1 규칙 C). `InquiryService`가 쓴다 |
| `InquiryAnswer` | entity. 위 `member.inquiry_answer`와 1:1. 답변 행을 `Inquiry`가 들고 만들지 `InquiryService`가 따로 저장할지는 문서에 정해져 있지 않다(확인 필요). 같은 문의에 동시에 두 답변이 오면 `uk_inquiry_answer_inquiry` 위반이 최후 방어선이다(규칙 D). `answer`가 쓴다 |
| `MemberErrorCode` | 기존 enum에 `INQUIRY_NOT_FOUND`를 추가한다(값·메시지는 위 Z 표). `InquiryService`가 던진다 |
| `InquiryRepository` | `Optional<Inquiry> findByIdAndMemberId(UUID id, UUID memberId)`, 본인 목록 keyset(`member_id`, `created_at desc, id desc`, 첫·다음 페이지), 관리자 목록 keyset(`status`, `created_at`). 이름·인자는 문서에 없다(권장: `OrderRepository.findByMemberFirstPage(UUID memberId, Limit limit)`·`findByMemberNextPage(UUID memberId, Instant cursorCreatedAt, UUID cursorId, Limit limit)`와 같은 모양). 답변을 읽는 쿼리(`inquiry_id`로)도 필요하다. 인덱스는 위 마이그레이션. `InquiryService`가 쓴다 |

**3) member.infrastructure**

| 클래스 | 규격 |
|---|---|
| `JpaInquiryRepository` | `InquiryRepository`의 Jpa 구현 |

**4) member.application**

| 클래스 | 규격 |
|---|---|
| `InquiryResult`·`InquiryDetailResult` | 응답용 record. 필드는 문서에 없다(확인 필요: `Inquiry` 컬럼 기준 id·title·status·createdAt, 상세는 답변 `content`·`createdAt` 포함). `InquiryService`의 반환이다 |

| 클래스 | 메서드 |
|---|---|
| `InquiryService` | 메서드 이름은 권장안: `UUID write(UUID memberId, ...)`(→ `inquiryId`) / `CursorPage<InquiryResult> getMine(UUID memberId, String cursor, int size)` / `InquiryDetailResult getMine(UUID memberId, UUID inquiryId)`(답변 포함. `findByIdAndMemberId`, 남의 문의·없는 문의 모두 404 `INQUIRY_NOT_FOUND`) / `CursorPage<InquiryResult> getOpen(String status, String cursor, int size)`(관리자) / `@Transactional void answer(UUID adminId, UUID inquiryId, String content)`(문의가 없으면 404 `INQUIRY_NOT_FOUND`) |

- 목록 정렬은 문서에 없다. 인덱스가 `(member_id, created_at DESC, id DESC)`·`(status, created_at)`이라 본인 목록은 최신순(`Limit.of(size + 1)`로 읽어 `size`보다 많이 받으면 `size`번째 항목의 `new Cursor(createdAt, id).encode()`를 `nextCursor`로 하는 커서 페이징), 관리자 목록은 `created_at` 오름차순(오래된 문의가 먼저)으로 읽는 것이 인덱스에 맞는다(확인 필요)

**5) member.web**

| API | 권한 | 요청 | 응답 |
|---|---|---|---|
| `POST /api/inquiries` | 로그인 | `@NotBlank @Size(max=100) title`, `@NotBlank @Size(max=2000) content` | 201 `{inquiryId}` |
| `GET /api/inquiries?cursor=` · `GET /api/inquiries/{id}` | 본인 | | 목록·상세(답변 포함). 남의 문의 → 404 |
| `GET /admin/api/inquiries?status=OPEN&cursor=` | ADMIN | | 목록 |
| `POST /admin/api/inquiries/{id}/answer` | ADMIN | `@NotBlank @Size(max=2000) content` | 204 |

- 컨트롤러 클래스 이름은 문서에 없다(권장: 본인용 `InquiryController`, 관리자용 `AdminInquiryController`). 요청자 ID는 `@CurrentMember UUID`로 받고 관리자 API는 `/admin/api/**`의 ADMIN 권한으로 막는다

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 이 단계에서 추가하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `inquiryOf(UUID inquiryId)` → `InquirySnapshot` | JDBC 쿼리 2개로 한 번에 읽는다: `member.inquiry`의 `member_id`·`title`·`content`·`status`, `member.inquiry_answer`의 `admin_id`·`content`(해당 `inquiry_id`, 생성 순). `InquirySnapshot`은 `support/`의 record `(UUID memberId, String title, String content, String status, List<Answer> answers)`이고 `Answer`는 `(UUID adminId, String content)`. 문의 행이 없으면 `null` | `InquiryTest` 3·7번 |
| `inquiriesOf(UUID memberId)` → `List<InquirySnapshot>` | `member.inquiry`에서 `member_id = ?`인 행을 `created_at` 순으로 읽어 각각 `inquiryOf`와 같은 모양으로 만든다. 회원의 문의 전체이고 문의 행 수는 `.size()` | `InquiryTest` 1·5번 |

이미 있는 헬퍼: `signup`, `login`, `void changeRole(UUID memberId, String role)`(JDBC로 `member.member.role`을 바꾼다).

헬퍼를 쓰는 방법은 다음과 같다.
- 응답: `jsonPath`. 에러 코드는 `$.code`, 목록은 `$.items.length()`·`$.items[i].…`·`$.nextCursor`. 목록·상세의 항목 필드 이름은 문서에 없다(확인 필요: 6-6 `member.application` 절의 `InquiryResult`·`InquiryDetailResult`가 정한다. 아래는 `Inquiry` 컬럼에 맞춘 `id`·`title`·`status`·`createdAt`을 쓴다).

**공통 준비**: 회원 `user1@test.com`, `user2@test.com`(`signup` + `login`으로 ID와 토큰). 관리자 `admin@test.com`: `adminId = signup("admin@test.com")` → `changeRole(adminId, "ADMIN")` → `login`으로 토큰을 다시 받는다(4-4의 `adminToken(email)`은 같은 순서를 밟지만 가입한 회원 ID를 돌려주지 않아 `admin_id` 확인에 쓸 수 없다). 문의 작성 요청은 `POST /api/inquiries`에 `Authorization: Bearer {user1 토큰}`, 본문 `{"title":"배송이 늦어요","content":"주문한 상품이 아직 도착하지 않았습니다."}`다. 응답 `$.inquiryId`로 문의 ID(`Q1`)를 받는다. 답변 요청은 `POST /admin/api/inquiries/{id}/answer`에 `Authorization: Bearer {admin 토큰}`, 본문 `{"content":"확인 후 오늘 중 안내드리겠습니다."}`다.

#### `member/web/InquiryTest`

**1. 문의 작성** — 동작: `user1`이 작성 요청.
- 응답: 201, `$.inquiryId`가 비어 있지 않다.
- `inquiriesOf(user1)` 1개: `memberId()` = `user1`, `title()` = `"배송이 늦어요"`, `content()` = 보낸 본문, `status()` = `OPEN`.

**2. 남의 문의는 보이지 않는다** — 준비: `user1`이 `Q1`을 작성. 동작·확인:
- `user2` 토큰으로 `GET /api/inquiries/{Q1}` → 404, `$.code` = `INQUIRY_NOT_FOUND`.
- `user2` 토큰으로 `GET /api/inquiries/{Ids.newId()}` → 404, `$.code` = `INQUIRY_NOT_FOUND`.
- `user2` 토큰으로 `GET /api/inquiries` → 200, `$.items.length()` = 0, `$.nextCursor` = null.
- 본인: `user1` 토큰으로 `GET /api/inquiries/{Q1}` → 200, `$.title` = `"배송이 늦어요"` / `GET /api/inquiries` → 200, `$.items.length()` = 1, `$.items[0].id` = `Q1`.

**3. 답변하면 ANSWERED** — 준비: `user1`이 `Q1`을 작성. 동작: `admin`이 답변 요청.
- 확인: 204. `inquiryOf(Q1)`: `status()` = `ANSWERED`, `answers()` 1개(`adminId()` = `adminId`, `content()` = `"확인 후 오늘 중 안내드리겠습니다."`). `user1` 토큰으로 `GET /api/inquiries/{Q1}` → 200, `$.status` = `ANSWERED`, 답변 내용이 위 `content`로 들어 있다(필드 이름은 확인 필요).

**4. 관리자 목록은 상태로 거른다** (추가) — 준비: `user1`이 문의 3개(`Q1`, `Q2`, `Q3`)를 작성하고 `admin`이 `Q2`에 답변한다. 동작: `admin` 토큰으로 `GET /admin/api/inquiries?status=OPEN`.
- 응답: 200, `$.items.length()` = 2, id 집합 `{Q1, Q3}`, `$.nextCursor` = null.
- `?status=ANSWERED` → `$.items.length()` = 1, `$.items[0].id` = `Q2`.

**5. 문의 입력 검증** (추가) — 준비: `user1`이 1번의 문의 하나를 작성해 둔다. 동작: `title` 빈 문자열 / `title` `"가"×101` / `content` 빈 문자열 / `content` `"가"×2001`로 각각 요청.
- 모두 400, `$.code` = `INVALID_REQUEST`, `inquiriesOf(user1).size()`는 1 그대로. `title` `"가"×100`·`content` `"가"×2000`은 201.

**6. 관리자 API는 일반 회원에게 막힌다** (추가) — 준비: `user1`이 `Q1`을 작성. 동작: `user1` 토큰으로 `GET /admin/api/inquiries?status=OPEN`과 `POST /admin/api/inquiries/{Q1}/answer`.
- 두 요청 모두 403, `$.code` = `FORBIDDEN`.

**7. 두 번째 답변은 409** — 준비: 3번의 동작까지(`user1`이 `Q1` 작성, `admin`이 답변). 동작: `admin`이 본문 `{"content":"추가 안내입니다."}`로 같은 문의에 다시 답변.
- 확인: 409, `$.code` = `INVALID_STATE_TRANSITION`. `inquiryOf(Q1)`: `answers()`는 여전히 1개이고 `content()`는 첫 답변 그대로, `status()` = `ANSWERED`.

**8. 없는 문의에 답변하면 404** (추가) — 동작: `admin`이 없는 ID(`Ids.newId()`)에 답변.
- 확인: 404, `$.code` = `INQUIRY_NOT_FOUND`.

**9. 답변 입력 검증** (추가) — 준비: `user1`이 `Q1`을 작성. 동작: `admin`이 `content` 빈 문자열 또는 `"가"×2001`로 답변.
- 확인: 400, `$.code` = `INVALID_REQUEST`.

**추가 항목**
- 제재 회원도 문의 쓰기: 로그인은 허용하고 토큰에 `st: "BANNED"`를 넣어, 필터가 허용 목록(`/api/inquiries/**`, `GET /api/members/me`, 로그아웃·갱신) 외 요청을 403 `MEMBER_BANNED`로 막는다. 1-4·4-4의 "제재 → 로그인 403" 테스트가 "로그인 200 + 체크아웃 403"으로 바뀐다

**리뷰 때 물어볼 것**: 문의와 답변을 한 테이블이 아니라 둘로 나눈 이유는?

---

## Part 6 완료
- [ ] `v0.6.0` 릴리스, Part 7 문서 다듬기
