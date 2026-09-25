package saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import saga.dto.ItemResponse;

import java.util.List;

/**
 * Заметки по модели, пока нет сущностей.
 * items.amount — доступный остаток.
 * Резерв: amount уменьшается, появляется строка в reservations.
 * Отмена: строка удаляется, amount увеличивается на reserved_amount.
 * Подтверждение: amount остаётся уменьшенным, строка удаляется.
 * Пополнение: прямое увеличение amount, без резерва.
 * История резерва не хранится. Состав заказа — в order_items.
 * Гонка остатка: резерв и пополнение читают один amount и пишут своё число.
 * Без сверки второй UPDATE затирает первый. Лок в Java не видит другой инстанс и Kafka-consumer.
 * @Version — CAS на строке: запись только если счётчик тот же, что прочитали; иначе OptimisticLockException, повтор с новой дельтой.
 * Альтернатива без @Version: FAA в SQL, amount = amount ± n, одно изменение применяется к текущему значению.
 *
 * TODO: ошибки HTTP — @RestControllerAdvice + ProblemDetail.
 * Сервис кидает доменные unchecked (not found / conflict). Не ловить Exception → 500 с message.
 * Advice только HTTP, Kafka-consumer не покрывает.
 */
@RequestMapping("/inventory")
@RestController
public class InventoryController {
    private final InventoryService inventoryService;

    @Autowired
    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ResponseEntity<List<ItemResponse>> getAllItems()
    {
        List<ItemResponse> response = inventoryService.findAllItems();
        return ResponseEntity.ok(response);
    }
}
