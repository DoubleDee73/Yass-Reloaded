package yass.options;

import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.integration.transcription.TranscriptionEngine;
import yass.integration.transcription.whisperx.*;

import javax.swing.*;
import java.awt.*;

public class WhisperXPanel extends OptionsPanel {

    private static final long serialVersionUID = 1L;

    private JTextArea statusArea;

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
        addFullWidthComment(I18.get("options_external_tools_whisperx_comment"));
        addChoice(I18.get("options_external_tools_transcription_engine"), TranscriptionEngine.values(), "transcription-engine");
        addFullWidthComment(I18.get("options_external_tools_whisperx_mode_explainer"));
        addFile(I18.get("options_external_tools_whisperx_python"), "whisperx-python");
        addBoolean(I18.get("options_external_tools_whisperx_use_module"),
                   "whisperx-use-module",
                   I18.get("options_external_tools_whisperx_use_module_value"));
        addText(I18.get("options_external_tools_whisperx_command"), "whisperx-command");
        addFullWidthComment(I18.get("options_external_tools_whisperx_command_hint"));
        addChoice(I18.get("options_external_tools_whisperx_model"), WhisperXModel.values(), "whisperx-model");
        addText(I18.get("options_external_tools_whisperx_language"), "whisperx-language");
        addFullWidthComment(I18.get("options_external_tools_whisperx_language_hint"));
        addChoice(I18.get("options_external_tools_whisperx_device"), WhisperXDevice.values(), "whisperx-device");
        addChoice(I18.get("options_external_tools_whisperx_compute_type"),
                  WhisperXComputeType.values(),
                  "whisperx-compute-type");
        addText(I18.get("options_external_tools_whisperx_cache_folder"), "whisperx-cache-folder");
        addHealthCheckSection();
    }

    private void prefillDetectedPython() {
        if (getProperty("whisperx-python") != null && !getProperty("whisperx-python").isBlank()) {
            return;
        }
        WhisperXHealthCheckService service = new WhisperXHealthCheckService("",
                                                                            Boolean.parseBoolean(getProperty("whisperx-use-module")),
                                                                            getProperty("whisperx-command"));
        String detectedPython = service.detectPythonExecutable();
        if (detectedPython != null && !detectedPython.isBlank()) {
            setProperty("whisperx-python", detectedPython);
        }
    }

    private void addHealthCheckSection() {
        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        JLabel label = new JLabel(I18.get("options_external_tools_whisperx_test"));
        label.setPreferredSize(new Dimension(getLabelWidth(), 20));
        JButton button = new JButton(I18.get("options_external_tools_whisperx_test_button"));
        button.addActionListener(e -> runHealthCheck(button));
        buttonRow.add(label);
        buttonRow.add(button);
        buttonRow.add(Box.createHorizontalGlue());
        getRight().add(buttonRow);

        statusArea = new JTextArea(7, 40);
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
        statusLabel.setPreferredSize(new Dimension(getLabelWidth(), 20));
        JScrollPane statusScroll = new JScrollPane(statusArea);
        statusScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        statusScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        statusScroll.setBorder(BorderFactory.createCompoundBorder(UIManager.getBorder("TextField.border"), BorderFactory.createEmptyBorder()));
        statusScroll.setPreferredSize(new Dimension(460, 120));
        statusRow.add(statusLabel);
        statusRow.add(statusScroll);
        getRight().add(statusRow);
        getRight().add(Box.createRigidArea(new Dimension(0, 6)));
    }

    private void runHealthCheck(JButton button) {
        button.setEnabled(false);
        statusArea.setText(I18.get("options_external_tools_whisperx_status_running"));

        SwingWorker<WhisperXHealthCheckResult, Void> worker = new SwingWorker<>() {
            @Override
            protected WhisperXHealthCheckResult doInBackground() {
                WhisperXHealthCheckService service = new WhisperXHealthCheckService(getProperty("whisperx-python"),
                                                                                   Boolean.parseBoolean(getProperty("whisperx-use-module")),
                                                                                   getProperty("whisperx-command"));
                return service.runHealthCheck();
            }

            @Override
            protected void done() {
                button.setEnabled(true);
                try {
                    WhisperXHealthCheckResult result = get();
                    setProperty("whisperx-health-ok", Boolean.toString(result.isPythonFound() && result.isWhisperXAvailable() && result.isFfmpegAvailable()));
                    applyRecommendedRuntimeSettings(result);
                    statusArea.setText(formatResult(result));
                } catch (Exception ex) {
                    setProperty("whisperx-health-ok", "false");
                    statusArea.setText(I18.get("options_external_tools_whisperx_status_failed") + "\n\n" + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void applyRecommendedRuntimeSettings(WhisperXHealthCheckResult result) {
        String gpu = result.getGpuAvailability() == null ? "unknown" : result.getGpuAvailability().trim().toLowerCase();
        String recommendedDevice = null;
        String recommendedComputeType = null;

        if (gpu.contains("not available")) {
            recommendedDevice = WhisperXDevice.CPU.getValue();
            recommendedComputeType = WhisperXComputeType.INT8.getValue();
        } else if (gpu.contains("available")) {
            recommendedDevice = WhisperXDevice.CUDA.getValue();
            recommendedComputeType = WhisperXComputeType.FLOAT16.getValue();
        }

        if (recommendedDevice != null && "auto".equalsIgnoreCase(getProperty("whisperx-device"))) {
            setProperty("whisperx-device", recommendedDevice);
        }
        if (recommendedComputeType != null && "auto".equalsIgnoreCase(getProperty("whisperx-compute-type"))) {
            setProperty("whisperx-compute-type", recommendedComputeType);
        }
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

        text.append("\nGPU: ").append(result.getGpuAvailability());
        text.append("\nRecommended device: ").append(StringUtils.defaultIfBlank(getProperty("whisperx-device"), "auto"));
        text.append("\nRecommended compute type: ").append(StringUtils.defaultIfBlank(getProperty("whisperx-compute-type"), "auto"));
        text.append("\n\n").append(result.getDetails());
        return text.toString();
    }
}
