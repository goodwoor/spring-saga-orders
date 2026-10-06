package saga.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import saga.entity.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    // TODO шаг 5: Optional<Payment> findByOrderId(Long orderId);
}
