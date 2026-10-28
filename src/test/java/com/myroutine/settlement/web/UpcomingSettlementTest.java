package com.myroutine.settlement.web;

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
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UpcomingSettlementTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final SettlementService settlementService;

    @Autowired
    public UpcomingSettlementTest(MockMvc mockMvc, TestFixtures testFixtures, SettlementService settlementService) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.settlementService = settlementService;
    }

    @Test
    @DisplayName("가게 주인이 아직 정산되지 않은 구매확정 품목의 수와 합계를 조회한다.")
    void getUpcoming() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        String token = testFixtures.login("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");

        UUID seller2Id = testFixtures.signup("seller2@test.com");
        UUID shop2Id = testFixtures.openShop(seller2Id, "2222222222");

        testFixtures.insertSettlementItem(shop1Id, 1000, Instant.parse("2026-08-15T03:00:00Z"));
        settlementService.run(YearMonth.of(2026, 8));

        testFixtures.insertSettlementItem(shop1Id, 7500, Instant.parse("2026-09-15T03:00:00Z"));
        testFixtures.insertSettlementItem(shop1Id, 3000, Instant.parse("2026-10-02T03:00:00Z"));
        testFixtures.insertSettlementItem(shop2Id, 5000, Instant.parse("2026-09-01T03:00:00Z"));

        // When & Then
        mockMvc.perform(get("/api/shops/{shopA}/settlements/upcoming", shop1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.grossAmount").value(10500))
                .andExpect(jsonPath("$.firstConfirmedAt").value("2026-09-15T03:00:00Z"));
    }

    @Test
    @DisplayName("정산 예정 품목이 없으면 0을 조회한다.")
    void getUpcomingWithoutItems() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        String token = testFixtures.login("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");

        // When & Then
        mockMvc.perform(get("/api/shops/{shopA}/settlements/upcoming", shop1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(0))
                .andExpect(jsonPath("$.grossAmount").value(0))
                .andExpect(jsonPath("$.firstConfirmedAt").value(nullValue()));
    }

    @Test
    @DisplayName("폐업한 가게의 정산 예정을 조회한다.")
    void getUpcomingOfClosedShop() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        String token = testFixtures.login("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");

        testFixtures.insertSettlementItem(shop1Id, 1000, Instant.parse("2026-09-15T03:00:00Z"));
        testFixtures.closeShop(shop1Id);

        // When & Then
        mockMvc.perform(get("/api/shops/{shopA}/settlements/upcoming", shop1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.grossAmount").value(1000));
    }

    @Test
    @DisplayName("다른 가게 판매자가 내 가게의 정산 예정을 조회하면 실패한다. (403 FORBIDDEN)")
    void getUpcomingOfOtherShopFails() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111");

        UUID seller2Id = testFixtures.signup("seller2@test.com");
        String token = testFixtures.login("seller2@test.com");

        testFixtures.insertSettlementItem(shop1Id, 1000, Instant.parse("2026-09-15T03:00:00Z"));

        // When & Then
        mockMvc.perform(get("/api/shops/{shopA}/settlements/upcoming", shop1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
