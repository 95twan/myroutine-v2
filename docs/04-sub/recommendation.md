# 서브: 취향 추천

> 버전 0.1 · 2026-10-31 · 로드맵의 옛 6-4를 서브로 옮겨 왔다(단계 ID SUB-5)

> **서브**: 로드맵에서 뺀 기능이다. Stage 1(Part 6) 뒤에 하고, [서브 안내의 "Part 6에 더할 것"](README.md#part-6에-더할-것)을 함께 반영한다.
> **따르는 규칙**: 로드맵 Part 1~6의 공통 규칙. 외부 호출·consumer 모양·Spring AI 버전은 [Part 5](../03-roadmap/part5-review-search.md) 공통 규칙 X다.
> **인프라 추가**: pgvector 확장(이미 쓰는 Postgres 이미지에 포함), OpenAI(Spring AI)

## 모듈 의존

| 모듈 | `allowedDependencies` |
|---|---|
| recommendation | `common`, `product::api`, `order::api`, `member::api` |

---

## SUB-5. 취향 추천 (pgvector) (M)
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
- `support/FakeOpenAiServer`: `/v1/embeddings` → 입력 문자열 해시로 결정적인 1536차원 벡터를 만들어 응답, 호출 횟수 기록, 지연·500 설정 / `/v1/chat/completions` → 고정 요약 문장 (SUB-6)

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
