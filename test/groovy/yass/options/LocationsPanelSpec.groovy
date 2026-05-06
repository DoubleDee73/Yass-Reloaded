package yass.options

import spock.lang.Specification
import yass.I18
import yass.YassProperties

class LocationsPanelSpec extends Specification {

    def setupSpec() {
        I18.setDefaultLanguage()
        OptionsPanel.loadProperties(new YassProperties())
    }

    def "locations panel uses wider labels for external tool paths"() {
        when:
        def panel = new LocationsPanel()
        panel.getBody()

        then:
        panel.labelWidth >= 180
    }
}
