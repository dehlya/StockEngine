package MiniProjectBank.ui;

import MiniProjectBank.Order;
import MiniProjectBank.StockExchange;
import MiniProjectBank.Transaction;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.DefaultListCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Clean fintech-style dashboard with live price charts :)
 * Ticker cards on top, click one to see its chart below.
 * Trade feed on the right. Stats in the top bar.
 */
public class TradingDashboard extends JFrame {

    // color palette
    private static final Color BG_MAIN = new Color(15, 17, 23);
    private static final Color BG_CARD = new Color(22, 27, 38);
    private static final Color BG_CARD_SELECTED = new Color(30, 36, 52);
    private static final Color BG_FEED = new Color(22, 27, 38);
    private static final Color BORDER = new Color(35, 40, 55);
    private static final Color BORDER_SELECTED = new Color(99, 102, 241);
    private static final Color TEXT_WHITE = new Color(230, 235, 245);
    private static final Color TEXT_GRAY = new Color(120, 130, 155);
    private static final Color TEXT_MUTED = new Color(75, 82, 105);
    private static final Color GREEN = new Color(16, 185, 129);
    private static final Color ACCENT = new Color(99, 102, 241);

    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font FONT_TICKER = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_PRICE = new Font("Segoe UI", Font.BOLD, 22);
    private static final Font FONT_LABEL = new Font("Segoe UI", Font.PLAIN, 10);
    private static final Font FONT_FEED = new Font("Consolas", Font.PLAIN, 11);
    private static final Font FONT_STAT = new Font("Segoe UI", Font.BOLD, 20);
    private static final Font FONT_STAT_LABEL = new Font("Segoe UI", Font.PLAIN, 11);

    // ticker card components
    private final ConcurrentHashMap<String, JLabel> priceLabels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JLabel> volumeLabels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JLabel> tradeCountLabels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JLabel> bidAskLabels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JPanel> tickerCards = new ConcurrentHashMap<>();

    // chart
    private final PriceChartPanel chartPanel;

    // live feed
    private final DefaultListModel<String> feedModel;
    private final JList<String> feedList;

    // stats
    private final JLabel ordersVal = new JLabel("0");
    private final JLabel tradesVal = new JLabel("0");
    private final JLabel elapsedVal = new JLabel("0s");
    private final JLabel statusDot = new JLabel("●");
    private JLabel statusText;

    // tracking
    private final ConcurrentHashMap<String, long[]> tickerData = new ConcurrentHashMap<>();
    private final AtomicLong tradeCount = new AtomicLong(0);
    private long startTime;

    // reference to the exchange so we can pull bid/ask spreads
    private StockExchange exchange;

    private static final int MAX_FEED = 500;

    // feed filter — null means show everything, otherwise only show entries for this ticker
    private String feedFilter = null;
    private final java.util.List<String> allFeedEntries = new java.util.ArrayList<>();
    private JComboBox<String> filterBox;

    public TradingDashboard(String[] tickers) {
        super("StockEngine");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1300, 800);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_MAIN);
        setLayout(new BorderLayout(0, 0));

        for (String t : tickers) {
            tickerData.put(t, new long[]{0, 0, 0});
        }

        chartPanel = new PriceChartPanel(tickers);

        // === TOP BAR ===
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(BG_MAIN);
        topBar.setBorder(new EmptyBorder(15, 25, 8, 25));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);

        JLabel titleLabel = new JLabel("StockEngine");
        titleLabel.setFont(FONT_TITLE);
        titleLabel.setForeground(TEXT_WHITE);

        statusDot.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        statusDot.setForeground(GREEN);

        statusText = new JLabel("LIVE");
        statusText.setFont(new Font("Segoe UI", Font.BOLD, 11));
        statusText.setForeground(GREEN);

        titleRow.add(titleLabel);
        titleRow.add(Box.createHorizontalStrut(8));
        titleRow.add(statusDot);
        titleRow.add(statusText);

        JPanel statsRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        statsRow.setOpaque(false);
        statsRow.add(buildStatPill("ORDERS", ordersVal));
        statsRow.add(buildStatPill("MATCHED", tradesVal));
        statsRow.add(buildStatPill("ELAPSED", elapsedVal));

        topBar.add(titleRow, BorderLayout.WEST);
        topBar.add(statsRow, BorderLayout.EAST);

        // === TICKER CARDS — 2 rows of 5 so all 10 fit nicely ===
        JPanel cardsGrid = new JPanel(new GridLayout(2, 5, 8, 8));
        cardsGrid.setOpaque(false);

        for (String ticker : tickers) {
            JPanel card = buildTickerCard(ticker);
            tickerCards.put(ticker, card);
            cardsGrid.add(card);
        }

        JPanel cardsWrapper = new JPanel(new BorderLayout());
        cardsWrapper.setOpaque(false);
        cardsWrapper.setBorder(new EmptyBorder(5, 25, 5, 10));
        cardsWrapper.add(cardsGrid, BorderLayout.CENTER);

        // highlight first card by default
        highlightCard(tickers[0]);

        // === CHART ===
        JPanel chartWrapper = new JPanel(new BorderLayout());
        chartWrapper.setBackground(BG_CARD);
        chartWrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(5, 5, 5, 5)
        ));
        chartWrapper.add(chartPanel, BorderLayout.CENTER);

        JPanel chartOuter = new JPanel(new BorderLayout());
        chartOuter.setOpaque(false);
        chartOuter.setBorder(new EmptyBorder(8, 25, 15, 10));
        chartOuter.add(chartWrapper, BorderLayout.CENTER);

        // === LEFT SIDE: cards + chart stacked ===
        JPanel leftSide = new JPanel(new BorderLayout());
        leftSide.setOpaque(false);

        JPanel topSection = new JPanel(new BorderLayout());
        topSection.setOpaque(false);
        topSection.add(cardsWrapper, BorderLayout.NORTH);
        topSection.add(chartOuter, BorderLayout.CENTER);

        leftSide.add(topSection, BorderLayout.CENTER);

        // === TRADE FEED (right side) ===
        feedModel = new DefaultListModel<>();
        feedList = new JList<>(feedModel);
        feedList.setFont(FONT_FEED);
        feedList.setBackground(BG_FEED);
        feedList.setForeground(TEXT_GRAY);
        feedList.setSelectionBackground(new Color(35, 40, 55));
        feedList.setFixedCellHeight(20);

        // color-code feed entries: BID=blue, ASK=orange, TRADE=green
        feedList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setFont(FONT_FEED);
                setBackground(isSelected ? new Color(35, 40, 55) : BG_FEED);
                String text = value.toString();
                if (text.contains(" BID ")) {
                    setForeground(new Color(96, 165, 250));   // soft blue for bids
                } else if (text.contains(" ASK ")) {
                    setForeground(new Color(251, 191, 36));   // amber for asks
                } else if (text.contains("→")) {
                    setForeground(GREEN);                      // green for executed trades
                } else {
                    setForeground(TEXT_GRAY);
                }
                return this;
            }
        });

        JScrollPane feedScroll = new JScrollPane(feedList);
        feedScroll.setBorder(BorderFactory.createEmptyBorder());
        feedScroll.getViewport().setBackground(BG_FEED);

        JPanel feedPanel = new JPanel(new BorderLayout());
        feedPanel.setBackground(BG_FEED);
        feedPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER),
                new EmptyBorder(15, 15, 15, 15)
        ));

        JLabel feedTitle = new JLabel("TRADE FEED");
        feedTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        feedTitle.setForeground(TEXT_MUTED);

        // filter dropdown — pick a ticker or "ALL" to see everything
        String[] filterOptions = new String[tickers.length + 1];
        filterOptions[0] = "ALL";
        System.arraycopy(tickers, 0, filterOptions, 1, tickers.length);
        filterBox = new JComboBox<>(filterOptions);
        filterBox.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        filterBox.setBackground(BG_CARD);
        filterBox.setForeground(TEXT_WHITE);
        filterBox.setPreferredSize(new Dimension(80, 22));
        filterBox.addActionListener(e -> {
            String selected = (String) filterBox.getSelectedItem();
            feedFilter = "ALL".equals(selected) ? null : selected;
            rebuildFeed();
        });

        JPanel feedHeader = new JPanel(new BorderLayout());
        feedHeader.setOpaque(false);
        feedHeader.setBorder(new EmptyBorder(0, 0, 10, 0));
        feedHeader.add(feedTitle, BorderLayout.WEST);
        feedHeader.add(filterBox, BorderLayout.EAST);

        feedPanel.add(feedHeader, BorderLayout.NORTH);
        feedPanel.add(feedScroll, BorderLayout.CENTER);
        feedPanel.setPreferredSize(new Dimension(340, 0));

        // === ASSEMBLE ===
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(BG_MAIN);
        centerPanel.add(leftSide, BorderLayout.CENTER);
        centerPanel.add(feedPanel, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        startTime = System.currentTimeMillis();

        Timer timer = new Timer(1000, e -> {
            long secs = (System.currentTimeMillis() - startTime) / 1000;
            String txt = secs < 60 ? secs + "s" : (secs / 60) + "m " + (secs % 60) + "s";
            elapsedVal.setText(txt);
        });
        timer.start();
    }

    private void highlightCard(String ticker) {
        tickerCards.forEach((t, card) -> {
            card.setBackground(BG_CARD);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER, 1, true),
                    new EmptyBorder(10, 14, 10, 14)
            ));
        });
        JPanel selected = tickerCards.get(ticker);
        if (selected != null) {
            selected.setBackground(BG_CARD_SELECTED);
            selected.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_SELECTED, 2, true),
                    new EmptyBorder(9, 13, 9, 13)
            ));
        }
    }

    private JPanel buildStatPill(String label, JLabel valueLabel) {
        JPanel pill = new JPanel();
        pill.setLayout(new BoxLayout(pill, BoxLayout.Y_AXIS));
        pill.setBackground(BG_CARD);
        pill.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(6, 16, 6, 16)
        ));

        JLabel l = new JLabel(label);
        l.setFont(FONT_STAT_LABEL);
        l.setForeground(TEXT_MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);

        valueLabel.setFont(FONT_STAT);
        valueLabel.setForeground(TEXT_WHITE);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        pill.add(l);
        pill.add(valueLabel);
        return pill;
    }

    private JPanel buildTickerCard(String ticker) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(10, 14, 10, 14)
        ));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setPreferredSize(new Dimension(120, 90));

        // click to switch chart AND filter the feed to this ticker
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                chartPanel.setSelectedTicker(ticker);
                highlightCard(ticker);
                feedFilter = ticker;
                filterBox.setSelectedItem(ticker);
                rebuildFeed();
            }
        });

        JLabel tickerLabel = new JLabel(ticker);
        tickerLabel.setFont(FONT_TICKER);
        tickerLabel.setForeground(ACCENT);
        tickerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel priceLabel = new JLabel("—");
        priceLabel.setFont(FONT_PRICE);
        priceLabel.setForeground(TEXT_WHITE);
        priceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        priceLabels.put(ticker, priceLabel);

        JLabel volLabel = new JLabel("Vol: 0");
        volLabel.setFont(FONT_LABEL);
        volLabel.setForeground(TEXT_GRAY);
        volLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        volumeLabels.put(ticker, volLabel);

        JLabel tcLabel = new JLabel("0 trades");
        tcLabel.setFont(FONT_LABEL);
        tcLabel.setForeground(TEXT_MUTED);
        tcLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        tradeCountLabels.put(ticker, tcLabel);

        JLabel baLabel = new JLabel("Bid: — / Ask: —");
        baLabel.setFont(FONT_LABEL);
        baLabel.setForeground(TEXT_MUTED);
        baLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bidAskLabels.put(ticker, baLabel);

        card.add(tickerLabel);
        card.add(priceLabel);
        card.add(Box.createVerticalStrut(3));
        card.add(volLabel);
        card.add(tcLabel);
        card.add(baLabel);

        return card;
    }

    /** called by MarketBroadcaster every time a trade goes through */
    public void onTrade(Transaction tx) {
        long count = tradeCount.incrementAndGet();

        tickerData.compute(tx.ticker, (k, data) -> {
            if (data == null) data = new long[]{0, 0, 0};
            data[0] = tx.price;
            data[1] += tx.quantity;
            data[2]++;
            return data;
        });

        // push price to the chart
        chartPanel.addPrice(tx.ticker, tx.price);

        SwingUtilities.invokeLater(() -> {
            long[] data = tickerData.get(tx.ticker);

            JLabel pl = priceLabels.get(tx.ticker);
            if (pl != null) pl.setText(String.format("$%.2f", data[0] / 100.0));

            JLabel vl = volumeLabels.get(tx.ticker);
            if (vl != null) vl.setText(String.format("Vol: %,d", data[1]));

            JLabel tl = tradeCountLabels.get(tx.ticker);
            if (tl != null) tl.setText(data[2] + " trades");

            // update bid/ask spread from the order book
            JLabel ba = bidAskLabels.get(tx.ticker);
            if (ba != null && exchange != null) {
                long[] bidAsk = exchange.getBidAsk(tx.ticker);
                String bidStr = bidAsk[0] > 0 ? String.format("$%.2f", bidAsk[0] / 100.0) : "—";
                String askStr = bidAsk[1] > 0 ? String.format("$%.2f", bidAsk[1] / 100.0) : "—";
                ba.setText("Bid: " + bidStr + " / Ask: " + askStr);
            }

            // feed — executed trade
            String line = String.format(" %s  %s → %s  %d @ $%.2f",
                    tx.ticker, tx.sellerId, tx.buyerId, tx.quantity, tx.price / 100.0);
            addFeedEntry(line);
            tradesVal.setText(String.format("%,d", count));
        });
    }

    public void setExchange(StockExchange exchange) {
        this.exchange = exchange;
    }

    /** adds a line to the feed, respecting the current filter */
    private void addFeedEntry(String line) {
        allFeedEntries.add(line);
        if (allFeedEntries.size() > MAX_FEED) {
            allFeedEntries.remove(0);
        }

        // only show it if it passes the filter
        if (feedFilter == null || line.contains(" " + feedFilter + " ")) {
            feedModel.addElement(line);
            if (feedModel.size() > MAX_FEED) {
                feedModel.removeRange(0, feedModel.size() - MAX_FEED);
            }
            feedList.ensureIndexIsVisible(feedModel.size() - 1);
        }
    }

    /** rebuilds the visible feed from scratch when the filter changes */
    private void rebuildFeed() {
        feedModel.clear();
        for (String line : allFeedEntries) {
            if (feedFilter == null || line.contains(" " + feedFilter + " ")) {
                feedModel.addElement(line);
            }
        }
        if (feedModel.size() > 0) {
            feedList.ensureIndexIsVisible(feedModel.size() - 1);
        }
    }

    /** called by OrderBook when a new order lands — shows bids/asks in the feed too */
    public void onOrderSubmitted(Order order) {
        String side = order.isBuy ? "BID" : "ASK";
        String priceStr = order.isMarketOrder ? "MARKET" : String.format("$%.2f", order.price / 100.0);
        String line = String.format(" %s  %s  %s  %d @ %s",
                order.ticker, side, order.traderId, order.quantity, priceStr);

        SwingUtilities.invokeLater(() -> addFeedEntry(line));
    }

    public void updateOrderCount(long count) {
        SwingUtilities.invokeLater(() ->
                ordersVal.setText(String.format("%,d", count))
        );
    }

    public void markFinished() {
        SwingUtilities.invokeLater(() -> {
            statusDot.setForeground(ACCENT);
            statusText.setText("DONE");
            statusText.setForeground(ACCENT);
        });
    }
}
