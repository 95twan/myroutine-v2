package com.myroutine.member.application;

import com.myroutine.common.model.Ids;
import com.myroutine.common.security.JwtProperties;
import com.myroutine.common.security.JwtProvider;
import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.RefreshToken;
import com.myroutine.member.domain.Session;
import com.myroutine.member.domain.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtProvider jwtProvider;
    private final SessionRepository sessionRepository;
    private final JwtProperties properties;
    private final Clock clock;

    public TokenResult issueNewSession(Member member) {
        UUID sessionId = Ids.newId();
        RefreshToken refreshToken = RefreshToken.generate(sessionId);
        Session session = Session.open(sessionId, member.getId(), refreshToken.secretHash(), clock.instant(), properties.refreshTokenTtl());
        sessionRepository.save(session);
        String accessToken = jwtProvider.issue(member.getId(), sessionId);
        return new TokenResult(accessToken, jwtProvider.accessTokenTtl().getSeconds(), refreshToken.value(), properties.refreshTokenTtl().getSeconds());
    }

    public TokenResult reissue(Member member, UUID sessionId, RefreshToken refreshToken) {
        String accessToken = jwtProvider.issue(member.getId(), sessionId);
        return new TokenResult(accessToken, jwtProvider.accessTokenTtl().getSeconds(), refreshToken.value(), properties.refreshTokenTtl().getSeconds());
    }

    public TokenResult reissueAccessToken(Member member, UUID sessionId) {
        String accessToken = jwtProvider.issue(member.getId(), sessionId);
        return new TokenResult(accessToken, jwtProvider.accessTokenTtl().getSeconds(), null, 0);
    }
}
