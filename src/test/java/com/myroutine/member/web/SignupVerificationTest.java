package com.myroutine.member.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.member.domain.EmailCodePurpose;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SignupVerificationTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;
    private final FakeMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public SignupVerificationTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper, FakeMailSender mailSender, StringRedisTemplate redisTemplate) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
        this.mailSender = mailSender;
        this.redisTemplate = redisTemplate;
    }

    @Test
    @DisplayName("가입 인증코드를 발송한다.")
    void sendSignupVerificationCode() throws Exception {
        // Given
        String email = "new@test.com";

        SignupEmailRequest request = new SignupEmailRequest(email);

        // When & Then
        mockMvc.perform(post("/api/auth/signup/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        List<SentMail> sent = mailSender.sent();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).to()).isEqualTo(email);
        assertThat(sent.get(0).title()).isEqualTo("[MyRoutine] 이메일 인증코드");
        assertThat(sent.get(0).body()).matches("인증코드: \\d{6}");

        EmailAuthSnapshot s = testFixtures.emailAuthSnapshotOf(EmailCodePurpose.VERIFY_EMAIL, email);
        assertThat(s.codeTtlSeconds()).isBetween(1L, 300L);
        assertThat(s.cooldownTtlSeconds()).isBetween(1L, 60L);
        assertThat(s.hourly()).isEqualTo(1);
        assertThat(s.hourlyTtlSeconds()).isBetween(1L, 3600L);
    }

    @Test
    @DisplayName("인증코드를 확인하면 인증 토큰을 받는다.")
    void confirmCodeIssuesVerificationToken() throws Exception {
        // Given
        String email = "new@test.com";

        mockMvc.perform(post("/api/auth/signup/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupEmailRequest(email))))
                .andExpect(status().isAccepted());

        List<SentMail> sent = mailSender.sent();
        String code = sent.get(0).body().split("인증코드: ")[1];

        SignupEmailConfirmRequest request = new SignupEmailConfirmRequest(email, code);

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/auth/signup/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationToken").exists())
                .andReturn();

        String verificationToken = JsonPath.read(result.getResponse().getContentAsString(), "$.verificationToken");

        RedisString redisString = testFixtures.redisStringOf("auth:signup-verified:" + TestFixtures.sha256Hex(verificationToken));
        assertThat(redisString.value()).isEqualTo("new@test.com");
        assertThat(redisString.ttlSeconds()).isBetween(1L, 1800L);

        assertThat(testFixtures.emailAuthSnapshotOf(EmailCodePurpose.VERIFY_EMAIL, "new@test.com").codeExists()).isFalse();
    }

    @Test
    @DisplayName("인증 토큰으로 가입한다.")
    void signupWithVerificationToken() throws Exception {
        // Given
        String email = "new@test.com";

        mockMvc.perform(post("/api/auth/signup/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupEmailRequest(email))))
                .andExpect(status().isAccepted());

        List<SentMail> sent = mailSender.sent();
        String code = sent.get(0).body().split("인증코드: ")[1];

        MvcResult result = mockMvc.perform(post("/api/auth/signup/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupEmailConfirmRequest(email, code))))
                .andExpect(status().isOk())
                .andReturn();

        String verificationToken = JsonPath.read(result.getResponse().getContentAsString(), "$.verificationToken");

        SignupRequest request = new SignupRequest(email, "pass1234", "newbie", "뉴비", verificationToken);

        // When & Then
        MvcResult result2 = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        SignupResponse response = objectMapper.readValue(result2.getResponse().getContentAsString(), SignupResponse.class);

        MemberSnapshot s = testFixtures.memberSnapshotOf(response.memberId());
        assertThat(s.email()).isEqualTo(email);

        assertThat(testFixtures.redisStringOf("auth:signup-verified:" + TestFixtures.sha256Hex(verificationToken)).exists()).isFalse();
    }

    @Test
    @DisplayName("인증코드 발송을 실패한다. (60초 안에 재발송, RATE_LIMITED)")
    void resendWithinCooldownFails() throws Exception {
        // Given
        String email = "new@test.com";

        SignupEmailRequest request = new SignupEmailRequest(email);

        mockMvc.perform(post("/api/auth/signup/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        EmailAuthSnapshot before = testFixtures.emailAuthSnapshotOf(EmailCodePurpose.VERIFY_EMAIL, email);

        // When & Then
        mockMvc.perform(post("/api/auth/signup/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.details.retryAfterSeconds").value(allOf(greaterThanOrEqualTo(1), lessThanOrEqualTo(60))));

        assertThat(mailSender.sent()).hasSize(1);

        EmailAuthSnapshot s = testFixtures.emailAuthSnapshotOf(EmailCodePurpose.VERIFY_EMAIL, email);
        assertThat(s.codeHash()).isEqualTo(before.codeHash());
        assertThat(s.hourly()).isEqualTo(1);
    }

    @Test
    @DisplayName("인증코드 발송을 실패한다. (시간당 5번 초과, RATE_LIMITED)")
    void sendOverHourlyLimitFails() throws Exception {
        // Given
        String email = "new@test.com";

        SignupEmailRequest request = new SignupEmailRequest(email);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/signup/email-verifications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());

            redisTemplate.delete("auth:email-cooldown:" + email);
        }

        EmailAuthSnapshot s = testFixtures.emailAuthSnapshotOf(EmailCodePurpose.VERIFY_EMAIL, email);
        assertThat(s.hourly()).isEqualTo(5);

        // When & Then
        mockMvc.perform(post("/api/auth/signup/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.details.retryAfterSeconds").value(allOf(greaterThanOrEqualTo(3500), lessThanOrEqualTo(3600))));

        assertThat(mailSender.sent()).hasSize(5);

        s = testFixtures.emailAuthSnapshotOf(EmailCodePurpose.VERIFY_EMAIL, email);
        assertThat(s.hourly()).isEqualTo(6);
    }

    @Test
    @DisplayName("인증코드 확인을 실패한다. (5번 틀린 뒤 맞는 코드, EMAIL_CODE_INVALID)")
    void confirmAfterFiveWrongAttemptsFails() throws Exception {
        // Given
        String email = "new@test.com";

        SignupEmailRequest request = new SignupEmailRequest(email);

        mockMvc.perform(post("/api/auth/signup/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn();

        List<SentMail> sent = mailSender.sent();
        String code = sent.get(0).body().split("인증코드: ")[1];
        String wrong = code.equals("000000") ? "111111" : "000000";

        // When & Then
        for (int i = 0; i < 5; i++) {
            MvcResult result = mockMvc.perform(post("/api/auth/signup/email-verifications/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new SignupEmailConfirmRequest(email, wrong))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"))
                    .andReturn();

            EmailAuthSnapshot s = testFixtures.emailAuthSnapshotOf(EmailCodePurpose.VERIFY_EMAIL, email);

            if (i == 4) {
                assertThat(s.codeExists()).isFalse();
            } else if (i >= 2) {
                assertThat(s.attempts()).isEqualTo(i + 1);
            }
        }

        mockMvc.perform(post("/api/auth/signup/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupEmailConfirmRequest(email, code))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"));

        assertThat(redisTemplate.keys("auth:signup-verified:*")).isEmpty();
    }

    @Test
    @DisplayName("인증코드 확인을 실패한다. (만료된 코드, EMAIL_CODE_INVALID)")
    void confirmExpiredCodeFails() throws Exception {
        // Given
        String email = "new@test.com";

        SignupEmailRequest request = new SignupEmailRequest(email);

        mockMvc.perform(post("/api/auth/signup/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn();

        List<SentMail> sent = mailSender.sent();
        String code = sent.get(0).body().split("인증코드: ")[1];

        redisTemplate.delete("auth:email-code:" + EmailCodePurpose.VERIFY_EMAIL + ":" + email);

        // When & Then
        mockMvc.perform(post("/api/auth/signup/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupEmailConfirmRequest(email, code))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"));

        assertThat(redisTemplate.keys("auth:signup-verified:*")).isEmpty();
    }

    @Test
    @DisplayName("Redis에 인증코드 원문을 저장하지 않는다.")
    void redisDoesNotStoreRawCode() throws Exception {
        // Given
        String email = "new@test.com";

        SignupEmailRequest request = new SignupEmailRequest(email);

        // When & Then
        mockMvc.perform(post("/api/auth/signup/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        List<SentMail> sent = mailSender.sent();
        String code = sent.get(0).body().split("인증코드: ")[1];

        EmailAuthSnapshot s = testFixtures.emailAuthSnapshotOf(EmailCodePurpose.VERIFY_EMAIL, email);
        assertThat(s.fieldNames()).containsExactlyInAnyOrder("codeHash", "attempts");
        assertThat(s.codeHash()).isNotEqualTo(code);
        assertThat(redisTemplate.keys("auth:*")).allMatch(key -> !key.contains(code));
    }

    @Test
    @DisplayName("인증코드 발송을 실패한다. (이미 가입된 이메일, MEMBER_EMAIL_DUPLICATED)")
    void sendToRegisteredEmailFails() throws Exception {
        // Given
        String email = "done@test.com";
        testFixtures.signup(email);

        SignupEmailRequest request = new SignupEmailRequest(email);

        // When & Then
        mockMvc.perform(post("/api/auth/signup/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_EMAIL_DUPLICATED"));

        List<SentMail> sent = mailSender.sent();
        assertThat(sent).isEmpty();

        assertThat(redisTemplate.keys("auth:email-*")).isEmpty();
    }

    @Test
    @DisplayName("가입을 실패한다. (발급하지 않은 인증 토큰, VERIFICATION_TOKEN_INVALID)")
    void signupWithUnknownTokenFails() throws Exception {
        // Given
        String email = "new@test.com";

        String verificationToken = "never-issued";

        SignupRequest request = new SignupRequest(email, "pass1234", "newbie", "뉴비", verificationToken);

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_INVALID"));

        assertThat(testFixtures.countOf("member.member")).isEqualTo(0);
    }

    @Test
    @DisplayName("가입을 실패한다. (다른 이메일의 인증 토큰, VERIFICATION_TOKEN_INVALID)")
    void signupWithOtherEmailTokenFails() throws Exception {
        // Given
        String email = "new@test.com";

        String other = testFixtures.verificationToken("other@test.com");

        SignupRequest request = new SignupRequest(email, "pass1234", "newbie", "뉴비", other);

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_INVALID"));

        assertThat(testFixtures.countOf("member.member")).isEqualTo(0);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("가입을 실패한다. (인증 토큰 없음, INVALID_REQUEST)")
    void signupWithoutTokenFails(String verificationToken) throws Exception {
        // Given
        String email = "new@test.com";

        SignupRequest request = new SignupRequest(email, "pass1234", "newbie", "뉴비", verificationToken);

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(testFixtures.countOf("member.member")).isEqualTo(0);
    }

    @Test
    @DisplayName("가입이 실패해도 같은 인증 토큰으로 다시 가입한다.")
    void tokenRemainsAfterFailedSignup() throws Exception {
        // Given
        String email = "taken@test.com";
        testFixtures.signup(email);

        String verificationToken = testFixtures.verificationToken("new@test.com");

        SignupRequest request = new SignupRequest("new@test.com", "pass1234", "taken", "뉴비", verificationToken);

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_NICKNAME_DUPLICATED"));

        request = new SignupRequest("new@test.com", "pass1234", "newbie", "뉴비", verificationToken);
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("가입을 실패한다. (이미 가입에 쓴 인증 토큰, VERIFICATION_TOKEN_INVALID)")
    void signupWithUsedTokenFails() throws Exception {
        // Given
        String email = "new@test.com";

        mockMvc.perform(post("/api/auth/signup/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupEmailRequest(email))))
                .andExpect(status().isAccepted());

        List<SentMail> sent = mailSender.sent();
        String code = sent.get(0).body().split("인증코드: ")[1];

        MvcResult result = mockMvc.perform(post("/api/auth/signup/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupEmailConfirmRequest(email, code))))
                .andExpect(status().isOk())
                .andReturn();

        String verificationToken = JsonPath.read(result.getResponse().getContentAsString(), "$.verificationToken");

        SignupRequest request = new SignupRequest(email, "pass1234", "newbie", "뉴비", verificationToken);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        request = new SignupRequest(email, "pass1234", "newbie2", "뉴비", verificationToken);

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_INVALID"));

        assertThat(testFixtures.countOf("member.member")).isEqualTo(1);
    }

    @Test
    @DisplayName("Redis에 인증 토큰 원문을 저장하지 않는다.")
    void redisDoesNotStoreRawVerificationToken() throws Exception {
        // Given
        String email = "new@test.com";

        mockMvc.perform(post("/api/auth/signup/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupEmailRequest(email))))
                .andExpect(status().isAccepted());

        List<SentMail> sent = mailSender.sent();
        String code = sent.get(0).body().split("인증코드: ")[1];

        SignupEmailConfirmRequest request = new SignupEmailConfirmRequest(email, code);

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/auth/signup/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String verificationToken = JsonPath.read(result.getResponse().getContentAsString(), "$.verificationToken");

        Set<String> keys = redisTemplate.keys("auth:*");
        assertThat(keys).allMatch(key -> !key.contains(verificationToken));
        assertThat(keys)
                .filteredOn(key -> redisTemplate.type(key) == DataType.STRING)
                .allMatch(key -> !verificationToken.equals(redisTemplate.opsForValue().get(key)));
    }
}
