package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.common.security.JwtProperties;
import com.myroutine.member.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenRefreshService {
    private final TokenIssuer tokenIssuer;
    private final MemberRepository memberRepository;
    private final SessionStore sessionStore;
    private final JwtProperties properties;

    @Transactional(readOnly = true)
    public TokenResult refresh(String rawRefreshToken) {
        RefreshToken refreshToken = RefreshToken.parse(rawRefreshToken);
        UUID sessionId = refreshToken.sessionId();
        UUID memberId = sessionStore.findMemberId(sessionId).orElseThrow(
                () -> new BusinessException(CommonErrorCode.UNAUTHORIZED)
        );
        RefreshToken newRefreshToken = RefreshToken.generate(sessionId);
        boolean rotate = sessionStore.rotate(sessionId, memberId, refreshToken.secretHash(), newRefreshToken.secretHash(), properties.refreshTokenTtl());
        if (!rotate) {
            sessionStore.delete(sessionId);
            throw new BusinessException(MemberErrorCode.REFRESH_REUSED);
        }
        Optional<Member> member = memberRepository.findById(memberId);
        if (member.isEmpty()) {
            sessionStore.delete(sessionId);
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        try {
            member.get().verifyCanLogin();
        } catch (BusinessException e) {
            sessionStore.delete(sessionId);
            throw e;
        }

        return tokenIssuer.reissue(member.get(), sessionId, newRefreshToken);
    }

}
