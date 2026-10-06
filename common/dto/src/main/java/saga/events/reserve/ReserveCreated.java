package saga.events.reserve;

import java.math.BigDecimal;

public record ReserveCreated(
        Long orderId,
        Long userId,
        BigDecimal cost
)
{}
