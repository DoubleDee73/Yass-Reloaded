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
import yass.*;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.List;

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
    private YassProperties yassProperties;


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

        lyricsArea = new JTextArea(10, 20);
        YassUtils.addChangeListener(lyricsArea, e -> determineLanguage());
        JPanel pan = new JPanel(new BorderLayout());
        pan.add("Center", new JScrollPane(lyricsArea));
        pan.add("South", buttons);
        content.add("South", pan);
        return content;
    }

    private void determineLanguage() {
        if (language != null && StringUtils.isEmpty((String)language.getSelectedItem()) && 
                lyricsArea.getDocument().getLength() > 0) {
            String detectedLanguage = new YassLanguageUtils().detectLanguage(lyricsArea.getText());
            language.setSelectedItem(detectedLanguage);
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

        outputStream.println("#TITLE:Unknown");
        outputStream.println("#ARTIST:Unknown");
        outputStream.println("#LANGUAGE:Unknown");
        outputStream.println("#EDITION:Unknown");
        outputStream.println("#GENRE:Unknown");
        outputStream.println("#CREATOR:Unknown");
        outputStream.println("#MP3:Unknown");
        outputStream.println("#BPM:300");
        outputStream.println("#GAP:0");
        YassHyphenator hyphenator = new YassHyphenator("DE|EN|ES|FR|IT|PL|PT|RU|SV|TR|ZH");
        hyphenator.setLanguage(new YassLanguageUtils().determineLanguageCode(wizard.getValue("language")));
        hyphenator.setYassProperties(yassProperties);
        YassUtils yassUtils = new YassUtils();
        yassUtils.setHyphenator(hyphenator);
        yassUtils.setDefaultLength(3);
        yassUtils.setSpacingAfter(yassProperties.isUncommonSpacingAfter());
        List<String> lyrics = yassUtils.splitLyricsToLines(getText().split("\n"), 0);
        for (String line : lyrics) {
            if (StringUtils.isEmpty(line.trim())) {
                continue;
            }
            outputStream.println(line.replace("\t", " "));
        }
        outputStream.println("E");
        return buffer.toString();
    }
}

