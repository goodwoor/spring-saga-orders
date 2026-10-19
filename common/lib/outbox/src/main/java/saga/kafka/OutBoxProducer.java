package saga.kafka;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import saga.entity.OutBoxItem;
import saga.entity.OutBoxItemStatus;
import saga.entity.Topic;
import saga.repository.OutBoxRepository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Component
public class OutBoxProducer {
    private final OutBoxRepository outBoxRepository;
    private final JsonMapper mapper;

    @Autowired
    OutBoxProducer(
            OutBoxRepository outBoxRepository,
            JsonMapper mapper
    )
    {
        this.outBoxRepository = outBoxRepository;
        this.mapper = mapper;
    }

    public void send(Topic topic, String key, Object message) {
        String messageType = message.getClass().getName();
        OutBoxItem oldItem = outBoxRepository.findByMessageKeyAndMessageType(
                key,
                messageType
        ).orElse(null);

        if (oldItem != null) {
            return;
        }

        String payload = mapper.writeValueAsString(message);
        Instant now = Instant.now();
        OutBoxItem newItem = new OutBoxItem(
                key,
                messageType,
                topic,
                payload,
                OutBoxItemStatus.NEW,
                now,
                now
        );

        outBoxRepository.save(newItem);
    }
}
