package yass.integration.lyrics.lrclib;

import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;

public final class LrcLibQueryDialog {
    private LrcLibQueryDialog() {
    }

    public static LrcLibSearchQuery show(Window owner,
                                         String title,
                                         String artistLabel,
                                         String titleLabel,
                                         String searchLabel,
                                         String initialArtist,
                                         String initialTitle) {
        JTextField artistField = new JTextField(StringUtils.defaultString(initialArtist), 28);
        JTextField titleField = new JTextField(StringUtils.defaultString(initialTitle), 28);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel(artistLabel), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(artistField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel(titleLabel), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(titleField, gbc);

        int result = JOptionPane.showConfirmDialog(owner,
                panel,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String artist = StringUtils.trimToEmpty(artistField.getText());
        String trackTitle = StringUtils.trimToEmpty(titleField.getText());
        if (StringUtils.isBlank(artist) || StringUtils.isBlank(trackTitle)) {
            JOptionPane.showMessageDialog(owner,
                    searchLabel,
                    title,
                    JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return new LrcLibSearchQuery(artist, trackTitle);
    }
}
