# 서브: 무중단 재색인

> 버전 0.1 · 2026-11-02 · 로드맵의 옛 5-3을 서브로 옮겨 왔다(단계 ID SUB-7). `ProductApi.findForIndexing`과 재색인 API도 이 단계가 더한다

> **서브**: 로드맵에서 뺀 기능이다. Stage 1(Part 6) 뒤에 한다.
> **따르는 규칙**: 로드맵 Part 1~6의 공통 규칙. [5-2](../03-roadmap/part5-review-search.md)가 만든 `search` 모듈을 고치고, 그 단계의 ES 사용법을 그대로 쓴다.

---

## SUB-7. 무중단 재색인 (S)

**목표**: DB의 현재 상태로 ES 인덱스를 새로 만들어 교체한다. 분석기·매핑을 바꿀 때와 ES 문서가 DB와 어긋났을 때 쓴다. 전체 재색인 중에도 검색이 멈추지 않는다(As-Is SUP-04).

**새로 등장**: 이중 쓰기 — 재색인하는 동안 consumer가 기존 인덱스(alias)와 새 인덱스 **둘 다**에 쓴다. 전체 적재와 실시간 변경이 겹쳐도 external version 덕분에 최신 버전만 남는다.

**정책**: 재색인은 한 번에 하나(잡 락 이름 `search-reindex`). 재색인 대상 인덱스 이름은 Redis `search:reindex-target`(TTL 2시간)에 둔다.

### 할 일

**구현 순서**: 절 번호 순서대로 만든다. 1\~2) 상품 keyset 쿼리(`product.domain` → `product.infrastructure`) → 3\~4) `ProductApi.findForIndexing`(`product.api` → `product.application`) → 5) `search.infrastructure`(ES 쓰기 작업 → consumer 이중 쓰기 → 실행기 빈) → 6) `ReindexService.reindex()` → 7) 관리자 API → 테스트.

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

**준비 A**(5번을 뺀 모든 케이스가 쓴다): 판매자 1명, 가게 1개. `RegisterProductService.register`로 상품 1,000개를 만든다: 이름 `"재색인상품 " + i`(i = 1~1000), 설명 `"재색인 검증용 상품"`, `ETC`, 1000원, 재고 1. 첫 상품을 `P1`이라 부른다. 옛 인덱스를 채운다: `productApi.findForIndexing(null, 1_000)`로 받은 이벤트 1,000건을 `productIndexer.upsert(e, "products")`로 색인하고 `POST /products/_refresh`. `oldIndex` = `searchIndexStateOf().aliasTargets().get(0)`(원소가 하나뿐이다). 재색인은 `reindexService.reindex()`를 별도 스레드(`ExecutorService`)에서 부르고 `Future`로 결과를 받는다(반환값 = 색인한 문서 수 1000).

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

**3. 재색인이 끝나면 alias가 새 인덱스를 가리키고 정리된다** — 동작: 준비 A 뒤 `reindex()`를 호출하고 끝나기를 기다린다.
- 반환값: `reindex()` = 1000.
- ES(searchIndexStateOf()): `aliasTargets()`가 정확히 1개이고 그 이름이 `oldIndex`와 다르며 `products-\d{14}` 모양 / `indexNames()`에 `oldIndex`가 없다(`doesNotContain(oldIndex)`) / `docCount()` = 1000.
- Redis(searchIndexStateOf().reindexTarget()): `null`(`isNull()`).
- ES(productSearchDocOf(P1)): `found()` = true.

**4. 재색인이 실패하면 아무것도 바뀌지 않는다** (추가) — 준비: 준비 A + `@MockitoSpyBean ProductApi`에 `doThrow(new RuntimeException("test")).when(productApi).findForIndexing(any(), anyInt())`. 동작: `reindex()`를 `try/catch`로 감싸 호출한다(밖으로 예외가 나오는지는 문서에 없다. 확인 필요).
- ES(searchIndexStateOf(), `reindex()` 호출이 끝난 직후): `aliasTargets()` = [`oldIndex`] 그대로 / `indexNames()` = [`oldIndex`].
- Redis(searchIndexStateOf().reindexTarget(), `reindex()` 호출이 끝난 직후): `null`(`isNull()`).
- 응답(andExpect): `reindex()` 호출이 끝난 뒤 검색 `q=재색인상품&size=1` 상태 200, `$.items.length()` = 1.

**5. 재색인 API 권한** (추가) — 준비: 준비 A는 쓰지 않고 판매자 1명·가게 1개에 상품 1개만 등록한다. 일반 회원 토큰(`token("user@test.com")`), ADMIN 토큰(`adminToken("admin@test.com")`, 4-4). ADMIN 호출 전 `searchIndexStateOf().aliasTargets()`를 `before`로 적어 둔다. 동작: `POST /admin/api/search/reindex`.
- ADMIN 토큰 — 응답(andExpect): 상태 202. ES(searchIndexStateOf().aliasTargets()): 응답 뒤 `Eventually.await(Duration.ofSeconds(30), …)`로 `before`와 달라질 때까지 기다린 뒤 끝낸다.
- 일반 회원 토큰 — 응답(andExpect): 상태 403, `$.code` = `FORBIDDEN`.
- 토큰 없음 — 응답(andExpect): 상태 401, `$.code` = `UNAUTHORIZED`.

**리뷰 때 물어볼 것**: alias 없이 같은 인덱스를 지우고 다시 만들면 무엇이 문제인가?
