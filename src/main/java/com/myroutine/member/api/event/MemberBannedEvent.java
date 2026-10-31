package com.myroutine.member.api.event;

import java.util.UUID;

public record MemberBannedEvent(
        UUID memberId,
        String reason
) {
}
