package saga;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@KafkaListener(topics = "payment-events", groupId = "payment-service")
public class PaymentEventListener {

}
