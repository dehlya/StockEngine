package MiniProjectBank;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class TraderBot implements Runnable {
    private final String traderId;
    private final StockExchange exchange;
    private final String[] tickers;
    private final Random random = new Random();
    private final int delayMs; // how long to wait between orders

    private static final AtomicLong orderIdGenerator = new AtomicLong(1);

    public TraderBot(String traderId, StockExchange exchange, String[] tickers, int delayMs) {
        this.traderId = traderId;
        this.exchange = exchange;
        this.tickers = tickers;
        this.delayMs = delayMs;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < 100; i++) {
                String ticker = tickers[random.nextInt(tickers.length)];
                boolean isBuy = random.nextBoolean();

                // 20% chance of a market order (no price limit — just fill it now)
                boolean isMarket = random.nextInt(5) == 0;

                // market buys use MAX price so they always cross, market sells use 0
                long price;
                if (isMarket) {
                    price = isBuy ? Long.MAX_VALUE : 0;
                } else {
                    // limit orders hover around $95-$105 range (in cents)
                    price = 9500 + random.nextInt(1001);
                }

                // quantity between 10 and 100 shares
                long quantity = 10 + random.nextInt(91);

                Order order = new Order(
                        orderIdGenerator.getAndIncrement(),
                        traderId,
                        ticker,
                        isBuy,
                        isMarket,
                        price,
                        quantity
                );

                exchange.submitOrder(order);

                // delay depends on the mode — fast for benchmark, slow for demo
                Thread.sleep(random.nextInt(delayMs) + 1);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(traderId + " was interrupted.");
        }
    }
}
