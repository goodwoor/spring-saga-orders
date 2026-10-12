package saga.events.inventory;

public record ReserveFailed(
        Long orderId,
        ReserveFailedReason reason
)
{}
