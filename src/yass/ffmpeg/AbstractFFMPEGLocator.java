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

package yass.ffmpeg;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Logger;

public abstract class AbstractFFMPEGLocator {
    final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    public static final String CURRENT_OS = System.getProperty("os.name").toLowerCase();
    public static final String PATH = System.getenv("PATH");

    abstract boolean isCurrentOS();

    abstract String getFfmpegPath();

    String findFfmpegInPath() {
        return findFfmpegInPath(PATH);
    }

    String findFfmpegInPath(String pathValue) {
        if (pathValue == null || pathValue.isBlank()) {
            return null;
        }
        String delimiter;
        if (CURRENT_OS.contains("linux") || CURRENT_OS.contains("mac")) {
            delimiter = ":";
        } else {
            delimiter = ";";
        }
        String[] pathsToSearch = pathValue.split(delimiter);
        String returnPath = null;
        for (String path : pathsToSearch) {
            if (isValidFfmpegBinDirectory(path)) {
                returnPath = path;
                break;
            }
        }
        if (returnPath != null) {
            LOGGER.info("Found FFmpeg in path " + returnPath);
        }
        return returnPath;
    }

    private boolean isValidFfmpegBinDirectory(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        java.io.File dir = Path.of(path).toFile();
        java.io.File[] files = dir.listFiles();
        if (!dir.exists() || !dir.isDirectory() || files == null) {
            return false;
        }

        boolean hasFfmpeg = hasExecutable(files, "ffmpeg");
        boolean hasFfprobe = hasExecutable(files, "ffprobe");
        if (!hasFfmpeg || !hasFfprobe) {
            if (path.toLowerCase().contains("ffmpeg") || path.toLowerCase().contains("bin") || path.toLowerCase().contains("homebrew")) {
                LOGGER.info("Skipping FFmpeg path candidate without both ffmpeg and ffprobe: " + path);
            }
            return false;
        }
        return true;
    }

    private boolean hasExecutable(java.io.File[] files, String baseName) {
        return Arrays.stream(Objects.requireNonNull(files))
                .anyMatch(file -> file.isFile() && matchesExecutableName(file.getName(), baseName));
    }

    private boolean matchesExecutableName(String fileName, String baseName) {
        if (CURRENT_OS.contains("win")) {
            return fileName.equalsIgnoreCase(baseName + ".exe");
        }
        return fileName.equals(baseName);
    }
}
