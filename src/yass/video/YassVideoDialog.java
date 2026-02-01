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

package yass.video;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;
import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.TimeSpinner;
import yass.YassActions;
import yass.YassPlayer;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class YassVideoDialog extends JDialog {

    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    private final JFXPanel jfxPanel;
    private MediaPlayer mediaPlayer;
    private final JSlider timeSlider;
    private final JButton playPauseButton;
    private final TimeSpinner gapSpinner;
    private final JLabel timeLabel;
    private boolean isSliderDragging = false;
    private final YassPlayer yassPlayer;
    private final YassActions yassActions;

    private double videoDurationSeconds = 0;
    private JTextField videoFile;
    private int videoGapMs = 0; // in milliseconds
    private Consumer<Integer> onGapChanged;
    private Consumer<String> onFileChanged;

    public YassVideoDialog(Frame owner, YassPlayer yassPlayer, YassActions yassActions) {
        super(owner, "Video Preview", false); // Non-modal
        this.yassPlayer = yassPlayer;
        this.yassActions = yassActions;

        setSize(1000, 700);
        setLayout(new BorderLayout());
        setLocationRelativeTo(owner); // Center relative to the main window

        // --- Video Panel ---
        jfxPanel = new JFXPanel();
        jfxPanel.setBackground(Color.BLACK);
        add(jfxPanel, BorderLayout.CENTER);

        // --- Controls Panel ---
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));

        // 1. Slider Row
        JPanel sliderPanel = new JPanel(new BorderLayout());
        timeSlider = new JSlider(0, 1000, 0);
        timeSlider.setEnabled(false);
        timeSlider.addChangeListener(e -> {
            if (timeSlider.getValueIsAdjusting()) {
                isSliderDragging = true;
                updateTimeLabelFromSlider();
            } else if (isSliderDragging) {
                isSliderDragging = false;
                double pos = timeSlider.getValue() / 1000.0;
                if (videoDurationSeconds > 0) {
                    double videoTime = pos * videoDurationSeconds;
                    seekVideo(videoTime);

                    double audioTime = videoTime - videoGapMs;
                    if (audioTime < 0) {
                        audioTime = 0;
                    }

                    yassPlayer.interruptMP3();
                }
            }
        });
        sliderPanel.add(timeSlider, BorderLayout.CENTER);

        timeLabel = new JLabel("00:00.000");
        timeLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        timeLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        sliderPanel.add(timeLabel, BorderLayout.EAST);

        southPanel.add(sliderPanel);

        JPanel controlsPanel = new JPanel(new BorderLayout(10, 0));
        controlsPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Left controls
        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        playPauseButton = new JButton(yassActions.getIcon("play24Icon"));
        playPauseButton.addActionListener(e -> togglePlayPause());
        leftControls.add(playPauseButton);

        leftControls.add(Box.createHorizontalStrut(10));

        gapSpinner = new TimeSpinner(I18.get("tool_video_gap"), 0, 100000, TimeSpinner.NEGATIVE);
        gapSpinner.getSpinner().addChangeListener(e -> {
            int ms = gapSpinner.getTime();
            if (ms != videoGapMs) {
                videoGapMs = ms;
                if (onGapChanged != null) {
                    onGapChanged.accept(ms);
                }
            }
        });
        leftControls.add(gapSpinner);
        controlsPanel.add(leftControls, BorderLayout.WEST);

        // Center: File and Button
        JPanel filePanel = new JPanel(new BorderLayout(5, 0));
        videoFile = new JTextField();
        filePanel.add(videoFile, BorderLayout.CENTER);

        JButton openFileButton = new JButton();
        openFileButton.setIcon(yassActions.getIcon("open24Icon"));
        openFileButton.setToolTipText(I18.get("video_dialog_open"));
        openFileButton.addActionListener(e -> chooseVideoFile());
        filePanel.add(openFileButton, BorderLayout.EAST);

        controlsPanel.add(filePanel, BorderLayout.CENTER);

        southPanel.add(controlsPanel);
        add(southPanel, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stop();
            }
        });
    }

    public void setOnFileChanged(Consumer<String> callback) {
        this.onFileChanged = callback;
    }

    public void setOnGapChanged(Consumer<Integer> callback) {
        this.onGapChanged = callback;
    }

    private void chooseVideoFile() {
        JFileChooser chooser = new JFileChooser();
        if (videoFile != null && StringUtils.isNotEmpty(videoFile.getText())) {
            chooser.setCurrentDirectory(new File(videoFile.getText()).getParentFile());
        } else {
            String songDir = yassActions.getTable() != null ? yassActions.getTable().getDir() : null;
            if (songDir != null) {
                chooser.setCurrentDirectory(new File(songDir));
            }
        }

        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Video Files", "mp4", "avi", "mkv", "flv", "mov", "wmv", "mpg", "mpeg");
        chooser.setFileFilter(filter);

        int returnVal = chooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (onFileChanged != null) {
                onFileChanged.accept(file.getName());
            }
            setVideo(file.getAbsolutePath());
        }
    }

    private void updateTimeLabelFromSlider() {
        if (videoDurationSeconds <= 0) {
            return;
        }
        double pos = timeSlider.getValue() / 1000.0;
        double currentSeconds = pos * videoDurationSeconds;
        timeLabel.setText(formatTime(currentSeconds));
    }

    private String formatTime(double seconds) {
        long totalMillis = (long) (seconds * 1000);
        long mm = (totalMillis / 1000) / 60;
        long ss = (totalMillis / 1000) % 60;
        long ms = totalMillis % 1000;
        return String.format("%02d:%02d.%03d", mm, ss, ms);
    }

    public void setVideo(String vd) {
        if (vd == null) {
            closeVideo();
            return;
        }
        videoFile.setText(vd.replace('\\', '/'));
        loadVideoFile(videoFile.getText());
    }

    public void setVideoGap(int seconds) {
        if (this.videoGapMs != seconds) {
            this.videoGapMs = seconds;
            gapSpinner.setTime(seconds);
        }
    }

    public void updateTime(long audioTimeMs) {
        if (isSliderDragging) {
            return;
        }

        double audioSeconds = audioTimeMs / 1000.0;
        double targetVideoTime = audioSeconds + (videoGapMs / 1000.0);
        if (targetVideoTime < 0) {
            targetVideoTime = 0;
        }

        if (videoDurationSeconds > 0) {
            final double finalVideoTime = targetVideoTime;
            SwingUtilities.invokeLater(() -> {
                int sliderValue = (int) ((finalVideoTime / videoDurationSeconds) * 1000);
                timeSlider.setValue(sliderValue);
                timeLabel.setText(formatTime(finalVideoTime));
            });
        }

        final double finalTargetTime = targetVideoTime;
        Platform.runLater(() -> {
            if (mediaPlayer != null) {
                if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                    double currentVideoTime = mediaPlayer.getCurrentTime().toSeconds();
                    double diff = Math.abs(currentVideoTime - finalTargetTime);
                    if (diff > 0.1) {
                        mediaPlayer.seek(Duration.seconds(finalTargetTime));
                    }
                } else {
                    if (finalTargetTime >= 0) {
//                         mediaPlayer.play();
                        mediaPlayer.seek(Duration.seconds(finalTargetTime));
                    }
                }
            }
        });
    }

    public void closeVideo() {
        stop();
        videoFile.setText("");
    }

    private void loadVideoFile(String path) {
        Platform.runLater(() -> {
            try {
                if (mediaPlayer != null) {
                    mediaPlayer.dispose();
                }

                File file = new File(path);
                if (!file.exists()) {
                    SwingUtilities.invokeLater(() -> videoFile.setText(""));
                    return;
                }

                Media media = new Media(file.toURI().toString());
                mediaPlayer = new MediaPlayer(media);
                mediaPlayer.setAutoPlay(false);
                mediaPlayer.setVolume(0);

                MediaView mediaView = new MediaView(mediaPlayer);
                BorderPane root = new BorderPane();
                root.setCenter(mediaView);

                mediaView.fitWidthProperty().bind(root.widthProperty());
                mediaView.fitHeightProperty().bind(root.heightProperty());
                mediaView.setPreserveRatio(true);

                Scene scene = new Scene(root, javafx.scene.paint.Color.BLACK);
                jfxPanel.setScene(scene);

                mediaPlayer.setOnReady(() -> {
                    videoDurationSeconds = media.getDuration().toSeconds();
                    gapSpinner.setDuration((int) (videoDurationSeconds * 1000));
                    SwingUtilities.invokeLater(() -> {
                        timeSlider.setEnabled(true);
                        videoFile.setText(file.getName());
                        setTitle(I18.get("video_dialog_title") + " - " + file.getName());
                    });
                });

                mediaPlayer.setOnError(() -> {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Media Error: " +
                            mediaPlayer.getError().getMessage()));
                });

            } catch (Exception e) {
                LOGGER.log(java.util.logging.Level.SEVERE, "Error loading video: " + path, e);
                SwingUtilities.invokeLater(
                        () -> JOptionPane.showMessageDialog(this, "Error loading video: " + e.getMessage()));
            }
        });
    }

    private void togglePlayPause() {
        if (yassPlayer.isPlaying()) {
            yassPlayer.interruptMP3();
            pause();
        } else {
            double currentSliderPos = timeSlider.getValue() / 1000.0;
            double videoTime = currentSliderPos * videoDurationSeconds;

            double audioStartTime = videoTime - (videoGapMs / 1000.0);
            if (audioStartTime < 0) {
                audioStartTime = 0;
            }

            long startMs = (long) (audioStartTime * 1000);

            yassPlayer.playSelection(startMs * 1000, -1, null);
            play();
        }
    }

    public int getVideoGapMs() {
        return videoGapMs;
    }

    public void play() {
        Platform.runLater(() -> {
            if (mediaPlayer != null) {
                mediaPlayer.play();
                SwingUtilities.invokeLater(() -> playPauseButton.setIcon(yassActions.getIcon("pause24Icon")));
            }
        });
    }

    public void pause() {
        Platform.runLater(() -> {
            if (mediaPlayer != null) {
                mediaPlayer.pause();
                SwingUtilities.invokeLater(() -> playPauseButton.setIcon(yassActions.getIcon("play24Icon")));
            }
        });
    }

    public void stop() {
        Platform.runLater(() -> {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
            }
        });
        if (yassPlayer.isPlaying()) {
            yassPlayer.interruptMP3();
        }
        SwingUtilities.invokeLater(() -> {
            playPauseButton.setIcon(yassActions.getIcon("play24Icon"));
            timeSlider.setValue(0);
            timeLabel.setText("00:00.000");
        });
    }

    public int getTime() {
        if (timeSlider == null || videoDurationSeconds <= 0) {
            return 0;
        }
        return (int) (timeSlider.getValue() * videoDurationSeconds);
    }

    public void seekVideo(double seconds) {
        Platform.runLater(() -> {
            if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(seconds));
            }
        });
    }
}
