package saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import saga.commands.CreateReserveCommand;
import saga.events.ReserveCreated;

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
        service.reserveItems(command);

        ReserveCreated reserveCreatedEvent = new ReserveCreated(
                command.orderId(),
                command.userId(),
                command.cost()
        );

        producer.send("inventory-events", command.orderId().toString(), reserveCreatedEvent);
    }
}
