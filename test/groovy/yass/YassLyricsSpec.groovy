package yass

import spock.lang.Specification

import javax.swing.JPanel
import java.awt.event.KeyEvent

class YassLyricsSpec extends Specification {

    def 'recognizes minus key events from keyboard layouts and numeric keypads'() {
        given:
        def component = new JPanel()

        expect:
        YassLyrics.isMinusKey(new KeyEvent(component, KeyEvent.KEY_PRESSED, 0, 0,
                keyCode, keyChar)) == expected

        where:
        keyCode              | keyChar                    || expected
        KeyEvent.VK_MINUS    | KeyEvent.CHAR_UNDEFINED    || true
        KeyEvent.VK_SUBTRACT | KeyEvent.CHAR_UNDEFINED    || true
        KeyEvent.VK_UNDEFINED| '-'                        || true
        KeyEvent.VK_A        | 'a'                        || false
    }
}
