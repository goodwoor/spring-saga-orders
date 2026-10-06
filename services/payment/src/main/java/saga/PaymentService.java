package saga;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
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
    public Payment createPayment(CreatePaymentCommand command)
    {
        Instant now = Instant.now();
        Payment oldPayment = paymentRepository.findByOrderId(command.orderId()).orElse(null);

        if (oldPayment != null) {
            log.info(
                    "Payment already exists, payment id: %s, order id: %s".formatted(
                            oldPayment.getId(),
                            command.orderId()
                    )
            );
            return oldPayment;
        }

        PaymentStatus status = command.userId() == 9999L
                ? PaymentStatus.FAILED
                : PaymentStatus.SUCCESS;

        Payment newPayment = new Payment(
                command.userId(),
                command.orderId(),
                status,
                now,
                now,
                command.cost()
        );

        Payment savedPayment = paymentRepository.saveAndFlush(newPayment);

        return savedPayment;
    }
}
