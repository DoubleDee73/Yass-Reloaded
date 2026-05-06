package yass;

public final class StartupFlowSupport {
    private StartupFlowSupport() {
    }

    public static boolean shouldContinueAfterLibrarySetup(boolean firstRunLibrarySetup, boolean librarySetupConfirmed) {
        return !firstRunLibrarySetup || librarySetupConfirmed;
    }
}
