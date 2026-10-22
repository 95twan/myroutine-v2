package com.myroutine.member.application;

import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberRoleService {
    private final MemberRepository memberRepository;

    @Transactional
    public void grantSeller(UUID memberId) {
        Optional<Member> member = memberRepository.findById(memberId);
        if (member.isEmpty()) {
            log.warn("회원을 찾을 수 없습니다. memberId={}", memberId);
            return;
        }
        member.get().promoteToSeller();
    }
}
