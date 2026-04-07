package yass.input;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class EditorKeyBindingRegistry {
    private final Map<KeyStroke, EditorCommand> singleKeys = new HashMap<>();

    public void bind(KeyStroke keyStroke, EditorCommand command) {
        singleKeys.put(keyStroke, command);
    }

    public EditorCommand get(KeyStroke keyStroke) {
        return singleKeys.get(keyStroke);
    }

    public void clear() {
        singleKeys.clear();
    }
}
