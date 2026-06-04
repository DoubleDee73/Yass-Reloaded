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

    @Unroll
    def "#language resource bundle contains every default message key"() {
        given:
        Set<String> defaultKeys = propertyKeys("src/yass/resources/i18/yass_en.properties")
        Set<String> localizedKeys = propertyKeys("src/yass/resources/i18/yass_${language}.properties")

        expect:
        (defaultKeys - localizedKeys).empty

        where:
        language << supportedLanguageCodes().findAll { it != "en" }
    }

    def "format group label keys match configured encoding rules"() {
        expect:
        supportedLanguageCodes().every { language ->
            Set<String> keys = propertyKeys("src/yass/resources/i18/yass_${language}.properties")
            keys.contains("group_format_encoding_utf8") &&
                    !keys.contains("group_format_encoding_cp1252")
        }
    }

    private static List<String> supportedLanguageCodes() {
        UiLanguageSupport.getSupportedDialogLanguages()*.code
    }

    private static Set<String> propertyKeys(String path) {
        new File(path)
                .readLines("UTF-8")
                .collect { it.trim() }
                .findAll { it && !it.startsWith("#") && propertySeparatorIndex(it) >= 0 }
                .collect { it.substring(0, propertySeparatorIndex(it)).trim() }
                .toSet()
    }

    private static int propertySeparatorIndex(String line) {
        int equals = line.indexOf("=")
        int colon = line.indexOf(":")
        if (equals < 0) {
            return colon
        }
        if (colon < 0) {
            return equals
        }
        Math.min(equals, colon)
    }
}
