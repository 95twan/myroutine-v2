package com.myroutine.member.application;

import com.myroutine.member.domain.MemberAddress;

import java.time.Instant;
import java.util.UUID;

public record AddressResult(
        UUID id,
        String recipient,
        String phone,
        String zipcode,
        String address1,
        String address2,
        Boolean isDefault,
        Instant createdAt
) {
    public static AddressResult from(MemberAddress memberAddress) {
        return new AddressResult(
                memberAddress.getId(),
                memberAddress.getRecipient(),
                memberAddress.getPhone(),
                memberAddress.getZipcode(),
                memberAddress.getAddress1(),
                memberAddress.getAddress2(),
                memberAddress.isDefault(),
                memberAddress.getCreatedAt()
        );
    }
}
