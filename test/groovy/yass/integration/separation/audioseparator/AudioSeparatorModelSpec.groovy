package yass.integration.separation.audioseparator

import spock.lang.Specification

class AudioSeparatorModelSpec extends Specification {

    def "displayLabelForValue keeps friendly label for known models"() {
        expect:
        AudioSeparatorModel.displayLabelForValue("Kim_Vocal_2.onnx") ==
                "Kim Vocal 2 MDX-Net (SDR ~10)"
    }

    def "displayLabelForValue falls back to filename for unknown models"() {
        expect:
        AudioSeparatorModel.displayLabelForValue("custom-model.onnx") == "custom-model.onnx"
    }
}
