package com.myroutine.product.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.product.domain.ProductCategory;
import com.myroutine.product.domain.ProductStatus;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProductControllerTest extends IntegrationTestSupport {
    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final EntityManagerFactory entityManagerFactory;

    @Autowired
    public ProductControllerTest(MockMvc mockMvc, TestFixtures testFixtures, EntityManagerFactory entityManagerFactory) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.entityManagerFactory = entityManagerFactory;
    }

    @Test
    @DisplayName("토큰 없이 상품 목록을 조회해도 성공한다.")
    void getPublicProductsWithoutToken() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        testFixtures.registerProduct(memberId, shopId);
        testFixtures.registerProduct(memberId, shopId);
        testFixtures.registerProduct(memberId, shopId);

        // When & Then
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(hasSize(3)));

    }

    @Test
    @DisplayName("커서로 조회하면 상품이 중복과 누락 없이 한 번씩 조회된다.")
    void getPublicProductsByCursor() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        List<UUID> productIds = List.of(
                testFixtures.registerProduct(memberId, shopId),
                testFixtures.registerProduct(memberId, shopId),
                testFixtures.registerProduct(memberId, shopId),
                testFixtures.registerProduct(memberId, shopId),
                testFixtures.registerProduct(memberId, shopId)
        );

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/products")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.items[*].id");
        String nextCursor = JsonPath.read(body, "$.nextCursor");

        List<String> collected = new ArrayList<>(ids);

        testFixtures.registerProduct(memberId, shopId);

        while (nextCursor != null) {
            result = mockMvc.perform(get("/api/products")
                            .param("size", "2")
                            .param("cursor", nextCursor))
                    .andExpect(status().isOk())
                    .andReturn();

            body = result.getResponse().getContentAsString();
            ids = JsonPath.read(body, "$.items[*].id");
            nextCursor = JsonPath.read(body, "$.nextCursor");
            collected.addAll(ids);
        }

        assertThat(collected).containsExactlyElementsOf(productIds.reversed().stream().map(UUID::toString).toList());
    }

    @Test
    @DisplayName("카테고리로 상품 목록을 필터링한다.")
    void getPublicProductsByCategory() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        testFixtures.registerProduct(memberId, shopId, ProductCategory.BEAUTY, 10);
        testFixtures.registerProduct(memberId, shopId, ProductCategory.BEAUTY, 10);
        testFixtures.registerProduct(memberId, shopId, ProductCategory.FOOD, 10);
        testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 10);

        // When & Then
        mockMvc.perform(get("/api/products")
                        .param("category", ProductCategory.BEAUTY.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(hasSize(2)))
                .andExpect(jsonPath("$.items[0].category").value(ProductCategory.BEAUTY.name()));
    }

    @Test
    @DisplayName("숨김 상품은 목록에서 제외된다.")
    void getPublicProductsWithoutHiddenProduct() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        testFixtures.registerProduct(memberId, shopId);
        testFixtures.registerProduct(memberId, shopId);
        UUID productId = testFixtures.registerProduct(memberId, shopId);
        testFixtures.hideProduct(productId);

        // When & Then
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(hasSize(2)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 51})
    @DisplayName("상품 목록 조회를 실패한다. (size 범위 초과)")
    void getPublicProductsWithInvalidSize(int size) throws Exception {
        // Given

        // When & Then
        mockMvc.perform(get("/api/products")
                        .param("size", String.valueOf(size)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details").value(hasKey("size")));
    }

    @Test
    @DisplayName("상품 수가 늘어도 목록 조회의 쿼리 수는 같다.")
    void getPublicProductsQueryCountIsConstant() throws Exception {
        // Given
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        testFixtures.registerProduct(memberId, shopId);
        testFixtures.registerProduct(memberId, shopId);
        testFixtures.registerProduct(memberId, shopId);

        // When & Then
        statistics.clear();

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(hasSize(3)));

        long first = statistics.getPrepareStatementCount();

        testFixtures.registerProduct(memberId, shopId);
        testFixtures.registerProduct(memberId, shopId);
        testFixtures.registerProduct(memberId, shopId);
        testFixtures.registerProduct(memberId, shopId);
        testFixtures.registerProduct(memberId, shopId);
        testFixtures.registerProduct(memberId, shopId);
        testFixtures.registerProduct(memberId, shopId);

        statistics.clear();

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(hasSize(10)));

        long second = statistics.getPrepareStatementCount();

        assertThat(first).isGreaterThan(0);
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("상품 상세를 조회한다.")
    void getProduct() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId);

        // When & Then
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ProductStatus.ON_SALE.name()))
                .andExpect(jsonPath("$.inStock").value(true));
    }

    @Test
    @DisplayName("상품 상세 조회를 실패한다. (숨김 상품)")
    void getHiddenProduct() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId);
        testFixtures.hideProduct(productId);

        // When & Then
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isNotFound());
    }
}
