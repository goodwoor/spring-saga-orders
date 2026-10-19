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
        return move(orderId, OrderStatus.CREATED, OrderStatus.AWAITING_PAYMENT);
    }

    public Boolean validateAndSetConfirmedStatus(Long orderId) {
        return move(orderId, OrderStatus.AWAITING_PAYMENT, OrderStatus.CONFIRMED);
    }

    public Boolean validateAndSetCancelledAfterReserveFailed(Long orderId) {
        return move(orderId, OrderStatus.CREATED, OrderStatus.CANCELLED);
    }

    public Boolean validateAndSetCancelledAfterRevert(Long orderId) {
        return move(orderId, OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLED);
    }

    public Boolean canRevertReserve(Long orderId) {
        return isStatus(orderId, OrderStatus.AWAITING_PAYMENT);
    }

    private Boolean isStatus(Long orderId, OrderStatus expected)
    {
        Order order = orderRepository.findById(orderId)
                .orElse(null);

        if (order == null) {
            log.warn("Not found order %s".formatted(orderId));
            return false;
        }

        if (order.getStatus() != expected) {
            log.warn(
                    "Order %s has status: %s, expected: %s".formatted(
                            orderId,
                            order.getStatus(),
                            expected
                    )
            );
            return false;
        }

        return true;
    }

    private Boolean move(Long orderId, OrderStatus expectedFrom, OrderStatus to)
    {
        Order order = orderRepository.findById(orderId)
                .orElse(null);

        if (order == null) {
            log.warn("Not found order %s".formatted(orderId));
            return false;
        }

        OrderStatus current = order.getStatus();

        if (current == to) {
            log.info("Order %s already have status: %s".formatted(orderId, current));
            return false;
        }

        if (current != expectedFrom) {
            log.warn(
                    "Illegal status move, order %s: %s -> %s, expected from %s".formatted(
                            orderId,
                            current,
                            to,
                            expectedFrom
                    )
            );
            return false;
        }

        if (!current.canGoTo(to)) {
            log.warn("Illegal status move, order %s: %s -> %s".formatted(orderId, current, to));
            return false;
        }

        order.setStatus(to);
        return true;
    }
}
