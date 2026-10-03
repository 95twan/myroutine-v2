package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.security.JwtProvider;
import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.MemberErrorCode;
import com.myroutine.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional(readOnly = true)
    public TokenResult login(LoginCommand command) {
        String email = Member.normalizeEmail(command.email());
        Member member = memberRepository.findByEmail(email).orElseThrow(
                () -> new BusinessException(MemberErrorCode.LOGIN_FAILED)
        );
        if (member.getPasswordHash() == null || !passwordEncoder.matches(command.password(), member.getPasswordHash())) {
            throw new BusinessException(MemberErrorCode.LOGIN_FAILED);
        }

        member.verifyCanLogin();

        String accessToken = jwtProvider.issue(member.getId(), member.getRole().name());

        return new TokenResult(accessToken, jwtProvider.accessTokenTtl().getSeconds());
    }

}
