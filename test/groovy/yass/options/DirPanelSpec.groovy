package yass.options

import spock.lang.Specification
import spock.lang.Unroll
import yass.I18
import yass.YassProperties

class DirPanelSpec extends Specification {

    def cleanup() {
        I18.setDefaultLanguage()
    }

    @Unroll
    def "directory panel builds with #language locale"() {
        given:
        I18.setLanguage(language)
        OptionsPanel.loadProperties(new TestProperties())

        when:
        new DirPanel().getBody()

        then:
        noExceptionThrown()

        where:
        language << ["fr", "es"]
    }

    private static class TestProperties extends YassProperties {
        @Override
        void load() {
            setDefaultProperties(this)
        }
    }
}
