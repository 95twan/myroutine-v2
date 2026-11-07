package com.myroutine.product.application;

import com.myroutine.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.fail;

class StockGateTest extends IntegrationTestSupport {

    @Test
    @DisplayName("재고와 Redis 수량이 100일 때 서로 다른 주문 1,000건이 동시에 예약해도 정확히 100건만 성공한다.")
    void reserveConcurrentlyThroughGate() {
        fail("구현 필요");
    }

    @Test
    @DisplayName("재고 예약을 실패한다. (Redis 수량 부족)")
    void reserveFailsWhenGateShort() {
        fail("구현 필요");
    }

    @Test
    @DisplayName("게이트를 통과한 뒤 DB 재고가 모자라 예약이 실패하면 Redis 수량을 되돌린다.")
    void giveBackGateWhenDbRejects() {
        fail("구현 필요");
    }
}
