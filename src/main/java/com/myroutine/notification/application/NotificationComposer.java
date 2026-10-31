package com.myroutine.notification.application;

import com.myroutine.member.api.event.MemberBannedEvent;
import com.myroutine.member.api.event.MemberRegisteredEvent;
import com.myroutine.notification.domain.NotificationDraft;
import com.myroutine.notification.domain.NotificationType;
import com.myroutine.order.api.event.*;
import com.myroutine.settlement.api.event.SettlementPaidEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationComposer {

    public List<NotificationDraft> compose(MemberRegisteredEvent event) {
        NotificationDraft draft = NotificationDraft.toMember(event.memberId(), NotificationType.WELCOME, "[MyRoutine] 가입을 환영합니다", "%s님, MyRoutine에 가입해 주셔서 감사합니다.".formatted(event.nickname()));
        return List.of(draft);
    }

    public List<NotificationDraft> compose(OrderPaidEvent event) {
        List<NotificationDraft> drafts = new ArrayList<>();
        drafts.add(NotificationDraft.toMember(event.memberId(), NotificationType.ORDER_PAID, "주문 결제가 완료되었습니다 (%d원)".formatted(event.totalAmount()),
                "주문번호 %s의 결제가 완료되었습니다. 결제 금액은 %d원입니다.".formatted(event.orderId(), event.totalAmount())));
        event.shopOrders().forEach(shopOrder -> drafts.add(NotificationDraft.toShop(shopOrder.shopId(), NotificationType.SHOP_ORDER_RECEIVED, "새 주문이 들어왔습니다",
                "주문번호 %s에 가게주문 %s가 접수되었습니다.".formatted(event.orderId(), shopOrder.shopOrderId()))));
        return drafts;
    }

    public List<NotificationDraft> compose(OrderExpiredEvent event) {
        String title;
        if (event.reason().equals("EXPIRED")) {
            title = "주문이 만료되었습니다";
        } else {
            title = "결제에 실패했습니다";
        }
        NotificationDraft draft = NotificationDraft.toMember(event.memberId(), NotificationType.ORDER_CLOSED, title, "주문번호 %s가 닫혔습니다. 사유: %s".formatted(event.orderId(), title));
        return List.of(draft);
    }

    public List<NotificationDraft> compose(ShopOrderShippedEvent event) {
        NotificationDraft draft = NotificationDraft.toMember(event.memberId(), NotificationType.SHIPPED, "상품이 발송되었습니다 (%s %s)".formatted(event.carrier(), event.trackingNumber()),
                "주문번호 %s의 상품이 %s 운송장 %s로 발송되었습니다.".formatted(event.orderId(), event.carrier(), event.trackingNumber()));
        return List.of(draft);
    }

    public List<NotificationDraft> compose(ShopOrderDeliveredEvent event) {
        NotificationDraft draft = NotificationDraft.toMember(event.memberId(), NotificationType.DELIVERED, "배송이 완료되었습니다", "주문번호 %s의 배송이 완료되었습니다.".formatted(event.orderId()));
        return List.of(draft);
    }

    public List<NotificationDraft> compose(OrderLineRefundedEvent event) {
        NotificationDraft draft = NotificationDraft.toMember(event.memberId(), NotificationType.REFUNDED, "%d원이 환불되었습니다".formatted(event.amount()),
                "주문번호 %s의 품목 환불 %d원이 처리되었습니다.".formatted(event.orderId(), event.amount()));
        return List.of(draft);
    }

    public List<NotificationDraft> compose(SettlementPaidEvent event) {
        NotificationDraft draft = NotificationDraft.toShop(
                event.shopId(),
                NotificationType.SETTLEMENT_PAID,
                "%s~%s 정산금 %d원이 지급되었습니다".formatted(event.periodStart(), event.periodEnd(), event.netAmount()),
                "정산 기간 %s~%s의 정산금 %d원이 지급되었습니다.".formatted(event.periodStart(), event.periodEnd(), event.netAmount())
        );
        return List.of(draft);
    }

    public List<NotificationDraft> compose(MemberBannedEvent event) {
        NotificationDraft draft = NotificationDraft.toMember(event.memberId(), NotificationType.BANNED, "계정 이용이 제한되었습니다",
                "운영 정책에 따라 계정 이용이 제한되었습니다. 사유: %s".formatted(event.reason()));
        return List.of(draft);
    }
}
