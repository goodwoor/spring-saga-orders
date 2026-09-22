package saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import saga.entity.Item;

import java.util.List;
import java.util.Map;

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
 */
@RequestMapping("/inventory")
@RestController
public class InventoryController {
    private final ItemsRepository itemsRepository;

    @Autowired
    InventoryController(ItemsRepository itemsRepository) {
        this.itemsRepository = itemsRepository;
    }

    @GetMapping("/hello")
    public ResponseEntity<List<Map<String, String>>> getHomePage()
    {
        List<Item> items = itemsRepository.findAll();

        List<Map<String, String>> response = items
                .stream()
                .map(item -> Map.of(
                        "id", item.getId().toString(),
                        "name", item.getName(),
                        "cost", item.getCost().toPlainString(),
                        "amount", item.getAmount().toString(),
                        "description", item.getDescription() == null ? "" : item.getDescription()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }
}
