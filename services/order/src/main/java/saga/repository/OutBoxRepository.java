package saga.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import saga.entity.OutBoxItem;

import java.util.Optional;

public interface OutBoxRepository extends JpaRepository<OutBoxItem, Long> {
    Optional<OutBoxItem> findByMessageKeyAndMessageType(String messageKey, String messageType);
}
