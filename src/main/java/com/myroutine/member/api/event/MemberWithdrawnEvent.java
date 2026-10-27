package com.myroutine.member.api.event;

import java.util.UUID;

public record MemberWithdrawnEvent(
        UUID memberId
) {
}
