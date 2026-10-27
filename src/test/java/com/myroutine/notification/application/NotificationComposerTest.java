package com.myroutine.notification.application;

import com.myroutine.common.model.Ids;
import com.myroutine.member.api.event.MemberRegisteredEvent;
import com.myroutine.notification.domain.NotificationDraft;
import com.myroutine.notification.domain.NotificationType;
import com.myroutine.order.api.event.*;
import com.myroutine.settlement.api.event.SettlementPaidEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class NotificationComposerTest {

    private final NotificationComposer composer = new NotificationComposer();

    @Test
    @DisplayName("가입 이벤트로 환영 알림을 만든다.")
    void composeWelcome() {
        // Given
        UUID buyerId = Ids.newId();
        MemberRegisteredEvent event = new MemberRegisteredEvent(buyerId, "a@test.com", "nick");

        // When
        List<NotificationDraft> drafts = composer.compose(event);

        // Then
        assertThat(drafts).hasSize(1);
        NotificationDraft draft = drafts.getFirst();
        assertThat(draft.memberId()).isEqualTo(buyerId);
        assertThat(draft.shopId()).isNull();
        assertThat(draft.type()).isEqualTo(NotificationType.WELCOME);
        assertThat(draft.title()).isEqualTo("[MyRoutine] 가입을 환영합니다");
        assertThat(draft.body()).isNotBlank();
    }

    @Test
    @DisplayName("결제 완료 이벤트로 구매자 알림과 가게 알림을 만든다.")
    void composeOrderPaid() {
        // Given
        UUID orderId = Ids.newId();
        UUID buyerId = Ids.newId();
        UUID shopId = Ids.newId();
        OrderPaidEvent event = new OrderPaidEvent(
                orderId,
                buyerId,
                3000,
                List.of(new PaidShopOrder(
                        Ids.newId(),
                        shopId,
                        List.of(
                                new PaidLine(Ids.newId(), Ids.newId(), "상품A", 1),
                                new PaidLine(Ids.newId(), Ids.newId(), "상품B", 2)
                        )
                ))
        );

        // When
        List<NotificationDraft> drafts = composer.compose(event);

        // Then
        assertThat(drafts).hasSize(2);
        assertThat(drafts)
                .extracting(NotificationDraft::memberId, NotificationDraft::shopId,
                        NotificationDraft::type, NotificationDraft::title)
                .containsExactlyInAnyOrder(
                        tuple(buyerId, null, NotificationType.ORDER_PAID, "주문 결제가 완료되었습니다 (3000원)"),
                        tuple(null, shopId, NotificationType.SHOP_ORDER_RECEIVED, "새 주문이 들어왔습니다")
                );

        assertThat(drafts).allSatisfy(draft -> assertThat(draft.body()).isNotBlank());
    }

    @Test
    @DisplayName("가게주문이 둘이면 가게마다 알림을 만든다.")
    void composeOrderPaidWithTwoShopOrders() {
        // Given
        UUID orderId = Ids.newId();
        UUID buyerId = Ids.newId();
        UUID shop1Id = Ids.newId();
        UUID shop2Id = Ids.newId();
        OrderPaidEvent event = new OrderPaidEvent(
                orderId,
                buyerId,
                5000,
                List.of(
                        new PaidShopOrder(
                                Ids.newId(),
                                shop1Id,
                                List.of(
                                        new PaidLine(Ids.newId(), Ids.newId(), "상품A", 1)
                                )
                        ),
                        new PaidShopOrder(
                                Ids.newId(),
                                shop2Id,
                                List.of(
                                        new PaidLine(Ids.newId(), Ids.newId(), "상품B", 1),
                                        new PaidLine(Ids.newId(), Ids.newId(), "상품C", 1),
                                        new PaidLine(Ids.newId(), Ids.newId(), "상품D", 1)
                                )
                        )
                )
        );

        // When
        List<NotificationDraft> drafts = composer.compose(event);

        // Then
        assertThat(drafts).hasSize(3);
        assertThat(drafts)
                .extracting(NotificationDraft::memberId, NotificationDraft::shopId,
                        NotificationDraft::type, NotificationDraft::title)
                .containsExactlyInAnyOrder(
                        tuple(buyerId, null, NotificationType.ORDER_PAID, "주문 결제가 완료되었습니다 (5000원)"),
                        tuple(null, shop1Id, NotificationType.SHOP_ORDER_RECEIVED, "새 주문이 들어왔습니다"),
                        tuple(null, shop2Id, NotificationType.SHOP_ORDER_RECEIVED, "새 주문이 들어왔습니다")
                );

        assertThat(drafts).allSatisfy(draft -> assertThat(draft.body()).isNotBlank());
    }

    @Test
    @DisplayName("만료된 주문은 만료 알림을 만든다.")
    void composeOrderExpired() {
        // Given
        UUID buyerId = Ids.newId();
        OrderExpiredEvent event = new OrderExpiredEvent(Ids.newId(), buyerId, "EXPIRED");

        // When
        List<NotificationDraft> drafts = composer.compose(event);

        // Then
        assertThat(drafts).hasSize(1);
        NotificationDraft draft = drafts.getFirst();
        assertThat(draft.memberId()).isEqualTo(buyerId);
        assertThat(draft.shopId()).isNull();
        assertThat(draft.type()).isEqualTo(NotificationType.ORDER_CLOSED);
        assertThat(draft.title()).isEqualTo("주문이 만료되었습니다");
        assertThat(draft.body()).isNotBlank();
    }

    @Test
    @DisplayName("결제에 실패한 주문은 결제 실패 알림을 만든다.")
    void composeOrderPaymentFailed() {
        // Given
        UUID buyerId = Ids.newId();
        OrderExpiredEvent event = new OrderExpiredEvent(Ids.newId(), buyerId, "PAYMENT_FAILED");

        // When
        List<NotificationDraft> drafts = composer.compose(event);

        // Then
        assertThat(drafts).hasSize(1);
        NotificationDraft draft = drafts.getFirst();
        assertThat(draft.memberId()).isEqualTo(buyerId);
        assertThat(draft.shopId()).isNull();
        assertThat(draft.type()).isEqualTo(NotificationType.ORDER_CLOSED);
        assertThat(draft.title()).isEqualTo("결제에 실패했습니다");
        assertThat(draft.body()).isNotBlank();
    }

    @Test
    @DisplayName("발송 이벤트로 택배사와 운송장 번호가 담긴 알림을 만든다.")
    void composeShipped() {
        // Given
        UUID buyerId = Ids.newId();
        ShopOrderShippedEvent event = new ShopOrderShippedEvent(Ids.newId(), Ids.newId(), buyerId, "CJ대한통운", "1234567890");

        // When
        List<NotificationDraft> drafts = composer.compose(event);

        // Then
        assertThat(drafts).hasSize(1);
        NotificationDraft draft = drafts.getFirst();
        assertThat(draft.memberId()).isEqualTo(buyerId);
        assertThat(draft.shopId()).isNull();
        assertThat(draft.type()).isEqualTo(NotificationType.SHIPPED);
        assertThat(draft.title()).isEqualTo("상품이 발송되었습니다 (CJ대한통운 1234567890)");
        assertThat(draft.body()).isNotBlank();
    }

    @Test
    @DisplayName("배송완료 이벤트로 배송 완료 알림을 만든다.")
    void composeDelivered() {
        // Given
        UUID buyerId = Ids.newId();
        ShopOrderDeliveredEvent event = new ShopOrderDeliveredEvent(Ids.newId(), Ids.newId(), buyerId);

        // When
        List<NotificationDraft> drafts = composer.compose(event);

        // Then
        assertThat(drafts).hasSize(1);
        NotificationDraft draft = drafts.getFirst();
        assertThat(draft.memberId()).isEqualTo(buyerId);
        assertThat(draft.shopId()).isNull();
        assertThat(draft.type()).isEqualTo(NotificationType.DELIVERED);
        assertThat(draft.title()).isEqualTo("배송이 완료되었습니다");
        assertThat(draft.body()).isNotBlank();
    }

    @Test
    @DisplayName("환불 이벤트로 환불 금액이 담긴 알림을 만든다.")
    void composeRefunded() {
        // Given
        UUID buyerId = Ids.newId();
        OrderLineRefundedEvent event = new OrderLineRefundedEvent(Ids.newId(), Ids.newId(), Ids.newId(), buyerId, 1000);

        // When
        List<NotificationDraft> drafts = composer.compose(event);

        // Then
        assertThat(drafts).hasSize(1);
        NotificationDraft draft = drafts.getFirst();
        assertThat(draft.memberId()).isEqualTo(buyerId);
        assertThat(draft.shopId()).isNull();
        assertThat(draft.type()).isEqualTo(NotificationType.REFUNDED);
        assertThat(draft.title()).isEqualTo("1000원이 환불되었습니다");
        assertThat(draft.body()).isNotBlank();
    }

    @Test
    @DisplayName("정산 지급 이벤트로 가게에 정산 알림을 만든다.")
    void composeSettlementPaid() {
        // Given
        UUID shopId = Ids.newId();
        SettlementPaidEvent event = new SettlementPaidEvent(Ids.newId(), shopId, 14250, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        // When
        List<NotificationDraft> drafts = composer.compose(event);

        // Then
        assertThat(drafts).hasSize(1);
        NotificationDraft draft = drafts.getFirst();
        assertThat(draft.memberId()).isNull();
        assertThat(draft.shopId()).isEqualTo(shopId);
        assertThat(draft.type()).isEqualTo(NotificationType.SETTLEMENT_PAID);
        assertThat(draft.title()).isEqualTo("2026-09-01~2026-09-30 정산금 14250원이 지급되었습니다");
        assertThat(draft.body()).isNotBlank();
    }
}
