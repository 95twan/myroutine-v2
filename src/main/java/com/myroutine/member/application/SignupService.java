package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.MemberErrorCode;
import com.myroutine.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignupService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResult signUp(SignupCommand command) {
        if (memberRepository.existsByEmail(Member.normalizeEmail(command.email()))) {
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
        return new SignupResult(savedMember.getId());
    }
}
