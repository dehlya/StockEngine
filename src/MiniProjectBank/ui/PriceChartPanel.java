package MiniProjectBank.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom painted line chart for price history — no external libraries needed :)
 * Stores the last N prices per ticker and draws a smooth line chart with a gradient fill.
 * Click a ticker card to switch which chart is displayed.
 */
public class PriceChartPanel extends JPanel {

    private static final Color BG = new Color(22, 27, 38);
    private static final Color GRID = new Color(35, 40, 55);
    private static final Color LINE_GREEN = new Color(16, 185, 129);
    private static final Color LINE_RED = new Color(239, 68, 68);
    private static final Color TEXT_MUTED = new Color(75, 82, 105);
    private static final Color TEXT_GRAY = new Color(120, 130, 155);
    private static final Color ACCENT = new Color(99, 102, 241);

    private static final int MAX_POINTS = 150;
    private static final int PADDING_LEFT = 55;
    private static final int PADDING_RIGHT = 15;
    private static final int PADDING_TOP = 35;
    private static final int PADDING_BOTTOM = 25;

    // ticker -> list of prices (in cents)
    private final ConcurrentHashMap<String, List<Long>> priceHistory = new ConcurrentHashMap<>();

    // which ticker is currently shown on the chart
    private String selectedTicker;

    public PriceChartPanel(String[] tickers) {
        setBackground(BG);
        for (String t : tickers) {
            priceHistory.put(t, new ArrayList<>());
        }
        selectedTicker = tickers[0];
    }

    public void setSelectedTicker(String ticker) {
        this.selectedTicker = ticker;
        repaint();
    }

    public String getSelectedTicker() {
        return selectedTicker;
    }

    /** called on every trade — adds a price point to the history */
    public void addPrice(String ticker, long priceInCents) {
        List<Long> history = priceHistory.get(ticker);
        if (history == null) return;

        synchronized (history) {
            history.add(priceInCents);
            if (history.size() > MAX_POINTS) {
                history.remove(0);
            }
        }

        // only repaint if this is the ticker we're looking at
        if (ticker.equals(selectedTicker)) {
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int w = getWidth();
        int h = getHeight();

        // chart title
        g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
        g2.setColor(ACCENT);
        g2.drawString(selectedTicker, PADDING_LEFT, 22);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g2.setColor(TEXT_MUTED);
        g2.drawString("PRICE HISTORY", PADDING_LEFT + 60, 22);

        List<Long> points;
        List<Long> history = priceHistory.get(selectedTicker);
        if (history == null) { g2.dispose(); return; }

        synchronized (history) {
            points = new ArrayList<>(history);
        }

        if (points.size() < 2) {
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            g2.setColor(TEXT_MUTED);
            g2.drawString("Waiting for trades...", w / 2 - 60, h / 2);
            g2.dispose();
            return;
        }

        // find min/max for scaling
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (long p : points) {
            if (p < min) min = p;
            if (p > max) max = p;
        }
        // add some padding so the line doesn't touch the edges
        long range = max - min;
        if (range == 0) range = 100; // avoid division by zero
        min -= range * 0.1;
        max += range * 0.1;

        int chartW = w - PADDING_LEFT - PADDING_RIGHT;
        int chartH = h - PADDING_TOP - PADDING_BOTTOM;

        // draw horizontal grid lines + y-axis labels
        g2.setFont(new Font("Consolas", Font.PLAIN, 10));
        int gridLines = 5;
        for (int i = 0; i <= gridLines; i++) {
            int y = PADDING_TOP + (chartH * i / gridLines);
            g2.setColor(GRID);
            g2.drawLine(PADDING_LEFT, y, w - PADDING_RIGHT, y);

            long priceAtLine = max - (max - min) * i / gridLines;
            g2.setColor(TEXT_MUTED);
            g2.drawString(String.format("$%.2f", priceAtLine / 100.0), 2, y + 4);
        }

        // determine if price went up or down overall — colors the line accordingly
        boolean goingUp = points.get(points.size() - 1) >= points.get(0);
        Color lineColor = goingUp ? LINE_GREEN : LINE_RED;

        // build the line path
        Path2D.Double linePath = new Path2D.Double();
        Path2D.Double fillPath = new Path2D.Double();

        for (int i = 0; i < points.size(); i++) {
            double x = PADDING_LEFT + (double) i / (points.size() - 1) * chartW;
            double y = PADDING_TOP + (1.0 - (double)(points.get(i) - min) / (max - min)) * chartH;

            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }

        // close the fill path along the bottom
        double lastX = PADDING_LEFT + (double)(points.size() - 1) / (points.size() - 1) * chartW;
        fillPath.lineTo(lastX, PADDING_TOP + chartH);
        fillPath.lineTo(PADDING_LEFT, PADDING_TOP + chartH);
        fillPath.closePath();

        // gradient fill under the line
        Color fillTop = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 60);
        Color fillBottom = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 5);
        g2.setPaint(new GradientPaint(0, PADDING_TOP, fillTop, 0, PADDING_TOP + chartH, fillBottom));
        g2.fill(fillPath);

        // the line itself
        g2.setColor(lineColor);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(linePath);

        // current price dot at the end of the line
        long lastPrice = points.get(points.size() - 1);
        double dotX = lastX;
        double dotY = PADDING_TOP + (1.0 - (double)(lastPrice - min) / (max - min)) * chartH;
        g2.setColor(lineColor);
        g2.fillOval((int) dotX - 4, (int) dotY - 4, 8, 8);

        // current price label next to the dot
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g2.setColor(TEXT_GRAY);
        String priceStr = String.format("$%.2f", lastPrice / 100.0);
        g2.drawString(priceStr, (int) dotX - 50, (int) dotY - 10);

        g2.dispose();
    }
}
