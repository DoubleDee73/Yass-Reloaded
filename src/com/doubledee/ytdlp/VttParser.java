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

package com.doubledee.ytdlp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VttParser {
    private static final Pattern TIMECODE_PATTERN =
            Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3})\\s+-->\\s+(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3})");

    public static List<VttCue> parse(Path path) throws IOException {
        List<VttCue> cues = new ArrayList<>();
        List<String> lines = Files.readAllLines(path);

        long start = -1;
        long end = -1;
        StringBuilder textBuffer = new StringBuilder();

        for (String line : lines) {
            Matcher m = TIMECODE_PATTERN.matcher(line);
            List<String> words;
            if (!textBuffer.isEmpty()) {
                String[] tokens = textBuffer.toString().trim().split("\\s+");
                words = tokens.length == 0 ? List.of() : List.of(tokens);
            } else {
                words = List.of();
            }
            if (m.find()) {
                if (start >= 0 && !textBuffer.isEmpty()) {
                    cues.add(new VttCue(start, end, words));
                    textBuffer.setLength(0);
                }
                start = parseMillis(m.group(1), m.group(2), m.group(3), m.group(4));
                end   = parseMillis(m.group(5), m.group(6), m.group(7), m.group(8));
            } else if (!line.isBlank() && !line.startsWith("WEBVTT") && !line.startsWith("NOTE")) {
                if (!textBuffer.isEmpty()) textBuffer.append(" ");
                textBuffer.append(line.trim());
            }
        }

        if (start >= 0 && !textBuffer.isEmpty()) {
            String[] tokens = textBuffer.toString().trim().split("\\s+");
            List<String> words = tokens.length == 0 ? List.of() : List.of(tokens);
            cues.add(new VttCue(start, end, words));
        }
        return cues;
    }

    private static long parseMillis(String h, String m, String s, String ms) {
        long hours = Long.parseLong(h);
        long minutes = Long.parseLong(m);
        long seconds = Long.parseLong(s);
        long millis = Long.parseLong(ms);
        return (((hours * 60 + minutes) * 60) + seconds) * 1000 + millis;
    }
}
