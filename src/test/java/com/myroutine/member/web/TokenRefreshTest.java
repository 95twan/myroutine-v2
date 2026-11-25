package com.myroutine.member.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.model.Ids;
import com.myroutine.common.security.AccessTokenClaims;
import com.myroutine.common.security.JwtProvider;
import com.myroutine.member.application.TokenResult;
import com.myroutine.member.domain.RefreshToken;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.SessionRow;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TokenRefreshTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;
    private final JwtProvider jwtProvider;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public TokenRefreshTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper, JwtProvider jwtProvider, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
        this.jwtProvider = jwtProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("가입 응답에 refresh 토큰이 들어 있고 세션이 만들어진다.")
    void signupIssueRefreshToken() throws Exception {
        // Given
        SignupRequest request = new SignupRequest("new@test.com", "pass1234", "newbie", "테스터", testFixtures.verificationToken("new@test.com"));

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                ).andExpect(status().isCreated())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshExpiresIn").value(604800))
                .andReturn();

        String memberId = JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        String refreshToken = JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");
        UUID sid = RefreshToken.parse(refreshToken).sessionId();
        assertThat(sid).isEqualTo(jwtProvider.parse(accessToken).get().sessionId());
        SessionRow session = testFixtures.sessionRowsOf(UUID.fromString(memberId)).getLast();
        assertThat(session.refreshHash()).isEqualTo(RefreshToken.parse(refreshToken).secretHash());
        assertThat(session.expiresAt()).isCloseTo(Instant.now().plus(Duration.ofDays(7)), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("로그인할 때마다 새 세션이 만들어진다.")
    void loginCreatesNewSession() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("member@test.com");

        LoginRequest request = new LoginRequest("member@test.com", "pass1234");

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn();

        TokenResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
        String refreshToken = response.refreshToken();
        UUID sid = RefreshToken.parse(refreshToken).sessionId();

        List<SessionRow> sessions = testFixtures.sessionRowsOf(memberId);

        assertThat(sessions).hasSize(2).anyMatch(session -> session.id().equals(sid));
    }

    @Test
    @DisplayName("refresh로 갱신하면 새 access와 새 refresh를 받는다.")
    void refreshRotatesToken() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("member@test.com");
        TokenResult token = testFixtures.loginTokens("member@test.com");
        UUID sid = RefreshToken.parse(token.refreshToken()).sessionId();

        RefreshRequest request = new RefreshRequest(token.refreshToken());

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        TokenResponse token2 = objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
        assertThat(token2.refreshToken()).isNotEqualTo(token.refreshToken());

        UUID sid2 = RefreshToken.parse(token2.refreshToken()).sessionId();
        assertThat(sid).isEqualTo(sid2);

        AccessTokenClaims claims = jwtProvider.parse(token2.accessToken()).orElseThrow();

        assertThat(sid).isEqualTo(claims.sessionId());
        assertThat(memberId).isEqualTo(claims.memberId());

        assertThat(testFixtures.sessionRowsOf(memberId).getLast().refreshHash()).isEqualTo(RefreshToken.parse(token2.refreshToken()).secretHash());
    }

    @Test
    @DisplayName("refresh로 갱신하면 세션 만료가 7일로 연장된다.")
    void refreshExtendsSessionTtl() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("member@test.com");
        TokenResult token = testFixtures.loginTokens("member@test.com");
        String refreshToken = token.refreshToken();
        UUID sid = RefreshToken.parse(refreshToken).sessionId();

        jdbcTemplate.update(
                "UPDATE member.auth_session SET expires_at = now() + interval '100 seconds' WHERE id = ?",
                sid);

        RefreshRequest request = new RefreshRequest(refreshToken);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        SessionRow session = testFixtures.sessionRowsOf(memberId).getLast();
        assertThat(session.expiresAt()).isCloseTo(Instant.now().plus(Duration.ofDays(7)), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("refresh 갱신을 실패한다. (빈 요청 본문, INVALID_REQUEST)")
    void refreshBlankBodyFails() throws Exception {
        // Given
        testFixtures.signup("member@test.com");

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("refresh 갱신을 실패한다. (없는 세션, UNAUTHORIZED)")
    void refreshUnknownSessionFails() throws Exception {
        // Given
        testFixtures.signup("member@test.com");

        RefreshRequest request = new RefreshRequest(Ids.newId() + ".xyz");

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("이미 쓴 refresh로 갱신하는 것을 실패한다. (재사용 탐지, 세션 폐기)")
    void refreshReusedTokenFails() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("member@test.com");
        TokenResult token = testFixtures.loginTokens("member@test.com");
        UUID sid = RefreshToken.parse(token.refreshToken()).sessionId();
        String refreshToken = token.refreshToken();

        RefreshRequest request = new RefreshRequest(refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_REUSED"));

        List<SessionRow> sessions = testFixtures.sessionRowsOf(memberId);
        assertThat(sessions).noneMatch(session -> session.id().equals(sid));
    }

    @Test
    @DisplayName("refresh 갱신을 실패한다. (secret 불일치, REFRESH_REUSED)")
    void refreshWrongSecretFails() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("member@test.com");
        TokenResult token = testFixtures.loginTokens("member@test.com");
        UUID sid = RefreshToken.parse(token.refreshToken()).sessionId();

        RefreshRequest request = new RefreshRequest(sid + "." + "x".repeat(43));

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_REUSED"));

        List<SessionRow> sessions = testFixtures.sessionRowsOf(memberId);
        assertThat(sessions).noneMatch(session -> session.id().equals(sid));
    }

    @Test
    @DisplayName("refresh 갱신을 실패한다. (정지 회원, MEMBER_BANNED)")
    void refreshBannedMemberFails() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("ban@test.com");
        TokenResult token = testFixtures.loginTokens("ban@test.com");
        String refreshToken = token.refreshToken();
        UUID sidBan = RefreshToken.parse(refreshToken).sessionId();

        testFixtures.changeStatus(memberId, "BANNED");

        RefreshRequest request = new RefreshRequest(refreshToken);


        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_BANNED"));

        List<SessionRow> sessions = testFixtures.sessionRowsOf(memberId);
        assertThat(sessions).noneMatch(session -> session.id().equals(sidBan));
    }

    @Test
    @DisplayName("refresh 갱신을 실패한다. (탈퇴 회원, LOGIN_FAILED)")
    void refreshWithdrawnMemberFails() throws Exception {
        // Given
        UUID goneId = testFixtures.signup("gone@test.com");
        TokenResult token = testFixtures.loginTokens("gone@test.com");
        String refreshGone = token.refreshToken();
        UUID sidGone = RefreshToken.parse(refreshGone).sessionId();

        testFixtures.changeStatus(goneId, "WITHDRAWN");

        RefreshRequest request = new RefreshRequest(refreshGone);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));

        List<SessionRow> sessions = testFixtures.sessionRowsOf(goneId);
        assertThat(sessions).noneMatch(session -> session.id().equals(sidGone));
    }

    @Test
    @DisplayName("로그아웃하면 현재 기기의 세션만 폐기된다.")
    void logoutDeletesCurrentSessionOnly() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("member@test.com");
        TokenResult tokenA = testFixtures.loginTokens("member@test.com");
        UUID sidA = RefreshToken.parse(tokenA.refreshToken()).sessionId();
        String accessTokenA = tokenA.accessToken();

        TokenResult tokenB = testFixtures.loginTokens("member@test.com");
        UUID sidB = RefreshToken.parse(tokenB.refreshToken()).sessionId();

        // When & Then
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessTokenA))
                .andExpect(status().isNoContent());

        List<SessionRow> sessions = testFixtures.sessionRowsOf(memberId);
        assertThat(sessions).noneMatch(session -> session.id().equals(sidA));
        assertThat(sessions).anyMatch(session -> session.id().equals(sidB));
    }

    @Test
    @DisplayName("전체 로그아웃하면 그 회원의 모든 세션이 폐기된다.")
    void logoutAllDeletesAllSessionsOfMember() throws Exception {
        // Given
        UUID otherId = testFixtures.signup("other@test.com");
        TokenResult tokenOther = testFixtures.loginTokens("other@test.com");
        UUID sidOther = RefreshToken.parse(tokenOther.refreshToken()).sessionId();

        UUID memberId = testFixtures.signup("member@test.com");
        TokenResult tokenA = testFixtures.loginTokens("member@test.com");
        String accessTokenA = tokenA.accessToken();

        testFixtures.loginTokens("member@test.com");

        // When & Then
        mockMvc.perform(post("/api/auth/logout")
                        .param("all", "true")
                        .header("Authorization", "Bearer " + accessTokenA))
                .andExpect(status().isNoContent());

        List<SessionRow> sessions = testFixtures.sessionRowsOf(memberId);
        assertThat(sessions).isEmpty();

        List<SessionRow> otherSessions = testFixtures.sessionRowsOf(otherId);
        assertThat(otherSessions).anyMatch(session -> session.id().equals(sidOther));
    }

    @Test
    @DisplayName("로그아웃을 실패한다. (인증 없음, UNAUTHORIZED)")
    void logoutWithoutAuthenticationFails() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("member@test.com");
        TokenResult tokenA = testFixtures.loginTokens("member@test.com");
        UUID sibA = RefreshToken.parse(tokenA.refreshToken()).sessionId();

        // When & Then
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        List<SessionRow> sessions = testFixtures.sessionRowsOf(memberId);
        assertThat(sessions).anyMatch(session -> session.id().equals(sibA));
    }
}
