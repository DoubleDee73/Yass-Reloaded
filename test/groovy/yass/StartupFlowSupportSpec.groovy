package yass

import spock.lang.Specification

class StartupFlowSupportSpec extends Specification {

    def "should stop startup when first-run library setup is cancelled"() {
        expect:
        !StartupFlowSupport.shouldContinueAfterLibrarySetup(true, false)
    }

    def "should continue startup when first-run library setup is confirmed"() {
        expect:
        StartupFlowSupport.shouldContinueAfterLibrarySetup(true, true)
    }

    def "should continue startup when library setup dialog was not part of first-run"() {
        expect:
        StartupFlowSupport.shouldContinueAfterLibrarySetup(false, false)
    }
}
