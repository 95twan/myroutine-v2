package com.myroutine.product.application;

import com.myroutine.member.application.AddressResult;
import com.myroutine.product.domain.ProductCategory;
import com.myroutine.product.domain.StockRepository;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

public class StockAdjustmentConcurrencyTest extends IntegrationTestSupport {

    private final AdjustStockService adjustStockService;
    private final StockRepository stockRepository;
    private final TestFixtures testFixtures;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public StockAdjustmentConcurrencyTest(AdjustStockService adjustStockService, StockRepository stockRepository, TestFixtures testFixtures, JdbcTemplate jdbcTemplate) {
        this.adjustStockService = adjustStockService;
        this.stockRepository = stockRepository;
        this.testFixtures = testFixtures;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("동시에 100건을 입고해도 재고와 이력이 정확히 100건만큼 반영된다.")
    void adjustStockConcurrently() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 0);

        ExecutorService pool = Executors.newFixedThreadPool(100);
        CountDownLatch ready = new CountDownLatch(100);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<StockResult>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return adjustStockService.adjust(memberId, shopId, productId, 1, "");
            }));
        }

        // When
        ready.await(10, TimeUnit.SECONDS);
        start.countDown();

        // Then
        int success = 0;
        List<Throwable> failures = new ArrayList<>();
        for (Future<StockResult> f : futures) {
            try {
                f.get(10, TimeUnit.SECONDS);
                success++;
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            }
        }
        pool.shutdown();

        assertThat(failures).isEmpty();
        assertThat(success).isEqualTo(100);

        Map<String, Object> stock = jdbcTemplate.queryForMap("SELECT available, received FROM product.stock WHERE product_id = ?", productId);
        assertThat(stock.get("available")).isEqualTo(100);
        assertThat(stock.get("received")).isEqualTo(100);

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM product.stock_movement WHERE product_id = ? AND type = 'ADJUST'", Integer.class, productId);
        assertThat(count).isEqualTo(100);

        assertThat(stockRepository.findProductIdsWithBrokenBalance()).isEmpty();
    }

}
