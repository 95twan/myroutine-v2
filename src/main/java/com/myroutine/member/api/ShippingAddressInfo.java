package com.myroutine.member.api;

import com.myroutine.member.domain.MemberAddress;

public record ShippingAddressInfo(
        String recipient,
        String phone,
        String zipcode,
        String address1,
        String address2
) {
    public static ShippingAddressInfo from(MemberAddress memberAddress) {
        return new ShippingAddressInfo(
                memberAddress.getRecipient(),
                memberAddress.getPhone(),
                memberAddress.getZipcode(),
                memberAddress.getAddress1(),
                memberAddress.getAddress2()
        );
    }
}
