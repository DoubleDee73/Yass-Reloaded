package yass.options

import spock.lang.Specification

class OptionsDialogOwnerSpec extends Specification {

    def "options dialog uses the shared dialog owner helper"() {
        given:
        String source = new File("src/yass/options/YassOptions.java").text

        expect:
        !source.contains("super(new OwnerFrame())")
        !source.contains("getToolkit().getScreenSize()")
        !source.contains("setLocation(dim.width")
        source.contains("YassUtils.resolveDialogOwnerWindow")
        source.contains("YassUtils.resolveDialogOwner")
    }

    def "library preferences dialog uses the shared dialog owner helper"() {
        given:
        String source = new File("src/yass/YassLibOptions.java").text

        expect:
        !source.contains("super(a.createOwnerFrame())")
        !source.contains("getToolkit().getScreenSize()")
        !source.contains("setLocation(dim.width")
        source.contains("YassUtils.resolveDialogOwnerWindow")
        source.contains("YassUtils.resolveDialogOwner")
    }
}
