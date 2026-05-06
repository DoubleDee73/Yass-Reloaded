package yass;

import org.apache.commons.lang3.StringUtils;

import java.text.Normalizer;
import java.util.Locale;

public final class YassSearchNormalizer {
    private YassSearchNormalizer() {
    }

    public static String normalizeForSearch(String value) {
        String normalized = StringUtils.defaultString(value);
        normalized = normalized
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201A', '\'')
                .replace('\u201B', '\'')
                .replace('\u2032', '\'')
                .replace('\u02BC', '\'')
                .replace('\u0060', '\'')
                .replace('\u00B4', '\'');
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "");
        normalized = normalized.toLowerCase(Locale.ROOT);
        normalized = normalized.replace("'", "");
        normalized = normalized.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ");
        return StringUtils.normalizeSpace(normalized);
    }
}
