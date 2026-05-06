package yass.ffmpeg

import spock.lang.Specification

class FfmpegPromptSupportSpec extends Specification {

    def "should remind when ffmpeg prompt returned no path"() {
        expect:
        FfmpegPromptSupport.shouldShowPostPromptReminder(null)
        FfmpegPromptSupport.shouldShowPostPromptReminder("")
        !FfmpegPromptSupport.shouldShowPostPromptReminder("C:\\ffmpeg\\bin")
    }
}
