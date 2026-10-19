package saga;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import saga.commands.CreatePaymentCommand;
import saga.commands.RevertReserveCommand;
import saga.entity.Order;
import saga.entity.OrderStatus;
import saga.events.inventory.ReserveFailed;
import saga.events.inventory.ReserveFailedReason;
import saga.events.inventory.ReserveReverted;
import saga.events.order.OrderCancelled;
import saga.events.payment.PaymentFailed;
import saga.events.payment.PaymentFailedReason;
import saga.repository.OrderRepository;
import saga.repository.OutBoxRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = {OrderApplication.class, OrderCompensationTest.class},
        properties = {
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer",
                "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JacksonJsonDeserializer",
                "spring.kafka.consumer.properties.spring.json.trusted.packages=saga.commands,saga.events,saga.events.inventory,saga.events.order,saga.events.payment"
        }
)
@TestConfiguration(proxyBeanMethods = false)
@Testcontainers
class OrderCompensationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    private final OrderRepository orderRepository;
    private final OutBoxRepository outBoxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    OrderCompensationTest(
            OrderRepository orderRepository,
            OutBoxRepository outBoxRepository,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this.orderRepository = orderRepository;
        this.outBoxRepository = outBoxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Bean
    NewTopic inventoryEvents() {
        return TopicBuilder.name("inventory-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic paymentEvents() {
        return TopicBuilder.name("payment-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Сценарий: заказ в CREATED, в Kafka приходит ReserveFailed.
     * Закрывает: CANCELLED, OrderCancelled в outbox, CreatePaymentCommand нет.
     */
    @Test
    void reserveFailedCancelsWithoutPaymentCommand() throws Exception {
        Order order = saveOrder(OrderStatus.CREATED);
        Long orderId = order.getId();

        kafkaTemplate.send(
                "inventory-events",
                orderId.toString(),
                new ReserveFailed(orderId, ReserveFailedReason.NOT_ENOUGH_AMOUNT)
        ).get(30, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals(OrderStatus.CANCELLED, statusOf(orderId));
            assertTrue(outboxExists(orderId, OrderCancelled.class));
            assertFalse(outboxExists(orderId, CreatePaymentCommand.class));
        });
    }

    /**
     * Сценарий: заказ в AWAITING_PAYMENT, в Kafka приходит PaymentFailed (userId 9999).
     * Закрывает: статус остаётся AWAITING_PAYMENT, RevertReserveCommand в outbox, OrderCancelled нет.
     */
    @Test
    void paymentFailedWritesRevertWithoutCancelling() throws Exception {
        Order order = saveOrder(OrderStatus.AWAITING_PAYMENT);
        Long orderId = order.getId();

        kafkaTemplate.send(
                "payment-events",
                orderId.toString(),
                new PaymentFailed(orderId, 9999L, PaymentFailedReason.NOT_ENOUGH_MONEY)
        ).get(30, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals(OrderStatus.AWAITING_PAYMENT, statusOf(orderId));
            assertTrue(outboxExists(orderId, RevertReserveCommand.class));
            assertFalse(outboxExists(orderId, OrderCancelled.class));
        });
    }

    /**
     * Сценарий: заказ в AWAITING_PAYMENT, в Kafka приходит ReserveReverted.
     * Закрывает: CANCELLED и OrderCancelled в outbox.
     */
    @Test
    void reserveRevertedCancels() throws Exception {
        Order order = saveOrder(OrderStatus.AWAITING_PAYMENT);
        Long orderId = order.getId();

        kafkaTemplate.send(
                "inventory-events",
                orderId.toString(),
                new ReserveReverted(orderId)
        ).get(30, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals(OrderStatus.CANCELLED, statusOf(orderId));
            assertTrue(outboxExists(orderId, OrderCancelled.class));
        });
    }

    private Order saveOrder(OrderStatus status) {
        Instant now = Instant.now();
        Order order = new Order(
                42L,
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
}
