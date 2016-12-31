package albite.net.rtsp;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.IIOException;

public abstract class Message {

    // Header-Key: Header Value
    private static final Pattern PATTERN = Pattern.compile("([^:]*): *(.*)");

    public static final String MIME_TYPE_SDP = "application/sdp";

    public static final String HEADER_ACCEPT = "Accept";
    public static final String HEADER_CONNECTION = "Connection";
    public static final String HEADER_CONTENT_BASE = "Content-Base";
    public static final String HEADER_CONTENT_LENGTH = "Content-Length";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_PUBLIC = "Public";
    public static final String HEADER_SEQUENCE_NUMBER = "CSeq";
    public static final String HEADER_SESSION = "Session";
    public static final String HEADER_TRANSPORT = "Transport";

    public static final String CHARSET = "UTF-8";

    private static final String NEW_LINE = "\r\n";
    private static final String HEADER_SERARATOR = ": ";
    private static final int BUFFER_SIZE = 2048;

    private byte[] mBody;
    private final Map<String, String> mHeaders = new HashMap<>();

    protected Message() {
    }

    protected Message(int sequenceNumber, String session) {
        this(sequenceNumber, session, null);
    }

    protected Message(int sequenceNumber, String session, byte[] body) {
        mBody = body;

        // Update the headers
        mHeaders.put(HEADER_SEQUENCE_NUMBER, Integer.toString(sequenceNumber));

        if (session != null) {
            mHeaders.put(HEADER_SESSION, session);
        }

        if (body != null) {
            mHeaders.put(HEADER_CONTENT_LENGTH, Integer.toString(body.length));
        }
    }

    public final int getSequenceNumber() {
        return Integer.parseInt(getRequiredHeader(HEADER_SEQUENCE_NUMBER));
    }

    public final String getSession() {
        if (mHeaders.containsKey(HEADER_SESSION)) {
            return mHeaders.get(HEADER_SESSION);
        }

        return null;
    }

    public final boolean containsHeader(String header) {
        return mHeaders.containsKey(header);
    }

    public final String getOptionalHeader(String header) {
        if (mHeaders.containsKey(header)) {
            return mHeaders.get(header);
        }

        return null;
    }

    public final String getRequiredHeader(String header) {
        if (mHeaders.containsKey(header)) {
            return mHeaders.get(header);
        }

        throw new IllegalStateException(
                "message does not contain required header: " + header);
    }

    public final void setHeader(String header, String value) {
        mHeaders.put(header, value);
    }

    public final void setNonPersistent() {
        mHeaders.put(HEADER_CONNECTION, "close");
    }

    public final byte[] getBody() {
        return mBody;
    }

    protected abstract String getTitleLine();

    public final void send(OutputStream out) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(out, CHARSET);

        // Write the request method / response message first
        writer.write(getTitleLine());
        writer.write(NEW_LINE);

        // Now write all headers
        for (Map.Entry<String, String> entry : mHeaders.entrySet()) {
            writer.write(entry.getKey());
            writer.write(HEADER_SERARATOR);
            writer.write(entry.getValue());
            writer.write(NEW_LINE);
        }

        // Write an extra new line to specify end of header section
        writer.write(NEW_LINE);
        writer.flush();

        // Write the payload if any
        if (mBody != null) {
            out.write(mBody);
        }
    }

    protected static String fill(Message message, InputStream in) throws IOException {

        // We use a combination of a buffered input stream and a non-buffered
        // buffered reader (i.e. the buffer size being 1). That is because
        // we need to:
        //   - read the header section line by line
        //   - if there's a body, read it as pure octet data
        // In other words, the buffering is done by the BIS, while the BR
        // is only used for parsing the lines. If we used a BR with a buffer
        // size > 1, it would have buffered *past* the header section right
        // somewhere into the body
        BufferedInputStream bis = new BufferedInputStream(in, BUFFER_SIZE);
        BufferedReader reader = new BufferedReader(new InputStreamReader(bis), 1);

        // Read the title line
        String title = reader.readLine();
        if (title == null) {
            throw new IOException("EOF before reading the title line");
        }

        // An empty line indicates the end of the header section
        for (String header = reader.readLine(); !"".equals(header); header = reader.readLine()) {
            if (header == null) {
                throw new IIOException("EOF before end of header section");
            }

            // Try to parse the header
            Matcher m = PATTERN.matcher(header);
            if (!m.matches()) {
                throw new IIOException("Could not parse header: " + header);
            }

            message.mHeaders.put(m.group(1), m.group(2));
        }

        // Read the body (if present)
        if (message.mHeaders.containsKey(HEADER_CONTENT_LENGTH)) {
            int size = Integer.parseInt(message.mHeaders.get(HEADER_CONTENT_LENGTH));
            byte[] body = new byte[size];
            new DataInputStream(bis).readFully(body);
            message.mBody = body;
        }

        return title;
    }
}
