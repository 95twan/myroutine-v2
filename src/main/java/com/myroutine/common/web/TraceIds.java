package com.myroutine.common.web;

import com.myroutine.common.model.Ids;

public final class TraceIds {
    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "traceId";

    private TraceIds() {
    }

    public static String newId() {
        return Ids.newId().toString();
    }

}
