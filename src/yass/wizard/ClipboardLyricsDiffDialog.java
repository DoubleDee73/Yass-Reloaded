package yass.wizard;

import org.apache.commons.lang3.StringUtils;
import yass.I18;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public class ClipboardLyricsDiffDialog extends JDialog {
    private static final Color CHANGED_LEFT = new Color(255, 238, 196);
    private static final Color CHANGED_RIGHT = new Color(201, 233, 255);
    private static final Color MISSING_LEFT = new Color(255, 225, 225);
    private static final Color MISSING_RIGHT = new Color(225, 255, 225);

    private final JTextArea transcriptArea;
    private final JTextArea clipboardArea;
    private final JLabel statusLabel;
    private final JButton applyButton;
    private Result result;

    public ClipboardLyricsDiffDialog(Window owner, String transcriptText, String clipboardText) {
        super(owner, I18.get("create_lyrics_paste_diff_title"), ModalityType.APPLICATION_MODAL);
        transcriptArea = createTextArea(transcriptText, true);
        clipboardArea = createTextArea(clipboardText, true);
        statusLabel = new JLabel(" ");
        applyButton = new JButton(I18.get("create_lyrics_paste_apply"));
        buildUi();
        bindListeners();
        refreshDiffHighlights();
        setPreferredSize(new Dimension(980, 620));
        pack();
        setLocationRelativeTo(owner);
    }

    public Result showDialog() {
        setVisible(true);
        return result;
    }

    private void buildUi() {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel header = new JPanel(new BorderLayout(0, 6));
        JLabel title = new JLabel(I18.get("create_lyrics_paste_diff_hint"));
        title.setBorder(new EmptyBorder(0, 0, 4, 0));
        header.add(title, BorderLayout.NORTH);
        statusLabel.setForeground(UIManager.getColor("Label.foreground"));
        header.add(statusLabel, BorderLayout.SOUTH);
        content.add(header, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapPane(I18.get("create_lyrics_paste_diff_transcript"), transcriptArea),
                wrapPane(I18.get("create_lyrics_paste_diff_clipboard"), clipboardArea));
        splitPane.setResizeWeight(0.5);
        content.add(splitPane, BorderLayout.CENTER);

        JButton cancelButton = new JButton(I18.get("wizard_cancel"));
        cancelButton.addActionListener(e -> {
            result = null;
            dispose();
        });
        JButton pasteButton = new JButton(I18.get("create_lyrics_paste"));
        pasteButton.addActionListener(e -> pasteClipboardIntoRightSide());
        applyButton.addActionListener(e -> {
            result = new Result(transcriptArea.getText(), clipboardArea.getText());
            dispose();
        });

        JPanel buttons = new JPanel(new BorderLayout());
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftButtons.add(pasteButton);
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightButtons.add(applyButton);
        rightButtons.add(cancelButton);
        buttons.add(leftButtons, BorderLayout.WEST);
        buttons.add(rightButtons, BorderLayout.EAST);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
    }

    private JPanel wrapPane(String title, JTextArea area) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
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
        DocumentListener listener = new DocumentListener() {
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
        };
        transcriptArea.getDocument().addDocumentListener(listener);
        clipboardArea.getDocument().addDocumentListener(listener);
    }

    private void refreshDiffHighlights() {
        clearHighlights(transcriptArea);
        clearHighlights(clipboardArea);

        List<String> transcriptLines = splitLines(transcriptArea.getText());
        List<String> clipboardLines = splitLines(clipboardArea.getText());
        int max = Math.max(transcriptLines.size(), clipboardLines.size());
        boolean sameLineCount = transcriptLines.size() == clipboardLines.size();

        for (int i = 0; i < max; i++) {
            String left = i < transcriptLines.size() ? transcriptLines.get(i) : null;
            String right = i < clipboardLines.size() ? clipboardLines.get(i) : null;
            if (left == null) {
                highlightLine(clipboardArea, i, MISSING_RIGHT);
            } else if (right == null) {
                highlightLine(transcriptArea, i, MISSING_LEFT);
            } else if (!StringUtils.equals(left, right)) {
                highlightDifference(transcriptArea, i, left, right, CHANGED_LEFT);
                highlightDifference(clipboardArea, i, right, left, CHANGED_RIGHT);
            }
        }

        if (sameLineCount) {
            statusLabel.setText(I18.get("create_lyrics_paste_diff_status_ok"));
            statusLabel.setForeground(new Color(0, 110, 0));
        } else {
            statusLabel.setText(MessageFormat.format(I18.get("create_lyrics_paste_diff_status_line_mismatch"), transcriptLines.size(), clipboardLines.size()));
            statusLabel.setForeground(new Color(150, 80, 0));
        }
        applyButton.setEnabled(StringUtils.isNotBlank(transcriptArea.getText()) || StringUtils.isNotBlank(clipboardArea.getText()));
    }

    private void pasteClipboardIntoRightSide() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard == null || !clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return;
            }
            Object value = clipboard.getData(DataFlavor.stringFlavor);
            if (!(value instanceof String pastedText)) {
                return;
            }
            clipboardArea.setText(StringUtils.defaultString(pastedText).replace("\r\n", "\n").replace('\r', '\n'));
        } catch (Exception ignored) {
        }
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

    private void highlightLine(JTextArea area, int lineIndex, Color color) {
        try {
            int start = area.getLineStartOffset(lineIndex);
            int end = area.getLineEndOffset(lineIndex);
            Highlighter.HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(color);
            area.getHighlighter().addHighlight(start, Math.max(start, end - 1), painter);
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

    public record Result(String transcriptText, String clipboardText) {
    }
}
