package saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import saga.commands.CreatePaymentCommand;
import saga.commands.CreateReserveCommand;
import saga.entity.Order;
import saga.events.OrderConfirmed;
import saga.events.OrderCreated;
import saga.events.PaymentCompleted;
import saga.events.ReserveCreated;

@Component
public class OrderSaga {
    private static final Logger log = LoggerFactory.getLogger(OrderSaga.class);
    private final KafkaTemplate<String, Object> producer;
    private final OrderMapper mapper;

    @Autowired
    OrderSaga(
            KafkaTemplate<String, Object> producer,
            OrderMapper mapper
    )
    {
        this.producer = producer;
        this.mapper = mapper;
    }

    public void sendOrderCreatedEvent(Order savedOrder) {
        OrderCreated event = mapper.toOrderCreated(savedOrder);
        log.info("Send event: order created {}", savedOrder.getId());
        producer.send("order-events", savedOrder.getId().toString(), event);
        sendCreateReserveCommand(event);
    }

    public void sendCreateReserveCommand(OrderCreated event) {
        CreateReserveCommand createReserveCommand = mapper.toCreateReserveCommand(event);
        producer.send("inventory-commands", event.orderId().toString(), createReserveCommand);
        log.info("Send command: reserve order: {}", event.orderId());
    }

    @KafkaListener(topics = "inventory-events", groupId = "order-service")
    public void onReserveCreated(ReserveCreated event) {
        log.info("Received event: reserve created: {}", event.orderId());
        sendCreatePaymentCommand(event);
    }

    public void sendCreatePaymentCommand(ReserveCreated event) {
        CreatePaymentCommand createPaymentCommand = mapper.toCreatePaymentCommand(event);
        producer.send("payment-commands", event.orderId().toString(), createPaymentCommand);
        log.info("Send command: create payment: {}", event.orderId());
    }

    @KafkaListener(topics = "payment-events", groupId = "order-service")
    public void onPaymentCompleted(PaymentCompleted event) {
        log.info("Received event: payment completed: {}", "");
        sendOrderConfirmedEvent(event);
    }

    public void sendOrderConfirmedEvent(PaymentCompleted event) {
        OrderConfirmed orderConfirmed = mapper.toOrderConfirmed(event);
        producer.send("order-events", event.orderId().toString(), event);
        log.info("Send event: confirm order: {}", orderConfirmed);
    }

    //todo: сделать когда будет готов сервис доставки
    /*public void sendCreateDeliveryCommand(PaymentCompleted event) {
        CreateDeliveryCommand createDeliveryCommand = mapper.toCreateDeliveryCommand(event);
        producer.send("inventory-commands", "", "");
        log.info("Send command: reserve order: {}", "");
    }*/
}
