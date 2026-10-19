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
import saga.commands.CreateReserveCommand;
import saga.dto.OrderCreateRequest;
import saga.dto.OrderItemCreateRequest;
import saga.dto.OrderResponse;
import saga.entity.OrderStatus;
import saga.events.inventory.ReserveCreated;
import saga.events.order.OrderConfirmed;
import saga.events.order.OrderCreated;
import saga.events.payment.PaymentCompleted;
import saga.repository.OrderRepository;
import saga.repository.OutBoxRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = {OrderApplication.class, OrderHappyPathTest.class},
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
class OrderHappyPathTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OutBoxRepository outBoxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    OrderHappyPathTest(
            OrderService orderService,
            OrderRepository orderRepository,
            OutBoxRepository outBoxRepository,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this.orderService = orderService;
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
     * Сценарий: processCreateOrder, затем ReserveCreated и PaymentCompleted из Kafka.
     * Закрывает: CREATED с OrderCreated и CreateReserveCommand, AWAITING_PAYMENT с CreatePaymentCommand, CONFIRMED с OrderConfirmed.
     */
    @Test
    void confirmOrderThroughForeignEvents() throws Exception {
        Long userId = 42L;
        BigDecimal cost = new BigDecimal("10.00");
        OrderResponse created = orderService.processCreateOrder(new OrderCreateRequest(
                userId,
                List.of(new OrderItemCreateRequest(1L, 1, cost)),
                cost
        ));
        Long orderId = created.id();

        assertEquals(OrderStatus.CREATED, statusOf(orderId));
        assertTrue(outboxExists(orderId, OrderCreated.class));
        assertTrue(outboxExists(orderId, CreateReserveCommand.class));

        kafkaTemplate.send(
                "inventory-events",
                orderId.toString(),
                new ReserveCreated(orderId, userId, cost)
        ).get(30, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals(OrderStatus.AWAITING_PAYMENT, statusOf(orderId));
            assertTrue(outboxExists(orderId, CreatePaymentCommand.class));
        });

        kafkaTemplate.send(
                "payment-events",
                orderId.toString(),
                new PaymentCompleted(orderId, userId, 1L)
        ).get(30, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals(OrderStatus.CONFIRMED, statusOf(orderId));
            assertTrue(outboxExists(orderId, OrderConfirmed.class));
        });
    }

    private OrderStatus statusOf(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    private boolean outboxExists(Long orderId, Class<?> messageType) {
        return outBoxRepository.findByMessageKeyAndMessageType(orderId.toString(), messageType.getName()).isPresent();
    }
}
