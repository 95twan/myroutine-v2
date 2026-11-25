package com.myroutine.member.application;

import com.myroutine.member.domain.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class SessionCleanupService {
    private final SessionRepository sessionRepository;
    private final Clock clock;

    @Transactional
    public int deleteExpired() {
        return sessionRepository.deleteExpired(clock.instant());
    }
}
