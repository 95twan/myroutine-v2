package com.myroutine.common.config;

import com.myroutine.common.model.Ids;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaDltTest extends IntegrationTestSupport {

    private final AlwaysFailListener listener;
    private final TestKafka testKafka;

    @Autowired
    public KafkaDltTest(AlwaysFailListener listener, TestKafka testKafka) {
        this.listener = listener;
        this.testKafka = testKafka;
    }

    @Test
    @DisplayName("일시적 오류는 4번 시도한 뒤 같은 파티션의 DLT로 옮긴다.")
    void transientFailureMovesToDltAfterFourAttempts() throws Exception {
        // Given
        String key = Ids.newId().toString();
        UUID eventId = Ids.newId();

        // When & Then
        int partition = testKafka.send("test.always-fail.v1", key, eventId, new TestEvent("x"));
        Eventually.await(Duration.ofSeconds(20), () -> {
            List<ReceivedMessage> messages = testKafka.messages("test.always-fail.v1.dlt", key);
            assertThat(messages).hasSize(1);
        });

        List<ReceivedMessage> messages = testKafka.messages("test.always-fail.v1.dlt", key);
        assertThat(messages.get(0).value()).isEqualTo(testKafka.messages("test.always-fail.v1", key).get(0).value());
        assertThat(messages.get(0).partition()).isEqualTo(partition);
        assertThat(listener.attempts(key)).isEqualTo(4);
    }

    @Test
    @DisplayName("깨진 JSON은 재시도 없이 바로 DLT로 옮긴다.")
    void malformedJsonMovesToDltWithoutRetry() throws Exception {
        // Given
        String key = Ids.newId().toString();

        // When & Then
        testKafka.sendRaw("test.always-fail.v1", key, "{not-json");
        Eventually.await(Duration.ofSeconds(3), () -> {
            List<ReceivedMessage> messages = testKafka.messages("test.always-fail.v1.dlt", key);
            assertThat(messages).hasSize(1);
        });

        List<ReceivedMessage> messages = testKafka.messages("test.always-fail.v1.dlt", key);
        assertThat(messages.get(0).value()).isEqualTo("{not-json");
        assertThat(listener.attempts(key)).isEqualTo(1);
    }
}
