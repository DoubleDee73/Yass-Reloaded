package yass.ffmpeg

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class AbstractFFMPEGLocatorSpec extends Specification {

    @TempDir
    Path tempDir

    def "findFfmpegInPath skips candidate without ffprobe and accepts later valid candidate"() {
        given:
        Path invalidBin = Files.createDirectories(tempDir.resolve("ffmpeg-broken").resolve("bin"))
        Files.createFile(invalidBin.resolve(executableName("ffmpeg")))

        Path validBin = Files.createDirectories(tempDir.resolve("ffmpeg-valid").resolve("bin"))
        Files.createFile(validBin.resolve(executableName("ffmpeg")))
        Files.createFile(validBin.resolve(executableName("ffprobe")))

        def locator = new TestLocator(pathValue(invalidBin, validBin))

        expect:
        locator.findFfmpegInPath() == validBin.toString()
    }

    def "findFfmpegInPath returns null when no candidate contains both ffmpeg and ffprobe"() {
        given:
        Path invalidBin = Files.createDirectories(tempDir.resolve("ffmpeg-broken").resolve("bin"))
        Files.createFile(invalidBin.resolve(executableName("ffmpeg")))

        def locator = new TestLocator(invalidBin.toString())

        expect:
        locator.findFfmpegInPath() == null
    }

    private static String executableName(String base) {
        return AbstractFFMPEGLocator.CURRENT_OS.contains("win") ? base + ".exe" : base
    }

    private static String pathValue(Path first, Path second) {
        String delimiter = AbstractFFMPEGLocator.CURRENT_OS.contains("linux") || AbstractFFMPEGLocator.CURRENT_OS.contains("mac") ? ":" : ";"
        return first.toString() + delimiter + second
    }

    private static class TestLocator extends AbstractFFMPEGLocator {
        private final String pathValue

        TestLocator(String pathValue) {
            this.pathValue = pathValue
        }

        @Override
        boolean isCurrentOS() {
            return true
        }

        @Override
        String getFfmpegPath() {
            return findFfmpegInPath()
        }

        @Override
        String findFfmpegInPath() {
            return findFfmpegInPath(pathValue)
        }
    }
}
