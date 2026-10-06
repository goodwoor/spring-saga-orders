package saga.kafka;

public class ItemsNotFoundException extends Exception {
    public ItemsNotFoundException(String message) {
        super(message);
    }
}
