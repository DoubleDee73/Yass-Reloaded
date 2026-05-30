package yass.integration.lyrics.lrc;

import org.apache.commons.lang3.StringUtils;
import yass.alignment.LyricsAlignmentTokenizer;
import yass.integration.transcription.openai.OpenAiTranscriptSegment;
import yass.integration.transcription.openai.OpenAiTranscriptWord;
import yass.integration.transcription.openai.OpenAiTranscriptionResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LrcTranscriptionAdapter {
    public static final String SOURCE_TAG = "#LRC";
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]");

    public OpenAiTranscriptionResult fromFile(File lrcFile, int durationSeconds) throws IOException {
        if (lrcFile == null || !lrcFile.isFile()) {
            throw new IOException("LRC file not found.");
        }
        return fromLrcText(Files.readString(lrcFile.toPath(), StandardCharsets.UTF_8), durationSeconds);
    }

    public OpenAiTranscriptionResult fromLrcText(String lrcText, int durationSeconds) {
        List<OpenAiTranscriptSegment> segments = parseSyncedLyrics(lrcText, durationSeconds);
        List<OpenAiTranscriptWord> words = new ArrayList<>();
        for (OpenAiTranscriptSegment segment : segments) {
            words.addAll(segment.getWords());
        }
        String transcriptText = normalizeTranscriptText(segments);
        return new OpenAiTranscriptionResult(
                null,
                null,
                SOURCE_TAG,
                transcriptText,
                words,
                segments,
                List.of(),
                false,
                null,
                SOURCE_TAG,
                SOURCE_TAG);
    }

    private List<OpenAiTranscriptSegment> parseSyncedLyrics(String syncedLyrics, int durationSeconds) {
        if (StringUtils.isBlank(syncedLyrics)) {
            return List.of();
        }

        List<TimedLine> timedLines = new ArrayList<>();
        for (String rawLine : syncedLyrics.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            Matcher matcher = TIMESTAMP_PATTERN.matcher(rawLine);
            List<Integer> timestamps = new ArrayList<>();
            int endOfLastTimestamp = 0;
            while (matcher.find()) {
                timestamps.add(toMilliseconds(matcher.group(1), matcher.group(2), matcher.group(3)));
                endOfLastTimestamp = matcher.end();
            }
            if (timestamps.isEmpty()) {
                continue;
            }
            String text = StringUtils.trimToEmpty(rawLine.substring(endOfLastTimestamp));
            for (Integer timestamp : timestamps) {
                timedLines.add(new TimedLine(timestamp, text));
            }
        }
        timedLines.sort(Comparator.comparingInt(TimedLine::startMs));
        if (timedLines.isEmpty()) {
            return List.of();
        }

        List<OpenAiTranscriptSegment> segments = new ArrayList<>();
        int fallbackEndMs = durationSeconds > 0 ? durationSeconds * 1000 : timedLines.get(timedLines.size() - 1).startMs() + 2000;
        for (int i = 0; i < timedLines.size(); i++) {
            TimedLine current = timedLines.get(i);
            int nextStartMs = i + 1 < timedLines.size() ? timedLines.get(i + 1).startMs() : fallbackEndMs;
            int endMs = Math.max(current.startMs() + 1, nextStartMs);
            List<OpenAiTranscriptWord> words = buildWords(current.text(), current.startMs(), endMs);
            segments.add(new OpenAiTranscriptSegment(current.startMs(), endMs, current.text(), words));
        }
        return segments;
    }

    private List<OpenAiTranscriptWord> buildWords(String line, int startMs, int endMs) {
        List<String> tokens = new ArrayList<>();
        for (String token : StringUtils.defaultString(line).trim().split("\\s+")) {
            String trimmed = StringUtils.trimToEmpty(token);
            if (StringUtils.isNotBlank(trimmed)) {
                tokens.add(trimmed);
            }
        }
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<OpenAiTranscriptWord> words = new ArrayList<>(tokens.size());
        int span = Math.max(1, endMs - startMs);
        for (int i = 0; i < tokens.size(); i++) {
            int wordStart = startMs + (int) Math.round((double) span * i / tokens.size());
            int wordEnd = startMs + (int) Math.round((double) span * (i + 1) / tokens.size());
            if (wordEnd <= wordStart) {
                wordEnd = wordStart + 1;
            }
            String token = tokens.get(i);
            words.add(new OpenAiTranscriptWord(token, LyricsAlignmentTokenizer.normalizeText(token), wordStart, wordEnd));
        }
        return words;
    }

    private int toMilliseconds(String minutePart, String secondPart, String fractionPart) {
        int minutes = Integer.parseInt(minutePart);
        int seconds = Integer.parseInt(secondPart);
        int millis = 0;
        if (StringUtils.isNotBlank(fractionPart)) {
            String padded = StringUtils.rightPad(fractionPart, 3, '0');
            millis = Integer.parseInt(padded.substring(0, 3));
        }
        return minutes * 60_000 + seconds * 1_000 + millis;
    }

    private String normalizeTranscriptText(List<OpenAiTranscriptSegment> segments) {
        return segments.stream()
                .map(OpenAiTranscriptSegment::getText)
                .filter(StringUtils::isNotBlank)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private record TimedLine(int startMs, String text) {
    }
}
