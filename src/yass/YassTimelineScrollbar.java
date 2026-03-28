package yass;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class YassTimelineScrollbar extends JComponent {

    private static final int OUTER_PAD = 8;
    private static final int TRACK_HEIGHT = 16;
    private static final int HANDLE_WIDTH = 12;
    private static final int MIN_WINDOW_MS = 1000;

    private enum DragMode { NONE, MOVE, RESIZE_LEFT, RESIZE_RIGHT }

    private final YassSheet sheet;
    private DragMode dragMode = DragMode.NONE;
    private int dragOffsetX = 0;
    private double dragStartMs = 0;
    private double dragWindowMs = 0;

    public YassTimelineScrollbar(YassSheet sheet) {
        this.sheet = sheet;
        setPreferredSize(new Dimension(10, 28));
        setMinimumSize(new Dimension(10, 24));
        setOpaque(false);

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMousePressed(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragMode = DragMode.NONE;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouseDragged(e);
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);

        Timer timer = new Timer(60, e -> repaint());
        timer.start();
    }

    private double getDurationMs() {
        return Math.max(1, sheet.getDuration());
    }

    private double getVisibleStartMs() {
        return Math.max(0, sheet.getMinVisibleMs());
    }

    private double getVisibleWindowMs() {
        double duration = getDurationMs();
        double start = getVisibleStartMs();
        double end = Math.max(start, sheet.getMaxVisibleMs());
        return Math.max(MIN_WINDOW_MS, Math.min(duration, end - start));
    }

    private Rectangle getTrackRect() {
        int width = Math.max(20, getWidth() - OUTER_PAD * 2);
        int y = (getHeight() - TRACK_HEIGHT) / 2;
        return new Rectangle(OUTER_PAD, y, width, TRACK_HEIGHT);
    }

    private Rectangle getThumbRect(Rectangle track) {
        double duration = getDurationMs();
        double start = getVisibleStartMs();
        double window = getVisibleWindowMs();

        int x = track.x;
        int w = track.width;
        if (duration > 0) {
            x = track.x + (int) Math.round(track.width * (start / duration));
            w = (int) Math.round(track.width * (window / duration));
        }
        w = Math.max(HANDLE_WIDTH * 2 + 4, Math.min(track.width, w));
        if (x + w > track.x + track.width) {
            x = track.x + track.width - w;
        }
        if (x < track.x) {
            x = track.x;
        }
        return new Rectangle(x, track.y, w, track.height);
    }

    private double xToMs(int x, Rectangle track) {
        int clamped = Math.max(track.x, Math.min(track.x + track.width, x));
        double ratio = (clamped - track.x) / (double) track.width;
        return ratio * getDurationMs();
    }

    private void handleMousePressed(MouseEvent e) {
        Rectangle track = getTrackRect();
        Rectangle thumb = getThumbRect(track);
        if (!track.contains(e.getPoint())) {
            dragMode = DragMode.NONE;
            return;
        }

        int x = e.getX();
        dragStartMs = getVisibleStartMs();
        dragWindowMs = getVisibleWindowMs();
        int leftHandleEnd = thumb.x + HANDLE_WIDTH;
        int rightHandleStart = thumb.x + thumb.width - HANDLE_WIDTH;

        if (x >= thumb.x && x <= leftHandleEnd) {
            dragMode = DragMode.RESIZE_LEFT;
            return;
        }
        if (x >= rightHandleStart && x <= thumb.x + thumb.width) {
            dragMode = DragMode.RESIZE_RIGHT;
            return;
        }
        if (thumb.contains(e.getPoint())) {
            dragMode = DragMode.MOVE;
            dragOffsetX = x - thumb.x;
            return;
        }
        dragMode = DragMode.NONE;
    }

    private void handleMouseDragged(MouseEvent e) {
        if (dragMode == DragMode.NONE) {
            return;
        }
        Rectangle track = getTrackRect();
        double duration = getDurationMs();
        double leftMs = dragStartMs;
        double rightMs = dragStartMs + dragWindowMs;

        if (dragMode == DragMode.MOVE) {
            double targetStart = xToMs(e.getX() - dragOffsetX, track);
            sheet.setVisibleWindowMs(targetStart, dragWindowMs);
            repaint();
            return;
        }
        if (dragMode == DragMode.RESIZE_LEFT) {
            double targetLeft = xToMs(e.getX(), track);
            double maxLeft = Math.max(0, rightMs - MIN_WINDOW_MS);
            leftMs = Math.min(maxLeft, Math.max(0, targetLeft));
            sheet.setVisibleWindowMs(leftMs, rightMs - leftMs);
            repaint();
            return;
        }
        if (dragMode == DragMode.RESIZE_RIGHT) {
            double targetRight = xToMs(e.getX(), track);
            double minRight = Math.min(duration, leftMs + MIN_WINDOW_MS);
            rightMs = Math.max(minRight, Math.min(duration, targetRight));
            sheet.setVisibleWindowMs(leftMs, rightMs - leftMs);
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Rectangle track = getTrackRect();
            Rectangle thumb = getThumbRect(track);

            Color border = sheet.isDarkMode() ? new Color(120, 120, 120) : new Color(140, 140, 140);
            Color trackBg = sheet.isDarkMode() ? new Color(42, 42, 42) : new Color(230, 230, 230);
            Color thumbBg = sheet.isDarkMode() ? new Color(80, 80, 80) : new Color(190, 190, 190);
            Color handleBg = sheet.isDarkMode() ? new Color(115, 115, 115) : new Color(145, 145, 145);
            Color handleLine = sheet.isDarkMode() ? new Color(220, 220, 220) : new Color(70, 70, 70);

            g2.setColor(trackBg);
            g2.fillRoundRect(track.x, track.y, track.width, track.height, 8, 8);
            g2.setColor(border);
            g2.drawRoundRect(track.x, track.y, track.width, track.height, 8, 8);

            g2.setColor(thumbBg);
            g2.fillRoundRect(thumb.x, thumb.y, thumb.width, thumb.height, 8, 8);
            g2.setColor(border);
            g2.drawRoundRect(thumb.x, thumb.y, thumb.width, thumb.height, 8, 8);

            int handleY = thumb.y + 1;
            int handleH = Math.max(6, thumb.height - 2);
            int leftHandleX = thumb.x + 1;
            int rightHandleX = thumb.x + thumb.width - HANDLE_WIDTH - 1;
            g2.setColor(handleBg);
            g2.fillRoundRect(leftHandleX, handleY, HANDLE_WIDTH, handleH, 4, 4);
            g2.fillRoundRect(rightHandleX, handleY, HANDLE_WIDTH, handleH, 4, 4);
            g2.setColor(border);
            g2.drawRoundRect(leftHandleX, handleY, HANDLE_WIDTH, handleH, 4, 4);
            g2.drawRoundRect(rightHandleX, handleY, HANDLE_WIDTH, handleH, 4, 4);

            g2.setColor(handleLine);
            int lineTop = handleY + 3;
            int lineBottom = handleY + handleH - 3;
            int l1 = leftHandleX + HANDLE_WIDTH / 2 - 2;
            int l2 = leftHandleX + HANDLE_WIDTH / 2 + 2;
            int r1 = rightHandleX + HANDLE_WIDTH / 2 - 2;
            int r2 = rightHandleX + HANDLE_WIDTH / 2 + 2;
            g2.drawLine(l1, lineTop, l1, lineBottom);
            g2.drawLine(l2, lineTop, l2, lineBottom);
            g2.drawLine(r1, lineTop, r1, lineBottom);
            g2.drawLine(r2, lineTop, r2, lineBottom);

            int playerPos = sheet.getPlayerPosition();
            if (playerPos >= 0) {
                double cursorMs = sheet.fromTimeline(playerPos);
                double ratio = Math.max(0, Math.min(1, cursorMs / getDurationMs()));
                int cursorX = track.x + (int) Math.round(track.width * ratio);
                g2.setColor(Color.RED);
                g2.drawLine(cursorX, track.y - 3, cursorX, track.y + track.height + 3);
            }
        } finally {
            g2.dispose();
        }
    }
}
