package yass

import spock.lang.Specification
import yass.analysis.PitchDetector
import yass.integration.separation.SeparationResult
import yass.integration.transcription.openai.OpenAiTranscriptSegment
import yass.integration.transcription.openai.OpenAiTranscriptWord
import yass.integration.transcription.openai.OpenAiTranscriptionResult
import yass.wizard.WizardTranscriptionState

import java.nio.file.Files

class YassActionsWizardSpec extends Specification {

    def setupSpec() {
        I18.setDefaultLanguage()
    }

    def "offers post-wizard separation only when no separation result exists"() {
        expect:
        YassActions.shouldOfferSeparationAfterWizard(null)
        YassActions.shouldOfferSeparationAfterWizard(new WizardTranscriptionState(null, null, null, null, null))
        !YassActions.shouldOfferSeparationAfterWizard(new WizardTranscriptionState(
                null,
                null,
                null,
                new SeparationResult(null, null, null, null),
                null))
    }

    def "finds canonical transcript artifact next to a song file"() {
        given:
        def songDir = Files.createTempDirectory("yass-song")
        def songFile = songDir.resolve("Artist - Title.txt")
        Files.writeString(songFile, "#TITLE:Title\nE\n")
        def transcriptFile = songDir.resolve("yass-transcript.json")
        Files.writeString(transcriptFile, "{}")

        expect:
        YassActions.findTranscriptArtifactForSongFile(songFile.toString()) == transcriptFile.toFile()

        cleanup:
        Files.deleteIfExists(transcriptFile)
        Files.deleteIfExists(songFile)
        Files.deleteIfExists(songDir)
    }

    def "does not report transcript artifact when song folder has none"() {
        given:
        def songDir = Files.createTempDirectory("yass-song")
        def songFile = songDir.resolve("Artist - Title.txt")
        Files.writeString(songFile, "#TITLE:Title\nE\n")

        expect:
        YassActions.findTranscriptArtifactForSongFile(songFile.toString()) == null
        YassActions.findTranscriptArtifactForSongFile("") == null

        cleanup:
        Files.deleteIfExists(songFile)
        Files.deleteIfExists(songDir)
    }

    def "post wizard separation prompt mentions reusable transcript when artifact exists"() {
        expect:
        YassActions.buildPostWizardSeparationPrompt(false) == I18.get("create_song_open_separation_prompt")
        YassActions.buildPostWizardSeparationPrompt(true) ==
                I18.get("create_song_open_separation_prompt") + "\n\n" +
                I18.get("create_song_open_separation_transcript_hint")
    }

    def "builds wizard transcription fallback from subtitle file at finish"() {
        given:
        def subtitleFile = Files.createTempFile("wizard-subtitle", ".vtt")
        Files.writeString(subtitleFile, """WEBVTT

00:00:01.000 --> 00:00:03.000
HELLO THERE

00:00:04.000 --> 00:00:05.000
AGAIN

""")

        when:
        def state = YassActions.withSubtitleTranscriptFallback(null, subtitleFile.toString())

        then:
        state.transcriptionResult.sourceTag == "#SUBTITLES"
        state.transcriptionResult.transcriptText == "Hello there\nAgain"
        state.transcriptionResult.segments*.startMs == [1000, 4000]

        cleanup:
        Files.deleteIfExists(subtitleFile)
    }

    def "keeps existing wizard transcript instead of replacing it with subtitle fallback"() {
        given:
        def subtitleFile = Files.createTempFile("wizard-subtitle", ".vtt")
        Files.writeString(subtitleFile, """WEBVTT

00:00:01.000 --> 00:00:03.000
HELLO THERE

""")
        def existingResult = new OpenAiTranscriptionResult(
                null,
                null,
                "#VOCALS",
                "Existing transcript",
                [new OpenAiTranscriptWord("Existing", "existing", 0, 1000)],
                [new OpenAiTranscriptSegment(0, 1000, "Existing transcript",
                        [new OpenAiTranscriptWord("Existing", "existing", 0, 1000)])],
                [],
                false,
                null)
        def existingState = new WizardTranscriptionState(null, null, null, null, existingResult)

        expect:
        YassActions.withSubtitleTranscriptFallback(existingState, subtitleFile.toString()).is(existingState)

        cleanup:
        Files.deleteIfExists(subtitleFile)
    }

    def "refines wizard subtitle transcript against copied vocals before rebuild"() {
        given:
        int gapMs = 53200
        double bpm = 388d
        def table = new YassTable()
        table.init(new YassProperties())
        assert table.setText("""#TITLE:Test
#ARTIST:Test
#LANGUAGE:English
#BPM:388
#GAP:53200
E
""")
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
        def transcript = new OpenAiTranscriptionResult(
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
        def vocalFrames = []
        vocalFrames.addAll(densePitchFramesForBeats(gapMs, bpm, 0, 16, 1, 0.13d))
        vocalFrames.addAll(densePitchFramesForBeats(gapMs, bpm, 16, 5, 1, 0.05d))
        vocalFrames.addAll(densePitchFramesForBeats(gapMs, bpm, 25, 3, 6, 0.06d))
        vocalFrames.addAll(densePitchFramesForBeats(gapMs, bpm, 33, 7, 1, 0.23d))
        vocalFrames.addAll(densePitchFramesForBeats(gapMs, bpm, 40, 2, 0, 0.07d))
        vocalFrames.addAll(densePitchFramesForBeats(gapMs, bpm, 42, 5, 3, 0.22d))
        vocalFrames.addAll(densePitchFramesForBeats(gapMs, bpm, 48, 4, 1, 0.14d))
        vocalFrames.addAll(densePitchFramesForBeats(gapMs, bpm, 52, 6, 3, 0.10d))
        vocalFrames.addAll(densePitchFramesForBeats(gapMs, bpm, 58, 23, 0, 0.20d))
        vocalFrames.addAll(densePitchFramesForBeats(gapMs, bpm, 82, 8, 0, 0.03d))
        vocalFrames.addAll(densePitchFramesForBeats(gapMs, bpm, 90, 2, -4, 0.12d))
        vocalFrames.addAll(densePitchFramesForBeats(gapMs, bpm, 93, 5, 15, 0.06d))
        vocalFrames.addAll(densePitchFramesForBeats(gapMs, bpm, 98, 5, 0, 0.20d))
        vocalFrames.addAll(densePitchFramesForBeats(gapMs, bpm, 107, 6, 1, 0.20d))
        vocalFrames.addAll(densePitchFramesForBeats(gapMs, bpm, 114, 4, 0, 0.15d))
        vocalFrames.addAll(densePitchFramesForBeats(gapMs, bpm, 118, 3, 2, 0.07d))
        vocalFrames.addAll(densePitchFramesForBeats(gapMs, bpm, 122, 23, -2, 0.17d))

        when:
        def refined = YassActions.refineWizardTranscriptForRebuild(table, transcript, vocalFrames)

        then:
        refined.timingSourceTag == UltrastarHeaderTag.VOCALS.toString()
        refined.segments[0].words*.startMs.collect { beatForMs(it, gapMs, bpm) } == [0, 33, 42, 48, 58]
        refined.segments[1].words*.startMs.collect { beatForMs(it, gapMs, bpm) } == [90, 98, 118]
    }

    private static OpenAiTranscriptSegment segment(int startMs, int endMs, List<OpenAiTranscriptWord> words) {
        new OpenAiTranscriptSegment(startMs, endMs, words*.text.join(" "), words)
    }

    private static OpenAiTranscriptWord word(String text, int startMs, int endMs) {
        new OpenAiTranscriptWord(text, text.toLowerCase(Locale.ROOT), startMs, endMs)
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

    private static int beatForMs(int ms, int gapMs, double bpm) {
        Math.round((float) ((ms - gapMs) * bpm * 4d / 60000d))
    }
}
