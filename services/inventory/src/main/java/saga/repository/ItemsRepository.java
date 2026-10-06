package saga.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import saga.entity.Item;
import saga.entity.Reservation;

import java.util.List;

@Repository
public interface ItemsRepository extends JpaRepository<Item, Long> {
}
