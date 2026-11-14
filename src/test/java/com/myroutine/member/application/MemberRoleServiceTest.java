package com.myroutine.member.application;

import com.myroutine.common.model.Ids;
import com.myroutine.shop.api.ShopApi;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.MemberSnapshot;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willAnswer;

class MemberRoleServiceTest extends IntegrationTestSupport {

    private final MemberRoleService memberRoleService;
    private final TestFixtures testFixtures;

    @MockitoSpyBean
    private ShopApi shopApi;

    @Autowired
    public MemberRoleServiceTest(MemberRoleService memberRoleService, TestFixtures testFixtures) {
        this.memberRoleService = memberRoleService;
        this.testFixtures = testFixtures;
    }

    @Test
    @DisplayName("ACTIVE 가게가 있는 USER 회원은 SELLER가 된다.")
    void syncSellerRolePromotesUserWithActiveShop() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        testFixtures.openShop(memberId, "1111111111");

        // When
        memberRoleService.syncSellerRole(memberId);

        // Then
        MemberSnapshot snapshot = testFixtures.memberSnapshotOf(memberId);
        assertThat(snapshot.role()).isEqualTo("SELLER");
    }

    @Test
    @DisplayName("ACTIVE 가게가 없는 SELLER 회원은 USER가 된다.")
    void syncSellerRoleDemotesSellerWithoutActiveShop() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        testFixtures.closeShop(shopId);
        testFixtures.changeRole(memberId, "SELLER");

        // When
        memberRoleService.syncSellerRole(memberId);

        // Then
        MemberSnapshot snapshot = testFixtures.memberSnapshotOf(memberId);
        assertThat(snapshot.role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("ACTIVE 가게가 남은 SELLER 회원은 SELLER 그대로 둔다.")
    void syncSellerRoleKeepsSellerWithActiveShop() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shop1Id = testFixtures.openShop(memberId, "1111111111");
        testFixtures.openShop(memberId, "2222222222");
        testFixtures.closeShop(shop1Id);
        testFixtures.changeRole(memberId, "SELLER");

        // When
        memberRoleService.syncSellerRole(memberId);

        // Then
        MemberSnapshot snapshot = testFixtures.memberSnapshotOf(memberId);
        assertThat(snapshot.role()).isEqualTo("SELLER");
    }

    @Test
    @DisplayName("ADMIN 회원은 ACTIVE 가게가 있어도 ADMIN 그대로 둔다.")
    void syncSellerRoleKeepsAdminWithActiveShop() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        testFixtures.openShop(memberId, "1111111111");
        testFixtures.changeRole(memberId, "ADMIN");

        // When
        memberRoleService.syncSellerRole(memberId);

        // Then
        MemberSnapshot snapshot = testFixtures.memberSnapshotOf(memberId);
        assertThat(snapshot.role()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("ADMIN 회원은 ACTIVE 가게가 없어도 ADMIN 그대로 둔다.")
    void syncSellerRoleKeepsAdminWithoutActiveShop() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shop1Id = testFixtures.openShop(memberId, "1111111111");
        testFixtures.closeShop(shop1Id);
        testFixtures.changeRole(memberId, "ADMIN");

        // When
        memberRoleService.syncSellerRole(memberId);

        // Then
        MemberSnapshot snapshot = testFixtures.memberSnapshotOf(memberId);
        assertThat(snapshot.role()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("없는 회원이면 예외 없이 끝난다.")
    void syncSellerRoleIgnoresMissingMember() {
        // Given

        // When & Then
        assertThatCode(() -> memberRoleService.syncSellerRole(Ids.newId())).doesNotThrowAnyException();

    }

    @Test
    @DisplayName("폐업 처리가 커밋하기 전에 다른 가게의 개설 처리가 와도 SELLER로 끝난다.")
    void syncSellerRoleEndsSellerWhenOpenOverlapsClose() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        testFixtures.changeRole(memberId, "SELLER");
        testFixtures.closeShop(shopId);

        AtomicBoolean pauseOnce = new AtomicBoolean(true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        willAnswer(inv -> {
            Object count = inv.callRealMethod();
            if (pauseOnce.getAndSet(false)) {
                entered.countDown();
                release.await(5, TimeUnit.SECONDS);
            }
            return count;
        }).given(shopApi).countActiveShops(memberId);

        CompletableFuture<Void> closing = CompletableFuture.runAsync(() -> memberRoleService.syncSellerRole(memberId));
        entered.await(5, TimeUnit.SECONDS);
        testFixtures.openShop(memberId, "2222222222");

        // When
        CompletableFuture<Void> opening = CompletableFuture.runAsync(() -> memberRoleService.syncSellerRole(memberId));
        try {
            opening.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException ignored) {
        }
        release.countDown();
        closing.get(10, TimeUnit.SECONDS);
        opening.get(10, TimeUnit.SECONDS);

        // Then
        assertThat(testFixtures.memberSnapshotOf(memberId).role()).isEqualTo("SELLER");
    }
}
