package com.myroutine.order.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShipRequest(
        @NotBlank(message = "필수 값입니다.") @Size(max=30, message = "택배사는 30자 이하여야 합니다.") String carrier,
        @NotBlank(message = "필수 값입니다.") @Pattern(regexp="^[0-9-]{8,30}$", message = "송장번호는 숫자와 하이픈으로 8~30자입니다.") String trackingNumber
) {
}
