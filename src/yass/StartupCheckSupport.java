package yass;

import java.util.function.BooleanSupplier;

public final class StartupCheckSupport {
    private StartupCheckSupport() {
    }

    public static boolean accumulate(boolean currentStore, BooleanSupplier check) {
        return currentStore | check.getAsBoolean();
    }
}
