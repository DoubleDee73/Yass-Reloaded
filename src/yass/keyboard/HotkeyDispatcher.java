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

import javax.swing.*;
import java.awt.event.KeyEvent;

public class HotkeyDispatcher {
    private static final long DOUBLE_PRESS_THRESHOLD_MS = 300;

    private LastKeyPress lastKey;
    private Timer singleActionTimer;

    public void handleKeyPressed(KeyEvent e,
                                 Runnable singleAction,
                                 Runnable doubleAction) {

        long now = System.currentTimeMillis();
        KeyStrokeSignature current = KeyStrokeSignature.from(e);

        if (lastKey != null
                && lastKey.signature().equals(current)
                && now - lastKey.timestamp() <= DOUBLE_PRESS_THRESHOLD_MS) {

            cancelSingleAction();
            lastKey = null;
            doubleAction.run();
            return;
        }

        lastKey = new LastKeyPress(current, now);

        singleActionTimer = new Timer((int) DOUBLE_PRESS_THRESHOLD_MS, ev -> {
            singleAction.run();
            lastKey = null;
        });
        singleActionTimer.setRepeats(false);
        singleActionTimer.start();
    }

    private void cancelSingleAction() {
        if (singleActionTimer != null) {
            singleActionTimer.stop();
            singleActionTimer = null;
        }
    }
}
