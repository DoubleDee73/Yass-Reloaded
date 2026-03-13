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

import yass.I18;
import yass.integration.separation.mvsep.MvsepAccountInfo;
import yass.integration.separation.mvsep.MvsepInstrumentalDefault;
import yass.integration.separation.mvsep.MvsepModel;
import yass.integration.separation.mvsep.MvsepOutputFormat;
import yass.integration.separation.mvsep.MvsepSeparationService;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class MvsepPanel extends OptionsPanel {

    private static final long serialVersionUID = 1L;

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
        addChoice(I18.get("options_external_tools_mvsep_model"), MvsepModel.values(), "mvsep-model");
        addChoice(I18.get("options_external_tools_mvsep_output_format"), MvsepOutputFormat.values(),
                  "mvsep-output-format");
        addChoice("Default instrumental link", MvsepInstrumentalDefault.values(), "mvsep-instrumental-default");
        addText(I18.get("options_external_tools_mvsep_poll_interval"), "mvsep-poll-interval");
        addAccountInfoBox();
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
