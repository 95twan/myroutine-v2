package com.myroutine.shop.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ShopTest {

    @Test
    @DisplayName("가게를 개설하면 운영 중 상태가 되고 ID가 생성된다.")
    void openCreatesActiveShop() {
        // Given

        // When
        Shop shop = Shop.open(
                UUID.randomUUID(),
                "shop",
                "1304913993",
                "shop@test.com",
                "010-1234-5678",
                "address"
        );

        // Then
        assertThat(shop.getId()).isNotNull();
        assertThat(shop.getStatus()).isEqualTo(ShopStatus.ACTIVE);
    }

    @Test
    @DisplayName("소유자 확인을 실패한다. (다른 회원)")
    void verifyOwnerOfOtherMember() {
        // Given
        UUID otherMemberId = UUID.randomUUID();
        Shop shop = Shop.open(
                UUID.randomUUID(),
                "shop",
                "1304913993",
                "shop@test.com",
                "010-1234-5678",
                "address"
        );

        // When & Then
        assertThatThrownBy(() -> shop.verifyOwner(otherMemberId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));

    }

}
