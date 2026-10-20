package com.myroutine.order.web;

import com.myroutine.order.application.CartResult;
import com.myroutine.order.application.CartService;
import com.myroutine.order.domain.OrderStatus;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.StockSnapshot;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OrderControllerTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final CartService cartService;
    private final ObjectMapper objectMapper;

    @Autowired
    public OrderControllerTest(TestFixtures testFixtures, MockMvc mockMvc, CartService cartService, ObjectMapper objectMapper) {
        this.testFixtures = testFixtures;
        this.mockMvc = mockMvc;
        this.cartService = cartService;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("체크아웃하면 PENDING_PAYMENT 주문이 만들어지고 재고가 예약되며 장바구니는 그대로다. (pgOrderId는 ORD-{orderId})")
    void checkout() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);
        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 2);

        CartResult cart = cartService.getCart(buyerId);
        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId), addressId);

        // When
        MvcResult result = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // Then
        CheckoutResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), CheckoutResponse.class);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.pgOrderId()).isEqualTo("ORD-" + response.orderId());
        StockSnapshot stockSnapshot = testFixtures.stockOf(productId);
        assertThat(stockSnapshot.available()).isEqualTo(98);
        assertThat(stockSnapshot.reserved()).isEqualTo(2);
        assertThat(cart).isEqualTo(cartService.getCart(buyerId));
    }

    @Test
    @DisplayName("요청에 price를 넣어도 무시하고 서버 가격으로 계산한다.")
    void checkoutIgnoresRequestPrice() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);
        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 2);

        // When
        MvcResult result = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\":[\"%s\"],\"addressId\":\"%s\", \"price\":%d}".formatted(cartItemId, addressId, 10)))
                .andExpect(status().isCreated())
                .andReturn();

        CheckoutResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), CheckoutResponse.class);

        // Then
        assertThat(response.totalAmount()).isEqualTo(1000 * 2);
    }

    @Test
    @DisplayName("체크아웃을 실패한다. (상품 2개 중 두 번째 재고 부족, 422 OUT_OF_STOCK, 주문 0건·첫 번째 상품 reserved 0·장바구니 그대로)")
    void checkoutRollsBackWhenOutOfStock() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId, 1000, 100);
        UUID productId2 = testFixtures.registerProduct(memberId, shopId, 5000, 3);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);
        UUID cartItemId1 = testFixtures.addToCart(buyerId, productId1, 2);
        UUID cartItemId2 = testFixtures.addToCart(buyerId, productId2, 5);

        CartResult cart = cartService.getCart(buyerId);
        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId1, cartItemId2), addressId);

        // When & Then
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.details.productIds", contains(productId2.toString())));

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        assertThat(cart).isEqualTo(cartService.getCart(buyerId));
        StockSnapshot stockSnapshot = testFixtures.stockOf(productId1);
        assertThat(stockSnapshot.available()).isEqualTo(100);
        assertThat(stockSnapshot.reserved()).isEqualTo(0);
    }

    @Test
    @DisplayName("체크아웃을 실패한다. (자기 가게 상품, 422 OWN_SHOP_PRODUCT)")
    void checkoutFailsOwnShopProduct() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId1 = testFixtures.openShop(memberId, "1111111111");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId1, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID shopId2 = testFixtures.openShop(buyerId, "2222222222");
        UUID productId2 = testFixtures.registerProduct(buyerId, shopId2, 1000, 100);
        UUID addressId = testFixtures.address(buyerId);

        UUID cartItemId1 = testFixtures.addToCart(buyerId, productId1, 2);
        UUID cartItemId2 = testFixtures.addToCart(buyerId, productId2, 5);

        CartResult cart = cartService.getCart(buyerId);
        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId1, cartItemId2), addressId);

        // When & Then
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("OWN_SHOP_PRODUCT"))
                .andExpect(jsonPath("$.details.productIds", contains(productId2.toString())));

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        assertThat(cart).isEqualTo(cartService.getCart(buyerId));
        StockSnapshot stockSnapshot = testFixtures.stockOf(productId1);
        assertThat(stockSnapshot.available()).isEqualTo(100);
        assertThat(stockSnapshot.reserved()).isEqualTo(0);
    }

    @Test
    @DisplayName("체크아웃을 실패한다. (CLOSED 가게 상품, 422 SHOP_NOT_ACTIVE)")
    void checkoutFailsClosedShop() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId, 1000, 100);
        UUID productId2 = testFixtures.registerProduct(memberId, shopId, 5000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);
        UUID cartItemId1 = testFixtures.addToCart(buyerId, productId1, 2);
        UUID cartItemId2 = testFixtures.addToCart(buyerId, productId2, 5);

        testFixtures.closeShop(shopId);

        CartResult cart = cartService.getCart(buyerId);
        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId1, cartItemId2), addressId);

        // When & Then
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SHOP_NOT_ACTIVE"))
                .andExpect(jsonPath("$.details.shopIds", contains(shopId.toString())));

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        assertThat(cart).isEqualTo(cartService.getCart(buyerId));
    }

    @Test
    @DisplayName("체크아웃을 실패한다. (다른 회원의 배송지, 404)")
    void checkoutFailsOthersAddress() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId, 1000, 100);
        UUID productId2 = testFixtures.registerProduct(memberId, shopId, 5000, 100);
        UUID addressId = testFixtures.address(memberId);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID cartItemId1 = testFixtures.addToCart(buyerId, productId1, 2);
        UUID cartItemId2 = testFixtures.addToCart(buyerId, productId2, 5);

        CartResult cart = cartService.getCart(buyerId);
        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId1, cartItemId2), addressId);

        // When & Then
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_ADDRESS_NOT_FOUND"));

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        assertThat(cart).isEqualTo(cartService.getCart(buyerId));
    }

    @Test
    @DisplayName("체크아웃을 실패한다. (다른 회원의 장바구니 항목, 404 CART_ITEM_NOT_FOUND)")
    void checkoutFailsOthersCartItem() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId, 1000, 100);
        UUID productId2 = testFixtures.registerProduct(memberId, shopId, 5000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID cartItemId1 = testFixtures.addToCart(buyerId, productId1, 2);
        UUID cartItemId2 = testFixtures.addToCart(buyerId, productId2, 5);

        CartResult cart = cartService.getCart(buyerId);

        UUID otherMemberId = testFixtures.signup("other@test.com");
        String other = testFixtures.login("other@test.com");
        UUID addressId = testFixtures.address(otherMemberId);
        testFixtures.addToCart(otherMemberId, productId1, 2);

        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId1, cartItemId2), addressId);

        // When & Then
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + other)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        assertThat(cart).isEqualTo(cartService.getCart(buyerId));
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 두 번 체크아웃해도 주문은 1건이고 응답이 같다.")
    void checkoutWithSameIdempotencyKeyTwice() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);
        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 2);

        CartResult cart = cartService.getCart(buyerId);

        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId), addressId);

        String idempotencyKey = UUID.randomUUID().toString();

        // When & Then
        MvcResult result1 = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));

        JsonNode tree1 = objectMapper.readTree(result1.getResponse().getContentAsString());
        JsonNode tree2 = objectMapper.readTree(result2.getResponse().getContentAsString());
        assertThat(tree1).isEqualTo(tree2);
        assertThat(cart).isEqualTo(cartService.getCart(buyerId));
    }

    @Test
    @DisplayName("같은 장바구니 항목으로 다른 Idempotency-Key로 두 번 체크아웃하면 주문이 2건 만들어지고 장바구니는 그대로다.")
    void checkoutSameCartItemsWithDifferentKeys() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);
        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 2);

        CartResult cart = cartService.getCart(buyerId);

        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId), addressId);

        // When & Then
        MvcResult result1 = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)));

        JsonNode tree1 = objectMapper.readTree(result1.getResponse().getContentAsString());
        JsonNode tree2 = objectMapper.readTree(result2.getResponse().getContentAsString());
        assertThat(tree1).isNotEqualTo(tree2);
        assertThat(cart).isEqualTo(cartService.getCart(buyerId));
    }

}
