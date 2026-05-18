package yass

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import spock.lang.Specification
import yass.alignment.LyricsAlignmentResult
import yass.alignment.LyricsAlignmentService
import yass.alignment.LyricsAlignmentTokenizer
import yass.integration.transcription.openai.OpenAiTranscriptSegment
import yass.integration.transcription.openai.OpenAiTranscriptWord
import yass.integration.transcription.openai.OpenAiTranscriptionResult

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class LyricsAlignmentServiceSpec extends Specification {

    def "calimero OpenAI alignment applies gap, improves timing and avoids overlaps"() {
        given:
        def fixture = alignFixture("calimero-transcript.json", "calimero.openai-transcript.json")

        expect:
        fixture.alignmentResult.matchedRegions >= 10
        fixture.table.gap > 0d
        fixture.table.gap == 7980d
        fixture.early35After <= Math.round(fixture.early35Before * 0.35d)
        fixture.early70After <= Math.round(fixture.early70Before * 0.20d)
        fixture.overallAfter <= Math.round(fixture.overallBefore * 0.10d)
        hasNoOverlaps(fixture.table)
        fixture.originalEntries.size() == fixture.alignedEntries.size()
    }

    def "calimero WhisperX alignment applies gap, improves timing and avoids overlaps"() {
        given:
        def fixture = alignFixture("calimero-whisperx-transcript.json", "calimero-whisperx-transcript.json")

        expect:
        fixture.alignmentResult.matchedRegions >= 8
        fixture.table.gap > 0d
        fixture.table.gap == 8480d
        fixture.early35After <= Math.round(fixture.early35Before * 0.46d)
        fixture.early70After <= Math.round(fixture.early70Before * 0.35d)
        fixture.alignmentResult.movedNotes > 0
        hasNoOverlaps(fixture.table)
        fixture.originalEntries.size() == fixture.alignedEntries.size()
    }

    def "calimero WhisperX rebuild creates fresh notes from transcript"() {
        given:
        def originalText = loadFixture("calimero-original.txt")
        def transcriptJson = JsonParser.parseString(loadFixture("calimero-whisperx-transcript.json")).asJsonObject
        def table = createTable(originalText)
        def words = extractWords(transcriptJson)
        def segments = extractSegments(transcriptJson)
        def result = new OpenAiTranscriptionResult(
                new File("calimero.opus"),
                new File("calimero.opus"),
                "#VOCALS",
                extractTranscriptText(transcriptJson),
                words,
                segments,
                LyricsAlignmentTokenizer.buildTokens(table),
                true,
                new File("calimero-whisperx-transcript.json"))

        when:
        def rebuildResult = new yass.alignment.TranscriptNoteRebuildService().transcript(table, result)

        then:
        table.gap == 8480d
        rebuildResult.noteCount > 0
        rebuildResult.pageBreakCount > 0
        table.firstNote.beatInt == 0
        hasNoOverlaps(table)
        table.hasLyrics()
    }

    def "tovenaar WhisperX rebuild uses transcript timing and avoids empty notes"() {
        given:
        def originalText = loadFixture("tovenaar-original.txt")
        def transcriptJson = JsonParser.parseString(loadFixture("tovenaar-whisperx-transcript.json")).asJsonObject
        def table = createTable(originalText)
        def words = extractWords(transcriptJson)
        def segments = extractSegments(transcriptJson)
        def result = new OpenAiTranscriptionResult(
                new File("tovenaar.opus"),
                new File("tovenaar-vocals.m4a"),
                "#VOCALS",
                extractTranscriptText(transcriptJson),
                words,
                segments,
                LyricsAlignmentTokenizer.buildTokens(table),
                true,
                new File("tovenaar-whisperx-transcript.json"))

        when:
        def rebuildResult = new yass.alignment.TranscriptNoteRebuildService().transcript(table, result)

        then:
        rebuildResult.noteCount > 0
        hasNoOverlaps(table)
        noEmptyNoteTexts(table)
        beatForWord(table, "oud?") >= 18
    }

    def "alignment falls back to current table tokens when transcript artifact has no lyric tokens"() {
        given:
        def originalText = loadFixture("calimero-original.txt")
        def alignedText = loadFixture("calimero-aligned.txt")
        def transcriptJson = JsonParser.parseString(loadFixture("calimero-transcript.json")).asJsonObject
        def table = createTable(originalText)
        def expected = createTable(alignedText)
        def words = extractWords(transcriptJson)
        def segments = extractSegments(transcriptJson)
        def result = new OpenAiTranscriptionResult(
                new File("calimero.opus"),
                new File("calimero.opus"),
                "#AUDIO",
                extractTranscriptText(transcriptJson),
                words,
                segments,
                List.of(),
                true,
                new File("calimero.openai-transcript.json"))
        def beforeEntries = timingEntries(table)
        def expectedEntries = timingEntries(expected)

        when:
        def alignmentResult = new LyricsAlignmentService().alignAndApply(table, result)
        def afterEntries = timingEntries(table)

        then:
        alignmentResult.matchedRegions >= 10
        alignmentResult.movedNotes > 0
        sumBeatDistance(afterEntries, expectedEntries) < sumBeatDistance(beforeEntries, expectedEntries)
        hasNoOverlaps(table)
    }

    def "alignment updates existing gap to refined opening word start when first anchor is safe"() {
        given:
        def table = createTable('''#TITLE:Test
#ARTIST:Spec
#BPM:240
#GAP:14310
: 0 4 6 I 
: 6 4 6 can 
: 12 4 6 go 
- 17
E
''')
        def result = new OpenAiTranscriptionResult(
                new File("test.wav"),
                new File("test.wav"),
                "#VOCALS",
                "I can go",
                [
                        new OpenAiTranscriptWord("I", "i", 14131, 14500),
                        new OpenAiTranscriptWord("can", "can", 14500, 14900),
                        new OpenAiTranscriptWord("go", "go", 14900, 15300)
                ],
                [
                        new OpenAiTranscriptSegment(14131, 15300, "I can go", [
                                new OpenAiTranscriptWord("I", "i", 14131, 14500),
                                new OpenAiTranscriptWord("can", "can", 14500, 14900),
                                new OpenAiTranscriptWord("go", "go", 14900, 15300)
                        ])
                ],
                LyricsAlignmentTokenizer.buildTokens(table),
                false,
                null)

        when:
        def alignmentResult = new LyricsAlignmentService().alignAndApply(table, result)

        then:
        alignmentResult.matchedRegions >= 1
        table.gap == 14130d
    }

    def "alignment redistributes note lengths across transcript word windows"() {
        given:
        def table = createTable('''#TITLE:Test
#ARTIST:Spec
#BPM:60
#GAP:1000
: 0 1 6 I 
: 2 1 6 can 
: 4 1 6 go
E
''')
        def result = new OpenAiTranscriptionResult(
                new File("test.wav"),
                new File("test.wav"),
                "#VOCALS",
                "I can go",
                [
                        new OpenAiTranscriptWord("I", "i", 1000, 2000),
                        new OpenAiTranscriptWord("can", "can", 2000, 3000),
                        new OpenAiTranscriptWord("go", "go", 3000, 4500)
                ],
                [
                        new OpenAiTranscriptSegment(1000, 4500, "I can go", [
                                new OpenAiTranscriptWord("I", "i", 1000, 2000),
                                new OpenAiTranscriptWord("can", "can", 2000, 3000),
                                new OpenAiTranscriptWord("go", "go", 3000, 4500)
                        ])
                ],
                LyricsAlignmentTokenizer.buildTokens(table),
                false,
                null)

        when:
        new LyricsAlignmentService().alignAndApply(table, result)

        then:
        table.getFirstNote().beatInt == 0
        table.getFirstNote().lengthInt == 3
        table.getRowAt(table.getFirstNoteRow() + 1).beatInt == 4
        table.getRowAt(table.getFirstNoteRow() + 1).lengthInt == 3
        table.getRowAt(table.getFirstNoteRow() + 2).beatInt == 8
        table.getRowAt(table.getFirstNoteRow() + 2).lengthInt == 6
    }

    def "alignment redistributes opening page notes even when first anchor crosses a page break"() {
        given:
        def table = createTable('''#TITLE:Test
#ARTIST:Spec
#BPM:60
#GAP:1000
: 0 1 6 I 
: 2 1 6 can 
: 4 1 6 think 
: 6 1 6 of 
: 8 1 6 youn
: 10 1 6 ger 
: 12 1 6 days 
- 14
: 16 1 6 When 
: 18 1 6 liv
: 20 1 6 ing 
E
''')
        def words = [
                new OpenAiTranscriptWord("I", "i", 1000, 1400),
                new OpenAiTranscriptWord("can", "can", 1400, 1800),
                new OpenAiTranscriptWord("think", "think", 1800, 2200),
                new OpenAiTranscriptWord("of", "of", 2200, 2600),
                new OpenAiTranscriptWord("younger", "younger", 2600, 3400),
                new OpenAiTranscriptWord("days", "days", 3400, 5400),
                new OpenAiTranscriptWord("When", "when", 7000, 7600),
                new OpenAiTranscriptWord("living", "living", 7600, 8600)
        ]
        def result = new OpenAiTranscriptionResult(
                new File("test.wav"),
                new File("test.wav"),
                "#VOCALS",
                "I can think of younger days When living",
                words,
                [
                        new OpenAiTranscriptSegment(1000, 5400, "I can think of younger days", words.subList(0, 6)),
                        new OpenAiTranscriptSegment(7000, 8600, "When living", words.subList(6, 8))
                ],
                LyricsAlignmentTokenizer.buildTokens(table),
                false,
                null)

        when:
        new LyricsAlignmentService().alignAndApply(table, result)

        then:
        table.getRowAt(table.getFirstNoteRow() + 6).beatInt == 12
        table.getRowAt(table.getFirstNoteRow() + 6).lengthInt == 6
        table.getRowAt(table.getFirstNoteRow() + 8).beatInt == 24
    }

    // --- helpers ---

    private static AlignmentFixture alignFixture(String transcriptFixture, String cacheFileName) {
        def originalText = loadFixture("calimero-original.txt")
        def alignedText = loadFixture("calimero-aligned.txt")
        def transcriptJson = JsonParser.parseString(loadFixture(transcriptFixture)).asJsonObject

        def original = createTable(originalText)
        def table = createTable(originalText)
        def expected = createTable(alignedText)
        def words = extractWords(transcriptJson)
        def segments = extractSegments(transcriptJson)
        def result = new OpenAiTranscriptionResult(
                new File("calimero.opus"),
                new File("calimero.opus"),
                "#AUDIO",
                extractTranscriptText(transcriptJson),
                words,
                segments,
                LyricsAlignmentTokenizer.buildTokens(table),
                true,
                new File(cacheFileName))

        def alignmentResult = new LyricsAlignmentService().alignAndApply(table, result)
        def originalEntries = timingEntries(original)
        def alignedEntries = timingEntries(table)
        def expectedEntries = timingEntries(expected)
        int early35Before = sumBeatDistance(firstTimingEntries(original, 35), firstTimingEntries(expected, 35))
        int early35After = sumBeatDistance(firstTimingEntries(table, 35), firstTimingEntries(expected, 35))
        int early70Before = sumBeatDistance(firstTimingEntries(original, 70), firstTimingEntries(expected, 70))
        int early70After = sumBeatDistance(firstTimingEntries(table, 70), firstTimingEntries(expected, 70))
        int overallBefore = sumBeatDistance(originalEntries, expectedEntries)
        int overallAfter = sumBeatDistance(alignedEntries, expectedEntries)

        new AlignmentFixture(table, alignmentResult, originalEntries, alignedEntries,
                early35Before, early35After, early70Before, early70After, overallBefore, overallAfter)
    }

    private static YassTable createTable(String content) {
        I18.setDefaultLanguage()
        YassTable table = new YassTable()
        table.init(new YassProperties())
        assert table.setText(content)
        table
    }

    private static List<Map<String, Integer>> firstTimingEntries(YassTable table, int limit) {
        def entries = timingEntries(table)
        entries.subList(0, Math.min(limit, entries.size()))
    }

    private static List<Map<String, Integer>> timingEntries(YassTable table) {
        def entries = []
        for (int i = 0; i < table.rowCount; i++) {
            YassRow row = table.getRowAt(i)
            if (row == null || !row.isNoteOrPageBreak()) continue
            entries << [type: row.pageBreak ? -1 : 1, beat: row.beatInt, length: row.note ? row.lengthInt : 0]
        }
        entries
    }

    private static int sumBeatDistance(List<Map<String, Integer>> left, List<Map<String, Integer>> right) {
        int limit = Math.min(left.size(), right.size())
        int total = 0
        for (int i = 0; i < limit; i++) {
            total += Math.abs(left[i].beat - right[i].beat)
        }
        total
    }

    private static boolean hasNoOverlaps(YassTable table) {
        Integer nextFreeBeat = null
        for (int i = 0; i < table.rowCount; i++) {
            YassRow row = table.getRowAt(i)
            if (row == null || !row.isNoteOrPageBreak()) continue
            if (nextFreeBeat != null && row.beatInt < nextFreeBeat) return false
            nextFreeBeat = row.note ? row.beatInt + Math.max(1, row.lengthInt) + 1 : row.beatInt + 1
        }
        true
    }

    private static boolean noEmptyNoteTexts(YassTable table) {
        for (int i = 0; i < table.rowCount; i++) {
            YassRow row = table.getRowAt(i)
            if (row != null && row.note && row.trimmedText.isEmpty()) return false
        }
        true
    }

    private static int beatForWord(YassTable table, String word) {
        for (int i = 0; i < table.rowCount; i++) {
            YassRow row = table.getRowAt(i)
            if (row != null && row.note && row.text?.contains(word)) return row.beatInt
        }
        -1
    }

    private static List<OpenAiTranscriptSegment> extractSegments(JsonObject json) {
        List<OpenAiTranscriptSegment> segments = []
        if (!json.has("segments")) return segments
        for (JsonElement segmentElement : json.getAsJsonArray("segments")) {
            JsonObject segment = segmentElement.asJsonObject
            List<OpenAiTranscriptWord> words = []
            JsonArray segmentWords = segment.getAsJsonArray("words")
            if (segmentWords != null) {
                for (JsonElement wordElement : segmentWords) {
                    JsonObject word = wordElement.asJsonObject
                    if (!word.has("word") || !word.has("start") || !word.has("end")) continue
                    String text = word.get("word").asString
                    words << new OpenAiTranscriptWord(
                            text,
                            LyricsAlignmentTokenizer.normalizeText(text),
                            (int) Math.round(word.get("start").asDouble * 1000d),
                            (int) Math.round(word.get("end").asDouble * 1000d))
                }
            }
            segments << new OpenAiTranscriptSegment(
                    segment.has("start") ? (int) Math.round(segment.get("start").asDouble * 1000d) : -1,
                    segment.has("end") ? (int) Math.round(segment.get("end").asDouble * 1000d) : -1,
                    segment.has("text") ? segment.get("text").asString : "",
                    words)
        }
        segments
    }

    private static List<OpenAiTranscriptWord> extractWords(JsonObject json) {
        List<OpenAiTranscriptWord> words = []
        JsonArray source = json.has("word_segments") ? json.getAsJsonArray("word_segments")
                : (json.has("words") ? json.getAsJsonArray("words") : new JsonArray())
        if (source.isEmpty() && json.has("segments")) {
            for (JsonElement segmentElement : json.getAsJsonArray("segments")) {
                JsonObject segment = segmentElement.asJsonObject
                JsonArray segmentWords = segment.getAsJsonArray("words")
                if (segmentWords != null) {
                    for (JsonElement w : segmentWords) source.add(w)
                }
            }
        }
        for (JsonElement wordElement : source) {
            JsonObject word = wordElement.asJsonObject
            if (!word.has("word") || !word.has("start") || !word.has("end")) continue
            String text = word.get("word").asString
            words << new OpenAiTranscriptWord(
                    text,
                    LyricsAlignmentTokenizer.normalizeText(text),
                    (int) Math.round(word.get("start").asDouble * 1000d),
                    (int) Math.round(word.get("end").asDouble * 1000d))
        }
        words
    }

    private static String extractTranscriptText(JsonObject json) {
        if (json.has("text")) return json.get("text").asString
        if (!json.has("segments")) return ""
        def parts = []
        for (JsonElement segmentElement : json.getAsJsonArray("segments")) {
            String text = segmentElement.asJsonObject.get("text").asString.trim()
            if (!text.isEmpty()) parts << text
        }
        parts.join(' ')
    }

    private static String loadFixture(String filename) {
        Path path = Path.of("test", "resources", "yass", "alignment", filename)
        Files.readString(path, StandardCharsets.UTF_8)
    }

    private static class AlignmentFixture {
        YassTable table
        LyricsAlignmentResult alignmentResult
        List<Map<String, Integer>> originalEntries
        List<Map<String, Integer>> alignedEntries
        int early35Before, early35After
        int early70Before, early70After
        int overallBefore, overallAfter

        AlignmentFixture(YassTable table, LyricsAlignmentResult alignmentResult,
                         List<Map<String, Integer>> originalEntries, List<Map<String, Integer>> alignedEntries,
                         int early35Before, int early35After, int early70Before, int early70After,
                         int overallBefore, int overallAfter) {
            this.table = table
            this.alignmentResult = alignmentResult
            this.originalEntries = originalEntries
            this.alignedEntries = alignedEntries
            this.early35Before = early35Before
            this.early35After = early35After
            this.early70Before = early70Before
            this.early70After = early70After
            this.overallBefore = overallBefore
            this.overallAfter = overallAfter
        }
    }
}
