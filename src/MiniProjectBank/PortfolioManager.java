package MiniProjectBank;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

// consumes transactions from the queue and updates each trader's cash + stock positions
public class PortfolioManager implements Runnable {
    private final BlockingQueue<Transaction> transactionQueue;

    private static final long STARTING_CASH = 10_000_000L; // $100k in cents

    // trader -> cash balance (in cents)
    private final ConcurrentHashMap<String, Long> cashBalances = new ConcurrentHashMap<>();

    // trader -> (ticker -> quantity held)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> equityPositions = new ConcurrentHashMap<>();

    private long transactionsProcessed = 0;

    public PortfolioManager(BlockingQueue<Transaction> queue) {
        this.transactionQueue = queue;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // blocks here until a transaction shows up in the queue
                Transaction tx = transactionQueue.take();
                long totalCost = tx.price * tx.quantity;

                // update cash — merge() handles first-time initialization too.
                // if the key doesn't exist yet, the default value (STARTING_CASH ± cost) is used.
                // if it does exist, the lambda runs and adjusts the old value.
                cashBalances.merge(tx.buyerId, STARTING_CASH - totalCost, (old, val) -> old - totalCost);
                cashBalances.merge(tx.sellerId, STARTING_CASH + totalCost, (old, val) -> old + totalCost);

                // update stock holdings — computeIfAbsent creates the inner map on first access
                equityPositions.computeIfAbsent(tx.buyerId, k -> new ConcurrentHashMap<>())
                        .merge(tx.ticker, tx.quantity, Long::sum);
                equityPositions.computeIfAbsent(tx.sellerId, k -> new ConcurrentHashMap<>())
                        .merge(tx.ticker, -tx.quantity, Long::sum);

                transactionsProcessed++;
            }
        } catch (InterruptedException e) {
            System.out.println("Portfolio Manager shutting down.");
            Thread.currentThread().interrupt();
        }
    }

    /** prints everyone's final portfolio — the moment of truth :) */
    public void printSummary() {
        System.out.println("\n========== PORTFOLIO SUMMARY ==========");
        System.out.printf("Transactions processed: %d%n%n", transactionsProcessed);

        cashBalances.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String traderId = entry.getKey();
                    long cash = entry.getValue();
                    double cashDollars = cash / 100.0;
                    double pnl = (cash - STARTING_CASH) / 100.0;
                    String pnlSign = pnl >= 0 ? "+" : "";

                    System.out.printf("  %-12s | Cash: $%,.2f (%s$%.2f)", traderId, cashDollars, pnlSign, pnl);

                    // show stock holdings, skip zeros since they're just noise
                    ConcurrentHashMap<String, Long> positions = equityPositions.get(traderId);
                    if (positions != null) {
                        StringBuilder sb = new StringBuilder();
                        positions.entrySet().stream()
                                .filter(e -> e.getValue() != 0)
                                .sorted(Map.Entry.comparingByKey())
                                .forEach(e -> sb.append(String.format("%s=%d ", e.getKey(), e.getValue())));
                        if (sb.length() > 0) {
                            System.out.print(" | " + sb.toString().trim());
                        }
                    }
                    System.out.println();
                });

        System.out.println("========================================");
    }
}
