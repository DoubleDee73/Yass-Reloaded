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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import yass.I18;
import yass.YassRow;
import yass.YassSong;
import yass.YassTable;
import yass.titlecase.TitleCaseConverter;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Description of the Class
 *
 * @author Saruta
 */
public class Tap extends JPanel {
    /**
     * Description of the Field
     */
    public final static String ID = "tap";
    private static final long serialVersionUID = -7998365588082763662L;
    private CreateSongWizard wizard;
    private YassTable table = null;
    private JScrollPane scroll = null;
    private JCheckBox check = null;


    /**
     * Constructor for the Tap object
     *
     * @param w Description of the Parameter
     */
    public Tap(CreateSongWizard w) {
        wizard = w;
        JLabel iconLabel = new JLabel();
        setLayout(new BorderLayout());
        iconLabel.setIcon(new ImageIcon(this.getClass().getResource("clouds.jpg")));
        add("West", iconLabel);
        add("Center", getContentPanel());
    }


    /**
     * Gets the contentPanel attribute of the Tap object
     *
     * @return The contentPanel value
     */
    private JPanel getContentPanel() {
        JPanel content = new JPanel(new BorderLayout());
        JTextPane txt = new JTextPane();
        HTMLDocument doc = (HTMLDocument) txt.getEditorKitForContentType("text/html").createDefaultDocument();
        doc.setAsynchronousLoadPriority(-1);
        txt.setDocument(doc);
        URL url = I18.getResource("create_tap.html");
        try {
            txt.setPage(url);
        } catch (Exception ignored) {
        }
        txt.setEditable(false);
        content.add("North", new JScrollPane(txt));

        table = new YassTable();
        table.setEnabled(false);
        content.add("Center", scroll = new JScrollPane(table));
        content.add("South", check = new JCheckBox(I18.get("create_tap_edit")));
        check.setSelected(true);
        check.addItemListener(
                new ItemListener() {
                    public void itemStateChanged(ItemEvent e) {
                        if (check.isSelected()) {
                            wizard.setValue("starteditor", "true");
                        } else {
                            wizard.setValue("starteditor", "false");
                        }
                    }
                });
        scroll.setPreferredSize(new Dimension(100, 100));
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        return content;
    }


    /**
     * Description of the Method
     */
    public void updateTable() {
        table.setEnabled(true);
        table.removeAllRows();
        table.setText(wizard.getValue("melodytable"));
        String title = wizard.getValue("title");
        if (wizard.getValue("language").equalsIgnoreCase(Locale.ENGLISH.getDisplayLanguage())) {
            if (wizard.getProperty("titlecase").equals("simple")) {
                title = WordUtils.capitalize(title);
            } else {
                title = TitleCaseConverter.toApTitleCase(title);
            }
        }
        table.setTitle(title);
        table.setArtist(wizard.getValue("artist"));
        String extension;
        String temp = wizard.getValue("filename");
        if (StringUtils.isNotEmpty(temp)) {
            extension = temp.substring(temp.lastIndexOf("."));
        } else {
            extension = ".mp3";
        }
        String artist = wizard.getValue("artist");
        String actualFileName = YassSong.toFilename(artist + " - " + title + extension);
        table.setMP3(actualFileName);
        wizard.setValue("audio", actualFileName);
        String videoPath = wizard.getValue("video");
        if (StringUtils.isNotEmpty(videoPath)) {
            String videoExtension = videoPath.substring(videoPath.lastIndexOf("."));
            String videoFilename = YassSong.toFilename(artist + " - " + title + videoExtension);
            table.setVideo(videoFilename);
            wizard.setValue("videofile", videoFilename);
        }
        table.setBPM(wizard.getValue("bpm"));
        setCommentIfNotEmpty("EDITION:", wizard.getValue("edition"));
        setCommentIfNotEmpty("GENRE:", wizard.getValue("genre"));
        setCommentIfNotEmpty("LANGUAGE:", wizard.getValue("language"));
        setCommentIfNotEmpty("YEAR:", wizard.getValue("year"));
        setCommentIfNotEmpty("CREATOR:", wizard.getValue("creator"));
        String youtubeUrl = wizard.getValue("youtube");
        if (StringUtils.isNotEmpty(youtubeUrl)) {
            String videoId = extractVideoId(youtubeUrl);
            if (videoId != null) {
                table.setCommentTag("v=" + videoId);
            }
        }
        wizard.setValue("melodytable", table.getPlainText());
        table.getColumnModel().getColumn(0).setPreferredWidth(10);
        table.getColumnModel().getColumn(0).setMaxWidth(10);
        table.setEnabled(false);
        table.revalidate();
        scroll.revalidate();
    }

    private void setCommentIfNotEmpty(String tag, String value) {
        if (StringUtils.isNotEmpty(value)) {
            YassRow commentRow = table.getCommentRow(tag);
            if (commentRow == null) {
                commentRow = new YassRow("#", tag, value, "", "");
                table.appendHeaderTag(commentRow);
            } else {
                commentRow.setLength(value);
            }
        }
    }

    private String extractVideoId(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        String videoId = null;
        // This regex should cover most common YouTube URL formats.
        String pattern = "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%2F|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*";

        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(url);
        if (matcher.find()) {
            videoId = matcher.group();
        }
        return videoId;
    }
}
