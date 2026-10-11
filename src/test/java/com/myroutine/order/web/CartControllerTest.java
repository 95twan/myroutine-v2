package com.myroutine.order.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.model.Ids;
import com.myroutine.product.web.UpdateProductRequest;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CartControllerTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;
    private final EntityManagerFactory entityManagerFactory;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CartControllerTest(
            MockMvc mockMvc,
            TestFixtures testFixtures,
            ObjectMapper objectMapper,
            EntityManagerFactory entityManagerFactory,
            JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
        this.entityManagerFactory = entityManagerFactory;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("같은 상품을 두 번 담으면 한 행에 수량이 합산된다.")
    void addSameProductTwice() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 10000, 10);
        AddCartItemRequest request1 = new AddCartItemRequest(productId, 2);
        AddCartItemRequest request2 = new AddCartItemRequest(productId, 3);

        MvcResult result1 = mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated())
                .andReturn();

        String cartItemId1 = JsonPath.read(result1.getResponse().getContentAsString(), "$.cartItemId");
        String cartItemId2 = JsonPath.read(result2.getResponse().getContentAsString(), "$.cartItemId");

        // When & Then
        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(50000))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(5));

        assertThat(cartItemId1).isEqualTo(cartItemId2);
    }

    @Test
    @DisplayName("합산 수량이 99를 넘으면 99로 맞춘다.")
    void addItemCappedAt99() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 10000, 200);
        AddCartItemRequest request1 = new AddCartItemRequest(productId, 98);
        AddCartItemRequest request2 = new AddCartItemRequest(productId, 5);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        // When & Then
        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(99));
    }

    @Test
    @DisplayName("상품 가격을 바꾸면 장바구니 조회에도 바뀐 가격이 반영된다.")
    void getCartWithChangedPrice() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 10000, 200);
        AddCartItemRequest request = new AddCartItemRequest(productId, 5);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].unitPrice").value(10000))
                .andExpect(jsonPath("$.totalAmount").value(50000));

        UpdateProductRequest updateProductRequest = new UpdateProductRequest(
                null,
                null,
                null,
                2000L,
                null
        );

        mockMvc.perform(patch("/api/shops/{shopId}/products/{productId}", shopId, productId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateProductRequest)))
                .andExpect(status().isOk());

        // When & Then
        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].unitPrice").value(2000))
                .andExpect(jsonPath("$.totalAmount").value(10000));
    }

    @Test
    @DisplayName("숨김 상품은 구매 불가(NOT_ON_SALE)로 표시되고 총액에서 제외된다.")
    void getCartWithHiddenProduct() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId, 10000, 200);
        UUID productId2 = testFixtures.registerProduct(memberId, shopId, 5000, 200);
        AddCartItemRequest request1 = new AddCartItemRequest(productId1, 5);
        AddCartItemRequest request2 = new AddCartItemRequest(productId2, 2);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        testFixtures.hideProduct(productId1);

        // When & Then
        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[?(@.productId == '%s')].purchasable", productId1).value(false))
                .andExpect(jsonPath("$.items[?(@.productId == '%s')].unavailableReason", productId1).value("NOT_ON_SALE"))
                .andExpect(jsonPath("$.items[?(@.productId == '%s')].purchasable", productId2).value(true))
                .andExpect(jsonPath("$.items[?(@.productId == '%s')].unavailableReason", productId2).value(contains(nullValue())))
                .andExpect(jsonPath("$.totalAmount").value(10000));
    }

    @Test
    @DisplayName("재고가 0인 상품은 구매 불가(OUT_OF_STOCK)로 표시되고 총액에서 제외된다.")
    void getCartWithOutOfStockProduct() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId, 10000, 0);
        UUID productId2 = testFixtures.registerProduct(memberId, shopId, 5000, 200);
        AddCartItemRequest request1 = new AddCartItemRequest(productId1, 5);
        AddCartItemRequest request2 = new AddCartItemRequest(productId2, 2);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());


        // When & Then
        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[?(@.productId == '%s')].purchasable", productId1).value(false))
                .andExpect(jsonPath("$.items[?(@.productId == '%s')].unavailableReason", productId1).value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.items[?(@.productId == '%s')].purchasable", productId2).value(true))
                .andExpect(jsonPath("$.items[?(@.productId == '%s')].unavailableReason", productId2).value(contains(nullValue())))
                .andExpect(jsonPath("$.totalAmount").value(10000));
    }

    @Test
    @DisplayName("다른 회원의 장바구니 항목 수량 변경을 실패한다. (내 장바구니의 항목이 아님)")
    void changeQuantityOfOthersItem() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 10000, 200);
        AddCartItemRequest request = new AddCartItemRequest(productId, 5);

        MvcResult result = mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String cartItemId = JsonPath.read(result.getResponse().getContentAsString(), "$.cartItemId");

        String otherToken = testFixtures.token("other@test.com");
        ChangeQuantityRequest changeQuantityRequest = new ChangeQuantityRequest(10);

        // When & Then
        mockMvc.perform(patch("/api/cart/items/{id}", cartItemId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeQuantityRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(5));
    }

    @Test
    @DisplayName("다른 회원의 장바구니 항목 삭제를 실패한다. (내 장바구니의 항목이 아님)")
    void removeOthersItem() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 10000, 200);
        AddCartItemRequest request = new AddCartItemRequest(productId, 5);

        MvcResult result = mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String cartItemId = JsonPath.read(result.getResponse().getContentAsString(), "$.cartItemId");

        String otherToken = testFixtures.token("other@test.com");

        // When & Then
        mockMvc.perform(delete("/api/cart/items/{id}", cartItemId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(5));
    }

    @Test
    @DisplayName("장바구니 담기를 실패한다. (존재하지 않는 상품)")
    void addItemWithNonExistentProduct() throws Exception {
        // Given
        String token = testFixtures.token("test@test.com");
        AddCartItemRequest request = new AddCartItemRequest(Ids.newId(), 5);

        // When & Then
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("장바구니 항목이 늘어도 조회의 쿼리 수는 같다.")
    void getCartQueryCountIsConstant() throws Exception {
        // Given
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        for (int i = 0; i < 2; i++) {
            UUID productId = testFixtures.registerProduct(memberId, shopId, 10000, 200);
            AddCartItemRequest request = new AddCartItemRequest(productId, 1);
            mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        }

        // When & Then
        statistics.clear();

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)));

        long first = statistics.getPrepareStatementCount();

        for (int i = 0; i < 18; i++) {
            UUID productId = testFixtures.registerProduct(memberId, shopId, 10000, 200);
            AddCartItemRequest request = new AddCartItemRequest(productId, 1);
            mockMvc.perform(post("/api/cart/items")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        statistics.clear();

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(20)));

        long second = statistics.getPrepareStatementCount();

        assertThat(first).isGreaterThan(0);
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("수량을 변경하면 갱신된 전체 장바구니를 반환한다.")
    void changeQuantity() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 10000, 200);
        AddCartItemRequest request = new AddCartItemRequest(productId, 5);

        MvcResult result = mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String cartItemId = JsonPath.read(result.getResponse().getContentAsString(), "$.cartItemId");

        ChangeQuantityRequest changeQuantityRequest = new ChangeQuantityRequest(10);

        // When & Then
        mockMvc.perform(patch("/api/cart/items/{id}", cartItemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeQuantityRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(10))
                .andExpect(jsonPath("$.items[0].lineAmount").value(100000))
                .andExpect(jsonPath("$.totalAmount").value(100000));
    }

    @Test
    @DisplayName("장바구니 항목을 삭제하면 목록에서 사라진다.")
    void removeItem() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId, 10000, 200);
        UUID productId2 = testFixtures.registerProduct(memberId, shopId, 10000, 200);
        AddCartItemRequest request = new AddCartItemRequest(productId1, 5);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        request = new AddCartItemRequest(productId2, 5);

        MvcResult result = mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String cartItemId = JsonPath.read(result.getResponse().getContentAsString(), "$.cartItemId");

        // When & Then
        mockMvc.perform(delete("/api/cart/items/{id}", cartItemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[?(@.productId == '%s')]", productId1).exists())
                .andExpect(jsonPath("$.items[?(@.productId == '%s')]", productId2).doesNotExist());
    }

    @Test
    @DisplayName("장바구니에 담은 적이 없으면 빈 장바구니를 반환한다.")
    void getEmptyCart() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");

        // When & Then
        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalAmount").value(0));
    }

    @Test
    @DisplayName("상품 정보를 찾을 수 없는 항목은 구매 불가(NOT_FOUND)로 표시되고 총액에서 제외된다.")
    void getCartWithNotFoundProduct() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId, 10000, 200);
        UUID productId2 = testFixtures.registerProduct(memberId, shopId, 10000, 200);
        AddCartItemRequest request = new AddCartItemRequest(productId1, 5);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        request = new AddCartItemRequest(productId2, 5);

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        jdbcTemplate.update("DELETE FROM product.stock WHERE product_id = ?", productId1);

        // When & Then
        mockMvc.perform(get("/api/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[?(@.productId == '%s')].purchasable", productId1).value(false))
                .andExpect(jsonPath("$.items[?(@.productId == '%s')].unavailableReason", productId1).value("NOT_FOUND"))
                .andExpect(jsonPath("$.items[?(@.productId == '%s')].purchasable", productId2).value(true))
                .andExpect(jsonPath("$.totalAmount").value(50000));
    }

    @Test
    @DisplayName("장바구니 담기를 실패한다. (수량 범위 밖)")
    void addItemWithInvalidQuantity() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 10000, 200);
        AddCartItemRequest request = new AddCartItemRequest(productId, 100);

        // When & Then
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("수량 변경을 실패한다. (수량 범위 밖)")
    void changeQuantityWithInvalidQuantity() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 10000, 200);
        AddCartItemRequest request = new AddCartItemRequest(productId, 1);

        MvcResult result = mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String cartItemId = JsonPath.read(result.getResponse().getContentAsString(), "$.cartItemId");
        ChangeQuantityRequest changeQuantityRequest = new ChangeQuantityRequest(100);

        // When & Then
        mockMvc.perform(patch("/api/cart/items/{cartItemId}", cartItemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeQuantityRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
