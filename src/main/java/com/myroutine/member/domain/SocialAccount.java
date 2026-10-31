package com.myroutine.member.domain;

import com.myroutine.common.model.Ids;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "social_account", schema = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EntityListeners(AuditingEntityListener.class)
public class SocialAccount {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(name = "provider_email")
    private String providerEmail;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;


    public static SocialAccount link(UUID memberId, OAuthProvider provider, String providerUserId, String providerEmail) {
        SocialAccount socialAccount = new SocialAccount();
        socialAccount.id = Ids.newId();
        socialAccount.memberId = memberId;
        socialAccount.provider = provider;
        socialAccount.providerUserId = providerUserId;
        socialAccount.providerEmail = providerEmail;
        return socialAccount;
    }

}
