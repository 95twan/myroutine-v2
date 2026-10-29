package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.mail.MailSender;
import com.myroutine.member.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionStore sessionStore;
    private final EmailCodeStore emailCodeStore;
    private final MailSender mailSender;
    private final TokenIssuer tokenIssuer;

    @Transactional
    public TokenResult change(UUID memberId, UUID sessionId, String current, String next) {
        Member member = memberRepository.findById(memberId).orElseThrow(
                () -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)
        );
        if (member.getPasswordHash() == null || !passwordEncoder.matches(current, member.getPasswordHash())) {
            throw new BusinessException(MemberErrorCode.PASSWORD_MISMATCH);
        }
        member.changePassword(passwordEncoder.encode(next));
        sessionStore.deleteAllOfExcept(memberId, sessionId);

        return tokenIssuer.reissueAccess(member, sessionId);
    }

    public void requestReset(String email) {
        String normalizedEmail = Member.normalizeEmail(email);
        emailCodeStore.checkSendLimit(normalizedEmail);
        Optional<Member> member = memberRepository.findByEmail(normalizedEmail);
        if (member.isPresent() && member.get().getStatus() == MemberStatus.ACTIVE) {
            SecureRandom secureRandom = new SecureRandom();
            String code = String.format("%06d", secureRandom.nextInt(1_000_000));
            emailCodeStore.save(EmailCodePurpose.RESET_PASSWORD, normalizedEmail, code);
            try {
                mailSender.send(normalizedEmail, "[MyRoutine] 비밀번호 재설정 인증코드", "인증코드: " + code);
            } catch (Exception e) {
                log.error("Failed to send password reset code. memberId={}", member.get().getId(), e);
            }
        }
    }

    @Transactional
    public void confirmReset(String email, String code, String newPassword) {
        String normalizedEmail = Member.normalizeEmail(email);
        if (!emailCodeStore.verify(EmailCodePurpose.RESET_PASSWORD, normalizedEmail, code)) {
            throw new BusinessException(MemberErrorCode.EMAIL_CODE_INVALID);
        }
        Member member = memberRepository.findByEmail(normalizedEmail).orElseThrow(
                () -> new BusinessException(MemberErrorCode.EMAIL_CODE_INVALID)
        );
        member.changePassword(passwordEncoder.encode(newPassword));
        sessionStore.deleteAllOf(member.getId());
    }
}
