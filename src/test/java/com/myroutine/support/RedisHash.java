package com.myroutine.support;

import java.util.Map;

public record RedisHash(
        Map<String, String> fields,
        long ttlSeconds
) {
    public boolean exists() {
        return !fields.isEmpty();
    }
}
