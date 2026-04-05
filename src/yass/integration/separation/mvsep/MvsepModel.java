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
    ENSEMBLE_TWO_STEM("ensemble_two_stem", "Ensemble (vocals, instrum)", 26, null, null, false, true),
    ENSEMBLE_FIVE_STEM("ensemble_five_stem", "Ensemble (vocals, instrum, bass, drums, other)", 28, null, null, false, true),
    ENSEMBLE_ALL_IN("ensemble_all_in", "Ensemble All-In (vocals, bass, drums, piano, guitar, lead/back vocals, other)", 30, null, null, false, true),
    BS_ROFORMER_SW("bs_roformer_sw", "BS Roformer SW (vocals, bass, drums, guitar, piano, other)", 63, null, null, false, false),
    DEMUCS4_HT("demucs4_ht", "Demucs4 HT (vocals, drums, bass, other)", 20, null, null, false, false),
    BS_ROFORMER("bs_roformer", "BS Roformer (vocals, instrumental)", 40, null, null, false, false),
    MELBAND_ROFORMER("melband_roformer", "MelBand Roformer (vocals, instrumental)", 48, null, null, false, false),
    MDX23C("mdx23c", "MDX23C (vocals, instrumental)", 25, null, null, false, false),
    SCNET("scnet", "SCNet (vocals, instrumental)", 46, null, null, false, false),
    MDX_B("mdx_b", "MDX B (vocals, instrumental)", 23, null, null, false, false),
    ULTIMATE_VOCAL_REMOVER_VR("ultimate_vocal_remover_vr", "Ultimate Vocal Remover VR (vocals, music)", 9, null, null, false, false),
    DEMUCS4_VOCALS_2023("demucs4_vocals_2023", "Demucs4 Vocals 2023 (vocals, instrum)", 27, null, null, false, false),
    KARAOKE_LEAD_BACK("karaoke_lead_back", "MVSep Karaoke (lead/back vocals)", 49, "1", "1", true, false),
    MDX_B_KARAOKE("mdx_b_karaoke", "MDX-B Karaoke (lead/back vocals)", 12, null, null, false, false),
    MEDLEY_VOX("medley_vox", "Medley Vox (Multi-singer separation)", 53, null, null, false, false),
    MVSEP_MULTICHANNEL_BS("mvsep_multichannel_bs", "MVSep Multichannel BS (vocals, instrumental)", 43, null, null, false, false),
    MVSEP_MALE_FEMALE("mvsep_male_female", "MVSep Male/Female separation", 57, null, null, false, false),
    DEMUCS3_MODEL("demucs3_model", "Demucs3 Model (vocals, drums, bass, other)", 10, null, null, false, false),
    MDX_A_B("mdx_a_b", "MDX A/B (vocals, drums, bass, other)", 7, null, null, false, false),
    VIT_LARGE_23("vit_large_23", "Vit Large 23 (vocals, instrum)", 33, null, null, false, false),
    UVRV5_DEMUCS("uvrv5_demucs", "UVRv5 Demucs (vocals, music)", 17, null, null, false, false),
    MVSEP_OLD_VOCAL_MODEL("mvsep_old_vocal_model", "MVSep Old Vocal Model (vocals, music)", 19, null, null, false, false),
    DEMUCS2("demucs2", "Demucs2 (vocals, drums, bass, other)", 13, null, null, false, false),
    DANNA_SEP("danna_sep", "Danna Sep (vocals, drums, bass, other)", 15, null, null, false, false),
    BYTE_DANCE("byte_dance", "Byte Dance (vocals, drums, bass, other)", 16, null, null, false, false);

    private final String value;
    private final String label;
    private final int sepType;
    private final String addOpt1;
    private final String addOpt2;
    private final boolean karaokeThreeStem;
    private final boolean premiumOnly;

    MvsepModel(String value,
               String label,
               int sepType,
               String addOpt1,
               String addOpt2,
               boolean karaokeThreeStem,
               boolean premiumOnly) {
        this.value = value;
        this.label = label;
        this.sepType = sepType;
        this.addOpt1 = addOpt1;
        this.addOpt2 = addOpt2;
        this.karaokeThreeStem = karaokeThreeStem;
        this.premiumOnly = premiumOnly;
    }

    public static MvsepModel fromValue(String value) {
        for (MvsepModel model : values()) {
            if (model.value.equalsIgnoreCase(value)) {
                return model;
            }
        }
        return MELBAND_ROFORMER;
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
        if (premiumOnly && (accountInfo == null || !accountInfo.hasPremiumAccess())) {
            return false;
        }
        if (algorithms == null || algorithms.isEmpty()) {
            return true;
        }
        MvsepAlgorithmInfo info = algorithms.get(sepType);
        if (info == null) {
            return true;
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