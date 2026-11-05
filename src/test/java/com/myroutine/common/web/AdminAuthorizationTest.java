package com.myroutine.common.web;

import com.myroutine.common.model.Ids;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AdminAuthorizationTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final OutboxAssert outboxAssert;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public AdminAuthorizationTest(MockMvc mockMvc, TestFixtures testFixtures, OutboxAssert outboxAssert, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.outboxAssert = outboxAssert;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("일반 회원은 운영 API를 호출할 수 없다. (403)")
    void nonAdminCannotCallAdminApis(CapturedOutput output) throws Exception {
        // Given
        String token = testFixtures.token("user@test.com");
        UUID x = insertDeadOutbox();
        OutboxSnapshot before = outboxAssert.outboxSnapshotOf(x);

        // When & Then
        for (MockHttpServletRequestBuilder builder : adminRequests(x)) {
            mockMvc.perform(builder.header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        assertThat(outboxAssert.outboxSnapshotOf(x)).isEqualTo(before);
        assertThat(before.status()).isEqualTo("DEAD");
        assertThat(output.getAll()).doesNotContain("admin action=");
    }

    @Test
    @DisplayName("토큰 없이는 운영 API를 호출할 수 없다. (401)")
    void anonymousCannotCallAdminApis(CapturedOutput output) throws Exception {
        // Given
        UUID x = insertDeadOutbox();
        OutboxSnapshot before = outboxAssert.outboxSnapshotOf(x);

        // When & Then
        for (MockHttpServletRequestBuilder builder : adminRequests(x)) {
            mockMvc.perform(builder)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        assertThat(outboxAssert.outboxSnapshotOf(x)).isEqualTo(before);
        assertThat(before.status()).isEqualTo("DEAD");
        assertThat(output.getAll()).doesNotContain("admin action=");
    }

    private List<MockHttpServletRequestBuilder> adminRequests(UUID outboxId) {
        UUID anyId = Ids.newId();
        return List.of(
                request(HttpMethod.GET, "/admin/api/outbox?status=DEAD"),
                request(HttpMethod.POST, "/admin/api/outbox/{id}/retry", outboxId),
                request(HttpMethod.GET, "/admin/api/dlt/test.admin-dlt.v1?limit=50"),
                request(HttpMethod.POST, "/admin/api/dlt/test.admin-dlt.v1/0/0/replay"),
                request(HttpMethod.GET, "/admin/api/payments?status=UNKNOWN"),
                request(HttpMethod.POST, "/admin/api/payments/{orderId}/reconcile", anyId),
                request(HttpMethod.GET, "/admin/api/refunds?status=FAILED"),
                request(HttpMethod.POST, "/admin/api/refunds/{id}/retry", anyId),
                request(HttpMethod.GET, "/admin/api/notifications?status=FAILED"),
                request(HttpMethod.POST, "/admin/api/notifications/{id}/retry", anyId),
                request(HttpMethod.POST, "/admin/api/settlements/run?period=2026-09"),
                request(HttpMethod.GET, "/admin/api/dlt"),
                request(HttpMethod.GET, "/admin/api/settlements?status=PAYOUT_FAILED"),
                request(HttpMethod.POST, "/admin/api/refunds/{id}/close", anyId)
                        .contentType("application/json")
                        .content("{\"reason\":\"사유\"}")
        );
    }

    private UUID insertDeadOutbox() {
        UUID x = Ids.newId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                        INSERT INTO common.outbox_event (id, aggregate_type, aggregate_id, event_type, topic, payload, status, attempts, next_attempt_at, last_error, created_at, updated_at)
                        VALUES (?, 'TEST', ?, 'TestEvent', 'test.admin-outbox.v1', '{"hello":"admin"}'::jsonb, 'DEAD', 10, ?, 'kafka timeout', ?, ?)
                        """,
                x, Ids.newId(), now, now, now);
        return x;
    }
}
