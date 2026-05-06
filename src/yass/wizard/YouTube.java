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

import static yass.wizard.CreateSongWizard.LOGGER;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serial;
import java.net.URL;
import java.text.MessageFormat;
import java.nio.file.Files;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.html.HTMLDocument;

import org.apache.commons.lang3.StringUtils;

import com.jfposton.ytdlp.YtDlp;
import com.jfposton.ytdlp.YtDlpCallback;
import com.jfposton.ytdlp.YtDlpException;
import com.jfposton.ytdlp.YtDlpRequest;

import yass.I18;
import yass.YassProperties;
import yass.YtDlpSupport;
import yass.analysis.BpmDetector;
import yass.options.YtDlpPanel;

public class YouTube extends JPanel {
    /**
     * Description of the Field
     */
    public final static String ID = "youtube";
    @Serial
    private static final long serialVersionUID = 1L;
    private CreateSongWizard wizard;
    private JTextField youTubeUrl = null;
    private int fallbackLevel = 0; // 0=Strict, 1=Relaxed Codec, 2=Best
    private File discoveredManualSubtitleFile = null;
    private File discoveredAutoSubtitleFile = null;
    private TempWizardAssets pendingDownloadAssets = new TempWizardAssets();
    private DownloadReuseMode pendingDownloadMode = DownloadReuseMode.REDOWNLOAD_ALL;

    private enum DownloadReuseMode {
        REUSE_EXISTING,
        DOWNLOAD_MISSING_ONLY,
        REDOWNLOAD_ALL
    }

    private enum SubtitleKind {
        MANUAL,
        AUTO
    }

    private static final class TempWizardAssets {
        private File audioFile;
        private File videoFile;
        private File manualSubtitleFile;
        private File autoSubtitleFile;
        private String derivedArtist;
        private String derivedTitle;

        private boolean hasAny() {
            return audioFile != null || videoFile != null || manualSubtitleFile != null || autoSubtitleFile != null;
        }

        private boolean hasAudio() {
            return audioFile != null && audioFile.isFile();
        }

        private boolean hasVideo() {
            return videoFile != null && videoFile.isFile();
        }

        private boolean hasAnySubtitle() {
            return (manualSubtitleFile != null && manualSubtitleFile.isFile())
                    || (autoSubtitleFile != null && autoSubtitleFile.isFile());
        }
    }

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
        discoveredManualSubtitleFile = null;
        discoveredAutoSubtitleFile = null;
        String youTubeId = extractYouTubeId(getYouTubeUrl());
        TempWizardAssets existingAssets = normalizeTempAssetNames(detectExistingAssets(youTubeId), youTubeId);
        applyStoredMetadata(youTubeId, existingAssets);
        if (existingAssets.hasAny()) {
            DownloadReuseMode mode = promptReuseMode(existingAssets);
            if (mode == null) {
                return;
            }
            if (mode == DownloadReuseMode.REUSE_EXISTING) {
                applyAssetsToWizard(existingAssets);
                detectBpmForAudio(existingAssets.audioFile, null);
                return;
            }
            if (mode == DownloadReuseMode.DOWNLOAD_MISSING_ONLY
                    && existingAssets.hasAudio()
                    && existingAssets.hasVideo()
                    && existingAssets.hasAnySubtitle()) {
                applyAssetsToWizard(existingAssets);
                detectBpmForAudio(existingAssets.audioFile, null);
                return;
            }
            if (mode == DownloadReuseMode.REDOWNLOAD_ALL) {
                deleteExistingAssets(existingAssets);
                existingAssets = new TempWizardAssets();
            }
            fallbackLevel = 0;
            startDownload(new DownloadSplashFrame(SwingUtilities.getWindowAncestor(this)), existingAssets, mode);
            return;
        }
        fallbackLevel = 0;
        startDownload(new DownloadSplashFrame(SwingUtilities.getWindowAncestor(this)), existingAssets, DownloadReuseMode.REDOWNLOAD_ALL);
    }

    static String extractYouTubeId(String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        // Handles: youtube.com/watch?v=ID, youtu.be/ID, youtube.com/shorts/ID
        Matcher m = Pattern.compile("(?:v=|youtu\\.be/|/shorts/)([A-Za-z0-9_-]{11})").matcher(url);
        return m.find() ? m.group(1) : null;
    }
    private void startDownload(DownloadSplashFrame splash, TempWizardAssets existingAssets, DownloadReuseMode mode) {
        if (!ensureYtDlpExecutableAvailable()) {
            return;
        }
        pendingDownloadAssets = existingAssets != null ? existingAssets : new TempWizardAssets();
        pendingDownloadMode = mode != null ? mode : DownloadReuseMode.REDOWNLOAD_ALL;
        new Thread(() -> {
            try {
                YtDlpRequest request = buildYtDlpRequest(pendingDownloadAssets, pendingDownloadMode);
                if (request == null) {
                    SwingUtilities.invokeLater(() -> {
                        applyAssetsToWizard(normalizeTempAssetNames(detectExistingAssets(extractYouTubeId(getYouTubeUrl())),
                                extractYouTubeId(getYouTubeUrl())));
                        splash.enableCloseButton();
                        splash.dispose();
                    });
                    return;
                }
                YtDlpCallback callback = createYtDlpCallback(splash, request.getDirectory());
                executeYtDlpWithFallback(request, callback);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    splash.appendText("An error occurred: " + ex.getMessage());
                    splash.enableCloseButton();
                });
            }
        }, "yt-dlp-downloader").start();

        if (!splash.isVisible()) {
            splash.setVisible(true);
        }
    }

    private boolean ensureYtDlpExecutableAvailable() {
        try {
            YtDlpSupport.ensureExecutableAvailable(wizard.getYassProperties());
            String configuredPath = StringUtils.trimToNull(wizard.getYassProperties().getProperty("ytdlpPath"));
            if (StringUtils.isNotBlank(configuredPath)) {
                LOGGER.info("Using configured yt-dlp executable: " + configuredPath);
            }
            return true;
        } catch (IOException ex) {
            String configuredPath = StringUtils.trimToNull(wizard.getYassProperties().getProperty("ytdlpPath"));
            String message = StringUtils.isNotBlank(configuredPath)
                    ? MessageFormat.format(I18.get("create_youtube_ytdlp_configured_missing"), configuredPath)
                    : I18.get("create_youtube_ytdlp_not_available");
            LOGGER.warning("yt-dlp is not executable in current environment: " + ex.getMessage());
            JOptionPane.showMessageDialog(
                    this,
                    message,
                    I18.get("create_youtube_ytdlp_error_title"),
                    JOptionPane.WARNING_MESSAGE);
        }
        return false;
    }

    private void executeYtDlpWithFallback(YtDlpRequest request, YtDlpCallback callback) throws Exception {
        try {
            YtDlp.executeAsync(request, callback);
        } catch (Exception ex) {
            throw ex;
        }
    }

    private YtDlpRequest buildYtDlpRequest(TempWizardAssets existingAssets, DownloadReuseMode mode) {
        File tempDir = new File(wizard.getProperty("temp-dir"));
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        String youTubeId = extractYouTubeId(getYouTubeUrl());
        boolean needAudio = mode != DownloadReuseMode.DOWNLOAD_MISSING_ONLY || !existingAssets.hasAudio();
        boolean needVideo = mode != DownloadReuseMode.DOWNLOAD_MISSING_ONLY || !existingAssets.hasVideo();
        boolean needSubtitles = mode != DownloadReuseMode.DOWNLOAD_MISSING_ONLY || !existingAssets.hasAnySubtitle();
        if (!needAudio && !needVideo && !needSubtitles) {
            return null;
        }

        YtDlpRequest request = new YtDlpRequest(getYouTubeUrl().trim());
        request.setDirectory(tempDir.getAbsolutePath());
        request.setOption("output", buildTempOutputTemplate(youTubeId));

        if (needAudio && needVideo) {
            if (fallbackLevel == 2) {
                LOGGER.info("Using fallback format 'best'");
                request.setOption("format", "best");
            } else {
                request.setOption("format", YtDlpSupport.buildCombinedVideoFormatString(wizard.getYassProperties(),
                                                                                        fallbackLevel == 0));
            }
        } else if (needAudio) {
            request.setOption("format", "bestaudio");
        } else if (needVideo) {
            request.setOption("format", YtDlpSupport.buildVideoOnlyFormatString(wizard.getYassProperties(),
                                                                                fallbackLevel == 0));
        } else {
            request.setOption("skip-download");
        }

        String audioFormat = wizard.getProperty(YtDlpPanel.YTDLP_AUDIO_FORMAT);
        if (needAudio && StringUtils.isNotEmpty(audioFormat)) {
            YtDlpSupport.applyAudioExtractionOptions(request, wizard.getYassProperties());
        }

        YtDlpSupport.applyCommonOptions(request, wizard.getYassProperties());

        if (needSubtitles) {
            request.setOption("write-subs");
            if (wizard.getLyrics() != null && StringUtils.isNotEmpty(wizard.getProperty("language"))) {
                request.setOption("sub-lang", wizard.getProperty("language"));
            }
            request.setOption("write-auto-subs");
        }
        request.setOption("ignore-errors");

        return request;
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
                        splash.enableCloseButton();
                    } else {
                        handleDownloadFailure(exitCode, err, splash);
                    }
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

    private static final Set<String> AUDIO_EXTENSIONS =
            Set.of("opus", "mp3", "aac", "ogg", "flac", "m4a", "wav");

    private void handleDownloadSuccess(DownloadSplashFrame splash) {
        LOGGER.info("yt-dlp process finished successfully.");
        String youTubeId = extractYouTubeId(getYouTubeUrl());
        TempWizardAssets assets = normalizeTempAssetNames(detectExistingAssets(youTubeId), youTubeId);
        applyAssetsToWizard(assets);

        String autoGenerated = wizard.getValue("subtitles-auto-generated");
        if ("true".equals(autoGenerated)) {
            splash.appendText("Using auto-generated subtitles");
        } else if ("false".equals(autoGenerated)) {
            splash.appendText("Using manual subtitles");
        }

        detectBpmForAudio(assets.audioFile, splash);
    }

    private void handleDownloadFailure(int exitCode, String error, DownloadSplashFrame splash) {
        if (fallbackLevel < 2 && error != null && error.contains("Requested format is not available")) {
            fallbackLevel++;
            String msg = (fallbackLevel == 1)
                ? "Requested format not available. Retrying with relaxed codec constraints..."
                : "Requested format not available. Retrying with fallback format 'best'...";

            LOGGER.warning(msg);
            splash.appendText(msg);
            startDownload(splash, pendingDownloadAssets, pendingDownloadMode);
            return;
        }

        // If ffmpeg was not found for merging but the media files were already downloaded,
        // treat this as a success — the files exist and the merge error is a false failure.
        if (error != null && error.contains("ffprobe and ffmpeg not found")
                && detectExistingAssets(extractYouTubeId(getYouTubeUrl())).hasAny()) {
            LOGGER.warning("ffmpeg not found for merging, but media files already exist. Treating as success.");
            handleDownloadSuccess(splash);
            splash.enableCloseButton();
            return;
        }

        LOGGER.severe("yt-dlp process failed with exit code: " + exitCode);
        if (StringUtils.isNotEmpty(error)) {
            LOGGER.severe("yt-dlp error output:\n" + error);
            // Display the error to the user as well.
            splash.appendText("Download failed. Error: " + error.trim());
        } else {
            splash.appendText("Download failed. Please check the log for errors.");
        }
        splash.enableCloseButton();
    }

    private void detectBpmForAudio(File audioFile, DownloadSplashFrame splash) {
        if (audioFile == null || !audioFile.isFile()) {
            return;
        }
        String audioPath = audioFile.getAbsolutePath();

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
            if (splash != null) {
                splash.appendText("Analyzing audio to detect BPM...");
            }
            BpmDetector.detectBpm(audioPath, bpm -> {
                String bpmString = String.format(java.util.Locale.US, "%.2f", bpm);
                wizard.setValue("bpm", bpmString);
                if (splash != null) {
                    splash.appendText("Successfully detected BPM: " + bpmString);
                }
            }, wizard.getYassProperties());
        }
    }

    private void parseYtDlpOutput(String line, File tempDir) {
        if (line.contains("[ExtractAudio] Destination:")) {
            String relativePath = line.substring(line.indexOf(":") + 2).trim();
            maybeSeedArtistAndTitleFromPath(relativePath);
            wizard.setValue("filename", new File(tempDir, relativePath).getAbsolutePath());
        } else if (line.contains("[info] Writing video automatic subtitles to:")) {
            String relativePath = line.substring(line.indexOf(":") + 2).trim();
            maybeSeedArtistAndTitleFromPath(relativePath);
            discoveredAutoSubtitleFile = new File(tempDir, relativePath);
            if (discoveredManualSubtitleFile == null) {
                wizard.setValue("subtitle", discoveredAutoSubtitleFile.getAbsolutePath());
                wizard.setValue("subtitles-auto-generated", "true");
            }
        } else if (line.contains("[info] Writing video subtitles to:")) {
            String relativePath = line.substring(line.indexOf(":") + 2).trim();
            maybeSeedArtistAndTitleFromPath(relativePath);
            discoveredManualSubtitleFile = new File(tempDir, relativePath);
            wizard.setValue("subtitle", discoveredManualSubtitleFile.getAbsolutePath());
            wizard.setValue("subtitles-auto-generated", "false");
        } else if (line.contains("[download] Destination:")) {
            String relativePath = line.substring(line.indexOf(":") + 2).trim();
            maybeSeedArtistAndTitleFromPath(relativePath);
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

    private TempWizardAssets detectExistingAssets(String youTubeId) {
        TempWizardAssets assets = new TempWizardAssets();
        if (StringUtils.isBlank(youTubeId)) {
            return assets;
        }
        File tempDir = new File(wizard.getProperty("temp-dir"));
        if (!tempDir.isDirectory()) {
            return assets;
        }
        File[] matches = tempDir.listFiles(f -> f.isFile() && !f.getName().endsWith(".part") && f.getName().contains(youTubeId));
        if (matches == null) {
            return assets;
        }
        for (File file : matches) {
            maybeDeriveArtistAndTitleFromFile(file, assets, youTubeId);
            if (isSubtitleFile(file)) {
                if (isAutoSubtitleFile(file)) {
                    if (assets.autoSubtitleFile == null) {
                        assets.autoSubtitleFile = file;
                    }
                } else if (assets.manualSubtitleFile == null) {
                    assets.manualSubtitleFile = file;
                }
                continue;
            }
            if (isAudioFile(file)) {
                if (assets.audioFile == null || preferAudioCandidate(file, assets.audioFile)) {
                    assets.audioFile = file;
                }
                continue;
            }
            if (isVideoFile(file)) {
                if (assets.videoFile == null || preferVideoCandidate(file, assets.videoFile)) {
                    assets.videoFile = file;
                }
            }
        }
        return assets;
    }

    private TempWizardAssets normalizeTempAssetNames(TempWizardAssets assets, String youTubeId) {
        if (assets == null || StringUtils.isBlank(youTubeId)) {
            return assets;
        }
        TempWizardAssets normalized = new TempWizardAssets();
        normalized.audioFile = renameToCanonicalTempAssetName(assets.audioFile, youTubeId, assets.derivedArtist, assets.derivedTitle, null);
        normalized.videoFile = renameToCanonicalTempAssetName(assets.videoFile, youTubeId, assets.derivedArtist, assets.derivedTitle, null);
        normalized.manualSubtitleFile = renameToCanonicalTempAssetName(assets.manualSubtitleFile, youTubeId, assets.derivedArtist, assets.derivedTitle, "subtitles");
        normalized.autoSubtitleFile = renameToCanonicalTempAssetName(assets.autoSubtitleFile, youTubeId, assets.derivedArtist, assets.derivedTitle, "subtitles-auto");
        normalized.derivedArtist = assets.derivedArtist;
        normalized.derivedTitle = assets.derivedTitle;
        return normalized;
    }

    private String buildTempOutputTemplate(String youTubeId) {
        if (StringUtils.isBlank(youTubeId)) {
            return "%(uploader)s - %(title)s.%(ext)s";
        }
        return youTubeId + " - %(uploader)s - %(title)s.%(ext)s";
    }

    private File renameToCanonicalTempAssetName(File sourceFile, String youTubeId, String artist, String title, String variantSuffix) {
        if (sourceFile == null || !sourceFile.isFile()) {
            return null;
        }
        String extension = "";
        String name = sourceFile.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            extension = name.substring(dot);
        }
        File canonicalFile = new File(sourceFile.getParentFile(),
                buildCanonicalTempFileName(youTubeId, artist, title, variantSuffix, extension.toLowerCase()));
        if (sourceFile.equals(canonicalFile)) {
            return sourceFile;
        }
        if (canonicalFile.exists() && canonicalFile.isFile()) {
            return canonicalFile;
        }
        if (sourceFile.renameTo(canonicalFile)) {
            LOGGER.info("Normalized temp asset " + sourceFile.getAbsolutePath() + " -> " + canonicalFile.getAbsolutePath());
            return canonicalFile;
        }
        LOGGER.warning("Could not normalize temp asset name for " + sourceFile.getAbsolutePath());
        return sourceFile;
    }

    private String buildCanonicalTempFileName(String youTubeId, String artist, String title, String variantSuffix, String extension) {
        StringBuilder name = new StringBuilder();
        if (StringUtils.isNotBlank(youTubeId)) {
            name.append(youTubeId);
        } else {
            name.append("youtube");
        }
        if (StringUtils.isNotBlank(artist) || StringUtils.isNotBlank(title)) {
            name.append(" - ");
            if (StringUtils.isNotBlank(artist)) {
                name.append(sanitizeTempNamePart(artist));
            } else {
                name.append("Unknown Artist");
            }
            if (StringUtils.isNotBlank(title)) {
                name.append(" - ").append(sanitizeTempNamePart(title));
            }
        }
        if (StringUtils.isNotBlank(variantSuffix)) {
            name.append(" - ").append(variantSuffix);
        }
        name.append(extension);
        return name.toString();
    }

    private String sanitizeTempNamePart(String value) {
        String sanitized = StringUtils.defaultString(value)
                .replaceAll("[\\\\/:*?\"<>|]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return StringUtils.defaultIfBlank(sanitized, "Unknown");
    }

    private void applyAssetsToWizard(TempWizardAssets assets) {
        if (assets == null) {
            return;
        }
        wizard.setValue("filename", assets.hasAudio() ? assets.audioFile.getAbsolutePath() : "");
        wizard.setValue("video", assets.hasVideo() ? assets.videoFile.getAbsolutePath() : "");
        if (assets.manualSubtitleFile != null && assets.manualSubtitleFile.isFile()) {
            wizard.setValue("subtitle", assets.manualSubtitleFile.getAbsolutePath());
            wizard.setValue("subtitles-auto-generated", "false");
        } else if (assets.autoSubtitleFile != null && assets.autoSubtitleFile.isFile()) {
            wizard.setValue("subtitle", assets.autoSubtitleFile.getAbsolutePath());
            wizard.setValue("subtitles-auto-generated", "true");
        } else {
            wizard.setValue("subtitle", "");
            wizard.setValue("subtitles-auto-generated", "");
        }
        if (StringUtils.isBlank(wizard.getValue("artist")) && StringUtils.isNotBlank(assets.derivedArtist)) {
            wizard.setValue("artist", assets.derivedArtist);
        }
        if (StringUtils.isBlank(wizard.getValue("title")) && StringUtils.isNotBlank(assets.derivedTitle)) {
            wizard.setValue("title", assets.derivedTitle);
        }
    }

    private DownloadReuseMode promptReuseMode(TempWizardAssets assets) {
        Object[] options = {
                I18.get("create_youtube_existing_files_reuse"),
                I18.get("create_youtube_existing_files_missing"),
                I18.get("create_youtube_existing_files_redownload")
        };
        String message = MessageFormat.format(
                I18.get("create_youtube_existing_files_prompt"),
                assetStateLabel(assets.hasAudio()),
                assetStateLabel(assets.hasVideo()),
                assetStateLabel(assets.hasAnySubtitle()));
        int choice = JOptionPane.showOptionDialog(
                SwingUtilities.getWindowAncestor(this),
                message,
                I18.get("create_title"),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
        return switch (choice) {
            case 0 -> DownloadReuseMode.REUSE_EXISTING;
            case 1 -> DownloadReuseMode.DOWNLOAD_MISSING_ONLY;
            case 2 -> DownloadReuseMode.REDOWNLOAD_ALL;
            default -> null;
        };
    }

    private String assetStateLabel(boolean present) {
        return present ? I18.get("create_youtube_existing_files_present") : I18.get("create_youtube_existing_files_missing_state");
    }

    private void deleteExistingAssets(TempWizardAssets assets) {
        deleteAsset(assets.audioFile);
        deleteAsset(assets.videoFile);
        deleteAsset(assets.manualSubtitleFile);
        deleteAsset(assets.autoSubtitleFile);
    }

    private void deleteAsset(File asset) {
        if (asset == null || !asset.isFile()) {
            return;
        }
        if (!asset.delete()) {
            LOGGER.warning("Could not delete temp asset " + asset.getAbsolutePath());
        }
    }

    private boolean isAudioFile(File file) {
        String lower = file.getName().toLowerCase();
        String ext = getExtension(lower);
        return lower.contains(".v_none.") || AUDIO_EXTENSIONS.contains(ext);
    }

    private boolean isVideoFile(File file) {
        String lower = file.getName().toLowerCase();
        String ext = getExtension(lower);
        return lower.contains(".a_none.") || VIDEO_EXTENSIONS.contains(ext);
    }

    private boolean isSubtitleFile(File file) {
        return SUBTITLE_EXTENSIONS.contains(getExtension(file.getName().toLowerCase()));
    }

    private boolean isAutoSubtitleFile(File file) {
        String lower = file.getName().toLowerCase();
        return lower.contains("automatic subtitles") || lower.contains(".auto.");
    }

    private boolean preferAudioCandidate(File candidate, File current) {
        return candidate.getName().toLowerCase().contains(".v_none.")
                || candidate.lastModified() > current.lastModified();
    }

    private boolean preferVideoCandidate(File candidate, File current) {
        return candidate.getName().toLowerCase().contains(".a_none.")
                || candidate.lastModified() > current.lastModified();
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    private void maybeSeedArtistAndTitleFromPath(String relativePath) {
        if (StringUtils.isBlank(relativePath)) {
            return;
        }
        String youTubeId = extractYouTubeId(getYouTubeUrl());
        YouTubeMetadataParser.Metadata metadata =
                YouTubeMetadataParser.inferMetadata(new File(relativePath).getName(), youTubeId);
        if (StringUtils.isBlank(metadata.getArtist()) && StringUtils.isBlank(metadata.getTitle())) {
            return;
        }
        String currentArtist = StringUtils.trimToEmpty(wizard.getValue("artist"));
        String currentTitle = StringUtils.trimToEmpty(wizard.getValue("title"));
        String artist = StringUtils.isBlank(currentArtist) ? metadata.getArtist() : currentArtist;
        String title = StringUtils.isBlank(currentTitle) ? metadata.getTitle() : currentTitle;
        if (StringUtils.isNotBlank(artist) && StringUtils.isBlank(currentArtist)) {
            wizard.setValue("artist", artist);
        }
        if (StringUtils.isNotBlank(title) && StringUtils.isBlank(currentTitle)) {
            wizard.setValue("title", title);
        }
        if (StringUtils.isNotBlank(artist) || StringUtils.isNotBlank(title)) {
            persistYouTubeMetadata(youTubeId, artist, title);
        }
    }

    private void maybeDeriveArtistAndTitleFromFile(File file, TempWizardAssets assets, String youTubeId) {
        if (file == null || assets == null || (!StringUtils.isBlank(assets.derivedArtist) && !StringUtils.isBlank(assets.derivedTitle))) {
            return;
        }
        YouTubeMetadataParser.Metadata metadata = YouTubeMetadataParser.inferMetadata(file.getName(), youTubeId);
        if (StringUtils.isBlank(metadata.getArtist()) && StringUtils.isBlank(metadata.getTitle())) {
            return;
        }
        if (StringUtils.isBlank(assets.derivedArtist) && StringUtils.isNotBlank(metadata.getArtist())) {
            assets.derivedArtist = metadata.getArtist();
        }
        if (StringUtils.isBlank(assets.derivedTitle) && StringUtils.isNotBlank(metadata.getTitle())) {
            assets.derivedTitle = metadata.getTitle();
        }
    }

    private String normalizeDisplayBaseName(String baseName, String youTubeId) {
        String normalized = StringUtils.defaultString(baseName).trim();
        if (StringUtils.isBlank(normalized)) {
            return normalized;
        }
        normalized = normalized.replaceAll("\\.v_.*?\\.a_.*?$", "");
        normalized = normalized.replaceAll("\\.(f\\d+|NA)$", "");
        normalized = normalized.replaceAll("\\s+-\\s+subtitles(?:-auto)?$", "");
        normalized = normalized.replaceAll(",[a-z]{2}(?:-[A-Za-z0-9_-]{8,})?$", "");
        normalized = normalized.replaceAll("\\.[a-z]{2}(?:-[A-Za-z0-9_-]{8,})?$", "");
        if (StringUtils.isNotBlank(youTubeId)) {
            normalized = normalized.replace("." + youTubeId, "");
            normalized = normalized.replace(youTubeId + ".", "");
            normalized = normalized.replaceFirst("^" + Pattern.quote(youTubeId) + "\\s*-\\s*", "");
            normalized = normalized.replaceFirst("^" + Pattern.quote(youTubeId) + "[-_.]", "");
        }
        return normalized.trim();
    }

    private boolean isTechnicalBaseName(String baseName, String youTubeId) {
        String normalized = StringUtils.trimToEmpty(baseName);
        if (StringUtils.isBlank(normalized)) {
            return true;
        }
        if (StringUtils.isNotBlank(youTubeId) && normalized.equalsIgnoreCase(youTubeId)) {
            return true;
        }
        return normalized.matches("(?i)" + Pattern.quote(StringUtils.defaultString(youTubeId)))
                || normalized.matches("(?i)" + Pattern.quote(StringUtils.defaultString(youTubeId)) + "\\.(audio|video|subtitles\\.(manual|auto))");
    }

    private void applyStoredMetadata(String youTubeId, TempWizardAssets assets) {
        Properties metadata = loadYouTubeMetadata(youTubeId);
        if (metadata.isEmpty()) {
            return;
        }
        YouTubeMetadataParser.Metadata repairedMetadata = YouTubeMetadataParser.repairStoredMetadata(
                metadata.getProperty("artist"),
                metadata.getProperty("title"),
                youTubeId);
        String artist = sanitizeStoredArtist(repairedMetadata.getArtist(), youTubeId);
        String title = sanitizeStoredTitle(repairedMetadata.getTitle(), artist);
        if (!StringUtils.equals(metadata.getProperty("artist"), artist)
                || !StringUtils.equals(metadata.getProperty("title"), title)) {
            persistYouTubeMetadata(youTubeId, artist, title);
        }
        assets.derivedArtist = artist;
        assets.derivedTitle = title;
        if (StringUtils.isBlank(wizard.getValue("artist")) && StringUtils.isNotBlank(artist)) {
            wizard.setValue("artist", artist);
        }
        if (StringUtils.isBlank(wizard.getValue("title")) && StringUtils.isNotBlank(title)) {
            wizard.setValue("title", title);
        }
    }

    private Properties loadYouTubeMetadata(String youTubeId) {
        Properties properties = new Properties();
        File metadataFile = getYouTubeMetadataFile(youTubeId);
        if (metadataFile == null || !metadataFile.isFile()) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(metadataFile.toPath())) {
            properties.load(input);
        } catch (IOException ex) {
            LOGGER.warning("Could not read YouTube temp metadata " + metadataFile.getAbsolutePath());
        }
        return properties;
    }

    private void persistYouTubeMetadata(String youTubeId, String artist, String title) {
        File metadataFile = getYouTubeMetadataFile(youTubeId);
        if (metadataFile == null) {
            return;
        }
        Properties properties = new Properties();
        properties.setProperty("artist", StringUtils.defaultString(artist));
        properties.setProperty("title", StringUtils.defaultString(title));
        try (OutputStream output = Files.newOutputStream(metadataFile.toPath())) {
            properties.store(output, "Wizard YouTube temp metadata");
        } catch (IOException ex) {
            LOGGER.warning("Could not write YouTube temp metadata " + metadataFile.getAbsolutePath());
        }
    }

    private File getYouTubeMetadataFile(String youTubeId) {
        if (StringUtils.isBlank(youTubeId)) {
            return null;
        }
        File tempDir = new File(wizard.getProperty("temp-dir"));
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        return new File(tempDir, youTubeId + ".metadata.properties");
    }

    private String sanitizeStoredArtist(String artist, String youTubeId) {
        String value = StringUtils.trimToEmpty(artist);
        if (StringUtils.isBlank(value) || StringUtils.isBlank(youTubeId)) {
            return value;
        }
        value = value.replaceFirst("^" + Pattern.quote(youTubeId) + "\\s*-\\s*", "");
        value = value.replaceFirst("^" + Pattern.quote(youTubeId) + "[-_.]", "");
        return value.trim();
    }

    private String sanitizeStoredTitle(String title, String artist) {
        String value = StringUtils.trimToEmpty(title);
        if (StringUtils.isBlank(value)) {
            return value;
        }
        value = value.replaceAll(",[a-z]{2}(?:-[A-Za-z0-9_-]{8,})?$", "");
        value = value.replaceAll("\\.[a-z]{2}(?:-[A-Za-z0-9_-]{8,})?$", "");
        return cleanupParsedTitle(artist, value);
    }

    private String cleanupParsedTitle(String artist, String title) {
        String cleanTitle = StringUtils.trimToEmpty(title);
        String cleanArtist = StringUtils.trimToEmpty(artist);
        if (StringUtils.isBlank(cleanTitle)) {
            return cleanTitle;
        }
        if (StringUtils.isNotBlank(cleanArtist)) {
            String pattern = "(?i)^" + Pattern.quote(cleanArtist) + "\\s*-\\s*";
            cleanTitle = cleanTitle.replaceFirst(pattern, "");
        }
        cleanTitle = cleanTitle.replaceAll("\\s*\\((?i:official\\s+video)\\)\\s*$", "");
        cleanTitle = cleanTitle.replaceAll("\\s+", " ").trim();
        return cleanTitle;
    }

    private static final Set<String> VIDEO_EXTENSIONS =
            Set.of("mp4", "mkv", "webm", "mov", "avi");
    private static final Set<String> SUBTITLE_EXTENSIONS =
            Set.of("srt", "vtt", "ass", "ssa", "lrc");
}

