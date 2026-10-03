package com.myroutine.member.domain;

import com.myroutine.common.model.BaseTimeEntity;
import com.myroutine.common.model.Ids;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Locale;
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
}
