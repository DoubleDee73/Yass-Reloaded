package yass.input;

public record EditorInputContext(
        FocusArea focusArea,
        boolean lyricsEditing,
        boolean songHeaderEditing,
        boolean playing,
        boolean recording,
        boolean absolutePitchView,
        boolean hasSelection,
        boolean multiSelection
) {
    public boolean isTypingContext() {
        return focusArea == FocusArea.LYRICS_EDIT || focusArea == FocusArea.SONG_HEADER || songHeaderEditing;
    }
}
