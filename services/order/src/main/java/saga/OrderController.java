package saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import saga.dto.OrderCreateRequest;
import saga.dto.OrderResponse;

import java.net.URI;
import java.util.List;

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
        OrderResponse response = orderService.processCreateOrder(request);
        return ResponseEntity.created(URI.create("/order/" + response.id()))
                .body(response);
    }
}
