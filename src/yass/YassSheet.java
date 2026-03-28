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

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import yass.analysis.PitchDetector;
import yass.musicalkey.MusicalKeyEnum;
import yass.renderer.*;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.logging.Logger;

@Getter
@Setter
public class YassSheet extends JPanel implements YassPlaybackRenderer, Scrollable {

    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    public final static int NORM_HEIGHT = 20;

    // gray, blue, golden, freestyle, red
    public static final int COLORSET_COUNT = 9;
    public static final int COLOR_NORMAL = 0;
    public static final int COLOR_SHADE = 1;
    public static final int COLOR_ACTIVE = 2;
    public static final int COLOR_GOLDEN = 3;
    public static final int COLOR_RAPGOLDEN = 4;
    public static final int COLOR_RAP = 5;
    public static final int COLOR_FREESTYLE = 6;
    public static final int COLOR_ERROR = 7;
    public static final int COLOR_WARNING = 8;
    private final Color[] colorSet = new Color[COLORSET_COUNT];

    public static final Color DK_GRAY = new Color(102, 102, 102);
    public static final Color HI_GRAY = new Color(153, 153, 153);
    public static final Color HI_GRAY_2 = new Color(230, 230, 230);

    public static final Color blackDarkMode = new Color(200,200,200);
    public static final Color dkGrayDarkMode = new Color(142,142,142);
    public static final Color hiGrayDarkMode = new Color(100,100,100);
    public static final Color HI_GRAY_2_DARK_MODE = new Color(70,70,70);
    public static final Color whiteDarkMode = new Color(50,50,50);
    public static final Color dkGreen = new Color(0,120,0);
    public static final Color dkGreenLight = new Color(50,220,50);

    private static final Color arrow = new Color(238, 238, 238, 160);
    private static final Color arrowDarkMode = new Color(200, 200, 200, 160);

    private static final Color playertextBG = new Color(1f, 1f, 1f, .9f); // used once; deprecated
    private static final Color playBlueHi = new Color(1f, 1f, 1f, 1f);  // used once
    private static final Color playBlue = new Color(.4f, .6f, .8f, 1f); // used once
    public static final Color BLUE = new Color(.4f, .6f, .8f, .7f);
    public static final Color blueDrag = new Color(.8f, .9f, 1f, .5f);
    public static final Color blueDragDarkMode = new Color(.4f, .6f, .8f, .5f);
    private static final Color dkRed = new Color(.8f, .4f, .4f, .7f);
    public static final Color playerColor = new Color(1f, .1f, .1f, .5f);
    private static final Color playerColor2 = new Color(1f, .1f, .1f, .3f);
    private static final Color playerColor3 = new Color(1f, .1f, .1f, .1f);
    private static final Color inoutColor = new Color(.9f, .9f, 1f, .5f);
    private static final Color inoutSnapshotBarColor = new Color(.3f, .3f, .5f, .7f);
    private static final Color inoutBarColor = new Color(.5f, .5f, .7f, .7f);

    public static final BasicStroke thinStroke = new BasicStroke(0.5f),
            stdStroke = new BasicStroke(1f), medStroke = new BasicStroke(1.5f),
            thickStroke = new BasicStroke(2f);

    @Serial
    private static final long serialVersionUID = 3284920111520989009L;
    private static final int ACTION_CONTROL = 1;
    private static final int ACTION_ALT = 2;
    private static final int ACTION_CONTROL_ALT = 4;
    private static final int ACTION_NONE = 0;

    private static final int SKETCH_LENGTH = 30;
    private static final int SKETCH_UP = 1;
    private static final int SKETCH_DOWN = 2;
    private static final int SKETCH_LEFT = 3;
    private static final int SKETCH_RIGHT = 4;
    private static final int SKETCH_NONE = 0;
    private static final int SKETCH_HORIZONTAL = -1;
    private static final int SKETCH_VERTICAL = -2;
    private static final int[] GESTURE_UP = new int[]{SKETCH_VERTICAL, SKETCH_UP};
    private static final int[] GESTURE_UP_DOWN = new int[]{SKETCH_VERTICAL, SKETCH_UP, SKETCH_DOWN};
    private static final int[] GESTURE_DOWN_UP = new int[]{SKETCH_VERTICAL, SKETCH_DOWN, SKETCH_UP};
    private static final int[] GESTURE_UP_DOWN_UP = new int[]{SKETCH_VERTICAL, SKETCH_UP, SKETCH_DOWN, SKETCH_UP};
    private static final int[] GESTURE_DOWN_UP_DOWN = new int[]{SKETCH_VERTICAL, SKETCH_DOWN, SKETCH_UP, SKETCH_DOWN};
    private static final int[] GESTURE_LEFT = new int[]{SKETCH_HORIZONTAL, SKETCH_LEFT};
    private static final int[] GESTURE_RIGHT = new int[]{SKETCH_HORIZONTAL, SKETCH_RIGHT};
    private static final int[] GESTURE_LEFT_RIGHT = new int[]{SKETCH_HORIZONTAL, SKETCH_LEFT, SKETCH_RIGHT};
    private static final int[] GESTURE_RIGHT_LEFT = new int[]{SKETCH_HORIZONTAL, SKETCH_RIGHT, SKETCH_LEFT};
    private static final int[] GESTURE_LEFT_RIGHT_LEFT = new int[]{SKETCH_HORIZONTAL, SKETCH_LEFT, SKETCH_RIGHT,
            SKETCH_LEFT};
    private static final int[] GESTURE_RIGHT_LEFT_RIGHT = new int[]{SKETCH_HORIZONTAL, SKETCH_RIGHT, SKETCH_LEFT,
            SKETCH_RIGHT};
    private static final int[] GESTURE_DOWN = new int[]{SKETCH_VERTICAL, SKETCH_DOWN};
    private static final int fs = 14;

    private final Font font = new Font("SansSerif", Font.BOLD, fs);
    private final Font fontv = new Font("SansSerif", Font.PLAIN, fs);
    private final Font fonti = new Font("SansSerif", Font.ITALIC, fs);
    private final Font fontb = new Font("SansSerif", Font.BOLD, fs + 2);
    private final Font fontt = new Font("MonoSpaced", Font.PLAIN, fs);
    private final Font fonttb = new Font("MonoSpaced", Font.BOLD, fs + 2);
    private final Font[] bigFonts = createFontCache();
    private static final int UNDEFINED = 0;
    private int dragDir = UNDEFINED;
    private int hiliteCue = UNDEFINED, dragMode = UNDEFINED;

    private static final int VERTICAL = 1;
    private static final int HORIZONTAL = 2;
    private static final int LEFT = 1;
    private static final int RIGHT = 2;
    private static final int CENTER = 3;
    private static final int CUT = 4;
    private static final int JOIN_LEFT = 5;
    private static final int JOIN_RIGHT = 6;
    private static final int SNAPSHOT = 7;
    private static final int MOVE_REMAINDER = 8;
    private static final int SLIDE = 9;

    // some flags for better button handling
    private static final int BUTTON_FLAG = 0x100;
    private static final int PRESSED_FLAG = 0x1000;

    // buttons which will be handled by getButtonXY()
    private static final int PREV_PAGE = BUTTON_FLAG | 1;
    private static final int NEXT_PAGE = BUTTON_FLAG | 2;
    private static final int PLAY_NOTE = BUTTON_FLAG | 3;
    private static final int PLAY_PAGE = BUTTON_FLAG | 4;
    private static final int PLAY_BEFORE = BUTTON_FLAG | 5;
    private static final int PLAY_NEXT = BUTTON_FLAG | 6;
    private static final int PREV_SLIDE = BUTTON_FLAG | 7;
    private static final int NEXT_SLIDE = BUTTON_FLAG | 8;

    // button ids for the pressed state
    private static final int PREV_PAGE_PRESSED = PREV_PAGE | PRESSED_FLAG;
    private static final int NEXT_PAGE_PRESSED = NEXT_PAGE | PRESSED_FLAG;
    private static final int PLAY_NOTE_PRESSED = PLAY_NOTE | PRESSED_FLAG;
    private static final int PLAY_PAGE_PRESSED = PLAY_PAGE | PRESSED_FLAG;
    private static final int PLAY_BEFORE_PRESSED = PLAY_BEFORE | PRESSED_FLAG;
    private static final int PLAY_NEXT_PRESSED = PLAY_NEXT | PRESSED_FLAG;
    private static final int PREV_SLIDE_PRESSED = PREV_SLIDE | PRESSED_FLAG;
    private static final int NEXT_SLIDE_PRESSED = NEXT_SLIDE | PRESSED_FLAG;
    private static final int BUTTON_HIT_SLOP = 4;
    private static final double MIDDLE_PAN_HORIZONTAL_FACTOR = 1.0;
    private static final double MIDDLE_PAN_VERTICAL_FACTOR = 0.6;

    boolean useSketching = false;
    boolean useSketchingPlayback = false;
    private AffineTransform identity = new AffineTransform();
    private VolatileImage backVolImage = null;
    private VolatileImage plainVolImage = null;
    String[] hNoteTable = new String[]{"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "B", "H"};
    String[] bNoteTable = new String[]{"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    String[] actualNoteTable = bNoteTable;
    boolean paintHeights = false;
    private Boolean paintHeightsBeforeAbsolutePitchView = null;
    boolean live = false;
    private YassTable table = null;
    private final Vector<YassTable> tables = new Vector<>();
    private final Vector<Vector<YassRectangle>> rects = new Vector<>();
    private Vector<YassRectangle> rect = null;
    private Vector<Cloneable> snapshot = null, snapshotRect = null;
    private YassActions actions = null;
    private boolean noshade = false;
    boolean darkMode = false;
    private boolean autoTrim = false;
    private boolean temporaryZoomOff = false;
    private long lastMidiTime = -1;
    private long lastDragTime = -1;
    private int lyricsWidth = 400;
    private boolean lyricsVisible = true;
    private boolean messageMemory = false;
    private final int[] keycodes = new int[19];
    private long equalsKeyMillis = 0;
    private Paint tex, bgtex;
    private BufferedImage bgImage = null;
    private boolean showVideo = false, showBackground = false;
    private boolean mouseover = true;
    private boolean paintSnapshot = false;
    private boolean showArrows = true;
    private boolean showPlayerButtons = true;
    private boolean showText = true;
    private int hiliteAction = 0;
    private long lastTime = -1;
    private String lastTimeString = "";
    private static final int LEFT_BORDER = 36;
    private static final int RIGHT_BORDER = 36;
    private static final int TOP_BORDER = 30;
    private static final int PLAY_PAGE_X = -76;
    private static final int PLAY_PAGE_W = 36;
    private static final int PLAY_BEFORE_X = -36;
    private static final int PLAY_BEFORE_W = 36;
    private static final int PLAY_NOTE_X = 2;
    private static final int PLAY_NOTE_W = 48;
    private static final int PLAY_NEXT_X = 49;
    private static final int PLAY_NEXT_W = 36;
    private static final int PLAYER_BUTTONS_HEIGHT = 64;
    private static final int ABSOLUTE_PITCH_MIN_HEIGHT = -60; // MIDI 0
    private static final int ABSOLUTE_PITCH_MAX_HEIGHT = 67;  // MIDI 127
    private static final int ABSOLUTE_MIN_VISIBLE_PITCH_SPAN = 6;
    private int BOTTOM_BORDER = 56;
    private int TOP_LINE;
    private int TOP_PLAYER_BUTTONS;
    private Point[] sketch = null;
    private int sketchPos = 0, dirPos = 0;
    private long sketchStartTime = 0;
    private int[] sketchDirs = null;
    private boolean sketchStarted = false;
    private final Font smallFont = new Font("SansSerif", Font.PLAIN, 10);
    private int minHeight = 0;
    private int maxHeight = 18;
    private int minBeat = 0;
    private int maxBeat = 1000;
    private int hit = -1;
    private int hilite = -1;
    private int hiliteHeight = 1000;
    private int hhPageMin = 0;
    private final int heightBoxWidth = 74;
    private final Rectangle2D.Double select = new Rectangle2D.Double(0, 0, 0, 0);
    private double selectX, selectY;
    private boolean marqueeSelectActive = false;
    private boolean marqueeSelectMoved = false;
    private int marqueeStartX = 0;
    private int marqueeStartY = 0;
    private double wSize = 30, hSize = -1;
    private double uiScale = 1.0;
    private int dragOffsetX = 0, dragOffsetY = 0, slideX = 0;
    private double dragOffsetXRatio = 0;
    private boolean middlePanActive = false;
    private int middlePanStartX = 0;
    private int middlePanStartY = 0;
    private int middlePanStartScreenX = 0;
    private int middlePanStartScreenY = 0;
    private int middlePanStartViewX = 0;
    private int middlePanStartViewY = 0;
    private final Cursor panCursor;
    private boolean centerDragPreviewActive = false;
    private int centerDragPreviewRow = -1;
    private double centerDragOriginalRectX = 0;
    private double centerDragOriginalRectY = 0;
    private final Map<Integer, Point> centerDragOriginalPositions = new HashMap<>();
    private int centerDragStartBeat = 0;
    private int centerDragStartPitch = 0;
    private int centerDragTargetBeat = 0;
    private int centerDragTargetPitch = 0;
    private int absolutePitchShiftSinceSelection = 0;
    private String absolutePitchShiftSelectionKey = "";
    private boolean pan = false, isPlaying = false, isTemporaryStop = false;
    private VerticalPitchViewMode verticalPitchViewMode = VerticalPitchViewMode.RELATIVE_PAGE;
    private double bpm = -1;
    private double gap = 0;
    private double beatgap = 0;
    private double duration = -1;
    private int outgap = 0;
    private double cutPercent = .5;
    private BufferedImage image;
    private boolean imageChanged = true;
    private int imageX = -1;
    private int imageY = -1;
    private int playerPos = -1;
    private int inPoint = -1;
    private int outPoint = -1;
    private String message = "";
    private long inSelect = -1, outSelect = -1;
    private long inSnapshot = -1, outSnapshot = -1;
    private final Cursor cutCursor;
    private boolean showNoteLength = false;
    private boolean showNoteBeat = false;

    private boolean showNoteScale = false;
    private boolean showNoteHeight = true;

    private boolean showNoteHeightNum = false;
    private Rectangle clip = new Rectangle();
    private boolean refreshing = false;
    private String equalsDigits = "";
    private boolean versionTextPainted = true;
    private final Vector<Long> tmpNotes = new Vector<>(1024);
    private List<Integer> tmpPitches = new ArrayList<>();
    private List<PitchDetector.PitchData> recordingOverlayPitchData = Collections.emptyList();
    private final Dimension dim = new Dimension(1000, 200);
    private Graphics2D pgb = null;
    private int ppos = 0;
    private Point psheetpos = null;
    private boolean pisinterrupted = false;
    private BufferedImage videoFrame = null;
    private YassSession session = null;
    private boolean isMousePressed = false;
    private int lastAbsoluteViewportY = Integer.MIN_VALUE;

    private YassLyrics lyrics;

    private SongHeader songHeader;

    private YassMain owner;
    private List<Integer> noteMapping;

    // Index of the note currently being timed in "record mode". Set by YassActions.
    private int recordingNoteIndex = -1;
    private int absoluteVisiblePitchSpan = NORM_HEIGHT - 2;

    private enum VerticalPitchViewMode {
        RELATIVE_PAGE,
        ABSOLUTE
    }


    public YassSheet() {
        super(false);
        setFocusable(true);
        Image image = new ImageIcon(this.getClass().getResource("/yass/resources/img/cut.gif")).getImage();
        cutCursor = Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(0, 10), "cut");
        panCursor = createPanCursorFromResource();
        setLayout(new BorderLayout()); // Layout-Manager setzen
        removeAll();
        setDarkMode(false); // creates TexturePaint
        initKeyListener();
        initMouseListener();
        initMouseMotionListener();
    }

    private Cursor createPanCursorFromResource() {
        try {
            BufferedImage raw = ImageIO.read(Objects.requireNonNull(
                    this.getClass().getResource("/yass/resources/img/hand.png")));
            if (raw == null) {
                return createPanCursorStyle();
            }
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Dimension best = toolkit.getBestCursorSize(raw.getWidth(), raw.getHeight());
            int targetW = best != null && best.width > 0 ? best.width : 32;
            int targetH = best != null && best.height > 0 ? best.height : 32;
            Image scaled = raw.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);

            // Use center as grab hotspot for panning.
            Point hotspot = new Point(Math.max(0, targetW / 2), Math.max(0, targetH / 2));
            return toolkit.createCustomCursor(scaled, hotspot, "pan-cursor-hand-png");
        } catch (IOException | IllegalArgumentException | NullPointerException ignored) {
            return createPanCursorStyle();
        }
    }

    private Cursor createPanCursorStyle() {
        try {
            int size = 32;
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Path2D.Double hand = new Path2D.Double();
            hand.moveTo(10, 29);
            hand.curveTo(7, 27, 6, 22, 5, 18);
            hand.curveTo(4.3, 15, 6.2, 14.2, 8.2, 15.5);
            hand.curveTo(10, 16.7, 10.8, 18.1, 12, 20);
            hand.lineTo(13, 8.8);
            hand.curveTo(13.2, 6.9, 15.6, 6.7, 16, 8.5);
            hand.lineTo(16.1, 16.2);
            hand.lineTo(18.2, 7.6);
            hand.curveTo(18.8, 5.8, 21.2, 5.9, 21.4, 7.9);
            hand.lineTo(20.9, 16.5);
            hand.lineTo(23, 8.7);
            hand.curveTo(23.6, 7.0, 25.8, 7.2, 26.0, 9.1);
            hand.lineTo(25.7, 17.8);
            hand.lineTo(27.6, 11.8);
            hand.curveTo(28.2, 10.0, 30.3, 10.3, 30.2, 12.3);
            hand.lineTo(29.2, 20.2);
            hand.curveTo(28.6, 24.8, 26.9, 27.6, 23.8, 29.4);
            hand.curveTo(20.5, 31.3, 13.4, 31.3, 10, 29);
            hand.closePath();

            // Outline-only style similar to the provided icon.
            g.setColor(new Color(15, 15, 15, 245));
            g.setStroke(new BasicStroke(2.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(hand);

            g.dispose();
            return Toolkit.getDefaultToolkit().createCustomCursor(img, new Point(16, 14), "pan-cursor");
        } catch (Exception ignored) {
            return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        AffineTransform tx = getGraphicsConfiguration().getDefaultTransform();
        uiScale = tx.getScaleX();
    }

    /**
     * Creates a cache of Font objects of different sizes to improve rendering performance.
     * Instead of creating new Font objects during painting, we can pick a pre-made one from this array.
     *
     * @return An array of pre-instantiated Font objects.
      */
    private static Font[] createFontCache() {
        final int FONT_ARRAY_SIZE = 39;
        Font[] fonts = new Font[FONT_ARRAY_SIZE];

        // The logic reconstructs the original array structure in a readable way.
        // It creates fonts from size 6 (fs-8) up to 34 (fs+20).
        for (int i = 0; i < FONT_ARRAY_SIZE; i++) {
            int size;
            if (i <= 10) { // The first 11 fonts have the minimum size
                size = fs - 8;
            } else { // The rest increase in size
                size = fs - (18 - i);
            }
            fonts[i] = new Font("SansSerif", Font.BOLD, size);
        }
        return fonts;
    }
    
    private boolean isMouseOverInteractiveChild(Point p) {
        return false;
    }

    private void initMouseMotionListener() {
        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                handleMouseMoved(e);
            }

            public void mouseDragged(MouseEvent e) {
                handleMouseDragged(e);
            }
        });
    }
    
    private void handleMouseMoved(MouseEvent e) {
        if (equalsKeyMillis > 0) {
            equalsKeyMillis = 0;
            equalsDigits = "";
        }
        if (isMouseOverInteractiveChild(e.getPoint())) {
            // When the mouse is over an interactive child,
            // let the child control the cursor and events.
            setCursor(Cursor.getDefaultCursor());
            return;
        }
        if (table == null || rect == null || isPlaying()) {
            return;
        }
        int x = e.getX();
        int y = e.getY();
        if (hilite >= 0) {
            hilite = -2;
        } else {
            hilite = -1;
        }
        if (hiliteCue != UNDEFINED) {
            hilite = -2;
        }
        hiliteCue = UNDEFINED;
        hiliteAction = ACTION_NONE;

        boolean shouldRepaint = false;

        if (paintHeights) {
            int heightBoxTop = isAbsolutePitchViewEnabled() ? clip.y + TOP_LINE - 10 : TOP_LINE - 10;
            int heightBoxBottom = isAbsolutePitchViewEnabled()
                    ? getVerticalRenderHeight() - BOTTOM_BORDER
                    : clip.height - BOTTOM_BORDER;
            if (x < clip.x + heightBoxWidth && y > heightBoxTop && (y < heightBoxBottom)) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                if (hiliteHeight < 1000) {
                    int maxY = isAbsolutePitchViewEnabled() ? getVerticalRenderHeight() : dim.height;
                    if (y < 0)
                        y = 0;
                    if (y > maxY)
                        y = maxY;
                    int dy = getScalePitchFromY(y, hhPageMin);

                    if (hiliteHeight != dy) {
                        hiliteHeight = dy;
                        repaint();
                    }
                }
                return;
            } else {
                if (hiliteHeight != 1000) {
                    shouldRepaint = true;
                    hiliteHeight = 1000;
                }
            }
        }
        int button = getButtonXY(x, y);
        if (button != UNDEFINED) {
            // generate the hover effect
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            hiliteCue = button;
            repaint();
            return;
        }
        if (table != null) {
            double previewStart = table.getPreviewStart();
            if (previewStart >= 0) {
                int previewX = toTimeline(previewStart * 1000);
                if (x >= previewX - 2 && x <= previewX + 2) {
                    setToolTipText(String.format("Preview Start: %.2f s", previewStart));
                    return;
                }
            }
            int medleyStart = table.getMedleyStartBeat();
            if (medleyStart >= 0) {
                int medleyX = beatToTimeline(medleyStart);
                if (x >= medleyX - 2 && x <= medleyX + 2) {
                    setToolTipText(String.format("Medley Start: %d ms", medleyX));
                    return;
                }
            }
            int medleyEnd = table.getMedleyEndBeat();
            if (medleyEnd >= 0) {
                int medleyX = beatToTimeline(medleyEnd);
                if (x >= medleyX - 2 && x <= medleyX + 2) {
                    setToolTipText(String.format("Medley End: %d ms", medleyX));
                    return;
                }
            }
        }
        // Clear tooltip when not hovering over anything specific
        if (getToolTipText() != null) {
            setToolTipText(null);
        }

        if (inSelect >= 0 && inSelect != outSelect && y < TOP_BORDER
                && x >= toTimeline(Math.min(inSelect, outSelect))
                && x <= toTimeline(Math.max(inSelect, outSelect))) {
            hiliteCue = SNAPSHOT;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            repaint();
            return;
        }
        Rectangle2D.Double selectedGroupHitArea = getSelectedGroupBounds(10);
        if (selectedGroupHitArea != null && selectedGroupHitArea.contains(x, y)) {
            int anchorRow = findSelectedNoteRowNear(x, y);
            if (anchorRow >= 0) {
                hilite = anchorRow;
                hiliteCue = CENTER;
                hiliteAction = ACTION_CONTROL_ALT;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                repaint();
                return;
            }
        }
        YassRectangle next = null;
        YassRectangle r;
        int i = 0;
        for (Enumeration<?> en = rect.elements(); en.hasMoreElements(); i++) {
            if (next != null) {
                r = next;
                next = (YassRectangle) en.nextElement();
            } else {
                r = (YassRectangle) en.nextElement();
            }
            if (next == null) {
                next = en.hasMoreElements() ? (YassRectangle) en.nextElement() : null;
            }
            if (r != null) {
                boolean isNote = !r.isType(YassRectangle.GAP) && !r.isType(YassRectangle.START) && !r.isType(YassRectangle.END);
                if (r.isPageBreak()) {
                    if (x > r.x - 5 && x < r.x + 5 && !autoTrim
                        && y >= TOP_LINE) {

                        hiliteCue = CENTER;
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        repaint();
                        return;
                    }
                } else if (r.contains(x, y)) {
                    hilite = i;
                    if (mouseover) {
                        if (!table.isRowSelected(i)) {
                            if (!(e.isShiftDown() || e.isControlDown()))
                                table.clearSelection();
                            table.addRowSelectionInterval(i, i);
                            table.updatePlayerPosition();
                        }
                    }
                    int dragw = r.width > Math.max(wSize, 32) * 3 ? (int) Math.max(wSize, 32) : (r.width > 72 ? 24 : (r.width > 48 ? 16 : 5));
                    if (Math.abs(r.x - x) < dragw && r.width > 20) {
                        hiliteCue = LEFT;
                        hiliteAction = ACTION_CONTROL;
                    } else if (Math.abs(r.x + r.width - x) < dragw && r.width > 20) {
                        hiliteCue = RIGHT;
                        hiliteAction = ACTION_ALT;
                    } else {
                        hiliteCue = CENTER;
                        hiliteAction = ACTION_CONTROL_ALT;
                    }

                    if (hiliteCue == CENTER) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    } else {
                        setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                    }
                    repaint();
                    return;
                } else if (table.getMultiSize() > 1
                        && r.x < x
                        && (((next == null || next.isPageBreak() || next.hasType(YassRectangle.END)) && x < r.x + r.width)
                        || (next != null && (!next.isPageBreak() && !next.hasType(YassRectangle.END)) && x < next.x))
                        && y > getStickyBandTopY() - 16
                        && y < getStickyBandTopY()) {
                    hiliteCue = CENTER;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    repaint();
                    return;
                } else if (isNote && r.x + wSize / 2 < x
                        && x < r.x + r.width - wSize / 2
                        && Math.abs(r.y - y) < hSize && r.width > 5) {
                    hilite = i;
                    hiliteCue = CUT;
                    cutPercent = (x - r.x) / r.width;
                    setCursor(cutCursor);
                    repaint();
                    return;
                } else if (isNote && r.x < x && x < r.x + wSize / 2
                        && r.width > 5) {
                    if (Math.abs(r.y - y) < hSize) {
                        hilite = i;
                        hiliteCue = JOIN_LEFT;
                        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                        repaint();
                        return;
                    }
                } else if (isNote && r.x + r.width - wSize / 2 < x
                        && x < r.x + r.width && r.width > 5) {
                    if (Math.abs(r.y - y) < hSize) {
                        hilite = i;
                        hiliteCue = JOIN_RIGHT;
                        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                        repaint();
                        return;
                    }
                }
            }
        }
        if (!isAbsolutePitchViewEnabled()
                && (y > clip.height - BOTTOM_BORDER + 20 || (y > 20 && y < TOP_LINE - 10))) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            hiliteCue = SLIDE;
            repaint();
            return;
        }
        if (x > playerPos - 10 && x < playerPos && y > TOP_LINE && y < dim.height - BOTTOM_BORDER) {
            hiliteCue = MOVE_REMAINDER;
            setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
            repaint();
            return;
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        if (hilite == -2) {
            hilite = -1;
            repaint();
            return;
        }
        if (shouldRepaint)
            repaint();
    }
    
    private void handleMouseDragged(MouseEvent e) {
        if (isMouseOverInteractiveChild(e.getPoint())) {
            return;
        }
        if (rect == null)
            return;
        if (! isMousePressed)
            return;
        if (middlePanActive) {
            updateMiddlePan(e);
            return;
        }
        boolean left = SwingUtilities.isLeftMouseButton(e);
        Point p = e.getPoint();
        int px = Math.max(clip.x, Math.min(p.x, clip.x + clip.width));
        int py = p.y;

        if ((hiliteCue & BUTTON_FLAG) != 0) {
            // we hold a button, which is handled by getButtonXY()
            int button = getButtonXY(px, py);
            if ((hiliteCue & ~PRESSED_FLAG) == button) {
                // the mouse is (again) above the held button
                hiliteCue |= PRESSED_FLAG;
            } else {
                // the mouse is outside the held button
                hiliteCue &= ~PRESSED_FLAG;
            }
            repaint();
            return;
        }

        if (paintHeights) {
            int heightBoxTop = isAbsolutePitchViewEnabled() ? clip.y + TOP_LINE - 10 : TOP_LINE - 10;
            int heightBoxBottom = isAbsolutePitchViewEnabled()
                    ? getVerticalRenderHeight() - BOTTOM_BORDER
                    : clip.height - BOTTOM_BORDER;
            if (px < clip.x + heightBoxWidth && py > heightBoxTop && (py < heightBoxBottom)) {
                int maxY = isAbsolutePitchViewEnabled() ? getVerticalRenderHeight() : dim.height;
                if (py < 0)
                    py = 0;
                if (py > maxY)
                    py = maxY;
                int dy = getScalePitchFromY(py, hhPageMin);
                hiliteHeight = dy;
                repaint();
                if (hiliteHeight > 200)
                    return;
                long time = System.currentTimeMillis();
                if (time - lastMidiTime > 100) {
                    firePropertyChange("midi", null, isRelativePagePitchView() ? (hiliteHeight - 2) : hiliteHeight);
                    lastMidiTime = time;
                }
                return;
            }
        }

        if (hiliteCue == SLIDE && left) {
            if (slideX == px)
                return;
            Point vp = getViewPosition();
            int off = px - slideX;
            int oldpoff = px - vp.x;
            vp.x = vp.x - off;
            if (vp.x < 0)
                vp.x = 0;
            setViewPosition(vp);
            slideX = vp.x + oldpoff;
            if (playerPos < vp.x || playerPos > vp.x + clip.width) {
                int next = nextElement(vp.x);
                if (next >= 0) {
                    YassRow row = table.getRowAt(next);
                    if (!row.isNote() && next + 1 < table.getRowCount()) {
                        next = next + 1;
                        row = table.getRowAt(next);
                    }
                    if (row.isNote()) {
                        table.setRowSelectionInterval(next, next);
                        table.updatePlayerPosition();
                    }
                }
            }
            setPlayerPosition(-1);
            return;
        }
        int shiftRemainder = InputEvent.BUTTON1_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
        if ((e.getModifiersEx() & shiftRemainder) == shiftRemainder)
            shiftRemainder = 1;
        else
            shiftRemainder = 0;
        if (hiliteCue == MOVE_REMAINDER)
            shiftRemainder = 1;

        if (useSketching) {
            if (sketchStarted()) {
                addSketch(px, py);
                if (!detectSketch()) {
                    cancelSketch();
                } else {
                    repaint();
                    return;
                }
            }
        }

        if (hit < 0) {
            if (left && marqueeSelectActive) {
                updateMarqueeSelection(px, py);
                repaint();
                return;
            }
            if (left) {
                // playerPos = px;
                outPoint = px;
                outSelect = fromTimeline(outPoint);

                select.x = Math.min(inPoint, outPoint);
                select.y = 0;
                select.width = Math.abs(outPoint - inPoint);
                select.height = clip.height;
            } else {

                Point p2 = new Point((int) selectX, (int) selectY);
                SwingUtilities.convertPointFromScreen(p2,
                                                      YassSheet.this);

                select.x = Math.min(p2.getX(), px);
                select.y = Math.min(p2.getY(), py);
                select.width = Math.abs(p2.getX() - (double) px);
                select.height = Math.abs(p2.getY() - (double) py);
            }

            int n = rect.size();
            YassRectangle r;
            boolean any = false;
            for (int i = 0; i < n; i++) {
                r = rect.elementAt(i);
                if (r.intersects(select)) {
                    if (!any) {
                        if (!(e.isShiftDown() || e.isControlDown())) {
                            table.clearSelection();
                        }
                    }
                    if (!table.isRowSelected(i)) {
                        table.addRowSelectionInterval(i, i);
                    }
                    any = true;
                }
            }
            if (any) {
                table.updatePlayerPosition();
            } else {
                table.clearSelection();
                playerPos = Math.min(inPoint, outPoint);
                table.updatePlayerPosition();
            }

            repaint();
            return;
        }

        YassRectangle rr = rect.elementAt(hit);
        YassRow r = table.getRowAt(hit);
        if (dragMode == CENTER && r != null && r.isNote() && hasCenterDragPreview()) {
            updateCenterDragPreview(rr, px, py, rr.getPageMin());
            repaint();
            return;
        }

        long time = System.currentTimeMillis();
        if (time - lastDragTime < 60)
            return;
        lastDragTime = time;
        table.setPreventUndo(true);
        int pageMin = rr.getPageMin();
        int x;
        int dx;
        int y = py - dragOffsetY;
        int maxY = isAbsolutePitchViewEnabled() ? getVerticalRenderHeight() : dim.height;
        if (y < 0)
            y = 0;
        if (y > maxY)
            y = maxY;
        if (y < hSize)
            y = (int) -hSize;
        int dy = getDraggedPitchFromY(y, pageMin);

        if (isAbsolutePitchViewEnabled() && r != null && r.isNote()) {
            logAbsolutePitchDrag("handleMouseDragged", rr, r, py, y, dy);
        }
        if (rr.isType(YassRectangle.GAP)) {
            x = (int) ((px - dragOffsetXRatio * wSize));
            if (paintHeights)
                x -= heightBoxWidth;
            double gapres = x / wSize;
            double gap2 = gapres * 60 * 1000 / (4 * bpm);
            gap2 = Math.round(gap2 / 10) * 10;
            firePropertyChange("gap", null, (int) gap2);
            return;
        }
        if (rr.isType(YassRectangle.START)) {
            x = (int) ((px - dragOffsetXRatio * wSize));
            if (paintHeights)
                x -= heightBoxWidth;
            double valres = x / wSize;
            double val = valres * 60 * 1000 / (4 * bpm);
            val = Math.round(val / 10) * 10;
            firePropertyChange("start", null, (int) val);
            return;
        }
        if (rr.isType(YassRectangle.END)) {
            x = (int) ((px - dragOffsetXRatio * wSize));
            if (paintHeights)
                x -= heightBoxWidth;
            double valres = x / wSize;
            double val = valres * 60 * 1000 / (4 * bpm);
            val = Math.round(val / 10) * 10;
            table.clearSelection();
            // quick hack
            firePropertyChange("end", null, (int) val);
            table.clearSelection();
            return;
        }

        boolean isPageBreak = r.isPageBreak();
        if (!isPageBreak) {
            int oldy = r.getHeightInt();
            if (oldy != dy) {
                if (dragDir != HORIZONTAL) {
                    dragDir = VERTICAL;
                    firePropertyChange("relHeight", null, (dy - oldy));
                    return;
                }
            }
        }
        boolean isPageBreakMin = false;
        if (isPageBreak)
            isPageBreakMin = r.getBeatInt() == r.getSecondBeatInt();
        if (!isPageBreakMin && dragMode == RIGHT) {
            x = (int) (px - beatgap * wSize - 2 + wSize / 2);
            if (paintHeights)
                x -= heightBoxWidth;
            dx = (int) Math.round(x / wSize);
        } else {
            x = (int) (px - beatgap * wSize - 2 - dragOffsetXRatio * wSize);
            if (paintHeights)
                x -= heightBoxWidth;
            dx = (int) Math.round(x / wSize);
        }
        if (isPageBreakMin || dragMode == CENTER) {
            int oldx = r.getBeatInt();
            if (oldx != dx) {
                if (dragDir != VERTICAL) {
                    dragDir = HORIZONTAL;
                    if (shiftRemainder != 0) {
                        firePropertyChange("relBeatRemainder", null, (dx - oldx));
                    } else {
                        firePropertyChange("relBeat", null, (dx - oldx));
                    }
                }
            }
        } else if (dragMode == LEFT) {
            int oldx = r.getBeatInt();
            if (oldx != dx) {
                if (dragDir != VERTICAL) {
                    dragDir = HORIZONTAL;
                    firePropertyChange("relLeft", null, (dx - oldx));
                }
            }
        } else {
            // dragMode==RIGHT
            int oldx = 0;
            if (r.isNote())
                oldx = r.getBeatInt() + r.getLengthInt();
            else if (r.isPageBreak())
                oldx = r.getSecondBeatInt();
            if (oldx != dx) {
                if (dragDir != VERTICAL) {
                    dragDir = HORIZONTAL;
                    firePropertyChange("relRight", null, (dx - oldx));
                }
            }
        }
    }

    private void initMouseListener() {
        addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                hiliteAction = ACTION_NONE;
                if (! isMousePressed)
                    return;
                isMousePressed = false;

                if (table == null) {
                    return;
                }

                if (middlePanActive) {
                    stopMiddlePan();
                    return;
                }

                if (marqueeSelectActive) {
                    if (marqueeSelectMoved) {
                        commitMarqueeSelection(e.isShiftDown() || e.isControlDown(), e.isAltDown());
                        resetMarqueeSelection();
                        repaint();
                        return;
                    }
                    // Plain click (no drag) in empty grid: keep cursor positioning behavior.
                    resetMarqueeSelection();
                    if (SwingUtilities.isLeftMouseButton(e) && !e.isShiftDown() && !e.isControlDown() && !e.isAltDown()) {
                        if (isAbsolutePitchViewEnabled()) {
                            int candidate = findBestNoteRowAtTimelineX(e.getX(), e.getY());
                            if (candidate >= 0) {
                                applyPointSelection(candidate, e);
                                table.scrollRectToVisible(table.getCellRect(candidate, 0, true));
                                table.updatePlayerPosition();
                                inPoint = outPoint = playerPos;
                                inSelect = fromTimeline(inPoint);
                                outSelect = fromTimeline(outPoint);
                                repaint();
                                return;
                            }
                        }
                        table.clearSelection();
                        inPoint = outPoint = e.getX();
                        playerPos = e.getX();
                        setPlayerPosition(playerPos);
                        inSelect = fromTimeline(inPoint);
                        outSelect = fromTimeline(outPoint);
                    }
                    repaint();
                    return;
                }

                if (hasCenterDragPreview()) {
                    commitCenterDragPreview();
                }

                if (temporaryZoomOff) {
                    temporaryZoomOff = false;
                    YassTable.setZoomMode(YassTable.ZOOM_ONE);
                    table.zoomPage();
                }

                if (table.getPreventUndo()) {
                    table.setPreventUndo(false);
                    table.setSaved(false);
                    table.addUndo();
                    actions.updateActions();
                }
                if (actions != null) {
                    actions.showMessage(0);
                }

                /*
                 * if (table.getPreventZoom()) { table.setPreventZoom(false);
                 * table.zoomPage(); }
                 */
                if (hiliteCue == MOVE_REMAINDER) {
                    hiliteCue = UNDEFINED;
                }

                if ((hiliteCue & BUTTON_FLAG) != 0) {
                    switch (hiliteCue) {
                        case PREV_SLIDE_PRESSED:
                        case NEXT_SLIDE_PRESSED:
                            stopSlide();
                            break;
                        case PREV_PAGE_PRESSED:
                            SwingUtilities.invokeLater(() -> firePropertyChange("page", null, -1));
                            break;
                        case NEXT_PAGE_PRESSED:
                            SwingUtilities.invokeLater(() -> firePropertyChange("page", null, +1));
                            break;
                        case PLAY_NOTE_PRESSED:
                            SwingUtilities.invokeLater(() -> firePropertyChange("play", null, "start"));
                            break;
                        case PLAY_PAGE_PRESSED:
                            SwingUtilities.invokeLater(() -> firePropertyChange("play", null, "page"));
                            break;
                        case PLAY_BEFORE_PRESSED:
                            SwingUtilities.invokeLater(() -> firePropertyChange("play", null, "before"));
                            break;
                        case PLAY_NEXT_PRESSED:
                            SwingUtilities.invokeLater(() -> firePropertyChange("play", null, "next"));
                            break;
                    }
                    hiliteCue = UNDEFINED;
                    repaint();
                    return;
                }

                if (sketchStarted()) {
                    // firePropertyChange("play", null, "stop");
                    SwingUtilities.invokeLater(() -> {
                        int ok = executeSketch();
                        cancelSketch();
                        if (useSketchingPlayback) {
                            if (ok == 2) {
                                firePropertyChange("play", null, "start");
                            } else if (ok == 3) {
                                int i = table.getSelectionModel()
                                        .getMinSelectionIndex();
                                if (i >= 0) {
                                    YassRow r = table.getRowAt(i);
                                    if (r.isNote()) {
                                        int h = r.getHeightInt();
                                        firePropertyChange("midi", null, h);
                                        firePropertyChange("play", null,
                                                "start");
                                    }
                                }
                            }
                        }
                        repaint();
                    });

                } else {
                    repaint();
                }
                selectX = selectY = -1;
                resetMarqueeSelection();
                inPoint = outPoint = -1;
            }

            public void mouseClicked(MouseEvent e) {
                if (isPlaying() || isTemporaryStop()) {
                    clearCenterDragPreview(true);
                    firePropertyChange("play", null, "stop");
                    e.consume();
                    return;
                }

                int x = e.getX();
                int y = e.getY();

                int button = getButtonXY(x, y);
                if (button != UNDEFINED) {
                    // ignore this button click, it was handled in mouseReleased()
                    return;
                }

                boolean left = SwingUtilities.isLeftMouseButton(e);
                boolean twice = e.getClickCount() > 1;
                boolean one = e.getClickCount() == 1;

                if (!isAbsolutePitchViewEnabled()
                        && (y > clip.height - BOTTOM_BORDER + 20
                        || (y > 20 && y < TOP_LINE - 10))) {
                    if (!left || twice || one) {
                        //firePropertyChange("one", null, null);
                        return;
                    }
                }

                if (!twice) {
                    int timelineTop = clip.y;
                    int timelineBottom = clip.y + TOP_BORDER;
                    if (left && y >= timelineTop && y <= timelineBottom) {
                        setPlayerPosition(x);
                        repaint();
                        return;
                    }
                    if (!paintHeights) {
                        return;
                    }
                    if (e.getX() > clip.x + heightBoxWidth) {
                        return;
                    }
                    if (hiliteHeight > 200) {
                        return;
                    }
                    firePropertyChange("midi", null, isRelativePagePitchView() ? (hiliteHeight - 2) : hiliteHeight);
                    return;
                }
                table.selectLine();
            }

            public void mousePressed(MouseEvent e) {
                if (isMouseOverInteractiveChild(e.getPoint())) {
                    return;
                }
                if (equalsKeyMillis > 0) {
                    equalsKeyMillis = 0;
                    equalsDigits = "";
                }
                boolean left = SwingUtilities.isLeftMouseButton(e);
                boolean middle = SwingUtilities.isMiddleMouseButton(e);
                if (table == null)
                    return;
                if (!hasFocus()) {
                    requestFocusInWindow();
                    requestFocus();
                }
                if (isPlaying() || isTemporaryStop()) {
                    firePropertyChange("play", null, "stop");
                    e.consume();
                    return;
                }
                isMousePressed = true; // not while playing
                int x = e.getX();
                int y = e.getY();
                if (!isAbsolutePitchViewEnabled()
                        && YassTable.getZoomMode() == YassTable.ZOOM_ONE
                        && dragMode != SLIDE) {
                    temporaryZoomOff = true;
                    YassTable.setZoomMode(YassTable.ZOOM_MULTI);
                }
                setErrorMessage("");
                if (middle) {
                    startMiddlePan(e);
                    inPoint = outPoint = -1;
                    inSelect = outSelect = -1;
                    repaint();
                    return;
                }
                int heightBoxTop = isAbsolutePitchViewEnabled() ? clip.y + TOP_LINE - 10 : TOP_LINE - 10;
                int heightBoxBottom = isAbsolutePitchViewEnabled()
                        ? getVerticalRenderHeight() - BOTTOM_BORDER
                        : clip.height - BOTTOM_BORDER;
                if (paintHeights && (x < clip.x + heightBoxWidth && y > heightBoxTop
                        && (y < heightBoxBottom))) {
                    if (y < 0) {
                        y = 0;
                    }
                    int maxY = isAbsolutePitchViewEnabled() ? getVerticalRenderHeight() : dim.height;
                    if (y > maxY) {
                        y = maxY;
                    }

                    int dy;
                    dy = getScalePitchFromY(y, hhPageMin);
                    hiliteHeight = dy;
                    repaint();
                    return;

                }
                int button = getButtonXY(x, y);
                if (button != UNDEFINED) {
                    if (button == NEXT_SLIDE) {
                        startSlide(10);
                    } else if (button == PREV_SLIDE) {
                        startSlide(-10);
                    }
                    hiliteCue = button | PRESSED_FLAG;
                    repaint();
                    // no further processing, because the actual event will be handled in mouseReleased()
                    return;
                }
                YassRectangle r;
                if (hiliteCue == CUT) {
                    r = rect.elementAt(hilite);
                    table.clearSelection();
                    table.addRowSelectionInterval(hilite, hilite);
                    firePropertyChange("split", null, (e.getX() - r.x) / r.width);
                    hiliteCue = UNDEFINED;
                } else if (hiliteCue == JOIN_LEFT) {
                    r = rect.elementAt(hilite);
                    table.clearSelection();
                    table.addRowSelectionInterval(hilite, hilite);
                    firePropertyChange("joinLeft", null, (e.getX() - r.x));
                    hiliteCue = UNDEFINED;
                } else if (hiliteCue == JOIN_RIGHT) {
                    r = rect.elementAt(hilite);
                    table.clearSelection();
                    table.addRowSelectionInterval(hilite, hilite);
                    firePropertyChange("joinRight", null, (int) (e.getX() - r.x));
                    hiliteCue = UNDEFINED;
                } else if (hiliteCue == SNAPSHOT) {
                    hiliteCue = UNDEFINED;
                    createSnapshot();
                    repaint();
                    return;
                } else if (hiliteCue == MOVE_REMAINDER) {
                    hit = nextElement();
                    if (hit < 0)
                        return;
                    table.setRowSelectionInterval(hit, hit);
                    table.updatePlayerPosition();

                    dragMode = CENTER;
                    r = rect.elementAt(hit);
                    dragOffsetX = (int) (e.getX() - r.x);
                    dragOffsetY = (int) (e.getY() - r.y);
                    dragOffsetXRatio = dragOffsetX / wSize;
                    return;
                } else if (hiliteCue == SLIDE && left) {
                    YassTable t = getActiveTable();
                    if (isAbsolutePitchViewEnabled()) {
                        hiliteCue = UNDEFINED;
                        isMousePressed = false;
                        repaint();
                        return;
                    }
                    if (t != null && t.getMultiSize() == 1) {
                        YassTable.setZoomMode(YassTable.ZOOM_MULTI);
                        enablePan(false);
                        update();
                        repaint();
                    }
                    dragMode = SLIDE;
                    slideX = e.getX();
                    return;
                }
                YassRectangle next = null;
                hit = -1;
                selectX = selectY = -1;
                dragDir = UNDEFINED;
                dragOffsetX = dragOffsetY = 0;
                int i = 0;
                for (Enumeration<?> en = rect.elements(); en.hasMoreElements(); i++) {
                    if (next != null) {
                        r = next;
                        next = (YassRectangle) en.nextElement();
                    } else {
                        r = (YassRectangle) en.nextElement();
                    }
                    if (next == null) {
                        next = en.hasMoreElements() ? (YassRectangle) en.nextElement() : null;
                    }
                    if (r == null)
                        break;
                    if (r.isPageBreak()) {
                        if (x > r.x - 5 && x < r.x + 5
                            && y >= TOP_LINE) {
                            if (e.isAltDown()) {
                                if (table.isRowSelected(i)) {
                                    table.removeRowSelectionInterval(i, i);
                                    table.updatePlayerPosition();
                                }
                                repaint();
                                return;
                            }

                            hit = i;
                            dragOffsetX = (int) (e.getX() - r.x);
                            dragOffsetY = (int) (e.getY() - r.y);
                            dragOffsetXRatio = dragOffsetX / wSize;
                            dragMode = CENTER;
                            applyPointSelection(i, e);
                            table.scrollRectToVisible(table.getCellRect(i, 0, true));
                            repaint();
                            break;
                        }
                    } else if (r.contains(e.getPoint())) {
                        if (e.isAltDown()) {
                            if (table.isRowSelected(i)) {
                                table.removeRowSelectionInterval(i, i);
                                table.updatePlayerPosition();
                            }
                            repaint();
                            return;
                        }
                        // hiliteAction = ACTION_CONTROL_ALT;
                        hit = i;
                        dragOffsetX = (int) (e.getX() - r.x);
                        dragOffsetY = (int) (e.getY() - r.y);
                        dragOffsetXRatio = dragOffsetX / wSize;
                        int dragw = r.width > Math.max(wSize, 32) * 3
                                ? (int) Math.max(wSize, 32)
                                : (r.width > 72 ? 24 : (r.width > 48 ? 16 : 5));
                        if (Math.abs(r.x - x) < dragw && r.width > 20) {
                            dragMode = LEFT;
                        } else if (Math.abs(r.x + r.width - x) < dragw && r.width > 20) {
                            dragMode = RIGHT;
                        } else {
                            dragMode = CENTER;
                        }
                        applyPointSelection(i, e);
                        table.scrollRectToVisible(table.getCellRect(i, 0, true));
                        table.updatePlayerPosition();

                        inPoint = outPoint = playerPos;
                        inSelect = fromTimeline(inPoint);
                        outSelect = fromTimeline(outPoint);
                        if (r.hasType(YassRectangle.GAP)) {
                            temporaryZoomOff = false;
                        }
                        if (dragMode == CENTER) {
                            beginCenterDragPreview(i, r, table.getRowAt(i));
                        }
                        repaint();
                        break;
                    } else if (table.getMultiSize() > 1
                            && r.x < x
                            && (((next == null || next.isPageBreak() || next.hasType(YassRectangle.END)) && x < r.x + r.width) ||
                            (next != null && (!next.isPageBreak() && !next.hasType(YassRectangle.END)) && x < next.x))
                            && y > getStickyBandTopY() - 16
                            && y < getStickyBandTopY()) {
                        hiliteAction = ACTION_CONTROL_ALT;

                        hit = i;
                        dragOffsetX = (int) (e.getX() - r.x);
                        dragOffsetY = (int) (e.getY() - r.y);
                        dragOffsetXRatio = dragOffsetX / wSize;
                        dragMode = CENTER;

                        table.setRowSelectionInterval(i, i);
                        table.selectLine();
                        table.updatePlayerPosition();

                        inPoint = outPoint = playerPos;
                        inSelect = fromTimeline(inPoint);
                        outSelect = fromTimeline(outPoint);

                        repaint();
                        break;
                    }
                }
                if (hit < 0) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        Rectangle2D.Double selectedGroupHitArea = getSelectedGroupBounds(10);
                        if (selectedGroupHitArea != null && selectedGroupHitArea.contains(e.getX(), e.getY())) {
                            int anchorRow = findSelectedNoteRowNear(e.getX(), e.getY());
                            if (anchorRow >= 0) {
                                YassRectangle anchorRect = rect.elementAt(anchorRow);
                                YassRow anchorNote = table.getRowAt(anchorRow);
                                hit = anchorRow;
                                dragOffsetX = (int) (e.getX() - anchorRect.x);
                                dragOffsetY = (int) (e.getY() - anchorRect.y);
                                dragOffsetXRatio = dragOffsetX / wSize;
                                dragMode = CENTER;
                                applyPointSelection(anchorRow, e);
                                table.scrollRectToVisible(table.getCellRect(anchorRow, 0, true));
                                table.updatePlayerPosition();
                                inPoint = outPoint = playerPos;
                                inSelect = fromTimeline(inPoint);
                                outSelect = fromTimeline(outPoint);
                                beginCenterDragPreview(anchorRow, anchorRect, anchorNote);
                                repaint();
                                return;
                            }
                        }
                        if (isInMarqueeGridArea(e.getY())) {
                            if (!(e.isShiftDown() || e.isControlDown() || e.isAltDown())) {
                                table.clearSelection();
                            }
                            startMarqueeSelection(e);
                            repaint();
                            return;
                        }
                        if (isAbsolutePitchViewEnabled()) {
                            int candidate = findBestNoteRowAtTimelineX(e.getX(), e.getY());
                            if (candidate >= 0) {
                                if (e.isAltDown()) {
                                    if (table.isRowSelected(candidate)) {
                                        table.removeRowSelectionInterval(candidate, candidate);
                                    }
                                } else {
                                    applyPointSelection(candidate, e);
                                }
                                table.scrollRectToVisible(table.getCellRect(candidate, 0, true));
                                table.updatePlayerPosition();
                                inPoint = outPoint = playerPos;
                            } else if (!(e.isShiftDown() || e.isControlDown() || e.isAltDown())) {
                                table.clearSelection();
                                inPoint = outPoint = e.getX();
                                playerPos = Math.min(inPoint, outPoint);
                                setPlayerPosition(playerPos);
                            } else {
                                inPoint = outPoint = e.getX();
                            }

                            inSelect = fromTimeline(inPoint);
                            outSelect = fromTimeline(outPoint);
                            repaint();
                            return;
                        }
                        inPoint = outPoint = e.getX();
                        inSelect = fromTimeline(inPoint);
                        outSelect = fromTimeline(outPoint);

                        if (useSketching) {
                            startSketch();
                            addSketch(e.getX(), e.getY());
                            repaint();
                            return;
                        }

                        boolean any = false;
                        int k = 0;
                        for (Enumeration<?> en = rect.elements(); en.hasMoreElements(); k++) {
                            r = (YassRectangle) en.nextElement();
                            if (r.x <= e.getX() && e.getX() <= r.x + r.width) {
                                if (!any) {
                                    if (!(e.isShiftDown() || e.isControlDown())) {
                                        table.clearSelection();
                                    }
                                }
                                if (!table.isRowSelected(k)) {
                                    table.addRowSelectionInterval(k, k);
                                }
                                any = true;
                            }
                        }
                        if (any) {
                            table.updatePlayerPosition();
                        } else {
                            table.clearSelection();
                            playerPos = Math.min(inPoint, outPoint);
                            table.updatePlayerPosition();
                        }
                    } else {
                        inPoint = outPoint = -1;
                        inSelect = outSelect = -1;

                        Point p = (Point) e.getPoint().clone();
                        SwingUtilities.convertPointToScreen(p, YassSheet.this);
                        selectX = p.getX();
                        selectY = p.getY();
                        select.x = select.y = select.width = select.height = 0;
                    }
                    repaint();
                }
            }

            public void mouseEntered(MouseEvent e) {
                // LOGGER.info("sheet entered");
            }

            public void mouseExited(MouseEvent e) {
                stopMiddlePan();
                clearCenterDragPreview(true);
                resetMarqueeSelection();
                hilite = -1;
                hiliteHeight = 1000;
                repaint();
            }

        });
    }

    private boolean isInMarqueeGridArea(int y) {
        int gridTop = clip.y + TOP_LINE - 10;
        int gridBottom = getStickyBandTopY() - 16;
        return y >= gridTop && y <= gridBottom;
    }

    private void startMarqueeSelection(MouseEvent e) {
        marqueeSelectActive = true;
        marqueeSelectMoved = false;
        marqueeStartX = e.getX();
        marqueeStartY = e.getY();
        inPoint = outPoint = -1;
        inSelect = outSelect = -1;
        select.x = marqueeStartX;
        select.y = marqueeStartY;
        select.width = 0;
        select.height = 0;
    }

    private void updateMarqueeSelection(int x, int y) {
        int minX = Math.min(marqueeStartX, x);
        int minY = Math.min(marqueeStartY, y);
        int width = Math.abs(x - marqueeStartX);
        int height = Math.abs(y - marqueeStartY);
        select.x = minX;
        select.y = minY;
        select.width = width;
        select.height = height;
        if (width > 2 || height > 2) {
            marqueeSelectMoved = true;
        }
    }

    private boolean isMarqueeCrossingSelection() {
        return marqueeSelectActive && select.x < marqueeStartX;
    }

    private void commitMarqueeSelection(boolean additiveSelection, boolean subtractiveSelection) {
        if (!marqueeSelectActive || table == null || rect == null || !marqueeSelectMoved) {
            return;
        }
        boolean crossingSelection = isMarqueeCrossingSelection();
        if (!additiveSelection && !subtractiveSelection) {
            table.clearSelection();
        }
        boolean any = false;
        int rowIndex = 0;
        for (Enumeration<?> en = rect.elements(); en.hasMoreElements(); rowIndex++) {
            YassRectangle rowRect = (YassRectangle) en.nextElement();
            if (rowRect == null || rowRect.isPageBreak() || rowRect.isType(YassRectangle.GAP)
                    || rowRect.isType(YassRectangle.START) || rowRect.isType(YassRectangle.END)) {
                continue;
            }
            boolean matches = crossingSelection
                    ? rowRect.intersects(select)
                    : (rowRect.x >= select.x
                    && rowRect.x + rowRect.width <= select.x + select.width
                    && rowRect.y >= select.y
                    && rowRect.y + rowRect.height <= select.y + select.height);
            if (!matches) {
                continue;
            }
            if (subtractiveSelection) {
                if (table.isRowSelected(rowIndex)) {
                    table.removeRowSelectionInterval(rowIndex, rowIndex);
                    any = true;
                }
            } else {
                table.addRowSelectionInterval(rowIndex, rowIndex);
                any = true;
            }
        }
        if (any) {
            table.updatePlayerPosition();
        }
    }

    private void resetMarqueeSelection() {
        marqueeSelectActive = false;
        marqueeSelectMoved = false;
        select.x = select.y = select.width = select.height = 0;
    }

    private void startMiddlePan(MouseEvent e) {
        Point view = getViewPosition();
        middlePanActive = true;
        middlePanStartX = e.getX();
        middlePanStartY = e.getY();
        try {
            Point screen = e.getLocationOnScreen();
            middlePanStartScreenX = screen.x;
            middlePanStartScreenY = screen.y;
        } catch (IllegalComponentStateException ex) {
            middlePanStartScreenX = middlePanStartX;
            middlePanStartScreenY = middlePanStartY;
        }
        middlePanStartViewX = view.x;
        middlePanStartViewY = view.y;
        setCursor(panCursor != null ? panCursor : Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    }

    private void updateMiddlePan(MouseEvent e) {
        if (!middlePanActive) {
            return;
        }
        Container parent = getParent();
        if (!(parent instanceof JViewport viewport)) {
            return;
        }
        Point view = viewport.getViewPosition();
        Dimension extent = viewport.getExtentSize();
        Dimension pref = getPreferredSize();
        int dx;
        int dy;
        try {
            Point screen = e.getLocationOnScreen();
            dx = screen.x - middlePanStartScreenX;
            dy = screen.y - middlePanStartScreenY;
        } catch (IllegalComponentStateException ex) {
            dx = e.getX() - middlePanStartX;
            dy = e.getY() - middlePanStartY;
        }
        int targetX = middlePanStartViewX - (int) Math.round(dx * MIDDLE_PAN_HORIZONTAL_FACTOR);
        int targetY = middlePanStartViewY + (int) Math.round(dy * MIDDLE_PAN_VERTICAL_FACTOR);
        if (targetX < 0) {
            targetX = 0;
        }
        if (targetY < 0) {
            targetY = 0;
        }
        int maxX = Math.max(0, pref.width - extent.width);
        int maxY = Math.max(0, pref.height - extent.height);
        if (targetX > maxX) {
            targetX = maxX;
        }
        if (targetY > maxY) {
            targetY = maxY;
        }
        if (targetX != view.x || targetY != view.y) {
            setViewPosition(new Point(targetX, targetY));
        }
    }

    private void stopMiddlePan() {
        middlePanActive = false;
        if (table != null && rect != null && !isPlaying()) {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private void applyPointSelection(int rowIndex, MouseEvent e) {
        if (table == null || rowIndex < 0 || rowIndex >= table.getRowCount()) {
            return;
        }
        boolean additive = e.isShiftDown() || e.isControlDown();
        boolean subtractive = e.isAltDown();
        if (subtractive) {
            if (table.isRowSelected(rowIndex)) {
                table.removeRowSelectionInterval(rowIndex, rowIndex);
            }
            return;
        }
        if (additive) {
            table.addRowSelectionInterval(rowIndex, rowIndex);
        } else {
            // Keep existing multi-selection when the user starts dragging an already selected note.
            // This allows moving all selected notes together without requiring modifiers.
            if (table.isRowSelected(rowIndex) && table.getSelectedRowCount() > 1) {
                return;
            }
            table.setRowSelectionInterval(rowIndex, rowIndex);
        }
    }

    private int findBestNoteRowAtTimelineX(int x, int y) {
        if (rect == null || rect.isEmpty()) {
            return -1;
        }

        int bestRow = -1;
        int bestDistance = Integer.MAX_VALUE;
        int rowIndex = 0;
        for (Enumeration<?> en = rect.elements(); en.hasMoreElements(); rowIndex++) {
            YassRectangle r = (YassRectangle) en.nextElement();
            if (r == null || r.isPageBreak() || r.isType(YassRectangle.GAP)
                    || r.isType(YassRectangle.START) || r.isType(YassRectangle.END)) {
                continue;
            }
            if (x < r.x || x > r.x + r.width) {
                continue;
            }
            int centerY = (int) (r.y + r.height / 2.0);
            int distance = Math.abs(centerY - y);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestRow = rowIndex;
                if (distance == 0) {
                    break;
                }
            }
        }
        return bestRow;
    }

    private void initKeyListener() {
        addKeyListener(new KeyListener() {
            private long lastDigitMillis;
            private int lastDigit;

            public void keyTyped(KeyEvent e) {
                if (equalsKeyMillis > 0) {
                    e.consume();
                    return;
                }
                dispatch();
            }

            public void keyPressed(KeyEvent e) {
                if (isFocusInSongHeader()) {
                    return;
                }
                if (table == null) {
                    return;
                }
                if (actions.getMP3().isPlaying()) {
                    actions.interruptPlay();
                }
                char c = e.getKeyChar();
                int code = e.getKeyCode();
                int shift = KeyEvent.SHIFT_DOWN_MASK;
                int ctrl = KeyEvent.CTRL_DOWN_MASK;
                int alt = KeyEvent.ALT_DOWN_MASK;
                int ctrlShift = ctrl | shift;
                int ctrlShiftAlt = ctrlShift | alt;
                if (actions.isMidiEnabled() && actions.getKeyboardLayout().getPosition(code) >= 0) {
                    return;
                }
                if (equalsKeyMillis > 0) {
                    if (code == KeyEvent.VK_BACK_SPACE) {
                        equalsDigits = equalsDigits.substring(0,
                                equalsDigits.length() - 1);
                        repaint();
                    } else if (code == KeyEvent.VK_ESCAPE) {
                        equalsDigits = "";
                        equalsKeyMillis = 0;
                        repaint();
                    } else if (code == KeyEvent.VK_ENTER) {
                        if (!equalsDigits.isEmpty())
                            setCurrentLineTo(Integer.valueOf(equalsDigits)
                                    .intValue());
                        equalsDigits = "";
                        equalsKeyMillis = 0;
                        repaint();
                    } else if (c >= '0' && c <= '9') {
                        equalsDigits = equalsDigits + c;
                        repaint();
                    }
                    e.consume();
                    return;
                }

                if (e.isControlDown() && e.isAltDown() && c == KeyEvent.CHAR_UNDEFINED) {
                    setHiliteAction(ACTION_CONTROL_ALT);
                } else if (e.isControlDown() && c == KeyEvent.CHAR_UNDEFINED) {
                    setHiliteAction(ACTION_CONTROL);
                } else if (e.isAltDown() && c == KeyEvent.CHAR_UNDEFINED) {
                    setHiliteAction(ACTION_ALT);
                } else if (e.isShiftDown() && c == KeyEvent.CHAR_UNDEFINED) {
                    setHiliteAction(ACTION_CONTROL_ALT);
                }

                // 0=next_note, 1=prev_note, 2=page_down, 3=page_up
                if (code == keycodes[0] && !e.isControlDown() && !e.isAltDown()) {
                    table.nextBeat(false);
                    e.consume();
                    return;
                }
                if (code == keycodes[1] && !e.isControlDown() && !e.isAltDown()) {
                    table.prevBeat(false);
                    e.consume();
                    return;
                }
                if (code == keycodes[2] && !e.isControlDown() && !e.isAltDown()) {
                    table.gotoPage(1);
                    e.consume();
                    return;
                }
                if (code == keycodes[3] && !e.isControlDown() && !e.isAltDown()) {
                    table.gotoPage(-1);
                    e.consume();
                    return;
                }
                // 12=play, 13=play_page
                if (code == keycodes[12] && !e.isControlDown()
                        && !e.isAltDown()) {
                    if (e.isShiftDown()) {
                        firePropertyChange("play", null, "page");
                    } else {
                        firePropertyChange("play", null, "start");
                    }
                    e.consume();
                    return;
                }
                if (code == keycodes[13] && !e.isControlDown()
                        && !e.isAltDown()) {
                    Integer mode = e.isShiftDown() ? 1 : 0;
                    firePropertyChange("play", mode, "page");
                    e.consume();
                    return;
                }

                // 17=play_before, 18=play_next
                if (code == keycodes[17] && !e.isControlDown() && !e.isAltDown()) {
                    Integer mode = e.isShiftDown() ? 1 : 0;
                    firePropertyChange("play", mode, "before");
                    e.consume();
                    return;
                }
                if (code == keycodes[18] && !e.isControlDown() && !e.isAltDown()) {
                    Integer mode = e.isShiftDown() ? 1 : 0;
                    firePropertyChange("play", mode, "next");
                    e.consume();
                    return;
                }

                // 4=init, 5=init_next, 6=right, 7=left, 8=up, 9=down,
                // 10=lengthen, 11=shorten, 12=play, 13=play_page,
                // 14=scroll_left, 15=scroll_right, 16=one_page
                if (code == keycodes[14] && !e.isControlDown()
                        && !e.isAltDown()) {
                    slideLeft(e.isShiftDown() ? 50 : 10);
                    e.consume();
                    return;
                }
                if (code == keycodes[15] && !e.isControlDown()
                        && !e.isAltDown()) {
                    slideRight(e.isShiftDown() ? 50 : 10);
                    e.consume();
                    return;
                }

                if (code == keycodes[16] && !e.isControlDown()
                        && !e.isAltDown()) {
                    firePropertyChange("one", null, null);
                    e.consume();
                    return;
                }

                // 4=init, 5=init_next, 6=right, 7=left, 8=up, 9=down,
                // 10=lengthen, 11=shorten
                if ((code == keycodes[10] || code == keycodes[11])
                        && !e.isControlDown() && !e.isAltDown()) {
                    boolean lengthen = code == keycodes[10];
                    boolean changed = false;
                    int[] rows = table.getSelectedRows();
                    for (int next : rows) {
                        YassRow row = table.getRowAt(next);
                        if (!row.isNote()) {
                            continue;
                        }

                        int length = row.getLengthInt();
                        row.setLength(lengthen ? length + 1 : Math.max(1,
                                length - 1));
                        changed = true;
                    }
                    table.zoomPage();
                    table.updatePlayerPosition();
                    if (changed) {
                        table.addUndo();
                    }

                    e.consume();
                    SwingUtilities.invokeLater(() -> {
                        update();
                        repaint();
                        firePropertyChange("play", null, "start");
                    });
                    return;
                }

                if ((code == keycodes[6] || code == keycodes[7])
                        && !e.isControlDown() && !e.isAltDown()) {
                    boolean right = code == keycodes[6];
                    boolean changed = false;
                    int[] rows = table.getSelectedRows();
                    for (int next : rows) {
                        YassRow row = table.getRowAt(next);
                        if (!row.isNote()) {
                            continue;
                        }

                        int beat = row.getBeatInt();
                        row.setBeat(right ? beat + 1 : beat - 1);
                        changed = true;
                    }
                    table.updatePlayerPosition();
                    if (changed) {
                        table.addUndo();
                    }

                    e.consume();
                    SwingUtilities.invokeLater(() -> {
                        update();
                        repaint();
                        firePropertyChange("play", null, "start");
                    });
                    return;
                }

                // 4=init, 5=init_next, 6=right, 7=left, 8=up, 9=down,
                // 10=lengthen, 11=shorten
                if ((code == keycodes[8] || code == keycodes[9])
                        && !e.isControlDown() && !e.isAltDown()) {
                    boolean up = code == keycodes[8];
                    boolean changed = false;
                    int[] rows = table.getSelectedRows();
                    for (int next : rows) {
                        YassRow row = table.getRowAt(next);
                        if (!row.isNote()) {
                            continue;
                        }

                        int height = row.getHeightInt();
                        row.setHeight(up ? height + 1 : height - 1);
                        changed = true;
                    }
                    table.updatePlayerPosition();
                    if (changed) {
                        table.addUndo();
                    }

                    e.consume();
                    SwingUtilities.invokeLater(new Thread(() -> {
                        update();
                        repaint();
                        firePropertyChange("play", 2, "page");
                    }));
                    return;
                }

                if (Character.isDigit(c) && c != '0' && c != '9' && e.isAltDown() && !e.isControlDown()) {
                    String cstr = Character.toString(c);
                    long currentTime = System.currentTimeMillis();
                    if (currentTime < lastTime + 700) {
                        if (lastTimeString.length() < 3) {
                            cstr = lastTimeString + cstr;
                        }
                        lastTimeString = cstr;
                        try {
                            int n = Integer.parseInt(cstr);
                            table.gotoPageNumber(n);
                        } catch (Exception ignored) {
                        }
                    } else {
                        lastTimeString = cstr;
                        try {
                            int n = Integer.parseInt(cstr);
                            table.gotoPageNumber(n);
                        } catch (Exception ignored) {
                        }
                    }
                    lastTime = currentTime;
                    e.consume();
                    return;
                }

                // 4=init, 5=init_next, 6=right, 7=left, 8=up, 9=down,
                // 10=lengthen, 11=shorten, 12=play, 13=play_page,
                // 14=scroll_left, 15=scroll_right, 16=one_page
                if ((code == keycodes[4] || code == keycodes[5])
                        && !e.isControlDown() && !e.isAltDown()) {
                    boolean initCurrent = code == keycodes[4];
                    boolean changed = false;

                    if (initCurrent) {
                        int[] rows = table.getSelectedRows();
                        for (int next : rows) {
                            YassRow row = table.getRowAt(next);
                            if (!row.isNote()) {
                                continue;
                            }

                            YassRow row2 = table.getRowAt(next - 1);
                            if (!row2.isNote()) {
                                continue;
                            }
                            int beat = row2.getBeatInt();
                            int len = row2.getLengthInt();
                            row.setBeat(beat + len + 1);
                            row.setLength(1);
                            changed = true;
                        }
                        table.updatePlayerPosition();
                    } else {
                        int next = table.getSelectionModel()
                                .getMaxSelectionIndex();
                        YassRow row = table.getRowAt(next);
                        if (!row.isNote()) {
                            e.consume();
                            return;
                        }

                        int beat = row.getBeatInt();
                        int len = row.getLengthInt();
                        if (next + 1 >= table.getRowCount()) {
                            e.consume();
                            return;
                        }
                        YassRow row2 = table.getRowAt(next + 1);
                        if (!row2.isNote()) {
                            e.consume();
                            return;
                        }
                        row2.setBeat(beat + len + 1);
                        row2.setLength(1);
                        table.setRowSelectionInterval(next + 1, next + 1);
                        changed = true;
                        table.updatePlayerPosition();
                    }

                    if (changed) {
                        table.addUndo();
                    }

                    e.consume();
                    SwingUtilities.invokeLater(() -> {
                        update();
                        repaint();
                        firePropertyChange("play", null, "start");
                    });
                    return;
                }

                // change length of note, except 0 and 9 which are reserved for GAP
                if (Character.isDigit(c) && c != '0' && c != '9' && !e.isControlDown()) {
                    boolean changed = false;
                    String cstr = Character.toString(c);
                    int n = -1;
                    try {
                        n = Integer.parseInt(cstr);
                    } catch (Exception ignored) {
                    }

                    long currentMillis = System.currentTimeMillis();
                    if (currentMillis - lastDigitMillis < 500)
                        n = lastDigit * 10 + n;
                    lastDigitMillis = System.currentTimeMillis();
                    lastDigit = n;

                    int[] rows = table.getSelectedRows();
                    for (int next : rows) {
                        YassRow row = table.getRowAt(next);
                        if (!row.isNote()) {
                            continue;
                        }
                        row.setLength(n);
                        changed = true;
                    }

                    table.updatePlayerPosition();
                    if (changed) {
                        table.addUndo();
                    }

                    e.consume();
                    SwingUtilities.invokeLater(() -> {
                        update();
                        repaint();
                        firePropertyChange("play", null, "start");
                    });
                    return;
                } else if (c == '\'') {
                    table.toggleApostropheEnd();
                } else if (c == '~') {
                    table.toggleTildeStart();
                }
                dispatch();
            }

            public void keyReleased(KeyEvent e) {
                if (table == null)
                    return;
                if (!e.isControlDown() && !e.isAltDown() && !e.isShiftDown()) {
                    setHiliteAction(ACTION_NONE);
                } else if (!e.isControlDown() && e.isAltDown()) {
                    setHiliteAction(ACTION_ALT);
                } else if (e.isControlDown() && !e.isAltDown()) {
                    setHiliteAction(ACTION_CONTROL);
                } else if (e.isShiftDown()) {
                    setHiliteAction(ACTION_CONTROL_ALT);
                }

                if (!isPlaying) {
                    table.setPreventAutoCheck(false);
                    if (actions != null) {
                        actions.checkData(table, false, true);
                        actions.showMessage(0);
                    }
                }
                dispatch();
            }

            private void dispatch() {
                // if (table != null)
                // table.dispatchEvent(e);
            }
        });
    }

    /**
     * @brief Gets the button which is at the specific location
     * @param x x position in pixel
     * @param y y position in pixel
     * @return Button ID
     * @retval UNDEFINED if no button found
     */
    private int getButtonXY(int x, int y) {
        int sideButtonTop = getStickySideButtonTop();
        int sideButtonBottom = sideButtonTop + (BOTTOM_BORDER - 16);
        if (x < (clip.x + LEFT_BORDER + BUTTON_HIT_SLOP)
                && y > sideButtonTop - BUTTON_HIT_SLOP && y < sideButtonBottom + BUTTON_HIT_SLOP) {
            return PREV_PAGE;
        }
        if (x > (clip.x + clip.width - RIGHT_BORDER - BUTTON_HIT_SLOP)
                && y > sideButtonTop - BUTTON_HIT_SLOP && y < sideButtonBottom + BUTTON_HIT_SLOP) {
            return NEXT_PAGE;
        }
        if (x > (clip.x + LEFT_BORDER - BUTTON_HIT_SLOP)
                && x < (clip.x + LEFT_BORDER + LEFT_BORDER + BUTTON_HIT_SLOP)
                && y > sideButtonTop - BUTTON_HIT_SLOP && y < sideButtonBottom + BUTTON_HIT_SLOP) {
            return PREV_SLIDE;
        }
        if (x > (clip.x + clip.width - RIGHT_BORDER - RIGHT_BORDER - BUTTON_HIT_SLOP)
                && x < (clip.x + clip.width - RIGHT_BORDER + BUTTON_HIT_SLOP)
                && y > sideButtonTop - BUTTON_HIT_SLOP && y < sideButtonBottom + BUTTON_HIT_SLOP) {
            return NEXT_SLIDE;
        }
        if (showPlayerButtons && y > TOP_PLAYER_BUTTONS - BUTTON_HIT_SLOP && y < (TOP_PLAYER_BUTTONS + PLAYER_BUTTONS_HEIGHT + BUTTON_HIT_SLOP)) {
            if (x > (playerPos + PLAY_PAGE_X - BUTTON_HIT_SLOP) && x < (playerPos + PLAY_PAGE_X + PLAY_PAGE_W + BUTTON_HIT_SLOP)) {
                return PLAY_PAGE;
            }
            if (x > (playerPos + PLAY_BEFORE_X - BUTTON_HIT_SLOP) && x < (playerPos + PLAY_BEFORE_X + PLAY_BEFORE_W + BUTTON_HIT_SLOP)) {
                return PLAY_BEFORE;
            }
            if (x > (playerPos + PLAY_NOTE_X - BUTTON_HIT_SLOP) && x < (playerPos + PLAY_NOTE_X + PLAY_NOTE_W + BUTTON_HIT_SLOP)) {
                return PLAY_NOTE;
            }
            if (x > (playerPos + PLAY_NEXT_X - BUTTON_HIT_SLOP) && x < (playerPos + PLAY_NEXT_X + PLAY_NEXT_W + BUTTON_HIT_SLOP)) {
                return PLAY_NEXT;
            }
        }
        return UNDEFINED;
    }

    public int[] getKeyCodes() {
        return keycodes;
    }

    public void setLyricsWidth(int w) {
        lyricsWidth = w;
    }

    public void setLyricsVisible(boolean onoff) {
        lyricsVisible = onoff;
    }

    public void setDebugMemory(boolean onoff) {
        messageMemory = onoff;
    }

    public void setColors(Color[] c) {
        System.arraycopy(c, 0, colorSet, 0, colorSet.length);
    }

    public Color[] getColors() {
        return colorSet;
    }

    public void shadeNotes(boolean onoff) {
        noshade = !onoff;
    }

    public void setDarkMode(boolean onoff) {
        darkMode = onoff;
        BufferedImage bi = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        Graphics2D big = bi.createGraphics();
        big.setColor(darkMode ? dkGrayDarkMode : DK_GRAY);
        big.fillRect(0, 0, 4, 2);
        big.setColor(darkMode ? HI_GRAY_2_DARK_MODE : HI_GRAY_2);
        big.fillRect(0, 2, 4, 2);
        Rectangle rec = new Rectangle(0, 0, 4, 4);
        tex = new TexturePaint(bi, rec);

        int w = 16;
        int w2 = w / 2;
        BufferedImage im = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setColor(darkMode ? whiteDarkMode : Color.WHITE);
        g.fillRect(0, 0, w, w);
        g.setColor(darkMode ? HI_GRAY_2_DARK_MODE : HI_GRAY_2);
        g.fillRect(0, 0, w2, w2);
        g.fillRect(w2, w2, w2, w2);
        bgtex = new TexturePaint(im, new Rectangle(w, w));
        if (songHeader != null) {
            songHeader.applyTheme(darkMode);
        }
    }

    public void setAutoTrim(boolean onoff) {
        autoTrim = onoff;
    }

    public int getTopLine() {
        return TOP_LINE;
    }

    public void showArrows(boolean onoff) {
        showArrows = onoff;
    }

    public void showPlayerButtons(boolean onoff) {
        showPlayerButtons = onoff;
    }

    public void showText(boolean onoff) {
        BOTTOM_BORDER = onoff ? 56 : 10;
        showText = onoff;
    }

    public boolean showVideo() {
        return showVideo;
    }

    public boolean showBackground() {
        return showBackground;
    }

    public void showBackground(boolean onoff) {
        showBackground = onoff;
    }

    public void showVideo(boolean onoff) {
        showVideo = onoff;
    }

    private void addSketch(int x, int y) {
        if (sketch == null) {
            sketch = new Point[SKETCH_LENGTH];
        }
        if (sketch[sketchPos] == null) {
            sketch[sketchPos] = new Point();
        }

        sketch[sketchPos].setLocation(x, y);
        if (sketchPos < sketch.length - 1) {
            sketchPos++;
        }
    }

    private void startSketch() {
        sketchPos = dirPos = 0;
        sketchStartTime = System.currentTimeMillis();
        sketchStarted = true;
    }

    private void cancelSketch() {
        sketchStarted = false;
    }

    private boolean sketchStarted() {
        return sketchStarted;
    }

    public void enableSketching(boolean onoff, boolean playonoff) {
        useSketching = onoff;
        useSketchingPlayback = playonoff;
    }

    private boolean detectSketch() {
        if (sketchPos < 3) {
            return true;
        }

        long sketchEndTime = System.currentTimeMillis();

        long ms = sketchEndTime - sketchStartTime;
        boolean intime = ms < 300;
        if (!intime) {
            return false;
        }

        Rectangle r = new Rectangle(sketch[0]);
        for (int i = 1; i < sketchPos; i++) {
            r.add(sketch[i]);
        }

        boolean vertical = r.height < 36 && r.height > r.width && r.height > 2
                && r.width < 10;
        boolean horizontal = r.width < 36 && r.width > r.height && r.width > 2
                && r.height < 10;

        if (!horizontal && !vertical) {
            return false;
        }

        if (sketchDirs == null) {
            sketchDirs = new int[SKETCH_LENGTH];
        }
        sketchDirs[0] = vertical ? SKETCH_VERTICAL : SKETCH_HORIZONTAL;
        dirPos = 1;
        Point s = sketch[1];
        for (int i = 2; i < sketchPos; i++) {
            Point s1 = s;
            s = sketch[i];

            int dx = s.x - s1.x;

            int dy = s.y - s1.y;

            if (horizontal && Math.abs(dx) > Math.abs(dy)) {
                sketchDirs[dirPos] = dx > 0 ? SKETCH_RIGHT : SKETCH_LEFT;
                if (sketchDirs[dirPos] != sketchDirs[dirPos - 1]) {
                    dirPos++;
                }
            } else if (vertical && Math.abs(dy) > Math.abs(dx)) {
                sketchDirs[dirPos] = dy > 0 ? SKETCH_DOWN : SKETCH_UP;
                if (sketchDirs[dirPos] != sketchDirs[dirPos - 1]) {
                    dirPos++;
                }
            }
        }
        sketchDirs[dirPos] = SKETCH_NONE;

        /*
         * System.out.print("p.x"); for (int i = 0; i < sketchPos; i++) {
         * System.out.print(" " + sketch[i].x); } LOGGER.info();
         * System.out.print("p.y"); for (int i = 0; i < sketchPos; i++) {
         * System.out.print(" " + sketch[i].y); } LOGGER.info();
         * System.out.print("dir"); for (int i = 1; i < dirPos; i++) {
         * System.out.print(" " + sketchDirs[i]); } LOGGER.info();
         */
        return true;
    }

    public boolean compareWithGesture(int[] g1, int[] g2) {
        if (g1.length < g2.length) {
            return false;
        }

        int n = g2.length;
        for (int i = 0; i < n; i++) {
            if (g1[i] != g2[i]) {
                return false;
            }
        }
        return true;
    }

    private int executeSketch() {
        // LOGGER.info("execute");

        if (sketchDirs == null) {
            return 0;
        }
        if (dirPos < 2) {
            return 0;
        }
        if (table.getSelectedRows().length < 1) {
            return 0;
        }

        if (compareWithGesture(sketchDirs, GESTURE_RIGHT_LEFT_RIGHT)) {
            firePropertyChange("rollRight", null, 1);
            // LOGGER.info("gesture right-left-right");
            return 1;
        } else if (compareWithGesture(sketchDirs, GESTURE_LEFT_RIGHT_LEFT)) {
            firePropertyChange("rollLeft", null, -1);
            // LOGGER.info("gesture left-right-left");
            return 1;
        } else if (compareWithGesture(sketchDirs, GESTURE_UP_DOWN_UP)) {
            firePropertyChange("removePageBreak", null, 1);
            // LOGGER.info("gesture up-down-up");
            return 1;
        } else if (compareWithGesture(sketchDirs, GESTURE_DOWN_UP_DOWN)) {
            firePropertyChange("addPageBreak", null, 1);
            // LOGGER.info("gesture up-down-up");
            return 1;
        } else if (compareWithGesture(sketchDirs, GESTURE_RIGHT_LEFT)) {
            firePropertyChange("relRight", null, 1);
            // LOGGER.info("gesture right-left");
            return 2;
        } else if (compareWithGesture(sketchDirs, GESTURE_LEFT_RIGHT)) {
            firePropertyChange("relRight", null, -1);
            // LOGGER.info("gesture left-right");
            return 2;
        } else if (compareWithGesture(sketchDirs, GESTURE_UP_DOWN)) {
            firePropertyChange("join", null, 0.5d);
            // LOGGER.info("gesture up-down");
            return 1;
        } else if (compareWithGesture(sketchDirs, GESTURE_DOWN_UP)) {
            firePropertyChange("split", null, 0.5d);
            // LOGGER.info("gesture down-up");
            return 1;
        }

        if (compareWithGesture(sketchDirs, GESTURE_LEFT)) {
            firePropertyChange("relBeat", null, -1);
            table.updatePlayerPosition();
            // LOGGER.info("gesture left");
            return 2;
        } else if (compareWithGesture(sketchDirs, GESTURE_RIGHT)) {
            firePropertyChange("relBeat", null, 1);
            table.updatePlayerPosition();
            // LOGGER.info("gesture right");
            return 2;
        } else if (compareWithGesture(sketchDirs, GESTURE_UP)) {
            firePropertyChange("relHeight", null, 1);
            // LOGGER.info("gesture up");
            return 3;
        } else if (compareWithGesture(sketchDirs, GESTURE_DOWN)) {
            firePropertyChange("relHeight", null, -1);
            // LOGGER.info("gesture down");
            return 3;
        }
        return 0;
    }

    public void setMouseOver(boolean onoff) {
        mouseover = onoff;
    }

    public void setTemporaryStop(boolean onoff) {
        isTemporaryStop = onoff;
    }

    protected void setCurrentLineTo(int line) {
        table.setCurrentLineTo(line);
    }

    public boolean isSnapshotShown() {
        return paintSnapshot;
    }

    public void showSnapshot(boolean onoff) {
        paintSnapshot = onoff;
    }

    public void removeSnapshot() {
        snapshot = null;
    }

    public void makeSnapshot() {
        int i = table.getSelectionModel().getMinSelectionIndex();
        int j = table.getSelectionModel().getMaxSelectionIndex();
        if (i >= 0) {
            YassRow r = table.getRowAt(i);
            while (!r.isNote() && i <= j) {
                r = table.getRowAt(++i);
            }
            inSelect = fromTimeline(beatToTimeline(r.getBeatInt()));

            r = table.getRowAt(j);
            while (!r.isNote() && j > i) {
                r = table.getRowAt(--j);
            }
            outSelect = fromTimeline(beatToTimeline(r.getBeatInt()
                    + r.getLengthInt()));
            createSnapshot();
        } else {
            createSnapshot();
        }
    }

    public void createSnapshot() {
        inSnapshot = inSelect;
        outSnapshot = outSelect;

        int i = table.getSelectionModel().getMinSelectionIndex();
        int j = table.getSelectionModel().getMaxSelectionIndex();
        if (i < 0) {
            return;
        }

        int n = j - i + 1;
        snapshot = new Vector<>(n);
        snapshotRect = new Vector<>(n);
        int startx = -1;
        for (int k = i; k <= j; k++) {
            YassRow row = table.getRowAt(k);
            snapshot.addElement(row);

            YassRectangle r = rect.elementAt(k);
            r = (YassRectangle) r.clone();
            if (startx < 0) {
                startx = (int) r.x;
            }
            r.x -= startx;
            snapshotRect.addElement(r);
        }
    }

    public void addTable(YassTable t) {
        tables.addElement(t);
        rects.addElement(new Vector<>(3000, 1000));
    }

    public void removeTable(YassTable t) {
        int i = tables.indexOf(t);
        if (i >= 0) {
            tables.removeElementAt(i);
            rects.removeElementAt(i);
        }
    }

    public void setActiveTable(int i) {
        YassTable t = tables.elementAt(i);
        setActiveTable(t);
    }

    public BufferedImage getBackgroundImage() {
        return bgImage;
    }

    public void setBackgroundImage(BufferedImage i) {
        bgImage = i;
    }

    public YassTable getActiveTable() {
        return table;
    }

    public YassTable getTable(int track) {
        if (track < 0 || track >= tables.size())
            return null;
        return tables.elementAt(track);
    }

    public int getTableCount() {
        return tables.size();
    }

    public void setActiveTable(YassTable t) {
        table = t;
        if (table != null) {
            int k = tables.indexOf(table);
            rect = rects.elementAt(k);
        }
        init();
    }

    public void removeAll() {
        tables.clear();
        rects.clear();
        snapshot = null;
        rect = null;
        table = null;
        gap = 0;
        beatgap = 0;
        outgap = 0;
        bpm = 120;
        setDuration(-1);
        init();
        setZoom(80 * 60 / bpm);
    }

    public void setNoteLengthVisible(boolean onoff) {
        showNoteLength = onoff;
    }

    public void setNoteScaleVisible(boolean onoff) {
        showNoteScale = onoff;
    }

    public void setNoteBeatVisible(boolean onoff) {
        showNoteBeat = onoff;
    }

    public void setNoteHeightVisible(boolean onoff) {
        showNoteHeight = onoff;
    }

    public void setNoteHeightNumVisible(boolean onoff) {
        showNoteHeightNum = onoff;
    }

    public boolean isVisible(int i) {
        YassRectangle r = rect.elementAt(i);
        if (r == null || r.y < 0)
            return false;
        return r.x >= getLeftX() && r.x + r.width <= clip.x + clip.width - RIGHT_BORDER;
    }

    public void scrollRectToVisible(int i, int j) {
        int minx = Integer.MAX_VALUE;
        for (int k = i; k <= j; k++) {
            if (k >= table.getRowCount()) {
                return;
            }
            YassRectangle r = rect.elementAt(k);

            double x = r.x;
            if (r.isType(YassRectangle.HEADER) || r.isType(YassRectangle.START)) {
                x = paintHeights ? heightBoxWidth : 0;
            } else if (r.isType(YassRectangle.END)) {
                x = beatToTimeline(outgap);
            } else if (r.isType(YassRectangle.UNDEFINED)) {
                continue;
            } else if (r.y < 0) {
                continue;
            }

            minx = (int) Math.min(x, minx);
        }
        setLeftX(minx);
        autoCenterAbsolutePitchView(i, j);
    }

    public Point getViewPosition() {
        Container parent = getParent();
        if (parent instanceof JViewport viewport) {
            return viewport.getViewPosition();
        }
        return new Point(0, 0);
    }

    public void setViewPosition(Point p) {
        if(p==null)
        {
            return;
        }
        Container parent = getParent();
        if (!(parent instanceof JViewport viewport)) {
            return;
        }
        viewport.setViewPosition(p);
        clip = getClipBounds();
        if (verticalPitchViewMode == VerticalPitchViewMode.ABSOLUTE) {
            logAbsolutePitchViewState("setViewPosition x=" + p.x + " y=" + p.y);
            update();
        }
    }

    public int getLeftX() {
        int x = getViewPosition().x;
        if (paintHeights) {
            x += heightBoxWidth;
        }

        x += LEFT_BORDER;
        return x;
    }

    public void setLeftX(int x) {
        if (paintHeights) {
            x -= heightBoxWidth;
        }

        x -= LEFT_BORDER;

        setViewPosition(new Point(x, getViewPositionY()));
    }

    /**
     * Gets the clipBounds attribute of the YassSheet object
     *
     * @return The clipBounds value
     */
    public Rectangle getClipBounds() {
        return ((JViewport) getParent()).getViewRect();
    }

    /**
     * Gets the validateRoot attribute of the YassSheet object
     *
     * @return The validateRoot value
     */
    public boolean isValidateRoot() {
        return true;
    }

    /**
     * Description of the Method
     */
    public void revalidate() {
        super.revalidate();
    }

    /**
     * Description of the Method
     *
     * @param g Description of the Parameter
     */
    public void paint(Graphics g) {
        super.paint(g);
    }

    /**
     * Description of the Method
     *
     * @param g Description of the Parameter
     */
    public void paintChildren(Graphics g) {
        // This is the standard way to paint child components like SongHeader and YassLyrics.
        // It ensures they are interactive and repaint correctly.
        super.paintChildren(g);
    }

    /**
     * Description of the Method
     */
    public void repaint() {
        if (rect == null || rect.size() < 1) {
            return;
        }
        super.repaint();
    }

    /**
     * Description of the Method
     *
     * @param g Description of the Parameter
     */
    public void paintComponent(Graphics g) {
        // If the table is empty, just paint a gray background and stop.
        if (table == null || rect == null || rect.size() < 1) {
            Graphics2D g2d = (Graphics2D) g;
            g.setColor(darkMode ? hiGrayDarkMode : HI_GRAY);
            g2d.fillRect(0, 0, getWidth(), getHeight());
            return;
        }
        if (table == null || rect == null || rect.size() < 1) {
            return;
        }
        int currentViewportY = getViewPositionY();
        if (verticalPitchViewMode == VerticalPitchViewMode.ABSOLUTE && currentViewportY != lastAbsoluteViewportY) {
            lastAbsoluteViewportY = currentViewportY;
            update();
        } else if (verticalPitchViewMode != VerticalPitchViewMode.ABSOLUTE) {
            lastAbsoluteViewportY = Integer.MIN_VALUE;
        }

        if (hSize < 0 || (beatgap == 0 && gap != 0)) {
            update();
        }

        Graphics2D g2 = (Graphics2D) g;
        if (isPlaying()) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);
        }

        clip = getClipBounds();
        if (image == null || image.getWidth() != clip.width
                || image.getHeight() != clip.height) {
            // do not use INT_RGB for width>2000 (sun bug_id=5005969)
            // image = new BufferedImage(clip.width, clip.height,
            // BufferedImage.TYPE_INT_ARGB);

            image = g2.getDeviceConfiguration().createCompatibleImage(
                    clip.width, clip.height, Transparency.TRANSLUCENT);
            backVolImage = g2.getDeviceConfiguration()
                    .createCompatibleVolatileImage(clip.width, clip.height,
                            Transparency.OPAQUE);
            plainVolImage = g2.getDeviceConfiguration()
                    .createCompatibleVolatileImage(clip.width, clip.height,
                            Transparency.OPAQUE);
            imageChanged = true;
        }

        refreshImage();

        // http://weblogs.java.net/blog/chet/archive/2005/05/graphics_accele.html
        // http://weblogs.java.net/blog/chet/archive/2004/08/toolkitbuffered.html
        // http://today.java.net/pub/a/today/2004/11/12/graphics2d.html

        Graphics2D gb = getBackBuffer().createGraphics();
        gb.drawImage(getPlainBuffer(), 0, 0, null);
        if (getPlainBuffer().contentsLost()) {
            // setErrorMessage(bufferlost);
            revalidate();
        }

        paintText(gb);
        if (showText) {
            paintPlayerText(gb);
        }
        paintPlayerPosition(gb);
        if (!live) {
            paintStickyMarkers(gb);
        }
        if (showArrows && !live) {
            paintArrows(gb);
            paintSlideArrows(gb);
        }
        if (showPlayerButtons && !live) {
            paintPlayerButtons(gb);
        }
        if (!live) {
            paintSketch(gb);
        }
        gb.dispose();

         paintBackBuffer(g2);

          if (!live) {
              paintMessage(g2);
          }
      }

    /**
     * Gets the backBuffer attribute of the YassSheet object
     *
     * @return The backBuffer value
     */
    public VolatileImage getBackBuffer() {
        return backVolImage;
    }

    /**
     * Gets the plainBuffer attribute of the YassSheet object
     *
     * @return The plainBuffer value
     */
    public VolatileImage getPlainBuffer() {
        return plainVolImage;
    }

    /**
     * Description of the Method
     */
    public void refreshImage() {
        refreshing = true;
        LOGGER.finest("YassSheet.refreshImage size=" + getWidth() + "x" + getHeight()
                + " image=" + (image == null ? "null" : image.getWidth() + "x" + image.getHeight())
                + " clip=" + (clip == null ? "null" : clip.width + "x" + clip.height));

        if (image == null) {
            refreshing = false;
            repaint();
            return;
        }

        Graphics2D db = image.createGraphics();
        db.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        db.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        db.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        clip = getClipBounds();
        db.setTransform(identity);
        db.setClip(0, 0, clip.width, clip.height);
        db.translate(-clip.x, -clip.y);
        if (!imageChanged) {
            imageChanged = clip.x != imageX || clip.y != imageY;
        }

        paintEmptySheet(db);

        YassPlayer mp3 = actions != null ? actions.getMP3() : null;
        if (mp3 != null && mp3.hasAudio() && mp3.createWaveform()) {
            paintWaveform(db);
        }

        if (!showVideo() && !showBackground()) {
            paintBeatLines(db);
        }
        paintLines(db);
        if (!live) {
            paintBeats(db);
            paintInOut(db);
            // Avoid double-visualization ("ghost note") while dragging a note:
            // snapshot overlay and center-drag preview can overlap in single-selection drags.
            if (paintSnapshot && !hasCenterDragPreview()) {
                paintSnapshot(db);
            }
        }

        paintRectangles(db);
        if (paintHeights) {
            paintHeightBox(db);
        }

        paintVersionsText(db);

        if (messageMemory && !live) {
            db.setFont(font);
            int maxHeap = (int) (Runtime.getRuntime().maxMemory() / 1024 / 1024);
            int occHeap = (int) (Runtime.getRuntime().totalMemory() / 1024 / 1024);
            int freeHeap = (int) (Runtime.getRuntime().freeMemory() / 1024 / 1024);
            int usedHeap = occHeap - freeHeap;
            String info = usedHeap + " of " + maxHeap + "Mb in use" + ", "
                    + occHeap + "Mb reserved.";
            db.drawString(info, clip.x + 10, 40);
        }

        imageChanged = false;
        imageX = clip.x;
        imageY = clip.y;

        db.dispose();

        Graphics2D gc = backVolImage.createGraphics();
        gc.drawImage(image, 0, 0, null);
        gc.dispose();

        gc = plainVolImage.createGraphics();
        gc.drawImage(image, 0, 0, null);
        gc.dispose();

        refreshing = false;
    }

    /**
     * Description of the Method
     *
     * @param g Description of the Parameter
     */
    public synchronized void paintBackBuffer(Graphics2D g) {
        final int MAX_TRIES = 5;
        for (int i = 0; i < MAX_TRIES; i++) {

            // switch (backVolImage.validate(g.getDeviceConfiguration())) {
            switch (backVolImage.validate(getGraphicsConfiguration())) {
                case VolatileImage.IMAGE_INCOMPATIBLE:
                    backVolImage.flush();
                    backVolImage = null;
                    image.flush();
                    image = null;
                    plainVolImage.flush();
                    plainVolImage = null;
                    image = g.getDeviceConfiguration().createCompatibleImage(
                            clip.width, clip.height, Transparency.TRANSLUCENT);
                    backVolImage = g.getDeviceConfiguration()
                            .createCompatibleVolatileImage(clip.width, clip.height,
                                    Transparency.OPAQUE);
                    plainVolImage = g.getDeviceConfiguration()
                            .createCompatibleVolatileImage(clip.width, clip.height,
                                    Transparency.OPAQUE);
                    // backVolImage = createVolatileImage(clip.width, clip.height);
                case VolatileImage.IMAGE_RESTORED:
                    Graphics2D gc = backVolImage.createGraphics();
                    gc.drawImage(image, 0, 0, Color.WHITE, null);
                    gc.dispose();
                    break;
            }

            g.drawImage(backVolImage, clip.x, clip.y, this);
            if (!backVolImage.contentsLost()) {
                return;
            }
            LOGGER.info("contents lost (" + i + ")");
        }
        g.drawImage(image, clip.x, clip.y, clip.x + clip.width, clip.y
                + clip.height, 0, 0, clip.width, clip.height, Color.WHITE, this);
    }

    /**
     * Description of the Method
     *
     * @param g2 Description of the Parameter
     * @param x Description of the Parameter
     * @param y Description of the Parameter
     * @param w Description of the Parameter
     * @param h Description of the Parameter
     */
    public synchronized void paintBackBuffer(Graphics2D g2, int x, int y, int w,
                                             int h) {
        final int MAX_TRIES = 5;
        for (int i = 0; i < MAX_TRIES; i++) {

            // switch (backVolImage.validate(g.getDeviceConfiguration())) {
            switch (backVolImage.validate(getGraphicsConfiguration())) {
                case VolatileImage.IMAGE_INCOMPATIBLE:
                    backVolImage.flush();
                    backVolImage = null;
                    image.flush();
                    image = null;
                    plainVolImage.flush();
                    plainVolImage = null;
                    image = g2.getDeviceConfiguration().createCompatibleImage(
                            clip.width, clip.height, Transparency.TRANSLUCENT);
                    backVolImage = g2.getDeviceConfiguration()
                            .createCompatibleVolatileImage(clip.width, clip.height,
                                    Transparency.OPAQUE);
                    plainVolImage = g2.getDeviceConfiguration()
                            .createCompatibleVolatileImage(clip.width, clip.height,
                                    Transparency.OPAQUE);
                    // backVolImage = createVolatileImage(clip.width, clip.height);
                case VolatileImage.IMAGE_RESTORED:
                    Graphics2D gc = backVolImage.createGraphics();
                    gc.drawImage(image, 0, 0, Color.WHITE, null);
                    gc.dispose();
                    break;
            }

            g2.drawImage(backVolImage, clip.x + x, clip.y + y, clip.x + x + w,
                    clip.y + y + h, x, y, x + w, y + h, this);
            if (!backVolImage.contentsLost()) {
                return;
            }
            LOGGER.info("contents lost (" + i + ")");
        }
        g2.drawImage(image, clip.x, clip.y, clip.x + clip.width, clip.y
                + clip.height, 0, 0, clip.width, clip.height, Color.WHITE, this);
    }

    /**
     * Description of the Method
     *
     * @param onoff Description of the Parameter
     */
    public void previewEdit(boolean onoff) {
        actions.previewEdit(onoff);
    }

    /**
     * Description of the Method
     *
     * @param g2 Description of the Parameter
     */
    public void paintEmptySheet(Graphics2D g2) {
        BufferedImage img = null;
        if (videoFrame != null && showVideo) {
            img = videoFrame;
        }
        if (img == null && showBackground) {
            img = bgImage;
        }

        if (img != null) {
            int w = clip.width;
            int h = (int) (w * 3 / 4.0);
            int yy = clip.height / 2 - h / 2;

            g2.setColor(darkMode ? whiteDarkMode : Color.WHITE);
            g2.fillRect(clip.x, 0, clip.width, yy);
            g2.fillRect(clip.x, yy, clip.width, clip.height);

            g2.drawImage(img, clip.x, clip.y + yy, w, h, null);
        } else {
            g2.setColor(darkMode ? whiteDarkMode : Color.WHITE);
            g2.fillRect(clip.x, clip.y, clip.width, clip.height);

            // Keep the upper free sheet area plain so beat/time markers and the
            // cursor sit on the normal sheet background, but render the karaoke
            // text band at the bottom as a simple solid area instead of the old
            // checkerboard texture.
            g2.setColor(darkMode ? whiteDarkMode : Color.WHITE);
            if (live) {
                g2.fillRect(clip.x, getStickyBandTopY(),
                        clip.width, BOTTOM_BORDER + 16);
            } else {
                g2.fillRect(clip.x + LEFT_BORDER, getStickyBandTopY(), clip.width - LEFT_BORDER
                        - RIGHT_BORDER, BOTTOM_BORDER + 16);
            }
            g2.setColor(darkMode ? whiteDarkMode : Color.WHITE);
        }
    }

    /**
     * Description of the Method
     *
     * @param g2 Description of the Parameter
     */
    public void paintWaveform(Graphics2D g2) {
        YassPlayer mp3 = actions.getMP3();
        if (mp3.getAudioBytes() == null) {
            return;
        }
        paintStickyWaveform(g2, mp3);
        List<PitchDetector.PitchData> pitchDataList = mp3.getPitchDataList();
        String selectedAudioTag = songHeader != null ? songHeader.getSelectedAudio() : null;
        boolean vocalsSelected = UltrastarHeaderTag.VOCALS.toString().equals(selectedAudioTag);
        boolean usePitchWaveform = vocalsSelected
                && pitchDataList != null
                && !pitchDataList.isEmpty()
                && !actions.isRecording();

        if (usePitchWaveform) {
            PagePitchScope pagePitchScope = getVisiblePagePitchScope(pitchDataList);
            List<PitchDetector.PitchData> visiblePitchData = pagePitchScope.pitchData();
            if (visiblePitchData.isEmpty()) {
                visiblePitchData = pitchDataList;
            }

            int visibleMin = getCurrentVisiblePitchMin();
            int visibleMax = getCurrentVisiblePitchMax();
            int pitchTranspose = isAbsolutePitchViewEnabled() ? 0 : getWaveformPitchTranspose(pagePitchScope, visiblePitchData);
            int pitchRenderMin = pan ? visibleMin : getWaveformPitchRenderMin(visiblePitchData, pitchTranspose, visibleMin, visibleMax);
            mp3.setPitchWaveformTranspose(pitchTranspose);

            Color outerColor = darkMode ? new Color(11, 103, 95, 145) : new Color(11, 103, 95, 125);
            Color middleColor = darkMode ? new Color(0, 180, 216, 170) : new Color(17, 145, 133, 150);
            Color innerColor = darkMode ? new Color(144, 224, 239, 190) : new Color(147, 215, 208, 170);
            Color lineGlowColor = darkMode ? new Color(202, 240, 248, 150) : new Color(147, 215, 208, 140);
            Color lineCoreColor = darkMode ? new Color(202, 240, 248, 242) : new Color(11, 103, 95, 238);

            Stroke oldStroke = g2.getStroke();
            Color oldColor = g2.getColor();
            Path2D.Double pitchPath = new Path2D.Double();
            int pitchDataIndex = 0;
            boolean pathStarted = false;
            double smoothedYCenter = Double.NaN;
            double lineYCenter = Double.NaN;
            double lineAmplitudeThreshold = 1.8;

            for (int x = clip.x; x < clip.x + clip.width; x++) {
                double timeInSeconds = fromTimelineExact(x) / 1000.0;
                while (pitchDataIndex < pitchDataList.size() - 1 &&
                        pitchDataList.get(pitchDataIndex + 1).time() < timeInSeconds) {
                    pitchDataIndex++;
                }
                PitchDetector.PitchData currentPitch = pitchDataList.get(pitchDataIndex);
                int displayPitch = currentPitch.pitch() + pitchTranspose;
                double yCenter = mapPitchOverlayToY(displayPitch, pitchRenderMin);
                int amplitude = Math.abs(mp3.getWaveFormAtMillis(timeInSeconds * 1000));
                double amplitudeScaled = amplitude * uiScale;

                if (Double.isNaN(smoothedYCenter) || Math.abs(smoothedYCenter - yCenter) > hSize * 0.9) {
                    smoothedYCenter = yCenter;
                } else {
                    smoothedYCenter = smoothedYCenter * 0.45 + yCenter * 0.55;
                }

                if (Double.isNaN(lineYCenter)) {
                    lineYCenter = smoothedYCenter;
                } else if (Math.abs(lineYCenter - smoothedYCenter) > hSize * 2.6) {
                    lineYCenter = smoothedYCenter;
                    pathStarted = false;
                } else {
                    lineYCenter = lineYCenter * 0.82 + smoothedYCenter * 0.18;
                }

                PitchConfidence confidence = getPitchFrameConfidence(
                        pitchDataList, pitchDataIndex, amplitudeScaled, lineAmplitudeThreshold);
                if (confidence != PitchConfidence.LOW) {
                    if (!pathStarted) {
                        pitchPath.moveTo(x, lineYCenter);
                        pathStarted = true;
                    } else {
                        pitchPath.lineTo(x, lineYCenter);
                    }
                } else {
                    pathStarted = false;
                }

                int outerTop = (int) Math.round(smoothedYCenter - amplitudeScaled / 2.0);
                int outerBottom = (int) Math.round(smoothedYCenter + amplitudeScaled / 2.0);
                int middleTop = (int) Math.round(smoothedYCenter - amplitudeScaled * 0.34);
                int middleBottom = (int) Math.round(smoothedYCenter + amplitudeScaled * 0.34);
                int innerTop = (int) Math.round(smoothedYCenter - amplitudeScaled * 0.17);
                int innerBottom = (int) Math.round(smoothedYCenter + amplitudeScaled * 0.17);

                double alphaScale = getConfidenceAlphaScale(confidence);
                g2.setColor(withAlphaScale(outerColor, alphaScale));
                g2.drawLine(x, outerTop, x, outerBottom);
                g2.setColor(withAlphaScale(middleColor, alphaScale));
                g2.drawLine(x, middleTop, x, middleBottom);
                g2.setColor(withAlphaScale(innerColor, alphaScale));
                g2.drawLine(x, innerTop, x, innerBottom);
            }

            g2.setStroke(new BasicStroke(4.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(lineGlowColor);
            g2.draw(pitchPath);
            g2.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(lineCoreColor);
            g2.draw(pitchPath);
            g2.setStroke(oldStroke);
            g2.setColor(oldColor);

            if (actions.getProperties().getBooleanProperty("debug-raw-pitches")) {
                List<PitchDetector.PitchData> rawPitchData = mp3.getRawPitchDataList();
                if (rawPitchData != null && !rawPitchData.isEmpty()) {
                    paintRawPitchOverlay(g2, rawPitchData, pitchTranspose, pitchRenderMin);
                }
            }
        } else {
            paintRecordingPitchOverlay(g2, recordingOverlayPitchData);
        }
        g2.setColor(darkMode ? whiteDarkMode : Color.WHITE);
    }

    private void paintRawPitchOverlay(Graphics2D g2, List<PitchDetector.PitchData> rawPitchData,
                                      int pitchTranspose, int pitchRenderMin) {
        Stroke oldStroke = g2.getStroke();
        Color oldColor = g2.getColor();
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int rawIndex = 0;
        for (int x = clip.x; x < clip.x + clip.width; x++) {
            double timeInSeconds = fromTimelineExact(x) / 1000.0;
            while (rawIndex < rawPitchData.size() - 1 &&
                    rawPitchData.get(rawIndex + 1).time() < timeInSeconds) {
                rawIndex++;
            }
            PitchDetector.PitchData pd = rawPitchData.get(rawIndex);
            int displayPitch = pd.pitch() + pitchTranspose;
            double y = mapPitchOverlayToY(displayPitch, pitchRenderMin);

            int amplitude = Math.abs(actions.getMP3().getWaveFormAtMillis(timeInSeconds * 1000));
            double amplitudeScaled = amplitude * uiScale;
            if (amplitudeScaled < 1.8) {
                continue;
            }
            int alpha = (int) Math.min(200, Math.round(amplitudeScaled * 8));
            g2.setColor(new Color(220, 40, 40, alpha));
            int iy = (int) Math.round(y);
            g2.drawLine(x, iy - 1, x, iy + 1);
        }

        g2.setStroke(oldStroke);
        g2.setColor(oldColor);
    }

    private void paintRecordingPitchOverlay(Graphics2D g2, List<PitchDetector.PitchData> pitchDataList) {
        if (!actions.isRecording() || pitchDataList == null || pitchDataList.isEmpty()) {
            return;
        }
        PagePitchScope pagePitchScope = getVisiblePagePitchScope(pitchDataList);
        List<PitchDetector.PitchData> visiblePitchData = pagePitchScope.pitchData();
        if (visiblePitchData.isEmpty()) {
            visiblePitchData = pitchDataList;
        }

        int visibleMin = getCurrentVisiblePitchMin();
        int visibleMax = getCurrentVisiblePitchMax();
        int pitchTranspose = isAbsolutePitchViewEnabled() ? 0 : getWaveformPitchTranspose(pagePitchScope, visiblePitchData);
        int pitchRenderMin = pan ? visibleMin : getWaveformPitchRenderMin(visiblePitchData, pitchTranspose, visibleMin, visibleMax);

        Stroke oldStroke = g2.getStroke();
        Color oldColor = g2.getColor();
        Path2D.Double pitchPath = new Path2D.Double();
        int pitchDataIndex = 0;
        boolean pathStarted = false;
        double smoothedYCenter = Double.NaN;
        double lineYCenter = Double.NaN;
        double lineAmplitudeThreshold = 1.8;

        Color outerColor = darkMode ? new Color(11, 103, 95, 145) : new Color(11, 103, 95, 125);
        Color middleColor = darkMode ? new Color(0, 180, 216, 170) : new Color(17, 145, 133, 150);
        Color innerColor = darkMode ? new Color(144, 224, 239, 190) : new Color(147, 215, 208, 170);
        Color lineGlowColor = darkMode ? new Color(202, 240, 248, 150) : new Color(147, 215, 208, 140);
        Color lineCoreColor = darkMode ? new Color(202, 240, 248, 242) : new Color(11, 103, 95, 238);

        for (int x = clip.x; x < clip.x + clip.width; x++) {
            double timeInSeconds = fromTimelineExact(x) / 1000.0;
            while (pitchDataIndex < pitchDataList.size() - 1
                    && pitchDataList.get(pitchDataIndex + 1).time() < timeInSeconds) {
                pitchDataIndex++;
            }
            PitchDetector.PitchData currentPitch = pitchDataList.get(pitchDataIndex);
            int displayPitch = currentPitch.pitch() + pitchTranspose;
            double yCenter = mapPitchOverlayToY(displayPitch, pitchRenderMin);
            int amplitude = Math.abs(actions.getMP3().getWaveFormAtMillis(timeInSeconds * 1000));
            double amplitudeScaled = amplitude * uiScale;

            if (Double.isNaN(smoothedYCenter) || Math.abs(smoothedYCenter - yCenter) > hSize * 0.9) {
                smoothedYCenter = yCenter;
            } else {
                smoothedYCenter = smoothedYCenter * 0.45 + yCenter * 0.55;
            }

            if (Double.isNaN(lineYCenter)) {
                lineYCenter = smoothedYCenter;
            } else if (Math.abs(lineYCenter - smoothedYCenter) > hSize * 2.6) {
                lineYCenter = smoothedYCenter;
                pathStarted = false;
            } else {
                lineYCenter = lineYCenter * 0.82 + smoothedYCenter * 0.18;
            }

            PitchConfidence confidence = getPitchFrameConfidence(
                    pitchDataList, pitchDataIndex, amplitudeScaled, lineAmplitudeThreshold);
            if (confidence != PitchConfidence.LOW) {
                if (!pathStarted) {
                    pitchPath.moveTo(x, lineYCenter);
                    pathStarted = true;
                } else {
                    pitchPath.lineTo(x, lineYCenter);
                }
            } else {
                pathStarted = false;
            }

            int outerTop = (int) Math.round(smoothedYCenter - amplitudeScaled / 2.0);
            int outerBottom = (int) Math.round(smoothedYCenter + amplitudeScaled / 2.0);
            int middleTop = (int) Math.round(smoothedYCenter - amplitudeScaled * 0.34);
            int middleBottom = (int) Math.round(smoothedYCenter + amplitudeScaled * 0.34);
            int innerTop = (int) Math.round(smoothedYCenter - amplitudeScaled * 0.17);
            int innerBottom = (int) Math.round(smoothedYCenter + amplitudeScaled * 0.17);

            double alphaScale = getConfidenceAlphaScale(confidence);
            g2.setColor(withAlphaScale(outerColor, alphaScale));
            g2.drawLine(x, outerTop, x, outerBottom);
            g2.setColor(withAlphaScale(middleColor, alphaScale));
            g2.drawLine(x, middleTop, x, middleBottom);
            g2.setColor(withAlphaScale(innerColor, alphaScale));
            g2.drawLine(x, innerTop, x, innerBottom);
        }

        g2.setStroke(new BasicStroke(4.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(lineGlowColor);
        g2.draw(pitchPath);
        g2.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(lineCoreColor);
        g2.draw(pitchPath);

        g2.setStroke(oldStroke);
        g2.setColor(oldColor);
    }

    private RecordingPitchScale getRecordingPitchScale(List<PitchDetector.PitchData> pitchDataList,
                                                       int pitchTranspose,
                                                       int visibleMin,
                                                       int visibleMax) {
        final boolean absoluteMode = isAbsolutePitchViewEnabled();
        final int singableLow = absoluteMode ? -72 : -24;
        final int singableHigh = absoluteMode ? 72 : 24;
        java.util.List<Integer> inRangePitches = new java.util.ArrayList<>();
        for (PitchDetector.PitchData pitchData : pitchDataList) {
            int shifted = pitchData.pitch() + pitchTranspose;
            if (shifted >= singableLow && shifted <= singableHigh) {
                inRangePitches.add(shifted);
            }
        }
        int localMin;
        int localMax;
        if (inRangePitches.isEmpty()) {
            localMin = singableLow;
            localMax = singableHigh;
        } else {
            java.util.Collections.sort(inRangePitches);
            int lowIndex = (int) Math.floor((inRangePitches.size() - 1) * 0.10);
            int highIndex = (int) Math.ceil((inRangePitches.size() - 1) * 0.90);
            localMin = inRangePitches.get(Math.max(0, lowIndex));
            localMax = inRangePitches.get(Math.min(inRangePitches.size() - 1, highIndex));
        }
        if (localMax - localMin < 4) {
            int midpoint = (localMin + localMax) / 2;
            localMin = Math.max(singableLow, midpoint - 2);
            localMax = Math.min(singableHigh, midpoint + 2);
        }
        int padding = getPitchOverlayPadding();
        int renderHeight = getVerticalRenderHeight();
        double bottomY = renderHeight - BOTTOM_BORDER - padding * hSize;
        double topY = renderHeight - BOTTOM_BORDER - (visibleMax - visibleMin + padding) * hSize;
        return new RecordingPitchScale(singableLow, singableHigh, localMin, localMax, topY, bottomY);
    }

    private boolean hasRecordingOutOfRangeSupport(List<PitchDetector.PitchData> pitchDataList,
                                                  int pitchDataIndex,
                                                  int pitchTranspose,
                                                  int singableLow,
                                                  int singableHigh) {
        if (pitchDataList == null || pitchDataIndex < 0 || pitchDataIndex >= pitchDataList.size()) {
            return false;
        }
        PitchDetector.PitchData centerPitch = pitchDataList.get(pitchDataIndex);
        int centerPitchClass = Math.max(singableLow, Math.min(singableHigh, centerPitch.pitch() + pitchTranspose));
        double centerTime = centerPitch.time();
        int support = 0;
        int start = Math.max(0, pitchDataIndex - 3);
        int end = Math.min(pitchDataList.size() - 1, pitchDataIndex + 3);
        for (int i = start; i <= end; i++) {
            if (i == pitchDataIndex) {
                continue;
            }
            PitchDetector.PitchData neighbor = pitchDataList.get(i);
            if (Math.abs(neighbor.time() - centerTime) > 0.18) {
                continue;
            }
            int neighborPitchClass = Math.max(singableLow, Math.min(singableHigh, neighbor.pitch() + pitchTranspose));
            if (Math.abs(neighborPitchClass - centerPitchClass) <= 2) {
                support++;
                if (support >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    private double mapRecordingPitchToY(int displayPitch, RecordingPitchScale scale) {
        int clampedPitch = Math.max(scale.singableLow(), Math.min(scale.singableHigh(), displayPitch));
        if (scale.localMax() <= scale.localMin()) {
            return (scale.topY() + scale.bottomY()) / 2.0;
        }
        double ratio = (double) (clampedPitch - scale.localMin()) / (double) (scale.localMax() - scale.localMin());
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        return scale.bottomY() - ratio * (scale.bottomY() - scale.topY());
    }

    public void logSelectedNotePitchDistribution() {
        if (table == null || actions == null) {
            return;
        }
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= table.getRowCount()) {
            return;
        }
        YassRow selectedNote = table.getRowAt(selectedRow);
        if (selectedNote == null || !selectedNote.isNote()) {
            return;
        }
        YassPlayer mp3 = actions.getMP3();
        if (mp3 == null || !mp3.hasAudio()) {
            return;
        }
        List<PitchDetector.PitchData> processedPitchData = mp3.getPitchDataList();
        List<PitchDetector.PitchData> rawPitchData = mp3.getRawPitchDataList();
        List<PitchDetector.PitchData> locallyNormalizedPitchData = mp3.getLocallyNormalizedPitchDataList();
        List<PitchDetector.PitchData> viterbiPitchData = mp3.getViterbiPitchDataList();
        List<PitchDetector.PitchData> transposedPitchData = mp3.getTransposedPitchDataList();
        if ((processedPitchData == null || processedPitchData.isEmpty())
                && (rawPitchData == null || rawPitchData.isEmpty())
                && (locallyNormalizedPitchData == null || locallyNormalizedPitchData.isEmpty())
                && (viterbiPitchData == null || viterbiPitchData.isEmpty())
                && (transposedPitchData == null || transposedPitchData.isEmpty())) {
            return;
        }

        double startSeconds = table.beatToMs(selectedNote.getBeatInt()) / 1000.0;
        double endSeconds = table.beatToMs(selectedNote.getBeatInt() + selectedNote.getLengthInt()) / 1000.0;
        List<PitchDetector.PitchData> noteProcessedPitchData = processedPitchData == null
                ? java.util.Collections.emptyList()
                : processedPitchData.stream()
                .filter(pd -> pd.time() >= startSeconds && pd.time() <= endSeconds)
                .toList();
        List<PitchDetector.PitchData> noteRawPitchData = rawPitchData == null
                ? java.util.Collections.emptyList()
                : rawPitchData.stream()
                .filter(pd -> pd.time() >= startSeconds && pd.time() <= endSeconds)
                .toList();
        List<PitchDetector.PitchData> noteLocallyNormalizedPitchData = locallyNormalizedPitchData == null
                ? java.util.Collections.emptyList()
                : locallyNormalizedPitchData.stream()
                .filter(pd -> pd.time() >= startSeconds && pd.time() <= endSeconds)
                .toList();
        List<PitchDetector.PitchData> noteViterbiPitchData = viterbiPitchData == null
                ? java.util.Collections.emptyList()
                : viterbiPitchData.stream()
                .filter(pd -> pd.time() >= startSeconds && pd.time() <= endSeconds)
                .toList();
        List<PitchDetector.PitchData> noteTransposedPitchData = transposedPitchData == null
                ? java.util.Collections.emptyList()
                : transposedPitchData.stream()
                .filter(pd -> pd.time() >= startSeconds && pd.time() <= endSeconds)
                .toList();
        if (noteProcessedPitchData.isEmpty() && noteRawPitchData.isEmpty() && noteLocallyNormalizedPitchData.isEmpty() && noteViterbiPitchData.isEmpty() && noteTransposedPitchData.isEmpty()) {
            LOGGER.info("Selected note row " + selectedRow
                    + " beat " + selectedNote.getBeatInt()
                    + " len " + selectedNote.getLengthInt()
                    + " has no detected pitches.");
            return;
        }

        PagePitchScope pagePitchScope = getPitchScopeForRow(selectedRow,
                processedPitchData == null ? java.util.Collections.emptyList() : processedPitchData);
        int pitchTranspose = getWaveformPitchTranspose(pagePitchScope,
                pagePitchScope.pitchData().isEmpty() ? noteProcessedPitchData : pagePitchScope.pitchData());

        String rawDistribution = formatRawPitchDistribution(noteRawPitchData);
        String locallyNormalizedDistribution = formatPitchDistribution(noteLocallyNormalizedPitchData, 0);
        String viterbiDistribution = formatPitchDistribution(noteViterbiPitchData, 0);
        String transposedDistribution = formatPitchDistribution(noteTransposedPitchData, 0);
        String displayedDistribution = formatPitchDistribution(noteProcessedPitchData, pitchTranspose);

        LOGGER.info("Selected note row " + selectedRow
                + " beat " + selectedNote.getBeatInt()
                + " len " + selectedNote.getLengthInt()
                + " text=\"" + selectedNote.getTrimmedText() + "\""
                + " rawPitches=[" + rawDistribution + "]"
                + " afterLocalOctaveNormalization=[" + locallyNormalizedDistribution + "]"
                + " afterViterbi=[" + viterbiDistribution + "]"
                + " afterGlobalTranspose=[" + transposedDistribution + "]"
                + " displayedPitches=[" + displayedDistribution + "]"
                + " transpose=" + pitchTranspose);
    }

    private PitchConfidence getPitchFrameConfidence(List<PitchDetector.PitchData> pitchDataList,
                                                    int pitchDataIndex,
                                                    double amplitudeScaled,
                                                    double lineAmplitudeThreshold) {
        if (pitchDataList == null || pitchDataList.isEmpty() || pitchDataIndex < 0 || pitchDataIndex >= pitchDataList.size()) {
            return PitchConfidence.LOW;
        }
        if (amplitudeScaled < lineAmplitudeThreshold) {
            return PitchConfidence.LOW;
        }

        PitchDetector.PitchData current = pitchDataList.get(pitchDataIndex);
        List<Integer> neighborhood = new ArrayList<>();
        for (int i = Math.max(0, pitchDataIndex - 3); i <= Math.min(pitchDataList.size() - 1, pitchDataIndex + 3); i++) {
            PitchDetector.PitchData candidate = pitchDataList.get(i);
            if (Math.abs(candidate.time() - current.time()) <= 0.18f) {
                neighborhood.add(candidate.pitch());
            }
        }
        if (neighborhood.size() < 3) {
            if (amplitudeScaled >= lineAmplitudeThreshold * 2.2) {
                return PitchConfidence.HIGH;
            }
            return amplitudeScaled >= lineAmplitudeThreshold * 1.6 ? PitchConfidence.MEDIUM : PitchConfidence.LOW;
        }
        java.util.Collections.sort(neighborhood);
        int medianPitch = neighborhood.get(neighborhood.size() / 2);
        int distance = Math.abs(current.pitch() - medianPitch);
        if (distance <= 1 && amplitudeScaled >= lineAmplitudeThreshold * 1.3) {
            return PitchConfidence.HIGH;
        }
        if (distance <= 2) {
            return PitchConfidence.MEDIUM;
        }
        return PitchConfidence.LOW;
    }

    private boolean isMelodicFrame(List<PitchDetector.PitchData> pitchDataList,
                                   int pitchDataIndex,
                                   double amplitudeScaled,
                                   double lineAmplitudeThreshold) {
        return getPitchFrameConfidence(pitchDataList, pitchDataIndex, amplitudeScaled, lineAmplitudeThreshold)
                != PitchConfidence.LOW;
    }

    private String formatRawPitchDistribution(List<PitchDetector.PitchData> pitchDataList) {
        if (pitchDataList == null || pitchDataList.isEmpty()) {
            return "-";
        }
        java.util.Map<String, Integer> pitchHistogram = new java.util.LinkedHashMap<>();
        for (PitchDetector.PitchData pitchData : pitchDataList) {
            String label = formatRawPitchLabel(pitchData.rawFrequency(), pitchData.pitch());
            pitchHistogram.put(label, pitchHistogram.getOrDefault(label, 0) + 1);
        }
        int total = pitchDataList.size();
        return pitchHistogram.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                .map(entry -> Math.round(entry.getValue() * 1000.0 / total) / 10.0 + "% " + entry.getKey())
                .reduce((left, right) -> left + ", " + right)
                .orElse("-");
    }

    private String formatRawPitchLabel(double frequency, int fallbackPitch) {
        if (frequency <= 0) {
            return formatPitchName(fallbackPitch);
        }
        double midi = 69 + 12 * (Math.log(frequency / 440.0) / Math.log(2.0));
        int nearestMidi = (int) Math.round(midi);
        int cents = (int) Math.round((midi - nearestMidi) * 100);
        int roundedCents = 25 * Math.round(cents / 25.0f);
        int octave = nearestMidi / 12 - 1;
        String note = getNoteName(normalizeNoteHeight(nearestMidi)) + octave;
        String centsLabel = roundedCents == 0 ? "" : String.format("(%+dc)", roundedCents);
        return note + centsLabel;
    }

    private String formatPitchDistribution(List<PitchDetector.PitchData> pitchDataList, int transpose) {
        if (pitchDataList == null || pitchDataList.isEmpty()) {
            return "-";
        }
        java.util.Map<String, Integer> pitchHistogram = new java.util.LinkedHashMap<>();
        for (PitchDetector.PitchData pitchData : pitchDataList) {
            String noteName = formatPitchName(pitchData.pitch() + transpose);
            pitchHistogram.put(noteName, pitchHistogram.getOrDefault(noteName, 0) + 1);
        }
        int total = pitchDataList.size();
        return pitchHistogram.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                .map(entry -> Math.round(entry.getValue() * 1000.0 / total) / 10.0 + "% " + entry.getKey())
                .reduce((left, right) -> left + ", " + right)
                .orElse("-");
    }

    private String formatPitchName(int pitch) {
        int midi = pitch + 60;
        int octave = midi / 12 - 1;
        return getNoteName(normalizeNoteHeight(midi)) + octave;
    }

    private PagePitchScope getPitchScopeForRow(int noteRow, List<PitchDetector.PitchData> pitchDataList) {
        if (table == null || pitchDataList == null || pitchDataList.isEmpty() || noteRow < 0 || noteRow >= table.getRowCount()) {
            return new PagePitchScope(-1, -1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, java.util.Collections.emptyList());
        }
        int startRow = noteRow;
        int pageNumber = 1;
        for (int i = 0; i < noteRow; i++) {
            if (table.getRowAt(i).isPageBreak()) {
                pageNumber++;
            }
        }
        while (startRow > 0 && !table.getRowAt(startRow - 1).isPageBreak()) {
            startRow--;
        }
        int endRow = noteRow;
        while (endRow < table.getRowCount() - 1 && !table.getRowAt(endRow + 1).isPageBreak() && !table.getRowAt(endRow + 1).isEnd()) {
            endRow++;
        }

        int startBeat = table.getRowAt(startRow).isNote() ? table.getRowAt(startRow).getBeatInt() : 0;
        int endBeat = table.getRowAt(endRow).isNote()
                ? table.getRowAt(endRow).getBeatInt() + table.getRowAt(endRow).getLengthInt()
                : startBeat + 1;
        if (endRow + 1 < table.getRowCount() && table.getRowAt(endRow + 1).isPageBreak()) {
            endBeat = table.getRowAt(endRow + 1).getBeatInt();
        }
        double startSeconds = table.beatToMs(startBeat) / 1000.0;
        double endSeconds = table.beatToMs(endBeat) / 1000.0;
        List<PitchDetector.PitchData> pagePitchData = pitchDataList.stream()
                .filter(pd -> pd.time() >= startSeconds && pd.time() <= endSeconds)
                .toList();
        int noteMin = Integer.MAX_VALUE;
        int noteMax = Integer.MIN_VALUE;
        for (int row = startRow; row <= endRow; row++) {
            YassRow currentRow = table.getRowAt(row);
            if (currentRow != null && currentRow.isNote()) {
                noteMin = Math.min(noteMin, currentRow.getHeightInt());
                noteMax = Math.max(noteMax, currentRow.getHeightInt());
            }
        }
        return new PagePitchScope(pageNumber, startBeat, endBeat, noteMin, noteMax, pagePitchData);
    }

    private PagePitchScope getVisiblePagePitchScope(List<PitchDetector.PitchData> pitchDataList) {
        if (table == null || pitchDataList == null || pitchDataList.isEmpty()) {
            return new PagePitchScope(-1, -1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, java.util.Collections.emptyList());
        }
        int noteRow = firstVisibleNote();
        if (noteRow < 0 || noteRow >= table.getRowCount()) {
            return new PagePitchScope(-1, -1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, java.util.Collections.emptyList());
        }
        return getPitchScopeForRow(noteRow, pitchDataList);
    }

    private int getWaveformPitchTranspose(PagePitchScope scope, List<PitchDetector.PitchData> pitchDataList) {
        if (pitchDataList == null || pitchDataList.isEmpty()) {
            return 0;
        }

        final int targetLow = -3;  // A3
        final int targetHigh = 16; // E5
        final int targetMid = 6;   // A#4/Bb4 midpoint-ish

        int noteMin = scope != null ? scope.noteMin() : Integer.MAX_VALUE;
        int noteMax = scope != null ? scope.noteMax() : Integer.MIN_VALUE;

        int bestOffset = 0;
        int bestHits = Integer.MIN_VALUE;
        int bestCenterDistance = Integer.MAX_VALUE;
        int bestNoteDistance = Integer.MAX_VALUE;
        int noteCenter = noteMin == Integer.MAX_VALUE ? targetMid : (noteMin + noteMax) / 2;
        for (int offset = -48; offset <= 48; offset += 12) {
            int hits = 0;
            int distanceSum = 0;
            int noteDistanceSum = 0;
            for (PitchDetector.PitchData pitchData : pitchDataList) {
                int shifted = pitchData.pitch() + offset;
                if (shifted >= targetLow && shifted <= targetHigh) {
                    hits++;
                }
                distanceSum += Math.abs(shifted - targetMid);
                noteDistanceSum += Math.abs(shifted - noteCenter);
            }
            if (hits > bestHits
                    || (hits == bestHits && distanceSum < bestCenterDistance)
                    || (hits == bestHits && distanceSum == bestCenterDistance && noteDistanceSum < bestNoteDistance)) {
                bestOffset = offset;
                bestHits = hits;
                bestCenterDistance = distanceSum;
                bestNoteDistance = noteDistanceSum;
            }
        }
        return bestOffset;
    }

    private int getWaveformPitchRenderMin(List<PitchDetector.PitchData> pitchDataList,
                                          int pitchTranspose,
                                          int visibleMin,
                                          int visibleMax) {
        final int targetLow = 0;
        int pitchMin = Integer.MAX_VALUE;
        int pitchMax = Integer.MIN_VALUE;
        for (PitchDetector.PitchData pitchData : pitchDataList) {
            int shifted = pitchData.pitch() + pitchTranspose;
            pitchMin = Math.min(pitchMin, shifted);
            pitchMax = Math.max(pitchMax, shifted);
        }
        if (pitchMin == Integer.MAX_VALUE) {
            return visibleMin;
        }
        int contentMin = Math.min(targetLow, pitchMin - 2);
        int contentMax = Math.max(targetLow + 18, pitchMax + 2);
        int visibleSpan = Math.max(19, visibleMax - visibleMin);
        int minAllowed = Math.min(visibleMin, visibleMax - visibleSpan);
        int maxAllowed = Math.max(visibleMin, visibleMax - visibleSpan);
        int preferredMin = Math.min(contentMin, targetLow);
        if (contentMax - preferredMin > visibleSpan) {
            preferredMin = contentMax - visibleSpan;
        }
        return Math.max(minAllowed, Math.min(preferredMin, maxAllowed));
    }

    private record PagePitchScope(int pageNumber, int startBeat, int endBeat, int noteMin, int noteMax, List<PitchDetector.PitchData> pitchData) {
    }

    private record RecordingPitchScale(int singableLow, int singableHigh, int localMin, int localMax,
                                       double topY, double bottomY) {
    }

    private enum PitchConfidence {
        LOW,
        MEDIUM,
        HIGH
    }

    private Color withAlphaScale(Color base, double scale) {
        int alpha = (int) Math.round(base.getAlpha() * scale);
        alpha = Math.max(0, Math.min(255, alpha));
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }

    private double getConfidenceAlphaScale(PitchConfidence confidence) {
        return switch (confidence) {
            case HIGH -> 1.0;
            case MEDIUM -> 0.68;
            case LOW -> 0.38;
        };
    }

    public void paintArrows(Graphics2D g2) {

        int x = 0;
        int y = getStickySideButtonTop() - clip.y;
        int w = LEFT_BORDER;
        int h = BOTTOM_BORDER - 16;

        Color fg = darkMode ? hiGrayDarkMode : HI_GRAY;
        Color sh = darkMode ? HI_GRAY_2_DARK_MODE : HI_GRAY_2;
        Color wt = darkMode ? whiteDarkMode : Color.WHITE;
        Color arr = darkMode ? arrowDarkMode : arrow;

        boolean isPressed = hiliteCue == PREV_PAGE_PRESSED;
        paintButton(g2, x, y, w, h, isPressed, fg, sh, wt, arr);

        boolean isEnabled = hiliteCue == PREV_PAGE
                || hiliteCue == PREV_PAGE_PRESSED;
        YassUtils.paintTriangle(g2, x + 10, y + 14, w / 3, YassUtils.NORTH,
                isEnabled, fg, sh, wt);

        x = clip.width - RIGHT_BORDER;
        y = getStickySideButtonTop() - clip.y;
        w = RIGHT_BORDER;
        h = BOTTOM_BORDER - 16;

        isPressed = hiliteCue == NEXT_PAGE_PRESSED;
        paintButton(g2, x, y, w, h, isPressed, fg, sh, wt, arr);

        isEnabled = hiliteCue == NEXT_PAGE || hiliteCue == NEXT_PAGE_PRESSED;
        YassUtils.paintTriangle(g2, x + 10, y + 14, w / 3, YassUtils.SOUTH,
                isEnabled, fg, sh, wt);
    }
    /**
     * Description of the Method
     *
     * @param g2 Description of the Parameter
     */
    public void paintSlideArrows(Graphics2D g2) {

        int x = LEFT_BORDER;
        int y = getStickySideButtonTop() - clip.y;
        int w = LEFT_BORDER;
        int h = BOTTOM_BORDER - 16;

        Color fg = darkMode ? hiGrayDarkMode : HI_GRAY;
        Color sh = darkMode ? HI_GRAY_2_DARK_MODE : HI_GRAY_2;
        Color wt = darkMode ? whiteDarkMode : Color.WHITE;
        Color arr = darkMode ? arrowDarkMode : arrow;

        boolean isPressed = hiliteCue == PREV_SLIDE_PRESSED;
        paintButton(g2, x, y, w, h, isPressed, fg, sh, wt, arr);

        boolean isEnabled = hiliteCue == PREV_SLIDE
                || hiliteCue == PREV_SLIDE_PRESSED;
        YassUtils.paintTriangle(g2, x + 10, y + 14, w / 3, YassUtils.WEST,
                isEnabled, fg, sh, wt);

        x = clip.width - RIGHT_BORDER - RIGHT_BORDER;
        y = getStickySideButtonTop() - clip.y;
        w = RIGHT_BORDER;
        h = BOTTOM_BORDER - 16;

        isPressed = hiliteCue == NEXT_SLIDE_PRESSED;
        paintButton(g2, x, y, w, h, isPressed, fg, sh, wt, arr);

        isEnabled = hiliteCue == NEXT_SLIDE || hiliteCue == NEXT_SLIDE_PRESSED;
        YassUtils.paintTriangle(g2, x + 10, y + 14, w / 3, YassUtils.EAST,
                isEnabled, fg, sh, wt);
    }

    /**
     * Description of the Method
     *
     * @param g2 Description of the Parameter
     */
    public void paintPlayerButtons(Graphics2D g2) {
        // if (!paintArrows) return;

        TOP_PLAYER_BUTTONS = clip.y + dim.height - BOTTOM_BORDER - PLAYER_BUTTONS_HEIGHT;

        int next = nextElementStarting(playerPos);
        if (next >= 0) {
            YassRectangle rec = rect.elementAt(next);
            if (rec.hasType(YassRectangle.GAP) && next + 1 < rect.size()) {
                rec = rect.elementAt(next + 1);
            }
            if (!isAbsolutePitchViewEnabled() && rec.y + rec.height > TOP_PLAYER_BUTTONS) {
                TOP_PLAYER_BUTTONS = clip.y + TOP_LINE - 10;
            }
        }

        // play current note

        int x = playerPos - clip.x + PLAY_NOTE_X;
        int y = TOP_PLAYER_BUTTONS - clip.y;
        int w = PLAY_NOTE_W;
        int h = PLAYER_BUTTONS_HEIGHT;

        Color fg = darkMode ? hiGrayDarkMode : HI_GRAY;
        Color sh = darkMode ? HI_GRAY_2_DARK_MODE : HI_GRAY_2;
        Color wt = darkMode ? whiteDarkMode : Color.WHITE;
        Color arr = darkMode ? arrowDarkMode : arrow;

        boolean isPressed = hiliteCue == PLAY_NOTE_PRESSED;
        paintButton(g2, x, y, w, h, isPressed, fg, sh, wt, arr);
        boolean isEnabled = hiliteCue == PLAY_NOTE_PRESSED || hiliteCue == PLAY_NOTE;
        YassUtils.paintTriangle(g2, x + 16, y + 24, 18, YassUtils.EAST,
                isEnabled, fg, sh, wt);


        // play note before

        x = playerPos - clip.x + PLAY_BEFORE_X;
        y = TOP_PLAYER_BUTTONS - clip.y;
        w = PLAY_BEFORE_W;

        isPressed = hiliteCue == PLAY_BEFORE_PRESSED;
        paintButton(g2, x, y, w, h, isPressed, fg, sh, wt, arr);
        isEnabled = hiliteCue == PLAY_BEFORE || hiliteCue == PLAY_BEFORE_PRESSED;
        g2.setColor(isEnabled ? sh : fg);
        g2.fillRect(x + 22, y + 21, 3, 23);
        YassUtils.paintTriangle(g2, x + 10, y + 27, 12, YassUtils.EAST,
                isEnabled, fg, sh, wt);

        // play note next

        x = playerPos - clip.x + PLAY_NEXT_X;
        y = TOP_PLAYER_BUTTONS - clip.y;
        w = PLAY_NEXT_W;

        isPressed = hiliteCue == PLAY_NEXT_PRESSED;
        paintButton(g2, x, y, w, h, isPressed, fg, sh, wt, arr);
        isEnabled = hiliteCue == PLAY_NEXT || hiliteCue == PLAY_NEXT_PRESSED;
        g2.setColor(isEnabled ? sh : fg);
        g2.fillRect(x + 10, y + 21, 3, 23);
        YassUtils.paintTriangle(g2, x + 15, y + 27, 12, YassUtils.EAST,
                isEnabled, fg, sh, wt);

        // play page

        x = playerPos - clip.x + PLAY_PAGE_X;
        y = TOP_PLAYER_BUTTONS - clip.y;
        w = PLAY_PAGE_W;

        isPressed = hiliteCue == PLAY_PAGE_PRESSED;
        paintButton(g2, x, y, w, h, isPressed, fg, sh, wt, arr);
        isEnabled = hiliteCue == PLAY_PAGE || hiliteCue == PLAY_PAGE_PRESSED;
        g2.setColor(isEnabled ? sh : fg);
        g2.fillRect(x + 10, y + 30, 14, 3);
        YassUtils.paintTriangle(g2, x + 4, y + 28, 8, YassUtils.WEST,
                isEnabled, fg, sh, wt);
        YassUtils.paintTriangle(g2, x + 24, y + 28, 8, YassUtils.EAST,
                isEnabled, fg, sh, wt);

    }

    private void paintButton(Graphics2D g2, int x, int y, int w, int h, boolean isPressed, Color fg, Color sh, Color wt, Color arr) {
        g2.setColor(isPressed ? fg : arr);
        g2.fillRect(x, y, w, h);

        if (isPressed) {
            g2.setColor(sh);
            g2.drawRect(x, y, w - 1, h - 1);
        } else {
            g2.setColor(fg);
            g2.drawLine(x, y, x, y + h - 1);
            g2.drawLine(x + 1, y, x + w - 2, y);

            g2.setColor(wt);
            g2.drawLine(x + 1, y + 1, x + 1, y + h - 3);
            g2.drawLine(x + 2, y + 1, x + w - 3, y + 1);

            g2.setColor(sh);
            g2.drawLine(x + 1, y + h - 2, x + w - 2, y + h - 2);
            g2.drawLine(x + w - 2, y + 1, x + w - 2, y + h - 3);

            g2.setColor(fg);
            g2.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
            g2.drawLine(x + w - 1, y + h - 1, x + w - 1, y);
        }
    }

    /**
     * Description of the Method
     *
     * @param g2 Description of the Parameter
     */
    public void paintBeats(Graphics2D g2) {
        int off = 0;
        if (paintHeights) {
            off += heightBoxWidth;
        }
        int stickyTop = getStickyTimelineTopY();
        g2.setColor(darkMode ? HI_GRAY_2_DARK_MODE : HI_GRAY_2);
        g2.fillRect((int) (beatgap * wSize + off), stickyTop, dim.width, TOP_BORDER);
        g2.setFont(smallFont);
        FontMetrics metrics = g2.getFontMetrics();

        int multiplier = 1;
        double wwSize = wSize;
        while (wwSize < 10) {
            wwSize *= 4.0;
            multiplier *= 4;
        }
        String str;
        long ms;
        Line2D.Double line = new Line2D.Double(0, stickyTop + 20, 0, stickyTop + 28);
        double leftx = clip.x;
        double rightx = clip.x + clip.width;
        g2.setColor(darkMode ? hiGrayDarkMode : HI_GRAY);
        g2.setStroke(thinStroke);
        int i = 0, j, strw;
        while (true) {
            line.x1 = line.x2 = (beatgap + i) * wSize + off;
            if (line.x1 < leftx) {
                i++;
                continue;
            }
            if (line.x1 > rightx) {
                break;
            }
            j = i / multiplier;
            if (multiplier == 1 || i % multiplier == 0) {
                g2.setStroke(j % 4 == 0 ? stdStroke : thinStroke);
                g2.setColor(j % 4 == 0 ? (darkMode ? dkGrayDarkMode : DK_GRAY) : (darkMode ? hiGrayDarkMode : HI_GRAY));
                g2.draw(line);
                if (j % 4 == 0) {
                    g2.setColor(darkMode ? hiGrayDarkMode : HI_GRAY);
                    str = Integer.toString(i);
                    strw = metrics.stringWidth(str);
                    g2.drawString(str, (float) (line.x1 - strw / 2), (float) getStickyTimelineTextY(0));

                    ms = (long) table.beatToMs(i);
                    str = YassUtils.commaTime(ms) + "s";
                    strw = metrics.stringWidth(str);
                    g2.drawString(str, (float) (line.x1 - strw / 2), (float) getStickyTimelineTextY(1));
                }
            }
            i++;
        }

        // Draw preview start indicator
        if (table != null) {
            double previewStart = table.getPreviewStart();
            if (previewStart >= 0) {
                int previewX = toTimeline(previewStart * 1000);
                g2.setColor(dkGreen);
                g2.setStroke(thickStroke); // 2px wide
                g2.drawLine(previewX, stickyTop, previewX, stickyTop + TOP_BORDER);
            }
            int medleyStart = table.getMedleyStartBeat();
            if (medleyStart >= 0) {
                int medleyX = beatToTimeline(medleyStart);
                g2.setColor(dkGreen);
                g2.setStroke(thickStroke); // 2px wide
                g2.drawLine(medleyX, stickyTop, medleyX, stickyTop + TOP_BORDER);
            }

            int medleyEnd = table.getMedleyEndBeat();
            if (medleyEnd >= 0) {
                int medleyX = beatToTimeline(medleyEnd);
                g2.setColor(dkGreen);
                g2.setStroke(thickStroke); // 2px wide
                g2.drawLine(medleyX, stickyTop, medleyX, stickyTop + TOP_BORDER);
            }
        }
    }

    /**
     * Description of the Method
     *
     * @param g2 Description of the Parameter
     */
    public void paintBeatLines(Graphics2D g2) {
        if (minHeight > maxHeight) {
            return;
        }

        g2.setStroke(thinStroke);
        g2.setColor(darkMode ? hiGrayDarkMode : HI_GRAY);
        double renderBottom = getVerticalRenderHeight() - BOTTOM_BORDER;
        double miny = renderBottom;
        double maxy;
        if (pan) {
            if (isAbsolutePitchViewEnabled()) {
                maxy = renderBottom - getVisiblePitchSpan() * hSize + 1;
            } else {
                maxy = renderBottom - ((double) (2 * (NORM_HEIGHT - 1)) / 2) * hSize + 1;
            }
        } else {
            maxy = renderBottom - ((double) (2 * (maxHeight - 1)) / 2 - minHeight) * hSize + 1;
        }

        int multiplier = 1;
        double wwSize = wSize;
        while (wwSize < 10) {
            wwSize *= 4.0;
            multiplier *= 4;
        }

        float firstB = -1;
        double leftx = getLeftX() - LEFT_BORDER;
        double rightx = clip.getX() + clip.getWidth();
        Line2D.Double line = new Line2D.Double(0, maxy, 0, miny);
        int off = 0;
        if (paintHeights) {
            off += heightBoxWidth;
        }
        int i = 0;
        int j;
        while (true) {
            line.x1 = line.x2 = (beatgap + i) * wSize + off;
            if (line.x1 < leftx) {
                i++;
                continue;
            }
            if (line.x1 > rightx) {
                break;
            }
            j = i / multiplier;
            if (multiplier == 1 || i % multiplier == 0) {
                if (firstB < 0 && j % 4 == 0) {
                    firstB = (float) line.x1;
                }
                g2.setStroke(j % 4 == 0 ? (showBackground || showVideo) ? thickStroke
                        : stdStroke
                        : thinStroke);
                g2.setColor(j % 4 == 0 ? (darkMode ? hiGrayDarkMode : HI_GRAY) : (darkMode ? HI_GRAY_2_DARK_MODE : HI_GRAY_2));
                g2.draw(line);
            }
            i++;
        }

        if (!live) {
            if (firstB < 0) {
                return;
            }
            line.x1 = firstB;
            line.x2 = line.x1 + 4 * multiplier * wSize;
            line.y1 = line.y2 = TOP_LINE - 10;
            g2.setStroke(thickStroke);
            g2.setColor(darkMode ? dkGrayDarkMode : DK_GRAY);
            g2.draw(line);
            String bstr = "B";
            if (multiplier > 1) {
                bstr = bstr + "/" + multiplier;
            }
            g2.drawString(bstr, (float) (line.x1 + line.x2) / 2f, (float) (line.y2 - 2));
            line.x1 = line.x2;
            line.y1 -= 2;
            line.y2 += 2;
            g2.draw(line);
            line.x1 = line.x2 = firstB;
            g2.draw(line);
        }
    }

    /**
     * Description of the Method
     *
     * @param g2 Description of the Parameter
     */
    public void paintLines(Graphics2D g2) {
        Line2D.Double line = new Line2D.Double(getLeftX() - LEFT_BORDER, 0,
                clip.x + clip.width, 0);
        double renderBottom = getVerticalRenderHeight() - BOTTOM_BORDER;
        if (pan) {
            if (isAbsolutePitchViewEnabled()) {
                int visiblePitchMin = getCurrentVisiblePitchMin();
                int visiblePitchSpan = getVisiblePitchSpan();

                g2.setColor(new Color(0, 0, 0, 10));
                int firstOctaveStart = Math.floorDiv(visiblePitchMin, 12) * 12;
                int visiblePitchMax = visiblePitchMin + visiblePitchSpan + 2;
                for (int octaveStart = firstOctaveStart; octaveStart <= visiblePitchMax; octaveStart += 12) {
                    if ((Math.floorDiv(octaveStart, 12) & 1) == 0) {
                        continue;
                    }
                    double blockBottom = renderBottom - (octaveStart - visiblePitchMin) * hSize;
                    double blockTop = renderBottom - (octaveStart + 12 - visiblePitchMin) * hSize;
                    int fillY = (int) Math.round(Math.max(blockTop, clip.y));
                    int fillHeight = (int) Math.round(Math.min(blockBottom, clip.y + clip.height) - Math.max(blockTop, clip.y));
                    if (fillHeight > 0) {
                        g2.fillRect((int) line.x1, fillY, (int) (line.x2 - line.x1), fillHeight);
                    }
                }

                for (int pitch = visiblePitchMin - 2; pitch <= visiblePitchMax; pitch++) {
                    if (pitch % 2 != 0) {
                        continue;
                    }
                    line.y1 = line.y2 = renderBottom - (pitch - visiblePitchMin) * hSize;
                    g2.setStroke(pitch % 12 == 0 ? stdStroke : thinStroke);
                    g2.setColor(pitch % 12 == 0 ? (darkMode ? hiGrayDarkMode : HI_GRAY) : (darkMode ? HI_GRAY_2_DARK_MODE : HI_GRAY_2));
                    g2.draw(line);
                }

                if (paintHeights) {
                    g2.setColor(darkMode ? new Color(165, 165, 165) : new Color(185, 185, 185));
                    int fs = (int) Math.min(bigFonts.length * hSize / 8, bigFonts.length - 1);
                    g2.setFont(bigFonts[fs]);
                    for (int octaveStart = firstOctaveStart; octaveStart <= visiblePitchMax; octaveStart += 12) {
                        double labelY = renderBottom - (octaveStart - visiblePitchMin) * hSize;
                        if (labelY < clip.y || labelY > clip.y + clip.height) {
                            continue;
                        }
                        String octaveLabel = "C" + (Math.floorDiv(octaveStart + 60, 12) - 1);
                        int sw = g2.getFontMetrics().stringWidth(octaveLabel);
                        int textY = (int) Math.round(labelY - 2);
                        g2.drawString(octaveLabel, (int) line.x1 + 5, textY);
                        g2.drawString(octaveLabel, (int) line.x2 - 5 - sw, textY);
                    }
                }
            } else {
                g2.setColor(darkMode ? HI_GRAY_2_DARK_MODE : HI_GRAY_2);
                g2.setStroke(stdStroke);
                for (int h = 0; h < NORM_HEIGHT; h += 2) {
                    line.y1 = line.y2 = renderBottom - h * hSize;
                    g2.draw(line);
                }
            }
        } else {
            // scale with alternating background
            g2.setColor(new Color(0, 0, 0, 10));
            for (int h = minHeight; h < maxHeight; h++) {
                if (h % 2 != 0) {
                    continue;
                }
                double y = renderBottom - (h - minHeight) * hSize;
                if ((h+12) % 24 == 0) {
                    g2.fillRect((int) line.x1, (int) (y - 12 * hSize), (int) (line.x2 - line.x1), (int) (12 * hSize));
                }
            }
            // lowest scale might be visible partly
            int mh = minHeight;
            if ((mh+12) % 24 != 0) {
                while (mh % 12 != 0) mh++;
                if (mh % 24 == 0) {
                    double y = renderBottom - (mh - minHeight) * hSize;
                    g2.fillRect((int) line.x1, (int) y, (int) (line.x2 - line.x1), (int) ((mh - minHeight) * hSize));
                }
            }
            // lines
            for (int h = minHeight; h < maxHeight; h++) {
                if (h % 2 != 0) {
                    continue;
                }
                line.y1 = line.y2 = renderBottom - (h - minHeight) * hSize;
                g2.setStroke(h % 12 == 0 ? stdStroke : thinStroke);
                g2.setColor(h % 12 == 0 ? (darkMode ? hiGrayDarkMode : HI_GRAY) : (darkMode ? HI_GRAY_2_DARK_MODE : HI_GRAY_2));
                g2.draw(line);
            }

            // scale numbers
            if (paintHeights) {
                g2.setColor(darkMode ? hiGrayDarkMode : HI_GRAY);
                int fs = (int) Math.min(bigFonts.length * hSize / 8, bigFonts.length - 1);
                g2.setFont(bigFonts[fs]);
                int fh = g2.getFontMetrics().getAscent();
                for (int h = minHeight; h < maxHeight; h++) {
                    if (h % 12 == 0) {
                        double y = renderBottom - (h - minHeight) * hSize;
                        String s = "" + (h / 12 + 4);
                        int sw = g2.getFontMetrics().stringWidth(s);
                        g2.drawString(s, (int) line.x1 + 5, (int) (y - 6 * hSize + fh / 2));
                        g2.drawString(s, (int) line.x2 - 5 - sw, (int) (y - 6 * hSize + fh / 2));
                    }
                }
                double y = renderBottom - (mh - minHeight) * hSize;
                if (y + 12 * hSize - (double) fh / 2 < renderBottom) {
                    String s = "" + (mh / 12 + 4 - 1);
                    int sw = g2.getFontMetrics().stringWidth(s);
                    g2.drawString(s, (int) line.x1 + 5, (int) (y + 12 * hSize - fh / 2));
                    g2.drawString(s, (int) line.x2 - 5 - sw, (int) (y + 12 * hSize - fh / 2));
                }
            }
        }

    }

    /**
     * Draws a vertical marker line with a label for special time points like PreviewStart.
     * The marker is positioned dynamically above or below notes to avoid overlap.
     *
     * @param g2    The Graphics2D context.
     * @param x     The x-coordinate for the marker.
     * @param label The text to display next to the marker.
     * @param lower If true, draw the marker in the lower part of the grid.
     */
    private void drawMarker(Graphics2D g2, int x, String label, boolean lower) {
        if (x < clip.x || x > clip.x + clip.width) {
            return; // Don't draw if outside the visible area
        }
        int labelLane = switch (label) {
            case "Preview Start" -> 0;
            case "Medley Start" -> lower ? 2 : 1;
            case "Medley End" -> 2;
            default -> 1;
        };
        int markerY = getStickyTimelineTopY() + TOP_BORDER + 14 + labelLane * 14;

        // --- Draw the marker and label ---
        g2.setColor(dkGreen);
        g2.setStroke(thickStroke);
        g2.drawLine(x, markerY - 12, x, markerY + 12); // 24pt high line

        // Use a bigger font and dark-mode-aware color for the label
        g2.setFont(font);
        g2.setColor(darkMode ? blackDarkMode : Color.BLACK);

        // Position "Medley End" to the left, others to the right
        if ("Medley End".equals(label)) {
            int labelWidth = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, x - labelWidth - 5, markerY + 5); // Label to the left
        } else {
            g2.drawString(label, x + 5, markerY + 5); // Label to the right
        }
    }

    private void paintStickyMarkers(Graphics2D g2) {
        if (table == null || live) {
            return;
        }

        double previewStart = table.getPreviewStart();
        int medleyStartBeat = table.getMedleyStartBeat();
        boolean overlap = (previewStart >= 0 && medleyStartBeat >= 0
                && Math.abs(toTimeline(previewStart * 1000) - beatToTimeline(medleyStartBeat)) < 1);

        Graphics2D markerGraphics = (Graphics2D) g2.create();
        markerGraphics.translate(-clip.x, -clip.y);
        try {
            if (previewStart >= 0) {
                drawMarker(markerGraphics, toTimeline(previewStart * 1000), "Preview Start", false);
            }
            if (medleyStartBeat >= 0) {
                drawMarker(markerGraphics, beatToTimeline(medleyStartBeat), "Medley Start", overlap);
            }
            int medleyEndBeat = table.getMedleyEndBeat();
            if (medleyEndBeat >= 0) {
                drawMarker(markerGraphics, beatToTimeline(medleyEndBeat), "Medley End", false);
            }
        } finally {
            markerGraphics.dispose();
        }
    }

    /**
     * Gets the noteName attribute of the YassSheet object
     *
     * @param n Description of the Parameter
     * @return The noteName value
     */
    public String getNoteName(int n) {
        return actualNoteTable[n];
    }

    public int normalizeNoteHeight(int n) {
        while (n < 0) {
            n += 12;
        }
        return n % 12;
    }

    /**
     * Gets the whiteNote attribute of the YassSheet object
     *
     * @param n Description of the Parameter
     * @return The whiteNote value
     */
    public boolean isWhiteNote(int n) {
        n = n % 12;
        return n == 0 || n == 2 || n == 4 || n == 5 || n == 7 || n == 9 || n == 11;
    }

    /**
     * Description of the Method
     *
     * @param g2 Description of the Parameter
     */
    public void paintHeightBox(Graphics2D g2) {
        int x = clip.x;
        int y = isAbsolutePitchViewEnabled() ? clip.y + TOP_LINE - 10 : TOP_LINE - 10;
        int w = heightBoxWidth - 1;
        int hh = dim.height - TOP_LINE + 10 - BOTTOM_BORDER - 1;

        g2.setStroke(thinStroke);
        g2.setColor(darkMode ? HI_GRAY_2_DARK_MODE : HI_GRAY_2);
        g2.fillRect(x, y, w, hh);

        g2.setColor(Color.gray);
        g2.drawRect(x, y, w, hh);

        int blackw = 24;
        int whitew = 40;

        double renderBottom = getVerticalRenderHeight() - BOTTOM_BORDER;
        Line2D.Double line = new Line2D.Double(clip.x + heightBoxWidth,
                y, clip.x + clip.width, renderBottom);
        g2.setFont(smallFont);
        FontMetrics metrics = g2.getFontMetrics();
        String str;
        int pianoIdx = 0;
        String pianoKey;
        int displayedPitchAnchor = getDisplayedPitchAnchor(hhPageMin);
        if (pan) {
            initNoteMapping(displayedPitchAnchor - 1);
            for (int h = 1; h < NORM_HEIGHT - 2; h++) {
                int absolutePitch = displayedPitchAnchor + h - 2;
                line.y1 = line.y2 = getNoteY(absolutePitch, hhPageMin) + hSize - 1.0;
                int noteHeight = normalizeNoteHeight(absolutePitch + 60);
                boolean isWhite = isWhiteNote(noteHeight);
                pianoKey = getLetterForNote(pianoIdx++);
                if (StringUtils.isEmpty(pianoKey) || h == 1 && !isWhite) {
                    pianoKey = getLetterForNote(pianoIdx++);
                }
                int midiPitch = absolutePitch + 60;
                str = getNoteName(noteHeight);
                if (isAbsolutePitchViewEnabled() && noteHeight == 0) {
                    str += (Math.floorDiv(midiPitch, 12) - 1);
                }
                if (isWhite) {
                    g2.setColor(darkMode ? new Color(245, 245, 245) : Color.white);
                    g2.fillRect(x + 1, (int) line.y1 - (int) hSize / 2 - 2,
                            whitew, (int) hSize - 2);
                    g2.setColor(darkMode ? dkGrayDarkMode : HI_GRAY);
                    g2.drawRect(x + 1, (int) line.y1 - (int) hSize / 2 - 2,
                            whitew, (int) hSize - 2);
                } else {
                    g2.setColor(Color.BLACK);
                    g2.fillRect(x + 1, (int) line.y1 - (int) hSize / 3 - 2,
                            blackw, Math.max(6, (int) (2 * hSize / 3) - 3));
                    g2.setColor(darkMode ? HI_GRAY_2_DARK_MODE : DK_GRAY);
                    g2.drawRect(x + 1, (int) line.y1 - (int) hSize / 3 - 2,
                            blackw, Math.max(6, (int) (2 * hSize / 3) - 3));
                }
                if (absolutePitch == hiliteHeight) {
                    g2.setColor(Color.black);
                } else {
                    g2.setColor(isWhite ? Color.gray : Color.white);
                }

                if (isWhite || isAbsolutePitchViewEnabled()) {
                    int noteLabelX = isWhite ? clip.x + 3 : clip.x + 4;
                    g2.drawString(str, (float) noteLabelX, (float) (line.y1));
                }

                str = "" + absolutePitch;
                int strw = metrics.stringWidth(str);
                if (!isAbsolutePitchViewEnabled()) {
                    int strKey = metrics.stringWidth(pianoKey);
                    g2.drawString(pianoKey, (float) clip.x + heightBoxWidth - strKey - strw - 3, (float) line.y1);
                }
                g2.drawString(str, (float) (clip.x + heightBoxWidth - strw - 3), (float) (line.y1));
            }
            if (hiliteHeight < 200) {
                g2.setColor(blueDrag);
                g2.fillRect(clip.x + heightBoxWidth, (int) (renderBottom
                        - (hiliteHeight - displayedPitchAnchor + 1)
                        * hSize), clip.width, (int) (2 * hSize));
                g2.setColor(BLUE);
                g2.fillRect(clip.x,
                        (int) (renderBottom - (hiliteHeight
                                - displayedPitchAnchor + 1)
                                * hSize), heightBoxWidth, (int) (2 * hSize));
            }
        } else {
            initNoteMapping(minHeight + 1);
            for (int h = minHeight + 1; h < maxHeight - 2; h++) {
                line.y1 = line.y2 = dim.height - BOTTOM_BORDER
                        - (h - minHeight) * hSize + 4;
                int noteHeight = normalizeNoteHeight(h + 60);
                boolean isWhite = isWhiteNote(noteHeight);
                pianoKey = getLetterForNote(pianoIdx++);
                if (StringUtils.isEmpty(pianoKey) || pianoIdx == 1 && !isWhite) {
                    pianoKey = getLetterForNote(pianoIdx++);
                }
                str = getNoteName(noteHeight);

                if (isWhite) {
                    g2.setColor(Color.white);
                    g2.fillRect(x + 1, (int) line.y1 - 9, whitew, 10);
                } else {
                    g2.setColor(Color.BLACK);
                    g2.fillRect(x + 1, (int) line.y1 - 7, blackw, 6);
                }

                if (h == hiliteHeight) {
                    g2.setColor(Color.BLACK);
                } else {
                    g2.setColor(Color.gray);
                }

                if (isWhite) {
                    metrics.stringWidth(str);
                    g2.drawString(str, (float) (clip.x + 3), (float) (line.y1));
                }
                str = "" + h;
                int strw = metrics.stringWidth(str);
                int strKey = metrics.stringWidth(pianoKey);
                g2.drawString(pianoKey, (float) x + w - strKey - strw - 5, (float) line.y1);
                g2.drawString(str, (float) (x + w - strw - 5), (float) (line.y1));
            }
            if (hiliteHeight < 200) {
                g2.setColor(HI_GRAY);
                g2.fillRect(clip.x + heightBoxWidth, (int) (dim.height
                        - BOTTOM_BORDER - (hiliteHeight - minHeight + 1)
                        * hSize), clip.width, (int) (2 * hSize));
                g2.setColor(BLUE);
                g2.fillRect(clip.x,
                        (int) (dim.height - BOTTOM_BORDER - (hiliteHeight
                                - minHeight + 1)
                                * hSize), heightBoxWidth, (int) (2 * hSize));
            }
        }
    }

    public void paintInOut(Graphics2D g2) {
        if (sketchStarted())
            return;

        if (marqueeSelectActive && (select.width > 0 || select.height > 0)) {
            Stroke oldStroke = g2.getStroke();
            Color oldColor = g2.getColor();
            float[] dash = {6f, 4f};
            boolean crossingSelection = isMarqueeCrossingSelection();
            g2.setColor(crossingSelection
                    ? (darkMode ? new Color(120, 210, 170, 58) : new Color(70, 165, 105, 48))
                    : (darkMode ? new Color(170, 200, 240, 55) : new Color(90, 130, 210, 45)));
            g2.fill(select);
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
            g2.setColor(crossingSelection
                    ? (darkMode ? new Color(152, 255, 205, 220) : new Color(28, 118, 62, 220))
                    : (darkMode ? new Color(190, 220, 255, 210) : new Color(40, 80, 160, 210)));
            g2.draw(select);
            g2.setStroke(oldStroke);
            g2.setColor(oldColor);
        } else if (select.y > 0) {
            g2.setColor(inoutColor);
            g2.fill(select);
        }

        if (inSnapshot >= 0) {
            if (outSnapshot < 0) {
                outSnapshot = inSnapshot;
            }
            g2.setColor(inoutSnapshotBarColor);
            int inf = toTimeline(inSnapshot);
            int outf = toTimeline(outSnapshot);
            int x = Math.min(inf, outf);
            int xw = Math.abs(outf - inf);
            if (xw > 0) {
                g2.fillRect(x, 0, xw, 10);
            }
        }

        if (inSelect >= 0) {
            if (outSelect < 0) {
                outSelect = inSelect;
            }
            g2.setColor(hiliteCue == SNAPSHOT ? inoutSnapshotBarColor : inoutBarColor);
            g2.setFont(smallFont);
            int inf = toTimeline(inSelect);
            int outf = toTimeline(outSelect);
            int x = Math.min(inf, outf);
            int xw = Math.abs(outf - inf);
            if (xw > 0) {
                g2.fillRect(x, 0, xw, 10);
                long d = paintHeights ? fromTimeline(xw + heightBoxWidth) : fromTimeline(xw);
                String s = YassUtils.commaTime(d) + "s";
                int sw = g2.getFontMetrics().stringWidth(s);

                g2.setColor(darkMode ? HI_GRAY_2_DARK_MODE : HI_GRAY_2);
                g2.fillRect(x + xw / 2 - sw / 2 - 5, 11, sw + 10, 9);

                g2.setColor(darkMode ? dkGrayDarkMode : DK_GRAY);
                g2.drawString(s, x + xw / 2 - sw / 2, 18);
            }
        }

        if (inPoint < 0)
            return;
        if (outPoint < 0)
            outPoint = inPoint;

        g2.setColor(inoutColor);
        g2.fillRect(Math.min(inPoint, outPoint), TOP_LINE - 10,
                Math.abs(outPoint - inPoint), clip.height - TOP_LINE + 10
                        - BOTTOM_BORDER);
    }

    public void paintPlayerPosition(Graphics2D g2, boolean active) {
        int left = paintHeights ? heightBoxWidth : 0;
        if (playerPos < left) {
            return;
        }

        int y = TOP_LINE - 10;
        int h = dim.height - TOP_LINE + 10 - BOTTOM_BORDER;
        if (!active) {
            if (hiliteCue == MOVE_REMAINDER) {
                g2.setColor(BLUE);
            } else {
                g2.setColor(playerColor);
            }
            g2.fillRect(playerPos - clip.x - 1, y, 3, h);
            if (!live) {
                g2.fillRect(playerPos - clip.x - 1, TOP_BORDER, 3, 8);
            }
        } else {
            g2.setColor(playerColor3);
            g2.fillRect(playerPos - clip.x - 1 - 2, y, 1, h);
            g2.setColor(playerColor2);
            g2.fillRect(playerPos - clip.x - 1 - 1, y, 1, h);
            g2.setColor(playerColor);
            g2.fillRect(playerPos - clip.x - 1, y, 3, h);
        }
    }

    public void paintPlayerPosition(Graphics2D g2) {
        paintPlayerPosition(g2, false);
    }

    public void paintSketch(Graphics2D g2) {
        if (!sketchStarted())
            return;
        if (sketchPos < 1)
            return;

        g2.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        g2.setColor(BLUE);
        Point p1 = sketch[0];
        for (int k = 1; k < sketchPos; k++) {
            Point p2 = sketch[k];
            g2.drawLine(-clip.x + p1.x, p1.y, -clip.x + p2.x, p2.y);
            p1 = p2;
        }
    }

    public void paintRectangles(Graphics2D g2) {
        Enumeration<YassTable> ts = tables.elements();
        for (Enumeration<Vector<YassRectangle>> e = rects.elements(); e.hasMoreElements(); ) {
            Vector<YassRectangle> r = e.nextElement();
            YassTable t = ts.nextElement();
            if (r == rect) {
                continue;
            }
            paintRectangles(g2, r, t.getTableColor(), false);
        }
        if (rect != null) {
            paintRectangles(g2, rect, table.getTableColor(), true);
        }
    }

    public void paintRectangles(Graphics2D g2, Vector<?> rect, Color col, boolean onoff) {
        YassRectangle r = null;
        int i = 0;
        new Line2D.Double(0, 0, 0, 0);
        RoundRectangle2D.Double mouseRect = new RoundRectangle2D.Double(0, 0,0, 0, 0, 0);
        RoundRectangle2D.Double cutRect = new RoundRectangle2D.Double(0, 0, 0,0, 10, 10);
        YassRectangle prev;
        YassRectangle next = null;

        int[] rows = table != null ? table.getSelectedRows() : null;
        int selnum = rows != null ? rows.length : 1;
        int selfirst = table != null ? table.getSelectionModel()
                .getMinSelectionIndex() : -1;
        int sellast = table != null ? table.getSelectionModel()
                .getMaxSelectionIndex() : -1;

        int pn = 1;

        int cx = clip.x;
        int cw = clip.width;
        int ch = clip.height;
        Line2D.Double smallRect = new Line2D.Double(0, 0, 0, 0);
        Rectangle clip2 = new Rectangle(cx, 0, cw, ch);

        Paint oldPaint = g2.getPaint();
        Stroke oldStroke = g2.getStroke();

        for (Enumeration<?> e = rect.elements(); e.hasMoreElements() || next != null; i++) {
            prev = r;
            if (next != null) {
                r = next;
                next = e.hasMoreElements() ? (YassRectangle) e.nextElement() : null;
            } else {
                r = (YassRectangle) e.nextElement();
            }
            if (next == null) {
                next = e.hasMoreElements() ? (YassRectangle) e.nextElement() : null;
            }
            if (r.isPageBreak()) {
                pn = r.getPageNumber();
            }
            Color borderCol = col;
            if (r.x < clip.x + clip.width && r.x + r.width > clip.x) {
                if (!r.isPageBreak())
                    hhPageMin = r.getPageMin();
                boolean isSelected = table != null && table.isRowSelected(i);
                if (onoff) {
                    if (!r.isPageBreak() && table.getMultiSize() > 1) {
                        Color bg = pn % 2 == 1
                                ? (darkMode ? hiGrayDarkMode : HI_GRAY)
                                : (darkMode ? HI_GRAY_2_DARK_MODE : HI_GRAY_2);
                        int w = (next == null || next.isPageBreak() || next.hasType(YassRectangle.END))
                                ? (int) r.width
                                : (int) (next.x - r.x + 1);
                        g2.setColor(bg);
                        g2.fillRect((int) r.x, getStickyBandTopY() - 16, w, 16);
                    }
                    Color shadeCol = table.isRowSelected(i) ? colorSet[YassSheet.COLOR_ACTIVE]
                            : colorSet[YassSheet.COLOR_SHADE];
                    Color hiliteFill = colorSet[YassSheet.COLOR_NORMAL];
                    if (r.isType(YassRectangle.GOLDEN)) {
                        hiliteFill = colorSet[YassSheet.COLOR_GOLDEN];
                    } else if (r.isType(YassRectangle.FREESTYLE)) {
                        hiliteFill = colorSet[YassSheet.COLOR_FREESTYLE];
                    } else if (r.isType(YassRectangle.RAP)) {
                        hiliteFill = colorSet[YassSheet.COLOR_RAP];
                    } else if (r.isType(YassRectangle.RAPGOLDEN)) {
                        hiliteFill = colorSet[YassSheet.COLOR_RAPGOLDEN];
                    } else if (r.isType(YassRectangle.WRONG)) {
                        hiliteFill = colorSet[YassSheet.COLOR_ERROR];
                    }
                    if (noshade) {
                        g2.setPaint(table.isRowSelected(i) ? colorSet[YassSheet.COLOR_ACTIVE] : hiliteFill);
                    } else {
                        g2.setPaint(new GradientPaint(
                                (float) r.x, (float) (r.y + 2), hiliteFill,
                                (float) r.x, (float) (r.y + r.height), shadeCol));
                    }
                }

                if (r.isPageBreak()) {
                    Line2D.Double dashLine = new Line2D.Double(0, 0, 0, 0);
                    float[] dash1 = {8f, 2f};
                    float dashWidth = .5f;
                    BasicStroke dashed = new BasicStroke(dashWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,10.0f, dash1, 0.0f);
                    g2.setStroke(dashed);
                    if (r.isType(YassRectangle.WRONG)) {
                        g2.setColor(colorSet[YassSheet.COLOR_WARNING]);
                    }
                    dashLine.y1 = TOP_LINE - 10;
                    dashLine.y2 = getStickyBandTopY() - 16;
                    if (wSize > 5) {
                        dashLine.x1 = dashLine.x2 = r.x - 3;
                        g2.draw(dashLine);
                    }
                    dashLine.x1 = dashLine.x2 = r.x - 1;
                    g2.draw(dashLine);
                    if (r.width >= 2 * wSize) {
                        dashLine.x1 = dashLine.x2 = r.x + r.width;
                        g2.draw(dashLine);
                    }
                } else {
                    if (r.isType(YassRectangle.GAP)) {
                        if (!live) {
                            g2.setColor(col);
                            g2.drawString("【", (float)r.x, (float)r.y + 8);
                        }
                        continue;
                    }
                    if (r.isType(YassRectangle.START)) {
                        if (!live) {
                            g2.setColor(darkMode ? dkGrayDarkMode : DK_GRAY);
                            Rectangle2D.Double rec = new Rectangle2D.Double(
                                    r.x, r.y, 2, r.height);
                            g2.fill(rec);
                            rec.width = 4;
                            rec.height = 2;
                            g2.fill(rec);
                            rec.y = r.y + r.height - 2;
                            g2.fill(rec);
                        }
                        continue;
                    }
                    if (r.isType(YassRectangle.END)) {
                        if (!live) {
                            g2.setColor(darkMode ? dkGrayDarkMode : DK_GRAY);
                            Rectangle2D.Double rec = new Rectangle2D.Double(
                                    r.x, r.y, 2, r.height);
                            g2.fill(rec);
                            rec.x = r.x - 4;
                            rec.width = 4;
                            rec.height = 2;
                            g2.fill(rec);
                            rec.y = r.y + r.height - 2;
                            g2.fill(rec);
                        }
                        continue;
                    }

                    if (onoff && r.width > 2.4) {
                        g2.fill(r);
                        // additional info text only if showing few pages
                        if (isSelected && (table.getMultiSize() <= 4 || table.hasSingleSelectedRow()))
                        {
                            if (!isPlaying && !live) {
                                g2.setFont(smallFont);
                                g2.setColor(darkMode ? blackDarkMode : Color.BLACK);
                                FontMetrics fm = g2.getFontMetrics();

                                YassRow row = table.getRowAt(i);
                                int beat = row.getBeatInt();
                                int x = beatToTimeline(beat);
                                String s = beat+"";
                                int sw = fm.stringWidth(s);
                                g2.setColor(darkMode ? HI_GRAY_2_DARK_MODE : HI_GRAY_2);
                                g2.fillRect(x - sw / 2, 0, sw, 10);
                                g2.setColor(darkMode ? blackDarkMode : Color.BLACK);
                                g2.drawString(s, x - sw / 2, 8f);

                                long ms = (long)table.beatToMs(beat);
                                s = YassUtils.commaTime(ms) + "s";
                                sw = fm.stringWidth(s);
                                g2.setColor(darkMode ? HI_GRAY_2_DARK_MODE : HI_GRAY_2);
                                g2.fillRect(x - sw / 2, 10, sw, 10);
                                g2.setColor(darkMode ? blackDarkMode : Color.BLACK);
                                g2.drawString(s, x - sw / 2, 18);
                            }
                        }
                        if (table.getMultiSize() <= 4) {
                            YassRow row = table.getRowAt(i);
                            if (showNoteBeat) {
                                String beatstr = row.getBeat();
                                int yoff = 4;

                                Font oldFont = g2.getFont();
                                g2.setColor(darkMode ? dkGrayDarkMode : DK_GRAY);
                                g2.setFont(bigFonts[15]);
                                FontMetrics metrics = g2.getFontMetrics();
                                int strw = metrics.stringWidth(beatstr);
                                int midx = (int) (r.x + r.width / 2);
                                int hx = (int) (r.x + 3);
                                int hy = (int) (r.y - yoff);
                                if (hx + strw > midx - 4)
                                    hx = midx - strw - 2;
                                g2.drawString(beatstr, hx, hy);
                                g2.setFont(oldFont);
                            }
                            if (showNoteLength) {
                                String lenstr = row.getLength();
                                int yoff = 4;

                                int midx = (int) (r.x + r.width / 2);
                                Font oldFont = g2.getFont();
                                g2.setColor(darkMode ? blackDarkMode : Color.BLACK);
                                g2.setFont(bigFonts[16]);
                                FontMetrics metrics = g2.getFontMetrics();
                                int strw = metrics.stringWidth(lenstr);
                                int lenx = (int) (r.x + r.width - 2 - strw);
                                int leny = (int) (r.y + r.height - yoff + 16);
                                if (lenx < midx + 2)
                                    lenx = midx;
                                if (r.width < 24) {
                                    lenx = (int) (midx - strw / 2 + 0.5);
                                }
                                g2.drawString(lenstr, lenx, leny);
                                g2.setFont(oldFont);

                            }
                            if (showNoteHeight || showNoteHeightNum) {
                                int pitch = row.getHeightInt();
                                if (hasCenterDragPreview() && onoff && i == centerDragPreviewRow) {
                                    pitch = centerDragTargetPitch;
                                }
                                String hstr = "";
                                if (showNoteHeightNum)
                                    hstr = pitch + "";
                                else if (showNoteHeight) {
                                    int noteHeight = normalizeNoteHeight(pitch + 60);
                                    hstr = getNoteName(noteHeight);
                                    if (showNoteScale || paintHeights) {
                                        int scale = pitch >= 0 ? (pitch / 12 + 4) : (3 + (pitch + 1) / 12); // negative pitch requires special handling
                                        hstr += "" + scale;
                                    }
                                }
                                int yoff = 4;
                                int midx = (int) (r.x + r.width / 2);
                                Font oldFont = g2.getFont();
                                g2.setColor(darkMode ? blackDarkMode : Color.BLACK);
                                g2.setFont(bigFonts[16]);
                                FontMetrics metrics = g2.getFontMetrics();
                                int strw = metrics.stringWidth(hstr);
                                int hx = (int) (r.x + 3);
                                int hy = (int) (r.y + r.height - yoff + 16);
                                if (hx + strw > midx - 2)
                                    hx = midx - strw;
                                if (r.width < 24) {
                                    hx = (int) (midx - strw / 2 + 0.5);
                                    if (showNoteLength) hy += 10;
                                }
                                g2.drawString(hstr, hx, hy);
                                g2.setFont(oldFont);
                            }
                        }
                    }

                    if (!live) {
                        boolean paintLeft = false;
                        boolean paintRight = false;
                        if (mouseover) {
                            if (isSelected && hiliteAction != ACTION_NONE) {
                                if (selnum == 1) {
                                    if (hiliteAction == ACTION_CONTROL_ALT) {
                                        paintLeft = paintRight = true;
                                    }
                                    if (hiliteAction == ACTION_CONTROL) {
                                        paintLeft = true;
                                    }
                                    if (hiliteAction == ACTION_ALT) {
                                        paintRight = true;
                                    }
                                } else if (selnum >= 2) {
                                    if (hiliteAction == ACTION_CONTROL_ALT) {
                                        if (selfirst == i) {
                                            paintLeft = true;
                                        }
                                        if (sellast == i) {
                                            paintRight = true;
                                        }
                                    }
                                    if (hiliteAction == ACTION_CONTROL) {
                                        paintLeft = true;
                                    }
                                    if (hiliteAction == ACTION_ALT) {
                                        paintRight = true;
                                    }
                                }
                            }
                        } else {
                            if ((selnum == 1 && isSelected)
                                    || (hilite == i && !isSelected)) {
                                if (hilite < 0 || hilite == i) {
                                    if (hiliteAction == ACTION_CONTROL_ALT) {
                                        paintLeft = paintRight = true;
                                    }
                                    if (hiliteAction == ACTION_CONTROL) {
                                        paintLeft = true;
                                    }
                                    if (hiliteAction == ACTION_ALT) {
                                        paintRight = true;
                                    }
                                }
                            } else if (selnum >= 2 && isSelected) {
                                if (hilite < 0 || table.isRowSelected(hilite)) {
                                    if (hiliteAction == ACTION_CONTROL_ALT) {
                                        if (selfirst == i) {
                                            paintLeft = true;
                                        }
                                        if (sellast == i) {
                                            paintRight = true;
                                        }
                                    }
                                }
                                if (hiliteAction == ACTION_CONTROL) {
                                    paintLeft = true;
                                }
                                if (hiliteAction == ACTION_ALT) {
                                    paintRight = true;
                                }
                            }
                        }
                        int dragw = r.width > Math.max(wSize, 32) * 3 ? (int) Math
                                .max(wSize, 32) : (r.width > 72 ? 24
                                : (r.width > 48 ? 16 : 5));
                        if (paintLeft) {
                            g2.setColor(blueDrag);
                            clip.width = (int) r.x - clip.x + dragw;
                            g2.setClip(clip);
                            g2.fill(r);
                        }
                        if (paintRight) {
                            g2.setColor(blueDrag);
                            clip.x = (int) (r.x + r.width - dragw + 1);
                            g2.setClip(clip);
                            g2.fill(r);
                        }
                        if (paintLeft || paintRight) {
                            clip.x = cx;
                            clip.width = cw;
                            g2.setClip(clip);
                        }
                    }

                    borderCol = hilite == i ? colorSet[YassSheet.COLOR_ACTIVE] : borderCol;
                    g2.setColor(r.isInKey() ? borderCol : Color.ORANGE);

                    if (wSize < 10) {
                        g2.setStroke(stdStroke);
                    } else {
                        g2.setStroke(medStroke);
                    }

                    if (hilite == i && !r.isType(YassRectangle.GAP)
                            && !r.isType(YassRectangle.START)
                            && !r.isType(YassRectangle.END)) {
                        if (hiliteCue == CUT || hiliteCue == JOIN_LEFT
                                || hiliteCue == JOIN_RIGHT) {
                            if (r.width > 5) {
                                mouseRect.x = r.x;
                                mouseRect.y = r.y - hSize;
                                mouseRect.width = r.width;
                                mouseRect.height = hSize - 1;

                                g2.setColor(blueDrag);
                                g2.fill(mouseRect);

                                g2.setColor(borderCol);
                                clip2.x = (int) (r.x);
                                clip2.width = (int) (wSize / 2);
                                g2.setClip(clip2);
                                g2.fill(mouseRect);

                                clip2.x = (int) (r.x + r.width - wSize / 2);
                                clip2.width = cw;
                                g2.setClip(clip2);
                                g2.fill(mouseRect);

                                g2.setClip(clip);

                                if (wSize / 2 > 5) {
                                    g2.setColor(Color.WHITE);
                                    int hh = (int) (r.y - hSize / 2);
                                    g2.drawLine((int) (r.x + 3), hh, (int) (r.x
                                            + wSize / 2 - 4), hh);
                                    g2.drawLine((int) (r.x + 5), hh - 1,
                                            (int) (r.x + 5), hh + 1);
                                    g2.drawLine((int) (r.x + 6), hh - 2,
                                            (int) (r.x + 6), hh + 2);
                                    g2.drawLine((int) (r.x + r.width - wSize
                                                    / 2 + 3), hh,
                                            (int) (r.x + r.width - 4), hh);
                                    g2.drawLine((int) (r.x + r.width - 6),
                                            hh - 1, (int) (r.x + r.width - 6),
                                            hh + 1);
                                    g2.drawLine((int) (r.x + r.width - 7),
                                            hh - 2, (int) (r.x + r.width - 7),
                                            hh + 2);
                                }

                                // int cutx = (int) (r.x - 2 + wSize / 2);
                                // g2.drawLine(cutx, (int) (r.y - hSize), cutx,
                                // (int) (r.y - 3));
                                // cutx = (int) (r.x + r.width - 2 - wSize / 2);
                                // g2.drawLine(cutx, (int) (r.y - hSize), cutx,
                                // (int) (r.y - 3));
                                // for (cutx = (int) (r.x + wSize); cutx < (int)
                                // (r.x + r.width - wSize / 2); cutx += wSize) {
                                // g2.drawLine(cutx, (int) (r.y - hSize), cutx,
                                // (int) (r.y - 3));
                                // }

                                g2.setColor(borderCol);
                            }
                        }

                        if (hiliteCue == CUT) {
                            cutRect.x = r.x;
                            cutRect.y = r.y;
                            cutRect.width = wSize
                                    * Math.round(cutPercent * r.width / wSize);
                            cutRect.height = r.height;
                            g2.draw(cutRect);
                            cutRect.x = r.x + cutRect.width;
                            cutRect.y = r.y;
                            cutRect.width = r.width - cutRect.width;
                            cutRect.height = r.height;
                            g2.draw(cutRect);
                        } else if (hiliteCue == JOIN_LEFT && prev != null) {
                            cutRect.x = prev.x;
                            cutRect.y = r.y;
                            cutRect.width = r.x - prev.x + r.width;
                            cutRect.height = r.height;
                            g2.draw(cutRect);
                        } else if (hiliteCue == JOIN_RIGHT && next != null) {
                            cutRect.x = r.x;
                            cutRect.y = r.y;
                            cutRect.width = next.x - r.x + next.width;
                            cutRect.height = r.height;
                            g2.draw(cutRect);
                        }
                    }

                    if (hilite != i
                            || (hiliteCue != CUT && hiliteCue != JOIN_RIGHT && hiliteCue != JOIN_LEFT)) {
                        if (r.width > 2.4) {
                            g2.draw(r);
                        } else if (r.width > 1.4) {
                            Color c = table.isRowSelected(i) ? colorSet[YassSheet.COLOR_ACTIVE]
                                    : (darkMode ? dkGrayDarkMode : DK_GRAY);
                            g2.setColor(c);

                            smallRect.x1 = r.x;
                            smallRect.y1 = r.y;
                            smallRect.x2 = r.x;
                            smallRect.y2 = r.y + r.height;
                            g2.draw(smallRect);
                            smallRect.x1++;
                            smallRect.x2++;
                            g2.draw(smallRect);
                        } else {
                            Color c = table.isRowSelected(i) ? colorSet[YassSheet.COLOR_ACTIVE]
                                    : (darkMode ? dkGrayDarkMode : DK_GRAY);
                            g2.setColor(c);

                            smallRect.x1 = r.x;
                            smallRect.y1 = r.y;
                            smallRect.x2 = r.x;
                            smallRect.y2 = r.y + r.height;
                            g2.draw(smallRect);
                        }
                    }
                }
            }
        }
        if (onoff) {
            paintSelectedGroupOutline(g2);
        }
        g2.setPaint(oldPaint);
        g2.setStroke(oldStroke);
    }

    private Rectangle2D.Double getSelectedGroupBounds(double padding) {
        if (table == null || rect == null || wSize >= 14) {
            return null;
        }
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows == null || selectedRows.length < 2) {
            return null;
        }
        Rectangle2D.Double groupBounds = null;
        for (int row : selectedRows) {
            if (row < 0 || row >= rect.size()) {
                continue;
            }
            YassRectangle r = rect.elementAt(row);
            if (r == null || r.isPageBreak() || r.isType(YassRectangle.GAP)
                    || r.isType(YassRectangle.START) || r.isType(YassRectangle.END)) {
                continue;
            }
            Rectangle2D.Double padded = new Rectangle2D.Double(
                    r.x - padding, r.y - padding, r.width + 2 * padding, r.height + 2 * padding);
            if (groupBounds == null) {
                groupBounds = padded;
            } else {
                Rectangle2D.union(groupBounds, padded, groupBounds);
            }
        }
        return groupBounds;
    }

    private int findSelectedNoteRowNear(int x, int y) {
        if (table == null || rect == null) {
            return -1;
        }
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows == null || selectedRows.length < 2) {
            return -1;
        }
        int bestRow = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int row : selectedRows) {
            if (row < 0 || row >= rect.size()) {
                continue;
            }
            YassRectangle r = rect.elementAt(row);
            if (r == null || r.isPageBreak() || r.isType(YassRectangle.GAP)
                    || r.isType(YassRectangle.START) || r.isType(YassRectangle.END)) {
                continue;
            }
            double cx = r.getCenterX();
            double cy = r.getCenterY();
            double dx = x - cx;
            double dy = y - cy;
            double dist = dx * dx + dy * dy;
            if (dist < bestDistance) {
                bestDistance = dist;
                bestRow = row;
            }
        }
        return bestRow;
    }

    private void paintSelectedGroupOutline(Graphics2D g2) {
        Rectangle2D.Double groupBounds = getSelectedGroupBounds(3);
        if (groupBounds == null) {
            return;
        }
        Stroke prevStroke = g2.getStroke();
        Color prevColor = g2.getColor();
        float[] dash = {4f, 4f};
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
        g2.setColor(colorSet[YassSheet.COLOR_ACTIVE]);
        g2.draw(groupBounds);
        g2.setStroke(prevStroke);
        g2.setColor(prevColor);
    }

    /**
     * Description of the Method
     *
     * @param g2 Description of the Parameter
     */
    public void paintPlainRectangles(Graphics2D g2) {
        YassRectangle r;
        new YassRectangle();
        Color borderCol = darkMode ? dkGrayDarkMode : DK_GRAY;

        int cx = clip.x;
        int cw = clip.width;
        int ch = clip.height;

        Paint oldPaint = g2.getPaint();

        Rectangle clip2 = new Rectangle(cx, 0, cw, ch);

        for (Enumeration<?> e = rect.elements(); e.hasMoreElements(); ) {
            r = (YassRectangle) e.nextElement();
            if (r.isPageBreak() || r.isType(YassRectangle.START)
                    || r.isType(YassRectangle.GAP)
                    || r.isType(YassRectangle.END)) {
                continue;
            }

            if (!(r.x < clip.x + clip.width && r.x + r.width > clip.x)) {
                continue;
            }

            // r.x = r.x - clip.x;

            Color shadeCol = colorSet[YassSheet.COLOR_SHADE];
            Color hiliteFill = colorSet[YassSheet.COLOR_NORMAL];
            if (r.isType(YassRectangle.GOLDEN)) {
                hiliteFill = colorSet[YassSheet.COLOR_GOLDEN];
            } else if (r.isType(YassRectangle.FREESTYLE)) {
                hiliteFill = colorSet[YassSheet.COLOR_FREESTYLE];
            } else if (r.isType(YassRectangle.RAP)) {
                hiliteFill = colorSet[YassSheet.COLOR_RAP];
            } else if (r.isType(YassRectangle.RAPGOLDEN)) {
                hiliteFill = colorSet[YassSheet.COLOR_RAPGOLDEN];
            } else if (r.isType(YassRectangle.WRONG)) {
                hiliteFill = colorSet[YassSheet.COLOR_ERROR];
            }

            g2.setPaint(new GradientPaint((float) r.x, (float) r.y + 2,
                    hiliteFill, (float) (r.x), (float) (r.y + r.height),
                    shadeCol));

            clip2.width = cw;
            g2.setClip(clip2);
            g2.fill(r);

            g2.setPaint(new GradientPaint((float) r.x, (float) r.y - 4,
                    playBlueHi, (float) (r.x), (float) (r.y + r.height),
                    playBlue));

            clip2.width = playerPos - cx;
            g2.setClip(clip2);
            g2.fill(r);

            clip2.width = cw;
            g2.setClip(clip2);

            g2.setColor(borderCol);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(r);

            // r.x = r.x + clip.x;
        }
        g2.setPaint(oldPaint);
    }

    /**
     * Description of the Method
     *
     * @param g2 Description of the Parameter
     */
    public void paintSnapshot(Graphics2D g2) {
        if (snapshot == null) {
            return;
        }

        Paint oldPaint = g2.getPaint();

        int next = nextElement(playerPos);
        if (next < 0) {
            return;
        }
        if (!table.isRowSelected(next)) {
            return;
        }
        YassRectangle rec = rect.elementAt(next);
        int pageMin = rec.getPageMin();

        double timelineGap = table.getGap() * 4 / (60 * 1000 / table.getBPM());

        YassRectangle r;
        YassRow row;
        int startx = -1;
        for (Enumeration<Cloneable> e = snapshotRect.elements(), te = snapshot
                .elements(); e.hasMoreElements() && te.hasMoreElements(); ) {
            r = (YassRectangle) e.nextElement();
            row = (YassRow) te.nextElement();

            // copied from update
            int beat = row.getBeatInt();
            int length = row.getLengthInt();
            int height = row.getHeightInt();
            r.x = (timelineGap + beat) * wSize + 1;
            if (paintHeights)
                r.x += heightBoxWidth;
            r.y = getNoteY(height, pageMin);
            r.width = length * wSize - 2;
            r.height = 2 * hSize - 2;

            if (startx < 0) {
                startx = (int) r.x;
            }

            r.x = r.x - startx + playerPos;

            g2.setPaint(tex);
            g2.fill(r);

            if (r.height > 3 * hSize) {
                g2.fill(r);
            } else {
                g2.fill(r);
            }
        }
        g2.setPaint(oldPaint);
    }

    /**
     * Sets the versionTextPainted attribute of the YassSheet object
     *
     * @param onoff The new versionTextPainted value
     */
    public void setVersionTextPainted(boolean onoff) {
        versionTextPainted = onoff;
    }

    /**
     * Description of the Method
     *
     * @param g2 Description of the Parameter
     */
    public void paintVersionsText(Graphics2D g2) {
        if (!versionTextPainted) {
            return;
        }

        int off = 1;
        Enumeration<Vector<YassRectangle>> er = rects.elements();
        for (Enumeration<YassTable> e = tables.elements(); e.hasMoreElements()
                && er.hasMoreElements(); ) {
            YassTable t = e.nextElement();
            Vector<?> r = er.nextElement();
            if (t == table) {
                continue;
            }
            Color c = t.getTableColor();
            paintTableText(g2, t, r, c.darker(), c, off, 0, false);
            off++;
        }
    }

    /**
     * Description of the Method
     *
     * @param g2 Description of the Parameter
     */
    public void paintText(Graphics2D g2) {
        int i = tables.indexOf(table);
        if (i < 0) {
            return;
        }
        Color c = table.getTableColor();
        if (c==null) c = Color.BLACK;
        paintTableText(g2, table, rect, c.darker(), c, 0, -clip.x, true);
    }

    /**
     * Description of the Method
     *
     * @param g2   Description of the Parameter
     * @param t    Description of the Parameter
     * @param re   Description of the Parameter
     * @param c1   Description of the Parameter
     * @param c2   Description of the Parameter
     * @param off  Description of the Parameter
     * @param offx Description of the Parameter
     * @param info Description of the Parameter
     */
    public void paintTableText(Graphics2D g2, YassTable t, Vector<?> re,
                               Color c1, Color c2, int off, int offx, boolean info) {
        String str;
        String ostr;
        int strw;
        int strh;
        YassRectangle r;
        YassRectangle next = null;
        FontMetrics metrics;

        int pn = 1;
        float sx;
        float sh;
        int offy = verticalPitchViewMode == VerticalPitchViewMode.ABSOLUTE ? -clip.y : 0;

        Rectangle2D.Double lastStringBounds = null;
        Rectangle2D.Double strBounds = new Rectangle2D.Double(0, 0, 0, 0);

        Enumeration<?> en = ((YassTableModel) t.getModel()).getData()
                .elements();
        for (Enumeration<?> ren = re.elements(); ren.hasMoreElements()
                && en.hasMoreElements(); ) {
            if (next != null) {
                r = next;
                next = (YassRectangle) ren.nextElement();
            } else {
                r = (YassRectangle) ren.nextElement();
            }
            if (next == null) {
                next = ren.hasMoreElements() ? (YassRectangle) ren.nextElement() : null;
            }

            str = ((YassRow) en.nextElement()).getText();

            if (r.isType(YassRectangle.GAP)) {
                continue;
            }
            if (r.isType(YassRectangle.START)) {
                continue;
            }
            if (r.isType(YassRectangle.END)) {
                continue;
            }
            if (r.isPageBreak()) {
                pn = r.getPageNumber();
            }

            boolean isVisible = r.x < clip.x + clip.width
                    && r.x + r.width > getLeftX();
            if (!isVisible) {
                continue;
            }

            if (info && r.isType(YassRectangle.FIRST)) {
                String s = Integer.toString(pn);
                g2.setColor(darkMode ? dkGrayDarkMode : DK_GRAY);
                g2.setFont(bigFonts[18]);
                // g2.drawString(s, (float) (r.x + offx + 5), 18);
                g2.drawString(s, (float) (r.x + offx + 5), clip.height
                        - BOTTOM_BORDER + 14);
            }

            if (str.length() < 1) {
                continue;
            }
            ostr = str = str.replace(YassRow.SPACE, ' ');

            if (off == 0 && playerPos >= r.x && playerPos < r.x + r.width) {
                g2.setColor(c1);
                // if (isPlaying) {
                // int shade = (int) ((big.length - 1 - 6) - (big.length - 18 -
                // 6) * (playerPos - r.x) / r.width);
                // g2.setFont(big[shade]);
                // } else {
                g2.setFont(bigFonts[18]);
                // }
            } else {
                g2.setColor(c2);
                g2.setFont(bigFonts[18]);

                if (r.isType(YassRectangle.FREESTYLE)) {
                    g2.setFont(fonti);
                } else if (r.isType(YassRectangle.GOLDEN)) {
                    g2.setFont(fontb);
                } else if (r.isType(YassRectangle.RAP)) {
                    g2.setFont(fontt);
                } else if (r.isType(YassRectangle.RAPGOLDEN)) {
                    g2.setFont(fonttb);
                } else if (table != t) {
                    g2.setFont(fontv);
                } else {
                    g2.setFont(font);
                }
            }

            metrics = g2.getFontMetrics();
            strw = metrics.stringWidth(str);

            if (off == 0 && strw > r.width) {
                g2.setFont(bigFonts[15]);
                metrics = g2.getFontMetrics();
                strw = metrics.stringWidth(str);
            }
            if (off == 0 && strw > r.width) {
                g2.setFont(bigFonts[13]);
                metrics = g2.getFontMetrics();
                strw = metrics.stringWidth(str);
            }

            sx = (float) Math.round(r.x + r.width / 2f - strw / 2f + offx + 1);
            strh = metrics.getAscent();
            if (off == 0) {
                sh = (float) (r.y + r.height / 2 + strh / 2f + offy);
            } else {
                sh = (float) (r.y + r.height + 2 + strh + offy);
            }

            if (strw <= r.width) {
                if (off == 0) {
                    g2.setColor(darkMode ? whiteDarkMode : Color.WHITE);
                }
                g2.drawString(str, sx, sh);
            } else {
                g2.setFont(bigFonts[24]);
                metrics = g2.getFontMetrics();
                strw = metrics.stringWidth(ostr);
                strh = metrics.getAscent();
                if (strh > r.width + 4) {
                    g2.setFont(bigFonts[15]);
                    metrics = g2.getFontMetrics();
                    strh = metrics.getAscent();
                }
                if (strh > r.width + 4) {
                    g2.setFont(bigFonts[13]);
                    metrics = g2.getFontMetrics();
                    strh = metrics.getAscent();
                }
                sx = (float) (r.x + r.width / 2 + strh / 2f + offx - 1);
                sh = (float) (r.y - 5 + offy);
                strBounds.x = r.x + r.width / 2 - strh / 2f + offx - 1;
                strBounds.y = r.y - 5 - strw + offy;
                strBounds.width = strh;
                strBounds.height = strw;
                if (strh <= r.width + 4 || lastStringBounds == null
                        || !lastStringBounds.intersects(strBounds)) {
                    if (lastStringBounds == null) {
                        lastStringBounds = new Rectangle2D.Double(0, 0, 0, 0);
                    }
                    lastStringBounds.setRect(strBounds);

                    g2.translate(sx, sh);
                    g2.rotate(-Math.PI / 2);

                    g2.setColor(darkMode ? dkGrayDarkMode : DK_GRAY);
                    g2.drawString(ostr, 0, 0);

                    g2.rotate(Math.PI / 2);
                    g2.translate(-sx, -sh);
                }
            }
        }
    }

    /**
     * Gets the live attribute of the YassSheet object
     *
     * @return The live value
     */
    public boolean isLive() {
        return live;
    }

    /**
     * Description of the Method
     *
     * @param g2     Description of the Parameter
     * @param waitms Description of the Parameter
     */
    public void paintWait(Graphics2D g2, int waitms) {
        int sec = waitms / 1000;

        int leftx = live ? 0 : LEFT_BORDER;

        if (waitms <= 4000) {
            int width = (int) (60 * waitms / 4000.0);
            g2.setColor(BLUE);
            g2.fillRect(leftx, clip.height - BOTTOM_BORDER + 16, width,
                    BOTTOM_BORDER - 16);
        } else {
            int width = 60;
            g2.setColor(BLUE);
            g2.fillRect(leftx, clip.height - BOTTOM_BORDER + 16, width,BOTTOM_BORDER - 16);
        }
        if (waitms > 3000) {
            String s = Integer.toString(sec);
            g2.setFont(bigFonts[24]);
            g2.setColor(Color.WHITE);
            FontMetrics metrics = g2.getFontMetrics();
            int strw = metrics.stringWidth(s);
            float sx = leftx + 30 - strw / 2;
            float sh = clip.height - 12;
            g2.drawString(s, sx, sh);
        }
    }

    /**
     * Paints the karaoke text at the bottom of the screen.
     * - In normal playback mode, it shows the text at the current player position.
     * - In "record mode" (when recordingNoteIndex is set), it shows the current line to be timed
     *   and the next line as a preview.
     * @param g2 The Graphics2D context.
     */
    public void paintPlayerText(Graphics2D g2) {
        if (table == null) {
            return;
        }

        int i, j;
        if (recordingNoteIndex != -1) {
            // --- RECORD MODE ---
            // The current line is determined by the recordingNoteIndex, which is controlled by the record action.
            i = j = recordingNoteIndex;
        } else if (isPlaying) {
            // --- PLAYBACK MODE ---
            // Find the current note based on the player position, ignoring any static selection.
            i = j = nextElement();
        } else {
            // --- EDIT MODE (STATIC) ---
            // Show the line based on the user's selection in the table.
            i = table.getSelectionModel().getMinSelectionIndex();
            j = table.getSelectionModel().getMaxSelectionIndex();
        }

        if (i < 0) {
            i = nextElement();
            if (i < 0)
                i = rect.size() - 2;
        }
        if (i < 0)
            return;
        if (j < 0)
            j = i;
        int[] ij = table.enlargeToPages(i, j);
        if (ij == null) return;

        i = ij[0];
        j = ij[1];

        if (showVideo() || showBackground()) {
            int leftx = 0;
            g2.setColor(playertextBG);
            if (live)
                g2.fillRect(leftx, clip.height - BOTTOM_BORDER + 16, clip.width, BOTTOM_BORDER - 16);
            else
                g2.fillRect(leftx + LEFT_BORDER, clip.height - BOTTOM_BORDER + 16, clip.width - LEFT_BORDER - RIGHT_BORDER, BOTTOM_BORDER - 16);
        }

        // --- Paint the primary (current) line ---
        paintLyricLine(g2, i, j, 0, bigFonts[24]);

        // --- In record mode, paint the next line as a preview (unless tapping is done) ---
        if (recordingNoteIndex != -1 && !actions.isRecordingInputFinished()) {
            int nextLineStart = j + 1;
            while (nextLineStart < table.getRowCount() && !table.getRowAt(nextLineStart).isNote()) {
                nextLineStart++;
            }

            if (nextLineStart < table.getRowCount()) {
                int[] next_ij = table.enlargeToPages(nextLineStart, nextLineStart);
                if (next_ij != null) {
                    // Paint the preview line below the primary line, with a smaller font.
                    paintLyricLine(g2, next_ij[0], next_ij[1], 25, bigFonts[18]);
                }
            }
        }
    }

    /**
     * Helper method to paint a single line of lyrics.
     * @param g2 The Graphics2D context.
     * @param firstRow The index of the first row of the line.
     * @param lastRow The index of the last row of the line.
     * @param yOffset The vertical offset to draw the text at.
     * @param font The font to use for drawing.
     */
    private void paintLyricLine(Graphics2D g2, int firstRow, int lastRow, int yOffset, Font font) {
        // --- 1. Calculate total width to check for overflow and for centering ---
        int totalWidth = 0;
        FontMetrics metrics = g2.getFontMetrics(font);

        for (int k = firstRow; k <= lastRow; k++) {
            YassRow row = table.getRowAt(k);
            if (row == null || !row.isNote()) continue;

            String text = row.getText().replace(YassRow.SPACE, ' ');
            if (StringUtils.isNotEmpty(text)) {
                totalWidth += metrics.stringWidth(text);
            }
        }

        if (totalWidth > clip.width) {
            String str = I18.get("sheet_msg_too_much_text");
            int strw = metrics.stringWidth(str);
            float sx = clip.width / 2f - strw / 2f;
            float sy = clip.height - 12 - yOffset;
            g2.setFont(font);
            g2.setColor(colorSet[YassSheet.COLOR_ERROR]);
            g2.drawString(str, sx, sy);
            return;
        }

        // --- 2. Draw the syllables one by one, centered ---
        float currentX = clip.width / 2f - totalWidth / 2f;
        float sy = clip.height - 12 - yOffset;

        for (int k = firstRow; k <= lastRow; k++) {
            YassRow row = table.getRowAt(k);
            YassRectangle r = rect.elementAt(k);
            if (row == null || !row.isNote()) continue;

            String text = row.getText().replace(YassRow.SPACE, ' ');
            if (StringUtils.isEmpty(text)) continue;

            // Determine font and color for the current syllable
            Font currentFont = font;
            Color currentColor = darkMode ? colorSet[YassSheet.COLOR_NORMAL] : colorSet[YassSheet.COLOR_SHADE];
            int verticalAdjust = 0;

            if (isPlaying || recordingNoteIndex != -1) {
                boolean isActiveNote = (recordingNoteIndex != -1 && k == recordingNoteIndex) ||
                                       (recordingNoteIndex == -1 && playerPos >= r.x && playerPos < r.x + r.width);

                // Highlight the active syllable during playback or recording
                if (isActiveNote) {
                    currentColor = colorSet[YassSheet.COLOR_ACTIVE];

                    // "Grow" effect for the active syllable
                    // In normal playback, grow is based on player position.
                    // In record mode, the note is "active" but doesn't grow automatically.
                    if (isPlaying && recordingNoteIndex == -1) {
                        int sizeIndex = 24 + (int) ((bigFonts.length - 1 - 24) * (playerPos - r.x) / r.width);
                        if (sizeIndex >= 0 && sizeIndex < bigFonts.length) {
                            currentFont = bigFonts[sizeIndex];
                            FontMetrics growMetrics = g2.getFontMetrics(currentFont);
                            int growStrw = growMetrics.stringWidth(text);
                            int baseStrw = g2.getFontMetrics(font).stringWidth(text);
                            currentX -= (growStrw - baseStrw) / 2f;
                            verticalAdjust = (growMetrics.getHeight() - metrics.getHeight()) / 4;
                        }
                    }
                }
            }

            g2.setFont(currentFont);
            g2.setColor(currentColor);
            g2.drawString(text, currentX, sy - verticalAdjust);

            // Reset X position if it was adjusted for the "grow" effect
            if (verticalAdjust != 0) {
                int growStrw = g2.getFontMetrics(currentFont).stringWidth(text);
                int baseStrw = g2.getFontMetrics(font).stringWidth(text);
                currentX += (growStrw - baseStrw) / 2f;
            }

            currentX += g2.getFontMetrics(font).stringWidth(text);
        }
    }

    public void setRecordingNoteIndex(int index) {
        this.recordingNoteIndex = index;
    }

    public void setRecordingOverlayPitchData(List<PitchDetector.PitchData> pitchData) {
        this.recordingOverlayPitchData = pitchData == null ? java.util.Collections.emptyList() : new ArrayList<>(pitchData);
    }

    public void clearRecordingOverlayPitchData() {
        this.recordingOverlayPitchData = java.util.Collections.emptyList();
    }

    private void setHiliteAction(int action) {
        if (hiliteAction != action) {
            hiliteAction = action;
            repaint();
        }
    }

    public Vector<Long> getTemporaryNotes() {
        return tmpNotes;
    }

    public void paintTemporaryNotes() {
        Graphics2D g2 = backVolImage.createGraphics();
        g2.setColor(dkRed);
        Enumeration<Long> e = tmpNotes.elements();
        int i;
        int o;
        int x1;
        int x2;
        double ms;
        double ms2;
        while (e.hasMoreElements()) {
            Long in = e.nextElement();
            i = (int) in.longValue();
            ms = i / 1000.0;
            x1 = toTimeline(ms);
            if (e.hasMoreElements()) {
                Long out = e.nextElement();
                o = (int) out.longValue();
                ms2 = o / 1000.0;
                x2 = toTimeline(ms2);
            } else {
                x2 = playerPos;
            }
            x1 = x1 - clip.x;
            x2 = x2 - clip.x;
            if (x1 < 0)
                x1 = 0;
            if (x2 >= clip.width)
                x2 = clip.width - 1;
            g2.fillRect(x1, getTopLine() - 10, x2 - x1, (int) hSize);
        }
        g2.dispose();
    }

    public void paintRecordedNotes() {
        if (session == null)
            return;
        Graphics2D g2 = backVolImage.createGraphics();
        YassTrack track = session.getTrack(0);
        Vector<YassPlayerNote> playerNotes = track.getPlayerNotes();
        int lastPlayerNote = playerNotes.size() - 1;
        if (lastPlayerNote < 0)
            return;
        g2.setStroke(medStroke);
        for (int playerNoteIndex = lastPlayerNote; playerNoteIndex >= 0; playerNoteIndex--) {
            YassPlayerNote playerNote = playerNotes.elementAt(playerNoteIndex);
            if (playerNote.isNoise())
                continue;
            long startMillis = playerNote.getStartMillis();
            long endMillis = playerNote.getEndMillis();
            int playerHeight = playerNote.getHeight();
            int currentNote = track.getCurrentNote();
            YassNote note = track.getNote(currentNote);
            while (note.getStartMillis() >= endMillis && currentNote > 0)
                note = track.getNote(--currentNote);
            int noteHeight = note.getHeight();
            if (playerHeight < noteHeight) {
                while (Math.abs(playerHeight - noteHeight) > 6)
                    playerHeight += 12;
            } else {
                while (Math.abs(playerHeight - noteHeight) > 6)
                    playerHeight -= 12;
            }

            int x1 = toTimeline(startMillis);
            int x2 = toTimeline(endMillis);
            x1 = x1 - clip.x;
            x2 = x2 - clip.x;
            if (x1 < 0)
                x1 = 0;
            if (x2 >= clip.width)
                x2 = clip.width - 1;
            int displayAnchor = getDisplayedPitchAnchor(hhPageMin);
            int h = isRelativePagePitchView() ? (playerHeight - displayAnchor + 3) : playerHeight - displayAnchor + 1;
            if (h <= 0)
                h += 12;
            g2.setColor(new Color(0, 120, 0, 100));
            g2.fillRoundRect(x1 + 1, (int) (dim.height - BOTTOM_BORDER - h * hSize) + 1, x2 - x1 - 3, (int) (2 * hSize - 2), 10, 10);
            g2.setColor(new Color(160, 200, 160));
            g2.drawRoundRect(x1 + 1, (int) (dim.height - BOTTOM_BORDER - h * hSize) + 1, x2 - x1 - 3, (int) (2 * hSize - 2), 10, 10);
        }
        g2.dispose();
    }

    public void setMessage(String s) {
    }

    public void setErrorMessage(String s) {
        message = s;
    }

    public void paintMessage(Graphics2D g2) {
        if (message == null || message.length() < 1)
            return;
        g2.setFont(bigFonts[19]);
        FontMetrics metrics = g2.getFontMetrics();
        metrics.stringWidth(message);
        metrics.getHeight();
        g2.setColor(Color.blue);
        g2.drawString(message, clip.x + 4, 2 + metrics.getAscent());
    }

    private int getNotePageMin(YassTable t, int rowIndex, YassRow row) {
        int pageMin = row.getHeightInt();
        if (!pan) {
            return minHeight;
        }

        int j = rowIndex - 1;
        YassRow adjacentRow = t.getRowAt(j);
        while (adjacentRow.isNote()) {
            pageMin = Math.min(pageMin, adjacentRow.getHeightInt());
            adjacentRow = t.getRowAt(--j);
        }

        j = rowIndex + 1;
        adjacentRow = t.getRowAt(j);
        while (adjacentRow != null && adjacentRow.isNote()) {
            pageMin = Math.min(pageMin, adjacentRow.getHeightInt());
            adjacentRow = t.getRowAt(++j);
        }
        return pageMin;
    }

    private int getVisiblePitchAnchor(int pageMin) {
        if (verticalPitchViewMode == VerticalPitchViewMode.ABSOLUTE) {
            return ABSOLUTE_PITCH_MIN_HEIGHT + getAbsoluteVerticalPitchOffset();
        }
        return pan ? pageMin : minHeight;
    }

    private boolean isRelativePagePitchView() {
        return verticalPitchViewMode == VerticalPitchViewMode.RELATIVE_PAGE && pan;
    }

    private int getDisplayedPitchAnchor(int pageMin) {
        return pan ? getVisiblePitchAnchor(pageMin) : minHeight;
    }

    private int getVerticalRenderHeight() {
        if (verticalPitchViewMode == VerticalPitchViewMode.ABSOLUTE) {
            return dim.height + getViewPositionY();
        }
        return dim.height;
    }

    private int getViewPositionY() {
        Container parent = getParent();
        if (!(parent instanceof JViewport viewport)) {
            return 0;
        }
        return viewport.getViewPosition().y;
    }

    private int getVisiblePitchSpan() {
        if (verticalPitchViewMode == VerticalPitchViewMode.ABSOLUTE) {
            return getClampedAbsoluteVisiblePitchSpan();
        }
        if (pan) {
            return NORM_HEIGHT - 2;
        }
        return Math.max(1, maxHeight - minHeight - 2);
    }

    private int getAbsoluteFullPitchSpan() {
        return Math.max(1, ABSOLUTE_PITCH_MAX_HEIGHT - ABSOLUTE_PITCH_MIN_HEIGHT);
    }

    private int getClampedAbsoluteVisiblePitchSpan() {
        int maxSpan = getAbsoluteFullPitchSpan();
        int minSpan = Math.min(ABSOLUTE_MIN_VISIBLE_PITCH_SPAN, maxSpan);
        if (absoluteVisiblePitchSpan < minSpan) {
            absoluteVisiblePitchSpan = minSpan;
        } else if (absoluteVisiblePitchSpan > maxSpan) {
            absoluteVisiblePitchSpan = maxSpan;
        }
        return absoluteVisiblePitchSpan;
    }

    private int getAbsoluteScrollablePitchSpan() {
        int fullPitchSpan = Math.max(getVisiblePitchSpan(), getAbsoluteFullPitchSpan());
        return Math.max(0, fullPitchSpan - getVisiblePitchSpan());
    }

    private int getAbsoluteScrollableHeight() {
        if (hSize <= 0) {
            return 0;
        }
        return (int) Math.ceil(getAbsoluteScrollablePitchSpan() * hSize);
    }

    private int getAbsoluteVerticalPitchOffset() {
        if (verticalPitchViewMode != VerticalPitchViewMode.ABSOLUTE || hSize <= 0) {
            return 0;
        }
        int maxOffset = getAbsoluteScrollablePitchSpan();
        int offset = (int) Math.round(getViewPositionY() / hSize);
        if (offset < 0) {
            return 0;
        }
        return Math.min(offset, maxOffset);
    }

    private int clampAbsolutePitchOffset(int offset) {
        if (offset < 0) {
            return 0;
        }
        return Math.min(offset, getAbsoluteScrollablePitchSpan());
    }

    private int getAbsolutePitchOffsetForRows(int startRow, int endRow) {
        if (actions != null && actions.isRecording()) {
            Integer recordingOffset = getAbsolutePitchOffsetForRecordingRows(startRow, endRow);
            if (recordingOffset != null) {
                return recordingOffset;
            }
        }
        if (table == null) {
            return 0;
        }
        int noteMin = Integer.MAX_VALUE;
        int noteMax = Integer.MIN_VALUE;
        int safeStart = Math.max(0, startRow);
        int safeEnd = Math.min(table.getRowCount() - 1, Math.max(safeStart, endRow));
        for (int rowIndex = safeStart; rowIndex <= safeEnd; rowIndex++) {
            YassRow row = table.getRowAt(rowIndex);
            if (row != null && row.isNote()) {
                noteMin = Math.min(noteMin, row.getHeightInt());
                noteMax = Math.max(noteMax, row.getHeightInt());
            }
        }
        if (noteMin == Integer.MAX_VALUE) {
            return getAbsoluteVerticalPitchOffset();
        }
        int visibleSpan = getVisiblePitchSpan();
        int noteCenter = (int) Math.round((noteMin + noteMax) / 2.0);
        int targetOffset = noteCenter - ABSOLUTE_PITCH_MIN_HEIGHT - visibleSpan / 2;
        return clampAbsolutePitchOffset(targetOffset);
    }

    private Integer getAbsolutePitchOffsetForRecordingRows(int startRow, int endRow) {
        if (table == null || recordingOverlayPitchData == null || recordingOverlayPitchData.isEmpty()) {
            return null;
        }
        int safeStart = Math.max(0, startRow);
        int safeEnd = Math.min(table.getRowCount() - 1, Math.max(safeStart, endRow));
        int startBeat = Integer.MAX_VALUE;
        int endBeat = Integer.MIN_VALUE;
        for (int rowIndex = safeStart; rowIndex <= safeEnd; rowIndex++) {
            YassRow row = table.getRowAt(rowIndex);
            if (row == null || !row.isNote()) {
                continue;
            }
            startBeat = Math.min(startBeat, row.getBeatInt());
            endBeat = Math.max(endBeat, row.getBeatInt() + row.getLengthInt());
        }
        if (startBeat == Integer.MAX_VALUE) {
            return null;
        }

        double startSeconds = table.beatToMs(startBeat) / 1000.0;
        double endSeconds = table.beatToMs(endBeat) / 1000.0;
        java.util.List<Integer> pitches = new java.util.ArrayList<>();
        for (PitchDetector.PitchData pitchData : recordingOverlayPitchData) {
            if (pitchData == null) {
                continue;
            }
            double t = pitchData.time();
            if (t < startSeconds || t > endSeconds) {
                continue;
            }
            pitches.add(pitchData.pitch());
        }
        if (pitches.isEmpty()) {
            return null;
        }
        java.util.Collections.sort(pitches);
        int lowIndex = (int) Math.floor((pitches.size() - 1) * 0.10);
        int highIndex = (int) Math.ceil((pitches.size() - 1) * 0.90);
        int pitchMin = pitches.get(Math.max(0, lowIndex));
        int pitchMax = pitches.get(Math.min(pitches.size() - 1, highIndex));
        int visibleSpan = getVisiblePitchSpan();
        int pitchCenter = (int) Math.round((pitchMin + pitchMax) / 2.0);
        int targetOffset = pitchCenter - ABSOLUTE_PITCH_MIN_HEIGHT - visibleSpan / 2;
        return clampAbsolutePitchOffset(targetOffset);
    }

    public void autoCenterAbsolutePitchView(int startRow, int endRow) {
        if (verticalPitchViewMode != VerticalPitchViewMode.ABSOLUTE || hSize <= 0) {
            return;
        }
        Container parent = getParent();
        if (!(parent instanceof JViewport viewport)) {
            return;
        }
        Point viewPosition = viewport.getViewPosition();
        int targetPitchOffset = getAbsolutePitchOffsetForRows(startRow, endRow);
        int targetY = (int) Math.round(targetPitchOffset * hSize);
        if (viewPosition.y == targetY) {
            return;
        }
        setViewPosition(new Point(viewPosition.x, targetY));
    }

    public void autoCenterAbsolutePitchView() {
        if (table == null) {
            return;
        }
        int startRow = table.getSelectionModel().getMinSelectionIndex();
        int endRow = table.getSelectionModel().getMaxSelectionIndex();

        // In one-page mode center against all notes of the current page,
        // not only the current selection.
        if (YassTable.getZoomMode() == YassTable.ZOOM_ONE) {
            int pageStartSeed = startRow;
            int pageEndSeed = endRow;
            if (pageStartSeed < 0 || pageEndSeed < 0) {
                int firstVisibleNoteIndex = firstVisibleNote();
                if (firstVisibleNoteIndex >= 0) {
                    pageStartSeed = firstVisibleNoteIndex;
                    pageEndSeed = firstVisibleNoteIndex;
                }
            }
            if (pageStartSeed >= 0 && pageEndSeed >= 0) {
                int[] pageRows = table.enlargeToPages(pageStartSeed, Math.max(pageStartSeed, pageEndSeed));
                if (pageRows != null) {
                    autoCenterAbsolutePitchView(pageRows[0], pageRows[1]);
                    return;
                }
            }
        }

        if (startRow < 0 || endRow < 0) {
            int firstVisibleNoteIndex = firstVisibleNote();
            if (firstVisibleNoteIndex < 0) {
                return;
            }
            startRow = firstVisibleNoteIndex;
            endRow = firstVisibleNoteIndex;
        }
        autoCenterAbsolutePitchView(startRow, endRow);
    }

    public void resetAbsolutePitchShiftTracking() {
        absolutePitchShiftSinceSelection = 0;
        absolutePitchShiftSelectionKey = "";
    }

    public void trackAbsolutePitchShiftForSelection(int[] selectedRows, int semitoneDelta) {
        if (!isAbsolutePitchViewEnabled() || semitoneDelta == 0 || selectedRows == null || selectedRows.length == 0) {
            return;
        }
        StringBuilder keyBuilder = new StringBuilder(selectedRows.length * 4);
        for (int selectedRow : selectedRows) {
            keyBuilder.append(selectedRow).append(',');
        }
        String selectionKey = keyBuilder.toString();
        if (!selectionKey.equals(absolutePitchShiftSelectionKey)) {
            absolutePitchShiftSelectionKey = selectionKey;
            absolutePitchShiftSinceSelection = 0;
        }

        absolutePitchShiftSinceSelection += semitoneDelta;
        if (Math.abs(absolutePitchShiftSinceSelection) > 6) {
            absolutePitchShiftSinceSelection = 0;
            SwingUtilities.invokeLater(this::autoCenterAbsolutePitchView);
        }
    }

    public void setAbsolutePitchViewEnabled(boolean enabled) {
        boolean wasAbsolutePitchViewEnabled = isAbsolutePitchViewEnabled();
        if (enabled && !wasAbsolutePitchViewEnabled) {
            paintHeightsBeforeAbsolutePitchView = paintHeights;
            paintHeights = true;
        } else if (!enabled && wasAbsolutePitchViewEnabled && paintHeightsBeforeAbsolutePitchView != null) {
            paintHeights = paintHeightsBeforeAbsolutePitchView;
            paintHeightsBeforeAbsolutePitchView = null;
        }
        verticalPitchViewMode = enabled ? VerticalPitchViewMode.ABSOLUTE : VerticalPitchViewMode.RELATIVE_PAGE;
        if (enabled) {
            getClampedAbsoluteVisiblePitchSpan();
        }
        Point viewPosition = getParent() instanceof JViewport ? getViewPosition() : new Point(0, 0);
        if (!enabled) {
            viewPosition.y = 0;
        }
        updateHeight();
        revalidate();
        setViewPosition(viewPosition);
        if (enabled) {
            autoCenterAbsolutePitchView();
        }
        if (enabled || wasAbsolutePitchViewEnabled) {
            logAbsolutePitchViewState("setAbsolutePitchViewEnabled enabled=" + enabled);
        }
        repaint();
    }

    public boolean isAbsolutePitchViewEnabled() {
        return verticalPitchViewMode == VerticalPitchViewMode.ABSOLUTE;
    }

    private int getScalePitchFromY(int y, int pageMin) {
        int anchor = getDisplayedPitchAnchor(pageMin);
        int renderHeight = getVerticalRenderHeight();
        return (int) Math.round(anchor + (renderHeight - y - BOTTOM_BORDER) / hSize);
    }

    private int getDraggedPitchFromY(int y, int pageMin) {
        int anchor = getDisplayedPitchAnchor(pageMin);
        int renderHeight = getVerticalRenderHeight();
        int pitch = (int) Math.round(anchor + (renderHeight - y - hSize - BOTTOM_BORDER + 1) / hSize);
        return isRelativePagePitchView() ? pitch - 2 : pitch;
    }

    private int getCurrentVisiblePitchMin() {
        int pageMin = minHeight;
        if (pan) {
            int firstVisibleNoteIndex = firstVisibleNote();
            if (firstVisibleNoteIndex != -1 && rect != null && firstVisibleNoteIndex < rect.size()) {
                pageMin = rect.elementAt(firstVisibleNoteIndex).getPageMin();
            } else {
                pageMin = hhPageMin;
            }
        }
        return getDisplayedPitchAnchor(pageMin);
    }

    private int getCurrentVisiblePitchMax() {
        if (pan) {
            return getCurrentVisiblePitchMin() + getVisiblePitchSpan();
        }
        return maxHeight;
    }

    private int getPitchOverlayPadding() {
        return isRelativePagePitchView() ? 2 : 0;
    }

    private double mapPitchOverlayToY(int displayPitch, int pitchRenderMin) {
        return getVerticalRenderHeight() - BOTTOM_BORDER - (displayPitch - pitchRenderMin + getPitchOverlayPadding()) * hSize;
    }

    private int getStickyWaveformBaselineY() {
        int gridTop = clip.y + TOP_LINE - 10;
        int gridBottom = clip.y + dim.height - BOTTOM_BORDER;
        return gridTop + (gridBottom - gridTop) / 2;
    }

    private int getStickyBandTopY() {
        return clip.y + dim.height - BOTTOM_BORDER + 16;
    }

    private int getStickyTimelineTopY() {
        return clip.y;
    }

    private int getStickyTimelineTextY(int lineIndex) {
        return getStickyTimelineTopY() + 8 + lineIndex * 10;
    }

    private int getStickyGridMidY(int elementHeight) {
        int gridTop = clip.y + TOP_LINE - 10;
        int gridBottom = clip.y + dim.height - BOTTOM_BORDER;
        return gridTop + (gridBottom - gridTop - elementHeight) / 2;
    }

    private int getStickySideButtonTop() {
        int buttonHeight = BOTTOM_BORDER - 16;
        int gridTop = clip.y + TOP_LINE - 10;
        int preferredTop = gridTop - buttonHeight - 4;
        return Math.max(clip.y + 2, preferredTop);
    }

    private void paintStickyWaveform(Graphics2D g2, YassPlayer mp3) {
        g2.setColor(darkMode ? dkGreen : dkGreenLight);
        int baselineY = getStickyWaveformBaselineY();
        int lasty = 0;
        for (int x = clip.x + 1; x < clip.x + clip.width; x++) {
            double ms = fromTimelineExact(x);
            int y = mp3.getWaveFormAtMillis(ms);
            g2.drawLine(x - 1, baselineY - lasty, x, baselineY - y);
            lasty = y;
        }
    }

    private int getNoteY(int noteHeight, int pageMin) {
        int verticalAnchor = getVisiblePitchAnchor(pageMin);
        int renderHeight = getVerticalRenderHeight();
        if (isRelativePagePitchView()) {
            return (int) Math.round(renderHeight - (noteHeight - verticalAnchor + 2) * hSize - hSize - BOTTOM_BORDER + 1);
        }
        return (int) Math.round(renderHeight - (noteHeight - verticalAnchor) * hSize - hSize - BOTTOM_BORDER + 1);
    }

    private void logAbsolutePitchViewState(String event) {
        if (!LOGGER.isLoggable(java.util.logging.Level.FINE)) {
            return;
        }
        Point viewPosition = getViewPosition();
        int selectionStart = table == null ? -1 : table.getSelectionModel().getMinSelectionIndex();
        int selectionEnd = table == null ? -1 : table.getSelectionModel().getMaxSelectionIndex();
        String clipInfo = clip == null ? "null" : clip.x + "," + clip.y + " " + clip.width + "x" + clip.height;
        LOGGER.fine("[AbsolutePitchView] " + event
                + " mode=" + verticalPitchViewMode
                + " pan=" + pan
                + " paintHeights=" + paintHeights
                + " zoomMode=" + YassTable.getZoomMode()
                + " view=" + viewPosition.x + "," + viewPosition.y
                + " clip=" + clipInfo
                + " playerPos=" + playerPos
                + " selection=" + selectionStart + "-" + selectionEnd
                + " hit=" + hit
                + " hiliteCue=" + hiliteCue
                + " dragMode=" + dragMode);
    }

    private void logAbsolutePitchDrag(String event, YassRectangle rr, YassRow row, int mouseY, int adjustedY, int computedPitch) {
        if (!LOGGER.isLoggable(java.util.logging.Level.FINE) || rr == null || row == null) {
            return;
        }
        int pageMin = rr.getPageMin();
        int anchor = getDisplayedPitchAnchor(pageMin);
        int renderHeight = getVerticalRenderHeight();
        LOGGER.fine("[AbsolutePitchDrag] " + event
                + " rowPitch=" + row.getHeightInt()
                + " computedPitch=" + computedPitch
                + " delta=" + (computedPitch - row.getHeightInt())
                + " mouseY=" + mouseY
                + " adjustedY=" + adjustedY
                + " dragOffsetY=" + dragOffsetY
                + " rectY=" + (int) rr.y
                + " rectH=" + (int) rr.height
                + " pageMin=" + pageMin
                + " anchor=" + anchor
                + " renderHeight=" + renderHeight
                + " viewY=" + getViewPositionY()
                + " hSize=" + hSize
                + " mode=" + verticalPitchViewMode
                + " pan=" + pan
                + " dragMode=" + dragMode);
    }

    private boolean hasCenterDragPreview() {
        return centerDragPreviewActive && centerDragPreviewRow >= 0 && rect != null && centerDragPreviewRow < rect.size();
    }

    private void beginCenterDragPreview(int rowIndex, YassRectangle rectangle, YassRow row) {
        if (rectangle == null || row == null || !row.isNote()) {
            return;
        }
        centerDragPreviewActive = true;
        centerDragPreviewRow = rowIndex;
        centerDragOriginalRectX = rectangle.x;
        centerDragOriginalRectY = rectangle.y;
        centerDragOriginalPositions.clear();
        if (table != null) {
            int[] selectedRows = table.getSelectedRows();
            for (int selectedRow : selectedRows) {
                if (selectedRow < 0 || rect == null || selectedRow >= rect.size()) {
                    continue;
                }
                YassRow selected = table.getRowAt(selectedRow);
                if (selected == null || !selected.isNote()) {
                    continue;
                }
                YassRectangle selectedRect = rect.elementAt(selectedRow);
                centerDragOriginalPositions.put(selectedRow, new Point((int) selectedRect.x, (int) selectedRect.y));
            }
        }
        if (centerDragOriginalPositions.isEmpty()) {
            centerDragOriginalPositions.put(rowIndex, new Point((int) rectangle.x, (int) rectangle.y));
        }
        centerDragStartBeat = row.getBeatInt();
        centerDragStartPitch = row.getHeightInt();
        centerDragTargetBeat = centerDragStartBeat;
        centerDragTargetPitch = centerDragStartPitch;
    }

    private void updateCenterDragPreview(YassRectangle rectangle, int px, int py, int pageMin) {
        if (!hasCenterDragPreview() || rectangle == null) {
            return;
        }
        int previewY = py - dragOffsetY;
        int maxY = isAbsolutePitchViewEnabled() ? getVerticalRenderHeight() : dim.height;
        if (previewY < 0) {
            previewY = 0;
        }
        if (previewY > maxY) {
            previewY = maxY;
        }
        int previewX = px - dragOffsetX;
        int deltaX = (int) Math.round(previewX - centerDragOriginalRectX);
        int deltaY = (int) Math.round(previewY - centerDragOriginalRectY);
        for (Map.Entry<Integer, Point> entry : centerDragOriginalPositions.entrySet()) {
            int rowIndex = entry.getKey();
            if (rowIndex < 0 || rect == null || rowIndex >= rect.size()) {
                continue;
            }
            YassRectangle selectedRect = rect.elementAt(rowIndex);
            Point original = entry.getValue();
            selectedRect.x = original.x + deltaX;
            selectedRect.y = original.y + deltaY;
        }
        rectangle.x = previewX;
        rectangle.y = previewY;

        int contentOffset = paintHeights ? heightBoxWidth : 0;
        centerDragTargetBeat = (int) Math.round((rectangle.x - contentOffset - 1) / wSize - beatgap);
        centerDragTargetPitch = getDraggedPitchFromY(previewY, pageMin);
        hiliteHeight = centerDragTargetPitch;
    }

    private void clearCenterDragPreview(boolean restoreRectangle) {
        if (!hasCenterDragPreview()) {
            centerDragPreviewActive = false;
            centerDragPreviewRow = -1;
            return;
        }
        if (restoreRectangle) {
            for (Map.Entry<Integer, Point> entry : centerDragOriginalPositions.entrySet()) {
                int rowIndex = entry.getKey();
                if (rowIndex < 0 || rect == null || rowIndex >= rect.size()) {
                    continue;
                }
                YassRectangle rectangle = rect.elementAt(rowIndex);
                Point original = entry.getValue();
                rectangle.x = original.x;
                rectangle.y = original.y;
            }
        }
        centerDragOriginalPositions.clear();
        centerDragPreviewActive = false;
        centerDragPreviewRow = -1;
        hiliteHeight = 1000;
    }

    private void commitCenterDragPreview() {
        if (!hasCenterDragPreview() || table == null) {
            clearCenterDragPreview(false);
            return;
        }
        int beatDelta = centerDragTargetBeat - centerDragStartBeat;
        int pitchDelta = centerDragTargetPitch - centerDragStartPitch;
        clearCenterDragPreview(true);
        if (beatDelta == 0 && pitchDelta == 0) {
            repaint();
            return;
        }
        table.setPreventUndo(true);
        if (pitchDelta != 0) {
            firePropertyChange("relHeight", null, pitchDelta);
        }
        if (beatDelta != 0) {
            firePropertyChange("relBeat", null, beatDelta);
        }
        if (isAbsolutePitchViewEnabled() && Math.abs(pitchDelta) > 6) {
            SwingUtilities.invokeLater(this::autoCenterAbsolutePitchView);
        }
    }

    private void updateFromRow(YassTable t, int i, YassRow prev, YassRow r, YassRectangle rr) {
        double timelineGap = t.getGap() * 4 / (60 * 1000 / t.getBPM());
        if (r.isNote()) {
            int pageMin = getNotePageMin(t, i, r);
            boolean isInKey;
            MusicalKeyEnum key;
            YassActions yassActions = t.getActions();
            if (yassActions != null && yassActions.getMP3() != null && yassActions.getMP3().getKey() != null) {
                key = t.getActions().getMP3().getKey();
            } else {
                key = MusicalKeyEnum.UNDEFINED;
            }
            if (pan) {
                int j = i - 1;
                YassRow p = t.getRowAt(j);
                while (p.isNote()) {
                    pageMin = Math.min(pageMin, p.getHeightInt());
                    p = t.getRowAt(--j);
                }
                j = i + 1;
                p = t.getRowAt(j);
                while (p != null && p.isNote()) {
                    pageMin = Math.min(pageMin, p.getHeightInt());
                    p = t.getRowAt(++j);
                }
            }
            int beat = r.getBeatInt();
            int length = r.getLengthInt();
            int height = r.getHeightInt();
            // Only evaluate out-of-key highlighting if a key is known.
            boolean keyDefined = key != null && key != MusicalKeyEnum.UNDEFINED;
            rr.setInKey(!keyDefined || r.isFreeStyle() || r.isRap() || r.isRapGolden() || key.isInKey(height));
            rr.x = (timelineGap + beat) * wSize + 1;
            if (paintHeights)
                rr.x += heightBoxWidth;
            rr.y = getNoteY(height, pageMin);
            rr.width = length * wSize - 2;
            if (rr.width < 1)
                rr.width = 1;
            rr.height = 2 * hSize - 2;
            rr.setPageMin(getVisiblePitchAnchor(pageMin));
            if (r.hasMessage())
                rr.setType(YassRectangle.WRONG);
            else if (r.isGolden())
                rr.setType(YassRectangle.GOLDEN);
            else if (r.isFreeStyle())
                rr.setType(YassRectangle.FREESTYLE);
            else if (r.isRap())
                rr.setType(YassRectangle.RAP);
            else if (r.isRapGolden())
                rr.setType(YassRectangle.RAPGOLDEN);
            else
                rr.resetType();
            if (prev != null && prev.isPageBreak())
                rr.addType(YassRectangle.FIRST);
        } else if (r.isPageBreak()) {
            int beat = r.getBeatInt();
            int beat2 = r.getSecondBeatInt();
            int length = beat2 - beat;
            rr.x = (timelineGap + beat) * wSize + 2;
            if (paintHeights)
                rr.x += heightBoxWidth;
            rr.width = (length == 0) ? wSize / 4.0 : length * wSize - 2;
            if (pan)
                rr.height = ((double) (2 * NORM_HEIGHT) / 2 - 1) * hSize;
            else
                rr.height = ((double) (2 * maxHeight) / 2 - 1 - minHeight) * hSize;
            rr.y = dim.height - BOTTOM_BORDER - rr.height;
            if (r.hasMessage())
                rr.setType(YassRectangle.WRONG);
            else
                rr.resetType();
            rr.setPageNumber(0);
        } else if (r.isComment() && r.getHeaderCommentTag().equals("GAP:")) {
            // choose correct index i
            rr.x = timelineGap * wSize - 10;
            if (paintHeights)
                rr.x += heightBoxWidth;
            rr.width = 20;
            rr.height = 9;
            rr.y = 0;
            rr.setType(YassRectangle.GAP);
        } else if (r.isComment()
                && (r.getHeaderCommentTag().equals("START:") || r.getHeaderCommentTag().equals("TITLE:"))) {
            double start = t.getStart() * 4 / (60 * 1000 / t.getBPM());
            // choose correct index i
            rr.x = start * wSize;
            if (paintHeights)
                rr.x += heightBoxWidth;
            rr.width = 10;
            rr.height = 18;
            rr.y = 21;
            rr.setType(YassRectangle.START);
        } else if (r.isEnd()) {
            double end = t.getEnd();
            if (end < 0 || end > duration)
                end = duration;
            end = end * 4 / (60 * 1000 / t.getBPM());
            // choose correct index i
            rr.x = end * wSize - 5;
            if (paintHeights)
                rr.x += heightBoxWidth;
            rr.width = 10;
            rr.height = 18;
            rr.y = 21;
            rr.setType(YassRectangle.END);
        } else if (r.isComment()) {
            rr.setType(YassRectangle.HEADER);
        }
    }

    public void enablePan(boolean onoff) {
        pan = onoff;
        updateHeight();
        revalidate();
        if (isAbsolutePitchViewEnabled() || onoff != pan) {
            logAbsolutePitchViewState("enablePan onoff=" + onoff);
        }
    }

    public boolean isPanEnabled() {
        return pan;
    }

    /**
     * Calculates min/max bounds for all tables (heights and beats).
     * Table gaps are added to beats (rounded to next beat).
     * @return
     */
    public int[] getHeightRange() {
        int minH = 128;
        int maxH = -128;
        int minB = 100000;
        int maxB = 0;
        for (YassTable t: tables) {
            for (YassRow r: t.getModelData()) {
                if (r.isNote()) {
                    int height = r.getHeightInt();
                    minH = Math.min(minH, height);
                    maxH = Math.max(maxH, height);
                    minB = Math.min(minB, r.getBeatInt());
                    maxB = Math.max(maxB, r.getBeatInt() + r.getLengthInt());
                }
            }
        }
        if (minH == 128)
            minH = 0;
        maxH = maxH + 3;
        minH = minH - 1;
        if (maxH - minH < 19)
            maxH = minH + 19;
        return new int[]{minH, maxH, minB, maxB};
    }

    /**
     * Description of the Method
     */
    public void init() {
        firePropertyChange("play", null, "stop");
        int maxWait = 10;
        while (isRefreshing() && maxWait-- > 0) {
            try {
                Thread.sleep(100);
            } catch (Exception ignored) {}
        }
        int[] range = getHeightRange();
        minHeight = range[0];
        maxHeight = range[1];
        minBeat = range[2];
        maxBeat = range[3];
        fireRangeChanged(minHeight, maxHeight, minBeat, maxBeat);

        Enumeration<YassTable> et = tables.elements();
        for (Enumeration<Vector<YassRectangle>> e = rects.elements(); e.hasMoreElements() && et.hasMoreElements(); ) {
            Vector<YassRectangle> r = e.nextElement();
            YassTable t = et.nextElement();
            r.removeAllElements();
            int n = t.getRowCount();
            for (int i = 0; i < n; i++)
                r.addElement(new YassRectangle());
        }
        if (isValid()) {
            updateHeight();
            update();
            repaint();
        }
    }

    public void updateHeight() {
        if (dim == null || getParent() == null)
            return;
        dim.setSize(dim.width, getParent().getSize().height);
        if (pan)
            hSize = (dim.height - BOTTOM_BORDER - 30) / (double) getVisiblePitchSpan();
        else
            hSize = (dim.height - BOTTOM_BORDER - 30) / (double) (maxHeight - minHeight - 2);
        if (hSize > 16)
            hSize = 16;
        if (pan)
            TOP_LINE = dim.height - BOTTOM_BORDER + 10 - (int) (hSize * getVisiblePitchSpan());
        else
            TOP_LINE = dim.height - BOTTOM_BORDER + 10 - (int) (hSize * (maxHeight - minHeight - 2));
        LOGGER.fine("YassSheet.updateHeight parent=" + getParent().getWidth() + "x" + getParent().getHeight()
                + " dim=" + dim.width + "x" + dim.height
                + " minHeight=" + minHeight + " maxHeight=" + maxHeight
                + " hSize=" + hSize + " pan=" + pan);
    }

    public int getAbsolutePitchRangeSpan() {
        return getAbsoluteFullPitchSpan();
    }

    public int getAbsolutePitchWindowStart() {
        return getAbsoluteVerticalPitchOffset();
    }

    public int getAbsolutePitchWindowSpan() {
        return getVisiblePitchSpan();
    }

    public void setAbsolutePitchWindow(int startOffset, int windowSpan) {
        if (!isAbsolutePitchViewEnabled()) {
            return;
        }
        int fullSpan = getAbsoluteFullPitchSpan();
        int minSpan = Math.min(ABSOLUTE_MIN_VISIBLE_PITCH_SPAN, fullSpan);
        int clampedSpan = Math.max(minSpan, Math.min(fullSpan, windowSpan));
        absoluteVisiblePitchSpan = clampedSpan;

        updateHeight();
        revalidate();

        int maxOffset = Math.max(0, fullSpan - clampedSpan);
        int clampedOffset = Math.max(0, Math.min(maxOffset, startOffset));
        int targetY = (int) Math.round(clampedOffset * hSize);
        Point current = getViewPosition();
        setViewPosition(new Point(current.x, targetY));
        repaint();
    }

    public void update() {
        updateHeight();
        if (table != null) {
            gap = table.getGap();
            bpm = table.getBPM();
            beatgap = gap * 4 / (60 * 1000 / bpm);
        }
        outgap = 0;

        // beat/height range
        int minH = 128;
        int maxH = -128;
        int minB = 100000;
        int maxB = 0;
        
        // enumerate all row vectors and rect vectors
        Enumeration<YassTable> eTables = tables.elements();
        Enumeration<Vector<YassRectangle>> eRects = rects.elements();
        while (eRects.hasMoreElements() && eTables.hasMoreElements()) {
            Vector<YassRectangle> vRects = eRects.nextElement();
            YassTable table = eTables.nextElement();
            if (vRects.size() != table.getRowCount())
            {
                LOGGER.severe("number of rows and rect elements do not match: "+vRects.size()+" != "+table.getRowCount());
                continue;
            }
            int pn = 1;
            Vector<YassRow> vRows = ((YassTableModel) table.getModel()).getData();
            YassRow prev = null;
            for(int i = 0; i < vRects.size();i++){
                YassRow row = vRows.elementAt(i);
                YassRectangle rect = vRects.elementAt(i);

                if (row.isNote())
                    outgap = Math.max(outgap, row.getBeatInt() + row.getLengthInt());
                else if (row.isPageBreak())
                    outgap = Math.max(outgap, row.getSecondBeatInt());
                updateFromRow(table, i, prev, row, rect);
                if (rect.isPageBreak()) {
                    rect.setPageNumber(++pn);
                    // should better add PAGE_BREAK type
                    rect.removeType(YassRectangle.DEFAULT);
                }
                // beat/height range
                if (row.isNote()) {
                    int height = row.getHeightInt();
                    minH = Math.min(minH, height);
                    maxH = Math.max(maxH, height);
                    minB = Math.min(minB, row.getBeatInt());
                    maxB = Math.max(maxB, row.getBeatInt() + row.getLengthInt());
                }
                prev = row;
            }
        }
        
        // beat/height range
        if (minH == 128)
            minH = 0;
        maxH = maxH + 3;
        minH = minH - 1;
        if (maxH - minH < 19)
            maxH = minH + 19;
        boolean changed = false;
        if (minHeight != minH) { minHeight = minH; changed = true; }
        if (maxHeight != maxH) { maxHeight = maxH; changed = true; }
        if (minBeat   != minB) { minBeat   = minB; changed = true; }
        if (maxBeat   != maxB) { maxBeat   = maxB; changed = true; }
        if (changed)
            fireRangeChanged(minHeight, maxHeight, minBeat, maxBeat);
        if (changed || verticalPitchViewMode == VerticalPitchViewMode.ABSOLUTE) {
            revalidate();
        }
        LOGGER.fine("YassSheet.update range minH=" + minHeight + " maxH=" + maxHeight
                + " minB=" + minBeat + " maxB=" + maxBeat
                + " outgap=" + outgap + " tableRows=" + (table == null ? -1 : table.getRowCount()));
    }

    public void setHNoteEnabled(boolean b) {
        actualNoteTable = b ? hNoteTable : bNoteTable;
    }

    public void updateActiveTable() {
        if (table == null)
            return;
        gap = table.getGap();
        bpm = table.getBPM();
        beatgap = gap * 4 / (60 * 1000 / bpm);
        int i = 0;
        int pn = 1;
        Enumeration<?> ren = rect.elements();
        Enumeration<?> ten = ((YassTableModel) table.getModel()).getData().elements();
        YassRow row = null;
        YassRow prev;
        YassRow next = null;
        while (ren.hasMoreElements() && ten.hasMoreElements()) {
            prev = row;
            if (next != null) {
                row = next;
                next = (YassRow) ten.nextElement();
            } else
                row = (YassRow) ten.nextElement();
            if (next == null)
                next = ten.hasMoreElements() ? (YassRow) ten.nextElement() : null;
            if (row.isNote())
                outgap = Math.max(outgap, row.getBeatInt() + row.getLengthInt());
            else if (row.isPageBreak())
                outgap = Math.max(outgap, row.getSecondBeatInt());
            YassRectangle rr = (YassRectangle) ren.nextElement();
            updateFromRow(table, i++, prev, row, rr);
            if (rr.isPageBreak()) {
                rr.setPageNumber(++pn);
            }
        }
    }

    public int getPlayerPosition() {
        return playerPos;
    }

    public void setPlayerPosition(int x) {
        if (x>=0) playerPos = x;
        firePosChanged();
    }

    public long getInSnapshot() {
        return inSnapshot;
    }

    public long getOutSnapshot() {
        return outSnapshot;
    }

    public void setPaintHeights(boolean onoff) {
        if (isAbsolutePitchViewEnabled()) {
            paintHeightsBeforeAbsolutePitchView = onoff;
            paintHeights = true;
            logAbsolutePitchViewState("setPaintHeights absolute-request=" + onoff);
            return;
        }
        paintHeights = onoff;
    }

    public int toTimeline(double ms) {
        int x = (int) (4 * bpm * ms / (60 * 1000) * wSize + .5);
        if (paintHeights)
            x += heightBoxWidth;
        return x;
    }

    public long fromTimeline(double x) {
        if (paintHeights)
            x -= heightBoxWidth;
        return (long) (x * 60 * 1000L / (4.0 * bpm * wSize) + .5);
    }
    public long fromTimeline(int track, double x) {
        if (paintHeights)
            x -= heightBoxWidth;
        return (long) (x * 60 * 1000L / (4.0 * getTable(track).getBPM() * wSize) + .5);
    }

    public double fromTimelineExact(double x) {
        if (paintHeights)
            x -= heightBoxWidth;
        return x * 60 * 1000L / (4.0 * bpm * wSize);
    }

    public int beatToTimeline(int beat) {
        int x = (int) ((beatgap + beat) * wSize + .5);
        if (paintHeights)
            x += heightBoxWidth;
        return x;
    }

    public int timelineToBeat(int x) {
        if (paintHeights)
            x -= heightBoxWidth;
        return (int) (x/wSize - beatgap);
    }

    public double getMinGapInBeats()
    {
        int n = tables.size();
        double b = 10000;
        for (int i=0; i<n; i++)
            b = Math.min(b, getTable(i).getGapInBeats());
        return b;
    }

    /**
     * Finds first element that starts or ends after current position (or directly at)
     * @return index, -1 if not found
     */
    public int nextElement() {
        return nextElement(playerPos);
    }

    /**
     * Finds first element that starts or ends after given position (or directly at)
     * @param pos
     * @return index, -1 if not found
     */
    public int nextElement(int pos) {
        YassRectangle r;
        int i = 0;
        for (Enumeration<?> e = rect.elements(); e.hasMoreElements(); i++) {
            r = (YassRectangle) e.nextElement();
            if (r.x - 1 >= pos || r.x + r.width >= pos) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds first element that starts after given position (or nearly at)
     * @param pos
     * @return index, -1 if not found
     */
    public int nextElementStarting(int pos) {
        YassRectangle r;
        int i = 0;
        for (Enumeration<?> e = rect.elements(); e.hasMoreElements(); i++) {
            r = (YassRectangle) e.nextElement();
            if (r.x >= pos - 2) {
                return i;
            }
        }
        return -1;
    }

    /* not used
    public int nextElement(int track, int pos) {
        YassRectangle r;
        int i = 0;
        for (Enumeration<?> e = rects.elementAt(track).elements(); e.hasMoreElements(); i++) {
            r = (YassRectangle) e.nextElement();
            if (r.x - 1 >= pos || r.x + r.width >= pos) {
                return i;
            }
        }
        return -1;
    }*/

    public int firstVisibleNote() {
        int x = clip.x + LEFT_BORDER;
        if (paintHeights) {
            x += heightBoxWidth;
        }
        return nextNote(x);
    }
    public int nextNote(int pos) {
        YassRectangle r;
        int i = 0;
        for (Enumeration<?> e = rect.elements(); e.hasMoreElements(); i++) {
            r = (YassRectangle) e.nextElement();
            if (! r.isPageBreak() && !r.isType(YassRectangle.GAP) && !r.isType(YassRectangle.START)&& !r.isType(YassRectangle.END)) {
                if (r.x - 1 >= pos || r.x + r.width >= pos)
                    return i;
            }
        }
        return -1;
    }
    public int firstVisibleNote(int track) {
        int x = clip.x + LEFT_BORDER;
        if (paintHeights) {
            x += heightBoxWidth;
        }
        return nextNote(track, x);
    }
    public int nextNote(int track, int pos) {
        YassRectangle r;
        int i = 0;
        if (track < 0 || track >= rects.size())
            return -1;
        for (Enumeration<?> e = rects.elementAt(track).elements(); e.hasMoreElements(); i++) {
            r = (YassRectangle) e.nextElement();
            if (! r.isPageBreak() && !r.isType(YassRectangle.GAP) && !r.isType(YassRectangle.START)&& !r.isType(YassRectangle.END))
                if (r.x - 1 >= pos || r.x + r.width >= pos)
                    return i;
        }
        return -1;
    }

    public double getMinVisibleMs() {
        return fromTimelineExact(clip.x);
    }
    public double getMaxVisibleMs() {
        int x = clip.x + clip.width - 1;
        return fromTimelineExact(x);
    }

    public boolean isVisibleMs(double ms) {
        return getMinVisibleMs() < ms && ms < getMaxVisibleMs();
    }

    public double getLeftMs() {
        return fromTimeline(clip.x);
    }

    public void setViewToNextPage() {
        table.gotoPage(1);
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double ms) {
        if (ms <= 0)
            ms = 10000L;
        duration = ms;
        dim.setSize(toTimeline(duration), dim.height);
        setSize(dim);
    }

    public double getBeatSize() {
        return wSize;
    }

    public void setBeatSize(double w) {
        wSize = (int)w;
        update();
    }

    public void setZoom(double w) {
        wSize = (int)w;
        dim.setSize(toTimeline(duration), dim.height);
        setSize(dim);
        update();
        if (table != null) {
            int i = table.getSelectionModel().getMinSelectionIndex();
            int j = table.getSelectionModel().getMaxSelectionIndex();
            if (i >= 0)
                scrollRectToVisible(i, j);
        }
        repaint();
    }

    public void setVisibleWindowMs(double startMs, double windowMs) {
        if (bpm <= 0) {
            return;
        }
        Container parent = getParent();
        if (!(parent instanceof JViewport viewport)) {
            return;
        }

        int visibleTimelinePixels = Math.max(1, viewport.getExtentSize().width - 1);

        double durationMs = Math.max(1, duration);
        double clampedWindowMs = Math.max(1000, Math.min(windowMs, durationMs));
        double maxStartMs = Math.max(0, durationMs - clampedWindowMs);
        double clampedStartMs = Math.max(0, Math.min(startMs, maxStartMs));

        double newWSize = visibleTimelinePixels * 60000d / (clampedWindowMs * 4d * bpm);
        if (newWSize < 1) {
            newWSize = 1;
        }

        Point currentView = viewport.getViewPosition();
        int preserveY = currentView.y;
        wSize = newWSize;
        dim.setSize(toTimeline(duration), dim.height);
        update();
        Dimension preferredSize = getPreferredSize();
        setSize(preferredSize);
        revalidate();

        int leftX = toTimeline(clampedStartMs);
        Dimension extentSize = viewport.getExtentSize();
        int maxX = Math.max(0, preferredSize.width - extentSize.width);
        int maxY = Math.max(0, preferredSize.height - extentSize.height);
        int targetX = Math.max(0, Math.min(leftX, maxX));
        int targetY = Math.max(0, Math.min(preserveY, maxY));
        setViewPosition(new Point(targetX, targetY));
        repaint();
    }

    public void setZoom(int i, int j, boolean force) {
        if (table == null)
            return;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int beat = min;
        int end = max;
        for (int k = i; k <= j; k++) {
            YassRow r = table.getRowAt(k);
            if (r.isNote()) {
                beat = r.getBeatInt();
                end = beat + r.getLengthInt();
            } else if (r.isPageBreak()) {
                end = r.getSecondBeatInt();
            } else if (r.isComment() && !r.getHeaderCommentTag().equals("END:")) {
                beat = table.msToBeat(0);
                if (r.getHeaderCommentTag().equals("GAP:"))
                    end = 0;
            } else if (r.isEnd()) {
                beat = Math.max(outgap - 1, 0);
                double b = table.getEnd();
                if (b < 0)
                    b = duration;
                end = table.msToBeat(b);
            }
            min = Math.min(min, beat);
            max = Math.max(max, end);
        }
        if (min == Integer.MAX_VALUE)
            return;

        // quick hack to get actual size on screen
        int d = ((JViewport) getParent()).getExtentSize().width - 2;
        if (d < 0) {
            LOGGER.info("warning: invalid sheet width");
        }
        d -= LEFT_BORDER + RIGHT_BORDER;
        if (paintHeights)
            d -= heightBoxWidth;
        double val = min == max ? d : d / (double) (max - min);
        if (force || val < wSize) {
            // adjust wSize
            wSize = (int)val;
            if (wSize == 0) wSize = val;
            dim.setSize(toTimeline(duration), dim.height);
            setSize(dim);
            if (isVisible()) {
                validate();
                update();
            }
        }
        scrollRectToVisible(i, j);
    }

    private class SlideThread extends Thread {
        private int off;
        private int ticks = 0;
        public boolean quit = false;

        public SlideThread(int off) {
            this.off=off;
        }
        public void run() {
            while (! quit && hiliteCue == PREV_SLIDE_PRESSED || hiliteCue == NEXT_SLIDE_PRESSED) {
                if (off < 0)
                    slideLeft(-off);
                else slideRight(off);
                ticks++;
                if (ticks == 50)
                    off *= 5;
                try { Thread.sleep(40); } catch (Exception e) {}
                }
            }
        }
    private SlideThread slideThread = null;

    private void startSlide (int off) {
        stopSlide();
        slideThread = new SlideThread(off);
        slideThread.start();
    }
    private void stopSlide() {
        if (slideThread != null) {
            slideThread.quit = true;
            slideThread = null;
        }
    }

    public void slideLeft(int off) {
        if (!isAbsolutePitchViewEnabled() && YassTable.getZoomMode() == YassTable.ZOOM_ONE) {
            YassTable.setZoomMode(YassTable.ZOOM_MULTI);
            enablePan(false);
            update();
        }
        Point vp = getViewPosition();
        vp.x = vp.x - off;
        if (vp.x < 0) {
            vp.x = 0;
        }
        setViewPosition(vp);
        if (playerPos < vp.x || playerPos > vp.x + clip.width) {
            int next = nextElement(vp.x);
            if (next >= 0) {
                YassRow row = table.getRowAt(next);
                if (!row.isNote() && next + 1 < table.getRowCount()) {
                    next = next + 1;
                    row = table.getRowAt(next);
                }
                if (row.isNote()) {
                    table.setRowSelectionInterval(next, next);
                    table.updatePlayerPosition();
                }
            }
        }
    }
    public void slideRight(int off) {
        if (!isAbsolutePitchViewEnabled() && YassTable.getZoomMode() == YassTable.ZOOM_ONE) {
            YassTable.setZoomMode(YassTable.ZOOM_MULTI);
            enablePan(false);
            update();
        }
        Point vp = getViewPosition();
        vp.x = vp.x + off;
        if (vp.x < 0) {
            vp.x = 0;
        }
        setViewPosition(vp);
        if (playerPos < vp.x || playerPos > vp.x + clip.width) {
            int next = nextElement(vp.x);
            if (next >= 0) {
                YassRow row = table.getRowAt(next);
                if (!row.isNote() && next + 1 < table.getRowCount()) {
                    next = next + 1;
                    row = table.getRowAt(next);
                }
                if (row.isNote()) {
                    table.setRowSelectionInterval(next, next);
                    table.updatePlayerPosition();
                }
            }
        }
    }

    // //////////////////////// PLAYBACK RENDERER
    public Dimension getPreferredSize() {
        int preferredHeight = dim.height;
        if (verticalPitchViewMode == VerticalPitchViewMode.ABSOLUTE) {
            preferredHeight += getAbsoluteScrollableHeight();
        }
        return new Dimension(dim.width, preferredHeight);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        if (orientation == SwingConstants.VERTICAL) {
            return Math.max(1, (int) Math.round(hSize > 0 ? hSize : NORM_HEIGHT));
        }
        return Math.max(1, (int) Math.round(wSize > 0 ? wSize : 1));
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        if (orientation == SwingConstants.VERTICAL) {
            int unit = getScrollableUnitIncrement(visibleRect, orientation, direction);
            return Math.max(unit, unit * 8);
        }
        return Math.max(1, visibleRect.width - LEFT_BORDER - RIGHT_BORDER);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return false;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    public int getAvailableAcceleratedMemory() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        try {
            GraphicsDevice[] gs = ge.getScreenDevices();
            // Get current amount of available memory in bytes for each screen
            for (GraphicsDevice g : gs) {
                // Workaround; see description
                VolatileImage im = g.getDefaultConfiguration().createCompatibleVolatileImage(1, 1);
                // Retrieve available free accelerated image memory
                int bytes = g.getAvailableAcceleratedMemory();
                // Release the temporary volatile image
                im.flush();

                return bytes;
            }
        } catch (HeadlessException e) {
            // Is thrown if there are no screen devices
        }
        return 0;
    }
    public void init(yass.renderer.YassSession s) {
        session = s;
    }
    public yass.renderer.YassSession getSession() {
        return session;
    }
    public void setVideoFrame(BufferedImage img) {
        videoFrame = img;
    }
    public boolean isPlaybackInterrupted() {
        return pisinterrupted;
    }
    public void setPlaybackInterrupted(boolean onoff) {
        pisinterrupted = onoff;
    }
    public boolean preparePlayback(long inpoint_ms, long endpoint_ms) {
        Graphics2D pg2 = (Graphics2D) getGraphics();
        if (pg2 == null) {
            return false;
        }
        pg2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        // g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
        // RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        // g2.setRenderingHint(RenderingHints.KEY_RENDERING,
        // RenderingHints.VALUE_RENDER_SPEED);

        pgb = getBackBuffer().createGraphics();
        // gb.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
        // RenderingHints.VALUE_ANTIALIAS_OFF);
        // gb.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
        // RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        // gb.setRenderingHint(RenderingHints.KEY_RENDERING,
        // RenderingHints.VALUE_RENDER_SPEED);

        ppos = playerPos;
        playerPos = -1;
        setPlaying(true);

        psheetpos = getViewPosition();

        int maxwait = 10;
        while (isRefreshing() && maxwait-- > 0) {
            try {
                Thread.sleep(10);
            } catch (Exception ignored) { }
        }
        // stalls sometimes in c.print:
        // sheet.refreshImage();
        return true;
    }

    public void startPlayback() {
    }

    private void recenterAbsolutePitchAfterPlaybackPageStep() {
        if (!isAbsolutePitchViewEnabled() || table == null) {
            return;
        }
        int noteRow = nextElement(getViewPosition().x);
        if (noteRow < 0) {
            noteRow = firstVisibleNote();
        }
        if (noteRow < 0) {
            return;
        }
        int[] pageRows = table.enlargeToPages(noteRow, noteRow);
        if (pageRows == null) {
            autoCenterAbsolutePitchView();
            return;
        }
        autoCenterAbsolutePitchView(pageRows[0], pageRows[1]);
    }

    public void updatePlayback(long pos_ms) {
        int newPlayerPos = toTimeline(pos_ms);

        if (newPlayerPos <= playerPos) {
            return;
        }
        playerPos = newPlayerPos;
        if (playerPos > clip.x + clip.width) {
            setTemporaryStop(true);
            setPlaying(false);
            if (live) {
                setViewToNextPage();
                playerPos = toTimeline(pos_ms);
            } else {
                Point p = getViewPosition();
                p.x += clip.width;
                setViewPosition(p);
            }
            recenterAbsolutePitchAfterPlaybackPageStep();
            paintComponent(pgb);
            setPlaying(true);
            setTemporaryStop(false);
        }
        if (isPlaybackInterrupted())
            return;
        if (!isRefreshing()) {
            VolatileImage plain = getPlainBuffer();
            if (showVideo()) {
                BufferedImage img = videoFrame;
                if (img != null) {
                    int w = plain.getWidth();
                    int h = plain.getHeight();
                    int hh = (int) (w * 3 / 4.0);
                    int yy = h / 2 - hh / 2;
                    pgb.setColor(Color.WHITE);
                    pgb.fillRect(0, 0, w, yy);
                    pgb.fillRect(0, yy, w, h);
                    pgb.drawImage(img, 0, yy, w, hh, null);
                } else {
                    pgb.drawImage(plain, 0, 0, null);
                }
                pgb.translate(-clip.x, 0);
                paintLines(pgb);
                paintPlainRectangles(pgb);
                pgb.translate(clip.x, 0);
            } else if (showBackground()) {
                BufferedImage img = getBackgroundImage();
                if (img != null) {
                    int w = plain.getWidth();
                    int h = plain.getHeight();
                    int hh = (int) (w * 3 / 4.0);
                    int yy = h / 2 - hh / 2;
                    pgb.setColor(Color.WHITE);
                    pgb.fillRect(0, 0, w, yy);
                    pgb.fillRect(0, yy, w, h);
                    pgb.drawImage(img, 0, yy, w, hh, null);
                } else {
                    pgb.drawImage(plain, 0, 0, null);
                }
                pgb.translate(-clip.x, 0);
                paintLines(pgb);
                paintPlainRectangles(pgb);
                pgb.translate(clip.x, 0);
            } else {
                int top = getTopLine() - 10;
                int w = plain.getWidth();
                int h = plain.getHeight() - top;
                    pgb.drawImage(plain, 0, top, w, top + h, 0, top, w,top + h, null);
            }

            if (getPlainBuffer().contentsLost())
                setErrorMessage(I18.get("sheet_msg_buffer_lost"));
            if (isPlaybackInterrupted())
                return;
            paintText(pgb);
            paintPlayerText(pgb);
              paintPlayerPosition(pgb, true);
                if (playerPos < clip.x)
                    paintWait(pgb, (int) fromTimeline(clip.x - playerPos));
              paintTemporaryNotes();
              paintRecordedNotes();

            Graphics2D pg2 = (Graphics2D) getGraphics();
            // Always blit the full back buffer to avoid stale pixels in the
            // upper sheet area (timeline/sticky zones) after split/layout changes.
            paintBackBuffer(pg2);
        }
    }

    public void finishPlayback() {
        Graphics2D pg2 = (Graphics2D) getGraphics();
        if (pg2 != null) {
            pg2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        if (isLive()) {
            previewEdit(false);
            showVideo(false);
        }
        showBackground(false);
        setLyricsVisible(true);
        if (!isLive()) {
            setViewPosition(psheetpos);
            setPlayerPosition(ppos);
        }
        setLive(false);
        setPlaying(false);
        repaint();
    }

    public JComponent getComponent() {
        return this;
    }
    public void setPause(boolean onoff) {
    }

    private ArrayList<YassSheetListener> listeners = new ArrayList<>();
    public void addYassSheetListener(YassSheetListener listener) {
        listeners.add(listener);
    }
    public void removeYassSheetListener(YassSheetListener listener) {
        listeners.remove(listener);
    }
    public void firePosChanged() {
        double posMs = fromTimeline(playerPos);
        for (YassSheetListener listener : listeners) listener.posChanged(this, posMs);
    }
    public void fireRangeChanged(int minH, int maxH, int minB, int maxB) {
        for (YassSheetListener listener : listeners) listener.rangeChanged(this, minH, maxH, minB, maxB);
    }
    public void firePropsChanged() {
        for (YassSheetListener listener : listeners) listener.propsChanged(this);
    }

    public void stopPlaying() {
        firePropertyChange("play", null, "stop");
    }

    public void setLyrics(YassLyrics lyrics) {
        this.lyrics = lyrics;
    }

    public void clearTempPitches() {
        this.tmpPitches = new ArrayList<>();
    }
    public String getLetterForNote(int pianoIdx) {
        if (pianoIdx < 0 || noteMapping == null || pianoIdx >= noteMapping.size()) {
            return "";
        }
        Integer mappedNote = noteMapping.get(pianoIdx);
        if (mappedNote == null || mappedNote == Integer.MIN_VALUE || actions == null || actions.getKeyboardLayout() == null) {
            return "";
        }
        return "[" + actions.getKeyboardLayout().getLetter(pianoIdx) + "]";
    }

    public void initNoteMapping(int lowestNote) {
        noteMapping = new ArrayList<>();
        int normalized = normalizeNoteHeight(lowestNote);
        while (!isWhiteNote(normalized)) {
            lowestNote--;
            normalized = normalizeNoteHeight(lowestNote);
        }
        int i = 0;
        boolean exit;
        do {
            noteMapping.add(i + lowestNote);
            exit = noteMapping.size() >= KeyboardMapping.QWERTZ.keys.size();
            normalized = normalizeNoteHeight(lowestNote + i);
            if (!exit && (normalized == 4 || normalized == 11)) {
                noteMapping.add(Integer.MIN_VALUE);
                exit = noteMapping.size() >= KeyboardMapping.QWERTZ.keys.size();
            }
            i++;
        } while (!exit);
    }
    
    private boolean isFocusInSongHeader() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return focusOwner != null && songHeader != null && SwingUtilities.isDescendingFrom(focusOwner, songHeader);
    }
}


