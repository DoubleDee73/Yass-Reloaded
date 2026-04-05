package yass.alignment;

public class TranscriptRebuildResult {
    private final int noteCount;
    private final int pageBreakCount;
    private final int gapMs;

    public TranscriptRebuildResult(int noteCount, int pageBreakCount, int gapMs) {
        this.noteCount = noteCount;
        this.pageBreakCount = pageBreakCount;
        this.gapMs = gapMs;
    }

    public int getNoteCount() {
        return noteCount;
    }

    public int getPageBreakCount() {
        return pageBreakCount;
    }

    public int getGapMs() {
        return gapMs;
    }
}
