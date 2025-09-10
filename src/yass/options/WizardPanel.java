/*
 * Yass - Karaoke Editor
 * Copyright (C) 2009 Saruta
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

package yass.options;

import yass.I18;
import yass.wizard.WizardMidiMode;

/**
 * Wizard-Default settings panel
 *
 * @author DoubleDee
 */
public class WizardPanel extends OptionsPanel {

    private static final long serialVersionUID = 1L;

    /**
     * Gets the body attribute of the DirPanel object
     */
    public void addRows() {
        setLabelWidth(140);
        addText(I18.get("options_wizard_defaults_creator"), "creator");
        addChoice(I18.get("options_wizard_defaults_midi"), WizardMidiMode.values(), "wizard-skip-midi");
    }
}

