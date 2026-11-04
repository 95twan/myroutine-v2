package com.myroutine.search.application;

import com.myroutine.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SearchErrorCode implements ErrorCode {
    SEARCH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "검색을 일시적으로 사용할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    SearchErrorCode(HttpStatus status, String message) {
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
