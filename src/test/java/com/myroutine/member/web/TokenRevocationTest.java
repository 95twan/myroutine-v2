package com.myroutine.member.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.member.application.TokenResult;
import com.myroutine.support.FakeMailSender;
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
class TokenRevocationTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;
    private final FakeMailSender mailSender;

    @Autowired
    public TokenRevocationTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper, FakeMailSender mailSender) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
        this.mailSender = mailSender;
    }

    @Test
    @DisplayName("회원을 제재하면 기존 access 토큰이 거부된다.")
    void banRejectsExistingAccessToken() throws Exception {
        // Given
        String adminToken = testFixtures.adminToken("admin@test.com");

        UUID targetId = testFixtures.signup("target@test.com");
        TokenResult token = testFixtures.loginTokens("target@test.com");

        String accessToken = token.accessToken();

        BanRequest request = new BanRequest("스팸");

        // When & Then
        mockMvc.perform(post("/admin/api/members/{targetId}/ban", targetId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("전체 로그아웃하면 기존 access 토큰이 모두 거부된다.")
    void logoutAllRejectsExistingAccessTokens() throws Exception {
        // Given
        testFixtures.signup("target@test.com");
        TokenResult tokenA = testFixtures.loginTokens("target@test.com");
        String accessTokenA = tokenA.accessToken();

        TokenResult tokenB = testFixtures.loginTokens("target@test.com");
        String accessTokenB = tokenB.accessToken();

        // When & Then
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessTokenA)
                        .param("all", "true"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + accessTokenA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + accessTokenB))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        String newToken = testFixtures.login("target@test.com");

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비밀번호를 변경하면 다른 기기의 access가 거부된다. (401)")
    void changePasswordRejectsAccessOfOtherDevice() throws Exception {
        // Given
        testFixtures.signup("target@test.com");
        TokenResult tokenA = testFixtures.loginTokens("target@test.com");
        String accessTokenA = tokenA.accessToken();

        TokenResult tokenB = testFixtures.loginTokens("target@test.com");
        String accessTokenB = tokenB.accessToken();

        // When & Then
        mockMvc.perform(patch("/api/members/me/password")
                        .header("Authorization", "Bearer " + accessTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("pass1234", "newpass5678"))))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + accessTokenB))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비밀번호를 변경한 기기는 응답의 새 access로 인증한다.")
    void changedDeviceAuthenticatesWithNewAccess() throws Exception {
        // Given
        testFixtures.signup("target@test.com");
        TokenResult tokenA = testFixtures.loginTokens("target@test.com");
        String accessTokenA = tokenA.accessToken();

        // When & Then
        MvcResult result = mockMvc.perform(patch("/api/members/me/password")
                        .header("Authorization", "Bearer " + accessTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("pass1234", "newpass5678"))))
                .andExpect(status().isOk())
                .andReturn();

        String newAccess = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("회원 탈퇴하면 기존 access 토큰이 거부된다.")
    void withdrawRejectsExistingAccessToken() throws Exception {
        // Given
        testFixtures.signup("leave@test.com");
        TokenResult tokenA = testFixtures.loginTokens("leave@test.com");
        String accessTokenA = tokenA.accessToken();

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + accessTokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + accessTokenA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("비밀번호를 재설정하면 기존 access 토큰이 거부된다.")
    void resetPasswordRejectsExistingAccessToken() throws Exception {
        // Given
        testFixtures.signup("target@test.com");
        TokenResult tokenA = testFixtures.loginTokens("target@test.com");
        String accessTokenA = tokenA.accessToken();

        testFixtures.loginTokens("target@test.com");

        MvcResult result = mockMvc.perform(patch("/api/members/me/password")
                        .header("Authorization", "Bearer " + accessTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("pass1234", "newpass5678"))))
                .andExpect(status().isOk())
                .andReturn();

        String newAccess = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");

        // When & Then
        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest("target@test.com"))))
                .andExpect(status().isAccepted());

        String code = mailSender.sent().get(0).body().split("인증코드: ")[1];

        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("target@test.com", code, "resetpass99");

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isUnauthorized());
    }

}
