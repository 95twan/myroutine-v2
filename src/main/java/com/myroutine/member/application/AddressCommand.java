package com.myroutine.member.application;

public record AddressCommand(
        String recipient,
        String phone,
        String zipcode,
        String address1,
        String address2,
        Boolean isDefault
) {
}
