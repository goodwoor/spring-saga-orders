package saga;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import saga.commands.CreateReserveCommand;
import saga.entity.Item;
import saga.events.order.OrderLine;
import saga.kafka.NotEnoughAmountException;
import saga.repository.ItemsRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(classes = InventoryApplication.class)
@Testcontainers
class InventoryReserveTest {

    private static final int RACE_ATTEMPTS = 5;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    private final InventoryService inventoryService;
    private final ItemsRepository itemsRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    InventoryReserveTest(
            InventoryService inventoryService,
            ItemsRepository itemsRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.inventoryService = inventoryService;
        this.itemsRepository = itemsRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Сценарий: два заказа одновременно резервируют последнюю единицу одного нового товара.
     * Закрывает: проигравший вызов бросает NotEnoughAmountException или OptimisticLockingFailureException, остаток не ниже нуля, резерв на товар ровно один.
     */
    @Test
    void raceLastUnitThrowsOnceAndKeepsSingleReservation() throws Exception {
        warmUp();

        String lastDetail = "no attempt";
        for (int attempt = 0; attempt < RACE_ATTEMPTS; attempt++) {
            Outcome outcome = raceOnce(attempt);
            lastDetail = outcome.detail();
            if (!outcome.serviceCallsFinished()) {
                continue;
            }
            if (!outcome.businessOk()) {
                assertRace(outcome);
            }
            if (outcome.overlapped()) {
                assertRace(outcome);
                return;
            }
        }
        fail("calls did not overlap; sequential run is not a race. Last attempt: " + lastDetail);
    }

    /**
     * Сценарий: повторный резерв с тем же orderId после успешного первого вызова.
     * Закрывает: второй вызов не бросает исключение, остаток равен значению после первого вызова, резерв по orderId один.
     */
    @Test
    void retrySameOrderDoesNotDecreaseStock() throws Exception {
        Item item = persistUnit("t3-retry");
        Long orderId = 210_001L;
        CreateReserveCommand command = commandFor(orderId, item.getId());

        inventoryService.reserveAndSendEvent(command);
        int amountAfterFirst = reloadAmount(item.getId());
        long reservationsAfterFirst = countReservationsByOrderId(orderId);

        inventoryService.reserveAndSendEvent(command);
        int amountAfterSecond = reloadAmount(item.getId());
        long reservationsAfterSecond = countReservationsByOrderId(orderId);

        assertEquals(0, amountAfterFirst);
        assertEquals(amountAfterFirst, amountAfterSecond);
        assertEquals(1L, reservationsAfterFirst);
        assertEquals(1L, reservationsAfterSecond);
    }

    private void warmUp() throws Exception {
        Item item = persistUnit("t3-warmup");
        inventoryService.reserveAndSendEvent(commandFor(210_000L, item.getId()));
    }

    private Outcome raceOnce(int attempt) throws InterruptedException {
        Item item = persistUnit("t3-race-" + attempt);
        Long itemId = item.getId();
        long firstOrderId = 110_000L + (attempt * 2L);
        long secondOrderId = firstOrderId + 1L;
        CreateReserveCommand firstCommand = commandFor(firstOrderId, itemId);
        CreateReserveCommand secondCommand = commandFor(secondOrderId, itemId);

        CyclicBarrier barrier = new CyclicBarrier(2);
        long[] started = {Long.MAX_VALUE, Long.MAX_VALUE};
        long[] finished = {Long.MIN_VALUE, Long.MIN_VALUE};
        Throwable[] errors = new Throwable[2];

        Thread first = new Thread(
                () -> reserve(firstCommand, barrier, started, finished, errors, 0),
                "reserve-a-" + attempt
        );
        Thread second = new Thread(
                () -> reserve(secondCommand, barrier, started, finished, errors, 1),
                "reserve-b-" + attempt
        );
        first.setDaemon(true);
        second.setDaemon(true);
        first.start();
        second.start();
        if (!first.join(Duration.ofSeconds(30)) || !second.join(Duration.ofSeconds(30))) {
            first.interrupt();
            second.interrupt();
            fail("reserve threads did not finish within 30s");
        }

        boolean serviceCallsFinished = started[0] != Long.MAX_VALUE
                && started[1] != Long.MAX_VALUE
                && finished[0] != Long.MIN_VALUE
                && finished[1] != Long.MIN_VALUE;
        boolean overlapped = serviceCallsFinished
                && started[0] < finished[1]
                && started[1] < finished[0];

        int successes = 0;
        int acceptedLosses = 0;
        Throwable unexpected = null;
        for (Throwable error : errors) {
            if (error == null) {
                successes++;
            } else if (isAcceptedLoss(error)) {
                acceptedLosses++;
            } else if (unexpected == null) {
                unexpected = error;
            }
        }

        int amount = reloadAmount(itemId);
        long reservations = countReservationsByItemId(itemId);
        String detail = (
                "overlapped=%s callsFinished=%s amount=%s reservations=%s successes=%s losses=%s t0=[%s] t1=[%s]"
        ).formatted(
                overlapped,
                serviceCallsFinished,
                amount,
                reservations,
                successes,
                acceptedLosses,
                describe(errors[0]),
                describe(errors[1])
        );

        return new Outcome(
                serviceCallsFinished,
                overlapped,
                successes,
                acceptedLosses,
                amount,
                reservations,
                unexpected,
                detail
        );
    }

    private void reserve(
            CreateReserveCommand command,
            CyclicBarrier barrier,
            long[] started,
            long[] finished,
            Throwable[] errors,
            int index
    ) {
        try {
            barrier.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            errors[index] = error;
            return;
        } catch (Exception error) {
            errors[index] = error;
            return;
        }

        started[index] = System.nanoTime();
        try {
            inventoryService.reserveAndSendEvent(command);
        } catch (Throwable error) {
            errors[index] = error;
        } finally {
            finished[index] = System.nanoTime();
        }
    }

    private static void assertRace(Outcome outcome) {
        if (outcome.unexpected() != null) {
            fail(outcome.detail(), outcome.unexpected());
        }
        assertEquals(1, outcome.successes(), outcome.detail());
        assertEquals(1, outcome.acceptedLosses(), outcome.detail());
        assertTrue(outcome.amount() >= 0, outcome.detail());
        assertEquals(1L, outcome.reservations(), outcome.detail());
    }

    private static boolean isAcceptedLoss(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof NotEnoughAmountException
                    || current instanceof OptimisticLockingFailureException) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return false;
    }

    private static String describe(Throwable error) {
        if (error == null) {
            return "returned";
        }
        String text = error.getClass().getName();
        if (error.getMessage() != null) {
            text = text + ": " + error.getMessage();
        }
        return text;
    }

    private Item persistUnit(String name) {
        Item item = itemsRepository.saveAndFlush(
                new Item(new BigDecimal("10.00"), 1, name, "t3")
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

    private long countReservationsByItemId(Long itemId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from reservations where item_id = ?",
                Long.class,
                itemId
        );
        return count == null ? 0L : count;
    }

    private long countReservationsByOrderId(Long orderId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from reservations where order_id = ?",
                Long.class,
                orderId
        );
        return count == null ? 0L : count;
    }

    private record Outcome(
            boolean serviceCallsFinished,
            boolean overlapped,
            int successes,
            int acceptedLosses,
            int amount,
            long reservations,
            Throwable unexpected,
            String detail
    ) {
        private boolean businessOk() {
            return unexpected == null
                    && successes == 1
                    && acceptedLosses == 1
                    && amount >= 0
                    && reservations == 1L;
        }
    }
}
