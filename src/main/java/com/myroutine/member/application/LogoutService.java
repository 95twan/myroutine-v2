package com.myroutine.member.application;

import com.myroutine.member.domain.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LogoutService {
    private final SessionRepository sessionRepository;

    @Transactional
    public void logout(UUID memberId, UUID sessionId, boolean all) {
        if (all) {
            sessionRepository.deleteAllByMemberId(memberId);
        } else {
            sessionRepository.deleteBySessionId(sessionId);
        }
    }
}
