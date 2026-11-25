package com.myroutine.member.application;

import com.myroutine.member.domain.RefreshToken;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCleanupServiceTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final JdbcTemplate jdbcTemplate;
    private final SessionCleanupService sessionCleanupService;

    @Autowired
    public SessionCleanupServiceTest(TestFixtures testFixtures, JdbcTemplate jdbcTemplate, SessionCleanupService sessionCleanupService) {
        this.testFixtures = testFixtures;
        this.jdbcTemplate = jdbcTemplate;
        this.sessionCleanupService = sessionCleanupService;
    }

    @Test
    @DisplayName("만료된 세션을 지운다.")
    void deletesExpiredSession() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        TokenResult token1 = testFixtures.loginTokens("test@test.com");
        TokenResult token2 = testFixtures.loginTokens("test@test.com");

        UUID sid1 = RefreshToken.parse(token1.refreshToken()).sessionId();

        jdbcTemplate.update("UPDATE member.auth_session SET expires_at = NOW() - interval '1 second' WHERE id = ?", sid1);

        // When
        sessionCleanupService.deleteExpired();

        // Then
        assertThat(testFixtures.sessionRowsOf(memberId)).noneMatch(row -> row.id().equals(sid1));
    }

    @Test
    @DisplayName("만료되지 않은 세션은 남긴다.")
    void keepsUnexpiredSession() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        TokenResult token1 = testFixtures.loginTokens("test@test.com");
        TokenResult token2 = testFixtures.loginTokens("test@test.com");

        UUID sid1 = RefreshToken.parse(token1.refreshToken()).sessionId();
        UUID sid2 = RefreshToken.parse(token2.refreshToken()).sessionId();

        jdbcTemplate.update("UPDATE member.auth_session SET expires_at = NOW() - interval '1 second' WHERE id = ?", sid1);

        // When
        sessionCleanupService.deleteExpired();

        // Then
        assertThat(testFixtures.sessionRowsOf(memberId)).anyMatch(row -> row.id().equals(sid2));
    }
}
