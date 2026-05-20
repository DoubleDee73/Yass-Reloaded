package yass.usdb;

import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.YassActions;
import yass.YassSong;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UsdbImportQueueService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    public interface Listener {
        void onQueueChanged();
    }

    private final YassActions actions;
    private final List<UsdbImportQueueJob> jobs = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService importExecutor;
    private final ExecutorService separationExecutor;
    private final boolean autoShowDialog;
    private final Map<String, Future<?>> importTasks = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> separationTasks = new ConcurrentHashMap<>();
    private UsdbImportQueueDialog dialog;

    public UsdbImportQueueService(YassActions actions) {
        this(actions,
                Executors.newSingleThreadExecutor(r -> new Thread(r, "usdb-import-queue")),
                Executors.newSingleThreadExecutor(r -> new Thread(r, "usdb-separation-queue")),
                true);
    }

    UsdbImportQueueService(YassActions actions,
                           ExecutorService importExecutor,
                           ExecutorService separationExecutor,
                           boolean autoShowDialog) {
        this.actions = actions;
        this.importExecutor = importExecutor;
        this.separationExecutor = separationExecutor;
        this.autoShowDialog = autoShowDialog;
    }

    public UsdbImportQueueJob enqueue(UsdbSongSummary summary,
                                      boolean separateAfterImport,
                                      UsdbImportConflictChoice conflictChoice) {
        UsdbImportQueueJob job = new UsdbImportQueueJob(summary, separateAfterImport, conflictChoice);
        synchronized (jobs) {
            jobs.add(job);
        }
        fireQueueChanged();
        Future<?> future = importExecutor.submit(() -> runImport(job));
        importTasks.put(job.getId(), future);
        if (autoShowDialog && actions != null) {
            SwingUtilities.invokeLater(() -> showDialog(actions.createOwnerFrame()));
        }
        return job;
    }

    public List<UsdbImportQueueJob> enqueueExistingSongSeparation(List<YassSong> songs) {
        if (songs == null || songs.isEmpty()) {
            return List.of();
        }
        List<UsdbImportQueueJob> queued = new ArrayList<>();
        for (YassSong song : songs) {
            if (song == null) {
                continue;
            }
            Path songFile = Path.of(song.getDirectory(), song.getFilename());
            if (hasActiveSeparationJobFor(songFile)) {
                continue;
            }
            String displayName = StringUtils.defaultString(song.getArtist()) + " - " + StringUtils.defaultString(song.getTitle());
            UsdbImportQueueJob job = UsdbImportQueueJob.forExistingSongSeparation(
                    songFile,
                    displayName,
                    song.getArtist(),
                    song.getTitle(),
                    YassActions.hasExistingSeparatedStemAssignments(song));
            synchronized (jobs) {
                jobs.add(job);
            }
            updateState(job,
                    UsdbImportQueueJob.State.WAITING_SEPARATION,
                    "Queued audio separation for " + job.getDisplayName(),
                    buildSeparationQueueStatus(job));
            if (job.isExistingSeparationAssigned()) {
                job.appendDetail(I18.get("usdb_queue_existing_separation_hint"));
            }
            Future<?> separationFuture = separationExecutor.submit(() -> runSeparation(job, songFile));
            separationTasks.put(job.getId(), separationFuture);
            queued.add(job);
        }
        fireQueueChanged();
        if (!queued.isEmpty() && autoShowDialog && actions != null) {
            SwingUtilities.invokeLater(() -> showDialog(actions.createOwnerFrame()));
        }
        return queued;
    }

    public List<UsdbImportQueueJob> snapshot() {
        synchronized (jobs) {
            return jobs.stream()
                    .sorted(Comparator
                            .comparingInt((UsdbImportQueueJob job) -> job.isTerminal() ? 1 : 0)
                            .thenComparingLong(UsdbImportQueueJob::getLastUpdatedAt))
                    .toList();
        }
    }

    public boolean hasActiveJobFor(UsdbSongSummary summary) {
        return findActiveJobFor(summary) != null;
    }

    public boolean hasActiveSeparationJobFor(Path songFile) {
        Path normalized = normalizeSongFile(songFile);
        synchronized (jobs) {
            return jobs.stream()
                    .filter(job -> !job.isTerminal())
                    .filter(UsdbImportQueueJob::isSeparationJob)
                    .map(UsdbImportQueueJob::getSongFile)
                    .map(this::normalizeSongFile)
                    .anyMatch(normalized::equals);
        }
    }

    private Path normalizeSongFile(Path songFile) {
        return songFile == null ? Path.of("") : songFile.toAbsolutePath().normalize();
    }

    public UsdbImportQueueJob findActiveJobFor(UsdbSongSummary summary) {
        if (summary == null) {
            return null;
        }
        synchronized (jobs) {
            return jobs.stream()
                    .filter(job -> !job.isTerminal())
                    .filter(UsdbImportQueueJob::isImportJob)
                    .filter(job -> sameSong(job.getSummary(), summary))
                    .findFirst()
                    .orElse(null);
        }
    }

    public void cancelJob(UsdbImportQueueJob job, Component parent) {
        if (job == null || job.isTerminal() || job.isCancellationRequested()) {
            return;
        }
        job.setCancellationRequested(true);
        job.appendGeneral("Cancelling " + job.getDisplayName());
        job.appendDetail("Cancellation requested...");
        Future<?> importFuture = importTasks.get(job.getId());
        Future<?> separationFuture = separationTasks.get(job.getId());
        if (importFuture != null) {
            importFuture.cancel(true);
        }
        if (separationFuture != null) {
            separationFuture.cancel(true);
        }
        if (job.getState() == UsdbImportQueueJob.State.QUEUED
                || job.getState() == UsdbImportQueueJob.State.WAITING_SEPARATION) {
            updateState(job, UsdbImportQueueJob.State.CANCELED, "Cancelled " + job.getDisplayName(), "Queued job cancelled.");
        }
        if (job.isImportJob()) {
            maybeDeleteUnfinishedFolder(job, parent);
        }
        fireQueueChanged();
    }

    public void removeFinishedAndCancelledJobs() {
        synchronized (jobs) {
            jobs.removeIf(UsdbImportQueueJob::isRemovable);
        }
        fireQueueChanged();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void showDialog(Window owner) {
        if (dialog == null) {
            dialog = new UsdbImportQueueDialog(owner, this);
        }
        dialog.setVisible(true);
        dialog.toFront();
    }

    YassActions getActions() {
        return actions;
    }

    private boolean sameSong(UsdbSongSummary left, UsdbSongSummary right) {
        if (left == null || right == null) {
            return false;
        }
        return left.songId() == right.songId()
                || (StringUtils.equalsIgnoreCase(StringUtils.trimToEmpty(left.artist()), StringUtils.trimToEmpty(right.artist()))
                && StringUtils.equalsIgnoreCase(StringUtils.trimToEmpty(left.title()), StringUtils.trimToEmpty(right.title())));
    }

    private void runImport(UsdbImportQueueJob job) {
        try {
            updateState(job, UsdbImportQueueJob.State.IMPORTING, "Started import for " + job.getDisplayName(), "Preparing import...");
            UsdbSongImportService importService = new UsdbSongImportService(actions.getProperties(), actions.getUsdbClient());
            UsdbSongImportResult result = importService.importSong(actions.createOwnerFrame(),
                                                                  job.getSummary(),
                                                                  new JobProgressAdapter(job),
                                                                  job.getConflictChoice());
            if (result == null) {
                updateState(job, UsdbImportQueueJob.State.CANCELED, "Import cancelled for " + job.getDisplayName(), "Import cancelled.");
                return;
            }

            job.setSongFile(result.songFile());
            updateState(job, UsdbImportQueueJob.State.FINALIZING, "Finalizing " + job.getDisplayName(), "Refreshing library entry...");
            actions.refreshImportedSongInLibrary(result.songFile().toFile(), true);

            if (job.isSeparationJob()) {
                updateState(job,
                            UsdbImportQueueJob.State.WAITING_SEPARATION,
                            "Queued audio separation for " + job.getDisplayName(),
                            buildSeparationQueueStatus(job));
                Future<?> separationFuture = separationExecutor.submit(() -> runSeparation(job, result.songFile()));
                separationTasks.put(job.getId(), separationFuture);
            } else {
                updateState(job, UsdbImportQueueJob.State.DONE, "Finished " + job.getDisplayName(), "Import finished.");
            }
        } catch (CancellationException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            updateState(job, UsdbImportQueueJob.State.CANCELED, "Cancelled " + job.getDisplayName(), "Import cancelled.");
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "USDB queue import failed for " + job.getDisplayName(), ex);
            job.setErrorMessage(StringUtils.defaultIfBlank(ex.getMessage(), ex.toString()));
            updateState(job, UsdbImportQueueJob.State.FAILED, "Import failed for " + job.getDisplayName(), job.getErrorMessage());
        } finally {
            importTasks.remove(job.getId());
        }
    }

    private void runSeparation(UsdbImportQueueJob job, Path songFile) {
        try {
            updateState(job,
                        UsdbImportQueueJob.State.SEPARATING,
                        "Started audio separation for " + job.getDisplayName(),
                        "Audio separation started...");
            actions.runQuietLocalSeparationForSongFile(songFile.toString(), status -> {
                if (StringUtils.isNotBlank(status)) {
                    job.appendGeneral(status + " for " + job.getDisplayName());
                }
                job.appendDetail(status);
                fireQueueChanged();
            });
            actions.refreshImportedSongInLibrary(songFile.toFile(), true);
            String doneMessage = job.getMode() == UsdbImportQueueJob.Mode.IMPORT_AND_SEPARATE
                    ? "Import and audio separation finished."
                    : "Audio separation finished.";
            updateState(job, UsdbImportQueueJob.State.DONE, "Finished " + job.getDisplayName(), doneMessage);
        } catch (CancellationException ex) {
            updateState(job, UsdbImportQueueJob.State.CANCELED, "Cancelled " + job.getDisplayName(), "Audio separation cancelled.");
        } catch (Exception ex) {
            if (Thread.currentThread().isInterrupted() || job.isCancellationRequested()) {
                updateState(job, UsdbImportQueueJob.State.CANCELED, "Cancelled " + job.getDisplayName(), "Audio separation cancelled.");
                return;
            }
            LOGGER.log(Level.WARNING, "USDB queue separation failed for " + job.getDisplayName(), ex);
            job.setErrorMessage(StringUtils.defaultIfBlank(ex.getMessage(), ex.toString()));
            updateState(job, UsdbImportQueueJob.State.FAILED, "Audio separation failed for " + job.getDisplayName(), job.getErrorMessage());
        } finally {
            separationTasks.remove(job.getId());
        }
    }

    private String buildSeparationQueueStatus(UsdbImportQueueJob job) {
        synchronized (jobs) {
            List<UsdbImportQueueJob> separationJobs = jobs.stream()
                    .filter(UsdbImportQueueJob::isSeparationJob)
                    .filter(candidate -> candidate.getState() == UsdbImportQueueJob.State.WAITING_SEPARATION
                            || candidate.getState() == UsdbImportQueueJob.State.SEPARATING)
                    .toList();
            int total = separationJobs.size();
            int index = Math.max(1, separationJobs.indexOf(job) + 1);
            return "Audio-Separation. Waiting in Queue " + index + " of " + Math.max(index, total);
        }
    }

    private void updateState(UsdbImportQueueJob job,
                             UsdbImportQueueJob.State state,
                             String generalMessage,
                             String songMessage) {
        job.setState(state);
        job.appendGeneral(generalMessage);
        job.appendDetail(songMessage);
        fireQueueChanged();
    }

    private void fireQueueChanged() {
        SwingUtilities.invokeLater(() -> listeners.forEach(Listener::onQueueChanged));
    }

    private void maybeDeleteUnfinishedFolder(UsdbImportQueueJob job, Component parent) {
        Path songFile = job.getSongFile();
        if (songFile == null) {
            return;
        }
        Path folder = songFile.getParent();
        if (folder == null || !folder.toFile().exists()) {
            return;
        }
        int option = JOptionPane.showConfirmDialog(parent,
                "Delete unfinished folder?\n" + folder,
                "Cancel Song",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (option != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            deleteDirectory(folder);
            job.appendDetail("Deleted unfinished folder: " + folder.getFileName());
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Could not delete unfinished USDB import folder " + folder, ex);
            job.appendDetail("Could not delete unfinished folder: " + ex.getMessage());
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (directory == null || !directory.toFile().exists()) {
            return;
        }
        try (var paths = java.nio.file.Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    java.nio.file.Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw ex;
        }
    }

    private final class JobProgressAdapter implements UsdbImportProgressListener {
        private final UsdbImportQueueJob job;

        private JobProgressAdapter(UsdbImportQueueJob job) {
            this.job = job;
        }

        @Override
        public void onGeneralStatus(String message) {
            job.appendGeneral(message + " for " + job.getDisplayName());
            fireQueueChanged();
        }

        @Override
        public void onSongStatus(String message) {
            job.appendDetail(message);
            fireQueueChanged();
        }

        @Override
        public void onDetailLog(String message) {
            job.appendDetail(message);
            fireQueueChanged();
        }
    }
}
