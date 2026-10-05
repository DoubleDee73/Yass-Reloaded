package yass

import spock.lang.Specification

class YtDlpSupportSpec extends Specification {

    def 'combined video format joins video and audio so keep-video can preserve both outputs'() {
        given:
        YassProperties properties = Stub(YassProperties) {
            getProperty('ytdlp-video-codec') >> ''
            getProperty('ytdlp-video-resolution') >> '[height<=480]'
            getProperty('ytdlp-audio-bitrate') >> '192k'
        }

        expect:
        YtDlpSupport.buildCombinedVideoFormatString(properties, false) ==
                'bestvideo[height<=480]+bestaudio[abr<=192000]'
    }
}
