package yass.wizard

import spock.lang.Specification

class MP3Spec extends Specification {

    def "resolvedMetadata keeps existing wizard values when they are already meaningful"() {
        expect:
        def resolved = MP3.resolveMetadata("Sting", "My One And Only Love", "u1hbwc8ADKg", "Sting - My One And Only Love")
        resolved.artist == "Sting"
        resolved.title == "My One And Only Love"
    }

    def "resolvedMetadata falls back to parsed values when wizard values are still unknown"() {
        expect:
        def resolved = MP3.resolveMetadata("UnknownArtist", "UnknownTitle", "Sting", "My One And Only Love")
        resolved.artist == "Sting"
        resolved.title == "My One And Only Love"
    }
}
