package com.myroutine.order.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.support.ConcurrencyRunner;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.OrderSnapshot;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmConcurrencyTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final ConfirmPurchaseService confirmPurchaseService;
    private final AutoConfirmService autoConfirmService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ConfirmConcurrencyTest(TestFixtures testFixtures, ConfirmPurchaseService confirmPurchaseService, AutoConfirmService autoConfirmService, JdbcTemplate jdbcTemplate) {
        this.testFixtures = testFixtures;
        this.confirmPurchaseService = confirmPurchaseService;
        this.autoConfirmService = autoConfirmService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @RepeatedTest(20)
    @DisplayName("수동 확정과 자동 확정이 동시에 와도 품목은 CONFIRMED로 한 번 확정되고 가게주문은 COMPLETED가 된다. (20회 반복)")
    void manualAndAutoConfirmConcurrently() throws InterruptedException {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 200);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID order1Id = testFixtures.createDeliveredOrder(buyerId, sellerId, shopId, product1Id);

        UUID shopOrder1Id = testFixtures.shopOrderIdOf(order1Id, shopId);
        UUID line1Id = testFixtures.orderLineIdOf(order1Id, product1Id);

        testFixtures.setShopOrderDeliveredAt(shopOrder1Id, Instant.now().minus(Duration.ofDays(8)));

        // When
        Callable<Object> task1 = () -> confirmPurchaseService.confirm(buyerId, order1Id, line1Id);
        Callable<Object> task2 = autoConfirmService::confirmBatch;

        List<ConcurrencyRunner.Result<Object>> results = ConcurrencyRunner.run(List.of(task1, task2));

        // Then
        for (ConcurrencyRunner.Result<Object> result : results) {
            Throwable error = result.error();
            if (error == null) {
                continue;
            }
            if (error instanceof BusinessException be) {
                assertThat(be.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION);
            } else {
                assertThat(error).isInstanceOf(OptimisticLockingFailureException.class);
            }
        }

        OrderSnapshot s = testFixtures.snapshotOf(order1Id);
        assertThat(s.line(product1Id).status()).isEqualTo("CONFIRMED");
        assertThat(s.line(product1Id).confirmedAt()).isNotNull();
        assertThat(s.shopOrderStatus()).isEqualTo("COMPLETED");
    }
}
