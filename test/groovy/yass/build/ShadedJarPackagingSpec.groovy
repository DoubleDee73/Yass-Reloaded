package yass.build

import spock.lang.Specification

import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import java.nio.file.Path

class ShadedJarPackagingSpec extends Specification {

    def 'shade plugin strips signed dependency metadata from the fat jar'() {
        given:
        def pom = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(Path.of('pom.xml').toFile())
        def shadePlugin = nodes(pom.getElementsByTagName('plugin')).find { plugin ->
            plugin.getElementsByTagName('artifactId').item(0).textContent == 'maven-shade-plugin'
        }
        def signatureFilter = nodes(shadePlugin.getElementsByTagName('filter')).find { filter ->
            filter.getElementsByTagName('artifact').item(0).textContent == '*:*'
        }
        def signatureExcludes = signatureFilter == null ? [] : nodes(signatureFilter.getElementsByTagName('exclude')).collect {
            it.textContent
        }

        expect:
        signatureExcludes.contains('META-INF/*.SF')
        signatureExcludes.contains('META-INF/*.DSA')
        signatureExcludes.contains('META-INF/*.RSA')
    }

    def 'release workflow publishes a versioned Linux jar asset instead of the generic package input jar'() {
        given:
        def workflow = Files.readString(Path.of('.github/workflows/build.yml'))
        def releaseUpload = blockAfter(workflow, 'Upload to GitHub Release')

        expect:
        workflow.contains('RELEASE_REF="${{ inputs.tag || github.ref_name }}"')
        workflow.contains('Yass-Reloaded-${RELEASE_VERSION}-linux-x64.jar')
        releaseUpload.contains('dist/Yass-Reloaded-*-linux-x64.jar')
        !releaseUpload.contains('package-input/Yass-Reloaded.jar')
    }

    def 'nightly workflow publishes a timestamped Linux jar asset instead of a generic nightly jar'() {
        given:
        def workflow = Files.readString(Path.of('.github/workflows/nightly.yml'))

        expect:
        workflow.contains("NIGHTLY_STAMP=\$(date -u +'%Y-%m-%d-%H%M%SZ')")
        workflow.contains('Yass-Reloaded-nightly-${NIGHTLY_STAMP}-linux-x64.jar')
        !workflow.contains('Yass-Reloaded-nightly.jar')
    }

    private static List nodes(def nodeList) {
        (0..<nodeList.length).collect { nodeList.item(it) }
    }

    private static String blockAfter(String text, String marker) {
        def start = text.indexOf(marker)
        start == -1 ? '' : text.substring(start)
    }
}
