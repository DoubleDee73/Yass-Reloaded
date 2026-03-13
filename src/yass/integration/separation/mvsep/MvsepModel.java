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

package yass.integration.separation.mvsep;

import yass.YassEnum;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public enum MvsepModel implements YassEnum {
    KARAOKE_LEAD_BACK("karaoke_lead_back", "MVSep Karaoke (lead/back vocals)", 49, "1", "1", true),
    ENSEMBLE("ensemble", "Ensemble (vocals + instrumental)", 26, "0", "7", false),
    MDX23C("mdx23c", "MDX23C (vocals + instrumental)", 25, "7", null, false),
    DEMUCS_VOCALS_2023("demucs_vocals_2023", "Demucs 4 Vocals 2023", 27, null, null, false);

    private final String value;
    private final String label;
    private final int sepType;
    private final String addOpt1;
    private final String addOpt2;
    private final boolean karaokeThreeStem;

    MvsepModel(String value, String label, int sepType, String addOpt1, String addOpt2, boolean karaokeThreeStem) {
        this.value = value;
        this.label = label;
        this.sepType = sepType;
        this.addOpt1 = addOpt1;
        this.addOpt2 = addOpt2;
        this.karaokeThreeStem = karaokeThreeStem;
    }

    public static MvsepModel fromValue(String value) {
        for (MvsepModel model : values()) {
            if (model.value.equalsIgnoreCase(value)) {
                return model;
            }
        }
        return KARAOKE_LEAD_BACK;
    }

    public int getSepType() {
        return sepType;
    }

    public String getAddOpt1() {
        return addOpt1;
    }

    public String getAddOpt2() {
        return addOpt2;
    }

    public boolean isKaraokeThreeStem() {
        return karaokeThreeStem;
    }

    public boolean isAllowedFor(MvsepAccountInfo accountInfo, Map<Integer, MvsepAlgorithmInfo> algorithms) {
        if (algorithms == null || algorithms.isEmpty()) {
            return true;
        }
        MvsepAlgorithmInfo info = algorithms.get(sepType);
        if (info == null) {
            return false;
        }
        return !info.isPremiumOnly() || (accountInfo != null && accountInfo.hasPremiumAccess());
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public List<YassEnum> listElements() {
        return Arrays.asList(values());
    }

    @Override
    public String getLabel() {
        return label;
    }
}
