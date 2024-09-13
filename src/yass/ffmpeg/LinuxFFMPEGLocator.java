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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class LinuxFFMPEGLocator extends AbstractFFMPEGLocator {
    @Override
    boolean isCurrentOS() {
        return CURRENT_OS.contains("linux");
    }

    @Override
    String getFfmpegPath() {
        Runtime run = Runtime.getRuntime();

        Process p = null;
        String cmd = "which ffmpeg";
        String s;
        String ffmpeg = null;
        try {
            p = run.exec(cmd);
            p.getErrorStream();

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

            while ((s = stdInput.readLine()) != null && ffmpeg == null) {
                if (s.contains("ffmpeg")) {
                    ffmpeg = s.substring(0, s.indexOf("ffmpeg"));
                }
            }
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            System.out.println("Error occured");
            ffmpeg = null;
        } finally {
            p.destroy();
        }
        return ffmpeg;
    }
}
