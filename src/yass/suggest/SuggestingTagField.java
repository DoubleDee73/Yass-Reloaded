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

package yass.suggest;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * A text field component that displays input as tags and provides auto-suggestions.
 */
public class SuggestingTagField extends JPanel {

    private final List<String> tags = new ArrayList<>();
    private final SuggestTextField inputField = new SuggestTextField();
    private final JPopupMenu suggestionPopup = new JPopupMenu();
    private final Function<String, List<String>> suggestionProvider;
    private final List<ChangeListener> listeners = new ArrayList<>();
    private final JPanel tagsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));

    private Color tagFontColor;
    private Color tagBackgroundColor;

    public SuggestingTagField(Function<String, List<String>> suggestionProvider) {
        this(suggestionProvider, null, null);
    }
    
    public SuggestingTagField(Function<String, List<String>> suggestionProvider,
                              Color tagFontColor, Color tagBackgroundColor) {
        this.suggestionProvider = suggestionProvider;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                new EmptyBorder(1, 1, 1, 1)
        ));
        setBackground(Color.WHITE);
        this.tagFontColor = tagFontColor;
        this.tagBackgroundColor = tagBackgroundColor;
        tagsPanel.setOpaque(false);
        inputField.setBorder(null);
        inputField.setOpaque(false);
        inputField.setSuggestionPopup(suggestionPopup);

        // Add the input field to the tags panel so it flows with the tags
        tagsPanel.add(inputField);
        add(tagsPanel, BorderLayout.CENTER);

        setupInputFieldListeners();
        setupFocusHandling();
    }

    private void setupInputFieldListeners() {
        inputField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                // Triggered when typing characters
                SwingUtilities.invokeLater(() -> updateSuggestions());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                // Triggered when deleting characters (e.g., backspace, delete, cut)
                SwingUtilities.invokeLater(() -> updateSuggestions());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // Typically for style changes, but good to have for robustness
                SwingUtilities.invokeLater(() -> updateSuggestions());
            }
        });

        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ENTER:
                    case KeyEvent.VK_COMMA:
                        MenuElement[] selectedPath = MenuSelectionManager.defaultManager().getSelectedPath();
                        if (suggestionPopup.isVisible() && selectedPath.length > 1 && selectedPath[selectedPath.length - 1] instanceof JMenuItem) {
                            JMenuItem selectedItem = (JMenuItem) selectedPath[selectedPath.length - 1];
                            selectedItem.doClick();
                        } else {
                            addTagFromInput();
                        }
                        e.consume();
                        break;
                    case KeyEvent.VK_BACK_SPACE:
                        if (inputField.getText().isEmpty() && !tags.isEmpty()) {
                            removeTag(tags.get(tags.size() - 1));
                        }
                        break;
                    case KeyEvent.VK_UP:
                        if (suggestionPopup.isVisible() && suggestionPopup.getComponentCount() > 0) {
                            MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
                            if (path.length > 1) { // An item is selected
                                int currentIndex = getSelectedItemIndex(path);
                                if (currentIndex > 0) {
                                    MenuSelectionManager.defaultManager().setSelectedPath(
                                        new MenuElement[]{suggestionPopup, (MenuElement) suggestionPopup.getComponent(currentIndex - 1)}
                                    );
                                } else { // We are at the top, so clear selection
                                    MenuSelectionManager.defaultManager().clearSelectedPath();
                                }
                            } else {
                                // If nothing is selected, select the last item
                                MenuSelectionManager.defaultManager().setSelectedPath(
                                    new MenuElement[]{suggestionPopup, (MenuElement) suggestionPopup.getComponent(suggestionPopup.getComponentCount() - 1)}
                                );
                            }
                            e.consume();
                        }
                        break;
                    case KeyEvent.VK_DOWN:
                        if (suggestionPopup.isVisible() && suggestionPopup.getComponentCount() > 0) {
                            MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
                            if (path.length <= 1) { // Nothing is selected, select the first item
                                MenuSelectionManager.defaultManager().setSelectedPath(
                                    new MenuElement[]{suggestionPopup, (MenuElement) suggestionPopup.getComponent(0)}
                                );
                            } else { // An item is selected, move to the next one
                                int currentIndex = getSelectedItemIndex(path);
                                if (currentIndex < suggestionPopup.getComponentCount() - 1) {
                                    MenuSelectionManager.defaultManager().setSelectedPath(
                                        new MenuElement[]{suggestionPopup, (MenuElement) suggestionPopup.getComponent(currentIndex + 1)}
                                    );
                                }
                            }
                            e.consume();
                        }
                        break;
                    case KeyEvent.VK_ESCAPE:
                        if (suggestionPopup.isVisible()) {
                            suggestionPopup.setVisible(false);
                            e.consume();
                        }
                        break;
                }
            }
        });
    }

    private int getSelectedItemIndex(MenuElement[] path) {
        if (path.length <= 1) return -1;
        Component currentItem = (Component) path[path.length - 1];
        for (int i = 0; i < suggestionPopup.getComponentCount(); i++) {
            if (suggestionPopup.getComponent(i) == currentItem) {
                return i;
            }
        }
        return -1;
    }

    private void setupFocusHandling() {
        // When clicking anywhere in the panel, focus the input field
        this.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                inputField.requestFocusInWindow();
            }
        });

        // Make the input field grow to fill available space
        inputField.addFocusListener(new FocusAdapter() {
            private Dimension originalPreferredSize;

            @Override
            public void focusGained(FocusEvent e) {
                if (originalPreferredSize == null) {
                    originalPreferredSize = SuggestingTagField.this.getPreferredSize();
                    // If preferred size is not set, use current size as a fallback
                    int requiredHeight = (int)(Math.ceil(getRequiredHeight() / originalPreferredSize.getHeight()) *
                            originalPreferredSize.getHeight());
                    SuggestingTagField.this.setPreferredSize(new Dimension(originalPreferredSize.width, requiredHeight));
                    SuggestingTagField.this.revalidate();
                }
                updateInputFieldSize();
            }

            @Override
            public void focusLost(FocusEvent e) {
                // Don't shrink if focus is going to the suggestion popup
                // or to another component within this field (like a tag's close button).
                Component oppositeComponent = e.getOppositeComponent();

                // If the focus is going to a component that is a child of this field (like a tag's close button)
                // or the suggestion popup, we don't want to shrink.
                boolean focusStaysInside = false;
                if (oppositeComponent != null) {
                    focusStaysInside = SwingUtilities.isDescendingFrom(oppositeComponent, SuggestingTagField.this) ||
                                       SwingUtilities.isDescendingFrom(oppositeComponent, suggestionPopup);
                }
                if (focusStaysInside) {
                    return;
                }

                if (originalPreferredSize != null) {
                    SuggestingTagField.this.setPreferredSize(originalPreferredSize);
                    SuggestingTagField.this.revalidate();
                    originalPreferredSize = null; // Reset for the next focus gain
                }
                // Also add any pending tag when focus is lost
                addTagFromInput();
            }
        });        this.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent evt) {
                updateInputFieldSize();
            }
        });
    }

    /**
     * Calculates the required height to display all tags without clipping.
     * It simulates the FlowLayout wrapping behavior.
     * @return The required height in pixels.
     */
    private int getRequiredHeight() {
        FlowLayout layout = (FlowLayout) tagsPanel.getLayout();
        int hgap = layout.getHgap();
        int vgap = layout.getVgap();
        // Use the actual width of the component for calculation
        int panelWidth = this.getWidth();

        // If panel is not yet displayed, we can't calculate, return current height
        if (panelWidth <= 0) {
            return this.getPreferredSize().height;
        }

        int rowCount = 1;
        int currentLineWidth = 0;
        // Use the input field's height as a good reference for row height
        int rowHeight = inputField.getPreferredSize().height;

        for (Component comp : tagsPanel.getComponents()) {
            int compWidth = comp.getPreferredSize().width;
            if (currentLineWidth > 0 && currentLineWidth + hgap + compWidth > panelWidth) {
                // Component doesn't fit, wrap to a new line
                rowCount++;
                currentLineWidth = compWidth;
            } else {
                // Component fits, add it to the current line
                currentLineWidth += (currentLineWidth > 0 ? hgap : 0) + compWidth;
            }
        }

        Insets insets = getInsets();
        return rowCount * rowHeight + Math.max(0, rowCount - 1) * vgap + insets.top + insets.bottom + vgap * 2;
    }

    private void addTagFromInput() {
        String text = inputField.getText().trim();
        if (!text.isEmpty()) {
            addTag(text);
            inputField.setText("");
            suggestionPopup.setVisible(false);
        }
    }

    private void updateSuggestions() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            suggestionPopup.setVisible(false);
            return;
        }

        List<String> suggestions = suggestionProvider.apply(text);
        suggestionPopup.removeAll();

        if (suggestions.isEmpty()) {
            suggestionPopup.setVisible(false);
            return;
        }

        for (String suggestion : suggestions) {
            JMenuItem item = new JMenuItem(suggestion);
            item.addActionListener(e -> {
                addTag(suggestion);
                inputField.setText("");
                suggestionPopup.setVisible(false);
            });
            suggestionPopup.add(item);
        }

        if (!suggestionPopup.isVisible()) {
            suggestionPopup.setFocusable(false); // MUST be before show()
            try {
                Rectangle r = inputField.modelToView(inputField.getCaretPosition());
                // Use inputField as invoker and show popup relative to it
                suggestionPopup.show(inputField, r.x, r.y + r.height);
            } catch (javax.swing.text.BadLocationException e) {
                // Fallback
                suggestionPopup.show(inputField, 0, inputField.getHeight());
            }
        }
        // Force focus back to input field reliably
        SwingUtilities.invokeLater(() -> inputField.requestFocusInWindow());
    }

    private void updateInputFieldSize() {
        int totalTagWidth = 0;
        for (Component comp : tagsPanel.getComponents()) {
            if (comp instanceof TagComponent) {
                totalTagWidth += comp.getPreferredSize().width + ((FlowLayout) tagsPanel.getLayout()).getHgap();
            }
        }
        int availableWidth = tagsPanel.getWidth() - totalTagWidth;
        int minWidth = 100; // Minimum width for the input field
        inputField.setPreferredSize(new Dimension(Math.max(minWidth, availableWidth - 10), inputField.getPreferredSize().height));
        tagsPanel.revalidate();
    }

    private void addTag(String tag) {
        if (tag == null || tag.trim().isEmpty() || tags.contains(tag)) {
            return;
        }
        tags.add(tag);
        rebuildTagsPanel();
    }
    
    public void addChangeListener(ChangeListener listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        listeners.remove(listener);
    }

    protected void fireStateChanged() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : listeners) {
            listener.stateChanged(event);
        }
    }

    private void removeTag(String tag) {
        tags.remove(tag);
        rebuildTagsPanel();
    }

    private void rebuildTagsPanel() {
        // Remember focus
        boolean hadFocus = inputField.hasFocus();

        tagsPanel.removeAll();
        for (String tag : tags) {
            if (tagFontColor == null || tagBackgroundColor == null) {
                tagsPanel.add(new TagComponent(tag, this::removeTag));
            } else {
                tagsPanel.add(new TagComponent(tag, this::removeTag, tagFontColor, tagBackgroundColor));
            }
        }
        tagsPanel.add(inputField);
        updateInputFieldSize();
        revalidate();
        repaint();

        // Restore focus
        if (hadFocus) {
            SwingUtilities.invokeLater(inputField::requestFocusInWindow);
        }
        
        fireStateChanged();
    }

    public void setText(String commaSeparatedTags) {
        tags.clear();
        if (commaSeparatedTags != null && !commaSeparatedTags.trim().isEmpty()) {
            Arrays.stream(commaSeparatedTags.split(","))
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .forEach(this.tags::add);
        }
        rebuildTagsPanel();
    }

    public String getText() {
        return String.join(", ", tags);
    }

    public List<String> getTags() {
        return new ArrayList<>(tags);
    }
}