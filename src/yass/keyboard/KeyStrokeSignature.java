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

package yass.keyboard;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public record KeyStrokeSignature(int keyCode, int modifiers) {

    static KeyStrokeSignature from(KeyEvent e) {
        return new KeyStrokeSignature(
                e.getKeyCode(),
                e.getModifiersEx() & (
                        InputEvent.CTRL_DOWN_MASK |
                                InputEvent.SHIFT_DOWN_MASK |
                                InputEvent.ALT_DOWN_MASK |
                                InputEvent.META_DOWN_MASK
                )
        );
    }
}
