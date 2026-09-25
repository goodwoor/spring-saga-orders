package saga.listener;

import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import saga.OrderCreated;

@Component
@KafkaListener(topics = "order-events", groupId = "order-service")
public class OrderEventsListener {

    @KafkaHandler
    public void onOrderCreated(OrderCreated event) {
        System.out.println("Get new meassage: order created: " + event.orderId());
    }

}
