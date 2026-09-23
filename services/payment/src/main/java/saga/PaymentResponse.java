package saga;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse (
        Long id,
        Long userId,
        Long orderId,
        String status,
        Instant statusChangedDate,
        Instant createdAt,
        BigDecimal cost
) {}
