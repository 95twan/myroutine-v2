package com.myroutine.product.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.product.domain.ProductErrorCode;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class ProductImageConcurrencyTest extends IntegrationTestSupport {

    private final ProductImageService productImageService;
    private final TestFixtures testFixtures;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ProductImageConcurrencyTest(ProductImageService productImageService, TestFixtures testFixtures, JdbcTemplate jdbcTemplate) {
        this.productImageService = productImageService;
        this.testFixtures = testFixtures;
        this.jdbcTemplate = jdbcTemplate;
    }

    @RepeatedTest(10)
    @DisplayName("이미지가 9장인 상품에 두 이미지를 동시에 등록해도 10장을 넘지 않는다.")
    void registerImagesConcurrently() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId);
        for (int i = 0; i < 9; i++) {
            testFixtures.registerProductImage(memberId, shopId, productId);
        }

        byte[] file = new byte[1000];
        UploadUrlResult result1 = productImageService.issueUploadUrl(memberId, shopId, productId, "image/jpeg", file.length);
        UploadUrlResult result2 = productImageService.issueUploadUrl(memberId, shopId, productId, "image/jpeg", file.length);

        HttpResponse<String> response1 = testFixtures.putToStorage(result1.uploadUrl(), result1.headers(), file);
        HttpResponse<String> response2 = testFixtures.putToStorage(result2.uploadUrl(), result2.headers(), file);
        assertThat(response1.statusCode()).isEqualTo(200);
        assertThat(response2.statusCode()).isEqualTo(200);

        List<String> objectKeys = List.of(result1.objectKey(), result2.objectKey());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);   // 둘 다 준비될 때까지
        CountDownLatch start = new CountDownLatch(1);

        List<Future<ImageResult>> futures = new ArrayList<>();
        for (String objectKey : objectKeys) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return productImageService.register(memberId, shopId, productId, objectKey);
            }));
        }

        // When
        ready.await();
        start.countDown();

        // Then
        int success = 0;
        List<Throwable> failures = new ArrayList<>();
        for (Future<ImageResult> f : futures) {
            try {
                f.get(10, TimeUnit.SECONDS);
                success++;
            } catch (ExecutionException e) {
                failures.add(e.getCause());   // 서비스가 던진 진짜 예외
            }
        }
        pool.shutdown();

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product.product_image WHERE product_id = ?", Integer.class, productId);
        assertThat(count).isEqualTo(10);
        assertThat(success).isEqualTo(1);
        assertThat(failures).hasSize(1);
        Throwable failure = failures.get(0);
        if (failure instanceof BusinessException e) {
            assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.PRODUCT_IMAGE_LIMIT_EXCEEDED);
        } else {
            assertThat(failure).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        }
    }
}
