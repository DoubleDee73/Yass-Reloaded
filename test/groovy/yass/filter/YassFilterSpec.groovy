package yass.filter

import spock.lang.Specification

class YassFilterSpec extends Specification {

    def 'matches library text across equivalent latin diacritics'() {
        expect:
        YassFilter.containsIgnoreCase('Bløf - Harder Dan Ik Hebben Kan', 'blof')
        YassFilter.containsIgnoreCase('Bløf - Harder Dan Ik Hebben Kan', 'blöf')
    }
}
