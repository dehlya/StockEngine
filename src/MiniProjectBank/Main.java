package MiniProjectBank;

import MiniProjectBank.ui.TradingDashboard;

import javax.swing.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * StockEngine — High-Frequency Trading Simulator
 *
 * Traders, tickers, thousands of orders flying around concurrently.
 * Each ticker's OrderBook is independently synchronized so different stocks
 * get matched in parallel. The matching engine fans out executed trades to
 * a PortfolioManager and a MarketBroadcaster via BlockingQueues.
 *
 *   TraderBots --> StockExchange --> OrderBook (per ticker, synchronized)
 *                                       |
 *                             Transaction queues (BlockingQueue)
 *                              /                    \
 *                     PortfolioManager         MarketBroadcaster
 *                     (cash + equity)          (price feed + log)
 *                                                   |
 *                                            TradingDashboard (Swing GUI)
 */
public class Main {
    public static void main(String[] args) throws InterruptedException {

        // pick your speed :)
        String[] options = {"Simulation (slow — for demo)", "Benchmark (fast — full speed)"};
        int choice = JOptionPane.showOptionDialog(
                null,
                "Choose a mode:\n\n"
                        + "• Simulation: slowed down so you can watch trades happen\n"
                        + "• Benchmark: full speed, processes everything as fast as possible",
                "StockEngine — Mode Selection",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        boolean isBenchmark = (choice == 1);
        int traderDelay = isBenchmark ? 5   : 2000;   // ~2 seconds between orders — readable but not boring
        int numTraders  = isBenchmark ? 200 : 8;      // 8 bots — slightly more action

        System.out.println("=== StockEngine: High-Frequency Trading Simulation ===");
        System.out.printf("Mode: %s (%d traders, ~%dms between orders)%n%n",
                isBenchmark ? "BENCHMARK" : "SIMULATION", numTraders, traderDelay);

        // 1. shared queues
        BlockingQueue<Transaction> portfolioQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Transaction> broadcasterQueue = new LinkedBlockingQueue<>();

        // 2. exchange with 10 tickers
        StockExchange exchange = new StockExchange(portfolioQueue, broadcasterQueue);
        String[] tickers = {"AAPL", "TSLA", "GOOG", "MSFT", "AMZN", "NVDA", "META", "NFLX", "JPM", "BAC"};
        for (String ticker : tickers) {
            exchange.addTicker(ticker);
        }

        // 3. downstream service threads
        PortfolioManager portfolioManager = new PortfolioManager(portfolioQueue);
        MarketBroadcaster marketBroadcaster = new MarketBroadcaster(broadcasterQueue);

        // 4. launch the dashboard — hooks into the exchange + broadcaster
        TradingDashboard dashboard = new TradingDashboard(tickers);
        dashboard.setExchange(exchange);
        exchange.setDashboard(dashboard);
        marketBroadcaster.setDashboard(dashboard);
        SwingUtilities.invokeLater(() -> dashboard.setVisible(true));

        Thread portfolioThread = new Thread(portfolioManager, "PortfolioManager");
        Thread broadcasterThread = new Thread(marketBroadcaster, "MarketBroadcaster");
        portfolioThread.setDaemon(true);
        broadcasterThread.setDaemon(true);
        portfolioThread.start();
        broadcasterThread.start();

        // 5. unleash the traders
        ExecutorService traderPool = Executors.newFixedThreadPool(numTraders);
        System.out.printf("Launching %d traders across %d tickers...%n%n", numTraders, tickers.length);

        for (int i = 1; i <= numTraders; i++) {
            traderPool.submit(new TraderBot("Bot-" + i, exchange, tickers, traderDelay));
        }

        // 6. background thread for the order counter
        Thread statsUpdater = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    dashboard.updateOrderCount(exchange.getTotalOrdersSubmitted());
                    Thread.sleep(200);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "StatsUpdater");
        statsUpdater.setDaemon(true);
        statsUpdater.start();

        // 7. wait for all bots to finish
        traderPool.shutdown();
        int timeout = isBenchmark ? 30 : 120;
        if (traderPool.awaitTermination(timeout, TimeUnit.SECONDS)) {
            System.out.println("\nAll traders done!");
        } else {
            System.out.println("\nTimeout reached :(");
            traderPool.shutdownNow();
        }

        // 8. drain queues
        Thread.sleep(1000);

        dashboard.updateOrderCount(exchange.getTotalOrdersSubmitted());
        dashboard.markFinished();

        statsUpdater.interrupt();

        // 9. clean shutdown
        portfolioThread.interrupt();
        broadcasterThread.interrupt();
        portfolioThread.join();
        broadcasterThread.join();

        // 10. console summary too
        System.out.printf("%nTotal orders submitted: %,d%n", exchange.getTotalOrdersSubmitted());
        marketBroadcaster.printSummary();
        portfolioManager.printSummary();
    }
}
