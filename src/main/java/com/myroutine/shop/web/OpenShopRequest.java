package com.myroutine.shop.web;

import com.myroutine.shop.application.OpenShopCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OpenShopRequest(
        @NotBlank(message = "필수 값입니다.")
        @Size(max = 50, message = "가게 이름은 50자 이하여야 합니다.")
        String name,
        @NotBlank(message = "필수 값입니다.")
        @Pattern(regexp = "^\\d{10}$", message = "사업자번호는 하이픈 없는 10자리 숫자입니다.")
        String businessNumber,
        @NotBlank(message = "필수 값입니다.")
        @Email(message = "이메일 형식이 아닙니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,
        @NotBlank(message = "필수 값입니다.")
        @Pattern(regexp = "^[0-9-]{9,20}$", message = "전화번호는 숫자와 하이픈으로 9~20자입니다.")
        String phone,
        @NotBlank(message = "필수 값입니다.")
        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        String address
) {
        public OpenShopCommand toCommand() {
                return new OpenShopCommand(name, businessNumber, email, phone, address);
        }
}
