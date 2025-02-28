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

package yass;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;

public enum KeyboardMapping {
    QWERTY(List.of(KeyEvent.VK_Q, KeyEvent.VK_2, KeyEvent.VK_W, KeyEvent.VK_3, KeyEvent.VK_E, KeyEvent.VK_4,
                         KeyEvent.VK_R, KeyEvent.VK_5, KeyEvent.VK_T, KeyEvent.VK_6, KeyEvent.VK_Y, KeyEvent.VK_7,
                         KeyEvent.VK_U, KeyEvent.VK_8, KeyEvent.VK_I, KeyEvent.VK_9, KeyEvent.VK_O, KeyEvent.VK_0,
                         KeyEvent.VK_P)),

    QWERTZ(List.of(KeyEvent.VK_Q, KeyEvent.VK_2, KeyEvent.VK_W, KeyEvent.VK_3, KeyEvent.VK_E, KeyEvent.VK_4,
                         KeyEvent.VK_R, KeyEvent.VK_5, KeyEvent.VK_T, KeyEvent.VK_6, KeyEvent.VK_Z, KeyEvent.VK_7,
                         KeyEvent.VK_U, KeyEvent.VK_8, KeyEvent.VK_I, KeyEvent.VK_9, KeyEvent.VK_O, KeyEvent.VK_0,
                         KeyEvent.VK_P)),
    AZERTY(List.of(KeyEvent.VK_A, KeyEvent.VK_2, KeyEvent.VK_Z, KeyEvent.VK_3, KeyEvent.VK_E, KeyEvent.VK_4,
                         KeyEvent.VK_R, KeyEvent.VK_5, KeyEvent.VK_T, KeyEvent.VK_6, KeyEvent.VK_Y, KeyEvent.VK_7,
                         KeyEvent.VK_U, KeyEvent.VK_8, KeyEvent.VK_I, KeyEvent.VK_9, KeyEvent.VK_O, KeyEvent.VK_0,
                         KeyEvent.VK_P));

    final List<Integer> keys;

    KeyboardMapping(List<Integer> keys) {
        this.keys = keys;
    }
    
    public static KeyboardMapping getKeyboardMapping(Locale locale) {
        String country = locale.getCountry();
        if (country.equals("BE")) {
            country = locale.getLanguage() + "_" + country;
        }
        return switch (country) {
            case "DE", "AT", "nl_BE", "LI", "AL", "CZ", "HU", "PL", "RO", "SK", "BA", "CS", "SI", "CH", "LU" -> QWERTZ;
            case "FR", "fr_BE" -> AZERTY;
            default -> QWERTY;
        };
    }
    
    public int getPosition(int keyCode) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i) == keyCode) {
                return i;
            }
            if (this == QWERTY || this == QWERTZ) {
                if (keyCode == KeyEvent.VK_Z || keyCode == KeyEvent.VK_Y) {
                    return 10;
                }
            }
        }
        return -1;
    }
    
    public String getLetter(int note) {
        return KeyEvent.getKeyText(keys.get(note));
    }
}
