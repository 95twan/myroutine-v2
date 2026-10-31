package com.myroutine.support;

public record TokenVersionSnapshot(
        int db,
        Integer redis,
        long redisTtlSeconds
) {
}
