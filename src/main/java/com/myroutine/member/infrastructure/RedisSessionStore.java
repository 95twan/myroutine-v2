package com.myroutine.member.infrastructure;

import com.myroutine.member.domain.SessionStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class RedisSessionStore implements SessionStore {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> rotateScript;

    public RedisSessionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.rotateScript = new DefaultRedisScript<>();
        this.rotateScript.setLocation(new ClassPathResource("redis/rotate.lua"));
        this.rotateScript.setResultType(Long.class);
    }

    @Override
    public void create(UUID sessionId, UUID memberId, String refreshHash, Duration ttl) {
        redisTemplate.opsForHash().putAll(
                sessionKey(sessionId),
                Map.of(
                        "memberId", memberId.toString(),
                        "refreshHash", refreshHash

                )
        );
        redisTemplate.expire(sessionKey(sessionId), ttl);
        redisTemplate.opsForSet().add(memberSessionsKey(memberId), sessionId.toString());
        redisTemplate.expire(memberSessionsKey(memberId), ttl);
    }

    @Override
    public Optional<UUID> findMemberId(UUID sessionId) {
        Map<Object, Object> map = redisTemplate.opsForHash().entries(sessionKey(sessionId));
        if (map.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString((String) map.get("memberId")));
    }

    @Override
    public boolean rotate(UUID sessionId, UUID memberId, String expectedHash, String newHash, Duration ttl) {
        Long r = redisTemplate.execute(rotateScript, List.of(sessionKey(sessionId), memberSessionsKey(memberId)), expectedHash, newHash, String.valueOf(ttl.getSeconds()));
        return Long.valueOf(1L).equals(r);
    }

    @Override
    public void delete(UUID sessionId) {
        Map<Object, Object> map = redisTemplate.opsForHash().entries(sessionKey(sessionId));
        if (map.isEmpty()) {
            return;
        }
        String memberId = (String) map.get("memberId");
        redisTemplate.delete(sessionKey(sessionId));
        redisTemplate.opsForSet().remove(memberSessionsKey(UUID.fromString(memberId)), sessionId.toString());
    }

    @Override
    public void deleteAllOf(UUID memberId) {
        Set<String> sessionIds = redisTemplate.opsForSet().members(memberSessionsKey(memberId));
        if (sessionIds.isEmpty()) {
            return;
        }
        Set<String> sessionKeys = sessionIds.stream()
                .map(sessionId -> sessionKey(UUID.fromString(sessionId)))
                .collect(Collectors.toSet());
        redisTemplate.delete(sessionKeys);
        redisTemplate.opsForSet().remove(memberSessionsKey(memberId), sessionIds.toArray());
    }

    @Override
    public void deleteAllOfExcept(UUID memberId, UUID keepSessionId) {
        Set<String> sessionIds = redisTemplate.opsForSet().members(memberSessionsKey(memberId)).stream()
                .filter(sessionId -> !sessionId.equals(keepSessionId.toString()))
                .collect(Collectors.toSet());
        if (sessionIds.isEmpty()) {
            return;
        }
        Set<String> sessionKeys = sessionIds.stream()
                .map(sessionId -> sessionKey(UUID.fromString(sessionId)))
                .collect(Collectors.toSet());
        redisTemplate.delete(sessionKeys);
        redisTemplate.opsForSet().remove(memberSessionsKey(memberId), sessionIds.toArray());
    }

    private String sessionKey(UUID sessionId) {
        return "auth:session:" + sessionId;
    }

    private String memberSessionsKey(UUID memberId) {
        return "auth:member-sessions:" + memberId;
    }
}
