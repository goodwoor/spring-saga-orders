package saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import saga.commands.CreatePaymentCommand;

@Component
@KafkaListener(topics = "payment-commands", groupId = "payment-service")
public class PaymentEventListener {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);
    private final PaymentService service;

    public PaymentEventListener(PaymentService service) {
        this.service = service;
    }

    @KafkaHandler
    public void onCreatePaymentCommand(CreatePaymentCommand command) {
        log.info("Get new command: create: {}", command.orderId());
        service.createPayment(command);
    }
}
