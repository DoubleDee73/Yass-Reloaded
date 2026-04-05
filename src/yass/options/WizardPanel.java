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

package yass.options;

import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.YassEnum;
import yass.integration.separation.SeparationPreference;
import yass.integration.transcription.TranscriptionEngine;
import yass.wizard.WizardMidiMode;

import javax.swing.*;
import java.awt.*;

/**
 * Wizard-Default settings panel
 *
 * @author DoubleDee
 */
public class WizardPanel extends OptionsPanel {

    private static final long serialVersionUID = 1L;

    private void addSectionHeader(String text) {
        JPanel row = new JPanel(new BorderLayout());
        JLabel label = new JLabel("<html><b>" + text + "</b></html>");
        label.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
        row.add(label, BorderLayout.CENTER);
        getRight().add(row);
    }

    public void addRows() {
        setLabelWidth(200);
        addText(I18.get("options_wizard_defaults_creator"), "creator");
        addChoice(I18.get("options_wizard_defaults_midi"), WizardMidiMode.values(), "wizard-skip-midi");
        addSectionHeader(I18.get("options_wizard_defaults_engine_preferences"));

        boolean mvsepConfigured = StringUtils.isNotBlank(getProperty("mvsep-api-token"));
        boolean audioSepConfigured = StringUtils.isNotBlank(getProperty("audiosep-python"))
                && Boolean.parseBoolean(StringUtils.defaultIfBlank(getProperty("audiosep-health-ok"), "false"));
        boolean openAiConfigured = StringUtils.isNotBlank(getProperty("openai-api-key"));
        boolean whisperXConfigured = Boolean.parseBoolean(getProperty("whisperx-use-module"))
                ? StringUtils.isNotBlank(getProperty("whisperx-python"))
                : StringUtils.isNotBlank(getProperty("whisperx-command"));

        // Separation: LOCAL_FIRST, ONLINE_FIRST, MVSEP_ONLY, AUDIO_SEP_ONLY
        boolean[] sepEnabled = {
            mvsepConfigured || audioSepConfigured,  // LOCAL_FIRST  — needs at least one
            mvsepConfigured || audioSepConfigured,  // ONLINE_FIRST — needs at least one
            mvsepConfigured,                         // MVSEP_ONLY
            audioSepConfigured                       // AUDIO_SEP_ONLY
        };
        addEngineChoice(I18.get("options_external_tools_separation_preference"),
                        SeparationPreference.values(), sepEnabled, "separation-preference");

        // Transcription: ONLINE_FIRST, LOCAL_FIRST, OPENAI_ONLY, WHISPERX_ONLY
        boolean[] transcEnabled = {
            openAiConfigured || whisperXConfigured,  // ONLINE_FIRST
            openAiConfigured || whisperXConfigured,  // LOCAL_FIRST
            openAiConfigured,                         // OPENAI only
            whisperXConfigured                        // WHISPERX only
        };
        addEngineChoice(I18.get("options_external_tools_transcription_engine"),
                        TranscriptionEngine.values(), transcEnabled, "transcription-engine");
    }

    private void addEngineChoice(String labelText, YassEnum[] options, boolean[] enabled, String propertyKey) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));

        JLabel lab = new JLabel(labelText);
        lab.setVerticalAlignment(JLabel.CENTER);
        lab.setPreferredSize(new Dimension(getLabelWidth(), 20));

        String[] labels = new String[options.length];
        String[] keys = new String[options.length];
        for (int i = 0; i < options.length; i++) {
            labels[i] = options[i].getLabel();
            keys[i] = options[i].getValue();
        }

        JComboBox<String> box = new JComboBox<>(labels);
        box.setMaximumSize(new Dimension(350, 25));

        // Renderer: grey out disabled items
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                int idx = index < 0 ? box.getSelectedIndex() : index;
                if (idx >= 0 && idx < enabled.length && !enabled[idx]) {
                    setForeground(Color.GRAY);
                    setFont(getFont().deriveFont(java.awt.Font.ITALIC));
                }
                return this;
            }
        });

        // Prevent selecting disabled items; save valid selections
        final boolean[] updating = {false};
        box.addActionListener(e -> {
            if (updating[0]) return;
            int idx = box.getSelectedIndex();
            if (idx >= 0 && idx < enabled.length && !enabled[idx]) {
                updating[0] = true;
                for (int i = 0; i < enabled.length; i++) {
                    if (enabled[i]) { box.setSelectedIndex(i); break; }
                }
                updating[0] = false;
            } else if (idx >= 0) {
                setProperty(propertyKey, keys[idx]);
            }
        });

        // Restore saved value
        String saved = getProperty(propertyKey);
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equalsIgnoreCase(saved) && enabled[i]) {
                box.setSelectedIndex(i);
                break;
            }
        }
        // If saved value is disabled or not found, select first enabled
        int selectedIdx = box.getSelectedIndex();
        if (selectedIdx < 0 || (selectedIdx < enabled.length && !enabled[selectedIdx])) {
            for (int i = 0; i < enabled.length; i++) {
                if (enabled[i]) {
                    box.setSelectedIndex(i);
                    break;
                }
            }
        }

        row.add(lab);
        row.add(box);
        row.add(Box.createHorizontalGlue());
        lab.setAlignmentY(Component.TOP_ALIGNMENT);
        box.setAlignmentY(Component.TOP_ALIGNMENT);
        getRight().add(row);
        getRight().add(Box.createRigidArea(new Dimension(0, 2)));
    }
}

