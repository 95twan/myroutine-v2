package com.myroutine.order.application;

import com.myroutine.order.domain.ShippingAddress;

public record ShippingAddressResult(
        String recipient,
        String phone,
        String zipcode,
        String address1,
        String address2
) {
    public static ShippingAddressResult from(ShippingAddress shippingAddress) {
        return new ShippingAddressResult(
                shippingAddress.recipient(),
                shippingAddress.phone(),
                shippingAddress.zipcode(),
                shippingAddress.address1(),
                shippingAddress.address2()
        );
    }
}
