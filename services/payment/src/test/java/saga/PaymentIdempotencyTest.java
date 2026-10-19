package saga;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import saga.commands.CreatePaymentCommand;
import saga.entity.Payment;
import saga.entity.PaymentStatus;
import saga.kafka.PaymentCommandListener;
import saga.repository.PaymentRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = PaymentApplication.class)
@Testcontainers
class PaymentIdempotencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    private final PaymentCommandListener listener;
    private final PaymentRepository paymentRepository;

    @Autowired
    PaymentIdempotencyTest(
            PaymentCommandListener listener,
            PaymentRepository paymentRepository
    ) {
        this.listener = listener;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Сценарий: второй onCreatePaymentCommand с тем же orderId.
     * Закрывает: одна строка payments со статусом SUCCESS и тем же id платежа.
     */
    @Test
    void secondCreatePaymentCommandKeepsOneRow() {
        long orderId = 310001L;
        CreatePaymentCommand command = new CreatePaymentCommand(orderId, 42L, new BigDecimal("15.00"));

        listener.onCreatePaymentCommand(command);
        Long paymentId = paymentRepository.findByOrderId(orderId).orElseThrow().getId();

        listener.onCreatePaymentCommand(command);

        List<Payment> rows = paymentRepository.findAll().stream()
                .filter(payment -> Long.valueOf(orderId).equals(payment.getOrderId()))
                .toList();

        assertEquals(1, rows.size());
        assertEquals(PaymentStatus.SUCCESS, rows.getFirst().getStatus());
        assertEquals(paymentId, rows.getFirst().getId());
    }
}
