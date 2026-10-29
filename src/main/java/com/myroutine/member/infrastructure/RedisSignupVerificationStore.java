package com.myroutine.member.infrastructure;

import com.myroutine.member.domain.SignupVerificationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RedisSignupVerificationStore implements SignupVerificationStore {

    private final StringRedisTemplate redisTemplate;

    @Override
    public String issue(String email) {
        byte[] randomBytes = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        redisTemplate.opsForValue().set("auth:signup-verified:" + getCodeHash(token), email, Duration.ofMinutes(30));
        return token;
    }

    @Override
    public Optional<String> findEmail(String token) {
        String email = redisTemplate.opsForValue().get("auth:signup-verified:" + getCodeHash(token));
        return Optional.ofNullable(email);
    }

    @Override
    public void delete(String token) {
        redisTemplate.delete("auth:signup-verified:" + getCodeHash(token));
    }

    private String getCodeHash(String code) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        byte[] digest = md.digest(code.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
