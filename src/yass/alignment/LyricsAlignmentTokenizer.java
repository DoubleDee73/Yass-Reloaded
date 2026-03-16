package yass.alignment;

import org.apache.commons.lang3.StringUtils;
import yass.YassRow;
import yass.YassTable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LyricsAlignmentTokenizer {
    private LyricsAlignmentTokenizer() {
    }

    public static List<LyricToken> buildTokens(YassTable table) {
        List<LyricToken> tokens = new ArrayList<>();
        if (table == null) {
            return tokens;
        }

        StringBuilder currentRaw = new StringBuilder();
        List<Integer> currentRows = new ArrayList<>();
        int rowCount = table.getRowCount();
        for (int i = 0; i < rowCount; i++) {
            YassRow row = table.getRowAt(i);
            if (row == null || !row.isNote()) {
                continue;
            }
            String fragment = sanitizeFragment(row.getText());
            if (StringUtils.isBlank(fragment)) {
                continue;
            }

            appendFragment(currentRaw, fragment);
            currentRows.add(i);
            if (endsWord(fragment)) {
                addToken(tokens, currentRaw, currentRows);
            }
        }

        addToken(tokens, currentRaw, currentRows);
        return tokens;
    }

    public static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace(YassRow.SPACE, ' ')
                                .replace("’", "'")
                                .replace("`", "'")
                                .replaceAll("[~]+", "")
                                .replaceAll("[^\\p{L}\\p{N}' ]+", " ");
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKC)
                               .toLowerCase(Locale.ROOT)
                               .replaceAll("\\s+", " ")
                               .trim();
        return normalized;
    }

    private static void appendFragment(StringBuilder currentRaw, String fragment) {
        currentRaw.append(fragment.replace("~", ""));
    }

    private static boolean endsWord(String fragment) {
        if (StringUtils.isBlank(fragment)) {
            return false;
        }
        char last = fragment.charAt(fragment.length() - 1);
        return Character.isWhitespace(last) || ",.!?:;".indexOf(last) >= 0;
    }

    private static String sanitizeFragment(String text) {
        if (text == null) {
            return "";
        }
        String fragment = text.replace(YassRow.SPACE, ' ');
        if (StringUtils.isBlank(fragment)) {
            return "";
        }
        return fragment;
    }

    private static void addToken(List<LyricToken> tokens, StringBuilder currentRaw, List<Integer> currentRows) {
        if (currentRaw.length() == 0 || currentRows.isEmpty()) {
            currentRaw.setLength(0);
            currentRows.clear();
            return;
        }
        String raw = currentRaw.toString().trim();
        String normalized = normalizeText(raw);
        if (StringUtils.isNotBlank(normalized)) {
            tokens.add(new LyricToken(raw, normalized, new ArrayList<>(currentRows)));
        }
        currentRaw.setLength(0);
        currentRows.clear();
    }
}