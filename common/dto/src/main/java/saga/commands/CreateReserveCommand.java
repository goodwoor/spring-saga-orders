package saga.commands;

import saga.events.OrderLine;

import java.math.BigDecimal;
import java.util.List;

public record CreateReserveCommand(
        Long orderId,
        Long userId,
        List<OrderLine> orderItems,
        BigDecimal cost
) {}
