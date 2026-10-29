package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.mail.MailSender;
import com.myroutine.member.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class SignupVerificationService {

    private final MailSender mailSender;
    private final EmailCodeStore emailCodeStore;
    private final MemberRepository memberRepository;
    private final SignupVerificationStore signupVerificationStore;

    public void send(String email) {
        String normalizeEmail = Member.normalizeEmail(email);
        if (memberRepository.existsByEmail(normalizeEmail)) {
            throw new BusinessException(MemberErrorCode.MEMBER_EMAIL_DUPLICATED);
        }
        emailCodeStore.checkSendLimit(normalizeEmail);
        SecureRandom secureRandom = new SecureRandom();
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        emailCodeStore.save(EmailCodePurpose.VERIFY_EMAIL, normalizeEmail, code);
        mailSender.send(normalizeEmail, "[MyRoutine] 이메일 인증코드", "인증코드: " + code);
    }

    public String confirm(String email, String code) {
        String normalizeEmail = Member.normalizeEmail(email);
        if (!emailCodeStore.verify(EmailCodePurpose.VERIFY_EMAIL, normalizeEmail, code)) {
            throw new BusinessException(MemberErrorCode.EMAIL_CODE_INVALID);
        }
        return signupVerificationStore.issue(normalizeEmail);
    }
}
