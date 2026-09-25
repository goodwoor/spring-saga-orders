package saga.commands;

import saga.events.OrderLine;

import java.time.Instant;
import java.util.List;

public record CreateReserveCommand(
        String orderId,
        String userId,
        List<OrderLine> orderItems,
        Instant createdAt
) {}
