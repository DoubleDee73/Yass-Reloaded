package yass.integration.separation;

import yass.YassEnum;

import java.util.Arrays;
import java.util.List;

public enum SeparationPreference implements YassEnum {
    LOCAL_FIRST("local_first", "Local first (audio-separator, fall back to MVSEP)"),
    ONLINE_FIRST("online_first", "Online first (MVSEP, fall back to audio-separator)"),
    MVSEP_ONLY("mvsep_only", "MVSEP only"),
    AUDIO_SEP_ONLY("audio_sep_only", "audio-separator only");

    private final String value;
    private final String label;

    SeparationPreference(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public static SeparationPreference fromValue(String value) {
        for (SeparationPreference p : values()) {
            if (p.value.equalsIgnoreCase(value)) {
                return p;
            }
        }
        return LOCAL_FIRST;
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
        return label;
    }
}
