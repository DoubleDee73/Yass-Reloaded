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

package yass.hyphenator;

import yass.YassHyphenator;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HyphenatedLanguage {
    private Locale languageLocale;
    private final String displayLanguage;
    
    private Map<String, String> hyphenations = new HashMap<>();
    private String dictionaryPath;
    private boolean dirty;

    public HyphenatedLanguage(Locale languageLocale,
                              String displayLanguage,
                              String dictionaryPath) {
        this.languageLocale = languageLocale;
        this.displayLanguage = displayLanguage;
        this.dictionaryPath = dictionaryPath;
    }

    @Override
    public String toString() {
        return displayLanguage;
    }

    public void init(YassHyphenator yassHyphenator) {
        if (!hyphenations.isEmpty()) {
            return;
        }
        yassHyphenator.setLanguage(languageLocale.getLanguage().toUpperCase());
        if (yassHyphenator.initFallbackHyphenations()) {
            this.hyphenations = yassHyphenator.getFallbackHyphenations();
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public String getDictionaryPath() {
        return dictionaryPath;
    }

    public Map<String, String> getHyphenations() {
        return hyphenations;
    }

    public Locale getLanguageLocale() {
        return languageLocale;
    }
}
