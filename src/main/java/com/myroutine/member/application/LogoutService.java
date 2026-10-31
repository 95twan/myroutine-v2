package com.myroutine.member.application;

import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.MemberRepository;
import com.myroutine.member.domain.SessionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LogoutService {
    private final SessionStore sessionStore;
    private final MemberRepository memberRepository;
    private final TokenVersionService tokenVersionService;

    @Transactional
    public void logout(UUID memberId, UUID sessionId, boolean all) {
        if (all) {
            Member member = memberRepository.findById(memberId).orElseThrow();
            tokenVersionService.bump(member);
            sessionStore.deleteAllOf(memberId);
        } else {
            sessionStore.delete(sessionId);
        }
    }
}
