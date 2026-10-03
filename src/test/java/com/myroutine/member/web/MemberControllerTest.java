package com.myroutine.member.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.security.JwtProperties;
import com.myroutine.common.security.JwtProvider;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MemberControllerTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final JwtProperties jwtProperties;

    @Autowired
    public MemberControllerTest(MockMvc mockMvc, TestFixtures testFixtures, JwtProperties jwtProperties) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.jwtProperties = jwtProperties;
    }

    @Test
    @DisplayName("내 정보를 조회한다.")
    void getMe() throws Exception {
        // Given
        String email = "test@test.com";
        String accessToken = testFixtures.token(email);

        // When & Then
        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.nickname").value("test"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

    }

    @Test
    @DisplayName("내 정보 조회를 실패한다. (토큰 없음)")
    void getMeWithoutToken() throws Exception {
        // Given

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.details").exists())
                .andReturn();

        String xRequestId = result.getResponse().getHeader("X-Request-ID");
        String traceId = JsonPath.read(result.getResponse().getContentAsString(), "$.traceId");

        assertThat(xRequestId).isEqualTo(traceId);
    }

    @Test
    @DisplayName("내 정보 조회를 실패한다. (형식이 잘못된 토큰)")
    void getMeWithMalformedToken() throws Exception {
        // Given

        // When & Then
        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("내 정보 조회를 실패한다. (만료된 토큰)")
    void getMeWithExpiredToken() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        JwtProvider jwtProvider = new JwtProvider(jwtProperties, Clock.fixed(Instant.now().minusSeconds(7200), ZoneOffset.UTC));
        String expiredToken = jwtProvider.issue(memberId, "USER");

        // When & Then
        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
