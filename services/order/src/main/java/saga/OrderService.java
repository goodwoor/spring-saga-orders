package saga;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import saga.dto.OrderCreateRequest;
import saga.dto.OrderResponse;
import saga.entity.Order;
import saga.kafka.OrderSaga;
import saga.repository.OrderRepository;

import java.util.List;

@Service
public class OrderService {
    private static final int MAX_LIMIT = 200;

    private final OrderRepository orderRepository;
    private final OrderWriter orderWriter;
    private final OrderMapper mapper;
    private final OrderSaga saga;

    @Autowired
    public OrderService(
            OrderRepository orderRepository,
            OrderWriter orderWriter,
            OrderMapper mapper,
            OrderSaga saga
    ) {
        this.orderRepository = orderRepository;
        this.orderWriter = orderWriter;
        this.mapper = mapper;
        this.saga = saga;
    }

    public OrderResponse findOrder(Long id) {
        Order order = orderRepository.findWithItems(id).orElse(null);
        OrderResponse orderDto = mapper.toOrderResponse(order);

        return orderDto;
    }

    public List<OrderResponse> findLatestOrdersWithItems(int limit) {
        int size = Math.clamp(limit, 1, MAX_LIMIT);
        List<Long> ids = orderRepository.findLatestIds(PageRequest.of(0, size));
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Order> orders = orderRepository.findAllWithItemsByIds(ids);

        List<OrderResponse> ordersDto = orders.stream()
                .map(mapper::toOrderResponse)
                .toList();

        return ordersDto;
    }

    @Transactional
    public OrderResponse processCreateOrder(OrderCreateRequest createRequest) {
        Order savedOrder = orderWriter.createOrder(createRequest);
        saga.sendOrderCreatedEvent(savedOrder);
        OrderResponse response = mapper.toOrderResponse(savedOrder);

        return response;
    }
}
