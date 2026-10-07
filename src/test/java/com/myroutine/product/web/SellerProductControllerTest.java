package com.myroutine.product.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.product.domain.ProductCategory;
import com.myroutine.product.domain.StockMovementType;
import com.myroutine.product.domain.StockRefType;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SellerProductControllerTest extends IntegrationTestSupport {
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TestFixtures testFixtures;

    @Autowired
    public SellerProductControllerTest(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate, TestFixtures testFixtures) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.testFixtures = testFixtures;
    }

    @Test
    @DisplayName("상품을 등록하면 상품, 재고, 재고 이력이 함께 저장된다.")
    void registerProduct() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        RegisterProductRequest request = new RegisterProductRequest(
                "product",
                "product description",
                ProductCategory.ETC,
                10000L,
                100,
                false
        );

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/shops/" + shopId + "/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID productId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.productId"));

        Integer productCount = jdbcTemplate.queryForObject("SELECT count(*) FROM product.product WHERE id = ?", Integer.class, productId);
        Map<String, Object> stockMap = jdbcTemplate.queryForMap("SELECT available, reserved, sold, received FROM product.stock WHERE product_id = ?", productId);
        Map<String, Object> stockMovementMap = jdbcTemplate.queryForMap("SELECT type, quantity, ref_type FROM product.stock_movement WHERE product_id = ?", productId);

        assertThat(productCount).isEqualTo(1);
        assertThat(stockMap.get("available")).isEqualTo(100);
        assertThat(stockMap.get("reserved")).isEqualTo(0);
        assertThat(stockMap.get("sold")).isEqualTo(0);
        assertThat(stockMap.get("received")).isEqualTo(100);
        assertThat(stockMovementMap.get("type")).isEqualTo(StockMovementType.RECEIVE.name());
        assertThat(stockMovementMap.get("quantity")).isEqualTo(100);
        assertThat(stockMovementMap.get("ref_type")).isEqualTo(StockRefType.PRODUCT_REGISTER.name());
    }

    @Test
    @DisplayName("상품 등록을 실패한다. (다른 회원의 가게)")
    void registerProductOfOtherMemberShop() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        String token = testFixtures.token("other@test.com");
        RegisterProductRequest request = new RegisterProductRequest(
                "product",
                "product description",
                ProductCategory.ETC,
                10000L,
                100,
                false
        );

        // When & Then
        mockMvc.perform(post("/api/shops/" + shopId + "/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("상품 등록을 실패한다. (폐업한 가게)")
    void registerProductToClosedShop() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        testFixtures.closeShop(shopId);
        RegisterProductRequest request = new RegisterProductRequest(
                "product",
                "product description",
                ProductCategory.ETC,
                10000L,
                100,
                false
        );

        // When & Then
        mockMvc.perform(post("/api/shops/" + shopId + "/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SHOP_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("상품 등록을 실패한다. (없는 가게)")
    void registerProductToNotExistShop() throws Exception {
        // Given
        String token = testFixtures.token("test@test.com");
        RegisterProductRequest request = new RegisterProductRequest(
                "product",
                "product description",
                ProductCategory.ETC,
                10000L,
                100,
                false
        );

        // When & Then
        mockMvc.perform(post("/api/shops/" + UUID.randomUUID() + "/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOP_NOT_FOUND"));
    }

    @Test
    @DisplayName("상품 등록을 실패한다. (가격 0)")
    void registerProductWithZeroPrice() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        RegisterProductRequest request = new RegisterProductRequest(
                "product",
                "product description",
                ProductCategory.ETC,
                0L,
                100,
                false
        );

        // When & Then
        mockMvc.perform(post("/api/shops/" + shopId + "/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("판매자 상품 목록을 조회한다.")
    void getShopProducts() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        testFixtures.registerProduct(memberId, shopId);
        testFixtures.registerProduct(memberId, shopId);
        testFixtures.registerProduct(memberId, shopId);

        // When & Then
        mockMvc.perform(get("/api/shops/" + shopId + "/products")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(hasSize(3)));
    }

    @Test
    @DisplayName("판매자 상품 목록 조회를 실패한다. (다른 회원의 가게)")
    void getShopProductsOfOtherMemberShop() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        String token = testFixtures.token("other@test.com");

        // When & Then
        mockMvc.perform(get("/api/shops/" + shopId + "/products")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 51})
    @DisplayName("판매자 상품 목록 조회를 실패한다. (size 범위 초과)")
    void getShopProductsWithInvalidSize(int size) throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        testFixtures.registerProduct(memberId, shopId);
        testFixtures.registerProduct(memberId, shopId);
        testFixtures.registerProduct(memberId, shopId);

        // When & Then
        mockMvc.perform(get("/api/shops/" + shopId + "/products")
                        .header("Authorization", "Bearer " + token)
                        .param("size", String.valueOf(size)))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details").value(hasKey("size")));
    }

    @Test
    @DisplayName("폐업한 가게의 소유자는 상품 목록을 조회할 수 있다.")
    void getShopProductsOfClosedShop() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        testFixtures.registerProduct(memberId, shopId);
        testFixtures.registerProduct(memberId, shopId);
        testFixtures.registerProduct(memberId, shopId);
        testFixtures.closeShop(shopId);

        // When & Then
        mockMvc.perform(get("/api/shops/" + shopId + "/products")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(hasSize(3)));
    }

}
