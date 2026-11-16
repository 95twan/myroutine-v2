package com.myroutine.order.application;

import com.myroutine.common.model.Ids;
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
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
class CheckoutLimitConcurrencyTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CheckoutLimitConcurrencyTest(
            MockMvc mockMvc,
            TestFixtures testFixtures,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("같은 회원이 동시에 체크아웃해도 결제 대기 주문은 3건을 넘지 않는다.")
    void checkoutPendingOrderLimitConcurrently() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        String token = testFixtures.login("buyer@test.com");
        UUID addressId = testFixtures.address(buyerId);

        UUID cartItemId = testFixtures.addToCart(buyerId, productId, 1);

        CheckoutRequest request = new CheckoutRequest(List.of(cartItemId), addressId);

        Callable<MvcResult> task = () -> mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        // When
        List<ConcurrencyRunner.Result<MvcResult>> result = ConcurrencyRunner.run(5, task);

        // Then
        assertThat(result).allMatch(ConcurrencyRunner.Result::ok);
        List<MvcResult> responses = result.stream().map(ConcurrencyRunner.Result::value).toList();
        List<Integer> statuses = responses.stream().map(r -> r.getResponse().getStatus()).toList();
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(3);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(2);

        for (MvcResult response : responses) {
            if (response.getResponse().getStatus() == 409) {
                String code = objectMapper.readTree(response.getResponse().getContentAsString()).get("code").asString();
                assertThat(code).isEqualTo("PENDING_ORDER_LIMIT_EXCEEDED");
            }
        }

        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders.orders WHERE member_id = ?", Integer.class, buyerId);
        assertThat(orderCount).isEqualTo(3);
    }
}
