package yass.usdb;

public interface UsdbImportProgressListener {
    default void onGeneralStatus(String message) {
    }

    default void onSongStatus(String message) {
    }

    default void onDetailLog(String message) {
    }
}
