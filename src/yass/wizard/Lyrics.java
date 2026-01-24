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

package yass.wizard;

import com.nexes.wizard.Wizard;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import yass.*;
import yass.analysis.SubtitleParser;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Description of the Class
 *
 * @author Saruta
 */
@Getter
public class Lyrics extends JPanel {
    /**
     * Description of the Field
     */
    public final static String ID = "lyrics";
    private static final long serialVersionUID = 2887575440389998645L;
    private Wizard wizard;
    private JTextArea lyricsArea = null;
    private JComboBox<String> language = null;
    private JTextField subtitleFileField = null;
    private YassProperties yassProperties;
    private Map<Integer, String> subtitles = null;

    /**
     * Constructor for the Lyrics object
     *
     * @param w Description of the Parameter
     */
    public Lyrics(Wizard w, YassProperties yassProperties) {
        wizard = w;
        this.yassProperties = yassProperties;
        JLabel iconLabel = new JLabel();
        setLayout(new BorderLayout());
        iconLabel.setIcon(new ImageIcon(this.getClass().getResource("clouds.jpg")));
        add("West", iconLabel);
        add("Center", getContentPanel());
    }


    /**
     * Gets the contentPanel attribute of the Lyrics object
     *
     * @return The contentPanel value
     */
    private JPanel getContentPanel() {
        JPanel content = new JPanel(new BorderLayout());
        JTextPane txt = new JTextPane();
        HTMLDocument doc = (HTMLDocument) txt.getEditorKitForContentType("text/html").createDefaultDocument();
        doc.setAsynchronousLoadPriority(-1);
        txt.setDocument(doc);
        URL url = I18.getResource("create_lyrics.html");
        try {
            txt.setPage(url);
        } catch (Exception ignored) {
        }
        txt.setEditable(false);
        content.add("Center", new JScrollPane(txt));

        JPanel lyricsPanel = new JPanel(new BorderLayout(5, 5));

        // Subtitle file selection
        JPanel subtitlePanel = new JPanel(new BorderLayout(5, 0));
        subtitlePanel.add(new JLabel("Subtitle file:"), BorderLayout.WEST);
        subtitleFileField = new JTextField();
        subtitleFileField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                handleUpdate();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                handleUpdate();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                handleUpdate();
            }

            private void handleUpdate() {
                parseSubtitles(subtitleFileField.getText());
            }
        });
        subtitlePanel.add(subtitleFileField, BorderLayout.CENTER);
        JButton subtitleBrowseButton = new JButton(I18.get("options_browse"));
        subtitleBrowseButton.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(yassProperties.getProperty("temp-dir"));
            fc.setDialogTitle("Select Subtitle file");
            FileNameExtensionFilter fileFilter = new FileNameExtensionFilter("Subtitles", "vtt", "srt");
            fc.setFileFilter(fileFilter);
            if (fc.showOpenDialog(wizard.getDialog()) == JFileChooser.APPROVE_OPTION) {
                subtitleFileField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        subtitlePanel.add(subtitleBrowseButton, BorderLayout.EAST);
        lyricsPanel.add(subtitlePanel, BorderLayout.NORTH);

        lyricsArea = new JTextArea(10, 20);
        YassUtils.addChangeListener(lyricsArea, e -> determineLanguage());
        lyricsPanel.add(new JScrollPane(lyricsArea), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, 3));
        List<String> languages = YassLanguageUtils.getSupportedLanguages();
        languages.add(0, "");
        language = new JComboBox<>(languages.toArray(new String[0]));
        try {
            if (wizard.getValues().contains("language") && languages.contains(wizard.getValue("language"))) {
                language.setSelectedItem(wizard.getValue("language"));
            }
        } catch (NoSuchMethodError nsme) {
            // ignore
        }
        language.addItemListener(
                e -> {
                        wizard.setValue("language", (String)language.getSelectedItem());
                });
        buttons.add(new JLabel(I18.get("options_group1_language")));
        buttons.add(language);
        buttons.add(new JLabel(""));

        lyricsPanel.add(buttons, BorderLayout.SOUTH);
        content.add("South", lyricsPanel);
        return content;
    }

    /**
     * Sets the path for the subtitle file and triggers parsing.
     * @param path The absolute path to the subtitle file.
     */
    public void setSubtitleFile(String path) {
        subtitleFileField.setText(path);
    }

    private void determineLanguage() {
        if (language != null && StringUtils.isEmpty((String)language.getSelectedItem()) && 
                lyricsArea.getDocument().getLength() > 0) {
            String detectedLanguage = new YassLanguageUtils().detectLanguage(lyricsArea.getText());
            language.setSelectedItem(detectedLanguage);
        }
    }

    private void parseSubtitles(String subtitlePath) {
        if (StringUtils.isEmpty(subtitlePath)) {
            if (subtitles != null && !subtitles.isEmpty()) {
                subtitles = new HashMap<>();
            }
            return;
        }
        java.io.File subtitleFile = new java.io.File(subtitlePath);
        if (subtitleFile.exists()) {
            // This check prevents re-parsing if the lyrics area already has content from this file.
            if (StringUtils.isNotEmpty(lyricsArea.getText())) {
                return;
            }
            subtitles = SubtitleParser.parse(subtitleFile);
            if (!subtitles.isEmpty()) {
                String lyricsText = String.join("\n", subtitles.values());
                setText(lyricsText);
            }
        }
    }

    /**
     * Gets the text attribute of the Lyrics object
     *
     * @return The text value
     */
    public String getText() {
        return lyricsArea.getText();
    }


    /**
     * Sets the text attribute of the Lyrics object
     *
     * @param s The new text value
     */
    public void setText(String s) {
        if (s == null) {
            s = "";
        }
        lyricsArea.setText(s);
    }


    /**
     * Gets the table attribute of the Lyrics object
     *
     * @return The table value
     */
    public String getTable() {
        StringWriter buffer = new StringWriter();
        PrintWriter outputStream = new PrintWriter(buffer);

        YassHyphenator hyphenator = new YassHyphenator("DE|EN|ES|FR|IT|PL|PT|RU|SV|TR|ZH");
        hyphenator.setLanguage(new YassLanguageUtils().determineLanguageCode(wizard.getValue("language")));
        hyphenator.setYassProperties(yassProperties);
        YassUtils yassUtils = new YassUtils();
        yassUtils.setHyphenator(hyphenator);
        yassUtils.setDefaultLength(3);
        yassUtils.setSpacingAfter(yassProperties.isUncommonSpacingAfter());
        List<String> lyrics;
        int gap;
        if (subtitles != null && !subtitles.isEmpty()) {
            Pair<Integer, List<String>> gapLyricsPair = processSubtitles(yassUtils);
            lyrics = gapLyricsPair.getRight();
            gap = gapLyricsPair.getLeft();
        } else {
            lyrics = yassUtils.splitLyricsToLines(getText().split("\n"), 0);
            gap = 0;
        }
        outputStream.println("#TITLE:Unknown");
        outputStream.println("#ARTIST:Unknown");
        outputStream.println("#LANGUAGE:Unknown");
        outputStream.println("#EDITION:Unknown");
        outputStream.println("#GENRE:Unknown");
        outputStream.println("#CREATOR:Unknown");
        outputStream.println("#MP3:Unknown");
        outputStream.println("#BPM:" + ((int)NumberUtils.toDouble(wizard.getValue("bpm"), 300)));
        outputStream.println("#GAP:" + gap);

        for (String line : lyrics) {
            if (StringUtils.isEmpty(line.trim())) {
                continue;
            }
            outputStream.println(line.replace("\t", " "));
        }
        outputStream.println("E");
        return buffer.toString();
    }

    private Pair<Integer, List<String>> processSubtitles(YassUtils yassUtils) {
        Integer gap = null;
        List<String> lines = new ArrayList<>();
        int bpm = (int)NumberUtils.toDouble(wizard.getValue("bpm"), 300);
        for (Map.Entry<Integer, String> entry : subtitles.entrySet()) {
            if (gap == null) {
                gap = entry.getKey();
            }
            int startBeat = (int)YassTable.msToBeatExact((double)entry.getKey(), gap, bpm);
            if (!lines.isEmpty()) {
                String lastLine = lines.getLast();
                if (lastLine.contains("\t")) {
                    int tempLastBeat = NumberUtils.toInt(lastLine.substring(lastLine.lastIndexOf("\t") + 1));
                    startBeat = Math.max(startBeat, tempLastBeat);
                }
            }
            lines.addAll(
                    yassUtils.splitLyricsToLines(new String[]{StringUtils.capitalize(entry.getValue())}, startBeat));
        }
        return new ImmutablePair<>(gap, lines);
    }
}
