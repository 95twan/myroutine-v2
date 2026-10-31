package com.myroutine.member.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.support.Eventually;
import com.myroutine.support.FakeMailSender;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LoginLockTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final FakeMailSender mailSender;

    private final String K = "auth:login-fail:lock@test.com";

    @Autowired
    public LoginLockTest(MockMvc mockMvc, TestFixtures testFixtures, StringRedisTemplate redisTemplate, ObjectMapper objectMapper, FakeMailSender mailSender) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.mailSender = mailSender;
    }

    private void givenLoginFailures(int count) throws Exception {
        for (int i = 1; i <= count; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest("lock@test.com", "wrongpass1"))));
        }
    }

    @Test
    @DisplayName("로그인을 5번 실패하면 잠기고 올바른 비밀번호도 거부된다. (ACCOUNT_LOCKED)")
    void fiveFailuresLockAccount() throws Exception {
        // Given
        testFixtures.signup("lock@test.com");

        // When & Then
        for (int i = 1; i <= 5; i++) {
            if (i <= 4) {
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new LoginRequest("lock@test.com", "wrongpass1"))))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
            } else {
                MvcResult result = mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new LoginRequest("lock@test.com", "wrongpass1"))))
                        .andExpect(status().isLocked())
                        .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                        .andReturn();

                Instant unlockAt = Instant.parse(JsonPath.read(result.getResponse().getContentAsString(), "$.details.unlockAt"));
                assertThat(Duration.between(Instant.now(), unlockAt)).isBetween(Duration.ofMinutes(14), Duration.ofMinutes(15));
            }
            assertThat(testFixtures.redisStringOf(K).value()).isEqualTo(String.valueOf(i));
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("lock@test.com", "pass1234"))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("잠금 시간이 지나면 다시 로그인한다.")
    void unlocksAfterExpiry() throws Exception {
        // Given
        testFixtures.signup("lock@test.com");
        givenLoginFailures(5);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("lock@test.com", "pass1234"))))
                .andExpect(status().isLocked());

        // When & Then
        redisTemplate.expire(K, Duration.ofSeconds(1));

        Eventually.await(Duration.ofSeconds(5), () -> {
            assertThat(testFixtures.redisStringOf(K).exists()).isFalse();
        });

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("lock@test.com", "pass1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("존재하지 않는 이메일도 실패 횟수를 세어 가입한 회원과 같게 응답한다.")
    void unknownEmailCountedLikeMember() throws Exception {
        // When & Then
        for (int i = 1; i <= 5; i++) {
            if (i <= 4) {
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new LoginRequest("ghost@test.com", "wrongpass1"))))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                        .andExpect(jsonPath("$.details.remainingAttempts").value(5 - i));

            } else {
                MvcResult result = mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new LoginRequest("ghost@test.com", "wrongpass1"))))
                        .andExpect(status().isLocked())
                        .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                        .andExpect(jsonPath("$.details.unlockAt").isNotEmpty())
                        .andReturn();
            }
            assertThat(testFixtures.redisStringOf("auth:login-fail:ghost@test.com").value()).isEqualTo(String.valueOf(i));
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("ghost@test.com", "pass1234"))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

    }

    @Test
    @DisplayName("로그인에 성공하면 실패 횟수가 초기화된다.")
    void successResetsFailureCount() throws Exception {
        // Given
        testFixtures.signup("lock@test.com");
        givenLoginFailures(4);

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("lock@test.com", "pass1234"))))
                .andExpect(status().isOk());

        assertThat(testFixtures.redisStringOf(K).exists()).isFalse();

        givenLoginFailures(4);

        assertThat(testFixtures.redisStringOf(K).value()).isEqualTo("4");
    }

    @Test
    @DisplayName("잠긴 동안에는 실패 횟수를 늘리지 않고 세션을 만들지 않는다.")
    void lockedLoginKeepsCountAndCreatesNoSession() throws Exception {
        // Given
        UUID lockId = testFixtures.signup("lock@test.com");

        int before = testFixtures.sessionIdsOf(lockId).size();

        givenLoginFailures(5);

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("lock@test.com", "pass1234"))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        assertThat(testFixtures.redisStringOf(K).value()).isEqualTo("5");
        assertThat(testFixtures.sessionIdsOf(lockId).size()).isEqualTo(before);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("lock@test.com", "wrongpass1"))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        assertThat(testFixtures.redisStringOf(K).value()).isEqualTo("5");
    }

    @Test
    @DisplayName("잠금 시간은 첫 실패에 걸고 5번째 실패에서 다시 건다.")
    void lockTtlSetOnFirstAndFifthFailure() throws Exception {
        // Given
        testFixtures.signup("lock@test.com");

        // When & Then
        givenLoginFailures(1);

        assertThat(testFixtures.redisStringOf(K).ttlSeconds()).isBetween(1L, 900L);

        givenLoginFailures(3);

        redisTemplate.expire(K, Duration.ofSeconds(60));

        givenLoginFailures(1);

        assertThat(testFixtures.redisStringOf(K).ttlSeconds()).isBetween(890L, 900L);
    }

    @Test
    @DisplayName("로그인 실패 응답에 남은 횟수가 담긴다.")
    void failureResponseHasRemainingAttempts() throws Exception {
        // Given
        UUID lockId = testFixtures.signup("lock@test.com");

        // When & Then
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest("lock@test.com", "wrongpass1"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                    .andExpect(jsonPath("$.details.remainingAttempts").value(5 - i));
        }
    }

    @Test
    @DisplayName("비밀번호를 재설정하면 로그인 잠금이 풀린다.")
    void resetPasswordClearsLock() throws Exception {
        // Given
        testFixtures.signup("lock@test.com");
        givenLoginFailures(5);

        // When & Then
        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest("lock@test.com"))))
                .andExpect(status().isAccepted());

        String code = mailSender.sent().get(0).body().split("인증코드: ")[1];

        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("lock@test.com", code, "resetpass99");

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        assertThat(testFixtures.redisStringOf(K).exists()).isFalse();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("lock@test.com", "resetpass99"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }
}
