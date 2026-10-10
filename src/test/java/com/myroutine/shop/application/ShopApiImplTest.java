package com.myroutine.shop.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.shop.api.ShopApi;
import com.myroutine.shop.api.ShopInfo;
import com.myroutine.shop.domain.ShopErrorCode;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ShopApiImplTest extends IntegrationTestSupport {

    private final ShopApi shopApi;
    @Autowired
    private TestFixtures testFixtures;

    @Autowired
    public ShopApiImplTest(ShopApi shopApi) {
        this.shopApi = shopApi;
    }

    @Test
    @DisplayName("운영 중인 내 가게는 소유자 확인을 통과한다.")
    void verifyOwnerOfActiveShopSuccess() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");

        // When & Then
        assertThatCode(() -> shopApi.verifyOwnerOfActiveShop(shopId, memberId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("운영 중인 가게 소유자 확인을 실패한다. (없는 가게)")
    void verifyOwnerOfActiveShopNotFound() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");

        // When & Then
        assertThatThrownBy(() -> shopApi.verifyOwnerOfActiveShop(UUID.randomUUID(), memberId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ShopErrorCode.SHOP_NOT_FOUND));
    }

    @Test
    @DisplayName("운영 중인 가게 소유자 확인을 실패한다. (다른 회원의 가게)")
    void verifyOwnerOfActiveShopOfOtherMember() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID otherMemberId = testFixtures.signup("other@test.com");
        UUID shopId = testFixtures.openShop(otherMemberId, "1111111111");

        // When & Then
        assertThatThrownBy(() -> shopApi.verifyOwnerOfActiveShop(shopId, memberId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("운영 중인 가게 소유자 확인을 실패한다. (폐업한 가게)")
    void verifyOwnerOfActiveShopClosed() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        testFixtures.closeShop(shopId);

        // When & Then
        assertThatThrownBy(() -> shopApi.verifyOwnerOfActiveShop(shopId, memberId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ShopErrorCode.SHOP_NOT_ACTIVE));
    }

    @Test
    @DisplayName("다른 회원의 폐업한 가게는 권한 오류가 먼저 발생한다.")
    void verifyOwnerOfActiveShopCheckOwnerFirst() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID otherMemberId = testFixtures.signup("other@test.com");
        UUID shopId = testFixtures.openShop(otherMemberId, "1111111111");
        testFixtures.closeShop(shopId);

        // When & Then
        assertThatThrownBy(() -> shopApi.verifyOwnerOfActiveShop(shopId, memberId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("요청한 가게가 모두 운영 중이면 전부 반환한다.")
    void requireActiveShopsAllActive() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId1 = testFixtures.openShop(memberId, "1111111111");
        UUID shopId2 = testFixtures.openShop(memberId, "2222222222");
        UUID shopId3 = testFixtures.openShop(memberId, "3333333333");
        List<UUID> shopIds = List.of(shopId1, shopId2, shopId3);

        // When
        List<ShopInfo> shopInfos = shopApi.requireActiveShops(shopIds);

        // Then
        assertThat(shopInfos)
                .extracting(ShopInfo::shopId)
                .containsExactlyInAnyOrder(shopId1, shopId2, shopId3);
    }

    @Test
    @DisplayName("운영 중인 가게 확인을 실패한다. (폐업한 가게 포함)")
    void requireActiveShopsClosed() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId1 = testFixtures.openShop(memberId, "1111111111");
        UUID shopId2 = testFixtures.openShop(memberId, "2222222222");
        UUID shopId3 = testFixtures.openShop(memberId, "3333333333");
        testFixtures.closeShop(shopId3);
        List<UUID> shopIds = List.of(shopId1, shopId2, shopId3);

        // When & Then
        assertThatThrownBy(() -> shopApi.requireActiveShops(shopIds))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> {
                            assertThat(e.getErrorCode()).isEqualTo(ShopErrorCode.SHOP_NOT_ACTIVE);
                            assertThat((List<UUID>) e.getDetails().get("shopIds")).containsExactly(shopId3);
                        });
    }

    @Test
    @DisplayName("운영 중인 가게 확인을 실패한다. (존재하지 않는 가게 포함)")
    void requireActiveShopsNotFound() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId1 = testFixtures.openShop(memberId, "1111111111");
        UUID shopId2 = UUID.randomUUID();
        UUID shopId3 = testFixtures.openShop(memberId, "3333333333");
        List<UUID> shopIds = List.of(shopId1, shopId2, shopId3);

        // When & Then
        assertThatThrownBy(() -> shopApi.requireActiveShops(shopIds))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> {
                            assertThat(e.getErrorCode()).isEqualTo(ShopErrorCode.SHOP_NOT_ACTIVE);
                            assertThat((List<UUID>) e.getDetails().get("shopIds")).containsExactly(shopId2);
                        });
    }

    @Test
    @DisplayName("운영 중인 가게 확인을 실패한다. (폐업과 없는 가게가 섞인 경우 모두 응답에 담긴다)")
    void requireActiveShopsClosedAndNotFound() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId1 = UUID.randomUUID();
        UUID shopId2 = testFixtures.openShop(memberId, "2222222222");
        UUID shopId3 = testFixtures.openShop(memberId, "3333333333");
        testFixtures.closeShop(shopId3);
        List<UUID> shopIds = List.of(shopId1, shopId2, shopId3);

        // When & Then
        assertThatThrownBy(() -> shopApi.requireActiveShops(shopIds))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> {
                            assertThat(e.getErrorCode()).isEqualTo(ShopErrorCode.SHOP_NOT_ACTIVE);
                            assertThat((List<UUID>) e.getDetails().get("shopIds")).containsExactly(shopId1, shopId3);
                        });
    }

    @Test
    @DisplayName("같은 가게 ID를 중복해서 요청해도 가게당 하나만 반환한다.")
    void requireActiveShopsDuplicateIds() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId1 = testFixtures.openShop(memberId, "1111111111");
        UUID shopId2 = testFixtures.openShop(memberId, "2222222222");
        List<UUID> shopIds = List.of(shopId1, shopId2, shopId2);

        // When
        List<ShopInfo> shopInfos = shopApi.requireActiveShops(shopIds);

        // Then
        assertThat(shopInfos).hasSize(2);
        assertThat(shopInfos)
                .extracting(ShopInfo::shopId)
                .containsExactlyInAnyOrder(shopId1, shopId2);
    }

    @Test
    @DisplayName("빈 목록으로 요청하면 빈 목록을 반환한다.")
    void requireActiveShopsEmpty() {
        // Given
        List<UUID> shopIds = List.of();

        // When
        List<ShopInfo> shopInfos = shopApi.requireActiveShops(shopIds);

        // Then
        assertThat(shopInfos).isEmpty();
    }
}
