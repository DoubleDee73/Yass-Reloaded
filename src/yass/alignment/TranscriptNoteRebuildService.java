package yass.alignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;

import yass.YassProperties;
import yass.YassRow;
import yass.YassTable;
import yass.integration.transcription.openai.OpenAiTranscriptSegment;
import yass.integration.transcription.openai.OpenAiTranscriptWord;
import yass.integration.transcription.openai.OpenAiTranscriptionResult;

public class TranscriptNoteRebuildService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final int DEFAULT_PITCH = 6;
    private static final int MIN_NOTE_LENGTH = 1;
    private static final int HARD_PAUSE_THRESHOLD_MS = 1200;
    private static final int MEDIUM_PAUSE_THRESHOLD_MS = 700;
    private static final int SOFT_BREAK_WORD_COUNT = 10;
    private static final int TARGET_BREAK_WORD_COUNT_MIN = 10;
    private static final int TARGET_BREAK_WORD_COUNT_MAX = 12;
    private static final int MAX_WORDS_PER_PHRASE = 20;
    private static final int MAX_SEGMENT_DURATION_MS = 20_000;
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.2d;
    private static final String SOFT_HYPHEN = "\u00AD";

    public TranscriptRebuildResult transcript(YassTable table, OpenAiTranscriptionResult transcriptionResult) {
        List<List<OpenAiTranscriptWord>> phrases = collectPhrases(transcriptionResult);
        if (phrases.isEmpty()) {
            throw new IllegalArgumentException("No transcript segments available for rebuild.");
        }

        int gapMs = roundToNearestTen(findInitialGap(collectSegments(transcriptionResult)));
        List<String> rebuiltRows = createRows(phrases, table, gapMs);
        if (rebuiltRows.isEmpty()) {
            throw new IllegalStateException("Transcript rebuild produced no note rows.");
        }

        table.addUndo();
        boolean oldUndo = table.getPreventUndo();
        table.setPreventUndo(true);
        table.removeNotes();
        table.setPreventUndo(oldUndo);
        pruneEmptyNoteRows(table);
        table.setGap(gapMs);
        table.setSaved(false);
        logTableNotes(table);

        int noteCount = 0;
        int pageBreakCount = 0;
        for (String row : rebuiltRows) {
            YassRow parsed = new YassRow(row);
            if (parsed.isNote()) {
                noteCount++;
            } else if (parsed.isPageBreak()) {
                pageBreakCount++;
            }
            table.addRow(parsed);
        }
        table.addRow("E");
        normalizeTiming(table);
        LOGGER.info("Rebuilt " + noteCount + " notes, " + pageBreakCount + " page breaks from transcript with gap " + gapMs + " ms.");
        return new TranscriptRebuildResult(noteCount, pageBreakCount, gapMs);
    }

    public Map<Integer, String> deriveDisplayPhrases(OpenAiTranscriptionResult transcriptionResult) {
        Map<Integer, String> phrases = new LinkedHashMap<>();
        for (List<OpenAiTranscriptWord> phraseWords : collectPhrases(transcriptionResult)) {
            if (phraseWords.isEmpty()) {
                continue;
            }
            OpenAiTranscriptWord firstWord = phraseWords.get(0);
            phrases.put(firstWord.getStartMs(), joinPhraseText(phraseWords));
        }
        return phrases;
    }

    private List<List<OpenAiTranscriptWord>> collectPhrases(OpenAiTranscriptionResult transcriptionResult) {
        List<List<OpenAiTranscriptWord>> phrases = new ArrayList<>();
        if (hasUsableSegments(transcriptionResult)) {
            List<OpenAiTranscriptSegment> segments = collectSegments(transcriptionResult);
            for (OpenAiTranscriptSegment segment : segments) {
                phrases.addAll(splitSegmentIntoPhrases(segment));
            }
            return phrases;
        }
        List<OpenAiTranscriptSegment> segments = collectSegments(transcriptionResult);
        for (OpenAiTranscriptSegment segment : segments) {
            phrases.addAll(splitIntoPhrases(segment.getWords()));
        }
        return phrases;
    }

    private boolean hasUsableSegments(OpenAiTranscriptionResult transcriptionResult) {
        if (transcriptionResult == null || transcriptionResult.getSegments() == null) {
            return false;
        }
        for (OpenAiTranscriptSegment segment : transcriptionResult.getSegments()) {
            if (segment != null && containsUsableWords(segment.getWords())) {
                return true;
            }
        }
        return false;
    }

    private List<List<OpenAiTranscriptWord>> splitSegmentIntoPhrases(OpenAiTranscriptSegment segment) {
        if (segment == null || !containsUsableWords(segment.getWords())) {
            return Collections.emptyList();
        }
        if (segmentDurationMs(segment) <= MAX_SEGMENT_DURATION_MS) {
            List<OpenAiTranscriptWord> words = collectUsableWords(segment.getWords());
            return words.isEmpty() ? Collections.emptyList() : List.of(words);
        }
        return splitIntoPhrases(segment.getWords());
    }

    private List<OpenAiTranscriptSegment> collectSegments(OpenAiTranscriptionResult transcriptionResult) {
        List<OpenAiTranscriptSegment> segments = new ArrayList<>();
        if (transcriptionResult.getSegments() != null) {
            for (OpenAiTranscriptSegment segment : transcriptionResult.getSegments()) {
                if (segment != null && containsUsableWords(segment.getWords())) {
                    segments.add(segment);
                }
            }
        }
        if (!segments.isEmpty()) {
            return segments;
        }
        if (transcriptionResult.getWords() != null && !transcriptionResult.getWords().isEmpty()) {
            segments.add(new OpenAiTranscriptSegment(findInitialGapFromWords(transcriptionResult.getWords()),
                    findFinalEnd(transcriptionResult.getWords()),
                    transcriptionResult.getTranscriptText(),
                    transcriptionResult.getWords()));
        }
        return segments;
    }

    private List<List<OpenAiTranscriptWord>> splitIntoPhrases(List<OpenAiTranscriptWord> words) {
        if (words == null || words.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<OpenAiTranscriptWord>> phrases = new ArrayList<>();
        List<OpenAiTranscriptWord> currentPhrase = new ArrayList<>();
        for (int index = 0; index < words.size(); index++) {
            OpenAiTranscriptWord currentWord = words.get(index);
            if (currentWord == null || StringUtils.isBlank(cleanWord(currentWord.getText()))) {
                continue;
            }
            currentPhrase.add(currentWord);
            if (index >= words.size() - 1) {
                continue;
            }
            OpenAiTranscriptWord nextWord = words.get(index + 1);
            if (shouldBreakPhrase(currentPhrase, currentWord, nextWord)) {
                phrases.add(new ArrayList<>(currentPhrase));
                currentPhrase.clear();
            }
        }
        if (!currentPhrase.isEmpty()) {
            phrases.add(currentPhrase);
        }
        return phrases;
    }

    private List<OpenAiTranscriptWord> collectUsableWords(List<OpenAiTranscriptWord> words) {
        List<OpenAiTranscriptWord> usableWords = new ArrayList<>();
        if (words == null) {
            return usableWords;
        }
        for (OpenAiTranscriptWord word : words) {
            if (word != null && StringUtils.isNotBlank(cleanWord(word.getText()))) {
                usableWords.add(word);
            }
        }
        return usableWords;
    }

    private boolean shouldBreakPhrase(List<OpenAiTranscriptWord> currentPhrase,
                                      OpenAiTranscriptWord currentWord,
                                      OpenAiTranscriptWord nextWord) {
        if (currentPhrase.isEmpty()) {
            return false;
        }
        if (pauseAfter(currentWord, nextWord) >= HARD_PAUSE_THRESHOLD_MS) {
            return true;
        }
        if (currentPhrase.size() >= MAX_WORDS_PER_PHRASE) {
            return true;
        }
        if (currentPhrase.size() < SOFT_BREAK_WORD_COUNT) {
            return false;
        }
        return scoreSoftBreak(currentPhrase, currentWord, nextWord) >= 3;
    }

    private int scoreSoftBreak(List<OpenAiTranscriptWord> currentPhrase,
                               OpenAiTranscriptWord currentWord,
                               OpenAiTranscriptWord nextWord) {
        String currentText = cleanWord(currentWord.getText());
        String nextText = cleanWord(nextWord != null ? nextWord.getText() : "");
        int score = 0;
        if (endsWithStrongPunctuation(currentText)) {
            score += 4;
        } else if (endsWithComma(currentText)) {
            score += 1;
        }
        if (pauseAfter(currentWord, nextWord) >= MEDIUM_PAUSE_THRESHOLD_MS) {
            score += 3;
        }
        if (isLikelyPhraseStart(nextText)) {
            score += 2;
        }
        int wordCount = currentPhrase.size();
        if (wordCount >= TARGET_BREAK_WORD_COUNT_MIN && wordCount <= TARGET_BREAK_WORD_COUNT_MAX) {
            score += 2;
        } else if (wordCount == TARGET_BREAK_WORD_COUNT_MIN - 1 || wordCount == TARGET_BREAK_WORD_COUNT_MAX + 1) {
            score += 1;
        }
        return score;
    }

    private int pauseAfter(OpenAiTranscriptWord currentWord, OpenAiTranscriptWord nextWord) {
        if (currentWord == null || nextWord == null) {
            return 0;
        }
        return Math.max(0, nextWord.getStartMs() - currentWord.getEndMs());
    }

    private boolean endsWithStrongPunctuation(String text) {
        return StringUtils.endsWithAny(StringUtils.trimToEmpty(text), ".", "!", "?", ":", ";");
    }

    private boolean endsWithComma(String text) {
        return StringUtils.endsWith(StringUtils.trimToEmpty(text), ",");
    }

    private boolean isLikelyPhraseStart(String word) {
        if (StringUtils.isBlank(word)) {
            return false;
        }
        String trimmedWord = StringUtils.trim(word);
        if ("I".equals(trimmedWord)
                || "I'm".equals(trimmedWord)
                || "I'll".equals(trimmedWord)
                || "I've".equals(trimmedWord)
                || "I'd".equals(trimmedWord)) {
            return false;
        }
        return Character.isUpperCase(trimmedWord.charAt(0));
    }

    private List<String> createRows(List<List<OpenAiTranscriptWord>> phrases,
                                    YassTable table,
                                    int gapMs) {
        List<String> rows = new ArrayList<>();
        YassProperties properties = table.getProperties();
        boolean spacingAfter = properties != null && properties.isUncommonSpacingAfter();
        double bpm = table.getBPM();
        Integer previousNoteBeat = null;
        int previousNoteIndex = -1;
        int nextAllowedBeat = 0;

        for (List<OpenAiTranscriptWord> words : phrases) {
            if (words == null || words.isEmpty()) {
                continue;
            }
            List<OpenAiTranscriptWord> effectiveWords = adjustLowConfidenceWordTimings(words);

            if (!rows.isEmpty()) {
                int firstWordBeat = msToBeat(effectiveWords.get(0).getStartMs(), gapMs, bpm);
                // Place page break halfway between previous note end and first word of this segment,
                // but never at or after the first word beat (note must start there)
                int midpoint = nextAllowedBeat + Math.max(0, (firstWordBeat - nextAllowedBeat) / 2);
                int pageBeat = Math.min(midpoint, Math.max(nextAllowedBeat, firstWordBeat - 1));
                rows.add("-\t" + Math.max(0, pageBeat));
                previousNoteBeat = null;
                previousNoteIndex = -1;
                nextAllowedBeat = pageBeat + 1;
            }

            for (OpenAiTranscriptWord word : effectiveWords) {
                if (word == null) {
                    continue;
                }
                String cleanedWord = cleanWord(word.getText());
                if (StringUtils.isBlank(cleanedWord)) {
                    continue;
                }

                int startBeat = Math.max(nextAllowedBeat, msToBeat(word.getStartMs(), gapMs, bpm));
                int endBeat = msToBeatFloor(word.getEndMs(), gapMs, bpm);
                int length = Math.max(MIN_NOTE_LENGTH, endBeat - startBeat);
                List<NotePart> noteParts = createWordParts(table, cleanedWord, startBeat, length, spacingAfter);

                if (noteParts.isEmpty()) {
                    continue;
                }

                if (previousNoteIndex >= 0 && previousNoteBeat != null) {
                    tightenPreviousNote(rows, previousNoteIndex, previousNoteBeat, noteParts.get(0).beat);
                }

                for (NotePart notePart : noteParts) {
                    rows.add(createNoteRow(notePart.beat, notePart.length, notePart.text));
                    previousNoteBeat = notePart.beat;
                    previousNoteIndex = rows.size() - 1;
                    nextAllowedBeat = notePart.beat + notePart.length + 1;
                }
            }
        }

        return rows;
    }

    private List<OpenAiTranscriptWord> adjustLowConfidenceWordTimings(List<OpenAiTranscriptWord> words) {
        if (words == null || words.isEmpty()) {
            return Collections.emptyList();
        }
        List<OpenAiTranscriptWord> adjusted = new ArrayList<>(words);
        int index = 0;
        while (index < adjusted.size()) {
            if (!isLowConfidence(adjusted.get(index))) {
                index++;
                continue;
            }
            int clusterStart = index;
            while (index + 1 < adjusted.size() && isLowConfidence(adjusted.get(index + 1))) {
                index++;
            }
            int clusterEnd = index;
            repositionLowConfidenceCluster(adjusted, clusterStart, clusterEnd);
            index++;
        }
        return adjusted;
    }

    private void repositionLowConfidenceCluster(List<OpenAiTranscriptWord> words, int clusterStart, int clusterEnd) {
        int previousStableIndex = findPreviousStableWord(words, clusterStart);
        int nextStableIndex = findNextStableWord(words, clusterEnd);
        if (previousStableIndex < 0 && nextStableIndex < 0) {
            return;
        }

        int originalStartMs = words.get(clusterStart).getStartMs();
        int originalEndMs = words.get(clusterEnd).getEndMs();
        int totalDurationMs = Math.max(1, originalEndMs - originalStartMs);

        int targetStartMs = originalStartMs;
        int targetEndMs = originalEndMs;
        if (previousStableIndex >= 0 && nextStableIndex >= 0) {
            int prevEndMs = words.get(previousStableIndex).getEndMs();
            int nextStartMs = words.get(nextStableIndex).getStartMs();
            if (nextStartMs <= prevEndMs) {
                return;
            }
            targetStartMs = prevEndMs;
            targetEndMs = nextStartMs;
        } else if (previousStableIndex >= 0) {
            int prevEndMs = words.get(previousStableIndex).getEndMs();
            int gapMs = Math.max(0, originalStartMs - prevEndMs);
            if (gapMs <= 0) {
                return;
            }
            int shiftMs = Math.max(120, gapMs / 3);
            shiftMs = Math.min(shiftMs, Math.max(0, originalStartMs - prevEndMs));
            targetStartMs = prevEndMs + shiftMs;
            targetEndMs = targetStartMs + totalDurationMs;
        } else {
            int nextStartMs = words.get(nextStableIndex).getStartMs();
            int gapMs = Math.max(0, nextStartMs - originalEndMs);
            if (gapMs <= 0) {
                return;
            }
            int shiftMs = Math.max(120, gapMs / 3);
            shiftMs = Math.min(shiftMs, Math.max(0, nextStartMs - originalEndMs));
            targetEndMs = nextStartMs - shiftMs;
            targetStartMs = targetEndMs - totalDurationMs;
        }

        int availableSpanMs = Math.max(totalDurationMs, targetEndMs - targetStartMs);
        if (availableSpanMs <= 0) {
            return;
        }
        double scale = (double) availableSpanMs / totalDurationMs;
        for (int index = clusterStart; index <= clusterEnd; index++) {
            OpenAiTranscriptWord original = words.get(index);
            int relativeStartMs = original.getStartMs() - originalStartMs;
            int relativeEndMs = original.getEndMs() - originalStartMs;
            int adjustedStartMs = targetStartMs + (int) Math.round(relativeStartMs * scale);
            int adjustedEndMs = targetStartMs + (int) Math.round(relativeEndMs * scale);
            adjustedEndMs = Math.max(adjustedStartMs + 1, adjustedEndMs);
            words.set(index, new OpenAiTranscriptWord(
                    original.getText(),
                    original.getNormalizedText(),
                    adjustedStartMs,
                    adjustedEndMs,
                    original.getScore()));
        }
    }

    private int findPreviousStableWord(List<OpenAiTranscriptWord> words, int startIndex) {
        for (int index = startIndex - 1; index >= 0; index--) {
            if (!isLowConfidence(words.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private int findNextStableWord(List<OpenAiTranscriptWord> words, int startIndex) {
        for (int index = startIndex + 1; index < words.size(); index++) {
            if (!isLowConfidence(words.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private boolean isLowConfidence(OpenAiTranscriptWord word) {
        return word != null && word.getScore() != null && word.getScore() < LOW_CONFIDENCE_THRESHOLD;
    }

    private List<NotePart> createWordParts(YassTable table,
                                           String cleanedWord,
                                           int startBeat,
                                           int length,
                                           boolean spacingAfter) {
        List<String> syllables = splitWordIntoSyllables(table, cleanedWord, length);
        if (syllables.size() < 2) {
            return List.of(new NotePart(startBeat, length, applyWordSpacing(cleanedWord, spacingAfter)));
        }

        int totalGapBeats = syllables.size() - 1;
        int usableBeats = length - totalGapBeats;
        if (usableBeats < syllables.size()) {
            return List.of(new NotePart(startBeat, length, applyWordSpacing(cleanedWord, spacingAfter)));
        }

        List<Integer> noteLengths = distributeEvenly(usableBeats, syllables.size());
        List<NotePart> noteParts = new ArrayList<>();
        int currentBeat = startBeat;
        for (int index = 0; index < syllables.size(); index++) {
            noteParts.add(new NotePart(currentBeat,
                    noteLengths.get(index),
                    applySyllableSpacing(syllables, index, spacingAfter)));
            currentBeat += noteLengths.get(index) + 1;
        }
        return noteParts;
    }

    private List<String> splitWordIntoSyllables(YassTable table, String cleanedWord, int length) {
        if (table == null || table.getHyphenator() == null || length < 3) {
            return Collections.emptyList();
        }
        String hyphenated = table.getHyphenator().hyphenateWord(cleanedWord);
        if (!StringUtils.contains(hyphenated, SOFT_HYPHEN)) {
            return Collections.emptyList();
        }
        List<String> syllables = new ArrayList<>();
        for (String syllable : hyphenated.split(SOFT_HYPHEN)) {
            if (StringUtils.isNotBlank(syllable)) {
                syllables.add(syllable);
            }
        }
        if (syllables.size() < 2) {
            return Collections.emptyList();
        }
        if (length == 3 && syllables.size() >= 3) {
            StringBuilder remaining = new StringBuilder();
            for (int index = 1; index < syllables.size(); index++) {
                remaining.append(syllables.get(index));
            }
            return List.of(syllables.get(0), remaining.toString());
        }
        return syllables;
    }

    private int countVowels(String word) {
        int vowels = 0;
        for (char ch : StringUtils.defaultString(word).toCharArray()) {
            if ("aeiouyAEIOUYäöüÄÖÜáéíóúÁÉÍÓÚàèìòùÀÈÌÒÙ".indexOf(ch) >= 0) {
                vowels++;
            }
        }
        return vowels;
    }

    private List<Integer> distributeEvenly(int total, int parts) {
        List<Integer> distribution = new ArrayList<>();
        int base = total / parts;
        int remainder = total % parts;
        for (int index = 0; index < parts; index++) {
            distribution.add(base + (index < remainder ? 1 : 0));
        }
        return distribution;
    }

    private String applySyllableSpacing(List<String> syllables, int index, boolean spacingAfter) {
        String syllable = syllables.get(index);
        if (!spacingAfter && index == 0) {
            return String.valueOf(YassRow.SPACE) + syllable;
        }
        if (spacingAfter && index == syllables.size() - 1) {
            return syllable + YassRow.SPACE;
        }
        return syllable;
    }

    private String joinPhraseText(List<OpenAiTranscriptWord> phraseWords) {
        List<String> words = new ArrayList<>();
        for (OpenAiTranscriptWord word : phraseWords) {
            String cleanedWord = cleanWord(word.getText());
            if (StringUtils.isNotBlank(cleanedWord)) {
                words.add(cleanedWord);
            }
        }
        return String.join(" ", words).trim();
    }

    private String createNoteRow(int beat, int length, String text) {
        StringJoiner joiner = new StringJoiner("\t");
        joiner.add(":");
        joiner.add(Integer.toString(Math.max(0, beat)));
        joiner.add(Integer.toString(Math.max(MIN_NOTE_LENGTH, length)));
        joiner.add(Integer.toString(DEFAULT_PITCH));
        joiner.add(StringUtils.defaultString(text));
        return joiner.toString();
    }

    private String cleanWord(String text) {
        return StringUtils.trimToEmpty(text)
                .replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .replace('\u00A0', ' ')
                .replace("\t", " ")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    private String applyWordSpacing(String word, boolean spacingAfter) {
        if (StringUtils.isBlank(word)) {
            return word;
        }
        String space = String.valueOf(YassRow.SPACE);
        return spacingAfter ? (word + space) : (space + word);
    }

    private void tightenPreviousNote(List<String> rows, int previousIndex, int previousBeat, int currentBeat) {
        if (previousIndex < 0 || previousIndex >= rows.size() || currentBeat <= previousBeat) {
            return;
        }
        YassRow previousRow = new YassRow(rows.get(previousIndex));
        if (!previousRow.isNote()) {
            return;
        }
        // Leave at least 1 beat gap between notes; minimum length is 1
        int maxLength = Math.max(MIN_NOTE_LENGTH, currentBeat - previousBeat - 1);
        if (previousRow.getLengthInt() > maxLength) {
            rows.set(previousIndex, createNoteRow(previousBeat, maxLength, previousRow.getText()));
        }
    }

    private boolean containsUsableWords(List<OpenAiTranscriptWord> words) {
        if (words == null) {
            return false;
        }
        for (OpenAiTranscriptWord word : words) {
            if (word != null && StringUtils.isNotBlank(cleanWord(word.getText()))) {
                return true;
            }
        }
        return false;
    }

    private int findInitialGap(List<OpenAiTranscriptSegment> segments) {
        for (OpenAiTranscriptSegment segment : segments) {
            if (segment == null) {
                continue;
            }
            for (OpenAiTranscriptWord word : segment.getWords()) {
                if (word != null && StringUtils.isNotBlank(cleanWord(word.getText())) && word.getStartMs() >= 0) {
                    return word.getStartMs();
                }
            }
            if (segment.getStartMs() >= 0) {
                return segment.getStartMs();
            }
        }
        return 0;
    }

    private int findInitialGapFromWords(List<OpenAiTranscriptWord> words) {
        for (OpenAiTranscriptWord word : words) {
            if (word != null && StringUtils.isNotBlank(cleanWord(word.getText())) && word.getStartMs() >= 0) {
                return word.getStartMs();
            }
        }
        return 0;
    }

    private int findFinalEnd(List<OpenAiTranscriptWord> words) {
        int end = 0;
        for (OpenAiTranscriptWord word : words) {
            if (word != null) {
                end = Math.max(end, Math.max(word.getStartMs(), word.getEndMs()));
            }
        }
        return end;
    }

    private int segmentDurationMs(OpenAiTranscriptSegment segment) {
        if (segment == null) {
            return 0;
        }
        int startMs = segment.getStartMs();
        int endMs = segment.getEndMs();
        if (startMs >= 0 && endMs >= startMs) {
            return endMs - startMs;
        }
        return Math.max(0, findFinalEnd(segment.getWords()) - findInitialGapFromWords(segment.getWords()));
    }

    private void logTableNotes(YassTable table) {
        StringBuilder sb = new StringBuilder("Table notes after rebuild:\n");
        for (int i = 0; i < table.getRowCount(); i++) {
            YassRow row = table.getRowAt(i);
            if (row != null && (row.isNote() || row.isPageBreak())) {
                sb.append("  row[").append(i).append("] ").append(row.toString()).append('\n');
            }
        }
        LOGGER.info(sb.toString());
    }

    private int roundToNearestTen(int value) {
        return (int) (Math.round(value / 10.0d) * 10);
    }

    private int msToBeat(int ms, int gapMs, double bpm) {
        return (int) Math.max(0, Math.round(YassTable.msToBeatExact(ms, gapMs, bpm)));
    }

    private int msToBeatFloor(int ms, int gapMs, double bpm) {
        return (int) Math.max(0, Math.floor(YassTable.msToBeatExact(ms, gapMs, bpm)));
    }

    private void pruneEmptyNoteRows(YassTable table) {
        for (int rowIndex = table.getRowCount() - 1; rowIndex >= 0; rowIndex--) {
            YassRow row = table.getRowAt(rowIndex);
            if (row != null && row.isNote() && StringUtils.isBlank(row.getTrimmedText())) {
                table.getModelData().removeElementAt(rowIndex);
            }
        }
    }

    private void normalizeTiming(YassTable table) {
        shiftRowsForward(table);
        shortenRowsToFit(table);
        shiftRowsForward(table);
    }

    private void shiftRowsForward(YassTable table) {
        Integer nextFreeBeat = null;
        for (int rowIndex = 0; rowIndex < table.getRowCount(); rowIndex++) {
            YassRow row = table.getRowAt(rowIndex);
            if (row == null || !row.isNoteOrPageBreak()) {
                continue;
            }
            if (nextFreeBeat != null && row.getBeatInt() < nextFreeBeat) {
                row.setBeat(nextFreeBeat);
            }
            nextFreeBeat = row.isNote()
                    ? row.getBeatInt() + Math.max(MIN_NOTE_LENGTH, row.getLengthInt()) + 1
                    : row.getBeatInt() + 1;
        }
    }

    private void shortenRowsToFit(YassTable table) {
        for (int rowIndex = 0; rowIndex < table.getRowCount(); rowIndex++) {
            YassRow row = table.getRowAt(rowIndex);
            if (row == null || !row.isNote()) {
                continue;
            }
            int nextBeat = findNextTimingBoundaryBeat(table, rowIndex);
            if (nextBeat == Integer.MAX_VALUE) {
                continue;
            }
            int maxLength = nextBeat - row.getBeatInt() - 2;
            if (maxLength < MIN_NOTE_LENGTH) {
                maxLength = MIN_NOTE_LENGTH;
            }
            if (row.getLengthInt() > maxLength) {
                row.setLength(maxLength);
            }
        }
    }

    private int findNextTimingBoundaryBeat(YassTable table, int rowIndex) {
        for (int index = rowIndex + 1; index < table.getRowCount(); index++) {
            YassRow next = table.getRowAt(index);
            if (next != null && next.isNoteOrPageBreak()) {
                return next.getBeatInt();
            }
        }
        return Integer.MAX_VALUE;
    }

    private static final class NotePart {
        private final int beat;
        private final int length;
        private final String text;

        private NotePart(int beat, int length, String text) {
            this.beat = beat;
            this.length = Math.max(MIN_NOTE_LENGTH, length);
            this.text = text;
        }
    }
}
