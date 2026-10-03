package com.myroutine.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtProvider {
    private final SecretKey key;
    private final JwtProperties jwtProperties;
    private final Clock clock;


    public JwtProvider(JwtProperties jwtProperties, Clock clock) {
        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public String issue(UUID memberId, String role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(memberId.toString())
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwtProperties.accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    public Optional<AuthClaims> parse(String token) {
        JwtParser parser = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build();
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            String subject = claims.getSubject();
            String role = claims.get("role", String.class);
            if (subject == null || role == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthClaims(UUID.fromString(subject), role));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Duration accessTokenTtl() {
        return jwtProperties.accessTokenTtl();
    }
}
