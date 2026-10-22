package com.myroutine.member.application;

import com.myroutine.common.model.Ids;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.MemberSnapshot;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class MemberRoleServiceTest extends IntegrationTestSupport {

    private final MemberRoleService memberRoleService;
    private final TestFixtures testFixtures;

    @Autowired
    public MemberRoleServiceTest(MemberRoleService memberRoleService, TestFixtures testFixtures) {
        this.memberRoleService = memberRoleService;
        this.testFixtures = testFixtures;
    }

    @Test
    @DisplayName("USER 회원에게 SELLER 역할을 부여한다.")
    void grantSellerPromotesUser() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");

        // When
        memberRoleService.grantSeller(memberId);

        // Then
        MemberSnapshot snapshot = testFixtures.memberSnapshotOf(memberId);
        assertThat(snapshot.role()).isEqualTo("SELLER");
    }

    @Test
    @DisplayName("SELLER 회원은 SELLER 그대로 둔다.")
    void grantSellerKeepsSeller() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        testFixtures.changeRole(memberId, "SELLER");

        // When
        memberRoleService.grantSeller(memberId);

        // Then
        MemberSnapshot snapshot = testFixtures.memberSnapshotOf(memberId);
        assertThat(snapshot.role()).isEqualTo("SELLER");
    }

    @Test
    @DisplayName("ADMIN 회원은 ADMIN 그대로 둔다.")
    void grantSellerKeepsAdmin() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        testFixtures.changeRole(memberId, "ADMIN");

        // When
        memberRoleService.grantSeller(memberId);

        // Then
        MemberSnapshot snapshot = testFixtures.memberSnapshotOf(memberId);
        assertThat(snapshot.role()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("없는 회원이면 예외 없이 끝난다.")
    void grantSellerIgnoresMissingMember() {
        // Given

        // When & Then
        assertThatCode(() -> memberRoleService.grantSeller(Ids.newId())).doesNotThrowAnyException();

    }
}
