package com.myroutine.common.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.model.Ids;
import com.myroutine.common.outbox.OutboxRelay;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AdminOutboxTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final OutboxAssert outboxAssert;
    private final OutboxRelay outboxRelay;
    private final TestKafka testKafka;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public AdminOutboxTest(MockMvc mockMvc, TestFixtures testFixtures, OutboxAssert outboxAssert, OutboxRelay outboxRelay, TestKafka testKafka, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.outboxAssert = outboxAssert;
        this.outboxRelay = outboxRelay;
        this.testKafka = testKafka;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("DEAD 상태의 outbox 목록을 조회한다.")
    void listDeadOutboxEvents() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID x = Ids.newId();
        UUID k = Ids.newId();
        insertOutbox(x, k, "DEAD", Instant.now().minus(1, ChronoUnit.HOURS));
        insertOutbox(Ids.newId(), Ids.newId(), "SENT", Instant.now().minus(1, ChronoUnit.HOURS));

        // When & Then
        mockMvc.perform(get("/admin/api/outbox")
                        .param("status", "DEAD")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(x.toString()))
                .andExpect(jsonPath("$.items[0].topic").value("test.admin-outbox.v1"))
                .andExpect(jsonPath("$.items[0].aggregateId").value(k.toString()))
                .andExpect(jsonPath("$.items[0].attempts").value(10))
                .andExpect(jsonPath("$.items[0].lastError").value("kafka timeout"))
                .andExpect(jsonPath("$.items[0].createdAt").value(notNullValue()));
    }

    @Test
    @DisplayName("outbox 목록을 커서로 나눠 오래된 순으로 조회한다.")
    void listDeadOutboxEventsByCursor() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        insertOutbox(Ids.newId(), Ids.newId(), "DEAD", Instant.now().minus(3, ChronoUnit.HOURS));
        insertOutbox(Ids.newId(), Ids.newId(), "DEAD", Instant.now().minus(2, ChronoUnit.HOURS));
        insertOutbox(Ids.newId(), Ids.newId(), "DEAD", Instant.now().minus(1, ChronoUnit.HOURS));

        // When
        MvcResult first = mockMvc.perform(get("/admin/api/outbox")
                        .param("status", "DEAD")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.nextCursor").value(notNullValue()))
                .andReturn();
        String firstBody = first.getResponse().getContentAsString();
        String cursor = JsonPath.read(firstBody, "$.nextCursor");

        MvcResult second = mockMvc.perform(get("/admin/api/outbox")
                        .param("status", "DEAD")
                        .param("size", "2")
                        .param("cursor", cursor)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andReturn();
        String secondBody = second.getResponse().getContentAsString();

        // Then
        List<Instant> createdAts = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (String body : List.of(firstBody, secondBody)) {
            List<String> pageCreatedAts = JsonPath.read(body, "$.items[*].createdAt");
            pageCreatedAts.forEach(createdAt -> createdAts.add(Instant.parse(createdAt)));
            List<String> pageIds = JsonPath.read(body, "$.items[*].id");
            ids.addAll(pageIds);
        }
        assertThat(createdAts).isSorted();
        assertThat(ids).hasSize(3);
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("DEAD 상태의 outbox 행을 재시도하면 READY로 돌아간다.")
    void retryDeadOutboxEvent(CapturedOutput output) throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID x = Ids.newId();
        insertOutbox(x, Ids.newId(), "DEAD", Instant.now().minus(1, ChronoUnit.HOURS));

        // When & Then
        mockMvc.perform(post("/admin/api/outbox/{id}/retry", x)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        OutboxSnapshot snapshot = outboxAssert.outboxSnapshotOf(x);
        assertThat(snapshot.status()).isEqualTo("READY");
        assertThat(snapshot.attempts()).isEqualTo(0);
        assertThat(output.getAll()).contains("target=" + x + " by=" + adminId);
    }

    @Test
    @DisplayName("같은 outbox 행의 재시도를 한 번 더 요청하면 실패한다. (DEAD가 아님)")
    void retryOutboxEventTwiceFails() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID x = Ids.newId();
        UUID k = Ids.newId();
        insertOutbox(x, k, "DEAD", Instant.now().minus(1, ChronoUnit.HOURS));
        mockMvc.perform(post("/admin/api/outbox/{id}/retry", x)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        outboxRelay.relayOnce();
        OutboxSnapshot before = outboxAssert.outboxSnapshotOf(x);

        // When & Then
        mockMvc.perform(post("/admin/api/outbox/{id}/retry", x)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(outboxAssert.outboxSnapshotOf(x)).isEqualTo(before);

        assertThat(outboxRelay.relayOnce()).isEqualTo(0);
        assertThat(testKafka.messages("test.admin-outbox.v1", k.toString())).hasSize(1);
    }

    @Test
    @DisplayName("없는 outbox 행의 재시도를 실패한다. (존재하지 않는 id)")
    void retryUnknownOutboxEventFails() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        // When & Then
        mockMvc.perform(post("/admin/api/outbox/{id}/retry", Ids.newId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "NOPE")
    @DisplayName("outbox 목록 조회를 실패한다. (status가 없거나 올바르지 않음)")
    void listOutboxEventsWithInvalidStatusFails(String status) throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        // When & Then
        var request = get("/admin/api/outbox").header("Authorization", "Bearer " + token);
        if (status != null) {
            request.param("status", status);
        }
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private void insertOutbox(UUID id, UUID aggregateId, String status, Instant createdAt) {
        Timestamp createdAtTimestamp = Timestamp.from(createdAt);
        jdbcTemplate.update("""
                        INSERT INTO common.outbox_event (id, aggregate_type, aggregate_id, event_type, topic, payload, status, attempts, next_attempt_at, sent_at, last_error, created_at, updated_at)
                        VALUES (?, 'TEST', ?, 'TestEvent', 'test.admin-outbox.v1', '{"hello":"admin"}'::jsonb, ?, 10, ?, ?, 'kafka timeout', ?, ?)
                        """,
                id, aggregateId, status, createdAtTimestamp, "SENT".equals(status) ? Timestamp.from(Instant.now()) : null, createdAtTimestamp, createdAtTimestamp);
    }
}
