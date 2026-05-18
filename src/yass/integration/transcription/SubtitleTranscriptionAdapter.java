package yass.integration.transcription;

import org.apache.commons.lang3.StringUtils;
import yass.alignment.LyricsAlignmentTokenizer;
import yass.analysis.SubtitleParser;
import yass.integration.transcription.openai.OpenAiTranscriptSegment;
import yass.integration.transcription.openai.OpenAiTranscriptWord;
import yass.integration.transcription.openai.OpenAiTranscriptionResult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SubtitleTranscriptionAdapter {
    public static final String SOURCE_TAG = "#SUBTITLES";

    public OpenAiTranscriptionResult fromSubtitles(File subtitleFile) {
        return fromCues(SubtitleParser.parseCues(subtitleFile));
    }

    public OpenAiTranscriptionResult fromCues(List<SubtitleParser.SubtitleCue> cues) {
        if (cues == null || cues.isEmpty()) {
            return null;
        }

        List<OpenAiTranscriptSegment> segments = new ArrayList<>();
        List<OpenAiTranscriptWord> words = new ArrayList<>();
        StringBuilder transcriptText = new StringBuilder();
        for (SubtitleParser.SubtitleCue cue : cues) {
            if (cue == null || StringUtils.isBlank(cue.text())) {
                continue;
            }
            int startMs = Math.max(0, cue.startMs());
            int endMs = Math.max(startMs + 1, cue.endMs());
            List<OpenAiTranscriptWord> segmentWords = buildWords(cue.text(), startMs, endMs);
            if (segmentWords.isEmpty()) {
                continue;
            }
            if (!transcriptText.isEmpty()) {
                transcriptText.append('\n');
            }
            transcriptText.append(cue.text());
            words.addAll(segmentWords);
            segments.add(new OpenAiTranscriptSegment(startMs, endMs, cue.text(), segmentWords));
        }
        if (segments.isEmpty()) {
            return null;
        }

        return new OpenAiTranscriptionResult(null,
                null,
                SOURCE_TAG,
                transcriptText.toString(),
                words,
                segments,
                List.of(),
                false,
                null);
    }

    private List<OpenAiTranscriptWord> buildWords(String text, int startMs, int endMs) {
        List<String> tokens = new ArrayList<>();
        for (String token : StringUtils.defaultString(text).trim().split("\\s+")) {
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
}
