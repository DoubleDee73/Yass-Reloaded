package yass.options

import spock.lang.Specification
import yass.I18

class ErrorPanelSpec extends Specification {

    def "error panel declares every persisted correction setting"() {
        given:
        I18.setDefaultLanguage()
        RecordingErrorPanel panel = new RecordingErrorPanel()

        when:
        panel.addRows()

        then:
        panel.keys == [
                "touching-syllables",
                "correct-uncommon-pagebreaks",
                "correct-uncommon-pagebreaks-fix",
                "correct-uncommon-spacing",
                "typographic-apostrophes",
                "capitalize-rows"
        ] as Set
    }

    private static class RecordingErrorPanel extends ErrorPanel {
        final Set<String> keys = new LinkedHashSet<>()

        @Override
        void addBoolean(String label, String key, String val) {
            keys.add(key)
        }

        @Override
        void addRadio(String label, String key, String val, String txt) {
            keys.add(key)
        }

        @Override
        void addText(String label, String key) {
            keys.add(key)
        }

        @Override
        void addComment(String comment) {
        }
    }
}
