package saga.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long userId,
        List<OrderItemResponse> orderItems,
        BigDecimal cost,
        String status,
        Instant statusChangedDate,
        Instant createdAt
) {}
