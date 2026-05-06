package yass.integration.separation.mvsep;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record MvsepAlgorithmField(String serverKey, String text, List<MvsepFieldOption> options, String defaultKey) {
    private static final Pattern INSTRUM_VERSION_PATTERN = Pattern.compile("(?i)\\binstrum:\\s*([0-9]+(?:\\.[0-9]+)?)");

    public MvsepFieldOption findOption(String key) {
        if (key == null || options == null) {
            return null;
        }
        return options.stream().filter(o -> o.key().equalsIgnoreCase(key)).findFirst().orElse(null);
    }

    public MvsepFieldOption defaultOption() {
        return findOption(defaultKey);
    }

    public MvsepFieldOption preferredOption(String configuredKey) {
        MvsepFieldOption configured = findOption(configuredKey);
        if (configured != null) {
            return configured;
        }

        MvsepFieldOption latestInstrumental = latestInstrumentalOption();
        if (latestInstrumental != null) {
            return latestInstrumental;
        }

        MvsepFieldOption fallback = defaultOption();
        if (fallback != null) {
            return fallback;
        }
        return options == null || options.isEmpty() ? null : options.get(0);
    }

    public MvsepFieldOption latestInstrumentalOption() {
        if (options == null || options.isEmpty()) {
            return null;
        }

        MvsepFieldOption best = null;
        double bestVersion = Double.NEGATIVE_INFINITY;
        for (MvsepFieldOption option : options) {
            double version = extractInstrumentalVersion(option);
            if (version > bestVersion) {
                bestVersion = version;
                best = option;
            }
        }
        return bestVersion == Double.NEGATIVE_INFINITY ? null : best;
    }

    private static double extractInstrumentalVersion(MvsepFieldOption option) {
        if (option == null) {
            return Double.NEGATIVE_INFINITY;
        }

        double keyVersion = extractInstrumentalVersion(option.key());
        double valueVersion = extractInstrumentalVersion(option.value());
        return Math.max(keyVersion, valueVersion);
    }

    private static double extractInstrumentalVersion(String raw) {
        if (StringUtils.isBlank(raw)) {
            return Double.NEGATIVE_INFINITY;
        }
        Matcher matcher = INSTRUM_VERSION_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return Double.NEGATIVE_INFINITY;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return Double.NEGATIVE_INFINITY;
        }
    }
}
