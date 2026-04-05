package yass.integration.transcription;

import yass.YassEnum;

import java.util.Arrays;
import java.util.List;

public enum TranscriptionEngine implements YassEnum {
    ONLINE_FIRST("online_first", "Online first (OpenAI, fall back to WhisperX)"),
    LOCAL_FIRST("local_first", "Local first (WhisperX, fall back to OpenAI)"),
    OPENAI("openai", "OpenAI only"),
    WHISPERX("whisperx", "WhisperX only");

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
        return ONLINE_FIRST;
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