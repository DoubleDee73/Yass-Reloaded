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

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.JTextComponent;
import java.awt.*;

public class OverwriteCaret extends DefaultCaret {
    private boolean overwriteMode = false;

    public void setOverwriteMode(boolean overwriteMode) {
        this.overwriteMode = overwriteMode;
        repaint(); // Aktualisiert die Darstellung
    }

    @Override
    protected synchronized void damage(Rectangle r) {
        if (r == null) {
            return;
        }
        // Übersteuert den "Schaden" des Cursors, um ihn visuell wie ein Block darzustellen
        x = r.x;
        y = r.y;
        width = getComponent().getFontMetrics(getComponent().getFont()).charWidth('m'); // Breite eines Buchstabens
        height = r.height;
        repaint();
    }

    @Override
    public void paint(Graphics g) {
        if (isVisible()) {
            try {
                JTextComponent comp = getComponent();
                Rectangle r = comp.modelToView(getDot());
                if (r == null) return;

                g.setColor(comp.getCaretColor());

                if (overwriteMode) {
                    // Blockartige Darstellung des Cursors im Overwrite-Modus
                    g.fillRect(r.x, r.y + r.height - 2, width, 1);
                } else {
                    // Standard-Linien-Caret zeichnen
                    g.drawLine(r.x, r.y, r.x, r.y + r.height - 1);
                }
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Description of the Method
     *
     * @param r
     *            Description of the Parameter
     */
    protected void adjustVisibility(Rectangle r) {
    }

    @Override
    public boolean isVisible() {
        return true;
    }
}
