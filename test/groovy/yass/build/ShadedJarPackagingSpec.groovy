package yass.build

import spock.lang.Specification

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

    private static List nodes(def nodeList) {
        (0..<nodeList.length).collect { nodeList.item(it) }
    }
}
