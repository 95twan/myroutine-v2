package com.myroutine.common.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BusinessException e) {
        ErrorResponse response = ErrorResponse.of(e.getErrorCode(), e.getDetails());
        log.warn("{}", response);
        return ResponseEntity.status(e.getErrorCode().status()).body(response);
    }

    // @Valid 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        ErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;
        Map<String, Object> details = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> Optional.ofNullable(fieldError.getDefaultMessage()).orElse("Invalid value"),
                        (existing, replacement) -> existing // 중복 키 발생 시 기존 값 유지
                ));

        ErrorResponse response = ErrorResponse.of(errorCode, details);
        log.warn("{}", response);
        return ResponseEntity.status(errorCode.status()).body(response);
    }

    // JSON 문법 오류, 타입 불일치, 없는 enum 값
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        ErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;
        ErrorResponse response = ErrorResponse.of(errorCode, Map.of());
        log.warn("{}", response);
        return ResponseEntity.status(errorCode.status()).body(response);
    }

    // 경로 변수·쿼리 파라미터 타입 오류, 예: UUID 자리에 abc
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        ErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;
        ErrorResponse response = ErrorResponse.of(errorCode, Map.of(e.getName(), "형식이 올바르지 않습니다."));
        log.warn("{}", response);
        return ResponseEntity.status(errorCode.status()).body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParameterException(MissingServletRequestParameterException e) {
        ErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;
        ErrorResponse response = ErrorResponse.of(errorCode, Map.of(e.getParameterName(), "필수 값입니다."));
        log.warn("{}", response);
        return ResponseEntity.status(errorCode.status()).body(response);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        ErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;
        Map<String, Object> details = e.getValueResults().stream()
                .collect(Collectors.toMap(
                        r -> r.getMethodParameter().getParameterName(),
                        r -> Optional.ofNullable(r.getResolvableErrors().getFirst().getDefaultMessage()).orElse("올바르지 않은 값입니다."),
                        (existing, replacement) -> existing
                ));
        ErrorResponse response = ErrorResponse.of(errorCode, details);
        log.warn("{}", response);
        return ResponseEntity.status(errorCode.status()).body(response);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParameterException(MissingRequestHeaderException e) {
        ErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;
        ErrorResponse response = ErrorResponse.of(errorCode, Map.of(e.getHeaderName(), "필수 값입니다."));
        log.warn("{}", response);
        return ResponseEntity.status(errorCode.status()).body(response);
    }

    // 없는 경로
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFoundException(NoResourceFoundException e) {
        ErrorCode errorCode = CommonErrorCode.NOT_FOUND;
        ErrorResponse response = ErrorResponse.of(errorCode, Map.of());
        log.warn("{}", response);
        return ResponseEntity.status(errorCode.status()).body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        ErrorCode errorCode = CommonErrorCode.METHOD_NOT_ALLOWED;
        ErrorResponse response = ErrorResponse.of(errorCode, Map.of());
        log.warn("{}", response);
        return ResponseEntity.status(errorCode.status()).body(response);
    }

    // @Version 충돌
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailureException(OptimisticLockingFailureException e) {
        ErrorCode errorCode = CommonErrorCode.CONFLICT_RETRY;
        ErrorResponse response = ErrorResponse.of(errorCode, Map.of());
        log.warn("{}", response);
        return ResponseEntity.status(errorCode.status()).body(response);
    }

    // unique 위반
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        ErrorCode errorCode;
        ErrorResponse response;

        if (isUniqueViolation(e)) {
            errorCode = CommonErrorCode.DUPLICATE_RESOURCE;
            response = ErrorResponse.of(errorCode, Map.of());
            log.warn("{}", response);
            return ResponseEntity.status(errorCode.status()).body(response);
        }
        errorCode = CommonErrorCode.INTERNAL_ERROR;
        response = ErrorResponse.of(errorCode, Map.of());
        log.error("{}\nunexpected error", response, e);
        return ResponseEntity.status(errorCode.status()).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        ErrorCode errorCode = CommonErrorCode.INTERNAL_ERROR;
        ErrorResponse response = ErrorResponse.of(errorCode, Map.of());
        log.error("{}\nunexpected error", response, e);
        return ResponseEntity.status(errorCode.status()).body(response);
    }

    private boolean isUniqueViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
