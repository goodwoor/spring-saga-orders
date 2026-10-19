package saga.single;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import saga.OrderApplication;
import saga.commands.CreatePaymentCommand;
import saga.commands.RevertReserveCommand;
import saga.entity.Order;
import saga.entity.OrderStatus;
import saga.events.inventory.ReserveCreated;
import saga.events.inventory.ReserveFailed;
import saga.events.inventory.ReserveFailedReason;
import saga.events.inventory.ReserveReverted;
import saga.events.inventory.RevertReserveFailed;
import saga.events.order.OrderCancelled;
import saga.events.order.OrderConfirmed;
import saga.events.payment.PaymentCompleted;
import saga.events.payment.PaymentFailed;
import saga.events.payment.PaymentFailedReason;
import saga.kafka.OrderSaga;
import saga.repository.OrderRepository;
import saga.repository.OutBoxRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = OrderApplication.class)
@Testcontainers
class OrderSagaStatusTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    private final OrderRepository orderRepository;
    private final OutBoxRepository outBoxRepository;
    private final OrderSaga orderSaga;

    @Autowired
    OrderSagaStatusTest(
            OrderRepository orderRepository,
            OutBoxRepository outBoxRepository,
            OrderSaga orderSaga
    ) {
        this.orderRepository = orderRepository;
        this.outBoxRepository = outBoxRepository;
        this.orderSaga = orderSaga;
    }

    /**
     * Сценарий: заказ в CREATED, приходит ReserveCreated.
     * Закрывает: живой переход в AWAITING_PAYMENT и строка CreatePaymentCommand в outbox.
     */
    @Test
    void createdToAwaitingPaymentWritesCreatePaymentCommand() {
        Order order = saveOrder(OrderStatus.CREATED);

        orderSaga.onReserveCreated(new ReserveCreated(order.getId(), order.getUserId(), order.getCost()));

        assertEquals(OrderStatus.AWAITING_PAYMENT, statusOf(order.getId()));
        assertTrue(outboxExists(order.getId(), CreatePaymentCommand.class));
    }

    /**
     * Сценарий: заказ в AWAITING_PAYMENT, приходит PaymentCompleted.
     * Закрывает: живой переход в CONFIRMED и строка OrderConfirmed в outbox.
     */
    @Test
    void awaitingPaymentToConfirmedWritesOrderConfirmed() {
        Order order = saveOrder(OrderStatus.AWAITING_PAYMENT);

        orderSaga.onPaymentCompleted(new PaymentCompleted(order.getId(), order.getUserId(), 1L));

        assertEquals(OrderStatus.CONFIRMED, statusOf(order.getId()));
        assertTrue(outboxExists(order.getId(), OrderConfirmed.class));
    }

    /**
     * Сценарий: заказ в CREATED, приходит ReserveFailed.
     * Закрывает: переход в CANCELLED, OrderCancelled в outbox, CreatePaymentCommand нет.
     */
    @Test
    void createdToCancelledOnReserveFailedWritesOrderCancelled() {
        Order order = saveOrder(OrderStatus.CREATED);

        orderSaga.onReserveFailed(new ReserveFailed(order.getId(), ReserveFailedReason.NOT_ENOUGH_AMOUNT));

        assertEquals(OrderStatus.CANCELLED, statusOf(order.getId()));
        assertTrue(outboxExists(order.getId(), OrderCancelled.class));
        assertFalse(outboxExists(order.getId(), CreatePaymentCommand.class));
    }

    /**
     * Сценарий: заказ в AWAITING_PAYMENT, приходит PaymentFailed.
     * Закрывает: статус остаётся AWAITING_PAYMENT, в outbox есть RevertReserveCommand.
     */
    @Test
    void awaitingPaymentOnPaymentFailedWritesRevertReserveCommand() {
        Order order = saveOrder(OrderStatus.AWAITING_PAYMENT);

        orderSaga.onPaymentFailed(paymentFailed(order));

        assertEquals(OrderStatus.AWAITING_PAYMENT, statusOf(order.getId()));
        assertTrue(outboxExists(order.getId(), RevertReserveCommand.class));
    }

    /**
     * Сценарий: заказ в AWAITING_PAYMENT, приходит ReserveReverted.
     * Закрывает: переход в CANCELLED и строка OrderCancelled в outbox.
     */
    @Test
    void awaitingPaymentToCancelledOnReserveRevertedWritesOrderCancelled() {
        Order order = saveOrder(OrderStatus.AWAITING_PAYMENT);

        orderSaga.onReserveReverted(new ReserveReverted(order.getId()));

        assertEquals(OrderStatus.CANCELLED, statusOf(order.getId()));
        assertTrue(outboxExists(order.getId(), OrderCancelled.class));
    }

    /**
     * Сценарий: PaymentFailed при статусах CREATED, CONFIRMED и CANCELLED.
     * Закрывает: revert только из AWAITING_PAYMENT — статус не меняется, RevertReserveCommand нет.
     */
    @Test
    void paymentFailedOutsideAwaitingPaymentDoesNotWriteRevert() {
        for (OrderStatus status : List.of(OrderStatus.CREATED, OrderStatus.CONFIRMED, OrderStatus.CANCELLED)) {
            Order order = saveOrder(status);

            orderSaga.onPaymentFailed(paymentFailed(order));

            assertEquals(status, statusOf(order.getId()));
            assertFalse(outboxExists(order.getId(), RevertReserveCommand.class));
        }
    }

    /**
     * Сценарий: событие приходит в статусе, из которого этот переход запрещён.
     * Закрывает: мёртвый переход не меняет статус и не пишет команду или событие живого перехода.
     */
    @Test
    void deadTransitionsDoNotChangeStatusOrOutbox() {
        Order confirmed = saveOrder(OrderStatus.CONFIRMED);
        orderSaga.onReserveCreated(new ReserveCreated(confirmed.getId(), confirmed.getUserId(), confirmed.getCost()));
        assertEquals(OrderStatus.CONFIRMED, statusOf(confirmed.getId()));
        assertFalse(outboxExists(confirmed.getId(), CreatePaymentCommand.class));

        Order cancelled = saveOrder(OrderStatus.CANCELLED);
        orderSaga.onPaymentCompleted(new PaymentCompleted(cancelled.getId(), cancelled.getUserId(), 1L));
        assertEquals(OrderStatus.CANCELLED, statusOf(cancelled.getId()));
        assertFalse(outboxExists(cancelled.getId(), OrderConfirmed.class));

        Order awaitingPayment = saveOrder(OrderStatus.AWAITING_PAYMENT);
        orderSaga.onReserveFailed(new ReserveFailed(awaitingPayment.getId(), ReserveFailedReason.NOT_ENOUGH_AMOUNT));
        assertEquals(OrderStatus.AWAITING_PAYMENT, statusOf(awaitingPayment.getId()));
        assertFalse(outboxExists(awaitingPayment.getId(), OrderCancelled.class));

        Order created = saveOrder(OrderStatus.CREATED);
        orderSaga.onReserveReverted(new ReserveReverted(created.getId()));
        assertEquals(OrderStatus.CREATED, statusOf(created.getId()));
        assertFalse(outboxExists(created.getId(), OrderCancelled.class));

        for (OrderStatus status : OrderStatus.values()) {
            Order order = saveOrder(status);
            long rowsBefore = outboxCount(order.getId());

            orderSaga.onRevertReserveFailed(
                    new RevertReserveFailed(order.getId(), ReserveFailedReason.NOT_ENOUGH_AMOUNT)
            );

            assertEquals(status, statusOf(order.getId()));
            assertEquals(rowsBefore, outboxCount(order.getId()));
        }
    }

    private Order saveOrder(OrderStatus status) {
        Instant now = Instant.now();
        Order order = new Order(
                101L,
                List.of(),
                new BigDecimal("10.00"),
                status,
                now,
                now
        );
        return orderRepository.saveAndFlush(order);
    }

    private OrderStatus statusOf(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    private boolean outboxExists(Long orderId, Class<?> messageType) {
        return outBoxRepository.findByMessageKeyAndMessageType(orderId.toString(), messageType.getName()).isPresent();
    }

    private long outboxCount(Long orderId) {
        String key = orderId.toString();
        return outBoxRepository.findAll().stream()
                .filter(item -> key.equals(item.getMessageKey()))
                .count();
    }

    private PaymentFailed paymentFailed(Order order) {
        return new PaymentFailed(order.getId(), order.getUserId(), PaymentFailedReason.NOT_ENOUGH_MONEY);
    }
}
