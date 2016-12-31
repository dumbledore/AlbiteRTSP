package albite.net.rtsp;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static albite.net.rtsp.Constants.*;

public class Request extends Message {

    public enum Method {
        DESCRIBE, // Stateless
        ANNOUNCE, // N/A on the server
        OPTIONS, // Stateless
        SETUP, // Initializes a session if none already running
        PLAY, // Requires an active session
        PAUSE, // Requires an active session. N/A on the server
        RECORD, // Requires an active session. N/A on the server
        GET_PARAMETER, // Requires an active session. N/A on the server
        SET_PARAMETER, // Requires an active session. N/A on the server
        REDIRECT, // Requires an active session. N/A on the server
        TEARDOWN, // Destroys the session
    }

    public static class Description {

        // Request-Line = Method SP Request-URI SP RTSP-Version CRLF
        // E.g.: PLAY rtsp://audio.example.com/audio RTSP/1.0
        private static final Pattern PATTERN
                = Pattern.compile("([^ ]+) (.*) " + RTSP_VERSION);

        private final Method mMethod;
        private final URI mUri;

        public Description(Method method, URI uri) {
            mMethod = method;
            mUri = uri;
        }

        public Method getMethod() {
            return mMethod;
        }

        public URI getUri() {
            return mUri;
        }

        @Override
        public String toString() {
            return String.format("%s %s %s", mMethod.name(), mUri, RTSP_VERSION);
        }

        public static Description fromString(String s)
                throws URISyntaxException {

            Matcher m = PATTERN.matcher(s);
            if (!m.matches()) {
                throw new IllegalArgumentException("Invalid request line: " + s);
            }

            Method method = Method.valueOf(m.group(1));
            URI uri = new URI(m.group(2));
            return new Description(method, uri);
        }
    }

    private static final String HEADER_USER_AGENT = "User-Agent";

    private Description mDescription;

    public Request(Description description, int sequenceNumber) {
        this(description, sequenceNumber, null);
    }

    public Request(Description description, int sequenceNumber, String session) {
        this(description, sequenceNumber, session, null);
    }

    public Request(Description description, int sequenceNumber, String session, byte[] body) {
        this(description, sequenceNumber, session, body, null);
    }

    public Request(Description description, int sequenceNumber, String session, byte[] body, String userAgent) {
        super(sequenceNumber, session, body);

        mDescription = description;

        // Make sure we update the headers
        if (userAgent != null) {
            setHeader(HEADER_USER_AGENT, userAgent);
        }
    }

    private Request() {
    }

    public Description getDescription() {
        return mDescription;
    }

    public String getUserAgent() {
        return getOptionalHeader(HEADER_USER_AGENT);
    }

    @Override
    protected final String getTitleLine() {
        return mDescription.toString();
    }

    public static Request readFromStream(InputStream in) throws IOException {
        Request r = new Request();
        try {
            r.mDescription = Description.fromString(fill(r, in));
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        return r;
    }

    @Override
    public String toString() {
        return mDescription.toString();
    }
}
