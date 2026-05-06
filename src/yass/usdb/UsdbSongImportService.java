package yass.usdb;

import com.jfposton.ytdlp.YtDlp;
import com.jfposton.ytdlp.YtDlpCallback;
import com.jfposton.ytdlp.YtDlpException;
import com.jfposton.ytdlp.YtDlpRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import yass.UsdbFile;
import yass.UsdbSyncerMetaFile;
import yass.YassProperties;
import yass.YassSong;
import yass.YassTable;
import yass.YassUtils;
import yass.YtDlpSupport;
import yass.extras.UsdbSyncerMetaTagCreator;
import yass.options.YtDlpPanel;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UsdbSongImportService {
    private static final String YOUTUBE_WATCH_PREFIX = "https://www.youtube.com/watch?v=";
    private static final int IMAGE_CONNECT_TIMEOUT_MS = 15_000;
    private static final int IMAGE_READ_TIMEOUT_MS = 30_000;
    private static final int IMAGE_FUTURE_TIMEOUT_SECONDS = 60;
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private final YassProperties properties;
    private final UsdbClient usdbClient;

    public UsdbSongImportService(YassProperties properties, UsdbClient usdbClient) {
        this.properties = properties;
        this.usdbClient = usdbClient;
    }

    public UsdbSongImportResult importSong(Window owner, UsdbSongSummary summary) throws IOException, InterruptedException {
        return importSong(owner, summary, (UsdbImportProgressListener) null);
    }

    public UsdbSongImportResult importSong(Window owner,
                                           UsdbSongSummary summary,
                                           Consumer<String> statusConsumer) throws IOException, InterruptedException {
        return importSong(owner, summary, toListener(statusConsumer), null);
    }

    public UsdbSongImportResult importSong(Window owner,
                                           UsdbSongSummary summary,
                                           UsdbImportProgressListener progressListener) throws IOException, InterruptedException {
        return importSong(owner, summary, progressListener, null);
    }

    public UsdbSongImportResult importSong(Window owner,
                                           UsdbSongSummary summary,
                                           UsdbImportProgressListener progressListener,
                                           UsdbImportConflictChoice preferredConflictChoice) throws IOException, InterruptedException {
        reportSong(progressListener, "Downloading TXT from USDB...");
        Path songsRoot = resolveSongsRoot();
        String importedText = usdbClient.downloadSongText(summary.songId());
        reportSong(progressListener, "Parsing song data...");
        YassTable importedTable = loadImportedTable(importedText);
        String artist = StringUtils.defaultIfBlank(importedTable.getArtist(), summary.artist());
        String title = StringUtils.defaultIfBlank(importedTable.getTitle(), summary.title());
        String baseFolderName = buildFolderName(artist, title);
        Path existingSongFile = songsRoot.resolve(baseFolderName).resolve(baseFolderName + ".txt");

        String finalFolderName = baseFolderName;
        if (Files.exists(existingSongFile)) {
            String existingText = readTextSafely(existingSongFile);
            UsdbImportConflictChoice choice = preferredConflictChoice != null
                    ? preferredConflictChoice
                    : UsdbImportConflictDialog.showDialog(owner, existingText, importedText, artist + " - " + title);
            if (choice == UsdbImportConflictChoice.CANCEL) {
                return null;
            }
            if (choice == UsdbImportConflictChoice.CREATE_NEW_VERSION) {
                finalFolderName = nextAvailableFolderName(songsRoot, artist, title);
            }
        }

        Path finalSongDirectory = songsRoot.resolve(finalFolderName);
        Path stagingDirectory = Files.createTempDirectory(songsRoot, finalFolderName + "-import-");
        boolean moveCompleted = false;
        try {
            reportSong(progressListener, "Importing media files...");
            UsdbMetaTagParser.UsdbParsedMetaTags metaTags = prepareImportedSong(importedTable,
                                                                                stagingDirectory,
                                                                                finalFolderName,
                                                                                progressListener);
            Path stagingSongFile = stagingDirectory.resolve(finalFolderName + ".txt");
            if (!importedTable.storeFile(stagingSongFile.toString())) {
                throw new IOException("USDB import could not be stored.");
            }
            reportSong(progressListener, "Writing song files...");
            writeSyncerMetaFileIfConfigured(stagingDirectory, stagingSongFile, importedTable, summary.songId(), metaTags);
            replaceDirectory(finalSongDirectory, stagingDirectory);
            moveCompleted = true;
            reportSong(progressListener, "Import completed.");
            return new UsdbSongImportResult(finalSongDirectory, finalSongDirectory.resolve(finalFolderName + ".txt"));
        } finally {
            if (!moveCompleted) {
                deleteDirectory(stagingDirectory);
            }
        }
    }

    private Path resolveSongsRoot() throws IOException {
        String songsDir = properties.getProperty("song-directory");
        if (StringUtils.isBlank(songsDir)) {
            throw new IOException("No song directory configured.");
        }
        Path path = Path.of(songsDir);
        if (!Files.isDirectory(path)) {
            throw new IOException("Configured song directory does not exist: " + songsDir);
        }
        return path;
    }

    private YassTable loadImportedTable(String importedText) throws IOException {
        YassTable table = new YassTable();
        table.init(properties);
        table.removeAllRows();
        if (!table.setText(importedText)) {
            throw new IOException("USDB song text could not be parsed.");
        }
        return table;
    }

    private UsdbMetaTagParser.UsdbParsedMetaTags prepareImportedSong(YassTable table,
                                                                     Path targetDirectory,
                                                                     String finalFolderName,
                                                                     UsdbImportProgressListener progressListener) throws IOException, InterruptedException {
        UsdbMetaTagParser.UsdbParsedMetaTags metaTags = UsdbMetaTagParser.parse(table.getVideo());
        table.setVideo("");

        ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "usdb-import-stage");
            thread.setDaemon(true);
            return thread;
        });
        try {
            CompletableFuture<MediaImport> mediaFuture = startMediaImport(metaTags,
                                                                          targetDirectory,
                                                                          finalFolderName,
                                                                          progressListener,
                                                                          executor);
            CompletableFuture<String> coverFuture = startCoverImport(metaTags,
                                                                     targetDirectory,
                                                                     finalFolderName,
                                                                     progressListener,
                                                                     executor);
            CompletableFuture<String> backgroundFuture = startBackgroundImport(metaTags,
                                                                               targetDirectory,
                                                                               finalFolderName,
                                                                               progressListener,
                                                                               executor);

            MediaImport media = awaitFuture(mediaFuture);
            if (media != null) {
                table.setVideo(StringUtils.defaultString(media.videoFileName()));
                table.setMP3(StringUtils.defaultString(media.audioFileName()));
                table.setAudio(StringUtils.defaultString(media.audioFileName()));
                reportGeneral(progressListener, "Finished media download");
            }

            String coverFileName = awaitOptionalImageFuture(coverFuture, "cover", progressListener);
            if (StringUtils.isNotBlank(coverFileName)) {
                table.setCover(coverFileName);
            }
            reportGeneral(progressListener, StringUtils.isNotBlank(coverFileName)
                    ? "Finished cover download"
                    : "Skipped cover download");

            String backgroundFileName = awaitOptionalImageFuture(backgroundFuture, "background", progressListener);
            if (StringUtils.isNotBlank(backgroundFileName)) {
                table.setBackground(backgroundFileName);
            }
            reportGeneral(progressListener, StringUtils.isNotBlank(backgroundFileName)
                    ? "Finished background download"
                    : "Skipped background download");
        } finally {
            executor.shutdownNow();
        }

        applyStructuredTags(table, metaTags.values());
        return metaTags;
    }

    private CompletableFuture<MediaImport> startMediaImport(UsdbMetaTagParser.UsdbParsedMetaTags metaTags,
                                                            Path targetDirectory,
                                                            String finalFolderName,
                                                            UsdbImportProgressListener progressListener,
                                                            ExecutorService executor) {
        String videoSource = normalizeMediaSource(metaTags.get("v"));
        String audioSource = normalizeMediaSource(metaTags.get("a"));
        if (StringUtils.isNotBlank(videoSource)) {
            reportGeneral(progressListener, "Started video download");
            reportSong(progressListener, "Downloading video and audio...");
            return startVideoAndAudioImport(videoSource, targetDirectory, finalFolderName, progressListener, executor);
        }
        if (StringUtils.isNotBlank(audioSource)) {
            reportGeneral(progressListener, "Started audio download");
            reportSong(progressListener, "Downloading audio...");
            return CompletableFuture.supplyAsync(() -> {
                try {
                    String audioFileName = downloadAudio(audioSource, targetDirectory, finalFolderName, progressListener);
                    return new MediaImport(null, audioFileName);
                } catch (IOException ex) {
                    throw new StageImportRuntimeException(ex);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new StageImportRuntimeException(ex);
                }
            }, executor);
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<MediaImport> startVideoAndAudioImport(String source,
                                                                    Path targetDirectory,
                                                                    String baseName,
                                                                    UsdbImportProgressListener progressListener,
                                                                    ExecutorService executor) {
        String normalizedSource = normalizeMediaSource(source);
        CompletableFuture<String> audioFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return downloadAudio(normalizedSource, targetDirectory, baseName, progressListener);
            } catch (IOException ex) {
                throw new StageImportRuntimeException(ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new StageImportRuntimeException(ex);
            }
        }, executor);
        CompletableFuture<String> videoFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return downloadVideo(normalizedSource, targetDirectory, baseName, progressListener);
            } catch (IOException ex) {
                throw new StageImportRuntimeException(ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new StageImportRuntimeException(ex);
            }
        }, executor);
        return audioFuture.thenCombine(videoFuture, (audioFileName, videoFileName) -> new MediaImport(videoFileName, audioFileName));
    }

    private CompletableFuture<String> startCoverImport(UsdbMetaTagParser.UsdbParsedMetaTags metaTags,
                                                       Path targetDirectory,
                                                       String finalFolderName,
                                                       UsdbImportProgressListener progressListener,
                                                       ExecutorService executor) {
        String coverSource = metaTags.get("co");
        if (StringUtils.isBlank(coverSource)) {
            return CompletableFuture.completedFuture(null);
        }
        reportGeneral(progressListener, "Started cover download");
        reportSong(progressListener, "Downloading cover image...");
        return CompletableFuture.supplyAsync(() -> {
            try {
                return downloadImage(metaTags, targetDirectory, finalFolderName + " [CO]", coverSource, true);
            } catch (IOException ex) {
                throw new StageImportRuntimeException(ex);
            }
        }, executor);
    }

    private CompletableFuture<String> startBackgroundImport(UsdbMetaTagParser.UsdbParsedMetaTags metaTags,
                                                            Path targetDirectory,
                                                            String finalFolderName,
                                                            UsdbImportProgressListener progressListener,
                                                            ExecutorService executor) {
        String backgroundSource = metaTags.get("bg");
        if (StringUtils.isBlank(backgroundSource)) {
            return CompletableFuture.completedFuture(null);
        }
        reportGeneral(progressListener, "Started background download");
        reportSong(progressListener, "Downloading background image...");
        return CompletableFuture.supplyAsync(() -> {
            try {
                return downloadImage(metaTags, targetDirectory, finalFolderName + " [BG]", backgroundSource, false);
            } catch (IOException ex) {
                throw new StageImportRuntimeException(ex);
            }
        }, executor);
    }

    private void writeSyncerMetaFileIfConfigured(Path targetDirectory,
                                                 Path txtFile,
                                                 YassTable table,
                                                 int songId,
                                                 UsdbMetaTagParser.UsdbParsedMetaTags metaTags) throws IOException {
        String syncerPath = StringUtils.trimToEmpty(properties.getProperty(UsdbSyncerBridge.PROPERTY_SYNCER_PATH));
        if (StringUtils.isBlank(syncerPath) || !Files.exists(Path.of(syncerPath))) {
            return;
        }
        UsdbSyncerMetaFile file = new UsdbSyncerMetaFile();
        file.setSongId(songId);
        file.setMetaTags(StringUtils.trimToEmpty(metaTags.originalTagLine()));
        file.setPinned(false);
        file.setVersion(1);

        file.setTxt(createResource(txtFile, "https://usdb.animux.de/?link=gettxt&id=" + songId));
        file.setAudio(createResource(resolvePath(targetDirectory, table.getMP3()),
                                     normalizeMediaSource(StringUtils.defaultIfBlank(metaTags.get("a"), metaTags.get("v")))));
        file.setVideo(createResource(resolvePath(targetDirectory, table.getVideo()),
                                     normalizeMediaSource(metaTags.get("v"))));
        file.setCover(createResource(resolvePath(targetDirectory, table.getCover()),
                                     UsdbSyncerMetaTagCreator.toSyncerImageLink(metaTags.get("co"))));
        file.setBackground(createResource(resolvePath(targetDirectory, table.getBackgroundTag()),
                                          UsdbSyncerMetaTagCreator.toSyncerImageLink(metaTags.get("bg"))));

        Path metaFile = targetDirectory.resolve(UUID.randomUUID().toString().replace("-", "") + ".usdb");
        Files.writeString(metaFile, GSON.toJson(file), Charset.forName("UTF-8"));
    }

    private Path resolvePath(Path folder, String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return null;
        }
        Path path = folder.resolve(fileName);
        return Files.exists(path) ? path : null;
    }

    private UsdbFile createResource(Path file, String resource) {
        if (file == null || StringUtils.isBlank(resource)) {
            return null;
        }
        UsdbFile usdbFile = new UsdbFile();
        usdbFile.setFname(file.getFileName().toString());
        usdbFile.setResource(resource);
        usdbFile.setMtime(BigDecimal.valueOf(file.toFile().lastModified() * 1000L));
        return usdbFile;
    }

    private void applyStructuredTags(YassTable table, Map<String, String> metaTags) {
        applyDouble(metaTags.get("preview"), table::setPreviewStart);
        applyInteger(metaTags.get("medley-start"), table::setMedleyStartBeat);
        applyInteger(metaTags.get("medley-end"), table::setMedleyEndBeat);
        if (StringUtils.isNotBlank(metaTags.get("p1"))) {
            table.setDuetSingerName(0, metaTags.get("p1"));
        }
        if (StringUtils.isNotBlank(metaTags.get("p2"))) {
            table.setDuetSingerName(1, metaTags.get("p2"));
        }
        if (StringUtils.isNotBlank(metaTags.get("tags"))) {
            table.setTags(metaTags.get("tags"));
        }
    }

    private void applyDouble(String value, java.util.function.DoubleConsumer consumer) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        try {
            consumer.accept(Double.parseDouble(value.replace(',', '.')));
        } catch (NumberFormatException ignored) {
        }
    }

    private void applyInteger(String value, java.util.function.IntConsumer consumer) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        try {
            consumer.accept(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
        }
    }

    private String downloadImage(UsdbMetaTagParser.UsdbParsedMetaTags metaTags,
                                 Path targetDirectory,
                                 String targetBaseName,
                                 String imageSource,
                                 boolean cover) throws IOException {
        String imageUrl = normalizeImageUrl(imageSource);
        if (StringUtils.isBlank(imageUrl)) {
            LOGGER.info("Skipping image import because no absolute URL could be derived from source: "
                    + StringUtils.trimToEmpty(imageSource));
            return null;
        }
        try {
            String downloadableUrl = imageUrl.replace("images.fanart.tv", "assets.fanart.tv");
            LOGGER.info("Downloading USDB image from " + downloadableUrl);
            BufferedImage image = readImageWithTimeout(downloadableUrl);
            if (image == null) {
                LOGGER.info("Skipping image import because image data could not be read: " + imageUrl);
                return null;
            }
            int[] crop = parseCrop(metaTags, cover ? "co-crop" : "bg-crop");
            if (crop != null) {
                image = image.getSubimage(crop[0], crop[1], crop[2], crop[3]);
            }
            if (cover) {
                image = applyRotation(image, metaTags.get("co-rotate"));
                image = applyContrast(image, metaTags.get("co-contrast"));
                int resize = parseInteger(metaTags.get("co-resize"), properties.getIntProperty("cover-max-width"));
                if (resize > 0 && image.getWidth() != resize) {
                    image = YassUtils.getScaledInstance(image, resize, resize);
                }
            } else {
                int width = parseInteger(metaTags.get("bg-resize-width"), 0);
                int height = parseInteger(metaTags.get("bg-resize-height"), 0);
                if (width > 0 && height > 0 && (image.getWidth() != width || image.getHeight() != height)) {
                    image = YassUtils.getScaledInstance(image, width, height);
                }
            }
            Path imageFile = targetDirectory.resolve(targetBaseName + ".jpg");
            BufferedImage output = ensureRgb(image);
            ImageIO.write(output, "jpeg", imageFile.toFile());
            LOGGER.info("Downloaded USDB image to " + imageFile);
            return imageFile.getFileName().toString();
        } catch (URISyntaxException | IllegalArgumentException ex) {
            LOGGER.log(Level.INFO, "Skipping image import because URL is invalid: " + imageUrl, ex);
            return null;
        } catch (Exception ex) {
            LOGGER.log(Level.INFO, "Skipping image import because download failed: " + imageUrl, ex);
            return null;
        } catch (Throwable throwable) {
            LOGGER.log(Level.INFO, "Skipping image import because an unexpected error occurred: " + imageUrl, throwable);
            return null;
        }
    }

    private BufferedImage readImageWithTimeout(String imageUrl) throws IOException, URISyntaxException {
        byte[] data = fetchImageBytes(imageUrl);
        if (data.length == 0) {
            return null;
        }
        try (InputStream inputStream = new java.io.ByteArrayInputStream(data)) {
            return ImageIO.read(inputStream);
        }
    }

    private byte[] fetchImageBytes(String imageUrl) throws IOException, URISyntaxException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URI(imageUrl).toURL().openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(IMAGE_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(IMAGE_READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36");
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("Referer", "https://usdb.animux.de/");
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) {
                throw new IOException("Empty response from " + imageUrl + " (HTTP " + code + ")");
            }
            try (stream) {
                byte[] data = stream.readAllBytes();
                if (code >= 400) {
                    throw new IOException("HTTP " + code + " while loading image " + imageUrl);
                }
                return data;
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String normalizeImageUrl(String sourceValue) {
        String normalizedSource = UsdbSyncerMetaTagCreator.toSyncerImageLink(sourceValue);
        String trimmed = StringUtils.trimToEmpty(normalizedSource);
        if (StringUtils.isBlank(trimmed)) {
            return null;
        }
        // Match usdb_syncer semantics:
        // - full URL stays URL
        // - value with slash becomes https://<value>
        // - plain short value/file is interpreted as fanart id/path.
        if (!trimmed.contains("://") && !trimmed.startsWith("//")) {
            if (trimmed.contains("/")) {
                trimmed = "https://" + trimmed;
            } else {
                trimmed = "https://assets.fanart.tv/fanart/" + trimmed;
            }
        }
        if (trimmed.startsWith("//")) {
            trimmed = "https:" + trimmed;
        }
        return validateAbsoluteImageUrl(trimmed);
    }

    private String validateAbsoluteImageUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = StringUtils.lowerCase(StringUtils.trimToEmpty(uri.getScheme()));
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            String host = StringUtils.trimToEmpty(uri.getHost());
            if (StringUtils.isBlank(host) || !host.contains(".")) {
                return null;
            }
            return uri.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private int[] parseCrop(UsdbMetaTagParser.UsdbParsedMetaTags metaTags, String prefix) {
        int left = parseInteger(metaTags.get(prefix + "-left"), 0);
        int top = parseInteger(metaTags.get(prefix + "-top"), 0);
        int width = parseInteger(metaTags.get(prefix + "-width"), 0);
        int height = parseInteger(metaTags.get(prefix + "-height"), 0);
        if (width <= 0 || height <= 0) {
            return null;
        }
        return new int[]{left, top, width, height};
    }

    private BufferedImage applyRotation(BufferedImage image, String rotationValue) {
        if (StringUtils.isBlank(rotationValue)) {
            return image;
        }
        double degrees;
        try {
            degrees = Double.parseDouble(rotationValue.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return image;
        }
        if (Math.abs(degrees) < 0.001d) {
            return image;
        }
        double radians = Math.toRadians(degrees);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));
        int width = image.getWidth();
        int height = image.getHeight();
        int newWidth = (int) Math.floor(width * cos + height * sin);
        int newHeight = (int) Math.floor(height * cos + width * sin);
        BufferedImage rotated = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = rotated.createGraphics();
        try {
            AffineTransform transform = new AffineTransform();
            transform.translate((double) (newWidth - width) / 2, (double) (newHeight - height) / 2);
            transform.rotate(radians, width / 2.0, height / 2.0);
            g2d.setTransform(transform);
            g2d.drawImage(image, 0, 0, null);
        } finally {
            g2d.dispose();
        }
        return rotated;
    }

    private BufferedImage applyContrast(BufferedImage image, String contrastValue) {
        if (StringUtils.isBlank(contrastValue) || "auto".equalsIgnoreCase(contrastValue)) {
            return image;
        }
        float contrast;
        try {
            contrast = Float.parseFloat(contrastValue.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return image;
        }
        RescaleOp op = new RescaleOp(new float[]{contrast, contrast, contrast, 1f},
                                     new float[]{0f, 0f, 0f, 0f},
                                     null);
        return op.filter(image, null);
    }

    private BufferedImage ensureRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgbImage.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgbImage;
    }

    private <T> T awaitFuture(CompletableFuture<T> future) throws IOException, InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof StageImportRuntimeException wrapped && wrapped.getCause() != null) {
                cause = wrapped.getCause();
            }
            if (cause instanceof InterruptedException interruptedException) {
                throw interruptedException;
            }
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException(StringUtils.defaultIfBlank(cause != null ? cause.getMessage() : ex.getMessage(),
                                                             ex.toString()),
                                  cause != null ? cause : ex);
        }
    }

    private String awaitOptionalImageFuture(CompletableFuture<String> future,
                                            String imageType,
                                            UsdbImportProgressListener progressListener) throws InterruptedException {
        try {
            return future.get(IMAGE_FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            String message = "Skipping " + imageType + " download because it timed out.";
            LOGGER.log(Level.INFO, message, ex);
            logDetail(progressListener, message);
            return null;
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof StageImportRuntimeException wrapped && wrapped.getCause() != null) {
                cause = wrapped.getCause();
            }
            String message = "Skipping " + imageType + " download because it failed: "
                    + StringUtils.defaultIfBlank(cause != null ? cause.getMessage() : ex.getMessage(), ex.toString());
            LOGGER.log(Level.INFO, message, cause != null ? cause : ex);
            logDetail(progressListener, message);
            return null;
        }
    }

    private String downloadAudio(String source,
                                 Path targetDirectory,
                                 String baseName,
                                 UsdbImportProgressListener progressListener) throws IOException, InterruptedException {
        long started = System.currentTimeMillis();
        YtDlpRequest request = new YtDlpRequest(source);
        request.setDirectory(targetDirectory.toString());
        request.setOption("output", baseName + ".%(ext)s");
        request.setOption("format", "bestaudio");
        YtDlpSupport.applyAudioExtractionOptions(request, properties);
        YtDlpSupport.applyCommonOptions(request, properties);
        executeYtDlp(request, "USDB audio import failed.", progressListener);
        return detectNewestMediaFile(targetDirectory, baseName, started, true);
    }

    private String downloadVideo(String source,
                                 Path targetDirectory,
                                 String baseName,
                                 UsdbImportProgressListener progressListener) throws IOException, InterruptedException {
        long started = System.currentTimeMillis();
        YtDlpRequest request = new YtDlpRequest(source);
        request.setDirectory(targetDirectory.toString());
        request.setOption("output", baseName + ".%(ext)s");
        request.setOption("format", YtDlpSupport.buildVideoOnlyFormatString(properties, true));
        request.setOption("no-audio");
        YtDlpSupport.applyCommonOptions(request, properties);
        executeYtDlp(request, "USDB video import failed.", progressListener);
        return detectNewestMediaFile(targetDirectory, baseName, started, false);
    }

    private void executeYtDlp(YtDlpRequest request,
                              String errorPrefix,
                              UsdbImportProgressListener progressListener) throws IOException, InterruptedException {
        YtDlpSupport.ensureExecutableAvailable(properties);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            YtDlp.executeAsync(request, new YtDlpCallback() {
                @Override
                public void onProcessStarted(Process process, YtDlpRequest request) {
                }

                @Override
                public void onProcessFinished(int exitCode, String out, String err) {
                    if (exitCode > 0) {
                        String message = StringUtils.defaultIfBlank(err, out);
                        failure.set(new IOException(errorPrefix + " " + StringUtils.defaultIfBlank(message, "yt-dlp failed.")));
                    }
                    latch.countDown();
                }

                @Override
                public void onOutput(String line) {
                    if (StringUtils.isBlank(line)) {
                        return;
                    }
                    logDetail(progressListener, line);
                }

                @Override
                public void onProgressUpdate(float progress, long etaInSeconds) {
                }
            });
            latch.await();
        } catch (YtDlpException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof InterruptedException interruptedException) {
                throw interruptedException;
            }
            LOGGER.log(Level.WARNING, errorPrefix + " " + StringUtils.defaultIfBlank(ex.getMessage(), ex.toString()), ex);
            throw new IOException(errorPrefix + " " + StringUtils.defaultIfBlank(ex.getMessage(), ex.toString()), ex);
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof InterruptedException interruptedException) {
                throw interruptedException;
            }
            LOGGER.log(Level.WARNING, errorPrefix + " " + StringUtils.defaultIfBlank(ex.getMessage(), ex.toString()), ex);
            throw new IOException(errorPrefix + " " + StringUtils.defaultIfBlank(ex.getMessage(), ex.toString()), ex);
        }
        Throwable throwable = failure.get();
        if (throwable instanceof InterruptedException interruptedException) {
            throw interruptedException;
        }
        if (throwable != null) {
            LOGGER.log(Level.WARNING, throwable.getMessage(), throwable);
            throw throwable instanceof IOException ioException ? ioException : new IOException(throwable.getMessage(), throwable);
        }
    }

    private UsdbImportProgressListener toListener(Consumer<String> statusConsumer) {
        if (statusConsumer == null) {
            return null;
        }
        return new UsdbImportProgressListener() {
            @Override
            public void onSongStatus(String message) {
                statusConsumer.accept(message);
            }

            @Override
            public void onGeneralStatus(String message) {
                statusConsumer.accept(message);
            }
        };
    }

    private void reportGeneral(UsdbImportProgressListener progressListener, String message) {
        if (progressListener != null && StringUtils.isNotBlank(message)) {
            progressListener.onGeneralStatus(message);
        }
    }

    private void reportSong(UsdbImportProgressListener progressListener, String message) {
        if (progressListener != null && StringUtils.isNotBlank(message)) {
            progressListener.onSongStatus(message);
        }
    }

    private void logDetail(UsdbImportProgressListener progressListener, String message) {
        if (progressListener != null && StringUtils.isNotBlank(message)) {
            progressListener.onDetailLog(message);
        }
    }

    private String detectNewestMediaFile(Path directory, String baseName, long started, boolean audio) throws IOException {
        String candidate;
        try (var files = Files.list(directory)) {
            candidate = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(baseName + "."))
                    .filter(path -> !path.getFileName().toString().endsWith(".part"))
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis() >= started - 2000;
                        } catch (IOException ex) {
                            return false;
                        }
                    })
                    .filter(path -> isAudio(path) == audio)
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                    .map(path -> path.getFileName().toString())
                    .orElse(null);
        }
        if (candidate == null) {
            throw new IOException("Imported media file could not be detected.");
        }
        return candidate;
    }

    private boolean isAudio(Path path) {
        String extension = FilenameUtils.getExtension(path.getFileName().toString()).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "mp3", "aac", "ogg", "flac", "m4a", "wav", "opus" -> true;
            default -> false;
        };
    }

    private String normalizeMediaSource(String source) {
        String trimmed = StringUtils.trimToEmpty(source);
        if (trimmed.matches("[A-Za-z0-9_-]{11}")) {
            return YOUTUBE_WATCH_PREFIX + trimmed;
        }
        if (trimmed.startsWith("v=") && trimmed.length() > 2) {
            return YOUTUBE_WATCH_PREFIX + trimmed.substring(2);
        }
        return trimmed;
    }

    private String buildFolderName(String artist, String title) {
        return YassSong.toFilename(StringUtils.defaultIfBlank(artist, "UnknownArtist")
                + " - " + StringUtils.defaultIfBlank(title, "UnknownTitle"));
    }

    private String nextAvailableFolderName(Path songsRoot, String artist, String title) {
        String baseName = buildFolderName(artist, title);
        int version = 2;
        while (true) {
            String candidate = YassSong.toFilename(artist + " - " + title + " (" + version + ")");
            Path candidateTxt = songsRoot.resolve(candidate).resolve(candidate + ".txt");
            if (!Files.exists(candidateTxt)) {
                return candidate;
            }
            version++;
        }
    }

    private String readTextSafely(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return new String(bytes, Charset.defaultCharset());
    }

    private void replaceDirectory(Path targetDirectory, Path stagingDirectory) throws IOException {
        if (Files.exists(targetDirectory)) {
            deleteDirectory(targetDirectory);
        }
        Files.move(stagingDirectory, targetDirectory, StandardCopyOption.REPLACE_EXISTING);
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
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

    private int parseInteger(String value, int defaultValue) {
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private record MediaImport(String videoFileName, String audioFileName) {
    }

    private static final class StageImportRuntimeException extends RuntimeException {
        private StageImportRuntimeException(Throwable cause) {
            super(cause);
        }
    }
}
