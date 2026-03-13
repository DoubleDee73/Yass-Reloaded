/*
 * Yass Reloaded - Karaoke Editor
 */

package yass

import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class I18PropertiesEncodingSpec extends Specification {

    private static final List<String> MOJIBAKE_MARKERS = ['?', '?', '??', '???', '???', '???', '???', '???', '�']

    def 'i18n property files are valid utf-8 and do not contain mojibake markers'() {
        given:
        Path i18Dir = Path.of('src', 'yass', 'resources', 'i18')

        when:
        List<Path> propertyFiles = Files.list(i18Dir)
                .filter { path -> path.fileName.toString().startsWith('yass_') && path.fileName.toString().endsWith('.properties') }
                .sorted()
                .toList()

        then:
        !propertyFiles.isEmpty()

        when:
        List<String> problems = []
        propertyFiles.each { path ->
            byte[] bytes = Files.readAllBytes(path)
            String text
            try {
                text = new String(bytes, StandardCharsets.UTF_8)
            } catch (Exception ex) {
                problems << "${path}: not readable as UTF-8 (${ex.message})"
                return
            }

            MOJIBAKE_MARKERS.each { marker ->
                if (text.contains(marker)) {
                    problems << "${path}: contains suspicious mojibake marker '${marker}'"
                }
            }
        }

        then:
        problems.isEmpty(), problems.join('
')
    }
}
