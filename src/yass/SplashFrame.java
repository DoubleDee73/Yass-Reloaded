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
import java.io.File;
import java.util.StringJoiner;

public class SplashFrame extends JWindow {

    private JProgressBar progressBar;
    private OutlinedLabel textLabel;
    
    public SplashFrame(YassPlayer yassPlayer,
                       YassSheet yassSheet) {
        JPanel content = new JPanel(new BorderLayout());
        setName(I18.get("splash_title"));
        JLabel imageLabel = new JLabel(new ImageIcon(getClass().getResource("/yass/resources/splash/splash.png"))); // Bilddatei hinzufügen
        imageLabel.setLayout(new BorderLayout());
        textLabel = new OutlinedLabel("Loading please wait...", new Font("Arial", Font.PLAIN, 18),
                                                    Color.WHITE, Color.BLACK, 1);
        textLabel.setHorizontalAlignment(SwingConstants.CENTER);
        textLabel.setBorder(BorderFactory.createEmptyBorder(20, 10, 10, 10));

        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        imageLabel.add(textLabel, BorderLayout.NORTH);
        imageLabel.add(progressBar, BorderLayout.SOUTH);

        content.add(imageLabel);
        setContentPane(content);

        pack();
        setLocationRelativeTo(null); // Splash-Screen in der Mitte anzeigen
        setVisible(true);
        yassPlayer.initNoteMap();
        byte[] note;
        int counter = 0;
        String path;
        for (int i = 21; i < 109; i++) {
            path = "samples/longnotes/" + i;
            File file = new File(YassPlayer.USER_PATH + path + ".wav");
            String noteName = yassSheet.getNoteName(i % 12) + ((i / 12) - 1);
            StringJoiner label = new StringJoiner(StringUtils.SPACE);
            if (!file.exists()) {
                label.add(I18.get("splash_create"));
            } else {
                label.add(I18.get("splash_load"));
            }
            label.add(noteName);
            textLabel.setText(label.toString());
            textLabel.repaint();
            progressBar.setValue(100 * counter++ / 176);
            note = yassPlayer.createNotePlayer(path);
            yassPlayer.addNoteToMap(note, i, true);
            progressBar.setValue(100 * counter++ / 176);
            note = yassPlayer.createNotePlayer("samples/shortnotes/" + i);
            yassPlayer.addNoteToMap(note, i, false);
            progressBar.repaint();
        }
        setVisible(false);
        dispose();
    }
}

class OutlinedLabel extends JLabel {
    private final Color outlineColor;
    private final int outlineSize;

    public OutlinedLabel(String text, Font font, Color textColor, Color outlineColor, int outlineSize) {
        super(text);
        setFont(font);
        setForeground(textColor);
        this.outlineColor = outlineColor;
        this.outlineSize = outlineSize;
        setOpaque(false); // Hintergrund transparent
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Umrandung zeichnen
        g2.setColor(outlineColor);
        for (int i = -outlineSize; i <= outlineSize; i++) {
            for (int j = -outlineSize; j <= outlineSize; j++) {
                if (i != 0 || j != 0) {
                    g2.drawString(getText(), getInsets().left + i, getInsets().top + g2.getFontMetrics().getAscent() + j);
                }
            }
        }

        // Haupttext zeichnen
        g2.setColor(getForeground());
        g2.drawString(getText(), getInsets().left, getInsets().top + g2.getFontMetrics().getAscent());

        g2.dispose();
    }
}
