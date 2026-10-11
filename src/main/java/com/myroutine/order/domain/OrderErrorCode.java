package com.myroutine.order.domain;

import com.myroutine.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum OrderErrorCode implements ErrorCode {
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니 항목을 찾을 수 없습니다."),
    CART_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    OrderErrorCode(HttpStatus status, String message) {
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
