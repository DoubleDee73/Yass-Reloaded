package yass.wizard;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

final class YouTubeMetadataParser {
    private static final Pattern BRACKETED_NOISE = Pattern.compile(
            "\\s*[\\[(](?i:(official(\\s+music)?\\s+video|lyrics?|audio|hd|4k|visualizer|music\\s+video))[\\])]\\s*");
    private static final Pattern TRAILING_NOISE = Pattern.compile(
            "(?i)\\b(official(\\s+music)?\\s+video|lyrics?|audio|hd|4k|visualizer|music\\s+video)\\b");

    private YouTubeMetadataParser() {
    }

    static Metadata inferMetadata(String rawValue, String youTubeId) {
        String normalized = normalizeBaseName(rawValue, youTubeId);
        if (isTechnicalOrEmpty(normalized, youTubeId)) {
            return Metadata.empty();
        }

        String[] parts = normalized.split("\\s+-\\s+", 2);
        if (parts.length == 2) {
            String artist = cleanupPart(parts[0]);
            String title = cleanupTitle(parts[1], artist);
            if (looksUseful(artist) && looksUseful(title)) {
                return new Metadata(artist, title);
            }
            if (!looksUseful(title)) {
                return Metadata.empty();
            }
            return new Metadata("", title);
        }

        String title = cleanupTitle(normalized, "");
        if (!looksUseful(title)) {
            return Metadata.empty();
        }
        return new Metadata("", title);
    }

    static Metadata repairStoredMetadata(String storedArtist, String storedTitle, String youTubeId) {
        Metadata repaired = inferMetadata(
                StringUtils.trimToEmpty(storedArtist) + " - " + StringUtils.trimToEmpty(storedTitle),
                youTubeId);
        if (StringUtils.isNotBlank(repaired.getArtist()) || StringUtils.isNotBlank(repaired.getTitle())) {
            return repaired;
        }
        return new Metadata(cleanupPart(storedArtist), cleanupTitle(storedTitle, cleanupPart(storedArtist)));
    }

    static String normalizeBaseName(String baseName, String youTubeId) {
        String normalized = StringUtils.defaultString(baseName).trim();
        int dot = normalized.lastIndexOf('.');
        if (dot > 0) {
            normalized = normalized.substring(0, dot);
        }
        normalized = normalized.replaceAll("\\.v_.*?\\.a_.*?$", "");
        normalized = normalized.replaceAll("\\.(f\\d+|NA)$", "");
        normalized = normalized.replaceAll("\\s+-\\s+subtitles(?:-auto)?$", "");
        normalized = normalized.replaceAll(",[a-z]{2}(?:-[A-Za-z0-9_-]{8,})?$", "");
        normalized = normalized.replaceAll("\\.[a-z]{2}(?:-[A-Za-z0-9_-]{8,})?$", "");
        if (StringUtils.isNotBlank(youTubeId)) {
            normalized = normalized.replace("." + youTubeId, "");
            normalized = normalized.replace(youTubeId + ".", "");
            normalized = normalized.replaceFirst("^" + Pattern.quote(youTubeId) + "\\s*-\\s*", "");
            normalized = normalized.replaceFirst("^" + Pattern.quote(youTubeId) + "[-_.]", "");
        }
        return normalized.trim();
    }

    private static boolean isTechnicalOrEmpty(String value, String youTubeId) {
        String normalized = StringUtils.trimToEmpty(value);
        if (StringUtils.isBlank(normalized)) {
            return true;
        }
        if (StringUtils.isNotBlank(youTubeId) && normalized.equalsIgnoreCase(youTubeId)) {
            return true;
        }
        String cleaned = cleanupPart(normalized);
        if (StringUtils.isBlank(cleaned)) {
            return true;
        }
        return cleaned.matches("(?i)(official(\\s+music)?\\s+video|lyrics?|audio|hd|4k|visualizer)+");
    }

    private static String cleanupTitle(String value, String artist) {
        String title = cleanupPart(value);
        if (StringUtils.isBlank(title)) {
            return "";
        }
        if (StringUtils.isNotBlank(artist)) {
            title = title.replaceFirst("(?i)^" + Pattern.quote(artist) + "\\s*-\\s*", "");
        }
        return title.trim();
    }

    private static String cleanupPart(String value) {
        String cleaned = StringUtils.trimToEmpty(value);
        cleaned = BRACKETED_NOISE.matcher(cleaned).replaceAll(" ");
        cleaned = TRAILING_NOISE.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll("[_]+", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private static boolean looksUseful(String value) {
        String cleaned = cleanupPart(value);
        if (StringUtils.isBlank(cleaned)) {
            return false;
        }
        if (cleaned.matches("(?i)[A-Za-z0-9_-]{11}")) {
            return false;
        }
        return cleaned.matches(".*[A-Za-z].*");
    }

    static final class Metadata {
        private final String artist;
        private final String title;

        Metadata(String artist, String title) {
            this.artist = StringUtils.defaultString(artist).trim();
            this.title = StringUtils.defaultString(title).trim();
        }

        static Metadata empty() {
            return new Metadata("", "");
        }

        String getArtist() {
            return artist;
        }

        String getTitle() {
            return title;
        }
    }
}
