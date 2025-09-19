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

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import yass.I18;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FilenameFilter;
import java.net.URL;

/**
 * Description of the Class
 *
 * @author Saruta
 */
@Getter
@Setter
public class Melody extends JPanel {
    /**
     * Description of the Field
     */
    public final static String ID = "melody";
    private static final long serialVersionUID = -5882327508846264166L;
    private JTextField txtField;
    private JCheckBox check;
    private JButton browse;
    private CreateSongWizard wizard;
    private WizardMidiMode wizardMidiMode;


    /**
     * Constructor for the Melody object
     *
     * @param w Description of the Parameter
     */
    public Melody(CreateSongWizard w) {
        wizard = w;
        JLabel iconLabel = new JLabel();
        setLayout(new BorderLayout());
        iconLabel.setIcon(new ImageIcon(this.getClass().getResource("clouds.jpg")));
        add("West", iconLabel);
        add("Center", getContentPanel());
    }


    /**
     * Gets the filename attribute of the Melody object
     *
     * @return The filename value
     */
    public String getFilename() {
        return txtField.getText();
    }


    /**
     * Sets the filename attribute of the Melody object
     *
     * @param s The new filename value
     */
    public void setFilename(String s) {
        if (s == null) {
            s = "";
        }
        txtField.setText(s);
        wizard.setValue("melody", s);

        if (check.isSelected() || ((new File(s).exists()))) {
            wizard.setNextFinishButtonEnabled(true);
        } else {
            wizard.setNextFinishButtonEnabled(false);
        }
    }


    /**
     * Gets the contentPanel attribute of the Melody object
     *
     * @return The contentPanel value
     */
    private JPanel getContentPanel() {
        JPanel content = new JPanel(new BorderLayout());
        JTextPane txt = new JTextPane();
        HTMLDocument doc = (HTMLDocument) txt.getEditorKitForContentType("text/html").createDefaultDocument();
        doc.setAsynchronousLoadPriority(-1);
        txt.setDocument(doc);
        URL url = I18.getResource("create_melody.html");
        try {
            txt.setPage(url);
        } catch (Exception ignored) {
        }
        txt.setEditable(false);
        content.add("Center", new JScrollPane(txt));
        JPanel filePanel = new JPanel(new BorderLayout());
        check = new JCheckBox(I18.get("create_melody_manually"));
        filePanel.add("South", check);
        check.addItemListener(
                e -> {
                    boolean onoff = check.isSelected();
                    if (onoff) {
                        setFilename("");
                    } else {
                        setFilename(getFilename());
                    }
                    txtField.setEnabled(!onoff);
                    browse.setEnabled(!onoff);
                });
        filePanel.add("Center", txtField = new JTextField());
        txtField.addActionListener(evt -> setFilename(txtField.getText()));
        browse = new JButton(I18.get("create_melody_browse"));
        browse.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        Frame f = new Frame();
                        FileDialog fd = new FileDialog(f, I18.get("create_melody_title"), FileDialog.LOAD);
                        fd.setFilenameFilter(
                                new FilenameFilter() {
                                    // won't work on Windows - thx to Sun
                                    public boolean accept(File dir, String name) {
                                        name = name.toLowerCase();
                                        return name.endsWith(".mid") || name.endsWith(".midi") || name.endsWith(".kar");
                                    }
                                });
                        //fd.setFile("*.kar");
                        fd.setVisible(true);
                        if (fd.getFile() != null) {
                            String name = fd.getFile();
                            name = name.toLowerCase();
                            if (name.endsWith(".mid") || name.endsWith(".midi") || name.endsWith(".kar")) {
                                String dir = fd.getDirectory();
                                if (!dir.endsWith(File.separator)) {
                                    dir = dir + File.separator;
                                }
                                setFilename(dir + fd.getFile());
                            }
                        }
                        fd.dispose();
                        f.dispose();
                    }
                });
        filePanel.add("East", browse);
        content.add("South", filePanel);
        if (StringUtils.isEmpty(wizard.getValue("melody"))) {
            check.setSelected(getWizardMidiMode() == WizardMidiMode.SUGGEST_MIDI);
        }
        return content;
    }

    public void setWizardMidiMode(WizardMidiMode wizardMidiMode) {
        this.wizardMidiMode = wizardMidiMode;
        check.setSelected(getWizardMidiMode() == WizardMidiMode.SUGGEST_MIDI);
    }
}

