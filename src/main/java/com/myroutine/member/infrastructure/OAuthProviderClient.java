package com.myroutine.member.infrastructure;

import com.myroutine.member.domain.OAuthProvider;
import com.myroutine.member.domain.OAuthUserInfo;

public interface OAuthProviderClient {
    OAuthProvider provider();

    OAuthUserInfo fetchUser(String code, String state);
}
