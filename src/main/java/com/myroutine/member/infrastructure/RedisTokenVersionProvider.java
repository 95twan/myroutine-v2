package com.myroutine.member.infrastructure;

import com.myroutine.common.security.TokenVersionProvider;
import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisTokenVersionProvider implements TokenVersionProvider {

    private final StringRedisTemplate redisTemplate;
    private final MemberRepository memberRepository;

    @Override
    public int currentVersion(UUID memberId) {
        String tokenVersionRow = redisTemplate.opsForValue().get("auth:token-version:" + memberId);
        if (tokenVersionRow != null) {
            return Integer.parseInt(tokenVersionRow);
        }
        Optional<Member> member = memberRepository.findById(memberId);
        if (member.isEmpty()) {
            return -1;
        }
        int tokenVersion = member.get().getTokenVersion();
        Boolean stored = redisTemplate.opsForValue().setIfAbsent("auth:token-version:" + memberId, String.valueOf(tokenVersion), Duration.ofDays(1));
        if (stored) {
            return tokenVersion;
        }
        tokenVersionRow = redisTemplate.opsForValue().get("auth:token-version:" + memberId);
        return Integer.parseInt(tokenVersionRow);
    }
}
