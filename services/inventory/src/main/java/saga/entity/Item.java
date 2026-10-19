package saga.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "items")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "item")
    private List<Reservation> reservations = new ArrayList<>();

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal cost;

    @Column(nullable = false)
    private Integer amount;

    @Column(length = 200, nullable = false)
    private String name;

    private String description;

    public Item(BigDecimal cost, Integer amount, String name, String description) {
        this.cost = cost;
        this.amount = amount;
        this.name = name;
        this.description = description;
    }

    protected Item() {}

    public List<Reservation> getReservations() {
        return Collections.unmodifiableList(reservations);
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object item) {
        if (this == item) {
            return true;
        }

        if (!(item instanceof Item)) {
            return false;
        }

        Item typedItem = (Item) item;
        return this.getId() != null && this.getId().equals(typedItem.getId());
    }

    @Override
    public int hashCode() {
        return Item.class.hashCode();
    }
}
