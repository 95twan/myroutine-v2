package com.myroutine.member.infrastructure;

import com.myroutine.member.domain.LoginAttemptStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class RedisLoginAttemptStore implements LoginAttemptStore {

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final DefaultRedisScript<Long> loginAttemptScript;

    public RedisLoginAttemptStore(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.loginAttemptScript = new DefaultRedisScript<>();
        this.loginAttemptScript.setLocation(new ClassPathResource("redis/login-attempt.lua"));
        this.loginAttemptScript.setResultType(Long.class);
    }

    @Override
    public Optional<Instant> lockedUntil(String email) {
        String failureCountRow = redisTemplate.opsForValue().get("auth:login-fail:" + email);
        if (failureCountRow == null || Integer.parseInt(failureCountRow) < 5) {
            return Optional.empty();
        }

        Long expire = redisTemplate.getExpire("auth:login-fail:" + email, TimeUnit.SECONDS);

        return Optional.of(clock.instant().plusSeconds(expire));
    }

    @Override
    public int recordAttempt(String email) {
        Long r = redisTemplate.execute(loginAttemptScript, List.of("auth:login-fail:" + email));
        return r.intValue();
    }

    @Override
    public void reset(String email) {
        redisTemplate.delete("auth:login-fail:" + email);
    }
}
