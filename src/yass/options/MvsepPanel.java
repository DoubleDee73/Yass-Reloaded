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

package yass.options;

import org.apache.commons.lang3.StringUtils;
import yass.I18;
import yass.integration.separation.mvsep.MvsepAccountInfo;
import yass.integration.separation.mvsep.MvsepAlgorithmCacheService;
import yass.integration.separation.mvsep.MvsepAlgorithmField;
import yass.integration.separation.mvsep.MvsepAlgorithmInfo;
import yass.integration.separation.mvsep.MvsepFieldOption;
import yass.integration.separation.mvsep.MvsepInstrumentalDefault;
import yass.integration.separation.mvsep.MvsepModel;
import yass.integration.separation.mvsep.MvsepOutputFormat;
import yass.integration.separation.mvsep.MvsepSeparationService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MvsepPanel extends OptionsPanel {

    private static final long serialVersionUID = 1L;

    private JComboBox<String> modelComboBox;
    private JComboBox<MvsepFieldOption> modelTypeComboBox;
    private JPanel modelTypeRow;
    private JButton modelInfoButton;
    private Map<Integer, MvsepAlgorithmInfo> algorithms = Collections.emptyMap();

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
        addFullWidthComment(I18.get("options_external_tools_mvsep_comment"));
        addText(I18.get("options_external_tools_mvsep_api_token"), "mvsep-api-token");
        addModelChoiceWithInfo();
        addModelTypeRow();
        addChoice(I18.get("options_external_tools_mvsep_output_format"), MvsepOutputFormat.values(),
                  "mvsep-output-format");
        addChoice("Default instrumental link", MvsepInstrumentalDefault.values(), "mvsep-instrumental-default");
        addText(I18.get("options_external_tools_mvsep_poll_interval"), "mvsep-poll-interval");
        addAccountInfoBox();
        loadAlgorithmsAsync();
    }

    private void addModelChoiceWithInfo() {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));

        JLabel lab = new JLabel(I18.get("options_external_tools_mvsep_model"));
        lab.setVerticalAlignment(JLabel.CENTER);
        lab.setHorizontalAlignment(JLabel.LEFT);
        lab.setPreferredSize(new Dimension(getLabelWidth(), 20));

        MvsepModel[] models = MvsepModel.values();
        String[] labels = new String[models.length];
        String[] keys = new String[models.length];
        for (int i = 0; i < models.length; i++) {
            labels[i] = models[i].getLabel();
            keys[i] = models[i].getValue();
        }
        modelComboBox = new JComboBox<>(labels);
        modelComboBox.setMaximumSize(new Dimension(200, 25));
        String savedModel = getProperty("mvsep-model");
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equalsIgnoreCase(savedModel)) {
                modelComboBox.setSelectedIndex(i);
                break;
            }
        }
        modelComboBox.addActionListener(e -> {
            int idx = modelComboBox.getSelectedIndex();
            if (idx >= 0 && idx < keys.length) {
                setProperty("mvsep-model", keys[idx]);
                updateModelTypeOptions(models[idx]);
            }
        });

        modelInfoButton = new JButton("?");
        modelInfoButton.setToolTipText(I18.get("options_external_tools_mvsep_model_info"));
        modelInfoButton.setMargin(new Insets(1, 4, 1, 4));
        modelInfoButton.setEnabled(false);
        modelInfoButton.addActionListener(e -> showAlgorithmInfo(getSelectedModel(models, keys)));

        row.add(lab);
        row.add(modelComboBox);
        row.add(Box.createRigidArea(new Dimension(4, 0)));
        row.add(modelInfoButton);
        row.add(Box.createHorizontalGlue());
        lab.setAlignmentY(Component.TOP_ALIGNMENT);
        modelComboBox.setAlignmentY(Component.TOP_ALIGNMENT);
        modelInfoButton.setAlignmentY(Component.TOP_ALIGNMENT);
        getRight().add(row);
        getRight().add(Box.createRigidArea(new Dimension(0, 2)));
    }

    private void addModelTypeRow() {
        modelTypeRow = new JPanel();
        modelTypeRow.setLayout(new BoxLayout(modelTypeRow, BoxLayout.X_AXIS));

        JLabel lab = new JLabel(I18.get("options_external_tools_mvsep_model_type"));
        lab.setVerticalAlignment(JLabel.CENTER);
        lab.setHorizontalAlignment(JLabel.LEFT);
        lab.setPreferredSize(new Dimension(getLabelWidth(), 20));

        modelTypeComboBox = new JComboBox<>();
        modelTypeComboBox.setMaximumSize(new Dimension(200, 25));
        modelTypeComboBox.addActionListener(e -> {
            MvsepFieldOption selected = (MvsepFieldOption) modelTypeComboBox.getSelectedItem();
            if (selected != null) {
                setProperty("mvsep-model-type", selected.key());
            }
        });

        modelTypeRow.add(lab);
        modelTypeRow.add(modelTypeComboBox);
        modelTypeRow.add(Box.createHorizontalGlue());
        lab.setAlignmentY(Component.TOP_ALIGNMENT);
        modelTypeComboBox.setAlignmentY(Component.TOP_ALIGNMENT);
        modelTypeRow.setVisible(false);
        getRight().add(modelTypeRow);
        getRight().add(Box.createRigidArea(new Dimension(0, 2)));
    }

    private void loadAlgorithmsAsync() {
        SwingWorker<Map<Integer, MvsepAlgorithmInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected Map<Integer, MvsepAlgorithmInfo> doInBackground() {
                return new MvsepAlgorithmCacheService(getProperties()).load();
            }

            @Override
            protected void done() {
                try {
                    algorithms = get();
                    MvsepModel[] models = MvsepModel.values();
                    int idx = modelComboBox.getSelectedIndex();
                    if (idx >= 0 && idx < models.length) {
                        updateModelTypeOptions(models[idx]);
                    }
                } catch (Exception ignored) {
                }
            }
        };
        worker.execute();
    }

    private void updateModelTypeOptions(MvsepModel model) {
        MvsepAlgorithmInfo info = algorithms.get(model.getSepType());
        MvsepAlgorithmField field = info != null ? info.modelTypeField() : null;

        modelInfoButton.setEnabled(info != null && StringUtils.isNotBlank(info.descriptionHtml()));

        if (field == null || field.options().isEmpty()) {
            modelTypeRow.setVisible(false);
            return;
        }

        // Temporarily disable action listener to avoid NPE from removeAllItems() firing events
        ActionListener[] listeners = modelTypeComboBox.getActionListeners();
        for (ActionListener l : listeners) {
            modelTypeComboBox.removeActionListener(l);
        }
        modelTypeComboBox.removeAllItems();
        for (MvsepFieldOption option : field.options()) {
            modelTypeComboBox.addItem(option);
        }
        for (ActionListener l : listeners) {
            modelTypeComboBox.addActionListener(l);
        }

        String savedKey = getProperty("mvsep-model-type");
        MvsepFieldOption toSelect = field.preferredOption(savedKey);
        if (toSelect != null) {
            modelTypeComboBox.setSelectedItem(toSelect);
        } else if (modelTypeComboBox.getItemCount() > 0) {
            modelTypeComboBox.setSelectedIndex(0);
        }

        modelTypeRow.setVisible(true);
        getRight().revalidate();
        getRight().repaint();
    }

    private MvsepModel getSelectedModel(MvsepModel[] models, String[] keys) {
        int idx = modelComboBox.getSelectedIndex();
        if (idx < 0 || idx >= models.length) {
            return null;
        }
        return models[idx];
    }

    private void showAlgorithmInfo(MvsepModel model) {
        if (model == null) {
            return;
        }
        MvsepAlgorithmInfo info = algorithms.get(model.getSepType());
        if (info == null || StringUtils.isBlank(info.descriptionHtml())) {
            return;
        }
        JEditorPane editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setOpaque(false);
        javax.swing.text.html.HTMLEditorKit kit = new javax.swing.text.html.HTMLEditorKit();
        String uiFont = editorPane.getFont().getFamily();
        int uiSize = editorPane.getFont().getSize();
        kit.getStyleSheet().addRule(
                "body { font-family: '" + uiFont + "', sans-serif; font-size: " + uiSize + "pt; margin: 4px; }"
                + "p { margin-top: 4px; margin-bottom: 4px; }"
        );
        editorPane.setEditorKit(kit);
        editorPane.setText(info.descriptionHtml());
        JScrollPane scroll = new JScrollPane(editorPane);
        scroll.setPreferredSize(new Dimension(500, 300));
        JOptionPane.showMessageDialog(this,
                                      scroll,
                                      model.getLabel(),
                                      JOptionPane.INFORMATION_MESSAGE);
    }

    private void addAccountInfoBox() {
        JTextArea info = new JTextArea(6, 40);
        info.setEditable(false);
        info.setLineWrap(true);
        info.setWrapStyleWord(true);
        info.setOpaque(false);
        info.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        info.setText("Loading MVSEP account information...");

        JPanel row = new JPanel(new BorderLayout());
        row.add(info, BorderLayout.CENTER);
        getRight().add(row);
        getRight().add(Box.createRigidArea(new Dimension(0, 6)));

        String apiToken = getProperty("mvsep-api-token");
        if (apiToken == null || apiToken.isBlank()) {
            info.setText("No MVSEP API token configured.\n\nConfigure the token above to show account, queue and premium-minute information.");
            return;
        }

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                MvsepSeparationService service = new MvsepSeparationService(getProperties());
                try {
                    MvsepAccountInfo accountInfo = service.fetchAccountInfo();
                    Integer planQueue = service.fetchPlanQueue();
                    return buildAccountInfoText(accountInfo, planQueue);
                } catch (IOException ex) {
                    return "MVSEP account information could not be loaded.\n\n" + ex.getMessage();
                }
            }

            @Override
            protected void done() {
                try {
                    info.setText(get());
                } catch (Exception ex) {
                    info.setText("MVSEP account information could not be loaded.\n\n" + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private String buildAccountInfoText(MvsepAccountInfo accountInfo, Integer planQueue) {
        if (accountInfo == null) {
            return "MVSEP account information is currently unavailable.";
        }

        StringBuilder text = new StringBuilder();
        text.append(accountInfo.hasPremiumAccess() ? "Plan: Premium" : "Plan: Free");
        if (accountInfo.email() != null && !accountInfo.email().isBlank()) {
            text.append("\nAccount: ").append(accountInfo.email());
        }
        text.append("\nPremium minutes available: ").append(accountInfo.premiumMinutes());
        text.append("\nCurrent queue (account): ").append(accountInfo.currentQueue());
        if (planQueue != null) {
            text.append("\nCurrent queue (plan): ").append(planQueue);
        }
        text.append("\n\nNote: MVSEP currently exposes premium minutes and queue information, but no exact remaining count for free separations.");
        return text.toString();
    }
}
