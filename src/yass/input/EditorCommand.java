package yass.input;

public interface EditorCommand {
    String id();

    boolean isEnabled(EditorInputContext context);

    void execute(EditorInputContext context);
}
