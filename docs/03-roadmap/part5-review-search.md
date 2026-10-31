# Part 5. 리뷰 · 검색 · 문의

> 버전 0.28 · 2026-11-01 · 5-4 문의 목록 정렬(본인 최신순·관리자 오래된 순)과 상세 응답의 답변 필드(`answer.content`·`answer.createdAt`)를 확정했다
> 0.27 · 2026-10-31 · SUB-5(취향 추천)·SUB-6(월간 리뷰 요약)를 로드맵에서 빼서 [서브](../04-sub/README.md)로 옮겼다. 5-1 평점 통계 응답에서 `latestSummary`를 빼고 SUB-6이 더하게 했다
> 0.26 · 2026-10-27
> 0.10 · 2026-10-16 · 쓰지 않는 요소를 걷어냈다. 5-1 `ConfirmedLineInfo`의 `shopId`·`confirmedAt`을 뺐다. 반환값을 아무도 읽지 않는 `ReviewStatRepository.add`·`remove`·`change`, `ReviewRepository.increaseLikeCount`·`decreaseLikeCount`의 "반영 행 수"를 "없음"으로 바꿨다. 5-2 ES 문서의 `updatedAt` 필드를 매핑과 `upsert` 본문에서 뺐다. 5-3 `ProductIndexSnapshot`을 없애고 `findForIndexing`이 `List<ProductUpsertedEvent>`를 돌려주게 했다. SUB-5 `recommendation/api/package-info.java`를 뺐다
> 0.5 · 2026-10-09 · ES 이미지 `9.4.8`(Spring Data Elasticsearch: Boot BOM 6.1.1, 클라이언트 9.4.5), Spring AI `2.0.1`(Boot 4.1.1 스타터에 의존)과 OpenAI 설정 키를 확정했다. Spring AI 2.0의 OpenAI 클라이언트가 자체 재시도(기본 3회)를 하므로 테스트의 호출 횟수에 반영한다. pgvector `avg` 집계를 확인했다. 1-10 규칙에 따라 추천 응답의 `thumbnailKey`를 `thumbnailUrl`로 바꿨다
> 0.4 · 2026-10-02 · 5-3 무중단 재색인을 선택 단계로 하고, 리뷰 임베딩을 제거했다(요약은 단순 선택). 제재 회원 문의 허용은 제안으로, 리뷰 중복은 사전 조회로 했다

> **끝나면**: 구매확정한 상품에 리뷰를 쓰고, 키워드로 상품을 검색하고, 1:1 문의를 남긴다.
> **인프라 추가**: **Elasticsearch**
> 검색 모듈은 **이벤트로 데이터를 받고**, 동기 호출은 상품 조회(`ProductApi`)만 한다. Stage 3에서 가장 먼저 분리될 대상이다.
> 시간이 부족하면 5-1(리뷰), 5-2(검색)만 하고 나머지는 생략할 수 있다. **5-3은 선택 단계**다.
> **릴리스**: `v0.5.0`

| 단계 | 제목 | 크기 |
|---|---|---|
| 5-1 | 리뷰 · 평점 통계 · 좋아요 | M |
| 5-2 | 상품 검색 · 자동완성 (Elasticsearch) | M |
| 5-3 | (선택) 무중단 재색인 | S |
| 5-4 | 1:1 문의 | S |

---

## Part 5 공통 규칙 (Part 1~4 공통 규칙에 더해서)

### X. 외부 AI·검색 호출
- OpenAI·Elasticsearch 호출은 **트랜잭션 밖**에서 한다(공통 규칙 K). consumer에서 외부 호출이 필요하면 순서를 "중복 확인 → 외부 호출 → `InboxGuard.runOnce`로 저장"으로 한다(`runOnce`는 트랜잭션이라 그 안에서 부르면 안 된다).
- 타임아웃: ES 2초, OpenAI 임베딩 10초·요약 60초. 테스트는 가짜 서버(JDK `HttpServer`).
- 호환 버전(2026-10-09 Maven Central 확인, 개발 가이드 §1.1 표에도 적었다): Spring Data Elasticsearch **6.1.1**(Boot 4.1.1 BOM이 관리, ES Java 클라이언트 9.4.5) / Spring AI **2.0.1**(`spring-ai-starter-model-openai` 2.0.1이 Boot 4.1.1 스타터에 의존). 착수할 때 더 새 패치가 있으면 그것으로.
- **consumer 모양** (5-2와 서브 [취향 추천](../04-sub/recommendation.md)의 consumer 설명은 이 모양을 전제로 한다): `@KafkaListener(topics = XxxTopics.YYY, groupId = "{모듈}") void onYyy(String message)` → `eventEnvelopes.parse(message, Payload.class)`가 `TypedEnvelope`(`envelope()` = `eventId`·`traceId`가 든 봉투, `payload()` = 이벤트 record)를 돌려준다 → `inboxGuard.runOnce("{모듈}.{이벤트}", typed.envelope(), () -> 처리)`. `runOnce`는 `processed_message` INSERT와 처리를 한 트랜잭션에 묶어서, 처리가 예외면 INSERT도 롤백되고 다시 소비해도 처리된다. `runOnce`를 쓰지 않는 consumer(5-2)는 `EventContext.run(envelope, () -> ...)`로 MDC만 설정한다. 예외는 던지면 3-2 정책(3번 재시도 → DLT)이고, `BusinessException`·`IllegalArgumentException`·JSON 오류는 **재시도 없이 바로 DLT**다. 그래서 ES·OpenAI 호출 실패는 `BusinessException`이 아니라 일반 예외(`RuntimeException`)로 던져야 재시도된다.
- Redis는 `StringRedisTemplate`만 쓰고 모든 키에 TTL을 둔다([Part 4 공통 규칙 T](part4-auth.md)). JSON 값은 주입받은 `ObjectMapper`(`tools.jackson.databind`)로 문자열 ↔ record를 바꾼다.

### Y. 모듈 의존 (Part 5 추가분)

| 모듈 | `allowedDependencies` |
|---|---|
| review | `common`, `order::api` |
| search | `common`, `product::api` |

### Z. Part 5 에러 코드

| 코드 | HTTP | 메시지 | 정의 위치 | 단계 |
|---|---|---|---|---|
| `REVIEW_NOT_ALLOWED` | 422 | 구매확정한 상품만 리뷰를 쓸 수 있습니다. | `OrderErrorCode` | 5-1 |
| `REVIEW_ALREADY_EXISTS` | 409 | 이미 리뷰를 작성했습니다. | `ReviewErrorCode` | 5-1 |
| `REVIEW_NOT_FOUND` | 404 | 리뷰를 찾을 수 없습니다. | `ReviewErrorCode` | 5-1 |
| `REVIEW_EDIT_EXPIRED` | 409 | 작성 후 30일이 지나 수정할 수 없습니다. | `ReviewErrorCode` | 5-1 |
| `SEARCH_UNAVAILABLE` | 503 | 검색을 일시적으로 사용할 수 없습니다. | `SearchErrorCode` | 5-2 |
| `INQUIRY_NOT_FOUND` | 404 | 문의를 찾을 수 없습니다. | `MemberErrorCode` | 5-4 |

---

## 5-1. 리뷰 · 평점 통계 · 좋아요 (M)

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
| `OrderRepository` | 품목 조회 쿼리를 추가한다. `orderLineId`와 `memberId`로 품목을 찾는다. 품목 행에는 회원이 없고 `order_line → shop_order → orders(member_id)`로 이어지므로 그 경로로 소유를 확인하며 품목을 읽는다. `Optional<OrderLine> findLineByIdAndMemberId(UUID lineId, UUID memberId)` — JPQL로 `OrderLine`·`ShopOrder`(`ol.shopOrderId = so.id`)·`Order`(`so.orderId = o.id`)를 조인해 `ol.id = :lineId and o.memberId = :memberId`로 거른다(`JpaOrderRepository`에 구현). 없거나 남의 품목이면 빈 `Optional`이다. `getConfirmedLine`이 가장 먼저 부른다 |

**3) order.api**

| 클래스 | 규격 |
|---|---|
| `ConfirmedLineInfo` | `record (UUID orderLineId, UUID productId)`. `getConfirmedLine`이 반환하고 `ReviewService.write`가 쓴다 |
| `OrderApi` | `interface { ConfirmedLineInfo getConfirmedLine(UUID memberId, UUID orderLineId); }` — 던지는 예외는 4) `getConfirmedLine` 순서. `order/api`에는 지금 `package-info.java`(`@NamedInterface("api")`)와 `event` 하위 패키지뿐이고, 이 두 클래스는 이 단계에서 `order.api`에 만든다. `ReviewService.write`의 첫 단계에서 부른다 |

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
| `ReviewLikeRepository` | 인터페이스. 구현은 `review.infrastructure`의 `JdbcReviewLikeRepository`(`JdbcTemplate`). 메서드는 아래 |
| `ReviewStatRepository` | 인터페이스. 구현은 `JdbcReviewStatRepository`(`JdbcTemplate`). 메서드는 아래 |
| `ReviewStatRow` | `record (int reviewCount, long ratingSum, int r1, int r2, int r3, int r4, int r5)`. `ReviewStatRepository`의 읽기 결과 |

- `review_like`·`product_review_stat`은 엔티티 없이 `JdbcTemplate`으로 읽고 쓴다. 두 테이블은 SQL 한 문장 증감과 행 단위 읽기만 하고 엔티티로 읽어 바꾸는 경로가 없으며, `review_like`의 복합 PK를 매핑할 이유도 없기 때문이다. `JdbcTemplate`은 호출한 `@Transactional`의 같은 커넥션을 쓴다(`OutboxPublisher`도 같은 방식)

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
- `Optional<ReviewStatRow> findByProductId(UUID productId)` — `product_id`로 통계 행(`review_count`, `rating_sum`, `r1`~`r5`)을 읽는다. 행이 없으면 빈 `Optional`. `ReviewQueryService.getStats`가 쓴다

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
| `review/package-info.java`, `review/api/package-info.java`, `review/api/event/package-info.java` | 모듈 경계 검증. `@ApplicationModule(allowedDependencies = {"common", "order::api"})`(규칙 Y). `review.api`와 `review.api.event`의 `package-info.java`는 `order/api/package-info.java`·`order/api/event/package-info.java`처럼 각각 `@NamedInterface("api") package com.myroutine.review.api;`·`...review.api.event;` 한 줄이다 |

**7) review.infrastructure**

| 클래스 | 규격 |
|---|---|
| `ReviewTopicConfig` | 기동 시 토픽을 만든다. `@Configuration` 클래스에 `@Bean NewTopic`을 둔다: `TopicBuilder.name(ReviewTopics.REVIEW_CREATED).partitions(3).replicas(1).build()`(`org.springframework.kafka.config.TopicBuilder`. `ShopTopicConfig`·`OrderTopicConfig`와 같은 방식). 구독자가 아직 없어도 `REVIEW_CREATED + ".dlt"` 토픽(같은 설정)을 함께 만든다. 구독자가 없는 `product-discontinued`·`product-price-changed`도 `ProductTopicConfig`에서 `.dlt`를 같이 만들고 있다 |
| `JpaReviewRepository` | `ReviewRepository`의 Spring Data 구현(`JpaRepository<Review, UUID>`). 좋아요 수 증감은 `@Modifying` native 쿼리 |
| `JdbcReviewLikeRepository`·`JdbcReviewStatRepository` | `ReviewLikeRepository`·`ReviewStatRepository`의 `JdbcTemplate` 구현(5) 절) |

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
5. `outboxPublisher.publish(ReviewTopics.REVIEW_CREATED, "REVIEW", review.getId(), new ReviewCreatedEvent(review.getId(), line.productId(), content))` — `OutboxPublisher.publish(String topic, String aggregateType, UUID aggregateId, Object event)`는 호출한 트랜잭션 안에서만 쓸 수 있어(없으면 예외) 상태 변경과 같은 트랜잭션에서 발행된다. `aggregateType`은 다른 이벤트처럼 대상 종류를 대문자로 쓴 값(`"SHOP"`, `"PRODUCT"`, `"ORDER"`)을 따라 `"REVIEW"`로 적었다
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

`getStats` 순서 — 평점 통계를 돌려준다
1. 통계 행을 `productId`로 읽는다. `average`는 `rating_sum / review_count`를 소수 1자리로 만든다(반올림 방식과, 행이 없거나 `review_count = 0`일 때의 응답은 문서에 정해져 있지 않다. 확인 필요)
2. `distribution`은 `{1: r1, 2: r2, 3: r3, 4: r4, 5: r5}`
- review는 product를 호출하지 않으므로(허용 의존) 상품이 실제로 있는지는 확인하지 않는다

**9) review.web**

| API | 권한 | 요청 | 응답 |
|---|---|---|---|
| `POST /api/reviews` | 로그인 | `WriteReviewRequest(@NotNull orderLineId, @Min(1) @Max(5) rating, @Size(min=10, max=1000) content)` | 201 `{reviewId}` |
| `PATCH /api/reviews/{id}` | 작성자 | `EditReviewRequest(rating, content)` | 200 `ReviewResponse` |
| `DELETE /api/reviews/{id}` | 작성자 | | 204 |
| `POST /api/reviews/{id}/likes`, `DELETE ...` | 로그인 | | 204 |
| `GET /api/products/{id}/reviews?sort=latest\|likes&cursor=&size=` | 공개 | | 200 `CursorPage<ReviewResponse(id, rating, content, likeCount, createdAt)>` (작성자 정보는 내려주지 않는다 — review가 member를 참조하지 않게) |
| `GET /api/products/{id}/review-stats` | 공개 | | 200 `ReviewStatsResponse(reviewCount, average(소수 1자리), distribution{1..5})` |

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
| `reviewCreatedEvents()` → `List<ReviewCreatedEvent>` | `OutboxAssert.rows("review.review-created.v1")`(topic의 outbox 행을 생성 순 `List<OutboxSnapshot>`으로 읽는다)의 각 행 `payload()`(`OutboxSnapshot`의 `JsonNode`)를 `objectMapper.treeToValue(…, ReviewCreatedEvent.class)`로 풀어 생성 순으로 돌려준다. 건수는 `.size()`. `treeToValue(JsonNode, Class)`는 검사 예외 없이 `T`를 돌려준다(`common.outbox.EventEnvelopes.parse`가 같은 호출을 쓴다) | `ReviewControllerTest`, `ReviewConcurrencyTest` |
| `setReviewCreatedAt(UUID reviewId, Instant instant)` → `void` | JDBC `UPDATE review.review SET created_at = ? WHERE id = ?`(`Timestamp.from(instant)`) | `ReviewControllerTest` 8·11·16번 |

이미 있는 헬퍼: `signup`, `login`, `token`, `openShop`, `registerProduct`, `createDeliveredOrder`, `orderLineIdOf`, `ConcurrencyRunner`, `OutboxAssert`.

헬퍼를 쓰는 방법은 다음과 같다.
- 응답: `jsonPath`. 에러 응답의 코드는 `$.code`. 리뷰 목록은 `$.items[i].id`·`.rating`·`.content`·`.likeCount`·`.createdAt`과 `$.nextCursor`(마지막 페이지는 `null`). 통계는 `$.reviewCount`, `$.average`, `$.distribution['1']`~`['5']`.
- 응답에서 값을 따로 꺼낼 때(`$.reviewId`, `$.nextCursor`): 요청을 `.andReturn()`으로 받아 `getResponse().getContentAsString()`을 본문으로 하고 `JsonPath.read(본문, "$.reviewId")`(`String`)로 꺼낸다. 리뷰 ID는 `UUID.fromString(...)`으로 바꿔 `R1` 등에 담는다. 다음 쪽 요청의 `cursor`에는 꺼낸 `$.nextCursor` 문자열을 그대로 넣는다.

**준비 A**(17번을 뺀 모든 케이스와 `ReviewConcurrencyTest`가 쓴다): 판매자 1명(`seller@test.com`), 가게 1개, 상품 `productA`(`registerProduct(sellerId, shopId, 10000, 100)`, 재고 100). 구매자는 필요한 만큼 `buyer1@test.com`, `buyer2@test.com`, …로 `signup` + `login`해 토큰을 받고, 각자 `createConfirmedLine(buyerId, sellerId, shopId, productA)`로 구매확정된 품목 하나씩을 가진다(`lineOf(buyerN)`이라 부른다). 작성 요청은 `POST /api/reviews`에 `Authorization: Bearer {구매자 토큰}`, 본문 `{"orderLineId":"{lineId}","rating":5,"content":"효과가 좋아요 정말 만족합니다"}`(본문 16자)다. 응답 `$.reviewId`로 리뷰 ID를 받는다.

**준비 B (리뷰 3개)**: `buyer1`이 5점, `buyer2`가 4점, `buyer3`이 4점으로 `productA`에 순서대로 작성한다. 이 리뷰를 `R1`(5점), `R2`(4점), `R3`(4점)이라 부른다. 이때 통계는 `review_count` 3, `rating_sum` 13, `r4` 2, `r5` 1, 나머지 0이다.

#### `review/domain/ReviewTest`

준비 A(`불변식 위반` 케이스는 쓰지 않는다): `Review.write(new ConfirmedLineInfo(lineId, productId), memberId, 4, "열 글자가 넘는 리뷰 본문입니다")`(세 ID는 `Ids.newId()`). 영속 전이라 `createdAt`이 `null`이므로 `ReflectionTestUtils.setField(review, "createdAt", T0)`로 채운다. `T0` = `Instant.parse("2026-09-01T00:00:00Z")`.

| 케이스 | 동작 | 확인할 것 |
|---|---|---|
| 작성 | 준비 A 직후 | 반환값(review getter): `status` = `ACTIVE`, `likeCount` = 0, `rating` = 4, `content` = 넘긴 본문, `orderLineId`·`productId`·`memberId` = 넘긴 값, `id`가 `isNotNull()` |
| 불변식 위반 | `Review.write`를 `rating` 0 / `rating` 6 / 본문 `"가"×9` / 본문 `"가"×1001`로 각각 호출 | 예외: 네 번 모두 `IllegalArgumentException`. 예외: 경계인 `rating` 1·5, 본문 `"가"×10`·`"가"×1000`은 예외 없이 만들어진다 |
| 30일 안 수정 | `edit(5, "수정한 본문입니다 열 글자 이상", T0.plus(29, DAYS))` | 반환값: 4(바꾸기 전 별점). 반환값(review getter): `rating` = 5, `content` = 새 본문 |
| 30일 지난 수정 | `edit(5, "수정한 본문입니다 열 글자 이상", T0.plus(31, DAYS))` | 예외: `BusinessException`, `errorCode` = `REVIEW_EDIT_EXPIRED`. 반환값(review getter): `rating` = 4, `content` = 원래 본문 그대로. 정확히 30일째 경계는 문서에 없다(확인 필요) |
| 삭제 | `delete()` | 반환값(review getter): `status` = `DELETED` |

#### `review/web/ReviewControllerTest` (MockMvc)

**1. 구매확정 전 품목 거부** — 준비: `buyer1`이 `createDeliveredOrder(buyerId, sellerId, shopId, productA)` + `orderLineIdOf(orderId, productA)`까지만 한다. 동작: 그 품목으로 작성 요청.
- 응답(andExpect): 상태 422, `$.code` = `REVIEW_NOT_ALLOWED`.
- DB(reviewCountOf(productA)): 0. DB(reviewStatOf(productA)): `null`(`isNull()`). 반환값(reviewCreatedEvents()): 비어 있다(`isEmpty()`).

**2. 남의 품목 거부** — 준비: `buyer1`의 구매확정 품목, `buyer2` 가입·로그인. 동작: `buyer2` 토큰으로 `buyer1`의 `lineId`를 담아 작성 요청.
- 응답(andExpect): 상태 404, `$.code` = `ORDER_LINE_NOT_FOUND`.
- DB(reviewCountOf(productA)): 0. DB(reviewStatOf(productA)): `null`(`isNull()`).

**3. 작성하면 통계가 쌓인다** — 동작: 준비 B를 실행한다.
- 응답(andExpect): 세 응답 모두 상태 201, `jsonPath("$.reviewId").isNotEmpty()`.
- DB(reviewStatOf(productA)): `reviewCount` 3, `ratingSum` 13, `rating(4)` 2, `rating(5)` 1, `rating(1)`~`rating(3)` 0.
- 응답(andExpect): `GET /api/products/{productA}/review-stats`(Authorization 없이) 상태 200, `$.reviewCount` = 3, `$.average` = 4.3(13÷3=4.333…), `$.distribution['4']` = 2, `['5']` = 1, `['1']`·`['2']`·`['3']` = 0.

**4. 별점 수정으로 분포가 옮겨 간다** — 준비: 준비 B. 동작: `buyer3`이 `PATCH /api/reviews/{R3}`에 `{"rating":2,"content":"수정한 본문입니다 열 글자 이상"}`.
- 응답(andExpect): PATCH 응답 상태 200, `$.id` = `R3`, `$.rating` = 2, `$.content` = 새 본문.
- DB(reviewStatOf(productA)): `reviewCount` 3, `ratingSum` 11, `rating(2)` 1, `rating(4)` 1, `rating(5)` 1.
- 응답(andExpect): `GET /api/products/{productA}/review-stats`(Authorization 없이)의 `$.average` = 3.7(11÷3=3.666…), `$.distribution['2']` = 1.

**5. 삭제하면 통계에서 빠진다** — 준비: 준비 B. 동작: `buyer1`이 `R1`을 `DELETE /api/reviews/{R1}`.
- 응답(andExpect): 상태 204.
- DB(reviewSnapshotOf(Ri)): `status()` — `R1` → `DELETED` / `R2`, `R3` → `ACTIVE`.
- DB(reviewStatOf(productA)): `reviewCount` 2, `ratingSum` 8, `rating(4)` 2, `rating(5)` 0.
- 응답(andExpect): `GET /api/products/{productA}/review-stats`(Authorization 없이)의 `$.average` = 4.0.

**6. 같은 리뷰에 좋아요 두 번은 1** — 준비: 준비 B. 동작: `buyer2`가 `POST /api/reviews/{R1}/likes`를 두 번 보낸다.
- 응답(andExpect): 두 응답 모두 상태 204.
- DB(reviewSnapshotOf(R1)): `likerIds()` = [`buyer2`의 회원 ID], `likeCount` = 1.

**7. 입력 검증** — 준비: `buyer1`의 구매확정 품목. 동작: 아래 요청을 차례로 보낸다(`rating`·`content`는 따로 적은 것만 바꾼다).
- `rating` 0 / `rating` 6 / `content` `"가"×9` / `content` `"가"×1001` / `orderLineId` 생략 → 응답(andExpect): 다섯 번 모두 상태 400, `$.code` = `INVALID_REQUEST`. DB(reviewCountOf(productA)): 0.
- `content` `"가"×10`, `rating` 1 → 응답(andExpect): 상태 201.

**8. 목록: 최신순 커서** — 준비: `buyer1`~`buyer5`가 `productA`에 5점으로 순서대로 작성해 `R1`~`R5`를 만든다. `setReviewCreatedAt(Ri, 기준.plus(i, HOURS))`(`기준` = `Instant.now().minus(10, DAYS)`, `i`는 리뷰 번호)로 맞춘다. 좋아요(각자 자기 토큰으로 `POST .../likes`, 작성자 본인은 누르지 않는다): `R3`에 `buyer1`·`buyer2`·`buyer4`(3개), `R5`에 `buyer1`·`buyer2`(2개), `R1`에 `buyer2`(1개), `R2`·`R4`는 0개. 아래 `GET`은 모두 Authorization 없이 보낸다.
- 최신순 1쪽 `GET /api/products/{productA}/reviews?sort=latest&size=2`(`.andReturn()`으로 받아 본문에서 `$.nextCursor`를 꺼낸다) — 응답(andExpect): 상태 200, `$.items.length()` = 2, `$.items[0].id` = `R5`, `$.items[1].id` = `R4`, `jsonPath("$.nextCursor").value(notNullValue())`. `$.items[0]`과 `$.items[1]` 각각에 `id`·`rating`(5)·`content`·`likeCount`·`createdAt`이 있고, `jsonPath("$.items[0].memberId").doesNotExist()`.
- 2쪽 `?sort=latest&size=2&cursor={1쪽 응답 본문(andReturn 후)에서 꺼낸 $.nextCursor}`(마찬가지로 `.andReturn()`으로 받는다) — 응답(andExpect): `$.items[0].id` = `R3`, `$.items[1].id` = `R2`, `jsonPath("$.nextCursor").value(notNullValue())`.
- 3쪽 같은 방식(2쪽 응답 본문(andReturn 후)에서 꺼낸 `$.nextCursor`) — 응답(andExpect): `$.items.length()` = 1, `$.items[0].id` = `R1`, `jsonPath("$.nextCursor").value(nullValue())`.

**9. 작성하면 `ReviewCreatedEvent`가 outbox에 쌓인다** — 3번의 동작(준비 B)을 하되 세 작성 요청을 `.andReturn()`으로 받는다.
- 반환값(reviewCreatedEvents()): 3건. 각 이벤트의 `productId()` = `productA`, `content()` = 보낸 본문.
- 반환값(reviewCreatedEvents()): 각 이벤트의 `reviewId()` = 해당 요청의 응답 본문(andReturn 후)에서 `JsonPath.read(본문, "$.reviewId")`로 꺼낸 값.

**10. 남의 리뷰는 수정할 수 없다** — 준비: 4번과 같은 준비와 수정(준비 B 뒤 `buyer3`이 `R3`을 2점으로 수정). 동작: `buyer1`이 `R3`을 수정.
- 응답(andExpect): 상태 404, `$.code` = `REVIEW_NOT_FOUND`. DB(reviewSnapshotOf(R3)): `rating()`은 2 그대로.

**11. 30일이 지난 리뷰는 수정할 수 없다** — 준비: 4번과 같은 준비와 수정. `setReviewCreatedAt(R2, Instant.now().minus(31, DAYS))`. 동작: `buyer2`가 `R2`를 수정.
- 응답(andExpect): 상태 409, `$.code` = `REVIEW_EDIT_EXPIRED`. DB(reviewSnapshotOf(R2)): `rating()` 4 그대로. DB(reviewStatOf(productA)): `ratingSum()` 11 그대로.

**12. 남의 리뷰는 삭제할 수 없다** — 준비: 준비 B. 동작: `buyer2`가 `R1`을 삭제 시도.
- 응답(andExpect): 상태 404, `$.code` = `REVIEW_NOT_FOUND`. DB(reviewSnapshotOf(R1)): `status()`는 `ACTIVE` 그대로.

**13. 삭제한 리뷰는 목록에서 빠진다** — 준비: 5번의 삭제까지 마친 상태(준비 B 뒤 `buyer1`이 `R1`을 삭제). 동작: `GET /api/products/{productA}/reviews?sort=latest`.
- 응답(andExpect): `$.items.length()` = 2, `$.items[*].id`가 `R2`·`R3`(`containsInAnyOrder`, 순서 무관)이고 `R1`은 없다(`jsonPath("$.items[*].id", not(hasItem(R1)))`).

**14. 삭제한 품목에는 다시 쓸 수 없다** — 준비: 5번의 삭제까지 마친 상태. 동작: `buyer1`이 같은 `lineId`로 4점 리뷰를 다시 작성.
- 응답(andExpect): 상태 409, `$.code` = `REVIEW_ALREADY_EXISTS`. DB(reviewCountOf(productA)): 3. DB(reviewStatOf(productA)): 5번의 값(`reviewCount` 2, `ratingSum` 8, `rating(4)` 2, `rating(5)` 0) 그대로.

**15. 좋아요 취소는 두 번 보내도 0 아래로 내려가지 않는다** — 준비: 준비 B 뒤 `buyer2`가 `R1`에 좋아요를 한 번 누른다. 동작: `buyer2`가 `DELETE /api/reviews/{R1}/likes`를 두 번 보낸다.
- 응답(andExpect): 두 응답 모두 상태 204. DB(reviewSnapshotOf(R1)): `likerIds()`가 비어 있고(`isEmpty()`) `likeCount` = 0.

**16. 목록: 좋아요순** — 준비: 8번과 같은 준비. 동작: `?sort=likes`(size 생략, Authorization 없이).
- 응답(andExpect): 상태 200, `$.items.length()` = 5, `$.items[0..2].id` = `R3`, `R5`, `R1`이고 `$.items[0..2].likeCount` = 3, 2, 1, `$.items[3].id`·`$.items[4].id`는 `R2`·`R4`(순서 불문), `jsonPath("$.nextCursor").value(nullValue())`.

**17. 목록: 리뷰가 없는 상품** — 동작: 리뷰가 없는 상품(`Ids.newId()`)의 `GET /api/products/{그 id}/reviews`(Authorization 없이).
- 응답(andExpect): 상태 200, `jsonPath("$.items").value(hasSize(0))`.

#### `review/application/ReviewConcurrencyTest` (준비 A 사용)

각 작업은 요청을 `.andReturn()`으로 받은 `MvcResult`를 반환한다. 상태 코드는 `Result.value().getResponse().getStatus()`, 본문 값은 `JsonPath.read(Result.value().getResponse().getContentAsString(), "$.code")`처럼 꺼낸다. 같은 작업을 N번 돌릴 때는 `ConcurrencyRunner.run(int threads, Callable<T> task)`, 작업이 서로 다르면 `run(List<Callable<T>> tasks)`(`run(List.of(...))`)를 쓴다. 둘 다 스레드를 동시에 출발시키고 제출 순서대로 `List<Result<T>>`(`Result(T value, Throwable error)`)를 돌려준다.

**1. 같은 품목 동시 작성 2건** — 준비: `buyer1`의 구매확정 품목 하나. 동작: 같은 요청(5점)을 두 스레드가 동시에 보낸다.
- 응답 본문(andReturn 후, 두 스레드가 모두 끝난 뒤): 두 결과의 상태 코드 — 201이 정확히 1개, 409가 정확히 1개.
- 응답 본문(andReturn 후): 409 결과 본문의 `$.code`는 `DUPLICATE_RESOURCE` 또는 `REVIEW_ALREADY_EXISTS` 중 하나.
- 아래 DB·이벤트 확인은 두 스레드가 모두 끝난 뒤에 읽는다. DB(reviewCountOf(productA)): 1.
- DB(reviewSnapshotOf(reviewId)): `status()` = `ACTIVE`(`reviewId`는 201 결과의 응답 본문(andReturn 후)에서 `JsonPath.read(본문, "$.reviewId")`로 꺼낸다).
- DB(reviewStatOf(productA)): `reviewCount` 1, `ratingSum` 5, `rating(5)` 1.
- 반환값(reviewCreatedEvents()): 1건.

**2. 서로 다른 회원 좋아요 동시 100건** — 준비: `buyer1`이 쓴 리뷰 `R1` 하나. 회원 100명(`token("liker" + i + "@test.com")`, i = 1~100)의 토큰을 미리 받아 둔다. 동작: 100명이 동시에 `POST /api/reviews/{R1}/likes`.
- 응답 본문(andReturn 후, 100개 스레드가 모두 끝난 뒤): 100개 결과의 상태 코드가 모두 204.
- DB(reviewSnapshotOf(R1), 100개 스레드가 모두 끝난 뒤): `likerIds().size()` = 100, `likeCount` = 100.

**3. 서로 다른 품목 리뷰 동시 50건** — 준비: 구매자 50명(`signup` + `login`)이 각자 `createConfirmedLine`으로 `productA`의 구매확정 품목을 하나씩 가진다. 별점은 번호 `i`(0~49)에 따라 `i < 20` → 5점, `20 ≤ i < 35` → 4점, `35 ≤ i < 45` → 3점, `i ≥ 45` → 1점. 동작: 50명이 동시에 작성한다.
- 응답 본문(andReturn 후, 50개 스레드가 모두 끝난 뒤): 50개 결과의 상태 코드가 모두 201.
- 아래 DB·이벤트·통계 확인은 50개 스레드가 모두 끝난 뒤에 읽는다. DB(reviewCountOf(productA)): 50. 반환값(reviewCreatedEvents()): 50건.
- DB(reviewStatOf(productA)): `reviewCount` 50, `ratingSum` 195(20×5 + 15×4 + 10×3 + 5×1), `rating(1)` 5, `rating(3)` 10, `rating(4)` 15, `rating(5)` 20.
- 응답(andExpect): `GET /api/products/{productA}/review-stats`(Authorization 없이)의 `$.average` = 3.9(195÷50).

**리뷰 때 물어볼 것**
- unique 위반을 트랜잭션 안에서 잡으면 왜 안 되나? (As-Is SUP-05)
- 통계를 매번 `COUNT`/`AVG`로 계산하지 않고 따로 두는 이유와 대가는?

---

## 5-2. 상품 검색 · 자동완성 (Elasticsearch) (M)

**목표**: 키워드 검색과 자동완성. 상품 변경이 몇 초 안에 반영된다. ES가 죽어도 검색만 실패한다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| Elasticsearch | 검색 전용 저장소. 원본은 Postgres, ES는 이벤트로 갱신하는 **읽기 모델**이다 |
| alias | 인덱스에 붙이는 별칭. 검색·색인은 항상 alias(`products`)로 한다 → 5-3에서 실제 인덱스를 바꿔치기할 수 있다 |
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
- `IntegrationTestSupport`: `@ServiceConnection protected static final ElasticsearchContainer es = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:9.4.8").withEnv("xpack.security.enabled", "false")`(8.x 이후 이미지는 기본으로 보안이 켜져 있어 끈다. `kafka`처럼 `protected`라서 같은 패키지의 `TestFixtures`와 다른 패키지의 하위 테스트가 모두 `es`를 읽는다) → `Startables.deepStart(...)`에 추가
- `application.yaml`: `spring.elasticsearch.connection-timeout: 2s`, `spring.elasticsearch.socket-timeout: 2s`(공통 규칙 X의 ES 타임아웃 2초. Boot 4.1.1 기본값은 1s·30s, 키 이름은 jar 메타데이터로 확인)

**2) search — 모듈 경계** (domain 생략, application + infrastructure, 개발 가이드 §3.3)

- **역할**: search는 상품 원본(Postgres)을 부르지 않는다. product가 내보내는 `product-upserted` 이벤트를 받아 ES에 문서로 복제해 두고, 검색 요청은 그 ES만 읽는다.
- **호출 수단**: 공식 Java 클라이언트 `ElasticsearchClient`를 쓴다(`spring-boot-starter-data-elasticsearch`가 빈으로 만든다). 요청이 ES REST API의 JSON과 1:1이라 아래 요청 모양을 그대로 옮길 수 있고, Spring Data의 `ElasticsearchOperations`는 엔티티 매핑을 전제하는데 이 모듈은 domain을 두지 않는다. 아래는 **ES에 보내야 하는 요청의 모양**으로 적었다. external version·alias 교체·`search_after`·bulk를 클라이언트의 어떤 메서드가 맡는지는 클라이언트 jar에서 확인한다(확인 필요).

| 클래스 | 규격 |
|---|---|
| `search/package-info.java` | 모듈 경계. `@ApplicationModule(allowedDependencies = {"common", "product::api"})`(규칙 Y). `product::api`는 이벤트 record(`ProductUpsertedEvent`)와 5-3의 `findForIndexing`을 쓰기 위한 것이다 |

**3) search.infrastructure**

| 클래스 | 규격 |
|---|---|
| `ProductIndexDefinition` | 인덱스를 만들 때 쓰는 설정·매핑 JSON(`src/main/resources/search/products-index.json`)을 읽어 준다. `ensureIndex`와 5-3 재색인이 같은 JSON을 쓰므로 한 곳에 둔다. JSON 내용: 분석기 `autocomplete`(tokenizer `edge_ngram` min 1 max 20, filter `lowercase`), 필드 `productId keyword`, `shopId keyword`, `name text` + 하위 필드 `name.autocomplete`(분석기 autocomplete, 검색 분석기 standard), `description text`, `category keyword`, `price long`, `status keyword`. JSON 내용을 돌려주는 메서드 하나(이름은 구현이 정한다). `search.infrastructure`에 둔다 |
| `ProductIndexer` | `@Component`. 상품 문서를 ES에 쓰는 유일한 곳. consumer와 5-3 재색인이 같이 쓴다. `void ensureIndex()` — 앱 시작 때 한 번 / `void upsert(ProductUpsertedEvent e, String indexOrAlias)` — 문서 한 건 색인 |
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
- ES가 내려가 있으면 consumer가 실패 → 3번 재시도 → DLT. 복구 후 DLT 재처리 API(`POST /admin/api/dlt/{topic}/{partition}/{offset}/replay`, 6-3 — DLT 레코드 하나를 원래 토픽으로 다시 보낸다)로 재처리하거나 5-3 재색인으로 맞춘다.

**4) search.application**

| 클래스 | 규격 |
|---|---|
| `SearchErrorCode` | 검색 에러 코드. `ErrorCode`를 구현하는 enum. `SEARCH_UNAVAILABLE`(503) — 값·메시지는 위 Z 표. domain을 생략하므로 `search.application`에 둔다(`ProductSearchService`가 던진다) |
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
| `productSearchDocOf(UUID productId)` → `ProductSearchDoc` | ES REST `GET {es 주소}/products/_doc/{productId}`(alias로 읽는다)를 JDK `HttpClient`로 보내 응답 JSON에서 한 번에 읽는다(요청 코드는 테스트에 쓰지 않는다): `found`, `_version`(external version이 그대로 저장된다), `_source.name`·`.description`·`.category`·`.price`·`.status`. `ProductSearchDoc`은 `support/`의 record `(boolean found, Long version, String name, String description, String category, Long price, String status)`이고, 404면 `found` = `false`에 나머지는 `null`이다. ES 주소는 `IntegrationTestSupport.es.getHttpHostAddress()`다(`es`는 `protected static`이고 `TestFixtures`는 같은 `support` 패키지라 그대로 읽는다) | `ProductSearchTest` 4·5·8번, 5-3 `ReindexTest` 2·5번 |

이미 있는 헬퍼와 주입: `RegisterProductService`·`UpdateProductService`(주입), `outboxRelay.relayOnce()`(`int` 반환. `READY` outbox 행을 Kafka로 한 번 발행한다), `Eventually.await(Duration timeout, ThrowingRunnable assertion)`(단언을 100ms마다 다시 실행해 통과하면 끝나고, 시간이 다 되면 마지막 실패를 던진다), `ProductIndexer.upsert`(이 단계에서 만든다). `ProductUpsertedEvent` 필드 순서는 `(productId, shopId, name, description, category, price, status, subscribable, version)`(3-3)이다.
헬퍼를 쓰는 방법은 다음과 같다.
- 응답: `jsonPath`. 검색은 `$.items[i].productId`·`.shopId`·`.name`·`.category`·`.price`와 `$.nextCursor`(마지막 페이지는 `null`). 자동완성은 응답이 배열이라 `$.length()`와 `$[i]`. 에러 코드는 `$.code`.
- 색인 반영 대기: `Eventually.await(Duration.ofSeconds(10), …)`로 검색 결과를 기다린다(기본 refresh 주기 1초로 충분하다. 못 기다리면 `POST /products/_refresh`). "안 나온다"는 확인은 Eventually로 기다린 뒤 한 번 더 확인한다.

**ProductSearchTest 공통 규칙**: ES 문서는 테스트가 끝나도 지워지지 않으므로(DB 초기화 대상이 아니다) 케이스마다 아래에 굵게 적은 **검색어**를 따로 쓰고, 다른 케이스의 상품 이름은 "루틴"으로 시작하지 않게 한다. 검색 요청은 `GET /api/search/products?q=…`(Authorization 없이)이다.

**준비 A**(`ProductSearchTest` 1·2·3·3-1·6·7·8·9번이 쓴다. 4·5번은 쓰지 않는다): 판매자 1명, 가게 1개. 상품은 이름·설명을 정해야 하므로 `TestFixtures.registerProduct`(이름이 `product`로 고정)가 아니라 테스트가 `RegisterProductService.register(sellerId, shopId, new RegisterProductCommand(이름, 설명, ProductCategory.X, 가격, 10, false))`로 만든다(반환값이 상품 ID). 등록 뒤 `outboxRelay.relayOnce()`로 이벤트를 흘린다.

#### `search/application/ProductSearchTest` (MockMvc로 공개 API를 부른다)

**1. 등록 → 검색됨** (검색어 **마그네슘**, **근육이완**) — 준비: 준비 A로 상품 `P` = 이름 `"마그네슘 데일리 정"`, 설명 `"수면용 근육이완 보충제"`, `HEALTH`, 15000원. 동작: `relayOnce()` → Eventually로 `q=마그네슘` 조회.
- 응답(andExpect): 상태 200, `$.items.length()` = 1, `$.items[0].productId` = `P`, `.shopId` = 가게 ID, `.name` = `"마그네슘 데일리 정"`, `.category` = `HEALTH`, `.price` = 15000, `jsonPath("$.nextCursor").value(nullValue())`.
- 위 `q=마그네슘`이 통과한 뒤 `q=근육이완` → 응답(andExpect): `$.items.length()` = 1, `$.items[0].productId` = `P`.

**2. 숨김·단종은 검색에서 빠진다** (검색어 **오메가**) — 준비: 준비 A로 상품 `P` = 이름 `"오메가 쓰리 캡슐"`, 설명 `"혈행 개선 보충제"`, `HEALTH`, 22000원. 아래를 차례로 하고 매번 `relayOnce()` 후 Eventually로 `q=오메가`를 확인한다.
- 등록 직후: 응답(andExpect): `$.items.length()` = 1.
- `updateProductService.changeStatus(sellerId, shopId, P, ProductStatus.HIDDEN)` 후: 응답(andExpect): `jsonPath("$.items").value(hasSize(0))`.
- `changeStatus(…, ON_SALE)` 후: 응답(andExpect): `$.items.length()` = 1, `$.items[0].productId` = `P`.
- `changeStatus(…, DISCONTINUED)` 후: 응답(andExpect): `jsonPath("$.items").value(hasSize(0))`.

**3. 자동완성** (검색어 **루틴**) — 준비: 준비 A로 상품 4개, 모두 `HEALTH`.

| 상품 | 이름 | 상태 |
|---|---|---|
| A | `"루틴 비타민"` | `ON_SALE` |
| B | `"루틴 마스크팩"` | `ON_SALE` |
| C | `"비타민 루틴"` | `ON_SALE` |
| D | `"루틴 오일"` | 등록 후 `changeStatus(…, HIDDEN)` |

동작: `relayOnce()` → Eventually로 `GET /api/search/autocomplete?q=루틴`.
- 응답(andExpect): 상태 200, 응답 본문 배열의 `$.length()` = 2, `$[0]`·`$[1]`이 `"루틴 비타민"`·`"루틴 마스크팩"`(순서 불문). `"비타민 루틴"`(C)과 `"루틴 오일"`(D)은 배열에 없다(`jsonPath("$", not(hasItem("비타민 루틴")))`, `"루틴 오일"`도 같은 식).

**3-1. 자동완성은 최대 10개** (검색어 **젤리맛**) — 준비: 준비 A로 이름 `"젤리맛 01"` ~ `"젤리맛 12"`인 `ON_SALE` 상품 12개. 동작: `relayOnce()` → Eventually로 `GET /api/search/autocomplete?q=젤리맛`. 요청을 `.andReturn()`으로 받는다.
- 응답(andExpect): 상태 200, 응답 본문 배열의 `$.length()` = 10.
- 응답 본문(andReturn 후): `JsonPath.read(본문, "$")`로 꺼낸 `List<String>`의 모든 원소가 `startsWith("젤리맛 ")`.

**4. 순서 역전: 오래된 버전이 늦게 와도 최신 유지** (검색어 **순서역전**) — 준비: 새 `productId` `X`와 임의의 `shopId`(`Ids.newId()`)로 `ProductUpsertedEvent` 두 개를 직접 만든다(`category` = `"ETC"`, `status` = `"ON_SALE"`, `subscribable` = false).
- `v3` = 이름 `"순서역전 버전삼"`, 설명 `"세 번째 내용"`, 가격 3000, `version` 3.
- `v2` = 이름 `"순서역전 버전이"`, 설명 `"두 번째 내용"`, 가격 2000, `version` 2.
- 동작: `productIndexer.upsert(v3, "products")` → `productIndexer.upsert(v2, "products")`.
- 예외: 두 번째 `upsert` 호출이 예외 없이 끝난다(`assertThatCode(...).doesNotThrowAnyException()`).
- ES(productSearchDocOf(X), 두 번째 `upsert`가 끝난 뒤): `version()` = 3.
- 응답(andExpect): 두 번째 `upsert`가 끝난 뒤 Eventually(10초)로 `q=순서역전` → `$.items.length()` = 1, `$.items[0].name` = `"순서역전 버전삼"`.

**5. 같은 이벤트 두 번** (검색어 **중복이벤트**) — 준비: 임의의 `shopId`(`Ids.newId()`)로 `ProductUpsertedEvent` `Y`를 직접 만든다. `Y` = 이름 `"중복이벤트 상품"`, 설명 `"같은 이벤트 검증"`, 가격 1000, `version` 1. 동작: `productIndexer.upsert(Y, "products")`를 두 번 호출.
- 예외: 두 번째 `upsert` 호출도 예외가 없다(`assertThatCode(...).doesNotThrowAnyException()`).
- ES(productSearchDocOf(Y.productId()), 두 번째 `upsert`가 끝난 뒤): `version()` = 1.
- 응답(andExpect): 두 번째 `upsert`가 끝난 뒤 Eventually(10초)로 `q=중복이벤트` → `$.items.length()` = 1.

**6. 관련도 정렬과 페이지** (검색어 **프로폴리스**)
- 준비: 준비 A로 상품 `A` = 이름 `"프로폴리스 스프레이"`, 설명 `"목 건강 케어"` / 상품 `B` = 이름 `"목 케어 스프레이"`, 설명 `"프로폴리스 함유"`(둘 다 `HEALTH`, `ON_SALE`).
- `q=프로폴리스&size=20`(준비 A의 `relayOnce()` 뒤 Eventually(10초) 안에서 `$.items.length()` = 2가 될 때까지 기다린다) — 응답(andExpect): `$.items.length()` = 2, `$.items[0].productId` = `A`, `$.items[1].productId` = `B`, `jsonPath("$.nextCursor").value(nullValue())`.
- `q=프로폴리스&size=1`(`.andReturn()`으로 받는다) — 응답(andExpect): `$.items.length()` = 1, `$.items[0].productId` = `A`, `jsonPath("$.nextCursor").value(notNullValue())`.
- 위 `size=1` 응답 본문(andReturn 후)에서 `JsonPath.read(본문, "$.nextCursor")`로 꺼낸 값을 `cursor=`로 넘겨 `q=프로폴리스&size=1` — 응답(andExpect): `$.items.length()` = 1, `$.items[0].productId` = `B`, `jsonPath("$.nextCursor").value(nullValue())`.

**7. 카테고리 필터** (검색어 **카테고리검증**) — 준비: 준비 A로 `X1` = 이름 `"카테고리검증 영양제"`, `HEALTH` / `X2` = 이름 `"카테고리검증 크림"`, `BEAUTY`(둘 다 `ON_SALE`).
- `q=카테고리검증&category=HEALTH`(준비 A의 `relayOnce()` 뒤 Eventually(10초) 안에서 읽는다. 아래 두 조회는 이 조회가 통과한 뒤 읽는다) — 응답(andExpect): `$.items.length()` = 1, `$.items[0].productId` = `X1`.
- `q=카테고리검증`(`category` 생략) — 응답(andExpect): `$.items.length()` = 2, `$.items[*].productId`가 `X1`·`X2`(`containsInAnyOrder`, 순서 무관).
- `q=카테고리검증&category=FOOD` — 응답(andExpect): `jsonPath("$.items").value(hasSize(0))`.

**8. 단종 상품의 ES 문서는 남는다** (검색어 없음) — 준비: 준비 A로 상품 `P` = 이름 `"단종문서 캡슐"`, 설명 `"혈행 개선 보충제"`, `HEALTH`, 22000원. 동작: `changeStatus(sellerId, shopId, P, ProductStatus.DISCONTINUED)` → `relayOnce()` → Eventually로 `productSearchDocOf(P).status()`가 `DISCONTINUED`가 될 때까지 기다린다.
- ES(productSearchDocOf(P)): `found()` = true, `status()` = `DISCONTINUED`.

**9. 동점은 `productId` 오름차순** (검색어 **동점검증**) — 준비: 준비 A로 이름 `"동점검증 세트"`, 설명 `"동일한 설명"`이 같은 상품 `C1`, `C2`. `lo`·`hi` = `Stream.of(C1.toString(), C2.toString()).sorted().toList()`의 첫째·둘째 원소.
- `q=동점검증&size=1`(준비 A의 `relayOnce()` 뒤 Eventually(10초) 안에서 두 상품이 모두 색인될 때까지 기다린 뒤 `.andReturn()`으로 받는다) — 응답(andExpect): `$.items[0].productId` = `lo`, `jsonPath("$.nextCursor").value(notNullValue())`.
- 그 응답 본문(andReturn 후)에서 `JsonPath.read(본문, "$.nextCursor")`로 꺼낸 값을 `cursor=`로 넘긴 다음 쪽 — 응답(andExpect): `$.items[0].productId` = `hi`, `jsonPath("$.nextCursor").value(nullValue())`.

#### `search/web/SearchRequestTest` (추가)

모두 `GET`, Authorization 없이.

| 요청 | 확인할 것 |
|---|---|
| `/api/search/products?q=` / `q`가 `"가"×51` / `q=테스트&size=0` / `q=테스트&size=51` | 응답(andExpect): 상태 400, `$.code` = `INVALID_REQUEST` |
| `q=테스트&cursor=not-a-cursor!!` / `q=테스트&cursor=YWJj` | 응답(andExpect): 상태 400, `$.code` = `INVALID_REQUEST` |
| `q`가 `"가"×50`, `size=50` | 응답(andExpect): 상태 200 |
| `/api/search/autocomplete?q=루` / `q`가 `"가"×21` | 응답(andExpect): 상태 400, `$.code` = `INVALID_REQUEST` |
| `/api/search/autocomplete?q=루틴` | 응답(andExpect): 상태 200 |

#### `search/web/SearchUnavailableTest`

**ES가 멈춰도 검색만 실패한다** — 준비: 판매자·가게·`registerProduct(sellerId, shopId)`로 상품 `P` 하나. 동작: `es.getDockerClient().pauseContainerCmd(es.getContainerId()).exec()`로 ES 컨테이너를 멈추고, `finally`에서 반드시 `unpauseContainerCmd`로 되돌린다. 아래 네 요청은 모두 ES가 멈춘 동안(`unpause` 전에) 보낸다.
- `GET /api/search/products?q=product` — 응답(andExpect): 상태 503, `$.code` = `SEARCH_UNAVAILABLE`. 반환값(Instant.now() 두 번의 차이): 요청 직전과 응답 직후에 재서 `Duration.between(직전, 직후)`가 10초보다 짧다.
- `GET /api/search/autocomplete?q=pr` — 응답(andExpect): 상태 503, `$.code` = `SEARCH_UNAVAILABLE`.
- `GET /api/products`(DB, Authorization 없이) — 응답(andExpect): 상태 200, `$.items.length()` = 1, `$.items[0].id` = `P`.
- 상품 등록 `POST /api/shops/{shopId}/products`(판매자 토큰, 새 `Idempotency-Key`, 본문은 `RegisterProductRequest`) — 응답(andExpect): 상태 201.

**리뷰 때 물어볼 것**
- 검색 결과와 DB가 잠시 다를 수 있는데 괜찮은가?
- external version이 없다면 이벤트 순서 역전을 어떻게 막았을까?

---

## 5-3. (선택) 무중단 재색인 (S)

> 이 단계는 **선택**이다. 하지 않으면 재색인이 필요할 때 인덱스를 지우고 다시 만들며(그동안 검색 결과가 비어 있음), 그 한계를 회고에 적는다.

**목표**: 전체 재색인 중에도 검색이 멈추지 않는다(As-Is SUP-04).

**새로 등장**: 이중 쓰기 — 재색인하는 동안 consumer가 기존 인덱스(alias)와 새 인덱스 **둘 다**에 쓴다. 전체 적재와 실시간 변경이 겹쳐도 external version 덕분에 최신 버전만 남는다.

**정책**: 재색인은 한 번에 하나(잡 락 이름 `search-reindex`). 재색인 대상 인덱스 이름은 Redis `search:reindex-target`(TTL 2시간)에 둔다.

### 할 일

**구현 순서**: 절 번호 순서대로 만든다. 1~2) 상품 keyset 쿼리(`product.domain` → `product.infrastructure`) → 3~4) `ProductApi.findForIndexing`(`product.api` → `product.application`) → 5) `search.infrastructure`(ES 쓰기 작업 → consumer 이중 쓰기 → 실행기 빈) → 6) `ReindexService.reindex()` → 7) 관리자 API → 테스트.

**1) product.domain**

| 클래스 | 규격 |
|---|---|
| `ProductRepository` | 상품 keyset 쿼리를 추가한다. 상태를 가리지 않는다(단종·숨김 문서도 인덱스에 두고 `status`로 걸러내는 정책, 5-2). 비교는 DB가 하므로 자바 `UUID` 정렬과 달라도 문제없다. 기존 `findPublicFirstPage`·`findPublicNextPage`처럼 첫 페이지와 다음 페이지 쿼리 둘로 나눠 `afterId`에 `null`을 넘기지 않는다. `List<Product> findAllFirstPage(Limit limit)`(`ORDER BY id`) / `List<Product> findAllNextPage(UUID afterId, Limit limit)`(`WHERE id > :afterId ORDER BY id`). `ProductApiImpl.findForIndexing`이 쓴다 |

**2) product.infrastructure**

| 클래스 | 규격 |
|---|---|
| `JpaProductRepository` | 위 keyset 쿼리의 Jpa 구현(JPQL, `Limit`) |

**3) product.api**

| 클래스 | 규격 |
|---|---|
| `ProductApi` | `List<ProductUpsertedEvent> findForIndexing(UUID afterId, int limit)` — 재색인이 상품 전체를 한 번에 읽지 않고 `limit`개씩 읽어 가는 keyset 조회다. 반환 타입은 `product-upserted` 이벤트 record(3-3)이고 현재 상품 상태에서 `ProductEventPublisher.upserted`와 같은 조립 코드로 채운다(`version`은 DB의 `product.version`). 조립 코드는 `ProductEventPublisher`에서 `ProductUpsertedEvent upsertedEventOf(Product)` 메서드로 빼서 `upserted`와 `ProductApiImpl`이 함께 쓴다(둘 다 `product.application`). 이벤트와 같은 필드의 별도 record를 두면 `ProductIndexer.upsert`에 넘길 때마다 변환해야 해서 새로 만들지 않는다. 다음 호출의 `afterId`는 받은 목록의 **마지막 `productId`**, 받은 개수가 `limit`보다 적으면 끝이다. `search` → `product::api` 의존(공통 규칙 Y). `ReindexService`가 쓴다 |

**4) product.application**

| 클래스 | 메서드 |
|---|---|
| `ProductApiImpl` | `@Transactional(readOnly = true) List<ProductUpsertedEvent> findForIndexing(UUID afterId, int limit)` — `ProductApi` 구현. `afterId`가 `null`이면 `findAllFirstPage(Limit.of(limit))`, 아니면 `findAllNextPage(afterId, Limit.of(limit))`를 부르고 각 상품을 `upsertedEventOf`로 바꾼다 |

**5) search.infrastructure**

| 클래스 | 규격 |
|---|---|
| ES 쓰기 작업 | 새 인덱스 생성(`ProductIndexDefinition`의 JSON), bulk 색인(문서마다 external version), alias `products` 원자 교체(`_aliases` 한 요청), 새 인덱스 `refresh`, 인덱스 삭제, alias가 가리키는 현재 인덱스 이름 조회. `ProductIndexer`가 "상품 문서를 ES에 쓰는 유일한 곳"이므로 모두 `ProductIndexer`에 메서드로 더한다(메서드 이름은 구현이 정한다). `ReindexService`가 쓴다 |
| `ProductEventConsumer` (5-2 수정) | 재색인 중 이중 쓰기: 5-2 consumer 순서 2번의 `indexer.upsert(typed.payload(), "products")` 뒤에, `StringRedisTemplate`로 Redis `search:reindex-target`을 읽어 값이 있으면 같은 문서를 `indexer.upsert(payload, target)`으로 그 인덱스에도 쓴다. 두 쓰기 사이에 실패해 같은 메시지가 다시 와도 이미 쓴 쪽은 409로 무시되고 못 쓴 쪽만 반영된다. Redis 의존성을 consumer에 더한다 |
| 실행기 빈 | `Executors.newSingleThreadExecutor()` 빈(`@Async` 대신. MDC traceId는 `JobRunner`가 새로 만든다). 클래스 이름은 문서에 없다 |

**6) search.application**

| 클래스 | 메서드 |
|---|---|
| `ReindexService` | `int reindex()` — `@Transactional` 없음 |

`ReindexService.reindex()` — **누가 부르나**: 아래 `search.web` 절의 API가 `JobRunner`로 부른다. `JobRunner.run`은 `Supplier<Integer>`를 받으므로 `reindex()`는 색인한 문서 수(`int`)를 반환한다.
1. `newIndex = "products-" + 시각` 인덱스를 `ProductIndexDefinition`의 JSON으로 만든다(`ensureIndex`와 같은 매핑). alias는 아직 붙이지 않는다
2. Redis `SET search:reindex-target newIndex EX 7200`(`StringRedisTemplate`의 `opsForValue().set(키, 값, Duration.ofHours(2))`) → 이 순간부터 5-2의 consumer가 새 인덱스에도 쓴다(위 `search.infrastructure` 절의 consumer 이중 쓰기). 적재 중에 들어온 변경이 새 인덱스에서 빠지지 않게 하려는 것이다
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
| `searchIndexStateOf()` → `SearchIndexState` | 한 번 호출로 읽는다(요청 코드는 테스트에 쓰지 않는다). 읽는 곳 4개: ES `GET /_alias/products`(응답 JSON의 **키**들 → `aliasTargets` = alias `products`가 가리키는 인덱스 이름들, alias가 없어 404면 빈 목록), ES `GET /_cat/indices/products-*?format=json`(각 항목의 `index` → `indexNames` = `products-*` 인덱스 전부), ES `GET /products/_count`(`count` → `docCount` = alias로 센 문서 수, alias가 없으면 0), Redis `StringRedisTemplate.opsForValue().get("search:reindex-target")`(`reindexTarget`, 키가 없으면 `null`). `SearchIndexState`는 `support/`의 record `(List<String> aliasTargets, List<String> indexNames, long docCount, String reindexTarget)`. ES 요청은 JDK `HttpClient`로 보내고 주소는 `es.getHttpHostAddress()`다(5-2 `productSearchDocOf`와 같다) | `ReindexTest` 2·3·4·5번 |

이미 있는 헬퍼: 5-2 `productSearchDocOf`, `String adminToken(String email)`(4-4. `signup(email)` → `changeRole(memberId, "ADMIN")` → `login(email)`로 ADMIN 토큰을 돌려준다), `Eventually.await(Duration timeout, ThrowingRunnable assertion)`(통과할 때까지 100ms마다 단언을 다시 실행).

헬퍼를 쓰는 방법은 다음과 같다.
- DB: `jdbcTemplate.queryForObject("SELECT version FROM product.product WHERE id = ?", Long.class, id)`(이 한 곳뿐이라 헬퍼 없이 쓴다).
- 검색: `GET /api/search/products?q=재색인상품&size=1`(Authorization 없이).

**준비 A**(4번을 뺀 모든 케이스가 쓴다): 판매자 1명, 가게 1개. `RegisterProductService.register`로 상품 1,000개를 만든다: 이름 `"재색인상품 " + i`(i = 1~1000), 설명 `"재색인 검증용 상품"`, `ETC`, 1000원, 재고 1. 첫 상품을 `P1`이라 부른다. 옛 인덱스를 채운다: `productApi.findForIndexing(null, 1_000)`로 받은 이벤트 1,000건을 `productIndexer.upsert(e, "products")`로 색인하고 `POST /products/_refresh`. `oldIndex` = `searchIndexStateOf().aliasTargets().get(0)`(원소가 하나뿐이다). 재색인은 `reindexService.reindex()`를 별도 스레드(`ExecutorService`)에서 부르고 `Future`로 결과를 받는다(반환값 = 색인한 문서 수 1000).

#### `search/application/ReindexTest`

**1. 재색인 도중에도 검색이 끊기지 않는다** — 동작: 스레드 A가 `reindex()`를 시작한다. 시작 직후부터 A가 끝날 때까지 스레드 B가 검색 요청을 10ms 간격으로 연달아 보내며(`.andReturn()`으로 받는다) 응답마다 상태 코드와 본문의 `$.items.length()`를 기록한다.
- 응답 본문(andReturn 후): B가 기록한 모든 응답의 상태가 200.
- 응답 본문(andReturn 후): B가 기록한 모든 응답에서 `JsonPath.read(본문, "$.items.length()")` = 1.
- 반환값(B 스레드 기록 목록): 응답 목록의 `size()` ≥ 5.

**2. 재색인 도중 수정한 상품은 새 인덱스에 수정 내용으로 남는다** — 동작: ① 스레드 A가 `reindex()`를 시작한다. ② 테스트 스레드가 `Eventually.await(Duration.ofSeconds(5), …)`로 `searchIndexStateOf().reindexTarget()`이 `isNotNull()`일 때까지 기다린다. ③ 값이 보이는 즉시 `updateProductService.update(sellerId, shopId, P1, new UpdateProductCommand("재색인상품 수정됨", null, null, null, null))` → `outboxRelay.relayOnce()`. ④ A가 끝날 때까지 기다린다.
- Redis(searchIndexStateOf().reindexTarget()): ②에서 5초 안에 값이 생긴다.
- 반환값(A의 Future.isDone()): ③ 시점에 false다.
- DB(JDBC): ③의 `update` 뒤(④ 전)에 위 쿼리로 `P1`의 `version`을 읽어 둔다(다음 확인의 기준 값).
- ES(productSearchDocOf(P1)): ④에서 A가 끝난 뒤 Eventually(10초)로 `name()` = `"재색인상품 수정됨"`, `version()` = 위 DB 값.

**3. 재색인이 실패하면 아무것도 바뀌지 않는다** (추가) — 준비: 준비 A + `@MockitoSpyBean ProductApi`에 `doThrow(new RuntimeException("test")).when(productApi).findForIndexing(any(), anyInt())`. 동작: `reindex()`를 `try/catch`로 감싸 호출한다(밖으로 예외가 나오는지는 문서에 없다. 확인 필요).
- ES(searchIndexStateOf(), `reindex()` 호출이 끝난 직후): `aliasTargets()` = [`oldIndex`] 그대로 / `indexNames()` = [`oldIndex`].
- Redis(searchIndexStateOf().reindexTarget(), `reindex()` 호출이 끝난 직후): `null`(`isNull()`).
- 응답(andExpect): `reindex()` 호출이 끝난 뒤 검색 `q=재색인상품&size=1` 상태 200, `$.items.length()` = 1.

**4. 재색인 API 권한** (추가) — 준비: 준비 A는 쓰지 않고 판매자 1명·가게 1개에 상품 1개만 등록한다. 일반 회원 토큰(`token("user@test.com")`), ADMIN 토큰(`adminToken("admin@test.com")`, 4-4). ADMIN 호출 전 `searchIndexStateOf().aliasTargets()`를 `before`로 적어 둔다. 동작: `POST /admin/api/search/reindex`.
- ADMIN 토큰 — 응답(andExpect): 상태 202. ES(searchIndexStateOf().aliasTargets()): 응답 뒤 `Eventually.await(Duration.ofSeconds(30), …)`로 `before`와 달라질 때까지 기다린 뒤 끝낸다.
- 일반 회원 토큰 — 응답(andExpect): 상태 403, `$.code` = `FORBIDDEN`.
- 토큰 없음 — 응답(andExpect): 상태 401, `$.code` = `UNAUTHORIZED`.

**5. 재색인이 끝나면 alias가 새 인덱스를 가리키고 정리된다** — 동작: 준비 A 뒤 `reindex()`를 호출하고 끝나기를 기다린다.
- 반환값: `reindex()` = 1000.
- ES(searchIndexStateOf()): `aliasTargets()`가 정확히 1개이고 그 이름이 `oldIndex`와 다르며 `products-\d{14}` 모양 / `indexNames()`에 `oldIndex`가 없다(`doesNotContain(oldIndex)`) / `docCount()` = 1000.
- Redis(searchIndexStateOf().reindexTarget()): `null`(`isNull()`).
- ES(productSearchDocOf(P1)): `found()` = true.

**리뷰 때 물어볼 것**: alias 없이 같은 인덱스를 지우고 다시 만들면 무엇이 문제인가?

---

## 5-4. 1:1 문의 (S)

**목표**: 회원 문의, 관리자 답변.

**정책 (이 단계에서 정함)**: 제재된 회원은 로그인부터 막히므로(1-4) 문의도 쓸 수 없다. 제재 회원 문의 허용은 추가 항목으로 둔다(FR-MEM-07 "문의 외 기능 사용 불가"의 문의 예외는 이번 범위에서 뺀다).

### 할 일

**구현 순서**: 절 번호 순서대로 만든다. 1) 마이그레이션 → 2) `member.domain` → 3) `member.infrastructure` → 4) `member.application` → 5) `member.web` → 테스트.

새 모듈이 아니라 기존 member 모듈(`member.domain`·`member.application`·`member.web`)에 더한다. Part 5 모듈 의존 표(규칙 Y)에 member가 없는 이유다.

**1) 마이그레이션** — `member.inquiry`: `id`, `member_id fk_inquiry_member`, `title varchar(100) X`, `content text X`, `status varchar(20) X ck IN ('OPEN','ANSWERED')`, `version`, `created_at`, `updated_at`, 인덱스 `(member_id, created_at DESC, id DESC)`, `(status, created_at)` / `member.inquiry_answer`: `id`, `inquiry_id uk_inquiry_answer_inquiry fk`, `admin_id uuid X`, `content text X`, `created_at`

**2) member.domain**

| 클래스 | 규격 |
|---|---|
| `InquiryStatus` | `OPEN`, `ANSWERED`. `Inquiry`가 쓴다 |
| `Inquiry` | entity. 위 `member.inquiry` 컬럼과 1:1(`@Version` 포함). `static Inquiry write(UUID memberId, String title, String content)` → `id = Ids.newId()`, `status = OPEN`. `InquiryAnswer answer(UUID adminId, String content)` → 상태를 `OPEN → ANSWERED`로 바꾸고 `InquiryAnswer.write(id, adminId, content)`로 만든 답변을 반환한다(`InquiryStatus`에 전이표 `Map<InquiryStatus, Set<InquiryStatus>>`(`OPEN`만 `ANSWERED`로 갈 수 있다)와 `transitTo(to)`를 두고 `answer`가 `status.transitTo(ANSWERED)`를 부른다. 허용되지 않은 전이는 `BusinessException(INVALID_STATE_TRANSITION)`이라 이미 `ANSWERED`면 409. Part 1 규칙 C). `InquiryService`가 쓴다 |
| `InquiryAnswer` | entity. 위 `member.inquiry_answer`와 1:1(수정이 없어 `BaseTimeEntity` 없이 `@CreatedDate Instant createdAt`만 둔다, Part 1 규칙 B). `static InquiryAnswer write(UUID inquiryId, UUID adminId, String content)` → `id = Ids.newId()`. `Inquiry`는 답변을 필드로 들지 않고(`@OneToMany`·`@OneToOne` 없이 UUID로만 참조) `answer`가 만들어 반환하며, `InquiryService.answer`가 `InquiryAnswerRepository.save`로 저장한다. 같은 문의에 동시에 두 답변이 오면 `uk_inquiry_answer_inquiry` 위반이 최후 방어선이다(규칙 D). `answer`가 쓴다 |
| `MemberErrorCode` | 기존 enum에 `INQUIRY_NOT_FOUND`를 추가한다(값·메시지는 위 Z 표). `InquiryService`가 던진다 |
| `InquiryRepository` | `Optional<Inquiry> findByIdAndMemberId(UUID id, UUID memberId)`, 본인 목록 keyset(`member_id`, `created_at desc, id desc`, 첫·다음 페이지), 관리자 목록 keyset(`status`, `created_at`). 이름·인자는 권장안으로 `OrderRepository.findByMemberFirstPage(UUID memberId, Limit limit)`·`findByMemberNextPage(UUID memberId, Instant cursorCreatedAt, UUID cursorId, Limit limit)`와 같은 모양이다: `findByMemberFirstPage`·`findByMemberNextPage`(최신순), 관리자용 `findByStatusFirstPage(InquiryStatus status, Limit limit)`·`findByStatusNextPage(InquiryStatus status, Instant cursorCreatedAt, UUID cursorId, Limit limit)`(오래된 순). 인덱스는 위 마이그레이션. `InquiryService`가 쓴다 |
| `InquiryAnswerRepository` | `InquiryAnswer save(InquiryAnswer answer)`, `Optional<InquiryAnswer> findByInquiryId(UUID inquiryId)`(상세 조회가 답변을 읽는다). `InquiryService`가 쓴다 |

**3) member.infrastructure**

| 클래스 | 규격 |
|---|---|
| `JpaInquiryRepository`·`JpaInquiryAnswerRepository` | `InquiryRepository`·`InquiryAnswerRepository`의 Jpa 구현 |

**4) member.application**

| 클래스 | 규격 |
|---|---|
| `InquiryResult` | 목록 항목. `record (UUID id, String title, InquiryStatus status, Instant createdAt)`. `InquiryService`의 반환이다 |
| `InquiryDetailResult` | 상세. `record (UUID id, String title, String content, InquiryStatus status, Instant createdAt, Answer answer)`. 중첩 `record Answer(String content, Instant createdAt)`이고 답변이 없으면 `answer`는 `null`이다(`adminId`는 내려주지 않는다). `InquiryService`의 반환이다 |

| 클래스 | 메서드 |
|---|---|
| `InquiryService` | 메서드 이름은 권장안: `UUID write(UUID memberId, ...)`(→ `inquiryId`) / `CursorPage<InquiryResult> getMine(UUID memberId, String cursor, int size)` / `InquiryDetailResult getMine(UUID memberId, UUID inquiryId)`(답변 포함. `findByIdAndMemberId`, 남의 문의·없는 문의 모두 404 `INQUIRY_NOT_FOUND`) / `CursorPage<InquiryResult> getOpen(String status, String cursor, int size)`(관리자) / `@Transactional void answer(UUID adminId, UUID inquiryId, String content)`(문의가 없으면 404 `INQUIRY_NOT_FOUND`. `inquiry.answer(adminId, content)`가 반환한 `InquiryAnswer`를 `InquiryAnswerRepository.save`로 저장한다) |

- 목록 정렬은 인덱스 `(member_id, created_at DESC, id DESC)`·`(status, created_at)`에 맞춘다. 본인 목록은 최신순(`ORDER BY created_at DESC, id DESC`). 관리자 목록은 오래된 문의가 먼저(`ORDER BY created_at, id`)이고 다음 페이지 조건은 `created_at > :cursorCreatedAt OR (created_at = :cursorCreatedAt AND id > :cursorId)`다. 둘 다 `Limit.of(size + 1)`로 읽어 `size`보다 많이 받으면 `size`번째 항목의 `new Cursor(createdAt, id).encode()`를 `nextCursor`로 하는 커서 페이징이다

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
- 응답: `jsonPath`. 에러 코드는 `$.code`, 목록은 `$.items.length()`·`$.items[i].…`·`$.nextCursor`. 목록·상세의 항목 필드는 5-4 `member.application` 절의 `InquiryResult`·`InquiryDetailResult`와 같다(`id`·`title`·`status`·`createdAt`, 상세는 `content`와 `answer.content`·`answer.createdAt`).

**준비 A**(각 케이스는 필요한 계정만 쓴다): 회원 `user1@test.com`, `user2@test.com`(`signup` + `login`으로 ID와 토큰). 관리자 `admin@test.com`: `adminId = signup("admin@test.com")` → `changeRole(adminId, "ADMIN")` → `login`으로 토큰을 다시 받는다(4-4의 `adminToken(email)`은 같은 순서를 밟지만 가입한 회원 ID를 돌려주지 않아 `admin_id` 확인에 쓸 수 없다). 문의 작성 요청은 `POST /api/inquiries`에 `Authorization: Bearer {user1 토큰}`, 본문 `{"title":"배송이 늦어요","content":"주문한 상품이 아직 도착하지 않았습니다."}`다. 응답 `$.inquiryId`로 문의 ID(`Q1`)를 받는다. 답변 요청은 `POST /admin/api/inquiries/{id}/answer`에 `Authorization: Bearer {admin 토큰}`, 본문 `{"content":"확인 후 오늘 중 안내드리겠습니다."}`다.

#### `member/web/InquiryTest`

**1. 문의 작성** — 동작: `user1`이 작성 요청.
- 응답(andExpect): 상태 201, `jsonPath("$.inquiryId").isNotEmpty()`.
- DB(inquiriesOf(user1)): 1개이고 `memberId()` = `user1`, `title()` = `"배송이 늦어요"`, `content()` = 보낸 본문, `status()` = `OPEN`.

**2. 남의 문의는 보이지 않는다** — 준비: `user1`이 `Q1`을 작성. 동작·확인:
- `user2` 토큰으로 `GET /api/inquiries/{Q1}` → 응답(andExpect): 상태 404, `$.code` = `INQUIRY_NOT_FOUND`.
- `user2` 토큰으로 `GET /api/inquiries/{Ids.newId()}` → 응답(andExpect): 상태 404, `$.code` = `INQUIRY_NOT_FOUND`.
- `user2` 토큰으로 `GET /api/inquiries` → 응답(andExpect): 상태 200, `jsonPath("$.items").value(hasSize(0))`, `jsonPath("$.nextCursor").value(nullValue())`.
- 본인: `user1` 토큰으로 `GET /api/inquiries/{Q1}` → 응답(andExpect): 상태 200, `$.title` = `"배송이 늦어요"` / `GET /api/inquiries` → 응답(andExpect): 상태 200, `$.items.length()` = 1, `$.items[0].id` = `Q1`.

**3. 답변하면 ANSWERED** — 준비: `user1`이 `Q1`을 작성. 동작: `admin`이 답변 요청.
- 응답(andExpect): 상태 204.
- DB(inquiryOf(Q1)): `status()` = `ANSWERED`, `answers()` 1개(`adminId()` = `adminId`, `content()` = `"확인 후 오늘 중 안내드리겠습니다."`).
- 응답(andExpect): `user1` 토큰으로 `GET /api/inquiries/{Q1}` → 상태 200, `$.status` = `ANSWERED`, `$.answer.content`가 위 `content`다.

**4. 관리자 목록은 상태로 거른다** (추가) — 준비: `user1`이 문의 3개(`Q1`, `Q2`, `Q3`)를 작성하고 `admin`이 `Q2`에 답변한다. 동작: `admin` 토큰으로 `GET /admin/api/inquiries?status=OPEN`.
- 응답(andExpect): 상태 200, `$.items.length()` = 2, `$.items[*].id`의 집합이 `{Q1, Q3}`(`containsInAnyOrder`), `jsonPath("$.nextCursor").value(nullValue())`.
- `?status=ANSWERED` → 응답(andExpect): `$.items.length()` = 1, `$.items[0].id` = `Q2`.

**5. 문의 입력 검증** (추가) — 준비: `user1`이 1번의 문의 하나를 작성해 둔다. 동작: `title` 빈 문자열 / `title` `"가"×101` / `content` 빈 문자열 / `content` `"가"×2001`로 각각 요청.
- 응답(andExpect): 모두 상태 400, `$.code` = `INVALID_REQUEST`. DB(inquiriesOf(user1)): `.size()`는 1 그대로.
- `title` `"가"×100`·`content` `"가"×2000` → 응답(andExpect): 상태 201.

**6. 관리자 API는 일반 회원에게 막힌다** (추가) — 준비: `user1`이 `Q1`을 작성. 동작: `user1` 토큰으로 `GET /admin/api/inquiries?status=OPEN`과 `POST /admin/api/inquiries/{Q1}/answer`.
- 응답(andExpect): 두 요청 모두 상태 403, `$.code` = `FORBIDDEN`.

**7. 두 번째 답변은 409** — 준비: 3번의 동작까지(`user1`이 `Q1` 작성, `admin`이 답변). 동작: `admin`이 본문 `{"content":"추가 안내입니다."}`로 같은 문의에 다시 답변.
- 응답(andExpect): 상태 409, `$.code` = `INVALID_STATE_TRANSITION`.
- DB(inquiryOf(Q1)): `answers()`는 여전히 1개이고 `content()`는 첫 답변 그대로, `status()` = `ANSWERED`.

**8. 없는 문의에 답변하면 404** (추가) — 동작: `admin`이 없는 ID(`Ids.newId()`)에 답변.
- 응답(andExpect): 상태 404, `$.code` = `INQUIRY_NOT_FOUND`.

**9. 답변 입력 검증** (추가) — 준비: `user1`이 `Q1`을 작성. 동작: `admin`이 `content` 빈 문자열 또는 `"가"×2001`로 답변.
- 응답(andExpect): 상태 400, `$.code` = `INVALID_REQUEST`.

**추가 항목**
- 제재 회원도 문의 쓰기: 로그인은 허용하고 토큰에 `st: "BANNED"`를 넣어, 필터가 허용 목록(`/api/inquiries/**`, `GET /api/members/me`, 로그아웃·갱신) 외 요청을 403 `MEMBER_BANNED`로 막는다. 1-4·4-4의 "제재 → 로그인 403" 테스트가 "로그인 200 + 체크아웃 403"으로 바뀐다

**리뷰 때 물어볼 것**: 문의와 답변을 한 테이블이 아니라 둘로 나눈 이유는?

---

## Part 5 완료
- [ ] `v0.5.0` 릴리스, Part 6 문서 다듬기
