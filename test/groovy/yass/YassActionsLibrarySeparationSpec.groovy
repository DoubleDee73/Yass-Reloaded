package yass

import spock.lang.Specification

import java.nio.file.Files

class YassActionsLibrarySeparationSpec extends Specification {

    def 'detects existing separated stem assignments on library song'() {
        given:
        def dir = Files.createTempDirectory('yass-stems')
        def vocals = dir.resolve('vocals.ogg')
        Files.writeString(vocals, '')
        def song = new YassSong(dir.toString(), '', 'Song.txt', 'Artist', 'Title')
        song.setVocals('vocals.ogg')

        expect:
        YassActions.hasExistingSeparatedStemAssignments(song)

        cleanup:
        Files.deleteIfExists(vocals)
        Files.deleteIfExists(dir)
    }

    def 'does not overwrite valid existing quiet separated track assignment'() {
        given:
        def dir = Files.createTempDirectory('yass-quiet-stems')
        def existing = dir.resolve('existing.ogg')
        def generated = dir.resolve('generated.ogg')
        Files.writeString(existing, '')
        Files.writeString(generated, '')

        expect:
        !YassActions.shouldAssignQuietSeparatedTrack(dir.toFile(), 'existing.ogg', generated.toFile())
        YassActions.shouldAssignQuietSeparatedTrack(dir.toFile(), '', generated.toFile())
        YassActions.shouldAssignQuietSeparatedTrack(dir.toFile(), 'missing.ogg', generated.toFile())
        YassActions.shouldAssignQuietSeparatedTrack(dir.toFile(), 'generated.ogg', generated.toFile())

        cleanup:
        Files.deleteIfExists(existing)
        Files.deleteIfExists(generated)
        Files.deleteIfExists(dir)
    }
}
