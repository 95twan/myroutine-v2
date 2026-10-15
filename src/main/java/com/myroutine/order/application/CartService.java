package com.myroutine.order.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.model.Ids;
import com.myroutine.order.domain.*;
import com.myroutine.product.api.ProductApi;
import com.myroutine.product.api.ProductForCheckout;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final ProductApi productApi;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final Clock clock;

    @Transactional
    public UUID addItem(UUID memberId, UUID productId, int quantity) {
        List<ProductForCheckout> checkout = productApi.getForCheckout(List.of(productId));
        if (checkout.isEmpty()) {
            throw new BusinessException(OrderErrorCode.CART_PRODUCT_NOT_FOUND);
        }

        Instant now = Instant.now(clock);

        cartRepository.insertIfAbsent(Ids.newId(), memberId, now);

        Cart cart = cartRepository.findByMemberId(memberId).orElseThrow(
                () -> new IllegalStateException("장바구니가 생성되지 않았습니다. memberId=" + memberId)
        );

        cartItemRepository.addQuantity(
                Ids.newId(),
                cart.getId(),
                productId,
                quantity,
                now
        );

        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId).orElseThrow(
                () -> new IllegalStateException("장바구니 상품이 생성되지 않았습니다. cartId=" + cart.getId() + ", productId=" + productId)
        );

        return cartItem.getId();
    }

    @Transactional
    public CartResult changeQuantity(UUID memberId, UUID cartItemId, int quantity) {
        Cart cart = cartRepository.findByMemberId(memberId).orElseThrow(
                () -> new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND)
        );
        CartItem cartItem = cartItemRepository.findByIdAndCartId(cartItemId, cart.getId()).orElseThrow(
                () -> new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND)
        );

        cartItem.changeQuantity(quantity);

        List<CartItem> cartItems = cartItemRepository.findAllByCartIdOrderByCreatedAtDesc(cart.getId());

        return toResult(cartItems);
    }

    @Transactional
    public void removeItem(UUID memberId, UUID cartItemId) {
        Cart cart = cartRepository.findByMemberId(memberId).orElseThrow(
                () -> new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND)
        );
        CartItem cartItem = cartItemRepository.findByIdAndCartId(cartItemId, cart.getId()).orElseThrow(
                () -> new BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND)
        );
        cartItemRepository.delete(cartItem);
    }

    @Transactional(readOnly = true)
    public CartResult getCart(UUID memberId) {
        Optional<Cart> cart = cartRepository.findByMemberId(memberId);
        if (cart.isEmpty()) {
            return toResult(List.of());
        }
        List<CartItem> cartItems = cartItemRepository.findAllByCartIdOrderByCreatedAtDesc(cart.get().getId());
        return toResult(cartItems);
    }

    private CartResult toResult(List<CartItem> items) {
        if (items.isEmpty()) {
            return new CartResult(List.of(), 0);
        }

        List<ProductForCheckout> checkout = productApi.getForCheckout(items.stream().map(CartItem::getProductId).toList());
        Map<UUID, ProductForCheckout> checkoutMap = checkout.stream().collect(Collectors.toMap(ProductForCheckout::productId, Function.identity()));
        List<CartLineResult> cartLineResults = items.stream()
                .map(item -> toLine(item, checkoutMap.get(item.getProductId())))
                .toList();

        long totalAmount = cartLineResults.stream()
                .filter(CartLineResult::purchasable)
                .mapToLong(CartLineResult::lineAmount)
                .sum();

        return new CartResult(cartLineResults, totalAmount);
    }

    private CartLineResult toLine(CartItem item, ProductForCheckout product) {
        String unavailableReason = unavailableReason(product);
        boolean purchasable = unavailableReason == null;
        if (product == null) {
            return new CartLineResult(
                    item.getId(),
                    item.getProductId(),
                    null,
                    null,
                    null,
                    0,
                    item.getQuantity(),
                    0,
                    purchasable,
                    unavailableReason
            );
        }

        return new CartLineResult(
                item.getId(),
                item.getProductId(),
                product.shopId(),
                product.name(),
                product.thumbnailKey(),
                product.price().amount(),
                item.getQuantity(),
                product.price().times(item.getQuantity()).amount(),
                purchasable,
                unavailableReason
        );
    }

    private String unavailableReason(ProductForCheckout product) {
        if (product == null) return "NOT_FOUND";
        if (!product.onSale()) return "NOT_ON_SALE";
        if (!product.inStock()) return "OUT_OF_STOCK";
        return null;
    }

    @Transactional
    public void deductOrdered(UUID memberId, Map<UUID, Integer> quantitiesByProduct) {
        Optional<Cart> cart = cartRepository.findByMemberId(memberId);
        if (cart.isEmpty()) return;

        quantitiesByProduct.forEach((productId, quantity) -> {
            cartItemRepository.deleteIfQuantityAtMost(cart.get().getId(), productId, quantity);
            cartItemRepository.deductQuantity(cart.get().getId(), productId, quantity, clock.instant());
        });

    }
}
