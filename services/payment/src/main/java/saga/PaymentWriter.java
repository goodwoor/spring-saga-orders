package saga;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;
import saga.entity.Payment;
import saga.repository.PaymentRepository;

@Component
public class PaymentWriter {
    private final PaymentRepository paymentRepository;

    public PaymentWriter(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment createPayment() {

        return null;
    }
}
