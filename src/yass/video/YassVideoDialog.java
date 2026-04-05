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
import net.bramp.ffmpeg.FFmpeg;
import org.apache.commons.lang3.StringUtils;
import yass.*;
import yass.ffmpeg.FFMPEGLocator;

import com.jfposton.ytdlp.YtDlp;
import com.jfposton.ytdlp.YtDlpCallback;
import com.jfposton.ytdlp.YtDlpRequest;
import yass.options.YtDlpPanel;
import yass.wizard.DownloadSplashFrame;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private Runnable onDialogClosed;
    private boolean isInternalUpdate = false;
    private File video;

    private static final long UI_THROTTLE_NS    = 16_000_000L; // ~60 fps for slider/label
    private static final long SEEK_THROTTLE_NS  = 100_000_000L; // 100 ms for FX seek
    private static final Pattern YOUTUBE_ID_PATTERN = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?v=|shorts/|embed/)|youtu\\.be/)([A-Za-z0-9_-]{11})");
    private long lastUiUpdateNs   = 0;
    private long lastSeekUpdateNs = 0;
    private final AtomicInteger videoLoadGeneration = new AtomicInteger();
    private volatile VideoLoadTask currentVideoLoadTask;
    private volatile String failedVideoPath;
    private JDialog videoLoadDialog;
    private JLabel videoLoadStatusLabel;
    private JButton videoLoadCancelButton;

    /** Logical playhead position in seconds, updated immediately on stepMs so rapid key presses accumulate correctly. */
    private double logicalPositionSeconds = 0;

    private static final long VIDEO_TRANSCODE_TIMEOUT_MS = 60L * 60L * 1000L;

    public YassVideoDialog(Frame owner, YassPlayer yassPlayer, YassActions yassActions) {
        super(owner, "Video Preview", false); // Non-modal
        this.yassPlayer = yassPlayer;
        this.yassActions = yassActions;
        isInternalUpdate = true;
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
                    logicalPositionSeconds = videoTime; // keep logical position in sync
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
        leftControls.add(gapSpinner);
        controlsPanel.add(leftControls, BorderLayout.WEST);

        // Center: File and Button
        JPanel filePanel = new JPanel(new BorderLayout(5, 0));
        videoFile = new JTextField();
        filePanel.add(videoFile, BorderLayout.CENTER);

        videoFile.addActionListener((ActionEvent e) -> onVideoFieldEnter());

        JButton openFileButton = new JButton();
        openFileButton.setIcon(yassActions.getIcon("open24Icon"));
        openFileButton.setToolTipText(I18.get("video_dialog_open"));
        openFileButton.addActionListener(e -> chooseVideoFile());
        filePanel.add(openFileButton, BorderLayout.EAST);

        controlsPanel.add(filePanel, BorderLayout.CENTER);

        southPanel.add(controlsPanel);
        add(southPanel, BorderLayout.SOUTH);
        initListeners();
        initKeyBindings();
        isInternalUpdate = false;
    }

    private void initKeyBindings() {
        InputMap im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getRootPane().getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "togglePlayPause");
        am.put("togglePlayPause", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                togglePlayPause();
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "stepBack");
        am.put("stepBack", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                stepMs(-10);
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "stepForward");
        am.put("stepForward", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                stepMs(+10);
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.SHIFT_DOWN_MASK), "stepBackLarge");
        am.put("stepBackLarge", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                stepMs(-1000);
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.SHIFT_DOWN_MASK), "stepForwardLarge");
        am.put("stepForwardLarge", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                stepMs(+1000);
            }
        });

        im.put(KeyStroke.getKeyStroke("ctrl shift LEFT"), "stepBackXLarge");
        am.put("stepBackXLarge", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                stepMs(-10000);
            }
        });

        im.put(KeyStroke.getKeyStroke("ctrl shift RIGHT"), "stepForwardXLarge");
        am.put("stepForwardXLarge", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                stepMs(+10000);
            }
        });

        // Override the JTextField's built-in Ctrl+Shift+Left/Right word-selection bindings
        // so they don't consume the event before the window-level action fires.
        videoFile.getInputMap(JComponent.WHEN_FOCUSED)
                 .put(KeyStroke.getKeyStroke("ctrl shift LEFT"), "stepBackXLarge");
        videoFile.getInputMap(JComponent.WHEN_FOCUSED)
                 .put(KeyStroke.getKeyStroke("ctrl shift RIGHT"), "stepForwardXLarge");
        videoFile.getActionMap().put("stepBackXLarge", am.get("stepBackXLarge"));
        videoFile.getActionMap().put("stepForwardXLarge", am.get("stepForwardXLarge"));

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        am.put("close", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispatchEvent(new WindowEvent(YassVideoDialog.this, WindowEvent.WINDOW_CLOSING));
            }
        });
    }

    private void stepMs(int deltaMs) {
        if (videoDurationSeconds <= 0) {
            return;
        }
        // Use logicalPositionSeconds (updated immediately) rather than timeSlider.getValue(),
        // which may lag behind due to SwingUtilities.invokeLater queuing. This ensures rapid
        // key presses (e.g. holding Left/Right) accumulate correctly instead of all starting
        // from the same stale slider position.
        logicalPositionSeconds = Math.max(0, Math.min(logicalPositionSeconds + deltaMs / 1000.0, videoDurationSeconds));
        final double newVideoTime = logicalPositionSeconds;

        boolean wasPlaying = yassPlayer.isPlaying();
        if (wasPlaying) {
            yassPlayer.interruptMP3();
        }

        // Seek the video immediately so the frame updates right away
        seekVideo(newVideoTime);

        if (wasPlaying) {
            // Reset throttle so updateTime drives the video seek on the very first tick
            lastSeekUpdateNs = 0;
            double audioStartTime = Math.max(0, newVideoTime - (videoGapMs / 1000.0));
            long audioStartMs = (long) (audioStartTime * 1000);
            yassPlayer.setAudioEnabled(true);
            yassPlayer.setMIDIEnabled(false);
            yassPlayer.playSelection(audioStartMs * 1000, -1, null);
            // Defer play() briefly so the audio clock is established before JavaFX free-runs.
            // updateTime() will have fired at least once by then, putting the video at the
            // correct position before JavaFX takes over smooth playback.
            Timer timer = new Timer(150, e -> play());
            timer.setRepeats(false);
            timer.start();
        }

        int newSliderValue = (int) ((newVideoTime / videoDurationSeconds) * 1000);
        SwingUtilities.invokeLater(() -> {
            isSliderDragging = false;
            timeSlider.setValue(newSliderValue);
            timeLabel.setText(formatTime(newVideoTime));
        });
    }

    private void initListeners() {
        if (!isInternalUpdate) {
            return;
        }
        gapSpinner.getSpinner().addChangeListener(e -> {
            int ms = gapSpinner.getTime();
            if (ms != videoGapMs) {
                videoGapMs = ms;
                if (onGapChanged != null) {
                    onGapChanged.accept(ms);
                }
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancelCurrentVideoLoad();
                stop();
                Platform.runLater(() -> {
                    if (mediaPlayer != null) {
                        mediaPlayer.dispose();
                        mediaPlayer = null;
                    }
                    jfxPanel.setScene(null);
                });
                if (onDialogClosed != null) {
                    onDialogClosed.run();
                }
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                if (video != null && video.exists() && mediaPlayer == null) {
                    loadVideoFile(video.getAbsolutePath());
                }
            }
        });
        YassUtils.addChangeListener(videoFile, e -> {
            if (yassActions == null || yassActions.getTable() == null) {
                return;
            }
            String current = yassActions.getTable().getVideo();
            if (current.equalsIgnoreCase(videoFile.getText())) {
                return;
            }
            yassActions.getTable().setVideo(videoFile.getText());
        });
    }

    public void setOnFileChanged(Consumer<String> callback) {
        this.onFileChanged = callback;
    }

    public void setOnGapChanged(Consumer<Integer> callback) {
        this.onGapChanged = callback;
    }

    public void setOnDialogClosed(Runnable callback) {
        this.onDialogClosed = callback;
    }

    private void onVideoFieldEnter() {
        String text = videoFile.getText().trim();
        if (StringUtils.isEmpty(text)) {
            return;
        }

        // Only intercept when no video is loaded yet and the text looks like a YouTube URL
        if (video != null && video.exists()) {
            return;
        }

        Matcher matcher = YOUTUBE_ID_PATTERN.matcher(text);
        if (!matcher.find()) {
            return;
        }
        String youtubeId = matcher.group(1);

        // Check prerequisites
        YassProperties props = yassActions.getProperties();
        String ytdlpPath = props.getProperty("ytdlpPath");
        if (StringUtils.isEmpty(ytdlpPath) || !new File(ytdlpPath).exists()) {
            JOptionPane.showMessageDialog(this,
                    I18.get("video_dialog_ytdlp_not_configured"),
                    I18.get("video_dialog_ytdlp_error_title"),
                    JOptionPane.WARNING_MESSAGE);
            videoFile.setText("");
            return;
        }
        String videoCodec = props.getProperty(YtDlpPanel.YTDLP_VIDEO_CODEC);
        String videoResolution = props.getProperty(YtDlpPanel.YTDLP_VIDEO_RESOLUTION);
        if (StringUtils.isEmpty(videoCodec) && StringUtils.isEmpty(videoResolution)) {
            JOptionPane.showMessageDialog(this,
                    I18.get("video_dialog_ytdlp_no_video_settings"),
                    I18.get("video_dialog_ytdlp_error_title"),
                    JOptionPane.WARNING_MESSAGE);
            videoFile.setText("");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                I18.get("video_dialog_ytdlp_confirm"),
                I18.get("video_dialog_ytdlp_confirm_title"),
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            videoFile.setText("");
            return;
        }

        downloadVideoFromYouTube(text, youtubeId, ytdlpPath, videoCodec, videoResolution);
    }

    private void downloadVideoFromYouTube(String url, String youtubeId, String ytdlpPath,
                                          String videoCodec, String videoResolution) {
        YassTable table = yassActions.getTable();
        if (table == null) {
            return;
        }
        String songDir = table.getDir();
        if (StringUtils.isEmpty(songDir)) {
            return;
        }

        YtDlp.setExecutablePath(ytdlpPath);

        YtDlpRequest request = new YtDlpRequest(url.trim());
        request.setDirectory(songDir);
        request.setOption("output", "%(title)s.v_%(vcodec)s.%(ext)s");

        // Build video-only format string
        StringBuilder format = new StringBuilder("bestvideo");
        if (StringUtils.isNotEmpty(videoCodec)) {
            format.append(videoCodec);
        }
        if (StringUtils.isNotEmpty(videoResolution)) {
            format.append(videoResolution);
        }
        request.setOption("format", format.toString());
        request.setOption("no-audio");

        DownloadSplashFrame splash = new DownloadSplashFrame(this);

        final String[] downloadedPath = {null};

        YtDlpCallback callback = new YtDlpCallback() {
            @Override
            public void onProcessStarted(Process process, YtDlpRequest req) {}

            @Override
            public void onProcessFinished(int exitCode, String out, String err) {
                SwingUtilities.invokeLater(() -> {
                    if (exitCode != 0) {
                        splash.appendText(I18.get("video_dialog_ytdlp_failed"));
                        videoFile.setText("");
                        splash.enableCloseButton();
                        return;
                    }

                    // Resolve the downloaded file — fall back to scanning the song dir
                    // for any video file whose name contains the yt-dlp codec marker
                    File downloaded = downloadedPath[0] != null ? new File(downloadedPath[0]) : null;
                    if (downloaded == null || !downloaded.exists()) {
                        File[] candidates = new File(songDir).listFiles(f ->
                                !f.isDirectory() && f.getName().contains(".v_"));
                        if (candidates != null && candidates.length == 1) {
                            downloaded = candidates[0];
                            LOGGER.info("Resolved downloaded file via directory scan: " + downloaded.getAbsolutePath());
                        }
                    }

                    if (downloaded == null || !downloaded.exists()) {
                        splash.appendText(I18.get("video_dialog_ytdlp_failed"));
                        videoFile.setText("");
                        splash.enableCloseButton();
                        return;
                    }

                    String ext = downloaded.getName().contains(".")
                            ? downloaded.getName().substring(downloaded.getName().lastIndexOf('.')).toLowerCase()
                            : "";
                    // Rename to "Artist - Title.ext"
                    String artist = table.getArtist();
                    String title  = table.getTitle();
                    String targetName = YassSong.toFilename(artist + " - " + title + ext);
                    File target = new File(songDir, targetName);
                    if (downloaded.renameTo(target)) {
                        LOGGER.info("Renamed downloaded video to: " + target.getAbsolutePath());
                    } else {
                        // rename failed — use the downloaded file as-is
                        target = downloaded;
                        LOGGER.warning("Could not rename to Artist - Title, using: " + target.getName());
                    }

                    // Update #VIDEO tag and text field
                    table.setVideo(target.getName());
                    videoFile.setText(target.getName());

                    // Update #COMMENT tag with v=<youtubeId>
                    String entry = "v=" + youtubeId;
                    String existing = table.getCommentTag();
                    if (StringUtils.isEmpty(existing)) {
                        table.setCommentTag(entry);
                    } else if (!existing.contains(entry)) {
                        table.setCommentTag(existing + "," + entry);
                    }

                    // Load the video into the player — call loadVideoFile directly since
                    // the dialog is already visible and setVideo's isVisible() check is
                    // unreliable while the modal splash is blocking the window.
                    final String finalPath = target.getAbsolutePath();
                    video = target;
                    loadVideoFile(finalPath);

                    splash.appendText(I18.get("video_dialog_ytdlp_success"));
                    splash.enableCloseButton();
                });
            }

            @Override
            public void onOutput(String line) {
                if (line == null) return;
                SwingUtilities.invokeLater(() -> {
                    LOGGER.info(line);
                    splash.appendText(line);
                    // Capture the downloaded file path from yt-dlp output
                    if (line.contains("[download] Destination:")) {
                        String path = line.substring(line.indexOf(":") + 2).trim();
                        File f = new File(path);
                        if (!f.isAbsolute()) {
                            f = new File(songDir, path);
                        }
                        if (!path.endsWith(".part")) {
                            downloadedPath[0] = f.getAbsolutePath();
                        }
                    }
                });
            }

            @Override
            public void onProgressUpdate(float progress, long etaInSeconds) {
                SwingUtilities.invokeLater(() -> splash.updateProgress(progress, etaInSeconds));
            }
        };

        Thread.ofVirtual().name("yass-video-yt-download").start(() -> {
            try {
                YtDlp.executeAsync(request, callback);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "yt-dlp download failed", ex);
                SwingUtilities.invokeLater(() -> {
                    splash.appendText("Error: " + ex.getMessage());
                    splash.enableCloseButton();
                    videoFile.setText("");
                });
            }
        });

        splash.setVisible(true); // blocks (modal) until closed
    }

    private void chooseVideoFile() {
        JFileChooser chooser = new JFileChooser();
        if (videoFile != null && StringUtils.isNotEmpty(videoFile.getText())) {
            chooser.setCurrentDirectory(new File(yassActions.getTable().getDir()));
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
        video = new File(vd.replace('\\', '/'));
        if (StringUtils.isEmpty(vd) || !video.exists()) {
            return;
        }
        if (isVisible()) {
            loadVideoFile(vd);
        }
        // If not visible, componentShown will call loadVideoFile when the dialog is opened.
    }

    public void setVideoGap(int seconds) {
        if (this.videoGapMs != seconds) {
            this.videoGapMs = seconds;
            SwingUtilities.invokeLater(() -> gapSpinner.setTime(seconds));
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

        long now = System.nanoTime();

        if (videoDurationSeconds > 0 && (now - lastUiUpdateNs) >= UI_THROTTLE_NS) {
            lastUiUpdateNs = now;
            logicalPositionSeconds = targetVideoTime; // keep logical position in sync during playback
            final double finalVideoTime = targetVideoTime;
            SwingUtilities.invokeLater(() -> {
                int sliderValue = (int) ((finalVideoTime / videoDurationSeconds) * 1000);
                timeSlider.setValue(sliderValue);
                timeLabel.setText(formatTime(finalVideoTime));
            });
        }

        if ((now - lastSeekUpdateNs) >= SEEK_THROTTLE_NS) {
            lastSeekUpdateNs = now;
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
                            mediaPlayer.seek(Duration.seconds(finalTargetTime));
                        }
                    }
                } else {
                    // Avoid automatic reload loops after failed/cancelled video loads.
                    if (video != null
                            && video.exists()
                            && currentVideoLoadTask == null
                            && !video.getAbsolutePath().equalsIgnoreCase(StringUtils.defaultString(failedVideoPath))) {
                        loadVideoFile(video.getAbsolutePath());
                    }
                }
            });
        }
    }

    public void closeVideo() {
        cancelCurrentVideoLoad();
        stop();
        video = null;
        SwingUtilities.invokeLater(() -> videoFile.setText(""));
    }

    private void loadVideoFile(String path) {
        if (StringUtils.isEmpty(path)) {
            return;
        }
        File file = new File(path);
        if (!file.exists() || file.isDirectory()) {
            SwingUtilities.invokeLater(() -> videoFile.setText(""));
            return;
        }

        cancelCurrentVideoLoad();
        failedVideoPath = null;
        int generation = videoLoadGeneration.incrementAndGet();
        VideoLoadTask task = new VideoLoadTask(generation, file.getAbsolutePath());
        currentVideoLoadTask = task;

        // Tear down the existing player on the FX thread first, then hand off to a
        // background thread for the blocking ffprobe/ffmpeg work.
        Platform.runLater(() -> {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
                mediaPlayer = null;
            }
            jfxPanel.setScene(null);
        });

        SwingUtilities.invokeLater(() -> {
            videoFile.setText(file.getName());
            setTitle(I18.get("video_dialog_title") + " - " + file.getName());
            setVideoControlsEnabled(false);
            showVideoLoadDialog(task, I18.get("video_dialog_loading_open"));
        });

        Thread.ofVirtual().name("yass-video-loader").start(() -> {
            try {
                File mediaFile = prepareInitialPlaybackFile(file, task);
                task.ensureActive();
                Platform.runLater(() -> {
                    if (!task.isActive()) {
                        return;
                    }
                    initMediaPlayer(mediaFile, task);
                });
            } catch (CancellationException ignored) {
                LOGGER.fine("Cancelled video load for " + path);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error loading video: " + path, e);
                handleVideoLoadFailure(task, path, e);
            }
        });
    }

    private void initMediaPlayer(File file, VideoLoadTask task) {
        if (!task.isActive()) {
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
        SwingUtilities.invokeLater(() -> jfxPanel.setScene(scene));

        mediaPlayer.setOnReady(() -> {
            if (!task.isActive()) {
                return;
            }
            videoDurationSeconds = media.getDuration().toSeconds();
            failedVideoPath = null;
            finishVideoLoad(task);
            SwingUtilities.invokeLater(() -> {
                gapSpinner.setDuration((int) (videoDurationSeconds * 1000));
                timeSlider.setEnabled(true);
            });
        });
        mediaPlayer.setOnError(() -> handleMediaPlaybackFailure(task,
                                                                file,
                                                                new IOException(getMediaPlayerErrorMessage(mediaPlayer))));
        mediaPlayer.setOnHalted(() -> handleMediaPlaybackFailure(task,
                                                                 file,
                                                                 new IOException(I18.get("video_dialog_halted_error"))));
    }

    private File prepareInitialPlaybackFile(File file, VideoLoadTask task) throws IOException {
        File cacheFile = getCachedVideoFile(file.getAbsolutePath());
        task.setCacheFile(cacheFile);
        if (cacheFile.isFile() && cacheFile.length() > 0) {
            LOGGER.info("Reusing cached transcoded video " + cacheFile.getAbsolutePath());
            task.setCurrentPlaybackFile(cacheFile);
            task.setUsedCachedFile(true);
            updateVideoLoadDialog(task, I18.get("video_dialog_loading_cached"));
            return cacheFile;
        }
        task.setCurrentPlaybackFile(file);
        task.setUsedCachedFile(false);
        updateVideoLoadDialog(task, I18.get("video_dialog_loading_open"));
        return file;
    }

    private File transcodeToTempMp4(String inputPath, VideoLoadTask task) throws IOException {
        FFmpeg ffmpeg = FFMPEGLocator.getInstance().getFfmpeg();
        if (ffmpeg == null) {
            throw new IOException("FFmpeg not configured");
        }
        File tempDir = new File(YassPlayer.USER_PATH, "temp");
        Files.createDirectories(tempDir.toPath());
        File tempFile = new File(tempDir, "video-" + buildVideoTranscodeHash(inputPath) + ".mp4");

        ProcessBuilder builder = new ProcessBuilder(
                ffmpeg.getPath(),
                "-y",
                "-i", inputPath,
                "-an",
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-tune", "zerolatency",
                "-crf", "23",
                "-pix_fmt", "yuv420p",
                "-profile:v", "baseline",
                "-level", "3.0",
                "-movflags", "+faststart",
                tempFile.getAbsolutePath());
        CommandResult result = runProcess(builder,
                                          VIDEO_TRANSCODE_TIMEOUT_MS,
                                          "Timed out while transcoding video.",
                                          task);
        if (result.exitCode != 0 || !tempFile.exists() || tempFile.length() == 0) {
            throw new IOException("FFmpeg transcoding failed (" + result.exitCode + "): " + result.output);
        }
        return tempFile;
    }

    private CommandResult runProcess(ProcessBuilder builder,
                                     long timeoutMillis,
                                     String timeoutMessage,
                                     VideoLoadTask task) throws IOException {
        builder.redirectErrorStream(true);
        Process process = builder.start();
        task.registerProcess(process);

        StringBuilder output = new StringBuilder();
        Thread collector = Thread.ofVirtual().name("yass-video-process-output").start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (output) {
                        output.append(line).append(System.lineSeparator());
                    }
                }
            } catch (IOException ignored) {
                if (process.isAlive()) {
                    LOGGER.log(Level.FINE, "Could not read process output", ignored);
                }
            }
        });

        try {
            boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException(timeoutMessage);
            }
            try {
                collector.join(2_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while reading process output", e);
            }
            task.ensureActive();
            synchronized (output) {
                return new CommandResult(process.exitValue(), output.toString());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Interrupted while waiting for process", e);
        } finally {
            task.clearProcess(process);
        }
    }

    private void handleVideoLoadFailure(VideoLoadTask task, String path, Exception e) {
        if (e instanceof CancellationException || !task.isActive()) {
            finishVideoLoad(task);
            return;
        }

        failedVideoPath = new File(path).getAbsolutePath();
        Platform.runLater(() -> {
            if (mediaPlayer != null) {
                mediaPlayer.dispose();
                mediaPlayer = null;
            }
            jfxPanel.setScene(null);
        });
        finishVideoLoad(task);
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                                                                       I18.get("video_dialog_loading_failed") + " " + e.getMessage(),
                                                                       I18.get("video_dialog_loading_failed_title"),
                                                                       JOptionPane.ERROR_MESSAGE));
    }

    private void handleMediaPlaybackFailure(VideoLoadTask task, File attemptedFile, Exception e) {
        if (e instanceof CancellationException || !task.isActive()) {
            finishVideoLoad(task);
            return;
        }

        if (!task.hasTranscodeAttempted()) {
            task.markTranscodeAttempted();
            Platform.runLater(() -> {
                if (mediaPlayer != null) {
                    mediaPlayer.dispose();
                    mediaPlayer = null;
                }
                jfxPanel.setScene(null);
            });
            updateVideoLoadDialog(task, I18.get("video_dialog_loading_transcode"));
            Thread.ofVirtual().name("yass-video-transcode-fallback").start(() -> {
                try {
                    File transcoded = transcodeToTempMp4(task.path, task);
                    task.ensureActive();
                    task.setCurrentPlaybackFile(transcoded);
                    task.setUsedCachedFile(true);
                    Platform.runLater(() -> {
                        if (!task.isActive()) {
                            return;
                        }
                        initMediaPlayer(transcoded, task);
                    });
                } catch (CancellationException ignored) {
                    LOGGER.fine("Cancelled video transcode for " + task.path);
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Video transcode fallback failed: " + task.path, ex);
                    handleVideoLoadFailure(task, task.path, ex);
                }
            });
            return;
        }

        String attempted = attemptedFile != null ? attemptedFile.getAbsolutePath() : task.path;
        LOGGER.log(Level.SEVERE, "Video playback failed after transcode fallback: " + attempted, e);
        handleVideoLoadFailure(task, attempted, e);
    }

    private String getMediaPlayerErrorMessage(MediaPlayer player) {
        if (player == null || player.getError() == null || StringUtils.isBlank(player.getError().getMessage())) {
            return I18.get("video_dialog_loading_failed_unknown");
        }
        return player.getError().getMessage();
    }

    private void finishVideoLoad(VideoLoadTask task) {
        if (currentVideoLoadTask != task) {
            return;
        }
        currentVideoLoadTask = null;
        SwingUtilities.invokeLater(() -> {
            hideVideoLoadDialog(task);
            setVideoControlsEnabled(mediaPlayer != null);
        });
    }

    private void cancelCurrentVideoLoad() {
        VideoLoadTask task = currentVideoLoadTask;
        if (task != null) {
            task.cancel();
            failedVideoPath = task.path;
            currentVideoLoadTask = null;
        }
        SwingUtilities.invokeLater(() -> {
            hideVideoLoadDialog(task);
            setVideoControlsEnabled(mediaPlayer != null);
        });
    }

    private void setVideoControlsEnabled(boolean enabled) {
        playPauseButton.setEnabled(enabled);
        timeSlider.setEnabled(enabled && videoDurationSeconds > 0);
        gapSpinner.getSpinner().setEnabled(enabled);
    }

    private void showVideoLoadDialog(VideoLoadTask task, String message) {
        if (videoLoadDialog == null) {
            videoLoadDialog = new JDialog(this, I18.get("video_dialog_loading_title"), Dialog.ModalityType.MODELESS);
            videoLoadDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            videoLoadDialog.setLayout(new BorderLayout(10, 10));

            JPanel content = new JPanel(new BorderLayout(10, 10));
            content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            videoLoadStatusLabel = new JLabel(message);
            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            content.add(videoLoadStatusLabel, BorderLayout.NORTH);
            content.add(progressBar, BorderLayout.CENTER);

            videoLoadCancelButton = new JButton(I18.get("wizard_cancel"));
            videoLoadCancelButton.addActionListener(e -> cancelCurrentVideoLoad());
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            buttons.add(videoLoadCancelButton);
            content.add(buttons, BorderLayout.SOUTH);

            videoLoadDialog.add(content, BorderLayout.CENTER);
            videoLoadDialog.pack();
            videoLoadDialog.setResizable(false);
            videoLoadDialog.setLocationRelativeTo(this);
        }

        videoLoadDialog.setTitle(I18.get("video_dialog_loading_title"));
        videoLoadStatusLabel.setText(message);
        videoLoadCancelButton.setEnabled(true);
        videoLoadDialog.setLocationRelativeTo(this);
        if (!videoLoadDialog.isVisible() && task != null && task.isActive()) {
            videoLoadDialog.setVisible(true);
        }
    }

    private File getCachedVideoFile(String inputPath) throws IOException {
        File tempDir = new File(YassPlayer.USER_PATH, "temp");
        Files.createDirectories(tempDir.toPath());
        return new File(tempDir, "video-" + buildVideoTranscodeHash(inputPath) + ".mp4");
    }

    private void updateVideoLoadDialog(VideoLoadTask task, String message) {
        SwingUtilities.invokeLater(() -> {
            if (task.isActive() && videoLoadDialog != null && videoLoadStatusLabel != null) {
                videoLoadStatusLabel.setText(message);
                videoLoadDialog.pack();
                videoLoadDialog.setLocationRelativeTo(this);
            }
        });
    }

    private void hideVideoLoadDialog(VideoLoadTask task) {
        if (videoLoadDialog != null) {
            videoLoadDialog.setVisible(false);
        }
    }

    private static final class CommandResult {
        private final int exitCode;
        private final String output;

        private CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private static final class VideoLoadTask {
        private final int generation;
        private final String path;
        private volatile boolean cancelled;
        private volatile Process process;
        private volatile File cacheFile;
        private volatile File currentPlaybackFile;
        private volatile boolean usedCachedFile;
        private volatile boolean transcodeAttempted;

        private VideoLoadTask(int generation, String path) {
            this.generation = generation;
            this.path = path;
        }

        private boolean isActive() {
            return !cancelled;
        }

        private void ensureActive() {
            if (cancelled) {
                throw new CancellationException("Video load was cancelled for " + path + " (" + generation + ")");
            }
        }

        private void registerProcess(Process process) {
            this.process = process;
            if (cancelled && process != null) {
                process.destroyForcibly();
            }
        }

        private void clearProcess(Process process) {
            if (this.process == process) {
                this.process = null;
            }
        }

        private void cancel() {
            cancelled = true;
            Process running = process;
            if (running != null) {
                running.destroyForcibly();
            }
        }

        private boolean hasTranscodeAttempted() {
            return transcodeAttempted;
        }

        private void markTranscodeAttempted() {
            transcodeAttempted = true;
        }

        private void setCacheFile(File cacheFile) {
            this.cacheFile = cacheFile;
        }

        private void setCurrentPlaybackFile(File currentPlaybackFile) {
            this.currentPlaybackFile = currentPlaybackFile;
        }

        private void setUsedCachedFile(boolean usedCachedFile) {
            this.usedCachedFile = usedCachedFile;
        }
    }

    private String buildVideoTranscodeHash(String inputPath) {
        File sourceFile = new File(inputPath);
        String fingerprint = sourceFile.getAbsolutePath() + "|" + sourceFile.length() + "|" + sourceFile.lastModified();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(fingerprint.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(bytes.length, 8); i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(fingerprint.hashCode());
        }
    }

    private void togglePlayPause() {
        if (yassPlayer.isPlaying()) {
            yassPlayer.interruptMP3();
            pause();
        } else {
            double sliderPos = timeSlider.getValue() / 1000.0;
            double virtualVideoTime = sliderPos * videoDurationSeconds;

            double audioStartTime = virtualVideoTime - (videoGapMs / 1000.0);
            audioStartTime = Math.max(0, audioStartTime);

            long audioStartMs = (long) (audioStartTime * 1000);

            double seekVideoTime = getSeekableVideoTime(virtualVideoTime);
            yassPlayer.setAudioEnabled(true);
            yassPlayer.setMIDIEnabled(false);
            yassPlayer.playSelection(audioStartMs * 1000, -1, null);

            seekVideo(seekVideoTime);
            play();
        }
    }

    private double getVirtualVideoTime(double audioSeconds) {
        return audioSeconds + (videoGapMs / 1000.0);
    }

    private double getSeekableVideoTime(double virtualVideoTime) {
        return Math.max(0, virtualVideoTime);
    }

    public int getVideoGapMs() {
        return videoGapMs;
    }

    public void play() {
        Platform.runLater(() -> {
            if (mediaPlayer != null) {
                mediaPlayer.play();
                SwingUtilities.invokeLater(() -> playPauseButton.setIcon(yassActions.getIcon("pause24Icon")));
            } else {
                if (video != null && video.exists()) {
                    loadVideoFile(video.getAbsolutePath());
                }
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

    /** Returns the current slider position as milliseconds. */
    public int getTime() {
        if (timeSlider == null || videoDurationSeconds <= 0) {
            return 0;
        }
        double fraction = timeSlider.getValue() / 1000.0;
        return (int) (fraction * videoDurationSeconds * 1000);
    }

    public void seekVideo(double seconds) {
        Platform.runLater(() -> {
            if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(seconds));
            }
        });
    }
}
