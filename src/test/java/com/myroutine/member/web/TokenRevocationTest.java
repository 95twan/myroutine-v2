package com.myroutine.member.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.security.JwtProvider;
import com.myroutine.member.application.TokenResult;
import com.myroutine.support.FakeMailSender;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import com.myroutine.support.TokenVersionSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TokenRevocationTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;
    private final FakeMailSender mailSender;

    @Autowired
    public TokenRevocationTest(MockMvc mockMvc, TestFixtures testFixtures, JwtProvider jwtProvider, ObjectMapper objectMapper, FakeMailSender mailSender) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.jwtProvider = jwtProvider;
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

        TokenVersionSnapshot v = testFixtures.tokenVersionSnapshotOf(targetId);
        assertThat(v.db()).isEqualTo(1);
        assertThat(v.redis()).isEqualTo(1);
        assertThat(v.redisTtlSeconds()).isBetween(80000L, 86400L);

        assertThat(testFixtures.sessionIdsOf(targetId)).isEmpty();

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("전체 로그아웃하면 기존 access 토큰이 모두 거부된다.")
    void logoutAllRejectsExistingAccessTokens() throws Exception {
        // Given
        UUID targetId = testFixtures.signup("target@test.com");
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

        TokenVersionSnapshot v = testFixtures.tokenVersionSnapshotOf(targetId);
        assertThat(v.db()).isEqualTo(1);
        assertThat(v.redis()).isEqualTo(1);

        String newToken = testFixtures.login("target@test.com");
        assertThat(jwtProvider.parse(newToken).get().tokenVersion()).isEqualTo(1);

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비밀번호를 변경하면 기존 access 토큰이 거부되고 새 토큰은 통과한다.")
    void changePasswordRejectsExistingAccessTokens() throws Exception {
        // Given
        UUID targetId = testFixtures.signup("target@test.com");
        TokenResult tokenA = testFixtures.loginTokens("target@test.com");
        String accessTokenA = tokenA.accessToken();

        TokenResult tokenB = testFixtures.loginTokens("target@test.com");
        String accessTokenB = tokenB.accessToken();

        ChangePasswordRequest request = new ChangePasswordRequest("pass1234", "newpass5678");

        // When & Then
        MvcResult result = mockMvc.perform(patch("/api/members/me/password")
                        .header("Authorization", "Bearer " + accessTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String newAccess = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + accessTokenA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + accessTokenB))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk());

        assertThat(jwtProvider.parse(newAccess).get().tokenVersion()).isEqualTo(1);
        assertThat(testFixtures.tokenVersionSnapshotOf(targetId).db()).isEqualTo(1);
        assertThat(testFixtures.tokenVersionSnapshotOf(targetId).redis()).isEqualTo(1);
    }

    @Test
    @DisplayName("회원 탈퇴하면 기존 access 토큰이 거부된다.")
    void withdrawRejectsExistingAccessToken() throws Exception {
        // Given
        UUID leaveId = testFixtures.signup("leave@test.com");
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

        assertThat(testFixtures.tokenVersionSnapshotOf(leaveId).db()).isEqualTo(1);
    }

    @Test
    @DisplayName("전체 로그아웃이 아닌 로그아웃은 토큰 버전을 올리지 않는다.")
    void logoutWithoutAllKeepsTokenVersion() throws Exception {
        // Given
        UUID otherId = testFixtures.signup("other@test.com");
        String accessToken = testFixtures.login("other@test.com");


        // When & Then
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("all", "false"))
                .andExpect(status().isNoContent());

        assertThat(testFixtures.tokenVersionSnapshotOf(otherId).db()).isEqualTo(0);
    }

    @Test
    @DisplayName("비밀번호를 재설정하면 기존 access 토큰이 거부된다.")
    void resetPasswordRejectsExistingAccessToken() throws Exception {
        // Given
        UUID targetId = testFixtures.signup("target@test.com");
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

        assertThat(testFixtures.tokenVersionSnapshotOf(targetId).db()).isEqualTo(2);
        assertThat(testFixtures.tokenVersionSnapshotOf(targetId).redis()).isEqualTo(2);

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isUnauthorized());
    }

}
