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
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockGateTest extends IntegrationTestSupport {

    private final ProductApi productApi;
    private final TestFixtures testFixtures;
    private final StockRepository stockRepository;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public StockGateTest(ProductApi productApi, TestFixtures testFixtures, StockRepository stockRepository, StringRedisTemplate redisTemplate) {
        this.productApi = productApi;
        this.testFixtures = testFixtures;
        this.stockRepository = stockRepository;
        this.redisTemplate = redisTemplate;
    }

    @Test
    @DisplayName("재고와 Redis 수량이 100일 때 서로 다른 주문 1,000건이 동시에 예약해도 정확히 100건만 성공한다.")
    void reserveConcurrentlyThroughGate() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 100);
        redisTemplate.opsForValue().set("stock:available:" + productId, "100");
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
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // Then
        assertThat(success.get()).isEqualTo(100);
        assertThat(failures.size()).isEqualTo(900);
        assertThat(failures).allSatisfy(e ->
                assertThat(e).isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ProductErrorCode.OUT_OF_STOCK)
                )
        );
        Stock changed = stockRepository.findById(productId).get();
        assertThat(changed.getAvailable()).isEqualTo(0);
        assertThat(changed.getReserved()).isEqualTo(100);
        assertThat(redisTemplate.opsForValue().get("stock:available:" + productId)).isEqualTo("0");
    }

    @Test
    @DisplayName("재고 예약을 실패한다. (Redis 수량 부족)")
    void reserveFailsWhenGateShort() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 100);
        redisTemplate.opsForValue().set("stock:available:" + productId, "0");
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        // When & Then
        assertThatThrownBy(() -> productApi.reserve(Ids.newId(), List.of(new ReserveItem(productId, 1)), expiresAt))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.OUT_OF_STOCK)
                );
        Stock unchanged = stockRepository.findById(productId).get();
        assertThat(unchanged.getAvailable()).isEqualTo(100);
        assertThat(unchanged.getReserved()).isEqualTo(0);
    }

    @Test
    @DisplayName("게이트를 통과한 뒤 DB 재고가 모자라 예약이 실패하면 Redis 수량을 되돌린다.")
    void giveBackGateWhenDbRejects() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 1);
        redisTemplate.opsForValue().set("stock:available:" + productId, "2");
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        // When & Then
        assertThatThrownBy(() -> productApi.reserve(Ids.newId(), List.of(new ReserveItem(productId, 2)), expiresAt))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.OUT_OF_STOCK)
                );
        assertThat(redisTemplate.opsForValue().get("stock:available:" + productId)).isEqualTo("2");
    }
}
