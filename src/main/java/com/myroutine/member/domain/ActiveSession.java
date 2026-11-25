package com.myroutine.member.domain;

import java.util.UUID;

public record ActiveSession(
        UUID memberId,
        MemberRole role
) {
}
