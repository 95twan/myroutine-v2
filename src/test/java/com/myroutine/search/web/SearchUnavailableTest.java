package com.myroutine.search.web;

import com.myroutine.common.model.Ids;
import com.myroutine.product.domain.ProductCategory;
import com.myroutine.product.web.RegisterProductRequest;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SearchUnavailableTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;
    private final HealthEndpoint healthEndpoint;

    @Autowired
    public SearchUnavailableTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper, HealthEndpoint healthEndpoint) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
        this.healthEndpoint = healthEndpoint;
    }

    @Test
    @DisplayName("ES가 멈춰도 검색만 실패한다.")
    void onlySearchFailsWhenEsPaused() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        String token = testFixtures.login("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");

        UUID productId = testFixtures.registerProduct(sellerId, shopId);

        es.getDockerClient().pauseContainerCmd(es.getContainerId()).exec();
        try {
            Instant start = Instant.now();
            mockMvc.perform(get("/api/search/products")
                            .param("q", "product"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SEARCH_UNAVAILABLE"));
            Instant end = Instant.now();
            assertThat(Duration.between(start, end)).isLessThan(Duration.ofSeconds(10));

            mockMvc.perform(get("/api/search/autocomplete")
                            .param("q", "pr"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SEARCH_UNAVAILABLE"));

            mockMvc.perform(get("/api/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(productId.toString()));

            mockMvc.perform(post("/api/shops/{shopId}/products", shopId)
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", Ids.newId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RegisterProductRequest(
                                    "product2",
                                    "description2",
                                    ProductCategory.ETC,
                                    1000L,
                                    10,
                                    false
                            ))))
                    .andExpect(status().isCreated());

            assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);

        } finally {
            es.getDockerClient().unpauseContainerCmd(es.getContainerId()).exec();
        }

    }
}
