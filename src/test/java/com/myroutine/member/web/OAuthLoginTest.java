package com.myroutine.member.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.security.JwtProvider;
import com.myroutine.member.domain.OAuthProvider;
import com.myroutine.member.domain.RefreshToken;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMockMvc
class OAuthLoginTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public OAuthLoginTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper, JwtProvider jwtProvider, StringRedisTemplate redisTemplate) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
        this.jwtProvider = jwtProvider;
        this.redisTemplate = redisTemplate;
    }

    @Test
    @DisplayName("신규 소셜 사용자가 로그인부터 가입까지 마친다.")
    void newSocialUserSignsUp() throws Exception {
        // Given
        oauth.givenUser(OAuthProvider.KAKAO, "123456789", "social@test.com");

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.email").value("social@test.com"))
                .andExpect(jsonPath("$.signupToken").exists())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn();

        String signupToken = JsonPath.read(result.getResponse().getContentAsString(), "$.signupToken");

        RedisHash redisHash = testFixtures.redisHashOf("auth:oauth-signup:" + TestFixtures.sha256Hex(signupToken));
        assertThat(redisHash.exists()).isTrue();
        assertThat(redisHash.ttlSeconds()).isBetween(1L, 600L);
        assertThat(testFixtures.countOf("member.member")).isEqualTo(0);
        assertThat(testFixtures.countOf("member.social_account")).isEqualTo(0);

        String verificationToken = testFixtures.verificationToken("social@test.com");

        SocialSignupRequest socialSignupRequest = new SocialSignupRequest(signupToken, null, "socialnick", "소셜", verificationToken);

        MvcResult result2 = mockMvc.perform(post("/api/auth/oauth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(socialSignupRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn();

        String accessToken = JsonPath.read(result2.getResponse().getContentAsString(), "$.accessToken");
        String refreshToken = JsonPath.read(result2.getResponse().getContentAsString(), "$.refreshToken");

        assertThat(jwtProvider.parse(accessToken).get().role()).isEqualTo("USER");

        UUID memberId = testFixtures.memberIdOf("social@test.com");
        assertThat(jwtProvider.parse(accessToken).get().memberId()).isEqualTo(memberId);

        assertThat(testFixtures.countOf("member.member")).isEqualTo(1);
        MemberSnapshot m = testFixtures.memberSnapshotOf(memberId);
        assertThat(m.passwordHash()).isNull();
        assertThat(m.nickname()).isEqualTo("socialnick");

        assertThat(testFixtures.countOf("member.social_account")).isEqualTo(1);
        SocialAccountRow s = testFixtures.socialAccountsOf(memberId).getFirst();
        assertThat(s.provider()).isEqualTo("KAKAO");
        assertThat(s.providerUserId()).isEqualTo("123456789");

        redisHash = testFixtures.redisHashOf("auth:oauth-signup:" + TestFixtures.sha256Hex(signupToken));
        assertThat(redisHash.exists()).isFalse();

        RedisString redisString = testFixtures.redisStringOf("auth:signup-verified:" + TestFixtures.sha256Hex(verificationToken));
        assertThat(redisString.exists()).isFalse();

        SessionSnapshot ss = testFixtures.sessionSnapshotOf(RefreshToken.parse(refreshToken).sessionId());
        assertThat(ss.memberId()).isEqualTo(memberId);

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("같은 소셜 계정으로 다시 로그인한다.")
    void sameSocialAccountLogsInAgain() throws Exception {
        // Given
        UUID memberId = socialSignup(OAuthProvider.KAKAO, "123456789", "social@test.com");

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGGED_IN"))
                .andReturn();

        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.token.accessToken");
        String refreshToken = JsonPath.read(result.getResponse().getContentAsString(), "$.token.refreshToken");

        assertThat(jwtProvider.parse(accessToken).get().memberId()).isEqualTo(memberId);
        UUID sid = RefreshToken.parse(refreshToken).sessionId();
        assertThat(testFixtures.sessionSnapshotOf(sid).exists()).isTrue();
        assertThat(testFixtures.sessionSnapshotOf(sid).memberId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("소셜 가입을 실패한다. (같은 이메일의 이메일 가입 계정 존재, MEMBER_EMAIL_DUPLICATED)")
    void signupWithExistingEmailFails() throws Exception {
        // Given
        UUID existingId = testFixtures.signup("dup@test.com");

        oauth.givenUser(OAuthProvider.KAKAO, "kakao-dup", "dup@test.com");

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        String before = testFixtures.memberSnapshotOf(existingId).passwordHash();

        // When & Then
        MvcResult result1 = mockMvc.perform(post("/api/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNUP_REQUIRED"))
                .andReturn();

        String signupToken = JsonPath.read(result1.getResponse().getContentAsString(), "$.signupToken");

        String verificationToken = testFixtures.verificationToken("dup@test.com");

        SocialSignupRequest socialSignupRequest = new SocialSignupRequest(signupToken, null, "socialnick", "소셜", verificationToken);

        mockMvc.perform(post("/api/auth/oauth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(socialSignupRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_EMAIL_DUPLICATED"));

        assertThat(testFixtures.countOf("member.member")).isEqualTo(1);
        assertThat(testFixtures.memberSnapshotOf(existingId).passwordHash()).isEqualTo(before);
        assertThat(testFixtures.socialAccountsOf(existingId)).hasSize(0);

    }

    @ParameterizedTest
    @MethodSource("providerFailureFailsProvider")
    @DisplayName("소셜 로그인을 실패한다. (제공자 장애, OAUTH_PROVIDER_ERROR)")
    void providerFailureFails(Behavior behavior) throws Exception {
        // Given
        oauth.givenUser(OAuthProvider.KAKAO, "kakao-1", "a@test.com");
        oauth.failWith(OAuthProvider.KAKAO, behavior);

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        // When & Then
        mockMvc.perform(post("/api/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("OAUTH_PROVIDER_ERROR"));

        assertThat(testFixtures.countOf("member.member")).isEqualTo(0);
        assertThat(testFixtures.countOf("member.social_account")).isEqualTo(0);
        assertThat(redisTemplate.keys("auth:oauth-signup:*")).isEmpty();
    }

    private Stream<Behavior> providerFailureFailsProvider() {
        return Stream.of(
                Behavior.delay(6000),
                Behavior.serverError()
        );
    }

    @ParameterizedTest
    @MethodSource("parsesProviderResponsesProvider")
    @DisplayName("세 제공자의 응답을 파싱해 가입 토큰에 맡긴다.")
    void parsesProviderResponses(OAuthProvider provider, String id) throws Exception {
        // Given
        oauth.givenUser(provider, id, provider.name().toLowerCase() + "@test.com");

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/auth/oauth/{provider}/login", provider.name().toLowerCase())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.email").value(provider.name().toLowerCase() + "@test.com"))
                .andReturn();

        String signupToken = JsonPath.read(result.getResponse().getContentAsString(), "$.signupToken");

        RedisHash redisHash = testFixtures.redisHashOf("auth:oauth-signup:" + TestFixtures.sha256Hex(signupToken));
        assertThat(redisHash.fields().get("provider")).isEqualTo(provider.name());
        assertThat(redisHash.fields().get("providerUserId")).isEqualTo(id);
        assertThat(redisHash.fields().get("email")).isEqualTo(provider.name().toLowerCase() + "@test.com");
    }

    public Stream<Arguments> parsesProviderResponsesProvider() {
        return Stream.of(
                Arguments.of(OAuthProvider.KAKAO, "123456789"),
                Arguments.of(OAuthProvider.GOOGLE, "google-sub-1"),
                Arguments.of(OAuthProvider.NAVER, "naver-id-1")
        );
    }

    @Test
    @DisplayName("이메일을 주지 않는 제공자는 직접 입력한 이메일로 가입한다.")
    void signupWithTypedEmailWhenProviderGivesNone() throws Exception {
        // Given
        oauth.givenUser(OAuthProvider.KAKAO, "kakao-noemail", null);

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andReturn();

        String signupToken = JsonPath.read(result.getResponse().getContentAsString(), "$.signupToken");

        RedisHash redisHash = testFixtures.redisHashOf("auth:oauth-signup:" + TestFixtures.sha256Hex(signupToken));
        assertThat(redisHash.fields().keySet()).containsExactlyInAnyOrder("provider", "providerUserId");

        String verificationToken = testFixtures.verificationToken("typed@test.com");

        SocialSignupRequest socialSignupRequest = new SocialSignupRequest(signupToken, "typed@test.com", "socialnick", "소셜", verificationToken);

        mockMvc.perform(post("/api/auth/oauth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(socialSignupRequest)))
                .andExpect(status().isCreated());

        assertThat(testFixtures.memberIdOf("typed@test.com")).isNotNull();
    }

    @Test
    @DisplayName("소셜 가입을 실패한다. (다른 이메일의 인증 토큰, VERIFICATION_TOKEN_INVALID)")
    void signupWithOtherEmailVerificationFails() throws Exception {
        // Given
        oauth.givenUser(OAuthProvider.KAKAO, "123456789", "social@test.com");

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        MvcResult result = mockMvc.perform(post("/api/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String signupToken = JsonPath.read(result.getResponse().getContentAsString(), "$.signupToken");
        String verificationToken = testFixtures.verificationToken("other@test.com");

        SocialSignupRequest socialSignupRequest = new SocialSignupRequest(signupToken, "social@test.com", "socialnick", "소셜", verificationToken);

        // When & Then
        mockMvc.perform(post("/api/auth/oauth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(socialSignupRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_INVALID"));

        assertThat(testFixtures.countOf("member.member")).isEqualTo(0);
        assertThat(testFixtures.countOf("member.social_account")).isEqualTo(0);
        assertThat(testFixtures.redisHashOf("auth:oauth-signup:" + TestFixtures.sha256Hex(signupToken)).exists()).isTrue();
    }

    @Test
    @DisplayName("제공자 이메일이 바뀌어도 같은 회원으로 로그인한다.")
    void changedProviderEmailLogsInSameMember() throws Exception {
        // Given
        UUID memberId = socialSignup(OAuthProvider.KAKAO, "123456789", "social@test.com");

        oauth.givenUser(OAuthProvider.KAKAO, "123456789", "changed@test.com");

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGGED_IN"))
                .andReturn();

        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.token.accessToken");
        assertThat(jwtProvider.parse(accessToken).get().memberId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("소셜 로그인을 실패한다. (정지 회원, MEMBER_BANNED)")
    void bannedMemberLoginFails() throws Exception {
        // Given
        UUID memberId = socialSignup(OAuthProvider.KAKAO, "123456789", "social@test.com");

        oauth.givenUser(OAuthProvider.KAKAO, "123456789", "social@test.com");

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        testFixtures.changeStatus(memberId, "BANNED");

        int before = testFixtures.sessionIdsOf(memberId).size();

        // When & Then
        mockMvc.perform(post("/api/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_BANNED"));

        assertThat(testFixtures.sessionIdsOf(memberId).size()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"code\":\"x\"}", "{\"code\":\"x\",\"state\":\"\"}"})
    @DisplayName("소셜 로그인을 실패한다. (state 없음 또는 빈 문자열, INVALID_REQUEST)")
    void blankStateFails(String request) throws Exception {
        // Given

        // When & Then
        mockMvc.perform(post("/api/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(testFixtures.countOf("member.member")).isEqualTo(0);
        assertThat(testFixtures.countOf("member.social_account")).isEqualTo(0);
    }

    @Test
    @DisplayName("소셜 로그인을 실패한다. (알 수 없는 제공자, INVALID_REQUEST)")
    void unknownProviderFails() throws Exception {
        // Given
        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        // When & Then
        mockMvc.perform(post("/api/auth/oauth/facebook/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(testFixtures.countOf("member.member")).isEqualTo(0);
        assertThat(testFixtures.countOf("member.social_account")).isEqualTo(0);
    }

    @Test
    @DisplayName("소셜 가입을 실패한다. (이메일이 어디에도 없음, INVALID_REQUEST)")
    void signupWithoutAnyEmailFails() throws Exception {
        // Given
        oauth.givenUser(OAuthProvider.KAKAO, "kakao-noemail", null);

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        MvcResult result = mockMvc.perform(post("/api/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String signupToken = JsonPath.read(result.getResponse().getContentAsString(), "$.signupToken");
        String verificationToken = testFixtures.verificationToken("typed@test.com");

        SocialSignupRequest socialSignupRequest = new SocialSignupRequest(signupToken, null, "socialnick", "소셜", verificationToken);

        // When & Then
        mockMvc.perform(post("/api/auth/oauth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(socialSignupRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(testFixtures.countOf("member.member")).isEqualTo(0);
    }

    @Test
    @DisplayName("소셜 가입을 실패한다. (가입 토큰 없음, SIGNUP_TOKEN_INVALID)")
    void signupWithoutSignupTokenFails() throws Exception {
        // Given
        oauth.givenUser(OAuthProvider.KAKAO, "123456789", "social@test.com");

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        MvcResult result = mockMvc.perform(post("/api/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String signupToken = JsonPath.read(result.getResponse().getContentAsString(), "$.signupToken");
        String verificationToken = testFixtures.verificationToken("typed@test.com");

        redisTemplate.delete("auth:oauth-signup:" + TestFixtures.sha256Hex(signupToken));

        SocialSignupRequest socialSignupRequest = new SocialSignupRequest(signupToken, null, "socialnick", "소셜", verificationToken);

        // When & Then
        mockMvc.perform(post("/api/auth/oauth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(socialSignupRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGNUP_TOKEN_INVALID"));

        assertThat(testFixtures.countOf("member.member")).isEqualTo(0);
    }

    @Test
    @DisplayName("소셜 가입 회원의 내 정보에는 비밀번호가 없다고 나온다.")
    void socialMemberHasNoPassword() throws Exception {
        // Given
        socialSignup(OAuthProvider.KAKAO, "123456789", "social@test.com");

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        MvcResult result = mockMvc.perform(post("/api/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.token.accessToken");

        // When & Then
        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(false))
                .andExpect(jsonPath("$.socialAccounts", hasSize(1)))
                .andExpect(jsonPath("$.socialAccounts[0].provider").value("KAKAO"))
                .andExpect(jsonPath("$.socialAccounts[0].linkedAt").exists());
    }

    @Test
    @DisplayName("탈퇴한 회원의 소셜 계정으로 다시 가입한다.")
    void withdrawnMemberSocialAccountSignsUpAgain() throws Exception {
        socialSignup(OAuthProvider.KAKAO, "123456789", "social@test.com");

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        MvcResult result = mockMvc.perform(post("/api/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.token.accessToken");

        // When & Then
        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        assertThat(testFixtures.countOf("member.social_account")).isEqualTo(0);

        mockMvc.perform(post("/api/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNUP_REQUIRED"));
    }

    private UUID socialSignup(OAuthProvider provider, String providerUserId, String email) throws Exception {
        oauth.givenUser(provider, providerUserId, email);

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        MvcResult result1 = mockMvc.perform(post("/api/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String signupToken = JsonPath.read(result1.getResponse().getContentAsString(), "$.signupToken");

        String verificationToken = testFixtures.verificationToken(email);

        SocialSignupRequest socialSignupRequest = new SocialSignupRequest(signupToken, null, "socialnick", "소셜", verificationToken);

        mockMvc.perform(post("/api/auth/oauth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(socialSignupRequest)))
                .andExpect(status().isCreated());

        return testFixtures.memberIdOf(email);
    }
}
