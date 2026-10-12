package saga.commands;

import java.math.BigDecimal;

public record CreatePaymentCommand(
        Long orderId,
        Long userId,
        BigDecimal cost
)
{}
