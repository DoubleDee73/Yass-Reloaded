package yass.usdb

import spock.lang.Specification

import java.nio.file.Path

class UsdbImportQueueJobSpec extends Specification {

    def 'usdb jobs expose import modes'() {
        given:
        def summary = new UsdbSongSummary(123, 'Artist', 'Title', '', '', '0', '0', false)

        expect:
        new UsdbImportQueueJob(summary, false, null).mode == UsdbImportQueueJob.Mode.IMPORT
        new UsdbImportQueueJob(summary, true, null).mode == UsdbImportQueueJob.Mode.IMPORT_AND_SEPARATE
    }

    def 'library separation job stores song file identity and existing-stem hint'() {
        given:
        def songFile = Path.of('C:/songs/Artist - Title/Artist - Title.txt')

        when:
        def job = UsdbImportQueueJob.forExistingSongSeparation(songFile, 'Artist - Title', 'Artist', 'Title', true)

        then:
        job.mode == UsdbImportQueueJob.Mode.SEPARATE_EXISTING_SONG
        job.songFile == songFile
        job.displayName == 'Artist - Title'
        job.artist == 'Artist'
        job.title == 'Title'
        job.existingSeparationAssigned
        job.separationJob
        !job.importJob
    }
}
