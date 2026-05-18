package yass

import spock.lang.Specification

class SongTimingTagSanityServiceSpec extends Specification {

    def 'large negative START is detected and removed'() {
        given:
        YassTable table = tableWith("""
#START:-5000
: 0 4 10 La
""")

        when:
        def findings = new SongTimingTagSanityService().cleanup(table, 180)

        then:
        findings*.tag == [UltrastarHeaderTag.START]
        table.getCommentRow('START:') == null
        !table.isSaved()
    }

    def 'END before START removes END'() {
        given:
        YassTable table = tableWith("""
#GAP:30000
#START:20
#END:10
: 0 4 10 La
""")

        when:
        def findings = new SongTimingTagSanityService().cleanup(table, 180)

        then:
        findings*.tag == [UltrastarHeaderTag.END]
        table.getCommentRow('START:') != null
        table.getCommentRow('END:') == null
    }

    def 'preview after audio duration is removed'() {
        given:
        YassTable table = tableWith("""
#PREVIEWSTART:240
: 0 4 10 La
""")

        when:
        def findings = new SongTimingTagSanityService().cleanup(table, 180)

        then:
        findings*.tag == [UltrastarHeaderTag.PREVIEWSTART]
        table.getCommentRow('PREVIEWSTART:') == null
    }

    def 'START after first singable note is removed'() {
        given:
        YassTable table = tableWith("""
#GAP:1000
#START:1.2
: 0 4 10 La
""")

        when:
        def findings = new SongTimingTagSanityService().cleanup(table, 180)

        then:
        findings*.tag == [UltrastarHeaderTag.START]
        table.getCommentRow('START:') == null
    }

    def 'START and END beyond audio duration are removed'() {
        given:
        YassTable table = tableWith("""
#START:181
#END:240
: 0 4 10 La
""")

        when:
        def findings = new SongTimingTagSanityService().cleanup(table, 180)

        then:
        findings*.tag as Set == [UltrastarHeaderTag.START, UltrastarHeaderTag.END] as Set
        table.getCommentRow('START:') == null
        table.getCommentRow('END:') == null
    }

    def 'negative GAP is corrected to zero'() {
        given:
        YassTable table = tableWith("""
#GAP:-250
: 0 4 10 La
""")

        when:
        def findings = new SongTimingTagSanityService().cleanup(table, 180)

        then:
        findings*.tag == [UltrastarHeaderTag.GAP]
        table.getCommentRow('GAP:').getHeaderComment() == '0'
        table.getGap() == 0
    }

    def 'negative note beat is accepted when GAP places it inside the audio'() {
        given:
        YassTable table = tableWith("""
#BPM:240
#GAP:7000
: -100 4 10 La
""")

        expect:
        new SongTimingTagSanityService().validate(table, 180).empty
    }

    def 'every negative note beat is checked against GAP plausibility'() {
        given:
        YassTable table = tableWith("""
#BPM:240
#GAP:1000
: -1 4 10 La
: -100 4 10 la
""")

        when:
        def findings = new SongTimingTagSanityService().validate(table, 180)

        then:
        findings*.tag == [UltrastarHeaderTag.GAP]
        findings[0].reason.contains('-100')
    }

    def 'medley end before medley start removes both medley tags'() {
        given:
        YassTable table = tableWith("""
#MEDLEYSTARTBEAT:40
#MEDLEYENDBEAT:30
: 0 4 10 La
: 80 4 10 la
""")

        when:
        def findings = new SongTimingTagSanityService().cleanup(table, 180)

        then:
        findings*.tag as Set == [UltrastarHeaderTag.MEDLEYSTARTBEAT, UltrastarHeaderTag.MEDLEYENDBEAT] as Set
        table.getCommentRow('MEDLEYSTARTBEAT:') == null
        table.getCommentRow('MEDLEYENDBEAT:') == null
    }

    def 'extreme VIDEOGAP is removed while moderate negative video gap remains valid'() {
        given:
        YassTable extreme = tableWith("""
#VIDEOGAP:-240
: 0 4 10 La
""")
        YassTable moderate = tableWith("""
#VIDEOGAP:-20
: 0 4 10 La
""")

        expect:
        new SongTimingTagSanityService().cleanup(extreme, 180)*.tag == [UltrastarHeaderTag.VIDEOGAP]
        extreme.getCommentRow('VIDEOGAP:') == null
        new SongTimingTagSanityService().cleanup(moderate, 180).empty
        moderate.getCommentRow('VIDEOGAP:') != null
    }

    def 'normal song with missing optional timing tags does not trigger findings'() {
        given:
        YassTable table = tableWith("""
#GAP:0
: 0 4 10 La
""")

        expect:
        new SongTimingTagSanityService().cleanup(table, 180).empty
        table.isSaved()
    }

    private static YassTable tableWith(String body) {
        I18.setDefaultLanguage()
        YassTable table = new YassTable()
        table.init(new YassProperties())
        String text = """#TITLE:Sanity
#ARTIST:Spec
#BPM:120
${body.stripIndent().trim()}
E"""
        assert table.setText(text)
        table.setSaved(true)
        table
    }
}
