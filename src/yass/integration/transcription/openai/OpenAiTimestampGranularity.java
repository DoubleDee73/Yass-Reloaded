package yass.integration.transcription.openai;

import yass.YassEnum;

import java.util.List;

public enum OpenAiTimestampGranularity implements YassEnum {
    WORD("word", "Word"),
    SEGMENT("segment", "Segment");

    private final String value;
    private final String label;

    OpenAiTimestampGranularity(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public static OpenAiTimestampGranularity fromValue(String value) {
        for (OpenAiTimestampGranularity granularity : values()) {
            if (granularity.value.equalsIgnoreCase(value)) {
                return granularity;
            }
        }
        return WORD;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public List<yass.YassEnum> listElements() {
        return List.of(values());
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
