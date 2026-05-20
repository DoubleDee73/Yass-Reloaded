package yass.usdb;

import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class UsdbImportQueueJob {
    public enum Mode {
        IMPORT,
        IMPORT_AND_SEPARATE,
        SEPARATE_EXISTING_SONG
    }

    public enum State {
        QUEUED,
        IMPORTING,
        WAITING_SEPARATION,
        SEPARATING,
        FINALIZING,
        CANCELED,
        DONE,
        FAILED
    }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String id = UUID.randomUUID().toString();
    private final Mode mode;
    private final UsdbSongSummary summary;
    private final boolean separateAfterImport;
    private final UsdbImportConflictChoice conflictChoice;
    private final String displayName;
    private final String artist;
    private final String title;
    private final boolean existingSeparationAssigned;
    private final StringBuilder generalLog = new StringBuilder();
    private final StringBuilder detailLog = new StringBuilder();
    private volatile State state = State.QUEUED;
    private volatile String currentSongStatus = "Queued";
    private volatile String currentGeneralStatus = "Queued";
    private volatile Path songFile;
    private volatile String errorMessage;
    private volatile boolean cancellationRequested;
    private volatile long lastUpdatedAt = System.currentTimeMillis();

    public UsdbImportQueueJob(UsdbSongSummary summary, boolean separateAfterImport, UsdbImportConflictChoice conflictChoice) {
        this.mode = separateAfterImport ? Mode.IMPORT_AND_SEPARATE : Mode.IMPORT;
        this.summary = summary;
        this.separateAfterImport = separateAfterImport;
        this.conflictChoice = conflictChoice;
        this.artist = summary != null ? StringUtils.defaultString(summary.artist()) : "";
        this.title = summary != null ? StringUtils.defaultString(summary.title()) : "";
        this.displayName = artist + " - " + title;
        this.existingSeparationAssigned = false;
        appendGeneral("Queued " + displayName);
        appendDetail("Created queue job for " + displayName);
    }

    public static UsdbImportQueueJob forExistingSongSeparation(Path songFile,
                                                               String displayName,
                                                               String artist,
                                                               String title,
                                                               boolean existingSeparationAssigned) {
        return new UsdbImportQueueJob(songFile, displayName, artist, title, existingSeparationAssigned);
    }

    private UsdbImportQueueJob(Path songFile,
                               String displayName,
                               String artist,
                               String title,
                               boolean existingSeparationAssigned) {
        this.mode = Mode.SEPARATE_EXISTING_SONG;
        this.summary = null;
        this.separateAfterImport = false;
        this.conflictChoice = null;
        this.displayName = StringUtils.defaultIfBlank(displayName,
                songFile != null ? songFile.getFileName().toString() : "");
        this.artist = StringUtils.defaultString(artist);
        this.title = StringUtils.defaultString(title);
        this.existingSeparationAssigned = existingSeparationAssigned;
        this.songFile = songFile;
        appendGeneral("Queued " + this.displayName);
        appendDetail("Created separation queue job for " + this.displayName);
    }

    public String getId() {
        return id;
    }

    public Mode getMode() {
        return mode;
    }

    public UsdbSongSummary getSummary() {
        return summary;
    }

    public boolean isSeparateAfterImport() {
        return separateAfterImport;
    }

    public UsdbImportConflictChoice getConflictChoice() {
        return conflictChoice;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getArtist() {
        return artist;
    }

    public String getTitle() {
        return title;
    }

    public boolean isExistingSeparationAssigned() {
        return existingSeparationAssigned;
    }

    public boolean isImportJob() {
        return mode == Mode.IMPORT || mode == Mode.IMPORT_AND_SEPARATE;
    }

    public boolean isSeparationJob() {
        return mode == Mode.IMPORT_AND_SEPARATE || mode == Mode.SEPARATE_EXISTING_SONG;
    }

    public synchronized void appendGeneral(String message) {
        currentGeneralStatus = StringUtils.defaultIfBlank(message, currentGeneralStatus);
        appendLine(generalLog, currentGeneralStatus);
        touch();
    }

    public synchronized void appendDetail(String message) {
        currentSongStatus = StringUtils.defaultIfBlank(message, currentSongStatus);
        appendLine(detailLog, currentSongStatus);
        touch();
    }

    private void appendLine(StringBuilder builder, String message) {
        if (StringUtils.isBlank(message)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(System.lineSeparator());
        }
        builder.append("[").append(LocalDateTime.now().format(TS)).append("] ").append(message);
    }

    public synchronized String getGeneralLog() {
        return generalLog.toString();
    }

    public synchronized String getDetailLog() {
        return detailLog.toString();
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
        touch();
    }

    public String getCurrentSongStatus() {
        return currentSongStatus;
    }

    public String getCurrentGeneralStatus() {
        return currentGeneralStatus;
    }

    public Path getSongFile() {
        return songFile;
    }

    public void setSongFile(Path songFile) {
        this.songFile = songFile;
        touch();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        touch();
    }

    public boolean isCancellationRequested() {
        return cancellationRequested;
    }

    public void setCancellationRequested(boolean cancellationRequested) {
        this.cancellationRequested = cancellationRequested;
        touch();
    }

    public long getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public boolean isTerminal() {
        return state == State.DONE || state == State.CANCELED || state == State.FAILED;
    }

    public boolean isRemovable() {
        return state == State.DONE || state == State.CANCELED;
    }

    private void touch() {
        lastUpdatedAt = System.currentTimeMillis();
    }
}
