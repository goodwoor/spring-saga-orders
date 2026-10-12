package saga.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import saga.entity.OutBoxItem;
import saga.entity.OutBoxItemStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface OutBoxRepository extends JpaRepository<OutBoxItem, Long> {
    Optional<OutBoxItem> findByMessageKeyAndMessageType(String messageKey, String messageType);
    List<OutBoxItem> findTop100ByStatusOrderByIdAsc(OutBoxItemStatus status);
}
