package saga.dto;

import java.math.BigDecimal;

public record ItemResponse(
        Long id,
        BigDecimal cost,
        Integer amount,
        String name,
        String description
) {}
