package com.myroutine.support;

import com.myroutine.common.model.Ids;
import com.myroutine.member.application.*;
import com.myroutine.order.application.*;
import com.myroutine.product.application.*;
import com.myroutine.product.domain.ProductCategory;
import com.myroutine.product.domain.ProductStatus;
import com.myroutine.shop.application.OpenShopCommand;
import com.myroutine.shop.application.OpenShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Component
@RequiredArgsConstructor
public class TestFixtures {

    private final SignupService signupService;
    private final MemberAddressService memberAddressService;
    private final OpenShopService openShopService;
    private final RegisterProductService registerProductService;
    private final ProductImageService productImageService;
    private final LoginService loginService;
    private final CartService cartService;
    private final CheckoutService checkoutService;
    private final ConfirmPaymentService confirmPaymentService;
    private final ShipmentService shipmentService;
    private final UpdateProductService updateProductService;
    private final JdbcTemplate jdbcTemplate;

    public UUID signup(String email) {

        SignupCommand command = new SignupCommand(
                email,
                "pass1234",
                email.substring(0, email.indexOf("@")),
                "테스터"
        );

        SignupResult result = signupService.signUp(command);
        return result.memberId();
    }

    public String token(String email) {
        SignupCommand command = new SignupCommand(
                email,
                "pass1234",
                email.substring(0, email.indexOf("@")),
                "테스터"
        );

        SignupResult result = signupService.signUp(command);
        return result.token().accessToken();
    }

    public void changeStatus(UUID memberId, String status) {
        jdbcTemplate.update("UPDATE member.member SET status = ? WHERE id = ?", status, memberId);
    }

    public UUID openShop(UUID memberId, String businessNumber) {
        return openShop(memberId, businessNumber, "shop@test.com");
    }

    public void closeShop(UUID shopId) {
        jdbcTemplate.update("UPDATE shop.shop SET status = 'CLOSED' WHERE id = ?", shopId);
    }

    public String login(String email) {
        return loginService.login(new LoginCommand(email, "pass1234")).accessToken();
    }

    public UUID registerProduct(UUID memberId, UUID shopId) {
        RegisterProductCommand command = new RegisterProductCommand(
                "product",
                "description",
                ProductCategory.ETC,
                10000,
                10,
                false
        );
        return registerProductService.register(memberId, shopId, command);
    }

    public UUID registerProduct(UUID memberId, UUID shopId, long price, int initialStock) {
        RegisterProductCommand command = new RegisterProductCommand(
                "product",
                "description",
                ProductCategory.ETC,
                price,
                initialStock,
                false
        );
        return registerProductService.register(memberId, shopId, command);
    }

    public UUID registerProduct(UUID memberId, UUID shopId, ProductCategory category, int initialStock) {
        RegisterProductCommand command = new RegisterProductCommand(
                "product",
                "description",
                category,
                10000,
                initialStock,
                false
        );
        return registerProductService.register(memberId, shopId, command);
    }

    public void hideProduct(UUID productId) {
        jdbcTemplate.update("UPDATE product.product SET status = 'HIDDEN' WHERE id = ?", productId);
    }

    public void discontinueProduct(UUID productId) {
        jdbcTemplate.update("UPDATE product.product SET status = 'DISCONTINUED' WHERE id = ?", productId);
    }

    public ImageResult registerProductImage(UUID memberId, UUID shopId, UUID productId) throws Exception {
        byte[] fileBytes = new byte[2000];
        new Random().nextBytes(fileBytes);

        UploadUrlResult issued = productImageService.issueUploadUrl(memberId, shopId, productId, "image/png", fileBytes.length);

        HttpResponse<String> response = putToStorage(issued.uploadUrl(), issued.headers(), fileBytes);

        if (response.statusCode() != 200) {
            throw new IllegalStateException("저장소 업로드에 실패했습니다. status=" + response.statusCode() + ", body=" + response.body());
        }

        return productImageService.register(memberId, shopId, productId, issued.objectKey());
    }

    public HttpResponse<String> putToStorage(String uploadUrl, Map<String, String> headers, byte[] body) throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder putBuilder = HttpRequest.newBuilder(URI.create(uploadUrl))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach((name, value) -> {
            if (!name.equalsIgnoreCase("host") && !name.equalsIgnoreCase("content-length")) {
                putBuilder.header(name, value);
            }
        });

        return httpClient.send(putBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public UUID address(UUID memberId) {
        return memberAddressService.register(memberId, new AddressCommand(
                "recipient",
                "010-1213-1234",
                "30382",
                "address1",
                "address2",
                true
        ));
    }

    public UUID addToCart(UUID memberId, UUID productId, int quantity) {
        return cartService.addItem(memberId, productId, quantity);
    }

    public CheckoutResult checkout(UUID memberId, List<UUID> cartItemIds, UUID addressId) {
        return checkoutService.checkout(memberId, new CheckoutCommand(cartItemIds, addressId));
    }

    public StockSnapshot stockOf(UUID productId) {
        String sql = "SELECT available, reserved, sold FROM product.stock WHERE product_id = ?";
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new StockSnapshot(
                rs.getInt("available"),
                rs.getInt("reserved"),
                rs.getInt("sold")
        ), productId);
    }

    public CheckoutResult createPendingOrder(UUID buyerId, UUID... productIds) {
        UUID addressId = address(buyerId);
        List<UUID> cartItemIds = new ArrayList<>();
        for (UUID productId : productIds) {
            UUID cartItemId = addToCart(buyerId, productId, 1);
            cartItemIds.add(cartItemId);
        }
        return checkout(buyerId, cartItemIds, addressId);
    }

    public CheckoutResult createPendingOrder(UUID buyerId, Map<UUID, Integer> productIds) {
        UUID addressId = address(buyerId);
        List<UUID> cartItemIds = new ArrayList<>();
        productIds.forEach((productId, quantity) -> {
            UUID cartItemId = addToCart(buyerId, productId, quantity);
            cartItemIds.add(cartItemId);
        });
        return checkout(buyerId, cartItemIds, addressId);
    }

    public ConfirmResult confirmPayment(UUID memberId, UUID orderId) {
        Long totalAmount = jdbcTemplate.queryForObject("SELECT total_amount FROM orders.orders WHERE id = ?", Long.class, orderId);
        ConfirmPaymentCommand command = new ConfirmPaymentCommand("pk_" + orderId, "ORD-" + orderId, totalAmount);
        return confirmPaymentService.confirm(memberId, orderId, command);
    }

    public void setOrderExpired(UUID orderId, Instant expiresAt) {
        jdbcTemplate.update("UPDATE orders.orders SET expires_at = ? WHERE id = ?", Timestamp.from(expiresAt), orderId);
    }

    public void breakReservedStock(UUID productId) {
        jdbcTemplate.update("UPDATE product.stock SET available = available + reserved, reserved = 0 WHERE product_id = ?", productId);
    }

    public void setOrderUpdatedAt(UUID orderId, Instant updatedAt) {
        jdbcTemplate.update("UPDATE orders.orders SET updated_at = ? WHERE id = ?", Timestamp.from(updatedAt), orderId);
    }

    public UUID createPaidOrder(UUID buyerId, UUID... productIds) {
        CheckoutResult order = createPendingOrder(buyerId, productIds);
        ConfirmResult result = confirmPayment(buyerId, order.orderId());
        return result.orderId();
    }

    public UUID shopOrderIdOf(UUID orderId, UUID shopId) {
        return jdbcTemplate.queryForObject("SELECT id FROM orders.shop_order WHERE order_id = ? AND shop_id = ?", UUID.class, orderId, shopId);
    }

    public void shipShopOrder(UUID sellerId, UUID shopId, UUID shopOrderId) {
        shipmentService.ship(sellerId, shopId, shopOrderId, "CJ대한통운", "1234567890");
    }

    public void deliverShopOrder(UUID sellerId, UUID shopId, UUID shopOrderId) {
        shipmentService.deliver(sellerId, shopId, shopOrderId);
    }

    public OrderSnapshot snapshotOf(UUID orderId) {
        Map<UUID, OrderSnapshot.LineState> lines = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT ol.product_id, ol.status, ol.refunded_amount, ol.confirmed_at FROM orders.order_line ol "
                        + "JOIN orders.shop_order so ON so.id = ol.shop_order_id WHERE so.order_id = ?",
                (rs, rowNum) -> lines.put(
                        rs.getObject("product_id", UUID.class),
                        new OrderSnapshot.LineState(
                                rs.getString("status"),
                                rs.getLong("refunded_amount"),
                                rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toInstant())
                ),
                orderId);

        List<String> shopOrderStatuses = jdbcTemplate.queryForList(
                "SELECT status FROM orders.shop_order WHERE order_id = ? ORDER BY created_at, id", String.class, orderId);

        List<String> refundStatuses = jdbcTemplate.queryForList(
                "SELECT status FROM orders.refund WHERE order_id = ? ORDER BY created_at, id", String.class, orderId);

        List<OrderSnapshot.PaymentState> payments = jdbcTemplate.query(
                "SELECT amount, cancelled_amount, status, payment_key FROM payment.payment WHERE order_id = ?",
                (rs, rowNum) -> new OrderSnapshot.PaymentState(
                        rs.getLong("amount"), rs.getLong("cancelled_amount"), rs.getString("status"), rs.getString("payment_key")),
                orderId);

        List<String> status = jdbcTemplate.queryForList("SELECT status FROM orders.orders WHERE id = ?", String.class, orderId);

        return new OrderSnapshot(lines, shopOrderStatuses, refundStatuses, payments.isEmpty() ? null : payments.getFirst(), status.isEmpty() ? null : status.getFirst());
    }

    public UUID orderLineIdOf(UUID orderId, UUID productId) {
        return jdbcTemplate.queryForObject("SELECT ol.id FROM orders.order_line ol JOIN orders.shop_order so ON so.id = ol.shop_order_id WHERE so.order_id = ? AND ol.product_id = ?", UUID.class, orderId, productId);
    }

    public UUID refundIdOf(UUID orderLineId) {
        return jdbcTemplate.queryForObject("SELECT id FROM orders.refund WHERE order_line_id = ?", UUID.class, orderLineId);
    }

    public void setRefundUpdatedAt(UUID refundId, Instant updateAt) {
        jdbcTemplate.update("UPDATE orders.refund SET updated_at = ? WHERE id = ?", Timestamp.from(updateAt), refundId);
    }

    public void setRefundedAmount(UUID orderLineId, long refundedAmount) {
        jdbcTemplate.update("UPDATE orders.order_line SET refunded_amount = ? WHERE id = ?", refundedAmount, orderLineId);
    }

    public UUID createDeliveredOrder(UUID buyerId, UUID sellerId, UUID shopId, UUID... productIds) {
        UUID orderId = createPaidOrder(buyerId, productIds);
        UUID shopOrderId = shopOrderIdOf(orderId, shopId);
        shipShopOrder(sellerId, shopId, shopOrderId);
        deliverShopOrder(sellerId, shopId, shopOrderId);
        return orderId;
    }

    public RefundRow refundRowOf(UUID refundId) {
        return jdbcTemplate.queryForObject("SELECT status, reason_type, amount, reason, reject_reason, completed_at FROM orders.refund WHERE id = ?",
                (rs, rowNum) -> {
                    return new RefundRow(
                            rs.getString("status"),
                            rs.getString("reason_type"),
                            rs.getLong("amount"),
                            rs.getString("reason"),
                            rs.getString("reject_reason"),
                            rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null
                    );
                },
                refundId);
    }

    public ShopOrderSnapshot shopOrderSnapshotOf(UUID shopOrderId) {
        return jdbcTemplate.queryForObject("SELECT status, carrier, tracking_number, shipped_at, delivered_at, completed_at FROM orders.shop_order WHERE id = ?",
                (rs, rowNum) -> {
                    return new ShopOrderSnapshot(
                            rs.getString("status"),
                            rs.getString("carrier"),
                            rs.getString("tracking_number"),
                            rs.getTimestamp("shipped_at") != null ? rs.getTimestamp("shipped_at").toInstant() : null,
                            rs.getTimestamp("delivered_at") != null ? rs.getTimestamp("delivered_at").toInstant() : null,
                            rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null
                    );
                },
                shopOrderId);
    }

    public void setShopOrderDeliveredAt(UUID shopOrderId, Instant deliveredAt) {
        jdbcTemplate.update("UPDATE orders.shop_order SET delivered_at = ? WHERE id = ?", Timestamp.from(deliveredAt), shopOrderId);
    }

    public MemberSnapshot memberSnapshotOf(UUID memberId) {
        return jdbcTemplate.queryForObject("""
                        SELECT nickname, name, phone, password_hash, role, status, email, created_at, updated_at
                        FROM member.member
                        WHERE id = ?
                        """,
                (rs, rowNum) -> {
                    return new MemberSnapshot(
                            rs.getString("nickname"),
                            rs.getString("name"),
                            rs.getString("phone"),
                            rs.getString("password_hash"),
                            rs.getString("role"),
                            rs.getString("status"),
                            rs.getString("email"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant()
                    );
                },
                memberId);
    }

    public long countOf(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    public void changeRole(UUID memberId, String role) {
        jdbcTemplate.update("UPDATE member.member SET role = ? WHERE id = ?", role, memberId);
    }

    public int processedCountOf(String consumer, UUID eventId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM common.processed_message WHERE consumer = ? AND event_id = ?", Integer.class, consumer, eventId);
    }

    public ShopSnapshot shopSnapshotOf(UUID shopId) {
        return jdbcTemplate.queryForObject("SELECT name, email, phone, address, status, business_number, closed_at FROM shop.shop WHERE id = ?",
                (rs, rowNum) -> {
                    Timestamp closedAt = rs.getTimestamp("closed_at");
                    return new ShopSnapshot(
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("phone"),
                            rs.getString("address"),
                            rs.getString("status"),
                            rs.getString("business_number"),
                            closedAt != null ? closedAt.toInstant() : null
                    );
                },
                shopId);
    }

    public ProductSnapshot productSnapshotOf(UUID productId) {
        List<MovementState> movements = jdbcTemplate.query("""
                        SELECT type, quantity, ref_type, ref_id
                        FROM product.stock_movement
                        WHERE product_id = ?
                        ORDER BY created_at, id
                        """,
                (rs, rowNum) -> new MovementState(
                        rs.getString("type"),
                        rs.getInt("quantity"),
                        rs.getString("ref_type"),
                        rs.getObject("ref_id", UUID.class)
                ),
                productId);

        List<PriceChangeState> priceChangeStates = jdbcTemplate.query("""
                        SELECT old_price, new_price, changed_at
                        FROM product.price_history
                        WHERE product_id = ?
                        ORDER BY changed_at, id
                        """,
                (rs, rowNum) -> new PriceChangeState(
                        rs.getLong("old_price"),
                        rs.getLong("new_price"),
                        rs.getTimestamp("changed_at").toInstant()
                ),
                productId);

        return jdbcTemplate.query("""
                        SELECT p.name, p.status, p.price, p.subscribable, s.received, p.version
                        FROM product.product p
                        JOIN product.stock s ON s.product_id = p.id
                        WHERE p.id = ?
                        """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return new ProductSnapshot(
                            rs.getString("name"),
                            rs.getString("status"),
                            rs.getLong("price"),
                            rs.getBoolean("subscribable"),
                            rs.getInt("received"),
                            rs.getLong("version"),
                            priceChangeStates,
                            movements
                    );
                },
                productId);
    }

    public void updateProduct(UUID memberId, UUID shopId, UUID productId, String name, Long price) {
        updateProductService.update(memberId, shopId, productId, new UpdateProductCommand(name, null, null, price, null));
    }

    public void changeProductStatus(UUID memberId, UUID shopId, UUID productId, ProductStatus to) {
        updateProductService.changeStatus(memberId, shopId, productId, to);
    }

    public UUID insertSettlementItem(UUID shopId, long amount, Instant confirmedAt) {
        UUID orderLineId = Ids.newId();
        jdbcTemplate.update("""
                        INSERT INTO settlement.settlement_item (id, order_line_id, shop_id, order_id, product_id, amount, confirmed_at, settlement_id, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, NULL, now(), now())
                        """,
                Ids.newId(), orderLineId, shopId, Ids.newId(), Ids.newId(), amount, OffsetDateTime.ofInstant(confirmedAt, ZoneOffset.UTC)
        );
        return orderLineId;
    }

    public void insertSettlementItems(List<UUID> shopIds, int itemsPerShop, long amount, Instant confirmedAt) {
        List<Object[]> args = new ArrayList<>();
        for (UUID shopId : shopIds) {
            for (int i = 0; i < itemsPerShop; i++) {
                args.add(new Object[]{
                        Ids.newId(), Ids.newId(), shopId, Ids.newId(), Ids.newId(),
                        amount, OffsetDateTime.ofInstant(confirmedAt, ZoneOffset.UTC)
                });
            }
        }
        jdbcTemplate.batchUpdate("""
                        INSERT INTO settlement.settlement_item (id, order_line_id, shop_id, order_id, product_id, amount, confirmed_at, settlement_id, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, NULL, now(), now())
                        """,
                args
        );
    }

    public SettlementSnapshot settlementSnapshotOf(UUID shopId, YearMonth month) {
        List<SettlementSnapshot> snapshots = jdbcTemplate.query("""
                        SELECT id, period_start, period_end, status, gross_amount, fee_amount, net_amount, fee_rate_bp, item_count, payout_attempts, payout_reference, last_error, paid_at
                        FROM settlement.settlement
                        WHERE shop_id = ? AND period_start = ?
                        """,
                (rs, rowNum) -> {
                    Instant paidAt = rs.getTimestamp("paid_at") != null ? rs.getTimestamp("paid_at").toInstant() : null;
                    UUID settlementId = rs.getObject("id", UUID.class);
                    List<ItemState> items = jdbcTemplate.query("""
                                    SELECT order_line_id, amount, confirmed_at
                                    FROM settlement.settlement_item
                                    WHERE settlement_id = ? ORDER BY confirmed_at, order_line_id
                                    """,
                            (r, n) -> new ItemState(
                                    r.getObject("order_line_id", UUID.class),
                                    r.getLong("amount"),
                                    r.getTimestamp("confirmed_at").toInstant()
                            ),
                            settlementId);
                    return new SettlementSnapshot(
                            settlementId,
                            rs.getDate("period_start").toLocalDate(),
                            rs.getDate("period_end").toLocalDate(),
                            rs.getString("status"),
                            rs.getString("payout_reference"),
                            rs.getString("last_error"),
                            rs.getLong("gross_amount"),
                            rs.getLong("fee_amount"),
                            rs.getLong("net_amount"),
                            rs.getInt("fee_rate_bp"),
                            rs.getInt("item_count"),
                            rs.getInt("payout_attempts"),
                            paidAt,
                            items
                    );
                },
                shopId, month.atDay(1)
        );

        if (snapshots.isEmpty()) {
            return null;
        }

        return snapshots.getFirst();
    }

    public SettlementSnapshot settlementSnapshotOf(UUID shopId) {
        List<SettlementSnapshot> snapshots = jdbcTemplate.query("""
                        SELECT id, period_start, period_end, status, gross_amount, fee_amount, net_amount, fee_rate_bp, item_count, payout_attempts, payout_reference, last_error, paid_at
                        FROM settlement.settlement
                        WHERE shop_id = ?
                        """,
                (rs, rowNum) -> {
                    Instant paidAt = rs.getTimestamp("paid_at") != null ? rs.getTimestamp("paid_at").toInstant() : null;
                    UUID settlementId = rs.getObject("id", UUID.class);
                    List<ItemState> items = jdbcTemplate.query("""
                                    SELECT order_line_id, amount, confirmed_at
                                    FROM settlement.settlement_item
                                    WHERE settlement_id = ? ORDER BY confirmed_at, order_line_id
                                    """,
                            (r, n) -> new ItemState(
                                    r.getObject("order_line_id", UUID.class),
                                    r.getLong("amount"),
                                    r.getTimestamp("confirmed_at").toInstant()
                            ),
                            settlementId);
                    return new SettlementSnapshot(
                            settlementId,
                            rs.getDate("period_start").toLocalDate(),
                            rs.getDate("period_end").toLocalDate(),
                            rs.getString("status"),
                            rs.getString("payout_reference"),
                            rs.getString("last_error"),
                            rs.getLong("gross_amount"),
                            rs.getLong("fee_amount"),
                            rs.getLong("net_amount"),
                            rs.getInt("fee_rate_bp"),
                            rs.getInt("item_count"),
                            rs.getInt("payout_attempts"),
                            paidAt,
                            items
                    );
                },
                shopId
        );

        if (snapshots.isEmpty()) {
            return null;
        }

        if (snapshots.size() > 1) {
            throw new IllegalStateException("Expected at most one settlement snapshot for shopId " + shopId);
        }

        return snapshots.getFirst();
    }

    public SettlementItemSnapshot settlementItemSnapshotOf(UUID orderLineId) {
        List<SettlementItemSnapshot> snapshots = jdbcTemplate.query("""
                        SELECT i.id, i.shop_id, i.order_id, i.product_id, i.amount, i.confirmed_at, i.settlement_id, s.period_start
                        FROM settlement.settlement_item i
                        LEFT JOIN settlement.settlement s ON s.id = i.settlement_id
                        WHERE i.order_line_id = ?
                        """,
                (rs, rowNum) -> {
                    LocalDate periodStart = rs.getDate("period_start") != null ? rs.getDate("period_start").toLocalDate() : null;
                    UUID settlementId = rs.getString("settlement_id") != null ? UUID.fromString(rs.getString("settlement_id")) : null;
                    return new SettlementItemSnapshot(
                            rs.getObject("id", UUID.class),
                            rs.getObject("shop_id", UUID.class),
                            rs.getObject("order_id", UUID.class),
                            rs.getObject("product_id", UUID.class),
                            settlementId,
                            rs.getLong("amount"),
                            rs.getTimestamp("confirmed_at").toInstant(),
                            periodStart
                    );
                },
                orderLineId
        );

        if (snapshots.isEmpty()) {
            return null;
        }
        return snapshots.getFirst();
    }

    public long unsettledItemCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlement.settlement_item WHERE settlement_id IS NULL", Long.class);
    }

    public List<NotificationRow> notificationRowsOf(UUID memberId) {
        return jdbcTemplate.query("""
                        SELECT member_id, type, channel, recipient, title, status, attempts, last_error, dedup_key, sent_at, next_attempt_at
                        FROM notification.notification
                        WHERE member_id = ?
                        ORDER BY created_at, id
                        """,
                (rs, rowNum) -> {
                    Instant sentAt = rs.getTimestamp("sent_at") != null ? rs.getTimestamp("sent_at").toInstant() : null;
                    return new NotificationRow(
                            rs.getObject("member_id", UUID.class),
                            rs.getString("type"),
                            rs.getString("channel"),
                            rs.getString("recipient"),
                            rs.getString("title"),
                            rs.getString("status"),
                            rs.getString("last_error"),
                            rs.getString("dedup_key"),
                            rs.getInt("attempts"),
                            sentAt,
                            rs.getTimestamp("next_attempt_at").toInstant()
                    );
                },
                memberId
        );
    }

    public List<NotificationRow> notificationRows() {
        return jdbcTemplate.query("""
                        SELECT member_id, type, channel, recipient, title, status, attempts, last_error, dedup_key, sent_at, next_attempt_at
                        FROM notification.notification
                        ORDER BY created_at, id
                        """,
                (rs, rowNum) -> {
                    Instant sentAt = rs.getTimestamp("sent_at") != null ? rs.getTimestamp("sent_at").toInstant() : null;
                    return new NotificationRow(
                            rs.getObject("member_id", UUID.class),
                            rs.getString("type"),
                            rs.getString("channel"),
                            rs.getString("recipient"),
                            rs.getString("title"),
                            rs.getString("status"),
                            rs.getString("last_error"),
                            rs.getString("dedup_key"),
                            rs.getInt("attempts"),
                            sentAt,
                            rs.getTimestamp("next_attempt_at").toInstant()
                    );
                }
        );
    }

    public UUID openShop(UUID memberId, String businessNumber, String email) {
        OpenShopCommand command = new OpenShopCommand(
                "shop",
                businessNumber,
                email,
                "010-1111-1111",
                "shop address"
        );
        return openShopService.open(memberId, command);
    }

    public void setNotificationsDue() {
        jdbcTemplate.update("""
                UPDATE notification.notification
                SET next_attempt_at = now() - interval '1 second'
                """);
    }
}
