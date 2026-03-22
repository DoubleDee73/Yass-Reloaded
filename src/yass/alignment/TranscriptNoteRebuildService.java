package yass.alignment;

import java.util.ArrayList;
import java.util.List;
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

    public TranscriptRebuildResult transcript(YassTable table, OpenAiTranscriptionResult transcriptionResult) {
        List<OpenAiTranscriptSegment> segments = collectSegments(transcriptionResult);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("No transcript segments available for rebuild.");
        }

        int gapMs = roundToNearestTen(findInitialGap(segments));
        List<String> rebuiltRows = createRows(segments, table.getProperties(), table.getBPM(), gapMs);
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

    private List<String> createRows(List<OpenAiTranscriptSegment> segments,
                                    YassProperties properties,
                                    double bpm,
                                    int gapMs) {
        List<String> rows = new ArrayList<>();
        boolean spacingAfter = properties != null && properties.isUncommonSpacingAfter();
        Integer previousNoteBeat = null;
        int previousNoteIndex = -1;
        int nextAllowedBeat = 0;

        for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
            OpenAiTranscriptSegment segment = segments.get(segmentIndex);
            List<OpenAiTranscriptWord> words = segment.getWords();
            if (words == null || words.isEmpty()) {
                continue;
            }

            if (!rows.isEmpty()) {
                int firstWordBeat = msToBeat(words.get(0).getStartMs(), gapMs, bpm);
                // Place page break halfway between previous note end and first word of this segment,
                // but never at or after the first word beat (note must start there)
                int midpoint = nextAllowedBeat + Math.max(0, (firstWordBeat - nextAllowedBeat) / 2);
                int pageBeat = Math.min(midpoint, Math.max(nextAllowedBeat, firstWordBeat - 1));
                rows.add("-\t" + Math.max(0, pageBeat));
                previousNoteBeat = null;
                previousNoteIndex = -1;
                nextAllowedBeat = pageBeat + 1;
            }

            for (OpenAiTranscriptWord word : words) {
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
                String text = applyWordSpacing(cleanedWord, spacingAfter);

                if (previousNoteIndex >= 0 && previousNoteBeat != null) {
                    tightenPreviousNote(rows, previousNoteIndex, previousNoteBeat, startBeat);
                }

                rows.add(createNoteRow(startBeat, length, text));
                int currentIndex = rows.size() - 1;

                previousNoteBeat = startBeat;
                previousNoteIndex = currentIndex;
                nextAllowedBeat = startBeat + length;
            }
        }

        return rows;
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
}
