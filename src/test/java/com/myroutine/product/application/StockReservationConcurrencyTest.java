package com.myroutine.product.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.model.Ids;
import com.myroutine.product.api.ProductApi;
import com.myroutine.product.api.ReserveItem;
import com.myroutine.product.domain.*;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StockReservationConcurrencyTest extends IntegrationTestSupport {

    private final ProductApi productApi;
    private final TestFixtures testFixtures;
    private final StockRepository stockRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public StockReservationConcurrencyTest(ProductApi productApi, TestFixtures testFixtures, StockRepository stockRepository, JdbcTemplate jdbcTemplate) {
        this.productApi = productApi;
        this.testFixtures = testFixtures;
        this.stockRepository = stockRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("재고 100개에 서로 다른 주문 1,000건이 동시에 예약해도 정확히 100건만 성공한다.")
    void reserveConcurrently() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 100);
        List<ReserveItem> reserveItems = List.of(new ReserveItem(productId, 1));
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        int threads = 64;
        int total = 1000;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(total);

        AtomicInteger success = new AtomicInteger();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < total; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    productApi.reserve(Ids.newId(), reserveItems, expiresAt);
                    success.incrementAndGet();
                } catch (Throwable e) {
                    failures.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        // When
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();   // 64개가 출발선에 설 때까지 대기
        start.countDown();                                        // 출발
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();    // 1,000건이 모두 끝날 때까지 대기
        pool.shutdown();

        // Then
        assertThat(success.get()).isEqualTo(100);
        assertThat(failures.size()).isEqualTo(900);
        assertThat(failures).allSatisfy(e -> {
            assertThat(e).isInstanceOfSatisfying(BusinessException.class,
                    ex -> assertThat(ex.getErrorCode()).isEqualTo(ProductErrorCode.OUT_OF_STOCK)
            );
        });
        Stock changed = stockRepository.findById(productId).get();
        assertThat(changed.getAvailable()).isEqualTo(0);
        assertThat(changed.getReserved()).isEqualTo(100);
        Integer reservationCount = jdbcTemplate.queryForObject("SELECT count(*) FROM product.stock_reservation WHERE product_id = ? AND status = 'HELD'", Integer.class, productId);
        assertThat(reservationCount).isEqualTo(100);
        Integer stockMovementCount = jdbcTemplate.queryForObject("SELECT count(*) FROM product.stock_movement WHERE product_id = ? AND type = 'RESERVE'", Integer.class, productId);
        assertThat(stockMovementCount).isEqualTo(100);
        assertThat(stockRepository.findProductIdsWithBrokenBalance()).isEmpty();
    }

}
