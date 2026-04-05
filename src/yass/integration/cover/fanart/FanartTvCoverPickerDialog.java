package yass.integration.cover.fanart;

import org.apache.commons.lang3.StringUtils;
import yass.YassUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.List;

public class FanartTvCoverPickerDialog extends JDialog {
    private static final int PREVIEW_SIZE = 250;

    private FanartTvCoverCandidate selectedCandidate;

    public FanartTvCoverPickerDialog(Window owner,
                                     String title,
                                     String downloadLabel,
                                     List<FanartTvCoverCandidate> candidates) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setSize(620, 430);
        setLocationRelativeTo(owner);

        JPanel imagePanel = new JPanel();
        imagePanel.setLayout(new BoxLayout(imagePanel, BoxLayout.X_AXIS));
        ButtonGroup group = new ButtonGroup();

        for (int i = 0; i < candidates.size(); i++) {
            FanartTvCoverCandidate candidate = candidates.get(i);
            JToggleButton button = createCandidateButton(candidate);
            group.add(button);
            imagePanel.add(button);
            imagePanel.add(Box.createHorizontalStrut(10));
            if (i == 0) {
                button.setSelected(true);
                selectedCandidate = candidate;
            }
        }

        JScrollPane scrollPane = new JScrollPane(imagePanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(32);
        add(scrollPane, BorderLayout.CENTER);

        JButton downloadButton = new JButton(downloadLabel);
        downloadButton.addActionListener(e -> dispose());
        JButton cancelButton = new JButton(UIManager.getString("OptionPane.cancelButtonText"));
        cancelButton.addActionListener(e -> {
            selectedCandidate = null;
            dispose();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(downloadButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(downloadButton);
    }

    public FanartTvCoverCandidate showDialog() {
        setVisible(true);
        return selectedCandidate;
    }

    private JToggleButton createCandidateButton(FanartTvCoverCandidate candidate) {
        JToggleButton button = new JToggleButton();
        button.setLayout(new BorderLayout(4, 4));
        button.setPreferredSize(new Dimension(PREVIEW_SIZE + 10, PREVIEW_SIZE + 48));
        button.setFocusable(false);

        JLabel imageLabel = new JLabel("Loading...", SwingConstants.CENTER);
        imageLabel.setPreferredSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE));
        button.add(imageLabel, BorderLayout.CENTER);

        StringBuilder labelBuilder = new StringBuilder();
        if (StringUtils.isNotBlank(candidate.getAlbumName())) {
            labelBuilder.append(candidate.getAlbumName());
        }
        if (candidate.getLikes() > 0) {
            if (labelBuilder.length() > 0) {
                labelBuilder.append(" · ");
            }
            labelBuilder.append("Likes: ").append(candidate.getLikes());
        }
        JLabel textLabel = new JLabel(StringUtils.defaultIfBlank(labelBuilder.toString(), " "), SwingConstants.CENTER);
        button.add(textLabel, BorderLayout.SOUTH);

        button.setToolTipText(candidate.getImageUrl());
        button.addActionListener(e -> selectedCandidate = candidate);

        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                BufferedImage image = ImageIO.read(new URI(candidate.getImageUrl()).toURL());
                if (image == null) {
                    return null;
                }
                BufferedImage scaled = YassUtils.getScaledInstance(image, PREVIEW_SIZE, PREVIEW_SIZE);
                return new ImageIcon(scaled);
            }

            @Override
            protected void done() {
                try {
                    ImageIcon icon = get();
                    if (icon != null) {
                        imageLabel.setText("");
                        imageLabel.setIcon(icon);
                    } else {
                        imageLabel.setText("Preview unavailable");
                    }
                } catch (Exception e) {
                    imageLabel.setText("Preview unavailable");
                }
            }
        }.execute();

        return button;
    }
}
