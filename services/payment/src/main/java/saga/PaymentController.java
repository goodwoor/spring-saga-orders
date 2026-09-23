package saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * TODO: ошибки HTTP — @RestControllerAdvice + ProblemDetail.
 * Сервис кидает доменные unchecked (not found / conflict). Не ловить Exception → 500 с message.
 * Advice только HTTP, Kafka-consumer не покрывает.
 */
@RequestMapping("/payment")
@RestController
public class PaymentController {
    private final PaymentService paymentService;

    @Autowired
    public PaymentController(
            PaymentService paymentService
    ) {
        this.paymentService = paymentService;
    }

    @GetMapping("/hello")
    public ResponseEntity<List<PaymentResponse>> getHomePage()
    {
        List<PaymentResponse> response = paymentService.findAllPayments();
        return ResponseEntity.ok(response);
    }
}
