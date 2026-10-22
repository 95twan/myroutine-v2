package com.myroutine.shop.web;

import com.myroutine.common.model.Ids;
import com.myroutine.common.outbox.OutboxRelay;
import com.myroutine.member.application.MemberRoleService;
import com.myroutine.product.application.ProductDiscontinueService;
import com.myroutine.shop.api.event.ShopClosedEvent;
import com.myroutine.shop.api.event.ShopTopics;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CloseShopTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final OutboxAssert outboxAssert;
    private final JdbcTemplate jdbcTemplate;
    private final OutboxRelay outboxRelay;
    private final TestKafka testKafka;

    @MockitoSpyBean
    private ProductDiscontinueService productDiscontinueService;

    @MockitoSpyBean
    private MemberRoleService memberRoleService;

    @Autowired
    public CloseShopTest(MockMvc mockMvc, TestFixtures testFixtures, OutboxAssert outboxAssert, JdbcTemplate jdbcTemplate, OutboxRelay outboxRelay, TestKafka testKafka) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.outboxAssert = outboxAssert;
        this.jdbcTemplate = jdbcTemplate;
        this.outboxRelay = outboxRelay;
        this.testKafka = testKafka;
    }

    @Test
    @DisplayName("진행 중인 가게주문이 있으면 폐업을 실패한다. (SHOP_HAS_ACTIVE_ORDERS)")
    void closeShopWithActiveOrderFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        String token = testFixtures.login("seller@test.com");
        UUID shop1Id = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shop1Id, 1000, 10);
        testFixtures.changeRole(sellerId, "SELLER");

        UUID buyerId = testFixtures.signup("buyer@test.com");
        testFixtures.createPaidOrder(buyerId, product1Id);

        outboxAssert.clear();

        // When & Then
        mockMvc.perform(delete("/api/shops/{id}", shop1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SHOP_HAS_ACTIVE_ORDERS"));

        ShopSnapshot shopSnapshot = testFixtures.shopSnapshotOf(shop1Id);
        assertThat(shopSnapshot.status()).isEqualTo("ACTIVE");
        assertThat(shopSnapshot.closedAt()).isNull();

        ProductSnapshot productSnapshot = testFixtures.productSnapshotOf(product1Id);
        assertThat(productSnapshot.status()).isEqualTo("ON_SALE");

        assertThat(testFixtures.countOf("common.outbox_event")).isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "PAID", "SHIPPED", "DELIVERED"})
    @DisplayName("PENDING, PAID, SHIPPED, DELIVERED 가게주문이 있으면 폐업을 실패한다. (SHOP_HAS_ACTIVE_ORDERS)")
    void closeShopWithActiveShopOrderStatusFails(String shopOrderStatus) throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        String token = testFixtures.login("seller@test.com");
        UUID shop1Id = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shop1Id, 1000, 10);
        testFixtures.changeRole(sellerId, "SELLER");

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id);
        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shop1Id);

        jdbcTemplate.update("UPDATE orders.shop_order SET status = ? WHERE id = ?", shopOrderStatus, shopOrderId);

        outboxAssert.clear();

        // When & Then
        mockMvc.perform(delete("/api/shops/{id}", shop1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SHOP_HAS_ACTIVE_ORDERS"));

        ShopSnapshot shopSnapshot = testFixtures.shopSnapshotOf(shop1Id);
        assertThat(shopSnapshot.status()).isEqualTo("ACTIVE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "CANCELLED"})
    @DisplayName("COMPLETED, CANCELLED 가게주문만 있으면 폐업한다.")
    void closeShopWithTerminalShopOrderStatusSucceeds(String shopOrderStatus) throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        String token = testFixtures.login("seller@test.com");
        UUID shop1Id = testFixtures.openShop(sellerId, "1111111111");
        UUID product1Id = testFixtures.registerProduct(sellerId, shop1Id, 1000, 10);
        testFixtures.changeRole(sellerId, "SELLER");

        UUID buyerId = testFixtures.signup("buyer@test.com");
        UUID orderId = testFixtures.createPaidOrder(buyerId, product1Id);
        UUID shopOrderId = testFixtures.shopOrderIdOf(orderId, shop1Id);

        jdbcTemplate.update("UPDATE orders.shop_order SET status = ? WHERE id = ?", shopOrderStatus, shopOrderId);

        outboxAssert.clear();

        // When & Then
        mockMvc.perform(delete("/api/shops/{id}", shop1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        ShopSnapshot shopSnapshot = testFixtures.shopSnapshotOf(shop1Id);
        assertThat(shopSnapshot.status()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("폐업하면 가게가 CLOSED가 되고 이벤트가 발행되며 판매 중·숨김 상품이 단종된다.")
    void closeShopClosesShopPublishesEventAndDiscontinuesProducts() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        String token = testFixtures.login("seller@test.com");
        UUID shop1Id = testFixtures.openShop(sellerId, "1111111111");
        testFixtures.changeRole(sellerId, "SELLER");
        UUID product1Id = testFixtures.registerProduct(sellerId, shop1Id, 1000, 10);
        UUID product2Id = testFixtures.registerProduct(sellerId, shop1Id, 2000, 10);
        UUID product3Id = testFixtures.registerProduct(sellerId, shop1Id, 3000, 10);
        testFixtures.hideProduct(product2Id);
        testFixtures.discontinueProduct(product3Id);

        outboxAssert.clear();

        // When & Then
        mockMvc.perform(delete("/api/shops/{id}", shop1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        ShopSnapshot shopSnapshot = testFixtures.shopSnapshotOf(shop1Id);
        assertThat(shopSnapshot.status()).isEqualTo("CLOSED");
        assertThat(shopSnapshot.closedAt()).isNotNull();

        List<OutboxSnapshot> rows = outboxAssert.rows("shop.shop-closed.v1");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).aggregateType()).isEqualTo("SHOP");
        assertThat(rows.get(0).aggregateId()).isEqualTo(shop1Id);
        assertThat(rows.get(0).status()).isEqualTo("READY");
        assertThat(rows.get(0).payload().get("shopId").asString()).isEqualTo(shop1Id.toString());
        assertThat(rows.get(0).payload().get("memberId").asString()).isEqualTo(sellerId.toString());

        int count = outboxRelay.relayOnce();

        assertThat(count).isEqualTo(1);
        Eventually.await(Duration.ofSeconds(5), () -> {
            assertThat(testFixtures.productSnapshotOf(product1Id).status()).isEqualTo("DISCONTINUED");
            assertThat(testFixtures.productSnapshotOf(product2Id).status()).isEqualTo("DISCONTINUED");
        });
    }

    @Test
    @DisplayName("남의 가게 폐업을 실패한다. (FORBIDDEN)")
    void closeOthersShopFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shop1Id = testFixtures.openShop(sellerId, "1111111111");
        testFixtures.changeRole(sellerId, "SELLER");

        String otherToken = testFixtures.token("other@test.com");

        outboxAssert.clear();

        // When & Then
        mockMvc.perform(delete("/api/shops/{id}", shop1Id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        ShopSnapshot shopSnapshot = testFixtures.shopSnapshotOf(shop1Id);
        assertThat(shopSnapshot.status()).isEqualTo("ACTIVE");
        assertThat(testFixtures.countOf("common.outbox_event")).isEqualTo(0);
    }

    @Test
    @DisplayName("이미 폐업한 가게의 폐업을 실패한다. (SHOP_NOT_ACTIVE)")
    void closeAlreadyClosedShopFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        String token = testFixtures.login("seller@test.com");
        UUID shop1Id = testFixtures.openShop(sellerId, "1111111111");
        testFixtures.changeRole(sellerId, "SELLER");

        mockMvc.perform(delete("/api/shops/{id}", shop1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        outboxAssert.clear();

        // When & Then
        mockMvc.perform(delete("/api/shops/{id}", shop1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SHOP_NOT_ACTIVE"));

        assertThat(testFixtures.countOf("common.outbox_event")).isEqualTo(0);
    }

    @Test
    @DisplayName("없는 가게의 폐업을 실패한다. (SHOP_NOT_FOUND)")
    void closeMissingShopFails() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        String token = testFixtures.login("seller@test.com");
        testFixtures.changeRole(sellerId, "SELLER");

        outboxAssert.clear();

        // When & Then
        mockMvc.perform(delete("/api/shops/{id}", Ids.newId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOP_NOT_FOUND"));

        assertThat(testFixtures.countOf("common.outbox_event")).isEqualTo(0);
    }

    @Test
    @DisplayName("가게 2개 중 1개를 폐업하면 SELLER를 유지하고, 나머지도 폐업하면 USER로 회수한다.")
    void closeShopsRevokesSellerOnlyAfterLastActiveShop() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        String token = testFixtures.login("seller@test.com");
        UUID shop1Id = testFixtures.openShop(sellerId, "1111111111");
        UUID shop2Id = testFixtures.openShop(sellerId, "2222222222");
        testFixtures.changeRole(sellerId, "SELLER");

        outboxAssert.clear();

        // When & Then
        mockMvc.perform(delete("/api/shops/{id}", shop1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        int count = outboxRelay.relayOnce();
        assertThat(count).isEqualTo(1);

        List<OutboxSnapshot> rows = outboxAssert.rows(ShopTopics.SHOP_CLOSED);
        UUID eventId1 = rows.stream()
                .filter(r -> r.aggregateId().equals(shop1Id))
                .findFirst().orElseThrow()
                .id();
        Eventually.await(Duration.ofSeconds(5), () -> assertThat(testFixtures.processedCountOf("member.shop-closed", eventId1)).isEqualTo(1));
        MemberSnapshot memberSnapshot = testFixtures.memberSnapshotOf(sellerId);
        assertThat(memberSnapshot.role()).isEqualTo("SELLER");

        mockMvc.perform(delete("/api/shops/{id}", shop2Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        count = outboxRelay.relayOnce();
        assertThat(count).isEqualTo(1);

        rows = outboxAssert.rows(ShopTopics.SHOP_CLOSED);
        UUID eventId2 = rows.stream()
                .filter(r -> r.aggregateId().equals(shop2Id))
                .findFirst().orElseThrow()
                .id();
        Eventually.await(Duration.ofSeconds(5), () -> assertThat(testFixtures.processedCountOf("member.shop-closed", eventId2)).isEqualTo(1));
        memberSnapshot = testFixtures.memberSnapshotOf(sellerId);
        assertThat(memberSnapshot.role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("같은 회원의 가게 2개를 동시에 폐업해도 SELLER를 회수한다.")
    void closeTwoShopsConcurrentlyRevokesSeller() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        String token = testFixtures.login("seller@test.com");
        UUID shop1Id = testFixtures.openShop(sellerId, "1111111111");
        UUID shop2Id = testFixtures.openShop(sellerId, "2222222222");
        testFixtures.changeRole(sellerId, "SELLER");

        outboxAssert.clear();

        // When
        Callable<MvcResult> test1 = () -> mockMvc.perform(delete("/api/shops/{id}", shop1Id)
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        Callable<MvcResult> test2 = () -> mockMvc.perform(delete("/api/shops/{id}", shop2Id)
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        List<ConcurrencyRunner.Result<MvcResult>> results = ConcurrencyRunner.run(List.of(test1, test2));

        // Then
        results.stream().map(ConcurrencyRunner.Result::ok).forEach(result -> assertThat(result).isTrue());
        assertThat(results).extracting(result -> result.value().getResponse().getStatus()).containsOnly(204);

        List<OutboxSnapshot> rows = outboxAssert.rows(ShopTopics.SHOP_CLOSED);
        assertThat(rows).hasSize(2);

        int count = outboxRelay.relayOnce();
        assertThat(count).isEqualTo(2);

        UUID eventId1 = rows.stream()
                .filter(r -> r.aggregateId().equals(shop1Id))
                .findFirst().orElseThrow()
                .id();
        Eventually.await(Duration.ofSeconds(10), () -> assertThat(testFixtures.processedCountOf("member.shop-closed", eventId1)).isEqualTo(1));

        UUID eventId2 = rows.stream()
                .filter(r -> r.aggregateId().equals(shop2Id))
                .findFirst().orElseThrow()
                .id();
        Eventually.await(Duration.ofSeconds(10), () -> assertThat(testFixtures.processedCountOf("member.shop-closed", eventId2)).isEqualTo(1));

        MemberSnapshot memberSnapshot = testFixtures.memberSnapshotOf(sellerId);
        assertThat(memberSnapshot.role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("같은 shop-closed 이벤트를 두 번 받아도 한 번만 처리한다.")
    void duplicateShopClosedEventIsProcessedOnce() throws Exception {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        String token = testFixtures.login("seller@test.com");
        UUID shop1Id = testFixtures.openShop(sellerId, "1111111111");
        testFixtures.changeRole(sellerId, "SELLER");
        testFixtures.closeShop(shop1Id);

        UUID eventId1 = Ids.newId();
        UUID eventId2 = Ids.newId();

        outboxAssert.clear();

        // When
        testKafka.send(ShopTopics.SHOP_CLOSED, shop1Id.toString(), eventId1, new ShopClosedEvent(shop1Id, sellerId));
        testKafka.send(ShopTopics.SHOP_CLOSED, shop1Id.toString(), eventId1, new ShopClosedEvent(shop1Id, sellerId));
        testKafka.send(ShopTopics.SHOP_CLOSED, shop1Id.toString(), eventId2, new ShopClosedEvent(shop1Id, sellerId));

        // Then
        Eventually.await(Duration.ofSeconds(10), () -> {
            assertThat(testFixtures.processedCountOf("product.shop-closed", eventId2)).isEqualTo(1);
            assertThat(testFixtures.processedCountOf("member.shop-closed", eventId2)).isEqualTo(1);
        });

        assertThat(testFixtures.processedCountOf("product.shop-closed", eventId1)).isEqualTo(1);
        assertThat(testFixtures.processedCountOf("member.shop-closed", eventId1)).isEqualTo(1);

        then(productDiscontinueService).should(times(2)).discontinueAllByShop(shop1Id);
        then(memberRoleService).should(times(2)).revokeSellerIfNoActiveShop(sellerId);
    }
}
