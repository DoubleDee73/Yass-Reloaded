package yass

import spock.lang.Specification
import spock.lang.Unroll

class PythonRuntimeSupportSpec extends Specification {

    @Unroll
    def "shouldAutodetectDefaultPython returns #expected for '#configuredValue'"() {
        expect:
        PythonRuntimeSupport.shouldAutodetectDefaultPython(configuredValue) == expected

        where:
        configuredValue              || expected
        null                         || true
        ""                           || true
        "python"                     || true
        "python.exe"                 || true
        ".venv/Scripts/python.exe"   || false
        "C:\\Python313\\python.exe"  || false
        "/usr/bin/python3"           || false
    }

    @Unroll
    def "shouldCanonicalizeToolPython returns #expected for '#configuredValue'"() {
        expect:
        PythonRuntimeSupport.shouldCanonicalizeToolPython(configuredValue) == expected

        where:
        configuredValue              || expected
        null                         || false
        ""                           || false
        "python"                     || true
        "python3"                    || true
        "python.exe"                 || true
        ".venv/Scripts/python.exe"   || false
        "/usr/bin/python3"           || false
    }

    def "resolveToolPython falls back to default python when tool runtime is empty"() {
        given:
        def properties = new YassProperties()
        properties.setProperty(PythonRuntimeSupport.PROP_DEFAULT_PYTHON, "C:\\Python313\\python.exe")
        properties.setProperty("whisperx-python", "")

        expect:
        PythonRuntimeSupport.resolveToolPython(properties, "whisperx-python") == "C:\\Python313\\python.exe"
    }
}
