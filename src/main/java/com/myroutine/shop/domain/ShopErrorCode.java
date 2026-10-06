package com.myroutine.shop.domain;

import com.myroutine.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ShopErrorCode implements ErrorCode {
    SHOP_NOT_FOUND(HttpStatus.NOT_FOUND, "가게를 찾을 수 없습니다."),
    SHOP_BUSINESS_NUMBER_DUPLICATED(HttpStatus.CONFLICT, "이미 등록된 사업자번호입니다."),
    SHOP_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_CONTENT, "운영 중인 가게가 아닙니다.");

    private final HttpStatus status;
    private final String message;

    ShopErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String message() {
        return message;
    }
}
