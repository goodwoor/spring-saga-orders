package saga.entity;

public enum OrderStatus {
    CREATED, AWAITING_PAYMENT, CONFIRMED, CANCELLED;

    public boolean canGoTo(OrderStatus target)
    {
        if (this == CREATED) {
            return target == AWAITING_PAYMENT || target == CANCELLED;
        }

        if (this == AWAITING_PAYMENT) {
            return target == CONFIRMED || target == CANCELLED;
        }

        return false;
    }
}
