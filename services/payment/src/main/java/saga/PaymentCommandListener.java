package saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import saga.commands.CreatePaymentCommand;
import saga.entity.Payment;
import saga.events.PaymentCompleted;

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

        PaymentCompleted event = new PaymentCompleted(
                command.orderId(),
                command.userId(),
                newPayment.getId()
        );

        producer.send("payment-events", command.orderId().toString(), event);
    }
}
