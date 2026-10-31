package com.myroutine.member.infrastructure;

import com.myroutine.member.domain.EmailCodePurpose;
import com.myroutine.member.domain.EmailCodeStore;
import com.myroutine.support.ConcurrencyRunner;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

class EmailCodeConcurrencyTest extends IntegrationTestSupport {

    private final EmailCodeStore emailCodeStore;
    private final TestFixtures testFixtures;

    @Autowired
    public EmailCodeConcurrencyTest(EmailCodeStore emailCodeStore, TestFixtures testFixtures) {
        this.emailCodeStore = emailCodeStore;
        this.testFixtures = testFixtures;
    }

    @RepeatedTest(10)
    @DisplayName("틀린 인증코드를 동시에 보내도 비교는 5번까지만 한다.")
    void concurrentWrongCodesComparedAtMostFiveTimes() throws InterruptedException {
        // Given
        emailCodeStore.save(EmailCodePurpose.RESET_PASSWORD, "pw@test.com", "123456");
        testFixtures.resetRedisStats();

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String guess = String.format("%06d", 200000 + i);
            tasks.add(() -> emailCodeStore.verify(EmailCodePurpose.RESET_PASSWORD, "pw@test.com", guess));
        }

        // When
        List<ConcurrencyRunner.Result<Boolean>> results = ConcurrencyRunner.run(tasks);

        // Then
        assertThat(results).allMatch(r -> r.ok() && Boolean.FALSE.equals(r.value()));
        assertThat(testFixtures.redisCallsOf("hincrby")).isEqualTo(5);
    }
}
