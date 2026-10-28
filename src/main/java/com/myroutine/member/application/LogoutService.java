package com.myroutine.member.application;

import com.myroutine.member.domain.SessionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LogoutService {
    private final SessionStore sessionStore;

    public void logout(UUID memberId, UUID sessionId, boolean all) {
        if (all) {
            sessionStore.deleteAllOf(memberId);
        } else {
            sessionStore.delete(sessionId);
        }
    }
}
