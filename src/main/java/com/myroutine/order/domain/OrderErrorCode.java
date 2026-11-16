package com.myroutine.order.domain;

import com.myroutine.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum OrderErrorCode implements ErrorCode {
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장바구니 항목을 찾을 수 없습니다."),
    CART_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    OWN_SHOP_PRODUCT(HttpStatus.UNPROCESSABLE_CONTENT, "자기 가게 상품은 주문할 수 없습니다."),
    ORDER_EXPIRED(HttpStatus.CONFLICT, "주문 유효시간이 지났습니다."),
    ORDER_ALREADY_PAID(HttpStatus.CONFLICT, "이미 결제된 주문입니다."),
    SHOP_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "가게 주문을 찾을 수 없습니다."),
    ORDER_LINE_NOT_FOUND(HttpStatus.NOT_FOUND, "주문 품목을 찾을 수 없습니다."),
    REFUND_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 환불이 있습니다."),
    REFUND_NOT_FOUND(HttpStatus.NOT_FOUND, "환불 요청을 찾을 수 없습니다."),
    RETURN_NOT_ALLOWED(HttpStatus.CONFLICT, "반품을 요청할 수 없는 품목입니다."),
    SHOP_HAS_ACTIVE_ORDERS(HttpStatus.UNPROCESSABLE_CONTENT, "진행 중인 주문이 있어 폐업할 수 없습니다."),
    REVIEW_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT, "구매확정한 상품만 리뷰를 쓸 수 있습니다."),
    PENDING_ORDER_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "결제 대기 중인 주문이 많습니다. 결제를 마치거나 취소한 뒤, 또는 잠시 뒤 다시 시도해 주세요."),
    UNPAID_ORDER_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS,  "결제하지 않은 주문이 많아 잠시 주문할 수 없습니다.");

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
