package saga.kafka;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import saga.entity.OutBoxItem;
import saga.entity.OutBoxItemStatus;
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
        List<OutBoxItem> messagesForSend = outBoxRepository.findTop100ByStatusOrderByIdAsc(
                OutBoxItemStatus.NEW
        );

        for (OutBoxItem message: messagesForSend) {
            try {
                String className = message.getMessageType();
                Class<?> type = Class.forName(className);
                Object event = mapper.readValue(message.getJsonPayload(), type);
                producer.send(message.getTopic().getCode(), message.getMessageKey(), event).get();
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException(
                        "Unknown message type: %s, message: %s".formatted(
                                message.getMessageType(),
                                message
                        )
                );
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
