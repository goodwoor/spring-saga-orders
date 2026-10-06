package saga.events.order;

import java.math.BigDecimal;

public record OrderLine(
        Long id,
        Long itemId,
        Integer amount,
        BigDecimal cost
)
{}
