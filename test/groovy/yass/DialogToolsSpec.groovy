package yass

import spock.lang.Specification

import javax.swing.JTextArea

class DialogToolsSpec extends Specification {

    def "createHeaderPanel uses a wrapping text component for long hints"() {
        when:
        def panel = DialogTools.createHeaderPanel("This is a very long hint that should wrap inside the options dialog.", 200)

        then:
        panel.componentCount == 2
        panel.getComponent(1) instanceof JTextArea
        with((JTextArea) panel.getComponent(1)) {
            lineWrap
            wrapStyleWord
            !editable
            !opaque
        }
    }
}
