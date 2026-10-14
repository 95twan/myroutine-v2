package com.myroutine.support;

import java.util.Map;

public record RecordedRequest(
        String method,
        String path,
        Map<String, String> headers,
        String body
) {
}
