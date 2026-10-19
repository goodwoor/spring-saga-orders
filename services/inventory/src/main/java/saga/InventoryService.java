package saga;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import saga.commands.CreateReserveCommand;
import saga.commands.RevertReserveCommand;
import saga.dto.ItemResponse;
import saga.entity.Item;
import saga.entity.Reservation;
import saga.entity.Topic;
import saga.events.inventory.ReserveCreated;
import saga.events.inventory.ReserveReverted;
import saga.kafka.ItemsNotFoundException;
import saga.kafka.NotEnoughAmountException;
import saga.events.order.OrderLine;
import saga.kafka.OutBoxProducer;
import saga.repository.ItemsRepository;
import saga.repository.ReservationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InventoryService {
    private final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private final ItemsRepository itemsRepository;
    private final ReservationRepository reservationRepository;
    private final OutBoxProducer producer;
    private final InventoryMapper mapper;

    @Autowired
    public InventoryService(
            ItemsRepository itemsRepository,
            ReservationRepository reservationRepository,
            OutBoxProducer producer,
            InventoryMapper mapper
    ) {
        this.itemsRepository = itemsRepository;
        this.reservationRepository = reservationRepository;
        this.producer = producer;
        this.mapper = mapper;
    }

    public List<ItemResponse> findAllItems() {
        List<Item> items = itemsRepository.findAll();

        List<ItemResponse> itemsDto = items.stream()
                .map(mapper::toItemResponse)
                .toList();

        return itemsDto;
    }


    @Transactional(rollbackOn = Exception.class)
    public void reserveAndSendEvent(CreateReserveCommand command)
            throws ItemsNotFoundException, NotEnoughAmountException
    {
        reserveItems(command);
        ReserveCreated reserveCreatedEvent = mapper.toReserveCreated(command);
        producer.send(Topic.INVENTORY_EVENTS, command.orderId().toString(), reserveCreatedEvent);
    }

    private void reserveItems(CreateReserveCommand command)
            throws ItemsNotFoundException, NotEnoughAmountException
    {
        List<Reservation> oldReservations = reservationRepository.findAllByOrderId(command.orderId());

        if (!oldReservations.isEmpty()) {
            log.info(
                    "Reservations already exists, order id: %s".formatted(
                            command.orderId()
                    )
            );
            return;
        }

        List<Long> itemsIds = command.orderItems().stream()
                .map(OrderLine::itemId)
                .sorted()
                .toList();

        Map<Long, Integer> reservedAmountByItemId = command.orderItems().stream()
                .collect(
                        Collectors.toMap(
                                OrderLine::itemId,
                                OrderLine::amount
                        )
                );

        List<Item> itemsToReserve = itemsRepository.findAllByIdForUpdate(itemsIds);

        if (itemsToReserve.size() != command.orderItems().size()) {
            throw new ItemsNotFoundException(
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
                throw new NotEnoughAmountException(
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

    @Transactional(rollbackOn = Exception.class)
    public void revertReserveAndSendEvent(RevertReserveCommand command) {
        revertReserve(command.orderId());
        ReserveReverted reserveRevertedEvent = mapper.toReserveReverted(command);
        producer.send(Topic.INVENTORY_EVENTS, command.orderId().toString(), reserveRevertedEvent);
    }

    private void revertReserve(Long orderId) {
        List<Reservation> allReservations = reservationRepository.findAllByOrderId(orderId);
        if (allReservations.isEmpty()) {
            return;
        }

        List<Long> itemIds = allReservations.stream()
                .map(reservation -> reservation.getItem().getId())
                .distinct()
                .sorted()
                .toList();

        Map<Long, Item> lockedItems = itemsRepository.findAllByIdForUpdate(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, item -> item));

        for (Reservation reservation : allReservations) {
            Item reservationItem = lockedItems.get(reservation.getItem().getId());
            reservationItem.setAmount(reservationItem.getAmount() + reservation.getReservedAmount());
        }

        reservationRepository.deleteAll(allReservations);
    }
}
