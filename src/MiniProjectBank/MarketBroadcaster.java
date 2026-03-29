package MiniProjectBank;

import MiniProjectBank.ui.TradingDashboard;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The ticker plant — logs every trade and tracks the latest price per ticker.
 * Think of it as the scrolling thing at the bottom of Bloomberg TV :)
 * Also pushes updates to the dashboard if one is connected.
 */
public class MarketBroadcaster implements Runnable {
    private final BlockingQueue<Transaction> transactionQueue;

    // Append-only structure for the transaction history.
    private final List<Transaction> transactionLog = new CopyOnWriteArrayList<>();

    // Tracks the last traded price for the Price Feed
    private final ConcurrentHashMap<String, Long> lastTradedPrice = new ConcurrentHashMap<>();

    // Total volume traded per ticker
    private final ConcurrentHashMap<String, Long> volumeByTicker = new ConcurrentHashMap<>();

    // optional GUI dashboard — null if running in console mode
    private TradingDashboard dashboard;

    public MarketBroadcaster(BlockingQueue<Transaction> queue) {
        this.transactionQueue = queue;
    }

    public void setDashboard(TradingDashboard dashboard) {
        this.dashboard = dashboard;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Transaction tx = transactionQueue.take();

                transactionLog.add(tx);
                lastTradedPrice.put(tx.ticker, tx.price);
                volumeByTicker.merge(tx.ticker, tx.quantity, Long::sum);

                // push to GUI if connected
                if (dashboard != null) {
                    dashboard.onTrade(tx);
                }
            }
        } catch (InterruptedException e) {
            System.out.println("Market Broadcaster shutting down.");
            Thread.currentThread().interrupt();
        }
    }

    // end-of-day market report :)
    public void printSummary() {
        System.out.println("\n========== MARKET SUMMARY ==========");
        System.out.printf("Total trades: %d%n%n", transactionLog.size());

        System.out.println("  Ticker | Last Price |    Volume");
        System.out.println("  -------|------------|----------");
        lastTradedPrice.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String ticker = entry.getKey();
                    long price = entry.getValue();
                    long volume = volumeByTicker.getOrDefault(ticker, 0L);
                    System.out.printf("  %-6s |     $%5.2f | %,8d shares%n", ticker, price / 100.0, volume);
                });

        // last 5 trades so we can see how things ended
        int logSize = transactionLog.size();
        int start = Math.max(0, logSize - 5);
        if (logSize > 0) {
            System.out.println("\n  Last 5 trades:");
            for (int i = start; i < logSize; i++) {
                Transaction tx = transactionLog.get(i);
                System.out.printf("    [%s] %s -> %s | %d shares @ $%.2f%n",
                        tx.ticker, tx.sellerId, tx.buyerId, tx.quantity, tx.price / 100.0);
            }
        }
        System.out.println("=====================================");
    }
}
