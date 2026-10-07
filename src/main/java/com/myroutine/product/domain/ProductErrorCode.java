package com.myroutine.product.domain;

import com.myroutine.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ProductErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    ProductErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override public String code() { return name(); }
    @Override public HttpStatus status() { return status; }
    @Override public String message() { return message; }
}
