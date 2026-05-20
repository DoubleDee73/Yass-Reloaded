package yass.usdb

import spock.lang.Specification
import yass.I18

import java.nio.file.Path

class UsdbImportQueueDialogSpec extends Specification {

    def setupSpec() {
        I18.setDefaultLanguage()
    }

    def 'mode text distinguishes import and library separation jobs'() {
        given:
        def summary = new UsdbSongSummary(7, 'Artist', 'Title', '', '', '0', '0', false)

        expect:
        UsdbImportQueueDialog.modeTextFor(new UsdbImportQueueJob(summary, false, null)) == I18.get('usdb_queue_mode_import')
        UsdbImportQueueDialog.modeTextFor(new UsdbImportQueueJob(summary, true, null)) == I18.get('usdb_queue_mode_import_separate')
        UsdbImportQueueDialog.modeTextFor(UsdbImportQueueJob.forExistingSongSeparation(Path.of('Song.txt'), 'Artist - Title', 'Artist', 'Title', false)) == I18.get('usdb_queue_mode_separation')
    }
}
