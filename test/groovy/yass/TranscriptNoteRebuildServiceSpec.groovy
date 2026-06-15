package yass

import spock.lang.Specification
import yass.alignment.TranscriptNoteRebuildService
import yass.alignment.TranscriptTimingRefinementService
import yass.analysis.PitchDetector
import yass.integration.transcription.openai.OpenAiTranscriptSegment
import yass.integration.transcription.openai.OpenAiTranscriptWord
import yass.integration.transcription.openai.OpenAiTranscriptionResult

class TranscriptNoteRebuildServiceSpec extends Specification {

    def setupSpec() {
        I18.setDefaultLanguage()
    }

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

    def "rebuild can align freshly created notes to melody when pitch data is provided"() {
        given:
        def table = createTrackingTable()
        def result = transcriptionResultWithSegments([
                segment(1000, 3000, [
                        word("Hello", 1000, 1800),
                        word("world", 1800, 3000)
                ])
        ])
        def pitchData = [
                new yass.analysis.PitchDetector.PitchData(1.05f, 6, "F#4", 370.0f),
                new yass.analysis.PitchDetector.PitchData(1.55f, 6, "F#4", 370.0f),
                new yass.analysis.PitchDetector.PitchData(2.05f, 6, "F#4", 370.0f),
                new yass.analysis.PitchDetector.PitchData(2.55f, 6, "F#4", 370.0f)
        ]

        when:
        new TranscriptNoteRebuildService().transcript(table, result, pitchData)

        then:
        table.alignToMelodyCalled
        table.alignedRows.size() == 3
        table.alignedRows.every { row -> table.actualAlignedRows.any { actual -> actual.is(row) } }
        table.alignmentContext?.origin() == YassTable.AlignToMelodyOrigin.CREATE_WIZARD
        table.alignedPitchData == pitchData
    }

    def "rebuild uses refined vocal windows for dense subtitle opening lines"() {
        given:
        def table = createTable()
        int gapMs = 53200
        double bpm = 388d
        table.setBPM(bpm)
        def firstLineWords = [
                word("I", 52291, 53083),
                word("can\u2019t", 53083, 53875),
                word("tell", 53875, 54666),
                word("you", 54666, 55458),
                word("why", 55458, 56250)
        ]
        def nextLineWords = [
                word("But", 56333, 57208),
                word("something", 57208, 58083),
                word("inside", 58083, 58958)
        ]
        def result = new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("vocals.wav"),
                "#SUBTITLES",
                "I can\u2019t tell you why\nBut something inside",
                firstLineWords + nextLineWords,
                [
                        segment(52291, 56250, firstLineWords),
                        segment(56333, 58958, nextLineWords)
                ],
                [],
                false,
                null,
                "#SUBTITLES",
                "#SUBTITLES")
        def pitchData = []
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 0, 16, 1, 0.13d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 16, 5, 1, 0.05d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 25, 3, 6, 0.06d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 33, 7, 1, 0.23d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 40, 2, 0, 0.07d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 42, 5, 3, 0.22d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 48, 4, 1, 0.14d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 52, 6, 3, 0.10d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 58, 23, 0, 0.20d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 82, 8, 0, 0.03d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 90, 2, -4, 0.12d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 93, 5, 15, 0.06d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 98, 5, 0, 0.20d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 107, 6, 1, 0.20d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 114, 4, 0, 0.15d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 118, 3, 2, 0.07d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 122, 23, -2, 0.17d))
        def refined = new TranscriptTimingRefinementService().refineForAlignment(result, pitchData, gapMs, "#VOCALS", bpm)

        when:
        new TranscriptNoteRebuildService().transcript(table, refined, pitchData)

        then:
        noteTexts(table).take(5) == ["I", "can't", "tell", "you", "why"]
        noteBeats(table).take(5) == [0, 33, 42, 48, 58]
        noteLengths(table).take(5) == [16, 7, 5, 4, 23]
        noteHeights(table).take(5) == [1, 1, 3, 1, 0]
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
        initializeTable(table)
        table
    }

    private static TrackingYassTable createTrackingTable() {
        def table = new TrackingYassTable()
        initializeTable(table)
        table
    }

    private static void initializeTable(YassTable table) {
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
    }

    private static class TrackingYassTable extends YassTable {
        boolean alignToMelodyCalled
        List<YassRow> alignedRows
        List<yass.analysis.PitchDetector.PitchData> alignedPitchData
        AlignToMelodyContext alignmentContext
        List<YassRow> actualAlignedRows = []

        @Override
        void alignToMelody(List<YassRow> rows,
                           List<yass.analysis.PitchDetector.PitchData> pitchData,
                           AlignToMelodyContext context) {
            alignToMelodyCalled = true
            alignedRows = rows
            alignedPitchData = pitchData
            alignmentContext = context
            actualAlignedRows = (0..<rowCount)
                    .collect { getRowAt(it) }
                    .findAll { it?.isNote() }
        }
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

    private static List<Integer> noteHeights(YassTable table) {
        (0..<table.rowCount)
                .collect { table.getRowAt(it) }
                .findAll { it?.note }
                .collect { it.heightInt }
    }

    private static List<PitchDetector.PitchData> densePitchFramesForBeats(int gapMs,
                                                                           double bpm,
                                                                           int startBeat,
                                                                           int length,
                                                                           int pitch,
                                                                           double energy) {
        double beatMs = 60000d / (4d * bpm)
        float startSeconds = ((double) gapMs + startBeat * beatMs) / 1000d
        float endSeconds = ((double) gapMs + (startBeat + length) * beatMs) / 1000d
        List<PitchDetector.PitchData> frames = []
        for (float t = startSeconds; t < endSeconds - 0.0001f; t += 0.01f) {
            frames.add(new PitchDetector.PitchData(t, pitch, "C4", 261.63d, energy))
        }
        return frames
    }
}
