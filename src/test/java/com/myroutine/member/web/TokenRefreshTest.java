package com.myroutine.member.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.model.Ids;
import com.myroutine.common.security.AuthClaims;
import com.myroutine.common.security.JwtProvider;
import com.myroutine.member.application.TokenResult;
import com.myroutine.member.domain.RefreshToken;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.SessionSnapshot;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public TokenRefreshTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper, JwtProvider jwtProvider, StringRedisTemplate redisTemplate) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
        this.jwtProvider = jwtProvider;
        this.redisTemplate = redisTemplate;
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
        SessionSnapshot s = testFixtures.sessionSnapshotOf(sid);
        assertThat(s.memberId().toString()).isEqualTo(memberId);
        assertThat(s.refreshHash()).isEqualTo(RefreshToken.parse(refreshToken).secretHash());
        assertThat(s.ttlSeconds()).isBetween(604000L, 604800L);
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

        AuthClaims claims = jwtProvider.parse(token2.accessToken()).orElseThrow();

        assertThat(sid).isEqualTo(claims.sessionId());
        assertThat(memberId).isEqualTo(claims.memberId());
        assertThat("USER").isEqualTo(claims.role());

        assertThat(testFixtures.sessionSnapshotOf(sid).refreshHash()).isEqualTo(RefreshToken.parse(token2.refreshToken()).secretHash());
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

        SessionSnapshot s = testFixtures.sessionSnapshotOf(sid);
        assertThat(s.exists()).isFalse();
        assertThat(testFixtures.sessionIdsOf(memberId)).doesNotContain(sid);
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

        assertThat(testFixtures.sessionSnapshotOf(sidA).exists()).isFalse();
        assertThat(testFixtures.sessionSnapshotOf(sidB).exists()).isTrue();
        assertThat(testFixtures.sessionIdsOf(memberId)).doesNotContain(sidA).contains(sidB);
    }

    @Test
    @DisplayName("전체 로그아웃하면 그 회원의 모든 세션이 폐기된다.")
    void logoutAllDeletesAllSessionsOfMember() throws Exception {
        // Given
        testFixtures.signup("other@test.com");
        TokenResult tokenOther = testFixtures.loginTokens("other@test.com");
        UUID sidOther = RefreshToken.parse(tokenOther.refreshToken()).sessionId();

        UUID memberId = testFixtures.signup("member@test.com");
        TokenResult tokenA = testFixtures.loginTokens("member@test.com");
        UUID sidA = RefreshToken.parse(tokenA.refreshToken()).sessionId();
        String accessTokenA = tokenA.accessToken();

        TokenResult tokenB = testFixtures.loginTokens("member@test.com");
        UUID sidB = RefreshToken.parse(tokenB.refreshToken()).sessionId();

        // When & Then
        mockMvc.perform(post("/api/auth/logout")
                        .param("all", "true")
                        .header("Authorization", "Bearer " + accessTokenA))
                .andExpect(status().isNoContent());

        assertThat(testFixtures.sessionSnapshotOf(sidA).exists()).isFalse();
        assertThat(testFixtures.sessionSnapshotOf(sidB).exists()).isFalse();
        assertThat(testFixtures.sessionIdsOf(memberId)).isEmpty();

        assertThat(testFixtures.sessionSnapshotOf(sidOther).exists()).isTrue();
    }

    @Test
    @DisplayName("Redis에 refresh 원문을 저장하지 않는다.")
    void redisDoesNotStoreRawRefresh() throws Exception {
        // Given
        testFixtures.signup("member@test.com");
        TokenResult tokenA = testFixtures.loginTokens("member@test.com");
        UUID sidA = RefreshToken.parse(tokenA.refreshToken()).sessionId();
        String secretA = RefreshToken.parse(tokenA.refreshToken()).secret();
        String refreshTokenA = tokenA.refreshToken();

        RefreshRequest request = new RefreshRequest(refreshTokenA);

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        TokenResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
        String refreshTokenB = response.refreshToken();
        String secretB = RefreshToken.parse(refreshTokenB).secret();

        Set<String> keys = redisTemplate.keys("*");
        assertThat(keys).noneMatch(key -> key.contains(secretA) || key.contains(secretB));

        SessionSnapshot s = testFixtures.sessionSnapshotOf(sidA);

        assertThat(s.fieldNames()).containsExactlyInAnyOrder("memberId", "refreshHash");
        assertThat(s.values()).noneMatch(value -> value.contains(secretA) || value.contains(secretB));
    }

    @Test
    @DisplayName("refresh 갱신을 실패한다. (없는 세션, UNAUTHORIZED)")
    void refreshUnknownSessionFails() throws Exception {
        // Given
        testFixtures.signup("member@test.com");

        RefreshRequest request = new RefreshRequest(Ids.newId() + ".xyz");

        Set<String> keys = redisTemplate.keys("*");

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(redisTemplate.keys("*")).isEqualTo(keys);
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

        assertThat(testFixtures.sessionSnapshotOf(sidBan).exists()).isFalse();
    }

    @Test
    @DisplayName("같은 access로 로그아웃을 두 번 해도 204를 반환한다.")
    void logoutTwiceReturnsNoContent() throws Exception {
        // Given
        testFixtures.signup("member@test.com");
        TokenResult tokenA = testFixtures.loginTokens("member@test.com");
        String accessTokenA = tokenA.accessToken();

        // When & Then
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessTokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessTokenA))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("로그아웃을 실패한다. (인증 없음, UNAUTHORIZED)")
    void logoutWithoutAuthenticationFails() throws Exception {
        // Given
        testFixtures.signup("member@test.com");
        TokenResult tokenA = testFixtures.loginTokens("member@test.com");
        UUID sibA = RefreshToken.parse(tokenA.refreshToken()).sessionId();

        // When & Then
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(testFixtures.sessionSnapshotOf(sibA).exists()).isTrue();
    }

    @Test
    @DisplayName("refresh 갱신을 실패한다. (빈 요청 본문, INVALID_REQUEST)")
    void refreshBlankBodyFails() throws Exception {
        // Given
        testFixtures.signup("member@test.com");

        Set<String> keys = redisTemplate.keys("*");

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(redisTemplate.keys("*")).isEqualTo(keys);
    }

    @Test
    @DisplayName("refresh 갱신을 실패한다. (secret 불일치, REFRESH_REUSED)")
    void refreshWrongSecretFails() throws Exception {
        // Given
        testFixtures.signup("member@test.com");
        TokenResult token = testFixtures.loginTokens("member@test.com");
        UUID sid = RefreshToken.parse(token.refreshToken()).sessionId();

        RefreshRequest request = new RefreshRequest(sid + "." + "x".repeat(43));

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_REUSED"));

        assertThat(testFixtures.sessionSnapshotOf(sid).exists()).isFalse();
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

        assertThat(testFixtures.sessionSnapshotOf(sidGone).exists()).isFalse();
    }

    @Test
    @DisplayName("refresh로 갱신하면 회원의 세션 목록 만료도 연장된다.")
    void refreshExtendsMemberSessionsTtl() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("member@test.com");
        TokenResult token = testFixtures.loginTokens("member@test.com");
        String refreshToken = token.refreshToken();

        redisTemplate.expire("auth:member-sessions:" + memberId, Duration.ofSeconds(100));

        RefreshRequest request = new RefreshRequest(refreshToken);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertThat(testFixtures.memberSessionsTtlOf(memberId)).isBetween(604000L, 604800L);
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

        assertThat(testFixtures.sessionIdsOf(memberId)).hasSize(2).contains(sid);
        assertThat(testFixtures.sessionSnapshotOf(sid).memberId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("refresh로 갱신하면 세션 만료가 7일로 연장된다.")
    void refreshExtendsSessionTtl() throws Exception {
        // Given
        testFixtures.signup("member@test.com");
        TokenResult token = testFixtures.loginTokens("member@test.com");
        String refreshToken = token.refreshToken();
        UUID sid = RefreshToken.parse(refreshToken).sessionId();

        redisTemplate.expire("auth:session:" + sid, Duration.ofSeconds(100));

        RefreshRequest request = new RefreshRequest(refreshToken);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertThat(testFixtures.sessionSnapshotOf(sid).ttlSeconds()).isBetween(604000L, 604800L);
    }
}
