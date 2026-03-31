# StockEngine

High-frequency trading simulator built in Java for the Concurrent Programming mini project (PR6) at HES-SO Valais.

## What it does

Simulates a small stock exchange: 10 tickers, up to 200 concurrent trader bots, a matching engine with price-time priority, portfolio tracking, and a live Swing dashboard.

Three modes:
- **Simulation** — 8 bots, ~2s delay, live dashboard
- **Benchmark** — 200 bots, ~5ms delay, replay dashboard at the end
- **Console** — same as benchmark, no GUI

## How to run

```bash
javac -d out src/MiniProjectBank/*.java src/MiniProjectBank/ui/*.java
java -cp out MiniProjectBank.Main
```

## Project structure

```
src/MiniProjectBank/
├── Main.java              — entry point, wiring, lifecycle
├── Order.java             — order data (AtomicLong quantity for partial fills)
├── Transaction.java       — executed trade record (immutable, all final fields)
├── TraderBot.java         — generates random orders in a loop
├── OrderBook.java         — matching engine (synchronized per ticker)
├── StockExchange.java     — routes orders to the right OrderBook
├── PortfolioManager.java  — tracks cash + equity per trader
├── MarketBroadcaster.java — price feed + transaction log
└── ui/
    ├── TradingDashboard.java  — live Swing GUI
    ├── PriceChartPanel.java   — custom line chart (Graphics2D)
    └── ReplayDashboard.java   — post-simulation scrubber
```

## AI usage declaration

AI tools (Claude) were used for:
- The GUI (TradingDashboard, ReplayDashboard, PriceChartPanel) — built collaboratively with AI
- UI-related code adjustments and wiring (connecting the dashboard to the backend)
- Documentation and comments
- Reviewing and discussing design decisions

The core logic (OrderBook matching engine, StockExchange routing, PortfolioManager, MarketBroadcaster, TraderBot, Order, Transaction), the architecture, and all synchronization/concurrency choices were written by us.