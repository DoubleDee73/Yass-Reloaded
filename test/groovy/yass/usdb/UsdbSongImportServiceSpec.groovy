package yass.usdb

import spock.lang.Specification

class UsdbSongImportServiceSpec extends Specification {

    def 'builds safe folder name when title contains only illegal filename characters'() {
        expect:
        UsdbSongImportService.buildFolderName('Nena', '?') == 'Nena - UnknownTitle'
    }
}
