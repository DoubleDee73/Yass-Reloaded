package yass.integration.separation.audioseparator

import spock.lang.Specification

class AudioSeparatorHealthCheckServiceSpec extends Specification {

    def "detectPythonExecutable resolves an absolute interpreter path"() {
        when:
        def detected = new AudioSeparatorHealthCheckService("", null).detectPythonExecutable()

        then:
        detected
        new File(detected).isAbsolute()
        new File(detected).exists()
    }

    def "candidateScriptDirectories includes user script directories for windows user installs"() {
        when:
        def directories = AudioSeparatorHealthCheckService.candidateScriptDirectories(
                "C:\\Python313\\python.exe",
                "C:\\Python313\\Scripts",
                "C:\\Users\\User\\AppData\\Roaming\\Python")

        then:
        directories*.path.contains("C:\\Python313\\Scripts")
        directories*.path.contains("C:\\Users\\User\\AppData\\Roaming\\Python\\Scripts")
        directories*.path.contains("C:\\Users\\User\\AppData\\Roaming\\Python\\Python313\\Scripts")
    }

    def "managed python executable points into dedicated yass venv"() {
        expect:
        AudioSeparatorHealthCheckService.getManagedVenvDirectory().toString().contains(".yass")
        AudioSeparatorHealthCheckService.getManagedVenvDirectory().toString().contains("audio-separator-venv")
        AudioSeparatorHealthCheckService.getManagedPythonExecutable().toString().contains("audio-separator-venv")
    }
}
