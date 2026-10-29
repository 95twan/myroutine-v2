package com.myroutine.member.infrastructure;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.member.domain.EmailCodePurpose;
import com.myroutine.member.domain.EmailCodeStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class RedisEmailCodeStore implements EmailCodeStore {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> verifyScript;

    public RedisEmailCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.verifyScript = new DefaultRedisScript<>();
        this.verifyScript.setLocation(new ClassPathResource("redis/verify.lua"));
        this.verifyScript.setResultType(Long.class);
    }

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

        Long r = redisTemplate.execute(verifyScript, List.of(key), getCodeHash(code));

        return Long.valueOf(1L).equals(r);
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
