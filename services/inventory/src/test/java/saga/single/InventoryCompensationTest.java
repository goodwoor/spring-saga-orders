package saga.single;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import saga.InventoryApplication;
import saga.InventoryService;
import saga.commands.CreateReserveCommand;
import saga.commands.RevertReserveCommand;
import saga.entity.Item;
import saga.entity.OutBoxItem;
import saga.events.inventory.ReserveFailed;
import saga.events.inventory.ReserveReverted;
import saga.events.order.OrderLine;
import saga.kafka.InventoryCommandListener;
import saga.repository.ItemsRepository;
import saga.repository.OutBoxRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = InventoryApplication.class)
@Testcontainers
class InventoryCompensationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    private final InventoryCommandListener inventoryCommandListener;
    private final InventoryService inventoryService;
    private final ItemsRepository itemsRepository;
    private final OutBoxRepository outBoxRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    InventoryCompensationTest(
            InventoryCommandListener inventoryCommandListener,
            InventoryService inventoryService,
            ItemsRepository itemsRepository,
            OutBoxRepository outBoxRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.inventoryCommandListener = inventoryCommandListener;
        this.inventoryService = inventoryService;
        this.itemsRepository = itemsRepository;
        this.outBoxRepository = outBoxRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Сценарий: резерв 1 штуки нового товара с остатком 0 через listener.
     * Закрывает: outbox ReserveFailed с NOT_ENOUGH_AMOUNT, резерва нет, остаток остаётся 0.
     */
    @Test
    void notEnoughAmountWritesReserveFailed() {
        Item item = persistItem("t2-empty", 0);
        Long orderId = 310_001L;

        inventoryCommandListener.onReserveCommand(commandFor(orderId, item.getId()));

        OutBoxItem failed = outbox(orderId, ReserveFailed.class);
        assertEquals(ReserveFailed.class.getName(), failed.getMessageType());
        assertTrue(failed.getJsonPayload().contains("NOT_ENOUGH_AMOUNT"));
        assertEquals(0L, countReservationsByOrderId(orderId));
        assertEquals(0, reloadAmount(item.getId()));
    }

    /**
     * Сценарий: резерв строки с itemId, которого нет на складе.
     * Закрывает: outbox ReserveFailed с ITEMS_NOT_FOUND и ни одной резервации по orderId.
     */
    @Test
    void missingItemWritesReserveFailed() {
        Long orderId = 310_002L;

        inventoryCommandListener.onReserveCommand(commandFor(orderId, 999999L));

        OutBoxItem failed = outbox(orderId, ReserveFailed.class);
        assertEquals(ReserveFailed.class.getName(), failed.getMessageType());
        assertTrue(failed.getJsonPayload().contains("ITEMS_NOT_FOUND"));
        assertEquals(0L, countReservationsByOrderId(orderId));
    }

    /**
     * Сценарий: успешный резерв последней единицы нового товара, затем revert.
     * Закрывает: остаток снова 1, резерв снят, в outbox есть ReserveReverted.
     */
    @Test
    void revertRestoresStock() throws Exception {
        Item item = persistItem("t2-revert", 1);
        Long orderId = 310_003L;

        inventoryService.reserveAndSendEvent(commandFor(orderId, item.getId()));

        assertEquals(0, reloadAmount(item.getId()));
        assertEquals(1L, countReservationsByOrderId(orderId));

        inventoryService.revertReserveAndSendEvent(new RevertReserveCommand(orderId));

        assertEquals(1, reloadAmount(item.getId()));
        assertEquals(0L, countReservationsByOrderId(orderId));
        OutBoxItem reverted = outbox(orderId, ReserveReverted.class);
        assertEquals(ReserveReverted.class.getName(), reverted.getMessageType());
    }

    private Item persistItem(String name, int amount) {
        Item item = itemsRepository.saveAndFlush(
                new Item(new BigDecimal("10.00"), amount, name, "t2")
        );
        assertNotNull(item.getId());
        return item;
    }

    private static CreateReserveCommand commandFor(Long orderId, Long itemId) {
        return new CreateReserveCommand(
                orderId,
                42L,
                List.of(new OrderLine(null, itemId, 1, new BigDecimal("10.00"))),
                new BigDecimal("10.00")
        );
    }

    private OutBoxItem outbox(Long orderId, Class<?> messageType) {
        return outBoxRepository
                .findByMessageKeyAndMessageType(orderId.toString(), messageType.getName())
                .orElseThrow(() -> new AssertionError(
                        "missing outbox " + messageType.getName() + " for order " + orderId
                ));
    }

    private int reloadAmount(Long itemId) {
        Integer amount = jdbcTemplate.queryForObject(
                "select amount from items where id = ?",
                Integer.class,
                itemId
        );
        if (amount == null) {
            throw new IllegalStateException("item not found: " + itemId);
        }
        return amount;
    }

    private long countReservationsByOrderId(Long orderId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from reservations where order_id = ?",
                Long.class,
                orderId
        );
        return count == null ? 0L : count;
    }
}
