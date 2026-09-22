package saga;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import saga.entity.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

}
