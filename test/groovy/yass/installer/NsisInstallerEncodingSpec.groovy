package yass.installer

import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class NsisInstallerEncodingSpec extends Specification {

    def 'installer script is UTF-8 with BOM for makensis unicode text'() {
        given:
        byte[] bytes = installerScriptBytes()

        expect:
        bytes.length >= 3
        bytes[0] == (byte) 0xEF
        bytes[1] == (byte) 0xBB
        bytes[2] == (byte) 0xBF

        and:
        String script = installerScript()
        script.contains('Kontextmenüeintrag für Textdateien')
        script.contains('Yass Reloaded ya está instalado. $\\n$\\nElija `OK`')
    }

    def 'installer avoids stale jpackage app images during upgrades'() {
        given:
        String script = installerScript()

        expect:
        script.contains('InstallDirRegKey HKLM "SOFTWARE\\Yass Reloaded" "installdir"')
        script.contains('ExecWait \'"$R0"\'')
        script.contains('RMDir /r "$INSTDIR\\app"')
        script.contains('RMDir /r "$INSTDIR\\runtime"')

        and:
        script.findAll(/File \/r "dist-img\\yass\\\*\.\*"/).size() == 1
        script.contains('WriteRegStr HKLM "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Yass Reloaded" "UninstallString" \'"$INSTDIR\\uninstall.exe"\'')
    }

    private static byte[] installerScriptBytes() {
        Files.readAllBytes(Path.of('nsis-installer.nsi'))
    }

    private static String installerScript() {
        byte[] bytes = installerScriptBytes()
        int offset = bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF ? 3 : 0
        new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8)
    }
}
