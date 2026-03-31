package MiniProjectBank;

import MiniProjectBank.ui.TradingDashboard;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// routes orders to the right OrderBook by ticker — ConcurrentHashMap for lock-free lookups
public class StockExchange {
    private final ConcurrentHashMap<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final BlockingQueue<Transaction> portfolioQueue;
    private final BlockingQueue<Transaction> broadcasterQueue;

    // AtomicLong because multiple trader threads increment this concurrently
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
            book.processOrder(order);
            totalOrdersSubmitted.incrementAndGet();
        } else {
            // shouldn't happen unless someone typos a ticker lol
            System.err.println("Rejected: Unknown ticker " + order.ticker);
        }
    }

    /** hooks up the dashboard to every OrderBook so they can push order events to the feed */
    public void setDashboard(TradingDashboard dashboard) {
        orderBooks.values().forEach(book -> book.setDashboard(dashboard));
    }

    /** returns [bestBid, bestAsk] for a given ticker — used by the price feed on the dashboard */
    public long[] getBidAsk(String ticker) {
        OrderBook book = orderBooks.get(ticker);
        if (book == null) return new long[]{0, 0};
        return new long[]{book.getBestBid(), book.getBestAsk()};
    }

    public long getTotalOrdersSubmitted() {
        return totalOrdersSubmitted.get();
    }
}
