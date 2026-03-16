package yass.alignment;

public class AlignmentAnchor {
    private final int lyricStart;
    private final int lyricEnd;
    private final int transcriptStart;
    private final int transcriptEnd;
    private final double score;

    public AlignmentAnchor(int lyricStart, int lyricEnd, int transcriptStart, int transcriptEnd, double score) {
        this.lyricStart = lyricStart;
        this.lyricEnd = lyricEnd;
        this.transcriptStart = transcriptStart;
        this.transcriptEnd = transcriptEnd;
        this.score = score;
    }

    public int getLyricStart() { return lyricStart; }
    public int getLyricEnd() { return lyricEnd; }
    public int getTranscriptStart() { return transcriptStart; }
    public int getTranscriptEnd() { return transcriptEnd; }
    public double getScore() { return score; }
}