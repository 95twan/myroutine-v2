package com.myroutine.notification.infrastructure;

import com.myroutine.common.inbox.InboxGuard;
import com.myroutine.common.outbox.EventEnvelopes;
import com.myroutine.common.outbox.TypedEnvelope;
import com.myroutine.member.api.event.MemberRegisteredEvent;
import com.myroutine.member.api.event.MemberTopics;
import com.myroutine.notification.application.NotificationComposer;
import com.myroutine.notification.application.NotificationService;
import com.myroutine.notification.domain.NotificationDraft;
import com.myroutine.order.api.event.*;
import com.myroutine.settlement.api.event.SettlementPaidEvent;
import com.myroutine.settlement.api.event.SettlementTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {
    private final NotificationService notificationService;
    private final NotificationComposer composer;
    private final InboxGuard inboxGuard;
    private final EventEnvelopes envelopes;

    @KafkaListener(topics = MemberTopics.MEMBER_REGISTERED, groupId = "notification")
    public void onMemberRegistered(String message) {
        TypedEnvelope<MemberRegisteredEvent> typed = envelopes.parse(message, MemberRegisteredEvent.class);
        inboxGuard.runOnce("notification.member-registered", typed.envelope(), () -> {
            List<NotificationDraft> drafts = composer.compose(typed.payload());
            notificationService.enqueue(typed.envelope().eventId(), drafts);
        });
    }

    @KafkaListener(topics = OrderTopics.ORDER_PAID, groupId = "notification")
    public void onOrderPaid(String message) {
        TypedEnvelope<OrderPaidEvent> typed = envelopes.parse(message, OrderPaidEvent.class);
        inboxGuard.runOnce("notification.order-paid", typed.envelope(), () -> {
            List<NotificationDraft> drafts = composer.compose(typed.payload());
            notificationService.enqueue(typed.envelope().eventId(), drafts);
        });
    }

    @KafkaListener(topics = OrderTopics.ORDER_EXPIRED, groupId = "notification")
    public void onOrderExpired(String message) {
        TypedEnvelope<OrderExpiredEvent> typed = envelopes.parse(message, OrderExpiredEvent.class);
        inboxGuard.runOnce("notification.order-expired", typed.envelope(), () -> {
            List<NotificationDraft> drafts = composer.compose(typed.payload());
            notificationService.enqueue(typed.envelope().eventId(), drafts);
        });
    }

    @KafkaListener(topics = OrderTopics.SHOP_ORDER_SHIPPED, groupId = "notification")
    public void onShopOrderShipped(String message) {
        TypedEnvelope<ShopOrderShippedEvent> typed = envelopes.parse(message, ShopOrderShippedEvent.class);
        inboxGuard.runOnce("notification.shop-order-shipped", typed.envelope(), () -> {
            List<NotificationDraft> drafts = composer.compose(typed.payload());
            notificationService.enqueue(typed.envelope().eventId(), drafts);
        });
    }

    @KafkaListener(topics = OrderTopics.SHOP_ORDER_DELIVERED, groupId = "notification")
    public void onShopOrderDelivered(String message) {
        TypedEnvelope<ShopOrderDeliveredEvent> typed = envelopes.parse(message, ShopOrderDeliveredEvent.class);
        inboxGuard.runOnce("notification.shop-order-delivered", typed.envelope(), () -> {
            List<NotificationDraft> drafts = composer.compose(typed.payload());
            notificationService.enqueue(typed.envelope().eventId(), drafts);
        });
    }

    @KafkaListener(topics = OrderTopics.ORDER_LINE_REFUNDED, groupId = "notification")
    public void onOrderLineRefunded(String message) {
        TypedEnvelope<OrderLineRefundedEvent> typed = envelopes.parse(message, OrderLineRefundedEvent.class);
        inboxGuard.runOnce("notification.order-line-refunded", typed.envelope(), () -> {
            List<NotificationDraft> drafts = composer.compose(typed.payload());
            notificationService.enqueue(typed.envelope().eventId(), drafts);
        });
    }

    @KafkaListener(topics = SettlementTopics.SETTLEMENT_PAID, groupId = "notification")
    public void onSettlementPaid(String message) {
        TypedEnvelope<SettlementPaidEvent> typed = envelopes.parse(message, SettlementPaidEvent.class);
        inboxGuard.runOnce("notification.settlement-paid", typed.envelope(), () -> {
            List<NotificationDraft> drafts = composer.compose(typed.payload());
            notificationService.enqueue(typed.envelope().eventId(), drafts);
        });
    }
}
