package com.myroutine.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class JwtProviderTest {

    private final JwtProperties jwtProperties = new JwtProperties("8kZ3yF1vN6tE9aB2qX4mP7rL0cI8eG5wJ1vN6tE9aB0=", Duration.ofHours(1));
    private final Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
    private final JwtProvider jwtProvider = new JwtProvider(jwtProperties, Clock.fixed(t0, ZoneOffset.UTC));

    @Test
    @DisplayName("발급한 토큰을 검증하면 같은 memberId와 role이 나온다.")
    void issue_then_parse_returns_same_claims() {
        UUID memberId = UUID.randomUUID();
        String role = "USER";
        String token = jwtProvider.issue(memberId, role);

        AuthClaims claims = jwtProvider.parse(token).orElseThrow();
        assertThat(claims.memberId()).isEqualTo(memberId);
        assertThat(claims.role()).isEqualTo(role);
    }

    @Test
    @DisplayName("토큰 검증을 실패한다. (만료)")
    void parse_after_expiry_returns_empty() {
        UUID memberId = UUID.randomUUID();
        String role = "USER";
        String token = jwtProvider.issue(memberId, role);

        JwtProvider newJwtProvider = new JwtProvider(jwtProperties, Clock.fixed(t0.plusSeconds(3601), ZoneOffset.UTC));
        assertThat(newJwtProvider.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("토큰 검증을 실패한다. (서명 변조)")
    void parse_tampered_signature_returns_empty() {
        UUID memberId = UUID.randomUUID();
        String role = "USER";
        String token = jwtProvider.issue(memberId, role);

        String[] parts = token.split("\\.");
        String signature = parts[2];

        int mid = signature.length() / 2;
        char original = signature.charAt(mid);
        char replaced = original == 'A' ? 'B' : 'A';

        String tampered = parts[0] + "." + parts[1] + "."
                + signature.substring(0, mid) + replaced + signature.substring(mid + 1);

        assertThat(jwtProvider.parse(tampered)).isEmpty();
    }

    @Test
    @DisplayName("토큰 검증을 실패한다. (다른 키로 서명)")
    void parse_token_signed_with_other_key_returns_empty() {
        JwtProperties otherJwtProperties = new JwtProperties("/X+uaAk0Ub+zJuc3k5yzCzpxS11JaML5lybbrW2tQHU=", Duration.ofHours(1));
        JwtProvider otherJwtProvider = new JwtProvider(otherJwtProperties, Clock.fixed(t0, ZoneOffset.UTC));
        String token = jwtProvider.issue(UUID.randomUUID(), "USER");
        assertThat(otherJwtProvider.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("토큰 검증을 실패한다. (형식 오류)")
    void parse_malformed_token_returns_empty() {
        String token = "abc";
        assertThat(jwtProvider.parse(token)).isEmpty();
    }

}
