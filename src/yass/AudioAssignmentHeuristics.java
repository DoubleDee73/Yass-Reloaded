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

package yass;

import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToIntFunction;

final class AudioAssignmentHeuristics {
    private static final int STRONG_AUDIO_SCORE = 15;
    private static final int STRONG_VOCALS_SCORE = 30;
    private static final int STRONG_INSTRUMENTAL_SCORE = 25;

    private AudioAssignmentHeuristics() {
    }

    static AudioAssignmentDecision evaluate(YassSong song, Collection<String> filenames, Collection<String> audioExtensions) {
        AudioAssignmentContext context = AudioAssignmentContext.from(song);
        Map<String, AudioCandidate> candidatesByName = new LinkedHashMap<>();
        for (String filename : filenames) {
            AudioCandidate candidate = AudioCandidate.from(filename, audioExtensions, context);
            candidatesByName.put(candidate.filename().toLowerCase(Locale.ROOT), candidate);
        }

        List<AudioCandidate> audioCandidates = candidatesByName.values().stream()
                .filter(AudioCandidate::audioLike)
                .toList();

        AudioCandidate expectedAudio = select(audioCandidates, AudioCandidate::audioScore, STRONG_AUDIO_SCORE);
        AudioCandidate expectedVocals = select(audioCandidates, AudioCandidate::vocalsScore, STRONG_VOCALS_SCORE);
        AudioCandidate expectedInstrumental = select(audioCandidates, AudioCandidate::instrumentalScore, STRONG_INSTRUMENTAL_SCORE);

        List<String> warnings = new ArrayList<>();
        String resolvedAudio = resolveAudio(song.getAudio(), expectedAudio, candidatesByName);
        String resolvedInstrumental = resolveInstrumental(song.getInstrumental(), expectedInstrumental, candidatesByName, expectedVocals != null, context.duet(), warnings);
        String resolvedVocals = resolveVocals(song.getVocals(), expectedVocals, candidatesByName);

        return new AudioAssignmentDecision(resolvedAudio, resolvedInstrumental, resolvedVocals, warnings);
    }

    private static String resolveAudio(String currentValue, AudioCandidate expectedAudio, Map<String, AudioCandidate> candidatesByName) {
        AudioCandidate current = find(currentValue, candidatesByName);
        if (current == null) {
            return expectedAudio != null ? expectedAudio.filename() : currentValue;
        }
        if (current.audioLike() && (current.webmAudioLike() || current.hasHint(AudioHint.VOX))) {
            return current.filename();
        }
        if (expectedAudio == null || current.filename().equalsIgnoreCase(expectedAudio.filename())) {
            return current.filename();
        }
        if (current.audioScore() >= expectedAudio.audioScore()) {
            return current.filename();
        }
        return expectedAudio.filename();
    }

    private static String resolveVocals(String currentValue, AudioCandidate expectedVocals, Map<String, AudioCandidate> candidatesByName) {
        AudioCandidate current = find(currentValue, candidatesByName);
        if (current == null) {
            return expectedVocals != null ? expectedVocals.filename() : currentValue;
        }
        if (expectedVocals == null || current.filename().equalsIgnoreCase(expectedVocals.filename())) {
            return current.filename();
        }
        if (current.vocalsScore() >= STRONG_VOCALS_SCORE) {
            return current.filename();
        }
        return expectedVocals.filename();
    }

    private static String resolveInstrumental(String currentValue,
                                              AudioCandidate expectedInstrumental,
                                              Map<String, AudioCandidate> candidatesByName,
                                              boolean hasExplicitVocals,
                                              boolean duet,
                                              List<String> warnings) {
        AudioCandidate current = find(currentValue, candidatesByName);
        if (current == null) {
            return expectedInstrumental != null ? expectedInstrumental.filename() : currentValue;
        }
        if (current.hasHint(AudioHint.INSTRUMENTAL) || current.hasHint(AudioHint.INST)) {
            if (!duet && current.instrumentalScore() >= STRONG_INSTRUMENTAL_SCORE
                    && expectedInstrumental != null
                    && !current.filename().equalsIgnoreCase(expectedInstrumental.filename())
                    && expectedInstrumental.isBackingVariant()) {
                warnings.add("Instrumental already points to explicit instrumental '" + current.filename()
                        + "'; solo version may also have a backing/BV variant");
            }
            return current.filename();
        }
        if (expectedInstrumental == null) {
            return current.filename();
        }
        if (current.filename().equalsIgnoreCase(expectedInstrumental.filename())) {
            return current.filename();
        }
        if (current.hasHint(AudioHint.PLAIN_ARTIST_TITLE) && hasExplicitVocals) {
            warnings.add("Instrumental looks ambiguous: base track is assigned, but an explicit instrumental file also exists");
            return current.filename();
        }
        return expectedInstrumental.filename();
    }

    private static AudioCandidate find(String filename, Map<String, AudioCandidate> candidatesByName) {
        if (StringUtils.isBlank(filename)) {
            return null;
        }
        return candidatesByName.get(filename.toLowerCase(Locale.ROOT));
    }

    private static AudioCandidate select(List<AudioCandidate> candidates, ToIntFunction<AudioCandidate> scoreFn, int minimumScore) {
        return candidates.stream()
                .sorted(Comparator.comparingInt(scoreFn).reversed().thenComparing(AudioCandidate::filename, String.CASE_INSENSITIVE_ORDER))
                .filter(candidate -> scoreFn.applyAsInt(candidate) >= minimumScore)
                .findFirst()
                .orElse(null);
    }

    private record AudioAssignmentContext(String artist,
                                          String title,
                                          String txtFilename,
                                          boolean duet,
                                          List<String> normalizedTitleKeys) {
        private static AudioAssignmentContext from(YassSong song) {
            String txtFilename = StringUtils.defaultString(song.getFilename());
            String cleanedTxtStem = normalize(Path.of(txtFilename).getFileName().toString().replaceFirst("\\.[^.]+$", "")
                    .replaceAll("\\[[^\\]]+\\]", " "));
            List<String> keys = new ArrayList<>();
            if (StringUtils.isNotBlank(song.getArtist()) && StringUtils.isNotBlank(song.getTitle())) {
                keys.add(normalize(song.getArtist() + " - " + song.getTitle()));
            }
            if (StringUtils.isNotBlank(song.getTitle())) {
                keys.add(normalize(song.getTitle()));
            }
            if (StringUtils.isNotBlank(cleanedTxtStem)) {
                keys.add(cleanedTxtStem);
            }
            String duetBlob = String.join(" ",
                    StringUtils.defaultString(song.getTitle()),
                    StringUtils.defaultString(song.getArtist()),
                    txtFilename,
                    StringUtils.defaultString(song.getDuetSingerNames())).toLowerCase(Locale.ROOT);
            boolean duet = duetBlob.contains("[duet]") || duetBlob.contains("duet");
            return new AudioAssignmentContext(song.getArtist(),
                    song.getTitle(),
                    txtFilename,
                    duet,
                    keys.stream().filter(StringUtils::isNotBlank).distinct().toList());
        }
    }

    private enum AudioHint {
        VOX, LEAD_VOCALS, VOCALS, INSTRUMENTAL, INST, BACKING, WITH_BV, PLAIN_ARTIST_TITLE, WEBM
    }

    private record AudioCandidate(String filename,
                                  boolean audioLike,
                                  boolean webmAudioLike,
                                  int audioScore,
                                  int vocalsScore,
                                  int instrumentalScore,
                                  Set<AudioHint> hints) {
        static AudioCandidate from(String filename, Collection<String> audioExtensions, AudioAssignmentContext context) {
            String lowerName = filename.toLowerCase(Locale.ROOT);
            String stem = lowerName.replaceFirst("\\.[^.]+$", "");
            String normalizedStem = normalize(stem);
            String extension = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
            boolean audioLike = audioExtensions.stream().anyMatch(ext -> lowerName.endsWith(ext.toLowerCase(Locale.ROOT)))
                    || lowerName.endsWith(".webm");
            boolean webmAudioLike = lowerName.endsWith(".webm");
            Set<AudioHint> hints = java.util.EnumSet.noneOf(AudioHint.class);
            if (normalizedStem.contains("vox")) {
                hints.add(AudioHint.VOX);
            }
            if (normalizedStem.contains("lead vocals")) {
                hints.add(AudioHint.LEAD_VOCALS);
            }
            if (normalizedStem.contains("vocal") || normalizedStem.contains("vocals")) {
                hints.add(AudioHint.VOCALS);
            }
            if (normalizedStem.contains("instrumental")) {
                hints.add(AudioHint.INSTRUMENTAL);
            }
            if (normalizedStem.contains("inst")) {
                hints.add(AudioHint.INST);
            }
            if (normalizedStem.contains("backing track") || normalizedStem.contains("back instrum") || normalizedStem.contains("back instr")) {
                hints.add(AudioHint.BACKING);
            }
            if (normalizedStem.contains("with bv") || normalizedStem.contains("with backing") || normalizedStem.contains("backing")) {
                hints.add(AudioHint.WITH_BV);
            }
            if (webmAudioLike) {
                hints.add(AudioHint.WEBM);
            }
            if (isPlainArtistTitle(filename, context)) {
                hints.add(AudioHint.PLAIN_ARTIST_TITLE);
            }

            int titleMatchScore = context.normalizedTitleKeys().stream()
                    .mapToInt(key -> {
                        if (Objects.equals(key, normalizedStem)) {
                            return 20;
                        }
                        return normalizedStem.contains(key) ? 10 : 0;
                    })
                    .max()
                    .orElse(0);

            int audioScore = 0;
            if ((extension.equals(".m4a") || extension.equals(".opus")) && hints.contains(AudioHint.PLAIN_ARTIST_TITLE)) {
                audioScore += 50;
            }
            audioScore += titleMatchScore;
            if (hints.contains(AudioHint.VOX)) {
                audioScore += 7;
            }
            if (hints.contains(AudioHint.LEAD_VOCALS)) {
                audioScore -= 20;
            } else if (hints.contains(AudioHint.VOCALS)) {
                audioScore -= 12;
            }
            if (hints.contains(AudioHint.INSTRUMENTAL) || hints.contains(AudioHint.INST) || normalizedStem.contains("karaoke")) {
                audioScore -= 20;
            }
            if (hints.contains(AudioHint.BACKING)) {
                audioScore -= 20;
            }
            if (hints.contains(AudioHint.WITH_BV)) {
                audioScore -= 10;
            }
            if (hints.contains(AudioHint.PLAIN_ARTIST_TITLE)) {
                audioScore += 2;
            }

            int vocalsScore = 0;
            if (hints.contains(AudioHint.LEAD_VOCALS)) {
                vocalsScore += 60;
            } else if (hints.contains(AudioHint.VOCALS)) {
                vocalsScore += 40;
            } else if (hints.contains(AudioHint.VOX)) {
                vocalsScore += 30;
            }
            vocalsScore += titleMatchScore;
            if (normalizedStem.contains("back vocals") || normalizedStem.contains("backing vocals")) {
                vocalsScore -= 20;
            }
            if (hints.contains(AudioHint.INSTRUMENTAL) || hints.contains(AudioHint.INST) || normalizedStem.contains("karaoke")) {
                vocalsScore -= 30;
            }

            int instrumentalScore = 0;
            if (normalizedStem.contains("instrum only") || normalizedStem.contains("instrum-only")) {
                instrumentalScore += 60;
            } else if (hints.contains(AudioHint.INSTRUMENTAL) || hints.contains(AudioHint.INST)) {
                instrumentalScore += 45;
            } else if (normalizedStem.contains("karaoke") || hints.contains(AudioHint.BACKING)) {
                instrumentalScore += 20;
            }
            instrumentalScore += titleMatchScore;
            if (hints.contains(AudioHint.WITH_BV)) {
                instrumentalScore += 8;
            }
            if (hints.contains(AudioHint.VOX) || hints.contains(AudioHint.VOCALS) || hints.contains(AudioHint.LEAD_VOCALS)) {
                instrumentalScore -= 40;
            }

            return new AudioCandidate(filename, audioLike, webmAudioLike, audioScore, vocalsScore, instrumentalScore, hints);
        }

        boolean hasHint(AudioHint hint) {
            return hints.contains(hint);
        }

        boolean isBackingVariant() {
            return hints.contains(AudioHint.WITH_BV) || hints.contains(AudioHint.BACKING);
        }

        private static boolean isPlainArtistTitle(String filename, AudioAssignmentContext context) {
            String normalizedFilename = normalize(filename.replaceFirst("\\.[^.]+$", ""));
            for (String key : context.normalizedTitleKeys()) {
                if (normalizedFilename.equals(key)) {
                    return true;
                }
            }
            if (StringUtils.isNotBlank(context.artist()) && StringUtils.isNotBlank(context.title())) {
                String combined = normalize(context.artist() + " - " + context.title());
                return normalizedFilename.equals(combined);
            }
            return false;
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
