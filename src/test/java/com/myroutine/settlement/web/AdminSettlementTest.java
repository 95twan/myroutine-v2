package com.myroutine.settlement.web;

import com.myroutine.common.config.BusinessProperties;
import com.myroutine.support.FakePayoutGateway;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminSettlementTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final FakePayoutGateway fakePayoutGateway;
    private final Clock clock;
    private final BusinessProperties businessProperties;

    @Autowired
    public AdminSettlementTest(MockMvc mockMvc, TestFixtures testFixtures, FakePayoutGateway fakePayoutGateway, Clock clock, BusinessProperties businessProperties) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.fakePayoutGateway = fakePayoutGateway;
        this.clock = clock;
        this.businessProperties = businessProperties;
    }

    @Test
    @DisplayName("지급 실패 정산 목록을 조회한다.")
    void listPayoutFailedSettlements() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopA = testFixtures.openShop(sellerId, "1111111111");
        testFixtures.insertSettlementItems(List.of(shopA), 2, 7500, Instant.parse("2026-09-15T03:00:00Z"));
        fakePayoutGateway.failNext(1);
        mockMvc.perform(post("/admin/api/settlements/run")
                        .param("period", "2026-09")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payoutFailed").value(1));

        // When & Then
        mockMvc.perform(get("/admin/api/settlements")
                        .param("status", "PAYOUT_FAILED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].shopId").value(shopA.toString()))
                .andExpect(jsonPath("$.items[0].shopName").value("shop"))
                .andExpect(jsonPath("$.items[0].periodStart").value("2026-09-01"))
                .andExpect(jsonPath("$.items[0].periodEnd").value("2026-09-30"))
                .andExpect(jsonPath("$.items[0].netAmount").value(14250))
                .andExpect(jsonPath("$.items[0].payoutAttempts").value(1))
                .andExpect(jsonPath("$.items[0].lastError").value(notNullValue()));
    }

    @Test
    @DisplayName("폐업한 가게의 지급 실패 정산도 가게 이름과 함께 조회한다.")
    void listPayoutFailedSettlementsIncludesClosedShopName() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopA = testFixtures.openShop(sellerId, "1111111111");
        testFixtures.insertSettlementItems(List.of(shopA), 2, 7500, Instant.parse("2026-09-15T03:00:00Z"));
        fakePayoutGateway.failNext(1);
        mockMvc.perform(post("/admin/api/settlements/run")
                        .param("period", "2026-09")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        testFixtures.closeShop(shopA);

        // When & Then
        mockMvc.perform(get("/admin/api/settlements")
                        .param("status", "PAYOUT_FAILED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].shopName").value("shop"));
    }

    @Test
    @DisplayName("같은 달로 정산을 다시 실행하면 새 정산 없이 지급 실패 건만 다시 지급한다.")
    void runSettlementAgainOnlyRepays() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopA = testFixtures.openShop(sellerId, "1111111111");
        testFixtures.insertSettlementItems(List.of(shopA), 2, 7500, Instant.parse("2026-09-15T03:00:00Z"));
        fakePayoutGateway.failNext(1);
        mockMvc.perform(post("/admin/api/settlements/run")
                        .param("period", "2026-09")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payoutFailed").value(1));

        // When & Then
        mockMvc.perform(post("/admin/api/settlements/run")
                        .param("period", "2026-09")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settledShops").value(0))
                .andExpect(jsonPath("$.paid").value(1))
                .andExpect(jsonPath("$.payoutFailed").value(0));

        assertThat(testFixtures.settlementSnapshotOf(shopA).status()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("끝나지 않은 달의 정산 실행을 실패한다. (이번 달)")
    void runSettlementForUnfinishedMonthFails() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID sellerId = testFixtures.signup("seller@test.com");
        UUID shopA = testFixtures.openShop(sellerId, "1111111111");
        testFixtures.insertSettlementItem(shopA, 7500, clock.instant());
        YearMonth thisMonth = YearMonth.now(clock.withZone(businessProperties.businessZone()));

        // When & Then
        mockMvc.perform(post("/admin/api/settlements/run")
                        .param("period", thisMonth.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details.period").value(notNullValue()));

        mockMvc.perform(post("/admin/api/settlements/run")
                        .param("period", thisMonth.plusMonths(1).toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details.period").value(notNullValue()));

        assertThat(testFixtures.countOf("settlement.settlement")).isEqualTo(0);
        assertThat(testFixtures.unsettledItemCount()).isEqualTo(1);
    }
}
