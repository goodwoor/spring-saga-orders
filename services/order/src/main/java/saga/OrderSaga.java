package saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import saga.commands.CreateDeliveryCommand;
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

    public void sendOrderCreated(Order savedOrder) {
        OrderCreated orderCreatedMessage = mapper.toOrderCreated(savedOrder);
        log.info("Send message: order created {}", savedOrder.getId());

        producer.send(
                "order-events",
                savedOrder.getId().toString(),
                orderCreatedMessage
        );
    }

    @KafkaListener(topics = "order-events", groupId = "order-service")
    public void onOrderCreated(OrderCreated event) {
        log.info("Get new message: order created: {}", event.orderId());
        sendCreateReserveCommand(event);
    }

    public void sendCreateReserveCommand(OrderCreated event) {
        CreateReserveCommand createReserveCommand = mapper.toCreateReserveCommand(event);
        producer.send("inventory-commands", event.orderId(), createReserveCommand);
        log.info("Push command: reserve order: {}", event.orderId());
    }

    @KafkaListener(topics = "inventory-events", groupId = "order-service")
    public void onReserveCreated(ReserveCreated event) {
        log.info("Get new message: reserve created: {}", "");
        sendStartPaymentCommand(event);
    }

    public void sendStartPaymentCommand(ReserveCreated event) {
        CreatePaymentCommand createPaymentCommand = mapper.toCreatePaymentCommand(event);
        producer.send("payment-commands", "", "");
        log.info("Push command: start payment: {}", "");
    }

    @KafkaListener(topics = "payment-events", groupId = "order-service")
    public void onPaymentCompleted(PaymentCompleted event) {
        log.info("Get new message: payment completed: {}", "");
        sendOrderConfirmedMessage(event);
    }

    public void sendOrderConfirmedMessage(PaymentCompleted event) {
        OrderConfirmed orderConfirmedMessage = mapper.toOrderConfirmed(event);
        producer.send("order-events", "", "");
        log.info("Push command: reserve order: {}", "");
    }

    //todo: сделать когда будет готов сервис доставки
    /*public void sendCreateDeliveryCommand(PaymentCompleted event) {
        CreateDeliveryCommand createDeliveryCommand = mapper.toCreateDeliveryCommand(event);
        producer.send("inventory-commands", "", "");
        log.info("Push command: reserve order: {}", "");
    }*/
}
