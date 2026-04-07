package yass.input;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.function.Supplier;

public class EditorKeyDispatcher implements KeyEventDispatcher {
    private final Supplier<Boolean> editorActive;
    private final Supplier<EditorInputContext> contextSupplier;
    private final EditorKeyBindingRegistry registry;
    private final KeySequenceTracker sequenceTracker;

    public EditorKeyDispatcher(Supplier<Boolean> editorActive,
                               Supplier<EditorInputContext> contextSupplier,
                               EditorKeyBindingRegistry registry,
                               KeySequenceTracker sequenceTracker) {
        this.editorActive = Objects.requireNonNull(editorActive);
        this.contextSupplier = Objects.requireNonNull(contextSupplier);
        this.registry = Objects.requireNonNull(registry);
        this.sequenceTracker = Objects.requireNonNull(sequenceTracker);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (!editorActive.get() || e.isConsumed()) {
            return false;
        }
        if (e.getID() != KeyEvent.KEY_PRESSED && e.getID() != KeyEvent.KEY_RELEASED) {
            return false;
        }

        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner == null) {
            return false;
        }
        Window window = SwingUtilities.getWindowAncestor(focusOwner);
        if (window instanceof JDialog) {
            return false;
        }

        EditorInputContext context = contextSupplier.get();
        if (context == null) {
            return false;
        }

        KeyStroke stroke = KeyStroke.getKeyStrokeForEvent(e);
        if (isBlockedByTypingContext(context, stroke)) {
            return false;
        }
        EditorCommand command = registry.get(stroke);
        if (command == null) {
            if (context.isTypingContext()) {
                sequenceTracker.clear();
            }
            return false;
        }
        if (!command.isEnabled(context)) {
            return false;
        }

        if (e.getID() == KeyEvent.KEY_PRESSED) {
            sequenceTracker.record(stroke, System.currentTimeMillis());
        }
        command.execute(context);
        e.consume();
        return true;
    }

    private boolean isBlockedByTypingContext(EditorInputContext context, KeyStroke stroke) {
        if (!context.isTypingContext()) {
            return false;
        }
        int modifiers = stroke.getModifiers();
        boolean hasCommandModifier = (modifiers & (KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK | KeyEvent.META_DOWN_MASK)) != 0;
        return !hasCommandModifier;
    }
}
