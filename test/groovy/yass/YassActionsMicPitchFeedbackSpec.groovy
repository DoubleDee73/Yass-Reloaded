package yass

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class YassActionsMicPitchFeedbackSpec extends Specification {

    def 'mic pitch setup hints use an escape-dismissable sheet message'() {
        given:
        String source = Files.readString(Path.of('src/yass/YassActions.java'))
        String toggleBlock = sourceBetween(source,
                'private void toggleMicPitchCapture() {',
                'private void bindMicPitchSelection(MicPitchSession session, int[] rows) {')

        expect:
        toggleBlock.contains('showDismissibleMicPitchMessage(I18.get("edit_mic_select_one_note"));')
        toggleBlock.contains('showDismissibleMicPitchMessage(I18.get("edit_mic_no_device"));')
    }

    def 'dismissible mic pitch messages clear on escape only while their own message is visible'() {
        given:
        String source = Files.readString(Path.of('src/yass/YassActions.java'))
        String helperBlock = sourceBetween(source,
                'private void showDismissibleMicPitchMessage(String message) {',
                'private void setMicPitchBanner(MicPitchSession session, String text) {')

        expect:
        helperBlock.contains('installMicPitchMessageDismissDispatcher(message);')
        helperBlock.contains('KeyEvent.VK_ESCAPE')
        helperBlock.contains('micPitchDismissibleMessage')
        helperBlock.contains('sheet.getMessage()')
        helperBlock.contains('sheet.setMessage("")')
        helperBlock.contains('removeMicPitchMessageDismissDispatcher();')
        helperBlock.contains('e.consume();')
    }

    def 'missing microphone messages point to the actual preferences menu path'() {
        given:
        String english = Files.readString(Path.of('src/yass/resources/i18/yass_en.properties'))
        String german = Files.readString(Path.of('src/yass/resources/i18/yass_de.properties'))

        expect:
        english.contains('edit_mic_no_device = No microphone configured. Open Extras > Preferences... > Editor > Control and choose a device under Mic:.')
        german.contains('edit_mic_no_device = Kein Mikrofon konfiguriert. Unter Extras > Einstellungen... > Editor > Steuerung bei Mic: ein Ger\u00e4t ausw\u00e4hlen.')
        !english.contains('edit_mic_no_device = No microphone configured. Set one in Options > Control.')
        !german.contains('edit_mic_no_device = Kein Mikrofon konfiguriert. Bitte unter Optionen > Steuerung festlegen.')
    }

    private static String sourceBetween(String source, String start, String end) {
        int startIndex = source.indexOf(start)
        int endIndex = source.indexOf(end, startIndex)
        assert startIndex >= 0
        assert endIndex > startIndex
        source.substring(startIndex, endIndex)
    }
}
