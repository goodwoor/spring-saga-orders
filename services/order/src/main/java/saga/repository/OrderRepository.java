package saga.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import saga.entity.Order;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Сначала id страницы, потом заказы с позициями: Pageable вместе с fetch коллекции
    // Hibernate режет в памяти (HHH90003004), то есть всё равно тянет всю таблицу.
    @Query("SELECT o.id FROM Order o ORDER BY o.id DESC")
    List<Long> findLatestIds(Pageable pageable);

    @EntityGraph(attributePaths = "orderItems")
    @Query("SELECT o FROM Order o WHERE o.id IN :ids ORDER BY o.id DESC")
    List<Order> findAllWithItemsByIds(@Param("ids") List<Long> ids);

    @EntityGraph(attributePaths = "orderItems")
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findWithItems(@Param("id") Long id);
}
