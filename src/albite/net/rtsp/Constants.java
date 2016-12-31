package albite.net.rtsp;

public final class Constants {

    private Constants() {
    }

    public static final int RTSP_MAJOR_VERSION = 1;
    public static final int RTSP_MINOR_VERSION = 0;

    public static final String RTSP_VERSION = String.format(
            "RTSP/%d.%d", RTSP_MAJOR_VERSION, RTSP_MINOR_VERSION);

    public static final int RTSP_PORT = 554;
}
