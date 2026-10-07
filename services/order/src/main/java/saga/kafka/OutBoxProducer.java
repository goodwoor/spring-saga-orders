package saga.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import saga.entity.OutBoxItem;
import saga.entity.OutBoxItemStatus;
import saga.repository.OutBoxRepository;

import java.time.Instant;

@Component
public class OutBoxProducer {
    private final OutBoxRepository outBoxRepository;
    private final ObjectMapper objectMapper;
    private final Logger log = LoggerFactory.getLogger(OutBoxProducer.class);

    @Autowired
    OutBoxProducer(
            OutBoxRepository outBoxRepository,
            ObjectMapper objectMapper
    )
    {
        this.outBoxRepository = outBoxRepository;
        this.objectMapper = objectMapper;
    }

    public void send(String key, Object message)
            throws JsonProcessingException
    {
        String messageType = message.getClass().getSimpleName().toString();
        OutBoxItem oldItem = outBoxRepository.findByMessageKeyAndMessageType(
                key,
                messageType
        ).orElse(null);

        if (oldItem != null) {
            return;
        }

        String payload = objectMapper.writeValueAsString(message);
        Instant now = Instant.now();
        OutBoxItem newItem = new OutBoxItem(
                key,
                messageType,
                payload,
                OutBoxItemStatus.NEW,
                now,
                now
        );

        outBoxRepository.save(newItem);
    }
}
