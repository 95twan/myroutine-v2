package com.myroutine.member.infrastructure;

import com.myroutine.common.security.TokenVersionProvider;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTokenVersionProviderTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final TokenVersionProvider tokenVersionProvider;

    @Autowired
    public RedisTokenVersionProviderTest(TestFixtures testFixtures, JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate, TokenVersionProvider tokenVersionProvider) {
        this.testFixtures = testFixtures;
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.tokenVersionProvider = tokenVersionProvider;
    }

    @Test
    @DisplayName("Redis에 값이 없으면 DB 값을 읽어 Redis에 채운다.")
    void fillsRedisFromDbWhenMissing() {
        // Given
        UUID targetId = testFixtures.signup("target@test.com");
        jdbcTemplate.update("UPDATE member.member SET token_version = 3 WHERE id = ?", targetId);
        redisTemplate.delete("auth:token-version:" + targetId);

        // When
        int currentVersion = tokenVersionProvider.currentVersion(targetId);

        // Then
        assertThat(currentVersion).isEqualTo(3);

        assertThat(testFixtures.tokenVersionSnapshotOf(targetId).redis()).isEqualTo(3);
        assertThat(testFixtures.tokenVersionSnapshotOf(targetId).redisTtlSeconds()).isBetween(80000L, 86400L);

        jdbcTemplate.update("UPDATE member.member SET token_version = 5 WHERE id = ?", targetId);
        redisTemplate.delete("auth:token-version:" + targetId);

        currentVersion = tokenVersionProvider.currentVersion(targetId);

        assertThat(currentVersion).isEqualTo(5);

        assertThat(testFixtures.tokenVersionSnapshotOf(targetId).redis()).isEqualTo(5);
    }

    @Test
    @DisplayName("Redis에 값이 있으면 DB를 읽지 않는다.")
    void doesNotReadDbWhenRedisHasValue() {
        // Given
        UUID targetId = testFixtures.signup("target@test.com");
        jdbcTemplate.update("UPDATE member.member SET token_version = 3 WHERE id = ?", targetId);
        redisTemplate.delete("auth:token-version:" + targetId);

        tokenVersionProvider.currentVersion(targetId);

        jdbcTemplate.update("UPDATE member.member SET token_version = 5 WHERE id = ?", targetId);

        // When
        int currentVersion = tokenVersionProvider.currentVersion(targetId);

        // Then
        assertThat(currentVersion).isEqualTo(3);
    }
}
