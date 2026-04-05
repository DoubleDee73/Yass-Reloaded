package yass.alignment;

import org.apache.commons.lang3.StringUtils;
import yass.YassRow;
import yass.YassTable;
import yass.integration.transcription.openai.OpenAiTranscriptWord;
import yass.integration.transcription.openai.OpenAiTranscriptionResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public class LyricsAlignmentService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final int MIN_PHRASE_TOKENS = 3;
    private static final int MAX_PHRASE_TOKENS = 8;
    private static final double MIN_ANCHOR_SCORE = 0.82d;
    private static final double MIN_OPENING_ANCHOR_SCORE = 0.78d;
    private static final int MAX_NEAR_DISTANCE = 1;

    public LyricsAlignmentResult alignAndApply(YassTable table, OpenAiTranscriptionResult transcriptionResult) {
        List<LyricToken> lyricTokens = transcriptionResult.getLyricTokens();
        List<OpenAiTranscriptWord> transcriptWords = transcriptionResult.getWords();
        if (lyricTokens.isEmpty() || transcriptWords.isEmpty()) {
            return new LyricsAlignmentResult(0, 0, 0, 0d, table.getGap(), List.of());
        }

        List<AlignmentAnchor> anchors = findAnchors(lyricTokens, transcriptWords);
        if (anchors.isEmpty()) {
            LOGGER.info("OpenAI alignment found no safe anchors.");
            return new LyricsAlignmentResult(0, 0, transcriptWords.size(), 0d, table.getGap(), List.of());
        }

        table.addUndo();
        boolean oldUndo = table.getPreventUndo();
        table.setPreventUndo(true);

        double appliedGapMs = table.getGap();
        int movedNotes = 0;
        double totalBeatDelta = 0d;
        int changedRowsMin = Integer.MAX_VALUE;
        int changedRowsMax = -1;

        try {
            appliedGapMs = maybeApplyInitialGap(table, lyricTokens, transcriptWords, anchors);
            for (AlignmentAnchor anchor : anchors) {
                totalBeatDelta += Math.abs(computeAnchorDeltaBeats(table, lyricTokens, transcriptWords, anchor));
                movedNotes += applyAnchor(table, lyricTokens, transcriptWords, anchor);
                changedRowsMin = Math.min(changedRowsMin, lyricTokens.get(anchor.getLyricStart()).getFirstRow());
                changedRowsMax = Math.max(changedRowsMax, lyricTokens.get(anchor.getLyricEnd()).getLastRow());
            }
            movedNotes += interpolateUnmatchedBlocks(table, lyricTokens, transcriptWords, anchors);
            movedNotes += shiftRowsForwardToAvoidOverlaps(table);
            movedNotes += shortenNoteLengthsToFit(table);
            movedNotes += shiftRowsForwardToAvoidOverlaps(table);
            repositionPageBreaks(table);
        } finally {
            table.setPreventUndo(oldUndo);
        }

        if (changedRowsMin == Integer.MAX_VALUE) {
            changedRowsMin = 0;
        }
        if (changedRowsMax < changedRowsMin) {
            changedRowsMax = changedRowsMin;
        }
        table.fireTableRowsUpdated(changedRowsMin, changedRowsMax);
        table.setSaved(false);

        int ignoredWords = Math.max(0, transcriptWords.size() - anchors.stream()
                .mapToInt(a -> a.getTranscriptEnd() - a.getTranscriptStart() + 1)
                .sum());
        double avgDelta = anchors.isEmpty() ? 0d : totalBeatDelta / anchors.size();
        return new LyricsAlignmentResult(movedNotes, anchors.size(), ignoredWords, avgDelta, appliedGapMs, anchors);
    }

    public List<AlignmentAnchor> findAnchors(List<LyricToken> lyricTokens, List<OpenAiTranscriptWord> transcriptWords) {
        List<AlignmentAnchor> candidates = new ArrayList<>();
        for (int lyricStart = 0; lyricStart < lyricTokens.size(); lyricStart++) {
            for (int transcriptStart = 0; transcriptStart < transcriptWords.size(); transcriptStart++) {
                for (int length = MAX_PHRASE_TOKENS; length >= MIN_PHRASE_TOKENS; length--) {
                    int lyricEnd = lyricStart + length - 1;
                    int transcriptEnd = transcriptStart + length - 1;
                    if (lyricEnd >= lyricTokens.size() || transcriptEnd >= transcriptWords.size()) {
                        continue;
                    }
                    double score = scoreWindow(lyricTokens, transcriptWords, lyricStart, transcriptStart, length);
                    if (score >= MIN_ANCHOR_SCORE) {
                        candidates.add(new AlignmentAnchor(lyricStart, lyricEnd, transcriptStart, transcriptEnd, score));
                        break;
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(AlignmentAnchor::getScore).reversed()
                                  .thenComparingInt(a -> -(a.getLyricEnd() - a.getLyricStart())));
        List<AlignmentAnchor> selected = new ArrayList<>();
        for (AlignmentAnchor candidate : candidates) {
            if (overlaps(selected, candidate)) {
                continue;
            }
            selected.add(candidate);
        }
        selected.sort(Comparator.comparingInt(AlignmentAnchor::getLyricStart));
        prependOpeningAnchorIfSafe(selected, lyricTokens, transcriptWords);
        selected.sort(Comparator.comparingInt(AlignmentAnchor::getLyricStart));
        return selected;
    }

    private double maybeApplyInitialGap(YassTable table,
                                        List<LyricToken> lyricTokens,
                                        List<OpenAiTranscriptWord> transcriptWords,
                                        List<AlignmentAnchor> anchors) {
        if (table.getGap() > 0d) {
            return table.getGap();
        }
        int startMs = transcriptWords.stream()
                .mapToInt(OpenAiTranscriptWord::getStartMs)
                .filter(value -> value >= 0)
                .findFirst()
                .orElse(-1);
        if (startMs < 0) {
            return table.getGap();
        }
        startMs = (int) (Math.round(startMs / 10.0d) * 10);
        LOGGER.info("Applying initial OpenAI word start to #GAP: " + startMs + " ms");
        table.setGap(startMs);
        return startMs;
    }

    private int applyAnchor(YassTable table,
                            List<LyricToken> lyricTokens,
                            List<OpenAiTranscriptWord> transcriptWords,
                            AlignmentAnchor anchor) {
        LyricToken firstToken = lyricTokens.get(anchor.getLyricStart());
        LyricToken lastToken = lyricTokens.get(anchor.getLyricEnd());
        int moved = 0;
        int nextFreeBeat = findRequiredStartBeatBefore(table, firstToken.getFirstRow());
        boolean preserveSongStart = anchor.getLyricStart() == 0 && firstToken.getFirstRow() == table.getFirstNoteRow();
        int preserveThroughRow = preserveSongStart ? findFirstPageBreakWithin(table, firstToken.getFirstRow(), lastToken.getLastRow()) : -1;

        for (int offset = 0; offset <= anchor.getLyricEnd() - anchor.getLyricStart(); offset++) {
            LyricToken token = lyricTokens.get(anchor.getLyricStart() + offset);
            OpenAiTranscriptWord word = transcriptWords.get(anchor.getTranscriptStart() + offset);
            List<Integer> rowIndices = token.getRowIndices();
            if (rowIndices.isEmpty()) {
                continue;
            }
            if (preserveThroughRow >= 0 && rowIndices.get(rowIndices.size() - 1) <= preserveThroughRow) {
                nextFreeBeat = table.getRowAt(rowIndices.get(rowIndices.size() - 1)).getBeatInt()
                        + Math.max(1, table.getRowAt(rowIndices.get(rowIndices.size() - 1)).getLengthInt()) + 1;
                continue;
            }

            double targetStartBeat = msToBeat(table, word.getStartMs());
            double targetEndBeat = msToBeat(table, Math.max(word.getStartMs(), word.getEndMs()));
            double span = Math.max(rowIndices.size() + 1d, targetEndBeat - targetStartBeat);

            for (int rowOffset = 0; rowOffset < rowIndices.size(); rowOffset++) {
                int rowIndex = rowIndices.get(rowOffset);
                YassRow row = table.getRowAt(rowIndex);
                if (row == null || !row.isNote()) {
                    continue;
                }
                double segmentStart = targetStartBeat + (span * rowOffset / rowIndices.size());
                int targetBeat = (int) Math.round(segmentStart);
                if (nextFreeBeat != Integer.MIN_VALUE && targetBeat < nextFreeBeat) {
                    targetBeat = nextFreeBeat;
                }
                if (row.getBeatInt() != targetBeat) {
                    row.setBeat(targetBeat);
                    moved++;
                }
                nextFreeBeat = row.getBeatInt() + 1;
            }
        }
        return moved;
    }

    private double computeAnchorDeltaBeats(YassTable table,
                                           List<LyricToken> lyricTokens,
                                           List<OpenAiTranscriptWord> transcriptWords,
                                           AlignmentAnchor anchor) {
        int firstRow = lyricTokens.get(anchor.getLyricStart()).getFirstRow();
        if (firstRow < 0) {
            return 0d;
        }
        double targetBeat = anchor.getLyricStart() == 0 && firstRow == table.getFirstNoteRow()
                ? table.getRowAt(firstRow).getBeatInt()
                : msToBeat(table, transcriptWords.get(anchor.getTranscriptStart()).getStartMs());
        return targetBeat - table.getRowAt(firstRow).getBeatInt();
    }

    private int findFirstPageBreakWithin(YassTable table, int firstRow, int lastRow) {
        for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex++) {
            YassRow row = table.getRowAt(rowIndex);
            if (row != null && row.isPageBreak()) {
                return rowIndex;
            }
        }
        return -1;
    }
    private int findRequiredStartBeatBefore(YassTable table, int firstRow) {
        for (int rowIndex = firstRow - 1; rowIndex >= 0; rowIndex--) {
            YassRow row = table.getRowAt(rowIndex);
            if (row == null) {
                continue;
            }
            if (row.isNote()) {
                return row.getBeatInt() + Math.max(1, row.getLengthInt()) + 1;
            }
            if (row.isPageBreak()) {
                return row.getBeatInt() + 1;
            }
        }
        return Integer.MIN_VALUE;
    }

    private int interpolateUnmatchedBlocks(YassTable table,
                                           List<LyricToken> lyricTokens,
                                           List<OpenAiTranscriptWord> transcriptWords,
                                           List<AlignmentAnchor> anchors) {
        if (anchors.size() < 2) {
            return 0;
        }

        List<AnchorPlacement> placements = new ArrayList<>();
        for (AlignmentAnchor anchor : anchors) {
            LyricToken firstToken = lyricTokens.get(anchor.getLyricStart());
            int row = firstToken.getFirstRow();
            if (row < 0) {
                continue;
            }
            double delta = anchor.getLyricStart() == 0 && row == table.getFirstNoteRow()
                    ? 0d
                    : msToBeat(table, transcriptWords.get(anchor.getTranscriptStart()).getStartMs()) - table.getRowAt(row).getBeatInt();
            placements.add(new AnchorPlacement(row,
                    lyricTokens.get(anchor.getLyricEnd()).getLastRow(),
                    delta));
        }

        int moved = 0;
        for (int i = 0; i < placements.size() - 1; i++) {
            AnchorPlacement left = placements.get(i);
            AnchorPlacement right = placements.get(i + 1);
            int startRow = left.endRow + 1;
            int endRow = right.startRow - 1;
            if (startRow > endRow) {
                continue;
            }
            int span = Math.max(1, endRow - startRow + 1);
            for (int rowIndex = startRow; rowIndex <= endRow; rowIndex++) {
                YassRow row = table.getRowAt(rowIndex);
                if (row == null || !row.isNote()) {
                    continue;
                }
                double progress = (rowIndex - startRow + 1d) / (span + 1d);
                double delta = left.deltaBeats + (right.deltaBeats - left.deltaBeats) * progress;
                int targetBeat = (int) Math.round(row.getBeatInt() + delta);
                if (row.getBeatInt() != targetBeat) {
                    row.setBeat(targetBeat);
                    moved++;
                }
            }
        }
        return moved;
    }

    private static final class AnchorPlacement {
        private final int startRow;
        private final int endRow;
        private final double deltaBeats;

        private AnchorPlacement(int startRow, int endRow, double deltaBeats) {
            this.startRow = startRow;
            this.endRow = endRow;
            this.deltaBeats = deltaBeats;
        }
    }
    private int shiftRowsForwardToAvoidOverlaps(YassTable table) {
        int moved = 0;
        int nextFreeBeat = Integer.MIN_VALUE;
        for (int rowIndex = 0; rowIndex < table.getRowCount(); rowIndex++) {
            YassRow row = table.getRowAt(rowIndex);
            if (row == null || !row.isNoteOrPageBreak()) {
                continue;
            }
            int targetBeat = row.getBeatInt();
            if (nextFreeBeat != Integer.MIN_VALUE && targetBeat < nextFreeBeat) {
                targetBeat = nextFreeBeat;
            }
            if (targetBeat != row.getBeatInt()) {
                row.setBeat(targetBeat);
                if (row.isPageBreak() && row.hasSecondBeat() && row.getSecondBeatInt() < targetBeat) {
                    row.setSecondBeat(targetBeat);
                }
                moved++;
            }
            if (row.isNote()) {
                nextFreeBeat = row.getBeatInt() + Math.max(1, row.getLengthInt()) + 1;
            } else {
                int previousEnd = findPreviousNoteEndBeat(table, rowIndex);
                int nextBeat = findNextTimingBoundaryBeat(table, rowIndex);
                if (previousEnd != Integer.MIN_VALUE && nextBeat != Integer.MAX_VALUE && nextBeat - previousEnd > 2) {
                    int preferredBeat = previousEnd + (int) Math.round((nextBeat - previousEnd) * 0.6d);
                    if (preferredBeat > row.getBeatInt()) {
                        row.setBeat(Math.min(preferredBeat, nextBeat - 1));
                    }
                }
                nextFreeBeat = row.getBeatInt() + 1;
            }
        }
        return moved;
    }

    private int shortenNoteLengthsToFit(YassTable table) {
        int changed = 0;
        for (int rowIndex = 0; rowIndex < table.getRowCount(); rowIndex++) {
            YassRow row = table.getRowAt(rowIndex);
            if (row == null || !row.isNote()) {
                continue;
            }
            int nextBeat = findNextTimingBoundaryBeat(table, rowIndex);
            if (nextBeat == Integer.MAX_VALUE) {
                continue;
            }
            int maxLength = nextBeat - row.getBeatInt() - 1;
            if (maxLength >= 1 && row.getLengthInt() > maxLength) {
                row.setLength(maxLength);
                changed++;
            }
        }
        return changed;
    }

    private void repositionPageBreaks(YassTable table) {
        for (int rowIndex = 0; rowIndex < table.getRowCount(); rowIndex++) {
            YassRow row = table.getRowAt(rowIndex);
            if (row == null || !row.isPageBreak()) {
                continue;
            }
            int prevEnd = findPreviousNoteEndBeat(table, rowIndex);
            int nextStart = findNextTimingBoundaryBeat(table, rowIndex);
            if (prevEnd == Integer.MIN_VALUE || nextStart == Integer.MAX_VALUE) {
                continue;
            }
            int gap = nextStart - prevEnd;
            if (gap < 2) {
                continue;
            }
            int idealBeat = prevEnd + (int) Math.round(gap * 0.6d);
            idealBeat = Math.max(prevEnd + 1, Math.min(nextStart - 1, idealBeat));
            if (row.getBeatInt() != idealBeat) {
                row.setBeat(idealBeat);
                if (row.hasSecondBeat() && row.getSecondBeatInt() < idealBeat) {
                    row.setSecondBeat(idealBeat);
                }
            }
        }
    }

    private int findPreviousNoteEndBeat(YassTable table, int fromRowIndex) {
        for (int rowIndex = fromRowIndex - 1; rowIndex >= 0; rowIndex--) {
            YassRow row = table.getRowAt(rowIndex);
            if (row != null && row.isNote()) {
                return row.getBeatInt() + Math.max(1, row.getLengthInt());
            }
        }
        return Integer.MIN_VALUE;
    }
    private int findNextTimingBoundaryBeat(YassTable table, int fromRowIndex) {
        for (int rowIndex = fromRowIndex + 1; rowIndex < table.getRowCount(); rowIndex++) {
            YassRow row = table.getRowAt(rowIndex);
            if (row != null && row.isNoteOrPageBreak()) {
                return row.getBeatInt();
            }
        }
        return Integer.MAX_VALUE;
    }

    private void prependOpeningAnchorIfSafe(List<AlignmentAnchor> selected,
                                            List<LyricToken> lyricTokens,
                                            List<OpenAiTranscriptWord> transcriptWords) {
        boolean hasOpeningAnchor = selected.stream().anyMatch(anchor -> anchor.getLyricStart() == 0 && anchor.getTranscriptStart() == 0);
        if (hasOpeningAnchor) {
            return;
        }
        int maxLength = Math.min(MAX_PHRASE_TOKENS, Math.min(lyricTokens.size(), transcriptWords.size()));
        if (!selected.isEmpty()) {
            maxLength = Math.min(maxLength, Math.min(selected.get(0).getLyricStart(), selected.get(0).getTranscriptStart()));
        }
        for (int length = maxLength; length >= MIN_PHRASE_TOKENS; length--) {
            double score = scoreWindow(lyricTokens, transcriptWords, 0, 0, length);
            if (score >= MIN_OPENING_ANCHOR_SCORE) {
                selected.add(new AlignmentAnchor(0, length - 1, 0, length - 1, score));
                return;
            }
        }
    }
    private boolean overlaps(List<AlignmentAnchor> selected, AlignmentAnchor candidate) {
        for (AlignmentAnchor anchor : selected) {
            boolean lyricOverlap = candidate.getLyricStart() <= anchor.getLyricEnd()
                    && candidate.getLyricEnd() >= anchor.getLyricStart();
            boolean transcriptOverlap = candidate.getTranscriptStart() <= anchor.getTranscriptEnd()
                    && candidate.getTranscriptEnd() >= anchor.getTranscriptStart();
            if (lyricOverlap || transcriptOverlap) {
                return true;
            }
        }
        return false;
    }

    private double scoreWindow(List<LyricToken> lyricTokens,
                               List<OpenAiTranscriptWord> transcriptWords,
                               int lyricStart,
                               int transcriptStart,
                               int length) {
        double total = 0d;
        for (int offset = 0; offset < length; offset++) {
            String lyric = lyricTokens.get(lyricStart + offset).getNormalizedText();
            String transcript = transcriptWords.get(transcriptStart + offset).getNormalizedText();
            total += scoreToken(lyric, transcript);
        }
        return total / length;
    }

    private double scoreToken(String lyric, String transcript) {
        if (StringUtils.equals(lyric, transcript)) {
            return 1d;
        }
        String lyricPhonetic = phoneticNormalize(lyric);
        String transcriptPhonetic = phoneticNormalize(transcript);
        if (StringUtils.equals(lyricPhonetic, transcriptPhonetic)) {
            return 0.94d;
        }
        int distance = levenshtein(lyricPhonetic, transcriptPhonetic);
        int maxLength = Math.max(lyricPhonetic.length(), transcriptPhonetic.length());
        if (distance <= MAX_NEAR_DISTANCE && maxLength >= 4) {
            return 0.86d;
        }
        return 0d;
    }

    private String phoneticNormalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replace("zijn", "zn")
                .replace("ij", "y")
                .replace("ck", "k")
                .replace("ph", "f")
                .replaceAll("c([aou])", "k$1")
                .replaceAll("([a-z])\\1+", "$1");
    }

    private int levenshtein(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) {
            costs[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int northwest = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int upper = costs[j];
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                costs[j] = Math.min(Math.min(costs[j] + 1, costs[j - 1] + 1), northwest + cost);
                northwest = upper;
            }
        }
        return costs[b.length()];
    }

    private double msToBeat(YassTable table, int millis) {
        return ((millis - table.getGap()) / 1000d) * 4d * table.getBPM() / 60d;
    }
}