package saga;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import saga.dto.ItemResponse;
import saga.entity.Item;
import saga.repository.ItemsRepository;

import java.util.List;

@Service
public class InventoryService {
    private final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private final ItemsRepository itemsRepository;
    private final InventoryMapper mapper;
    private final KafkaTemplate<String, Object> producer;

    @Autowired
    public InventoryService(
            ItemsRepository itemsRepository,
            InventoryMapper mapper,
            KafkaTemplate<String, Object> producer
    ) {
        this.itemsRepository = itemsRepository;
        this.mapper = mapper;
        this.producer = producer;
    }

    public List<ItemResponse> findAllItems() {
        List<Item> items = itemsRepository.findAll();

        List<ItemResponse> itemsDto = items.stream()
                .map(mapper::toItemResponse)
                .toList();

        return itemsDto;
    }

    @Transactional
    public List<ItemResponse> reserveItems() {



        return null;
    }
}
