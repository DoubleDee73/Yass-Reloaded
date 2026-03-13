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

package yass

import spock.lang.Specification

class AudioAssignmentHeuristicsSpec extends Specification {
    private static final List<String> AUDIO_EXTENSIONS = ['.mp3', '.m4a', '.opus', '.wav']

    def 'keeps existing webm audio assignment'() {
        given:
        YassSong song = song('Artist', 'Title', 'Artist - Title.txt')
        song.setAudio('Artist - Title.webm')

        when:
        def decision = AudioAssignmentHeuristics.evaluate(song,
                ['Artist - Title.webm', 'Artist - Title.mp3', 'Artist - Title (Instrumental).mp3'],
                AUDIO_EXTENSIONS)

        then:
        decision.audio() == 'Artist - Title.webm'
    }

    def 'prefers plain m4a audio over base mp3'() {
        given:
        YassSong song = song('Artist', 'Title', 'Artist - Title.txt')
        song.setAudio('Artist - Title.mp3')

        when:
        def decision = AudioAssignmentHeuristics.evaluate(song,
                ['Artist - Title.mp3', 'Artist - Title.m4a', 'Artist - Title vox.mp3'],
                AUDIO_EXTENSIONS)

        then:
        decision.audio() == 'Artist - Title.m4a'
    }

    def 'sets vocals only for explicit vocals candidate'() {
        given:
        YassSong song = song('Artist', 'Title', 'Artist - Title.txt')

        when:
        def decision = AudioAssignmentHeuristics.evaluate(song,
                ['Artist - Title.mp3', 'Artist - Title Lead Vocals.mp3', 'Artist - Title (Instrumental).mp3'],
                AUDIO_EXTENSIONS)

        then:
        decision.vocals() == 'Artist - Title Lead Vocals.mp3'
    }

    def 'keeps explicit instrumental assignment even if backing variant exists'() {
        given:
        YassSong song = song('Artist', 'Title', 'Artist - Title.txt')
        song.setInstrumental('Artist - Title inst.mp3')

        when:
        def decision = AudioAssignmentHeuristics.evaluate(song,
                ['Artist - Title.mp3', 'Artist - Title vox.mp3', 'Artist - Title inst.mp3', 'Artist - Title (Instrumental with BV).mp3'],
                AUDIO_EXTENSIONS)

        then:
        decision.instrumental() == 'Artist - Title inst.mp3'
        decision.warnings().any { it.contains('solo version may also have a backing/BV variant') }
    }

    def 'warns instead of changing plain base track instrumental when explicit instrumental exists'() {
        given:
        YassSong song = song('Artist', 'Title', 'Artist - Title.txt')
        song.setInstrumental('Artist - Title.mp3')

        when:
        def decision = AudioAssignmentHeuristics.evaluate(song,
                ['Artist - Title.mp3', 'Artist - Title vox.mp3', 'Artist - Title (Instrumental).mp3'],
                AUDIO_EXTENSIONS)

        then:
        decision.instrumental() == 'Artist - Title.mp3'
        decision.warnings().any { it.contains('base track is assigned') }
    }

    def 'uses txt filename context for alternate versions in one folder'() {
        given:
        YassSong song = song('Artist', 'Title (Duet)', 'Artist - Title [DUET].txt')

        when:
        def decision = AudioAssignmentHeuristics.evaluate(song,
                ['Artist - Title [DUET] inst.mp3', 'Artist - Title inst with BV.mp3', 'Artist - Title [DUET] vox.mp3'],
                AUDIO_EXTENSIONS)

        then:
        decision.instrumental() == 'Artist - Title [DUET] inst.mp3'
        decision.vocals() == 'Artist - Title [DUET] vox.mp3'
    }

    private static YassSong song(String artist, String title, String filename) {
        new YassSong('C:/songs/Artist', 'Artist - Title', filename, artist, title)
    }
}
