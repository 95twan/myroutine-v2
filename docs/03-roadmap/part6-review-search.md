# Part 6. 리뷰 · 검색 · 추천

> 버전 0.4 · 2026-10-02 · **덜어내기**: 6-3 무중단 재색인은 선택 단계로, 리뷰 임베딩 제거(요약은 단순 선택), 제재 회원 문의 허용은 제안으로, 리뷰 중복은 사전 조회로
> 0.3 · 이 문서만 보고 개발할 수 있게 구체화(모듈 경계, ES·pgvector·OpenAI 사용 규격, 테스트 케이스)

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
- 타임아웃: ES 2초, OpenAI 임베딩 10초·요약 60초. 테스트는 가짜 서버(JDK `HttpServer`, 2-3와 같은 방식).
- 확인 필요: Spring Boot 4와 호환되는 Spring AI·Spring Data Elasticsearch 버전. 착수할 때 호환표를 확인하고 개발 가이드 §1.1 표에 적는다(Claude).

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

**1) order.api — 작성 자격 확인**
```java
public interface OrderApi {
    ConfirmedLineInfo getConfirmedLine(UUID memberId, UUID orderLineId);   // 내 품목이 아니면 ORDER_LINE_NOT_FOUND, CONFIRMED가 아니면 REVIEW_NOT_ALLOWED
}
public record ConfirmedLineInfo(UUID orderLineId, UUID productId, UUID shopId, Instant confirmedAt) {}
```
- `OrderApiImpl` (order.application, package-private, `@Transactional(readOnly = true)`)

**2) 마이그레이션** — `db/migration/review/V{...}__review_create_review.sql` (`CREATE SCHEMA IF NOT EXISTS review;`)

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
- 같은 품목 리뷰 중복은 `existsByOrderLineId`로 먼저 확인해 `REVIEW_ALREADY_EXISTS`. 동시 작성 경합은 unique 제약 → 409 `DUPLICATE_RESOURCE`

**3) review 모듈**

| 클래스 | 규격 |
|---|---|
| `Review` (entity) | `static Review write(ConfirmedLineInfo line, UUID memberId, int rating, String content)`, `int edit(int rating, String content, Instant now)`(createdAt + 30일 지났으면 `REVIEW_EDIT_EXPIRED`, 이전 별점 반환), `void delete()` |
| `ReviewStatRepository` | native `int add(UUID productId, int rating, Instant now)` / `int remove(UUID productId, int rating, Instant now)` / `int change(UUID productId, int oldRating, int newRating, Instant now)` |
| `ReviewLikeRepository` | native `int insertIgnoringConflict(UUID reviewId, UUID memberId, Instant now)`, `int deleteByReviewIdAndMemberId(...)` |
| `ReviewRepository` | `@Modifying` `int increaseLikeCount(UUID id)` / `int decreaseLikeCount(UUID id)` (`like_count = like_count - 1 WHERE like_count > 0`) + 목록 쿼리 |
| `ReviewService` | `write`, `edit`, `delete`, `like`, `unlike` (모두 `@Transactional`) |
| `ReviewQueryService` | `getReviews(UUID productId, String sort, String cursor, int size)`, `getStats(UUID productId)` |

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

`write` 순서: `orderApi.getConfirmedLine(memberId, orderLineId)` → `existsByOrderLineId`면 `REVIEW_ALREADY_EXISTS` → `Review.write` save → `add` → `publish(REVIEW_CREATED)` — 같은 품목 동시 작성은 커밋 시 unique 위반 → 409 (트랜잭션 안에서 잡지 않는다, As-Is SUP-05)

`like`: `insertIgnoringConflict` → 1이면 `increaseLikeCount` / `unlike`: 삭제 행 수 1이면 `decreaseLikeCount`

이벤트: `review.api.event.ReviewTopics.REVIEW_CREATED = "review.review-created.v1"`, `ReviewCreatedEvent(UUID reviewId, UUID productId, String content)`

**4) API**

| API | 권한 | 요청 | 응답 |
|---|---|---|---|
| `POST /api/reviews` | 로그인 | `WriteReviewRequest(@NotNull orderLineId, @Min(1) @Max(5) rating, @Size(min=10, max=1000) content)` | 201 `{reviewId}` |
| `PATCH /api/reviews/{id}` | 작성자 | `EditReviewRequest(rating, content)` | 200 `ReviewResponse` |
| `DELETE /api/reviews/{id}` | 작성자 | | 204 |
| `POST /api/reviews/{id}/likes`, `DELETE ...` | 로그인 | | 204 |
| `GET /api/products/{id}/reviews?sort=latest\|likes&cursor=&size=` | 공개 | | 200 `CursorPage<ReviewResponse(id, rating, content, likeCount, createdAt)>` (작성자 정보는 내려주지 않는다 — review가 member를 참조하지 않게) |
| `GET /api/products/{id}/review-stats` | 공개 | | 200 `ReviewStatsResponse(reviewCount, average(소수 1자리), distribution{1..5}, latestSummary)` — summary는 6-5 전까지 null |

- 작성자가 아닌 수정·삭제는 404 `REVIEW_NOT_FOUND` (`findByIdAndMemberId`)
- `SecurityConfig`: 리뷰 목록·통계 GET 공개

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `review/domain/ReviewTest` | [ ] 30일 지난 리뷰 수정 → `REVIEW_EDIT_EXPIRED` |
| `review/web/ReviewControllerTest` | [ ] 구매확정 전 품목 → 422 `REVIEW_NOT_ALLOWED` / 남의 품목 → 404 / [ ] 작성 → 통계 count 1·분포 / 별점 수정 → 분포 이동 / 삭제 → 통계에서 빠짐·목록에 없음 / [ ] 같은 리뷰 좋아요 두 번 → 1 / [ ] 최신순 커서·좋아요순 |
| `review/application/ReviewConcurrencyTest` | [ ] **같은 품목 동시 작성 2건 → 1건 201, 1건 409** / [ ] **서로 다른 회원 좋아요 동시 100건 → like_count 100** / [ ] 서로 다른 품목 리뷰 동시 50건 → `review_count` 50, `rating_sum` 정확 |

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

**1) 인프라**
- docker-compose: `docker.elastic.co/elasticsearch/elasticsearch:{버전}` (확인 필요: Spring Data Elasticsearch가 지원하는 8.x/9.x), `discovery.type=single-node`, `xpack.security.enabled=false`, `ES_JAVA_OPTS=-Xms512m -Xmx512m`, 포트 `127.0.0.1:9200`
- 의존성: `spring-boot-starter-data-elasticsearch` (확인 필요: Boot 4 이름), 테스트 `org.testcontainers:testcontainers-elasticsearch`
- `IntegrationTestSupport`: `@ServiceConnection static final ElasticsearchContainer es` (보안 끄는 환경변수 동일)

**2) search 모듈 (domain 생략, application + infrastructure, 개발 가이드 §3.3)**

| 클래스 | 규격 |
|---|---|
| `ProductIndexDefinition` | 인덱스 설정·매핑 JSON(`src/main/resources/search/products-index.json`): 분석기 `autocomplete`(tokenizer `edge_ngram` min 1 max 20, filter `lowercase`), 필드 `productId keyword`, `shopId keyword`, `name text` + 하위 필드 `name.autocomplete`(분석기 autocomplete, 검색 분석기 standard), `description text`, `category keyword`, `price long`, `status keyword`, `updatedAt date` |
| `ProductIndexer` (`@Component`) | `void ensureIndex()` (앱 시작 시 alias `products`가 없으면 `products-{yyyyMMddHHmmss}` 생성 + alias 연결), `void upsert(ProductUpsertedEvent e, String indexOrAlias)`: 문서 id = productId, **`version = e.version()`, `versionType = EXTERNAL`** → 버전 충돌(409)은 "이미 더 최신"이므로 무시 |
| `ProductSearchService` | `CursorPage<ProductSearchResult> search(String q, String category, String cursor, int size)` (`multi_match` q → `name^3`, `description`; filter `status = ON_SALE`, category), `List<String> autocomplete(String q)` (`match` on `name.autocomplete`, filter ON_SALE, size 10, `_source`는 name만). ES 예외·타임아웃 → `BusinessException(SEARCH_UNAVAILABLE)` |
| `ProductEventConsumer` (consumer `search.product-upserted`) | `inboxGuard.runOnce`를 쓰지 않는다 — ES 쓰기 자체가 external version으로 멱등이고, 트랜잭션 안에서 ES를 부르면 안 되기 때문. `ProductUpsertedEvent` → `indexer.upsert(e, "products")` (+ 6-3의 재색인 대상이 있으면 거기에도) |

- `product-discontinued`는 따로 구독하지 않는다. 단종도 `product-upserted`(status DISCONTINUED)가 함께 발행되기 때문이다(3-3).
- ES 클라이언트 타임아웃: `spring.elasticsearch.connection-timeout: 2s`, `socket-timeout: 2s`
- ES가 내려가 있으면 consumer가 실패 → 3번 재시도 → DLT. 복구 후 7-3 운영 API로 DLT를 재처리하거나 6-3 재색인으로 맞춘다.

**3) API** — `SearchController`

| API | 요청 | 응답 |
|---|---|---|
| `GET /api/search/products?q=&category=&cursor=&size=` | `q` 1~50자 필수 | 200 `CursorPage<ProductSearchResponse(productId, shopId, name, category, price)>` |
| `GET /api/search/autocomplete?q=` | 2~20자 | 200 `List<String>` |

### 완료 확인

| 테스트 클래스 | 케이스 (`Eventually`로 색인 반영 대기, 필요하면 `refresh` 호출) |
|---|---|
| `search/application/ProductSearchTest` | [ ] 상품 등록 → 릴레이 → 검색됨 / [ ] 단종 → 검색 안 됨 / [ ] 자동완성 "루" → "루틴 비타민" / [ ] **순서 역전**: version 3 이벤트를 먼저, version 2 이벤트를 나중에 색인 → 문서는 version 3 내용 / [ ] 같은 이벤트 두 번 → 문서 그대로 |
| `search/web/SearchUnavailableTest` | [ ] **ES 컨테이너 pause → 검색 503 `SEARCH_UNAVAILABLE`, 상품 목록(DB) API는 200** |

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

**1) product.api 추가** — `List<ProductIndexSnapshot> findForIndexing(UUID afterId, int limit)`: 상태 상관없이 id 오름차순, `ProductIndexSnapshot`은 `ProductUpsertedEvent`와 같은 필드(version 포함). `search` → `product::api` 의존(공통 규칙 Y)

**2) `ReindexService.reindex()`** (search.application, `@Transactional` 없음)
1. `newIndex = "products-" + 시각` 생성(매핑 동일)
2. Redis `SET search:reindex-target newIndex EX 7200` → 이 순간부터 consumer가 새 인덱스에도 쓴다
3. `findForIndexing` keyset 1,000건씩 → bulk 색인(external version)
4. alias `products`를 원자적으로 교체(`_aliases` actions: remove old, add new)
5. Redis 키 삭제 → 이전 인덱스 삭제
6. 실패하면 Redis 키 삭제, 새 인덱스 삭제, alias는 그대로(검색 영향 없음)

**3) API**: `POST /admin/api/search/reindex` (ADMIN) → 202, 실제 작업은 `JobRunner`로 비동기 실행(`@Async` 대신 별도 스레드 하나: `Executors.newSingleThreadExecutor()` 빈 — MDC traceId는 `JobRunner`가 새로 만든다)

### 완료 확인
- [ ] `ReindexTest`: 상품 1,000개 → 재색인 시작 → 재색인 도중 검색 결과가 계속 있다(별도 스레드에서 반복 검색, 0건인 순간이 없음) → 끝난 뒤 alias가 새 인덱스, 이전 인덱스 삭제
- [ ] 재색인 도중 상품 하나 수정 → 끝난 뒤 새 인덱스에 수정 내용(version 큰 쪽)

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

**1) 인프라·설정**
- 의존성: Spring AI OpenAI 스타터 + BOM (확인 필요: Boot 4 호환 버전과 아티팩트 이름)
- `spring.ai.openai.api-key: ${OPENAI_API_KEY}`, `spring.ai.openai.base-url`(테스트는 가짜 서버), 임베딩 모델 이름은 설정으로
- `support/FakeOpenAiServer`: `/v1/embeddings` → 입력 문자열 해시로 결정적인 1536차원 벡터를 만들어 응답, 호출 횟수 기록, 지연·500 설정 / `/v1/chat/completions` → 고정 요약 문장 (6-5)

**2) 마이그레이션** — `db/migration/recommendation/V{...}__recommendation_create_tables.sql` (`CREATE SCHEMA IF NOT EXISTS recommendation; CREATE EXTENSION IF NOT EXISTS vector;`)
- `recommendation.product_embedding`: `product_id uuid pk`, `embedding vector(1536) X`, `model varchar(50) X`, `source_hash varchar(64) X`, `status varchar(20) X ck IN ('ACTIVE','INACTIVE')`, `updated_at`. 인덱스 `CREATE INDEX idx_product_embedding_hnsw ON recommendation.product_embedding USING hnsw (embedding vector_cosine_ops)`
- `recommendation.member_purchase`: `member_id uuid`, `product_id uuid`, `purchased_at timestamptz`, `pk_member_purchase (member_id, product_id)` (같은 상품 재구매는 시각만 갱신)

**3) recommendation 모듈**
- 벡터는 JPA로 매핑하지 않고 `JdbcTemplate` + 문자열(`'[0.1,0.2,...]'`)과 `CAST(? AS vector)`로 다룬다(새 라이브러리 없이)

| consumer | 처리 순서 |
|---|---|
| `product-upserted` (consumer `recommendation.product-upserted`) | ① `processed_message` 조회만 해서 이미 처리했으면 종료 ② status가 ON_SALE이 아니면 → `runOnce`로 `status = INACTIVE`만 ③ 해시 계산 → 저장된 해시와 같으면 `runOnce`로 status만 ACTIVE ④ **트랜잭션 밖에서** `embeddingModel.embed(입력)` ⑤ `runOnce`로 upsert(`ON CONFLICT (product_id) DO UPDATE`) |
| `order-paid` (consumer `recommendation.order-paid`) | `runOnce` → 품목 상품마다 `member_purchase` upsert → Redis 캐시 삭제 |
| `member-withdrawn` (consumer `recommendation.member-withdrawn`) | `runOnce` → `member_purchase` 삭제 → 캐시 삭제 |

`RecommendationService.recommend(UUID memberId)` (`@Transactional(readOnly = true)` 아님 — Redis를 먼저 본다)
1. Redis 캐시 있으면 반환
2. 최근 구매 상품 ID(90일, 최대 20)
3. 평균 벡터를 SQL로: `SELECT AVG(embedding)::text FROM recommendation.product_embedding WHERE product_id IN (...)` (pgvector는 `avg` 집계를 지원한다, 확인 필요)
4. `SELECT product_id FROM recommendation.product_embedding WHERE status = 'ACTIVE' AND product_id NOT IN (:purchased) ORDER BY embedding <=> CAST(:taste AS vector) LIMIT 20`
5. `productApi.getForCheckout(ids)`로 이름·가격·판매 여부 → 판매 중만, 순서 유지 → Redis 저장(JSON, 30분)

**4) API**: `GET /api/recommendations/me` → 200 `List<RecommendationResponse(productId, name, price, thumbnailKey)>`

### 완료 확인

| 테스트 클래스 | 케이스 (가짜 OpenAI 서버) |
|---|---|
| `recommendation/application/ProductEmbeddingTest` | [ ] **같은 본문 이벤트 두 번 → 임베딩 호출 1번** / [ ] 가격만 바뀐 상품 수정(본문 동일) → 호출 0번 / [ ] 단종 → INACTIVE / [ ] **OpenAI 500 → 재시도 → DLT, 상품 등록·조회 API는 정상** |
| `recommendation/web/RecommendationTest` | [ ] 상품 A(비타민)를 산 회원 → 비슷한 상품이 앞에, 이미 산 A는 제외 / [ ] 구매 이력 없음 → 빈 목록 / [ ] 두 번째 호출은 DB 조회 없이 캐시(SQL 수 0) / [ ] 새 결제 → 캐시 삭제 |

**리뷰 때 물어볼 것**
- OpenAI 비용을 어떻게 관리했나?
- consumer에서 외부 호출을 `runOnce` 밖에서 하는 이유는? 그 대가는(중복 호출 가능성)?

---

## 6-5. 월간 리뷰 요약 (S)

**목표**: 매월 1일 상품별 리뷰를 LLM으로 요약한다. 직전 요약 이후 새 리뷰가 없으면 건너뛴다.

**정책 (이 단계에서 정함)**
- 프롬프트에 넣는 리뷰: 좋아요 많은 순 30개 + 별점 낮은 순 10개(중복 제외, 최대 40개). 리뷰가 아무리 많아도 40개를 넘지 않는다.
- 요약 대상: 전월(업무 날짜)에 ACTIVE 리뷰가 1개 이상 새로 생긴 상품. 요약 모델은 설정값(예: `gpt-4o-mini`, 확인 필요).
- 요약 잡: 매월 1일 04:00(Asia/Seoul), 상품별 실패는 기록하고 건너뛴다.

### 할 일

**1) 마이그레이션** — `review.review_summary`: `id uuid pk`, `product_id uuid X`, `period_end date X`, `uk_review_summary_product_period (product_id, period_end)`, `summary text X`, `source_review_count int X`, `model varchar(50) X`, `created_at`

**2) `ReviewSummaryService.summarizeMonth(YearMonth month)`** (`@Transactional` 없음)
1. 대상 상품 ID: `SELECT DISTINCT product_id FROM review.review WHERE status = 'ACTIVE' AND created_at >= :from AND created_at < :to` (keyset)
2. 상품마다 try/catch:
   1. 이미 `(product_id, period_end)` 요약이 있으면 건너뜀(**LLM 호출 전에** 확인 — 비용)
   2. 대표 리뷰 선택(위 정책: `ORDER BY like_count DESC LIMIT 30` + `ORDER BY rating, created_at DESC LIMIT 10`)
   3. 프롬프트: 시스템 "상품 리뷰를 3문장 이내로 장단점 중심 요약" + 리뷰 목록(별점·본문 200자). **트랜잭션 밖에서** `chatClient` 호출
   4. `INSERT ... ON CONFLICT DO NOTHING`
- `ReviewSummaryJob`: `@Scheduled(cron = "0 0 4 1 * *", zone = "Asia/Seoul")` → 전월
- 6-1의 `review-stats` 응답에 최신 요약(`periodEnd`, `summary`) 채우기

### 완료 확인
- [ ] `ReviewSummaryTest`: 잡 두 번 → 요약 1건, LLM 호출 1번 / [ ] 전월 새 리뷰가 없는 상품 → LLM 호출 0 / [ ] 리뷰 100개인 상품 → 프롬프트에 들어간 리뷰 ≤ 40 (가짜 서버가 받은 요청 본문 확인) / [ ] 한 상품 요약 실패(가짜 서버 500) → 다른 상품은 요약됨

**제안 (선택)**
- 리뷰 임베딩(`review-created` 구독 → 리뷰 벡터 저장)으로 대표 리뷰 고르기: 리뷰 벡터의 평균(중심)에 가까운 리뷰를 고르면 "가장 흔한 의견"을 대표로 넣을 수 있다

**리뷰 때 물어볼 것**: 리뷰가 너무 많으면 무엇을 프롬프트에 넣나? 왜 그렇게 골랐나?

---

## 6-6. 1:1 문의 (S)

**목표**: 회원 문의, 관리자 답변.

**정책 (이 단계에서 정함)**: 제재된 회원은 로그인부터 막히므로(1-4) 문의도 쓸 수 없다. 제재 회원 문의 허용은 제안으로 둔다(FR-MEM-07 "문의 외 기능 사용 불가"의 문의 예외는 이번 범위에서 뺀다).

### 할 일

**1) 마이그레이션** — `member.inquiry`: `id`, `member_id fk_inquiry_member`, `title varchar(100) X`, `content text X`, `status varchar(20) X ck IN ('OPEN','ANSWERED')`, `version`, `created_at`, `updated_at`, 인덱스 `(member_id, created_at DESC, id DESC)`, `(status, created_at)` / `member.inquiry_answer`: `id`, `inquiry_id uk_inquiry_answer_inquiry fk`, `admin_id uuid X`, `content text X`, `created_at`

**2) member**: `Inquiry.write(memberId, title, content)`, `Inquiry.answer(adminId, content)` → ANSWERED(이미 답변이면 `INVALID_STATE_TRANSITION`) / `InquiryService`, `MemberErrorCode.INQUIRY_NOT_FOUND`

**3) API**

| API | 권한 | 요청 | 응답 |
|---|---|---|---|
| `POST /api/inquiries` | 로그인 | `@NotBlank @Size(max=100) title`, `@NotBlank @Size(max=2000) content` | 201 `{inquiryId}` |
| `GET /api/inquiries?cursor=` · `GET /api/inquiries/{id}` | 본인 | | 목록·상세(답변 포함). 남의 문의 → 404 |
| `GET /admin/api/inquiries?status=OPEN&cursor=` | ADMIN | | 목록 |
| `POST /admin/api/inquiries/{id}/answer` | ADMIN | `@NotBlank @Size(max=2000) content` | 204 |

### 완료 확인
- [ ] `InquiryTest`: 문의 작성 → 201 / [ ] 남의 문의 조회 → 404 / [ ] 답변 → ANSWERED, 두 번 답변 → 409

**제안 (선택)**
- 제재 회원도 문의 쓰기: 로그인은 허용하고 토큰에 `st: "BANNED"`를 넣어, 필터가 허용 목록(`/api/inquiries/**`, `GET /api/members/me`, 로그아웃·갱신) 외 요청을 403 `MEMBER_BANNED`로 막는다. 1-4·4-4의 "제재 → 로그인 403" 테스트가 "로그인 200 + 체크아웃 403"으로 바뀐다

**리뷰 때 물어볼 것**: 문의와 답변을 한 테이블이 아니라 둘로 나눈 이유는?

---

## Part 6 완료
- [ ] `v0.6.0` 릴리스, Part 7 문서 다듬기
