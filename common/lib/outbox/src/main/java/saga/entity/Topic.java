package saga.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Topic {
    ORDER_EVENTS("order-events"),
    INVENTORY_COMMANDS("inventory-commands"),
    PAYMENT_COMMANDS("payment-commands");

    private String code;

    Topic(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static Topic fromCode(String code) {
        for (Topic topic: values()) {
            if (topic.code.equals(code)) {
                return topic;
            }
        }

        throw new IllegalStateException("Unknown code: " + code);
    }
}
