package com.myroutine.member.web;

import com.myroutine.support.ConcurrencyRunner;
import com.myroutine.support.FakeMailSender;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
class LoginLockConcurrencyTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final FakeMailSender mailSender;

    private final String K = "auth:login-fail:lock@test.com";

    @Autowired
    public LoginLockConcurrencyTest(MockMvc mockMvc, TestFixtures testFixtures, StringRedisTemplate redisTemplate, ObjectMapper objectMapper, FakeMailSender mailSender) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.mailSender = mailSender;
    }


    @RepeatedTest(10)
    @DisplayName("틀린 비밀번호를 동시에 보내도 비밀번호 확인은 5번까지만 한다.")
    void concurrentWrongPasswordsCheckedAtMostFiveTimes() throws Exception {
        // Given
        testFixtures.signup("lock@test.com");

        Callable<MvcResult> task = () -> mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("lock@test.com", "wrongpass1"))))
                .andReturn();

        // When & Then
        List<ConcurrencyRunner.Result<MvcResult>> results = ConcurrencyRunner.run(20, task);

        assertThat(results).allMatch(ConcurrencyRunner.Result::ok);
        List<Integer> statuses = results.stream().map(r -> r.value().getResponse().getStatus()).toList();
        assertThat(statuses).filteredOn(s -> s == 401).hasSize(4);
        assertThat(statuses).filteredOn(s -> s == 423).hasSize(16);

        assertThat(testFixtures.redisStringOf("auth:login-fail:lock@test.com").value()).isEqualTo("5");
    }
}
