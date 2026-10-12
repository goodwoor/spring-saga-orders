package saga.events.payment;

public record PaymentFailed(
        Long orderId,
        Long userId,
        PaymentFailedReason reason
) {}
