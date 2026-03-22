package yass.alignment;

import org.apache.commons.lang3.StringUtils;
import yass.integration.transcription.openai.OpenAiTranscriptSegment;
import yass.integration.transcription.openai.OpenAiTranscriptWord;
import yass.integration.transcription.openai.OpenAiTranscriptionResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TranscriptTruthRewriteService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    public OpenAiTranscriptionResult rewrite(OpenAiTranscriptionResult original, String correctedLyrics) {
        if (original == null || StringUtils.isBlank(correctedLyrics)) {
            return original;
        }

        List<OpenAiTranscriptSegment> sourceSegments = normalizeSegments(original);
        if (sourceSegments.isEmpty()) {
            return original;
        }

        String normalizedLyrics = normalizeLyrics(correctedLyrics);
        List<String> targetLines = splitTargetLines(normalizedLyrics);
        List<OpenAiTranscriptWord> transcriptWords = flattenWords(sourceSegments);
        if (targetLines.isEmpty() || transcriptWords.isEmpty()) {
            return original;
        }

        List<LineToken> targetTokens = flattenLineTokens(targetLines);
        List<TokenMatch> anchors = computeAnchors(targetTokens, transcriptWords);
        List<LineRange> ranges = buildRanges(targetLines, transcriptWords, anchors);

        int mapped = 0;
        for (LineRange range : ranges) {
            if (range.resolved()) {
                mapped++;
            }
        }
        if (mapped == 0) {
            LOGGER.info("Transcript rewrite found no usable anchors. Keeping original transcript.");
            return original;
        }

        logLineMappings("rewrite", targetLines, ranges, transcriptWords, original.getSegments());

        List<OpenAiTranscriptSegment> originalSegments = sourceSegments;
        logLineMappings("alignToReferenceLines", targetLines, ranges, transcriptWords, originalSegments);
        List<OpenAiTranscriptSegment> rewrittenSegments = new ArrayList<>();
        List<OpenAiTranscriptWord> rewrittenWords = new ArrayList<>();

        for (int lineIndex = 0; lineIndex < targetLines.size(); lineIndex++) {
            String line = targetLines.get(lineIndex);
            LineRange range = ranges.get(lineIndex);
            List<OpenAiTranscriptWord> sourceWords;
            int startMs;
            int endMs;

            if (range.resolved()) {
                sourceWords = new ArrayList<>(transcriptWords.subList(range.startWordIndex(), range.endWordIndex() + 1));
                startMs = sourceWords.getFirst().getStartMs();
                endMs = sourceWords.getLast().getEndMs();
            } else {
                OpenAiTranscriptSegment fallback = originalSegments.get(Math.min(lineIndex, originalSegments.size() - 1));
                sourceWords = new ArrayList<>(fallback.getWords());
                if (sourceWords.isEmpty()) {
                    continue;
                }
                startMs = fallback.getStartMs();
                endMs = fallback.getEndMs();
            }

            List<OpenAiTranscriptWord> remappedWords = remapLineWords(line, sourceWords);
            if (remappedWords.isEmpty()) {
                continue;
            }
            rewrittenSegments.add(new OpenAiTranscriptSegment(startMs, endMs, line, remappedWords));
            rewrittenWords.addAll(remappedWords);
        }

        if (rewrittenSegments.isEmpty()) {
            return original;
        }

        LOGGER.info("Transcript rewrite mapped " + mapped + "/" + targetLines.size() + " target lines to transcript word ranges.");
        return new OpenAiTranscriptionResult(
                original.getSourceAudioFile(),
                original.getUploadAudioFile(),
                original.getSourceTag(),
                normalizedLyrics,
                rewrittenWords,
                rewrittenSegments,
                original.getLyricTokens(),
                original.isFromCache(),
                original.getCacheFile());
    }


    public OpenAiTranscriptionResult applyLyricsToStructuredTranscript(OpenAiTranscriptionResult structuredTranscript, String correctedLyrics) {
        if (structuredTranscript == null || StringUtils.isBlank(correctedLyrics)) {
            return structuredTranscript;
        }

        String normalizedLyrics = normalizeLyrics(correctedLyrics);
        List<String> targetLines = splitTargetLines(normalizedLyrics);
        List<OpenAiTranscriptSegment> structuredSegments = normalizeSegments(structuredTranscript);
        if (structuredSegments.isEmpty()) {
            return structuredTranscript;
        }
        if (targetLines.isEmpty()) {
            return structuredTranscript;
        }
        if (targetLines.size() != structuredSegments.size()) {
            LOGGER.info("Structured transcript apply is preserving the existing segment structure despite line/segment mismatch: "
                    + targetLines.size() + " lines vs " + structuredSegments.size() + " segments.");
            return applyLyricsBySegmentWordDistribution(structuredTranscript, normalizedLyrics, structuredSegments);
        }

        List<OpenAiTranscriptSegment> rewrittenSegments = new ArrayList<>(structuredSegments.size());
        List<OpenAiTranscriptWord> rewrittenWords = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < targetLines.size(); lineIndex++) {
            OpenAiTranscriptSegment segment = structuredSegments.get(lineIndex);
            List<OpenAiTranscriptWord> sourceWords = segment.getWords() == null ? Collections.emptyList() : segment.getWords();
            if (sourceWords.isEmpty()) {
                continue;
            }
            String line = targetLines.get(lineIndex);
            List<OpenAiTranscriptWord> remappedWords = remapLineWords(line, sourceWords);
            if (remappedWords.isEmpty()) {
                remappedWords = List.of(new OpenAiTranscriptWord(line,
                        LyricsAlignmentTokenizer.normalizeText(line),
                        segment.getStartMs(),
                        segment.getEndMs()));
            }
            rewrittenSegments.add(new OpenAiTranscriptSegment(segment.getStartMs(), segment.getEndMs(), line, remappedWords));
            rewrittenWords.addAll(remappedWords);
        }

        if (rewrittenSegments.isEmpty()) {
            return structuredTranscript;
        }

        return new OpenAiTranscriptionResult(
                structuredTranscript.getSourceAudioFile(),
                structuredTranscript.getUploadAudioFile(),
                structuredTranscript.getSourceTag(),
                normalizedLyrics,
                rewrittenWords,
                rewrittenSegments,
                structuredTranscript.getLyricTokens(),
                structuredTranscript.isFromCache(),
                structuredTranscript.getCacheFile());
    }

    private OpenAiTranscriptionResult applyLyricsBySegmentWordDistribution(OpenAiTranscriptionResult structuredTranscript,
                                                                          String normalizedLyrics,
                                                                          List<OpenAiTranscriptSegment> structuredSegments) {
        List<String> correctedWords = splitWordsPreservingOrder(normalizedLyrics);
        if (correctedWords.isEmpty()) {
            return structuredTranscript;
        }

        int segmentCount = structuredSegments.size();
        int[] sourceWordCounts = new int[segmentCount];
        int totalSourceWords = 0;
        for (int i = 0; i < segmentCount; i++) {
            List<OpenAiTranscriptWord> words = structuredSegments.get(i).getWords();
            int count = words == null ? 0 : words.size();
            sourceWordCounts[i] = Math.max(1, count);
            totalSourceWords += sourceWordCounts[i];
        }

        List<OpenAiTranscriptSegment> rewrittenSegments = new ArrayList<>(segmentCount);
        List<OpenAiTranscriptWord> rewrittenWords = new ArrayList<>();
        int correctedIndex = 0;
        int sourcePrefix = 0;
        for (int segmentIndex = 0; segmentIndex < segmentCount; segmentIndex++) {
            OpenAiTranscriptSegment segment = structuredSegments.get(segmentIndex);
            List<OpenAiTranscriptWord> sourceWords = segment.getWords() == null ? Collections.emptyList() : segment.getWords();
            if (sourceWords.isEmpty()) {
                sourcePrefix += sourceWordCounts[segmentIndex];
                continue;
            }

            int remainingSegments = segmentCount - segmentIndex;
            int suggestedEnd = (int) Math.round((double) (sourcePrefix + sourceWordCounts[segmentIndex]) * correctedWords.size() / totalSourceWords);
            int maxEnd = correctedWords.size() - Math.max(0, remainingSegments - 1);
            int correctedEnd = Math.max(correctedIndex + 1, Math.min(maxEnd, suggestedEnd));
            if (segmentIndex == segmentCount - 1) {
                correctedEnd = correctedWords.size();
            }

            List<String> assignedWords = correctedWords.subList(correctedIndex, Math.max(correctedIndex + 1, correctedEnd));
            String line = String.join(" ", assignedWords).trim();
            List<OpenAiTranscriptWord> remappedWords = remapLineWords(line, sourceWords);
            if (remappedWords.isEmpty()) {
                remappedWords = List.of(new OpenAiTranscriptWord(line,
                        LyricsAlignmentTokenizer.normalizeText(line),
                        segment.getStartMs(),
                        segment.getEndMs()));
            }
            rewrittenSegments.add(new OpenAiTranscriptSegment(segment.getStartMs(), segment.getEndMs(), line, remappedWords));
            rewrittenWords.addAll(remappedWords);

            correctedIndex = correctedEnd;
            sourcePrefix += sourceWordCounts[segmentIndex];
        }

        if (rewrittenSegments.isEmpty()) {
            return structuredTranscript;
        }

        return new OpenAiTranscriptionResult(
                structuredTranscript.getSourceAudioFile(),
                structuredTranscript.getUploadAudioFile(),
                structuredTranscript.getSourceTag(),
                rewrittenSegments.stream().map(OpenAiTranscriptSegment::getText).collect(Collectors.joining("\n")),
                rewrittenWords,
                rewrittenSegments,
                structuredTranscript.getLyricTokens(),
                structuredTranscript.isFromCache(),
                structuredTranscript.getCacheFile());
    }

    public OpenAiTranscriptionResult alignToReferenceLines(OpenAiTranscriptionResult original, String referenceLyrics) {
        if (original == null || StringUtils.isBlank(referenceLyrics)) {
            return original;
        }

        List<OpenAiTranscriptSegment> sourceSegments = normalizeSegments(original);
        if (sourceSegments.isEmpty()) {
            return original;
        }

        String normalizedLyrics = normalizeLyrics(referenceLyrics);
        List<String> targetLines = splitTargetLines(normalizedLyrics);
        List<OpenAiTranscriptWord> transcriptWords = flattenWords(sourceSegments);
        if (targetLines.isEmpty() || transcriptWords.isEmpty()) {
            return original;
        }

        List<LineToken> targetTokens = flattenLineTokens(targetLines);
        List<TokenMatch> anchors = computeAnchors(targetTokens, transcriptWords);
        List<LineRange> ranges = buildRanges(targetLines, transcriptWords, anchors);

        List<OpenAiTranscriptSegment> originalSegments = sourceSegments;
        List<OpenAiTranscriptSegment> rewrittenSegments = new ArrayList<>();
        List<OpenAiTranscriptWord> rewrittenWords = new ArrayList<>();

        for (int lineIndex = 0; lineIndex < targetLines.size(); lineIndex++) {
            LineRange range = ranges.get(lineIndex);
            List<OpenAiTranscriptWord> sourceWords;
            int startMs;
            int endMs;
            String lineText;

            if (range.resolved()) {
                sourceWords = new ArrayList<>(transcriptWords.subList(range.startWordIndex(), range.endWordIndex() + 1));
                startMs = sourceWords.getFirst().getStartMs();
                endMs = sourceWords.getLast().getEndMs();
                lineText = joinTranscriptWords(sourceWords);
            } else {
                OpenAiTranscriptSegment fallback = originalSegments.get(Math.min(lineIndex, originalSegments.size() - 1));
                sourceWords = new ArrayList<>(fallback.getWords());
                if (sourceWords.isEmpty()) {
                    continue;
                }
                startMs = fallback.getStartMs();
                endMs = fallback.getEndMs();
                lineText = StringUtils.defaultIfBlank(fallback.getText(), joinTranscriptWords(sourceWords));
            }

            rewrittenSegments.add(new OpenAiTranscriptSegment(startMs, endMs, lineText, sourceWords));
            rewrittenWords.addAll(sourceWords);
        }

        if (rewrittenSegments.isEmpty()) {
            return original;
        }

        return new OpenAiTranscriptionResult(
                original.getSourceAudioFile(),
                original.getUploadAudioFile(),
                original.getSourceTag(),
                rewrittenSegments.stream().map(OpenAiTranscriptSegment::getText).collect(Collectors.joining("\n")),
                rewrittenWords,
                rewrittenSegments,
                original.getLyricTokens(),
                original.isFromCache(),
                original.getCacheFile());
    }

    private String normalizeLyrics(String text) {
        String normalized = StringUtils.defaultString(text).replace("\r\n", "\n").replace("\r", "\n");
        normalized = normalized.replaceAll("(?m)[ \t]+$", "");
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        return StringUtils.strip(normalized, "\n");
    }

    private void logLineMappings(String phase,
                                 List<String> targetLines,
                                 List<LineRange> ranges,
                                 List<OpenAiTranscriptWord> transcriptWords,
                                 List<OpenAiTranscriptSegment> originalSegments) {
        for (int lineIndex = 0; lineIndex < targetLines.size() && lineIndex < ranges.size(); lineIndex++) {
            LineRange range = ranges.get(lineIndex);
            if (range.resolved()) {
                OpenAiTranscriptWord first = transcriptWords.get(range.startWordIndex());
                OpenAiTranscriptWord last = transcriptWords.get(range.endWordIndex());
                LOGGER.info(String.format("Transcript %s line %d resolved words %d-%d ms %d-%d target=\"%s\" transcript=\"%s\"",
                        phase,
                        lineIndex + 1,
                        range.startWordIndex(),
                        range.endWordIndex(),
                        first.getStartMs(),
                        last.getEndMs(),
                        abbreviate(targetLines.get(lineIndex)),
                        abbreviate(joinTranscriptWords(transcriptWords.subList(range.startWordIndex(), range.endWordIndex() + 1)))));
            } else {
                OpenAiTranscriptSegment fallback = originalSegments.get(Math.min(lineIndex, originalSegments.size() - 1));
                LOGGER.info(String.format("Transcript %s line %d fallback segment ms %d-%d target=\"%s\" fallback=\"%s\"",
                        phase,
                        lineIndex + 1,
                        fallback.getStartMs(),
                        fallback.getEndMs(),
                        abbreviate(targetLines.get(lineIndex)),
                        abbreviate(StringUtils.defaultIfBlank(fallback.getText(), joinTranscriptWords(fallback.getWords())))));
            }
        }
    }

    private String abbreviate(String value) {
        String normalized = StringUtils.normalizeSpace(StringUtils.defaultString(value));
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 77) + "...";
    }

    private List<OpenAiTranscriptSegment> normalizeSegments(OpenAiTranscriptionResult result) {
        if (result.getSegments() != null && !result.getSegments().isEmpty()) {
            return result.getSegments();
        }
        if (result.getWords() == null || result.getWords().isEmpty()) {
            return Collections.emptyList();
        }
        List<OpenAiTranscriptWord> usableWords = new ArrayList<>();
        for (OpenAiTranscriptWord word : result.getWords()) {
            if (word != null && StringUtils.isNotBlank(word.getNormalizedText())) {
                usableWords.add(word);
            }
        }
        if (usableWords.isEmpty()) {
            return Collections.emptyList();
        }
        int startMs = usableWords.get(0).getStartMs();
        int endMs = usableWords.get(usableWords.size() - 1).getEndMs();
        String text = joinTranscriptWords(usableWords);
        return List.of(new OpenAiTranscriptSegment(startMs, endMs, text, usableWords));
    }

    private List<String> splitTargetLines(String text) {
        if (StringUtils.isBlank(text)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String line : text.split("\\n")) {
            String trimmed = StringUtils.trimToEmpty(line);
            if (StringUtils.isNotBlank(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private List<String> splitWordsPreservingOrder(String text) {
        List<String> result = new ArrayList<>();
        for (String line : splitTargetLines(text)) {
            for (String word : line.split("\s+")) {
                String trimmed = StringUtils.trimToEmpty(word);
                if (StringUtils.isNotBlank(trimmed)) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    private List<OpenAiTranscriptWord> flattenWords(List<OpenAiTranscriptSegment> segments) {
        List<OpenAiTranscriptWord> result = new ArrayList<>();
        for (OpenAiTranscriptSegment segment : segments) {
            if (segment == null || segment.getWords() == null) {
                continue;
            }
            for (OpenAiTranscriptWord word : segment.getWords()) {
                if (word != null && StringUtils.isNotBlank(word.getNormalizedText())) {
                    result.add(word);
                }
            }
        }
        return result;
    }

    private String joinTranscriptWords(List<OpenAiTranscriptWord> words) {
        return words.stream()
                .map(OpenAiTranscriptWord::getText)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(" "))
                .trim();
    }

    private List<LineToken> flattenLineTokens(List<String> targetLines) {
        List<LineToken> tokens = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < targetLines.size(); lineIndex++) {
            String[] words = targetLines.get(lineIndex).split("\\s+");
            for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
                String word = StringUtils.trimToEmpty(words[wordIndex]);
                if (StringUtils.isBlank(word)) {
                    continue;
                }
                tokens.add(new LineToken(lineIndex, wordIndex, word, LyricsAlignmentTokenizer.normalizeText(word)));
            }
        }
        return tokens;
    }

    private List<TokenMatch> computeAnchors(List<LineToken> targetTokens, List<OpenAiTranscriptWord> transcriptWords) {
        int n = targetTokens.size();
        int m = transcriptWords.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (tokensMatch(targetTokens.get(i).normalized(), transcriptWords.get(j).getNormalizedText())) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        List<TokenMatch> matches = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (tokensMatch(targetTokens.get(i).normalized(), transcriptWords.get(j).getNormalizedText())) {
                matches.add(new TokenMatch(i, j));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        return matches;
    }

    private List<LineRange> buildRanges(List<String> targetLines,
                                        List<OpenAiTranscriptWord> transcriptWords,
                                        List<TokenMatch> anchors) {
        List<LineRange> ranges = new ArrayList<>();
        List<LineToken> lineTokens = flattenLineTokens(targetLines);
        List<List<Integer>> lineMatches = new ArrayList<>();
        for (int i = 0; i < targetLines.size(); i++) {
            lineMatches.add(new ArrayList<>());
        }
        for (TokenMatch anchor : anchors) {
            int lineIndex = lineTokens.get(anchor.targetTokenIndex()).lineIndex();
            lineMatches.get(lineIndex).add(anchor.transcriptWordIndex());
        }

        int nextFree = 0;
        for (int lineIndex = 0; lineIndex < targetLines.size(); lineIndex++) {
            List<Integer> matches = lineMatches.get(lineIndex);
            if (matches.isEmpty()) {
                ranges.add(new LineRange(lineIndex, -1, -1));
                continue;
            }
            int start = Math.max(nextFree, matches.getFirst());
            int end = Math.max(start, matches.getLast());
            ranges.add(new LineRange(lineIndex, start, end));
            nextFree = end + 1;
        }
        return ranges;
    }

    private List<OpenAiTranscriptWord> remapLineWords(String line, List<OpenAiTranscriptWord> sourceWords) {
        if (StringUtils.isBlank(line) || sourceWords == null || sourceWords.isEmpty()) {
            return Collections.emptyList();
        }
        String[] correctedWords = line.split("\\s+");
        if (correctedWords.length == sourceWords.size()) {
            return remapExact(correctedWords, sourceWords);
        }
        if (correctedWords.length < sourceWords.size()) {
            return remapByJoining(correctedWords, sourceWords);
        }
        List<OpenAiTranscriptWord> split = remapBySplitting(correctedWords, sourceWords);
        return split != null ? split : remapByJoining(correctedWords, sourceWords);
    }

    private List<OpenAiTranscriptWord> remapExact(String[] correctedWords, List<OpenAiTranscriptWord> sourceWords) {
        List<OpenAiTranscriptWord> result = new ArrayList<>(sourceWords.size());
        for (int i = 0; i < sourceWords.size(); i++) {
            OpenAiTranscriptWord src = sourceWords.get(i);
            String corrected = correctedWords[i];
            result.add(new OpenAiTranscriptWord(corrected, LyricsAlignmentTokenizer.normalizeText(corrected), src.getStartMs(), src.getEndMs()));
        }
        return result;
    }

    private List<OpenAiTranscriptWord> remapByJoining(String[] correctedWords, List<OpenAiTranscriptWord> sourceWords) {
        int n = sourceWords.size();
        int m = correctedWords.length;
        if (m == 0) {
            return Collections.emptyList();
        }
        List<OpenAiTranscriptWord> result = new ArrayList<>(m);
        for (int corrIdx = 0; corrIdx < m; corrIdx++) {
            int start = (int) Math.round((double) corrIdx * n / m);
            int end = (int) Math.round((double) (corrIdx + 1) * n / m);
            if (start >= end) {
                end = start + 1;
            }
            if (end > n) {
                end = n;
            }
            if (start >= n) {
                return Collections.emptyList();
            }
            int startMs = sourceWords.get(start).getStartMs();
            int endMs = sourceWords.get(end - 1).getEndMs();
            String corrected = correctedWords[corrIdx];
            result.add(new OpenAiTranscriptWord(corrected, LyricsAlignmentTokenizer.normalizeText(corrected), startMs, endMs));
        }
        return result;
    }

    private List<OpenAiTranscriptWord> remapBySplitting(String[] correctedWords, List<OpenAiTranscriptWord> sourceWords) {
        int n = sourceWords.size();
        int m = correctedWords.length;
        List<OpenAiTranscriptWord> result = new ArrayList<>(m);
        for (int corrIdx = 0; corrIdx < m; corrIdx++) {
            int transcriptIdx = (int) Math.floor((double) corrIdx * n / m);
            if (transcriptIdx >= n) {
                transcriptIdx = n - 1;
            }
            int firstCorr = (int) Math.floor((double) transcriptIdx * m / n);
            int lastCorr = (int) Math.floor((double) (transcriptIdx + 1) * m / n) - 1;
            int splitCount = lastCorr - firstCorr + 1;
            OpenAiTranscriptWord src = sourceWords.get(transcriptIdx);
            int span = src.getEndMs() - src.getStartMs();
            if (splitCount > 1 && span < splitCount) {
                return null;
            }
            int offsetInSlot = corrIdx - firstCorr;
            int slotStartMs = src.getStartMs() + (int) Math.round((double) span * offsetInSlot / splitCount);
            int slotEndMs = src.getStartMs() + (int) Math.round((double) span * (offsetInSlot + 1) / splitCount);
            String corrected = correctedWords[corrIdx];
            result.add(new OpenAiTranscriptWord(corrected, LyricsAlignmentTokenizer.normalizeText(corrected), slotStartMs, slotEndMs));
        }
        return result;
    }

    private boolean tokensMatch(String target, String transcript) {
        String left = StringUtils.defaultString(target);
        String right = StringUtils.defaultString(transcript);
        if (StringUtils.equals(left, right)) {
            return true;
        }
        if (StringUtils.isBlank(left) || StringUtils.isBlank(right)) {
            return false;
        }
        if (left.length() >= 4 && right.length() >= 4 && (left.startsWith(right) || right.startsWith(left))) {
            return true;
        }
        return levenshteinDistance(left, right) <= 1;
    }

    private int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private record LineToken(int lineIndex, int wordIndex, String text, String normalized) {
    }

    private record TokenMatch(int targetTokenIndex, int transcriptWordIndex) {
    }

    private record LineRange(int lineIndex, int startWordIndex, int endWordIndex) {
        boolean resolved() {
            return startWordIndex >= 0 && endWordIndex >= startWordIndex;
        }
    }
}
