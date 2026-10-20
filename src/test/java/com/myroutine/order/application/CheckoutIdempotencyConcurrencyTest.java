package com.myroutine.order.application;

import com.myroutine.order.web.CheckoutRequest;
import com.myroutine.support.ConcurrencyRunner;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
class CheckoutIdempotencyConcurrencyTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CheckoutIdempotencyConcurrencyTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 동시에 10번 체크아웃해도 주문은 1건이고 응답은 201과 409(IDEMPOTENCY_IN_PROGRESS)뿐이다.")
    void checkoutConcurrentlyWithSameKey() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);
        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 2);

        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId), addressId);

        String idempotencyKey = UUID.randomUUID().toString();

        // When
        List<ConcurrencyRunner.Result<MvcResult>> results = ConcurrencyRunner.run(10, () ->
                mockMvc.perform(post("/api/orders/checkout")
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn()
        );

        // Then
        assertThat(results).allMatch(ConcurrencyRunner.Result::ok);
        List<MvcResult> responses = results.stream().map(ConcurrencyRunner.Result::value).toList();
        List<Integer> statuses = responses.stream().map(r -> r.getResponse().getStatus()).toList();
        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses).contains(201);

        for (MvcResult response : responses) {
            if (response.getResponse().getStatus() == 409) {
                String code = objectMapper.readTree(response.getResponse().getContentAsString()).get("code").asString();
                assertThat(code).isEqualTo("IDEMPOTENCY_IN_PROGRESS");
            }
        }

        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders.orders WHERE member_id = ?", Integer.class, buyerId);
        assertThat(orderCount).isEqualTo(1);
        assertThat(testFixtures.stockOf(productId).reserved()).isEqualTo(2);
    }

}
