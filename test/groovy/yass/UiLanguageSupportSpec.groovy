package yass

import spock.lang.Specification
import spock.lang.Unroll

class UiLanguageSupportSpec extends Specification {

    @Unroll
    def "resolveStartupLanguage returns #expected for configured '#configuredLanguage' and system '#systemLanguage'"() {
        expect:
        UiLanguageSupport.resolveStartupLanguage(configuredLanguage, new Locale(systemLanguage)) == expected

        where:
        configuredLanguage | systemLanguage || expected
        null               | "de"           || "de"
        "default"          | "es"           || "es"
        "default"          | "it"           || "en"
        "fr"               | "de"           || "fr"
        "xx"               | "de"           || "en"
    }

    def "supported dialog languages contain the maintained yass ui locales"() {
        expect:
        UiLanguageSupport.getSupportedDialogLanguages()*.code == ["en", "de", "es", "fr", "hu", "pl"]
    }

    def "supported dialog languages expose readable labels"() {
        when:
        def options = UiLanguageSupport.getSupportedDialogLanguages()

        then:
        options.find { it.code == "de" }.toString() == "Deutsch"
        options.find { it.code == "en" }.toString() == "English"
    }
}
