package saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import saga.entity.Payment;

import java.util.List;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentMapper mapper;
    private final KafkaTemplate<String, Object> producer;

    @Autowired
    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentMapper mapper,
            KafkaTemplate<String, Object> producer
    ) {
        this.paymentRepository = paymentRepository;
        this.mapper = mapper;
        this.producer = producer;
    }

    public List<PaymentResponse> findAllPayments() {
        List<Payment> payments = paymentRepository.findAll();

        List<PaymentResponse> paymentsDto = payments.stream()
                .map(mapper::toPaymentResponse)
                .toList();

        return paymentsDto;
    }
}
