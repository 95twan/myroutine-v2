package com.myroutine.member.web;

import com.myroutine.member.application.AddressCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank
        @Size(max = 50)
        String recipient,
        @NotBlank
        @Pattern(regexp="^[0-9-]{9,20}$")
        String phone,
        @NotBlank
        @Pattern(regexp="^\\d{5}$")
        String zipcode,
        @NotBlank
        @Size(max = 200)
        String address1,
        @Size(max = 200)
        String address2,
        Boolean isDefault
) {
    public AddressCommand toCommand() {
        return new AddressCommand(recipient, phone, zipcode, address1, address2, isDefault);
    }
}
