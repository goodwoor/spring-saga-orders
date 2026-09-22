package saga.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "order_items",
        indexes = {
                @Index(name = "idx_orders_user_id", columnList = "user_id"),
                @Index(name = "idx_order_items_order_id", columnList = "order_id")
        }
)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Integer amount;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal cost;

    public OrderItem(Long itemId, Integer amount, BigDecimal cost) {
        this.itemId = itemId;
        this.amount = amount;
        this.cost = cost;
    }

    protected OrderItem () {}

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    @Override
    public boolean equals(Object orderItem) {
        if (this == orderItem) {
            return true;
        }

        if (!(orderItem instanceof OrderItem)) {
            return false;
        }

        OrderItem typedOrderItem = (OrderItem) orderItem;
        return this.getId() != null && this.getId().equals(typedOrderItem.getId());
    }

    @Override
    public int hashCode() {
        return OrderItem.class.hashCode();
    }
}
