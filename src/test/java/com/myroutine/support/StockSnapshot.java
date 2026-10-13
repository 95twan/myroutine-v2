package com.myroutine.support;

public record StockSnapshot(
        int available,
        int reserved,
        int sold
) {
}
