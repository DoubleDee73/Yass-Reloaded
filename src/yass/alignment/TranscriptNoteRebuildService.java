package yass.alignment;

import org.apache.commons.lang3.StringUtils;
import yass.YassProperties;
import yass.YassRow;
import yass.YassTable;
import yass.integration.transcription.openai.OpenAiTranscriptSegment;
import yass.integration.transcription.openai.OpenAiTranscriptWord;
import yass.integration.transcription.openai.OpenAiTranscriptionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.logging.Logger;

public class TranscriptNoteRebuildService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final int DEFAULT_PITCH = 6;
    private static final int MIN_NOTE_LENGTH = 1;

    public TranscriptRebuildResult Transcript(YassTable table, OpenAiTranscriptionResult transcriptionResult) {
        List<OpenAiTranscriptSegment> segments = collectSegments(transcriptionResult);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("No transcript segments available for rebuild.");
        }

        int gapMs = roundToNearestTen(findInitialGap(segments));
        List<String> rebuiltRows = createRows(segments, table.getProperties(), table.getBPM(), gapMs);
        if (rebuiltRows.isEmpty()) {
            throw new IllegalStateException("Transcript rebuild produced no note rows.");
        }

        String rebuiltText = buildRebuiltTableText(table, rebuiltRows, gapMs);
        String currentDir = table.getDir();
        String currentFilename = table.getFilename();
        List<Integer> originalHeights = collectOriginalNoteHeights(table);

        table.addUndo();
        boolean oldUndo = table.getPreventUndo();
        table.setPreventUndo(true);
        try {
            table.removeAllRows();
            table.setDir(currentDir);
            table.setFilename(currentFilename);
            if (!table.setText(rebuiltText)) {
                throw new IllegalStateException("Failed to rebuild notes from transcript.");
            }
        } finally {
            table.setPreventUndo(oldUndo);
        }
        table.setDir(currentDir);
        table.setFilename(currentFilename);
        table.setGap(gapMs);
        applyPitchContour(table, originalHeights);
        pruneEmptyNoteRows(table);
        normalizeTiming(table);
        table.setSaved(false);

        int noteCount = 0;
        int pageBreakCount = 0;
        for (String row : rebuiltRows) {
            YassRow parsed = new YassRow(row);
            if (parsed.isNote()) {
                noteCount++;
            } else if (parsed.isPageBreak()) {
                pageBreakCount++;
            }
        }
        LOGGER.info("Rebuilt " + noteCount + " notes from transcript with gap " + gapMs + " ms.");
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

    private List<Integer> collectOriginalNoteHeights(YassTable table) {
        List<Integer> heights = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < table.getRowCount(); rowIndex++) {
            YassRow row = table.getRowAt(rowIndex);
            if (row != null && row.isNote()) {
                heights.add(row.getHeightInt());
            }
        }
        return heights;
    }

    private void applyPitchContour(YassTable table, List<Integer> originalHeights) {
        if (originalHeights == null || originalHeights.isEmpty()) {
            return;
        }
        List<YassRow> rebuiltNotes = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < table.getRowCount(); rowIndex++) {
            YassRow row = table.getRowAt(rowIndex);
            if (row != null && row.isNote()) {
                rebuiltNotes.add(row);
            }
        }
        if (rebuiltNotes.isEmpty()) {
            return;
        }
        if (originalHeights.size() == 1) {
            int height = originalHeights.getFirst();
            for (YassRow row : rebuiltNotes) {
                row.setHeight(height);
            }
            return;
        }
        int rebuiltCount = rebuiltNotes.size();
        int originalCount = originalHeights.size();
        for (int index = 0; index < rebuiltCount; index++) {
            int sourceIndex = rebuiltCount == 1
                    ? originalCount / 2
                    : (int) Math.round(index * (originalCount - 1d) / (rebuiltCount - 1d));
            sourceIndex = Math.max(0, Math.min(originalCount - 1, sourceIndex));
            rebuiltNotes.get(index).setHeight(originalHeights.get(sourceIndex));
        }
    }

    private List<String> createRows(List<OpenAiTranscriptSegment> segments,
                                    YassProperties properties,
                                    double bpm,
                                    int gapMs) {
        List<String> rows = new ArrayList<>();
        boolean uncommonSpacingAfter = properties != null && properties.isUncommonSpacingAfter();
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
                int pageBeat = Math.max(nextAllowedBeat, msToBeat(segment.getStartMs(), gapMs, bpm));
                rows.add("-\t" + Math.max(0, pageBeat));
                previousNoteBeat = null;
                previousNoteIndex = -1;
                nextAllowedBeat = Math.max(pageBeat + 1, nextAllowedBeat);
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
                int endBeat = msToBeat(word.getEndMs(), gapMs, bpm);
                int length = Math.max(MIN_NOTE_LENGTH, endBeat - startBeat + 1);
                String text = applyWordSpacing(cleanedWord, uncommonSpacingAfter);

                rows.add(createNoteRow(startBeat, length, text));
                int currentIndex = rows.size() - 1;
                if (previousNoteIndex >= 0 && previousNoteBeat != null) {
                    tightenPreviousNote(rows, previousNoteIndex, previousNoteBeat, startBeat);
                }

                previousNoteBeat = startBeat;
                previousNoteIndex = currentIndex;
                nextAllowedBeat = startBeat + 1;
            }
        }

        return rows;
    }

    private String buildRebuiltTableText(YassTable table, List<String> rebuiltRows, int gapMs) {
        String[] lines = table.getPlainText().split("\\R");
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            YassRow row = new YassRow(line);
            if (row.isNote() || row.isPageBreak() || row.isEnd()) {
                break;
            }
            if (row.isGap()) {
                builder.append("#GAP:").append(gapMs).append('\n');
            } else {
                builder.append(line).append('\n');
            }
        }
        for (String rebuiltRow : rebuiltRows) {
            builder.append(rebuiltRow).append('\n');
        }
        builder.append('E').append('\n');
        return builder.toString();
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

    private String applyWordSpacing(String word, boolean uncommonSpacingAfter) {
        if (StringUtils.isBlank(word)) {
            return word;
        }
        return uncommonSpacingAfter ? (YassRow.SPACE + word) : (word + YassRow.SPACE);
    }

    private void tightenPreviousNote(List<String> rows, int previousIndex, int previousBeat, int currentBeat) {
        if (previousIndex < 0 || previousIndex >= rows.size() || currentBeat <= previousBeat) {
            return;
        }
        YassRow previousRow = new YassRow(rows.get(previousIndex));
        if (!previousRow.isNote()) {
            return;
        }
        int desiredLength = Math.max(MIN_NOTE_LENGTH, currentBeat - previousBeat - 2);
        if (previousRow.getLengthInt() > desiredLength) {
            rows.set(previousIndex, createNoteRow(previousBeat, desiredLength, previousRow.getText()));
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

    private int roundToNearestTen(int value) {
        return (int) (Math.round(value / 10.0d) * 10);
    }

    private int msToBeat(int ms, int gapMs, double bpm) {
        return (int) Math.max(0, Math.round(YassTable.msToBeatExact(ms, gapMs, bpm)));
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
