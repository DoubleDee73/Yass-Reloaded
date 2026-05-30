package yass;

import org.apache.commons.lang3.StringUtils;

import java.text.Normalizer;
import java.util.Locale;

public final class YassSearchNormalizer {
    private YassSearchNormalizer() {
    }

    public static String normalizeForSearch(String value) {
        String normalized = StringUtils.defaultString(value);
        normalized = foldSpecialLatinLetters(normalized);
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

    private static String foldSpecialLatinLetters(String value) {
        return value
                .replace("Æ", "AE")
                .replace("Ǽ", "AE")
                .replace("æ", "ae")
                .replace("ǽ", "ae")
                .replace("Œ", "OE")
                .replace("œ", "oe")
                .replace("Ø", "O")
                .replace("ø", "o")
                .replace("Ł", "L")
                .replace("ł", "l")
                .replace("Đ", "D")
                .replace("đ", "d")
                .replace("Ð", "D")
                .replace("ð", "d")
                .replace("Þ", "TH")
                .replace("þ", "th")
                .replace("Ħ", "H")
                .replace("ħ", "h")
                .replace("ı", "i")
                .replace("Ŋ", "N")
                .replace("ŋ", "n")
                .replace("ẞ", "SS")
                .replace("ß", "ss");
    }
}
