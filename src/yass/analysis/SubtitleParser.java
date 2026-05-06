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

package yass.analysis;

import org.apache.commons.lang3.StringUtils;
import yass.titlecase.TitleCaseConverter;
import yass.titlecase.PhrasalVerbManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A parser for reading subtitle files (like .vtt or .srt) and extracting timed text.
 */
public class SubtitleParser {

    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final int MAX_ROLLING_CAPTION_GAP_MS = 400;

    /**
     * Parses a subtitle file (SRT or VTT) into a map of timestamps and text.
     *
     * @param subtitleFile The .srt or .vtt file to parse.
     * @return A map where the key is the start time in milliseconds and the value is the cleaned subtitle text.
     *         The map preserves the insertion order of the subtitles.
     *         Returns an empty map if the file cannot be read or is invalid.
     */
    public static Map<Integer, String> parse(File subtitleFile) {
        Map<Integer, String> subtitles = new LinkedHashMap<>();
        if (subtitleFile == null || !subtitleFile.exists()) {
            LOGGER.warning("Subtitle file not found or is null: " + (subtitleFile != null ? subtitleFile.getAbsolutePath() : "null"));
            return subtitles;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(subtitleFile))) {
            String line;
            String previousCleanedText = null;
            int previousEndMillis = -1;
            while ((line = reader.readLine()) != null) {
                // Skip WEBVTT header, comments, and empty lines that separate cues
                if (line.equals("WEBVTT") || line.startsWith("NOTE") || line.trim().isEmpty()) {
                    continue;
                }

                // Skip sequence number if present (common in SRT)
                if (line.matches("^\\d+$")) {
                    line = reader.readLine(); // The next line should be the timestamp
                    if (line == null) {
                        break; // End of file
                    }
                }

                // We should now be at a timestamp line
                if (line.contains("-->")) {
                    String[] range = line.split("-->", 2);
                    int millis = parseTimestamp(range[0].trim());
                    int endMillis = range.length > 1 ? parseTimestamp(extractEndTimestamp(range[1])) : -1;
                    if (millis == -1) {
                        LOGGER.warning("Skipping invalid timestamp line: " + line);
                        continue;
                    }

                    StringBuilder textBuilder = new StringBuilder();
                    while ((line = reader.readLine()) != null && !line.trim().isEmpty()) {
                        if (!textBuilder.isEmpty()) {
                            textBuilder.append(" ");
                        }
                        textBuilder.append(line);
                    }

                    String cleanedText = cleanText(textBuilder.toString());
                    if (!cleanedText.isEmpty()) {
                        String incrementalText = collapseRollingCaption(previousCleanedText, previousEndMillis, millis, cleanedText);
                        if (!incrementalText.isEmpty()) {
                            subtitles.put(millis, incrementalText);
                        }
                        previousCleanedText = cleanedText;
                        previousEndMillis = endMillis;
                    }
                }
            }
            normalizeSubtitleCasingIfNeeded(subtitles);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading subtitle file: " + subtitleFile.getAbsolutePath(), e);
        }

        return subtitles;
    }

    private static String extractEndTimestamp(String rawEndPart) {
        String trimmed = StringUtils.trimToEmpty(rawEndPart);
        int firstSpace = trimmed.indexOf(' ');
        return firstSpace >= 0 ? trimmed.substring(0, firstSpace) : trimmed;
    }

    /**
     * Heuristically checks if a subtitle file appears to be auto-generated by YouTube.
     * <p>
     * This check is based on the common presence of specific VTT cue settings like "align:start"
     * in auto-generated files from YouTube, which are often absent in manually created subtitles.
     *
     * @param subtitleFile The .vtt or .srt file to check.
     * @return {@code true} if the file is likely auto-generated, {@code false} otherwise.
     */
    public static boolean isAutoGenerated(File subtitleFile) {
        if (subtitleFile == null || !subtitleFile.exists()) {
            return false;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(subtitleFile))) {
            String line;
            int cuesWithSettings = 0;
            int totalCues = 0;
            final int CUES_TO_SAMPLE = 20;

            while ((line = reader.readLine()) != null && totalCues < CUES_TO_SAMPLE) {
                if (line.contains("-->")) {
                    totalCues++;
                    if (line.matches(".*align:(start|middle|end|left|right).*")) {
                        cuesWithSettings++;
                    }
                }
            }
            return totalCues > 0 && (double) cuesWithSettings / totalCues > 0.5;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading subtitle file for auto-generation check: " + subtitleFile.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Parses a timestamp string (e.g., "00:01:23.456" or "01:23.456") into milliseconds.
     *
     * @param timestamp The timestamp string.
     * @return The time in milliseconds, or -1 if the format is invalid.
     */
    private static int parseTimestamp(String timestamp) {
        try {
            timestamp = timestamp.replace(',', '.');
            String[] parts = timestamp.split(":");

            int hours = (parts.length == 3) ? Integer.parseInt(parts[0]) : 0;
            int minutes = Integer.parseInt(parts[parts.length - 2]);
            String[] secParts = parts[parts.length - 1].split("\\.");
            int seconds = Integer.parseInt(secParts[0]);
            int millis = Integer.parseInt(secParts[1]);

            return hours * 3600 * 1000 + minutes * 60 * 1000 + seconds * 1000 + millis;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            LOGGER.log(Level.WARNING, "Could not parse timestamp: " + timestamp, e);
            return -1;
        }
    }

    /**
     * Cleans subtitle text by removing common artifacts like CC hints and HTML tags.
     *
     * @param text The raw subtitle text.
     * @return The cleaned text.
     */
    private static String cleanText(String text) {
        // Remove CC hints like [Music] or (SOUND)
        text = text.replaceAll("[\\[\\(].*?[\\]\\)]", "");
        // Remove musical note character
        text = text.replace("\u266A", "");
        // Remove HTML-like tags e.g. <c.color> or <i>
        text = text.replaceAll("<.*?>", "");
        // Remove leading hyphens or other list-like markers that are sometimes used for different speakers
        text = text.replaceAll("^[-\u2013\u2014]\\s*", "");
        // Replace multiple spaces with a single space and trim
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String collapseRollingCaption(String previousText,
                                                 int previousEndMillis,
                                                 int currentStartMillis,
                                                 String currentText) {
        if (StringUtils.isBlank(previousText) || StringUtils.isBlank(currentText)) {
            return StringUtils.defaultString(currentText);
        }
        if (!isRollingCaptionContinuation(previousEndMillis, currentStartMillis)) {
            return currentText;
        }

        List<String> previousTokens = tokenize(previousText);
        List<String> currentTokens = tokenize(currentText);
        if (previousTokens.isEmpty() || currentTokens.isEmpty()) {
            return currentText;
        }

        int overlap = findTrailingLeadingTokenOverlap(previousTokens, currentTokens);
        if (overlap <= 0) {
            return currentText;
        }
        if (overlap >= currentTokens.size()) {
            return "";
        }
        return String.join(" ", currentTokens.subList(overlap, currentTokens.size())).trim();
    }

    private static boolean isRollingCaptionContinuation(int previousEndMillis, int currentStartMillis) {
        return previousEndMillis >= 0
                && currentStartMillis >= 0
                && Math.abs(currentStartMillis - previousEndMillis) <= MAX_ROLLING_CAPTION_GAP_MS;
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        for (String token : StringUtils.split(StringUtils.defaultString(text))) {
            if (StringUtils.isNotBlank(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static int findTrailingLeadingTokenOverlap(List<String> previousTokens, List<String> currentTokens) {
        int maxOverlap = Math.min(previousTokens.size(), currentTokens.size());
        for (int overlap = maxOverlap; overlap > 0; overlap--) {
            boolean matches = true;
            for (int i = 0; i < overlap; i++) {
                String previousToken = previousTokens.get(previousTokens.size() - overlap + i);
                String currentToken = currentTokens.get(i);
                if (!previousToken.equals(currentToken)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return overlap;
            }
        }
        return 0;
    }

    private static void normalizeSubtitleCasingIfNeeded(Map<Integer, String> subtitles) {
        if (subtitles.isEmpty() || !shouldNormalizeAllCaps(subtitles)) {
            return;
        }
        PhrasalVerbManager.getInstance(null);
        for (Map.Entry<Integer, String> entry : subtitles.entrySet()) {
            entry.setValue(TitleCaseConverter.toApTitleCase(StringUtils.lowerCase(entry.getValue())));
        }
    }

    private static boolean shouldNormalizeAllCaps(Map<Integer, String> subtitles) {
        int uppercaseLetters = 0;
        int lowercaseLetters = 0;
        for (String value : subtitles.values()) {
            for (char ch : StringUtils.defaultString(value).toCharArray()) {
                if (Character.isUpperCase(ch)) {
                    uppercaseLetters++;
                } else if (Character.isLowerCase(ch)) {
                    lowercaseLetters++;
                }
            }
        }
        int totalLetters = uppercaseLetters + lowercaseLetters;
        if (totalLetters < 8) {
            return false;
        }
        return lowercaseLetters == 0 || ((double) uppercaseLetters / totalLetters) >= 0.9d;
    }
}
