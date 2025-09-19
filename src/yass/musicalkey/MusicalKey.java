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

package yass.musicalkey;

import yass.I18;
import yass.YassActions;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class MusicalKey extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JComboBox<MusicalKeyEnum> cboKey;
    private JLabel lblKey;
    
    private YassActions actions;

    public MusicalKey(YassActions yassActions) {
        // Manually initialize components
        contentPane = new JPanel(new BorderLayout(10, 10));
        buttonOK = new JButton("OK");
        buttonCancel = new JButton("Cancel");
        cboKey = new JComboBox<>();
        lblKey = new JLabel();

        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        // Build UI with standard Swing layouts
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        topPanel.add(lblKey);
        topPanel.add(cboKey);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(buttonOK);
        bottomPanel.add(buttonCancel);

        contentPane.add(topPanel, BorderLayout.CENTER);
        contentPane.add(bottomPanel, BorderLayout.SOUTH);
        contentPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        lblKey.setText(I18.get("musical.key.label"));
        initKeys();
        this.actions = yassActions;
        MusicalKeyEnum currentKey = actions.getMP3().getKey();
        if (currentKey != MusicalKeyEnum.UNDEFINED) {
            cboKey.setSelectedItem(currentKey);
        }
        buttonOK.addActionListener(e -> onOK());

        buttonCancel.addActionListener(e -> onCancel());

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void initKeys() {
        for (MusicalKeyEnum musicalKey : MusicalKeyEnum.values()) {
            cboKey.addItem(musicalKey);
        }
    }

    private void onOK() {
        actions.getMP3().saveKey((MusicalKeyEnum) cboKey.getSelectedItem());
        actions.getTable().fireTableTableDataChanged();
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }
}
