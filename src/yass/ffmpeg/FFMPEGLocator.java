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

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFprobe;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class FFMPEGLocator {
    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static FFMPEGLocator INSTANCE;

    private FFmpeg ffmpeg;
    private FFprobe ffprobe;
    private String path;

    private FFMPEGLocator(FFmpeg ffmpeg, FFprobe ffprobe) {
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
    }

    private static final List<AbstractFFMPEGLocator> LOCATORS = Arrays.asList(
            new WindowsFFMPEGLocator(), new MacFFMPEGLocator(), new LinuxFFMPEGLocator()
    );

    public static String determineFfmpegPath() {
        for (AbstractFFMPEGLocator locator : LOCATORS) {
            if (locator.isCurrentOS()) {
                return locator.getFfmpegPath();
            }
        }
        return null;
    }

    public static FFMPEGLocator getInstance(String ffmpegPath) {
        if (INSTANCE == null) {
            if (StringUtils.isNotEmpty(ffmpegPath)) {
                try {
                    INSTANCE = new FFMPEGLocator(new FFmpeg(ffmpegPath + File.separator + "ffmpeg"),
                                                 new FFprobe(ffmpegPath + File.separator + "ffprobe"));
                    INSTANCE.setPath(ffmpegPath);
                } catch (IOException e) {
                    LOGGER.info("Could not find ffmpeg at the given location " + ffmpegPath);
                }
            } else {
                INSTANCE = new FFMPEGLocator(null, null);
            }
        }
        return INSTANCE;
    }

    public static FFMPEGLocator getInstance() {
        if (INSTANCE == null) {
            INSTANCE = getInstance(determineFfmpegPath());
        }
        return INSTANCE;
    }
    
    public FFmpeg getFfmpeg() {
        return ffmpeg;
    }

    public void setFfmpeg(FFmpeg ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    public FFprobe getFfprobe() {
        return ffprobe;
    }

    public void setFfprobe(FFprobe ffprobe) {
        this.ffprobe = ffprobe;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
    
    public boolean hasFFmpeg() {
        return ffmpeg != null && ffprobe != null;
    }
}
