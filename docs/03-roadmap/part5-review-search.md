# Part 5. 리뷰 · 검색 · 문의

> 버전 0.39 · 2026-11-03 · 5-3에 답변 전 문의 수정(`PATCH /api/inquiries/{id}`)·삭제(`DELETE /api/inquiries/{id}`)와 에러 코드 `INQUIRY_ALREADY_ANSWERED`를 더했다. 삭제와 답변이 동시에 오면 `answer`가 문의 행을 잠가서, 삭제가 먼저 끝난 뒤 답변 INSERT가 FK 위반 500이 되지 않게 한다
> 0.38 · 2026-11-03 · 자동완성을 관련도순으로, 점수가 같으면 이름 오름차순으로 정렬하게 했다. 동점인 이름의 순서가 색인 순서에 따라 달라졌다
> 0.37 · 2026-11-03 · 검색 분석기가 감탄사와 일반 부사를 버리지 않게 했다. nori가 `마스크팩 세트`의 `마스크`·`팩`을 감탄사·부사로 판단해 버려서 `마스크팩`으로 찾지 못했다. 완료 확인에 케이스를 더했다
> 0.36 · 2026-11-03 · 자동완성이 상품명과 검색어의 공백을 무시하고 맞추게 했다. `허브티`로도 `허브 티백`이 나온다. 분석기에 `remove_whitespace` 문자 필터를 더하고 완료 확인에 케이스를 더했다
> 0.35 · 2026-11-03 · 자동완성이 서로 다른 상품명을 최대 10개 돌려주도록 `collapse`를 더했다. 매핑에 하위 필드 `name.keyword`를 추가하고 완료 확인에 케이스를 더했다
> 0.34 · 2026-11-02 · 자동완성 `min_gram`을 2로 바꿨다. 자동완성이 2자 이상일 때만이라 한 글자 조각이 필요 없고, 공백을 더한 `" 브"`처럼 검증을 지나 trim으로 한 글자가 된 검색어는 0건이 된다
> 0.33 · 2026-11-02 · 5-3 무중단 재색인을 로드맵에서 빼서 [서브 SUB-7](../04-sub/search-reindex.md)로 옮겼다. `ProductApi.findForIndexing`도 함께 옮겨 갔다. 1:1 문의를 5-4에서 5-3으로 당겼다(아래 이력의 5-3은 무중단 재색인, 5-4는 문의다)
> 0.32 · 2026-11-02 · 5-2에 nori를 넣어 ES 이미지를 `docker/elasticsearch/Dockerfile`로 빌드하고, 자동완성 검색어를 나누지 않고 한 덩어리로 맞추게 했다. ES 응답 타임아웃 설정, `/actuator/health`에서 ES 제외, 운영 compose, 기동 때 ES가 꺼져 있을 때의 처리를 정했다
> 0.31 · 2026-11-02 · 5-1 `MyReviewResponse`에서 `updatedAt`을 뺐다(수정 응답을 만들 때 감사 필드가 아직 채워지기 전이라 수정 전 값이 나가고, 화면이 쓰지 않는다).
> 0.30 · 2026-11-01 · 5-1이 `review.review-created.v1`을 발행하지 않게 했다(소비자가 없다). `review.api`·`ReviewTopicConfig`·`write`의 outbox 발행·완료 확인의 이벤트 케이스를 뺐다. SUB-6 추가 항목(리뷰 임베딩)에서 발행을 더한다. `Review.likeCount`를 `updatable = false`로 정해 수정·삭제가 동시에 들어온 좋아요를 덮어쓰지 않게 했다. 리뷰 작성 요청에서 `content`가 없으면 500이 아니라 400 `INVALID_REQUEST`다
> 0.29 · 2026-11-01 · 프론트 빈칸 G18~G23 반영: 5-1에 내 리뷰 조회·`likedByMe`를 더하고 수정·삭제·좋아요·통계·정렬의 미정 동작을 정했다. 5-2 검색 결과를 상품 요약 모양으로 바꿔 ES는 순서만 정하게 했고([ADR-013](../adr/ADR-013-search-hydration.md)), 5-4 문의 응답 필드와 관리자 목록을 정하고 답변 알림을 추가 항목으로 뒀다
> 0.28 · 2026-11-01 · 5-4 문의 목록 정렬(본인 최신순·관리자 오래된 순)과 상세 응답의 답변 필드(`answer.content`·`answer.createdAt`)를 확정했다
> 0.27 · 2026-10-31 · SUB-5(취향 추천)·SUB-6(월간 리뷰 요약)를 로드맵에서 빼서 [서브](../04-sub/README.md)로 옮겼다. 5-1 평점 통계 응답에서 `latestSummary`를 빼고 SUB-6이 더하게 했다
> 0.26 · 2026-10-27
> 0.10 · 2026-10-16 · 쓰지 않는 요소를 걷어냈다. 5-1 `ConfirmedLineInfo`의 `shopId`·`confirmedAt`을 뺐다. 반환값을 아무도 읽지 않는 `ReviewStatRepository.add`·`remove`·`change`, `ReviewRepository.increaseLikeCount`·`decreaseLikeCount`의 "반영 행 수"를 "없음"으로 바꿨다. 5-2 ES 문서의 `updatedAt` 필드를 매핑과 `upsert` 본문에서 뺐다. 5-3 `ProductIndexSnapshot`을 없애고 `findForIndexing`이 `List<ProductUpsertedEvent>`를 돌려주게 했다. SUB-5 `recommendation/api/package-info.java`를 뺐다
> 0.5 · 2026-10-09 · ES 이미지 `9.4.8`(Spring Data Elasticsearch: Boot BOM 6.1.1, 클라이언트 9.4.5), Spring AI `2.0.1`(Boot 4.1.1 스타터에 의존)과 OpenAI 설정 키를 확정했다. Spring AI 2.0의 OpenAI 클라이언트가 자체 재시도(기본 3회)를 하므로 테스트의 호출 횟수에 반영한다. pgvector `avg` 집계를 확인했다. 1-10 규칙에 따라 추천 응답의 `thumbnailKey`를 `thumbnailUrl`로 바꿨다
> 0.4 · 2026-10-02 · 5-3 무중단 재색인을 선택 단계로 하고, 리뷰 임베딩을 제거했다(요약은 단순 선택). 제재 회원 문의 허용은 제안으로, 리뷰 중복은 사전 조회로 했다

> **끝나면**: 구매확정한 상품에 리뷰를 쓰고, 키워드로 상품을 검색하고, 1:1 문의를 남긴다.
> **인프라 추가**: **Elasticsearch**
> 검색 모듈은 **이벤트로 받은 문서로 검색 순서를 정하고**, 결과 항목은 상품 조회(`ProductApi`)로 채운다([ADR-013](../adr/ADR-013-search-hydration.md)). Stage 3에서 가장 먼저 분리될 대상이다.
> 시간이 부족하면 5-1(리뷰), 5-2(검색)만 하고 나머지는 생략할 수 있다. 무중단 재색인은 [서브 SUB-7](../04-sub/search-reindex.md)에 있다.
> **릴리스**: `v0.5.0`

| 단계 | 제목 | 크기 |
|---|---|---|
| 5-1 | 리뷰 · 평점 통계 · 좋아요 | M |
| 5-2 | 상품 검색 · 자동완성 (Elasticsearch) | M |
| 5-3 | 1:1 문의 | S |

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
| `INQUIRY_NOT_FOUND` | 404 | 문의를 찾을 수 없습니다. | `MemberErrorCode` | 5-3 |
| `INQUIRY_ALREADY_ANSWERED` | 409 | 답변이 등록된 문의는 수정하거나 삭제할 수 없습니다. | `MemberErrorCode` | 5-3 |

---

## 5-1. 리뷰 · 평점 통계 · 좋아요 (M)

**목표**: 구매확정된 품목당 리뷰 1개. 상품별 평점 분포와 좋아요.

**정책 (이 단계에서 정함)**
- 별점 1\~5, 본문 10\~1,000자. 품목당 1개(삭제해도 같은 품목에 다시 쓸 수 없다, POL-14).
- 작성 후 30일 안에만 수정 가능. `createdAt + 30일` 그 시각까지 수정할 수 있고, 내 리뷰 조회의 `editableUntil`이 이 시각이다. 수정은 별점과 본문을 함께 보낸다.
- 삭제는 soft delete(DELETED)이고 통계에서 빠진다. 지운 리뷰는 없는 리뷰와 같다: 수정·삭제·좋아요·좋아요 취소 모두 404 `REVIEW_NOT_FOUND`다. 다시 지워도 통계가 두 번 빠지지 않는다.
- 상품 상세 API는 평점을 포함하지 않는다. 평점은 `GET /api/products/{id}/review-stats`로 따로 조회한다(product가 review를 참조하지 않게).
- 평점 통계: 평균은 소수 1자리 반올림(`HALF_UP`). 리뷰가 없거나 모두 지워졌으면 200 `reviewCount` 0, `average` `null`, `distribution`의 `1`~`5`가 모두 0이다.
- 리뷰 목록 정렬: `sort`가 없으면 최신순(커서). 좋아요순은 `size`와 상관없이 상위 50개이고 커서가 없다(좋아요 수는 계속 바뀌어 keyset 기준으로 쓸 수 없다). 같은 좋아요 수는 최신순(`created_at DESC, id DESC`)이다. `latest`·`likes` 밖의 값은 400 `INVALID_REQUEST`다.
- 리뷰 목록의 `likedByMe`는 요청자가 그 리뷰에 좋아요를 눌렀는지다. 공개 API라 토큰이 없거나 유효하지 않으면 모두 `false`다.
- 작성자는 구매확정 품목 ID로 자기 리뷰를 찾는다(`GET /api/reviews/mine`). 공개 목록에는 작성자를 내려주지 않으므로 수정·삭제할 리뷰 ID와 지금 별점·본문을 여기서 얻는다.

### 할 일

**구현 순서**: 절 번호 순서대로 만든다. 1) 마이그레이션 → 2~4) 작성 자격 확인(`order.domain` → `order.api` → `order.application`) → 5) `review.domain` → 6) 모듈 경계 → 7) `review.infrastructure` → 8) `review.application` → 9) `review.web` → 10) `common.security` → 테스트 준비 → 테스트.

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

- 인덱스 `idx_review_product_id_created_at (product_id, created_at DESC, id DESC) WHERE status = 'ACTIVE'`, `idx_review_product_id_like_count (product_id, like_count DESC, created_at DESC, id DESC) WHERE status = 'ACTIVE'`(좋아요순의 동점 정렬까지 인덱스로 읽는다)
- `review.review_like`: `review_id uuid`, `member_id uuid`, `created_at`, `pk_review_like (review_id, member_id)`
- `review.product_review_stat`: `product_id uuid pk_product_review_stat`, `review_count int`, `rating_sum bigint`, `r1`\~`r5 int`(DEFAULT 0), `updated_at`. `review_count`·`rating_sum`·`r1`\~`r5`는 모두 `>= 0` CHECK다(`remove`가 잘못 두 번 빼도 음수로 저장되지 않는다)

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
- 통계(`product_review_stat`)와 `like_count`는 **읽고 더해서 저장하지 않고 SQL 한 문장으로 증감**한다. 동시 요청이 같은 행을 읽고 각자 +1을 저장하면 하나가 사라지기 때문이다(`ReviewConcurrencyTest`가 이것을 확인한다). 엔티티 UPDATE도 같은 문제를 만든다: `edit`·`delete`는 더티 체킹으로 저장하는데 그 UPDATE가 모든 컬럼을 쓰므로, 읽은 뒤 들어온 좋아요를 읽을 때의 `like_count`로 덮어쓴다(native 증감은 `version`을 올리지 않아 낙관적 락도 못 잡는다). 그래서 `Review.likeCount`는 `updatable = false`로 두어 엔티티 UPDATE에서 뺀다. 이 값은 `increaseLikeCount`·`decreaseLikeCount`로만 바뀌고, 자바 코드에서 필드를 바꿔도 저장되지 않는다.

| 클래스 | 규격 |
|---|---|
| `ReviewStatus` | `ACTIVE`, `DELETED` |
| `Review` | `@Entity @Table(name = "review", schema = "review")`, `extends BaseTimeEntity`, 필드는 위 `review.review` 컬럼과 1:1(`@Version Long version` 포함). `likeCount`는 `@Column(name = "like_count", nullable = false, updatable = false)`다. 메서드(`write`·`edit`·`delete`)는 아래. `ReviewService`가 쓴다 |
| `ReviewErrorCode` | `ErrorCode`를 구현하는 enum. `REVIEW_ALREADY_EXISTS`(409), `REVIEW_NOT_FOUND`(404), `REVIEW_EDIT_EXPIRED`(409) — 값·메시지는 위 Z 표. `ReviewService`와 `Review.edit`이 쓴다 |
| `ReviewRepository` | 메서드는 아래 |
| `ReviewLikeRepository` | 인터페이스. 구현은 `review.infrastructure`의 `JdbcReviewLikeRepository`(`NamedParameterJdbcTemplate`). 메서드는 아래 |
| `ReviewStatRepository` | 인터페이스. 구현은 `JdbcReviewStatRepository`(`NamedParameterJdbcTemplate`). 메서드는 아래 |
| `ReviewStatRow` | `record (int reviewCount, long ratingSum, int r1, int r2, int r3, int r4, int r5)`. `ReviewStatRepository`의 읽기 결과 |

- `review_like`·`product_review_stat`은 엔티티 없이 `NamedParameterJdbcTemplate`으로 읽고 쓴다. 두 테이블은 SQL 한 문장 증감과 행 단위 읽기만 하고 엔티티로 읽어 바꾸는 경로가 없으며, `review_like`의 복합 PK를 매핑할 이유도 없기 때문이다. 통계 SQL은 같은 별점 값이 칸마다 여러 번 나오고, 좋아요 조회의 `IN (:reviewIds)`는 컬렉션을 펼쳐야 해서 `?` 대신 이름 파라미터로 넘긴다. 호출한 `@Transactional`의 같은 커넥션을 쓴다(`OutboxPublisher`도 같은 방식)

`Review` 메서드
- `static Review write(ConfirmedLineInfo line, UUID memberId, int rating, String content)` — 새 리뷰를 만든다. `id = Ids.newId()`, `ACTIVE`, `likeCount 0`. 별점·본문 불변식 위반은 `IllegalArgumentException`(요청 DTO가 먼저 막지만 도메인도 지킨다, 규칙 B)
- `Instant editableUntil()` — `createdAt.plus(30, DAYS)`. 수정할 수 있는 마지막 시각이다. `edit`과 내 리뷰 조회 응답이 쓴다
- `int edit(int rating, String content, Instant now)` — 별점·본문을 바꾼다. `now`가 `editableUntil()`보다 뒤면(`isAfter`) `REVIEW_EDIT_EXPIRED`라 그 시각 자체는 수정할 수 있다. **바꾸기 전 별점**을 반환한다(서비스가 통계의 옛 칸을 줄이는 데 쓴다)
- `void delete()` — `status`를 `DELETED`로(soft delete)

`ReviewRepository` 메서드
- `Review save(Review review)` — `ReviewService.write`가 새 리뷰를 저장한다(`JpaRepository.save`가 구현한다. 도메인 인터페이스에 선언해야 서비스가 쓸 수 있다)
- `void increaseLikeCount(UUID id)` / `void decreaseLikeCount(UUID id)` (`@Modifying`) — `like_count`를 SQL로 ±1. `decrease`는 `WHERE like_count > 0`이라 0 아래로 내려가지 않는다(`>= 0` 제약)
- `boolean existsByOrderLineId(UUID)` — 같은 품목의 리뷰가 있는지. **삭제된 리뷰도 센다**(POL-14)
- `Optional<Review> findByIdAndMemberIdAndStatus(UUID id, UUID memberId, ReviewStatus status)` — 수정·삭제가 `ACTIVE`로 부른다. 작성자 본인의 지우지 않은 리뷰만 찾는다. 남의 것과 지운 것은 없는 것과 같다(404)
- `boolean existsByIdAndStatus(UUID id, ReviewStatus status)` — 좋아요·취소가 `ACTIVE`로 부른다. 리뷰가 지우지 않은 채로 있는지
- `List<Review> findByMemberIdAndOrderLineIdIn(UUID memberId, Collection<UUID> orderLineIds)` — 내 리뷰 조회. **지운 리뷰도 돌려준다**. 남의 품목은 `member_id` 조건에서 빠진다(`uk_review_order_line` 인덱스)
- 목록 쿼리 → `List<Review>` — `product_id = :productId AND status = ACTIVE`. 최신순은 첫 페이지용과 다음 페이지용 쿼리 두 개로 `ProductRepository.findPublicFirstPage`·`findPublicNextPage`와 같은 모양이다(메서드 이름은 문서에 없다): `ORDER BY created_at DESC, id DESC`, 다음 페이지는 `created_at < :cursorCreatedAt OR (created_at = :cursorCreatedAt AND id < :cursorId)`를 더하고, 둘 다 `Limit.of(size + 1)`로 부른다. 좋아요순은 `findPublicByLikes(UUID productId, Limit limit)`로 `ORDER BY like_count DESC, created_at DESC, id DESC` 상위 50(`Limit.of(50)`)

`ReviewLikeRepository` 메서드 (native)
- `int insertIgnoringConflict(UUID reviewId, UUID memberId, Instant now)` — `INSERT ... ON CONFLICT DO NOTHING`. 이미 누른 회원이면 아무것도 하지 않는다. 1(새로 눌림) / 0(이미 있음)을 반환한다
- `int deleteByReviewIdAndMemberId(UUID reviewId, UUID memberId)` — 좋아요 행 삭제. 1 / 0(누른 적 없음)을 반환한다
- `List<UUID> findLikedReviewIds(UUID memberId, Collection<UUID> reviewIds)` — `SELECT review_id FROM review.review_like WHERE member_id = :memberId AND review_id IN (:reviewIds)`. 목록 한 페이지의 `likedByMe`를 한 번에 채운다(PK `(review_id, member_id)`로 읽는다). `reviewIds`가 비어 있으면 `IN ()`이 문법 오류라 쿼리를 보내지 않고 빈 목록을 돌려준다

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

**6) 모듈 경계**

`review/package-info.java` — `@ApplicationModule(allowedDependencies = {"common", "order::api"})`(규칙 Y). review는 다른 모듈이 쓸 API와 이벤트를 내놓지 않아 `review.api` 패키지를 만들지 않는다.

**7) review.infrastructure**

| 클래스 | 규격 |
|---|---|
| `JpaReviewRepository` | `ReviewRepository`의 Spring Data 구현(`JpaRepository<Review, UUID>`). 좋아요 수 증감은 `@Modifying` native 쿼리 |
| `JdbcReviewLikeRepository`·`JdbcReviewStatRepository` | `ReviewLikeRepository`·`ReviewStatRepository`의 `NamedParameterJdbcTemplate` 구현(5) 절) |

**8) review.application**

`Command`·`Result` record 이름은 규칙 D·E를 따른 권장안이다. 서비스는 `XxxCommand`를 받아 `XxxResult` record를 반환하고 컨트롤러가 `XxxResponse.from(result)`로 바꾼다(Part 1 규칙 D·E).

| 클래스 | 규격 |
|---|---|
| `WriteReviewCommand` | `record (UUID orderLineId, int rating, String content)`. `ReviewService.write`의 입력 |
| `EditReviewCommand` | `record (int rating, String content)`. 둘 다 필수다(Part 1 규칙 E의 "PATCH 필드는 선택"의 예외. 화면이 늘 둘을 함께 보내고, 별점만 바꿀 때도 통계의 옛 칸·새 칸을 한 번에 옮긴다). `ReviewService.edit`의 입력 |
| `ReviewResult` | 응답 `ReviewResponse`(review.web 절의 API 표)와 같은 필드. `ReviewQueryService.getLatestReviews`·`getTopLikedReviews`의 출력 |
| `MyReviewResult` | 응답 `MyReviewResponse`(review.web 절의 API 표)와 같은 필드. `editableUntil`은 `review.editableUntil()`, `status`는 `ReviewStatus`의 이름. `ReviewService.edit`·`ReviewQueryService.getMine`의 출력 |
| `ReviewStatsResult` | `record (int reviewCount, BigDecimal average, Map<Integer, Integer> distribution)`. `average`는 리뷰가 없으면 `null`이다. `distribution`은 별점(1\~5)을 키로, 그 별점의 리뷰 수를 값으로 하는 `Map`이다(예: 5점 2개·4점 1개·3점 1개면 `{1: 0, 2: 0, 3: 1, 4: 1, 5: 2}`). 키 1\~5가 늘 있다. 응답 `ReviewStatsResponse`는 같은 필드이고 `distribution`이 JSON 객체 `{"1": 0, …, "5": 2}`로 나간다(정수 키는 JSON에서 문자열이다). `ReviewQueryService.getStats`의 출력 |

| 클래스 | 메서드 |
|---|---|
| `ReviewService` | `@Transactional UUID write(UUID memberId, WriteReviewCommand command)` / `@Transactional MyReviewResult edit(UUID memberId, UUID reviewId, EditReviewCommand command)` / `@Transactional void delete(UUID memberId, UUID reviewId)` / `@Transactional void like(UUID memberId, UUID reviewId)` / `@Transactional void unlike(UUID memberId, UUID reviewId)` |
| `ReviewQueryService` | `@Transactional(readOnly = true) CursorPage<ReviewResult> getLatestReviews(UUID productId, UUID memberId, String cursor, int size)` / `@Transactional(readOnly = true) CursorPage<ReviewResult> getTopLikedReviews(UUID productId, UUID memberId)`(`memberId`는 둘 다 비로그인이면 `null`) / `@Transactional(readOnly = true) ReviewStatsResult getStats(UUID productId)` / `@Transactional(readOnly = true) List<MyReviewResult> getMine(UUID memberId, List<UUID> orderLineIds)` |

`write` 순서 — 리뷰를 작성하고 새 `reviewId`(`UUID`)를 반환한다. 전부 한 트랜잭션이다
1. `ConfirmedLineInfo line = orderApi.getConfirmedLine(memberId, command.orderLineId())` — 404·422는 그대로 전파된다
2. `reviewRepository.existsByOrderLineId(line.orderLineId())`면 409 `REVIEW_ALREADY_EXISTS`
3. `Review.write(line, memberId, rating, content)` → `reviewRepository.save`
4. `reviewStatRepository.add(line.productId(), rating, now)` — 리뷰와 통계가 같은 트랜잭션이라 하나만 반영되는 일이 없다
5. `review.getId()` 반환

- 같은 품목 동시 작성은 둘 다 2번을 통과하고 커밋 시 `uk_review_order_line` 위반 → 409 `DUPLICATE_RESOURCE`다. 트랜잭션 안에서 잡지 않는다(As-Is SUP-05)

`edit` 순서 — 작성자가 30일 안에 별점·본문을 고친다. 한 트랜잭션이다
1. `findByIdAndMemberIdAndStatus(reviewId, memberId, ACTIVE)` — 없으면 404 `REVIEW_NOT_FOUND`(남의 리뷰·지운 리뷰 포함)
2. `int oldRating = review.edit(rating, content, now)` — 30일이 지났으면 409 `REVIEW_EDIT_EXPIRED`
3. `reviewStatRepository.change(review.getProductId(), oldRating, rating, now)`
4. 바뀐 리뷰를 `MyReviewResult`로 반환(저장은 커밋 때 더티 체킹). 수정 화면이 내 리뷰 조회와 같은 모양으로 상태를 바꿔 끼운다

`delete` 순서 — 작성자가 삭제한다(soft delete). 한 트랜잭션이다: `findByIdAndMemberIdAndStatus(reviewId, memberId, ACTIVE)`(없으면 404. 지운 리뷰를 다시 지우는 요청도 여기서 막혀 `remove`가 통계를 두 번 빼지 않는다) → `review.delete()` → `reviewStatRepository.remove(review.getProductId(), review.getRating(), now)`

`like`·`unlike` — 좋아요 / 취소. 리뷰가 있으면 이미 눌렀거나 누른 적 없어도 에러 없이 끝난다(멱등)
- `like` 순서 (한 트랜잭션): `existsByIdAndStatus(reviewId, ACTIVE)`가 false면 404 `REVIEW_NOT_FOUND`(`review_like`에 리뷰 FK가 없어 확인하지 않으면 없는 리뷰에도 행이 쌓인다) → `insertIgnoringConflict`가 1이면 `increaseLikeCount`, 0이면 이미 눌렀으니 아무것도 하지 않는다
- `unlike` 순서 (한 트랜잭션): `existsByIdAndStatus(reviewId, ACTIVE)`가 false면 404 `REVIEW_NOT_FOUND` → `deleteByReviewIdAndMemberId`가 1이면 `decreaseLikeCount`, 0이면 아무것도 하지 않는다
- 확인과 쓰기 사이에 리뷰가 지워지면 지운 리뷰의 `like_count`가 바뀔 수 있다. 지운 리뷰는 목록·통계에 쓰이지 않아 락을 잡지 않는다

상품의 ACTIVE 리뷰 목록은 정렬마다 메서드가 다르고, 컨트롤러가 `sort`로 고른다(review.web 절). 두 메서드가 `likedByMe`를 채우는 4번은 같다.

`getLatestReviews` — 최신순, 커서 페이징이다(`ProductQueryService.getPublicProducts`와 같다)
1. `cursor`가 `null`이거나 빈 문자열이면 첫 페이지 쿼리, 아니면 `Cursor.decode(cursor)`로 `(createdAt, id)`를 복원해 다음 페이지 쿼리
2. `Limit.of(size + 1)`로 읽는다
3. 받은 수가 `size`보다 많으면 `size`개만 남기고 `size`번째 리뷰의 `new Cursor(createdAt, id).encode()`를 `nextCursor`로, 아니면 `null`
4. `memberId`가 있으면 남긴 리뷰들의 ID로 `findLikedReviewIds`를 한 번 불러 그 안에 있는 리뷰만 `likedByMe = true`로 한다. `memberId`가 `null`이면 모두 `false`이고 조회하지 않는다
5. `new CursorPage<>(items, nextCursor)`

`getTopLikedReviews` — 좋아요순, `size`와 `cursor`를 받지 않는다. 좋아요순 쿼리로 상위 50개를 읽고 위 4번으로 `likedByMe`를 채워 `new CursorPage<>(items, null)`로 돌려준다(3번의 자르기를 하지 않는다)

`getMine` — 작성자가 구매확정 품목 ID로 자기 리뷰를 찾는다. `findByMemberIdAndOrderLineIdIn(memberId, orderLineIds)`의 결과를 `MyReviewResult`로 바꿔 돌려준다. 리뷰가 없는 품목과 남의 품목은 결과에서 빠지고 에러가 없다. 순서는 정하지 않는다(화면이 `orderLineId`로 찾는다)

`getStats` 순서 — 평점 통계를 돌려준다
1. 통계 행을 `productId`로 읽는다. 행이 없거나 `review_count = 0`이면(모두 지워진 상품) `reviewCount` 0, `average` `null`, `distribution` `{1: 0, …, 5: 0}`을 돌려준다(0으로 나누지 않는다)
2. `average`는 `BigDecimal.valueOf(ratingSum).divide(BigDecimal.valueOf(reviewCount), 1, RoundingMode.HALF_UP)`이다
3. `distribution`은 `{1: r1, 2: r2, 3: r3, 4: r4, 5: r5}`. 키 다섯 개가 늘 있다
- review는 product를 호출하지 않으므로(허용 의존) 상품이 실제로 있는지는 확인하지 않는다

**9) review.web**

| API | 권한 | 요청 | 응답 |
|---|---|---|---|
| `POST /api/reviews` | 로그인 | `WriteReviewRequest(@NotNull UUID orderLineId, @Min(1) @Max(5) int rating, @NotNull @Size(min=10, max=1000) String content)` | 201 `ReviewIdResponse(UUID reviewId)` (`{"reviewId":"…"}`) |
| `PATCH /api/reviews/{id}` | 작성자 | `EditReviewRequest(@NotNull @Min(1) @Max(5) Integer rating, @NotNull @Size(min=10, max=1000) String content)` | 200 `MyReviewResponse` |
| `DELETE /api/reviews/{id}` | 작성자 | | 204 |
| `POST /api/reviews/{id}/likes`, `DELETE ...` | 로그인 | | 204 |
| `GET /api/reviews/mine?orderLineIds={id},{id}` | 로그인 | `@RequestParam @Size(min=1, max=50) List<UUID> orderLineIds` | 200 `List<MyReviewResponse(reviewId, orderLineId, productId, rating, content, likeCount, status, editableUntil, createdAt)>`. `status`는 `ACTIVE`·`DELETED`, `editableUntil`은 `Instant` |
| `GET /api/products/{id}/reviews?sort=latest\|likes&cursor=&size=` | 공개 | `@RequestParam(defaultValue = "latest") @Pattern(regexp = "latest\|likes") String sort`, `size`는 기본 20·`@Min(1) @Max(50)` | 200 `CursorPage<ReviewResponse(id, rating, content, likeCount, likedByMe, createdAt)>` (작성자 정보는 내려주지 않는다 — review가 member를 참조하지 않게) |
| `GET /api/products/{id}/review-stats` | 공개 | | 200 `ReviewStatsResponse(int reviewCount, BigDecimal average(소수 1자리, 리뷰가 없으면 null), Map<Integer, Integer> distribution(별점 1~5 → 리뷰 수))`. 예: `{"reviewCount":4,"average":4.3,"distribution":{"1":0,"2":0,"3":1,"4":1,"5":2}}` |

- 컨트롤러는 review 모듈의 `review.web`에 둔다(클래스 이름은 문서에 없다. 권장 `ReviewController`). 경로가 `/api/products/{id}/...`인 두 GET도 product 모듈이 아니라 여기서 처리한다(위 정책). 요청자 ID는 `@CurrentMember UUID memberId`
- 리뷰 목록은 공개 API라 요청자가 없을 수 있다. `JwtAuthenticationFilter`는 공개 경로에서도 유효한 토큰이면 인증을 채운다. 비로그인 요청에서는 `@CurrentMember`(`@AuthenticationPrincipal(expression = "memberId")`)를 쓸 수 없다. 익명 요청의 `Authentication`은 `null`이 아니고 principal이 문자열 `anonymousUser`라, `AuthenticationPrincipalArgumentResolver`가 그 문자열에 `memberId` 표현식을 평가하다 예외를 던진다(`authentication == null`일 때만 `null`을 준다). 그래서 이 API만 `@AuthenticationPrincipal AuthClaims claims`로 받는다. 표현식이 없으면 principal 타입이 `AuthClaims`가 아닐 때(`errorOnInvalidType` 기본값 `false`) 예외 없이 `null`을 준다. 컨트롤러는 `claims == null ? null : claims.memberId()`를 `memberId`로 넘긴다
- 컨트롤러는 `sort`가 `likes`이면 `getTopLikedReviews(productId, memberId)`(`size`·`cursor`는 읽지 않는다), 아니면 `getLatestReviews(productId, memberId, cursor, size)`를 부른다
- `sort`의 `@Pattern` 위반은 메서드 검증(Part 2 공통 규칙 P-3)이라 400 `INVALID_REQUEST`다. `/api/reviews/mine`은 `/api/reviews/**`의 기본 인증 규칙을 그대로 따른다

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
| `setReviewCreatedAt(UUID reviewId, Instant instant)` → `void` | JDBC `UPDATE review.review SET created_at = ? WHERE id = ?`(`Timestamp.from(instant)`) | `ReviewControllerTest` 8·18·19·20·21·27번 |

이미 있는 헬퍼: `signup`, `login`, `token`, `openShop`, `registerProduct`, `createDeliveredOrder`, `orderLineIdOf`, `ConcurrencyRunner`.

헬퍼를 쓰는 방법은 다음과 같다.
- 응답: `jsonPath`. 에러 응답의 코드는 `$.code`. 리뷰 목록은 `$.items[i].id`·`.rating`·`.content`·`.likeCount`·`.likedByMe`·`.createdAt`과 `$.nextCursor`(마지막 페이지는 `null`). 통계는 `$.reviewCount`, `$.average`, `$.distribution['1']`~`['5']`. 내 리뷰 조회는 응답이 배열이라 `$.length()`와 `$[i].reviewId`·`.orderLineId`·`.productId`·`.rating`·`.content`·`.status`·`.editableUntil`. PATCH 응답은 같은 필드를 `$.reviewId`처럼 읽는다.
- 응답에서 값을 따로 꺼낼 때(`$.reviewId`, `$.nextCursor`): 요청을 `.andReturn()`으로 받아 `getResponse().getContentAsString()`을 본문으로 하고 `JsonPath.read(본문, "$.reviewId")`(`String`)로 꺼낸다. 리뷰 ID는 `UUID.fromString(...)`으로 바꿔 `R1` 등에 담는다. 다음 쪽 요청의 `cursor`에는 꺼낸 `$.nextCursor` 문자열을 그대로 넣는다.

#### `review/domain/ReviewTest`

준비(`불변식 위반` 케이스는 쓰지 않는다): `Review.write(new ConfirmedLineInfo(lineId, productId), memberId, 4, "열 글자가 넘는 리뷰 본문입니다")`(세 ID는 `Ids.newId()`). 영속 전이라 `createdAt`이 `null`이므로 `ReflectionTestUtils.setField(review, "createdAt", T0)`로 채운다. `T0` = `Instant.parse("2026-09-01T00:00:00Z")`.

| 케이스 | 동작 | 확인할 것 |
|---|---|---|
| 작성 | 준비 직후 | 반환값(review getter): `status` = `ACTIVE`, `likeCount` = 0, `rating` = 4, `content` = 넘긴 본문, `orderLineId`·`productId`·`memberId` = 넘긴 값, `id`가 `isNotNull()` |
| 불변식 위반 | `Review.write`를 `rating` 0 / `rating` 6 / 본문 `"가"×9` / 본문 `"가"×1001`로 각각 호출 | 예외: 네 번 모두 `IllegalArgumentException`. 예외: 경계인 `rating` 1·5, 본문 `"가"×10`·`"가"×1000`은 예외 없이 만들어진다 |
| 30일째까지 수정 | `edit(5, "수정한 본문입니다 열 글자 이상", T0.plus(30, DAYS))`(`editableUntil()` 그 시각) | 반환값: 4(바꾸기 전 별점). 반환값(review getter): `rating` = 5, `content` = 새 본문 |
| 30일 지난 수정 | `edit(5, "수정한 본문입니다 열 글자 이상", T0.plus(30, DAYS).plusSeconds(1))` | 예외: `BusinessException`, `errorCode` = `REVIEW_EDIT_EXPIRED`. 반환값(review getter): `rating` = 4, `content` = 원래 본문 그대로 |
| 삭제 | `delete()` | 반환값(review getter): `status` = `DELETED` |

#### `review/web/ReviewControllerTest` (MockMvc)

**준비 A**(이 클래스의 23·24·25번을 뺀 모든 케이스와 `ReviewConcurrencyTest`가 쓴다): 판매자 1명(`seller@test.com`), 가게 1개, 상품 `productA`(`registerProduct(sellerId, shopId, 10000, 100)`, 재고 100). 구매자는 필요한 만큼 `buyer1@test.com`, `buyer2@test.com`, …로 `signup` + `login`해 토큰을 받고, 각자 `createConfirmedLine(buyerId, sellerId, shopId, productA)`로 구매확정된 품목 하나씩을 가진다(`lineOf(buyerN)`이라 부른다). 작성 요청은 `POST /api/reviews`에 `Authorization: Bearer {구매자 토큰}`, 본문 `{"orderLineId":"{lineId}","rating":5,"content":"효과가 좋아요 정말 만족합니다"}`(본문 16자)다. 응답 `$.reviewId`로 리뷰 ID를 받는다.

**준비 B (리뷰 3개)**: `buyer1`이 5점, `buyer2`가 4점, `buyer3`이 4점으로 `productA`에 순서대로 작성한다. 이 리뷰를 `R1`(5점), `R2`(4점), `R3`(4점)이라 부른다. 이때 통계는 `review_count` 3, `rating_sum` 13, `r4` 2, `r5` 1, 나머지 0이다.

**준비 C (최신순 목록)**: 준비 B 뒤 `setReviewCreatedAt(Ri, 기준.plus(i, HOURS))`(`기준` = `Instant.now().minus(10, DAYS)`, `i`는 리뷰 번호 1~3)로 `R1 < R2 < R3` 순으로 작성 시각을 맞춘다. 18·19·20·21번이 쓴다.

**준비 D (삭제한 리뷰)**: 준비 B 뒤 `buyer1`이 `DELETE /api/reviews/{R1}`로 `R1`을 지운다. 이때 통계는 `review_count` 2, `rating_sum` 8, `r4` 2, 나머지 0이다. 5·9·13·16·22·28번이 쓴다.

**1. 작성하면 통계가 쌓인다** — 동작: 준비 B를 실행한다.
- 응답(andExpect): 세 응답 모두 상태 201, `jsonPath("$.reviewId").isNotEmpty()`.
- DB(reviewStatOf(productA)): `reviewCount` 3, `ratingSum` 13, `rating(4)` 2, `rating(5)` 1, `rating(1)`~`rating(3)` 0.
- 응답(andExpect): `GET /api/products/{productA}/review-stats`(Authorization 없이) 상태 200, `$.reviewCount` = 3, `$.average` = 4.3(13÷3=4.333…), `$.distribution['4']` = 2, `['5']` = 1, `['1']`·`['2']`·`['3']` = 0.

**2. 입력 검증** — 준비: `buyer1`의 구매확정 품목. 동작: 아래 요청을 차례로 보낸다(`rating`·`content`는 따로 적은 것만 바꾼다).
- `rating` 0 / `rating` 6 / `content` `"가"×9` / `content` `"가"×1001` / `orderLineId` 생략 → 응답(andExpect): 다섯 번 모두 상태 400, `$.code` = `INVALID_REQUEST`. DB(reviewCountOf(productA)): 0.
- `content` `"가"×10`, `rating` 1 → 응답(andExpect): 상태 201.

**3. 구매확정 전 품목 거부** — 준비: `buyer1`이 `createDeliveredOrder(buyerId, sellerId, shopId, productA)` + `orderLineIdOf(orderId, productA)`까지만 한다. 동작: 그 품목으로 작성 요청.
- 응답(andExpect): 상태 422, `$.code` = `REVIEW_NOT_ALLOWED`.
- DB(reviewCountOf(productA)): 0. DB(reviewStatOf(productA)): `null`(`isNull()`).

**4. 남의 품목 거부** — 준비: `buyer1`의 구매확정 품목, `buyer2` 가입·로그인. 동작: `buyer2` 토큰으로 `buyer1`의 `lineId`를 담아 작성 요청.
- 응답(andExpect): 상태 404, `$.code` = `ORDER_LINE_NOT_FOUND`.
- DB(reviewCountOf(productA)): 0. DB(reviewStatOf(productA)): `null`(`isNull()`).

**5. 삭제한 품목에는 다시 쓸 수 없다** — 준비: 준비 D. 동작: `buyer1`이 같은 `lineId`로 4점 리뷰를 다시 작성.
- 응답(andExpect): 상태 409, `$.code` = `REVIEW_ALREADY_EXISTS`. DB(reviewCountOf(productA)): 3. DB(reviewStatOf(productA)): 준비 D의 값(`reviewCount` 2, `ratingSum` 8, `rating(4)` 2, `rating(5)` 0) 그대로.

**6. 별점 수정으로 분포가 옮겨 간다** — 준비: 준비 B. 동작: `buyer3`이 `PATCH /api/reviews/{R3}`에 `{"rating":2,"content":"수정한 본문입니다 열 글자 이상"}`.
- 응답(andExpect): PATCH 응답 상태 200, `$.reviewId` = `R3`, `$.rating` = 2, `$.content` = 새 본문.
- DB(reviewStatOf(productA)): `reviewCount` 3, `ratingSum` 11, `rating(2)` 1, `rating(4)` 1, `rating(5)` 1.
- 응답(andExpect): `GET /api/products/{productA}/review-stats`(Authorization 없이)의 `$.average` = 3.7(11÷3=3.666…), `$.distribution['2']` = 1.

**7. 남의 리뷰는 수정할 수 없다** — 준비: 준비 B. 동작: `buyer1`이 `PATCH /api/reviews/{R3}`에 `{"rating":2,"content":"수정한 본문입니다 열 글자 이상"}`.
- 응답(andExpect): 상태 404, `$.code` = `REVIEW_NOT_FOUND`. DB(reviewSnapshotOf(R3)): `rating()`은 4 그대로.

**8. 30일이 지난 리뷰는 수정할 수 없다** — 준비: 준비 B 뒤 `setReviewCreatedAt(R2, Instant.now().minus(31, DAYS))`. 동작: `buyer2`가 `PATCH /api/reviews/{R2}`에 `{"rating":2,"content":"수정한 본문입니다 열 글자 이상"}`.
- 응답(andExpect): 상태 409, `$.code` = `REVIEW_EDIT_EXPIRED`. DB(reviewSnapshotOf(R2)): `rating()` 4 그대로. DB(reviewStatOf(productA)): `ratingSum()` 13 그대로.

**9. 지운 리뷰는 수정할 수 없다** — 준비: 준비 D. 동작: `buyer1`이 `PATCH /api/reviews/{R1}`에 `{"rating":2,"content":"수정한 본문입니다 열 글자 이상"}`.
- 응답(andExpect): 상태 404, `$.code` = `REVIEW_NOT_FOUND`.
- DB(reviewSnapshotOf(R1)): `rating()` 5, `content()` 원래 본문 그대로. DB(reviewStatOf(productA)): 준비 D의 값(`ratingSum` 8, `rating(2)` 0) 그대로.

**10. 수정은 별점과 본문을 함께 보내야 한다** — 준비: 준비 B. 동작: `buyer3`이 `PATCH /api/reviews/{R3}`에 `{"content":"수정한 본문입니다 열 글자 이상"}`(별점 생략) / `{"rating":2}`(본문 생략)를 각각 보낸다.
- 응답(andExpect): 두 번 모두 상태 400, `$.code` = `INVALID_REQUEST`.
- DB(reviewSnapshotOf(R3)): `rating()` 4, `content()` 원래 본문 그대로. DB(reviewStatOf(productA)): `ratingSum()` 13 그대로.

**11. 삭제하면 통계에서 빠진다** — 준비: 준비 B. 동작: `buyer1`이 `R1`을 `DELETE /api/reviews/{R1}`.
- 응답(andExpect): 상태 204.
- DB(reviewSnapshotOf(Ri)): `status()` — `R1` → `DELETED` / `R2`, `R3` → `ACTIVE`.
- DB(reviewStatOf(productA)): `reviewCount` 2, `ratingSum` 8, `rating(4)` 2, `rating(5)` 0.
- 응답(andExpect): `GET /api/products/{productA}/review-stats`(Authorization 없이)의 `$.average` = 4.0.

**12. 남의 리뷰는 삭제할 수 없다** — 준비: 준비 B. 동작: `buyer2`가 `R1`을 삭제 시도.
- 응답(andExpect): 상태 404, `$.code` = `REVIEW_NOT_FOUND`. DB(reviewSnapshotOf(R1)): `status()`는 `ACTIVE` 그대로.

**13. 지운 리뷰는 다시 지울 수 없다** — 준비: 준비 D. 동작: `buyer1`이 `DELETE /api/reviews/{R1}`을 다시 보낸다.
- 응답(andExpect): 상태 404, `$.code` = `REVIEW_NOT_FOUND`.
- DB(reviewStatOf(productA)): 준비 D의 값(`reviewCount` 2, `ratingSum` 8, `rating(5)` 0) 그대로.

**14. 같은 리뷰에 좋아요 두 번은 1** — 준비: 준비 B. 동작: `buyer2`가 `POST /api/reviews/{R1}/likes`를 두 번 보낸다.
- 응답(andExpect): 두 응답 모두 상태 204.
- DB(reviewSnapshotOf(R1)): `likerIds()` = [`buyer2`의 회원 ID], `likeCount` = 1.

**15. 좋아요 취소는 두 번 보내도 0 아래로 내려가지 않는다** — 준비: 준비 B 뒤 `buyer2`가 `R1`에 좋아요를 한 번 누른다. 동작: `buyer2`가 `DELETE /api/reviews/{R1}/likes`를 두 번 보낸다.
- 응답(andExpect): 두 응답 모두 상태 204. DB(reviewSnapshotOf(R1)): `likerIds()`가 비어 있고(`isEmpty()`) `likeCount` = 0.

**16. 지운 리뷰에는 좋아요를 누를 수 없다** — 준비: 준비 D. 동작: `buyer2`가 `POST /api/reviews/{R1}/likes`.
- 응답(andExpect): 상태 404, `$.code` = `REVIEW_NOT_FOUND`.
- DB(reviewSnapshotOf(R1)): `likerIds()`가 비어 있고(`isEmpty()`) `likeCount` = 0.

**17. 지운 리뷰의 좋아요는 취소할 수 없다** — 준비: 준비 B 뒤 `buyer2`가 `R1`에 좋아요를 누르고, `buyer1`이 `R1`을 지운다. 동작: `buyer2`가 `DELETE /api/reviews/{R1}/likes`.
- 응답(andExpect): 상태 404, `$.code` = `REVIEW_NOT_FOUND`.
- DB(reviewSnapshotOf(R1)): `likerIds()` = [`buyer2`의 회원 ID], `likeCount` = 1 그대로.

**18. 목록: 최신순 커서** — 준비: 준비 C. 아래 `GET`은 모두 Authorization 없이 보낸다.
- 최신순 1쪽 `GET /api/products/{productA}/reviews?sort=latest&size=2`(`.andReturn()`으로 받아 본문에서 `$.nextCursor`를 꺼낸다) — 응답(andExpect): 상태 200, `$.items.length()` = 2, `$.items[0].id` = `R3`, `$.items[1].id` = `R2`, `jsonPath("$.nextCursor").value(notNullValue())`. `$.items[0]`과 `$.items[1]` 각각에 `id`·`rating`(4)·`content`·`likeCount`(0)·`likedByMe`(false)·`createdAt`이 있고, `jsonPath("$.items[0].memberId").doesNotExist()`.
- 2쪽 `?sort=latest&size=2&cursor={1쪽 응답 본문(andReturn 후)에서 꺼낸 $.nextCursor}` — 응답(andExpect): `$.items.length()` = 1, `$.items[0].id` = `R1`, `jsonPath("$.nextCursor").value(nullValue())`.

**19. 정렬을 생략하면 최신순이다** — 준비: 준비 C. 동작: `GET /api/products/{productA}/reviews?size=2`(`sort` 생략, Authorization 없이).
- 응답(andExpect): 상태 200, `$.items[0].id` = `R3`, `$.items[1].id` = `R2`.

**20. 목록: 좋아요순** — 준비: 준비 C 뒤 `buyer2`가 `POST /api/reviews/{R1}/likes`로 `R1`에 좋아요를 한 번 누른다. 동작: `?sort=likes&size=1`(Authorization 없이).
- 응답(andExpect): 상태 200, `$.items.length()` = 3(좋아요순은 `size`를 쓰지 않는다), `$.items[0..2].id` = `R1`, `R3`, `R2`이고 `$.items[0..2].likeCount` = 1, 0, 0(`R3`·`R2`는 좋아요 0으로 같아 최신순), `jsonPath("$.nextCursor").value(nullValue())`.

**21. 목록의 `likedByMe`는 요청자 기준이다** — 준비: 준비 C 뒤 `buyer1`이 `R2`에, `buyer2`가 `R1`에 좋아요를 누른다. 동작: `buyer1` 토큰으로 `GET /api/products/{productA}/reviews?sort=latest&size=3`.
- 응답(andExpect): 상태 200, `$.items[0..2].id` = `R3`, `R2`, `R1`이고 `$.items[0..2].likedByMe` = false, true, false(`R1`은 다른 회원 `buyer2`가 눌러서 `buyer1`에게는 false다).

**22. 삭제한 리뷰는 목록에서 빠진다** — 준비: 준비 D. 동작: `GET /api/products/{productA}/reviews?sort=latest`.
- 응답(andExpect): `$.items.length()` = 2, `$.items[*].id`가 `R2`·`R3`(`containsInAnyOrder`, 순서 무관)이고 `R1`은 없다(`jsonPath("$.items[*].id", not(hasItem(R1)))`).

**23. 목록: 리뷰가 없는 상품** — 동작: 리뷰가 없는 상품(`Ids.newId()`)의 `GET /api/products/{그 id}/reviews`(Authorization 없이).
- 응답(andExpect): 상태 200, `jsonPath("$.items").value(hasSize(0))`.

**24. 허용 밖 정렬 값은 거부한다** — 동작: `GET /api/products/{Ids.newId()}/reviews?sort=rating`(Authorization 없이).
- 응답(andExpect): 상태 400, `$.code` = `INVALID_REQUEST`.

**25. 통계: 리뷰가 없는 상품** — 동작: `GET /api/products/{Ids.newId()}/review-stats`(Authorization 없이).
- 응답(andExpect): 상태 200, `$.reviewCount` = 0, `jsonPath("$.average").value(nullValue())`, `$.distribution['1']`~`['5']` = 0.

**26. 통계: 리뷰가 모두 지워진 상품** — 준비: `buyer1`이 `productA`에 5점 리뷰 `R1`을 쓰고 지운다(통계 행이 `review_count` 0으로 남는다). 동작: `GET /api/products/{productA}/review-stats`(Authorization 없이).
- 응답(andExpect): 상태 200, `$.reviewCount` = 0, `jsonPath("$.average").value(nullValue())`, `$.distribution['5']` = 0.

**27. 내 리뷰 조회** — 준비: `buyer1`이 구매확정 품목 두 개를 가진다. `L1`은 준비 A의 `createConfirmedLine(buyer1Id, sellerId, shopId, productA)`로 만든 `lineOf(buyer1)`이고, `L2`는 같은 호출을 한 번 더 해서 만든 품목이다. `buyer1`은 `L1`에만 5점 리뷰 `R1`을 쓴다. `setReviewCreatedAt(R1, Instant.parse("2026-10-01T00:00:00Z"))`. 동작: `buyer1` 토큰으로 `GET /api/reviews/mine?orderLineIds={L1},{L2}`.
- 응답(andExpect): 상태 200, `$.length()` = 1(리뷰가 없는 `L2`는 빠진다), `$[0].reviewId` = `R1`, `.orderLineId` = `L1`, `.productId` = `productA`, `.rating` = 5, `.content` = 보낸 본문, `.status` = `ACTIVE`, `.editableUntil` = `"2026-10-31T00:00:00Z"`.

**28. 내 리뷰 조회는 지운 리뷰도 돌려준다** — 준비: 준비 D. 동작: `buyer1` 토큰으로 `GET /api/reviews/mine?orderLineIds={lineOf(buyer1)}`.
- 응답(andExpect): 상태 200, `$.length()` = 1, `$[0].reviewId` = `R1`, `$[0].status` = `DELETED`.

**29. 남의 품목은 내 리뷰 조회에서 빠진다** — 준비: 준비 B. 동작: `buyer2` 토큰으로 `GET /api/reviews/mine?orderLineIds={lineOf(buyer1)}`.
- 응답(andExpect): 상태 200, `jsonPath("$").value(hasSize(0))`.

**30. 내 리뷰 조회 입력 검증** — 준비: 준비 A의 `buyer1` 토큰. 동작: `orderLineIds` 생략 / `Ids.newId()` 51개 / `orderLineIds=not-a-uuid`로 각각 `GET /api/reviews/mine`.
- 응답(andExpect): 세 번 모두 상태 400, `$.code` = `INVALID_REQUEST`.
- `Ids.newId()` 50개 → 응답(andExpect): 상태 200.

#### `review/application/ReviewConcurrencyTest` (`ReviewControllerTest`의 준비 A 사용)

각 작업은 요청을 `.andReturn()`으로 받은 `MvcResult`를 반환한다. 상태 코드는 `Result.value().getResponse().getStatus()`, 본문 값은 `JsonPath.read(Result.value().getResponse().getContentAsString(), "$.code")`처럼 꺼낸다. 같은 작업을 N번 돌릴 때는 `ConcurrencyRunner.run(int threads, Callable<T> task)`, 작업이 서로 다르면 `run(List<Callable<T>> tasks)`(`run(List.of(...))`)를 쓴다. 둘 다 스레드를 동시에 출발시키고 제출 순서대로 `List<Result<T>>`(`Result(T value, Throwable error)`)를 돌려준다.

**1. 같은 품목 동시 작성 2건** — 준비: `buyer1`의 구매확정 품목 하나. 동작: 같은 요청(5점)을 두 스레드가 동시에 보낸다.
- 응답 본문(andReturn 후, 두 스레드가 모두 끝난 뒤): 두 결과의 상태 코드 — 201이 정확히 1개, 409가 정확히 1개.
- 응답 본문(andReturn 후): 409 결과 본문의 `$.code`는 `DUPLICATE_RESOURCE` 또는 `REVIEW_ALREADY_EXISTS` 중 하나.
- 아래 DB 확인은 두 스레드가 모두 끝난 뒤에 읽는다. DB(reviewCountOf(productA)): 1.
- DB(reviewSnapshotOf(reviewId)): `status()` = `ACTIVE`(`reviewId`는 201 결과의 응답 본문(andReturn 후)에서 `JsonPath.read(본문, "$.reviewId")`로 꺼낸다).
- DB(reviewStatOf(productA)): `reviewCount` 1, `ratingSum` 5, `rating(5)` 1.

**2. 서로 다른 회원 좋아요 동시 100건** — 준비: `buyer1`이 쓴 리뷰 `R1` 하나. 회원 100명(`token("liker" + i + "@test.com")`, i = 1~100)의 토큰을 미리 받아 둔다. 동작: 100명이 동시에 `POST /api/reviews/{R1}/likes`.
- 응답 본문(andReturn 후, 100개 스레드가 모두 끝난 뒤): 100개 결과의 상태 코드가 모두 204.
- DB(reviewSnapshotOf(R1), 100개 스레드가 모두 끝난 뒤): `likerIds().size()` = 100, `likeCount` = 100.

**3. 서로 다른 품목 리뷰 동시 50건** — 준비: 구매자 50명(`signup` + `login`)이 각자 `createConfirmedLine`으로 `productA`의 구매확정 품목을 하나씩 가진다. 별점은 번호 `i`(0~49)에 따라 `i < 20` → 5점, `20 ≤ i < 35` → 4점, `35 ≤ i < 45` → 3점, `i ≥ 45` → 1점. 동작: 50명이 동시에 작성한다.
- 응답 본문(andReturn 후, 50개 스레드가 모두 끝난 뒤): 50개 결과의 상태 코드가 모두 201.
- 아래 DB·통계 확인은 50개 스레드가 모두 끝난 뒤에 읽는다. DB(reviewCountOf(productA)): 50.
- DB(reviewStatOf(productA)): `reviewCount` 50, `ratingSum` 195(20×5 + 15×4 + 10×3 + 5×1), `rating(1)` 5, `rating(3)` 10, `rating(4)` 15, `rating(5)` 20.
- 응답(andExpect): `GET /api/products/{productA}/review-stats`(Authorization 없이)의 `$.average` = 3.9(195÷50).

**4. 수정과 좋아요 동시** — 준비: `buyer1`이 `productA`에 5점 리뷰 `R1`을 쓴다. 회원 50명(`token("liker" + i + "@test.com")`, i = 1~50)의 토큰을 미리 받아 둔다. 동작: `buyer1`이 `PATCH /api/reviews/{R1}`(`{"rating":4,"content":"수정한 리뷰 내용입니다."}`)을 보내는 작업 1개와 50명이 `POST /api/reviews/{R1}/likes`를 보내는 작업 50개를 `run(List.of(...))`로 함께 출발시킨다.
- 응답 본문(andReturn 후, 51개 스레드가 모두 끝난 뒤): 수정 결과의 상태 코드 200, 좋아요 50개 결과의 상태 코드가 모두 204.
- DB(reviewSnapshotOf(R1), 51개 스레드가 모두 끝난 뒤): `rating()` = 4, `content()` = `"수정한 리뷰 내용입니다."`, `likerIds().size()` = 50, `likeCount` = 50. 수정이 읽은 `like_count`로 덮어쓰면 `likeCount`가 50보다 작아진다.

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
| alias | 인덱스에 붙이는 별칭. 검색·색인은 항상 alias(`products`)로 한다 → SUB-7에서 실제 인덱스를 바꿔치기할 수 있다 |
| external version | 문서에 상품 `version`을 같이 넣으면 ES가 **더 오래된 버전의 쓰기를 거부**한다 → 이벤트 순서가 뒤바뀌어도 최신 유지 |
| edge_ngram | "루틴비타" → "루틴", "루틴비", "루틴비타"처럼 앞부분 조각을 미리 색인해 자동완성을 빠르게 |
| nori | 한국어 형태소 분석기. "멀티비타민"을 "멀티", "비타민"으로 잘라 색인해 "비타민"으로도 찾게 한다. ES 플러그인이라 플러그인을 넣은 이미지를 직접 만든다 |

**정책 (이 단계에서 정함)**
- 단종·숨김 상품도 문서는 남기고 `status`로 걸러낸다(삭제와 재색인이 엇갈리는 문제를 피함). 검색 결과는 `ON_SALE`만.
- 상품명·설명의 검색은 nori로 분석하고, 자동완성은 edge_ngram을 쓴다. ES를 고른 근거와 측정 결과는 [ADR-014](../adr/ADR-014-search-engine-choice.md)에 있다.
- 검색어 조각은 OR로 맞춘다(`multi_match` 기본값). nori는 같은 단어를 문맥에 따라 다르게 자르기 때문에 AND면 놓친다: `수면용 근육이완 보충제` 안의 근육이완은 `근육`·`이완`인데 검색어 `근육이완`만 넣으면 `근육`·`완`·`오`가 되어 AND로는 못 찾는다.
- 사전에 없는 말은 쪼개진다. `프로폴리스`는 `프로`·`폴리스`가 되어 `프로틴`(`프로`·`틴`)도 결과 뒤쪽에 나온다. 두 조각이 모두 맞는 상품의 점수가 높아 앞에 온다. 사용자 사전과 검색 품질 개선은 Stage 2 이후 과제로 둔다.
- 품사 필터는 감탄사(`IC`)와 일반 부사(`MAG`)를 버리지 않는다. nori가 뒤에 다른 단어가 오면 상품 이름을 이 품사로 잘못 판단해서, 기본값대로 버리면 `마스크팩 세트`는 `마스크팩`으로, `아이 크림`은 `아이`로 찾지 못한다. 대신 `정말`·`매우` 같은 부사도 검색어로 맞는다.
- 검색 정렬: 관련도(`_score`) → id. 페이지는 `search_after` 커서(Base64로 감싼 정렬값), size 기본 20·최대 50.
- 검색 결과 항목은 상품 목록 카드(`ProductSummaryResponse`)와 같은 모양이다. ES는 순서와 커서만 정하고, 항목 값(이름·가격·카테고리·사진·재고 여부·등록 시각)은 상품 DB에서 읽는다([ADR-013](../adr/ADR-013-search-hydration.md)). 사진은 이미지 추가·삭제가, 재고는 재고 조정·주문이 바꾸는데 둘 다 `product-upserted`를 내지 않아 ES 문서에 두면 낡는다.
- ES에서는 `ON_SALE`이어도 DB에서 판매 중이 아니면 결과에서 뺀다. 그래서 한 페이지가 `size`보다 짧거나 비어 있을 수 있고, `nextCursor`는 ES 결과로 정한다.
- 자동완성: 상품명이 검색어로 시작하는 판매 중 상품의 서로 다른 이름을 최대 10개, 2자 이상일 때만. 맞는 이름이 10개보다 적으면 있는 만큼만 돌려준다. 관련도순으로 정렬하고, 점수가 같으면 이름 오름차순(한글은 가나다순)이다. 띄어쓰기가 달라도 찾도록 공백은 무시한다: `허브티`와 `허브 티`는 둘 다 `허브 티백`을 찾는다.
- 재색인 기능은 이 단계에 없다([서브 SUB-7](../04-sub/search-reindex.md)). 문서는 `product-upserted` 이벤트로만 채워진다. 분석기·매핑을 바꾸거나 ES 데이터를 잃으면 인덱스를 지우고 앱을 다시 시작하는데, 새 인덱스는 비어 있어 기존 상품은 다음에 수정될 때까지 검색되지 않는다. 이 한계는 회고에 적는다.

### 할 일

**구현 순서**: 절 번호 순서대로 만든다. 1) 인프라(compose·의존성·`IntegrationTestSupport`·`application.yaml` 타임아웃) → 2) 모듈 경계 → 3) `search.infrastructure`(`ElasticsearchTimeoutConfig` → `ProductIndexDefinition`·`products-index.json` → `ProductIndexer`(`ensureIndex` → `upsert`) → `ProductEventConsumer`) → 4) product 요약 조회(`product.domain` → `product.infrastructure` → `product.api` → `product.application`) → 5) `search.application`(`SearchErrorCode` → `ProductSearchService`) → 6) `search.web` → 7) `common.security` → 테스트.

**1) 인프라**
- `docker/elasticsearch/Dockerfile`: 공식 이미지에 nori를 넣는다. 로컬·운영·테스트가 이 파일 하나를 같이 쓴다. `FROM docker.elastic.co/elasticsearch/elasticsearch:9.4.8`(클라이언트 9.4.5와 같은 9.4 줄의 최신 패치) + `RUN bin/elasticsearch-plugin install --batch analysis-nori`(`--batch`는 권한 확인 프롬프트를 건너뛴다. 빌드할 때 `artifacts.elastic.co`에서 받는다)
- `docker/docker-compose.yml`: 서비스 `elasticsearch`. `build: ./elasticsearch`(compose 파일 기준 경로), `image: myroutine-elasticsearch:9.4.8`, `restart: unless-stopped`, 환경변수 `discovery.type: single-node`, `xpack.security.enabled: "false"`, `ES_JAVA_OPTS: -Xms512m -Xmx512m`, 포트 `127.0.0.1:9200:9200`, named volume `elasticsearch_data:/usr/share/elasticsearch/data`
- `docker/docker-compose.ops.yml`: 위와 같은 서비스에서 `ports`를 빼고 아래 `healthcheck`를 둔다(`up`이 이미지가 없으면 빌드하고 healthy까지 기다리는 것을 이 설정으로 확인했다). `app`에는 `environment`의 `SPRING_ELASTICSEARCH_URIS: http://elasticsearch:9200`과 `depends_on`의 `elasticsearch: condition: service_healthy`를 더한다. `depends_on`은 `compose up` 때의 시작 순서만 정하므로, 평소 ES가 죽어도 앱은 영향받지 않는다
  - `healthcheck`: `test: ["CMD", "curl", "-fsS", "http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=5s"]`, `interval: 10s`, `timeout: 10s`, `retries: 12`, `start_period: 60s`. 노드가 하나라 복제본을 둘 곳이 없어 상태는 `green`이 아니라 `yellow`까지 오른다
- Dockerfile을 바꾸면 `image` 태그도 올린다. 같은 태그의 이미지가 이미 있으면 `up`이 다시 빌드하지 않는다
- 의존성: `implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'`(Boot 4.1.1에 있다), 테스트 `testImplementation 'org.testcontainers:testcontainers-elasticsearch'`(Testcontainers BOM 2.0.5가 관리)
- `IntegrationTestSupport`: 아래 형태로 만들고 `Startables.deepStart(...)`에 추가한다. `kafka`처럼 `protected`라서 같은 패키지의 `TestFixtures`와 다른 패키지의 하위 테스트가 모두 `es`를 읽는다.

  ```java
  @ServiceConnection
  protected static final ElasticsearchContainer es = new ElasticsearchContainer(
          DockerImageName.parse(new ImageFromDockerfile("myroutine-elasticsearch:9.4.8", false)
                          .withDockerfile(Path.of("docker/elasticsearch/Dockerfile"))
                          .get())
                  .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
          .withEnv("xpack.security.enabled", "false")
          .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
  ```

  | 부분 | 이유 |
  |---|---|
  | `new ImageFromDockerfile(이름, false)` | 테스트가 시작될 때 Dockerfile로 이미지를 빌드한다. `false`는 끝나도 이미지를 지우지 않는다는 뜻이라 다음 실행은 Docker 캐시로 바로 끝난다. `.get()`은 빌드가 끝날 때까지 기다려 이미지 이름을 돌려준다 |
  | `Path.of("docker/...")` | Gradle 테스트의 작업 폴더는 리포 루트다 |
  | `asCompatibleSubstituteFor` | `ElasticsearchContainer`는 공식 이미지 이름만 받으므로, 직접 만든 이미지를 공식 이미지 대신 쓴다고 알린다(`minio`와 같다) |
  | 태그 `9.4.8` | `ElasticsearchContainer`가 태그로 8.x 이상인지 판단하므로 버전을 태그에 그대로 둔다 |
  | `xpack.security.enabled` | 8.x 이후 이미지는 기본으로 보안이 켜져 있어 끈다 |
  | `ES_JAVA_OPTS` | `ElasticsearchContainer`의 기본 힙이 2GB라 compose와 같은 512MB로 줄인다 |

- `application.yaml`
  - `spring.elasticsearch.connection-timeout: 2s`, `spring.elasticsearch.socket-timeout: 2s`(공통 규칙 X의 ES 타임아웃 2초. Boot 4.1.1 기본값은 1s·30s). 이 둘만으로는 응답 대기가 끊기지 않는다. 아래 `ElasticsearchTimeoutConfig`와 함께 둔다
  - `management.health.elasticsearch.enabled: false`. 켜 두면(기본값) ES가 멈출 때 `/actuator/health`가 `DOWN`이 되고, 운영 `app`의 healthcheck가 이 주소를 보므로 앱이 `unhealthy`가 되어 배포가 롤백된다. "ES가 죽어도 검색만 실패한다"는 목표와 어긋난다

**2) search — 모듈 경계** (domain 생략, application + infrastructure, 개발 가이드 §3.3)

- **역할**: search는 상품 원본(Postgres)을 부르지 않는다. product가 내보내는 `product-upserted` 이벤트를 받아 ES에 문서로 복제해 두고, 검색 요청은 그 ES만 읽는다.
- **호출 수단**: 공식 Java 클라이언트 `ElasticsearchClient`를 쓴다(`spring-boot-starter-data-elasticsearch`가 빈으로 만든다). 요청이 ES REST API의 JSON과 1:1이라 아래 요청 모양을 그대로 옮길 수 있고, Spring Data의 `ElasticsearchOperations`는 엔티티 매핑을 전제하는데 이 모듈은 domain을 두지 않는다. 아래 3)·5)의 순서는 **ES에 보내야 하는 요청의 모양**으로 적었고, 클라이언트 호출은 아래 사용법과 같다.

| 클래스 | 규격 |
|---|---|
| `search/package-info.java` | 모듈 경계. `@ApplicationModule(allowedDependencies = {"common", "product::api"})`(규칙 Y). `product::api`는 이벤트 record(`ProductUpsertedEvent`), 검색 결과 요약(`getOnSaleSummaries`)을 쓰기 위한 것이다 |

아래 코드는 클라이언트 9.4.5로 ES 9.4.8에 실행해 확인했다. 검색·자동완성의 응답값은 그때 나온 값이다.

**타입과 패키지**

| 타입 | 패키지 | 뜻 |
|---|---|---|
| `ElasticsearchClient` | `co.elastic.clients.elasticsearch` | ES 호출 진입점. 스타터가 빈으로 만들어 주므로 생성자로 주입받는다 |
| `ElasticsearchException` | `co.elastic.clients.elasticsearch._types` | ES가 오류 응답을 줄 때 나는 런타임 예외 |
| `FieldValue` | `co.elastic.clients.elasticsearch._types` | 숫자·문자열 하나를 담는 값. `FieldValue.of(0.22)`, `FieldValue.of("abc")` |
| `SortOrder`, `VersionType` | `co.elastic.clients.elasticsearch._types` | 정렬 방향(`Desc`, `Asc`), 버전 방식(`External`) enum |
| `Query` | `co.elastic.clients.elasticsearch._types.query_dsl` | 검색 조건 하나. `filter`에 넣을 목록을 미리 만들 때 쓴다 |
| `SearchResponse<T>` | `co.elastic.clients.elasticsearch.core` | 검색 응답. `T`는 `_source`를 담을 타입이다 |
| `Hit<T>` | `co.elastic.clients.elasticsearch.core.search` | 검색 결과 한 건 |

**호출 코드를 읽는 법**

모든 호출은 람다로 요청을 조립한다. `b`는 요청 빌더이고, 메서드 이름이 ES JSON의 키, 인자가 값이다. 값이 객체인 키는 다시 람다를 받는다.

```java
client.search(b -> b.index("products").size(21), Map.class);
// POST /products/_search  {"size": 21}
```

- 모든 호출은 검사 예외 `IOException`을 던진다. 람다 바깥의 메서드나 `try`에서 처리한다.
- 람다 안에서 쓰는 지역 변수와 파라미터는 다시 대입하면 안 된다(effectively final). 람다 파라미터 이름은 바깥 변수와 겹칠 수 없어서, 메서드 파라미터가 `q`면 `query -> ...`처럼 다른 이름을 쓴다.

**인덱스 준비** (`ensureIndex`)

```java
boolean exists = client.indices().existsAlias(b -> b.name("products")).value();

String json = new ClassPathResource("search/products-index.json").getContentAsString(StandardCharsets.UTF_8);
String indexName = "products-" + LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
client.indices().create(b -> b
        .index(indexName)
        .withJson(new StringReader(json))   // JSON의 settings·mappings를 빌더에 넣는다
        .aliases("products", a -> a));      // 만들면서 alias를 연결한다
```

`ClassPathResource`는 `org.springframework.core.io` 패키지에 있고, `src/main/resources` 아래 파일을 읽는다.

**문서 쓰기** (`upsert`)

```java
Map<String, Object> document = Map.of(
        "productId", e.productId().toString(),
        "shopId", e.shopId().toString(),
        "name", e.name(),
        "description", e.description(),
        "category", e.category(),
        "price", e.price(),
        "status", e.status());

try {
    client.index(b -> b
            .index(indexOrAlias)
            .id(e.productId().toString())
            .version(e.version())
            .versionType(VersionType.External)
            .document(document));
} catch (ElasticsearchException ex) {
    if (ex.status() != 409) {
        throw ex;
    }
    // 409는 이미 같거나 더 최신이라는 뜻이다
}
```

`Map.of`는 값에 `null`이 있으면 `NullPointerException`을 던진다. 위 값은 모두 `NOT NULL` 컬럼에서 온다. 같은 id에 버전 0을 다시 쓰면 `ex.status()` = 409, `ex.error().type()` = `version_conflict_engine_exception`이고, 더 큰 버전 3을 쓰면 문서가 바뀐다.

**검색** (`search`)

카테고리와 커서는 있을 때만 넣어야 한다. 그래서 `filter` 목록을 미리 만들고, 요청은 블록 람다로 조립해 마지막에 `b`를 돌려준다.

```java
List<Query> filters = new ArrayList<>();
filters.add(Query.of(f -> f.term(t -> t.field("status").value("ON_SALE"))));
if (category != null) {
    filters.add(Query.of(f -> f.term(t -> t.field("category").value(category))));
}

SearchResponse<Map> response = client.search(b -> {
    b.index("products")
            .size(size + 1)
            .query(query -> query.bool(bool -> bool
                    .must(must -> must.multiMatch(mm -> mm.query(q).fields("name^3", "description")))
                    .filter(filters)))
            .sort(s -> s.score(o -> o.order(SortOrder.Desc)))
            .sort(s -> s.field(f -> f.field("productId").order(SortOrder.Asc)))
            .source(src -> src.filter(f -> f.includes("productId")));
    if (after != null) {
        b.searchAfter(after);   // after는 List<FieldValue>
    }
    return b;
}, Map.class);
```

ES에 가는 JSON은 다음과 같다.

```json
{
  "size": 21,
  "query": {
    "bool": {
      "must": { "multi_match": { "query": "루틴", "fields": ["name^3", "description"] } },
      "filter": [
        { "term": { "status": "ON_SALE" } },
        { "term": { "category": "HEALTH" } }
      ]
    }
  },
  "sort": [ { "_score": { "order": "desc" } }, { "productId": { "order": "asc" } } ],
  "_source": { "includes": ["productId"] },
  "search_after": [0.22232392, "aaaaaaaa-0000-4000-8000-000000000001"]
}
```

| 키 | 뜻 |
|---|---|
| `bool.must` | 맞아야 하고 점수에 반영된다 |
| `bool.filter` | 맞아야 하지만 점수에 영향이 없다 |
| `multi_match` | 여러 필드에서 찾는다. 검색어는 각 필드의 분석기로 잘린다. `name^3`은 이름 점수를 3배로 친다 |
| `term` | 검색어를 자르지 않고 정확히 비교한다. `keyword` 필드에 쓴다 |
| `search_after` | 이전 쪽 마지막 결과의 `sort` 값 뒤부터 준다 |

**결과 읽기**

`Map.class`는 `_source`를 `Map`으로 받는다는 뜻이다. 그래서 값은 `Object`로 나오고 형변환이 필요하다.

```java
List<Hit<Map>> hits = response.hits().hits();   // 결과가 없으면 빈 목록

Hit<Map> hit = hits.get(0);
String productId = (String) hit.source().get("productId");   // aaaaaaaa-0000-...
List<FieldValue> sort = hit.sort();                          // [0.22232392, aaaaaaaa-0000-...]
double score = sort.get(0).doubleValue();
String sortProductId = sort.get(1).stringValue();
```

**커서**

`[점수, productId]`를 JSON 배열로 만들어 Base64로 감싼다. `ObjectMapper`는 `tools.jackson.databind.ObjectMapper`, `JsonNode`는 `tools.jackson.databind.JsonNode`이고 빈으로 주입받는다(`OutboxRelay`와 같다).

```java
// hit → 커서: [0.22232392,"aaaaaaaa-0000-..."] → WzAuMjIy...
List<Object> values = List.of(sort.get(0).doubleValue(), sort.get(1).stringValue());
String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(values));

// 커서 → search_after 값
JsonNode node = objectMapper.readTree(Base64.getUrlDecoder().decode(cursor));
if (!node.isArray() || node.size() != 2 || !node.get(0).isNumber() || !node.get(1).isString()) {
    // 모양이 맞지 않는다
}
List<FieldValue> after = List.of(FieldValue.of(node.get(0).doubleValue()), FieldValue.of(node.get(1).stringValue()));
```

잘못된 커서는 세 가지로 실패하고, 모두 400 `INVALID_REQUEST`다.

| 커서 | 내용 | 실패 지점 |
|---|---|---|
| `not-a-cursor!!` | Base64가 아니다 | `decode`의 `IllegalArgumentException` |
| `YWJj` | `abc`, JSON이 아니다 | `readTree`의 `JacksonException`(`tools.jackson.core`) |
| `WzFd` | `[1]`, 배열 길이가 다르다 | 위 모양 검사 |

**자동완성** (`autocomplete`)

```java
SearchResponse<Map> response = client.search(b -> b
        .index("products")
        .size(10)
        .query(query -> query.bool(bool -> bool
                .must(must -> must.match(m -> m.field("name.autocomplete").query(q)))
                .filter(f -> f.term(t -> t.field("status").value("ON_SALE")))))
        .sort(s -> s.score(o -> o.order(SortOrder.Desc)))
        .sort(s -> s.field(f -> f.field("name.keyword").order(SortOrder.Asc)))
        .collapse(c -> c.field("name.keyword"))
        .source(src -> src.filter(f -> f.includes("name"))), Map.class);

List<String> names = response.hits().hits().stream()
        .map(hit -> (String) hit.source().get("name"))
        .toList();
```

`루틴 비타`로 찾으면 `루틴 비타민`은 걸리고 `비타민 루틴`은 걸리지 않는다. `collapse`는 같은 필드 값을 가진 hit를 하나로 묶으므로, `size(10)`이 서로 다른 이름 10개를 뜻한다. 받은 뒤 자바에서 중복을 빼면 같은 이름이 상위를 차지할 때 10개보다 적어진다. 두 번째 정렬 키가 없으면 점수가 같은 이름은 색인된 순서대로 나온다.

**실패 종류**

| 상황 | 예외 |
|---|---|
| ES가 멈춰 응답이 없다 | `SocketTimeoutException`(`IOException`), 약 2초 |
| ES가 꺼져 있다 | `ConnectException`(`IOException`) |
| 없는 인덱스나 잘못된 쿼리 | `ElasticsearchException`. `ex.error().type()` 예: `index_not_found_exception` |
| 버전 충돌 | `ElasticsearchException`, `ex.status()` = 409 |

**직접 확인하는 명령**

구현 중 ES 상태는 터미널에서 본다. 개발용 compose는 `localhost:9200`에 열려 있다.

```bash
E=http://localhost:9200

curl -s "$E/_cat/indices?v"                     # 인덱스 목록과 문서 수
curl -s "$E/_cat/aliases?v"                     # alias가 어느 인덱스를 가리키는지
curl -s "$E/products/_mapping?pretty"           # 매핑
curl -s "$E/products/_doc/{productId}?pretty"   # 문서 하나. _version이 상품 버전이다
curl -s -XPOST "$E/products/_refresh"           # 방금 쓴 문서를 바로 검색되게 한다

# 분석기 결과: [멀티, 비타민]
curl -s "$E/products/_analyze?pretty" -H 'Content-Type: application/json' \
  -d '{"analyzer":"korean","text":"멀티비타민"}'

# 검색
curl -s "$E/products/_search?pretty" -H 'Content-Type: application/json' \
  -d '{"query":{"match":{"name":"비타민"}}}'

# 처음부터 다시: alias가 가리키는 실제 인덱스를 지운다
curl -s -XDELETE "$E/products-{yyyyMMddHHmmss}"
```

문서를 쓴 직후에는 검색에 나오지 않는다. 기본 1초 주기의 refresh 뒤에 보인다. `_doc` 조회는 바로 된다.

**자주 나는 오류**

| 증상 | 원인 |
|---|---|
| `invalid_alias_name_exception` | alias 이름과 같은 일반 인덱스 `products`가 이미 있다. 문서를 먼저 써서 자동 생성된 것이다. `curl -XDELETE "$E/products"` 뒤 앱을 다시 시작한다 |
| `Unknown tokenizer type [nori_tokenizer]` | nori가 없는 이미지다. Dockerfile로 빌드한 이미지인지 확인한다 |
| 검색 결과가 0건 | refresh 전이거나, 쿼리 필드 이름이 매핑과 다르다. 위 `_analyze`로 잘린 조각을 비교한다 |
| `Fielddata access on the _id field is disallowed` | `_id`로 정렬했다. `productId`로 정렬한다 |

**3) search.infrastructure**

| 클래스 | 규격 |
|---|---|
| `ElasticsearchTimeoutConfig` | `@Configuration`. ES 응답 대기에 타임아웃을 건다. 코드는 표 아래에 있다 |
| `ProductIndexDefinition` | 인덱스를 만들 때 쓰는 설정·매핑 JSON(`src/main/resources/search/products-index.json`)을 읽어 준다. `ensureIndex`와 SUB-7 재색인이 같은 JSON을 쓰므로 한 곳에 둔다. JSON 내용은 아래 표다. JSON 내용을 돌려주는 메서드 하나(이름은 구현이 정한다). `search.infrastructure`에 둔다 |
| `ProductIndexer` | `@Component`. 상품 문서를 ES에 쓰는 유일한 곳. consumer가 쓰고, SUB-7 재색인도 같이 쓴다. `synchronized void ensureIndex()` — 인덱스 준비 / `void upsert(ProductUpsertedEvent e, String indexOrAlias)` — 문서 한 건 색인 / `@EventListener(ApplicationReadyEvent.class)` 메서드 — 앱 시작 때 `ensureIndex`를 부르고, 실패하면 `log.warn`만 남긴다. 준비가 끝났는지를 `volatile boolean` 필드에 둔다 |
| `ProductEventConsumer` | consumer `search.product-upserted`. `product-upserted` 이벤트를 받아 `indexer.upsert`를 부른다. `void onProductUpserted(String message)` |

`ElasticsearchTimeoutConfig`는 다음과 같다.

```java
@Configuration
public class ElasticsearchTimeoutConfig {

    @Bean
    public Rest5ClientBuilderCustomizer rest5ClientBuilderCustomizer(
            @Value("${spring.elasticsearch.socket-timeout}") Duration timeout) {
        return new Rest5ClientBuilderCustomizer() {
            @Override
            public void customize(Rest5ClientBuilder builder) {
            }

            @Override
            public void customize(RequestConfig.Builder builder) {
                builder.setResponseTimeout(Timeout.of(timeout));
                builder.setConnectionRequestTimeout(Timeout.of(timeout));
            }
        };
    }
}
```

| 타입 | 패키지 |
|---|---|
| `Rest5ClientBuilderCustomizer` | `org.springframework.boot.elasticsearch.autoconfigure` |
| `Rest5ClientBuilder` | `co.elastic.clients.transport.rest5_client.low_level` |
| `RequestConfig` | `org.apache.hc.client5.http.config` |
| `Timeout` | `org.apache.hc.core5.util` |

- **왜 필요한가**: Boot의 `socket-timeout`은 연결 설정에만 들어가서, ES 컨테이너를 멈추면 `client.search`가 9분이 넘도록 돌아오지 않았다. 요청 설정의 `responseTimeout`을 따로 넣어야 2초에 `SocketTimeoutException`이 난다. `connectionRequestTimeout`은 연결 풀이 빌 때까지 기다리는 시간이다. 이 설정으로 ES를 멈추고 동시 30건을 보내면 모두 약 2초에 끝났다.
- **왜 익명 클래스인가**: 인터페이스의 추상 메서드는 `customize(Rest5ClientBuilder)`이고, 필요한 것은 기본 메서드 `customize(RequestConfig.Builder)`다. 람다는 추상 메서드만 구현하므로 익명 클래스로 만들고 추상 메서드는 비워 둔다.
- **`setRequestConfigCallback`을 쓰지 않는 이유**: Boot가 이미 이 콜백으로 모든 customizer의 `customize(RequestConfig.Builder)`를 부르는데, 호출하면 그 콜백을 덮어쓴다.

`src/main/resources/search/products-index.json`은 다음과 같다.

```json
{
  "settings": {
    "analysis": {
      "char_filter": {
        "remove_whitespace": {
          "type": "pattern_replace",
          "pattern": "\\s+",
          "replacement": ""
        }
      },
      "filter": {
        "product_part_of_speech": {
          "type": "nori_part_of_speech",
          "stoptags": [
            "EP", "EF", "EC", "ETN", "ETM",
            "JKS", "JKC", "JKG", "JKO", "JKB", "JKV", "JKQ", "JX", "JC",
            "MAJ", "MM",
            "SP", "SSC", "SSO", "SC", "SE",
            "XPN", "XSA", "XSN", "XSV",
            "UNA", "NA", "VSV"
          ]
        }
      },
      "tokenizer": {
        "korean_tokenizer": {
          "type": "nori_tokenizer",
          "decompound_mode": "mixed"
        },
        "autocomplete_tokenizer": {
          "type": "edge_ngram",
          "min_gram": 2,
          "max_gram": 20
        }
      },
      "analyzer": {
        "korean": {
          "type": "custom",
          "tokenizer": "korean_tokenizer",
          "filter": ["product_part_of_speech", "lowercase"]
        },
        "autocomplete": {
          "type": "custom",
          "char_filter": ["remove_whitespace"],
          "tokenizer": "autocomplete_tokenizer",
          "filter": ["lowercase"]
        },
        "autocomplete_search": {
          "type": "custom",
          "char_filter": ["remove_whitespace"],
          "tokenizer": "keyword",
          "filter": ["lowercase"]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "productId": { "type": "keyword" },
      "shopId": { "type": "keyword" },
      "name": {
        "type": "text",
        "analyzer": "korean",
        "fields": {
          "keyword": { "type": "keyword" },
          "autocomplete": {
            "type": "text",
            "analyzer": "autocomplete",
            "search_analyzer": "autocomplete_search"
          }
        }
      },
      "description": { "type": "text", "analyzer": "korean" },
      "category": { "type": "keyword" },
      "price": { "type": "long" },
      "status": { "type": "keyword" }
    }
  }
}
```

| 구분 | 이름 | 뜻 |
|---|---|---|
| char_filter | `remove_whitespace` | 분석 전에 공백을 모두 지운다. 자동완성의 두 분석기가 함께 써서 상품명과 검색어의 띄어쓰기가 달라도 맞는다 |
| filter | `product_part_of_speech` | `stoptags`에 적은 품사를 버린다. 어미(`E…`)·조사(`J…`)·접사(`X…`)·기호(`S…`) 등 기본값에서 `IC`와 `MAG`만 뺀 목록이다. `E`나 `J` 같은 축약 태그를 쓰면 인덱스 생성이 400으로 실패한다 |
| tokenizer | `korean_tokenizer` | nori. `decompound_mode: mixed`는 복합명사의 원형과 조각을 모두 색인한다 |
| tokenizer | `autocomplete_tokenizer` | 앞부분 조각을 2~20자로 만든다. 자동완성이 2자 이상일 때만이라 한 글자 조각은 만들지 않는다. 공백을 지운 뒤에 자르므로 20자에 공백은 세지 않는다 |
| analyzer | `korean` | `product_part_of_speech`로 조사 등을 빼고 소문자로 바꾼다 |
| analyzer | `autocomplete` | 색인할 때 쓴다. `루틴 비타` → `루틴`, `루틴비`, `루틴비타` |
| analyzer | `autocomplete_search` | 검색할 때 쓴다. 공백을 지우고 `keyword`로 나누지 않으므로 ` 루틴 비타 ` → `루틴비타` |
| 필드 | `name` | `text`라 잘라서 색인한다. `fields`의 `name.keyword`와 `name.autocomplete`는 같은 값을 다른 방식으로 한 번 더 색인한 하위 필드이고, 문서에 따로 넣지 않는다. `name.keyword`는 이름을 통째로 저장해 자동완성의 `collapse`와 정렬이 쓴다 |
| 필드 | `productId`, `shopId`, `category`, `status` | `keyword`라 통째로 저장한다. 정확히 일치 비교와 정렬에 쓴다 |

**자동완성 분석기가 두 개인 이유**: `autocomplete`는 **색인할 때** 상품명 전체의 앞부분 조각을 만든다. `루틴 비타민`은 `루틴`, `루틴비`, `루틴비타`, `루틴비타민`이 된다. 검색어는 `autocomplete_search`로 나누지 않고 한 덩어리로 찾는다. 검색어까지 조각내면 `루틴 비타`가 `루틴`, `루틴비`, `루틴비타`로 쪼개져 `루틴 마스크팩`처럼 `루틴`으로 시작하는 문서가 모두 걸린다. `standard`로 공백에서 나누면 `루틴 비타`가 `루틴`, `비타`로 갈라져 `비타민 루틴`과 `루틴 마스크팩`까지 걸린다.

`ensureIndex()` 순서
1. 준비가 끝났다는 필드가 true면 끝. 아니면 alias `products`가 있는지 확인한다. 있으면 필드를 true로 두고 끝
2. 없으면 `products-{yyyyMMddHHmmss}`(현재 시각, `Clock` 기준) 인덱스를 `ProductIndexDefinition`의 JSON으로 만들고 alias `products`를 연결한 뒤 필드를 true로 둔다
- 존재하지 않는 alias 이름으로 문서를 쓰면 ES가 `products`라는 **일반 인덱스를 자동 생성**하고, 그 뒤로는 같은 이름의 alias를 만들지 못한다(`invalid_alias_name_exception`). 그래서 문서를 쓰기 전에 항상 준비를 끝낸다. 시작 이벤트(`ApplicationReadyEvent`) 때는 Kafka 리스너가 이미 돌고 있어 consumer가 먼저 도착할 수 있고, 앱이 뜰 때 ES가 꺼져 있었을 수도 있다.
- `synchronized`인 이유: 시작 이벤트와 consumer가 동시에 들어와 인덱스를 둘 만들면 alias 하나가 두 인덱스를 가리켜 쓰기가 실패한다. 앱 인스턴스 하나를 기준으로 한다.
- 앱이 뜰 때 ES가 꺼져 있으면 경고만 남기고 앱은 뜬다. 이때 검색은 503이고, ES가 돌아온 뒤 첫 상품 이벤트가 인덱스를 만든다. ES에 이미 인덱스가 있었다면 ES가 돌아오는 즉시 검색이 된다.
- 앱이 도는 중에 ES 데이터가 통째로 사라지면 준비 필드가 true라 일반 인덱스가 자동 생성된다. 이 경우 그 인덱스를 지우고 앱을 다시 시작한다.

`upsert(e, indexOrAlias)` 순서
1. `ensureIndex()`를 부른다. 실패하면 예외가 그대로 나가 재시도된다
2. 문서 본문: 매핑의 필드 이름 그대로 `productId`, `shopId`, `name`, `description`, `category`, `price`, `status`(문자열 그대로, 예 `ON_SALE`). `subscribable`·`version`은 문서 필드로 넣지 않는다
3. 문서 id = `e.productId().toString()`. 요청에 **`version = e.version()`, `versionType = EXTERNAL`**을 붙인다(REST로는 `PUT {indexOrAlias}/_doc/{id}?version={n}&version_type=external`). 등록 이벤트의 첫 버전 0도 받는다. ES는 저장된 버전이 **같거나 더 크면** 쓰기를 거부(409 `version_conflict_engine_exception`)하므로 순서가 뒤바뀐 이벤트·같은 이벤트의 중복이 문서를 덮어쓰지 못한다
4. 409는 "이미 같거나 더 최신"이므로 `log.debug` 후 정상 반환한다. 그 밖의 ES 오류·타임아웃은 그대로 던진다
- `IOException` 같은 검사 예외는 `RuntimeException`으로 감싸 던진다. `BusinessException`으로 감싸지 않는다(규칙 X의 consumer 모양).

`ProductEventConsumer.onProductUpserted(String message)` 순서
1. `eventEnvelopes.parse(message, ProductUpsertedEvent.class)` — JSON 오류는 `JacksonException`이라 재시도 없이 DLT(3-2)
2. `EventContext.run(typed.envelope(), () -> indexer.upsert(typed.payload(), "products"))`
- `inboxGuard.runOnce`를 쓰지 않는다. ES 쓰기 자체가 external version으로 멱등이고, `runOnce`는 DB 트랜잭션이라 그 안에서 ES를 부르면 안 되기 때문이다(규칙 X).

- `product-discontinued`는 따로 구독하지 않는다. 단종도 `product-upserted`(status DISCONTINUED)가 함께 발행되기 때문이다(3-3).
- ES가 내려가 있으면 consumer가 실패 → 3번 재시도 → DLT. 복구 후 DLT 재처리 API(`POST /admin/api/dlt/{topic}/{partition}/{offset}/replay`, 6-3 — DLT 레코드 하나를 원래 토픽으로 다시 보낸다)로 재처리한다.

**4) product — 검색 결과 요약 조회**

| 클래스 | 규격 |
|---|---|
| `ProductRepository` (product.domain) | `List<ProductListRow> findOnSaleByIds(Collection<UUID> ids)` — `findPublicFirstPage`와 같은 JPQL(`Product`·`Stock` JOIN, `ProductListRow` 생성자 식)에 조건 `p.id in :ids and p.status = ON_SALE`. 정렬하지 않는다 |
| `JpaProductRepository` (product.infrastructure) | 위 쿼리의 구현 |
| `ProductSummaryView` (product.api) | `record (UUID productId, UUID shopId, String name, String category, Money price, String thumbnailKey, boolean inStock, Instant createdAt)`. `category`는 `ProductCategory`의 이름이다(search는 `product.domain`을 참조할 수 없다). `inStock`은 `available > 0`. `ProductForCheckout`처럼 `static from(ProductListRow row)`로 만든다 |
| `ProductApi` (product.api) | `List<ProductSummaryView> getOnSaleSummaries(Collection<UUID> productIds)` — 판매 중인 상품의 요약만 돌려준다. 없거나 판매 중이 아닌 ID는 결과에서 빠지고 에러가 없다. 순서는 정하지 않는다(호출자가 맞춘다). `ProductSearchService.search`가 쓴다 |
| `ProductApiImpl` (product.application) | `@Transactional(readOnly = true)`. `findOnSaleByIds(productIds)`의 각 행을 `ProductSummaryView.from`으로 바꾼다 |

**5) search.application**

| 클래스 | 규격 |
|---|---|
| `SearchErrorCode` | 검색 에러 코드. `ErrorCode`를 구현하는 enum. `SEARCH_UNAVAILABLE`(503) — 값·메시지는 위 Z 표. domain을 생략하므로 `search.application`에 둔다(`ProductSearchService`가 던진다) |
| `ProductSearchResult` | 검색 결과 한 건. `record (UUID id, UUID shopId, String name, String category, long price, String thumbnailKey, boolean inStock, Instant createdAt)`. `ProductSummaryView`에서 만든다(`price`는 `Money.amount()`) |
| `ProductSearchService` | 검색·자동완성 요청을 ES 쿼리로 바꿔 실행한다. `CursorPage<ProductSearchResult> search(String q, String category, String cursor, int size)` / `List<String> autocomplete(String q)` |

`search(q, category, cursor, size)` 순서
1. `cursor`가 비어 있지 않으면 해석해 `search_after` 값 두 개(점수, `productId`)로 복원한다. 해석 실패는 400 `INVALID_REQUEST`다. 1-7의 `Cursor`는 `(createdAt, id)` 전용이라 쓸 수 없고, 여기서는 `[score, productId]` JSON 배열을 Base64 URL-safe(패딩 없음)로 감싼 값을 쓴다. **이 해석은 ES 호출을 감싸는 `try` 밖에서** 한다(안에서 하면 400이 503으로 바뀐다)
2. 별칭 `products`에 검색 요청을 보낸다: `query.bool.must = multi_match(q, fields [name^3, description])`, `query.bool.filter = [term status = ON_SALE, (category가 있으면 term category)]`(filter라 점수에 영향이 없다), `sort = [_score desc, productId asc]`, `size = size + 1`, `_source = ["productId"]`, 커서가 있으면 `search_after`. 동점을 가르는 두 번째 정렬 키로 `_id`가 아니라 매핑의 `productId`(keyword)를 쓴다(`_id` 정렬은 기본으로 막혀 있어 `illegal_argument_exception`이 난다)
3. 받은 hit가 `size`보다 많으면 다음 페이지가 있다. `size`개만 남기고, **`size`번째 hit**의 `sort` 배열(`[score, productId]`)을 1번의 형식으로 감싸 `nextCursor`에 넣는다. 없으면 `null`
4. 남긴 hit의 `_source.productId`를 모아 `productApi.getOnSaleSummaries(ids)`를 한 번 부른다. 결과를 hit 순서대로 놓아 `ProductSearchResult`로 바꾸고, 결과에 없는 ID(그사이 판매 중이 아니게 된 상품)는 뺀다. 3번의 `nextCursor`는 그대로 둔다
5. `new CursorPage<>(items, nextCursor)`로 반환한다
- ES 예외·타임아웃(`catch (IOException | ElasticsearchException e)`)은 `BusinessException(SEARCH_UNAVAILABLE)`(503)로 바꾼다. 4번 `getOnSaleSummaries`는 ES 호출을 감싸는 `try` 밖에서 부른다(안에서 부르면 DB 오류가 503으로 바뀐다)

`autocomplete(q)` 순서: `match` 쿼리(`name.autocomplete` 필드에 `q`) + `filter status = ON_SALE`, `sort = [_score desc, name.keyword asc]`, `collapse = name.keyword`, `size = 10`, `_source = ["name"]`을 `products`에 보내고, hit의 `_source.name`을 모아 `List<String>`으로 반환한다. 2자 미만은 컨트롤러 검증이 막으므로 서비스는 검사하지 않는다. 실패는 `search`와 같이 `SEARCH_UNAVAILABLE`.

**6) search.web**

| API | 요청 | 응답 |
|---|---|---|
| `GET /api/search/products?q=&category=&cursor=&size=` | `q` 1~50자 필수 | 200 `CursorPage<ProductSearchResponse(id, shopId, name, category, price, thumbnailUrl, inStock, createdAt)>`. 필드 이름이 `ProductSummaryResponse`와 같다 |
| `GET /api/search/autocomplete?q=` | 2~20자 | 200 `List<String>` |

- `SearchController`는 `ProductSearchService`만 부른다. `ProductSearchResponse.from(result, imageUrls)`가 `ImageUrls.toUrl(thumbnailKey)`로 `thumbnailUrl`을 만든다(`ProductSummaryResponse.from`과 같다). `q`의 길이 검증은 메서드 파라미터의 `@Size`·`@NotBlank`에 붙이고(클래스에 `@Validated` 금지, Part 2 공통 규칙 P-3), `size`는 기본 20·`@Min(1) @Max(50)`.

**7) common.security — SecurityConfig**

- 두 GET을 공개로 연다: `.requestMatchers(HttpMethod.GET, "/api/search/**").permitAll()`

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 이 단계에서 추가하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `productSearchDocOf(UUID productId)` → `ProductSearchDoc` | ES REST `GET {es 주소}/products/_doc/{productId}`(alias로 읽는다)를 JDK `HttpClient`로 보내 응답 JSON에서 한 번에 읽는다(요청 코드는 테스트에 쓰지 않는다): `found`, `_version`(external version이 그대로 저장된다), `_source.name`·`.description`·`.category`·`.price`·`.status`. `ProductSearchDoc`은 `support/`의 record `(boolean found, Long version, String name, String description, String category, Long price, String status)`이고, 404면 `found` = `false`에 나머지는 `null`이다. ES 주소는 `IntegrationTestSupport.es.getHttpHostAddress()`다(`es`는 `protected static`이고 `TestFixtures`는 같은 `support` 패키지라 그대로 읽는다) | `ProductSearchTest` 10·11·12번 |

이미 있는 헬퍼와 주입: `RegisterProductService`·`UpdateProductService`(주입), `outboxRelay.relayOnce()`(`int` 반환. `READY` outbox 행을 Kafka로 한 번 발행한다), `Eventually.await(Duration timeout, ThrowingRunnable assertion)`(단언을 100ms마다 다시 실행해 통과하면 끝나고, 시간이 다 되면 마지막 실패를 던진다), `ProductIndexer.upsert`(이 단계에서 만든다). `ProductUpsertedEvent` 필드 순서는 `(productId, shopId, name, description, category, price, status, subscribable, version)`(3-3)이다. 5·7번은 `AdjustStockService.adjust(memberId, shopId, productId, delta, reason)`(주입), `testFixtures.registerProductImage(memberId, shopId, productId)`(→ `ImageResult`, 저장소 업로드까지 한다), `testFixtures.hideProduct(productId)`(JDBC로 상태만 `HIDDEN`으로 바꾸고 이벤트를 내지 않는다), `StorageProperties`(주입, `publicBaseUrl()`)를 쓴다.
헬퍼를 쓰는 방법은 다음과 같다.
- 응답: `jsonPath`. 검색은 `$.items[i].id`·`.shopId`·`.name`·`.category`·`.price`·`.thumbnailUrl`·`.inStock`과 `$.nextCursor`(마지막 페이지는 `null`). 자동완성은 응답이 배열이라 `$.length()`와 `$[i]`. 에러 코드는 `$.code`.
- 색인 반영 대기: `Eventually.await(Duration.ofSeconds(10), …)`로 검색 결과를 기다린다(기본 refresh 주기 1초로 충분하다. 못 기다리면 `POST /products/_refresh`). "안 나온다"는 확인은 Eventually로 기다린 뒤 한 번 더 확인한다.

**ProductSearchTest 공통 규칙**: ES 문서는 테스트가 끝나도 지워지지 않으므로(DB 초기화 대상이 아니다) 케이스마다 아래에 굵게 적은 **검색어**를 따로 쓰고, 다른 케이스의 상품 이름은 "루틴"으로 시작하지 않게 한다. 검색어는 조각으로 나뉘어 OR로 맞으므로, 새 케이스의 이름·설명에 다른 케이스의 검색어 조각이 들어가면 그 문서가 걸려 `nextCursor`가 달라진다. 그래서 아래 검색어는 nori가 한 조각으로 두는 단어로 골랐다(`프로폴리스`는 `프로`·`폴리스`로 나뉘지만 다른 케이스에 두 조각이 없다). 검색 요청은 `GET /api/search/products?q=…`(Authorization 없이)이다.

**준비 A**(`ProductSearchTest`에서 10·11번을 뺀 모든 케이스가 쓴다): 판매자 1명, 가게 1개. 상품은 이름·설명을 정해야 하므로 `TestFixtures.registerProduct`(이름이 `product`로 고정)가 아니라 테스트가 `RegisterProductService.register(sellerId, shopId, new RegisterProductCommand(이름, 설명, ProductCategory.X, 가격, 10, false))`로 만든다(반환값이 상품 ID). 등록 뒤 `outboxRelay.relayOnce()`로 이벤트를 흘린다.

#### `search/application/ProductSearchTest` (MockMvc로 공개 API를 부른다)

**1. 등록 → 검색됨** (검색어 **마그네슘**, **수면**) — 준비: 준비 A로 상품 `P` = 이름 `"마그네슘 데일리 정"`, 설명 `"수면용 근육이완 보충제"`, `HEALTH`, 15000원. 동작: `relayOnce()` → Eventually로 `q=마그네슘` 조회.
- 응답(andExpect): 상태 200, `$.items.length()` = 1, `$.items[0].id` = `P`, `.shopId` = 가게 ID, `.name` = `"마그네슘 데일리 정"`, `.category` = `HEALTH`, `.price` = 15000, `jsonPath("$.nextCursor").value(nullValue())`.
- 위 `q=마그네슘`이 통과한 뒤 `q=수면` → 응답(andExpect): `$.items.length()` = 1, `$.items[0].id` = `P`.

**2. 관련도 정렬과 페이지** (검색어 **프로폴리스**)
- 준비: 준비 A로 상품 `A` = 이름 `"프로폴리스 스프레이"`, 설명 `"목 건강 케어"` / 상품 `B` = 이름 `"목 케어 스프레이"`, 설명 `"프로폴리스 함유"`(둘 다 `HEALTH`, `ON_SALE`).
- `q=프로폴리스&size=20`(준비 A의 `relayOnce()` 뒤 Eventually(10초) 안에서 `$.items.length()` = 2가 될 때까지 기다린다) — 응답(andExpect): `$.items.length()` = 2, `$.items[0].id` = `A`, `$.items[1].id` = `B`, `jsonPath("$.nextCursor").value(nullValue())`.
- `q=프로폴리스&size=1`(`.andReturn()`으로 받는다) — 응답(andExpect): `$.items.length()` = 1, `$.items[0].id` = `A`, `jsonPath("$.nextCursor").value(notNullValue())`.
- 위 `size=1` 응답 본문(andReturn 후)에서 `JsonPath.read(본문, "$.nextCursor")`로 꺼낸 값을 `cursor=`로 넘겨 `q=프로폴리스&size=1` — 응답(andExpect): `$.items.length()` = 1, `$.items[0].id` = `B`, `jsonPath("$.nextCursor").value(nullValue())`.

**3. 카테고리 필터** (검색어 **아로마**) — 준비: 준비 A로 `X1` = 이름 `"아로마 영양제"`, `HEALTH` / `X2` = 이름 `"아로마 크림"`, `BEAUTY`(둘 다 설명 `"향기 케어"`, `ON_SALE`).
- `q=아로마&category=HEALTH`(준비 A의 `relayOnce()` 뒤 Eventually(10초) 안에서 읽는다. 아래 두 조회는 이 조회가 통과한 뒤 읽는다) — 응답(andExpect): `$.items.length()` = 1, `$.items[0].id` = `X1`.
- `q=아로마`(`category` 생략) — 응답(andExpect): `$.items.length()` = 2, `$.items[*].id`가 `X1`·`X2`(`containsInAnyOrder`, 순서 무관).
- `q=아로마&category=FOOD` — 응답(andExpect): `jsonPath("$.items").value(hasSize(0))`.

**4. 동점은 `productId` 오름차순** (검색어 **라벤더**) — 준비: 준비 A로 이름 `"라벤더 세트"`, 설명 `"동일한 설명"`이 같은 상품 `C1`, `C2`. `lo`·`hi` = `Stream.of(C1.toString(), C2.toString()).sorted().toList()`의 첫째·둘째 원소.
- `q=라벤더&size=1`(준비 A의 `relayOnce()` 뒤 Eventually(10초) 안에서 두 상품이 모두 색인될 때까지 기다린 뒤 `.andReturn()`으로 받는다) — 응답(andExpect): `$.items[0].id` = `lo`, `jsonPath("$.nextCursor").value(notNullValue())`.
- 그 응답 본문(andReturn 후)에서 `JsonPath.read(본문, "$.nextCursor")`로 꺼낸 값을 `cursor=`로 넘긴 다음 쪽 — 응답(andExpect): `$.items[0].id` = `hi`, `jsonPath("$.nextCursor").value(nullValue())`.

**5. 사진과 품절은 이벤트 없이 상품 DB 값으로 나온다** (검색어 **콜라겐**) — 준비: 준비 A로 상품 `P` = 이름 `"콜라겐 파우더"`, 설명 `"피부 보습 보충제"`, `BEAUTY`, 30000원, 재고 10. `relayOnce()` → Eventually로 `q=콜라겐`이 1건이 될 때까지 기다린다. 동작: `image = testFixtures.registerProductImage(sellerId, shopId, P)` → `adjustStockService.adjust(sellerId, shopId, P, -10, "재고 정리")` → `relayOnce()` 없이 `q=콜라겐`(두 동작 모두 `product-upserted`를 내지 않는다).
- 응답(andExpect): 상태 200, `$.items[0].id` = `P`, `$.items[0].thumbnailUrl` = `storageProperties.publicBaseUrl() + "/" + image.objectKey()`, `$.items[0].inStock` = false.

**6. 숨김·단종은 검색에서 빠진다** (검색어 **오메가**) — 준비: 준비 A로 상품 `P` = 이름 `"오메가 쓰리 캡슐"`, 상태를 바꾸지 않는 비교 상품 `Q` = 이름 `"오메가 듀얼 캡슐"`(둘 다 설명 `"혈행 개선 보충제"`, `HEALTH`, 22000원). 아래를 차례로 하고 매번 `relayOnce()` 후 Eventually로 `q=오메가&size=1`을 확인한다. `P`가 빠지는 것만 보면 DB 조회가 걸러 주어 이벤트 없이도 통과하므로 `nextCursor`를 함께 본다. `nextCursor`는 ES가 `status` 필터를 거쳐 돌려준 건수로 정해져, ES에 반영된 상태를 드러낸다. 점수 순서에 기대지 않도록 두 상품이 모두 나오는 단계에서는 `items[0].id`를 확인하지 않는다.
- 등록 직후: 응답(andExpect): `$.items.length()` = 1, `jsonPath("$.nextCursor").value(notNullValue())`.
- `updateProductService.changeStatus(sellerId, shopId, P, ProductStatus.HIDDEN)` 후: 응답(andExpect): `$.items.length()` = 1, `$.items[0].id` = `Q`, `jsonPath("$.nextCursor").value(nullValue())`.
- `changeStatus(…, ON_SALE)` 후: 응답(andExpect): `$.items.length()` = 1, `jsonPath("$.nextCursor").value(notNullValue())`.
- `changeStatus(…, DISCONTINUED)` 후: 응답(andExpect): `$.items.length()` = 1, `$.items[0].id` = `Q`, `jsonPath("$.nextCursor").value(nullValue())`.

**7. DB에서 판매 중이 아닌 상품은 ES에 반영되기 전에도 빠진다** (검색어 **히알루론**) — 준비: 준비 A로 상품 `P1` = 이름 `"히알루론 앰플"`, `P2` = 이름 `"히알루론 크림"`(둘 다 설명 `"보습 케어"`, `BEAUTY`). `relayOnce()` → Eventually로 `q=히알루론`이 2건이 될 때까지 기다린다. 동작: `testFixtures.hideProduct(P1)` → `relayOnce()` 없이 `q=히알루론`.
- 응답(andExpect): 상태 200, `$.items.length()` = 1, `$.items[0].id` = `P2`.

**8. 자동완성** (검색어 **루틴**) — 준비: 준비 A로 상품 4개, 모두 `HEALTH`.

| 상품 | 이름 | 상태 |
|---|---|---|
| A | `"루틴 비타민"` | `ON_SALE` |
| B | `"루틴 마스크팩"` | `ON_SALE` |
| C | `"비타민 루틴"` | `ON_SALE` |
| D | `"루틴 오일"` | 등록 후 `changeStatus(…, HIDDEN)` |

동작: `relayOnce()` → Eventually로 `GET /api/search/autocomplete?q=루틴`.
- 응답(andExpect): 상태 200, `jsonPath("$[*]").value(containsInAnyOrder("루틴 비타민", "루틴 마스크팩"))`. 순서는 보지 않지만 요소가 더 있거나 모자라면 실패하므로, `"비타민 루틴"`(C)과 `"루틴 오일"`(D)이 없는 것까지 확인된다.

**9. 자동완성은 최대 10개** (검색어 **젤리맛**) — 준비: 준비 A로 이름 `"젤리맛 01"` ~ `"젤리맛 12"`인 `ON_SALE` 상품 12개. 동작: `relayOnce()` → Eventually로 `GET /api/search/autocomplete?q=젤리맛`. 요청을 `.andReturn()`으로 받는다.
- 응답(andExpect): 상태 200, 응답 본문 배열의 `$.length()` = 10.
- 응답 본문(andReturn 후): `JsonPath.read(본문, "$")`로 꺼낸 `List<String>`의 모든 원소가 `startsWith("젤리맛 ")`.

**10. 순서 역전: 오래된 버전이 늦게 와도 최신 유지** (검색어 없음. `X`는 DB에 없는 상품이라 검색 API 결과에서는 빠지므로 ES 문서로만 확인한다) — 준비: 새 `productId` `X`와 임의의 `shopId`(`Ids.newId()`)로 `ProductUpsertedEvent` 두 개를 직접 만든다(`category` = `"ETC"`, `status` = `"ON_SALE"`, `subscribable` = false).
- `v3` = 이름 `"순서역전 버전삼"`, 설명 `"세 번째 내용"`, 가격 3000, `version` 3.
- `v2` = 이름 `"순서역전 버전이"`, 설명 `"두 번째 내용"`, 가격 2000, `version` 2.
- 동작: `productIndexer.upsert(v3, "products")` → `productIndexer.upsert(v2, "products")`.
- 예외: 두 번째 `upsert` 호출이 예외 없이 끝난다(`assertThatCode(...).doesNotThrowAnyException()`).
- ES(productSearchDocOf(X), 두 번째 `upsert`가 끝난 뒤): `version()` = 3, `name()` = `"순서역전 버전삼"`.

**11. 같은 이벤트 두 번** (검색어 없음. 10번과 같은 이유로 ES 문서로만 확인한다) — 준비: 임의의 `shopId`(`Ids.newId()`)로 `ProductUpsertedEvent` `Y`를 직접 만든다. `Y` = 이름 `"중복이벤트 상품"`, 설명 `"같은 이벤트 검증"`, 가격 1000, `version` 1. 동작: `productIndexer.upsert(Y, "products")`를 두 번 호출.
- 예외: 두 번째 `upsert` 호출도 예외가 없다(`assertThatCode(...).doesNotThrowAnyException()`).
- ES(productSearchDocOf(Y.productId()), 두 번째 `upsert`가 끝난 뒤): `version()` = 1.

**12. 단종 상품의 ES 문서는 남는다** (검색어 없음) — 준비: 준비 A로 상품 `P` = 이름 `"단종문서 캡슐"`, 설명 `"혈행 개선 보충제"`, `HEALTH`, 22000원. 동작: `changeStatus(sellerId, shopId, P, ProductStatus.DISCONTINUED)` → `relayOnce()` → Eventually로 `productSearchDocOf(P).status()`가 `DISCONTINUED`가 될 때까지 기다린다.
- ES(productSearchDocOf(P)): `found()` = true, `status()` = `DISCONTINUED`.

**13. 복합어의 일부로 찾는다** (검색어 **미네랄**) — 준비: 준비 A로 상품 `P` = 이름 `"멀티미네랄 정"`, 설명 `"하루 한 알"`, `HEALTH`, 18000원. 동작: `relayOnce()` → Eventually로 `q=미네랄`.
- 응답(andExpect): 상태 200, `$.items.length()` = 1, `$.items[0].id` = `P`.

**14. 자동완성은 두 단어를 이어서 맞춘다** (검색어 **허브 티**) — 준비: 준비 A로 이름 `"허브 티백"`, `"허브 오일"`, `"티백 허브"`인 `ON_SALE` 상품 3개. 동작: `relayOnce()` → Eventually로 `GET /api/search/autocomplete?q=허브` 가 2건이 될 때까지 기다린 뒤 `GET /api/search/autocomplete?q=허브 티`.
- 응답(andExpect): 상태 200, `$.length()` = 1, `$[0]` = `"허브 티백"`.

**15. 자동완성은 이름이 같은 상품을 하나로 묶어 10개를 채운다** (검색어 **쿠키맛**) — 준비: 준비 A로 이름 `"쿠키맛"`인 `ON_SALE` 상품 12개와 이름 `"쿠키맛 09"`부터 `"쿠키맛 01"`까지 거꾸로 등록한 `ON_SALE` 상품 9개. 이름이 짧은 쪽의 점수가 높아, 묶지 않으면 `"쿠키맛"`이 상위 10건을 모두 차지한다. 차례로 등록하면 이름 정렬 없이도 통과하므로 거꾸로 등록한다. 동작: `relayOnce()` → Eventually로 `GET /api/search/autocomplete?q=쿠키맛`이 10건이 될 때까지 기다린다. 요청을 `.andReturn()`으로 받는다.
- 응답(andExpect): 상태 200, 응답 본문 배열의 `$.length()` = 10.
- 응답 본문(andReturn 후): `JsonPath.read(본문, "$")`로 꺼낸 `List<String>`이 `"쿠키맛"`, `"쿠키맛 01"`, … `"쿠키맛 09"`를 이 순서로 담는다(`containsExactly`).

**16. 자동완성은 공백을 무시한다** (검색어 **캐모마일**) — 준비: 준비 A로 이름 `"캐모마일 차"`, `"캐모마일차 세트"`, `"캐모마일 오일"`인 `ON_SALE` 상품 3개. 동작: `relayOnce()` → Eventually로 `GET /api/search/autocomplete?q=캐모마일`이 3건이 될 때까지 기다린 뒤 `q=캐모마일차`와 `q=캐모마일 차`를 각각 조회한다.
- 두 응답 모두(andExpect): 상태 200, `jsonPath("$[*]").value(containsInAnyOrder("캐모마일 차", "캐모마일차 세트"))`.

**17. nori가 감탄사·부사로 판단한 이름도 찾는다** (검색어 **마스크팩**) — 준비: 준비 A로 상품 `P` = 이름 `"마스크팩 세트"`, 설명 `"보습 케어"`, `BEAUTY`, 12000원. 동작: `relayOnce()` → Eventually로 `q=마스크팩`. 8번의 `"루틴 마스크팩"` 문서도 ES에서 걸리므로 `nextCursor`는 확인하지 않는다. 그 문서의 상품은 DB에 없어 응답에서 빠진다.
- 응답(andExpect): 상태 200, `$.items.length()` = 1, `$.items[0].id` = `P`.

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

**ES가 멈춰도 검색만 실패한다** — 준비: 판매자·가게·`registerProduct(sellerId, shopId)`로 상품 `P` 하나. 동작: `es.getDockerClient().pauseContainerCmd(es.getContainerId()).exec()`로 ES 컨테이너를 멈추고, `finally`에서 반드시 `unpauseContainerCmd`로 되돌린다. 아래 다섯 확인은 모두 ES가 멈춘 동안(`unpause` 전에) 한다.
- `GET /api/search/products?q=product` — 응답(andExpect): 상태 503, `$.code` = `SEARCH_UNAVAILABLE`. 반환값(Instant.now() 두 번의 차이): 요청 직전과 응답 직후에 재서 `Duration.between(직전, 직후)`가 10초보다 짧다.
- `GET /api/search/autocomplete?q=pr` — 응답(andExpect): 상태 503, `$.code` = `SEARCH_UNAVAILABLE`.
- `GET /api/products`(DB, Authorization 없이) — 응답(andExpect): 상태 200, `$.items.length()` = 1, `$.items[0].id` = `P`.
- 상품 등록 `POST /api/shops/{shopId}/products`(판매자 토큰, 새 `Idempotency-Key`, 본문은 `RegisterProductRequest`) — 응답(andExpect): 상태 201.
- 헬스 — 반환값(`HealthEndpoint` 주입, `org.springframework.boot.health.actuate.endpoint`): `healthEndpoint.health().getStatus()` = `Status.UP`.

#### 수동 확인: ES가 꺼진 채 앱 기동

`@EventListener`가 이미 끝난 테스트 컨텍스트에서는 준비 필드를 되돌릴 수 없어 로컬에서 한 번 확인한다. 로컬 ES에 인덱스가 없는 상태로 시작한다(`docker compose -f docker/docker-compose.yml down elasticsearch` 뒤 `docker volume rm myroutine_elasticsearch_data`).

| 동작 | 확인 |
|---|---|
| ES를 띄우지 않고 앱 기동 | 로그: `ensureIndex` 실패의 `WARN` 한 줄, 앱은 뜬다 |
| `curl -s -o /dev/null -w '%{http_code}' localhost:8080/api/products` | 명령 출력: `200` |
| `curl -s -o /dev/null -w '%{http_code}' 'localhost:8080/api/search/products?q=x'` | 명령 출력: `503` |
| `docker compose -f docker/docker-compose.yml up -d --wait elasticsearch` 뒤 상품 하나 등록 | 몇 초 안에 `curl -s localhost:9200/_alias/products`가 `products-{14자리 숫자}` 키를 돌려준다 |

**리뷰 때 물어볼 것**
- 검색 결과와 DB가 잠시 다를 수 있는데 괜찮은가?
- external version이 없다면 이벤트 순서 역전을 어떻게 막았을까?

---

## 5-3. 1:1 문의 (S)

**목표**: 회원 문의(답변 전 수정·삭제), 관리자 답변.

**정책 (이 단계에서 정함)**
- 제재된 회원은 로그인부터 막히므로(1-4) 문의도 쓸 수 없다. 제재 회원 문의 허용은 추가 항목으로 둔다(FR-MEM-07 "문의 외 기능 사용 불가"의 문의 예외는 이번 범위에서 뺀다).
- 본인 목록은 최신순, 관리자 목록은 오래된 순(답변 대기열)이다. 관리자 목록은 `status`를 생략하면 전체다.
- 관리자 목록은 문의 본문·작성자(ID·이메일·닉네임)·답변을 함께 내려줘 관리자 상세 API를 두지 않는다. 본인에게는 답변한 관리자를 내려주지 않는다.
- 본인은 답변 전(`OPEN`)인 문의만 수정·삭제할 수 있다. 삭제는 행을 지운다(상태를 두지 않는다). 답변이 달린 문의는 기록으로 남아 수정·삭제하면 409 `INQUIRY_ALREADY_ANSWERED`다.
- 문의 하나에 답변은 하나다(추가 답변·재문의 없음). 문의를 주문·상품과 연결하는 필드도 없다(필요하면 본문에 적는다).

### 할 일

**구현 순서**: 절 번호 순서대로 만든다. 1) 마이그레이션 → 2) `member.domain` → 3) `member.infrastructure` → 4) `member.application` → 5) `member.web` → 테스트.

새 모듈이 아니라 기존 member 모듈(`member.domain`·`member.application`·`member.web`)에 더한다. Part 5 모듈 의존 표(규칙 Y)에 member가 없는 이유다.

**1) 마이그레이션** — `member.inquiry`: `id`, `member_id fk_inquiry_member`, `title varchar(100) X`, `content text X`, `status varchar(20) X ck IN ('OPEN','ANSWERED')`, `version`, `created_at`, `updated_at`, 인덱스 `(member_id, created_at DESC, id DESC)`, `(status, created_at, id)` / `member.inquiry_answer`: `id`, `inquiry_id uk_inquiry_answer_inquiry fk`, `admin_id uuid X`, `content text X`, `created_at`

**2) member.domain**

| 클래스 | 규격 |
|---|---|
| `InquiryStatus` | 상태 enum. 허용 전이: `OPEN → {ANSWERED}`, `ANSWERED → {}`. `ShopStatus`처럼 `transitTo(to)`가 허용 밖이면 `INVALID_STATE_TRANSITION`(409)을 던진다(Part 1 규칙 C). 그래서 이미 `ANSWERED`인 문의에 다시 답변하면 409다. `Inquiry`가 쓴다 |
| `Inquiry` | entity. 위 `member.inquiry` 컬럼과 1:1(`@Version` 포함). `static Inquiry write(UUID memberId, String title, String content)` → `id = Ids.newId()`, `status = OPEN`. `InquiryAnswer answer(UUID adminId, String content)` → `status = status.transitTo(ANSWERED)`로 상태를 바꾸고 `InquiryAnswer.write(id, adminId, content)`로 만든 답변을 반환한다. `void edit(String title, String content)` → `validateEditable()` 뒤 `null`이 아닌 값만 바꾼다(Part 1 규칙 E). `void validateEditable()` → 상태가 `OPEN`이 아니면 `BusinessException(INQUIRY_ALREADY_ANSWERED)`(수정·삭제는 상태 전이가 아니라 전이표를 거치지 않는다. 삭제는 서비스가 `validateEditable`을 그대로 부른다). `InquiryService`가 쓴다 |
| `InquiryAnswer` | entity. 위 `member.inquiry_answer`와 1:1(수정이 없어 `BaseTimeEntity` 없이 `@CreatedDate Instant createdAt`만 둔다, Part 1 규칙 B). `static InquiryAnswer write(UUID inquiryId, UUID adminId, String content)` → `id = Ids.newId()`. `Inquiry`는 답변을 필드로 들지 않고(`@OneToMany`·`@OneToOne` 없이 UUID로만 참조) `answer`가 만들어 반환하며, `InquiryService.answer`가 `InquiryAnswerRepository.save`로 저장한다. 같은 문의에 동시에 두 답변이 오면 `uk_inquiry_answer_inquiry` 위반이 최후 방어선이다(규칙 D). `answer`가 쓴다 |
| `MemberErrorCode` | 기존 enum에 `INQUIRY_NOT_FOUND`·`INQUIRY_ALREADY_ANSWERED`를 추가한다(값·메시지는 위 Z 표). `InquiryService`·`Inquiry`가 던진다 |
| `InquiryRepository` | `Inquiry save(Inquiry inquiry)`(`InquiryService.write`가 새 문의를 저장한다), `Optional<Inquiry> findByIdAndMemberId(UUID id, UUID memberId)`, `Optional<Inquiry> findByIdForUpdate(UUID id)`(`answer` 전용. 2-8 `OrderRepository.findByIdForUpdate`와 같은 모양으로 `@Lock(PESSIMISTIC_WRITE)` + `@Query`), `void delete(Inquiry inquiry)`, 본인 목록 keyset(`member_id`, `created_at desc, id desc`, 첫·다음 페이지), 관리자 목록 keyset(`created_at`, `id` 오름차순, 첫·다음 페이지. `status`로 거르는 것과 전체 두 벌). 이름·인자는 권장안으로 `OrderRepository.findByMemberFirstPage(UUID memberId, Limit limit)`·`findByMemberNextPage(UUID memberId, Instant cursorCreatedAt, UUID cursorId, Limit limit)`와 같은 모양이다: `findByMemberFirstPage`·`findByMemberNextPage`(최신순), 관리자용 `findByStatusFirstPage(InquiryStatus status, Limit limit)`·`findByStatusNextPage(InquiryStatus status, Instant cursorCreatedAt, UUID cursorId, Limit limit)`와 `status`를 생략했을 때의 `findAllFirstPage(Limit limit)`·`findAllNextPage(Instant cursorCreatedAt, UUID cursorId, Limit limit)`(모두 오래된 순). 인덱스는 위 마이그레이션. `InquiryService`가 쓴다 |
| `InquiryAnswerRepository` | `InquiryAnswer save(InquiryAnswer answer)`, `Optional<InquiryAnswer> findByInquiryId(UUID inquiryId)`(상세 조회가 답변을 읽는다), `List<InquiryAnswer> findByInquiryIdIn(Collection<UUID> inquiryIds)`(목록 한 페이지의 답변을 한 번에 읽는다. 문의마다 읽으면 N+1). `InquiryService`가 쓴다 |
| `MemberRepository` | 기존 인터페이스에 `List<Member> findAllByIdIn(Collection<UUID> ids)`를 추가한다. 관리자 목록 한 페이지의 작성자를 한 번에 읽는다(`CartItemRepository.findAllByIdInAndCartId`와 같은 이름 규칙의 파생 쿼리라 `JpaMemberRepository`에 구현을 더하지 않는다). `InquiryService`가 쓴다 |

**3) member.infrastructure**

| 클래스 | 규격 |
|---|---|
| `JpaInquiryRepository`·`JpaInquiryAnswerRepository` | `InquiryRepository`·`InquiryAnswerRepository`의 Jpa 구현 |

**4) member.application**

| 클래스 | 규격 |
|---|---|
| `InquiryResult` | 본인 목록 항목. `record (UUID id, String title, InquiryStatus status, Instant createdAt, Instant answeredAt)`. `answeredAt`은 답변의 `createdAt`이고 답변 전이면 `null`이다(`findByInquiryIdIn`으로 채운다). `InquiryService`의 반환이다 |
| `InquiryDetailResult` | 상세. `record (UUID id, String title, String content, InquiryStatus status, Instant createdAt, Answer answer)`. 중첩 `record Answer(String content, Instant createdAt)`이고 답변이 없으면 `answer`는 `null`이다(`adminId`는 내려주지 않는다). `InquiryService`의 반환이다 |
| `AdminInquiryResult` | 관리자 목록 항목. `record (UUID id, UUID memberId, String memberEmail, String memberNickname, String title, String content, InquiryStatus status, Instant createdAt, Answer answer)`. 중첩 `record Answer(UUID adminId, String content, Instant createdAt)`이고 답변이 없으면 `null`이다. 답변은 `findByInquiryIdIn`으로, 작성자 이메일·닉네임은 페이지의 `member_id`로 `MemberRepository.findAllByIdIn`에서 한 번에 읽는다(같은 모듈이라 허용). `InquiryService`의 반환이다 |

| 클래스 | 메서드 |
|---|---|
| `InquiryService` | 메서드 이름은 권장안: `UUID write(UUID memberId, ...)`(→ `inquiryId`) / `CursorPage<InquiryResult> getMine(UUID memberId, String cursor, int size)` / `InquiryDetailResult getMine(UUID memberId, UUID inquiryId)`(답변 포함. `findByIdAndMemberId`, 남의 문의·없는 문의 모두 404 `INQUIRY_NOT_FOUND`) / `CursorPage<AdminInquiryResult> getForAdmin(InquiryStatus status, String cursor, int size)`(관리자. `status`가 `null`이면 전체) / `@Transactional void answer(UUID adminId, UUID inquiryId, String content)`(`findByIdForUpdate`로 읽는다. 문의가 없으면 404 `INQUIRY_NOT_FOUND`. `inquiry.answer(adminId, content)`가 반환한 `InquiryAnswer`를 `InquiryAnswerRepository.save`로 저장한다) / `@Transactional InquiryDetailResult edit(UUID memberId, UUID inquiryId, EditInquiryCommand command)`(`findByIdAndMemberId`로 읽고, 남의 문의·없는 문의는 404 `INQUIRY_NOT_FOUND`. `inquiry.edit(title, content)`가 상태를 검사하고 바꾼다. `OPEN`만 수정되므로 반환의 `answer`는 항상 `null`) / `@Transactional void delete(UUID memberId, UUID inquiryId)`(`findByIdAndMemberId`로 읽고, 남의 문의·없는 문의는 404 `INQUIRY_NOT_FOUND`. `inquiry.validateEditable()`을 부른 뒤 `InquiryRepository.delete`) |

| 클래스 | 규격 |
|---|---|
| `EditInquiryCommand` | `record (String title, String content)`. 둘 다 선택이고 `null`이면 바꾸지 않는다. `InquiryService.edit`의 입력 |

- 목록의 답변은 한 페이지의 문의 ID로 `findByInquiryIdIn`을 한 번 부르고, 결과를 `Map<UUID, InquiryAnswer>`(키는 `inquiryId`, 값은 그 문의의 답변)로 만들어 문의마다 `get`한다. 답변이 없는 문의는 키가 없어 `null`이다. 문의 하나에 답변은 하나(`uk_inquiry_answer_inquiry`)라 `Collectors.toMap`이 중복 키로 실패하지 않는다. 관리자 목록의 작성자(`memberId → Member`)도 같은 방식이다. JOIN으로 읽지 않는 이유: `Inquiry`가 답변을 연관으로 들지 않아(Part 1 규칙 B) JOIN은 엔티티 조인과 projection record가 필요하고, 목록 쿼리가 6벌(본인/관리자 × 첫·다음 쪽 × `status` 유무)이라 전부에 JOIN과 projection이 붙는다. IN 조회는 쿼리가 목록 1개 + 연관 종류당 1개(본인 1+1, 관리자 1+2)로 페이지 크기와 무관해서 행마다 읽는 N+1이 아니고, `SellerOrderQueryService`의 `findLinesByShopOrderIdIn`과 같은 방식이다
- **수정·삭제와 답변의 경합**: `answer`는 문의 행을 잠그고 읽고(`findByIdForUpdate`), `edit`·`delete`는 잠그지 않고 읽는다. 수정은 `answer`와 같은 행을 UPDATE하므로 `answer`가 먼저 잠그면 수정의 UPDATE가 풀리길 기다렸다가 버전 충돌(409 `CONFLICT_RETRY`)이고, `answer`가 커밋한 뒤에 읽은 수정은 `validateEditable`이 409 `INQUIRY_ALREADY_ANSWERED`다. 수정이 먼저 커밋되면 `answer`는 바뀐 문의에 그대로 답한다. 삭제는 다음과 같다. ① 삭제가 먼저 커밋되면 `answer`의 잠금 조회가 행을 못 찾아 404 `INQUIRY_NOT_FOUND`다. ② `answer`가 먼저 잠그면 `delete`의 `DELETE ... WHERE id = ? AND version = ?`가 잠금이 풀리길 기다렸다가 버전이 올라가 0행이 되어 `OptimisticLockingFailureException`(409 `CONFLICT_RETRY`)이고, `answer`가 커밋한 뒤에 읽은 `delete`는 `validateEditable`이 409 `INQUIRY_ALREADY_ANSWERED`다. 잠금이 없으면 `answer`가 읽은 뒤 삭제가 커밋되고, `answer`의 답변 INSERT가 `fk` 위반(`DataIntegrityViolationException`, 500)으로 끝난다

- 목록 정렬은 인덱스 `(member_id, created_at DESC, id DESC)`·`(status, created_at, id)`에 맞춘다. `status`를 생략한 관리자 전체 목록은 어느 인덱스도 `ORDER BY created_at, id`를 받쳐 주지 못하지만(`status`가 인덱스 맨 앞이다) 관리자 문의 목록 규모라 별도 인덱스를 두지 않는다. 본인 목록은 최신순(`ORDER BY created_at DESC, id DESC`). 관리자 목록은 오래된 문의가 먼저(`ORDER BY created_at, id`)이고 다음 페이지 조건은 `created_at > :cursorCreatedAt OR (created_at = :cursorCreatedAt AND id > :cursorId)`다. 둘 다 `Limit.of(size + 1)`로 읽어 `size`보다 많이 받으면 `size`번째 항목의 `new Cursor(createdAt, id).encode()`를 `nextCursor`로 하는 커서 페이징이다

**5) member.web**

| API | 권한 | 요청 | 응답 |
|---|---|---|---|
| `POST /api/inquiries` | 로그인 | `@NotBlank @Size(max=100) title`, `@NotBlank @Size(max=2000) content` | 201 `{inquiryId}` |
| `GET /api/inquiries?cursor=&size=` | 본인 | `size`는 기본 20·`@Min(1) @Max(50)` | 200 `CursorPage<InquiryResponse(id, title, status, createdAt, answeredAt)>`, 최신순 |
| `GET /api/inquiries/{id}` | 본인 | | 200 `InquiryDetailResponse(id, title, content, status, createdAt, answer)`. `answer`는 `{content, createdAt}` 또는 `null`. 남의 문의 → 404 |
| `GET /admin/api/inquiries?status=&cursor=&size=` | ADMIN | `InquiryStatus status`(생략 가능), `size`는 기본 20·`@Min(1) @Max(50)` | 200 `CursorPage<AdminInquiryResponse(id, memberId, memberEmail, memberNickname, title, content, status, createdAt, answer)>`, 오래된 순. `answer`는 `{adminId, content, createdAt}` 또는 `null` |
| `PATCH /api/inquiries/{id}` | 본인 | `EditInquiryRequest(@Pattern(regexp = ".*\\S.*") @Size(max=100) title, @Pattern(regexp = ".*\\S.*") @Size(max=2000) content)`, 둘 다 선택(Part 1 규칙 E) | 200 `InquiryDetailResponse`(`answer`는 항상 `null`). 남의 문의·없는 문의 → 404 `INQUIRY_NOT_FOUND`, 답변이 있으면 409 `INQUIRY_ALREADY_ANSWERED` |
| `DELETE /api/inquiries/{id}` | 본인 | | 204. 남의 문의·없는 문의 → 404 `INQUIRY_NOT_FOUND`, 답변이 있으면 409 `INQUIRY_ALREADY_ANSWERED` |
| `POST /admin/api/inquiries/{id}/answer` | ADMIN | `@NotBlank @Size(max=2000) content` | 204 |

- 컨트롤러 클래스 이름은 문서에 없다(권장: 본인용 `InquiryController`, 관리자용 `AdminInquiryController`). 요청자 ID는 `@CurrentMember UUID`로 받고 관리자 API는 `/admin/api/**`의 ADMIN 권한으로 막는다

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 이 단계에서 추가하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `inquiryOf(UUID inquiryId)` → `InquirySnapshot` | JDBC 쿼리 2개로 한 번에 읽는다: `member.inquiry`의 `member_id`·`title`·`content`·`status`, `member.inquiry_answer`의 `admin_id`·`content`(해당 `inquiry_id`, 생성 순). `InquirySnapshot`은 `support/`의 record `(UUID memberId, String title, String content, String status, List<Answer> answers)`이고 `Answer`는 `(UUID adminId, String content)`. 문의 행이 없으면 `null` | `InquiryTest` 6·8·15·16·17·19번, `InquiryConcurrencyTest` |
| `inquiriesOf(UUID memberId)` → `List<InquirySnapshot>` | `member.inquiry`에서 `member_id = ?`인 행을 `created_at` 순으로 읽어 각각 `inquiryOf`와 같은 모양으로 만든다. 회원의 문의 전체이고 문의 행 수는 `.size()` | `InquiryTest` 1·2·18번 |

이미 있는 헬퍼: `signup`, `login`, `void changeRole(UUID memberId, String role)`(JDBC로 `member.member.role`을 바꾼다).

헬퍼를 쓰는 방법은 다음과 같다.
- 응답: `jsonPath`. 에러 코드는 `$.code`, 목록은 `$.items.length()`·`$.items[i].…`·`$.nextCursor`. 항목 필드는 5-3 `member.application` 절의 `InquiryResult`·`InquiryDetailResult`·`AdminInquiryResult`와 같다(본인 목록 `id`·`title`·`status`·`createdAt`·`answeredAt`, 상세는 `content`와 `answer.content`·`answer.createdAt`, 관리자 목록은 `memberId`·`memberEmail`·`memberNickname`·`content`·`answer.adminId`·`answer.content`).

**준비 A**(각 케이스는 필요한 계정만 쓴다): 회원 `user1@test.com`, `user2@test.com`(`signup` + `login`으로 ID와 토큰). 관리자 `admin@test.com`: `adminId = signup("admin@test.com")` → `changeRole(adminId, "ADMIN")` → `login`으로 토큰을 다시 받는다(4-4의 `adminToken(email)`은 같은 순서를 밟지만 가입한 회원 ID를 돌려주지 않아 `admin_id` 확인에 쓸 수 없다). 문의 작성 요청은 `POST /api/inquiries`에 `Authorization: Bearer {user1 토큰}`, 본문 `{"title":"배송이 늦어요","content":"주문한 상품이 아직 도착하지 않았습니다."}`다. 응답 `$.inquiryId`로 문의 ID(`Q1`)를 받는다. 답변 요청은 `POST /admin/api/inquiries/{id}/answer`에 `Authorization: Bearer {admin 토큰}`, 본문 `{"content":"확인 후 오늘 중 안내드리겠습니다."}`다.

#### `member/web/InquiryTest`

**1. 문의 작성** — 동작: `user1`이 작성 요청.
- 응답(andExpect): 상태 201, `jsonPath("$.inquiryId").isNotEmpty()`.
- DB(inquiriesOf(user1)): 1개이고 `memberId()` = `user1`, `title()` = `"배송이 늦어요"`, `content()` = 보낸 본문, `status()` = `OPEN`.

**2. 문의 입력 검증** (추가) — 준비: `user1`이 1번의 문의 하나를 작성해 둔다. 동작: `title` 빈 문자열 / `title` `"가"×101` / `content` 빈 문자열 / `content` `"가"×2001`로 각각 요청.
- 응답(andExpect): 모두 상태 400, `$.code` = `INVALID_REQUEST`. DB(inquiriesOf(user1)): `.size()`는 1 그대로.
- `title` `"가"×100`·`content` `"가"×2000` → 응답(andExpect): 상태 201.

**3. 본인 목록은 최신순이다** — 준비: `user1`이 문의 3개(`Q1`, `Q2`, `Q3`)를 차례로 작성한다. 동작: `user1` 토큰으로 `GET /api/inquiries`.
- 응답(andExpect): 상태 200, `$.items[0].id` = `Q3`, `$.items[1].id` = `Q2`, `$.items[2].id` = `Q1`.

**4. 본인 목록에 답변 시각이 나온다** — 준비: `user1`이 `Q1`, `Q2`를 차례로 작성하고 `admin`이 `Q2`에 답변한다. 동작: `user1` 토큰으로 `GET /api/inquiries`.
- 응답(andExpect): 상태 200, `$.items[0].id` = `Q2`이고 `jsonPath("$.items[0].answeredAt").value(notNullValue())`, `$.items[1].id` = `Q1`이고 `jsonPath("$.items[1].answeredAt").value(nullValue())`.

**5. 남의 문의는 보이지 않는다** — 준비: `user1`이 `Q1`을 작성. 동작·확인:
- `user2` 토큰으로 `GET /api/inquiries/{Q1}` → 응답(andExpect): 상태 404, `$.code` = `INQUIRY_NOT_FOUND`.
- `user2` 토큰으로 `GET /api/inquiries/{Ids.newId()}` → 응답(andExpect): 상태 404, `$.code` = `INQUIRY_NOT_FOUND`.
- `user2` 토큰으로 `GET /api/inquiries` → 응답(andExpect): 상태 200, `jsonPath("$.items").value(hasSize(0))`, `jsonPath("$.nextCursor").value(nullValue())`.
- 본인: `user1` 토큰으로 `GET /api/inquiries/{Q1}` → 응답(andExpect): 상태 200, `$.title` = `"배송이 늦어요"` / `GET /api/inquiries` → 응답(andExpect): 상태 200, `$.items.length()` = 1, `$.items[0].id` = `Q1`.

**6. 답변하면 ANSWERED** — 준비: `user1`이 `Q1`을 작성. 동작: `admin`이 답변 요청.
- 응답(andExpect): 상태 204.
- DB(inquiryOf(Q1)): `status()` = `ANSWERED`, `answers()` 1개(`adminId()` = `adminId`, `content()` = `"확인 후 오늘 중 안내드리겠습니다."`).
- 응답(andExpect): `user1` 토큰으로 `GET /api/inquiries/{Q1}` → 상태 200, `$.status` = `ANSWERED`, `$.answer.content` = `"확인 후 오늘 중 안내드리겠습니다."`, `jsonPath("$.answer.adminId").doesNotExist()`(본인에게는 답변한 관리자를 내려주지 않는다, 5-3 정책).

**7. 답변 입력 검증** (추가) — 준비: `user1`이 `Q1`을 작성. 동작: `admin`이 `content` 빈 문자열 또는 `"가"×2001`로 답변.
- 응답(andExpect): 상태 400, `$.code` = `INVALID_REQUEST`.

**8. 두 번째 답변은 409** — 준비: 6번의 동작까지(`user1`이 `Q1` 작성, `admin`이 답변). 동작: `admin`이 본문 `{"content":"추가 안내입니다."}`로 같은 문의에 다시 답변.
- 응답(andExpect): 상태 409, `$.code` = `INVALID_STATE_TRANSITION`.
- DB(inquiryOf(Q1)): `answers()`는 여전히 1개이고 `content()`는 첫 답변 그대로, `status()` = `ANSWERED`.

**9. 없는 문의에 답변하면 404** (추가) — 동작: `admin`이 없는 ID(`Ids.newId()`)에 답변.
- 응답(andExpect): 상태 404, `$.code` = `INQUIRY_NOT_FOUND`.

**10. 관리자 목록은 상태로 거른다** (추가) — 준비: `user1`이 문의 3개(`Q1`, `Q2`, `Q3`)를 작성하고 `admin`이 `Q2`에 답변한다. 동작: `admin` 토큰으로 `GET /admin/api/inquiries?status=OPEN`.
- 응답(andExpect): 상태 200, `$.items.length()` = 2, `$.items[*].id`의 집합이 `{Q1, Q3}`(`containsInAnyOrder`), `jsonPath("$.nextCursor").value(nullValue())`.
- `?status=ANSWERED` → 응답(andExpect): `$.items.length()` = 1, `$.items[0].id` = `Q2`.

**11. 관리자 목록은 오래된 문의부터 다음 쪽으로 이어진다** — 준비: `user1`이 문의 3개(`Q1`, `Q2`, `Q3`)를 차례로 작성한다. 동작: `admin` 토큰으로 `GET /admin/api/inquiries?status=OPEN&size=2`(`.andReturn()`으로 받는다) → 그 응답 본문에서 `JsonPath.read(본문, "$.nextCursor")`로 꺼낸 값을 `cursor=`로 넘겨 같은 요청.
- 1쪽 응답(andExpect): `$.items[0].id` = `Q1`, `$.items[1].id` = `Q2`, `jsonPath("$.nextCursor").value(notNullValue())`.
- 2쪽 응답(andExpect): `$.items.length()` = 1, `$.items[0].id` = `Q3`, `jsonPath("$.nextCursor").value(nullValue())`.

**12. 관리자 목록은 `status`를 생략하면 전체다** — 준비: `user1`이 `Q1`, `Q2`를 작성하고 `admin`이 `Q2`에 답변한다. 동작: `admin` 토큰으로 `GET /admin/api/inquiries`(`status` 생략).
- 응답(andExpect): 상태 200, `$.items.length()` = 2, `$.items[*].id`가 `Q1`·`Q2`(`containsInAnyOrder`).

**13. 관리자 목록에 작성자·본문·답변이 있다** — 준비: `user1`이 `Q1`을 작성하고 `admin`이 답변한다. 동작: `admin` 토큰으로 `GET /admin/api/inquiries?status=ANSWERED`.
- 응답(andExpect): 상태 200, `$.items[0].id` = `Q1`, `.memberId` = `user1`의 회원 ID, `.memberEmail` = `"user1@test.com"`, `.memberNickname` = `"user1"`(`signup`이 이메일 앞부분을 닉네임으로 넣는다), `.content` = `"주문한 상품이 아직 도착하지 않았습니다."`, `.answer.adminId` = `adminId`, `.answer.content` = `"확인 후 오늘 중 안내드리겠습니다."`.

**14. 관리자 API는 일반 회원에게 막힌다** (추가) — 준비: `user1`이 `Q1`을 작성. 동작: `user1` 토큰으로 `GET /admin/api/inquiries?status=OPEN`과 `POST /admin/api/inquiries/{Q1}/answer`.
- 응답(andExpect): 두 요청 모두 상태 403, `$.code` = `FORBIDDEN`.

**15. 답변 전 문의를 수정한다** — 준비: `user1`이 `Q1`을 작성. 동작: `user1`이 `PATCH /api/inquiries/{Q1}`에 `{"title":"배송이 많이 늦어요"}`(`content` 생략).
- 응답(andExpect): 상태 200, `$.title` = `"배송이 많이 늦어요"`, `$.content` = 작성 때 본문, `$.status` = `OPEN`, `jsonPath("$.answer").value(nullValue())`.
- DB(inquiryOf(Q1)): `title()` = `"배송이 많이 늦어요"`, `content()` = 작성 때 본문, `status()` = `OPEN`(`content`를 생략하면 바꾸지 않는다).

**16. 수정 입력 검증** (추가) — 준비: `user1`이 `Q1`을 작성. 동작: `user1`이 `PATCH /api/inquiries/{Q1}`에 `title` 빈 문자열 / `title` 공백만 `"   "` / `title` `"가"×101` / `content` 빈 문자열 / `content` `"가"×2001`로 각각 요청.
- 응답(andExpect): 모두 상태 400, `$.code` = `INVALID_REQUEST`. DB(inquiryOf(Q1)): `title()`·`content()`는 작성 때 그대로.
- `title` `"가"×100`·`content` `"가"×2000` → 응답(andExpect): 상태 200.

**17. 남의 문의는 수정·삭제할 수 없다** — 준비: `user1`이 `Q1`을 작성. 동작·확인:
- `user2` 토큰으로 `PATCH /api/inquiries/{Q1}`(`{"title":"바꾼 제목"}`) → 응답(andExpect): 상태 404, `$.code` = `INQUIRY_NOT_FOUND`.
- `user2` 토큰으로 `DELETE /api/inquiries/{Q1}` → 응답(andExpect): 상태 404, `$.code` = `INQUIRY_NOT_FOUND`.
- `user1` 토큰으로 `DELETE /api/inquiries/{Ids.newId()}` → 응답(andExpect): 상태 404, `$.code` = `INQUIRY_NOT_FOUND`.
- DB(inquiryOf(Q1)): 행이 있고 `title()` = `"배송이 늦어요"`, `content()` = 작성 때 본문.

**18. 답변 전 문의를 삭제한다** — 준비: `user1`이 `Q1`을 작성. 동작: `user1`이 `DELETE /api/inquiries/{Q1}`.
- 응답(andExpect): 상태 204.
- DB(inquiriesOf(user1)): `.size()` = 0.
- 응답(andExpect): `user1` 토큰으로 `GET /api/inquiries/{Q1}` → 상태 404, `$.code` = `INQUIRY_NOT_FOUND`.

**19. 답변된 문의는 수정·삭제할 수 없다** — 준비: `user1`이 `Q1`을 작성하고 `admin`이 답변한다. 동작·확인:
- `user1` 토큰으로 `PATCH /api/inquiries/{Q1}`(`{"title":"바꾼 제목"}`) → 응답(andExpect): 상태 409, `$.code` = `INQUIRY_ALREADY_ANSWERED`.
- `user1` 토큰으로 `DELETE /api/inquiries/{Q1}` → 응답(andExpect): 상태 409, `$.code` = `INQUIRY_ALREADY_ANSWERED`.
- DB(inquiryOf(Q1)): `title()` = `"배송이 늦어요"`, `status()` = `ANSWERED`, `answers()` 1개.

#### `member/application/InquiryConcurrencyTest`

**1. 삭제와 답변이 동시에 와도 한쪽만 성공하고 데이터가 어긋나지 않는다** (`@RepeatedTest(10)`) — 준비: `user1`이 `inquiryService.write`로 `Q1`을 작성한다. 동작: 스레드 2개가 `CountDownLatch`로 동시에 출발해 하나는 `inquiryService.delete(user1, Q1)`, 하나는 `inquiryService.answer(adminId, Q1, "확인 후 오늘 중 안내드리겠습니다.")`를 부른다(`adminId`는 `Ids.newId()`로 충분하다. 서비스는 관리자 존재를 확인하지 않는다).
- 성공은 정확히 1건. 실패한 쪽의 예외는 삭제 쪽이면 `ObjectOptimisticLockingFailureException` 또는 `BusinessException(INQUIRY_ALREADY_ANSWERED)`, 답변 쪽이면 `BusinessException(INQUIRY_NOT_FOUND)`이다. `DataIntegrityViolationException`은 없다.
- 삭제가 성공했으면 DB: `inquiryOf(Q1)`이 `null`이고 `JdbcTemplate`으로 센 `member.inquiry_answer`의 `inquiry_id = Q1` 행이 0개. 답변이 성공했으면 DB: `inquiryOf(Q1)`의 `status()` = `ANSWERED`, `answers()` 1개.
- 겹침이 매번 일어나지 않아 10회 반복한다. `answer`의 `findByIdForUpdate`를 `findById`로 바꾸면 `DataIntegrityViolationException`으로 실패하는 회차가 나오는지 확인한다(락 판단은 실험으로).

**추가 항목**
- 문의 답변 알림: 답변하면 문의한 회원에게 이메일을 보낸다(제목 "문의에 답변이 등록되었습니다", 본문에 문의 제목). `InquiryService.answer`가 같은 트랜잭션에서 outbox로 `member.inquiry-answered.v1`(`InquiryAnsweredEvent(UUID inquiryId, UUID memberId, String title)`)을 발행하고, notification이 4-4의 `MemberBannedEvent`와 같은 모양으로 구독한다(`MemberTopics` 상수, `MemberTopicConfig`의 본 토픽·`.dlt` 빈, `NotificationType.INQUIRY_ANSWERED`, `NotificationComposer.compose(InquiryAnsweredEvent)`, `NotificationEventConsumer` 메서드). 하면 요구사항 FR-NTF-01과 [이벤트 카탈로그](../02-design/05-events.md)에 더한다
- 제재 회원도 문의 쓰기: 로그인은 허용하고 토큰에 `st: "BANNED"`를 넣어, 필터가 허용 목록(`/api/inquiries/**`, `GET /api/members/me`, 로그아웃·갱신) 외 요청을 403 `MEMBER_BANNED`로 막는다. 1-4·4-4의 "제재 → 로그인 403" 테스트가 "로그인 200 + 체크아웃 403"으로 바뀐다

**리뷰 때 물어볼 것**
- 문의와 답변을 한 테이블이 아니라 둘로 나눈 이유는?
- 삭제와 답변이 동시에 오면 `answer`에 잠금이 없을 때 무슨 일이 생기나?

---

## Part 5 완료
- [x] `v0.5.0` 릴리스, Part 6 문서 다듬기
