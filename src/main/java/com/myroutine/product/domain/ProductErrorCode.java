package com.myroutine.product.domain;

import com.myroutine.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ProductErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    PRODUCT_DISCONTINUED(HttpStatus.CONFLICT, "단종된 상품은 수정할 수 없습니다."),
    OUT_OF_STOCK(HttpStatus.UNPROCESSABLE_CONTENT, "재고가 부족합니다."),
    PRODUCT_NOT_ON_SALE(HttpStatus.UNPROCESSABLE_CONTENT, "판매 중인 상품이 아닙니다."),
    PRODUCT_IMAGE_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "상품 이미지는 최대 10장입니다."),
    IMAGE_NOT_UPLOADED(HttpStatus.UNPROCESSABLE_CONTENT, "이미지가 업로드되지 않았습니다."),
    PRODUCT_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다.");

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
