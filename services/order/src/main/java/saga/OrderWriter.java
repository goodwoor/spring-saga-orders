package saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import saga.dto.OrderCreateRequest;
import saga.entity.Order;
import saga.entity.OrderItem;
import saga.entity.OrderStatus;
import saga.repository.OrderRepository;

import java.time.Instant;
import java.util.List;

@Component
public class OrderWriter {
    private final OrderRepository orderRepository;
    private final Logger log = LoggerFactory.getLogger(OrderWriter.class);

    @Autowired
    public OrderWriter(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

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

    public Boolean validateAndSetAwaitingPaymentStatus(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElse(null);

        if (order == null) {
            log.warn("Not found order %s".formatted(orderId));
            return false;
        }

        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.CANCELLED)
        {
            log.warn("Order %s already have status: %s".formatted(orderId, order.getStatus()));
            return false;
        }

        if (order.getStatus() == OrderStatus.AWAITING_PAYMENT)
        {
            log.info("Order %s already have status: %s".formatted(orderId, order.getStatus()));
            return true;
        }

        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        return true;
    }

    public Boolean validateAndSetConfirmedStatus(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElse(null);

        if (order == null) {
            log.warn("Not found order %s".formatted(orderId));
            return false;
        }

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("Order %s already have status: %s".formatted(orderId, order.getStatus()));
            return true;
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Order %s already have status: %s".formatted(orderId, order.getStatus()));
            return false;
        }

        order.setStatus(OrderStatus.CONFIRMED);
        return true;
    }

    public Boolean validateAndSetCancelledStatus(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElse(null);

        if (order == null) {
            log.warn("Not found order %s".formatted(orderId));
            return false;
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Order %s already have status: %s".formatted(orderId, order.getStatus()));
            return false;
        }

        order.setStatus(OrderStatus.CANCELLED);
        return true;
    }
}
