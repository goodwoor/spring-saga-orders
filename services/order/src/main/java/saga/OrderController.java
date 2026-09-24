package saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import saga.dto.OrderCreateRequest;
import saga.dto.OrderResponse;

import java.net.URI;
import java.util.List;

/**
 * Заметки по модели, пока нет сущностей.
 * id — BIGSERIAL, выдаёт Postgres. В транзакции создания читать после saveAndFlush.
 *
 * TODO: слой чтения — Service + DTO-record + MapStruct (entity → response).
 * POST создания заказа (CREATED + позиции) в свою Postgres, без Kafka.
 * TODO: библиотека ошибок проекта (common/web, не dto).
 * Общее: ProblemDetail (status, title, detail), NotFound / Conflict, @RestControllerAdvice.
 * Сервис кидает unchecked, не Exception → 500 с message. Advice только HTTP, не Kafka.
 * Кастом сервиса: свой exception + @ExceptionHandler в модуле (остальной JSON тот же).
 * Пример — optimistic lock / unique order_id → тот же 409, свой detail.
 */
@RequestMapping("/order")
@RestController
public class OrderController {
    private final OrderService orderService;

    @Autowired
    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable Long id
    )
    {
        OrderResponse response = orderService.findOrder(id);

        return response == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders()
    {
        List<OrderResponse> response = orderService.findAllOrdersWithItems();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody OrderCreateRequest request
    )
    {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.created(URI.create("/order/" + response.id()))
                .body(response);
    }
}
