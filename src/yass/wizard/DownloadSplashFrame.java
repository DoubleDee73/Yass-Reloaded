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

package yass.wizard;

import yass.I18;

import javax.swing.*;
import java.awt.*;

public class DownloadSplashFrame extends JDialog {
    private final JProgressBar progressBar;
    private final JTextArea outputArea;
    private final JLabel etaLabel;
    private final JButton closeButton;

    public DownloadSplashFrame(Window owner) {
        super(owner, I18.get("wizard_youtube_downloading"), ModalityType.APPLICATION_MODAL);
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        outputArea = new JTextArea(15, 60);
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(outputArea);
        content.add(scrollPane, BorderLayout.CENTER);

        JPanel southContainer = new JPanel(new BorderLayout(0, 5));

        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressPanel.add(progressBar, BorderLayout.CENTER);

        etaLabel = new JLabel("ETA: --:--");
        progressPanel.add(etaLabel, BorderLayout.EAST);
        southContainer.add(progressPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        closeButton.setEnabled(false);
        buttonPanel.add(closeButton);
        southContainer.add(buttonPanel, BorderLayout.SOUTH);

        content.add(southContainer, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
    }

    public void enableCloseButton() {
        closeButton.setEnabled(true);
    }

    public void appendText(String text) {
        outputArea.append(text + "\n");
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    public void updateProgress(float progress, long etaInSeconds) {
        progressBar.setValue((int) progress);
        if (etaInSeconds >= 3600) {
            long hours = etaInSeconds / 3600;
            long minutes = (etaInSeconds % 3600) / 60;
            long seconds = etaInSeconds % 60;
            etaLabel.setText(String.format("ETA: %d:%02d:%02d", hours, minutes, seconds));
        } else {
            etaLabel.setText(String.format("ETA: %02d:%02d", etaInSeconds / 60, etaInSeconds % 60));
        }
    }
}
