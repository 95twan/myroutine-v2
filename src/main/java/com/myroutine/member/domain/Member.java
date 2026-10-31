package com.myroutine.member.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.model.BaseTimeEntity;
import com.myroutine.common.model.Ids;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "member", schema = "member")
public class Member extends BaseTimeEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "nickname", nullable = false)
    private String nickname;

    @Column(name = "phone")
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MemberStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private MemberRole role;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "ban_reason")
    private String banReason;

    @Column(name = "banned_at")
    private Instant bannedAt;

    public static String normalizeEmail(String raw) {
        return raw.strip().toLowerCase(Locale.ROOT);
    }

    public static Member signUp(String email, String encodedPassword, String nickname, String name) {
        Member member = new Member();
        member.id = Ids.newId();
        member.email = normalizeEmail(email);
        member.passwordHash = encodedPassword;
        member.nickname = nickname;
        member.name = name;
        member.status = MemberStatus.ACTIVE;
        member.role = MemberRole.USER;
        return member;
    }

    public static Member signUpWithSocial(String email, String nickname, String name) {
        Member member = new Member();
        member.id = Ids.newId();
        member.email = normalizeEmail(email);
        member.passwordHash = null;
        member.nickname = nickname;
        member.name = name;
        member.status = MemberStatus.ACTIVE;
        member.role = MemberRole.USER;
        return member;
    }

    public void verifyCanLogin() {
        if (status == MemberStatus.BANNED) {
            if (banReason == null) {
                throw new BusinessException(MemberErrorCode.MEMBER_BANNED);
            }
            throw new BusinessException(MemberErrorCode.MEMBER_BANNED, Map.of("reason", banReason));
        } else if (status == MemberStatus.WITHDRAWN) {
            throw new BusinessException(MemberErrorCode.LOGIN_FAILED);
        }
    }

    public void changeProfile(String nickname, String name, String phone) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (name != null) {
            this.name = name;
        }
        if (phone != null) {
            this.phone = phone;
        }
    }

    public void promoteToSeller() {
        if (this.role == MemberRole.USER) {
            this.role = MemberRole.SELLER;
        }
    }

    public void demoteToUser() {
        if (this.role == MemberRole.SELLER) {
            this.role = MemberRole.USER;
        }
    }

    public void withdraw(Instant now) {
        this.status = this.status.transitTo(MemberStatus.WITHDRAWN);
        this.email = "withdrawn+" + this.id + "@deleted.invalid";
        this.nickname = "탈퇴-" + this.id.toString().replace("-", "").substring(15);
        this.name = "탈퇴회원";
        this.phone = null;
        this.passwordHash = null;
        this.withdrawnAt = now;
    }

    public void changePassword(String encodedPassword) {
        this.passwordHash = encodedPassword;
    }

    public void increaseTokenVersion() {
        this.tokenVersion++;
    }

    public void ban(String reason, Instant now) {
        this.status = this.status.transitTo(MemberStatus.BANNED);
        this.banReason = reason;
        this.bannedAt = now;
    }

    public void unban() {
        this.status = this.status.transitTo(MemberStatus.ACTIVE);
        this.banReason = null;
        this.bannedAt = null;
    }
}
