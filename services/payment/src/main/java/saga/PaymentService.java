package saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import saga.commands.CreatePaymentCommand;
import saga.dto.PaymentResponse;
import saga.entity.Payment;
import saga.repository.PaymentRepository;

import java.util.List;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentWriter paymentWriter;
    private final PaymentMapper mapper;

    @Autowired
    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentWriter paymentWriter,
            PaymentMapper mapper
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentWriter = paymentWriter;
        this.mapper = mapper;
    }

    public List<PaymentResponse> findAllPayments() {
        List<Payment> payments = paymentRepository.findAll();

        List<PaymentResponse> paymentsDto = payments.stream()
                .map(mapper::toPaymentResponse)
                .toList();

        return paymentsDto;
    }

    //todo: продумать, как решается ситуация с изменением цен товаров с момента создания заказа до его оплаты
    public void processPayment(CreatePaymentCommand command) {

    }

    private
}
