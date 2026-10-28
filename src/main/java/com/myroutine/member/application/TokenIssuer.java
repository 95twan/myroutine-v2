package com.myroutine.member.application;

import com.myroutine.common.model.Ids;
import com.myroutine.common.security.JwtProperties;
import com.myroutine.common.security.JwtProvider;
import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.RefreshToken;
import com.myroutine.member.domain.SessionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtProvider jwtProvider;
    private final SessionStore sessionStore;
    private final JwtProperties properties;

    public TokenResult issueNewSession(Member member) {
        UUID sessionId = Ids.newId();
        RefreshToken refreshToken = RefreshToken.generate(sessionId);
        sessionStore.create(sessionId, member.getId(), refreshToken.secretHash(), properties.refreshTokenTtl());
        String accessToken = jwtProvider.issue(member.getId(), member.getRole().name(), sessionId);
        return new TokenResult(accessToken, jwtProvider.accessTokenTtl().getSeconds(), refreshToken.value(), properties.refreshTokenTtl().getSeconds());
    }

    public TokenResult reissue(Member member, UUID sessionId, RefreshToken refreshToken) {
        String access = jwtProvider.issue(member.getId(), member.getRole().name(), sessionId);
        return new TokenResult(access, jwtProvider.accessTokenTtl().getSeconds(), refreshToken.value(), properties.refreshTokenTtl().getSeconds());
    }

    public TokenResult reissueAccess(Member member, UUID sessionId) {
        String access = jwtProvider.issue(member.getId(), member.getRole().name(), sessionId);
        return new TokenResult(access, jwtProvider.accessTokenTtl().getSeconds(), null, 0);
    }
}
