package yass.input;

import javax.swing.*;
import java.util.ArrayDeque;
import java.util.Deque;

public class KeySequenceTracker {
    private final long timeoutMs;
    private final Deque<TimedStroke> recent = new ArrayDeque<>();

    public KeySequenceTracker(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public void record(KeyStroke stroke, long nowMs) {
        cleanup(nowMs);
        recent.addLast(new TimedStroke(stroke, nowMs));
    }

    public void clear() {
        recent.clear();
    }

    private void cleanup(long nowMs) {
        while (!recent.isEmpty() && nowMs - recent.peekFirst().timestampMs() > timeoutMs) {
            recent.removeFirst();
        }
    }

    private record TimedStroke(KeyStroke stroke, long timestampMs) {
    }
}
