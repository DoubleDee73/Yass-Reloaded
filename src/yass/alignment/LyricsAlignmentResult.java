package yass.alignment;

import java.util.Collections;
import java.util.List;

public class LyricsAlignmentResult {
    private final int movedNotes;
    private final int matchedRegions;
    private final int ignoredWords;
    private final double averageBeatDelta;
    private final double appliedGapMs;
    private final List<AlignmentAnchor> anchors;

    public LyricsAlignmentResult(int movedNotes,
                                 int matchedRegions,
                                 int ignoredWords,
                                 double averageBeatDelta,
                                 double appliedGapMs,
                                 List<AlignmentAnchor> anchors) {
        this.movedNotes = movedNotes;
        this.matchedRegions = matchedRegions;
        this.ignoredWords = ignoredWords;
        this.averageBeatDelta = averageBeatDelta;
        this.appliedGapMs = appliedGapMs;
        this.anchors = anchors == null ? Collections.emptyList() : List.copyOf(anchors);
    }

    public int getMovedNotes() { return movedNotes; }
    public int getMatchedRegions() { return matchedRegions; }
    public int getIgnoredWords() { return ignoredWords; }
    public double getAverageBeatDelta() { return averageBeatDelta; }
    public double getAppliedGapMs() { return appliedGapMs; }
    public List<AlignmentAnchor> getAnchors() { return anchors; }
}