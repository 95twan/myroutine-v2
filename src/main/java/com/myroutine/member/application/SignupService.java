package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.outbox.OutboxPublisher;
import com.myroutine.common.security.JwtProvider;
import com.myroutine.member.api.event.MemberRegisteredEvent;
import com.myroutine.member.api.event.MemberTopics;
import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.MemberErrorCode;
import com.myroutine.member.domain.MemberRepository;
import com.myroutine.member.domain.SignupVerificationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SignupService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final OutboxPublisher publisher;
    private final SignupVerificationStore signupVerificationStore;

    @Transactional
    public SignupResult signUp(SignupCommand command) {
        String normalizedEmail = Member.normalizeEmail(command.email());
        Optional<String> email = signupVerificationStore.findEmail(command.verificationToken());
        if (email.isEmpty() || !email.get().equals(normalizedEmail)) {
            throw new BusinessException(MemberErrorCode.VERIFICATION_TOKEN_INVALID);
        }

        if (memberRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(MemberErrorCode.MEMBER_EMAIL_DUPLICATED);
        }
        if (memberRepository.existsByNickname(command.nickname())) {
            throw new BusinessException(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        }
        String encodedPassword = passwordEncoder.encode(command.password());
        Member member = Member.signUp(
                command.email(),
                encodedPassword,
                command.nickname(),
                command.name()
        );

        Member savedMember = memberRepository.save(member);
        UUID memberId = savedMember.getId();

        MemberRegisteredEvent event = new MemberRegisteredEvent(savedMember.getId(), savedMember.getEmail(), savedMember.getNickname());
        publisher.publish(MemberTopics.MEMBER_REGISTERED, "MEMBER", memberId, event);

        signupVerificationStore.delete(command.verificationToken());

        TokenResult token = tokenIssuer.issueNewSession(savedMember);

        return new SignupResult(memberId, token);
    }
}
