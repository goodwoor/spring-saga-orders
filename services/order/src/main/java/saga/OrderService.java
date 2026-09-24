package saga;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import saga.dto.OrderCreateRequest;
import saga.dto.OrderResponse;
import saga.entity.Order;
import saga.entity.OrderItem;
import saga.entity.OrderStatus;

import java.time.Instant;
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

    public OrderResponse findOrder(Long id) {
        Order order = orderRepository.findWithItems(id).orElse(null);
        OrderResponse orderDto = mapper.toOrderResponse(order);

        return orderDto;
    }

    public List<OrderResponse> findAllOrdersWithItems() {
        List<Order> orders = orderRepository.findAllWithItems();

        List<OrderResponse> ordersDto = orders.stream()
                .map(mapper::toOrderResponse)
                .toList();

        return ordersDto;
    }

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest createRequest) {
        Instant now = Instant.now();

        List<OrderItem> newOrderItems = createRequest.orderItems()
                .stream()
                .map(
                        requestItem -> new OrderItem(
                                requestItem.itemId(),
                                requestItem.amount(),
                                requestItem.cost()
                        )
                )
                .toList();

        Order newOrder = new Order(
                createRequest.userId(),
                newOrderItems,
                createRequest.cost(),
                OrderStatus.CREATED,
                now,
                now
        );

        Order savedOrder = orderRepository.saveAndFlush(newOrder);
        OrderResponse response = mapper.toOrderResponse(savedOrder);

        return response;
    }
}
