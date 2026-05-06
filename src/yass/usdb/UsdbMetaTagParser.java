package yass.usdb;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public final class UsdbMetaTagParser {
    private UsdbMetaTagParser() {
    }

    public static UsdbParsedMetaTags parse(String videoTag) {
        if (StringUtils.isBlank(videoTag)) {
            return new UsdbParsedMetaTags("", Map.of());
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawPart : videoTag.split(",")) {
            String part = StringUtils.trimToEmpty(rawPart);
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            String[] keyValue = part.split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            String key = StringUtils.trimToEmpty(keyValue[0]);
            String value = StringUtils.trimToEmpty(keyValue[1]);
            if (StringUtils.isEmpty(key)) {
                continue;
            }
            values.put(key, value);
            if ("co-rotation".equalsIgnoreCase(key) && !values.containsKey("co-rotate")) {
                values.put("co-rotate", value);
            }
            if ("co-rotate".equalsIgnoreCase(key) && !values.containsKey("co-rotation")) {
                values.put("co-rotation", value);
            }
            if ("medley".equalsIgnoreCase(key)) {
                String[] parts = value.split("-");
                if (parts.length == 2) {
                    values.put("medley-start", StringUtils.trimToEmpty(parts[0]));
                    values.put("medley-end", StringUtils.trimToEmpty(parts[1]));
                }
            }
            if (key.endsWith("-crop")) {
                splitComposite(values, key, value, 4, new String[]{"left", "top", "width", "height"});
            } else if (key.endsWith("-resize")) {
                splitComposite(values, key, value, 2, new String[]{"width", "height"});
            }
        }
        return new UsdbParsedMetaTags(videoTag, values);
    }

    public static String buildCommentTag(String syncerVideoTag, String existingCommentTag) {
        String syncer = StringUtils.trimToEmpty(syncerVideoTag);
        String existing = StringUtils.trimToEmpty(existingCommentTag);
        if (StringUtils.isEmpty(syncer)) {
            return existing;
        }
        if (StringUtils.isEmpty(existing)) {
            return "#VIDEO:" + syncer;
        }
        return "#VIDEO:" + syncer + "|" + existing;
    }

    private static void splitComposite(Map<String, String> values, String key, String value, int expectedLength, String[] suffixes) {
        if (StringUtils.isBlank(value) || value.startsWith("-")) {
            return;
        }
        String[] parts = value.split("-");
        if (parts.length != expectedLength) {
            return;
        }
        for (int i = 0; i < suffixes.length; i++) {
            values.put(key + "-" + suffixes[i], StringUtils.trimToEmpty(parts[i]));
        }
    }

    public record UsdbParsedMetaTags(String originalTagLine, Map<String, String> values) {
        public boolean hasSyncerTags() {
            return !values.isEmpty();
        }

        public String get(String key) {
            return values.get(key);
        }
    }
}
