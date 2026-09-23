package saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import saga.dto.OrderResponse;
import saga.entity.Order;

import java.util.List;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderMapper mapper;

    @Autowired
    public OrderService(
            OrderRepository orderRepository,
            OrderMapper mapper
    ) {
        this.orderRepository = orderRepository;
        this.mapper = mapper;
    }

    public List<OrderResponse> findAllOrdersWithItems() {
        List<Order> orders = orderRepository.findAllWithItems();

        List<OrderResponse> ordersDto = orders.stream()
                .map(mapper::toOrderResponse)
                .toList();

        return ordersDto;
    }
}
