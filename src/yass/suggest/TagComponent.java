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

package yass.suggest;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Represents a single visual "tag" with a label and a remove button.
 */
public class TagComponent extends JPanel {
    private final String text;
    private Color fontColor;
    private Color backgroundColor;

    public TagComponent(String text, Consumer<String> removeCallback) {
        this(text, removeCallback, Color.WHITE, new Color(0, 120, 215));
    }
    
    public TagComponent(String text, Consumer<String> removeCallback,
                        Color fontColor, Color backgroundColor) {
        this.text = text;
        setLayout(new BorderLayout(4, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(0, 5, 0, 4));

        this.fontColor = fontColor;
        this.backgroundColor = backgroundColor;
        JLabel label = new JLabel(text);
        label.setForeground(this.fontColor);

        JButton removeButton = new JButton("x");
        removeButton.setMargin(new Insets(0, 0, 0, 0));
        removeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeButton.setOpaque(false);
        removeButton.setContentAreaFilled(false);
        removeButton.setBorderPainted(false);
        removeButton.setFocusPainted(false);
        removeButton.setForeground(Color.WHITE);
        removeButton.setFont(getFont().deriveFont(Font.BOLD));
        removeButton.addActionListener(e -> removeCallback.accept(text));

        // Hover effect for the remove button
        removeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                removeButton.setForeground(Color.RED);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                removeButton.setForeground(Color.WHITE);
            }
        });

        add(label, BorderLayout.CENTER);
        add(removeButton, BorderLayout.EAST);
    }

    @Override
    public String toString() {
        return text;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(backgroundColor); // A nice blue color
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
        g2.dispose();
        super.paintComponent(g);
    }
}