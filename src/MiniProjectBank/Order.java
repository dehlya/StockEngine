package MiniProjectBank;

import java.util.concurrent.atomic.AtomicLong;

public class Order {
    public final long orderId;
    public final String traderId;
    public final String ticker;
    public final boolean isBuy;
    public final boolean isMarketOrder; // market orders match at any price
    public final long price;            // Stored in cents to avoid double/float errors
    public final long timestamp;

    // AtomicLong so partial fills are safe even if someone reads it outside the OrderBook lock
    private final AtomicLong quantity;

    public Order(long orderId, String traderId, String ticker, boolean isBuy, boolean isMarketOrder, long price, long quantity) {
        this.orderId = orderId;
        this.traderId = traderId;
        this.ticker = ticker;
        this.isBuy = isBuy;
        this.isMarketOrder = isMarketOrder;
        this.price = price;
        this.quantity = new AtomicLong(quantity);
        this.timestamp = System.nanoTime(); // High precision for FIFO tie-breaking
    }

    public long getQuantity() {
        return quantity.get();
    }

    public void reduceQuantity(long amount) {
        quantity.addAndGet(-amount);
    }
}
