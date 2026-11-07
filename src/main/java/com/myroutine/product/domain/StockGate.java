package com.myroutine.product.domain;

import java.util.List;
import java.util.UUID;

public interface StockGate {
    int tryAcquire(List<UUID> productIds, List<Integer> quantities);
    void giveBack(List<UUID> productIds, List<Integer> quantities);
}
