package saga.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "outbox")
public class OutBoxItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_key", length = 300, nullable = false)
    private String messageKey;

    @Column(name = "message_type", length = 30, nullable = false)
    private String messageType;

    @Column(name = "payload", nullable = false)
    private String jsonPayload;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private OutBoxItemStatus status;

    @Column(name = "status_changed_date", nullable = false)
    private Instant statusChangedDate;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected OutBoxItem() {};

    public OutBoxItem(String messageKey, String messageType, String jsonPayload, OutBoxItemStatus status, Instant statusChangedDate, Instant createdAt) {
        this.messageKey = messageKey;
        this.messageType = messageType;
        this.jsonPayload = jsonPayload;
        this.status = status;
        this.statusChangedDate = statusChangedDate;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageId) {
        this.messageKey = messageId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getJsonPayload() {
        return jsonPayload;
    }

    public void setJsonPayload(String jsonPayload) {
        this.jsonPayload = jsonPayload;
    }

    public OutBoxItemStatus getStatus() {
        return status;
    }

    public void setStatus(OutBoxItemStatus status) {
        this.status = status;
        this.setStatusChangedDate(Instant.now());
    }

    public Instant getStatusChangedDate() {
        return statusChangedDate;
    }

    private void setStatusChangedDate(Instant statusChangedDate) {
        this.statusChangedDate = statusChangedDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object outBoxItem) {

        if (this == outBoxItem) {
            return true;
        }

        if (!(outBoxItem instanceof OutBoxItem)) {
            return false;
        }

        OutBoxItem typedOutBoxItem = (OutBoxItem) outBoxItem;
        return this.getId() != null && this.getId().equals(typedOutBoxItem.getId());
    }

    @Override
    public int hashCode() {
        return OutBoxItem.class.hashCode();
    }
}
