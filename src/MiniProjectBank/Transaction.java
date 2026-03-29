package MiniProjectBank;

public class Transaction {
    public final String ticker;
    public final String buyerId;
    public final String sellerId;
    public final long price;
    public final long quantity;
    public final long timestamp;

    public Transaction(String ticker, String buyerId, String sellerId, long price, long quantity) {
        this.ticker = ticker;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = System.currentTimeMillis();
    }
}
