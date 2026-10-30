package com.myroutine.member.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties("myroutine.oauth")
public record OAuthProperties(
        Map<String, Provider> providers
) {
}
