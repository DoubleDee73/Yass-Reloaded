/*
 * Yass Reloaded - Karaoke Editor
 * Copyright (C) 2009-2023 Saruta
 * Copyright (C) 2024-2025 DoubleDee
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

package yass.alignment;

import yass.analysis.PitchDetector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Energy helpers shared by the note-shaping passes so "is this loud enough to be sung?" is judged
 * consistently. The loud reference is a high percentile of per-frame energy across the whole track,
 * so a run's energy can be compared to it as a fraction (breath/noise sits far below sung notes).
 */
public final class VocalEnergy {

    private static final double LOUD_PERCENTILE = 0.85d;

    private VocalEnergy() {
    }

    /**
     * The song's loud reference: the {@value #LOUD_PERCENTILE} percentile of finite, positive
     * per-frame energies. Returns 0 when no energy information is available (older frame sets without
     * attached energy), which callers treat as "energy gate disabled".
     */
    public static double loudReference(List<PitchDetector.PitchData> frames) {
        if (frames == null || frames.isEmpty()) {
            return 0d;
        }
        List<Double> energies = new ArrayList<>();
        for (PitchDetector.PitchData frame : frames) {
            if (frame != null && Double.isFinite(frame.energy()) && frame.energy() > 0d) {
                energies.add(frame.energy());
            }
        }
        if (energies.isEmpty()) {
            return 0d;
        }
        Collections.sort(energies);
        int index = (int) Math.floor((energies.size() - 1) * LOUD_PERCENTILE);
        return energies.get(index);
    }

    /** Mean per-frame energy over [fromMs, toMs), or 0 when no energy-bearing frames fall inside. */
    public static double meanEnergy(List<PitchDetector.PitchData> frames, double fromMs, double toMs) {
        if (frames == null || frames.isEmpty() || toMs <= fromMs) {
            return 0d;
        }
        double sum = 0d;
        int count = 0;
        for (PitchDetector.PitchData frame : frames) {
            if (frame == null || !Double.isFinite(frame.energy())) {
                continue;
            }
            double ms = frame.time() * 1000.0;
            if (ms < fromMs || ms >= toMs) {
                continue;
            }
            sum += frame.energy();
            count++;
        }
        return count == 0 ? 0d : sum / count;
    }
}
