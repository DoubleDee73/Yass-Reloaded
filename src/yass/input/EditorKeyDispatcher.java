package yass.input;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class EditorKeyDispatcher implements KeyEventDispatcher {
    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private final Supplier<Boolean> editorActive;
    private final Supplier<EditorInputContext> contextSupplier;
    private final EditorKeyBindingRegistry registry;
    private final KeySequenceTracker sequenceTracker;
    private final BooleanSupplier multiPressEscalationEnabled;
    private final Set<Integer> pressedKeyCodes = new HashSet<>();
    private boolean suppressNextTypedTildeShortcut = false;

    public EditorKeyDispatcher(Supplier<Boolean> editorActive,
                               Supplier<EditorInputContext> contextSupplier,
                               EditorKeyBindingRegistry registry,
                               KeySequenceTracker sequenceTracker,
                               BooleanSupplier multiPressEscalationEnabled) {
        this.editorActive = Objects.requireNonNull(editorActive);
        this.contextSupplier = Objects.requireNonNull(contextSupplier);
        this.registry = Objects.requireNonNull(registry);
        this.sequenceTracker = Objects.requireNonNull(sequenceTracker);
        this.multiPressEscalationEnabled = Objects.requireNonNull(multiPressEscalationEnabled);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (!editorActive.get() || e.isConsumed()) {
            return false;
        }
        if (e.getID() != KeyEvent.KEY_PRESSED && e.getID() != KeyEvent.KEY_RELEASED && e.getID() != KeyEvent.KEY_TYPED) {
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
        if (e.getID() == KeyEvent.KEY_TYPED) {
            return dispatchTypedCharacter(e, context);
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
        KeyStroke lookupStroke = normalizeAltGraphDeadKeyStroke(stroke);
        if (e.getID() == KeyEvent.KEY_RELEASED) {
            pressedKeyCodes.remove(e.getKeyCode());
            EditorCommand releaseCommand = registry.get(lookupStroke, 1);
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
            sequenceTracker.record(lookupStroke, nowMs);
        }
        int pressCount = (repeatedPressWithoutRelease || !multiPressEscalationEnabled.getAsBoolean())
                ? 1 : sequenceTracker.countRecentMatches(lookupStroke, nowMs);
        if (isTrackedMultiPressStroke(stroke)) {
            LOGGER.fine("Editor multi-press count: keyCode=" + e.getKeyCode()
                    + ", modifiers=" + stroke.getModifiers()
                    + ", pressCount=" + pressCount
                    + ", repeatedPressWithoutRelease=" + repeatedPressWithoutRelease
                    + ", focusArea=" + context.focusArea());
        }
        EditorCommand command = registry.get(lookupStroke, pressCount);
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
        if (e.getID() == KeyEvent.KEY_PRESSED && isDeadTildeKeyCode(lookupStroke.getKeyCode())) {
            suppressNextTypedTildeShortcut = true;
        }
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
        if (isAltGraphDeadKeyStroke(stroke)) {
            return true;
        }
        int modifiers = stroke.getModifiers();
        return !hasCommandModifier(modifiers);
    }

    private boolean hasCommandModifier(int modifiers) {
        return (modifiers & (KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK | KeyEvent.META_DOWN_MASK)) != 0;
    }

    private KeyStroke normalizeAltGraphDeadKeyStroke(KeyStroke stroke) {
        if (!isAltGraphDeadKeyStroke(stroke)) {
            return stroke;
        }
        return KeyStroke.getKeyStroke(stroke.getKeyCode(), 0, stroke.isOnKeyRelease());
    }

    private boolean isAltGraphDeadKeyStroke(KeyStroke stroke) {
        if (stroke == null || !isDeadTildeKeyCode(stroke.getKeyCode())) {
            return false;
        }
        int modifiers = stroke.getModifiers();
        boolean altGraph = (modifiers & KeyEvent.ALT_GRAPH_DOWN_MASK) != 0;
        boolean ctrlAlt = (modifiers & KeyEvent.CTRL_DOWN_MASK) != 0
                && (modifiers & KeyEvent.ALT_DOWN_MASK) != 0;
        return altGraph || ctrlAlt;
    }

    private boolean isDeadTildeKeyCode(int keyCode) {
        return keyCode == KeyEvent.VK_DEAD_TILDE || keyCode == KeyEvent.VK_DEAD_CIRCUMFLEX;
    }

    private boolean dispatchTypedCharacter(KeyEvent e, EditorInputContext context) {
        char keyChar = e.getKeyChar();
        if (keyChar == KeyEvent.CHAR_UNDEFINED || Character.isISOControl(keyChar)) {
            return false;
        }
        if (suppressNextTypedTildeShortcut) {
            suppressNextTypedTildeShortcut = false;
            if (keyChar == '~') {
                e.consume();
                return true;
            }
        }
        if (hasBlockingCharacterShortcutModifier(e.getModifiersEx())) {
            return false;
        }
        if (!isCharacterShortcutContext(context)) {
            if (context.isTypingContext()) {
                sequenceTracker.clear();
            }
            return false;
        }
        EditorCommand command = registry.get(keyChar);
        if (command == null || !command.isEnabled(context)) {
            return false;
        }
        command.execute(context);
        e.consume();
        return true;
    }

    private boolean hasBlockingCharacterShortcutModifier(int modifiers) {
        int commandModifiers = modifiers & (KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK | KeyEvent.META_DOWN_MASK);
        if ((modifiers & KeyEvent.ALT_GRAPH_DOWN_MASK) != 0) {
            commandModifiers &= ~(KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK);
        }
        return commandModifiers != 0;
    }

    private boolean isCharacterShortcutContext(EditorInputContext context) {
        return (context.focusArea() == FocusArea.SHEET || context.focusArea() == FocusArea.LYRICS_VIEW)
                && !context.songHeaderEditing();
    }
}
