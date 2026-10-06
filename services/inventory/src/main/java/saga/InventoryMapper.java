package saga;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import saga.commands.CreateReserveCommand;
import saga.commands.RevertReserveCommand;
import saga.dto.ItemResponse;
import saga.entity.Item;
import saga.events.inventory.ReserveCreated;
import saga.events.inventory.ReserveReverted;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface InventoryMapper {
    ItemResponse toItemResponse(Item item);

    ReserveCreated toReserveCreated(CreateReserveCommand command);
    ReserveReverted toReserveReverted(RevertReserveCommand command);
}
