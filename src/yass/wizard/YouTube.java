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

import com.jfposton.ytdlp.YtDlp;
import com.jfposton.ytdlp.YtDlpCallback;
import com.jfposton.ytdlp.YtDlpRequest;
import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.analysis.BpmDetector;
import yass.options.YtDlpPanel;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.Serial;
import java.net.URL;

import static yass.wizard.CreateSongWizard.LOGGER;

public class YouTube extends JPanel {
    /**
     * Description of the Field
     */
    public final static String ID = "youtube";
    @Serial
    private static final long serialVersionUID = 1L;
    private CreateSongWizard wizard;
    private JTextField youTubeUrl = null;

    public YouTube(CreateSongWizard wizard) {
        this.wizard = wizard;
        JLabel iconLabel = new JLabel();
        setLayout(new BorderLayout());
        iconLabel.setIcon(new ImageIcon(this.getClass().getResource("clouds.jpg")));
        add("West", iconLabel);
        add("Center", getContentPanel());
    }

    public String getYouTubeUrl() {
        if (youTubeUrl != null) {
            return youTubeUrl.getText();
        }
        return null;
    }

    public void setYouTubeUrl(String value) {
        youTubeUrl.setText(value);
        wizard.setValue("youtube", value);
    }

    /**
     * Gets the contentPanel attribute of the Melody object
     *
     * @return The contentPanel value
     */
    private JPanel getContentPanel() {
        JPanel content = new JPanel(new BorderLayout());
        JTextPane txt = new JTextPane();
        HTMLDocument doc = (HTMLDocument) txt.getEditorKitForContentType("text/html").createDefaultDocument();
        doc.setAsynchronousLoadPriority(-1);
        txt.setDocument(doc);
        URL url = I18.getResource("create_youtube.html");
        try {
            txt.setPage(url);
        } catch (Exception ignored) {
        }
        txt.setEditable(false);
        content.add("Center", new JScrollPane(txt));
        JPanel filePanel = new JPanel(new BorderLayout());
        filePanel.add("Center", youTubeUrl = new JTextField());
        youTubeUrl.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent evt) {
                        setYouTubeUrl(youTubeUrl.getText());
                    }
                });

        content.add("South", filePanel);
        return content;
    }

    public void downloadFromYouTube() {
        if (StringUtils.isEmpty(getYouTubeUrl())) {
            return;
        }
        final DownloadSplashFrame splash = new DownloadSplashFrame(SwingUtilities.getWindowAncestor(this));

        new Thread(() -> {
            try {
                YtDlpRequest request = buildYtDlpRequest();
                YtDlpCallback callback = createYtDlpCallback(splash, request.getDirectory());
                YtDlp.executeAsync(request, callback);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    splash.appendText("An error occurred: " + ex.getMessage());
                    splash.enableCloseButton();
                });
            }
        }, "yt-dlp-downloader").start();

        splash.setVisible(true);
    }

    private YtDlpRequest buildYtDlpRequest() {
        File tempDir = new File(wizard.getProperty("temp-dir"));
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        YtDlpRequest request = new YtDlpRequest(getYouTubeUrl().trim());
        request.setDirectory(tempDir.getAbsolutePath());
        request.setOption("output", "%(title)s.v_%(vcodec)s.a_%(acodec)s.%(ext)s");
        request.setOption("format", buildVideoFormatString());

        String audioFormat = wizard.getProperty(YtDlpPanel.YTDLP_AUDIO_FORMAT);
        if (StringUtils.isNotEmpty(audioFormat)) {
            request.setOption("extract-audio");
            request.setOption("audio-format", audioFormat);
        }

        request.setOption("write-subs");
        if (wizard.getLyrics() != null && StringUtils.isNotEmpty(wizard.getProperty("language"))) {
            request.setOption("sub-lang", wizard.getProperty("language"));
        }
        request.setOption("write-auto-subs");
        request.setOption("ignore-errors");

        return request;
    }

    private String buildVideoFormatString() {
        String videoCodec = wizard.getProperty(YtDlpPanel.YTDLP_VIDEO_CODEC);
        String videoResolution = wizard.getProperty(YtDlpPanel.YTDLP_VIDEO_RESOLUTION);
        StringBuilder ytDlpArgs = new StringBuilder("bestvideo");
        if (StringUtils.isNotEmpty(videoCodec)) {
            ytDlpArgs.append(videoCodec);
        }
        if (StringUtils.isNotEmpty(videoResolution)) {
            ytDlpArgs.append(videoResolution);
        }
        ytDlpArgs.append(",bestaudio");
        String audioBitrate = wizard.getProperty(YtDlpPanel.YTDLP_AUDIO_BITRATE);
        if (StringUtils.isNotEmpty(audioBitrate)) {
            ytDlpArgs.append("[abr<=").append(audioBitrate.replace("k", "000")).append("]");
        }
        return ytDlpArgs.toString();
    }

    private YtDlpCallback createYtDlpCallback(DownloadSplashFrame splash, String tempDirPath) {
        File tempDir = new File(tempDirPath);
        return new YtDlpCallback() {
            @Override
            public void onProcessStarted(Process process, YtDlpRequest request) {
                // No action needed here
            }

            @Override
            public void onProcessFinished(int exitCode, String out, String err) {
                SwingUtilities.invokeLater(() -> {
                    if (exitCode == 0) {
                        handleDownloadSuccess(splash);
                    } else {
                        handleDownloadFailure(exitCode, err, splash);
                    }
                    splash.enableCloseButton();
                });
            }

            @Override
            public void onOutput(String line) {
                if (line == null) {
                    return;
                }
                // This is a known, harmless warning when yt-dlp tries to extract audio from a video-only file.
                // We can safely ignore it and not show it to the user to avoid confusion.
                if (line.contains("unable to obtain file audio codec with ffprobe")) {
                    LOGGER.info("Ignoring known yt-dlp/ffmpeg warning: " + line);
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    LOGGER.info(line);
                    splash.appendText(line);
                    parseYtDlpOutput(line, tempDir);
                });
            }

            @Override
            public void onProgressUpdate(float progress, long etaInSeconds) {
                SwingUtilities.invokeLater(() -> splash.updateProgress(progress, etaInSeconds));
            }
        };
    }

    private void handleDownloadSuccess(DownloadSplashFrame splash) {
        LOGGER.info("yt-dlp process finished successfully.");
        renameDownloadedFiles();

        String autoGenerated = wizard.getValue("subtitles-auto-generated");
        if ("true".equals(autoGenerated)) {
            splash.appendText("Using auto-generated subtitles");
        } else if ("false".equals(autoGenerated)) {
            splash.appendText("Using manual subtitles");
        }

        detectBpm(splash);
    }

    private void handleDownloadFailure(int exitCode, String error, DownloadSplashFrame splash) {
        LOGGER.severe("yt-dlp process failed with exit code: " + exitCode);
        if (StringUtils.isNotEmpty(error)) {
            LOGGER.severe("yt-dlp error output:\n" + error);
            // Display the error to the user as well.
            splash.appendText("Download failed. Error: " + error.trim());
        } else {
            splash.appendText("Download failed. Please check the log for errors.");
        }
    }

    private void detectBpm(DownloadSplashFrame splash) {
        String audioPath = wizard.getValue("filename");
        if (StringUtils.isEmpty(audioPath)) {
            return;
        }

        String currentBpmStr = wizard.getValue("bpm");
        boolean shouldDetect = true;
        if (StringUtils.isNotEmpty(currentBpmStr)) {
            try {
                if (Float.parseFloat(currentBpmStr.replace(',', '.')) > 0) {
                    shouldDetect = false;
                    LOGGER.info("BPM is already populated (" + currentBpmStr + "), skipping detection.");
                }
            } catch (NumberFormatException ignored) {
                // Invalid format, proceed with detection.
            }
        }

        if (shouldDetect) {
            LOGGER.info("Found audio file for BPM detection: " + audioPath);
            splash.appendText("Analyzing audio to detect BPM...");
            BpmDetector.detectBpm(audioPath, bpm -> {
                String bpmString = String.format(java.util.Locale.US, "%.2f", bpm);
                wizard.setValue("bpm", bpmString);
                splash.appendText("Successfully detected BPM: " + bpmString);
            }, wizard.getYassProperties());
        }
    }

    private void parseYtDlpOutput(String line, File tempDir) {
        if (line.contains("[ExtractAudio] Destination:")) {
            String relativePath = line.substring(line.indexOf(":") + 2).trim();
            wizard.setValue("filename", new File(tempDir, relativePath).getAbsolutePath());
        } else if (line.contains("[info] Writing video automatic subtitles to:")) {
            String relativePath = line.substring(line.indexOf(":") + 2).trim();
            wizard.setValue("subtitle", new File(tempDir, relativePath).getAbsolutePath());
            wizard.setValue("subtitles-auto-generated", "true");
        } else if (line.contains("[info] Writing video subtitles to:")) {
            String relativePath = line.substring(line.indexOf(":") + 2).trim();
            wizard.setValue("subtitle", new File(tempDir, relativePath).getAbsolutePath());
            wizard.setValue("subtitles-auto-generated", "false");
        } else if (line.contains("[download] Destination:")) {
            String relativePath = line.substring(line.indexOf(":") + 2).trim();
            File downloadedFile = new File(tempDir, relativePath);
            if (relativePath.contains(".a_none.")) {
                // This is the video-only file.
                wizard.setValue("video", downloadedFile.getAbsolutePath());
            } else if (relativePath.contains(".v_none.")) {
                // This is the audio-only file. If we are not extracting audio (i.e., not converting format),
                // this is our final audio file.
                String audioFormat = wizard.getProperty(YtDlpPanel.YTDLP_AUDIO_FORMAT);
                if (StringUtils.isEmpty(audioFormat)) {
                    wizard.setValue("filename", downloadedFile.getAbsolutePath());
                }
            } else if (!relativePath.endsWith(".part")) {
                // This could be a pre-muxed file if separate streams weren't available.
                // Set it as the video file if one hasn't been found yet.
                if (StringUtils.isEmpty(wizard.getValue("video"))) {
                    wizard.setValue("video", downloadedFile.getAbsolutePath());
                }
            }
        }
    }

    private void renameDownloadedFiles() {
        // We need to read the values before we start modifying them, as renameFile updates them.
        String videoPath = wizard.getValue("video");
        String audioPath = wizard.getValue("filename");
        String subtitlePath = wizard.getValue("subtitle");

        renameFile(videoPath);
        renameFile(audioPath);
        renameFile(subtitlePath);
    }

    private void renameFile(String oldPath) {
        if (StringUtils.isEmpty(oldPath)) {
            return;
        }
        File oldFile = new File(oldPath);
        if (!oldFile.exists()) {
            LOGGER.warning("File to rename does not exist: " + oldPath);
            return;
        }

        String filename = oldFile.getName();
        // This regex removes the ".v_...a_..." part from the filename.
        // The non-greedy `.*?` is used to correctly handle video codec names that may contain dots.
        String newFilename = filename.replaceAll("\\.v_.*?\\.a_.*?\\.", ".");

        if (filename.equals(newFilename)) {
            return; // No change needed
        }

        File newFile = new File(oldFile.getParent(), newFilename);

        if (oldFile.renameTo(newFile)) {
            LOGGER.info("Renamed " + oldPath + " to " + newFile.getAbsolutePath());
            // Update the wizard with the new path
            if (oldPath.equals(wizard.getValue("video"))) wizard.setValue("video", newFile.getAbsolutePath());
            if (oldPath.equals(wizard.getValue("filename"))) wizard.setValue("filename", newFile.getAbsolutePath());
            if (oldPath.equals(wizard.getValue("subtitle"))) wizard.setValue("subtitle", newFile.getAbsolutePath());
        } else {
            LOGGER.warning("Could not rename file " + oldPath + " to " + newFilename);
        }
    }
}
