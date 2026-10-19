package saga.kafka;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import saga.entity.OutBoxItem;
import saga.entity.OutBoxItemStatus;
import saga.repository.OutBoxRepository;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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

    @Scheduled(fixedDelayString = "50")
    public void sendOutBoxMessages() {
        List<OutBoxItem> messagesForSend = outBoxRepository.findTop100ByStatusOrderByIdAsc(
                OutBoxItemStatus.NEW
        );
        if (messagesForSend.isEmpty()) {
            return;
        }

        List<CompletableFuture<?>> sends = new ArrayList<>(messagesForSend.size());
        for (OutBoxItem message : messagesForSend) {
            try {
                String className = message.getMessageType();
                Class<?> type = Class.forName(className);
                Object event = mapper.readValue(message.getJsonPayload(), type);
                sends.add(producer.send(
                        message.getTopic().getCode(),
                        message.getMessageKey(),
                        event
                ));
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException(
                        "Unknown message type: %s, message: %s".formatted(
                                message.getMessageType(),
                                message
                        ),
                        exception
                );
            }
        }

        try {
            CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Outbox send failed, thread interrupted", cause);
            }
            throw new IllegalStateException(
                    "Outbox send execution failed: %s".formatted(cause),
                    exception
            );
        }

        messagesForSend.forEach(message -> message.setStatus(OutBoxItemStatus.PROCESSED));
        outBoxRepository.saveAll(messagesForSend);
    }
}
