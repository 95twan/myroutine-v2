package com.myroutine.common;

import com.myroutine.common.inbox.InboxGuard;
import com.myroutine.common.job.JobRunner;
import com.myroutine.common.model.Ids;
import com.myroutine.common.outbox.EventEnvelope;
import com.myroutine.common.outbox.OutboxRelay;
import com.myroutine.notification.application.NotificationDispatcher;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.OutboxAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
@AutoConfigureMockMvc
class LogFlowTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final InboxGuard inboxGuard;
    private final ObjectMapper objectMapper;
    private final OutboxRelay outboxRelay;
    private final OutboxAssert outboxAssert;
    private final NotificationDispatcher notificationDispatcher;
    private final JobRunner jobRunner;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public LogFlowTest(
            MockMvc mockMvc,
            InboxGuard inboxGuard,
            ObjectMapper objectMapper,
            OutboxRelay outboxRelay,
            OutboxAssert outboxAssert,
            NotificationDispatcher notificationDispatcher,
            JobRunner jobRunner,
            JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.inboxGuard = inboxGuard;
        this.objectMapper = objectMapper;
        this.outboxRelay = outboxRelay;
        this.outboxAssert = outboxAssert;
        this.notificationDispatcher = notificationDispatcher;
        this.jobRunner = jobRunner;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("HTTP 요청마다 메서드·경로·상태를 로그로 남기고 쿼리 문자열은 남기지 않는다.")
    void httpRequestIsLoggedWithoutQueryString(CapturedOutput output) throws Exception {
        // Given

        // When & Then
        mockMvc.perform(get("/api/products")
                        .param("size", "1"))
                .andExpect(status().isOk());

        assertThat(output.getOut()).contains("http GET /api/products status=200");
        assertThat(output.getOut()).doesNotContain("size=1");
    }

    @Test
    @DisplayName("이벤트를 처리하면 consumer와 이벤트 타입을 로그로 남긴다.")
    void inboxGuardLogsHandledEvent(CapturedOutput output) {
        // Given
        EventEnvelope envelope = new EventEnvelope(Ids.newId(), "TestEvent", Instant.now(), Ids.newId(), "trace-log-flow", objectMapper.createObjectNode());

        // When
        inboxGuard.runOnce("test.log-flow", envelope, () -> {
        });

        // Then
        assertThat(output.getAll()).contains("event handled consumer=test.log-flow type=TestEvent");
    }

    @Test
    @DisplayName("중복 이벤트는 처리 로그를 남기지 않는다.")
    void inboxGuardDoesNotLogDuplicateEvent(CapturedOutput output) {
        // Given
        EventEnvelope envelope = new EventEnvelope(Ids.newId(), "TestEvent", Instant.now(), Ids.newId(), "trace-log-flow", objectMapper.createObjectNode());
        inboxGuard.runOnce("test.log-flow", envelope, () -> {
        });

        // When
        inboxGuard.runOnce("test.log-flow", envelope, () -> {
        });

        // Then
        assertThat(StringUtils.countOccurrencesOf(output.getAll(), "event handled consumer=test.log-flow")).isEqualTo(1);
        assertThat(output.getAll()).contains("duplicate event skipped");
    }

    @Test
    @DisplayName("릴레이가 전송에 성공하면 eventId와 원래 traceId를 로그로 남긴다.")
    void outboxRelayLogsSentEvent(CapturedOutput output) {
        // Given
        outboxAssert.clear();
        UUID outboxId = outboxAssert.publishReady("test.outbox-relay.v1", Ids.newId());
        jdbcTemplate.update("UPDATE common.outbox_event SET trace_id = ? WHERE id = ?", "t-log", outboxId);

        // When
        outboxRelay.relayOnce();

        // Then
        assertThat(output.getAll()).contains("outbox sent eventId=" + outboxId);
        assertThat(output.getAll()).contains("originTraceId=t-log");
    }

    @Test
    @DisplayName("Kafka가 멈춰 전송에 실패하면 릴레이가 eventId와 함께 경고 로그를 남긴다.")
    void outboxRelayLogsFailedEvent(CapturedOutput output) {
        // Given
        outboxAssert.clear();
        UUID outboxId = outboxAssert.publishReady("test.outbox-relay.v1", Ids.newId());

        // When
        kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
        try {
            outboxRelay.relayOnce();
        } finally {
            kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
        }

        // Then
        assertThat(output.getAll()).contains("Failed to send outbox event. eventId=" + outboxId);
    }

    @Test
    @DisplayName("알림을 발송하면 notificationId를 로그로 남긴다.")
    void notificationDispatcherLogsSentNotification(CapturedOutput output) {
        // Given
        UUID notificationId = Ids.newId();
        jdbcTemplate.update("""
                        INSERT INTO notification.notification
                            (id, member_id, type, channel, recipient, title, body, dedup_key, occurred_at, status, attempts, next_attempt_at, version, created_at, updated_at)
                        VALUES (?, ?, 'ORDER_PAID', 'EMAIL', 'buyer@test.com', 't', 'b', ?, now(), 'PENDING', 0, now() - interval '1 hour', 0, now(), now())
                        """,
                notificationId, Ids.newId(), "log-flow-" + notificationId);

        // When
        notificationDispatcher.dispatchBatch();

        // Then
        assertThat(output.getAll()).contains("notification sent notificationId=" + notificationId);
    }

    @Test
    @DisplayName("잡은 처리 건수가 있을 때만 로그를 남긴다.")
    void jobRunnerLogsOnlyWhenSomethingProcessed(CapturedOutput output) {
        // Given

        // When
        jobRunner.run("test-log-zero", () -> 0);
        jobRunner.run("test-log-three", () -> 3);

        // Then
        assertThat(output.getAll()).doesNotContain("job test-log-zero processed");
        assertThat(output.getAll()).contains("job test-log-three processed 3");
    }
}
