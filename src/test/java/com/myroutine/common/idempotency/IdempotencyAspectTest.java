package com.myroutine.common.idempotency;

import com.myroutine.common.model.Ids;
import com.myroutine.shop.web.OpenShopRequest;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IdempotencyAspectTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public IdempotencyAspectTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("멱등 키가 필요한 API 호출을 실패한다. (Idempotency-Key 헤더 없음, 400 IDEMPOTENCY_KEY_REQUIRED)")
    void requireIdempotencyKey() throws Exception {
        // Given
        String token = testFixtures.token("test@test.com");
        OpenShopRequest request = new OpenShopRequest(
                "shop",
                "1111111111",
                "shop@test.com",
                "010-1234-5678",
                "shop address"
        );

        // When & Then
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("같은 키·같은 바디로 두 번 요청해도 가게는 1개만 만들어지고 두 응답 바디가 같다.")
    void sameKeySameBodyReturnsStoredResponse() throws Exception {
        // Given
        String token = testFixtures.token("test@test.com");
        OpenShopRequest request = new OpenShopRequest(
                "shop",
                "1111111111",
                "shop@test.com",
                "010-1234-5678",
                "shop address"
        );

        UUID idempotencyKey = Ids.newId();

        // When & Then
        MvcResult result1 = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult result2 = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(get("/api/shops/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        JsonNode tree1 = objectMapper.readTree(result1.getResponse().getContentAsString());
        JsonNode tree2 = objectMapper.readTree(result2.getResponse().getContentAsString());

        assertThat(tree1).isEqualTo(tree2);
    }

    @Test
    @DisplayName("같은 키로 다른 바디를 보내면 요청을 실패한다. (422 IDEMPOTENCY_KEY_REUSED)")
    void sameKeyDifferentBodyFails() throws Exception {
        // Given
        String token = testFixtures.token("test@test.com");
        OpenShopRequest request1 = new OpenShopRequest(
                "shop",
                "1111111111",
                "shop@test.com",
                "010-1234-5678",
                "shop address"
        );

        UUID idempotencyKey = Ids.newId();

        OpenShopRequest request2 = new OpenShopRequest(
                "shop2",
                "2222222222",
                "shop2@test.com",
                "010-2222-2222",
                "shop2 address"
        );

        // When & Then
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        mockMvc.perform(get("/api/shops/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("에러 응답을 받은 요청의 키는 지워져 같은 키·같은 바디로 다시 보내면 다시 처리된다. (사업자번호 중복 409가 또 409)")
    void keyIsDeletedAfterErrorResponse() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        testFixtures.openShop(memberId, "1111111111");
        OpenShopRequest request = new OpenShopRequest(
                "shop",
                "1111111111",
                "shop@test.com",
                "010-1234-5678",
                "shop address"
        );

        UUID idempotencyKey = Ids.newId();

        // When & Then
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOP_BUSINESS_NUMBER_DUPLICATED"));

        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOP_BUSINESS_NUMBER_DUPLICATED"));

        mockMvc.perform(get("/api/shops/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("만료된 키와 같은 키로 같은 바디를 보내면 저장된 응답이 재생되지 않고 새로 처리된다. (사업자번호 중복 409)")
    void expiredKeyIsProcessedAgain() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        OpenShopRequest request = new OpenShopRequest(
                "shop",
                "1111111111",
                "shop@test.com",
                "010-1234-5678",
                "shop address"
        );

        String idempotencyKey = Ids.newId().toString();

        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        jdbcTemplate.update("UPDATE common.idempotency_key SET expires_at = NOW() - interval '1 hour' WHERE member_id = ? AND idempotency_key = ?", memberId, idempotencyKey);

        // When & Then
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOP_BUSINESS_NUMBER_DUPLICATED"));

        mockMvc.perform(get("/api/shops/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("만료된 키와 같은 키로 다른 바디를 보내도 새로 처리된다. (422 IDEMPOTENCY_KEY_REUSED가 아니다)")
    void expiredKeyWithDifferentBodyIsProcessedAgain() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        OpenShopRequest request1 = new OpenShopRequest(
                "shop",
                "1111111111",
                "shop@test.com",
                "010-1234-5678",
                "shop address"
        );

        String idempotencyKey = Ids.newId().toString();

        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        jdbcTemplate.update("UPDATE common.idempotency_key SET expires_at = NOW() - interval '1 hour' WHERE member_id = ? AND idempotency_key = ?", memberId, idempotencyKey);

        OpenShopRequest request2 = new OpenShopRequest(
                "shop2",
                "2222222222",
                "shop2@test.com",
                "010-2222-2222",
                "shop2 address"
        );

        // When & Then
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/shops/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

}
