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
    REFRESH_REUSED(HttpStatus.UNAUTHORIZED, "이미 사용된 토큰입니다. 다시 로그인해 주세요."),
    EMAIL_CODE_INVALID(HttpStatus.BAD_REQUEST, "인증코드가 올바르지 않거나 만료되었습니다."),
    VERIFICATION_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "이메일 인증이 만료되었거나 올바르지 않습니다. 다시 인증해 주세요."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),
    OAUTH_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "소셜 로그인 제공자와 통신하지 못했습니다."),
    SIGNUP_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "가입 시간이 만료되었습니다. 다시 시도해 주세요."),
    SOCIAL_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 연결된 소셜 계정입니다."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "로그인 시도가 많아 잠시 잠겼습니다.");

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
