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

    def "with unknown gap (0) skips pre-song dialog noise and anchors on the first real vocal entry"() {
        given:
        // Audio ripped from a video: low-level dialog/bleed for the first ~3s, then silence, then the
        // song. The real vocal entry at 5.0s rises from a silent lead-in through a short attack ramp.
        def result = transcript([
                segment(0, 8000, [word("first", 0, 1000)])
        ])
        def pitchData = []
        // dialog/bleed 0.5-3.0s, all well under the quiet threshold (peak is 0.50)
        for (float t = 0.5f; t < 3.0f; t += 0.05f) {
            pitchData.add(frame(t, 0.06d))
        }
        // silent gap 3.0 - 5.0 (no frames)
        // attack ramp + sustained vocal from 5.0s
        pitchData.add(frame(5.00f, 0.20d))   // attack start (above quiet 0.075, below strong 0.20)
        pitchData.add(frame(5.02f, 0.26d))
        pitchData.add(frame(5.04f, 0.37d))
        pitchData.add(frame(5.06f, 0.48d))   // crosses strong here
        for (float t = 5.08f; t < 6.5f; t += 0.02f) {
            pitchData.add(frame(t, 0.50d))
        }

        when:
        def analysis = new TranscriptTimingRefinementService().analyze(result, pitchData, 0)

        then:
        analysis.accepted
        // anchors on the attack start ~5000, not the dialog (~500-3000) nor mid-attack (~5060)
        Math.abs(analysis.firstVocalOnsetMs - 5000) <= 40
    }

    def "with a set gap, still uses the window around it rather than vocal-entry detection"() {
        given:
        def result = transcript([
                segment(30000, 45000, [word("first", 30000, 31000)])
        ])
        // A strong early frame at 26s sits within +/-5s of the 30s gap; the gap-window path takes the
        // first significant frame in that window (26.0s), unlike the vocal-entry path.
        def pitchData = [
                frame(26.0f, 0.30d),
                frame(31.0f, 0.30d)
        ]

        when:
        def analysis = new TranscriptTimingRefinementService().analyze(result, pitchData, 30000)

        then:
        analysis.accepted
        analysis.firstVocalOnsetMs == 26000
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
                Integer.TYPE,
                List)
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
                16390,
                null)

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

    // Characterization of the "Hush" first-phrase regression: a #LRC line of 6 words is
    // split by the half-beat grid into 22 tiny signal windows. shouldPreferSignalWindowsOverOnsets
    // flips the whole phrase onto those windows because one late word ("cobra") trips the
    // >=120ms trigger, which drags "closer" ~290ms late. The onset anchors place every word,
    // including "closer", much closer to the manual reference. Fixture values are the exact
    // onsets/windows captured in ~/.yass/log.txt for phrase=0.
    def "over-fragmented signal windows must not override clean onset anchors for #LRC phrases"() {
        given:
        def service = new TranscriptTimingRefinementService()
        def method = TranscriptTimingRefinementService.getDeclaredMethod(
                "refineWordsForPhrase",
                List,
                TranscriptTimingRefinementService.PhraseTiming,
                OpenAiTranscriptSegment)
        method.accessible = true
        def words = [
                word("It's", 30250, 31068),
                word("coming", 31068, 31887),
                word("closer,", 31887, 32705),
                word("quiet", 32705, 33523),
                word("the", 33523, 34342),
                word("cobra", 34342, 35160)
        ]
        def onsets = [30331, 30556, 30846, 31056, 31581, 31906, 32082,
                      32806, 33356, 33506, 33631, 33936, 34056]
        def timing = new TranscriptTimingRefinementService.PhraseTiming(
                0,
                30250,
                30331,
                35160,
                30331,
                35267,
                34754,
                onsets,
                onsets,
                [
                        signalWindow(30331, 30434),
                        signalWindow(30434, 30537),
                        signalWindow(30537, 30743),
                        signalWindow(30743, 30846),
                        signalWindow(30846, 31052),
                        signalWindow(31052, 31464),
                        signalWindow(31567, 31670),
                        signalWindow(31670, 31876),
                        signalWindow(31876, 31979),
                        signalWindow(31979, 32082),
                        signalWindow(32082, 32288),
                        signalWindow(32700, 32803),
                        signalWindow(32803, 32906),
                        signalWindow(32906, 33215),
                        signalWindow(33215, 33318),
                        signalWindow(33318, 33421),
                        signalWindow(33421, 33524),
                        signalWindow(33627, 33730),
                        signalWindow(33730, 33833),
                        signalWindow(33936, 34039),
                        signalWindow(34039, 34142),
                        signalWindow(34142, 34754)
                ],
                "It's coming closer, quiet the cobra",
                "detected vocal end")
        def segment = segment(30250, 35160, words)

        when:
        def refined = (List<OpenAiTranscriptWord>) method.invoke(service, words, timing, segment)

        then:
        // "closer," (index 2) should land on its onset (~31581), not the late window start (31876).
        Math.abs(refined[2].startMs - 31581) <= 60
    }

    // Characterization of the "Hush" "the cobra" mis-placement: the energy splits "quiet the cobra"
    // into three blocks (32700-33524, 33627-33833, 33936-34754), but onset normalization merges the
    // genuine "the" onset (33631) away and leaves "the" sitting at 33356 inside "quiet"'s sustained
    // tail. That leaves the strong, clean energy block at 33627 completely unused. "the" should snap
    // into that empty block. Fixture values are the exact onsets/windows captured for phrase=0.
    def "an orphaned word snaps into an unused energy block instead of sharing the previous block"() {
        given:
        def service = new TranscriptTimingRefinementService()
        def method = TranscriptTimingRefinementService.getDeclaredMethod(
                "refineWordsForPhrase",
                List,
                TranscriptTimingRefinementService.PhraseTiming,
                OpenAiTranscriptSegment)
        method.accessible = true
        def words = [
                word("It's", 30250, 31068),
                word("coming", 31068, 31887),
                word("closer,", 31887, 32705),
                word("quiet", 32705, 33523),
                word("the", 33523, 34342),
                word("cobra", 34342, 35160)
        ]
        def onsets = [30331, 30556, 30846, 31056, 31581, 31906, 32082,
                      32806, 33356, 33506, 33631, 33936, 34056]
        def timing = new TranscriptTimingRefinementService.PhraseTiming(
                0,
                30250,
                30331,
                35160,
                30331,
                35267,
                34754,
                onsets,
                onsets,
                [
                        signalWindow(30331, 30434),
                        signalWindow(30434, 30537),
                        signalWindow(30537, 30743),
                        signalWindow(30743, 30846),
                        signalWindow(30846, 31052),
                        signalWindow(31052, 31464),
                        signalWindow(31567, 31670),
                        signalWindow(31670, 31876),
                        signalWindow(31876, 31979),
                        signalWindow(31979, 32082),
                        signalWindow(32082, 32288),
                        signalWindow(32700, 32803),
                        signalWindow(32803, 32906),
                        signalWindow(32906, 33215),
                        signalWindow(33215, 33318),
                        signalWindow(33318, 33421),
                        signalWindow(33421, 33524),
                        signalWindow(33627, 33730),
                        signalWindow(33730, 33833),
                        signalWindow(33936, 34039),
                        signalWindow(34039, 34142),
                        signalWindow(34142, 34754)
                ],
                "It's coming closer, quiet the cobra",
                "detected vocal end")
        def segment = segment(30250, 35160, words)

        when:
        def refined = (List<OpenAiTranscriptWord>) method.invoke(service, words, timing, segment)

        then:
        // "the" (index 4) should snap into the unused energy block at 33627, not stay at 33356.
        Math.abs(refined[4].startMs - 33627) <= 60
        // "quiet" (index 3) and "cobra" (index 5) keep their placements.
        Math.abs(refined[3].startMs - 32806) <= 60
        Math.abs(refined[5].startMs - 33936) <= 60
    }

    // Characterization of the "ming" early-break: "coming" sits in the energy block 30331-31464,
    // and the next word "closer" starts in a separate block (31567+). The old midpoint end rule cut
    // "coming" at ~31213 (halfway to "closer"), truncating the sustained "ming" while the vocal
    // energy was still strong to ~31464. When the next word lives in a different energy block, the
    // word should hold until its own block ends. Fixture values are the phrase=0 onsets/windows.
    def "a word holds until its energy block ends when the next word starts a new block"() {
        given:
        def service = new TranscriptTimingRefinementService()
        def method = TranscriptTimingRefinementService.getDeclaredMethod(
                "refineWordsForPhrase",
                List,
                TranscriptTimingRefinementService.PhraseTiming,
                OpenAiTranscriptSegment)
        method.accessible = true
        def words = [
                word("It's", 30250, 31068),
                word("coming", 31068, 31887),
                word("closer,", 31887, 32705),
                word("quiet", 32705, 33523),
                word("the", 33523, 34342),
                word("cobra", 34342, 35160)
        ]
        def onsets = [30331, 30556, 30846, 31056, 31581, 31906, 32082,
                      32806, 33356, 33506, 33631, 33936, 34056]
        def timing = new TranscriptTimingRefinementService.PhraseTiming(
                0,
                30250,
                30331,
                35160,
                30331,
                35267,
                34754,
                onsets,
                onsets,
                [
                        signalWindow(30331, 30434),
                        signalWindow(30434, 30537),
                        signalWindow(30537, 30743),
                        signalWindow(30743, 30846),
                        signalWindow(30846, 31052),
                        signalWindow(31052, 31464),
                        signalWindow(31567, 31670),
                        signalWindow(31670, 31876),
                        signalWindow(31876, 31979),
                        signalWindow(31979, 32082),
                        signalWindow(32082, 32288),
                        signalWindow(32700, 32803),
                        signalWindow(32803, 32906),
                        signalWindow(32906, 33215),
                        signalWindow(33215, 33318),
                        signalWindow(33318, 33421),
                        signalWindow(33421, 33524),
                        signalWindow(33627, 33730),
                        signalWindow(33730, 33833),
                        signalWindow(33936, 34039),
                        signalWindow(34039, 34142),
                        signalWindow(34142, 34754)
                ],
                "It's coming closer, quiet the cobra",
                "detected vocal end")
        def segment = segment(30250, 35160, words)

        when:
        def refined = (List<OpenAiTranscriptWord>) method.invoke(service, words, timing, segment)

        then:
        // "coming" (index 1) holds until its energy block ends (~31464), not the midpoint (31213).
        Math.abs(refined[1].endMs - 31464) <= 60
        // and it must not bleed into the next word's start.
        refined[1].endMs <= refined[2].startMs
    }

    // Characterization of the "Hush" page-2 melisma regression: "composure" is sung across one long
    // energy block (36095-37331) that contains its own internal onsets (po, sure). Plain onset
    // normalization mapped those internal onsets to the following words "I" and "want", burying them
    // ~1.2s early inside composure's melisma while the genuine "I want" energy block (37743-38464)
    // got starved. Syllable-balanced grouping of words across energy blocks must let composure claim
    // its block alone, so "I" lands in the 37743 block. Fixture values are the phrase=1 onsets/windows.
    def "a melismatic word claims its own energy block instead of capturing following words"() {
        given:
        def service = new TranscriptTimingRefinementService()
        def method = TranscriptTimingRefinementService.getDeclaredMethod(
                "refineWordsForPhrase",
                List,
                TranscriptTimingRefinementService.PhraseTiming,
                OpenAiTranscriptSegment)
        method.accessible = true
        def words = [
                word("Can't", 35160, 35756),
                word("keep", 35756, 36353),
                word("composure,", 36353, 36949),
                word("I", 36949, 37545),
                word("want", 37545, 38141),
                word("to", 38141, 38738),
                word("feel", 38738, 39334),
                word("it", 39334, 39930)
        ]
        def onsets = [35271, 35683, 35796, 36095, 36198, 36646, 36971,
                      37949, 38621, 38796, 38921, 39082, 39221]
        def timing = new TranscriptTimingRefinementService.PhraseTiming(
                1,
                35160,
                35271,
                39930,
                35271,
                39930,
                39717,
                onsets,
                onsets,
                [
                        signalWindow(35271, 35374),
                        signalWindow(35374, 35580),
                        signalWindow(35580, 35683),
                        signalWindow(35683, 35786),
                        signalWindow(35786, 35889),
                        signalWindow(35889, 35992),
                        signalWindow(36095, 36198),
                        signalWindow(36198, 36610),
                        signalWindow(36610, 36713),
                        signalWindow(36713, 36919),
                        signalWindow(36919, 37022),
                        signalWindow(37022, 37125),
                        signalWindow(37125, 37331),
                        signalWindow(37743, 37949),
                        signalWindow(37949, 38052),
                        signalWindow(38052, 38464),
                        signalWindow(38567, 38670),
                        signalWindow(38670, 38773),
                        signalWindow(38773, 38876),
                        signalWindow(38876, 38979),
                        signalWindow(38979, 39082),
                        signalWindow(39082, 39185),
                        signalWindow(39185, 39717)
                ],
                "Can't keep composure, I want to feel it",
                "detected vocal end")
        def segment = segment(35160, 39930, words)

        when:
        def refined = (List<OpenAiTranscriptWord>) method.invoke(service, words, timing, segment)

        then:
        // "composure," (index 2) starts its block at ~36095.
        Math.abs(refined[2].startMs - 36095) <= 80
        // "I" (index 3) lands in the genuine "I want" block (~37743), not inside composure (~36646).
        Math.abs(refined[3].startMs - 37743) <= 120
        // "want" (index 4) stays within its own block, not buried in composure.
        refined[4].startMs >= 37743
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

    // The dominant-pitch-band filter drops octave-error frames (consonant transients / breath that
    // aubio mis-detects an octave or more above the voice) while keeping the in-band fundamentals
    // and all unpitched energy-only frames. Pitches are semitones from C4, so G3 = -5.
    def "dominant pitch band filter drops octave-error frames but keeps in-band and unpitched frames"() {
        given:
        def service = new TranscriptTimingRefinementService()
        def method = TranscriptTimingRefinementService.getDeclaredMethod("filterToDominantPitchBand", List)
        method.accessible = true
        // Majority of pitched frames sit on G3 (-5); a few octave-error spikes land at C7 (+36)/A6 (+33);
        // one unpitched energy-only frame (rawFrequency 0) must survive regardless of band.
        def frames = []
        20.times { frames.add(new PitchDetector.PitchData((0.10f + it * 0.01f) as float, -5, "G3", 196.0d, 0.40d)) }
        frames.add(new PitchDetector.PitchData(0.31f as float, 36, "C7", 2093.0d, 0.20d))
        frames.add(new PitchDetector.PitchData(0.32f as float, 33, "A6", 1760.0d, 0.18d))
        frames.add(new PitchDetector.PitchData(0.33f as float, 0, "-", 0.0d, 0.05d))

        when:
        def filtered = (List<PitchDetector.PitchData>) method.invoke(service, frames)

        then:
        // octave-error spikes removed
        filtered.findAll { it.rawFrequency() > 0 && (it.pitch() == 36 || it.pitch() == 33) }.isEmpty()
        // all 20 in-band G3 frames kept
        filtered.count { it.pitch() == -5 } == 20
        // the unpitched energy-only frame survives
        filtered.any { it.rawFrequency() == 0.0d }
    }

    def "refines evenly distributed subtitle words to separate vocal islands in a dense opening line"() {
        given:
        int gapMs = 53200
        double bpm = 388d
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
        pitchData.add(frame(56.343f, 0.20d))
        pitchData.add(frame(56.430f, 0.055d))
        pitchData.add(frame(56.520f, 0.065d))
        pitchData.add(frame(56.600f, 0.055d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 82, 8, 0, 0.03d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 90, 2, -4, 0.12d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 93, 5, 15, 0.06d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 98, 5, 0, 0.20d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 107, 6, 1, 0.20d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 114, 4, 0, 0.15d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 118, 3, 2, 0.07d))
        pitchData.addAll(densePitchFramesForBeats(gapMs, bpm, 122, 23, -2, 0.17d))

        when:
        def refined = new TranscriptTimingRefinementService().refineForAlignment(result, pitchData, gapMs, "#VOCALS", bpm)

        then:
        refined.segments[0].words*.startMs.collect { beatForMs(it, gapMs, bpm) } == [0, 33, 42, 48, 58]
        beatForMs(refined.segments[0].words.last().endMs, gapMs, bpm) <= 82
        refined.segments[1].words*.startMs.collect { beatForMs(it, gapMs, bpm) } == [90, 98, 118]
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

    def "occupancy windows are rejected when they undercover most words despite extra onsets"() {
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
                signalWindow(53197, 53854),
                signalWindow(53893, 53970)
        ]
        def signalWindows = [
                signalWindow(53197, 53274),
                signalWindow(53274, 54044),
                signalWindow(54429, 54506),
                signalWindow(54506, 54737),
                signalWindow(54814, 54891),
                signalWindow(54891, 55276),
                signalWindow(55430, 56354)
        ]

        expect:
        !method.invoke(service, 5, occupancyWindows, signalWindows, 4)
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
                null, null, "#LRC", "", [], [], [], false, null, "#LRC", "#LRC"))
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

    private static Object signalWindow(int startMs, int endMs) {
        def clazz = TranscriptTimingRefinementService.getDeclaredClasses().find { it.simpleName == 'SignalWindow' }
        def constructor = clazz.getDeclaredConstructor(Integer.TYPE, Integer.TYPE)
        constructor.accessible = true
        constructor.newInstance(startMs, endMs)
    }
}
