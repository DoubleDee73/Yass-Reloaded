/*
 * Yass Reloaded - Karaoke Editor
 * Copyright (C) 2009-2023 Saruta
 * Copyright (C) 2023 DoubleDee
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package yass.integration.separation;

import java.io.File;

public class SeparationRequest {
    private final String songDirectory;
    private final File audioFile;
    private final String model;
    private final String outputFormat;
    private final String songBaseName;
    private final String modelType;

    public SeparationRequest(String songDirectory, File audioFile, String model, String outputFormat, String songBaseName) {
        this(songDirectory, audioFile, model, outputFormat, songBaseName, null);
    }

    public SeparationRequest(String songDirectory, File audioFile, String model, String outputFormat, String songBaseName, String modelType) {
        this.songDirectory = songDirectory;
        this.audioFile = audioFile;
        this.model = model;
        this.outputFormat = outputFormat;
        this.songBaseName = songBaseName;
        this.modelType = modelType;
    }

    public SeparationRequest withOptions(String newModel, String newOutputFormat) {
        return new SeparationRequest(songDirectory, audioFile, newModel, newOutputFormat, songBaseName, modelType);
    }

    public SeparationRequest withOptions(String newModel, String newOutputFormat, String newModelType) {
        return new SeparationRequest(songDirectory, audioFile, newModel, newOutputFormat, songBaseName, newModelType);
    }

    public String getSongDirectory() {
        return songDirectory;
    }

    public File getAudioFile() {
        return audioFile;
    }

    public String getModel() {
        return model;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public String getSongBaseName() {
        return songBaseName;
    }

    public String getModelType() {
        return modelType;
    }
}
