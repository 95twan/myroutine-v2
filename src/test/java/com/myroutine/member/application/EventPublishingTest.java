package com.myroutine.member.application;

import com.myroutine.member.api.event.MemberTopics;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.OutboxAssert;
import com.myroutine.support.OutboxSnapshot;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventPublishingTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final OutboxAssert outboxAssert;

    @Autowired
    public EventPublishingTest(TestFixtures testFixtures, OutboxAssert outboxAssert) {
        this.testFixtures = testFixtures;
        this.outboxAssert = outboxAssert;
    }

    @Test
    @DisplayName("가입하면 member-registered 이벤트를 outbox에 남긴다.")
    void signUpPublishesMemberRegistered() {
        // Given
        outboxAssert.clear();

        // When
        UUID memberId = testFixtures.signup("test@test.com");

        // Then
        List<OutboxSnapshot> rows = outboxAssert.rows(MemberTopics.MEMBER_REGISTERED);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).aggregateType()).isEqualTo("MEMBER");
        assertThat(rows.get(0).aggregateId()).isEqualTo(memberId);
        assertThat(rows.get(0).payload().size()).isEqualTo(3);
        assertThat(rows.get(0).payload().get("memberId").asString()).isEqualTo(memberId.toString());
        assertThat(rows.get(0).payload().get("email").asString()).isEqualTo("test@test.com");
        assertThat(rows.get(0).payload().get("nickname").asString()).isEqualTo("test");
    }
}
