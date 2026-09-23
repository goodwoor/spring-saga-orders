package saga.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import saga.entity.Item;

@Repository
public interface ItemsRepository extends JpaRepository<Item, Long> {

}
