package saga.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderCreated(
        String orderId,
        String userId,
        List<OrderLine> orderItems,
        BigDecimal cost,
        Instant createdAt
)
{}