package com.myroutine.member.domain;

import com.myroutine.common.model.BaseTimeEntity;
import com.myroutine.common.model.Ids;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "member_address", schema = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class MemberAddress extends BaseTimeEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "recipient", nullable = false)
    private String recipient;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "zipcode", nullable = false)
    private String zipcode;

    @Column(name = "address1", nullable = false)
    private String address1;

    @Column(name = "address2")
    private String address2;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static MemberAddress register(UUID memberId, String recipient, String phone, String zipcode, String address1, String address2) {
        MemberAddress memberAddress = new MemberAddress();
        memberAddress.id = Ids.newId();
        memberAddress.memberId = memberId;
        memberAddress.recipient = recipient;
        memberAddress.phone = phone;
        memberAddress.zipcode = zipcode;
        memberAddress.address1 = address1;
        memberAddress.address2 = address2;
        memberAddress.isDefault = false;
        return memberAddress;
    }

    public void update(String recipient, String phone, String zipcode, String address1, String address2) {
        if (recipient != null) {
            this.recipient = recipient;
        }
        if (phone != null) {
            this.phone = phone;
        }
        if (zipcode != null) {
            this.zipcode = zipcode;
        }
        if (address1 != null) {
            this.address1 = address1;
        }
        if (address2 != null) {
            this.address2 = address2;
        }
    }

    public void markDefault() {
        this.isDefault = true;
    }

    public void unmarkDefault() {
        this.isDefault = false;
    }
}
