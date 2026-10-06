package saga.events.order;

public record OrderCancelled(
        Long orderId
) {}
