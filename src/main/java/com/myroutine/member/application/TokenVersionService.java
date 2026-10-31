package com.myroutine.member.application;

import com.myroutine.member.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenVersionService {
    private final StringRedisTemplate redisTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public void bump(Member member) {
        member.increaseTokenVersion();
        int version = member.getTokenVersion();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisTemplate.opsForValue().set("auth:token-version:" + member.getId(), String.valueOf(version), Duration.ofDays(1));
            }
        });
    }
}
