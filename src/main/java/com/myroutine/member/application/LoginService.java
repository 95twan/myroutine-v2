package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.member.domain.LoginAttemptStore;
import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.MemberErrorCode;
import com.myroutine.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LoginService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final LoginAttemptStore loginAttemptStore;

    @Transactional(readOnly = true)
    public TokenResult login(LoginCommand command) {
        String email = Member.normalizeEmail(command.email());

        int attempt = loginAttemptStore.recordAttempt(email);
        if (attempt == -1) {
            throw lockedException(email);
        }

        Optional<Member> member = memberRepository.findByEmail(email);
        if (member.isEmpty() || member.get().getPasswordHash() == null || !passwordEncoder.matches(command.password(), member.get().getPasswordHash())) {
            if (attempt < 5) {
                throw new BusinessException(MemberErrorCode.LOGIN_FAILED, Map.of("remainingAttempts", 5 - attempt));
            }
            throw lockedException(email);
        }

        loginAttemptStore.reset(email);

        member.get().verifyCanLogin();

        return tokenIssuer.issueNewSession(member.get());
    }

    private BusinessException lockedException(String email) {
        return loginAttemptStore.lockedUntil(email)
                .map(unlockAt -> new BusinessException(MemberErrorCode.ACCOUNT_LOCKED, Map.of("unlockAt", unlockAt.toString())))
                .orElseGet(() -> new BusinessException(MemberErrorCode.LOGIN_FAILED, Map.of("remainingAttempts", 4)));
    }

}
