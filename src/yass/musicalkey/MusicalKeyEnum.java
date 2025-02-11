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

package yass.musicalkey;

import yass.I18;

import java.util.Collections;
import java.util.List;

public enum MusicalKeyEnum {
    UNDEFINED(null, 0, null, null, Collections.emptyList(), Collections.emptyList()),
    C_A("musical.key.0", 8, "C", "Am", Collections.emptyList(), List.of(0, 2, 4, 5, 7, 9, 11)),
    G_E("musical.key.1", 9, "G", "Em", Collections.emptyList(), List.of(0, 2, 4, 6, 7, 9, 11)),
    D_H("musical.key.2", 10, "D", "Bm", Collections.emptyList(), List.of(1, 2, 4, 6, 7, 9, 11)),
    A_FSHARP("musical.key.3", 11, "A", "F#m", Collections.emptyList(), List.of(1, 2, 4, 6, 8, 9, 11)),
    E_CSHARP("musical.key.4", 12, "E", "C#m", Collections.emptyList(), List.of(1, 3, 4, 6, 8, 9, 11)),
    B_GSHARP("musical.key.5", 1, "B", "G#m", List.of("Cb", "Abm"), List.of(1, 3, 4, 6, 8, 10, 11)),
    FSHARP_DSHARP("musical.key.6", 2, "F#", "D#m", List.of("Gb", "Ebm"), List.of(1, 3, 5, 6, 8, 10, 11)),
    CSHARP_ASHARP("musical.key.7", 3, "C#", "A#m", List.of("Db", "Bbm"), List.of(0, 1, 3, 5, 6, 8, 10)),
    F_D("musical.key.-1", 7, "F", "Dm", Collections.emptyList(), List.of(0, 2, 4, 5, 7, 9, 10)),
    BFLAT_G("musical.key.-2", 6, "Bb", "Gm", List.of("A#"), List.of(0, 2, 3, 5, 7, 9, 10)),
    EFLAT_C("musical.key.-3", 5, "Eb", "Cm", List.of("D#"), List.of(0, 2, 3, 5, 7, 8, 10)),
    AFLAT_F("musical.key.-4", 4, "Ab", "Fm", List.of("G#"), List.of(0, 1, 3, 5, 7, 8, 10)),
//    DFLAT_BFLAT("musical.key.-5", 3, "Db", "bb", List.of(0, 1, 3, 5, 6, 8, 10)),
//    GFLAT_EFLAT("musical.key.-6", 2, "Gb", "eb", List.of(-1, 1, 3, 5, 6, 8, 10)),
//    CFLAT_AFLAT("musical.key.-7", 1, "Cb", "ab", List.of(-1, 1, 3, 4, 6, 8, 10))
    ;

    private String messageKey;
    private int camelot;
    private String majorKey;
    private String minorKey;

    private List<String> alternativeKeys;

    private List<Integer> notes;

    MusicalKeyEnum(String messageKey,
                   int camelot,
                   String majorKey,
                   String minorKey,
                   List<String> alternativeKeys,
                   List<Integer> notes) {
        this.messageKey = messageKey;
        this.camelot = camelot;
        this.majorKey = majorKey;
        this.minorKey = minorKey;
        this.alternativeKeys = alternativeKeys;
        this.notes = notes;
    }

    @Override
    public String toString() {
        if (messageKey == null) {
            return "";
        }
        return I18.get(messageKey);
    }
    
    public boolean isKey(String key) {
        if (this == UNDEFINED || key == null) {
            return false;
        }
        if (key.equalsIgnoreCase(majorKey)) {
            return true;
        }
        if (key.equalsIgnoreCase(minorKey)) {
            return true;
        }
        return alternativeKeys.stream().anyMatch(key::equalsIgnoreCase);
    }
    
    public static MusicalKeyEnum findKey(String key) {
        if (key == null) {
            return MusicalKeyEnum.UNDEFINED;
        }
        for (MusicalKeyEnum musicalKey : MusicalKeyEnum.values()) {
            if (musicalKey.isKey(key)) {
                return musicalKey;
            }
        }
        return MusicalKeyEnum.UNDEFINED;
    }
    
    public String getRelevantKey() {
        if (this == UNDEFINED) {
            return null;
        }
        if (majorKey.endsWith("b")) {
            return minorKey;
        } else {
            return majorKey;
        }
    }
    
    public boolean isInKey(int note) {
        if (this == UNDEFINED) {
            return true;
        }
        if (note < 0) {
            note = note + 120;
        }
        return notes.contains(note % 12);
    }
}
