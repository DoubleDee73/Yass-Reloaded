package yass

import spock.lang.Specification

class DialogOwnerSpec extends Specification {

    def "legacy owner factory prefers the current application frame"() {
        given:
        String source = new File("src/yass/YassActions.java").text

        expect:
        source.contains("JFrame owner = getFrame(tab)")
        source.contains("return owner != null ? owner : new OwnerFrame()")
    }

    def "yass actions dialogs avoid dummy owner frames"() {
        given:
        String source = new File("src/yass/YassActions.java").text

        expect:
        !source.contains("new JDialog(new OwnerFrame())")
        !source.contains("JOptionPane.showMessageDialog(null, label")
    }

    def "remaining application dialogs avoid default-screen centering and null owners"() {
        expect:
        def usdbSyncer = new File("src/yass/extras/UsdbSyncerMetaTagCreator.java").text
        !usdbSyncer.contains("setLocation(dim.width / 2")
        !usdbSyncer.contains("showMessageDialog(null")
        usdbSyncer.contains("YassUtils.resolveDialogOwner")

        def hyphenator = new File("src/yass/hyphenator/HyphenatorDictionary.java").text
        !hyphenator.contains("setLocation(dim.width / 2")
        hyphenator.contains("YassUtils.resolveDialogOwner")

        def captureAudio = new File("src/yass/YassCaptureAudio.java").text
        !captureAudio.contains("setLocation(dim.width / 2")
        captureAudio.contains("YassUtils.resolveDialogOwner")

        def printer = new File("src/yass/YassSongListPrinter.java").text
        !printer.contains("setLocation(dim.width / 2")
        printer.contains("YassUtils.resolveDialogOwner")

        def table = new File("src/yass/YassTable.java").text
        !table.contains("JOptionPane.showMessageDialog(null")

        def properties = new File("src/yass/YassProperties.java").text
        !properties.contains("JOptionPane.showMessageDialog(null")

        def ffmpegDownloader = new File("src/yass/ffmpeg/FfmpegDownloader.java").text
        !ffmpegDownloader.contains("JOptionPane.showMessageDialog(null")

        def main = new File("src/yass/YassMain.java").text
        !main.contains("JOptionPane.showConfirmDialog(null")
        !main.contains("JOptionPane.showMessageDialog(null")
    }
}
