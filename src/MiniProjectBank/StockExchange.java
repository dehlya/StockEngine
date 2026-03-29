package MiniProjectBank;

import MiniProjectBank.ui.TradingDashboard;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The central exchange — routes incoming orders to the right OrderBook.
 * Each ticker gets its own book so they can process in parallel :)
 */
public class StockExchange {
    // Maps the ticker symbol to its specific OrderBook
    private final ConcurrentHashMap<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final BlockingQueue<Transaction> portfolioQueue;
    private final BlockingQueue<Transaction> broadcasterQueue;

    // keeps track of how many orders came through total
    private final AtomicLong totalOrdersSubmitted = new AtomicLong(0);

    public StockExchange(BlockingQueue<Transaction> portfolioQueue, BlockingQueue<Transaction> broadcasterQueue) {
        this.portfolioQueue = portfolioQueue;
        this.broadcasterQueue = broadcasterQueue;
    }

    public void addTicker(String ticker) {
        orderBooks.putIfAbsent(ticker, new OrderBook(ticker, portfolioQueue, broadcasterQueue));
    }

    public void submitOrder(Order order) {
        OrderBook book = orderBooks.get(order.ticker);
        if (book != null) {
            // Hand off to the thread-safe OrderBook
            book.processOrder(order);
            totalOrdersSubmitted.incrementAndGet();
        } else {
            // this shouldn't really happen unless someone typos a ticker lol
            System.err.println("Rejected: Unknown ticker " + order.ticker);
        }
    }

    /** lets each OrderBook push order submissions to the dashboard feed */
    public void setDashboard(TradingDashboard dashboard) {
        orderBooks.values().forEach(book -> book.setDashboard(dashboard));
    }

    /** returns [bestBid, bestAsk] for a ticker — for the price feed */
    public long[] getBidAsk(String ticker) {
        OrderBook book = orderBooks.get(ticker);
        if (book == null) return new long[]{0, 0};
        return new long[]{book.getBestBid(), book.getBestAsk()};
    }

    public long getTotalOrdersSubmitted() {
        return totalOrdersSubmitted.get();
    }
}
