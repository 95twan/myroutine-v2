package com.myroutine.shop.web;

import com.myroutine.shop.application.UpdateShopCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateShopRequest(
        @Pattern(regexp = ".*\\S.*")
        @Size(max = 50)
        String name,
        @Pattern(regexp = ".*\\S.*")
        @Email
        @Size(max = 255)
        String email,
        @Pattern(regexp = "^[0-9-]{9,20}$")
        String phone,
        @Pattern(regexp = ".*\\S.*")
        @Size(max = 255)
        String address
) {
    public UpdateShopCommand toCommand() {
        return new UpdateShopCommand(name, email, phone, address);
    }
}
