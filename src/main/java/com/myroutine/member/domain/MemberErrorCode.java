package com.myroutine.member.domain;

import com.myroutine.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MemberErrorCode implements ErrorCode {
    MEMBER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    MEMBER_NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    MEMBER_BANNED(HttpStatus.FORBIDDEN, "이용이 제한된 회원입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    MEMBER_ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "배송지를 찾을 수 없습니다."),
    MEMBER_HAS_ACTIVE_RESOURCES(HttpStatus.UNPROCESSABLE_CONTENT, "탈퇴할 수 없는 상태입니다."),
    REFRESH_REUSED(HttpStatus.UNAUTHORIZED, "이미 사용된 토큰입니다. 다시 로그인해 주세요.");

    private final HttpStatus status;
    private final String message;

    MemberErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override public String code() { return name(); }
    @Override public HttpStatus status() { return status; }
    @Override public String message() { return message; }
}
