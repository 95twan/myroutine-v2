package com.myroutine.search.application;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.model.Ids;
import com.myroutine.common.outbox.OutboxRelay;
import com.myroutine.common.storage.StorageProperties;
import com.myroutine.product.api.event.ProductUpsertedEvent;
import com.myroutine.product.application.*;
import com.myroutine.product.domain.ProductCategory;
import com.myroutine.product.domain.ProductStatus;
import com.myroutine.search.infrastructure.ProductIndexer;
import com.myroutine.support.Eventually;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProductSearchTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final RegisterProductService registerProductService;
    private final UpdateProductService updateProductService;
    private final AdjustStockService adjustStockService;
    private final StorageProperties storageProperties;
    private final OutboxRelay outboxRelay;
    private final ProductIndexer productIndexer;

    @Autowired
    public ProductSearchTest(
            MockMvc mockMvc,
            TestFixtures testFixtures,
            RegisterProductService registerProductService,
            UpdateProductService updateProductService,
            AdjustStockService adjustStockService, StorageProperties storageProperties,
            OutboxRelay outboxRelay, ProductIndexer productIndexer
    ) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.registerProductService = registerProductService;
        this.updateProductService = updateProductService;
        this.adjustStockService = adjustStockService;
        this.storageProperties = storageProperties;
        this.outboxRelay = outboxRelay;
        this.productIndexer = productIndexer;
    }

    @Test
    @DisplayName("상품을 등록하면 이름과 설명으로 검색된다.")
    void registerThenSearch() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "마그네슘 데일리 정",
                "수면용 근육이완 보충제",
                ProductCategory.HEALTH,
                15000L,
                10,
                false
        ));

        outboxRelay.relayOnce();

        // When & Then
        Eventually.await(Duration.ofSeconds(10), () -> {
            mockMvc.perform(get("/api/search/products")
                            .param("q", "마그네슘"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(productId.toString()))
                    .andExpect(jsonPath("$.items[0].shopId").value(shopId.toString()))
                    .andExpect(jsonPath("$.items[0].name").value("마그네슘 데일리 정"))
                    .andExpect(jsonPath("$.items[0].category").value("HEALTH"))
                    .andExpect(jsonPath("$.items[0].price").value(15000))
                    .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        });

        mockMvc.perform(get("/api/search/products")
                        .param("q", "수면"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(productId.toString()));
    }

    @Test
    @DisplayName("관련도순으로 정렬하고 커서로 다음 쪽을 조회한다.")
    void sortByRelevanceAndPage() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "프로폴리스 스프레이",
                "목 건강 케어",
                ProductCategory.HEALTH,
                10000L,
                10,
                false
        ));

        UUID product2Id = registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "목 케어 스프레이",
                "프로폴리스 함유",
                ProductCategory.HEALTH,
                15000L,
                10,
                false
        ));

        outboxRelay.relayOnce();

        // When & Then
        Eventually.await(Duration.ofSeconds(10), () -> {
            mockMvc.perform(get("/api/search/products")
                            .param("q", "프로폴리스")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.items[0].id").value(product1Id.toString()))
                    .andExpect(jsonPath("$.items[1].id").value(product2Id.toString()))
                    .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        });

        MvcResult result = mockMvc.perform(get("/api/search/products")
                        .param("q", "프로폴리스")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(product1Id.toString()))
                .andExpect(jsonPath("$.nextCursor").value(notNullValue()))
                .andReturn();

        String nextCursor = JsonPath.read(result.getResponse().getContentAsString(), "$.nextCursor");

        mockMvc.perform(get("/api/search/products")
                        .param("q", "프로폴리스")
                        .param("size", "1")
                        .param("cursor", nextCursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(product2Id.toString()))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    @DisplayName("카테고리로 검색 결과를 거른다.")
    void filterByCategory() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "아로마 영양제",
                "향기 케어",
                ProductCategory.HEALTH,
                10000L,
                10,
                false
        ));

        UUID product2Id = registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "아로마 크림",
                "향기 케어",
                ProductCategory.BEAUTY,
                15000L,
                10,
                false
        ));

        outboxRelay.relayOnce();

        // When & Then
        Eventually.await(Duration.ofSeconds(10), () -> {
            mockMvc.perform(get("/api/search/products")
                            .param("q", "아로마")
                            .param("category", "HEALTH"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(product1Id.toString()));
        });

        mockMvc.perform(get("/api/search/products")
                        .param("q", "아로마"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[*].id").value(containsInAnyOrder(product1Id.toString(), product2Id.toString())));

        mockMvc.perform(get("/api/search/products")
                        .param("q", "아로마")
                        .param("category", "FOOD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("점수가 같으면 productId 오름차순으로 정렬한다.")
    void tieBreakByProductId() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "라벤더 세트",
                "동일한 설명",
                ProductCategory.HEALTH,
                10000L,
                10,
                false
        ));

        UUID product2Id = registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "라벤더 세트",
                "동일한 설명",
                ProductCategory.HEALTH,
                10000L,
                10,
                false
        ));

        String lo = Stream.of(product1Id.toString(), product2Id.toString()).sorted().toList().getFirst();
        String hi = Stream.of(product1Id.toString(), product2Id.toString()).sorted().toList().getLast();

        outboxRelay.relayOnce();

        // When & Then
        Eventually.await(Duration.ofSeconds(10), () -> {
            MvcResult result = mockMvc.perform(get("/api/search/products")
                            .param("q", "라벤더")
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].id").value(lo))
                    .andExpect(jsonPath("$.nextCursor").value(notNullValue()))
                    .andReturn();

            String nextCursor = JsonPath.read(result.getResponse().getContentAsString(), "$.nextCursor");

            mockMvc.perform(get("/api/search/products")
                            .param("q", "라벤더")
                            .param("size", "1")
                            .param("cursor", nextCursor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].id").value(hi))
                    .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        });
    }

    @Test
    @DisplayName("사진과 품절 여부는 이벤트 없이 상품 DB 값으로 나온다.")
    void thumbnailAndStockFromDb() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "콜라겐 파우더",
                "피부 보습 보충제",
                ProductCategory.BEAUTY,
                30000L,
                10,
                false
        ));

        outboxRelay.relayOnce();

        Eventually.await(Duration.ofSeconds(10), () -> {
            mockMvc.perform(get("/api/search/products")
                            .param("q", "콜라겐"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1));
        });

        // When & Then
        ImageResult image = testFixtures.registerProductImage(sellerId, shopId, product1Id);
        adjustStockService.adjust(sellerId, shopId, product1Id, -10, "재고 정리");

        mockMvc.perform(get("/api/search/products")
                        .param("q", "콜라겐"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(product1Id.toString()))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").value(storageProperties.publicBaseUrl() + "/" + image.objectKey()))
                .andExpect(jsonPath("$.items[0].inStock").value(false));
    }

    @Test
    @DisplayName("숨김·단종 상품은 검색에서 빠진다.")
    void hiddenAndDiscontinuedExcluded() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "오메가 쓰리 캡슐",
                "혈행 개선 보충제",
                ProductCategory.HEALTH,
                22000L,
                10,
                false
        ));

        UUID product2Id = registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "오메가 듀얼 캡슐",
                "혈행 개선 보충제",
                ProductCategory.HEALTH,
                22000L,
                10,
                false
        ));

        outboxRelay.relayOnce();

        // When & Then
        Eventually.await(Duration.ofSeconds(10), () -> {
            mockMvc.perform(get("/api/search/products")
                            .param("q", "오메가")
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.nextCursor").value(notNullValue()));
        });

        updateProductService.changeStatus(sellerId, shopId, product1Id, ProductStatus.HIDDEN);
        outboxRelay.relayOnce();

        Eventually.await(Duration.ofSeconds(10), () -> {
            mockMvc.perform(get("/api/search/products")
                            .param("q", "오메가")
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(product2Id.toString()))
                    .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        });

        updateProductService.changeStatus(sellerId, shopId, product1Id, ProductStatus.ON_SALE);
        outboxRelay.relayOnce();

        Eventually.await(Duration.ofSeconds(10), () -> {
            mockMvc.perform(get("/api/search/products")
                            .param("q", "오메가")
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.nextCursor").value(notNullValue()));
        });

        updateProductService.changeStatus(sellerId, shopId, product1Id, ProductStatus.DISCONTINUED);
        outboxRelay.relayOnce();

        Eventually.await(Duration.ofSeconds(10), () -> {
            mockMvc.perform(get("/api/search/products")
                            .param("q", "오메가")
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(product2Id.toString()))
                    .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        });
    }

    @Test
    @DisplayName("DB에서 판매 중이 아닌 상품은 ES에 반영되기 전에도 빠진다.")
    void notOnSaleInDbExcludedBeforeIndexing() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "히알루론 앰플",
                "보습 케어",
                ProductCategory.BEAUTY,
                22000L,
                10,
                false
        ));

        UUID product2Id = registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "히알루론 크림",
                "보습 케어",
                ProductCategory.BEAUTY,
                22000L,
                10,
                false
        ));

        outboxRelay.relayOnce();

        // When & Then
        Eventually.await(Duration.ofSeconds(10), () -> {
            mockMvc.perform(get("/api/search/products")
                            .param("q", "히알루론"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(2));
        });

        testFixtures.hideProduct(product1Id);

        mockMvc.perform(get("/api/search/products")
                        .param("q", "히알루론"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(product2Id.toString()));
    }

    @Test
    @DisplayName("자동완성은 상품명이 검색어로 시작하는 판매 중 상품만 돌려준다.")
    void autocompleteOnSalePrefixOnly() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "루틴 비타민",
                "보습 케어",
                ProductCategory.HEALTH,
                22000L,
                10,
                false
        ));
        registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "루틴 마스크팩",
                "보습 케어",
                ProductCategory.HEALTH,
                22000L,
                10,
                false
        ));

        registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "비타민 루틴",
                "보습 케어",
                ProductCategory.HEALTH,
                22000L,
                10,
                false
        ));

        UUID product4Id = registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "루틴 오일",
                "보습 케어",
                ProductCategory.HEALTH,
                22000L,
                10,
                false
        ));

        updateProductService.changeStatus(sellerId, shopId, product4Id, ProductStatus.HIDDEN);

        outboxRelay.relayOnce();

        // When & Then
        Eventually.await(Duration.ofSeconds(10), () -> {
            mockMvc.perform(get("/api/search/autocomplete")
                            .param("q", "루틴"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[*]").value(containsInAnyOrder("루틴 비타민", "루틴 마스크팩")));
        });
    }

    @Test
    @DisplayName("자동완성은 최대 10개를 돌려준다.")
    void autocompleteLimit() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");

        for (int i = 1; i <= 12; i++) {
            registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                    "젤리맛 %02d".formatted(i),
                    "보습 케어",
                    ProductCategory.HEALTH,
                    22000L,
                    10,
                    false
            ));
        }

        outboxRelay.relayOnce();

        // When & Then
        Eventually.await(Duration.ofSeconds(10), () -> {
            MvcResult result = mockMvc.perform(get("/api/search/autocomplete")
                            .param("q", "젤리맛"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(10))
                    .andReturn();

            List<String> autoCompletes = JsonPath.read(result.getResponse().getContentAsString(), "$");

            assertThat(autoCompletes).allMatch(name -> name.startsWith("젤리맛 "));
        });
    }

    @Test
    @DisplayName("오래된 버전의 이벤트가 늦게 와도 최신 문서를 유지한다.")
    void staleVersionIgnored() throws Exception {
        // Given
        UUID shopId = Ids.newId();
        UUID product1Id = Ids.newId();

        ProductUpsertedEvent v3 = new ProductUpsertedEvent(product1Id, shopId, "순서역전 버전삼", "세 번째 내용", "ETC", 3000, "ON_SALE", false, 3);
        ProductUpsertedEvent v2 = new ProductUpsertedEvent(product1Id, shopId, "순서역전 버전이", "두 번째 내용", "ETC", 2000, "ON_SALE", false, 2);

        // When & Then
        productIndexer.upsert(v3, "products");

        assertThatCode(() -> productIndexer.upsert(v2, "products")).doesNotThrowAnyException();

        assertThat(testFixtures.productSearchDocOf(product1Id).version()).isEqualTo(3);
        assertThat(testFixtures.productSearchDocOf(product1Id).name()).isEqualTo("순서역전 버전삼");
    }

    @Test
    @DisplayName("같은 이벤트를 두 번 받아도 문서 버전이 그대로다.")
    void duplicateEventIgnored() throws Exception {
        // Given
        UUID shopId = Ids.newId();
        UUID product1Id = Ids.newId();

        ProductUpsertedEvent Y = new ProductUpsertedEvent(product1Id, shopId, "중복이벤트 상품", "같은 이벤트 검증", "ETC", 1000, "ON_SALE", false, 1);

        // When & Then
        productIndexer.upsert(Y, "products");

        assertThatCode(() -> productIndexer.upsert(Y, "products")).doesNotThrowAnyException();

        assertThat(testFixtures.productSearchDocOf(product1Id).version()).isEqualTo(1);
    }

    @Test
    @DisplayName("단종 상품의 ES 문서는 남는다.")
    void discontinuedDocRemains() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "단종문서 캡슐",
                "혈행 개선 보충제",
                ProductCategory.HEALTH,
                22000L,
                10,
                false
        ));

        updateProductService.changeStatus(sellerId, shopId, product1Id, ProductStatus.DISCONTINUED);

        outboxRelay.relayOnce();

        // When & Then
        Eventually.await(Duration.ofSeconds(10), () -> {
            assertThat(testFixtures.productSearchDocOf(product1Id).found()).isEqualTo(true);
            assertThat(testFixtures.productSearchDocOf(product1Id).status()).isEqualTo("DISCONTINUED");
        });
    }

    @Test
    @DisplayName("복합어의 일부로 상품을 검색한다.")
    void searchByCompoundPart() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "멀티미네랄 정",
                "하루 한 알",
                ProductCategory.HEALTH,
                18000L,
                10,
                false
        ));

        outboxRelay.relayOnce();

        // When & Then
        Eventually.await(Duration.ofSeconds(10), () -> {
            mockMvc.perform(get("/api/search/products")
                            .param("q", "미네랄"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(product1Id.toString()));
        });
    }

    @Test
    @DisplayName("자동완성은 두 단어를 이어서 맞춘다.")
    void autocompleteTwoWords() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "허브 티백",
                "하루 한 알",
                ProductCategory.HEALTH,
                18000L,
                10,
                false
        ));

        registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "허브 오일",
                "하루 한 알",
                ProductCategory.HEALTH,
                18000L,
                10,
                false
        ));

        registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "티백 허브",
                "하루 한 알",
                ProductCategory.HEALTH,
                18000L,
                10,
                false
        ));

        outboxRelay.relayOnce();

        // When & Then
        Eventually.await(Duration.ofSeconds(10), () -> {
            mockMvc.perform(get("/api/search/autocomplete")
                            .param("q", "허브"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        });

        mockMvc.perform(get("/api/search/autocomplete")
                        .param("q", "허브 티"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value("허브 티백"));
    }

    @Test
    @DisplayName("자동완성은 이름이 같은 상품을 하나로 묶어 10개를 채운다.")
    void autocompleteCollapsesSameName() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");

        for (int i = 0; i < 12; i++) {
            registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                    "쿠키맛",
                    "하루 한 알",
                    ProductCategory.FOOD,
                    18000L,
                    10,
                    false
            ));
        }

        for (int i = 9; i >= 1; i--) {
            registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                    "쿠키맛 %02d".formatted(i),
                    "하루 한 알",
                    ProductCategory.FOOD,
                    18000L,
                    10,
                    false
            ));
        }

        outboxRelay.relayOnce();

        // When & Then
        Eventually.await(Duration.ofSeconds(10), () -> {
            MvcResult result = mockMvc.perform(get("/api/search/autocomplete")
                            .param("q", "쿠키맛"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(10))
                    .andReturn();

            List<String> autoCompletes = JsonPath.read(result.getResponse().getContentAsString(), "$");
            assertThat(autoCompletes).containsExactly("쿠키맛", "쿠키맛 01", "쿠키맛 02", "쿠키맛 03", "쿠키맛 04", "쿠키맛 05", "쿠키맛 06", "쿠키맛 07", "쿠키맛 08", "쿠키맛 09");
        });
    }

    @Test
    @DisplayName("자동완성은 공백을 무시한다.")
    void autocompleteIgnoresWhitespace() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");

        registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "캐모마일 차",
                "하루 한 알",
                ProductCategory.FOOD,
                18000L,
                10,
                false
        ));

        registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "캐모마일차 세트",
                "하루 한 알",
                ProductCategory.FOOD,
                18000L,
                10,
                false
        ));

        registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "캐모마일 오일",
                "하루 한 알",
                ProductCategory.FOOD,
                18000L,
                10,
                false
        ));

        outboxRelay.relayOnce();

        // When & Then
        Eventually.await(Duration.ofSeconds(10), () -> {
            mockMvc.perform(get("/api/search/autocomplete")
                            .param("q", "캐모마일"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3));
        });

        mockMvc.perform(get("/api/search/autocomplete")
                        .param("q", "캐모마일차"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*]").value(containsInAnyOrder("캐모마일차 세트", "캐모마일 차")));

        mockMvc.perform(get("/api/search/autocomplete")
                        .param("q", "캐모마일 차"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*]").value(containsInAnyOrder("캐모마일차 세트", "캐모마일 차")));
    }

    @Test
    @DisplayName("nori가 감탄사·부사로 판단한 이름도 찾는다.")
    void searchNameMisjudgedAsInterjection() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");

        UUID product1Id = registerProductService.register(sellerId, shopId, new RegisterProductCommand(
                "마스크팩 세트",
                "보습 케어",
                ProductCategory.BEAUTY,
                12000L,
                10,
                false
        ));

        outboxRelay.relayOnce();

        // When & Then
        Eventually.await(Duration.ofSeconds(10), () -> {
            mockMvc.perform(get("/api/search/products")
                            .param("q", "마스크팩"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(product1Id.toString()));
        });
    }
}
