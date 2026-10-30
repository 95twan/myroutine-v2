package com.myroutine.member.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialAccountRepository {
    Optional<SocialAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    boolean existsByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    boolean existsByMemberIdAndProvider(UUID memberId, OAuthProvider provider);

    List<SocialAccount> findByMemberIdOrderByCreatedAtAscIdAsc(UUID memberId);

    void deleteAllByMemberId(UUID memberId);

    SocialAccount save(SocialAccount socialAccount);
}
