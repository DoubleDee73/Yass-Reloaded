package yass.integration.separation.mvsep;

import java.util.List;

public record MvsepAlgorithmField(String serverKey, String text, List<MvsepFieldOption> options, String defaultKey) {
    public MvsepFieldOption findOption(String key) {
        if (key == null || options == null) {
            return null;
        }
        return options.stream().filter(o -> o.key().equalsIgnoreCase(key)).findFirst().orElse(null);
    }

    public MvsepFieldOption defaultOption() {
        return findOption(defaultKey);
    }
}
