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
import java.util.List;

public class RadioOptionsDialog {
    public static String showRadioOptionDialog(Component parent, String title, String message, List<String> options) {
        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel(message));
        ButtonGroup group = new ButtonGroup();
        JRadioButton[] buttons = new JRadioButton[options.size()];
        int i = 0;
        for (String option : options) {
            buttons[i] = new JRadioButton(option);
            group.add(buttons[i]);
            panel.add(buttons[i++]);
        }
        if (options.isEmpty()) {
            return null;
        }

        buttons[0].setSelected(true);
        int result = JOptionPane.showConfirmDialog(parent, panel, title, JOptionPane.OK_CANCEL_OPTION,
                                                   JOptionPane.QUESTION_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            for (JRadioButton b : buttons) {
                if (b.isSelected()) {
                    return b.getText();
                }
            }
        }
        return null;
    }
}
