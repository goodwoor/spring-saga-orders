package saga.commands;

import java.math.BigDecimal;

// TODO шаг 5: рядом ReleaseReserveCommand(orderId)
public record CreatePaymentCommand(
        Long orderId,
        Long userId,
        BigDecimal cost
)
{}
