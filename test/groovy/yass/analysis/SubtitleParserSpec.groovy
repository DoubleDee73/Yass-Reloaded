package yass.analysis

import spock.lang.Specification

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

    def "parse normalizes subtitles that are almost entirely all caps"() {
        given:
        def subtitleFile = Files.createTempFile("all-caps", ".srt")
        subtitleFile.toFile().text = """1
00:00:01,000 --> 00:00:02,000
THIS IS THE CHORUS

2
00:00:03,000 --> 00:00:04,000
AND THIS IS THE NEXT LINE

"""

        when:
        def parsed = SubtitleParser.parse(subtitleFile.toFile())

        then:
        parsed.values().toList() == ["This Is the Chorus", "And This Is the Next Line"]

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
