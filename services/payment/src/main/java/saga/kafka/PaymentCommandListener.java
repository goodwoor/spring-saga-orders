package saga.kafka;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import saga.PaymentService;
import saga.commands.CreatePaymentCommand;
import saga.entity.Payment;
import saga.entity.PaymentStatus;
import saga.entity.Topic;
import saga.events.payment.PaymentCompleted;
import saga.events.payment.PaymentFailed;
import saga.events.payment.PaymentFailedReason;

@Component
@KafkaListener(topics = "payment-commands", groupId = "payment-service")
public class PaymentCommandListener {
    private static final Logger log = LoggerFactory.getLogger(PaymentCommandListener.class);
    private final PaymentService service;
    private final OutBoxProducer producer;

    @Autowired
    public PaymentCommandListener(
            PaymentService service,
            OutBoxProducer producer
    ) {
        this.service = service;
        this.producer = producer;
    }

    @KafkaHandler
    @Transactional
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
            producer.send(Topic.PAYMENT_EVENTS, failedEvent.orderId().toString(), failedEvent);
            return;
        }

        PaymentCompleted event = new PaymentCompleted(
                command.orderId(),
                command.userId(),
                newPayment.getId()
        );

        producer.send(Topic.PAYMENT_EVENTS, command.orderId().toString(), event);
    }

    @KafkaHandler(isDefault = true)
    public void onUnknown(Object unknownEvent) {
        log.warn("Unknown Kafka payload, skipping: {}", unknownEvent.getClass().getName());
    }
}
