package yass.analysis

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class PitchShiftBakeServiceSpec extends Specification {

    @TempDir
    Path dir

    private File makeFile(String name, String content = "audio") {
        def f = dir.resolve(name).toFile()
        f.text = content
        return f
    }

    /** A renderer that just writes a marker file so the candidate is non-empty. */
    private PitchShiftBakeService.Renderer writingRenderer() {
        return { File source, File target, double cents -> target.text = "shifted:${source.name}:${cents}" }
    }

    def "missing backend reports no_backend and writes nothing"() {
        given:
        makeFile("song.ogg")

        when:
        def outcome = PitchShiftBakeService.bake(dir.toFile(), ["AUDIO:": "song.ogg"], -18.0d, null, null)

        then:
        !outcome.success()
        outcome.reasonKey() == "pitch_shift_bake_no_backend"
        outcome.tagUpdates().isEmpty()
        dir.toFile().list().toList() == ["song.ogg"]
    }

    def "zero correction is rejected"() {
        given:
        makeFile("song.ogg")

        expect:
        PitchShiftBakeService.bake(dir.toFile(), ["AUDIO:": "song.ogg"], 0.0d, writingRenderer(), null)
                .reasonKey() == "pitch_shift_bake_no_correction"
    }

    def "a missing source file aborts before any render"() {
        given: "the tag points at a file that does not exist"
        def rendered = []
        def renderer = { File s, File t, double c -> rendered << s.name; t.text = "x" } as PitchShiftBakeService.Renderer

        when:
        def outcome = PitchShiftBakeService.bake(dir.toFile(), ["AUDIO:": "ghost.ogg"], -18.0d, renderer, null)

        then:
        !outcome.success()
        outcome.reasonKey() == "pitch_shift_bake_source_missing"
        rendered.isEmpty()
    }

    def "successful bake renders copies, maps tags, and leaves sources intact"() {
        given:
        makeFile("audio.ogg")
        makeFile("voc.ogg")
        def tags = ["AUDIO:": "audio.ogg", "VOCALS:": "voc.ogg"]

        when:
        def outcome = PitchShiftBakeService.bake(dir.toFile(), tags, -18.0d, writingRenderer(), null)

        then: "all-or-nothing succeeds and maps every tag to its shifted copy"
        outcome.success()
        outcome.reasonKey() == "pitch_shift_bake_done"
        outcome.tagUpdates() == [
                "AUDIO:" : "audio (Pitch Shifted).ogg",
                "VOCALS:": "voc (Pitch Shifted).ogg"
        ]

        and: "original sources still exist and the rendered files were written"
        dir.resolve("audio.ogg").toFile().exists()
        dir.resolve("voc.ogg").toFile().exists()
        dir.resolve("audio (Pitch Shifted).ogg").toFile().exists()
        dir.resolve("voc (Pitch Shifted).ogg").toFile().exists()

        and: "no leftover .part candidates remain"
        dir.toFile().list().toList().every { !it.endsWith(".part") }
    }

    def "tags sharing one source render it once and both map to the same copy"() {
        given:
        makeFile("mix.ogg")
        def renderCount = 0
        def renderer = { File s, File t, double c -> renderCount++; t.text = "x" } as PitchShiftBakeService.Renderer
        def tags = ["MP3:": "mix.ogg", "AUDIO:": "mix.ogg"]

        when:
        def outcome = PitchShiftBakeService.bake(dir.toFile(), tags, 12.0d, renderer, null)

        then:
        outcome.success()
        renderCount == 1
        outcome.tagUpdates()["MP3:"] == "mix (Pitch Shifted).ogg"
        outcome.tagUpdates()["AUDIO:"] == "mix (Pitch Shifted).ogg"
    }

    def "cancellation before rendering leaves files and tags unchanged"() {
        given:
        makeFile("song.ogg")
        def renderer = { File s, File t, double c -> t.text = "x" } as PitchShiftBakeService.Renderer
        def cancel = { true } as PitchShiftBakeService.CancelCheck

        when:
        def outcome = PitchShiftBakeService.bake(dir.toFile(), ["AUDIO:": "song.ogg"], -18.0d, renderer, cancel)

        then:
        !outcome.success()
        outcome.reasonKey() == "pitch_shift_bake_cancelled"
        outcome.tagUpdates().isEmpty()
        dir.toFile().list().toList() == ["song.ogg"]
    }

    def "a failed render on the second file removes the first candidate and reports failure"() {
        given: "two sources; the renderer throws on the second"
        makeFile("a.ogg")
        makeFile("b.ogg")
        def renderer = { File s, File t, double c ->
            if (s.name == "b.ogg") throw new RuntimeException("ffmpeg blew up")
            t.text = "x"
        } as PitchShiftBakeService.Renderer

        when:
        def outcome = PitchShiftBakeService.bake(dir.toFile(),
                ["AUDIO:": "a.ogg", "VOCALS:": "b.ogg"], -18.0d, renderer, null)

        then: "all-or-nothing: no tag updates, no leftover output, sources intact"
        !outcome.success()
        outcome.reasonKey() == "pitch_shift_bake_render_failed"
        outcome.tagUpdates().isEmpty()
        dir.toFile().list().toList().sort() == ["a.ogg", "b.ogg"]
    }

    def "an empty rendered candidate counts as a failed render"() {
        given:
        makeFile("song.ogg")
        def renderer = { File s, File t, double c -> t.createNewFile() } as PitchShiftBakeService.Renderer

        when:
        def outcome = PitchShiftBakeService.bake(dir.toFile(), ["AUDIO:": "song.ogg"], -18.0d, renderer, null)

        then:
        !outcome.success()
        outcome.reasonKey() == "pitch_shift_bake_render_failed"
        dir.toFile().list().toList() == ["song.ogg"]
    }

    def "rendered filename avoids collision with an existing file"() {
        given: "the natural shifted name is already taken on disk"
        makeFile("song.ogg")
        makeFile("song (Pitch Shifted).ogg", "pre-existing")
        def tags = ["AUDIO:": "song.ogg"]

        when:
        def outcome = PitchShiftBakeService.bake(dir.toFile(), tags, -18.0d, writingRenderer(), null)

        then: "the new copy gets a counter suffix and the existing file is untouched"
        outcome.success()
        outcome.tagUpdates()["AUDIO:"] == "song (Pitch Shifted) 2.ogg"
        dir.resolve("song (Pitch Shifted).ogg").toFile().text == "pre-existing"
    }

    def "collisionSafeName increments past multiple reserved names"() {
        given:
        makeFile("song (Pitch Shifted).ogg")
        def reserved = ["song (pitch shifted) 2.ogg"] as Set

        expect:
        PitchShiftBakeService.collisionSafeName(dir.toFile(), "song.ogg", reserved) == "song (Pitch Shifted) 3.ogg"
    }
}
