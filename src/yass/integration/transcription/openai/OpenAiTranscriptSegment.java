package yass.integration.transcription.openai;

import java.util.Collections;
import java.util.List;

public class OpenAiTranscriptSegment {
    private final int startMs;
    private final int endMs;
    private final String text;
    private final List<OpenAiTranscriptWord> words;

    public OpenAiTranscriptSegment(int startMs, int endMs, String text, List<OpenAiTranscriptWord> words) {
        this.startMs = startMs;
        this.endMs = endMs;
        this.text = text;
        this.words = words == null ? Collections.emptyList() : List.copyOf(words);
    }

    public int getStartMs() {
        return startMs;
    }

    public int getEndMs() {
        return endMs;
    }

    public String getText() {
        return text;
    }

    public List<OpenAiTranscriptWord> getWords() {
        return words;
    }
}
