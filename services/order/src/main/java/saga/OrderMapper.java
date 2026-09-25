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
    CreateDeliveryCommand toCreateDeliveryCommand(PaymentCompleted paymentCompletedEvent);
}
