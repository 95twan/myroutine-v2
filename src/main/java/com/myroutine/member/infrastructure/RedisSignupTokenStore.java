package com.myroutine.member.infrastructure;

import com.myroutine.member.domain.OAuthProvider;
import com.myroutine.member.domain.OAuthUserInfo;
import com.myroutine.member.domain.PendingSocialSignup;
import com.myroutine.member.domain.SignupTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

@Component
@RequiredArgsConstructor
public class RedisSignupTokenStore implements SignupTokenStore {

    private final StringRedisTemplate redisTemplate;

    @Override
    public String issue(OAuthProvider provider, OAuthUserInfo info) {
        byte[] randomBytes = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Map<String, String> map = new HashMap<>();
        map.put("provider", provider.name());
        map.put("providerUserId", info.providerUserId());
        if (info.email() != null) {
            map.put("email", info.email());
        }
        redisTemplate.opsForHash().putAll("auth:oauth-signup:" + getCodeHash(token), map);
        redisTemplate.expire("auth:oauth-signup:" + getCodeHash(token), Duration.ofMinutes(10));
        return token;
    }

    @Override
    public Optional<PendingSocialSignup> find(String token) {
        Map<Object, Object> map = redisTemplate.opsForHash().entries("auth:oauth-signup:" + getCodeHash(token));
        if (map.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new PendingSocialSignup(
                OAuthProvider.valueOf((String) map.get("provider")),
                (String) map.get("providerUserId"),
                (String) map.get("email")
        ));
    }

    @Override
    public void delete(String token) {
        redisTemplate.delete("auth:oauth-signup:" + getCodeHash(token));
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
