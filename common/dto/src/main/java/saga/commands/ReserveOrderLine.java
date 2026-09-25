package saga.commands;

import java.math.BigDecimal;

public record ReserveOrderLine (
        Long id,
        Long itemId,
        Integer amount
) {}
