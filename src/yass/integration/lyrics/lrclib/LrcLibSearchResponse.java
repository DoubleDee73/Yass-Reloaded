package yass.integration.lyrics.lrclib;

import java.util.Collections;
import java.util.List;

public class LrcLibSearchResponse {
    private final List<LrcLibCandidate> candidates;
    private final Long preferredCandidateId;

    public LrcLibSearchResponse(List<LrcLibCandidate> candidates, Long preferredCandidateId) {
        this.candidates = candidates == null ? Collections.emptyList() : List.copyOf(candidates);
        this.preferredCandidateId = preferredCandidateId;
    }

    public List<LrcLibCandidate> getCandidates() {
        return candidates;
    }

    public Long getPreferredCandidateId() {
        return preferredCandidateId;
    }
}
