package MiniProjectBank;

import MiniProjectBank.ui.TradingDashboard;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;

/**
 * The heart of the matching engine — one OrderBook per ticker.
 *
 * Each book holds two heaps: bids (buys, highest first) and asks (sells, lowest first).
 * When a new order comes in we try to match it against the opposite side. If the best bid
 * price >= best ask price, we have a deal and execute the trade :)
 *
 * Thread safety: processOrder() is synchronized so two threads can't mess with the same
 * ticker's book at the same time. But different tickers can be processed in parallel
 * since each has its own OrderBook — that's where the concurrency wins come from.
 *
 * Supports both Limit orders (specific price) and Market orders (match at any available price).
 * Market buys use Long.MAX_VALUE so they always cross, market sells use 0. If two market
 * orders meet each other (rare but possible), we fall back to the last traded price.
 */
public class OrderBook {
    public final String ticker;

    private final BlockingQueue<Transaction> portfolioQueue;
    private final BlockingQueue<Transaction> broadcasterQueue;

    // optional — for pushing order submissions to the GUI feed
    private TradingDashboard dashboard;

    // bids = buy orders. max-heap so the highest bidder gets served first.
    // if two bids have the same price, the older one wins (FIFO)
    private final PriorityQueue<Order> bids = new PriorityQueue<>(
            Comparator.<Order>comparingLong(o -> o.price).reversed()
                    .thenComparingLong(o -> o.timestamp)
    );

    // asks = sell orders. min-heap so the cheapest seller gets matched first.
    private final PriorityQueue<Order> asks = new PriorityQueue<>(
            Comparator.<Order>comparingLong(o -> o.price)
                    .thenComparingLong(o -> o.timestamp)
    );

    // volatile so other threads (like the dashboard) can read them without locking
    private volatile long bestBid = 0;
    private volatile long bestAsk = 0;

    // when two market orders match we need a fallback price — this is it
    private long lastTradedPrice = 0;

    public OrderBook(String ticker, BlockingQueue<Transaction> portfolioQueue, BlockingQueue<Transaction> broadcasterQueue) {
        this.ticker = ticker;
        this.portfolioQueue = portfolioQueue;
        this.broadcasterQueue = broadcasterQueue;
    }

    public void setDashboard(TradingDashboard dashboard) {
        this.dashboard = dashboard;
    }

    /**
     * Main entry point — adds the order to the book and tries to match.
     * synchronized at the ticker level so we don't get race conditions on the heaps.
     */
    public synchronized void processOrder(Order newOrder) {
        if (newOrder.isBuy) {
            bids.add(newOrder);
        } else {
            asks.add(newOrder);
        }

        // let the dashboard know a new order just came in
        if (dashboard != null) {
            dashboard.onOrderSubmitted(newOrder);
        }

        matchOrders();
        updateBestPrices();
    }

    /**
     * The actual matching loop — keeps going as long as the best bid crosses the best ask.
     * Partially filled orders get their quantity reduced and go back into the heap.
     */
    private void matchOrders() {
        while (!bids.isEmpty() && !asks.isEmpty() && bids.peek().price >= asks.peek().price) {
            Order bestBidOrder = bids.poll();
            Order bestAskOrder = asks.poll();

            // figure out the execution price depending on order types
            long matchPrice;
            if (bestBidOrder.isMarketOrder && bestAskOrder.isMarketOrder) {
                // both market orders — super rare, use last known price or default $100
                matchPrice = lastTradedPrice > 0 ? lastTradedPrice : 10000;
            } else if (bestBidOrder.isMarketOrder) {
                // market buy meets limit sell — seller's price wins
                matchPrice = bestAskOrder.price;
            } else if (bestAskOrder.isMarketOrder) {
                // limit buy meets market sell — buyer's price wins
                matchPrice = bestBidOrder.price;
            } else {
                // both limit orders — the resting order (whoever was first) sets the price
                matchPrice = bestBidOrder.timestamp < bestAskOrder.timestamp ? bestBidOrder.price : bestAskOrder.price;
            }

            long matchQuantity = Math.min(bestBidOrder.quantity, bestAskOrder.quantity);

            executeTrade(bestBidOrder, bestAskOrder, matchPrice, matchQuantity);

            // if there's leftover quantity, put the remainder back
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

    /**
     * Recalculates the best bid/ask after matching.
     * Filters out market orders since their prices are fake (MAX_VALUE / 0).
     */
    private void updateBestPrices() {
        bestBid = bids.stream().filter(o -> !o.isMarketOrder).mapToLong(o -> o.price).max().orElse(0);
        bestAsk = asks.stream().filter(o -> !o.isMarketOrder).mapToLong(o -> o.price).min().orElse(0);
    }

    public long getBestBid() { return bestBid; }
    public long getBestAsk() { return bestAsk; }

    /**
     * Creates a Transaction and fans it out to both the PortfolioManager
     * and MarketBroadcaster queues. They'll pick it up asynchronously.
     */
    private void executeTrade(Order bid, Order ask, long price, long quantity) {
        lastTradedPrice = price;
        Transaction tx = new Transaction(ticker, bid.traderId, ask.traderId, price, quantity);

        portfolioQueue.offer(tx);
        broadcasterQueue.offer(tx);

        System.out.printf("TRADE [%s]: %s bought %d from %s @ $%.2f%n",
                ticker, bid.traderId, quantity, ask.traderId, price / 100.0);
    }
}
