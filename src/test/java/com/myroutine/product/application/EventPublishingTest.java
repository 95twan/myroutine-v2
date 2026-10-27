package com.myroutine.product.application;

import com.myroutine.product.api.event.ProductTopics;
import com.myroutine.product.domain.ProductStatus;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.OutboxAssert;
import com.myroutine.support.OutboxSnapshot;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

class EventPublishingTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final OutboxAssert outboxAssert;
    private final ProductDiscontinueService productDiscontinueService;

    @Autowired
    public EventPublishingTest(TestFixtures testFixtures, OutboxAssert outboxAssert, ProductDiscontinueService productDiscontinueService) {
        this.testFixtures = testFixtures;
        this.outboxAssert = outboxAssert;
        this.productDiscontinueService = productDiscontinueService;
    }

    @Test
    @DisplayName("상품을 등록하면 product-upserted 이벤트를 남긴다.")
    void registerPublishesProductUpserted() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");

        outboxAssert.clear();

        // When
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        // Then
        List<OutboxSnapshot> rows = outboxAssert.rows(ProductTopics.PRODUCT_UPSERTED);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).aggregateType()).isEqualTo("PRODUCT");
        assertThat(rows.get(0).aggregateId()).isEqualTo(productId);
        assertThat(rows.get(0).payload().get("productId").asString()).isEqualTo(productId.toString());
        assertThat(rows.get(0).payload().get("shopId").asString()).isEqualTo(shopId.toString());
        assertThat(rows.get(0).payload().get("name").asString()).isEqualTo("product");
        assertThat(rows.get(0).payload().get("description").asString()).isEqualTo("description");
        assertThat(rows.get(0).payload().get("category").asString()).isEqualTo("ETC");
        assertThat(rows.get(0).payload().get("price").asLong()).isEqualTo(1000);
        assertThat(rows.get(0).payload().get("status").asString()).isEqualTo("ON_SALE");
        assertThat(rows.get(0).payload().get("subscribable").asBoolean()).isFalse();
        assertThat(rows.get(0).payload().get("version").asLong()).isEqualTo(testFixtures.productSnapshotOf(productId).version());
    }

    @Test
    @DisplayName("가격 변경 없는 수정은 product-upserted만 남기고 version이 1 오른다.")
    void updateWithoutPriceChangePublishesUpsertedOnly() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        long version = testFixtures.productSnapshotOf(productId).version();

        outboxAssert.clear();

        // When
        testFixtures.updateProduct(sellerId, shopId, productId, "renamed", 1000L);

        // Then
        List<OutboxSnapshot> rows = outboxAssert.rows(ProductTopics.PRODUCT_UPSERTED);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).payload().get("name").asString()).isEqualTo("renamed");
        assertThat(rows.get(0).payload().get("price").asLong()).isEqualTo(1000);
        assertThat(rows.get(0).payload().get("version").asLong()).isEqualTo(version + 1);

        assertThat(testFixtures.productSnapshotOf(productId).version()).isEqualTo(version + 1);

        assertThat(outboxAssert.rows(ProductTopics.PRODUCT_PRICE_CHANGED)).hasSize(0);
    }

    @Test
    @DisplayName("가격을 변경하면 product-upserted와 product-price-changed를 남긴다.")
    void updateWithPriceChangePublishesPriceChanged() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID productId = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        outboxAssert.clear();

        // When
        testFixtures.updateProduct(sellerId, shopId, productId, null, 1200L);

        // Then
        List<OutboxSnapshot> productUpsertRows = outboxAssert.rows(ProductTopics.PRODUCT_UPSERTED);
        assertThat(productUpsertRows).hasSize(1);
        assertThat(productUpsertRows.get(0).payload().get("name").asString()).isEqualTo("product");
        assertThat(productUpsertRows.get(0).payload().get("price").asLong()).isEqualTo(1200);
        assertThat(productUpsertRows.get(0).payload().get("version").asLong()).isEqualTo(testFixtures.productSnapshotOf(productId).version());

        List<OutboxSnapshot> productPriceChangedRows = outboxAssert.rows(ProductTopics.PRODUCT_PRICE_CHANGED);
        assertThat(productPriceChangedRows).hasSize(1);
        assertThat(productPriceChangedRows.get(0).aggregateType()).isEqualTo("PRODUCT");
        assertThat(productPriceChangedRows.get(0).aggregateId()).isEqualTo(productId);
        assertThat(productPriceChangedRows.get(0).payload().get("productId").asString()).isEqualTo(productId.toString());
        assertThat(productPriceChangedRows.get(0).payload().get("oldPrice").asLong()).isEqualTo(1000);
        assertThat(productPriceChangedRows.get(0).payload().get("newPrice").asLong()).isEqualTo(1200);
        Instant eventChangedAt = Instant.parse(productPriceChangedRows.get(0).payload().get("changedAt").asString()).truncatedTo(ChronoUnit.MILLIS);
        Instant dbChangedAt = testFixtures.productSnapshotOf(productId).priceChanges().get(0).changedAt().truncatedTo(ChronoUnit.MILLIS);
        assertThat(eventChangedAt).isEqualTo(dbChangedAt);
    }

    @Test
    @DisplayName("상태를 변경하면 product-upserted를 남기고 단종일 때만 product-discontinued를 더 남긴다.")
    void changeStatusPublishesDiscontinuedOnlyForDiscontinued() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);

        outboxAssert.clear();

        // When
        testFixtures.changeProductStatus(sellerId, shopId, product1Id, ProductStatus.HIDDEN);

        // Then
        List<OutboxSnapshot> productUpsertRows = outboxAssert.rows(ProductTopics.PRODUCT_UPSERTED);
        assertThat(productUpsertRows).hasSize(1);
        assertThat(productUpsertRows.get(0).payload().get("status").asString()).isEqualTo("HIDDEN");
        assertThat(productUpsertRows.get(0).payload().get("version").asLong()).isEqualTo(testFixtures.productSnapshotOf(product1Id).version());

        List<OutboxSnapshot> productDiscontinuedRows = outboxAssert.rows(ProductTopics.PRODUCT_DISCONTINUED);
        assertThat(productDiscontinuedRows).hasSize(0);

        outboxAssert.clear();

        testFixtures.changeProductStatus(sellerId, shopId, product1Id, ProductStatus.DISCONTINUED);

        productUpsertRows = outboxAssert.rows(ProductTopics.PRODUCT_UPSERTED);
        assertThat(productUpsertRows).hasSize(1);
        assertThat(productUpsertRows.get(0).payload().get("status").asString()).isEqualTo("DISCONTINUED");
        assertThat(productUpsertRows.get(0).payload().get("version").asLong()).isEqualTo(testFixtures.productSnapshotOf(product1Id).version());

        productDiscontinuedRows = outboxAssert.rows(ProductTopics.PRODUCT_DISCONTINUED);
        assertThat(productDiscontinuedRows).hasSize(1);
        assertThat(productDiscontinuedRows.get(0).payload().get("productId").asString()).isEqualTo(product1Id.toString());
        assertThat(productDiscontinuedRows.get(0).payload().get("shopId").asString()).isEqualTo(shopId.toString());
        assertThat(productDiscontinuedRows.get(0).payload().get("reason").asString()).isEqualTo("SELLER");
    }

    @Test
    @DisplayName("가게 폐업 단종은 아직 단종되지 않은 상품마다 이벤트를 남긴다.")
    void discontinueAllByShopPublishesPerProduct() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shopId, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shopId, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shopId, 3000, 10);

        testFixtures.hideProduct(product2Id);
        testFixtures.discontinueProduct(product3Id);

        outboxAssert.clear();

        // When
        productDiscontinueService.discontinueAllByShop(shopId);

        // Then
        List<OutboxSnapshot> productUpsertRows = outboxAssert.rows(ProductTopics.PRODUCT_UPSERTED);
        assertThat(productUpsertRows).hasSize(2);
        assertThat(productUpsertRows).extracting(OutboxSnapshot::aggregateId).containsExactlyInAnyOrder(product1Id, product2Id);
        assertThat(productUpsertRows.get(0).payload().get("status").asString()).isEqualTo("DISCONTINUED");
        assertThat(productUpsertRows.get(1).payload().get("status").asString()).isEqualTo("DISCONTINUED");
        Map<UUID, OutboxSnapshot> byProduct = productUpsertRows.stream()
                .collect(toMap(OutboxSnapshot::aggregateId, Function.identity()));
        assertThat(byProduct.get(product1Id).payload().get("version").asLong())
                .isEqualTo(testFixtures.productSnapshotOf(product1Id).version());
        assertThat(byProduct.get(product2Id).payload().get("version").asLong())
                .isEqualTo(testFixtures.productSnapshotOf(product2Id).version());

        List<OutboxSnapshot> productPriceChangedRows = outboxAssert.rows(ProductTopics.PRODUCT_DISCONTINUED);
        assertThat(productPriceChangedRows).hasSize(2);
        assertThat(productPriceChangedRows).extracting(OutboxSnapshot::aggregateId).containsExactlyInAnyOrder(product1Id, product2Id);
        assertThat(productPriceChangedRows.get(0).payload().get("reason").asString()).isEqualTo("SHOP_CLOSED");
        assertThat(productPriceChangedRows.get(1).payload().get("reason").asString()).isEqualTo("SHOP_CLOSED");
        assertThat(productPriceChangedRows.get(0).payload().get("shopId").asString()).isEqualTo(shopId.toString());
        assertThat(productPriceChangedRows.get(1).payload().get("shopId").asString()).isEqualTo(shopId.toString());

        productDiscontinueService.discontinueAllByShop(shopId);
        productUpsertRows = outboxAssert.rows(ProductTopics.PRODUCT_UPSERTED);
        assertThat(productUpsertRows).hasSize(2);
        productPriceChangedRows = outboxAssert.rows(ProductTopics.PRODUCT_DISCONTINUED);
        assertThat(productPriceChangedRows).hasSize(2);
    }
}
