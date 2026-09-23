package saga;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import saga.dto.ItemResponse;
import saga.entity.Item;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface InventoryMapper {
    ItemResponse toItemResponse(Item item);
}
