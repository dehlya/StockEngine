package MiniProjectBank.ui;

import MiniProjectBank.Transaction;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Post-run replay dashboard — lets you scrub through the entire simulation history.
 * Drag the slider to any point in time and see what the market looked like then.
 * Cards, chart, and feed all update based on slider position.
 *
 * This is for benchmark mode where things go too fast to watch live :)
 */
public class ReplayDashboard extends JFrame {

    // same palette as TradingDashboard so it looks consistent
    private static final Color BG_MAIN = new Color(15, 17, 23);
    private static final Color BG_CARD = new Color(22, 27, 38);
    private static final Color BG_CARD_SELECTED = new Color(30, 36, 52);
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

    private final List<Transaction> allTrades;
    private final String[] tickers;

    // per-ticker UI components
    private final Map<String, JLabel> priceLabels = new HashMap<>();
    private final Map<String, JLabel> volumeLabels = new HashMap<>();
    private final Map<String, JLabel> tradeCountLabels = new HashMap<>();
    private final Map<String, JPanel> tickerCards = new HashMap<>();

    private final PriceChartPanel chartPanel;
    private final DefaultListModel<String> feedModel;
    private final JList<String> feedList;

    // stats
    private final JLabel tradesVal = new JLabel("0");
    private final JLabel positionLabel = new JLabel("0 / 0");

    // slider
    private JSlider timeSlider;

    // filter
    private String feedFilter = null;
    private JComboBox<String> filterBox;

    public ReplayDashboard(String[] tickers, List<Transaction> trades) {
        super("StockEngine — Replay");
        this.tickers = tickers;
        this.allTrades = trades;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1300, 850);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_MAIN);
        setLayout(new BorderLayout(0, 0));

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

        JLabel replayBadge = new JLabel("REPLAY");
        replayBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        replayBadge.setForeground(ACCENT);

        titleRow.add(titleLabel);
        titleRow.add(Box.createHorizontalStrut(10));
        titleRow.add(replayBadge);

        JPanel statsRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        statsRow.setOpaque(false);
        statsRow.add(buildStatPill("TRADES", tradesVal));
        statsRow.add(buildStatPill("POSITION", positionLabel));

        topBar.add(titleRow, BorderLayout.WEST);
        topBar.add(statsRow, BorderLayout.EAST);

        // === TICKER CARDS ===
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
        chartOuter.setBorder(new EmptyBorder(8, 25, 10, 10));
        chartOuter.add(chartWrapper, BorderLayout.CENTER);

        // === LEFT SIDE ===
        JPanel leftSide = new JPanel(new BorderLayout());
        leftSide.setOpaque(false);

        JPanel topSection = new JPanel(new BorderLayout());
        topSection.setOpaque(false);
        topSection.add(cardsWrapper, BorderLayout.NORTH);
        topSection.add(chartOuter, BorderLayout.CENTER);

        leftSide.add(topSection, BorderLayout.CENTER);

        // === TRADE FEED ===
        feedModel = new DefaultListModel<>();
        feedList = new JList<>(feedModel);
        feedList.setFont(FONT_FEED);
        feedList.setBackground(new Color(22, 27, 38));
        feedList.setForeground(TEXT_GRAY);
        feedList.setSelectionBackground(new Color(35, 40, 55));
        feedList.setFixedCellHeight(20);

        feedList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setFont(FONT_FEED);
                setBackground(isSelected ? new Color(35, 40, 55) : new Color(22, 27, 38));
                setForeground(GREEN);
                return this;
            }
        });

        JScrollPane feedScroll = new JScrollPane(feedList);
        feedScroll.setBorder(BorderFactory.createEmptyBorder());
        feedScroll.getViewport().setBackground(new Color(22, 27, 38));

        JPanel feedPanel = new JPanel(new BorderLayout());
        feedPanel.setBackground(new Color(22, 27, 38));
        feedPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, BORDER),
                new EmptyBorder(15, 15, 15, 15)
        ));

        JLabel feedTitle = new JLabel("TRADE FEED");
        feedTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        feedTitle.setForeground(TEXT_MUTED);

        // filter
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
            rebuildToPosition(timeSlider.getValue());
        });

        JPanel feedHeader = new JPanel(new BorderLayout());
        feedHeader.setOpaque(false);
        feedHeader.setBorder(new EmptyBorder(0, 0, 10, 0));
        feedHeader.add(feedTitle, BorderLayout.WEST);
        feedHeader.add(filterBox, BorderLayout.EAST);

        feedPanel.add(feedHeader, BorderLayout.NORTH);
        feedPanel.add(feedScroll, BorderLayout.CENTER);
        feedPanel.setPreferredSize(new Dimension(340, 0));

        // === CENTER ===
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(BG_MAIN);
        centerPanel.add(leftSide, BorderLayout.CENTER);
        centerPanel.add(feedPanel, BorderLayout.EAST);

        // === BOTTOM: TIME SLIDER ===
        int maxTrades = Math.max(1, allTrades.size());
        timeSlider = new JSlider(0, maxTrades, maxTrades);
        timeSlider.setBackground(BG_MAIN);
        timeSlider.setForeground(ACCENT);
        timeSlider.setPaintTicks(false);

        timeSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                rebuildToPosition(timeSlider.getValue());
            }
        });

        JPanel sliderPanel = new JPanel(new BorderLayout(15, 0));
        sliderPanel.setBackground(new Color(22, 27, 38));
        sliderPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                new EmptyBorder(12, 25, 12, 25)
        ));

        JLabel sliderLabel = new JLabel("TIMELINE");
        sliderLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        sliderLabel.setForeground(TEXT_MUTED);

        JLabel sliderHint = new JLabel("← drag to scrub through history →");
        sliderHint.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        sliderHint.setForeground(TEXT_MUTED);
        sliderHint.setHorizontalAlignment(SwingConstants.RIGHT);

        sliderPanel.add(sliderLabel, BorderLayout.WEST);
        sliderPanel.add(timeSlider, BorderLayout.CENTER);
        sliderPanel.add(sliderHint, BorderLayout.EAST);

        // === ASSEMBLE ===
        add(topBar, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(sliderPanel, BorderLayout.SOUTH);

        // start at the end — showing all trades
        rebuildToPosition(maxTrades);
    }

    /**
     * Rebuilds the entire dashboard state based on trades 0..position.
     * Recalculates prices, volumes, trade counts, chart, and feed from scratch.
     * Not the most efficient thing ever but it's replay mode so who cares :)
     */
    private void rebuildToPosition(int position) {
        // clamp
        position = Math.max(0, Math.min(position, allTrades.size()));

        // reset per-ticker accumulators
        Map<String, Long> prices = new HashMap<>();
        Map<String, Long> volumes = new HashMap<>();
        Map<String, Long> tradeCounts = new HashMap<>();

        // clear the chart and rebuild from scratch
        chartPanel.clearAll();

        // replay trades up to the current position
        feedModel.clear();
        String selectedTicker = chartPanel.getSelectedTicker();

        for (int i = 0; i < position; i++) {
            Transaction tx = allTrades.get(i);

            prices.put(tx.ticker, tx.price);
            volumes.merge(tx.ticker, tx.quantity, Long::sum);
            tradeCounts.merge(tx.ticker, 1L, Long::sum);

            chartPanel.addPrice(tx.ticker, tx.price);

            // feed entry (filtered)
            if (feedFilter == null || tx.ticker.equals(feedFilter)) {
                String line = String.format(" %s  %s → %s  %d @ $%.2f",
                        tx.ticker, tx.sellerId, tx.buyerId, tx.quantity, tx.price / 100.0);
                feedModel.addElement(line);
            }
        }

        // update ticker cards
        for (String ticker : tickers) {
            JLabel pl = priceLabels.get(ticker);
            JLabel vl = volumeLabels.get(ticker);
            JLabel tl = tradeCountLabels.get(ticker);

            Long price = prices.get(ticker);
            long vol = volumes.getOrDefault(ticker, 0L);
            long tc = tradeCounts.getOrDefault(ticker, 0L);

            if (pl != null) pl.setText(price != null ? String.format("$%.2f", price / 100.0) : "—");
            if (vl != null) vl.setText(String.format("Vol: %,d", vol));
            if (tl != null) tl.setText(tc + " trades");
        }

        // scroll feed to bottom
        if (feedModel.size() > 0) {
            feedList.ensureIndexIsVisible(feedModel.size() - 1);
        }

        // update stats
        tradesVal.setText(String.format("%,d", position));
        positionLabel.setText(position + " / " + allTrades.size());
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
        card.setPreferredSize(new Dimension(120, 80));

        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                chartPanel.setSelectedTicker(ticker);
                highlightCard(ticker);
                feedFilter = ticker;
                filterBox.setSelectedItem(ticker);
                rebuildToPosition(timeSlider.getValue());
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

        card.add(tickerLabel);
        card.add(priceLabel);
        card.add(Box.createVerticalStrut(3));
        card.add(volLabel);
        card.add(tcLabel);

        return card;
    }
}
