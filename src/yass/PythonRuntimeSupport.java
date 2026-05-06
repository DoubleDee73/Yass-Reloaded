package yass;

import org.apache.commons.lang3.StringUtils;

import java.io.File;

public final class PythonRuntimeSupport {
    public static final String PROP_DEFAULT_PYTHON = "default-python";

    private PythonRuntimeSupport() {
    }

    public static String resolveToolPython(YassProperties properties, String toolPythonProperty) {
        if (properties == null) {
            return "";
        }
        String toolPython = StringUtils.trimToEmpty(properties.getProperty(toolPythonProperty));
        if (StringUtils.isNotBlank(toolPython)) {
            return toolPython;
        }
        return StringUtils.trimToEmpty(properties.getProperty(PROP_DEFAULT_PYTHON));
    }

    public static boolean hasToolPython(YassProperties properties, String toolPythonProperty) {
        return StringUtils.isNotBlank(resolveToolPython(properties, toolPythonProperty));
    }

    public static boolean shouldAutodetectDefaultPython(String configuredValue) {
        String trimmed = StringUtils.trimToEmpty(configuredValue);
        return trimmed.isBlank() || isAliasLike(trimmed);
    }

    public static boolean shouldCanonicalizeToolPython(String configuredValue) {
        String trimmed = StringUtils.trimToEmpty(configuredValue);
        return !trimmed.isBlank() && isAliasLike(trimmed);
    }

    public static boolean isAliasLike(String configuredValue) {
        String trimmed = StringUtils.trimToEmpty(configuredValue);
        if (trimmed.isBlank()) {
            return false;
        }
        File file = new File(trimmed);
        if (file.isAbsolute()) {
            return false;
        }
        return !trimmed.contains("/") && !trimmed.contains("\\");
    }
}
