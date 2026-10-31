package com.myroutine.member.web;

import com.myroutine.common.security.JwtProvider;
import com.myroutine.member.application.TokenResult;
import com.myroutine.member.domain.EmailCodePurpose;
import com.myroutine.member.domain.RefreshToken;
import com.myroutine.support.FakeMailSender;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.MemberSnapshot;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class PasswordTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;
    private final JwtProvider jwtProvider;
    private final FakeMailSender mailSender;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public PasswordTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper, JwtProvider jwtProvider, FakeMailSender mailSender, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
        this.jwtProvider = jwtProvider;
        this.mailSender = mailSender;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("비밀번호를 변경하면 새 비밀번호로만 로그인한다.")
    void changePasswordThenLoginWithNewPassword() throws Exception {
        // Given
        testFixtures.signup("pw@test.com");
        String token = testFixtures.login("pw@test.com");

        ChangePasswordRequest request = new ChangePasswordRequest("pass1234", "newpass5678");

        // When & Then
        mockMvc.perform(patch("/api/members/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.refreshExpiresIn").value(0));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("pw@test.com", "pass1234"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));


        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("pw@test.com", "newpass5678"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    @DisplayName("비밀번호를 변경하면 현재 세션만 남고 다른 기기의 세션은 폐기된다.")
    void changePasswordDeletesOtherSessions() throws Exception {
        // Given
        UUID member1Id = testFixtures.signup("member1@test.com");
        TokenResult token1 = testFixtures.loginTokens("member1@test.com");
        String access1 = token1.accessToken();
        String refresh1 = token1.refreshToken();
        UUID sid1 = RefreshToken.parse(refresh1).sessionId();

        TokenResult token2 = testFixtures.loginTokens("member1@test.com");
        String refresh2 = token2.refreshToken();
        UUID sid2 = RefreshToken.parse(refresh2).sessionId();

        ChangePasswordRequest request = new ChangePasswordRequest("pass1234", "newpass5678");

        // When & Then
        MvcResult result = mockMvc.perform(patch("/api/members/me/password")
                        .header("Authorization", "Bearer " + access1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        TokenResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
        String newAccess = response.accessToken();

        assertThat(jwtProvider.parse(newAccess).get().sessionId()).isEqualTo(sid1);

        mockMvc.perform(get("/api/members/me")
                        .header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member1Id.toString()));

        assertThat(testFixtures.sessionSnapshotOf(sid1).exists()).isTrue();
        assertThat(testFixtures.sessionSnapshotOf(sid2).exists()).isFalse();
        assertThat(testFixtures.sessionIdsOf(member1Id)).containsExactly(sid1);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refresh2))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refresh1))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비밀번호 변경을 실패한다. (현재 비밀번호 불일치, PASSWORD_MISMATCH)")
    void changeWithWrongCurrentPasswordFails() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("pw@test.com");
        TokenResult token = testFixtures.loginTokens("pw@test.com");
        String accessA = token.accessToken();
        UUID sidA = RefreshToken.parse(token.refreshToken()).sessionId();

        ChangePasswordRequest request = new ChangePasswordRequest("wrongpass1", "newpass5678");

        String before = testFixtures.memberSnapshotOf(memberId).passwordHash();

        // When & Then
        mockMvc.perform(patch("/api/members/me/password")
                        .header("Authorization", "Bearer " + accessA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

        MemberSnapshot s = testFixtures.memberSnapshotOf(memberId);
        assertThat(s.passwordHash()).isEqualTo(before);
        assertThat(testFixtures.sessionSnapshotOf(sidA).exists()).isTrue();
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 가입 여부와 상관없이 같은 응답을 반환한다.")
    void resetRequestSameResponseRegardlessOfSignup() throws Exception {
        // Given
        testFixtures.signup("pw@test.com");
        PasswordResetRequest request = new PasswordResetRequest("nobody@test.com");

        // When & Then
        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        assertThat(mailSender.sent()).isEmpty();

        request = new PasswordResetRequest("pw@test.com");
        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        assertThat(mailSender.sent()).hasSize(1);
        assertThat(mailSender.sent().getFirst().to()).isEqualTo("pw@test.com");
        assertThat(testFixtures.emailAuthSnapshotOf(EmailCodePurpose.RESET_PASSWORD, "pw@test.com").codeExists()).isTrue();
    }

    @Test
    @DisplayName("비밀번호를 재설정하면 모든 세션이 폐기되고 새 비밀번호로만 로그인한다.")
    void confirmResetDeletesAllSessions() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("pw@test.com");
        TokenResult tokenA = testFixtures.loginTokens("pw@test.com");
        String refreshA = tokenA.refreshToken();
        UUID sidA = RefreshToken.parse(refreshA).sessionId();

        TokenResult tokenB = testFixtures.loginTokens("pw@test.com");
        String refreshB = tokenB.refreshToken();
        UUID sidB = RefreshToken.parse(refreshB).sessionId();

        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest("pw@test.com"))))
                .andExpect(status().isAccepted());

        String code = mailSender.sent().get(0).body().split("인증코드: ")[1];

        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("pw@test.com", code, "resetpass99");

        // When & Then
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        assertThat(testFixtures.sessionSnapshotOf(sidA).exists()).isFalse();
        assertThat(testFixtures.sessionSnapshotOf(sidB).exists()).isFalse();
        assertThat(testFixtures.sessionIdsOf(memberId)).isEmpty();
        assertThat(testFixtures.emailAuthSnapshotOf(EmailCodePurpose.RESET_PASSWORD, "pw@test.com").codeExists()).isFalse();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("pw@test.com", "pass1234"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("pw@test.com", "resetpass99"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비밀번호 변경을 실패한다. (비밀번호 없는 회원, PASSWORD_MISMATCH)")
    void changeWithoutPasswordHashFails() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("pw@test.com");
        TokenResult token = testFixtures.loginTokens("pw@test.com");
        String accessA = token.accessToken();

        jdbcTemplate.update("UPDATE member.member SET password_hash = null WHERE id = ?", memberId);

        ChangePasswordRequest request = new ChangePasswordRequest("pass1234", "newpass5678");

        // When & Then
        mockMvc.perform(patch("/api/members/me/password")
                        .header("Authorization", "Bearer " + accessA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
    }

    @Test
    @DisplayName("ACTIVE가 아닌 회원에게는 재설정 메일을 보내지 않는다.")
    void resetRequestForInactiveMemberSendsNoMail() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("pw@test.com");

        testFixtures.changeStatus(memberId, "BANNED");

        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest("pw@test.com"))))
                .andExpect(status().isAccepted());

        assertThat(mailSender.sent()).isEmpty();
    }

    @Test
    @DisplayName("비밀번호 재설정을 실패한다. (틀린 코드, EMAIL_CODE_INVALID)")
    void confirmResetWithWrongCodeFails() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("pw@test.com");
        TokenResult tokenA = testFixtures.loginTokens("pw@test.com");
        String refreshA = tokenA.refreshToken();
        UUID sidA = RefreshToken.parse(refreshA).sessionId();

        TokenResult tokenB = testFixtures.loginTokens("pw@test.com");
        String refreshB = tokenB.refreshToken();
        UUID sidB = RefreshToken.parse(refreshB).sessionId();

        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest("pw@test.com"))))
                .andExpect(status().isAccepted());

        String code = mailSender.sent().get(0).body().split("인증코드: ")[1];
        String wrong = code.equals("000000") ? "111111" : "000000";

        String before = testFixtures.memberSnapshotOf(memberId).passwordHash();

        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("pw@test.com", wrong, "resetpass99");

        // When & Then
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"));

        assertThat(testFixtures.memberSnapshotOf(memberId).passwordHash()).isEqualTo(before);
        assertThat(testFixtures.sessionSnapshotOf(sidA).exists()).isTrue();
        assertThat(testFixtures.sessionSnapshotOf(sidB).exists()).isTrue();
    }

    @Test
    @DisplayName("재설정 요청의 발송 한도는 가입 여부와 상관없이 적용된다.")
    void resetRequestRateLimitRegardlessOfSignup() throws Exception {
        // Given
        testFixtures.signup("pw@test.com");

        // When & Then
        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest("nobody@test.com"))))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest("nobody@test.com"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.details.retryAfterSeconds").value(allOf(greaterThanOrEqualTo(1), lessThanOrEqualTo(60))));

        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest("pw@test.com"))))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest("pw@test.com"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.details.retryAfterSeconds").value(allOf(greaterThanOrEqualTo(1), lessThanOrEqualTo(60))));
    }

    @Test
    @DisplayName("비밀번호 재설정을 실패한다. (가입 규칙 위반 새 비밀번호, INVALID_REQUEST)")
    void confirmResetWithInvalidNewPasswordFails() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("pw@test.com");

        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest("pw@test.com"))))
                .andExpect(status().isAccepted());

        String code = mailSender.sent().get(0).body().split("인증코드: ")[1];

        String before = testFixtures.memberSnapshotOf(memberId).passwordHash();

        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest("pw@test.com", code, "abcdefgh");

        // When & Then
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(testFixtures.memberSnapshotOf(memberId).passwordHash()).isEqualTo(before);
        assertThat(testFixtures.emailAuthSnapshotOf(EmailCodePurpose.RESET_PASSWORD, "pw@test.com").codeExists()).isTrue();
    }

    @Test
    @DisplayName("재설정 메일 발송이 실패해도 같은 응답을 반환한다.")
    void resetRequestSameResponseWhenMailFails() throws Exception {
        // Given
        testFixtures.signup("pw@test.com");

        mailSender.failNext(1);

        // When & Then
        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest("pw@test.com"))))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        assertThat(mailSender.sent()).hasSize(1);
    }
}
