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

import java.io.File;

/**
 * Description of the Class
 *
 * @author Saruta
 */
public class LocationsPanel extends OptionsPanel {

    private static final long serialVersionUID = -7453496938869803003L;

    /**
     * Gets the body attribute of the DirPanel object
     */
    public void addRows() {
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
        addDirectory(I18.get("options_locations_ffmpeg"), "ffmpegPath");
        addComment(I18.get("options_locations_ffmpeg_comment"));
        addFile(I18.get("options_locations_ytdlp"), "ytdlpPath");
        addComment(I18.get("options_locations_ytdlp_comment"));
        addDirectory(I18.get("options_locations_aubio"), "aubioPath");
        addComment(I18.get("options_locations_aubio_comment"));
    }

    /**
     * Checks the system's PATH for external tools and pre-fills the configuration
     * fields if they are found and not already configured by the user.
     */
    private void prefillToolPaths() {
        if (StringUtils.isEmpty(getProperty("ffmpegPath"))) {
            String ffmpegPath = findExecutable("ffmpeg");
            if (ffmpegPath != null) {
                File ffmpegFile = new File(ffmpegPath);
                setProperty("ffmpegPath", ffmpegFile.getParent());
                setGoToYtDlpPanel(true);
            }
        }
        if (StringUtils.isEmpty(getProperty("ytdlpPath"))) {
            String ytdlpPath = findExecutable("yt-dlp");
            if (ytdlpPath != null) {
                setProperty("ytdlpPath", ytdlpPath);
            }
        }
        if (StringUtils.isEmpty(getProperty("aubioPath"))) {
            // We can check for any of the aubio tools. 'aubiotrack' is a good candidate.
            String aubioToolPath = findExecutable("aubiotrack");
            if (aubioToolPath != null) {
                File aubioFile = new File(aubioToolPath);
                setProperty("aubioPath", aubioFile.getParent());
            }
        }
    }

    /**
     * Searches for an executable in the directories specified by the PATH environment variable.
     *
     * @param executableName The name of the executable to find (without extension).
     * @return The absolute path to the executable if found, otherwise null.
     */
    private String findExecutable(String executableName) {
        String os = System.getProperty("os.name").toLowerCase();
        String[] executableNames = os.contains("win")
                ? new String[]{executableName + ".exe", executableName + ".bat", executableName + ".cmd"}
                : new String[]{executableName};

        String systemPath = System.getenv("PATH");
        if (systemPath == null) return null;

        for (String pathDir : systemPath.split(File.pathSeparator)) {
            for (String name : executableNames) {
                File file = new File(pathDir, name);
                if (file.isFile() && file.canExecute()) {
                    return file.getAbsolutePath();
                }
            }
        }
        return null;
    }
}
