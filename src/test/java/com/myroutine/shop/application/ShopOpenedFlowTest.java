package com.myroutine.shop.application;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.error.BusinessException;
import com.myroutine.common.model.Ids;
import com.myroutine.common.outbox.OutboxRelay;
import com.myroutine.shop.api.event.ShopTopics;
import com.myroutine.shop.domain.ShopErrorCode;
import com.myroutine.shop.web.OpenShopRequest;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ShopOpenedFlowTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final OutboxAssert outboxAssert;
    private final TestFixtures testFixtures;
    private final OutboxRelay outboxRelay;
    private final TestKafka testKafka;

    @Autowired
    public ShopOpenedFlowTest(MockMvc mockMvc, ObjectMapper objectMapper, OutboxAssert outboxAssert, TestFixtures testFixtures, OutboxRelay outboxRelay, TestKafka testKafka) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.outboxAssert = outboxAssert;
        this.testFixtures = testFixtures;
        this.outboxRelay = outboxRelay;
        this.testKafka = testKafka;
    }

    @Test
    @DisplayName("가게 개설이 SELLER 역할 부여로 이어진다.")
    void openShopGrantsSellerRole() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");

        // When
        outboxAssert.clear();
        UUID shopId = testFixtures.openShop(sellerId, "1111111111");

        // Then
        List<OutboxSnapshot> rows = outboxAssert.rows(ShopTopics.SHOP_OPENED);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).aggregateType()).isEqualTo("SHOP");
        assertThat(rows.get(0).aggregateId()).isEqualTo(shopId);
        assertThat(rows.get(0).payload().get("shopId").asString()).isEqualTo(shopId.toString());
        assertThat(rows.get(0).payload().get("memberId").asString()).isEqualTo(sellerId.toString());

        int count = outboxRelay.relayOnce();

        assertThat(count).isEqualTo(1);

        OutboxSnapshot snapshot = outboxAssert.outboxSnapshotOf(rows.get(0).id());
        assertThat(snapshot.status()).isEqualTo("SENT");

        List<ReceivedMessage> messages = testKafka.messages(ShopTopics.SHOP_OPENED, shopId.toString());
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).envelope().eventId()).isEqualTo(rows.get(0).id());

        Eventually.await(Duration.ofSeconds(5), () -> assertThat(testFixtures.memberSnapshotOf(sellerId).role()).isEqualTo("SELLER"));
    }

    @Test
    @DisplayName("가게 개설을 실패한다. (사업자번호 중복, outbox 행 없음)")
    void openShopWithDuplicatedBusinessNumberLeavesNoEvent() {
        // Given
        UUID sellerId = testFixtures.signup("seller@test.com");
        testFixtures.openShop(sellerId, "1111111111");

        // When
        outboxAssert.clear();
        assertThatThrownBy(() -> testFixtures.openShop(sellerId, "1111111111"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ShopErrorCode.SHOP_BUSINESS_NUMBER_DUPLICATED));

        // Then
        assertThat(testFixtures.countOf("common.outbox_event")).isEqualTo(0);
        assertThat(testFixtures.countOf("shop.shop")).isEqualTo(1);
    }

    @Test
    @DisplayName("요청의 traceId가 outbox 행과 Kafka 봉투까지 이어진다.")
    void openShopPropagatesTraceId() throws Exception {
        // Given
        String token = testFixtures.token("seller@test.com");

        OpenShopRequest request = new OpenShopRequest(
                "shop",
                "1111111111",
                "shop@test.com",
                "010-1111-1111",
                "address"
        );

        // When
        outboxAssert.clear();
        MvcResult result = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // Then
        String traceId = result.getResponse().getHeader("X-Request-Id");
        String shopId = JsonPath.read(result.getResponse().getContentAsString(), "$.shopId");

        List<OutboxSnapshot> rows = outboxAssert.rows(ShopTopics.SHOP_OPENED);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).traceId()).isEqualTo(traceId);
        assertThat(rows.get(0).aggregateId()).isEqualTo(UUID.fromString(shopId));

        int count = outboxRelay.relayOnce();
        assertThat(count).isEqualTo(1);

        List<ReceivedMessage> messages = testKafka.messages(ShopTopics.SHOP_OPENED, shopId);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).envelope().traceId()).isEqualTo(traceId);
    }
}
