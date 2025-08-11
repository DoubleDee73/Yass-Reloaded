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
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class SongHeader extends JDialog implements YassSheetListener {

    private TimeSpinner gapSpinner;
    private TimeSpinner startSpinner;
    private TimeSpinner endSpinner;
    private TimeSpinner vgapSpinner;
    private JTextField mp3;
    private JTextField bpmField;
    private JComboBox<String> audioSelector;
    private YassActions actions;
    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private final List<JPanel> panels = new ArrayList<>();
    private final List<JLabel> labels = new ArrayList<>();
    private final List<JTextField> textFields = new ArrayList<>();

    public SongHeader(JFrame owner, YassActions actions, YassTable table) {
        super(owner);
        LOGGER.info("Init Songheader");
        if (isVisible()) {
            return;
        }

        this.actions = actions;
        YassProperties yassProperties = actions.getProperties();

        configureDialog();
        add("Center", createMainPanel(table, yassProperties));
        addListeners(table);

        pack();
        applyTheme(yassProperties.getBooleanProperty("dark-mode"));
        setVisible(true);
        refreshLocation();
        toFront();
    }

    /**
     * Configures the basic properties of the dialog window.
     */
    private void configureDialog() {
        setTitle(I18.get("edit_header"));
        setResizable(false);
        setUndecorated(true);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                e.getWindow().dispose();
            }
        });
    }

    /**
     * Creates the main panel containing all the song header controls.
     * @param table The table model containing song data.
     * @param yassProperties The application properties.
     * @return The fully constructed main panel.
     */
    private JPanel createMainPanel(YassTable table, YassProperties yassProperties) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        panels.add(box);

        box.add(createAudioPanel(table, yassProperties));
        box.add(createBpmAndGapPanel(table));
        box.add(createStartEndPanel(table));

        return box;
    }

    /**
     * Creates the panel for audio file selection and management.
     * @param table The table model containing song data.
     * @param yassProperties The application properties.
     * @return The audio panel.
     */
    private JPanel createAudioPanel(YassTable table, YassProperties yassProperties) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(Box.createHorizontalStrut(5));
        panels.add(panel);

        Dimension labelSize = new Dimension(120, 20);
        if (yassProperties.isShinyOrNewer()) {
            audioSelector = new JComboBox<>(new String[]{UltrastarHeaderTag.AUDIO.toString(),
                    UltrastarHeaderTag.INSTRUMENTAL.toString(), UltrastarHeaderTag.VOCALS.toString()});
            audioSelector.setMinimumSize(labelSize);
            audioSelector.setPreferredSize(labelSize);
            panel.add(audioSelector);
        } else {
            JLabel label = new JLabel(I18.get("edit_audio"));
            label.setMinimumSize(labelSize);
            label.setPreferredSize(labelSize);
            panel.add(label);
            labels.add(label);
        }

        boolean debugWaveform = yassProperties.getBooleanProperty("debug-waveform");
        String audio = (debugWaveform && StringUtils.isNotEmpty(table.getVocals()))
                ? table.getVocals()
                : (StringUtils.isNotEmpty(table.getAudio()) ? table.getAudio() : table.getMP3());
        if (debugWaveform && StringUtils.isNotEmpty(table.getVocals())) {
            audioSelector.setSelectedItem(UltrastarHeaderTag.VOCALS.toString());
        }

        mp3 = new JTextField(audio);
        mp3.setName("mp3");
        textFields.add(mp3);
        panel.add(mp3);

        JButton button = createOpenFileButton(actions.selectAudioFile, "mp3");
        button.setIcon(actions.getIcon("open24Icon"));
        panel.add(button);

        return panel;
    }

    /**
     * Creates the panel for managing BPM and GAP values.
     * @param table The table model containing song data.
     * @return The BPM and GAP panel.
     */
    private JPanel createBpmAndGapPanel(YassTable table) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(Box.createHorizontalStrut(5));
        panels.add(panel);

        gapSpinner = new TimeSpinner(I18.get("mpop_gap"), (int) table.getGap(), (int) table.getGap() * 10);
        gapSpinner.setLabelSize(new Dimension(120, 20));
        gapSpinner.setSpinnerWidth(100);
        gapSpinner.getSpinner().setFocusable(false);
        panel.add(gapSpinner);
        labels.addAll(gapSpinner.getLabels());
        textFields.add(gapSpinner.getTextField());

        panel.add(Box.createHorizontalStrut(5));
        panel.add(createActionButton(actions.setGapHere, "gap24Icon", new Dimension(20, 20)));
        panel.add(Box.createHorizontalStrut(5));

        JLabel label = new JLabel(I18.get("edit_bpm_title"));
        Dimension midDimension = new Dimension(80, 20);
        label.setPreferredSize(midDimension);
        label.setMinimumSize(midDimension);
        panel.add(label);
        labels.add(label);

        bpmField = new JTextField(String.valueOf(table.getBPM()));
        bpmField.setPreferredSize(midDimension);
        panel.add(bpmField);

        panel.add(createActionButton(actions.multiply, "fastforward24Icon", null));
        panel.add(createActionButton(actions.divide, "rewind24Icon", null));
        panel.add(createActionButton(actions.recalcBpm, "refresh24Icon", null));
        panel.add(createActionButton(actions.showOnlineHelpBeat, "help24Icon", null));

        return panel;
    }

    /**
     * Creates the panel for managing song start and end times.
     * @param table The table model containing song data.
     * @return The start/end panel.
     */
    private JPanel createStartEndPanel(YassTable table) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(Box.createHorizontalStrut(5));
        panels.add(panel);

        int duration = actions.getMP3() != null ? (int) (actions.getMP3().getDuration() / 1000) : 10000;
        startSpinner = new TimeSpinner(I18.get("mpop_audio_start"), (int) table.getStart() * 1000, duration);
        startSpinner.setLabelSize(new Dimension(120, 20));
        startSpinner.setSpinnerWidth(100);
        startSpinner.getSpinner().setFocusable(false);
        panel.add(startSpinner);
        labels.addAll(startSpinner.getLabels());
        textFields.add(startSpinner.getTextField());

        int end = table.getEnd() > 0 ? (int) table.getEnd() : 10000;
        endSpinner = new TimeSpinner(I18.get("mpop_audio_end"), Math.min(duration, end), Math.max(10000, duration));
        endSpinner.setLabelSize(new Dimension(80, 20));
        endSpinner.setSpinnerWidth(100);
        endSpinner.getSpinner().setFocusable(false);
        panel.add(endSpinner);
        labels.addAll(endSpinner.getLabels());
        textFields.add(endSpinner.getTextField());

        panel.add(createActionButton(actions.setStartHere, "bookmarks24Icon", null));
        panel.add(createActionButton(actions.removeStart, "delete24Icon", null));
        panel.add(createActionButton(actions.setEndHere, "bookmarks24Icon", null));
        panel.add(createActionButton(actions.removeEnd, "delete24Icon", null));
        panel.add(Box.createHorizontalStrut(30));

        return panel;
    }

    /**
     * Adds all necessary event listeners to the components.
     * @param table The table model to be updated by the listeners.
     */
    private void addListeners(YassTable table) {
        if (audioSelector != null) {
            audioSelector.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.DESELECTED && mp3 != null) {
                    table.setAudioByTag(e.getItem().toString(), mp3.getText());
                }
                if (e.getStateChange() == ItemEvent.SELECTED && mp3 != null) {
                    YassRow mp3Row = table.getCommentRow(e.getItem() + ":");
                    if (mp3Row != null) {
                        mp3.setText(mp3Row.getHeaderComment());
                        actions.openMp3(table.getDir() + File.separator + mp3Row.getHeaderComment());
                    } else {
                        mp3.setText(StringUtils.EMPTY);
                    }
                }
            });
        }

        YassUtils.addChangeListener(mp3, e -> {
            String selectedAudio = (audioSelector != null && audioSelector.getSelectedItem() != null)
                    ? audioSelector.getSelectedItem().toString() : StringUtils.EMPTY;
            if (mp3.getText() != null) {
                table.setAudioByTag(selectedAudio, mp3.getText());
            }
        });

        gapSpinner.getSpinner().addChangeListener(e -> actions.setGap(gapSpinner.getTime()));
        startSpinner.getSpinner().addChangeListener(e -> actions.setStart(startSpinner.getTime()));
        endSpinner.getSpinner().addChangeListener(e -> actions.setEnd(endSpinner.getTime()));

        bpmField.addActionListener(e -> {
            double bpm = table.getBPM();
            try {
                bpm = Double.parseDouble(bpmField.getText());
            } catch (Exception ex) {
                bpmField.setText(String.valueOf(bpm));
            }
            for (YassTable temp : actions.getOpenTables(table)) {
                temp.setBPM(bpm);
            }
        });
    }

    private JButton createActionButton(Action action, String iconName, Dimension size) {
        JButton button = new JButton();
        button.setAction(action);
        button.setToolTipText(button.getText());
        button.setText("");
        if (iconName != null) {
            button.setIcon(actions.getIcon(iconName));
        }
        button.setFocusable(false);
        if (size != null) {
            button.setPreferredSize(size);
        }
        return button;
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

    @Override
    public void posChanged(YassSheet source, double posMs) {

    }

    @Override
    public void rangeChanged(YassSheet source, int minHeight, int maxHeight, int minBeat, int maxBeat) {

    }

    @Override
    public void propsChanged(YassSheet sheet) {
        // dark mode buttons
        Border emptyBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.RAISED),
                BorderFactory.createEmptyBorder(4, 4, 4, 4));
        Border rolloverBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.RAISED),
                BorderFactory.createEmptyBorder(4, 4, 4, 4));
        for (Component c : getComponents()) {
            if (c instanceof JButton) {
                ((JButton) c).getModel().addChangeListener(e -> {
                    ButtonModel model = (ButtonModel) e.getSource();
                    c.setBackground(model.isRollover()
                            ? (YassSheet.BLUE)
                            : YassSheet.HI_GRAY_2);
                    ((JButton) c).setBorder(model.isRollover() ? rolloverBorder : emptyBorder);
                });
            }
            if (c instanceof JToggleButton) {
                ((JToggleButton) c).getModel().addChangeListener(e -> {
                    ButtonModel model = (ButtonModel) e.getSource();
                    c.setBackground(model.isRollover()
                            ? (YassSheet.BLUE)
                            : YassSheet.HI_GRAY_2);
                    ((JToggleButton) c).setBorder(model.isRollover() ? rolloverBorder : emptyBorder);
                });
            }
        }
    }

    public void applyTheme(boolean darkMode) {
        if (audioSelector != null) {
            audioSelector.setBackground(darkMode ? YassSheet.HI_GRAY_2_DARK_MODE : YassSheet.white);
            audioSelector.setForeground(darkMode ? YassSheet.white : null);
        }
        for (JTextField textField : textFields) {
            textField.setBackground(darkMode ? YassSheet.HI_GRAY_2_DARK_MODE : YassSheet.white);
            textField.setForeground(darkMode ? YassSheet.white : null);
        }
        for (JPanel panel : panels) {
            panel.setBackground(darkMode ? YassSheet.HI_GRAY_2_DARK_MODE : null);
            panel.setForeground(darkMode ? YassSheet.white : null);
        }
        for (JLabel label : labels) {
            label.setBackground(darkMode ? YassSheet.HI_GRAY_2_DARK_MODE : YassSheet.white);
            label.setForeground(darkMode ? YassSheet.white : null);
        }
        repaint();
    }
}
