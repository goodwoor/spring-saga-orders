package saga.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "reservations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_reservations_order_item",
                columnNames = {"order_id", "item_id"}
        ),
        indexes = @Index(name = "idx_reservations_item_id", columnList = "item_id")
)

public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "reserved_amount", nullable = false)
    private Integer reservedAmount;

    public Reservation(Long orderId, Item item, Integer reservedAmount) {
        this.orderId = orderId;
        this.item = item;
        this.reservedAmount = reservedAmount;
    }

    protected Reservation() {}

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public Integer getReservedAmount() {
        return reservedAmount;
    }

    public void setReservedAmount(Integer reservedAmount) {
        this.reservedAmount = reservedAmount;
    }

    @Override
    public boolean equals(Object reservation) {
        if (this == reservation) {
            return true;
        }

        if (!(reservation instanceof Reservation)) {
            return false;
        }

        Reservation typedReservation = (Reservation) reservation;
        return this.getId() != null && this.getId().equals(typedReservation.getId());
    }

    @Override
    public int hashCode() {
        return Reservation.class.hashCode();
    }
}
