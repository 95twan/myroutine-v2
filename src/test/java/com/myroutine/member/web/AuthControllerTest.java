package com.myroutine.member.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.MemberRepository;
import com.myroutine.support.IntegrationTestSupport;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthControllerTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public AuthControllerTest(MockMvc mockMvc, MemberRepository memberRepository, PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }


    @Test
    @DisplayName("회원 가입을 한다.")
    void signUp() throws Exception {
        // Given
        String password = "password123";
        SignupRequest request = new SignupRequest("test@test.com", password, "nick", "name");

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String memberId = JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");

        Member member = memberRepository.findById(UUID.fromString(memberId)).orElseThrow();
        assertThat(member.getPasswordHash()).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, member.getPasswordHash())).isTrue();
        assertThat(member.getCreatedAt()).isNotNull();
        assertThat(member.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("회원 가입을 실패한다. (이메일 중복)")
    void signUpWithDuplicateEmail() throws Exception {
        // Given
        SignupRequest request1 = new SignupRequest("test@test.com", "1111aaaa", "nick1", "name1");
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        SignupRequest request2 = new SignupRequest("TEST@test.com", "2222bbbb", "nick2", "name2");

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_EMAIL_DUPLICATED"));
    }

    @Test
    @DisplayName("회원 가입을 실패한다. (닉네임 중복)")
    void signUpWithDuplicateNickname() throws Exception {
        // Given
        String nickname = "nick";
        SignupRequest request1 = new SignupRequest("test1@test.com", "1111aaaa", nickname, "name1");
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        SignupRequest request2 = new SignupRequest("test2@test.com", "2222bbbb", nickname, "name2");

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_NICKNAME_DUPLICATED"));
    }

    @RepeatedTest(10)
    @DisplayName("회원가입 동시성 테스트")
    void signUpConcurrently() throws Exception {
        // Given
        String email = "test@test.com";
        SignupRequest[] requests = {
                new SignupRequest(email, "1111aaaa", "nick1", "name1"),
                new SignupRequest(email, "2222bbbb", "nick2", "name2")
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);   // 둘 다 준비될 때까지
        CountDownLatch start = new CountDownLatch(1);   // 동시에 출발시키는 신호

        List<Future<Integer>> futures = new ArrayList<>();
        for (SignupRequest request : requests) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andReturn().getResponse().getStatus();
            }));
        }

        ready.await();
        start.countDown();

        // When
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) statuses.add(f.get(10, TimeUnit.SECONDS));
        pool.shutdown();

        // Then
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM member.member WHERE email = ?", Integer.class, email);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("회원가입 입력값이 유효하지 않을 경우")
    void signUpWithInvalidInput() throws Exception {
        // Given
        SignupRequest request = new SignupRequest("test1testcom", "111aaa", "nick", "name");

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details.password").exists())
                .andExpect(jsonPath("$.details.email").exists());
    }

    @Test
    @DisplayName("입력값이 깨진 JSON일 경우")
    void signUpWithMalformedJson() throws Exception {
        // Given
        String brokenJson = "{\"email\":";

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brokenJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("에러 응답의 헤더 X-Request-Id와 본문 traceId가 같다.")
    void errorResponseTraceId() throws Exception {
        // Given
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andReturn();

        // When & Then
        String headerTraceId = result.getResponse().getHeader("X-Request-Id");
        String bodyTraceId = JsonPath.read(result.getResponse().getContentAsString(), "$.traceId");

        assertThat(headerTraceId).isNotBlank();
        assertThat(bodyTraceId).isEqualTo(headerTraceId);
    }

    @Test
    void unexpectedError() throws Exception {
        // Given

        // When & Then
        MvcResult result = mockMvc.perform(get("/api/auth/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("SELECT", "IllegalStateException");
    }
}
