/*
 * Yass Reloaded - Karaoke Editor
 * Copyright (C) 2009-2023 Saruta
 * Copyright (C) 2023 DoubleDee
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
import java.awt.event.ItemEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Objects;

public class SongHeader extends JDialog {

    private TimeSpinner gapSpinner = null;
    private TimeSpinner startSpinner = null;
    private TimeSpinner endSpinner = null;
    private TimeSpinner vgapSpinner = null;
    private JTextField mp3 = null;
    private JTextField bpmField;
    private JComboBox<String> audioSelector;
    private YassActions actions;

    public SongHeader(JFrame owner, YassActions actions, YassTable table) {
        super(owner);
        System.out.println("Init Songheader");
        if (isVisible()) {
            return;
        }
        this.actions = actions;
        setTitle(I18.get("edit_header"));
        setResizable(false);
        setUndecorated(true);
        addWindowListener(new WindowAdapter() {
        public void windowClosing(WindowEvent e) {
                e.getWindow().dispose();
            }
        });
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        AbstractButton button;
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(Box.createHorizontalStrut(5));
        JLabel label;
        Dimension labelSize = new Dimension(120, 20);
        if (table.getProperties().isShinyOrNewer()) {
            audioSelector = new JComboBox<>(new String[]{UltrastarHeaderTag.AUDIO.toString(),
                    UltrastarHeaderTag.INSTRUMENTAL.toString(), UltrastarHeaderTag.VOCALS.toString(),});
            audioSelector.setMinimumSize(labelSize);
            audioSelector.setPreferredSize(labelSize);
            audioSelector.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.DESELECTED && mp3 != null) {
                    String currentFile = mp3.getText();
                    table.setAudioByTag(e.getItem().toString(), currentFile);
                }
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    YassRow mp3Row = table.getCommentRow(e.getItem() + ":");
                    if (mp3Row != null) {
                        mp3.setText(mp3Row.getComment());
                        actions.openMp3(table.getDir() + File.separator + mp3Row.getComment());
                    } else {
                        mp3.setText(StringUtils.EMPTY);
                    }
                }
            });
            panel.add(audioSelector);
        } else {
            label = new JLabel(I18.get("edit_audio"));
            label.setMinimumSize(labelSize);
            label.setPreferredSize(labelSize);
            panel.add(label);
        }
        mp3 = new JTextField(StringUtils.isNotEmpty(table.getAudio()) ? table.getAudio() : table.getMP3());
        mp3.setName("mp3");
        YassUtils.addChangeListener(mp3, e -> {
            String selectedAudio = audioSelector != null && audioSelector.getSelectedItem() != null ?
                    audioSelector.getSelectedItem().toString() : StringUtils.EMPTY;
            String currentText = mp3.getText();
            if (currentText != null) {
                table.setAudioByTag(selectedAudio, currentText);
            }
        });
        panel.add(mp3);
        button = createOpenFileButton(actions.selectAudioFile, "mp3");
        button.setIcon(actions.getIcon("open24Icon"));
        panel.add(button);
        box.add(panel);
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(Box.createHorizontalStrut(5));
        gapSpinner = new TimeSpinner(I18.get("mpop_gap"), (int) table.getGap(), (int) table.getGap() * 10);
        gapSpinner.setLabelSize(labelSize);
        gapSpinner.setSpinnerWidth(100);
        gapSpinner.getSpinner().setFocusable(false);
        gapSpinner.getSpinner().addChangeListener(e -> {
            actions.setGap(gapSpinner.getTime());
        });
        panel.add(gapSpinner);
        panel.add(Box.createHorizontalStrut(5));
        panel.add(button = new JButton());
        button.setAction(actions.setGapHere);
        button.setToolTipText(button.getText());
        button.setText("");
        button.setIcon(actions.getIcon("gap24Icon"));
        button.setFocusable(false);
        button.setPreferredSize(new Dimension(20, 20));
        panel.add(Box.createHorizontalStrut(5));
        panel.add(label = new JLabel(I18.get("edit_bpm_title")));
        Dimension midDimension = new Dimension(80, 20);
        label.setPreferredSize(midDimension);
        label.setMinimumSize(midDimension);
        bpmField = new JTextField(String.valueOf(table.getBPM()));
        bpmField.setPreferredSize(midDimension);
        bpmField.setPreferredSize(midDimension);
        bpmField.addActionListener(e1 -> {
            String s = bpmField.getText();
            double bpm1 = table.getBPM();
            try {
                bpm1 = Double.parseDouble(s);
            } catch (Exception ex) {
                bpmField.setText(String.valueOf(bpm1));
            }
            table.setBPM(bpm1);
        });
        panel.add(bpmField);
        panel.add(button = new JButton());
        button.setAction(actions.multiply);
        button.setToolTipText(button.getText());
        button.setText("");
        button.setIcon(actions.getIcon("fastforward24Icon"));
        button.setFocusable(false);
        panel.add(button = new JButton());
        button.setAction(actions.divide);
        button.setToolTipText(button.getText());
        button.setText("");
        button.setIcon(actions.getIcon("rewind24Icon"));
        button.setFocusable(false);
        panel.add(button = new JButton());
        button.setAction(actions.recalcBpm);
        button.setToolTipText(button.getText());
        button.setText("");
        button.setIcon(actions.getIcon("refresh24Icon"));
        button.setFocusable(false);
        panel.add(button = new JButton());
        button.setAction(actions.showOnlineHelpBeat);
        button.setToolTipText(button.getText());
        button.setText("");
        button.setIcon(actions.getIcon("help24Icon"));
        button.setFocusable(false);

        box.add(panel);

        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(Box.createHorizontalStrut(5));
        startSpinner = new TimeSpinner(I18.get("mpop_audio_start"), (int)table.getStart(), 10000);
        startSpinner.setLabelSize(labelSize);
        startSpinner.setSpinnerWidth(100);
        startSpinner.getSpinner().setFocusable(false);
        startSpinner.getSpinner().addChangeListener(e -> {
            actions.setStart(startSpinner.getTime());
        });
        panel.add(startSpinner);
        panel.add(Box.createHorizontalStrut(30));
        int end = table.getEnd() > 0 ? (int) table.getEnd() : 10000;
        int duration = actions.getMP3() != null ? (int)(actions.getMP3().getDuration() / 1000) : 10000;
        endSpinner = new TimeSpinner(I18.get("mpop_audio_end"), end, Math.max(10000, duration));
        endSpinner.setLabelSize(midDimension);
        endSpinner.setSpinnerWidth(100);
        endSpinner.getSpinner().setFocusable(false);
        endSpinner.getSpinner().addChangeListener(e -> {
            actions.setEnd(endSpinner.getTime());
        });
        panel.add(endSpinner);

        panel.add(button = new JButton());
        button.setAction(actions.setStartHere);
        button.setToolTipText(button.getText());
        button.setText("");
        button.setIcon(actions.getIcon("bookmarks24Icon"));
        button.setFocusable(false);

        panel.add(button = new JButton());
        button.setAction(actions.removeStart);
        button.setToolTipText(button.getText());
        button.setText("");
        button.setIcon(actions.getIcon("delete24Icon"));
        button.setFocusable(false);

        panel.add(button = new JButton());
        button.setAction(actions.setEndHere);
        button.setToolTipText(button.getText());
        button.setText("");
        button.setIcon(actions.getIcon("bookmarks24Icon"));
        button.setFocusable(false);

        panel.add(button = new JButton());
        button.setAction(actions.removeEnd);
        button.setToolTipText(button.getText());
        button.setText("");
        button.setIcon(actions.getIcon("delete24Icon"));
        button.setFocusable(false);
        box.add(panel);

        add("Center", box);
        pack();
        setVisible(true);
        refreshLocation();
        toFront();
    }

    private JButton createOpenFileButton(Action action, String fieldName) {
        JButton button = new JButton();
        action.putValue("fileField", fieldName);
        button.setAction(action);
        return button;
    }

    @Override
    public void dispose() {
        setVisible(false);
        super.dispose();
    }

    public void refreshLocation() {
        setLocation(actions.getX() + 8, actions.getY() + 125);
        repaint();
    }

    public JTextField getTextFieldByName(String name) {
        if ("mp3".equals(name)) {
            return mp3;
        }
        return null;
    }

    public TimeSpinner getGapSpinner() {
        return gapSpinner;
    }

    public TimeSpinner getStartSpinner() {
        return startSpinner;
    }

    public TimeSpinner getEndSpinner() {
        return endSpinner;
    }

    public TimeSpinner getVgapSpinner() {
        return vgapSpinner;
    }

    public JTextField getMp3() {
        return mp3;
    }

    public JTextField getBpmField() {
        return bpmField;
    }

    public String getSelectedAudio() {
        if (audioSelector == null) {
            return UltrastarHeaderTag.MP3.name();
        } else {
            return Objects.requireNonNull(audioSelector.getSelectedItem()).toString();
        }
    }
}
