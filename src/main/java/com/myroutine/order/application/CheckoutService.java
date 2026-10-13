package com.myroutine.order.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.member.api.MemberApi;
import com.myroutine.member.api.ShippingAddressInfo;
import com.myroutine.order.domain.*;
import com.myroutine.product.api.ProductApi;
import com.myroutine.product.api.ProductForCheckout;
import com.myroutine.product.api.ReserveItem;
import com.myroutine.shop.api.ShopApi;
import com.myroutine.shop.api.ShopInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final ProductApi productApi;
    private final ShopApi shopApi;
    private final MemberApi memberApi;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;

    private final Clock clock;
    private final OrderProperties orderProperties;

    @Transactional
    public CheckoutResult checkout(UUID memberId, CheckoutCommand command) {
        Cart cart = cartRepository.findByMemberId(memberId).orElseThrow(
                () -> new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND)
        );
        Set<UUID> cartItemIds = new HashSet<>(command.cartItemIds());
        List<CartItem> cartItems = cartItemRepository.findAllByIdInAndCartId(cartItemIds, cart.getId());
        if (cartItems.size() != cartItemIds.size()) {
            throw new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND);
        }

        Map<UUID, Integer> quantityByProduct = cartItems.stream()
                .collect(Collectors.toMap(CartItem::getProductId, CartItem::getQuantity));

        if (cartItemIds.size() > 50) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        List<ProductForCheckout> products = productApi.getPurchasable(quantityByProduct.keySet());
        List<ShopInfo> shops = shopApi.requireActiveShops(products.stream().map(ProductForCheckout::shopId).toList());
        Map<UUID, ShopInfo> shopInfoMap = shops.stream()
                .collect(Collectors.toMap(ShopInfo::shopId, Function.identity()));

        Set<UUID> myShopIds = shops.stream()
                .filter(shop -> shop.ownerId().equals(memberId))
                .map(ShopInfo::shopId)
                .collect(Collectors.toSet());

        if (!myShopIds.isEmpty()) {
            List<UUID> ownProductIds = products.stream()
                    .filter(product -> myShopIds.contains(product.shopId()))
                    .map(ProductForCheckout::productId)
                    .toList();
            throw new BusinessException(OrderErrorCode.OWN_SHOP_PRODUCT, Map.of("productIds", ownProductIds));
        }

        ShippingAddressInfo addressInfo = memberApi.getAddress(memberId, command.addressId());

        List<CheckoutLine> checkoutLines = products.stream()
                .map(product -> new CheckoutLine(
                        product.productId(),
                        product.shopId(),
                        shopInfoMap.get(product.shopId()).name(),
                        product.name(),
                        product.thumbnailKey(),
                        product.price(),
                        quantityByProduct.get(product.productId()))
                )
                .toList();

        ShippingAddress address = new ShippingAddress(
                addressInfo.recipient(),
                addressInfo.phone(),
                addressInfo.zipcode(),
                addressInfo.address1(),
                addressInfo.address2()
        );

        Instant now = clock.instant();

        Order order = Order.checkout(
                memberId,
                checkoutLines,
                address,
                now,
                orderProperties.paymentTtl()
        );

        Order savedOrder = orderRepository.save(order);
        List<ReserveItem> reserveItems = quantityByProduct.entrySet().stream()
                .map(entry -> new ReserveItem(entry.getKey(), entry.getValue()))
                .toList();
        productApi.reserve(savedOrder.getId(), reserveItems, savedOrder.getExpiresAt());

        return CheckoutResult.from(savedOrder);
    }

}
