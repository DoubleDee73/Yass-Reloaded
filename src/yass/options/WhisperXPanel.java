package yass.options;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import java.util.concurrent.CancellationException;

import org.apache.commons.lang3.StringUtils;

import yass.I18;
import yass.PythonRuntimeSupport;
import yass.integration.transcription.openai.OpenAiTranscriptionModel;
import yass.integration.transcription.whisperx.WhisperXComputeType;
import yass.integration.transcription.whisperx.WhisperXDevice;
import yass.integration.transcription.whisperx.WhisperXHealthCheckResult;
import yass.integration.transcription.whisperx.WhisperXHealthCheckService;
import yass.integration.transcription.whisperx.WhisperXModel;

public class WhisperXPanel extends OptionsPanel {

    private static final long serialVersionUID = 1L;

    private JTextArea statusArea;
    private JButton whisperXTestButton;
    private JButton whisperXUpdateButton;
    private WhisperXHealthCheckService whisperXUpdateService;
    private SwingWorker<WhisperXHealthCheckService.PackageUpdateResult, String> whisperXUpdateWorker;
    private volatile boolean whisperXUpdateRunning;
    private volatile boolean whisperXPythonAvailable;

    private void addFullWidthComment(String text) {
        JPanel row = new JPanel(new BorderLayout());
        JLabel label = new JLabel("<html><font color=gray>" + text);
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        row.add(label, BorderLayout.CENTER);
        getRight().add(row);
    }

    @Override
    public void addRows() {
        setLabelWidth(180);
        prefillDetectedPython();
        addFullWidthComment(I18.get("options_external_tools_transcription_comment"));
        addFullWidthComment(I18.get("options_external_tools_openai_comment"));
        addApiKey(I18.get("options_external_tools_openai_api_key"), "openai-api-key");
        addChoice(I18.get("options_external_tools_openai_model"), OpenAiTranscriptionModel.values(), "openai-model");
        addFullWidthComment(I18.get("options_external_tools_whisperx_comment"));
        addFullWidthComment(I18.get("options_external_tools_whisperx_mode_explainer"));
        addFile(I18.get("options_external_tools_whisperx_python"), "whisperx-python");
        addFullWidthComment("Path to the Python runtime used for this tool. Leave empty to use the default Python under External Tools > Locations.");
        addBoolean(I18.get("options_external_tools_whisperx_use_module"),
                   "whisperx-use-module",
                   I18.get("options_external_tools_whisperx_use_module_value"));
        addText(I18.get("options_external_tools_whisperx_command"), "whisperx-command");
        addFullWidthComment(I18.get("options_external_tools_whisperx_command_hint"));
        addChoice(I18.get("options_external_tools_whisperx_model"), WhisperXModel.values(), "whisperx-model");
        addChoice(I18.get("options_external_tools_whisperx_device"), WhisperXDevice.values(), "whisperx-device");
        addChoice(I18.get("options_external_tools_whisperx_compute_type"),
                  WhisperXComputeType.values(),
                  "whisperx-compute-type");
        harmonizeRuntimeChoiceWidths();
        addText(I18.get("options_external_tools_whisperx_cache_folder"), "whisperx-cache-folder");
        addHealthCheckSection();
    }

    private void harmonizeRuntimeChoiceWidths() {
        JComboBox<?> reference = findChoiceComboByLabel(I18.get("options_external_tools_openai_model"));
        if (reference == null) {
            return;
        }
        Dimension referenceSize = reference.getPreferredSize();
        if (referenceSize == null || referenceSize.width <= 0) {
            return;
        }
        alignChoiceWidth(I18.get("options_external_tools_whisperx_model"), referenceSize);
        alignChoiceWidth(I18.get("options_external_tools_whisperx_device"), referenceSize);
        alignChoiceWidth(I18.get("options_external_tools_whisperx_compute_type"), referenceSize);
    }

    private void alignChoiceWidth(String labelText, Dimension referenceSize) {
        JComboBox<?> combo = findChoiceComboByLabel(labelText);
        if (combo == null) {
            return;
        }
        int width = referenceSize.width;
        int height = Math.max(combo.getPreferredSize().height, referenceSize.height);
        Dimension size = new Dimension(width, height);
        combo.setPreferredSize(size);
        combo.setMinimumSize(size);
        combo.setMaximumSize(size);
    }

    private JComboBox<?> findChoiceComboByLabel(String labelText) {
        if (StringUtils.isBlank(labelText) || getRight() == null) {
            return null;
        }
        for (Component rowComponent : getRight().getComponents()) {
            if (!(rowComponent instanceof Container row)) {
                continue;
            }
            JLabel rowLabel = null;
            JComboBox<?> rowCombo = null;
            for (Component child : row.getComponents()) {
                if (rowLabel == null && child instanceof JLabel label) {
                    rowLabel = label;
                } else if (rowCombo == null && child instanceof JComboBox<?> combo) {
                    rowCombo = combo;
                }
            }
            if (rowLabel != null && rowCombo != null && labelText.equals(rowLabel.getText())) {
                return rowCombo;
            }
        }
        return null;
    }

    private void prefillDetectedPython() {
        String configuredPython = getProperty("whisperx-python");
        if (!PythonRuntimeSupport.shouldCanonicalizeToolPython(configuredPython)) {
            return;
        }
        WhisperXHealthCheckService service = new WhisperXHealthCheckService(configuredPython,
                                                                            getProperty(PythonRuntimeSupport.PROP_DEFAULT_PYTHON),
                                                                            Boolean.parseBoolean(getProperty("whisperx-use-module")),
                                                                            getProperty("whisperx-command"));
        String detectedPython = service.detectPythonExecutable();
        if (detectedPython != null && !detectedPython.isBlank()) {
            setProperty("whisperx-python", detectedPython);
        }
    }

    private void addHealthCheckSection() {
        getRight().add(Box.createRigidArea(new Dimension(0, 8)));
        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        JLabel label = new JLabel(I18.get("options_external_tools_whisperx_test"));
        label.setPreferredSize(new Dimension(getLabelWidth(), 20));
        whisperXTestButton = new JButton(I18.get("options_external_tools_whisperx_test_button"));
        whisperXUpdateButton = new JButton(I18.get("options_external_tools_whisperx_update_button"));
        whisperXTestButton.addActionListener(e -> runHealthCheck(whisperXTestButton, whisperXUpdateButton));
        whisperXUpdateButton.addActionListener(e -> runWhisperXUpdate(whisperXTestButton, whisperXUpdateButton));
        whisperXPythonAvailable = false;
        whisperXUpdateButton.setEnabled(canRunWhisperXInstallOrUpdate());
        buttonRow.add(label);
        buttonRow.add(whisperXTestButton);
        buttonRow.add(Box.createRigidArea(new Dimension(8, 0)));
        buttonRow.add(whisperXUpdateButton);
        buttonRow.add(Box.createHorizontalGlue());
        getRight().add(buttonRow);
        getRight().add(Box.createRigidArea(new Dimension(0, 8)));

        statusArea = new JTextArea(5, 40);
        statusArea.setEditable(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        statusArea.setOpaque(false);
        statusArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        statusArea.setText(I18.get("options_external_tools_whisperx_status_idle"));

        JPanel statusRow = new JPanel();
        statusRow.setLayout(new BoxLayout(statusRow, BoxLayout.X_AXIS));
        JLabel statusLabel = new JLabel(I18.get("options_external_tools_whisperx_status_label"));
        statusLabel.setVerticalAlignment(SwingConstants.TOP);
        statusLabel.setPreferredSize(new Dimension(getLabelWidth(), 90));
        statusLabel.setMaximumSize(new Dimension(getLabelWidth(), 90));
        statusLabel.setAlignmentY(Component.TOP_ALIGNMENT);
        JScrollPane statusScroll = new JScrollPane(statusArea);
        statusScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        statusScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        statusScroll.setBorder(BorderFactory.createCompoundBorder(UIManager.getBorder("TextField.border"), BorderFactory.createEmptyBorder()));
        statusScroll.setPreferredSize(new Dimension(460, 90));
        statusScroll.setAlignmentY(Component.TOP_ALIGNMENT);
        statusRow.add(statusLabel);
        statusRow.add(statusScroll);
        getRight().add(statusRow);
        getRight().add(Box.createRigidArea(new Dimension(0, 6)));
    }

    private void runHealthCheck(JButton button, JButton updateButton) {
        button.setEnabled(false);
        updateButton.setEnabled(false);
        statusArea.setText(I18.get("options_external_tools_whisperx_status_running"));

        SwingWorker<WhisperXHealthCheckResult, Void> worker = new SwingWorker<>() {
            @Override
            protected WhisperXHealthCheckResult doInBackground() {
                WhisperXHealthCheckService service = new WhisperXHealthCheckService(getProperty("whisperx-python"),
                                                                                   getProperty(PythonRuntimeSupport.PROP_DEFAULT_PYTHON),
                                                                                   Boolean.parseBoolean(getProperty("whisperx-use-module")),
                                                                                   getProperty("whisperx-command"),
                                                                                   getProperty("ffmpegPath"));
                return service.runHealthCheck();
            }

            @Override
            protected void done() {
                button.setEnabled(true);
                try {
                    WhisperXHealthCheckResult result = get();
                    whisperXPythonAvailable = result.isPythonFound();
                    String healthOk = Boolean.toString(result.isPythonFound() && result.isWhisperXAvailable() && result.isFfmpegAvailable());
                    setProperty("whisperx-health-ok", healthOk);
                    prop.setProperty("whisperx-health-ok", healthOk);
                    applyRecommendedRuntimeSettings(result);
                    prop.store();
                    statusArea.setText(formatResult(result));
                } catch (Exception ex) {
                    whisperXPythonAvailable = false;
                    setProperty("whisperx-health-ok", "false");
                    prop.setProperty("whisperx-health-ok", "false");
                    prop.store();
                    statusArea.setText(I18.get("options_external_tools_whisperx_status_failed") + "\n\n" + ex.getMessage());
                }
                updateButton.setEnabled(canRunWhisperXInstallOrUpdate());
            }
        };
        worker.execute();
    }

    private void runWhisperXUpdate(JButton testButton, JButton updateButton) {
        if (whisperXUpdateRunning) {
            cancelWhisperXUpdate();
            return;
        }
        testButton.setEnabled(false);
        updateButton.setEnabled(false);
        statusArea.setText(I18.get("options_external_tools_whisperx_status_updating"));
        whisperXUpdateService = new WhisperXHealthCheckService(getProperty("whisperx-python"),
                                                               getProperty(PythonRuntimeSupport.PROP_DEFAULT_PYTHON),
                                                               Boolean.parseBoolean(getProperty("whisperx-use-module")),
                                                               getProperty("whisperx-command"),
                                                               getProperty("ffmpegPath"));
        whisperXUpdateRunning = true;
        updateButton.setText(I18.get("tool_correct_cancel"));
        updateButton.setEnabled(true);

        whisperXUpdateWorker = new SwingWorker<>() {
            @Override
            protected WhisperXHealthCheckService.PackageUpdateResult doInBackground() {
                return whisperXUpdateService.updateWhisperXPackage(line -> publish(line));
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    appendStatusLine(line);
                }
            }

            @Override
            protected void done() {
                testButton.setEnabled(true);
                whisperXUpdateRunning = false;
                updateButton.setText(I18.get("options_external_tools_whisperx_update_button"));
                updateButton.setEnabled(canRunWhisperXInstallOrUpdate());
                try {
                    WhisperXHealthCheckService.PackageUpdateResult result = get();
                    if (result.isCancelled()) {
                        statusArea.setText(I18.get("options_external_tools_whisperx_status_update_cancelled"));
                    } else if (result.isSuccess()) {
                        if (StringUtils.isNotBlank(result.getPythonExecutable())) {
                            setProperty("whisperx-python", result.getPythonExecutable());
                            setProperty("whisperx-health-ok", "false");
                            prop.setProperty("whisperx-health-ok", "false");
                            prop.store();
                        }
                        statusArea.setText(result.getMessage());
                    } else {
                        statusArea.setText(I18.get("options_external_tools_whisperx_status_update_failed")
                                           + "\n\n"
                                           + result.getMessage());
                    }
                } catch (Exception ex) {
                    if (ex instanceof CancellationException) {
                        statusArea.setText(I18.get("options_external_tools_whisperx_status_update_cancelled"));
                    } else {
                        statusArea.setText(I18.get("options_external_tools_whisperx_status_update_failed")
                                           + "\n\n"
                                           + ex.getMessage());
                    }
                }
            }
        };
        whisperXUpdateWorker.execute();
    }

    private void cancelWhisperXUpdate() {
        if (!whisperXUpdateRunning || whisperXUpdateService == null) {
            return;
        }
        statusArea.setText(I18.get("options_external_tools_whisperx_status_update_cancelling"));
        whisperXUpdateService.cancelWhisperXUpdate();
        if (whisperXUpdateWorker != null) {
            whisperXUpdateWorker.cancel(true);
        }
    }

    private void appendStatusLine(String line) {
        if (line == null) {
            return;
        }
        String current = statusArea.getText();
        String next = StringUtils.isBlank(current) ? line : current + "\n" + line;
        statusArea.setText(next);
        statusArea.setCaretPosition(statusArea.getDocument().getLength());
    }

    private boolean isWhisperXHealthOk() {
        return Boolean.parseBoolean(StringUtils.defaultString(getProperty("whisperx-health-ok"), "false"));
    }

    private boolean canRunWhisperXInstallOrUpdate() {
        return whisperXPythonAvailable
                || isWhisperXHealthOk()
                || PythonRuntimeSupport.hasToolPython(prop, "whisperx-python");
    }

    private void applyRecommendedRuntimeSettings(WhisperXHealthCheckResult result) {
        if (result == null) {
            return;
        }

        if (isAutoValue("whisperx-model")) {
            setAndPersist("whisperx-effective-model", StringUtils.defaultIfBlank(result.getRecommendedModel(), "small"));
        }
        if (isAutoValue("whisperx-device")) {
            setAndPersist("whisperx-effective-device",
                          StringUtils.defaultIfBlank(result.getRecommendedDevice(), WhisperXDevice.CPU.getValue()));
        }
        if (isAutoValue("whisperx-compute-type")) {
            setAndPersist("whisperx-effective-compute-type",
                          StringUtils.defaultIfBlank(result.getRecommendedComputeType(), WhisperXComputeType.INT8.getValue()));
        }
    }

    private boolean isAutoValue(String key) {
        return "auto".equalsIgnoreCase(StringUtils.defaultIfBlank(getProperty(key), "auto"));
    }

    private void setAndPersist(String key, String value) {
        setProperty(key, value);
        prop.setProperty(key, value);
    }

    private String formatResult(WhisperXHealthCheckResult result) {
        StringBuilder text = new StringBuilder();
        text.append("Python: ")
            .append(result.isPythonFound() ? I18.get("options_external_tools_status_ok") : I18.get("options_external_tools_status_missing"));
        if (result.getPythonCommand() != null) {
            text.append(" (").append(result.getPythonCommand()).append(")");
        }
        if (result.getPythonVersion() != null) {
            text.append("\nVersion: ").append(result.getPythonVersion());
        }

        text.append("\nWhisperX: ")
            .append(result.isWhisperXAvailable() ? I18.get("options_external_tools_status_ok") : I18.get("options_external_tools_status_missing"));
        if (result.getWhisperXVersion() != null) {
            text.append("\nWhisperX version: ").append(result.getWhisperXVersion());
        }

        text.append("\nFFmpeg: ")
            .append(result.isFfmpegAvailable() ? I18.get("options_external_tools_status_ok") : I18.get("options_external_tools_status_missing"));
        if (result.getFfmpegVersion() != null) {
            text.append("\nFFmpeg version: ").append(result.getFfmpegVersion());
        }

        if (result.getTorchVersion() != null) {
            text.append("\nTorch version: ").append(result.getTorchVersion());
        }
        if (result.getTorchCudaBuild() != null) {
            text.append("\nTorch CUDA build: ").append(result.getTorchCudaBuild());
        }
        String gpuLabel = StringUtils.defaultIfBlank(result.getGpuAvailability(), "unknown");
        if (StringUtils.isNotBlank(result.getGpuName())) {
            gpuLabel += " (" + result.getGpuName() + ")";
        }
        if (result.getGpuVramMiB() != null) {
            gpuLabel += " " + result.getGpuVramMiB() + " MiB";
        }
        text.append("\nGPU: ").append(gpuLabel);

        String recommendedModel = StringUtils.defaultIfBlank(result.getRecommendedModel(), "small");
        String recommendedDevice = StringUtils.defaultIfBlank(result.getRecommendedDevice(), WhisperXDevice.CPU.getValue());
        String recommendedComputeType = StringUtils.defaultIfBlank(result.getRecommendedComputeType(), WhisperXComputeType.INT8.getValue());

        String appliedModel = resolveAppliedValue("whisperx-model",
                                                  "whisperx-effective-model",
                                                  recommendedModel,
                                                  "small");
        String appliedDevice = resolveAppliedValue("whisperx-device",
                                                   "whisperx-effective-device",
                                                   recommendedDevice,
                                                   WhisperXDevice.CPU.getValue());
        String appliedComputeType = resolveAppliedValue("whisperx-compute-type",
                                                        "whisperx-effective-compute-type",
                                                        recommendedComputeType,
                                                        WhisperXComputeType.INT8.getValue());

        text.append("\nRecommended model: ").append(recommendedModel);
        text.append("\nRecommended device: ").append(recommendedDevice);
        text.append("\nRecommended compute type: ").append(recommendedComputeType);
        text.append("\nApplied model: ").append(appliedModel);
        text.append("\nApplied device: ").append(appliedDevice);
        text.append("\nApplied compute type: ").append(appliedComputeType);
        if (StringUtils.isNotBlank(result.getRecommendationReason())) {
            text.append("\nReason: ").append(result.getRecommendationReason());
        }
        text.append("\n\n").append(result.getDetails());
        return text.toString();
    }

    private String resolveAppliedValue(String configKey,
                                       String effectiveKey,
                                       String recommendedValue,
                                       String fallback) {
        String configured = StringUtils.defaultIfBlank(getProperty(configKey), "auto");
        if (!"auto".equalsIgnoreCase(configured)) {
            return configured;
        }
        String effective = StringUtils.defaultIfBlank(getProperty(effectiveKey), "");
        if (StringUtils.isNotBlank(effective)) {
            return effective;
        }
        return StringUtils.defaultIfBlank(recommendedValue, fallback);
    }
}
