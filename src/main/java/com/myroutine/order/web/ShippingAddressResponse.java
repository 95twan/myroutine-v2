package com.myroutine.order.web;

import com.myroutine.order.application.ShippingAddressResult;

public record ShippingAddressResponse(
        String recipient,
        String phone,
        String zipcode,
        String address1,
        String address2
) {
    public static ShippingAddressResponse from(ShippingAddressResult result) {
        return new ShippingAddressResponse(
                result.recipient(),
                result.phone(),
                result.zipcode(),
                result.address1(),
                result.address2()
        );
    }
}
