package saga.kafka;

public class NotEnoughAmountException extends Exception {
    public NotEnoughAmountException(String message) {
        super(message);
    }
}
