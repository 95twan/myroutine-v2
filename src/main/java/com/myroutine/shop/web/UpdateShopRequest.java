package com.myroutine.shop.web;

import com.myroutine.shop.application.UpdateShopCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateShopRequest(
        @Pattern(regexp = ".*\\S.*", message = "공백만 입력할 수 없습니다.")
        @Size(max = 50, message = "가게 이름은 50자 이하여야 합니다.")
        String name,
        @Pattern(regexp = ".*\\S.*", message = "공백만 입력할 수 없습니다.")
        @Email(message = "이메일 형식이 아닙니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,
        @Pattern(regexp = "^[0-9-]{9,20}$", message = "전화번호는 숫자와 하이픈으로 9~20자입니다.")
        String phone,
        @Pattern(regexp = ".*\\S.*", message = "공백만 입력할 수 없습니다.")
        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        String address
) {
    public UpdateShopCommand toCommand() {
        return new UpdateShopCommand(name, email, phone, address);
    }
}
