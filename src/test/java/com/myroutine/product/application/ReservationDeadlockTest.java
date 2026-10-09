package com.myroutine.product.application;

import com.myroutine.common.model.Ids;
import com.myroutine.product.api.ProductApi;
import com.myroutine.product.api.ReserveItem;
import com.myroutine.product.domain.ProductCategory;
import com.myroutine.product.domain.Stock;
import com.myroutine.product.domain.StockRepository;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationDeadlockTest extends IntegrationTestSupport {

    private final ProductApi productApi;
    private final TestFixtures testFixtures;
    private final StockRepository stockRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ReservationDeadlockTest(ProductApi productApi, TestFixtures testFixtures, StockRepository stockRepository, JdbcTemplate jdbcTemplate) {
        this.productApi = productApi;
        this.testFixtures = testFixtures;
        this.stockRepository = stockRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @RepeatedTest(50)
    @DisplayName("상품 순서가 반대인 두 주문이 동시에 예약해도 둘 다 성공하고 데드락이 발생하지 않는다.")
    void reserveInOppositeOrder() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 100);
        UUID productId2 = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 100);
        List<ReserveItem> reserveItems1 = List.of(
                new ReserveItem(productId1, 1),
                new ReserveItem(productId2, 1)
        );
        List<ReserveItem> reserveItems2 = List.of(
                new ReserveItem(productId2, 1),
                new ReserveItem(productId1, 1)
        );
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        AtomicInteger success = new AtomicInteger();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        for (List<ReserveItem> reserveItems : List.of(reserveItems1, reserveItems2)) {
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
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // Then
        assertThat(success.get()).isEqualTo(2);
        assertThat(failures).isEmpty();
        Stock changed1 = stockRepository.findById(productId1).get();
        assertThat(changed1.getAvailable()).isEqualTo(98);
        assertThat(changed1.getReserved()).isEqualTo(2);
        Stock changed2 = stockRepository.findById(productId2).get();
        assertThat(changed2.getAvailable()).isEqualTo(98);
        assertThat(changed2.getReserved()).isEqualTo(2);
        assertThat(stockRepository.findProductIdsWithBrokenBalance()).isEmpty();
    }

}
