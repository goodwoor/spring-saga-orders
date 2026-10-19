package saga.e2e;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SagaFlowIT extends SagaFlowStand {

    private static final String ORDER_BODY = """
            {"userId":42,"orderItems":[{"itemId":8,"amount":1,"cost":199.00}],"cost":199.00}""";

    private static final String NOT_ENOUGH_BODY = """
            {"userId":42,"orderItems":[{"itemId":14,"amount":11,"cost":2799.00}],"cost":2799.00}""";

    private static final String PAYMENT_FAIL_BODY = """
            {"userId":9999,"orderItems":[{"itemId":8,"amount":1,"cost":199.00}],"cost":199.00}""";

    /**
     * Сценарий: три процесса order, inventory, payment и один POST /order.
     * Закрывает: заказ доходит до CONFIRMED, тест не публикует события в Kafka.
     */
    @Test
    void postOrderReachesConfirmed() throws Exception {
        String orderId = postOrder(ORDER_BODY);
        awaitStatus(orderId, "CONFIRMED");
    }

    /**
     * Сценарий: POST /order на товар 14 с amount 11 при остатке 10, userId 42.
     * Закрывает: заказ CANCELLED, остаток товара 14 равен значению до POST и не ниже 0.
     */
    @Test
    void notEnoughAmountReachesCancelled() throws Exception {
        int amountBefore = itemAmount(14);
        String orderId = postOrder(NOT_ENOUGH_BODY);
        awaitStatus(orderId, "CANCELLED");
        int amountAfter = itemAmount(14);
        assertTrue(amountAfter >= 0, "amount=" + amountAfter);
        assertEquals(amountBefore, amountAfter);
    }

    /**
     * Сценарий: POST /order с userId 9999 и одной штукой товара 8 — резерв проходит, оплата падает.
     * Закрывает: заказ CANCELLED, ровно одна строка payments FAILED, остаток товара 8 как до этого POST.
     */
    @Test
    void paymentFailedReachesCancelled() throws Exception {
        int amountBefore = itemAmount(8);
        String orderId = postOrder(PAYMENT_FAIL_BODY);
        awaitStatus(orderId, "CANCELLED");
        assertEquals(1, failedPaymentCount(Long.parseLong(orderId)));
        assertEquals(amountBefore, itemAmount(8));
    }
}
