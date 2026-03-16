package yass.integration.transcription.openai;

public class OpenAiTranscriptWord {
    private final String text;
    private final String normalizedText;
    private final int startMs;
    private final int endMs;

    public OpenAiTranscriptWord(String text, String normalizedText, int startMs, int endMs) {
        this.text = text;
        this.normalizedText = normalizedText;
        this.startMs = startMs;
        this.endMs = endMs;
    }

    public String getText() {
        return text;
    }

    public String getNormalizedText() {
        return normalizedText;
    }

    public int getStartMs() {
        return startMs;
    }

    public int getEndMs() {
        return endMs;
    }
}
