package com.myroutine.member.application;

import com.myroutine.common.security.AuthClaims;
import com.myroutine.common.security.SessionVerifier;
import com.myroutine.member.domain.ActiveSession;
import com.myroutine.member.domain.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionVerificationService implements SessionVerifier {

    private final SessionRepository sessionRepository;
    private final Clock clock;

    @Override
    public Optional<AuthClaims> verify(UUID sessionId, UUID memberId) {
        Optional<ActiveSession> activeSession = sessionRepository.findActive(sessionId, clock.instant());
        if (activeSession.isEmpty() || !activeSession.get().memberId().equals(memberId)) {
            return Optional.empty();
        }

        return Optional.of(new AuthClaims(sessionId, activeSession.get().memberId(), activeSession.get().role().name()));
    }
}
