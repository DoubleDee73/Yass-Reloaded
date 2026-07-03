/*
 * Yass Reloaded - Karaoke Editor
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

class YassMicPitchCaptureSpec extends Specification {

    def 'resolveHeight maps a pitch class into the one-octave window'() {
        expect:
        YassMicPitchCapture.resolveHeight(pitchClass, windowLow) == expected

        where:
        pitchClass | windowLow || expected
        0          | 0         || 0     // C in window [0..11] -> C
        9          | 0         || 9     // A in window [0..11] -> A
        0          | 5         || 12    // C in window [5..16] -> next C (12)
        5          | 5         || 5     // window low itself
        4          | 5         || 16    // E just below low wraps up into window
        11         | 0         || 11    // B at top of window
        9          | 17        || 21    // A inside [17..28]
    }

    def 'resolved height always lies within the active window and matches the pitch class'() {
        expect:
        def h = YassMicPitchCapture.resolveHeight(pc, low)
        h >= low
        h <= low + 11
        Math.floorMod(h, 12) == pc

        where:
        pc << (0..11)
        low << [5, 17, -7, 0, 12, 3, 60, 41, -12, 24, 9, 7]
    }

    def 'shifting the window by an octave shifts the resolved height by 12'() {
        given:
        int pc = 9 // A
        int low = 5

        expect:
        YassMicPitchCapture.resolveHeight(pc, low + 12) == YassMicPitchCapture.resolveHeight(pc, low) + 12
        YassMicPitchCapture.resolveHeight(pc, low - 12) == YassMicPitchCapture.resolveHeight(pc, low) - 12
    }

    def 'octaveWindowLow centres a sub-octave page range (G4-D5 -> F4-E5)'() {
        given: 'a page spanning G4 (height 7) to D5 (height 14) -> span 7'
        int g4 = 7
        int d5 = 14

        when:
        int low = YassMicPitchCapture.octaveWindowLow(g4, d5)

        then: 'window is F4 (height 5) .. E5 (height 16)'
        low == 5
        low + 11 == 16
        // both page extremes fall inside the window
        g4 >= low && g4 <= low + 11
        d5 >= low && d5 <= low + 11
    }

    def 'octaveWindowLow clamps to the minimum when the range exceeds an octave'() {
        expect:
        YassMicPitchCapture.octaveWindowLow(min, max) == min

        where:
        min | max
        0   | 11   // exactly an octave
        0   | 20   // wider than an octave
        3   | 15
    }

    def 'octaveWindowLow handles reversed arguments'() {
        expect:
        YassMicPitchCapture.octaveWindowLow(14, 7) == YassMicPitchCapture.octaveWindowLow(7, 14)
    }

    def 'stablePitchClass returns empty when too few readings'() {
        expect:
        YassMicPitchCapture.stablePitchClass(readings) == OptionalInt.empty()

        where:
        readings << [null, [], [9], [9, 9, 9]]
    }

    def 'stablePitchClass returns the dominant pitch class'() {
        expect:
        YassMicPitchCapture.stablePitchClass([9, 9, 9, 9, 9]) == OptionalInt.of(9)
        YassMicPitchCapture.stablePitchClass([7, 7, 7, 8, 7]) == OptionalInt.of(7)
    }

    def 'stablePitchClass treats the octave edge as adjacent (11 and 0 average near the boundary)'() {
        when: 'readings cluster around the C/B boundary'
        def result = YassMicPitchCapture.stablePitchClass([0, 0, 11, 0, 11, 0])

        then: 'the circular mean stays at the boundary (0 or 11), not the linear mean ~5'
        result.present
        result.asInt == 0 || result.asInt == 11
    }
}
