package yass.alignment;

import java.util.Collections;
import java.util.List;

public class LyricToken {
    private final String rawText;
    private final String normalizedText;
    private final List<Integer> rowIndices;

    public LyricToken(String rawText, String normalizedText, List<Integer> rowIndices) {
        this.rawText = rawText;
        this.normalizedText = normalizedText;
        this.rowIndices = rowIndices == null ? Collections.emptyList() : List.copyOf(rowIndices);
    }

    public String getRawText() { return rawText; }
    public String getNormalizedText() { return normalizedText; }
    public List<Integer> getRowIndices() { return rowIndices; }
    public int getFirstRow() { return rowIndices.isEmpty() ? -1 : rowIndices.get(0); }
    public int getLastRow() { return rowIndices.isEmpty() ? -1 : rowIndices.get(rowIndices.size() - 1); }
}