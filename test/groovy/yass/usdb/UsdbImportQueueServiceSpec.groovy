package yass.usdb

import spock.lang.Specification
import yass.I18
import yass.YassSong

import java.nio.file.Files
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

class UsdbImportQueueServiceSpec extends Specification {

    def setupSpec() {
        I18.setDefaultLanguage()
    }

    def 'queues one separation job per selected library song without running it immediately'() {
        given:
        def dir = Files.createTempDirectory('yass-queue')
        def firstFile = dir.resolve('First.txt')
        def secondFile = dir.resolve('Second.txt')
        Files.writeString(firstFile, '#TITLE:First\nE\n')
        Files.writeString(secondFile, '#TITLE:Second\nE\n')
        def separationExecutor = new RecordingExecutorService()
        def service = new UsdbImportQueueService(null, new RecordingExecutorService(), separationExecutor, false)

        when:
        def jobs = service.enqueueExistingSongSeparation([
                song(dir.toString(), 'First.txt', 'Artist', 'First'),
                song(dir.toString(), 'Second.txt', 'Artist', 'Second')
        ])

        then:
        jobs*.mode == [UsdbImportQueueJob.Mode.SEPARATE_EXISTING_SONG, UsdbImportQueueJob.Mode.SEPARATE_EXISTING_SONG]
        jobs*.state.every { it == UsdbImportQueueJob.State.WAITING_SEPARATION }
        service.snapshot().size() == 2
        separationExecutor.commands.size() == 2

        cleanup:
        Files.deleteIfExists(firstFile)
        Files.deleteIfExists(secondFile)
        Files.deleteIfExists(dir)
    }

    def 'skips duplicate active library separation jobs by song file'() {
        given:
        def dir = Files.createTempDirectory('yass-queue-dup')
        def songFile = dir.resolve('Song.txt')
        Files.writeString(songFile, '#TITLE:Song\nE\n')
        def service = new UsdbImportQueueService(null, new RecordingExecutorService(), new RecordingExecutorService(), false)
        def selectedSong = song(dir.toString(), 'Song.txt', 'Artist', 'Song')

        expect:
        service.enqueueExistingSongSeparation([selectedSong]).size() == 1
        service.enqueueExistingSongSeparation([selectedSong]).isEmpty()
        service.snapshot().size() == 1

        cleanup:
        Files.deleteIfExists(songFile)
        Files.deleteIfExists(dir)
    }

    def 'cancels waiting library separation job without delete prompt'() {
        given:
        def dir = Files.createTempDirectory('yass-queue-cancel')
        def songFile = dir.resolve('Song.txt')
        Files.writeString(songFile, '#TITLE:Song\nE\n')
        def service = new UsdbImportQueueService(null, new RecordingExecutorService(), new RecordingExecutorService(), false)
        def job = service.enqueueExistingSongSeparation([song(dir.toString(), 'Song.txt', 'Artist', 'Song')]).first()

        when:
        service.cancelJob(job, null)

        then:
        job.state == UsdbImportQueueJob.State.CANCELED
        songFile.toFile().exists()
        dir.toFile().exists()

        cleanup:
        Files.deleteIfExists(songFile)
        Files.deleteIfExists(dir)
    }

    def 'marks already separated library songs with a queue hint'() {
        given:
        def dir = Files.createTempDirectory('yass-queue-separated')
        def songFile = dir.resolve('Song.txt')
        def vocalsFile = dir.resolve('vocals.ogg')
        Files.writeString(songFile, '#TITLE:Song\n#VOCALS:vocals.ogg\nE\n')
        Files.writeString(vocalsFile, '')
        def selectedSong = song(dir.toString(), 'Song.txt', 'Artist', 'Song')
        selectedSong.setVocals('vocals.ogg')
        def service = new UsdbImportQueueService(null, new RecordingExecutorService(), new RecordingExecutorService(), false)

        when:
        def job = service.enqueueExistingSongSeparation([selectedSong]).first()

        then:
        job.existingSeparationAssigned
        job.detailLog.contains(I18.get('usdb_queue_existing_separation_hint'))

        cleanup:
        Files.deleteIfExists(vocalsFile)
        Files.deleteIfExists(songFile)
        Files.deleteIfExists(dir)
    }

    def 'usdb active-job lookup ignores library separation jobs'() {
        given:
        def dir = Files.createTempDirectory('yass-queue-usdb-lookup')
        def songFile = dir.resolve('Song.txt')
        Files.writeString(songFile, '#TITLE:Song\nE\n')
        def service = new UsdbImportQueueService(null, new RecordingExecutorService(), new RecordingExecutorService(), false)
        service.enqueueExistingSongSeparation([song(dir.toString(), 'Song.txt', 'Artist', 'Song')])
        def summary = new UsdbSongSummary(99, 'Artist', 'Song', '', '', '0', '0', false)

        expect:
        !service.hasActiveJobFor(summary)

        cleanup:
        Files.deleteIfExists(songFile)
        Files.deleteIfExists(dir)
    }

    private static YassSong song(String dir, String filename, String artist, String title) {
        new YassSong(dir, '', filename, artist, title)
    }

    private static class RecordingExecutorService extends AbstractExecutorService {
        final List<Runnable> commands = []
        boolean shutdown

        @Override
        void shutdown() {
            shutdown = true
        }

        @Override
        List<Runnable> shutdownNow() {
            shutdown = true
            []
        }

        @Override
        boolean isShutdown() {
            shutdown
        }

        @Override
        boolean isTerminated() {
            shutdown
        }

        @Override
        boolean awaitTermination(long timeout, TimeUnit unit) {
            true
        }

        @Override
        void execute(Runnable command) {
            commands << command
        }
    }
}
