package com.myroutine.common.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.common.model.Ids;
import com.myroutine.support.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AdminDltTest extends IntegrationTestSupport {

    private static final String TOPIC = "test.admin-dlt.v1";

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final TestKafka testKafka;
    private final AdminDltListener listener;

    @Autowired
    public AdminDltTest(MockMvc mockMvc, TestFixtures testFixtures, TestKafka testKafka, AdminDltListener listener) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.testKafka = testKafka;
        this.listener = listener;
    }

    @BeforeEach
    void resetListener() {
        listener.failAlways();
        listener.resetCounts();
    }

    @Test
    @DisplayName("DLT 기록 목록을 조회한다.")
    void listDltRecords() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID k = Ids.newId();
        String envelope = envelopeJson(Ids.newId(), k);

        // When
        testKafka.sendRaw(TOPIC, k.toString(), envelope);

        // Then
        Eventually.await(Duration.ofSeconds(20), () -> assertThat(recordsOf(token, k)).hasSize(1));

        Map<String, Object> record = recordsOf(token, k).getFirst();
        assertThat(record.get("value")).isEqualTo(envelope);
        assertThat((String) record.get("exception")).contains("always-fail");
        assertThat(record.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("DLT 기록을 replay하면 원래 consumer가 처리에 성공한다.")
    void replayDltRecord(CapturedOutput output) throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID k = Ids.newId();
        testKafka.sendRaw(TOPIC, k.toString(), envelopeJson(Ids.newId(), k));
        Eventually.await(Duration.ofSeconds(20), () -> assertThat(recordsOf(token, k)).hasSize(1));
        Map<String, Object> record = recordsOf(token, k).getFirst();
        int partition = (int) record.get("partition");
        int offset = ((Number) record.get("offset")).intValue();

        listener.resetCounts();
        listener.succeed();

        // When & Then
        mockMvc.perform(post("/admin/api/dlt/{topic}/{partition}/{offset}/replay", TOPIC, partition, offset)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(output.getAll()).contains("target=" + TOPIC + "/" + partition + "/" + offset + " by=" + adminId);
        Eventually.await(Duration.ofSeconds(10), () -> {
            assertThat(listener.received()).isEqualTo(1);
            assertThat(listener.processed()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("같은 DLT 기록을 두 번 replay해도 처리는 한 번만 된다.")
    void replayDltRecordTwiceProcessesOnce() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID k = Ids.newId();
        testKafka.sendRaw(TOPIC, k.toString(), envelopeJson(Ids.newId(), k));
        Eventually.await(Duration.ofSeconds(20), () -> assertThat(recordsOf(token, k)).hasSize(1));
        Map<String, Object> record = recordsOf(token, k).getFirst();
        int partition = (int) record.get("partition");
        int offset = ((Number) record.get("offset")).intValue();

        listener.resetCounts();
        listener.succeed();
        mockMvc.perform(post("/admin/api/dlt/{topic}/{partition}/{offset}/replay", TOPIC, partition, offset)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        Eventually.await(Duration.ofSeconds(10), () -> assertThat(listener.processed()).isEqualTo(1));

        // When & Then
        mockMvc.perform(post("/admin/api/dlt/{topic}/{partition}/{offset}/replay", TOPIC, partition, offset)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        Eventually.await(Duration.ofSeconds(10), () -> assertThat(listener.received()).isEqualTo(2));
        Thread.sleep(1000);
        assertThat(listener.processed()).isEqualTo(1);
        assertThat(recordsOf(token, k)).hasSize(1);
    }

    @Test
    @DisplayName("DLT replay를 실패한다. (없는 offset)")
    void replayUnknownOffsetFails() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        UUID k = Ids.newId();
        testKafka.sendRaw(TOPIC, k.toString(), envelopeJson(Ids.newId(), k));
        Eventually.await(Duration.ofSeconds(20), () -> assertThat(recordsOf(token, k)).hasSize(1));
        int partition = (int) recordsOf(token, k).getFirst().get("partition");
        int receivedBefore = listener.received();

        // When & Then
        mockMvc.perform(post("/admin/api/dlt/{topic}/{partition}/{offset}/replay", TOPIC, partition, 9999)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        Thread.sleep(1000);
        assertThat(listener.received()).isEqualTo(receivedBefore);
    }

    @Test
    @DisplayName("DLT 토픽 목록을 조회한다.")
    void listDltTopics() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        long before = recordCountOf(token);

        // When
        UUID k = Ids.newId();
        testKafka.sendRaw(TOPIC, k.toString(), envelopeJson(Ids.newId(), k));

        // Then
        Eventually.await(Duration.ofSeconds(20), () -> assertThat(recordCountOf(token)).isEqualTo(before + 1));

        String body = mockMvc.perform(get("/admin/api/dlt")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> topics = JsonPath.read(body, "$[*].topic");
        assertThat(topics).noneMatch(topic -> topic.endsWith(".dlt"));
    }

    @Test
    @DisplayName("DLT 조회와 replay를 실패한다. (없는 토픽)")
    void unknownDltTopicFails() throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        // When & Then
        mockMvc.perform(get("/admin/api/dlt/{topic}", "test.no-such.v1")
                        .param("limit", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(post("/admin/api/dlt/{topic}/0/0/replay", "test.no-such.v1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    @DisplayName("DLT 기록 목록 조회를 실패한다. (limit 범위 밖)")
    void listDltRecordsWithInvalidLimitFails(int limit) throws Exception {
        // Given
        UUID adminId = testFixtures.signup("admin@test.com");
        testFixtures.changeRole(adminId, "ADMIN");
        String token = testFixtures.login("admin@test.com");

        // When & Then
        mockMvc.perform(get("/admin/api/dlt/{topic}", TOPIC)
                        .param("limit", String.valueOf(limit))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private List<Map<String, Object>> recordsOf(String token, UUID key) throws Exception {
        String body = mockMvc.perform(get("/admin/api/dlt/{topic}", TOPIC)
                        .param("limit", "50")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[?(@.key == '" + key + "')]");
    }

    private long recordCountOf(String token) throws Exception {
        String body = mockMvc.perform(get("/admin/api/dlt")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> counts = JsonPath.read(body, "$[?(@.topic == '" + TOPIC + "')].recordCount");
        return counts.isEmpty() ? 0 : counts.getFirst().longValue();
    }

    private String envelopeJson(UUID eventId, UUID aggregateId) {
        return "{\"eventId\":\"%s\",\"eventType\":\"TestEvent\",\"occurredAt\":\"2026-10-18T00:00:00Z\",\"aggregateId\":\"%s\",\"traceId\":\"t\",\"payload\":{}}"
                .formatted(eventId, aggregateId);
    }
}
