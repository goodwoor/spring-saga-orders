package saga;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import saga.dto.OrderItemResponse;
import saga.dto.OrderResponse;
import saga.entity.Order;
import saga.entity.OrderItem;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderMapper {
    OrderResponse toOrderResponse(Order order);
    OrderItemResponse toOrderItemResponse(OrderItem orderItem);
}
