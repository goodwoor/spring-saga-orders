package saga.commands;

public record ReserveOrderLine (
        Long id,
        Long itemId,
        Integer amount
) {}
