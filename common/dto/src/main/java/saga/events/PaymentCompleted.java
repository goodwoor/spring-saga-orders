package saga.events;

// TODO шаг 5: рядом PaymentFailed(orderId, userId)
public record PaymentCompleted(
        Long orderId,
        Long userId,
        Long paymentId
)
{}
