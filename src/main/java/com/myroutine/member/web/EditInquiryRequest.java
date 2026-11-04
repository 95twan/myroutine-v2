package com.myroutine.member.web;

import com.myroutine.member.application.EditInquiryCommand;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EditInquiryRequest(
        @Pattern(regexp = ".*\\S.*", message = "공백만 입력할 수 없습니다.") @Size(max = 100, message = "제목은 100자 이하여야 합니다.") String title,
        @Pattern(regexp = "(?s).*\\S.*", message = "공백만 입력할 수 없습니다.") @Size(max = 2000, message = "내용은 2000자 이하여야 합니다.") String content
) {
    public EditInquiryCommand toCommand() {
        return new EditInquiryCommand(title, content);
    }
}
