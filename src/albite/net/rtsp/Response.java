package albite.net.rtsp;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static albite.net.rtsp.Constants.*;

public class Response extends Message {

    public static class Status {

        public enum Kind {
            INFORMATIONAL,
            SUCCESS,
            REDIRECTION,
            CLIENT_ERROR,
            SERVER_ERROR,
        }

        public static final Status CONTINUE = new Status(100, "Continue");
        public static final Status OK = new Status(200, "OK");
        public static final Status CREATED = new Status(201, "Created");
        public static final Status LOW_ON_STORAGE_SPACE = new Status(250, "Low on Storage Space");
        public static final Status MULTIPLE_CHOICES = new Status(300, "Multiple Choices");
        public static final Status MOVED_PERMANENTLY = new Status(301, "Moved Permanently");
        public static final Status MOVED_TEMPORARILY = new Status(302, "Moved Temporarily");
        public static final Status SEE_OTHER = new Status(303, "See Other");
        public static final Status NOT_MODIFIED = new Status(304, "Not Modified");
        public static final Status USE_PROXY = new Status(305, "Use Proxy");
        public static final Status BAD_REQUEST = new Status(400, "Bad Request");
        public static final Status UNAUTHORIZED = new Status(401, "Unauthorized");
        public static final Status PAYMENT_REQUIRED = new Status(402, "Payment Required");
        public static final Status FORBIDDEN = new Status(403, "Forbidden");
        public static final Status NOT_FOUND = new Status(404, "Not Found");
        public static final Status METHOD_NOT_ALLOWED = new Status(405, "Method Not Allowed");
        public static final Status NOT_ACCEPTABLE = new Status(406, "Not Acceptable");
        public static final Status PROXY_AUTHENTICATION_REQUIRED = new Status(407, "Proxy Authentication Required");
        public static final Status REQUEST_TIMEOUT = new Status(408, "Request Time-out");
        public static final Status GONE = new Status(410, "Gone");
        public static final Status LENGTH_REQUIRED = new Status(411, "Length Required");
        public static final Status PRECONDITION_FAILED = new Status(412, "Precondition Failed");
        public static final Status REQUEST_ENTITY_TOO_LARGE = new Status(413, "Request Entity Too Large");
        public static final Status REQUEST_URI_TOO_LARGE = new Status(414, "Request-URI Too Large");
        public static final Status UNSUPPORTED_MEDIA_TYPE = new Status(415, "Unsupported Media Type");
        public static final Status PARAMETER_NOT_UNDERSTOOD = new Status(451, "Parameter Not Understood");
        public static final Status CONFERENCE_NOT_FOUND = new Status(452, "Conference Not Found");
        public static final Status NOT_ENOUGH_BANDWIDTH = new Status(453, "Not Enough Bandwidth");
        public static final Status SESSION_NOT_FOUND = new Status(454, "Session Not Found");
        public static final Status METHOD_NOT_VALID_IN_THIS_STATE = new Status(455, "Method Not Valid in This State");
        public static final Status HEADER_FIELD_NOT_VALID_FOR_RESOURCE = new Status(456, "Header Field Not Valid for Resource");
        public static final Status INVALID_RANGE = new Status(457, "Invalid Range");
        public static final Status PARAMETER_IS_READONLY = new Status(458, "Parameter Is Read-Only");
        public static final Status AGGREGATE_OPERATION_NOT_ALLOWED = new Status(459, "Aggregate operation not allowed");
        public static final Status ONLY_AGGREGATE_OPERATION_ALLOWED = new Status(460, "Only aggregate operation allowed");
        public static final Status UNSUPPORTED_TRANSPORT = new Status(461, "Unsupported transport");
        public static final Status DESTINATION_UNREACHABLE = new Status(462, "Destination unreachable");
        public static final Status INTERNAL_SERVER_ERROR = new Status(500, "Internal Server Error");
        public static final Status NOT_IMPLEMENTED = new Status(501, "Not Implemented");
        public static final Status BAD_GATEWAY = new Status(502, "Bad Gateway");
        public static final Status SERVICE_UNAVAILABLE = new Status(503, "Service Unavailable");
        public static final Status GATEWAY_TIMEOUT = new Status(504, "Gateway Time-out");
        public static final Status RTSP_VERSION_NOT_SUPPORTED = new Status(505, "RTSP Version not supported");
        public static final Status OPTION_NOT_SUPPORTED = new Status(551, "Option not supported");

        // Status-Line = RTSP-Version SP Status-Code SP Reason-Phrase CRLF
        // E.g.: RTSP/1.0 200 OK
        private static final Pattern PATTERN
                = Pattern.compile(RTSP_VERSION + " (\\d\\d\\d) (.*)");

        private final Kind mKind;
        private final int mCode;
        private final String mPhrase;

        public Status(int code, String phrase) {
            mCode = code;
            mPhrase = phrase;
            mKind = codeToKind(code);
        }

        private static Kind codeToKind(int code) {
            if (100 <= code && code < 200) {
                return Kind.INFORMATIONAL;
            } else if (200 <= code && code < 300) {
                return Kind.SUCCESS;
            } else if (300 <= code && code < 400) {
                return Kind.REDIRECTION;
            } else if (400 <= code && code < 500) {
                return Kind.CLIENT_ERROR;
            } else if (500 <= code && code < 600) {
                return Kind.SERVER_ERROR;
            } else {
                throw new IllegalArgumentException("Illegal code: " + code);
            }
        }

        public int getCode() {
            return mCode;
        }

        public Kind getKind() {
            return mKind;
        }

        public String getPhrase() {
            return mPhrase;
        }

        @Override
        public String toString() {
            return String.format("%s %d %s", RTSP_VERSION, mCode, mPhrase);
        }

        private static Status fromString(String s) {
            Matcher m = PATTERN.matcher(s);
            if (!m.matches()) {
                throw new IllegalArgumentException("Invalid status line: " + s);
            }

            return new Status(Integer.parseInt(m.group(1)), m.group(2));
        }
    }

    private static final String HEADER_SERVER = "Server";

    private Status mStatus;

    public static Response respondWithSuccess(Request request) {
        return respondWithSuccess(request, null);
    }

    public static Response respondWithSuccess(Request request, byte[] body) {
        return respondWithSuccess(request, body, null);
    }

    public static Response respondWithSuccess(Request request, byte[] body, String server) {
        return new Response(
                Status.OK,
                request.getSequenceNumber(),
                request.getSession(), body, server);
    }

    public static Response respondWithError(Request request) {
        return respondWithError(request, null);
    }

    public static Response respondWithError(Request request, String server) {
        return new Response(
                Status.INTERNAL_SERVER_ERROR,
                request.getSequenceNumber(),
                request.getSession(), null, server);
    }

    public Response(Status status, int sequenceNumber) {
        this(status, sequenceNumber, null);
    }

    public Response(Status status, int sequenceNumber, String session) {
        this(status, sequenceNumber, session, null);
    }

    public Response(Status status, int sequenceNumber, String session, byte[] body) {
        this(status, sequenceNumber, session, body, null);
    }

    public Response(Status status, int sequenceNumber, String session, byte[] body, String server) {
        super(sequenceNumber, session, body);

        mStatus = status;

        // Make sure we update the headers
        if (server != null) {
            setHeader(HEADER_SERVER, server);
        }
    }

    private Response() {
    }

    public Status getStatus() {
        return mStatus;
    }

    public String getServer() {
        return getOptionalHeader(HEADER_SERVER);
    }

    @Override
    protected String getTitleLine() {
        return mStatus.toString();
    }

    public static Response readFromStream(InputStream in) throws IOException {
        Response r = new Response();
        r.mStatus = Status.fromString(fill(r, in));
        return r;
    }

    @Override
    public String toString() {
        return mStatus.toString();
    }
}
