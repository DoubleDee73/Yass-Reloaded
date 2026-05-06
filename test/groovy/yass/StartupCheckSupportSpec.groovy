package yass

import spock.lang.Specification

import java.util.concurrent.atomic.AtomicBoolean

class StartupCheckSupportSpec extends Specification {

    def "accumulate evaluates check even when store is already true"() {
        given:
        def called = new AtomicBoolean(false)

        when:
        def result = StartupCheckSupport.accumulate(true, {
            called.set(true)
            return false
        })

        then:
        result
        called.get()
    }

    def "accumulate merges current store with check result"() {
        expect:
        !StartupCheckSupport.accumulate(false, { false })
        StartupCheckSupport.accumulate(false, { true })
        StartupCheckSupport.accumulate(true, { false })
    }
}
