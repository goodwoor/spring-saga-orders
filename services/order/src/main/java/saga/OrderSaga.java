package saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import saga.commands.CreatePaymentCommand;
import saga.commands.CreateReserveCommand;
import saga.entity.Order;
import saga.events.*;
import saga.events.order.OrderCancelled;
import saga.events.order.OrderConfirmed;
import saga.events.order.OrderCreated;
import saga.events.reserve.ReserveCreated;
import saga.events.reserve.ReserveFailed;

@Component
@KafkaListener(topics = {"inventory-events", "payment-events"}, groupId = "order-service")
public class OrderSaga {
    private static final Logger log = LoggerFactory.getLogger(OrderSaga.class);
    private final KafkaTemplate<String, Object> producer;
    private final OrderWriter orderWriter;
    private final OrderMapper mapper;

    @Autowired
    OrderSaga(
            KafkaTemplate<String, Object> producer,
            OrderWriter orderWriter,
            OrderMapper mapper
    )
    {
        this.producer = producer;
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

    @KafkaHandler
    public void onReserveFailed(ReserveFailed event) {
        log.info("Received event: reserve failed: {}", event);

        Boolean validateResult = orderWriter.validateAndSetCancelledStatus(event.orderId());
        if (validateResult) {
            sendOrderCancelledEvent(event);
        }
    }

    public void sendOrderCancelledEvent(ReserveFailed event) {
        OrderCancelled orderCancelled = mapper.toOrderCancelled(event);
        producer.send("order-events", orderCancelled.orderId().toString(), orderCancelled);
        log.info("Send event: cancel order: {}", orderCancelled);
    }

    // TODO шаг 5: onPaymentFailed → ReleaseReserveCommand, статус не менять (ещё AWAITING_PAYMENT)
    // TODO шаг 5: onReserveReleased → validateAndSetCancelledStatus → sendOrderCancelledEvent
    // TODO шаг 5: sendOrderCancelledEvent(Long orderId), не только из ReserveFailed
    // TODO шаг 5: @KafkaHandler(isDefault = true) — лог + ack

    public void sendCreatePaymentCommand(ReserveCreated event) {
        CreatePaymentCommand createPaymentCommand = mapper.toCreatePaymentCommand(event);
        producer.send("payment-commands", event.orderId().toString(), createPaymentCommand);
        log.info("Send command: create payment: {}", event.orderId());
    }

    @KafkaHandler
    public void onPaymentCompleted(PaymentCompleted event) {
        log.info("Received event: payment completed: {}", "");

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

    //todo: сделать когда будет готов сервис доставки
    /*public void sendCreateDeliveryCommand(PaymentCompleted event) {
        CreateDeliveryCommand createDeliveryCommand = mapper.toCreateDeliveryCommand(event);
        producer.send("inventory-commands", "", "");
        log.info("Send command: reserve order: {}", "");
    }*/
}
