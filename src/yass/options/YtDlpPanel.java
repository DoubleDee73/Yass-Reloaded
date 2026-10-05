/*
 * Yass - Karaoke Editor
 * Copyright (C) 2009 Saruta
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

package yass.options;

import com.jfposton.ytdlp.YtDlp;
import com.jfposton.ytdlp.YtDlpException;
import com.jfposton.ytdlp.YtDlpRequest;
import com.jfposton.ytdlp.YtDlpResponse;
import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.PythonRuntimeSupport;
import yass.options.enums.YtDlpAudioBitrate;
import yass.options.enums.YtDlpAudioFormat;
import yass.options.enums.YtDlpVideoCodec;
import yass.options.enums.YtDlpVideoResolution;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wizard-Default settings panel
 *
 * @author DoubleDee
 */
public class YtDlpPanel extends OptionsPanel {

    private static final long serialVersionUID = 1L;
    public static final String YTDLP_AUDIO_FORMAT = "ytdlp-audio-format";
    public static final String YTDLP_AUDIO_BITRATE = "ytdlp-audio-bitrate";
    public static final String YTDLP_VIDEO_CODEC = "ytdlp-video-codec";
    public static final String YTDLP_VIDEO_RESOLUTION = "ytdlp-video-resolution";

    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    private String ytDlpVersion;
    private JLabel versionLabel;

    private enum PackageManager {
        HOMEBREW("homebrew", List.of("brew", "upgrade", "yt-dlp")),
        WINGET("winget", List.of("winget", "upgrade", "yt-dlp")),
        CHOCOLATEY("chocolatey", List.of("choco", "upgrade", "yt-dlp")),
        SCOOP("scoop", List.of("scoop", "update", "yt-dlp"));

        final String keyword;
        final List<String> upgradeCommand;

        PackageManager(String keyword, List<String> upgradeCommand) {
            this.keyword = keyword;
            this.upgradeCommand = upgradeCommand;
        }
    }

    public YtDlpPanel(String ytDlpVersion) {
        this.ytDlpVersion = ytDlpVersion;
    }

    /**
     * Gets the body attribute of the DirPanel object
     */
    public void addRows() {
        if (StringUtils.isNotEmpty(getYtDlpVersion())) {
            JPanel versionRow = new JPanel();
            versionRow.setLayout(new BoxLayout(versionRow, BoxLayout.X_AXIS));
            versionLabel = new JLabel(I18.get("options_wizard_ytdlp_version") + " " + getYtDlpVersion());
            JButton updateButton = new JButton(I18.get("options_wizard_ytdlp_update"));
            updateButton.addActionListener(e -> runUpdate(updateButton));
            versionRow.add(versionLabel);
            versionRow.add(Box.createHorizontalGlue());
            versionRow.add(updateButton);
            getRight().add(versionRow);
            addSeparator(I18.get("options_wizard_ytdlp_audio"));
            addChoice(I18.get("options_wizard_ytdlp_audio_format"), YtDlpAudioFormat.values(),
                      YTDLP_AUDIO_FORMAT, 100);
            addChoice(I18.get("options_wizard_ytdlp_audio_bitrate"), YtDlpAudioBitrate.values(),
                      YTDLP_AUDIO_BITRATE, 100);
            addSeparator();
            addSeparator(I18.get("options_wizard_ytdlp_video"));
            addChoice(I18.get("options_wizard_ytdlp_video_codec"), YtDlpVideoCodec.values(),
                      YTDLP_VIDEO_CODEC, 100);
            addChoice(I18.get("options_wizard_ytdlp_video_resolution"), YtDlpVideoResolution.values(),
                      YTDLP_VIDEO_RESOLUTION, 100);
        } else {
            addComment(I18.get("options_wizard_ytdlp_not_found"));
        }
    }

    private void runUpdate(JButton button) {
        button.setEnabled(false);
        button.setText(I18.get("options_wizard_ytdlp_updating"));
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                LOGGER.info("[YtDlpUpdate] Starting yt-dlp update");
                YtDlpRequest request = new YtDlpRequest();
                request.setOption("update");
                try {
                    YtDlpResponse response = YtDlp.execute(request);
                    String combined = StringUtils.defaultString(response.getOut())
                            + "\n" + StringUtils.defaultString(response.getErr());
                    LOGGER.info("[YtDlpUpdate] yt-dlp --update output: " + combined.trim());
                    PackageManager pm = detectPackageManager(combined);
                    if (pm != null) {
                        LOGGER.info("[YtDlpUpdate] Package manager detected (exit 0): " + pm);
                        runPackageManagerUpgrade(pm);
                    } else if (isPipInstallError(combined)) {
                        LOGGER.info("[YtDlpUpdate] pip block detected (exit 0), trying pip upgrade");
                        runPipUpgrade();
                    }
                } catch (YtDlpException ex) {
                    LOGGER.info("[YtDlpUpdate] yt-dlp --update failed (exit non-zero): " + ex.getMessage());
                    if (isPipInstallError(ex.getMessage())) {
                        LOGGER.info("[YtDlpUpdate] pip-managed install detected, trying pip upgrade");
                        runPipUpgrade();
                    } else {
                        PackageManager pm = detectPackageManager(ex.getMessage());
                        if (pm != null) {
                            LOGGER.info("[YtDlpUpdate] Package manager detected (exit non-zero): " + pm);
                            runPackageManagerUpgrade(pm);
                        } else {
                            throw ex;
                        }
                    }
                }
                String newVersion = StringUtils.trimToEmpty(YtDlp.getVersion());
                LOGGER.info("[YtDlpUpdate] Version after update: " + newVersion);
                return newVersion;
            }

            @Override
            protected void done() {
                try {
                    String newVersion = get();
                    String oldVersion = getYtDlpVersion();
                    setYtDlpVersion(newVersion);
                    versionLabel.setText(I18.get("options_wizard_ytdlp_version") + " " + newVersion);
                    if (StringUtils.equals(oldVersion, newVersion)) {
                        LOGGER.info("[YtDlpUpdate] Version unchanged after update (" + newVersion + ")");
                        showDialog(I18.get("options_wizard_ytdlp_already_up_to_date") + " (" + newVersion + ")",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String msg = StringUtils.defaultIfBlank(cause.getMessage(), cause.toString());
                    LOGGER.log(Level.WARNING, "[YtDlpUpdate] Update failed: " + msg, cause);
                    showDialog(I18.get("options_wizard_ytdlp_update_failed") + ":<br>" + msg.replace("\n", "<br>"),
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    button.setText(I18.get("options_wizard_ytdlp_update"));
                    button.setEnabled(true);
                }
            }

            private void showDialog(String htmlBody, int messageType) {
                JOptionPane.showMessageDialog(YtDlpPanel.this,
                        "<html>" + htmlBody + "</html>",
                        I18.get("options_wizard_ytdlp_update"),
                        messageType);
            }
        }.execute();
    }

    private static boolean isPipInstallError(String message) {
        return message != null && message.toLowerCase().contains("pip");
    }

    private static PackageManager detectPackageManager(String output) {
        if (output == null) return null;
        String lower = output.toLowerCase();
        for (PackageManager pm : PackageManager.values()) {
            if (lower.contains(pm.keyword)) return pm;
        }
        return null;
    }

    private void runPackageManagerUpgrade(PackageManager pm) throws IOException, InterruptedException {
        List<String> command = resolvePackageManagerCommand(pm);
        LOGGER.info("[YtDlpUpdate] Running package manager upgrade: " + String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process;
        try {
            process = pb.start();
        } catch (IOException ex) {
            throw new IOException(I18.get("options_wizard_ytdlp_update_package_manager"), ex);
        }
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        LOGGER.info("[YtDlpUpdate] Package manager upgrade exit=" + exitCode + " output: " + output.trim());
        if (exitCode != 0) {
            throw new IOException(I18.get("options_wizard_ytdlp_update_package_manager") + "\n\n" + output.trim());
        }
    }

    private List<String> resolvePackageManagerCommand(PackageManager pm) {
        if (pm == PackageManager.HOMEBREW) {
            // Derive brew path from the yt-dlp binary (they share the same bin directory)
            String ytDlpPath = YtDlp.getExecutablePath();
            if (StringUtils.isNotBlank(ytDlpPath)) {
                Path brewPath = Path.of(ytDlpPath).resolveSibling("brew");
                if (Files.isExecutable(brewPath)) {
                    LOGGER.info("[YtDlpUpdate] Resolved brew at: " + brewPath);
                    return List.of(brewPath.toString(), "upgrade", "yt-dlp");
                }
            }
        }
        return pm.upgradeCommand;
    }

    private void runPipUpgrade() throws IOException, InterruptedException {
        String python = resolveYtDlpPython();
        List<String> command = StringUtils.isNotBlank(python)
                ? List.of(python, "-m", "pip", "install", "--upgrade", "--disable-pip-version-check", "yt-dlp")
                : List.of("pip", "install", "--upgrade", "--disable-pip-version-check", "yt-dlp");
        LOGGER.info("[YtDlpUpdate] pip upgrade command: " + String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process;
        try {
            process = pb.start();
        } catch (IOException ex) {
            throw new IOException(I18.get("options_wizard_ytdlp_update_pip_not_found"), ex);
        }
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        LOGGER.info("[YtDlpUpdate] pip upgrade exit=" + exitCode + " output: " + output.trim());
        if (exitCode != 0) {
            // pip failed because yt-dlp was installed by a package manager — try that instead
            PackageManager pm = detectPackageManager(output);
            if (pm != null || output.toLowerCase().contains("no record file")) {
                if (pm != null) {
                    runPackageManagerUpgrade(pm);
                    return;
                }
                throw new IOException(I18.get("options_wizard_ytdlp_update_package_manager"));
            }
            throw new IOException("pip upgrade failed:\n" + output);
        }
    }

    private String resolveYtDlpPython() {
        // Read the shebang of the yt-dlp script to find the Python that owns it
        String ytDlpPath = YtDlp.getExecutablePath();
        if (StringUtils.isNotBlank(ytDlpPath)) {
            try {
                Path script = Path.of(ytDlpPath);
                if (Files.isRegularFile(script)) {
                    try (BufferedReader reader = Files.newBufferedReader(script)) {
                        String firstLine = reader.readLine();
                        if (firstLine != null && firstLine.startsWith("#!") && firstLine.contains("python")) {
                            String shebangPython = firstLine.substring(2).trim();
                            LOGGER.info("[YtDlpUpdate] Resolved Python from yt-dlp shebang: " + shebangPython);
                            return shebangPython;
                        }
                    }
                }
            } catch (IOException ex) {
                LOGGER.log(Level.FINE, "[YtDlpUpdate] Could not read yt-dlp shebang", ex);
            }
        }
        // Fall back to configured default Python
        String python = getProperties() != null
                ? StringUtils.trimToEmpty(getProperties().getProperty(PythonRuntimeSupport.PROP_DEFAULT_PYTHON))
                : "";
        LOGGER.info("[YtDlpUpdate] Using configured Python for pip: "
                + (StringUtils.isBlank(python) ? "<bare pip>" : python));
        return python;
    }

    public String getYtDlpVersion() {
        return ytDlpVersion;
    }

    public void setYtDlpVersion(String ytDlpVersion) {
        this.ytDlpVersion = ytDlpVersion;
    }
}
