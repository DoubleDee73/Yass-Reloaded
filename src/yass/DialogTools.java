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
import javax.swing.event.DocumentListener;
import java.awt.*;

public class DialogTools {

    public static JPanel createHeaderPanel(String title, int leftPadding) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        JLabel la = new JLabel("<html><font color=gray>" + title);
        la.setVerticalAlignment(JLabel.TOP);

        la.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JLabel spacer = new JLabel("");
        //spacer.setSize(new Dimension(120, 10));
        spacer.setPreferredSize(new Dimension(leftPadding, 20));
        //spacer.setMaximumSize(new Dimension(200, 20));
        row.add(spacer);
        row.add(la);
        return row;
    }
    
    public static JPanel createTextfield(String label, int labelWidth, String text, DocumentListener listener,
                                         String txtFieldName) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        JLabel lab = new JLabel(label);
        lab.setVerticalAlignment(JLabel.CENTER);
        lab.setHorizontalAlignment(JLabel.LEFT);
        //lab.setSize(new Dimension(120, 10));
        lab.setPreferredSize(new Dimension(labelWidth, 20));
        //lab.setMaximumSize(new Dimension(200, 20));

        JTextField txtField = new JTextField(text);
        if (StringUtils.isNotEmpty(txtFieldName)) {
            txtField.setName(txtFieldName);
        }
        if (listener != null) {
            txtField.getDocument().addDocumentListener(listener);
        }
        row.add(lab);
        row.add(txtField);
        return row;
    }
}
