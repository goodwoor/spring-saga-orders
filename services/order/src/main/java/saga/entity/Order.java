package saga.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal cost;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "status_changed_date", nullable = false)
    private Instant statusChangedDate;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected Order() {}

    public Order(Long userId, List<OrderItem> orderItems, BigDecimal cost, OrderStatus status, Instant statusChangedDate, Instant createdAt) {
        this.userId = userId;
        this.cost = cost;
        this.status = status;
        this.statusChangedDate = statusChangedDate;
        this.createdAt = createdAt;

        if (orderItems == null) {
            return;
        }
        for (OrderItem item: orderItems) {
            this.addItem(item);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Instant getStatusChangedDate() {
        return statusChangedDate;
    }

    public void setStatusChangedDate(Instant statusChangedDate) {
        this.statusChangedDate = statusChangedDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<OrderItem> getOrderItems() {
        return Collections.unmodifiableList(orderItems);
    }

    public void addItem(OrderItem item) {
        orderItems.add(item);
        item.setOrder(this);
    }

    @Override
    public boolean equals(Object order) {
        if (order == this) {
            return true;
        }

        if (!(order instanceof Order)) {
            return false;
        }

        Order typedOrder = (Order) order;
        return this.getId() != null && this.getId().equals(typedOrder.getId());
    }

    @Override
    public int hashCode() {
        return Order.class.hashCode();
    }
}
