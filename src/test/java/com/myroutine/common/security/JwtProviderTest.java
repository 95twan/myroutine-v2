package com.myroutine.common.security;

import com.myroutine.common.model.Ids;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

public class JwtProviderTest {

    private final JwtProperties jwtProperties = new JwtProperties("8kZ3yF1vN6tE9aB2qX4mP7rL0cI8eG5wJ1vN6tE9aB0=", Duration.ofMinutes(15), Duration.ofDays(7));
    private final Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
    private final JwtProvider jwtProvider = new JwtProvider(jwtProperties, Clock.fixed(t0, ZoneOffset.UTC));

    @Test
    @DisplayName("발급한 토큰을 검증하면 같은 memberId와 role이 나온다.")
    void issueThenParseReturnsSameClaims() {
        UUID memberId = Ids.newId();
        UUID sessionId = Ids.newId();
        String role = "USER";
        String token = jwtProvider.issue(memberId, role, sessionId, 1);

        AuthClaims claims = jwtProvider.parse(token).orElseThrow();
        assertThat(claims.memberId()).isEqualTo(memberId);
        assertThat(claims.role()).isEqualTo(role);
    }

    @Test
    @DisplayName("토큰 검증을 실패한다. (만료)")
    void parseAfterExpiryReturnsEmpty() {
        UUID memberId = Ids.newId();
        UUID sessionId = Ids.newId();
        String role = "USER";
        String token = jwtProvider.issue(memberId, role, sessionId, 1);

        JwtProvider newJwtProvider = new JwtProvider(jwtProperties, Clock.fixed(t0.plusSeconds(3601), ZoneOffset.UTC));
        assertThat(newJwtProvider.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("토큰 검증을 실패한다. (서명 변조)")
    void parseTamperedSignatureReturnsEmpty() {
        UUID memberId = Ids.newId();
        UUID sessionId = Ids.newId();
        String role = "USER";
        String token = jwtProvider.issue(memberId, role, sessionId, 1);

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
    void parseTokenSignedWithOtherKeyReturnsEmpty() {
        JwtProperties otherJwtProperties = new JwtProperties("/X+uaAk0Ub+zJuc3k5yzCzpxS11JaML5lybbrW2tQHU=", Duration.ofMinutes(15), Duration.ofDays(7));
        JwtProvider otherJwtProvider = new JwtProvider(otherJwtProperties, Clock.fixed(t0, ZoneOffset.UTC));
        String token = jwtProvider.issue(Ids.newId(), "USER", Ids.newId(), 1);
        assertThat(otherJwtProvider.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("토큰 검증을 실패한다. (형식 오류)")
    void parseMalformedTokenReturnsEmpty() {
        String token = "abc";
        assertThat(jwtProvider.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("토큰 검증을 실패한다. (tv 클레임 없음)")
    void parseTokenWithoutTokenVersionReturnsEmpty() {
        Instant now = Instant.now();
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
        String token = Jwts.builder()
                .subject(Ids.newId().toString())
                .claim("role", "USER")
                .claim("sid", Ids.newId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwtProperties.accessTokenTtl())))
                .signWith(key)
                .compact();

        assertThat(jwtProvider.parse(token)).isEmpty();
    }

}
