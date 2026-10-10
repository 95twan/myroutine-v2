package com.myroutine.member.web;

import com.myroutine.member.application.ChangeProfileCommand;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangeProfileRequest(
        @Pattern(regexp = ".*\\S.*", message = "공백만 입력할 수 없습니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2~20자입니다.")
        String nickname,
        @Pattern(regexp = ".*\\S.*", message = "공백만 입력할 수 없습니다.")
        @Size(min = 1, max = 50, message = "이름은 1~50자입니다.")
        String name,
        @Pattern(regexp = "^[0-9-]{9,20}$", message = "전화번호는 숫자와 하이픈으로 9~20자입니다.")
        String phone
) {
    public ChangeProfileCommand toCommand() {
        return new ChangeProfileCommand(nickname, name, phone);
    }
}
