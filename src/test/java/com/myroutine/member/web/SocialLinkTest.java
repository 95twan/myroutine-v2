package com.myroutine.member.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.security.JwtProvider;
import com.myroutine.member.domain.OAuthProvider;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.SocialAccountRow;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class SocialLinkTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;
    private final JwtProvider jwtProvider;

    @Autowired
    public SocialLinkTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper, JwtProvider jwtProvider) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
        this.jwtProvider = jwtProvider;
    }

    @Test
    @DisplayName("소셜 계정을 연결하면 그 계정으로 로그인한다.")
    void linkThenLogin() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("link@test.com");
        String token = testFixtures.login("link@test.com");

        oauth.givenUser(OAuthProvider.KAKAO, "kakao-9", "other@test.com");

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        // When & Then
        mockMvc.perform(post("/api/members/me/social-accounts/{provider}", "kakao")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(testFixtures.countOf("member.social_account")).isEqualTo(1);
        SocialAccountRow row = testFixtures.socialAccountsOf(memberId).getFirst();
        assertThat(row.provider()).isEqualTo("KAKAO");
        assertThat(row.providerUserId()).isEqualTo("kakao-9");
        assertThat(testFixtures.memberSnapshotOf(memberId).email()).isEqualTo("link@test.com");

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
    @DisplayName("소셜 계정 연결을 실패한다. (다른 회원이 이미 연결, SOCIAL_ACCOUNT_ALREADY_LINKED)")
    void linkedByOtherMemberFails() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("link@test.com");
        String token = testFixtures.login("link@test.com");

        oauth.givenUser(OAuthProvider.KAKAO, "kakao-9", "other@test.com");

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        mockMvc.perform(post("/api/members/me/social-accounts/{provider}", "kakao")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        testFixtures.signup("link2@test.com");
        String token2 = testFixtures.login("link2@test.com");

        // When & Then
        mockMvc.perform(post("/api/members/me/social-accounts/{provider}", "kakao")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOCIAL_ACCOUNT_ALREADY_LINKED"));

        assertThat(testFixtures.countOf("member.social_account")).isEqualTo(1);
        assertThat(testFixtures.socialAccountsOf(memberId)).hasSize(1);
    }

    @Test
    @DisplayName("소셜 계정 연결을 실패한다. (인증 없음, UNAUTHORIZED)")
    void linkWithoutAuthenticationFails() throws Exception {
        // Given
        testFixtures.signup("link@test.com");

        oauth.givenUser(OAuthProvider.KAKAO, "kakao-9", "other@test.com");

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        // When & Then
        mockMvc.perform(post("/api/members/me/social-accounts/{provider}", "kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(testFixtures.countOf("member.social_account")).isEqualTo(0);
    }

    @Test
    @DisplayName("소셜 계정 연결을 실패한다. (같은 제공자의 다른 계정, SOCIAL_ACCOUNT_ALREADY_LINKED)")
    void linkSameProviderTwiceFails() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("link@test.com");
        String token = testFixtures.login("link@test.com");

        oauth.givenUser(OAuthProvider.KAKAO, "kakao-9", "other@test.com");

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        mockMvc.perform(post("/api/members/me/social-accounts/{provider}", "kakao")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        oauth.givenUser(OAuthProvider.KAKAO, "kakao-10", "other@test.com");

        // When & Then
        mockMvc.perform(post("/api/members/me/social-accounts/{provider}", "kakao")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOCIAL_ACCOUNT_ALREADY_LINKED"));

        assertThat(testFixtures.socialAccountsOf(memberId)).hasSize(1);
    }

    @Test
    @DisplayName("다른 제공자의 소셜 계정은 연결한다.")
    void linkOtherProvider() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("link@test.com");
        String token = testFixtures.login("link@test.com");

        oauth.givenUser(OAuthProvider.KAKAO, "kakao-9", "other@test.com");

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        mockMvc.perform(post("/api/members/me/social-accounts/{provider}", "kakao")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        oauth.givenUser(OAuthProvider.GOOGLE, "google-9", "other@test.com");

        // When & Then
        mockMvc.perform(post("/api/members/me/social-accounts/{provider}", "google")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        List<SocialAccountRow> rows = testFixtures.socialAccountsOf(memberId);
        assertThat(rows).extracting(SocialAccountRow::provider).containsExactlyInAnyOrder("KAKAO", "GOOGLE");
    }

    @Test
    @DisplayName("내 정보에 연결한 소셜 계정이 연결한 순서로 나온다.")
    void meListsSocialAccountsInLinkOrder() throws Exception {
        // Given
        testFixtures.signup("link@test.com");
        String token = testFixtures.login("link@test.com");

        oauth.givenUser(OAuthProvider.KAKAO, "kakao-9", "other@test.com");

        OAuthLoginRequest request = new OAuthLoginRequest("any-code", "test-state");

        mockMvc.perform(post("/api/members/me/social-accounts/{provider}", "kakao")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        oauth.givenUser(OAuthProvider.GOOGLE, "google-9", "other@test.com");

        mockMvc.perform(post("/api/members/me/social-accounts/{provider}", "google")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        // When & Then
        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.socialAccounts[0].provider").value("KAKAO"))
                .andExpect(jsonPath("$.socialAccounts[1].provider").value("GOOGLE"));
    }

    @Test
    @DisplayName("연결한 소셜 계정이 없으면 내 정보의 목록이 비어 있다.")
    void meListsNoSocialAccounts() throws Exception {
        // Given
        testFixtures.signup("link@test.com");
        String token = testFixtures.login("link@test.com");

        // When & Then
        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socialAccounts").isEmpty());
    }
}
