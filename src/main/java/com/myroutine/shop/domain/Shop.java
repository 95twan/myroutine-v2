package com.myroutine.shop.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.common.model.BaseTimeEntity;
import com.myroutine.common.model.Ids;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shop", schema = "shop")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Shop extends BaseTimeEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "business_number", nullable = false)
    private String businessNumber;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "address", nullable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShopStatus status;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    private Long version;

    public static Shop open(UUID memberId, String name, String businessNumber, String email, String phone, String address) {
        Shop shop = new Shop();
        shop.id = Ids.newId();
        shop.memberId = memberId;
        shop.name = name;
        shop.businessNumber = businessNumber;
        shop.email = email;
        shop.phone = phone;
        shop.address = address;
        shop.status = ShopStatus.ACTIVE;
        return shop;
    }

    public boolean isActive() {
        return status == ShopStatus.ACTIVE;
    }

    public void verifyOwner(UUID memberId) {
        if (!this.memberId.equals(memberId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    public void verifyActive() {
        if (!isActive()) {
            throw new BusinessException(ShopErrorCode.SHOP_NOT_ACTIVE);
        }
    }

    public void update(String name, String email, String phone, String address) {
        verifyActive();
        if (name != null) {
            this.name = name;
        }
        if (email != null) {
            this.email = email;
        }
        if (phone != null) {
            this.phone = phone;
        }
        if (address != null) {
            this.address = address;
        }
    }
}
