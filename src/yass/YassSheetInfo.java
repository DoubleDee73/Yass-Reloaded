/*
 * Yass - Karaoke Editor
 * Copyright (C) 2009 Saruta
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package yass;

import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.Vector;
import java.util.logging.Logger;

/**
 * Description of the Class
 *
 * @author Saruta
 */
public class YassSheetInfo extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private final YassSheet sheet;
    private final int track;
    private int minHeight;
    private double minBeat;
    private double rangeBeat;
    private int rangeHeight;

    double posMs = 0;

    private static final int notesBar = 50;
    private static final int msgBar = 14;
    private static final int txtBar = 32;
    private static final int sideBar = 16;
    //private static final int selectBar = 20;

    private final YassSheetListener sheetListener;

    public static Image err_page_icon = null, err_major_ico = null, err_file_icon = null, err_tags_icon = null, err_text_icon = null;
    public static Image no_err_page_icon = null, no_err_major_ico = null, no_err_file_icon = null, no_err_tags_icon = null, no_err_text_icon = null;

    Stroke minLineStroke = new BasicStroke(0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
    Stroke stdLineStroke = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
    Stroke medLineStroke = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
    Stroke maxLineStroke = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);

    private int hiliteCue = NONE;
    private static final int NONE = 0;
    private static final int ACTIVATE_TRACK = 1;
    private static final int SHOW_ERRORS = 2;
    //private static final int SHOW_SELECT = 3;
    //private boolean isSelected = false;

    private boolean hasErr = false;

    private static final int OVERVIEW_HANDLE_WIDTH = 12;
    private static final int OVERVIEW_MIN_WINDOW_MS = 1000;
    private static final int OVERVIEW_EDGE_HIT_SLOP = 6;
    private static final int OVERVIEW_MIN_EDGE_HIT_SLOP = 2;

    private enum OverviewDragMode { NONE, MOVE, RESIZE_LEFT, RESIZE_RIGHT }
    private OverviewDragMode overviewDragMode = OverviewDragMode.NONE;
    private int overviewDragOffsetX = 0;
    private double overviewDragLeftMs = 0;
    private double overviewDragRightMs = 0;
    private int overviewFixedEdgeX = -1;

    public YassSheetInfo(YassSheet s, int track) {
        super(true);
        setFocusable(false);

        this.sheet = s;
        this.track = track;
        sheet.addYassSheetListener(sheetListener = new YassSheetListener() {
            @Override
            public void posChanged(YassSheet source, double posMs) {
                setPosMs(posMs);
            }
            @Override
            public void rangeChanged(YassSheet source, int minH, int maxH, int minB, int maxB) {
                setHeightRange(minH, maxH, minB, maxB);
            }
            @Override
            public void propsChanged(YassSheet source) {
                setBackground(sheet.darkMode ? sheet.HI_GRAY_2_DARK_MODE : sheet.HI_GRAY_2);
                repaint();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (sheet.isPlaying() || sheet.isTemporaryStop())
                    sheet.stopPlaying();
                if (! isActiveTrack())
                    activateTrack();
                if (handleOverviewMousePressed(e)) {
                    hiliteCue = NONE;
                    repaint();
                    return;
                }
                //if (hiliteCue == SHOW_SELECT) {
                //    isSelected = !isSelected;
                //    repaint();
                //}
                final int trackNameWidth = sheet.getTableCount() > 1 ? 100 : 0;
                if (isInErrorQuickArea(e.getX(), e.getY(), trackNameWidth) && hiliteCue == SHOW_ERRORS)
                    showErrors();
                else {
                    SwingUtilities.invokeLater(() -> {
                        boolean exact = e.isAltDown() || e.isControlDown() || e.getButton() == MouseEvent.BUTTON2|| e.getButton() == MouseEvent.BUTTON3;
                        moveTo(e.getX(), exact);
                        if (exact)
                            moveTo(e.getX(), exact);
                    });
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (overviewDragMode != OverviewDragMode.NONE) {
                    overviewDragMode = OverviewDragMode.NONE;
                    overviewFixedEdgeX = -1;
                }
            }
            @Override
            public void mouseEntered(MouseEvent e) {
            }
            public void mouseExited(MouseEvent e) {
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                if (hiliteCue != NONE) {
                    hiliteCue = NONE;
                    repaint();
                }
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (handleOverviewMouseDragged(e)) {
                    return;
                }
                if (!(sheet.isPlaying() || sheet.isTemporaryStop()))
                    moveTo(e.getX(), true);
            }
            @Override
            public void mouseMoved(MouseEvent e) {
                if (sheet.isPlaying())
                    return;
                if (updateOverviewCursor(e)) {
                    if (hiliteCue == SHOW_ERRORS) {
                        hiliteCue = NONE;
                        repaint();
                    }
                    return;
                }
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                final int trackNameWidth = sheet.getTableCount() > 1 ? 100 : 0;
                if (isInErrorQuickArea(e.getX(), e.getY(), trackNameWidth) && hasErr) {
                    if (hiliteCue != SHOW_ERRORS) {
                        hiliteCue = SHOW_ERRORS;
                        repaint();
                    }
                }
                /*else if (e.getX() > getWidth() - sideBar -selectBar && e.getY() < txtBar) {
                    if (hiliteCue != SHOW_SELECT) {
                        hiliteCue = SHOW_SELECT;
                        repaint();
                    }
                }*/
                else {
                    if (! isActiveTrack()) {
                        if (hiliteCue != ACTIVATE_TRACK) {
                            hiliteCue = ACTIVATE_TRACK;
                            repaint();
                        }
                    }
                    else {
                        if (hiliteCue != NONE) {
                            hiliteCue = NONE;
                            repaint();
                        }
                    }
                }
            }
        });
        addMouseWheelListener(e -> sheet.dispatchEvent(e));

        try {
            err_page_icon = new ImageIcon(getClass().getResource("/yass/resources/img/MinorPageError.gif")).getImage();
            err_major_ico = new ImageIcon(getClass().getResource("/yass/resources/img/MajorError2.gif")).getImage();
            err_file_icon = new ImageIcon(getClass().getResource("/yass/resources/img/FileError.gif")).getImage();
            err_tags_icon = new ImageIcon(getClass().getResource("/yass/resources/img/TagError.gif")).getImage();
            err_text_icon = new ImageIcon(getClass().getResource("/yass/resources/img/TextError.gif")).getImage();

            no_err_page_icon = new ImageIcon(getClass().getResource("/yass/resources/img/MinorPageNoError.gif")).getImage();
            no_err_major_ico = new ImageIcon(getClass().getResource("/yass/resources/img/MajorNoError2.gif")).getImage();
            no_err_file_icon = new ImageIcon(getClass().getResource("/yass/resources/img/FileNoError.gif")).getImage();
            no_err_tags_icon = new ImageIcon(getClass().getResource("/yass/resources/img/TagNoError.gif")).getImage();
            no_err_text_icon = new ImageIcon(getClass().getResource("/yass/resources/img/TextNoError.gif")).getImage();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Rectangle getOverviewRect() {
        int width = Math.max(1, getWidth() - 2 * sideBar - 1);
        int y = txtBar;
        int height = Math.max(1, getHeight() - (msgBar + txtBar));
        return new Rectangle(sideBar, y, width, height);
    }

    private Rectangle getOverviewThumbRect(Rectangle overviewRect) {
        if (overviewRect.width <= 1 || rangeBeat <= 0) {
            return new Rectangle(overviewRect.x, overviewRect.y, 1, overviewRect.height);
        }

        YassTable master = sheet.getTable(0);
        if (master == null) {
            master = sheet.getTable(track);
        }
        if (master == null) {
            return new Rectangle(overviewRect.x, overviewRect.y, 1, overviewRect.height);
        }

        double minVisBeat = master.msToBeatExact(sheet.getMinVisibleMs());
        double maxVisBeat = master.msToBeatExact(sheet.getMaxVisibleMs());
        double leftRatio = (minVisBeat - minBeat) / rangeBeat;
        double rightRatio = (maxVisBeat - minBeat) / rangeBeat;
        leftRatio = Math.max(0, Math.min(1, leftRatio));
        rightRatio = Math.max(leftRatio, Math.min(1, rightRatio));

        int x = overviewRect.x + (int) Math.round(leftRatio * overviewRect.width);
        int w = (int) Math.round((rightRatio - leftRatio) * overviewRect.width);
        w = Math.max(OVERVIEW_HANDLE_WIDTH * 2 + 4, Math.min(overviewRect.width, w));
        if (x + w > overviewRect.x + overviewRect.width) {
            x = overviewRect.x + overviewRect.width - w;
        }
        if (x < overviewRect.x) {
            x = overviewRect.x;
        }
        return new Rectangle(x, overviewRect.y + 1, w, Math.max(8, overviewRect.height - 2));
    }

    private double overviewXToMs(int x, Rectangle overviewRect) {
        YassTable master = sheet.getTable(0);
        if (master == null) {
            master = sheet.getTable(track);
        }
        if (master == null || rangeBeat <= 0) {
            return 0;
        }
        int clampedX = Math.max(overviewRect.x, Math.min(overviewRect.x + overviewRect.width, x));
        double ratio = (clampedX - overviewRect.x) / (double) Math.max(1, overviewRect.width);
        double beat = minBeat + ratio * rangeBeat;
        return 1000d * 60d * beat / (4d * master.getBPM()) + master.getGap();
    }

    private int overviewMsToX(double ms, Rectangle overviewRect) {
        YassTable master = sheet.getTable(0);
        if (master == null) {
            master = sheet.getTable(track);
        }
        if (master == null || rangeBeat <= 0) {
            return overviewRect.x;
        }
        double beat = master.msToBeatExact(ms);
        double ratio = (beat - minBeat) / rangeBeat;
        ratio = Math.max(0, Math.min(1, ratio));
        return overviewRect.x + (int) Math.round(ratio * overviewRect.width);
    }

    private boolean handleOverviewMousePressed(MouseEvent e) {
        if (sheet.isPlaying() || sheet.isTemporaryStop()) {
            return false;
        }
        Rectangle overviewRect = getOverviewRect();
        if (!overviewRect.contains(e.getPoint())) {
            return false;
        }
        Rectangle thumb = getOverviewThumbRect(overviewRect);
        int x = e.getX();

        overviewDragLeftMs = Math.max(0, sheet.getMinVisibleMs());
        overviewDragRightMs = Math.max(overviewDragLeftMs + OVERVIEW_MIN_WINDOW_MS, sheet.getMaxVisibleMs());

        int pressEdgeSlop = getOverviewPressEdgeHitSlop(thumb);
        int leftDistance = Math.abs(x - thumb.x);
        int rightDistance = Math.abs(x - (thumb.x + thumb.width - 1));
        boolean nearLeftEdge = Math.abs(x - thumb.x) <= pressEdgeSlop;
        boolean nearRightEdge = Math.abs(x - (thumb.x + thumb.width - 1)) <= pressEdgeSlop;
        if (nearLeftEdge) {
            overviewDragMode = OverviewDragMode.RESIZE_LEFT;
            overviewFixedEdgeX = thumb.x + thumb.width - 1;
            logOverviewDrag("pressed-left", e.getX(), overviewRect, thumb, leftDistance, rightDistance, pressEdgeSlop);
            return true;
        }
        if (nearRightEdge) {
            overviewDragMode = OverviewDragMode.RESIZE_RIGHT;
            overviewFixedEdgeX = thumb.x;
            logOverviewDrag("pressed-right", e.getX(), overviewRect, thumb, leftDistance, rightDistance, pressEdgeSlop);
            return true;
        }
        if (thumb.contains(e.getPoint())) {
            overviewDragMode = OverviewDragMode.MOVE;
            overviewDragOffsetX = x - thumb.x;
            overviewFixedEdgeX = -1;
            logOverviewDrag("pressed-move", e.getX(), overviewRect, thumb, leftDistance, rightDistance, pressEdgeSlop);
            return true;
        }
        if (x < thumb.x || x > thumb.x + thumb.width - 1) {
            overviewDragMode = OverviewDragMode.NONE;
            overviewFixedEdgeX = -1;
            jumpOverviewToPageAtX(x);
            repaint();
            return true;
        }
        overviewDragMode = OverviewDragMode.NONE;
        return false;
    }

    private boolean handleOverviewMouseDragged(MouseEvent e) {
        if (overviewDragMode == OverviewDragMode.NONE) {
            return false;
        }
        Rectangle overviewRect = getOverviewRect();
        double duration = Math.max(1, sheet.getDuration());
        double leftMs = overviewDragLeftMs;
        double rightMs = overviewDragRightMs;
        double dragWindowMs = Math.max(OVERVIEW_MIN_WINDOW_MS, rightMs - leftMs);

        if (overviewDragMode == OverviewDragMode.MOVE) {
            double targetStartMs = overviewXToMs(e.getX() - overviewDragOffsetX, overviewRect);
            sheet.setVisibleWindowMs(targetStartMs, dragWindowMs);
            logOverviewDrag("drag-move", e.getX(), overviewRect, getOverviewThumbRect(overviewRect), -1, -1, -1);
            repaint();
            return true;
        }
        if (overviewDragMode == OverviewDragMode.RESIZE_LEFT) {
            double fixedRightMs = overviewDragRightMs;
            int fixedRightX = overviewMsToX(fixedRightMs, overviewRect);
            int maxLeftX = Math.max(overviewRect.x, fixedRightX - 1);
            int targetLeftX = Math.max(overviewRect.x, Math.min(maxLeftX, e.getX()));
            double targetLeftMs = overviewXToMs(targetLeftX, overviewRect);
            double maxLeftMs = Math.max(0, fixedRightMs - OVERVIEW_MIN_WINDOW_MS);
            leftMs = Math.min(maxLeftMs, Math.max(0, targetLeftMs));
            rightMs = fixedRightMs;
            sheet.setVisibleWindowMs(leftMs, rightMs - leftMs);
            enforceFixedOverviewEdge(overviewRect, true, overviewFixedEdgeX);
            logOverviewDrag("drag-left", e.getX(), overviewRect, getOverviewThumbRect(overviewRect), -1, -1, -1);
            repaint();
            return true;
        }
        if (overviewDragMode == OverviewDragMode.RESIZE_RIGHT) {
            double fixedLeftMs = overviewDragLeftMs;
            int fixedLeftX = overviewMsToX(fixedLeftMs, overviewRect);
            int minRightX = Math.min(overviewRect.x + overviewRect.width, fixedLeftX + 1);
            int targetRightX = Math.max(minRightX, Math.min(overviewRect.x + overviewRect.width, e.getX()));
            double targetRightMs = overviewXToMs(targetRightX, overviewRect);
            double minRightMs = Math.min(duration, fixedLeftMs + OVERVIEW_MIN_WINDOW_MS);
            rightMs = Math.max(minRightMs, Math.min(duration, targetRightMs));
            leftMs = fixedLeftMs;
            sheet.setVisibleWindowMs(leftMs, rightMs - leftMs);
            enforceFixedOverviewEdge(overviewRect, false, overviewFixedEdgeX);
            logOverviewDrag("drag-right", e.getX(), overviewRect, getOverviewThumbRect(overviewRect), -1, -1, -1);
            repaint();
            return true;
        }
        return false;
    }

    private boolean updateOverviewCursor(MouseEvent e) {
        Rectangle overviewRect = getOverviewRect();
        if (!overviewRect.contains(e.getPoint())) {
            return false;
        }
        Rectangle thumb = getOverviewThumbRect(overviewRect);
        if (!thumb.contains(e.getPoint())) {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            return true;
        }
        int x = e.getX();
        int edgeSlop = getOverviewEdgeHitSlop(thumb);
        boolean nearLeftEdge = Math.abs(x - thumb.x) <= edgeSlop;
        boolean nearRightEdge = Math.abs(x - (thumb.x + thumb.width - 1)) <= edgeSlop;
        if (nearLeftEdge) {
            setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
            return true;
        }
        if (nearRightEdge) {
            setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
            return true;
        }
        if (thumb.contains(e.getPoint())) {
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            return true;
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        return true;
    }

    private int getOverviewEdgeHitSlop(Rectangle thumb) {
        int adaptive = thumb.width / 8;
        adaptive = Math.max(OVERVIEW_MIN_EDGE_HIT_SLOP, adaptive);
        return Math.min(OVERVIEW_EDGE_HIT_SLOP, adaptive);
    }

    private int getOverviewPressEdgeHitSlop(Rectangle thumb) {
        int adaptive = thumb.width / 8;
        adaptive = Math.max(4, adaptive);
        return Math.min(8, adaptive);
    }

    private int getOverviewHandleZoneWidth(Rectangle thumb) {
        int adaptive = thumb.width / 4;
        adaptive = Math.max(OVERVIEW_HANDLE_WIDTH, adaptive);
        return Math.min(20, adaptive);
    }

    private double getCurrentVisibleWindowMs() {
        double minVisibleMs = Math.max(0, sheet.getMinVisibleMs());
        double maxVisibleMs = Math.max(minVisibleMs, sheet.getMaxVisibleMs());
        return Math.max(OVERVIEW_MIN_WINDOW_MS, maxVisibleMs - minVisibleMs);
    }

    private int getLastNoteRow(YassTable table) {
        if (table == null) {
            return -1;
        }
        for (int i = table.getRowCount() - 1; i >= 0; i--) {
            YassRow row = table.getRowAt(i);
            if (row != null && row.isNote()) {
                return i;
            }
        }
        return -1;
    }

    private boolean isInErrorQuickArea(int x, int y, int trackNameWidth) {
        return x > trackNameWidth + 30 && x < trackNameWidth + 230 && y < txtBar;
    }

    private void enforceFixedOverviewEdge(Rectangle overviewRect, boolean fixRightEdge, int desiredEdgeX) {
        if (desiredEdgeX < 0) {
            return;
        }
        double leftMs = sheet.getMinVisibleMs();
        double rightMs = sheet.getMaxVisibleMs();
        double windowMs = Math.max(OVERVIEW_MIN_WINDOW_MS, rightMs - leftMs);
        if (fixRightEdge) {
            double fixedRightMs = overviewXToMs(desiredEdgeX, overviewRect);
            sheet.setVisibleWindowMs(fixedRightMs - windowMs, windowMs);
        } else {
            double fixedLeftMs = overviewXToMs(desiredEdgeX, overviewRect);
            sheet.setVisibleWindowMs(fixedLeftMs, windowMs);
        }
    }

    private void logOverviewDrag(String event, int mouseX, Rectangle overviewRect, Rectangle thumb,
                                 int leftDistance, int rightDistance, int edgeSlop) {
        if (!LOGGER.isLoggable(java.util.logging.Level.FINE)) {
            return;
        }
        LOGGER.fine("[OverviewDrag] " + event
                + " mode=" + overviewDragMode
                + " mouseX=" + mouseX
                + " thumb=" + thumb.x + "+" + thumb.width
                + " overview=" + overviewRect.x + "+" + overviewRect.width
                + " leftDist=" + leftDistance
                + " rightDist=" + rightDistance
                + " edgeSlop=" + edgeSlop
                + " visibleMs=" + Math.round(sheet.getMinVisibleMs()) + "-" + Math.round(sheet.getMaxVisibleMs())
        );
    }

    public void removeListener() {
        sheet.removeYassSheetListener(sheetListener);
    }

    public int getTrack() {
        return track;
    }

    private void activateTrack() {
        YassTable table = sheet.getActiveTable();
        if (table == null)
            return;
        if (track != table.getActions().getActiveTrack())
            table.getActions().activateTrack(track);
    }
    private void showErrors() {
        sheet.getActiveTable().getActions().showErrors.actionPerformed(null);
    }

    private boolean isActiveTrack() {
        YassTable table = sheet.getActiveTable();
        return table != null && track == table.getActions().getActiveTrack();
    }

    private void jumpOverviewToPageAtX(int x) {
        YassTable table = sheet.getTable(track);
        if (table == null) {
            return;
        }
        int w = getWidth() - 1;
        int clampedX = Math.max(sideBar, Math.min(w - sideBar, x));
        int activeBeat = (int) (minBeat + (rangeBeat * (clampedX - sideBar) / ((double) w - 2 * sideBar)));
        int row = table.getIndexOfNoteBeforeBeat(activeBeat);
        if (row < 0) {
            row = table.getPage(1);
        }
        if (row < 0) {
            return;
        }
        if (row + 2 < table.getRowCount() - 1) {
            YassRow next = table.getRowAt(row + 1);
            if (next.isPageBreak() && activeBeat > next.getBeatInt()) {
                row += 2;
            }
        }

        double clickedMs = Math.max(0, overviewXToMs(clampedX, getOverviewRect()));
        int firstPageRow = table.getPage(1);
        int lastNoteRow = getLastNoteRow(table);
        double firstNoteMs = firstPageRow >= 0 ? Math.max(0, table.beatToMs(table.getRowAt(firstPageRow).getBeatInt())) : 0;
        double lastNoteMs = 0;
        if (lastNoteRow >= 0) {
            YassRow last = table.getRowAt(lastNoteRow);
            lastNoteMs = Math.max(0, table.beatToMs(last.getBeatInt() + last.getLengthInt()));
        }
        if (clickedMs < firstNoteMs || clickedMs > lastNoteMs) {
            double durationMs = Math.max(1, sheet.getDuration());
            double windowMs = Math.min(durationMs, getCurrentVisibleWindowMs());
            double maxStartMs = Math.max(0, durationMs - windowMs);
            double targetStartMs = Math.max(0, Math.min(maxStartMs, clickedMs - windowMs / 2.0));
            sheet.setVisibleWindowMs(targetStartMs, windowMs);
            if (sheet.isAbsolutePitchViewEnabled()) {
                sheet.autoCenterAbsolutePitchView();
            }
            return;
        }

        int pageNumber = table.getPageNumber(row);
        if (pageNumber <= 0) {
            return;
        }
        table.gotoPageNumber(pageNumber);
    }

    private void moveTo(int x, boolean exact) {
        YassTable table = sheet.getTable(track);
        if (table == null) return;

        if (sheet.isPlaying() || sheet.isTemporaryStop())
            return;

        // calculate ms in clicked track
        int w = getWidth()-1;
        if (x > w-sideBar) x = w-sideBar;
        if (x < sideBar) x = sideBar;
        int activeBeat = (int) (minBeat + (rangeBeat * (x-sideBar) / ((double) w - 2*sideBar)));
        double clickedMs = table.beatToMs(activeBeat);

        // select note at ms in active track
        int i= table.getIndexOfNoteBeforeBeat(activeBeat);
        if (i < 0)
            i = table.getPage(1);
        if (i>=0) {
            if (i+2 < table.getRowCount() - 1) {
                YassRow r = table.getRowAt(i+1); // clicked directly after pagebreak -> select note after beat
                if (r.isPageBreak() && activeBeat > r.getBeatInt()) i+=2;
            }
            table.setRowSelectionInterval(i, i);
            table.updatePlayerPosition();
            if (! sheet.isVisibleMs(clickedMs)) table.zoomPage();

            if (exact) {
                int newPos = sheet.toTimeline(clickedMs);
                int vx = sheet.getViewPosition().x;
                int tx = Math.max(0, newPos - 200);
                sheet.setPlayerPosition(newPos);
                sheet.slideRight(tx - vx);
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(600, notesBar + msgBar + txtBar);
    }

    private void setHeightRange(int minH, int maxH, int minB, int maxB) {
        minHeight = minH;
        rangeHeight = maxH - minHeight;
        YassTable master = sheet.getTable(0);
        if (master == null) {
            master = sheet.getTable(track);
        }
        if (master != null) {
            double songStartBeat = master.msToBeatExact(0);
            double songEndBeat = master.msToBeatExact(Math.max(1, sheet.getDuration()));
            if (songEndBeat > songStartBeat) {
                minBeat = songStartBeat;
                rangeBeat = songEndBeat - songStartBeat;
            } else {
                minBeat = minB;
                rangeBeat = Math.max(1, maxB - minB);
            }
        } else {
            minBeat = minB;
            rangeBeat = Math.max(1, maxB - minB);
        }
        repaint(0);
    }

    private void setPosMs(double ms) {
        posMs = ms;
        repaint(0);
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        paintInfoArea((Graphics2D)g);
    }

    public void paintInfoArea(Graphics2D g2) {
        if (sheet.isPlaying()) return;

        YassTable masterTable = sheet.getTable(0);
        YassTable table = sheet.getTable(track);
        if (table == null) return;
        int activeTrack = table.getActions().getActiveTrack();
        boolean isActive = track == activeTrack;

        double bpm = table.getBPM();
        double gap = table.getGap();

        Color[] colorSet = sheet.getColors();

        int x = 0, y = txtBar;
        double rx, rx2, ry, rw;
        final int trackNameWidth = sheet.getTableCount() > 1 ? 100 : 0;
        final int hBar = msgBar + txtBar;
        final int w = getWidth() - 2* sideBar - 1;
        final int h = getHeight();

        // sidebar
        g2.setColor(table.getTableColor());
        g2.fillRect(0, 0, sideBar, h);
        g2.fillRect(sideBar+w+1, 0, sideBar, h);
        if (isActive && sheet.getTableCount() > 1) {
            g2.setColor(sheet.darkMode ? sheet.whiteDarkMode : Color.WHITE);
            g2.fillRect(5, 5, sideBar-10, h-10);
        }

        // background
        x += sideBar;
        if (isActive) {
            g2.setColor(sheet.darkMode ? sheet.whiteDarkMode : Color.WHITE);
            g2.fillRect(x, y + h - hBar - notesBar, w, notesBar);
            g2.fillRect(x, 0, w, txtBar);
        }
        else {
            g2.setColor(sheet.darkMode ? sheet.HI_GRAY_2_DARK_MODE : sheet.HI_GRAY_2);
            g2.fillRect(x, 0, w, txtBar);
        }

        //  notes background
        g2.setColor(sheet.darkMode ? sheet.dkGrayDarkMode : sheet.DK_GRAY);
        g2.fillRect(x, y + h - hBar, w, hBar);
        g2.setColor(sheet.darkMode ? sheet.dkGrayDarkMode : sheet.DK_GRAY);
        g2.drawRect(x, y, w, h);
        g2.drawRect(x, y + h - hBar, w, hBar);

        if (rangeBeat <= 0)
            return;

        // samePages[i]==true -> page i same as active page i
        Vector<Boolean> sameAsActivePage = new Vector<>();
        if (! isActive) {
            YassTable table2 = sheet.getTable(activeTrack);
            boolean newPage = true;
            int n = table.getRowCount();
            int i1 = 0;
            while (i1 < n) {
                YassRow row = table.getRowAt(i1);
                if (row.isPageBreak()) {
                    newPage = true;
                } else if (row.isNote() && newPage) {
                    newPage = false;
                    boolean same = false;
                    int[] ij1 = table.enlargeToPages(i1, i1);
                    YassRow in1 = table.getRowAt(ij1[0]);
                    double ms = table.beatToMs(in1.getBeatInt());
                    int x1 = sheet.toTimeline(ms);
                    int i2 = sheet.nextNote(activeTrack, x1);
                    int[] ij2 = table2.enlargeToPages(i2, i2);
                    if (ij2 != null && ij1[1] - ij1[0] == ij2[1] - ij2[0]) { // same number of notes?
                        int len = ij1[1] - ij1[0] + 1;
                        int k = 0;
                        while (k < len) {
                            YassRow r1 = table.getRowAt(ij1[0] + k);
                            YassRow r2 = table2.getRowAt(ij2[0] + k);
                            if (r1.getBeatInt() != r2.getBeatInt() ||
                                    ! r1.getType().equals(r2.getType()) ||
                                    r1.getLengthInt() != r2.getLengthInt() || r1.getHeightInt() != r2.getHeightInt() ||
                                    ! r1.getText().equals(r2.getText())) {
                                break;
                            }
                            k++;
                        }
                        same = k == len;
                    }
                    sameAsActivePage.add(same);
                }
                ++i1;
            }
        }

        // selection
        double minVisBeat = masterTable.msToBeatExact(sheet.getMinVisibleMs());
        double maxVisBeat = masterTable.msToBeatExact(sheet.getMaxVisibleMs());
        if (isActive) {
            double rxx1 = (minVisBeat-minBeat)/rangeBeat * w;
            double rxx2 = (maxVisBeat-minBeat)/rangeBeat * w;
            g2.setColor(sheet.darkMode ? sheet.blueDragDarkMode : sheet.blueDrag);
            g2.fill(new Rectangle2D.Double(x + rxx1, y, rxx2 - rxx1, h - hBar));
            g2.setColor(sheet.darkMode ? sheet.blackDarkMode : Color.BLACK);
            g2.draw(new Rectangle2D.Double(x + rxx1, y+1, rxx2 - rxx1, h - hBar-2));
        }

        // notes
        Graphics2D g3 = (Graphics2D) g2.create();
        g3.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g3.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g3.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        YassRow rPrev = null;
        double rxPrev = 0, ryPrev = 0, rwPrev = 0, rxFirstNoteOnPage = 0;
        boolean firstNoteOnPage = true;
        int page = 0;
        boolean same = false;
        for (YassRow r: table.getModelData()) {
            if (r.isNote()) {
                rx = (r.getBeatInt() - minBeat)/rangeBeat * w;
                rx2 =(r.getBeatInt() + r.getLengthInt() - minBeat)/rangeBeat * w;
                ry = (int) ((h - hBar - 4) * (r.getHeightInt() - minHeight) / (double) rangeHeight + 3);
                rw = rx2 - rx;
                if (firstNoteOnPage) {
                    firstNoteOnPage = false;
                    rxFirstNoteOnPage = rx;
                    same = page < sameAsActivePage.size() && sameAsActivePage.elementAt(page).booleanValue();
                    page++;
                }
                Color fillColor = null;
                if (r.hasMessage())
                    fillColor = colorSet[YassSheet.COLOR_ERROR];
                else if (r.isGolden())
                    fillColor = colorSet[YassSheet.COLOR_GOLDEN];
                else if (r.isFreeStyle())
                    fillColor = colorSet[YassSheet.COLOR_FREESTYLE];
                else if (r.isRap())
                    fillColor = colorSet[YassSheet.COLOR_RAP];
                else if (r.isRapGolden())
                    fillColor = colorSet[YassSheet.COLOR_RAPGOLDEN];
                if (fillColor != null) {
                    g3.setColor(fillColor);
                    g3.fill(new Rectangle2D.Double(x + rx, y + h - hBar + 1, rw, hBar - 1));
                }
                if (same) {
                    g3.setColor(sheet.darkMode ? sheet.hiGrayDarkMode : sheet.HI_GRAY);
                    g3.setStroke(minLineStroke);
                } else {
                    g3.setColor(sheet.darkMode ? sheet.dkGrayDarkMode : sheet.DK_GRAY);
                    g3.setStroke(maxLineStroke);
                }
                g3.draw(new Line2D.Double(x + rx, y + h - hBar - ry, x + rx + rw, y + h - hBar - ry));
                if (rPrev != null && rPrev.isNote()) {
                    int gapNotes = r.getBeatInt() - (rPrev.getBeatInt() + rPrev.getLengthInt());
                    double gapNotesMs = gapNotes * 60 / (4 * bpm) * 1000;
                    if (gapNotesMs < 300) {
                        g3.setColor(sheet.darkMode ? sheet.hiGrayDarkMode : sheet.HI_GRAY);
                        g3.setStroke(minLineStroke);
                        g3.draw(new Line2D.Double(x + rxPrev + rwPrev, y + h - hBar - ryPrev, x + rx, y + h - hBar - ry));
                        g3.setStroke(medLineStroke);
                    }
                }
                rxPrev = rx;
                ryPrev = ry;
                rwPrev = rw;
                rPrev = r;
            }
            else if (r.isPageBreak()) {
                g3.setColor(sheet.darkMode ? sheet.dkGrayDarkMode : sheet.DK_GRAY);
                rx = (r.getBeatInt() - minBeat)/rangeBeat * w;
                rx2 = (r.getSecondBeatInt() - minBeat)/rangeBeat * w;
                rw = Math.max(1,rx2-rx);
                g3.fill(new Rectangle2D.Double(x + rx, y, rw, h));
                rPrev = null;
                firstNoteOnPage = true;
                String s = "" + page;
                int sw = g3.getFontMetrics().stringWidth(s);
                int sx = (int)(rxFirstNoteOnPage + rxPrev + rwPrev);
                if (sw < (rxPrev + rwPrev - rxFirstNoteOnPage) || page%5==0) {
                    g3.setColor(sheet.darkMode ? sheet.HI_GRAY_2_DARK_MODE : sheet.HI_GRAY_2);
                    g3.drawString(s, x + (sx - sw) / 2, y + h - hBar + 12);
                }
            }
            else if (r.isEnd()) {
                String s = "" + page;
                int sw = g3.getFontMetrics().stringWidth(s);
                int sx = (int)(rxFirstNoteOnPage + rxPrev + rwPrev);
                if (sw < (rxPrev + rwPrev - rxFirstNoteOnPage) || page%5==0) {
                    g3.setColor(sheet.darkMode ? sheet.HI_GRAY_2_DARK_MODE : sheet.HI_GRAY_2);
                    g3.drawString(s, x + (sx - sw) / 2, y + h - hBar + 12);
                }
            }
        }
        g3.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        g3.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_DEFAULT);
        g3.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);
        g3.dispose();
        g2.setStroke(sheet.stdStroke);

        // cursor
        double prx = (table.msToBeatExact(posMs)-minBeat)/rangeBeat * w;
        g2.setColor(sheet.playerColor);
        g2.fill(new Rectangle2D.Double(x + prx, y - 3, 2, h + 5));

        // track name
        x = x + 6;
        y = txtBar - 3;
        if (trackNameWidth > 0) {
            String name = table.getDuetTrackName();
            int duetTrack = table.getDuetTrack();
            if (duetTrack >= 0) {
                if (name == null || name.length() < 1)
                    name = "?";
                int sw = g2.getFontMetrics().stringWidth(name);
                if (sw > trackNameWidth && name.length() > 12)
                    name = name.substring(0, 10) + "...";
                g2.setColor(sheet.darkMode ? sheet.hiGrayDarkMode : sheet.HI_GRAY);
                g2.drawString(duetTrack + ": " + name, x + 22, y);
                g2.drawOval(x, y - msgBar + 4, msgBar - 5, msgBar - 5);
                g2.drawOval(x + 6, y - msgBar + 4, msgBar - 5, msgBar - 5);
            }
        }

        // error selection
        x = x + trackNameWidth + 6;
        int errorWidth = 190;
        int goldenPoints = table.getGoldenPoints();
        int idealGoldenPoints = table.getIdealGoldenPoints();
        int goldenVariance = table.getGoldenVariance();
        String goldenDiff = table.getGoldenDiff();
        boolean goldenErr = Math.abs(goldenPoints - idealGoldenPoints) > goldenVariance;
        hasErr = table.hasUnhandledError() || table.hasMinorPageBreakMessages() || table.hasPageBreakMessages() || table.hasSpacingMessages() || goldenErr;
        if (hasErr) {
            if (hiliteCue == SHOW_ERRORS) {
                g2.setColor(sheet.darkMode ? sheet.blueDragDarkMode : sheet.blueDrag);
                g2.fillRect(x, 3, errorWidth, txtBar - 4);
                g2.setColor(colorSet[YassSheet.COLOR_ERROR]);
                g2.setStroke(sheet.thickStroke);
                g2.drawRect(x, 3, errorWidth, txtBar - 4);
                g2.setStroke(sheet.stdStroke);
            }
        }
        // errors
        x = x + 10;
        y = txtBar - 3 - 13;
        int we = 16;
        int he = 16;
        if (table.hasUnhandledError()) {
            if (err_major_ico != null)
                g2.drawImage(err_major_ico, x + (we+8)*0, y, we, he, null);
        }
        else {
            if (no_err_major_ico != null)
                g2.drawImage(no_err_major_ico, x + (we+8)*0, y, we, he, null);
        }
        if (table.hasMinorPageBreakMessages() || table.hasPageBreakMessages()) {
            if (err_page_icon != null)
                g2.drawImage(err_page_icon, x + (we+8)*1, y, we, he, null);
        }
        else {
            if (no_err_page_icon != null)
                g2.drawImage(no_err_page_icon, x + (we+8)*1, y, we, he, null);
        }
        if (table.hasSpacingMessages()) {
            if (err_text_icon != null)
                g2.drawImage(err_text_icon, x + (we+8)*2, y, we, he, null);
        }
        else {
            if (no_err_text_icon != null)
                g2.drawImage(no_err_text_icon, x + (we+8)*2, y, we, he, null);
        }

        // golden
        y = txtBar - 3;
        if (idealGoldenPoints > 0) {
            int wg = 60;
            int hg = 8;
            int xg = x + 74;
            int y2 = y - 9;

            double varPercentage = goldenVariance / (double) idealGoldenPoints;
            int xVar = (int) (wg * varPercentage);
            double goldenPercentage = goldenPoints / (double) idealGoldenPoints;
            if (goldenPercentage > 2) goldenPercentage = 2;
            int xGold = (int) (wg / 2 * goldenPercentage);

            boolean perfect = goldenDiff.equals("0");
            if (! perfect) {
                String goldenStringMinor = "\u2605" + goldenDiff;
                g2.setColor(goldenErr ? colorSet[YassSheet.COLOR_ERROR] : (sheet.darkMode ? sheet.hiGrayDarkMode : sheet.HI_GRAY));
                g2.drawString(goldenStringMinor, xg + wg + 4, y);
            }
            else {
                String goldenStringMinor = "\u2605";
                g2.setColor(sheet.darkMode ? sheet.hiGrayDarkMode : sheet.HI_GRAY);
                g2.drawString(goldenStringMinor, xg + wg + 4, y);
            }

            if (! perfect) {
                g2.setColor(sheet.darkMode ? sheet.dkGrayDarkMode : sheet.DK_GRAY);
                g2.drawRect(xg, y2, wg, hg);
                if (goldenErr) {g2.setColor(goldenErr ? colorSet[YassSheet.COLOR_ERROR] : (sheet.darkMode ? sheet.dkGrayDarkMode : sheet.DK_GRAY));
                g2.fillRect(xg + 1, y2 + 1, wg - 1, hg - 1);}
                g2.setColor(colorSet[YassSheet.COLOR_GOLDEN]);
                g2.fillRect(xg + wg / 2 - xVar / 2, y2 + 1, xVar, hg - 1);
                g2.setColor(sheet.darkMode ? sheet.dkGrayDarkMode : sheet.DK_GRAY);
                g2.drawRect(xg + wg / 2, y2, 1, hg);
                g2.setColor(sheet.darkMode ? sheet.blackDarkMode : Color.BLACK);
                g2.drawRect(xg + xGold, y2 - 1, 1, hg + 2);
            }
            else {
                g2.setColor(sheet.darkMode ? sheet.hiGrayDarkMode : sheet.HI_GRAY);
                g2.drawRect(xg, y2, wg, hg);
                g2.drawRect(xg + xGold, y2, 1, hg );
            }
        }

        // artist/title/year
        x = getWidth()-sideBar; //-selectBar;
        String t = StringUtils.defaultString(table.getTitle());
        String a = StringUtils.defaultString(table.getArtist());
        String year = StringUtils.defaultString(table.getYear());
        String g = StringUtils.defaultString(table.getGenre());
        String fn = StringUtils.defaultString(table.getFilename());
        int sec = (int) (sheet.getDuration() / 1000.0 + 0.5);
        int min = sec / 60;
        sec = sec - min * 60;
        String dString = (sec < 10) ? min + ":0" + sec : min + ":" + sec;
        if (year == null) year = "";
        if (g == null) g = "";
        if (g.length() > 10) g = g.substring(0, 9) + "...";

        String s1 = a;
        if (s1.length() > 0 && t.length() > 0) s1 += " - ";
        s1 += t;

        String s2 = year;
        if (s2.length() > 0 && g.length() > 0) s2 += " \u00B7 ";
        s2 += g;
        String bpmString;
        if (bpm == (long) bpm) bpmString = String.format("%d", (int) bpm);
        else bpmString = String.format("%s", bpm);

        String gapString = String.format("%.3f", (int) (gap+0.5)/1000.0); // show rounded (resolution < 1ms makes no sense)

        if (s2 != null && s2.length() > 0)
            s2 += " \u00B7 ";
        s2 += gapString + "s";
        s2 += " \u00B7 " + bpmString + " bpm";
        s2 += " \u00B7 " + dString;

        String s = fn;
        if (! table.isSaved())
            s = "\uD83D\uDDAB" + s;

        int sw = g2.getFontMetrics().stringWidth(s);
        int sw1 = g2.getFontMetrics().stringWidth(s1);
        int sw2 = g2.getFontMetrics().stringWidth(s2);
        if (sw2 < w-trackNameWidth-errorWidth-20) {
            g2.setColor(sheet.darkMode ? sheet.hiGrayDarkMode : sheet.HI_GRAY);
            g2.drawString(s2, x - sw2 - 4, y);
        }
        if (sw1 < w-sw) {
            g2.setColor(sheet.darkMode ? sheet.hiGrayDarkMode : sheet.HI_GRAY);
            g2.drawString(s1, x - sw1 - 4, y-msgBar);
        }
        if (sw < w) {
            if (! table.isSaved())
                g2.setColor(sheet.darkMode ? sheet.dkGrayDarkMode : sheet.DK_GRAY);
            else
                g2.setColor(sheet.darkMode ? sheet.hiGrayDarkMode : sheet.HI_GRAY);
            g2.drawString(s, sideBar + 4, y-msgBar);
        }

        // select
        /*x = getWidth()-sideBar-selectBar;
        if (hiliteCue == SHOW_SELECT) {
            g2.setColor(sheet.blue);
            g2.fillRect(x, 2, selectBar-2, txtBar-4);
        }
        if (isSelected) {
            g2.setColor(sheet.darkMode ? sheet.dkGrayDarkMode : sheet.dkGray);
            g2.fillRect(x+3, 2+3, selectBar-2-5, txtBar-4-5);
        }
        g2.setColor(sheet.darkMode ? sheet.dkGrayDarkMode : sheet.dkGray);
        g2.drawRect(x, 2, selectBar-2, txtBar-4);*/
    }
}
