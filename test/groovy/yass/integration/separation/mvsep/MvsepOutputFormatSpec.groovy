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

import spock.lang.Specification

class MvsepOutputFormatSpec extends Specification {

    def 'ogg formats are requested from MVSEP as wav and converted locally'() {
        expect:
        MvsepOutputFormat.OGG_VORBIS.requiresLocalTranscode()
        MvsepOutputFormat.OGG_VORBIS.remoteApiValue == MvsepOutputFormat.WAV.apiValue
        MvsepOutputFormat.OGG_VORBIS.downloadExtension == MvsepOutputFormat.WAV.extension
        MvsepOutputFormat.OGG_VORBIS.extension == 'ogg'

        MvsepOutputFormat.OGG_OPUS.requiresLocalTranscode()
        MvsepOutputFormat.OGG_OPUS.remoteApiValue == MvsepOutputFormat.WAV.apiValue
        MvsepOutputFormat.OGG_OPUS.downloadExtension == MvsepOutputFormat.WAV.extension
        MvsepOutputFormat.OGG_OPUS.extension == 'opus'
    }

    def 'plain ogg and opus values are accepted as aliases'() {
        expect:
        MvsepOutputFormat.fromValue('ogg') == MvsepOutputFormat.OGG_VORBIS
        MvsepOutputFormat.fromValue('opus') == MvsepOutputFormat.OGG_OPUS
    }
}
