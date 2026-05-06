package yass.usdb;

import org.apache.commons.lang3.StringUtils;
import yass.YassSearchNormalizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UsdbSongAddService {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final DateTimeFormatter DEBUG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern VERIFY_ROW_PATTERN = Pattern.compile(
            "<tr\\s+class=['\"]list_tr\\d+['\"][^>]*onclick=['\"]show_detail\\((\\d+)\\)['\"][^>]*>\\s*"
                    + "<td>(.*?)</td>\\s*<td>(.*?)</td>\\s*<td>(.*?)</td>\\s*</tr>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern VERIFY_SUBMIT_PATTERN = Pattern.compile(
            "window\\.location\\.href='\\?link=submit&id=(\\d+)'",
            Pattern.CASE_INSENSITIVE);

    private final UsdbSessionService sessionService;

    public UsdbSongAddService(UsdbSessionService sessionService) {
        this.sessionService = sessionService;
    }

    public AddSongResult submitSong(LocalSongData song) throws IOException, InterruptedException {
        sessionService.resetSessionInfoRetryLimit();
        String logicalTxt = Files.readString(song.txtFile(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String submittedTxt = logicalTxt.replace("\n", "\r\n");
        byte[] txtBytes = submittedTxt.getBytes(StandardCharsets.UTF_8);
        String response = sessionService.postMultipartFile("index.php",
                Map.of("link", "saver"),
                "file",
                song.txtFile().getFileName().toString(),
                "text/plain; charset=UTF-8",
                txtBytes);
        LOGGER.info("USDB add-song upload payload artist='" + song.artist()
                + "' title='" + song.title()
                + "' txtLength=" + submittedTxt.length()
                + " byteLength=" + txtBytes.length);
        LOGGER.info("USDB add-song saver response snippet: "
                + StringUtils.abbreviate(StringUtils.normalizeSpace(response), 500));

        String verifyHtml = "";
        VerifyMatch verifyMatch = VerifyMatch.NONE;
        for (int attempt = 1; attempt <= 5; attempt++) {
            verifyHtml = sessionService.get("index.php", Map.of("link", "verify"));
            verifyMatch = findVerifyMatch(verifyHtml, song.artist(), song.title());
            LOGGER.info("USDB add-song verify attempt " + attempt
                    + " artist='" + song.artist()
                    + "' title='" + song.title()
                    + "' verifySongId=" + verifyMatch.songId());
            if (verifyMatch.songId() > 0) {
                break;
            }
            if (attempt < 5) {
                Thread.sleep(1000L);
            }
        }
        String detailHtml = verifyMatch.songId() > 0
                ? sessionService.get("index.php", Map.of("link", "detailz", "id", String.valueOf(verifyMatch.songId())))
                : "";
        int submitSongId = extractSubmitSongId(detailHtml);
        LOGGER.info("USDB add-song verify lookup artist='" + song.artist()
                + "' title='" + song.title() + "' verifySongId=" + verifyMatch.songId()
                + " submitSongId=" + submitSongId);
        AddSongResult result = new AddSongResult(response, verifyHtml, verifyMatch.songId(), detailHtml, submitSongId);
        if (!result.confirmedOnVerify()) {
            Path debugDir = writeDebugFiles(song, logicalTxt, submittedTxt, response, verifyHtml);
            throw new IOException("USDB upload could not be confirmed on the verify page. Debug: " + debugDir);
        }
        return result;
    }

    public int findPendingSongId(String artist, String title) throws IOException, InterruptedException {
        sessionService.resetSessionInfoRetryLimit();
        String verifyHtml = sessionService.get("index.php", Map.of("link", "verify"));
        VerifyMatch verifyMatch = findVerifyMatch(verifyHtml, artist, title);
        return verifyMatch.songId();
    }

    private VerifyMatch findVerifyMatch(String html, String artist, String title) {
        String normalizedArtist = YassSearchNormalizer.normalizeForSearch(artist);
        String normalizedTitle = YassSearchNormalizer.normalizeForSearch(title);
        Matcher matcher = VERIFY_ROW_PATTERN.matcher(StringUtils.defaultString(html));
        while (matcher.find()) {
            int songId = Integer.parseInt(matcher.group(1));
            String rowArtist = normalizeCell(matcher.group(2));
            String rowTitle = normalizeCell(matcher.group(3));
            if (!StringUtils.equals(rowArtist, normalizedArtist) || !StringUtils.equals(rowTitle, normalizedTitle)) {
                continue;
            }
            return new VerifyMatch(songId, rowArtist, rowTitle);
        }
        return VerifyMatch.NONE;
    }

    private int extractSubmitSongId(String detailHtml) {
        Matcher matcher = VERIFY_SUBMIT_PATTERN.matcher(StringUtils.defaultString(detailHtml));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private String normalizeCell(String htmlCell) {
        String withoutTags = StringUtils.defaultString(htmlCell).replaceAll("<[^>]+>", " ");
        return YassSearchNormalizer.normalizeForSearch(UsdbSessionService.decodeHtml(withoutTags));
    }

    private Path writeDebugFiles(LocalSongData song,
                                 String logicalTxt,
                                 String submittedTxt,
                                 String saverHtml,
                                 String verifyHtml) {
        try {
            Path debugDir = Path.of(System.getProperty("user.home"), ".yass", "logs");
            Files.createDirectories(debugDir);
            String baseName = "usdb-add-song-" + DEBUG_TIMESTAMP.format(LocalDateTime.now());
            Files.writeString(debugDir.resolve(baseName + "-logical.txt"), StringUtils.defaultString(logicalTxt), StandardCharsets.UTF_8);
            Files.writeString(debugDir.resolve(baseName + "-submitted.txt"), StringUtils.defaultString(submittedTxt), StandardCharsets.UTF_8);
            Files.writeString(debugDir.resolve(baseName + "-saver.html"), StringUtils.defaultString(saverHtml), StandardCharsets.UTF_8);
            Files.writeString(debugDir.resolve(baseName + "-verify.html"), StringUtils.defaultString(verifyHtml), StandardCharsets.UTF_8);
            Files.writeString(debugDir.resolve(baseName + "-meta.txt"),
                    "artist=" + StringUtils.defaultString(song.artist()) + System.lineSeparator()
                            + "title=" + StringUtils.defaultString(song.title()) + System.lineSeparator()
                            + "txtFile=" + song.txtFile() + System.lineSeparator()
                            + "logicalLength=" + StringUtils.length(logicalTxt) + System.lineSeparator()
                            + "submittedLength=" + StringUtils.length(submittedTxt) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            LOGGER.info("USDB add-song debug files written to " + debugDir);
            return debugDir;
        } catch (Exception ex) {
            LOGGER.info("USDB add-song debug files could not be written: " + ex.getMessage());
            return Path.of(System.getProperty("user.home"), ".yass", "logs");
        }
    }

    private record VerifyMatch(int songId, String artist, String title) {
        private static final VerifyMatch NONE = new VerifyMatch(0, "", "");
    }

    public record LocalSongData(String artist,
                                String title,
                                Path txtFile) {
    }

    public record AddSongResult(String responseHtml,
                                String verifyHtml,
                                int verifySongId,
                                String detailHtml,
                                int submitSongId) {
        public boolean foundInVerify() {
            return verifySongId > 0;
        }

        public boolean confirmedOnVerify() {
            return verifySongId > 0;
        }
    }
}
