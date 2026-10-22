package com.myroutine.common.outbox;

import com.myroutine.common.model.Ids;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRelayTest extends IntegrationTestSupport {
    private final OutboxRelay outboxRelay;
    private final TestFixtures testFixtures;
    private final OutboxAssert outboxAssert;
    private final TestKafka testKafka;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public OutboxRelayTest(OutboxRelay outboxRelay, TestFixtures testFixtures, OutboxAssert outboxAssert, TestKafka testKafka, JdbcTemplate jdbcTemplate) {
        this.testKafka = testKafka;
        this.outboxRelay = outboxRelay;
        this.testFixtures = testFixtures;
        this.outboxAssert = outboxAssert;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("READY 행 3건을 모두 발행하고 SENT로 바꾼다.")
    void relayOnceSendsAllReadyRows() {
        // Given
        outboxAssert.clear();

        UUID aggregate1Id = Ids.newId();
        UUID aggregate2Id = Ids.newId();
        UUID aggregate3Id = Ids.newId();

        UUID a1 = outboxAssert.publishReady("test.outbox-relay.v1", aggregate1Id);
        UUID a2 = outboxAssert.publishReady("test.outbox-relay.v1", aggregate2Id);
        UUID a3 = outboxAssert.publishReady("test.outbox-relay.v1", aggregate3Id);

        // When
        int sent = outboxRelay.relayOnce();

        // Then
        assertThat(sent).isEqualTo(3);
        OutboxSnapshot outboxSnapshotOfA1 = outboxAssert.outboxSnapshotOf(a1);
        OutboxSnapshot outboxSnapshotOfA2 = outboxAssert.outboxSnapshotOf(a2);
        OutboxSnapshot outboxSnapshotOfA3 = outboxAssert.outboxSnapshotOf(a3);

        assertThat(outboxSnapshotOfA1.status()).isEqualTo("SENT");
        assertThat(outboxSnapshotOfA2.status()).isEqualTo("SENT");
        assertThat(outboxSnapshotOfA3.status()).isEqualTo("SENT");

        assertThat(outboxSnapshotOfA1.sentAt()).isNotNull();
        assertThat(outboxSnapshotOfA2.sentAt()).isNotNull();
        assertThat(outboxSnapshotOfA3.sentAt()).isNotNull();

        assertThat(outboxSnapshotOfA1.lockedUntil()).isNull();
        assertThat(outboxSnapshotOfA2.lockedUntil()).isNull();
        assertThat(outboxSnapshotOfA3.lockedUntil()).isNull();

        assertThat(outboxSnapshotOfA1.attempts()).isEqualTo(0);
        assertThat(outboxSnapshotOfA2.attempts()).isEqualTo(0);
        assertThat(outboxSnapshotOfA3.attempts()).isEqualTo(0);

        List<ReceivedMessage> a1Messages = testKafka.messages("test.outbox-relay.v1", aggregate1Id.toString());
        List<ReceivedMessage> a2Messages = testKafka.messages("test.outbox-relay.v1", aggregate2Id.toString());
        List<ReceivedMessage> a3Messages = testKafka.messages("test.outbox-relay.v1", aggregate3Id.toString());

        assertThat(a1Messages).hasSize(1);
        assertThat(a2Messages).hasSize(1);
        assertThat(a3Messages).hasSize(1);

        assertThat(a1Messages.get(0).envelope().eventId()).isEqualTo(a1);
        assertThat(a2Messages.get(0).envelope().eventId()).isEqualTo(a2);
        assertThat(a3Messages.get(0).envelope().eventId()).isEqualTo(a3);

        assertThat(a1Messages.get(0).envelope().aggregateId()).isEqualTo(aggregate1Id);
        assertThat(a2Messages.get(0).envelope().aggregateId()).isEqualTo(aggregate2Id);
        assertThat(a3Messages.get(0).envelope().aggregateId()).isEqualTo(aggregate3Id);

        assertThat(a1Messages.get(0).envelope().eventType()).isEqualTo("TestEvent");
        assertThat(a2Messages.get(0).envelope().eventType()).isEqualTo("TestEvent");
        assertThat(a3Messages.get(0).envelope().eventType()).isEqualTo("TestEvent");

        assertThat(a1Messages.get(0).envelope().traceId()).isNull();
        assertThat(a2Messages.get(0).envelope().traceId()).isNull();
        assertThat(a3Messages.get(0).envelope().traceId()).isNull();

        assertThat(outboxRelay.relayOnce()).isEqualTo(0);

        assertThat(testKafka.messages("test.outbox-relay.v1", aggregate1Id.toString())).hasSize(1);
        assertThat(testKafka.messages("test.outbox-relay.v1", aggregate2Id.toString())).hasSize(1);
        assertThat(testKafka.messages("test.outbox-relay.v1", aggregate3Id.toString())).hasSize(1);
    }

    @Test
    @DisplayName("Kafka가 멈추면 READY로 되돌려 10초 뒤로 미루고, 돌아오면 다시 발행한다.")
    void relayOnceRetriesAfterKafkaRecovers() {
        // Given
        outboxAssert.clear();

        UUID aggregate1Id = Ids.newId();

        UUID a1 = outboxAssert.publishReady("test.outbox-relay.v1", aggregate1Id);

        // When & Then
        kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
        try {
            int sent = outboxRelay.relayOnce();
            assertThat(sent).isEqualTo(0);
            OutboxSnapshot outboxSnapshotOfA1 = outboxAssert.outboxSnapshotOf(a1);

            assertThat(outboxSnapshotOfA1.status()).isEqualTo("READY");
            assertThat(outboxSnapshotOfA1.attempts()).isEqualTo(1);
            assertThat(outboxSnapshotOfA1.lastError()).isNotNull().hasSizeLessThanOrEqualTo(500);
            assertThat(outboxSnapshotOfA1.lockedUntil()).isNull();
            assertThat(outboxSnapshotOfA1.nextAttemptAt()).isAfter(Instant.now());
        } finally {
            kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
        }

        jdbcTemplate.update("UPDATE common.outbox_event SET next_attempt_at = now() - interval '1 second' WHERE id = ?", a1);

        int sent = outboxRelay.relayOnce();

        assertThat(sent).isEqualTo(1);
        OutboxSnapshot outboxSnapshotOfA1 = outboxAssert.outboxSnapshotOf(a1);

        assertThat(outboxSnapshotOfA1.status()).isEqualTo("SENT");
        assertThat(outboxSnapshotOfA1.attempts()).isEqualTo(1);

        List<ReceivedMessage> a1Messages = testKafka.messages("test.outbox-relay.v1", aggregate1Id.toString());

        assertThat(a1Messages.size()).isIn(1, 2);
        assertThat(a1Messages).extracting(m -> m.envelope().eventId()).containsOnly(a1);
    }

    @Test
    @DisplayName("리스가 끝난 PROCESSING 행만 회수해 발행한다.")
    void relayOnceReclaimsOnlyExpiredLease() {
        // Given
        outboxAssert.clear();

        UUID aggregate1Id = Ids.newId();
        UUID aggregate2Id = Ids.newId();

        UUID a1 = outboxAssert.publishReady("test.outbox-relay.v1", aggregate1Id);
        UUID a2 = outboxAssert.publishReady("test.outbox-relay.v1", aggregate2Id);

        jdbcTemplate.update("UPDATE common.outbox_event SET status = 'PROCESSING', locked_until = now() - interval '1 minute' WHERE aggregate_id = ?", aggregate1Id);
        jdbcTemplate.update("UPDATE common.outbox_event SET status = 'PROCESSING', locked_until = now() + interval '1 minute' WHERE aggregate_id = ?", aggregate2Id);

        // When & Then
        int sent = outboxRelay.relayOnce();

        assertThat(sent).isEqualTo(1);
        OutboxSnapshot outboxSnapshotOfA1 = outboxAssert.outboxSnapshotOf(a1);
        OutboxSnapshot outboxSnapshotOfA2 = outboxAssert.outboxSnapshotOf(a2);

        assertThat(outboxSnapshotOfA1.status()).isEqualTo("SENT");

        assertThat(outboxSnapshotOfA2.status()).isEqualTo("PROCESSING");
        assertThat(outboxSnapshotOfA2.lockedUntil()).isAfter(Instant.now());

        List<ReceivedMessage> a1Messages = testKafka.messages("test.outbox-relay.v1", aggregate1Id.toString());
        List<ReceivedMessage> a2Messages = testKafka.messages("test.outbox-relay.v1", aggregate2Id.toString());

        assertThat(a1Messages.size()).isEqualTo(1);
        assertThat(a2Messages.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("10번째 실패는 DEAD로 바꾼다.")
    void relayOnceMarksDeadOnTenthFailure() {
        // Given
        outboxAssert.clear();

        UUID aggregate1Id = Ids.newId();

        UUID a1 = outboxAssert.publishReady("test.outbox-relay.v1", aggregate1Id);

        jdbcTemplate.update("UPDATE common.outbox_event SET attempts = 9 WHERE aggregate_id = ?", aggregate1Id);

        // When & Then
        kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
        try {
            int sent = outboxRelay.relayOnce();
            assertThat(sent).isEqualTo(0);

            OutboxSnapshot outboxSnapshotOfA1 = outboxAssert.outboxSnapshotOf(a1);
            assertThat(outboxSnapshotOfA1.status()).isEqualTo("DEAD");
            assertThat(outboxSnapshotOfA1.attempts()).isEqualTo(10);
            assertThat(outboxSnapshotOfA1.lastError()).isNotNull();
            assertThat(outboxSnapshotOfA1.lockedUntil()).isNull();
        } finally {
            kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
        }

        int sent = outboxRelay.relayOnce();
        assertThat(sent).isEqualTo(0);

        OutboxSnapshot outboxSnapshotOfA1 = outboxAssert.outboxSnapshotOf(a1);
        assertThat(outboxSnapshotOfA1.status()).isEqualTo("DEAD");
    }

    @Test
    @DisplayName("첫 실패에서 중단하고 시도하지 않은 행은 그대로 되돌린다.")
    void relayOnceStopsAtFirstFailure() {
        // Given
        outboxAssert.clear();

        UUID aggregate1Id = Ids.newId();
        UUID aggregate2Id = Ids.newId();

        UUID a1 = outboxAssert.publishReady("test.outbox-relay.v1", aggregate1Id);
        UUID a2 = outboxAssert.publishReady("test.outbox-relay.v1", aggregate2Id);

        // When & Then
        kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
        try {
            int sent = outboxRelay.relayOnce();

            assertThat(sent).isEqualTo(0);
            OutboxSnapshot outboxSnapshotOfA1 = outboxAssert.outboxSnapshotOf(a1);
            OutboxSnapshot outboxSnapshotOfA2 = outboxAssert.outboxSnapshotOf(a2);

            assertThat(outboxSnapshotOfA1.status()).isEqualTo("READY");
            assertThat(outboxSnapshotOfA1.attempts()).isEqualTo(1);
            assertThat(outboxSnapshotOfA1.lastError()).isNotNull();
            assertThat(outboxSnapshotOfA1.lockedUntil()).isNull();
            assertThat(outboxSnapshotOfA1.nextAttemptAt()).isAfter(Instant.now());

            assertThat(outboxSnapshotOfA2.status()).isEqualTo("READY");
            assertThat(outboxSnapshotOfA2.attempts()).isEqualTo(0);
            assertThat(outboxSnapshotOfA2.lastError()).isNull();
            assertThat(outboxSnapshotOfA2.lockedUntil()).isNull();
            assertThat(outboxSnapshotOfA2.nextAttemptAt()).isBefore(Instant.now());
        } finally {
            kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
        }
    }
}
