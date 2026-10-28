package com.myroutine.settlement.web;

import com.myroutine.common.model.Ids;
import com.myroutine.settlement.application.SettlementService;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SettlementControllerTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final SettlementService settlementService;

    @Autowired
    public SettlementControllerTest(MockMvc mockMvc, TestFixtures testFixtures, SettlementService settlementService) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.settlementService = settlementService;
    }

    @Test
    @DisplayName("가게 주인이 정산 목록을 최신 기간 먼저 조회한다.")
    void getSettlements() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");

        UUID seller2Id = testFixtures.signup("seller2@test.com");
        UUID shop2Id = testFixtures.openShop(seller2Id, "2222222222");

        Instant confirmedAt = Instant.parse("2026-09-15T03:00:00Z");
        testFixtures.insertSettlementItems(List.of(shop1Id), 2, 7500, confirmedAt);
        testFixtures.insertSettlementItems(List.of(shop2Id), 2, 7500, confirmedAt);

        settlementService.run(YearMonth.of(2026, 9));

        UUID settlement1Id = testFixtures.settlementSnapshotOf(shop1Id).id();

        String token1 = testFixtures.login("seller1@test.com");

        // When & Then
        mockMvc.perform(get("/api/shops/{shopId}/settlements", shop1Id)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.items[0].id").value(settlement1Id.toString()))
                .andExpect(jsonPath("$.items[0].periodStart").value("2026-09-01"))
                .andExpect(jsonPath("$.items[0].periodEnd").value("2026-09-30"))
                .andExpect(jsonPath("$.items[0].grossAmount").value(15000))
                .andExpect(jsonPath("$.items[0].feeRateBp").value(500))
                .andExpect(jsonPath("$.items[0].feeAmount").value(750))
                .andExpect(jsonPath("$.items[0].netAmount").value(14250))
                .andExpect(jsonPath("$.items[0].itemCount").value(2))
                .andExpect(jsonPath("$.items[0].status").value("PAID"))
                .andExpect(jsonPath("$.items[0].paidAt").value(notNullValue()));
    }

    @Test
    @DisplayName("가게 주인이 정산 상세와 품목 목록을 조회한다.")
    void getSettlement() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");

        UUID seller2Id = testFixtures.signup("seller2@test.com");
        UUID shop2Id = testFixtures.openShop(seller2Id, "2222222222");

        Instant confirmedAt = Instant.parse("2026-09-15T03:00:00Z");
        testFixtures.insertSettlementItems(List.of(shop1Id), 2, 7500, confirmedAt);
        testFixtures.insertSettlementItems(List.of(shop2Id), 2, 7500, confirmedAt);

        settlementService.run(YearMonth.of(2026, 9));

        UUID settlement1Id = testFixtures.settlementSnapshotOf(shop1Id).id();

        String token1 = testFixtures.login("seller1@test.com");

        // When & Then
        mockMvc.perform(get("/api/shops/{shopId}/settlements/{id}", shop1Id, settlement1Id)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.id").value(settlement1Id.toString()))
                .andExpect(jsonPath("$.settlement.netAmount").value(14250))
                .andExpect(jsonPath("$.items.items", hasSize(2)))
                .andExpect(jsonPath("$.items.items[0].orderLineId").exists())
                .andExpect(jsonPath("$.items.items[1].orderLineId").exists())
                .andExpect(jsonPath("$.items.items[0].orderId").exists())
                .andExpect(jsonPath("$.items.items[1].orderId").exists())
                .andExpect(jsonPath("$.items.items[0].productId").exists())
                .andExpect(jsonPath("$.items.items[1].productId").exists())
                .andExpect(jsonPath("$.items.items[0].confirmedAt").exists())
                .andExpect(jsonPath("$.items.items[1].confirmedAt").exists())
                .andExpect(jsonPath("$.items.items[0].amount").value(7500))
                .andExpect(jsonPath("$.items.items[1].amount").value(7500));
    }

    @Test
    @DisplayName("다른 가게 판매자가 내 가게의 정산을 조회하면 실패한다. (403 FORBIDDEN)")
    void getSettlementsOfOtherShopFails() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");

        UUID seller2Id = testFixtures.signup("seller2@test.com");
        UUID shop2Id = testFixtures.openShop(seller2Id, "2222222222");

        Instant confirmedAt = Instant.parse("2026-09-15T03:00:00Z");
        testFixtures.insertSettlementItems(List.of(shop1Id), 2, 7500, confirmedAt);
        testFixtures.insertSettlementItems(List.of(shop2Id), 2, 7500, confirmedAt);

        settlementService.run(YearMonth.of(2026, 9));

        UUID settlement1Id = testFixtures.settlementSnapshotOf(shop1Id).id();

        String token2 = testFixtures.login("seller2@test.com");

        // When & Then
        mockMvc.perform(get("/api/shops/{shopId}/settlements", shop1Id)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/shops/{shopId}/settlements/{id}", shop1Id, settlement1Id)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("자기 가게 경로로 남의 정산 ID를 조회하면 실패한다. (404 SETTLEMENT_NOT_FOUND)")
    void getOtherShopSettlementFails() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");

        UUID seller2Id = testFixtures.signup("seller2@test.com");
        UUID shop2Id = testFixtures.openShop(seller2Id, "2222222222");

        Instant confirmedAt = Instant.parse("2026-09-15T03:00:00Z");
        testFixtures.insertSettlementItems(List.of(shop1Id), 2, 7500, confirmedAt);
        testFixtures.insertSettlementItems(List.of(shop2Id), 2, 7500, confirmedAt);

        settlementService.run(YearMonth.of(2026, 9));

        UUID settlement2Id = testFixtures.settlementSnapshotOf(shop2Id).id();

        String token1 = testFixtures.login("seller1@test.com");

        // When & Then
        mockMvc.perform(get("/api/shops/{shopId}/settlements/{id}", shop1Id, settlement2Id)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("일반 회원이 정산을 수동 실행하면 실패한다. (403 FORBIDDEN)")
    void runByNonAdminFails() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");

        Instant confirmedAt = Instant.parse("2026-09-15T03:00:00Z");
        testFixtures.insertSettlementItems(List.of(shop1Id), 2, 7500, confirmedAt);

        String token = testFixtures.token("user@test.com");

        // When & Then
        mockMvc.perform(post("/admin/api/settlements/run")
                        .header("Authorization", "Bearer " + token)
                        .param("period", "2026-09"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(testFixtures.unsettledItemCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("관리자가 정산을 수동 실행한다.")
    void runByAdmin() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");

        Instant confirmedAt = Instant.parse("2026-09-15T03:00:00Z");
        testFixtures.insertSettlementItems(List.of(shop1Id), 2, 7500, confirmedAt);

        UUID adminId = testFixtures.signup("admin@test.com");
        String oldToken = testFixtures.login("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        // When & Then
        mockMvc.perform(post("/admin/api/settlements/run")
                        .header("Authorization", "Bearer " + token)
                        .param("period", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settledShops").value(1))
                .andExpect(jsonPath("$.paid").value(1));

        assertThat(testFixtures.countOf("settlement.settlement")).isEqualTo(1);
        assertThat(testFixtures.settlementSnapshotOf(shop1Id).status()).isEqualTo("PAID");

        mockMvc.perform(post("/admin/api/settlements/run")
                        .header("Authorization", "Bearer " + oldToken)
                        .param("period", "2026-09"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("정산 품목을 가게주문 ID·상품 이름·수량과 함께 조회한다.")
    void getSettlementItemsWithOrderLineFields() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        String token = testFixtures.login("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");

        UUID shopOrderId = Ids.newId();
        Instant confirmedAt = Instant.parse("2026-09-15T03:00:00Z");
        testFixtures.insertSettlementItem(shop1Id, 7500, confirmedAt, shopOrderId, "상품A", 3);

        settlementService.run(YearMonth.of(2026, 9));

        UUID settlementA = testFixtures.settlementSnapshotOf(shop1Id, YearMonth.of(2026, 9)).id();

        // When & Then
        mockMvc.perform(get("/api/shops/{shopId}/settlements/{settlementId}", shop1Id, settlementA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.items[0].shopOrderId").value(shopOrderId.toString()))
                .andExpect(jsonPath("$.items.items[0].productName").value("상품A"))
                .andExpect(jsonPath("$.items.items[0].quantity").value(3));
    }
}
