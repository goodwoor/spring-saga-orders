package saga.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import saga.InventoryService;
import saga.commands.CreateReserveCommand;
import saga.commands.RevertReserveCommand;
import saga.entity.Topic;
import saga.events.inventory.ReserveFailed;
import saga.events.inventory.ReserveFailedReason;
import saga.events.inventory.RevertReserveFailed;

@Component
@KafkaListener(topics = "inventory-commands", groupId = "inventory-service")
public class InventoryCommandListener {
    private static final Logger log = LoggerFactory.getLogger(InventoryCommandListener.class);
    private final InventoryService service;
    private final OutBoxProducer producer;

    @Autowired
    InventoryCommandListener(
            InventoryService service,
            OutBoxProducer producer
    ) {
        this.service = service;
        this.producer = producer;
    }

    @KafkaHandler
    public void onReserveCommand(CreateReserveCommand command) {
        log.info("Received command: create reserve: {}", command.orderId());
        ReserveFailed failedEvent = null;

        try {
            service.reserveAndSendEvent(command);
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
        } catch (OptimisticLockingFailureException exception) {
            failedEvent = new ReserveFailed(
                    command.orderId(),
                    ReserveFailedReason.TOO_MANY_ATTEMPTS
            );
        }

        if (failedEvent != null) {
            log.warn("Reserve failed, order id: {}, reason: {}", command.orderId(), failedEvent.reason());
            producer.send(Topic.INVENTORY_EVENTS, failedEvent.orderId().toString(), failedEvent);
        }
    }

    @KafkaHandler
    public void onRevertReserveCommand(RevertReserveCommand command) {
        log.info("Received command: revert reserve: {}", command.orderId());
        RevertReserveFailed failedEvent = null;

        try {
            service.revertReserveAndSendEvent(command);
        } catch (OptimisticLockingFailureException exception) {
            failedEvent = new RevertReserveFailed(
                    command.orderId(),
                    ReserveFailedReason.TOO_MANY_ATTEMPTS
            );
        }

        if (failedEvent != null) {
            log.warn("Revert reserve failed, order id: {}, reason: {}", command.orderId(), failedEvent.reason());
            producer.send(Topic.INVENTORY_EVENTS, failedEvent.orderId().toString(), failedEvent);
        }
    }

    @KafkaHandler(isDefault = true)
    public void onUnknown(Object unknownEvent) {
        log.warn("Unknown Kafka payload, skipping: {}", unknownEvent.getClass().getName());
    }
}
