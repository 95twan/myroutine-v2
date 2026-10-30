package com.myroutine.member.infrastructure;

import com.myroutine.member.domain.OAuthProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OAuthClients {
    private final Map<OAuthProvider, OAuthProviderClient> clientMap;

    public OAuthClients(List<OAuthProviderClient> clients) {
        this.clientMap = clients.stream()
                .collect(
                        Collectors.toMap(
                                OAuthProviderClient::provider,
                                Function.identity()
                        )
                );
    }

    public OAuthProviderClient get(OAuthProvider provider) {
        return clientMap.get(provider);
    }
}
