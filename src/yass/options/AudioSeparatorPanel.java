package yass.options;

import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.integration.separation.audioseparator.AudioSeparatorHealthCheckResult;
import yass.integration.separation.audioseparator.AudioSeparatorHealthCheckService;
import yass.integration.separation.audioseparator.AudioSeparatorModel;
import yass.integration.separation.audioseparator.AudioSeparatorSeparationService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;

public class AudioSeparatorPanel extends OptionsPanel {
    private static final long serialVersionUID = 1L;

    private JTextArea statusArea;
    private JComboBox<String> modelComboBox;

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
        addFile(I18.get("options_external_tools_audiosep_python"), AudioSeparatorSeparationService.PROP_PYTHON);
        addModelChoiceRow();
        addText(I18.get("options_external_tools_audiosep_model_dir"), AudioSeparatorSeparationService.PROP_MODEL_DIR);
        addText(I18.get("options_external_tools_audiosep_output_format"), AudioSeparatorSeparationService.PROP_OUTPUT_FORMAT);
        addHealthCheckSection();
    }

    private void prefillDetectedPython() {
        if (StringUtils.isNotBlank(getProperty(AudioSeparatorSeparationService.PROP_PYTHON))) {
            return;
        }
        AudioSeparatorHealthCheckService service = new AudioSeparatorHealthCheckService("", getProperty("ffmpegPath"));
        String detected = service.detectPythonExecutable();
        if (StringUtils.isNotBlank(detected)) {
            setProperty(AudioSeparatorSeparationService.PROP_PYTHON, detected);
        }
    }

    private void addModelChoiceRow() {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));

        JLabel lab = new JLabel(I18.get("options_external_tools_audiosep_model"));
        lab.setVerticalAlignment(JLabel.CENTER);
        lab.setHorizontalAlignment(JLabel.LEFT);
        lab.setPreferredSize(new Dimension(getLabelWidth(), 20));

        // Seed with static fallback values so the combobox is never empty
        AudioSeparatorModel[] fallbackModels = AudioSeparatorModel.values();
        String[] fallbackLabels = new String[fallbackModels.length];
        String[] fallbackValues = new String[fallbackModels.length];
        for (int i = 0; i < fallbackModels.length; i++) {
            fallbackLabels[i] = fallbackModels[i].getLabel();
            fallbackValues[i] = fallbackModels[i].getValue();
        }
        modelComboBox = new JComboBox<>(fallbackLabels);
        modelComboBox.setMaximumSize(new Dimension(400, 25));

        // Restore saved selection from static fallback list
        String savedModel = getProperty(AudioSeparatorSeparationService.PROP_MODEL);
        for (int i = 0; i < fallbackValues.length; i++) {
            if (fallbackValues[i].equalsIgnoreCase(savedModel)) {
                modelComboBox.setSelectedIndex(i);
                break;
            }
        }

        // Store backing filename values as client property; initially maps to fallback enum values
        modelComboBox.putClientProperty("modelValues", java.util.Arrays.asList(fallbackValues));

        modelComboBox.addActionListener(e -> {
            Object selected = modelComboBox.getSelectedItem();
            if (selected != null) {
                @SuppressWarnings("unchecked")
                List<String> values = (List<String>) modelComboBox.getClientProperty("modelValues");
                int idx = modelComboBox.getSelectedIndex();
                if (values != null && idx >= 0 && idx < values.size()) {
                    setProperty(AudioSeparatorSeparationService.PROP_MODEL, values.get(idx));
                }
            }
        });

        row.add(lab);
        row.add(modelComboBox);
        row.add(Box.createHorizontalGlue());
        lab.setAlignmentY(Component.TOP_ALIGNMENT);
        modelComboBox.setAlignmentY(Component.TOP_ALIGNMENT);
        getRight().add(row);
        getRight().add(Box.createRigidArea(new Dimension(0, 2)));

        loadModelsAsync(fallbackValues);
    }

    private void loadModelsAsync(String[] fallbackValues) {
        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() {
                String python = getProperty(AudioSeparatorSeparationService.PROP_PYTHON);
                if (StringUtils.isBlank(python)) return List.of();
                AudioSeparatorHealthCheckService svc = new AudioSeparatorHealthCheckService(python, getProperty("ffmpegPath"));
                return svc.listModels(python);
            }

            @Override
            protected void done() {
                try {
                    List<String> dynamicModels = get();
                    if (dynamicModels == null || dynamicModels.isEmpty()) return;
                    String savedModel = getProperty(AudioSeparatorSeparationService.PROP_MODEL);

                    // Detach listeners to avoid spurious property-save events during repopulation
                    ActionListener[] listeners = modelComboBox.getActionListeners();
                    for (ActionListener l : listeners) modelComboBox.removeActionListener(l);

                    modelComboBox.removeAllItems();
                    List<String> values = new java.util.ArrayList<>();
                    for (String filename : dynamicModels) {
                        values.add(filename);
                        modelComboBox.addItem(filename);
                    }
                    modelComboBox.putClientProperty("modelValues", values);

                    // Restore saved selection
                    int savedIdx = -1;
                    for (int i = 0; i < values.size(); i++) {
                        if (values.get(i).equalsIgnoreCase(savedModel)) {
                            savedIdx = i;
                            break;
                        }
                    }
                    if (savedIdx >= 0) {
                        modelComboBox.setSelectedIndex(savedIdx);
                    } else if (!values.isEmpty()) {
                        modelComboBox.setSelectedIndex(0);
                        setProperty(AudioSeparatorSeparationService.PROP_MODEL, values.get(0));
                    }

                    for (ActionListener l : listeners) modelComboBox.addActionListener(l);

                    getRight().revalidate();
                    getRight().repaint();
                } catch (Exception ignored) {
                }
            }
        };
        worker.execute();
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
        JLabel statusLabel = new JLabel(I18.get("options_external_tools_audiosep_status_label"));
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
                        getProperty(AudioSeparatorSeparationService.PROP_PYTHON), getProperty("ffmpegPath"));
                return service.runHealthCheck();
            }

            @Override
            protected void done() {
                button.setEnabled(true);
                try {
                    AudioSeparatorHealthCheckResult result = get();
                    String healthOk = Boolean.toString(result.isHealthy());
                    prop.setProperty(AudioSeparatorSeparationService.PROP_HEALTH_OK, healthOk);
                    prop.store();
                    statusArea.setText(formatResult(result));
                } catch (Exception ex) {
                    prop.setProperty(AudioSeparatorSeparationService.PROP_HEALTH_OK, "false");
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
