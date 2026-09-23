package saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import saga.dto.ItemResponse;
import saga.entity.Item;
import saga.repository.ItemsRepository;

import java.util.List;

@Service
public class InventoryService {
    private final ItemsRepository itemsRepository;
    private final InventoryMapper mapper;

    @Autowired
    public InventoryService(
            ItemsRepository itemsRepository,
            InventoryMapper mapper
    ) {
        this.itemsRepository = itemsRepository;
        this.mapper = mapper;
    }

    public List<ItemResponse> findAllItems() {
        List<Item> items = itemsRepository.findAll();

        List<ItemResponse> itemsDto = items.stream()
                .map(mapper::toItemResponse)
                .toList();

        return itemsDto;
    }
}
