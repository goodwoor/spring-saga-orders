package saga;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import saga.commands.CreatePaymentCommand;
import saga.commands.CreateReserveCommand;
import saga.commands.RevertReserveCommand;
import saga.dto.OrderItemResponse;
import saga.dto.OrderResponse;
import saga.entity.Order;
import saga.entity.OrderItem;
import saga.events.inventory.ReserveReverted;
import saga.events.order.OrderCancelled;
import saga.events.order.OrderConfirmed;
import saga.events.order.OrderCreated;
import saga.events.order.OrderLine;
import saga.events.payment.PaymentCompleted;
import saga.events.payment.PaymentFailed;
import saga.events.inventory.ReserveCreated;
import saga.events.inventory.ReserveFailed;

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
    OrderCancelled toOrderCancelled(ReserveReverted reserveReverted);
    RevertReserveCommand toRevertReservationCommand(PaymentFailed paymentFailedEvent);
}
