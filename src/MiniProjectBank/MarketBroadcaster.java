package MiniProjectBank;

import MiniProjectBank.ui.TradingDashboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The "Ticker Plant" — dedicated thread that consumes executed transactions and keeps
 * a running record of everything that happened on the exchange.
 *
 * Tracks three things:
 *  1. Transaction log — append-only list of every trade (Collections.synchronizedList
 *     so adds are cheap even at 20k+ trades — way better than CopyOnWriteArrayList)
 *  2. Last traded price per ticker — the "price feed"
 *  3. Volume per ticker — total shares traded
 *
 * If a TradingDashboard is connected, every trade gets pushed to it in real time
 * so the GUI can update the charts and feed. If not (headless mode), it just logs
 * to the internal structures and prints a summary at the end.
 *
 * Think of it as those live stock price tickers you see when you google a company's stock :)
 */
public class MarketBroadcaster implements Runnable {
    private final BlockingQueue<Transaction> transactionQueue;

    // append-only — never deleted, only added to.
    // synchronizedList wraps an ArrayList so we don't copy the whole array on every add()
    // (CopyOnWriteArrayList was killing performance at 20k+ trades)
    private final List<Transaction> transactionLog = Collections.synchronizedList(new ArrayList<>());

    // last executed price per ticker
    private final ConcurrentHashMap<String, Long> lastTradedPrice = new ConcurrentHashMap<>();

    // cumulative volume per ticker
    private final ConcurrentHashMap<String, Long> volumeByTicker = new ConcurrentHashMap<>();

    // optional GUI hook — null if we're running without a dashboard
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
                // blocks until a transaction arrives from the matching engine
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

    /** returns the full transaction log — used by replay mode to scrub through history */
    public List<Transaction> getTransactionLog() {
        return transactionLog;
    }

    /** end-of-day market report — shows final prices, volumes, and last few trades */
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

        // show the last 5 trades so we can see how things ended
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
