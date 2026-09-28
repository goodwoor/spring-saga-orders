package saga;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import saga.commands.CreateReserveCommand;
import saga.dto.ItemResponse;
import saga.entity.Item;
import saga.entity.Reservation;
import saga.events.OrderLine;
import saga.events.ReserveCreated;
import saga.repository.ItemsRepository;
import saga.repository.ReservationRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InventoryService {
    private final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private final ItemsRepository itemsRepository;
    private final ReservationRepository reservationRepository;
    private final InventoryMapper mapper;

    @Autowired
    public InventoryService(
            ItemsRepository itemsRepository,
            ReservationRepository reservationRepository,
            InventoryMapper mapper
    ) {
        this.itemsRepository = itemsRepository;
        this.reservationRepository = reservationRepository;
        this.mapper = mapper;
    }

    public List<ItemResponse> findAllItems() {
        List<Item> items = itemsRepository.findAll();

        List<ItemResponse> itemsDto = items.stream()
                .map(mapper::toItemResponse)
                .toList();

        return itemsDto;
    }

    @Transactional
    public void reserveItems(CreateReserveCommand command) {
        List<Long> itemsIds = command.orderItems().stream()
                .map(OrderLine::itemId)
                .toList();

        Map<Long, Integer> reservedAmountByItemId = command.orderItems().stream()
                .collect(
                        Collectors.toMap(
                                OrderLine::itemId,
                                OrderLine::amount
                        )
                );

        List<Item> itemsToReserve = itemsRepository.findAllById(itemsIds);

        if (itemsToReserve.size() != command.orderItems().size()) {
            throw new IllegalStateException(
                    "Items not found for order %s: requested %s, found %s".formatted(
                            command.orderId(),
                            command.orderItems().size(),
                            itemsToReserve.size()
                    )
            );
        }

        List<Reservation> reservations = new ArrayList<>();

        for (Item item: itemsToReserve) {
            Integer reservedAmount = reservedAmountByItemId.get(item.getId());
            Integer newAmount = item.getAmount() - reservedAmount;

            if (newAmount < 0) {
                throw new IllegalStateException(
                        "Not enough amount: item id: %s, amount: %s, reserved amount: %s".formatted(
                                item.getId(),
                                item.getAmount(),
                                reservedAmount
                        )
                );
            }

            item.setAmount(newAmount);

            reservations.add(
                    new Reservation(
                            command.orderId(),
                            item,
                            reservedAmountByItemId.get(item.getId())
                    )
            );
        }

        reservationRepository.saveAll(reservations);
    }
}
