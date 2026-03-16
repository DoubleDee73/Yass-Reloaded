package yass.integration.transcription;

import yass.YassEnum;

import java.util.Arrays;
import java.util.List;

public enum TranscriptionEngine implements YassEnum {
    OPENAI("openai", "OpenAI"),
    WHISPERX("whisperx", "WhisperX");

    private final String value;
    private final String label;

    TranscriptionEngine(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public static TranscriptionEngine fromValue(String value) {
        for (TranscriptionEngine engine : values()) {
            if (engine.value.equalsIgnoreCase(value)) {
                return engine;
            }
        }
        return OPENAI;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public List<YassEnum> listElements() {
        return Arrays.asList(values());
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return getLabel();
    }
}