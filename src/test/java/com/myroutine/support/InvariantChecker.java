package com.myroutine.support;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Component
@RequiredArgsConstructor
public class InvariantChecker {

    private final JdbcTemplate jdbcTemplate;

    public void checkAll() {
        orderAmounts();
        paymentsReflected();
        stockBalance();
        refundLimits();
        settlementOnce();
    }

    public void orderAmounts() {
        assertNoViolation("주문 총액이 품목 금액 합과 다른 주문", """
                SELECT o.id
                FROM orders.orders o
                JOIN orders.shop_order so ON so.order_id = o.id
                JOIN orders.order_line ol ON ol.shop_order_id = so.id
                GROUP BY o.id, o.total_amount
                HAVING o.total_amount <> SUM(ol.line_amount)
                """);
        assertNoViolation("결제 금액이 총액과 다른 PAID 주문", """
                SELECT o.id
                FROM orders.orders o
                JOIN payment.payment p ON p.order_id = o.id
                WHERE o.status = 'PAID' AND p.amount <> o.total_amount
                """);
    }

    public void paymentsReflected() {
        assertNoViolation("승인된 결제가 반영되지 않은 주문", """
                SELECT o.id
                FROM payment.payment p
                JOIN orders.orders o ON o.id = p.order_id
                WHERE p.status IN ('APPROVED', 'PARTIAL_CANCELLED', 'CANCELLED')
                  AND o.status <> 'PAID'
                  AND NOT (p.status = 'CANCELLED' AND o.status = 'PAYMENT_FAILED')
                """);
    }

    public void stockBalance() {
        assertNoViolation("재고 등식이 깨진 상품", """
                SELECT s.product_id
                FROM product.stock s
                LEFT JOIN (SELECT product_id, SUM(quantity) q FROM product.stock_movement WHERE type IN ('RECEIVE', 'ADJUST') GROUP BY product_id) m ON m.product_id = s.product_id
                LEFT JOIN (SELECT product_id, SUM(quantity) q FROM product.stock_reservation WHERE status = 'HELD' GROUP BY product_id) r ON r.product_id = s.product_id
                WHERE s.available + s.reserved + s.sold <> s.received
                   OR s.available < 0 OR s.reserved < 0 OR s.sold < 0
                   OR s.received <> COALESCE(m.q, 0)
                   OR s.reserved <> COALESCE(r.q, 0)
                """);
    }

    public void refundLimits() {
        assertNoViolation("환불액이 품목 금액을 넘은 품목", """
                SELECT id FROM orders.order_line WHERE refunded_amount > line_amount
                """);
        assertNoViolation("취소액이 승인액을 넘은 결제의 주문", """
                SELECT order_id FROM payment.payment WHERE cancelled_amount > amount
                """);
        assertNoViolation("완료된 환불 합이 총액을 넘은 주문", """
                SELECT o.id
                FROM orders.orders o
                JOIN orders.refund r ON r.order_id = o.id
                WHERE r.status = 'COMPLETED'
                GROUP BY o.id, o.total_amount
                HAVING SUM(r.amount) > o.total_amount
                """);
    }

    public void settlementOnce() {
        assertNoViolation("정산 항목이 없는 구매확정 품목", """
                SELECT ol.id
                FROM orders.order_line ol
                LEFT JOIN settlement.settlement_item si ON si.order_line_id = ol.id
                WHERE ol.status = 'CONFIRMED' AND si.id IS NULL
                """);
    }

    private void assertNoViolation(String description, String sql) {
        List<UUID> ids = jdbcTemplate.queryForList(sql, UUID.class);
        assertThat(ids).as(description).isEmpty();
    }
}
