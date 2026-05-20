package yass

import spock.lang.Specification

class YassUtilsSpec extends Specification {

    def "explicit wizard separators split in input order without hyphenator lookup"() {
        given:
        def hyphenator = Mock(YassHyphenator)
        def utils = manualWizardUtils(hyphenator)

        when:
        def rows = utils.splitLyricsToLines(["Hel\u2022lo+world"] as String[], 0, true)

        then:
        noteTexts(rows) == ["Hel", "lo", "world"]
        noteLyrics(rows).every { !it.contains("\u2022") && !it.contains("+") }
        0 * hyphenator.hyphenateWord(_)
    }

    def "plus separators bypass dictionary syllabification"() {
        given:
        def hyphenator = Mock(YassHyphenator)
        def utils = manualWizardUtils(hyphenator)

        when:
        def rows = utils.splitLyricsToLines(["hello+world"] as String[], 0, true)

        then:
        noteTexts(rows) == ["hello", "world"]
        0 * hyphenator.hyphenateWord(_)
    }

    def "ordinary wizard lines keep dictionary backed syllable splitting"() {
        given:
        def hyphenator = Mock(YassHyphenator)
        def utils = manualWizardUtils(hyphenator)

        when:
        def rows = utils.splitLyricsToLines(["hello"] as String[], 0, true)

        then:
        1 * hyphenator.hyphenateWord("hello") >> "hel\u00ADlo"
        noteTexts(rows) == ["hel", "lo"]
    }

    def "explicit wizard separators preserve punctuation and ignore accidental empty fragments"() {
        given:
        def hyphenator = Mock(YassHyphenator)
        def utils = manualWizardUtils(hyphenator)

        when:
        def rows = utils.splitLyricsToLines(["+\u2022wait,\u2022+~++there!+"] as String[], 0, true)

        then:
        noteTexts(rows) == ["wait,", "~", "there!"]
        0 * hyphenator.hyphenateWord(_)
    }

    def "explicit separator support is opt in for wizard manual lyrics"() {
        given:
        def hyphenator = Mock(YassHyphenator)
        def utils = manualWizardUtils(hyphenator)

        when:
        def rows = utils.splitLyricsToLines(["Hel\u2022lo"] as String[], 0)

        then:
        1 * hyphenator.hyphenateWord("Hel\u2022lo") >> "Hel\u2022lo"
        noteTexts(rows) == ["Hel\u2022lo"]
    }

    private static YassUtils manualWizardUtils(YassHyphenator hyphenator) {
        new YassUtils(hyphenator: hyphenator, defaultLength: 3, spacingAfter: false)
    }

    private static List<String> noteTexts(List<String> lines) {
        noteLyrics(lines).collect { it.trim() }
    }

    private static List<String> noteLyrics(List<String> lines) {
        lines.findAll { it.startsWith(":") }
                .collect { it.split("\t", 5)[4] }
    }
}
