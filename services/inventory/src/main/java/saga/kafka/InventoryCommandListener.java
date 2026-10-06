package saga.kafka;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import saga.InventoryMapper;
import saga.InventoryService;
import saga.commands.CreateReserveCommand;
import saga.commands.RevertReserveCommand;
import saga.events.inventory.ReserveCreated;
import saga.events.inventory.ReserveFailed;
import saga.events.inventory.ReserveFailedReason;
import saga.events.inventory.ReserveReverted;

@Component
@KafkaListener(topics = "inventory-commands", groupId = "inventory-service")
public class InventoryCommandListener {
    private static final Logger log = LoggerFactory.getLogger(InventoryCommandListener.class);
    private final InventoryService service;
    private final KafkaTemplate<String, Object> producer;
    private final InventoryMapper mapper;

    @Autowired
    InventoryCommandListener(
            InventoryService service,
            KafkaTemplate<String, Object> producer,
            InventoryMapper mapper
    ) {
        this.service = service;
        this.producer = producer;
        this.mapper = mapper;
    }

    @KafkaHandler
    public void onReserveCommand(CreateReserveCommand command) {
        log.info("Received command: create reserve: {}", command.orderId());
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

        ReserveCreated reserveCreatedEvent = mapper.toReserveCreated(command);
        producer.send("inventory-events", command.orderId().toString(), reserveCreatedEvent);
    }

    @KafkaHandler
    public void onRevertReserveCommand(RevertReserveCommand command) {
        log.info("Received command: revert reserve: {}", command.orderId());
        service.revertReserve(command.orderId());
        ReserveReverted reserveCreatedEvent = mapper.toReserveReverted(command);
        producer.send("inventory-events", command.orderId().toString(), reserveCreatedEvent);
    }

    @KafkaHandler(isDefault = true)
    public void onUnknown(Object unknownEvent) {
        log.warn("Unknown Kafka payload, skipping: {}", unknownEvent.getClass().getName());
    }
}
