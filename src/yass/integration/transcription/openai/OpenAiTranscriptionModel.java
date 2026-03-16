package yass.integration.transcription.openai;

import yass.YassEnum;

import java.util.List;

public enum OpenAiTranscriptionModel implements YassEnum {
    WHISPER_1("whisper-1", "whisper-1");

    private final String value;
    private final String label;

    OpenAiTranscriptionModel(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public static OpenAiTranscriptionModel fromValue(String value) {
        for (OpenAiTranscriptionModel model : values()) {
            if (model.value.equalsIgnoreCase(value)) {
                return model;
            }
        }
        return WHISPER_1;
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
