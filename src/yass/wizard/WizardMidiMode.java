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

package yass.wizard;

import lombok.Getter;
import yass.I18;
import yass.YassEnum;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public enum WizardMidiMode implements YassEnum {
    USE_MIDI("wizard_midi_mode_use", "USE_MIDI"),
    SUGGEST_MIDI("wizard_midi_mode_suggest", "SUGGEST_MIDI"),
    SKIP_MIDI("wizard_midi_mode_skip", "SKIP_MIDI");

    private final String label;
    private final String value;

    WizardMidiMode(String key, String value) {
        this.label = I18.get(key);
        this.value = value;
    }

    @Override
    public List<YassEnum> listElements() {
        return Arrays.stream(values()).collect(Collectors.toList());
    }
    
}
