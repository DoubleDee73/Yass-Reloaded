package yass.integration.transcription.whisperx;

import yass.YassEnum;

import java.util.List;

public enum WhisperXDevice implements YassEnum {
    AUTO("auto", "Auto"),
    CPU("cpu", "CPU"),
    CUDA("cuda", "CUDA"),
    MPS("mps", "MPS");

    private final String value;
    private final String label;

    WhisperXDevice(String value, String label) {
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