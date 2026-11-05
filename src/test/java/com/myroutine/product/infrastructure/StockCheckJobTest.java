package com.myroutine.product.infrastructure;

import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class StockCheckJobTest extends IntegrationTestSupport {

    private final StockCheckJob stockCheckJob;
    private final MeterRegistry registry;
    private final TestFixtures testFixtures;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public StockCheckJobTest(StockCheckJob stockCheckJob, MeterRegistry registry, TestFixtures testFixtures, JdbcTemplate jdbcTemplate) {
        this.stockCheckJob = stockCheckJob;
        this.registry = registry;
        this.testFixtures = testFixtures;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("재고가 입고 이력·예약과 맞으면 불일치 0건을 보고한다.")
    void checkReportsZeroWhenStockMatchesRecords() {
        // Given
        UUID sellerId = testFixtures.signup("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        // When
        int mismatch = stockCheckJob.check();

        // Then
        assertThat(mismatch).isEqualTo(0);
        assertThat(registry.get("stock_balance_mismatch_count").gauge().value()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("입고 합과 received가 다르면 불일치로 보고하고 되돌리면 0으로 돌아온다.")
    void checkReportsReceivedMismatchAndRecovers(CapturedOutput output) {
        // Given
        UUID sellerId = testFixtures.signup("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        jdbcTemplate.update("UPDATE product.stock SET received = received + 5, available = available + 5 WHERE product_id = ?", productId);

        // When
        int mismatch = stockCheckJob.check();

        // Then
        assertThat(mismatch).isEqualTo(1);
        assertThat(registry.get("stock_balance_mismatch_count").gauge().value()).isEqualTo(1.0);
        assertThat(output.getAll()).contains("stock mismatch productId=" + productId);

        // When
        jdbcTemplate.update("UPDATE product.stock SET received = received - 5, available = available - 5 WHERE product_id = ?", productId);
        int recovered = stockCheckJob.check();

        // Then
        assertThat(recovered).isEqualTo(0);
        assertThat(registry.get("stock_balance_mismatch_count").gauge().value()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("HELD 예약 합과 reserved가 다르면 불일치로 보고한다.")
    void checkReportsReservedMismatch(CapturedOutput output) {
        // Given
        UUID sellerId = testFixtures.signup("seller1@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID buyerId = testFixtures.signup("buyer@test.com");
        testFixtures.createPendingOrder(buyerId, productId);
        testFixtures.breakReservedStock(productId);

        // When
        int mismatch = stockCheckJob.check();

        // Then
        assertThat(mismatch).isEqualTo(1);
        assertThat(registry.get("stock_balance_mismatch_count").gauge().value()).isEqualTo(1.0);
        assertThat(output.getAll()).contains("stock mismatch productId=" + productId);
    }
}
