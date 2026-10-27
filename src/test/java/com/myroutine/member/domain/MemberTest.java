package com.myroutine.member.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

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

    @Test
    @DisplayName("탈퇴하면 개인정보가 마스킹된다.")
    void withdraw_masks_personal_information() {
        // Given
        Member member = Member.signUp("test@test.com", "pass1234", "test", "테스트");
        member.changeProfile("test", "테스트", "010-1234-5678");

        Instant now = Instant.parse("2026-10-18T00:00:00Z");

        // When
        member.withdraw(now);

        // Then
        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getEmail()).isEqualTo("withdrawn+" + member.getId() + "@deleted.invalid");
        assertThat(member.getNickname()).isEqualTo("탈퇴-" + member.getId().toString().substring(0, 8));
        assertThat(member.getName()).isEqualTo("탈퇴회원");
        assertThat(member.getPhone()).isNull();
        assertThat(member.getPasswordHash()).isNull();
        assertThat(member.getWithdrawnAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("탈퇴를 실패한다. (BANNED 회원, INVALID_STATE_TRANSITION)")
    void withdraw_banned_throws_invalid_state_transition() {
        // Given
        Member member = Member.signUp("test@test.com", "pass1234", "test", "테스트");
        member.changeProfile("test", "테스트", "010-1234-5678");
        ReflectionTestUtils.setField(member, "status", MemberStatus.BANNED);
        Instant now = Instant.parse("2026-10-18T00:00:00Z");

        // When & Then
        assertThatThrownBy(() -> member.withdraw(now))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION));

        assertThat(member.getEmail()).isEqualTo("test@test.com");
        assertThat(member.getNickname()).isEqualTo("test");
        assertThat(member.getName()).isEqualTo("테스트");
        assertThat(member.getPhone()).isEqualTo("010-1234-5678");
        assertThat(member.getWithdrawnAt()).isNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.BANNED);
    }

    @Test
    @DisplayName("탈퇴를 실패한다. (이미 탈퇴한 회원, INVALID_STATE_TRANSITION)")
    void withdraw_already_withdrawn_throws_invalid_state_transition() {
        // Given
        Member member = Member.signUp("test@test.com", "pass1234", "test", "테스트");
        member.changeProfile("test", "테스트", "010-1234-5678");
        Instant now = Instant.parse("2026-10-18T00:00:00Z");

        member.withdraw(now);

        // When & Then
        assertThatThrownBy(() -> member.withdraw(now.plusSeconds(60)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION));

        assertThat(member.getWithdrawnAt()).isEqualTo(now);
    }
}
