package yass.input;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class EditorKeyBindingRegistry {
    private final Map<KeyStroke, Map<Integer, EditorCommand>> bindings = new HashMap<>();
    private final Map<Character, EditorCommand> charBindings = new HashMap<>();

    public void bind(KeyStroke keyStroke, EditorCommand command) {
        bind(keyStroke, 1, command);
    }

    public void bind(KeyStroke keyStroke, int pressCount, EditorCommand command) {
        bindings.computeIfAbsent(keyStroke, ignored -> new HashMap<>()).put(pressCount, command);
    }

    public void bind(char keyChar, EditorCommand command) {
        charBindings.put(keyChar, command);
    }

    public EditorCommand get(KeyStroke keyStroke, int pressCount) {
        Map<Integer, EditorCommand> commands = bindings.get(keyStroke);
        if (commands == null) {
            return null;
        }
        EditorCommand command = commands.get(pressCount);
        if (command != null) {
            return command;
        }
        int fallbackCount = 1;
        for (Integer configuredCount : commands.keySet()) {
            if (configuredCount <= pressCount && configuredCount > fallbackCount) {
                fallbackCount = configuredCount;
            }
        }
        return commands.get(fallbackCount);
    }

    public EditorCommand get(char keyChar) {
        return charBindings.get(keyChar);
    }

    public void clear() {
        bindings.clear();
        charBindings.clear();
    }
}
