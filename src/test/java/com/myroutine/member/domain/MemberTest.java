package com.myroutine.member.domain;

import com.myroutine.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class MemberTest {

    @Test
    @DisplayName("가입하면 이메일은 소문자로 정리되고 기본 역할 USER, 상태 ACTIVE로 만들어진다.")
    void sign_up_creates_active_user() {
        // Given
        String email = " Buyer@Test.COM ";

        // When
        Member result = Member.signUp(email, "1q2w3e4r", "nick", "name");

        // Then
        assertThat(result.getEmail()).isEqualTo("buyer@test.com");
        assertThat(result.getRole()).isEqualTo(MemberRole.USER);
        assertThat(result.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(result.getId()).isNotNull();
    }

    @Test
    @DisplayName("ACTIVE 회원은 로그인 검증을 통과한다.")
    void verify_can_login_active_does_not_throw() {
        // Given
        Member activeMember = Member.signUp("test@test.com", "pass1234", "test", "테스트");

        // When & Then
        assertThatCode(activeMember::verifyCanLogin).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("로그인 검증을 실패한다. (BANNED 회원, MEMBER_BANNED)")
    void verify_can_login_banned_throws_member_banned() {
        // Given
        Member bannedMember = Member.signUp("test@test.com", "pass1234", "test", "테스트");
        ReflectionTestUtils.setField(bannedMember, "status", MemberStatus.BANNED);

        // When & Then
        assertThatThrownBy(bannedMember::verifyCanLogin)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_BANNED));
    }

    @Test
    @DisplayName("로그인 검증을 실패한다. (WITHDRAWN 회원, LOGIN_FAILED)")
    void verify_can_login_withdrawn_throws_login_failed() {
        // Given
        Member withdrawnMember = Member.signUp("test@test.com", "pass1234", "test", "테스트");
        ReflectionTestUtils.setField(withdrawnMember, "status", MemberStatus.WITHDRAWN);

        // When & Then
        assertThatThrownBy(withdrawnMember::verifyCanLogin)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.LOGIN_FAILED));
    }
}
