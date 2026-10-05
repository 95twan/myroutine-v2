package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.MemberErrorCode;
import com.myroutine.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberProfileService {
    private final MemberRepository memberRepository;

    @Transactional
    public MeResult changeProfile(UUID memberId, ChangeProfileCommand command) {
        Member member = memberRepository.findById(memberId).orElseThrow(
                () -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)
        );
        if (command.nickname() != null && !command.nickname().equals(member.getNickname()) && memberRepository.existsByNickname(command.nickname())) {
            throw new BusinessException(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        }
        member.changeProfile(command.nickname(), command.name(), command.phone());

        return new MeResult(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getName(),
                member.getPhone(),
                member.getRole(),
                member.getStatus(),
                member.getCreatedAt()
        );
    }
}
