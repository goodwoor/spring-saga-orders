package saga.events.inventory;

import java.math.BigDecimal;

public record ReserveCreated(
        Long orderId,
        Long userId,
        BigDecimal cost
)
{}
