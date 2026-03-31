# StockEngine

High-frequency trading simulator built in Java. Simulates a small stock exchange with multiple concurrent traders, a matching engine, portfolio tracking, and a live dashboard.

Built for the Concurrent Programming mini project at HES-SO Valais.

## What it does

10 stock tickers. Up to 200 trader bots. Thousands of orders flying around at the same time. The matching engine processes buy/sell orders concurrently, executes trades when prices cross, and fans out the results to a portfolio manager and a market broadcaster — all running on separate threads.

There's also a Swing GUI that shows everything happening in real time: price cards, a live chart, and a color-coded trade feed with filtering.

Two modes:
- **Simulation** — 8 bots, ~2s delay. Slow enough to actually follow what's going on
- **Benchmark** — 200 bots, ~5ms delay. Full blast, thousands of orders per second

## Architecture

```
TraderBots (ExecutorService thread pool — 1 thread per bot)
    │
    ▼
StockExchange (ConcurrentHashMap<ticker, OrderBook>)
    │
    │  submitOrder() routes to the right OrderBook
    │
    ▼
OrderBook (synchronized per ticker — different tickers run in parallel)
    │
    │  matchOrders() compares best bid vs best ask
    │  if bid.price >= ask.price → trade executes
    │
    │  matched Transaction gets pushed to two BlockingQueues
    │
    ├────► PortfolioManager thread (updates cash + stock positions)
    │        └─ BlockingQueue.take() blocks until a transaction arrives
    │
    └────► MarketBroadcaster thread (logs trades, updates price feed)
             └─ pushes to TradingDashboard (Swing GUI) if connected
```

The key idea: **each ticker has its own OrderBook, and each OrderBook is independently synchronized**. So AAPL and TSLA can process orders at the same time without waiting for each other. The only contention happens when two threads try to submit to the *same* ticker simultaneously — and that's handled by `synchronized` on `processOrder()`.

## Concurrency patterns used

### Per-resource locking (OrderBook)
Instead of one giant lock on the whole exchange, each OrderBook has its own `synchronized` block. This gives us parallelism across tickers while keeping each individual book thread-safe. Way better throughput than a global lock :)

### Producer-consumer with BlockingQueues
The matching engine *produces* Transaction objects and drops them into `LinkedBlockingQueue`s. The PortfolioManager and MarketBroadcaster *consume* them on their own threads using `take()`, which blocks until something shows up. Clean decoupling — the matching engine never waits for downstream processing.

### ConcurrentHashMap everywhere
- `StockExchange` uses it to map tickers → OrderBooks (multiple traders look up books concurrently)
- `PortfolioManager` uses it for cash balances and equity positions
- `MarketBroadcaster` uses it for the price feed and volume tracking

All of these get read/written from multiple threads, and ConcurrentHashMap handles the atomicity for operations like `merge()`, `computeIfAbsent()`, and `putIfAbsent()`.

### AtomicLong for counters
Order IDs (`TraderBot.orderIdGenerator`) and the total order count (`StockExchange.totalOrdersSubmitted`) use `AtomicLong` since they get incremented by many threads at once. Lock-free and fast.

### volatile for cross-thread visibility
`OrderBook.bestBid` and `bestAsk` are marked `volatile` so the dashboard thread can read them without synchronization. The values are only written inside the `synchronized` block but read from outside it — volatile guarantees the latest write is always visible.

### CopyOnWriteArrayList for the transaction log
The MarketBroadcaster's transaction log is append-only and gets iterated by the dashboard for display. `CopyOnWriteArrayList` is perfect here — writes (new trades) create a copy internally, but reads (dashboard iterating) never block. Since trades happen way less often than the UI reads the list, this tradeoff makes sense.

### Thread pool (ExecutorService)
Trader bots run in a `newFixedThreadPool`. In benchmark mode that's 200 threads in the pool. The pool manages thread lifecycle for us — we just submit Runnables and call `shutdown()` when done.

### Daemon threads
The PortfolioManager, MarketBroadcaster, and stats updater threads are all set as daemon threads. This means if the main thread finishes and the GUI closes, the JVM can shut down cleanly without these threads hanging around.

## Order types

- **Limit orders** (~80% of bot orders) — have a specific price. Only execute if there's a matching order at that price or better
- **Market orders** (~20% of bot orders) — no price limit, they just want to trade NOW. Internally they use `Long.MAX_VALUE` (buy) or `0` (sell) as price so they always cross the spread. The actual execution price comes from the limit order on the other side

When two market orders match each other (rare), the last traded price is used as fallback.

## Project structure

```
src/MiniProjectBank/
├── Main.java              ← entry point, wiring, lifecycle
├── Order.java             ← order data (id, trader, ticker, price, qty, type)
├── Transaction.java       ← executed trade record (immutable)
├── TraderBot.java         ← generates random orders in a loop
├── OrderBook.java         ← matching engine (the interesting part)
├── StockExchange.java     ← routes orders to the right OrderBook
├── PortfolioManager.java  ← tracks cash + equity per trader
├── MarketBroadcaster.java ← price feed + transaction log
└── ui/
    ├── TradingDashboard.java  ← Swing GUI
    └── PriceChartPanel.java   ← custom chart (Graphics2D)
```

Core concurrent logic is in `MiniProjectBank`. The UI is separated in `MiniProjectBank.ui`.

## How to run

```bash
# compile
javac -d out src/MiniProjectBank/*.java src/MiniProjectBank/ui/*.java

# run
java -cp out MiniProjectBank.Main
```

A dialog pops up to choose Simulation or Benchmark mode. Then the dashboard opens and trading begins.

## Scale

| | Simulation | Benchmark |
|---|---|---|
| Traders | 8 | 200 |
| Tickers | 10 | 10 |
| Orders per bot | 100 | 100 |
| Total orders | 800 | 20,000 |
| Delay between orders | ~2s | ~5ms |

Benchmark mode processes thousands of orders per second on a single machine — realistic enough for a small exchange.
