package com.myroutine.member.web;

import com.myroutine.member.application.AddressCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank(message = "필수 값입니다.")
        @Size(max = 50, message = "수령인은 50자 이하여야 합니다.")
        String recipient,
        @NotBlank(message = "필수 값입니다.")
        @Pattern(regexp = "^[0-9-]{9,20}$", message = "전화번호는 숫자와 하이픈으로 9~20자입니다.")
        String phone,
        @NotBlank(message = "필수 값입니다.")
        @Pattern(regexp = "^\\d{5}$", message = "우편번호는 5자리 숫자입니다.")
        String zipcode,
        @NotBlank(message = "필수 값입니다.")
        @Size(max = 200, message = "주소는 200자 이하여야 합니다.")
        String address1,
        @Size(max = 200, message = "상세주소는 200자 이하여야 합니다.")
        String address2,
        Boolean isDefault
) {
    public AddressCommand toCommand() {
        return new AddressCommand(recipient, phone, zipcode, address1, address2, isDefault);
    }
}
