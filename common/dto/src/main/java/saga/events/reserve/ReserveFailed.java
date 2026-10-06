package saga.events.reserve;

// TODO шаг 5: рядом ReserveReleased(orderId)
public record ReserveFailed(
        Long orderId,
        ReserveFailedReason reason
)
{}
