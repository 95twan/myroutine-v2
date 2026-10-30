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
public class MemberQueryService {
    private final MemberRepository memberRepository;
    private final SocialAccountRepository socialAccountRepository;

    @Transactional(readOnly = true)
    public MeResult getMe(UUID memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(
                () -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)
        );

        List<SocialAccount> socialAccounts = socialAccountRepository.findByMemberIdOrderByCreatedAtAscIdAsc(member.getId());

        return MeResult.from(member, socialAccounts);
    }
}
