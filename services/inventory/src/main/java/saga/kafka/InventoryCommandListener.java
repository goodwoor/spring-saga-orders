package saga.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import saga.InventoryService;
import saga.commands.CreateReserveCommand;
import saga.events.reserve.ReserveCreated;
import saga.events.reserve.ReserveFailed;
import saga.events.reserve.ReserveFailedReason;

@Component
@KafkaListener(topics = "inventory-commands", groupId = "inventory-service")
public class InventoryCommandListener {
    private static final Logger log = LoggerFactory.getLogger(InventoryCommandListener.class);
    private final InventoryService service;
    private final KafkaTemplate<String, Object> producer;

    @Autowired
    InventoryCommandListener(
            InventoryService service,
            KafkaTemplate<String, Object> producer
    ) {
        this.service = service;
        this.producer = producer;
    }

    @KafkaHandler
    public void onReserveCommand(CreateReserveCommand command) {
        log.info("Received command: reserve: {}", command.orderId());
        ReserveFailed failedEvent = null;

        try {
            service.reserveItems(command);
        } catch (NotEnoughAmountException exception) {
            failedEvent = new ReserveFailed(
                    command.orderId(),
                    ReserveFailedReason.NOT_ENOUGH_AMOUNT
            );
        } catch (ItemsNotFoundException exception) {
            failedEvent = new ReserveFailed(
                    command.orderId(),
                    ReserveFailedReason.ITEMS_NOT_FOUND
            );
        }

        if (failedEvent != null) {
            log.warn("Reserve failed, order id: {}, reason: {}", command.orderId(), failedEvent.reason());
            producer.send("inventory-events", failedEvent.orderId().toString(), failedEvent);
            return;
        }

        ReserveCreated reserveCreatedEvent = new ReserveCreated(
                command.orderId(),
                command.userId(),
                command.cost()
        );

        producer.send("inventory-events", command.orderId().toString(), reserveCreatedEvent);
    }

    // TODO шаг 5: @KafkaHandler onReleaseCommand(ReleaseReserveCommand) → releaseItems → ReserveReleased
}
