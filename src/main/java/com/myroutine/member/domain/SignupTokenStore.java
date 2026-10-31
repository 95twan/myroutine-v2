package com.myroutine.member.domain;

import java.util.Optional;

public interface SignupTokenStore {
    String issue(OAuthProvider provider, OAuthUserInfo info);

    Optional<PendingSocialSignup> find(String token);

    void delete(String token);
}
