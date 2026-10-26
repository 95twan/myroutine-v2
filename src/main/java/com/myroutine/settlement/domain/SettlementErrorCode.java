package com.myroutine.settlement.domain;

import com.myroutine.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SettlementErrorCode implements ErrorCode {
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "정산 내역을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    SettlementErrorCode(HttpStatus status, String message) {
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
