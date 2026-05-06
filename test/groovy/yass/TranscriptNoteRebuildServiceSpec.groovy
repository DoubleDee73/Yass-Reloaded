package yass

import spock.lang.Specification
import yass.alignment.TranscriptNoteRebuildService
import yass.integration.transcription.openai.OpenAiTranscriptSegment
import yass.integration.transcription.openai.OpenAiTranscriptWord
import yass.integration.transcription.openai.OpenAiTranscriptionResult

class TranscriptNoteRebuildServiceSpec extends Specification {

    def "rebuild derives page breaks from long pauses even without transcript segments"() {
        given:
        def table = createTable()
        def result = transcriptionResult([
                word("Hello", 0, 500),
                word("darkness", 500, 1000),
                word("my", 1000, 1250),
                word("old", 1250, 1500),
                word("friend", 2800, 3400)
        ])

        when:
        def rebuildResult = new TranscriptNoteRebuildService().transcript(table, result)

        then:
        rebuildResult.pageBreakCount == 1
        pageBreakBeats(table).size() == 1
    }

    def "rebuild splits oversized phrases near sentence starts when segments are missing"() {
        given:
        def table = createTable()
        def result = transcriptionResult([
                word("we", 0, 250),
                word("walk", 250, 500),
                word("through", 500, 750),
                word("the", 750, 1000),
                word("city", 1000, 1250),
                word("streets", 1250, 1500),
                word("and", 1500, 1750),
                word("listen", 1750, 2000),
                word("to", 2000, 2250),
                word("every", 2250, 2500),
                word("sound", 2500, 2750),
                word("Then", 2750, 3000),
                word("we", 3000, 3250),
                word("run", 3250, 3500)
        ])

        when:
        def rebuildResult = new TranscriptNoteRebuildService().transcript(table, result)

        then:
        rebuildResult.pageBreakCount == 1
        pageBreakBeats(table).size() == 1
        wordTexts(table).contains("Then")
    }

    def "rebuild uses existing hyphenator for long vowel-rich words"() {
        given:
        def table = createTable()
        table.hyphenator.fallbackHyphenations = ["banana": "ba\u00ADna\u00ADna"]
        def result = transcriptionResult([word("banana", 0, 750)])

        when:
        new TranscriptNoteRebuildService().transcript(table, result)

        then:
        noteTexts(table) == ["ba", "nana"]
        noteLengths(table) == [1, 1]
    }

    def "rebuild also splits long two-vowel words when hyphenation exists"() {
        given:
        def table = createTable()
        table.hyphenator.fallbackHyphenations = ["trying": "try\u00ADing"]
        def result = transcriptionResult([word("trying", 0, 3250)])

        when:
        new TranscriptNoteRebuildService().transcript(table, result)

        then:
        noteTexts(table) == ["try", "ing"]
        noteLengths(table) == [5, 6]
    }

    def "rebuild derives display phrases with line breaks from words when segments are missing"() {
        given:
        def result = transcriptionResult([
                word("My", 0, 300),
                word("one", 300, 600),
                word("and", 600, 900),
                word("only", 900, 1200),
                word("love", 1200, 1500),
                word("My", 2800, 3100),
                word("one", 3100, 3400),
                word("and", 3400, 3700),
                word("only", 3700, 4000),
                word("love", 4000, 4300)
        ])

        when:
        def phrases = new TranscriptNoteRebuildService().deriveDisplayPhrases(result)

        then:
        phrases.values().toList() == ["My one and only love", "My one and only love"]
        phrases.keySet().toList() == [0, 2800]
    }

    def "rebuild keeps transcript segments intact when they are shorter than twenty seconds"() {
        given:
        def result = transcriptionResultWithSegments([
                segment(0, 12000, [
                        word("We", 0, 200),
                        word("would", 300, 600),
                        word("talk", 700, 1000)
                ]),
                segment(13000, 18000, [
                        word("every", 13000, 13300),
                        word("day", 13400, 13800)
                ])
        ])

        when:
        def phrases = new TranscriptNoteRebuildService().deriveDisplayPhrases(result)

        then:
        phrases.values().toList() == ["We would talk", "every day"]
        phrases.keySet().toList() == [0, 13000]
    }

    def "rebuild only splits transcript segments when segment duration exceeds twenty seconds"() {
        given:
        def result = transcriptionResultWithSegments([
                segment(0, 23000, [
                        word("we", 0, 250),
                        word("walk", 250, 500),
                        word("through", 500, 750),
                        word("the", 750, 1000),
                        word("city", 1000, 1250),
                        word("streets", 1250, 1500),
                        word("and", 1500, 1750),
                        word("listen", 1750, 2000),
                        word("to", 2000, 2250),
                        word("every", 2250, 2500),
                        word("sound", 2500, 2750),
                        word("Then", 2750, 3000),
                        word("we", 3000, 3250),
                        word("run", 3250, 3500)
                ])
        ])

        when:
        def phrases = new TranscriptNoteRebuildService().deriveDisplayPhrases(result)

        then:
        phrases.values().toList() == ["we walk through the city streets and listen to every sound", "Then we run"]
    }

    def "rebuild pulls trailing low confidence words closer to stable neighbors"() {
        given:
        def table = createTable()
        def result = transcriptionResultWithSegments([
                segment(1000, 3960, [
                        word("mile", 1000, 2000, 0.9d),
                        word("a", 3800, 3820, 0.0d),
                        word("minute", 3820, 3960, 0.1d)
                ])
        ])

        when:
        new TranscriptNoteRebuildService().transcript(table, result)

        then:
        noteTexts(table) == ["mile", "a", "minute"]
        noteBeats(table)[1] < 11
        noteBeats(table)[2] < 12
    }

    private static OpenAiTranscriptionResult transcriptionResult(List<OpenAiTranscriptWord> words) {
        new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("audio.wav"),
                "#AUDIO",
                words*.text.join(" "),
                words,
                [],
                [],
                false,
                null)
    }

    private static OpenAiTranscriptionResult transcriptionResultWithSegments(List<OpenAiTranscriptSegment> segments) {
        new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("audio.wav"),
                "#AUDIO",
                segments.collect { it.text }.join("\n"),
                segments.collectMany { it.words },
                segments,
                [],
                false,
                null)
    }

    private static OpenAiTranscriptWord word(String text, int startMs, int endMs) {
        new OpenAiTranscriptWord(text, text.toLowerCase(), startMs, endMs)
    }

    private static OpenAiTranscriptWord word(String text, int startMs, int endMs, Double score) {
        new OpenAiTranscriptWord(text, text.toLowerCase(), startMs, endMs, score)
    }

    private static OpenAiTranscriptSegment segment(int startMs, int endMs, List<OpenAiTranscriptWord> words) {
        new OpenAiTranscriptSegment(startMs, endMs, words*.text.join(" "), words)
    }

    private static YassTable createTable() {
        I18.setDefaultLanguage()
        def table = new YassTable()
        def properties = new YassProperties()
        table.init(properties)
        assert table.setText("""#TITLE:Test
#ARTIST:Test
#LANGUAGE:English
#BPM:60
#GAP:0
: 0 4 0 test
E
""")
        def hyphenator = new YassHyphenator("EN")
        hyphenator.language = "EN"
        hyphenator.yassProperties = properties
        table.hyphenator = hyphenator
        table
    }

    private static List<Integer> pageBreakBeats(YassTable table) {
        (0..<table.rowCount)
                .collect { table.getRowAt(it) }
                .findAll { it?.pageBreak }
                .collect { it.beatInt }
    }

    private static List<String> wordTexts(YassTable table) {
        noteTexts(table)
    }

    private static List<String> noteTexts(YassTable table) {
        (0..<table.rowCount)
                .collect { table.getRowAt(it) }
                .findAll { it?.note }
                .collect { it.trimmedText }
    }

    private static List<Integer> noteLengths(YassTable table) {
        (0..<table.rowCount)
                .collect { table.getRowAt(it) }
                .findAll { it?.note }
                .collect { it.lengthInt }
    }

    private static List<Integer> noteBeats(YassTable table) {
        (0..<table.rowCount)
                .collect { table.getRowAt(it) }
                .findAll { it?.note }
                .collect { it.beatInt }
    }
}
