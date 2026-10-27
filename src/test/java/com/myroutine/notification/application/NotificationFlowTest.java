package com.myroutine.notification.application;

import com.myroutine.common.model.Ids;
import com.myroutine.common.outbox.OutboxRelay;
import com.myroutine.order.api.event.OrderPaidEvent;
import com.myroutine.order.api.event.OrderTopics;
import com.myroutine.order.api.event.PaidLine;
import com.myroutine.order.api.event.PaidShopOrder;
import com.myroutine.order.application.CheckoutResult;
import com.myroutine.support.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

class NotificationFlowTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final OutboxRelay relay;
    private final OutboxAssert outboxAssert;
    private final NotificationDispatcher dispatcher;
    private final FakeMailSender mailSender;
    private final JdbcTemplate jdbcTemplate;
    private final TestKafka testKafka;

    @MockitoSpyBean
    private NotificationService notificationService;

    @Autowired
    public NotificationFlowTest(TestFixtures testFixtures, OutboxRelay relay, OutboxAssert outboxAssert, NotificationDispatcher dispatcher, FakeMailSender mailSender, JdbcTemplate jdbcTemplate, TestKafka testKafka) {
        this.testFixtures = testFixtures;
        this.relay = relay;
        this.outboxAssert = outboxAssert;
        this.dispatcher = dispatcher;
        this.mailSender = mailSender;
        this.jdbcTemplate = jdbcTemplate;
        this.testKafka = testKafka;
    }

    @Test
    @DisplayName("결제 승인부터 알림 저장과 발송까지 이어진다.")
    void paymentToDispatch() {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111", "shop1@test.com");
        UUID product1Id = testFixtures.registerProduct(seller1Id, shop1Id, 1000, 10);

        UUID seller2Id = testFixtures.signup("seller2@test.com");
        UUID shop2Id = testFixtures.openShop(seller2Id, "2222222222", "shop2@test.com");
        UUID product2Id = testFixtures.registerProduct(seller2Id, shop2Id, 2000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id, product2Id);

        outboxAssert.clear();

        // When
        testFixtures.confirmPayment(buyerId, order.orderId());
        int relayCount = relay.relayOnce();

        // Then
        assertThat(relayCount).isEqualTo(1);
        Eventually.await(Duration.ofSeconds(5), () -> {
            assertThat(testFixtures.notificationRows()).hasSize(3);
        });
        List<OutboxSnapshot> rows = outboxAssert.rows(OrderTopics.ORDER_PAID);
        UUID eventId = rows.getFirst().id();
        assertThat(testFixtures.notificationRows())
                .extracting(NotificationRow::memberId, NotificationRow::type, NotificationRow::recipient,
                        NotificationRow::title, NotificationRow::status, NotificationRow::dedupKey)
                .containsExactlyInAnyOrder(
                        tuple(buyerId, "ORDER_PAID", "buyer@test.com", "주문 결제가 완료되었습니다 (3000원)",
                                "PENDING", eventId + ":" + buyerId + ":ORDER_PAID"),
                        tuple(seller1Id, "SHOP_ORDER_RECEIVED", "shop1@test.com", "새 주문이 들어왔습니다",
                                "PENDING", eventId + ":" + shop1Id + ":SHOP_ORDER_RECEIVED"),
                        tuple(seller2Id, "SHOP_ORDER_RECEIVED", "shop2@test.com", "새 주문이 들어왔습니다",
                                "PENDING", eventId + ":" + shop2Id + ":SHOP_ORDER_RECEIVED")
                );

        int dispatchCount = dispatcher.dispatchBatch();
        assertThat(dispatchCount).isEqualTo(3);
        assertThat(testFixtures.notificationRows()).extracting(NotificationRow::status).containsOnly("SENT");
        List<SentMail> sentEmails = mailSender.sent();
        assertThat(sentEmails).hasSize(3);
        assertThat(sentEmails).extracting(SentMail::to, SentMail::title)
                .containsExactlyInAnyOrder(
                        tuple("buyer@test.com", "주문 결제가 완료되었습니다 (3000원)"),
                        tuple("shop1@test.com", "새 주문이 들어왔습니다"),
                        tuple("shop2@test.com", "새 주문이 들어왔습니다")
                );
        assertThat(sentEmails).allSatisfy(sentMail -> assertThat(sentMail.body()).isNotBlank());

        assertThat(dispatcher.dispatchBatch()).isEqualTo(0);
        assertThat(mailSender.sent()).hasSize(3);
    }

    @Test
    @DisplayName("같은 이벤트를 두 번 받아도 알림은 한 번만 만든다.")
    void duplicateEventCreatesOnce() throws Exception {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111", "shop1@test.com");
        UUID product1Id = testFixtures.registerProduct(seller1Id, shop1Id, 1000, 10);

        UUID seller2Id = testFixtures.signup("seller2@test.com");
        UUID shop2Id = testFixtures.openShop(seller2Id, "2222222222", "shop2@test.com");
        UUID product2Id = testFixtures.registerProduct(seller2Id, shop2Id, 2000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id, product2Id);

        outboxAssert.clear();

        testFixtures.confirmPayment(buyerId, order.orderId());
        relay.relayOnce();

        jdbcTemplate.update("UPDATE common.outbox_event SET status = 'READY', next_attempt_at = now() WHERE topic = 'order.order-paid.v1'");

        // When
        int relayCount = relay.relayOnce();

        UUID event2Id = Ids.newId();
        List<PaidShopOrder> paidShopOrders = List.of(
                new PaidShopOrder(Ids.newId(), shop1Id, List.of(new PaidLine(Ids.newId(), product1Id, "product", 1))));
        testKafka.send(OrderTopics.ORDER_PAID, order.orderId().toString(), event2Id, new OrderPaidEvent(Ids.newId(), buyerId, 1000, paidShopOrders));

        // Then
        assertThat(relayCount).isEqualTo(1);
        Eventually.await(Duration.ofSeconds(5), () -> {
            assertThat(testFixtures.notificationRows()).filteredOn(row -> row.dedupKey().startsWith(event2Id.toString())).hasSize(2);
        });

        UUID event1Id = outboxAssert.rows(OrderTopics.ORDER_PAID).getFirst().id();
        assertThat(testFixtures.notificationRows()).filteredOn(row -> row.dedupKey().startsWith(event1Id.toString())).hasSize(3);
        then(notificationService).should(times(1)).enqueue(eq(event1Id), anyList());
    }

    @Test
    @DisplayName("메일 발송이 5번 실패하면 알림을 FAILED로 바꾼다.")
    void failedAfterFiveAttempts() {
        // Given
        outboxAssert.clear();
        UUID seller1Id = testFixtures.signup("seller1@test.com");

        relay.relayOnce();

        Eventually.await(Duration.ofSeconds(5), () -> {
            assertThat(testFixtures.notificationRowsOf(seller1Id).get(0).type()).isEqualTo("WELCOME");
            assertThat(testFixtures.notificationRowsOf(seller1Id).get(0).status()).isEqualTo("PENDING");
        });

        mailSender.failNext(5);

        // When & Then
        for (int i = 1; i <= 5; i++) {
            dispatcher.dispatchBatch();
            NotificationRow n = testFixtures.notificationRowsOf(seller1Id).get(0);
            assertThat(n.attempts()).isEqualTo(i);
            assertThat(n.lastError()).isNotNull().hasSizeLessThanOrEqualTo(500);
            assertThat(n.sentAt()).isNull();
            if (i != 5) {
                assertThat(n.status()).isEqualTo("PENDING");
            } else {
                assertThat(n.status()).isEqualTo("FAILED");
            }
            testFixtures.setNotificationsDue();
        }
    }

    @Test
    @DisplayName("메일 발송이 실패해도 주문과 결제는 그대로다.")
    void dispatchFailureKeepsOrderPaid() {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111", "shop1@test.com");
        UUID product1Id = testFixtures.registerProduct(seller1Id, shop1Id, 1000, 10);

        UUID seller2Id = testFixtures.signup("seller2@test.com");
        UUID shop2Id = testFixtures.openShop(seller2Id, "2222222222", "shop2@test.com");
        UUID product2Id = testFixtures.registerProduct(seller2Id, shop2Id, 2000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id, product2Id);

        outboxAssert.clear();

        testFixtures.confirmPayment(buyerId, order.orderId());
        relay.relayOnce();

        Eventually.await(Duration.ofSeconds(5), () -> {
            assertThat(testFixtures.notificationRows()).hasSize(3);
        });

        mailSender.failNext(15);

        // When & Then
        for (int i = 1; i <= 5; i++) {
            dispatcher.dispatchBatch();
            testFixtures.setNotificationsDue();
        }
        OrderSnapshot s = testFixtures.snapshotOf(order.orderId());
        assertThat(s.status()).isEqualTo("PAID");
        assertThat(s.payment().status()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("같은 판매자의 가게가 둘이면 가게마다 알림을 만든다.")
    void sameOwnerShopsCreateEach() {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111", "shop1@test.com");
        UUID product1Id = testFixtures.registerProduct(seller1Id, shop1Id, 1000, 10);
        UUID shop3Id = testFixtures.openShop(seller1Id, "3333333333", "shop3@test.com");
        UUID product3Id = testFixtures.registerProduct(seller1Id, shop3Id, 3000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id, product3Id);

        outboxAssert.clear();

        // When & Then
        testFixtures.confirmPayment(buyerId, order.orderId());
        relay.relayOnce();

        UUID eventId = outboxAssert.rows(OrderTopics.ORDER_PAID).getFirst().id();
        Eventually.await(Duration.ofSeconds(5), () -> {
            assertThat(testFixtures.notificationRows()).hasSize(3);
        });
        assertThat(testFixtures.notificationRows()).extracting(NotificationRow::memberId, NotificationRow::type, NotificationRow::recipient, NotificationRow::dedupKey)
                .containsExactlyInAnyOrder(
                        tuple(buyerId, "ORDER_PAID", "buyer@test.com",
                                eventId + ":" + buyerId + ":ORDER_PAID"),
                        tuple(seller1Id, "SHOP_ORDER_RECEIVED", "shop1@test.com",
                                eventId + ":" + shop1Id + ":SHOP_ORDER_RECEIVED"),
                        tuple(seller1Id, "SHOP_ORDER_RECEIVED", "shop3@test.com",
                                eventId + ":" + shop3Id + ":SHOP_ORDER_RECEIVED")
                );
    }

    @Test
    @DisplayName("탈퇴한 구매자의 알림은 건너뛰고 가게 알림은 만든다.")
    void skipWithdrawnMember() {
        // Given
        UUID seller1Id = testFixtures.signup("seller1@test.com");
        UUID shop1Id = testFixtures.openShop(seller1Id, "1111111111", "shop1@test.com");
        UUID product1Id = testFixtures.registerProduct(seller1Id, shop1Id, 1000, 10);

        UUID seller2Id = testFixtures.signup("seller2@test.com");
        UUID shop2Id = testFixtures.openShop(seller2Id, "2222222222", "shop2@test.com");
        UUID product2Id = testFixtures.registerProduct(seller2Id, shop2Id, 2000, 10);

        UUID buyerId = testFixtures.signup("buyer@test.com");
        CheckoutResult order = testFixtures.createPendingOrder(buyerId, product1Id, product2Id);

        outboxAssert.clear();

        testFixtures.confirmPayment(buyerId, order.orderId());
        jdbcTemplate.update("UPDATE member.member SET status = 'WITHDRAWN' WHERE id = ?", buyerId);

        // When & Then
        relay.relayOnce();

        Eventually.await(Duration.ofSeconds(5), () -> {
            assertThat(testFixtures.notificationRows()).hasSize(2);
        });
        assertThat(testFixtures.notificationRows()).extracting(NotificationRow::memberId, NotificationRow::type)
                .containsExactlyInAnyOrder(
                        tuple(seller1Id, "SHOP_ORDER_RECEIVED"),
                        tuple(seller2Id, "SHOP_ORDER_RECEIVED")
                );
    }

    @Test
    @DisplayName("메일 발송이 실패하면 5분 뒤에 다시 시도한다.")
    void retryAfterFiveMinutes() {
        // Given
        outboxAssert.clear();
        UUID seller1Id = testFixtures.signup("seller1@test.com");

        relay.relayOnce();

        Eventually.await(Duration.ofSeconds(5), () -> {
            assertThat(testFixtures.notificationRowsOf(seller1Id).get(0).type()).isEqualTo("WELCOME");
            assertThat(testFixtures.notificationRowsOf(seller1Id).get(0).status()).isEqualTo("PENDING");
        });

        mailSender.failNext(5);

        // When & Then
        dispatcher.dispatchBatch();
        NotificationRow n = testFixtures.notificationRowsOf(seller1Id).get(0);
        assertThat(n.attempts()).isEqualTo(1);
        assertThat(n.nextAttemptAt()).isAfter(Instant.now().plus(Duration.ofMinutes(4))).isBefore(Instant.now().plus(Duration.ofMinutes(6)));

        dispatcher.dispatchBatch();
        n = testFixtures.notificationRowsOf(seller1Id).get(0);
        assertThat(n.attempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("FAILED가 된 알림은 다시 보내지 않는다.")
    void failedNotificationIsNotRetried() {
        // Given
        outboxAssert.clear();
        UUID seller1Id = testFixtures.signup("seller1@test.com");

        relay.relayOnce();

        Eventually.await(Duration.ofSeconds(5), () -> {
            assertThat(testFixtures.notificationRowsOf(seller1Id).get(0).type()).isEqualTo("WELCOME");
            assertThat(testFixtures.notificationRowsOf(seller1Id).get(0).status()).isEqualTo("PENDING");
        });

        mailSender.failNext(5);

        // When & Then
        for (int i = 1; i <= 5; i++) {
            dispatcher.dispatchBatch();
            testFixtures.setNotificationsDue();
        }
        dispatcher.dispatchBatch();
        NotificationRow n = testFixtures.notificationRowsOf(seller1Id).get(0);
        assertThat(n.attempts()).isEqualTo(5);
        assertThat(n.status()).isEqualTo("FAILED");
        assertThat(mailSender.sent()).hasSize(5);
    }
}
