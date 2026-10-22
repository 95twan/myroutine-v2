package com.myroutine.common.inbox;

import com.myroutine.common.model.Ids;
import com.myroutine.common.outbox.EventEnvelope;
import com.myroutine.support.ConcurrencyRunner;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboxGuardTest extends IntegrationTestSupport {

    private final InboxGuard inboxGuard;
    private final ObjectMapper objectMapper;
    private final TestFixtures testFixtures;

    @Autowired
    public InboxGuardTest(InboxGuard inboxGuard, ObjectMapper objectMapper, TestFixtures testFixtures) {
        this.inboxGuard = inboxGuard;
        this.objectMapper = objectMapper;
        this.testFixtures = testFixtures;
    }

    @Test
    @DisplayName("같은 eventId로 두 번 호출해도 action은 한 번만 실행한다.")
    void runOnceExecutesActionOnlyOnceForSameEventId() {
        // Given
        UUID eventId = Ids.newId();
        UUID aggregateId = Ids.newId();
        EventEnvelope envelope = new EventEnvelope(eventId, "TestEvent", Instant.now(), aggregateId, "trace-test-1", objectMapper.createObjectNode());
        AtomicInteger counter = new AtomicInteger(0);

        // When
        inboxGuard.runOnce("test.consumer", envelope, counter::incrementAndGet);
        inboxGuard.runOnce("test.consumer", envelope, counter::incrementAndGet);

        // Then
        assertThat(counter.get()).isEqualTo(1);
        assertThat(testFixtures.processedCountOf("test.consumer", eventId)).isEqualTo(1);
    }

    @Test
    @DisplayName("consumer 이름이 다르면 같은 eventId도 각자 한 번씩 실행한다.")
    void runOnceExecutesPerConsumer() {
        // Given
        UUID eventId = Ids.newId();
        UUID aggregateId = Ids.newId();
        EventEnvelope envelope = new EventEnvelope(eventId, "TestEvent", Instant.now(), aggregateId, "trace-test-1", objectMapper.createObjectNode());
        AtomicInteger counter = new AtomicInteger(0);

        // When
        inboxGuard.runOnce("test.consumer", envelope, counter::incrementAndGet);
        inboxGuard.runOnce("test.consumer2", envelope, counter::incrementAndGet);

        // Then
        assertThat(counter.get()).isEqualTo(2);
        assertThat(testFixtures.processedCountOf("test.consumer", eventId)).isEqualTo(1);
        assertThat(testFixtures.processedCountOf("test.consumer2", eventId)).isEqualTo(1);

        envelope = new EventEnvelope(Ids.newId(), "TestEvent", Instant.now(), aggregateId, "trace-test-1", objectMapper.createObjectNode());
        inboxGuard.runOnce("test.consumer", envelope, counter::incrementAndGet);
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("action이 예외를 던지면 처리 기록도 롤백하고 재시도 때 다시 처리한다.")
    void runOnceRollsBackRecordWhenActionFails() {
        // Given
        UUID eventId = Ids.newId();
        UUID aggregateId = Ids.newId();
        EventEnvelope envelope = new EventEnvelope(eventId, "TestEvent", Instant.now(), aggregateId, "trace-test-1", objectMapper.createObjectNode());
        AtomicInteger counter = new AtomicInteger(0);

        // When & Then
        assertThatThrownBy(() -> inboxGuard.runOnce("test.consumer", envelope, () -> {
            throw new IllegalStateException("fail");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(testFixtures.processedCountOf("test.consumer", eventId)).isEqualTo(0);

        inboxGuard.runOnce("test.consumer", envelope, counter::incrementAndGet);

        assertThat(counter.get()).isEqualTo(1);
        assertThat(testFixtures.processedCountOf("test.consumer", eventId)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 eventId가 동시에 두 건 들어와도 action은 한 번만 실행한다.")
    void runOnceExecutesActionOnlyOnceForConcurrentSameEventId() throws InterruptedException {
        // Given
        UUID eventId = Ids.newId();
        UUID aggregateId = Ids.newId();
        EventEnvelope envelope = new EventEnvelope(eventId, "TestEvent", Instant.now(), aggregateId, "trace-test-1", objectMapper.createObjectNode());
        AtomicInteger counter = new AtomicInteger(0);

        // When & Then
        List<ConcurrencyRunner.Result<Object>> results = ConcurrencyRunner.run(2, () -> {
            inboxGuard.runOnce("test.consumer", envelope, () -> {
                counter.incrementAndGet();
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            return null;
        });

        results.stream().map(ConcurrencyRunner.Result::ok).forEach(result -> assertThat(result).isTrue());
        assertThat(counter.get()).isEqualTo(1);
        assertThat(testFixtures.processedCountOf("test.consumer", eventId)).isEqualTo(1);
    }

    @Test
    @DisplayName("처리하는 동안 MDC에 traceId와 eventId를 두고 끝나면 지운다.")
    void runOnceSetsAndClearsMdc() {
        // Given
        UUID eventId = Ids.newId();
        UUID aggregateId = Ids.newId();
        EventEnvelope envelope = new EventEnvelope(eventId, "TestEvent", Instant.now(), aggregateId, "trace-test-1", objectMapper.createObjectNode());
        AtomicInteger counter = new AtomicInteger(0);

        // When & Then
        inboxGuard.runOnce("test.consumer", envelope, () -> {
            assertThat(MDC.get("eventId")).isEqualTo(eventId.toString());
            assertThat(MDC.get("traceId")).isEqualTo("trace-test-1");
        });

        assertThat(MDC.get("eventId")).isNull();
        assertThat(MDC.get("traceId")).isNull();
    }
}
