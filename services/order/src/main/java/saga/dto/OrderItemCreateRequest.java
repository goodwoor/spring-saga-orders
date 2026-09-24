package saga.dto;

import java.math.BigDecimal;

public record OrderItemCreateRequest(
        Long itemId,
        Integer amount,
        BigDecimal cost
)
{}
