package app.presence.launcher.mms.vendor;

/** Replaces the vendored library's logger: message contents and addresses must never reach the log, so every call is a no-op. */
public final class Log {
    private Log() {}

    public static void v(String tag, String msg) {}
    public static void v(String tag, String msg, Throwable t) {}
    public static void d(String tag, String msg) {}
    public static void e(String tag, String msg) {}
    public static void e(String tag, String msg, Throwable t) {}
    public static void w(String tag, String msg) {}
    public static void w(String tag, String msg, Throwable t) {}
}
