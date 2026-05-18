package yass

import spock.lang.Specification

import java.awt.event.FocusEvent
import java.awt.event.KeyEvent

class TimeSpinnerSpec extends Specification {

    def setupSpec() {
        I18.setDefaultLanguage()
    }

    def 'commits typed value when the editor loses focus'() {
        given:
        TimeSpinner spinner = new TimeSpinner(null, 0, 10000)
        def textField = spinner.getTextField()
        textField.setText('500')

        when:
        FocusEvent event = new FocusEvent(textField, FocusEvent.FOCUS_LOST)
        textField.getFocusListeners().each { it.focusLost(event) }

        then:
        spinner.getTime() == 500
    }

    def 'commits grouped integer value when the editor loses focus'() {
        given:
        TimeSpinner spinner = new TimeSpinner(null, 0, 10000)
        spinner.setDuration(300000)
        spinner.setTime(26230)
        def textField = spinner.getTextField()
        textField.setText('2.230')

        when:
        FocusEvent event = new FocusEvent(textField, FocusEvent.FOCUS_LOST)
        textField.getFocusListeners().each { it.focusLost(event) }

        then:
        spinner.getTime() == 2230
    }

    def 'spinner model advances by its step size'() {
        given:
        TimeSpinner spinner = new TimeSpinner(null, 0, 10000)
        spinner.setTime(26230)
        spinner.setDuration(300000)

        when:
        spinner.getSpinner().setValue(spinner.getSpinner().getNextValue())

        then:
        spinner.getTime() == 26240
    }

    def 'allows text editing control keys in the spinner editor'() {
        given:
        TimeSpinner spinner = new TimeSpinner(null, 500, 10000)
        def textField = spinner.getTextField()
        def event = new KeyEvent(textField, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, keyChar as char)

        when:
        textField.getKeyListeners().each { it.keyTyped(event) }

        then:
        !event.consumed

        where:
        keyChar << ['\b', '\u007F']
    }

    def 'keeps current value editable when duration temporarily drops to zero'() {
        given:
        TimeSpinner spinner = new TimeSpinner(null, 0, 10000)
        spinner.setTime(500)

        when:
        spinner.setDuration(0)
        spinner.getSpinner().setValue(510)

        then:
        spinner.getTime() == 510
    }
}
