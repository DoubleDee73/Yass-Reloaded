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

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CheckboxOptionsDialog {

    public static List<KeyValue> showCheckboxOptionDialog(Component parent, String title, String message,
                                                          List<KeyValue> options) {
        if (options == null || options.isEmpty()) {
            return new ArrayList<>();
        }

        JPanel mainPanel = new JPanel(new BorderLayout(5, 10));
        if (message != null && !message.isEmpty()) {
            mainPanel.add(new JLabel(message), BorderLayout.NORTH);
        }

        JPanel optionsPanel = new JPanel(new GridBagLayout());
        List<JCheckBox> checkBoxes = new ArrayList<>();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;

        int y = 0;
        for (KeyValue option : options) {
            gbc.gridy = y++;

            JCheckBox checkBox = new JCheckBox();
            checkBox.setSelected(option.checked());
            checkBoxes.add(checkBox);

            // Checkbox (schmale Spalte)
            gbc.gridx = 0;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            optionsPanel.add(checkBox, gbc);

            // Key/Label (flexibler Platz bis zur Mitte)
            gbc.gridx = 1;
            gbc.weightx = 0.5;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            optionsPanel.add(new JLabel(option.key()), gbc);

            // Value (flexibler Platz ab der Mitte)
            gbc.gridx = 2;
            gbc.weightx = 0.5;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            optionsPanel.add(new JLabel(option.value()), gbc);
        }

        mainPanel.add(optionsPanel, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(parent, mainPanel, title, JOptionPane.OK_CANCEL_OPTION,
                                                   JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            List<KeyValue> selectedOptions = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                if (checkBoxes.get(i).isSelected()) {
                    selectedOptions.add(options.get(i));
                }
            }
            return selectedOptions;
        }

        return null;
    }
}