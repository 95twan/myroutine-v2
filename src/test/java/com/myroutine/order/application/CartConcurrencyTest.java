package com.myroutine.order.application;

import com.myroutine.support.ConcurrencyRunner;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CartConcurrencyTest extends IntegrationTestSupport {

    private final CartService cartService;
    private final TestFixtures testFixtures;

    @Autowired
    public CartConcurrencyTest(CartService cartService, TestFixtures testFixtures) {
        this.cartService = cartService;
        this.testFixtures = testFixtures;
    }

    @Test
    @DisplayName("같은 상품을 동시에 10번 1개씩 담아도 한 행에 수량 10으로 저장된다.")
    void addSameProductConcurrently() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 10000, 200);

        // When
        List<ConcurrencyRunner.Result<UUID>> results = ConcurrencyRunner.run(10, () -> cartService.addItem(memberId, productId, 1));

        // Then
        assertThat(results).extracting(ConcurrencyRunner.Result::error).containsOnlyNulls();
        CartResult cart = cartService.getCart(memberId);
        assertThat(cart.items().size()).isEqualTo(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(10);
    }
}
