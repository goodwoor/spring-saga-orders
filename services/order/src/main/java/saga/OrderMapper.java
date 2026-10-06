package saga;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import saga.commands.CreateDeliveryCommand;
import saga.commands.CreatePaymentCommand;
import saga.commands.CreateReserveCommand;
import saga.dto.OrderItemResponse;
import saga.dto.OrderResponse;
import saga.entity.Order;
import saga.entity.OrderItem;
import saga.events.*;
import saga.events.order.OrderCancelled;
import saga.events.order.OrderConfirmed;
import saga.events.order.OrderCreated;
import saga.events.order.OrderLine;
import saga.events.reserve.ReserveCreated;
import saga.events.reserve.ReserveFailed;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderMapper {
    OrderResponse toOrderResponse(Order order);
    OrderItemResponse toOrderItemResponse(OrderItem orderItem);

    @Mapping(source = "id", target = "orderId")
    OrderCreated toOrderCreated(Order order);
    OrderLine toOrderLine(OrderItem orderItem);

    CreateReserveCommand toCreateReserveCommand(OrderCreated orderCreatedEvent);
    CreatePaymentCommand toCreatePaymentCommand(ReserveCreated reserveCreatedEvent);
    OrderConfirmed toOrderConfirmed(PaymentCompleted paymentCompletedEvent);
    OrderCancelled toOrderCancelled(ReserveFailed reserveFailed);
    // TODO шаг 5: toOrderCancelled(Long orderId) или из ReserveReleased
    CreateDeliveryCommand toCreateDeliveryCommand(PaymentCompleted paymentCompletedEvent);
}
