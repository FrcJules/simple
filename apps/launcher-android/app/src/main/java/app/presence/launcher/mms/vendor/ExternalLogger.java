package app.presence.launcher.mms.vendor;

/** Replaces the vendored library's external logger (no-op, see {@link Log}). */
public final class ExternalLogger {
    private ExternalLogger() {}

    public static void logMessage(String tag, String message) {}
}
