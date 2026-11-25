package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.common.security.JwtProperties;
import com.myroutine.member.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenRefreshService {
    private final TokenIssuer tokenIssuer;
    private final MemberRepository memberRepository;
    private final SessionRepository sessionRepository;
    private final JwtProperties properties;
    private final Clock clock;

    public TokenResult refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken refreshToken = RefreshToken.parse(rawRefreshToken);

        UUID sessionId = refreshToken.sessionId();

        Optional<Session> session = sessionRepository.findById(sessionId);
        if (session.isEmpty() || !session.get().getExpiresAt().isAfter(now)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        RefreshToken newRefreshToken = RefreshToken.generate(sessionId);
        int rotate = sessionRepository.rotate(sessionId, refreshToken.secretHash(), newRefreshToken.secretHash(), now.plus(properties.refreshTokenTtl()));
        if (rotate == 0) {
            sessionRepository.deleteBySessionId(sessionId);
            throw new BusinessException(MemberErrorCode.REFRESH_REUSED);
        }

        Optional<Member> member = memberRepository.findById(session.get().getMemberId());
        if (member.isEmpty()) {
            sessionRepository.deleteBySessionId(sessionId);
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        try {
            member.get().verifyCanLogin();
        } catch (BusinessException e) {
            sessionRepository.deleteBySessionId(sessionId);
            throw e;
        }

        return tokenIssuer.reissue(member.get(), sessionId, newRefreshToken);
    }

}
