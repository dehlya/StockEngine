package MiniProjectBank;

public class Order {
    public final long orderId;
    public final String traderId;
    public final String ticker;
    public final boolean isBuy;
    public final boolean isMarketOrder; // market orders match at any price
    public final long price;            // Stored in cents to avoid double/float errors
    public final long timestamp;
    public long quantity;               // Mutable because it can be partially filled

    public Order(long orderId, String traderId, String ticker, boolean isBuy, boolean isMarketOrder, long price, long quantity) {
        this.orderId = orderId;
        this.traderId = traderId;
        this.ticker = ticker;
        this.isBuy = isBuy;
        this.isMarketOrder = isMarketOrder;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = System.nanoTime(); // High precision for FIFO tie-breaking
    }
}
