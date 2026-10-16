package saga.events.inventory;

public record RevertReserveFailed(
        Long orderId,
        ReserveFailedReason reason
)
{}
