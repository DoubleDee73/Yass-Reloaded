package yass.integration.transcription.whisperx;

import yass.YassEnum;

import java.util.List;

public enum WhisperXComputeType implements YassEnum {
    AUTO("auto", "Auto"),
    INT8("int8", "int8"),
    FLOAT16("float16", "float16"),
    FLOAT32("float32", "float32");

    private final String value;
    private final String label;

    WhisperXComputeType(String value, String label) {
        this.value = value;
        this.label = label;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public List<YassEnum> listElements() {
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