package com.myroutine.member.domain;

import java.util.Optional;

public interface SignupVerificationStore {
    String issue(String email);

    Optional<String> findEmail(String token);

    void delete(String token);
}
