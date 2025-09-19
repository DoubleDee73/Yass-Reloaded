/*
 * Yass Reloaded - Karaoke Editor
 * Copyright (C) 2009-2023 Saruta
 * Copyright (C) 2023 DoubleDee
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package yass.titlecase;

import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TitleCaseConverter {

    public static Pattern BLOCK_PATTERN = java.util.regex.Pattern.compile(
            "\\([^)]*\\)|\\[[^]]*\\]|[^()\\[\\]]+"
    );

    public static Pattern SENTENCE_PATTERN = java.util.regex.Pattern.compile(".*?[.!?](?=\\s+[A-Z\\(\\[])|.+$");

    private static final Set<String> SMALL_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "and", "as", "at", "but", "by", "for", "in", "nor",
            "of", "on", "or", "per", "so", "the", "to", "up", "via", "yet", "off", "out", "o'", "o’"
    ));

    private static final Set<String> ALWAYS_LOWERCASE = new HashSet<>(Arrays.asList(
            "feat.", "(feat.", "featuring", "(featuring", "(live", "(remix", "(from ", "(acoustic)"
    ));

    private static final Set<Character> FORCE_CAP_TRIGGERS = Set.of('(', '[', '{', '“', '‘', '"', ':', '–', '—');

    private static final Set<String> PRONOUNS = Set.of("me", "you", "him", "her", "it", "us", "them", "anybody",
                                                       "nobody", "somebody", "anyone", "noone", "someone");

    public static String toApTitleCase(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        List<String> blocks = splitText(input);
        StringBuilder title = new StringBuilder();
        for (String block : blocks) {
            if (block.length() == 1) {
                title.append(block);
                continue;
            }
            if (block.startsWith("(")) {
                title.append("(");
                block = block.substring(1);
            }
            title.append(processSentence(block));
        }
        return title.toString();
    }

    public static List<String> splitText(String input) {
        List<String> parts = new ArrayList<>();

        // Abkürzungen wie T.V., U.S.A., etc. extrahieren und durch Platzhalter ersetzen
        Map<String, String> abbreviationMap = new LinkedHashMap<>();
        Matcher abbrMatcher = Pattern.compile("(?:[A-Za-z]\\.){2,}[A-Za-z]?").matcher(input);
        int abbrIndex = 0;

        while (abbrMatcher.find()) {
            String abbr = abbrMatcher.group();
            String placeholder = "__ABBR" + abbrIndex++ + "__";
            abbreviationMap.put(placeholder, abbr);
            input = input.replace(abbr, placeholder);
        }

        // Haupt-Splitting auf bearbeitetem String
        Pattern pattern = Pattern.compile(
                "\\([^)]*\\)" +                          // (Inhalt)
                        "|\\[[^]]*\\]" +                         // [Inhalt]
                        "|/" +                                   // Slash
                        "|-" +                                   // Bindestrich
                        "|[.!?]" +                               // Satzzeichen
                        "| " +                                   // einzelnes Leerzeichen
                        "|[^\\s/\\-\\[\\](){}.!?]+(?: [^\\s/\\-\\[\\](){}.!?]+)*" // Wortgruppen
        );

        Matcher matcher = pattern.matcher(input);

        while (matcher.find()) {
            String token = matcher.group();
            if (!token.isEmpty()) {
                // Ersetze ggf. Platzhalter zurück in Abkürzungen
                for (Map.Entry<String, String> entry : abbreviationMap.entrySet()) {
                    token = token.replace(entry.getKey(), entry.getValue());
                }
                parts.add(token);
            }
        }

        return parts;
    }

    private static String processSentence(String input) {
        String[] words = input.trim().split("\\s+");
        String[] result = new String[words.length];

        for (int i = 0; i < words.length; i++) {
            String current = words[i];
            String prev = (i > 0) ? words[i - 1] : "";
            String prePrev = (i > 1) ? words[i - 2] : "";
            if ("(feat.".equals(prev)) {
                result[i] = current;
                continue;
            }
            boolean isFirst = (i == 0);
            boolean isLast = (i == words.length - 1);

            boolean followsSpecial =
                    endsWithSpecialChar(prev) || isStandaloneSpecialWord(prev);

            boolean forceCap = isFirst || isLast || followsSpecial || isPhrasalVerb(current.toLowerCase(),
                                                                                    prev.toLowerCase(),
                                                                                    prePrev.toLowerCase());

            result[i] = processWord(current, forceCap);
        }

        return String.join(" ", result);
    }

    private static boolean isPhrasalVerb(String current, String previous, String prePrevious) {
        if (PhrasalVerbManager.phrasalVerbs != null && !PhrasalVerbManager.phrasalVerbs.containsKey(current)) {
            return false;
        }
        StringJoiner expression = new StringJoiner(" ");
        if (StringUtils.isNotEmpty(prePrevious)) {
            expression.add(prePrevious);
        }
        if (StringUtils.isNotEmpty(previous)) {
            if (PRONOUNS.contains(previous)) {
                expression.add("<sb>");
            } else {
                expression.add(previous);
            }
        }
        expression.add(current);
        return PhrasalVerbManager.containsPhrasalVerb(current, expression.toString());
    }

    private static String processWord(String word, boolean capitalizeIfNeeded) {
        String wordLower = word.toLowerCase();

        if (ALWAYS_LOWERCASE.contains(wordLower)) {
            return wordLower;
        }

        if (isDotAbbreviation(word) || (word.equals(word.toUpperCase()) && word.length() > 1)) {
            return word;
        }
        if (word.contains("-")) {
            String[] parts = word.split("-");
            for (int i = 0; i < parts.length; i++) {
                parts[i] = capitalizeIfNeeded(parts[i], true);
            }
            return String.join("-", parts);
        }
        return capitalizeIfNeeded(word, capitalizeIfNeeded || !SMALL_WORDS.contains(wordLower));
    }

    private static String capitalizeIfNeeded(String word, boolean capitalize) {
        if (word.isBlank()) return word;

        if (isDotAbbreviation(word) || (word.equals(word.toUpperCase()) && word.length() > 1)) {
            return word;
        }

        if (capitalize) {
            if (!Character.isLetter(word.charAt(0)) && word.length() > 1) {
                return String.valueOf(word.charAt(0)) +
                        Character.toUpperCase(word.charAt(1)) +
                        word.substring(2).toLowerCase();
            } else {
                return Character.toUpperCase(word.charAt(0)) +
                        word.substring(1).toLowerCase();
            }
        } else {
            return word.toLowerCase();
        }
    }

    private static boolean isDotAbbreviation(String word) {
        return word.matches("([A-Z]\\.){2,}[A-Z]?(\\.)?");
    }

    private static boolean endsWithSpecialChar(String word) {
        if (word == null || word.isEmpty()) return false;
        char last = word.charAt(word.length() - 1);
        return FORCE_CAP_TRIGGERS.contains(last);
    }

    private static boolean isStandaloneSpecialWord(String word) {
        return word.length() == 1 && FORCE_CAP_TRIGGERS.contains(word.charAt(0));
    }

    public static String highlightDiff(String original, String modified) {
        String[] origWords = original.split("\\s+");
        String[] modWords = modified.split("\\s+");

        int[][] lcs = new int[origWords.length + 1][modWords.length + 1];
        for (int i = origWords.length - 1; i >= 0; i--) {
            for (int j = modWords.length - 1; j >= 0; j--) {
                if (origWords[i].equals(modWords[j])) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        StringBuilder result = new StringBuilder();
        int i = 0, j = 0;

        while (i < origWords.length && j < modWords.length) {
            if (origWords[i].equals(modWords[j])) {
                result.append(modWords[j]).append(" ");
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                i++;
            } else {
                result.append("<u>").append(modWords[j]).append("</u> ");
                j++;
            }
        }

        while (j < modWords.length) {
            result.append("<u>").append(modWords[j]).append("</u> ");
            j++;
        }

        return result.toString().trim();
    }

}
