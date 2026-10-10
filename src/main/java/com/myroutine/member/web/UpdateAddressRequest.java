package com.myroutine.member.web;

import com.myroutine.member.application.AddressCommand;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAddressRequest(
        @Pattern(regexp = ".*\\S.*")
        @Size(max = 50)
        String recipient,
        @Pattern(regexp = "^[0-9-]{9,20}$")
        String phone,
        @Pattern(regexp = "^\\d{5}$")
        String zipcode,
        @Pattern(regexp = ".*\\S.*")
        @Size(max = 200)
        String address1,
        @Pattern(regexp = "^$|.*\\S.*")
        @Size(max = 200)
        String address2,
        Boolean isDefault
) {
    public AddressCommand toCommand() {
        return new AddressCommand(recipient, phone, zipcode, address1, address2, isDefault);
    }
}
