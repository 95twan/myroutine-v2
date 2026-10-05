package com.myroutine.member.web;

import com.myroutine.member.application.AddressResult;

import java.time.Instant;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        String recipient,
        String phone,
        String zipcode,
        String address1,
        String address2,
        boolean isDefault,
        Instant createdAt
) {
    public static AddressResponse from(AddressResult result) {
        return new AddressResponse(
                result.id(),
                result.recipient(),
                result.phone(),
                result.zipcode(),
                result.address1(),
                result.address2(),
                result.isDefault(),
                result.createdAt()
        );
    }
}
