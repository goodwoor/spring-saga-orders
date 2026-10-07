package saga.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import saga.OrderMapper;
import saga.OrderWriter;
import saga.commands.CreatePaymentCommand;
import saga.commands.CreateReserveCommand;
import saga.commands.RevertReserveCommand;
import saga.entity.Order;
import saga.events.inventory.ReserveReverted;
import saga.events.order.OrderCancelled;
import saga.events.order.OrderConfirmed;
import saga.events.order.OrderCreated;
import saga.events.payment.PaymentCompleted;
import saga.events.payment.PaymentFailed;
import saga.events.inventory.ReserveCreated;
import saga.events.inventory.ReserveFailed;

@Component
@KafkaListener(topics = {"inventory-events", "payment-events"}, groupId = "order-service")
public class OrderSaga {
    private static final Logger log = LoggerFactory.getLogger(OrderSaga.class);
    private final KafkaTemplate<String, Object> producer;
    private final OutBoxProducer outBoxProducer;
    private final OrderWriter orderWriter;
    private final OrderMapper mapper;

    @Autowired
    OrderSaga(
            KafkaTemplate<String, Object> producer,
            OutBoxProducer outBoxProducer,
            OrderWriter orderWriter,
            OrderMapper mapper
    )
    {
        this.producer = producer;
        this.outBoxProducer = outBoxProducer;
        this.orderWriter = orderWriter;
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

    @KafkaHandler
    public void onReserveCreated(ReserveCreated event) {
        log.info("Received event: reserve created: {}", event.orderId());

        Boolean validateResult = orderWriter.validateAndSetAwaitingPaymentStatus(event.orderId());
        if (validateResult) {
            sendCreatePaymentCommand(event);
        }
    }

    public void sendCreatePaymentCommand(ReserveCreated event) {
        CreatePaymentCommand createPaymentCommand = mapper.toCreatePaymentCommand(event);
        producer.send("payment-commands", event.orderId().toString(), createPaymentCommand);
        log.info("Send command: create payment: {}", event.orderId());
    }

    @KafkaHandler
    public void onReserveFailed(ReserveFailed event) {
        log.info("Received event: reserve failed: {}", event);

        Boolean validateResult = orderWriter.validateAndSetCancelledStatus(event.orderId());
        if (validateResult) {
            OrderCancelled orderCancelled = mapper.toOrderCancelled(event);
            sendOrderCancelledEvent(orderCancelled);
        }
    }

    public void sendOrderCancelledEvent(OrderCancelled orderCancelled) {
        producer.send("order-events", orderCancelled.orderId().toString(), orderCancelled);
        log.info("Send event: cancel order: {}", orderCancelled);
    }

    @KafkaHandler
    public void onPaymentCompleted(PaymentCompleted event) {
        log.info("Received event: payment completed: {}", event);

        Boolean validateResult = orderWriter.validateAndSetConfirmedStatus(event.orderId());
        if (validateResult) {
            sendOrderConfirmedEvent(event);
        }
    }

    public void sendOrderConfirmedEvent(PaymentCompleted event) {
        OrderConfirmed orderConfirmed = mapper.toOrderConfirmed(event);
        producer.send("order-events", orderConfirmed.orderId().toString(), orderConfirmed);
        log.info("Send event: confirm order: {}", orderConfirmed);
    }

    @KafkaHandler
    public void onPaymentFailed(PaymentFailed event) {
        log.info("Received event: payment failed: {}", event);
        RevertReserveCommand revertReserveCommand = mapper.toRevertReservationCommand(event);
        sendRevertReservationCommand(revertReserveCommand);
    }

    public void sendRevertReservationCommand(RevertReserveCommand event) {
        producer.send("inventory-commands", event.orderId().toString(), event);
        log.info("Send command: revert reservation: {}", event.orderId());
    }

    @KafkaHandler
    public void onReserveReverted(ReserveReverted event) {
        log.info("Received event: reserve reverted: {}", event);
        Boolean validateResult = orderWriter.validateAndSetCancelledStatus(event.orderId());
        if (validateResult) {
            OrderCancelled orderCancelled = mapper.toOrderCancelled(event);
            sendOrderCancelledEvent(orderCancelled);
        }
    }

    @KafkaHandler(isDefault = true)
    public void onUnknown(Object unknownEvent) {
        log.warn("Unknown Kafka payload, skipping: {}", unknownEvent.getClass().getName());
    }
}
