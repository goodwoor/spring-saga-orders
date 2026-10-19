package saga.single;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import saga.PaymentApplication;
import saga.commands.CreatePaymentCommand;
import saga.entity.OutBoxItem;
import saga.entity.Payment;
import saga.entity.PaymentStatus;
import saga.events.payment.PaymentFailed;
import saga.kafka.PaymentCommandListener;
import saga.repository.OutBoxRepository;
import saga.repository.PaymentRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = PaymentApplication.class)
@Testcontainers
class PaymentFailureTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    private final PaymentCommandListener listener;
    private final PaymentRepository paymentRepository;
    private final OutBoxRepository outBoxRepository;

    @Autowired
    PaymentFailureTest(
            PaymentCommandListener listener,
            PaymentRepository paymentRepository,
            OutBoxRepository outBoxRepository
    ) {
        this.listener = listener;
        this.paymentRepository = paymentRepository;
        this.outBoxRepository = outBoxRepository;
    }

    /**
     * Сценарий: один onCreatePaymentCommand с userId 9999 и новым orderId.
     * Закрывает: одна строка payments FAILED и PaymentFailed с NOT_ENOUGH_MONEY в outbox.
     */
    @Test
    void user9999WritesOneFailedPayment() {
        long orderId = 320001L;
        CreatePaymentCommand command = new CreatePaymentCommand(orderId, 9999L, new BigDecimal("15.00"));

        listener.onCreatePaymentCommand(command);

        List<Payment> rows = paymentRepository.findAll().stream()
                .filter(payment -> Long.valueOf(orderId).equals(payment.getOrderId()))
                .toList();

        assertEquals(1, rows.size());
        assertEquals(PaymentStatus.FAILED, rows.getFirst().getStatus());

        OutBoxItem outbox = outBoxRepository
                .findByMessageKeyAndMessageType(Long.toString(orderId), PaymentFailed.class.getName())
                .orElseThrow();

        assertTrue(outbox.getJsonPayload().contains("NOT_ENOUGH_MONEY"));
    }
}
