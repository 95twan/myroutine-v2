package com.myroutine.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
}
