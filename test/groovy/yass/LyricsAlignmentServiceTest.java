package yass;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import yass.alignment.LyricsAlignmentResult;
import yass.alignment.LyricsAlignmentService;
import yass.alignment.LyricsAlignmentTokenizer;
import yass.integration.transcription.openai.OpenAiTranscriptSegment;
import yass.integration.transcription.openai.OpenAiTranscriptWord;
import yass.integration.transcription.openai.OpenAiTranscriptionResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LyricsAlignmentServiceTest {
    @Test
    void calimeroOpenAiAlignmentAppliesGapImprovesTimingAndAvoidsOverlaps() throws IOException {
        AlignmentFixture fixture = alignFixture("calimero-transcript.json", "calimero.openai-transcript.json");

        assertTrue(fixture.alignmentResult.getMatchedRegions() >= 10,
                "Expected the improved OpenAI transcript to create multiple matched regions.");
        assertTrue(fixture.table.getGap() > 0d, "Expected the aligned GAP to be set.");
        assertEquals(7980d, fixture.table.getGap(), 0.0001d,
                "Expected the aligned GAP to use the first recognized word start rounded to 10 ms.");
        assertTrue(fixture.early35After <= Math.round(fixture.early35Before * 0.35d),
                "Expected the early aligned timing block to move substantially closer to the Calimero reference.");
        assertTrue(fixture.early70After <= Math.round(fixture.early70Before * 0.20d),
                "Expected the larger aligned timing block to move substantially closer to the Calimero reference.");
        assertTrue(fixture.overallAfter <= Math.round(fixture.overallBefore * 0.10d),
                "Expected the overall timing to move much closer to the Calimero reference.");
        assertTrue(hasNoOverlaps(fixture.table), "Aligned notes should not overlap anymore.");
        assertEquals(fixture.originalEntries.size(), fixture.alignedEntries.size(),
                "Expected the note/pagebreak structure to stay intact.");
    }

    @Test
    void calimeroWhisperxAlignmentAppliesGapImprovesTimingAndAvoidsOverlaps() throws IOException {
        AlignmentFixture fixture = alignFixture("calimero-whisperx-transcript.json", "calimero-whisperx-transcript.json");

        assertTrue(fixture.alignmentResult.getMatchedRegions() >= 8,
                "Expected the WhisperX transcript to create multiple matched regions.");
        assertTrue(fixture.table.getGap() > 0d, "Expected the aligned GAP to be set.");
        assertEquals(8480d, fixture.table.getGap(), 0.0001d,
                "Expected the aligned GAP to use the first WhisperX word start rounded to 10 ms.");
        assertTrue(fixture.early35After <= Math.round(fixture.early35Before * 0.45d),
                "Expected the early aligned timing block to move substantially closer to the Calimero reference.");
        assertTrue(fixture.early70After <= Math.round(fixture.early70Before * 0.35d),
                "Expected the larger aligned timing block to move substantially closer to the Calimero reference.");
        assertTrue(fixture.alignmentResult.getMovedNotes() > 0,
                "Expected the WhisperX alignment to move at least some notes.");
        assertTrue(hasNoOverlaps(fixture.table), "Aligned notes should not overlap anymore.");
        assertEquals(fixture.originalEntries.size(), fixture.alignedEntries.size(),
                "Expected the note/pagebreak structure to stay intact.");
    }

    @Test
    void calimeroWhisperxRebuildCreatesFreshNotesFromTranscript() throws IOException {
        String originalText = loadFixture("calimero-original.txt");
        JsonObject transcriptJson = JsonParser.parseString(loadFixture("calimero-whisperx-transcript.json")).getAsJsonObject();
        YassTable table = createTable(originalText);
        List<OpenAiTranscriptWord> words = extractWords(transcriptJson);
        List<OpenAiTranscriptSegment> segments = extractSegments(transcriptJson);
        OpenAiTranscriptionResult result = new OpenAiTranscriptionResult(
                new File("calimero.opus"),
                new File("calimero.opus"),
                "#VOCALS",
                extractTranscriptText(transcriptJson),
                words,
                segments,
                LyricsAlignmentTokenizer.buildTokens(table),
                true,
                new File("calimero-whisperx-transcript.json"));

        yass.alignment.TranscriptRebuildResult rebuildResult =
                new yass.alignment.TranscriptNoteRebuildService().Transcript(table, result);

        assertEquals(8480d, table.getGap(), 0.0001d,
                "Expected rebuild mode to set GAP from the first WhisperX word.");
        assertTrue(rebuildResult.getNoteCount() > 0, "Expected transcript rebuild to create note rows.");
        assertTrue(rebuildResult.getPageBreakCount() > 0, "Expected transcript rebuild to create at least one page break.");
        assertEquals(0, table.getFirstNote().getBeatInt(), "Expected rebuild mode to start the first note at beat 0.");
        assertTrue(hasNoOverlaps(table), "Rebuilt notes should not overlap.");
        assertTrue(table.hasLyrics(), "Rebuilt table should still contain lyrics.");
    }

    @Test
    void tovenaarWhisperxRebuildUsesTranscriptTimingAndAvoidsEmptyNotes() throws IOException {
        String originalText = loadFixture("tovenaar-original.txt");
        JsonObject transcriptJson = JsonParser.parseString(loadFixture("tovenaar-whisperx-transcript.json")).getAsJsonObject();
        YassTable table = createTable(originalText);
        List<OpenAiTranscriptWord> words = extractWords(transcriptJson);
        List<OpenAiTranscriptSegment> segments = extractSegments(transcriptJson);
        OpenAiTranscriptionResult result = new OpenAiTranscriptionResult(
                new File("tovenaar.opus"),
                new File("tovenaar-vocals.m4a"),
                "#VOCALS",
                extractTranscriptText(transcriptJson),
                words,
                segments,
                LyricsAlignmentTokenizer.buildTokens(table),
                true,
                new File("tovenaar-whisperx-transcript.json"));

        yass.alignment.TranscriptRebuildResult rebuildResult =
                new yass.alignment.TranscriptNoteRebuildService().Transcript(table, result);

        assertTrue(rebuildResult.getNoteCount() > 0, "Expected transcript rebuild to create note rows for Tovenaar.");
        assertTrue(hasNoOverlaps(table), "Rebuilt Tovenaar notes should not overlap.");
        assertTrue(noEmptyNoteTexts(table), "Rebuilt Tovenaar notes should not contain empty note texts: " + describeEmptyNotes(table));

        int secondPhraseBeat = beatForWord(table, "oud?");
        assertTrue(secondPhraseBeat >= 18,
                "Expected the second phrase to start much later than the wizard default placement.");
    }

    private static AlignmentFixture alignFixture(String transcriptFixture, String cacheFileName) throws IOException {
        String originalText = loadFixture("calimero-original.txt");
        String alignedText = loadFixture("calimero-aligned.txt");
        JsonObject transcriptJson = JsonParser.parseString(loadFixture(transcriptFixture)).getAsJsonObject();

        YassTable original = createTable(originalText);
        YassTable table = createTable(originalText);
        YassTable expected = createTable(alignedText);
        List<OpenAiTranscriptWord> words = extractWords(transcriptJson);
        List<OpenAiTranscriptSegment> segments = extractSegments(transcriptJson);
        OpenAiTranscriptionResult result = new OpenAiTranscriptionResult(
                new File("calimero.opus"),
                new File("calimero.opus"),
                "#AUDIO",
                extractTranscriptText(transcriptJson),
                words,
                segments,
                LyricsAlignmentTokenizer.buildTokens(table),
                true,
                new File(cacheFileName));

        LyricsAlignmentResult alignmentResult = new LyricsAlignmentService().alignAndApply(table, result);
        List<Map<String, Integer>> originalEntries = timingEntries(original);
        List<Map<String, Integer>> alignedEntries = timingEntries(table);
        List<Map<String, Integer>> expectedEntries = timingEntries(expected);
        int early35Before = sumBeatDistance(firstTimingEntries(original, 35), firstTimingEntries(expected, 35));
        int early35After = sumBeatDistance(firstTimingEntries(table, 35), firstTimingEntries(expected, 35));
        int early70Before = sumBeatDistance(firstTimingEntries(original, 70), firstTimingEntries(expected, 70));
        int early70After = sumBeatDistance(firstTimingEntries(table, 70), firstTimingEntries(expected, 70));
        int overallBefore = sumBeatDistance(originalEntries, expectedEntries);
        int overallAfter = sumBeatDistance(alignedEntries, expectedEntries);

        return new AlignmentFixture(table,
                alignmentResult,
                originalEntries,
                alignedEntries,
                early35Before,
                early35After,
                early70Before,
                early70After,
                overallBefore,
                overallAfter);
    }

    private static YassTable createTable(String content) {
        I18.setDefaultLanguage();
        YassTable table = new YassTable();
        table.init(new YassProperties());
        assertTrue(table.setText(content));
        return table;
    }

    private static List<Map<String, Integer>> firstTimingEntries(YassTable table, int limit) {
        List<Map<String, Integer>> entries = timingEntries(table);
        return entries.subList(0, Math.min(limit, entries.size()));
    }

    private static List<Map<String, Integer>> timingEntries(YassTable table) {
        List<Map<String, Integer>> entries = new ArrayList<>();
        for (int i = 0; i < table.getRowCount(); i++) {
            YassRow row = table.getRowAt(i);
            if (row == null || !row.isNoteOrPageBreak()) {
                continue;
            }
            Map<String, Integer> entry = new HashMap<>();
            entry.put("type", row.isPageBreak() ? -1 : 1);
            entry.put("beat", row.getBeatInt());
            entry.put("length", row.isNote() ? row.getLengthInt() : 0);
            entries.add(entry);
        }
        return entries;
    }

    private static int sumBeatDistance(List<Map<String, Integer>> left, List<Map<String, Integer>> right) {
        int limit = Math.min(left.size(), right.size());
        int total = 0;
        for (int i = 0; i < limit; i++) {
            total += Math.abs(left.get(i).get("beat") - right.get(i).get("beat"));
        }
        return total;
    }

    private static boolean hasNoOverlaps(YassTable table) {
        Integer nextFreeBeat = null;
        for (int i = 0; i < table.getRowCount(); i++) {
            YassRow row = table.getRowAt(i);
            if (row == null || !row.isNoteOrPageBreak()) {
                continue;
            }
            if (nextFreeBeat != null && row.getBeatInt() < nextFreeBeat) {
                return false;
            }
            nextFreeBeat = row.isNote()
                    ? row.getBeatInt() + Math.max(1, row.getLengthInt()) + 1
                    : row.getBeatInt() + 1;
        }
        return true;
    }

    private static boolean noEmptyNoteTexts(YassTable table) {
        for (int i = 0; i < table.getRowCount(); i++) {
            YassRow row = table.getRowAt(i);
            if (row != null && row.isNote() && row.getTrimmedText().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String describeEmptyNotes(YassTable table) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < table.getRowCount(); i++) {
            YassRow row = table.getRowAt(i);
            if (row != null && row.isNote() && row.getTrimmedText().isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(i).append(':').append(row.toString());
            }
        }
        return sb.toString();
    }

    private static int beatForWord(YassTable table, String word) {
        for (int i = 0; i < table.getRowCount(); i++) {
            YassRow row = table.getRowAt(i);
            if (row != null && row.isNote() && row.getText() != null && row.getText().contains(word)) {
                return row.getBeatInt();
            }
        }
        return -1;
    }

    private static List<OpenAiTranscriptSegment> extractSegments(JsonObject json) {
        List<OpenAiTranscriptSegment> segments = new ArrayList<>();
        if (!json.has("segments")) {
            return segments;
        }
        for (JsonElement segmentElement : json.getAsJsonArray("segments")) {
            JsonObject segment = segmentElement.getAsJsonObject();
            List<OpenAiTranscriptWord> words = new ArrayList<>();
            JsonArray segmentWords = segment.getAsJsonArray("words");
            if (segmentWords != null) {
                for (JsonElement wordElement : segmentWords) {
                    JsonObject word = wordElement.getAsJsonObject();
                    if (!word.has("word") || !word.has("start") || !word.has("end")) {
                        continue;
                    }
                    String text = word.get("word").getAsString();
                    words.add(new OpenAiTranscriptWord(
                            text,
                            LyricsAlignmentTokenizer.normalizeText(text),
                            (int) Math.round(word.get("start").getAsDouble() * 1000d),
                            (int) Math.round(word.get("end").getAsDouble() * 1000d)));
                }
            }
            segments.add(new OpenAiTranscriptSegment(
                    segment.has("start") ? (int) Math.round(segment.get("start").getAsDouble() * 1000d) : -1,
                    segment.has("end") ? (int) Math.round(segment.get("end").getAsDouble() * 1000d) : -1,
                    segment.has("text") ? segment.get("text").getAsString() : "",
                    words));
        }
        return segments;
    }

    private static List<OpenAiTranscriptWord> extractWords(JsonObject json) {
        List<OpenAiTranscriptWord> words = new ArrayList<>();
        JsonArray source = json.has("word_segments") ? json.getAsJsonArray("word_segments")
                : (json.has("words") ? json.getAsJsonArray("words") : new JsonArray());
        if (source.isEmpty() && json.has("segments")) {
            json.getAsJsonArray("segments").forEach(segmentElement -> {
                JsonObject segment = segmentElement.getAsJsonObject();
                JsonArray segmentWords = segment.getAsJsonArray("words");
                if (segmentWords != null) {
                    segmentWords.forEach(source::add);
                }
            });
        }
        for (JsonElement wordElement : source) {
            JsonObject word = wordElement.getAsJsonObject();
            if (!word.has("word") || !word.has("start") || !word.has("end")) {
                continue;
            }
            String text = word.get("word").getAsString();
            words.add(new OpenAiTranscriptWord(
                    text,
                    LyricsAlignmentTokenizer.normalizeText(text),
                    (int) Math.round(word.get("start").getAsDouble() * 1000d),
                    (int) Math.round(word.get("end").getAsDouble() * 1000d)));
        }
        return words;
    }

    private static String extractTranscriptText(JsonObject json) {
        if (json.has("text")) {
            return json.get("text").getAsString();
        }
        if (!json.has("segments")) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonElement segmentElement : json.getAsJsonArray("segments")) {
            String text = segmentElement.getAsJsonObject().get("text").getAsString().trim();
            if (text.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(text);
        }
        return sb.toString();
    }

    private static String loadFixture(String filename) throws IOException {
        Path path = Path.of("test", "resources", "yass", "alignment", filename);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private record AlignmentFixture(YassTable table,
                                    LyricsAlignmentResult alignmentResult,
                                    List<Map<String, Integer>> originalEntries,
                                    List<Map<String, Integer>> alignedEntries,
                                    int early35Before,
                                    int early35After,
                                    int early70Before,
                                    int early70After,
                                    int overallBefore,
                                    int overallAfter) {
    }
}

