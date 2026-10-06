package com.myroutine.shop.web;

import com.myroutine.shop.application.OpenShopCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OpenShopRequest(
        @NotBlank
        @Size(max = 50)
        String name,
        @NotBlank
        @Pattern(regexp = "^\\d{10}$")
        String businessNumber,
        @NotBlank
        @Email
        @Size(max = 255)
        String email,
        @NotBlank
        @Pattern(regexp = "^[0-9-]{9,20}$")
        String phone,
        @NotBlank
        @Size(max = 255)
        String address
) {
        public OpenShopCommand toCommand() {
                return new OpenShopCommand(name, businessNumber, email, phone, address);
        }
}
