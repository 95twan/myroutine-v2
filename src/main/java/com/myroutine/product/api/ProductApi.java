package com.myroutine.product.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductApi {
    List<ProductForCheckout> getForCheckout(Collection<UUID> productIds);     // 조회만. 없는 ID는 결과에서 빠짐 (장바구니용)
    List<ProductForCheckout> getPurchasable(Collection<UUID> productIds);     // 하나라도 없거나 ON_SALE이 아니면 PRODUCT_NOT_ON_SALE(details.productIds) (체크아웃용)
    void reserve(UUID orderId, List<ReserveItem> items, Instant expiresAt);   // 멱등: 같은 orderId 재호출 시 무시
    void commitReservation(UUID orderId);                                     // 멱등
    void releaseReservation(UUID orderId, ReleaseReason reason);              // 멱등, COMMITTED는 건드리지 않음
    void restore(UUID orderId, UUID productId, int quantity, UUID refundId);  // 멱등: refundId
}
