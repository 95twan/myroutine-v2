package com.myroutine.member.web;

import com.myroutine.member.application.TokenResult;
import com.myroutine.member.domain.RefreshToken;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.SessionRow;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SessionAuthTest extends IntegrationTestSupport {
    private final TestFixtures testFixtures;
    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public SessionAuthTest(MockMvc mockMvc, TestFixtures testFixtures, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("로그인하면 DB에 refresh 해시로 세션을 만든다.")
    void loginCreatesSessionWithRefreshHash() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");

        // When & Then
        TokenResult token = testFixtures.loginTokens("test@test.com");

        SessionRow session = testFixtures.sessionRowsOf(memberId).getLast();
        assertThat(session.id()).isEqualTo(RefreshToken.parse(token.refreshToken()).sessionId());
        assertThat(session.refreshHash()).isEqualTo(RefreshToken.parse(token.refreshToken()).secretHash());
    }

    @Test
    @DisplayName("로그아웃하면 그 기기의 access가 바로 거부된다. (401)")
    void logoutRejectsAccessOfThatDevice() throws Exception {
        // Given
        testFixtures.signup("test@test.com");
        TokenResult token = testFixtures.loginTokens("test@test.com");

        // When & Then
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("세션이 만료되면 access가 유효해도 인증을 실패한다. (401)")
    void authenticationFailsWhenSessionExpired() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        TokenResult token = testFixtures.loginTokens("test@test.com");
        jdbcTemplate.update("UPDATE member.auth_session SET expires_at = NOW() - interval '1 second' WHERE member_id = ?", memberId);

        // When & Then
        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("회원이 ACTIVE가 아니면 세션이 남아 있어도 인증을 실패한다. (401)")
    void authenticationFailsWhenMemberIsNotActive() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        TokenResult token = testFixtures.loginTokens("test@test.com");
        testFixtures.changeStatus(memberId, "BANNED");

        // When & Then
        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("역할을 바꾸면 같은 access의 다음 요청부터 반영한다.")
    void appliesRoleChangeFromNextRequest() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        TokenResult token = testFixtures.loginTokens("test@test.com");
        testFixtures.changeRole(memberId, "ADMIN");

        // When & Then
        mockMvc.perform(get("/admin/api/members")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("refresh 재사용으로 세션을 지우면 그 세션의 access도 거부된다. (401)")
    void reusedRefreshRejectsAccessOfThatSession() throws Exception {
        // Given
        testFixtures.signup("test@test.com");
        TokenResult token = testFixtures.loginTokens("test@test.com");

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(token.refreshToken()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(token.refreshToken()))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + token.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Redis가 응답하지 않아도 access로 인증한다.")
    void authenticatesWhenRedisPaused() throws Exception {
        // Given
        testFixtures.signup("test@test.com");
        TokenResult token = testFixtures.loginTokens("test@test.com");

        redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();

        // When & Then
        try {
            mockMvc.perform(get("/api/members/me")
                            .header("Authorization", "Bearer " + token.accessToken()))
                    .andExpect(status().isOk());
        } finally {
            redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
        }
    }

    @Test
    @DisplayName("Redis가 응답하지 않아도 refresh로 갱신한다.")
    void refreshesWhenRedisPaused() throws Exception {
        // Given
        testFixtures.signup("test@test.com");
        TokenResult token = testFixtures.loginTokens("test@test.com");

        redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();

        // When & Then
        try {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest(token.refreshToken()))))
                    .andExpect(status().isOk());
        } finally {
            redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
        }
    }

    @Test
    @DisplayName("Redis가 응답하지 않아도 로그아웃한다.")
    void logsOutWhenRedisPaused() throws Exception {
        // Given
        testFixtures.signup("test@test.com");
        TokenResult token = testFixtures.loginTokens("test@test.com");

        redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();

        // When & Then
        try {
            mockMvc.perform(post("/api/auth/logout")
                            .header("Authorization", "Bearer " + token.accessToken()))
                    .andExpect(status().isNoContent());
        } finally {
            redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
        }
    }
}
