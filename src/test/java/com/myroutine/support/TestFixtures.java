package com.myroutine.support;

import com.myroutine.member.application.*;
import com.myroutine.order.application.CartService;
import com.myroutine.order.application.CheckoutCommand;
import com.myroutine.order.application.CheckoutResult;
import com.myroutine.order.application.CheckoutService;
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

}
