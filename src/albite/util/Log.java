package albite.util;

public abstract class Log {

    public static Log LOG = ConsoleLog.INSTANCE;

    public enum Priority {
        VERBOSE,
        DEBUG,
        INFO,
        WARNING,
        ERROR,
    }

    public void log(Priority priority, String tag, String message) {
        log(priority, tag, message, null);
    }

    public abstract void log(Priority priority, String tag, String message, Throwable tr);

    public static void v(String tag, String msg) {
        LOG.log(Priority.VERBOSE, tag, msg, null);
    }

    public static void d(String tag, String msg) {
        LOG.log(Priority.DEBUG, tag, msg);
    }

    public static void i(String tag, String msg) {
        LOG.log(Priority.INFO, tag, msg);
    }

    public static void w(String tag, String msg) {
        w(tag, msg, null);
    }

    public static void w(String tag, String msg, Throwable tr) {
        LOG.log(Priority.WARNING, tag, msg, tr);
    }

    public static void e(String tag, String msg) {
        e(tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable tr) {
        LOG.log(Priority.ERROR, tag, msg, tr);
    }
}
