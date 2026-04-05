package yass;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class YassVerticalPitchScrollbar extends JComponent {

    private static final int OUTER_PAD = 6;
    private static final int TRACK_WIDTH = 14;
    private static final int HANDLE_HEIGHT = 10;
    private static final int MIN_WINDOW_SPAN = 6;

    private enum DragMode { NONE, MOVE, RESIZE_TOP, RESIZE_BOTTOM }

    private final YassSheet sheet;
    private DragMode dragMode = DragMode.NONE;
    private int dragOffsetY = 0;
    private int dragStartOffset = 0;
    private int dragWindowSpan = 0;

    public YassVerticalPitchScrollbar(YassSheet sheet) {
        this.sheet = sheet;
        setPreferredSize(new Dimension(22, 10));
        setMinimumSize(new Dimension(18, 10));
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

            @Override
            public void mouseMoved(MouseEvent e) {
                updateCursor(e);
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);

        Timer timer = new Timer(80, e -> repaint());
        timer.start();
    }

    private int getTotalSpan() {
        return Math.max(1, sheet.getAbsolutePitchRangeSpan());
    }

    private int getVisibleStart() {
        return Math.max(0, sheet.getAbsolutePitchWindowStart());
    }

    private int getVisibleSpan() {
        return Math.max(MIN_WINDOW_SPAN, Math.min(getTotalSpan(), sheet.getAbsolutePitchWindowSpan()));
    }

    private Rectangle getTrackRect() {
        int h = Math.max(20, getHeight() - OUTER_PAD * 2);
        int x = (getWidth() - TRACK_WIDTH) / 2;
        return new Rectangle(x, OUTER_PAD, TRACK_WIDTH, h);
    }

    private Rectangle getThumbRect(Rectangle track) {
        int total = getTotalSpan();
        int start = getVisibleStart();
        int span = getVisibleSpan();

        int y = track.y;
        int h = track.height;
        if (total > 0) {
            y = track.y + (int) Math.round(track.height * (start / (double) total));
            h = (int) Math.round(track.height * (span / (double) total));
        }
        h = Math.max(HANDLE_HEIGHT * 2 + 4, Math.min(track.height, h));
        if (y + h > track.y + track.height) {
            y = track.y + track.height - h;
        }
        if (y < track.y) {
            y = track.y;
        }
        return new Rectangle(track.x, y, track.width, h);
    }

    private int yToOffset(int y, Rectangle track) {
        int clamped = Math.max(track.y, Math.min(track.y + track.height, y));
        double ratio = (clamped - track.y) / (double) Math.max(1, track.height);
        return (int) Math.round(ratio * getTotalSpan());
    }

    private void handleMousePressed(MouseEvent e) {
        if (!sheet.isAbsolutePitchViewEnabled()) {
            dragMode = DragMode.NONE;
            return;
        }
        Rectangle track = getTrackRect();
        if (!track.contains(e.getPoint())) {
            dragMode = DragMode.NONE;
            return;
        }
        Rectangle thumb = getThumbRect(track);
        int y = e.getY();
        dragStartOffset = getVisibleStart();
        dragWindowSpan = getVisibleSpan();

        int topHandleBottom = thumb.y + HANDLE_HEIGHT;
        int bottomHandleTop = thumb.y + thumb.height - HANDLE_HEIGHT;
        if (Math.abs(y - thumb.y) <= 4 || (y >= thumb.y && y <= topHandleBottom)) {
            dragMode = DragMode.RESIZE_TOP;
            return;
        }
        if (Math.abs(y - (thumb.y + thumb.height - 1)) <= 4 || (y >= bottomHandleTop && y <= thumb.y + thumb.height)) {
            dragMode = DragMode.RESIZE_BOTTOM;
            return;
        }
        if (thumb.contains(e.getPoint())) {
            dragMode = DragMode.MOVE;
            dragOffsetY = y - thumb.y;
            return;
        }

        // Click outside thumb: jump to window around pointer.
        int targetCenter = yToOffset(y, track);
        int targetStart = targetCenter - dragWindowSpan / 2;
        sheet.setAbsolutePitchWindow(targetStart, dragWindowSpan);
        dragMode = DragMode.NONE;
    }

    private void handleMouseDragged(MouseEvent e) {
        if (!sheet.isAbsolutePitchViewEnabled() || dragMode == DragMode.NONE) {
            return;
        }
        Rectangle track = getTrackRect();
        int total = getTotalSpan();
        int minSpan = Math.min(MIN_WINDOW_SPAN, total);
        int top = dragStartOffset;
        int bottom = dragStartOffset + dragWindowSpan;

        if (dragMode == DragMode.MOVE) {
            int targetStart = yToOffset(e.getY() - dragOffsetY, track);
            sheet.setAbsolutePitchWindow(targetStart, dragWindowSpan);
            repaint();
            return;
        }
        if (dragMode == DragMode.RESIZE_TOP) {
            int targetTop = yToOffset(e.getY(), track);
            int maxTop = Math.max(0, bottom - minSpan);
            top = Math.max(0, Math.min(maxTop, targetTop));
            sheet.setAbsolutePitchWindow(top, bottom - top);
            repaint();
            return;
        }
        if (dragMode == DragMode.RESIZE_BOTTOM) {
            int targetBottom = yToOffset(e.getY(), track);
            int minBottom = Math.min(total, top + minSpan);
            bottom = Math.max(minBottom, Math.min(total, targetBottom));
            sheet.setAbsolutePitchWindow(top, bottom - top);
            repaint();
        }
    }

    private void updateCursor(MouseEvent e) {
        if (!sheet.isAbsolutePitchViewEnabled()) {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            return;
        }
        Rectangle track = getTrackRect();
        if (!track.contains(e.getPoint())) {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            return;
        }
        Rectangle thumb = getThumbRect(track);
        int y = e.getY();
        if (Math.abs(y - thumb.y) <= 4 || Math.abs(y - (thumb.y + thumb.height - 1)) <= 4) {
            setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
            return;
        }
        if (thumb.contains(e.getPoint())) {
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            return;
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
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

            if (!sheet.isAbsolutePitchViewEnabled()) {
                g2.setColor(trackBg);
                g2.fillRoundRect(track.x, track.y, track.width, track.height, 8, 8);
                g2.setColor(border);
                g2.drawRoundRect(track.x, track.y, track.width, track.height, 8, 8);
                return;
            }

            g2.setColor(trackBg);
            g2.fillRoundRect(track.x, track.y, track.width, track.height, 8, 8);
            g2.setColor(border);
            g2.drawRoundRect(track.x, track.y, track.width, track.height, 8, 8);

            g2.setColor(thumbBg);
            g2.fillRoundRect(thumb.x, thumb.y, thumb.width, thumb.height, 8, 8);
            g2.setColor(border);
            g2.drawRoundRect(thumb.x, thumb.y, thumb.width, thumb.height, 8, 8);

            int handleX = thumb.x + 1;
            int handleW = Math.max(6, thumb.width - 2);
            int topY = thumb.y + 1;
            int bottomY = thumb.y + thumb.height - HANDLE_HEIGHT - 1;
            int handleH = HANDLE_HEIGHT;

            g2.setColor(handleBg);
            g2.fillRoundRect(handleX, topY, handleW, handleH, 4, 4);
            g2.fillRoundRect(handleX, bottomY, handleW, handleH, 4, 4);
            g2.setColor(border);
            g2.drawRoundRect(handleX, topY, handleW, handleH, 4, 4);
            g2.drawRoundRect(handleX, bottomY, handleW, handleH, 4, 4);

            g2.setColor(handleLine);
            int lx1 = handleX + 3;
            int lx2 = handleX + handleW - 3;
            int t1 = topY + handleH / 2 - 2;
            int t2 = topY + handleH / 2 + 2;
            int b1 = bottomY + handleH / 2 - 2;
            int b2 = bottomY + handleH / 2 + 2;
            g2.drawLine(lx1, t1, lx2, t1);
            g2.drawLine(lx1, t2, lx2, t2);
            g2.drawLine(lx1, b1, lx2, b1);
            g2.drawLine(lx1, b2, lx2, b2);
        } finally {
            g2.dispose();
        }
    }
}
