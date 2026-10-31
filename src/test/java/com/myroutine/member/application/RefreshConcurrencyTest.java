package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.security.JwtProvider;
import com.myroutine.member.domain.MemberErrorCode;
import com.myroutine.member.domain.RefreshToken;
import com.myroutine.support.ConcurrencyRunner;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class RefreshConcurrencyTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final TokenRefreshService tokenRefreshService;

    @Autowired
    public RefreshConcurrencyTest(TestFixtures testFixtures, TokenRefreshService tokenRefreshService) {
        this.testFixtures = testFixtures;
        this.tokenRefreshService = tokenRefreshService;
    }

    @RepeatedTest(10)
    @DisplayName("같은 refresh로 동시에 두 번 갱신하면 하나만 성공하고 세션이 폐기된다.")
    void concurrentRefreshWithSameTokenRevokesSession() throws InterruptedException {
        // Given
        testFixtures.signup("member@test.com");
        TokenResult token = testFixtures.loginTokens("member@test.com");
        String refreshToken = token.refreshToken();
        UUID sid = RefreshToken.parse(refreshToken).sessionId();

        // When
        List<ConcurrencyRunner.Result<TokenResult>> results = ConcurrencyRunner.run(2, () -> tokenRefreshService.refresh(refreshToken));

        assertThat(results.stream().filter(ConcurrencyRunner.Result::ok).count()).isEqualTo(1);
        assertThat(results.stream().filter(ConcurrencyRunner.Result::ok).map(result -> result.value().refreshToken()).findFirst().orElseThrow()).isNotEqualTo(refreshToken);
        assertThat(results.stream().filter(result -> !result.ok()).map(ConcurrencyRunner.Result::error).findFirst().orElseThrow()).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.REFRESH_REUSED));

        assertThat(testFixtures.sessionSnapshotOf(sid).exists()).isFalse();
    }
}
