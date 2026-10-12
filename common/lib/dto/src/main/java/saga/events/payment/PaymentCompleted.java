package saga.events.payment;

public record PaymentCompleted(
        Long orderId,
        Long userId,
        Long paymentId
)
{}
