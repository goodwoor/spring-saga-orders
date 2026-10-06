package saga;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import saga.commands.CreatePaymentCommand;
import saga.dto.PaymentResponse;
import saga.entity.Payment;
import saga.entity.PaymentStatus;
import saga.repository.PaymentRepository;

import java.time.Instant;
import java.util.List;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentMapper mapper;

    @Autowired
    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentMapper mapper
    ) {
        this.paymentRepository = paymentRepository;
        this.mapper = mapper;
    }

    public List<PaymentResponse> findAllPayments() {
        List<Payment> payments = paymentRepository.findAll();

        List<PaymentResponse> paymentsDto = payments.stream()
                .map(mapper::toPaymentResponse)
                .toList();

        return paymentsDto;
    }

    @Transactional
    public Payment createPayment(CreatePaymentCommand command) {
        Instant now = Instant.now();
        // TODO шаг 5: константа DECLINED_USER_ID = 9999L → статус FAILED
        // TODO шаг 5: если платёж по order_id уже есть — вернуть его, не вставлять
        Payment newPayment = new Payment(
                command.userId(),
                command.orderId(),
                PaymentStatus.SUCCESS,
                now,
                now,
                command.cost()
        );

        Payment savedPayment = paymentRepository.saveAndFlush(newPayment);

        return savedPayment;
    }
}
