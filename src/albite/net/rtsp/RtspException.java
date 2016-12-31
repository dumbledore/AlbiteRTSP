package albite.net.rtsp;

import java.io.IOException;

public class RtspException extends IOException {

    private final Response.Status mStatus;

    public RtspException(Response.Status status) {
        mStatus = status;
    }

    public RtspException(Response.Status status, String message) {
        super(message);
        mStatus = status;
    }

    public Response.Status getStatus() {
        return mStatus;
    }
}
