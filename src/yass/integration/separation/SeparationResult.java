package yass.integration.separation;

import org.apache.commons.lang3.StringUtils;

import java.io.File;

public class SeparationResult {
    private final File vocalsFile;
    private final File leadFile;
    private final File instrumentalFile;
    private final File instrumentalBackingFile;

    public SeparationResult(File vocalsFile, File leadFile, File instrumentalFile, File instrumentalBackingFile) {
        this.vocalsFile = vocalsFile;
        this.leadFile = leadFile;
        this.instrumentalFile = instrumentalFile;
        this.instrumentalBackingFile = instrumentalBackingFile;
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
}