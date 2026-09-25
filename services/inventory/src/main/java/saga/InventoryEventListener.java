package saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import saga.commands.CreateReserveCommand;

@Component
@KafkaListener(topics = "inventory-commands", groupId = "inventory-service")
public class InventoryEventListener {
    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);
    private final InventoryService service;

    @Autowired
    InventoryEventListener(InventoryService service) {
        this.service = service;
    }

    @KafkaHandler
    public void onReserveCommand(CreateReserveCommand command) {
        log.info("Get new command: reserve: {}", command.orderId());
        service.reserveItems();
    }

}
