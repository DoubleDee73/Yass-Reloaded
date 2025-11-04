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
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import yass.analysis.PitchDetector;
import yass.suggest.SuggestingTagField;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.File;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Getter
@Setter
public class SongHeader extends JPanel implements YassSheetListener {

    private TimeSpinner gapSpinner;
    private TimeSpinner startSpinner;
    private TimeSpinner endSpinner;
    private TimeSpinner vgapSpinner;
    private JTextField audioField;
    private JTextField bpmField;
    private JComboBox<String> audioSelector;
    private SuggestingTagField languageField;
    private JFormattedTextField yearField;
    private SuggestingTagField genreField;
    private SuggestingTagField tagField;
    private YassActions actions;
    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private final List<JPanel> panels = new ArrayList<>();
    private final List<JLabel> labels = new ArrayList<>();
    private final List<JTextField> textFields = new ArrayList<>();
    private boolean isInternalUpdate = false;


    public SongHeader(YassActions actions) {
        LOGGER.info("Init Songheader");
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Color.LIGHT_GRAY);
        this.actions = actions;
        Border border = BorderFactory.createEtchedBorder();
        setBorder(border);
        YassProperties yassProperties = actions.getProperties();
        JPanel inner = createMainPanel(actions);
        add(inner, BorderLayout.CENTER);
        applyTheme(yassProperties.getBooleanProperty("dark-mode"));

        // Verhindert, dass der Header in der Höhe wächst und das Split-Panel verschiebt.
        Dimension prefSize = getPreferredSize();
        setMaximumSize(new Dimension(Integer.MAX_VALUE, prefSize.height));
    }

    /**
     * Creates the main panel containing all the song header controls.
     * @param actions The application actions.
     * @return The fully constructed main panel.
     */
    private JPanel createMainPanel(YassActions actions) {
        YassProperties yassProperties = actions.getProperties();
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        panels.add(mainPanel);

        // --- Row 0: Audio Panel ---
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 5; // Span all columns
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.NORTH;
        mainPanel.add(createAudioPanel(getTable(), yassProperties), gbc);

        // --- Row 2: Gap and BPM ---
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.weightx = 0;


        // Gap Spinner
        gbc.gridx = 0;
        mainPanel.add(createGapSpinnerPanel(), gbc);

        // Gap Button
        gbc.gridx = 1;
        mainPanel.add(createGapButtonPanel(), gbc);

        // BPM Panel
        gbc.gridx = 2;
        mainPanel.add(createBpmPanel(), gbc);

        // --- Row 3: Start and End ---
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.NORTH;

        // Start Spinner
        gbc.gridx = 0;
        mainPanel.add(createStartSpinnerPanel(), gbc);

        // Spacer to align with Gap Button
        gbc.gridx = 1;
        mainPanel.add(Box.createRigidArea(new Dimension(30, 0)), gbc);

        // End Panel
        gbc.gridx = 2;
        mainPanel.add(createEndPanel(), gbc);

        // --- Row 4: Metadata Panel (Language, Year, Genre) ---
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 5; // Span all columns
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(createMetadataPanel(yassProperties), gbc);

        setInternalUpdate(true);
        addListeners(actions.getSheet());
        setInternalUpdate(false);
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
        String audio;
        if (table != null) {
            audio = (debugWaveform && StringUtils.isNotEmpty(table.getVocals()))
                    ? table.getVocals()
                    : (StringUtils.isNotEmpty(table.getAudio()) ? table.getAudio() : getTable().getMP3());
            if (debugWaveform && StringUtils.isNotEmpty(table.getVocals())) {
                setAudioToVocals();
                determinePitches();
            }
        } else {
            audio = "";
        }
        audioField = new JTextField(audio);
        audioField.setName("mp3");
        audioField.setPreferredSize(new Dimension(100, 30)); // Set a consistent height
        textFields.add(audioField);
        panel.add(audioField);

        JButton button = createOpenFileButton(actions.selectAudioFile, "mp3");
        button.setIcon(actions.getIcon("open24Icon"));
        panel.add(button);

        return panel;
    }

    private JPanel createMetadataPanel(YassProperties yassProperties) {
        JPanel metaPanel = new JPanel();
        metaPanel.setLayout(new BoxLayout(metaPanel, BoxLayout.Y_AXIS));
        panels.add(metaPanel);

        // --- Row 1: Language and Year ---
        JPanel row1 = new JPanel(new GridBagLayout());
        panels.add(row1);
        GridBagConstraints gbc1 = new GridBagConstraints();
        gbc1.insets = new Insets(1, 5, 1, 5);
        gbc1.anchor = GridBagConstraints.WEST;

        // Language Label
        JLabel langLabel = new JLabel(I18.get("options_group1_language"));
        langLabel.setPreferredSize(new Dimension(110, 30));
        gbc1.gridx = 0;
        gbc1.gridy = 0;
        gbc1.weightx = 0;
        gbc1.fill = GridBagConstraints.NONE;
        row1.add(langLabel, gbc1);
        labels.add(langLabel);

        // Language Field
        languageField = createLanguageField();
        gbc1.gridx = 1;
        gbc1.weightx = 0.9;
        gbc1.fill = GridBagConstraints.HORIZONTAL;
        row1.add(languageField, gbc1);

        // Year Label
        JLabel yearLabel = new JLabel(I18.get("options_group1_year"));
        yearLabel.setPreferredSize(new Dimension(60, 30));
        gbc1.gridx = 2;
        gbc1.weightx = 0;
        gbc1.fill = GridBagConstraints.NONE;
        row1.add(yearLabel, gbc1);
        labels.add(yearLabel);

        // Year Field
        yearField = createYearField();
        gbc1.gridx = 3;
        gbc1.weightx = 0.1;
        gbc1.fill = GridBagConstraints.HORIZONTAL;
        row1.add(yearField, gbc1);

        // --- Row 2: Genre and Tags ---
        JPanel row2 = new JPanel(new GridBagLayout());
        panels.add(row2);
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.insets = new Insets(1, 5, 1, 5);
        gbc2.anchor = GridBagConstraints.WEST;

        // Genre Label
        JLabel genreLabel = new JLabel(I18.get("options_group1_genre"));
        genreLabel.setPreferredSize(new Dimension(110, 30));
        gbc2.gridx = 0;
        gbc2.gridy = 0;
        gbc2.weightx = 0;
        gbc2.fill = GridBagConstraints.NONE;
        row2.add(genreLabel, gbc2);
        labels.add(genreLabel);

        // Genre Field
        genreField = createGenreField(yassProperties);
        gbc2.gridx = 1;
        gbc2.weightx = 0.5;
        gbc2.fill = GridBagConstraints.HORIZONTAL;
        row2.add(genreField, gbc2);

        // Tag Label
        JLabel tagLabel = new JLabel(I18.get("options_tags"));
        tagLabel.setPreferredSize(new Dimension(90, 30));
        gbc2.gridx = 2;
        gbc2.weightx = 0;
        gbc2.fill = GridBagConstraints.NONE;
        row2.add(tagLabel, gbc2);
        labels.add(tagLabel);

        // Tag Field
        tagField = createTagField(yassProperties);
        gbc2.gridx = 3;
        gbc2.weightx = 0.5;
        gbc2.fill = GridBagConstraints.HORIZONTAL;
        row2.add(tagField, gbc2);

        metaPanel.add(row1);
        metaPanel.add(row2);

        return metaPanel;
    }

    private SuggestingTagField createLanguageField() {
        Function<String, List<String>> languageSuggester = (input) -> Arrays.stream(Locale.getAvailableLocales())
                                                                             .map(l -> l.getDisplayLanguage(Locale.ENGLISH))
                                                                             .filter(lang -> !lang.isEmpty())
                                                                             .filter(lang -> lang.toLowerCase().startsWith(input.toLowerCase()))
                                                                             .distinct()
                                                                             .sorted()
                                                                             .collect(Collectors.toList());
        SuggestingTagField field = new SuggestingTagField(languageSuggester, Color.WHITE, Color.DARK_GRAY);
        field.setPreferredSize(new Dimension(100, 30));
        return field;
    }

    private JFormattedTextField createYearField() {
        NumberFormat format = NumberFormat.getIntegerInstance();
        format.setGroupingUsed(false);
        JFormattedTextField field = new JFormattedTextField(format);
        field.setColumns(4);
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 30));
        textFields.add(field);
        return field;
    }

    private SuggestingTagField createGenreField(YassProperties yassProperties) {
        // Create the suggestion provider function
        Function<String, List<String>> genreSuggester = (input) -> {
            String genreTags = yassProperties.getProperty("genre-tag", "") + "|" + yassProperties.getProperty("genre-more-tag", "");
            String[] allGenres = genreTags.split("\\|");
            return Arrays.stream(allGenres)
                         .map(String::trim)
                         .filter(g -> !g.isEmpty() && !g.equals("MORE"))
                         .filter(g -> g.toLowerCase().startsWith(input.toLowerCase()))
                         .distinct()
                         .collect(Collectors.toList());
        };
        SuggestingTagField field = new SuggestingTagField(genreSuggester);
        field.setPreferredSize(new Dimension(100, 30));
        return field;
    }
    
    private SuggestingTagField createTagField(YassProperties yassProperties) {
        // Create the suggestion provider function
        Function<String, List<String>> tagsSuggester = (input) -> {
            String tags = yassProperties.getProperty("tags-tag", "");
            String[] allTags = tags.split("\\|");
            return Arrays.stream(allTags)
                         .map(String::trim)
                         .filter(g -> !g.isEmpty())
                         .filter(g -> g.toLowerCase().startsWith(input.toLowerCase()))
                         .distinct()
                         .collect(Collectors.toList());
        };
        SuggestingTagField field = new SuggestingTagField(tagsSuggester);
        field.setPreferredSize(new Dimension(100, 30));
        return field;
    }
    
    private JPanel createGapSpinnerPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(Box.createHorizontalStrut(5));
        panels.add(panel);
        gapSpinner = new TimeSpinner(I18.get("mpop_gap"), 0, 10000);
        gapSpinner.setLabelSize(new Dimension(120, 30));
        gapSpinner.setSpinnerSize(new Dimension(100, 30));
        gapSpinner.getSpinner().setFocusable(false);
        textFields.add(gapSpinner.getTextField());
        panel.add(gapSpinner);
        labels.addAll(gapSpinner.getLabels());
        return panel;
    }


    public void initSongHeader(YassTable table) {
        YassProperties yassProperties = actions.getProperties();
        boolean debugWaveform = yassProperties.getBooleanProperty("debug-waveform");
        String audio = (debugWaveform && StringUtils.isNotEmpty(table.getVocals()))
                ? table.getVocals()
                : (StringUtils.isNotEmpty(table.getAudio()) ? table.getAudio() : table.getMP3());
        audioField.setText(audio);
        if (debugWaveform && StringUtils.isNotEmpty(table.getVocals())) {
            setAudioToVocals();
            determinePitches();
        }
        setInternalUpdate(true);
        gapSpinner.setTime((int)table.getGap());
        gapSpinner.setDuration((int)(table.getGap() * 10));
        bpmField.setText(String.valueOf(table.getBPM()));
        int duration = actions.getMP3() != null ? (int) (actions.getMP3().getDuration() / 1000) : 10000;
        startSpinner.setTime((int) (table.getStart() * 1000));
        startSpinner.setDuration(duration);
        int end = table.getEnd() > 0 ? (int) table.getEnd() : 10000;
        endSpinner.setTime(Math.min(duration, end));
        endSpinner.setDuration(Math.max(10000, duration));
        languageField.setText(table.getLanguage());
        yearField.setText(table.getYear());
        genreField.setText(table.getGenre());
        tagField.setText(table.getTags());
        setInternalUpdate(false);
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

    private JPanel createBpmPanel() {
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

        bpmField = new JTextField("");
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


    private JPanel createStartSpinnerPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(Box.createHorizontalStrut(5));
        panels.add(panel);
        startSpinner = new TimeSpinner(I18.get("mpop_audio_start"), 0, 0);
        startSpinner.setLabelSize(new Dimension(120, 30)); // Same as gapSpinner
        startSpinner.setSpinnerSize(new Dimension(100, 30)); // Same as gapSpinner
        startSpinner.getSpinner().setFocusable(false);
        panel.add(startSpinner);
        labels.addAll(startSpinner.getLabels());
        textFields.add(startSpinner.getTextField());
        return panel;
    }

    private JPanel createEndPanel() {
        // Group End-Spinner controls into their own panel for alignment
        JPanel endPanel = new JPanel();
        endPanel.setLayout(new BoxLayout(endPanel, BoxLayout.X_AXIS));
        panels.add(endPanel);
        endSpinner = new TimeSpinner(I18.get("mpop_audio_end"), 0, 0);
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
     * @param sheet The YassSheet instance to be updated by the listeners.
     */
    private void addListeners(YassSheet sheet) {
        if (audioSelector != null) {
            audioSelector.addItemListener(e -> {
                YassTable table = sheet.getTable();
                if (e.getStateChange() == ItemEvent.DESELECTED && audioField != null) {
                    if (isInternalUpdate) {
                        return;
                    }
                    LOGGER.info("SongHeader: Deselecting " + e.getItem() + ": " + audioField.getText());
                    table.setAudioByTag(e.getItem().toString(), audioField.getText());
                }
                if (e.getStateChange() == ItemEvent.SELECTED && audioField != null) {
                    if (isInternalUpdate) {
                        return;
                    }
                    YassRow mp3Row = table.getCommentRow(e.getItem() + ":");
                    if (mp3Row != null) {
                        YassProperties properties = actions.getProperties();
                        audioField.setText(mp3Row.getHeaderComment());
                        LOGGER.info("SongHeader: Selecting " + e.getItem() + ": " + audioField.getText());
                        audioField.repaint();
                        actions.openMp3(table.getDir() + File.separator + mp3Row.getHeaderComment());
                    } else {
                        LOGGER.info("SongHeader: Selecting. No " + e.getItem() + " configured");
                        audioField.setText(StringUtils.EMPTY);
                    }
                    sheet.refreshImage();
                }
            });
        }

        YassUtils.addChangeListener(audioField, e -> {
            if (isInternalUpdate) {
                return;
            }
            String selectedAudio = (audioSelector != null && audioSelector.getSelectedItem() != null)
                    ? audioSelector.getSelectedItem().toString() : StringUtils.EMPTY;
            YassTable table = sheet.getTable();
            if (audioField.getText() != null && table != null) {
                table.setAudioByTag(selectedAudio, audioField.getText());
                if (StringUtils.isNotEmpty(this.audioField.getText())) {
                    determinePitches();
                }
            }
        });

        gapSpinner.getSpinner().addChangeListener(e -> {
            if (isInternalUpdate) {
                return;
            }
            actions.setGap(gapSpinner.getTime());
        });
        startSpinner.getSpinner().addChangeListener(e -> {
            if (isInternalUpdate) {
                return;
            }
            actions.setStart(startSpinner.getTime());
        });
                                                    
        endSpinner.getSpinner().addChangeListener(e -> {
            if (isInternalUpdate) {
                return;
            }
            actions.setEnd(endSpinner.getTime());
        });

        bpmField.addActionListener(e -> {
            if (isInternalUpdate) {
                return;
            }
            YassTable table = sheet.getTable();
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
        languageField.addChangeListener(e -> {
            if (isInternalUpdate || sheet == null || sheet.getTable() == null) {
                return;
            }
            YassTable table = sheet.getTable();
            table.setLanguage(languageField.getText());
        });

        YassUtils.addChangeListener(yearField, e -> {
            if (isInternalUpdate || sheet == null || sheet.getTable() == null) {
                return;
            }
            YassTable table = sheet.getTable();
            table.setYear(yearField.getText());
        });

        genreField.addChangeListener(e -> {
            if (isInternalUpdate || sheet == null || sheet.getTable() == null) {
                return;
            }
            YassTable table = sheet.getTable();
            table.setGenre(genreField.getText());
        });

        tagField.addChangeListener(e -> {
            if (isInternalUpdate || sheet == null || sheet.getTable() == null) {
                return;
            }
            YassTable table = sheet.getTable();
            table.setTags(tagField.getText());
        });
    }
    
    private void determinePitches() {
        YassProperties prop = actions.getProperties();
        YassPlayer player = actions.getMP3();
        if (player.getPitchDataList() != null && !player.getPitchDataList().isEmpty()) {
            return;
        }
        if (this.audioSelector.getSelectedItem()
                              .toString()
                              .equalsIgnoreCase(UltrastarHeaderTag.VOCALS.toString()) &&
                prop.getBooleanProperty("debug-waveform") && player.getTempFile() != null) {
            player.setPitchDataList(
                    PitchDetector.detectPitch(player.getTempFile(), prop));
            actions.getSheet().repaint();
        } else {
            player.setPitchDataList(Collections.emptyList());
        }
    }
    
    public void setAudioToVocals() {
        setInternalUpdate(true);
        audioSelector.setSelectedItem(UltrastarHeaderTag.VOCALS.toString());
        setInternalUpdate(false);
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
            return audioField;
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
            audioSelector.setBackground(darkMode ? YassSheet.HI_GRAY_2_DARK_MODE : Color.WHITE);
            audioSelector.setForeground(darkMode ? Color.WHITE : null);
        }
        for (JTextField textField : textFields) {
            textField.setBackground(darkMode ? YassSheet.HI_GRAY_2_DARK_MODE : Color.WHITE);
            textField.setForeground(darkMode ? Color.WHITE : null);
        }
        for (JPanel panel : panels) {
            panel.setBackground(darkMode ? YassSheet.HI_GRAY_2_DARK_MODE : null);
            panel.setForeground(darkMode ? Color.WHITE : null);
        }
        for (JLabel label : labels) {
            label.setBackground(darkMode ? YassSheet.HI_GRAY_2_DARK_MODE : Color.WHITE);
            label.setForeground(darkMode ? Color.WHITE : null);
        }
        repaint();
    }
    
    private YassTable getTable() {
        return getActions().getTable();
    }

    public void reset() {
        isInternalUpdate = true; // Disable listeners

        audioSelector.setSelectedIndex(0);
        audioField.setText("");
        gapSpinner.setTime(0);
        bpmField.setText("");
        startSpinner.setTime(0);
        endSpinner.setTime(0);
        languageField.setText("");
        yearField.setText("");
        genreField.setText("");
        tagField.setText("");

        isInternalUpdate = false; // Re-enable listeners
    }
}
