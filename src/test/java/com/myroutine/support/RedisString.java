package com.myroutine.support;

public record RedisString(
        String value,
        long ttlSeconds
) {
    public boolean exists() {
        return value != null;
    }
}
