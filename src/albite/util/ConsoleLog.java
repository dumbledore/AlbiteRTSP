package albite.util;

import java.io.PrintStream;
import java.util.Date;

public final class ConsoleLog extends Log {

    public static final ConsoleLog INSTANCE = new ConsoleLog();

    private ConsoleLog() {
    }

    @Override
    public void log(Priority priority, String tag, String message, Throwable tr) {
        switch (priority) {
            case VERBOSE:
            case DEBUG:
            case INFO:
            case WARNING: {
                log(System.out, priority, tag, message, tr);
                break;
            }

            case ERROR: {
                log(System.err, priority, tag, message, tr);
                break;
            }

            default:
                throw new IllegalArgumentException("bad priority: " + priority.name());
        }
    }

    public void log(PrintStream stream, Priority priority, String tag, String message, Throwable tr) {
        // time I/tag: message
        stream.println(String.format("%d %C/%s: %s", System.currentTimeMillis(), priority.name().charAt(0), tag, message));

        if (tr != null) {
            tr.printStackTrace(stream);
        }
    }
}
