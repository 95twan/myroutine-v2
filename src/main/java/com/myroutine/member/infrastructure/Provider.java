package com.myroutine.member.infrastructure;

public record Provider(
        String clientId,
        String clientSecret,
        String tokenUri,
        String userInfoUri,
        String redirectUri
) {
}
