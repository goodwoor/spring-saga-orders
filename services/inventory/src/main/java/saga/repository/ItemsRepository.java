package saga.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import saga.entity.Item;

import java.util.Collection;
import java.util.List;

@Repository
public interface ItemsRepository extends JpaRepository<Item, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Item i WHERE i.id in :ids ORDER BY i.id")
    List<Item> findAllByIdForUpdate(Collection<Long> ids);
}
