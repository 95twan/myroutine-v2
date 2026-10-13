package com.myroutine.order.domain;

public record ShippingAddress(
        String recipient,
        String phone,
        String zipcode,
        String address1,
        String address2
) {

}
