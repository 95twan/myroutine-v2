package com.myroutine.member.infrastructure;

import com.myroutine.common.error.BusinessException;
import com.myroutine.member.domain.MemberErrorCode;
import com.myroutine.member.domain.OAuthProvider;
import com.myroutine.member.domain.OAuthUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Slf4j
@Component
public class GoogleOAuthClient implements OAuthProviderClient {

    private final Provider provider;
    private final RestClient oauthRestClient;

    public GoogleOAuthClient(OAuthProperties properties, RestClient oauthRestClient) {
        this.provider = properties.providers().get("google");
        this.oauthRestClient = oauthRestClient;
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.GOOGLE;
    }

    @Override
    public OAuthUserInfo fetchUser(String code, String state) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            form.add("redirect_uri", provider.redirectUri());
            form.add("client_id", provider.clientId());
            form.add("client_secret", provider.clientSecret());

            JsonNode tokenResponse = oauthRestClient.post().uri(provider.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

            String accessToken = tokenResponse == null ? "" : tokenResponse.path("access_token").asString();
            if (accessToken.isBlank()) {
                log.warn("OAuth token response has no access_token. provider={}", provider());
                throw new BusinessException(MemberErrorCode.OAUTH_PROVIDER_ERROR);
            }

            JsonNode userInfoResponse = oauthRestClient.get().uri(provider.userInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);

            String id = userInfoResponse == null ? "" : userInfoResponse.path("sub").asString();
            if (id.isBlank()) {
                log.warn("OAuth user info has no id. provider={}", provider());
                throw new BusinessException(MemberErrorCode.OAUTH_PROVIDER_ERROR);
            }

            String email = userInfoResponse.path("email").asString();
            return new OAuthUserInfo(id, email.isBlank() ? null : email);
        } catch (RestClientException e) {
            log.warn("Failed to fetch OAuth user. provider={}", provider(), e);
            throw new BusinessException(MemberErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }
}
