package com.myroutine.member.api;

import java.util.UUID;

public interface MemberApi {
    ShippingAddressInfo getAddress(UUID memberId, UUID addressId);

    MemberContact getContact(UUID memberId);
}
