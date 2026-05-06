/*
 * Yass - Karaoke Editor
 * Copyright (C) 2009 Saruta
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

package yass.options;

import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.PythonRuntimeSupport;
import yass.ffmpeg.FFMPEGLocator;
import yass.integration.separation.audioseparator.AudioSeparatorHealthCheckService;

import javax.swing.*;
import java.io.File;
import java.util.logging.Logger;

/**
 * Description of the Class
 *
 * @author Saruta
 */
public class LocationsPanel extends OptionsPanel {

    private static final long serialVersionUID = -7453496938869803003L;
    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final String OPTIONS_DEBUG = "[OptionsDebug] ";

    public LocationsPanel() {
        super();
    }

    /**
     * Gets the body attribute of the DirPanel object
     */
    public void addRows() {
        setLabelWidth(180);
        // Pre-fill paths from environment if not already set by the user.
        prefillToolPaths();

        addSeparator(I18.get("options_locations_cache"));
        addFile(I18.get("options_locations_cache_songs"), "songlist-cache");
        addComment(I18.get("options_locations_cache_songs_comment"));
        addFile(I18.get("options_locations_cache_playlists"), "playlist-cache");
        addComment(I18.get("options_locations_cache_playlists_comment"));

        addDirectory(I18.get("options_locations_cache_covers"), "songlist-imagecache");
        addComment(I18.get("options_locations_cache_covers_comment"));

        addComment(I18.get("options_locations_cache_comment"));

        addSeparator(I18.get("options_locations_tools"));
        addFile("Default Python executable", PythonRuntimeSupport.PROP_DEFAULT_PYTHON);
        addComment("Used for Python-based helpers and as fallback when a tool-specific Python runtime is empty.");
        addDirectory(I18.get("options_locations_ffmpeg"), "ffmpegPath");
        addComment(I18.get("options_locations_ffmpeg_comment"));
        addFile(I18.get("options_locations_ytdlp"), "ytdlpPath");
        addComment(I18.get("options_locations_ytdlp_comment"));
        addDirectory(I18.get("options_locations_aubio"), "aubioPath");
        addComment(I18.get("options_locations_aubio_comment"));
        addDirectory(I18.get("options_locations_syncer"), "usdb-syncer-path");
        addComment(I18.get("options_locations_syncer_comment"));
    }

    /**
     * Checks the system's PATH for external tools and pre-fills the configuration
     * fields if they are found and not already configured by the user.
     */
    private void prefillToolPaths() {
        LOGGER.info(OPTIONS_DEBUG + "LocationsPanel prefillToolPaths start");
        String configuredPython = getProperty(PythonRuntimeSupport.PROP_DEFAULT_PYTHON);
        if (PythonRuntimeSupport.shouldAutodetectDefaultPython(configuredPython)) {
            String detectedPython = new AudioSeparatorHealthCheckService(configuredPython, "", getProperty("ffmpegPath")).detectPythonExecutable();
            if (StringUtils.isNotBlank(detectedPython)) {
                setProperty(PythonRuntimeSupport.PROP_DEFAULT_PYTHON, detectedPython);
                LOGGER.info(OPTIONS_DEBUG + "Auto-detected default Python at " + detectedPython);
            }
        }
        if (StringUtils.isEmpty(getProperty("ffmpegPath"))) {
            String ffmpegPath = findExecutable("ffmpeg");
            if (ffmpegPath != null) {
                File ffmpegFile = new File(ffmpegPath);
                setProperty("ffmpegPath", ffmpegFile.getParent());
                setGoToYtDlpPanel(true);
                LOGGER.info(OPTIONS_DEBUG + "Auto-detected ffmpeg at " + ffmpegPath);
            }
        }
        if (StringUtils.isEmpty(getProperty("ytdlpPath"))) {
            String ytdlpPath = findExecutable("yt-dlp");
            if (ytdlpPath != null) {
                setProperty("ytdlpPath", ytdlpPath);
                LOGGER.info(OPTIONS_DEBUG + "Auto-detected yt-dlp at " + ytdlpPath);
            }
        }
        if (StringUtils.isEmpty(getProperty("aubioPath"))) {
            // We can check for any of the aubio tools. 'aubiotrack' is a good candidate.
            String aubioToolPath = findExecutable("aubiotrack");
            if (aubioToolPath != null) {
                File aubioFile = new File(aubioToolPath);
                setProperty("aubioPath", aubioFile.getParent());
                LOGGER.info(OPTIONS_DEBUG + "Auto-detected aubio at " + aubioToolPath);
            }
        }
        LOGGER.info(OPTIONS_DEBUG + "LocationsPanel prefillToolPaths end");
    }

    /**
     * Searches for an executable in the directories specified by the PATH environment variable.
     *
     * @param executableName The name of the executable to find (without extension).
     * @return The absolute path to the executable if found, otherwise null.
     */
    private String findExecutable(String executableName) {
        LOGGER.info(OPTIONS_DEBUG + "Searching PATH for executable: " + executableName);
        String os = System.getProperty("os.name").toLowerCase();
        String[] executableNames = os.contains("win")
                ? new String[]{executableName + ".exe", executableName + ".bat", executableName + ".cmd"}
                : new String[]{executableName};

        String systemPath = System.getenv("PATH");
        if (systemPath == null) {
            LOGGER.info(OPTIONS_DEBUG + "PATH environment variable is empty while searching for " + executableName);
            return null;
        }

        for (String pathDir : systemPath.split(File.pathSeparator)) {
            for (String name : executableNames) {
                File file = new File(pathDir, name);
                if (file.isFile() && file.canExecute()) {
                    LOGGER.info(OPTIONS_DEBUG + "Found " + executableName + " at " + file.getAbsolutePath());
                    return file.getAbsolutePath();
                }
            }
        }
        LOGGER.info(OPTIONS_DEBUG + "Did not find " + executableName + " on PATH");
        return null;
    }

    @Override
    public void setProperty(String key, String value) {
        String oldValue = getProperty(key);
        super.setProperty(key, value);

        if ("ffmpegPath".equals(key)) {
            if (value != null && !value.equals(oldValue)) {
                if (StringUtils.isNotEmpty(value)) {
                    // Use invokeLater to ensure UI updates (like the text field) happen before any dialog blocks
                    SwingUtilities.invokeLater(() -> {
                        LOGGER.info("FFmpeg path changed to: " + value + ". Re-initializing FFMPEGLocator.");
                        FFMPEGLocator.initFfmpeg(value); // Re-initialize with the new path

                        // Validate the new path immediately
                        if (FFMPEGLocator.getInstance().getFfmpeg() == null || FFMPEGLocator.getInstance().getFfprobe() == null) {
                            JOptionPane.showMessageDialog(this,
                                    "<html>" + I18.get("ffmpeg_folder_invalid") + "</html>",
                                    I18.get("ffmpeg_folder_invalid_title"),
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        LOGGER.info("FFmpeg path cleared. Resetting FFMPEGLocator to use system PATH.");
                        FFMPEGLocator.getInstance(null);
                    });
                }
            }
        }
    }
}
