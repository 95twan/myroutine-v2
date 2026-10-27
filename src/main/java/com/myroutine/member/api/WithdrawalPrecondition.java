package com.myroutine.member.api;

import java.util.List;
import java.util.UUID;

public interface WithdrawalPrecondition {
    List<String> violations(UUID memberId);
}
