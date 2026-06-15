package yass

import spock.lang.Specification

import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.nio.file.Files

class YassActionsBackgroundSpec extends Specification {

    def setupSpec() {
        I18.setDefaultLanguage()
    }

    def 'resolves no background image when song has no background tag'() {
        given:
        def dir = Files.createTempDirectory('yass-background')
        def songFile = writeSong(dir, null)
        def table = new YassTable()
        assert table.loadFile(songFile.toString())

        expect:
        YassActions.resolveBackgroundImage(table) == null

        cleanup:
        Files.deleteIfExists(songFile)
        Files.deleteIfExists(dir)
    }

    def 'resolves no background image when tagged file is missing'() {
        given:
        def dir = Files.createTempDirectory('yass-background')
        def songFile = writeSong(dir, 'missing.jpg')
        def table = new YassTable()
        assert table.loadFile(songFile.toString())

        expect:
        YassActions.resolveBackgroundImage(table) == null

        cleanup:
        Files.deleteIfExists(songFile)
        Files.deleteIfExists(dir)
    }

    def 'loads tagged background image from song folder'() {
        given:
        def dir = Files.createTempDirectory('yass-background')
        def backgroundFile = dir.resolve('background.png')
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), 'png', backgroundFile.toFile())
        def songFile = writeSong(dir, backgroundFile.fileName.toString())
        def table = new YassTable()
        assert table.loadFile(songFile.toString())

        when:
        def image = YassActions.resolveBackgroundImage(table)

        then:
        image != null
        image.width == 2
        image.height == 2

        cleanup:
        Files.deleteIfExists(backgroundFile)
        Files.deleteIfExists(songFile)
        Files.deleteIfExists(dir)
    }

    private static java.nio.file.Path writeSong(java.nio.file.Path dir, String background) {
        def lines = ['#TITLE:Title', '#ARTIST:Artist']
        if (background != null) {
            lines << "#BACKGROUND:${background}"
        }
        lines << 'E'
        def songFile = dir.resolve('Artist - Title.txt')
        Files.writeString(songFile, lines.join(System.lineSeparator()) + System.lineSeparator())
        songFile
    }
}
