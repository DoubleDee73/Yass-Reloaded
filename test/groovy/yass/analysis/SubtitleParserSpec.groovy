package yass.analysis

import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class SubtitleParserSpec extends Specification {

    def "parse preserves mixed-case subtitles"() {
        given:
        def subtitleFile = Files.createTempFile("mixed-case", ".srt")
        subtitleFile.toFile().text = """1
00:00:01,000 --> 00:00:02,000
Hello there, General Kenobi

"""

        when:
        def parsed = SubtitleParser.parse(subtitleFile.toFile())

        then:
        parsed.values().toList() == ["Hello there, General Kenobi"]

        cleanup:
        Files.deleteIfExists(subtitleFile)
    }

    def "parseCues preserves cue end timestamps"() {
        given:
        def subtitleFile = Files.createTempFile("cue-times", ".srt")
        subtitleFile.toFile().text = """1
00:00:01,250 --> 00:00:03,750
Hello timing

"""

        when:
        def cues = SubtitleParser.parseCues(subtitleFile.toFile())

        then:
        cues.size() == 1
        cues[0].startMs() == 1250
        cues[0].endMs() == 3750
        cues[0].text() == "Hello timing"

        cleanup:
        Files.deleteIfExists(subtitleFile)
    }

    def "parse normalizes all-caps lyric subtitles to sentence case"() {
        given:
        def subtitleFile = Files.createTempFile("all-caps", ".srt")
        subtitleFile.toFile().text = """1
00:00:01,000 --> 00:00:02,000
THIS IS THE CHORUS

2
00:00:03,000 --> 00:00:04,000
AND I'LL KEEP I IN PRONOUN CASE. I'M READY

"""

        when:
        def parsed = SubtitleParser.parse(subtitleFile.toFile())

        then:
        parsed.values().toList() == ["This is the chorus", "And I'll keep I in pronoun case. I'm ready"]

        cleanup:
        Files.deleteIfExists(subtitleFile)
    }

    def "parse reads youtube subtitles as UTF-8"() {
        given:
        def subtitleFile = Files.createTempFile("utf8-subtitles", ".vtt")
        Files.writeString(subtitleFile, """WEBVTT

00:00:01.000 --> 00:00:02.000
I CAN\u2019T TELL YOU WHY

""", StandardCharsets.UTF_8)

        when:
        def parsed = SubtitleParser.parse(subtitleFile.toFile())

        then:
        parsed.values().toList() == ["I can\u2019t tell you why"]

        cleanup:
        Files.deleteIfExists(subtitleFile)
    }

    def "parse collapses rolling youtube subtitles into incremental lyric lines"() {
        given:
        def subtitleFile = Files.createTempFile("rolling-captions", ".srt")
        subtitleFile.toFile().text = """1
00:00:13,840 --> 00:00:16,310
[Music]
Talk to me.

2
00:00:16,310 --> 00:00:16,320
Talk to me.

3
00:00:16,320 --> 00:00:21,429
Talk to me.
You never talk to me.

4
00:00:21,429 --> 00:00:21,439
You never talk to me.

5
00:00:21,439 --> 00:00:25,590
You never talk to me.
It seems that I can speak,

6
00:00:25,590 --> 00:00:25,600
It seems that I can speak,

7
00:00:25,600 --> 00:00:30,950
It seems that I can speak,
but I can hear my voice out.

"""

        when:
        def parsed = SubtitleParser.parse(subtitleFile.toFile())

        then:
        parsed.values().toList() == [
                "Talk to me.",
                "You never talk to me.",
                "It seems that I can speak,",
                "but I can hear my voice out."
        ]

        cleanup:
        Files.deleteIfExists(subtitleFile)
    }
}
