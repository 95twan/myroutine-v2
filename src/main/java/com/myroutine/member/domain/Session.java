package com.myroutine.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_session", schema = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Session {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "refresh_hash", nullable = false)
    private String refreshHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Session open(UUID id, UUID memberId, String refreshHash, Instant now, Duration ttl) {
        Session session = new Session();
        session.id = id;
        session.memberId = memberId;
        session.refreshHash = refreshHash;
        session.expiresAt = now.plus(ttl);
        session.createdAt = now;
        return session;
    }
}
