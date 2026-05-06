package yass.integration.separation.mvsep

import spock.lang.Specification
import yass.YassProperties

class MvsepDefaultsSpec extends Specification {

    def "preferredOption keeps configured selection when present"() {
        given:
        def field = new MvsepAlgorithmField(
                "model_type",
                "Model Type",
                [
                        new MvsepFieldOption("legacy", "instrum: 8.00"),
                        new MvsepFieldOption("modern", "instrum: 12.60"),
                ],
                "legacy"
        )

        expect:
        field.preferredOption("legacy").key() == "legacy"
    }

    def "preferredOption chooses latest instrumental variant when no configured value is present"() {
        given:
        def field = new MvsepAlgorithmField(
                "model_type",
                "Model Type",
                [
                        new MvsepFieldOption("old", "instrum: 10.12"),
                        new MvsepFieldOption("new", "instrum: 12.60"),
                        new MvsepFieldOption("default", "vocals: 2.0"),
                ],
                "default"
        )

        expect:
        field.preferredOption(null).key() == "new"
        field.preferredOption("missing").key() == "new"
    }

    def "preferredOption falls back to default when no instrumental variants exist"() {
        given:
        def field = new MvsepAlgorithmField(
                "model_type",
                "Model Type",
                [
                        new MvsepFieldOption("default", "balanced"),
                        new MvsepFieldOption("alt", "quality"),
                ],
                "default"
        )

        expect:
        field.preferredOption(null).key() == "default"
    }

    def "mvsep output format falls back to m4a"() {
        expect:
        MvsepOutputFormat.fromValue("unknown") == MvsepOutputFormat.M4A
    }

    def "fresh defaults enable waveform and use m4a for mvsep"() {
        given:
        def properties = new YassProperties()

        expect:
        properties.getDefaultProperty("debug-waveform") == "true"
        properties.getDefaultProperty("mvsep-output-format") == "m4a"
    }
}
