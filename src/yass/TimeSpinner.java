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

package yass;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Description of the Class
 *
 * @author Saruta
 */
public class TimeSpinner extends JPanel {
    /**
     * Description of the Field
     */
    public final static int POSITIVE = 1, NEGATIVE = 2;
    @Serial
    private static final long serialVersionUID = -1220107624676188602L;
    private double duration;
    private final JSpinner msSpinner;
    private final SpinnerNumberModel msModel;
    private final boolean decimalMode;
    private JLabel lab1 = null, lab2 = null;


    /**
     * Constructor for the TimeSpinner object
     *
     * @param label Description of the Parameter
     * @param init  Description of the Parameter
     * @param dur   Description of the Parameter
     */
    public TimeSpinner(String label, int init, int dur) {
        this(label, init, dur, null);
    }


    /**
     * Constructor for the TimeSpinner object
     *
     * @param label Description of the Parameter
     * @param init  Description of the Parameter
     * @param dur   Description of the Parameter
     * @param type  Description of the Parameter
     */
    public TimeSpinner(String label, int init, int dur, int type) {
        this(label, init, dur, null, type);
    }


    /**
     * Constructor for the TimeSpinner object
     *
     * @param dur   Description of the Parameter
     * @param init  Description of the Parameter
     * @param label Description of the Parameter
     * @param mss   Description of the Parameter
     */
    public TimeSpinner(String label, int init, int dur, String mss) {
        this(label, init, dur, mss, POSITIVE);
    }


    /**
     * Constructor for the TimeSpinner object
     *
     * @param label Description of the Parameter
     * @param init  Description of the Parameter
     * @param dur   Description of the Parameter
     * @param mss   Description of the Parameter
     * @param type  Description of the Parameter
     */
    public TimeSpinner(String label, int init, int dur, String mss, int type) {
        this(label, Integer.valueOf(init), Integer.valueOf(dur), Integer.valueOf(10), mss, type, false);
    }

    public TimeSpinner(String label, double init, double dur, double step) {
        this(label, init, dur, step, null, POSITIVE);
    }

    public TimeSpinner(String label, double init, double dur, double step, String mss) {
        this(label, init, dur, step, mss, POSITIVE);
    }

    public TimeSpinner(String label, double init, double dur, double step, String mss, int type) {
        this(label, Double.valueOf(init), Double.valueOf(dur), Double.valueOf(step), mss, type, true);
    }

    private TimeSpinner(String label, Number init, Number dur, Number step, String mss, int type, boolean decimalMode) {
        duration = dur.doubleValue();
        this.decimalMode = decimalMode;

        if (decimalMode) {
            double minimum = type == POSITIVE ? 0d : -duration;
            double maximum = duration;
            msModel = new SpinnerNumberModel(init.doubleValue(), minimum, maximum, step.doubleValue());
        } else {
            int maximum = (int) Math.round(duration);
            int minimum = type == POSITIVE ? 0 : -maximum;
            msModel = new SpinnerNumberModel(init.intValue(), minimum, maximum, step.intValue());
        }
        msSpinner = new JSpinner(msModel);
        if (decimalMode) {
            msSpinner.setEditor(new JSpinner.NumberEditor(msSpinner, "0.0"));
        }

        JTextField tf = ((JSpinner.DefaultEditor) msSpinner.getEditor()).getTextField();
        tf.setColumns(Math.max(4, String.valueOf(dur).length()));
        tf.setHorizontalAlignment(JTextField.RIGHT);
        tf.addKeyListener(
                new KeyAdapter() {
                    public void keyTyped(KeyEvent e) {
                        char c = e.getKeyChar();
                        if (Character.isDigit(c) || c == ',' || c == '.') {
                            return;
                        }
                        if (c == '-' && ((Number) msModel.getMinimum()).intValue() < 0 && tf.getCaretPosition() == 0 && !tf.getText().contains("-")) {
                            return;
                        }
                        e.consume();
                    }
                });

        JPanel boxPanel = new JPanel();
        boxPanel.setLayout(new BoxLayout(boxPanel, BoxLayout.LINE_AXIS));
        boxPanel.setOpaque(false);

        if (label != null) {
            lab1 = new JLabel(label, JLabel.LEFT);
            lab1.setOpaque(false);
            boxPanel.add(lab1);
            boxPanel.add(Box.createRigidArea(new Dimension(5, 0)));
        }

        boxPanel.add(msSpinner);
        msSpinner.setToolTipText(I18.get("time_spinner_tip"));

        if (mss != null) {
            lab2 = new JLabel(mss, JLabel.LEFT);
            lab2.setOpaque(false);
            boxPanel.add(Box.createRigidArea(new Dimension(5, 0)));
            boxPanel.add(lab2);
            boxPanel.add(Box.createRigidArea(new Dimension(5, 0)));
        }

        setLayout(new BorderLayout());
        setOpaque(false);
        add("Center", boxPanel);
    }

    /**
     * Sets the enabled attribute of the TimeSpinner object
     *
     * @param onoff The new enabled value
     */
    public void setEnabled(boolean onoff) {
        msSpinner.setEnabled(onoff);
        if (lab1 != null) {
            lab1.setEnabled(onoff);
        }
        if (lab2 != null) {
            lab2.setEnabled(onoff);
        }
    }

    /**
     * Sets the toolTipText attribute of the TimeSpinner object
     *
     * @return The spinner value
     */
    public JSpinner getSpinner() {
        return msSpinner;
    }

    public void setLabelSize(Dimension dimension) {
        Dimension adjustedDimension = new Dimension((int)dimension.getWidth() - 10, (int)dimension.getHeight());
        if (lab1 != null) {
            lab1.setSize(adjustedDimension);
            lab1.setMinimumSize(adjustedDimension);
            lab1.setPreferredSize(adjustedDimension);
        }
    }

    public List<JLabel> getLabels() {
        List<JLabel> labels = new ArrayList<>();
        if (lab1 != null) {
            labels.add(lab1);
        }
        if (lab2 != null) {
            labels.add(lab2);
        }
        return labels;
    }
    
    public JTextField getTextField() {
        if (msSpinner != null) {
            return ((JSpinner.DefaultEditor) msSpinner.getEditor()).getTextField(); 
        }
        return null;
    }
    public void setSpinnerSize(Dimension size) {
        ((JSpinner.DefaultEditor) msSpinner.getEditor()).getTextField().setColumns(5);
        ((JSpinner.DefaultEditor) msSpinner.getEditor()).getTextField().setSize(size);
        ((JSpinner.DefaultEditor) msSpinner.getEditor()).getTextField().setMinimumSize(size);
        ((JSpinner.DefaultEditor) msSpinner.getEditor()).getTextField().setPreferredSize(size);
    }
    /**
     * Gets the time attribute of the TimeSpinner object
     *
     * @return The time value
     */
    public int getTime() {
        return (int) Math.round(((Number) msSpinner.getValue()).doubleValue());
    }

    public double getTimeDouble() {
        return ((Number) msSpinner.getValue()).doubleValue();
    }

    /**
     * Sets the time attribute of the TimeSpinner object
     *
     * @param t The new time value
     */
    public void setTime(int t) {
        setTime((double) t);
    }

    public void setTime(double t) {
        double current = ((Number) msSpinner.getValue()).doubleValue();
        if (Double.compare(t, current) == 0) {
            return;
        }
        msSpinner.setValue(decimalMode ? t : (int) Math.round(t));
    }

    /**
     * Sets the duration attribute of the TimeSpinner object
     *
     * @param d The new duration value
     */
    public void setDuration(int d) {
        setDuration((double) d);
    }

    public void setDuration(double d) {
        if (Double.compare(d, duration) == 0) {
            return;
        }
        duration = d;
        msModel.setMaximum(decimalMode ? d : (int) Math.round(duration));
    }

    @Override
    public void setBackground(Color bg) {
        if (msSpinner == null) {
            return;
        }
        ((JSpinner.DefaultEditor)msSpinner.getEditor()).getTextField().setForeground(bg);
        msSpinner.getEditor().repaint();
    }

    @Override
    public void setForeground(Color fg) {
        if (msSpinner == null) {
            return;
        }
        ((JSpinner.DefaultEditor)msSpinner.getEditor()).getTextField().setBackground(fg);
        msSpinner.getEditor().repaint();
    }
}
