package yass.usdb;

import org.apache.commons.lang3.StringUtils;
import yass.I18;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class UsdbSongEditDiffDialog extends JDialog {
    private static final Set<String> IGNORED_HEADER_TAGS = new HashSet<>(Set.of(
            "MP3",
            "AUDIO",
            "VOCALS",
            "INSTRUMENTAL",
            "COVER",
            "BACKGROUND",
            "COMMENT",
            "VERSION"
    ));
    private static final Color CHANGED_LEFT = new Color(255, 238, 196);
    private static final Color CHANGED_RIGHT = new Color(201, 233, 255);
    private static final Color MISSING_LEFT = new Color(255, 225, 225);
    private static final Color MISSING_RIGHT = new Color(225, 255, 225);
    private static final Color APPENDED_RIGHT = new Color(226, 242, 214);
    private static final Color BLOCK_LEFT = new Color(255, 244, 214);
    private static final Color BLOCK_RIGHT = new Color(217, 239, 255);
    private static final Color CURRENT_BLOCK_LEFT = new Color(255, 214, 138);
    private static final Color CURRENT_BLOCK_RIGHT = new Color(150, 212, 255);
    private static final Color MISSING_ANCHOR = new Color(190, 190, 190);
    private static final Color FIELD_LEFT = new Color(255, 186, 92);
    private static final Color FIELD_RIGHT = new Color(102, 181, 255);

    private final JTextArea usdbArea;
    private final JTextArea localArea;
    private JScrollPane usdbScrollPane;
    private JScrollPane localScrollPane;
    private final JLabel statusLabel;
    private final JLabel diffCountLabel;
    private final JButton previousDiffButton;
    private final JButton nextDiffButton;
    private final JButton applyDiffButton;
    private final JButton saveLocallyButton;
    private final boolean allowCompleteVerification;
    private List<Integer> appendedLocalHeaderLines;
    private List<String> appendedLocalHeaderTags;
    private String initialLocalText;
    private List<DiffBlock> diffBlocks = List.of();
    private Map<Integer, Integer> leftToRightLineMap = Map.of();
    private Map<Integer, Integer> rightToLeftLineMap = Map.of();
    private int currentDiffIndex = -1;
    private boolean syncingScroll;
    private Result result;
    private ActionHandler actionHandler;
    private final JPanel busyGlassPane = new JPanel();

    public UsdbSongEditDiffDialog(Window owner,
                                  String songName,
                                  String usdbText,
                                  String localText,
                                  List<Integer> appendedLocalHeaderLines,
                                  List<String> appendedLocalHeaderTags) {
        this(owner, songName, usdbText, localText, appendedLocalHeaderLines, appendedLocalHeaderTags, false);
    }

    public UsdbSongEditDiffDialog(Window owner,
                                  String songName,
                                  String usdbText,
                                  String localText,
                                  List<Integer> appendedLocalHeaderLines,
                                  List<String> appendedLocalHeaderTags,
                                  boolean allowCompleteVerification) {
        super(owner, I18.get("usdb_edit_diff_title"), ModalityType.APPLICATION_MODAL);
        usdbArea = createTextArea(usdbText, false);
        localArea = createTextArea(localText, true);
        initialLocalText = StringUtils.defaultString(localText).replace("\r\n", "\n").replace('\r', '\n');
        statusLabel = new JLabel(" ");
        diffCountLabel = new JLabel(" ");
        previousDiffButton = new JButton("\u2191");
        previousDiffButton.setToolTipText(I18.get("usdb_edit_diff_prev"));
        nextDiffButton = new JButton("\u2193");
        nextDiffButton.setToolTipText(I18.get("usdb_edit_diff_next"));
        applyDiffButton = new JButton(">>");
        applyDiffButton.setToolTipText(I18.get("usdb_edit_diff_apply"));
        saveLocallyButton = new JButton(I18.get("usdb_edit_save_local"));
        this.allowCompleteVerification = allowCompleteVerification;
        this.appendedLocalHeaderLines = appendedLocalHeaderLines == null ? List.of() : List.copyOf(appendedLocalHeaderLines);
        this.appendedLocalHeaderTags = appendedLocalHeaderTags == null ? List.of() : List.copyOf(appendedLocalHeaderTags);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        buildUi(songName);
        bindListeners();
        refreshDiffHighlights();
        setPreferredSize(new Dimension(1040, 680));
        pack();
        setLocationRelativeTo(owner);
    }

    public Result showDialog() {
        setVisible(true);
        return result;
    }

    public void setActionHandler(ActionHandler actionHandler) {
        this.actionHandler = actionHandler;
    }

    public void setBusy(boolean busy) {
        busyGlassPane.setVisible(busy);
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
        usdbArea.setEnabled(!busy);
        localArea.setEnabled(!busy);
        updateDiffToolbar();
    }

    public void reloadTexts(String usdbText,
                            String localText,
                            List<Integer> appendedLocalHeaderLines,
                            List<String> appendedLocalHeaderTags) {
        this.initialLocalText = StringUtils.defaultString(localText).replace("\r\n", "\n").replace('\r', '\n');
        this.appendedLocalHeaderLines = appendedLocalHeaderLines == null ? List.of() : List.copyOf(appendedLocalHeaderLines);
        this.appendedLocalHeaderTags = appendedLocalHeaderTags == null ? List.of() : List.copyOf(appendedLocalHeaderTags);
        usdbArea.setText(StringUtils.defaultString(usdbText));
        localArea.setText(StringUtils.defaultString(localText));
        usdbArea.setCaretPosition(0);
        localArea.setCaretPosition(0);
        currentDiffIndex = -1;
        refreshDiffHighlights();
    }

    private void buildUi(String songName) {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.add(new JLabel(MessageFormat.format(I18.get("usdb_edit_diff_hint"), StringUtils.defaultIfBlank(songName, "?"))),
                BorderLayout.NORTH);
        header.add(createToolbar(), BorderLayout.CENTER);
        header.add(statusLabel, BorderLayout.SOUTH);
        content.add(header, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapPane(I18.get("usdb_edit_diff_usdb"), usdbArea, true),
                wrapPane(I18.get("usdb_edit_diff_local"), localArea, false));
        splitPane.setResizeWeight(0.5);
        content.add(splitPane, BorderLayout.CENTER);

        JButton cancelButton = new JButton(I18.get("wizard_cancel"));
        cancelButton.addActionListener(e -> {
            attemptCloseDialog();
        });
        JButton submitButton = new JButton(I18.get("usdb_edit_submit"));
        submitButton.addActionListener(e -> {
            if (actionHandler != null) {
                actionHandler.onAction(Action.SAVE_TO_USDB, localArea.getText(), this);
                return;
            }
            result = new Result(Action.SAVE_TO_USDB, localArea.getText());
            dispose();
        });
        saveLocallyButton.addActionListener(e -> {
            if (actionHandler != null) {
                actionHandler.onAction(Action.SAVE_LOCAL, localArea.getText(), this);
                return;
            }
            result = new Result(Action.SAVE_LOCAL, localArea.getText());
            dispose();
        });
        JButton completeVerificationButton = null;
        if (allowCompleteVerification) {
            completeVerificationButton = new JButton(I18.get("usdb_pending_complete_verification"));
            completeVerificationButton.addActionListener(e -> {
                if (actionHandler != null) {
                    actionHandler.onAction(Action.COMPLETE_VERIFICATION, localArea.getText(), this);
                    return;
                }
                result = new Result(Action.COMPLETE_VERIFICATION, localArea.getText());
                dispose();
            });
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        if (completeVerificationButton != null) {
            buttons.add(completeVerificationButton);
        }
        buttons.add(submitButton);
        buttons.add(saveLocallyButton);
        buttons.add(cancelButton);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        busyGlassPane.setOpaque(false);
        busyGlassPane.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        busyGlassPane.addMouseListener(new MouseAdapter() { });
        busyGlassPane.addMouseMotionListener(new MouseAdapter() { });
        setGlassPane(busyGlassPane);
    }

    private JPanel createToolbar() {
        previousDiffButton.addActionListener(e -> navigateDiff(-1));
        nextDiffButton.addActionListener(e -> navigateDiff(1));
        applyDiffButton.addActionListener(e -> applyCurrentDiffFromLeftToRight());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(previousDiffButton);
        left.add(nextDiffButton);
        left.add(applyDiffButton);

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.add(left, BorderLayout.WEST);
        toolbar.add(diffCountLabel, BorderLayout.EAST);
        return toolbar;
    }

    private JPanel wrapPane(String title, JTextArea area, boolean left) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel(title), BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(area);
        if (left) {
            usdbScrollPane = scrollPane;
        } else {
            localScrollPane = scrollPane;
        }
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JTextArea createTextArea(String text, boolean editable) {
        JTextArea area = new JTextArea(StringUtils.defaultString(text));
        area.setEditable(editable);
        area.setLineWrap(false);
        area.setWrapStyleWord(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setTabSize(4);
        return area;
    }

    private void bindListeners() {
        localArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshDiffHighlights();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshDiffHighlights();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshDiffHighlights();
            }
        });
        installSynchronizedScrolling();
        installCloseHandling();
    }

    private void installCloseHandling() {
        getRootPane().registerKeyboardAction(
                e -> attemptCloseDialog(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                attemptCloseDialog();
            }
        });
    }

    private void attemptCloseDialog() {
        if (hasUnsavedChanges()) {
            int option = JOptionPane.showConfirmDialog(this,
                    I18.get("usdb_edit_diff_unsaved"),
                    I18.get("usdb_edit_diff_title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (option != JOptionPane.YES_OPTION) {
                return;
            }
        }
        result = null;
        dispose();
    }

    private boolean hasUnsavedChanges() {
        String currentText = StringUtils.defaultString(localArea.getText()).replace("\r\n", "\n").replace('\r', '\n');
        return !StringUtils.equals(initialLocalText, currentText);
    }

    private void installSynchronizedScrolling() {
        ChangeListener usdbSync = e -> syncScrollFrom(usdbArea, localArea, leftToRightLineMap);
        ChangeListener localSync = e -> syncScrollFrom(localArea, usdbArea, rightToLeftLineMap);
        if (usdbScrollPane != null) {
            usdbScrollPane.getViewport().addChangeListener(usdbSync);
        }
        if (localScrollPane != null) {
            localScrollPane.getViewport().addChangeListener(localSync);
        }
    }

    private void refreshDiffHighlights() {
        clearHighlights(usdbArea);
        clearHighlights(localArea);
        List<DiffBlock> newDiffBlocks = new ArrayList<>();

        List<ComparedLine> leftHeaderLines = extractComparedHeaderLines(usdbArea.getText());
        List<ComparedLine> rightHeaderLines = extractComparedHeaderLines(localArea.getText());
        collectComparedLinePairDiffs(leftHeaderLines, rightHeaderLines, newDiffBlocks);
        highlightChangedHeaderFields(leftHeaderLines, rightHeaderLines);

        List<ComparedBodyLine> leftBodyLines = extractComparedBodyLines(usdbArea.getText());
        List<ComparedBodyLine> rightBodyLines = extractComparedBodyLines(localArea.getText());
        List<AlignedBodyPair> alignedBodyPairs = alignBodyLines(leftBodyLines, rightBodyLines);
        collectComparedBodyLinePairDiffs(alignedBodyPairs, newDiffBlocks);
        highlightChangedBodyFields(alignedBodyPairs);
        rebuildLineMaps(leftHeaderLines, rightHeaderLines, alignedBodyPairs);

        diffBlocks = mergeDiffBlocks(newDiffBlocks);
        if (diffBlocks.isEmpty()) {
            currentDiffIndex = -1;
        } else if (currentDiffIndex < 0 || currentDiffIndex >= diffBlocks.size()) {
            currentDiffIndex = 0;
        }

        for (int i = 0; i < diffBlocks.size(); i++) {
            DiffBlock block = diffBlocks.get(i);
            boolean current = i == currentDiffIndex;
            highlightDiffBlock(block, current);
        }

        for (Integer lineIndex : appendedLocalHeaderLines) {
            if (lineIndex != null && lineIndex >= 0 && !isRightLineInDiffBlock(lineIndex)) {
                highlightLine(localArea, lineIndex, APPENDED_RIGHT);
            }
        }

        String status = MessageFormat.format(I18.get("usdb_edit_diff_status"),
                leftHeaderLines.size() + leftBodyLines.size(),
                rightHeaderLines.size() + rightBodyLines.size());
        if (!appendedLocalHeaderTags.isEmpty()) {
            status += " " + MessageFormat.format(I18.get("usdb_edit_diff_appended_tags"),
                    String.join(", ", appendedLocalHeaderTags));
        }
        statusLabel.setText(status);
        statusLabel.setForeground(new Color(0, 80, 140));
        updateDiffToolbar();
    }

    private void clearHighlights(JTextArea area) {
        area.getHighlighter().removeAllHighlights();
    }

    private List<String> splitLines(String text) {
        String normalized = StringUtils.defaultString(text).replace("\r\n", "\n").replace('\r', '\n');
        String[] parts = normalized.split("\\n", -1);
        List<String> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            result.add(part);
        }
        return result;
    }

    private void collectComparedLinePairDiffs(List<ComparedLine> leftLines, List<ComparedLine> rightLines, List<DiffBlock> target) {
        int max = Math.max(leftLines.size(), rightLines.size());
        for (int i = 0; i < max; i++) {
            ComparedLine left = i < leftLines.size() ? leftLines.get(i) : null;
            ComparedLine right = i < rightLines.size() ? rightLines.get(i) : null;
            if (left == null && right != null) {
                target.add(DiffBlock.rightOnly(right.originalLineIndex()));
            } else if (right == null && left != null) {
                target.add(DiffBlock.leftOnly(left.originalLineIndex()));
            } else if (left != null && right != null && !StringUtils.equals(left.text(), right.text())) {
                target.add(DiffBlock.both(left.originalLineIndex(), right.originalLineIndex()));
            }
        }
    }

    private void collectComparedBodyLinePairDiffs(List<AlignedBodyPair> alignedPairs, List<DiffBlock> target) {
        for (AlignedBodyPair pair : alignedPairs) {
            ComparedBodyLine left = pair.left();
            ComparedBodyLine right = pair.right();
            if (left == null && right != null) {
                target.add(DiffBlock.rightOnly(right.originalLineIndex()));
            } else if (right == null && left != null) {
                target.add(DiffBlock.leftOnly(left.originalLineIndex()));
            } else if (left != null && right != null && !left.semanticEquals(right)) {
                target.add(DiffBlock.both(left.originalLineIndex(), right.originalLineIndex()));
            }
        }
    }

    private void highlightChangedHeaderFields(List<ComparedLine> leftLines, List<ComparedLine> rightLines) {
        int max = Math.max(leftLines.size(), rightLines.size());
        for (int i = 0; i < max; i++) {
            ComparedLine left = i < leftLines.size() ? leftLines.get(i) : null;
            ComparedLine right = i < rightLines.size() ? rightLines.get(i) : null;
            if (left == null || right == null || StringUtils.equals(left.text(), right.text())) {
                continue;
            }
            highlightDifference(usdbArea, left.originalLineIndex(), left.text(), right.text(), FIELD_LEFT);
            highlightDifference(localArea, right.originalLineIndex(), right.text(), left.text(), FIELD_RIGHT);
        }
    }

    private List<AlignedBodyPair> alignBodyLines(List<ComparedBodyLine> leftLines, List<ComparedBodyLine> rightLines) {
        int leftSize = leftLines.size();
        int rightSize = rightLines.size();
        int[][] score = new int[leftSize + 1][rightSize + 1];
        Direction[][] path = new Direction[leftSize + 1][rightSize + 1];

        for (int i = 1; i <= leftSize; i++) {
            score[i][0] = score[i - 1][0] - 3;
            path[i][0] = Direction.UP;
        }
        for (int j = 1; j <= rightSize; j++) {
            score[0][j] = score[0][j - 1] - 3;
            path[0][j] = Direction.LEFT;
        }

        for (int i = 1; i <= leftSize; i++) {
            for (int j = 1; j <= rightSize; j++) {
                int diagonal = score[i - 1][j - 1] + alignmentScore(leftLines.get(i - 1), rightLines.get(j - 1));
                int up = score[i - 1][j] - 3;
                int left = score[i][j - 1] - 3;
                if (diagonal >= up && diagonal >= left) {
                    score[i][j] = diagonal;
                    path[i][j] = Direction.DIAGONAL;
                } else if (up >= left) {
                    score[i][j] = up;
                    path[i][j] = Direction.UP;
                } else {
                    score[i][j] = left;
                    path[i][j] = Direction.LEFT;
                }
            }
        }

        List<AlignedBodyPair> result = new ArrayList<>();
        int i = leftSize;
        int j = rightSize;
        while (i > 0 || j > 0) {
            Direction direction = path[i][j];
            if (direction == Direction.DIAGONAL) {
                result.add(0, new AlignedBodyPair(leftLines.get(i - 1), rightLines.get(j - 1)));
                i--;
                j--;
            } else if (direction == Direction.UP) {
                result.add(0, new AlignedBodyPair(leftLines.get(i - 1), null));
                i--;
            } else {
                result.add(0, new AlignedBodyPair(null, rightLines.get(j - 1)));
                j--;
            }
        }
        return result;
    }

    private int alignmentScore(ComparedBodyLine left, ComparedBodyLine right) {
        if (left.semanticEquals(right)) {
            return 8;
        }
        ParsedBodyLine leftParsed = left.parsed();
        ParsedBodyLine rightParsed = right.parsed();
        if (Objects.equals(leftParsed.normalizedText(), rightParsed.normalizedText())
                && StringUtils.isNotBlank(leftParsed.normalizedText())) {
            return 5;
        }
        if (Objects.equals(leftParsed.type(), rightParsed.type())
                && Objects.equals(leftParsed.beat(), rightParsed.beat())) {
            return 3;
        }
        if (Objects.equals(leftParsed.type(), rightParsed.type())) {
            return 1;
        }
        return -4;
    }

    private void highlightChangedBodyFields(List<AlignedBodyPair> alignedPairs) {
        for (AlignedBodyPair pair : alignedPairs) {
            ComparedBodyLine left = pair.left();
            ComparedBodyLine right = pair.right();
            if (left == null || right == null || left.semanticEquals(right)) {
                continue;
            }
            highlightChangedFields(usdbArea, left, right, true);
            highlightChangedFields(localArea, right, left, false);
        }
    }

    private void highlightChangedFields(JTextArea area,
                                        ComparedBodyLine current,
                                        ComparedBodyLine other,
                                        boolean leftSide) {
        ParsedBodyLine currentParsed = current.parsed();
        ParsedBodyLine otherParsed = other.parsed();
        Color color = leftSide ? FIELD_LEFT : FIELD_RIGHT;

        if (!Objects.equals(currentParsed.type(), otherParsed.type())) {
            highlightToken(area, current.originalLineIndex(), 0, color);
        }
        if (!Objects.equals(currentParsed.beat(), otherParsed.beat())) {
            highlightToken(area, current.originalLineIndex(), 1, color);
        }
        if (!Objects.equals(currentParsed.length(), otherParsed.length())) {
            highlightToken(area, current.originalLineIndex(), 2, color);
        }
        if (!Objects.equals(currentParsed.pitch(), otherParsed.pitch())) {
            highlightToken(area, current.originalLineIndex(), 3, color);
        }
        if (!Objects.equals(currentParsed.normalizedText(), otherParsed.normalizedText())
                || !Objects.equals(currentParsed.text(), otherParsed.text())) {
            highlightToken(area, current.originalLineIndex(), 4, color);
        }
    }

    private void highlightToken(JTextArea area, int lineIndex, int tokenIndex, Color color) {
        try {
            int lineStart = area.getLineStartOffset(lineIndex);
            int lineEnd = area.getLineEndOffset(lineIndex);
            String line = area.getDocument().getText(lineStart, Math.max(0, lineEnd - lineStart));
            int[] range = tokenRange(line, tokenIndex);
            if (range == null) {
                highlightLine(area, lineIndex, color);
                return;
            }
            Highlighter.HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(color);
            area.getHighlighter().addHighlight(lineStart + range[0], lineStart + range[1], painter);
        } catch (BadLocationException ignored) {
        }
    }

    private void highlightDifference(JTextArea area, int lineIndex, String current, String other, Color color) {
        try {
            int lineStart = area.getLineStartOffset(lineIndex);
            int prefix = commonPrefixLength(current, other);
            int suffix = commonSuffixLength(current, other, prefix);
            int currentDiffStart = prefix;
            int currentDiffEnd = Math.max(prefix, current.length() - suffix);
            if (currentDiffStart == currentDiffEnd && current.length() > 0) {
                if (currentDiffStart >= current.length()) {
                    currentDiffStart = Math.max(0, current.length() - 1);
                }
                currentDiffEnd = Math.min(current.length(), currentDiffStart + 1);
            }
            Highlighter.HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(color);
            area.getHighlighter().addHighlight(lineStart + currentDiffStart, lineStart + currentDiffEnd, painter);
        } catch (BadLocationException ignored) {
        }
    }

    private int commonPrefixLength(String left, String right) {
        int limit = Math.min(left.length(), right.length());
        int index = 0;
        while (index < limit && left.charAt(index) == right.charAt(index)) {
            index++;
        }
        return index;
    }

    private int commonSuffixLength(String left, String right, int prefixLength) {
        int leftIndex = left.length() - 1;
        int rightIndex = right.length() - 1;
        int suffix = 0;
        while (leftIndex >= prefixLength && rightIndex >= prefixLength && left.charAt(leftIndex) == right.charAt(rightIndex)) {
            suffix++;
            leftIndex--;
            rightIndex--;
        }
        return suffix;
    }

    private int[] tokenRange(String line, int tokenIndex) {
        int token = 0;
        int i = 0;
        while (i < line.length()) {
            while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
                i++;
            }
            if (i >= line.length()) {
                break;
            }
            int start = i;
            while (i < line.length() && !Character.isWhitespace(line.charAt(i))) {
                i++;
            }
            int end = i;
            if (token == tokenIndex) {
                return new int[]{start, end};
            }
            token++;
        }
        if (tokenIndex == 4 && token > 4) {
            // fallback: text token may absorb the rest of the line
            int[] firstText = tokenRange(line, 4);
            if (firstText != null) {
                return new int[]{firstText[0], line.stripTrailing().length()};
            }
        }
        return null;
    }

    private List<ComparedLine> extractComparedHeaderLines(String text) {
        List<String> allLines = splitLines(text);
        List<ComparedLine> result = new ArrayList<>();
        int bodyStartIndex = firstBodyLineIndex(allLines);
        for (int i = 0; i < bodyStartIndex; i++) {
            String line = allLines.get(i);
            String trimmed = StringUtils.trimToEmpty(line);
            if (shouldCompareHeader(trimmed)) {
                result.add(new ComparedLine(i, line));
            }
        }
        return result;
    }

    private List<ComparedBodyLine> extractComparedBodyLines(String text) {
        List<String> allLines = splitLines(text);
        List<ComparedBodyLine> result = new ArrayList<>();
        int bodyStartIndex = firstBodyLineIndex(allLines);
        for (int i = bodyStartIndex; i < allLines.size(); i++) {
            String line = allLines.get(i);
            String trimmed = StringUtils.trimToEmpty(line);
            if (StringUtils.isEmpty(trimmed)) {
                continue;
            }
            result.add(new ComparedBodyLine(i, line, parseBodyLine(trimmed)));
        }
        return result;
    }

    private int firstBodyLineIndex(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (!StringUtils.trimToEmpty(lines.get(i)).startsWith("#")) {
                return i;
            }
        }
        return lines.size();
    }

    private boolean shouldCompareHeader(String trimmedLine) {
        if (!trimmedLine.startsWith("#")) {
            return false;
        }
        int colon = trimmedLine.indexOf(':');
        if (colon <= 1) {
            return false;
        }
        String tag = StringUtils.upperCase(trimmedLine.substring(1, colon));
        return !IGNORED_HEADER_TAGS.contains(tag);
    }

    private void highlightLine(JTextArea area, int lineIndex, Color color) {
        try {
            int start = area.getLineStartOffset(lineIndex);
            int end = area.getLineEndOffset(lineIndex);
            Highlighter.HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(color);
            area.getHighlighter().addHighlight(start, Math.max(start, end - 1), painter);
        } catch (BadLocationException ignored) {
        }
    }

    private void highlightLineRange(JTextArea area, int startLineIndex, int endLineIndex, Color color) {
        for (int i = startLineIndex; i <= endLineIndex; i++) {
            highlightLine(area, i, color);
        }
    }

    private void highlightDiffBlock(DiffBlock block, boolean current) {
        if (block.hasLeft()) {
            highlightLineRange(usdbArea,
                    block.leftStartLineIndex(),
                    block.leftEndLineIndex(),
                    current ? CURRENT_BLOCK_LEFT : (block.hasRight() ? BLOCK_LEFT : MISSING_LEFT));
        }
        if (block.hasRight()) {
            highlightLineRange(localArea,
                    block.rightStartLineIndex(),
                    block.rightEndLineIndex(),
                    current ? CURRENT_BLOCK_RIGHT : (block.hasLeft() ? BLOCK_RIGHT : MISSING_RIGHT));
        }
        if (current && block.hasLeft() && !block.hasRight()) {
            highlightMissingAnchor(localArea, usdbArea, block.leftStartLineIndex());
        } else if (current && block.hasRight() && !block.hasLeft()) {
            highlightMissingAnchor(usdbArea, localArea, block.rightStartLineIndex());
        }
    }

    private List<DiffBlock> mergeDiffBlocks(List<DiffBlock> rawBlocks) {
        List<DiffBlock> merged = new ArrayList<>();
        for (DiffBlock block : rawBlocks) {
            if (merged.isEmpty()) {
                merged.add(block);
                continue;
            }
            DiffBlock last = merged.get(merged.size() - 1);
            if (last.canMergeWith(block)) {
                merged.set(merged.size() - 1, last.mergeWith(block));
            } else {
                merged.add(block);
            }
        }
        return merged;
    }

    private boolean isRightLineInDiffBlock(int lineIndex) {
        for (DiffBlock block : diffBlocks) {
            if (block.hasRight()
                    && lineIndex >= block.rightStartLineIndex()
                    && lineIndex <= block.rightEndLineIndex()) {
                return true;
            }
        }
        return false;
    }

    private void updateDiffToolbar() {
        boolean hasDiffs = !diffBlocks.isEmpty();
        boolean busy = busyGlassPane.isVisible();
        previousDiffButton.setEnabled(!busy && hasDiffs);
        nextDiffButton.setEnabled(!busy && hasDiffs);
        applyDiffButton.setEnabled(!busy && hasDiffs && currentDiffIndex >= 0 && currentDiffIndex < diffBlocks.size());
        saveLocallyButton.setEnabled(!busy);
        if (!hasDiffs) {
            diffCountLabel.setText(I18.get("usdb_edit_diff_none"));
            return;
        }
        diffCountLabel.setText(MessageFormat.format(I18.get("usdb_edit_diff_count"),
                diffBlocks.size(),
                currentDiffIndex + 1));
    }

    private void navigateDiff(int delta) {
        if (diffBlocks.isEmpty()) {
            return;
        }
        currentDiffIndex = (currentDiffIndex + delta + diffBlocks.size()) % diffBlocks.size();
        refreshDiffHighlights();
        scrollToDiffBlock(diffBlocks.get(currentDiffIndex));
    }

    private void applyCurrentDiffFromLeftToRight() {
        if (diffBlocks.isEmpty() || currentDiffIndex < 0 || currentDiffIndex >= diffBlocks.size()) {
            return;
        }
        DiffBlock block = diffBlocks.get(currentDiffIndex);
        List<String> leftLines = splitLines(usdbArea.getText());
        List<String> rightLines = new ArrayList<>(splitLines(localArea.getText()));

        List<String> replacement = new ArrayList<>();
        if (block.hasLeft()) {
            for (int i = block.leftStartLineIndex(); i <= block.leftEndLineIndex(); i++) {
                replacement.add(leftLines.get(i));
            }
        }

        int replaceStart;
        int replaceEndExclusive;
        if (block.hasRight()) {
            replaceStart = block.rightStartLineIndex();
            replaceEndExclusive = block.rightEndLineIndex() + 1;
        } else if (block.hasLeft()) {
            replaceStart = findNearestBeatLine(localArea, extractBeatAtLine(usdbArea, block.leftStartLineIndex()));
            replaceEndExclusive = replaceStart;
        } else {
            return;
        }

        replaceStart = Math.max(0, Math.min(replaceStart, rightLines.size()));
        replaceEndExclusive = Math.max(replaceStart, Math.min(replaceEndExclusive, rightLines.size()));
        int targetRightLine = replaceStart;

        rightLines.subList(replaceStart, replaceEndExclusive).clear();
        rightLines.addAll(replaceStart, replacement);
        localArea.setText(String.join("\n", rightLines));
        SwingUtilities.invokeLater(() -> {
            currentDiffIndex = findClosestDiffIndexForRightLine(targetRightLine);
            if (currentDiffIndex >= 0 && currentDiffIndex < diffBlocks.size()) {
                scrollToDiffBlock(diffBlocks.get(currentDiffIndex));
            } else {
                scrollToLine(localArea, targetRightLine);
            }
        });
    }

    private int findClosestDiffIndexForRightLine(int lineIndex) {
        if (diffBlocks.isEmpty()) {
            return -1;
        }
        int bestIndex = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < diffBlocks.size(); i++) {
            DiffBlock block = diffBlocks.get(i);
            int anchor = block.hasRight()
                    ? block.rightStartLineIndex()
                    : rightToLeftLineMap.entrySet().stream()
                    .filter(entry -> entry.getValue() == block.leftStartLineIndex())
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(lineIndex);
            int distance = Math.abs(anchor - lineIndex);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private void scrollToDiffBlock(DiffBlock block) {
        if (block.hasLeft()) {
            scrollToLine(usdbArea, block.leftStartLineIndex());
        } else if (block.hasRight()) {
            scrollToAnchorLine(usdbArea, localArea, block.rightStartLineIndex());
        }
        if (block.hasRight()) {
            scrollToLine(localArea, block.rightStartLineIndex());
        } else if (block.hasLeft()) {
            scrollToAnchorLine(localArea, usdbArea, block.leftStartLineIndex());
        }
    }

    private void scrollToLine(JTextArea area, int lineIndex) {
        try {
            if (area.getDocument().getLength() <= 0) {
                return;
            }
            int safeLineIndex = Math.max(0, Math.min(lineIndex, Math.max(0, area.getLineCount() - 1)));
            int offset = area.getLineStartOffset(safeLineIndex);
            var rect2d = area.modelToView2D(offset);
            if (rect2d == null) {
                return;
            }
            Rectangle rect = rect2d.getBounds();
            rect.height = Math.max(rect.height, 48);
            area.scrollRectToVisible(rect);
            area.setCaretPosition(offset);
        } catch (BadLocationException ignored) {
        }
    }

    private void scrollToAnchorLine(JTextArea targetArea, JTextArea referenceArea, int referenceLineIndex) {
        int anchorLine = findNearestBeatLine(targetArea, extractBeatAtLine(referenceArea, referenceLineIndex));
        scrollToLine(targetArea, anchorLine);
    }

    private void highlightMissingAnchor(JTextArea targetArea, JTextArea referenceArea, int referenceLineIndex) {
        int anchorLine = findNearestBeatLine(targetArea, extractBeatAtLine(referenceArea, referenceLineIndex));
        highlightLine(targetArea, anchorLine, MISSING_ANCHOR);
    }

    private Integer extractBeatAtLine(JTextArea area, int lineIndex) {
        try {
            int start = area.getLineStartOffset(lineIndex);
            int end = area.getLineEndOffset(lineIndex);
            String line = area.getDocument().getText(start, Math.max(0, end - start)).trim();
            ParsedBodyLine parsed = parseBodyLine(line);
            return parsed.beat();
        } catch (BadLocationException ignored) {
            return null;
        }
    }

    private int findNearestBeatLine(JTextArea area, Integer beat) {
        List<String> lines = splitLines(area.getText());
        if (beat == null) {
            return 0;
        }
        int bestLine = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < lines.size(); i++) {
            ParsedBodyLine parsed = parseBodyLine(StringUtils.trimToEmpty(lines.get(i)));
            if (parsed.beat() == null) {
                continue;
            }
            int distance = Math.abs(parsed.beat() - beat);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestLine = i;
                if (distance == 0) {
                    break;
                }
            }
        }
        return bestLine;
    }

    private void rebuildLineMaps(List<ComparedLine> leftHeaderLines,
                                 List<ComparedLine> rightHeaderLines,
                                 List<AlignedBodyPair> alignedBodyPairs) {
        Map<Integer, Integer> leftMap = new HashMap<>();
        Map<Integer, Integer> rightMap = new HashMap<>();
        int headerMax = Math.min(leftHeaderLines.size(), rightHeaderLines.size());
        for (int i = 0; i < headerMax; i++) {
            leftMap.put(leftHeaderLines.get(i).originalLineIndex(), rightHeaderLines.get(i).originalLineIndex());
            rightMap.put(rightHeaderLines.get(i).originalLineIndex(), leftHeaderLines.get(i).originalLineIndex());
        }
        for (AlignedBodyPair pair : alignedBodyPairs) {
            if (pair.left() != null && pair.right() != null) {
                leftMap.put(pair.left().originalLineIndex(), pair.right().originalLineIndex());
                rightMap.put(pair.right().originalLineIndex(), pair.left().originalLineIndex());
            }
        }
        leftToRightLineMap = leftMap;
        rightToLeftLineMap = rightMap;
    }

    private void syncScrollFrom(JTextArea sourceArea, JTextArea targetArea, Map<Integer, Integer> lineMap) {
        if (syncingScroll || lineMap.isEmpty() || !sourceArea.isShowing() || !targetArea.isShowing()) {
            return;
        }
        syncingScroll = true;
        try {
            int sourceLine = getVisibleTopLine(sourceArea);
            int targetLine = mapNearestLine(sourceLine, lineMap);
            scrollToLine(targetArea, targetLine);
        } finally {
            syncingScroll = false;
        }
    }

    private int getVisibleTopLine(JTextArea area) {
        Rectangle visible = area.getVisibleRect();
        int offset = area.viewToModel2D(new Point(visible.x, visible.y));
        try {
            return area.getLineOfOffset(offset);
        } catch (BadLocationException ignored) {
            return 0;
        }
    }

    private int mapNearestLine(int sourceLine, Map<Integer, Integer> lineMap) {
        Integer exact = lineMap.get(sourceLine);
        if (exact != null) {
            return exact;
        }
        int bestSource = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (Integer candidate : lineMap.keySet()) {
            int distance = Math.abs(candidate - sourceLine);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestSource = candidate;
            }
        }
        return bestSource >= 0 ? lineMap.get(bestSource) : 0;
    }

    public record Result(Action action, String submittedText) {
    }

    public interface ActionHandler {
        void onAction(Action action, String submittedText, UsdbSongEditDiffDialog dialog);
    }

    public enum Action {
        SAVE_TO_USDB,
        SAVE_LOCAL,
        COMPLETE_VERIFICATION
    }

    private record ComparedLine(int originalLineIndex, String text) {
    }

    private ParsedBodyLine parseBodyLine(String trimmedLine) {
        if (StringUtils.isEmpty(trimmedLine)) {
            return new ParsedBodyLine("EMPTY", null, null, null, "", "");
        }
        String[] parts = trimmedLine.split("\\s+", 5);
        String type = parts[0];
        if (Set.of(":", "*", "F", "R", "G").contains(type) && parts.length >= 5) {
            return new ParsedBodyLine(type,
                    parseInteger(parts[1]),
                    parseInteger(parts[2]),
                    parseInteger(parts[3]),
                    parts[4],
                    normalizeBodyText(parts[4]));
        }
        if ("-".equals(type) && parts.length >= 2) {
            return new ParsedBodyLine(type, parseInteger(parts[1]), null, null, "", "");
        }
        if ("E".equals(type)) {
            return new ParsedBodyLine(type, null, null, null, "", "");
        }
        return new ParsedBodyLine("RAW", null, null, null, trimmedLine, normalizeBodyText(trimmedLine));
    }

    private String normalizeBodyText(String text) {
        return StringUtils.lowerCase(StringUtils.trimToEmpty(text));
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.parseInt(StringUtils.trimToEmpty(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record ComparedBodyLine(int originalLineIndex, String text, ParsedBodyLine parsed) {
        boolean semanticEquals(ComparedBodyLine other) {
            return other != null && Objects.equals(parsed, other.parsed);
        }
    }

    private record ParsedBodyLine(String type,
                                  Integer beat,
                                  Integer length,
                                  Integer pitch,
                                  String text,
                                  String normalizedText) {
    }

    private record AlignedBodyPair(ComparedBodyLine left, ComparedBodyLine right) {
    }

    private enum Direction {
        DIAGONAL,
        UP,
        LEFT
    }

    private record DiffBlock(Integer leftStartLineIndex,
                             Integer leftEndLineIndex,
                             Integer rightStartLineIndex,
                             Integer rightEndLineIndex) {
        static DiffBlock both(int leftLineIndex, int rightLineIndex) {
            return new DiffBlock(leftLineIndex, leftLineIndex, rightLineIndex, rightLineIndex);
        }

        static DiffBlock leftOnly(int leftLineIndex) {
            return new DiffBlock(leftLineIndex, leftLineIndex, null, null);
        }

        static DiffBlock rightOnly(int rightLineIndex) {
            return new DiffBlock(null, null, rightLineIndex, rightLineIndex);
        }

        boolean hasLeft() {
            return leftStartLineIndex != null && leftEndLineIndex != null;
        }

        boolean hasRight() {
            return rightStartLineIndex != null && rightEndLineIndex != null;
        }

        boolean canMergeWith(DiffBlock other) {
            return isAdjacent(leftEndLineIndex, other.leftStartLineIndex)
                    && isAdjacent(rightEndLineIndex, other.rightStartLineIndex);
        }

        private boolean isAdjacent(Integer end, Integer start) {
            if (end == null && start == null) {
                return true;
            }
            if (end == null || start == null) {
                return false;
            }
            return start <= end + 1;
        }

        DiffBlock mergeWith(DiffBlock other) {
            return new DiffBlock(
                    hasLeft() ? leftStartLineIndex : other.leftStartLineIndex,
                    other.hasLeft() ? other.leftEndLineIndex : leftEndLineIndex,
                    hasRight() ? rightStartLineIndex : other.rightStartLineIndex,
                    other.hasRight() ? other.rightEndLineIndex : rightEndLineIndex
            );
        }
    }
}
