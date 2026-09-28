package saga;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import saga.dto.OrderCreateRequest;
import saga.entity.Order;
import saga.entity.OrderItem;
import saga.entity.OrderStatus;

import java.time.Instant;
import java.util.List;

@Component
public class OrderWriter {
    private final OrderRepository orderRepository;

    @Autowired
    public OrderWriter(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order createOrder(OrderCreateRequest createRequest) {
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
        return savedOrder;
    }
}
