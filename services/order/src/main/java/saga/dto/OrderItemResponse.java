package saga.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long itemId,
        Integer amount,
        BigDecimal cost
)
{}
