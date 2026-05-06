package yass.ffmpeg

import spock.lang.Specification
import yass.I18

class FfmpegDownloaderSpec extends Specification {

    def setup() {
        I18.setLanguage("en")
    }

    def "dialog options use existing translated labels"() {
        expect:
        FfmpegDownloader.getDialogOptions() == [
                I18.get("ffmpeg_button_download"),
                I18.get("ffmpeg_button_select_folder"),
                I18.get("screen_selectcontrol_cancel")
        ] as Object[]
    }

    def "offline dialog options still allow selecting a local folder"() {
        expect:
        FfmpegDownloader.getDialogOptions(false) == [
                I18.get("ffmpeg_button_select_folder"),
                I18.get("screen_selectcontrol_cancel")
        ] as Object[]
    }
}
