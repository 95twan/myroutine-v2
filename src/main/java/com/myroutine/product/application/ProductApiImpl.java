package com.myroutine.product.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.common.model.Ids;
import com.myroutine.product.api.ProductApi;
import com.myroutine.product.api.ProductForCheckout;
import com.myroutine.product.api.ReleaseReason;
import com.myroutine.product.api.ReserveItem;
import com.myroutine.product.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class ProductApiImpl implements ProductApi {
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final StockReservationRepository stockReservationRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<ProductForCheckout> getForCheckout(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = new LinkedHashSet<>(productIds);
        List<ProductCheckoutRow> rows = productRepository.findCheckoutRowsByIds(ids);

        return rows.stream().map(ProductForCheckout::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductForCheckout> getPurchasable(Collection<UUID> productIds) {
        List<ProductForCheckout> checkouts = getForCheckout(productIds);
        Set<UUID> purchasableIds = checkouts.stream()
                .filter(ProductForCheckout::onSale)
                .map(ProductForCheckout::productId)
                .collect(Collectors.toSet());

        Set<UUID> ids = new LinkedHashSet<>(productIds);
        List<UUID> invalidIds = ids.stream()
                .filter(id -> !purchasableIds.contains(id))
                .toList();

        if (!invalidIds.isEmpty()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NOT_ON_SALE, Map.of("productIds", invalidIds));
        }

        return checkouts;
    }

    @Override
    @Transactional
    public void reserve(UUID orderId, List<ReserveItem> items, Instant expiresAt) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("예약 품목이 비어 있습니다.");
        }
        if (items.stream().anyMatch(i -> i.quantity() <= 0)) {
            throw new IllegalArgumentException("예약 수량은 1 이상이어야 합니다.");
        }
        Set<UUID> ids = items.stream()
                .map(ReserveItem::productId)
                .collect(Collectors.toSet());

        if (ids.size() != items.size()) {
            throw new IllegalArgumentException("같은 상품이 중복되어 있습니다.");
        }

        if (stockReservationRepository.existsByOrderId(orderId)) {
            return;
        }

        // 없거나 판매 중이 아닌 상품이 있으면 PRODUCT_NOT_ON_SALE을 던진다
        getPurchasable(ids);

        List<ReserveItem> sortedItems = items.stream().sorted(Comparator.comparing(ReserveItem::productId)).toList();

        Instant now = Instant.now(clock);

        for (ReserveItem item : sortedItems) {
            UUID productId = item.productId();
            int quantity = item.quantity();
            int count = stockRepository.reserve(productId, quantity, now);
            if (count == 0) {
                throw new BusinessException(ProductErrorCode.OUT_OF_STOCK, Map.of("productIds", List.of(productId)));
            }
            stockReservationRepository.insertReservation(
                    Ids.newId(),
                    orderId,
                    productId,
                    quantity,
                    expiresAt,
                    now
            );
            stockRepository.insertMovement(
                    Ids.newId(),
                    productId,
                    StockMovementType.RESERVE.name(),
                    quantity,
                    StockRefType.ORDER.name(),
                    orderId,
                    null,
                    now
            );
        }
    }

    @Override
    @Transactional
    public void commitReservation(UUID orderId) {
        List<StockReservation> stockReservations = stockReservationRepository.findAllByOrderIdAndStatusOrderByProductIdAsc(orderId, ReservationStatus.HELD);
        Instant now = Instant.now(clock);
        for (StockReservation reservation : stockReservations) {
            int count = stockReservationRepository.transit(reservation.getId(), ReservationStatus.HELD.name(), ReservationStatus.COMMITTED.name(), now);
            if (count == 1) {
                int commit = stockRepository.commit(reservation.getProductId(), reservation.getQuantity(), now);
                if (commit == 0) {
                    throw new IllegalStateException("예약된 재고가 부족해 확정할 수 없습니다. orderId=" + orderId + ", productId=" + reservation.getProductId());
                }
                stockRepository.insertMovement(
                        Ids.newId(),
                        reservation.getProductId(),
                        StockMovementType.COMMIT.name(),
                        reservation.getQuantity(),
                        StockRefType.ORDER.name(),
                        orderId,
                        null,
                        now
                );
            }
        }
    }

    @Override
    @Transactional
    public void releaseReservation(UUID orderId, ReleaseReason reason) {
        List<StockReservation> stockReservations = stockReservationRepository.findAllByOrderIdAndStatusOrderByProductIdAsc(orderId, ReservationStatus.HELD);
        Instant now = Instant.now(clock);
        for (StockReservation reservation : stockReservations) {
            int count = switch (reason) {
                case PAYMENT_FAILED ->
                        stockReservationRepository.transit(reservation.getId(), ReservationStatus.HELD.name(), ReservationStatus.RELEASED.name(), now);
                case EXPIRED ->
                        stockReservationRepository.transit(reservation.getId(), ReservationStatus.HELD.name(), ReservationStatus.EXPIRED.name(), now);
            };
            if (count == 1) {
                int release = stockRepository.release(reservation.getProductId(), reservation.getQuantity(), now);
                if (release == 0) {
                    throw new IllegalStateException("예약된 재고가 부족해 해제할 수 없습니다. orderId=" + orderId + ", productId=" + reservation.getProductId());
                }
                stockRepository.insertMovement(
                        Ids.newId(),
                        reservation.getProductId(),
                        StockMovementType.RELEASE.name(),
                        reservation.getQuantity(),
                        StockRefType.ORDER.name(),
                        orderId,
                        null,
                        now
                );
            }
        }
    }

    @Override
    @Transactional
    public void restore(UUID orderId, UUID productId, int quantity, UUID refundId) {
        StockReservation stockReservation = stockReservationRepository.findByOrderIdAndProductId(orderId, productId).orElseThrow(
                () -> new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION)
        );
        if (stockReservation.getStatus() != ReservationStatus.COMMITTED) {
            throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
        }

        if (quantity <= 0 || stockReservation.getQuantity() < quantity) {
            throw new IllegalArgumentException("수량이 유효하지 않습니다.");
        }
        Instant now = Instant.now(clock);
        int count = stockRepository.insertMovement(
                Ids.newId(),
                productId,
                StockMovementType.RESTORE.name(),
                quantity,
                StockRefType.REFUND.name(),
                refundId,
                null,
                now
        );
        if (count == 0) {
            return;
        }
        if (stockRepository.restore(productId, quantity, now) == 0) {
            throw new IllegalStateException("재고 복원에 실패했습니다. orderId=" + orderId + ", productId=" + productId + ", refundId=" + refundId);
        }
    }
}
