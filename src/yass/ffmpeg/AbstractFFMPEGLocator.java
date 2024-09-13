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

public abstract class AbstractFFMPEGLocator {

    public static final String CURRENT_OS = System.getProperty("os.name").toLowerCase();
    public static final String PATH = System.getenv("PATH");

    abstract boolean isCurrentOS();

    abstract String getFfmpegPath();

    String findFfmpegInPath() {
        String[] pathsToSearch = PATH.split(";");
        String returnPath = null;
        for (String path : pathsToSearch) {
            if (path.contains("ffmpeg")) {
                returnPath = path;
                break;
            }
            if ((path.contains("/bin") || path.contains("/homebrew")) && Arrays.stream(
                                                                                       Objects.requireNonNull(Path.of(path)
                                                                                                                  .toFile()
                                                                                                                  .listFiles()))
                                                                               .anyMatch(file -> file.getName()
                                                                                                     .contains(
                                                                                                             "ffmpeg"))) {
                returnPath = path;
                break;
            }
        }
        if (returnPath != null) {
            System.out.println("Found FFmpeg in path " + returnPath);
        }
        return returnPath;
    }
}
