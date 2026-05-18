package yass

import spock.lang.Specification
import yass.alignment.TranscriptTimingRefinementService
import yass.analysis.PitchDetector
import yass.integration.transcription.openai.OpenAiTranscriptSegment
import yass.integration.transcription.openai.OpenAiTranscriptWord
import yass.integration.transcription.openai.OpenAiTranscriptionResult

class TranscriptTimingRefinementServiceSpec extends Specification {

    def "accepts first vocal onset as global offset when it is within five seconds of current gap"() {
        given:
        def result = transcript([
                segment(30000, 45000, [word("first", 30000, 31000)]),
                segment(50000, 60000, [word("second", 50000, 51000)])
        ])
        def pitchData = [
                frame(34.2f, 0.3d),
                frame(44.8f, 0.28d),
                frame(54.9f, 0.25d)
        ]

        when:
        def analysis = new TranscriptTimingRefinementService().analyze(result, pitchData, 30000)

        then:
        analysis.accepted
        analysis.firstTranscriptStartMs == 30000
        analysis.currentGapMs == 30000
        analysis.firstVocalOnsetMs == 34200
        analysis.transcriptToAudioOffsetMs == 4200
        analysis.proposedGapMs == 34200
        analysis.phrases[0].adjustedStartMs == 34200
        analysis.phrases[0].detectedVocalEndMs == 44840
    }

    def "uses first vocal onset near current gap before earlier global pitch"() {
        given:
        def result = transcript([
                segment(30000, 45000, [word("first", 30000, 31000)])
        ])
        def pitchData = [
                frame(10.0f, 0.3d),
                frame(31.2f, 0.3d)
        ]

        when:
        def analysis = new TranscriptTimingRefinementService().analyze(result, pitchData, 30000)

        then:
        analysis.accepted
        analysis.firstVocalOnsetMs == 31200
        analysis.gapDeltaMs == 1200
        analysis.proposedGapMs == 31200
    }

    def "falls back to first relevant vocal onset from song start when current gap window is empty"() {
        given:
        def result = transcript([
                segment(30000, 45000, [word("first", 30000, 31000)])
        ])
        def pitchData = [frame(36.1f, 0.3d)]

        when:
        def analysis = new TranscriptTimingRefinementService().analyze(result, pitchData, 30000)

        then:
        analysis.accepted
        analysis.firstVocalOnsetMs == 36100
        analysis.gapDeltaMs == 6100
        analysis.proposedGapMs == 36100
        analysis.rejectionReason == ""
    }

    def "reports unavailable when transcript or vocal frames are missing"() {
        expect:
        !new TranscriptTimingRefinementService().analyze(null, [], 0).accepted
        !new TranscriptTimingRefinementService().analyze(transcript([]), [], 0).accepted
    }

    def "uses empty transcript segment as phrase boundary anchor"() {
        given:
        def result = transcript([
                segment(30000, 45000, [word("first", 30000, 31000)]),
                new OpenAiTranscriptSegment(45000, 50000, "", []),
                segment(50000, 60000, [word("second", 50000, 51000)])
        ])
        def pitchData = [
                frame(34.2f, 0.3d),
                frame(44.8f, 0.28d),
                frame(50.8f, 0.27d)
        ]

        when:
        def analysis = new TranscriptTimingRefinementService().analyze(result, pitchData, 30000)

        then:
        analysis.accepted
        analysis.phrases.size() == 2
        analysis.phrases[0].nextAnchorMs == 49200
        analysis.phrases[0].detectedVocalEndMs == 44840
    }

    def "uses a long silence before the next anchor as the detected phrase end"() {
        given:
        def result = transcript([
                segment(30000, 45000, [word("first", 30000, 31000)]),
                segment(50000, 60000, [word("second", 50000, 51000)])
        ])
        def pitchData = [
                frame(34.2f, 0.3d),
                frame(44.8f, 0.28d),
                frame(53.8f, 0.27d)
        ]

        when:
        def analysis = new TranscriptTimingRefinementService().analyze(result, pitchData, 30000)

        then:
        analysis.accepted
        analysis.phrases[0].detectedVocalEndMs == 44840
        analysis.phrases[0].decision == "detected vocal end before long silence"
    }

    def "ignores low-energy tail frames when detecting phrase end before next anchor"() {
        given:
        def result = transcript([
                segment(14130, 16760, [word("first", 14130, 16760)]),
                segment(17560, 20070, [word("second", 17560, 20070)])
        ])
        def pitchData = [
                frame(14.13f, 0.45d),
                frame(14.80f, 0.42d),
                frame(15.40f, 0.41d),
                frame(16.00f, 0.40d),
                frame(16.35f, 0.39d),
                frame(16.50f, 0.18d),
                frame(16.62f, 0.17d),
                frame(16.74f, 0.16d),
                frame(17.56f, 0.50d),
                frame(18.05f, 0.45d)
        ]

        when:
        def analysis = new TranscriptTimingRefinementService().analyze(result, pitchData, 14130)

        then:
        analysis.accepted
        analysis.phrases[0].detectedVocalEndMs == 16390
        analysis.phrases[0].decision == "detected vocal end before long silence"
    }

    def "keeps quieter phrase-tail frames for local end detection even when they are below the global threshold"() {
        given:
        def result = transcript([
                segment(14130, 16760, [word("first", 14130, 16760)]),
                segment(17560, 20070, [word("second", 17560, 20070)])
        ])
        def pitchData = [
                frame(14.13f, 0.30d),
                frame(14.80f, 0.28d),
                frame(15.40f, 0.27d),
                frame(16.35f, 0.15d),
                frame(17.56f, 1.00d),
                frame(18.05f, 0.90d)
        ]

        when:
        def analysis = new TranscriptTimingRefinementService().analyze(result, pitchData, 14130)

        then:
        analysis.accepted
        analysis.phrases[0].detectedVocalEndMs == 16390
    }

    def "extends phrase end across a continuous quieter tail after the last strong frame"() {
        given:
        def result = transcript([
                segment(17560, 20070, [word("life", 17560, 20070)]),
                segment(20266, 22000, [word("next", 20266, 22000)])
        ])
        def pitchData = [
                frame(17.56f, 0.40d),
                frame(18.05f, 0.38d),
                frame(18.50f, 0.37d),
                frame(18.72f, 0.11d),
                frame(18.96f, 0.10d),
                frame(19.18f, 0.09d),
                frame(20.27f, 0.42d)
        ]

        when:
        def analysis = new TranscriptTimingRefinementService().analyze(result, pitchData, 17560)

        then:
        analysis.accepted
        analysis.phrases[0].detectedVocalEndMs == 19220
    }

    def "keeps OpenAI segment anchors when segment words are missing but top-level words exist"() {
        given:
        def result = new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("vocals.wav"),
                "#AUDIO",
                "first\nsecond",
                [word("first", 30000, 31000), word("second", 50000, 51000)],
                [
                        new OpenAiTranscriptSegment(30000, 45000, "first", []),
                        new OpenAiTranscriptSegment(50000, 60000, "second", [])
                ],
                [],
                false,
                null)
        def pitchData = [
                frame(34.2f, 0.3d),
                frame(44.8f, 0.28d),
                frame(54.9f, 0.25d)
        ]

        when:
        def analysis = new TranscriptTimingRefinementService().analyze(result, pitchData, 30000)

        then:
        analysis.accepted
        analysis.phrases.size() == 2
        analysis.phrases[0].transcriptStartMs == 30000
        analysis.phrases[0].nextAnchorMs == 54200
    }

    def "refines subtitle phrase boundaries against detected vocal onsets and endings"() {
        given:
        def result = new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("vocals.wav"),
                "#SUBTITLES",
                "I can think of younger days\nWhen living for my life\nWas everything",
                [
                        word("I", 14310, 14718),
                        word("can", 14718, 15127),
                        word("think", 15127, 15535),
                        word("of", 15535, 15943),
                        word("younger", 15943, 16352),
                        word("days", 16352, 16760),
                        word("When", 16760, 17422),
                        word("living", 17422, 18084),
                        word("for", 18084, 18746),
                        word("my", 18746, 19408),
                        word("life", 19408, 20070),
                        word("Was", 20070, 20885),
                        word("everything", 20885, 21700)
                ],
                [
                        segment(14310, 16760, [
                                word("I", 14310, 14718),
                                word("can", 14718, 15127),
                                word("think", 15127, 15535),
                                word("of", 15535, 15943),
                                word("younger", 15943, 16352),
                                word("days", 16352, 16760)
                        ]),
                        segment(16760, 20070, [
                                word("When", 16760, 17422),
                                word("living", 17422, 18084),
                                word("for", 18084, 18746),
                                word("my", 18746, 19408),
                                word("life", 19408, 20070)
                        ]),
                        segment(20070, 21700, [
                                word("Was", 20070, 20885),
                                word("everything", 20885, 21700)
                        ])
                ],
                [],
                false,
                new File("audio-transcript.openai.json"),
                "#SUBTITLES",
                "#SUBTITLES")
        def pitchData = [
                frame(14.31f, 0.30d),
                frame(14.72f, 0.28d),
                frame(15.12f, 0.27d),
                frame(15.53f, 0.26d),
                frame(15.94f, 0.25d),
                frame(16.35f, 0.25d),
                frame(17.56f, 0.29d),
                frame(18.05f, 0.28d),
                frame(18.55f, 0.27d),
                frame(18.95f, 0.27d),
                frame(19.42f, 0.28d),
                frame(20.08f, 0.30d),
                frame(20.88f, 0.29d),
                frame(21.45f, 0.28d)
        ]

        when:
        def refined = new TranscriptTimingRefinementService().refineForAlignment(result, pitchData, 14310, "#VOCALS")

        then:
        refined.timingSourceTag == "#VOCALS"
        refined.segments.size() == 3
        refined.segments[1].startMs == 17560
        refined.segments[1].endMs == 19460
        refined.segments[1].words[0].text == "When"
        refined.segments[1].words[0].startMs == 17560
        refined.segments[1].words[-1].text == "life"
        refined.segments[1].words[-1].endMs == 19460
    }

    def "pulls a later phrase start earlier when the next vocal block already begins before the transcript anchor"() {
        given:
        def result = new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("vocals.wav"),
                "#SUBTITLES",
                "first line\nsecond line",
                [
                        word("first", 1000, 1500),
                        word("line", 1500, 2000),
                        word("second", 2500, 3000),
                        word("line", 3000, 3500)
                ],
                [
                        segment(1000, 2000, [
                                word("first", 1000, 1500),
                                word("line", 1500, 2000)
                        ]),
                        segment(2500, 3500, [
                                word("second", 2500, 3000),
                                word("line", 3000, 3500)
                        ])
                ],
                [],
                false,
                new File("audio-transcript.openai.json"),
                "#SUBTITLES",
                "#SUBTITLES")
        def pitchData = [
                frame(1.00f, 0.40d),
                frame(1.40f, 0.35d),
                frame(2.15f, 0.42d),
                frame(2.30f, 0.38d)
        ]

        when:
        def refined = new TranscriptTimingRefinementService().refineForAlignment(result, pitchData, 1000, "#VOCALS")

        then:
        refined.segments[0].endMs == 1440
        refined.segments[1].startMs == 2150
        refined.segments[1].words[0].startMs == 2150
    }

    def "aligns refined word windows to detected intra-phrase audio islands when counts match"() {
        given:
        def result = new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("vocals.wav"),
                "#SUBTITLES",
                "one two three\nnext",
                [
                        word("one", 0, 500),
                        word("two", 500, 1000),
                        word("three", 1000, 1500),
                        word("next", 1700, 2200)
                ],
                [
                        segment(0, 1500, [
                                word("one", 0, 500),
                                word("two", 500, 1000),
                                word("three", 1000, 1500)
                        ]),
                        segment(1700, 2200, [
                                word("next", 1700, 2200)
                        ])
                ],
                [],
                false,
                new File("audio-transcript.openai.json"),
                "#SUBTITLES",
                "#SUBTITLES")
        def pitchData = [
                frame(0.00f, 0.40d),
                frame(0.10f, 0.35d),
                frame(0.24f, 0.03d),
                frame(0.38f, 0.02d),
                frame(0.50f, 0.42d),
                frame(0.55f, 0.38d),
                frame(0.72f, 0.03d),
                frame(0.84f, 0.02d),
                frame(0.90f, 0.41d),
                frame(0.95f, 0.37d),
                frame(1.70f, 0.50d)
        ]

        when:
        def refined = new TranscriptTimingRefinementService().refineForAlignment(result, pitchData, 0, "#VOCALS")

        then:
        refined.segments[0].words*.startMs == [0, 500, 900]
        refined.segments[0].words*.endMs == [140, 590, 990]
    }

    def "grid-based signal windows split nearby onsets that would otherwise merge"() {
        given:
        def result = new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("vocals.wav"),
                "#SUBTITLES",
                "one two\nnext",
                [
                        word("one", 0, 250),
                        word("two", 250, 500),
                        word("next", 800, 1100)
                ],
                [
                        segment(0, 500, [
                                word("one", 0, 250),
                                word("two", 250, 500)
                        ]),
                        segment(800, 1100, [
                                word("next", 800, 1100)
                        ])
                ],
                [],
                false,
                new File("audio-transcript.openai.json"),
                "#SUBTITLES",
                "#SUBTITLES")
        def pitchData = [
                frame(0.00f, 0.40d),
                frame(0.05f, 0.36d),
                frame(0.18f, 0.42d),
                frame(0.23f, 0.38d),
                frame(0.80f, 0.50d)
        ]

        when:
        def refined = new TranscriptTimingRefinementService().refineForAlignment(result, pitchData, 0, "#VOCALS", 120d)

        then:
        refined.segments[0].words[0].startMs == 0
        refined.segments[0].words[0].endMs <= 130
        refined.segments[0].words[1].startMs >= 125
    }

    def "grid-based signal windows split a sustained block on a clear pitch jump"() {
        given:
        def result = new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("vocals.wav"),
                "#SUBTITLES",
                "of younger\nnext",
                [
                        word("of", 0, 250),
                        word("younger", 250, 500),
                        word("next", 800, 1100)
                ],
                [
                        segment(0, 500, [
                                word("of", 0, 250),
                                word("younger", 250, 500)
                        ]),
                        segment(800, 1100, [
                                word("next", 800, 1100)
                        ])
                ],
                [],
                false,
                new File("audio-transcript.openai.json"),
                "#SUBTITLES",
                "#SUBTITLES")
        def pitchData = [
                new PitchDetector.PitchData(0.00f, 0, "C4", 261.63d, 0.40d),
                new PitchDetector.PitchData(0.04f, 0, "C4", 261.63d, 0.38d),
                new PitchDetector.PitchData(0.14f, 5, "F4", 349.23d, 0.41d),
                new PitchDetector.PitchData(0.18f, 5, "F4", 349.23d, 0.39d),
                new PitchDetector.PitchData(0.80f, 7, "G4", 392.00d, 0.50d)
        ]

        when:
        def refined = new TranscriptTimingRefinementService().refineForAlignment(result, pitchData, 0, "#VOCALS", 60d)

        then:
        refined.segments[0].words[0].startMs == 0
        refined.segments[0].words[0].endMs <= 130
        refined.segments[0].words[1].startMs >= 125
    }

    def "uses the first strong signal window as the refined phrase start"() {
        given:
        def result = new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("vocals.wav"),
                "#SUBTITLES",
                "I can\nnext",
                [
                        word("I", 0, 300),
                        word("can", 300, 700),
                        word("next", 1000, 1400)
                ],
                [
                        segment(0, 700, [
                                word("I", 0, 300),
                                word("can", 300, 700)
                        ]),
                        segment(1000, 1400, [
                                word("next", 1000, 1400)
                        ])
                ],
                [],
                false,
                new File("audio-transcript.openai.json"),
                "#SUBTITLES",
                "#SUBTITLES")
        def pitchData = [
                frame(0.00f, 0.05d),
                frame(0.05f, 0.08d),
                frame(0.14f, 0.40d),
                frame(0.20f, 0.36d),
                frame(0.50f, 0.42d),
                frame(0.56f, 0.38d),
                frame(1.00f, 0.45d)
        ]

        when:
        def refined = new TranscriptTimingRefinementService().refineForAlignment(result, pitchData, 0, "#VOCALS")

        then:
        refined.segments[0].startMs == 140
        refined.segments[0].words[0].startMs == 140
    }

    def "opening phrase start can move earlier than the global onset when local anchor audio starts earlier"() {
        given:
        def result = new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("vocals.wav"),
                "#SUBTITLES",
                "I can\nnext",
                [
                        word("I", 14310, 14600),
                        word("can", 14600, 15000),
                        word("next", 16000, 16500)
                ],
                [
                        segment(14310, 15000, [
                                word("I", 14310, 14600),
                                word("can", 14600, 15000)
                        ]),
                        segment(16000, 16500, [
                                word("next", 16000, 16500)
                        ])
                ],
                [],
                false,
                new File("audio-transcript.openai.json"),
                "#SUBTITLES",
                "#SUBTITLES")
        def pitchData = [
                frame(14.14f, 0.08d),
                frame(14.18f, 0.28d),
                frame(14.25f, 0.26d),
                frame(14.50f, 0.30d),
                frame(14.56f, 0.27d),
                frame(16.00f, 1.00d),
                frame(16.10f, 0.90d)
        ]

        when:
        def refined = new TranscriptTimingRefinementService().refineForAlignment(result, pitchData, 14310, "#VOCALS")

        then:
        refined.segments[0].startMs == 14140
        refined.segments[0].words[0].startMs == 14140
    }

    def "opening phrase start uses local anchor energy instead of a stronger later peak in the same phrase"() {
        given:
        def result = new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("vocals.wav"),
                "#SUBTITLES",
                "I can think\nnext",
                [
                        word("I", 14310, 14600),
                        word("can", 14600, 15000),
                        word("think", 15000, 16760),
                        word("next", 17560, 18000)
                ],
                [
                        segment(14310, 16760, [
                                word("I", 14310, 14600),
                                word("can", 14600, 15000),
                                word("think", 15000, 16760)
                        ]),
                        segment(17560, 18000, [
                                word("next", 17560, 18000)
                        ])
                ],
                [],
                false,
                new File("audio-transcript.openai.json"),
                "#SUBTITLES",
                "#SUBTITLES")
        def pitchData = [
                frame(14.14f, 0.08d),
                frame(14.18f, 0.10d),
                frame(14.29f, 0.18d),
                frame(14.40f, 0.17d),
                frame(15.70f, 0.60d),
                frame(15.76f, 0.58d),
                frame(17.56f, 0.50d)
        ]

        when:
        def refined = new TranscriptTimingRefinementService().refineForAlignment(result, pitchData, 14310, "#VOCALS", 244d)

        then:
        refined.segments[0].startMs == 14140
        refined.segments[0].words[0].startMs == 14140
    }

    def "onset-anchor refinement keeps the first word at the refined phrase start and the last word at the refined phrase end"() {
        given:
        def service = new TranscriptTimingRefinementService()
        def method = TranscriptTimingRefinementService.getDeclaredMethod(
                "refineWordsFromOnsetAnchors",
                List,
                List,
                Integer.TYPE,
                Integer.TYPE)
        method.accessible = true
        def words = [
                word("I", 14310, 14718),
                word("can", 14718, 15127),
                word("days", 15127, 16760)
        ]

        when:
        def refined = (List<OpenAiTranscriptWord>) method.invoke(service,
                words,
                [14290, 14420, 15690],
                14140,
                16390)

        then:
        refined[0].startMs == 14140
        refined[-1].endMs == 16390
    }

    def "signal-window refinement keeps the first word at the refined phrase start and the last word at the refined phrase end"() {
        given:
        def service = new TranscriptTimingRefinementService()
        def method = TranscriptTimingRefinementService.getDeclaredMethod(
                "refineWordsFromSignalWindows",
                List,
                List,
                Integer.TYPE,
                Integer.TYPE,
                List)
        method.accessible = true
        def words = [
                word("I", 14310, 14718),
                word("can", 14718, 15127),
                word("days", 15127, 16760)
        ]
        def signalWindowClass = TranscriptTimingRefinementService.getDeclaredClasses()
                .find { it.simpleName == 'SignalWindow' }
        def constructor = signalWindowClass.getDeclaredConstructor(Integer.TYPE, Integer.TYPE)
        constructor.accessible = true
        def windows = [
                constructor.newInstance(14290, 14380),
                constructor.newInstance(14420, 14520),
                constructor.newInstance(15690, 15910)
        ]

        when:
        def refined = (List<OpenAiTranscriptWord>) method.invoke(service,
                words,
                windows,
                14140,
                16390,
                [])

        then:
        refined[0].startMs == 14140
        refined[-1].endMs == 16390
    }

    def "shared signal windows can split words at internal onset anchors before falling back to ratios"() {
        given:
        def service = new TranscriptTimingRefinementService()
        def method = TranscriptTimingRefinementService.getDeclaredMethod(
                "subdivideWordsIntoWindow",
                List,
                Integer.TYPE,
                Integer.TYPE,
                List,
                Boolean.TYPE)
        method.accessible = true
        def words = [
                word("think", 15127, 15535),
                word("of", 15535, 15943)
        ]

        when:
        def refined = (List<OpenAiTranscriptWord>) method.invoke(service,
                words,
                14700,
                15622,
                [14722, 14922, 15147, 15272, 15522],
                false)

        then:
        refined*.startMs == [14700, 14922]
        refined*.endMs == [14922, 15622]
    }

    def "coarse occupancy windows still use raw onset anchors for internal word starts"() {
        given:
        def service = new TranscriptTimingRefinementService()
        def method = TranscriptTimingRefinementService.getDeclaredMethod(
                "refineWordsForPhrase",
                List,
                TranscriptTimingRefinementService.PhraseTiming,
                OpenAiTranscriptSegment)
        method.accessible = true
        def words = [
                word("I", 14310, 14718),
                word("can", 14718, 15127),
                word("think", 15127, 15535),
                word("of", 15535, 15943),
                word("younger", 15943, 16352),
                word("days", 16352, 16760)
        ]
        def timing = new TranscriptTimingRefinementService.PhraseTiming(
                0,
                14310,
                14147,
                16600,
                14147,
                17400,
                16671,
                [14147, 14393, 14700, 15745],
                [14147, 14372, 14597, 14722, 14922, 15147, 15272, 15522, 15747],
                [
                        signalWindow(14147, 14331),
                        signalWindow(14393, 14639),
                        signalWindow(14700, 15622),
                        signalWindow(15745, 16360)
                ],
                "I can think of younger days",
                "detected vocal end")
        def segment = segment(14310, 16760, words)

        when:
        def refined = (List<OpenAiTranscriptWord>) method.invoke(service, words, timing, segment)

        then:
        refined[2].startMs == 14700
        refined[2].endMs == 14922
        refined[3].startMs == 14922
        refined[4].startMs == 15147
        refined[5].startMs == 15745
    }

    def "onset anchors take precedence over equally-sized signal windows for word starts"() {
        given:
        def result = new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("vocals.wav"),
                "#SUBTITLES",
                "I can think of younger days\nnext",
                [
                        word("I", 14310, 14718),
                        word("can", 14718, 15127),
                        word("think", 15127, 15535),
                        word("of", 15535, 15943),
                        word("younger", 15943, 16352),
                        word("days", 16352, 16760),
                        word("next", 17560, 18000)
                ],
                [
                        segment(14310, 16760, [
                                word("I", 14310, 14718),
                                word("can", 14718, 15127),
                                word("think", 15127, 15535),
                                word("of", 15535, 15943),
                                word("younger", 15943, 16352),
                                word("days", 16352, 16760)
                        ]),
                        segment(17560, 18000, [
                                word("next", 17560, 18000)
                        ])
                ],
                [],
                false,
                new File("audio-transcript.openai.json"),
                "#SUBTITLES",
                "#SUBTITLES")
        def pitchData = [
                frame(14.14f, 0.08d),
                frame(14.18f, 0.10d),
                frame(14.40f, 0.15d),
                frame(14.42f, 0.14d),
                frame(14.75f, 0.16d),
                frame(14.98f, 0.15d),
                frame(15.14f, 0.17d),
                frame(15.27f, 0.16d),
                frame(15.70f, 0.60d),
                frame(15.76f, 0.58d),
                frame(17.56f, 0.50d)
        ]

        when:
        def refined = new TranscriptTimingRefinementService().refineForAlignment(result, pitchData, 14310, "#VOCALS", 244d)

        then:
        refined.segments[0].words*.startMs == [14140, 14390, 14740, 14965, 15140, 15690]
    }

    def "detectOnsetAnchors exposes the expected coarse starts for the first buble phrase"() {
        given:
        def service = new TranscriptTimingRefinementService()
        def method = TranscriptTimingRefinementService.getDeclaredMethod(
                "detectOnsetAnchors",
                List,
                Integer.TYPE,
                Integer.TYPE)
        method.accessible = true
        def pitchData = [
                frame(14.14f, 0.08d),
                frame(14.18f, 0.10d),
                frame(14.40f, 0.15d),
                frame(14.42f, 0.14d),
                frame(14.75f, 0.16d),
                frame(14.98f, 0.15d),
                frame(15.14f, 0.17d),
                frame(15.27f, 0.16d),
                frame(15.70f, 0.60d),
                frame(15.76f, 0.58d)
        ]

        when:
        def onsets = (List<Integer>) method.invoke(service, pitchData, 14140, 16390)

        then:
        onsets == [14390, 14740, 14965, 15140, 15265, 15690]
    }

    def "opening phrase beat occupancy groups micro-onsets into coarse word windows"() {
        given:
        def result = new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("vocals.wav"),
                "#SUBTITLES",
                "I can think of younger days\nnext",
                [
                        word("I", 14310, 14718),
                        word("can", 14718, 15127),
                        word("think", 15127, 15535),
                        word("of", 15535, 15943),
                        word("younger", 15943, 16352),
                        word("days", 16352, 16760),
                        word("next", 17560, 18000)
                ],
                [
                        segment(14310, 16760, [
                                word("I", 14310, 14718),
                                word("can", 14718, 15127),
                                word("think", 15127, 15535),
                                word("of", 15535, 15943),
                                word("younger", 15943, 16352),
                                word("days", 16352, 16760)
                        ]),
                        segment(17560, 18000, [
                                word("next", 17560, 18000)
                        ])
                ],
                [],
                false,
                new File("audio-transcript.openai.json"),
                "#SUBTITLES",
                "#SUBTITLES")
        def pitchData = []
        pitchData.addAll(denseFrames(14.15f, 14.32f, 0.22d))
        pitchData.addAll(denseFrames(14.40f, 14.52f, 0.26d))
        pitchData.addAll(denseFrames(14.75f, 14.84f, 0.27d))
        pitchData.addAll(denseFrames(14.98f, 15.06f, 0.24d))
        pitchData.addAll(denseFrames(15.14f, 15.58f, 0.23d))
        pitchData.addAll(denseFrames(15.70f, 16.62f, 0.32d))
        pitchData.add(frame(17.56f, 0.40d))

        when:
        def refined = new TranscriptTimingRefinementService().refineForAlignment(result, pitchData, 14310, "#VOCALS", 244d)

        then:
        refined.segments[0].words*.startMs == [14150, 14396, 14765, 14949, 15134, 15687]
    }

    def "opening phrase can prefer coarse occupancy windows even when they undershoot word count"() {
        given:
        def result = new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("vocals.wav"),
                "#SUBTITLES",
                "I can think of younger days\nnext",
                [
                        word("I", 14310, 14718),
                        word("can", 14718, 15127),
                        word("think", 15127, 15535),
                        word("of", 15535, 15943),
                        word("younger", 15943, 16352),
                        word("days", 16352, 16760),
                        word("next", 17560, 18000)
                ],
                [
                        segment(14310, 16760, [
                                word("I", 14310, 14718),
                                word("can", 14718, 15127),
                                word("think", 15127, 15535),
                                word("of", 15535, 15943),
                                word("younger", 15943, 16352),
                                word("days", 16352, 16760)
                        ]),
                        segment(17560, 18000, [
                                word("next", 17560, 18000)
                        ])
                ],
                [],
                false,
                new File("audio-transcript.openai.json"),
                "#SUBTITLES",
                "#SUBTITLES")
        def pitchData = []
        pitchData.addAll(denseFrames(14.15f, 14.52f, 0.25d))
        pitchData.addAll(denseFrames(14.75f, 15.06f, 0.24d))
        pitchData.addAll(denseFrames(15.14f, 16.62f, 0.23d))
        pitchData.add(frame(17.56f, 0.40d))

        when:
        def analysis = new TranscriptTimingRefinementService().analyze(result, pitchData, 14310, 244d)

        then:
        analysis.phrases[0].signalWindows*.startMs == [14150, 14765, 15134]
    }

    def "subdivides a shared occupancy window using internal onset anchors before transcript ratios"() {
        given:
        def result = new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("vocals.wav"),
                "#SUBTITLES",
                "I can think of younger days\nnext",
                [
                        word("I", 14310, 14718),
                        word("can", 14718, 15127),
                        word("think", 15127, 15535),
                        word("of", 15535, 15943),
                        word("younger", 15943, 16352),
                        word("days", 16352, 16760),
                        word("next", 17560, 18000)
                ],
                [
                        segment(14310, 16760, [
                                word("I", 14310, 14718),
                                word("can", 14718, 15127),
                                word("think", 15127, 15535),
                                word("of", 15535, 15943),
                                word("younger", 15943, 16352),
                                word("days", 16352, 16760)
                        ]),
                        segment(17560, 18000, [
                                word("next", 17560, 18000)
                        ])
                ],
                [],
                false,
                new File("audio-transcript.openai.json"),
                "#SUBTITLES",
                "#SUBTITLES")
        def pitchData = []
        pitchData.addAll(denseFrames(14.15f, 14.32f, 0.22d))
        pitchData.addAll(denseFrames(14.40f, 14.52f, 0.26d))
        pitchData.addAll(denseFrames(14.75f, 14.84f, 0.27d))
        pitchData.addAll(denseFrames(14.98f, 15.06f, 0.24d))
        pitchData.addAll(denseFrames(15.14f, 15.58f, 0.23d))
        pitchData.addAll(denseFrames(15.70f, 16.62f, 0.32d))
        pitchData.add(frame(17.56f, 0.40d))

        when:
        def refined = new TranscriptTimingRefinementService().refineForAlignment(result, pitchData, 14310, "#VOCALS", 244d)

        then:
        refined.segments[0].words*.startMs == [14150, 14396, 14765, 14949, 15134, 15687]
    }

    def "short support windows can extend a multisyllabic word instead of forcing a new word start"() {
        given:
        def service = new TranscriptTimingRefinementService()
        def method = TranscriptTimingRefinementService.getDeclaredMethod(
                "refineWordsFromSignalWindows",
                List,
                List,
                Integer.TYPE,
                Integer.TYPE,
                List)
        method.accessible = true
        def words = [
                word("When", 16760, 17422),
                word("living", 17422, 18084),
                word("for", 18084, 18746),
                word("my", 18746, 19408),
                word("life", 19408, 20070)
        ]
        def windows = [
                signalWindow(17583, 18013),
                signalWindow(18075, 18259),
                signalWindow(18444, 19427)
        ]

        when:
        def refined = method.invoke(service, words, windows, 17583, 19942, [17583, 18058, 18433]) as List<OpenAiTranscriptWord>

        then:
        refined*.startMs == [17583, 17921, 18444, 18772, 19099]
        refined*.endMs == [17921, 18259, 18772, 19099, 19942]
    }

    def "occupancy windows can be preferred for undercovered phrases when they still preserve coarse word structure"() {
        given:
        def service = new TranscriptTimingRefinementService()
        def method = TranscriptTimingRefinementService.getDeclaredMethod(
                "shouldUseOccupancyWindows",
                Integer.TYPE,
                List,
                List,
                Integer.TYPE)
        method.accessible = true
        def occupancyWindows = [
                signalWindow(17583, 18013),
                signalWindow(18075, 18259),
                signalWindow(18444, 19427)
        ]
        def signalWindows = [
                signalWindow(17583, 18075),
                signalWindow(18075, 18321),
                signalWindow(18321, 18444),
                signalWindow(18444, 19551)
        ]

        expect:
        method.invoke(service, 5, occupancyWindows, signalWindows, 3)
    }

    def "applies occupancy refinement to every phrase when occupancy windows are a better fit"() {
        given:
        List<OpenAiTranscriptSegment> segments = []
        List<OpenAiTranscriptWord> words = []
        List<PitchDetector.PitchData> pitchData = []
        7.times { index ->
            int offsetMs = index * 4000
            float offsetSeconds = offsetMs / 1000f
            def phraseWords = [
                    shiftedWord("I", 14310, 14718, offsetMs),
                    shiftedWord("can", 14718, 15127, offsetMs),
                    shiftedWord("think", 15127, 15535, offsetMs),
                    shiftedWord("of", 15535, 15943, offsetMs),
                    shiftedWord("younger", 15943, 16352, offsetMs),
                    shiftedWord("days", 16352, 16760, offsetMs)
            ]
            words.addAll(phraseWords)
            segments.add(segment(14310 + offsetMs, 16760 + offsetMs, phraseWords))
            pitchData.addAll(denseFrames((14.15f + offsetSeconds) as float, (14.32f + offsetSeconds) as float, 0.22d))
            pitchData.addAll(denseFrames((14.40f + offsetSeconds) as float, (14.52f + offsetSeconds) as float, 0.26d))
            pitchData.addAll(denseFrames((14.75f + offsetSeconds) as float, (14.84f + offsetSeconds) as float, 0.27d))
            pitchData.addAll(denseFrames((14.98f + offsetSeconds) as float, (15.06f + offsetSeconds) as float, 0.24d))
            pitchData.addAll(denseFrames((15.14f + offsetSeconds) as float, (15.58f + offsetSeconds) as float, 0.23d))
            pitchData.addAll(denseFrames((15.70f + offsetSeconds) as float, (16.62f + offsetSeconds) as float, 0.32d))
        }
        def result = new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("vocals.wav"),
                "#SUBTITLES",
                segments*.text.join("\n"),
                words,
                segments,
                [],
                false,
                new File("audio-transcript.openai.json"),
                "#SUBTITLES",
                "#SUBTITLES")

        when:
        def analysis = new TranscriptTimingRefinementService().analyze(result, pitchData, 14310, 244d)

        then:
        analysis.phrases[5].signalWindows*.startMs == [34150, 34396, 34765, 34949, 35134, 35687]
        analysis.phrases[6].signalWindows*.startMs == [38150, 38396, 38765, 38949, 39134, 39687]
    }

    def "refines subtitles and openai transcripts but skips whisperx-style caches"() {
        given:
        def service = new TranscriptTimingRefinementService()

        expect:
        service.shouldRefineForAlignment(new OpenAiTranscriptionResult(
                null, null, "#SUBTITLES", "", [], [], [], false, null, "#SUBTITLES", "#SUBTITLES"))
        service.shouldRefineForAlignment(new OpenAiTranscriptionResult(
                null, null, "#LRCLIB", "", [], [], [], false, null, "#LRCLIB", "#LRCLIB"))
        service.shouldRefineForAlignment(new OpenAiTranscriptionResult(
                null, null, "#AUDIO", "", [], [], [], false, new File("audio-transcript.openai.json")))
        !service.shouldRefineForAlignment(new OpenAiTranscriptionResult(
                null, null, "#VOCALS", "", [], [], [], false, new File("vocals-transcript.json")))
    }

    private static OpenAiTranscriptionResult transcript(List<OpenAiTranscriptSegment> segments) {
        new OpenAiTranscriptionResult(
                new File("audio.wav"),
                new File("vocals.wav"),
                "#VOCALS",
                segments*.text.join("\n"),
                segments.collectMany { it.words },
                segments,
                [],
                false,
                null)
    }

    private static OpenAiTranscriptSegment segment(int startMs, int endMs, List<OpenAiTranscriptWord> words) {
        new OpenAiTranscriptSegment(startMs, endMs, words*.text.join(" "), words)
    }

    private static OpenAiTranscriptWord word(String text, int startMs, int endMs) {
        new OpenAiTranscriptWord(text, text.toLowerCase(Locale.ROOT), startMs, endMs)
    }

    private static OpenAiTranscriptWord shiftedWord(String text, int startMs, int endMs, int offsetMs) {
        word(text, startMs + offsetMs, endMs + offsetMs)
    }

    private static PitchDetector.PitchData frame(float timeSeconds, double energy) {
        new PitchDetector.PitchData(timeSeconds, 0, "C4", 261.63d, energy)
    }

    private static List<PitchDetector.PitchData> denseFrames(float startSeconds,
                                                             float endSeconds,
                                                             double energy) {
        List<PitchDetector.PitchData> frames = []
        for (float t = startSeconds; t <= endSeconds + 0.0001f; t += 0.01f) {
            frames.add(frame(t, energy))
        }
        return frames
    }

    private static Object signalWindow(int startMs, int endMs) {
        def clazz = TranscriptTimingRefinementService.getDeclaredClasses().find { it.simpleName == 'SignalWindow' }
        def constructor = clazz.getDeclaredConstructor(Integer.TYPE, Integer.TYPE)
        constructor.accessible = true
        constructor.newInstance(startMs, endMs)
    }
}
