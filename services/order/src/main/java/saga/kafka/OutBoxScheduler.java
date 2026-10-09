package saga.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import saga.commands.CreateReserveCommand;
import saga.entity.OutBoxItem;
import saga.entity.OutBoxItemStatus;
import saga.events.order.OrderCreated;
import saga.repository.OutBoxRepository;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
public class OutBoxScheduler {
    private final KafkaTemplate<String, Object> producer;
    private final OutBoxRepository outBoxRepository;
    private final JsonMapper mapper;

    @Autowired
    public OutBoxScheduler(
            KafkaTemplate<String, Object> producer,
            OutBoxRepository outBoxRepository,
            JsonMapper mapper
    ) {
        this.producer = producer;
        this.outBoxRepository = outBoxRepository;
        this.mapper = mapper;
    }

    //todo: пул работает в одном потоке, в будущем можно попробовать распараллелить
    @Scheduled(fixedDelayString = "1000")
    public void sendOutBoxMessages()
    {
        List<OutBoxItem> messagesForSend = outBoxRepository.findByStatusOrderByIdAsc(
                OutBoxItemStatus.NEW
        );

        for (OutBoxItem message: messagesForSend) {

            try {
                if (message.getMessageType().equals(OrderCreated.class.getSimpleName())) {
                    OrderCreated event = mapper.readValue(message.getJsonPayload(), OrderCreated.class);
                    producer.send("order-events", message.getMessageKey(), event).get();
                }
                else if (message.getMessageType().equals(CreateReserveCommand.class.getSimpleName())) {
                    CreateReserveCommand event = mapper.readValue(message.getJsonPayload(), CreateReserveCommand.class);
                    producer.send("inventory-commands", message.getMessageKey(), event).get();
                } else {
                    throw new IllegalStateException(
                            "Unknown message type: %s, message: %s".formatted(
                                    message.getMessageType(),
                                    message
                            )
                    );
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Outbox send failed, thread interrupted", exception);
            } catch (ExecutionException exception) {
                throw new IllegalStateException("Outbox send execution failed: %s".formatted(exception.getCause()), exception);
            }

            message.setStatus(OutBoxItemStatus.PROCESSED);
            outBoxRepository.save(message);
        }
    }
}
