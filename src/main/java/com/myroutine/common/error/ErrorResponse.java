package com.myroutine.common.error;

import com.myroutine.common.web.TraceIds;
import org.slf4j.MDC;

import java.util.Map;

public record ErrorResponse(
        String code,
        String message,
        String traceId,
        Map<String, Object> details
) {
    public static ErrorResponse of(ErrorCode errorCode, Map<String, Object> details) {
        String traceId = MDC.get(TraceIds.MDC_KEY);
        return new ErrorResponse(errorCode.code(), errorCode.message(), traceId, details);
    }
}
