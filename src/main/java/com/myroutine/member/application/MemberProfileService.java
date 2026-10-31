package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.member.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberProfileService {
    private final MemberRepository memberRepository;
    private final SocialAccountRepository socialAccountRepository;

    @Transactional
    public MeResult changeProfile(UUID memberId, ChangeProfileCommand command) {
        Member member = memberRepository.findById(memberId).orElseThrow(
                () -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)
        );
        if (command.nickname() != null && !command.nickname().equals(member.getNickname()) && memberRepository.existsByNickname(command.nickname())) {
            throw new BusinessException(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        }
        member.changeProfile(command.nickname(), command.name(), command.phone());

        List<SocialAccount> socialAccounts = socialAccountRepository.findByMemberIdOrderByCreatedAtAscIdAsc(memberId);

        return MeResult.from(member, socialAccounts);
    }
}
