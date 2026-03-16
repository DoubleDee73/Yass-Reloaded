/*
 * Yass Reloaded - Karaoke Editor
 */

package yass

import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class I18PropertiesEncodingSpec extends Specification {

    private static final List<String> MOJIBAKE_MARKERS = ['?', '?', '??', '???', '??', '???', '???', '?']
    private static final List<String> NO_ESCAPE_PREFIXES = [
            'options_external_tools',
            'edit_audio_separate',
            'edit_align_transcription',
            'create_lyrics_separate_transcribe',
            'create_youtube_reuse_download_prompt'
    ]

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

            text.eachLine { line ->
                if (!line.startsWith('#') && line.contains('=')) {
                    String key = line.substring(0, line.indexOf('='))
                    String value = line.substring(line.indexOf('=') + 1)
                    if (NO_ESCAPE_PREFIXES.any { prefix -> key == prefix || key.startsWith(prefix + '_') }) {
                        if (value.contains('\u')) {
                            problems << "${path}: contains unicode escape in key '${key}'"
                        }
                    }
                }
            }
        }

        then:
        problems.isEmpty(), problems.join('
')
    }
}
