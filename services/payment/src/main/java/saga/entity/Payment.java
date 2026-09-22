package saga.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_payments_order_id",
                columnNames = "order_id"
        ),
        indexes = @Index(name = "idx_payments_user_id", columnList = "user_id")
)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(length = 30, nullable = false)
    private String status;

    @Column(name = "status_changed_date", nullable = false)
    private Instant statusChangedDate;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal cost;

    protected Payment() {}

    public Payment(Long userId, Long orderId, String status, Instant statusChangedDate, Instant createdAt, BigDecimal cost) {
        this.userId = userId;
        this.orderId = orderId;
        this.status = status;
        this.statusChangedDate = statusChangedDate;
        this.createdAt = createdAt;
        this.cost = cost;
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

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    @Override
    public boolean equals(Object payment) {
        if (this == payment) {
            return true;
        }

        if (!(payment instanceof Payment)) {
            return false;
        }

        Payment typedPayment = (Payment) payment;
        return this.getId() != null && this.getId().equals(typedPayment.getId());
    }

    @Override
    public int hashCode() {
        return Payment.class.hashCode();
    }
}
