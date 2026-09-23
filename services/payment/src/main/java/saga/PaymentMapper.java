package saga;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import saga.entity.Payment;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PaymentMapper {
    PaymentResponse toPaymentResponse(Payment payment);
}
