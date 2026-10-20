package com.myroutine.order.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.order.domain.OrderStatus;
import com.myroutine.payment.api.*;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

class OrderReconcileServiceTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final JdbcTemplate jdbcTemplate;
    private final OrderReconcileService orderReconcileService;
    private final TransactionTemplate tx;
    private final PaymentApi paymentApi;
    private final OrderPaymentApplier applier;

    @Autowired
    public OrderReconcileServiceTest(
            TestFixtures testFixtures,
            JdbcTemplate jdbcTemplate,
            OrderReconcileService orderReconcileService,
            TransactionTemplate tx,
            PaymentApi paymentApi,
            OrderPaymentApplier applier
    ) {
        this.testFixtures = testFixtures;
        this.jdbcTemplate = jdbcTemplate;
        this.orderReconcileService = orderReconcileService;
        this.tx = tx;
        this.paymentApi = paymentApi;
        this.applier = applier;
    }

    @Test
    @DisplayName("승인 응답이 타임아웃 났지만 PG에 승인 기록이 있으면 대사로 주문이 PAID가 되고 주문한 수량만큼 장바구니에서 빠진다.")
    void reconcileApprovedAfterTimeout() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, productId);
        // 가짜 PG는 승인을 처리한 뒤 응답을 늦게 보낸다. 지연은 테스트 read-timeout(500ms)보다 길어야 타임아웃이 난다
        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        ConfirmResult result = testFixtures.confirmPayment(buyerId, order.orderId());
        UUID orderId = result.orderId();

        Instant updatedAt = Instant.now().minusSeconds(60);
        testFixtures.setOrderUpdatedAt(orderId, updatedAt);

        // When
        int reconciled = orderReconcileService.reconcileBatch();

        // Then
        assertThat(reconciled).isEqualTo(1);

        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders.orders WHERE id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("PAID");

        String paymentStatus = jdbcTemplate.queryForObject("SELECT status FROM payment.payment WHERE order_id = ?", String.class, orderId);
        assertThat(paymentStatus).isEqualTo("APPROVED");

        StockSnapshot stock = testFixtures.stockOf(productId);
        assertThat(stock.available()).isEqualTo(99);
        assertThat(stock.reserved()).isEqualTo(0);
        assertThat(stock.sold()).isEqualTo(1);

        Integer cartItemCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders.cart_item WHERE product_id = ?", Integer.class, productId);
        assertThat(cartItemCount).isEqualTo(0);

        List<RecordedRequest> requests = pg.requests("/v1/payments/orders/");
        assertThat(requests).hasSize(1);
    }

    @Test
    @DisplayName("승인 요청이 PG에서 처리되지 않았으면(응답 없이 연결이 끊김) 대사로 주문이 PAYMENT_FAILED가 되고 재고가 원복되며 장바구니는 그대로다.")
    void reconcileNotFoundAfterDroppedRequest() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, productId);

        pg.on("/v1/payments/confirm", Behavior.drop());
        ConfirmResult result = testFixtures.confirmPayment(buyerId, order.orderId());
        UUID orderId = result.orderId();

        Instant updatedAt = Instant.now().minusSeconds(60);
        testFixtures.setOrderUpdatedAt(orderId, updatedAt);

        // When
        int reconciled = orderReconcileService.reconcileBatch();

        // Then
        assertThat(reconciled).isEqualTo(1);

        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders.orders WHERE id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("PAYMENT_FAILED");

        Map<String, Object> paymentStatus = jdbcTemplate.queryForMap("SELECT status, failure_code FROM payment.payment WHERE order_id = ?", orderId);
        assertThat(paymentStatus.get("status")).isEqualTo("FAILED");
        assertThat(paymentStatus.get("failure_code")).isEqualTo("NOT_FOUND_PAYMENT");

        StockSnapshot stock = testFixtures.stockOf(productId);
        assertThat(stock.available()).isEqualTo(100);
        assertThat(stock.reserved()).isEqualTo(0);
        assertThat(stock.sold()).isEqualTo(0);

        Integer cartItemCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders.cart_item WHERE product_id = ?", Integer.class, productId);
        assertThat(cartItemCount).isEqualTo(1);

        assertThat(pg.requests("/v1/payments/confirm")).hasSize(1);
        assertThat(pg.requests("/v1/payments/orders/")).hasSize(1);
    }

    @Test
    @DisplayName("결제가 이미 승인으로 확정돼 있으면 PG를 조회하지 않고 주문이 PAID가 된다.")
    void reconcileAlreadyApprovedPayment() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, productId);

        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        ConfirmResult result = testFixtures.confirmPayment(buyerId, order.orderId());

        UUID orderId = result.orderId();

        jdbcTemplate.update("UPDATE payment.payment SET status = 'APPROVED', method = '카드', approved_at = ? WHERE order_id = ?", Timestamp.from(Instant.now()), orderId);

        Instant updatedAt = Instant.now().minusSeconds(60);
        testFixtures.setOrderUpdatedAt(orderId, updatedAt);

        // When
        int reconciled = orderReconcileService.reconcileBatch();

        // Then
        assertThat(reconciled).isEqualTo(1);

        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders.orders WHERE id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("PAID");

        String paymentStatus = jdbcTemplate.queryForObject("SELECT status FROM payment.payment WHERE order_id = ?", String.class, orderId);
        assertThat(paymentStatus).isEqualTo("APPROVED");

        StockSnapshot stock = testFixtures.stockOf(productId);
        assertThat(stock.available()).isEqualTo(99);
        assertThat(stock.reserved()).isEqualTo(0);
        assertThat(stock.sold()).isEqualTo(1);

        Integer cartItemCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders.cart_item WHERE product_id = ?", Integer.class, productId);
        assertThat(cartItemCount).isEqualTo(0);

        assertThat(pg.requests("/v1/payments/orders/")).isEmpty();
    }

    @Test
    @DisplayName("결제가 이미 거절로 확정돼 있으면 PG를 조회하지 않고 주문이 PAYMENT_FAILED가 되며 재고가 원복된다.")
    void reconcileAlreadyFailedPayment() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, productId);

        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        ConfirmResult result = testFixtures.confirmPayment(buyerId, order.orderId());

        UUID orderId = result.orderId();

        jdbcTemplate.update("UPDATE payment.payment SET status = 'FAILED', failure_code = 'PG_REJECT' WHERE order_id = ?", orderId);

        Instant updatedAt = Instant.now().minusSeconds(60);
        testFixtures.setOrderUpdatedAt(orderId, updatedAt);

        // When
        int reconciled = orderReconcileService.reconcileBatch();

        // Then
        assertThat(reconciled).isEqualTo(1);

        Map<String, Object> orderRow = jdbcTemplate.queryForMap("SELECT status, failure_reason FROM orders.orders WHERE id = ?", orderId);
        assertThat(orderRow.get("status")).isEqualTo("PAYMENT_FAILED");
        assertThat(orderRow.get("failure_reason")).isEqualTo("PG 거절: PG_REJECT");

        Map<String, Object> payment = jdbcTemplate.queryForMap("SELECT status, failure_code FROM payment.payment WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).isEqualTo("FAILED");
        assertThat(payment.get("failure_code")).isEqualTo("PG_REJECT");

        StockSnapshot stock = testFixtures.stockOf(productId);
        assertThat(stock.available()).isEqualTo(100);
        assertThat(stock.reserved()).isEqualTo(0);
        assertThat(stock.sold()).isEqualTo(0);

        Integer cartItemCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders.cart_item WHERE product_id = ?", Integer.class, productId);
        assertThat(cartItemCount).isEqualTo(1);

        assertThat(pg.requests("/v1/payments/orders/")).isEmpty();
    }

    @Test
    @DisplayName("결제가 이미 취소돼 있으면 주문이 PAYMENT_FAILED가 되고 재고가 원복되며 PG를 조회하지 않는다.")
    void reconcileAlreadyCancelledPayment() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, productId);

        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        ConfirmResult result = testFixtures.confirmPayment(buyerId, order.orderId());

        UUID orderId = result.orderId();

        jdbcTemplate.update("UPDATE payment.payment SET status = 'CANCELLED', cancelled_amount = amount WHERE order_id = ?", orderId);

        Instant updatedAt = Instant.now().minusSeconds(60);
        testFixtures.setOrderUpdatedAt(orderId, updatedAt);

        // When
        int reconciled = orderReconcileService.reconcileBatch();

        // Then
        assertThat(reconciled).isEqualTo(1);

        Map<String, Object> orderRow = jdbcTemplate.queryForMap("SELECT status, failure_reason FROM orders.orders WHERE id = ?", orderId);
        assertThat(orderRow.get("status")).isEqualTo("PAYMENT_FAILED");
        assertThat(orderRow.get("failure_reason")).isEqualTo("결제 완료 처리 실패로 자동 취소");

        String paymentStatus = jdbcTemplate.queryForObject("SELECT status FROM payment.payment WHERE order_id = ?", String.class, orderId);
        assertThat(paymentStatus).isEqualTo("CANCELLED");

        StockSnapshot stock = testFixtures.stockOf(productId);
        assertThat(stock.available()).isEqualTo(100);
        assertThat(stock.reserved()).isEqualTo(0);
        assertThat(stock.sold()).isEqualTo(0);

        Integer cartItemCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders.cart_item WHERE product_id = ?", Integer.class, productId);
        assertThat(cartItemCount).isEqualTo(1);

        assertThat(pg.requests("/v1/payments/orders/")).isEmpty();
    }

    @Test
    @DisplayName("조회도 실패하면 주문은 그대로이고 reconcile_attempts만 실행할 때마다 1씩 늘어난다.")
    void reconcileQueryFailureOnlyIncreasesAttempts() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, productId);

        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        ConfirmResult result = testFixtures.confirmPayment(buyerId, order.orderId());

        UUID orderId = result.orderId();

        Instant updatedAt = Instant.now().minusSeconds(60);
        testFixtures.setOrderUpdatedAt(orderId, updatedAt);

        pg.on("/v1/payments/orders", Behavior.serverError());

        // When & Then
        int reconciled = orderReconcileService.reconcileBatch();

        assertThat(reconciled).isEqualTo(0);

        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders.orders WHERE id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("PAYMENT_IN_PROGRESS");

        Map<String, Object> payment = jdbcTemplate.queryForMap("SELECT status, reconcile_attempts FROM payment.payment WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).isEqualTo("UNKNOWN");
        assertThat(payment.get("reconcile_attempts")).isEqualTo(1);

        StockSnapshot stock = testFixtures.stockOf(productId);
        assertThat(stock.available()).isEqualTo(99);
        assertThat(stock.reserved()).isEqualTo(1);
        assertThat(stock.sold()).isEqualTo(0);

        Integer cartItemCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders.cart_item WHERE product_id = ?", Integer.class, productId);
        assertThat(cartItemCount).isEqualTo(1);

        assertThat(pg.requests("/v1/payments/orders/")).hasSize(1);

        reconciled = orderReconcileService.reconcileBatch();
        assertThat(reconciled).isEqualTo(0);

        orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders.orders WHERE id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("PAYMENT_IN_PROGRESS");

        payment = jdbcTemplate.queryForMap("SELECT status, reconcile_attempts FROM payment.payment WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).isEqualTo("UNKNOWN");
        assertThat(payment.get("reconcile_attempts")).isEqualTo(2);

        assertThat(pg.requests("/v1/payments/orders/")).hasSize(2);
    }

    @Test
    @DisplayName("같은 주문에 결과 반영을 동시에 두 번 해도 재고 sold는 한 번만 늘고 재고 이력 COMMIT은 1건이며 주문은 PAID다.")
    void applyOutcomeConcurrently() throws InterruptedException {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, productId);

        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        ConfirmResult result = testFixtures.confirmPayment(buyerId, order.orderId());
        UUID orderId = result.orderId();

        PaymentView view = paymentApi.find(orderId).get();

        // When
        Callable<OrderStatus> task = () -> {
            PaymentState state = tx.execute(s -> paymentApi.applyOutcome(view.paymentId(), new PgOutcome(PgOutcomeType.APPROVED, null, "카드", Instant.now())));
            return tx.execute(s -> applier.applyPaymentState(orderId, state, null));
        };
        List<ConcurrencyRunner.Result<OrderStatus>> results = ConcurrencyRunner.run(2, task);

        // Then
        List<ConcurrencyRunner.Result<OrderStatus>> succeeded = results.stream().filter(ConcurrencyRunner.Result::ok).toList();
        assertThat(succeeded).isNotEmpty();
        assertThat(succeeded).allSatisfy(r -> assertThat(r.value()).isEqualTo(OrderStatus.PAID));

        List<Throwable> errors = results.stream().filter(r -> !r.ok()).map(ConcurrencyRunner.Result::error).toList();
        assertThat(errors).allSatisfy(e -> assertThat(e).satisfiesAnyOf(
                x -> assertThat(x).isInstanceOf(OptimisticLockingFailureException.class),
                x -> assertThat(x).isInstanceOfSatisfying(BusinessException.class,
                        b -> assertThat(b.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION))
        ));

        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM orders.orders WHERE id = ?", String.class, orderId);
        assertThat(orderStatus).isEqualTo("PAID");

        String paymentStatus = jdbcTemplate.queryForObject("SELECT status FROM payment.payment WHERE order_id = ?", String.class, orderId);
        assertThat(paymentStatus).isEqualTo("APPROVED");

        StockSnapshot stock = testFixtures.stockOf(productId);
        assertThat(stock.available()).isEqualTo(99);
        assertThat(stock.reserved()).isEqualTo(0);
        assertThat(stock.sold()).isEqualTo(1);

        Integer stockMovementCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product.stock_movement WHERE product_id = ? and type = 'COMMIT'", Integer.class, productId);
        assertThat(stockMovementCount).isEqualTo(1);

        Integer cartItemCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders.cart_item WHERE product_id = ?", Integer.class, productId);
        assertThat(cartItemCount).isEqualTo(0);
    }

    @Test
    @DisplayName("결제가 이미 승인으로 확정됐는데 주문 완료가 불가하면 PG 승인을 취소하고 주문이 PAYMENT_FAILED가 되며 PG를 조회하지 않는다.")
    void compensateWhenCompletionFailsAfterAlreadyApproved() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, productId);

        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        ConfirmResult result = testFixtures.confirmPayment(buyerId, order.orderId());
        UUID orderId = result.orderId();

        jdbcTemplate.update("UPDATE payment.payment SET status = 'APPROVED', method = '카드', approved_at = ? WHERE order_id = ?", Timestamp.from(Instant.now().minus(Duration.ofMinutes(10))), orderId);
        jdbcTemplate.update("UPDATE orders.shop_order SET status = 'CANCELLED' WHERE order_id = ?", orderId);

        Instant updatedAt = Instant.now().minusSeconds(60);
        testFixtures.setOrderUpdatedAt(orderId, updatedAt);

        // When
        int reconciled = orderReconcileService.reconcileBatch();

        // Then
        assertThat(reconciled).isEqualTo(1);

        Map<String, Object> orderRow = jdbcTemplate.queryForMap("SELECT status, failure_reason FROM orders.orders WHERE id = ?", orderId);
        assertThat(orderRow.get("status")).isEqualTo("PAYMENT_FAILED");
        assertThat(orderRow.get("failure_reason")).isEqualTo("결제 완료 처리 실패로 자동 취소");

        Map<String, Object> payment = jdbcTemplate.queryForMap("SELECT payment_key, status, cancelled_amount, amount FROM payment.payment WHERE order_id = ?", orderId);
        assertThat(payment.get("status")).isEqualTo("CANCELLED");
        assertThat(payment.get("amount")).isEqualTo(payment.get("cancelled_amount"));

        Map<String, Object> paymentCancel = jdbcTemplate.queryForMap("SELECT status FROM payment.payment_cancel WHERE idempotency_key = ?", "order-compensate-" + orderId);
        assertThat(paymentCancel.get("status")).isEqualTo("DONE");

        String paymentKey = (String) payment.get("payment_key");

        List<RecordedRequest> requests = pg.requests("/v1/payments/" + paymentKey + "/cancel");
        assertThat(requests).hasSize(1);
        assertThat(pg.requests("/v1/payments/orders/")).hasSize(0);

        StockSnapshot stock = testFixtures.stockOf(productId);
        assertThat(stock.available()).isEqualTo(100);
        assertThat(stock.reserved()).isEqualTo(0);
        assertThat(stock.sold()).isEqualTo(0);

        Integer cartItemCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders.cart_item WHERE product_id = ?", Integer.class, productId);
        assertThat(cartItemCount).isEqualTo(1);
    }

    @Test
    @DisplayName("PG 조회로 승인이 확인된 뒤 주문 완료가 불가하면 PG 승인을 취소하고 주문이 PAYMENT_FAILED가 되며 재고 예약이 원복되고 PG를 1번 조회한다.")
    void compensateWhenCompletionFailsAfterApproval() {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        UUID shopId = testFixtures.openShop(memberId, "1111111111");
        UUID productId = testFixtures.registerProduct(memberId, shopId, 1000, 100);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, productId);

        pg.on("/v1/payments/confirm", Behavior.delay(1000));
        ConfirmResult result = testFixtures.confirmPayment(buyerId, order.orderId());
        UUID orderId = result.orderId();

        jdbcTemplate.update("UPDATE orders.shop_order SET status = 'CANCELLED' WHERE order_id = ?", orderId);

        Map<String, Object> payment = jdbcTemplate.queryForMap("SELECT payment_key, pg_order_id, amount FROM payment.payment WHERE order_id = ?", orderId);

        pg.markApproved((String) payment.get("pg_order_id"), (String) payment.get("payment_key"), (Long) payment.get("amount"), Instant.now().minus(Duration.ofMinutes(10)));

        Instant updatedAt = Instant.now().minusSeconds(60);
        testFixtures.setOrderUpdatedAt(orderId, updatedAt);

        // When
        int reconciled = orderReconcileService.reconcileBatch();

        // Then
        assertThat(reconciled).isEqualTo(1);

        Map<String, Object> orderRow = jdbcTemplate.queryForMap("SELECT status, failure_reason FROM orders.orders WHERE id = ?", orderId);
        assertThat(orderRow.get("status")).isEqualTo("PAYMENT_FAILED");
        assertThat(orderRow.get("failure_reason")).isEqualTo("결제 완료 처리 실패로 자동 취소");

        Map<String, Object> paymentRow = jdbcTemplate.queryForMap("SELECT payment_key, status, cancelled_amount, amount FROM payment.payment WHERE order_id = ?", orderId);
        assertThat(paymentRow.get("status")).isEqualTo("CANCELLED");
        assertThat(paymentRow.get("amount")).isEqualTo(paymentRow.get("cancelled_amount"));

        Map<String, Object> paymentCancel = jdbcTemplate.queryForMap("SELECT status FROM payment.payment_cancel WHERE idempotency_key = ?", "order-compensate-" + orderId);
        assertThat(paymentCancel.get("status")).isEqualTo("DONE");

        String paymentKey = (String) paymentRow.get("payment_key");

        List<RecordedRequest> requests = pg.requests("/v1/payments/" + paymentKey + "/cancel");
        assertThat(requests).hasSize(1);
        assertThat(pg.requests("/v1/payments/orders/")).hasSize(1);

        StockSnapshot stock = testFixtures.stockOf(productId);
        assertThat(stock.available()).isEqualTo(100);
        assertThat(stock.reserved()).isEqualTo(0);
        assertThat(stock.sold()).isEqualTo(0);

        String stockReservationStatus = jdbcTemplate.queryForObject("SELECT status FROM product.stock_reservation WHERE order_id = ?", String.class, orderId);
        assertThat(stockReservationStatus).isEqualTo("RELEASED");

        Integer cartItemCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders.cart_item WHERE product_id = ?", Integer.class, productId);
        assertThat(cartItemCount).isEqualTo(1);
    }
}
