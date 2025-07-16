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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.math3.util.Precision;
import org.mozilla.universalchardet.Constants;
import unicode.UnicodeReader;
import yass.autocorrect.YassAutoCorrect;
import yass.autocorrect.YassAutoCorrectApostrophes;
import yass.hyphenator.HyphenatorDictionary;
import yass.renderer.YassLine;
import yass.renderer.YassNote;
import yass.renderer.YassSession;
import yass.renderer.YassTrack;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Year;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static yass.UltrastarHeaderTag.*;

public class YassTable extends JTable {
    public final static int ZOOM_TIME = 0;
    public final static int ZOOM_ONE = 1;
    public final static int ZOOM_MULTI = 2;
    public static int zoomMode = ZOOM_ONE;

    public int multiSize = 1;
    private final YassTableModel tm;
    private YassActions actions = null;
    private YassSheet sheet = null;
    private YassAutoCorrect auto = null;
    private YassProperties prop = null;
    private YassHyphenator hyphenator;
    private YassLanguageUtils languageUtils;
    private YassUtils yassUtils;
    
    private String mp3 = null;
    private String audio = null;
    private String instrumental = null;
    private String vocals = null;
    private String dir = null;
    private String txtFilename = null;
    private double bpm = 120, gap = 0, vgap = 0, start = 0, end = -1;
    private final int MAX_UNDO = 2048;
    private final Vector<YassUndoElement> undos = new Vector<>(MAX_UNDO);
    // [0] = P1, [1] = P2, ...
    private String[] duetSingerNames = new String[8];
    private int undoPos = -1, redoMax = 0;
    private boolean isRelative = false;
    private int maxP = 0;
    private Color myColor = null;
    private boolean saved = true;
    private boolean autosaved = true;
    private boolean isLoading = false;
    private String encoding = null;
    private boolean showMessages = true;
    private boolean preventUndo = false;
    private boolean preventAutoCheck = false;
    private boolean lyricsChanged = true;
    private Hashtable<String, Boolean> messages = null;
    private int relativePageBreak = 0;
    private boolean preventZoom = false;
    private int goldenPoints = 0;
    private int idealGoldenPoints = 0;
    private int goldenVariance = 0;
    private int durationGolden;
    private int idealGoldenBeats;
    private String goldenDiff;

    private YassFileUtils fileUtils;
    private int duetTrack = -1;
    private String duetTrackName = null;
    private int duetTrackCount = -1;
    private boolean preventRelativeToAbsoluteConversion = false;

    private UsdbSyncerMetaFile usdbSyncerMetaFile;
    private String usdbSyncerMetaFilePath;

    private Set<String> tags = new HashSet<>();

    public static final List<String> FIXED_UPPERCASE = List.of("I", "I'm", "I’m");

    public Timer timer;

    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    
    public YassTable() {
        fileUtils = new YassFileUtils();
        getTableHeader().setReorderingAllowed(false);
        createDefaultColumnsFromModel();
        ToolTipManager.sharedInstance().unregisterComponent(this);
        ToolTipManager.sharedInstance().unregisterComponent(getTableHeader());

        setDragEnabled(true);
        setTransferHandler(new YassTableTransferHandler());

        getSelectionModel().addListSelectionListener(
                e -> {
                    if (sheet != null) {
                        sheet.repaint();
                    }
                });
        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                zoomPage();
            }
        });
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                zoomPage();
                updatePlayerPosition();
            }

            public void mouseClicked(MouseEvent e) {
                zoomPage();
                if (e.getClickCount() > 1) {
                    int i = getSelectedRow();
                    if (i < 0) {
                        return;
                    }
                    int col = columnAtPoint(e.getPoint());

                    YassRow r = tm.getRowAt(i);
                    if (r.isGap() || r.isEnd() || r.isComment()) {
                        firePropertyChange("edit", null, "start");
                    }

                    if (r.isNote() && col == 4) {
                        firePropertyChange("edit", null, "start");
                    }

                    if (r.isNote() && col == 0) {
                        if (r.isGolden()) {
                            r.setType("G");
                        } else if (r.isRapGolden()) {
                            r.setType("R");
                        } else if (r.isRap()) {
                            r.setType("F");
                        } else if (r.isFreeStyle()) {
                            r.setType(":");
                        } else {
                            r.setType("*");
                        }
                        tm.fireTableRowsUpdated(i, i);
                    }
                }
            }
        });
        setDefaultRenderer(Object.class, new YassTableRenderer());
        setAutoscrolls(true);

        boolean oldUndo = preventUndo;
        preventUndo = true;
        setModel(tm = new YassTableModel());
        addTableModelListener();
        removeAllRows();
        preventUndo = oldUndo;
    }

    /**
     * Constructor for YassTable for unit testing only
     *
     * @param yassTableModel
     */
    public YassTable(YassTableModel yassTableModel, YassProperties props) {
        this.tm = yassTableModel;
        this.prop = props;
    }

    private void addTableModelListener() {
        tm.addTableModelListener(e -> {
            int i = e.getFirstRow();
            int j = e.getLastRow();
            int t = e.getType();
            if (i == TableModelEvent.HEADER_ROW)
                return;
            if (i < 0)
                return;

            if (!(isLoading || preventUndo)) {
                setSaved(false);
                addUndo();
            }

            // don't check comment header
            // (done only after loading && focusGained)
            boolean checkAll = t != TableModelEvent.UPDATE;
            if (actions != null && !preventAutoCheck) {
                actions.checkData(YassTable.this, checkAll, checkAll);
            }

            repaint();
            if (sheet != null) {
                int n = getRowCount();
                if (i >= n || j >= n || t != TableModelEvent.UPDATE) {
                    sheet.init();
                }
                // errors may affect unchanged notes
                // so always update all rows
                if (t == TableModelEvent.UPDATE) {
                    sheet.updateActiveTable(); // todo really active?
                    sheet.repaint();
                    sheet.firePropsChanged();
                }
            }
            if (actions != null) actions.updateActions();
        });
    }

    public void removeAllRows() {
        tm.getData().clear();
        resetUndo();
        addUndo();
        mp3 = dir = txtFilename = null;
        gap = 0;
        bpm = 100;
        vgap = 0;
        start = 0;
        end = -1;
        isRelative = false;
        maxP = 0;
        encoding = null;
        duetTrack = -1;
        duetTrackCount = -1;
        duetTrackName = null;
        Arrays.fill(duetSingerNames, null);
        if (actions != null) actions.updateActions();
    }

    public static int getZoomMode() {
        return zoomMode;
    }

    public static void setZoomMode(int i) {
        zoomMode = i;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String s) {
        encoding = s;
    }

    public boolean showMessages() {
        return showMessages;
    }

    public void showMessages(boolean onoff) {
        showMessages = onoff;
    }

    public boolean isSaved() {
        return saved;
    }

    public boolean isAutosaved() {
        return autosaved;
    }

    public void setSaved(boolean onoff) {
        saved = onoff;
        autosaved = onoff;
    }

    public void setAutosaved(boolean autosaved) {
        this.autosaved = autosaved;
    }

    public Color getTableColor() {
        return myColor;
    }

    public void setTableColor(Color c) {
        myColor = c;
    }

    public void updatePlayerPosition() {
        if (actions != null) {
            actions.updatePlayerPosition();
        }
    }

    public boolean getPreventUndo() {
        return preventUndo;
    }

    public void setPreventUndo(boolean onoff) {
        preventUndo = onoff;
    }

    public boolean getPreventAutoCheck() {
        return preventAutoCheck;
    }

    public void setPreventAutoCheck(boolean onoff) {
        preventAutoCheck = onoff;
    }

    public void preventLyricsUpdate(boolean onoff) {
        lyricsChanged = !onoff;
    }

    public boolean lyricsChanged() {
        return lyricsChanged;
    }

    public YassActions getActions() {
        return actions;
    }

    public void setActions(YassActions a) {
        actions = a;
    }

    public void setSheet(YassSheet s) {
        sheet = s;
    }

    public void init(YassProperties p) {
        prop = p;
    }

    public YassProperties getProperties() {
        return prop;
    }

    public YassAutoCorrect getAutoCorrect() {
        return auto;
    }

    public void setAutoCorrect(YassAutoCorrect a) {
        auto = a;
    }

    public String getMP3() {
        return mp3;
    }

    public void setMP3(String s) {
        mp3 = s;
        YassRow r = tm.getCommentRow(MP3);
        if (r == null) {
            return;
        }
        r.setComment(s);
    }

    public String getDir() {
        return dir;
    }

    public void setDir(String d) {
        dir = d;
    }

    public String getFilename() {
        return txtFilename;
    }

    public void setFilename(String s) {
        txtFilename = s;
    }

    /**
     * String dir/filename
     *
     * @return null if any is null
     */
    public String getDirFilename() {
        if (dir == null || txtFilename == null)
            return null;
        return dir + File.separator + txtFilename;
    }

    /**
     * String dir/mp3file
     *
     * @return null if any is null
     */
    public String getDirMP3() {
        if (dir == null || (mp3 == null && audio == null)) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(File.separator);
        joiner.add(dir);
        if (prop.isShinyOrNewer()) {
            if (prop.getBooleanProperty("debug-waveform") && StringUtils.isNotEmpty(vocals)) {
                joiner.add(vocals);
            } else if (StringUtils.isNotEmpty(audio)) {
                joiner.add(audio);
            } else {
                joiner.add(mp3);
            }
        } else {
            joiner.add(mp3);
        }
        return joiner.toString();
    }

    public String getCanonicalFilename() {
        String a = getArtist();
        String t = getTitle();
        return YassSong.toFilename(a + " - " + t + ".txt");
    }

    public double getGap() {
        return gap;
    }

    public void setGap(double g) {
        gap = g;
        String s = Integer.toString((int) gap);
        YassRow r = tm.getCommentRow("GAP:");
        if (r == null) {
            r = new YassRow("#", "GAP:", s, "", "");
            YassRow v = tm.getCommentRow("BPM:");
            int i = v != null ? tm.getData().indexOf(v) : 0;
            tm.getData().insertElementAt(r, i + 1);
            tm.fireTableDataChanged();
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
            }
        }
    }

    public void addGap(double g) {
        setGap(calculateNewGap(g, getGap()));
        if (sheet != null) {
            sheet.update();
            sheet.repaint();
        }
    }

    public static double calculateNewGap(double gapChange, double currentGap) {
        int mod = (int)currentGap % 10;
        if ((int)Math.abs(gapChange) == 10 && mod > 0) {
            gapChange = gapChange > 0 ? (gapChange - mod) : (-1 * mod);
        }
        double newGap = currentGap + gapChange;
        return newGap > 0 ? newGap : 0;
    }

    public void addBpm(double g) {
        setBPM(calculateNewBpm(g, getBPM()));
        if (sheet != null) {
            sheet.update();
            sheet.repaint();
        }
    }

    public static double calculateNewBpm(double dblBpmChange, double dblCurrentBpm) {
        int bpmChange = (int)(Math.round(dblBpmChange * 100));
        int currentBpm = (int)(Math.round(dblCurrentBpm * 100));
        int mod;
        if (Math.abs(bpmChange) == 10) {
            mod = currentBpm % 10;
        } else if (Math.abs(bpmChange) == 100) {
            mod = currentBpm % 100;
        } else {
            mod = 0;
        }
        if (mod > 0) {
            bpmChange = bpmChange > 0 ? (bpmChange - mod) : (-1 * mod);
        }
        double newGap = ((double)currentBpm + (double)bpmChange) / 100d;
        return newGap > 0 ? newGap : 0;
    }
    public double getBPM() {
        return bpm;
    }

    public void setBPM(double b) {
        bpm = b;
        DecimalFormat decimalFormat = new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.US));
        String s = decimalFormat.format(b);
        YassRow r = tm.getCommentRow("BPM:");
        if (r == null) {
            r = new YassRow("#", "BPM:", s, "", "");
            YassRow v = tm.getCommentRow("GAP:");
            int i = v != null ? tm.getData().indexOf(v) : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
            }
        }

        repaint();
    }

    public void setBPM(String s) {
        if (s == null || s.length() < 1) {
            return;
        }
        s = s.replace(',', '.');
        double d = Double.parseDouble(s);
        if (d > 0) {
            setBPM(d);
        }
    }

    public double getStart() {
        return start;
    }

    public void setStart(double b) {
        start = b;

        String s = (b == (int) b) ? Integer.toString((int) b) : Double.toString(start);
        YassRow r = tm.getCommentRow("START:");
        if (r == null && start > 0) {
            r = new YassRow("#", "START:", s, "", "");
            YassRow v = tm.getCommentRow("GAP:");
            int i = v != null ? tm.getData().indexOf(v) + 1 : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
        } else if (start <= 0) {
            if (r != null) {
                tm.getData().removeElement(r);
                tm.fireTableDataChanged();
            }
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
            }
        }
    }

    public void setStart(String s) {
        if (s == null || s.length() < 1) {
            return;
        }
        s.replace(',', '.');
        double d = Double.parseDouble(s);
        if (d > 0) {
            setStart(d);
        }
    }

    public double getEnd() {
        return end;
    }

    public void setEnd(double b) {
        end = b;

        String s = Double.toString(end);
        YassRow r = tm.getCommentRow("END:");
        if (r == null && end >= 0) {
            r = new YassRow("#", "END:", s, "", "");
            YassRow v = tm.getCommentRow("GAP:");
            int i = v != null ? tm.getData().indexOf(v) + 1 : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
        } else if (end < 0) {
            if (r != null) {
                tm.getData().removeElement(r);
                tm.fireTableDataChanged();
            }
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
            }
        }
    }

    public void setEnd(String s) {
        if (s == null || s.length() < 1) {
            return;
        }
        s.replace(',', '.');
        double d = Double.parseDouble(s);
        if (d > 0) {
            setEnd(d);
        }
    }

    public double getVideoGap() {
        return vgap;
    }

    public void setVideoGap(double g) {
        vgap = g;
        String s = Double.toString(vgap).replace('.', ',');
        if (s.endsWith(",0")) {
            s = s.substring(0, s.length() - 2);
        }
        YassRow r = tm.getCommentRow("VIDEOGAP:");
        if (r == null) {
            r = new YassRow("#", "VIDEOGAP:", s, "", "");
            YassRow v = tm.getCommentRow("VIDEO:");
            int i = v != null ? tm.getData().indexOf(v) : 0;
            tm.getData().insertElementAt(r, i + 1);
            tm.fireTableDataChanged();
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
            }
        }
    }

    public void setVideoGap(String vg) {
        vg = vg.replace(',', '.');
        double vgap = Double.parseDouble(vg);
        setVideoGap(vgap);
    }

    public double getPreviewStart() {
        YassRow r = tm.getCommentRow("PREVIEWSTART:");
        if (r == null) {
            return -1;
        }

        String p = r.getHeaderComment();
        p = p.replace(',', '.');
        return Double.parseDouble(p);
    }

    public void setPreviewStart(double dblPrevStart) {
        String s = (dblPrevStart == (int) dblPrevStart) ? Integer.toString((int) dblPrevStart) : Double.toString(
                dblPrevStart);
        YassRow r = tm.getCommentRow("PREVIEWSTART:");
        if (r == null) {
            r = new YassRow("#", "PREVIEWSTART:", s, "", "");
            YassRow v = tm.getCommentRow("GAP:");
            int i = v != null ? tm.getData().indexOf(v) : 0;
            tm.getData().insertElementAt(r, i + 1);
            tm.fireTableDataChanged();
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
            }
        }
    }

    public void setPreviewStart(String p) {
        p = p.replace(',', '.');
        double pp = Double.parseDouble(p);
        setPreviewStart(pp);
    }

    public int getMedleyStartBeat() {
        YassRow r = tm.getCommentRow("MEDLEYSTARTBEAT:");
        if (r == null) {
            return -1;
        }

        String p = r.getHeaderComment();
        return Integer.parseInt(p);
    }

    public void setMedleyStartBeat(int g) {
        String s = String.valueOf(g);
        YassRow r = tm.getCommentRow("MEDLEYSTARTBEAT:");
        if (r == null) {
            r = new YassRow("#", "MEDLEYSTARTBEAT:", s, "", "");
            YassRow v = tm.getCommentRow("PREVIEWSTART:");
            if (v == null) {
                v = tm.getCommentRow("GAP:");
            }
            int i = v != null ? tm.getData().indexOf(v) : 0;
            tm.getData().insertElementAt(r, i + 1);
            tm.fireTableDataChanged();
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
            }
        }
    }

    public int getMedleyEndBeat() {
        YassRow r = tm.getCommentRow("MEDLEYENDBEAT:");
        if (r == null) {
            return -1;
        }

        String p = r.getHeaderComment();
        return Integer.parseInt(p);
    }

    public void setMedleyEndBeat(int g) {
        String s = String.valueOf(g);
        YassRow r = tm.getCommentRow("MEDLEYENDBEAT:");
        if (r == null) {
            r = new YassRow("#", "MEDLEYENDBEAT:", s, "", "");
            YassRow v = tm.getCommentRow("MEDLEYSTARTBEAT:");
            if (v == null) {
                v = tm.getCommentRow("PREVIEWSTART:");
            }
            if (v == null) {
                v = tm.getCommentRow("GAP:");
            }
            int i = v != null ? tm.getData().indexOf(v) : 0;
            tm.getData().insertElementAt(r, i + 1);
            tm.fireTableDataChanged();
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
            }
        }
    }

    public void setCalcMedley(String calcMedley) {
        YassRow r = tm.getCommentRow(CALCMEDLEY.getTagName());
        if (r == null) {
            if (StringUtils.isEmpty(calcMedley)) {
                // Nothing to do
                return;
            }
            r = new YassRow("#", CALCMEDLEY.getTagName(), calcMedley, "", "");
            int rowIndex = removeMedleyTags();
            YassRow v = tm.getCommentRow(PREVIEWSTART.getTagName());
            if (v == null) {
                v = tm.getCommentRow(GAP.getTagName());
            }
            if (rowIndex == 0 && v != null) {
                rowIndex = tm.getData().indexOf(v);
            }
            tm.getData().insertElementAt(r, rowIndex + 1);
            tm.fireTableDataChanged();
        } else {
            if (StringUtils.isEmpty(calcMedley)) {
                tm.removeRowAt(tm.getData().indexOf(r));
                return;
            }
            removeMedleyTags();
            String old = r.getHeaderComment();
            if (!calcMedley.equals(old)) {
                r.setComment(calcMedley);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
            }
        }
    }

    private int removeMedleyTags() {
        int removedIndex = 0;
        YassRow v = tm.getCommentRow(MEDLEYENDBEAT.getTagName());
        if (v != null) {
            removedIndex = tm.getData().indexOf(v);
            tm.getData().remove(v);
        }
        v = tm.getCommentRow(MEDLEYSTARTBEAT.getTagName());
        if (v != null) {
            tm.getData().remove(v);
        }
        return removedIndex;
    }

    public void setMedleyStartBeat(String p) {
        int pp = Integer.parseInt(p);
        setMedleyStartBeat(pp);
    }

    public void setMedleyEndBeat(String p) {
        int pp = Integer.parseInt(p);
        setMedleyEndBeat(pp);
    }

    public boolean isRelative() {
        YassRow r = tm.getCommentRow("RELATIVE:");
        if (r == null) {
            return false;
        }
        String s = r.getHeaderComment();
        if (s == null) {
            return false;
        }
        s = s.toLowerCase();
        return s.equals("yes") || s.equals("true");
    }

    /**
     * Gets largest P number (singer mask) in file.
     * Examples:
     * P 2 ==> 2
     * P 4 ==> 4
     * P 8 ==> 8
     * <p>
     * To get the actual track number, {@link YassUtils#getBitCount(int)}
     * Examples:
     * P 1 ==> 1
     * P 2 ==> 2
     * P 4 ==> 3
     * P 8 ==> 4
     *
     * @return
     */
    public int getMaxP() {
        return maxP;
    }

    /**
     * Gets number of singers, as determined by largest P number in file.
     *
     * @return >= 1
     */
    public int getPlayerCount() {
        if (maxP > 1)
            return YassUtils.getBitCount(maxP);
        return 1;
    }

    public String getArtist() {
        YassRow r = tm.getCommentRow(ARTIST);
        if (r == null) {
            return null;
        }
        return r.getHeaderComment();
    }

    public String getTitle() {
        YassRow r = tm.getCommentRow(TITLE);
        if (r == null) {
            return null;
        }
        return r.getHeaderComment();
    }

    public boolean setTitle(String s) {
        YassRow r = tm.getCommentRow(TITLE);
        if (r == null) {
            return false;
        }
        String old = r.getHeaderComment();
        if (!s.equals(old)) {
            r.setComment(s);
            tm.fireTableDataChanged();
            return true;
        }
        return false;
    }

    public boolean setArtist(String s) {
        YassRow r = tm.getCommentRow(ARTIST);
        if (r == null) {
            return false;
        }
        String old = r.getHeaderComment();
        if (!s.equals(old)) {
            r.setComment(s);
            tm.fireTableDataChanged();
            return true;
        }
        return false;
    }


    public UltrastarHeaderTagVersion getVersion() {
        YassRow row = tm.getCommentRow(VERSION);
        if (row == null) {
            return null;
        }
        return UltrastarHeaderTagVersion.getFormatVersion(row.getHeaderComment());
    }

    public String getGenre() {
        YassRow r = tm.getCommentRow(GENRE);
        if (r == null) {
            return null;
        }
        return r.getHeaderComment();
    }

    public String getEdition() {
        YassRow r = tm.getCommentRow(EDITION);
        if (r == null) {
            return null;
        }
        return r.getHeaderComment();
    }

    public String getAlbum() {
        YassRow r = tm.getCommentRow(ALBUM);
        if (r == null) {
            return null;
        }
        return r.getHeaderComment();
    }

    public String getID() {
        YassRow r = tm.getCommentRow(ID);
        if (r == null) {
            return null;
        }
        return r.getHeaderComment();
    }

    public String getLength() {
        YassRow r = tm.getCommentRow(LENGTH);
        if (r == null) {
            return null;
        }
        return r.getHeaderComment();
    }

    public String getCover() {
        YassRow r = tm.getCommentRow(COVER);
        if (r == null) {
            return null;
        }
        return r.getHeaderComment();
    }

    public String getBackgroundTag() {
        YassRow r = tm.getCommentRow(BACKGROUND);
        if (r == null) {
            return null;
        }
        return r.getHeaderComment();
    }

    public String getVideo() {
        YassRow r = tm.getCommentRow(VIDEO);
        if (r == null) {
            return null;
        }
        return r.getHeaderComment();
    }

    public String getLanguage() {
        YassRow r = tm.getCommentRow(LANGUAGE);
        if (r == null) {
            return null;
        }
        return r.getHeaderComment();
    }

    public String getYear() {
        YassRow r = tm.getCommentRow(YEAR);
        if (r == null) {
            return null;
        }
        return r.getHeaderComment();
    }


    public String getCommentTag() {
        YassRow r = tm.getCommentRow(COMMENT);
        if (r == null) {
            return null;
        }
        return r.getHeaderComment();
    }
    
    public boolean setGenre(String s) {
        YassRow r = tm.getCommentRow("GENRE:");
        if (r == null) {
            r = new YassRow("#", "GENRE:", s, "", "");
            YassRow v = tm.getCommentRow("EDITION:");
            if (v == null) {
                v = tm.getCommentRow("LANGUAGE:");
            }
            if (v == null) {
                v = tm.getCommentRow("ARTIST:");
            }
            int i = v != null ? tm.getData().indexOf(v) + 1 : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
            return true;
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
                return true;
            }
        }
        return false;
    }

    public boolean setTags(String tag) {
        YassRow r = tm.getCommentRow(TAGS);
        if (StringUtils.isEmpty(tag) && r != null) {
            tm.removeRowAt(tm.getData().indexOf(r));
            return true;
        }
        if (r == null) {
            if (StringUtils.isEmpty(tag)) {
                return false;
            }
            r = new YassRow("#", TAGS.getTagName(), tag, "", "");
            YassRow v = tm.getCommentRow(GENRE);
            if (v == null) {
                v = tm.getCommentRow(EDITION);
            }
            if (v == null) {
                v = tm.getCommentRow(LANGUAGE);
            }
            if (v == null) {
                v = tm.getCommentRow(ARTIST);
            }
            int i = v != null ? tm.getData().indexOf(v) + 1 : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
            return true;
        } else {
            String newTag;
            if (tag.contains("[YEAR]")) {
                YassRow yearRow = tm.getCommentRow(YEAR);
                String year;
                if (yearRow == null) {
                    year = Integer.toString(Year.now().getValue());
                } else {
                    year = yearRow.getHeaderComment();
                }
                newTag = tag.replace("[YEAR]", year);
            } else {
                newTag = tag;
            }
            if (!newTag.equals(r.getHeaderComment())) {
                r.setComment(newTag);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
                return true;
            }
        }
        return false;
    }

    public String getTags() {
        YassRow tags = tm.getCommentRow(TAGS);
        if (tags != null) {
            return tags.getHeaderComment();
        } else {
            return null;
        }
    }

    public boolean setTags(String tag, boolean add) {
        YassRow r = tm.getCommentRow(TAGS);
        if (r == null) {
            if (!add) {
                // Nothing to do
                return false;
            }
            r = new YassRow("#", TAGS.getTagName(), tag, "", "");
            YassRow v = tm.getCommentRow(GENRE);
            if (v == null) {
                v = tm.getCommentRow(EDITION);
            }
            if (v == null) {
                v = tm.getCommentRow(LANGUAGE);
            }
            if (v == null) {
                v = tm.getCommentRow(ARTIST);
            }
            int i = v != null ? tm.getData().indexOf(v) + 1 : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
            return true;
        } else {
            String newTag;
            if (tag.contains("[YEAR]")) {
                YassRow yearRow = tm.getCommentRow(YEAR);
                String year;
                if (yearRow == null) {
                    year = Integer.toString(Year.now().getValue());
                } else {
                    year = yearRow.getHeaderComment();
                }
                newTag = tag.replace("[YEAR]", year);
            } else {
                newTag = tag;
            }
            if (add && addTags(newTag)) {
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
                return true;
            }
            if (!add) {
                return removeTagFromTags(tag);
            }
        }
        return false;
    }

    private boolean removeTagFromTags(String tag) {
        boolean changed;
        Set<String> currentTags = new HashSet<>(getTagsAsSet());
        changed = currentTags.contains(tag);
        currentTags.remove(tag);
        YassRow tagsRow = getCommentRow(TAGS.getTagName());
        String newTags = currentTags.stream().sorted(Comparator.naturalOrder()).collect(Collectors.joining(", "));
        if (tagsRow == null) {
            // Should not happen
            return false;
        } else {
            if (StringUtils.isNotEmpty(newTags)) {
                tagsRow.setComment(newTags);
            } else {
                getModelData().remove(tagsRow);
            }
        }
        return changed;
    }

    private boolean addTags(String newTag) {
        boolean changed;
        Set<String> currentTags = new HashSet<>(getTagsAsSet());
        Set<String> newTags = Arrays.stream(newTag.split(","))
                                    .map(YassSong::normalizeTag)
                                    .collect(Collectors.toSet());
        changed = !currentTags.contains(newTags);
        currentTags.addAll(newTags);
        YassRow tagsRow = getCommentRow(TAGS.getTagName());
        String newTagsString = currentTags.stream()
                                          .sorted(Comparator.naturalOrder())
                                          .collect(Collectors.joining(", "));
        if (tagsRow == null) {
            // Should not happen
            return false;
        } else {
            tagsRow.setComment(newTagsString);
        }
        return changed;
    }

    private Set<String> getTagsAsSet() {
        YassRow tagsRow = getCommentRow(TAGS.getTagName());
        if (tagsRow == null) {
            return new HashSet<>();
        }
        return Arrays.stream(tagsRow.getHeaderComment().split(",")).map(YassSong::normalizeTag).collect(Collectors.toSet());
    }

    public boolean setVersion() {
        YassRow r = tm.getCommentRow(VERSION);
        String actualVersion = prop.getUsFormatVersion().version;
        int rowToInsert = 0;
        if (r == null) {
            r = new YassRow("#", VERSION.getTagName(), actualVersion, "", "");
        } else {
            String old = r.getHeaderComment();
            if (!actualVersion.equals(old)) {
                r.setComment(actualVersion);
                tm.getData().remove(r);
            } else {
                int currentRow = tm.getData().indexOf(r);
                YassRow nextRow = tm.getRowAt(currentRow + 1);
                if (nextRow.isNote()) {
                    return false;
                }
                tm.getData().remove(r);
            }
        }
        for (int i = 0; i < tm.getRowCount(); i++) {
            YassRow firstNote = tm.getRowAt(i);
            if (firstNote.isNote() || firstNote.isP()) {
                rowToInsert = i;
                break;
            }
        }
        tm.getData().insertElementAt(r, rowToInsert);
        tm.fireTableDataChanged();
        return true;
    }

    public boolean setCommentTag(String comment) {
        YassRow r = tm.getCommentRow(COMMENT);
        int rowToInsert = 0;
        if (r == null) {
            r = new YassRow("#", COMMENT.getTagName(), comment, "", "");
        } else {
            r.setComment(comment);
            tm.fireTableDataChanged();
            return true;
        }
        for (int i = 0; i < tm.getRowCount(); i++) {
            YassRow firstNote = tm.getRowAt(i);
            if (firstNote.isNote() || firstNote.isP()) {
                rowToInsert = i;
                break;
            }
        }
        tm.getData().insertElementAt(r, rowToInsert - 1);
        return true;
    }

    public boolean setEdition(String s) {
        YassRow r = tm.getCommentRow("EDITION:");
        if (r == null) {
            r = new YassRow("#", "EDITION:", s, "", "");
            YassRow v = tm.getCommentRow("LANGUAGE:");
            if (v == null) {
                v = tm.getCommentRow("ARTIST:");
            }
            int i = v != null ? tm.getData().indexOf(v) + 1 : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
            return true;
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
                return true;
            }
        }
        return false;
    }

    public boolean setLanguage(String s) {
        YassRow r = tm.getCommentRow("LANGUAGE:");
        if (r == null) {
            r = new YassRow("#", "LANGUAGE:", s, "", "");
            YassRow v = tm.getCommentRow("ARTIST:");
            int i = v != null ? tm.getData().indexOf(v) + 1 : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
            return true;
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
                return true;
            }
        }
        return false;
    }

    public boolean setYear(String s) {
        if (s != null && (s.trim().equals("0") || s.trim().length() < 1)) {
            s = null;
        }

        YassRow r = tm.getCommentRow("YEAR:");
        if (r == null && s != null) {
            r = new YassRow("#", "YEAR:", s, "", "");
            YassRow v = tm.getCommentRow("LANGUAGE:");
            if (v == null) {
                v = tm.getCommentRow("GENRE:");
            }
            if (v == null) {
                v = tm.getCommentRow("EDITION:");
            }
            if (v == null) {
                v = tm.getCommentRow("ARTIST:");
            }
            int i = v != null ? tm.getData().indexOf(v) + 1 : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
            return true;
        } else if (s == null) {
            if (r != null) {
                tm.getData().removeElement(r);
                tm.fireTableDataChanged();
            }
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
                return true;
            }
        }
        return false;
    }

    public boolean setAlbum(String s) {
        YassRow r = tm.getCommentRow("ALBUM:");
        if (r == null) {
            r = new YassRow("#", "ALBUM:", s, "", "");
            YassRow v = tm.getCommentRow("GENRE:");
            if (v == null) {
                v = tm.getCommentRow("ARTIST:");
            }
            int i = v != null ? tm.getData().indexOf(v) + 1 : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
            return true;
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
                return true;
            }
        }
        return false;
    }

    public boolean setID(String s) {
        YassRow r = tm.getCommentRow("ID:");
        if (r == null) {
            r = new YassRow("#", "ID:", s, "", "");
            YassRow v = tm.getCommentRow("YEAR:");
            if (v == null) {
                v = tm.getCommentRow("ARTIST:");
            }
            int i = v != null ? tm.getData().indexOf(v) + 1 : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
            return true;
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
                return true;
            }
        }
        return false;
    }

    public boolean setLength(String s) {
        YassRow r = tm.getCommentRow("LENGTH:");
        if (r == null) {
            r = new YassRow("#", "LENGTH:", s, "", "");
            YassRow v = tm.getCommentRow("GAP:");
            if (v == null) {
                v = tm.getCommentRow("MP3:");
            }
            int i = v != null ? tm.getData().indexOf(v) + 1 : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
            return true;
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
                return true;
            }
        }
        return false;
    }

    public boolean setCover(String s) {
        YassRow r = tm.getCommentRow("COVER:");
        if (r == null) {
            r = new YassRow("#", "COVER:", s, "", "");
            YassRow v = tm.getCommentRow("MP3:");
            if (v == null) {
                v = tm.getCommentRow("TITLE:");
            }
            int i = v != null ? tm.getData().indexOf(v) + 1 : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
            return true;
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
                return true;
            }
        }
        return false;
    }

    public boolean setBackground(String s) {
        YassRow r = tm.getCommentRow("BACKGROUND:");
        if (r == null) {
            r = new YassRow("#", "BACKGROUND:", s, "", "");
            YassRow v = tm.getCommentRow("COVER:");
            if (v == null) {
                v = tm.getCommentRow("MP3:");
            }
            if (v == null) {
                v = tm.getCommentRow("TITLE:");
            }
            int i = v != null ? tm.getData().indexOf(v) + 1 : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
            return true;
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
                return true;
            }
        }
        return false;
    }

    public boolean setVideo(String s) {
        YassRow r = tm.getCommentRow("VIDEO:");
        if (r == null) {
            r = new YassRow("#", "VIDEO:", s, "", "");
            YassRow v = tm.getCommentRow("BACKGROUND:");
            if (v == null) {
                v = tm.getCommentRow("COVER:");
            }
            if (v == null) {
                v = tm.getCommentRow("MP3:");
            }
            if (v == null) {
                v = tm.getCommentRow("TITLE:");
            }
            int i = v != null ? tm.getData().indexOf(v) + 1 : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
            return true;
        } else {
            String old = r.getHeaderComment();
            if (!s.equals(old)) {
                r.setComment(s);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
                return true;
            }
        }
        return false;
    }

    public boolean hasMessage(String key) {
        if (messages == null) {
            return false;
        }
        return messages.contains(key);
    }

    public boolean setAudioByTag(String tag, String file) {
        if (AUDIO.toString().equals(tag)) {
            return setAudio(file);
        } else if (INSTRUMENTAL.toString().equals(tag)) {
            return setInstrumental(file);
        } else if (VOCALS.toString().equals(tag)) {
            return setVocals(file);
        } else {
            setMP3(file);
            return true;
        }
    }

    public boolean setAudio(String s) {
        YassRow r = tm.getCommentRow(AUDIO.getTagName());
        if (r == null) {
            if (StringUtils.isEmpty(s)) {
                return false;
            }
            r = new YassRow("#", AUDIO.getTagName(), s, "", "");
            YassRow v = tm.getCommentRow(MP3.getTagName());
            int i = v != null ? tm.getData().indexOf(v) + 1 : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
            return true;
        } else {
            return updateOrRemoveRow(s, r);
        }
    }

    private boolean updateOrRemoveRow(String comment, YassRow yassRow) {
        String old = yassRow.getHeaderComment();
        if (old == null) {
            return false;
        }
        boolean changed = false;
        int k = tm.getData().indexOf(yassRow);
        if (StringUtils.isEmpty(comment)) {
            tm.removeRowAt(k);
            changed = true;
        } else if (!comment.equals(old)) {
            yassRow.setComment(comment);
            changed = true;
        }
        if (changed) {
            tm.fireTableRowsUpdated(k, k);
        }
        return changed;
    }

    public String getAudio() {
        YassRow r = tm.getCommentRow(AUDIO);
        if (r == null) {
            return null;
        }
        return r.getHeaderComment();
    }

    public boolean setInstrumental(String s) {
        YassRow r = tm.getCommentRow(INSTRUMENTAL.getTagName());
        if (r == null) {
            if (StringUtils.isEmpty(s)) {
                return false;
            }
            r = new YassRow("#", INSTRUMENTAL.getTagName(), s, "", "");
            YassRow v = tm.getCommentRow(AUDIO.getTagName());
            if (v == null) {
                v = tm.getCommentRow(MP3.getTagName());
            }
            int i = v != null ? tm.getData().indexOf(v) + 1 : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
            return true;
        } else {
            return updateOrRemoveRow(s, r);
        }
    }

    public String getInstrumental() {
        YassRow r = tm.getCommentRow(INSTRUMENTAL);
        if (r == null) {
            return null;
        }
        return r.getHeaderComment();
    }

    public boolean setVocals(String s) {
        YassRow r = tm.getCommentRow(VOCALS.getTagName());
        if (r == null) {
            if (StringUtils.isEmpty(s)) {
                return false;
            }
            r = new YassRow("#", VOCALS.getTagName(), s, "", "");
            YassRow v = tm.getCommentRow(INSTRUMENTAL.getTagName());
            if (v == null) {
                v = tm.getCommentRow(AUDIO.getTagName());
            }
            if (v == null) {
                v = tm.getCommentRow(MP3.getTagName());
            }
            int i = v != null ? tm.getData().indexOf(v) + 1 : 0;
            tm.getData().insertElementAt(r, i);
            tm.fireTableDataChanged();
            return true;
        } else {
            return updateOrRemoveRow(s, r);
        }
    }


    public String getVocals() {
        YassRow r = tm.getCommentRow(VOCALS);
        if (r == null) {
            return null;
        }
        return r.getHeaderComment();
    }

    public void resetMessages() {
        if (messages == null) {
            messages = new Hashtable<>();
        }
        messages.clear();
    }

    public void addMessage(String key) {
        if (messages == null) {
            messages = new Hashtable<>();
        }
        messages.put(key, Boolean.TRUE);
    }

    public boolean hasMinorPageBreakMessages() {
        if (messages == null || auto == null) {
            return false;
        }
        Enumeration<String> en = messages.keys();
        while (en.hasMoreElements()) {
            String key = en.nextElement();
            if (YassAutoCorrect.isAutoCorrectionMinorPageBreak(key)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPageBreakMessages() {
        if (messages == null || auto == null) {
            return false;
        }
        Enumeration<String> en = messages.keys();
        while (en.hasMoreElements()) {
            String key = en.nextElement();
            if (YassAutoCorrect.isAutoCorrectionPageBreak(key)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasTransposedMessages() {
        if (messages == null || auto == null) {
            return false;
        }
        Enumeration<String> en = messages.keys();
        while (en.hasMoreElements()) {
            String key = en.nextElement();
            if (key.equals(YassRow.TRANSPOSED_NOTES)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasFileNameMessages() {
        if (messages == null || auto == null) {
            return false;
        }
        Enumeration<String> en = messages.keys();
        while (en.hasMoreElements()) {
            String key = en.nextElement();
            if (YassAutoCorrect.isAutoCorrectionFileName(key)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasSpacingMessages() {
        if (messages == null || auto == null) {
            return false;
        }
        Enumeration<String> en = messages.keys();
        while (en.hasMoreElements()) {
            String key = en.nextElement();
            if (YassAutoCorrect.isAutoCorrectionSpacing(key)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasUnhandledError() {
        if (messages == null || auto == null) {
            return false;
        }
        Enumeration<String> en = messages.keys();
        while (en.hasMoreElements()) {
            String key = en.nextElement();
            if (YassAutoCorrect.isUnhandledError(key)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasTagsMessages() {
        if (messages == null || auto == null) {
            return false;
        }
        Enumeration<String> en = messages.keys();
        while (en.hasMoreElements()) {
            String key = en.nextElement();
            if (YassAutoCorrect.isAutoCorrectionTags(key)) {
                return true;
            }
        }
        return false;
    }

    public YassRow getCommentRow(String s) {
        return tm.getCommentRow(s);
    }

    public synchronized boolean loadTable(YassTable t, boolean withDuet) {
        if (t == null)
            return false;
        dir = t.dir;
        txtFilename = t.txtFilename;
        isRelative = false;
        encoding = t.getEncoding();
        relativePageBreak = 0;
        if (withDuet) {
            duetTrack = t.duetTrack;
            duetTrackCount = t.duetTrackCount;
            duetTrackName = t.duetTrackName;
        } else {
            duetTrack = -1;
            duetTrackCount = -1;
            duetTrackName = null;
            Arrays.fill(duetSingerNames, null);
        }
        getModelData().clear();
        setPreventRelativeToAbsoluteConversion(t.isPreventRelativeToAbsoluteConversion());
        for (YassRow r : t.getModelData()) {
            addRow(r.toString());
        }
        languageUtils = new YassLanguageUtils();
        if (StringUtils.isEmpty(getLanguage())) {
            detectAndSetMissingLanguage();
        }
        hyphenator = new YassHyphenator("DE|EN|ES|FR|IT|PL|PT|RU|SV|TR|ZH");
        hyphenator.setLanguage(languageUtils.determineLanguageCode(getLanguage()));
        hyphenator.setYassProperties(prop);
        yassUtils = new YassUtils();
        yassUtils.setHyphenator(hyphenator);
        yassUtils.setSpacingAfter(prop.isUncommonSpacingAfter());
        return true;
    }

    private void detectAndSetMissingLanguage() {
        String language = languageUtils.detectLanguage(getText());
        String msg = I18.get("tool_correct_tag_language");
        String input = JOptionPane.showInputDialog(actions.getTab(), msg, language);
        if (input == null) {
            return;
        }
        setLanguage(input);
        setSaved(false);
    }

    static int ns = 0, utf = 0, win = 0;
    static boolean debugEncoding = false;

    public synchronized boolean loadFile(String filename) {
        File f = new File(filename);
        if (!f.exists())
            return false;
        if (f.length() > 1024 * 1024)
            return false;
        dir = f.getAbsolutePath();
        int isep = dir.lastIndexOf(File.separator);
        if (isep <= 0)
            return false;
        txtFilename = dir.substring(isep + 1);
        dir = dir.substring(0, isep);

        // saruta, Jan 2019: better UTF-8 detection method
        // String detectedEncoding = detectUTF8(new File(filename)) ? "UTF-8" : null;
        String detectedEncoding = fileUtils.detectEncoding(new File(filename));
        if (debugEncoding) {
            if (detectedEncoding == null)
                ns++;
            else if (detectedEncoding.equals(Constants.CHARSET_UTF_8))
                utf++;
            else if (detectedEncoding.equals(Constants.CHARSET_WINDOWS_1252))
                win++;
            else
                LOGGER.info("enc = " + detectedEncoding);
            LOGGER.info("ns=" + ns + " utf=" + utf + " win=" + win);
        }
        isRelative = false;
        // saruta, Jan 2019: better UTF-8 detection method
        // encoding = null;
        encoding = Objects.equals(detectedEncoding, Constants.CHARSET_UTF_8) ? "UTF-8" : null;
        relativePageBreak = 0;
        UnicodeReader r = null;
        BufferedReader inputStream = null;
        FileInputStream fis = null;
        boolean success = false;
        try {
            r = new UnicodeReader(fis = new FileInputStream(filename), detectedEncoding);
            inputStream = new BufferedReader(r);
            // BufferedReader inputStream = new BufferedReader(new FileReader(filename));
            String l;
            while ((l = inputStream.readLine()) != null) {
                if (!addRow(l)) {
                    throw new IOException("Invalid data");
                }
            }
            if (isRelative()) {
                // addRow has already done some conversions to absolute mode. We have to set a flag, that this is not done
                // a second time
                setPreventRelativeToAbsoluteConversion(true);
            }
            // saruta, Jan 2019: better UTF-8 detection method
            // encoding = r.getEncoding();
            success = true;
        } catch (Exception e) {
            LOGGER.log(Level.INFO, e.getMessage(), e);
            success = false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
            if (r != null) {
                try {
                    r.close();
                } catch (Exception ignored) {
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception ignored) {
                }
            }
        }

        if (tm.getCommentRow("TITLE:") == null) {
            // LOGGER.info("ERROR: No title tag in - "+filename);
            success = false;
        }
        if (tm.getCommentRow("ARTIST:") == null) {
            // LOGGER.info("ERROR: No artist tag - "+filename);
            success = false;
        }

        isLoading = true;
        tm.fireTableDataChanged();
        isLoading = false;
        return success;
    }

    public synchronized boolean setText(String s) {
        if (s == null) {
            return false;
        }
        if (s.length() > 1024 * 1024) {
            return false;
        }

        isRelative = false;
        encoding = null;
        relativePageBreak = 0;
        try {
            BufferedReader inputStream = new BufferedReader(new StringReader(s));
            String l;
            while ((l = inputStream.readLine()) != null) {
                if (!addRow(l)) {
                    throw new IOException("Invalid data");
                }
            }
            inputStream.close();
        } catch (Exception e) {
            LOGGER.log(Level.INFO, e.getMessage(), e);
            return false;
        }

        if (tm.getCommentRow("TITLE:") == null) {
            // LOGGER.info("ERROR: No title tag in - "+filename);
            return false;
        }
        if (tm.getCommentRow("ARTIST:") == null) {
            // LOGGER.info("ERROR: No artist tag - "+filename);
            return false;
        }

        tm.fireTableDataChanged();
        if (actions != null) {
            actions.checkData(this, true, true);
        }
        return true;
    }

    public boolean storeFile(String filename) {
        // LOGGER.info("Storing "+tm.getCommentRow("ARTIST:").getComment()+" - "+tm.getCommentRow("TITLE:").getComment());

        int relPageBreak = 0;
        boolean isRel = isRelative() && !prop.isUnityOrNewer();

        BufferedWriter bw = null;
        OutputStreamWriter osw = null;
        FileOutputStream fos = null;
        PrintWriter outputStream = null;

        if (prop == null) {
            StringWriter errors = new StringWriter();
            new Exception().printStackTrace(new PrintWriter(errors));
            String msg = "Cannot store file: properties not initialized. Please send me the following stacktrace:\n"
                    + errors;
            LOGGER.info(msg);
            JTextArea text = new JTextArea(msg);
            text.setOpaque(false);
            JOptionPane.showMessageDialog(null, text);
            return false;
        }
        boolean isUnityFormat = prop.getUsFormatVersion().getNumericVersion() >= 1d;
        String p = prop.getProperty("utf8-without-bom");
        boolean utf8WithoutBom = isUnityFormat || (p != null && p.equals("true"));

        p = prop.getProperty("utf8-always");
        boolean utf8Always = isUnityFormat || (p != null && p.equals("true"));

        boolean success = false;
        boolean removeRelative = isRelative() && prop.isUnityOrNewer();
        try {
            File f = new File(filename);
            if (f.exists()) {
                // todo: backup
                f.delete();
            }

            // saruta, Jan 2019: utf8-->UTF-8
            if ((encoding != null && encoding.equals("UTF-8")) || utf8Always) {
                fos = new FileOutputStream(f, false);
                if (f.length() < 1 && !utf8WithoutBom) {
                    final byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB,
                            (byte) 0xBF};
                    fos.write(bom);
                }
                osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                bw = new BufferedWriter(osw);
                outputStream = new PrintWriter(bw);
                encoding = "UTF-8";
            } else {
                outputStream = new PrintWriter(new FileWriter(filename));
            }
            // If this is an auto-save, don't deal with Tag-Versioning
            if (!filename.endsWith(".bak")) {
                setVersion();
                handleDeprecations();
                handleAudio();
            }
            int rows = tm.getRowCount();
            String s;
            int lastPageBreak = 0;
            for (int i = 0; i < rows; i++) {
                YassRow r = tm.getRowAt(i);
                if (isRel) {
                    if (r.isPageBreak() && i + 1 < rows) {
                        int currentBeat = r.getBeatInt();
                        r.setBeat(currentBeat);
                        r.setSecondBeat(currentBeat);
                    }
                    if (r.isNote()) {
                        s = r.toString(relPageBreak);
                    } else if (r.isPageBreak()) {
                        relPageBreak = r.getBeatInt();
                        s = r.toString(lastPageBreak);
                        lastPageBreak = relPageBreak;
                    } else {
                        s = r.toString();
                    }
                } else {
                    s = r.toString();
                }

                s = s.replace(YassRow.SPACE, ' ');
                if (s.equals("E")) {
                    outputStream.print(s);
                } else {
                    outputStream.println(s);
                }
                // LOGGER.info(tm.getRowAt(i).toString());
            }
            success = true;
            setPreventRelativeToAbsoluteConversion(false);
        } catch (Exception e) {
            if (sheet != null) {
                sheet.setMessage(I18.get("sheet_msg_write_error"));
            }
            LOGGER.log(Level.INFO, "Write Error: " + e.getMessage(), e);
            success = false;
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (bw != null) {
                    bw.close();
                }
                if (osw != null) {
                    osw.close();
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception ex) {
                LOGGER.log(Level.INFO, ex.getMessage(), ex);
            }
        }
        if (removeRelative) {
            removeRelative();
        }
        YassTable verify = new YassTable();
        verify.init(prop);
        verify.loadFile(filename);
        if (!equalsData(verify)) {
            LOGGER.info("###############################################");
            LOGGER.info("Write Error: Written data could not be verified.");
            LOGGER.info("File: " + filename);
            LOGGER.info("Encoding: " + encoding);
            LOGGER.info("###############################################");
            JOptionPane.showMessageDialog(null,
                                          "<html>Write Error: Written data could not be verified.<br>File: "
                                                  + filename, "Error", JOptionPane.ERROR_MESSAGE);
        }
        if (prop.getBooleanProperty("usdbsyncer-always-pin")) {
            pinUsdbSyncer();
        }
        return success;
    }

    private void pinUsdbSyncer() {
        if (usdbSyncerMetaFilePath != null && usdbSyncerMetaFile != null && !usdbSyncerMetaFile.isPinned()) {
            usdbSyncerMetaFile.setPinned(true);
            Gson json = new GsonBuilder()
                    .disableHtmlEscaping()
                    .create();
            try (Writer writer = new FileWriter(usdbSyncerMetaFilePath)) {
                json.toJson(usdbSyncerMetaFile, writer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    void loadUsdbSyncerMetaFile(String filename) {
        UsdbSyncerMetaFileLoader loader = new UsdbSyncerMetaFileLoader(filename);
        usdbSyncerMetaFile = loader.getMetaFile();
        usdbSyncerMetaFilePath = loader.getMetaFilePath();
    }

    private void handleAudio() {
        if (!prop.isShinyOrNewer()) {
            return;
        }
        YassRow mp3 = tm.getCommentRow(MP3);
        boolean isFilled = false;
        if (sheet != null && sheet.getSongHeader() != null) {
            isFilled = setAudioToTableAndIsMp3Filled();
        }
        YassRow audio = tm.getCommentRow(AUDIO);
        if (audio == null) {
            int row = findRowByComment(MP3.getTagName());
            if (row >= 0) {
                tm.insertRowAt("#", AUDIO.getTagName(), mp3.getHeaderComment(), "", "", row + 1);
            }
        } else {
            if (!isFilled && mp3 != null) {
                mp3.setComment(audio.getHeaderComment());
            }
        }
    }

    private boolean setAudioToTableAndIsMp3Filled() {
        SongHeader songHeader = sheet.getSongHeader();
        JTextField mp3 = songHeader.getTextFieldByName("mp3");
        setAudioByTag(songHeader.getSelectedAudio(), mp3.getText());
        return StringUtils.isNotEmpty(mp3.getText());
    }

    private void handleDeprecations() {
        if (!prop.isUnityOrNewer()) {
            return;
        }
        int rows = tm.getRowCount();
        List<Integer> toBeRemoved = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            YassRow currentRow = tm.getRowAt(i);
            if (!currentRow.isComment()) {
                break;
            }
            UltrastarHeaderTag deprecationTag = UltrastarHeaderTag.getDeprecation(currentRow.getHeaderCommentTag(),
                                                                                  prop.getUsFormatVersion());
            if (deprecationTag == null) {
                toBeRemoved.add(i);
            } else if (!deprecationTag.getTagName().equals(currentRow.getHeaderCommentTag())) {
                YassRow replacementRow = tm.getCommentRow(deprecationTag);
                if (replacementRow == null) {
                    currentRow.setBeat(deprecationTag.getTagName());
                } else {
                    if (!currentRow.getHeaderComment().equals(replacementRow.getHeaderComment())) {
                        // If current tag is deprecated and replaced by a new tag, we are trying to find
                        // out, if the new tag is already used and differs from the tag to be replaced
                        // if so, we are appending the comment.
                        replacementRow.setComment(replacementRow.getHeaderComment() + " / " + currentRow.getHeaderComment());
                    }
                    toBeRemoved.add(i);
                }
            }
        }
        toBeRemoved.sort(Collections.reverseOrder());
        for (Integer remove : toBeRemoved) {
            tm.removeRowAt(remove);
        }
    }

    private void removeRelative() {
        for (YassRow row : tm.getData()) {
            if (row.getHeaderCommentTag().equals(RELATIVE.getTagName())) {
                tm.getData().remove(row);
                break;
            }
        }
    }

    public void resetUndo() {
        undos.removeAllElements();
        undoPos = -1;
        redoMax = 0;
        if (actions != null) actions.updateActions();
    }

    public YassUndoElement addUndo() {
        if (preventUndo)
            return null;

        if (undoPos == MAX_UNDO - 1) {
            undos.remove(0);
        } else {
            undoPos++;
        }

        Vector<YassRow> c = (Vector<YassRow>) tm.getData().clone();
        int n = c.size();
        for (int i = 0; i < n; i++) {
            c.setElementAt(c.elementAt(i).clone(), i);
        }

        n = undos.size();
        while (n > undoPos) {
            undos.remove(n - 1);
            n--;
        }
        YassUndoElement ue;
        if (sheet != null) {
            ue = new YassUndoElement(c, getSelectedRows(), sheet.getViewPosition(), sheet.getBeatSize(), bpm, gap,
                                     start, end, vgap, isRelative, saved, duetTrack, duetTrackName, duetTrackCount,
                                     duetSingerNames);
        } else {
            ue = new YassUndoElement(c, getSelectedRows(), new Point(), 0, bpm, gap, start, end, vgap, isRelative,
                                     saved, duetTrack, duetTrackName, duetTrackCount, duetSingerNames);
        }
        undos.addElement(ue);

        redoMax = 0;
        if (actions != null) actions.updateActions();
        return ue;
    }

    public boolean canUndo() {
        return undoPos > 0;
    }

    public boolean canRedo() {
        return redoMax > 0;
    }

    public void removeLastUndo() {
        if (undoPos < 1) {
            return;
        }
        undos.remove(undoPos - 1);
        undoPos--;
    }

    public YassUndoElement currentUndo() {
        return undos.elementAt(undoPos);
    }

    public void redoRows() {
        if (redoMax < 1) {
            return;
        }
        redoMax--;
        undoPos++;

        YassUndoElement undoElem = undos.elementAt(undoPos);

        Vector<YassRow> c = (Vector<YassRow>) undoElem.data.clone();
        int n = c.size();
        for (int i = 0; i < n; i++) {
            c.setElementAt(c.elementAt(i).clone(), i);
        }

        boolean oldUndo = preventUndo;
        preventUndo = true;
        bpm = undoElem.bpm;
        gap = undoElem.gap;
        start = undoElem.start;
        end = undoElem.end;
        vgap = undoElem.vgap;
        isRelative = undoElem.isRelative;
        saved = undoElem.isSaved;
        duetTrack = undoElem.duetTrack;
        duetTrackCount = undoElem.duetTrackCount;
        duetTrackName = undoElem.duetTrackName;
        duetSingerNames = Arrays.copyOf(undoElem.duetSingerNames, undoElem.duetSingerNames.length);
        tm.setData(c);
        tm.fireTableDataChanged();
        preventUndo = oldUndo;

        ListSelectionModel sel = getSelectionModel();
        sel.setValueIsAdjusting(true);
        sel.clearSelection();
        Rectangle rr = new Rectangle();
        for (int i = 0; i < undoElem.selectedRows.length; i++) {
            int k = undoElem.selectedRows[i];
            rr.add(getCellRect(k, 0, true));
            sel.addSelectionInterval(k, k);
        }
        sel.setValueIsAdjusting(false);

        if (actions != null) actions.updateActions();
        if (sheet != null) {
            sheet.init();
            sheet.update();
            sheet.setBeatSize(undoElem.sheetBeatSize);
            sheet.setViewPosition(undoElem.sheetViewPosition);
            sheet.repaint();
        }
    }

    public void undoRows() {
        int n = undos.size();
        if (n < 1 || undoPos > n - 1 || undoPos <= 0)
            return;
        undoPos--;
        redoMax++;

        YassUndoElement undoElem = undos.elementAt(undoPos);
        YassUndoElement nextUndoElem = undos.elementAt(undoPos + 1);

        Vector<YassRow> c = (Vector<YassRow>) undoElem.data.clone();
        n = c.size();
        for (int i = 0; i < n; i++) {
            c.setElementAt(c.elementAt(i).clone(), i);
        }

        boolean oldUndo = preventUndo;
        preventUndo = true;
        bpm = undoElem.bpm;
        gap = undoElem.gap;
        start = undoElem.start;
        end = undoElem.end;
        vgap = undoElem.vgap;
        isRelative = undoElem.isRelative;
        saved = undoElem.isSaved;
        duetTrack = undoElem.duetTrack;
        duetTrackCount = undoElem.duetTrackCount;
        duetTrackName = undoElem.duetTrackName;
        duetSingerNames = Arrays.copyOf(undoElem.duetSingerNames, undoElem.duetSingerNames.length);
        tm.setData(c);
        tm.fireTableDataChanged();
        preventUndo = oldUndo;

        ListSelectionModel sel = getSelectionModel();
        sel.setValueIsAdjusting(true);
        sel.clearSelection();
        Rectangle rr = new Rectangle();
        for (int i = 0; i < nextUndoElem.selectedRows.length; i++) {
            int k = nextUndoElem.selectedRows[i];
            rr.add(getCellRect(k, 0, true));
            sel.addSelectionInterval(k, k);
        }
        sel.setValueIsAdjusting(false);

        if (actions != null) actions.updateActions();
        if (sheet != null) {
            sheet.init();
            sheet.update();
            sheet.setBeatSize(nextUndoElem.sheetBeatSize);
            sheet.setViewPosition(nextUndoElem.sheetViewPosition);
            sheet.repaint();
        }
    }

    public synchronized boolean addRow(String s) {
        // trim empty lines
        if (s == null || s.trim().length() < 1 || s.trim().equals("#")) {
            tm.addRow("#", "", "", "", "", YassRow.EMPTY_LINE);
            return true;
        }
        int n = s.length();

        // convert tag-style track number to "P space bitmask" pattern
        if (s.equals("#P1") || s.equals("#P2") || s.equals("#P1:") || s.equals("#P2:")) {
            s = "P " + s.charAt(2);
        }
        // trim text after "E"
        if (s.charAt(0) == 'E') {
            s = s.substring(1).trim();
            if (s.length() > 0) {
                tm.addRow("E", " " + s, "", "", "", YassRow.COMMENT_AFTER_END, s);
            } else {
                tm.addRow("E", "", "", "", "");
            }
            return true;
        }
        if (s.charAt(0) == '#') {
            int i = s.indexOf(':');
            if (i >= 0 && i + 1 < s.length()) {
                String tag = s.substring(1, i + 1);
                String ss = s;
                s = s.substring(i + 1).trim();
                if (tag.equals("MP3:")) {
                    mp3 = s;
                } else if (tag.equals("BPM:")) {
                    // bpm or gap are set to 0 for invalid input
                    bpm = Double.parseDouble(s.replace(',', '.'));
                    DecimalFormat decimalFormat = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.US));
                    s = decimalFormat.format(bpm);
                } else if (tag.equals("GAP:")) {
                    gap = Double.parseDouble(s.replace(',', '.'));
                } else if (tag.equals("START:")) {
                    start = Double.parseDouble(s.replace(',', '.'));
                } else if (tag.equals("END:")) {
                    end = Double.parseDouble(s.replace(',', '.'));
                } else if (tag.equals("VIDEOGAP:")) {
                    vgap = Double.parseDouble(s.replace(',', '.'));
                } else if (tag.equals(AUDIO.getTagName())) {
                    audio = s;
                } else if (tag.equals(INSTRUMENTAL.getTagName())) {
                    instrumental = s;
                } else if (tag.equals(VOCALS.getTagName())) {
                    vocals = s;
                } else if (tag.equals("RELATIVE:")) {
                    isRelative = s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("true");
                } else if (tag.startsWith("DUETSINGERP") || (tag.startsWith("P") && NumberUtils.isDigits(
                        tag.substring(1, 2)))) {
                    try {
                        int pIndex = tag.indexOf("P") + 1;
                        int p = Integer.parseInt(String.valueOf(tag.charAt(pIndex))) - 1; // P1=[0], P2=[1], ...
                        maxP = Math.max(maxP, p + 1);
                        if (duetSingerNames[p] != null) { // duplicate
                            tm.addRow("#", tag, s, "", "", YassRow.INVALID_LINE);
                            return true;
                        }
                        duetSingerNames[p] = s;
                    } catch (Exception e) {
                        tm.addRow("#", tag, s, "", "", YassRow.INVALID_LINE);
                        return true;
                    }
                } else if (tag.equals(TAGS.getTagName())) {
                    tags = Arrays.stream(s.split(",")).map(YassSong::normalizeTag).collect(Collectors.toSet());
                }
                tm.addRow("#", tag, s, "", "");
                return true;
            }
            // non-tag comment
            tm.addRow("#", s.substring(1).trim(), "", "", "", YassRow.INVALID_LINE);
            return true;
        }

        // insert space when missing
        char c0 = s.charAt(0);
        if (n > 1 && c0 == '-' || c0 == ':' || c0 == '*' || c0 == 'G' || c0 == 'R' || c0 == 'F'
                || c0 == 'f') {
            if (s.charAt(1) != ' ') {
                s = c0 + " " + s.substring(1);
                n++;
            }
        }

        if (s.charAt(0) == '-' && n > 1) {
            // skip spaces
            String time = s.substring(1).trim();
            int i = time.indexOf(' ');
            String time2 = "";
            if (i > 0) {
                time2 = time.substring(i + 1).trim();
                time = time.substring(0, i).trim();
                if (isRelative && !preventRelativeToAbsoluteConversion) {
                    int ti = Integer.parseInt(time);
                    int ti2 = (time2.length() > 0) ? Integer.parseInt(time2) : ti;
                    time = String.valueOf(ti + relativePageBreak);
                    time2 = "";
                    relativePageBreak += ti2;
                }
            } else if (isRelative && !preventRelativeToAbsoluteConversion) {
                relativePageBreak += Integer.parseInt(time);
            }
            tm.addRow("-", time, time2, "", "");
            return true;
        }

        if (handleDuets(s, n)) return true;

        // convert invalid lines into comments
        if (s.charAt(0) != ':' && s.charAt(0) != '*' && s.charAt(0) != 'G' && s.charAt(0) != 'R' && s.charAt(
                0) != 'F' && s.charAt(0) != 'P') {
            tm.addRow("#", s, "", "", "", YassRow.LINE_CUT);
            return true;
        }

        int i = s.indexOf(' ');
        if (i < 0) {
            tm.addRow(s.charAt(0), "", "", "", "", YassRow.LINE_CUT);
            return true;
        }

        // skip spaces
        while (i < n && s.charAt(i) == ' ') {
            i++;
        }
        if (i == n) {
            tm.addRow(s.charAt(0), "", "", "", "", YassRow.LINE_CUT);
            return true;
        }
        while (i < n && s.charAt(i) != ' ') {
            i++;
        }
        if (i == n) {
            tm.addRow(s.charAt(0), "", "", "", "", YassRow.LINE_CUT);
            return true;
        }
        String time = s.substring(1, i).trim();
        try {
            Integer.parseInt(time);
        } catch (Exception e) {
            tm.addRow(s.charAt(0), "", "", "", "", YassRow.LINE_CUT);
            return true;
        }

        while (i < n && s.charAt(i) == ' ') {
            i++;
        }
        int j = s.indexOf(' ', i);
        if (i == n || j < 0) {
            tm.addRow(s.charAt(0), time, "", "", "", YassRow.LINE_CUT);
            return true;
        }
        String length = s.substring(i, j).trim();
        try {
            Integer.parseInt(length);
        } catch (Exception e) {
            tm.addRow(s.charAt(0), time, "", "", "", YassRow.LINE_CUT);
            return true;
        }

        while (j < n && s.charAt(j) == ' ') {
            j++;
        }
        int k = s.indexOf(' ', j);
        if (j == n || k < 0) {
            tm.addRow(s.charAt(0), time, length, "", "", YassRow.LINE_CUT);
            return true;
        }
        String height = s.substring(j, k).trim();
        try {
            Integer.parseInt(height);
        } catch (Exception e) {
            tm.addRow(s.charAt(0), time, length, "", "", YassRow.LINE_CUT);
            return true;
        }

        if (k + 1 > n - 1) {
            tm.addRow(s.charAt(0), time, length, height, "", YassRow.LINE_CUT);
            return true;
        }
        String txt = s.substring(k + 1);

        if (isRelative && !preventRelativeToAbsoluteConversion) {
            int timeInt = Integer.parseInt(time);
            timeInt += relativePageBreak;
            time = String.valueOf(timeInt);
        }
        txt = txt.replace(' ', YassRow.SPACE);
        tm.addRow(s.charAt(0), time, length, height, txt);
        return true;
    }

    private boolean handleDuets(String line, int numberOfPlayers) {
        if (line.charAt(0) == 'P' && numberOfPlayers > 1) {
            String pnum = line.substring(1).trim();
            try {
                int currentPlayer = Integer.parseInt(pnum);

                if (prop.getUsFormatVersion().getNumericVersion() < 1d && prop.isLegacyDuet()) {
                    tm.addRow("P", pnum, "", "", "", ""); // add space (not stored, see YassRow.toString)
                } else {
                    tm.addRow("P" + pnum, "", "", "", "", "");
                }
                if (currentPlayer > 0) {
                    if (currentPlayer == 3) currentPlayer = 0; // P0 = P3 = both
                    maxP = Math.max(maxP, currentPlayer);
                }
                return true;
            } catch (Exception e) {
                tm.addRow("#", line, "", "", "", YassRow.LINE_CUT);
                return true;
            }
        }
        return false;
    }

    public YassRow getRowAt(int row) {
        return tm.getRowAt(row);
    }

    public void shiftBeat(int b) {
        int row;
        int[] rows = getSelectedRows();
        if (rows == null || rows.length < 1) {
            return;
        }
        int minb = Integer.MAX_VALUE;
        int maxb = -1;
        for (int row1 : rows) {
            row = row1;
            YassRow r = getRowAt(row);
            if (r.isNote()) {
                int db = r.getBeatInt() + b;
                r.setBeat(db);
                minb = Math.min(db, minb);
                maxb = Math.max(db + r.getLengthInt(), maxb);
            } else if (r.isPageBreak()) {
                int db = r.getBeatInt() + b;
                r.setBeat(db);
                if (r.hasSecondBeat()) {
                    db = r.getSecondBeatInt() + b;
                    r.setSecondBeat(db);
                }
                minb = Math.min(db, minb);
                maxb = Math.max(db, maxb);
            }
        }

        if (sheet != null) {
            boolean scrolled = false;
            if (minb < Integer.MAX_VALUE) {
                int minx = sheet.beatToTimeline(minb);
                if (minx < sheet.getLeftX()) {
                    sheet.setLeftX(minx);
                    scrolled = true;
                }
            }
            if (!scrolled) {
                // int minx = sheet.beatToTimeline(minb);
                int maxx = sheet.beatToTimeline(maxb);
                int w = sheet.getClipBounds().width;
                if (maxx > sheet.getViewPosition().x + w) {
                    sheet.setViewPosition(new Point(maxx - w, 0));
                }
            }
        }
        if (zoomMode == ZOOM_ONE) {
            zoomPage();
        }
        updatePlayerPosition();

        preventLyricsUpdate(true);
        tm.fireTableRowsUpdated(getSelectionModel().getMinSelectionIndex(),
                                getSelectionModel().getMaxSelectionIndex());
        preventLyricsUpdate(false);
    }

    public void shiftRemainder(int b) {
        int row = getSelectionModel().getMinSelectionIndex();
        if (row < 0 && sheet != null) {
            row = sheet.nextElement();
        }
        if (row < 0) {
            return;
        }

        int n = getRowCount();
        int minb = Integer.MAX_VALUE;
        for (int i = row; i < n; i++) {
            YassRow r = getRowAt(i);
            if (r.isNote()) {
                int db = r.getBeatInt() + b;
                r.setBeat(db);
                minb = Math.min(db, minb);
            } else if (r.isPageBreak()) {
                int db = r.getBeatInt() + b;
                r.setBeat(db);
                if (r.hasSecondBeat()) {
                    db = r.getSecondBeatInt() + b;
                    r.setSecondBeat(db);
                }
                minb = Math.min(db, minb);
            }
        }

        if (sheet != null && minb < Integer.MAX_VALUE) {
            int minx = sheet.beatToTimeline(minb);
            if (minx < sheet.getLeftX()) {
                sheet.setLeftX(minx);
            }
        }
        if (zoomMode == ZOOM_ONE) {
            zoomPage();
        }
        updatePlayerPosition();

        preventLyricsUpdate(true);
        tm.fireTableRowsUpdated(row, n);
        preventLyricsUpdate(false);
    }

    public void shiftLine(int b) {
        int row = getSelectionModel().getMinSelectionIndex();
        if (row < 0 && sheet != null) {
            row = sheet.nextElement();
        }
        if (row < 0) {
            return;
        }

        int pb = -1;
        int n = getRowCount();
        int i = row;
        YassRow rpb = null;
        YassRow r = getRowAt(i);
        while (r.isNote()) {
            r = getRowAt(--i);
        }
        if (r.isPageBreak()) {
            rpb = r;
            pb = i;
        }
        i++;

        int minb = Integer.MAX_VALUE;

        int maxb = -1;
        while (i < n) {
            r = getRowAt(i);
            if (r.isNote()) {
                int db = r.getBeatInt() + b;
                r.setBeat(db);
                minb = Math.min(db, minb);
                maxb = Math.max(db + r.getLengthInt(), maxb);

            } else if (r.isPageBreak()) {
                break;
            }
            i++;
        }

        if (rpb != null && pb > 0 && pb < n - 1) {
            int[] comm = new int[2];
            YassRow prev = getRowAt(pb - 1);
            YassRow next = getRowAt(pb + 1);
            if (prev.isNote() && next.isNote()) {
                comm[0] = prev.getBeatInt() + prev.getLengthInt();
                comm[1] = getRowAt(pb + 1).getBeatInt();
                YassAutoCorrect.getCommonPageBreak(comm, getBPM(), null);
                rpb.setBeat(comm[0]);
                rpb.setSecondBeat(comm[1]);
            }
        }

        if (sheet != null) {
            boolean scrolled = false;
            if (minb < Integer.MAX_VALUE) {
                int minx = sheet.beatToTimeline(minb);
                if (minx < sheet.getLeftX()) {
                    sheet.setLeftX(minx);
                    scrolled = true;
                }
            }
            if (!scrolled) {
                int maxx = sheet.beatToTimeline(maxb);
                int w = sheet.getClipBounds().width;
                if (maxx > sheet.getViewPosition().x + w) {
                    sheet.setViewPosition(new Point(maxx - w, 0));
                }
            }
        }
        updatePlayerPosition();

        preventLyricsUpdate(true);
        tm.fireTableRowsUpdated(row, n);
        preventLyricsUpdate(false);
    }

    public void selectLine() {
        int row = getSelectionModel().getMinSelectionIndex();
        if (row < 0 && sheet != null) {
            row = sheet.nextElement();
            if (row < 0) {
                return;
            }
            YassRow r = getRowAt(row);
            if (r == null) {
                return;
            }
            if (!r.isNote()) {
                row--;
            }
        }
        if (row < 0) {
            return;
        }

        int n = getRowCount();
        int i = row;
        YassRow r = getRowAt(i);
        if (!r.isNote()) {
            return;
        }

        while (r.isNote() && i > 0) {
            r = getRowAt(--i);
        }

        if (!r.isNote()) {
            i++;
        }

        int j = row;
        while (j < n) {
            r = getRowAt(j);
            if (r.isPageBreak()) {
                break;
            }
            j++;
        }
        clearSelection();
        addRowSelectionInterval(i, j - 1);
        updatePlayerPosition();
    }

    public void selectAll() {
        int n = getRowCount();
        int i = 0;
        YassRow r = getRowAt(i);
        while (!r.isNoteOrPageBreak() && i < n) {
            r = getRowAt(++i);
        }

        if (!r.isNoteOrPageBreak()) {
            return;
        }

        int j = n - 1;
        r = getRowAt(j);
        while (!r.isNoteOrPageBreak() && j > 0) {
            r = getRowAt(--j);
        }

        clearSelection();
        addRowSelectionInterval(i, j);

        updatePlayerPosition();

        if (zoomMode != ZOOM_TIME) {
            sheet.setZoom(i, j, true);
            sheet.scrollRectToVisible(i, j);
        }
        actions.setRelative(false);
        setZoomMode(ZOOM_MULTI);
        addMultiSize(getRowCount());

        if (sheet != null) {
            sheet.repaint();
        }
    }

    public void viewAll() {
        int n = getRowCount();
        int i = 0;
        YassRow r = getRowAt(i);
        while (!r.isNoteOrPageBreak() && i < n) {
            r = getRowAt(++i);
        }

        if (!r.isNoteOrPageBreak()) {
            return;
        }

        int j = n - 1;
        r = getRowAt(j);
        while (!r.isNoteOrPageBreak() && j > 0) {
            r = getRowAt(--j);
        }

        if (zoomMode != ZOOM_TIME) {
            sheet.setZoom(i, j, true);
            sheet.scrollRectToVisible(i, j);
        }
        actions.setRelative(false);
        setZoomMode(ZOOM_MULTI);
        addMultiSize(getRowCount());

        updatePlayerPosition();
        if (sheet != null) {
            sheet.repaint();
        }
    }

    public void shiftHeight(int h) {
        int row;
        int[] rows = getSelectedRows();
        if (rows == null || rows.length < 1) {
            return;
        }
        /*
         * if (rows.length==1) { row = rows[0]; YassRow r = getRowAt(row); if
         * (r.isNote()) { int n = r.getHeightInt() + h; midi.play(n); } }
         */
        for (int row1 : rows) {
            row = row1;
            YassRow r = getRowAt(row);
            if (r.isNote()) {
                r.setHeight(r.getHeightInt() + h);
            }
        }
        if (sheet != null) {
            sheet.init();
            sheet.update();
            // sheet.repaint();
        }
        preventLyricsUpdate(true);
        tm.fireTableRowsUpdated(getSelectionModel().getMinSelectionIndex(),
                                getSelectionModel().getMaxSelectionIndex());
        preventLyricsUpdate(false);
    }

    public void shiftLeftEndian(int d) {
        int row = -1;
        int row2 = -1;
        int[] rows = getSelectedRows();
        if (rows == null || rows.length < 1) {
            return;
        }
        if (rows.length == 1) {
            row = rows[0];
        } else {
            int minb = Integer.MAX_VALUE;
            int dd = d;
            for (int row1 : rows) {
                YassRow r = getRowAt(row1);
                if (!r.isNote()) {
                    continue;
                }
                int k = row1 - 1;
                YassRow r2 = getRowAt(k);
                while (r2.isPageBreak() && (k > 0)) {
                    r2 = getRowAt(--k);
                }
                if (!r2.isNoteOrPageBreak()) {
                    r2 = null;
                }

                d = dd;
                int beat = r.getBeatInt() + d;
                int len = r.getLengthInt();
                int dur = len - d;
                if (dur < 1) {
                    beat -= 1 - dur;
                    dur = 1;
                }
                if (r2 != null) {
                    // prevent dragging beyond left note
                    int beat2 = r2.getBeatInt() + r2.getLengthInt();
                    if (beat < beat2) {
                        beat = beat2;
                        dur = r.getBeatInt() - beat + len;
                    }
                }
                r.setBeat(beat);
                minb = Math.min(beat, minb);
                r.setLength(dur);
            }
            if (sheet != null && minb < Integer.MAX_VALUE) {
                int minx = sheet.beatToTimeline(minb);
                if (minx < sheet.getLeftX()) {
                    sheet.setLeftX(minx);
                }
            }
            if (zoomMode == ZOOM_ONE) {
                zoomPage();
            }
            updatePlayerPosition();

            preventLyricsUpdate(true);
            tm.fireTableRowsUpdated(getSelectionModel().getMinSelectionIndex(),
                                    getSelectionModel().getMaxSelectionIndex());
            preventLyricsUpdate(false);
            return;
        }

        YassRow r = getRowAt(row);

        YassRow r2 = null;
        if (row2 >= 0) {
            r2 = getRowAt(row2);
            if (!r2.isNoteOrPageBreak()) {
                r2 = null;
            }
        }

        int minb = Integer.MAX_VALUE;

        int beat = 0;
        if (r.isNote()) {
            beat = r.getBeatInt() + d;
            int len = r.getLengthInt();
            int dur = len - d;
            if (dur < 1) {
                beat -= 1 - dur;
                dur = 1;
                d = r.getLengthInt() - 1;
            }
            if (r2 != null) {
                int beat2 = r2.getBeatInt();
                if (beat < beat2 + 1) {
                    beat = beat2 + 1;
                    dur = r.getBeatInt() - beat + len;
                }
            }
            r.setBeat(beat);
            minb = Math.min(beat, minb);
            r.setLength(dur);
        } else if (r.isPageBreak()) {
            beat = r.getBeatInt() + d;
            int beat2 = r.getSecondBeatInt() + d;
            if (beat > beat2 - 1) {
                beat = beat2 - 1;
            }
            r.setBeat(beat);
            minb = Math.min(beat, minb);
        }

        if (r2 != null) {
            r = r2;
            if (r.isNote()) {
                r.setLength(Math.max(1, r.getLengthInt() + d));
            } else if (r.isPageBreak()) {
                int beat1 = r.getBeatInt() + d;
                int beat2 = r.getSecondBeatInt() + d;
                if (beat2 < beat1 + 1) {
                    beat2 = beat1 + 1;
                }
                r.setSecondBeat(beat2);
            }
        }

        if (sheet != null && minb < Integer.MAX_VALUE) {
            int minx = sheet.beatToTimeline(minb);
            if (minx < sheet.getLeftX()) {
                sheet.setLeftX(minx);
            }
        }
        if (zoomMode == ZOOM_ONE) {
            zoomPage();
        }
        updatePlayerPosition();

        preventLyricsUpdate(true);
        tm.fireTableRowsUpdated(getSelectionModel().getMinSelectionIndex(),
                                getSelectionModel().getMaxSelectionIndex());
        preventLyricsUpdate(false);
    }

    public void shiftRightEndian(int d) {
        int row = -1;
        int[] rows = getSelectedRows();
        if (rows == null || rows.length < 1) {
            return;
        }
        int n = getRowCount();
        if (rows.length == 1) {
            row = rows[0];
        } else {
            int maxb = -1;
            for (int row1 : rows) {
                YassRow r = getRowAt(row1);
                if (!r.isNote()) {
                    continue;
                }

                int k = row1 + 1;
                YassRow r2 = getRowAt(k);
                while (r2.isPageBreak() && (k < n - 1)) {
                    r2 = getRowAt(++k);
                }
                if (!r2.isNoteOrPageBreak()) {
                    r2 = null;
                }

                int beat1 = r.getBeatInt();
                int len = r.getLengthInt();
                len = Math.max(1, len + d);
                int beat2 = 0;

                if (r2 != null) {
                    // prevent dragging beyond right note
                    beat2 = r2.getBeatInt();
                    if (beat1 + len > beat2) {
                        len = beat2 - beat1;
                    }
                }
                r.setLength(len);
                beat2 = beat1 + len;
                maxb = Math.max(beat2, maxb);
            }
            if (sheet != null) {
                int maxx = sheet.beatToTimeline(maxb);
                int w = sheet.getClipBounds().width;
                if (maxx > sheet.getViewPosition().x + w) {
                    sheet.setViewPosition(new Point(maxx - w, 0));
                }
            }
            if (zoomMode == ZOOM_ONE) {
                zoomPage();
            }
            updatePlayerPosition();

            preventLyricsUpdate(true);
            tm.fireTableRowsUpdated(getSelectionModel().getMinSelectionIndex(),
                                    getSelectionModel().getMaxSelectionIndex());
            preventLyricsUpdate(false);
            return;
        }

        int maxb = -1;
        int beat2 = 0;
        YassRow r = getRowAt(row);
        if (r.isNote()) {
            int beat1 = r.getBeatInt() + d;
            int len = r.getLengthInt();
            len = Math.max(1, len + d);
            r.setLength(len);
            beat2 = beat1 + len;
            maxb = Math.max(beat2, maxb);
        } else if (r.isPageBreak()) {
            int beat1 = r.getBeatInt() + d;
            beat2 = r.getSecondBeatInt() + d;
            if (beat2 < beat1 + 1) {
                beat2 = beat1 + 1;
            }
            r.setSecondBeat(beat2);
            maxb = Math.max(beat2, maxb);
        }
        YassRow next = getRowAt(row + 1);
        if (next.isPageBreak() && r.getBeatInt() + r.getLengthInt() > next.getBeatInt()) {
            next.setBeat(next.getBeatInt() + 1);
        }
        if (sheet != null) {
            int maxx = sheet.beatToTimeline(maxb);
            int w = sheet.getClipBounds().width;
            if (maxx > sheet.getViewPosition().x + w) {
                sheet.setViewPosition(new Point(maxx - w, 0));
            }
        }
        if (zoomMode == ZOOM_ONE) {
            zoomPage();
        }

        preventLyricsUpdate(true);
        tm.fireTableRowsUpdated(getSelectionModel().getMinSelectionIndex(),
                                getSelectionModel().getMaxSelectionIndex());
        preventLyricsUpdate(false);
    }

    public void setType(String s) {
        int row;
        int[] rows = getSelectedRows();
        if (rows == null || rows.length < 1) {
            return;
        }

        boolean changed = false;
        for (int row2 : rows) {
            row = row2;
            YassRow r = getRowAt(row);
            if (r.isNote()) {
                if (!r.getType().equals(s)) {
                    r.setType(s);
                    changed = true;
                }
            }
        }
        if (!changed) {
            for (int row1 : rows) {
                row = row1;
                YassRow r = getRowAt(row);
                if (r.isNote()) {
                    r.setType(":");
                }
            }
        }
        tm.fireTableRowsUpdated(getSelectionModel().getMinSelectionIndex(),
                                getSelectionModel().getMaxSelectionIndex());
    }

    public void gotoPageNumber(int n) {
        int pn = 1;
        int i = 0;
        Enumeration<?> en = tm.getData().elements();
        while (en.hasMoreElements()) {
            YassRow r = (YassRow) en.nextElement();
            if (n == 0 || (r.isNote() && n == pn)) {
                setRowSelectionInterval(i, i);
                updatePlayerPosition();
                zoomPage();
                return;
            }
            if (r.isPageBreak()) {
                pn++;
            }
            i++;
        }

        i = getRowCount();
        if (i < 1) {
            return;
        }
        setRowSelectionInterval(i - 1, i - 1);
        updatePlayerPosition();
        zoomPage();
    }

    public int getPageNumber() {
        int row = getSelectionModel().getMinSelectionIndex();
        if (row < 0) {
            return -1;
        }
        return getPageNumber(row);
    }

    public Enumeration<YassRow> getRows() {
        return tm.getData().elements();
    }

    public List<YassRow> getNoteRows() {
        return tm.getData()
                 .stream()
                 .filter(YassRow::isNote)
                 .collect(Collectors.toList());
    }

    public int getFirstVisiblePageNumber() {
        if (sheet == null) {
            return -1;
        }
        int row = sheet.firstVisibleNote();
        if (row < 0) {
            return -1;
        }
        return getPageNumber(row);
    }

    public int getPageNumber(int row) {
        if (row < 0)
            return 0;
        YassRow r = getRowAt(row);
        if (r == null || r.isComment()) {
            return 0;
        }
        if (r.isEnd()) {
            return getPageCount() + 1;
        }

        int pn = 1;
        int i = 0;
        Enumeration<?> en = tm.getData().elements();
        while (en.hasMoreElements()) {
            r = (YassRow) en.nextElement();
            if (i == row) {
                return pn;
            }
            if (r.isPageBreak()) {
                pn++;
            }
            i++;
        }
        return -1;
    }

    public int getPageCount() {
        int pn = 1;
        Enumeration<?> en = tm.getData().elements();
        while (en.hasMoreElements()) {
            YassRow r = (YassRow) en.nextElement();
            if (r.isPageBreak()) {
                pn++;
            }
        }
        return pn;
    }

    public Vector<YassPage> getPages() {
        Vector<YassPage> pages = new Vector<>();
        YassPage p = null;

        boolean first = true;
        for (YassRow r : tm.getData()) {
            if (r.isNote()) {
                if (first) {
                    p = new YassPage(this);
                    pages.addElement(p);
                    first = false;
                }
                p.addRow(r);
            }
            if (p != null && (r.isPageBreak() || r.isEnd())) {
                p.addRow(r);
                first = true;
            }
        }
        return pages;
    }

    public int getMultiSize() {
        return multiSize;
    }

    public void setMultiSize(int i) {
        multiSize = i;
    }

    public void addMultiSize(int i) {
        multiSize += i;
        if (multiSize < 1) {
            multiSize = 1;
        }
        int n = getPageCount() + 2;
        int k = getPageNumber();
        if (multiSize > n - k + 1) {
            multiSize = n - k + 1;
        }
    }

    public boolean getPreventZoom() {
        return preventZoom;
    }

    public void setPreventZoom(boolean onoff) {
        preventZoom = onoff;
    }

    public void zoomPage() {
        if (preventZoom) {
            return;
        }

        if (sheet == null) {
            return;
        }
        double pos = sheet.fromTimeline(sheet.getPlayerPosition());

        int i = getSelectionModel().getMinSelectionIndex();
        int j = getSelectionModel().getMaxSelectionIndex();
        if (i < 0) {
            i = sheet.nextElement();
        }
        if (i < 0) {
            return;
        }
        if (j < 0) {
            j = i;
        }

        if (zoomMode == ZOOM_TIME) {
            sheet.setZoom(80 * 60 / bpm);
        }

        int n = getRowCount();
        if (i == n - 1) // only end selected => select row before
            i--;
        int[] ij = null;
        if (zoomMode == ZOOM_ONE) {
            ij = enlargeToPages(i, j);
        }
        if (zoomMode == ZOOM_MULTI) {
            ij = enlargeToPages(i, j);
            if (ij == null) {
                return;
            }

            int k = multiSize - 1;
            int span = countSelectedPages();
            if (span > 1) {
                k -= span;
            }

            boolean addEnd = false;

            while (k > 1) {
                ij[1] = enlargeToPageBreak(Math.min(ij[1] + 1, n - 1));
                boolean endReached = getRowAt(ij[1]).isEnd();
                if (endReached && !addEnd) {
                    while (ij[1] > 0 && !getRowAt(ij[1]).isNote()) {
                        ij[1]--;
                    }
                    addEnd = true;
                }
                k--;
            }
        }
        if (zoomMode == ZOOM_TIME) {
            ij = new int[]{i, j};
        }
        if (ij == null) {
            return;
        }

        Rectangle rr = getCellRect(ij[0], 0, true);
        rr.add(getCellRect(ij[1], 4, true));
        scrollRectToVisible(rr);

        if (zoomMode != ZOOM_TIME) {
            sheet.setZoom(ij[0], ij[1], true);
        }
        sheet.setPlayerPosition(sheet.toTimeline(pos));
        if (zoomMode == ZOOM_TIME) {
            sheet.scrollRectToVisible(ij[0], ij[1]);
        }

        actions.revalidateLyricsArea();
        sheet.repaint();
    }

    /**
     * Enlarge current span to full pages.
     *
     * @param i span start
     * @param j span end
     * @return [s, e] where
     * s = note before i that starts a page
     * e = note after j that ends a page
     */
    public int[] enlargeToPages(int i, int j) {
        if (i < 0)
            return null;
        int n = getRowCount();
        if (i >= n)
            return null;
        YassRow r = getRowAt(i);
        if (r.isEnd())
            return new int[]{i, i};
        while (!(r.isNote()) && i > 0)
            r = getRowAt(--i);
        // move back until i-->note
        boolean inHeader = !r.isNote();
        YassRow r2 = getRowAt(j);
        while (r2 != null && !(r2.isNote()) && j < n - 1)
            r2 = getRowAt(++j);
        // // move forward until j-->note
        if (inHeader)
            return new int[]{i, j - 1};
        r = getRowAt(i);
        while (r.isNote() && i > 0)
            r = getRowAt(--i);
        r2 = getRowAt(j);
        while (r2 != null && r2.isNote() && j < n - 1)
            r2 = getRowAt(++j);
        return new int[]{i + 1, j - 1};
    }

    public int enlargeToPageBreak(int j) {
        int n = getRowCount();
        YassRow r2 = getRowAt(j);
        while (j < n - 1 && !r2.isPageBreak() && !r2.isEnd()) {
            j++;
            r2 = getRowAt(j);
        }
        return j;
    }

    public void home() {
        int row = getSelectionModel().getMinSelectionIndex();
        if (row < 0 && sheet != null) {
            row = sheet.nextElement();
        }
        if (row < 0) {
            return;
        }

        int n = getRowCount();
        YassRow r = getRowAt(row);
        while (!r.isNote() && row > 0) {
            r = getRowAt(--row);
        }

        while (r.isNote() && row > 0) {
            r = getRowAt(--row);
        }
        row++;

        if (row < 0 || row > n - 1) {
            return;
        }
        setRowSelectionInterval(row, row);
        Rectangle rr = getCellRect(row, 0, true);
        scrollRectToVisible(rr);
        updatePlayerPosition();
    }

    public void note(int k) {
        int notes = 0;
        int n = getRowCount();
        int row = 0;
        YassRow r = getRowAt(0);
        if (r.isNoteOrPageBreak()) {
            notes++;
        }
        while (notes < k && row < n - 1) {
            r = getRowAt(++row);
            if (r.isNoteOrPageBreak()) {
                notes++;
            }
        }
        setRowSelectionInterval(row, row);
        Rectangle rr = getCellRect(row, 0, true);
        if (row < n - 1) {
            rr.add(getCellRect(row + 1, 0, true));
        }
        if (row > 0) {
            rr.add(getCellRect(row - 1, 0, true));
        }
        scrollRectToVisible(rr);
        updatePlayerPosition();
    }

    public void end() {
        int row = getSelectionModel().getMaxSelectionIndex();
        int n = getRowCount();
        if (row < 0 && sheet != null) {
            row = sheet.nextElement();
        }
        if (row < 0) {
            return;
        }

        YassRow r = getRowAt(row);
        while (!r.isNote() && row > 0) {
            r = getRowAt(--row);
        }

        while (r.isNote() && row < n - 1) {
            r = getRowAt(++row);
        }
        row--;
        if (row < 0 || row > n - 1) {
            return;
        }

        setRowSelectionInterval(row, row);
        updatePlayerPosition();
    }

    public int getFirstNoteRow() {
        int n = getRowCount();
        if (n < 0) {
            return -1;
        }

        int i = 0;
        YassRow r = getRowAt(i);
        while (!r.isNote() && i < n - 1) {
            r = getRowAt(++i);
        }
        if (!r.isNote()) {
            return -1;
        }
        return i;
    }
    public YassRow getFirstNote() {
        int n = getFirstNoteRow();
        if (n < 0) {
            return null;
        }
        return getRowAt(n);
    }

    public void firstNote() {
        int n = getRowCount();
        if (n <= 0) {
            return;
        }

        int i = 0;
        YassRow r = getRowAt(i);
        while (!r.isNote() && i < n - 1) {
            r = getRowAt(++i);
        }
        if (!r.isNote()) {
            return;
        }

        setRowSelectionInterval(i, i);
        updatePlayerPosition();

        Rectangle rr = getCellRect(i, 0, true);
        scrollRectToVisible(rr);
        zoomPage();
    }

    public void lastNote() {
        int n = getRowCount();
        if (n < 0) {
            return;
        }

        int i = n - 1;
        YassRow r = getRowAt(i);
        while (!r.isNote() && i > 0) {
            r = getRowAt(--i);
        }
        if (!r.isNote()) {
            return;
        }

        setRowSelectionInterval(i, i);
        updatePlayerPosition();

        Rectangle rr = getCellRect(i, 0, true);
        scrollRectToVisible(rr);
        zoomPage();

    }

    public YassRow getNoteAtBeat(int beat) {
        YassRow r;
        int i = 0;
        int n = getRowCount();
        while (i < n - 1) {
            r = getRowAt(i++);
            if (r.isNote() && r.getBeatInt() == beat) return r;
        }
        return null;
    }

    public YassRow getNoteEndingAtBeat(int beat) {
        YassRow r;
        int i = 0;
        int n = getRowCount();
        while (i < n - 1) {
            r = getRowAt(i++);
            if (r.isNote() && r.getBeatInt() + r.getLengthInt() == beat) return r;
        }
        return null;
    }

    /**
     * Returns closest note before given beat.
     * If a note exactly hits that beat, it is returned.
     * Stops search at any note that lays after the given beat.
     */
    public int getIndexOfNoteBeforeBeat(int beat) {
        YassRow r;
        int i = 0;
        int iMin = -1;
        int n = getRowCount();
        int min = Integer.MAX_VALUE;
        while (i < n - 1) {
            r = getRowAt(i);
            if (r.isNote()) {
                int b = r.getBeatInt();
                if (b <= beat) {
                    if (beat - b < min) {
                        min = beat - b;
                        iMin = i;
                    }
                } else break;
            }
            i++;
        }
        return iMin;
    }

    /**
     * Returns closest note before given beat.
     * If a note exactly hits that beat, it is returned.
     * Stops search at any note that lays after the given beat.
     */
    public YassRow getNoteBeforeBeat(int beat) {
        YassRow r;
        YassRow rMin = null;
        int i = 0;
        int n = getRowCount();
        int min = Integer.MAX_VALUE;
        while (i < n - 1) {
            r = getRowAt(i);
            if (r.isNote()) {
                int b = r.getBeatInt();
                if (b <= beat) {
                    if (beat - b < min) {
                        min = beat - b;
                        rMin = r;
                    }
                } else break;
            }
            i++;
        }
        return rMin;
    }

    /**
     * Returns closest note end before given beat.
     * If a note end exactly hits that beat, it is returned.
     * Stops search at any note end that lays after the given beat.
     */
    public YassRow getNoteEndingBeforeBeat(int beat) {
        YassRow r;
        YassRow rMin = null;
        int i = 0;
        int n = getRowCount();
        int min = Integer.MAX_VALUE;
        while (i < n - 1) {
            r = getRowAt(i++);
            if (r.isNote()) {
                int b = r.getBeatInt() + r.getLengthInt();
                if (b <= beat) {
                    if (beat - b < min) {
                        min = beat - b;
                        rMin = r;
                    }
                } else break;
            }
        }
        return rMin;
    }

    public void prevBeat() {
        prevBeat(false);
    }

    public void prevBeat(boolean add) {
        int[] rows = getSelectedRows();
        int i = -1;
        int n = getRowCount();
        if (rows == null || rows.length < 1) {
            // if nothing selected
            i = sheet != null ? sheet.nextElement() : -1;
            // take next possible note
            if (i < 0) {
                i = n - 1;
            } else if (!getRowAt(i).isNoteOrPageBreak()) {
                i = 1;
            }
            // or goto start
        } else {
            i = rows[0];
        }

        if (i > 0) {
            if (!add && !getRowAt(i - 1).isNote()) {
                end();
            } else {
                if (add) {
                    if (rows.length > 1) {
                        int j = rows[rows.length - 2];
                        while (!getRowAt(j).isNote()) {
                            j--;
                            if (j < i) {
                                return;
                            }
                        }
                        setRowSelectionInterval(i, j);
                    }
                } else {
                    setRowSelectionInterval(i - 1, i - 1);
                }

                Rectangle rr = getCellRect(i - 1, 0, true);
                rr.add(getCellRect(i - 1, 4, true));
                scrollRectToVisible(rr);

                updatePlayerPosition();
                if (add) {
                    adjustMultiSize();
                }
            }
        }
    }

    public void nextBeat() {
        nextBeat(false);
    }

    public void nextBeat(boolean add) {
        int[] rows = getSelectedRows();
        int i = -1;
        int n = getRowCount();
        if (n < 1) {
            return;
        }

        if (rows == null || rows.length < 1) {
            // if nothing selected
            int k = sheet != null ? sheet.nextElement() : 0;
            // take next possible note
            if (k >= 0) {
                while (k < n && !getRowAt(k).isNoteOrPageBreak()) {
                    k++;
                }
                if (k < n) {
                    i = k - 1;
                }
            } else {
                i = n - 2;
            }
        } else {
            i = rows[rows.length - 1];
        }
        if (i < n - 1) {
            if (!add && !getRowAt(i + 1).isNote()) {
                home();
            } else {
                if (add) {
                    int j = i + 1;
                    while (!getRowAt(j).isNote()) {
                        j++;
                        if (j >= n) {
                            return;
                        }
                    }
                    setRowSelectionInterval(rows[0], j);
                } else {
                    setRowSelectionInterval(i + 1, i + 1);
                }

                Rectangle rr = getCellRect(i + 1, 0, true);
                rr.add(getCellRect(i + 1, 4, true));
                scrollRectToVisible(rr);

                updatePlayerPosition();
                if (add) {
                    adjustMultiSize();
                }
            }
        }
    }

    public int countSelectedPages() {
        int i = getSelectionModel().getMinSelectionIndex();
        int j = getSelectionModel().getMaxSelectionIndex();
        if (i < 0) {
            return 0;
        }

        int span = 1;
        for (int k = i; k <= j; k++) {
            if (getRowAt(k).isPageBreak()) {
                span++;
            }
        }
        return span;
    }

    public void adjustMultiSize() {
        int s = getSelectionModel().getMinSelectionIndex();
        int t = getSelectionModel().getMaxSelectionIndex();
        if (sheet == null || s < 0 || t < 0
                || (sheet.isVisible(s) && sheet.isVisible(t))) {
            return;
        }

        int span = countSelectedPages();
        if (span > 1) {
            if (sheet != null) {
                sheet.enablePan(false);
            }
            zoomMode = ZOOM_MULTI;
            multiSize = span + 1;
        } else {
            if (sheet != null) {
                sheet.enablePan(true);
            }
            zoomMode = ZOOM_ONE;
            multiSize = 1;
        }
        zoomPage();
    }

    public void selectNextBeat() {
        nextBeat(true);
    }

    public void selectPrevBeat() {
        prevBeat(true);
    }

    public void gotoGap() {
        YassRow r = tm.getCommentRow("GAP:");
        if (r == null) {
            return;
        }
        int i = tm.getData().indexOf(r);
        setRowSelectionInterval(i, i);
        Rectangle rr = getCellRect(i, 0, true);
        rr.add(getCellRect(i, 4, true));
        scrollRectToVisible(rr);
        updatePlayerPosition();
    }

    public void gotoStart() {
        YassRow r = tm.getCommentRow("START:");
        if (r == null) {
            return;
        }
        int i = tm.getData().indexOf(r);
        setRowSelectionInterval(i, i);
        Rectangle rr = getCellRect(i, 0, true);
        rr.add(getCellRect(i, 4, true));
        scrollRectToVisible(rr);
        updatePlayerPosition();
    }

    public void gotoEnd() {
        YassRow r = tm.getCommentRow("END:");
        if (r == null) {
            return;
        }
        int i = tm.getData().indexOf(r);
        setRowSelectionInterval(i, i);
        Rectangle rr = getCellRect(i, 0, true);
        rr.add(getCellRect(i, 4, true));
        scrollRectToVisible(rr);
        updatePlayerPosition();
    }

    public int getPage(int b) {
        int pn = 1;
        int i = 0;
        Enumeration<?> en = tm.getData().elements();
        while (en.hasMoreElements()) {
            YassRow r = (YassRow) en.nextElement();
            if (r.isNote() && pn == b) {
                return i;
            }
            if (r.isPageBreak()) {
                pn++;
            }
            i++;
        }
        return -1;
    }

    public void gotoPage(int b) {
        int n = getRowCount();
        if (n < 1) {
            return;
        }

        int row = 0;
        int[] rows = getSelectedRows();
        if (rows == null || rows.length < 1) {
            row = sheet != null ? sheet.nextElement() : 0;
            if (row < 0) {
                row = 0;
            }
        } else {
            if (b < 0) {
                row = rows[0];
            } else {
                row = rows[rows.length - 1];
            }
        }

        if (b < 0) {
            YassRow r = getRowAt(row);
            while (r.isNote() && row > 0) {
                r = getRowAt(--row);
            }
            while ((!r.isNote()) && row > 0) {
                r = getRowAt(--row);
            }
            while (r.isNote() && row > 0) {
                r = getRowAt(--row);
            }
            if (row > 0) {
                row++;
            }
            if (row == 0) {
                return;
            }
        } else if (b > 0) {
            YassRow r = getRowAt(row);
            while (r != null && r.isNote() && row < n - 1) {
                r = getRowAt(++row);
            }
            while (r != null && (!r.isNote()) && row < n - 1) {
                r = getRowAt(++row);
            }
            if (row == n - 1) { // select END --> no
                return;
            }
        }

        setRowSelectionInterval(row, row);
        Rectangle rr = getCellRect(row, 0, true);
        rr.add(getCellRect(row, 4, true));
        scrollRectToVisible(rr);

        updatePlayerPosition();
        if (sheet != null && !sheet.isVisible(row)) {
            zoomPage();
        }
    }

    public void multiply() {
        boolean oldUndo = preventUndo;
        preventUndo = true;
        recalcBPM(2d, oldUndo);

    }

    public void divide() {
        if (getBPM() / 2.0 < 20) {
            return;
        }

        boolean oldUndo = preventUndo;
        preventUndo = true;

        recalcBPM(0.5d, oldUndo);
    }

    public void recalcBPM(double newBpm) {
        double multiplier = newBpm / getBPM();

        boolean oldUndo = preventUndo;
        preventUndo = true;
        recalcBPM(multiplier, oldUndo);
    }

    private void recalcBPM(double multiplier, boolean oldUndo) {
        int sel = getSelectionModel().getMinSelectionIndex();
        if (sel < 0 && sheet != null) {
            sel = sheet.nextElement();
        }
        boolean addSyllableGap = prop.getBooleanProperty("touching-syllables") && getBPM() >= 100d;
        Enumeration<?> en = tm.getData().elements();
        YassRow previousRow = null;
        while (en.hasMoreElements()) {
            YassRow currentRow = (YassRow) en.nextElement();
            if (currentRow.isNote()) {
                currentRow.setBeat(currentRow.getBeatInt() * multiplier);
                currentRow.setLength(Math.max(1, (currentRow.getLengthInt() * multiplier)));
            }
            if (currentRow.isPageBreak()) {
                currentRow.setBeat(currentRow.getBeatInt() * multiplier);
                if (currentRow.hasSecondBeat()) {
                    currentRow.setSecondBeat(Math.max(1, (currentRow.getSecondBeatInt() * multiplier)));
                }
            }
            if (previousRow != null && previousRow.isNote()) {
                int previousEnd = previousRow.getBeatInt() + previousRow.getLengthInt();
                if (currentRow.getBeatInt() - previousEnd <= 1 && previousRow.getLengthInt() > 1 && addSyllableGap) {
                    previousRow.setLength(previousRow.getLengthInt() - 1);
                }
            }
            previousRow = currentRow;
        }
        setBPM(Precision.round(getBPM() * multiplier, 2));
        preventUndo = oldUndo;
        tm.fireTableDataChanged();
        if (sel >= 0) {
            setRowSelectionInterval(sel, sel);
            zoomPage();
        }
    }

    public void rehyphenate() {
        if (getSelectedRows().length < 2) {
            return;
        }
        List<String> syllables = new ArrayList<>();
        for (int i = 0; i < getSelectedRows().length; i++) {
            int selectedRow = getSelectedRows()[i];
            YassRow currentRow = getRowAt(selectedRow);
            if (currentRow.isNote()) {
                syllables.add(currentRow.getTrimmedText());
            }
        }
        List<String> rehyphenated = hyphenator.rehyphenate(syllables);
        int index = 0;
        boolean endsWithSpace;
        if (syllables.equals(rehyphenated)) {
            // Nothing has changed
            return;
        }
        for (int i = 0; i < getSelectedRows().length; i++) {
            int selectedRow = getSelectedRows()[i];
            YassRow currentRow = getRowAt(selectedRow);
            if (currentRow.isNote()) {
                endsWithSpace = currentRow.endsWithSpace();
                currentRow.setText(rehyphenated.get(index++) + (endsWithSpace ? YassRow.SPACE : ""));
            }
        }
        tm.fireTableDataChanged();
    }

    public void addHyphenatedWord() {
        if (getSelectedRows().length < 2) {
            return;
        }
        boolean spacingAfter = prop.isUncommonSpacingAfter();
        List<String> words = new ArrayList<>();
        StringJoiner word = new StringJoiner("•");
        StringBuilder trimmedText;
        int selectedRowCount = getSelectedRows().length;
        for (int i = 0; i < selectedRowCount; i++) {
            int selectedRow = getSelectedRows()[i];
            YassRow currentRow = getRowAt(selectedRow);
            if (!currentRow.isNote()) {
                continue;
            }
            int tempIndex = i;
            trimmedText = new StringBuilder(
                    currentRow.getTrimmedText().replace("~", "").replaceAll("[.,!?:;]", ""));
            YassRow nextRow;
            boolean checkNextRow = false;
            if (tempIndex + 1 < selectedRowCount) {
                nextRow = getRowAt(selectedRow + 1);
                checkNextRow = nextRow.getText().startsWith("~");
            }
            if (checkNextRow) {
                for (int j = 1; (j + tempIndex) < selectedRowCount; j++) {
                    nextRow = getRowAt(selectedRow + j);
                    if (nextRow.getText().equals("~")) {
                        continue;
                    }
                    if (spacingAfter && nextRow.endsWithSpace() || !spacingAfter && !nextRow.startsWithSpace()) {
                        trimmedText.append(
                                nextRow.getTrimmedText().replace("~", "").replaceAll("\\p{Punct}&&[^']", ""));
                        currentRow = nextRow;
                        i++;
                    } else {
                        break;
                    }
                }
            }
            if (spacingAfter) {
                if (StringUtils.isNotEmpty(trimmedText)) {
                    word.add(trimmedText);
                }
                if (currentRow.endsWithSpace()) {
                    words.add(word.toString());
                    word = new StringJoiner("•");
                }
            } else {
                if (currentRow.startsWithSpace() && word.length() > 0) {
                    words.add(word.toString());
                    word = new StringJoiner("•");
                }
                if (StringUtils.isNotEmpty(trimmedText)) {
                    word.add(trimmedText);
                }
            }
        }
        if (word.length() > 0) {
            words.add(word.toString());
        }
        if (words.isEmpty()) {
            return;
        }
        getActions().initHyphenatorDictionary(false);
        Locale songLocale = YassUtils.determineLocale(getLanguage());
        HyphenatorDictionary hyphenatorDictionary = getActions().hyphenatorDictionary; 
        hyphenatorDictionary.changeLanguage(songLocale.getLanguage());
        words.stream()
             .filter(it -> it.contains("•"))
             .forEach(hyphenatorDictionary::addWordToDictionary);
        hyphenatorDictionary.reinitWords();
        hyphenatorDictionary.setSaveAndClose(true);
        hyphenatorDictionary.setVisible(true);
        hyphenatorDictionary.selectWord(words.get(0));
        hyphenatorDictionary.focusTxtWord();
    }
    public void shiftEnding() {
        if (!checkShiftEndingConditions(false)) {
            return;
        }
        int selectedRows = getSelectedRows().length;
        int selectedRowIndex = getSelectedRows()[0];
        YassRow lastNote = getRowAt(selectedRowIndex + (selectedRows - 1));
        YassRow firstNote = getRowAt(selectedRowIndex);
        String oldLast = lastNote.getTrimmedText();
        int tilde = oldLast.indexOf("~");
        String newLast = "~" + StringUtils.right(firstNote.getTrimmedText(), 1) + oldLast.substring(tilde + 1);
        String newFirst = StringUtils.left(firstNote.getTrimmedText(), firstNote.getTrimmedText().length() - 1);
        lastNote.setText(newLast + (lastNote.endsWithSpace() ? YassRow.SPACE : ""));
        firstNote.setText(newFirst);
        trimMiddleTildes();
        tm.fireTableDataChanged();
        getSelectionModel().setSelectionInterval(selectedRowIndex, selectedRowIndex + (selectedRows - 1));
    }

    private void trimMiddleTildes() {
        int selectedRows = getSelectedRows().length;
        if (selectedRows < 3) {
            return;
        }
        int selectedRowIndex = getSelectedRows()[0];
        for (int i = 0; i < selectedRows; i++) {
            YassRow tempSyllable = getRowAt(selectedRowIndex + i);
            String text = tempSyllable.getTrimmedText();
            if (i > 0 && i < selectedRows - 1) {
                tempSyllable.setText(text);
            }
        }
    }

    /**
     * Shifts the first letter before a ~ of the last syllable to the right.
     * Conditions:
     * First syllable must end with a letter
     * Last syllable must start with ~ and must have at least one letter after that
     * All syllables between the first and the last must be a ~
     */
    public void shiftEndingLeft() {
        if (!checkShiftEndingConditions(true)) {
            return;
        }
        int selectedRows = getSelectedRows().length;
        int selectedRowIndex = getSelectedRows()[0];
        YassRow lastNote = getRowAt(selectedRowIndex + (selectedRows - 1));
        YassRow firstNote = getRowAt(selectedRowIndex);
        String oldLast = lastNote.getTrimmedText();
        String newLast;
        String characterToMove;
        if (oldLast.length() == 1) {
            newLast = "";
            characterToMove = oldLast;
        } else {
            int tilde = oldLast.indexOf("~");
            if (tilde + 1 == oldLast.length() && tilde - 1 >= 0) {
                characterToMove = oldLast.substring(tilde - 1, 1);
                newLast = oldLast.substring(tilde);
            } else {
                characterToMove = oldLast.substring(tilde + 1, tilde + 2);
                newLast = oldLast.substring(tilde + 2);
            }
        }
        String newFirst = firstNote.getTrimmedText() + characterToMove;
        lastNote.setText("~" + newLast + (lastNote.endsWithSpace() ? YassRow.SPACE : ""));
        firstNote.setText(newFirst);
        trimMiddleTildes();
        tm.fireTableDataChanged();
        getSelectionModel().setSelectionInterval(selectedRowIndex, selectedRowIndex + (selectedRows - 1));
    }
    
    private boolean checkShiftEndingConditions(boolean toLeft) {
        int selectedRows = getSelectedRows().length;
        if (selectedRows < 2) {
            return false;
        }
        int selectedRowIndex = getSelectedRows()[0];
        for (int i = 0; i < selectedRows; i++) {
            YassRow tempSyllable = getRowAt(selectedRowIndex + i);
            if (!tempSyllable.isNote()) {
                return false;
            }
            String text = tempSyllable.getTrimmedText();
            if (i == 0 && !toLeft && text.length() == 1) {
                // First syllable is too short
                return false;
            } else if (i > 0 && i < (selectedRows - 1) && !text.equals("~")) {
                // Syllables between are not ~
                return false;
            }
        }
        return true;
    }

    public void toggleCase() {
        int selectedRows = getSelectedRows().length;
        int selectedRowIndex = getSelectedRows()[0];
        StringBuilder lyrics = new StringBuilder();
        YassRow row;
        for (int i = 0; i < selectedRows; i++) {
            row = getRowAt(selectedRowIndex + i);
            lyrics.append(row.getText());
        }
        String text = lyrics.toString();
        boolean allCaps = text.equals(text.toUpperCase());
        boolean allLower = text.equals(text.toLowerCase());
        boolean titleCase;
        if (text.length() > 1) {
            titleCase = text.substring(0, 1).equals(text.toUpperCase().substring(0, 1)) && text.substring(1)
                                                                                               .equals(text.substring(1)
                                                                                                           .toLowerCase());
        } else {
            titleCase = allCaps;
        }
        for (int i = 0; i < selectedRows; i++) {
            row = getRowAt(selectedRowIndex + i);
            text = row.getText();
            if (allCaps) {
                row.setText(text.toLowerCase());
            } else if (allLower && i == 0) {
                row.setText(StringUtils.capitalize(text));
            } else if (titleCase) {
                row.setText(text.toUpperCase());
            }
        }
        tm.fireTableDataChanged();
        getSelectionModel().setSelectionInterval(selectedRowIndex, selectedRowIndex + (selectedRows - 1));
    }

    
    public void pasteRows() {
        int startRow = getSelectionModel().getMinSelectionIndex();
        if (startRow < 0) {
            return;
        }

        YassRow r = getRowAt(startRow);
        if (!r.isNote()) {
            return;
        }
        int startBeat = r.getBeatInt();
        try {
            Clipboard system = Toolkit.getDefaultToolkit().getSystemClipboard();
            String trstring = (String) (system.getContents(this)
                                              .getTransferData(DataFlavor.stringFlavor));
            if (StringUtils.isNotEmpty(trstring) && !trstring.startsWith(":") && !trstring.contains("\t")) {
                // Only lyrics in the clipboard -> Just pasting lyrics
                pasteLyrics(trstring);
                return;
            }

            List<String> lines = splitLinesAndStripPageBreaks(trstring);
            int i = 0;
            int n = getRowCount();
            int pasteBeat = -1;
            for (String line : lines) {
                if (startRow + i >= n) {
                    break;
                }
                StringTokenizer st2 = new StringTokenizer(line, "\t");
                String type = st2.hasMoreTokens() ? st2.nextToken() : "";
                String beat = st2.hasMoreTokens() ? st2.nextToken() : "";
                if (pasteBeat == -1) {
                    pasteBeat = Integer.parseInt(beat);
                }
                String length = st2.hasMoreTokens() ? st2.nextToken() : "";
                String height = st2.hasMoreTokens() ? st2.nextToken() : "";

                boolean isSep = type.equals("-");
                if (isSep) {
                    continue;
                }

                int beatInt = Integer.parseInt(beat);
                YassRow r2 = getRowAt(startRow + i);
                if (r2.isNote()) {
                    r2.setBeat(startBeat + beatInt - pasteBeat);
                    r2.setLength(length);
                    r2.setHeight(height);
                }
                i++;
            }
            tm.fireTableRowsUpdated(startRow, Math.min(startRow + i, n - 1));
        } catch (Exception ignored) {
        }
    }

    private List<String> splitLinesAndStripPageBreaks(String trstring) {
        List<String> lines = new ArrayList<>(Arrays.stream(trstring.split("\n")).toList());
        if (!lines.isEmpty() && lines.get(lines.size() - 1).startsWith("-")) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private void pasteLyrics(String trstring) {
        String[] lines = splitTextToLines(trstring, 0);
        for (int i = 0; i < getSelectionModel().getSelectedItemsCount(); i++) {
            YassRow row = getRowAt(i + getSelectedRow());
            if (i < lines.length) {
                YassRow tempRow = new YassRow(lines[i]);
                row.setText(tempRow.getText().replace(' ', YassRow.SPACE));
            }
        }
        tm.fireTableRowsUpdated(getSelectedRow(), getSelectedRow() + getSelectionModel().getSelectedItemsCount());
    }

    /**
     * Description of the Method
     */
    public void insertNote() {
        int n = getRowCount();
        int row = getSelectionModel().getMinSelectionIndex();
        if (row < 0) {
            row = sheet != null ? sheet.nextElement() : 0;
            if (row < 0) {
                row = n - 1;
            }
            if (row > 0) {
                row--;
            }
        }
        if (getRowAt(row).isComment()) {
            return;
        }

        int pnote = row;
        YassRow prevNote = getRowAt(pnote);
        while (!prevNote.isNote() && pnote > 0) {
            prevNote = getRowAt(--pnote);
        }
        if (!prevNote.isNote()) {
            prevNote = null;
        }

        int nnote = Math.min(row + 1, n - 1);
        YassRow nextNote = getRowAt(nnote);
        while (!nextNote.isNote() && nnote < n - 1) {
            nextNote = getRowAt(++nnote);
        }
        if (!nextNote.isNote()) {
            nextNote = null;
        }

        int pbreak = row;
        YassRow prevBreak = getRowAt(pbreak);
        while (!prevBreak.isPageBreak() && pbreak > 0) {
            prevBreak = getRowAt(--pbreak);
        }
        if (!prevBreak.isPageBreak()) {
            prevBreak = null;
        }

        int nbreak = row;
        YassRow nextBreak = getRowAt(nbreak);
        while (!nextBreak.isPageBreak() && nbreak < n - 1) {
            nextBreak = getRowAt(++nbreak);
        }
        if (!nextBreak.isPageBreak()) {
            nextBreak = null;
        }

        int beat = 0;
        if (prevNote != null) {
            beat = prevNote.getBeatInt() + prevNote.getLengthInt();
        }
        if (prevBreak != null) {
            beat = Math.max(beat, prevBreak.getSecondBeatInt());
        }

        int length = 4;
        if (nextNote != null) {
            length = Math.min(length, nextNote.getBeatInt() - beat);
        }
        if (nextBreak != null) {
            length = Math.min(length, nextBreak.getBeatInt() - beat);
        }
        if (length == 0) {
            return;
        }

        // preserve spacing
        /*
         * if (length > 1) { beat++; length--; }
         */
        if (length > 1) {
            length--;
        }

        int height = 0;
        if (prevNote != null) {
            height = prevNote.getHeightInt();
        } else if (nextNote != null) {
            height = nextNote.getHeightInt();
        }

        tm.insertRowAt(":", beat, length, height, "~", row + 1);
        tm.fireTableRowsInserted(row + 1, row + 1);
        setRowSelectionInterval(row + 1, row + 1);
        updatePlayerPosition();
        zoomPage();
    }

    public void togglePageBreak() {
        int row = getSelectionModel().getMinSelectionIndex() - 1;
        if (row < 0) {
            return;
        }
        YassRow r = getRowAt(row);
        YassRow nextRow = getRowAt(row + 1);
        if (r.isNote()) {
            insertPageBreak(true);
            setRowSelectionInterval(row + 2, row + 2);
            if (prop.getBooleanProperty("capitalize-rows") && nextRow.isNote()) {
                nextRow.setText(StringUtils.capitalize(nextRow.getText()));
            }
        } else if (r.isPageBreak()) {
            removePageBreak(true);
            if (prop.getBooleanProperty("capitalize-rows") && nextRow.isNote()) {
                nextRow.setText(StringUtils.uncapitalize(nextRow.getText()));
            }
            setRowSelectionInterval(row, row);
        }
        if (zoomMode == ZOOM_ONE) {
            zoomPage();
        } else {
            // why update??
            sheet.update();
            sheet.repaint();
        }
    }

    public void insertPageBreak(boolean before) {
        int row = before
                ? getSelectionModel().getMinSelectionIndex() - 1
                : getSelectionModel().getMaxSelectionIndex();
        if (row < 0)
            return;
        insertPageBreakAt(row);
    }

    public void insertPageBreakAt(int row) {
        YassRow r = getRowAt(row);

        if (r.isEnd()) {
            return;
        }
        if (r.isPageBreak()) {
            return;
        }

        int n = getRowCount();

        YassRow next = row < n - 1 ? getRowAt(row + 1) : null;

        if (r.isComment() && next != null && next.isComment()) {
            return;
        }

        if (next != null) {
            if (next.isPageBreak()) {
                return;
            }
            String txt = next.getText();
            if (txt.startsWith(YassRow.SPACE + "")) {
                next.setText(txt.substring(1));
            }
        }

        String beat = String.valueOf(r.getBeatInt() + r.getLengthInt());

        int[] ij = null;
        if (row > 0 && next != null) {
            if (r.isNote() && next.isNote()) {
                ij = new int[2];
                ij[0] = r.getBeatInt() + r.getLengthInt();
                ij[1] = next.getBeatInt();
                YassAutoCorrect.getCommonPageBreak(ij, bpm, null);
            }
        }
        if (ij != null) {
            tm.insertRowAt("-", ij[0] + "", (ij[1] == ij[0]) ? "" : ij[1] + "",
                           "", "", row + 1);
        } else {
            tm.insertRowAt("-", beat, "", "", "", row + 1);
        }
        tm.fireTableRowsInserted(row + 1, row + 1);
    }

    /**
     * Description of the Method
     *
     * @param before Description of the Parameter
     */
    public void removePageBreak(boolean before) {
        int row = before
                ? getSelectionModel().getMinSelectionIndex() - 1
                : getSelectionModel().getMaxSelectionIndex() + 1;
        if (row < 0)
            return;

        int n = getRowCount();
        if (row >= n - 1) {
            return;
        }

        YassRow r = getRowAt(row);
        if (!r.isPageBreak()) {
            return;
        }

        YassRow next = row < n - 1 ? getRowAt(row + 1) : null;
        YassRow prev = row > 0 ? getRowAt(row - 1) : null;

        if (next != null && next.isNote()) {
            String txt = next.getText();
            if (prev != null && prev.isNote() && prev.getText().endsWith("-")) {
                prev.setText(prev.getText().substring(0,
                                                      prev.getText().length() - 1));
            } else if (!prop.isUncommonSpacingAfter() && !txt.startsWith(YassRow.SPACE + "")
                    && !txt.startsWith("~")) {
                next.setText(YassRow.SPACE + txt);
            }
            if (prop.getBooleanProperty("capitalize-rows") && next.isNote() && !dontUncapitalize(next.getText())) {
                next.setText(StringUtils.uncapitalize(next.getText()));
            }
        }
        tm.getData().removeElementAt(row);
        tm.fireTableDataChanged();
    }

    private boolean dontUncapitalize(String word) {
        String wordToCheck = word.replace(String.valueOf(YassRow.SPACE), StringUtils.EMPTY);
        return FIXED_UPPERCASE.contains(wordToCheck);
    }

    /**
     * Description of the Method
     *
     * @param trstring Description of the Parameter
     * @param startRow Description of the Parameter
     * @param before   Description of the Parameter
     * @return Description of the Return Value
     */
    public int insertRowsAt(String trstring, int startRow, boolean before) {
        int startBeat = -1;
        if (startRow < 0) {
            if (sheet != null) {
                int pos = sheet.getPlayerPosition();
                long ms = sheet.fromTimeline(pos);
                startBeat = msToBeat(ms);
                startRow = sheet.nextNote(pos);
            }
            if (startRow < 0) {
                startRow = getRowCount() - 1;
            }
        } else {
            YassRow r = getRowAt(startRow);
            boolean isSep = r.isPageBreak();
            if (!(r.isNote() || isSep)) {
                return 0;
            }
            startBeat = r.getBeatInt();
        }
        String[] lines = splitTextToLines(trstring, startBeat);
        int num = 0;
        if (startBeat >= 0) {
            YassRow firstRowToAdd = new YassRow(lines[0]);
            YassRow lastRowToAdd;
            int temp = -1;
            do {
                lastRowToAdd = new YassRow(lines[lines.length + temp--]);
            } while (!lastRowToAdd.isNote() && (lines.length + temp) > 1);
            int offset = lastRowToAdd.getBeatInt() - firstRowToAdd.getBeatInt();
            int lastBeat;
            if (getSelectedRows().length <= 1) {
                // Only one row is selected (or none) over which to paste -> This is the starting row
                lastBeat = startBeat + offset + lastRowToAdd.getLengthInt();
            } else {
                YassRow lastSelectedRow = getRowAt(getSelectedRows()[getSelectedRows().length - 1]);
                lastBeat = lastSelectedRow.getBeatInt() + lastSelectedRow.getLengthInt();
            }
            int endRowNum = findRowThatDoesNotOverlap(startRow, lastBeat);
            List<YassRow> existingRowsToAppend;
            if (endRowNum >= startRow) {
                existingRowsToAppend = copyRowsFrom(endRowNum);
            } else {
                existingRowsToAppend = Collections.emptyList();
            }
            deleteAllRowsFrom(startRow);
            int rowCounter = before ? 0 : 1;
            int pasteBeat = -1;
            int currentBeat = 0;
            int lengthInt = 0;
            boolean hasPageBreak = false;
            for (String line : lines) {
                String[] currentRow = line.split("\t");
                String type = currentRow.length > 0 ? currentRow[0] : "";
                String beat = currentRow.length > 1 ? currentRow[1] : "";
                if (pasteBeat == -1) {
                    pasteBeat = Integer.parseInt(beat);
                }
                String length = currentRow.length > 2 ? currentRow[2] : "";
                String height = currentRow.length > 3 ? currentRow[3] : "";
                String txt = currentRow.length > 4 ? currentRow[4] : "";
                txt = txt.replace(' ', YassRow.SPACE);
                int beatInt = Integer.parseInt(beat);
                lengthInt = StringUtils.isNumeric(length) ? Integer.parseInt(length) : 0;
                boolean isSep = type.equals("-");
                if (isSep && !length.isEmpty()) {
                    length = Integer.toString(lengthInt + startBeat);
                }
                currentBeat = startBeat + beatInt - pasteBeat;
                if (currentBeat > lastBeat) {
                    break;
                }
                tm.addRow(type, String.valueOf(currentBeat), length, height, txt);
                hasPageBreak = "-".equals(type);
                rowCounter++;
            }
            if (!existingRowsToAppend.isEmpty()) {
                YassRow firstRowToAppend = existingRowsToAppend.get(0);
                int start = hasPageBreak && firstRowToAppend.isPageBreak() ? 1 : 0;
                for (YassRow rowToAppend : existingRowsToAppend.subList(start, existingRowsToAppend.size())) {
                    tm.addRow(rowToAppend);
                    rowCounter++;
                }
            }
            if (getRowCount() > 1 && tm.getRowAt(getRowCount() - 1).isPageBreak()) {
                tm.removeRowAt(getRowCount() - 1);
            }
            if (tm.getEndRow() == null) {
                tm.addEndRow();
            }
            tm.fireTableRowsInserted(before ? startRow : startRow + 1, startRow + rowCounter - 1);
        }
        return num;
    }

    private void deleteAllRowsFrom(int startRow) {
        boolean stop = false;
        while (!stop) {
            if (getRowAt(startRow) == null) {
                break;
            }
            stop = getRowAt(startRow).isEnd();
            tm.removeRowAt(startRow);
        }
    }

    private List<YassRow> copyRowsFrom(int rowNum) {
        List<YassRow> copiedRows = new ArrayList<>();
        YassRow row = getRowAt(rowNum);
        while (row != null) {
            copiedRows.add(row);
            row = getRowAt(++rowNum);
        }
        return copiedRows;
    }

    private String[] splitTextToLines(String trstring, int startBeat) {
        if (StringUtils.isEmpty(trstring)) {
            return new String[0];
        }
        String[] lines = trstring.split("\n");
        if (lines.length == 0) {
            return new String[0];
        }
        if (lines[0].contains("\t")) {
            return lines;
        }
        List<String> textLines = yassUtils.splitLyricsToLines(lines, startBeat); 
        return textLines.toArray(textLines.toArray(new String[0]));
    }
    
    private boolean endswithPageBreak(String line) {
        YassRow yassRow = new YassRow(line);
        for (YassRow row : tm.getData()) {
            if (!row.isNote() || row.getBeatInt() <= yassRow.getBeatInt()) {
                continue;
            }
            return row.isPageBreak();
        }
        return false;
    }

    private int findRowThatDoesNotOverlap(int startRow, int lastBeat) {
        YassRow row = getRowAt(startRow);
        int counter = 0;
        while (row != null && row.getBeatInt() < lastBeat) {
            row = getRowAt(startRow + (++counter));
        }
        return counter + startRow;
    }

    public int insertNotesHere() {
        if (sheet != null) {
            String trstring = null;
            try {
                Clipboard system = Toolkit.getDefaultToolkit().getSystemClipboard();
                trstring = (String) (system.getContents(this)
                                           .getTransferData(DataFlavor.stringFlavor));
            } catch (Exception e) {
                return 0;
            }

            int selectionStart = getSelectedRow();
            int selectedRows = getSelectedRowCount();
            int ret = insertRowsAt(trstring, selectionStart, true);
            sheet.updateActiveTable();
            getSelectionModel().setSelectionInterval(selectionStart, selectionStart + selectedRows - 1);
            return ret;
        }
        return 0;
    }

    public int pasteNoteHeights() {
        if (sheet != null) {
            String trstring = null;
            try {
                Clipboard system = Toolkit.getDefaultToolkit().getSystemClipboard();
                trstring = (String) (system.getContents(this)
                                           .getTransferData(DataFlavor.stringFlavor));
            } catch (Exception e) {
                return 0;
            }

            int ret = 0;
            int index = 0;
            String[] rows = trstring.split("\n");
            int startRow = -1;
            int lastRow = 0;
            int pasteRowCount = 0;
            int selectedRows = getSelectedRowCount();
            for (String row : rows) {
                YassRow pasteRow = new YassRow(row);
                if (!pasteRow.isNote()) {
                    continue;
                }
                YassRow yassRow = sheet.getActiveTable().getRowAt(getSelectedRow() + index);
                if (yassRow.isEnd()) {
                    break;
                }
                while (!yassRow.isNote()) {
                    index++;
                    yassRow = sheet.getActiveTable().getRowAt(getSelectedRow() + index);
                    if (yassRow.isEnd()) {
                        break;
                    }
                }
                if (startRow == -1) {
                    startRow = getSelectedRow() + index;
                }
                pasteRowCount++;
                // if more rows are to be pasted than rows selected, then we are jumping out now
                if (selectedRows > 1 && pasteRowCount > selectedRows) {
                    break;
                }
                lastRow = getSelectedRow() + index;
                yassRow.setHeight(pasteRow.getHeight());
                index++;
            }
            tm.fireTableRowsUpdated(startRow, lastRow);
            return ret;
        }
        return 0;
    }

    /**
     * Checks if more than one row is selected.
     */
    public boolean hasSingleSelectedRow() {
        int iMin = selectionModel.getMinSelectionIndex();
        int iMax = selectionModel.getMaxSelectionIndex();
        return iMin >= 0 && iMin == iMax;
    }

    /**
     * Description of the Method
     */
    public void joinRows() {
        int rows[] = getSelectedRows();
        if (rows == null || rows.length < 1) {
            return;
        }

        int sel = -1;
        Arrays.sort(rows);
        int n = getRowCount();
        int end = rows.length > 1 ? rows.length - 2 : 0;
        for (int i = end; i >= 0; i--) {
            int row = rows[i];

            YassRow r = getRowAt(row);
            if (!r.isNote()) {
                continue;
            }

            String txt = r.getText();

            String ntxt = null;

            YassRow nr = null;
            if (row < n - 1) {
                nr = getRowAt(row + 1);
                if (nr.isNote()) {
                    ntxt = nr.getText();
                }
            }

            /*
             * if (txt.equals("~") && ptxt!=null) {
             * pr.setLength(r.getBeatInt()-pr.getBeatInt()+r.getLengthInt());
             * tm.removeRowAt(row); sel = row-1; }
             */
            if (ntxt != null && ntxt.equals("~")) {
                r.setLength(nr.getBeatInt() - r.getBeatInt()
                                    + nr.getLengthInt());
                tm.removeRowAt(row + 1);
                sel = row;
            } else if (ntxt != null && !txt.endsWith(YassRow.SPACE + "")
                    && !ntxt.startsWith(YassRow.SPACE + "") && !prop.isUncommonSpacingAfter()) {
                if (txt.equals("~")) {
                    txt = "";
                }
                if (txt.endsWith("~")) {
                    txt = txt.substring(0, txt.length() - 1);
                }
                if (ntxt.startsWith("~")) {
                    ntxt = ntxt.substring(1);
                }
                r.setText(txt + ntxt);
                r.setLength(nr.getBeatInt() - r.getBeatInt()
                                    + nr.getLengthInt());
                tm.removeRowAt(row + 1);
                sel = row;
            } else if (ntxt != null) {
                if (txt.equals("~")) {
                    txt = "";
                } else {
                    if (ntxt.startsWith("~") && ntxt.length() > 1) {
                        ntxt = ntxt.substring(1);
                    }
                }
                r.setText(txt + ntxt);
                r.setLength(nr.getBeatInt() - r.getBeatInt()
                                    + nr.getLengthInt());
                tm.removeRowAt(row + 1);
                sel = row;
            }
        }
        tm.fireTableDataChanged();
        if (sel >= 0) {
            setRowSelectionInterval(sel, sel);
            updatePlayerPosition();
        }
    }

    /**
     * Description of the Method
     */
    public void splitRows() {
        int row = getSelectionModel().getMinSelectionIndex();
        if (row < 0) {
            return;
        }
        YassRow r = getRowAt(row);
        int words = 0;
        if (r.isNote()) {
            StringTokenizer st = new StringTokenizer(r.getText(), YassRow.SPACE
                    + "");
            words = st.countTokens();
        }
        double f = 0.5;
        if (words > 1) {
            f = (words - 1) / (double) words;
        }
        split(f, false);
    }

    /**
     * Description of the Method
     */
    public void removeRows() {
        int n = getRowCount();
        int[] rows = getSelectedRows();
        if (rows == null || rows.length < 1) {
            return;
        }

        int sel = -1;
        Arrays.sort(rows);
        for (int i = rows.length - 1; i >= 0; i--) {
            int row = rows[i];

            YassRow r = getRowAt(row);
            if (!r.isNote()) {
                if (row < n - 1) {
                    YassRow nr = getRowAt(row + 1);
                    if (nr.isNote()) {
                        String ntxt = nr.getText();
                        if (!ntxt.startsWith(YassRow.SPACE + "")) {
                            nr.setText(YassRow.SPACE + ntxt);
                        }
                    }
                }
                tm.removeRowAt(row);
                continue;
            }
            String txt = r.getText();

            String ntxt = null;

            String ptxt = null;
            YassRow nr = null;
            YassRow pr = null;
            if (row < n - 1) {
                nr = getRowAt(row + 1);
                if (nr.isNote()) {
                    ntxt = nr.getText();
                }
            }
            if (row > 0) {
                pr = getRowAt(row - 1);
                if (pr.isNote()) {
                    ptxt = pr.getText();
                }
            }

            if (txt.equals("~")) {
                tm.removeRowAt(row);
                sel = row - 1;
            } else if (ntxt != null && ntxt.equals("~")) {
                nr.setText(txt);
                tm.removeRowAt(row);
                sel = row;
            } else if (ntxt != null && !txt.endsWith(YassRow.SPACE + "")
                    && !ntxt.startsWith(YassRow.SPACE + "")) {
                nr.setText(txt + ntxt);
                tm.removeRowAt(row);
                sel = row;
            } else if (ptxt != null && !ptxt.endsWith(YassRow.SPACE + "")
                    && !txt.startsWith(YassRow.SPACE + "")) {
                pr.setText(ptxt + txt);
                tm.removeRowAt(row);
                sel = row - 1;
            } else if (ntxt != null) {
                nr.setText(txt + ntxt);
                tm.removeRowAt(row);
                sel = row;
            } else if (ptxt != null) {
                pr.setText(ptxt + txt);
                tm.removeRowAt(row);
                sel = row - 1;
            }
        }
        tm.fireTableDataChanged();
        if (sel >= 0) {
            setRowSelectionInterval(sel, sel);
            updatePlayerPosition();
        }
    }

    /**
     * Description of the Method
     */
    public boolean removeRowsWithLyrics() {
        int[] rows = getSelectedRows();
        if (rows == null || rows.length < 1) {
            return false;
        }
        Arrays.sort(rows);
        for (int i = rows.length - 1; i >= 0; i--) {
            int row = rows[i];

            YassRow r = getRowAt(row);
            if (r.isNote() || r.isPageBreak()) {
                tm.removeRowAt(row);
            }
        }
        YassRow prev = getRowAt(rows[0] - 1);
        YassRow next = getRowAt(rows[0]);
        YassRow next2 = getRowAt(rows[0] + 1);
        if (next != null && next.isPageBreak() && next2 != null && next2.isPageBreak())
            tm.removeRowAt(rows[0] + 1);
        else if (prev != null && (!prev.isNote()) && next != null && next.isPageBreak())
            tm.removeRowAt(rows[0]);

        tm.fireTableDataChanged();
        if (next != null)
            setRowSelectionInterval(rows[0], rows[0]);
        updatePlayerPosition();
        zoomPage();
        return true;
    }

    /**
     * Description of the Method
     */
    public void rollRight() {
        rollRight('$', 0);
        // shift from start
    }

    /**
     * Inserts a tilde at the current note and rolls the lyrics to the right.
     *
     * @param splitCode Char code (minus or space) when splitting the lyrics of a note. 0 when rolling manually
     * @param pos       Position within a lyric when using space or minus to roll. Otherwise 0
     */
    public void rollRight(char splitCode, int pos) {
        int rowCount = getRowCount();
        if (rowCount < 1) {
            return;
        }
        int row = getSelectionModel().getMinSelectionIndex();
        if (row < 0) {
            return;
        }

        if (skipRollingOrSplitting()) {
            return;
        }
        if (prop.isUncommonSpacingAfter()) {
            rollRightWithTrailingSpaces(row, rowCount, splitCode, pos);
        } else {
            rollRightLegacy(row, rowCount, splitCode, pos);
        }
        tm.fireTableDataChanged();
        setRowSelectionInterval(row, row);
        updatePlayerPosition();
    }

    private boolean skipRollingOrSplitting() {
        if (prop.isUncommonSpacingAfter() != isSongWithTrailingSpaces()) {
            int autocorrect = JOptionPane.showConfirmDialog(this, I18.get("edit_rollsplit_spacing_conflict"),
                                                            I18.get("edit_rollsplit_failed"),
                                                            JOptionPane.OK_CANCEL_OPTION,
                                                            JOptionPane.INFORMATION_MESSAGE);
            if (autocorrect == JOptionPane.OK_OPTION) {
                auto.autoCorrectSpacing(this);
                return false;
            }
            return true;
        }
        return false;
    }

    private void rollRightWithTrailingSpaces(int row, int rowCount, char splitCode, int pos) {
        YassRow currentRow = getRowAt(row);
        String txt = currentRow.getText();
        String[] init;
        if (pos > 0 && pos < txt.length()) {
            // Splitting a syllable with splitCode character (' ' or  '-') and rolling the lyrics to the right after
            // If the splitCode is '-', a connecting syllable is assumed. ' ' for a new syllable.
            init = new String[]{txt.substring(0, pos) + (splitCode == ' ' ? ' ' : ""), txt.substring(pos)};
        } else {
            init = new String[]{txt};
            trimEndSpace(row - 1); // remove trailing space of previous note
        }

        // Going backwards from the bottom up to the current row, thus we are determining the "first" row that is a note
        int i = rowCount - 1;
        currentRow = getRowAt(i);
        YassRow next = currentRow;
        while (i > 0 && !currentRow.isNote()) {
            next = currentRow;
            currentRow = getRowAt(--i);
        }
        if (i < 1) {
            return;
        }
        txt = currentRow.getText().trim();
        String lastTXT = null;
        if (txt.length() >= 1 && !txt.startsWith("~") && !txt.startsWith("-")) {
            lastTXT = txt;
        }
        String addSpace;
        YassRow prev;
        while (i > row) {
            prev = getRowAt(--i);
            boolean isAtEndOfPage = next.isPageBreak() || next.isEnd();
            while (i > row && !prev.isNote()) {
                prev = getRowAt(--i);
            }
            if (i >= row) {
                txt = prev.getText();
                if (i == row) {
                    if (init.length == 2) {
                        prev.setText(init[0]);
                        txt = init[1];
                    } else {
                        addSpace = shouldAddSpace(currentRow, prev, isAtEndOfPage);
                        prev.setText("~" + addSpace);
                    }
                    setRightRollTextToCurrentRow(currentRow, txt, lastTXT, isAtEndOfPage);
                    // Right-Roll initiated at second to last syllable. Thus, jumping out of the loop now
                    break;
                }
                setRightRollTextToCurrentRow(currentRow, txt, lastTXT, isAtEndOfPage);
                StringBuilder newTxt = new StringBuilder(txt);
                if (lastTXT != null) {
                    lastTXT = null;
                }
                next = currentRow;
                currentRow = prev;
            }
        }
    }

    private String shouldAddSpace(YassRow currentRow, YassRow prev, boolean isEndOfPage) {
        if (currentRow.endsWithSpace() && !prev.isTilde() && (!currentRow.isTilde() || isEndOfPage)) {
            return String.valueOf(YassRow.SPACE);
        }
        return StringUtils.EMPTY;
    }

    private void setRightRollTextToCurrentRow(YassRow currentRow, String txt, String lastTXT, boolean isAtEndOfPage) {
        StringBuilder newTxt = new StringBuilder(txt);

        if (lastTXT != null) {
            newTxt.append(lastTXT);
        }
        if (isAtEndOfPage && !newTxt.toString().endsWith(YassRow.SPACE + "")) {
            newTxt.append(YassRow.SPACE);
        }
        currentRow.setText(newTxt.toString());
    }

    private void rollRightLegacy(int row, int rowCount, char splitCode, int pos) {
        YassRow r = getRowAt(row);
        String txt = r.getText();
        String init = "~";
        if (pos > 0 && pos < txt.length()) {
            if (splitCode == ' ') {
                splitCode = YassRow.SPACE;
            }
            init = txt.substring(0, pos) + splitCode + txt.substring(pos);
        } else {
            init = txt;
        }

        int i = rowCount - 1;
        r = getRowAt(i);
        while (i > 0 && !r.isNote()) {
            r = getRowAt(--i);
        }
        if (i < 1) {
            return;
        }
        txt = r.getText().trim();
        String lastTXT = null;
        if (txt.length() >= 1 && !txt.equals("~") && !txt.equals("-")) {
            lastTXT = txt;
        }

        YassRow next = null;

        YassRow prev = null;
        while (i > row) {
            prev = getRowAt(--i);
            boolean isFirst = prev.isPageBreak();
            while (i > row && !prev.isNote()) {
                prev = getRowAt(--i);
            }
            if (i >= row) {
                YassRow pprev = i > 0 ? getRowAt(i - 1) : null;
                boolean prevIsFirst = pprev != null && pprev.isPageBreak();

                txt = prev.getText();
                if (i == row) {
                    if (init != null) {
                        char[] c = init.toCharArray();
                        int k = c.length - 1;
                        while (k > 0 && c[k] == YassRow.SPACE) {
                            k--;
                        }

                        while (k > 0 && c[k] != YassRow.SPACE && c[k] != '-') {
                            k--;
                        }
                        if (k > 0) {
                            YassRow prevnext = row < rowCount - 1 ? getRowAt(row + 1)
                                    : null;
                            if (prevnext != null && prevnext.isPageBreak()) {
                                if (c[k] == YassRow.SPACE) {
                                    prev.setText(init.substring(0, k));
                                    r.setText(init.substring(k + 1));
                                } else {
                                    prev.setText(init.substring(0, k) + "-");
                                    r.setText(init.substring(k + 1));
                                }
                            } else {
                                prev.setText(init.substring(0, k));
                                if (c[k] == YassRow.SPACE) {
                                    r.setText(init.substring(k));
                                } else {
                                    r.setText(init.substring(k + 1));
                                }
                            }
                        } else {
                            prev.setText("~");
                            r.setText(txt);
                        }
                    } else {
                        prev.setText("~");
                        r.setText(txt);
                    }
                    break;
                }

                if (prevIsFirst && !txt.startsWith(YassRow.SPACE + "")
                        && !txt.startsWith("~") && !txt.equals("-")) {
                    txt = YassRow.SPACE + txt;
                }

                if (isFirst) {
                    if (txt.startsWith(YassRow.SPACE + "")) {
                        txt = txt.substring(1);
                    } else {
                        if (pprev != null && pprev.isNote()) {
                            String ppText = pprev.getText();
                            // in v1.0.1:
                            // if (!pprev.getText().equals("~") &&
                            // !pprev.getText().equals("-")) {
                            if (!pprev.getText().equals("-")) {
                                pprev.setText(ppText + "-");
                            }
                        }
                    }
                    if (txt.endsWith("-")) {
                        txt = txt.substring(0, txt.length() - 1);
                        if (next != null) {
                            String ntxt = next.getText();
                            if (ntxt.startsWith(YassRow.SPACE + "")) {
                                next.setText(ntxt.substring(1));
                            }
                        }
                    }
                }

                if (lastTXT != null) {
                    if (!lastTXT.startsWith(YassRow.SPACE + "")) {
                        lastTXT = "-" + lastTXT;
                    }
                    r.setText(txt + lastTXT);
                    lastTXT = null;
                } else {
                    r.setText(txt);
                }

                next = r;
                r = prev;
            }
        }

    }

    public void removeEndSpace() {
        int row = getSelectionModel().getMinSelectionIndex();
        if (!prop.isUncommonSpacingAfter() || row < 0) {
            return;
        }
        YassRow currentRow = getRowAt(row);
        if (!currentRow.isNote()) {
            return;
        }
        if (currentRow.endsWithSpace()) {
            currentRow.setText(currentRow.getText().substring(0, currentRow.getText().length() - 1));
        }
    }

    /**
     * Description of the Method
     */
    public void rollLeft() {
        int n = getRowCount();
        if (n < 1) {
            return;
        }
        int row = getSelectionModel().getMinSelectionIndex();
        if (row < 0) {
            return;
        }

        int i = n - 1;
        YassRow currentRow = getRowAt(i);
        while (i > 0 && !currentRow.isNote()) {
            currentRow = getRowAt(--i);
        }
        if (i < 1) {
            return;
        }
        // index of last note
        int lastIndex = i;

        // start at current row
        i = row;
        currentRow = getRowAt(i);
        if (!currentRow.isNote()) {
            return;
        }
        if (skipRollingOrSplitting()) {
            return;
        }
        String txt = currentRow.getText();
        String init = null;
        if (txt.length() >= 1 && !txt.equals("~") && !txt.equals("-")) {
            init = txt;
        }

        YassRow prev = currentRow;
        if (prop.isUncommonSpacingAfter()) {
            rollLeftNew(n, i, currentRow, lastIndex, init);
        } else {
            rollLeftLegacy(n, i, currentRow, lastIndex, init, prev);
        }
        tm.fireTableDataChanged();
        setRowSelectionInterval(row, row);
        updatePlayerPosition();
    }

    private void rollLeftNew(int n, int i, YassRow currentRow, int lastIndex, String init) {
        YassRow prev = getRowAt(i - 1);
        YassRow next;
        String txt;
        while (i < lastIndex) {
            boolean isFirst = i == 0 || getRowAt(i - 1).isPageBreak();

            next = getRowAt(++i);
            while (i < n - 1 && !next.isNote()) {
                next = getRowAt(++i);
            }
            txt = next.getText();

            if (i == lastIndex) {
                char[] c = txt.toCharArray();
                int k = 0;
                while (k < c.length && c[k] == YassRow.SPACE) {
                    k++;
                }
                while (k < c.length && c[k] != YassRow.SPACE && c[k] != '-') {
                    k++;
                }
                if (k < c.length) {
                    currentRow.setText(txt.substring(0, k));
                    if (c[k] == YassRow.SPACE) {
                        next.setText(txt.substring(k));
                    } else {
                        next.setText(txt.substring(k + 1));
                    }
                } else {
                    currentRow.setText(txt);
                    next.setText("~");
                }
            } else {
                if (init != null) {
                    if (txt.startsWith("~")) {
                        txt = txt.substring(1);
                    }
                    if (init.startsWith(
                            "~" + YassRow.SPACE) && prev != null && !prev.isTilde() && !prev.endsWithSpace()) {
                        prev.setText(prev.getText() + YassRow.SPACE);
                        init = StringUtils.EMPTY;
                    }
                    currentRow.setText(init + txt);
                    init = null;
                } else {
                    if (isFirst && txt.startsWith(YassRow.SPACE + "")) {
                        txt = txt.substring(1);
                    }
                    currentRow.setText(txt);
                }

                prev = currentRow;
                currentRow = next;
            }
        }
        currentRow = getRowAt(lastIndex);
        prev = getRowAt(lastIndex - 1);
        if (currentRow != null && prev != null) {
            String[] textPunctuationPair = splitTextFromPunctuation(prev);
            prev.setText(textPunctuationPair[0]);
            String lastTxt = textPunctuationPair[1];
            if (!lastTxt.endsWith(YassRow.SPACE + "")) {
                lastTxt += YassRow.SPACE;
            }
            currentRow.setText(lastTxt);
        }
    }

    private void rollLeftLegacy(int n, int i, YassRow currentRow, int lastIndex, String init, YassRow prev) {
        String txt;
        YassRow next;
        while (i < lastIndex) {
            boolean isFirst = i == 0 || getRowAt(i - 1).isPageBreak();

            next = getRowAt(++i);
            boolean isLast = next.isPageBreak();
            while (i < n - 1 && !next.isNote()) {
                next = getRowAt(++i);
            }
            txt = next.getText();

            if (i == lastIndex) {
                char[] c = txt.toCharArray();
                int k = 0;
                while (k < c.length && c[k] == YassRow.SPACE) {
                    k++;
                }
                while (k < c.length && c[k] != YassRow.SPACE && c[k] != '-') {
                    k++;
                }
                if (k < c.length) {
                    currentRow.setText(txt.substring(0, k));
                    if (c[k] == YassRow.SPACE) {
                        next.setText(txt.substring(k));
                    } else {
                        next.setText(txt.substring(k + 1));
                    }
                } else {
                    currentRow.setText(txt);
                    next.setText("~");
                }
            } else {
                if (isLast) {
                    if (prev != null) {
                        String ptxt = prev.getText();
                        if (ptxt.endsWith("-")
                                || ptxt.endsWith(YassRow.HYPHEN + "")) {
                            prev.setText(ptxt.substring(0, ptxt.length() - 1));
                        } else if (!txt.startsWith("~")) {
                            txt = YassRow.SPACE + txt;
                        }
                    } else if (!txt.startsWith("~")) {
                        txt = YassRow.SPACE + txt;
                    }
                }

                if (init != null) {
                    if (!txt.startsWith(YassRow.SPACE + "")
                            && !init.endsWith("-")) {
                        init = init + "-";
                    }
                    if (init.endsWith("-")
                            || init.endsWith(YassRow.HYPHEN + "")) {
                        init = init.substring(0, init.length() - 1);
                    }
                    currentRow.setText(init + txt);
                    init = null;
                } else {
                    if (isFirst) {
                        if (txt.startsWith(YassRow.SPACE + "")) {
                            txt = txt.substring(1);
                        } else {
                            if (prev != null) {
                                String ptxt = prev.getText();
                                if (!ptxt.endsWith("~") && !ptxt.endsWith("-")) {
                                    prev.setText(ptxt + "-");
                                }
                            }
                        }
                    }
                    currentRow.setText(txt);
                }

                prev = currentRow;
                currentRow = next;
            }
        }
    }

    /**
     * Description of the Method
     */
    public void copyRows() {
        String s = getSelectedRowsAsString();
        StringSelection stsel = new StringSelection(s);
        Clipboard system = Toolkit.getDefaultToolkit().getSystemClipboard();
        system.setContents(stsel, stsel);
    }

    /**
     * Gets the rowCopy attribute of the YassTable object
     *
     * @return The rowCopy value
     */
    public String getSelectedRowsAsString() {
        StringBuilder sbf = new StringBuilder();
        int[] rows = getSelectedRows();
        if (rows == null || rows.length < 1) {
            return "";
        }
        Arrays.sort(rows);
        String txt = null;
        for (int row : rows) {
            sbf.append((String) getValueAt(row, 0));
            sbf.append("\t");
            sbf.append((String) getValueAt(row, 1));
            sbf.append("\t");
            sbf.append((String) getValueAt(row, 2));
            sbf.append("\t");
            sbf.append((String) getValueAt(row, 3));
            sbf.append("\t");

            txt = (String) getValueAt(row, 4);
            txt = txt.replace(YassRow.SPACE, ' ');
            sbf.append(txt);
            sbf.append("\n");
        }
        YassRow nextRow = null;
        if (getSelectedRows().length + 1 < getRowCount()) {
            nextRow = getRowAt(getSelectedRows().length + getSelectedRow());
        }
        if (nextRow != null && nextRow.isPageBreak()) {
            sbf.append("-\t" + nextRow.getBeat() + "\t\t\t\n");
        }
        return sbf.toString();
    }

    /**
     * Description of the Method
     *
     * @return Description of the Return Value
     */
    public String toString() {
        StringBuilder sbf = new StringBuilder();

        Enumeration<?> en = tm.getData().elements();
        while (en.hasMoreElements()) {
            YassRow r = (YassRow) en.nextElement();
            sbf.append(r.toString());
            sbf.append("\n");
        }
        return sbf.toString();
    }

    /**
     * Description of the Method
     */
    public void cutRows() {
        copyRows();
        removeRows();
    }

    /**
     * Description of the Method
     *
     * @param percent Description of the Parameter
     */
    public void split(double percent, boolean splitByMouse) {
        if (skipRollingOrSplitting()) {
            return;
        }
        int row = getSelectionModel().getMinSelectionIndex();
        if (row < 0) {
            return;
        }
        YassRow currentRow = getRowAt(row);
        if (!currentRow.isNote()) {
            return;
        }
        
        String[] textPunctuationPair = splitTextFromPunctuation(currentRow);
        String hyphenated = hyphenator.hyphenateWord(textPunctuationPair[0]);
        int hyphenPos = hyphenated.indexOf("\u00AD");
        int minusPos = textPunctuationPair[0].indexOf("-");
        if (minusPos >= 0 && (hyphenPos < 0 || minusPos < hyphenPos)) {
            hyphenated = textPunctuationPair[0].replaceFirst("-", "\u00AD");
            hyphenPos = minusPos;
        }
        String newText;
        String currentText;
        if (hyphenPos >= 0) {
            String[] syllables = hyphenated.split("\u00AD");
            if (currentRow.getLengthInt() > 2 && syllables.length > 2 && !splitByMouse) {
                percent = 1d / syllables.length;
            }
            currentText = hyphenated.substring(0, hyphenPos);
            if (minusPos >= 0) {
                currentText += "–";
            }
            newText = textPunctuationPair[1].replace("~", hyphenated.substring(hyphenPos + 1)
                                                                    .replace("\u00AD", ""));
        } else {
            currentText = textPunctuationPair[0];
            newText = textPunctuationPair[1];
        }

        int w = currentRow.getLengthInt();
        if (w < 2) {
            return;
        }

        int w1 = (int) Math.round(w * percent);
        int w2 = w - w1;
        boolean startSyllable = !prop.isUncommonSpacingAfter() && currentRow.startsWithSpace();
        boolean endSyllable = prop.isUncommonSpacingAfter() && currentRow.endsWithSpace();
        if (auto.isTouchingSyllables() && w1 > 1) {
            currentRow.setLength(w1 - 1);
        } else {
            currentRow.setLength(w1);
        }
        YassRow newRow = currentRow.clone();
        newRow.setBeat(currentRow.getBeatInt() + w1);
        newRow.setLength(w2);
        currentRow.setText((startSyllable ? YassRow.SPACE : StringUtils.EMPTY) + currentText);
        YassRow nextRow = getRowAt(row + 1);
        if (endSyllable && !nextRow.isTilde()) {
            newText = newText + YassRow.SPACE;
        }
        newRow.setText(newText);
        tm.getData().insertElementAt(newRow, row + 1);
        tm.fireTableDataChanged();
        setRowSelectionInterval(row, row);
        updatePlayerPosition();
    }

    public boolean isSpaceOrPunctuation(char c) {
        return c == YassRow.SPACE || c == ',' || c == '!' || c == '?'
                || c == ':' || c == ';' || c == '.';
    }

    /**
     * Description of the Method
     */
    public void joinLeft() {
        int row = getSelectionModel().getMinSelectionIndex();

        if (row < 1) {
            return;
        }
        YassRow r2 = getRowAt(row);
        if (!r2.isNote()) {
            return;
        }

        YassRow r = getRowAt(row - 1);
        if (!r.isNote()) {
            return;
        }

        int w2 = r2.getBeatInt() - r.getBeatInt() + r2.getLengthInt();
        r.setLength(w2);

        r.setHeight(r2.getHeightInt());

        String txt = r2.getText();
        if (txt.equals("~")) {
            txt = "";
        }
        r.setText(r.getText() + txt);

        tm.getData().removeElementAt(row);
        tm.fireTableDataChanged();
    }

    /**
     * Description of the Method
     */
    public void joinRight() {
        int row = getSelectionModel().getMinSelectionIndex();
        int n = getRowCount();

        if (row < 0 || row > n - 2) {
            return;
        }
        YassRow r = getRowAt(row);
        if (!r.isNote()) {
            return;
        }

        YassRow r2 = getRowAt(row + 1);
        if (!r2.isNote()) {
            return;
        }

        int w2 = r2.getBeatInt() - r.getBeatInt() + r2.getLengthInt();
        r.setLength(w2);

        String txt = r2.getText();
        if (txt.equals("~")) {
            txt = "";
        }
        r.setText(r.getText() + txt);

        tm.getData().removeElementAt(row + 1);
        tm.fireTableDataChanged();
        setRowSelectionInterval(row, row);
    }

    /**
     * Gets the text attribute of the YassTable object
     *
     * @return The text value
     */
    public String getText() {
        int n = getRowCount();
        StringBuilder sbf = new StringBuilder();
        for (int i = 0; i < n; i++) {
            YassRow r = getRowAt(i);
            if (r.isNote()) {
                String txt = r.getText();
                txt = txt.replace('-', YassRow.HYPHEN);

                boolean spacy = YassRow.trim(txt).length() == 0;
                if (spacy) {
                    // text is all-space --> author meant a space
                    txt = " " + YassRow.SPACE;
                    sbf.append(txt);
                } else {
                    if (!txt.startsWith(YassRow.SPACE + "")) {
                        if (i > 0) {
                            YassRow r2 = getRowAt(i - 1);
                            if ((r2.isNote())) {
                                String txt2 = r2.getText();
                                if (!txt2.endsWith(YassRow.SPACE + "")) {
                                    txt = "-" + txt;
                                } else {
                                    txt = YassRow.SPACE + txt;
                                }
                            }
                        }
                    }
                    int tn = txt.length();
                    if (tn > 0) {
                        if (tn > 1 && txt.charAt(0) == YassRow.SPACE) {
                            txt = " " + txt.substring(1);
                        }
                        if (tn > 1 && txt.charAt(tn - 1) == YassRow.SPACE) {
                            txt = txt.substring(0, tn - 1);
                        }
                        sbf.append(txt);
                    }
                }
            }
            if (r.isPageBreak()) {
                sbf.append("\n");
            }
        }
        return sbf.toString();
    }

    // copied from storeFile

    /**
     * Gets the plainText attribute of the YassTable object
     *
     * @return The plainText value
     */
    public String getPlainText() {
        int relPageBreak = 0;
        boolean isRel = isRelative();

        StringWriter buffer = new StringWriter();
        PrintWriter outputStream = new PrintWriter(buffer);

        int rows = tm.getRowCount();
        String s;
        for (int i = 0; i < rows; i++) {
            YassRow r = tm.getRowAt(i);
            if (isRel && !preventRelativeToAbsoluteConversion) {
                int revert = -1;
                if (r.isPageBreak() && i + 1 < rows) {
                    // set 2nd beat to next note
                    YassRow r2 = tm.getRowAt(i + 1);
                    if (r2.isNote()) {
                        revert = r.getSecondBeatInt();
                        r.setSecondBeat(r2.getBeatInt());
                    }
                }
                if (r.isNote() || r.isPageBreak()) {
                    s = r.toString(relPageBreak);
                    if (r.isPageBreak()) {
                        relPageBreak = r.getSecondBeatInt();
                    }
                } else {
                    s = r.toString();
                }
                if (revert >= 0) {
                    r.setSecondBeat(revert);
                }
            } else {
                s = r.toString();
            }

            s = s.replace(YassRow.SPACE, ' ');
            outputStream.println(s);
            // LOGGER.info(tm.getRowAt(i).toString());
        }
        return buffer.toString();
    }

    /**
     * Description of the Method
     *
     * @return Description of the Return Value
     */
    public boolean hasLyrics() {
        String txt = getText();
        txt = txt.replace('-', ' ');
        txt = txt.replace('~', ' ');
        txt = txt.replace('\n', ' ');
        txt = txt.replace('\r', ' ');
        txt = txt.replace(YassRow.HYPHEN, ' ');
        txt = txt.trim();
        // LOGGER.info("###hasLyrics: \n" + txt + "\n###");
        return txt.length() > 0;
    }

    /**
     * Gets the syllables attribute of the YassTable object
     *
     * @param txt Description of the Parameter
     * @return The syllables value
     */
    public Vector<String> getSyllables(String txt) {
        Vector<String> h = new Vector<>();
        StringTokenizer st = new StringTokenizer(txt, "\n");
        while (st.hasMoreTokens()) {
            String line = st.nextToken();
            StringTokenizer st2 = new StringTokenizer(line, " ");
            boolean first = true;
            while (st2.hasMoreTokens()) {
                String word = st2.nextToken();
                if (first) {
                    first = false;
                } else {
                    word = YassRow.SPACE + word;
                }

                StringTokenizer st3 = new StringTokenizer(word, "-", true);
                boolean last = false;
                boolean delim = false;
                while (st3.hasMoreTokens()) {
                    String syll = st3.nextToken();
                    last = delim;
                    delim = syll.equals("-");
                    if (delim && last) {
                        h.addElement("");
                    } else if (!delim) {
                        h.addElement(syll);
                    }
                }
            }
            if (st.hasMoreTokens()) {
                h.addElement("\n");
            }
        }
        return h;
    }

    /**
     * Gets the noteCount attribute of the YassTable object
     *
     * @return The noteCount value
     */
    public int getNoteOrPageBreakCount() {
        int i = 0;
        int notes = 0;
        int n = getRowCount() - 1;
        while (i <= n) {
            YassRow r = getRowAt(i);
            if (r.isNote() || r.isPageBreak()) {
                notes++;
            }
            i++;
        }
        return notes;
    }

    /**
     * Gets the noteCount attribute of the YassTable object
     *
     * @return The noteCount value
     */
    public int getNoteCount() {
        int i = 0;
        int notes = 0;
        int n = getRowCount() - 1;
        while (i <= n) {
            YassRow r = getRowAt(i);
            if (r.isNote()) {
                notes++;
            }
            i++;
        }
        return notes;
    }

    public String getPageMessage(int line) {
        int pageIndex = getPage(line);
        if (pageIndex >= 0) {
            int[] ij = enlargeToPages(pageIndex, pageIndex);
            for (int k = ij[0]; k <= ij[1] /* include page break */ + 1; k++) {
                YassRow row = getRowAt(k);
                if (row.hasMessage()) return row.getMessage();
            }
        }
        return null;
    }

    /**
     * Description of the Method
     *
     * @param txt Description of the Parameter
     * @return Description of the Return Value
     */
    public int applyLyrics(String txt) {
        int notes = getNoteOrPageBreakCount();

        Vector<String> hyph = getSyllables(txt);
        int syllables = hyph.size();

        int mismatch = syllables - notes;

        // spread syllables
        int k = 0;
        boolean changed = false;
        Vector<?> data = tm.getData();
        int n = data.size();
        for (int i = 0; i < n; i++) {
            YassRow r = getRowAt(i);
            if (r.isNoteOrPageBreak()) {
                if (k < syllables) {
                    txt = hyph.elementAt(k++);
                } else {
                    txt = " -";
                }
                if (txt.equals("\n")) {
                    if (k < syllables) {
                        txt = hyph.elementAt(k);
                        if (txt.equals("\n")) {
                            while (k < syllables && txt.equals("\n")) {
                                txt = hyph.elementAt(k++);
                            }
                        }
                    }
                    if (!r.isPageBreak()) {
                        insertPageBreakAt(i - 1);
                        // i++;
                        changed = true;
                        continue;
                    } else {
                        continue;
                    }
                }
                txt = txt.replace(YassRow.HYPHEN, '-');

                if (r.isNote() && !r.getText().equals(txt)) {
                    r.setText(txt);
                    changed = true;
                } else if (r.isPageBreak()) {
                    tm.getData().removeElementAt(i);
                    i--;
                    k--;
                    changed = true;
                }
            }
        }
        if (changed) {
            tm.fireTableDataChanged();
        }

        return mismatch;
    }

    /**
     * Gets the selection attribute of the YassTable object
     *
     * @param i                 Description of the Parameter
     * @param j                 Description of the Parameter
     * @param inout             Description of the Parameter
     * @param clicks            Description of the Parameter
     * @param includePageBreaks Description of the Parameter
     * @return The selection value
     */
    public long[][] getSelection(int i, int j, long[] inout, long[][] clicks,
                                 boolean includePageBreaks) {
        int n = getRowCount();
        boolean all = i < 0 && j < 0;
        if (i < 0) {
            inout[0] = 0;
            i = 0;
        }
        if (j < 0 || j > n - 1) {
            inout[1] = -1;
            j = n - 1;
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int beat;
        int end;
        int clickCount = 0;
        if (clicks == null) {
            if (includePageBreaks) {
                clicks = new long[j - i + 1][3];
            } else {
                int nn = 0;
                for (int k = i; k <= j; k++) {
                    YassRow r = getRowAt(k);
                    if (r.isNote()) {
                        nn++;
                    }
                }
                clicks = new long[nn][3];
            }
        }

        for (int k = i; k <= j; k++) {
            YassRow r = getRowAt(k);
            if (r.isComment() || r.isEnd()) {
                continue;
            }
            if (r.isPageBreak()) {
                if (includePageBreaks) {
                    beat = r.getBeatInt();
                    end = r.hasSecondBeat() ? r.getSecondBeatInt() : beat;
                    clicks[clickCount][0] = beat;
                    clicks[clickCount][1] = 100;
                    clicks[clickCount][2] = end;
                    clickCount++;
                    min = Math.min(min, beat);
                    max = Math.max(max, end);
                }
            } else if (r.isNote()) {
                beat = r.getBeatInt();
                end = beat + r.getLengthInt();
                clicks[clickCount][0] = beat;
                clicks[clickCount][1] = r.getHeightInt();
                clicks[clickCount][2] = end;
                clickCount++;
                min = Math.min(min, beat);
                max = Math.max(max, end);
            }
        }
        if (!all && min == Integer.MAX_VALUE) {
            return null;
        }

        if (!all) {
            inout[0] = (long) ((60 * min / (4.0 * bpm) + gap / 1000.0) * 1000000L);
            inout[1] = (long) ((60 * max / (4.0 * bpm) + gap / 1000.0) * 1000000L);
        }
        for (i = 0; i < clickCount; i++) {
            clicks[i][0] = (long) ((60 * clicks[i][0] / (4.0 * bpm) + gap / 1000.0) * 1000000L);
            clicks[i][2] = (long) ((60 * clicks[i][2] / (4.0 * bpm) + gap / 1000.0) * 1000000L);
        }
        return clicks;
    }

    /**
     * Get singer name from DUETSINGERP[i+1].
     *
     * @return null if < 2 players or if not set
     */
    public String getDuetSingerName(int i) {
        int n = getPlayerCount();
        if (n < 2 || i >= n || i < 0)
            return null;
        return duetSingerNames[i];
    }

    /**
     * Set singer name for DUETSINGERP[i+1]. Inserts row if missing, just before row #BPM.
     *
     * @return false if < 2 players or if index invalid
     */
    public boolean setDuetSingerName(int i, String name) {
        int n = getPlayerCount();
        if (n < 2 || i >= n || i < 0)
            return false;
        duetSingerNames[i] = name;

        YassRow r = tm.getCommentRow("DUETSINGERP" + (i + 1) + ":");
        if (r == null) {
            r = new YassRow("#", "DUETSINGERP" + (i + 1) + ":", name, "", "");
            YassRow bpm = tm.getCommentRow("BPM:");
            int k = bpm != null ? tm.getData().indexOf(bpm) : 0;
            tm.getData().insertElementAt(r, k);
            tm.fireTableDataChanged();
        } else {
            String old = r.getHeaderComment();
            if (!name.equals(old)) {
                r.setComment(name);
                int k = tm.getData().indexOf(r);
                tm.fireTableRowsUpdated(k, k);
            }
        }
        return true;
    }

    /**
     * Gets singer names as "P1/P2/...", with "-" for unnamed singers, e.g. "-/-".
     *
     * @return never null
     */
    public String getDuetSingerNamesAsString() {
        int n = getPlayerCount();
        if (n < 2)
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (!sb.isEmpty())
                sb.append('/');
            String name = duetSingerNames[i];
            sb.append(name != null ? name : "-");
        }
        return sb.toString();
    }

    /**
     * Splits duet into tracks.
     *
     * @return null if < 2 players
     */
    public Vector<YassTable> splitTable() {
        int trackCount = getPlayerCount();
        if (trackCount < 2)
            return null;
        Vector<YassTable> trackTables;
        try {
            // create tables
            trackTables = new Vector<>(trackCount);
            for (int i = 0; i < trackCount; i++) {
                YassTable t = new YassTable();
                t.init(prop);
                t.setDuetTrack(i + 1, getDuetSingerName(i));
                t.setDuetTrackCount(trackCount);
                trackTables.addElement(t);
            }

            // copy header
            for (YassTable t : trackTables) {
                Vector<YassRow> trackData = t.getModelData();
                for (YassRow r : getModelData()) {
                    UltrastarHeaderTag commentTag = UltrastarHeaderTag.getTag(r.getHeaderCommentTag());
                    if (!r.isComment()) // stop after header
                        break;
                    if (commentTag == DUETSINGERP1 || commentTag == DUETSINGERP2 || commentTag == P1 ||
                            commentTag == P2) {
                        // remove track name
                        continue;
                    }
                    trackData.addElement(new YassRow(r));
                }
                t.setDir(getDir());
                t.setFilename(getFilename());
                t.setBPM(getBPM());
                t.setGap(getGap());
            }

            // extract player notes
            Vector<YassRow>[] tracksData = new Vector[trackTables.size()];
            for (int i = 0; i < tracksData.length; i++)
                tracksData[i] = trackTables.elementAt(i).getModelData();
            int p = 0;
            for (YassRow r : getModelData()) {
                if (r.isComment())
                    continue;
                if (r.isP()) {
                    String player = StringUtils.isEmpty(r.getBeat()) && r.getType().length() > 1 ? r.getType()
                                                                                                    .substring(1)
                                                                                                    .trim() : r.getBeat();
                    try {
                        p = Integer.parseInt(player);
                    } catch (Exception e) {
                        p = 0;
                    }
                    continue;
                }
                if (r.isNote() && p == 0) { // add to all players
                    for (int i = 0; i < trackTables.size(); i++)
                        tracksData[i].addElement(new YassRow(r));
                }
                if (r.isNote() && p > 0) { // add to all player in bitmask
                    for (int k : YassUtils.getBitMask(p))
                        tracksData[k].addElement(new YassRow(r));
                } else if (r.isPageBreak()) {
                    for (Vector<YassRow> trackData : tracksData) {
                        int lastIndex = trackData.size() - 1;
                        YassRow lastRow = lastIndex >= 0 ? trackData.elementAt(lastIndex) : null;
                        if (lastRow != null && lastRow.isNote()) // skip other-player page breaks
                            trackData.addElement(new YassRow(r));
                    }
                } else if (r.isEnd()) {
                    for (Vector<YassRow> trackData : tracksData) {
                        // skip other-player page breaks
                        int lastIndex = trackData.size() - 1;
                        YassRow last = trackData.elementAt(lastIndex);
                        while (last.isPageBreak()) {
                            trackData.removeElementAt(lastIndex--);
                            if (lastIndex == 0)
                                break;
                            last = trackData.elementAt(lastIndex);
                        }
                        trackData.addElement(new YassRow(r));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.INFO, e.getMessage(), e);
            return null;
        }
        return trackTables;
    }

    // assure all tables share same gap/bpm/start/end
    public static boolean sameGap(Vector<YassTable> tables) {
        if (tables != null && tables.size() >= 2) {
            YassTable masterTable = tables.firstElement();
            for (YassTable t : tables) {
                if (t.getGap() != masterTable.getGap())
                    return false;
            }
        }
        return true;
    }

    public static boolean sameBPM(Vector<YassTable> tables) {
        if (tables != null && tables.size() >= 2) {
            YassTable masterTable = tables.firstElement();
            for (YassTable t : tables) {
                if (t.getBPM() != masterTable.getBPM())
                    return false;
            }
        }
        return true;
    }

    public static YassTable mergeTables(Vector<YassTable> tables, YassProperties prop) {
        if (tables == null || tables.size() < 2)
            return null;
        // copy all tables
        Vector<YassTable> tables2 = new Vector<>();
        for (YassTable t : tables) {
            YassTable t2 = new YassTable();
            t2.init(prop);
            t2.loadTable(t, true);
            tables2.add(t2);
        }

        // assure all tables share same gap/bpm/start/end
        if (!YassTable.sameBPM(tables))
            throw new IllegalArgumentException("BPM");
        if (!YassTable.sameGap(tables)) {
            // new gap = minimum of all first notes (in ms)
            double gapMs = Double.MAX_VALUE;
            for (YassTable t : tables) {
                YassRow r = t.getFirstNote();
                int b = r.getBeatInt();
                double ms = t.beatToMs(b);
                if (gapMs > ms)
                    gapMs = ms;
            }
            // set same gap on all tracks, recalculate beats
            for (YassTable t : tables2) {
                double bpm = t.getBPM();
                int rounded = 0;
                // recalculate beats
                for (YassRow r : t.getModelData()) {
                    if (r.isNoteOrPageBreak()) {
                        int rb = r.getBeatInt();
                        double ms = t.beatToMs(rb);
                        double rbNew = YassTable.msToBeatExact(ms, gapMs, bpm);
                        if (rbNew != (int) rbNew)
                            rounded++;
                        r.setBeat((int) (rbNew + 0.5)); // round to nearest
                        if (r.isPageBreak() && r.hasSecondBeat()) {
                            rb = r.getSecondBeatInt();
                            ms = t.beatToMs(rb);
                            rbNew = YassTable.msToBeatExact(ms, gapMs, bpm);
                            r.setSecondBeat((int) (rbNew + 0.5)); // round to nearest
                        }
                    }
                }
                t.setGap(gapMs); // same gap on all tracks
                if (rounded > 0)
                    LOGGER.info("Rounded " + rounded + " times in track " + t.getDirFilename());
            }
        }

        // copy header from first table
        YassTable masterTable = tables2.firstElement();
        YassTable res = new YassTable();
        res.init(prop);
        res.setDir(masterTable.getDir());
        Vector<YassRow> resData = res.getModelData();
        for (YassRow r : masterTable.getModelData()) {
            if (!r.isComment())
                break;
            resData.addElement(new YassRow(r));
        }
        int i = 1;
        String duetTag = determineDuetTag(prop);
        for (YassTable t : tables2) {
            String name = t.getDuetTrackName();
            if (name != null && name.trim().length() > 0)
                resData.addElement(new YassRow("#", duetTag + i + ":", name, "", ""));
            i++;
        }

        try {
            // notes sorted by player
            i = 1;
            for (YassTable t : tables2) {
                if (prop.isLegacyDuet()) {
                    resData.addElement(new YassRow("P", i + "", "", "", ""));
                } else {
                    resData.addElement(new YassRow("P" + i, "", "", "", ""));
                }
                for (YassRow r : t.getModelData()) {
                    if (r.isNoteOrPageBreak())
                        resData.addElement(r);
                }
                i <<= 1;
            }
            resData.addElement(new YassRow("E", "", "", "", ""));
            return res;
        } catch (Exception e) {
            LOGGER.log(Level.INFO, e.getMessage(), e);
        }
        return null;
    }

    private static String determineDuetTag(YassProperties prop) {
        if (prop.getUsFormatVersion().getNumericVersion() < 1d) {
            return prop.getProperty("duetsinger-tag", "P");
        } else {
            return "P";
        }
    }

    /**
     * Sets new gap (in millis) by the given beat, rounded to two millis.
     * Afterwards the note at the given beat will have beat 0.
     *
     * @param beat
     */
    public void setGapByBeat(int beat) {
        double gap = getGap();
        double bpm = getBPM();

        // calculate gap from beat
        double ms = beat * (60 * 1000) / (4 * bpm);
        gap = gap + ms;
        gap = ((int) (gap * 100)) / 100.0;
        setGap(gap);
        if (sheet != null && sheet.getSongHeader() != null && sheet.getSongHeader().getGapSpinner() != null) {
            sheet.getSongHeader().getGapSpinner().setTime((int) gap);
        }

        // correct beats
        YassRow r;
        int n = getRowCount();
        for (int j = 0; j < n; j++) {
            r = getRowAt(j);
            if (r.isNoteOrPageBreak()) {
                int rb = r.getBeatInt();
                rb = rb - beat;
                r.setBeat(rb);
                if (r.isPageBreak() && r.hasSecondBeat()) {
                    rb = r.getSecondBeatInt();
                    rb = rb - beat;
                    r.setSecondBeat(rb);
                }
            }
        }

        // correct medley start/end, too
        int b = getMedleyStartBeat();
        if (b >= 0)
            setMedleyStartBeat(b - beat);
        b = getMedleyEndBeat();
        if (b >= 0)
            setMedleyEndBeat(b - beat);
    }

    /**
     * Description of the Method
     *
     * @return Description of the Return Value
     */
    public YassSession createSession() {
        Vector<YassTrack> tracks = new Vector<>();
        Vector<YassNote> notes = new Vector<>();
        Vector<YassLine> lines = new Vector<>();
        YassLine line = null;

        int firstNoteIndex = -1;
        int lastNoteIndex = -1;
        int lineStartBeat = 0;
        int noteIndex = 0;
        boolean first = true;
        boolean lineFreestyle = true;
        int lineMin = 1000;
        int lineMax = -1000;

        double trackBPM = getBPM();
        int gapMillis = (int) getGap();
        long startMillis = (long) (getStart() * 1000);
        long endMillis = (long) (getEnd() * 1000);
        long lineMillis = 0;

        for (Enumeration<?> en = tm.getData().elements(); en.hasMoreElements(); ) {
            YassRow r = (YassRow) en.nextElement();

            if (r.isNote()) {
                int type = YassNote.NORMAL;
                if (r.isGolden()) {
                    type = YassNote.GOLDEN;
                    lineFreestyle = false;
                } else if (r.isFreeStyle()) {
                    type = YassNote.FREESTYLE;
                } else {
                    type = YassNote.NORMAL;
                    lineFreestyle = false;
                }
                int beat = r.getBeatInt();
                int length = r.getLengthInt();
                int height = r.getHeightInt();
                lineMin = Math.min(lineMin, height);
                lineMax = Math.max(lineMax, height);
                String txt = r.getText();
                txt = txt.replace(YassRow.SPACE, ' ');

                long noteStartMillis = (long) ((60 * beat / (4.0 * bpm) + gap / 1000.0) * 1000);
                long noteEndMillis = (long) ((60 * (beat + length)
                        / (4.0 * bpm) + gap / 1000.0) * 1000);
                if (!r.isFreeStyle()) {
                    lineMillis += noteEndMillis - noteStartMillis;
                }

                YassNote note = new YassNote(type, beat, length, height, txt,
                                             noteStartMillis, noteEndMillis);
                notes.addElement(note);

                if (first) {
                    firstNoteIndex = noteIndex;
                    first = false;
                }
                lastNoteIndex = noteIndex;
                noteIndex++;
            }
            if (r.isPageBreak() || r.isEnd()) {
                int lineEndBeat = r.getBeatInt();
                long lineStartMillis = (int) ((60 * lineStartBeat / (4.0 * bpm) + gap / 1000.0) * 1000);
                long lineEndMillis = (int) ((60 * lineEndBeat / (4.0 * bpm) + gap / 1000.0) * 1000);

                line = new YassLine(firstNoteIndex, lastNoteIndex, lineMin,
                                    lineMax, lineStartMillis, lineEndMillis, lineMillis,
                                    lineFreestyle);
                lines.addElement(line);
                lineStartBeat = r.getSecondBeatInt();
                lineMin = 1000;
                lineMax = -1000;
                lineMillis = 0;
                lineFreestyle = true;
                first = true;
            }
        }

        YassNote[] notesArray = notes.toArray(new YassNote[]{});
        YassLine[] linesArray = lines.toArray(new YassLine[]{});
        YassTrack track = new YassTrack(notesArray, linesArray, trackBPM,
                                        gapMillis);
        tracks.addElement(track);
        YassTrack[] tracksArray = tracks
                .toArray(new YassTrack[]{});
        tracksArray[0].setDifficulty(Integer.parseInt(prop
                                                              .getProperty("player1_difficulty")));

        String[] ratings = new String[9];
        for (int i = 0; i < 9; i++) {
            ratings[i] = I18.get("session_rating_" + i);
        }

        YassSession session = new YassSession(getArtist(), getTitle(), tracksArray, startMillis, endMillis, ratings);
        int noteScore = Integer.parseInt(prop.getProperty("max-points"));
        int goldenScore = Integer.parseInt(prop.getProperty("max-golden"));
        int lineScore = Integer.parseInt(prop.getProperty("max-linebonus"));
        session.initScore(noteScore, goldenScore, lineScore);
        return session;
    }

    public void alignQuarterNoteGrid() {
        if (sheet == null) {
            return;
        }
        int currentRow = getSelectedRow();
        if (currentRow < 0) {
            currentRow = sheet.nextElement();
        }
        YassRow row = getRowAt(currentRow);
        if (!row.isNote()) {
            return;
        }
        int start = findGridBeat(row.getBeatInt());
        
        int counter = 0;
        int nextRow = start;
        while (row.isNote()) {
            row.setBeat(nextRow);
            nextRow = findGridBeat(nextRow + row.getLengthInt() + 1);
            row = getRowAt(currentRow + ++counter);
            if (row.getBeatInt() > nextRow) {
                nextRow = findGridBeat(row.getBeatInt());
            }
        }
        tm.fireTableRowsUpdated(start, nextRow);
    }
    
    private int findGridBeat(int currentBeat) {
        int gridBeat = currentBeat;
        int diff = gridBeat % 4;
        if (diff == 1) {
            gridBeat = gridBeat - 1;
        } else if (diff == 2) {
            gridBeat = gridBeat + 2;
        } else if (diff == 3) {
            gridBeat = gridBeat + 1;
        }
        return gridBeat;
    }
    
    public void sortRows() {
        Collections.sort(tm.getData());
    }

    public boolean getScrollableTracksViewportHeight() {
        return getPreferredSize().height <= getParent().getHeight();
    }

    // "equals" is used by dnd & awt, can't overwrite it here
    public boolean equalsData(YassTable t) {
        return tm.equalsData(t.tm);
    }

    // EDITORS

    public void setGoldenPoints(int goldenPoints, int idealGoldenPoints,
                                int goldenVariance, int durationGolden, int idealGoldenBeats,
                                String diff) {
        this.goldenPoints = goldenPoints;
        this.idealGoldenPoints = idealGoldenPoints;
        this.goldenVariance = goldenVariance;
        this.durationGolden = durationGolden;
        this.idealGoldenBeats = idealGoldenBeats;
        this.goldenDiff = diff;

    }

    public int getGoldenPoints() {
        return goldenPoints;
    }

    public int getIdealGoldenPoints() {
        return idealGoldenPoints;
    }

    public int getGoldenVariance() {
        return goldenVariance;
    }

    public int getDurationGolden() {
        return durationGolden;
    }

    public int getIdealGoldenBeats() {
        return idealGoldenBeats;
    }

    public String getGoldenDiff() {
        return goldenDiff;
    }

    public void setCurrentLineTo(int sourceLine) {
        int[] rows = getSelectedRows();
        if (rows == null || rows.length != 1) {
            return;
        }

        int i = rows[0];
        if (i <= 0)
            return;

        if (!getRowAt(i).isNote())
            return;

        int firstTargetIndex = i;
        YassRow firstTargetNote = getRowAt(firstTargetIndex);
        while (firstTargetNote.isNote())
            firstTargetNote = getRowAt(--firstTargetIndex);
        firstTargetIndex++;

        int lastTargetIndex = i;
        YassRow lastTargetNote = getRowAt(lastTargetIndex);
        while (lastTargetNote.isNote())
            lastTargetNote = getRowAt(++lastTargetIndex);
        lastTargetIndex--;

        // target
        int firstSourceIndex = getPage(sourceLine);
        if (firstSourceIndex <= 0)
            return;

        if (!getRowAt(firstSourceIndex).isNote())
            return;

        int lastSourceIndex = firstSourceIndex;
        YassRow lastSourceNote = getRowAt(lastSourceIndex);
        while (lastSourceNote.isNote())
            lastSourceNote = getRowAt(++lastSourceIndex);
        lastSourceIndex--;

        int firstSourceBeat = getRowAt(firstSourceIndex).getBeatInt();
        int firstTargetBeat = getRowAt(firstTargetIndex).getBeatInt();
        for (i = firstSourceIndex; i <= lastSourceIndex; i++) {
            YassRow sourceRow = getRowAt(i);
            YassRow targetRow = getRowAt(i - firstSourceIndex
                                                 + firstTargetIndex);
            targetRow.setBeat(sourceRow.getBeatInt() - firstSourceBeat
                                      + firstTargetBeat);
            targetRow.setLength(sourceRow.getLength());
            targetRow.setHeight(sourceRow.getHeight());
        }

        tm.fireTableDataChanged();
        setRowSelectionInterval(rows[0], rows[0] + 1);
        zoomPage();
        updatePlayerPosition();
    }

    public double beatToMs(int beat) {
        return 1000 * 60 * beat / (4 * bpm) + gap;
    }

    /**
     * Converts milliseconds to beats (rounded down).
     */
    public int msToBeat(double ms) {
        return (int) Math.ceil((ms - gap) * 4 * bpm / (60 * 1000));
    }

    public double msToBeatExact(double ms) {
        return ((ms - gap) * 4 * bpm / (double) (60 * 1000));
    }

    public static double msToBeatExact(double ms, double gap, double bpm) {
        return ((ms - gap) * 4 * bpm / (double) (60 * 1000));
    }

    public double getGapInBeats() {
        return gap * 4 * bpm / (60 * 1000);
    }

    public Vector<YassRow> getModelData() {
        return ((YassTableModel) getModel()).getData();
    }

    /**
     * Set track number and name from corresponding duet file (see YassTable.splitTable)
     */
    public void setDuetTrack(int duetTrack, String singerName) {
        this.duetTrack = duetTrack;
        this.duetTrackName = singerName;
    }

    /**
     * Get track number in corresponding duet file (see YassTable.splitTable)
     *
     * @return -1 if not set
     */
    public int getDuetTrack() {
        return duetTrack;
    }

    /**
     * Get track name from corresponding duet file (see YassTable.splitTable)
     *
     * @return null if not set
     */
    public String getDuetTrackName() {
        return duetTrackName;
    }

    /**
     * Set total number of tracks from corresponding duet file (see YassTable.splitTable)
     */
    public void setDuetTrackCount(int duetTrackCount) {
        this.duetTrackCount = duetTrackCount;
    }

    /**
     * Get total number of tracks in corresponding duet file (see YassTable.splitTable)
     *
     * @return -1 if not set
     */
    public int getDuetTrackCount() {
        return duetTrackCount;
    }

    public String getSelectedAudio(String selectedAudio) {
        if (AUDIO.toString().equals(selectedAudio)) {
            return getAudio();
        } else if (INSTRUMENTAL.toString().equals(selectedAudio)) {
            return getInstrumental();
        } else if (VOCALS.toString().equals(selectedAudio)) {
            return getVocals();
        } else {
            return getMP3();
        }
    }

    public void toggleApostropheEnd() {
        int selectedRow = getSelectedRow();
        if (selectedRow < 1) {
            return;
        }
        YassRow row = getRowAt(selectedRow);
        if (row == null || !row.isNote() || StringUtils.isEmpty(row.getTrimmedText()) || row.getTrimmedText()
                                                                                            .length() < 2) {
            return;
        }
        String apostrophe = prop.getBooleanProperty("typographic-apostrophes") ? "’" : "'";
        String trimmedText = StringUtils.left(row.getTrimmedText(), row.getTrimmedText().length() - 1);
        String lastChar = StringUtils.right(row.getTrimmedText(), 1);
        String endSpace = row.endsWithSpace() ? YassRow.SPACE + "" : "";
        if (lastChar.equals("’") || YassAutoCorrectApostrophes.BORING_APOSTROPHES.contains(lastChar)) {
            row.setText(trimmedText + "g" + endSpace);
        } else {
            row.setText(trimmedText + apostrophe + endSpace);
        }
        fireTableTableDataChanged();
        setRowSelectionInterval(selectedRow, selectedRow);
    }
    
    public void toggleTildeStart() {
        int selectedRow = getSelectedRow();
        if (selectedRow < 1) {
            return;
        }
        YassRow row = getRowAt(selectedRow);
        if (row == null || !row.isNote() || StringUtils.isEmpty(row.getTrimmedText()) || row.getTrimmedText()
                                                                                            .length() < 2) {
            return;
        }
        if (row.getText().startsWith("~")) {
            row.setText(row.getText().substring(1));
        } else {
            row.setText("~" + row.getText());
        }
        fireTableTableDataChanged();
        setRowSelectionInterval(selectedRow, selectedRow);
    }

    public void addSpace() {
        int selectedRow = getSelectedRow();
        if (selectedRow < 1) {
            return;
        }
        YassRow row = getRowAt(selectedRow);
        if (row == null || !row.isNote() || StringUtils.isEmpty(row.getTrimmedText())) {
            return;
        }
        boolean trailingSpace = prop.isUncommonSpacingAfter();
        if (trailingSpace && !row.endsWithSpace()) {
            row.setText(row.getText() + YassRow.SPACE);
        }
        if (!trailingSpace && selectedRow < getRowCount()) {
            YassRow nextRow = getRowAt(selectedRow + 1);
            if (!nextRow.isNote() || nextRow.startsWithSpace()) {
                return;
            }
            nextRow.setText(YassRow.SPACE + nextRow.getText());
        }
        fireTableTableDataChanged();
        setRowSelectionInterval(selectedRow, selectedRow);
    }

    public void removeSpace() {
        int selectedRow = getSelectedRow();
        if (selectedRow < 1) {
            return;
        }
        YassRow row = getRowAt(selectedRow);
        if (row == null || !row.isNote() || StringUtils.isEmpty(row.getTrimmedText())) {
            return;
        }
        boolean trailingSpace = prop.isUncommonSpacingAfter();
        if (trailingSpace && row.endsWithSpace()) {
            row.setText(StringUtils.truncate(row.getText(), row.getText().length() - 1));
        }
        if (!trailingSpace && selectedRow < getRowCount()) {
            YassRow nextRow = getRowAt(selectedRow + 1);
            if (!nextRow.isNote() || !nextRow.startsWithSpace() || nextRow.getText().length() == 1) {
                return;
            }
            nextRow.setText(nextRow.getText().substring(1));
        }
        fireTableTableDataChanged();
        setRowSelectionInterval(selectedRow, selectedRow);
    }
    
    public static class YassTableCellEditor extends AbstractCellEditor implements
            TableCellEditor {
        Dimension d = new Dimension(100, 100);
        JComboBox<?> ed = new JComboBox<Object>(new String[]{":", "*", "F", "R", "G"}) {
            public Dimension getPopupSize() {
                return d;
            }
        };
        JLabel c = new JLabel("");

        public Component getTableCellEditorComponent(JTable table,
                                                     Object value, boolean isSelected, int rowIndex, int vColIndex) {
            String v = (String) value;

            if (v.equals("*")) {
                ed.setSelectedIndex(1);
            } else if (v.equals("F")) {
                ed.setSelectedIndex(2);
            } else if (v.equals("R")) {
                ed.setSelectedIndex(3);
            } else if (v.equals("G")) {
                ed.setSelectedIndex(4);
            } else {
                ed.setSelectedIndex(0);
            }
            return ed;
        }

        public Object getCellEditorValue() {
            return ed.getSelectedItem();
        }
    }

    /**
     * Removes a trailing space of a note.
     *
     * @param row Number of the row of the note to be trimmed.
     */
    private void trimEndSpace(int row) {
        if (row < 1) {
            return;
        }
        YassRow yassRow = getRowAt(row);
        if (yassRow == null || !yassRow.endsWithSpace()) {
            return;
        }
        String txt = yassRow.getText();
        if (txt.length() > 1) {
            yassRow.setText(txt.substring(0, txt.length() - 1));
        }
    }

    private String[] splitTextFromPunctuation(YassRow yassRow) {
        String txt = yassRow.getTrimmedText();
        int txtLength = txt.length();
        String newText;
        String remainder;
        if (txtLength > 0 && isSpaceOrPunctuation(txt.charAt(txtLength - 1))) {
            newText = txt.substring(0, txtLength - 1);
            remainder = "~" + txt.substring(txtLength - 1);
        } else {
            newText = txt;
            remainder = "~";
        }
        return new String[]{newText, remainder};
    }

    public boolean isSongWithTrailingSpaces() {
        int trailingCount = 0;
        int leadingCount = 0;
        for (YassRow yassRow : getNoteRows()) {
            if (yassRow.endsWithSpace()) {
                trailingCount++;
            }
            if (yassRow.startsWithSpace()) {
                leadingCount++;
            }
        }
        return trailingCount > leadingCount;
    }

    public boolean isPreventRelativeToAbsoluteConversion() {
        return preventRelativeToAbsoluteConversion;
    }

    public void setPreventRelativeToAbsoluteConversion(boolean preventRelativeToAbsoluteConversion) {
        this.preventRelativeToAbsoluteConversion = preventRelativeToAbsoluteConversion;
    }

    public YassHyphenator getHyphenator() {
        return hyphenator;
    }

    public void setHyphenator(YassHyphenator hyphenator) {
        this.hyphenator = hyphenator;
    }

    public int findRowByComment(String tag) {
        if (tm == null || tm.getRowCount() == 0) {
            return -1;
        }
        for (int i = 0; i < tm.getRowCount(); i++) {
            YassRow row = getRowAt(i);
            if (row != null && row.isComment() && tag.equals(row.getHeaderCommentTag())) {
                return i;
            }
        }
        return -1;
    }

    public void fireTableTableDataChanged() {
        tm.fireTableDataChanged();
    }
    
    public void fireTableRowsUpdated(int firstRow, int lastRow) {
        tm.fireTableRowsUpdated(firstRow, lastRow);
    }

    public void initAutoSave() {
        if (sheet == null) {
            return;
        }
        int period = prop.getIntProperty("options_autosave_interval");
        if (period == 0) {
            LOGGER.fine("Autosave disabled");
            return;
        } 
        timer = new Timer();
        int periodMillis = Math.min(period, 600) * 1000;
        LOGGER.info("Autosave initialized with " + Math.min(period, 600) + "s intervals.");
        YassAutoSave autoSave = new YassAutoSave(this);
        timer.scheduleAtFixedRate(autoSave, 30000, periodMillis);
    }
    
    public void removeAutoSave() {
        setAutosaved(true);
        if (sheet == null || timer == null) {
            return;
        }
        timer.cancel();
    }
}
