package yass.usdb;

import java.nio.file.Path;

public record UsdbSongImportResult(Path songDirectory, Path songFile) {
}
