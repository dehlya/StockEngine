package MiniProjectBank;

import MiniProjectBank.ui.TradingDashboard;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Order book for a single ticker — bids and asks get matched when prices cross.
 * Each ticker has its own OrderBook so they can process in parallel :)
 * Two orders on the same ticker have to wait their turn though (synchronized).
 *
 * Supports both Limit orders (specific price) and Market orders (match at any price).
 * Market buys get MAX_VALUE price so they always cross, market sells get 0.
 */
public class OrderBook {
    public final String ticker;

    private final BlockingQueue<Transaction> portfolioQueue;
    private final BlockingQueue<Transaction> broadcasterQueue;

    // optional dashboard reference for logging order submissions
    private TradingDashboard dashboard;

    // Bids (Buys): Max-Heap (Highest price first. If prices equal, oldest timestamp first)
    private final PriorityQueue<Order> bids = new PriorityQueue<>(
            Comparator.<Order>comparingLong(o -> o.price).reversed()
                    .thenComparingLong(o -> o.timestamp)
    );

    // Asks (Sells): Min-Heap (Lowest price first. If prices equal, oldest timestamp first)
    private final PriorityQueue<Order> asks = new PriorityQueue<>(
            Comparator.<Order>comparingLong(o -> o.price)
                    .thenComparingLong(o -> o.timestamp)
    );

    // best bid/ask — updated after every match attempt so the price feed can read them
    private volatile long bestBid = 0;
    private volatile long bestAsk = 0;

    // last traded price — needed when two market orders match each other
    private long lastTradedPrice = 0;

    public OrderBook(String ticker, BlockingQueue<Transaction> portfolioQueue, BlockingQueue<Transaction> broadcasterQueue) {
        this.ticker = ticker;
        this.portfolioQueue = portfolioQueue;
        this.broadcasterQueue = broadcasterQueue;
    }

    public void setDashboard(TradingDashboard dashboard) {
        this.dashboard = dashboard;
    }

    // Thread-safe at the Ticker level
    public synchronized void processOrder(Order newOrder) {
        if (newOrder.isBuy) {
            bids.add(newOrder);
        } else {
            asks.add(newOrder);
        }

        // log the order submission to the dashboard feed
        if (dashboard != null) {
            dashboard.onOrderSubmitted(newOrder);
        }

        matchOrders();
        updateBestPrices();
    }

    private void matchOrders() {
        // keep matching while the best bid can cross the best ask
        while (!bids.isEmpty() && !asks.isEmpty() && bids.peek().price >= asks.peek().price) {
            Order bestBidOrder = bids.poll();
            Order bestAskOrder = asks.poll();

            // for market orders the match price comes from the limit side
            long matchPrice;
            if (bestBidOrder.isMarketOrder && bestAskOrder.isMarketOrder) {
                // both market — use last traded price, or default to $100 if no history yet
                matchPrice = lastTradedPrice > 0 ? lastTradedPrice : 10000;
            } else if (bestBidOrder.isMarketOrder) {
                matchPrice = bestAskOrder.price;
            } else if (bestAskOrder.isMarketOrder) {
                matchPrice = bestBidOrder.price;
            } else {
                // both limit orders — resting order (whoever was there first) sets the price
                matchPrice = bestBidOrder.timestamp < bestAskOrder.timestamp ? bestBidOrder.price : bestAskOrder.price;
            }

            long matchQuantity = Math.min(bestBidOrder.quantity, bestAskOrder.quantity);

            executeTrade(bestBidOrder, bestAskOrder, matchPrice, matchQuantity);

            // leftover quantity goes back into the book
            bestBidOrder.quantity -= matchQuantity;
            bestAskOrder.quantity -= matchQuantity;

            if (bestBidOrder.quantity > 0) {
                bids.add(bestBidOrder);
            }
            if (bestAskOrder.quantity > 0) {
                asks.add(bestAskOrder);
            }
        }
    }

    private void updateBestPrices() {
        // skip market orders when reporting best bid/ask — only show real limit prices
        bestBid = bids.stream().filter(o -> !o.isMarketOrder).mapToLong(o -> o.price).max().orElse(0);
        bestAsk = asks.stream().filter(o -> !o.isMarketOrder).mapToLong(o -> o.price).min().orElse(0);
    }

    public long getBestBid() { return bestBid; }
    public long getBestAsk() { return bestAsk; }

    private void executeTrade(Order bid, Order ask, long price, long quantity) {
        lastTradedPrice = price;
        Transaction tx = new Transaction(ticker, bid.traderId, ask.traderId, price, quantity);

        // fan out to both downstream consumers
        portfolioQueue.offer(tx);
        broadcasterQueue.offer(tx);

        System.out.printf("TRADE [%s]: %s bought %d from %s @ $%.2f%n",
                ticker, bid.traderId, quantity, ask.traderId, price / 100.0);
    }
}
