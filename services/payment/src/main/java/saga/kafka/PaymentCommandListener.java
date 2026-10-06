package saga.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import saga.PaymentService;
import saga.commands.CreatePaymentCommand;
import saga.entity.Payment;
import saga.entity.PaymentStatus;
import saga.events.payment.PaymentCompleted;
import saga.events.payment.PaymentFailed;
import saga.events.payment.PaymentFailedReason;

@Component
@KafkaListener(topics = "payment-commands", groupId = "payment-service")
public class PaymentCommandListener {
    private static final Logger log = LoggerFactory.getLogger(PaymentCommandListener.class);
    private final PaymentService service;
    private final KafkaTemplate<String, Object> producer;

    public PaymentCommandListener(
            PaymentService service,
            KafkaTemplate<String, Object> producer
    ) {
        this.service = service;
        this.producer = producer;
    }

    @KafkaHandler
    public void onCreatePaymentCommand(CreatePaymentCommand command) {
        log.info("Received command: create payment: {}", command.orderId());
        Payment newPayment = service.createPayment(command);

        if (newPayment.getStatus() == PaymentStatus.FAILED) {
            PaymentFailed failedEvent = new PaymentFailed(
                    command.orderId(),
                    command.userId(),
                    PaymentFailedReason.NOT_ENOUGH_MONEY
            );

            log.warn("Payment failed, order id: {}, reason: {}", command.orderId(), failedEvent.reason());
            producer.send("payment-events", failedEvent.orderId().toString(), failedEvent);
            return;
        }

        PaymentCompleted event = new PaymentCompleted(
                command.orderId(),
                command.userId(),
                newPayment.getId()
        );

        producer.send("payment-events", command.orderId().toString(), event);
    }

    @KafkaHandler(isDefault = true)
    public void onUnknown(Object unknownEvent) {
        log.warn("Unknown Kafka payload, skipping: {}", unknownEvent.getClass().getName());
    }
}
