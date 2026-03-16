package yass.options;

import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.integration.separation.audioseparator.AudioSeparatorHealthCheckResult;
import yass.integration.separation.audioseparator.AudioSeparatorHealthCheckService;

import javax.swing.*;
import java.awt.*;

public class AudioSeparatorPanel extends OptionsPanel {
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
        setLabelWidth(200);
        prefillDetectedPython();
        addFullWidthComment(I18.get("options_external_tools_audiosep_comment"));
        addFile(I18.get("options_external_tools_audiosep_python"), "audiosep-python");
        addText(I18.get("options_external_tools_audiosep_model"), "audiosep-model");
        addFullWidthComment(I18.get("options_external_tools_audiosep_model_hint"));
        addText(I18.get("options_external_tools_audiosep_model_dir"), "audiosep-model-dir");
        addText(I18.get("options_external_tools_audiosep_output_format"), "audiosep-output-format");
        addHealthCheckSection();
    }

    private void prefillDetectedPython() {
        if (StringUtils.isNotBlank(getProperty("audiosep-python"))) {
            return;
        }
        AudioSeparatorHealthCheckService service = new AudioSeparatorHealthCheckService("", getProperty("ffmpegPath"));
        String detected = service.detectPythonExecutable();
        if (StringUtils.isNotBlank(detected)) {
            setProperty("audiosep-python", detected);
        }
    }

    private void addHealthCheckSection() {
        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        JLabel label = new JLabel(I18.get("options_external_tools_audiosep_test"));
        label.setPreferredSize(new Dimension(getLabelWidth(), 20));
        JButton button = new JButton(I18.get("options_external_tools_audiosep_test_button"));
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
        statusArea.setText(I18.get("options_external_tools_audiosep_status_idle"));

        JPanel statusRow = new JPanel();
        statusRow.setLayout(new BoxLayout(statusRow, BoxLayout.X_AXIS));
        JLabel statusLabel = new JLabel(I18.get("options_external_tools_whisperx_status_label"));
        statusLabel.setVerticalAlignment(SwingConstants.TOP);
        statusLabel.setPreferredSize(new Dimension(getLabelWidth(), 20));
        JScrollPane statusScroll = new JScrollPane(statusArea);
        statusScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        statusScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        statusScroll.setBorder(BorderFactory.createCompoundBorder(
                UIManager.getBorder("TextField.border"), BorderFactory.createEmptyBorder()));
        statusScroll.setPreferredSize(new Dimension(460, 120));
        statusRow.add(statusLabel);
        statusRow.add(statusScroll);
        getRight().add(statusRow);
        getRight().add(Box.createRigidArea(new Dimension(0, 6)));
    }

    private void runHealthCheck(JButton button) {
        button.setEnabled(false);
        statusArea.setText(I18.get("options_external_tools_audiosep_status_running"));

        SwingWorker<AudioSeparatorHealthCheckResult, Void> worker = new SwingWorker<>() {
            @Override
            protected AudioSeparatorHealthCheckResult doInBackground() {
                AudioSeparatorHealthCheckService service = new AudioSeparatorHealthCheckService(
                        getProperty("audiosep-python"), getProperty("ffmpegPath"));
                return service.runHealthCheck();
            }

            @Override
            protected void done() {
                button.setEnabled(true);
                try {
                    AudioSeparatorHealthCheckResult result = get();
                    String healthOk = Boolean.toString(result.isHealthy());
                    prop.setProperty("audiosep-health-ok", healthOk);
                    prop.store();
                    statusArea.setText(formatResult(result));
                } catch (Exception ex) {
                    prop.setProperty("audiosep-health-ok", "false");
                    prop.store();
                    statusArea.setText(I18.get("options_external_tools_audiosep_status_failed") + "\n\n" + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private String formatResult(AudioSeparatorHealthCheckResult result) {
        StringBuilder text = new StringBuilder();
        text.append("Python: ")
            .append(result.isPythonFound() ? I18.get("options_external_tools_status_ok") : I18.get("options_external_tools_status_missing"));
        if (result.getPythonCommand() != null) {
            text.append(" (").append(result.getPythonCommand()).append(")");
        }
        if (result.getPythonVersion() != null) {
            text.append("\nVersion: ").append(result.getPythonVersion());
        }
        text.append("\naudio-separator: ")
            .append(result.isAudioSeparatorAvailable() ? I18.get("options_external_tools_status_ok") : I18.get("options_external_tools_status_missing"));
        if (result.getAudioSeparatorVersion() != null) {
            text.append("\naudio-separator version: ").append(result.getAudioSeparatorVersion());
        }
        text.append("\nFFmpeg: ")
            .append(result.isFfmpegAvailable() ? I18.get("options_external_tools_status_ok") : I18.get("options_external_tools_status_missing"));
        if (result.getFfmpegVersion() != null) {
            text.append("\nFFmpeg version: ").append(result.getFfmpegVersion());
        }
        text.append("\n\n").append(result.getDetails());
        return text.toString();
    }
}
