package yass.input;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class EditorKeyDispatcher implements KeyEventDispatcher {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private final Supplier<Boolean> editorActive;
    private final Supplier<EditorInputContext> contextSupplier;
    private final EditorKeyBindingRegistry registry;
    private final KeySequenceTracker sequenceTracker;
    private final Set<Integer> pressedKeyCodes = new HashSet<>();

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
        if (isModifierOnlyKey(e)) {
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
        if (MenuSelectionManager.defaultManager().getSelectedPath().length > 0) {
            return false;
        }

        EditorInputContext context = contextSupplier.get();
        if (context == null) {
            return false;
        }
        if (context.recording()) {
            sequenceTracker.clear();
            pressedKeyCodes.clear();
            return false;
        }

        KeyStroke stroke = KeyStroke.getKeyStrokeForEvent(e);
        if (isBlockedByTypingContext(context, stroke)) {
            if (isTrackedMultiPressStroke(stroke)) {
                LOGGER.fine("Editor multi-press blocked in typing context: keyCode=" + e.getKeyCode()
                        + ", modifiers=" + stroke.getModifiers()
                        + ", focusArea=" + context.focusArea());
            }
            return false;
        }
        if (e.getID() == KeyEvent.KEY_RELEASED) {
            pressedKeyCodes.remove(e.getKeyCode());
            EditorCommand releaseCommand = registry.get(stroke, 1);
            if (releaseCommand == null || !releaseCommand.isEnabled(context)) {
                return false;
            }
            releaseCommand.execute(context);
            e.consume();
            return true;
        }

        long nowMs = System.currentTimeMillis();
        boolean repeatedPressWithoutRelease = !pressedKeyCodes.add(e.getKeyCode());
        if (!repeatedPressWithoutRelease) {
            sequenceTracker.record(stroke, nowMs);
        }
        int pressCount = repeatedPressWithoutRelease ? 1 : sequenceTracker.countRecentMatches(stroke, nowMs);
        if (isTrackedMultiPressStroke(stroke)) {
            LOGGER.fine("Editor multi-press count: keyCode=" + e.getKeyCode()
                    + ", modifiers=" + stroke.getModifiers()
                    + ", pressCount=" + pressCount
                    + ", repeatedPressWithoutRelease=" + repeatedPressWithoutRelease
                    + ", focusArea=" + context.focusArea());
        }
        EditorCommand command = registry.get(stroke, pressCount);
        if (command == null) {
            if (context.isTypingContext()) {
                sequenceTracker.clear();
            }
            if (isTrackedMultiPressStroke(stroke)) {
                LOGGER.fine("Editor multi-press has no command: keyCode=" + e.getKeyCode()
                        + ", modifiers=" + stroke.getModifiers()
                        + ", pressCount=" + pressCount);
            }
            return false;
        }
        if (pressCount > 1) {
            LOGGER.fine("Editor multi-press shortcut detected: keyCode=" + e.getKeyCode()
                    + ", modifiers=" + stroke.getModifiers()
                    + ", pressCount=" + pressCount
                    + ", command=" + command.id());
        }
        if (!command.isEnabled(context)) {
            return false;
        }
        command.execute(context);
        e.consume();
        return true;
    }

    private boolean isModifierOnlyKey(KeyEvent e) {
        return e.getKeyCode() == KeyEvent.VK_SHIFT
                || e.getKeyCode() == KeyEvent.VK_CONTROL
                || e.getKeyCode() == KeyEvent.VK_ALT
                || e.getKeyCode() == KeyEvent.VK_ALT_GRAPH
                || e.getKeyCode() == KeyEvent.VK_META;
    }

    private boolean isTrackedMultiPressStroke(KeyStroke stroke) {
        return stroke.getKeyCode() == KeyEvent.VK_UP && stroke.getModifiers() == KeyEvent.SHIFT_DOWN_MASK
                || stroke.getKeyCode() == KeyEvent.VK_DOWN && stroke.getModifiers() == KeyEvent.SHIFT_DOWN_MASK;
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
