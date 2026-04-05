package yass.integration.transcription.whisperx;

import yass.YassEnum;

import java.util.Arrays;
import java.util.List;

public enum WhisperXModel implements YassEnum {
    AUTO("auto", "Auto"),
    BASE("base", "base"),
    SMALL("small", "small"),
    MEDIUM("medium", "medium"),
    LARGE_V2("large-v2", "large-v2"),
    LARGE_V3("large-v3", "large-v3");

    private final String value;
    private final String label;

    WhisperXModel(String value, String label) {
        this.value = value;
        this.label = label;
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
