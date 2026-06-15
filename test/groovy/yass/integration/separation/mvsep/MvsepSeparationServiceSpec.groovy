/*
 * Yass Reloaded - Karaoke Editor
 * Copyright (C) 2009-2023 Saruta
 * Copyright (C) 2023 DoubleDee
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package yass.integration.separation.mvsep

import com.google.gson.JsonParser
import spock.lang.Specification
import spock.lang.TempDir
import yass.YassProperties
import yass.options.YtDlpPanel

import java.nio.file.Files
import java.nio.file.Path

class MvsepSeparationServiceSpec extends Specification {
    @TempDir
    Path tempDir

    def 'reverb removal request uses current vocals file and noreverb defaults'() {
        given:
        def properties = new TestProperties()
        properties.setProperty('mvsep-api-token', 'token')
        properties.setProperty('mvsep-output-format', 'wav')
        def service = new MvsepSeparationService(properties)
        def songDir = Files.createDirectories(tempDir.resolve('song')).toFile()
        def vocalsFile = new File(songDir, 'Artist - Title (Vocals).wav')
        vocalsFile.text = 'vocals'

        when:
        def request = service.createReverbRemovalRequest(songDir, vocalsFile, 'Artist - Title')

        then:
        request.songDirectory == songDir.absolutePath
        request.audioFile == vocalsFile
        request.model == MvsepModel.REVERB_REMOVAL.value
        request.modelType == '7'
        request.outputFormat == 'wav'
        request.songBaseName == 'Artist - Title'
    }

    def 'reverb removal maps noreverb download to vocals'() {
        given:
        def properties = new TestProperties()
        def service = new MvsepSeparationService(properties)
        def response = JsonParser.parseString('''
            {
              "data": {
                "files": [
                  {"name": "song_reverb.wav", "url": "https://example.test/song_reverb.wav"},
                  {"name": "song_noreverb.wav", "url": "https://example.test/song_noreverb.wav"}
                ]
              }
            }
        ''').asJsonObject

        when:
        def stems = invokeExtractStemDownloads(service, response, MvsepModel.REVERB_REMOVAL)

        then:
        stems['vocals'].name() == 'song_noreverb.wav'
        stems['dereverb-vocals'].name() == 'song_noreverb.wav'
    }

    def 'opus transcoding uses yt-dlp audio bitrate setting'() {
        given:
        def properties = new TestProperties()
        properties.setProperty(YtDlpPanel.YTDLP_AUDIO_BITRATE, '256')
        def service = new MvsepSeparationService(properties)
        def command = []

        when:
        invokeAddTranscodeArguments(service, command, MvsepOutputFormat.OGG_OPUS)

        then:
        command == ['-c:a', 'libopus', '-b:a', '256k']
    }

    def 'vorbis transcoding maps yt-dlp bitrate setting to stable quality level'() {
        given:
        def properties = new TestProperties()
        properties.setProperty(YtDlpPanel.YTDLP_AUDIO_BITRATE, '320')
        def service = new MvsepSeparationService(properties)
        def command = []

        when:
        invokeAddTranscodeArguments(service, command, MvsepOutputFormat.OGG_VORBIS)

        then:
        command == ['-c:a', 'libvorbis', '-q:a', '10']
    }

    def 'intermediate mvsep wav is moved to normal audio cache for final ogg file'() {
        given:
        def cacheRoot = Files.createDirectories(tempDir.resolve('yass-temp'))
        def properties = new TestProperties()
        properties.setProperty('temp-dir', cacheRoot.toString())
        def service = new MvsepSeparationService(properties)
        def intermediate = tempDir.resolve('Artist - Title (vocals).wav')
        def finalAudio = tempDir.resolve('Artist - Title (vocals).ogg')
        Files.writeString(intermediate, 'wav-data')
        Files.writeString(finalAudio, 'ogg-data')

        when:
        def cachedFile = invokeMoveIntermediateStemToTempCache(service, intermediate.toFile(), finalAudio.toFile())

        then:
        !Files.exists(intermediate)
        cachedFile.parentFile == cacheRoot.resolve('audio-cache').toFile()
        cachedFile.name ==~ /audio-[0-9a-f]{16}-normal\.wav/
        cachedFile.text == 'wav-data'
    }

    private static void invokeAddTranscodeArguments(MvsepSeparationService service,
                                                    List<String> command,
                                                    MvsepOutputFormat format) {
        def method = MvsepSeparationService.getDeclaredMethod('addTranscodeArguments', List, MvsepOutputFormat)
        method.accessible = true
        method.invoke(service, command, format)
    }

    private static File invokeMoveIntermediateStemToTempCache(MvsepSeparationService service,
                                                              File intermediate,
                                                              File finalAudio) {
        def method = MvsepSeparationService.getDeclaredMethod('moveIntermediateStemToTempCache', File, File)
        method.accessible = true
        method.invoke(service, intermediate, finalAudio) as File
    }

    private static Map invokeExtractStemDownloads(MvsepSeparationService service,
                                                  def response,
                                                  MvsepModel model) {
        def method = MvsepSeparationService.getDeclaredMethod('extractStemDownloads',
                Class.forName('com.google.gson.JsonObject'),
                MvsepModel)
        method.accessible = true
        method.invoke(service, response, model) as Map
    }

    private static class TestProperties extends YassProperties {
        @Override
        void load() {
            setDefaultProperties(this)
        }
    }
}
