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

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import yass.analysis.PitchDetector;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

@Getter
public class SongHeader extends JPanel implements YassSheetListener {

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

    public SongHeader(YassActions actions, YassTable table) {
        LOGGER.info("Init Songheader");
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Color.LIGHT_GRAY);
        this.actions = actions;
        Border border = BorderFactory.createEtchedBorder();
        setBorder(border);
        YassProperties yassProperties = actions.getProperties();
        JPanel inner = createMainPanel(table, yassProperties);
        add(inner, BorderLayout.CENTER);
        applyTheme(yassProperties.getBooleanProperty("dark-mode"));
    }

    /**
     * Creates the main panel containing all the song header controls.
     * @param table The table model containing song data.
     * @param yassProperties The application properties.
     * @return The fully constructed main panel.
     */
    private JPanel createMainPanel(YassTable table, YassProperties yassProperties) {
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        panels.add(mainPanel);

        // --- Row 0: Audio Panel ---
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 5; // Span all columns
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(createAudioPanel(table, yassProperties), gbc);

        // --- Row 1: Gap and BPM ---
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.weightx = 0;

        // Gap Spinner
        gbc.gridx = 0;
        mainPanel.add(createGapSpinnerPanel(table), gbc);

        // Gap Button
        gbc.gridx = 1;
        mainPanel.add(createGapButtonPanel(), gbc);

        // BPM Panel
        gbc.gridx = 2;
        mainPanel.add(createBpmPanel(table), gbc);

        // --- Row 2: Start and End ---
        gbc.gridy = 2;

        // Start Spinner
        gbc.gridx = 0;
        mainPanel.add(createStartSpinnerPanel(table), gbc);

        // Spacer to align with Gap Button
        gbc.gridx = 1;
        mainPanel.add(Box.createRigidArea(new Dimension(30, 0)), gbc);

        // End Panel
        gbc.gridx = 2;
        mainPanel.add(createEndPanel(table), gbc);
        addListeners(table);
        return mainPanel;
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

        Dimension labelSize = new Dimension(120, 30);
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
            determinePitches();
        }

        mp3 = new JTextField(audio);
        mp3.setName("mp3");
        mp3.setPreferredSize(new Dimension(100, 30)); // Set a consistent height
        textFields.add(mp3);
        panel.add(mp3);

        JButton button = createOpenFileButton(actions.selectAudioFile, "mp3");
        button.setIcon(actions.getIcon("open24Icon"));
        panel.add(button);

        return panel;
    }

    private JPanel createGapSpinnerPanel(YassTable table) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(Box.createHorizontalStrut(5));
        panels.add(panel);
        gapSpinner = new TimeSpinner(I18.get("mpop_gap"), (int) table.getGap(), (int) table.getGap() * 10);
        gapSpinner.setLabelSize(new Dimension(120, 30));
        gapSpinner.setSpinnerSize(new Dimension(100, 30));
        gapSpinner.getSpinner().setFocusable(false);
        textFields.add(gapSpinner.getTextField());
        panel.add(gapSpinner);
        labels.addAll(gapSpinner.getLabels());
        return panel;
    }

    private JPanel createGapButtonPanel() {
        // This button and its spacing will be the reference for the gap in the panel below.
        JPanel gapButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        panels.add(gapButtonPanel); // Add panel to the list so its theme is applied
        gapButtonPanel.add(Box.createHorizontalStrut(5));
        gapButtonPanel.add(createActionButton(actions.setGapHere, "gap24Icon", new Dimension(40, 30)));
        gapButtonPanel.add(Box.createHorizontalStrut(5));
        return gapButtonPanel;
    }

    private JPanel createBpmPanel(YassTable table) {
        // Group BPM controls into their own panel for alignment
        JPanel bpmPanel = new JPanel();
        bpmPanel.setLayout(new BoxLayout(bpmPanel, BoxLayout.X_AXIS));
        panels.add(bpmPanel);

        JLabel label = new JLabel(I18.get("edit_bpm_title"));
        Dimension midDimension = new Dimension(80, 30);
        label.setPreferredSize(midDimension);
        label.setMinimumSize(midDimension);
        bpmPanel.add(label);
        labels.add(label);

        bpmField = new JTextField(String.valueOf(table.getBPM()));
        bpmField.setPreferredSize(new Dimension(75, 30)); 
        bpmPanel.add(bpmField);
        textFields.add(bpmField);
        Dimension buttonSize = new Dimension(40, 30);
        bpmPanel.add(createActionButton(actions.multiply, "fastforward24Icon", buttonSize));
        bpmPanel.add(createActionButton(actions.divide, "rewind24Icon", buttonSize));
        bpmPanel.add(createActionButton(actions.recalcBpm, "refresh24Icon", buttonSize));
        bpmPanel.add(createActionButton(actions.showOnlineHelpBeat, "help24Icon", buttonSize));
        return bpmPanel;
    }


    private JPanel createStartSpinnerPanel(YassTable table) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(Box.createHorizontalStrut(5));
        panels.add(panel);
        int duration = actions.getMP3() != null ? (int) (actions.getMP3().getDuration() / 1000) : 10000;
        startSpinner = new TimeSpinner(I18.get("mpop_audio_start"), (int) table.getStart() * 1000, duration);
        startSpinner.setLabelSize(new Dimension(120, 30)); // Same as gapSpinner
        startSpinner.setSpinnerSize(new Dimension(100, 30)); // Same as gapSpinner
        startSpinner.getSpinner().setFocusable(false);
        panel.add(startSpinner);
        labels.addAll(startSpinner.getLabels());
        textFields.add(startSpinner.getTextField());
        return panel;
    }

    private JPanel createEndPanel(YassTable table) {
        // Group End-Spinner controls into their own panel for alignment
        JPanel endPanel = new JPanel();
        endPanel.setLayout(new BoxLayout(endPanel, BoxLayout.X_AXIS));
        panels.add(endPanel);
        int duration = actions.getMP3() != null ? (int) (actions.getMP3().getDuration() / 1000) : 10000;
        int end = table.getEnd() > 0 ? (int) table.getEnd() : 10000;
        endSpinner = new TimeSpinner(I18.get("mpop_audio_end"), Math.min(duration, end), Math.max(10000, duration));
        endSpinner.setLabelSize(new Dimension(80, 30));
        endSpinner.setSpinnerSize(new Dimension(100, 30));
        endSpinner.getSpinner().setFocusable(false);
        endPanel.add(endSpinner);
        labels.addAll(endSpinner.getLabels());
        textFields.add(endSpinner.getTextField());

        Dimension buttonSize = new Dimension(40, 30);
        endPanel.add(createActionButton(actions.setStartHere, "bookmarks24Icon", buttonSize));
        endPanel.add(createActionButton(actions.removeStart, "delete24Icon", buttonSize));
        endPanel.add(createActionButton(actions.setEndHere, "bookmarks24Icon", buttonSize));
        endPanel.add(createActionButton(actions.removeEnd, "delete24Icon", buttonSize));
        return endPanel;
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
                if (StringUtils.isNotEmpty(this.mp3.getText())) {
                    determinePitches();
                }
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

    private void determinePitches() {
        YassProperties prop = actions.getProperties();
        YassPlayer player = actions.getMP3();
        if (this.audioSelector.getSelectedItem()
                              .toString()
                              .equalsIgnoreCase(UltrastarHeaderTag.VOCALS.toString()) &&
                prop.getBooleanProperty("debug-waveform")) {
            player.setPitchDataList(
                    PitchDetector.detectPitch(player.getTempFile(), prop));
        }
    }
    
    private JButton createActionButton(Action action, String iconName, Dimension size) {
        JButton button = new JButton();
        button.setAction(action);
        button.setToolTipText(button.getText());
        button.setText("");
        if (iconName != null) {
            button.setIcon(actions.getIcon(iconName));
        }

        Border normalBorder = BorderFactory.createEtchedBorder(EtchedBorder.RAISED);
        Border rolloverBorder = BorderFactory.createEtchedBorder(YassSheet.BLUE.brighter(), YassSheet.BLUE.darker());

        button.setBorder(normalBorder);
        button.addChangeListener(e -> {
            ButtonModel model = button.getModel();
            button.setBorder(model.isRollover() ? rolloverBorder : normalBorder);
        });

        button.setFocusable(false);
        if (size != null) {
            button.setPreferredSize(size);
            button.setMaximumSize(size);
        }
        return button;
    }

    private JButton createOpenFileButton(Action action, String fieldName) {
        JButton button = new JButton();
        action.putValue("fileField", fieldName);
        button.setAction(action);
        return button;
    }

    public JTextField getTextFieldByName(String name) {
        if ("mp3".equals(name)) {
            return mp3;
        }
        return null;
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
        setBackground(darkMode ? YassSheet.HI_GRAY_2_DARK_MODE : null);
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
