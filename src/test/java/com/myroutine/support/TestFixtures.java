package com.myroutine.support;

import com.myroutine.member.application.*;
import com.myroutine.order.application.*;
import com.myroutine.product.application.*;
import com.myroutine.product.domain.ProductCategory;
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
import java.time.Instant;
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
        OpenShopCommand command = new OpenShopCommand(
                "shop",
                businessNumber,
                "shop@test.com",
                "010-1111-1111",
                "shop address"
        );
        return openShopService.open(memberId, command);
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
        for (UUID productId: productIds) {
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
                "SELECT ol.product_id, ol.status, ol.refunded_amount FROM orders.order_line ol "
                        + "JOIN orders.shop_order so ON so.id = ol.shop_order_id WHERE so.order_id = ?",
                (rs, rowNum) -> lines.put(
                        rs.getObject("product_id", UUID.class),
                        new OrderSnapshot.LineState(rs.getString("status"), rs.getLong("refunded_amount"))),
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

        return new OrderSnapshot(lines, shopOrderStatuses, refundStatuses, payments.isEmpty() ? null : payments.get(0));
    }

    public UUID orderLineIdOf(UUID orderId, UUID productId) {
        return jdbcTemplate.queryForObject("SELECT ol.id FROM orders.order_line ol JOIN orders.shop_order so ON so.id = ol.shop_order_id WHERE so.order_id = ? AND ol.product_id = ?", UUID.class, orderId, productId);
    }
}
