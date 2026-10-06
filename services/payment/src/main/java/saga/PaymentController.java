package saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import saga.dto.PaymentResponse;

import java.util.List;

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

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getAllPayments()
    {
        List<PaymentResponse> response = paymentService.findAllPayments();
        return ResponseEntity.ok(response);
    }
}
