package com.myroutine.common.error;

import com.myroutine.member.web.LoginRequest;
import com.myroutine.member.web.SignupEmailRequest;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RedisUnavailableTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final HealthEndpoint healthEndpoint;
    private final TestFixtures testFixtures;

    @Autowired
    public RedisUnavailableTest(MockMvc mockMvc, ObjectMapper objectMapper, HealthEndpoint healthEndpoint, TestFixtures testFixtures) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.healthEndpoint = healthEndpoint;
        this.testFixtures = testFixtures;
    }

    @Test
    @DisplayName("Redis가 응답하지 않으면 가입 인증 코드 발송을 실패한다. (503)")
    void sendSignupCodeFailsWhenRedisPaused() throws Exception {
        // Given
        redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();
        SignupEmailRequest request = new SignupEmailRequest("test@test.com");

        // When & Then
        try {
            mockMvc.perform(post("/api/auth/signup/email-verifications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        } finally {
            redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
        }
    }

    @Test
    @DisplayName("Redis가 응답하지 않으면 타임아웃 뒤 바로 실패한다.")
    void failsWithinTimeoutWhenRedisPaused() throws Exception {
        // Given
        redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();
        SignupEmailRequest request = new SignupEmailRequest("test@test.com");

        // When & Then
        try {
            long start = System.nanoTime();
            mockMvc.perform(post("/api/auth/signup/email-verifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
        } finally {
            redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
        }
    }

    @Test
    @DisplayName("Redis가 응답하지 않아도 헬스체크는 UP이다.")
    void healthIsUpWhenRedisPaused() {
        // Given
        redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();

        // When & Then
        try {
            HealthDescriptor health = healthEndpoint.health();
            assertThat(health.getStatus()).isEqualTo(Status.UP);
        } finally {
            redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
        }
    }

    @Test
    @DisplayName("Redis가 응답하지 않으면 이메일 로그인을 실패한다. (503)")
    void emailLoginFailsWhenRedisPaused() throws Exception {
        // Given
        testFixtures.signup("test@test.com");
        redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();

        // When & Then
        try {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest("test@test.com", "pass1234"))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        } finally {
            redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
        }
    }
}
