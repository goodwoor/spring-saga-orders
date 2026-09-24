package saga.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreateRequest(
        Long userId,
        List<OrderItemCreateRequest> orderItems,
        BigDecimal cost
)
{}
