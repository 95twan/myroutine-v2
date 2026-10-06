package com.myroutine.shop.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ShopControllerTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final TestFixtures testFixtures;

    @Autowired
    public ShopControllerTest(MockMvc mockMvc, ObjectMapper objectMapper, TestFixtures testFixtures) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.testFixtures = testFixtures;
    }

    @Test
    @DisplayName("가게를 개설하면 내 가게 목록에서 조회된다.")
    void openShopAndListMine() throws Exception {
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
        MvcResult result = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String shopId = JsonPath.read(result.getResponse().getContentAsString(), "$.shopId");

        mockMvc.perform(get("/api/shops/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(shopId));
    }

    @Test
    @DisplayName("토큰 없이 가게를 조회해도 성공한다.")
    void getShopWithoutToken() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");

        // When & Then
        mockMvc.perform(get("/api/shops/" + shopId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shopId.toString()));
    }

    @Test
    @DisplayName("내 가게 목록 조회를 실패한다. (토큰 없음)")
    void getMyShopsWithoutToken() throws Exception {
        // Given

        // When & Then
        mockMvc.perform(get("/api/shops/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("가게를 수정한다.")
    void updateShop() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");

        UpdateShopRequest updateShopRequest = new UpdateShopRequest(
                "updated shop",
                null,
                null,
                "updated shop address"
        );

        // When & Then
        mockMvc.perform(patch("/api/shops/" + shopId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateShopRequest))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shopId.toString()))
                .andExpect(jsonPath("$.name").value("updated shop"))
                .andExpect(jsonPath("$.email").value("shop@test.com"))
                .andExpect(jsonPath("$.phone").value("010-1111-1111"))
                .andExpect(jsonPath("$.address").value("updated shop address"));
    }

    @Test
    @DisplayName("가게 수정을 실패한다. (빈 문자열)")
    void updateShopWithEmptyString() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");

        UpdateShopRequest updateShopRequest = new UpdateShopRequest(
                "",
                null,
                null,
                "updated shop address"
        );

        // When & Then
        mockMvc.perform(patch("/api/shops/" + shopId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateShopRequest))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("가게 수정을 실패한다. (다른 회원의 가게)")
    void updateShopOfOtherMember() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");

        String other = testFixtures.token("other@test.com");
        UpdateShopRequest updateShopRequest = new UpdateShopRequest(
                "updated shop",
                "othershop@test.com",
                "010-9999-9999",
                "updated shop address"
        );

        // When & Then
        mockMvc.perform(patch("/api/shops/" + shopId)
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateShopRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("가게 개설을 실패한다. (이미 등록된 사업자번호)")
    void openShopDuplicatedBusinessNumber() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        testFixtures.openShop(memberId, "1111111111");

        OpenShopRequest request2 = new OpenShopRequest(
                "shop2",
                "1111111111",
                "shop2@test.com",
                "010-2222-2222",
                "shop2 address"
        );

        // When & Then
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOP_BUSINESS_NUMBER_DUPLICATED"));
    }

    @Test
    @DisplayName("가게 수정을 실패한다. (폐업한 가게)")
    void updateClosedShop() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        String token = testFixtures.login("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        testFixtures.closeShop(shopId);

        UpdateShopRequest updateShopRequest = new UpdateShopRequest(
                "updated shop",
                "othershop@test.com",
                "010-9999-9999",
                "updated shop address"
        );

        // When & Then
        mockMvc.perform(patch("/api/shops/" + shopId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateShopRequest)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SHOP_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("가게 개설을 실패한다. (사업자번호 형식 오류)")
    void openShopInvalidBusinessNumber() throws Exception {
        // Given
        String token = testFixtures.token("test@test.com");
        OpenShopRequest request = new OpenShopRequest(
                "shop",
                "123456789",
                "shop@test.com",
                "010-1111-1111",
                "shop address"
        );

        // When & Then
        mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
