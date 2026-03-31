package MiniProjectBank;

import MiniProjectBank.ui.ReplayDashboard;
import MiniProjectBank.ui.TradingDashboard;

import javax.swing.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Entry point for the StockEngine simulation.
 *
 * Architecture overview — everything is connected via BlockingQueues:
 *
 *   TraderBots (thread pool)
 *       |
 *       v
 *   StockExchange (ConcurrentHashMap of OrderBooks)
 *       |
 *       | processOrder() is synchronized per ticker — different tickers run in parallel
 *       |
 *       v
 *   OrderBook (PriorityQueue heaps for bids/asks, matching engine logic)
 *       |
 *       | matched trades get pushed to two BlockingQueues
 *       |
 *       +-----> PortfolioManager thread (updates cash + equity for each trader)
 *       +-----> MarketBroadcaster thread (logs trades, updates price feed, pushes to GUI)
 *                    |
 *                    v
 *              TradingDashboard (Swing GUI with charts and live feed) — optional
 *
 * Three modes:
 *  - Simulation: 8 bots, ~2s delay — slow enough to follow individual orders (with GUI)
 *  - Benchmark: 200 bots, ~5ms delay — stress test, thousands of orders per second (with GUI)
 *  - Console: same as benchmark but no GUI — pure terminal output
 */
public class Main {
    public static void main(String[] args) throws InterruptedException {

        // mode selection dialog — first thing the user sees
        String[] options = {"Simulation (slow — for demo)", "Benchmark (fast — full speed)", "Console (no GUI)"};
        int choice = JOptionPane.showOptionDialog(
                null,
                "Choose a mode:\n\n"
                        + "• Simulation: slowed down so you can watch trades happen (with dashboard)\n"
                        + "• Benchmark: full speed with dashboard\n"
                        + "• Console: full speed, terminal output only — no GUI",
                "StockEngine — Mode Selection",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        boolean isConsole   = (choice == 2);
        boolean isBenchmark = (choice == 1);
        boolean isSlow      = (!isBenchmark && !isConsole);

        int traderDelay = isSlow ? 2000 : 5;
        int numTraders  = isSlow ? 8    : 200;

        String modeName = isConsole ? "CONSOLE" : (isBenchmark ? "BENCHMARK" : "SIMULATION");
        System.out.println("=== StockEngine: High-Frequency Trading Simulation ===");
        System.out.printf("Mode: %s (%d traders, ~%dms between orders)%n%n", modeName, numTraders, traderDelay);

        // --- 1. create the two shared queues ---
        // these connect the matching engine to the downstream consumers.
        // LinkedBlockingQueue is unbounded so the matching engine never blocks on offer().
        BlockingQueue<Transaction> portfolioQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Transaction> broadcasterQueue = new LinkedBlockingQueue<>();

        // --- 2. set up the exchange with 10 tickers ---
        StockExchange exchange = new StockExchange(portfolioQueue, broadcasterQueue);
        String[] tickers = {"AAPL", "TSLA", "GOOG", "MSFT", "AMZN", "NVDA", "META", "NFLX", "JPM", "BAC"};
        for (String ticker : tickers) {
            exchange.addTicker(ticker);
        }

        // --- 3. start downstream consumer threads ---
        // these block on take() until transactions arrive — they'll run until interrupted
        PortfolioManager portfolioManager = new PortfolioManager(portfolioQueue);
        MarketBroadcaster marketBroadcaster = new MarketBroadcaster(broadcasterQueue);

        // --- 4. launch the live dashboard (simulation mode only) ---
        // benchmark runs headless and opens a replay dashboard at the end instead
        TradingDashboard dashboard = null;
        Thread statsUpdater = null;

        if (isSlow) {
            dashboard = new TradingDashboard(tickers);
            dashboard.setExchange(exchange);
            exchange.setDashboard(dashboard);
            marketBroadcaster.setDashboard(dashboard);

            TradingDashboard guiRef = dashboard; // need a final ref for the lambda
            SwingUtilities.invokeLater(() -> guiRef.setVisible(true));

            // background thread to keep the dashboard order counter updated
            statsUpdater = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        guiRef.updateOrderCount(exchange.getTotalOrdersSubmitted());
                        Thread.sleep(200);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "StatsUpdater");
            statsUpdater.setDaemon(true);
            statsUpdater.start();
        }

        // daemon threads so they don't prevent JVM shutdown
        Thread portfolioThread = new Thread(portfolioManager, "PortfolioManager");
        Thread broadcasterThread = new Thread(marketBroadcaster, "MarketBroadcaster");
        portfolioThread.setDaemon(true);
        broadcasterThread.setDaemon(true);
        portfolioThread.start();
        broadcasterThread.start();

        // --- 5. unleash the trader bots ---
        // fixed thread pool — each bot gets its own thread from the pool
        ExecutorService traderPool = Executors.newFixedThreadPool(numTraders);
        System.out.printf("Launching %d traders across %d tickers...%n%n", numTraders, tickers.length);

        for (int i = 1; i <= numTraders; i++) {
            traderPool.submit(new TraderBot("Bot-" + i, exchange, tickers, traderDelay));
        }

        // --- 6. wait for all bots to finish their 100 orders each ---
        traderPool.shutdown();
        int timeout = isSlow ? 120 : 30;
        if (traderPool.awaitTermination(timeout, TimeUnit.SECONDS)) {
            System.out.println("\nAll traders done!");
        } else {
            System.out.println("\nTimeout reached :(");
            traderPool.shutdownNow();
        }

        // --- 7. let the consumer threads drain whatever's left in the queues ---
        Thread.sleep(1000);

        if (dashboard != null) {
            dashboard.updateOrderCount(exchange.getTotalOrdersSubmitted());
            dashboard.markFinished();
        }
        if (statsUpdater != null) {
            statsUpdater.interrupt();
        }

        // --- 8. clean shutdown of consumer threads ---
        portfolioThread.interrupt();
        broadcasterThread.interrupt();
        portfolioThread.join();
        broadcasterThread.join();

        // --- 9. print console summary ---
        System.out.printf("%nTotal orders submitted: %,d%n", exchange.getTotalOrdersSubmitted());
        marketBroadcaster.printSummary();
        portfolioManager.printSummary();

        // --- 10. if benchmark mode, open replay dashboard so you can scrub through history ---
        if (isBenchmark) {
            System.out.println("\nOpening replay dashboard...");
            java.util.List<Transaction> log = marketBroadcaster.getTransactionLog();
            ReplayDashboard replay = new ReplayDashboard(tickers, log);
            SwingUtilities.invokeLater(() -> replay.setVisible(true));
        }
    }
}
