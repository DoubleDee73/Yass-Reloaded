package yass

import spock.lang.Specification

class YassSearchNormalizerSpec extends Specification {

    def 'folds Scandinavian slash vowels like ordinary latin vowels'() {
        expect:
        YassSearchNormalizer.normalizeForSearch('Bløf') == 'blof'
        YassSearchNormalizer.normalizeForSearch('blöf') == 'blof'
    }
}
