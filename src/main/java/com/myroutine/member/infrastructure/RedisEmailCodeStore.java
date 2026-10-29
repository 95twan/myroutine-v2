package com.myroutine.member.infrastructure;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.member.domain.EmailCodePurpose;
import com.myroutine.member.domain.EmailCodeStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisEmailCodeStore implements EmailCodeStore {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void checkSendLimit(String email) {
        Boolean isStored = redisTemplate.opsForValue().setIfAbsent("auth:email-cooldown:" + email, "1", Duration.ofSeconds(60));
        if (!isStored) {
            Long expire = redisTemplate.getExpire("auth:email-cooldown:" + email, TimeUnit.SECONDS);
            throw new BusinessException(CommonErrorCode.RATE_LIMITED, Map.of("retryAfterSeconds", expire < 1 ? 1 : expire));
        }
        Long increment = redisTemplate.opsForValue().increment("auth:email-hourly:" + email);
        if (increment == 1) {
            redisTemplate.expire("auth:email-hourly:" + email, Duration.ofHours(1));
        } else if (increment > 5) {
            Long expire = redisTemplate.getExpire("auth:email-hourly:" + email, TimeUnit.SECONDS);
            throw new BusinessException(CommonErrorCode.RATE_LIMITED, Map.of("retryAfterSeconds", expire < 1 ? 1 : expire));
        }
    }

    @Override
    public void save(EmailCodePurpose p, String email, String code) {
        String key = "auth:email-code:" + p + ":" + email;
        redisTemplate.opsForHash().putAll(key, Map.of("codeHash", getCodeHash(code), "attempts", "0"));
        redisTemplate.expire(key, Duration.ofMinutes(5));
    }

    @Override
    public boolean verify(EmailCodePurpose p, String email, String code) {
        String key = "auth:email-code:" + p + ":" + email;
        Map<Object, Object> map = redisTemplate.opsForHash().entries(key);
        if (map.isEmpty()) {
            return false;
        }
        if (map.get("codeHash") == null) {
            redisTemplate.delete(key);
            return false;
        }
        String codeHash = getCodeHash(code);
        if (MessageDigest.isEqual(codeHash.getBytes(StandardCharsets.UTF_8), ((String) map.get("codeHash")).getBytes(StandardCharsets.UTF_8))) {
            redisTemplate.delete(key);
            return true;
        } else {
            Long increment = redisTemplate.opsForHash().increment(key, "attempts", 1);
            if (increment >= 5) {
                redisTemplate.delete(key);
            }
            return false;
        }
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
