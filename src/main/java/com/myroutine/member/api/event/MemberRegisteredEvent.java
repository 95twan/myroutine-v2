package com.myroutine.member.api.event;

import java.util.UUID;

public record MemberRegisteredEvent(
        UUID memberId,
        String email,
        String nickname
) {
}
