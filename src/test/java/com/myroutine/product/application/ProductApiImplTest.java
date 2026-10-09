package com.myroutine.product.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.common.model.Ids;
import com.myroutine.product.api.ProductApi;
import com.myroutine.product.api.ProductForCheckout;
import com.myroutine.product.api.ReleaseReason;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ProductApiImplTest extends IntegrationTestSupport {

    private final ProductApi productApi;
    private final TestFixtures testFixtures;
    private final StockRepository stockRepository;
    private final StockReservationRepository stockReservationRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ProductApiImplTest(
            ProductApi productApi,
            TestFixtures testFixtures,
            StockRepository stockRepository,
            StockReservationRepository stockReservationRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.productApi = productApi;
        this.testFixtures = testFixtures;
        this.stockRepository = stockRepository;
        this.stockReservationRepository = stockReservationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }


    @Test
    @DisplayName("체크아웃용 상품을 조회하면 없는 ID는 결과에서 빠지고 재고가 0이면 inStock이 false다.")
    void getForCheckout() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 0);
        UUID productId2 = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 5);
        List<UUID> productIds = List.of(
                productId1,
                productId2,
                Ids.newId()
        );

        // When
        List<ProductForCheckout> checkout = productApi.getForCheckout(productIds);

        // Then
        assertThat(checkout).hasSize(2);
        assertThat(checkout).extracting("productId", "inStock").containsExactlyInAnyOrder(
                tuple(productId1, false),
                tuple(productId2, true)
        );
    }

    @Test
    @DisplayName("구매 가능 상품 조회를 실패한다. (HIDDEN 상품 포함)")
    void getPurchasableNotOnSale() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 3);
        UUID productId2 = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 5);
        UUID productId3 = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 4);
        testFixtures.hideProduct(productId2);
        testFixtures.hideProduct(productId3);
        List<UUID> productIds = List.of(
                productId1,
                productId2,
                productId3
        );

        // When & Then
        assertThatThrownBy(() -> productApi.getPurchasable(productIds))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> {
                            assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.PRODUCT_NOT_ON_SALE);
                            assertThat((List<UUID>) e.getDetails().get("productIds")).containsExactly(productId2, productId3);
                        });
    }

    @Test
    @DisplayName("재고를 예약하면 available이 reserved로 이동하고 예약(HELD)과 RESERVE 이력이 생긴다.")
    void reserve() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 10);
        List<ReserveItem> reserveItems = List.of(
                new ReserveItem(productId, 3)
        );

        UUID orderId = Ids.newId();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        // When
        productApi.reserve(orderId, reserveItems, expiresAt);

        // Then
        Stock changed = stockRepository.findById(productId).get();
        assertThat(changed.getAvailable()).isEqualTo(7);
        assertThat(changed.getReserved()).isEqualTo(3);
        StockReservation stockReservation = stockReservationRepository.findByOrderIdAndProductId(orderId, productId).get();
        assertThat(stockReservation.getStatus()).isEqualTo(ReservationStatus.HELD);
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM product.stock_movement WHERE product_id = ? AND type = 'RESERVE'", Integer.class, productId);
        assertThat(count).isEqualTo(1);
        assertThat(stockRepository.findProductIdsWithBrokenBalance()).isEmpty();
    }

    @Test
    @DisplayName("같은 orderId로 재고를 두 번 예약해도 한 번만 반영된다.")
    void reserveIdempotent() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 10);
        List<ReserveItem> reserveItems = List.of(
                new ReserveItem(productId, 3)
        );

        UUID orderId = Ids.newId();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        // When
        productApi.reserve(orderId, reserveItems, expiresAt);
        productApi.reserve(orderId, reserveItems, expiresAt);

        // Then
        Stock changed = stockRepository.findById(productId).get();
        assertThat(changed.getAvailable()).isEqualTo(7);
        assertThat(changed.getReserved()).isEqualTo(3);
        assertThat(stockRepository.findProductIdsWithBrokenBalance()).isEmpty();
    }

    @Test
    @DisplayName("재고 예약을 실패한다. (상품 3개 중 3번째 재고 부족, 1·2번째 재고는 그대로이고 예약은 0건)")
    void reserveOutOfStock() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 10);
        UUID productId2 = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 10);
        UUID productId3 = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 3);
        List<ReserveItem> reserveItems = List.of(
                new ReserveItem(productId1, 3),
                new ReserveItem(productId2, 3),
                new ReserveItem(productId3, 5)
        );

        UUID orderId = Ids.newId();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        // When & Then
        assertThatThrownBy(() -> productApi.reserve(orderId, reserveItems, expiresAt))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> {
                            assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.OUT_OF_STOCK);
                            assertThat((List<UUID>) e.getDetails().get("productIds")).containsExactly(productId3);
                        });

        Stock changed1 = stockRepository.findById(productId1).get();
        Stock changed2 = stockRepository.findById(productId2).get();
        assertThat(changed1.getAvailable()).isEqualTo(10);
        assertThat(changed1.getReserved()).isEqualTo(0);
        assertThat(changed2.getAvailable()).isEqualTo(10);
        assertThat(changed2.getReserved()).isEqualTo(0);
        assertThat(stockReservationRepository.findByOrderIdAndProductId(orderId, productId1)).isEmpty();
        assertThat(stockReservationRepository.findByOrderIdAndProductId(orderId, productId2)).isEmpty();
        assertThat(stockRepository.findProductIdsWithBrokenBalance()).isEmpty();
    }

    @Test
    @DisplayName("재고 예약을 실패한다. (DISCONTINUED·HIDDEN 상품, details.productIds에 해당 상품 ID)")
    void reserveNotOnSale() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId1 = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 10);
        UUID productId2 = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 10);
        UUID productId3 = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 10);
        testFixtures.hideProduct(productId2);
        testFixtures.discontinueProduct(productId3);
        List<ReserveItem> reserveItems = List.of(
                new ReserveItem(productId1, 3),
                new ReserveItem(productId2, 3),
                new ReserveItem(productId3, 5)
        );

        UUID orderId = Ids.newId();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        // When & Then
        assertThatThrownBy(() -> productApi.reserve(orderId, reserveItems, expiresAt))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> {
                            assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.PRODUCT_NOT_ON_SALE);
                            assertThat((List<UUID>) e.getDetails().get("productIds")).containsExactlyInAnyOrder(productId2, productId3);
                        });

        Stock changed1 = stockRepository.findById(productId1).get();
        assertThat(changed1.getAvailable()).isEqualTo(10);
        assertThat(changed1.getReserved()).isEqualTo(0);
        assertThat(stockRepository.findProductIdsWithBrokenBalance()).isEmpty();
    }

    @Test
    @DisplayName("같은 orderId로 예약 확정을 두 번 해도 한 번만 반영된다.")
    void commitReservationIdempotent() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 10);
        List<ReserveItem> reserveItems = List.of(
                new ReserveItem(productId, 3)
        );

        UUID orderId = Ids.newId();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        productApi.reserve(orderId, reserveItems, expiresAt);

        // When
        productApi.commitReservation(orderId);
        productApi.commitReservation(orderId);

        // Then
        Stock changed = stockRepository.findById(productId).get();
        assertThat(changed.getAvailable()).isEqualTo(7);
        assertThat(changed.getReserved()).isEqualTo(0);
        assertThat(changed.getSold()).isEqualTo(3);
        StockReservation stockReservation = stockReservationRepository.findByOrderIdAndProductId(orderId, productId).get();
        assertThat(stockReservation.getStatus()).isEqualTo(ReservationStatus.COMMITTED);
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM product.stock_movement WHERE product_id = ? AND type = 'COMMIT'", Integer.class, productId);
        assertThat(count).isEqualTo(1);
        assertThat(stockRepository.findProductIdsWithBrokenBalance()).isEmpty();
    }

    @Test
    @DisplayName("확정(COMMITTED)된 예약을 해제해도 아무 변화가 없다.")
    void releaseCommittedReservation() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 10);
        List<ReserveItem> reserveItems = List.of(
                new ReserveItem(productId, 3)
        );

        UUID orderId = Ids.newId();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        productApi.reserve(orderId, reserveItems, expiresAt);
        productApi.commitReservation(orderId);

        // When
        productApi.releaseReservation(orderId, ReleaseReason.EXPIRED);

        // Then
        Stock changed = stockRepository.findById(productId).get();
        assertThat(changed.getAvailable()).isEqualTo(7);
        assertThat(changed.getReserved()).isEqualTo(0);
        assertThat(changed.getSold()).isEqualTo(3);
        StockReservation stockReservation = stockReservationRepository.findByOrderIdAndProductId(orderId, productId).get();
        assertThat(stockReservation.getStatus()).isEqualTo(ReservationStatus.COMMITTED);
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM product.stock_movement WHERE product_id = ? AND type = 'RELEASE'", Integer.class, productId);
        assertThat(count).isEqualTo(0);
        assertThat(stockRepository.findProductIdsWithBrokenBalance()).isEmpty();
    }

    @Test
    @DisplayName("결제 실패로 예약을 해제하면 reserved가 available로 복구되고 예약은 RELEASED가 된다. 두 번 해제해도 한 번만 반영된다.")
    void releaseReservationPaymentFailed() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 10);
        List<ReserveItem> reserveItems = List.of(
                new ReserveItem(productId, 3)
        );

        UUID orderId = Ids.newId();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        productApi.reserve(orderId, reserveItems, expiresAt);

        // When
        productApi.releaseReservation(orderId, ReleaseReason.PAYMENT_FAILED);
        productApi.releaseReservation(orderId, ReleaseReason.PAYMENT_FAILED);

        // Then
        Stock changed = stockRepository.findById(productId).get();
        assertThat(changed.getAvailable()).isEqualTo(10);
        assertThat(changed.getReserved()).isEqualTo(0);
        StockReservation stockReservation = stockReservationRepository.findByOrderIdAndProductId(orderId, productId).get();
        assertThat(stockReservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM product.stock_movement WHERE product_id = ? AND type = 'RELEASE'", Integer.class, productId);
        assertThat(count).isEqualTo(1);
        assertThat(stockRepository.findProductIdsWithBrokenBalance()).isEmpty();
    }

    @Test
    @DisplayName("만료로 예약을 해제하면 reserved가 available로 복구되고 예약은 EXPIRED가 된다. 두 번 해제해도 한 번만 반영된다.")
    void releaseReservationExpired() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 10);
        List<ReserveItem> reserveItems = List.of(
                new ReserveItem(productId, 3)
        );

        UUID orderId = Ids.newId();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        productApi.reserve(orderId, reserveItems, expiresAt);

        // When
        productApi.releaseReservation(orderId, ReleaseReason.EXPIRED);
        productApi.releaseReservation(orderId, ReleaseReason.EXPIRED);

        // Then
        Stock changed = stockRepository.findById(productId).get();
        assertThat(changed.getAvailable()).isEqualTo(10);
        assertThat(changed.getReserved()).isEqualTo(0);
        StockReservation stockReservation = stockReservationRepository.findByOrderIdAndProductId(orderId, productId).get();
        assertThat(stockReservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM product.stock_movement WHERE product_id = ? AND type = 'RELEASE'", Integer.class, productId);
        assertThat(count).isEqualTo(1);
        assertThat(stockRepository.findProductIdsWithBrokenBalance()).isEmpty();
    }

    @Test
    @DisplayName("같은 refundId로 재고를 두 번 복구해도 한 번만 반영된다.")
    void restoreIdempotent() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 10);
        List<ReserveItem> reserveItems = List.of(
                new ReserveItem(productId, 3)
        );

        UUID orderId = Ids.newId();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        productApi.reserve(orderId, reserveItems, expiresAt);
        productApi.commitReservation(orderId);

        UUID refundId = Ids.newId();

        // When
        productApi.restore(orderId, productId, 1, refundId);
        productApi.restore(orderId, productId, 1, refundId);

        // Then
        Stock changed = stockRepository.findById(productId).get();
        assertThat(changed.getAvailable()).isEqualTo(8);
        assertThat(changed.getReserved()).isEqualTo(0);
        assertThat(changed.getSold()).isEqualTo(2);
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM product.stock_movement WHERE product_id = ? AND type = 'RESTORE'", Integer.class, productId);
        assertThat(count).isEqualTo(1);
        assertThat(stockRepository.findProductIdsWithBrokenBalance()).isEmpty();
    }

    @Test
    @DisplayName("재고 복구를 실패한다. (확정되지 않은 HELD 예약, 재고 변화 없음)")
    void restoreNotCommitted() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 10);
        List<ReserveItem> reserveItems = List.of(
                new ReserveItem(productId, 3)
        );

        UUID orderId = Ids.newId();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        productApi.reserve(orderId, reserveItems, expiresAt);

        UUID refundId = Ids.newId();

        // When & Then
        assertThatThrownBy(() -> productApi.restore(orderId, productId, 1, refundId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION)
                );

        Stock changed = stockRepository.findById(productId).get();
        assertThat(changed.getAvailable()).isEqualTo(7);
        assertThat(changed.getReserved()).isEqualTo(3);
        assertThat(changed.getSold()).isEqualTo(0);
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM product.stock_movement WHERE product_id = ? AND type = 'RESTORE'", Integer.class, productId);
        assertThat(count).isEqualTo(0);
        assertThat(stockRepository.findProductIdsWithBrokenBalance()).isEmpty();
    }

    @Test
    @DisplayName("재고 복구를 실패한다. (예약 수량보다 큰 수량, 재고 변화 없음)")
    void restoreOverQuantity() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, ProductCategory.ETC, 10);
        List<ReserveItem> reserveItems = List.of(
                new ReserveItem(productId, 3)
        );

        UUID orderId = Ids.newId();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        productApi.reserve(orderId, reserveItems, expiresAt);
        productApi.commitReservation(orderId);

        UUID refundId = Ids.newId();

        // When & Then
        assertThatThrownBy(() -> productApi.restore(orderId, productId, 5, refundId))
                .isInstanceOf(IllegalArgumentException.class);

        Stock changed = stockRepository.findById(productId).get();
        assertThat(changed.getAvailable()).isEqualTo(7);
        assertThat(changed.getReserved()).isEqualTo(0);
        assertThat(changed.getSold()).isEqualTo(3);
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM product.stock_movement WHERE product_id = ? AND type = 'RESTORE'", Integer.class, productId);
        assertThat(count).isEqualTo(0);
        assertThat(stockRepository.findProductIdsWithBrokenBalance()).isEmpty();
    }
}
