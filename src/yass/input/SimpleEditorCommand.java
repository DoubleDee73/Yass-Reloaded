package yass.input;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SimpleEditorCommand implements EditorCommand {
    private final String id;
    private final Predicate<EditorInputContext> enabled;
    private final Consumer<EditorInputContext> executor;

    public SimpleEditorCommand(String id,
                               Predicate<EditorInputContext> enabled,
                               Consumer<EditorInputContext> executor) {
        this.id = Objects.requireNonNull(id);
        this.enabled = Objects.requireNonNull(enabled);
        this.executor = Objects.requireNonNull(executor);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean isEnabled(EditorInputContext context) {
        return enabled.test(context);
    }

    @Override
    public void execute(EditorInputContext context) {
        executor.accept(context);
    }
}
