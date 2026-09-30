package saga.events;

public record PaymentCompleted(
        Long orderId,
        Long userId,
        Long paymentId
)
{}
