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
import yass.I18;
import yass.YassUtils;
import yass.analysis.BpmDetector;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Description of the Class
 *
 * @author Saruta
 */
public class MP3 extends JPanel {
    /**
     * Description of the Field
     */
    public final static String ID = "mp3";
    private static final long serialVersionUID = -35786632482964988L;
    private JTextField txtField;
    private JCheckBox skipCheck = null;
    private JButton browse;

    private CreateSongWizard wizard;


    /**
     * Constructor for the MP3 object
     *
     * @param w Description of the Parameter
     */
    public MP3(CreateSongWizard w) {
        wizard = w;
        JLabel iconLabel = new JLabel();
        setLayout(new BorderLayout());
        iconLabel.setIcon(new ImageIcon(this.getClass().getResource("clouds.jpg")));
        add("West", iconLabel);
        add("Center", getContentPanel());
    }


    /**
     * Gets the filename attribute of the MP3 object
     *
     * @return The filename value
     */
    public String getFilename() {
        return txtField.getText();
    }


    /**
     * Sets the filename attribute of the MP3 object
     *
     * @param s The new filename value
     */
    public void setFilename(String s) {
        if (s == null) {
            s = "";
        }
        txtField.setText(s);

        boolean onoff = skipCheck.isSelected();
        if (!onoff) {
            String data[] = YassUtils.getData(s);
            if (data == null) {
                wizard.setNextFinishButtonEnabled(false);
                return;
            }
            wizard.setValue("filename", getFilename());
            if (data[0] == null || data[0].trim().isEmpty()) {
                data[0] = "UnknownTitle";
            }
            wizard.setValue("title", data[0]);
            if (data[1] == null || data[1].trim().isEmpty()) {
                data[1] = "UnknownArtist";
            }
            wizard.setValue("artist", data[1]);
            wizard.setValue("genre", data[2]);
        } else {
            wizard.setValue("title", "UnknownTitle");
            wizard.setValue("artist", "UnknownArtist");
        }
        String currentBpmStr = wizard.getValue("bpm");
        boolean shouldDetect = true;
        if (StringUtils.isNotEmpty(currentBpmStr)) {
            try {
                if (Float.parseFloat(currentBpmStr.replace(',', '.')) > 0) {
                    shouldDetect = false;
                }
            } catch (NumberFormatException ignored) { }
        }
        if (shouldDetect) {
            BpmDetector.detectBpm(s, bpm -> {
                String bpmString = String.format(java.util.Locale.US, "%.2f", bpm);
                wizard.setValue("bpm", bpmString);
            }, wizard.getYassProperties());
        }
    
        wizard.setNextFinishButtonEnabled(true);
    }


    /**
     * Gets the contentPanel attribute of the MP3 object
     *
     * @return The contentPanel value
     */
    private JPanel getContentPanel() {
        JPanel content = new JPanel(new BorderLayout());
        JTextPane txt = new JTextPane();
        HTMLDocument doc = (HTMLDocument) txt.getEditorKitForContentType("text/html").createDefaultDocument();
        doc.setAsynchronousLoadPriority(-1);
        txt.setDocument(doc);
        URL url = I18.getResource("create_mp3.html");
        try {
            txt.setPage(url);
        } catch (Exception ignored) {
        }
        txt.setEditable(false);
        content.add("Center", new JScrollPane(txt));
        JPanel filePanel = new JPanel(new BorderLayout());
        filePanel.add("Center", txtField = new JTextField());
        filePanel.add("South", skipCheck = new JCheckBox(I18.get("create_mp3_skip")));
        txtField.addActionListener(
                evt -> setFilename(txtField.getText()));
        skipCheck.addItemListener(
                new ItemListener() {
                    public void itemStateChanged(ItemEvent e) {
                        boolean onoff = skipCheck.isSelected();
                        if (onoff) {
                            setFilename("");
                        } else {
                            setFilename(getFilename());
                        }
                        txtField.setEnabled(!onoff);
                        browse.setEnabled(!onoff);
                    }
                });

        browse = new JButton(I18.get("create_mp3_browse"));
        browse.addActionListener(
                e -> {
                    Frame f = new Frame();
                    JFileChooser chooser = new JFileChooser();
                    String tempExtensions = wizard.getProperty("audio-files");
                    String[] extensions;
                    if (StringUtils.isEmpty(tempExtensions)) {
                        extensions = List.of("mp3", "m4a", "wav", "ogg", "opus", "flac", ".webm", ".aac").toArray(new String[0]);
                    } else {
                        extensions = Arrays.stream(tempExtensions.split("\\|"))
                                           .map(it -> it.replace(".", ""))
                                           .toList()
                                           .toArray(new String[0]);
                    }
                    FileNameExtensionFilter fileFilter = new FileNameExtensionFilter("Audio Files", extensions);
                    chooser.setFileFilter(fileFilter);
                    chooser.addChoosableFileFilter(fileFilter);
                    try {
                        File songDir = Path.of(wizard.getProperty("song-directory")).toFile();
                        if (songDir.exists()) {
                            chooser.setCurrentDirectory(songDir);
                        }
                    } catch (Exception ex) {
                        // let's ignore this
                    }
                    int showOpenDialog = chooser.showOpenDialog(f);
                    if (showOpenDialog == JFileChooser.APPROVE_OPTION) {
                        File selectedFile = chooser.getSelectedFile();
                        setFilename(selectedFile.getAbsolutePath());
                    }
                    f.dispose();
                });
        filePanel.add("East", browse);
        content.add("South", filePanel);
        return content;
    }
}

