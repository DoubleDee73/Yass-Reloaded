package yass.integration.separation;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class SeparationResult {
    private final File vocalsFile;
    private final File leadFile;
    private final File instrumentalFile;
    private final File instrumentalBackingFile;
    private final List<File> downloadedFiles;

    public SeparationResult(File vocalsFile, File leadFile, File instrumentalFile, File instrumentalBackingFile) {
        this(vocalsFile, leadFile, instrumentalFile, instrumentalBackingFile, Collections.emptyList());
    }

    public SeparationResult(File vocalsFile,
                            File leadFile,
                            File instrumentalFile,
                            File instrumentalBackingFile,
                            List<File> downloadedFiles) {
        this.vocalsFile = vocalsFile;
        this.leadFile = leadFile;
        this.instrumentalFile = instrumentalFile;
        this.instrumentalBackingFile = instrumentalBackingFile;
        this.downloadedFiles = downloadedFiles == null ? Collections.emptyList() : List.copyOf(downloadedFiles);
    }

    public File getVocalsFile() {
        return vocalsFile != null ? vocalsFile : leadFile;
    }

    public File getLeadFile() {
        return leadFile;
    }

    public File getInstrumentalFile() {
        return instrumentalFile;
    }

    public File getInstrumentalBackingFile() {
        return instrumentalBackingFile;
    }

    public File getPreferredInstrumentalFile(String defaultInstrumental) {
        if ("instrumental-backing".equalsIgnoreCase(StringUtils.defaultIfBlank(defaultInstrumental, "instrumental"))
                && instrumentalBackingFile != null) {
            return instrumentalBackingFile;
        }
        return instrumentalFile != null ? instrumentalFile : instrumentalBackingFile;
    }

    public List<File> getDownloadedFiles() {
        return downloadedFiles;
    }

}