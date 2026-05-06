package yass.wizard

import spock.lang.Specification

class YouTubeMetadataParserSpec extends Specification {

    def "inferMetadata removes ids and common youtube noise from canonical temp names"() {
        expect:
        def metadata = YouTubeMetadataParser.inferMetadata("dQw4w9WgXcQ - Rick Astley - Never Gonna Give You Up (Official Video)", "dQw4w9WgXcQ")
        metadata.artist == "Rick Astley"
        metadata.title == "Never Gonna Give You Up"
    }

    def "inferMetadata keeps artist empty when source is too noisy"() {
        expect:
        def metadata = YouTubeMetadataParser.inferMetadata("dQw4w9WgXcQ - official video lyrics hd", "dQw4w9WgXcQ")
        metadata.artist == ""
        metadata.title == ""
    }

    def "repairStoredMetadata fixes legacy swapped youtube id metadata"() {
        expect:
        def metadata = YouTubeMetadataParser.repairStoredMetadata("u1hbwc8ADKg", "Sting - My One And Only Love", "u1hbwc8ADKg")
        metadata.artist == "Sting"
        metadata.title == "My One And Only Love"
    }
}
